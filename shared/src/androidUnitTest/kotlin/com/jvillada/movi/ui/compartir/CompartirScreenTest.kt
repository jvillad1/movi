package com.jvillada.movi.ui.compartir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.EnlaceCompartido
import com.jvillada.movi.shared.model.EnlaceCompartidoCreado
import com.jvillada.movi.shared.model.NuevoEnlaceCompartido
import com.jvillada.movi.shared.repository.CompartirRepository
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * # «Compartir», con la costura nueva de Ola D, Task 2
 *
 * Hasta esta tarea `CompartirScreen` no tenía ni una prueba: `Repositories.compartir` apuntaba
 * siempre al cliente HTTP real (ver el KDoc de [CompartirRepository]), así que no había forma de
 * montarla con datos de verdad sin señal. Acá se prueba solo el vacío que enseña — el resto de la
 * pantalla (crear, revocar) queda para otra tanda.
 */
@RunWith(RobolectricTestRunner::class)
class CompartirScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private open inner class Repo(private val enlaces: List<EnlaceCompartido> = emptyList()) : CompartirRepository {
        override suspend fun listar(): List<EnlaceCompartido> = enlaces
        override suspend fun crear(pedido: NuevoEnlaceCompartido): EnlaceCompartidoCreado =
            error("esta prueba no esperaba crear un enlace")
        override suspend fun revocar(id: String) = error("esta prueba no esperaba revocar un enlace")
    }

    private fun montar(repo: CompartirRepository = Repo()) {
        Repositories.sustitutoDeCompartirDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CompartirScreen(onNavigate = {}) } }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDeCompartirDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
    }

    /** Sin enlaces, el vacío que enseña: título, detalle y el botón que crea uno. */
    @Test
    fun `sin enlaces, el vacio que ensena con su boton`() {
        montar()
        esperarTexto("No tienes enlaces activos")

        assertTrue(hay("Los que crees aparecerán aquí hasta que venzan o los revoques."))
        assertTrue(hay("Crear un enlace"))
    }

    /** Tocar el botón del vacío crea un enlace — la MISMA acción que la sección de arriba. */
    @Test
    fun `tocar Crear un enlace del vacio crea el enlace`() {
        var creado = false
        montar(object : Repo() {
            override suspend fun crear(pedido: NuevoEnlaceCompartido): EnlaceCompartidoCreado {
                creado = true
                return EnlaceCompartidoCreado(
                    enlace = EnlaceCompartido(id = "e1", creadoEn = 0L, venceEn = 1L),
                    ruta = "/compartido#token",
                )
            }
        })
        esperarTexto("Crear un enlace")

        // El botón del vacío queda debajo del pliegue (la explicación de arriba es larga): sin
        // `performScrollTo()` el toque cae fuera del recorte visible y no dispara nada.
        composeRule.onNodeWithText("Crear un enlace", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(creado, "tocar el botón del vacío tiene que llamar a crear()")
    }

    /** Mientras la lectura está en vuelo, no se afirma que no hay enlaces. */
    @Test
    fun `mientras la lectura esta en vuelo, no hay vacio`() {
        val puerta = CompletableDeferred<List<EnlaceCompartido>>()
        montar(object : Repo() {
            override suspend fun listar(): List<EnlaceCompartido> = puerta.await()
        })
        composeRule.waitForIdle()

        assertTrue(!hay("No tienes enlaces activos"))
    }

    /** Con un enlace ya creado, no hay vacío que enseñar. */
    @Test
    fun `con un enlace activo, no aparece el vacio que ensena`() {
        montar(Repo(enlaces = listOf(EnlaceCompartido(id = "e1", creadoEn = 0L, venceEn = 9_999_999_999_999L))))
        composeRule.waitForIdle()

        assertTrue(!hay("No tienes enlaces activos"))
    }
}
