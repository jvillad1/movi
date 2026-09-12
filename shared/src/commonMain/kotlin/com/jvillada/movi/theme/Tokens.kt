package com.jvillada.movi.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * # El sistema de diseño de Movi
 *
 * ## El problema que resuelve, medido
 *
 * Antes de este archivo Movi tenía un sistema de **color** y nada más. Todo lo demás se decidía de
 * nuevo en cada pantalla, y el resultado en 31.000 líneas de interfaz era:
 *
 * | | cuántos distintos |
 * |---|---|
 * | tamaños de fuente escritos a mano | **25** |
 * | radios de esquina | **13** |
 * | valores de espaciado | **55** |
 * | temas | **1**, y es oscuro |
 *
 * Con 83 archivos decidiendo cada uno por su cuenta, la app se ve correcta y anónima a la vez: no
 * hay un sistema dándole carácter, hay 83 archivos poniéndose de acuerdo por casualidad.
 *
 * ## Qué cambia
 *
 * Cuatro escalas cortas y dos temas. Una pantalla nueva **no decide nada**: elige de una lista.
 *
 * ## Qué NO cambia todavía, a propósito
 *
 * Los `Min*` de `Color.kt` siguen existiendo y siguen valiendo lo mismo. Esta entrega agrega el
 * sistema y lo deja **provisto** por [MoviTheme]; migrar las pantallas es lo que viene después,
 * pantalla por pantalla. Meter las dos cosas en un solo cambio sería mover 83 archivos a ciegas.
 *
 * ## La parte que se defiende sola
 *
 * `ContrasteDeLosTokensTest` calcula el contraste WCAG de **cada par de tokens que puede
 * encontrarse en pantalla** y falla si alguno baja de 4,5:1. No es documentación: es una prueba.
 * Nadie puede agregar acá un color que no se lea, ni bajarle el contraste a uno existente sin que
 * el build se caiga.
 *
 * Hace falta porque el tema actual **ya tiene un color que no pasa**: `MinTextFaint` da 2,51:1
 * contra el fondo, y no es decorativo — lo usan los subtítulos, las pistas y la línea del rango del
 * período. Un sistema sin esa prueba habría repetido el error; de hecho las dos primeras versiones
 * de esta paleta lo repitieron, y la prueba es lo que las atrapó.
 */

// ─────────────────────────────────────────────────────────────────────────────
// COLOR — roles, no valores
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Los colores de Movi **por lo que significan**, no por cómo se ven.
 *
 * ### Dos capas, no tres
 *
 * [marca] hace de identidad **y** de acción: es el color de los botones, de la pestaña activa y de
 * los enlaces. Los colores de plata ([entra], [sale], [capital], [entreCuentas]) son otra cosa y
 * nunca se cruzan con él.
 *
 * Eso sale de mirar cómo lo resuelven los que lo tienen escrito en sus tokens. Wise usa **dos
 * verdes deliberados**, uno de marca y otro de «entró plata». Nubank separa marca de semántico en
 * el código, y su morado nunca significa dinero. Monzo lo explica mejor que nadie: su marca es un
 * coral rojizo, y en la interfaz «los rojos suelen significar que algo salió horriblemente mal»,
 * así que necesitan **tres** capas — marca, acción y dinero.
 *
 * Movi necesita dos porque su lavanda no choca con ningún color de plata. Y tiene la ventaja de que
 * el lavanda ya es el color que la app tiene hoy: esto no cambia la identidad, la ordena.
 *
 * ### Tres niveles de texto, no cuatro
 *
 * El tema actual tiene cuatro y el cuarto no pasa accesibilidad. Al armar esta paleta apareció el
 * mismo problema: un cuarto nivel daba 4,29:1 sobre tarjeta. En vez de maquillarlo se sacó.
 * **Tres niveles que dicen la verdad valen más que cuatro donde uno miente.**
 *
 * ### El color nunca es la única pista
 *
 * Un monto lleva signo solo cuando **entra** plata, y el subtítulo de cada fila dice el verbo
 * («Gastado», «Recibido», «abona tanto a capital»). Es la lección de Wise, y es lo que hace que
 * esto funcione para alguien que no distingue el lavanda del azul — los dos tokens tienen
 * luminancia parecida a propósito, porque nunca compiten en el mismo lugar.
 */
