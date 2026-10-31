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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.sms.PastillaDelPago
import com.jvillada.movi.ui.sms.TarjetaDeAviso
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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.NOTA_DEL_DEBITO_AUTOMATICO
import com.jvillada.movi.shared.model.confirmacionDelDebito
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.recurrentes.fechaLegibleDelChecklist
import kotlinx.coroutines.launch

/**
 * El título de la sección de «Por revisar» con lo que el banco cobró solo y, desde la vuelta 90, lo
 * esperado que no se ve (la nómina, una suscripción): ya no es solo «lo que el banco cobró solo».
 */
const val TITULO_DE_LOS_DEBITOS: String = "¿Pasó esto?"
const val SI_SE_COBRO: String = "Sí, se cobró"
const val CAMBIAR_MONTO: String = "Cambiar monto"
const val NO_SE_COBRO: String = "No se cobró"
/** Vuelta 90 · los botones de un gasto esperado sin la marca de débito (Netflix en la Master). */
const val SI_SE_PAGO: String = "Sí, se pagó"
const val NO_SE_PAGO: String = "No se pagó"
/** Vuelta 90 · los de la nómina que no se ve llegar. */
const val SI_ME_LLEGO: String = "Sí, me llegó"
const val NO_ME_LLEGO: String = "No me llegó"

/** «Sí, se cobró» / «Sí, se pagó» / «Sí, me llegó»: el principal, según lo que se pregunta. */
internal fun siDeLaPropuesta(debito: DebitoAutomaticoPorConfirmar): String = when {
    debito.esIngreso -> SI_ME_LLEGO
    debito.esperado -> SI_SE_PAGO
    else -> SI_SE_COBRO
}

/** «No se cobró» / «No se pagó» / «No me llegó». */
internal fun noDeLaPropuesta(debito: DebitoAutomaticoPorConfirmar): String = when {
    debito.esIngreso -> NO_ME_LLEGO
    debito.esperado -> NO_SE_PAGO
    else -> NO_SE_COBRO
}
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

/** Lo que dice la tarjeta de un débito automático debajo de la cabeza. */
const val TEXTO_DEL_DEBITO_EN_LA_BANDEJA: String =
    "¿Se cobró? El banco no avisa cuando la cobra: Movi no la anota hasta que confirmes."

/** Vuelta 90 · lo que dice la tarjeta de algo esperado: no hay débito marcado, solo falta el movimiento. */
const val TEXTO_DE_LO_ESPERADO_EN_LA_BANDEJA: String =
    "Movi lo esperaba y no ve el movimiento. No lo anota hasta que confirmes."

/** Vuelta 90 · la etiqueta del subtítulo de lo esperado, en lugar de «Débito automático». */
const val ROTULO_DE_LO_ESPERADO: String = "Esperado"

/**
 * «Débito automático · Bancolombia Ahorros · vence el 5 de octubre»: de dónde sale y cuándo. Lo
 * esperado (vuelta 90): «Esperado · Master Black · el 5 de octubre».
 */
internal fun subtituloDelDebito(debito: DebitoAutomaticoPorConfirmar): String {
    val fecha = fechaLegibleDelChecklist(debito.vence).ifEmpty { debito.vence }
    return if (debito.esperado) "$ROTULO_DE_LO_ESPERADO · ${debito.cuentaNombre} · el $fecha"
    else "$NOTA_DEL_DEBITO_AUTOMATICO · ${debito.cuentaNombre} · vence el $fecha"
}

/** El rótulo del campo de «Cambiar monto». */
internal fun rotuloDelMontoDeLaPropuesta(debito: DebitoAutomaticoPorConfirmar): String = when {
    debito.esIngreso -> "Lo que te llegó"
    debito.esperado -> "Lo que pagaste"
    else -> "Lo que cobró el banco"
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
    // Sin monto (una regla vieja en 0 sin historia) el campo arranca vacío y abierto: no se inventa.
    var cambiando by remember(debito.clave) { mutableStateOf(debito.monto <= 0L) }
    var monto by remember(debito.clave) { mutableStateOf<Long?>(debito.monto.takeIf { it > 0 }) }
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
    // La misma tarjeta que un aviso del banco (ola R4): la propuesta de Movi, con su estado y sus
    // acciones en botones chicos del sistema.
    // Vuelta de diseño 2: la misma cabeza que un aviso del banco —lo que se cobró, el monto a la
    // derecha y «cuenta · cuándo» debajo—, sin la píldora «Por confirmar»: en la bandeja todo lo está.
    TarjetaDeAviso(
        titulo = debito.nombre,
        subtitulo = subtituloDelDebito(debito),
        modifier = Modifier.padding(bottom = Medida.dentroDeSeccion).testTag(TAG_TARJETA_DEL_DEBITO),
        inicio = { IconoEnCirculo(Icons.Rounded.AccountBalance, tono = TonoDeIcono.Aviso) },
        cifra = monto?.takeIf { it > 0 }?.let { m -> { Cifra(if (debito.esIngreso) m else -m, moneda = debito.moneda) } },
        acciones = {
            PastillaDelPago(
                if (trabajando) "Anotando…" else siDeLaPropuesta(debito),
                principal = true,
                habilitada = !trabajando && montoValido,
            ) {
                val elMonto = monto ?: return@PastillaDelPago
                hacer { confirmarElDebitoAutomatico(debito, elMonto) }
            }
            if (!cambiando) {
                PastillaDelPago(CAMBIAR_MONTO, habilitada = !trabajando) {
                    // Vuelta 90: si la última vez fue otra cifra (el sueldo varía), abre lleno con esa.
                    debito.ultimoMonto?.let { monto = it }
                    cambiando = true
                }
            }
            PastillaDelPago(noDeLaPropuesta(debito), habilitada = !trabajando) {
                hacer { Repositories.wallets.descartarDebitoAutomatico(debito.ruleId, debito.periodo) }
            }
        },
    ) {
        Text(
            if (debito.esperado) TEXTO_DE_LO_ESPERADO_EN_LA_BANDEJA else TEXTO_DEL_DEBITO_EN_LA_BANDEJA,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        if (cambiando) MoneyField(monto, { monto = it }, label = rotuloDelMontoDeLaPropuesta(debito))
        error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
    }
}
