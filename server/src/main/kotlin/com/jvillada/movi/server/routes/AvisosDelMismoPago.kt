package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.momentoDelSms
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Cuánto pueden separarse dos avisos del mismo pago **entre SMS y notificaciones**. Llegan a
 * segundos: en el histórico del dueño (238 mensajes, 25-jul a 4-oct-2026) los 14 pagos avisados
 * dos veces quedaron a 0 minutos (13) o a 1 (1), y el resultado es el mismo con cualquier ventana
 * de 1 a 15 minutos. El par siguiente aparece recién a los 21 minutos —$3.300 en una máquina
 * expendedora, avisado por Glim y por Google Wallet con dos comercios distintos— y no hay forma de
 * saber si era un pago o dos. Diez minutos cubre una demora del teléfono sin llegar ahí.
 */
internal const val MINUTOS_PARA_EL_MISMO_PAGO: Long = 10

/**
 * **Cuando uno de los dos es un correo**, la ventana es más ancha: el correo del banco pasa por su
 * servidor de correo, por el reenvío de Gmail y por el proveedor de correo entrante (Resend
 * reintenta a los 5 s, 5 min y 30 min), y su hora es la de llegada al server. El histórico todavía
 * no tiene un solo correo de un movimiento —el canal se encendió el 4-oct—, así que este número no
 * está medido: es el tope de demora que se acepta. Dentro de esa hora la regla de «nunca dos del
 * mismo origen» sigue protegiendo de juntar dos compras iguales: un correo solo se junta con un SMS
 * o una notificación, nunca con otro correo.
 */
internal const val MINUTOS_PARA_EL_MISMO_PAGO_POR_CORREO: Long = 60

/** Por dónde llegó un aviso: decide su ventana, de cuál sale la propuesta y cómo se nombra. */
internal enum class CanalDelAviso { SMS, NOTIFICACION, CORREO, COMPROBANTE }

private fun limpioDeEspacios(origen: String): String =
    origen.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

internal fun canalDelAviso(origen: String): CanalDelAviso {
    val limpio = limpioDeEspacios(origen)
    return when {
        limpio.startsWith("Notificación", ignoreCase = true) || limpio.startsWith("Notificacion", ignoreCase = true) ->
            CanalDelAviso.NOTIFICACION
        limpio.startsWith("Correo", ignoreCase = true) -> CanalDelAviso.CORREO
        limpio.startsWith("Comprobante", ignoreCase = true) -> CanalDelAviso.COMPROBANTE
        // El código corto del remitente («85540», «85784»), vacío en el barrido, o «SMS».
        else -> CanalDelAviso.SMS
    }
}

/**
 * **El origen como lo cuenta la regla de «nunca dos del mismo origen»**: un origen no avisa dos
 * veces el mismo pago, y dos compras iguales de verdad sí existen.
 *
 * Todos los SMS son UNO: Bancolombia escribe desde varios códigos cortos (`85540` las compras,
 * `85784` los pagos, `85697` las ampliaciones) pero no avisa el mismo pago dos veces por SMS. Las
 * notificaciones y los correos se distinguen por su rótulo entero («Notificación · Google Wallet»,
 * «Correo · Bancolombia»), sin mayúsculas ni el espacio duro que trae Android en «Google Wallet».
 */
internal fun origenDelAviso(origen: String): String = when (canalDelAviso(origen)) {
    CanalDelAviso.SMS -> "sms"
    else -> limpioDeEspacios(origen).lowercase()
}

/** La ventana entre dos avisos según sus canales (ver las dos constantes de arriba). */
internal fun minutosParaElMismoPago(origenA: String, origenB: String): Long =
    if (canalDelAviso(origenA) == CanalDelAviso.CORREO || canalDelAviso(origenB) == CanalDelAviso.CORREO) {
        MINUTOS_PARA_EL_MISMO_PAGO_POR_CORREO
    } else {
        MINUTOS_PARA_EL_MISMO_PAGO
    }

private val MINUTOS_MAXIMOS = maxOf(MINUTOS_PARA_EL_MISMO_PAGO, MINUTOS_PARA_EL_MISMO_PAGO_POR_CORREO)

/**
 * **Los avisos de un mismo pago**, ya juntados: [id] es el del más antiguo (estable mientras ese no
 * se saque) y [miembros] van del más viejo al más nuevo.
 */
internal class GrupoDelMismoPago(val id: String, val miembros: List<SmsMessage>) {
    val ids: List<String> get() = miembros.map { it.id }
}

