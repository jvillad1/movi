package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.Tendencia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **«¿En qué se va?» contra el período pasado**, sin pantalla: qué flecha lleva cada fila y qué dice.
 * La franja de días la prueba `:core` (`ContraElPeriodoPasadoTest`).
 */
class ContraElPeriodoPasadoEnHoyTest {

    @Test
    fun `cada categoria se compara con lo suyo del periodo pasado`() {
        val cats = categoriasDelPeriodo(
            mapOf("Comida" to 300_000, "Mercado" to 100_000),
            gastoDelAnterior = mapOf("Comida" to 180_000, "Mercado" to 150_000),
        )
        assertEquals(Tendencia.SUBE, cats.single { it.nombre == "Comida" }.contraElAnterior?.tendencia)
        assertEquals(120_000L, cats.single { it.nombre == "Comida" }.contraElAnterior?.diferencia)
        assertEquals(Tendencia.BAJA, cats.single { it.nombre == "Mercado" }.contraElAnterior?.tendencia)
    }

    @Test
    fun `una categoria que no estaba el periodo pasado sube por todo lo gastado`() {
        val cats = categoriasDelPeriodo(mapOf("Fútbol" to 90_000), gastoDelAnterior = emptyMap())
        assertEquals(0L, cats.single().anterior)
        assertEquals(Tendencia.SUBE, cats.single().contraElAnterior?.tendencia)
        assertEquals(90_000L, cats.single().contraElAnterior?.diferencia)
    }

    @Test
    fun `sin comparacion ninguna fila lleva flecha`() {
        val cats = categoriasDelPeriodo(mapOf("Comida" to 300_000), gastoDelAnterior = null)
        assertNull(cats.single().contraElAnterior)
    }

    @Test
    fun `el grupo de la cola se compara con lo que esas mismas categorias gastaron`() {
        val gasto = (1..7).associate { "Cat$it" to (it * 100_000).toLong() }
        // Cat1 y Cat2 quedan en la cola («Otras 2 categorías»): $300.000 ahora.
        val anterior = mapOf("Cat1" to 50_000L, "Cat2" to 50_000L, "Cat7" to 700_000L)
        val cola = categoriasDelPeriodo(gasto, cuantas = 5, gastoDelAnterior = anterior).last()
        assertEquals("Otras 2 categorías", cola.nombre)
        assertEquals(100_000L, cola.anterior)
        assertEquals(200_000L, cola.contraElAnterior?.diferencia)
    }

    @Test
    fun `lo que dice la flecha`() {
        val sube = categoriasDelPeriodo(mapOf("Comida" to 300_000), gastoDelAnterior = mapOf("Comida" to 180_000)).single()
        assertEquals("↑ \$120.000", textoDeLaFlecha(sube.contraElAnterior!!))
        assertEquals("\$120.000 más que a esta altura del período pasado", descripcionDeLaFlecha(sube.contraElAnterior!!))
        val igual = categoriasDelPeriodo(mapOf("Comida" to 180_500), gastoDelAnterior = mapOf("Comida" to 180_000)).single()
        assertEquals("= igual", textoDeLaFlecha(igual.contraElAnterior!!))
    }

    @Test
    fun `el pie dice contra cuantos dias`() {
        assertEquals("Las flechas comparan con los primeros 12 días del período pasado.", pieDeLaComparacion(12))
        assertEquals("Las flechas comparan con el primer día del período pasado.", pieDeLaComparacion(1))
        assertNull(pieDeLaComparacion(null))
    }

    @Test
    fun `el Inicio lee la comparacion de la misma respuesta del resumen`() {
        val data = DashboardData().conResumenDelInicio(
            DashboardSummary(gastoDelAnteriorAMismaAltura = mapOf("Comida" to 10L), diasComparados = 4),
        )
        assertEquals(mapOf("Comida" to 10L), data.gastoDelAnterior)
        assertEquals(4, data.diasComparados)
    }
}
