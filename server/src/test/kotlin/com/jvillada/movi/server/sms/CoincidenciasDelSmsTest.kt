package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.coincidenciasDelSms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Confirmar un SMS siempre creaba un movimiento nuevo, aunque ya estuviera anotado a mano. Esto es
 * lo que decide qué se le ofrece como «¿es este?»: mismo monto, moneda y tipo, a pocos días.
 */
class CoincidenciasDelSmsTest {

    private val dia = 86_400_000L
    private val momento = 1_789_000_000_000L
    private val sms = ParsedSms(18_500.0, "Pago QR", TransactionType.EXPENSE, "Otro")

    private fun ev(id: String, monto: Long, cuando: Long, tipo: TransactionType = TransactionType.EXPENSE, moneda: String = "COP") =
        FinancialEvent(id = id, accountId = "a", type = tipo, amount = monto, category = "Comida", description = id, timestamp = cuando, currency = moneda)

    @Test
    fun `propone lo anotado con el mismo monto, del mas cercano al mas lejano`() {
        val eventos = listOf(
            ev("dos-dias-despues", 18_500, momento + 2 * dia),
            ev("mismo-dia", 18_500, momento + 3_600_000),
            ev("otro-monto", 18_000, momento),
            ev("ingreso", 18_500, momento, tipo = TransactionType.INCOME),
            ev("en-dolares", 18_500, momento, moneda = "USD"),
            ev("una-semana-despues", 18_500, momento + 7 * dia),
        )
        assertEquals(listOf("mismo-dia", "dos-dias-despues"), coincidenciasDelSms(sms, momento, eventos).map { it.id })
    }

    @Test
    fun `los centavos del SMS se redondean como se guarda el movimiento`() {
        val conCentavos = sms.copy(amount = 115_113.07)
        assertEquals(listOf("nu"), coincidenciasDelSms(conCentavos, momento, listOf(ev("nu", 115_113, momento))).map { it.id })
    }
}
