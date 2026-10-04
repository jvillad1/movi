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
 * `confirmado_en` es columna NUEVA en `sms_messages`, que existe en producción con los mensajes del
 * dueño. Mismo patrón que [SmsMessagesMismoPagoColumnTest]: se quita, se inserta una fila como las de
 * producción, y recién ahí corre lo que hace el arranque. Solo se admite un `ADD COLUMN` nullable.
 */
class SmsMessagesConfirmadoEnColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:sms_messages_confirmado_en_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages)
            SchemaUtils.create(SmsMessages)
            exec("ALTER TABLE sms_messages DROP COLUMN confirmado_en")
            exec(
                """INSERT INTO sms_messages (id, user_id, time, bank, text, state, det) VALUES
                   ('sms_compra', 'u1', '2026-09-01 10:00', '85540', 'Bancolombia: Compraste ${'$'}12.345,00 en TIENDA DE PRUEBA con tu T.Deb *1111.', 'confirmed', '')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(SmsMessages) }
        assertEquals(1, sentencias.size, "una sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("CONFIRMADO_EN"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los mensajes que ya estaban quedan intactos y sin hora`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(SmsMessages)
            val fila = SmsMessages.selectAll().where { SmsMessages.id eq "sms_compra" }.single()
            assertEquals("confirmed", fila[SmsMessages.state])
            assertNull(fila[SmsMessages.confirmadoEn])
        }
    }
}
