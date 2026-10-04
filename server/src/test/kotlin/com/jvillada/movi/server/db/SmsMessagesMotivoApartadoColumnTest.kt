package com.jvillada.movi.server.db

import com.jvillada.movi.server.db.Migrations.apartarLosPendientesQueNoSonMovimientos
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
 * `motivo_apartado` es una columna NUEVA en `sms_messages`, que existe en producción con los mensajes
 * del dueño. Mismo patrón que [KnownDestinationsLlaveColumnTest]: tabla con el esquema nuevo, se le
 * quita la columna, se insertan filas con SQL crudo como las de producción, y recién ahí corre lo
 * que hace el arranque.
 *
 * Lo único que se admite es el `ADD COLUMN` nullable: ni un `CREATE INDEX` (el de `user_id` ya
 * existe) ni un `NOT NULL`, que tumbarían la transacción de arranque y dejarían al dueño sin app.
 */
class SmsMessagesMotivoApartadoColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:sms_messages_motivo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages)
            SchemaUtils.create(SmsMessages)
            exec("ALTER TABLE sms_messages DROP COLUMN motivo_apartado")
            exec(
                """INSERT INTO sms_messages (id, user_id, time, bank, text, state, det) VALUES
                   ('sms_compra', 'u1', '2026-09-01 10:00', '85540', 'Bancolombia: Compraste ${'$'}12.345,00 en TIENDA DE PRUEBA con tu T.Deb *1111.', 'confirmed', ''),
                   ('sms_codigo', 'u1', '2026-09-02 10:00', '85540', 'Bancolombia: Tu clave dinamica es 482913. Es personal e intransferible.', 'pending', '')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(SmsMessages) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("MOTIVO_APARTADO"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los mensajes que ya estaban quedan intactos, y el pendiente que no es movimiento se aparta`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(SmsMessages)
            val compra = SmsMessages.selectAll().where { SmsMessages.id eq "sms_compra" }.single()
            assertEquals("confirmed", compra[SmsMessages.state], "lo confirmado no se toca")
            assertNull(compra[SmsMessages.motivoApartado])

            assertEquals(1, apartarLosPendientesQueNoSonMovimientos())
            val codigo = SmsMessages.selectAll().where { SmsMessages.id eq "sms_codigo" }.single()
            assertEquals("ignored", codigo[SmsMessages.state])
            assertEquals("CODIGO_DE_VERIFICACION", codigo[SmsMessages.motivoApartado])
        }
    }
}
