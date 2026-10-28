package com.jvillada.movi.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * La regla de la vuelta 29 (opción 3): **una sola letra, Figtree, para todo —también las cifras—, y
 * las cifras con números tabulares.** Si mañana alguien agrega un estilo y se olvida de ponerle
 * familia en `conFamilia`, sale en la letra del sistema; si una cifra pierde el `tnum`, una columna
 * de montos baila. Las dos cosas las atrapa esto.
 *
 * Se prueba con `FontFamily.Serif` como sustituto porque `Font(recurso)` es `@Composable` y no corre
 * en una prueba pura. Lo que importa acá es que cada estilo la lleve.
 */
class TipografiaTest {

    private val familia = FontFamily.Serif
    private val textos = TextosDeMovi().conFamilia(familia)

    @Test
    fun `todos los estilos van en la misma familia, tambien las cifras`() {
        mapOf(
            "cifra" to textos.cifra,
            "cifraDeTarjeta" to textos.cifraDeTarjeta,
            "monto" to textos.monto,
            "portada" to textos.portada,
            "titular" to textos.titular,
            "titularDePantalla" to textos.titularDePantalla,
            "saludo" to textos.saludo,
            "titulo" to textos.titulo,
            "cuerpo" to textos.cuerpo,
            "apoyo" to textos.apoyo,
            "rotulo" to textos.rotulo,
        ).forEach { (nombre, estilo) ->
            assertEquals(familia, estilo.fontFamily, "«$nombre» no va en la familia de Movi")
        }
    }

    @Test
    fun `las cifras llevan numeros tabulares`() {
        listOf(textos.cifra, textos.cifraDeTarjeta, textos.monto).forEach {
            assertEquals("tnum", it.fontFeatureSettings)
        }
    }

    /**
     * **Lo que se ve mide lo de la maqueta.** `App()` agranda la letra ×1,12, así que cada estilo es la
     * maqueta ÷ 1,12 (ver el KDoc de `TextosDeMovi`). Se mide lo que llega a la pantalla, con medio
     * punto de tolerancia por el redondeo.
     */
    @Test
    fun `los tamanos que se ven son los de la maqueta`() {
        val escalaDeLaApp = 1.12f
        mapOf(
            "cifra" to (textos.cifra to 40f),
            "titular" to (textos.titular to 28f),
            "titularDePantalla" to (textos.titularDePantalla to 22f),
            "saludo" to (textos.saludo to 17f),
            "titulo" to (textos.titulo to 16f),
            "cuerpo" to (textos.cuerpo to 15f),
            "monto" to (textos.monto to 15f),
            "apoyo" to (textos.apoyo to 12.5f),
        ).forEach { (nombre, par) ->
            val (estilo, maqueta) = par
            val seVe = estilo.fontSize.value * escalaDeLaApp
            assertTrue(kotlin.math.abs(seVe - maqueta) <= 0.5f, "«$nombre» se ve a $seVe; la maqueta dice $maqueta")
        }
        assertEquals(FontWeight.ExtraBold, textos.cifra.fontWeight)
        assertEquals(FontWeight.ExtraBold, textos.titular.fontWeight)
        assertEquals(FontWeight.Bold, textos.saludo.fontWeight)
        assertEquals(FontWeight.Bold, textos.titulo.fontWeight)
        assertEquals(FontWeight.SemiBold, textos.cuerpo.fontWeight)
        assertEquals(FontWeight.Bold, textos.monto.fontWeight)
        assertEquals(FontWeight.Bold, textos.cuerpoFuerte.fontWeight)
        assertEquals(FontWeight.Medium, textos.pestana.fontWeight)
        assertEquals(FontWeight.Bold, textos.pestanaActiva.fontWeight)
        assertEquals(12.sp, textos.pestana.fontSize, "la barra de pestañas no se toca en esta vuelta")
    }

