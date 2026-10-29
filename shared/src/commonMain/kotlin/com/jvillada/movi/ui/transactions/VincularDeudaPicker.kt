package com.jvillada.movi.ui.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
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
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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

/**
 * «Vehículo 4083 · COP · Debes $177.200.000» — nombre, moneda y cuánto debe, en una sola línea.
 *
 * Vuelta 53: **nunca «A favor»**. En este selector se elige a qué deuda va un pago, y un crédito «a
 * favor $980.000» no dice nada (pasaba con la demo: un crédito con condiciones y sin movimientos). Sin
 * deuda en Movi —saldo en 0 o por debajo— un crédito dice [SIN_EL_SALDO_EN_EL_SELECTOR], como la hoja de
 * Cuota de la vuelta 49; una tarjeta dice [SIN_DEUDA_EN_EL_SELECTOR] (pagarla de más sí la deja en
 * cero o a favor, y eso es un dato real, pero acá no es lo que se está eligiendo).
 */
fun textoDeCuentaDeDeuda(cuenta: Account): String {
    val (monto, moneda) = saldoEnSuMoneda(cuenta)
    val cuanto = when {
        // En una deuda el saldo positivo es lo que se debe (ver [saldoDeDeuda]).
        monto > 0L -> "Debes ${saldoDeDeuda(monto, moneda).magnitud}"
        cuenta.type == AccountType.LOAN -> SIN_EL_SALDO_EN_EL_SELECTOR
        else -> SIN_DEUDA_EN_EL_SELECTOR
    }
    return "${cuenta.name} · $moneda · $cuanto"
}

/** Lo que dice un crédito sin deuda en Movi en «¿De cuál crédito?» (vuelta 53). */
const val SIN_EL_SALDO_EN_EL_SELECTOR: String = "Movi no tiene el saldo"

/** Lo que dice una tarjeta en cero (o pagada de más) en el mismo selector (vuelta 53). */
const val SIN_DEUDA_EN_EL_SELECTOR: String = "Sin deuda"

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
    /** Vuelta 51: «¿De cuál crédito?» cuando solo se ofrecen créditos («Es la cuota de un crédito»). */
    titulo: String = "¿A cuál crédito o tarjeta corresponde?",
) {
    val deudas = cuentasDeDeuda(cuentas)
    if (deudas.isEmpty()) return
    Column(modifier = modifier) {
        Seccion(titulo = titulo)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column {
                deudas.forEachIndexed { index, cuenta ->
                    val on = cuenta.id == seleccionada?.id
                    // Una fila del sistema; la elegida lleva el ícono de listo (y lo dice: `selected`).
                    Fila(
                        titulo = textoDeCuentaDeDeuda(cuenta),
                        modifier = Modifier.semantics { selected = on },
                        final = if (on) {
                            { Icon(Icons.Rounded.Check, contentDescription = "Elegida", tint = Movi.colores.texto, modifier = Modifier.size(Icono.m)) }
                        } else null,
                        onClick = { onSeleccionar(if (on) null else cuenta) },
                    )
                    if (index != deudas.lastIndex) Separador(SangriaDeSeparador.Margen)
                }
            }
        }
        Text(
            "Es opcional: si no eliges ninguna, el movimiento queda como un gasto suelto.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(top = Espacio.xs),
        )
    }
}
