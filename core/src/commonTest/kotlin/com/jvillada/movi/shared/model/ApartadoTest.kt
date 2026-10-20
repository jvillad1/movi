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

    @Test
    fun el_efectivo_por_cuenta_tiene_tope_en_el_saldo_y_solo_cuenta_tu_plata() {
        val casiVacia = Account("acc-x", "X", AccountType.SAVINGS, 1_000_000L)
        val tarjeta = Account("acc-tc", "Tarjeta", AccountType.CREDIT_CARD, 500_000L)
        val efectivo = apartadoEfectivoPorCuenta(
            listOf(nu, casiVacia, tarjeta),
            listOf(colegio(), colegio(cuenta = "acc-x"), colegio(cuenta = "acc-tc")),
        )

        assertEquals(mapOf("acc-nu" to 8_000_000L, "acc-x" to 1_000_000L), efectivo)
        assertEquals(efectivo.values.sum(), patrimonioDe(listOf(nu, casiVacia, tarjeta), listOf(colegio(), colegio(cuenta = "acc-x"))).apartado)
    }

    // ── El apartado que ya es un fijo del período ────────────────────────────

    /** «Colegio Hija»: $4M el 25 desde Nu, categoría «Hija». */
    private fun colegioHija(pagado: Boolean = false, cuenta: String? = "acc-nu", categoria: String = "Hija", monto: Long = 4_000_000L) =
        PagoDelPeriodo(
            ruleId = "r-colegio", nombre = "Colegio Hija", monto = monto, pagado = pagado, diasParaVencer = 5,
            montoPagado = if (pagado) monto else null, categoria = categoria, cuentaId = cuenta,
        )

    private fun apartadoHija(restante: Long = 8_000_000L) =
        Apartado(id = "ap-1", accountId = "acc-nu", proposito = "Colegio", monto = 8_000_000L, categoria = "Hija", restante = restante)

    private fun teQuedan(apartado: Long, checklist: List<PagoDelPeriodo>): Long =
        disponibleDelPeriodo(
            ingresosRecibidos = 0L,
            checklist = checklist,
            gastoVariablePorDia = emptyMap(),
            inicio = kotlinx.datetime.LocalDate(2026, 9, 25),
            finExclusivo = kotlinx.datetime.LocalDate(2026, 10, 25),
            hoy = kotlinx.datetime.LocalDate(2026, 10, 20),
            plata = PlataDelDisponible(saldoAlInicio = 20_000_000L, entradas = 0L, guardado = 0L, apartado = apartado),
        )!!.disponible

    @Test
    fun el_pago_pendiente_del_colegio_no_se_resta_dos_veces() {
        val pendiente = listOf(colegioHija())
        val fuera = apartadoFueraDeLosFijos(listOf(nu), listOf(apartadoHija()), pendiente)

        assertEquals(4_000_000L, apartadoCubiertoPorLosFijos(listOf(nu), listOf(apartadoHija()), pendiente))
        assertEquals(4_000_000L, fuera)
        // «Te quedan» baja exactamente lo que el apartado no tiene ya en los fijos: $4M, no $8M.
        assertEquals(4_000_000L, teQuedan(0L, pendiente) - teQuedan(fuera, pendiente))
    }

    @Test
    fun pagar_el_colegio_no_mueve_Te_quedan() {
        val antes = listOf(colegioHija())
        val fueraAntes = apartadoFueraDeLosFijos(listOf(nu), listOf(apartadoHija()), antes)
        // El gasto de «Hija» por $4M desde Nu: baja el saldo y el restante, y la fila queda pagada.
        val nuDespues = nu.copy(balance = 4_217_464L)
        val despues = listOf(colegioHija(pagado = true))
        val fueraDespues = apartadoFueraDeLosFijos(listOf(nuDespues), listOf(apartadoHija(restante = 4_000_000L)), despues)

        assertEquals(4_000_000L, fueraDespues)
        assertEquals(teQuedan(fueraAntes, antes), teQuedan(fueraDespues, despues))
    }

    @Test
    fun solo_se_cruza_un_pendiente_de_la_misma_cuenta_y_la_misma_categoria() {
        val apartados = listOf(apartadoHija())
        fun cubierto(fila: PagoDelPeriodo) = apartadoCubiertoPorLosFijos(listOf(nu), apartados, listOf(fila))

        assertEquals(0L, cubierto(colegioHija(cuenta = "otra")))
        assertEquals(0L, cubierto(colegioHija(cuenta = null)))
        assertEquals(0L, cubierto(colegioHija(categoria = "Comida")))
        assertEquals(0L, cubierto(colegioHija(categoria = "")))
        assertEquals(0L, cubierto(colegioHija(pagado = true)))
        assertEquals(0L, cubierto(colegioHija().copy(moneda = "USD")))
        // Nunca más que lo que queda del apartado.
        assertEquals(8_000_000L, cubierto(colegioHija(monto = 10_000_000L)))
        // Un apartado sin categoría no lo consume ningún pago.
        assertEquals(0L, apartadoCubiertoPorLosFijos(listOf(nu), listOf(colegio()), listOf(colegioHija())))
    }

    @Test
    fun lo_cubierto_tiene_tope_en_lo_que_pesa_en_la_cuenta() {
        val pobre = nu.copy(balance = 3_000_000L)

        assertEquals(3_000_000L, apartadoCubiertoPorLosFijos(listOf(pobre), listOf(apartadoHija()), listOf(colegioHija())))
        assertEquals(0L, apartadoFueraDeLosFijos(listOf(pobre), listOf(apartadoHija()), listOf(colegioHija())))
    }
}
