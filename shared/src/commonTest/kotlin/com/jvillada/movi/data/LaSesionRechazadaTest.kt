package com.jvillada.movi.data

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La regla de [laSesionTras]: solo un 401 de Movi, con el token guardado, cierra la sesión. Lo que
 * se rompió el 26-oct era lo contrario — un token muerto que nunca cerraba nada —, y lo que no se
 * puede romper arreglándolo es que sin red Movi siga funcionando con lo guardado.
 */
class LaSesionRechazadaTest {

    private val movi = "movi.example"
    private fun en(ruta: String) = Url("https://$movi$ruta")

    private fun tras(codigo: Int?, url: Url = en("/api/accounts"), delPedido: String? = "tok", guardado: String? = "tok") =
        laSesionTras(codigo, url, delPedido, guardado, hostDeMovi = movi)

    @Test
    fun `un 401 de Movi con el token guardado cierra la sesion`() {
        assertEquals(LaSesionTras.SE_CERRO, tras(401))
        assertEquals(LaSesionTras.SE_CERRO, tras(401, en("/api/users/me")))
        assertEquals(LaSesionTras.SE_CERRO, tras(401, en("/api/sms/sync")))
    }

    @Test
    fun `sin red la sesion sigue - Movi funciona con lo guardado`() {
        assertEquals(LaSesionTras.SIGUE, tras(null))
    }

    @Test
    fun `un 5xx u otro error del server no es una sesion vencida`() {
        listOf(500, 502, 503, 504, 403, 404, 422, 429).forEach { codigo ->
            assertEquals(LaSesionTras.SIGUE, tras(codigo), "el $codigo no puede cerrar la sesión")
        }
        assertEquals(LaSesionTras.SIGUE, tras(200))
    }

    @Test
    fun `el 401 de otro host no habla de la sesion de Movi`() {
        assertEquals(LaSesionTras.SIGUE, tras(401, Url("https://api.otro.com/api/accounts")))
    }

    @Test
    fun `el 401 del login es una contrasena equivocada, no una sesion vencida`() {
        assertEquals(LaSesionTras.SIGUE, tras(401, en("/api/auth/login")))
    }

    @Test
    fun `un pedido sin token o con el token de antes no cierra la sesion de ahora`() {
        assertEquals(LaSesionTras.SIGUE, tras(401, delPedido = null))
        // Salió con el token viejo y volvió después de que el dueño entró de nuevo.
        assertEquals(LaSesionTras.SIGUE, tras(401, delPedido = "viejo", guardado = "nuevo"))
        // La sesión ya se cerró por otro pedido: no hay nada más que cerrar.
        assertEquals(LaSesionTras.SIGUE, tras(401, guardado = null))
    }

    @Test
    fun `el host se compara sin importar mayusculas`() {
        assertEquals(LaSesionTras.SE_CERRO, laSesionTras(401, Url("https://MOVI.example/api/accounts"), "t", "t", hostDeMovi = movi))
    }

    @Test
    fun `el token sale del encabezado Bearer`() {
        assertEquals("abc", tokenDelEncabezado("Bearer abc"))
        assertNull(tokenDelEncabezado(null))
        assertNull(tokenDelEncabezado("Basic abc"))
        assertNull(tokenDelEncabezado("Bearer "))
    }

    @Test
    fun `la linea del login nombra la captura solo donde la hay`() {
        assertNull(avisoDeLaSesionRechazada(rechazada = false, conCapturaDelBanco = true))
        assertTrue(avisoDeLaSesionRechazada(true, conCapturaDelBanco = true)!!.contains("mensajes del banco"))
        assertEquals("Tu sesión se cerró. Entra de nuevo.", avisoDeLaSesionRechazada(true, conCapturaDelBanco = false))
    }
}
