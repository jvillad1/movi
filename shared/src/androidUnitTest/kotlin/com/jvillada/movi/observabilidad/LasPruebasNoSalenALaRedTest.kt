package com.jvillada.movi.observabilidad

import com.jvillada.movi.AppDePrueba
import com.jvillada.movi.SelectorQueCierraMovi
import com.jvillada.movi.data.LaRedEstaCerradaEnLasPruebas
import com.jvillada.movi.data.RedDeLaApp
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/**
 * # La guarda: ninguna prueba de `:shared` sale a la red (vuelta 100)
 *
 * Las ráfagas de `401 GET /api/events/ev-hija/parecidos` y `/api/espacios` en los logs de Railway
 * eran de esta suite: una pantalla montada sin `Repositories.sustitutoDePrueba` construía el
 * repositorio de verdad y su cliente le pegaba a producción. Esto fija que no pueda volver a pasar:
 *
 * - toda prueba Robolectric corre con `AppDePrueba`, que cierra la red antes de cada método;
 * - con la red cerrada, el cliente de [createHttpClient] falla **antes de mandar nada**, también por
 *   el camino exacto de aquella vez (`WalletRepositoryImpl.getParecidos("ev-hija")`);
 * - y lo que abre `HttpURLConnection` a mano hacia el host de Movi va a un proxy muerto local.
 */
@RunWith(RobolectricTestRunner::class)
class LasPruebasNoSalenALaRedTest {

    @Test
    fun todaPruebaCorreConLaAppDePruebaYLaRedCerrada() {
        assertTrue("robolectric.properties dejó de declarar AppDePrueba", RuntimeEnvironment.getApplication() is AppDePrueba)
        assertTrue("AppDePrueba no cerró la red", RedDeLaApp.cerradaPorLasPruebas)
        assertTrue("AppDePrueba no instaló el ProxySelector", ProxySelector.getDefault() is SelectorQueCierraMovi)
    }

    @Test
    fun elClienteDeLaAppNoSaleAProduccion() = runBlocking {
        val cliente = createHttpClient()
        try {
            cliente.get("$apiBaseUrl/health")
            fail("el cliente salió a la red")
        } catch (e: LaRedEstaCerradaEnLasPruebas) {
            assertTrue(e.message.orEmpty().contains("/health"))
        } finally {
            cliente.close()
        }
    }

    @Test
    fun elCaminoDeEvHijaYaNoLlegaAlServer() = runBlocking {
        RedDeLaApp.frenados.clear()
        val repo = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)
        // `getParecidos` se traga su error (una oferta que no se pudo leer no se ofrece): lo que se
        // mira es que el pedido se frenó acá, no que haya fallado.
        assertTrue(repo.getParecidos("ev-hija").isEmpty())
        runCatching { repo.getEspacios() }
        assertTrue("el pedido de parecidos no pasó por la red cerrada: ${RedDeLaApp.frenados}", RedDeLaApp.frenados.any { "/api/events/ev-hija/parecidos" in it })
        assertTrue("el pedido de espacios no pasó por la red cerrada: ${RedDeLaApp.frenados}", RedDeLaApp.frenados.any { it.endsWith("/api/espacios") })
    }

    @Test
    fun httpUrlConnectionAMoviVaAUnProxyMuerto() {
        val selector = ProxySelector.getDefault()
        val aMovi = selector.select(URI("$apiBaseUrl/api/sms/sync")).single()
        assertEquals(Proxy.Type.HTTP, aMovi.type())
        assertEquals("127.0.0.1:1", aMovi.address().toString().substringAfter('/'))
        assertTrue(selector.select(URI("https://otra-cosa.up.railway.app/x")).single() == aMovi)
        // Lo demás (lo que Robolectric descarga) no se toca.
        assertTrue(selector.select(URI("https://repo.maven.apache.org/maven2/")).none { it == aMovi })
    }
}
