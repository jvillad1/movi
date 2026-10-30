package com.jvillada.movi.server.ai

import com.jvillada.movi.server.routes.creditRoutes
import com.jvillada.movi.server.routes.ocurrenciasDelPeriodo
import com.jvillada.movi.server.routes.proximosPagos
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.pagosPendientes
import com.jvillada.movi.shared.model.periodoDe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 77 · un crédito creado hoy con la deuda de hoy no nace con la cuota vencida
 *
 * La revisión fresca 6: un crédito creado con la deuda de hoy y la cuota el 15 decía al instante «Pago
 * vencido · hace 15 días · $770.000» en Inicio y «Te quedan» bajaba $770.000; `/occurrences` lo daba
 * `occurred:false` y `/upcoming` y Créditos decían el 15 del mes siguiente. Ahora el alta pregunta «¿Ya
 * pagaste la cuota de este período?» y, con «Sí», las cuotas corren desde el arranque del período
 * siguiente: ni `/occurrences`, ni la lista de Pagos (de donde salen Inicio y «Te quedan»), ni Movi AI
 * ven la cuota de este período, y `/upcoming` dice la del siguiente.
 *
 * Con el «hoy» real (el alta lo toma de `AppClock`): día de pago 1 y el período = el mes de calendario,
 * así que la cuota de este período ya venció siempre (o vence hoy, el 1).
 */
class ElCreditoNuevoNoNaceVencidoTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val hoy = AppClock.today()
    private val primeroDeEsteMes = hoy.withDayOfMonth(1)
    private val primeroDelSiguiente = primeroDeEsteMes.plusMonths(1)

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("credito_nuevo_no_vencido")
    }

    private fun crear(yaPago: Boolean, dia: Int = 1): CreditSummary {
        var creado: CreditSummary? = null
        testApplication {
            application { with(a) { modulo { creditRoutes() } } }
            val respuesta = client.post("/api/credits") {
                with(a) { como(a.duenoA) }
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateCreditRequest(
                            name = "Libre Inversión R6",
                            initialDebt = 18_500_000L,
                            terms = CreditTerms(
                                accountId = "", bank = "Bancolombia", principal = 25_000_000L, rateEa = 21.0,
                                termMonths = 48, installment = 770_000L, dayOfMonth = dia, startDate = "2025-06-01",
                            ),
                            yaPagoLaCuotaDeEstePeriodo = yaPago,
                        ),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Created, respuesta.status, respuesta.bodyAsText())
            creado = json.decodeFromString<CreditSummary>(respuesta.bodyAsText())
        }
        return assertNotNull(creado)
    }

    private fun checklist() = runBlocking {
        val ajustes = PeriodSettings()
        checklistDelPeriodo(
            upcoming = proximosPagos(a.duenoA, hoy),
            ocurrencias = ocurrenciasDelPeriodo(a.duenoA, hoy),
            periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes),
            settings = ajustes,
        )
    }

    @Test
    fun `con ya pague la cuota de este periodo no existe en ningun lado y la primera es la del siguiente`() {
        val credito = crear(yaPago = true)
        val ruleId = "credit_${credito.account.id}"
        assertEquals(primeroDelSiguiente.toString(), credito.terms?.cuotasDesde, "las cuotas corren desde el período siguiente")

        // /occurrences: nada de este período.
        val ocurrencias = runBlocking { ocurrenciasDelPeriodo(a.duenoA, hoy) }.filter { it.ruleId == ruleId }
        assertTrue(ocurrencias.none { it.dueDate <= hoy.toString() }, "la cuota de este período no existe: $ocurrencias")
        // /upcoming: la del período siguiente.
        val proxima = runBlocking { proximosPagos(a.duenoA, hoy) }.firstOrNull { it.rule.id == ruleId }
        assertEquals(primeroDelSiguiente.toString(), assertNotNull(proxima, "sin próxima cuota").dueDate)
        // La lista de Pagos (Inicio: «Pago vencido», «Te quedan», «Falta por pagar»): nada vencido.
        val lista = checklist()
        assertTrue(lista.none { it.ruleId == ruleId && it.vence <= hoy.toString() }, "nada de este período: $lista")
        assertTrue(pagosPendientes(lista).none { it.ruleId == ruleId && it.vencido })
        // Movi AI («¿Cómo voy?») lee la misma lista: no la resta como fijo.
        val datos = runBlocking { datosDeLasSugeridas(a.duenoA, PreguntaSugerida.COMO_VOY, hoy) }
        assertTrue(datos.pagos.none { it.ruleId == ruleId && it.vence <= hoy.toString() })
    }

    @Test
    fun `sin la respuesta la cuota de este periodo sigue como siempre`() {
        val credito = crear(yaPago = false)
        val ruleId = "credit_${credito.account.id}"
        assertNull(credito.terms?.cuotasDesde)
        val fila = checklist().firstOrNull { it.ruleId == ruleId }
        assertEquals(primeroDeEsteMes.toString(), assertNotNull(fila, "la cuota de este período está").vence)
        assertTrue(!fila.pagado)
    }

    /** Revisión: si la cuota de este período todavía no vence, el server no acepta «ya pagué». */
    @Test
    fun `si la cuota de este periodo no ha vencido el server ignora ya pague`() {
        val dia = hoy.dayOfMonth + 1
        org.junit.Assume.assumeTrue("el último día del mes ya venció todo", dia <= hoy.lengthOfMonth())
        val credito = crear(yaPago = true, dia = dia)
        assertNull(credito.terms?.cuotasDesde, "la cuota del ${hoy.withDayOfMonth(dia)} sigue esperándose")
        val proxima = runBlocking { proximosPagos(a.duenoA, hoy) }.firstOrNull { it.rule.id == "credit_${credito.account.id}" }
        assertEquals(hoy.withDayOfMonth(dia).toString(), assertNotNull(proxima).dueDate)
    }
}
