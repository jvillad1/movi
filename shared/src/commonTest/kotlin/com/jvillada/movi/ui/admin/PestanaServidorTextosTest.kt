package com.jvillada.movi.ui.admin

import com.jvillada.movi.shared.model.IaDelDia
import com.jvillada.movi.shared.model.PuntoDelServidor
import com.jvillada.movi.shared.model.SaludDelServidor
import com.jvillada.movi.shared.model.ServicioMedido
import com.jvillada.movi.shared.model.VariableDeEntorno
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vuelta 102 · los textos de la pestaña «Servidor», sin pantalla. */
class PestanaServidorTextosTest {

    @Test
    fun milisegundos_porcentajes_y_tiempos() {
        assertEquals("sin datos", textoDeMs(null))
        assertEquals("850 ms", textoDeMs(850))
        assertEquals("1,2 s", textoDeMs(1_250))
        assertEquals("0 %", textoDePorcentaje(0.0))
        assertEquals("3,4 %", textoDePorcentaje(0.034))
        assertEquals("10 %", textoDePorcentaje(0.1))
        assertEquals("12 min", textoDeUptime(12 * 60_000L))
        assertEquals("5 h 12 min", textoDeUptime((5 * 60 + 12) * 60_000L))
        assertEquals("3 d 4 h", textoDeUptime(((3 * 24 + 4) * 60) * 60_000L))
        assertEquals("hace 5 min", haceCuanto(0, 5 * 60_000L))
        assertEquals("hace 2 d", haceCuanto(0, 2 * 86_400_000L))
    }

    @Test
    fun los_pedidos_por_hora_dicen_promedio_y_pico() {
        assertEquals("Sin pedidos en este lapso", lineaDePedidosPorHora(listOf(PuntoDelServidor(0, 0))))
        val serie = listOf(PuntoDelServidor(0, 10), PuntoDelServidor(1, 30), PuntoDelServidor(2, 1_200))
        assertEquals("Unos 413 por hora · pico de 1.200 en una hora", lineaDePedidosPorHora(serie))
    }

    @Test
    fun un_servicio_nombra_cada_clase_de_error() {
        assertEquals("1 llamada · sin errores", subtituloDelServicio(ServicioMedido("trm", "consulta", 1)))
        val ia = ServicioMedido("anthropic", "claude-haiku", llamadas = 120, sinCredito = 1, limite = 2)
        assertEquals("120 llamadas · 3 errores: 1 sin crédito, 2 por límite", subtituloDelServicio(ia))
        assertEquals("Anthropic", nombreDelServicio("anthropic"))
    }

    @Test
    fun la_ia_por_dia_dice_las_fichas_por_usuario() {
        val d = IaDelDia("2026-11-01", llamadas = 12, fichasEntrada = 45_000, fichasSalida = 3_000, usuarios = 2)
        assertEquals("12 llamadas · 2 usuarios · 45.000 fichas de entrada, 3.000 de salida", lineaDeLaIa(d))
        assertEquals("24.000 por usuario", fichasPorUsuario(d))
        assertEquals("sin usuarios", fichasPorUsuario(d.copy(usuarios = 0)))
    }

    @Test
    fun las_variables_dicen_cuales_faltan_y_nunca_un_valor() {
        val todas = SaludDelServidor(baseOk = true, variables = listOf(VariableDeEntorno("JWT_SECRET", true), VariableDeEntorno("DATABASE_URL", true)))
        assertEquals("Están las 2", lineaDeVariables(todas))
        val faltan = todas.copy(variables = todas.variables + VariableDeEntorno("RESEND_API_KEY", false))
        assertEquals("Faltan: RESEND_API_KEY", lineaDeVariables(faltan))
        assertEquals("Sin pool (no se mide)", lineaDelPool(todas))
        assertEquals("2 en uso de 10 · 0 esperando", lineaDelPool(todas.copy(poolActivas = 2, poolTotal = 10, poolEsperando = 0)))
    }
}
