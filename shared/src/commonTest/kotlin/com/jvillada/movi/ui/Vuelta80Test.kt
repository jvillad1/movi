package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.accounts.cifraDeLasDeudasEnLaFila
import com.jvillada.movi.ui.accounts.laCurvaLlevaRelleno
import com.jvillada.movi.ui.components.ds.IconoResuelto
import com.jvillada.movi.ui.components.ds.iconoDelMovimiento
import com.jvillada.movi.ui.components.ds.inicialesDelDueno
import com.jvillada.movi.ui.components.ds.marcaDelComercio
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.credits.AccionSinSaldo
import com.jvillada.movi.ui.credits.CARGAR_EL_SALDO_DEL_CREDITO
import com.jvillada.movi.ui.credits.CUADRAR_LAS_DEUDAS_SIN_SALDO
import com.jvillada.movi.ui.credits.ElSaldoDelPlanEnLaFicha
import com.jvillada.movi.ui.credits.USAR_EL_SALDO_DEL_PLAN
import com.jvillada.movi.ui.credits.loQueSeDiceSinSaldo
import com.jvillada.movi.ui.credits.loQueSeRegistraAlAjustar
import com.jvillada.movi.ui.recurrentes.AccionDeLaFila
import com.jvillada.movi.ui.recurrentes.accionDeLaFilaPendiente
import com.jvillada.movi.ui.sdui.fraccionDeLaCategoriaEnElWidget
import com.jvillada.movi.ui.sdui.tonoDeLaPildoraDeLaDiferencia
import com.jvillada.movi.ui.transactions.AvisosPorConfirmar
import com.jvillada.movi.ui.transactions.botonCompactoDeConfirmar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 80 · lo puro: la prioridad única del ícono de un movimiento, las iniciales del dueño, la pieza
 * del préstamo sin saldo, la diferencia que explica la hoja de ajustar, una acción por factura, la
 * tarjeta de avisos compacta, el widget con barras, la curva y el signo de las deudas.
 */
class Vuelta80Test {

    private fun evento(descripcion: String, comercio: String? = null, categoria: String = "Mercado") = FinancialEvent(
        id = "e", accountId = "a", type = TransactionType.EXPENSE, amount = 1_000, category = categoria,
        description = descripcion, merchant = comercio, timestamp = 0L,
    )

    // ── (1) Un solo ícono por movimiento ────────────────────────────────────

    @Test
    fun `la marca del comercio gana y si no la categoria`() {
        assertEquals("marca:uber", iconoDelMovimiento(evento("Uber Rides", "UBER *TRIP", "Transporte")).clave)
        assertEquals("categoria:Comida", iconoDelMovimiento(evento("Almuerzo con la hija", categoria = "Comida")).clave)
    }

    @Test
    fun `un banco no es un comercio, la cuota lleva su categoria`() {
        // La «O» azul de Occidente en el detalle de la cuenta: ahora la categoría, como en el Inicio.
        val cuota = evento("Cuota de Vehículo 4083", "Banco de Occidente S A", "Cuota de crédito")
        assertNull(marcaDelComercio(cuota.merchant, cuota.description))
        assertEquals(IconoResuelto.DeCategoria("Cuota de crédito"), iconoDelMovimiento(cuota))
        // «Pago desde Bancolombia» tampoco lleva la «B».
        assertEquals("categoria:Traspaso", iconoDelMovimiento(evento("Pago desde Bancolombia Ahorros", categoria = "Traspaso")).clave)
    }

    @Test
    fun `con un banco en el comercio, la descripcion todavia puede nombrar el comercio`() {
        assertEquals("marca:d1", iconoDelMovimiento(evento("Tienda D1 Laureles", "Bancolombia")).clave)
    }

    // ── (2) Un solo avatar del dueño ─────────────────────────────────────────

    @Test
    fun `las iniciales del dueno son las de siempre`() {
        assertEquals("DM", inicialesDelDueno("Demo Movi"))
        assertEquals("J", inicialesDelDueno("Juan"))
        assertEquals("?", inicialesDelDueno(null))
        assertEquals(inicialesDelDueno("Juan Villada"), com.jvillada.movi.ui.components.inicialesDelUsuario("Juan Villada"))
    }

    // ── (3) El widget «En qué se va» ─────────────────────────────────────────

    @Test
    fun `la barra de cada categoria es relativa a la mayor`() {
        assertEquals(1f, fraccionDeLaCategoriaEnElWidget(1_800_000, 1_800_000))
        assertEquals(0.5f, fraccionDeLaCategoriaEnElWidget(900_000, 1_800_000))
        assertEquals(0f, fraccionDeLaCategoriaEnElWidget(0, 0))
        assertEquals(TonoDePildora.Peligro, tonoDeLaPildoraDeLaDiferencia(TonoDeCifra.Alerta))
        assertEquals(TonoDePildora.Neutra, tonoDeLaPildoraDeLaDiferencia(TonoDeCifra.Neutro))
        assertEquals(TonoDePildora.Neutra, tonoDeLaPildoraDeLaDiferencia(TonoDeCifra.Apagado))
    }

