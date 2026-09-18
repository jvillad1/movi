package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **La clave que le deja al server distinguir «no edité nada» de «no sé qué es esto»**, esta vez
 * para las cuentas.
 *
 * Misma trampa que ya documenta [EdicionEnElWireTest] para los movimientos, y acá cuesta el nombre
 * de un crédito: `POST /api/accounts` decide si el reenvío del teléfono pisa lo guardado mirando si
 * el cuerpo trae `lastEditedAt` (`pisaElReenvio`, en `EventRoutes.kt` — la misma función para los
 * dos).
 *
 * - **Clave ausente** = APK viejo, que no conoce el campo → se atiende como antes y pisa. Es lo que
 *   evita perderle datos al teléfono que el dueño ya tiene instalado (el 1.31).
 * - **Clave presente en `null`** = cliente nuevo diciendo «esta copia no la editó nadie» → pierde
 *   contra el renombre que el dueño acaba de hacer en la web.
 *
 * Los dos significan cosas opuestas, y kotlinx por defecto los vuelve el mismo cuerpo:
 * `encodeDefaults` es `false`, así que un `null` no se serializaría. Sin
 * [kotlinx.serialization.EncodeDefault] el arreglo entero no haría nada.
 *
 * **Por eso esta prueba mira el JSON y no el objeto.** Una que escriba el cuerpo a mano
 * (`"lastEditedAt":null`) manda algo que ningún cliente produce y pasa igual de rota.
 */
class CuentaEditadaEnElWireTest {

    /** La misma configuración que arman los tres `Platform`. */
    private val json = Json { ignoreUnknownKeys = true }

    private val libranza = Account(
        id = "acc-libranza",
        name = "Libranza 4818",
        type = AccountType.LOAN,
        balance = 200_000_000L,
    )

    private fun crudo(cuenta: Account): JsonObject =
        json.parseToJsonElement(json.encodeToString(Account.serializer(), cuenta)) as JsonObject

    @Test
    fun `una cuenta sin editar viaja con la clave presente en null, no sin la clave`() {
        val cuerpo = crudo(libranza)

        assertTrue(
            "lastEditedAt" in cuerpo,
            "sin la clave el server lee «APK viejo» y el reenvío pisa el renombre de la web",
        )
        assertEquals(JsonNull, cuerpo["lastEditedAt"])
    }

    @Test
    fun `y un renombre hecho sin senal viaja con su instante`() {
        val cuerpo = crudo(libranza.copy(lastEditedAt = 1_757_600_000_000L))

        assertEquals("1757600000000", cuerpo["lastEditedAt"].toString())
    }

    /**
     * Y la otra dirección: un cuerpo de un cliente viejo —sin la clave— se sigue leyendo, con
     * `lastEditedAt` en `null`. Es lo que hace que el APK instalado pueda seguir subiendo cuentas.
     */
    @Test
    fun `un cuerpo sin la clave se sigue leyendo`() {
        val viejo = """{"id":"acc-2","name":"Nequi","type":"SAVINGS","balance":0}"""

        assertEquals(null, json.decodeFromString(Account.serializer(), viejo).lastEditedAt)
    }
}
