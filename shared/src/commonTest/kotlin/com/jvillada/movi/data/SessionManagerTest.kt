package com.jvillada.movi.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El arranque, que es lo que puede dejar al dueño afuera de su propia app.**
 *
 * `SessionManager` leía `auth_token` **en el inicializador de su `object`**, sin protección. Una
 * excepción ahí no es un error que se maneja: se lleva puesta la clase entera y todo lo que
 * dependa de ella, que en Movi es cada pantalla. Con el almacenamiento del sitio bloqueado en el
 * navegador, `Settings()` es `StorageSettings(localStorage)` y tocar `window.localStorage` tira
 * `SecurityError` **al construirse**. Mismo final que ya había tenido `AppTimeZone` pidiéndole a
 * wasm una zona IANA que no tiene.
 *
 * Este archivo corre en la misma JVM donde `LastAccountStoreTest` dejó anotado que `Settings()` no
 * se puede construir (no hay contexto de Android ni almacenamiento de navegador). O sea que el
 * solo hecho de que estos tests corran ya prueba lo primero: **tocar este object no tumba nada**.
 * Antes de este arreglo, el `ExceptionInInitializerError` se llevaba puesto el archivo entero.
 *
 * Y prueban lo segundo, que es lo que hace que el arreglo sirva de algo: sin ningún lado donde
 * anotar el token, entrar tiene que seguir funcionando **durante la sesión**. Si `save()` no
 * dejara rastro, la app cargaría solo para rebotar en el login para siempre.
 *
 * **Lo que este archivo NO cubre, para que nadie lo lea como cobertura completa.** Justamente
 * porque acá `Settings()` nunca se construye, todo lo de abajo ejercita **solo el camino
 * degradado** (la copia en memoria). El camino normal —almacenamiento que funciona, sesión que
 * sobrevive a un reinicio, la lectura pasante que llena la copia— no lo toca ninguna prueba
 * automática: se verificó a ojo en el navegador, con `localStorage` sano y con `Storage.prototype`
 * roto a mitad de sesión. Cubrirlo acá pediría un `Settings` falso inyectable, que es un cambio de
 * diseño mucho más grande que el arreglo.
 */
class SessionManagerTest {

    @BeforeTest fun limpiarAntes() = SessionManager.clear()
    @AfterTest fun limpiarDespues() = SessionManager.clear()

    @Test
    fun `leer la sesion no explota aunque no haya donde guardarla`() {
        // Cada una de estas lecturas pasa por `leer()`. Sin el `runCatching`, la primera se
        // llevaba puesto el object.
        assertNull(SessionManager.token)
        assertNull(SessionManager.userId)
        assertNull(SessionManager.userName)
        assertNull(SessionManager.userEmail)
        assertNull(SessionManager.avatarColor)
        assertFalse(SessionManager.isLoggedIn)
        assertFalse(SessionManager.loggedIn)
    }

    @Test
    fun `entrar deja sesion utilizable aunque el almacenamiento no acepte nada`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")

