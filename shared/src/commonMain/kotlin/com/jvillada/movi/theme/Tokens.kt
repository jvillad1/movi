package com.jvillada.movi.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
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
 * Cuatro escalas cortas y un tema (desde la vuelta 29, el claro de la opción 3; ver [COLORES_CLAROS]).
 * Una pantalla nueva **no decide nada**: elige de una lista.
 *
 * ## Y ya no hay otro juego de colores
 *
 * `Color.kt` —los 23 `Min*` de nivel superior— **se borró**. No por prolijidad: mientras
 * existiera, el tema claro era imposible. Un `val` de nivel superior es un valor por proceso, así
 * que ningún tema puede moverlo, y cualquier pantalla que siguiera usándolos habría quedado oscura
 * adentro de una app clara.
 *
 * ## La parte que se defiende sola
 *
 * `ContrasteDeLosTokensTest` calcula el contraste WCAG de **cada par de tokens que puede
 * encontrarse en pantalla** y falla si alguno baja de 4,5:1. No es documentación: es una prueba.
 * Nadie puede agregar acá un color que no se lea, ni bajarle el contraste a uno existente sin que
 * el build se caiga.
 *
 * Hace falta porque el tema actual **ya tiene un color que no pasa**: `MinTextFaint` (el de antes) da 2,51:1
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
 * ## La opción 3 «Acción primero» (vuelta 29, 28-oct-2026)
 *
 * El dueño eligió la opción 3 del lienzo «Movi · 5 caminos» —«me encantó a mí y a mi esposa mucho
 * más que 1, 2 y 4»— y con ella **un solo tema, claro**: fondo crema, tarjetas blancas, tinta casi
 * negra y un amarillo que es la acción. Reemplaza al camino 1 («Noche premium», oscuro con lavanda)
 * y al tema oscuro entero: ya no hay `COLORES_OSCUROS`, ni preferencia de tema en Ajustes.
 *
 * ### La marca ya no es texto
 *
 * Hasta acá [marca] (el lavanda) era identidad, acción **y** el color de los enlaces. El amarillo
 * `#FFC21A` no puede ser texto: da 1,6:1 contra el blanco. Así que la marca es **un relleno** —el
 * botón principal, la acción destacada, la píldora de la pestaña activa— con [sobreMarca] (la
 * tinta) encima, y lo que antes era «texto en la marca» (un enlace, «Ver todo», un botón de texto,
 * la acción de un encabezado) pasa a [enlace], el azul de la maqueta. La regla para una pantalla:
 * **la marca va de fondo, nunca de letra.** `ContrasteDeLosTokensTest` lo sostiene: mide la tinta
 * sobre la marca y el enlace sobre cada superficie, y la marca ya no está en la lista de textos.
 *
 * ### Tres niveles de texto, no cuatro
 *
 * [texto] (`#1F1B16`), [textoMedio] (`#5C554C`, el apoyo de la maqueta) y [textoApagado], el más
 * tenue que **pasa** 4,5:1 sobre todas las capas. Un cuarto nivel no pasaba; no lo hay.
 *
 * ### El color nunca es la única pista
 *
 * Un monto lleva signo solo cuando **entra** plata, y el subtítulo de cada fila dice el verbo
 * («Gastado», «Recibido», «abona tanto a capital»). Es la lección de Wise.
 */
