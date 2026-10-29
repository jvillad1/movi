package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # La tarjeta «N por revisar» se va con el scroll (vuelta de diseño 1)
 *
 * En producción, a 390 dp, la tarjeta (≈125 dp, la superficie de las propuestas) quedaba clavada
 * arriba de la lista: se comía un tercio de la pantalla para siempre y, al hacer scroll, las filas
 * se asomaban por sus esquinas redondeadas al pasar por debajo. Ahora es el primer ítem de la lista:
 * fijos quedan solo el encabezado, el selector del período y los chips.
 *
 * **Vuelta de diseño 30: la franja Entró · Salió · Neto también** (decisión del coordinador). Va en la
 * lista, primero, y la tarjeta después; las dos se van con el scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LoDeArribaSeVaConElScrollTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")
    private val hoyIso = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()

    /** Treinta compras de hoy: más de una pantalla de filas. */
    private val compras = (1..30).map { i ->
        FinancialEvent(
            id = "e$i",
            accountId = banco.id,
            type = TransactionType.EXPENSE,
            amount = 10_000L + i,
            category = "Comida",
            description = "Compra ${i.toString().padStart(2, '0')}",
            timestamp = Clock.System.now().toEpochMilliseconds() - i * 1_000L,
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
            countsAsCashFlow = true,
        )
    }

    private fun montar() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = hoyIso, total = -compras.sumOf { it.amount }, items = compras))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getSmsMessages(): List<SmsMessage> = listOf(
                SmsMessage(
                    id = "s1", time = "${com.jvillada.movi.ui.fecha.hoyEnAppZone().let { kotlinx.datetime.LocalDate.fromEpochDays(it.toEpochDays() - 10) }} 07:15", bank = "Bancolombia",
                    text = "Compra aprobada \$28.500 en Uber BV.", state = SMS_STATE_PENDING, det = "Uber",
                ),
            )
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            // Vuelta 40: con un aviso del banco, la tarjeta de los avisos (antes «N por revisar»).
            composeRule.onAllNodesWithText("por confirmar", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Compra 01", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private val lista get() = composeRule.onNode(hasScrollToIndexAction())

    private fun tarjetas(): SemanticsNodeInteractionCollection =
        composeRule.onAllNodes(hasTestTag(TAG_TARJETA_DE_AVISOS), useUnmergedTree = true)

    private fun franjas(): SemanticsNodeInteractionCollection =
        composeRule.onAllNodes(hasTestTag(TAG_RESUMEN_EN_EL_TELEFONO), useUnmergedTree = true)

    /** Los rectángulos, dentro de la pantalla, de las filas de compras que se ven. */
    private fun filasALaVista(): List<Rect> {
        val pantalla = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val listaRect = lista.fetchSemanticsNode().boundsInRoot
        return composeRule.onAllNodes(hasText("Compra ", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.bottom > listaRect.top && it.top < minOf(listaRect.bottom, pantalla.bottom) }
    }

    @Test
    fun `la tarjeta es el primer item de la lista, no algo fijo encima`() {
        montar()

        composeRule.onNodeWithTag(TAG_TARJETA_DE_AVISOS, useUnmergedTree = true).assertIsDisplayed()
        // Vive adentro de la lista perezosa: se mueve con ella.
        composeRule.onNode(
            hasTestTag(TAG_TARJETA_DE_AVISOS) and hasAnyAncestor(hasScrollToIndexAction()),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        // Y antes de las filas: la primera compra va debajo de la tarjeta, sin taparse.
        val tarjeta = tarjetas().fetchSemanticsNodes().single().boundsInRoot
        val primera = filasALaVista().minByOrNull { it.top }!!
        assertTrue("la tarjeta ($tarjeta) tapa la primera fila ($primera)", tarjeta.bottom <= primera.top)
    }

    @Test
    fun `la franja del resumen es el primer item de la lista, antes de la tarjeta`() {
        montar()

        composeRule.onNode(
            hasTestTag(TAG_RESUMEN_EN_EL_TELEFONO) and hasAnyAncestor(hasScrollToIndexAction()),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        val franja = franjas().fetchSemanticsNodes().single().boundsInRoot
        val tarjeta = tarjetas().fetchSemanticsNodes().single().boundsInRoot
        val listaRect = lista.fetchSemanticsNode().boundsInRoot
        assertTrue("la franja ($franja) va adentro de la lista ($listaRect)", franja.top >= listaRect.top)
        assertTrue("la franja ($franja) va antes de la tarjeta ($tarjeta)", franja.bottom <= tarjeta.top)
    }

    @Test
    fun `tras un scroll corto la tarjeta sube con la lista y no tapa la primera fila visible`() {
        montar()
        val antes = tarjetas().fetchSemanticsNodes().single().boundsInRoot
        val antesLaFranja = franjas().fetchSemanticsNodes().single().boundsInRoot

        lista.performTouchInput { swipeUp(startY = bottom - 10f, endY = centerY) }
        composeRule.waitForIdle()

        // Se movió con la lista (o ya se fue): fija, quedaría en el mismo lugar.
        val despues = tarjetas().fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0f }
        assertTrue("la tarjeta no se movió con el scroll: $antes → $despues", despues.all { it.top < antes.top })
        val franjaDespues = franjas().fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0f }
        assertTrue("la franja no se movió con el scroll: $antesLaFranja → $franjaDespues", franjaDespues.all { it.top < antesLaFranja.top })
        val primera = filasALaVista().minByOrNull { it.top }!!
        tarjetas().fetchSemanticsNodes().forEach { nodo ->
            val tarjeta = nodo.boundsInRoot
            assertTrue(
                "la tarjeta ($tarjeta) quedó encima de la primera fila visible ($primera)",
                tarjeta.bottom <= primera.top || tarjeta.top >= primera.bottom,
            )
        }
    }

    @Test
    fun `tras hacer scroll hasta el final la tarjeta ya no esta en pantalla`() {
        montar()

        lista.performScrollToNode(hasText("Compra 30"))
        composeRule.waitForIdle()

        // `boundsInRoot` viene recortado: un ítem que salió de la lista mide cero (o ya no existe).
        val aLaVista = tarjetas().fetchSemanticsNodes().filter { it.boundsInRoot.height > 0f }
        assertTrue("la tarjeta se quedó fija arriba de la lista: ${aLaVista.map { it.boundsInRoot }}", aLaVista.isEmpty())
        val franjaALaVista = franjas().fetchSemanticsNodes().filter { it.boundsInRoot.height > 0f }
        assertTrue("la franja se quedó fija arriba de la lista: ${franjaALaVista.map { it.boundsInRoot }}", franjaALaVista.isEmpty())
        // Lo fijo sigue: los chips.
        composeRule.onAllNodesWithText("Todo", useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("los chips siguen fijos", it.isNotEmpty())
        }
    }
}
