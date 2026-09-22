package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El gasto variable que mide la tarjeta «Disponible»: lo que cuenta en «Gastos» menos los pagos del
 * checklist, que ya se restaron como fijos. Ver `GastoVariable.kt`.
 */
class GastoVariableTest {

    // El «timestamp» de estas pruebas es la fecha escrita como número (20260921): `diaDe` la
    // devuelve tal cual, así la prueba no depende de ninguna zona horaria.
    private fun evento(
        id: String,
        monto: Long,
        dia: String,
        tipo: TransactionType = TransactionType.EXPENSE,
        categoria: String = "Comida",
        moneda: String = "COP",
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
        esFlujo: Boolean = true,
    ) = FinancialEvent(
        id = id, accountId = "ahorros", type = tipo, amount = monto, currency = moneda,
        category = categoria, description = id, timestamp = dia.replace("-", "").toLong(),
        reconciliationStatus = estado, countsAsCashFlow = esFlujo,
    )

    private val diaDe: (Long) -> String = { t ->
        val s = t.toString()
        "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
    }

    @Test
    fun `suma por dia lo que cuenta en Gastos`() {
        val gasto = gastoVariablePorDia(
            listOf(
                evento("almuerzo", 30_000, "2026-09-21"),
                evento("mercado", 200_000, "2026-09-21"),
                evento("taxi", 15_000, "2026-09-20"),
            ),
            idsSellados = emptySet(),
            diaDe = diaDe,
        )
        assertEquals(mapOf("2026-09-21" to 230_000L, "2026-09-20" to 15_000L), gasto)
    }

    @Test
    fun `un traspaso, lo que espera en Por confirmar, un ingreso y los dolares no son gasto variable`() {
        val gasto = gastoVariablePorDia(
            listOf(
                evento("traspaso", 5_000_000, "2026-09-21", categoria = TRANSFER_CATEGORY, esFlujo = false),
                evento("sin-confirmar", 80_000, "2026-09-21", estado = ReconciliationStatus.UNCONFIRMED),
                evento("sueldo", 11_000_000, "2026-09-21", tipo = TransactionType.INCOME),
                evento("netflix-usd", 20, "2026-09-21", moneda = "USD"),
                evento("cafe", 8_000, "2026-09-21"),
            ),
            idsSellados = emptySet(),
            diaDe = diaDe,
        )
        assertEquals(mapOf("2026-09-21" to 8_000L), gasto)
    }

    /** El arriendo ya se restó como fijo: su pago, atado al sello, no se cuenta otra vez. */
    @Test
    fun `el pago de un fijo no se cuenta dos veces`() {
        val gasto = gastoVariablePorDia(
            listOf(
                evento("arriendo", 1_850_000, "2026-09-05", categoria = "Vivienda"),
                evento("cuota-carro", 900_000, "2026-09-05", categoria = CUOTA_CATEGORY),
                evento("almuerzo", 30_000, "2026-09-05"),
            ),
            idsSellados = setOf("arriendo"),
            diaDe = diaDe,
        )
        assertEquals(mapOf("2026-09-05" to 30_000L), gasto)
    }
}
