package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/** Tag estable de una [BarraDeProgreso]. */
const val TAG_BARRA_DE_PROGRESO: String = "ds-barra-de-progreso"

/** **Dónde empieza un separador.** */
enum class SangriaDeSeparador {
    /** De borde a borde. */
    Ninguna,

    /** Desde el margen de la pantalla. */
    Margen,

    /** Desde donde empieza el texto de una [Fila] con inicio: pasa por debajo del título, no del avatar. */
    ConInicio,
}

/**
 * **La línea entre dos filas** de una misma lista: [Medida.trazo] en el color `hilo`. Entre dos
 * superficies no va: las separa su capa.
 */
@Composable
fun Separador(sangria: SangriaDeSeparador = SangriaDeSeparador.ConInicio, modifier: Modifier = Modifier) {
    val inicio = when (sangria) {
        SangriaDeSeparador.Ninguna -> null
        SangriaDeSeparador.Margen -> Medida.margenDePantalla
        SangriaDeSeparador.ConInicio -> Medida.margenDePantalla + Medida.avatar + Espacio.s
    }
    HorizontalDivider(
        modifier = if (inicio != null) modifier.padding(start = inicio) else modifier,
        thickness = Medida.trazo,
        color = Movi.colores.hilo,
    )
}

/** **De qué color se llena una barra.** Marca para un avance propio; un estado solo si es estado. */
enum class TonoDeBarra { Marca, Neutro, Aviso, Peligro, Exito }

/**
 * El color del relleno, en la paleta del tema. Pura. El avance propio ([TonoDeBarra.Marca]) va **en
 * tinta** desde la vuelta 29, como la barra de «Falta por pagar» de la opción 3: el amarillo sobre
 * la pista clara no se ve (1,4:1).
 */
fun colorDeLaBarra(tono: TonoDeBarra, colores: ColoresDeMovi): Color = when (tono) {
    TonoDeBarra.Marca -> colores.texto
    TonoDeBarra.Neutro -> colores.textoMedio
    TonoDeBarra.Aviso -> colores.aviso
    TonoDeBarra.Peligro -> colores.sale
    TonoDeBarra.Exito -> colores.entra
}

/**
 * **Una barra de progreso**: la pista en la capa 3 y el relleno del [tono], de [Medida.altoDeBarraDeProgreso]
 * de alto, con las puntas redondas. [fraccion] se recorta a 0–1 (un presupuesto pasado llena la
 * barra; el «te pasaste» lo dice el texto y el tono, no una barra que se sale).
 *
 * ```
 * BarraDeProgreso(gastado.toFloat() / tope, tono = if (gastado > tope) TonoDeBarra.Peligro else TonoDeBarra.Marca)
 * ```
 */
/**
 * **Sobre qué va una barra** (vuelta 40). [Normal]: la pista en la capa 3, de
 * [Medida.altoDeBarraDeProgreso]. [SobreLaMarca]: dentro de una tarjeta amarilla («Falta por pagar»),
 * la pista en `marcaSuave` y de [Medida.altoDeBarraEnTarjeta]; el relleno sigue en su tono (la tinta).
 */
enum class FondoDeBarra { Normal, SobreLaMarca }

@Composable
fun BarraDeProgreso(
    fraccion: Float,
    modifier: Modifier = Modifier,
    tono: TonoDeBarra = TonoDeBarra.Marca,
    fondo: FondoDeBarra = FondoDeBarra.Normal,
    /**
     * Una marca vertical sobre la pista, en esta fracción (0–1): «la mitad» en «Quién puso qué» de
     * Espacios. `null`, ninguna.
     */
    marca: Float? = null,
) {
    if (marca == null) {
        Pista(fraccion, tono, modifier, fondo)
        return
    }
    Box(
        modifier = modifier.fillMaxWidth().height(Medida.altoDeMarcaDeBarra),
        contentAlignment = Alignment.CenterStart,
    ) {
        Pista(fraccion, tono, Modifier, fondo)
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Spacer(Modifier.fillMaxWidth(marca.coerceIn(0f, 1f)))
            Box(
                modifier = Modifier
                    .width(Medida.trazoDeCarga)
                    .fillMaxHeight()
                    .background(Movi.colores.texto)
                    .testTag(TAG_MARCA_DE_BARRA),
            )
        }
    }
}

/** El `testTag` de la marca de una [BarraDeProgreso]. */
const val TAG_MARCA_DE_BARRA: String = "ds:marca-de-barra"

@Composable
private fun Pista(fraccion: Float, tono: TonoDeBarra, modifier: Modifier, fondo: FondoDeBarra) {
    val recortada = fraccion.coerceIn(0f, 1f)
    val forma = RoundedCornerShape(Radio.pill)
    val sobreLaMarca = fondo == FondoDeBarra.SobreLaMarca
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (sobreLaMarca) Medida.altoDeBarraEnTarjeta else Medida.altoDeBarraDeProgreso)
            .clip(forma)
            .background(if (sobreLaMarca) Movi.colores.marcaSuave else Movi.colores.capa3)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(recortada, 0f..1f) }
            .testTag(TAG_BARRA_DE_PROGRESO),
    ) {
        if (recortada > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(recortada)
                    .fillMaxHeight()
                    .clip(forma)
                    .background(colorDeLaBarra(tono, Movi.colores)),
            )
        }
    }
}
