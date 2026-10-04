package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * # Los terceros que Movi encontró solo
 *
 * El dueño (4-oct-2026): *«quiero que la gestión de cuentas de terceros sea muy accesible y que
 * tenga sentido, además que se pueda auto detectar»*. Sus avisos del banco nombran una docena de
 * cuentas, llaves de QR y personas a las que les manda plata seguido, y Movi solo conocía las cinco
 * que él había guardado a mano. Lo demás se veía como «Transferencia a la cuenta *41279033068».
 *
 * [destinosSugeridos] recorre los avisos (SMS, notificaciones, correos, comprobantes) y los
 * movimientos, y junta **lo que no es de nadie conocido**: ni de un tercero guardado, ni una cuenta
 * suya, ni algo que él ya descartó. Por cada uno: cuántas veces, cuánto fue y cuánto vino, cuándo
 * fue la última, el nombre que Movi propone y si parece una persona o un comercio.
 *
 * **Movi propone, no decide.** Nada de esto se guarda solo: la pantalla ofrece «Guardar» con todo
 * prellenado, «Es mía» e «Ignorar». Y cuando un sugerido parece de alguien que ya está guardado
 * —«Carolina Restrepo Salazar» y «Caro»— se pregunta «¿Es Caro?»; unirlos sin preguntar le pondría
 * el nombre de otra persona a la plata si Movi se equivoca.
 */
@Serializable
data class DestinoSugerido(
    /** Cómo lo nombra el banco: el número, la llave o el nombre de la persona. */
    val identificador: IdentificadorDelDestino,
    /** El nombre que Movi propone; vacío si no hay ninguno legible (lo escribe él). */
    val nombrePropuesto: String = "",
    /** De dónde sale [nombrePropuesto]: lo escribió el banco, o él en un movimiento. */
    val origenDelNombre: OrigenDelNombre? = null,
    val tipo: TipoDeTercero = TipoDeTercero.PERSONA,
    /** Cuántos movimientos distintos (un SMS y su movimiento anotado cuentan una vez). */
    val veces: Int,
    /** Lo que se le envió, por moneda. */
    val enviado: Map<String, Long> = emptyMap(),
    /** Lo que llegó de ahí, por moneda. */
    val recibido: Map<String, Long> = emptyMap(),
    /** El más reciente, en milisegundos epoch. */
    val ultimo: Long,
    /**
     * Otras formas de reconocerlo que traen los mismos avisos: el nombre de la persona que acompaña
     * a una llave de Bre-B («a la llave @x … a HENRY JOSE ROJAS ARTEAGA»). Se guardan junto.
     */
    val otrosIdentificadores: List<IdentificadorDelDestino> = emptyList(),
    /** El id del tercero guardado al que se parece («¿Es Caro?»), o `null`. */
    val pareceDe: String? = null,
    val pareceDeNombre: String? = null,
    /** Cuántos movimientos anotados con un nombre ilegible se podrían renombrar al guardarlo. */
    val renombrables: Int = 0,
)

/** De dónde sale el nombre que Movi propone para un sugerido. */
@Serializable
enum class OrigenDelNombre { BANCO, TUYO }

/** Lo que se pide al descartar un sugerido: que no vuelva («Ignorar»), o que es suyo («Es mía»). */
@Serializable
enum class MotivoDeDescarte { IGNORADO, ES_MIA }

@Serializable
data class DescartarSugerido(val identificador: IdentificadorDelDestino, val motivo: MotivoDeDescarte)

/** Lo que pide «Es de un tercero que ya tengo» o «¿Es Caro? Sí»: sumarle [identificador]. */
@Serializable
data class AgregarIdentificador(
    val identificador: IdentificadorDelDestino,
    /** `true` = si otro tercero ya lo tenía, se le quita a ese («Mover a otra persona»). */
    val mover: Boolean = false,
)

/** `GET /api/destinos/{id}/renombrables`: los movimientos de nombre ilegible que se pueden renombrar. */
@Serializable
data class MovimientosParaRenombrar(
    val nombreNuevoDelGasto: String,
    val nombreNuevoDelIngreso: String,
    val movimientos: List<FinancialEvent>,
)

@Serializable
data class RenombrarMovimientos(val ids: List<String>)

@Serializable
data class RenombradosDelDestino(val renombrados: Int, val omitidos: Int)

/**
 * **Lo que el dueño ya dijo que no**, y lo que Movi sabe que es suyo: las claves ([clave]) de los
 * sugeridos que ignoró o marcó «Es mía», y las que Movi dedujo de sus avisos (su propio nombre en
 * «JUAN CAMILO … pagaste», sus cuentas en «desde tu cuenta *8133»). La fila «¿De quién es…?» no
 * pregunta por ninguna de estas.
 */
