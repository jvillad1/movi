package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RechazarOcurrenciaRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **«Tildado sin evidencia»** — los tres casos reales del dueño, probados contra el endpoint.
 *
 * La casilla del «Checklist del período» sellaba con `eventId = null`: daba por pagado sin ningún
 * movimiento detrás. En su base quedaron tres sellos así cuyo movimiento **sí existía**, con el
 * nombre casi calcado. Acá se reconstruyen esos tres casos y se exige que:
 *
 *  1. **«Mercado»** contra un movimiento «Mercado» de $2.000.000 → Movi empareja solo. El nombre
 *     pega, y eso alcanza aunque la categoría del movimiento no sea la de la regla.
 *  2. **«Gimnasio Cami»** con DOS pagos de gimnasio iguales en la ventana → Movi **pregunta**. No
 *     puede saber cuál es cuál, y elegir uno quemaría el otro (un id emparejado no se vuelve a
 *     proponer). Este caso es el que define el techo de toda esta rama.
 *  3. **«Salario»** contra «Salario Septiembre 2026» → empareja solo porque el nombre pega
 *     (`nombreDeMovimientoPegaConRegla` en `:core` perdona el mes y el año), y eso ni siquiera
 *     necesita el monto exacto. Una consignación que NO repite el nombre de la regla sigue
 *     necesitando las tres circunstancias juntas (categoría + cuenta + monto exacto), y una
 *     recarga con la MISMA categoría pero en otra cuenta no es concluyente y por lo tanto no
 *     genera ambigüedad.
 *
 * Y lo que sostiene que un emparejamiento automático sea reversible: el rechazo persistido.
 */
class MoviEmparejaSoloTest {

    private val testSecret = "test-secret-para-emparejar-solo-minimo-32-ch"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val uid = "user-empareja-solo"
    private val email = "empareja@solo.test"
    private val bancolombia = "acc-bancolombia"
    private val glim = "acc-glim"

    /** La fecha civil de la app (Bogotá), la misma que usan los endpoints. */
    private val hoy: LocalDate = AppClock.today()

