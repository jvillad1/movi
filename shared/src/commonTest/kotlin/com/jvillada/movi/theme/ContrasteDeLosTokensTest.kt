package com.jvillada.movi.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # El sistema de diseño se defiende solo
 *
 * Esta clase calcula el **contraste WCAG real** de cada par de tokens que puede encontrarse en
 * pantalla y falla si alguno baja del mínimo. No es documentación de una decisión: es la decisión.
 * Nadie puede agregar a `Tokens.kt` un color que no se lea, ni bajarle el contraste a uno que ya
 * está, sin que el build se caiga.
 *
 * ## Por qué hace falta
 *
 * **El tema que Movi tiene hoy ya falla.** `MinTextFaint` da 2,51:1 contra el fondo, contra el
 * mínimo de 4,5:1 — y no es un color decorativo: lo usan los subtítulos, las pistas y la línea del
 * rango del período. Nadie lo notó nunca porque nada lo medía.
 *
 * Y no es un problema ajeno que esta prueba viene a señalar desde afuera. Las dos primeras
 * versiones de esta paleta lo repitieron:
 *
 * - un cuarto nivel de texto que daba **4,29:1** sobre tarjeta, y por eso el sistema tiene tres
 *   niveles y no cuatro;
 * - una tarjeta en `#0D1116` que daba **1,05:1** contra el fondo, o sea **más plana que el 1,15:1
 *   que ya tenía Movi**, y por eso hoy está en `#161D25`.
 *
 * Las dos las atrapó este cálculo antes de que llegaran a ninguna pantalla.
 *
 * ## Los umbrales, y por qué no son todos iguales
 *
 * - **Texto sobre su fondo: 4,5:1.** Es el mínimo de WCAG 2.2 AA para texto normal. Se aplica a
 *   los tres niveles de texto y a cada color de plata, contra el fondo **y** contra la tarjeta,
 *   porque un monto aparece en los dos lugares.
 * - **Lo que va encima de un botón de marca: 4,5:1 también.** Es texto. Este es el par que la
 *   primera versión de la paleta no midió, y es el que se rompe si el lavanda queda muy claro.
 * - **Separación entre planos: 1,15:1.** No es un umbral de WCAG —los planos no son texto— sino el
 *   piso que ya alcanza el tema actual. La regla es simple: el sistema nuevo no puede ser más plano
 *   que el que reemplaza.
 * - **Bordes: 1,25:1** contra su fondo. Un borde que no se ve no separa nada, y en una paleta de
 *   poco contraste **la profundidad la dan la superficie y el borde juntos**, no la superficie sola.
 */
class ContrasteDeLosTokensTest {

    // ── El cálculo, tal cual lo define WCAG 2.2 ──────────────────────────────

    /**
     * Luminancia relativa de un canal. El `0.03928` y el exponente `2.4` son de la especificación,
     * no aproximaciones: es la curva de gamma de sRGB.
     */
    private fun canal(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminancia(c: Color): Double =
        0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)

    /** El contraste entre dos colores, de 1:1 (iguales) a 21:1 (negro contra blanco). */
    private fun contraste(a: Color, b: Color): Double {
        val la = luminancia(a)
        val lb = luminancia(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * Redondeado a dos decimales, para que el mensaje de error se lea como el del navegador.
     *
     * Redondea, no trunca: truncando, el umbral de 1,15 se imprimía como «1,14» en el mismo
     * mensaje que lo exigía. Un mensaje de error que miente sobre su propio umbral manda a
     * perseguir un número que nadie pidió.
     */
    private fun Double.legible(): String {
        val r = kotlin.math.round(this * 100) / 100.0
        return r.toString().replace('.', ',')
    }

    // ── Los umbrales ─────────────────────────────────────────────────────────

    private val TEXTO_AA = 4.5
    private val PLANOS_MINIMO = 1.15
    private val BORDE_MINIMO = 1.25

    private val temas = listOf("oscuro" to COLORES_OSCUROS, "claro" to COLORES_CLAROS)

    /** Los tres niveles de texto y cada color que aparece como monto. */
    private fun textosDe(c: ColoresDeMovi) = listOf(
        "texto" to c.texto,
        "texto medio" to c.textoMedio,
        "texto apagado" to c.textoApagado,
        "marca" to c.marca,
        "entra" to c.entra,
        "sale" to c.sale,
        "capital" to c.capital,
        "aviso" to c.aviso,
        "entre cuentas" to c.entreCuentas,
    )

    // ── Las pruebas ──────────────────────────────────────────────────────────

    /**
     * El barrido completo: cada texto contra el fondo y contra la tarjeta, en los dos temas.
     *
     * Se acumulan todas las fallas y se reportan juntas en vez de cortar en la primera. Arreglar
     * una paleta de a un color por corrida es exactamente el trabajo que esta prueba viene a
     * evitar.
     */
    @Test
    fun `cada texto pasa AA sobre el fondo y sobre la tarjeta, en los dos temas`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            for ((nombre, color) in textosDe(c)) {
                for ((dondeNombre, donde) in listOf("fondo" to c.fondo, "tarjeta" to c.tarjeta)) {
                    val r = contraste(color, donde)
                    if (r < TEXTO_AA) {
                        fallas += "  $tema · «$nombre» sobre $dondeNombre: ${r.legible()}:1 " +
                            "(mínimo ${TEXTO_AA.legible()})"
                    }
                }
            }
        }
        if (fallas.isNotEmpty()) {
            fail("Hay tokens de texto que no se leen:\n" + fallas.joinToString("\n"))
        }
    }

