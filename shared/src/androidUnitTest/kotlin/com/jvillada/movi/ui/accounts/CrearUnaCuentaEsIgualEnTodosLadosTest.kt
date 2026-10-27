package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.marcaDelBanco
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_CUENTA
import com.jvillada.movi.ui.components.ds.TarjetaDeCuenta
import com.jvillada.movi.ui.primeruso.PasoDePrimerUso
import com.jvillada.movi.ui.primeruso.PrimerUsoScreen
import com.jvillada.movi.ui.primeruso.ModoDeCaptura
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Crear una cuenta es igual en todos lados (vuelta de diseño 16)
 *
 * Antes la guía de primer uso ofrecía bancos con su marca y «Ahorros · Corriente · Efectivo ·
 * Inversión», y Patrimonio → «Nueva cuenta» ofrecía «Dinero / Inversión / Bien» en tarjetas grandes,
 * sin bancos. Ahora los dos pintan el mismo [FormularioDeCuenta] (por su tag) y, para la misma
 * elección, crean la misma cuenta con la creación de siempre. «Bien» solo en Patrimonio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class CrearUnaCuentaEsIgualEnTodosLadosTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val perfil = UserProfile(id = "u1", email = "papa@movi.test", name = "Papá", avatarColor = "#7C6CF0")

    private inner class Repo : RepositorioDePrueba() {
        val cuentasCreadas = mutableListOf<Account>()
        val eventos = mutableListOf<FinancialEvent>()
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun createAccount(account: Account): Account { cuentasCreadas += account; return account }
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent { eventos += event; return event }
        override suspend fun getDireccionDeCorreo(): DireccionDeCorreo =
            DireccionDeCorreo(token = "abc", direccion = "alertas+abc@movi.resend.app", configurado = true)
    }

    // El árbol sin fundir: el velo y el panel de una hoja son `clickable` y funden todo lo de adentro.
    // Los toques van por la acción semántica, como en `BienesEnCuentasTest`.
    private fun formularios(): Int =
        composeRule.onAllNodesWithTag(TAG_FORMULARIO_DE_CUENTA, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hayBien(): Boolean =
        composeRule.onAllNodesWithTag(TAG_TIPO_BIEN, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun seVe(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun escribir(campo: Int, texto: String) {
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[campo].performTextInput(texto)
        composeRule.waitForIdle()
    }

    /** La misma elección en los dos lugares: Bancolombia, Corriente, $1.500.000. */
    private fun elegirBancolombiaCorriente() {
        tocar("Bancolombia")
        tocar("Corriente")
        // El nombre lo propuso el banco con el tipo; el saldo es el segundo campo.
        escribir(1, "1500000")
    }

    /** Lo que las dos tienen que crear para Bancolombia + Corriente + $1.500.000. */
    private fun esLaMismaCuenta(repo: Repo) {
        val creada = repo.cuentasCreadas.single()
        assertEquals(AccountType.CHECKING, creada.type, "el mismo tipo para la misma elección")
        assertEquals("Bancolombia Corriente", creada.name, "el nombre propuesto solo, igual en los dos")
        assertEquals("COP", creada.currency)
        // La creación de siempre: la cuenta en $0 y el saldo como la apertura.
        assertEquals(0L, creada.balance)
        assertEquals(1_500_000L, repo.eventos.single().amount)
        assertEquals(creada.id, repo.eventos.single().accountId)
    }

    @Test
    fun `la guia pinta el bloque compartido, sin Bien, y crea la cuenta de siempre`() {
        val repo = Repo()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            ConClaseDeAncho {
                Box(Modifier.fillMaxSize()) {
                    PrimerUsoScreen(onNavigate = {}, pasoInicial = PasoDePrimerUso.CUENTAS, modoDeCaptura = ModoDeCaptura.REENVIO_DE_CORREO)
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, formularios(), "la guía pinta el bloque compartido")
        assertTrue(!hayBien(), "la guía no ofrece «Bien»: ahí hace falta dónde está la plata")
        elegirBancolombiaCorriente()
        tocar("Agregar cuenta")
        esLaMismaCuenta(repo)
    }

    @Test
    fun `Nueva cuenta pinta el mismo bloque, con Bien, y crea la misma cuenta`() {
        val repo = Repo()
        Repositories.sustitutoDePrueba = repo
        var creada = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    CreateAccountSheet(onDismiss = {}, onAccountCreated = { creada = true }, onElegirBien = {})
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, formularios(), "«Nueva cuenta» pinta el bloque compartido")
        assertTrue(hayBien(), "Patrimonio sí ofrece «Bien»")
        assertTrue(!seVe("Dinero"), "«Dinero» es un grupo de Patrimonio, no un tipo que se elige")
        assertTrue(seVe("Ahorros") && seVe("Efectivo") && seVe("Inversión"))
        elegirBancolombiaCorriente()
        tocar("Crear cuenta")
        assertTrue(creada)
        esLaMismaCuenta(repo)
    }

    @Test
    fun `Bien abre la hoja de bienes con lo que el duenio escribio`() {
        Repositories.sustitutoDePrueba = Repo()
        val elegidos = mutableListOf<String>()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    CreateAccountSheet(onDismiss = {}, onAccountCreated = {}, onElegirBien = { elegidos += it })
                }
            }
        }
        composeRule.waitForIdle()
        escribir(0, "La casa")
        composeRule.onNodeWithTag(TAG_TIPO_BIEN, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("La casa"), elegidos)
    }

    @Test
    fun `sin onElegirBien la hoja no ofrece Bien`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CreateAccountSheet(onDismiss = {}, onAccountCreated = {}) } }
        }
        composeRule.waitForIdle()
        assertEquals(1, formularios())
        assertTrue(!hayBien())
    }

    /**
     * Punto 3: «Bancolombia Ahorros» ya no lleva «Ahorros» a la derecha. Sin el tipo, el renglón del
     * rótulo queda con «Saldo» solo y la tarjeta mide lo mismo que su vecina con tipo: nada salta en el
     * carrusel ni en la grilla.
     */
    @Test
    fun `la tarjeta sin tipo mide lo mismo que la tarjeta con tipo`() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxWidth()) {
                    TarjetaDeCuenta(
                        nombre = "Bancolombia Ahorros", tipo = null, rotuloDelSaldo = "Saldo", saldo = 13_845_800,
                        marca = marcaDelBanco("Bancolombia"), icono = Icons.Rounded.AccountBalance, onClick = {},
                    )
                    TarjetaDeCuenta(
                        nombre = "Nu", tipo = "Ahorros", rotuloDelSaldo = "Saldo", saldo = 2_890_000,
                        marca = marcaDelBanco("Nu"), icono = Icons.Rounded.AccountBalance, onClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val tarjetas = composeRule.onAllNodesWithTag(TAG_TARJETA_DE_CUENTA, useUnmergedTree = true).fetchSemanticsNodes()
        val densidad = composeRule.density.density
        assertEquals(2, tarjetas.size)
        assertEquals(tarjetas[0].size.height, tarjetas[1].size.height, "las dos tarjetas miden lo mismo")
        assertEquals(Medida.altoDeTarjetaDeCuenta.value, tarjetas[0].size.height / densidad, 0.5f)
        // «Ahorros» aparece una sola vez: el de Nu, que no lo dice en el nombre.
        assertEquals(1, composeRule.onAllNodesWithText("Ahorros", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
