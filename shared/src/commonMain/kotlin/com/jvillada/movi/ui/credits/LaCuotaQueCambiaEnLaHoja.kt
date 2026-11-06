package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.AlCambiarLaTasa
import com.jvillada.movi.shared.model.IndiceDeLaTasa
import com.jvillada.movi.shared.model.rotuloDelIndice
import com.jvillada.movi.shared.model.TipoDeCuota
import com.jvillada.movi.shared.model.porQueCambiaLaCuota
import com.jvillada.movi.shared.model.rotuloDeAlCambiarLaTasa
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo

/*
 * # Vuelta 133 · «¿Tu cuota es siempre la misma?» en la hoja del crédito
 *
 * Crear y editar usan la misma hoja ([CreditTermsSheet]) y estas mismas piezas. **Sí** por defecto: la
 * mayoría de los créditos de consumo y todos los de antes son de cuota fija. Con **No, cambia**, el
 * porqué en palabras simples (las tres formas que la investigación encontró en Colombia) y, con tasa
 * variable, los puntos sobre el IBR (opcional) y qué cambia al reajustarse. Pastillas que bajan de
 * renglón, nunca cortadas a 360 dp con letra grande, y ningún amarillo nuevo.
 */

/** La pregunta. */
const val PREGUNTA_DE_LA_CUOTA_QUE_CAMBIA: String = "¿Tu cuota es siempre la misma?"

/** Las dos respuestas. */
const val LA_CUOTA_ES_FIJA: String = "Sí"
const val LA_CUOTA_CAMBIA: String = "No, cambia"

/** El rótulo de las razones. */
const val POR_QUE_CAMBIA: String = "¿Por qué cambia?"

/** El rótulo de los puntos y la pregunta de qué cambia. */
const val PUNTOS_SOBRE_EL_IBR: String = "¿Cuántos puntos sobre el IBR? (opcional)"

/** Vuelta 134 (M-7): con «Otro índice», los puntos son sobre ese índice. */
const val PUNTOS_SOBRE_OTRO_INDICE: String = "¿Cuántos puntos sobre el índice? (opcional)"

/** Vuelta 134 (M-7): sobre qué índice es la tasa variable. */
const val PREGUNTA_DEL_INDICE: String = "¿Sobre qué índice?"

/** El rótulo del campo de los puntos, según el índice. */
fun rotuloDeLosPuntos(indice: IndiceDeLaTasa): String =
    if (indice == IndiceDeLaTasa.OTRO) PUNTOS_SOBRE_OTRO_INDICE else PUNTOS_SOBRE_EL_IBR
const val QUE_CAMBIA_AL_REAJUSTAR: String = "Cuando cambia la tasa, ¿cambia la cuota o el plazo?"

/** La ayuda debajo de las razones, según la elegida. */
fun ayudaDeLaCuotaQueCambia(tipo: TipoDeCuota): String = when (tipo) {
    TipoDeCuota.FIJA -> ""
    TipoDeCuota.TASA_VARIABLE ->
        "Escribe arriba la tasa de hoy. Movi estima la cuota con la última que pagaste y proyecta con esa tasa; cuando cambie, actualízala o sube el extracto."
    TipoDeCuota.UVR ->
        "Escribe arriba la tasa que dice el extracto sobre la UVR. Movi estima la cuota con la última que pagaste y la proyecta subiendo con la inflación."
    TipoDeCuota.ABONO_CONSTANTE ->
        "Cada mes abonas lo mismo a capital y pagas menos interés. Movi estima la cuota con la última que pagaste."
}

/** Los tags. */
const val TAG_LA_CUOTA_ES_FIJA: String = "credito:cuota-fija"
const val TAG_LA_CUOTA_CAMBIA: String = "credito:cuota-cambia"
fun tagDelPorQue(tipo: TipoDeCuota): String = "credito:por-que-cambia:${tipo.name}"
fun tagDeAlCambiar(que: AlCambiarLaTasa): String = "credito:al-cambiar:${que.name}"
fun tagDelIndice(indice: IndiceDeLaTasa): String = "credito:indice:${indice.name}"
const val TAG_PUNTOS_SOBRE_EL_IBR: String = "credito:puntos-sobre-el-ibr"

