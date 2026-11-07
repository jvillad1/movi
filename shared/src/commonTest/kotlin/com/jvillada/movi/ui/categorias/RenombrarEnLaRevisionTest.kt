package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Revisión de la vuelta 138: lo que la hoja de renombrar decide sin pintar nada. */
class RenombrarEnLaRevisionTest {

    /** Falla sin el arreglo: «Trasnporte» → «Transporte» guardaba el ícono de respaldo como elegido. */
    @Test
    fun `corregir un tipeo no fija el icono de respaldo`() {
        assertNull(aparienciaQueViajaAlRenombrar(CategoryUsage(name = "Trasnporte", scope = CategoryScope.CUSTOM)))
    }

    @Test
    fun `la apariencia viaja si Movi la reconocio por el nombre`() {
        val vivienda = aparienciaQueViajaAlRenombrar(CategoryUsage(name = "Vivienda", scope = CategoryScope.PREDEFINED))
        assertEquals("casa" to "ambar", vivienda?.let { it.icono to it.color })
        // Una propia que una regla reconoce («uber» → taxi) también: el nombre nuevo puede perder la palabra.
        assertEquals("taxi", aparienciaQueViajaAlRenombrar(CategoryUsage(name = "Uber viajes", scope = CategoryScope.CUSTOM))?.icono)
        // Y una que fue del catálogo, aunque hoy sea propia: viaja lo que se ve (el ícono que guardó el
        // primer renombrado), con el color que le falte.
        val hogar = aparienciaQueViajaAlRenombrar(CategoryUsage(name = "Hogar", nombreDeMovi = "Vivienda", icono = "casa"))
        assertEquals("casa", hogar?.icono)
        // Con ícono y color elegidos, viajan solos con la reescritura.
        assertNull(aparienciaQueViajaAlRenombrar(CategoryUsage(name = "Vivienda", scope = CategoryScope.PREDEFINED, icono = "casa", color = "ambar")))
    }

    @Test
    fun `escribir un nombre retirado choca con el de hoy, salvo para volver`() {
        val alias = mapOf("Vivienda" to "Hogar")
        val hogar = CategoryUsage(name = "Hogar", nombreDeMovi = "Vivienda")
        val carro = CategoryUsage(name = "Carro")
        assertEquals("Hogar", colisionAlRenombrar(carro, "vivienda", listOf(hogar, carro), alias)?.name)
        assertNull(colisionAlRenombrar(hogar, "Vivienda", listOf(hogar, carro), alias), "volver a su nombre de Movi no choca")
        assertNull(colisionAlRenombrar(carro, "vivienda", listOf(hogar, carro)), "sin alias, como antes")
    }

    @Test
    fun `el tipo de una renombrada sin fijar es el de su nombre de Movi`() {
        assertEquals("Gasto · automático", subtituloDelTipo(CategoryUsage(name = "Hogar", nombreDeMovi = "Vivienda")))
        assertEquals("Automático", subtituloDelTipo(CategoryUsage(name = "Hogar")))
    }
}
