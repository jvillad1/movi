package com.jvillada.movi.server.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Cuál pregunta merece el modelo caro
 *
 * El dueño pidió que el chat fuera lo más barato posible. El modelo es lo que más pesa en esa
 * cuenta, y la regla que decide cuál usar es esta. Se equivoca barato en los dos sentidos —de más,
 * una respuesta más cara; de menos, una respuesta más simple—, así que lo que estas pruebas fijan
 * no es la perfección sino **los dos extremos**: que lo de todos los días no escale, y que un
 * pedido de criterio no se quede con el chico.
 */
class QueModeloUsarTest {

    @Test
    fun `las preguntas de todos los dias no escalan`() {
        listOf(
            "¿cuánto gasté en Comida en agosto?",
            "¿qué compré en Zelo Group?",
            "¿qué me falta pagar este mes?",
            "¿cuánta plata tengo?",
            "¿cuándo vence la cuota del vehículo?",
        ).forEach {
            assertFalse(laPreguntaPideCriterio(it), "no debería escalar: «$it»")
        }
    }

    @Test
    fun `pedir criterio si escala`() {
        listOf(
            "¿me conviene abonar al vehículo o al Crediágil?",
            "¿qué me recomiendas hacer con la deuda de la Master?",
            "¿debería cambiar de plan de celular?",
            "¿qué opinas de mis gastos este mes?",
            "analiza mis créditos",
            "¿vale la pena refinanciar?",
            "¿me alcanza para pagar el colegio y el arriendo?",
        ).forEach {
            assertTrue(laPreguntaPideCriterio(it), "debería escalar: «$it»")
        }
    }

    /** Una foto de un recibo o de una oferta del banco se manda para que opine sobre ella. */
    @Test
    fun `una imagen siempre escala`() {
        assertTrue(laPreguntaPideCriterio("mira esto", hayImagen = true))
    }

    /** Una pregunta larga en esta app no es «¿cuánto gasté?»: es una situación contada. */
    @Test
    fun `un parrafo escala aunque no diga ninguna palabra clave`() {
        val parrafo = "este mes se me juntaron el colegio y el seguro del carro, " +
            "además me subieron el arriendo y todavía no me pagan el bono que esperaba, " +
            "y encima tengo la tarjeta con el cupo casi lleno desde el viaje"
        assertTrue(parrafo.length > LARGO_QUE_YA_ES_UNA_CONSULTA)
        assertTrue(laPreguntaPideCriterio(parrafo))
    }

    @Test
    fun `no distingue tildes ni mayusculas`() {
        assertTrue(laPreguntaPideCriterio("QUE ME RECOMIENDAS"))
        assertTrue(laPreguntaPideCriterio("¿que deberia hacer?"))
        assertTrue(laPreguntaPideCriterio("¿qué debería hacer?"))
    }

    @Test
    fun `una pregunta vacia no escala`() {
        assertFalse(laPreguntaPideCriterio(""))
    }
}