@Immutable
data class ColoresDeMovi(
    /** El lienzo de la app: la capa 0. El crema de la maqueta. */
    val fondo: Color,
    /**
     * Un plano por encima del fondo: tarjetas, hojas, filas agrupadas. **Es la capa 1** (ver
     * [capa1]): se sigue llamando así porque lo nombran cientos de llamadas. Blanco.
     */
    val tarjeta: Color,
    /**
     * **La capa 2, «la superficie suave»**: lo que va dentro de una tarjeta y se distingue de ella sin
     * ser otra tarjeta —la franja de abajo de una tarjeta («Por confirmar» debajo del Disponible), un
     * campo—. En la opción 3 casi no se despega del blanco: lo que la separa es el [hilo] de arriba.
     */
    val capa2: Color,
    /**
     * **La capa 3**: la pista de una barra de progreso, una píldora neutra, un botón apagado. Se lee
     * texto encima (la prueba de contraste lo mide).
     */
    val capa3: Color,
    /**
     * **La superficie de una propuesta de Movi.** En la opción 3 es blanca como una tarjeta y la
     * distingue la **sombra** (la de la tarjeta de avisos de la maqueta), que `Superficie` le pone sola
     * (`NivelDeSuperficie.Propuesta`), y el ícono en un círculo amarillo. No es un color de acción: el
     * botón de adentro sí lo es.
     */
    val propuesta: Color,
    /**
     * El borde de lo que se recorta contra el fondo sin ser una tarjeta: el círculo de una acción
     * rápida, un botón redondo de encabezado, el borde de arriba de la barra de pestañas.
     */
    val borde: Color,
    /** El borde de una píldora o un filtro sin elegir. Un paso más firme que [borde]. */
    val bordeDePildora: Color,
    /** El borde de un botón secundario. El más firme de los tres: es un control. */
    val bordeDeBoton: Color,
    /** El separador entre filas de una misma tarjeta. */
    val hilo: Color,
    /** Texto principal: la tinta. También el fondo de un filtro elegido y de una insignia. */
    val texto: Color,
    /** Texto de apoyo que igual se lee cómodo: subtítulos, montos secundarios, descripciones. */
    val textoMedio: Color,
    /** El nivel más tenue que **pasa**. No hay un cuarto. */
    val textoApagado: Color,
    /**
     * **Identidad y acción, como relleno.** El botón principal, la acción destacada, la píldora de la
     * pestaña activa, el botón flotante. **Nunca va de texto** (ver [enlace]) **y nunca significa
     * plata.**
     */
    val marca: Color,
    /** Lo que va encima de [marca] (y de [marcaSuave]): la tinta. */
    val sobreMarca: Color,
    /** El texto secundario sobre la marca: la fecha bajo el saludo, «de $X · quedan 4 de 7 pagos». */
    val sobreMarcaMedio: Color,
    /** El amarillo claro: los botones redondos del encabezado sobre la franja amarilla. */
    val marcaSuave: Color,
    /**
     * **El tinte**: el fondo de un aviso y de «por confirmar», con [aviso] encima. Es también el
     * [lavado] de [marca] y de [aviso].
     */
    val tinte: Color,
    /** Enlaces y acciones de texto: «Ver toda la actividad», «Tu plata $X», un botón fantasma. */
    val enlace: Color,
    /** Plata que entró al bolsillo. */
    val entra: Color,
    /** Plata que salió y se pasó, y lo que no tiene vuelta: la alerta. Nunca un gasto común. */
    val sale: Color,
    /** La parte de una cuota que sí baja la deuda. */
    val capital: Color,
    /** Algo por vencer o por confirmar: el texto sobre el [tinte]. */
    val aviso: Color,
    /**
     * Plata que fue de una cuenta suya a otra: traspaso, cuota, pago de tarjeta. Vuelta 29: un gris
     * azulado (`#556372`) y no un azul: el `#1F5B96` de antes se leía como el [enlace].
     */
    val entreCuentas: Color,
    /** Relleno de gráficos para lo que no tiene color propio. */
    val neutro: Color,
    /** Los diez colores con los que se pinta una categoría. Ver [ColoresDeCategoria]. */
    val categorias: ColoresDeCategoria,
    /**
     * **Cuánto color lleva un lavado** (ver [lavado]). Con el 10 % el verde de [entra] sobre su
     * lavado encima de la capa 2 daba 4,47:1 con el `#14794A` de la maqueta; por eso [entra] está un
     * paso más oscuro (ver [COLORES_CLAROS]).
     */
    val alfaDelLavado: Float,
    /**
     * **El color de la sombra** de una superficie destacada (el héroe, una propuesta): la tinta. La
     * sombra la dibuja la plataforma con su propia opacidad, así que va sin alfa.
     */
    val sombra: Color,
    /**
     * **Lo que va encima del círculo de un avatar** (vuelta de diseño 18): las iniciales y la
     * verificación del color elegido. Blanco, a propósito: el color del círculo no es del tema sino
     * del dueño (`AvatarPalette`, en `:core`), y esa paleta se armó entera con tonos «600» para llevar
     * blanco encima. `ContrasteDeLosTokensTest` mide cada color de la paleta contra este.
     */
    val sobreAvatar: Color = Color(0xFFFFFFFF),
    /**
     * **La marca presionada** (vuelta 79): el tono del `Boton` principal mientras se presiona **con el
     * sistema pidiendo menos movimiento** (sin la escala, el toque tiene que verse en algo). Un amarillo
     * un paso más hondo, nunca un velo de tinta encima: la tinta sobre el amarillo lo volvía mostaza.
     * `ContrasteDeLosTokensTest` mide la tinta encima.
     */
    val marcaPresionada: Color = Color(0xFFF5B800),
    /**
     * **El velo de un toque** (vuelta 79): lo que se pone encima de una superficie neutra mientras el
     * mouse está encima (web) o mientras se presiona con menos movimiento. La tinta al 6 %, recortada a
     * la forma del componente (`Modifier.alPresionar`). **Nunca va sobre el amarillo.**
     */
    val velo: Color = Color(0x0F1F1B16),
    /**
     * **La letra sobre la tinta** (vuelta 79): el aviso «Anotado» de abajo es un snackbar oscuro, con
     * el fondo en [texto] y la letra en el crema del [fondo].
     */
    val sobreTinta: Color = Color(0xFFF4F1EC),
    /** Las acciones de texto sobre la tinta («Deshacer», «Repartir»): el [enlace], aclarado. */
    val enlaceSobreTinta: Color = Color(0xFFA9C7F5),
    /** Una falla dicha sobre la tinta (el aviso que no pudo deshacer): el [sale], aclarado. */
    val saleSobreTinta: Color = Color(0xFFFFB4AB),
) {
    /**
     * El color de una categoría **por su clave** (`"naranja"`, `"ambar"`…): el del ícono, y el de
     * la barra si la categoría tiene una. Una clave desconocida da `gris` — ver
     * [ColoresDeCategoria.color].
     */
    fun categoria(clave: String?): Color = categorias.color(clave)

    /** El círculo que va detrás del ícono de esa categoría: el mismo color, con alfa bajo. */
    fun circuloDeCategoria(clave: String?): Color = categorias.circulo(clave)

    /**
     * **El fondo lavado de un color**: una píldora de estado, el círculo de un ícono, el botón de
     * peligro. Encima va el mismo color entero, y `ContrasteDeLosTokensTest` mide que se lea.
     *
     * La [marca] y el [aviso] no se lavan con alfa: su fondo es el [tinte] de la maqueta (`#FFF2C6`),
     * que es lo que un amarillo al 10 % habría querido ser.
     */
    fun lavado(color: Color): Color =
        if (color == marca || color == aviso) tinte else color.copy(alpha = alfaDelLavado)

    /**
     * **La capa 1, con el nombre del sistema de capas.** Es [tarjeta] y nada más: un solo valor con
     * dos nombres, para que no puedan separarse.
     */
    val capa1: Color get() = tarjeta
}

