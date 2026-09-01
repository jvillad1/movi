package com.jvillada.movi.server.db

import com.jvillada.movi.server.credits.toCreditTerms
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
 * `insurance_monthly` es una columna NUEVA en una tabla VIEJA con datos reales adentro.
 *
 * **Las migraciones de este server corren DENTRO de la transacción de arranque**: un DDL que falle
 * la aborta entera y deja el server sin levantar — o sea al dueño sin app, no con un bug. Así que
 * la pregunta no es solo «¿queda la columna?» sino «¿qué SQL exactamente va a ejecutar Exposed
 * sobre `credit_terms` con seis créditos adentro?».
 *
 * Se reconstruye el estado previo igual que en [RemindMeColumnTest]: tabla con el esquema nuevo, se
 * le quita la columna, se insertan filas con SQL crudo (como las que hay en producción) y recién
 * ahí corre `createMissingTablesAndColumns`, que es lo que hace `DatabaseFactory.init()`.
 */
class SeguroMensualColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:seguro_mensual_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Credits)
            SchemaUtils.create(Credits)
            exec("ALTER TABLE credit_terms DROP COLUMN insurance_monthly")
            exec(
                """INSERT INTO credit_terms
                   (account_id, user_id, bank, principal, rate_ea, term_months, installment, day_of_month, start_date)
                   VALUES ('acc-9695', 'u1', 'Bancolombia', 45000000, 11.27, 132, 1286548, 15, '2026-01-01')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        // La verificación que el arranque necesita, hecha ANTES de ejecutar nada: se le pregunta a
        // Exposed qué sentencias piensa correr. Un `CREATE INDEX` sobre datos o un `NOT NULL` sin
        // default son las dos formas conocidas de tumbar esta transacción.
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Credits) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("INSURANCE_MONTHLY"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los creditos que ya existian quedan sin seguro, no en cero ni rotos`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Credits)
            val terms = Credits.selectAll().single().toCreditTerms()

            assertNull(terms.insuranceMonthly, "sin declarar no es lo mismo que declarar 0")
            // Y el resto de la fila sobrevivió: la migración no reescribe nada.
            assertEquals(1_286_548L, terms.installment)
            assertEquals(11.27, terms.rateEa)
        }
    }

    @Test
    fun `correrla dos veces no cambia nada`() {
        // Idempotencia: el server arranca muchas veces con la columna ya puesta.
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Credits)
            SchemaUtils.createMissingTablesAndColumns(Credits)
            assertEquals(0, SchemaUtils.addMissingColumnsStatements(Credits).size)
        }
    }
}
