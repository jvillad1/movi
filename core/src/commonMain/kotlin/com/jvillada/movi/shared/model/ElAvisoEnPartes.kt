package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Un aviso que pagó varias cosas: repartirlo en partes
 *
 * Vuelta 27, pedido del dueño (27-oct): «el movimiento que acabo de hacer corresponde al pago de 2
 * créditos de mi padre … al revisar el movimiento no lo puedo dividir». Cada mes manda UN giro a la
 * cuenta del papá que cubre la cuota de la Libranza y la de la Hipoteca, y sobra un poco.
 *
 * Hasta acá un aviso se confirmaba como UNA operación ([DosPatasDelAviso]) o, desde #447, se
 * repartía entre pagos del período cuya suma era **exacta** (`RepartirUnEnvio.kt`). Esto lo
 * generaliza sin cambiar cómo se escribe:
 *
 * - **Cada parte es una de las operaciones que el aviso ya sabe confirmar** ([QueFueLaParte]): la
 *   cuota de un crédito suyo, el pago de una tarjeta suya, un traspaso a una cuenta suya (las tres
 *   con sus dos patas, por la MISMA `escribirLasPatasDelAviso` del server), un gasto con su
 *   categoría o una transferencia a un tercero (un gasto con el nombre del tercero y el texto del
 *   aviso, que es lo que lo liga a «Cuentas de otros»).
 * - **Las partes suman exactamente el aviso**, en su moneda, y salen todas de la misma cuenta. La
 *   app lo dice en vivo ([cuadreDeLasPartes]) y el server lo vuelve a mirar (422 si no).
 * - **Nada se confirma solo.** Movi propone un reparto de entrada ([repartoDeEntrada]) —el de la
 *   última vez, la suma exacta de pagos del período, o las cuotas pendientes con lo que sobra— y el
 *   dueño lo revisa, lo ajusta y toca «Confirmar las N partes».
 */

/** **Qué fue cada parte de un aviso repartido.** Las tres primeras llevan dos patas; las dos últimas, un gasto. */
@Serializable
enum class QueFueLaParte {
    /** La cuota de un crédito suyo: «Cuota de crédito», la deuda baja por el capital y el período queda marcado. */
    CUOTA,

    /** El pago de una tarjeta suya. */
    PAGO_DE_TARJETA,

    /** Un traspaso a otra cuenta suya de dinero o inversión. */
    TRASPASO,

    /** Un gasto con su categoría. */
    GASTO,

    /** Una transferencia a un tercero (cuentas de otros): un gasto con su nombre, «Transferencia a Papá». */
    A_UN_TERCERO,
}

/** Las partes que se escriben con dos patas, y con qué operación del aviso. */
val QueFueLaParte.operacion: OperacionDelAviso?
    get() = when (this) {
        QueFueLaParte.CUOTA -> OperacionDelAviso.CUOTA
        QueFueLaParte.PAGO_DE_TARJETA -> OperacionDelAviso.PAGO_DE_TARJETA
        QueFueLaParte.TRASPASO -> OperacionDelAviso.TRASPASO
        QueFueLaParte.GASTO, QueFueLaParte.A_UN_TERCERO -> null
    }

/** La parte que corresponde a una operación de dos patas; `null` para el avance, que no se reparte. */
fun queFueLaOperacion(operacion: OperacionDelAviso): QueFueLaParte? = when (operacion) {
    OperacionDelAviso.CUOTA -> QueFueLaParte.CUOTA
    OperacionDelAviso.PAGO_DE_TARJETA -> QueFueLaParte.PAGO_DE_TARJETA
    OperacionDelAviso.TRASPASO -> QueFueLaParte.TRASPASO
    OperacionDelAviso.AVANCE -> null
}

/**
 * **Una parte como la arma el dueño** (o como la propone Movi): lo que la app edita, lo que Movi
 * recuerda del reparto anterior ([RepartoRecordado]) y lo que [aParteDelReparto] convierte en lo que
 * viaja al server.
 *
 * @property destinoId la cuenta suya a la que entra (cuota, tarjeta, traspaso), o el tercero guardado
 *   en una transferencia a un tercero (opcional: sirve solo para recordarlo).
 * @property categoria la del gasto (o la de la transferencia a un tercero).
 * @property nombre el texto del movimiento: el concepto de un gasto, la nota de una cuota, y en una
 *   transferencia a un tercero el texto entero, «Transferencia a Papá».
 * @property ruleId / [periodo] el pago del período que salda, si la parte salió de uno (la cuota de un
 *   crédito, `credit_<cuenta>`, o un recurrente común, que además queda sellado en su período).
 */