// ─────────────────────────────────────────────────────────────────────────────
// CATEGORÍAS — diez colores con nombre
// ─────────────────────────────────────────────────────────────────────────────

// Antes del tema y no después: son `val` de nivel superior del mismo archivo, que se inicializan en
// orden de aparición. Declaradas más abajo, `COLORES_CLAROS` las leería en `null`.

/** Un color del catálogo de categorías, como lo guarda el server y como lo lee el dueño. */
@Immutable
data class ColorDelCatalogo(
    /** Lo que viaja y se guarda (`category_prefs.color`, 20 caracteres): corto, ASCII, sin tildes. */
    val clave: String,
    /** Cómo se dice en pantalla. */
    val rotulo: String,
)

/** La clave del color que toma una categoría que no se sabe pintar. */
const val COLOR_DE_CATEGORIA_RESPALDO = "gris"

/**
 * Los diez colores que el dueño puede elegir para una categoría, **en el orden del selector**.
 *
 * Son claves y no colores porque lo que se guarda en el server es la clave: el dueño elige
 * «naranja» una vez, y si mañana el tono cambia, su categoría lo sigue.
 */
val COLORES_DEL_CATALOGO: List<ColorDelCatalogo> = listOf(
    ColorDelCatalogo("naranja", "Naranja"),
    ColorDelCatalogo("rojo", "Rojo"),
    ColorDelCatalogo("rosa", "Rosa"),
    ColorDelCatalogo("violeta", "Violeta"),
    ColorDelCatalogo("azul", "Azul"),
    ColorDelCatalogo("celeste", "Celeste"),
    ColorDelCatalogo("verde", "Verde"),
    ColorDelCatalogo("lima", "Lima"),
    ColorDelCatalogo("ambar", "Ámbar"),
    ColorDelCatalogo(COLOR_DE_CATEGORIA_RESPALDO, "Gris"),
)

/** ¿Es una clave de [COLORES_DEL_CATALOGO]? Una desconocida puede venir de una versión más nueva. */
fun esColorDelCatalogo(clave: String?): Boolean = COLORES_DEL_CATALOGO.any { it.clave == clave }

