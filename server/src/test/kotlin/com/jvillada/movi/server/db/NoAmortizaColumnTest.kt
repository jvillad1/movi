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
 * `no_amortiza` es una columna NUEVA en `financial_events`, que es **la tabla más poblada del
 * server** y la que tiene la historia entera del dueño adentro.
 *
 * **Las migraciones de este server corren DENTRO de la transacción de arranque**: un DDL que falle
 * la aborta entera y deja el server sin levantar — o sea al dueño sin app, no con un bug. Así que
 * la pregunta no es solo «¿queda la columna?» sino «¿qué SQL exactamente va a ejecutar Exposed
 * sobre `financial_events` con miles de filas adentro?».
 *
 * Mismo patrón que [SeguroMensualColumnTest] y [RemindMeColumnTest]: tabla con el esquema nuevo, se
 * le quita la columna, se insertan filas con SQL crudo (como las que hay en producción) y recién ahí
 * corre `createMissingTablesAndColumns`, que es lo que hace `DatabaseFactory.init()`.
 */
class NoAmortizaColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:no_amortiza_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Events)
            SchemaUtils.create(Events)
            exec("ALTER TABLE financial_events DROP COLUMN no_amortiza")
            // Un par de cuota ya registrado, con las dos patas iguales — que es como quedaban antes
            // de esta ola, y por eso NULL las describe bien.
            exec(
                """INSERT INTO financial_events
                   (id, user_id, account_id, type, amount, currency, category, description,
                    timestamp, source, reconciliation_status, transfer_id)
                   VALUES ('ev-dinero', 'u1', 'acc-ahorros', 'EXPENSE', 4215223, 'COP',
                           'Cuota de crédito', 'Cuota de Vehículo', 1788000000000, 'MANUAL',
                           'RECONCILED', 'tr-viejo')""",
            )
            exec(
                """INSERT INTO financial_events
                   (id, user_id, account_id, type, amount, currency, category, description,
                    timestamp, source, reconciliation_status, transfer_id)
                   VALUES ('ev-deuda', 'u1', 'acc-carro', 'INCOME', 4215223, 'COP',
                           'Cuota de crédito', 'Pago desde Bancolombia', 1788000000000, 'MANUAL',
                           'RECONCILED', 'tr-viejo')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        // La verificación que el arranque necesita, hecha ANTES de ejecutar nada: se le pregunta a
        // Exposed qué sentencias piensa correr. Un `CREATE INDEX` sobre miles de filas o un
        // `NOT NULL` sin default son las dos formas conocidas de tumbar esta transacción.
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Events) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("NO_AMORTIZA"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `los pagos que ya existian quedan en NULL, que es «par simetrico»`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Events)
            val patas = Events.selectAll().map { it.toFinancialEvent() }.associateBy { it.id }

            assertEquals(2, patas.size)
            assertNull(patas.getValue("ev-deuda").noAmortiza, "un par viejo es simétrico de verdad")
            assertNull(patas.getValue("ev-dinero").noAmortiza)
            // Y el resto de la fila sobrevivió: la migración no reescribe nada.
            assertEquals(4_215_223L, patas.getValue("ev-deuda").amount)
            assertEquals("tr-viejo", patas.getValue("ev-deuda").transferId)
        }
    }

    @Test
    fun `correrla dos veces no cambia nada`() {
        // Idempotencia: el server arranca muchas veces con la columna ya puesta.
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Events)
            SchemaUtils.createMissingTablesAndColumns(Events)
            assertEquals(0, SchemaUtils.addMissingColumnsStatements(Events).size)
        }
    }
}
