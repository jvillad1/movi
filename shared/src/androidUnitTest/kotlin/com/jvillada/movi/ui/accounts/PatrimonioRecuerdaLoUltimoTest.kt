package com.jvillada.movi.ui.accounts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.ui.VisitasDePrueba
import com.jvillada.movi.ui.components.TEXTO_ACTUALIZANDO
import com.jvillada.movi.ui.components.TEXTO_NO_PUDIMOS_ACTUALIZAR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Volver a Patrimonio no vuelve a cargar todo
 *
 * Mismo contrato que `MovimientosRecuerdaLoUltimoTest`: la segunda visita pinta las cuentas al
 * primer cuadro con «Actualizando…»; una lectura que falla con las cuentas a la vista lo dice y
 * no las borra; una escritura entre visitas o una persona distinta vuelven al esqueleto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PatrimonioRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)

    private var cuentas: suspend () -> List<Account> = { listOf(nu) }

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = cuentas()
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun deleteDestino(id: String) = Unit
    }

    private val visitas = VisitasDePrueba(composeRule) { AccountsScreen(onNavigate = {}) }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esqueleto() = visitas.cuantasConTag(TAG_ESQUELETO_DEL_PATRIMONIO)

    @Test
    fun `la segunda visita pinta las cuentas en el primer cuadro con Actualizando, y al contestar lo quita`() {
        val puerta = CompletableDeferred<List<Account>>()
        visitas.primeraYSalir()
        cuentas = { puerta.await() }

        visitas.volverAlPrimerCuadro()

        assertEquals(1, visitas.cuantas("Nu"))
        assertEquals(1, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, esqueleto())

        visitas.seguir()
        puerta.complete(listOf(nu))
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(1, visitas.cuantas("Nu"))
    }

    @Test
    fun `si la lectura falla con las cuentas a la vista, se dice y se quedan`() {
        visitas.primeraYSalir()
        cuentas = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Nu"))
        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("No pudimos cargar tus cuentas"))

        cuentas = { listOf(nu) }
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<Account>>()
        cuentas = { puerta.await() }
        runBlocking { Repositories.wallets.deleteDestino("d-otra") }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Nu"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<Account>>()
        cuentas = { puerta.await() }
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        visitas.volver()

        assertEquals(0, visitas.cuantas("Nu"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }
}