@Immutable
data class ColoresDeMovi(
    /** El lienzo de la app. */
    val fondo: Color,
    /** Un plano por encima del fondo: tarjetas, hojas, filas agrupadas. */
    val tarjeta: Color,
    /** El borde que de verdad separa los planos. Ver el KDoc de [COLORES_OSCUROS]. */
    val borde: Color,
    /** El separador entre filas de una misma tarjeta. */
    val hilo: Color,
    /** Texto principal. */
    val texto: Color,
    /** Texto de apoyo que igual se lee cómodo: montos secundarios, descripciones. */
    val textoMedio: Color,
    /** El nivel más tenue que **pasa**. No hay un cuarto. */
    val textoApagado: Color,
    /** Identidad y acción a la vez. **Nunca significa plata.** */
    val marca: Color,
    /** Lo que va encima de [marca]: el ícono del botón flotante, el texto del chip activo. */
    val sobreMarca: Color,
    /** Plata que entró al bolsillo. */
    val entra: Color,
    /** Plata que salió, y el interés de una cuota. */
    val sale: Color,
    /** La parte de una cuota que sí baja la deuda. */
    val capital: Color,
    /** Algo por vencer o por confirmar. */
    val aviso: Color,
    /** Plata que fue de una cuenta suya a otra: traspaso, cuota, pago de tarjeta. */
    val entreCuentas: Color,
    /** Relleno de gráficos para lo que no tiene color propio. */
    val neutro: Color,
)

/**
 * El tema oscuro, que es el de todos los días.
 *
 * **La tarjeta está en `#161D25` y no en algo más oscuro por una medición.** La primera versión de
 * esta paleta la puso en `#0D1116`, que da 1,05:1 contra el fondo — o sea **más plana que el
 * 1,15:1 que ya tenía Movi**. Subirla a `#161D25` la lleva a 1,17:1, y el borde a 1,49:1 termina de
 * separar los planos.
 *
 * Eso deja una lección que vale escribir: en una paleta de poco contraste **la profundidad no la da
 * la luminancia de la superficie, la dan la superficie y el borde juntos**. Un solo hexadecimal no
 * alcanza.
 */
val COLORES_OSCUROS = ColoresDeMovi(
    fondo         = Color(0xFF07090C),
    tarjeta       = Color(0xFF161D25),
    borde         = Color(0xFF26303B),
    hilo          = Color(0xFF1C242D),
    texto         = Color(0xFFEAEEF2),
    textoMedio    = Color(0xFFB4BEC9),
    textoApagado  = Color(0xFF95A0AD),
    marca         = Color(0xFFB9A0FF),
    sobreMarca    = Color(0xFF140F26),
    entra         = Color(0xFF7FD4A8),
    sale          = Color(0xFFF08A7C),
    capital       = Color(0xFFE8A96A),
    aviso         = Color(0xFFE8C06A),
    entreCuentas  = Color(0xFF8FB8E8),
    neutro        = Color(0xFF4A5763),
)

