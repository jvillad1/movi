package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.MemoriaDelAsistente
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.routes.resumenDeLoQueMoviSabeRoutes
import com.jvillada.movi.server.sms.memoriaDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ResumenDeLoQueMoviSabe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Lo que Movi sabe de ti», de un vistazo (vuelta 95)
 *
 * Los cuatro conteos de `GET /api/asistente/resumen` sobre un fixture: movimientos no anulados y sin el
 * «Saldo inicial», desde cuándo, cuentas, reglas de categoría aprendidas y recuerdos. Cada dueño ve
 * solo lo suyo.
 */
class ResumenDeLoQueMoviSabeTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true }
    private var n = 0

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("resumen_lo_que_movi_sabe")
        transaction {
            VoidEvents.deleteAll(); Events.deleteAll(); MemoriaDelAsistente.deleteAll()
        }
    }

    private fun ApplicationTestBuilder.montar() = with(a) { application { modulo { resumenDeLoQueMoviSabeRoutes() } } }

    private fun anotar(uid: String, cuenta: String, fecha: LocalDate, nombre: String, categoria: String, monto: Long = 20_000): String {
        val ev = "ev_resumen_${n++}"
        transaction {
            Events.insert {
                it[id] = ev; it[userId] = uid; it[accountId] = cuenta; it[type] = "EXPENSE"
                it[amount] = monto; it[currency] = "COP"; it[category] = categoria; it[description] = nombre
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[reconciliationStatus] = "RECONCILED"
            }
        }
        return ev
    }

    private fun recuerdo(uid: String, texto: String) = transaction {
        MemoriaDelAsistente.insert {
            it[id] = "mem_${n++}"; it[userId] = uid; it[MemoriaDelAsistente.texto] = texto
            it[origen] = "A_MANO"; it[creadoEn] = 1L
        }
    }

    private suspend fun ApplicationTestBuilder.leer(uid: String): ResumenDeLoQueMoviSabe {
        val res = client.get("/api/asistente/resumen") { with(a) { como(uid) } }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(ResumenDeLoQueMoviSabe.serializer(), res.bodyAsText())
    }

    @Test
    fun `cuenta movimientos, desde cuando, cuentas, reglas y recuerdos`() = testApplication {
        montar()
        // El saldo inicial no es algo que el dueño anotó: no cuenta ni fija el «desde».
        anotar(a.duenoA, a.cuentaDeA, LocalDate.of(2026, 7, 1), "Saldo inicial", OPENING_CATEGORY, 3_000_000)
        anotar(a.duenoA, a.cuentaDeA, LocalDate.of(2026, 8, 12), "Rappi", "Restaurantes")
        anotar(a.duenoA, a.cuentaDeA, LocalDate.of(2026, 9, 3), "Rappi", "Restaurantes")
        anotar(a.duenoA, a.cuentaDeA, LocalDate.of(2026, 9, 4), "Éxito", "Mercado")
        val anulado = anotar(a.duenoA, a.cuentaDeA, LocalDate.of(2026, 9, 5), "Cobro de más", "Otros")
        transaction {
            VoidEvents.insert {
                it[id] = "void_resumen"; it[userId] = a.duenoA; it[originalEventId] = anulado
                it[reason] = "prueba"; it[timestamp] = 1L
            }
            Accounts.insert { it[id] = "acc-2-a"; it[userId] = a.duenoA; it[name] = "Efectivo"; it[type] = "CASH"; it[currency] = "COP" }
        }
        recuerdo(a.duenoA, "Caro es mi esposa")
        recuerdo(a.duenoA, "El colegio se paga el 25")

        val r = leer(a.duenoA)
        assertEquals(3, r.movimientos, "sin el saldo inicial y sin el anulado")
        assertEquals("2026-08-12", r.desde)
        assertEquals(2, r.cuentas)
        assertEquals(2, r.recuerdos)
        assertTrue(r.reglasAprendidas > 0, "Rappi y Éxito enseñaron algo")
        assertEquals(transaction { memoriaDe(a.duenoA).cuantasHuellas }, r.reglasAprendidas)
    }

    @Test
    fun `cada duenio ve solo lo suyo y sin datos todo es cero`() = testApplication {
        montar()
        anotar(a.duenoA, a.cuentaDeA, LocalDate.of(2026, 8, 12), "Rappi", "Restaurantes")
        recuerdo(a.duenoA, "Caro es mi esposa")

        val delOtro = leer(a.duenoB)
        assertEquals(0, delOtro.movimientos)
        assertEquals(null, delOtro.desde)
        assertEquals(1, delOtro.cuentas, "solo su cuenta")
        assertEquals(0, delOtro.reglasAprendidas)
        assertEquals(0, delOtro.recuerdos)
        assertEquals(1, leer(a.duenoA).recuerdos)
    }
}
