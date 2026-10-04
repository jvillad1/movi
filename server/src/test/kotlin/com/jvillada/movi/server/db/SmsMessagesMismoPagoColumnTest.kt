package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `no_es_el_mismo_pago` y `evento_id` son columnas NUEVAS en `sms_messages`, que existe en producción
 * con los mensajes del dueño. Mismo patrón que [SmsMessagesMotivoApartadoColumnTest]: se quitan las
 * dos, se insertan filas como las de producción, y recién ahí corre lo que hace el arranque. Solo se
 * admiten dos `ADD COLUMN` nullable: ni índices ni `NOT NULL`, que tumbarían el arranque.
 */
class SmsMessagesMismoPagoColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:sms_messages_mismo_pago_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages)
            SchemaUtils.create(SmsMessages)
            exec("ALTER TABLE sms_messages DROP COLUMN no_es_el_mismo_pago")
            exec("ALTER TABLE sms_messages DROP COLUMN evento_id")
            exec(
                """INSERT INTO sms_messages (id, user_id, time, bank, text, state, det) VALUES
                   ('sms_compra', 'u1', '2026-09-01 10:00', '85540', 'Bancolombia: Compraste ${'$'}12.345,00 en TIENDA DE PRUEBA con tu T.Deb *1111.', 'confirmed', '')""",
            )
        }
    }

    @Test
    fun `los unicos DDL son dos ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(SmsMessages) }
        assertEquals(2, sentencias.size, "dos sentencias: $sentencias")
        val ddl = sentencias.joinToString("\n").uppercase()
        assertTrue(ddl.contains("NO_ES_EL_MISMO_PAGO") && ddl.contains("EVENTO_ID"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los mensajes que ya estaban quedan intactos y sin marca`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(SmsMessages)
            val fila = SmsMessages.selectAll().where { SmsMessages.id eq "sms_compra" }.single()
            assertEquals("confirmed", fila[SmsMessages.state])
            assertNull(fila[SmsMessages.noEsElMismoPago])
            assertNull(fila[SmsMessages.eventoId])
        }
    }
}
