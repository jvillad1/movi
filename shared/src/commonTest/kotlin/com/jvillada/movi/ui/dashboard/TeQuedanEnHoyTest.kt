package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Te quedan» en Hoy es la columna «Período» de Plan, y no otra cuenta** (Ola 1 · Movi avisa).
 *
 * Mismo escenario que `DisponibleEnInicioTest`: lunes 21 de septiembre de 2026, período del 25 de
 * agosto al 24 de septiembre (31 días, quedan 4 contando hoy). $10M de ingresos y $3,1M de arriendo
 * ya pagado: disponible $6,9M, meta diaria $222.580, meta de esta semana (corta, 4 días) $890.320.
 *
 * Cada prueba compara la cifra de Hoy contra `disponibleDelInicio(data).periodo.teQuedan`, que es
 * exactamente lo que pinta la tarjeta de Plan (`PlanScreen` → `TarjetaDelDisponible`).
 */
class TeQuedanEnHoyTest {

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(
        gasto: Map<String, Long>,
        ingresos: Long = 10_000_000,
        arriendo: Long = 3_100_000,
        conOcurrencias: Boolean = true,
    ) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = ingresos, egresos = 0),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = arriendo,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = if (conOcurrencias) {
            listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true))
        } else {
            null
        },
        gastoVariablePorDia = gasto,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    /** La cifra de Hoy y la de Plan, sacadas de los mismos datos. Tienen que ser la misma. */
    private fun lasDos(data: DashboardData, dia: LocalDate = hoy): Pair<TeQuedanDeHoy, DisponibleDelPeriodo> {
        val deHoy = assertNotNull(teQuedanDelInicio(data, dia))
        val dePlan = assertNotNull(disponibleDelInicio(data, dia))
        assertEquals(dePlan.periodo.teQuedan, deHoy.teQuedan, "Hoy y Plan dan dos cifras distintas")
        assertEquals(dePlan.periodo.nivel == NivelDelGasto.PASADO, deHoy.enRojo, "y dos colores distintos")
        return deHoy to dePlan
    }

    @Test
    fun `vas bien - te quedan, para cuantos dias y la meta diaria de la columna Hoy`() {
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-08-26" to 100_000L)))

        assertEquals(6_800_000, cifra.teQuedan)
        assertEquals(6_800_000, cifra.monto)
        assertEquals(ROTULO_TE_QUEDAN, cifra.rotulo)
        assertFalse(cifra.enRojo)
        // La meta diaria es la de la columna «Hoy», no lo que queda dividido entre los días.
        assertEquals(222_580, plan.metaPorDia)
        assertEquals("para 4 días · meta diaria \$222.580", cifra.detalle)
        assertEquals(NivelDelGasto.BIEN, cifra.nivelDelDetalle)
    }

    @Test
    fun `te pasaste - la misma cifra sin signo, en rojo, y cuantos dias faltan`() {
        val (cifra, _) = lasDos(datos(gasto = mapOf("2026-09-01" to 7_000_000L)))

        assertEquals(-100_000, cifra.teQuedan)
        assertEquals(100_000, cifra.monto, "el rótulo ya dice de qué lado está: el monto va sin signo")
        assertEquals(ROTULO_TE_PASASTE, cifra.rotulo)
        assertTrue(cifra.enRojo)
        assertEquals("del disponible del período · faltan 4 días para el corte", cifra.detalle)
        assertEquals(NivelDelGasto.PASADO, cifra.nivelDelDetalle)
    }

    @Test
    fun `la semana pasada se dice despues de los dias, con la cifra de la columna Semana`() {
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-09-21" to 1_000_000L)))

        assertEquals(5_900_000, cifra.teQuedan)
        assertEquals(890_320, plan.semana.meta)
        assertEquals("para 4 días · esta semana te pasaste de la meta por \$109.680", cifra.detalle)
        assertEquals(NivelDelGasto.PASADO, cifra.nivelDelDetalle)
        assertFalse(cifra.enRojo, "el período no está pasado: la cifra grande no va en rojo")
    }

    @Test
    fun `por encima del ritmo avisa sin pintar la cifra`() {
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-09-10" to 6_500_000L)))

        assertEquals(400_000, cifra.teQuedan)
        assertEquals(267_742, plan.periodo.contraElRitmo)
        assertEquals("para 4 días · vas \$267.742 por encima de lo previsto a hoy", cifra.detalle)
        assertEquals(NivelDelGasto.CERCA, cifra.nivelDelDetalle)
        assertFalse(cifra.enRojo)
    }

    @Test
    fun `sin margen - la frase de la tarjeta y lo gastado como te pasaste`() {
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-09-10" to 200_000L), arriendo = 12_000_000))

        assertFalse(plan.hayMargen)
        // Sin margen la meta es cero: la columna «Período» de Plan dice −$200.000 en rojo.
        assertEquals(-200_000, cifra.teQuedan)
        assertEquals(ROTULO_TE_PASASTE, cifra.rotulo)
        assertTrue(cifra.enRojo)
        assertEquals("Los fijos del período superan tus ingresos por \$2M", cifra.detalle)
        assertEquals(NivelDelGasto.PASADO, cifra.nivelDelDetalle)
    }

    @Test
    fun `el ultimo dia dice para cerrar el periodo`() {
        val (cifra, _) = lasDos(datos(gasto = emptyMap()), dia = LocalDate(2026, 9, 24))
        assertEquals(6_900_000, cifra.teQuedan)
        assertEquals("para cerrar el período", cifra.detalle)
    }

    @Test
    fun `sin una de las lecturas no hay cifra, igual que en Plan`() {
        val data = datos(gasto = emptyMap(), conOcurrencias = false)
        assertNull(disponibleDelInicio(data, hoy))
        assertNull(teQuedanDelInicio(data, hoy), "sin ocurrencias Plan no afirma nada, y Hoy tampoco")
    }

    @Test
    fun `sin ingresos ni plata no hay cifra que afirmar`() {
        val data = datos(gasto = emptyMap(), ingresos = 0)
        assertNull(teQuedanDelInicio(data, hoy))
    }

    @Test
    fun `con el aviso ya arriba, el veredicto no lo repite`() {
        val data = datos(gasto = mapOf("2026-09-01" to 7_000_000L), ingresos = 10_000_000).let {
            it.copy(summary = it.summary!!.copy(egresos = 7_000_000))
        }
        val conAviso = assertNotNull(veredictoDelInicio(data, hoy))
        val sinAviso = assertNotNull(veredictoDelInicio(data, hoy, conAvisoDelDisponible = false))
        assertTrue("te pasaste del disponible" in conAviso.frase)
        assertFalse("te pasaste del disponible" in sinAviso.frase)
    }
}
