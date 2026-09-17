package com.jvillada.movi.server.ai

import com.jvillada.movi.server.routes.PERSONA
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Las instrucciones tienen que hablar del asistente que existe hoy
 *
 * Esta prueba nace de un defecto real y silencioso. Al sacar los documentos del contexto para que
 * dejaran de viajar en cada mensaje, se les dio una herramienta —y **las instrucciones quedaron
 * describiendo el mundo anterior**: seguían diciendo que los papeles estaban en un «bloque
 * "Documentos guardados"» que ya no existía, y que había «dos herramientas» cuando había tres.
 *
 * El modelo no se queja de eso: contesta igual, buscando en un bloque que no está y sin usar una
 * herramienta que nadie le nombró. O sea, el defecto se ve como «el asistente no supo», que es
 * exactamente lo que el dueño reportó y lo más caro de diagnosticar.
 */
class LasInstruccionesNombranSusHerramientasTest {

    @Test
    fun `cada herramienta que existe esta nombrada en las instrucciones`() {
        LAS_HERRAMIENTAS.forEach { herramienta ->
            assertTrue(
                herramienta.name() in PERSONA,
                "«${herramienta.name()}» existe pero las instrucciones no la nombran: el modelo no la va a usar",
            )
        }
    }

    @Test
    fun `las instrucciones no nombran herramientas que no existen`() {
        val nombres = LAS_HERRAMIENTAS.map { it.name() }
        Regex("""\b(buscar|totales|consultar)_[a-z_]+""").findAll(PERSONA).forEach { encontrada ->
            assertTrue(
                encontrada.value in nombres,
                "las instrucciones nombran «${encontrada.value}», que no existe: el modelo va a pedir algo que no está",
            )
        }
    }

    /**
     * El bloque de documentos se fue del contexto (se paga solo cuando se consulta). Si las
     * instrucciones vuelven a mandarlo a buscar ahí, el asistente busca donde no hay nada.
     */
    @Test
    fun `las instrucciones no mandan a buscar en bloques que ya no viajan`() {
        assertFalse(
            "bloque \"Documentos guardados\"" in PERSONA,
            "ese bloque ya no viaja en el contexto: los papeles se piden con $BUSCAR_DOCUMENTOS",
        )
    }

    /** Lo que sí sigue viajando en cada mensaje, y el modelo tiene que saber que está ahí. */
    @Test
    fun `las instrucciones siguen apuntando al bloque que si viaja`() {
        assertTrue("DATOS DEL USUARIO" in PERSONA)
    }

    /**
     * **Un mes no es su período, y eso hay que decírselo.** Su corte es el 25: «agosto» y «este
     * período» son ventanas distintas que se superponen a medias. El bloque trae las cifras del
     * período rotuladas como tales, así que un modelo que no sepa la diferencia contesta una
     * pregunta por agosto con la cifra del período — un número equivocado con cara de exacto, que
     * es la peor forma de equivocarse en una app de plata.
     */
    @Test
    fun `las instrucciones avisan que un mes de calendario no es el periodo del usuario`() {
        assertTrue("OJO CON LOS MESES" in PERSONA, "sin esto contesta agosto con la cifra del período")
        assertTrue("fechas de calendario" in PERSONA)
    }
}
