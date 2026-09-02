package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # El selector que las pantallas de lo que llegó del banco no tenían
 *
 * `CuentaDelBancoTest` prueba a qué cuenta va un SMS o un extracto; esta clase prueba que, cuando
 * Movi le erra —o cuando no hay ninguna candidata y no resuelve nada—, **hay un camino hacia
 * adelante**. Antes de esto la cuenta se mostraba de solo lectura: si estaba mal, la única salida
 * era no confirmar.
 *
 * Lo que NO cubre: es Robolectric, así que no dice nada de iOS ni de la web.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ListaDeCuentasElegiblesTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val nu = Account("c1", "Nu", AccountType.CREDIT_CARD, 1_240_000)
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)
    private val todas = listOf(ahorros, nu, carro)

    private var elegida: String? = null

    private fun montar(
        cuentas: List<Account> = todas,
        uso: UsoDeCuenta = UsoDeCuenta.CUENTA_DEL_EXTRACTO,
        inicial: String? = null,
    ) {
        composeRule.setContent {
            var sel by remember { mutableStateOf(inicial) }
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    ListaDeCuentasElegibles(
                        cuentas = cuentasPara(cuentas, uso, conservar = sel),
                        uso = uso,
                        selectedId = sel,
                        onPick = { sel = it; elegida = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * **El destino de un extracto ya no puede ser un crédito por accidente.** El «Vehículo 4083»
     * era la salida de `accounts.firstOrNull()` cuando el nombre del banco no coincidía con nada.
     */
    @Test
    fun el_extracto_no_ofrece_el_credito_del_vehiculo() {
        montar()
        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()
        composeRule.onNodeWithText("Nu").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
    }

    /** Y sin embargo no es un filtro duro: está a un toque, como en el resto de la app. */
    @Test
    fun el_credito_sigue_estando_detras_de_ver_todas() {
        montar()
        composeRule.onNodeWithText("Ver todas las cuentas (1 más)")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Vehículo 4083").assertIsDisplayed()
    }

    /** Lo que esta ola agrega de verdad: la cuenta se puede cambiar con el dedo. */
    @Test
    fun tocar_una_cuenta_la_elige() {
        elegida = null
        montar(inicial = ahorros.id)
        composeRule.onNodeWithText("Nu").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(nu.id, elegida)
    }

    /**
     * **El callejón sin salida que el criterio nuevo podría abrir.** Quien solo tiene créditos no
     * recibe ninguna cuenta resuelta —eso es a propósito—, así que si además el selector abriera
     * vacío no le quedaría ningún camino. Abre desplegado.
     */
    @Test
    fun sin_ninguna_candidata_el_selector_abre_desplegado() {
        montar(cuentas = listOf(carro))
        composeRule.onNodeWithText("Vehículo 4083").assertIsDisplayed()
        composeRule.onNodeWithText("Ver solo las de siempre").assertIsDisplayed()
    }

    @Test
    fun sin_cuentas_lo_dice_en_vez_de_mostrar_una_lista_vacia() {
        montar(cuentas = emptyList())
        composeRule.onNodeWithText("No tienes cuentas todavía.").assertIsDisplayed()
    }
}
