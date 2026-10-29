package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # «¿A cuál crédito o tarjeta corresponde?» — completar el traspaso solo
 *
 * **El hallazgo (2026-09-27, cuatro veces el mismo día: Crédito Mamá, Hipoteca Papá, Libranza
 * Papá, y las dos Master Black):** el dueño confirma un SMS de traspaso con la categoría correcta
 * ([CUOTA_CATEGORY] o [CARD_PAYMENT_CATEGORY]), pero el movimiento queda como un GASTO SUELTO —
 * nunca el traspaso de dos patas que `PagosDeDeuda.kt` (server) exige para marcar la deuda pagada.
 * Cada vez había que corregirlo a mano por SQL.
 *
 * ### La forma del arreglo
 *
 * No se bloquea nada nuevo: elegir esas dos categorías para un gasto suelto sigue guardando el
 * gasto suelto de siempre. Lo que se agrega es un paso **opcional**, ofrecido en el mismo
 * instante en que el dueño elige la categoría —en la hoja de reconciliar un SMS y en el editor de
 * un movimiento ya guardado—: *«¿A cuál crédito o tarjeta corresponde?»*, con sus cuentas
 * LOAN/CREDIT_CARD. Si elige una, `PUT /api/events/{id}/vincular-deuda` arma las dos patas
 * reusando [pagoDeCuotaLegs] — la MISMA fórmula que ya reparte una cuota entre interés y capital,
 * y que ya sabe pagar una tarjeta por el monto completo. Si no elige ninguna, no pasa nada: el
 * gasto suelto queda como está.
 *
 * [ofreceVincularDeuda] es la función pura que decide cuándo mostrar el paso — la usan tanto la
 * hoja de SMS como el editor del movimiento, para que las dos pantallas ofrezcan el paso en
 * exactamente los mismos casos.
 */

/**
 * ¿Hay que ofrecerle al dueño «¿A cuál crédito o tarjeta corresponde?» para este movimiento?
 *
 * Las tres condiciones son necesarias, y cada una cierra una puerta distinta — las mismas tres
 * que [com.jvillada.movi.shared.model] usa del lado del server para leer un pago ya vinculado
 * (ver el KDoc de `PagosDeDeuda.kt`, del que esta función es la mitad simétrica: allí se LEE que
 * una cuota quedó pagada, acá se OFRECE armarla):
 *
 *  - **La categoría.** Solo [CUOTA_CATEGORY] o [CARD_PAYMENT_CATEGORY]: son las dos categorías que
 *    [pagoDeCuotaLegs] escribe, y las únicas que un pago de deuda puede llevar.
 *  - **EXPENSE.** Un traspaso de deuda siempre sale de una cuenta de dinero — nunca tiene sentido
 *    ofrecer esto sobre un INCOME.
 *  - **`transferId == null`.** Un movimiento que YA es la mitad de un traspaso no necesita este
 *    paso — ofrecerlo ahí sería una segunda forma de armar lo que ya existe.
 */
fun ofreceVincularDeuda(type: TransactionType, category: String, transferId: String?): Boolean =
    type == TransactionType.EXPENSE &&
        transferId == null &&
        (category == CUOTA_CATEGORY || category == CARD_PAYMENT_CATEGORY)

/**
 * El cuerpo de `PUT /api/events/{id}/vincular-deuda`: completar un gasto suelto ya categorizado
 * como [CUOTA_CATEGORY] o [CARD_PAYMENT_CATEGORY] en el traspaso completo que le falta.
 *
 * El movimiento a completar es el `{id}` de la ruta, no viaja en el cuerpo. Lo que sí viaja:
 *
 * @param debtAccountId la cuenta LOAN o CREDIT_CARD elegida. El server decide sola, mirando su
 *   tipo, cuánto de este pago baja capital ([pagoDeCuotaLegs]/[desglosarCuotaRegistrada]) — el
 *   dueño no elige eso, solo elige LA CUENTA.
 * @param transferId / @param toEventId los ids que enlazan las dos patas, generados por el
 *   CLIENTE — mismo criterio que [CreatePagoDeCuotaRequest]: hacen la operación idempotente si la
 *   petición se reintenta. `transferId` pasa a ser también el `transferId` del movimiento
 *   `{id}` que ya existía.
 * @param interesReal ver [CreatePagoDeCuotaRequest.interesReal]: el mismo campo opcional, mismo
 *   contrato — `null` es «estímalo».
 *
 * **No lleva `montoEnLaMonedaDeLaDeuda`, a propósito.** En [CreatePagoDeCuotaRequest] ese campo
 * existe porque el dueño está anotando el pago A MANO y sabe qué tipo de cambio aplicó el banco.
 * Acá el movimiento YA está anotado —vino de un SMS o de una edición— y nadie le va a preguntar
 * una segunda cifra: si las monedas no coinciden (tarjeta en dólares pagada en pesos), el server
 * la pide sola a `FxRateService`/`TasaUsdCop` con la TRM del día. Ver `VincularPagoDeDeudaRoutes.kt`.
 */