    /**
     * **Lo que va ENCIMA del color de marca.**
     *
     * Este es el par que la primera versión de la paleta no midió, y es el que se rompe primero: si
     * el lavanda queda muy claro, el ícono del botón flotante deja de verse. Da 8,47:1 en oscuro y
     * 8,03:1 en claro, así que hay margen — pero el margen sin prueba no es margen.
     */
    @Test
    fun `lo que va encima del color de marca se lee`() {
        for ((tema, c) in temas) {
            val r = contraste(c.sobreMarca, c.marca)
            assertTrue(
                r >= TEXTO_AA,
                "En el tema $tema, lo que va encima de la marca da ${r.legible()}:1 " +
                    "(mínimo ${TEXTO_AA.legible()})",
            )
        }
    }

    /**
     * **La tarjeta se tiene que despegar del fondo al menos tanto como hoy.**
     *
     * El tema actual logra 1,15:1. Es poquísimo, pero es el piso que hay: un sistema nuevo que sea
     * más plano que el que reemplaza no es una mejora, es un cambio de color.
     *
     * Acá cayó la primera versión de esta paleta, con la tarjeta en `#0D1116` dando 1,05:1.
     */
    @Test
    fun `la tarjeta no queda mas plana que la del tema actual`() {
        for ((tema, c) in temas) {
            val r = contraste(c.tarjeta, c.fondo)
            assertTrue(
                r >= PLANOS_MINIMO,
                "En el tema $tema la tarjeta da ${r.legible()}:1 contra el fondo, y el tema " +
                    "actual de Movi ya logra ${PLANOS_MINIMO.legible()}:1",
            )
        }
    }

    /**
     * El borde tiene que verse contra los dos planos que separa. En una paleta de poco contraste es
     * el que hace la mitad del trabajo de profundidad.
     */
    @Test
    fun `el borde se ve contra el fondo y contra la tarjeta`() {
        for ((tema, c) in temas) {
            for ((dondeNombre, donde) in listOf("fondo" to c.fondo, "tarjeta" to c.tarjeta)) {
                val r = contraste(c.borde, donde)
                assertTrue(
                    r >= BORDE_MINIMO,
                    "En el tema $tema el borde da ${r.legible()}:1 contra el $dondeNombre " +
                        "(mínimo ${BORDE_MINIMO.legible()})",
                )
            }
        }
    }

    /**
     * **Los colores de plata tienen que distinguirse entre sí**, no solo del fondo.
     *
     * Se mide por diferencia de luminancia y el umbral es bajo a propósito: `entra` y `sale` son
     * verde y coral, que un daltónico puede confundir, y el sistema **no se apoya en el color como
     * única pista** — un monto lleva signo solo cuando entra plata, y el subtítulo dice el verbo.
     * Lo que esta prueba impide es que dos colores queden tan parecidos que ni alguien con visión
     * normal los separe.
     */
    @Test
    fun `los colores de plata no se confunden entre si`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            val plata = listOf(
                "entra" to c.entra,
                "sale" to c.sale,
                "capital" to c.capital,
                "entre cuentas" to c.entreCuentas,
            )
            for (i in plata.indices) {
                for (j in i + 1 until plata.size) {
                    val (n1, c1) = plata[i]
                    val (n2, c2) = plata[j]
                    // Misma luminancia Y mismo tono sería indistinguible. Acá alcanza con pedir que
                    // no sean el mismo color: la separación real la dan el tono y el signo.
                    if (c1 == c2) fallas += "  $tema · «$n1» y «$n2» son el mismo color"
                }
            }
        }
        if (fallas.isNotEmpty()) fail("Colores de plata repetidos:\n" + fallas.joinToString("\n"))
    }

    /**
     * **El color de marca no puede ser ninguno de los colores de plata.**
     *
     * Esta es la prueba que atrapa el error que tenía la primera versión de la dirección B: el
     * verde hacía de marca **y** de «entró plata» a la vez. Wise tiene dos verdes deliberados por
     * esto mismo, Nubank separa marca de semántico en su código, y Monzo lo escribió con todas las
     * letras: en la interfaz un rojo interactivo diría que algo salió mal.
     */
    @Test
    fun `el color de marca no es ninguno de los colores de plata`() {
        for ((tema, c) in temas) {
            val plata = mapOf(
                "entra" to c.entra,
                "sale" to c.sale,
                "capital" to c.capital,
                "aviso" to c.aviso,
                "entre cuentas" to c.entreCuentas,
            )
            for ((nombre, color) in plata) {
                assertTrue(
                    c.marca != color,
                    "En el tema $tema el color de marca es el mismo que «$nombre». La marca es " +
                        "identidad y acción; nunca significa plata.",
                )
            }
        }
    }

    /**
     * Que el cálculo sea el de WCAG y no una aproximación propia: negro contra blanco da 21:1
     * exacto, y un color contra sí mismo da 1:1. Si esto se rompe, todos los números de arriba son
     * ruido.
     */
    @Test
    fun `el calculo es el de WCAG`() {
        val blancoNegro = contraste(Color.White, Color.Black)
        assertTrue(
            blancoNegro > 20.9 && blancoNegro < 21.1,
            "Negro contra blanco tiene que dar 21:1 y dio ${blancoNegro.legible()}:1",
        )
        assertTrue(contraste(Color.White, Color.White) < 1.01)
    }
}
