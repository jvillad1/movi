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
 * `period_starts` es una columna NUEVA en `users`, y las migraciones de este server corren
 * **dentro de la transacción de arranque**: un DDL que falle la aborta entera y deja al dueño sin
 * app, no con un bug.
 *
 * Mismo patrón que [NoAmortizaColumnTest]: tabla con el esquema nuevo, se le quita la columna, se
 * insertan filas con SQL crudo (como las que hay en producción) y recién ahí corre
 * `createMissingTablesAndColumns`.
 */
class PeriodStartsColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:period_starts_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
            exec("ALTER TABLE users DROP COLUMN period_starts")
            exec(
                """INSERT INTO users (id, email, password_hash, name, period_cutoff_day)
                   VALUES ('u1', 'jvillad1@gmail.com', 'hash', 'Juan', 25)""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Users) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("PERIOD_STARTS"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `la cuenta que ya existia queda en NULL, o sea sin excepciones`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users)
            val fila = Users.selectAll().single()

            assertNull(fila[Users.periodStarts], "nadie declaró ningún arranque propio")
            // Y el resto de la fila sobrevivió: la migración no reescribe nada.
            assertEquals(25, fila[Users.periodCutoffDay], "el día de corte no se toca")
            assertEquals("jvillad1@gmail.com", fila[Users.email])
        }
    }

    @Test
    fun `correrla dos veces no cambia nada`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users)
            SchemaUtils.createMissingTablesAndColumns(Users)
            assertEquals(0, SchemaUtils.addMissingColumnsStatements(Users).size)
        }
    }
}
