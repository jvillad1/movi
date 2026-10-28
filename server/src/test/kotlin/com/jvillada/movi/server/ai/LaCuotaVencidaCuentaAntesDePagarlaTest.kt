package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.routes.debitosAutomaticosRoutes
import com.jvillada.movi.server.routes.debitosPorConfirmar
import com.jvillada.movi.server.routes.ocurrenciasDelPeriodo
import com.jvillada.movi.server.routes.pagoDeCuotaRoutes
import com.jvillada.movi.server.routes.proximosPagos
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.EstadoDeLaFila
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.confirmacionDelDebito
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.pagosPendientes
import com.jvillada.movi.shared.model.periodoDe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 49 · la cuota vencida de un crédito cuenta antes de pagarla
 *
 * La forma de la demo de la revisión de la vuelta 48: «Vehículo 4083», cuota de $980.000 el día 5
 * desde enero, sin pagos, hoy 28 de octubre y el período = el mes de calendario. Antes de pagarla no
 * salía en «Pagos del período» ni en «Pago vencido» de Inicio, y «Te quedan» no la restaba: al
 * pagarla, la cifra bajaba $980.000 de golpe.
 *
 * **La causa**: pasados los 5 días de gracia, el vencimiento vigente de `/api/payments/upcoming`
 * rueda al 5 de noviembre (fuera del período), y `/api/payments/occurrences` solo emitía la cuota de
 * un crédito cuando ya estaba PAGADA. Entre el 11 y el último día del período la cuota vencida no
 * estaba en ninguna de las dos respuestas, así que el checklist —y con él los fijos del Disponible,
 * «Falta por pagar» y «Pago vencido»— no la veía. Con o sin débito automático.
 *
 * Todo con las funciones que usan Hoy y Plan (`proximosPagos`, `ocurrenciasDelPeriodo`,
 * `checklistDelPeriodo`, y `datosDeLasSugeridas`, que arma la tarjeta Disponible con la misma
 * `disponibleDelPeriodo`), con un «hoy» fijo.
 */
