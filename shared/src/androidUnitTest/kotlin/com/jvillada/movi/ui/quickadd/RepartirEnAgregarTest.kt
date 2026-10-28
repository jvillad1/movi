package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.jvillada.movi.ui.components.CascaraDeAncho
import com.jvillada.movi.ui.components.LocalHayTecladoFisico
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.espacios.TAG_CONFIRMAR_REPARTIR_ESTE_GASTO
import com.jvillada.movi.ui.espacios.TAG_HOJA_REPARTIR_ESTE_GASTO
import com.jvillada.movi.ui.espacios.TITULO_REPARTIR_ESTE_GASTO
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
 * # «Repartir este gasto» en Agregar (vuelta de diseño 23)
 *
 * El ícono de personas sacaba de la hoja a «Pagó otro» en el espacio, con el campo vacío: se perdía
 * el monto y el sentido quedaba al revés. Ahora abre una hoja encima, con lo escrito, y al confirmar
 * usa los caminos de siempre: «Pagué yo» guarda (`postEvent`) y reparte ese movimiento
 * (`repartirMovimiento`); «Pagó otro» anota el gasto del espacio (`anotarGastoDeOtro`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xhdpi")
class RepartirEnAgregarTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("nu", "Nu", AccountType.SAVINGS, 2_000_000)
    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private val guardados = mutableListOf<FinancialEvent>()
    private val repartidos = mutableListOf<Pair<String, String>>()
    private val anotados = mutableListOf<GastoDelEspacio>()
    private var cerrada = 0
    private val ofrecidos = mutableListOf<FinancialEvent>()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun el_monto_viaja_a_la_hoja_y_el_icono_dice_que_hace() {
        montarYEscribir45000()
        composeRule.onNode(hasContentDescription(DESCRIPCION_DE_REPARTIR_EN_AGREGAR), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasContentDescription(DESCRIPCION_DE_SUBIR_PAPEL_EN_AGREGAR), useUnmergedTree = true).assertExists()

        abrirLaHoja()

        composeRule.onNodeWithText(TITULO_REPARTIR_ESTE_GASTO).assertIsDisplayed()
        assertEquals("45000", textoDelMontoDeLaHoja().filter(Char::isDigit), "el monto escrito en Agregar llega a la hoja")
        // «Yo» viene elegido, y la regla del espacio se dice con tu parte.
        composeRule.onNodeWithText("Mitad y mitad · Tu parte $22.500", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Guardar y repartir", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun pague_yo_guarda_y_reparte_con_los_mismos_datos() {
        montarYEscribir45000()
        abrirLaHoja()
        confirmar()
        composeRule.waitUntil(5_000) { repartidos.isNotEmpty() }

        val ev = guardados.single()
        assertEquals(45_000L, ev.amount)
        assertEquals(TransactionType.EXPENSE, ev.type)
        assertEquals(nu.id, ev.accountId)
        assertEquals(listOf(casa.id to ev.id), repartidos, "se reparte el MISMO movimiento que se guardó")
        assertTrue(anotados.isEmpty(), "pagaste tú: no se anota como gasto de otro")
        composeRule.waitUntil(5_000) { cerrada == 1 }
        // Vuelta 44: después de repartir no se pregunta «¿se repite todos los meses?».
        assertTrue(ofrecidos.isEmpty(), "un gasto repartido no se avisa para ofrecer el recurrente")
    }

    /**
     * Vuelta 44 · **«Repartir» del Inicio abre Agregar en modo personas**: «Repartir este gasto», «¿Quién
     * pagó? Yo | Caro» con «Yo» marcado, y el monto en blanco.
     */
    @Test
    fun repartir_desde_el_inicio_abre_agregar_en_modo_personas_con_yo() {
        montar(presetRepartir = true)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(TITULO_REPARTIR_ESTE_GASTO), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(TITULO_REPARTIR_ESTE_GASTO).assertIsDisplayed()
        composeRule.onNodeWithText("¿Quién pagó?", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("", textoDelMontoDeLaHoja().filter(Char::isDigit), "el monto en blanco")
        // «Yo» marcado: dice que se anota en tu cuenta y se reparte («Guardar y repartir»), no «Pagó otro».
        composeRule.onNodeWithText("Guardar y repartir", useUnmergedTree = true).assertIsDisplayed()
        val yo = composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("Yo")) and hasAnyAncestor(hasTestTag(TAG_HOJA_REPARTIR_ESTE_GASTO)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Selected in yo.config && yo.config[SemanticsProperties.Selected], "«Yo» viene marcado")
    }

    @Test
    fun pago_otro_va_por_su_camino() {
        montarYEscribir45000()
        abrirLaHoja()
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("Caro")) and hasAnyAncestor(hasTestTag(TAG_HOJA_REPARTIR_ESTE_GASTO)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        confirmar()
        composeRule.waitUntil(5_000) { anotados.isNotEmpty() }

        val g = anotados.single()
        assertEquals("dst_caro", g.pagadoPor)
        assertEquals(45_000L, g.monto)
        assertTrue(g.categoria.isNotBlank(), "con la categoría que tenía Agregar")
        assertTrue(guardados.isEmpty(), "lo pagó otro: no es un movimiento de tus cuentas")
        assertTrue(repartidos.isEmpty())
        composeRule.waitUntil(5_000) { cerrada == 1 }
    }

    @Test
    fun guardado_y_sin_repartir_reintenta_solo_el_reparto() {
        fallosDelReparto = 1
        montarYEscribir45000()
        abrirLaHoja()
        confirmar()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Reintentar el reparto", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("El gasto quedó anotado, pero no se pudo repartir", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(0, cerrada, "la hoja sigue: falta el reparto")

        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("Reintentar el reparto")) and
                hasAnyAncestor(hasTestTag(TAG_CONFIRMAR_REPARTIR_ESTE_GASTO)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { repartidos.isNotEmpty() }

        assertEquals(1, guardados.size, "el gasto se guardó una sola vez")
        assertEquals(listOf(casa.id to guardados.single().id), repartidos)
        composeRule.waitUntil(5_000) { cerrada == 1 }
    }

    /**
     * **Con teclado físico (la web), abrir la hoja no cuelga la página.** La hoja de Agregar atiende
     * las teclas y recuperaba el foco *siempre* que lo perdía; la de «Repartir este gasto», encima,
     * recupera el suyo cuando es la de arriba y lo pierde. Las dos se lo quitaban sin fin: el
     * navegador dejaba de responder al tocar el ícono (Robolectric sin teclado no lo veía). Con la
     * cáscara de la app (la pila del foco de las hojas) y el teclado puesto, la hoja abre, el foco se
     * queda quieto y las teclas son de la hoja de arriba; al cerrarla vuelven a Agregar.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun con_teclado_fisico_abrir_la_hoja_no_se_queda_peleando_el_foco() {
        montarYEscribir45000(tecladoFisico = true)
        abrirLaHoja()

        composeRule.onNodeWithText(TITULO_REPARTIR_ESTE_GASTO).assertIsDisplayed()
        assertEquals(1, pedidosDeEspacios, "los espacios se piden una vez, no en cada vuelta")
        // Un dígito ahora es del campo de la hoja (la de arriba), no de las teclas de Agregar.
        composeRule.onRoot().performKeyInput { pressKey(Key.Seven) }
        composeRule.waitForIdle()
        assertEquals("45000", textoDelMontoDeLaHoja().filter(Char::isDigit), "Agregar no se quedó con la tecla")
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private var pedidosDeEspacios = 0

    /** Cuántas veces falla `repartirMovimiento` antes de funcionar (sin red, o el movimiento sin subir). */
    private var fallosDelReparto = 0

    private fun montarYEscribir45000(tecladoFisico: Boolean = false) {
        montar(tecladoFisico)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(nu.name, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasTestTag(TAG_REPARTIR_EN_AGREGAR), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tocar("4"); tocar("5"); tocar("000")
    }

    private fun montar(tecladoFisico: Boolean = false, presetRepartir: Boolean = false) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nu)
            override suspend fun getEspacios(): List<Espacio> = listOf(casa).also { pedidosDeEspacios++ }
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { guardados += it }
            override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
                if (fallosDelReparto > 0) {
                    fallosDelReparto--
                    throw ApiException(404, "Movimiento no encontrado")
                }
                repartidos += espacioId to eventoId
                return GastoDelEspacio(id = "gde", espacioId = espacioId, eventoId = eventoId, monto = 45_000)
            }
            override suspend fun anotarGastoDeOtro(espacioId: String, gasto: GastoDelEspacio): GastoDelEspacio =
                gasto.also { anotados += it }
        }
        composeRule.setContent {
            // La cáscara de la app: la pila del foco de las hojas es una sola, como en `App.kt`.
            CompositionLocalProvider(LocalHayTecladoFisico provides tecladoFisico) { MoviTheme {
                CascaraDeAncho(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            QuickAddScreen(
                                onDismiss = {},
                                onSaved = { cerrada++ },
                                onSavedEvent = { ofrecidos += it },
                                presetPestana = if (presetRepartir) PestanaDeAgregar.GASTO else null,
                                presetRepartir = presetRepartir,
                            )
                        }
                        Spacer(Modifier.height(64.dp))
                    }
                }
            } }
        }
        composeRule.waitForIdle()
    }

    private fun abrirLaHoja() {
        composeRule.onNodeWithTag(TAG_REPARTIR_EN_AGREGAR, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun confirmar() {
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("Guardar", substring = true)) and
                hasAnyAncestor(hasTestTag(TAG_CONFIRMAR_REPARTIR_ESTE_GASTO)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun textoDelMontoDeLaHoja(): String =
        composeRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_HOJA_REPARTIR_ESTE_GASTO)),
            useUnmergedTree = true,
        ).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** Bajo Robolectric `performClick` no llega al composable: ver [HojaAgregarEligeLaCuentaTest]. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
