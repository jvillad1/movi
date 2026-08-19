package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F13: [matchesQuery] es el filtro puro detrás de la búsqueda de Movimientos — sin acentos ni
 * mayúsculas, contra descripción, comercio y categoría.
 */
class TransactionsScreenTest {

    private fun event(
        description: String = "Almuerzo",
        merchant: String? = null,
        category: String = "Comida",
    ) = FinancialEvent(
        id = "1",
        accountId = "a1",
        type = TransactionType.EXPENSE,
        amount = 25_000,
        category = category,
        description = description,
        merchant = merchant,
        timestamp = 0L,
    )

    @Test
    fun `consulta en blanco matchea todo`() {
        assertTrue(matchesQuery(event(), ""))
        assertTrue(matchesQuery(event(), "   "))
    }

    @Test
    fun `matchea por descripcion sin importar tildes`() {
        // "Éxito" con tilde en el evento, "exito" sin tilde en la búsqueda.
        assertTrue(matchesQuery(event(description = "Compra en Éxito"), "exito"))
        assertTrue(matchesQuery(event(description = "Almuerzo frisby"), "Frisby"))
    }

    @Test
    fun `matchea por mayusculas indistintas`() {
        assertTrue(matchesQuery(event(description = "NETFLIX MENSUAL"), "netflix"))
        assertTrue(matchesQuery(event(description = "netflix mensual"), "NETFLIX"))
    }

    @Test
    fun `comercio nulo no rompe la busqueda y simplemente no matchea por ese campo`() {
        val ev = event(description = "Pago manual", merchant = null, category = "Otros")
        assertFalse(matchesQuery(ev, "mercado"))
        // Sigue matcheando por descripción aunque no haya comercio.
        assertTrue(matchesQuery(ev, "manual"))
    }

    @Test
    fun `matchea por comercio cuando existe`() {
        val ev = event(description = "Compra", merchant = "Mercado Fresco")
        assertTrue(matchesQuery(ev, "fresco"))
    }

    @Test
    fun `matchea por categoria`() {
        val ev = event(description = "Colegiatura marzo", category = "Educación")
        assertTrue(matchesQuery(ev, "educacion"))
    }

    @Test
    fun `una consulta sin coincidencia en ningun campo no matchea`() {
        val ev = event(description = "Almuerzo", merchant = "Frisby", category = "Comida")
        assertFalse(matchesQuery(ev, "xyzxyz"))
    }
}
