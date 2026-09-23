package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.NivelDelGasto
import com.jvillada.movi.ui.dashboard.VentanaDelDisponible
import com.jvillada.movi.ui.dashboard.desgloseDelDisponible
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.fraseDelDisponible
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.dashboard.rotuloDeLaSemana

/**
 * # «Disponible»: cuánto queda del período, compacto y con UNA frase
 *
 * El dueño la pidió así: *«Disponible en el periodo · por semana · por día»*. Se queda, pero baja de
 * lugar (debajo de «Falta por pagar», que son justamente los fijos que resta) y se achica: la cifra
 * del disponible, **una** frase de cómo viene y tres columnas —el período, la semana, hoy— con lo
 * gastado contra su meta y una barra que crece al cargar.
 *
 * **Una sola frase, no tres.** Cada fila decía la suya, y con el período pasado el dueño leía «Te
 * pasaste por $563.456» en rojo y, un renglón abajo, «Vas bien: te quedan … para esta semana». La
 * frase ahora es [fraseDelDisponible]: manda la peor ventana y nunca dice «vas bien». Y el veredicto
 * del hero sale de la misma cuenta ([excesoDelDisponible]), así que arriba y abajo no pueden decir
 * cosas opuestas.
 *
 * De dónde sale el disponible —lo que tenías el 25, lo que entró, los fijos— queda detrás de «¿De
 * dónde sale?»: es la explicación, no la noticia.
 *
 * **Cuando los fijos superan lo que hay lo dice con todas las letras** y no dibuja barras: una
 * barra contra un disponible negativo no tiene largo que signifique algo. Las cifras de lo gastado
 * se siguen mostrando, porque son lo único sobre lo que se puede actuar hoy.
 */
@Composable
internal fun DisponibleDelPeriodoSection(
    section: ScreenSection,
    data: DashboardData,
    // Por parámetro para que una prueba fije el día; la pantalla usa el de hoy en Bogotá.
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
) {
    val disponible = disponibleDelInicio(data, hoy) ?: return
    val frase = fraseDelDisponible(disponible)
    val entrada = rememberProgresoDeEntrada("disponible")
    var verDeDondeSale by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(
            title = section.title ?: "Disponible",
            action = if (verDeDondeSale) "Ocultar" else "¿De dónde sale?",
            onAction = { verDeDondeSale = !verDeDondeSale },
        )
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(Movi.espacios.amplio),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Disponible del período",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.weight(1f),
                )
                Cifra(
                    formatMoneyCompact(disponible.disponible),
                    Movi.textos.titulo,
                    color = if (disponible.hayMargen) Movi.colores.texto else Movi.colores.sale,
                )
            }
            Spacer(Modifier.height(Movi.espacios.minimo))
            Text(text = frase.texto, style = Movi.textos.apoyo, color = colorDeLaFrase(frase.nivel))
            if (verDeDondeSale) {
                Spacer(Modifier.height(Movi.espacios.minimo))
                // «Tenías $X el 25 · entraron $Y» y abajo los fijos: dos líneas cortas y no una
                // larga, para que quepan a 390 px con la letra grande del teléfono.
                desgloseDelDisponible(disponible).forEach { linea ->
                    Text(text = linea, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
            }
            Spacer(Modifier.height(Movi.espacios.medio))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            ) {
                ColumnaDeVentana("Período", disponible.periodo, disponible.hayMargen, entrada, Modifier.weight(1f))
                ColumnaDeVentana(rotuloDeLaSemana(disponible), disponible.semana, disponible.hayMargen, entrada, Modifier.weight(1f))
                ColumnaDeVentana("Hoy", disponible.hoy, disponible.hayMargen, entrada, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Una ventana en su columna: el rótulo, lo gastado, la meta y la barra. Sin margen no hay meta ni
 * barra —no hay contra qué medir—, solo lo gastado.
 */
@Composable
private fun ColumnaDeVentana(
    rotulo: String,
    ventana: VentanaDelDisponible,
    hayMargen: Boolean,
    entrada: Float,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(text = rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
        Cifra(
            formatMoneyCompact(ventana.gastado),
            Movi.textos.monto,
            color = if (ventana.nivel == NivelDelGasto.PASADO) Movi.colores.sale else Movi.colores.texto,
        )
        if (hayMargen) {
            Text(
                text = "de ${formatMoneyCompact(ventana.meta)}",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
                maxLines = 1,
            )
            Spacer(Modifier.height(Movi.espacios.minimo))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Movi.espacios.minimo + 2.dp)
                    .clip(RoundedCornerShape(Movi.formas.pleno))
                    .background(Movi.colores.hilo),
            ) {
                // Sin gasto no hay relleno: una barra vacía dice exactamente eso. Con algo gastado,
                // un mínimo del 2 % para que se vea que hay algo.
                val fraccion = ventana.fraccion
                if (fraccion != null && ventana.gastado > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraccion.coerceIn(0.02f, 1f) * entrada.coerceIn(0f, 1f))
                            .height(Movi.espacios.minimo + 2.dp)
                            .clip(RoundedCornerShape(Movi.formas.pleno))
                            .background(colorDelNivel(ventana.nivel)),
                    )
                }
            }
        }
    }
}

@Composable
private fun colorDelNivel(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.marca
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}

@Composable
private fun colorDeLaFrase(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.textoMedio
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}
