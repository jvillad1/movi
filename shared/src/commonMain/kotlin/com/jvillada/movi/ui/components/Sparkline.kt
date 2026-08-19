package com.jvillada.movi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

@Composable
fun InvestmentSparkline(modifier: Modifier, hasData: Boolean = true, color: Color = MinText) {
    val points = listOf(
        Offset(0f, 0.7f), Offset(0.12f, 0.68f), Offset(0.22f, 0.56f),
        Offset(0.31f, 0.62f), Offset(0.44f, 0.5f), Offset(0.56f, 0.39f),
        Offset(0.69f, 0.42f), Offset(0.81f, 0.27f), Offset(0.94f, 0.19f), Offset(1f, 0.11f),
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (!hasData) {
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, h / 2),
                end = Offset(w, h / 2),
                strokeWidth = 2.8f,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = pt.x * w
            val y = pt.y * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.92f),
            style = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
