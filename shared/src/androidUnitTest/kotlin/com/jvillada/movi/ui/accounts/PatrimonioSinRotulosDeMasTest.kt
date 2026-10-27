package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
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
 * # Patrimonio sin rótulos que repiten la sección ni subtítulos cortados (vuelta de diseño 2)
 *
 * En producción, a 390 px:
 * - dentro de «Dinero · 2» cada cuenta decía «Dinero» debajo de su nombre;
 * - en Herramientas, «Hace más de un mes que no cuadras …» se cortaba;
 * - «Bancolombia Ahorros» se partía en dos renglones al lado de «$13.845.800» aunque sobraba ancho.
 *
 * Mismo método que `LasPantallasDeR4cUsanElSistemaTest`: `sdk = [34]` y `NATIVE` para el motor de
 * texto, con la letra que la app le pone a todo (×1,12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PatrimonioSinRotulosDeMasTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    /** Hace dos meses: Bancolombia Ahorros lleva más de un mes sin cuadrarse. */
    private val haceDosMeses = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000

    private val cuentas = listOf(
        Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L, firstEventAt = haceDosMeses),
        Account("acc-nu", "Nu", AccountType.SAVINGS, 2_890_000L),
        Account("acc-ef", "Efectivo", AccountType.CASH, -26_500L),
        Account("acc-dv", "Davivienda", AccountType.CHECKING, 500_000L),
        Account("acc-sk", "Skandia", AccountType.INVESTMENT, 98_450_300L),
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
    }

    private fun conLetraDeMovi(content: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun montarPatrimonio() {
        Repositories.sustitutoDePrueba = repo
        conLetraDeMovi { AccountsScreen(onNavigate = {}) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Herramientas", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentroDe(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentroDe)

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo)
        return (0 until d.lineCount).none { d.isLineEllipsized(it) } && !d.multiParagraph.didExceedMaxLines
    }

    private fun nodoConTexto(t: String, substring: Boolean = false): SemanticsNode =
        composeRule.onAllNodesWithText(t, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().first()

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xhdpi")
    fun `ninguna cuenta repite el grupo de su seccion, y dice su tipo si el nombre no lo dice`() {
        montarPatrimonio()
        val filas = composeRule.onAllNodesWithTag(TAG_FILA_DE_CUENTA, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(cuentas.size, filas.size)
        val textos = filas.map { textosDentroDe(it) }
        textos.forEach { t ->
            assertTrue("Dinero" !in t && "Inversión" !in t, "una fila repite el grupo de su sección: $t")
        }
        // El nombre ya dice el tipo: sin subtítulo.
        assertTrue("Ahorros" !in textos.first { "Bancolombia Ahorros" in it })
        assertTrue("Ahorros" in textos.first { "Nu" in it })
        assertTrue("Corriente" in textos.first { "Davivienda" in it })
        assertEquals(1, textos.first { "Efectivo" in it }.count { it == "Efectivo" })
        // Con o sin subtítulo, las filas de un grupo miden lo mismo.
        val minimo = with(composeRule.density) { Medida.altoDeFilaDoble.toPx() } - 0.5f
        filas.forEach { assertTrue(it.size.height >= minimo, "una fila de cuenta mide ${it.size.height}px") }
    }

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xhdpi")
    fun `a 360 dp el subtitulo de Herramientas no se corta`() {
        montarPatrimonio()
        listOf("Hace más de un mes que no cuadras Bancolombia Ahorros", "Traspasos, cuotas de crédito y pagos de tarjeta").forEach { t ->
            val nodo = nodoConTexto(t)
            assertTrue(seVeEntero(nodo), "«$t» se corta a 360 dp (${disposicion(nodo).lineCount} renglones)")
        }
    }

    /**
     * La fila de Patrimonio a 390 dp (donde se tomó la captura): el nombre en un renglón al lado del
     * saldo. A 360 dp sí baja a un segundo renglón, y está bien (ver `LINEAS_DEL_TITULO_DE_FILA`).
     */
    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 dp Bancolombia Ahorros va en un renglon junto a su saldo`() {
        conLetraDeMovi {
            Superficie(modifier = Modifier.padding(horizontal = Medida.margenDePantalla), relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = "Bancolombia Ahorros",
                    inicio = { AvatarDeLaCuenta(cuentas.first()) },
                    final = { Cifra(13_845_800) },
                )
            }
        }
        composeRule.waitForIdle()
        val titulo = nodoConTexto("Bancolombia Ahorros")
        val cifra = nodoConTexto("\$13.845.800")
        val d = disposicion(titulo)
        assertEquals(
            1, d.lineCount,
            "se parte: el título mide ${titulo.size.width}px y necesita ${d.multiParagraph.maxIntrinsicWidth}px; " +
                "la cifra ${cifra.size.width}px desde ${cifra.boundsInRoot.left}, el título termina en ${titulo.boundsInRoot.right}",
        )
        assertTrue(seVeEntero(cifra))
    }
}
