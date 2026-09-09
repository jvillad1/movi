package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Las claves de [CardTerms] que el `PUT` necesita ver para saber qué se le está pidiendo.**
 *
 * Gemelo de [SuscripcionEnElWireTest], y existe porque la trampa que esa prueba describe se acababa
 * de repetir. `PUT /api/cards/{id}` distingue «cambiá esto» de «no lo toques» mirando si la clave
 * viene en el JSON crudo (`"pagoMinimo" in crudo`, en `CardRoutes`). Es la defensa contra el APK
 * instalado: un cliente que no conoce un campo no puede borrarlo.
 *
 * El costo de esa defensa es que un cliente NUEVO tiene que mandar la clave **siempre**, incluso
 * cuando vale el default — y kotlinx, por defecto, hace exactamente lo contrario (`encodeDefaults`
 * es `false`, y los tres `Platform` usan `Json { ignoreUnknownKeys = true }`, que no lo cambia).
 * Justo el valor por default es el que significa algo: `pagoMinimo = null` es «bórralo».
 *
 * El escenario que esto fija, con las cifras reales: carga $1.843.014 en la Master Black, se
 * guarda; abre el lápiz, **borra el campo**, guarda. Sin la anotación el cuerpo sale sin la clave,
 * el server lo lee como «cliente viejo» y repone los $1.843.014 — sin error, sin aviso, y con el
 * «Flujo libre» restando una plata que él quiso quitar.
 *
 * **Por eso esta prueba mira el JSON y no el objeto.** Una prueba que escriba el cuerpo a mano
 * (`"pagoMinimo":null`) manda algo que ningún cliente produce y pasa igual de rota.
 */
class MinimoEnElWireTest {

    /** La misma configuración que arman los tres `Platform`. */
    private val json = Json { ignoreUnknownKeys = true }

    private val masterBlack = CardTerms(
        accountId = "acc_master_black",
        bank = "Bancolombia",
        creditLimit = 40_000_000L,
        cutoffDay = 10,
        paymentDay = 25,
        pagoMinimo = 1_843_014L,
    )

    private fun crudo(terms: CardTerms): JsonObject =
        json.parseToJsonElement(json.encodeToString(CardTerms.serializer(), terms)) as JsonObject

    @Test
    fun `borrar el minimo viaja como una clave presente en null, no como una clave ausente`() {
        val borrado = crudo(masterBlack.copy(pagoMinimo = null))

        assertTrue(
            "pagoMinimo" in borrado,
            "sin la clave, el PUT lee «cliente viejo» y repone el mínimo: borrarlo se vuelve imposible",
        )
        assertEquals(
            JsonNull,
            borrado["pagoMinimo"],
            "y tiene que valer null: cualquier otra cosa sería elegirle un mínimo al dueño",
        )
    }

    /** Y una tarjeta recién creada, sin mínimo todavía, también dice que no lo tiene. */
    @Test
    fun `una tarjeta nueva sin minimo tambien manda la clave`() {
        assertTrue("pagoMinimo" in crudo(CardTerms(accountId = "acc_nueva", bank = "Nu", paymentDay = 15)))
    }

    /** Con mínimo cargado viaja el número que él tecleó, sin sorpresas. */
    @Test
    fun `con minimo cargado viaja ese minimo`() {
        val decodificada = json.decodeFromString(
            CardTerms.serializer(),
            json.encodeToString(CardTerms.serializer(), masterBlack),
        )
        assertEquals(1_843_014L, decodificada.pagoMinimo)
    }

    /**
     * **La otra mitad, que esta anotación NO cambia**: un cliente viejo que no manda la clave sigue
     * queriendo decir «no lo toques», y por eso el server no puede confiar solo en el modelo. Al
     * decodificar un cuerpo sin `pagoMinimo` el campo cae en su default, que es indistinguible de
     * un borrado a propósito — mirar el JSON crudo es lo único que los separa.
     */
    @Test
    fun `un cuerpo sin la clave decodifica en null, que es por que el server mira el crudo`() {
        val delApkViejo = """{"accountId":"acc_master_black","bank":"Bancolombia",
            "creditLimit":40000000,"cutoffDay":10,"paymentDay":25}"""
        assertEquals(null, json.decodeFromString(CardTerms.serializer(), delApkViejo).pagoMinimo)
    }
}
