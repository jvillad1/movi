package com.jvillada.movi.data

import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * **El cliente HTTP de Android, el de verdad, contra un server local que contesta lo que se le pide.**
 *
 * El 26-oct la rotación de `JWT_SECRET` dejó muerto el token del teléfono y la app siguió pintando el
 * caché sin pedir entrar. Acá se monta [createHttpClient] —con su validador— y se afirma lo que el
 * dueño tiene que vivir: un 401 de Movi lo saca al primer pedido, y ni un 503 ni la red caída lo
 * sacan (Movi sigue funcionando sin conexión con lo guardado).
 */
@RunWith(RobolectricTestRunner::class)
class UnTokenRechazadoCierraLaSesionTest {

    private var codigo = 401
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val cuerpo = "nope".toByteArray()
            ex.sendResponseHeaders(codigo, cuerpo.size.toLong())
            ex.responseBody.use { it.write(cuerpo) }
        }
    }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before fun arrancar() {
        server.start()
        hostDeMoviDePrueba = "127.0.0.1"
        SessionManager.save("tok_vivo", "usr_1", "Juan", "juan@correo.com")
    }

    @After fun parar() {
        server.stop(0)
        hostDeMoviDePrueba = null
        SessionManager.clear()
    }

    private fun pedirCuentas(url: String = base) {
        runCatching { runBlocking { WalletRepositoryImpl(createHttpClient(), url).getAccounts() } }
    }

    @Test
    fun `un 401 de Movi cierra la sesion al primer pedido y el login sabe por que`() {
        pedirCuentas()
        assertFalse(SessionManager.loggedIn)
        assertFalse(SessionManager.isLoggedIn)
        assertTrue(SessionManager.sesionRechazada)
    }

    @Test
    fun `un 503 no cierra la sesion`() {
        codigo = 503
        repeat(3) { pedirCuentas() }
        assertTrue(SessionManager.isLoggedIn)
        assertFalse(SessionManager.sesionRechazada)
    }

    @Test
    fun `sin red la sesion sigue`() {
        // Un server que acepta y corta sin contestar: el cliente recibe una IOException, igual que
        // con el teléfono sin señal. (Un puerto cerrado no sirve: el cliente espera sus tiempos.)
        val mudo = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) { runCatching { while (true) mudo.accept().close() } }
        try {
            repeat(3) { pedirCuentas("http://127.0.0.1:${mudo.localPort}") }
        } finally {
            mudo.close()
        }
        assertTrue(SessionManager.isLoggedIn)
        assertFalse(SessionManager.sesionRechazada)
    }

    @Test
    fun `una contrasena equivocada no cierra nada`() {
        repeat(3) {
            runCatching { runBlocking { WalletRepositoryImpl(createHttpClient(), base).login(LoginRequest("a@b.c", "mala")) } }
        }
        assertTrue(SessionManager.isLoggedIn)
    }
}