@Serializable
data class DestinosDescartados(val claves: List<String> = emptyList())

// ── Lo que cuenta como un rastro ────────────────────────────────────────────────

/**
 * **Una vez que un tercero aparece**: en un aviso del banco o en un movimiento anotado. No viaja:
 * lo arma el server (o el backtesting) y lo consume [destinosSugeridos].
 */
data class RastroDeTercero(
    val identificador: IdentificadorDelDestino,
    /** El nombre que escribió el banco junto («DANIEL LEONETT»), si lo hay. */
    val nombreDelBanco: String? = null,
    /** El nombre que el dueño le puso al movimiento («Arepas»), si es legible. */
    val nombreTuyo: String? = null,
    val monto: Long,
    val moneda: String = "COP",
    val tipo: TransactionType,
    val timestamp: Long,
    /** Pago por código QR: casi siempre un comercio. */
    val esQr: Boolean = false,
    /** `true` si sale de un movimiento anotado, `false` si de un aviso. */
    val esMovimiento: Boolean = false,
    /** Un movimiento anotado cuyo nombre es ilegible y se podría renombrar. */
    val renombrable: Boolean = false,
)

/**
 * El rastro de un aviso del banco ([texto], ya leído: [monto], [tipo]). `null` si no nombra a nadie
 * o si es un pago de tarjeta (un pago a la tarjeta propia no es a un tercero).
 */
fun rastroDeUnAviso(
    texto: String,
    monto: Long,
    moneda: String,
    tipo: TransactionType,
    timestamp: Long,
    esPagoDeTarjeta: Boolean = false,
): RastroDeTercero? {
    if (esPagoDeTarjeta || monto <= 0) return null
    val identificador = identificadorDelDestinoEn(texto) ?: return null
    val nombreDelBanco = nombresCrudosDelBancoEn(texto).firstOrNull()
    return RastroDeTercero(
        identificador = identificador,
        nombreDelBanco = nombreDelBanco,
        monto = monto,
        moneda = moneda,
        tipo = tipo,
        timestamp = timestamp,
        esQr = ES_QR.containsMatchIn(texto),
    )
}

/**
 * El rastro de un movimiento anotado. Fuera los traspasos entre cuentas suyas (`transferId`) y los
 * pagos de tarjeta: ninguno es plata que fue a un tercero.
 */
fun rastroDeUnMovimiento(evento: FinancialEvent): RastroDeTercero? {
    if (evento.transferId != null || evento.category == CARD_PAYMENT_CATEGORY) return null
    if (evento.type != TransactionType.EXPENSE && evento.type != TransactionType.INCOME) return null
    val identificador = identificadorDelDestinoEn(evento) ?: return null
    val textos = listOfNotNull(evento.rawPayload, evento.merchant, evento.description)
    val tuyo = nombreQueLePusoElDueno(evento.description)
    return RastroDeTercero(
        identificador = identificador,
        nombreDelBanco = textos.firstNotNullOfOrNull { nombresCrudosDelBancoEn(it).firstOrNull() },
        nombreTuyo = tuyo,
        monto = evento.amount,
        moneda = evento.currency,
        tipo = evento.type,
        timestamp = evento.timestamp,
        esQr = textos.any { ES_QR.containsMatchIn(it) },
        esMovimiento = true,
        renombrable = esUnNombreIlegible(evento.description),
    )
}

private val ES_QR = Regex("""\b(?:c[oó]digo\s+qr|pago\s+qr|qr)\b""", RegexOption.IGNORE_CASE)

/**
 * Lo que dice un concepto sacándole las palabras del banco («Pago QR», «Transferencia a la cuenta»,
 * «llave 0092184713», los números): «Arepas - Pago QR (llave 0092184713)» → «Arepas». `null` si no
 * queda un nombre — entonces el concepto es del banco, no del dueño.
 */
fun nombreQueLePusoElDueno(descripcion: String): String? {
    var limpio = descripcion
    PALABRAS_DEL_BANCO.forEach { limpio = it.replace(limpio, " ") }
    limpio = limpio.map { if (it.isLetter()) it else ' ' }.joinToString("")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    if (limpio.count { it.isLetter() } < 3) return null
    if (normalizarParaBuscar(limpio) in NO_SON_NOMBRES) return null
    return limpio.take(MAX_NOMBRE_DEL_DESTINO)
}

