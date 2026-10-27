package com.jvillada.movi.theme

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

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
