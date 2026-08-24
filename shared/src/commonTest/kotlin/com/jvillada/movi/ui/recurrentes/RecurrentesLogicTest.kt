package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurrentesLogicTest {

    private fun regla(
        nombre: String,
        monto: Long,
        dia: Int = 5,
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = RecurringRule(id = "r_$nombre", name = nombre, category = "Otros", amount = monto, dayOfMonth = dia, type = tipo)

    private fun sub(
        nombre: String,
        monto: Long,
        moneda: String = "COP",
        dia: Int = 5,
        estado: SubStatus = SubStatus.CONFIRMED,
        clave: String = nombre.lowercase(),
    ) = Subscription(
        id = "s_$nombre", merchantKey = clave, displayName = nombre, amount = monto, currency = moneda,
        dayOfMonth = dia, status = estado, confidence = SubConfidence.HIGH,
        firstSeen = 0, lastSeen = 0, occurrences = 3,
    )

    // ── El defecto que motivó todo esto ───────────────────────────────────────

    @Test
    fun `una suscripcion que ya existe como regla no vuelve a sumar al total`() {
        val reglas = listOf(regla("Netflix", 44_900))
        val subs = SubscriptionsResult(listOf(sub("Netflix", 44_900)), monthlyTotalCop = 44_900)

        val r = resumenRecurrentes(reglas, subs)

        // El cobro existe una sola vez en la vida real: una sola vez en el total.
        assertEquals(44_900, r.gastos)
        assertEquals(-44_900, r.flujoLibre)
        assertEquals(1, r.duplicadas)
        // Pero la fila SIGUE en la lista — se muestra marcada, no se esconde.
        assertEquals(2, r.items.size)
        val fila = r.items.filterIsInstance<Recurrente.Suscripcion>().single()
        assertTrue(fila.yaEsRegla)
    }

    @Test
    fun `sin solapamiento suma las dos cosas`() {
        val reglas = listOf(regla("Arriendo", 2_000_000))
        val subs = SubscriptionsResult(listOf(sub("Netflix", 44_900)), monthlyTotalCop = 44_900)

        val r = resumenRecurrentes(reglas, subs)

        assertEquals(2_044_900, r.gastos)
        assertEquals(0, r.duplicadas)
    }

    @Test
    fun `el solapamiento no depende de mayusculas, acentos ni puntuacion`() {
        val reglas = listOf(regla("Gimnasio Móvil", 80_000))
        val subs = SubscriptionsResult(listOf(sub("GIMNASIO  MOVIL.", 80_000)), monthlyTotalCop = 80_000)

        assertEquals(1, resumenRecurrentes(reglas, subs).duplicadas)
        assertEquals(80_000, resumenRecurrentes(reglas, subs).gastos)
    }

    @Test
    fun `nombres distintos no se confunden`() {
        assertFalse(claveDeNombre("Netflix") == claveDeNombre("Netflix Premium"))
        val reglas = listOf(regla("Netflix", 44_900))
        val subs = SubscriptionsResult(listOf(sub("Netflix Premium", 60_000)), monthlyTotalCop = 60_000)
        assertEquals(0, resumenRecurrentes(reglas, subs).duplicadas)
    }

    // ── Monedas ───────────────────────────────────────────────────────────────

    @Test
    fun `una suscripcion en dolares se convierte con la tasa que mando el server`() {
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Claude", 20, moneda = "USD")),
            monthlyTotalCop = 80_000,
            usdToCop = 4_000.0,
        )
        val r = resumenRecurrentes(emptyList(), subs)
        // Mismo número que el server calculó para el conjunto entero.
        assertEquals(80_000, r.gastos)
        assertTrue(r.hayMonedaExtranjera)
    }

    @Test
    fun `restar una fila en dolares del total da el resto exacto`() {
        val reglas = listOf(regla("Claude", 80_000))
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Claude", 20, moneda = "USD"), sub("Spotify", 16_900)),
            monthlyTotalCop = 96_900, // 20×4000 + 16900
            usdToCop = 4_000.0,
        )
        val r = resumenRecurrentes(reglas, subs)
        // Claude entra por la regla (80.000) y la suscripción en dólares queda excluida;
        // Spotify sí suma. Nada de doble conteo ni de restar de más.
        assertEquals(80_000 + 16_900, r.gastos)
        assertEquals(1, r.duplicadas)
    }

    @Test
    fun `sin tasa una fila en dolares no inventa pesos`() {
        val subs = SubscriptionsResult(listOf(sub("Claude", 20, moneda = "USD")), monthlyTotalCop = 0)
        assertEquals(0, resumenRecurrentes(emptyList(), subs).gastos)
    }

    // ── Lo que entra y lo que no ──────────────────────────────────────────────

    @Test
    fun `solo las activas cuentan — candidatas y descartadas quedan fuera`() {
        val subs = SubscriptionsResult(
            subscriptions = listOf(
                sub("Activa", 10_000, estado = SubStatus.CONFIRMED),
                sub("Auto", 20_000, estado = SubStatus.AUTO),
                sub("Propuesta", 30_000, estado = SubStatus.CANDIDATE),
                sub("Descartada", 40_000, estado = SubStatus.DISMISSED),
            ),
            monthlyTotalCop = 30_000,
        )
        val r = resumenRecurrentes(emptyList(), subs)
        assertEquals(30_000, r.gastos)
        assertEquals(2, r.items.size)
    }

    @Test
    fun `los ingresos no se mezclan con los gastos y la lista va por dia`() {
        val reglas = listOf(
            regla("Sueldo", 5_000_000, dia = 30, tipo = TransactionType.INCOME),
            regla("Arriendo", 2_000_000, dia = 1),
        )
        val subs = SubscriptionsResult(listOf(sub("Netflix", 44_900, dia = 15)), monthlyTotalCop = 44_900)
        val r = resumenRecurrentes(reglas, subs)

        assertEquals(5_000_000, r.ingresos)
        assertEquals(2_044_900, r.gastos)
        assertEquals(2_955_100, r.flujoLibre)
        assertEquals(listOf(1, 15, 30), r.items.map { it.dayOfMonth })
    }

    @Test
    fun `la marca de origen distingue lo que encontro Movi de lo que escribio el dueno`() {
        val detectada = Recurrente.Suscripcion(sub("Netflix", 1, clave = "netflix"), yaEsRegla = false)
        val manual = Recurrente.Suscripcion(sub("Gym", 1, clave = "manual_gym"), yaEsRegla = false)
        assertTrue(detectada.laEncontroMovi)
        assertFalse(manual.laEncontroMovi)
    }
}
