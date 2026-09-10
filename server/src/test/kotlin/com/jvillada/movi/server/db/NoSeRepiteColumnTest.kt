package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `no_se_repite` es una columna NUEVA en `financial_events`, la tabla más poblada del server.
 *
 * **Las migraciones de este server corren DENTRO de la transacción de arranque**: un DDL que falle
 * la aborta entera y deja el server sin levantar. Y esta columna trae una diferencia con las tres
 * que la precedieron ([NoAmortizaColumnTest] y compañía): es **NOT NULL**, porque acá el default
 * sí describe bien lo que ya existe —nadie marcó nada— y así todo lo que hoy se reconoce como
 * recurrente se sigue reconociendo.
 *
 * Un `NOT NULL` **sin** default sobre una tabla con filas es justamente lo que tumba el arranque.
 * Con default no, y eso es lo que se verifica acá antes que nada: que el DDL que Exposed piensa
 * emitir lleve el `DEFAULT` puesto.
 *
 * Mismo patrón que [NoAmortizaColumnTest]: tabla con el esquema nuevo, se le quita la columna, se
 * insertan filas con SQL crudo (como las que hay en producción) y recién ahí corre
 * `createMissingTablesAndColumns`, que es lo que hace `DatabaseFactory.init()`.
 */
class NoSeRepiteColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:no_se_repite_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Events)
            SchemaUtils.create(Events)
            exec("ALTER TABLE financial_events DROP COLUMN no_se_repite")
            // Los dos movimientos del caso real: la suscripción mensual de Microsoft y una compra
            // suelta en el mismo comercio. Antes de esta columna las dos se leían como recurrentes.
            exec(
                """INSERT INTO financial_events
                   (id, user_id, account_id, type, amount, currency, category, description,
                    timestamp, source, reconciliation_status)
                   VALUES ('ev-suscripcion', 'u1', 'acc-master', 'EXPENSE', 239900, 'COP',
                           'Entretenimiento', 'Microsoft', 1788000000000, 'MANUAL', 'RECONCILED')""",
            )
            exec(
                """INSERT INTO financial_events
                   (id, user_id, account_id, type, amount, currency, category, description,
                    timestamp, source, reconciliation_status)
                   VALUES ('ev-suelto', 'u1', 'acc-master', 'EXPENSE', 249000, 'COP',
                           'Entretenimiento', 'Microsoft', 1788100000000, 'MANUAL', 'RECONCILED')""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN con DEFAULT, sin indices`() {
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Events) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("NO_SE_REPITE"), ddl)
        // Lo que hace segura una columna NOT NULL sobre una tabla con datos: que traiga con qué
        // llenar las filas que ya están. Sin esto, el arranque se cae y el dueño se queda sin app.
        assertTrue(ddl.contains("DEFAULT"), "NOT NULL sin default tumba la transacción de arranque: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `lo que ya existia queda en false, o sea se sigue reconociendo igual que antes`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Events)
            val filas = Events.selectAll().map { it.toFinancialEvent() }.associateBy { it.id }

            assertEquals(2, filas.size)
            assertFalse(filas.getValue("ev-suscripcion").noSeRepite, "nadie marcó nada todavía")
            assertFalse(filas.getValue("ev-suelto").noSeRepite)
            // Y el resto de la fila sobrevivió: la migración no reescribe nada.
            assertEquals(249_000L, filas.getValue("ev-suelto").amount)
            assertEquals("Microsoft", filas.getValue("ev-suelto").description)
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
