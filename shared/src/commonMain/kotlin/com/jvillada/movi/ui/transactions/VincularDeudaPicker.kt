package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.saldoDeDeuda
import com.jvillada.movi.ui.components.saldoEnSuMoneda
import com.jvillada.movi.theme.Movi

/**
 * # Ola Y — «¿A cuál crédito o tarjeta corresponde?»
 *
 * El paso opcional que ofrece [SMSReconcileScreen] y [ContenidoDelMovimiento] cuando el dueño
 * elige [com.jvillada.movi.shared.model.CUOTA_CATEGORY] o
 * [com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY] para un gasto que todavía no es la mitad
 * de un traspaso (ver [com.jvillada.movi.shared.model.ofreceVincularDeuda]). Elegir una cuenta
 * arma el traspaso completo en el server (`PUT /api/events/{id}/vincular-deuda`); no elegir
 * ninguna deja el gasto suelto de siempre.
 *
 * **La lista es completa a propósito, sin adivinar una «mejor opción».** El hallazgo que motivó
 * esta ola incluye el caso de dos deudas que un mismo SMS podría saldar —la Master Black en pesos
 * y en dólares, mismo número de tarjeta— y ahí solo el dueño puede elegir bien. Por eso cada fila
 * dice el nombre completo, la moneda y cuánto debe: lo mínimo para no confundir dos tarjetas
 * parecidas.
 */

/** Las cuentas de deuda entre [cuentas] — únicas que puede recibir un pago de cuota o de tarjeta. */
fun cuentasDeDeuda(cuentas: List<Account>): List<Account> =
    cuentas.filter { it.type == AccountType.LOAN || it.type == AccountType.CREDIT_CARD }

/** «Vehículo 4083 · COP · Debes $177.200.000» — nombre, moneda y cuánto debe, en una sola línea. */
fun textoDeCuentaDeDeuda(cuenta: Account): String {
    val (monto, moneda) = saldoEnSuMoneda(cuenta)
    val saldo = saldoDeDeuda(monto, moneda)
    val cuanto = if (saldo.aFavor) "A favor ${saldo.magnitud}" else "Debes ${saldo.magnitud}"
    return "${cuenta.name} · $moneda · $cuanto"
}

/**
 * La sección «¿A cuál crédito o tarjeta corresponde?». `cuentas` es la lista COMPLETA de cuentas
 * del dueño — este composable filtra las de deuda con [cuentasDeDeuda], así que los dos llamadores
 * no tienen que acordarse de filtrar.
 */
@Composable
fun SelectorDeCuentaDeDeuda(
    cuentas: List<Account>,
    seleccionada: Account?,
    onSeleccionar: (Account?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deudas = cuentasDeDeuda(cuentas)
    if (deudas.isEmpty()) return
    Column(modifier = modifier) {
        MinSectionHeader(title = "¿A cuál crédito o tarjeta corresponde?")
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Default,
            padding = PaddingValues(vertical = 4.dp),
        ) {
            Column {
                deudas.forEachIndexed { index, cuenta ->
                    val on = cuenta.id == seleccionada?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccionar(if (on) null else cuenta) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) Movi.colores.texto else Color.Transparent)
                                .border(1.dp, if (on) Movi.colores.texto else Movi.colores.borde, RoundedCornerShape(999.dp)),
                        )
                        Text(
                            textoDeCuentaDeDeuda(cuenta),
                            style = Movi.textos.cuerpo,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            color = Movi.colores.texto,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (index != deudas.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp)
                                .background(Movi.colores.hilo)
                                .size(width = 0.dp, height = 1.dp),
                        )
                    }
                }
            }
        }
        Text(
            "Es opcional: si no eliges ninguna, el movimiento queda como un gasto suelto.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}
