package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.apartadoFueraDeLosFijos
import com.jvillada.movi.shared.model.mesDelGastoDe
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.PlataDelDisponible
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.dashboard.cuentasDelCarrusel
import com.jvillada.movi.ui.dashboard.notaDeLoApartado
import com.jvillada.movi.ui.dashboard.teQuedanDelInicio
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Los apartados que avisan (vuelta 64)
 *
 * La escena de la revisión del 29-oct: «Te quedan $1.450.950», un ahorro del período de $8M, y crear
 * «Colegio $2M» lo dejaba en «−$549.050» sin que la hoja dijera nada. La vista previa sale de la
 * función del héroe ([teQuedanDelInicio]) y da la misma cifra que el Inicio después de guardar.
 */
class ElApartadoQueAvisaTest {

    private val hoy = LocalDate(2026, 9, 21)
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 10_581_200L)

    /**
     * El Inicio del 21-sep (período 25-ago/24-sep): tenías $10M, entraron $5M, el arriendo de $3,1M ya
     * pagado, $8M de ahorro del período y $2.449.050 gastados → «Te quedan $1.450.950».
     */
    private fun inicio(ahorro: Long = 8_000_000L, apartados: List<Apartado> = emptyList(), apartadoDelServer: Long = 0L) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(
            OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true),
        ),
        gastoVariablePorDia = mapOf("2026-09-01" to 2_449_050L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
        plataDelDisponible = PlataDelDisponible(
            saldoAlInicio = 10_000_000, entradas = 5_000_000, guardado = 0,
            apartado = apartadoDelServer, ahorroDelPeriodo = ahorro,
        ),
        accounts = listOf(nu),
        apartados = apartados,
    )

    private val colegio = Apartado(id = "apt_colegio", accountId = "acc-nu", proposito = "Colegio", monto = 2_000_000L)

    @Test
    fun `la vista previa dice como queda Te quedan con el apartado`() {
        val v = assertNotNull(teQuedanConElApartado(inicio(), colegio, hoy))
        assertEquals(1_450_950L, v.antes)
        assertEquals(-549_050L, v.despues)
        assertEquals("Te quedan pasa de \$1.450.950 a −\$549.050", fraseDeLaVistaPrevia(v))
    }

    /**
     * **La misma cifra antes y después de guardar**: el Inicio que llega después trae el apartado en la
     * lista y lo apartado que el server manda (la misma [apartadoFueraDeLosFijos]); su héroe dice lo
     * que la hoja anunció.
     */
    @Test
    fun `la vista previa da la misma cifra que el Inicio despues de guardar`() {
        val antes = inicio()
        val v = assertNotNull(teQuedanConElApartado(antes, colegio, hoy))
        assertEquals(teQuedanDelInicio(antes, hoy)?.teQuedan, v.antes, "«antes» es el héroe de hoy")

        val guardados = listOf(colegio)
        val delServer = apartadoFueraDeLosFijos(listOf(nu), guardados, checklistDelPeriodoDe(antes))
        val despues = inicio(apartados = guardados, apartadoDelServer = delServer)
        assertEquals(teQuedanDelInicio(despues, hoy)?.teQuedan, v.despues)
    }

    @Test
    fun `editar un apartado mueve solo la diferencia`() {
        val guardados = listOf(colegio)
        val delServer = apartadoFueraDeLosFijos(listOf(nu), guardados, emptyList())
        val data = inicio(apartados = guardados, apartadoDelServer = delServer)
        val v = assertNotNull(teQuedanConElApartado(data, colegio.copy(monto = 2_500_000L), hoy))
        assertEquals(-549_050L, v.antes)
        // La meta del período pasa de $1,9M a $1,4M: medio millón más apartado, medio millón menos.
        assertEquals(1_400_000L - 2_449_050L, v.despues)
    }

    @Test
    fun `con ahorro del periodo la advertencia dice que se resta aparte`() {
        val v = assertNotNull(teQuedanConElApartado(inicio(), colegio, hoy))
        val aviso = assertNotNull(advertenciaDeLaVistaPrevia(v))
        assertTrue("Tu ahorro del período (\$8.000.000) se resta aparte" in aviso, aviso)
        assertTrue("Ahorro de cada período" in aviso, aviso)
    }

    @Test
    fun `sin quedar en negativo no hay advertencia, y sin ahorro la advertencia es otra`() {
        val chico = assertNotNull(teQuedanConElApartado(inicio(), colegio.copy(monto = 500_000L), hoy))
        assertEquals(950_950L, chico.despues)
        assertNull(advertenciaDeLaVistaPrevia(chico))

        val sinAhorro = TeQuedanConElApartado(antes = 1_000_000L, despues = -200_000L, ahorroDelPeriodo = 0L)
        assertEquals("Te pasarías del disponible del período.", advertenciaDeLaVistaPrevia(sinAhorro))
    }

    @Test
    fun `sin la plata del disponible no se afirma nada`() {
        assertNull(teQuedanConElApartado(inicio().copy(plataDelDisponible = null), colegio, hoy))
        assertNull(teQuedanConElApartado(inicio().copy(accounts = null), colegio, hoy))
    }

    @Test
    fun `uno que se edita con su categoria conserva lo consumido`() {
        val actual = Apartado(id = "a", accountId = "acc-nu", proposito = "Colegio", monto = 8_000_000L, categoria = "Hija", restante = 4_000_000L)
        assertEquals(5_000_000L, conSuRestante(actual.copy(monto = 9_000_000L), actual).restante)
        assertEquals(9_000_000L, conSuRestante(actual.copy(monto = 9_000_000L, categoria = "Otra"), actual).restante)
        assertEquals(2_000_000L, conSuRestante(colegio, null).restante)
    }

    // ── «Tus cuentas» en el Inicio ────────────────────────────────────────────

    @Test
    fun `Tus cuentas dice lo apartado debajo del saldo y concuerda con el detalle`() {
        val c = assertNotNull(cuentasDelCarrusel(listOf(nu), listOf(colegio))).single()
        assertEquals(10_581_200L, c.saldo, "el saldo sigue siendo el del banco")
        assertEquals(2_000_000L, c.apartado)
        assertEquals("\$2.000.000 apartado", notaDeLoApartado(c))
        // El detalle dice «Disponible en la cuenta» = saldo − lo apartado.
        assertEquals(c.saldo - c.apartado, libreDeLaCuenta(nu, listOf(colegio)))

        val sin = assertNotNull(cuentasDelCarrusel(listOf(nu))).single()
        assertNull(notaDeLoApartado(sin))
    }

    @Test
    fun `lo apartado en la tarjeta nunca pasa del saldo`() {
        val pobre = nu.copy(balance = 1_000_000L)
        val c = assertNotNull(cuentasDelCarrusel(listOf(pobre), listOf(colegio))).single()
        assertEquals(1_000_000L, c.apartado)
        assertEquals(0L, libreDeLaCuenta(pobre, listOf(colegio)))
    }

    // ── El gasto de un mes puntual ────────────────────────────────────────────

    private val hoyDeOctubre = LocalDate(2026, 10, 29)

    @Test
    fun `el detalle dice cuanto lleva y cuanto apartar al mes`() {
        val soat = Apartado(
            id = "s", accountId = "acc-nu", proposito = "SOAT", monto = 400_000L,
            objetivo = 1_200_000L, mesDelGasto = "2027-03",
        )
        assertEquals(
            "Para marzo de 2027: llevas \$400.000 de \$1.200.000 · aparta \$160.000 al mes",
            lineaDelGasto(soat, mesDelGastoDe(soat), hoyDeOctubre),
        )
        val diciembre = soat.copy(mesDelGasto = "2026-12")
        assertEquals(
            "Para diciembre: llevas \$400.000 de \$1.200.000 · aparta \$400.000 al mes",
            lineaDelGasto(diciembre, mesDelGastoDe(diciembre), hoyDeOctubre),
        )
        val completo = soat.copy(monto = 1_200_000L, restante = 1_200_000L)
        assertEquals(
            "Para marzo de 2027: llevas \$1.200.000 de \$1.200.000 · ya lo tienes",
            lineaDelGasto(completo, mesDelGastoDe(completo), hoyDeOctubre),
        )
        assertNull(lineaDelGasto(colegio, null, hoyDeOctubre), "un apartado de siempre no tiene esa línea")
    }

    @Test
    fun `la hoja sugiere el aporte y ofrece este mes y los once que siguen`() {
        val meses = mesesParaElGasto(hoyDeOctubre)
        assertEquals(12, meses.size)
        assertEquals(PeriodoFinanciero(2026, 10), meses.first())
        assertEquals(PeriodoFinanciero(2027, 9), meses.last())
        assertEquals("oct", pastillaDelMes(meses.first(), hoyDeOctubre))
        assertEquals("mar 2027", pastillaDelMes(PeriodoFinanciero(2027, 3), hoyDeOctubre))

        val (principal, detalle) = assertNotNull(sugerenciaDelGasto(1_200_000L, 400_000L, PeriodoFinanciero(2027, 3), hoyDeOctubre))
        assertEquals("Aparta \$160.000 al mes", principal)
        assertTrue("te faltan \$800.000 en 5 meses" in detalle, detalle)
        assertNull(sugerenciaDelGasto(null, 400_000L, PeriodoFinanciero(2027, 3), hoyDeOctubre))
        assertEquals(
            "Es para un gasto que llega en marzo de 2027",
            tituloDeLaCasillaDelGasto(PeriodoFinanciero(2027, 3), hoyDeOctubre),
        )
    }
}
