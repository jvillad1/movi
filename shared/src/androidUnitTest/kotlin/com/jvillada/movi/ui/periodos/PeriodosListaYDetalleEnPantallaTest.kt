package com.jvillada.movi.ui.periodos

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDeCategoria
import com.jvillada.movi.shared.model.PAGO_FIJO_PENDIENTE
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.ui.Screen
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Tus períodos» en lista + detalle (Ola W2), montada y medida
 *
 * Con la clase de ancho que le toca de verdad a cada ventana ([ConClaseDeAncho]) y la letra ×1,12
 * de la app. Se mide con `boundsInRoot`, nunca con capturas: `captureToImage` se cuelga en
 * Robolectric. `sdk = [34]` y `NATIVE`: el texto se mide con el motor real (en el SDK 24 el texto
 * con interlineado mide ancho cero).
 *
 * El período en curso sale del perfil y del reloj de verdad (corte 25), así que los doce períodos
 * de la prueba se arman hacia atrás desde el vigente de hoy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PeriodosListaYDetalleEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val perfil = UserProfile(
        id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = 25,
    )
    private val vigente: PeriodoFinanciero =
        periodoActual(Clock.System.now().toEpochMilliseconds(), PeriodSettings(cutoffDay = 25))

    /** Del vigente hacia atrás: doce períodos, más de los que caben en la ventana. */
    private val periodos: List<ResumenDePeriodo> =
        generateSequence(vigente) { periodoAnterior(it) }.take(12).mapIndexed { i, p ->
            ResumenDePeriodo(
                id = p.prefijo, nombre = tituloDelPeriodo(p),
                desde = "${p.prefijo}-01", hasta = "${p.prefijo}-28",
                enCurso = i == 0, entradas = 1_000_000L, salidas = 400_000L, movimientos = 3,
            )
        }.toList()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    /** Lo que solo el detalle de [resumen] dice: el gasto más grande lleva su nombre. */
    private fun gastoDe(resumen: ResumenDePeriodo) = "Gasto de ${resumen.nombre}"

    private fun detalleDe(resumen: ResumenDePeriodo) = DetalleDePeriodo(
        resumen = resumen,
        porCategoria = listOf(GastoDeCategoria("Mercado", 400_000L)),
        pagosFijos = listOf(
            PagoFijoDelPeriodo("r1", "Arriendo", 1_500_000L, esIngreso = false, vencimiento = "${resumen.id}-05",
                estado = PAGO_FIJO_PENDIENTE),
        ),
        masGrandes = listOf(
            FinancialEvent(
                id = "ev-${resumen.id}", accountId = banco.id, type = TransactionType.EXPENSE, amount = 400_000L,
                category = "Mercado", description = gastoDe(resumen), timestamp = 1_790_000_000_000L,
                reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
            ),
        ),
    )

    private inner class ConPeriodos : RepositorioDePrueba() {
        var lecturasDelPerfil = 0
        val lecturasDelDetalle = mutableListOf<String>()
        override suspend fun getUserProfile(): UserProfile {
            lecturasDelPerfil++
            return perfil
        }
        override suspend fun getPeriodos(): List<ResumenDePeriodo> = periodos
        override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo {
            lecturasDelDetalle += id
            return detalleDe(periodos.first { it.id == id })
        }
        override suspend fun getAccounts(): List<Account> = listOf(banco)
    }

    private val navegado = mutableListOf<Screen>()

    private fun montar(idPedido: String? = null): ConPeriodos {
        val repo = ConPeriodos()
        navegado.clear()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            ConClaseDeAncho { PeriodosListaYDetalle(onNavigate = { navegado += it }, idPedido = idPedido) }
        }
        composeRule.waitForIdle()
        return repo
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
    }

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun fila(resumen: ResumenDePeriodo): SemanticsNodeInteraction =
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText(resumen.nombre)), useUnmergedTree = true)

    // ── Escritorio ──────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp la lista y el detalle se ven a la vez, el detalle a la derecha`() {
        montar()
        val enCurso = periodos.first()
        esperar(gastoDe(enCurso))

        // Arranca en el período en curso, con la lista al lado.
        assertTrue(hay(periodos[1].nombre), "la lista se ve")
        val lista = limites(TAG_PANEL_DE_LA_LISTA)
        val detalle = limites(TAG_PANEL_DEL_DETALLE)
        assertTrue(detalle.left >= lista.right, "el detalle empieza donde termina la lista: $lista / $detalle")
        assertEquals(400f, lista.width, 0.5f)
        assertTrue(detalle.width > 800f, "el detalle se lleva lo que sobra: ${detalle.width}")
        // Al lado de la lista no hay a dónde volver: el detalle no lleva flecha. La lista sí.
        assertEquals(1, flechasDeVolver(), "solo la flecha de la lista")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `un id pedido gana la seleccion, y tocar otra fila la cambia sin navegar`() {
        val agosto = periodos[1]
        val julio = periodos[2]
        val repo = montar(idPedido = agosto.id)
        esperar(gastoDe(agosto))
        assertTrue(!hay(gastoDe(periodos.first())), "no arranca en el en curso")

        fila(julio).performClick()
        esperar(gastoDe(julio))

        assertTrue(navegado.isEmpty(), "no se navegó: $navegado")
        assertTrue(!hay(gastoDe(agosto)))
        assertEquals(listOf(agosto.id, julio.id), repo.lecturasDelDetalle)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `el perfil se lee una sola vez para la lista y el detalle`() {
        val repo = montar()
        esperar(gastoDe(periodos.first()))
        fila(periodos[1]).performClick()
        esperar(gastoDe(periodos[1]))

        assertEquals(1, repo.lecturasDelPerfil)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `con el panel de 840 dp o mas las dos columnas del detalle van una al lado de la otra`() {
        montar()
        esperar(gastoDe(periodos.first()))

        assertTrue(limites(TAG_PANEL_DEL_DETALLE).width >= 840f)
        val izquierda = limites(TAG_COLUMNA_IZQUIERDA_DEL_DETALLE)
        val derecha = limites(TAG_COLUMNA_DERECHA_DEL_DETALLE)
        assertTrue(derecha.left >= izquierda.right, "$izquierda / $derecha")
        assertEquals(izquierda.top, derecha.top, 0.5f)
        // La cabecera del período a la izquierda; los pagos fijos, a la derecha.
        val arriendo = composeRule.onAllNodesWithText("Arriendo", useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        assertTrue(arriendo.left >= derecha.left)
        // Las acciones van abajo, a lo ancho.
        val accion = limites(TAG_VER_MOVIMIENTOS_DEL_PERIODO)
        assertTrue(accion.top >= maxOf(izquierda.bottom, derecha.bottom))
        assertTrue(accion.width >= izquierda.width + derecha.width)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `cambiar de fila actualiza el panel sin perder el scroll de la lista`() {
        montar()
        esperar(gastoDe(periodos.first()))
        val lejano = periodos[10]
        composeRule.onNodeWithTag(TAG_LISTA_DE_PERIODOS, useUnmergedTree = true)
            .performScrollToNode(hasText(lejano.nombre))
        composeRule.waitForIdle()
        val antes = fila(lejano).fetchSemanticsNode().boundsInRoot

        fila(lejano).performClick()
        esperar(gastoDe(lejano))

        val despues = fila(lejano).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(antes.top - despues.top) < 0.5f, "la lista no se movió: $antes / $despues")
        // El primero de la lista sigue fuera de la vista (la lista no volvió arriba).
        val primera = composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(periodos.first().nombre)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(primera.isEmpty() || primera.single().boundsInRoot.bottom <= limites(TAG_PANEL_DE_LA_LISTA).top + 1f)
    }

    // ── Teléfono ────────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 dp tocar una fila navega al detalle como siempre`() {
        montar()
        esperar(periodos[1].nombre)
        assertTrue(!hayTag(TAG_PANEL_DEL_DETALLE), "en el teléfono no hay panel de detalle")

        fila(periodos[1]).performClick()

        assertEquals(Screen.DetalleDePeriodo(periodos[1].id), navegado.single())
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 dp con un id pedido se ve el detalle de siempre, con su flecha`() {
        montar(idPedido = periodos[1].id)
        esperar(gastoDe(periodos[1]))
        assertTrue(!hayTag(TAG_PANEL_DE_LA_LISTA))
        assertEquals(1, flechasDeVolver())
    }

    private fun flechasDeVolver(): Int =
        composeRule.onAllNodes(hasContentDescription("Volver"), useUnmergedTree = true).fetchSemanticsNodes().size
}