private val PALABRAS_DEL_BANCO = listOf(
    Regex("""\bllave\s+\S+""", RegexOption.IGNORE_CASE),
    Regex("""\*\s?\d+"""),
    Regex("""\d+"""),
    Regex(
        """\b(?:transferencia|pago|pagaste|transferiste|recibiste)(?:\s+(?:recibida|enviada|por|de|a|al|desde|en|la|el|c[oó]digo|qr|cuenta|llave|pse))*\b""",
        RegexOption.IGNORE_CASE,
    ),
    Regex("""\b(?:qr|cuenta|llave|movimiento)\b""", RegexOption.IGNORE_CASE),
)

private val NO_SON_NOMBRES = setOf("transferencia", "pago", "movimiento", "compra", "retiro", "abono")

/**
 * **¿Este concepto es ilegible?** — el que puso el banco con un número o una llave y nada más
 * («Transferencia a la cuenta *41279033068», «Pago QR · llave 0047142708»). Solo esos se ofrecen
 * renombrar: «Arepas - Pago QR (llave …)» lo escribió el dueño y es suyo, aunque traiga la llave.
 */
fun esUnNombreIlegible(descripcion: String): Boolean =
    sePuedeRenombrar(descripcion) && nombreQueLePusoElDueno(descripcion) == null

// ── Lo que es del dueño ─────────────────────────────────────────────────────────

/**
 * «desde tu cuenta *8133», «de tu cuenta *9586», «en tu cuenta *8133», «desde producto *8133». No
 * «de la cuenta *…» a secas: eso puede ser la cuenta de quien te mandó plata.
 */
private val CUENTA_PROPIA_EN_EL_TEXTO =
    Regex("""\b(?:desde\s+(?:(?:tu|la)\s+)?|de\s+tu\s+|en\s+tu\s+)(?:cuenta|producto)\s*\*\s?(\d{4,})""", RegexOption.IGNORE_CASE)

/** «Bancolombia: JUAN CAMILO VILLADA RAMIREZ pagaste $…»: el nombre del dueño, como lo escribe su banco. */
private val NOMBRE_DEL_DUENO_EN_EL_TEXTO =
    Regex("""^\s*[^:\n]{2,40}:\s+([A-ZÁÉÍÓÚÑ]+(?:\s+[A-ZÁÉÍÓÚÑ]+){1,5})\s+pagaste\b""")

/**
 * **Las colas de las cuentas que el banco dice que son suyas** («desde tu cuenta *8133»): la
 * cuenta «Bancolombia Ahorros» no lleva el número en el nombre, y sin esto «hacia la cuenta
 * *02955068133» —un traspaso a su propia cuenta— se ofrecería como un tercero.
 */
fun colasPropiasEn(textos: List<String>): Set<String> =
    textos.flatMap { t -> CUENTA_PROPIA_EN_EL_TEXTO.findAll(t).map { it.groupValues[1].takeLast(MIN_DIGITOS_DEL_NUMERO) } }.toSet()

/** Los nombres con que su banco nombra al dueño, listos para comparar ([claveDeLlave]). */
fun nombresPropiosEn(textos: List<String>): Set<String> =
    textos.mapNotNull { NOMBRE_DEL_DUENO_EN_EL_TEXTO.find(it)?.groupValues?.get(1) }.map(::claveDeLlave).toSet()

/**
 * Las claves que la fila «¿De quién es…?» no tiene que ofrecer nunca: lo descartado más lo que Movi
 * sabe que es del dueño (sus nombres y sus colas, como `NUMERO:…` de cuatro dígitos).
 */
fun clavesQueSonDelDueno(nombresPropios: Set<String>): Set<String> =
    nombresPropios.map { "LLAVE:$it" }.toSet()

// ── Juntar ──────────────────────────────────────────────────────────────────────

/** Cuánto puede separar el aviso del banco del movimiento que se anotó con él. */
private const val VENTANA_AVISO_MOVIMIENTO_MS = 36L * 60 * 60 * 1000

/** Dos avisos del mismo pago (el SMS y la notificación) llegan con minutos de diferencia. */
private const val VENTANA_AVISO_AVISO_MS = 15L * 60 * 1000

/** Un sugerido visto una sola vez entra si fue hace menos de esto: lo reciente todavía se recuerda. */
const val DIAS_DE_UN_SUGERIDO_RECIENTE: Int = 45

