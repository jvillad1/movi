package com.jvillada.movi.ui.accounts

import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.cuadre.EFECTIVO_EN_NEGATIVO_CORTO
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
 * # El subtítulo del efectivo en negativo se lee entero (vuelta de diseño 24)
 *
 * La revisión UX vio en Patrimonio, a 390 px, «No puede quedar en negativ…». Acá, peor: a 360 dp y
 * con la letra del sistema ×1,3, al lado de un saldo largo en negativo. El subtítulo tiene que verse
 * entero, sin «…» y sin pasarse de su ancho.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class ElEfectivoEnNegativoSeLeeEnteroTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuentas = listOf(
        Account("acc-efectivo", "Efectivo", AccountType.CASH, -1_250_000L),
        Account("acc-nu", "Nu", AccountType.SAVINGS, 2_400_000L),
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "u1@local", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25, reminderLeadDays = 5)
        override suspend fun isScreenAdmin(): Boolean = false
        override suspend fun getCategories(): List<CategoryUsage> = emptyList()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.single()
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    /** Monta Patrimonio con la letra del sistema por [escala] y devuelve el texto del aviso ya medido. */
    private fun montarYMedir(escala: Float): Pair<SemanticsNode, TextLayoutResult> {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) } }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(EFECTIVO_EN_NEGATIVO_CORTO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val nodo = composeRule.onAllNodesWithText(EFECTIVO_EN_NEGATIVO_CORTO, useUnmergedTree = true).fetchSemanticsNodes().single()
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return nodo to r.single()
    }

    @Test
    fun `a 360 dp con la letra por 1,3 el subtitulo del efectivo en negativo se lee entero`() {
        val (nodo, _) = montarYMedir(1.3f)
        assertTrue(seVeEntero(nodo), "«$EFECTIVO_EN_NEGATIVO_CORTO» se recorta a 360 dp con la letra ×1,3")
    }

    /** Vuelta 56: el 130 % del teléfono por el ×1,12 de la app, 1,456 en la práctica. */
    @Test
    fun `a 360 dp con la letra al 130 por ciento de verdad tambien se lee entero`() {
        val (nodo, _) = montarYMedir(ESCALA_DEL_TELEFONO_AL_130)
        assertTrue(seVeEntero(nodo), "«$EFECTIVO_EN_NEGATIVO_CORTO» se recorta a 360 dp con la letra ×1,456")
    }

    /**
     * Donde lo vio la revisión —390, con la letra de Movi (×1,12, la que pone `App.kt`)— la frase
     * corta entra en **un** renglón: la fila mide lo mismo que sus vecinas. El segundo renglón que la
     * fila permite es solo el respaldo de una letra más grande.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
    fun `a 390 dp con la letra de Movi entra en un renglon`() {
        val (nodo, medida) = montarYMedir(1.12f)
        assertTrue(seVeEntero(nodo))
        assertEquals(1, medida.lineCount, "«$EFECTIVO_EN_NEGATIVO_CORTO» entra en un renglón a 390 dp")
    }
}
