package com.jvillada.movi.ui.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.LocalRefreshTick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Una lectura vieja de los vencimientos no pisa a la nueva.**
 *
 * Una recarga cancela la lectura en vuelo, pero la cancelación no es instantánea: con la red de
 * verdad, la lectura vieja puede terminar —y correr su `finally`— cuando la nueva ya arrancó. Si
 * eso apagaba `leyendoVencimientos`, la tarjeta del disponible de Plan dejaba de decir
 * «Actualizando…» con la lectura nueva todavía en el aire; y si escribía sus listas, pisaba las
 * de la nueva con lo de antes. Acá la vieja se traba a propósito (`NonCancellable`) hasta después
 * de que arrancó la nueva.
 */
@RunWith(RobolectricTestRunner::class)
class VencimientosQueSeCruzanTest {

    @get:Rule val composeRule = createComposeRule()

    private val vieja = CompletableDeferred<Unit>()
    private val nueva = CompletableDeferred<Unit>()
    private var llamadas = 0

    private fun pago(nombre: String) = UpcomingPayment(
        rule = RecurringRule(id = "rr_$nombre", name = nombre, category = "Vivienda", amount = 1_000L, dayOfMonth = 1, type = TransactionType.EXPENSE),
        dueDate = "2026-09-01",
        daysUntil = 0,
        status = PaymentStatus.DUE_TODAY,
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> {
            llamadas++
            return if (llamadas == 1) {
                withContext(NonCancellable) { vieja.await() }
                listOf(pago("Vieja"))
            } else {
                nueva.await()
                listOf(pago("Nueva"))
            }
        }
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    @Test
    fun `la lectura vieja no apaga el leyendo de la nueva ni pisa sus listas`() {
        val tick = mutableStateOf(0)
        var estado: EstadoDelTableroDeRecurrentes? = null
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalRefreshTick provides tick.value) {
                    estado = rememberTableroMontadoSolo(vencimientosSiempre = true).estado
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { llamadas == 1 }
        assertTrue(estado!!.leyendoVencimientos)

        tick.value++
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { llamadas == 2 }

        vieja.complete(Unit)
        composeRule.waitForIdle()
        assertTrue(estado!!.leyendoVencimientos, "la nueva sigue en el aire: se sigue leyendo")
        assertTrue(estado!!.upcomingRecurrentes.none { it.rule.name == "Vieja" }, "lo de la vieja no se escribe")

        nueva.complete(Unit)
        composeRule.waitForIdle()
        assertTrue(!estado!!.leyendoVencimientos)
        assertEquals(listOf("Nueva"), estado!!.upcomingRecurrentes.map { it.rule.name })
    }
}
