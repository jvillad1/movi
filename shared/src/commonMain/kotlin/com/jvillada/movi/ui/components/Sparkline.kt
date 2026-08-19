package com.jvillada.movi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jvillada.movi.theme.MinText

// Ola 4: acá vivía `Sparkline`, la curva del Balance del Inicio. Se borró porque dibujaba
// una serie FIJA inventada (no salía de ningún dato) — "el sparkline de mentira" (F9).

@Composable
fun SimpleSparkline(
    modifier: Modifier,
    color: Color = MinText,
) {
    // Simple upward curve for smaller contexts
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path()
        path.moveTo(0f, h * 0.75f)
        path.quadraticTo(w * 0.25f, h * 0.6f, w * 0.5f, h * 0.45f)
        path.quadraticTo(w * 0.75f, h * 0.28f, w, h * 0.1f)
        drawPath(
            path = path,
            color = color.copy(alpha = 0.92f),
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// F50: acá vivía `InvestmentSparkline`, el gráfico por período de Inversiones. Se borró por el
// mismo motivo que el de arriba — una curva FIJA inventada, no datos reales — al sacar el
// modelo de "posiciones" de esa pantalla (ver InversionesScreen.kt).
