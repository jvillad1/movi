package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.PasswordReset
import com.jvillada.movi.server.auth.PasswordResetMailer
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.PasswordPolicy
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests HTTP de /api/auth: piso de contraseña y recuperación por correo.
 * Mismo harness que el resto (H2 en modo PostgreSQL + los plugins reales), pero sin JWT
 * porque estas rutas son públicas.
 *
 * El correo NO sale a la red: [PasswordResetMailer.sender] se reemplaza por un grabador,
 * que además es la única forma de leer el token que se envió (en la DB solo vive el hash).
 */
class AuthRoutesTest {

    private val strongPassword = "una-contrasena-larga-y-tranquila"
    private val legacyShortPassword = "abc123"           // 6 chars: por debajo del piso nuevo
    private val legacyUserId = "usr-legacy"
    private val legacyEmail = "legacy@movi.test"

    /** (destinatario, enlace) de cada correo que las rutas quisieron enviar. */
    private val sentEmails = mutableListOf<Pair<String, String>>()
    private var mailerResult = true

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.jwt.secret", "test-secret-for-auth-routes-tests-min-32-chars")
        System.setProperty("movi.resend.apiKey", "test-resend-key")
        System.setProperty("movi.reminder.from", "movi <test@movi.test>")
        System.setProperty("movi.app.baseUrl", "https://movi.test")
        RateLimiter.reset()

        Database.connect(
            url    = "jdbc:h2:mem:auth_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(PasswordResetTokens, Users)
            SchemaUtils.create(Users, PasswordResetTokens)
            // Usuario "viejo": su contraseña de 6 caracteres es anterior al piso nuevo.
            Users.insert {
                it[id]           = legacyUserId
                it[email]        = legacyEmail
                it[name]         = "Legacy"
                it[passwordHash] = BCrypt.withDefaults().hashToString(BCRYPT_COST, legacyShortPassword.toCharArray())
            }
        }

