package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.espacios.TITULO_DE_PERSONAS
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
 * # «Lo tuyo» entero a 360 dp (revisión de la vuelta 38)
 *
 * «Lo que tienes menos lo q…» se cortaba a 390 al lado de la cifra del patrimonio: la fila quedó sin
 * subtítulo (la cifra ya lo dice) y el del cuadre es corto. Con el motor de texto real, la letra 12 %
 * más grande de la app y una cifra de diez dígitos, ningún texto de la sección se corta ni se parte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LoTuyoA360Test {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> =
            listOf(Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 1_234_567_890L))
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio {
            val gastos = listOf(
                GastoDelEspacio(
                    id = "g1", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 7_800_000L,
                    partes = mapOf(MIEMBRO_YO to 3_900_000L, "dst_caro" to 3_900_000L),
                ),
            )
            val saldo = saldoDelEspacio(casa, gastos, emptyList())
            return DetalleDelEspacio(espacio = casa, saldo = saldo, delPeriodo = saldo, gastos = gastos)
        }
    }

    private fun layoutDe(texto: String): TextLayoutResult {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a 360 dp nada de lo tuyo se corta, y el patrimonio va sin subtitulo`() {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    // Como en Tú: la lista con el margen de la pantalla a los lados.
                    Box(Modifier.fillMaxSize().padding(horizontal = Medida.margenDePantalla)) { LoTuyo(onNavigate = {}) }
                }
            }
        }
        composeRule.waitUntil(5_000) { hay("Caro te debe") && hay("1.234.567.890") }

        assertFalse(hay("Lo que tienes"), "el patrimonio no lleva subtítulo: la cifra ya lo dice")
        listOf(FILA_TU_PATRIMONIO, TITULO_DE_PERSONAS, "Caro te debe", FILA_CUADRE_DE_SALDOS, SUBTITULO_DEL_CUADRE).forEach { texto ->
            val layout = layoutDe(texto)
            assertEquals(1, layout.lineCount, "«$texto» en un renglón")
            assertFalse(layout.isLineEllipsized(0), "«$texto» sin «…»")
            assertFalse(layout.multiParagraph.didExceedMaxLines, "«$texto» entero")
        }
        assertTrue(hay("3.900.000"), "la cifra de Personas")
    }
}
