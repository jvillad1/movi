package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola 16 — **un cobro anual pesa la doceava parte en el mes, y la pantalla lo dice.**
 *
 * Los montos son los cuatro cobros reales del dueño (Google One, YouTube Premium Family, NBA
 * League Pass y HBO Max Platinum), los mismos que fija `PeriodicidadDeCobroTest` en `:core`. Acá
 * se prueba la mitad del cliente: que [resumenRecurrentes] use el prorrateado —también en la rama
 * que suma fila por fila, que es la que NO puede apoyarse en el total del server— y que los
 * textos digan las dos cosas que hay que decir, el cobro real y su equivalente mensual.
 */
class SuscripcionAnualTest {

    private fun sub(
        nombre: String,
        monto: Long,
        periodicidad: PeriodicidadDeCobro = PeriodicidadDeCobro.MENSUAL,
        moneda: String = "COP",
        dia: Int = 5,
    ) = Subscription(
        id = "s_$nombre", merchantKey = "manual_${nombre.lowercase()}", displayName = nombre,
        amount = monto, currency = moneda, dayOfMonth = dia, status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 0,
        periodicidad = periodicidad,
    )

    private fun regla(nombre: String, monto: Long, dia: Int = 5) = RecurringRule(
        id = "r_$nombre", name = nombre, category = "Otros", amount = monto,
        dayOfMonth = dia, type = TransactionType.EXPENSE,
    )

    private val googleOne = sub("Google One", 79_000L)
    private val youTube = sub("YouTube Premium Family", 47_900L)
    private val nba = sub("NBA League Pass", 112_900L, PeriodicidadDeCobro.ANUAL, dia = 4)
    private val hboMax = sub("HBO Max Platinum", 369_900L, PeriodicidadDeCobro.ANUAL, dia = 28)

    // ── El total ──────────────────────────────────────────────────────────────

    /**
     * La rama que suma fila por fila (hay una exclusión, así que no puede usar el total cerrado
     * del server). Es la que tenía que aprender a prorratear: si se le olvidara, el «Flujo libre»
     * del cliente y el del server dirían cosas distintas sobre la misma plata.
     */
    @Test
    fun `los gastos cuentan el cobro anual dividido en doce, no entero`() {
        val resumen = resumenRecurrentes(
            rules = listOf(regla("Arriendo", 1_800_000L)),
            subs = SubscriptionsResult(
                subscriptions = listOf(googleOne, youTube, nba, hboMax, sub("Arriendo", 1_800_000L)),
                // A propósito ABSURDO: hay una fila excluida por duplicada, así que el cliente
                // suma fila por fila y este total no se usa. Si alguna vez lo usara, se vería.
                monthlyTotalCop = 999_999_999L,
                usdToCop = 4_000.0,
            ),
        )

        // 1.800.000 (arriendo) + 79.000 + 47.900 + 9.409 (NBA) + 30.825 (HBO) — y el «Arriendo»
        // de la suscripción no suma porque ya está como regla.
        assertEquals(1_967_134L, resumen.gastos)
        assertTrue(resumen.hayCobrosAnuales, "hay que explicar por qué el total no es la suma de la lista")
    }

    /** Y el error que esto vino a matar, dicho al derecho, fila por fila. */
    @Test
    fun `sin prorratear, los dos cobros anuales inflarian el mes por 482800`() {
        val real = copDeSuscripcion(nba, usdToCop = 0.0)!! + copDeSuscripcion(hboMax, usdToCop = 0.0)!!
        assertEquals(40_234L, real, "9.409 + 30.825: lo que de verdad le cuestan al mes")
        assertEquals(
            482_800L,
            nba.amount + hboMax.amount,
            "lo que le habría descontado del mes anotarlos sin periodicidad — doce veces de más",
        )
    }

    /**
     * Sin exclusiones, el cliente usa el total cerrado del server tal cual (ver
     * [resumenRecurrentes]) — o sea que el prorrateo lo hizo el server. Lo que el cliente sí tiene
     * que saber por su cuenta es que ahí adentro hay un cobro anual, para poder explicarlo.
     */
    @Test
    fun `sin exclusiones el total viene del server y el aviso igual aparece`() {
        val resumen = resumenRecurrentes(
            rules = emptyList(),
            subs = SubscriptionsResult(listOf(googleOne, hboMax), monthlyTotalCop = 109_825L, usdToCop = 0.0),
        )
        assertEquals(109_825L, resumen.gastos, "79.000 + 30.825, calculado por el server")
        assertTrue(resumen.hayCobrosAnuales)
    }

    @Test
    fun `sin cobros anuales no se explica ningun prorrateo`() {
        val resumen = resumenRecurrentes(
            rules = emptyList(),
            subs = SubscriptionsResult(listOf(googleOne, youTube), monthlyTotalCop = 126_900L, usdToCop = 0.0),
        )
        assertEquals(126_900L, resumen.gastos)
        assertFalse(resumen.hayCobrosAnuales)
    }

