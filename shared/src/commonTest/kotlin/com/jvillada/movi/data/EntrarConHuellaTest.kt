package com.jvillada.movi.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Entrar con huella», las cuatro formas de salir mal
 *
 * El prompt biométrico no existe en la JVM, así que **esta es la única cobertura posible** de lo
 * que pasa cuando no sale bien: cancelar, quedarse sin huellas registradas, que cambien las
 * huellas del teléfono, y un token que el servidor ya no acepta. Por eso la decisión vive en
 * funciones puras (`EntrarConHuella.kt`) y no adentro de la pantalla.
 *
 * Lo que estas pruebas NO cubren, dicho para que nadie las lea como cobertura del rasgo entero:
 * el cifrado real contra el Keystore de Android y el diálogo del sistema. Eso solo se puede ver
 * en un teléfono.
 */
class EntrarConHuellaTest {

    @BeforeTest fun limpiarAntes() = SessionManager.clear()
    @AfterTest fun limpiarDespues() = SessionManager.clear()

    // ---------- Qué hacer al arrancar ----------

    @Test
    fun `con la sesion abierta no se pide nada`() {
        assertEquals(
            ArranqueDeSesion.ENTRAR_DIRECTO,
            decidirArranque(
                sesionViva = true,
                huellaActivada = true,
                haySesionGuardada = true,
                estado = EstadoDeHuella.LISTA,
            ),
        )
    }

    @Test
    fun `con la huella activada y algo guardado se pide la huella`() {
        assertEquals(
            ArranqueDeSesion.PEDIR_HUELLA,
            decidirArranque(
                sesionViva = false,
                huellaActivada = true,
                haySesionGuardada = true,
                estado = EstadoDeHuella.LISTA,
            ),
        )
    }

