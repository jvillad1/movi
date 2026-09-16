package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **La clave que le deja al server distinguir «no edité nada» de «no sé qué es esto».**
 *
 * Tercer caso de la misma trampa que ya documentan [MinimoEnElWireTest] y `SuscripcionEnElWireTest`,
 * y acá cuesta plata de una forma nueva: `POST /api/events` decide si un reenvío del teléfono pisa
 * lo guardado mirando si el cuerpo trae `lastEditedAt` (`pisaElReenvio`, en `EventRoutes.kt`).
 *
 * - **Clave ausente** = APK viejo, que no conoce el campo → se atiende como antes de esta ola y
 *   pisa. Es la decisión que evita perderle datos al teléfono que el dueño ya tiene instalado.
 * - **Clave presente en `null`** = cliente nuevo diciendo «esta copia no la editó nadie» → pierde
 *   contra la corrección que el dueño acaba de hacer en la web.
 *
 * Los dos significan cosas opuestas, y kotlinx por defecto los vuelve el mismo cuerpo:
 * `encodeDefaults` es `false`, así que un `null` no se serializaría. Sin [kotlinx.serialization.EncodeDefault],
 * el cliente nuevo mandaría un cuerpo indistinguible del viejo y el arreglo entero no haría nada.
 *
 * **Por eso esta prueba mira el JSON y no el objeto.** Una que escriba el cuerpo a mano
 * (`"lastEditedAt":null`) manda algo que ningún cliente produce y pasa igual de rota.
 */
class EdicionEnElWireTest {

    /** La misma configuración que arman los tres `Platform`. */
    private val json = Json { ignoreUnknownKeys = true }

    private val gasto = FinancialEvent(
        id = "ev-1",
        accountId = "acc-ahorros",
        type = TransactionType.EXPENSE,
        amount = 165_289L,
        category = "Mercado",
        description = "Éxito",
        timestamp = 1_757_000_000_000L,
    )

    private fun crudo(evento: FinancialEvent): JsonObject =
        json.parseToJsonElement(json.encodeToString(FinancialEvent.serializer(), evento)) as JsonObject

    @Test
    fun `un movimiento sin editar viaja con la clave presente en null, no sin la clave`() {
        val cuerpo = crudo(gasto)

        assertTrue(
            "lastEditedAt" in cuerpo,
            "sin la clave el server lee «APK viejo» y el reenvío pisa la corrección de la web",
        )
        assertEquals(JsonNull, cuerpo["lastEditedAt"])
    }

    @Test
    fun `y una correccion hecha sin senal viaja con su instante`() {
        val cuerpo = crudo(gasto.copy(lastEditedAt = 1_757_600_000_000L))

        assertEquals("1757600000000", cuerpo["lastEditedAt"].toString())
    }

    /**
     * Y la otra dirección: un cuerpo de un cliente viejo —sin la clave— se sigue leyendo, con
     * `lastEditedAt` en `null`. Es lo que hace que el APK 1.25 pueda seguir subiendo movimientos.
     */
    @Test
    fun `un cuerpo sin la clave se sigue leyendo`() {
        val viejo = """{"id":"ev-2","accountId":"acc-ahorros","type":"EXPENSE","amount":9000,
            "category":"Mercado","description":"Éxito","timestamp":1757000000000}"""

        assertEquals(null, json.decodeFromString(FinancialEvent.serializer(), viejo).lastEditedAt)
    }
}
