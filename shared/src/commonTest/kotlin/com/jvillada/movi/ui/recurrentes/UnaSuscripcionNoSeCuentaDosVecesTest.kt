package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
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
 * **El mismo cobro, contado una sola vez** — las tres formas en que Movi lo contaba dos.
 *
 * 1. Una suscripción anotada a mano y la misma auto-descubierta convivían (claves `manual_netflix`
 *    y `netflix`), las dos activas y las dos sumando.
 * 2. La identidad de un cargo vive en `merchant` («NETFLIX.COM») y el cliente solo miraba
 *    `description` («COMPRA NETFLIX.COM BOGOTA»), así que no reconocía la suscripción «Netflix» y
 *    dejaba crearle encima una regla con el mismo nombre.
 * 3. Los cobros en dólares se convertían con la tasa de respaldo —una constante del código— sin
 *    decirlo.
 */
class UnaSuscripcionNoSeCuentaDosVecesTest {

    // El cargo tal cual llega de un extracto o de un SMS del banco: el texto crudo en la
    // descripción y el comercio aparte. Es la forma normal de un movimiento que NO escribió el
    // dueño a mano, o sea la de casi todos los cobros de una suscripción.
    private fun cargoDelBanco(
        description: String = "COMPRA NETFLIX.COM BOGOTA",
        merchant: String? = "NETFLIX.COM",
        category: String = "Entretenimiento",
        amount: Long = 44_900L,
    ) = FinancialEvent(
        id = "ev_netflix",
        accountId = "acc_tc",
        type = TransactionType.EXPENSE,
        amount = amount,
        category = category,
        description = description,
        merchant = merchant,
        timestamp = 1_754_406_000_000L,
    )

    private fun cobro(
        nombre: String,
        status: SubStatus = SubStatus.CONFIRMED,
        clave: String = nombre.lowercase(),
        moneda: String = "COP",
        monto: Long = 44_900L,
        id: String = "sub_$clave",
    ) = Subscription(
        id = id,
        merchantKey = clave,
        displayName = nombre,
        amount = monto,
        currency = moneda,
        dayOfMonth = 14,
        status = status,
        confidence = SubConfidence.HIGH,
        firstSeen = 0L,
        lastSeen = 0L,
        occurrences = 3,
    )

    private fun regla(nombre: String) = RecurringRule(
        id = "rr_$nombre",
        name = nombre,
        category = "Entretenimiento",
        amount = 44_900L,
        dayOfMonth = 14,
        type = TransactionType.EXPENSE,
    )

    // ── 2 · la identidad del cargo vive en `merchant` ────────────────────────

    @Test
    fun `el cargo crudo del banco se reconoce como la suscripcion canonica`() {
        val activas = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Netflix")))
        assertEquals(
            "Netflix",
            nombreRecurrenteDe(cargoDelBanco(), emptyList(), activas),
            "el chip «Recurrentes» tiene que marcar el cargo que paga la suscripción",
        )
    }

    @Test
    fun `no se ofrece crear una regla para un cobro que ya es suscripcion`() {
        val activas = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Netflix")))
        assertFalse(
            shouldOfferRecurring(cargoDelBanco(), emptyList(), emptySet(), activas),
            "crear la regla «Netflix» encima contaría el mismo \$44.900 dos veces",
        )
        // Y lo mismo desde el detalle del movimiento, que es la otra puerta.
        assertEquals(
            "Netflix",
            equivalenteYaAnotadoDe(cargoDelBanco(), null, emptyList(), activas),
        )
    }

    @Test
    fun `tambien se reconoce contra una regla que el dueno escribio a mano`() {
        assertEquals(
            "Netflix",
            nombreRecurrenteDe(cargoDelBanco(), listOf(regla("Netflix")), emptyList()),
        )
    }

    @Test
    fun `el comercio crudo alcanza cuando no hay canonico que valga`() {
        // Un comercio que no está en la tabla de servicios conocidos: la clave sale del texto del
        // comercio, no de la descripción larga del banco.
        val gimnasio = cargoDelBanco(
            description = "COMPRA BODYTECH CALLE 100 4471",
            merchant = "Bodytech",
            category = "Salud",
        )
        assertEquals(
            "Bodytech",
            nombreRecurrenteDe(gimnasio, listOf(regla("Bodytech")), emptyList()),
        )
    }

    @Test
    fun `un cargo de otro comercio sigue siendo nuevo`() {
        val activas = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Netflix")))
        val mercado = cargoDelBanco(
            description = "COMPRA EXITO COUNTRY BOGOTA",
            merchant = "EXITO COUNTRY",
            category = "Mercado",
        )
        assertNull(nombreRecurrenteDe(mercado, emptyList(), activas))
        assertTrue(shouldOfferRecurring(mercado, emptyList(), emptySet(), activas))
    }