        sentEmails.clear()
        mailerResult = true
        if (realSender == null) realSender = PasswordResetMailer.sender
        PasswordResetMailer.sender = { to, _, html, _, _ ->
            sentEmails += to to (LINK_RE.find(html)?.groupValues?.get(1) ?: "")
            mailerResult
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.jwt.secret")
        System.clearProperty("movi.resend.apiKey")
        System.clearProperty("movi.reminder.from")
        System.clearProperty("movi.app.baseUrl")
        realSender?.let { PasswordResetMailer.sender = it }
        RateLimiter.reset()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application {
            configureSerialization()
            routing { authRoutes() }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    //
    // OJO — estos helpers YA NO mandan `X-Forwarded-For`, y su ausencia es la verdad.
    // Antes lo mandaban con una IP distinta por test, lo que se LEÍA como aislamiento por
    // origen; era inerte: `ForwardedHeaders`/`XForwardedHeaders` no está instalado en
    // `server/src/main`, así que `origin.remoteHost` es el peer TCP y esa cabecera no la mira
    // nadie. Ningún test afirmaba nada sobre distintas IPs, así que pasaban igual con o sin
    // ella. Mandarla de nuevo acá sería volver a insinuar una propiedad que el deploy no tiene
    // (ver `el rate limit NO aisla por origen…` más abajo, que fija justamente lo contrario).

    private suspend fun ApplicationTestBuilder.register(email: String, password: String) =
        client.post("/api/auth/register") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"email":"$email","name":"Alguien","password":"$password"}""")
        }

    private suspend fun ApplicationTestBuilder.login(email: String, password: String) =
        client.post("/api/auth/login") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"email":"$email","password":"$password"}""")
        }

    private suspend fun ApplicationTestBuilder.requestReset(email: String) =
        client.post("/api/auth/password-reset/request") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"email":"$email"}""")
        }

    private suspend fun ApplicationTestBuilder.confirmReset(token: String, newPassword: String) =
        client.post("/api/auth/password-reset/confirm") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"token":"$token","newPassword":"$newPassword"}""")
        }

    /** El token solo existe en el enlace del correo — en la DB vive el hash. */
    private fun tokenFromLastEmail(): String =
        sentEmails.last().second.substringAfter("reset=")

    private fun storedHashFor(userId: String): String = transaction {
        Users.selectAll().where { Users.id eq userId }.single()[Users.passwordHash]
    }

    // ── Piso de contraseña ────────────────────────────────────────────────────

    @Test
    fun `registro rechaza una contrasena por debajo del piso`() = testApplication {
        wireApp()
        val res = register("corta@movi.test", "a".repeat(PasswordPolicy.MIN_LENGTH - 1))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("${PasswordPolicy.MIN_LENGTH}"), res.bodyAsText())
    }

    @Test
    fun `registro acepta exactamente el piso`() = testApplication {
        wireApp()
        val res = register("justa@movi.test", "a".repeat(PasswordPolicy.MIN_LENGTH))
        assertEquals(HttpStatusCode.Created, res.status)
    }

    @Test
    fun `registro rechaza por encima del maximo`() = testApplication {
        wireApp()
        val res = register("larga@movi.test", "a".repeat(PasswordPolicy.MAX_BYTES + 1))
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    // ── El techo de BCrypt se mide en BYTES ───────────────────────────────────
    //
    // `at.favre.lib:bcrypt` usa `LongPasswordStrategies.strict()`: por encima de 72 bytes UTF-8
    // **lanza** IllegalArgumentException, no trunca. Sin `StatusPages` instalado eso sale como
    // un 500 pelado. Estas contraseñas están por DEBAJO del techo si se cuentan caracteres y
    // por ENCIMA si se cuentan bytes, que es lo que cuenta la librería: son exactamente el caso
    // que el conteo por caracteres dejaba pasar hasta adentro de BCrypt.

    /** 65 caracteres, 73 bytes. Español con tildes: el caso real de esta app. */
    private val fraseLargaEnBytes = "mi contraseña es una frase larga con muchas tildes: á é í ó ú ñ ü"

    @Test
    fun `registro rechaza una frase que se pasa en bytes aunque no en caracteres`() = testApplication {
        wireApp()
        assertTrue(fraseLargaEnBytes.length <= PasswordPolicy.MAX_BYTES, "la trampa: por caracteres pasaba")
        assertTrue(PasswordPolicy.byteLength(fraseLargaEnBytes) > PasswordPolicy.MAX_BYTES)

        val res = register("frase@movi.test", fraseLargaEnBytes)
        assertEquals(HttpStatusCode.BadRequest, res.status, res.bodyAsText())
    }

    /**
     * El peor de los tres: `/login` es público y sin autenticar. Antes, cualquiera podía
     * disparar un 500 mandando una contraseña de más de 72 bytes. Ahora es el 401 de siempre.
     */
    @Test
    fun `login con una contrasena de mas de 72 bytes da 401 y no un 500`() = testApplication {
        wireApp()
        val res = login(legacyEmail, fraseLargaEnBytes)
        assertEquals(HttpStatusCode.Unauthorized, res.status, res.bodyAsText())

        val aunMasLarga = login(legacyEmail, "ñ".repeat(60))   // 120 bytes
        assertEquals(HttpStatusCode.Unauthorized, aunMasLarga.status)
    }

    /**
     * El rechazo por techo NO puede convertirse en un canal de enumeración: se evalúa antes de
     * mirar la base, así que un correo registrado y uno inventado contestan lo mismo.
     */
    @Test
    fun `el rechazo por contrasena demasiado larga no distingue correo registrado de desconocido`() = testApplication {
        wireApp()
        val registrado = login(legacyEmail, fraseLargaEnBytes)
        val desconocido = login("nadie-nunca@movi.test", fraseLargaEnBytes)
        assertEquals(registrado.status, desconocido.status)
        assertEquals(registrado.bodyAsText(), desconocido.bodyAsText())
    }

    @Test
    fun `el confirm tambien mide el techo en bytes`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()

        val res = confirmReset(token, fraseLargaEnBytes)
        assertEquals(HttpStatusCode.BadRequest, res.status, res.bodyAsText())
        // Y el token no se consumió: sigue sirviendo con una que sí entra.
        assertEquals(HttpStatusCode.OK, confirmReset(token, strongPassword).status)
    }

    /**
     * El caso que no se puede romper: el hash guardado de una contraseña vieja no se puede
     * re-evaluar contra el piso nuevo, así que el piso NO aplica al login. Si aplicara,
     * el único usuario real de la app quedaría afuera de sus propias finanzas.
     */
    @Test
    fun `un usuario con contrasena por debajo del piso sigue pudiendo entrar`() = testApplication {
        wireApp()
        assertTrue(legacyShortPassword.length < PasswordPolicy.MIN_LENGTH)
        val res = login(legacyEmail, legacyShortPassword)
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("token"))
    }

    // ── Login: enumeración por temporización ──────────────────────────────────

    /**
     * El oráculo que había: con el correo registrado el login corría BCrypt costo 12 (~290 ms
     * medidos en esta máquina) y con uno desconocido contestaba 401 al instante. Cualquiera con
     * un cronómetro separaba las cuentas que existen de las que no — dos órdenes de magnitud
     * más grande que los 2 ms que el endpoint de reset gasta 250 ms en tapar.
     *
     * El arreglo es una verificación **señuelo** contra un hash fijo del mismo costo, no un
     * `delay`: se hace el mismo trabajo, no se simula.
     */
    @Test
    fun `login desconocido y login con contrasena mala tardan lo mismo`() = testApplication {
        wireApp()
        val mala = "contrasena-que-no-es-la-suya"
        // Calentar LOS DOS caminos: la primera pasada por cada uno paga JIT y carga de clases,
        // y medir eso en vez del trabajo real inventa una diferencia que no existe.
        login(legacyEmail, mala)
        login("calentando@movi.test", mala)

        val tDesconocido = measure { login("no-existe-esta-cuenta@movi.test", mala) }
        val tConocido = measure { login(legacyEmail, mala) }
        // Se imprime a propósito: de un test de temporización el número es el resultado, y sin
        // esto solo se ve cuando falla. Medido acá: 3 ms vs 252 ms antes del señuelo,
        // 256 ms vs 250 ms después.
        println("[timing] login desconocido=$tDesconocido ms  conocido=$tConocido ms")

        // Que el camino "no existe" haya hecho trabajo REAL de BCrypt, no un retorno instantáneo.
        assertTrue(tDesconocido >= 100, "el correo desconocido contestó en $tDesconocido ms: no hubo verify señuelo")

        val brecha = kotlin.math.abs(tConocido - tDesconocido)
        assertTrue(
            brecha <= maxOf(tConocido, tDesconocido) / 2,
            "brecha de temporización: conocido=$tConocido ms, desconocido=$tDesconocido ms (dif=$brecha ms)",
        )
    }

    /**
     * El señuelo solo iguala si tiene el MISMO costo que los hashes reales. Si alguien sube
     * [BCRYPT_COST] y no regenera [DUMMY_PASSWORD_HASH], el camino "no existe" vuelve a ser más
     * barato y el oráculo se reabre en silencio. Este test es la alarma.
     */
    @Test
    fun `el hash senuelo tiene el mismo costo que los hashes reales`() {
        assertTrue(
            DUMMY_PASSWORD_HASH.startsWith("\$2a\$$BCRYPT_COST\$"),
            "el señuelo ($DUMMY_PASSWORD_HASH) no es un hash de costo $BCRYPT_COST",
        )
        // Y es un hash BCrypt de verdad: verifica (que no) sin explotar.
        assertFalse(BCrypt.verifyer().verify("lo-que-sea".toCharArray(), DUMMY_PASSWORD_HASH).verified)
    }

    // ── Pedido de reset: anti-enumeración ─────────────────────────────────────

    @Test
    fun `el pedido responde identico para un correo registrado y uno desconocido`() = testApplication {
        wireApp()
        val conocido = requestReset(legacyEmail)
        val desconocido = requestReset("nadie@movi.test")

        assertEquals(conocido.status, desconocido.status)
        assertEquals(HttpStatusCode.Accepted, conocido.status)
        assertEquals(conocido.bodyAsText(), desconocido.bodyAsText())
    }

    @Test
    fun `un correo desconocido no genera token ni correo`() = testApplication {
        wireApp()
        requestReset("nadie@movi.test")
        assertTrue(sentEmails.isEmpty())
        assertEquals(0L, transaction { PasswordResetTokens.selectAll().count() })
    }

    /**
     * El canal de temporización: el camino "registrado" hace escrituras que el otro no hace.
     * Ambos deben pasar el piso y ninguno puede dispararse por encima del otro.
     */
    @Test
    fun `los dos caminos tardan parecido — no hay oraculo de temporizacion`() = testApplication {
        wireApp()
        val tConocido = measure { requestReset(legacyEmail) }
        val tDesconocido = measure { requestReset("nadie@movi.test") }

        assertTrue(
            tConocido >= PasswordReset.REQUEST_FLOOR_MS && tDesconocido >= PasswordReset.REQUEST_FLOOR_MS,
            "piso no respetado: conocido=$tConocido ms, desconocido=$tDesconocido ms",
        )
        // El envío del correo sale del camino de la petición, así que la diferencia tiene que
        // quedar muy por debajo de lo que costaría un round-trip a Resend.
        assertTrue(
            kotlin.math.abs(tConocido - tDesconocido) < PasswordReset.REQUEST_FLOOR_MS,
            "diferencia demasiado grande: conocido=$tConocido ms, desconocido=$tDesconocido ms",
        )
    }

    @Test
    fun `el pedido esta rate-limitado`() = testApplication {
        wireApp()
        var got429 = false
        repeat(20) {
            if (requestReset(legacyEmail).status == HttpStatusCode.TooManyRequests) got429 = true
        }
        assertTrue(got429, "20 pedidos seguidos para el mismo correo no dispararon el rate limit")
    }

    @Test
    fun `el confirm esta rate-limitado`() = testApplication {
        wireApp()
        var got429 = false
        repeat(70) {
            if (confirmReset("token-inventado", strongPassword).status == HttpStatusCode.TooManyRequests) got429 = true
        }
        assertTrue(got429, "70 confirms seguidos no dispararon el rate limit")
    }

    // ── Rate limit: lo que protege y lo que NO ────────────────────────────────

    /**
     * El hueco que este test documenta —y fija— es de despliegue, no de código: sin
     * `ForwardedHeaders`/`XForwardedHeaders` instalado, `origin.remoteHost` es el peer TCP, que
     * detrás del borde de Railway es **una sola dirección para todo internet**. O sea: los
     * baldes con `$ip` en la clave NO aíslan por origen.
     *
     * Se afirma en la dirección incómoda a propósito: cambiar de `X-Forwarded-For` NO alcanza
     * para volver a pasar. Si algún día se instala forwarded-headers con una frontera de
     * confianza de verdad, este test se cae y hay que reescribirlo — que es exactamente el
     * aviso que se quiere.
     */
    @Test
    fun `el rate limit NO aisla por origen — X-Forwarded-For es inerte`() = testApplication {
        wireApp()
        repeat(10) { requestReset("victima@movi.test") }

        val desdeOtraIp = client.post("/api/auth/password-reset/request") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.XForwardedFor, "203.0.113.99")   // otra "IP": nadie la mira
            setBody("""{"email":"victima@movi.test"}""")
        }
        assertEquals(
            HttpStatusCode.TooManyRequests, desdeOtraIp.status,
            "cambiar X-Forwarded-For evitó el rate limit: alguien instaló forwarded-headers " +
                "sin frontera de confianza y el limitador ahora se saltea con una cabecera",
        )
    }

    /**
     * Antes login y register compartían la clave `$ip` con 10 intentos: diez logins fallidos
     * apagaban también el registro. Ahora cada uno tiene su balde.
     */
    @Test
    fun `agotar el balde de login no bloquea el registro`() = testApplication {
        wireApp()
        var loginBloqueado = false
        repeat(12) {
            if (login(legacyEmail, "contrasena-equivocada").status == HttpStatusCode.TooManyRequests) loginBloqueado = true
        }
        assertTrue(loginBloqueado, "12 logins fallidos no dispararon el rate limit del login")

        val res = register("nuevo-tras-el-429@movi.test", strongPassword)
        assertEquals(HttpStatusCode.Created, res.status, "el 429 del login se comió el registro")
    }

    /**
     * El balde estricto del reset es por correo, no global. Consecuencia buscada: agotar el de
     * una dirección **no** deja sin recuperación a las demás — que era el peor efecto del
     * esquema viejo (5 pedidos de cualquiera apagaban la recuperación para todo el mundo).
     *
     * Contracara, elegida a conciencia y anotada en `AuthRoutes.kt`: quemarle el balde a un
     * correo conocido le bloquea a esa persona el pedido durante la ventana.
     */
    @Test
    fun `agotar el reset de un correo no bloquea el de otro`() = testApplication {
        wireApp()
        var bloqueado = false
        repeat(8) {
            if (requestReset("uno@movi.test").status == HttpStatusCode.TooManyRequests) bloqueado = true
        }
        assertTrue(bloqueado, "8 pedidos para el mismo correo no dispararon su balde")

        assertEquals(
            HttpStatusCode.Accepted, requestReset("otro@movi.test").status,
            "el balde de un correo se comió el de otro: volvió a ser global",
        )
    }

    /** El 429 por correo depende de la cadena pedida, no de si existe: tampoco enumera. */
    @Test
    fun `el balde por correo se comporta igual para uno registrado y uno desconocido`() = testApplication {
        wireApp()
        val registrado = (1..8).map { requestReset(legacyEmail).status }
        RateLimiter.reset()
        val desconocido = (1..8).map { requestReset("nadie-de-nadie@movi.test").status }
        assertEquals(registrado, desconocido)
    }

    // ── El token en la DB ─────────────────────────────────────────────────────

    @Test
    fun `en la base se guarda el hash y nunca el token`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()
        assertTrue(token.isNotBlank())

        val stored = transaction { PasswordResetTokens.selectAll().single()[PasswordResetTokens.tokenHash] }
        assertNotEquals(token, stored)
        assertEquals(PasswordReset.hashToken(token), stored)
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    @Test
    fun `con un token valido se cambia la contrasena y la nueva sirve para entrar`() = testApplication {
        wireApp()
        val hashAntes = storedHashFor(legacyUserId)

        requestReset(legacyEmail)
        val res = confirmReset(tokenFromLastEmail(), strongPassword)
        assertEquals(HttpStatusCode.OK, res.status)

        assertNotEquals(hashAntes, storedHashFor(legacyUserId))
        assertEquals(HttpStatusCode.OK, login(legacyEmail, strongPassword).status)
    }

    @Test
    fun `tras el reset la contrasena vieja deja de servir`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        confirmReset(tokenFromLastEmail(), strongPassword)
        assertEquals(HttpStatusCode.Unauthorized, login(legacyEmail, legacyShortPassword).status)
    }

    @Test
    fun `un token ya usado no sirve dos veces`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()

        assertEquals(HttpStatusCode.OK, confirmReset(token, strongPassword).status)
        val segundo = confirmReset(token, "otra-contrasena-larga-igual")
        assertEquals(HttpStatusCode.BadRequest, segundo.status)
        // Y la contraseña quedó en la del PRIMER canje.
        assertEquals(HttpStatusCode.OK, login(legacyEmail, strongPassword).status)
    }

    @Test
    fun `un token vencido no sirve`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()
        transaction {
            PasswordResetTokens.update({ PasswordResetTokens.tokenHash eq PasswordReset.hashToken(token) }) {
                it[expiresAt] = System.currentTimeMillis() - 1
            }
        }
        assertEquals(HttpStatusCode.BadRequest, confirmReset(token, strongPassword).status)
        assertEquals(HttpStatusCode.OK, login(legacyEmail, legacyShortPassword).status)
    }

    @Test
    fun `un token manipulado o inventado no sirve`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()
        val manipulado = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'

        assertEquals(HttpStatusCode.BadRequest, confirmReset(manipulado, strongPassword).status)
        assertEquals(HttpStatusCode.BadRequest, confirmReset("no-existe-este-token", strongPassword).status)
        // La contraseña original sigue intacta.
        assertEquals(HttpStatusCode.OK, login(legacyEmail, legacyShortPassword).status)
    }

    @Test
    fun `el confirm aplica el mismo piso de contrasena que el registro`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()

        val res = confirmReset(token, "a".repeat(PasswordPolicy.MIN_LENGTH - 1))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("${PasswordPolicy.MIN_LENGTH}"), res.bodyAsText())
        // Y el token NO se consumió: sigue sirviendo con una contraseña que cumple.
        assertEquals(HttpStatusCode.OK, confirmReset(token, strongPassword).status)
    }

    @Test
    fun `una contrasena debil con un token invalido no revela que el token es invalido`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val valido = tokenFromLastEmail()
        val debil = "a".repeat(PasswordPolicy.MIN_LENGTH - 1)

        val conTokenValido = confirmReset(valido, debil)
        val conTokenBasura = confirmReset("token-que-no-existe", debil)
        assertEquals(conTokenValido.status, conTokenBasura.status)
        assertEquals(conTokenValido.bodyAsText(), conTokenBasura.bodyAsText())
    }

    @Test
    fun `un reset exitoso invalida los otros tokens pendientes del mismo usuario`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val primero = tokenFromLastEmail()
        requestReset(legacyEmail)
        val segundo = tokenFromLastEmail()
        assertNotEquals(primero, segundo)

        // Pedir uno nuevo ya invalida el anterior…
        assertEquals(HttpStatusCode.BadRequest, confirmReset(primero, strongPassword).status)
        // …y el nuevo funciona.
        assertEquals(HttpStatusCode.OK, confirmReset(segundo, strongPassword).status)
        assertTrue(transaction { PasswordResetTokens.selectAll().all { it[PasswordResetTokens.usedAt] != null } })
    }

    /**
     * Falla cerrado. Si la cuenta desaparece entre el pedido y el canje, el UPDATE toca cero
     * filas: no se cambió ninguna contraseña. Contestar 200 "tu contraseña quedó actualizada"
     * sería mentir en el único flujo donde la persona no tiene forma de verificarlo por su
     * cuenta — se iría a intentar entrar con una contraseña que nunca se guardó.
     */
    @Test
    fun `si el usuario ya no existe el confirm no dice que la contrasena quedo actualizada`() = testApplication {
        wireApp()
        requestReset(legacyEmail)
        val token = tokenFromLastEmail()

        transaction { Users.deleteWhere { Users.id eq legacyUserId } }

        val res = confirmReset(token, strongPassword)
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertFalse(res.bodyAsText().contains("actualizada"), res.bodyAsText())
    }

    @Test
    fun `el reset de un usuario no toca la contrasena de otro`() = testApplication {
        wireApp()
        register("otro@movi.test", strongPassword)
        val otroHashAntes = transaction {
            Users.selectAll().where { Users.email eq "otro@movi.test" }.single()[Users.passwordHash]
        }

        requestReset(legacyEmail)
        confirmReset(tokenFromLastEmail(), "contrasena-nueva-del-legacy")

        val otroHashDespues = transaction {
            Users.selectAll().where { Users.email eq "otro@movi.test" }.single()[Users.passwordHash]
        }
        assertEquals(otroHashAntes, otroHashDespues)
        assertEquals(HttpStatusCode.OK, login("otro@movi.test", strongPassword).status)
    }

    // ── RESEND_API_KEY ausente ────────────────────────────────────────────────

    /**
     * En producción la clave NO está puesta. Contestar 202 "revisá tu correo" mandaría a la
     * persona a esperar un mensaje que no existe, en el único flujo que tiene para recuperar
     * el acceso. Se contesta 503 — es una condición del servidor, igual para todo el mundo,
     * así que no filtra nada sobre qué correos están registrados.
     */
    @Test
    fun `sin RESEND_API_KEY el pedido responde 503 y no promete ningun correo`() = testApplication {
        System.clearProperty("movi.resend.apiKey")
        wireApp()
        val res = requestReset(legacyEmail)
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertTrue(sentEmails.isEmpty())
        assertEquals(0L, transaction { PasswordResetTokens.selectAll().count() })
    }

    @Test
    fun `sin RESEND_API_KEY la respuesta sigue siendo identica para registrado y desconocido`() = testApplication {
        System.clearProperty("movi.resend.apiKey")
        wireApp()
        val conocido = requestReset(legacyEmail)
        val desconocido = requestReset("nadie@movi.test")
        assertEquals(conocido.status, desconocido.status)
        assertEquals(conocido.bodyAsText(), desconocido.bodyAsText())
    }

    /**
     * Con la clave puesta pero Resend caído, el token YA se creó. No se puede desdecir sin
     * abrir un canal (un 502 solo para correos registrados sería un oráculo), así que la
     * respuesta sigue siendo el 202 genérico y el fallo queda en los logs.
     */
    @Test
    fun `si el envio falla la respuesta sigue siendo el 202 generico`() = testApplication {
        wireApp()
        mailerResult = false
        val res = requestReset(legacyEmail)
        assertEquals(HttpStatusCode.Accepted, res.status)
    }

    // ── Compatibilidad: nada de esto cambió la forma de las respuestas ────────

    @Test
    fun `register y login siguen devolviendo el mismo AuthResponse`() = testApplication {
        wireApp()
        val creado = register("forma@movi.test", strongPassword)
        assertEquals(HttpStatusCode.Created, creado.status)
        for (res in listOf(creado, login("forma@movi.test", strongPassword))) {
            val obj = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals(setOf("token", "userId", "name", "email"), obj.keys)
            assertTrue(obj["token"]!!.jsonPrimitive.content.isNotBlank())
            assertEquals("forma@movi.test", obj["email"]!!.jsonPrimitive.content)
        }
    }

    // ── utilidades ────────────────────────────────────────────────────────────

    private suspend fun measure(block: suspend () -> HttpResponse): Long {
        val start = System.currentTimeMillis()
        assertNotNull(block())
        return System.currentTimeMillis() - start
    }

    private companion object {
        /** El enlace del correo: href="…?reset=TOKEN". */
        val LINK_RE = Regex("""href="([^"]+)"""")

        /** El sender real, capturado antes de reemplazarlo, para restaurarlo al terminar. */
        var realSender: (suspend (String, String, String, String, String) -> Boolean)? = null
    }
}
