package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # El selector de «¿de dónde sale la plata?», dibujado de verdad
 *
 * `CuentasQueMuevenPlataTest` (en `:core`) prueba el criterio; esta clase prueba que el criterio
 * **llega a la pantalla**. No es una distinción académica: este repo ya tuvo una función pura
 * correcta, con su prueba verde, colgando de una rama que ningún camino de la app alcanzaba — o
 * sea una feature que no existía. Acá se monta el selector y se lee lo que se ve.
 *
 * Lo que NO cubre: es Robolectric, así que no dice nada de iOS ni de la web, y la disposición
 * horizontal es ficticia (ver el aviso largo de `HojaAgregarGeometriaTest`). Lo que sí es
 * confiable es qué filas existen, qué dicen y qué pasa al tocarlas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class SelectorDeCuentaTest {

    @get:Rule val composeRule = createComposeRule()

    // Las cuentas del dueño, recortadas a cuatro para que la lista entera entre en el alto del
    // sub-picker (360 dp): una `LazyColumn` no compone lo que no se ve, y una fila no compuesta
    // haría que estas afirmaciones probaran el viewport en vez del criterio.
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val nu = Account("c1", "Nu", AccountType.CREDIT_CARD, 1_240_000)
    private val masterBlack = Account(
        "c2", "Master Black 3684", AccountType.CREDIT_CARD, balance = 0,
        currency = "USD", balancesByCurrency = mapOf("USD" to 181),
    )
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)

    private val todas = listOf(ahorros, nu, masterBlack, carro)

    private fun montar(
        uso: UsoDeCuenta = UsoDeCuenta.ORIGEN_DE_GASTO,
        cuentas: List<Account> = todas,
        elegida: String? = ahorros.id,
    ) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    WalletPicker(
                        cuentas = cuentasPara(cuentas, uso, conservar = elegida),
                        uso = uso,
                        selectedId = elegida,
                        onPick = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * **El defecto que abrió esta rama.** Al elegir de dónde sale un gasto aparecía «Vehículo
     * 4083 · $177.200.000»: un crédito ya desembolsado, con la cifra de lo que DEBE puesta donde
     * se lee un saldo.
     */
    @Test
    fun un_gasto_no_ofrece_el_credito_del_vehiculo() {
        montar()
        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Nu").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
    }

    /**
     * **Y sin embargo no es un filtro duro.** El proyecto tiene una regla: nada que solo se
     * destrabe tocando código. El crédito está a un toque.
     */
    @Test
    fun el_credito_sigue_estando_detras_de_ver_todas() {
        montar()
        // El nombre lleva el conteo adentro a propósito: dice cuánto hay del otro lado antes de
        // abrirlo. Si mañana cambia el texto, esta prueba se rompe — y está bien, porque este es
        // el único camino que le queda al dueño hacia esas cuentas.
        composeRule.onNodeWithText("Ver todas las cuentas (1 más)")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Vehículo 4083").assertIsDisplayed()
    }

    /**
     * **Punto 4: la cuenta ya elegida no puede desaparecer.** Si un movimiento (o una regla)
     * viejo apunta a una cuenta que hoy no se ofrecería, el selector la muestra igual —arriba y
     * marcada— en vez de esconderla y dejar la fila diciendo una cosa y la lista otra.
     */
    @Test
    fun la_cuenta_ya_elegida_se_ve_aunque_hoy_no_se_ofrezca() {
        montar(elegida = carro.id)
        composeRule.onNodeWithText("Vehículo 4083").assertIsDisplayed()
    }

    /**
     * **Deber no es tener.** Desde que la tarjeta se ofrece para gastar —que es el pedido— el
     * renglón de abajo tiene que decir qué es esa cifra.
     */
    @Test
    fun bajo_una_tarjeta_el_renglon_dice_que_se_debe() {
        montar()
        composeRule.onNodeWithText("Debes $1.240.000").assertIsDisplayed()
        composeRule.onNodeWithText("$15.534.069").assertIsDisplayed()
    }

    /**
     * **La «Master Black 3684 USD» decía «$0».** `balance` es el componente en pesos de la
     * cuenta, así que una tarjeta en dólares mostraba un cero con el signo de otra moneda.
     */
    @Test
    fun una_cuenta_en_dolares_se_muestra_en_dolares() {
        montar()
        composeRule.onNodeWithText("Debes US$181").assertIsDisplayed()
    }

    /**
     * **Quien no tiene créditos ni inversión no ve nada nuevo.** La pantalla de la mayoría queda
     * exactamente como estaba: sin pie, sin plegado, sin una pregunta más que contestar.
     */
    @Test
    fun sin_cuentas_excluidas_no_aparece_el_pie() {
        montar(cuentas = listOf(ahorros, nu))
        composeRule.onNodeWithText("Ver todas las cuentas (1 más)").assertDoesNotExist()
        composeRule.onNodeWithText("Ver todas las cuentas (2 más)").assertDoesNotExist()
    }

    /**
     * **Un ingreso ofrece otra lista.** A una tarjeta no «entra» plata: lo que entra es un pago
     * del extracto, y eso es la pestaña «Cuota».
     */
    @Test
    fun un_ingreso_no_ofrece_las_tarjetas() {
        montar(uso = UsoDeCuenta.DESTINO_DE_INGRESO)
        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Nu").assertDoesNotExist()
    }

    /**
     * **Deber de menos es tener.** Si el dueño sobrepagó la Nu, su balance queda en negativo y eso
     * es plata **a favor**: «Debes −$50.000» dice dos cosas opuestas en cuatro palabras. La
     * inversión del signo sale de `saldoDeDeuda`, la misma que usa la tarjeta grande del detalle de
     * la cuenta desde F36.
     */
    @Test
    fun una_tarjeta_sobrepagada_no_dice_que_debes_menos_cero() {
        montar(cuentas = listOf(ahorros, nu.copy(balance = -50_000)))

        composeRule.onNodeWithText("A favor $50.000").assertIsDisplayed()
        composeRule.onNodeWithText("Debes −$50.000").assertDoesNotExist()
    }

    /**
     * **Sin red, una cifra en pesos no se rotula con el símbolo del dólar.**
     *
     * `LocalRepository.toAccountModel` arma el `Account` desde la fila de SQLDelight, que guarda
     * `balance` y `currency` pero **no** `balancesByCurrency`. El respaldo `?: account.balance`
     * tomaba entonces el componente en PESOS y lo escribía con la moneda de la cuenta: «Debes
     * US$1.500.000» sobre una cifra en COP. Antes de la rama decía «$0» — menos informativo, y
     * verdadero. Ver `saldoEnSuMoneda`, que devuelve el monto **y** con qué moneda escribirlo.
     */
    @Test
    fun sin_saldo_por_moneda_la_cifra_se_dice_en_pesos() {
        val sinRed = Account("c3", "Master Black 3684", AccountType.CREDIT_CARD, 1_500_000, currency = "USD")
        montar(cuentas = listOf(ahorros, sinRed))

        composeRule.onNodeWithText("Debes $1.500.000").assertIsDisplayed()
        composeRule.onNodeWithText("Debes US$1.500.000").assertDoesNotExist()
    }

    /**
     * **El «Ver todas» no se queda desplegado para siempre.**
     *
     * Con `remember` sin llave, el valor inicial (`principales.isEmpty()`) se congelaba en la
     * primera composición: el selector abierto con la lista todavía vacía quedaba desplegado, y al
     * llegar las cuentas se dibujaban TODAS —con el crédito del vehículo en el medio— sin que nadie
     * lo hubiera pedido. Acá se recompone el mismo selector con las cuentas puestas y se afirma que
     * volvió a plegarse solo.
     */
    @Test
    fun el_ver_todas_se_vuelve_a_plegar_cuando_las_cuentas_llegan() {
        val cuentas = mutableStateOf(emptyList<Account>())
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    WalletPicker(
                        cuentas = cuentasPara(cuentas.value, UsoDeCuenta.ORIGEN_DE_GASTO),
                        uso = UsoDeCuenta.ORIGEN_DE_GASTO,
                        selectedId = null,
                        onPick = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No tienes cuentas todavía.").assertIsDisplayed()

        cuentas.value = todas
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
        composeRule.onNodeWithText("Ver todas las cuentas (1 más)").assertIsDisplayed()
    }

    /**
     * **Y sin embargo, cuando arriba no queda ninguna, se abre desplegado.** Quien solo tiene
     * créditos abriría el selector y no vería nada, con la única salida escondida detrás de un
     * renglón que parece un pie de página: un callejón sin salida disfrazado de lista.
     */
    @Test
    fun con_principales_vacia_se_abre_desplegado() {
        montar(cuentas = listOf(carro), elegida = null)

        composeRule.onNodeWithText("Vehículo 4083").assertIsDisplayed()
        composeRule.onNodeWithText("Ver solo las de siempre").assertIsDisplayed()
    }
}
