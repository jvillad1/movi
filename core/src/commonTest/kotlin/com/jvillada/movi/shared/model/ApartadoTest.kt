package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La cuenta de los apartados ([Apartado]): lo que queda, y cómo restan de «Tu plata». */
class ApartadoTest {

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 8_217_464L)

    private fun colegio(restante: Long = 8_000_000L, cuenta: String = "acc-nu", proposito: String = "Colegio") =
        Apartado(id = "ap-1", accountId = cuenta, proposito = proposito, monto = 8_000_000L, restante = restante)

    @Test
    fun lo_que_queda_de_un_apartado_nunca_baja_de_cero() {
        assertEquals(4_000_000L, restanteDelApartado(8_000_000L, 4_000_000L))
        assertEquals(0L, restanteDelApartado(8_000_000L, 9_000_000L))
        assertEquals(8_000_000L, restanteDelApartado(8_000_000L, 0L))
    }

    @Test
    fun el_apartado_baja_Tu_plata_pero_no_el_neto() {
        val p = patrimonioDe(listOf(nu), listOf(colegio()))

        assertEquals(217_464L, p.tuPlata)
        assertEquals(8_000_000L, p.apartado)
        assertEquals("Colegio", p.apartadoPara)
        assertEquals(patrimonioDe(listOf(nu)).neto, p.neto)
    }

    @Test
    fun un_apartado_mayor_al_saldo_solo_resta_el_saldo() {
        val pobre = Account("acc-nu", "Nu", AccountType.SAVINGS, 3_000_000L)
        val p = patrimonioDe(listOf(pobre), listOf(colegio()))

        assertEquals(3_000_000L, p.apartado)
        assertEquals(0L, p.tuPlata)
    }

    @Test
    fun un_apartado_en_una_inversion_baja_lo_invertido_y_no_lo_disponible() {
        val cdt = Account("acc-cdt", "CDT", AccountType.INVESTMENT, 10_000_000L)
        val p = patrimonioDe(listOf(nu, cdt), listOf(colegio(cuenta = "acc-cdt")))

        assertEquals(2_000_000L, p.invertido)
        assertEquals(8_217_464L, p.disponible)
    }

    @Test
    fun un_apartado_en_una_cuenta_condicionada_o_de_deuda_se_ignora() {
        val skandia = Account("acc-sk", "Skandia", AccountType.INVESTMENT, 100_000_000L, condicionadaA = "Vivienda")
        val prestamo = Account("acc-loan", "Hipoteca", AccountType.LOAN, 500_000_000L)
        val base = patrimonioDe(listOf(nu, skandia, prestamo))
        val p = patrimonioDe(
            listOf(nu, skandia, prestamo),
            listOf(colegio(cuenta = "acc-sk"), colegio(cuenta = "acc-loan"), colegio(cuenta = "no-existe")),
        )

        assertEquals(base, p)
        assertNull(p.apartadoPara)
    }

    @Test
    fun con_varios_propositos_distintos_no_se_inventa_uno() {
        val p = patrimonioDe(listOf(nu), listOf(colegio(), colegio(proposito = "Predial")))

        assertNull(p.apartadoPara)
        assertEquals(8_217_464L, p.apartado)
    }

    @Test
    fun el_Disponible_resta_lo_apartado() {
        val sin = DashboardSummary(saldoTuPlataAlInicio = 5_000_000L, entradasDelPeriodo = 1_000_000L)
        val con = sin.copy(apartado = 500_000L)

        assertEquals(500_000L, plataDelDisponibleDe(sin)!!.neto - plataDelDisponibleDe(con)!!.neto)
    }
}
