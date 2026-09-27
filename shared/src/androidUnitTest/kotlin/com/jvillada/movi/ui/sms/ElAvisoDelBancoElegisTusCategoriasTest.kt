package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.CeldaDeCategoria
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CREAR_CATEGORIA
import com.jvillada.movi.ui.components.contenidoDelSelectorDeCategoria
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
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
 * # Ola L — reconciliar un aviso del banco elige categoría como todas las pantallas
 *
 * Las pastillas eran las diez primeras del catálogo en orden alfabético: «Hija», «Fútbol» y
 * «Mercado extra» —las que el dueño usa— quedaban fuera del corte, y la fila «Categoría» era de
 * solo lectura: no había forma de ver todas ni de crear una nueva. Ahora las pastillas siguen el
 * uso, la fila tiene «Cambiar» (el mismo selector de las otras pantallas) y hay «+ Nueva».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class ElAvisoDelBancoElegisTusCategoriasTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuenta = Account("g1", "Bancolombia Ahorros", AccountType.SAVINGS, 1_800_000)

    private val aviso = SmsMessage(
        id = "s1",
        time = "2026-09-25 09:15",
        bank = "Bancolombia",
        text = "Bancolombia: Compraste \$480.000 en COLEGIO LA SALLE.",
        state = SMS_STATE_PENDING,
        det = "",
    )

    private val publicados = mutableListOf<FinancialEvent>()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun montar(
        propuesta: String = "Otros",
        lectura: CompletableDeferred<Unit>? = null,
    ) {
        // Movi propone «Otros»; el catálogo tiene diez o más nombres alfabéticamente antes de «Hija».
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Hija", listOf(TransactionType.EXPENSE), usosRecientes = 12),
                UsedCategory("Fútbol", listOf(TransactionType.EXPENSE), usosRecientes = 7),
                UsedCategory("Mercado extra", listOf(TransactionType.EXPENSE), usosRecientes = 3),
                UsedCategory("Gimnasio", listOf(TransactionType.EXPENSE), usosRecientes = 0),
            ),
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(cuenta)
            override suspend fun getSms(id: String): SmsMessage = aviso
            override suspend fun parseSms(id: String): ParsedSms {
                lectura?.await()
                return ParsedSms(480_000.0, "COLEGIO LA SALLE", TransactionType.EXPENSE, propuesta)
            }
            override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
            override suspend fun confirmSms(id: String) {}
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = aviso.id) } }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** «Cambiar» de la fila Categoría, por su rótulo y no por su posición. */
    private fun cambiarLaCategoria() {
        composeRule.onNodeWithTag(tagDeFilaDelSms("Categoría")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun las_pastillas_traen_hija_y_futbol_sin_buscarlas() {
        montar()

        composeRule.onNodeWithText("Hija").assertIsDisplayed()
        composeRule.onNodeWithText("Fútbol").assertIsDisplayed()
    }

    @Test
    fun tocar_la_pastilla_de_hija_es_la_que_se_guarda() {
        montar()

        tocar("Hija")
        tocar("Confirmar")

        assertEquals("Hija", publicados.single().category)
    }

    @Test
    fun la_fila_de_categoria_tiene_cambiar_y_abre_el_selector_completo() {
        montar()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()

        cambiarLaCategoria()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsDisplayed()
        // Una propia que ni siquiera cabe en las pastillas está en la cuadrícula.
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Gimnasio")).assertIsDisplayed()
    }

    @Test
    fun elegir_en_el_selector_la_pone_y_lo_cierra() {
        montar()

        cambiarLaCategoria()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Gimnasio")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()
        tocar("Confirmar")
        assertEquals("Gimnasio", publicados.single().category)
    }

    @Test
    fun cambiar_permite_crear_una_categoria_nueva() {
        montar()

        cambiarLaCategoria()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Colegio")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("Confirmar")

        assertEquals("Colegio", publicados.single().category)
    }

    @Test
    fun la_pastilla_nueva_abre_el_selector_con_el_cursor_en_la_busqueda() {
        montar()

        tocar("+ Nueva")

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsDisplayed().assertIsFocused()
    }

    /** Los nombres de las pastillas de categoría, de izquierda a derecha. */
    private fun pastillas(): List<String> =
        composeRule.onAllNodes(esUnaPastilla()).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag].removePrefix(tagDePastillaDeCategoriaDelSms("")) }

    private fun esUnaPastilla() = SemanticsMatcher("es una pastilla de categoría") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(tagDePastillaDeCategoriaDelSms("")) == true
    }

    @Test
    fun la_propuesta_de_movi_es_la_primera_pastilla_aunque_no_este_en_ningun_catalogo() {
        montar(propuesta = "Colegio La Salle")

        assertEquals("Colegio La Salle", pastillas().first())
        // Y «Hija», la más usada, viene enseguida.
        assertEquals("Hija", pastillas()[1])
    }

    @Test
    fun elegir_otra_no_pierde_la_propuesta_de_movi() {
        montar(propuesta = "Colegio La Salle")

        tocar("Fútbol")

        assertEquals(listOf("Colegio La Salle", "Fútbol"), pastillas().take(2))
    }

    @Test
    fun la_pastilla_nueva_es_la_primera_de_la_fila() {
        montar()

        composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA).assertIsDisplayed()
        val primera = composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA).getUnclippedBoundsInRoot()
        val hija = composeRule.onNodeWithTag(tagDePastillaDeCategoriaDelSms("Hija")).getUnclippedBoundsInRoot()
        assertTrue(primera.left < hija.left, "«+ Nueva» tiene que ir antes que las pastillas de uso")
    }

    /** Antes de leer el aviso no se sabe si es gasto o ingreso: no hay de dónde elegir todavía. */
    @Test
    fun antes_de_que_llegue_la_lectura_no_se_ofrece_cambiar_ni_nueva() {
        val lectura = CompletableDeferred<Unit>()
        montar(lectura = lectura)

        composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA).assertDoesNotExist()
        composeRule.onAllNodesWithText("Cambiar").assertCountEquals(1) // solo el de la cuenta

        lectura.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA).assertIsDisplayed()
        composeRule.onAllNodesWithText("Cambiar").assertCountEquals(2)
    }

    /** Ola L, revisión: crear «Colegio» en un aviso la deja conocida para el siguiente. */
    @Test
    fun una_categoria_creada_en_un_aviso_queda_conocida_para_el_siguiente() {
        montar()

        cambiarLaCategoria()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Colegio")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("Confirmar")

        assertTrue("Colegio" in UsedCategoriesCache.used.keys, "el caché no la conoce: ${UsedCategoriesCache.used.keys}")
        val siguiente = categoriasParaElegirEnElSms(
            propuesta = "Otros", tipo = TransactionType.EXPENSE, usadas = UsedCategoriesCache.used,
            prefs = UsedCategoriesCache.prefs, usos = UsedCategoriesCache.usosRecientes, cuantas = 50,
        )
        assertTrue("Colegio" in siguiente, "el siguiente aviso no la ofrece: $siguiente")
        val selector = contenidoDelSelectorDeCategoria(
            "Colegio", TransactionType.EXPENSE, UsedCategoriesCache.used, UsedCategoriesCache.prefs,
        )
        assertTrue(selector.celdas.none { it is CeldaDeCategoria.Crear }, "se vuelve a ofrecer «Crear»")
    }
}
