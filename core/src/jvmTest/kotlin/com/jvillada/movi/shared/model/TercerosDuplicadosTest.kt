package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** **«¿Es la misma persona?»** (20-oct-2026). Ver `TercerosDuplicados.kt`. */
class TercerosDuplicadosTest {

    private val caro = DestinoConocido(id = "dest_caro", nombre = "Caro", numero = "31973270756", creadoEn = 100L)
    private val carolina = DestinoConocido(
        id = "dst_9f2", nombre = "Carolina Restrepo Salazar", numero = "", llave = "carolina restrepo salazar", creadoEn = 900L,
    )
    private val daniel = DestinoConocido(id = "dst_daniel", nombre = "Daniel Leonett", numero = "43087514791", creadoEn = 50L)
    private val mariana = DestinoConocido(id = "dst_mariana", nombre = "Mariana", numero = "55500003333", creadoEn = 60L)
    private val ana = DestinoConocido(id = "dst_ana", nombre = "Ana", numero = "55500004444", creadoEn = 70L)

    @Test
    fun `Caro y Carolina Restrepo Salazar se proponen, y queda la mas antigua`() {
        val par = posiblesDuplicados(listOf(carolina, daniel, caro)).single()
        assertEquals("dest_caro", par.queda.id, "la más antigua queda: es la que tienen los créditos y recurrentes")
        assertEquals("dst_9f2", par.seVa.id)
        assertEquals(MotivoDelParecido.NOMBRE_PARECIDO, par.motivo)
        assertEquals("¿Es la misma persona?", preguntaDelDuplicado(par))
    }

    @Test
    fun `nombres distintos no se proponen`() {
        assertTrue(posiblesDuplicados(listOf(caro, daniel, mariana, ana)).isEmpty(), "«Ana» no es el comienzo de «Mariana»")
    }

    @Test
    fun `lo que el dueño dijo que no son la misma no vuelve`() {
        val descartados = setOf(claveDeNoSonLaMisma("dst_9f2", "dest_caro"))
        assertTrue(posiblesDuplicados(listOf(caro, carolina), descartados).isEmpty())
        assertEquals(claveDeNoSonLaMisma("a", "b"), claveDeNoSonLaMisma("b", "a"), "el par es el mismo en cualquier orden")
    }

    @Test
    fun `compartir un identificador o el mismo nombre tambien se propone`() {
        val otraCaro = DestinoConocido(id = "dst_x", nombre = "Esposa", numero = "99990756", creadoEn = 5L)
        val porNumero = posiblesDuplicados(listOf(caro, otraCaro)).single()
        assertEquals(MotivoDelParecido.MISMO_IDENTIFICADOR, porNumero.motivo)
        assertEquals("dst_x", porNumero.queda.id)

        val conNombreDelBanco = DestinoConocido(id = "dst_y", nombre = "Lucía", numero = "", llave = "lucia gomez", creadoEn = 1L)
        val lucia = DestinoConocido(id = "dst_z", nombre = "Lucía Gómez", numero = "55500005555", creadoEn = 2L)
        assertEquals(MotivoDelParecido.MISMO_NOMBRE, posiblesDuplicados(listOf(conNombreDelBanco, lucia)).single().motivo)
    }

    @Test
    fun `una persona y un comercio elegidos asi no se proponen por el nombre`() {
        val persona = caro.copy(tipo = TipoDeTercero.PERSONA)
        val comercio = DestinoConocido(id = "dst_c", nombre = "Caro Panadería", numero = "", llave = "0011223344", tipo = TipoDeTercero.COMERCIO)
        assertTrue(posiblesDuplicados(listOf(persona, comercio)).isEmpty())
        // Pero si el tipo lo dedujo Movi, sí: una deducción no es una decisión del dueño.
        assertEquals(1, posiblesDuplicados(listOf(persona, comercio.copy(tipoInferido = true))).size)
    }
}