/** Las razones, en el orden de la hoja. */
val RAZONES_DE_LA_CUOTA_QUE_CAMBIA: List<TipoDeCuota> =
    listOf(TipoDeCuota.TASA_VARIABLE, TipoDeCuota.UVR, TipoDeCuota.ABONO_CONSTANTE)

/**
 * La pregunta y, con «No, cambia», el porqué y lo de la tasa variable. [tipo] en [TipoDeCuota.FIJA] es
 * «Sí»; «No, cambia» sin razón elegida todavía guarda la tasa variable, la más común en consumo.
 * [puntos] es el texto del campo (lo escribe [campoDeLosPuntos], para reusar el campo de la hoja).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreguntaDeLaCuotaQueCambia(
    tipo: TipoDeCuota,
    onTipo: (TipoDeCuota) -> Unit,
    alCambiar: AlCambiarLaTasa?,
    onAlCambiar: (AlCambiarLaTasa) -> Unit,
    habilitada: Boolean,
    /** Vuelta 134 (M-7): el índice de la tasa variable, con «IBR · Otro índice». */
    indice: IndiceDeLaTasa = IndiceDeLaTasa.IBR,
    onIndice: (IndiceDeLaTasa) -> Unit = {},
    campoDeLosPuntos: @Composable () -> Unit,
) {
    val cambia = tipo != TipoDeCuota.FIJA
    Column(modifier = Modifier.fillMaxWidth()) {
        RotuloDeCampo(PREGUNTA_DE_LA_CUOTA_QUE_CAMBIA)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            PildoraDeFiltro(
                LA_CUOTA_ES_FIJA,
                seleccionada = !cambia,
                onClick = { if (habilitada && cambia) onTipo(TipoDeCuota.FIJA) },
                modifier = Modifier.testTag(TAG_LA_CUOTA_ES_FIJA),
            )
            PildoraDeFiltro(
                LA_CUOTA_CAMBIA,
                seleccionada = cambia,
                onClick = { if (habilitada && !cambia) onTipo(TipoDeCuota.TASA_VARIABLE) },
                modifier = Modifier.testTag(TAG_LA_CUOTA_CAMBIA),
            )
        }
        if (cambia) {
            Spacer(Modifier.height(Espacio.s))
            RotuloDeCampo(POR_QUE_CAMBIA)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                RAZONES_DE_LA_CUOTA_QUE_CAMBIA.forEach { razon ->
                    PildoraDeFiltro(
                        porQueCambiaLaCuota(razon).orEmpty(),
                        seleccionada = tipo == razon,
                        onClick = { if (habilitada && tipo != razon) onTipo(razon) },
                        modifier = Modifier.testTag(tagDelPorQue(razon)),
                    )
                }
            }
            Spacer(Modifier.height(Espacio.xs))
            Text(ayudaDeLaCuotaQueCambia(tipo), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        if (tipo == TipoDeCuota.TASA_VARIABLE) {
            Spacer(Modifier.height(Espacio.s))
            RotuloDeCampo(PREGUNTA_DEL_INDICE)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                IndiceDeLaTasa.entries.forEach { este ->
                    PildoraDeFiltro(
                        rotuloDelIndice(este),
                        seleccionada = indice == este,
                        onClick = { if (habilitada && indice != este) onIndice(este) },
                        modifier = Modifier.testTag(tagDelIndice(este)),
                    )
                }
            }
            Spacer(Modifier.height(Espacio.s))
            campoDeLosPuntos()
            Spacer(Modifier.height(Espacio.s))
            RotuloDeCampo(QUE_CAMBIA_AL_REAJUSTAR)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                AlCambiarLaTasa.entries.forEach { que ->
                    PildoraDeFiltro(
                        rotuloDeAlCambiarLaTasa(que),
                        seleccionada = alCambiar == que,
                        onClick = { if (habilitada) onAlCambiar(que) },
                        modifier = Modifier.testTag(tagDeAlCambiar(que)),
                    )
                }
            }
        }
    }
}
