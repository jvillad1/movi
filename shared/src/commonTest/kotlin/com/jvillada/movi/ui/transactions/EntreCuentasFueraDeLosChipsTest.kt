package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Entre cuentas» se muda a Cuentas.**
 *
 * El dueño: *«Entre cuentas creo que no hace falta acá, debería ir en cuentas tal vez no?»*. Y sí:
 * un traspaso, una cuota de crédito o un pago de tarjeta son hechos **entre dos cuentas suyas**,
 * no una forma de mirar sus gastos. La lista sigue siendo la misma —el mismo filtro, la misma
 * pantalla, que es la única que sabe juntar las dos patas en un solo renglón— pero se entra desde
 * donde habla de eso.
 *
 * Con esto la fila de filtros baja de **seis a cuatro**, que era el pedido de fondo: *«ya tenemos
 * muchos filtros o al menos en ese nivel de jerarquía, se ve bastante mal»*.
 */
class EntreCuentasFueraDeLosChipsTest {

    private fun pagoDeTarjeta() = FinancialEvent(
        id = "ev_1",
        accountId = "acc_banco",
        type = TransactionType.EXPENSE,
        amount = 115_113L,
        category = CARD_PAYMENT_CATEGORY,
        description = "Pago de Nu Tarjeta",
        timestamp = 1_757_000_000_000L,
        transferId = "tr_1",
    )

    @Test
    fun `la fila de filtros quedo en cuatro`() {
        assertEquals(listOf(CHIP_TODO, CHIP_GASTOS, CHIP_INGRESOS, CHIP_RECURRENTES), CHIPS_VISIBLES)
        assertFalse(CHIP_ENTRE_CUENTAS in CHIPS_VISIBLES)
        assertFalse(CHIP_POR_CONFIRMAR in CHIPS_VISIBLES)
    }

    @Test
    fun `el filtro sigue existiendo y sigue valiendo 4`() {
        // Mismo motivo que con «Por confirmar»: el número viaja adentro de `Screen.Transactions` y
        // puede volver desde una pila de navegación restaurada. Renumerar cambiaría en silencio
        // qué significa un índice viejo.
        assertEquals(4, CHIP_ENTRE_CUENTAS)
        assertTrue(matchesChip(pagoDeTarjeta(), CHIP_ENTRE_CUENTAS))
    }

    @Test
    fun `adentro, el encabezado dice donde esta uno`() {
        // Sin chip marcado, la lista se vería filtrada sin nada que explicara por qué ni cómo
        // volver. Los dos filtros sin chip necesitan lo mismo.
        assertEquals("Entre cuentas", tituloDelModoSinChip(CHIP_ENTRE_CUENTAS))
        assertEquals("Por confirmar", tituloDelModoSinChip(CHIP_POR_CONFIRMAR))
        // Y los que sí tienen chip no lo llevan: ahí el chip marcado ya lo dice.
        assertNull(tituloDelModoSinChip(CHIP_TODO))
        assertNull(tituloDelModoSinChip(CHIP_GASTOS))
        assertNull(tituloDelModoSinChip(CHIP_RECURRENTES))
    }
}
