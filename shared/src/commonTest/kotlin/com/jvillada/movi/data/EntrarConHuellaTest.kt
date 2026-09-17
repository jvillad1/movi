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
 * # «Entrar con huella»: cuándo se pide, y las tres formas de salir mal
 *
 * El prompt biométrico no existe en la JVM, así que **esta es la única cobertura posible** de lo
 * que pasa cuando no sale bien: cancelar, quedarse sin huellas registradas, y un lector que falla.
 * Por eso la decisión vive en funciones puras (`EntrarConHuella.kt`) y no adentro de la pantalla.
 *
 * La que más importa de todo el archivo es [elTokenSigueDisponibleDeFondo]: es el motivo por el
 * que este diseño reemplazó al anterior.
 */
class EntrarConHuellaTest {

    @BeforeTest fun limpiarAntes() = SessionManager.clear()
    @AfterTest fun limpiarDespues() = SessionManager.clear()

    // ---------- Lo que el dueño eligió: la huella NO le cuesta la captura de SMS ----------

    @Test
    fun elTokenSigueDisponibleDeFondo() {
        // Con la huella prendida y todo.
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.huellaActivada = true

        // Esto es exactamente lo que hacen `SmsBackfill` (cada 6 horas) y el receptor en tiempo
        // real tras un reinicio: leer el token, sin pantalla, sin prompt, sin nadie delante del
        // teléfono. El diseño anterior cifraba el token con una llave que pedía un dedo, así que
        // acá devolvía null y los SMS del banco dejaban de subirse hasta que él abriera la app.
        assertEquals("tok_123", SessionManager.token, "un worker sin token no sube ningún SMS")
        assertTrue(SessionManager.isLoggedIn)
        // Y lo demas que el uploader manda con el mensaje.
        assertEquals("usr_1", SessionManager.userId)
    }

    @Test
    fun `prender y apagar el interruptor no toca la sesion`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")

        SessionManager.huellaActivada = true
        assertTrue(SessionManager.huellaActivada)
        assertEquals("tok_123", SessionManager.token)

        SessionManager.huellaActivada = false
        assertFalse(SessionManager.huellaActivada)
        assertEquals("tok_123", SessionManager.token, "apagar la huella no puede desloguear a nadie")
        assertTrue(SessionManager.isLoggedIn)
    }

    // ---------- Qué se interpone al abrir la app ----------

    @Test
    fun `con sesion y el interruptor prendido se pide la huella`() {
        assertEquals(
            ArranqueDeSesion.PEDIR_HUELLA,
            decidirArranque(haySesion = true, huellaActivada = true, estado = EstadoDeHuella.LISTA),
        )
    }

    @Test
    fun `sin el interruptor la app arranca como siempre`() {
        assertEquals(
            ArranqueDeSesion.SIN_PUERTA,
            decidirArranque(haySesion = true, huellaActivada = false, estado = EstadoDeHuella.LISTA),
        )
    }

    @Test
    fun `sin sesion no hay nada que tapar - el login ya es una puerta`() {
        assertEquals(
            ArranqueDeSesion.SIN_PUERTA,
            decidirArranque(haySesion = false, huellaActivada = true, estado = EstadoDeHuella.LISTA),
        )
    }

    @Test
    fun `sin huellas registradas se pide la contrasena, no un prompt que va a fallar`() {
        // El teléfono al que le borraron las huellas entre una apertura y la siguiente. No se
        // apaga el interruptor solo (sería bajarle la guardia sin avisar) ni se borra la sesión.
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(haySesion = true, huellaActivada = true, estado = EstadoDeHuella.SIN_REGISTRAR),
        )
        assertEquals(
            ArranqueDeSesion.PEDIR_CONTRASENA,
            decidirArranque(haySesion = true, huellaActivada = true, estado = EstadoDeHuella.NO_DISPONIBLE),
        )
    }

    // ---------- Qué se dice después del prompt ----------

    @Test
    fun `una huella correcta no tiene nada que explicar`() {
        assertNull(motivoDeLaHuella(ResultadoDeHuella.EXITO))
    }

    @Test
    fun `cancelar, sin huellas y una falla del lector dicen que hacer`() {
        assertEquals(MENSAJE_CANCELADA, motivoDeLaHuella(ResultadoDeHuella.CANCELADA))
        assertEquals(MENSAJE_SIN_REGISTRAR, motivoDeLaHuella(ResultadoDeHuella.SIN_REGISTRAR))
        assertEquals(MENSAJE_FALLA, motivoDeLaHuella(ResultadoDeHuella.FALLA))
        // Ninguno puede dejarlo sin salida: los tres nombran la contraseña, que es el camino que
        // siempre funciona.
        listOf(MENSAJE_CANCELADA, MENSAJE_SIN_REGISTRAR, MENSAJE_FALLA).forEach { mensaje ->
            assertTrue(mensaje.contains("contraseña"), "«$mensaje» no le dice cómo entrar")
        }
    }

    @Test
    fun `fallar la huella no le cuesta la sesion`() {
        // Es la diferencia entera con guardar el token bajo llave: acá la huella no abre nada, así
        // que ningún resultado puede costarle lo que ya tenía. Lo afirma el estado, no el mensaje.
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.huellaActivada = true

        ResultadoDeHuella.entries.forEach { resultado ->
            motivoDeLaHuella(resultado)
            assertEquals("tok_123", SessionManager.token, "$resultado tocó la sesión")
            assertTrue(SessionManager.huellaActivada, "$resultado apagó el interruptor")
        }
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

    @Test
    fun `el no del dueno sobrevive al logout, igual que su correo`() {
        SessionManager.huellaRechazada = true
        SessionManager.clear()

        assertTrue(SessionManager.huellaRechazada, "es una respuesta sobre este teléfono, no de la sesión")
        assertFalse(ofrecerHuellaTrasEntrar(EstadoDeHuella.LISTA, yaActivada = false, yaLoRechazo = true))

        // Y prenderla borra ese «no»: si lo prendió, quiere que se le ofrezca de nuevo si hiciera
        // falta más adelante.
        SessionManager.huellaActivada = true
        assertFalse(SessionManager.huellaRechazada)
    }

    // ---------- El token vencido ----------

    @Test
    fun `un token vencido no deja bucle - cerrar la sesion apaga el interruptor`() {
        SessionManager.save("tok_vencido", "usr_1", "Juan", "juan@correo.com")
        SessionManager.huellaActivada = true
        assertNotNull(SessionManager.token)

        // Lo que hace el servidor cuando el token de 30 días ya caducó: 401, 401, 401.
        SessionManager.onUnauthorized()
        SessionManager.onUnauthorized()
        SessionManager.onUnauthorized()

        assertFalse(SessionManager.isLoggedIn)
        assertFalse(
            SessionManager.huellaActivada,
            "quedaría pidiendo el dedo para abrir una app que igual va a rebotar al login",
        )
        // Y el arranque siguiente muestra el formulario directo, que es lo único que funciona.
        assertEquals(
            ArranqueDeSesion.SIN_PUERTA,
            decidirArranque(
                haySesion = SessionManager.isLoggedIn,
                huellaActivada = SessionManager.huellaActivada,
                estado = EstadoDeHuella.LISTA,
            ),
        )
    }
}