/**
 * # Qué avisos son el mismo pago
 *
 * El 25-sep el dueño pagó $15.100 con la tarjeta Glim y le llegaron dos avisos: el de Google Wallet
 * y el de la app de Glim. Los aprobó los dos, seis segundos aparte, y quedaron dos movimientos.
 * Hasta el 4-oct la bandeja se lo advertía ([SmsMessage.parecidoA]); desde entonces los junta: ver
 * el pago una vez, confirmarlo una vez, y poder decir «no son el mismo pago».
 *
 * **Dos avisos son el mismo pago** cuando `parseSms` lee en los dos el mismo monto (redondeado,
 * como se guarda), la misma moneda y el mismo tipo, llegaron a [minutosParaElMismoPago] o menos
 * (según [momentoDelSms]) y vienen de **orígenes distintos** ([origenDelAviso]).
 *
 * **Un pago junta en cadena**: si A es el mismo pago que B y B que C, A, B y C son uno, aunque A y C
 * estén más lejos entre sí. Con una condición que nunca se rompe: **dos avisos del mismo origen
 * nunca van en el mismo pago**. Por eso no es «todo lo conectado» a secas: se une primero lo más
 * cercano, y dos pagos se juntan solo si no comparten ningún origen. Dos cafés iguales pagados con
 * la Glim a tres minutos —dos avisos de Wallet y dos de Glim— quedan como dos pagos, cada Wallet
 * con su Glim más cercano, y no como uno de cuatro.
 *
 * **Quién entra**: los pendientes y los confirmados —un confirmado en el pago quiere decir que ese
 * pago ya tiene su movimiento—. No entran los ignorados (por el dueño o apartados por Movi: ya está
 * decidido), ni lo que él separó con «No son el mismo pago» ([sueltos]: nunca más se junta), ni los
 * comprobantes (`cmp_`: su lectura no es la de `parseSms` y su hora es la de la foto), ni un aviso
 * cuya hora no se puede leer o cae en el futuro: [momentoDelSms] los fecha «ahora» y dos de esos
 * quedarían a cero minutos.
 *
 * Con [soloConPendientes] (lo normal) solo salen los pagos con algo pendiente, y solo se lee (con
 * `parseSms`) lo que está cerca de un pendiente: el historial crece sin tope y casi nada de él está
 * cerca. Sin él —el backtesting— salen todos.
 */
internal fun gruposDelMismoPago(
    mensajes: List<SmsMessage>,
    ahora: Long,
    sueltos: Set<String> = emptySet(),
    soloConPendientes: Boolean = true,
    /** Cómo se lee un aviso. Es `parseSms`; se deja cambiar para poder contar cuántos se leen. */
    leer: (SmsMessage) -> ParsedSms? = { parseSms(it.text, it.bank) },
    /** La ventana entre dos avisos, en minutos. Es [minutosParaElMismoPago]; el backtesting la varía. */
    ventana: (String, String) -> Long = ::minutosParaElMismoPago,
): List<GrupoDelMismoPago> {
    val participantes = mensajes.mapNotNull { sms ->
        if (sms.state != SMS_STATE_PENDING && sms.state != SMS_STATE_CONFIRMED) return@mapNotNull null
        if (sms.id in sueltos || esIdDeComprobante(sms.id)) return@mapNotNull null
        val momento = momentoConfiable(sms.time, ahora) ?: return@mapNotNull null
        sms to momento
    }
    val anclas = participantes
        .filter { (sms, _) -> !soloConPendientes || sms.state == SMS_STATE_PENDING }
        .map { it.second }
        .sorted()
    if (anclas.isEmpty()) return emptyList()
    // Una cadena puede alejarse de su pendiente más que una ventana (A~B~C): se lee hasta dos.
    val alcance = 2 * MINUTOS_MAXIMOS * 60_000L
    fun cercaDeUnPendiente(momento: Long): Boolean {
        val i = anclas.binarySearch(momento)
        if (i >= 0) return true
        val despues = -i - 1
        return (despues < anclas.size && anclas[despues] - momento <= alcance) ||
            (despues > 0 && momento - anclas[despues - 1] <= alcance)
    }
    val leidos = participantes
        .filter { (_, momento) -> cercaDeUnPendiente(momento) }
        .mapNotNull { (sms, momento) -> leer(sms)?.let { Leido(sms, it, momento) } }
        .sortedWith(compareBy<Leido> { it.momento }.thenBy { it.sms.id })
    if (leidos.size < 2) return emptyList()

    // Las uniones posibles, de la más cercana a la más lejana. Ordenados por momento, cada aviso
    // solo se compara con los que vienen detrás dentro de la ventana más ancha.
    val margenMaximo = MINUTOS_MAXIMOS * 60_000L
    class Union(val a: Int, val b: Int, val distancia: Long)
    val uniones = mutableListOf<Union>()
    for (i in leidos.indices) {
        val a = leidos[i]
        var j = i + 1
        while (j < leidos.size && leidos[j].momento - a.momento <= margenMaximo) {
            val b = leidos[j]
            val distancia = b.momento - a.momento
            if (a.origen != b.origen && a.llave == b.llave && distancia <= ventana(a.sms.bank, b.sms.bank) * 60_000L) {
                uniones += Union(i, j, distancia)
            }
            j++
        }
    }
    if (uniones.isEmpty()) return emptyList()

    // Unión de conjuntos con los orígenes de cada pago: una unión que repetiría un origen no se hace.
    val padre = IntArray(leidos.size) { it }
    val origenes = Array(leidos.size) { mutableSetOf(leidos[it].origen) }
    fun raiz(x: Int): Int {
        var r = x
        while (padre[r] != r) r = padre[r]
        return r
    }
    uniones.sortedWith(compareBy<Union> { it.distancia }.thenBy { it.a }.thenBy { it.b }).forEach { u ->
        val ra = raiz(u.a)
        val rb = raiz(u.b)
        if (ra == rb || origenes[ra].any { it in origenes[rb] }) return@forEach
        val (queda, se) = if (ra < rb) ra to rb else rb to ra
        padre[se] = queda
        origenes[queda].addAll(origenes[se])
    }
    return leidos.indices
        .groupBy { raiz(it) }
        .values
        .filter { it.size > 1 }
        .map { indices -> indices.sorted().map { leidos[it].sms } }
        .filter { miembros -> !soloConPendientes || miembros.any { it.state == SMS_STATE_PENDING } }
        .map { miembros -> GrupoDelMismoPago(miembros.first().id, miembros) }
}