@Serializable
data class ParteElegida(
    val tipo: QueFueLaParte,
    val destinoId: String? = null,
    val categoria: String? = null,
    val nombre: String? = null,
    val monto: Long = 0L,
    val ruleId: String = "",
    val periodo: String = "",
)

/** ¿Esta parte ya dice todo lo que hace falta para escribirla? (El monto, y a dónde fue o en qué.) */
val ParteElegida.estaCompleta: Boolean
    get() = monto > 0L && when (tipo) {
        QueFueLaParte.CUOTA, QueFueLaParte.PAGO_DE_TARJETA, QueFueLaParte.TRASPASO -> !destinoId.isNullOrBlank()
        QueFueLaParte.GASTO -> !categoria.isNullOrBlank()
        QueFueLaParte.A_UN_TERCERO -> !nombre.isNullOrBlank() && !categoria.isNullOrBlank()
    }

/** Cuántas partes acepta un aviso repartido, como mucho. */
const val MAXIMO_DE_PARTES_DEL_AVISO: Int = 8

/**
 * **El cuerpo de `POST /api/sms/{id}/confirm` con varias partes.** El de una sola sigue siendo
 * [DosPatasDelAviso], igual que antes: el server mira si llegó `partes` y, si no, lee la forma vieja.
 */
@Serializable
data class PartesDelAviso(
    val partes: List<ParteDelReparto>,
    val recordarDestinoId: String? = null,
)

// ── Faltan / te pasaste ───────────────────────────────────────────────────────────────────────

/** **Cómo van las partes contra el total del aviso**, para la línea en vivo de «Repartir en partes». */
sealed interface CuadreDeLasPartes {
    data object Cuadra : CuadreDeLasPartes
    data class Faltan(val monto: Long) : CuadreDeLasPartes
    data class TePasaste(val monto: Long) : CuadreDeLasPartes
}

/** Las partes contra el aviso: cuadra solo si suman **exactamente** [total]. */
fun cuadreDeLasPartes(total: Long, montos: List<Long>): CuadreDeLasPartes {
    val suma = montos.sumOf { it.coerceAtLeast(0L) }
    return when {
        suma == total -> CuadreDeLasPartes.Cuadra
        suma < total -> CuadreDeLasPartes.Faltan(total - suma)
        else -> CuadreDeLasPartes.TePasaste(suma - total)
    }
}

/**
 * **¿Se pueden confirmar estas partes?** Dos o más, todas completas, y la suma exacta. Es la regla
 * del botón «Confirmar las N partes» (el server la vuelve a mirar).
 */
fun sePuedenConfirmar(total: Long, partes: List<ParteElegida>): Boolean =
    partes.size in 2..MAXIMO_DE_PARTES_DEL_AVISO && partes.all { it.estaCompleta } &&
        cuadreDeLasPartes(total, partes.map { it.monto }) == CuadreDeLasPartes.Cuadra

/**
 * **«El resto como…»**: lo que falta, convertido en una parte más de [tipo]. `null` si no falta nada.
 * Si la última parte ya es de ese tipo y apunta a lo mismo, se le suma en vez de agregar otra.
 */
fun conElResto(total: Long, partes: List<ParteElegida>, resto: ParteElegida): List<ParteElegida>? {
    val falta = (cuadreDeLasPartes(total, partes.map { it.monto }) as? CuadreDeLasPartes.Faltan)?.monto ?: return null
    val ultima = partes.lastOrNull()
    if (ultima != null && ultima.copy(monto = 0L) == resto.copy(monto = 0L)) {
        return partes.dropLast(1) + ultima.copy(monto = ultima.monto + falta)
    }
    return partes + resto.copy(monto = falta)
}

// ── La propuesta de entrada ───────────────────────────────────────────────────────────────────

/**
 * **Cuánto puede sobrar, como mucho, para que Movi proponga solo las cuotas de un giro**: el 5 % del
 * aviso. Elegido, no medido: el giro del dueño cubre dos cuotas y sobra menos del 1 %; con más de un
 * 5 % de sobra, que esas cuotas expliquen el giro ya es adivinar.
 */
const val SOBRANTE_MAXIMO_PORCIENTO: Long = 5L

/**
 * **Cuánto puede cambiar el monto para que se proponga el reparto de la última vez**: un 10 % arriba
 * o abajo. Elegido, no medido (la memoria de categorías no mira montos): el giro al papá se repite con
 * el mismo monto o casi, y una diferencia mayor ya es otro pago.
 */
