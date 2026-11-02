package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Movi propone repartir (vuelta 97)
 *
 * Hasta acá repartir era un acto voluntario: había que abrir el detalle y tocar «Repartir», y lo que
 * se olvidaba contaba entero como tuyo. Ahora Movi lo propone, con dos señales:
 *
 * 1. **Un recurrente del hogar**: el dueño marcó la regla («Se reparte en Casa con Caro», en el
 *    detalle del recurrente) y llegó un gasto que es ese recurrente ([reglaDelMovimiento]).
 * 2. **Un comercio que ya repartiste**: el mismo comercio ([claveDeComercio]) se repartió al menos
 *    [VECES_PARA_PROPONER_POR_COMERCIO] veces en ese grupo en los últimos
 *    [DIAS_DE_MEMORIA_DEL_REPARTO] días.
 *
 * **Nunca reparte solo.** «Repartir» abre la hoja de siempre, llena con el reparto de la última vez
 * para ese mismo recurrente o comercio; «No es compartido» descarta ese movimiento
 * ([huellaDeNoEsCompartido]); «Nunca para este comercio» guarda la decisión por comercio o recurrente
 * ([huellaDeNuncaRepartir]). Los descartes viven en `anomalias_descartadas`, como los de las otras
 * propuestas.
 *
 * **Umbrales elegidos, no medidos**: 15 días hacia atrás, 2 veces en 90 días, 2 propuestas a la vez.
 * Se eligieron para que la tarjeta aparezca con lo que se repite de verdad (el arriendo, el mercado de
 * siempre) y no con una compra suelta; no hay datos de uso que los respalden todavía.
 */

/** Cuántos días hacia atrás se mira un gasto para proponer repartirlo. Elegido, no medido. */
const val DIAS_PARA_PROPONER_REPARTIR: Int = 15

/** Cuántos días hacia atrás se recuerda lo repartido en un grupo. Elegido, no medido. */
const val DIAS_DE_MEMORIA_DEL_REPARTO: Int = 90

/** Cuántas veces se tuvo que repartir un comercio en un grupo para proponerlo. Elegido, no medido. */
const val VECES_PARA_PROPONER_POR_COMERCIO: Int = 2

/** Cuántas propuestas de repartir se muestran a la vez. Elegido, no medido. */
const val TOPE_DE_PROPUESTAS_DE_REPARTIR: Int = 2

/** Por qué Movi propone repartir un gasto. */
@Serializable
enum class MotivoDeReparto {
    /** Es un recurrente marcado «Se reparte en …». */
    RECURRENTE,

    /** El comercio ya se repartió varias veces en ese grupo. */
    COMERCIO,
}

/**
 * «¿Repartes Arriendo $2.400.000 con Caro? (Casa con Caro, 50/50)». Lo arma [propuestasDeRepartir]
 * en el server (`GET /api/espacios/propuestas-de-reparto`).
 *
 * @param espacio el grupo, con los nombres resueltos: la hoja de repartir lo necesita entero.
 * @param reparto el de la última vez para ese recurrente o comercio (ya llevado a este monto con
 *   [repartoParaOtroMonto]); `null` = la regla del grupo.
 * @param clave lo que «Nunca para este comercio» guarda: `regla:<id>` o `comercio:<clave>`.
 */
@Serializable
data class PropuestaDeRepartir(
    val eventoId: String,
    val descripcion: String,
    val monto: Long,
    val fecha: Long,
    val espacio: Espacio,
    val reparto: RepartoDelGasto? = null,
    val motivo: MotivoDeReparto = MotivoDeReparto.COMERCIO,
    val clave: String = "",
)

/** La huella de «No es compartido»: ese movimiento no se vuelve a proponer. */
fun huellaDeNoEsCompartido(eventoId: String): String = "repartir:$eventoId"

/** La huella de «Nunca para este comercio»: ni ese comercio ni ese recurrente se vuelven a proponer. */
fun huellaDeNuncaRepartir(clave: String): String = "repartir-nunca:$clave"

