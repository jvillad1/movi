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

private val dataIndividual = floatArrayOf(
    0f, 0.2f, 0.3f, 0.25f, 0.4f, 0.5f, 0.45f, 0.6f, 0.7f, 0.65f,
    0.7f, 0.85f, 0.9f, 0.85f, 1.0f, 1.1f, 1.0f, 1.15f, 1.3f, 1.4f,
    1.35f, 1.45f, 1.55f, 1.6f, 1.5f, 1.65f, 1.7f, 1.65f, 1.75f, 1.84f,
)

private val dataFamily = floatArrayOf(
    0f, 0.4f, 0.6f, 0.5f, 0.8f, 1.1f, 1.0f, 1.3f, 1.6f, 1.4f,
    1.5f, 1.9f, 2.2f, 2.0f, 2.4f, 2.6f, 2.4f, 2.7f, 3.1f, 3.4f,
    3.2f, 3.5f, 3.8f, 4.0f, 3.7f, 4.2f, 4.4f, 4.3f, 4.6f, 4.9f,
)

@Composable
fun Sparkline(
    modifier: Modifier,
    family: Boolean = false,
    color: Color = MinText,
    strokeWidth: Float = 3f,
) {
    val data = if (family) dataFamily else dataIndividual
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 2f
        val max = data.max()
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = pad + i * (w - pad * 2) / (data.size - 1)
            val y = h - pad - (v / max) * (h - pad * 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.92f),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

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
fun InvestmentSparkline(modifier: Modifier, color: Color = MinText) {
    val points = listOf(
        Offset(0f, 0.7f), Offset(0.12f, 0.68f), Offset(0.22f, 0.56f),
        Offset(0.31f, 0.62f), Offset(0.44f, 0.5f), Offset(0.56f, 0.39f),
        Offset(0.69f, 0.42f), Offset(0.81f, 0.27f), Offset(0.94f, 0.19f), Offset(1f, 0.11f),
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
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
