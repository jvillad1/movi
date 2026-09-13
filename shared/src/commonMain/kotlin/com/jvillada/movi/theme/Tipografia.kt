package com.jvillada.movi.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.resources.Res
import com.jvillada.movi.resources.martian_mono_medium
import com.jvillada.movi.resources.martian_mono_regular
import com.jvillada.movi.resources.space_grotesk_medium
import com.jvillada.movi.resources.space_grotesk_regular
import com.jvillada.movi.resources.space_grotesk_semibold
import org.jetbrains.compose.resources.Font

/**
 * # La letra de Movi
 *
 * **Space Grotesk lleva la interfaz. Martian Mono aparece solo en la cifra protagonista.** Es la
 * decisión de la dirección B, escrita en el tablero del sistema: a 42 sp el ancho de una
 * monoespaciada es carácter; en una fila de una lista sería estorbo, y ahí alcanza con las cifras
 * tabulares que Space Grotesk ya trae.
 *
 * ### Por qué fuente propia
 *
 * Cinco de los seis neobancos del benchmark la tienen, y Bancolombia fue más lejos: uno de los
 * cuatro cortes de su tipografía existe solo para los números. Con la del sistema, Movi se veía
 * distinta en cada plataforma —Roboto en Android, San Francisco en iOS, lo que tuviera el
 * navegador en la web— y en ninguna se veía como Movi.
 *
 * ### Los archivos
 *
 * **Android y la web, no iOS todavía**: ver [laPlataformaTraeLasFuentes].
 *
 * Cinco TTF estáticos, no las versiones variables: Compose en wasm e iOS no garantiza aplicar los
 * ejes de variación, y un peso que no se aplica cae en silencio al regular. Juntos pesan 300 KB.
 * Cubren todo el español y los signos que Movi escribe: tildes, eñe, «», el − de los montos, ≈ y
 * el $. Los dos son SIL Open Font License; los textos están en `docs/licencias/`.
 *
 * `Font(recurso)` es `@Composable` —en la web se carga por red— y por eso las familias se arman
 * acá y no como constantes en `Tokens.kt`.
 */
/**
 * ¿Los archivos de fuente llegan al aparato? En Android y la web sí. En iOS todavía no: ver el
 * `actual` de iOS, que explica por qué preguntar esto evita que la app se caiga al abrir.
 */
internal expect val laPlataformaTraeLasFuentes: Boolean

@Composable
internal fun familiaDeLaInterfaz(): FontFamily {
    if (!laPlataformaTraeLasFuentes) return FontFamily.Default
    return FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
    )
}

@Composable
internal fun familiaDeLasCifras(): FontFamily {
    // Sin fuentes propias, la cifra no cae a la monoespaciada del sistema: esa era justamente la
    // terminal que el rediseño vino a sacar. Va en la de la interfaz, con sus cifras tabulares.
    if (!laPlataformaTraeLasFuentes) return FontFamily.Default
    return FontFamily(
        Font(Res.font.martian_mono_regular, FontWeight.Normal),
        Font(Res.font.martian_mono_medium, FontWeight.Medium),
        // Martian Mono no tiene semibold en los archivos que trae Movi. Sin esta línea, el `cifra`
        // semibold lo sintetizaría engrosando el trazo del medium, que es justo lo que se ve barato.
        Font(Res.font.martian_mono_medium, FontWeight.SemiBold),
    )
}

/**
 * Los siete estilos con su familia puesta. **Pura**, sin `@Composable`, para poder probarla.
 *
 * `cifra` va en la de cifras; los otros seis, en la de la interfaz. `monto` también en la de la
 * interfaz: es un monto en una fila, y ahí manda `tnum`, no la monoespaciada.
 */
internal fun TextosDeMovi.conFamilias(interfaz: FontFamily, cifras: FontFamily): TextosDeMovi = copy(
    cifra = cifra.copy(fontFamily = cifras),
    titular = titular.copy(fontFamily = interfaz),
    titulo = titulo.copy(fontFamily = interfaz),
    cuerpo = cuerpo.copy(fontFamily = interfaz),
    monto = monto.copy(fontFamily = interfaz),
    apoyo = apoyo.copy(fontFamily = interfaz),
    rotulo = rotulo.copy(fontFamily = interfaz),
)

/**
 * **La tipografía de Material con la familia de Movi.**
 *
 * No es un detalle. Todo `Text` que no pasa `style` —y en la app quedan cientos— hereda el
 * `LocalTextStyle`, que `MaterialTheme` llena con `bodyLarge`. Si esto no se toca, los estilos
 * del sistema salen en Space Grotesk y todo lo demás sigue en la letra del sistema: la misma
 * pantalla con dos tipografías.
 *
 * Se cambia solo la familia, no los tamaños: los de Material siguen siendo los que esos textos
 * vienen usando, y unificarlos es la migración de los tamaños sueltos, que es otra entrega.
 */
internal fun tipografiaDeMaterial(interfaz: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.conLaFamilia() = copy(fontFamily = interfaz)
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
