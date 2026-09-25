package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_VACIO_QUE_ENSENA
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Presupuestos vacío propone presupuestos con lo que ya se gastó
 *
 * Sin ningún presupuesto, el vacío que enseña deja de pedir una cifra inventada: trae las categorías
 * de más gasto del período pasado (`GET /api/budgets/propuestas`), marcadas, y las crea con un toque
 * por el mismo POST de siempre. Si las propuestas no llegan, queda el vacío de siempre con su
 * «Nuevo presupuesto»: una sugerencia caída no puede bloquear crear a mano.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]`: ver `CreditosNoAfirmanMientrasCarganTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PropuestasDePresupuestoTest {

    @get:Rule val composeRule = createComposeRule()

    private val propuestas = listOf(
        PropuestaDePresupuesto("Mercado", 1_050_000.0, 1_020_000.0, "2026-07-25", "2026-08-24"),
        PropuestaDePresupuesto("Comida", 820_000.0, 820_000.0, "2026-07-25", "2026-08-24"),
        PropuestaDePresupuesto("Fútbol", 100_000.0, 99_001.0, "2026-07-25", "2026-08-24"),
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * Un repositorio sin presupuestos que devuelve lo que ya se creó (así, al recargar después de
     * crear, la pantalla ve los nuevos) y deja fallar el POST de las categorías de [fallanCon] con
     * ese código HTTP. Con [recargaFalla], toda lectura de presupuestos después de la primera falla.
     */
    private inner class SinPresupuestos(
        private val lasPropuestas: suspend () -> List<PropuestaDePresupuesto> = { propuestas },
        var fallanCon: Map<String, Int> = emptyMap(),
        private val recargaFalla: Boolean = false,
        private val yaTiene: List<Budget> = emptyList(),
    ) : RepositorioDePrueba() {
        val creados = mutableListOf<Budget>()
        val intentos = mutableListOf<Budget>()
        var lecturasDePropuestas = 0
        private var lecturasDePresupuestos = 0
        override suspend fun getBudgets(): List<Budget> {
            if (recargaFalla && lecturasDePresupuestos++ > 0) throw ApiException(503)
            return yaTiene + creados
        }
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = DashboardSummary(scope = Scope.SELF)
        override suspend fun getPropuestasDePresupuesto(): List<PropuestaDePresupuesto> {
            lecturasDePropuestas++
            return lasPropuestas()
        }
        override suspend fun createBudget(budget: Budget): Budget {
            intentos += budget
            fallanCon[budget.category]?.let { throw ApiException(it) }
            creados += budget
            return budget
        }
    }

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `con propuestas dice cuantas y de que fechas, y las trae marcadas`() {
        montar(SinPresupuestos())

        assertTrue(hay("Movi te propone 3 a partir de lo que gastaste del 25 de julio al 24 de agosto."))
        assertTrue(hay("Gastaste \$1.020.000 · tope \$1.050.000"))
        assertTrue(hay("Gastaste \$99.001 · tope \$100.000"))
        propuestas.forEach { composeRule.onNodeWithTag(tagDePropuestaDePresupuesto(it.category)).assertIsOn() }
        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).assertIsEnabled()
        assertTrue(hay("Crear estos 3"))
        assertTrue(!hay("No pudimos crear"))
        assertTrue(hay("Crear uno a mano"))
        assertTrue(!hay("Nuevo presupuesto"), "con propuestas, crear a mano es la acción secundaria")
    }

    @Test
    fun `Crear estos N crea las marcadas con su tope y la pantalla pasa a la lista`() {
        val repo = SinPresupuestos()
        montar(repo)

        composeRule.onNodeWithTag(tagDePropuestaDePresupuesto("Comida")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDePropuestaDePresupuesto("Comida")).assertIsOff()
        assertTrue(hay("Crear estos 2"), "desmarcar baja la cuenta")
        composeRule.onNodeWithTag(tagDePropuestaDePresupuesto("Mercado")).performClick()
        composeRule.waitForIdle()
        assertTrue(hay("Crear este presupuesto"), "con una sola, en singular: nada de «Crear estos 1»")
        composeRule.onNodeWithTag(tagDePropuestaDePresupuesto("Mercado")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Budget("Mercado", 1_050_000L), Budget("Fútbol", 100_000L)), repo.intentos)
        assertTrue(hay("Gastado en"), "con presupuestos creados, la pantalla ya no está vacía")
        assertTrue(!hay("Movi te propone"))
    }

    @Test
    fun `con todas desmarcadas el boton queda apagado`() {
        montar(SinPresupuestos())

        propuestas.forEach {
            composeRule.onNodeWithTag(tagDePropuestaDePresupuesto(it.category)).performClick()
            composeRule.waitForIdle()
        }

        assertTrue(hay("Crear estos 0"))
        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).assertIsNotEnabled()
    }

    @Test
    fun `Crear uno a mano abre la misma hoja de siempre`() {
        montar(SinPresupuestos())

        composeRule.onNodeWithText("Crear uno a mano", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(hay("Guardar"), "la hoja de alta de «Nuevo presupuesto»")
    }

    /**
     * Si una falla, las creadas quedan (la pantalla ya muestra la lista) y se dice cuál no, con
     * «Reintentar» que crea solo esa.
     */
    @Test
    fun `si una falla, las otras quedan y se dice cual fallo, con reintento`() {
        val repo = SinPresupuestos(fallanCon = mapOf("Comida" to 503))
        montar(repo)

        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Mercado", "Fútbol"), repo.creados.map { it.category })
        assertTrue(hay("No pudimos crear el presupuesto de Comida"))
        assertTrue(hay("Gastado en"))

        repo.fallanCon = emptyMap()
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Mercado", "Fútbol", "Comida"), repo.creados.map { it.category })
        assertTrue(!hay("No pudimos crear"))
    }

    /**
     * El POST de Comida se guardó pero la respuesta se perdió: el reintento encuentra el
     * presupuesto hecho y el server contesta 409. Eso es «creado», no una falla que nombrar.
     */
    @Test
    fun `un 409 al reintentar cuenta como creado y el aviso se va`() {
        val repo = SinPresupuestos(fallanCon = mapOf("Comida" to 503))
        montar(repo)
        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).performClick()
        composeRule.waitForIdle()
        assertTrue(hay("No pudimos crear el presupuesto de Comida"))

        repo.fallanCon = mapOf("Comida" to 409)
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(Budget("Comida", 820_000L), repo.intentos.last(), "el reintento sí se mandó")
        assertTrue(!hay("No pudimos crear"), "no puede decir que no creó un presupuesto que existe")
    }

    /**
     * Mercado y Fútbol se crean, Comida no, y la recarga de después también falla (la misma red
     * mala): el vacío sigue. Ahí solo puede ofrecer Comida, y el segundo toque solo manda Comida.
     */
    @Test
    fun `si la recarga falla, el segundo toque manda solo las que faltan`() {
        val repo = SinPresupuestos(fallanCon = mapOf("Comida" to 503), recargaFalla = true)
        montar(repo)
        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).performClick()
        composeRule.waitForIdle()

        assertTrue(hay("Movi te propone 1 a partir"), "el vacío sigue, con la que falta")
        assertEquals(0, composeRule.onAllNodesWithTag(tagDePropuestaDePresupuesto("Mercado")).fetchSemanticsNodes().size)
        assertTrue(hay("Crear este presupuesto"))

        repo.fallanCon = emptyMap()
        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Mercado", "Comida", "Fútbol", "Comida"), repo.intentos.map { it.category })
    }

    /**
     * Si no entra ninguna, el aviso va adentro de la tarjeta y arriba del botón que se acaba de
     * tocar: con cuatro propuestas la tarjeta es alta, y un aviso debajo de ella quedaba fuera de
     * la pantalla.
     */
    @Test
    fun `si no entra ninguna, el aviso queda en la tarjeta, junto al boton`() {
        montar(SinPresupuestos(fallanCon = propuestas.associate { it.category to 503 }))
        composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).performClick()
        composeRule.waitForIdle()

        val aviso = composeRule.onNodeWithText("No pudimos crear los presupuestos de Mercado, Comida y Fútbol")
            .getUnclippedBoundsInRoot()
        val tarjeta = composeRule.onNodeWithTag(TAG_VACIO_QUE_ENSENA).getUnclippedBoundsInRoot()
        val boton = composeRule.onNodeWithTag(TAG_CREAR_PROPUESTAS).getUnclippedBoundsInRoot()
        assertTrue(aviso.top >= tarjeta.top && aviso.bottom <= tarjeta.bottom, "adentro de la tarjeta")
        assertTrue(aviso.bottom <= boton.top, "arriba del botón")
    }

    /** A quien ya tiene presupuestos, las propuestas no le cuestan ni una llamada. */
    @Test
    fun `con presupuestos no se piden las propuestas`() {
        val repo = SinPresupuestos(yaTiene = listOf(Budget("Comida", 1_000_000L)))
        montar(repo)

        assertTrue(hay("Gastado en"))
        assertEquals(0, repo.lecturasDePropuestas)
    }

    @Test
    fun `si las propuestas no se pueden leer queda el vacio de siempre, sin error de pantalla`() {
        montar(SinPresupuestos(lasPropuestas = { throw ApiException(503) }))

        assertTrue(hay("Ponle un tope a lo que más gastas"))
        assertTrue(hay("Nuevo presupuesto"))
        assertTrue(!hay("Movi te propone"))
        assertTrue(!hay("No pudimos"))
    }

    /**
     * Mientras las propuestas vienen en camino se ve el vacío simple, y al llegar la tarjeta crece
     * UNA vez, hacia abajo: el título no se mueve (nada arriba de él cambia) y no hay un tercer
     * estado intermedio. Se eligió esto y no reservar el alto porque no se sabe cuántas propuestas
     * vienen (de cero a cuatro), y un hueco reservado que después queda vacío es otro salto.
     */
    @Test
    fun `mientras cargan las propuestas se ve el vacio simple y al llegar la tarjeta solo crece hacia abajo`() {
        val puerta = CompletableDeferred<List<PropuestaDePresupuesto>>()
        montar(SinPresupuestos(lasPropuestas = { puerta.await() }))

        assertTrue(hay("Nuevo presupuesto"))
        val antes = composeRule.onNodeWithTag(TAG_VACIO_QUE_ENSENA).getUnclippedBoundsInRoot()
        val tituloAntes = composeRule.onNodeWithText("Ponle un tope a lo que más gastas").getUnclippedBoundsInRoot()

        puerta.complete(propuestas)
        composeRule.waitForIdle()

        val despues = composeRule.onNodeWithTag(TAG_VACIO_QUE_ENSENA).getUnclippedBoundsInRoot()
        val tituloDespues = composeRule.onNodeWithText("Ponle un tope a lo que más gastas").getUnclippedBoundsInRoot()
        assertEquals(antes.top, despues.top, "la tarjeta no se corre")
        assertEquals(tituloAntes.top, tituloDespues.top, "el título no se corre")
        assertTrue(despues.bottom > antes.bottom, "crece hacia abajo, con las propuestas")
    }
}