/**
 * **Los sugeridos.** Ver el KDoc del archivo. Reglas:
 *
 * - **Se excluye** lo que reconoce un tercero guardado ([elDestinoConoce]), una cuenta suya (por el
 *   número en el nombre de sus cuentas, o por [colasPropias] que el banco dice que son suyas), su
 *   propio nombre ([nombresPropios]) y lo [descartados].
 * - **Un mismo pago cuenta una vez**: el aviso y el movimiento anotado con él (mismo monto, mismo
 *   tipo, hasta 36 h), o el SMS y la notificación (hasta 15 min).
 * - **Entra** lo que aparece dos veces o más, o una vez en los últimos [DIAS_DE_UN_SUGERIDO_RECIENTE]
 *   días.
 * - **El nombre**: el del banco en Título Caso; si no hay, el que él le puso al movimiento (el más
 *   usado).
 * - **El tipo**: comercio si se pagó por QR; persona si no.
 * - **«¿Es Caro?»**: [pareceDe].
 *
 * Ordenado por veces y después por lo más reciente.
 */
fun destinosSugeridos(
    rastros: List<RastroDeTercero>,
    destinos: List<DestinoConocido>,
    nombresDeCuentas: List<String>,
    colasPropias: Set<String>,
    nombresPropios: Set<String>,
    descartados: Set<String>,
    ahora: Long,
): List<DestinoSugerido> {
    fun esDelDueno(id: IdentificadorDelDestino): Boolean = when (id.tipo) {
        TipoDeIdentificador.NUMERO ->
            ultimosCuatro(id.valor) in colasPropias || nombreDeLaCuentaPropiaConEseNumero(id.valor, nombresDeCuentas) != null
        TipoDeIdentificador.LLAVE -> claveDeLlave(id.valor) in nombresPropios
    }

    val vivos = rastros.filter { r ->
        destinos.none { elDestinoConoce(it, r.identificador) } &&
            r.identificador.clave !in descartados &&
            !esDelDueno(r.identificador)
    }
    val minimoReciente = ahora - DIAS_DE_UN_SUGERIDO_RECIENTE * 86_400_000L

    return vivos.groupBy { it.identificador.clave }.mapNotNull { (_, delMismo) ->
        val pagos = unPagoUnaVez(delMismo)
        val ultimo = pagos.maxOf { it.timestamp }
        if (pagos.size < 2 && ultimo < minimoReciente) return@mapNotNull null
        val identificador = delMismo.first().identificador.normalizado()
        val delBanco = delMismo.mapNotNull { it.nombreDelBanco }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val tuyo = delMismo.mapNotNull { it.nombreTuyo }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val nombre = when {
            delBanco != null -> enTituloCaso(delBanco)
            tuyo != null -> tuyo
            identificador.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(identificador.valor) -> enTituloCaso(identificador.valor)
            else -> ""
        }.take(MAX_NOMBRE_DEL_DESTINO)
        val origen = when {
            delBanco != null -> OrigenDelNombre.BANCO
            tuyo != null -> OrigenDelNombre.TUYO
            nombre.isNotEmpty() -> OrigenDelNombre.BANCO
            else -> null
        }
        // El nombre que acompaña a una llave o a un número también lo reconoce: se guarda junto.
        val otros = delMismo.mapNotNull { it.nombreDelBanco }
            .map { IdentificadorDelDestino(TipoDeIdentificador.LLAVE, normalizarLlave(it)) }
            .filterNot { it.clave == identificador.clave || esDelDueno(it) }
            .distinctBy { it.clave }
        val parecido = pareceDe(listOfNotNull(nombre.takeIf { it.isNotEmpty() }, delBanco, tuyo) +
            listOfNotNull(identificador.valor.takeIf { laLlaveEsUnNombre(it) }), destinos)
        val esComercio = delMismo.any { it.esQr } && delBanco == null
        DestinoSugerido(
            identificador = identificador,
            nombrePropuesto = nombre,
            origenDelNombre = origen,
            tipo = if (esComercio) TipoDeTercero.COMERCIO else TipoDeTercero.PERSONA,
            veces = pagos.size,
            enviado = totalesPorMoneda(pagos.filter { it.tipo == TransactionType.EXPENSE }),
            recibido = totalesPorMoneda(pagos.filter { it.tipo == TransactionType.INCOME }),
            ultimo = ultimo,
            otrosIdentificadores = otros,
            pareceDe = parecido?.id,
            pareceDeNombre = parecido?.nombre,
            renombrables = delMismo.count { it.esMovimiento && it.renombrable },
        )
    }.sortedWith(compareByDescending<DestinoSugerido> { it.veces }.thenByDescending { it.ultimo })
}

