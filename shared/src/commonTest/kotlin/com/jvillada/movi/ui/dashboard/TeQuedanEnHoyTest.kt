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
 * ya pagado: disponible $6,9M, ritmo fijo $222.580 por día. Desde la vuelta 60 la meta de la semana y
 * la de hoy salen de lo que queda (esta semana, la última, se lleva todo lo que quedaba el lunes).
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
    fun `vas bien - te quedan, para cuantos dias y lo que queda por dia`() {
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-08-26" to 100_000L)))

        assertEquals(6_800_000, cifra.teQuedan)
        assertEquals(6_800_000, cifra.monto)
        assertEquals(ROTULO_TE_QUEDAN, cifra.rotulo)
        assertFalse(cifra.enRojo)
        // Vuelta de diseño 1: junto a «para 4 días» va lo que queda entre esos 4 días ($6,8M ÷ 4),
        // no la meta diaria del período entero ($222.580), que se leía como «$222.580 por día».
        assertEquals(222_580, plan.metaPorDia)
        assertEquals(1_700_000, plan.porDiaParaLoQueQueda)
        assertEquals("para 4 días · \$1,7M por día", cifra.detalle)
        assertFalse("meta diaria" in cifra.detalle)
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

    /**
     * Vuelta 45: **una sola alarma.** La semana pasada ya no va en el detalle de la cifra —que es el
     * mismo en el héroe del Inicio y en Pagos—: la dice una vez la alarma de Pagos, con qué hacer.
     */
    @Test
    fun `la semana pasada no va en el detalle - la dice una vez la alarma de Pagos`() {
        // Vuelta 60: la semana mide contra lo que quedaba el lunes, así que la última semana del período
        // (que se lleva todo lo que queda) no se puede pasar sin pasar el período. Esta es la del lunes
        // 14: quedaban $6,9M para 11 días, $627.272 por día, por 7 = $4.390.904.
        val lunes14 = LocalDate(2026, 9, 14)
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-09-14" to 4_500_000L)), lunes14)

        assertEquals(2_400_000, cifra.teQuedan)
        assertEquals(4_390_904, plan.semana.meta)
        assertEquals("para 11 días · \$218.181 por día", cifra.detalle)
        assertFalse("meta" in cifra.detalle, "el detalle no repite la alarma de la semana")
        assertFalse(cifra.enRojo, "el período no está pasado: la cifra grande no va en rojo")
        assertEquals(
            AlertaDelDisponible("Esta semana te pasaste de la meta por \$109.096."),
            alertaDelDisponible(plan),
        )
        // Ninguna barra en alerta: el período no se pasó.
        assertFalse(barraEnAlerta(plan, delPeriodo = true))
        assertFalse(barraEnAlerta(plan, delPeriodo = false))
    }

    @Test
    fun `hoy pasado - la alarma lo dice con la cifra de la columna de hoy`() {
        // Vuelta 60: $1.800.000 hoy contra una meta de $1.725.000 (lo que quedaba esta mañana, $6,9M,
        // entre los 4 días que quedan).
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-09-21" to 1_800_000L)))

        assertEquals(1_725_000, plan.hoy.meta)
        assertFalse("meta" in cifra.detalle)
        assertEquals(AlertaDelDisponible("Hoy te pasaste de la meta por \$75.000."), alertaDelDisponible(plan))
    }

    @Test
    fun `periodo pasado - la cifra ya lo dice, la alarma es solo que hacer, y una sola barra en alerta`() {
        val (cifra, plan) = lasDos(datos(gasto = mapOf("2026-09-01" to 7_000_000L)))

        assertEquals(ROTULO_TE_PASASTE, cifra.rotulo)
        assertEquals(AlertaDelDisponible(texto = null), alertaDelDisponible(plan))
        assertTrue(barraEnAlerta(plan, delPeriodo = true))
        assertFalse(barraEnAlerta(plan, delPeriodo = false), "la semana y hoy no van en alerta")
    }

    @Test
    fun `vas bien o sin margen - no hay alarma aparte`() {
        val (_, bien) = lasDos(datos(gasto = mapOf("2026-08-26" to 100_000L)))
        assertNull(alertaDelDisponible(bien))
        val (_, sinMargen) = lasDos(datos(gasto = mapOf("2026-09-10" to 200_000L), arriendo = 12_000_000))
        assertNull(alertaDelDisponible(sinMargen), "sin margen lo explica el detalle de la cifra")
        assertFalse(barraEnAlerta(sinMargen, delPeriodo = true))
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
    fun `el ultimo dia dice para hoy`() {
        val (cifra, _) = lasDos(datos(gasto = emptyMap()), dia = LocalDate(2026, 9, 24))
        assertEquals(6_900_000, cifra.teQuedan)
        assertEquals("para hoy", cifra.detalle)
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