const val MONTO_PARECIDO_PORCIENTO: Long = 10L

/** Cuántas cuotas se combinan, como mucho, para proponer un giro que las cubre. */
const val MAXIMO_DE_CUOTAS_EN_LA_PROPUESTA: Int = 4

/** ¿[a] y [b] son el mismo monto, con la tolerancia de [MONTO_PARECIDO_PORCIENTO]? */
fun montosParecidos(a: Long, b: Long): Boolean {
    if (a <= 0L || b <= 0L) return false
    val diferencia = if (a > b) a - b else b - a
    return diferencia * 100 <= maxOf(a, b) * MONTO_PARECIDO_PORCIENTO
}

/**
 * **El reparto de la vez anterior**: el server lo guarda en el aviso mismo (`sms_messages.reparto`) al
 * confirmarlo en partes, y «la última vez» es el último aviso repartido a la misma persona
 * ([claveDelReparto]). Viaja en [PropuestaDeReparto.recordado]; la app lo propone solo si sale de la
 * misma cuenta ([origenId]) y por un monto parecido.
 */
@Serializable
data class RepartoRecordado(
    val origenId: String,
    val monto: Long,
    val moneda: String = "COP",
    val partes: List<ParteElegida>,
)

/**
 * **Las cuotas que un giro cubre, con lo que sobra.** Entre los pagos del período que son cuotas de un
 * crédito ([PagoParaRepartir.cuentaDeLaDeudaId]), la combinación de 2 a [MAXIMO_DE_CUOTAS_EN_LA_PROPUESTA]
 * cuya suma no pasa de [monto] y deja la menor sobra, siempre que sobre a lo sumo el
 * [SOBRANTE_MAXIMO_PORCIENTO]. Primero entre las asociadas al tercero del aviso; si ninguna combinación
 * sirve, entre todas. Dos combinaciones con la misma sobra: ninguna (elegir sería adivinar).
 */
fun cuotasQueCubre(monto: Long, pagos: List<PagoParaRepartir>): List<PagoParaRepartir>? {
    if (monto <= 0L) return null
    val cuotas = pagos.filter { it.cuentaDeLaDeudaId != null && it.monto > 0L }.distinctBy { it.ruleId }
    fun buscarEntre(candidatas: List<PagoParaRepartir>): List<PagoParaRepartir>? {
        var mejor: List<PagoParaRepartir>? = null
        var mejorSobra = Long.MAX_VALUE
        var empate = false
        fun buscar(desde: Int, elegidas: List<PagoParaRepartir>, suma: Long) {
            if (suma > monto) return
            if (elegidas.size >= 2) {
                val sobra = monto - suma
                if (sobra * 100 <= monto * SOBRANTE_MAXIMO_PORCIENTO) {
                    when {
                        sobra < mejorSobra -> { mejor = elegidas; mejorSobra = sobra; empate = false }
                        sobra == mejorSobra -> empate = true
                    }
                }
            }
            if (elegidas.size == MAXIMO_DE_CUOTAS_EN_LA_PROPUESTA) return
            for (i in desde until candidatas.size) buscar(i + 1, elegidas + candidatas[i], suma + candidatas[i].monto)
        }
        buscar(0, emptyList(), 0L)
        return if (empate) null else mejor
    }
    return buscarEntre(cuotas.filter { it.asociado }) ?: buscarEntre(cuotas)
}

/** De dónde sale la propuesta de entrada: lo dice la tarjeta. */
enum class DeDondeSaleElReparto { LA_ULTIMA_VEZ, SUMA_EXACTA, LAS_CUOTAS }

/**
 * **El reparto que Movi propone de entrada**, para que el dueño lo revise. [partes] ya suman el aviso;
 * [pagos] son los pagos del período que explica (para la pregunta) y [sobrante] lo que quedó como
 * parte aparte (cero si no sobró nada).
 */
data class RepartoDeEntrada(
    val deDonde: DeDondeSaleElReparto,
    val partes: List<ParteElegida>,
    val pagos: List<PagoParaRepartir> = emptyList(),
    val sobrante: Long = 0L,
)

/** La parte que salda [pago]: la cuota de su crédito, o un gasto con su categoría y su nombre. */
fun parteDelPago(pago: PagoParaRepartir, monto: Long = pago.monto): ParteElegida {
    val deuda = pago.cuentaDeLaDeudaId
    return if (deuda != null) {
        ParteElegida(QueFueLaParte.CUOTA, destinoId = deuda, nombre = pago.nombre, monto = monto, ruleId = pago.ruleId, periodo = pago.periodo)
    } else {
        ParteElegida(QueFueLaParte.GASTO, categoria = pago.categoria, nombre = pago.nombre, monto = monto, ruleId = pago.ruleId, periodo = pago.periodo)
    }
}

