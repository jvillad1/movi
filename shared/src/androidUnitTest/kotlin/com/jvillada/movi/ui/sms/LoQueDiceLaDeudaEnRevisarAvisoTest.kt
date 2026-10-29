package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.transactions.ayudaDelSelectorDeDeuda
import com.jvillada.movi.ui.transactions.deudasConLasDeLaCategoriaPrimero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * # Lo que dicen el selector de deuda, el pie y el resumen (vuelta 58)
 *
 * Revisión de la vuelta 57: «Es opcional: si no eliges ninguna…» salía debajo de «¿De cuál crédito?» con
 * Confirmar apagado; «Pago de tarjeta» sin tarjeta no se entendía; y la tarjeta resumen decía «Otros» donde
 * la fila de categoría decía «Elige la categoría». Todo puro.
 */
class LoQueDiceLaDeudaEnRevisarAvisoTest {

    private val vehiculo = Account("v", "Vehículo 4083", AccountType.LOAN, 40_000_000)
    private val nu = Account("nu", "Nu", AccountType.CREDIT_CARD, -300_000)
    private val ahorros = Account("a", "Ahorros", AccountType.SAVINGS, 1_000_000)

    @Test
    fun `el pie del selector concuerda en genero con lo que se ofrece`() {
        assertEquals("Es opcional: si no eliges ningún crédito, el movimiento queda como un gasto suelto.", ayudaDelSelectorDeDeuda(listOf(vehiculo, ahorros)))
        assertEquals("Es opcional: si no eliges ninguna tarjeta, el movimiento queda como un gasto suelto.", ayudaDelSelectorDeDeuda(listOf(nu)))
        assertEquals("Es opcional: si no eliges ninguna deuda, el movimiento queda como un gasto suelto.", ayudaDelSelectorDeDeuda(listOf(vehiculo, nu)))
    }

    @Test
    fun `en Revisar aviso la cuota dice que sin el credito no se confirma, y la tarjeta que es opcional`() {
        assertEquals("Elige el crédito: sin él no se confirma", ayudaDelSelectorEnRevisarAviso(DeudaQuePagaElAviso.CREDITO))
        assertFalse(ayudaDelSelectorEnRevisarAviso(DeudaQuePagaElAviso.CREDITO)!!.contains("opcional"))
        assertEquals(
            "Es opcional: si no eliges ninguna tarjeta, se anota como un pago de tarjeta suelto.",
            ayudaDelSelectorEnRevisarAviso(DeudaQuePagaElAviso.TARJETA),
        )
        assertNull(ayudaDelSelectorEnRevisarAviso(null))
    }

    @Test
    fun `primero las deudas del tipo que pide la categoria, en el mismo orden`() {
        val otra = nu.copy(id = "nu2", name = "AMEX")
        val todas = listOf(vehiculo, nu, otra)
        assertEquals(listOf("nu", "nu2", "v"), deudasConLasDeLaCategoriaPrimero(todas, CARD_PAYMENT_CATEGORY).map { it.id })
        assertEquals(listOf("v", "nu", "nu2"), deudasConLasDeLaCategoriaPrimero(todas, CUOTA_CATEGORY).map { it.id })
        assertEquals(todas, deudasConLasDeLaCategoriaPrimero(todas, "Otros"))
    }

    @Test
    fun `un pago de tarjeta sin tarjeta explica que se anota como pago de tarjeta y no cuenta como gasto`() {
        val gasto = ParsedSms(980_000.0, "Occidente", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY)
        assertEquals(
            "Si la tarjeta no está en Movi, se anota como pago de tarjeta y no cuenta como gasto.",
            avisoDelPagoDeTarjetaSinTarjeta(gasto, CARD_PAYMENT_CATEGORY, propuestas = null),
        )
        // Otra categoría, o un ingreso: nada que explicar.
        assertNull(avisoDelPagoDeTarjetaSinTarjeta(gasto, CUOTA_CATEGORY, propuestas = null))
        assertNull(avisoDelPagoDeTarjetaSinTarjeta(gasto.copy(type = TransactionType.INCOME), CARD_PAYMENT_CATEGORY, propuestas = null))
        assertNull(avisoDelPagoDeTarjetaSinTarjeta(null, CARD_PAYMENT_CATEGORY, propuestas = null))
    }

    @Test
    fun `la tarjeta resumen dice lo mismo que la fila de categoria`() {
        val sinEntender = ParsedSms(980_000.0, "Occidente", TransactionType.EXPENSE, "Otros")
        // Movi no la entendió: la fila dice «Elige la categoría», el resumen «Sin categoría».
        assertEquals(ELIGE_LA_CATEGORIA, categoriaEnReconciliar("Otros", sinEntender, emptyList()).valor)
        assertEquals(SIN_CATEGORIA_EN_EL_RESUMEN, categoriaEnElResumen("Otros", sinEntender, emptyList()))
        assertEquals("Sin categoría", categoriaEnElResumen("", sinEntender, emptyList()))
        assertEquals("Sin categoría", categoriaEnElResumen(null, null, emptyList()))
        // Lo que él eligió a mano, aunque sea «Otros», se dice tal cual (la fila también).
        val aMano = sinEntender.copy(category = "Mercado")
        assertEquals("Otros", categoriaEnElResumen("Otros", aMano, emptyList()))
        // Una categoría entendida no cambia.
        assertEquals("Mercado", categoriaEnElResumen("Mercado", aMano, listOf("Mercado")))
    }
}