    @Test
    fun `sin huellas registradas se pide la contrasena, no un prompt que va a fallar`() {
        // El caso del teléfono al que le borraron las huellas entre una apertura y la siguiente.
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(
                sesionViva = false,
                huellaActivada = true,
                haySesionGuardada = true,
                estado = EstadoDeHuella.SIN_REGISTRAR,
            ),
        )
    }

    @Test
    fun `el interruptor prendido sin nada guardado no pide huella`() {
        // Es el estado en el que queda un teléfono tras un logout si el interruptor sobreviviera:
        // pedir la huella para abrir una caja vacía sería un bucle garantizado.
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(
                sesionViva = false,
                huellaActivada = true,
                haySesionGuardada = false,
                estado = EstadoDeHuella.LISTA,
            ),
        )
    }

    @Test
    fun `sin la huella activada la app entra como siempre`() {
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(
                sesionViva = false,
                huellaActivada = false,
                haySesionGuardada = true,
                estado = EstadoDeHuella.LISTA,
            ),
        )
    }

    @Test
    fun `en la web y en iOS nunca se pide huella`() {
        // `NO_DISPONIBLE` es lo que devuelven las plataformas sin lector, y también un Android
        // sin hardware biométrico.
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(
                sesionViva = false,
                huellaActivada = true,
                haySesionGuardada = true,
                estado = EstadoDeHuella.NO_DISPONIBLE,
            ),
        )
    }

    // ---------- Qué pasa después del prompt ----------

    @Test
    fun `una huella correcta suelta la sesion y no borra nada`() {
        val guardada = SesionGuardada("tok_123", "usr_1", "Juan", "juan@correo.com")
        val que = quePasaTrasLaHuella(ResultadoDeHuella.EXITO, guardada)

        assertEquals(guardada, que.sesion)
        assertFalse(que.olvidarLoGuardado)
        assertNull(que.mensaje, "entrar bien no tiene nada que explicar")
    }

    @Test
    fun `cancelar deja todo como estaba y lo dice`() {
        val que = quePasaTrasLaHuella(ResultadoDeHuella.CANCELADA, null)

        assertNull(que.sesion)
        assertFalse(que.olvidarLoGuardado, "cancelar no puede costarle la sesión guardada")
        assertEquals(MENSAJE_CANCELADA, que.mensaje)
    }

    @Test
    fun `quedarse sin huellas registradas olvida lo guardado`() {
        val que = quePasaTrasLaHuella(ResultadoDeHuella.SIN_REGISTRAR, null)

        assertNull(que.sesion)
        assertTrue(que.olvidarLoGuardado)
        assertEquals(MENSAJE_SIN_REGISTRAR, que.mensaje)
    }

    @Test
    fun `una llave invalidada olvida lo guardado - es lo unico que corta el bucle`() {
        // Android invalida la llave cuando cambian las huellas del teléfono. Lo cifrado ya no se
        // puede abrir NUNCA: dejarlo ahí sería ofrecer una huella inútil en cada arranque.
        val que = quePasaTrasLaHuella(ResultadoDeHuella.LLAVE_INVALIDA, null)

        assertTrue(que.olvidarLoGuardado)
        assertEquals(MENSAJE_LLAVE_INVALIDA, que.mensaje)
    }

    @Test
    fun `un fallo pasajero del lector no borra la sesion guardada`() {
        val que = quePasaTrasLaHuella(ResultadoDeHuella.FALLA, null)

        assertFalse(que.olvidarLoGuardado, "un lector ocupado no es motivo para perder lo guardado")
        assertEquals(MENSAJE_FALLA, que.mensaje)
    }

    @Test
    fun `un exito sin sesion adentro se trata como llave rota`() {
        // Descifró, pero lo que salió no se pudo leer. No hay otra causa posible, y seguir como
        // si nada dejaría al dueño mirando un prompt que "funciona" y no entra.
        val que = quePasaTrasLaHuella(ResultadoDeHuella.EXITO, null)

        assertNull(que.sesion)
        assertTrue(que.olvidarLoGuardado)
        assertEquals(MENSAJE_LLAVE_INVALIDA, que.mensaje)
    }

    // ---------- Cuándo se ofrece ----------

    @Test
    fun `se ofrece solo si el aparato puede, no esta activada y no dijo que no`() {
        assertTrue(ofrecerHuellaTrasEntrar(EstadoDeHuella.LISTA, yaActivada = false, yaLoRechazo = false))
        assertFalse(ofrecerHuellaTrasEntrar(EstadoDeHuella.LISTA, yaActivada = true, yaLoRechazo = false))
        assertFalse(ofrecerHuellaTrasEntrar(EstadoDeHuella.LISTA, yaActivada = false, yaLoRechazo = true))
        assertFalse(ofrecerHuellaTrasEntrar(EstadoDeHuella.SIN_REGISTRAR, yaActivada = false, yaLoRechazo = false))
        assertFalse(ofrecerHuellaTrasEntrar(EstadoDeHuella.NO_DISPONIBLE, yaActivada = false, yaLoRechazo = false))
    }

    // ---------- Lo que se guarda, y lo que se olvida ----------

    @Test
    fun `activar guarda lo cifrado y prende el interruptor`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.activarHuella(iv = "00112233445566778899aabb", datos = "deadbeef")

        assertTrue(SessionManager.huellaActivada)
        assertEquals("00112233445566778899aabb" to "deadbeef", SessionManager.sesionBajoLlave)
        // La sesión de ESTE momento no se corta: el dueño no se queda afuera por activarlo.
        assertEquals("tok_123", SessionManager.token)
        assertTrue(SessionManager.isLoggedIn)
    }

    @Test
    fun `apagar el interruptor olvida lo cifrado y no cierra la sesion`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.activarHuella(iv = "aabb", datos = "ccdd")
        SessionManager.desactivarHuella()

        assertFalse(SessionManager.huellaActivada)
        assertNull(SessionManager.sesionBajoLlave)
        assertEquals("tok_123", SessionManager.token, "apagar la huella no puede desloguear a nadie")
    }

    @Test
    fun `un token vencido no deja bucle - cerrar la sesion olvida la caja fuerte`() {
        SessionManager.save("tok_vencido", "usr_1", "Juan", "juan@correo.com")
        SessionManager.activarHuella(iv = "aabb", datos = "ccdd")
        assertNotNull(SessionManager.sesionBajoLlave)

        // Lo que hace el servidor cuando el token de 30 días ya caducó: 401, 401, 401.
        SessionManager.onUnauthorized()
        SessionManager.onUnauthorized()
        SessionManager.onUnauthorized()

        assertFalse(SessionManager.isLoggedIn)
        assertFalse(SessionManager.huellaActivada, "quedaría un interruptor prendido sin nada que abrir")
        assertNull(SessionManager.sesionBajoLlave, "abrir esto soltaría el MISMO token muerto")
        // Y el arranque siguiente pide la contraseña, que es lo único que puede funcionar.
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(
                sesionViva = SessionManager.isLoggedIn,
                huellaActivada = SessionManager.huellaActivada,
                haySesionGuardada = SessionManager.sesionBajoLlave != null,
                estado = EstadoDeHuella.LISTA,
            ),
        )
    }

    @Test
    fun `el no del dueno sobrevive al logout, igual que su correo`() {
        SessionManager.huellaRechazada = true
        SessionManager.clear()

        assertTrue(SessionManager.huellaRechazada, "es una respuesta sobre este teléfono, no de la sesión")
        assertFalse(ofrecerHuellaTrasEntrar(EstadoDeHuella.LISTA, yaActivada = false, yaLoRechazo = true))

        // Y activarla desde Perfil borra ese "no": si lo prendió, quiere que se le ofrezca de nuevo
        // la próxima vez que haga falta.
        SessionManager.activarHuella(iv = "aabb", datos = "ccdd")
        assertFalse(SessionManager.huellaRechazada)
    }
}