/**
 * El tema claro, **que hoy no existe** y es el hueco más grande del diseño actual: `MoviTheme`
 * arranca un `darkColorScheme` y punto.
 *
 * No es un tema aparte: son los mismos roles con otros valores. Que se pueda generar así es lo que
 * prueba que esto es un sistema y no dos diseños.
 *
 * La lección viene de Bancolombia: su marca se ancla en blanco y negro con el amarillo como
 * **acento**, y por eso pudieron sumar modo oscuro sin rediseñar nada. Una identidad construida
 * sobre un color saturado de fondo no se puede invertir; una construida sobre neutros sí.
 *
 * ## Cuatro valores que los puso la prueba, no el ojo
 *
 * `ContrasteDeLosTokensTest` rechazó la primera versión de este tema y por eso el fondo es más
 * oscuro de lo que uno elegiría mirando:
 *
 * - **fondo `#E9ECF1`** — con el `#F2F4F7` original, la tarjeta blanca daba 1,1:1 contra el fondo,
 *   o sea *más plana* que el tema oscuro que ya existe. Ahora da 1,18:1. Es el mismo error que
 *   cometió la primera dirección B en oscuro, repetido del otro lado.
 * - **borde `#CCD3DC`** — daba 1,14:1 contra el fondo nuevo. Un borde que no se ve no separa nada.
 * - **hilo `#DDE2E9`** — bajó con el borde, para que la jerarquía se mantenga: el hilo es más
 *   callado que el borde, no igual de callado que el fondo.
 * - **aviso `#7F5D0F`** — el `#8A6410` pasaba por 0,03. Pasar raspando no es pasar: cualquier
 *   retoque futuro del fondo lo tumbaba.
 */
val COLORES_CLAROS = ColoresDeMovi(
    fondo         = Color(0xFFE9ECF1),
    tarjeta       = Color(0xFFFFFFFF),
    borde         = Color(0xFFCCD3DC),
    hilo          = Color(0xFFDDE2E9),
    texto         = Color(0xFF0D1218),
    textoMedio    = Color(0xFF3D4752),
    textoApagado  = Color(0xFF5C6873),
    marca         = Color(0xFF5636B8),
    sobreMarca    = Color(0xFFFFFFFF),
    entra         = Color(0xFF117050),
    sale          = Color(0xFFB3392A),
    capital       = Color(0xFF8A5A18),
    aviso         = Color(0xFF7F5D0F),
    entreCuentas  = Color(0xFF1F5B96),
    neutro        = Color(0xFF98A3AE),
)

// ─────────────────────────────────────────────────────────────────────────────
// ESPACIADO — de 55 valores a 7
// ─────────────────────────────────────────────────────────────────────────────

/**
 * La escala de espaciado. **Siete pasos**, contra los 55 valores distintos que hay hoy.
 *
 * No es una progresión geométrica pura porque la interfaz no la necesita: los cuatro primeros pasos
 * viven dentro de una fila o una tarjeta, y los tres últimos separan bloques. Ahí es donde de
 * verdad hace falta resolución.
 */
@Immutable
data class EspaciosDeMovi(
    /** Entre un ícono y su rótulo, entre dos líneas de la misma fila. */
    val minimo: Dp = 4.dp,
    /** Entre elementos hermanos apretados: chips, puntos de leyenda. */
    val corto: Dp = 8.dp,
    /** El respiro de adentro de una fila. */
    val medio: Dp = 12.dp,
    /** El relleno de una tarjeta, y la separación entre tarjetas hermanas. */
    val amplio: Dp = 16.dp,
    /** El margen lateral de una pantalla. */
    val margen: Dp = 20.dp,
    /** Entre dos secciones que hablan de cosas distintas. */
    val seccion: Dp = 32.dp,
    /** Debajo de la barra de estado, antes del encabezado. */
    val encabezado: Dp = 52.dp,
)

// ─────────────────────────────────────────────────────────────────────────────
// FORMAS — de 13 radios a 4
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Los radios de esquina. **Cuatro**, contra los 13 que hay hoy.
 *
 * [pleno] es 999.dp a propósito y no un porcentaje: en Compose un `RoundedCornerShape` con un radio
 * mayor que la mitad del lado se recorta al lado, así que 999 siempre da la píldora completa sin
 * tener que saber el alto.
 */
@Immutable
data class FormasDeMovi(
    /** Cuadraditos: puntos de leyenda, muestras de color, chips chicos. */
    val minima: Dp = 6.dp,
    /** Lo habitual: filas, tarjetas, campos. */
    val normal: Dp = 11.dp,
    /** Contenedores grandes: hojas, tarjetas destacadas. */
    val amplia: Dp = 16.dp,
    /** Píldoras y círculos. */
    val pleno: Dp = 999.dp,
)

