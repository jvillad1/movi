package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.analisis.EnQueSeVaScreen
import com.jvillada.movi.ui.analisis.TAG_FILA_DEL_ANALISIS
import com.jvillada.movi.ui.buscar.TAG_RESULTADO_DE_BUSCAR
import com.jvillada.movi.ui.buscar.BuscarScreen
import com.jvillada.movi.ui.buscar.TAG_CAMPO_DE_BUSCAR
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Movimientos y sus pantallas, con el sistema de diseño (ola R4)
 *
 * Una prueba por pantalla del grupo —Movimientos, Por revisar, Reconciliar un aviso, Buscar y En qué
 * se va—, montada a **360 dp** (el teléfono más angosto) con datos de verdad, que mira lo que el
 * sistema promete:
 *
 * - arriba está el `Encabezado` del sistema;
 * - **ningún título de sección va en MAYÚSCULAS** (`Seccion`, en frase);
 * - **toda fila mide lo que mide una fila del sistema**: 56 dp como mínimo (64 con subtítulo);
 * - **ninguna cifra se recorta**: cada `Cifra` dibuja su monto entero, sin «…» ni desborde.
 *
 * `sdk = [34]` y `NATIVE`: hace falta el motor de texto real para medir el desborde (como en
 * `NadaSeRecortaEnElInicioTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class ElSistemaEnMovimientosYSusPantallasTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L)
    private val hoy = LocalDate.parse(todayIsoInAppZone())
    private val ahora = System.currentTimeMillis()

    private fun evento(id: String, descripcion: String, monto: Long, estado: ReconciliationStatus = ReconciliationStatus.RECONCILED) =
        FinancialEvent(
            id = id, accountId = banco.id, type = TransactionType.EXPENSE, amount = monto, category = "Comida",
            description = descripcion, timestamp = ahora, source = EventSource.MANUAL,
            reconciliationStatus = estado, countsAsCashFlow = true,
        )

    /** Un monto largo a propósito: el que más cuesta que entre a 360 dp. */
    private val dias = listOf(
        EventDay(
            hoy.toString(), -12_392_100L,
            listOf(
                evento("e1", "Cuota del apartamento de la Gardenera", 12_000_000L),
                evento("e2", "Rappi · Las Doce", 392_100L, ReconciliationStatus.UNCONFIRMED),
            ),
        ),
    )

    private val aviso = SmsMessage(
        id = "s1", time = "2026-09-25 09:15", bank = "Bancolombia",
        text = "Bancolombia: Compraste \$12.392.100 en COLEGIO LA SALLE.", state = SMS_STATE_PENDING, det = "Colegio",
    )

    private val repo = object : RepositorioDePruebaDeMovimientos() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 1)
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getEventsByDay(): List<EventDay> = dias
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getSmsMessages(): List<SmsMessage> = listOf(aviso)
        override suspend fun getSms(id: String): SmsMessage = aviso
        override suspend fun parseSms(id: String): ParsedSms =
            ParsedSms(12_392_100.0, "COLEGIO LA SALLE", TransactionType.EXPENSE, "Comida")
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
    }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@movi.test")
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = repo
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun montar(esperar: String, pantalla: @Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(esperar, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun conTag(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Los textos que viven dentro de [nodo] (el título de una sección, por ejemplo). */
    private fun textosDentro(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentro)

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.single()
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    /**
     * Las `Fila`s del sistema en pantalla. Una pantalla que le pone su propio `testTag` a la fila (para
     * sus pruebas: `TAG_FILA_DE_MOVIMIENTO_SUELTO`, `TAG_FILA_DEL_ANALISIS`, los resultados de Buscar,
     * las filas de Reconciliar) lo pone sobre la misma `Fila`, y el de afuera gana: se cuentan los dos.
     */
    private fun filasDelSistema(): List<SemanticsNode> =
        composeRule.onAllNodes(
            SemanticsMatcher("es una Fila") { nodo ->
                val tag = nodo.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
                tag == TAG_FILA || tag == TAG_FILA_DE_MOVIMIENTO_SUELTO || tag == TAG_FILA_DEL_ANALISIS ||
                    tag.startsWith(TAG_RESULTADO_DE_BUSCAR) || tag.startsWith("sms:fila:")
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

    /** Lo que el sistema promete en toda pantalla de este grupo. */
    private fun usaElSistema(pantalla: String, conFilas: Boolean = true) {
        assertEquals(1, conTag(TAG_ENCABEZADO).size, "$pantalla: arranca con el Encabezado del sistema")

        val titulos = conTag(TAG_SECCION).flatMap(::textosDentro)
        titulos.filter { t -> t.any { it.isLetter() } }.forEach { t ->
            assertTrue(t != t.uppercase(), "$pantalla: el título «$t» va en MAYÚSCULAS")
        }

        val densidad = composeRule.density.density
        val filas = filasDelSistema()
        if (conFilas) assertTrue(filas.isNotEmpty(), "$pantalla: pinta sus filas con la Fila del sistema")
        filas.forEach { fila ->
            val alto = fila.size.height / densidad
            assertTrue(alto >= Medida.altoDeFila.value - 0.5f, "$pantalla: una fila mide $alto dp, menos que la del sistema")
        }

        conTag(TAG_CIFRA).forEach { cifra ->
            assertTrue(seVeEntero(cifra), "$pantalla: «${texto(cifra)}» se recorta a 360 dp")
        }
    }

    @Test
    fun `Movimientos usa el sistema y nada se recorta`() {
        montar("Cuota del apartamento") { TransactionsScreen(onNavigate = {}) }
        usaElSistema("Movimientos")
        // El monto largo, entero.
        assertTrue(conTag(TAG_CIFRA).any { texto(it) == "−\$12.000.000" }, conTag(TAG_CIFRA).map(::texto).toString())
        // El día en un renglón: «Hoy» como se escribe, no «HOY».
        assertTrue(composeRule.onAllNodesWithText("HOY", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `Por revisar usa el sistema y nada se recorta`() {
        montar("Entraron solos") { PorRevisarScreen(onNavigate = {}) }
        usaElSistema("Por revisar")
    }

    @Test
    fun `Reconciliar usa el sistema y nada se recorta`() {
        montar("COLEGIO LA SALLE") { SMSReconcileScreen(onNavigate = {}, smsId = aviso.id) }
        usaElSistema("Reconciliar")
        assertTrue(conTag(TAG_CIFRA).any { texto(it) == "−\$12.392.100" }, conTag(TAG_CIFRA).map(::texto).toString())
    }

    @Test
    fun `Buscar usa el sistema y nada se recorta`() {
        montar("Buscar") { BuscarScreen(onNavigate = {}) }
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement("cuota")
        composeRule.waitUntil(timeoutMillis = 5_000) { filasDelSistema().isNotEmpty() }
        composeRule.waitForIdle()
        usaElSistema("Buscar")
    }

    @Test
    fun `En que se va usa el sistema y nada se recorta`() {
        montar("Comida") { EnQueSeVaScreen(onNavigate = {}) }
        usaElSistema("En qué se va")
        assertTrue(conTag(TAG_CIFRA).isNotEmpty(), "pinta lo gastado")
    }
}