    /**
     * **Todos los vencimientos de este archivo caen HOY.**
     *
     * No es comodidad: `/api/payments/occurrences` no emite nada cuyo vencimiento todavía no haya
     * llegado (preguntar por algo que vence en tres semanas es ruido), así que un día fijo —el 25,
     * el 5— haría que el test probara o no probara según el día en que corriera. Con el día de
     * hoy, la regla siempre está en juego y la ventana de candidatos siempre contiene «ayer».
     */
    private val diaDelVencimiento = hoy.dayOfMonth

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:empareja_solo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, Credits, Cards,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)

            Users.insert {
                it[id] = uid
                it[Users.email] = this@MoviEmparejaSoloTest.email
                it[name] = "Dueño"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = bancolombia
                it[userId] = uid
                it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"
            }
            Accounts.insert {
                it[id] = glim
                it[userId] = uid
                it[name] = "Glim"
                it[type] = "SAVINGS"
            }
        }
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    private fun regla(
        id: String,
        nombre: String,
        categoria: String,
        monto: Long,
        tipo: String = "EXPENSE",
        cuenta: String? = bancolombia,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = diaDelVencimiento
            it[type] = tipo
            it[accountId] = cuenta
        }
    }

    private fun movimiento(
        id: String,
        nota: String,
        categoria: String,
        monto: Long,
        cuenta: String = bancolombia,
        tipo: String = "EXPENSE",
        fecha: LocalDate = hoy,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[Events.category] = categoria
            it[description] = nota
            it[timestamp] = appDateToEpochMillis(fecha)
        }
    }

    private fun mintToken(): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", uid)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    private suspend fun HttpClient.ocurrencias(): List<OccurrenceState> {
        val resp = get("/api/payments/occurrences") { header(HttpHeaders.Authorization, "Bearer ${mintToken()}") }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body()
    }

    private suspend fun HttpClient.rechazar(ruleId: String, eventId: String): HttpStatusCode =
        post("/api/recurring-rules/$ruleId/occurrence/rechazo") {
            header(HttpHeaders.Authorization, "Bearer ${mintToken()}")
            contentType(ContentType.Application.Json)
            setBody(RechazarOcurrenciaRequest(eventId))
        }.status

    // ── Caso 1: «Mercado» ─────────────────────────────────────────────────────

    /**
     * El nombre idéntico alcanza solo, **aunque la categoría no coincida**: la regla está en
     * «Comida» y el movimiento en «Mercado». Es exactamente el sello sin evidencia que había en la
     * base del dueño, con su movimiento a la vista.
     */
    @Test
    fun `Mercado se empareja solo porque el nombre pega`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)

        val estado = client.ocurrencias().first { it.ruleId == "rr-mercado" }
        assertTrue(estado.occurred, "El movimiento se llama igual que la regla: no hay nada que preguntar")
        assertEquals("ev-mercado", estado.eventId)
        assertTrue(estado.automatica, "Sin esta marca la pantalla no sabría que esto lo dedujo Movi")
        assertTrue(
            estado.derivadaDeUnMovimiento,
            "No hay fila en recurring_occurrences: un «Deshacer» ahí sería un control muerto",
        )
        assertEquals(2_000_000L, estado.montoDelPago)
        assertTrue(estado.candidates.isEmpty(), "Ya está emparejado: no hay nada que proponer")
    }

    /**
     * **Y no se sella nada.** El emparejamiento se DERIVA en cada lectura, igual que las cuotas de
     * crédito: si el movimiento se anula o se edita, la marca desaparece sola sin que ningún
     * camino de borrado tenga que acordarse de esta tabla.
     */
    @Test
    fun `el emparejamiento automatico no escribe ningun sello`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)

        assertTrue(client.ocurrencias().first { it.ruleId == "rr-mercado" }.occurred)
        val sellos = transaction { RecurringOccurrences.selectAll().count() }
        assertEquals(0L, sellos, "Lo automático se deriva, no se sella: un sello sobrevive a su evidencia")

        // Y anulado el movimiento, la marca se cae sola en la lectura siguiente.
        transaction {
            VoidEvents.insert {
                it[id] = "void-mercado"
                it[userId] = uid
                it[originalEventId] = "ev-mercado"
                it[timestamp] = System.currentTimeMillis()
            }
        }
        val despues = client.ocurrencias().first { it.ruleId == "rr-mercado" }
        assertFalse(despues.occurred, "Anulado el movimiento, el periodo vuelve a estar abierto")
    }

    // ── Caso 2: «Gimnasio Cami» ───────────────────────────────────────────────

    /**
     * **Dos concluyentes: se pregunta.** Un movimiento «Gimnasio Cami» de $180.000 (el nombre
     * pega) y otro «Gimnasio» de $180.000 en la misma cuenta, categoría y monto (las tres
     * circunstancias). Son dos pagos de gimnasio iguales y Movi no puede saber cuál es cuál;
     * elegir el mejor puntuado sería inventar un desempate y quemar el otro movimiento.
     */
    @Test
    fun `Gimnasio Cami con dos pagos iguales pregunta en vez de emparejar`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-gym", nombre = "Gimnasio Cami", categoria = "Gimnasio", monto = 180_000L)
        movimiento(id = "ev-gym-cami", nota = "Gimnasio Cami", categoria = "Gimnasio", monto = 180_000L)
        movimiento(id = "ev-gym-otro", nota = "Gimnasio", categoria = "Gimnasio", monto = 180_000L)

        val estado = client.ocurrencias().first { it.ruleId == "rr-gym" }
        assertFalse(estado.occurred, "Hay dos candidatos concluyentes: Movi tiene que preguntar")
        assertFalse(estado.automatica)
        assertEquals(
            setOf("ev-gym-cami", "ev-gym-otro"), estado.candidates.map { it.id }.toSet(),
            "Y los dos siguen ofrecidos: el dueño elige en un toque",
        )
    }

    /** El control del caso 2: con UNO solo de los dos, sí empareja. Si no, el test de arriba no prueba nada. */
    @Test
    fun `Gimnasio Cami con un solo pago si se empareja`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-gym", nombre = "Gimnasio Cami", categoria = "Gimnasio", monto = 180_000L)
        movimiento(id = "ev-gym-cami", nota = "Gimnasio Cami", categoria = "Gimnasio", monto = 180_000L)

        val estado = client.ocurrencias().first { it.ruleId == "rr-gym" }
        assertTrue(estado.occurred)
        assertEquals("ev-gym-cami", estado.eventId)
    }

    // ── Caso 3: «Salario» ─────────────────────────────────────────────────────

    /**
     * **«Salario Septiembre 2026» pega con la regla «Salario» por el nombre**, no por el monto:
     * antes esto solo emparejaba si además el monto daba exacto (ver el commit que agregó
     * `nombreDeMovimientoPegaConRegla` en `:core`), y el monto de un sueldo varía mes a mes por
     * retenciones. Con $20.500.000 en vez de los $20.038.658 anotados en la regla, sigue siendo
     * concluyente — es el caso real del dueño.
     */
    @Test
    fun `Salario Octubre 2026 se empareja por el nombre, sin exigir el monto`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(
            id = "rr-salario", nombre = "Salario", categoria = "Salario",
            monto = 20_038_658L, tipo = "INCOME",
        )
        movimiento(
            id = "ev-salario", nota = "Salario Octubre 2026", categoria = "Salario",
            monto = 20_500_000L, tipo = "INCOME",
        )

        val estado = client.ocurrencias().first { it.ruleId == "rr-salario" }
        assertTrue(estado.occurred, "El nombre pega aunque el monto no sea el mismo")
        assertEquals("ev-salario", estado.eventId)
        assertTrue(estado.automatica)
        assertEquals(20_500_000L, estado.montoDelPago)
    }

    /** Con dos movimientos que dicen «Salario …» en la ventana, Movi no sabe cuál es cuál y pregunta. */
    @Test
    fun `dos Salario en la ventana siguen preguntando`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(
            id = "rr-salario", nombre = "Salario", categoria = "Salario",
            monto = 20_038_658L, tipo = "INCOME",
        )
        movimiento(
            id = "ev-salario-1", nota = "Salario Octubre 2026", categoria = "Salario",
            monto = 20_038_658L, tipo = "INCOME",
        )
        movimiento(
            id = "ev-salario-2", nota = "Salario", categoria = "Salario",
            monto = 20_038_658L, tipo = "INCOME",
        )

        val estado = client.ocurrencias().first { it.ruleId == "rr-salario" }
        assertFalse(estado.occurred, "Dos concluyentes por nombre: Movi pregunta, no elige")
    }

    /**
     * **Sin que el nombre repita el de la regla**, sigue haciendo falta la puerta de siempre: las
     * tres circunstancias juntas (categoría + cuenta + monto exacto). «Consignación nómina» no
     * empieza con «Salario», así que `nombreDeMovimientoPegaConRegla` no perdona nada acá.
     *
     * Y la recarga de $621.788 —misma categoría «Salario», pero en la cuenta Glim y con otro
     * monto— **no** es concluyente, así que no genera la ambigüedad que apagaría el emparejamiento.
     * Es la parte que prueba que las tres circunstancias tienen que darse **juntas**.
     */
    @Test
    fun `sin que el nombre pegue, empareja por categoria cuenta y monto exacto`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(
            id = "rr-salario", nombre = "Salario", categoria = "Salario",
            monto = 20_308_659L, tipo = "INCOME",
        )
        movimiento(
            id = "ev-salario", nota = "Consignación nómina", categoria = "Salario",
            monto = 20_308_659L, tipo = "INCOME",
        )
        movimiento(
            id = "ev-recarga", nota = "Recarga Glim Alimentación · Mercado Libre", categoria = "Salario",
            monto = 621_788L, cuenta = glim, tipo = "INCOME",
        )

        val estado = client.ocurrencias().first { it.ruleId == "rr-salario" }
        assertTrue(estado.occurred, "Categoría + cuenta + monto exacto: no puede ser otra cosa")
        assertEquals("ev-salario", estado.eventId)
        assertTrue(estado.automatica)
        assertEquals(20_308_659L, estado.montoDelPago)
    }

    /**
     * **«Monto exacto» es exacto.** Sin que el nombre pegue, un peso de diferencia y ya no hay las
     * tres circunstancias: el movimiento vuelve a ser una propuesta, que es lo que corresponde.
     * Nada de márgenes elegidos a ojo — el KDoc de `OccurrenceMatching` argumenta largo contra los
     * ±10 %.
     */
    @Test
    fun `sin que el nombre pegue, un peso de diferencia deja de ser concluyente`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(
            id = "rr-salario", nombre = "Salario", categoria = "Salario",
            monto = 20_308_659L, tipo = "INCOME",
        )
        movimiento(
            id = "ev-salario", nota = "Consignación nómina", categoria = "Salario",
            monto = 20_308_658L, tipo = "INCOME",
        )

        val estado = client.ocurrencias().first { it.ruleId == "rr-salario" }
        assertFalse(estado.occurred, "Con el monto distinto no hay certeza: se propone y decide el dueño")
        assertEquals(listOf("ev-salario"), estado.candidates.map { it.id })
    }

    // ── El rechazo ────────────────────────────────────────────────────────────

    /**
     * **Rechazar un emparejamiento automático lo revierte, y para siempre.**
     *
     * Es lo que hace que emparejar solo sea aceptable: si Movi se equivoca, el dueño tiene algo
     * que hacer al respecto y el «no» sobrevive a la recarga. Con el rechazo en memoria —como
     * estaba en la pantalla— la lectura siguiente volvería a emparejar lo mismo, para siempre.
     */
    @Test
    fun `el rechazo saca el movimiento del emparejamiento y de las propuestas`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)
        assertTrue(client.ocurrencias().first { it.ruleId == "rr-mercado" }.occurred)

        assertEquals(HttpStatusCode.NoContent, client.rechazar("rr-mercado", "ev-mercado"))

        val estado = client.ocurrencias().first { it.ruleId == "rr-mercado" }
        assertFalse(estado.occurred, "Rechazado, el periodo vuelve a estar abierto")
        assertFalse(estado.automatica)
        assertTrue(
            estado.candidates.isEmpty(),
            "Y tampoco se vuelve a proponer: un «no fue este» que siguiera ofreciéndolo no sería un no",
        )
    }

    /**
     * **El rechazo es por (regla, movimiento), no por movimiento.** Es el mismo bug que ya se
     * arregló del lado de la pantalla: con «Agua», «Gas» e «Internet» todas en «Servicios», el
     * pago del gas se propone en las tres. Rechazarlo en la del agua no puede quitárselo a la del
     * gas, que es donde sí era el correcto.
     */
    @Test
    fun `rechazar en una regla no afecta a otra`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        // El agua cuesta otra cosa: así el pago del gas NO le es concluyente (le falta el monto
        // exacto) y sigue siendo lo que este caso necesita, una simple propuesta compartida.
        regla(id = "rr-agua", nombre = "Agua", categoria = "Servicios", monto = 42_000L)
        regla(id = "rr-gas", nombre = "Gas", categoria = "Servicios", monto = 58_000L)
        movimiento(id = "ev-gas", nota = "Gas", categoria = "Servicios", monto = 58_000L)

        // El nombre pega con «Gas», así que Movi lo empareja ahí; en «Agua» solo se propone.
        assertTrue(client.ocurrencias().first { it.ruleId == "rr-gas" }.occurred)
        assertEquals(HttpStatusCode.NoContent, client.rechazar("rr-agua", "ev-gas"))

        val gas = client.ocurrencias().first { it.ruleId == "rr-gas" }
        assertTrue(gas.occurred, "El «no» era sobre el agua: el gas conserva su movimiento")
        assertEquals("ev-gas", gas.eventId)
    }

    /** Repetir el rechazo no falla ni recorre la fecha: un doble toque no puede ser un error. */
    @Test
    fun `el rechazo es idempotente`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)

        assertEquals(HttpStatusCode.NoContent, client.rechazar("rr-mercado", "ev-mercado"))
        val primera = transaction {
            OccurrenceRejections.selectAll().single()[OccurrenceRejections.rejectedAt]
        }
        assertEquals(HttpStatusCode.NoContent, client.rechazar("rr-mercado", "ev-mercado"))

        val filas = transaction { OccurrenceRejections.selectAll().toList() }
        assertEquals(1, filas.size, "El segundo rechazo no puede duplicar la fila")
        assertEquals(
            primera, filas.single()[OccurrenceRejections.rejectedAt],
            "Ni recorrer la fecha: lo que importa es CUÁNDO se dijo que no la primera vez",
        )
    }

    /** Un movimiento que no existe (o que es de otro) no puede entrar en esta tabla. */
    @Test
    fun `no se puede rechazar un movimiento ajeno o inventado`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)

        assertEquals(HttpStatusCode.BadRequest, client.rechazar("rr-mercado", "ev-que-no-existe"))
        assertEquals(0L, transaction { OccurrenceRejections.selectAll().count() })
    }

    // ── Un movimiento, una regla ──────────────────────────────────────────────

    /**
     * **Un movimiento emparejado solo no puede ser además el candidato de OTRA regla.**
     *
     * Una sola entrada de plata cerrando (o pareciendo cerrar) dos periodos es exactamente el
     * «marcar de más» que todo este archivo evita. Acá «Gas» se lo lleva por nombre, y «Agua» —que
     * lo tenía como candidato por compartir la categoría «Servicios»— se queda sin él.
     *
     * La reserva depende de un orden estable entre reglas (por id), así que también se comprueba
     * que la respuesta no cambie entre dos lecturas seguidas.
     */
    @Test
    fun `lo emparejado por una regla no se le ofrece a otra`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        // Distinto monto en el agua a propósito: si fueran iguales, el pago del gas sería
        // concluyente para las DOS reglas y la de id menor se lo llevaría por orden — otro caso,
        // legítimo pero distinto del que este test mide.
        regla(id = "rr-agua", nombre = "Agua", categoria = "Servicios", monto = 42_000L)
        regla(id = "rr-gas", nombre = "Gas", categoria = "Servicios", monto = 58_000L)
        movimiento(id = "ev-gas", nota = "Gas", categoria = "Servicios", monto = 58_000L)

        repeat(2) {
            val estados = client.ocurrencias()
            val gas = estados.first { it.ruleId == "rr-gas" }
            val agua = estados.first { it.ruleId == "rr-agua" }
            assertTrue(gas.occurred, "El nombre pega con «Gas»: ahí se empareja")
            assertEquals("ev-gas", gas.eventId)
            assertFalse(agua.occurred)
            assertTrue(
                agua.candidates.isEmpty(),
                "«Agua» no puede seguir ofreciendo un pago que ya quedó emparejado con «Gas»",
            )
        }
    }

    /**
     * Y un movimiento **sellado a mano** en otra regla tampoco se empareja solo acá: es la misma
     * cuarta puerta de siempre (`usedEventIds`), que la pasada automática respeta porque arranca
     * de ella.
     */
    @Test
    fun `un movimiento ya sellado en otra regla no se empareja solo`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)
        transaction {
            RecurringOccurrences.insert {
                it[userId] = uid
                it[ruleId] = "rr-otra"
                it[period] = "%04d-%02d".format(hoy.year, hoy.monthValue)
                it[eventId] = "ev-mercado"
                it[confirmedAt] = System.currentTimeMillis()
            }
        }

        val estado = client.ocurrencias().first { it.ruleId == "rr-mercado" }
        assertFalse(estado.occurred, "Ese movimiento ya cerró otro periodo: no puede cerrar este también")
        assertTrue(estado.candidates.isEmpty())
    }

    /**
     * Un sello a mano gana: si el periodo ya está cerrado por el dueño, la respuesta sigue siendo
     * la suya —sin `automatica`— aunque haya un movimiento concluyente dando vueltas.
     */
    @Test
    fun `un sello a mano no se pisa con el emparejamiento automatico`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-mercado", nombre = "Mercado", categoria = "Comida", monto = 2_000_000L)
        movimiento(id = "ev-mercado", nota = "Mercado", categoria = "Mercado", monto = 2_000_000L)
        transaction {
            RecurringOccurrences.insert {
                it[userId] = uid
                it[ruleId] = "rr-mercado"
                it[period] = "%04d-%02d".format(hoy.year, hoy.monthValue)
                it[eventId] = null
                it[confirmedAt] = 123L
            }
        }

        val estado = client.ocurrencias().first { it.ruleId == "rr-mercado" }
        assertTrue(estado.occurred)
        assertFalse(estado.automatica, "Lo cerró el dueño: la fila conserva su «Deshacer»")
        assertFalse(estado.derivadaDeUnMovimiento)
        assertEquals(123L, estado.confirmedAt)
    }

    /** Sin nada parecido en la ventana, todo sigue como antes: abierto y sin candidatos. */
    @Test
    fun `sin movimiento parecido el periodo sigue abierto`() = testApplication {
        application { testModule() }
        val client = createClient { install(ContentNegotiation) { json() } }
        regla(id = "rr-arriendo", nombre = "Arriendo", categoria = "Vivienda", monto = 1_800_000L)
        movimiento(id = "ev-exito", nota = "Éxito", categoria = "Mercado", monto = 1_750_000L)

        val estado = client.ocurrencias().first { it.ruleId == "rr-arriendo" }
        assertNotNull(estado)
        assertFalse(estado.occurred)
        assertFalse(estado.automatica)
        assertTrue(estado.candidates.isEmpty(), "Ni el nombre ni la categoría pegan: no es candidato de nada")
    }
}
