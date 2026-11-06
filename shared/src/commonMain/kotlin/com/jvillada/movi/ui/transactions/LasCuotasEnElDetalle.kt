package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.llevaCuotas
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.sms.CuotasDelAviso
import com.jvillada.movi.ui.sms.RenglonDeLasCuotas
import kotlinx.coroutines.launch

/*
 * # Vuelta 133 · «A 12 cuotas · Cambiar» en el detalle de una compra con tarjeta
 *
 * Una compra con tarjeta dice a cuántas cuotas fue, y se cambia ahí mismo: el mismo renglón de la
 * tarjeta de Por revisar («¿A cuántas cuotas? · Elegir» si no se sabe) y la misma fila de Agregar al
 * tocarlo. Guarda con `PUT /api/events/{id}/cuotas`, que solo cambia las cuotas —ni monto, ni fecha,
 * ni categoría—; la compra sigue contando entera el día que se hizo.
 *
 * En el teléfono, la base local no guarda las cuotas: un movimiento que todavía no subió las deja en
 * `CuotasPorSubir` (subirán con él) y uno ya subido va al server, que pide red (ver
 * `LocalRepository.updateEventCuotas`).
 */

/** ¿El detalle de [evento] ofrece las cuotas? Un gasto suelto de una tarjeta de crédito, vivo. Pura. */
fun ofreceLasCuotas(evento: FinancialEvent, cuentas: List<Account>): Boolean =
    llevaCuotas(evento, cuentas.firstOrNull { it.id == evento.accountId }?.type) && !isOpeningBalance(evento)

/** El tag del renglón de las cuotas en el detalle. */
const val TAG_CUOTAS_EN_EL_DETALLE: String = "movimiento:cuotas-del-detalle"

/** El prefijo de los tags de la fila de cuotas abierta en el detalle. */
const val TAG_FILA_DE_CUOTAS_EN_EL_DETALLE: String = "movimiento:cuotas-del-detalle:fila"

/** El renglón «A 12 cuotas · Cambiar» del detalle, con su guardado. */
@Composable
internal fun CuotasDelMovimiento(evento: FinancialEvent, onCambiado: (FinancialEvent) -> Unit) {
    val alcance = rememberCoroutineScope()
    var guardando by remember(evento.id) { mutableStateOf(false) }
    var elegidas by remember(evento.id, evento.cuotas) { mutableStateOf(evento.cuotas) }
    var error by remember(evento.id) { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        RenglonDeLasCuotas(
            cuotas = CuotasDelAviso(
                elegidas = elegidas,
                monto = evento.amount,
                moneda = evento.currency,
                onElegir = elegir@{ n ->
                    if (guardando || n == elegidas) return@elegir
                    val antes = elegidas
                    elegidas = n
                    guardando = true
                    error = null
                    alcance.launch {
                        runCatching { Repositories.wallets.updateEventCuotas(evento.id, n) }
                            .onSuccess { guardando = false; onCambiado(it) }
                            .onFailure { guardando = false; elegidas = antes; error = it.toUserMessage() }
                    }
                },
            ),
            habilitado = !guardando,
            tag = TAG_CUOTAS_EN_EL_DETALLE,
            base = TAG_FILA_DE_CUOTAS_EN_EL_DETALLE,
        )
        error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
    }
}
