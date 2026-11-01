package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CierresDescartados
import com.jvillada.movi.server.db.CierresEnviados
import com.jvillada.movi.server.db.CierresGuardados
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.htmlDelCierre
import com.jvillada.movi.server.reminders.mandarLosCierres
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.lineaDelCambioDelCierre
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Cerró tu período» en el server
 *
 * El armado y las frases los prueba `:core` (`CierreDelPeriodoTest`). Acá: que cada cifra salga de la
 * función de siempre con los datos del dueño (corte 25), que **el correo salga una sola vez por
 * período**, que el interruptor apagado no mande nada, que sin `RESEND_API_KEY` no se haga nada, que
 * cada dueño reciba solo lo suyo, y que la tarjeta de Hoy se descarte en el server.
 *
 * El correo se manda con un `enviar` falso: ninguna prueba toca la red.
 */
class CierreDelPeriodoTest {

    private val testSecret = "test-secret-for-cierre-del-periodo-min-32"
    private val uid = "user-cierre"
    private val otro = "user-otro-cierre"
    private val json = Json { ignoreUnknownKeys = true }
    private var n = 0

    /** El 25-oct es el día 1 de noviembre (corte 25): octubre (25-sep → 24-oct) acaba de cerrar. */
    private val diaUnoDeNoviembre = LocalDate.of(2026, 10, 25)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cierre_del_periodo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Apartados, Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, KnownDestinations, Budgets, Subscriptions, AnomaliasDescartadas,
                CierresEnviados, CierresDescartados, CierresGuardados,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(uid, otro).forEach { u ->
                Users.insert {
                    it[id] = u; it[email] = "$u@cierre.test"; it[name] = u; it[passwordHash] = "h"; it[periodCutoffDay] = 25
                }
                Accounts.insert { it[id] = "acc_$u"; it[userId] = u; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[balance] = 0L }
            }
        }
    }

    private fun anotar(
        fecha: LocalDate,
        monto: Long,
        categoria: String,
        descripcion: String = categoria,
        tipo: String = "EXPENSE",
        usuario: String = uid,
    ): String {
        val id = "ev_${n++}"
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = usuario
                it[accountId] = "acc_$usuario"
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = descripcion
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[reconciliationStatus] = "RECONCILED"
            }
        }
        return id
    }

    private fun regla(id: String, nombre: String, dia: Int, monto: Long, categoria: String) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = dia
            it[type] = "EXPENSE"
            it[accountId] = "acc_$uid"
            it[createdAt] = appDateToEpochMillis(LocalDate.of(2026, 8, 1))
        }
    }

    private fun sello(regla: String, periodo: String, evento: String?) = transaction {
        RecurringOccurrences.insert {
            it[userId] = uid
            it[ruleId] = regla
            it[period] = periodo
            it[eventId] = evento
            it[confirmedAt] = 1L
        }
    }

    /** El dueño lleva Movi desde agosto; octubre con sueldo, gastos, un arriendo pagado y un gimnasio sin pagar. */
    private fun octubreDelDueno() {
        anotar(LocalDate.of(2026, 8, 10), 3_000_000, OPENING_CATEGORY, "Saldo inicial", tipo = "INCOME")
        // Septiembre (25-ago → 24-sep): el período anterior, cubierto entero.
        anotar(LocalDate.of(2026, 8, 26), 4_000_000, "Salario", tipo = "INCOME")
        anotar(LocalDate.of(2026, 9, 3), 1_500_000, "Vivienda", "Arriendo septiembre")
        // Octubre (25-sep → 24-oct).
        anotar(LocalDate.of(2026, 9, 26), 5_000_000, "Salario", tipo = "INCOME")
        val arriendo = anotar(LocalDate.of(2026, 10, 3), 1_500_000, "Vivienda", "Arriendo octubre")
        anotar(LocalDate.of(2026, 10, 8), 600_000, "Comida", "Mercado")
        anotar(LocalDate.of(2026, 10, 9), 300_000, "Transporte", "Gasolina")
        anotar(LocalDate.of(2026, 10, 12), 100_000, "Salud", "Droguería")
        // Un cobro duplicado: lo que se salió de lo normal.
        anotar(LocalDate.of(2026, 10, 14), 45_900, "Comida", "RAPPI COLOMBIA")
        anotar(LocalDate.of(2026, 10, 15), 45_900, "Comida", "RAPPI COLOMBIA")
        regla("rr-arriendo", "Arriendo", 3, 1_500_000, "Vivienda")
        regla("rr-gimnasio", "Gimnasio", 10, 120_000, "Deporte")
        sello("rr-arriendo", "2026-10", arriendo)
    }

    @Test
    fun `el resumen sale de las cifras de siempre`() = runBlocking<Unit> {
        octubreDelDueno()
        val c = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))

        assertEquals("2026-10", c.periodo)
        assertEquals("Octubre 2026", c.nombre)
        assertEquals(1, c.diaDelPeriodoNuevo)
        // Las mismas cifras que «Tus períodos» para octubre.
        val tusPeriodos = transaction {
            resumenesDePeriodos(uid, appDateToEpochMillis(diaUnoDeNoviembre) + 12 * 3_600_000L, ajustes25)
        }.first { it.id == "2026-10" }
        assertEquals(tusPeriodos.entradas, c.entro)
        assertEquals(tusPeriodos.salidas, c.salio)
        assertEquals(5_000_000, c.entro)
        assertEquals(2_591_800, c.salio)
        // Contra septiembre, cubierto entero.
        assertEquals("2026-09", c.anterior?.periodo)
        assertEquals(1_500_000, c.anterior?.salio)
        assertEquals(listOf("Vivienda", "Comida", "Transporte"), c.categorias.map { it.category })
        // Pagos: el arriendo sellado, el gimnasio no.
        assertEquals(2, c.pagosEsperados)
        assertEquals(1, c.pagosCumplidos)
        assertEquals(listOf("Gimnasio"), c.pagosQueFaltaron)
        // El Rappi repetido.
        assertTrue(c.fueraDeLoNormal.any { it.contains("duplicado", ignoreCase = true) }, "avisos: ${c.fueraDeLoNormal}")
        assertNotNull(c.quedoDelDisponible)
    }

    /** El cierre es historia: un apartado de hoy no cambia lo que quedó en un período que ya cerró. */
    @Test
    fun `un apartado de hoy no cambia el cierre de un periodo pasado`() = runBlocking<Unit> {
        octubreDelDueno()
        val sin = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        transaction {
            Apartados.insert {
                it[Apartados.id] = "apt-cierre"
                it[Apartados.userId] = uid
                it[accountId] = "acc_$uid"
                it[proposito] = "Colegio"
                it[monto] = 1_000_000L
                it[desde] = 0L
                it[createdAt] = System.currentTimeMillis()
            }
        }
        val con = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        assertEquals(sin.quedoDelDisponible, con.quedoDelDisponible)
        assertEquals(sin, con)
    }

    @Test
    fun `sin periodo anterior entero no hay comparacion`() = runBlocking<Unit> {
        // El primer movimiento es de octubre: septiembre no lo vio Movi.
        anotar(LocalDate.of(2026, 9, 26), 5_000_000, "Salario", tipo = "INCOME")
        anotar(LocalDate.of(2026, 10, 8), 600_000, "Comida")
        val c = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        assertNull(c.anterior)
        assertEquals(0, c.pagosEsperados)
    }

    @Test
    fun `sin nada en el periodo no hay resumen`() = runBlocking<Unit> {
        assertNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
    }

    // ── El correo ────────────────────────────────────────────────────────────

    private class Buzon {
        val enviados = mutableListOf<Triple<String, String, String>>()
        var falla = false
        suspend fun enviar(para: String, asunto: String, html: String): Boolean {
            if (falla) return false
            enviados += Triple(para, asunto, html)
            return true
        }
    }

    @Test
    fun `el correo sale una sola vez por periodo`() = runBlocking<Unit> {
        octubreDelDueno()
        val buzon = Buzon()
        assertEquals(1, mandarLosCierres(diaUnoDeNoviembre, "re_clave", buzon::enviar))
        // El barrido corre cada 12 horas: el mismo día, y el siguiente, no vuelve a salir.
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre, "re_clave", buzon::enviar))
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre.plusDays(1), "re_clave", buzon::enviar))
        val (para, asunto, html) = buzon.enviados.single()
        assertEquals("$uid@cierre.test", para)
        assertEquals("Cerró tu período de octubre", asunto)
        assertTrue(html.contains("Gimnasio"))
    }

    @Test
    fun `si el envio falla se reintenta en el barrido siguiente`() = runBlocking<Unit> {
        octubreDelDueno()
        val buzon = Buzon().apply { falla = true }
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre, "re_clave", buzon::enviar))
        assertEquals(0L, transaction { CierresEnviados.selectAll().count() }, "un envío fallido no deja el candado puesto")
        buzon.falla = false
        assertEquals(1, mandarLosCierres(diaUnoDeNoviembre, "re_clave", buzon::enviar))
    }

    @Test
    fun `con el interruptor apagado no sale`() = runBlocking<Unit> {
        octubreDelDueno()
        transaction { Users.update({ Users.id eq uid }) { it[cierrePorCorreo] = false } }
        val buzon = Buzon()
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre, "re_clave", buzon::enviar))
        assertTrue(buzon.enviados.isEmpty())
    }

    @Test
    fun `sin la clave de Resend no hace nada`() = runBlocking<Unit> {
        octubreDelDueno()
        val buzon = Buzon()
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre, null, buzon::enviar))
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre, "  ", buzon::enviar))
        assertTrue(buzon.enviados.isEmpty())
        assertEquals(0L, transaction { CierresEnviados.selectAll().count() })
    }

    @Test
    fun `pasados los primeros dias ya no sale`() = runBlocking<Unit> {
        octubreDelDueno()
        val buzon = Buzon()
        assertEquals(0, mandarLosCierres(diaUnoDeNoviembre.plusDays(3), "re_clave", buzon::enviar))
    }

    @Test
    fun `cada duenio recibe solo lo suyo`() = runBlocking<Unit> {
        octubreDelDueno()
        anotar(LocalDate.of(2026, 9, 27), 7_777_000, "Salario", tipo = "INCOME", usuario = otro)
        anotar(LocalDate.of(2026, 10, 2), 333_000, "Comida", usuario = otro)
        val buzon = Buzon()
        assertEquals(2, mandarLosCierres(diaUnoDeNoviembre, "re_clave", buzon::enviar))
        val delDueno = buzon.enviados.single { it.first == "$uid@cierre.test" }.third
        val delOtro = buzon.enviados.single { it.first == "$otro@cierre.test" }.third
        assertTrue(delDueno.contains("\$5.000.000"))
        assertFalse(delDueno.contains("\$7.777.000"))
        assertTrue(delOtro.contains("\$7.777.000"))
        assertFalse(delOtro.contains("Gimnasio"))
    }

    @Test
    fun `el html escapa lo que escribe el duenio`() {
        val html = htmlDelCierre(
            CierreDelPeriodo(periodo = "2026-10", nombre = "Octubre 2026", pagosEsperados = 1, pagosQueFaltaron = listOf("<b>Gym</b>")),
            baseUrl = "https://movi.test",
        )
        assertTrue(html.contains("&lt;b&gt;Gym&lt;/b&gt;"))
        assertFalse(html.contains("<b>Gym</b>"))
    }

    // ── La foto del cierre (vuelta 95) ────────────────────────────────────────

    @Test
    fun `la primera lectura guarda la foto y la siguiente la devuelve igual`() = runBlocking<Unit> {
        octubreDelDueno()
        assertEquals(0L, transaction { CierresGuardados.selectAll().count() })
        val primera = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        assertEquals(1L, transaction { CierresGuardados.selectAll().count() }, "queda una foto")
        assertNotNull(primera.cerradoEn)
        assertNull(primera.cambio)
        val segunda = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre.plusDays(1)))
        assertEquals(primera.entro, segunda.entro)
        assertEquals(primera.salio, segunda.salio)
        assertEquals(primera.cerradoEn, segunda.cerradoEn, "es la misma foto, no otra")
        // Lo que depende de «hoy» sí se mueve: es cuándo se muestra, no qué dice.
        assertEquals(2, segunda.diaDelPeriodoNuevo)
        assertEquals(1L, transaction { CierresGuardados.selectAll().count() }, "y no se guarda otra")
    }

    @Test
    fun `una edicion posterior no cambia la foto pero aparece la diferencia`() = runBlocking<Unit> {
        octubreDelDueno()
        val foto = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        // Llega tarde un gasto del 20 de octubre (un SMS que entró el día 2).
        anotar(LocalDate.of(2026, 10, 20), 250_000, "Comida", "Cena")
        val despues = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre.plusDays(1)))
        assertEquals(foto.salio, despues.salio, "las cifras siguen siendo las de la foto")
        assertEquals(foto.categorias, despues.categorias)
        val cambio = assertNotNull(despues.cambio)
        assertEquals(foto.salio, cambio.salioAntes)
        assertEquals(foto.salio + 250_000, cambio.salioAhora)
        assertEquals(foto.entro, cambio.entroAntes)
        assertEquals(foto.entro, cambio.entroAhora)
        assertEquals(
            "Cambió desde el cierre: Salió \$2.591.800 → \$2.841.800",
            lineaDelCambioDelCierre(despues),
        )
    }

    @Test
    fun `Actualizar reemplaza la foto por el calculo de hoy`() = runBlocking<Unit> {
        octubreDelDueno()
        val foto = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        anotar(LocalDate.of(2026, 10, 20), 250_000, "Comida", "Cena")
        assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre)?.cambio)

        val nuevo = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre, reemplazarLaFoto = true, soloElPeriodo = "2026-10"))
        assertEquals(foto.salio + 250_000, nuevo.salio)
        assertNull(nuevo.cambio)
        val leida = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        assertEquals(foto.salio + 250_000, leida.salio, "la foto nueva es la de hoy")
        assertNull(leida.cambio)
    }

    @Test
    fun `Actualizar de otro periodo no toca nada`() = runBlocking<Unit> {
        octubreDelDueno()
        val foto = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        anotar(LocalDate.of(2026, 10, 20), 250_000, "Comida", "Cena")
        assertNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre, reemplazarLaFoto = true, soloElPeriodo = "2026-09"))
        assertEquals(foto.salio, assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre)).salio)
    }

    @Test
    fun `un movimiento anulado despues tambien aparece como cambio`() = runBlocking<Unit> {
        octubreDelDueno()
        val foto = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        val id = anotar(LocalDate.of(2026, 10, 21), 90_000, "Comida", "Almuerzo")
        // La foto se tomó antes del gasto y el gasto aparece: cambio. Anulado, vuelve a coincidir.
        assertEquals(foto.salio + 90_000, assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre)?.cambio).salioAhora)
        transaction {
            VoidEvents.insert {
                it[VoidEvents.id] = "void_$id"; it[userId] = uid; it[originalEventId] = id
                it[reason] = "prueba"; it[timestamp] = 1L
            }
        }
        assertNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre)?.cambio)
    }

    @Test
    fun `el correo dice lo que dijo la foto aunque algo haya cambiado`() = runBlocking<Unit> {
        octubreDelDueno()
        // La foto se toma al leer la tarjeta el día 1; después llega un gasto y recién ahí sale el correo.
        val foto = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        anotar(LocalDate.of(2026, 10, 20), 250_000, "Comida", "Cena")
        val buzon = Buzon()
        assertEquals(1, mandarLosCierres(diaUnoDeNoviembre.plusDays(1), "re_clave", buzon::enviar))
        val html = buzon.enviados.single().third
        assertTrue(html.contains("\$2.591.800"), "el correo usa la foto: $foto")
        assertFalse(html.contains("\$2.841.800"))
        assertFalse(html.contains("Cambió desde el cierre"), "la línea de cambio es de la tarjeta, no del correo")
    }

    @Test
    fun `la foto de un duenio no la ve otro`() = runBlocking<Unit> {
        octubreDelDueno()
        anotar(LocalDate.of(2026, 9, 27), 7_777_000, "Salario", tipo = "INCOME", usuario = otro)
        anotar(LocalDate.of(2026, 10, 2), 333_000, "Comida", usuario = otro)
        val delDueno = assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre))
        val delOtro = assertNotNull(cierreDelPeriodoDe(otro, diaUnoDeNoviembre))
        assertEquals(5_000_000, delDueno.entro)
        assertEquals(7_777_000, delOtro.entro)
        assertEquals(2L, transaction { CierresGuardados.selectAll().count() })
        assertEquals(
            setOf(uid, otro),
            transaction { CierresGuardados.selectAll().map { it[CierresGuardados.userId] }.toSet() },
        )
        // Cambiar lo del otro no le mueve nada al dueño.
        anotar(LocalDate.of(2026, 10, 3), 111_000, "Comida", usuario = otro)
        assertNull(assertNotNull(cierreDelPeriodoDe(uid, diaUnoDeNoviembre)).cambio)
        assertNotNull(assertNotNull(cierreDelPeriodoDe(otro, diaUnoDeNoviembre)).cambio)
    }

    // ── La tarjeta de Hoy ────────────────────────────────────────────────────

    private fun token(u: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", u).withClaim("email", "$u@cierre.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private suspend fun ApplicationTestBuilder.leer(u: String): CierreDelPeriodo? {
        val res = client.get("/api/cierre-del-periodo") { header(HttpHeaders.Authorization, "Bearer ${token(u)}") }
        if (res.status == HttpStatusCode.NoContent) return null
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(CierreDelPeriodo.serializer(), res.bodyAsText())
    }

    /**
     * La ruta usa el reloj de verdad: con el corte en el día de hoy, hoy es el día 1 del período nuevo
     * y ayer fue del que cerró, corra el día que corra.
     */
    @Test
    fun `la tarjeta se descarta en el server, y solo la de ese duenio`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        transaction {
            listOf(uid, otro).forEach { u -> Users.update({ Users.id eq u }) { it[periodCutoffDay] = hoy.dayOfMonth } }
        }
        listOf(uid, otro).forEach { u -> anotar(hoy.minusDays(1), 80_000, "Comida", usuario = u) }

        val tarjeta = assertNotNull(leer(uid))
        assertEquals(1, tarjeta.diaDelPeriodoNuevo)
        val res = client.post("/api/cierre-del-periodo/descartar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"periodo":"${tarjeta.periodo}"}""")
        }
        assertEquals(HttpStatusCode.NoContent, res.status)
        assertNull(leer(uid), "descartada, no vuelve")
        assertNotNull(leer(otro), "la del otro dueño sigue")
    }

    @Test
    fun `Actualizar por la ruta reemplaza la foto, y solo la del que pide`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        transaction {
            listOf(uid, otro).forEach { u -> Users.update({ Users.id eq u }) { it[periodCutoffDay] = hoy.dayOfMonth } }
        }
        listOf(uid, otro).forEach { u -> anotar(hoy.minusDays(1), 80_000, "Comida", usuario = u) }

        val foto = assertNotNull(leer(uid))
        assertNotNull(leer(otro))
        anotar(hoy.minusDays(2), 20_000, "Comida", usuario = uid)
        val conCambio = assertNotNull(leer(uid))
        assertEquals(foto.salio, conCambio.salio)
        assertEquals(foto.salio + 20_000, assertNotNull(conCambio.cambio).salioAhora)

        val res = client.post("/api/cierre-del-periodo/actualizar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"periodo":"${foto.periodo}"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val nuevo = json.decodeFromString(CierreDelPeriodo.serializer(), res.bodyAsText())
        assertEquals(foto.salio + 20_000, nuevo.salio)
        assertNull(nuevo.cambio)
        assertNull(assertNotNull(leer(uid)).cambio)

        val mal = client.post("/api/cierre-del-periodo/actualizar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"periodo":"2001-01"}""")
        }
        assertEquals(HttpStatusCode.NotFound, mal.status)
        val sinPeriodo = client.post("/api/cierre-del-periodo/actualizar") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, sinPeriodo.status)
    }

    private val ajustes25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)
}
