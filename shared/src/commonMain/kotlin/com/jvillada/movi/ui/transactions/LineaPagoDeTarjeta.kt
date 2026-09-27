package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.formatCOP

/**
 * # «Flujo del día $0» no puede quedar sin explicación cuando salió plata de verdad
 *
 * El dueño pagó dos tarjetas desde Bancolombia Ahorros el mismo día ($386.902 + $1.542.634 =
 * $1.929.536 que salieron de verdad de su cuenta disponible) y Movimientos le dijo «Flujo del día
 * $0». Su reacción textual: *«eso es una mentira»*.
 *
 * La regla de plata sigue siendo correcta y no se toca: un pago de tarjeta no es flujo porque la
 * compra ya se contó como gasto el día que se hizo (ver `isCashFlow`/[CARD_PAYMENT_CATEGORY] en
 * `:core`). Lo que faltaba era **decirlo** — un día así se lee como si no hubiera pasado nada,
 * cuando sí salió plata de la cuenta, solo que no es plata nueva.
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
 * **Cuánto salió de verdad de «Tu plata» hoy a pagar una tarjeta** — la plata que «Flujo del día»
 * excluye a propósito porque ya se contó como gasto al comprar, y que por eso puede dejar un día en
 * «$0» aunque haya salido plata de la cuenta.
 *
 * Suma los movimientos de [items] con [CARD_PAYMENT_CATEGORY] que son EXPENSE **en una cuenta de
 * Tu plata** — nunca en la propia tarjeta, donde la misma categoría llega como INCOME bajando la
 * deuda, no como plata que sale de ningún lado. Solo pesos, la misma moneda que suma «Flujo del
 * día» (`aporteAlFlujoDelDia`): sumar dólares acá inflaría la frase con una cifra que la cabecera
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
 * La frase que explica el pago de tarjeta del día, o `null` si ese día no tuvo ninguno — el caso
 * normal, donde «Flujo del día» ya cuenta la historia completa y no hay nada que agregar encima.
 *
 * Se lee bien tanto si «Flujo del día» quedó en $0 (el reclamo del dueño) como si ya venía con
 * gastos propios: «Además» avisa que esto se suma a lo que ya se dijo arriba, no que lo reemplaza.
 */
fun textoPagoDeTarjetaEnElDia(monto: Long): String? =
    if (monto <= 0L) null else "Además, ${formatCOP(monto)} salieron a pagar tarjeta (ya contado al comprar)"

/** El tag de la línea de pago de tarjeta del día, para que las pruebas la encuentren. */
const val TAG_PAGO_DE_TARJETA_EN_EL_DIA: String = "pago-de-tarjeta-en-el-dia"

/**
 * La línea que explica el pago de tarjeta del día, debajo del «Flujo del día» —ver
 * [textoPagoDeTarjetaEnElDia]. A diferencia de «Día a día» ([LineaDelDiaADiaEnElDia]), no reserva
 * alto cuando no hay nada que decir: el monto sale de movimientos que la pantalla ya tiene en
 * memoria, sin ninguna lectura en vuelo que pueda hacer saltar la lista al contestar.
 */
@Composable
internal fun LineaPagoDeTarjetaEnElDia(texto: String) {
    Text(
        text = texto,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag(TAG_PAGO_DE_TARJETA_EN_EL_DIA),
        style = Movi.textos.apoyo,
        color = Movi.colores.textoApagado,
    )
}
