package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.NOTA_DEL_DEBITO_AUTOMATICO
import com.jvillada.movi.shared.model.confirmacionDelDebito
import com.jvillada.movi.shared.model.textoDelDebitoAutomatico
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.recurrentes.fechaLegibleDelChecklist
import kotlinx.coroutines.launch

/** El título de la sección de «Por revisar» con lo que el banco cobró solo. */
const val TITULO_DE_LOS_DEBITOS: String = "Lo que el banco cobró solo"
const val SI_SE_COBRO: String = "Sí, se cobró"
const val CAMBIAR_MONTO: String = "Cambiar monto"
const val NO_SE_COBRO: String = "No se cobró"
const val TAG_TARJETA_DEL_DEBITO: String = "tarjeta-del-debito-automatico"

/**
 * **Confirmar un débito automático**: el server anota el movimiento con [monto] y los ids de la
 * propuesta (`POST /api/debitos-automaticos/confirmar`) — la cuota de dos patas de un crédito, con la
 * misma función que confirma un aviso de dos patas, o el gasto de un recurrente con el sello de su
 * período. Un doble toque no duplica. La fila de «Pagos del período» se tilda sola: la deriva el
 * movimiento.
 *
 * Nunca se llama sin que el dueño toque «Sí, se cobró».
 */
internal suspend fun confirmarElDebitoAutomatico(debito: DebitoAutomaticoPorConfirmar, monto: Long) {
    Repositories.wallets.confirmarDebitoAutomatico(confirmacionDelDebito(debito, monto))
}

/**
 * **Una tarjeta de «Por revisar» por cada débito automático vencido sin movimiento**: lo que Movi
 * propone, ya armado, con tres salidas — «Sí, se cobró», «Cambiar monto» (la cuota puede variar: el
 * Techo Gardenera no siempre cobra lo pactado) y «No se cobró» (sin saldo, o lo pagó por otro lado).
 *
 * Bloque aparte de la bandeja a propósito: no es un aviso del banco y no se mezcla con ellos (ver
 * [DebitoAutomaticoPorConfirmar]). [onResuelto] avisa que la tarjeta se puede quitar ya, sin
 * esperar la relectura.
 */
@Composable
internal fun TarjetaDelDebitoAutomatico(
    debito: DebitoAutomaticoPorConfirmar,
    onResuelto: () -> Unit,
) {
    var cambiando by remember(debito.clave) { mutableStateOf(false) }
    var monto by remember(debito.clave) { mutableStateOf<Long?>(debito.monto) }
    var trabajando by remember(debito.clave) { mutableStateOf(false) }
    var error by remember(debito.clave) { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()

    fun hacer(accion: suspend () -> Unit) {
        if (trabajando) return
        trabajando = true
        error = null
        alcance.launch {
            intentar { accion() }
                .onSuccess { onResuelto() }
                .onFailure { error = it.toUserMessage() }
            trabajando = false
        }
    }

    val montoValido = (monto ?: 0L) > 0L
    MinCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag(TAG_TARJETA_DEL_DEBITO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                NOTA_DEL_DEBITO_AUTOMATICO.uppercase(),
                style = Movi.textos.rotulo.copy(letterSpacing = 0.8.sp),
                color = Movi.colores.aviso,
                modifier = Modifier.weight(1f),
            )
            Text("Vence el ${fechaLegibleDelChecklist(debito.vence).ifEmpty { debito.vence }}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Text(textoDelDebitoAutomatico(debito), style = Movi.textos.cuerpo, color = Movi.colores.texto, lineHeight = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "El banco no avisa cuando la cobra. Movi no la anota hasta que confirmes.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        if (cambiando) {
            Spacer(Modifier.height(10.dp))
            MoneyField(monto, { monto = it }, label = "Lo que cobró el banco")
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pastilla(
                if (trabajando) "Anotando…" else SI_SE_COBRO,
                principal = true,
                habilitada = !trabajando && montoValido,
            ) {
                val elMonto = monto ?: return@Pastilla
                hacer { confirmarElDebitoAutomatico(debito, elMonto) }
            }
            if (!cambiando) {
                Pastilla(CAMBIAR_MONTO, habilitada = !trabajando) { cambiando = true }
            }
            Pastilla(NO_SE_COBRO, habilitada = !trabajando) {
                hacer { Repositories.wallets.descartarDebitoAutomatico(debito.ruleId, debito.periodo) }
            }
        }
    }
}

@Composable
private fun Pastilla(texto: String, habilitada: Boolean, principal: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (principal) Modifier.background(if (habilitada) Movi.colores.texto else Movi.colores.textoApagado)
                else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp)),
            )
            .clickable(enabled = habilitada, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyo,
            color = if (principal) Movi.colores.fondo else Movi.colores.texto,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
