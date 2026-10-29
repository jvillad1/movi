package com.jvillada.movi.ui.ai

import com.jvillada.movi.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vuelta 61: los nombres de pantalla de una respuesta de Movi AI, como enlaces. Conservador. */
class PantallasNombradasTest {

    private fun nombres(texto: String): List<Pair<String, Screen>> =
        enlacesAPantallas(texto).map { texto.substring(it.desde, it.hasta) to it.pantalla }

    @Test
    fun `las respuestas sin IA llevan a sus pantallas`() {
        assertEquals(listOf("Pagos" to Screen.Plan()), nombres("Si ya lo pagaste, anótalo y Movi lo tilda solo en Pagos."))
        assertEquals(listOf("Pagos" to Screen.Plan()), nombres("Míralo en la tarjeta Disponible de Pagos."))
        assertEquals(
            listOf("Para revisar" to Screen.Dashboard, "Inicio" to Screen.Dashboard),
            nombres("Y 2 cosas más en «Para revisar», en Inicio."),
        )
        assertEquals(listOf("Presupuestos" to Screen.Budgets), nombres("Puedes ajustar el límite en Presupuestos."))
        assertEquals(listOf("Por revisar" to Screen.PorRevisar), nombres("Confírmalos: ábrelos en Por revisar."))
        assertEquals(listOf("Actividad" to Screen.Transactions()), nombres("Lo ves desde Actividad."))
    }

    @Test
    fun `sin preposicion, en minuscula o pegado a otra palabra no es una pantalla`() {
        listOf(
            "Pagos del período: 3 pendientes.",
            "Tienes 3 pagos en el período.",
            "Ve a pagos y revisa.",
            "Lo anotaste en Pagosx.",
            "el Inicio del período es el 25",
            "Tú decides.",
            "cuenta Pagos",
        ).forEach { assertTrue(nombres(it).isEmpty(), "«$it» no tiene enlaces: ${nombres(it)}") }
    }

    @Test
    fun `una preposicion que es el final de otra palabra no cuenta`() {
        // «quien Pagos»: «en» es el final de «quien», no una preposición.
        assertTrue(nombres("quien Pagos").isEmpty())
        assertEquals(listOf("Tú" to Screen.Tu), nombres("Cámbialo en Tú."))
    }
}
