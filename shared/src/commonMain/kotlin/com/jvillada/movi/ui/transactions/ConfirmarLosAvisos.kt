package com.jvillada.movi.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.nombreParaMostrar
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.porrevisar.AnotacionEntendida
import com.jvillada.movi.ui.porrevisar.ResultadoDelLote
import com.jvillada.movi.ui.porrevisar.confirmarElLote
import com.jvillada.movi.ui.porrevisar.confirmarLoEntendido
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/*
 * # «Confirmar los N»: una sola hoja, en Actividad y en Por revisar (vuelta 44)
 *
 * La vuelta 40 armó en Actividad la pregunta de «Confirmar los N» —la lista corta, el total, cuántos
 * quedan— y el lote uno por uno por el MISMO camino que la fila (`confirmarElLote` →
 * `confirmarLoEntendido`). Por revisar tenía su propio lote, con otra tarjeta y otra pregunta. Ahora
 * los dos usan esto: el estado de la pregunta y la hoja. Quien lo usa decide qué hacer al terminar
 * (mostrar lo confirmado, vibrar, avisar abajo).
 */

/** El tag de la lista de avisos de la hoja de «Confirmar los N» (vuelta 46). */
const val TAG_LISTA_DE_LA_CONFIRMACION: String = "confirmar-los-avisos:lista"

/** Si la hoja de «Confirmar los N» está abierta y si el lote está corriendo. */
@Stable
class ConfirmarLosAvisos internal constructor(private val alcance: CoroutineScope) {
    /** La hoja está abierta. */
    var pidiendo by mutableStateOf(false)
        internal set

    /** El lote está corriendo: los botones se apagan y la hoja no se cierra. */
    var confirmando by mutableStateOf(false)
        private set

    /** Tocar «Confirmar los N»: abre la pregunta. */
    fun pedir() {
        pidiendo = true
    }

    internal fun cancelar() {
        if (!confirmando) pidiendo = false
    }

    /** Confirma [lote] uno por uno y llama [alTerminar] con lo que pasó. */
    internal fun confirmar(lote: List<AnotacionEntendida>, alTerminar: suspend (ResultadoDelLote) -> Unit) {
        if (confirmando || lote.isEmpty()) return
        confirmando = true
        alcance.launch {
            val resultado = confirmarElLote(lote) { confirmarLoEntendido(it, ahora = Clock.System.now().toEpochMilliseconds()) }
            confirmando = false
            pidiendo = false
            alTerminar(resultado)
        }
    }
}

@Composable
fun rememberConfirmarLosAvisos(): ConfirmarLosAvisos {
    val alcance = rememberCoroutineScope()
    return remember(alcance) { ConfirmarLosAvisos(alcance) }
}

/**
 * **La pregunta de «Confirmar los N»** (vuelta 40): «¿Confirmar estos 3 avisos?», cada aviso en una
 * [Fila] (vuelta 46), el total y cuántos quedan ([detalleDeLaConfirmacion]). Solo con algo que confirmar; si
 * la hoja quedó pedida y ya no hay nada (se confirmaron desde la fila), se cierra sola.
 */
@Composable
fun HojaDeConfirmarLosAvisos(
    estado: ConfirmarLosAvisos,
    avisos: AvisosPorConfirmar?,
    alTerminar: suspend (ResultadoDelLote) -> Unit,
) {
    if (estado.pidiendo && avisos != null && avisos.confirmables.isNotEmpty()) {
        val lote = avisos.confirmables
        ConfirmarEnHoja(
            pregunta = preguntaDeConfirmarLosAvisos(lote.size),
            detalle = detalleDeLaConfirmacion(avisos),
            textoConfirmar = "Confirmar",
            ocupado = estado.confirmando,
            varianteConfirmar = VarianteDeBoton.Principal,
            onCancelar = { estado.cancelar() },
            onConfirmar = { estado.confirmar(lote, alTerminar) },
            // Vuelta 46: cada aviso, una fila del sistema —el comercio, «Categoría · Cuenta» y el
            // monto—; era un texto corrido.
            lista = {
                Superficie(
                    modifier = Modifier.fillMaxWidth().testTag(TAG_LISTA_DE_LA_CONFIRMACION),
                    nivel = NivelDeSuperficie.Capa2,
                    relleno = RellenoDeSuperficie.Lista,
                ) {
                    lote.forEachIndexed { i, anotacion ->
                        if (i > 0) Separador()
                        val ingreso = anotacion.leido.type == TransactionType.INCOME
                        Fila(
                            titulo = nombreParaMostrar(anotacion.leido.merchant),
                            subtitulo = categoriaYCuentaDe(anotacion),
                            final = {
                                Cifra(
                                    if (ingreso) anotacion.monto else -anotacion.monto,
                                    tono = if (ingreso) TonoDeCifra.Ingreso else TonoDeCifra.Neutro,
                                    moneda = anotacion.leido.currency,
                                )
                            },
                        )
                    }
                }
            },
        )
    } else if (estado.pidiendo && !estado.confirmando) {
        SideEffect { estado.pidiendo = false }
    }
}