/**
 * **La bandeja con los pagos marcados**: cada aviso de un pago con algo pendiente lleva su
 * [SmsMessage.grupoId] y [SmsMessage.miembrosDelGrupo]; los pendientes, además, el aviso más cercano
 * del mismo pago en [SmsMessage.parecidoA] (lo que lee un APK viejo para advertir) y, si el pago ya
 * tiene un aviso confirmado, ese en [SmsMessage.yaAnotadoCon].
 *
 * No cambia el orden ni ningún otro campo. Ver [gruposDelMismoPago].
 */
internal fun conLosAvisosEnGrupo(
    mensajes: List<SmsMessage>,
    ahora: Long,
    sueltos: Set<String> = emptySet(),
    leer: (SmsMessage) -> ParsedSms? = { parseSms(it.text, it.bank) },
): List<SmsMessage> {
    val grupos = gruposDelMismoPago(mensajes, ahora, sueltos, leer = leer)
    if (grupos.isEmpty()) return mensajes
    val grupoDe = HashMap<String, GrupoDelMismoPago>()
    grupos.forEach { g -> g.miembros.forEach { grupoDe[it.id] = g } }
    return mensajes.map { sms ->
        val grupo = grupoDe[sms.id] ?: return@map sms
        val marcado = sms.copy(grupoId = grupo.id, miembrosDelGrupo = grupo.ids)
        if (sms.state != SMS_STATE_PENDING) return@map marcado
        val momento = momentoDelSms(sms.time, ahora)
        fun distancia(otro: SmsMessage) = abs(momentoDelSms(otro.time, ahora) - momento)
        val otros = grupo.miembros.filter { it.id != sms.id }
        marcado.copy(
            parecidoA = otros.minByOrNull(::distancia)?.id,
            yaAnotadoCon = otros.filter { it.state == SMS_STATE_CONFIRMED }.minByOrNull(::distancia)?.id,
        )
    }
}

/**
 * **De qué aviso sale la propuesta de Reconciliar**: del que más dice.
 *
 * 1. El que trae un **comercio legible**: «TOSTAO CAFE Y PAN», no «Pago QR · llave 0088…» ni
 *    «Movimiento». Es lo que el dueño va a buscar después en Movimientos.
 * 2. El que nombra **la cuenta o la tarjeta** («*8133», «••3037», «terminada en 1336»): es lo que
 *    deja a la app elegir la cuenta por el número y no por una corazonada.
 * 3. A igualdad, **el canal**: el SMS del banco (su propio registro), después la app del banco,
 *    después Google Wallet, después el correo; y entre iguales, el más viejo, para que la elección
 *    no cambie al recargar.
 *
 * La cuenta, además, no sale solo de este: la app prueba con el texto de todos los avisos del pago
 * y se queda con la mejor lectura (`resolverCuentaDelGrupo` en `:shared`). Así un pago por QR toma
 * el comercio de Google Wallet y la cuenta *8133 del SMS.
 */
