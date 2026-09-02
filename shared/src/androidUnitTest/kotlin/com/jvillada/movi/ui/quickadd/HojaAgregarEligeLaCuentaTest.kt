package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # El criterio de cuentas, ejercido desde la hoja de «Agregar» de verdad
 *
 * `CuentasQueMuevenPlataTest` (en `:core`) prueba la regla; `SelectorDeCuentaTest` prueba el
 * componente que la dibuja **recibiendo la lista ya partida**. Faltaba el pedazo del medio, que es
 * el que de verdad falla: **que [QuickAddScreen] pida la partición correcta y se la dé al
 * selector**. Ese cableado —`usoDeCuenta`, `cuentasDelPicker`, el `WalletPicker(cuentas = …)`— no
 * lo tocaba ninguna prueba: estaba sostenido solo por el compilador, que no sabe nada de si el
 * `uso` que se pasa es el de la pestaña que está abierta.
 *
 * No es una precaución teórica. Este repo ya tuvo una feature entera viviendo en una rama que
 * ningún call site alcanzaba: compilaba, tenía su prueba en verde, y no existía.
 *
 * Acá se monta la hoja entera contra un [RepositorioDePrueba] —ver [Repositories.sustitutoDePrueba],
 * la costura que lo hace posible—, se abre el selector con el dedo (bueno: con la acción
 * semántica) y se lee lo que se ve.
 *
 * Lo que NO cubre, dicho para que nadie lo lea como una garantía de más: es Robolectric, así que
 * no dice nada de iOS ni de la web, y **nada horizontal** es confiable (ver el aviso largo de
 * [HojaAgregarGeometriaTest]). Lo confiable es qué filas existen, qué dicen y qué pasa al tocarlas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD)
class HojaAgregarEligeLaCuentaTest {

    @get:Rule val composeRule = createComposeRule()