/**
 * **La parte que se lleva lo que sobra**: a la persona del aviso si Movi la conoce o el aviso dice un
 * nombre ([nombreDelTercero], ya como «Transferencia a Papá»), y si no un gasto con la categoría que
 * Movi leyó (que el dueño puede cambiar).
 */
fun parteDelSobrante(monto: Long, nombreDelTercero: String?, destinoId: String?, categoria: String?): ParteElegida =
    if (!nombreDelTercero.isNullOrBlank()) {
        ParteElegida(QueFueLaParte.A_UN_TERCERO, destinoId = destinoId, categoria = categoria, nombre = nombreDelTercero, monto = monto)
    } else {
        ParteElegida(QueFueLaParte.GASTO, categoria = categoria, monto = monto)
    }

/**
 * **El reparto de la última vez, llevado al aviso de hoy**: mismas partes y mismos montos, y la
 * última toma la diferencia. `null` si con la diferencia la última quedaría en cero o menos. El pago
 * del período de cada parte se busca otra vez entre los de hoy ([pagos]): uno que ya no está pendiente
 * deja la parte sin pago (se anota igual; el período se marca solo si es una cuota).
 */
fun repartoComoLaUltimaVez(recordado: RepartoRecordado, monto: Long, pagos: List<PagoParaRepartir>): List<ParteElegida>? {
    if (recordado.partes.size < 2) return null
    val antes = recordado.partes.dropLast(1)
    val resto = monto - antes.sumOf { it.monto }
    if (resto <= 0L) return null
    return (antes + recordado.partes.last().copy(monto = resto)).map { parte ->
        if (parte.ruleId.isBlank()) parte
        else pagos.firstOrNull { it.ruleId == parte.ruleId }?.let { parte.copy(periodo = it.periodo) }
            ?: parte.copy(ruleId = "", periodo = "")
    }
}

/**
 * **El reparto que Movi propone de entrada**, en este orden:
 *
 * 1. **El de la última vez** ([PropuestaDeReparto.recordado]), si salió de la misma cuenta
 *    ([origenId]; `null` = no se sabe todavía, no descarta) y por un monto parecido ([montosParecidos]).
 * 2. **La suma exacta** de pagos del período asociados al tercero ([PropuestaDeReparto.exacto], #447).
 * 3. **Las cuotas que el giro cubre, con lo que sobra** ([cuotasQueCubre]): cada cuota con su monto y
 *    lo que sobra como [parteDelSobrante] (a [nombreDelTercero] si lo hay).
 *
 * `null` si nada de eso aplica. **Es una propuesta**: nada se confirma sin que el dueño toque.
 */
fun repartoDeEntrada(
    propuesta: PropuestaDeReparto,
    origenId: String?,
    nombreDelTercero: String?,
    categoriaDelAviso: String?,
): RepartoDeEntrada? {
    val monto = propuesta.monto
    if (monto <= 0L) return null
    propuesta.recordado
        ?.takeIf { (origenId == null || it.origenId == origenId) && it.moneda == propuesta.moneda && montosParecidos(it.monto, monto) }
        ?.let { recordado -> repartoComoLaUltimaVez(recordado, monto, propuesta.pagos) }
        ?.let { return RepartoDeEntrada(DeDondeSaleElReparto.LA_ULTIMA_VEZ, it) }

    val exactos = propuesta.exacto.mapNotNull { id -> propuesta.pagos.firstOrNull { it.ruleId == id } }
    if (exactos.size >= 2 && exactos.sumOf { it.monto } == monto) {
        return RepartoDeEntrada(DeDondeSaleElReparto.SUMA_EXACTA, exactos.map { parteDelPago(it) }, pagos = exactos)
    }

    val cuotas = cuotasQueCubre(monto, propuesta.pagos) ?: return null
    val sobra = monto - cuotas.sumOf { it.monto }
    val partes = cuotas.map { parteDelPago(it) } +
        if (sobra > 0L) listOf(parteDelSobrante(sobra, nombreDelTercero, propuesta.destinoId, categoriaDelAviso)) else emptyList()
    return RepartoDeEntrada(
        if (sobra == 0L) DeDondeSaleElReparto.SUMA_EXACTA else DeDondeSaleElReparto.LAS_CUOTAS,
        partes,
        pagos = cuotas,
        sobrante = sobra,
    )
}

