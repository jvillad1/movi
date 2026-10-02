package com.jvillada.movi.server.ai

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.ConversacionesDelAsistente
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

/**
 * # El andamio de las pruebas de la Ola 3
 *
 * H2 en memoria con compatibilidad Postgres, dos dueños (A y B, para el aislamiento), una cuenta
 * de cada uno y el JWT local de siempre. Las rutas se arman a mano con [rutas] en vez de con
 * `configureRouting()`: así una prueba puede montar el chat con un modelo de mentira —ninguna
 * llama a Anthropic— junto a las rutas de siempre (`/api/events`, `/api/recurring-rules`…), que
 * son las que confirman una propuesta.
 */
internal class AndamioDelAsistente(nombreDeLaBase: String) {
    val secreto = "secreto-de-prueba-de-la-ola-3-con-32-caracteres"
    val duenoA = "user-a-ola3"
    val duenoB = "user-b-ola3"
    val cuentaDeA = "acc-nu-a"
    val cuentaDeB = "acc-nu-b"

    /** Todas las tablas que tocan el chat y las rutas que confirman. */
    private val tablas: Array<Table> = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
        RecurringOccurrences, OccurrenceRejections, SmsMessages, Credits, Goals, CategoryPrefs,
        Documents, KnownDestinations, AiTurns, ConversacionesDelAsistente,
    )

    init {
        Database.connect(
            url = "jdbc:h2:mem:$nombreDeLaBase;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            listOf(duenoA to cuentaDeA, duenoB to cuentaDeB).forEach { (uid, cuenta) ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@ola3.test"; it[name] = "Dueño $uid"; it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[id] = cuenta; it[userId] = uid; it[name] = "Nu"; it[type] = "SAVINGS"; it[currency] = "COP"
                }
            }
        }
    }

    fun token(uid: String): String =
        JWT.create().withIssuer("movi").withAudience("movi-client")
            .withClaim("userId", uid).withClaim("email", "$uid@ola3.test")
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000L))
            .sign(Algorithm.HMAC256(secreto))

    fun HttpRequestBuilder.como(uid: String) {
        header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
    }

    fun Application.modulo(rutas: Route.() -> Unit) {
        configureSerialization()
        val verificador = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                verifier(verificador)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        routing { authenticate("jwt") { rutas() } }
    }
}