    // Las cuentas del dueño. Los nombres y las cifras son los suyos: la que abrió el caso es
    // «Vehículo 4083 · $177.200.000», que apareciendo en «¿de dónde sale este gasto?» pone la
    // cifra de lo que DEBE donde se lee un saldo.
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val nu = Account("c1", "Nu", AccountType.CREDIT_CARD, 1_240_000)
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)
    private val skandia = Account(
        "i1", "Pensión Skandia", AccountType.INVESTMENT, 106_000_000,
        condicionadaA = "Vivienda",
    )

    private val todas = listOf(ahorros, nu, carro, skandia)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * **El defecto que abrió la rama, ahora afirmado desde la pantalla.** Antes de esto, «Cuenta»
     * hacía `items(accounts)` sobre la lista entera.
     *
     * Y de paso queda fijada la decisión de la plata condicionada: la pensión voluntaria de
     * Skandia es plata del dueño —cuenta en su patrimonio— pero solo la puede retirar para
     * vivienda sin perder el beneficio tributario, así que la app no se la propone para un gasto.
     */
    @Test
    fun un_gasto_ofrece_el_banco_y_la_tarjeta_pero_no_el_credito_ni_la_plata_condicionada() {
        montarHoja(todas)
        abrirElSelectorDeCuenta()

        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Nu").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
        composeRule.onNodeWithText("Pensión Skandia").assertDoesNotExist()
    }

    /**
     * **Y no es un filtro duro.** Regla del proyecto: nada que solo se destrabe tocando código —
     * el resto de la gente no tiene a nadie al lado para editarle un `filter`. Las dos cuentas
     * están a un toque.
     */
    @Test
    fun las_excluidas_estan_a_un_toque_detras_del_ver_todas() {
        montarHoja(todas)
        abrirElSelectorDeCuenta()

        tocar("Ver todas las cuentas (2 más)")

        composeRule.onNodeWithText("Vehículo 4083").assertIsDisplayed()
        composeRule.onNodeWithText("Pensión Skandia").assertIsDisplayed()
    }

    /**
     * **La lista depende de la pestaña, y la pestaña es de verdad la que está abierta.** Si el
     * `uso` que la hoja le pasa al selector fuera constante, esta prueba sería la que lo notaría:
     * a una tarjeta no «entra» plata (lo que entra es un pago del extracto, que es la pestaña
     * «Cuota»), y a la inversión sí (un rendimiento es un ingreso legítimo, y la condición de
     * Skandia es sobre el retiro, no sobre lo que le entra).
     */
    @Test
    fun en_Ingreso_el_selector_ofrece_otra_lista() {
        montarHoja(todas)
        tocar("Ingreso")
        abrirElSelectorDeCuenta()

        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Pensión Skandia").assertIsDisplayed()
        composeRule.onNodeWithText("Nu").assertDoesNotExist()
    }

    /**
     * **La reconciliación al cambiar de pestaña**, que es el mecanismo que decide contra qué
     * cuenta se guarda y no tenía una sola prueba.
     *
     * Con estas dos cuentas la elección de la app **tiene que** cambiar sola: en «Gasto» la única
     * que sirve es la Nu; en «Ingreso» la Nu deja de servir y queda Skandia. Sin
     * `reconciliarCuenta` corriendo con la llave de la pestaña, la hoja se quedaría con una cuenta
     * que su propio selector ya no ofrece — y guardaría contra ella.
     */
    @Test
    fun cambiar_de_pestaña_mueve_la_cuenta_elegida_a_una_que_sirva() {
        montarHoja(listOf(nu, skandia.copy(condicionadaA = null)))
        composeRule.onNodeWithText("Nu").assertIsDisplayed()

        tocar("Ingreso")

        composeRule.onNodeWithText("Pensión Skandia").assertIsDisplayed()
        composeRule.onNodeWithText("Nu").assertDoesNotExist()
    }

    /**
     * **El «Ver todas» no puede quedarse desplegado para siempre.**
     *
     * El escenario real: la fila «Cuenta» es alcanzable **antes** de que la lista llegue
     * (`hasNoAccounts` exige `accountsLoaded`, que todavía es `false`), así que con la red lenta el
     * dueño abre «Agregar», toca «Cuenta» y ve una lista vacía. Con el `remember` sin llave, ese
     * primer `principales.isEmpty()` congelaba `verTodas = true`; llegaban las cuentas y se
     * dibujaban **todas, desplegadas**, con «Vehículo 4083 · $177.200.000» en el medio. La
     * pantalla que esta rama vino a arreglar, servida por el arreglo.
     */
    @Test
    fun el_ver_todas_se_vuelve_a_plegar_cuando_las_cuentas_llegan_tarde() {
        val cuandoLlegue = CompletableDeferred<List<Account>>()
        montarHoja(cuentasDiferidas = cuandoLlegue)

        // La red todavía no contestó y el dueño ya abrió el selector: lo ve vacío.
        abrirElSelectorDeCuenta()
        composeRule.onNodeWithText("No tienes cuentas todavía.").assertIsDisplayed()

        cuandoLlegue.complete(todas)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Bancolombia Ahorros").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
        composeRule.onNodeWithText("Ver todas las cuentas (2 más)").assertIsDisplayed()
    }

    /**
     * **[QuickAddScreen.presetAccountId] con una tarjeta: se honra.**
     *
     * Hay un solo camino que lo usa —«+ Registrar el primero» en el estado vacío del detalle de una
     * cuenta— y esto fija que desde el detalle de la Nu la hoja abre con la Nu puesta. Sin esto, el
     * comportamiento deliberado del punto siguiente sería indistinguible de un preset roto.
     */
    @Test
    fun el_preset_de_una_tarjeta_queda_elegido() {
        montarHoja(todas, preset = nu.id)

        composeRule.onNodeWithText("Nu").assertIsDisplayed()
    }

    /**
     * **[QuickAddScreen.presetAccountId] con un crédito: NO se honra, y es a propósito.**
     *
     * Abrir la hoja desde el detalle del crédito del vehículo no alcanza para poner el crédito como
     * origen de un gasto: `reconciliarCuenta` resuelve el defecto sobre `principales` **sin**
     * `conservar`, así que el preset cae en la última usada y de ahí en la primera de la lista. Lo
     * que la app elige sola sale siempre de lo que está a la vista — «mostrarla» y «elegirla por su
     * cuenta» son dos permisos distintos, y la app solo tiene el primero.
     */
    @Test
    fun el_preset_de_un_credito_no_queda_elegido() {
        montarHoja(todas, preset = carro.id)

        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
    }

    /**
     * **Lo único que separa a la hoja de guardar contra un crédito.**
     *
     * Con solo el crédito del vehículo, `reconciliarCuenta` no encuentra ninguna candidata y deja
     * la cuenta en `null` — eso es a propósito, y es lo que fija
     * [el_preset_de_un_credito_no_queda_elegido] por el lado de la elección. Lo que fija esta
     * prueba es el lado del guardado: que el botón **no guarde**.
     *
     * No es una precaución teórica. En el renglón del `accountId` vivía
     * `?: accounts.firstOrNull()?.id ?: "acc_1"`: si `canSave` dejara pasar este estado, el
     * movimiento se anotaba contra la primera cuenta de la lista, que acá es el crédito. Ese
     * respaldo ya no está, y el `?: return` que lo reemplazó **depende** de esta guarda; por eso
     * la guarda ahora tiene una prueba y no una nota al pie.
     *
     * El doble de prueba es el detector: `RepositorioDePrueba.postEvent` explota con su nombre. Si
     * mañana alguien afloja `canSave`, esto no se pone rojo por una afirmación sutil — se pone
     * rojo con «esta prueba no esperaba que la pantalla llamara a postEvent()».
     */
    @Test
    fun con_una_sola_cuenta_que_no_sirve_el_boton_no_guarda_nada() {
        montarHoja(listOf(carro))

        tocar("5")
        tocar("000")
        tocar("0")

        composeRule.onNodeWithText("Falta la cuenta").assertIsDisplayed()

        tocar("Guardar movimiento")

        // Sigue diciendo lo que falta: no guardó, no se cerró, no inventó una cuenta.
        composeRule.onNodeWithText("Falta la cuenta").assertIsDisplayed()
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    /**
     * La hoja como la monta `App.kt`: dentro del área de contenido, con la barra inferior de 64 dp
     * ocupando su lugar debajo — el mismo andamio de [HojaAgregarGeometriaTest], por el mismo
     * motivo (sin ese hueco la ventana da 64 dp de más y deja de parecerse al teléfono).
     *
     * La diferencia está abajo: acá `Repositories.wallets` es un [RepositorioDePrueba] que contesta
     * la lista de cuentas, en vez de un cliente HTTP apuntando a producción que falla.
     */
    private fun montarHoja(
        cuentas: List<Account> = emptyList(),
        preset: String? = null,
        cuentasDiferidas: CompletableDeferred<List<Account>>? = null,
    ) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> =
                cuentasDiferidas?.await() ?: cuentas
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {}, presetAccountId = preset)
                    }
                    Spacer(Modifier.height(ALTO_BARRA_INFERIOR))
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Abre el sub-picker de «Cuenta».
     *
     * Se toca ANTES de que el encabezado del propio sub-picker exista, porque ese encabezado dice
     * «Cuenta» también y `onNodeWithText` no elegiría por nosotros.
     */
    private fun abrirElSelectorDeCuenta() = tocar("Cuenta")

    /**
     * Por qué `performSemanticsAction` y no `performClick`: bajo Robolectric el click no llega al
     * composable —no falla, simplemente no pasa nada— así que una prueba escrita con `performClick`
     * sería verde sin haber probado nada. Ver la nota larga en [HojaAgregarGeometriaTest].
     */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}

/**
 * El AVD `Movi_Sensor`: 411×731 dp. Es el mismo tamaño que usa [HojaAgregarGeometriaTest] y por el
 * mismo motivo: a 800×1000 entra todo y no se ejercita nada. Acá lo que se mide no es geometría,
 * pero el sub-picker sí tiene un tope de alto (360 dp) y una `LazyColumn` no compone lo que no se
 * ve — con una ventana ficticiamente grande, «la fila existe» probaría el viewport y no el
 * criterio.
 */
private const val TELEFONO_DEL_AVD = "w411dp-h731dp-xhdpi"

/** `MinBottomNav.kt:63` — leído del código, no estimado. */
private val ALTO_BARRA_INFERIOR = 64.dp
