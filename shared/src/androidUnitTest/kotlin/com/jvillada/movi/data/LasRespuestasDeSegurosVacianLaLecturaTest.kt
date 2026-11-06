package com.jvillada.movi.data

import com.jvillada.movi.shared.model.SegurosQuePagas
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 132 · «Tus seguros» se lee una vez, y cada respuesta del dueño la vuelve vieja
 *
 * Cuenta las peticiones de `getSeguros` que llegan al repositorio de abajo, con el envoltorio de la app
 * ([InvalidaElInicioAlEscribir] sobre [LecturasSinRepetir], como `Repositories.paraLaApp`) y un reloj y un
 * usuario fijos. Dos lecturas seguidas salen una vez; después de «Sí», «No», «¿Cada cuánto?» o «Me interesa»,
 * la siguiente vuelve a salir. Sin la línea de `getSeguros` en [LecturasSinRepetir] falla la primera
 * afirmación; sin el `trasEscribir` de cada respuesta, la de esa respuesta.
 */
class LasRespuestasDeSegurosVacianLaLecturaTest {

    private class Abajo : RepositorioDePrueba() {
        var lecturas = 0
        override suspend fun getSeguros(enLaPantalla: Boolean): SegurosQuePagas { lecturas++; return SegurosQuePagas(alMes = 121_100) }
        override suspend fun responderSiEsUnSeguro(clave: String, esUnSeguro: Boolean): SegurosQuePagas = SegurosQuePagas()
        override suspend fun cadaCuantoSePagaElSeguro(clave: String, meses: Int): SegurosQuePagas = SegurosQuePagas()
        override suspend fun meInteresaLaAyudaConLosSeguros() {}
    }

    private val abajo = Abajo()
    private val repo = InvalidaElInicioAlEscribir(
        LecturasSinRepetir(
            abajo,
            LecturasCompartidas(reloj = { 1_000_000L }, generacion = { CacheDeLecturas.generacion }, usuario = { "u1" }),
        ),
    )

    private fun leerDosVeces() = runBlocking {
        repo.getSeguros()
        repo.getSeguros()
    }

    @Test
    fun `dos lecturas seguidas salen una sola vez`() {
        leerDosVeces()
        assertEquals(1, abajo.lecturas)
    }

    @Test
    fun `si y no vuelven vieja la lectura`() {
        leerDosVeces()
        runBlocking { repo.responderSiEsUnSeguro("acc_ahorros:sura_eps", esUnSeguro = true) }
        leerDosVeces()
        assertEquals(2, abajo.lecturas, "tras «Sí»")
        runBlocking { repo.responderSiEsUnSeguro("acc_ahorros:sura_eps", esUnSeguro = false) }
        leerDosVeces()
        assertEquals(3, abajo.lecturas, "tras «No»")
    }

    @Test
    fun `cada cuanto y me interesa vuelven vieja la lectura`() {
        leerDosVeces()
        runBlocking { repo.cadaCuantoSePagaElSeguro("acc_ahorros:seguros_bolivar", 12) }
        leerDosVeces()
        assertEquals(2, abajo.lecturas, "tras «¿Cada cuánto?»")
        runBlocking { repo.meInteresaLaAyudaConLosSeguros() }
        leerDosVeces()
        assertEquals(3, abajo.lecturas, "tras «Me interesa»")
    }
}
