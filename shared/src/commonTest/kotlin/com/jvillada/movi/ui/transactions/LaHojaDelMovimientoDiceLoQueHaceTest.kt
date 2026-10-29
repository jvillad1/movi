package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParteElegida
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.CuadreDeLasPartes
import com.jvillada.movi.ui.sms.REPARTIR_EN_PARTES
import com.jvillada.movi.ui.sms.UNA_SOLA_PARTE
import com.jvillada.movi.ui.sms.loQueLeFaltaALaParte
import com.jvillada.movi.ui.sms.parteNuevaSinCategoria
import com.jvillada.movi.ui.sms.razonDeLasPartes
import com.jvillada.movi.ui.sms.textoDelCuadre
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 67 · la hoja del movimiento dice lo que hace
 *
 * La tercera revisión: «Nota» abría monto, cuenta y concepto; «Repartir en partes» se confundía con
 * «Repartir» con personas; «Es la cuota de un crédito» se ofrecía en un gasto compartido; la parte 2
 * copiaba la categoría de la 1; y un movimiento anotado a mano decía «Cuadra con el aviso».
 */
class LaHojaDelMovimientoDiceLoQueHaceTest {

    private val cuentas = listOf(
        Account("ahorros", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 1_000_000L),
        Account("carro", "Vehículo 4083", AccountType.LOAN, 30_000_000L),
    )
    private val gasto = FinancialEvent(
        id = "e", accountId = "ahorros", type = TransactionType.EXPENSE, amount = 100_000, category = "Mercado",
        description = "Olimpica", timestamp = 0L,
    )

    @Test
    fun `las acciones dicen Editar y Dividir en categorias`() {
        assertEquals("Editar", AccionDelMovimiento.Nota.rotulo)
        assertEquals("Dividir en categorías", REPARTIR_EN_PARTES)
    }

    @Test
    fun `la cuota de un credito no se ofrece en un gasto compartido`() {
        assertTrue(puedeMarcarseCuotaDeCredito(gasto, cuentas))
        assertFalse(puedeMarcarseCuotaDeCredito(gasto.copy(espacioId = "esp_casa", parteAjena = 50_000), cuentas))
        assertFalse(puedeMarcarseCuotaDeCredito(gasto.copy(pagadoPor = "dst_caro"), cuentas), "tu parte de lo que pagó otro tampoco")
    }

    @Test
    fun `la parte nueva empieza sin categoria y lo dice`() {
        val segunda = parteNuevaSinCategoria(40_000)
        assertNull(segunda.categoria)
        assertEquals("Elige la categoría", loQueLeFaltaALaParte(segunda))
        val partes = listOf(ParteElegida(QueFueLaParte.GASTO, categoria = "Mercado", monto = 60_000), segunda)
        assertEquals("Parte 2: elige la categoría", razonDeLasPartes(100_000, partes, "COP"))
        assertEquals(UNA_SOLA_PARTE, razonDeLasPartes(100_000, partes.take(1), "COP"))
    }

    @Test
    fun `sin aviso, cuadrado dice cuanto suma`() {
        assertEquals("Cuadra con el aviso", textoDelCuadre(CuadreDeLasPartes.Cuadra, "COP"))
        assertEquals("Suma \$100.000", textoDelCuadre(CuadreDeLasPartes.Cuadra, "COP", total = 100_000))
        assertEquals("Faltan \$40.000", textoDelCuadre(CuadreDeLasPartes.Faltan(40_000), "COP", total = 100_000))
    }
}