class LaCuotaVencidaCuentaAntesDePagarlaTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val hoy: LocalDate = LocalDate.of(2026, 10, 28)
    private val ahora: Long = appDateToEpochMillis(hoy) + 12 * 3_600_000L
    private val credito = "acc_carro_demo"
    private val ruleId = "credit_$credito"
    private val cuota = 980_000L

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("cuota_vencida_antes_de_pagarla")
        transaction {
            Accounts.insert {
                it[id] = credito; it[userId] = a.duenoA; it[name] = "Vehículo 4083"; it[type] = "LOAN"; it[currency] = "COP"
            }
        }
        // Tu plata al empezar el período: llegó con plata hace rato.
        anotar("ev_apertura", a.cuentaDeA, LocalDate.of(2026, 8, 1), 20_000_000L, OPENING_CATEGORY, "INCOME")
    }

    private fun anotar(id: String, cuenta: String, fecha: LocalDate, monto: Long, categoria: String, tipo: String) =
        transaction {
            Events.insert {
                it[Events.id] = id; it[userId] = a.duenoA; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
                it[currency] = "COP"; it[category] = categoria; it[description] = id
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
            }
        }

    /** La deuda que Movi conoce del crédito (sin esto, la deuda está en $0, como en la demo). */
    private fun conDeuda() = anotar("ev_deuda", credito, LocalDate.of(2026, 1, 5), 50_000_000L, "Deuda inicial", "EXPENSE")

    /** Los términos de la demo: desde el 5 de enero, cuota el 5, 60 meses. */
    private fun terminos(debitoDesde: String?) = transaction {
        Credits.insert {
            it[accountId] = credito; it[userId] = a.duenoA; it[bank] = "Bancolombia"; it[principal] = 50_000_000L
            it[rateEa] = 18.0; it[termMonths] = 60; it[installment] = cuota; it[dayOfMonth] = 5
            it[startDate] = "2026-01-05"; it[remindMe] = true; it[debitoAutomaticoDesde] = debitoDesde
        }
    }

    private fun checklist(): List<PagoDelPeriodo> = runBlocking {
        val ajustes = PeriodSettings()
        checklistDelPeriodo(
            upcoming = proximosPagos(a.duenoA, hoy),
            ocurrencias = ocurrenciasDelPeriodo(a.duenoA, hoy),
            periodo = periodoDe(ahora, ajustes),
            settings = ajustes,
        )
    }

    private fun laCuota(lista: List<PagoDelPeriodo>): PagoDelPeriodo =
        assertNotNull(lista.firstOrNull { it.ruleId == ruleId }, "la cuota no está en Pagos del período: $lista")

    private fun disponible() = assertNotNull(
        runBlocking { datosDeLasSugeridas(a.duenoA, PreguntaSugerida.COMO_VOY, hoy, ahora) }.disponible,
        "sin Disponible",
    )

    private fun casoDeLaCuotaVencida(debitoDesde: String?) {
        terminos(debitoDesde)
        val fila = laCuota(checklist())
        assertEquals(cuota, fila.monto)
        assertTrue(!fila.pagado, "no está pagada")
        assertTrue(fila.vencido, "venció el 5: $fila")
        assertEquals("2026-10-05", fila.vence)
        assertEquals("2026-10", fila.periodoDelSello)
        assertEquals(EstadoDeLaFila.SIN_MOVIMIENTO, fila.estado)
        // El débito automático solo cambia lo que dice la fila, no si cuenta.
        assertEquals(debitoDesde != null, fila.seCobraSola)
        // «Pago vencido» de Inicio sale de los pendientes del checklist.
        assertTrue(pagosPendientes(checklist()).any { it.ruleId == ruleId && it.vencido })
        assertEquals(cuota, faltaPorPagar(checklist()))
        // El Disponible la resta como fijo pendiente.
        assertEquals(cuota, disponible().fijos)
    }

    @Test
    fun `con debito automatico la cuota vencida esta en Pagos, en Pago vencido y restada del Disponible`() =
        casoDeLaCuotaVencida(debitoDesde = a.cuentaDeA)

    @Test
    fun `sin debito automatico pasa lo mismo`() = casoDeLaCuotaVencida(debitoDesde = null)

    @Test
    fun `con la deuda en cero en Movi, como en la demo, la cuota igual cuenta`() {
        // Sin la deuda, «Lo que el banco cobró solo» no la propone (un crédito pagado no se cobra),
        // pero la cuota pactada sigue esperándose: la fila y el fijo no dependen del saldo.
        terminos(a.cuentaDeA)
        assertTrue(runBlocking { debitosPorConfirmar(a.duenoA, hoy, ahora) }.isEmpty())
        assertEquals(cuota, disponible().fijos)
        assertTrue(!laCuota(checklist()).pagado)
    }

    @Test
    fun `antes del vencimiento la fila ya estaba y no pregunta nada`() {
        terminos(a.cuentaDeA)
        val antes = runBlocking {
            val dia = LocalDate.of(2026, 10, 3)
            val ajustes = PeriodSettings()
            checklistDelPeriodo(
                proximosPagos(a.duenoA, dia),
                ocurrenciasDelPeriodo(a.duenoA, dia),
                periodoDe(appDateToEpochMillis(dia) + 12 * 3_600_000L, ajustes),
                ajustes,
            )
        }
        val fila = laCuota(antes)
        assertEquals(EstadoDeLaFila.AUN_NO_VENCE, fila.estado)
        assertTrue(fila.seCobraSola)
    }

    // ── Cada peso, una vez ──────────────────────────────────────────────────────

    @Test
    fun `Si se cobro salda la fila y Te quedan no cambia, y la propuesta no se cuenta dos veces`() = testApplication {
        with(a) { application { modulo { debitosAutomaticosRoutes() } } }
        conDeuda()
        terminos(a.cuentaDeA)

        // Pendiente: la fila en Pagos y la propuesta en «Por revisar» son LA MISMA cuota. El fijo la
        // resta una vez; la propuesta no es un movimiento y no resta nada.
        val propuesta = runBlocking { debitosPorConfirmar(a.duenoA, hoy, ahora) }.single()
        assertEquals(ruleId, propuesta.ruleId)
        assertEquals("2026-10", propuesta.periodo)
        val antes = disponible()
        assertEquals(cuota, antes.fijos)
        assertEquals(0L, antes.plata?.otrosPagosDeDeuda)

        val r = client.post("/api/debitos-automaticos/confirmar") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ConfirmarDebitoAutomatico.serializer(), confirmacionDelDebito(propuesta, cuota)))
        }
        assertEquals(HttpStatusCode.Created, r.status)

        val fila = laCuota(checklist())
        assertTrue(fila.pagado, "«Sí, se cobró» salda la cuota del período: $fila")
        assertEquals(0L, faltaPorPagar(checklist()))
        assertTrue(runBlocking { debitosPorConfirmar(a.duenoA, hoy, ahora) }.isEmpty(), "la propuesta se va")
        val despues = disponible()
        assertEquals(cuota, despues.fijos, "el fijo pasa de pendiente a pagado, por el mismo monto")
        assertEquals(0L, despues.plata?.otrosPagosDeDeuda, "la cuota no se resta además como otro pago de deuda")
        assertEquals(antes.disponible, despues.disponible)
        assertEquals(antes.periodo.teQuedan, despues.periodo.teQuedan)
    }

    @Test
    fun `pagarla desde la hoja de Cuota tampoco mueve Te quedan`() = testApplication {
        with(a) { application { modulo { pagoDeCuotaRoutes() } } }
        conDeuda()
        terminos(debitoDesde = null)
        val antes = disponible()
        assertEquals(cuota, antes.fijos)

        val r = pagarLaCuota()
        assertEquals(HttpStatusCode.Created, r.status)

        assertTrue(laCuota(checklist()).pagado)
        val despues = disponible()
        assertEquals(cuota, despues.fijos)
        assertEquals(0L, despues.plata?.otrosPagosDeDeuda)
        assertEquals(antes.periodo.teQuedan, despues.periodo.teQuedan)
    }

    private suspend fun ApplicationTestBuilder.pagarLaCuota() = client.post("/api/payments/installment") {
        with(a) { como(a.duenoA) }
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                CreatePagoDeCuotaRequest.serializer(),
                CreatePagoDeCuotaRequest(
                    fromAccountId = a.cuentaDeA,
                    debtAccountId = credito,
                    amount = cuota,
                    timestamp = ahora,
                    transferId = "tr_cuota_oct",
                    fromEventId = "ev_cuota_oct_s",
                    toEventId = "ev_cuota_oct_e",
                ),
            ),
        )
    }
}
