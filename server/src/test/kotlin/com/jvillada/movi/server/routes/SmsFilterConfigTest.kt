package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTTP-level tests for GET /api/sms/filter-config (Task 1 of SP-sensor-app).
 * H2 in-memory DB (PostgreSQL compat mode), a test-local JWT secret/verifier, and
 * the full serialization+jwt+routing plugin chain wired through wireApp().
 */
class SmsFilterConfigTest {

    private val testSecret = "test-secret-for-sms-filter-config-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    // ── DB bootstrap ─────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:sms_filter_config_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )

        transaction {
            SchemaUtils.drop(
                PushSubscriptions, Subscriptions, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Subscriptions, PushSubscriptions,
            )
        }
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private fun mintToken(userId: String, email: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    // ── Test application module ───────────────────────────────────────────────

    private fun Application.testModule() {
        configureSerialization()

        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier  = JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()

        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null)
                        JWTPrincipal(credential.payload)
                    else null
                }
            }
        }

        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `filter config is public and returns the bank filter`() = testApplication {
        wireApp()
        val res = client.get("/api/sms/filter-config")   // SIN auth
        assertEquals(HttpStatusCode.OK, res.status)
        val obj = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals(listOf("85540", "891333", "87400"), obj["senderCodes"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(PALABRAS_DE_BANCOS, obj["bodyKeywords"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("bancolombia", "nubank"), PALABRAS_DE_BANCOS.take(2))
    }

    /**
     * **Vuelta 99 · los otros bancos, por el nombre.** Los SMS de Davivienda del papá del dueño llegan
     * de un código 871… que nadie confirmó: la palabra del cuerpo es lo que los deja pasar, y llega a
     * un APK instalado (1.87) sin reinstalar porque el teléfono baja este filtro cada día.
     *
     * Y ninguna palabra genérica: «popular» u «occidente» sueltos dejarían pasar SMS personales.
     */
    @Test
    fun `el filtro nombra a los otros bancos y ninguna palabra generica`() = testApplication {
        wireApp()
        val keywords = Json.parseToJsonElement(client.get("/api/sms/filter-config").bodyAsText())
            .jsonObject["bodyKeywords"]!!.jsonArray.map { it.jsonPrimitive.content }
        val esperadas = listOf(
            "davivienda", "davibank", "bbva", "banco de bogota", "av villas", "banco de occidente",
            "colpatria", "scotiabank", "itau", "falabella", "lulo bank", "rappipay",
        )
        assertEquals(true, keywords.containsAll(esperadas), "faltan: ${esperadas - keywords.toSet()}")
        for (generica in listOf("popular", "occidente", "bogota", "banco", "villas", "nu", "davi", "nequi", "daviplata")) {
            assertEquals(false, generica in keywords, "«$generica» suelta mete ruido")
        }
        // El cliente compara en minúsculas y por substring: el texto real de Davivienda tiene que pasar.
        val real = "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA."
        assertEquals(true, keywords.any { real.lowercase().contains(it) })
        // Y las palabras con tilde van también sin ella (y al revés): el cliente no quita tildes.
        assertEquals(true, keywords.containsAll(listOf("banco de bogotá", "itaú")))
    }

    /**
     * **El mismo endpoint sirve la lista de apps cuyas NOTIFICACIONES se capturan.**
     *
     * Un endpoint, un cache y un Worker de refresco para los dos sensores (ver
     * `SmsFilterConfigStore.loadNotificationApps`). Si alguien borra esta clave, todo teléfono
     * vuelve a sus paquetes compilados sin que nada avise.
     */
    @Test
    fun `filter config trae también los paquetes de las apps que avisan`() = testApplication {
        wireApp()
        val res = client.get("/api/sms/filter-config")
        val obj = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val paquetes = obj["appPackages"]!!.jsonArray.map { it.jsonPrimitive.content }
        // Leídos del teléfono del dueño el 19-sep, no conjeturados: la app de Bancolombia y la que
        // contiene el bolsillo Glim. Si alguien los cambia sin mirar un teléfono, esta prueba lo frena.
        assertEquals(
            listOf(
                "co.com.bancolombia.personas.superapp",
                "com.app.prontomas",
                "com.google.android.apps.walletnfcrel",
                "com.nu.production",
                // Vuelta 92: el paquete de Davivienda, leído del mismo teléfono el 19-sep.
                "com.todo1.davivienda.mobileapp",
                // Vuelta 98: confirmados en la ficha oficial de Google Play (título y desarrollador).
                "eu.netinfo.colpatria.system",
                "com.grupoavaloc1.bancamovil",
                "com.grupoavalav1.bancamovil",
                "co.com.bbva.mb",
                "com.bancodebogota.bancamovil",
                "com.grupoavalpo.bancamovil",
                "co.com.bancofalabella.mobile.omc",
                "co.com.lulobank.production",
                "com.nequi.MobileApp",
                "com.davivienda.daviplataapp",
            ),
            paquetes,
        )
    }

    /**
     * Vuelta 98 · **ninguna app que no sea de un banco.** Rappi publica los domicilios: su paquete
     * acá subiría cada «Tu pedido va en camino» a «Por revisar». RappiPay no tiene app propia, así
     * que no entra; y un paquete repetido sería un error de copia.
     */
    @Test
    fun `los paquetes no se repiten ni traen la app de domicilios`() = testApplication {
        wireApp()
        val paquetes = Json.parseToJsonElement(client.get("/api/sms/filter-config").bodyAsText())
            .jsonObject["appPackages"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(paquetes.size, paquetes.toSet().size, "paquetes repetidos")
        assertEquals(false, "com.grability.rappi" in paquetes, "Rappi es la app de domicilios, no RappiPay")
    }

    /**
     * **Un paquete, no una palabra suelta.** El cliente compara por IGUALDAD exacta contra el
     * nombre de paquete de la app que publicó la notificación (`FiltroDeNotificaciones`), así que
     * un «bancolombia» a secas acá no capturaría de más: no capturaría nada, en silencio, y el
     * dueño volvería a teclear sus movimientos a mano sin enterarse de por qué.
     */
    @Test
    fun `cada paquete tiene forma de nombre de paquete`() = testApplication {
        wireApp()
        val res = client.get("/api/sms/filter-config")
        val paquetes = Json.parseToJsonElement(res.bodyAsText())
            .jsonObject["appPackages"]!!.jsonArray.map { it.jsonPrimitive.content }

        val formaDePaquete = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")
        paquetes.forEach { p ->
            assertEquals(true, formaDePaquete.matches(p), "«$p» no es un nombre de paquete de Android")
        }
    }

    /**
     * **Ninguna keyword puede ser tan corta que aparezca dentro de palabras comunes.**
     *
     * El cliente compara por substring (`lower.contains(keyword)`, ver `BankSenderFilter`), y lo
     * que pasa el filtro **se sube al server**. O sea que una keyword como `nu` no haría ruido:
     * le mandaría la bandeja de SMS personal entera, porque coincide con «número», «nuevo»,
     * «nunca», «continuar». Este test existe para que agregar un banco nuevo con una keyword
     * corta falle acá y no en el teléfono del dueño.
     */
    @Test
    fun `ninguna keyword coincide dentro de una palabra española común`() = testApplication {
        wireApp()
        val res = client.get("/api/sms/filter-config")
        val keywords = Json.parseToJsonElement(res.bodyAsText())
            .jsonObject["bodyKeywords"]!!.jsonArray.map { it.jsonPrimitive.content.lowercase() }

        val fraseInocente = "tu numero de pedido continua en el nuevo local, nunca antes visto"
        keywords.forEach { k ->
            assertEquals(
                false,
                fraseInocente.contains(k),
                "la keyword «$k» coincide dentro de una frase común: capturaría SMS personales",
            )
        }
    }
}
