package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CambioDeCategoriaDeUnRecurrente
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.anomaliaDelCambioDeCategoria
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

/**
 * # «¿Arriendo va en Hogar, como en septiembre?» (vuelta 60)
 *
 * El recurrente que cambió de categoría se propone en «Movi propone hoy» con «Sí, cambiar» y «No».
 * «Sí, cambiar» usa las escrituras de siempre —la categoría del movimiento y, si la regla tiene la
 * otra, la de la regla—; nada se cambia sin el toque.
 */
class ElRecurrenteQueCambioDeCategoriaSeProponeTest {

    private val cambio = CambioDeCategoriaDeUnRecurrente(
        eventoId = "oct", nombre = "Arriendo", monto = 1_800_000, categoria = "Otros", categoriaAnterior = "Hogar",
        periodoAnterior = "2026-09", eventoAnteriorId = "sep", reglaId = "rr-arriendo", nombreDeLaRegla = "Arriendo",
    )

    @Test
    fun `se propone primero, con Si cambiar y No`() {
        val propuesta = propuestasDelInicio(DashboardData(anomalias = listOf(anomaliaDelCambioDeCategoria(cambio, emptyList()))))
            .single { it.tipo == TipoDePropuesta.ANOMALIA }
        assertEquals("¿Arriendo va en Hogar, como en septiembre?", propuesta.titulo)
        assertEquals(SI_CAMBIAR, propuesta.accion)
        assertEquals(AccionDeLaPropuesta.CambiarLaCategoria(cambio), propuesta.alTocar)
        assertEquals(NO_CAMBIAR, propuesta.accionSecundaria)
        assertEquals(AccionDeLaPropuesta.DescartarAnomalia("recurrente-categoria:oct"), propuesta.alTocarSecundaria)
    }

    @Test
    fun `Si cambiar cambia el movimiento y la regla, con las escrituras de siempre`() {
        val escrito = mutableListOf<String>()
        val regla = RecurringRule(
            id = "rr-arriendo", name = "Arriendo", category = "Otros", amount = 1_800_000, dayOfMonth = 26, type = TransactionType.EXPENSE,
        )
        val repo = object : RepositorioDePrueba() {
            override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
                escrito += "movimiento $id → $category"
                return FinancialEvent(
                    id = id, accountId = "a", type = TransactionType.EXPENSE, amount = 1, category = category, description = "", timestamp = 0,
                )
            }
            override suspend fun getRecurringRules(): List<RecurringRule> = listOf(regla)
            override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule {
                escrito += "regla $id → ${rule.category}"
                return rule
            }
        }
        runBlocking { aceptarElCambioDeCategoria(repo, cambio) }
        assertEquals(listOf("movimiento oct → Hogar", "regla rr-arriendo → Hogar"), escrito)

        // Sin regla que corregir, solo el movimiento.
        escrito.clear()
        runBlocking { aceptarElCambioDeCategoria(repo, cambio.copy(reglaId = null)) }
        assertEquals(listOf("movimiento oct → Hogar"), escrito)
    }
}
