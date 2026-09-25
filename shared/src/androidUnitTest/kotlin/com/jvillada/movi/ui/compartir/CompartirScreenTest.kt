package com.jvillada.movi.ui.compartir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    /**
     * Sin enlaces, el vacío que enseña: título y detalle, SIN acción propia (Ola D, pulido).
     *
     * Antes traía su propio «Crear un enlace», que quedaba justo debajo del «Crear enlace» de la
     * sección de arriba — a quien recién llega, sin ningún enlace todavía, la pantalla le mostraba
     * dos botones que hacen exactamente lo mismo al mismo tiempo. El vacío ahora solo explica; la
     * única puerta para crear un enlace es la de arriba.
     */
    @Test
    fun `sin enlaces, el vacio que ensena sin boton propio`() {
        montar()
        esperarTexto("No tienes enlaces activos")

        assertTrue(hay("Los que crees aparecerán aquí hasta que venzan o los revoques."))
        assertTrue(!hay("Crear un enlace"), "el vacío no puede traer una segunda acción idéntica a la de arriba")
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
