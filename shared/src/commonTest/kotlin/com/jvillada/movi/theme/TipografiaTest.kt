package com.jvillada.movi.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * La regla del camino 1 (ola R1), que es fácil de romper sin darse cuenta: **la monoespaciada va
 * en las cifras de plata —la protagonista, la de tarjeta y el monto de una fila— y en nada más.** Si mañana alguien agrega un octavo estilo y se olvida de ponerle
 * familia en `conFamilias`, sale en la letra del sistema; si se la pone mal, una fila entera se ve
 * terminal otra vez. Las dos cosas las atrapa esto.
 *
 * Se prueba con `FontFamily.Serif` y `FontFamily.Cursive` como sustitutos porque `Font(recurso)` es
 * `@Composable` y no corre en una prueba pura. Lo que importa acá es a qué estilo va cada una.
 */
class TipografiaTest {

    private val interfaz = FontFamily.Serif
    private val cifras = FontFamily.Cursive
    private val textos = TextosDeMovi().conFamilias(interfaz, cifras)

    @Test
    fun `la cifra protagonista va en la de cifras`() {
        assertEquals(cifras, textos.cifra.fontFamily)
    }

    /** Ola R0: la cifra de una tarjeta de cuenta es la otra que va en Martian Mono. */
    @Test
    fun `la cifra de tarjeta tambien va en la de cifras`() {
        assertEquals(cifras, textos.cifraDeTarjeta.fontFamily)
    }

    /** Ola R1: el monto de una fila también, para que toda cifra de la app sea de la misma familia. */
    @Test
    fun `el monto de una fila tambien va en la de cifras`() {
        assertEquals(cifras, textos.monto.fontFamily)
    }

    @Test
    fun `todo lo demas va en la de la interfaz`() {
        mapOf(
            "portada" to textos.portada,
            "titular" to textos.titular,
            "titulo" to textos.titulo,
            "cuerpo" to textos.cuerpo,
            "apoyo" to textos.apoyo,
            "rotulo" to textos.rotulo,
        ).forEach { (nombre, estilo) ->
            assertEquals(interfaz, estilo.fontFamily, "«$nombre» no va en la familia de la interfaz")
        }
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
     * base en el cuerpo de la clase. Si alguien los pasara al constructor, `conFamilias` no les
     * pondría la familia —saldrían en la letra del sistema— y podrían separarse de su base en tamaño.
     * Cada uno es su base con otro peso, y solo de los que la escala ya trae.
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
        val pesosDeLaEscala = setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
        derivados.forEach { (nombre, par) ->
            val (estilo, base) = par
            assertEquals(interfaz, estilo.fontFamily, "«$nombre» no va en la familia de la interfaz")
            assertEquals(base.fontSize, estilo.fontSize, "«$nombre» cambió de tamaño")
            assertEquals(base.lineHeight, estilo.lineHeight, "«$nombre» cambió de interlineado")
            assertTrue(estilo.fontWeight in pesosDeLaEscala, "«$nombre» usa un peso que la escala no trae")
            assertNotEquals(base.fontWeight, estilo.fontWeight, "«$nombre» es igual a su base: sobra")
        }
        assertEquals(FontWeight.SemiBold, textos.enfasis.fontWeight)
    }

    @Test
    fun `los quince estilos de Material llevan la familia de la interfaz`() {
        val t = tipografiaDeMaterial(interfaz)
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEach { assertEquals(interfaz, it.fontFamily) }
    }
}
