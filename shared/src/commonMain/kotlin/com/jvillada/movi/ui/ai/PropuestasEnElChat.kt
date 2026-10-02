package com.jvillada.movi.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.theme.Movi
import kotlinx.coroutines.launch

/**
 * # Las tarjetas de lo que Movi propone hacer (Ola 3 · «Movi actúa»)
 *
 * El asistente no escribe nada: propone, y el dueño decide aquí con un toque. Dos reglas:
 *
 * - **«Hacerlo» llama al MISMO método del repositorio que usa la pantalla de siempre** —el que llama
 *   al endpoint de siempre, con su validación de siempre—. No hay una puerta nueva que escriba: si
 *   el server rechaza (la categoría era reservada, la cuenta ya no existe), el dueño lee el motivo y
 *   la tarjeta queda sin hacer.
 * - **«No» no toca nada de su plata.** Solo le avisa al asistente, para que en el turno siguiente
 *   sepa que le dijeron que no.
 */

/**
 * Hace lo que la propuesta dice, **con el método de siempre**, y después le avisa al asistente.
 * Lanza si el endpoint de la acción rechaza: el aviso al asistente solo va si la acción se hizo.
 */
internal suspend fun hacerLaPropuesta(repo: WalletRepository, propuesta: AccionPropuesta) {
    when (propuesta.tipo) {
        TipoDeAccion.ANOTAR_MOVIMIENTO ->
            repo.postEvent(requireNotNull(propuesta.movimiento) { "La propuesta no trae el movimiento" })
        TipoDeAccion.CAMBIAR_CATEGORIA -> {
            val categoria = requireNotNull(propuesta.categoria) { "La propuesta no trae la categoría" }
            val ids = propuesta.idsDeMovimientos
            require(ids.isNotEmpty()) { "La propuesta no trae movimientos" }
            // Uno solo va por la puerta de un movimiento; varios, por la del lote (Ola 23).
            if (ids.size == 1) repo.updateEventCategory(ids.single(), categoria)
            else repo.recategorizarEnLote(ids, categoria)
        }
        TipoDeAccion.CREAR_RECURRENTE ->
            repo.createRecurringRule(requireNotNull(propuesta.recurrente) { "La propuesta no trae el recurrente" })
        TipoDeAccion.MARCAR_PAGO_HECHO -> repo.markOccurrence(
            ruleId = requireNotNull(propuesta.reglaId),
            period = requireNotNull(propuesta.periodo),
            // Nunca sin movimiento: es la regla del dueño, «el checklist lo tilda el movimiento».
            eventId = requireNotNull(propuesta.eventId) { "Un pago se marca solo con su movimiento" },
        )
    }
    // La acción ya está hecha; si este aviso no llega, lo único que se pierde es que el asistente
    // lo sepa. No vale tumbar la tarjeta por eso.
    runCatching { repo.resolverPropuesta(propuesta.id, EstadoDePropuesta.HECHA) }
}

/** Lo que se le dice al dueño si la acción no se pudo hacer. */
internal fun motivoDeLaFalla(falla: Throwable): String =
    (falla as? ApiException)?.serverMessage?.takeIf { it.isNotBlank() && it.length < 200 }
        ?: "No se pudo hacer. Revisa tu conexión e intenta otra vez."

internal const val HACERLO = "Hacerlo"
internal const val NO_HACERLO = "No"
internal const val YA_ESTA_HECHO = "Hecho"
internal const val DESCARTADA = "Descartada"
internal const val ROTULO_DE_LA_PROPUESTA = "MOVI PROPONE"

/**
 * Una propuesta, con su estado y sus dos botones. El estado vive en quien la pinta (la pantalla
 * del chat lo guarda por id): una tarjeta que se va de la pantalla al hacer scroll no puede volver
 * con «Hacerlo» después de haberse hecho.
 */
@Composable
internal fun PropuestaDelAsistente(
    propuesta: AccionPropuesta,
    estado: EstadoDePropuesta,
    onEstado: (EstadoDePropuesta) -> Unit,
    repo: () -> WalletRepository = { Repositories.wallets },
) {
    val coroutine = rememberCoroutineScope()
    var trabajando by remember(propuesta.id) { mutableStateOf(false) }
    var error by remember(propuesta.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .padding(start = 36.dp, bottom = 12.dp)
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.amplia))
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(Movi.formas.amplia))
            .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
        verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(ROTULO_DE_LA_PROPUESTA, style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
        }
        Text(propuesta.frase, style = Movi.textos.cuerpo, color = Movi.colores.texto)

        when (estado) {
            EstadoDePropuesta.HECHA -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.entra, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(YA_ESTA_HECHO, style = Movi.textos.apoyo, color = Movi.colores.entra, fontWeight = FontWeight.Medium)
            }
            EstadoDePropuesta.RECHAZADA ->
                Text(DESCARTADA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            EstadoDePropuesta.PENDIENTE -> {
                error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
                Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                    Text(
                        if (trabajando) "Haciéndolo…" else HACERLO,
                        style = Movi.textos.cuerpo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.sobreMarca,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Movi.formas.normal))
                            .background(Movi.colores.marca)
                            .clickable(enabled = !trabajando) {
                                trabajando = true
                                error = null
                                coroutine.launch {
                                    runCatching { hacerLaPropuesta(repo(), propuesta) }
                                        .onSuccess { onEstado(EstadoDePropuesta.HECHA) }
                                        .onFailure { error = motivoDeLaFalla(it) }
                                    trabajando = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        NO_HACERLO,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Movi.formas.normal))
                            .clickable(enabled = !trabajando) {
                                onEstado(EstadoDePropuesta.RECHAZADA)
                                // «No» no toca su plata: solo se le cuenta al asistente. Si el aviso
                                // no llega, la tarjeta igual queda descartada en pantalla.
                                coroutine.launch {
                                    runCatching { repo().resolverPropuesta(propuesta.id, EstadoDePropuesta.RECHAZADA) }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
