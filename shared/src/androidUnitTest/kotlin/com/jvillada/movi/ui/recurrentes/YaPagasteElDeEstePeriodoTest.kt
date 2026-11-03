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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.ui.components.ds.TAG_CAMPO_DE_MONTO
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
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
 * # Vuelta 121 · «¿Ya pagaste el de este período?» en la hoja de un pago fijo nuevo
 *
 * Claudia creó el 2-nov un pago fijo del día 28 y Movi le dijo «Venció hace 5 días». Ahora el server no le
 * inventa el período de antes, y la hoja pregunta solo cuando la fecha de este período ya pasó (hoy
 * incluido), solo al crear y solo en un gasto escrito a mano. «Sí» (por defecto) viaja en el `POST`.
 *
 * «Hoy» fijo el 2 de noviembre de 2026, con el mes de calendario como período.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class YaPagasteElDeEstePeriodoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)
    private val dosDeNoviembre = LocalDate(2026, 11, 2)
    private var creada: RecurringRule? = null
    private var editada: RecurringRule? = null
    /** Cuántas veces se leyó el perfil: sin él la pregunta nunca sale, así que un «no sale» lo espera primero. */
    @Volatile private var perfilLeido = 0
    /** Vuelta 125: la lectura del perfil falla. */
    private var perfilFalla = false

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile {
            if (perfilFalla) { perfilLeido++; error("sin señal") }
            // Sube al DEVOLVER, no al entrar: cuando la prueba lo ve, el perfil ya va camino a la hoja.
            return UserProfile(id = "u", email = "claudia@movi.test", name = "Claudia", avatarColor = "#FFC21A", periodCutoffDay = 1)
                .also { perfilLeido++ }
        }
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            creada = rule
            return rule.copy(id = "rr_nueva")
        }
        override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule {
            editada = rule
            return rule.copy(id = id)
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(
        dia: Int? = null,
        tipo: TransactionType = TransactionType.EXPENSE,
        existing: RecurringRule? = null,
        prefill: RecurringPrefill? = null,
        escala: Float? = null,
    ) {
        creada = null
        editada = null
        perfilLeido = 0
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, escala ?: base.fontScale)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        CreateRecurringRuleSheet(
                            onDismiss = {}, onSaved = {}, existing = existing, prefill = prefill,
                            tipoNuevo = tipo, diaNuevo = dia, nombreNuevo = "Gimnasio", hoy = dosDeNoviembre,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hayPregunta(): Boolean = composeRule.onAllNodesWithTag(TAG_YA_PAGASTE_EL_DE_ESTE_PERIODO, useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    /** El perfil ya llegó y la hoja ya se recompuso con él: desde acá, que la pregunta no esté significa algo. */
    private fun esperarElPerfil() {
        composeRule.waitUntil(5_000) { perfilLeido > 0 }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    private fun esperarLaPregunta() = composeRule.waitUntil(5_000) { hayPregunta() }

    private fun crear() {
        composeRule.onNodeWithText("Crear recurrente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { creada != null }
    }

    private val regla = RecurringRule(
        id = "rr_gym", name = "Gimnasio", category = "Gimnasio", amount = 120_000L, dayOfMonth = 1,
        type = TransactionType.EXPENSE, accountId = "acc_ahorros",
    )

    // ── Cuándo sale ─────────────────────────────────────────────────────────

    @Test
    fun `sale en un gasto cuya fecha de este periodo ya paso, y tambien si vence hoy`() {
        montar(dia = 1)
        esperarLaPregunta()
        assertTrue(composeRule.onAllNodesWithText(YA_PAGASTE_EL_DE_ESTE_PERIODO).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText(LO_ESPERA_DESDE_EL_PROXIMO_PERIODO).fetchSemanticsNodes().isNotEmpty(), "«Sí» por defecto")
    }

    @Test
    fun `vence hoy tambien pregunta`() {
        montar(dia = 2)
        esperarLaPregunta()
    }

    @Test
    fun `no sale si la fecha de este periodo todavia no llega`() {
        montar(dia = 28)
        esperarElPerfil()
        assertFalse(hayPregunta(), "el 28 de noviembre todavía no vence")
    }

    @Test
    fun `no sale en un ingreso, y se va al tocar Ingreso`() {
        montar(dia = 1)
        esperarLaPregunta()
        composeRule.onNodeWithText("Ingreso").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertFalse(hayPregunta(), "un ingreso no se pregunta")
    }

    @Test
    fun `no sale al editar`() {
        montar(existing = regla)
        // Editando, la hoja no pide el perfil (la lectura está apagada): se fija eso mismo, y que con el
        // tiempo tampoco aparece la pregunta.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(0, perfilLeido, "editar no lee el perfil para preguntar")
        assertFalse(hayPregunta(), "editar no pregunta")
    }

    @Test
    fun `no sale en una regla que viene de un movimiento`() {
        montar(
            prefill = RecurringPrefill(
                name = "Gimnasio", amount = 120_000L, category = "Gimnasio", type = TransactionType.EXPENSE,
                dayOfMonth = 1, accountId = "acc_ahorros", activeFrom = "2026-11-01", eventId = "ev_gym",
            ),
        )
        esperarElPerfil()
        assertFalse(hayPregunta(), "el movimiento ya es la ocurrencia de su período")
    }

    /**
     * Vuelta 125: si la lectura del perfil falla, la hoja decide con el mes de calendario (antes no preguntaba
     * y la regla nacía vencida sin aviso). La guía y la tarjeta de Movi AI usan el mismo criterio.
     */
    @Test
    fun `si la lectura del perfil falla pregunta con el mes de calendario`() {
        perfilFalla = true
        montar(dia = 1)
        esperarLaPregunta()
        escribirMonto()
        crear()
        assertEquals(true, assertNotNull(creada).yaPagoElDeEstePeriodo)
    }

    @Test
    fun `si la lectura del perfil falla y la fecha no llega, no pregunta`() {
        perfilFalla = true
        montar(dia = 28)
        esperarElPerfil()
        assertFalse(hayPregunta())
    }

    // ── Lo que viaja ────────────────────────────────────────────────────────

    @Test
    fun `Si viaja en el POST`() {
        montar(dia = 1)
        esperarLaPregunta()
        escribirMonto()
        crear()
        assertEquals(true, assertNotNull(creada).yaPagoElDeEstePeriodo)
        assertNull(creada?.activeFrom, "el server pone el arranque, no la hoja")
    }

    @Test
    fun `No viaja en el POST`() {
        montar(dia = 1)
        esperarLaPregunta()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EL_DE_ESTE_PERIODO, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(LO_ESPERA_EN_ESTE_PERIODO).fetchSemanticsNodes().isNotEmpty()
        }
        escribirMonto()
        crear()
        assertEquals(false, assertNotNull(creada).yaPagoElDeEstePeriodo)
    }

    @Test
    fun `sin la pregunta no viaja el campo`() {
        montar(dia = 28)
        esperarElPerfil()
        escribirMonto()
        crear()
        assertNull(assertNotNull(creada).yaPagoElDeEstePeriodo)
    }

    // ── Cómo se ve ──────────────────────────────────────────────────────────

    @Test
    fun `con la letra grande a 360 dp la fila no se corta`() {
        montar(dia = 1, escala = ESCALA_DEL_TELEFONO_AL_130)
        esperarLaPregunta()
        val fila = composeRule.onAllNodesWithTag(TAG_YA_PAGASTE_EL_DE_ESTE_PERIODO, useUnmergedTree = true).fetchSemanticsNodes()
        val fallas = cortados(fila)
        assertTrue(fallas.isEmpty(), "Con la letra a ×$ESCALA_DEL_TELEFONO_AL_130 a 360 dp:\n" + fallas.joinToString("\n"))
    }

    @Test
    fun `un solo amarillo con la pregunta a la vista`() {
        montar(dia = 1)
        esperarLaPregunta()
        escribirMonto() // con el botón habilitado: un principal apagado no lleva el amarillo
        fun cuenta(n: SemanticsNode): Int =
            (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf(::cuenta)
        assertEquals(1, cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()), "solo «Crear recurrente»")
    }

    // ── Ayudas ──────────────────────────────────────────────────────────────

    private fun escribirMonto() {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MONTO).performTextInput("120000")
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textos(raiz: SemanticsNode): List<SemanticsNode> =
        (if (raiz.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(raiz) else emptyList()) +
            raiz.children.flatMap(::textos)

    /** Lo que se corta o se sale (la misma vara que `ElTextoAl130NoSeCortaTest`). */
    private fun cortados(raices: List<SemanticsNode>): List<String> {
        assertTrue(raices.isNotEmpty(), "No encontré la fila")
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