/**
 * Los colores de las categorías.
 *
 * ### Pensados para un solo dibujo
 *
 * Un ícono pintado con el color, sobre un círculo **del mismo color con alfa bajo**
 * ([alfaDelCirculo]), encima de la tarjeta o del fondo. Es el dibujo que usa `IconoDeCategoria`, y
 * `ContrasteDeLosTokensTest` mide exactamente eso: el ícono contra la tarjeta, contra el fondo y
 * contra el círculo ya compuesto sobre cada uno, con el 3:1 que WCAG pide para gráficos que no son
 * texto.
 *
 * ### No son colores de plata
 *
 * El verde de una categoría **no** es [ColoresDeMovi.entra], y el ámbar no es
 * [ColoresDeMovi.aviso], aunque se parezcan: una categoría es un nombre, no un sentido del
 * dinero. Están a propósito corridos de esos tokens, para que nadie tenga la tentación de usar uno
 * por el otro.
 *
 * ### Por qué un `when` y no un mapa
 *
 * Diez campos con nombre hacen que olvidarse un color sea un error de compilación, no un gris que
 * aparece en silencio. El `when` de [color] es la única traducción de clave a campo.
 */
@Immutable
data class ColoresDeCategoria(
    val naranja: Color,
    val rojo: Color,
    val rosa: Color,
    val violeta: Color,
    val azul: Color,
    val celeste: Color,
    val verde: Color,
    val lima: Color,
    val ambar: Color,
    val gris: Color,
    /** Cuánto del color lleva el círculo de detrás del ícono. */
    val alfaDelCirculo: Float,
) {
    /** El color de una clave del catálogo. Una clave desconocida —o `null`— da [gris]. */
    fun color(clave: String?): Color = when (clave) {
        "naranja" -> naranja
        "rojo" -> rojo
        "rosa" -> rosa
        "violeta" -> violeta
        "azul" -> azul
        "celeste" -> celeste
        "verde" -> verde
        "lima" -> lima
        "ambar" -> ambar
        else -> gris
    }

    /** El círculo de detrás del ícono: [color] con [alfaDelCirculo]. */
    fun circulo(clave: String?): Color = color(clave).copy(alpha = alfaDelCirculo)
}

/**
 * Oscuros, porque el ícono va sobre blanco **y** sobre su círculo teñido: el círculo lo aclara
 * todo un poco, así que un color que pasa raspando contra el blanco deja de pasar contra él. Lima
 * y ámbar son los que más lo sienten — un lima o un ámbar «de verdad» no llega a 3:1 sobre blanco,
 * y por eso acá son un oliva y un ocre. El gris es cálido, como el resto de la paleta.
 */
val CATEGORIAS_CLARAS = ColoresDeCategoria(
    naranja = Color(0xFFA94E12),
    rojo    = Color(0xFFB0302F),
    rosa    = Color(0xFFA3346C),
    violeta = Color(0xFF6A42B5),
    azul    = Color(0xFF2059A8),
    celeste = Color(0xFF0E6A80),
    verde   = Color(0xFF1E7445),
    lima    = Color(0xFF4B6614),
    ambar   = Color(0xFF835A06),
    gris    = Color(0xFF6A6257),
    alfaDelCirculo = 0.12f,
)

/**
 * **El tema de Movi, el único** (opción 3, vuelta 29). Los valores son los de la maqueta tal cual
 * (`project/p3/`, las cuatro `.dc.html` del lienzo «Movi · 5 caminos»), salvo donde la prueba de contraste pidió
 * otra cosa:
 *
 * - **[ColoresDeMovi.entra] `#147849`** y no el `#14794A` de la maqueta: sobre su lavado encima de la
 *   capa 2 daba 4,47:1. Un paso más oscuro da 4,53. (El par de la maqueta —el verde sobre
 *   `#E3F1E8`— daba 4,66 y sigue pasando.)
 * - **[ColoresDeMovi.sale] `#B3261E`**: la maqueta no trae rojo. Es solo para alertas, 6,54:1 sobre
 *   blanco y 5,80 sobre el crema.
 * - **[ColoresDeMovi.entreCuentas] `#556372`**: un gris azulado, porque el azul de antes se confundía
 *   con el enlace (5,27:1 en la superficie más difícil).
 * - **[ColoresDeMovi.textoApagado] `#6E665B`**: la maqueta tiene dos niveles de texto; el tercero es
 *   el más claro de la misma familia que pasa sobre la capa 3 (4,84:1).
 *
 * ### Planos con sombra, no con luminancia
 *
 * El blanco sobre el crema da 1,13:1 —menos que el 1,15 que exigía el camino 1—, y la capa 2
 * (`#FBF8F3`) apenas 1,06 sobre el blanco. Es la decisión de la maqueta: lo que despega una tarjeta
 * destacada es la **sombra** suave (el héroe y la tarjeta de avisos) y lo que separa la franja de
 * abajo de una tarjeta es el [ColoresDeMovi.hilo]. `ContrasteDeLosTokensTest` mide esos pisos nuevos.
 */