    /** Mira lo que ENTRÓ al total, igual que [ResumenRecurrentes.hayMonedaExtranjera]. */
    @Test
    fun `un cobro anual excluido por duplicado no dispara la explicacion`() {
        val resumen = resumenRecurrentes(
            rules = listOf(regla("HBO Max Platinum", 369_900L)),
            subs = SubscriptionsResult(listOf(googleOne, hboMax), monthlyTotalCop = 0L, usdToCop = 0.0),
        )
        assertEquals(369_900L + 79_000L, resumen.gastos, "la regla sí suma entera; la suscripción no suma")
        assertFalse(resumen.hayCobrosAnuales, "no se explica un prorrateo que no se usó")
    }

    /** El prorrateo va ANTES de la TRM, en el mismo orden que el server. */
    @Test
    fun `un cobro anual en dolares se divide primero y se convierte despues`() {
        val anualEnDolares = sub("Dominio", 120L, PeriodicidadDeCobro.ANUAL, moneda = "USD")
        // 120 / 12 = 10 USD al mes → 10 × 4.000 = 40.000 COP.
        assertEquals(40_000L, copDeSuscripcion(anualEnDolares, usdToCop = 4_000.0))
    }

    @Test
    fun `un cobro mensual sigue valiendo exactamente lo que valia`() {
        assertEquals(79_000L, copDeSuscripcion(googleOne, usdToCop = 0.0))
        assertNull(copDeSuscripcion(sub("Claude", 12L, moneda = "USD"), usdToCop = 0.0), "USD sin tasa")
    }

    // ── Los textos ────────────────────────────────────────────────────────────

    @Test
    fun `la fila de un cobro anual muestra el cobro real con su periodicidad`() {
        // El cobro que el dueño puede buscar en el extracto, NUNCA el prorrateado — y con las dos
        // palabras que impiden leerlo como un gasto del mes al lado de las filas mensuales.
        assertEquals("−$369.900 al año", textoDelMontoDeSuscripcion(hboMax, conSigno = true))
        assertEquals("$112.900 al año", textoDelMontoDeSuscripcion(nba))
    }

    @Test
    fun `la fila de un cobro mensual no dice nada de periodicidad`() {
        assertEquals("−$79.000", textoDelMontoDeSuscripcion(googleOne, conSigno = true))
        assertEquals("$47.900", textoDelMontoDeSuscripcion(youTube))
    }

    @Test
    fun `un cobro anual en dolares se lee en su moneda`() {
        val anualEnDolares = sub("Dominio", 120L, PeriodicidadDeCobro.ANUAL, moneda = "USD")
        assertEquals("−US$120 al año", textoDelMontoDeSuscripcion(anualEnDolares, conSigno = true))
    }

    @Test
    fun `la nota dice cuanto de un cobro anual entra al total del mes`() {
        assertEquals(
            "Entra al total como $30.825 al mes",
            notaDeProrrateo(Recurrente.Suscripcion(hboMax, yaEsRegla = false), usdToCop = 0.0),
        )
        assertEquals(
            "Entra al total como $9.409 al mes",
            notaDeProrrateo(Recurrente.Suscripcion(nba, yaEsRegla = false), usdToCop = 0.0),
        )
    }

    @Test
    fun `no hay nota que poner en un cobro mensual`() {
        assertNull(notaDeProrrateo(Recurrente.Suscripcion(googleOne, yaEsRegla = false), usdToCop = 0.0))
    }

    /** Decir cuánto aporta al total algo que NO entra al total sería contradecirse al lado. */
    @Test
    fun `un cobro anual que ya es regla no dice cuanto aporta`() {
        val item = Recurrente.Suscripcion(hboMax, yaEsRegla = true)
        assertNull(notaDeProrrateo(item, usdToCop = 0.0))
        assertEquals(
            "Ya lo tienes como recurrente · no se suma dos veces",
            contextoDeSuscripcionActiva(item, accountNames = emptyMap()),
        )
    }

    /**
     * Y tampoco lo dice cuando la fila quedó fuera del total por no haberse podido convertir: el
     * card de arriba ya avisa «Este total no incluye 1 cobro en otra moneda», y prometer tres
     * líneas más abajo que sí entra sería la misma pantalla diciendo dos cosas opuestas.
     */
    @Test
    fun `un cobro anual en dolares sin tasa no promete que entra al total`() {
        val item = Recurrente.Suscripcion(
            sub("Dominio", 120L, PeriodicidadDeCobro.ANUAL, moneda = "USD"),
            yaEsRegla = false,
        )
        assertNull(notaDeProrrateo(item, usdToCop = 0.0), "sin tasa, esa fila no entró a ningún total")
        assertEquals("Entra al total como US$10 al mes", notaDeProrrateo(item, usdToCop = 4_000.0))
    }
}
