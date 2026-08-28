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
    fun `tres 401 seguidos cierran la sesion, y un exito corta la racha`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.onUnauthorized()
        SessionManager.onUnauthorized()
        SessionManager.onAuthSuccess()
        SessionManager.onUnauthorized()
        SessionManager.onUnauthorized()
        assertTrue(SessionManager.isLoggedIn, "dos 401 tras un éxito no pueden cerrar la sesión")

        SessionManager.onUnauthorized()
        assertFalse(SessionManager.isLoggedIn)
    }
}