val COLORES_CLAROS = ColoresDeMovi(
    fondo          = Color(0xFFF4F1EC),
    tarjeta        = Color(0xFFFFFFFF),
    capa2          = Color(0xFFFBF8F3),
    capa3          = Color(0xFFF1EDE6),
    propuesta      = Color(0xFFFFFFFF),
    borde          = Color(0xFFE7E1D7),
    bordeDePildora = Color(0xFFE0D9CD),
    bordeDeBoton   = Color(0xFFD9D1C3),
    hilo           = Color(0xFFEFEAE1),
    texto          = Color(0xFF1F1B16),
    textoMedio     = Color(0xFF5C554C),
    textoApagado   = Color(0xFF6E665B),
    marca          = Color(0xFFFFC21A),
    sobreMarca     = Color(0xFF1F1B16),
    sobreMarcaMedio = Color(0xFF3D3220),
    marcaSuave     = Color(0xFFFFE08A),
    tinte          = Color(0xFFFFF2C6),
    enlace         = Color(0xFF1D4E9E),
    entra          = Color(0xFF147849),
    sale           = Color(0xFFB3261E),
    capital        = Color(0xFF8A5A18),
    aviso          = Color(0xFF6B4A00),
    entreCuentas   = Color(0xFF556372),
    neutro         = Color(0xFF857C6F),
    categorias     = CATEGORIAS_CLARAS,
    alfaDelLavado  = 0.10f,
    sombra         = Color(0xFF1F1B16),
)

// ─────────────────────────────────────────────────────────────────────────────
// ESPACIADO — de 55 valores a 7
// ─────────────────────────────────────────────────────────────────────────────

/**
 * La escala de espaciado. **Siete pasos**, contra los 55 valores distintos que hay hoy.
 *
 * **Desde la ola R0 son nombres viejos de la escala nueva** ([Espacio] y [Medida], en `Escalas.kt`):
 * los nombra media app y se quedan, pero sus valores salen de allá. Dos cambiaron con la R0, en toda
 * la app a la vez: el margen de 20 a 16 y la separación entre secciones de 32 a 24 (camino 1).
 *
 * No es una progresión geométrica pura porque la interfaz no la necesita: los cuatro primeros pasos
 * viven dentro de una fila o una tarjeta, y los tres últimos separan bloques. Ahí es donde de
 * verdad hace falta resolución.
 */
@Immutable
data class EspaciosDeMovi(
    /** Entre un ícono y su rótulo, entre dos líneas de la misma fila. [Espacio.xxs]. */
    val minimo: Dp = Espacio.xxs,
    /** Entre elementos hermanos apretados: chips, puntos de leyenda. [Espacio.xs]. */
    val corto: Dp = Espacio.xs,
    /** El respiro de adentro de una fila. [Espacio.s]. */
    val medio: Dp = Espacio.s,
    /** El relleno de una tarjeta, y la separación entre tarjetas hermanas. [Espacio.m]. */
    val amplio: Dp = Espacio.m,
    /** El margen lateral de una pantalla: [Medida.margenDePantalla] (era 20; desde la R0, 16). */
    val margen: Dp = Medida.margenDePantalla,
    /** Entre dos secciones que hablan de cosas distintas: [Medida.entreSecciones] (era 32; ahora 24). */
    val seccion: Dp = Medida.entreSecciones,
    /** El alto del encabezado de una pantalla: [Medida.altoDeEncabezado] (era 52; ahora 56). */
    val encabezado: Dp = Medida.altoDeEncabezado,
)

// ─────────────────────────────────────────────────────────────────────────────
// FORMAS — de 13 radios a 4
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Los radios de esquina. **Cuatro**, contra los 13 que hay hoy.
 *
 * **Desde la ola R0 salen de [Radio]** (`Escalas.kt`): 6 → 8 y 11 → 12, para quedar en la grilla de
 * 4, y se sumó [hoja] (24).
 *
 * [pleno] es 999.dp a propósito y no un porcentaje: en Compose un `RoundedCornerShape` con un radio
 * mayor que la mitad del lado se recorta al lado, así que 999 siempre da la píldora completa sin
 * tener que saber el alto.
 */
