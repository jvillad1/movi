package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vuelta 43: la última cuenta de cada categoría y de cada nombre (ver `CuentasDeCadaUso.kt`). */
class CuentasDeCadaUsoTest {

    private fun mov(
        id: String,
        cuenta: String,
        categoria: String,
        nombre: String,
        cuando: Long,
        tipo: TransactionType = TransactionType.EXPENSE,
        transferId: String? = null,
        merchant: String? = null,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = 10_000, category = categoria,
        description = nombre, merchant = merchant, timestamp = cuando, transferId = transferId,
    )

    @Test
    fun `gana la cuenta del movimiento mas reciente`() {
        val usos = cuentasDeCadaUso(
            listOf(
                mov("2", "bancolombia", "Mercado", "Éxito", cuando = 200),
                mov("1", "nu", "Mercado", "Éxito", cuando = 100),
            ),
        )
        assertEquals("bancolombia", usos.porCategoria[claveDeCategoriaParaCuenta(TransactionType.EXPENSE, "Mercado")])
        assertEquals("bancolombia", usos.porNombre[claveDeNombreParaCuenta(TransactionType.EXPENSE, "Éxito")])
    }

    @Test
    fun `el texto del banco manda sobre la nota para el nombre`() {
        val usos = cuentasDeCadaUso(listOf(mov("1", "nu", "Comida", "Almuerzo", 1, merchant = "RAPPI COLOMBIA")))
        assertEquals("nu", usos.porNombre[claveDeNombreParaCuenta(TransactionType.EXPENSE, "Rappi Colombia")])
    }

    @Test
    fun `gasto e ingreso no se mezclan`() {
        val usos = cuentasDeCadaUso(
            listOf(
                mov("1", "nu", "Otros ingresos", "Reintegro", 1),
                mov("2", "bancolombia", "Otros ingresos", "Reintegro", 2, tipo = TransactionType.INCOME),
            ),
        )
        assertEquals("nu", usos.porCategoria[claveDeCategoriaParaCuenta(TransactionType.EXPENSE, "Otros ingresos")])
        assertEquals("bancolombia", usos.porCategoria[claveDeCategoriaParaCuenta(TransactionType.INCOME, "Otros ingresos")])
    }

    @Test
    fun `un traspaso, una reservada y Otros no son costumbre`() {
        val usos = cuentasDeCadaUso(
            listOf(
                mov("1", "nu", "Mercado", "Pata", 1, transferId = "tr"),
                mov("2", "nu", TRANSFER_CATEGORY, "Traspaso", 2),
                mov("3", "nu", "Otros", "", 3),
            ),
        )
        assertNull(usos.porCategoria[claveDeCategoriaParaCuenta(TransactionType.EXPENSE, "Mercado")])
        assertEquals(emptyMap(), usos.porCategoria)
    }
}
