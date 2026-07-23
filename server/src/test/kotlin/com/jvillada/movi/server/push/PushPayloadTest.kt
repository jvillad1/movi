package com.jvillada.movi.server.push

import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushPayloadTest {
    private val today = LocalDate.of(2026, 7, 20)
    private fun rule(name: String, amount: Long, day: Int) =
        RecurringRule(id = "r-$name", name = name, category = "Créditos", amount = amount, dayOfMonth = day, type = TransactionType.EXPENSE)

    private fun body(json: String) = Json.parseToJsonElement(json).jsonObject["body"]!!.jsonPrimitive.content

    @Test
    fun `single payment renders name, amount and status`() {
        val json = buildPushPayload(listOf(rule("Cuota Vehículo", 4_550_030, 22)), today, leadDays = 3)
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("Pagos próximos en movi", obj["title"]!!.jsonPrimitive.content)
        assertEquals("/", obj["url"]!!.jsonPrimitive.content)
        assertEquals("Cuota Vehículo — ${'$'}4.550.030 (vence en 2 días)", body(json))
    }

    @Test
    fun `overdue and due today use the email copy`() {
        val json = buildPushPayload(listOf(rule("Arriendo", 2_500_000, 15), rule("Internet", 90_000, 20)), today, leadDays = 3)
        val lines = body(json).split("\n")
        assertEquals("Arriendo — ${'$'}2.500.000 (vencido hace 5 días)", lines[0])
        assertEquals("Internet — ${'$'}90.000 (vence hoy)", lines[1])
    }

    @Test
    fun `more than three payments collapse into a suffix`() {
        val rules = (1..5).map { rule("Pago $it", 10_000L * it, 20) }
        val lines = body(buildPushPayload(rules, today, leadDays = 3)).split("\n")
        assertEquals(4, lines.size)
        assertEquals("…y 2 más", lines[3])
    }

    @Test
    fun `empty list yields empty body`() {
        assertTrue(body(buildPushPayload(emptyList(), today, leadDays = 3)).isEmpty())
    }

    @Test
    fun `single sms movement renders amount and merchant`() {
        val json = buildSmsPushPayload(listOf(ParsedSms(50_000.0, "EXITO COUNTRY", TransactionType.EXPENSE, "Mercado")))
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("Nuevo movimiento", obj["title"]!!.jsonPrimitive.content)
        assertEquals("$50.000 en EXITO COUNTRY — toca para confirmar", obj["body"]!!.jsonPrimitive.content)
        assertEquals("/", obj["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `multiple sms movements group with suffix`() {
        val parsed = (1..4).map { ParsedSms(10_000.0 * it, "Comercio $it", TransactionType.EXPENSE, "Otros") }
        val obj = Json.parseToJsonElement(buildSmsPushPayload(parsed)).jsonObject
        assertEquals("4 movimientos nuevos", obj["title"]!!.jsonPrimitive.content)
        val lines = obj["body"]!!.jsonPrimitive.content.split("\n")
        assertEquals(4, lines.size)
        assertEquals("$10.000 en Comercio 1", lines[0])
        assertEquals("…y 1 más", lines[3])
    }
}
