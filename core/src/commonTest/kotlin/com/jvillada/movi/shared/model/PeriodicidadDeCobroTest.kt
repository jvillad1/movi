package com.jvillada.movi.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **La división por doce, fijada con los cuatro cobros reales del dueño.**
 *
 * Los números de acá abajo no son de ejemplo: son las cuatro suscripciones de Google que él está
 * por cargar, y son la razón por la que existe [PeriodicidadDeCobro]. Sin periodicidad, las dos
 * anuales le habrían dicho que gasta $482.800 al mes en NBA + HBO Max cuando la plata real es
 * ~$40.200 — doce veces de más, sobre su propio dinero. Pinnedas acá, una regresión se lee de
 * una: el número que falla es un número que él puede reconocer.
 *
 * La otra mitad de lo que se blinda es el **acuerdo entre el server y el cliente**. Los dos
 * suman el mismo total por caminos distintos —`resultFor` (SubscriptionRoutes.kt) y
 * `resumenRecurrentes` (RecurrentesLogic.kt)— y este proyecto ya tuvo dos superficies que
 * calculaban «la misma» regla y se separaron. Acá se prueba la única pieza compartida
 * ([montoMensualEquivalente]) y, sobre ella, que el ida y vuelta por el wire no cambie nada.
 */
class PeriodicidadDeCobroTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Los cuatro cobros reales ──────────────────────────────────────────────

    @Test
    fun `un cobro mensual pasa sin que nadie lo toque`() {
        // Google One (AI Pro 5 TB) y YouTube Premium Family: lo que llega cada mes ES lo que
        // pesa cada mes, así que la función no puede introducir ni un peso de diferencia.
        assertEquals(
            79_000L,
            montoMensualEquivalente(79_000L, PeriodicidadDeCobro.MENSUAL),
            "Google One: el cobro mensual es su propio equivalente mensual",
        )
        assertEquals(
            47_900L,
            montoMensualEquivalente(47_900L, PeriodicidadDeCobro.MENSUAL),
            "YouTube Premium Family",
        )
    }

    @Test
    fun `un cobro anual se divide en doce, redondeando hacia arriba`() {
        // HBO Max Platinum: $369.900 ÷ 12 = $30.825 exacto, sin redondeo de por medio.
        assertEquals(
            30_825L,
            montoMensualEquivalente(369_900L, PeriodicidadDeCobro.ANUAL),
            "HBO Max: la división da exacta",
        )
        // NBA League Pass: $112.900 ÷ 12 = $9.408,33 — acá sí hay que elegir un lado.
        assertEquals(
            9_409L,
            montoMensualEquivalente(112_900L, PeriodicidadDeCobro.ANUAL),
            "NBA: se redondea hacia ARRIBA, no a 9.408",
        )
    }

    /**
     * La propiedad que justifica el redondeo hacia arriba, escrita como prueba: apartar el
     * equivalente mensual doce veces tiene que alcanzar para pagar el cobro anual. Redondeando
     * hacia abajo, al NBA le faltarían $4 al año.
     */
    @Test
    fun `doce veces el equivalente mensual siempre alcanza para el cobro anual`() {
        listOf(112_900L, 369_900L, 1L, 11L, 12L, 13L, 100_000L, 999_999L).forEach { anual ->
            val mensual = montoMensualEquivalente(anual, PeriodicidadDeCobro.ANUAL)
            assertTrue(
                mensual * 12 >= anual,
                "apartando $mensual doce veces no alcanza para pagar $anual",
            )
            // Y no se pasa de largo: nunca sobra un mes entero de más.
            assertTrue(
                (mensual - 1) * 12 < anual,
                "$mensual se pasa: $anual se cubría con menos",
            )
        }
    }

    @Test
    fun `el equivalente mensual de una anual es doce veces mas chico que su cobro`() {
        // El error de 12× que este cambio vino a matar, dicho al derecho: si esto se rompe, el
        // «Flujo libre» del dueño vuelve a descontarle $482.800 al mes por NBA + HBO Max.
        val nba = Subscription(
            id = "s_nba", merchantKey = "manual_nba", displayName = "NBA League Pass",
            amount = 112_900L, currency = "COP", dayOfMonth = 4,
            status = SubStatus.CONFIRMED, confidence = SubConfidence.HIGH,
            firstSeen = 0, lastSeen = 0, occurrences = 0,
            periodicidad = PeriodicidadDeCobro.ANUAL,
        )
        val hbo = nba.copy(
            id = "s_hbo", merchantKey = "manual_hbo_max", displayName = "HBO Max Platinum",
            amount = 369_900L,
        )
        assertEquals(9_409L + 30_825L, nba.montoMensualEquivalente() + hbo.montoMensualEquivalente())
        assertEquals(
            482_800L,
            nba.amount + hbo.amount,
            "el monto guardado sigue siendo el cobro real, el que el dueño puede ver en el extracto",
        )
    }

    // ── El campo ausente significa mensual, en las dos direcciones ────────────

    @Test
    fun `una suscripcion sin el campo se lee como mensual`() {
        // Exactamente lo que devuelve un server anterior a la Ola 16, y también lo que hay en la
        // base para toda fila que ya existía. El default no es una comodidad: es la migración.
        val deUnServerViejo = """
            {"id":"sub_1","merchantKey":"netflix","displayName":"Netflix","amount":44900,
             "currency":"COP","dayOfMonth":14,"status":"CONFIRMED","confidence":"HIGH",
             "firstSeen":0,"lastSeen":0,"occurrences":3}
        """.trimIndent()
        val sub = json.decodeFromString<Subscription>(deUnServerViejo)
        assertEquals(PeriodicidadDeCobro.MENSUAL, sub.periodicidad)
        assertEquals(44_900L, sub.montoMensualEquivalente(), "una fila vieja vale lo que siempre valió")
    }

    @Test
    fun `un alta sin el campo se lee como mensual`() {
        // El cuerpo que manda la hoja de un APK anterior a la Ola 16.
        val cuerpoViejo = """{"displayName":"Gimnasio","amount":90000,"currency":"COP","dayOfMonth":3}"""
        assertEquals(
            PeriodicidadDeCobro.MENSUAL,
            json.decodeFromString<CreateSubscriptionRequest>(cuerpoViejo).periodicidad,
        )
    }

    /**
     * `@EncodeDefault(ALWAYS)` en [Subscription.periodicidad]: la clave viaja aunque valga
     * MENSUAL. Sin eso, el `PUT /api/subscriptions/{id}` no podría distinguir «este cliente no
     * conoce la periodicidad» de «esta suscripción es mensual», que es la misma trampa que en la
     * Ola 15 le borraba el seguro a un crédito.
     */
    @Test
    fun `la periodicidad viaja siempre, aunque valga su default`() {
        val mensual = Subscription(
            id = "s1", merchantKey = "netflix", displayName = "Netflix", amount = 44_900L,
            currency = "COP", dayOfMonth = 14, status = SubStatus.CONFIRMED,
            confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 3,
        )
        assertTrue(
            "\"periodicidad\":\"MENSUAL\"" in Json.encodeToString(mensual),
            "sin la clave, el PUT no puede saber si el cliente la conoce",
        )
    }

    // ── El ida y vuelta por el wire no mueve ni un peso ───────────────────────

    @Test
    fun `el equivalente mensual sobrevive el viaje server-cliente`() {
        // El escenario real del desacuerdo: el server calcula el total, el cliente lo recalcula
        // fila por fila (lo hace cuando tiene que excluir alguna que ya es regla). Las dos
        // cuentas parten del MISMO objeto serializado, así que si el wire perdiera o redondeara
        // algo por el camino, los dos totales se separarían.
        val hbo = Subscription(
            id = "s_hbo", merchantKey = "manual_hbo_max", displayName = "HBO Max Platinum",
            amount = 369_900L, currency = "COP", dayOfMonth = 28,
            status = SubStatus.CONFIRMED, confidence = SubConfidence.HIGH,
            firstSeen = 0, lastSeen = 0, occurrences = 0,
            periodicidad = PeriodicidadDeCobro.ANUAL,
        )
        val ida = json.decodeFromString<Subscription>(Json.encodeToString(hbo))
        assertEquals(hbo, ida)
        assertEquals(hbo.montoMensualEquivalente(), ida.montoMensualEquivalente())
        assertEquals(30_825L, ida.montoMensualEquivalente())
    }

    @Test
    fun `un alta anual viaja con su cobro completo, no con el prorrateado`() {
        val pedido = CreateSubscriptionRequest(
            displayName = "HBO Max Platinum",
            amount = 369_900L,
            currency = "COP",
            dayOfMonth = 28,
            periodicidad = PeriodicidadDeCobro.ANUAL,
        )
        val encoded = Json.encodeToString(pedido)
        assertTrue("369900" in encoded, "se guarda el número que el dueño puede verificar")
        assertTrue("30825" !in encoded, "el prorrateado no existe en ninguna parte del mundo real")
        assertEquals(pedido, json.decodeFromString<CreateSubscriptionRequest>(encoded))
    }
}
