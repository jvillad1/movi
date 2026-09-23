package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ola A: la fila de chips de categorías frecuentes, ejercida desde la hoja de «Agregar» de
 * verdad. [com.jvillada.movi.ui.components.CategoryFieldTest] prueba
 * [com.jvillada.movi.ui.components.categoriasFrecuentes] a secas; falta el cableado —que
 * [QuickAddScreen] lea `UsedCategoriesCache.usosRecientes`, dibuje los chips y que tocar uno
 * cambie la categoría de verdad— que es justo lo que ningún test puro puede ver.
 *
 * Mismo andamio que [HojaAgregarEligeLaCuentaTest]: `RepositorioDePrueba` para las cuentas,
 * `performSemanticsAction` para tocar (bajo Robolectric `performClick` no llega al composable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD)
class HojaAgregarCategoriasFrecuentesTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_000_000)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * **El caso central.** Con «Mercado» (10 usos) y «Transporte» (3 usos) en los últimos 60
     * días, «Agregar» arranca en la más frecuente —«Mercado»— y la ofrece TAMBIÉN como chip: por
     * eso el conteo da dos coincidencias con ese texto (la fila «Categoría» y el chip) y solo una
     * con «Transporte» (el chip nomás).
     */
    @Test
    fun arranca_en_la_mas_frecuente_y_la_ofrece_tambien_como_chip() {
        cargarUsos(
            UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10),
            UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 3),
        )
        montarHoja()

        assertApariciones("Mercado", 2)
        assertApariciones("Transporte", 1)
    }

    /** **Tocar un chip cambia la categoría de verdad**, no solo la marca visualmente. */
    @Test
    fun tocar_un_chip_cambia_la_categoria_elegida() {
        cargarUsos(
            UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10),
            UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 3),
        )
        montarHoja()
        assertApariciones("Transporte", 1)

        tocar("Transporte")

        // Ahora «Transporte» es la elegida (chip + fila «Categoría») y «Mercado» quedó solo
        // como chip.
        assertApariciones("Transporte", 2)
        assertApariciones("Mercado", 1)
    }

    /** Sin datos de uso, la fila no dibuja nada: el comportamiento de siempre, intacto. */
    @Test
    fun sin_datos_de_uso_no_hay_chips() {
        montarHoja()

        // El valor inicial (sin uso) es el primero del catálogo de gastos — «Comida»— y aparece
        // una sola vez: si hubiera un chip duplicaría el texto.
        assertApariciones("Comida", 1)
    }

    /** Una categoría escondida no se ofrece como chip aunque tenga muchos usos. */
    @Test
    fun una_categoria_escondida_no_aparece_como_chip() {
        cargarUsos(UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10))
        UsedCategoriesCache.applyPref("Mercado", CategoryPref(hidden = true))
        montarHoja()

        // Ni como chip ni como valor inicial: escondida es escondida.
        assertApariciones("Mercado", 0)
    }

    /** La fila de chips es de Gasto/Ingreso — Traspaso no tiene categoría, y no tiene chips. */
    @Test
    fun en_traspaso_no_hay_chips_de_categoria() {
        cargarUsos(
            UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10),
            UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 3),
        )
        montarHoja()
        // Antes de cambiar de pestaña: «Mercado» es la elegida (chip + fila) y «Transporte» es
        // solo un chip — el mismo escenario del primer test, de nuevo acá para dejar claro qué
        // cambia al tocar «Traspaso».
        assertApariciones("Mercado", 2)
        assertApariciones("Transporte", 1)

        tocar("Traspaso")

        // Ni la fila «Categoría» ni los chips existen en esta pestaña: los dos nombres
        // desaparecen enteros, no solo el chip.
        assertApariciones("Mercado", 0)
        assertApariciones("Transporte", 0)
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun cargarUsos(vararg entradas: UsedCategory) {
        UsedCategoriesCache.recordFromServer(entradas.toList())
    }

    private fun montarHoja() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {})
                    }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertApariciones(texto: String, cuantas: Int) {
        val encontradas = composeRule.onAllNodesWithText(texto).fetchSemanticsNodes().size
        assertEquals("«$texto» no apareció las veces esperadas", cuantas, encontradas)
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}

/** El AVD `Movi_Sensor` — mismo tamaño y mismo motivo que en `HojaAgregarEligeLaCuentaTest`. */
private const val TELEFONO_DEL_AVD = "w411dp-h731dp-xhdpi"
