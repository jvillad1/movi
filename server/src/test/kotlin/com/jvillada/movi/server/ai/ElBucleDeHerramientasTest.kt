package com.jvillada.movi.server.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El ida y vuelta con el modelo, probado sin una sola llamada de red
 *
 * Esta es la parte de «Movi AI consulta tus datos» donde un error se paga caro y en producción: un
 * bucle que no termina, una herramienta que revienta la conversación, un resultado que se le
 * contesta a la llamada equivocada. Por eso [conversarConHerramientas] no sabe nada del SDK y todo
 * esto se prueba con un modelo de mentira.
 */
class ElBucleDeHerramientasTest {

    /** Un modelo de mentira: contesta el guion que se le dé, y anota cómo lo trataron. */
    private class ModeloDeGuion(private val guion: List<RespuestaDelModelo>) : ElModeloConHerramientas {
        var vueltas = 0
        val herramientasOfrecidas = mutableListOf<Boolean>()
        val resultadosRecibidos = mutableListOf<List<Pair<String, String>>>()

        override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo {
            herramientasOfrecidas += puedeUsarHerramientas
            return guion[vueltas++.coerceAtMost(guion.lastIndex)]
        }

        override fun anotarResultados(resultados: List<Pair<String, String>>) {
            resultadosRecibidos += resultados
        }
    }

    private fun pide(vararg nombres: String) = RespuestaDelModelo.PideHerramientas(
        nombres.mapIndexed { i, n -> LlamadaDeHerramienta(id = "tu_$i$n", nombre = n, argumentos = mapOf("desde" to "2026-08-01")) },
    )

    @Test
    fun `si el modelo contesta de una, eso es la respuesta`() = runBlocking {
        val modelo = ModeloDeGuion(listOf(RespuestaDelModelo.Texto("Gastaste \$1.200.000 en Comida.")))

        val texto = conversarConHerramientas(modelo, ejecutar = { "no debería pedirse" })

        assertEquals("Gastaste \$1.200.000 en Comida.", texto)
        assertEquals(1, modelo.vueltas, "una respuesta directa no gasta más vueltas")
    }

    @Test
    fun `lo que pide se ejecuta y vuelve con el id de SU llamada`() = runBlocking {
        val modelo = ModeloDeGuion(
            listOf(pide(TOTALES_POR_CATEGORIA), RespuestaDelModelo.Texto("En agosto: \$900.000.")),
        )
        val pedidas = mutableListOf<LlamadaDeHerramienta>()

        val texto = conversarConHerramientas(
            modelo,
            ejecutar = { llamada -> pedidas += llamada; "Comida: 900000" },
        )

        assertEquals("En agosto: \$900.000.", texto)
        assertEquals(listOf("2026-08-01"), pedidas.map { it.argumentos["desde"] })
        assertEquals(
            listOf(listOf("tu_0$TOTALES_POR_CATEGORIA" to "Comida: 900000")),
            modelo.resultadosRecibidos,
            "el resultado tiene que volver atado al id de su propia llamada",
        )
    }

    /** Dos consultas en la misma vuelta son UNA vuelta: si no, comparar dos meses costaría el doble. */
    @Test
    fun `dos herramientas en la misma vuelta se ejecutan las dos y cuestan una sola vuelta`() = runBlocking {
        val modelo = ModeloDeGuion(
            listOf(
                pide(TOTALES_POR_CATEGORIA, BUSCAR_MOVIMIENTOS),
                RespuestaDelModelo.Texto("Listo."),
            ),
        )
        var cuantas = 0

        conversarConHerramientas(modelo, ejecutar = { cuantas++; "ok" })

        assertEquals(2, cuantas)
        assertEquals(2, modelo.vueltas)
        assertEquals(1, modelo.resultadosRecibidos.size, "las dos se contestan juntas")
    }

    /**
     * **Así es como el bucle garantiza que termina**: en la última vuelta el modelo no tiene
     * herramientas, o sea que no puede volver a pedir y tiene que hablar con lo que ya tiene.
     */
    @Test
    fun `en la ultima vuelta ya no se le ofrecen herramientas`() = runBlocking {
        val modelo = ModeloDeGuion(listOf(pide(BUSCAR_MOVIMIENTOS)))

        conversarConHerramientas(modelo, ejecutar = { "ok" }, vueltasMaximas = 3)

        assertEquals(listOf(true, true, false), modelo.herramientasOfrecidas)
    }

    @Test
    fun `un modelo que solo pide y nunca contesta termina igual, y lo dice`() = runBlocking {
        val modelo = ModeloDeGuion(listOf(pide(BUSCAR_MOVIMIENTOS)))

        val texto = conversarConHerramientas(modelo, ejecutar = { "ok" }, vueltasMaximas = 3)

        assertEquals(SIN_RESPUESTA, texto)
        assertEquals(3, modelo.vueltas, "no se queda dando vueltas para siempre")
    }

    /**
     * Una consulta que falla **no tumba la conversación**: el error viaja como resultado para que
     * el modelo pueda corregir. El dueño prefiere una respuesta incompleta a una pantalla de error.
     */
    @Test
    fun `una herramienta que revienta se le contesta al modelo, no al dueno`() = runBlocking {
        val modelo = ModeloDeGuion(
            listOf(pide(BUSCAR_MOVIMIENTOS), RespuestaDelModelo.Texto("No pude ver eso, pero…")),
        )

        val texto = conversarConHerramientas(modelo, ejecutar = { error("la base se cayó") })

        assertEquals("No pude ver eso, pero…", texto)
        assertTrue(
            modelo.resultadosRecibidos.single().single().second.contains("la base se cayó"),
            "el modelo tiene que enterarse de qué falló: ${modelo.resultadosRecibidos}",
        )
    }
}
