package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
    /**
     * **Techo de ancho para el lado derecho, como fracción del ancho de la fila.** `null` (el
     * valor de siempre) = sin techo.
     *
     * Existe porque el lado derecho es un hijo SIN peso: se mide con todo el ancho disponible y
     * se lleva el que quiera, y recién con lo que sobra se mide la columna izquierda, que sí
     * tiene `weight(1f)`. Con un valor largo —el nombre de una cuenta, «Bancolombia Ahorros
     * Nómina Principal»— a la izquierda no le queda nada y su etiqueta se parte **en una letra
     * por renglón**: la fila pasa de 48 a ~200 dp y, en una hoja anclada abajo como la de
     * Agregar, empuja el teclado y «Guardar movimiento» fuera de la pantalla.
     *
     * Es de `master`, no de la Ola 11 —la estructura es la misma desde siempre—, pero la fila
     * «Cuenta» es de uso diario y la rama le suma 15 dp, así que se arregla acá.
     *
     * **Opcional a propósito.** Este componente lo usan 21 filas en siete pantallas, y algunas
     * ponen a la derecha cosas que no son texto (montos, chips, un `Switch`) donde un techo
     * podría apretar algo que hoy entra bien. Quien sabe que su valor puede ser largo lo pide;
     * el resto no cambia ni un píxel.
     *
     * Con techo, el lado derecho ocupa exactamente esa fracción y alinea su contenido al final,
     * que es donde ya estaba: un valor corto se ve idéntico, uno largo se corta ahí (poner
     * `maxLines = 1` en el `Text` que va adentro para que además se corte con «…» en vez de
     * ocupar dos renglones).
     */
    rightMaxFraction: Float? = null,
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
            if (right != null) {
                if (rightMaxFraction != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(rightMaxFraction),
                        contentAlignment = Alignment.CenterEnd,
                    ) { right() }
                } else {
                    right()
                }
            }
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
    // Ola 2 #5 (F11): "›" como texto suelto salía roto (▯) en la web — ícono Material en vez
    // de un glifo que depende de que la fuente del sistema lo tenga.
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MinTextFaint,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
fun StatusDot(color: Color, size: Dp = 5.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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

/**
 * Plata en poco espacio, **sin dejar de leerse como plata**.
 *
 * Reemplaza a `formatMillions`, que forzaba la escala de millones a cualquier cifra y por eso
 * decía «$0,00M» tres veces seguidas en el Inicio de una base vacía (Ola 8 · V5) y convertía
 * $250.000 en «$0,25M», obligando a hacer la cuenta mental para leer un cuarto de millón.
 *
 * La regla es la que usa cualquiera al hablar: **por debajo del millón se dicen los pesos**
 * («$0», «$250.000»), y **de un millón para arriba se dicen millones** con un decimal y sin
 * ceros de relleno («$1,2M», «$4,5M», «$12M»). Como mucho 8 caracteres («$999.999», «−$1.500M»),
 * que es lo que entra en cada una de las tres columnas de la tarjeta en un teléfono angosto.
 *
 * **Esto no unifica la tarjeta en un solo formato, y no pretende hacerlo.** Arriba, «Balance
 * neto» sigue en formato largo («$8.550.000») porque es la cifra principal y tiene toda la
 * fila para ella; abajo conviven «$4,5M» y «$250.000» en la misma fila, porque cada columna
 * dice su cifra en la escala en que esa cifra se lee sin traducir. Lo que se arregló es que
 * ninguna mienta ni obligue a dividir mentalmente, no que todas se vean iguales.
 *
 * El signo lo trae el formato, como en [formatCOP] — no lo dupliques afuera (F36).
 */
fun formatMoneyCompact(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    if (abs < 1_000_000L) return formatCOP(amount)
    val sign = if (amount < 0) MINUS_SIGN else ""
    // Décimas de millón, redondeadas sin pasar por coma flotante.
    val tenths = (abs + 50_000L) / 100_000L
    val intPart = tenths / 10
    val frac = tenths % 10
    // «$12M», no «$12,0M»: el decimal solo aparece cuando dice algo.
    return if (frac == 0L) "$sign$${groupThousands(intPart)}M" else "$sign$${groupThousands(intPart)},${frac}M"
}
