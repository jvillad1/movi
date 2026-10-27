package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UserProfile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **El texto guía de «Buscar» cabe entero a 360 dp con la letra agrandada** (×1,3 encima del ×1,12
 * que la app ya aplica), en un renglón y sin «…». Vuelta de diseño 5: «Movimientos, comercios,
 * personas, pantallas…» se cortaba ya a 390 dp con la letra normal.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LaGuiaDeBuscarNoSeCortaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `el texto guia no se corta a 360 dp con la letra agrandada`() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)
            override suspend fun getAccounts(): List<Account> =
                listOf(Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L))
        }
        composeRule.setContent {
            ConClaseDeAncho(escala = 1.3f * 1.12f) {
                Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = {}) }
            }
        }
        composeRule.waitForIdle()

        val nodo = composeRule.onNodeWithTag(TAG_GUIA_DE_BUSCAR, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val disposicion = resultados.single()
        assertEquals(GUIA_DE_BUSCAR, disposicion.layoutInput.text.text)
        val medida = "texto ${disposicion.multiParagraph.width} px, sin cortar ${disposicion.multiParagraph.maxIntrinsicWidth} px, caja ${nodo.size.width} px"
        assertEquals(1, disposicion.lineCount, "un renglón; $medida")
        assertFalse(disposicion.isLineEllipsized(0), "sin «…»; $medida")
        assertFalse(disposicion.multiParagraph.didExceedMaxLines, "sin cortarse")
        assertTrue(
            disposicion.getLineRight(0) - disposicion.getLineLeft(0) <= nodo.size.width + 0.5f,
            "cabe en el ancho de la caja (${nodo.size.width} px)",
        )
    }
}
