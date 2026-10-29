package com.jvillada.movi.server.ai

import com.jvillada.movi.shared.model.CambioDeCategoriaDeUnRecurrente
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Movi AI no cuenta el arriendo como «Otros +$1,8M» (vuelta 60)
 *
 * El arriendo quedó en Otros y el período pasado fue Hogar. Mientras el dueño no lo resuelve, «¿En qué
 * se me está yendo más la plata?» lo compara donde estaba —las cifras de cada categoría no cambian— y
 * dice por qué.
 */
class EnQueSeVaNoCuentaElCambioComoNuevoTest {

    private val cambio = CambioDeCategoriaDeUnRecurrente(
        eventoId = "oct", nombre = "Arriendo", monto = 1_800_000, categoria = "Otros", categoriaAnterior = "Hogar",
        periodoAnterior = "2026-09", eventoAnteriorId = "sep",
    )

    private fun datos(cambios: List<CambioDeCategoriaDeUnRecurrente>) = DatosDeLasSugeridas(
        rango = "25 de septiembre al 24 de octubre",
        entro = 5_000_000,
        gastoPorCategoria = mapOf("Otros" to 1_850_000L, "Mercado" to 400_000L),
        anterior = mapOf("Hogar" to 1_800_000L, "Otros" to 50_000L, "Mercado" to 380_000L),
        diasComparados = 20,
        cambiosDeCategoria = cambios,
    )

    @Test
    fun `sin el cambio Otros sube, con el cambio se compara como Hogar y lo dice`() {
        val antes = enQueSeVa(datos(emptyList()))
        assertTrue(antes.contains("Otros \$1.850.000 · \$1.800.000 más que el período pasado"), antes)

        val ahora = enQueSeVa(datos(listOf(cambio)))
        assertTrue(ahora.contains("Otros \$1.850.000 · casi igual que el período pasado"), ahora)
        assertFalse(ahora.contains("\$1.800.000 más que el período pasado"), ahora)
        assertTrue(ahora.contains("Arriendo (\$1.800.000) quedó en Otros y el período pasado fue Hogar"), ahora)
    }
}
