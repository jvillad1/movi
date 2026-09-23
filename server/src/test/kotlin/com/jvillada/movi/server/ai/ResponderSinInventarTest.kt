package com.jvillada.movi.server.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # El único reintento, con un modelo de mentira
 *
 * Lo que se fija acá es el contrato de costo que pidió el dueño —«lo más económico posible»—:
 *
 * - el caso normal hace **UNA** llamada, ni una más;
 * - el reintento ocurre **solo** cuando hay cifras sin respaldo, y es uno;
 * - la segunda respuesta **reemplaza** a la primera;
 * - si la segunda sigue sin respaldo, se entrega con la línea honesta y queda contado.
 */
class ResponderSinInventarTest {

    /** Un modelo de guion que además cuenta las correcciones que recibió. */
    private class ModeloQueSeCorrige(private val guion: List<RespuestaDelModelo>) : ElModeloQueSeCorrige {
        var llamadas = 0
        val permisos = mutableListOf<Boolean>()
        val correcciones = mutableListOf<String>()

        override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo {
            permisos += puedeUsarHerramientas
            return guion[llamadas++.coerceAtMost(guion.lastIndex)]
        }

        override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit

        override fun anotarCorreccion(correccion: String) {
            correcciones += correccion
        }
    }

    private val datos = listOf("- Comida: límite \$1500000, gastado \$1161535 — le quedan \$338465")

    private fun texto(t: String) = RespuestaDelModelo.Texto(t)

    @Test
    fun `el caso normal hace UNA sola llamada`() = runBlocking {
        val modelo = ModeloQueSeCorrige(listOf(texto("Llevas \$1.161.535 en Comida; te quedan \$338.465.")))

        val r = responderSinInventar(modelo, ejecutar = { "" }, fuentes = datos)

        assertEquals(1, modelo.llamadas, "sin cifras sin respaldo no hay reintento")
        assertTrue(modelo.correcciones.isEmpty())
        assertEquals("Llevas \$1.161.535 en Comida; te quedan \$338.465.", r.texto)
        assertFalse(r.huboReintento)
        assertTrue(r.sinRespaldo.isEmpty())
    }

    @Test
    fun `una respuesta sin cifras tampoco reintenta`() = runBlocking {
        val modelo = ModeloQueSeCorrige(listOf(texto("No encuentro movimientos en Rappi este año.")))
        responderSinInventar(modelo, ejecutar = { "" }, fuentes = datos)
        assertEquals(1, modelo.llamadas)
    }

    @Test
    fun `con una cifra sin respaldo reintenta una vez y la segunda reemplaza a la primera`() = runBlocking {
        val modelo = ModeloQueSeCorrige(
            listOf(
                texto("Te quedan \$400.000 en Comida."),
                texto("Te quedan \$338.465 en Comida."),
            ),
        )

        val r = responderSinInventar(modelo, ejecutar = { "" }, fuentes = datos)

        assertEquals(2, modelo.llamadas, "un reintento, y uno solo")
        assertEquals(listOf(true, false), modelo.permisos, "el reintento no puede consultar: es una llamada, no otra conversación")
        assertTrue("\$400.000" in modelo.correcciones.single(), modelo.correcciones.single())
        assertEquals("Te quedan \$338.465 en Comida.", r.texto, "la segunda reemplaza a la primera, sin línea honesta")
        assertEquals(listOf("\$400.000"), r.corregidas)
        assertTrue(r.sinRespaldo.isEmpty())
    }

    @Test
    fun `si el reintento sigue sin respaldo se entrega con la linea honesta`() = runBlocking {
        val modelo = ModeloQueSeCorrige(
            listOf(
                texto("Te quedan \$400.000 en Comida."),
                texto("Te quedan unos \$390.000 en Comida."),
                texto("no debería pedirse una tercera"),
            ),
        )

        val r = responderSinInventar(modelo, ejecutar = { "" }, fuentes = datos)

        assertEquals(2, modelo.llamadas, "nunca más de un reintento")
        assertEquals("Te quedan unos \$390.000 en Comida.\n\n$NO_PUDE_VERIFICAR \$390.000.", r.texto)
        assertEquals(listOf("\$390.000"), r.sinRespaldo)
        assertEquals(listOf("\$400.000"), r.corregidas)
    }

    @Test
    fun `si el reintento vuelve vacio se entrega la primera con su advertencia`() = runBlocking {
        val modelo = ModeloQueSeCorrige(listOf(texto("Te quedan \$400.000."), texto(NO_ALCANCE_A_TERMINAR)))

        val r = responderSinInventar(modelo, ejecutar = { "" }, fuentes = datos)

        assertEquals("Te quedan \$400.000.\n\n$NO_PUDE_VERIFICAR \$400.000.", r.texto)
    }

    /** Lo que devolvió una herramienta también es un dato: citarlo no es inventar. */
    @Test
    fun `lo que devolvio una herramienta respalda la respuesta`() = runBlocking {
        val modelo = ModeloQueSeCorrige(
            listOf(
                RespuestaDelModelo.PideHerramientas(listOf(LlamadaDeHerramienta("t1", TOTALES_POR_CATEGORIA, emptyMap()))),
                texto("En agosto de calendario gastaste \$1.200.000 en Comida."),
            ),
        )

        val r = responderSinInventar(modelo, ejecutar = { "- Comida: 1200000" }, fuentes = datos)

        assertEquals(2, modelo.llamadas, "una consulta y la respuesta: ningún reintento")
        assertFalse(r.huboReintento)
    }

    /** Con una foto, los montos salen de la imagen: el verificador no la puede leer y no se mete. */
    @Test
    fun `con una foto no se verifica`() = runBlocking {
        val modelo = ModeloQueSeCorrige(listOf(texto("El recibo es por \$87.300.")))

        val r = responderSinInventar(modelo, ejecutar = { "" }, fuentes = datos, verificar = false)

        assertEquals(1, modelo.llamadas)
        assertEquals("El recibo es por \$87.300.", r.texto)
    }

    /** Una trampa dispara el reintento aunque la cifra sea una resta de dos datos del mismo renglón. */
    @Test
    fun `una trampa dispara el reintento`() = runBlocking {
        val modelo = ModeloQueSeCorrige(
            listOf(texto("La deuda baja \$185.831 al mes."), texto("La deuda crece \$23.388 al mes.")),
        )
        val fuentes = listOf("- Cuota: \$2.613.714\n- Interés de este mes: \$2.427.883\n- CRECE unos \$23.388 al mes")

        val r = responderSinInventar(modelo, ejecutar = { "" }, fuentes = fuentes, trampas = mapOf(185_831L to "cuota − interés"))

        assertEquals(listOf("\$185.831"), r.corregidas)
        assertEquals("La deuda crece \$23.388 al mes.", r.texto)
    }
}
