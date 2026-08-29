package com.jvillada.movi.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El APK 1.9 está instalado en el teléfono del dueño y no se va a actualizar solo.** Lo que este
 * archivo blinda es que el wire del crédito siga sirviendo en las dos direcciones mientras eso sea
 * cierto:
 *
 * - **cliente viejo → server nuevo**: un cuerpo sin `disbursement` sigue siendo un alta válida del
 *   camino de siempre (crédito que ya se venía pagando).
 * - **cliente nuevo → server viejo**: una respuesta sin `disbursement` se sigue deserializando, y
 *   un pedido sin desembolso ni siquiera menciona el campo nuevo.
 *
 * El `Json` de acá abajo es el mismo que configuran los tres motores del cliente
 * (`Platform.android.kt`, `Platform.ios.kt`, `Platform.wasmjs.kt`) y el server
 * (`configureSerialization`): `ignoreUnknownKeys = true`. Esa opción es lo que hace que un campo
 * nuevo en la respuesta no rompa a un cliente que no lo conoce, y por eso se prueba con ella
 * puesta y no con el default.
 */
class CreditWireCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Exactamente lo que manda hoy el APK 1.9 al crear un crédito que ya venía pagando. */
    private val cuerpoDelApk19 = """
        {"name":"Crédito Vehículo Santander","initialDebt":160000000,
         "terms":{"accountId":"","bank":"Santander","principal":160000000,"rateEa":21.56,
                  "termMonths":72,"installment":4550030,"dayOfMonth":25,"startDate":"2025-11-25"}}
    """.trimIndent()

    @Test
    fun `un alta sin desembolso sigue siendo el camino viejo`() {
        val request = json.decodeFromString<CreateCreditRequest>(cuerpoDelApk19)
        assertEquals(160_000_000L, request.initialDebt)
        assertNull(request.disbursement, "sin el campo, el alta no registra ningún desembolso")
    }

    @Test
    fun `un alta sin desembolso no menciona el campo nuevo al serializarse`() {
        val encoded = Json.encodeToString(
            CreateCreditRequest(
                name = "Libranza",
                initialDebt = 257_000_000L,
                terms = terms(),
            ),
        )
        assertFalse("disbursement" in encoded, "un server viejo no tiene por qué ver el campo")
    }

    @Test
    fun `un alta con desembolso viaja con la cuenta y el monto`() {
        val encoded = Json.encodeToString(
            CreateCreditRequest(
                name = "Libranza",
                initialDebt = 0L,
                terms = terms(),
                disbursement = CreditDisbursement(toAccountId = "acc-corriente", amount = 257_000_000L),
            ),
        )
        val round = json.decodeFromString<CreateCreditRequest>(encoded)
        assertEquals("acc-corriente", round.disbursement?.toAccountId)
        assertEquals(257_000_000L, round.disbursement?.amount)
        assertEquals(0L, round.initialDebt)
    }

    @Test
    fun `una respuesta de un server viejo se deserializa sin el desembolso`() {
        val respuestaVieja = """
            {"account":{"id":"acc-1","name":"Libranza","type":"LOAN","balance":257000000,"currency":"COP"},
             "terms":null,"paidPct":null}
        """.trimIndent()
        val summary = json.decodeFromString<CreditSummary>(respuestaVieja)
        assertNull(summary.disbursement)
        // Y los defaults conservadores que ya existían siguen valiendo lo mismo que antes.
        assertTrue(summary.hasMovements)
        assertNull(summary.adjustmentEvent)
    }

    private fun terms() = CreditTerms(
        accountId = "",
        bank = "Bancolombia",
        principal = 257_000_000L,
        rateEa = 12.0,
        termMonths = 120,
        installment = 3_500_000L,
        dayOfMonth = 5,
        startDate = "2026-08-28",
    )
}
