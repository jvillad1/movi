package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.CONCEPTO_VACIO
import com.jvillada.movi.shared.model.CUENTA_NO_ENCONTRADA
import com.jvillada.movi.shared.model.MONTO_INVALIDO
import com.jvillada.movi.shared.model.PATA_NO_CAMBIA_DE_CUENTA
import com.jvillada.movi.shared.model.mensajeDeMonedaDistinta
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `PUT /api/events/{id}` — **corregir el monto, la cuenta y el concepto de un movimiento**.
 *
 * El caso que la abrió es del dueño y está probado tal cual acá abajo: *«voy a pagar 3 millones
 * desde NU y 1 millón desde Bancolombia»* sobre un movimiento «Hija» que ya estaba anotado por
 * $4.000.000 en Bancolombia.
 *
 * Lo que estas pruebas defienden, en una frase cada una:
 *
 * - Corregir la fila **es** el recálculo: los saldos y «Gastos del mes» se derivan de los eventos,
 *   así que las dos cuentas de un cambio de cuenta quedan bien sin tocar ningún acumulado.
 * - Mover un gasto a una cuenta de deuda lo **saca del mes**, y la respuesta lo dice.
 * - El monto de un **par** se mueve en las dos mitades; su **cuenta** no se mueve.
 * - El aislamiento entre usuarios se dice como «no existe».
 *
 * Mismo harness que `PagoDeCuotaRoutesTest`: H2 en memoria + JWT de prueba + `configureRouting()`.
 */
class EditarMovimientoRoutesTest {

    private val testSecret = "test-secret-for-edit-event-routes-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-edicion"
    private val otroId = "user-otro-edicion"

    private val banco = "acc-bancolombia"
    private val nu = "acc-nu"
    private val enDolares = "acc-usd"
    private val carro = "acc-carro-edicion"
    private val ajena = "acc-de-otro-edicion"

