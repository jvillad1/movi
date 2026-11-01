package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esTuParteDeOtro
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

/** Cuántos días hacia atrás se busca el último pago de la regla (dos períodos y un poco). */
const val DIAS_HACIA_ATRAS_DEL_ULTIMO_PAGO: Int = 75

/** A cuántos movimientos, como mucho, se les pregunta si son un pago de la regla (una lectura cada uno). */
const val MAXIMO_DE_MARCAS_A_PREGUNTAR: Int = 10

/** Un pago de la regla se parece al monto de la regla en este porcentaje (el arriendo sube un poco cada año). */
const val TOLERANCIA_DEL_MONTO_EN_PORCENTAJE: Long = 25

/**
 * **Los movimientos que pueden ser el último pago** de una regla de [monto], del más reciente al más viejo: gastos
 * en pesos de los últimos [DIAS_HACIA_ATRAS_DEL_ULTIMO_PAGO] días, sin patas de traspaso ni tu parte de lo que
 * pagó otro, de un monto parecido (±[TOLERANCIA_DEL_MONTO_EN_PORCENTAJE] %). **No mira el nombre ni la categoría**:
 * un pago emparejado con un aviso del banco se llama «Transferencia a Andrés», y «Gas» no es «Gasolina». Quién es
 * de la regla lo dice su marca ([repartoDeLaVezPasada]). Pura.
 */
fun candidatosAlUltimoPago(monto: Long, historia: List<FinancialEvent>, ahoraMs: Long): List<FinancialEvent> {
    if (monto <= 0L) return emptyList()
    val desde = ahoraMs - DIAS_HACIA_ATRAS_DEL_ULTIMO_PAGO * MS_POR_DIA
    val holgura = monto * TOLERANCIA_DEL_MONTO_EN_PORCENTAJE / 100
    return historia.asSequence()
        .filter { it.type == TransactionType.EXPENSE && it.currency == "COP" && !esTuParteDeOtro(it) && it.transferId == null }
        .filter { it.timestamp >= desde && it.amount in (monto - holgura)..(monto + holgura) }
        .sortedByDescending { it.timestamp }
        .take(MAXIMO_DE_MARCAS_A_PREGUNTAR)
        .toList()
}

/**
 * **El grupo en el que se repartió el último pago de la regla [ruleId]**, o `null` si no se sabe. El último pago
 * es el movimiento más reciente de [candidatosAlUltimoPago] cuya **marca de ocurrencia** ([marcaDe]: el sello que
 * dice «este movimiento fue la ocurrencia de tal regla», `GET /api/events/{id}/occurrence`) es de la regla;
 * si ese pago estaba repartido en uno de [espacios] ([FinancialEvent.espacioId]) y todavía se puede repartir
 * ([sePuedeRepartir]), es ese. Sin marca de la regla en ninguno, **no se sugiere nada**.
 *
 * Mira **solo el último**: si la vez pasada no se repartió, no se sugiere el reparto de hace dos meses.
 */
suspend fun repartoDeLaVezPasada(
    ruleId: String,
    monto: Long,
    historia: List<FinancialEvent>,
    espacios: List<Espacio>,
    ahoraMs: Long,
    marcaDe: suspend (eventoId: String) -> EventOccurrenceMark?,
): RepartoRecordado? {
    if (espacios.isEmpty()) return null
    for (candidato in candidatosAlUltimoPago(monto, historia, ahoraMs)) {
        if (marcaDe(candidato.id)?.ruleId != ruleId) continue
        val espacioId = candidato.espacioId ?: return null
        if (!sePuedeRepartir(candidato)) return null
        val espacio = espacios.firstOrNull { it.id == espacioId } ?: return null
        return RepartoRecordado(espacio, candidato.timestamp)
    }
    return null
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
