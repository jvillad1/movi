package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.AhorroDelPeriodo
import com.jvillada.movi.shared.model.TipoDeAhorro
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CAMPO
import com.jvillada.movi.ui.components.ds.TAG_CAMPO_DE_MONTO
import com.jvillada.movi.ui.mas.MasScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # «Ahorro de cada período» en Ajustes → Tu plata
 *
 * La fila dice el valor guardado («No apartas nada», «$500.000», «20 % de lo que entra») y abre la
 * hoja que lo elige; la hoja manda el tipo y el valor juntos por el `PUT` del perfil de siempre, y
 * «Guardar» no se deja tocar con un valor que el server rechazaría.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3200dp-xhdpi")
class AhorroDelPeriodoEnAjustesTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun perfil(tipo: TipoDeAhorro = TipoDeAhorro.NINGUNO, valor: Long = 0L) = UserProfile(
        id = "u1", email = "a@b.c", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25,
        ahorroDelPeriodoTipo = tipo.name, ahorroDelPeriodoValor = valor,
    )

    private var guardado: UpdateProfileRequest? = null

    private fun montar(perfil: UserProfile) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun isScreenAdmin(): Boolean = false
            override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
                guardado = request
                return perfil
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { MasScreen(onNavigate = {}, onLogout = {}) } } }
        esperar("Cada día 25")
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun tocar(matcher: SemanticsMatcher) {
        composeRule.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarFila(titulo: String) = tocar(hasClickAction() and hasAnyDescendant(hasText(titulo)))

    /** Un botón o una píldora: en el árbol unido, el nodo que se toca lleva su texto. */
    private fun tocarBoton(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarPildora(texto: String) = tocarBoton(texto)

    @Test
    fun `sin elegir nada la fila dice que no apartas nada`() {
        montar(perfil())
        esperar(NO_APARTAS_NADA)
    }

    @Test
    fun `la fila dice el monto o el porcentaje guardado`() {
        montar(perfil(TipoDeAhorro.PORCENTAJE, 20))
        esperar("20 % de lo que entra")
    }

    @Test
    fun `elegir un porcentaje lo guarda con su tipo por el perfil`() {
        montar(perfil())
        tocarFila(AHORRO_DE_CADA_PERIODO)
        esperar("Cuánto apartas")

        tocarPildora("Un porcentaje")
        // Sin el número, «Guardar» se apaga y dice por qué.
        esperar("Escribe qué porcentaje quieres apartar")
        composeRule.onNode(hasSetTextAction() and hasTestTag(TAG_CAMPO), useUnmergedTree = true).performTextInput("20")
        composeRule.waitForIdle()
        tocarBoton("Guardar")

        composeRule.waitUntil(5_000) { guardado != null }
        assertEquals(TipoDeAhorro.PORCENTAJE.name, guardado?.ahorroDelPeriodoTipo)
        assertEquals(20L, guardado?.ahorroDelPeriodoValor)
    }

    @Test
    fun `un monto se escribe en el campo de monto`() {
        montar(perfil())
        tocarFila(AHORRO_DE_CADA_PERIODO)
        esperar("Cuánto apartas")

        tocarPildora("Un monto")
        composeRule.onNode(hasSetTextAction() and hasTestTag(TAG_CAMPO_DE_MONTO), useUnmergedTree = true)
            .performTextInput("500000")
        composeRule.waitForIdle()
        tocarBoton("Guardar")

        composeRule.waitUntil(5_000) { guardado != null }
        assertEquals(TipoDeAhorro.MONTO.name, guardado?.ahorroDelPeriodoTipo)
        assertEquals(500_000L, guardado?.ahorroDelPeriodoValor)
    }

    @Test
    fun `mas del tope no se deja guardar`() {
        montar(perfil())
        tocarFila(AHORRO_DE_CADA_PERIODO)
        esperar("Cuánto apartas")
        tocarPildora("Un porcentaje")
        composeRule.onNode(hasSetTextAction() and hasTestTag(TAG_CAMPO), useUnmergedTree = true).performTextInput("95")
        composeRule.waitForIdle()
        esperar("El porcentaje va de 1 a 90")
        tocarBoton("Guardar")
        assertNull(guardado, "un porcentaje que el server rechaza no se manda")
    }

    @Test
    fun `volver a Nada guarda NINGUNO`() {
        montar(perfil(TipoDeAhorro.MONTO, 500_000))
        esperar("$500.000")
        tocarFila(AHORRO_DE_CADA_PERIODO)
        esperar("Cuánto apartas")
        tocarPildora("Nada")
        tocarBoton("Guardar")
        composeRule.waitUntil(5_000) { guardado != null }
        assertEquals(TipoDeAhorro.NINGUNO.name, guardado?.ahorroDelPeriodoTipo)
    }

    @Test
    fun `el texto de la fila`() {
        assertEquals(NO_APARTAS_NADA, textoDelAhorro(AhorroDelPeriodo()))
        assertEquals(NO_APARTAS_NADA, textoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.MONTO, 0)))
        assertEquals("$500.000", textoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.MONTO, 500_000)))
        assertEquals("20 % de lo que entra", textoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 20)))
    }
}
