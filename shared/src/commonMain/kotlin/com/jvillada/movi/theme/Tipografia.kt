package com.jvillada.movi.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.resources.Res
import com.jvillada.movi.resources.figtree_bold
import com.jvillada.movi.resources.figtree_extrabold
import com.jvillada.movi.resources.figtree_medium
import com.jvillada.movi.resources.figtree_regular
import com.jvillada.movi.resources.figtree_semibold
import org.jetbrains.compose.resources.Font

/**
 * # La letra de Movi
 *
 * **Figtree para todo, también las cifras** (vuelta 29, la opción 3 «Acción primero»). Hasta acá
 * eran dos: Space Grotesk en la interfaz y Martian Mono —una monoespaciada— en cada cifra de plata.
 * La opción 3 escribe las cifras en la misma letra con **números tabulares** (`tnum`, en `cifra`,
 * `cifraDeTarjeta` y `monto`): la columna de montos alinea igual, y la cifra se lee como parte de la
 * frase y no como una terminal.
 *
 * ### Por qué fuente propia
 *
 * Cinco de los seis neobancos del benchmark la tienen. Con la del sistema, Movi se veía distinta en
 * cada plataforma —Roboto en Android, San Francisco en iOS, lo que tuviera el navegador en la web— y
 * en ninguna se veía como Movi.
 *
 * ### Los archivos
 *
 * **Android y la web, no iOS todavía**: ver [laPlataformaTraeLasFuentes].
 *
 * Cinco TTF estáticos —400, 500, 600, 700 y 800—, no la versión variable: Compose en wasm e iOS no
 * garantiza aplicar los ejes de variación, y un peso que no se aplica cae en silencio al regular.
 * Juntos pesan 200 KB. Cubren todo el español y los signos que Movi escribe: tildes, eñe, «», el −
 * de los montos, ≈ y el $. SIL Open Font License; el texto está en `docs/licencias/OFL-Figtree.txt`.
 *
 * `Font(recurso)` es `@Composable` —en la web se carga por red— y por eso la familia se arma acá y
 * no como constante en `Tokens.kt`.
 */
/**
 * ¿Los archivos de fuente llegan al aparato? En Android y la web sí. En iOS todavía no: ver el
 * `actual` de iOS, que explica por qué preguntar esto evita que la app se caiga al abrir.
 */
internal expect val laPlataformaTraeLasFuentes: Boolean

/** Figtree en sus cinco pesos; sin fuentes propias, la del sistema (con sus cifras tabulares). */
@Composable
internal fun familiaDeMovi(): FontFamily {
    if (!laPlataformaTraeLasFuentes) return FontFamily.Default
    return FontFamily(
        Font(Res.font.figtree_regular, FontWeight.Normal),
        Font(Res.font.figtree_medium, FontWeight.Medium),
        Font(Res.font.figtree_semibold, FontWeight.SemiBold),
        Font(Res.font.figtree_bold, FontWeight.Bold),
        Font(Res.font.figtree_extrabold, FontWeight.ExtraBold),
    )
}

/**
 * Los nueve estilos con su familia puesta. **Pura**, sin `@Composable`, para poder probarla.
 *
 * Una sola familia para todos desde la vuelta 29: las cifras se distinguen por sus números tabulares
 * (que ya traen sus estilos), no por otra letra.
 */
internal fun TextosDeMovi.conFamilia(familia: FontFamily): TextosDeMovi = copy(
    cifra = cifra.copy(fontFamily = familia),
    cifraDeTarjeta = cifraDeTarjeta.copy(fontFamily = familia),
    portada = portada.copy(fontFamily = familia),
    titular = titular.copy(fontFamily = familia),
    titulo = titulo.copy(fontFamily = familia),
    cuerpo = cuerpo.copy(fontFamily = familia),
    monto = monto.copy(fontFamily = familia),
    apoyo = apoyo.copy(fontFamily = familia),
    rotulo = rotulo.copy(fontFamily = familia),
)

/**
 * **La tipografía de Material con la familia de Movi.**
 *
 * No es un detalle. Todo `Text` que no pasa `style` hereda el `LocalTextStyle`, que `MaterialTheme`
 * llena con `bodyLarge`. Si esto no se toca, los estilos del sistema salen en Figtree y todo lo demás
 * sigue en la letra del sistema: la misma pantalla con dos tipografías.
 *
 * Se cambia solo la familia, no los tamaños: los de Material siguen siendo los que esos textos
 * vienen usando.
 */
internal fun tipografiaDeMaterial(familia: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.conLaFamilia() = copy(fontFamily = familia)
    return base.copy(
        displayLarge = base.displayLarge.conLaFamilia(),
        displayMedium = base.displayMedium.conLaFamilia(),
        displaySmall = base.displaySmall.conLaFamilia(),
        headlineLarge = base.headlineLarge.conLaFamilia(),
        headlineMedium = base.headlineMedium.conLaFamilia(),
        headlineSmall = base.headlineSmall.conLaFamilia(),
        titleLarge = base.titleLarge.conLaFamilia(),
        titleMedium = base.titleMedium.conLaFamilia(),
        titleSmall = base.titleSmall.conLaFamilia(),
        bodyLarge = base.bodyLarge.conLaFamilia(),
        bodyMedium = base.bodyMedium.conLaFamilia(),
        bodySmall = base.bodySmall.conLaFamilia(),
        labelLarge = base.labelLarge.conLaFamilia(),
        labelMedium = base.labelMedium.conLaFamilia(),
        labelSmall = base.labelSmall.conLaFamilia(),
    )
}
