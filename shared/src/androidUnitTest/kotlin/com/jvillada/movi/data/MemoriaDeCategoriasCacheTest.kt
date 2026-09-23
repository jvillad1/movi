package com.jvillada.movi.data

import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 5, fix round 1 — **el defecto que hacía que ninguna sugerencia posterior a un guardado
 * llegara en toda la sesión.**
 *
 * `QuickAddScreen` relanzaba la carga con `coroutine.launch { MemoriaDeCategoriasCache.recargar() }`
 * sobre el `rememberCoroutineScope()` de la propia hoja, un par de líneas antes de `onSaved()` —que
 * es lo que hace que `App.kt` la saque de composición y cancele ese mismo scope. La petición
 * moría a mitad de camino, y como `runCatching` atrapaba la `CancellationException` como una falla
 * más, `cargada` quedaba en `false`... pero la hoja SIGUIENTE también se cerraba apenas guardaba,
 * así que el patrón se repetía indefinidamente. Ver el KDoc de [MemoriaDeCategoriasCache] para la
 * historia completa.
 *
 * Corre con Robolectric (no un JUnit pelado) por el mismo motivo que el resto de la suite: sin
 * `AppDePrueba` limpiando antes de cada método, este `object` traería la resaca de la prueba
 * anterior del mismo fork.
 */
@RunWith(RobolectricTestRunner::class)
class MemoriaDeCategoriasCacheTest {

    private val unRecuerdo = RecuerdoDeCategoria(
        huella = "nombre:morasoccer", categoria = "Fútbol", nombre = "Mora Soccer", cuantos = 1,
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun cargarSiHaceFalta_es_idempotente_mientras_nadie_invalide() = runBlocking {
        var llamadas = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> {
                llamadas++
                return listOf(unRecuerdo)
            }
        }

        MemoriaDeCategoriasCache.cargarSiHaceFalta() // primera hoja
        MemoriaDeCategoriasCache.cargarSiHaceFalta() // la misma hoja, o una que abrió sin guardar

        assertEquals("no debería haber pedido dos veces sin que nadie invalide", 1, llamadas)
    }

    /** El caso central del fix: guardar invalida, y la hoja siguiente vuelve a pedir. */
    @Test
    fun una_segunda_apertura_despues_de_guardar_vuelve_a_llamar_al_repositorio() = runBlocking {
        var llamadas = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> {
                llamadas++
                return listOf(unRecuerdo)
            }
        }

        MemoriaDeCategoriasCache.cargarSiHaceFalta() // primera hoja de «Agregar»
        assertEquals(1, llamadas)

        MemoriaDeCategoriasCache.invalidar() // guardó un movimiento — síncrono, sin red
        MemoriaDeCategoriasCache.cargarSiHaceFalta() // segunda hoja de «Agregar»

        assertEquals("la segunda apertura tenía que volver a preguntarle al repositorio", 2, llamadas)
    }

    /**
     * La otra mitad del defecto: si el guardado (y su `invalidar()`) llega MIENTRAS la primera
     * carga todavía viaja, la respuesta que esa carga trae cuando por fin contesta es una foto de
     * ANTES de guardar — no puede dejar `cargada` en `true`, o la memoria del movimiento recién
     * anotado se perdería para el resto de la sesión.
     */
    @Test
    fun invalidar_a_mitad_de_una_carga_en_vuelo_tambien_deja_todo_listo_para_la_proxima() = runBlocking {
        var llamadas = 0
        val primeraRespuesta = CompletableDeferred<List<RecuerdoDeCategoria>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> {
                llamadas++
                return if (llamadas == 1) primeraRespuesta.await() else listOf(unRecuerdo)
            }
        }

        val primeraApertura = launch { MemoriaDeCategoriasCache.cargarSiHaceFalta() }
        yield() // deja que la corrutina llegue a esperar la respuesta del server

        MemoriaDeCategoriasCache.invalidar() // el guardado ocurre MIENTRAS la primera viaja
        primeraRespuesta.complete(listOf(unRecuerdo)) // la primera por fin contesta, tarde
        primeraApertura.join()

        MemoriaDeCategoriasCache.cargarSiHaceFalta() // la hoja siguiente

        assertEquals("la invalidación en vuelo tenía que forzar una carga más", 2, llamadas)
    }
}