@Serializable
data class VincularPagoDeDeudaRequest(
    val debtAccountId: String,
    val transferId: String,
    val toEventId: String,
    val interesReal: Long? = null,
    /**
     * Vuelta 51 · **«Es la cuota de un crédito»**: el dueño dijo, desde el detalle, que este gasto suelto
     * es la cuota del crédito [debtAccountId]. Antes eso se hacía eligiendo «Cuota de crédito» en el
     * selector y después vinculando; desde que esa categoría no se elige a mano, esta marca es la que
     * deja vincular un gasto con otra categoría. Solo vale con un crédito (LOAN) y sobre un gasto cuya
     * categoría no sea una de las que escribe Movi; el resto es lo de siempre: las mismas dos patas
     * ([pagoDeCuotaLegs]), la pata del dinero pasa a [CUOTA_CATEGORY]. Default `false`: un cliente viejo
     * sigue igual.
     */
    val esLaCuota: Boolean = false,
    /**
     * Vuelta 53 · **«Un abono extra»**: el dueño dijo que este gasto suelto no es la cuota sino plata de
     * más contra el capital del crédito [debtAccountId]. El server arma **el traspaso de siempre**
     * ([transferLegsFor], el mismo del abono extraordinario de Agregar): la pata del dinero pasa a
     * [TRANSFER_CATEGORY] y entra un INCOME por el monto entero al crédito. No salda ninguna cuota
     * (`PagosDeDeuda.kt` solo lee [CUOTA_CATEGORY]). Solo con un crédito. Default `false`.
     */
    val comoAbono: Boolean = false,
)

/**
 * Vuelta 53 · el código del 409 de `vincular-deuda` cuando el dueño dice «es la cuota» ([VincularPagoDeDeudaRequest.esLaCuota])
 * de un crédito cuya cuota del período donde caería este pago **ya la saldó otro movimiento**. El
 * cliente no la vincula en silencio: pregunta si es un abono extra.
 */
const val VINCULO_CUOTA_YA_PAGADA: String = "CUOTA_YA_PAGADA"

/**
 * El cuerpo de ese 409. [periodo] es el mes de la cuota ya saldada (`"2026-10"`, el `periodOf` de su
 * vencimiento); [mensaje] la frase entera, para un cliente que no conozca el código (un APK de la
 * vuelta 51 la muestra tal cual).
 */
@Serializable
data class CuotaYaPagada(
    val codigo: String = VINCULO_CUOTA_YA_PAGADA,
    val periodo: String,
    val credito: String,
    val mensaje: String,
)

/** «La cuota de octubre de Vehículo 4083 ya está pagada.» — `periodo` como `"2026-10"`. */
fun fraseDeLaCuotaYaPagada(credito: String, periodo: String): String {
    val mes = periodoDelPrefijo(periodo)?.let { nombreDe(it).substringBefore(" de ") }
    return if (mes != null) "La cuota de $mes de $credito ya está pagada." else "La cuota de este mes de $credito ya está pagada."
}

/** El [CuotaYaPagada] de una respuesta de `vincular-deuda`, o `null` si no es ese 409. */
fun cuotaYaPagadaDe(status: Int, cuerpo: String?): CuotaYaPagada? {
    if (status != 409 || cuerpo.isNullOrBlank()) return null
    return runCatching { JSON_DEL_VINCULO.decodeFromString(CuotaYaPagada.serializer(), cuerpo) }
        .getOrNull()
        ?.takeIf { it.codigo == VINCULO_CUOTA_YA_PAGADA }
}

private val JSON_DEL_VINCULO = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/** Lo que se le dice a quien intenta vincular un movimiento que ya es parte de otro traspaso. */
const val VINCULO_YA_ES_TRASPASO: String =
    "Este movimiento ya es parte de un traspaso."

/** Lo que se le dice a quien intenta vincular algo que no es un gasto suelto de deuda. */
const val VINCULO_CATEGORIA_INVALIDA: String =
    "Elige primero la categoría «Cuota de crédito» o «Pago de tarjeta» para poder vincularlo a una deuda."

/** Lo que se le dice cuando no se pudo obtener la tasa del día para convertir la moneda. */
const val VINCULO_SIN_TASA: String =
    "No se pudo obtener la tasa del día para convertir la moneda. Inténtalo de nuevo más tarde."
