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

    /** Cuántas filas quedaron marcadas como «ya la tienes» — se cuenta desde la lista, que es lo
     *  que la UI de verdad pinta, en vez de desde un campo del resumen que nadie más usaría. */
    private fun ResumenRecurrentes.duplicadas() =
        items.filterIsInstance<Recurrente.Suscripcion>().count { it.yaEsRegla }

    // ── El defecto que motivó todo esto ───────────────────────────────────────

    @Test
    fun `una suscripcion que ya existe como regla no vuelve a sumar al total`() {
        val reglas = listOf(regla("Netflix", 44_900))
        val subs = SubscriptionsResult(listOf(sub("Netflix", 44_900)), monthlyTotalCop = 44_900)

        val r = resumenRecurrentes(reglas, subs)

        // El cobro existe una sola vez en la vida real: una sola vez en el total.
        assertEquals(44_900, r.gastos)
        assertEquals(-44_900, r.flujoLibre)
        assertEquals(1, r.duplicadas())
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
        assertEquals(0, r.duplicadas())
    }

    @Test
    fun `el solapamiento no depende de mayusculas, acentos ni puntuacion`() {
        val reglas = listOf(regla("Gimnasio Móvil", 80_000))
        val subs = SubscriptionsResult(listOf(sub("GIMNASIO  MOVIL.", 80_000)), monthlyTotalCop = 80_000)

        assertEquals(1, resumenRecurrentes(reglas, subs).duplicadas())
        assertEquals(80_000, resumenRecurrentes(reglas, subs).gastos)
    }

    @Test
    fun `nombres distintos no se confunden`() {
        assertFalse(claveDeNombre("Netflix") == claveDeNombre("Netflix Premium"))
        val reglas = listOf(regla("Netflix", 44_900))
        val subs = SubscriptionsResult(listOf(sub("Netflix Premium", 60_000)), monthlyTotalCop = 60_000)
        assertEquals(0, resumenRecurrentes(reglas, subs).duplicadas())
    }

    @Test
    fun `dos solapamientos distintos se excluyen los dos`() {
        val reglas = listOf(regla("Netflix", 44_900, dia = 5), regla("Spotify", 16_900, dia = 9))
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Netflix", 44_900, dia = 5), sub("Spotify", 16_900, dia = 9)),
            monthlyTotalCop = 61_800,
        )
        val r = resumenRecurrentes(reglas, subs)
        assertEquals(61_800, r.gastos, "cada cobro una sola vez")
        assertEquals(2, r.duplicadas())
        assertEquals(4, r.items.size)
    }

    /**
     * B1: una regla tapa UNA suscripción, no todas las que se llamen igual. Con dos cobros
     * «Seguro» distintos y una sola regla «Seguro», excluir los dos borraría un gasto real.
     */
    @Test
    fun `una sola regla no puede tapar dos suscripciones del mismo nombre`() {
        val reglas = listOf(regla("Seguro", 80_000))
        val subs = SubscriptionsResult(
            subscriptions = listOf(
                sub("Seguro", 80_000, clave = "seguro_a"),
                sub("Seguro", 30_000, clave = "seguro_b"),
            ),
            monthlyTotalCop = 110_000,
        )
        val r = resumenRecurrentes(reglas, subs)

        assertEquals(1, r.duplicadas(), "solo una queda tapada por la regla")
        assertEquals(80_000 + 30_000, r.gastos, "el segundo cobro sigue contando")
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
        assertEquals(1, r.duplicadas())
    }

    /**
     * Cliente nuevo contra server viejo (pasa de verdad: el APK se instala a mano y el server se
     * despliega aparte). El server viejo NO manda la tasa, pero sí convirtió los dólares dentro
     * de `monthlyTotalCop`. Sin nada que excluir, ese total se usa tal cual: los dólares entran.
     */
    @Test
    fun `sin tasa pero sin exclusiones el total del server se respeta, dolares incluidos`() {
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Claude", 20, moneda = "USD")),
            monthlyTotalCop = 80_000, // el server viejo ya lo convirtió
            usdToCop = 0.0,           // pero no expone la tasa
        )
        val r = resumenRecurrentes(emptyList(), subs)
        assertEquals(80_000, r.gastos, "el dólar no puede desaparecer del total")
        assertEquals(0, r.sinConvertir)
        assertTrue(r.hayMonedaExtranjera)
    }

    /**
     * El caso feo: hay que excluir una fila (así que hay que sumar una por una) Y falta la tasa.
     * Lo que no se puede convertir queda afuera, pero CONTADO, para que la pantalla lo diga.
     */
    @Test
    fun `sin tasa y con exclusiones lo no convertible se cuenta en vez de desaparecer`() {
        val reglas = listOf(regla("Netflix", 44_900))
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Netflix", 44_900), sub("Claude", 20, moneda = "USD")),
            monthlyTotalCop = 124_900,
            usdToCop = 0.0,
        )
        val r = resumenRecurrentes(reglas, subs)

        assertEquals(44_900, r.gastos, "solo la regla; el dólar no se pudo convertir")
        assertEquals(1, r.sinConvertir, "y eso se reporta, no se traga")
        assertFalse(r.hayMonedaExtranjera, "ningún dólar entró: no hay nada que explicar sobre la tasa")
    }

    /** B2: una fila en dólares excluida por duplicada no justifica avisar sobre la conversión. */
    @Test
    fun `un dolar excluido por duplicado no dispara la nota de la tasa`() {
        val reglas = listOf(regla("Claude", 80_000))
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Claude", 20, moneda = "USD")),
            monthlyTotalCop = 80_000,
            usdToCop = 4_000.0,
        )
        val r = resumenRecurrentes(reglas, subs)
        assertEquals(80_000, r.gastos)
        assertFalse(r.hayMonedaExtranjera, "el único dólar quedó fuera del total")
    }

    /** Una moneda que Movi no sabe convertir se reporta, no se cuenta como 0 en silencio. */
    @Test
    fun `una moneda desconocida se cuenta como no convertible`() {
        val reglas = listOf(regla("Netflix", 44_900))
        val subs = SubscriptionsResult(
            subscriptions = listOf(sub("Netflix", 44_900), sub("Spotify", 10, moneda = "EUR")),
            monthlyTotalCop = 44_900,
            usdToCop = 4_000.0,
        )
        val r = resumenRecurrentes(reglas, subs)
        assertEquals(44_900, r.gastos)
        assertEquals(1, r.sinConvertir)
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
