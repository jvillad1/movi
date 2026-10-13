package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # El motivo de cada envío a una persona
 *
 * A Caro se le gira por tres motivos, y lo que los distingue es el monto. Movi propone el motivo
 * (la categoría) del último envío a ella por el mismo monto exacto.
 */
class MotivoDelEnvioTest {

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "31973270756")
    private val numero = "*31973270756"

    private fun envio(id: String, monto: Long, categoria: String, cuando: Long, tipo: TransactionType = TransactionType.EXPENSE) =
        FinancialEvent(
            id = id, accountId = "acc", type = tipo, amount = monto, category = categoria,
            // Renombrado por el dueño: el número sobrevive en el texto del banco.
            description = categoria, rawPayload = "Bancolombia: Transferiste \$$monto desde tu cuenta *8133 a la cuenta $numero",
            timestamp = cuando,
        )

    private val historia = listOf(
        envio("mercado", 2_000_000L, "Mercado", 1_000L),
        envio("cuota-vieja", 1_300_000L, "Mercado", 2_000L),
        envio("cuota", 1_300_000L, "Cuota de crédito", 3_000L),
        envio("tia", 500_000L, "Tía", 4_000L),
        envio("traspaso", 700_000L, TRANSFER_CATEGORY, 5_000L),
        envio("de-ella", 900_000L, "Ingreso", 6_000L, tipo = TransactionType.INCOME),
    )

    @Test
    fun `el ultimo envio por el mismo monto exacto dice el motivo`() {
        assertEquals("Cuota de crédito", motivoDelEnvio(1_300_000L, "COP", caro, historia)?.categoria)
        assertEquals("Mercado", motivoDelEnvio(2_000_000L, "COP", caro, historia)?.categoria)
        assertEquals("Tía", motivoDelEnvio(500_000L, "COP", caro, historia)?.categoria)
        // Otro monto, otra moneda, un traspaso o lo que ella mandó: no hay motivo que aprender.
        assertNull(motivoDelEnvio(1_299_999L, "COP", caro, historia))
        assertNull(motivoDelEnvio(1_300_000L, "USD", caro, historia))
        assertNull(motivoDelEnvio(700_000L, "COP", caro, historia))
        assertNull(motivoDelEnvio(900_000L, "COP", caro, historia))
        // A otra persona, nada.
        assertNull(motivoDelEnvio(1_300_000L, "COP", DestinoConocido(id = "x", nombre = "Mamá", numero = "11112222"), historia))
    }

    @Test
    fun `el aviso a Caro toma el motivo y dice de donde salio`() {
        val texto = "Bancolombia: Transferiste \$1.300.000 desde tu cuenta *8133 a la cuenta $numero el 10/10/2026."
        val leido = ParsedSms(1_300_000.0, "Transferencia a Caro", TransactionType.EXPENSE, "Mercado", aprendidoDe = "Así lo anotaste 2 veces")
        val conMotivo = conElMotivoDelEnvio(leido, "Otros", texto, listOf(caro), historia)
        assertEquals("Cuota de crédito", conMotivo.category)
        assertEquals("La última vez que le mandaste \$1.300.000 a Caro fue Cuota de crédito", conMotivo.aprendidoDe)
        // Lo que dice el aviso manda: un pago de tarjeta no se vuelve «Cuota» por el monto.
        assertEquals(leido, conElMotivoDelEnvio(leido, CARD_PAYMENT_CATEGORY, texto, listOf(caro), historia))
        // Sin un envío igual, queda lo de la memoria.
        assertEquals(leido.copy(amount = 1_234_000.0), conElMotivoDelEnvio(leido.copy(amount = 1_234_000.0), "Otros", texto, listOf(caro), historia))
    }

    @Test
    fun `lo enviado por motivo, del mas grande al mas chico`() {
        val porMotivo = enviadoPorMotivo(historia.filter { it.type == TransactionType.EXPENSE })
        assertEquals(
            listOf(
                EnvioPorMotivo("Mercado", 3_300_000L, "COP", 2),
                EnvioPorMotivo("Cuota de crédito", 1_300_000L, "COP", 1),
                EnvioPorMotivo(SIN_MOTIVO, 700_000L, "COP", 1),
                EnvioPorMotivo("Tía", 500_000L, "COP", 1),
            ),
            porMotivo,
        )
    }
}
