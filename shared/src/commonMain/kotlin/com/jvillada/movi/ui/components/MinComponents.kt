package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

/**
 * Las tres alturas de tarjeta que **ya no son tres colores**.
 *
 * Eran `#191B20`, `#1D1F25` y `#22252B`: tres grises a 1,10 · 1,15 · 1,23 de contraste contra el
 * fondo, o sea tres planos separados por casi nada, que en un teléfono al sol no se distinguen.
 *
 * Ahora las tres pintan `Movi.colores.tarjeta` y la que necesita despegarse suma **borde**. Es la
 * decisión de la dirección B: en una paleta de poco contraste la profundidad la dan la superficie
 * y el borde juntos, no la superficie sola. Un borde a 1,27:1 se ve; un gris a 1,05 de distancia
 * del anterior, no.
 *
 * El enum sobrevive porque lo nombran 69 llamadas, y en los hechos ya era uno solo: [Elevated] en
 * 60, [Default] en 8 y [High] en **una**.
 */
enum class MinCardVariant {
    /** Lo normal. Sin borde. */
    Default,

    /** Lo normal también. Se conserva porque es el default histórico y lo dicen 60 llamadas. */
    Elevated,

    /** La que va ENCIMA de otra tarjeta: misma superficie, con borde para despegarse. */
    High,
}

@Composable
fun MinCard(
    modifier: Modifier = Modifier,
    variant: MinCardVariant = MinCardVariant.Elevated,
    padding: PaddingValues = PaddingValues(Movi.espacios.margen),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(Movi.formas.amplia)
    val baseModifier = modifier
        .clip(forma)
        .background(Movi.colores.tarjeta)
        .then(
            if (variant == MinCardVariant.High) {
                Modifier.border(1.dp, Movi.colores.borde, forma)
            } else {
                Modifier
            },
        )
        .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
        .padding(padding)
    Column(modifier = baseModifier, content = content)
}

@Composable
fun Hairline(insetStart: Dp = 0.dp, insetEnd: Dp = 0.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart, end = insetEnd),
        thickness = 1.dp,
        color = Movi.colores.hilo,
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
            .padding(start = Movi.espacios.corto, end = Movi.espacios.corto, bottom = Movi.espacios.medio),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            // El rótulo del sistema espacia 1,7.sp contra los 0,5 de antes. En mayúsculas el
            // espaciado no es adorno: sin él las versales se leen como una palabra apretada.
            Text(
                text = title.uppercase(),
                style = Movi.textos.rotulo,
                color = Movi.colores.textoMedio,
            )
            if (count != null) {
                Text(
                    text = " · $count",
                    style = Movi.textos.rotulo,
                    color = Movi.colores.textoApagado,
                )
            }
        }
        if (action != null) {
            Text(
                text = action,
                style = Movi.textos.cuerpo,
                color = Movi.colores.marca,
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
    modifier: Modifier = Modifier,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                left()
                if (sub != null) {
                    Text(
                        text = sub,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
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

/**
 * Una cifra. **Tabular, no monoespaciada** — y ahí está todo el cambio.
 *
 * Se llamaba `MonoText` y ponía `FontFamily.Monospace`, que es lo que le da a Movi aire de
 * terminal en vez de producto de plata. Los dos efectos no son el mismo: para que una columna de
 * montos alinee alcanza con **cifras tabulares** (`tnum`), que trae casi toda tipografía moderna y
 * solo le da ancho fijo a los dígitos. La monoespaciada además le cambia la forma a todo, y de
 * paso arrastra las letras del símbolo y del sufijo «M».
 *
 * Copilot Money, el referente de oficio del sector, declara `tnum` explícitamente. De las otras
 * tres apps de finanzas personales del benchmark ninguna lo hace, ni siquiera YNAB, cuya pantalla
 * central es literalmente una tabla de montos.
 *
 * En el resto de la app quedan **siete** `FontFamily.Monospace`, y los siete son a propósito:
 * seis arman la maqueta de un tiquete de caja —ahí la monoespaciada no es estilo, es el papel
 * térmico que imita— y el séptimo es el cuerpo crudo de un SMS del banco, citado tal cual.
 *
 * @param estilo un estilo de la escala (`Movi.textos.*`), elegido por el PAPEL de la cifra: un
 *   monto en una fila es [TextosDeMovi.monto]; un desglose chico o un saldo que se dice en gris,
 *   [TextosDeMovi.apoyo]; una cifra que encabeza un bloque, [TextosDeMovi.titulo]. Antes era un
 *   `fontSize: Float` suelto y las llamadas pedían 11,5, 12, 12,5, 13,5, 14,5 y 15 — seis tamaños
 *   para tres papeles, y el trinquete de tamaños sueltos no los veía porque no decían
 *   `fontSize =`. Del estilo salen el tamaño y el interlineado; las cifras tabulares se fuerzan
 *   acá, porque [TextosDeMovi.apoyo] y [TextosDeMovi.titulo] no las traen y una cifra sin `tnum`
 *   baila en la columna.
 * @param fontWeight manda sobre el peso del estilo, como antes: una cifra va en Medium aunque su
 *   estilo sea Normal o SemiBold.
 */
@Composable
fun Cifra(
    text: String,
    estilo: TextStyle = Movi.textos.monto,
    color: Color = Movi.colores.texto,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = text,
        style = estilo.copy(
            fontWeight = fontWeight,
            letterSpacing = (-0.3).sp,
            fontFeatureSettings = "tnum",
        ),
        color = color,
    )
}

@Composable
fun ChevronRight() {
    // Ola 2 #5 (F11): "›" como texto suelto salía roto (▯) en la web — ícono Material en vez
    // de un glifo que depende de que la fuente del sistema lo tenga.
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        // Era Movi.colores.textoApagado: 2,51:1 contra el fondo. Una flecha que dice «esto se toca» y no se
        // ve no dice nada. textoApagado da 7,51:1.
        tint = Movi.colores.textoApagado,
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

/**
 * **La cifra protagonista de una pantalla**: «Tu plata» en el Inicio, «Deuda total» en Créditos.
 * Una por pantalla, o ninguna. Va en `Movi.textos.cifra`, que es Martian Mono a 42 sp.
 *
 * **Un renglón siempre, achicándose si hace falta.** Martian Mono es ancha: a 42 sp «$8.550.000» no
 * entraba en un teléfono de 390 dp y se partía en «$8.550.00» con un «0» solo abajo. Visto en la
 * web, no deducido. Una deuda como la del dueño son catorce caracteres. El tamaño baja de a un sp
 * hasta que entra, con piso en 24: más chico que eso deja de ser la protagonista.
 *
 * Es un componente y no un estilo porque el achique vive en `BasicText`, no en un `TextStyle`.
 */
@Composable
fun CifraProtagonista(text: String, color: Color, modifier: Modifier = Modifier) {
    val estilo = Movi.textos.cifra
    BasicText(
        text = text,
        modifier = modifier,
        style = estilo.copy(color = color),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = estilo.fontSize, stepSize = 1.sp),
    )
}
