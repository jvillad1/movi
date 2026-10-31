package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esTuParteDeOtro
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.theme.Espacio as EscalaDeEspacio
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.tuParteDe
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.espacios.motivoDelRepartoPendiente
import com.jvillada.movi.ui.espacios.sePuedeRepartir
import com.jvillada.movi.ui.espacios.textoDeRepartido
import kotlinx.coroutines.CancellationException

/*
 * # «Anotar pago» recuerda cómo se repartió la vez pasada (vuelta 87)
 *
 * La revisión 9 vio el arriendo de agosto repartido en «Casa con Andrés» y, al anotar el de noviembre
 * con «Anotar pago», ni una pista: el pago fijo «se olvida de repartirse» y el dueño lo hace a mano
 * cada mes. Si **el último pago de esa regla** se repartió en un grupo, «Anotar pago» muestra
 * «Repartir en Casa con Andrés, como el mes pasado» **ya marcado**.
 *
 * **Solo sugiere.** El dueño lo ve y puede desmarcarlo; marcado, al guardar el pago se llama el mismo
 * `…/compartir` de siempre (`repartirMovimiento`) sobre el movimiento recién guardado, con la regla del
 * grupo (50/50 o sus porcentajes: la decide el server). Nada se reparte sin que la casilla esté marcada
 * y nunca se repartió antes de que el pago estuviera guardado. Sin red, o si el reparto falla, el pago
 * queda guardado y el aviso lo dice.
 *
 * Sin escrituras ni reglas de plata nuevas: es la sugerencia de una acción que ya existía.
 */

/** Hasta cuántos días atrás se dice «como el mes pasado»; más allá, «como la última vez». */
const val DIAS_DEL_MES_PASADO: Int = 45

private const val MS_POR_DIA: Long = 24L * 60L * 60L * 1000L

/** El grupo en que se repartió la vez pasada, y cuándo fue. */
data class RepartoRecordado(val espacio: Espacio, val fechaDelPago: Long)

/**
 * **El grupo en el que se repartió el último pago de [nombreDeLaRegla]**, o `null` si no se sabe. El
 * último pago de la regla es el gasto más reciente de [historia] con su nombre (la descripción empieza
 * por él, sin tildes ni mayúsculas) y su [categoria] (si la sabemos); si ese pago estaba repartido en uno
 * de [espacios] ([FinancialEvent.espacioId]) y todavía se puede repartir ([sePuedeRepartir]), es ese.
 *
 * Mira **solo el último**: si la vez pasada no se repartió, no se sugiere el reparto de hace dos meses.
 * Pura.
 */
fun repartoDeLaVezPasada(
    nombreDeLaRegla: String,
    categoria: String?,
    historia: List<FinancialEvent>,
    espacios: List<Espacio>,
): RepartoRecordado? {
    val nombre = normalizarParaBuscar(nombreDeLaRegla)
    if (nombre.isEmpty() || espacios.isEmpty()) return null
    val categoriaBuscada = categoria?.takeIf { it.isNotBlank() }?.let(::normalizarParaBuscar)
    val ultimo = historia.asSequence()
        .filter { it.type == TransactionType.EXPENSE && !esTuParteDeOtro(it) && it.transferId == null }
        .filter { normalizarParaBuscar(it.description).startsWith(nombre) }
        .filter { categoriaBuscada == null || normalizarParaBuscar(it.category) == categoriaBuscada }
        .maxByOrNull { it.timestamp } ?: return null
    val espacioId = ultimo.espacioId ?: return null
    if (!sePuedeRepartir(ultimo)) return null
    val espacio = espacios.firstOrNull { it.id == espacioId } ?: return null
    return RepartoRecordado(espacio, ultimo.timestamp)
}

/**
 * «Repartir en Casa con Andrés, como el mes pasado»: lo que dice la casilla. Con más de [DIAS_DEL_MES_PASADO]
 * días desde el pago, «como la última vez». Pura.
 */
fun textoDelRepartoRecordado(recordado: RepartoRecordado, ahoraMs: Long): String {
    val dias = (ahoraMs - recordado.fechaDelPago) / MS_POR_DIA
    val como = if (dias <= DIAS_DEL_MES_PASADO) "como el mes pasado" else "como la última vez"
    return "Repartir en ${recordado.espacio.nombre}, $como"
}

/** El tag de la fila de la sugerencia de reparto en «Anotar pago». */
const val TAG_REPARTO_RECORDADO: String = "quickadd:reparto-recordado"

/**
 * La fila con la casilla de [RepartoRecordado]: marcada de entrada; tocarla la desmarca. Es una
 * sugerencia, no una decisión tomada (el pie de la hoja guarda con lo que esté marcado).
 */
@Composable
internal fun FilaDelRepartoRecordado(
    texto: String,
    marcada: Boolean,
    onCambiar: (Boolean) -> Unit,
    habilitada: Boolean,
    modifier: Modifier = Modifier,
) {
    Fila(
        titulo = texto,
        final = { Casilla(marcada = marcada) },
        onClick = if (habilitada) ({ onCambiar(!marcada) }) else null,
        chevron = false,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = EscalaDeEspacio.xxs)
            .semantics { selected = marcada }
            .testTag(TAG_REPARTO_RECORDADO),
    )
}

/**
 * **Reparte el pago recién guardado** [evento] en [espacio], como la vez pasada: el `…/compartir` de
 * siempre ([WalletRepository.repartirMovimiento]) y, hecho, el aviso de abajo dice «Tu parte $X · repartido
 * con Andrés» con «Deshacer» (sacarlo del grupo). Si falla, el pago **queda guardado** y el aviso dice por
 * qué no se repartió ([motivoDelRepartoPendiente]): se reparte después desde su detalle.
 */
suspend fun repartirElPagoComoAntes(
    evento: FinancialEvent,
    espacio: Espacio,
    aviso: AvisoDeDeshacer?,
    repo: WalletRepository = Repositories.wallets,
) {
    try {
        val gasto = repo.repartirMovimiento(espacio.id, evento.id)
        val tuParte = tuParteDe(gasto).takeIf { gasto.partes.isNotEmpty() }
            ?: (partesDelGasto(evento.amount, espacio, MIEMBRO_YO)[MIEMBRO_YO] ?: 0L)
        aviso?.mostrar(textoDeRepartido(espacio, tuParte)) { repo.quitarDelEspacio(espacio.id, evento.id) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        aviso?.mostrar(motivoDelRepartoPendiente(e))
    }
}