        // Lo que hace falta para que el próximo pedido salga con `Authorization` y la app no
        // rebote de vuelta al login.
        assertEquals("tok_123", SessionManager.token)
        assertEquals("usr_1", SessionManager.userId)
        assertEquals("Juan", SessionManager.userName)
        assertEquals("juan@correo.com", SessionManager.userEmail)
        assertTrue(SessionManager.isLoggedIn)
        assertTrue(SessionManager.loggedIn)
    }

    @Test
    fun `cerrar sesion borra las credenciales`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.clear()

        assertNull(SessionManager.token)
        assertNull(SessionManager.userId)
        assertNull(SessionManager.userName)
        assertNull(SessionManager.userEmail)
        assertFalse(SessionManager.isLoggedIn)
        assertFalse(SessionManager.loggedIn)
    }

    @Test
    fun `el correo recordado sobrevive al logout`() {
        // «Recordar mi correo en este dispositivo» sin la preferencia en "0" (el caso de
        // Android/iOS, que no tienen la casilla) conserva el correo para prellenar el login.
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.clear()
        assertEquals("juan@correo.com", SessionManager.rememberedEmail)
    }

    @Test
    fun `escribir un campo suelto se lee de vuelta`() {
        // El color del avatar lo escribe PerfilScreen después del login; si no se pudiera leer
        // de vuelta, el avatar parpadearía al color por defecto en cada recomposición.
        SessionManager.avatarColor = "azul"
        assertEquals("azul", SessionManager.avatarColor)
        SessionManager.avatarColor = null
        assertNull(SessionManager.avatarColor)
    }

    @Test
    fun `el 401 del propio login NO cuenta como sesion vencida`() {
        // El bug: tres contraseñas equivocadas seguidas disparaban `clear()`, y en la web eso
        // recarga la página. Los campos quedaban vacíos y sin ningún mensaje — una pantalla en
        // blanco, justo cuando la persona ya estaba dudando de su contraseña.
        assertFalse(cuentaComoSesionVencida("/api/auth/login"))
        assertFalse(cuentaComoSesionVencida("/api/auth/register"))
        assertFalse(cuentaComoSesionVencida("/api/auth/password-reset/request"))
        assertFalse(cuentaComoSesionVencida("https://movi.example/api/auth/login?x=1"))
    }

    @Test
    fun `todo lo demas si cuenta - un token que caduco tiene que cerrar la sesion`() {
        assertTrue(cuentaComoSesionVencida("/api/accounts"))
        assertTrue(cuentaComoSesionVencida("/api/dashboard/summary"))
        assertTrue(cuentaComoSesionVencida("/api/users/me"))
        // Ojo con relajar la regla a "contiene auth": esta ruta no existe hoy, pero si existiera
        // sería un pedido autenticado normal y su 401 SÍ tendría que contar.
        assertTrue(cuentaComoSesionVencida("/api/sms/authors"))
    }

    @Test
    fun `un token rechazado cierra la sesion al primer 401 y el login sabe por que`() {
        // El 26-oct: JWT_SECRET rotado, y la app de Android siguió pintando el caché con un token
        // muerto. Un 401 de Movi con el token guardado es definitivo — ver `laSesionTras`.
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.seRechazoElToken("tok_123")

        assertFalse(SessionManager.isLoggedIn)
        assertFalse(SessionManager.loggedIn)
        assertTrue(SessionManager.sesionRechazada)
        assertEquals("usr_1", SessionManager.sesionRechazadaDe)
    }

    @Test
    fun `el 401 de un pedido con el token de antes no cierra la sesion de ahora`() {
        SessionManager.save("tok_nuevo", "usr_1", "Juan", "juan@correo.com")
        SessionManager.seRechazoElToken("tok_viejo")
        SessionManager.seRechazoElToken(null)

        assertTrue(SessionManager.isLoggedIn)
        assertFalse(SessionManager.sesionRechazada)
    }

    @Test
    fun `entrar de nuevo apaga el aviso y avisa a la plataforma`() {
        var entradas = 0
        SessionManager.alEntrar = { entradas++ }
        try {
            SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
            assertEquals(0, entradas, "sin una sesión rechazada antes, no hay nada esperando")

            SessionManager.seRechazoElToken("tok_123")
            SessionManager.save("tok_456", "usr_1", "Juan", "juan@correo.com")

            assertFalse(SessionManager.sesionRechazada)
            assertEquals(1, entradas)
        } finally {
            SessionManager.alEntrar = null
        }
    }

    @Test
    fun `salir a mano no cuenta como sesion rechazada`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.seRechazoElToken("tok_123")
        SessionManager.clear()
        assertFalse(SessionManager.sesionRechazada, "si el dueño salió, no hay nada que contarle")
    }

    @Test
    fun `cerrar por el server avisa a la plataforma una vez`() {
        var avisos = 0
        SessionManager.alRechazarseLaSesion = { avisos++ }
        try {
            SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
            SessionManager.seRechazoElToken("tok_123")
            // Los demás pedidos que vuelvan 401 con ese token ya no tienen sesión que cerrar.
            SessionManager.seRechazoElToken("tok_123")
            assertEquals(1, avisos)
        } finally {
            SessionManager.alRechazarseLaSesion = null
        }
    }
}