/** «Cuota Libranza Papá (BBVA)» → «Libranza Papá (BBVA)»: en «las cuotas de …» el «Cuota» sobra. */
fun nombreSinCuota(nombre: String): String {
    val sin = nombre.trim().removePrefix("Cuota de ").removePrefix("Cuota ")
    return sin.ifBlank { nombre }
}

/** «A y B», «A, B y C». */
fun enumerar(nombres: List<String>): String = when (nombres.size) {
    0 -> ""
    1 -> nombres.single()
    else -> nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
}

// ── Lo que viaja al server ────────────────────────────────────────────────────────────────────

/**
 * **La parte que viaja al server** ([ParteDelReparto]), con ids nuevos: un reintento con los mismos
 * ids no duplica nada. Todas salen de [origen], en el momento del aviso y con su texto
 * ([textoDelAviso]), que en una transferencia a un tercero es lo que la liga a su número.
 *
 * - Cuota, tarjeta, traspaso: [DosPatasDelAviso] con su operación (las escribe la misma
 *   `escribirLasPatasDelAviso` de un aviso suelto).
 * - Gasto y transferencia a un tercero: un [FinancialEvent] EXPENSE con su categoría.
 */
fun aParteDelReparto(
    elegida: ParteElegida,
    origen: Account,
    momento: Long,
    textoDelAviso: String,
    comercio: String?,
    fuente: EventSource,
    nuevoId: (String) -> String,
): ParteDelReparto {
    val operacion = elegida.tipo.operacion
    if (operacion != null) {
        return ParteDelReparto(
            ruleId = elegida.ruleId,
            periodo = elegida.periodo,
            monto = elegida.monto,
            tipo = elegida.tipo,
            patas = DosPatasDelAviso(
                operacion = operacion,
                origenId = origen.id,
                destinoId = elegida.destinoId.orEmpty(),
                monto = elegida.monto,
                timestamp = momento,
                transferId = nuevoId("tr"),
                origenEventId = nuevoId("ev"),
                destinoEventId = nuevoId("ev"),
                nota = elegida.nombre?.takeIf { it.isNotBlank() },
            ),
        )
    }
    val esDelTercero = elegida.tipo == QueFueLaParte.A_UN_TERCERO
    val categoria = elegida.categoria.orEmpty()
    return ParteDelReparto(
        ruleId = elegida.ruleId,
        periodo = elegida.periodo,
        monto = elegida.monto,
        tipo = elegida.tipo,
        evento = FinancialEvent(
            id = nuevoId("ev"),
            accountId = origen.id,
            type = TransactionType.EXPENSE,
            amount = elegida.monto,
            currency = origen.currency,
            category = categoria,
            description = elegida.nombre?.takeIf { it.isNotBlank() } ?: categoria,
            merchant = if (esDelTercero) elegida.nombre else comercio,
            timestamp = momento,
            source = fuente,
            rawPayload = textoDelAviso.ifBlank { null },
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        ),
    )
}

/**
 * **Lo que Movi recuerda de una parte ya escrita**: el inverso de [aParteDelReparto], para guardar el
 * reparto en `repartos_recordados`. Sin el período (el mes que viene es otro).
 */
fun parteParaRecordar(parte: ParteDelReparto): ParteElegida? {
    val patas = parte.patas
    if (patas != null) {
        val tipo = queFueLaOperacion(patas.operacion) ?: return null
        return ParteElegida(tipo, destinoId = patas.destinoId, nombre = patas.nota, monto = parte.monto, ruleId = parte.ruleId)
    }
    val evento = parte.evento ?: return null
    val tipo = if (parte.tipo == QueFueLaParte.A_UN_TERCERO) QueFueLaParte.A_UN_TERCERO else QueFueLaParte.GASTO
    return ParteElegida(
        tipo,
        categoria = evento.category,
        nombre = if (tipo == QueFueLaParte.A_UN_TERCERO) evento.merchant ?: evento.description else evento.description,
        monto = parte.monto,
        ruleId = parte.ruleId,
    )
}

/**
 * **A quién fue el aviso, como clave de la memoria del reparto**: el tercero guardado que nombra, o el
 * número / la llave que dice el texto. `null` si no nombra a nadie: sin a quién, no hay «la misma vez».
 */
fun claveDelReparto(texto: String, destinos: List<DestinoConocido>): String? =
    destinoQueNombra(texto, destinos)?.let { "tercero:${it.id}" }
        ?: identificadorDelDestinoEn(texto)?.let { "${it.tipo.name.lowercase()}:${it.valor}" }
