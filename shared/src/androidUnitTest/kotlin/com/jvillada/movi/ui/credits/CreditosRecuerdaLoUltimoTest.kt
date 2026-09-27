package com.jvillada.movi.ui.credits

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.CacheDeLecturas
import com.jvillada.movi.data.ClaveDeLectura
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
import kotlinx.datetime.Clock
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
    private var tarjetas: suspend () -> List<CardSummary> = { emptyList() }
    private var perfil: suspend () -> UserProfile = {
        UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
    }

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getCredits(): List<CreditSummary> = creditos()
        override suspend fun getCards(): List<CardSummary> = tarjetas()
        override suspend fun getUserProfile(): UserProfile = perfil()
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
        // Ni el vacío, ni lo que presupone deudas: «Deuda total $0» saldría de una lectura caída.
        assertEquals(0, visitas.cuantasConTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA))
        assertEquals(0, visitas.cuantas("Deuda total"))
        assertEquals(0, visitas.cuantas("Nuevo crédito", substring = true))
        assertEquals(0, visitas.cuantas("No pudimos cargar tus créditos"), "un solo aviso")
    }

    /**
     * Préstamos y tarjetas contestaron vacíos EN ESTA visita; lo que falló es el perfil, que acá
     * solo nombra el mes de la última cuota. El vacío es de esta visita: se enseña, como siempre.
     */
    @Test
    fun `si solo falla el perfil con uno recordado, el vacio de esta visita se ensena`() {
        creditos = { emptyList() }
        visitas.primeraYSalir()
        perfil = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Aquí van tus créditos y tarjetas"))
        assertEquals(0, visitas.cuantasConTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA))
        assertEquals(0, visitas.cuantas("Deuda total"))
    }

    /**
     * Una lista recordada falla y la otra falla sin nada recordado: no se puede pintar nada, y lo
     * dice UN aviso — el de «No pudimos cargar», no los dos a la vez.
     */
    @Test
    fun `sin nada que pintar, un solo aviso`() {
        // Recordados los préstamos, no las tarjetas; y las dos lecturas de esta visita caen.
        CacheDeLecturas.guardar(ClaveDeLectura.Creditos, listOf(prestamo), "u1", Clock.System.now().toEpochMilliseconds())
        creditos = { error("sin red") }
        tarjetas = { error("sin red") }

        visitas.montar()
        composeRule.waitForIdle()

        assertEquals(1, visitas.cuantas("No pudimos cargar tus créditos"))
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
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
