package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Las claves de [Subscription] que el `PUT` necesita ver para saber qué se le está pidiendo.**
 *
 * `PUT /api/subscriptions/{id}` distingue «cambiá esto» de «no lo toques» mirando si la clave
 * viene en el JSON crudo (`mandoLaCuenta` y `mandoLaPeriodicidad` en `SubscriptionRoutes`). Es
 * la defensa contra un APK viejo: un cliente que no conoce un campo no puede borrarlo.
 *
 * El costo de esa defensa es que un cliente NUEVO tiene que mandar la clave siempre, incluso
 * cuando vale el default — y kotlinx, por defecto, hace exactamente lo contrario (`encodeDefaults`
 * es `false`, y los tres `Platform` usan `Json { ignoreUnknownKeys = true }`, que no lo cambia).
 * Justo los dos valores por default son los que significan algo: `periodicidad = MENSUAL` y
 * `accountId = null`, que es «sin cuenta».
 *
 * Pasó de verdad con `accountId` (Ola 18): la hoja de edición ofrecía «Sin cuenta», el comentario
 * al lado afirmaba que la clave viajaba igual, y no viajaba. El PUT lo leía como «no la toques»,
 * la hoja cerraba, la lista recargaba y la cuenta seguía puesta — sin error, sin aviso, y sin
 * ninguna prueba que lo notara: en una prueba de UI el objeto se pasa entero y la serialización
 * nunca entra en juego. Por eso esta prueba mira el JSON, no el objeto.
 */
class SuscripcionEnElWireTest {

    /** La misma configuración que arman los tres `Platform`. */
    private val json = Json { ignoreUnknownKeys = true }

    private val netflix = Subscription(
        id = "sub_1", merchantKey = "manual_netflix", displayName = "Netflix",
        amount = 44_900L, currency = "COP", dayOfMonth = 19, status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 3,
        accountId = "acc_master_black",
    )

    private fun claves(sub: Subscription): Set<String> =
        json.parseToJsonElement(json.encodeToString(Subscription.serializer(), sub))
            .let { (it as kotlinx.serialization.json.JsonObject).keys }

    @Test
    fun `sin cuenta viaja como una clave presente en null, no como una clave ausente`() {
        val sinCuenta = netflix.copy(accountId = null)

        assertTrue(
            "accountId" in claves(sinCuenta),
            "sin la clave, el PUT lee «no la toques» y «Sin cuenta» se vuelve imposible de pedir",
        )
        assertTrue(
            "\"accountId\":null" in json.encodeToString(Subscription.serializer(), sinCuenta)
                .replace(" ", ""),
            "y tiene que valer null: cualquier otra cosa sería elegirle una cuenta al dueño",
        )
    }

    /** La misma trampa, en el campo donde ya se había resuelto una ola antes. */
    @Test
    fun `una mensual manda igual su periodicidad`() {
        assertTrue("periodicidad" in claves(netflix.copy(periodicidad = PeriodicidadDeCobro.MENSUAL)))
    }

    /** Y con cuenta puesta viaja lo que el dueño eligió, sin sorpresas. */
    @Test
    fun `con cuenta elegida viaja esa cuenta`() {
        val decodificada = json.decodeFromString(
            Subscription.serializer(),
            json.encodeToString(Subscription.serializer(), netflix),
        )
        assertEquals("acc_master_black", decodificada.accountId)
    }

    /**
     * **La otra mitad, que esta anotación NO cambia**: un cliente viejo que no manda la clave
     * sigue queriendo decir «no la toques», y por eso el server no puede confiar solo en el
     * modelo. Al decodificar un cuerpo sin `accountId` el campo cae en su default, que es
     * indistinguible de un «sin cuenta» — mirar el JSON crudo es lo único que los separa.
     */
    @Test
    fun `un cuerpo sin la clave decodifica en null, que es por que el server mira el crudo`() {
        val crudo = """{"id":"sub_1","merchantKey":"manual_netflix","displayName":"Netflix",
            "amount":44900,"currency":"COP","dayOfMonth":19,"status":"CONFIRMED",
            "confidence":"HIGH","firstSeen":0,"lastSeen":0,"occurrences":3}"""
        assertEquals(null, json.decodeFromString(Subscription.serializer(), crudo).accountId)
    }
}