// ─────────────────────────────────────────────────────────────────────────────
// TIPOGRAFÍA — de 25 tamaños a 7
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Los siete estilos de texto, contra los 25 tamaños sueltos que hay hoy.
 *
 * ### Las cifras van tabulares, no monoespaciadas
 *
 * Hoy Movi pone **todos** los números en monoespaciada, y eso es lo que le da aire de terminal en
 * vez de producto de plata. Monoespaciada y tabular no son lo mismo: para que una columna de montos
 * alinee alcanza con cifras tabulares, que casi toda tipografía moderna trae. Eso es
 * `fontFeatureSettings = "tnum"`, y va en [monto] y en [cifra].
 *
 * Copilot Money —el referente de oficio del sector— las declara explícitamente; de las otras tres
 * apps de finanzas personales del benchmark, **ninguna** lo hace, incluida YNAB, cuya pantalla
 * central es literalmente una tabla de montos.
 *
 * ### Todavía sin tipografía propia
 *
 * Estos estilos no fijan `fontFamily`: heredan la del sistema. Empaquetar Space Grotesk y Martian
 * Mono para Android, iOS y wasm es su propia entrega, y no tiene sentido bloquear la escala
 * esperándola. Cinco de los seis neobancos del benchmark tienen fuente propia con ejes Display y
 * Text separados, así que es a dónde va esto — pero después.
 */
@Immutable
data class TextosDeMovi(
    /** El número protagonista de una pantalla. Uno por pantalla, o ninguno. */
    val cifra: TextStyle = TextStyle(
        fontSize = 42.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.9).sp,
        fontFeatureSettings = "tnum",
    ),
    /** El título de una pantalla. */
    val titular: TextStyle = TextStyle(
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp,
    ),
    /** El título de una tarjeta o de un bloque. */
    val titulo: TextStyle = TextStyle(
        fontSize = 15.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    /** El texto de una fila. */
    val cuerpo: TextStyle = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    /** Un monto en una fila. Tabular, para que la columna no baile. */
    val monto: TextStyle = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    ),
    /** El subtítulo de una fila, que dice algo que no está en ninguna otra parte. */
    val apoyo: TextStyle = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    /** El rótulo de una sección, en mayúsculas y espaciado. */
    val rotulo: TextStyle = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.7.sp,
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// CÓMO SE LLEGA A ELLOS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * `staticCompositionLocalOf` y no `compositionLocalOf`: estos valores cambian **una vez**, cuando
 * el dueño alterna el tema. La variante estática no instala lectores, así que leerla en una fila de
 * una lista de trescientos movimientos no cuesta nada; la dinámica recompondría a cada lector ante
 * cualquier cambio, que es justo lo que no hace falta acá.
 *
 * El default es el tema oscuro porque es el único que Movi tiene hoy: si alguien lee esto fuera de
 * [MoviTheme] —una vista previa, una prueba— ve lo mismo que ve la app.
 */
val LocalColores: ProvidableCompositionLocal<ColoresDeMovi> =
    staticCompositionLocalOf { COLORES_OSCUROS }

val LocalEspacios: ProvidableCompositionLocal<EspaciosDeMovi> =
    staticCompositionLocalOf { EspaciosDeMovi() }

val LocalFormas: ProvidableCompositionLocal<FormasDeMovi> =
    staticCompositionLocalOf { FormasDeMovi() }

val LocalTextos: ProvidableCompositionLocal<TextosDeMovi> =
    staticCompositionLocalOf { TextosDeMovi() }

/**
 * El atajo para leer el sistema desde una pantalla: `Movi.colores.entra`, `Movi.espacios.margen`.
 *
 * Un `object` con propiedades `@Composable` en vez de cuatro `LocalX.current` sueltos, por lo mismo
 * que `MaterialTheme` hace igual: en el punto de uso se lee como una sola cosa, y si mañana se
 * agrega una quinta escala no hay que tocar ningún llamado.
 */
object Movi {
    val colores: ColoresDeMovi
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalColores.current

    val espacios: EspaciosDeMovi
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalEspacios.current

    val formas: FormasDeMovi
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalFormas.current

    val textos: TextosDeMovi
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalTextos.current
}
