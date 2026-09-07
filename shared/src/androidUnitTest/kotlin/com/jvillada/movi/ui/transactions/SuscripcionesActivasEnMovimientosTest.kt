package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR 5 del rediseño de Recurrentes (2026-09): [TransactionsScreen] montada de verdad, con el chip
 * «Recurrentes» activo, para lo que una función pura no alcanza a probar — que las suscripciones
 * ACTIVAS se pinten (entre el PR 2 y el PR 4 no tenían ninguna superficie, aunque seguían sumando
 * en «Flujo libre») y que «Quitar» de verdad escriba, y escriba **lo que corresponde según de
 * quién sea la suscripción**: borrar la que escribió el dueño, marcar DISMISSED la que encontró
 * el detector.
 *
 * Esa última distinción es la parte sutil y la que más vale fijar: las dos ramas se ven idénticas
 * en pantalla (la fila desaparece) y solo se distinguen por a qué endpoint le pegaron.
 *
 * Mismo patrón de montaje que [ResumenRecurrentesEnMovimientosTest].
 */
@RunWith(RobolectricTestRunner::class)
// Más alta que los 731dp del resto de las pruebas de esta pantalla, a propósito: la sección va al
// final del chip —debajo del «Flujo libre», ver `SeccionSuscripcionesActivas`— y en 731dp la última
// fila queda bajo el pliegue. Ahí sigue componiéndose (el card entero es UN item del LazyColumn) así
// que `onAllNodesWithText` la ve, pero un toque en sus coordenadas no llega a nada. Una ventana alta
// es más honesta que scrollear a ciegas: lo que se prueba es la acción, no el scroll.
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class SuscripcionesActivasEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    /** Ola 17: la tarjeta con la que el dueño paga sus cuatro suscripciones reales. */
    private val nubank = Account("acc-nu", "Nubank", AccountType.CREDIT_CARD, -200_000L, "COP")

    private fun sub(
        id: String,
        nombre: String,
        clave: String,
        monto: Long,
        moneda: String,
        dia: Int,
        estado: SubStatus,
        cuenta: String? = null,
    ) = Subscription(
        id = id, merchantKey = clave, displayName = nombre, amount = monto, currency = moneda,
        dayOfMonth = dia, status = estado, confidence = SubConfidence.HIGH,
        firstSeen = 0, lastSeen = 0, occurrences = 4, accountId = cuenta,
    )

    /**
     * Las tres variantes de origen, ordenadas por día del mes — que es el orden en que las pinta
     * la sección, y por lo tanto el orden de los enlaces «Quitar».
     */
    private var suscripciones = listOf(
        // día 3 · la escribió el dueño
        sub("s_claude", "Claude", MANUAL_SUB_PREFIX + "claude", 12L, "USD", 3, SubStatus.CONFIRMED),
        // día 12 · la encontró el detector y se activó sola
        sub("s_youtube", "YouTube", "youtube", 22_900L, "COP", 12, SubStatus.AUTO),
        // día 20 · la encontró el detector y el dueño la confirmó
        sub("s_netflix", "Netflix", "netflix", 44_900L, "COP", 20, SubStatus.CONFIRMED),
        // día 25 · Ola 17: la escribió el dueño y le dijo con qué tarjeta la paga. Va ÚLTIMA a
        // propósito, para no correr los índices de [quitarDeLaFila] de las tres de arriba.
        sub("s_google", "Google One", MANUAL_SUB_PREFIX + "google_one", 79_000L, "COP", 25, SubStatus.CONFIRMED, cuenta = nubank.id),
    )

    /**
     * La cuota de un crédito, tal como se la manda el server por `/api/payments/upcoming`. Está en
     * este fixture por dos motivos, y los dos hacen falta:
     *
     * 1. **Sin esa llamada contestada la sección no se pinta.** El total del pie sale del mismo
     *    reparto que decide qué suscripción ya está tapada por una regla, y ese reparto mira
     *    también las reglas sintéticas de los créditos: si la llamada falla, la lista llega corta
     *    y el total puede salir alto. Por eso `vencimientosOk` gatea el pie igual que al card.
     * 2. **Separa las dos cifras.** «Gastos recurrentes» suma la cuota y el pie no, así que
     *    $67.800 sigue apareciendo UNA sola vez en pantalla y la aserción de abajo sigue
     *    diciendo algo.
     */
    private val cuotaDelCarro = UpcomingPayment(
        rule = RecurringRule(
            id = CREDIT_RULE_PREFIX + "acc-carro", name = "Cuota Vehículo", category = "Créditos",
            amount = 4_215_223L, dayOfMonth = 17, type = TransactionType.EXPENSE,
        ),
        dueDate = "2026-09-17",
        daysUntil = 10,
        status = PaymentStatus.UPCOMING,
    )

    /** Lo que la pantalla le pidió al repositorio, que es lo único que distingue las dos ramas. */
    private var actualizada: Subscription? = null
    private var borrada: String? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia, nubank)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(suscripciones, monthlyTotalCop = 67_800L, usdToCop = 4_000.0)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(cuotaDelCarro)
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()

        override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription {
            actualizada = subscription
            suscripciones = suscripciones.map { if (it.id == id) subscription else it }
            return subscription
        }

        override suspend fun deleteSubscription(id: String) {
            borrada = id
            suscripciones = suscripciones.filterNot { it.id == id }
        }
    }

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        esperarTexto("Sin movimientos aún")
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("SUSCRIPCIONES ACTIVAS")
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarQueDesaparezca(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** El «Quitar» de la fila N, en el orden por día del mes de [suscripciones]. */
    private fun quitarDeLaFila(indice: Int) {
        composeRule.onAllNodesWithText("Quitar", useUnmergedTree = true)[indice].performClick()
    }

    // ── La etiqueta que el dueño pidió reponer ────────────────────────────────

    @Test
    fun `cada suscripcion activa dice de donde salio`() {
        composeRule.onNodeWithText("Suscripción · la encontró Movi y la activó sola", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("Suscripción · la encontró Movi", useUnmergedTree = true)
            .assertExists()
        // La del dueño no dice que la encontró nadie.
        composeRule.onNodeWithText("Suscripción", useUnmergedTree = true).assertExists()
    }

    /**
     * Ola 17 — **la fila dice con qué cuenta se paga**, resolviendo el id contra la lista de
     * cuentas de la pantalla. Es lo que una función pura no puede probar: que ese mapa de verdad
     * le llega a la sección, que hasta ahora nadie se lo pasaba.
     *
     * El nombre de la cuenta ocupa el lugar del rótulo genérico «Suscripción» en vez de sumarse:
     * dentro de una sección titulada «Suscripciones activas» esa palabra no informaba nada, y la
     * línea sigue teniendo dos segmentos como antes.
     */
    @Test
    fun `una suscripcion con cuenta dice el nombre de la cuenta que la paga`() {
        composeRule.onNodeWithText("Google One", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Nubank", useUnmergedTree = true).assertExists()
        // Y ocupó el lugar del rótulo, no se sumó a él: la única fila que sigue diciendo
        // «Suscripción» a secas es la de Claude, que no tiene cuenta. Si el mapa de cuentas no
        // llegara a la sección, acá habría DOS y esta aserción fallaría.
        composeRule.onAllNodesWithText("Suscripción", useUnmergedTree = true).assertCountEquals(1)
    }

    /**
     * Y las que no tienen cuenta se ven **exactamente** como antes: sin «sin cuenta» y sin un
     * hueco donde iría el nombre. A una suscripción que nunca tuvo cuenta no le falta nada — que
     * es el caso de todas las filas que ya estaban en producción.
     */
    @Test
    fun `las que no tienen cuenta no inventan ninguna`() {
        composeRule.onAllNodesWithText("sin cuenta", substring = true, ignoreCase = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** En SU moneda, sin convertir: solo el «Flujo libre» de arriba pasa por la TRM. */
    @Test
    fun `el monto va en la moneda de la suscripcion`() {
        composeRule.onNodeWithText("−US$12", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("−$22.900", useUnmergedTree = true).assertExists()
    }

    // ── Las dos ramas de «Quitar» ─────────────────────────────────────────────

    /**
     * Una que encontró el detector se marca DISMISSED y NO se borra: DISMISSED es el «no me la
     * propongas más» que respeta el re-scan. Borrarla haría que el próximo barrido la volviera a
     * proponer.
     */
    @Test
    fun `quitar una que encontro el detector la marca descartada`() {
        quitarDeLaFila(2) // Netflix, día 20

        esperarQueDesaparezca("Netflix")
        assertNull("no se puede borrar lo que el detector va a volver a proponer", borrada)
        assertEquals("s_netflix", actualizada?.id)
        assertEquals(SubStatus.DISMISSED, actualizada?.status)
    }

    /**
     * Una que escribió el dueño se BORRA. Marcarla DISMISSED la dejaba en un limbo: invisible en
     * la lista, imposible de recuperar, y todavía chocando con el alta si volvía a contratar el
     * servicio.
     */
    @Test
    fun `quitar una que escribio el dueno la borra`() {
        quitarDeLaFila(0) // Claude, día 3

        esperarQueDesaparezca("Claude")
        assertEquals("s_claude", borrada)
        assertNull("una manual no deja rastro DISMISSED", actualizada)
    }

    /** La AUTO también se puede sacar — es la que más falta hacía: nadie la aprobó nunca. */
    @Test
    fun `quitar la que se activo sola la marca descartada`() {
        quitarDeLaFila(1) // YouTube, día 12

        esperarQueDesaparezca("YouTube")
        assertEquals("s_youtube", actualizada?.id)
        assertEquals(SubStatus.DISMISSED, actualizada?.status)
        assertNull(borrada)
    }

    // ── Editar, la acción que faltaba ─────────────────────────────────────────

    /**
     * Ola 18. Antes de esto la fila solo ofrecía «Quitar», así que corregir un monto era quitar
     * y volver a escribir — y en una suscripción DETECTADA eso ni siquiera funcionaba: «Quitar»
     * la marca DISMISSED en vez de borrarla, o sea que el cobro quedaba invisible y el alta
     * manual chocaba con él.
     */
    @Test
    fun `cada suscripcion activa se puede editar`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)
            .assertCountEquals(suscripciones.size)
    }

    /** Y abre la hoja sobre ESA fila, con lo que ya estaba guardado adentro. */
    @Test
    fun `editar abre la hoja con los datos de la suscripcion`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)[3].performClick() // Google One

        esperarTexto("Editar suscripción")
        // DOS nodos: el de la fila que quedó atrás y el del campo prellenado de la hoja. Con
        // «hay al menos uno» esta prueba pasaba igual sin prefill —la fila sola ya lo aportaba—,
        // así que no probaba lo único que dice probar. Medido: 1 antes de abrir, 2 después.
        composeRule.onAllNodesWithText("Google One", useUnmergedTree = true).assertCountEquals(2)
    }

    /**
     * La moneda NO se ofrece al editar: el `PUT /api/subscriptions/{id}` no escribe esa columna.
     * Un selector que no guarda nada es peor que no tenerlo.
     */
    @Test
    fun `la hoja de edicion no ofrece cambiar la moneda`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)[3].performClick()

        esperarTexto("Editar suscripción")
        composeRule.onAllNodesWithText("MONEDA", useUnmergedTree = true).assertCountEquals(0)
        // Pero «cada cuánto» sí, porque el PUT sí la escribe — y una detectada nace mensual, así
        // que un cobro anual mal clasificado solo se arregla acá.
        composeRule.onAllNodesWithText("CADA CUÁNTO", useUnmergedTree = true).assertCountEquals(1)
    }

    /** Toca el clickable que CONTIENE ese texto — el toque sobre el texto suelto no llega. */
    private fun tocarPorSemantica(texto: String) {
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(texto)),
            useUnmergedTree = true,
        ).onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** El botón de guardar de la hoja: el toque directo cae en el scrim, el semántico no. */
    private fun guardarLaHoja() {
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText("Guardar cambios")),
            useUnmergedTree = true,
        ).onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /**
     * **Lo único que de verdad importa de esta acción: que guarde donde tiene que guardar.**
     * La hoja es la misma que crea reglas recurrentes, y la rama equivocada no falla ni avisa —
     * escribiría una regla NUEVA y dejaría la suscripción intacta, o sea el cobro contado dos
     * veces en «Gastos recurrentes». Todo lo que la hoja no ofrece tocar tiene que llegar igual
     * que estaba: la clave de origen, el estado, la moneda.
     */
    @Test
    fun `guardar la edicion actualiza la suscripcion y no crea una regla`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)[2].performClick() // Netflix
        esperarTexto("Editar suscripción")

        guardarLaHoja()

        val cambios = requireNotNull(actualizada) { "no se llamó a updateSubscription" }
        assertEquals("s_netflix", cambios.id)
        assertNull("editar no crea ni borra nada", borrada)
        // Lo que la hoja no toca llega igual: es una corrección, no un alta.
        assertEquals("netflix", cambios.merchantKey)
        assertEquals(SubStatus.CONFIRMED, cambios.status)
        assertEquals("COP", cambios.currency)
    }

    /**
     * **«Sin cuenta» tiene que poder quitarla de verdad.** El `PUT` distingue «quítala» de «no la
     * toques» por la PRESENCIA de la clave `accountId` en el JSON, y kotlinx omite una clave que
     * vale su default — que para `accountId` es `null`, justo el valor que significa «sin
     * cuenta». O sea que era la única intención que el cliente no podía expresar: la hoja
     * cerraba, la lista recargaba, y la cuenta seguía puesta. Lo arregla `@EncodeDefault(ALWAYS)`
     * en el modelo; esta prueba es lo que impide que vuelva.
     */
    @Test
    fun `quitarle la cuenta a una suscripcion la deja sin cuenta`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)[3].performClick() // Google One
        esperarTexto("Editar suscripción")
        // Por acción semántica y no por toque: lo clickable es la fila, y el toque sobre el
        // texto que está adentro no siempre le llega (mismo motivo que en [guardarLaHoja]).
        tocarPorSemantica("Elegir")
        // Esta fila del selector es un `Text` que ES el clickable, no uno adentro de otro, así
        // que va por el nodo mismo y no por un ancestro que lo contenga.
        composeRule.onAllNodesWithText("Sin cuenta", useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        guardarLaHoja()

        val cambios = requireNotNull(actualizada) { "no se llamó a updateSubscription" }
        assertEquals("s_google", cambios.id)
        assertNull("«Sin cuenta» tiene que llegar como accountId nulo", cambios.accountId)
    }

    /**
     * **Un cobro en dólares se edita con «US$», no con «$».** El comentario de la hoja lo dice
     * desde la V12: en Colombia «$12» se lee doce pesos. El prefijo salía de una condición que
     * valía `false` en cuanto la hoja entraba en modo edición — o sea siempre, en este modo.
     */
    @Test
    fun `editar una suscripcion en dolares muestra el prefijo en dolares`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)[0].performClick() // Claude, USD
        esperarTexto("Editar suscripción")

        // El prefijo es su propio nodo dentro de [MoneyField] y su texto es exactamente «US$»
        // (la fila que quedó atrás dice «−US$12», que no coincide exacto). Si el prefijo saliera
        // en pesos, acá habría cero nodos.
        composeRule.onAllNodesWithText("US$", useUnmergedTree = true).assertCountEquals(1)
        // Y el campo NO ofrece cambiar la moneda: cambiarla no sería corregir un dato.
        composeRule.onAllNodesWithText("MONEDA", useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * **La hoja de una suscripción no ofrece «Eliminar».** Lo ofrecía: ese enlace se pintaba con
     * `isEditMode`, que editar una suscripción también enciende, pero `delete()` sale en su
     * primera línea cuando no hay una regla detrás. Era un enlace rojo destructivo que no hacía
     * nada — ni borraba, ni fallaba, ni avisaba. Para una suscripción el camino es «Quitar» desde
     * la fila, que además sabe distinguir borrar una manual de marcar DISMISSED una detectada.
     */
    @Test
    fun `la hoja de una suscripcion no ofrece Eliminar`() {
        composeRule.onAllNodesWithText("Editar", useUnmergedTree = true)[2].performClick()
        esperarTexto("Editar suscripción")

        composeRule.onAllNodesWithText("Eliminar", useUnmergedTree = true).assertCountEquals(0)
    }

    // ── El total al pie ───────────────────────────────────────────────────────

    /**
     * La sección listaba los cobros y no decía cuánto suman: el dueño tenía que sumarlos de
     * cabeza, y ni siquiera eso servía, porque un cobro anual no aporta su monto entero.
     *
     * Lo que esta prueba cubre y la de la función pura no puede: que el total que llega al pie
     * es el del MISMO resumen que armó la lista —el que ya prorrateó, convirtió y salteó
     * duplicadas— y no una suma nueva hecha sobre las filas visibles.
     */
    @Test
    fun `la seccion cierra con el total del mes`() {
        composeRule.onNodeWithText("Total al mes", useUnmergedTree = true).assertExists()
        // 67.800 es el `monthlyTotalCop` del repositorio de prueba, no la suma de los montos
        // que se ven arriba: las filas están en su propia moneda y una va en dólares.
        //
        // UN solo nodo, y ahí está el punto de toda la sección: «Gastos recurrentes» —el card de
        // arriba— mezcla reglas, cuotas de créditos y suscripciones, así que esta cifra no
        // aparece en ninguna otra parte de la pantalla. El pie es el único lugar donde el dueño
        // puede leer qué le cuestan sus suscripciones.
        composeRule.onAllNodesWithText("$67.800", useUnmergedTree = true).assertCountEquals(1)
    }

    /** Sin nada raro que reportar, el pie no inventa advertencias. */
    @Test
    fun `el total no avisa de cobros sin convertir cuando no los hay`() {
        composeRule.onAllNodesWithText("no pudimos pasar a pesos", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
