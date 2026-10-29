package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TipoDeNotificacion
import com.jvillada.movi.ui.dashboard.conteoPorRevisarDelInicio
import com.jvillada.movi.ui.dashboard.notificationRows
import com.jvillada.movi.ui.dashboard.pendientesPorRevisar
import com.jvillada.movi.ui.espacios.laPropuestaEnLaCampana
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 67 · lo que salda un grupo cuenta en «Por revisar»
 *
 * La revisión de la vuelta 66: la bandeja decía «Todo al día» debajo de «Para saldar en tus grupos», y
 * el «N por revisar» del Inicio y de Actividad no contaba esas propuestas.
 */
class LoQueSaldaUnGrupoSeCuentaTest {

    private val propuesta = PropuestaDeSaldar(
        eventoId = "tr_caro", de = "dst_caro", a = MIEMBRO_YO, monto = 168_050, fecha = 0L,
        descripcion = "Transferencia de Caro", liquidacionId = "liq_1", espacioId = "esp", nombreDelEspacio = "Casa con Caro", quien = "Caro",
    )
    private val vacio = emptyList<FinancialEvent>()

    @Test
    fun `con una propuesta de saldar la bandeja no esta al dia`() {
        assertTrue(bandejaAlDia(emptyList(), emptyList(), vacio, emptyList(), emptyList()))
        assertFalse(bandejaAlDia(emptyList(), emptyList(), vacio, emptyList(), listOf(propuesta)))
        assertFalse(bandejaAlDia(emptyList(), emptyList(), vacio, emptyList(), null), "sin la lectura no se sabe")
    }

    @Test
    fun `el conteo de la bandeja y el del Inicio la cuentan igual`() {
        val deLaBandeja = conteoPorRevisar(emptyList(), emptyList(), vacio, emptyList(), propuestasDeSaldar = listOf(propuesta))
        assertEquals(1, deLaBandeja.total)
        assertEquals("1 para saldar", desgloseDePorRevisar(deLaBandeja))
        val data = DashboardData(propuestasDeSaldar = listOf(propuesta))
        assertEquals(deLaBandeja.total, conteoPorRevisarDelInicio(data).total)
        assertEquals(1, pendientesPorRevisar(data), "la insignia de Avisos la cuenta")
    }

    @Test
    fun `en la campana va con un titulo corto y la pregunta debajo`() {
        val (titulo, pregunta) = laPropuestaEnLaCampana(propuesta, "Caro")
        assertEquals("Transferencia de Caro", titulo)
        assertEquals("¿Es la que marcaste como saldada? · Casa con Caro", pregunta)
        val fila = notificationRows(DashboardData(propuestasDeSaldar = listOf(propuesta)))
            .single { it.tipo == TipoDeNotificacion.TransferenciaParaSaldar }
        assertEquals("Transferencia de Caro", fila.titulo)
        assertEquals(2, fila.lineasDelSubtitulo)
    }
}
