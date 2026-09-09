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
 * `otros_cargos_mensuales` es una columna NUEVA en una tabla VIEJA con los nueve créditos reales
 * del dueño adentro.
 *
 * Es el gemelo exacto de [SeguroMensualColumnTest], y existe por lo mismo: **las migraciones de
 * este server corren DENTRO de la transacción de arranque**, así que un DDL que falle la aborta
 * entera y deja el server sin levantar — al dueño sin app, no con un bug. La pregunta que se
 * contesta acá no es «¿queda la columna?» sino «¿qué SQL exactamente va a ejecutar Exposed sobre
 * `credit_terms` con nueve créditos adentro?».
 *
 * (`SchemaDeArranqueTest` cubre la otra mitad: que `Credits` esté en la lista de
 * `createMissingTablesAndColumns`, sin la cual este ALTER no se emite nunca.)
 */
class OtrosCargosColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:otros_cargos_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Credits)
            SchemaUtils.create(Credits)
            exec("ALTER TABLE credit_terms DROP COLUMN otros_cargos_mensuales")
            // El Vehículo 8761 tal como está hoy en producción: con seguro declarado y sin ningún
            // lugar donde poner los $25.000 de «otros conceptos» de su extracto.
            exec(
                """INSERT INTO credit_terms
                   (account_id, user_id, bank, principal, rate_ea, term_months, installment,
                    day_of_month, start_date, insurance_monthly)
                   VALUES ('acc-8761', 'u1', 'Banco de Occidente', 190000000, 18.16, 60, 4101123,
                           20, '2024-06-01', 89100)""",
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
        assertTrue(ddl.contains("ADD") && ddl.contains("OTROS_CARGOS_MENSUALES"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los creditos que ya existian quedan sin otros cargos, no en cero ni rotos`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Credits)
            val terms = Credits.selectAll().single().toCreditTerms()

            assertNull(terms.otrosCargosMensuales, "sin declarar no es lo mismo que declarar 0")
            // Y el resto de la fila sobrevivió: la migración no reescribe nada. En particular el
            // seguro, que es el campo con el que más fácil se confunde este.
            assertEquals(89_100L, terms.insuranceMonthly)
            assertEquals(4_101_123L, terms.installment)
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