internal fun propuestaDelGrupo(
    miembros: List<SmsMessage>,
    leer: (SmsMessage) -> ParsedSms? = { parseSms(it.text, it.bank) },
): SmsMessage {
    require(miembros.isNotEmpty())
    val comercios = miembros.associate { it.id to leer(it)?.merchant }
    return miembros.sortedWith(
        compareByDescending<SmsMessage> { comercios[it.id]?.let(::comercioLegible) == true }
            .thenByDescending { traeLaCuenta(it.text) }
            .thenBy { ordenDelCanal(it.bank) }
            .thenBy { momentoDelSms(it.time, Long.MAX_VALUE) }
            .thenBy { it.id },
    ).first()
}

private val COMERCIOS_GENERICOS = setOf("movimiento", "transferencia", "transferencia recibida", "pago qr", "pago de tarjeta")

/** ¿Es un nombre que sirve para buscar después? No lo son los que pone `parseSms` cuando no encontró uno. */
internal fun comercioLegible(comercio: String): Boolean {
    val limpio = comercio.trim().lowercase()
    return limpio.isNotEmpty() && limpio !in COMERCIOS_GENERICOS &&
        !limpio.startsWith("pago qr · llave") && !limpio.startsWith("transferencia a la cuenta")
}

private val CUENTA_EN_EL_TEXTO = Regex("""\*\s?\d{4,}|terminada\s+en\s+\d{4}|•+\s?\d{4}|\bproducto\s+\d{4}""", RegexOption.IGNORE_CASE)

/** ¿El aviso escribe una cuenta o una tarjeta? */
internal fun traeLaCuenta(texto: String): Boolean = CUENTA_EN_EL_TEXTO.containsMatchIn(texto)

private fun ordenDelCanal(origen: String): Int = when (canalDelAviso(origen)) {
    CanalDelAviso.SMS -> 0
    CanalDelAviso.NOTIFICACION -> if (origen.contains("wallet", ignoreCase = true)) 2 else 1
    CanalDelAviso.CORREO -> 3
    CanalDelAviso.COMPROBANTE -> 4
}

/**
 * **La bandeja del dueño, con los pagos marcados.** La lee entera (con lo que separó con «No son el
 * mismo pago»), del más nuevo al más viejo, y la pasa por [conLosAvisosEnGrupo]. Va dentro de una
 * transacción: la usan la bandeja, el detalle de un aviso, los avisos de un pago y el resumen del
 * Inicio, para que todos cuenten los mismos pagos.
 */
internal fun bandejaConLosPagos(uid: String, ahora: Long): List<SmsMessage> {
    val filas = SmsMessages.selectAll()
        .where { SmsMessages.userId eq uid }
        .orderBy(SmsMessages.time to SortOrder.DESC)
        .toList()
    val sueltos = filas.filter { it[SmsMessages.noEsElMismoPago] == true }.map { it[SmsMessages.id] }.toSet()
    return conLosAvisosEnGrupo(filas.map { it.toSmsMessage() }, ahora, sueltos)
}

/**
 * **Lo que el teléfono tiene que avisar de lo que acaba de subir** (Ola 1 · «Movi anotó»): de
 * [insertados], los que abren un pago nuevo. Un aviso que se juntó con uno que ya estaba —el SMS
 * que llegó un minuto después de la notificación, o la notificación de un pago ya confirmado— no se
 * vuelve a avisar: el dueño ya supo de ese pago. Dentro de lo subido junto, el primero del pago
 * habla por los demás.
 */
internal fun losQueAbrenUnPago(insertados: List<SmsMessage>, bandeja: List<SmsMessage>): List<SmsMessage> {
    val nuevos = insertados.map { it.id }
    val grupoDe = bandeja.associate { it.id to it.miembrosDelGrupo }
    return insertados.filterIndexed { i, sms ->
        grupoDe[sms.id].orEmpty().none { otro -> otro != sms.id && (otro !in nuevos || nuevos.indexOf(otro) < i) }
    }
}

/**
 * El momento del aviso solo si de verdad se leyó y no es futuro. Con `ahora = Long.MAX_VALUE`,
 * [momentoDelSms] no recorta nada y devuelve ese mismo valor únicamente cuando no entendió la hora.
 */
private fun momentoConfiable(time: String, ahora: Long): Long? {
    val momento = momentoDelSms(time, ahora = Long.MAX_VALUE)
    return momento.takeIf { it != Long.MAX_VALUE && it <= ahora }
}

/** Un aviso con lo que `parseSms` leyó de él. */
private class Leido(val sms: SmsMessage, leido: ParsedSms, val momento: Long) {
    val origen: String = origenDelAviso(sms.bank)
    /** Lo que tienen que compartir dos avisos para ser el mismo pago. */
    val llave: String = "${leido.amount.roundToLong()}|${leido.currency}|${leido.type}"
}
