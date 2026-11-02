package com.jvillada.movi.ui.profile

import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.FrecuenciaDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.presupuestosDelPeriodo
import com.jvillada.movi.ui.periodos.LaPieza
import com.jvillada.movi.ui.periodos.MesDeLasQuincenas
import com.jvillada.movi.ui.periodos.PiezaDePeriodos
import com.jvillada.movi.ui.periodos.conLosMeses
import com.jvillada.movi.ui.primeruso.ajustesDeLaGuia
import com.jvillada.movi.ui.primeruso.reglasDelSalarioQuincenal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 105 · la quincena en la app, lo puro
 *
 * Lo que dice Ajustes, el salario de la guía (dos reglas), los presupuestos de Hoy a la mitad y las
 * quincenas agrupadas bajo su mes en «Tus períodos». Con período mensual, nada de esto cambia.
 */
class LaQuincenaEnLaAppTest {

    private fun perfil(frecuencia: String = "MENSUAL", corte: Int = 1, segundo: Int? = null) =
        UserProfile(id = "u", email = "a@b.c", name = "A", avatarColor = "#000000",
            periodCutoffDay = corte, periodFrequency = frecuencia, periodSecondDay = segundo)

    @Test
    fun `la fila de Ajustes dice la quincena, y el mes como siempre`() {
        assertEquals("Cada quincena · el 15 y el último día · o el día hábil anterior",
            textoDelPeriodoDelPerfil(perfil("QUINCENAL", 15, 31), SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR))
        assertEquals("Cada quincena · el 1 y el 16", textoDelPeriodoDelPerfil(perfil("QUINCENAL", 1, 16), SiElCorteCaeEnFinDeSemana.ESE_DIA))
        assertEquals(textoDelInicioDelMes(25, SiElCorteCaeEnFinDeSemana.ESE_DIA), textoDelPeriodoDelPerfil(perfil(corte = 25), SiElCorteCaeEnFinDeSemana.ESE_DIA))
        assertEquals("Mes de calendario", textoDelPeriodoDelPerfil(perfil(), SiElCorteCaeEnFinDeSemana.ESE_DIA))
        // Un perfil quincenal sin el segundo día se lee como mes (y la app dice eso).
        assertEquals("Cada día 15", textoDelPeriodoDelPerfil(perfil("QUINCENAL", 15, null), SiElCorteCaeEnFinDeSemana.ESE_DIA))
    }

    @Test
    fun `el salario por quincenas son dos reglas, y con ya te pagaron cada una arranca en su proxima quincena`() {
        // 20-oct-2026: quincena del 15 al 30; el pago del 15 ya llegó.
        val ahora = LocalDate(2026, 10, 20).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
        val reglas = reglasDelSalarioQuincenal(2_000_000, 15, 31, SiElCorteCaeEnFinDeSemana.ESE_DIA, "acc", yaTePagaron = true, ahoraMillis = ahora)
        assertEquals(listOf(15, 31), reglas.map { it.dayOfMonth })
        assertEquals(listOf(2_000_000L, 2_000_000L), reglas.map { it.amount })
        // La del 15 espera desde el 15 de noviembre (la de octubre ya está en el saldo); la del 31, desde
        // el 31 de octubre (todavía no llega).
        assertEquals(listOf("2026-11-15", "2026-10-31"), reglas.map { it.activeFrom })
        val sinPagar = reglasDelSalarioQuincenal(2_000_000, 15, 31, SiElCorteCaeEnFinDeSemana.ESE_DIA, null, yaTePagaron = false, ahoraMillis = ahora)
        assertEquals(listOf(null, null), sinPagar.map { it.activeFrom })
    }

    @Test
    fun `los ajustes de la guia son quincenales solo con el segundo dia`() {
        assertEquals(PeriodSettings(cutoffDay = 25), ajustesDeLaGuia(25, SiElCorteCaeEnFinDeSemana.ESE_DIA))
        assertTrue(ajustesDeLaGuia(15, SiElCorteCaeEnFinDeSemana.ESE_DIA, 31).esQuincenal)
    }

    @Test
    fun `Hoy mide los presupuestos a la mitad en la quincena y enteros en el mes`() {
        val presupuestos = listOf(Budget("Comida", 800_000))
        val quincena = perfil("QUINCENAL", 15, 31).ajustesDelPeriodo()
        val enQuincena = DashboardData(budgets = presupuestos, ajustesDePeriodo = quincena,
            periodoActual = periodoDeLaFecha("2026-10-20", quincena))
        assertEquals(400_000, enQuincena.presupuestosDelPeriodo!!.single().monthlyLimit)
        val enMes = DashboardData(budgets = presupuestos, ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
            periodoActual = PeriodoFinanciero(2026, 11))
        assertEquals(presupuestos, enMes.presupuestosDelPeriodo)
    }

    private fun resumen(id: String, hasta: String = "2026-10-14", entradas: Long = 0, salidas: Long = 0) =
        ResumenDePeriodo(id = id, nombre = id, desde = id, hasta = hasta, entradas = entradas, salidas = salidas)

    @Test
    fun `tus periodos agrupan cada quincena bajo el mes en que termina, y los meses quedan igual`() {
        // 15 y el último: la del 31-oct termina el 14-nov y va en Noviembre.
        val quincenas = listOf(
            resumen("2026-11-15", "2026-11-29", 100, 10),
            resumen("2026-10-31", "2026-11-14", 200, 20),
            resumen("2026-10-15", "2026-10-30", 300, 30),
        ).map { PiezaDePeriodos.Tarjeta(it) }
        val lista = conLosMeses(quincenas)
        assertEquals(listOf("mes-2026-11", "2026-11-15", "2026-10-31", "mes-2026-10", "2026-10-15"), lista.map { it.clave })
        val noviembre = lista[0] as MesDeLasQuincenas
        assertEquals("Noviembre 2026", noviembre.titulo)
        assertTrue(noviembre.subtitulo.startsWith("Entró "))
        val meses = listOf(resumen("2026-10"), resumen("2026-09")).map { PiezaDePeriodos.Tarjeta(it) }
        assertEquals(meses.map(::LaPieza), conLosMeses(meses))
    }
}
