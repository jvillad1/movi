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
 * `llave` es una columna NUEVA en `known_destinations`, que ya existe en producción con los destinos
 * del dueño (la cuenta de Caro). Mismo patrón que [NoAmortizaColumnTest]: tabla con el esquema
 * nuevo, se le quita la columna, se inserta una fila con SQL crudo como la de producción, y recién
 * ahí corre `createMissingTablesAndColumns`, que es lo que hace el arranque.
 *
 * Lo que importa es QUÉ SQL corre: la tabla entra por primera vez a esa lista, y lo único que se
 * admite es el `ADD COLUMN` nullable — ni un `CREATE INDEX` (su índice de `user_id` ya existe) ni un
 * `NOT NULL`, que tumbarían la transacción de arranque y dejarían al dueño sin app.
 */
class KnownDestinationsLlaveColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:known_destinations_llave_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(KnownDestinations)
            SchemaUtils.create(KnownDestinations)
            exec("ALTER TABLE known_destinations DROP COLUMN llave")
            exec(
                """INSERT INTO known_destinations (id, user_id, nombre, numero, de_quien, created_at)
                   VALUES ('dst_caro', 'u1', 'Caro', '31973270756', 'esposa', 1788000000000)""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(KnownDestinations) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("LLAVE"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `el destino que ya existia queda intacto y sin llave`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(KnownDestinations)
            val fila = KnownDestinations.selectAll().single()
            assertEquals("31973270756", fila[KnownDestinations.numero], "el número no se toca")
            assertEquals("Caro", fila[KnownDestinations.nombre])
            assertNull(fila[KnownDestinations.llave], "ninguna fila vieja tenía llave: NULL es la verdad")
        }
    }
}
