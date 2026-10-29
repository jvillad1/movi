package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import com.jvillada.movi.shared.time.AppTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 51, lo puro de `:core`
 *
 * - Las categorías que solo escribe Movi: una sola lista ([CATEGORIAS_QUE_NO_SE_ELIGEN]).
 * - Los avisos de más de 30 días se apartan ([esPagoAntiguo]).
 * - Un candidato de pago muy distinto del monto no va de principal ([candidatoMuyDistinto]).
 */
class Vuelta51LogicaTest {

    // ── 2 · Las categorías que no se eligen ──────────────────────────────────────

    @Test
    fun `las cuatro que nombra la revision y las reservadas de siempre no se eligen`() {
        listOf(CUOTA_CATEGORY, DESEMBOLSO_CATEGORY, TRANSFER_CATEGORY, CARD_PAYMENT_CATEGORY, AVANCE_DE_TARJETA_CATEGORY)
            .forEach { assertTrue(esCategoriaQueNoSeElige(it), it) }
        RESERVED_CATEGORIES.forEach { assertTrue(esCategoriaQueNoSeElige(it), it) }
        // Sin mayúsculas ni tildes, como `isReservedCategory`.
        assertTrue(esCategoriaQueNoSeElige("cuota de credito"))
        assertTrue(esCategoriaQueNoSeElige("  PAGO DE TARJETA "))
    }

    @Test
    fun `una categoria del duenno si se elige`() {
        listOf("Comida", "Transporte", "Crédito", "Cuotas del colegio", "Otros").forEach {
            assertFalse(esCategoriaQueNoSeElige(it), it)
        }
    }

    @Test
    fun `la cuota y el desembolso siguen sin ser reservadas`() {
        // No se eligen, pero cuentan en el mes como cualquier categoría: la lista no las vuelve reservadas.
        assertFalse(isReservedCategory(CUOTA_CATEGORY))
        assertFalse(isReservedCategory(DESEMBOLSO_CATEGORY))
    }

    // ── 3 · Los avisos antiguos ────────────────────────────────────────────────

    private val ahora = LocalDateTime.parse("2026-10-28T12:00:00").toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun aviso(id: String, time: String, grupo: String? = null, state: String = SMS_STATE_PENDING) =
        SmsMessage(id = id, time = time, bank = "85540", text = "Compraste", state = state, det = "", grupoId = grupo)

    @Test
    fun `la frontera es de 30 dias contados desde la fecha del aviso`() {
        val hace29 = aviso("a", "2026-09-29 12:00")
        val justo30 = aviso("b", "2026-09-28 12:00")
        val hace31 = aviso("c", "2026-09-27 11:59")
        val rappi = aviso("rappi", "2026-09-02 19:40")
        assertFalse(esPagoAntiguo(hace29, emptyList(), ahora))
        assertFalse(esPagoAntiguo(justo30, emptyList(), ahora), "30 días exactos todavía no es «más de 30»")
        assertTrue(esPagoAntiguo(hace31, emptyList(), ahora))
        assertTrue(esPagoAntiguo(rappi, emptyList(), ahora), "el Rappi del 2 de septiembre")
    }

    @Test
    fun `una fecha que no se lee nunca se aparta`() {
        assertFalse(esPagoAntiguo(aviso("x", "ayer por la tarde"), emptyList(), ahora))
    }

    @Test
    fun `un pago avisado varias veces vale por su aviso mas reciente`() {
        val viejo = aviso("sms", "2026-09-01 10:00", grupo = "g1")
        val nuevo = aviso("noti", "2026-10-20 10:00", grupo = "g1")
        val bandeja = listOf(viejo, nuevo)
        assertFalse(esPagoAntiguo(viejo, bandeja, ahora))
    }

    @Test
    fun `separar no reordena y cuenta uno por pago`() {
        val bandeja = listOf(
            aviso("hoy", "2026-10-28 09:00"),
            aviso("rappi", "2026-09-02 19:40"),
            aviso("pana", "2026-09-10 14:05"),
            aviso("ignorado", "2026-08-01 10:00", state = SMS_STATE_IGNORED),
        )
        val partidos = separarLosPagosAntiguos(unoPorPago(bandeja), bandeja, ahora)
        assertEquals(listOf("hoy"), partidos.recientes.map { it.id })
        assertEquals(listOf("rappi", "pana"), partidos.antiguos.map { it.id })
        assertEquals(listOf("hoy"), pagosRecientesPorConfirmar(bandeja, ahora).map { it.id })
        assertEquals(2, cuantosPagosAntiguos(bandeja, ahora))
    }

    // ── 4 · El candidato que no se parece ────────────────────────────────────────

    private fun movimiento(monto: Long, moneda: String = "COP") = FinancialEvent(
        id = "ev-$monto", accountId = "acc", type = TransactionType.EXPENSE, amount = monto, currency = moneda,
        category = "Transporte", description = "", timestamp = ahora,
    )

    @Test
    fun `un tanqueo de 260 mil para una factura de 9 mil no se propone`() {
        assertTrue(candidatoMuyDistinto(9_000L, montoEsSaldo = false, monedaEsperada = "COP", candidato = movimiento(260_000L)))
    }

    @Test
    fun `la frontera es la mitad del monto, hacia los dos lados`() {
        assertFalse(candidatoMuyDistinto(100_000L, false, "COP", movimiento(150_000L)), "50 % justo todavía se propone")
        assertTrue(candidatoMuyDistinto(100_000L, false, "COP", movimiento(150_001L)))
        assertFalse(candidatoMuyDistinto(100_000L, false, "COP", movimiento(50_000L)))
        assertTrue(candidatoMuyDistinto(100_000L, false, "COP", movimiento(49_999L)))
        // Un salario que varía con las retenciones sigue proponiéndose.
        assertFalse(candidatoMuyDistinto(4_000_000L, false, "COP", movimiento(3_720_000L)))
    }

    @Test
    fun `sin un esperado comparable no se afirma nada`() {
        assertFalse(candidatoMuyDistinto(27_501_150L, montoEsSaldo = true, monedaEsperada = "COP", candidato = movimiento(900_000L)), "tarjeta: el monto es el saldo")
        assertFalse(candidatoMuyDistinto(80_000L, false, "COP", movimiento(20L, "USD")), "otra moneda")
        assertFalse(candidatoMuyDistinto(0L, false, "COP", movimiento(260_000L)), "regla sin monto")
    }

    @Test
    fun `la fila solo propone los parecidos y nunca se tilda sola`() {
        val carro = PagoDelPeriodo(
            ruleId = "r-carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -3,
            periodoDelSello = "2026-10", candidatos = listOf(movimiento(260_000L), movimiento(9_500L)),
        )
        assertEquals(listOf(9_500L), carro.candidatosParecidos.map { it.amount })
        val soloLejos = carro.copy(candidatos = listOf(movimiento(260_000L)))
        assertTrue(soloLejos.candidatosParecidos.isEmpty())
        // Lo que dice el estado no cambia: sigue con dudas, nada se da por pagado.
        assertEquals(EstadoDeLaFila.CON_DUDAS, soloLejos.estado)
        assertFalse(soloLejos.pagado)
    }
}
