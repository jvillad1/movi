package com.jvillada.movi.ui.recurrentes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ola V (revisión final, I2) — **la hoja de crear/editar un recurrente ofrece asociar un destino
 * conocido**, que hasta esta prueba no tenía ninguna cobertura de UI (solo la lógica del wire en
 * `RecurrentesLogicTest`).
 *
 * `MarcoDeHoja` funde casi todo el texto suelto de la hoja en un único nodo de semántica
 * (`marco-de-hoja-panel`), así que estas pruebas se quedan en lo que se puede verificar sin
 * simular una interacción de verdad sobre esa fusión — que la sección exista, con el rótulo
 * correcto, y que una regla que YA tiene un destino guardado lo muestre al abrirse en edición.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class DestinoConocidoEnRecurrenteTest {

    @get:Rule val composeRule = createComposeRule()

    private val caro = DestinoConocido(id = "dst-caro", nombre = "Caro", numero = "31973270756")

    private open inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = listOf(caro)
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `un recurrente nuevo de gasto ofrece la seccion de destino conocido`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}) }
        }
        // `assertExists`, no `assertIsDisplayed`: la hoja recorta con `verticalScroll` y la
        // sección vive lejos en la lista de campos — lo que importa acá es que la composición la
        // ofrezca, no que ya esté a la vista sin haber rodado.
        composeRule.onNodeWithText("PERSONA O COMERCIO (OPCIONAL)").assertExists()
    }

    /**
     * Editar una regla que ya tiene un destino guardado abre mostrando ese nombre, no «Sin
     * destino» — el mismo prellenado de tres estados que ya prueba `RecurrentesLogicTest` del
     * lado del wire, ahora del lado de lo que el dueño ve al reabrir la hoja.
     */
    @Test
    fun `editar una regla con destino guardado muestra el nombre del destino`() {
        Repositories.sustitutoDePrueba = Repo()
        val tiaCaro = RecurringRule(
            id = "rr-tia-caro", name = "Tía Caro", category = "Familia", amount = 100_000,
            dayOfMonth = 1, type = TransactionType.EXPENSE, destinoConocidoId = "dst-caro",
        )
        composeRule.setContent {
            MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}, existing = tiaCaro) }
        }
        composeRule.onNodeWithText("Caro").assertExists()
    }
}
