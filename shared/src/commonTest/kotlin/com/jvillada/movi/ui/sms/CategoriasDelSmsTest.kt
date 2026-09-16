package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Las pastillas del detalle de un SMS ofrecen las categorías del dueño.**
 *
 * Antes eran ocho nombres fijos («Restaurantes», «Mercado», «Suscripción», «Hogar») y él no usa
 * ninguno: sus movimientos viven en «Comida», «Fútbol», «Hija». Para poner una de esas tenía que
 * salirse de la pantalla — y por eso tantos terminaban en «Otro».
 */
class CategoriasDelSmsTest {

    private val suyas = mapOf(
        "Fútbol" to setOf(TransactionType.EXPENSE),
        "Hija" to setOf(TransactionType.EXPENSE),
        "Gardenera" to setOf(TransactionType.EXPENSE),
    )

    @Test
    fun `lo que Movi propone va primero, siempre`() {
        val opciones = categoriasParaElegirEnElSms(
            propuesta = "Fútbol", tipo = TransactionType.EXPENSE, usadas = suyas, prefs = emptyMap(),
        )
        assertEquals("Fútbol", opciones.first())
        assertEquals(opciones.distinct(), opciones, "la propuesta no se repite más abajo")
    }

    @Test
    fun `las categorias propias del dueno estan a un toque`() {
        val opciones = categoriasParaElegirEnElSms(
            propuesta = "Otros", tipo = TransactionType.EXPENSE, usadas = suyas, prefs = emptyMap(),
            cuantas = 20,
        )
        assertTrue(suyas.keys.all { it in opciones }, "faltan sus categorías: $opciones")
    }

    @Test
    fun `ya no se ofrecen categorias que no existen en ninguna otra pantalla`() {
        val opciones = categoriasParaElegirEnElSms(
            propuesta = null, tipo = TransactionType.EXPENSE, usadas = suyas, prefs = emptyMap(),
            cuantas = 50,
        )
        listOf("Restaurantes", "Mercado", "Suscripción", "Hogar", "Otro").forEach {
            assertFalse(it in opciones, "«$it» no existe en el catálogo de la app")
        }
    }

    @Test
    fun `un ingreso no ofrece categorias de gasto`() {
        val opciones = categoriasParaElegirEnElSms(
            propuesta = null, tipo = TransactionType.INCOME, usadas = suyas, prefs = emptyMap(),
            cuantas = 50,
        )
        assertFalse("Fútbol" in opciones, "«Fútbol» solo se vio como gasto")
        assertTrue(opciones.isNotEmpty())
    }

    @Test
    fun `sin propuesta y sin historia igual hay de donde elegir`() {
        val opciones = categoriasParaElegirEnElSms(
            propuesta = null, tipo = TransactionType.EXPENSE, usadas = emptyMap(), prefs = emptyMap(),
        )
        assertTrue(opciones.isNotEmpty(), "el catálogo predefinido siempre está")
        assertTrue(opciones.size <= 10)
    }
}
