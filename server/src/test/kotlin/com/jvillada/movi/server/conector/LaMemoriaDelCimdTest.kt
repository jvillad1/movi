package com.jvillada.movi.server.conector

import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 135 (N3 de la re-revisión) · Lo que se recuerda de un CIMD
 *
 * Con un reloj de prueba (`ahora`): el último documento bueno sigue ante una falla pasajera, solo un documento que
 * no sirve se recuerda como falla, y una cancelación de quien pide no se guarda ni deja colgados a los demás.
 */
class LaMemoriaDelCimdTest {

    private lateinit var c: AndamioDelConector
    private val minuto = 60_000L

    @BeforeTest
    fun setUp() {
        c = AndamioDelConector("conector_memoria_cimd")
    }

    @AfterTest
    fun tearDown() = c.cerrar()

    private val bueno get() = LecturaDelCimd.Documento(c.documentos.getValue(CIMD_DE_CLAUDE))

    @Test
    fun `una falla pasajera sigue usando el ultimo documento bueno`() = runBlocking {
        var lecturas = 0
        lectorDeCimd = LectorDeCimd { lecturas++; bueno }
        val t0 = 1_000_000L
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0))
        lectorDeCimd = LectorDeCimd { lecturas++; LecturaDelCimd.Pasajera }
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0 + 11 * minuto), "a los 11 min se pide de nuevo, falla, y sigue el bueno")
        assertEquals(2, lecturas)
    }

    @Test
    fun `solo un documento que no sirve se recuerda como falla`() = runBlocking {
        val t0 = 1_000_000L
        lectorDeCimd = LectorDeCimd { LecturaDelCimd.NoExiste }
        assertNull(resolverCliente(CIMD_DE_CLAUDE, t0))
        var lecturas = 0
        lectorDeCimd = LectorDeCimd { lecturas++; bueno }
        assertNull(resolverCliente(CIMD_DE_CLAUDE, t0 + minuto), "la falla se recuerda 5 min")
        assertEquals(0, lecturas)
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0 + 6 * minuto))
        assertEquals(1, lecturas)
    }

    @Test
    fun `cancelar a quien baja no guarda nada ni deja esperando a los demas`() = runBlocking {
        val compuerta = CompletableDeferred<Unit>()
        var lecturas = 0
        lectorDeCimd = LectorDeCimd { lecturas++; compuerta.await(); bueno }
        val primero = async { resolverCliente(CIMD_DE_CLAUDE, 1_000_000L) }
        delay(100)
        val segundo = async { resolverCliente(CIMD_DE_CLAUDE, 1_000_000L) }
        delay(100)
        primero.cancel()
        val antes = System.currentTimeMillis()
        withTimeout(1_000) { segundo.await() }
        assertTrue(System.currentTimeMillis() - antes < 1_000, "el que esperaba no esperó el tope entero")
        // Lo cancelado no quedó como falla: la próxima vez se baja y sirve.
        lectorDeCimd = LectorDeCimd { lecturas++; bueno }
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, 1_000_000L))
        assertEquals(2, lecturas)
    }

    @Test
    fun `un 404 o un HTML de la URL de Claude no borra el ultimo documento bueno`() = runBlocking {
        val t0 = 1_000_000L
        lectorDeCimd = LectorDeCimd { bueno }
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0))
        lectorDeCimd = LectorDeCimd { LecturaDelCimd.NoExiste }
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0 + 11 * minuto), "un 404 de claude.ai: sigue el bueno")
        lectorDeCimd = LectorDeCimd { LecturaDelCimd.Documento("<html>Cloudflare</html>") }
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0 + 22 * minuto), "una página HTML: sigue el bueno")
        // Y no se vuelve a pedir cada vez mientras tanto.
        var lecturas = 0
        lectorDeCimd = LectorDeCimd { lecturas++; bueno }
        assertNotNull(resolverCliente(CIMD_DE_CLAUDE, t0 + 23 * minuto))
        assertEquals(0, lecturas)
    }
}
