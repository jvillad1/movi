package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_CUENTA
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA
import com.jvillada.movi.ui.sdui.TAG_WIDGET_FALTA_POR_PAGAR
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Un monto nunca se recorta** (revisión de la ola R1 en el navegador a 390 px): el widget «En qué se
 * va» mostraba «Merca… ↑ $392.1» y la tarjeta de la cuenta «Bancolombia Ah…». Se monta el Inicio
 * entero en los dos anchos de teléfono —360 y 390 dp— y se mira el texto ya dibujado: cada cifra del
 * widget y de las tarjetas entra entera (sin desborde visual) y dice el monto completo, y el nombre de
 * la cuenta se lee entero.
 *
 * `sdk = [34]` y `NATIVE`: hace falta el motor de texto real para medir el desborde.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NadaSeRecortaEnElInicioTest {

    @get:Rule val composeRule = createComposeRule()

    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(
            Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800),
            Account("a2", "Nu", AccountType.SAVINGS, 2_890_000),
        ),
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        // Dos pagos pendientes: así «Falta por pagar» también se pinta y los dos widgets van lado a
        // lado, cada uno con media pantalla (que es donde se recortaba). Vuelta 73: con uno solo, ese
        // ya está en «Próximo pago» y el widget no se pinta.
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(id = "r_arr", name = "Arriendo", category = "Vivienda", amount = 1_850_000, dayOfMonth = 20, type = TransactionType.EXPENSE),
                dueDate = "2026-09-20", daysUntil = 6, status = PaymentStatus.UPCOMING,
            ),
            UpcomingPayment(
                rule = RecurringRule(id = "r_int", name = "Internet", category = "Servicios", amount = 80_000, dayOfMonth = 24, type = TransactionType.EXPENSE),
                dueDate = "2026-09-24", daysUntil = 10, status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 9),
        ajustesDePeriodo = PeriodSettings(),
        // Cinco categorías, todas con su flecha: el widget pinta las tres que más pesan.
        spentByCategory = mapOf(
            "Mercado" to 392_100L,
            // Un período con la cuota del apartamento: el monto más largo que el widget tiene que pintar.
            "Vivienda" to 12_392_100L,
            "Restaurantes y domicilios" to 236_000L,
            "Salud" to 210_000L,
            "Educación" to 269_200L,
        ),
        gastoDelAnterior = mapOf("Mercado" to 300_000L, "Vivienda" to 12_000_000L, "Restaurantes y domicilios" to 250_000L, "Salud" to 100_000L, "Educación" to 300_000L),
        diasComparados = 12,
    )

    private fun montar(escalaDeLetra: Float) {
        composeRule.setContent {
            // La letra del sistema agrandada: el monto tiene que entrar igual (y es lo que acerca la
            // prueba a la web, cuya letra es más ancha que la de Robolectric).
            val densidad = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidad.density, escalaDeLetra)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = datos,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    /**
     * **¿El texto se dibuja entero?** Ningún renglón cortado con «…» y cada renglón cabe en el ancho
     * que el nodo tiene en pantalla. (`hasVisualOverflow` no sirve acá: compara contra el ancho
     * máximo con que se midió el párrafo, no contra lo que el nodo terminó ocupando.)
     */
    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo)
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    /** Las cifras que viven dentro de [tagDelContenedor]. */
    private fun cifrasDentroDe(tagDelContenedor: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tagDelContenedor, useUnmergedTree = true).fetchSemanticsNodes().flatMap { contenedor ->
            fun bajar(n: SemanticsNode): List<SemanticsNode> =
                (if (n.config.getOrNull(SemanticsProperties.TestTag) == TAG_CIFRA) listOf(n) else emptyList()) + n.children.flatMap(::bajar)
            bajar(contenedor)
        }

    private fun nadaSeRecorta(escalaDeLetra: Float = 1f) {
        montar(escalaDeLetra)
        // Vuelta 80: los dos widgets, uno debajo del otro y a todo el ancho.
        val falta = composeRule.onAllNodesWithTag(TAG_WIDGET_FALTA_POR_PAGAR, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        val enQueSeVa = composeRule.onAllNodesWithTag(TAG_TARJETA_EN_QUE_SE_VA, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        assertEquals(falta.left, enQueSeVa.left)
        assertTrue(falta.bottom <= enQueSeVa.top)
        val delWidget = cifrasDentroDe(TAG_TARJETA_EN_QUE_SE_VA) + cifrasDentroDe(TAG_WIDGET_FALTA_POR_PAGAR)
        assertTrue(delWidget.isNotEmpty(), "el widget pinta sus cifras")
        // El monto completo, no «$12.392.1».
        assertTrue(delWidget.any { texto(it) == "\$12.392.100" }, "el widget dice \$12.392.100 entero: ${delWidget.map(::texto)}")
        delWidget.forEach { nodo ->
            assertTrue(seVeEntero(nodo), "«${texto(nodo)}» se recorta en el widget")
        }
        val deLasCuentas = cifrasDentroDe(TAG_TARJETA_DE_CUENTA)
        assertTrue(deLasCuentas.isNotEmpty(), "las tarjetas pintan su saldo")
        deLasCuentas.forEach { nodo ->
            assertTrue(seVeEntero(nodo), "«${texto(nodo)}» se recorta en la tarjeta")
        }
        // Los nombres de las categorías del widget tampoco se recortan con estos datos («Mercado»).
        val mercado = composeRule.onAllNodesWithText("Mercado", useUnmergedTree = true).fetchSemanticsNodes().single()
        assertTrue(seVeEntero(mercado), "«Mercado» se recorta")
        // El nombre de la cuenta se lee entero: «Bancolombia Ahorros», no «Bancolombia Ah…».
        val nombre = composeRule.onAllNodesWithText("Bancolombia Ahorros", useUnmergedTree = true).fetchSemanticsNodes().single()
        assertTrue(seVeEntero(nombre), "el nombre de la cuenta se recorta")
    }

    @Test
    @Config(qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 dp ningun monto ni nombre de cuenta se recorta`() = nadaSeRecorta()

    @Test
    @Config(qualifiers = "w390dp-h3200dp-xhdpi")
    fun `a 390 dp ningun monto ni nombre de cuenta se recorta`() = nadaSeRecorta()

    @Test
    @Config(qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 dp con la letra agrandada tampoco`() = nadaSeRecorta(escalaDeLetra = 1.3f)

    /** Vuelta 56: el 130 % del teléfono por el ×1,12 de la app, 1,456 en la práctica. */
    @Test
    @Config(qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 dp con la letra al 130 por ciento de verdad tampoco`() = nadaSeRecorta(escalaDeLetra = ESCALA_DEL_TELEFONO_AL_130)
}
