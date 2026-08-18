package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

enum class MinCardVariant { Default, Elevated, High }

@Composable
fun MinCard(
    modifier: Modifier = Modifier,
    variant: MinCardVariant = MinCardVariant.Elevated,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = when (variant) {
        MinCardVariant.Default  -> MinSurfaceContainerLow
        MinCardVariant.Elevated -> MinSurfaceContainer
        MinCardVariant.High     -> MinSurfaceContainerHigh
    }
    val baseModifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(bg)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(padding)
    Column(modifier = baseModifier, content = content)
}

@Composable
fun Hairline(insetStart: Dp = 0.dp, insetEnd: Dp = 0.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart, end = insetEnd),
        thickness = 1.dp,
        color = MinHairline,
    )
}

@Composable
fun MinSectionHeader(
    title: String,
    count: Int? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MinTextMute,
                letterSpacing = 0.5.sp,
            )
            if (count != null) {
                Text(
                    text = " · $count",
                    fontSize = 11.sp,
                    color = MinTextFaint,
                )
            }
        }
        if (action != null) {
            Text(
                text = action,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MinPrimary,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
            )
        }
    }
}

@Composable
fun CardRow(
    left: @Composable () -> Unit,
    right: (@Composable () -> Unit)? = null,
    sub: String? = null,
    showChevron: Boolean = false,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                left()
                if (sub != null) {
                    Text(
                        text = sub,
                        fontSize = 12.5.sp,
                        color = MinTextMute,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (right != null) right()
            if (showChevron) {
                ChevronRight()
            }
        }
        if (!isLast) Hairline()
    }
}

@Composable
fun MonoText(
    text: String,
    fontSize: Float,
    color: Color = MinText,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = text,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = fontWeight,
        color = color,
        letterSpacing = (-0.3).sp,
    )
}

@Composable
fun ChevronRight() {
    // Simple > indicator using Text as substitute for SVG
    Text(
        text = "›",
        fontSize = 18.sp,
        color = MinTextFaint,
        fontWeight = FontWeight.Light,
    )
}

@Composable
fun StatusDot(color: Color, size: Dp = 5.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** Thousands-grouped absolute value: 222933 -> "222.933". Sign is added by the caller below. */
internal fun groupThousands(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    return abs.toString().reversed().chunked(3).joinToString(".").reversed()
}

/**
 * Signo menos tipográfico (U+2212), no el guion ASCII "-". Es el mismo carácter que ya usaba
 * AccountDetailScreen a mano en varios lugares; ahora vive acá para que ningún llamador tenga
 * que reconstruirlo.
 */
private const val MINUS_SIGN = "−"

/**
 * `-1500 -> "−$1.500"`, `1500 -> "$1.500"`.
 *
 * Antes de este fix devolvía siempre el valor absoluto (F36): un mes en rojo (egresos > ingresos)
 * se mostraba en positivo en toda la app, porque cada pantalla asumía que tenía que armar el
 * signo aparte y la mayoría no lo hacía. Ahora el signo es parte del formato — los llamadores que
 * SÍ necesitan un signo propio (p.ej. mostrar deuda con la convención invertida) deben pasar
 * `kotlin.math.abs(x)` para no terminar con dos signos.
 */
fun formatCOP(amount: Long): String = (if (amount < 0) MINUS_SIGN else "") + "$" + groupThousands(amount)

/** Igual que [formatCOP] pero en millones («$1,25M»); mismo arreglo de signo, mismo motivo (F36). */
fun formatMillions(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    // Round to nearest 0.01M without floating-point error.
    val hundredths = (abs + 5_000) / 10_000
    val intPart = hundredths / 100
    val frac = (hundredths % 100).toString().padStart(2, '0')
    return (if (amount < 0) MINUS_SIGN else "") + "$$intPart,${frac}M"
}
