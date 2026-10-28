package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Repartir un envío entre varios pagos fijos
 *
 * El caso del papá: «a la cuenta *036200170136 $4.280.000» es exactamente la cuota de la Libranza
 * Papá (BBVA, $2.051.848) más la de la Hipoteca Papá (Davibank, $2.228.152). Pasa unas tres veces
 * al mes y terminaba como cuotas a mano —dos traspasos armados por SQL el 27-sep—, porque Movi no
 * repartía un movimiento entre dos pagos (a propósito: un movimiento salda UN pago).
 *
 * Esto no cambia esa regla: **repartir crea un movimiento por pago**, cada uno con su categoría y su
 * nota, y cada pago queda saldado por el suyo. Una cuota de un crédito lleva sus dos patas (la plata
 * que sale y el abono a la deuda); un recurrente común, su movimiento y el sello de su período.
 *
 * Cuándo se propone solo: el aviso es una salida hacia un tercero que el dueño guardó, y su monto es
 * **exactamente** la suma de 2 o 3 pagos del período, pendientes, que el dueño asoció a ese tercero
 * (`RecurringRule.destinoConocidoId` y `CreditTerms.destinoConocidoId`). Si la suma no es exacta, no
 * se propone nada: se ofrece «Repartir…» a mano, el dueño elige los pagos y el último toma el resto.
 */

/**
 * **Un pago del período que un envío puede saldar.**
 *
 * @property ruleId la regla del pago: una regla real, o la sintética de un crédito (`credit:<cuenta>`).
 * @property periodo el período del sello (`YYYY-MM`, el mes del vencimiento).
 * @property cuentaDeLaDeudaId la cuenta LOAN del crédito, si el pago es una cuota: su parte lleva dos patas.
 * @property asociado el pago va al tercero del aviso (el dueño lo asoció a él).
 */
@Serializable
data class PagoParaRepartir(
    val ruleId: String,
    val nombre: String,
    val monto: Long,
    val categoria: String,
    val periodo: String,
    val vence: String,
    val cuentaDeLaDeudaId: String? = null,
    val asociado: Boolean = false,
)

/**
 * La respuesta de `GET /api/sms/{id}/reparto`.
 *
 * @property monto lo que dice el aviso (de un pago avisado varias veces, el del aviso que más dice).
 * @property destinoId el tercero al que fue, si el dueño lo tiene guardado.
 * @property pagos los pagos del período que se pueden elegir a mano, primero los asociados al tercero.
 *   Vacía si el aviso no es una salida hacia alguien: entonces no se ofrece repartir.
 * @property exacto los `ruleId` de la única combinación de 2 o 3 pagos asociados que suma
 *   **exactamente** [monto] ([repartoExacto]); vacía si no hay ninguna o hay más de una.
 */
@Serializable
data class PropuestaDeReparto(
    val avisoId: String,
    val monto: Long = 0L,
    val moneda: String = "COP",
    val destinoId: String? = null,
    val destinoNombre: String? = null,
    val pagos: List<PagoParaRepartir> = emptyList(),
    val exacto: List<String> = emptyList(),
    /**
     * **El reparto de la vez anterior** a esta misma persona y desde una cuenta (vuelta 27, ver
     * `ElAvisoEnPartes.kt`): la app lo propone si el monto es parecido. `null` si nunca se repartió.
     * Un APK viejo lo ignora.
     */
    val recordado: RepartoRecordado? = null,
)

/**
 * **Una parte del reparto**, como viaja en [ConfirmarElMismoPago.reparto] y en [PartesDelAviso]: el
 * pago que salda, cuánto, y el movimiento que la anota — [evento] para un gasto (o un recurrente
 * común), [patas] para la cuota de un crédito, el pago de una tarjeta o un traspaso. Los ids los
 * genera el cliente, como en un traspaso: un reintento no crea nada nuevo.
 *
 * **Vuelta 27: una parte suelta** (ver `ElAvisoEnPartes.kt`). Con [ruleId] vacío la parte no salda
 * ningún pago del período por nombre: es lo que el dueño eligió en «Repartir en partes» (una cuota que
 * igual marca su período por la pata de la deuda, el pago de una tarjeta, un traspaso, un gasto o una
 * transferencia a un tercero). [tipo] dice cuál, para que el server lo mire y lo recuerde; un APK
 * viejo no lo manda y todo sigue como antes.
 */
@Serializable
data class ParteDelReparto(
    val ruleId: String = "",
    val periodo: String = "",
    val monto: Long,
    val evento: FinancialEvent? = null,
    val patas: DosPatasDelAviso? = null,
    val tipo: QueFueLaParte? = null,
)

/** Cuántos pagos se combinan, como mucho, para proponer una suma exacta. */
const val MAXIMO_DE_PAGOS_EN_LA_SUMA: Int = 3

/** Cuántas partes acepta un reparto a mano. */
const val MAXIMO_DE_PARTES: Int = 6

/**
 * **La suma exacta**, o `null`: entre los pagos [asociados][PagoParaRepartir.asociado], la combinación
 * de 2 o 3 cuyo monto suma **exactamente** [monto]. Si hay más de una, tampoco: con dos maneras de
 * explicar el mismo envío, elegir una sería adivinar, y eso lo decide el dueño a mano.
 */
fun repartoExacto(monto: Long, pagos: List<PagoParaRepartir>): List<PagoParaRepartir>? {
    if (monto <= 0L) return null
    val asociados = pagos.filter { it.asociado && it.monto > 0L }.distinctBy { it.ruleId }
    val encontradas = mutableListOf<List<PagoParaRepartir>>()
    fun buscar(desde: Int, elegidos: List<PagoParaRepartir>, suma: Long) {
        if (elegidos.size >= 2 && suma == monto) encontradas += elegidos
        if (elegidos.size == MAXIMO_DE_PAGOS_EN_LA_SUMA || suma >= monto) return
        for (i in desde until asociados.size) buscar(i + 1, elegidos + asociados[i], suma + asociados[i].monto)
    }
    buscar(0, emptyList(), 0L)
    return encontradas.singleOrNull()
}

/**
 * **El reparto a mano**: cada pago elegido toma su monto, en el orden en que se eligieron, y **el
 * último toma el resto**. `null` si así no alcanza (el resto quedaría en cero o negativo) o no hay
 * ninguno elegido.
 */
fun repartoAMano(monto: Long, elegidos: List<PagoParaRepartir>): List<Pair<PagoParaRepartir, Long>>? {
    if (elegidos.isEmpty() || monto <= 0L) return null
    val antes = elegidos.dropLast(1)
    val resto = monto - antes.sumOf { it.monto }
    if (resto <= 0L) return null
    return antes.map { it to it.monto } + (elegidos.last() to resto)
}

/** Lo que dice el server cuando las partes no suman lo que dice el aviso. */
const val EL_REPARTO_NO_SUMA: String = "Las partes no suman lo que dice el aviso. Revisa los montos."