    // ── (4) Una acción por factura ───────────────────────────────────────────

    @Test
    fun `la factura lleva el boton o el menu, nunca los dos`() {
        assertEquals(AccionDeLaFila.BOTON, accionDeLaFilaPendiente(conAnotar = true, conPropuesta = false, conMenu = true))
        assertEquals(AccionDeLaFila.MENU, accionDeLaFilaPendiente(conAnotar = true, conPropuesta = true, conMenu = true))
        assertEquals(AccionDeLaFila.MENU, accionDeLaFilaPendiente(conAnotar = false, conPropuesta = false, conMenu = true))
        assertEquals(AccionDeLaFila.NADA, accionDeLaFilaPendiente(conAnotar = false, conPropuesta = false, conMenu = false))
    }

    // ── (6) La tarjeta de avisos ─────────────────────────────────────────────

    @Test
    fun `confirmar en un boton chico, con cuantos`() {
        val nada = AvisosPorConfirmar(cuantos = 5, suman = null, comercios = null, confirmables = emptyList())
        assertNull(botonCompactoDeConfirmar(nada))
    }

    // ── (7) Patrimonio ───────────────────────────────────────────────────────

    @Test
    fun `con menos de cuatro puntos la curva va sin relleno`() {
        assertFalse(laCurvaLlevaRelleno(2))
        assertFalse(laCurvaLlevaRelleno(3))
        assertTrue(laCurvaLlevaRelleno(4))
        assertTrue(laCurvaLlevaRelleno(13))
    }

    @Test
    fun `la fila de las deudas lleva el signo del resumen`() {
        assertEquals(-1_583_000L, cifraDeLasDeudasEnLaFila(1_583_000L))
    }

    // ── (8) El préstamo sin saldo, una sola pieza ────────────────────────────

    private val vehiculo = Account(id = "veh", name = "Vehículo 4083", type = AccountType.LOAN, balance = -980_000)
    private val nu = Account(id = "nu", name = "Nu", type = AccountType.CREDIT_CARD, balance = -5_000)

    @Test
    fun `con el plan completo, usar este con la cifra del plan`() {
        val plan = ElSaldoDelPlanEnLaFicha(saldo = 35_830_084, ultimaCuota = null)
        val dice = loQueSeDiceSinSaldo(listOf(vehiculo), setOf(vehiculo.id), mapOf(vehiculo.id to plan))!!
        assertEquals("Falta el saldo de Vehículo 4083", dice.titulo)
        assertEquals(AccionSinSaldo.UsarElDelPlan(vehiculo.id, 35_830_084), dice.accion)
        assertEquals(USAR_EL_SALDO_DEL_PLAN, dice.accion.texto)
        assertTrue(dice.conPlan)
    }

    @Test
    fun `sin plan cargarlo, sin condiciones cuadrar, y varias cuadrar sin una`() {
        assertEquals(CARGAR_EL_SALDO_DEL_CREDITO, loQueSeDiceSinSaldo(listOf(vehiculo), setOf(vehiculo.id), emptyMap())!!.accion.texto)
        val sinCondiciones = loQueSeDiceSinSaldo(listOf(vehiculo), emptySet(), emptyMap())!!
        assertEquals(AccionSinSaldo.Cuadrar(vehiculo.id), sinCondiciones.accion)
        assertEquals(CUADRAR_LAS_DEUDAS_SIN_SALDO, sinCondiciones.accion.texto)
        val varias = loQueSeDiceSinSaldo(listOf(vehiculo, nu), setOf(vehiculo.id), emptyMap())!!
        assertEquals("2 deudas sin saldo", varias.titulo)
        assertEquals(AccionSinSaldo.Cuadrar(null), varias.accion)
        assertNull(loQueSeDiceSinSaldo(emptyList(), emptySet(), emptyMap()))
    }

    @Test
    fun `la hoja explica lo que Movi tenia a favor`() {
        // El campo dice $35.830.084 y el préstamo estaba en −$980.000: el cargo es $36.810.084.
        assertEquals(
            "Se registrará un cargo de \$36.810.084 (incluye los \$980.000 que Movi tenía a favor).",
            loQueSeRegistraAlAjustar(delta = 36_810_084, deudaEnMovi = -980_000),
        )
        assertEquals("Se registrará un cargo de \$10.000 para subir la deuda.", loQueSeRegistraAlAjustar(10_000, 50_000))
        assertEquals("Se registrará un abono de \$10.000 para bajar la deuda.", loQueSeRegistraAlAjustar(-10_000, 50_000))
    }
}