/** La clave de «Nunca» de un recurrente. */
fun claveDeRepartoDeLaRegla(reglaId: String): String = "regla:$reglaId"

/** La clave de «Nunca» de un comercio. */
fun claveDeRepartoDelComercio(comercio: String): String = "comercio:$comercio"

/** Un gasto del grupo con su movimiento, si es tuyo (lo de «Pagó otro» no tiene). */
data class GastoRepartido(val gasto: GastoDelEspacio, val evento: FinancialEvent? = null)

/**
 * **¿[evento] es el recurrente [regla]?** Un gasto en la misma moneda que se llama igual (el concepto
 * o el comercio, comparados como nombres), o que es de la misma cuenta, categoría y monto exacto: las
 * dos maneras con que Movi ya reconoce un recurrente en un movimiento.
 */
fun esElRecurrente(evento: FinancialEvent, regla: RecurringRule): Boolean {
    if (regla.type != TransactionType.EXPENSE || evento.type != TransactionType.EXPENSE) return false
    if (regla.currency != evento.currency) return false
    val nombre = claveComparableDeNombre(regla.name)
    if (nombre.length >= 3) {
        if (claveComparableDeNombre(evento.description) == nombre) return true
        if (evento.merchant?.let(::claveComparableDeNombre) == nombre) return true
    }
    return regla.accountId != null && regla.accountId == evento.accountId &&
        regla.category == evento.category && regla.amount == evento.amount
}

/** El primer recurrente de [reglas] que es [evento], o `null`. */
fun reglaDelMovimiento(evento: FinancialEvent, reglas: List<RecurringRule>): RecurringRule? =
    reglas.firstOrNull { esElRecurrente(evento, it) }

/** El comercio de un gasto del grupo: el de su movimiento, o el de su descripción si lo pagó otro. */
private fun comercioDe(g: GastoRepartido): String? =
    claveDeComercio(
        g.evento ?: FinancialEvent(
            id = g.gasto.id, accountId = "", type = TransactionType.EXPENSE, amount = g.gasto.monto,
            category = g.gasto.categoria, description = g.gasto.descripcion, timestamp = g.gasto.fecha,
        ),
    )

/**
 * ¿[e] es un gasto que se puede proponer repartir? Tuyo, confirmado, en pesos, de los últimos
 * [DIAS_PARA_PROPONER_REPARTIR] días, que cuenta en «Salió» y no está ya en un grupo ni salda uno.
 */
private fun sePuedeProponer(e: FinancialEvent, desde: Long, ahora: Long): Boolean =
    e.type == TransactionType.EXPENSE && e.amount > 0L && e.currency == "COP" &&
        e.timestamp in desde..ahora &&
        cuentaEnGastosEIngresos(e) &&
        e.espacioId == null && e.saldaEspacioId == null && e.transferId == null &&
        !esTuParteDeOtro(e) && !isOpeningBalance(e) && !isReservedCategory(e.category)

/**
 * **Las propuestas de repartir**, puras: la regla de la vuelta 97 (ver el KDoc del archivo).
 *
 * @param eventos tus movimientos recientes, ya con lo que dicen los grupos (`conLoCompartido`) y sin
 *   anulados.
 * @param repartidos los gastos de tus grupos (con su movimiento si es tuyo), sin anulados.
 * @param delHogar los recurrentes marcados «Se reparte en …»: id de la regla → id del grupo.
 * @param descartadas las huellas de `anomalias_descartadas`.
 * @return a lo sumo [TOPE_DE_PROPUESTAS_DE_REPARTIR]: primero los recurrentes, después el más reciente.
 */
