package com.jvillada.movi.ui.credits

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
import com.jvillada.movi.shared.model.UserProfile
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
 * # Volver a Créditos no vuelve a cargar todo
 *
 * Mismo contrato que `MovimientosRecuerdaLoUltimoTest`, sobre préstamos, tarjetas y perfil.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class CreditosRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val prestamo = CreditSummary(
        account = Account("acc_viejo", "Préstamo viejo", AccountType.LOAN, balance = 3_000_000L),
        terms = null,
        paidPct = null,
    )

    private var creditos: suspend () -> List<CreditSummary> = { listOf(prestamo) }

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getCredits(): List<CreditSummary> = creditos()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
        override suspend fun deleteDestino(id: String) = Unit
    }

    private val visitas = VisitasDePrueba(composeRule) { CreditosScreen(onNavigate = {}) }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esqueleto() = visitas.cuantasConTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA)

    @Test
    fun `la segunda visita pinta los creditos en el primer cuadro con Actualizando, y al contestar lo quita`() {
        val puerta = CompletableDeferred<List<CreditSummary>>()
        visitas.primeraYSalir()
        creditos = { puerta.await() }

        visitas.volverAlPrimerCuadro()

        assertEquals(1, visitas.cuantas("Préstamo viejo"))
        assertEquals(1, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, esqueleto())

        visitas.seguir()
        puerta.complete(listOf(prestamo))
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(1, visitas.cuantas("Préstamo viejo"))
    }

    @Test
    fun `si la lectura falla con los creditos a la vista, se dice y se quedan`() {
        visitas.primeraYSalir()
        creditos = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Préstamo viejo"))
        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("No pudimos cargar tus créditos"))

        creditos = { listOf(prestamo) }
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
    }

    @Test
    fun `sin creditos recordados y la lectura caida, no se afirma el vacio`() {
        creditos = { emptyList() }
        visitas.primeraYSalir()
        creditos = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("Aquí van tus créditos y tarjetas"))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<CreditSummary>>()
        creditos = { puerta.await() }
        runBlocking { Repositories.wallets.deleteDestino("d-otra") }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Préstamo viejo"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<CreditSummary>>()
        creditos = { puerta.await() }
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        visitas.volver()

        assertEquals(0, visitas.cuantas("Préstamo viejo"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }
}
