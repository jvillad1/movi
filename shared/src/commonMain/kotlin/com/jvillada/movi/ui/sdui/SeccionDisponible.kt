package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DisponibleDelPeriodo
import com.jvillada.movi.ui.dashboard.NivelDelGasto
import com.jvillada.movi.ui.dashboard.VentanaDelDisponible
import com.jvillada.movi.ui.dashboard.comoVieneElPeriodo
import com.jvillada.movi.ui.dashboard.comoVieneHoy
import com.jvillada.movi.ui.dashboard.comoVieneLaSemana
import com.jvillada.movi.ui.dashboard.desgloseDelDisponible
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.sinMargen
import com.jvillada.movi.ui.dashboard.rotuloDeLaSemana
import com.jvillada.movi.ui.dashboard.rotuloDelPeriodo

/**
 * # «Disponible»: lo que tenías y lo que entró, menos fijos, dividido en metas por período, semana y día
 *
 * Tres filas —el período, esta semana, hoy—, cada una con lo gastado en ella contra su meta, una
 * barra que se pone ámbar al ir por encima del ritmo o cerca del tope y roja al pasarlo, y una
 * frase de cómo viene. Las metas son fijas: arriba se dicen la de la semana y la del día.
 *
 * Toda la cuenta vive en `DisponibleDelPeriodo.kt`, que es puro y está probado; acá solo hay
 * disposición y color.
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

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Disponible")
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Encabezado(disponible)
            Spacer(Modifier.height(Movi.espacios.medio))
            Hairline()
            Spacer(Modifier.height(Movi.espacios.medio))
            if (disponible.hayMargen) {
                // Las tres filas siempre, con su meta, aunque el período ya esté pasado: la meta de
                // la semana y la de hoy son la vara con la que se organiza lo que queda (#353 las
                // escondía; el dueño las quiere a la vista).
                FilaConBarra(rotuloDelPeriodo(disponible), disponible.periodo, comoVieneElPeriodo(disponible))
                Spacer(Modifier.height(Movi.espacios.medio))
                FilaConBarra(rotuloDeLaSemana(disponible), disponible.semana, comoVieneLaSemana(disponible))
                Spacer(Modifier.height(Movi.espacios.medio))
                FilaConBarra("Hoy", disponible.hoy, comoVieneHoy(disponible))
            } else {
                FilaSinMargen("Gastado este período", disponible.periodo.gastado)
                FilaSinMargen("Esta semana", disponible.semana.gastado)
                FilaSinMargen("Hoy", disponible.hoy.gastado)
            }
        }
    }
}

/** La cifra de arriba y de dónde sale; o, sin margen, la frase que lo dice. */
@Composable
private fun Encabezado(d: DisponibleDelPeriodo) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Disponible del período",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        Cifra(
            formatMoneyCompact(d.disponible),
            Movi.textos.titulo,
            color = if (d.hayMargen) Movi.colores.texto else Movi.colores.sale,
        )
    }
    Spacer(Modifier.height(Movi.espacios.minimo))
    // De dónde sale: «Tenías $X el 25 · entraron $Y» y abajo los fijos. Dos líneas cortas y no
    // una larga, para que quepa a 390 px con la letra grande del teléfono.
    desgloseDelDisponible(d).forEach { linea ->
        Text(
            text = linea,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
        )
    }
    if (d.hayMargen) {
        Text(
            text = "Meta por semana ${formatMoneyCompact(d.metaPorSemana)} · Meta por día ${formatMoneyCompact(d.metaPorDia)}",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
    } else {
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            text = sinMargen(d),
            style = Movi.textos.cuerpo,
            color = Movi.colores.sale,
        )
        Text(
            text = "Sin margen para gastar este período. Esto es lo que llevas gastado aparte de los fijos:",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
    }
}

@Composable
private fun FilaConBarra(titulo: String, ventana: VentanaDelDisponible, comoViene: String) {
    val color = colorDelNivel(ventana.nivel)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = titulo,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(Movi.espacios.corto))
            Cifra(
                "${formatMoneyCompact(ventana.gastado)} de ${formatMoneyCompact(ventana.meta)}",
                Movi.textos.monto,
                color = if (ventana.nivel == NivelDelGasto.PASADO) Movi.colores.sale else Movi.colores.texto,
            )
        }
        Spacer(Modifier.height(Movi.espacios.minimo + 2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Movi.colores.hilo),
        ) {
            // Sin gasto no hay relleno: una barra vacía dice exactamente eso. Con algo gastado, un
            // mínimo del 2 % para que se vea que hay algo.
            val fraccion = ventana.fraccion
            if (fraccion != null && ventana.gastado > 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraccion.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(Movi.espacios.minimo))
        Text(
            text = comoViene,
            style = Movi.textos.apoyo,
            color = when (ventana.nivel) {
                NivelDelGasto.BIEN -> Movi.colores.textoMedio
                NivelDelGasto.CERCA -> Movi.colores.aviso
                NivelDelGasto.PASADO -> Movi.colores.sale
            },
        )
    }
}

@Composable
private fun FilaSinMargen(titulo: String, gastado: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Movi.espacios.minimo),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titulo,
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        Cifra(formatMoneyCompact(gastado), Movi.textos.monto, color = Movi.colores.texto)
    }
}

@Composable
private fun colorDelNivel(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.marca
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}
