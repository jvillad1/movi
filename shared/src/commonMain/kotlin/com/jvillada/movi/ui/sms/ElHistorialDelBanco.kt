package com.jvillada.movi.ui.sms

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.horaLegibleDeSms
import com.jvillada.movi.shared.model.loApartoMovi
import com.jvillada.movi.shared.model.textoDelApartado
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToLong

/*
 * # Vuelta 67 · «Mensajes del banco» se puede recorrer
 *
 * El historial era una tarjeta grande por mensaje —«Bancolombia», la píldora del estado y el texto
 * entero— y con 25 mensajes no se encontraba nada. Ahora es una lista compacta agrupada por día, con
 * chips Pendientes · Confirmados · Ignorados, el comercio de título (no el banco) y el monto a la
 * derecha. Tocar un pendiente abre Revisar aviso; un confirmado, su movimiento; lo demás muestra el
 * texto del banco ahí mismo.
 */

/** Los chips del historial. */
enum class FiltroDelHistorial(val rotulo: String) {
    TODOS("Todos"),
    PENDIENTES("Pendientes"),
    CONFIRMADOS("Confirmados"),
    IGNORADOS("Ignorados"),
}

/** ¿[sms] pasa el chip [filtro]? Pura. */
fun pasaElFiltroDelHistorial(sms: SmsMessage, filtro: FiltroDelHistorial): Boolean = when (filtro) {
    FiltroDelHistorial.TODOS -> true
    FiltroDelHistorial.PENDIENTES -> sms.state == SMS_STATE_PENDING
    FiltroDelHistorial.CONFIRMADOS -> sms.state == SMS_STATE_CONFIRMED
    FiltroDelHistorial.IGNORADOS -> sms.state == SMS_STATE_IGNORED
}

/** Un día del historial: «Hoy», «Ayer», «27 de octubre», con sus mensajes (el más nuevo primero). */
data class DiaDelHistorial(val clave: String, val titulo: String, val mensajes: List<SmsMessage>)

/** El día de llegada de [sms], o `null` si su `time` no se lee. */
private fun diaDelMensaje(sms: SmsMessage): LocalDate? {
    val t = sms.time.trim().replace(' ', 'T')
    return runCatching { LocalDateTime.parse(if (t.length == 16) "$t:00" else t).date }.getOrNull()
}

/**
 * **El historial agrupado por día**, con el chip puesto. [mensajes] ya del más nuevo al más viejo
 * (`mensajesMasRecientesPrimero`); los que no tienen una hora legible van al final, en un grupo «Sin
 * fecha». Pura.
 */
fun historialPorDia(mensajes: List<SmsMessage>, filtro: FiltroDelHistorial, hoy: LocalDate): List<DiaDelHistorial> =
    mensajes.filter { pasaElFiltroDelHistorial(it, filtro) }
        .groupBy { diaDelMensaje(it) }
        .entries
        .sortedWith(compareByDescending<Map.Entry<LocalDate?, List<SmsMessage>>> { it.key != null }.thenByDescending { it.key })
        .map { (dia, del) -> DiaDelHistorial(dia?.toString() ?: "sin-fecha", dia?.let { etiquetaDeFecha(it, hoy) } ?: "Sin fecha", del) }

/**
 * **La cabeza de una fila del historial**: el comercio de título (el del movimiento que anotó, o el
 * que lee el server en el texto, [SmsMessage.comercio]), y el banco si no hay comercio; el monto que
 * dice el texto. Debajo, el estado primero si no está confirmado (con todos los chips) y la hora:
 * «Pendiente · 10:30 p. m.». Pura.
 */
fun filaDelHistorial(sms: SmsMessage, hoy: LocalDate, conEstado: Boolean): CabezaDelAviso {
    val sinLeer = AvisoSinLeer(
        monto = sms.monto ?: 0.0, moneda = sms.moneda ?: "COP", tipo = sms.tipo ?: TransactionType.EXPENSE, comercio = sms.comercio,
    )
    val cabeza = cabezaDelAviso(listOf(sms), leido = null, hoy = hoy, sinLeer = sinLeer)
    val estado = when {
        !conEstado -> null
        loApartoMovi(sms) -> "Apartado"
        sms.state == SMS_STATE_PENDING -> "Pendiente"
        sms.state == SMS_STATE_IGNORED -> "Ignorado"
        else -> null
    }
    return cabeza.copy(
        // Vuelta 67 (revisión): el estado primero, y sin «Bancolombia ·» en cada fila: a 390 cortaba el
        // estado, que es lo que se busca al recorrer la lista.
        subtitulo = listOfNotNull(estado, horaLegibleDeSms(sms.time)).joinToString(" · ").ifEmpty { cabeza.subtitulo },
        monto = sms.monto?.roundToLong()?.takeIf { it > 0 },
    )
}

