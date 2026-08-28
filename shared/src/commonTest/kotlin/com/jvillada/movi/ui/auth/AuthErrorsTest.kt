package com.jvillada.movi.ui.auth

import com.jvillada.movi.shared.repository.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **El bug que estos tests fijan**: `LoginScreen` mandaba cualquier fallo al mismo mensaje
 * («Correo o contraseña incorrectos»), así que con la red caída la app acusaba a una contraseña
 * que estaba bien.
 *
 * Lo que se prueba acá no es «que compile»: es que los tres finales distintos —te rechazaron, no
 * llegamos al servidor, el servidor se cayó— produzcan tres textos distintos, y que el único que
 * habla de la contraseña sea el 401.
 */
class AuthErrorsTest {

    // ── Clasificación ────────────────────────────────────────────────────────────────────

    @Test
    fun `un 401 es lo unico que se clasifica como credenciales`() {
        assertEquals(FalloDeAuth.CREDENCIALES, clasificarFalloDeAuth(ApiException(401, "Invalid credentials")))
        assertEquals(FalloDeAuth.DEMASIADOS_INTENTOS, clasificarFalloDeAuth(ApiException(429, "Demasiados intentos")))
        assertEquals(FalloDeAuth.MOTIVO_DEL_SERVIDOR, clasificarFalloDeAuth(ApiException(400, "Contraseña muy corta")))
        assertEquals(FalloDeAuth.MOTIVO_DEL_SERVIDOR, clasificarFalloDeAuth(ApiException(409, "Email already registered")))
        assertEquals(FalloDeAuth.SERVIDOR_FALLO, clasificarFalloDeAuth(ApiException(500, "<html>…</html>")))
        assertEquals(FalloDeAuth.SERVIDOR_FALLO, clasificarFalloDeAuth(ApiException(502, null)))
    }

    @Test
    fun `sin respuesta HTTP no hay codigo que mirar y eso es SIN_SERVIDOR`() {
        // Lo que llega en la vida real cuando no hay señal, cuando el DNS no resuelve, cuando el
        // servidor de Railway está dormido, o cuando el navegador no pudo hacer el fetch.
        assertEquals(FalloDeAuth.SIN_SERVIDOR, clasificarFalloDeAuth(RuntimeException("Unable to resolve host \"movi\"")))
        assertEquals(FalloDeAuth.SIN_SERVIDOR, clasificarFalloDeAuth(RuntimeException("Fail to fetch")))
        assertEquals(FalloDeAuth.SIN_SERVIDOR, clasificarFalloDeAuth(RuntimeException("Connection refused")))
        assertEquals(FalloDeAuth.SIN_SERVIDOR, clasificarFalloDeAuth(RuntimeException("SocketTimeoutException")))
        // Y también un fallo sin mensaje ninguno: no se sabe qué pasó, pero seguro no fue el
        // servidor diciendo «esa contraseña está mal».
        assertEquals(FalloDeAuth.SIN_SERVIDOR, clasificarFalloDeAuth(RuntimeException()))
    }

    // ── Login: lo que ve el dueño ────────────────────────────────────────────────────────

    @Test
    fun `solo el 401 habla de la contrasena`() {
        assertEquals(CREDENCIALES_RECHAZADAS, mensajeDeLogin(ApiException(401, "Invalid credentials")))
    }

    @Test
    fun `un 401 se contesta igual exista o no el correo`() {
        // El servidor manda el MISMO cuerpo en los dos casos (ver INVALID_CREDENTIALS en
        // AuthRoutes.kt). Este test fija que la pantalla tampoco los separe: distinguir «ese
        // correo no existe» de «la contraseña está mal» le regalaría a un atacante saber qué
        // direcciones tienen cuenta.
        val correoInexistente = ApiException(401, "Invalid credentials")
        val contrasenaMala = ApiException(401, "Invalid credentials")
        assertEquals(mensajeDeLogin(correoInexistente), mensajeDeLogin(contrasenaMala))
        assertEquals(CREDENCIALES_RECHAZADAS, mensajeDeLogin(correoInexistente))
    }

    @Test
    fun `sin conexion no se culpa a la contrasena`() {
        val texto = mensajeDeLogin(RuntimeException("Unable to resolve host \"movi-project-production\""))
        assertFalse(texto.contains("incorrect"), "no puede acusar a las credenciales: $texto")
        assertTrue(texto.contains("Sin conexión"), texto)
        assertTrue(texto.contains("No es tu contraseña."), texto)
    }

    @Test
    fun `el fetch fallido del navegador se lee como servidor inalcanzable`() {
        // Chrome dice "Failed to fetch", Safari "Load failed", Ktor/JS reenvía "Fail to fetch".
        // Antes ninguno de los tres estaba mapeado y caían en el saco genérico.
        for (mensaje in listOf("Fail to fetch", "Failed to fetch", "Load failed", "NetworkError when attempting to fetch resource")) {
            val texto = mensajeDeLogin(RuntimeException(mensaje))
            assertTrue(texto.contains("No se pudo conectar al servidor"), "$mensaje -> $texto")
            assertTrue(texto.contains("No es tu contraseña."), "$mensaje -> $texto")
        }
    }