@Immutable
data class FormasDeMovi(
    /** Cuadraditos: puntos de leyenda, muestras de color, chips chicos. [Radio.s] (era 6). */
    val minima: Dp = Radio.s,
    /** Lo habitual: filas destacadas, botones, campos. [Radio.m] (era 11). */
    val normal: Dp = Radio.m,
    /** Superficies: tarjetas, listas agrupadas. [Radio.l]. */
    val amplia: Dp = Radio.l,
    /** Hojas y tarjetas de cuenta. [Radio.xl] (las hojas tenían 28 a mano). */
    val hoja: Dp = Radio.xl,
    /** Píldoras y círculos. [Radio.pill]. */
    val pleno: Dp = Radio.pill,
)

// ─────────────────────────────────────────────────────────────────────────────
// TIPOGRAFÍA — de 25 tamaños a 11
// ─────────────────────────────────────────────────────────────────────────────

/**
 * **Un renglón mide su interlineado, entero** (vuelta 29): `Trim.None`, centrado. Era la regla del
 * `monto` y pasó a todos los estilos: sin recorte, un texto de N renglones mide N × `lineHeight` con
 * cualquier letra, que es exactamente lo que reservan los esqueletos (`LineaEsqueleto`,
 * `altoDeUnRenglon`). Con Figtree y los tamaños nuevos, el recorte de siempre hacía que lo cargado y su
 * esqueleto difirieran de a unos dp por renglón, y lo de abajo saltaba al llegar el dato.
 */
val RENGLON_ENTERO: LineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * Los estilos de texto, contra los 25 tamaños sueltos que había: los siete de siempre, más
 * [cifraDeTarjeta] (ola R0), [portada] (vuelta de diseño 3) y, desde la vuelta 29, [titularDePantalla]
 * y [saludo].
 *
 * ### Figtree para todo, también las cifras (vuelta 29)
 *
 * La opción 3 escribe todo en **Figtree**, de 400 a 800, y las cifras en la misma letra con
 * **números tabulares** (`fontFeatureSettings = "tnum"`): una columna de montos alinea sin la
 * monoespaciada (Martian Mono) que el camino 1 ponía en cada cifra.
 *
 * ### En sp, la maqueta dividida por 1,12
 *
 * `App()` agranda toda la letra ×1,12 (`fontScale * 1.12f`, en las tres plataformas). La primera
 * versión de esta vuelta escribió acá los px de la maqueta tal cual, y en pantalla todo salía 12 %
 * más grande que en ella: «Bancolombia Ahorros» y «Mercado del sábado» en dos renglones, «Arrie…» en
 * el widget, «Hola, Demo» del tamaño de un título. Cada estilo es **la maqueta ÷ 1,12**, redondeada al
 * medio punto, para que **lo que se ve** mida lo de la maqueta (lo fija `TipografiaTest`):
 *
 * | estilo | sp | se ve como | maqueta |
 * |---|---|---|---|
 * | [cifra] | 36 / 800 | 40,3 | el héroe: 40/800, tracking −1,2 |
 * | [titular] | 25 / 800 | 28 | el título de una pestaña: 28/800 |
 * | [titularDePantalla] | 19,5 / 800 | 21,8 | el título de una pantalla con flecha: 22/800 |
 * | [saludo] | 15 / 700 | 16,8 | «Hola, Juan» y el nombre del perfil: 17/700 |
 * | [titulo] | 14,5 / 700 | 16,2 | el título de una sección: 16/700 |
 * | [cuerpo] | 13,5 / 600 | 15,1 | el título de una fila: 15/600 |
 * | [monto] | 13,5 / 700 | 15,1 | el monto de una fila: 15/700 |
 * | [apoyo] | 11 / 400 | 12,3 | el subtítulo: 12,5 |
 * | [rotulo] / [pestana] | 12 | 13,4 | (la barra de pestañas no se tocó: la rehace la vuelta 38) |
 * | [rotuloDeAccion] | 10,75 / 600 | 12 | el rótulo de una acción de Inicio: 12/600 (vuelta 39) |
 *
 * ### La familia se pone en el tema, no acá
 *
 * Estos estilos no fijan `fontFamily` porque cargar una fuente es `@Composable`. `MoviTheme` se
 * la pone con `conFamilia` (ver `Tipografia.kt`). Leídos fuera del tema —una prueba pura— no tienen
 * familia, y es a propósito.
 */
