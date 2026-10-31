package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 88 · «Otro banco»: todos los bancos, con búsqueda, y los nuevos no le roban nada a nadie
 *
 * En el teléfono del papá del dueño solo se veían dos bancos («faltan más opciones de bancos»). La lista
 * de «Otro banco» sale del catálogo ([bancosParaElegir]) y la vuelta sumó los bancos comunes de Colombia
 * que faltaban, con patrones conservadores.
 */
class BancosParaElegirTest {

    private fun ids(consulta: String, paraTarjetas: Boolean = false) = bancosParaElegir(consulta, paraTarjetas).map { it.id }

    @Test
    fun `sin consulta van todos los bancos que pueden ser una cuenta, en orden alfabetico`() {
        val todos = bancosParaElegir()
        val nombres = todos.map { normalizarParaBuscar(it.nombre) }
        assertEquals(nombres.sorted(), nombres, "en orden alfabético")
        assertEquals(BANCOS_CONOCIDOS.size - 3, todos.size, "todos menos Visa, Mastercard y la AMEX")
        listOf("visa", "mastercard", "amex").forEach { assertFalse(it in todos.map { m -> m.id }, it) }
        // Los fondos sí: el formulario tiene «Inversión».
        listOf("skandia", "porvenir", "proteccion", "colfondos", "fiducuenta").forEach { assertTrue(it in todos.map { m -> m.id }, it) }
    }

    @Test
    fun `para una tarjeta la AMEX si va, las redes no`() {
        val ids = ids("", paraTarjetas = true)
        assertTrue("amex" in ids)
        assertFalse("visa" in ids)
        assertFalse("mastercard" in ids)
    }

    @Test
    fun `la busqueda no mira tildes ni mayusculas`() {
        assertEquals(listOf("banco_de_bogota"), ids("bogota"))
        assertEquals(listOf("banco_de_bogota"), ids("BOGOTÁ"))
        assertEquals(listOf("uala"), ids("uala"))
        assertEquals(listOf("itau"), ids("itau"))
        assertTrue("caja_social" in ids("caja"))
    }

    @Test
    fun `la busqueda tambien mira los patrones`() {
        assertEquals(listOf("scotiabank"), ids("colpatria"))
        assertEquals(listOf("caja_social"), ids("bcsc"))
        assertTrue(ids("zzz").isEmpty())
    }

    @Test
    fun `estan los bancos comunes de Colombia que faltaban`() {
        val todos = bancosParaElegir().map { it.nombre }
        listOf(
            "Banco Caja Social", "Banco Agrario", "Banco Falabella", "Banco Pichincha", "Bancoomeva",
            "Banco Finandina", "Banco Serfinanza", "Banco Coopcentral", "Confiar", "Iris", "Ualá", "Banco W", "Mibanco",
        ).forEach { assertTrue(it in todos, "falta «$it»") }
    }

    @Test
    fun `los nuevos se reconocen en el nombre de una cuenta`() {
        fun cuenta(nombre: String) = marcaDelBanco(nombre)?.id
        assertEquals("caja_social", cuenta("Caja Social Ahorros"))
        assertEquals("banco_agrario", cuenta("Agrario"))
        assertEquals("pichincha", cuenta("Pichincha Ahorros"))
        assertEquals("bancoomeva", cuenta("Coomeva"))
        assertEquals("confiar", cuenta("Confiar Ahorros"))
        assertEquals("iris", cuenta("Iris"))
        assertEquals("uala", cuenta("Ualá"))
        assertEquals("banco_w", cuenta("Banco W"))
        assertEquals("mibanco", cuenta("Mibanco"))
    }

    @Test
    fun `las palabras de uso diario de los nuevos no se leen como banco en un texto`() {
        assertNull(marcaDelTexto("Hay que confiar en el proceso"))
        assertNull(marcaDelTexto("Flores iris para la mamá"))
        assertNull(marcaDelTexto("Cita Coomeva EPS"))
        assertNull(marcaDelTexto("Seguro agrario"))
        assertNull(marcaDelTexto("Pago a W"))
        // Una compra en la Olímpica con la tarjeta de Serfinanza sigue siendo la Olímpica.
        assertEquals("olimpica", marcaDelTexto("Compra en SUPERTIENDAS OLIMPICA con tarjeta Serfinanza")?.id)
    }

    /**
     * **Los avisos y cuentas de las pruebas de antes siguen yendo al mismo banco**: los textos de
     * `MarcasConocidasTest` y los orígenes de los avisos que la app ya ve, con lo que daban antes de la
     * vuelta 88.
     */
    @Test
    fun `lo que se reconocia antes sigue igual`() {
        val textos = mapOf(
            "Compraste \$27.500 en RAPPI*LAS DOCE" to "rappi",
            "Compraste \$85.200 en EXITO WOK con tu T.Cred *1234" to "exito",
            "Pago Rappi Pay" to "rappipay",
            "Banco Falabella" to "banco_falabella",
            "Falabella Santafé" to "falabella",
            "Compra en EXITO con tu tarjeta Bancolombia" to "exito",
            "UNE EPM Telecomunicaciones" to "une",
            "Transferiste \$50.000 desde tu cuenta Bancolombia" to "bancolombia",
            "Davivienda le informa una compra" to "davivienda",
            "Daviplata: recibiste \$20.000" to "daviplata",
            "Nequi: Enviaste \$15.000" to "nequi",
        )
        textos.forEach { (texto, id) -> assertEquals(id, marcaDelTexto(texto)?.id, texto) }
        val origenes = mapOf(
            "Notificación · Nu" to "nu",
            "Correo · Bancolombia" to "bancolombia",
            "Fiducuenta Bancolombia" to "fiducuenta",
            "Bancolombia Visa" to "bancolombia",
            "Falabella" to "banco_falabella",
            "Skandia voluntaria" to "skandia",
            "AFC Davibank 1234" to "davibank",
            "Notificación · Davivienda" to "davivienda",
            "Notificación · Banco de Bogotá" to "banco_de_bogota",
            "Scotiabank Colpatria" to "scotiabank",
            "Banco Popular" to "banco_popular",
            "AV Villas" to "av_villas",
            "Banco de Occidente" to "occidente",
        )
        origenes.forEach { (origen, id) -> assertEquals(id, marcaDelBanco(origen)?.id, origen) }
        assertNull(marcaDelBanco("Efectivo"))
        assertNull(marcaDelBanco("Casa"))
    }

    /** Ningún patrón de un banco nuevo casa dentro de un patrón de una marca que ya estaba. */
    @Test
    fun `ningun patron nuevo le roba el texto a una marca que ya estaba`() {
        val nuevos = setOf(
            "caja_social", "banco_agrario", "pichincha", "bancoomeva", "finandina", "serfinanza",
            "coopcentral", "confiar", "iris", "uala", "banco_w", "mibanco",
        )
        val viejas = MARCAS_CONOCIDAS.filter { it.id !in nuevos }
        for (m in viejas) {
            for (p in m.patrones) {
                assertEquals(m.id, marcaDelTexto(p)?.id ?: marcaDelBanco(p)?.id, "«$p» era de ${m.id}")
            }
            for (p in m.patronesDeCuenta) {
                assertEquals(m.id, marcaDelBanco(p)?.id, "«$p» (de cuenta) era de ${m.id}")
            }
        }
    }
}
