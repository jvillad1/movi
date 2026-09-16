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
 * `last_edited_at` es una columna NUEVA en `financial_events` — la tabla más poblada del server, la
 * que tiene la historia entera del dueño adentro. Gemelo de [NoAmortizaColumnTest] y por el mismo
 * motivo: **las migraciones corren DENTRO de la transacción de arranque**, así que un DDL que falle
 * la aborta entera y deja el server sin levantar. O sea al dueño sin app, no con un bug.
 *
 * Y hay una segunda cosa que fijar acá, propia de esta columna: las filas que ya existen tienen que
 * quedar en NULL. NULL significa «nadie editó esto», y es lo que hace que sigan comportándose
 * **exactamente como hasta hoy** frente a un reenvío del teléfono (ver `pisaElReenvio` en
 * `EventRoutes.kt`): un backfill con `now`, o con el `timestamp` de la fila, las habría hecho ganar
 * o perder discusiones que nunca existieron.
 */
class LastEditedAtColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:last_edited_at_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Events)
            SchemaUtils.create(Events)
            exec("ALTER TABLE financial_events DROP COLUMN last_edited_at")
            exec(
                """INSERT INTO financial_events
                   (id, user_id, account_id, type, amount, currency, category, description,
                    timestamp, source, reconciliation_status)
                   VALUES ('ev-viejo', 'u1', 'acc-ahorros', 'EXPENSE', 165289, 'COP',
                           'Mercado', 'Éxito', 1757000000000, 'MANUAL', 'RECONCILED')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Events) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("LAST_EDITED_AT"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los movimientos que ya existian quedan en NULL, que es «nadie los editó»`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Events)
            val fila = Events.selectAll().map { it.toFinancialEvent() }.single()

            assertNull(fila.lastEditedAt)
            // Y el resto de la fila sobrevivió: la migración no reescribe nada.
            assertEquals(165_289L, fila.amount)
            assertEquals("Mercado", fila.category)
        }
    }

    @Test
    fun `correrla dos veces no cambia nada`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Events)
            SchemaUtils.createMissingTablesAndColumns(Events)
            assertEquals(0, SchemaUtils.addMissingColumnsStatements(Events).size)
        }
    }
}
