package com.jvillada.movi.ui.primeruso

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuiaDePrimerUsoTest {

    @Test
    fun `seis pasos en orden y la barra se llena en el ultimo`() {
        assertEquals(6, PasoDePrimerUso.entries.size)
        assertNull(PasoDePrimerUso.BIENVENIDA.anterior)
        assertNull(PasoDePrimerUso.LISTO.siguiente)
        assertEquals(PasoDePrimerUso.CUENTAS, PasoDePrimerUso.PERIODO.siguiente)
        assertEquals(1f, PasoDePrimerUso.LISTO.progreso)
        assertTrue(PasoDePrimerUso.BIENVENIDA.progreso > 0f)
    }

    @Test
    fun `Android pide los permisos del telefono y la web e iOS el reenvio del correo`() {
        assertEquals(ModoDeCaptura.PERMISOS_DEL_TELEFONO, modoDeCapturaDe(esAndroid = true))
        assertEquals(ModoDeCaptura.REENVIO_DE_CORREO, modoDeCapturaDe(esAndroid = false))
    }

    @Test
    fun `los bancos de la guia existen en MarcasConocidas`() {
        val conocidos = com.jvillada.movi.shared.model.BANCOS_CONOCIDOS.map { it.id }
        com.jvillada.movi.ui.accounts.BANCOS_DE_UN_TOQUE.forEach { assertTrue(it in conocidos, it) }
    }

    @Test
    fun `el resumen dice lo que quedo y lo que falta`() {
        val todo = lineasDelResumen(
            ResumenDelPrimerUso(
                periodo = "Cada día 25 · o el día hábil anterior",
                cuentas = 2, tarjetas = 1, recurrentes = 3,
                captura = EstadoDeLaCaptura.ACTIVA,
            ),
        )
        assertTrue(todo.all { it.hecho })
        assertEquals("2 cuentas y 1 tarjeta", todo[1].detalle)
        assertEquals("3 pagos", todo[3].detalle)

        val nada = lineasDelResumen(ResumenDelPrimerUso(null, 0, 0, 0, EstadoDeLaCaptura.PENDIENTE))
        assertTrue(nada.none { it.hecho })
        assertEquals("Sin cambiar: mes de calendario", nada[0].detalle)

        val copiada = lineasDelResumen(ResumenDelPrimerUso(null, 1, 0, 0, EstadoDeLaCaptura.DIRECCION_COPIADA))
        assertEquals(false, copiada[2].hecho, "copiar la dirección no prueba que el reenvío ande")
    }
}
