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
 * `last_edited_at` es una columna NUEVA sobre `accounts`, que en producción ya tiene adentro todas
 * las cuentas del dueño. Gemelo de [LastEditedAtColumnTest] y por el mismo motivo: **las
 * migraciones corren DENTRO de la transacción de arranque**, así que un DDL que falle la aborta
 * entera y deja el server sin levantar — o sea al dueño sin app, no con un bug.
 *
 * Y la segunda cosa que fija, propia de esta columna: las cuentas que ya existen quedan en NULL.
 * NULL significa «nadie la editó», y es lo que hace que un reenvío del teléfono sobre una de ellas
 * se comporte **exactamente como hasta hoy** (ver `pisaElReenvio` en `EventRoutes.kt`, que es la
 * misma función para cuentas y movimientos). Un backfill con `now` las habría hecho ganar o perder
 * discusiones que nunca existieron.
 */
class CuentaLastEditedAtColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cuenta_last_edited_at_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Accounts)
            SchemaUtils.create(Accounts)
            // La base de producción, antes de esta columna.
            exec("ALTER TABLE accounts DROP COLUMN last_edited_at")
            exec(
                """INSERT INTO accounts (id, user_id, name, type, balance, currency)
                   VALUES ('acc-libranza', 'u1', 'Libranza 4817', 'LOAN', 257000000, 'COP')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Accounts) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("LAST_EDITED_AT"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `las cuentas que ya existian quedan en NULL, que es «nadie las editó»`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Accounts)
            val fila = Accounts.selectAll().single()

            assertNull(fila[Accounts.lastEditedAt])
            // Y el resto de la fila sobrevivió: la migración no reescribe nada.
            assertEquals("Libranza 4817", fila[Accounts.name])
            assertEquals(257_000_000L, fila[Accounts.balance])
        }
    }

    @Test
    fun `correrla dos veces no cambia nada`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Accounts)
            SchemaUtils.createMissingTablesAndColumns(Accounts)
            assertEquals(0, SchemaUtils.addMissingColumnsStatements(Accounts).size)
        }
    }
}