private fun totalesPorMoneda(pagos: List<RastroDeTercero>): Map<String, Long> =
    pagos.groupBy { it.moneda }.mapValues { (_, del) -> del.sumOf { it.monto } }.filterValues { it != 0L }

/**
 * **Cada pago una vez.** Primero se juntan los avisos repetidos del mismo pago (SMS y notificación,
 * mismo monto y tipo, ≤ 15 min); después cada movimiento anotado se come el aviso con el que se
 * anotó (mismo monto y tipo, ≤ 36 h, el más cercano). Lo que queda es un pago por elemento.
 */
private fun unPagoUnaVez(rastros: List<RastroDeTercero>): List<RastroDeTercero> {
    val avisos = mutableListOf<RastroDeTercero>()
    rastros.filterNot { it.esMovimiento }.sortedBy { it.timestamp }.forEach { aviso ->
        val repetido = avisos.any {
            it.monto == aviso.monto && it.tipo == aviso.tipo && abs(it.timestamp - aviso.timestamp) <= VENTANA_AVISO_AVISO_MS
        }
        if (!repetido) avisos += aviso
    }
    val sueltos = avisos.toMutableList()
    val movimientos = rastros.filter { it.esMovimiento }
    movimientos.forEach { mov ->
        val suyo = sueltos
            .filter { it.monto == mov.monto && it.tipo == mov.tipo && abs(it.timestamp - mov.timestamp) <= VENTANA_AVISO_MOVIMIENTO_MS }
            .minByOrNull { abs(it.timestamp - mov.timestamp) }
        if (suyo != null) sueltos.remove(suyo)
    }
    return sueltos + movimientos
}

/**
 * **¿Algún tercero guardado parece ser este?** Mismo nombre (sin tildes ni mayúsculas) que su
 * nombre o que uno de sus identificadores-nombre; o el nombre guardado es el comienzo de los de
 * [nombres] palabra por palabra — «Caro» y «Carolina Restrepo Salazar», «Daniel» y «Daniel Leonett».
 * Si se parece a más de uno, a ninguno: la pregunta tiene que tener una sola respuesta.
 */
fun pareceDe(nombres: List<String>, destinos: List<DestinoConocido>): DestinoConocido? {
    val candidatos = nombres.map { normalizarParaBuscar(it) }.filter { it.isNotBlank() }.distinct()
    if (candidatos.isEmpty()) return null
    val parecidos = destinos.filter { d ->
        val suyos = (listOf(d.nombre) + d.todosLosIdentificadores()
            .filter { it.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(it.valor) }.map { it.valor })
            .map { normalizarParaBuscar(it) }
        candidatos.any { c -> suyos.any { it == c } || elNombreEmpiezaIgual(normalizarParaBuscar(d.nombre), c) }
    }
    return parecidos.singleOrNull()
}

/**
 * «caro» frente a «carolina restrepo salazar»: cada palabra del nombre guardado es el comienzo de
 * la palabra que le toca del otro, en orden, y la primera tiene al menos tres letras. Así «Caro»
 * pega con «Carolina …» pero «Ana» no pega con «Mariana …» (no es el comienzo) ni «Jo» con nadie.
 */
private fun elNombreEmpiezaIgual(guardado: String, otro: String): Boolean {
    val suyas = guardado.split(' ').filter { it.isNotEmpty() }
    val otras = otro.split(' ').filter { it.isNotEmpty() }
    if (suyas.isEmpty() || suyas.size > otras.size) return false
    if (suyas.first().length < 3) return false
    return suyas.indices.all { i -> otras[i].startsWith(suyas[i]) }
}

/**
 * **¿Persona o comercio?**, para prellenar la fila «¿De quién es…?» (el dueño lo cambia con un
 * toque). Comercio si el aviso fue un pago por QR, o si la llave tiene la forma de las de QR de
 * Bancolombia (diez dígitos que empiezan en `00`); persona en todo lo demás.
 */
fun tipoProbable(identificador: IdentificadorDelDestino, texto: String? = null): TipoDeTercero = when {
    texto != null && ES_QR.containsMatchIn(texto) -> TipoDeTercero.COMERCIO
    identificador.tipo == TipoDeIdentificador.LLAVE && identificador.valor.length == 10 &&
        identificador.valor.startsWith("00") && identificador.valor.all { it.isDigit() } -> TipoDeTercero.COMERCIO
    else -> TipoDeTercero.PERSONA
}

/** «Persona» / «Comercio», como lo lee el dueño. */
fun TipoDeTercero.comoSeDice(): String = when (this) {
    TipoDeTercero.PERSONA -> "Persona"
    TipoDeTercero.COMERCIO -> "Comercio"
}