    @Test
    fun `las claves de un cargo incluyen la nota, el comercio y el canonico`() {
        val claves = clavesDeCobroDe(cargoDelBanco())
        assertTrue("netflix" in claves, "el canónico, que es como se llama la suscripción")
        assertTrue("netflixcom" in claves, "el comercio crudo, como lo mira el server")
        assertTrue("compranetflixcombogota" in claves, "la nota, que es con lo que nacería la regla")
    }

    @Test
    fun `un movimiento escrito a mano se sigue comparando por su nota`() {
        // Sin `merchant`: es lo que pasa con todo lo que el dueño anota él mismo, y tiene que
        // seguir funcionando exactamente igual que antes.
        val aMano = cargoDelBanco(description = "Arriendo", merchant = null, category = "Vivienda")
        assertEquals("Arriendo", nombreRecurrenteDe(aMano, listOf(regla("Arriendo")), emptyList()))
    }

    // ── 1 · una candidata que ya está anotada se avisa antes de confirmarla ──

    @Test
    fun `una candidata que ya es suscripcion activa se avisa`() {
        val candidata = cobro("Netflix", SubStatus.CANDIDATE, clave = "netflix", id = "sub_detectada")
        val aMano = cobro("Netflix", SubStatus.CONFIRMED, clave = "manual_netflix", id = "sub_manual")
        assertEquals(
            "Ya lo tienes como suscripción activa",
            avisoDeCandidataDuplicada(candidata, emptySet(), listOf(candidata, aMano)),
        )
    }

    @Test
    fun `una candidata que ya es regla sigue avisando lo suyo`() {
        val candidata = cobro("Netflix", SubStatus.CANDIDATE)
        assertEquals(
            "Ya lo tienes como recurrente",
            avisoDeCandidataDuplicada(candidata, setOf("netflix"), listOf(candidata)),
        )
    }

    @Test
    fun `una candidata nueva no avisa nada`() {
        val candidata = cobro("Spotify", SubStatus.CANDIDATE)
        val otra = cobro("Netflix")
        assertNull(avisoDeCandidataDuplicada(candidata, setOf("arriendo"), listOf(candidata, otra)))
    }

    @Test
    fun `una descartada con el mismo nombre no frena a la candidata`() {
        val candidata = cobro("Netflix", SubStatus.CANDIDATE, clave = "netflix", id = "sub_detectada")
        val muerta = cobro("Netflix", SubStatus.DISMISSED, clave = "manual_netflix", id = "sub_manual")
        assertNull(avisoDeCandidataDuplicada(candidata, emptySet(), listOf(candidata, muerta)))
    }

    // ── 3 · el total dice cuando le faltan los dólares ───────────────────────

    private fun resultado(
        subs: List<Subscription>,
        total: Long,
        usdToCop: Double = 0.0,
        sinConvertir: Int = 0,
    ) = SubscriptionsResult(
        subscriptions = subs,
        monthlyTotalCop = total,
        usdToCop = usdToCop,
        cobrosSinConvertir = sinConvertir,
    )

    @Test
    fun `los cobros que el server no pudo convertir se cuentan y se avisan`() {
        val subs = listOf(
            cobro("Google One", monto = 79_000L),
            cobro("Claude", moneda = "USD", monto = 20L),
            cobro("GitHub", moneda = "USD", monto = 4L),
        )
        val r = resumenRecurrentes(emptyList(), resultado(subs, total = 79_000L, sinConvertir = 2))
        assertEquals(79_000L, r.gastosDeSuscripciones)
        assertEquals(2, r.sinConvertir, "la pantalla tiene que poder decir que faltan dos")
        assertFalse(
            r.hayMonedaExtranjera,
            "no se puede prometer que los dólares entran convertidos cuando quedaron afuera",
        )
    }

    @Test
    fun `sin nada que avisar el total del server se usa tal cual`() {
        val subs = listOf(cobro("Google One", monto = 79_000L), cobro("Claude", moneda = "USD", monto = 20L))
        val r = resumenRecurrentes(emptyList(), resultado(subs, total = 162_000L, usdToCop = 4_150.0))
        assertEquals(162_000L, r.gastosDeSuscripciones)
        assertEquals(0, r.sinConvertir)
        assertTrue(r.hayMonedaExtranjera)
    }

    @Test
    fun `un cobro anual en dolares que quedo afuera no dispara la nota del prorrateo`() {
        val anual = cobro("Prime Video", moneda = "USD", monto = 120L)
            .copy(periodicidad = PeriodicidadDeCobro.ANUAL)
        val r = resumenRecurrentes(
            emptyList(),
            resultado(listOf(cobro("Google One", monto = 79_000L), anual), total = 79_000L, sinConvertir = 1),
        )
        assertFalse(r.hayCobrosAnuales, "no se explica un prorrateo que este total no hizo")
        assertEquals(1, r.sinConvertir)
    }
}
