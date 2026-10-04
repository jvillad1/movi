package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Un tercero, varias formas de reconocerlo** (4-oct-2026): números, llaves y el nombre con que lo
 * nombra el banco. Textos y números sintéticos — ninguno es del dueño.
 */
class TercerosConVariosIdentificadoresTest {

    private val lucia = DestinoConocido(id = "dst_lucia", nombre = "Lucía", numero = "", deQuien = "hermana")
        .conIdentificadores(
            listOf(
                IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "55500001111"),
                IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@lucia99"),
                IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "lucia fernanda gomez"),
            ),
        )
    private val tienda = DestinoConocido(id = "dst_tienda", nombre = "La Tienda", numero = "", llave = "0011223344")
    private val destinos = listOf(lucia, tienda)

    @Test
    fun `numero y llave quedan en el primero de cada clase, para el APK instalado`() {
        assertEquals("55500001111", lucia.numero)
        assertEquals("@lucia99", lucia.llave)
        assertEquals(3, lucia.todosLosIdentificadores().size)
    }

    @Test
    fun `se reconoce por cualquiera de sus identificadores`() {
        assertEquals(lucia, destinoQueNombra("Transferiste \$50.000 desde tu cuenta *9999 a la cuenta *55500001111", destinos))
        assertEquals(lucia, destinoQueNombra("JUAN, transferiste \$50.000 a la llave @lucia99 desde tu cuenta *9999", destinos))
        assertEquals(lucia, destinoQueNombra("Recibiste 80.000,00 en tu cuenta: Te llegó dinero de LUCIA FERNANDA GOMEZ con tu llave.", destinos))
        assertEquals(tienda, destinoQueNombra("pagaste \$9.000 por codigo QR desde tu cuenta *9999 a la llave 0011223344", destinos))
    }

    @Test
    fun `el nombre con que el banco nombra a la persona la reconoce aunque use otra llave`() {
        val texto = "JUAN, transferiste \$30.000 a la llave @otrallave desde tu cuenta *9999 a LUCIA FERNANDA GOMEZ el 01/10/26 a las 10:00."
        assertEquals(lucia, destinoQueNombra(texto, destinos))
    }

    @Test
    fun `recibiste una transferencia de X se reconoce, con o sin tildes`() {
        val texto = "Banco: PEDRO, recibiste una transferencia de LUCÍA FERNANDA GÓMEZ por \$120.000 en tu cuenta *9999 conectada a la llave @PEDRO1 el 28/09/26."
        assertEquals(lucia, destinoQueNombra(texto, destinos))
    }

    @Test
    fun `la llave del dueño, conectada a la llave, no se lee como la de otra persona`() {
        val texto = "Banco: PEDRO, recibiste una transferencia de MARTA RUIZ ORTIZ por \$70.000 en tu cuenta *9999 conectada a la llave @PEDRO1 el 28/09/26."
        val id = identificadorDelDestinoEn(texto)
        assertEquals(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "marta ruiz ortiz"), id, "es quien mandó, no la llave del dueño")
    }

    @Test
    fun `la plata que entra se propone como de ella, y la que sale como para ella`() {
        val texto = "Banco: PEDRO, recibiste una transferencia de LUCIA FERNANDA GOMEZ por \$120.000 en tu cuenta *9999 el 28/09/26."
        val entra = ParsedSms(120_000.0, "LUCIA FERNANDA GOMEZ", TransactionType.INCOME, "Transferencia")
        assertEquals("Transferencia de Lucía", conElDestinoConocido(entra, texto, destinos).merchant)

        val sale = ParsedSms(30_000.0, "Transferencia a la cuenta *55500001111", TransactionType.EXPENSE, "Otros")
        assertEquals(
            "Transferencia a Lucía",
            conElDestinoConocido(sale, "Transferiste \$30.000 desde tu cuenta *9999 a la cuenta *55500001111", destinos).merchant,
        )
    }

    @Test
    fun `la ficha separa lo que le enviaste de lo que te envio`() {
        val eventos = listOf(
            evento("e1", 30_000, TransactionType.EXPENSE, "Transferencia a Lucía", raw = "a la cuenta *55500001111"),
            evento("e2", 120_000, TransactionType.INCOME, "Ingreso", raw = "recibiste una transferencia de LUCIA FERNANDA GOMEZ por \$120.000"),
            evento("e3", 80_000, TransactionType.INCOME, "Transferencia de Lucía"),
            evento("e4", 5_000, TransactionType.EXPENSE, "Almuerzo"),
        )
        val conTotales = conLoQueSeLeMando(lucia, eventos)
        assertEquals(mapOf("COP" to 30_000L), conTotales.totales)
        assertEquals(1, conTotales.cuantos)
        assertEquals(mapOf("COP" to 200_000L), conTotales.recibidos)
        assertEquals(2, conTotales.cuantosRecibidos)
        assertEquals(listOf("e2", "e3"), movimientosDesdeElDestino(lucia, eventos).map { it.id })
    }

    @Test
    fun `un destino de un cliente viejo, sin la lista, se sigue reconociendo por numero y llave`() {
        val viejo = DestinoConocido(id = "dst_v", nombre = "Viejo", numero = "77700002222", llave = "@viejo")
        assertTrue(viejo.identificadores.isEmpty())
        assertTrue(elDestinoConoce(viejo, IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "2222")))
        assertTrue(elDestinoConoce(viejo, IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@VIEJO")))
        assertEquals(listOf("·2222", "llave @viejo"), identificadoresDelDestino(viejo))
    }

    @Test
    fun `un JSON sin los campos nuevos se lee igual, y uno con campos de mas tambien`() {
        val json = Json { ignoreUnknownKeys = true }
        val deUnServerViejo = json.decodeFromString(DestinoConocido.serializer(), """{"id":"d","nombre":"Caro","numero":"12345678"}""")
        assertEquals(emptyList(), deUnServerViejo.identificadores)
        assertNull(deUnServerViejo.tipo)
        assertEquals(listOf("12345678"), deUnServerViejo.numeros())
        val conDeMas = json.decodeFromString(
            DestinoConocido.serializer(),
            """{"id":"d","nombre":"Caro","numero":"","identificadores":[{"tipo":"LLAVE","valor":"@caro"}],"tipo":"PERSONA","algoNuevo":1}""",
        )
        assertEquals(TipoDeTercero.PERSONA, conDeMas.tipo)
        assertTrue(elDestinoConoce(conDeMas, IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@caro")))
    }

    @Test
    fun `la ficha lee todos sus identificadores`() {
        assertEquals(listOf("·1111", "llave @lucia99", "Lucia Fernanda Gomez"), identificadoresDelDestino(lucia))
    }

    @Test
    fun `sumar y quitar un identificador no duplica ni pierde los demas`() {
        val conOtra = sumarIdentificador(lucia, IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@LUCIA99"))
        assertEquals(3, conOtra.todosLosIdentificadores().size, "la misma llave escrita distinto no se repite")
        val sinNumero = quitarIdentificador(lucia, IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "55500001111"))
        assertEquals("", sinNumero.numero)
        assertEquals(2, sinNumero.todosLosIdentificadores().size)
    }

    @Test
    fun `no se ofrece guardar lo descartado ni una cola que el banco dice que es tuya`() {
        val numero = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "88812345678")
        val cuentas = listOf(Account("a", "Ahorros", AccountType.SAVINGS, 0))
        assertTrue(ofreceGuardarElDestino(numero, destinos, cuentas))
        assertFalse(ofreceGuardarElDestino(numero, destinos, cuentas, descartados = setOf(numero.clave)))
        assertFalse(ofreceGuardarElDestino(numero, destinos, cuentas, descartados = setOf("COLA:5678")))
        assertFalse(
            ofreceGuardarElDestino(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@lucia99"), destinos, cuentas),
            "ya es de Lucía",
        )
    }

    private fun evento(id: String, monto: Long, tipo: TransactionType, descripcion: String, raw: String? = null) =
        FinancialEvent(
            id = id, accountId = "a", type = tipo, amount = monto, category = "Otros",
            description = descripcion, timestamp = 1_790_000_000_000L + monto, rawPayload = raw,
        )
}
