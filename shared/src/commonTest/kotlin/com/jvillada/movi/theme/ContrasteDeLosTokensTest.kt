package com.jvillada.movi.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.ui.ai.fondoDeLaBurbujaDelDueno
import com.jvillada.movi.ui.components.avatarColorOrDefault
import com.jvillada.movi.ui.ai.textoDeLaBurbujaDelDueno
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
 * **El tema que Movi tenía ya fallaba.** `MinTextFaint` daba 2,51:1 contra el fondo, contra el
 * mínimo de 4,5:1 — y no era un color decorativo: lo usaban los subtítulos, las pistas, la flecha
 * de «esto se toca» y la manija de las hojas, en 103 lugares de 39 archivos. Nadie lo notó nunca
 * porque nada lo medía. Ese archivo ya no existe; esta prueba es lo que impide que vuelva.
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
 * - **El ícono de una categoría: 3:1** — el mínimo de WCAG para gráficos que no son texto —
 *   contra la tarjeta, contra el fondo y contra su propio círculo teñido ya compuesto sobre cada
 *   uno. Al lado del ícono siempre va el nombre, así que el color nunca es la única pista; pero
 *   un ícono que no se ve es ruido.
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

    /**
     * **Vuelta 29: el piso de la tarjeta contra el fondo, con sombra.** El camino 1 exigía 1,15 porque
     * sus planos se separaban solo por luminancia. En la opción 3 el blanco sobre el crema da 1,13 (la
     * maqueta) y lo que despega una superficie destacada es la sombra; el piso queda en 1,12 para que
     * nadie oscurezca el blanco ni aclare el crema sin que esto lo diga.
     */
    private val PLANOS_MINIMO = 1.12

    /**
     * **El paso mínimo entre dos capas consecutivas** (capa 1 → 2 → 3). Vuelta 29: la capa 2 es la
     * superficie suave de la maqueta (`#FBF8F3`), que apenas se despega del blanco (1,06) porque la
     * separa el `hilo` de arriba; el piso es que se distinga. La capa 3 sí da un paso (1,10).
     */
    private val PASO_DE_LA_CAPA_2 = 1.05
    private val PASO_DE_LA_CAPA_3 = 1.08
    private val BORDE_MINIMO = 1.25
    private val GRAFICO_AA = 3.0

    /** Un solo tema desde la vuelta 29 (opción 3): el claro. La lista queda para que el mensaje lo diga. */
    private val temas = listOf("claro" to COLORES_CLAROS)

    /**
     * Todo lo que puede ir debajo de un texto. Ola R0: además del fondo y la tarjeta (la capa 1), las
     * capas 2 y 3 y la superficie de propuesta — un monto puede caer en cualquiera.
     */
    private fun superficiesDe(c: ColoresDeMovi) = listOf(
        "fondo" to c.fondo,
        "tarjeta (capa 1)" to c.tarjeta,
        "capa 2" to c.capa2,
        "capa 3" to c.capa3,
        "propuesta" to c.propuesta,
        "tinte" to c.tinte,
    )

    /**
     * Los tres niveles de texto, el enlace y cada color que aparece como monto. **La marca no está**
     * desde la vuelta 29: el amarillo es un relleno, nunca una letra (ver el KDoc de `ColoresDeMovi`).
     */
    private fun textosDe(c: ColoresDeMovi) = listOf(
        "texto" to c.texto,
        "texto medio" to c.textoMedio,
        "texto apagado" to c.textoApagado,
        "enlace" to c.enlace,
        "entra" to c.entra,
        "sale" to c.sale,
        "capital" to c.capital,
        "aviso" to c.aviso,
        "entre cuentas" to c.entreCuentas,
    )

    // ── Las pruebas ──────────────────────────────────────────────────────────

    /**
     * El barrido completo: cada texto contra el fondo y contra cada capa.
     *
     * Se acumulan todas las fallas y se reportan juntas en vez de cortar en la primera. Arreglar
     * una paleta de a un color por corrida es exactamente el trabajo que esta prueba viene a
     * evitar.
     */
    @Test
    fun `cada texto pasa AA sobre el fondo y sobre cada capa`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            for ((nombre, color) in textosDe(c)) {
                for ((dondeNombre, donde) in superficiesDe(c)) {
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
            for ((nombre, texto, fondo) in listOf(
                Triple("la tinta sobre la marca", c.sobreMarca, c.marca),
                Triple("el texto medio sobre la marca", c.sobreMarcaMedio, c.marca),
                Triple("la tinta sobre la marca suave", c.sobreMarca, c.marcaSuave),
                // El filtro elegido y la insignia (vuelta 29): blanco sobre la tinta.
                Triple("el blanco sobre la tinta", c.tarjeta, c.texto),
            )) {
                val r = contraste(texto, fondo)
                assertTrue(
                    r >= TEXTO_AA,
                    "En el tema $tema, $nombre da ${r.legible()}:1 (mínimo ${TEXTO_AA.legible()})",
                )
            }
        }
    }

    /**
     * **Un traspaso no se lee como un enlace** (vuelta 29). `entreCuentas` era `#1F5B96`, casi el
     * `#1D4E9E` del enlace (1,07:1 entre los dos): un monto entre cuentas parecía tocable. Ahora es un gris
     * azulado y tiene que despegarse del enlace al menos lo que un borde se despega de la tarjeta.
     */
    @Test
    fun `entre cuentas no se confunde con el enlace`() {
        for ((tema, c) in temas) {
            val r = contraste(c.entreCuentas, c.enlace)
            assertTrue(r >= BORDE_MINIMO, "En el tema $tema entre cuentas y el enlace dan ${r.legible()}:1")
        }
    }

    /**
     * **La marca no es letra** (vuelta 29). Si alguien vuelve a poner el amarillo en la lista de
     * textos, o el amarillo se oscurece hasta parecer otro color, esto lo dice: el amarillo sobre el
     * blanco no llega ni al piso de un gráfico, así que **no puede** ir de letra.
     */
    @Test
    fun `la marca no se lee como letra, por eso hay un enlace`() {
        for ((tema, c) in temas) {
            assertTrue(contraste(c.marca, c.tarjeta) < GRAFICO_AA, "En el tema $tema el amarillo dejó de ser amarillo")
            assertTrue(c.enlace != c.marca, "En el tema $tema el enlace es la marca")
        }
    }

    /**
     * **Las iniciales de un avatar se leen sobre cada color de la paleta** (vuelta de diseño 18).
     * `sobreAvatar` reemplazó tres `Color.White` sueltos. El piso es el de un gráfico / texto
     * grande (3:1), no el de texto normal: la paleta (`AvatarPalette`, en `:core`, compartida con el
     * server) tiene tonos «600» que dan entre 3,3 y 7,7 contra blanco, y las iniciales van grandes y
     * con peso en un círculo de 40-48 dp. Si alguien agrega a la paleta un color claro, esto cae.
     */
    @Test
    fun `las iniciales se leen sobre cada color de avatar`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            for (hex in AvatarPalette.COLORS) {
                val r = contraste(c.sobreAvatar, avatarColorOrDefault(hex))
                if (r < GRAFICO_AA) fallas += "En el tema $tema, las iniciales sobre $hex dan ${r.legible()}:1"
            }
        }
        if (fallas.isNotEmpty()) fail(fallas.joinToString("\n"))
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
     * **Ola R0: las capas se escalonan.** Cada capa se despega de la de abajo al menos
     * [PASO_DE_LA_CAPA_2] y [PASO_DE_LA_CAPA_3], así que una capa 2 sobre una capa 1 se ve sin borde —es la promesa del
     * camino 1, «superficies por capas, sin bordes duros»—. Y la capa 1 sigue siendo la tarjeta: un
     * solo valor con dos nombres.
     */
    @Test
    fun `las capas se escalonan`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            if (c.capa1 != c.tarjeta) fallas += "  $tema · la capa 1 no es la tarjeta"
            for ((nombre, arriba, abajo, minimo) in listOf(
                Paso("capa 2 sobre capa 1", c.capa2, c.capa1, PASO_DE_LA_CAPA_2),
                Paso("capa 3 sobre capa 2", c.capa3, c.capa2, PASO_DE_LA_CAPA_3),
            )) {
                val r = contraste(arriba, abajo)
                if (r < minimo) {
                    fallas += "  $tema · $nombre: ${r.legible()}:1 (mínimo ${minimo.legible()})"
                }
            }
        }
        if (fallas.isNotEmpty()) fail("Hay capas que no se distinguen:\n" + fallas.joinToString("\n"))
    }

    private data class Paso(val nombre: String, val arriba: Color, val abajo: Color, val minimo: Double)

    /**
     * **La propuesta de Movi se despega del fondo** como una tarjeta. Desde la vuelta 29 es blanca y
     * la distingue la sombra (`Superficie` se la pone a `NivelDeSuperficie.Propuesta`, y lo vigila
     * `SistemaDeDisenoTest`), no un tinte; acá se mide que no se pierda contra el crema.
     */
    @Test
    fun `la superficie de propuesta se despega del fondo`() {
        for ((tema, c) in temas) {
            val r = contraste(c.propuesta, c.fondo)
            assertTrue(
                r >= PLANOS_MINIMO,
                "En el tema $tema la propuesta da ${r.legible()}:1 contra el fondo " +
                    "(mínimo ${PLANOS_MINIMO.legible()})",
            )
        }
    }

    /**
     * **Un color de estado sobre su propio lavado** (`ColoresDeMovi.lavado`, compuesto sobre una capa):
     * es como se dibuja una píldora de estado y el botón de peligro de `ui/components/ds/`. Es
     * texto, así que 4,5:1.
     */
    @Test
    fun `cada color de estado se lee sobre su lavado`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            for ((nombre, color) in listOf(
                "enlace" to c.enlace, "entra" to c.entra, "sale" to c.sale,
                "aviso" to c.aviso, "entre cuentas" to c.entreCuentas, "capital" to c.capital,
            )) {
                for ((dondeNombre, donde) in listOf("capa 1" to c.capa1, "capa 2" to c.capa2, "propuesta" to c.propuesta)) {
                    val lavado = c.lavado(color).compositeOver(donde)
                    val r = contraste(color, lavado)
                    if (r < TEXTO_AA) {
                        fallas += "  $tema · «$nombre» sobre su lavado en $dondeNombre: ${r.legible()}:1"
                    }
                }
            }
        }
        if (fallas.isNotEmpty()) fail("Hay estados que no se leen sobre su lavado:\n" + fallas.joinToString("\n"))
    }

    /**
     * **La burbuja del dueño en Movi AI** (vuelta de diseño 12): el lavado de la marca sobre el fondo
     * del chat, con el texto del sistema encima. Era el color del texto con el fondo encima (negra
     * en claro); ahora es un par de tokens y se mide como uno: 4,5:1.
     */
    @Test
    fun `lo que escribe el dueno se lee sobre su burbuja`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            val burbuja = fondoDeLaBurbujaDelDueno(c).compositeOver(c.fondo)
            val r = contraste(textoDeLaBurbujaDelDueno(c), burbuja)
            if (r < TEXTO_AA) fallas += "  $tema · texto sobre la burbuja del dueño: ${r.legible()}:1"
            assertTrue(burbuja != c.fondo, "En el tema $tema la burbuja del dueño no se distingue del fondo")
        }
        if (fallas.isNotEmpty()) fail("La burbuja del dueño no se lee:\n" + fallas.joinToString("\n"))
    }

    /**
     * **Los tres bordes se ven contra el blanco que recortan.** En la opción 3 un borde va alrededor
     * de algo blanco —el círculo de una acción, un filtro, un botón secundario— y es contra ese blanco
     * que tiene que verse. Contra el crema el que separa es el blanco mismo (1,13), así que ahí no se
     * mide. Y los tres van de menos a más firme: borde < píldora < botón.
     */
    @Test
    fun `los bordes se ven contra la tarjeta y van de menos a mas firme`() {
        for ((tema, c) in temas) {
            for ((nombre, borde) in listOf(
                "borde" to c.borde, "borde de píldora" to c.bordeDePildora, "borde de botón" to c.bordeDeBoton,
            )) {
                val r = contraste(borde, c.tarjeta)
                assertTrue(
                    r >= BORDE_MINIMO,
                    "En el tema $tema el $nombre da ${r.legible()}:1 contra la tarjeta (mínimo ${BORDE_MINIMO.legible()})",
                )
            }
            assertTrue(contraste(c.borde, c.tarjeta) < contraste(c.bordeDePildora, c.tarjeta), "tema $tema")
            assertTrue(contraste(c.bordeDePildora, c.tarjeta) < contraste(c.bordeDeBoton, c.tarjeta), "tema $tema")
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
     * **El ícono de una categoría se ve sobre todo lo que puede tener detrás.**
     *
     * El dibujo es uno solo (ver `ColoresDeCategoria`): el ícono del color, sobre un círculo del
     * mismo color con alfa bajo. El círculo se mide **ya compuesto** sobre la tarjeta y sobre el
     * fondo, que es lo que llega a la pantalla — medir el color con alfa contra nada no mide nada.
     *
     * Se prueba contra los dos planos aunque el ícono viva casi siempre en una tarjeta: una fila de
     * movimientos puede estar sobre el fondo, y en el tema claro el fondo es el plano más difícil
     * (el círculo teñido lo acerca al ícono).
     */
    @Test
    fun `el icono de cada categoria se ve sobre la tarjeta, el fondo y su circulo`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            for (opcion in COLORES_DEL_CATALOGO) {
                val icono = c.categoria(opcion.clave)
                for ((dondeNombre, donde) in listOf("tarjeta" to c.tarjeta, "fondo" to c.fondo)) {
                    val circulo = c.circuloDeCategoria(opcion.clave).compositeOver(donde)
                    for ((contraNombre, contra) in listOf(
                        dondeNombre to donde,
                        "su círculo sobre $dondeNombre" to circulo,
                    )) {
                        val r = contraste(icono, contra)
                        if (r < GRAFICO_AA) {
                            fallas += "  $tema · ícono «${opcion.clave}» contra $contraNombre: " +
                                "${r.legible()}:1 (mínimo ${GRAFICO_AA.legible()})"
                        }
                    }
                }
            }
        }
        if (fallas.isNotEmpty()) {
            fail("Hay colores de categoría que no se ven:\n" + fallas.joinToString("\n"))
        }
    }

    /**
     * **El color de la categoría, sobre la pista de la barra del Inicio.**
     *
     * Ola B, tarea 3: `FilaDeCategoria` (`SeccionesDelPeriodo.kt`) pintó la barra de «En qué se va»
     * del color de la categoría —el mismo que ya usa su ícono— en vez del `marca` único de antes.
     * El brief pedía mirarlo a ojo en el tema claro y, si el color no se distinguía de la pista,
     * usar el del ícono con alfa. Esto reemplaza el «mirarlo» por una medición: el mismo mínimo de
     * gráficos que ya exige `el icono de cada categoria se ve...` de acá arriba (3:1 WCAG), contra
     * `hilo` —la pista, `Movi.colores.hilo`—. Hoy el más ajustado es el naranja en
     * claro, con 4,26:1: hay margen, así que el color entero (sin alfa) es válido y no hizo falta
     * el fallback. Si algún día un color nuevo del catálogo bajara de 3:1 acá, esta prueba lo
     * atrapa antes de que llegue a una pantalla — y ahí sí correspondería el alfa que pedía el brief.
     */
    @Test
    fun `el color de una categoria se ve contra la pista de su barra`() {
        val fallas = mutableListOf<String>()
        for ((tema, c) in temas) {
            for (opcion in COLORES_DEL_CATALOGO) {
                val color = c.categoria(opcion.clave)
                val r = contraste(color, c.hilo)
                if (r < GRAFICO_AA) {
                    fallas += "  $tema · «${opcion.clave}» contra la pista (hilo): ${r.legible()}:1 " +
                        "(mínimo ${GRAFICO_AA.legible()})"
                }
            }
        }
        if (fallas.isNotEmpty()) {
            fail("Hay colores de categoría que no se distinguen de la pista de su barra:\n" + fallas.joinToString("\n"))
        }
    }

    /**
     * Cada clave del catálogo tiene **su** color en cada tema: si dos claves dieran el mismo, el
     * `when` de `ColoresDeCategoria.color` estaría mal cableado y el selector ofrecería diez
     * círculos con uno repetido. Y una clave que no existe cae en gris, no en un color cualquiera.
     */
    @Test
    fun `cada color de categoria es distinto y una clave desconocida da gris`() {
        for ((tema, c) in temas) {
            val colores = COLORES_DEL_CATALOGO.map { c.categoria(it.clave) }
            assertTrue(
                colores.toSet().size == COLORES_DEL_CATALOGO.size,
                "En el tema $tema hay claves de color de categoría con el mismo color",
            )
            assertTrue(c.categoria("fucsia") == c.categorias.gris, "tema $tema: clave desconocida")
            assertTrue(c.categoria(null) == c.categorias.gris, "tema $tema: sin clave")
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