    @Test
    fun `un 500 dice que fallo el servidor y tampoco culpa a la contrasena`() {
        val texto = mensajeDeLogin(ApiException(500, "<html>Application failed to respond</html>"))
        assertTrue(texto.contains("Error en el servidor"), texto)
        assertTrue(texto.contains("No es tu contraseña."), texto)
        // Y NO se le muestra el HTML del proxy: `toUserMessage` solo lee el cuerpo de un 4xx.
        assertFalse(texto.contains("html"), texto)
    }

    @Test
    fun `los tres finales dan tres textos distintos`() {
        val credenciales = mensajeDeLogin(ApiException(401, "Invalid credentials"))
        val sinServidor = mensajeDeLogin(RuntimeException("Unable to resolve host"))
        val servidorFallo = mensajeDeLogin(ApiException(503, null))
        assertEquals(3, setOf(credenciales, sinServidor, servidorFallo).size,
            "los tres tienen que leerse distinto: $credenciales / $sinServidor / $servidorFallo")
    }

    @Test
    fun `un 429 dice que hay que esperar, no que reintente`() {
        val texto = mensajeDeLogin(ApiException(429, "Demasiados intentos, espera unos minutos"))
        assertEquals(DEMASIADOS_INTENTOS_AUTH, texto)
    }

    @Test
    fun `un 4xx que no es 401 tampoco culpa a la contrasena`() {
        // El caso real: un proxy mal apuntado devolviendo 404. Antes se leía «Recurso no
        // encontrado.» a secas, al lado del campo de contraseña.
        val texto = mensajeDeLogin(ApiException(404, null))
        assertTrue(texto.contains("No es tu contraseña."), texto)
    }

    @Test
    fun `el cuerpo de un 5xx no puede reclasificar el error por lo que diga adentro`() {
        // `toUserMessage` clasificaba buscando subcadenas dentro del mensaje de la excepción, que
        // incluye el CUERPO. El HTML de error de un proxy que diga «Not Found» en cualquier parte
        // hacía que un 500 se leyera «Recurso no encontrado.».
        val texto = mensajeDeLogin(ApiException(502, "<html><h1>404 Not Found</h1><p>Unauthorized</p></html>"))
        assertTrue(texto.contains("Error en el servidor"), texto)
        assertFalse(texto.contains("Recurso no encontrado"), texto)
        assertFalse(texto.contains("Sesión expirada"), texto)
    }

    @Test
    fun `el Invalid credentials en ingles del servidor nunca se muestra tal cual`() {
        // 401 cae dentro del rango 400..422 que `toUserMessage` contesta con el cuerpo del
        // servidor. Si el mapeo del login se apoyara solo en esa función, el dueño leería
        // «Invalid credentials» en inglés.
        assertFalse(mensajeDeLogin(ApiException(401, "Invalid credentials")).contains("Invalid"))
    }

    // ── Registro ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `el correo ya tomado se detecta por el codigo, no buscando 409 en el texto`() {
        val texto = mensajeDeRegistro(ApiException(409, "Email already registered"))
        assertTrue(texto.contains("ya tiene una cuenta"), texto)
        assertFalse(texto.contains("Email already registered"), texto)
    }

    @Test
    fun `un 400 de registro muestra el motivo que escribio el servidor`() {
        // Es el mensaje de PasswordPolicy, que es la autoridad sobre el mínimo real.
        val texto = mensajeDeRegistro(ApiException(400, "La contraseña debe tener al menos 12 caracteres"))
        assertEquals("La contraseña debe tener al menos 12 caracteres", texto)
    }

    @Test
    fun `un 401 registrando tampoco filtra el ingles del servidor`() {
        // 401 cae dentro del rango 400..422 que `toUserMessage` contesta con el cuerpo. Sin una
        // rama propia, registrarse habría impreso «Invalid credentials».
        val texto = mensajeDeRegistro(ApiException(401, "Invalid credentials"))
        assertFalse(texto.contains("Invalid"), texto)
        assertTrue(texto.contains("crear la cuenta"), texto)
    }

    @Test
    fun `si no se llego al servidor, el registro dice que la cuenta no se creo`() {
        val texto = mensajeDeRegistro(RuntimeException("Fail to fetch"))
        assertTrue(texto.contains("No se pudo conectar al servidor"), texto)
        assertTrue(texto.contains("La cuenta no se creó."), texto)
    }

    // ── Recuperación de contraseña ───────────────────────────────────────────────────────

    @Test
    fun `pedir el enlace sin red no afirma que el problema fue conectarse cuando no lo fue`() {
        // Antes SIEMPRE decía «No se pudo conectar. Prueba de nuevo.», incluso cuando el
        // servidor sí había contestado.
        assertTrue(mensajeDeRecuperacion(RuntimeException("Unable to resolve host")).contains("Sin conexión"))
        assertEquals(DEMASIADOS_INTENTOS_AUTH, mensajeDeRecuperacion(ApiException(429, null)))
        assertEquals("Correo inválido", mensajeDeRecuperacion(ApiException(400, "Correo inválido")))
        // Misma trampa del 401 dentro de 400..422 que en el registro.
        assertFalse(mensajeDeRecuperacion(ApiException(401, "Invalid credentials")).contains("Invalid"))
    }
}
