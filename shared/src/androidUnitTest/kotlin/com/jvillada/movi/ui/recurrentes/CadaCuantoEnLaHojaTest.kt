package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EN_DOLARES_SOLO_MES_O_ANIO
import com.jvillada.movi.shared.model.FRECUENCIAS_DEL_PAGO
import com.jvillada.movi.shared.model.FrecuenciaDelPago
import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.nombreDeLaFrecuencia
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.components.ds.TAG_CAMPO_DE_MONTO
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 126 · «¿Cada cuánto?» en la hoja de un pago fijo
 *
 * Seis pastillas (Cada mes · Cada 2 meses · Cada 3 meses · Cada 6 meses · Cada año · Una sola vez); si no es
 * cada mes, la hoja pide la fecha («¿Cuándo es el próximo?» / «¿Cuándo es?») y el día sale de ahí; el `POST`
 * lleva la frecuencia y el ancla. En dólares solo cada mes o cada año. Editar muestra la frecuencia y el
 * próximo vencimiento.
 *
 * Sin el cambio fallan todas: la hoja tenía dos chips («Todos los meses» / «Una vez al año») y «Cada año»
 * mandaba a suscripción.
 *
 * «Hoy» fijo el 2 de noviembre de 2026, con el mes de calendario como período.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class CadaCuantoEnLaHojaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)
    private val dosDeNoviembre = LocalDate(2026, 11, 2)
    private var creada: RecurringRule? = null
    private var editada: RecurringRule? = null
    private var suscripcion: CreateSubscriptionRequest? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "papa@movi.test", name = "Papá", avatarColor = "#FFC21A", periodCutoffDay = 1)
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            creada = rule
            return rule.copy(id = "rr_nueva")
        }
        override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule {
            editada = rule
            return rule.copy(id = id)
        }
        override suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription {
            suscripcion = request
            error("no hace falta la respuesta")
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(existing: RecurringRule? = null, escala: Float? = null) {
        creada = null
        editada = null
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, escala ?: base.fontScale)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        CreateRecurringRuleSheet(
                            onDismiss = {}, onSaved = {}, existing = existing,
                            diaNuevo = 5, nombreNuevo = "Administración", hoy = dosDeNoviembre,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun elegida(texto: String): Boolean =
        composeRule.onNodeWithText(texto).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** Toca el día [dia] del calendario abierto. */
    private fun elegirDia(dia: Int) {
        composeRule.onAllNodes(hasClickAction() and hasText("$dia")).fetchSemanticsNodes().also { assertTrue(it.isNotEmpty(), "no está el día $dia") }
        composeRule.onAllNodes(hasClickAction() and hasText("$dia"))[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun escribirMonto() {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MONTO).performTextInput("450000")
        composeRule.waitForIdle()
    }

    private fun crear() {
        composeRule.onNodeWithText("Crear recurrente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { creada != null }
    }

    // ── Las pastillas y la fecha ──────────────────────────────────────────────

    @Test
    fun `las seis pastillas, con cada mes elegida y el dia del mes de siempre`() {
        montar()
        assertTrue(hay(CADA_CUANTO))
        FRECUENCIAS_DEL_PAGO.forEach { assertTrue(hay(nombreDeLaFrecuencia(it)), nombreDeLaFrecuencia(it)) }
        assertEquals(6, FRECUENCIAS_DEL_PAGO.size)
        assertTrue(elegida("Cada mes"))
        assertTrue(hay("Día del mes"))
        assertFalse(hay("¿Cuándo es el próximo?"))
    }

    @Test
    fun `si no es cada mes pide la fecha, y para una sola vez dice cuando es`() {
        montar()
        tocar("Cada 3 meses")
        assertTrue(elegida("Cada 3 meses"))
        assertTrue(hay("¿Cuándo es el próximo?"))
        assertFalse(hay("Día del mes"), "el día sale de la fecha")
        tocar("Una sola vez")
        assertTrue(hay("¿Cuándo es?"))
        tocar("Cada mes")
        assertTrue(hay("Día del mes"))
    }

    @Test
    fun `sin la fecha no se puede crear`() {
        montar()
        tocar("Cada 6 meses")
        escribirMonto()
        assertTrue(hay("Falta la fecha del próximo pago"))
    }

    // ── Lo que viaja ────────────────────────────────────────────────────────

    @Test
    fun `el POST lleva la frecuencia, el ancla y el dia de la fecha`() {
        montar()
        tocar("Cada 3 meses")
        elegirDia(15)
        assertTrue(hay("15 de noviembre de 2026"))
        escribirMonto()
        crear()
        val regla = assertNotNull(creada)
        assertEquals(FrecuenciaDelPago.TRIMESTRAL, regla.frecuencia)
        assertEquals("2026-11-15", regla.primerVencimiento)
        assertEquals(15, regla.dayOfMonth)
    }

    @Test
    fun `cada anio en pesos es una regla, no una suscripcion`() {
        montar()
        tocar("Cada año")
        elegirDia(20)
        escribirMonto()
        crear()
        assertEquals(FrecuenciaDelPago.ANUAL, assertNotNull(creada).frecuencia)
        assertNull(suscripcion)
    }

    @Test
    fun `cada mes viaja como siempre, sin el campo`() {
        montar()
        escribirMonto()
        crear()
        val regla = assertNotNull(creada)
        assertNull(regla.frecuencia)
        assertNull(regla.primerVencimiento)
        assertEquals(5, regla.dayOfMonth)
    }

    @Test
    fun `en dolares solo cada mes o cada anio`() {
        montar()
        tocar("Dólares")
        tocar("Cada 3 meses")
        escribirMonto()
        assertTrue(hay(EN_DOLARES_SOLO_MES_O_ANIO))
        composeRule.onNodeWithTag(TAG_SOLO_MES_O_ANIO, useUnmergedTree = true).assertExists()
        // No se puede guardar: el botón no hace nada.
        composeRule.onNodeWithText("Crear recurrente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertNull(creada)
        assertNull(suscripcion)
        tocar("Cada año")
        assertFalse(hay(EN_DOLARES_SOLO_MES_O_ANIO))
        // Cada año en dólares sigue siendo suscripción anual, con el día del mes.
        assertTrue(hay("Día del mes"))
        composeRule.onNodeWithText("Crear recurrente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { suscripcion != null }
        assertEquals(PeriodicidadDeCobro.ANUAL, suscripcion?.periodicidad)
        assertEquals("USD", suscripcion?.currency)
    }

    // ── Editar ──────────────────────────────────────────────────────────────

    private val trimestral = RecurringRule(
        id = "rr_admin", name = "Administración", category = "Vivienda", amount = 450_000L, dayOfMonth = 15,
        type = TransactionType.EXPENSE, accountId = "acc_ahorros",
        frecuencia = FrecuenciaDelPago.TRIMESTRAL, primerVencimiento = "2026-09-15",
    )

    @Test
    fun `editar muestra la frecuencia y el proximo vencimiento, y sin tocarla manda el ancla de siempre`() {
        montar(existing = trimestral)
        assertTrue(elegida("Cada 3 meses"))
        assertTrue(hay("15 de diciembre de 2026"), "el próximo desde hoy: 15-sep + 3 meses")
        composeRule.onNodeWithText("Guardar cambios").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { editada != null }
        assertEquals(FrecuenciaDelPago.TRIMESTRAL, editada?.frecuencia)
        assertEquals("2026-09-15", editada?.primerVencimiento, "el ancla no se corre: los vencimientos pasados siguen")
    }

    @Test
    fun `editar a cada mes manda MENSUAL explicito`() {
        montar(existing = trimestral)
        tocar("Cada mes")
        composeRule.onNodeWithText("Guardar cambios").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { editada != null }
        assertEquals(FrecuenciaDelPago.MENSUAL, editada?.frecuencia)
        assertNull(editada?.primerVencimiento)
    }

    // ── Cómo se ve ──────────────────────────────────────────────────────────

    @Test
    fun `con la letra grande a 360 dp nada se corta`() {
        montar(escala = ESCALA_DEL_TELEFONO_AL_130)
        tocar("Cada 6 meses")
        val nodos = FRECUENCIAS_DEL_PAGO.flatMap { f ->
            composeRule.onAllNodesWithText(nombreDeLaFrecuencia(f), useUnmergedTree = true).fetchSemanticsNodes()
        } + composeRule.onAllNodesWithTag(TAG_FECHA_DEL_PAGO, useUnmergedTree = true).fetchSemanticsNodes()
        val fallas = cortados(nodos)
        assertTrue(fallas.isEmpty(), "Con la letra a ×$ESCALA_DEL_TELEFONO_AL_130 a 360 dp:\n" + fallas.joinToString("\n"))
    }

    @Test
    fun `un solo amarillo con la fecha a la vista`() {
        montar()
        tocar("Cada 3 meses")
        elegirDia(15)
        escribirMonto()
        fun cuenta(n: SemanticsNode): Int =
            (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf(::cuenta)
        assertEquals(1, cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()), "solo «Crear recurrente»")
        // Y con el calendario abierto también.
        // («Elegir» de la fecha, no el de la cuenta, que va más abajo.)
        composeRule.onAllNodesWithText("Elegir")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Listo"), "el calendario quedó abierto")
        assertEquals(1, cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()))
    }

    // ── Ayudas ──────────────────────────────────────────────────────────────

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textos(raiz: SemanticsNode): List<SemanticsNode> =
        (if (raiz.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(raiz) else emptyList()) +
            raiz.children.flatMap(::textos)

    /** Lo que se corta o se sale (la misma vara que `ElTextoAl130NoSeCortaTest`). */
    private fun cortados(raices: List<SemanticsNode>): List<String> {
        assertTrue(raices.isNotEmpty(), "No encontré nada que medir")
        val anchoDePantalla = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().size.width + 0.5f
        return raices.flatMap(::textos).mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            val ancho = nodo.size.width + 0.5f
            val limites = nodo.boundsInRoot
            val problema = when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "no le caben los renglones"
                (0 until d.lineCount).any { d.getLineRight(it) - d.getLineLeft(it) > ancho } -> "es más ancho que su caja"
                limites.left < -0.5f || limites.right > anchoDePantalla -> "se sale de la pantalla"
                else -> null
            }
            problema?.let { "«${texto(nodo)}» $it" }
        }
    }
}
