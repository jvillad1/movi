package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.formatCOP

/**
 * # «Flujo del día $0» no puede quedar sin explicación cuando salió plata de verdad
 *
 * El dueño pagó dos tarjetas desde Bancolombia Ahorros el mismo día ($386.902 + $1.542.634 =
 * $1.929.536 que salieron de verdad de su cuenta disponible) y Movimientos le dijo «Flujo del día
 * $0». Su reacción textual: *«eso es una mentira»*.
 *
 * La Ola U resolvió esto con una línea aparte que ACLARABA la exclusión. La Ola W fue más allá:
 * el dueño pidió que «Flujo del día» sume también el pago de tarjeta —es su extracto del día, y
 * esa cuenta sí perdió esa plata—, así que ahora esta línea aclara lo contrario: que esa parte NO
 * se duplica en el gasto del período/Disponible, que sigue sin contarla (ver `aporteAlFlujoDelDia`
 * en `:core`, que no cambia). Dos cifras honestas cada una a su manera: «Flujo del día» por día,
 * «Salió»/Disponible por período.
 */

/**
 * Las cuentas de **«Tu plata»**: activo, no deuda — de donde sí sale plata de verdad al pagar una
 * tarjeta. La misma lista que ya usa el server en `GET /api/events/card-payment-candidates`
 * (`EventRoutes.kt`): se repite acá porque es la definición de qué es «Tu plata», no un dato que
 * valga la pena traer de otro lado.
 */
private val CUENTAS_DE_TU_PLATA = setOf(
    AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.INVESTMENT,
)

/**
 * **Cuánto salió de verdad de «Tu plata» hoy a pagar una tarjeta** — desde la Ola W, la misma
 * cifra que `diasVisibles` YA resta de «Flujo del día» (antes la excluía; ahora la cuenta como la
 * salida real que es). Esta función sigue siendo la única fuente de esa suma, para el total de
 * arriba y para la línea de contexto de abajo.
 *
 * Suma los movimientos de [items] con [CARD_PAYMENT_CATEGORY] que son EXPENSE **en una cuenta de
 * Tu plata** — nunca en la propia tarjeta, donde la misma categoría llega como INCOME bajando la
 * deuda, no como plata que sale de ningún lado. Solo pesos, la misma moneda que suma «Flujo del
 * día» (`aporteAlFlujoDelDia`): sumar dólares acá inflaría la cifra con un monto que la cabecera
 * del día no está contando.
 *
 * Puro sobre [items] y [accountTypes], que la pantalla ya tiene en memoria para armar el propio
 * «Flujo del día» (ver `diasVisibles`): no dispara ninguna lectura nueva.
 */
fun montoPagoDeTarjetaEnElDia(items: List<FinancialEvent>, accountTypes: Map<String, AccountType>): Long =
    items.filter {
        it.category == CARD_PAYMENT_CATEGORY &&
            it.type == TransactionType.EXPENSE &&
            it.currency == "COP" &&
            accountTypes[it.accountId] in CUENTAS_DE_TU_PLATA
    }.sumOf { it.amount }

/**
 * La nota del pago de tarjeta del día, en **un solo renglón** —«+ $100.000 a tu tarjeta · no suma, ya
 * contó al comprar»—, o `null` si ese día no tuvo ninguno (el caso normal: no hay nada que agregar).
 *
 * Ola W: ya no aclara una exclusión (esa plata SÍ está en el total de arriba, desde `diasVisibles`)
 * — aclara que no se cuenta dos veces: el gasto real ya quedó registrado el día de la compra, así
 * que el período/Disponible no vuelve a restarlo cuando se paga la tarjeta.
 *
 * Vuelta 52: era una frase de dos renglones («Además, $X fueron a pagar tarjeta — ya contado en tu
 * gasto del mes cuando compraste, no se duplica.») en un encabezado de día. Ahora cabe en uno; si ni
 * así cabe (360 dp con la letra ×1,12), [LineaPagoDeTarjetaEnElDia] baja a [textoCortoDelPagoDeTarjeta]
 * y la frase entera queda en [explicacionDelPagoDeTarjeta], que es lo que lee el lector de pantalla.
 */
fun textoPagoDeTarjetaEnElDia(monto: Long): String? =
    if (monto <= 0L) null else "+ ${formatCOP(monto)} a tu tarjeta · no suma, ya contó al comprar"

/** La nota recortada a lo esencial, para cuando la completa no cabe en el renglón. */
fun textoCortoDelPagoDeTarjeta(monto: Long): String? =
    if (monto <= 0L) null else "+ ${formatCOP(monto)} a tu tarjeta"

/**
 * La explicación entera, con todas las palabras: va como `contentDescription` de la línea (que a la
 * vista queda en un renglón) para que nada de lo que decía la frase larga se pierda.
 */
fun explicacionDelPagoDeTarjeta(monto: Long): String? =
    if (monto <= 0L) null else "Además, ${formatCOP(monto)} fueron a pagar tarjeta — ya contado en tu gasto " +
        "del mes cuando compraste, no se duplica."

/** El tag de la línea de pago de tarjeta del día, para que las pruebas la encuentren. */
const val TAG_PAGO_DE_TARJETA_EN_EL_DIA: String = "pago-de-tarjeta-en-el-dia"

/**
 * La línea de contexto del pago de tarjeta del día, debajo del encabezado —ver
 * [textoPagoDeTarjetaEnElDia]. **Un renglón, apagada**, que no cambia de alto: si la nota completa se
 * sale del ancho se pinta la corta ([textoCortoDelPagoDeTarjeta]) en el mismo renglón. No reserva alto
 * cuando no hay nada que decir: el monto sale de movimientos que la pantalla ya tiene en memoria, sin
 * ninguna lectura en vuelo que pueda hacer saltar la lista al contestar.
 */
@Composable
internal fun LineaPagoDeTarjetaEnElDia(monto: Long) {
    val largo = textoPagoDeTarjetaEnElDia(monto) ?: return
    val corto = textoCortoDelPagoDeTarjeta(monto) ?: return
    val explicacion = explicacionDelPagoDeTarjeta(monto) ?: return
    var noCabe by remember(monto) { mutableStateOf(false) }
    Text(
        text = if (noCabe) corto else largo,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Espacio.xs)
            .semantics { contentDescription = explicacion }
            .testTag(TAG_PAGO_DE_TARJETA_EN_EL_DIA),
        style = Movi.textos.apoyo,
        color = Movi.colores.textoApagado,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { if (!noCabe && it.didOverflowWidth) noCabe = true },
    )
}
