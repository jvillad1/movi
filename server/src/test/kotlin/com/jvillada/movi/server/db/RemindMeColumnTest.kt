package com.jvillada.movi.server.db

import com.jvillada.movi.server.credits.toCardTerms
import com.jvillada.movi.server.credits.toCreditTerms
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `remind_me` es una columna NUEVA en tres tablas VIEJAS (recurring_rules, credit_terms,
 * card_terms). La pregunta que este test contesta es la única que importa al desplegar: **¿qué
 * pasa con las filas que ya existen?** La respuesta tiene que ser "siguen recibiendo
 * recordatorios", porque ese es el comportamiento que el dueño ya tiene y nadie pidió cambiarlo.
 *
 * Para probarlo de verdad se reconstruye el estado previo: se crean las tablas con el esquema
 * nuevo, se le QUITA la columna, se insertan filas con SQL crudo (como las que ya están en
 * producción) y recién ahí corre [SchemaUtils.createMissingTablesAndColumns] — el mismo
 * mecanismo que usa `DatabaseFactory.init()` al arrancar. Si el ALTER no trajera el default,
 * las filas viejas quedarían en false/null y el dueño dejaría de recibir avisos en silencio.
 */
class RemindMeColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:remind_me_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(RecurringRules, Credits, Cards)
            SchemaUtils.create(RecurringRules, Credits, Cards)

            // Estado previo al cambio: las tres tablas sin la columna.
            exec("ALTER TABLE recurring_rules DROP COLUMN remind_me")
            exec("ALTER TABLE credit_terms DROP COLUMN remind_me")
            exec("ALTER TABLE card_terms DROP COLUMN remind_me")

            // Filas "de producción", escritas por el código viejo.
            exec(
                """INSERT INTO recurring_rules (id, user_id, "name", category, amount, day_of_month, "type")
                   VALUES ('rr-vieja', 'u1', 'Arriendo', 'Vivienda', 1500000, 5, 'EXPENSE')""",
            )
            exec(
                """INSERT INTO credit_terms
                   (account_id, user_id, bank, principal, rate_ea, term_months, installment, day_of_month, start_date)
                   VALUES ('acc-loan', 'u1', 'Bancolombia', 30000000, 17.46, 60, 750000, 12, '2026-01-01')""",
            )
            exec(
                """INSERT INTO card_terms (account_id, user_id, bank, payment_day)
                   VALUES ('acc-card', 'u1', 'Davivienda', 20)""",
            )

            // El mismo mecanismo que corre al arrancar el server.
            SchemaUtils.createMissingTablesAndColumns(RecurringRules, Credits, Cards)
        }
    }

    @Test
    fun `una regla recurrente que ya existia sigue con el recordatorio prendido`() {
        transaction {
            val remindMe = RecurringRules.selectAll().single()[RecurringRules.remindMe]
            assertTrue(remindMe, "las reglas que ya existían deben seguir avisando")
        }
    }

    @Test
    fun `un credito que ya existia sigue con el recordatorio prendido`() {
        transaction {
            assertTrue(Credits.selectAll().single().toCreditTerms().remindMe)
        }
    }

    @Test
    fun `una tarjeta que ya existia sigue con el recordatorio prendido`() {
        transaction {
            assertTrue(Cards.selectAll().single().toCardTerms().remindMe)
        }
    }
}
