package com.jvillada.movi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.PATH_DE_LA_M_DE_MOVI
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **El ícono de Movi habla los colores de `Tokens.kt`** (vuelta 65: la «m» en tinta sobre el
 * amarillo). Los recursos de `:androidApp` y de la web no pueden leer Kotlin, así que esta prueba
 * los compara con el único tema, como `ElWidgetHablaLosTokensTest` hace con el widget. Todo sale de
 * `scripts/generar-icono.py`: si algo falla acá, se corre el script, no se parchea a mano.
 */
class ElIconoDeMoviTest {

    private fun archivoEn(ruta: String): File =
        listOf("../$ruta", ruta).map(::File).firstOrNull { it.exists() }
            ?: fail("No encuentro $ruta desde ${File(".").absolutePath}")

    private fun archivo(ruta: String): String = archivoEn(ruta).readText()

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private val res = "androidApp/src/main/res"
    private val web = "webApp/src/wasmJsMain/resources"

    private val colores: Map<String, String> by lazy {
        Regex("""<color name="(\w+)">(#[0-9A-Fa-f]{6})</color>""")
            .findAll(archivo("$res/values/colors.xml"))
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun sinComentarios(xml: String) = xml.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `el fondo del icono es la marca y la m va en la tinta`() {
        assertEquals("#FFC21A", hex(COLORES_CLAROS.marca), "la marca de la opción 3")
        assertEquals(hex(COLORES_CLAROS.marca), colores["movi_marca"])
        assertEquals(hex(COLORES_CLAROS.sobreMarca), colores["movi_tinta"])
    }

    @Test
    fun `el icono adaptable usa la marca de fondo, la m adelante y la misma m en monocromo`() {
        for (nombre in listOf("ic_launcher", "ic_launcher_round")) {
            val xml = sinComentarios(archivo("$res/mipmap-anydpi-v26/$nombre.xml"))
            assertTrue("""<background android:drawable="@color/movi_marca" />""" in xml, "$nombre: el fondo no es @color/movi_marca")
            assertTrue("""<foreground android:drawable="@drawable/ic_launcher_foreground" />""" in xml, "$nombre: primer plano")
            assertTrue("""<monochrome android:drawable="@drawable/ic_launcher_foreground" />""" in xml, "$nombre: falta la capa monochrome")
        }
    }

    @Test
    fun `la m del icono es un vector en la tinta, dentro de la zona segura de 66`() {
        val xml = sinComentarios(archivo("$res/drawable/ic_launcher_foreground.xml"))
        assertTrue(xml.contains("<vector") && "android:viewportWidth=\"108\"" in xml, "el primer plano es un vector de 108")
        assertTrue("android:fillColor=\"@color/movi_tinta\"" in xml, "la m va en @color/movi_tinta")
        val path = Regex("""android:pathData="([^"]+)"""").find(xml)?.groupValues?.get(1) ?: fail("sin pathData")
        assertEquals(PATH_DE_LA_M_DE_MOVI, path, "LaMDeMovi.kt y el ícono de Android tienen que ser la misma m")
        val numeros = Regex("""-?\d+(?:\.\d+)?""").findAll(path).map { it.value.toDouble() }.toList()
        assertTrue(numeros.size >= 2 && numeros.size % 2 == 0)
        // El generador escribe solo coordenadas absolutas (M, L, Q, Z): de a pares son puntos.
        val fuera = numeros.chunked(2).filter { (x, y) -> hypot(x - 54, y - 54) > 33.0 }
        assertTrue(fuera.isEmpty(), "puntos fuera del círculo seguro de 66: $fuera")
        assertFalse(File(archivoEn("$res/drawable").parentFile, "drawable/ic_launcher_background.xml").exists(), "quedó el fondo viejo")
    }

    @Test
    fun `el icono chico de los avisos es un vector monocromo blanco`() {
        val xml = sinComentarios(archivo("$res/drawable/ic_stat_movi.xml"))
        assertTrue(xml.trimStart().let { it.startsWith("<?xml") || it.startsWith("<vector") } && "<vector" in xml, "ic_stat_movi es un vector")
        val rellenos = Regex("""android:fillColor="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        assertTrue(rellenos.isNotEmpty(), "sin relleno")
        assertTrue(rellenos.all { it.uppercase() == "#FFFFFFFF" }, "un solo color, blanco opaco: $rellenos")
        assertFalse("strokeColor" in xml || "gradient" in xml || "@color/" in xml, "nada de trazos, degradados ni colores del tema")
    }

    @Test
    fun `todos los avisos usan la m y el acento de la tinta`() {
        val fuentes = archivoEn("androidApp/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()
        val constructores = fuentes.sumOf { Regex("""NotificationCompat\.Builder\(|Notification\.Builder\(""").findAll(it).count() }
        val iconos = fuentes.sumOf { Regex("""setSmallIcon\(R\.drawable\.ic_stat_movi\)""").findAll(it).count() }
        val acentos = fuentes.sumOf { Regex("""setColor\(ContextCompat\.getColor\(context, R\.color\.movi_tinta\)\)""").findAll(it).count() }
        val otrosIconos = fuentes.sumOf { Regex("""setSmallIcon\((?!R\.drawable\.ic_stat_movi\))""").findAll(it).count() }
        assertTrue(constructores > 0, "no encontré ningún constructor de notificaciones")
        assertEquals(constructores, iconos, "cada notificación lleva ic_stat_movi")
        assertEquals(constructores, acentos, "cada notificación lleva el acento de la tinta")
        assertEquals(0, otrosIconos, "ningún otro ícono chico")
    }

    @Test
    fun `la PWA usa el crema y el icono nuevo`() {
        val manifest = archivo("$web/manifest.json")
        val fondo = hex(COLORES_CLAROS.fondo)
        assertTrue(""""background_color": "$fondo"""" in manifest, "background_color del manifest")
        val temaDelHtml = Regex("""<meta name="theme-color" content="(#[0-9A-Fa-f]{6})">""").find(archivo("$web/index.html"))?.groupValues?.get(1)
        assertTrue(""""theme_color": "$temaDelHtml"""" in manifest, "theme_color del manifest = el del index.html ($temaDelHtml)")
        for (ruta in Regex(""""src": "([^"]+)"""").findAll(manifest).map { it.groupValues[1] }) archivoEn("$web/$ruta")

        val marca = COLORES_CLAROS.marca.toArgb() and 0xFFFFFF
        for (lado in listOf(192, 512)) {
            val img = ImageIO.read(archivoEn("$web/icons/movi-$lado.png"))
            assertEquals(lado, img.width)
            // Maskable: a sangre, el amarillo llega a la esquina.
            assertEquals(marca, img.getRGB(0, 0) and 0xFFFFFF, "movi-$lado.png no es amarillo en la esquina")
        }
        val apple = ImageIO.read(archivoEn("$web/icons/apple-touch-icon.png"))
        assertEquals(180, apple.width)
        assertFalse(apple.colorModel.hasAlpha(), "el apple-touch-icon va sin transparencia")
        assertEquals(marca, apple.getRGB(0, 0) and 0xFFFFFF)

        val sw = archivo("$web/push-sw.js")
        val badge = Regex("""badge: '([^']+)'""").find(sw)?.groupValues?.get(1) ?: fail("push-sw.js sin badge")
        val icono = Regex("""icon: '([^']+)'""").find(sw)?.groupValues?.get(1) ?: fail("push-sw.js sin icon")
        archivoEn("$web/$icono")
        val imgBadge = ImageIO.read(archivoEn("$web/$badge"))
        assertTrue(imgBadge.colorModel.hasAlpha(), "el badge es una silueta: necesita alfa")
        val colores = (0 until imgBadge.width step 4).flatMap { x -> (0 until imgBadge.height step 4).map { y -> imgBadge.getRGB(x, y) } }
            .filter { (it ushr 24) > 0 }.map { it and 0xFFFFFF }.toSet()
        assertEquals(setOf(0xFFFFFF), colores, "el badge es monocromo blanco")
    }
}