/** Qué abre tocar una fila del historial. */
sealed interface AlTocarElMensaje {
    data class Revisar(val smsId: String) : AlTocarElMensaje
    data class Movimiento(val eventoId: String) : AlTocarElMensaje
    data object VerElTexto : AlTocarElMensaje
}

/** Pendiente → Revisar aviso; confirmado con su movimiento vivo → el movimiento; lo demás, el texto. Pura. */
fun alTocarElMensaje(sms: SmsMessage): AlTocarElMensaje = when {
    sms.state == SMS_STATE_PENDING -> AlTocarElMensaje.Revisar(sms.id)
    sms.state == SMS_STATE_CONFIRMED && sms.eventoId != null -> AlTocarElMensaje.Movimiento(sms.eventoId!!)
    else -> AlTocarElMensaje.VerElTexto
}

/** El tag de una fila del historial: `TAG:<id>`. */
const val TAG_FILA_DEL_HISTORIAL: String = "historial:fila"

/** El tag del encabezado de un día del historial. */
const val TAG_DIA_DEL_HISTORIAL: String = "historial:dia"

/** Los chips del historial, con cuántos hay en cada uno. */
@Composable
internal fun ChipsDelHistorial(mensajes: List<SmsMessage>, filtro: FiltroDelHistorial, onFiltro: (FiltroDelHistorial) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Medida.dentroDeSeccion),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        FiltroDelHistorial.entries.forEach { f ->
            val cuantos = mensajes.count { pasaElFiltroDelHistorial(it, f) }
            PildoraDeFiltro(
                texto = if (f == FiltroDelHistorial.TODOS) f.rotulo else "${f.rotulo} · $cuantos",
                seleccionada = f == filtro,
                onClick = { onFiltro(f) },
            )
        }
    }
}

/**
 * Un día del historial: su título y una lista compacta. [abierto] es el mensaje que muestra su texto;
 * [onTocar] decide qué abre ([alTocarElMensaje]).
 */
@Composable
internal fun DiaDelHistorialDelBanco(
    dia: DiaDelHistorial,
    hoy: LocalDate,
    conEstado: Boolean,
    abierto: String?,
    onTocar: (SmsMessage) -> Unit,
    onEraUnMovimiento: (SmsMessage) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Medida.entreSecciones)) {
        Seccion(titulo = dia.titulo, conteo = dia.mensajes.size, modifier = Modifier.testTag("$TAG_DIA_DEL_HISTORIAL:${dia.clave}"))
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            dia.mensajes.forEachIndexed { i, sms ->
                if (i > 0) Separador()
                val cabeza = remember(sms, hoy, conEstado) { filaDelHistorial(sms, hoy, conEstado) }
                Fila(
                    titulo = cabeza.titulo,
                    subtitulo = cabeza.subtitulo,
                    inicio = { AvatarDeLaCabeza(cabeza, pideDecision = sms.state == SMS_STATE_PENDING) },
                    final = cabeza.monto?.let { { CifraDeLaCabeza(cabeza) } },
                    onClick = { onTocar(sms) },
                    onClickLabel = when (alTocarElMensaje(sms)) {
                        is AlTocarElMensaje.Revisar -> "Revisar el aviso"
                        is AlTocarElMensaje.Movimiento -> "Ver el movimiento"
                        AlTocarElMensaje.VerElTexto -> "Ver el mensaje"
                    },
                    modifier = Modifier.testTag("$TAG_FILA_DEL_HISTORIAL:${sms.id}"),
                )
                if (abierto == sms.id) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla).padding(bottom = Espacio.s),
                        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                    ) {
                        textoDelApartado(sms)?.let {
                            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.testTag(TAG_MOTIVO_DEL_APARTADO))
                        }
                        LoQueDiceElBanco(sms.text)
                        if (loApartoMovi(sms)) {
                            Boton(ERA_UN_MOVIMIENTO, onClick = { onEraUnMovimiento(sms) }, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S)
                        }
                    }
                }
            }
        }
    }
}
