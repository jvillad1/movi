package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertCountEquals
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.EdicionDeMovimiento
import org.junit.After
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Tocar un movimiento ofrece **corregirlo**, no solo anularlo.
 *
 * El defecto que esta clase fija: hasta acá había dos puertas al mismo renglón y ofrecían cosas
 * distintas. Movimientos abría la hoja completa; el **detalle de la cuenta** abría directamente la
 * de anular. O sea que en la pantalla donde el dueño *nota* que un saldo está mal, lo único que se
 * le ofrecía sobre la fila era el rodeo destructivo —anular y volver a crear, perdiendo el id del
 * movimiento— que la edición vino justamente a reemplazar.
 *
 * Se prueba [HojaDelMovimiento], que es lo que ahora abren **las dos** pantallas. Lo que esta
 * prueba puede afirmar es que ese juego de hojas ofrece las dos cosas a la vez y en el orden
 * decidido (corregir arriba, anular abajo y separado). Lo que **no** puede afirmar es que cada
 * pantalla lo llame: `AccountDetailScreen` y `TransactionsScreen` leen de `Repositories`, que es
 * un `object` no inyectable, así que montarlas en Robolectric no traería ninguna fila que tocar.
 * Esa mitad queda garantizada por construcción —ninguna de las dos abre ya `VoidEventSheet` sobre
 * una fila— y no por este test; decir otra cosa sería prometer de más.
 *
 * Tamaño de teléfono chico, por la misma regla que `HojaAgregarGeometriaTest`: a 800×1000 entra
 * todo y la prueba no ejercitaría el desplazamiento de una hoja que hoy mide bastante más que la
 * ventana.
 *
 * **Todas las búsquedas van con `useUnmergedTree = true`**, por el mismo motivo que ya estaba
 * anotado en `HojaAgregarGeometriaTest`: el `clickable` del fondo de la hoja fusiona a todos sus
 * descendientes en un solo nodo, así que sin eso el texto lo absorbe la hoja entera — se mediría
 * la hoja y no el renglón, y la prueba sería verde sin haber mirado nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR)
class HojaDelMovimientoTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 0L, "COP")

    private val gasto = FinancialEvent(
        id = "ev-hija",
        accountId = bancolombia.id,
        type = TransactionType.EXPENSE,
        amount = 4_000_000L,
        category = "Otros",
        description = "Hija",
        timestamp = 1_754_406_000_000L,
    )

    private fun montar(event: FinancialEvent = gasto, cuentas: List<Account> = listOf(bancolombia, nu)) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(
                        event = event,
                        cuentas = cuentas,
                        onDismiss = {},
                        onCambiado = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * **La sección de corregir está, y está ARRIBA**: se ve sin ningún gesto. Es lo que el dueño
     * vino a arreglar cuando abre esta hoja frente a un saldo que no cuadra, y todo lo que quede
     * debajo de veinte renglones de categorías es, en la práctica, invisible.
     */
    @Test
    fun corregirElMontoYLaCuentaSeVeSinDesplazar() {
        montar()
        composeRule.onNodeWithText("MONTO, CUENTA Y CONCEPTO", useUnmergedTree = true).assertIsDisplayed()
        // Y con el monto y la cuenta actuales a la vista, no un id ni un campo en blanco.
        composeRule.onNodeWithText("Bancolombia", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Anular sigue estando — al final, que es donde se decidió que viva. */
    @Test
    fun anularSigueOfreciendoseAlFinal() {
        montar()
        composeRule.onNodeWithText("Anular este movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * Y «esto se repite», que es la otra acción que el detalle de la cuenta no tenía. Sobre un
     * gasto común aplica; el porqué de cuándo no, en `puedeOfrecerseComoRecurrenteDesdeElDetalle`.
     */
    @Test
    fun laHojaOfreceMarcarloComoRecurrente() {
        montar()
        composeRule.onNodeWithText("¿SE REPITE TODOS LOS MESES?", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * Sin la lista de cuentas la hoja **no se cae ni se bloquea**: lo dice y sigue dejando
     * corregir. Es el caso real de esta pantalla, donde las cuentas se piden al tocar la fila y
     * esa lectura puede fallar.
     */
    @Test
    fun sinLaListaDeCuentasLaHojaSigueSirviendo() {
        montar(cuentas = emptyList())
        composeRule.onNodeWithText("MONTO, CUENTA Y CONCEPTO", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Anular este movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * **Y la mitad de un par también se puede anular.**
     *
     * Esta es la rama que se había quedado sin la acción. Hasta el PR que unificó las dos puertas,
     * el detalle de la cuenta abría `VoidEventSheet` directo sobre *cualquier* fila, incluida la
     * pata de un traspaso o de una cuota. Al mandar las dos pantallas por [HojaDelMovimiento], la
     * rama de «esto es una pata de un par» de [ChangeCategorySheet] quedó devolviendo temprano —
     * explicador, monto, fecha— **sin pasar nunca por el bloque que ofrece anular**. O sea que una
     * cuota mal registrada, un traspaso o un pago de tarjeta dejaron de poder anularse desde
     * ningún lado, y nadie lo notó porque ninguna prueba montaba la hoja sobre una pata.
     *
     * Es justo el movimiento que MÁS necesita la salida: a una pata no se le puede cambiar la
     * cuenta ni la categoría (`PATA_NO_CAMBIA_DE_CUENTA` lo dice con todas las letras y remata
     * «anúlalo y vuelve a registrarlo desde Agregar») — un consejo que apuntaba a un botón que no
     * existía.
     */
    @Test
    fun laMitadDeUnParTambienSePuedeAnular() {
        montar(event = pataDeUnaCuota)
        composeRule.onNodeWithText("CUOTA DE CRÉDITO", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Anular este movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /** La pata del dinero de la cuota real del vehículo. */
    private val pataDeUnaCuota = FinancialEvent(
        id = "ev-cuota",
        accountId = bancolombia.id,
        type = TransactionType.EXPENSE,
        amount = 4_215_223L,
        category = CUOTA_CATEGORY,
        description = "Cuota de Vehículo 4083",
        timestamp = 1_754_406_000_000L,
        transferId = "tr-cuota",
    )

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    /**
     * **El concepto se puede corregir, y el rótulo lo dice.**
     *
     * El dueño reportó «no puedo editar los nombres de los movimientos». El campo existía desde
     * #126, pero vivía detrás de una fila rotulada «MONTO Y CUENTA»: quien venía a renombrar no
     * tenía motivo para tocar «Cambiar». Esta prueba hace el recorrido entero que él no encontró
     * —abrir, reescribir el concepto, guardar— y afirma que lo que llega al repositorio es el
     * concepto nuevo. Hasta acá ninguna prueba tipeaba en ese campo.
     */
    @Test
    fun elConceptoSeCorrigeYLlegaAlRepositorio() {
        var guardado: EdicionDeMovimiento? = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun updateEvent(id: String, cambios: EdicionDeMovimiento): FinancialEvent {
                guardado = cambios
                return gasto.copy(description = cambios.description ?: gasto.description)
            }
        }
        montar()

        composeRule.onNodeWithText("MONTO, CUENTA Y CONCEPTO", useUnmergedTree = true).assertIsDisplayed()
        // Se invoca el OnClick de la fila por semántica, no por un toque inyectado: en Robolectric el
        // toque sobre «Cambiar» (8 px de ancho) y sobre el monto no llegaba al clickable de la fila.
        // La fila se identifica por lo que contiene —el monto—, que solo está en esta sección.
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("$4.000.000")), useUnmergedTree = true)
            .onLast() // el fondo de la hoja y la hoja también son clickables y contienen el monto: el más profundo es la fila
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("CONCEPTO", useUnmergedTree = true).assertExists("la sección no se abrió")

        // El campo editable, no el título de la hoja: los dos dicen «Hija».
        composeRule.onNode(hasSetTextAction() and hasText("Hija"), useUnmergedTree = true)
            .performTextReplacement("Colegio de la hija")
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Guardar cambios")), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        val cambios = requireNotNull(guardado) { "no se llamó a updateEvent: el concepto no se guardó" }
        kotlin.test.assertEquals("Colegio de la hija", cambios.description)
        kotlin.test.assertEquals(gasto.amount, cambios.amount)
        kotlin.test.assertEquals(gasto.accountId, cambios.accountId)
    }

    /**
     * # PR 1 del rediseño de Recurrentes: «ya lo tienes» deja de ser un punto muerto.
     *
     * Hasta acá, tocar «¿Se repite todos los meses?» sobre un movimiento que YA matcheaba una
     * regla existente solo mostraba texto — «edítalo desde Recurrentes», una promesa de
     * navegación a una pantalla que este mismo rediseño va a hacer desaparecer. Esta prueba monta
     * [HojaDelMovimiento] con un repositorio de prueba donde existe una regla «Hija» (el mismo
     * nombre que el gasto sin recurrente todavía), toca el botón, y afirma que aparece una acción
     * tappeable «Editar este recurrente» — no solo el mensaje — y que tocarla abre
     * `CreateRecurringRuleSheet` en modo EDICIÓN sobre esa regla (el título cambia a «Editar
     * recurrente» y aparece «Eliminar», que solo existe en modo edición).
     */
    @Test
    fun tocarEstoSeRepiteSobreUnMovimientoYaAnotadoOfreceEditarLaRegla() {
        val reglaHija = RecurringRule(
            id = "rr-hija",
            name = "Hija",
            category = "Otros",
            amount = 4_000_000L,
            dayOfMonth = 5,
            type = TransactionType.EXPENSE,
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
            override suspend fun getRecurringRules(): List<RecurringRule> = listOf(reglaHija)
            override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), 0)
        }
        montar()

        composeRule.onNodeWithText("¿SE REPITE TODOS LOS MESES?", useUnmergedTree = true).performScrollTo()
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Sí, se repite todos los meses")), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // La acción está, tappeable — no solo el mensaje de que ya existe. El `clickable` va
        // directo sobre este `Text` (no hay una fila que lo envuelva), así que el nodo que dice
        // «Editar este recurrente» YA es el nodo con la acción de click — a diferencia de
        // «Guardar cambios» más arriba, acá no hace falta buscar un ancestro clickable.
        composeRule.onNodeWithText("Editar este recurrente", useUnmergedTree = true)
            .assertExists("no se ofreció editar la regla existente")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Se abrió CreateRecurringRuleSheet en modo EDICIÓN sobre la regla «Hija», no el alta.
        composeRule.onNodeWithText("Editar recurrente", useUnmergedTree = true)
            .assertExists("no se abrió la hoja en modo edición")
        composeRule.onNodeWithText("Eliminar", useUnmergedTree = true)
            .assertExists("«Eliminar» solo existe en modo edición")
    }

    /**
     * Y cuando lo que ya existe es una SUSCRIPCIÓN (no una regla): se explica, pero no se ofrece
     * una edición que no tiene a dónde ir — el fix del punto muerto es para la regla, no para
     * esto (ver el KDoc de `SeccionEstoSeRepite`).
     */
    @Test
    fun tocarEstoSeRepiteSobreUnaSuscripcionYaConfirmadaNoOfreceEditar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
            override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
            override suspend fun getSubscriptions(): SubscriptionsResult =
                SubscriptionsResult(listOf(suscripcionHija), 0)
        }
        montar()

        composeRule.onNodeWithText("¿SE REPITE TODOS LOS MESES?", useUnmergedTree = true).performScrollTo()
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Sí, se repite todos los meses")), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("una suscripción confirmada", useUnmergedTree = true, substring = true)
            .assertExists("no se explicó que ya está anotado como suscripción")
        composeRule.onAllNodes(hasText("Editar este recurrente"), useUnmergedTree = true).assertCountEquals(0)
    }

    private val suscripcionHija = com.jvillada.movi.shared.model.Subscription(
        id = "sub-hija",
        merchantKey = "manual_hija",
        displayName = "Hija",
        amount = 4_000_000L,
        currency = "COP",
        dayOfMonth = 5,
        status = com.jvillada.movi.shared.model.SubStatus.CONFIRMED,
        confidence = com.jvillada.movi.shared.model.SubConfidence.HIGH,
        firstSeen = 0L,
        lastSeen = 0L,
        occurrences = 3,
    )
}

/** El AVD `Movi_Sensor`: 411×731 dp. Mismo tamaño que usa `HojaAgregarGeometriaTest`. */
private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"
