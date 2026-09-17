package com.jvillada.movi.server.routes

import com.jvillada.movi.server.fx.TasaUsdCop
import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Los cobros en dólares no se convierten con un número inventado.**
 *
 * `FxRateService.usdToCop()` nunca falla: con la fuente oficial caída, sin caché del día y sin
 * `USD_COP_RATE`, devuelve su constante de $4.000 sin decirlo. El total de suscripciones sumaba
 * con eso, así que los cuatro cobros en dólares del dueño entraban a «Gastos recurrentes» —y se
 * restaban de su «Flujo libre»— con una cifra que nadie eligió para hoy.
 *
 * `totalDeSuscripciones` es la parte con la decisión adentro, separada justamente para poder
 * probarla sin red ni base — el mismo recurso que `FxRateService.resolverTasa`.
 */
class TotalDeSuscripcionesTest {

    private fun sub(
        nombre: String,
        monto: Long,
        moneda: String,
        periodicidad: PeriodicidadDeCobro = PeriodicidadDeCobro.MENSUAL,
        estado: SubStatus = SubStatus.CONFIRMED,
    ) = Subscription(
        id = "sub-$nombre", merchantKey = nombre.lowercase(), displayName = nombre,
        amount = monto, currency = moneda, dayOfMonth = 7, status = estado,
        confidence = SubConfidence.HIGH, firstSeen = 0L, lastSeen = 0L, occurrences = 3,
        periodicidad = periodicidad,
    )

    private fun totalDe(activas: List<Subscription>, tasa: TasaUsdCop?) =
        totalDeSuscripciones(todas = activas, activas = activas, tasa = tasa)

    @Test
    fun `con una tasa de verdad los dolares entran al total`() {
        val r = totalDe(
            listOf(sub("Google One", 79_000, "COP"), sub("Claude", 20, "USD")),
            TasaUsdCop(4_150.0, esRespaldo = false),
        )
        assertEquals(79_000L + 83_000L, r.monthlyTotalCop)
        assertEquals(4_150.0, r.usdToCop)
        assertEquals(0, r.cobrosSinConvertir)
    }

    @Test
    fun `con la tasa de respaldo los dolares quedan afuera y se cuentan`() {
        val r = totalDe(
            listOf(sub("Google One", 79_000, "COP"), sub("Claude", 20, "USD"), sub("GitHub", 4, "USD")),
            TasaUsdCop(4_000.0, esRespaldo = true),
        )
        // Solo lo que está en pesos. Antes daba 79.000 + 80.000 + 16.000 = $175.000, de los cuales
        // $96.000 salían de una constante del código.
        assertEquals(79_000L, r.monthlyTotalCop)
        // Y la tasa viaja en 0.0, que es lo que el cliente ya lee como «no puedo convertir esto».
        assertEquals(0.0, r.usdToCop)
        assertEquals(2, r.cobrosSinConvertir, "los dos cobros que quedaron afuera se dicen")
    }

    @Test
    fun `sin nada en dolares no se pide tasa y no falta nada`() {
        val r = totalDe(listOf(sub("Google One", 79_000, "COP")), tasa = null)
        assertEquals(79_000L, r.monthlyTotalCop)
        assertEquals(0.0, r.usdToCop)
        assertEquals(0, r.cobrosSinConvertir)
    }

    @Test
    fun `un cobro anual en dolares se prorratea primero y se convierte despues`() {
        val r = totalDe(
            listOf(sub("Prime Video", 120, "USD", PeriodicidadDeCobro.ANUAL)),
            TasaUsdCop(4_000.0, esRespaldo = false),
        )
        // 120 / 12 = 10 dólares al mes → $40.000. Al revés (convertir y después dividir) el
        // redondeo del medio daría otra cosa, y el cliente hace este mismo orden.
        assertEquals(40_000L, r.monthlyTotalCop)
    }

    @Test
    fun `la lista completa viaja aunque el total solo mire las activas`() {
        val activa = sub("Google One", 79_000, "COP")
        val candidata = sub("Canva", 50_000, "COP", estado = SubStatus.CANDIDATE)
        val r = totalDeSuscripciones(
            todas = listOf(activa, candidata),
            activas = listOf(activa),
            tasa = null,
        )
        assertEquals(2, r.subscriptions.size)
        assertEquals(79_000L, r.monthlyTotalCop)
    }
}