    /** El instante de «ahora», para que el movimiento caiga dentro del período que suma el mes. */
    private val ahora = System.currentTimeMillis()

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:editar_movimiento_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Goals, Subscriptions, CardPaymentDismissals, Cards, Credits, SmsMessages,
                RecurringRules, VoidEvents, Events, StatementImports, Budgets, Accounts, Users, CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
                SmsMessages, Credits, Cards, CardPaymentDismissals, Subscriptions, Goals, CategoryPrefs,
            )
            listOf(duenoId to "dueno@edicion.test", otroId to "otro@edicion.test").forEach { (uid, mail) ->
                Users.insert { it[id] = uid; it[email] = mail; it[name] = uid; it[passwordHash] = "hash" }
            }
            cuenta(banco, duenoId, "Bancolombia", "SAVINGS", "COP")
            cuenta(nu, duenoId, "Nu", "SAVINGS", "COP")
            cuenta(enDolares, duenoId, "Ahorros USD", "SAVINGS", "USD")
            cuenta(carro, duenoId, "Vehículo 4083", "LOAN", "COP")
            cuenta(ajena, otroId, "Ahorros de otro", "SAVINGS", "COP")

            // El movimiento del pedido: «Hija», $4.000.000, saliendo de Bancolombia.
            evento("ev-hija", duenoId, banco, "EXPENSE", 4_000_000L, "Otros", "Hija")
        }
    }

    private fun cuenta(id: String, uid: String, nombre: String, tipo: String, moneda: String) {
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = uid
            it[name] = nombre
            it[type] = tipo
            it[currency] = moneda
        }
    }

    private fun evento(
        id: String,
        uid: String,
        accountId: String,
        tipo: String,
        monto: Long,
        categoria: String,
        texto: String,
        transferId: String? = null,
        moneda: String = "COP",
    ) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[Events.accountId] = accountId
            it[type] = tipo
            it[amount] = monto
            it[currency] = moneda
            it[category] = categoria
            it[description] = texto
            it[timestamp] = ahora
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
            it[Events.transferId] = transferId
        }
    }

    private fun token(userId: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId).withClaim("email", "$userId@edicion.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    private suspend fun ApplicationTestBuilder.editar(uid: String, id: String, body: String) =
        client.put("/api/events/$id") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** El saldo DERIVADO de una cuenta, con la misma convención de signo que `computeBalances`. */
    private fun saldoDe(accountId: String): Long = transaction {
        val tipo = Accounts.selectAll().where { Accounts.id eq accountId }.single()[Accounts.type]
        val esDeuda = tipo == "LOAN" || tipo == "CREDIT_CARD"
        Events.selectAll().where { Events.accountId eq accountId }.sumOf { fila ->
            val monto = fila[Events.amount]
            val esGasto = fila[Events.type] == "EXPENSE"
            if (esDeuda) { if (esGasto) monto else -monto } else { if (esGasto) -monto else monto }
        }
    }

    private fun filaDe(id: String): Triple<Long, String, String> = transaction {
        Events.selectAll().where { Events.id eq id }.single().let {
            Triple(it[Events.amount], it[Events.accountId], it[Events.description])
        }
    }

    private suspend fun ApplicationTestBuilder.gastosDelMes(uid: String): Long {
        val texto = client.get("/api/finance-summary") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
        }.bodyAsText()
        // El JSON de este server sale con sangría, así que el separador lleva espacios. Un
        // `contains("\"egresos\":123")` a secas no matchea nada y el test pasaría por vacío.
        return Regex("\"egresos\"\\s*:\\s*(-?\\d+)").find(texto)!!.groupValues[1].toLong()
    }

    /** ¿La respuesta afirma [campo]? Tolerante a la sangría del JSON — ver [gastosDelMes]. */
    private fun campoBool(json: String, campo: String): Boolean? =
        Regex("\"$campo\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1)?.toBoolean()

    // ── El caso del dueño ──────────────────────────────────────────────────────

    @Test
    fun `cambiar el monto y la cuenta deja los dos saldos y los gastos del mes coherentes`() = testApplication {
        wireApp()
        assertEquals(-4_000_000L, saldoDe(banco))
        assertEquals(4_000_000L, gastosDelMes(duenoId))

        val res = editar(
            duenoId, "ev-hija",
            """{"amount":3000000,"accountId":"$nu","description":"Hija"}""",
        )
        val texto = res.bodyAsText()
        assertEquals(HttpStatusCode.OK, res.status, texto)

        val (monto, cuenta, concepto) = filaDe("ev-hija")
        assertEquals(3_000_000L, monto)
        assertEquals(nu, cuenta)
        assertEquals("Hija", concepto)
        assertEquals(0L, saldoDe(banco), "la cuenta vieja recupera lo que ya no sale de ella")
        assertEquals(-3_000_000L, saldoDe(nu), "y la nueva paga el monto corregido")
        assertEquals(3_000_000L, gastosDelMes(duenoId), "el mes cuenta el monto nuevo, no el viejo")
        // El eco no puede decir que dejó de contar: Nu es una cuenta de activo. Se afirma «no
        // dice false» y no «dice true» porque `true` es el default del wire y el serializador lo
        // omite; quien de verdad prueba que sigue contando es la línea de arriba, que suma el mes.
        assertTrue(campoBool(texto, "countsAsCashFlow") != false, texto)
    }

    @Test
    fun `mover un gasto a un credito lo saca de los gastos del mes y la respuesta lo dice`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-hija", """{"accountId":"$carro"}""")
        val texto = res.bodyAsText()

        assertEquals(HttpStatusCode.OK, res.status, texto)
        // `countsAsCashFlow` NUNCA se toma del cliente: se deriva del tipo de la cuenta nueva.
        assertEquals(false, campoBool(texto, "countsAsCashFlow"), texto)
        assertEquals(0L, gastosDelMes(duenoId), "en una cuenta LOAN nada es flujo de caja del mes")
        // Y en una cuenta de deuda el EXPENSE sube la deuda, que es lo que de verdad pasó.
        assertEquals(4_000_000L, saldoDe(carro))
    }

    @Test
    fun `guardar sin cambiar nada responde 200 y no toca la fila`() = testApplication {
        wireApp()
        val res = editar(
            duenoId, "ev-hija",
            """{"amount":4000000,"accountId":"$banco","description":"Hija"}""",
        )
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(Triple(4_000_000L, banco, "Hija"), filaDe("ev-hija"))
        assertEquals(-4_000_000L, saldoDe(banco))
    }

    @Test
    fun `el concepto se guarda recortado`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-hija", """{"description":"  Mesada de la hija  "}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals("Mesada de la hija", filaDe("ev-hija").third)
    }

    // ── Las dos mitades de un par ──────────────────────────────────────────────

    @Test
    fun `corregir el monto de una pata lo corrige en LAS DOS`() = testApplication {
        // Un pago de cuota: la plata sale de Bancolombia y baja la deuda del carro. Si solo se
        // corrigiera la pata tocada, saldrían $1.500.000 de la cuenta y bajarían $2.000.000 de
        // deuda — medio millón de la nada.
        transaction {
            evento("ev-pata-dinero", duenoId, banco, "EXPENSE", 2_000_000L, "Cuota de crédito", "Cuota", "tr-1")
            evento("ev-pata-deuda", duenoId, carro, "INCOME", 2_000_000L, "Cuota de crédito", "Pago", "tr-1")
        }
        wireApp()

        val res = editar(duenoId, "ev-pata-dinero", """{"amount":1500000}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        assertEquals(1_500_000L, filaDe("ev-pata-dinero").first)
        assertEquals(1_500_000L, filaDe("ev-pata-deuda").first, "la hermana se mueve con ella")
        // Y el par sigue cuadrado: lo que sale de la cuenta es lo que baja de la deuda.
        assertEquals(-1_500_000L, saldoDe(carro), "la deuda bajó exactamente lo que salió")
    }

    @Test
    fun `una pata no cambia de cuenta, y se dice por que`() = testApplication {
        transaction {
            evento("ev-pata-a", duenoId, banco, "EXPENSE", 1_000_000L, "Traspaso", "Traspaso a Nu", "tr-2")
            evento("ev-pata-b", duenoId, nu, "INCOME", 1_000_000L, "Traspaso", "Traspaso desde Bancolombia", "tr-2")
        }
        wireApp()

        val res = editar(duenoId, "ev-pata-a", """{"accountId":"$nu"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(PATA_NO_CAMBIA_DE_CUENTA, res.bodyAsText())
        assertEquals(banco, filaDe("ev-pata-a").second, "y no la movió a medias")
    }

    @Test
    fun `una pata si puede corregir su concepto`() = testApplication {
        transaction {
            evento("ev-pata-c", duenoId, banco, "EXPENSE", 1_000_000L, "Traspaso", "Traspaso a Nu", "tr-3")
            evento("ev-pata-d", duenoId, nu, "INCOME", 1_000_000L, "Traspaso", "Traspaso desde Bancolombia", "tr-3")
        }
        wireApp()

        val res = editar(duenoId, "ev-pata-c", """{"accountId":"$banco","description":"Traspaso a Nu · ahorro"}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals("Traspaso a Nu · ahorro", filaDe("ev-pata-c").third)
        // El concepto es de CADA pata: la hermana conserva el suyo.
        assertEquals("Traspaso desde Bancolombia", filaDe("ev-pata-d").third)
    }

    // ── Los rechazos ──────────────────────────────────────────────────────────

    @Test
    fun `un monto de cero se rechaza y no toca nada`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-hija", """{"amount":0}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(MONTO_INVALIDO, res.bodyAsText())
        assertEquals(4_000_000L, filaDe("ev-hija").first)
    }

    @Test
    fun `un concepto en blanco se rechaza`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-hija", """{"description":"   "}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(CONCEPTO_VACIO, res.bodyAsText())
    }

    @Test
    fun `no se muda un movimiento en pesos a una cuenta en dolares`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-hija", """{"accountId":"$enDolares"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(mensajeDeMonedaDistinta("USD", "COP"), res.bodyAsText())
        assertEquals(banco, filaDe("ev-hija").second)
    }

    @Test
    fun `una cuenta de otro usuario responde que no existe`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-hija", """{"accountId":"$ajena"}""")
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(CUENTA_NO_ENCONTRADA, res.bodyAsText())
        assertEquals(banco, filaDe("ev-hija").second)
    }

    @Test
    fun `el movimiento de otro usuario no existe para este`() = testApplication {
        wireApp()
        val res = editar(otroId, "ev-hija", """{"amount":1}""")
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(4_000_000L, filaDe("ev-hija").first, "y no se lo editó por la puerta de atrás")
    }

    @Test
    fun `un movimiento anulado se trata como inexistente`() = testApplication {
        wireApp()
        val anular = client.post("/api/events/ev-hija/void") {
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
        }
        assertEquals(HttpStatusCode.Created, anular.status, anular.bodyAsText())

        val res = editar(duenoId, "ev-hija", """{"amount":1000}""")
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(4_000_000L, filaDe("ev-hija").first)
    }

    @Test
    fun `un movimiento que no existe da 404`() = testApplication {
        wireApp()
        val res = editar(duenoId, "ev-que-no-esta", """{"amount":1000}""")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }
}
