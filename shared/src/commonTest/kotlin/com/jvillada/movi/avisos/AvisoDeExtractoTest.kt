package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.CUERPO_DEL_EXTRACTO_POR_SUBIR
import com.jvillada.movi.shared.model.ExtractoPorSubir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 94 · el aviso del extracto que falta suena una sola vez por tarjeta y corte. */
class AvisoDeExtractoTest {

    private val nuOctubre = ExtractoPorSubir("acc_nu", "Tarjeta Nu", "Nu", "2026-10-12")
    private val nuNoviembre = nuOctubre.copy(corte = "2026-11-12")
    private val master = ExtractoPorSubir("acc_master", "Master Black 3684", "Bancolombia", "2026-10-20")

    @Test
    fun `lo nuevo suena una vez por corte`() {
        assertEquals(listOf(nuOctubre), extractosParaAvisar(listOf(nuOctubre), yaAvisados = emptyList()))
        val avisados = recordarExtractosAvisados(emptyList(), listOf(nuOctubre))
        // El Worker corre otra vez al día siguiente: la misma tarjeta y el mismo corte no suenan.
        assertTrue(extractosParaAvisar(listOf(nuOctubre), avisados).isEmpty())
        assertNull(textoDelAvisoDeExtracto(extractosParaAvisar(listOf(nuOctubre), avisados)))
        // El corte siguiente es otra huella.
        assertEquals(listOf(nuNoviembre), extractosParaAvisar(listOf(nuNoviembre), avisados))
        // Otra tarjeta, el mismo corte: suena la otra sola.
        assertEquals(listOf(master), extractosParaAvisar(listOf(nuOctubre, master), avisados))
    }

    @Test
    fun `lo recordado tiene tope y olvida lo mas viejo`() {
        val muchas = (1..TOPE_DE_EXTRACTOS_AVISADOS).map { "extracto:x:$it" }
        val recordadas = recordarExtractosAvisados(muchas, listOf(nuOctubre))
        assertEquals(TOPE_DE_EXTRACTOS_AVISADOS, recordadas.size)
        assertEquals(nuOctubre.huella, recordadas.last())
        assertTrue("extracto:x:1" !in recordadas)
    }

    @Test
    fun `el texto dice la pregunta de Hoy, o cuantos faltan`() {
        val uno = textoDelAvisoDeExtracto(listOf(nuOctubre))!!
        assertEquals("¿Ya bajaste el extracto de Nu de octubre?", uno.titulo)
        assertEquals(CUERPO_DEL_EXTRACTO_POR_SUBIR, uno.texto)
        val dos = textoDelAvisoDeExtracto(listOf(nuOctubre, master))!!
        assertEquals("Faltan 2 extractos por subir", dos.titulo)
        assertEquals(listOf("Nu · octubre", "Bancolombia · octubre"), dos.lineas)
    }
}