    @Test
    fun `poner la familia no le cambia nada mas al estilo`() {
        val base = TextosDeMovi()
        assertEquals(base.cifra.fontSize, textos.cifra.fontSize)
        assertEquals(base.cifra.fontFeatureSettings, textos.cifra.fontFeatureSettings)
        assertEquals(base.monto.fontFeatureSettings, textos.monto.fontFeatureSettings)
        assertEquals(base.rotulo.letterSpacing, textos.rotulo.letterSpacing)
    }

    /**
     * Vuelta de diseño 21: los pesos con nombre (`apoyoMedio`, `cuerpoFuerte`, `pestana`…) salen de su
     * base en el cuerpo de la clase. Si alguien los pasara al constructor, `conFamilia` no les
     * pondría la familia —saldrían en la letra del sistema— y podrían separarse de su base en tamaño.
     * Cada uno es su base con otro peso, y solo de los que Figtree trae (400 a 800).
     */
    @Test
    fun `los pesos con nombre son su base con otro peso, con la familia puesta`() {
        val derivados = mapOf(
            "apoyoMedio" to (textos.apoyoMedio to textos.apoyo),
            "tituloMedio" to (textos.tituloMedio to textos.titulo),
            "tituloRegular" to (textos.tituloRegular to textos.titulo),
            "cuerpoFuerte" to (textos.cuerpoFuerte to textos.cuerpo),
            "cuerpoRegular" to (textos.cuerpoRegular to textos.cuerpo),
            "pestana" to (textos.pestana to textos.rotulo),
            "pestanaActiva" to (textos.pestanaActiva to textos.rotulo),
        )
        val pesosDeFigtree = setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
        derivados.forEach { (nombre, par) ->
            val (estilo, base) = par
            assertEquals(familia, estilo.fontFamily, "«$nombre» no va en la familia de Movi")
            assertEquals(base.fontSize, estilo.fontSize, "«$nombre» cambió de tamaño")
            assertEquals(base.lineHeight, estilo.lineHeight, "«$nombre» cambió de interlineado")
            assertTrue(estilo.fontWeight in pesosDeFigtree, "«$nombre» usa un peso que Figtree no trae")
            assertNotEquals(base.fontWeight, estilo.fontWeight, "«$nombre» es igual a su base: sobra")
        }
        assertEquals(FontWeight.Bold, textos.enfasis.fontWeight)
    }

    @Test
    fun `los quince estilos de Material llevan la familia de Movi`() {
        val t = tipografiaDeMaterial(familia)
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEach { assertEquals(familia, it.fontFamily) }
    }

    /**
     * **Ningún texto se ve por debajo de 12 px** (vuelta 40). `App()` agranda la letra ×1,12 y los
     * estilos son la maqueta ÷ 1,12: el más chico, `apoyo` (11 sp), se ve a 12,3. Si alguien agrega un
     * estilo más chico, Actividad y Pagos (y todo lo demás) quedarían con letra ilegible: lo atrapa esto.
     */
    @Test
    fun `ningun estilo se ve por debajo de 12 px`() {
        val escalaDeLaApp = 1.12f
        mapOf(
            "cifra" to textos.cifra, "cifraDeTarjeta" to textos.cifraDeTarjeta, "portada" to textos.portada,
            "titular" to textos.titular, "titularDePantalla" to textos.titularDePantalla, "saludo" to textos.saludo,
            "titulo" to textos.titulo, "tituloMedio" to textos.tituloMedio, "tituloRegular" to textos.tituloRegular,
            "cuerpo" to textos.cuerpo, "cuerpoFuerte" to textos.cuerpoFuerte, "cuerpoRegular" to textos.cuerpoRegular,
            "monto" to textos.monto, "apoyo" to textos.apoyo, "apoyoMedio" to textos.apoyoMedio,
            "rotulo" to textos.rotulo, "pestana" to textos.pestana, "pestanaActiva" to textos.pestanaActiva,
        ).forEach { (nombre, estilo) ->
            val seVe = estilo.fontSize.value * escalaDeLaApp
            assertTrue(seVe >= LETRA_MINIMA_QUE_SE_VE, "«$nombre» se ve a $seVe px, por debajo de $LETRA_MINIMA_QUE_SE_VE")
        }
    }
}