fun propuestasDeRepartir(
    eventos: List<FinancialEvent>,
    repartidos: List<GastoRepartido>,
    espacios: List<Espacio>,
    reglas: List<RecurringRule>,
    delHogar: Map<String, String>,
    descartadas: Set<String>,
    ahora: Long,
): List<PropuestaDeRepartir> {
    if (espacios.isEmpty()) return emptyList()
    val porId = espacios.associateBy { it.id }
    val desde = ahora - DIAS_PARA_PROPONER_REPARTIR * UN_DIA
    val memoria = repartidos.filter { it.gasto.fecha in (ahora - DIAS_DE_MEMORIA_DEL_REPARTO * UN_DIA)..ahora }
    val reglasDelHogar = reglas.filter { it.id in delHogar && delHogar[it.id] in porId }
    return eventos.asSequence()
        .filter { sePuedeProponer(it, desde, ahora) }
        .filterNot { huellaDeNoEsCompartido(it.id) in descartadas }
        .mapNotNull { e ->
            val comercio = claveDeComercio(e)
            if (comercio != null && huellaDeNuncaRepartir(claveDeRepartoDelComercio(comercio)) in descartadas) return@mapNotNull null
            val regla = reglaDelMovimiento(e, reglasDelHogar)
            if (regla != null) {
                val clave = claveDeRepartoDeLaRegla(regla.id)
                if (huellaDeNuncaRepartir(clave) in descartadas) return@mapNotNull null
                val espacio = porId.getValue(delHogar.getValue(regla.id))
                val ultima = memoria.filter { it.gasto.espacioId == espacio.id && it.evento?.let { ev -> esElRecurrente(ev, regla) } == true }
                    .maxByOrNull { it.gasto.fecha }
                    ?: comercio?.let { c -> memoria.filter { it.gasto.espacioId == espacio.id && comercioDe(it) == c }.maxByOrNull { it.gasto.fecha } }
                return@mapNotNull propuesta(e, espacio, ultima, MotivoDeReparto.RECURRENTE, clave)
            }
            comercio ?: return@mapNotNull null
            val delComercio = memoria.filter { comercioDe(it) == comercio }.groupBy { it.gasto.espacioId }
                .filterKeys { it in porId }
                .filterValues { it.size >= VECES_PARA_PROPONER_POR_COMERCIO }
            val (espacioId, suyos) = delComercio.entries
                .maxWithOrNull(compareBy<Map.Entry<String, List<GastoRepartido>>> { it.value.size }.thenBy { g -> g.value.maxOf { it.gasto.fecha } })
                ?.toPair() ?: return@mapNotNull null
            propuesta(e, porId.getValue(espacioId), suyos.maxByOrNull { it.gasto.fecha }, MotivoDeReparto.COMERCIO, claveDeRepartoDelComercio(comercio))
        }
        .sortedWith(compareBy<PropuestaDeRepartir> { if (it.motivo == MotivoDeReparto.RECURRENTE) 0 else 1 }.thenByDescending { it.fecha })
        .take(TOPE_DE_PROPUESTAS_DE_REPARTIR)
        .toList()
}

private fun propuesta(
    e: FinancialEvent,
    espacio: Espacio,
    ultima: GastoRepartido?,
    motivo: MotivoDeReparto,
    clave: String,
): PropuestaDeRepartir = PropuestaDeRepartir(
    eventoId = e.id,
    descripcion = e.description,
    monto = e.amount,
    fecha = e.timestamp,
    espacio = espacio,
    // La última vez que se repartió así: con montos exactos, llevados a este monto.
    reparto = repartoParaOtroMonto(ultima?.gasto?.reparto?.takeIf { !esDelGrupo(it) }, e.amount, espacio),
    motivo = motivo,
    clave = clave,
)

private const val UN_DIA: Long = 24L * 60 * 60 * 1000

/**
 * Un recurrente del hogar: la regla [reglaId] se reparte en el grupo [espacioId] («Se reparte en Casa
 * con Caro», en el detalle del recurrente). `GET /api/espacios/recurrentes-del-hogar` y
 * `PUT`/`DELETE /api/espacios/{espacioId}/recurrentes/{reglaId}`.
 */
@Serializable
data class RecurrenteDelHogar(val reglaId: String, val espacioId: String)