/**
 * **El tamaño más chico que se ve**, en px (vuelta 40): ningún texto de la app queda por debajo, con la
 * escala ×1,12 de `App()` aplicada. Lo vigila `TipografiaTest`; el más chico hoy es [TextosDeMovi.apoyo]
 * (11 sp → 12,3).
 */
const val LETRA_MINIMA_QUE_SE_VE: Float = 12f

@Immutable
data class TextosDeMovi(
    /** El número protagonista de una pantalla. Uno por pantalla, o ninguno. */
    val cifra: TextStyle = TextStyle(
        fontSize = 36.sp,
        lineHeight = 41.sp,
        // La única sin `RENGLON_ENTERO`: a 36 sp la letra es más alta que su interlineado, y con el
        // recorte de siempre el renglón mide la letra. Es la que dibuja la hoja de Agregar, anclada
        // abajo: con el renglón entero el pie se salía a 360 × 740.
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.1).sp,
        fontFeatureSettings = "tnum",
    ),
    /**
     * **La cifra de una tarjeta** (camino 1): el saldo dentro de la tarjeta de un banco, la cifra de un
     * widget, «Falta por pagar». El piso al que se achica la cifra héroe antes de dejar de ser
     * protagonista (`CifraProtagonista`, `ds.Cifra`).
     */
    val cifraDeTarjeta: TextStyle = TextStyle(
        fontSize = 21.5.sp,
        lineHeight = 27.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    ),
    /**
     * **El título de una pantalla de una sola cosa** (vuelta de diseño 3): el logotipo de la
     * entrada («Movi») y la bienvenida de la guía de primer uso. No es para una cifra —eso es
     * [cifra]— ni para el título de una pantalla con contenido —eso es [titular]—.
     */
    val portada: TextStyle = TextStyle(
        fontSize = 28.5.sp,
        lineHeight = 34.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.7).sp,
    ),
    /**
     * **El título de una pestaña** («Actividad», «Pagos», «Patrimonio»): el encabezado con el avatar.
     * Interlineado justo (30, no los 36 de la maqueta): en un renglón no se nota, y deja el encabezado
     * en 56 con el aire de abajo que pide `PlanScreenTest`.
     */
    val titular: TextStyle = TextStyle(
        fontSize = 25.sp,
        lineHeight = 30.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
    ),
    /**
     * **El título de una pantalla con flecha** (vuelta 29, «Personas» en la maqueta): más chico que el
     * de una pestaña. El de 28 solo va en las cinco raíces.
     */
    val titularDePantalla: TextStyle = TextStyle(
        fontSize = 19.5.sp,
        lineHeight = 24.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp,
    ),
    /**
     * **El saludo** (vuelta 29): «Hola, Juan» arriba de Hoy y el nombre en la tarjeta del perfil, con
     * la fecha o el correo debajo en `apoyo`. Es un nombre, no el título de una pestaña.
     */
    val saludo: TextStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 19.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.Bold,
    ),
    /** El título de una sección, de una tarjeta o de un bloque: «Últimos movimientos». */
    val titulo: TextStyle = TextStyle(
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.Bold,
    ),
    /** El texto de una fila —su título—, un filtro sin elegir, un enlace. */
    val cuerpo: TextStyle = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.SemiBold,
    ),
    /**
     * Un monto en una fila. Tabular, para que la columna no baile, y en la letra de todo lo demás.
     */
    val monto: TextStyle = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        // Sin recorte: un renglón de monto mide su `lineHeight` con cualquier fuente —lo que miden
        // los esqueletos y el alto del MoneyField—.
        lineHeightStyle = RENGLON_ENTERO,
    ),
    /** El subtítulo de una fila, que dice algo que no está en ninguna otra parte. */
    val apoyo: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.Normal,
    ),
    /** Un rótulo chico y espaciado. La base de las pestañas. */
    val rotulo: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = RENGLON_ENTERO,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
    ),
) {
    // ── Los pesos con nombre (vuelta de diseño 21) ───────────────────────────────────────────────
    //
    // Una pantalla no escribe `fontWeight =`: elige uno de estos. Son los mismos estilos de arriba
    // con otro de los pesos de Figtree (400 a 800), y salen de su base en el cuerpo de la clase, no
    // en el constructor: así `conFamilia` les pone la familia sin nombrarlos (el `copy` de la clase
    // los vuelve a calcular) y nunca se separan de su base en tamaño o interlineado.

    /**
     * **`apoyo` con peso: el mini-título.** El rótulo de un dato arriba de su valor («Desde»,
     * «Cuota»), el texto de una píldora, el rótulo de una acción rápida (12,5/600 en la maqueta).
     */
    val apoyoMedio: TextStyle = apoyo.copy(fontWeight = FontWeight.SemiBold)

    /**
     * **`titulo` en SemiBold: un nombre que encabeza sin gritar** (vuelta de diseño 4): el de una
     * cuenta, una categoría o un tercero arriba de su detalle, el título de una hoja. `titulo` a secas
     * (Bold) queda para el título de una sección o de un bloque.
     */
    val tituloMedio: TextStyle = titulo.copy(fontWeight = FontWeight.SemiBold)

    /**
     * **`titulo` en Normal: lo que acompaña a un título en su mismo renglón** —«· 3 movimientos»
     * junto a la fecha de un día plegado, el conteo de una `Seccion`—.
     */
    val tituloRegular: TextStyle = titulo.copy(fontWeight = FontWeight.Normal)

    /**
     * **`cuerpo` en Bold: lo elegido y lo que se toca.** El texto de un botón (15/700 en la maqueta),
     * el filtro elegido, las iniciales de un avatar: `if (elegido) cuerpoFuerte else cuerpo`.
     */
    val cuerpoFuerte: TextStyle = cuerpo.copy(fontWeight = FontWeight.Bold)

    /**
     * **`cuerpo` en Normal: un párrafo.** Una explicación de varios renglones, la respuesta de Movi
     * AI: `cuerpo` (SemiBold) es para el texto de una fila, y a lo largo de un párrafo pesa.
     */
    val cuerpoRegular: TextStyle = cuerpo.copy(fontWeight = FontWeight.Normal)

    /** **El rótulo de una pestaña sin elegir**: 12/500, sin espaciado. */
    val pestana: TextStyle = rotulo.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Medium)

    /** [pestana] activa: 12/700. */
    val pestanaActiva: TextStyle = pestana.copy(fontWeight = FontWeight.Bold)

    /**
     * **El rótulo de una acción de la cuadrícula del Inicio** (vuelta 39): 12/600 de la maqueta ÷ 1,12
     * (la letra que la app le pone a todo), sin espaciado, en dos renglones de alto fijo («Anotar /
     * gasto», «Subir / comprobante»). Es el piso de lo legible en el Inicio: 12 px efectivos en la web.
     * A 360 dp ninguna palabra se parte (`LasOchoAccionesDelInicioTest`).
     */
    val rotuloDeAccion: TextStyle = rotulo.copy(fontSize = 10.75.sp, lineHeight = 14.sp, letterSpacing = 0.sp)

    /**
     * **El énfasis dentro de una frase**: el nombre o la cifra que se resalta en medio de un texto
     * corrido (el «**así**» de Movi AI, lo que un aviso del banco dice que es el monto). Un
     * `SpanStyle`, para un `AnnotatedString`: `withStyle(Movi.textos.enfasis) { append(nombre) }`.
     * Bold, como el «<strong>» de la maqueta.
     */
    val enfasis: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)

    /**
     * **El menos pegado al «$»** (vuelta 29). El `−` (U+2212) de Figtree es del ancho de un dígito
     * (632 de 1000) con el trazo centrado, así que entre él y el «$» quedaba un hueco y «−$85.000» se
     * leía «- $85.000», un guion suelto. Este `SpanStyle` va solo sobre el menos y le quita el aire de
     * su derecha (123 de 1000 ≈ 0,12 em). Lo pone `Cifra` (`conElMenosPegado`).
     */
    val menosPegado: SpanStyle = SpanStyle(letterSpacing = (-0.12).em)
}

// ─────────────────────────────────────────────────────────────────────────────
// CÓMO SE LLEGA A ELLOS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * `staticCompositionLocalOf` y no `compositionLocalOf`: estos valores no cambian mientras la app
 * corre (desde la vuelta 29 hay un solo tema). La variante estática no instala lectores, así que
 * leerla en una fila de una lista de trescientos movimientos no cuesta nada.
 *
 * El default es el único tema que hay: si alguien lee esto fuera de [MoviTheme] —una vista previa,
 * una prueba— ve lo mismo que ve la app.
 */
val LocalColores: ProvidableCompositionLocal<ColoresDeMovi> =
    staticCompositionLocalOf { COLORES_CLAROS }

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
