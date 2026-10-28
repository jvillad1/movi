package com.jvillada.movi.ui.periodos

import com.jvillada.movi.ui.components.ds.TonoDeCifra
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «Neto del período» en Tus períodos (vuelta de diseño 28; antes «Te quedó»). La `Cifra` del sistema
 * con este tono pone el signo y el color: «+» y `entra` si entró más de lo que salió, «−» **en el color
 * del texto** si salió más —un neto negativo no es una alerta—, apagado en cero.
 */
class ElNetoDelPeriodoTest {

    @Test
    fun `el rotulo es neto del periodo, no te quedo`() {
        assertEquals("Neto del período", ROTULO_NETO_DEL_PERIODO)
    }

    @Test
    fun `el neto va en entra si quedo algo, neutro si salio mas, apagado en cero`() {
        assertEquals(TonoDeCifra.Ingreso, tonoDelNetoDelPeriodo(1))
        assertEquals(TonoDeCifra.Neutro, tonoDelNetoDelPeriodo(-817_250))
        assertEquals(TonoDeCifra.Apagado, tonoDelNetoDelPeriodo(0))
    }
}
