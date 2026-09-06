package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Que una columna nueva sobre una tabla vieja llegue de verdad a producción.**
 *
 * Este proyecto no tiene archivos de migración: `DatabaseFactory.crearYActualizarSchema()` corre
 * `SchemaUtils.create` (un `CREATE TABLE IF NOT EXISTS`, que sobre una tabla que ya existe **no
 * hace nada**) y después `createMissingTablesAndColumns`, que es lo único que emite un
 * `ALTER TABLE … ADD COLUMN`. Una columna nueva sobre una tabla que ya existe en producción solo
 * llega si **su tabla está en esa segunda lista**.
 *
 * Olvidarla no rompe ningún test de los normales: todos arrancan de un schema vacío, donde el
 * `create` deja la tabla completa y la columna siempre está. En producción, en cambio, cada
 * consulta que nombre esa columna falla con «column does not exist» — o sea la funcionalidad
 * entera, desde el primer request después del deploy.
 *
 * Pasó de verdad con `subscriptions.periodicidad`: la tabla existía en producción, `Subscriptions`
 * no estaba en la segunda lista, y CI estaba en verde. Este test reconstruye esa situación —crea
 * la tabla **sin** la columna, como estaba en producción, y después corre el arranque— así que
 * falla si alguien vuelve a agregar una columna sin registrar su tabla.
 */
class SchemaDeArranqueTest {

    @BeforeTest
    fun conectar() {
        Database.connect(
            url = "jdbc:h2:mem:schema_de_arranque_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            exec("DROP ALL OBJECTS")
            // La tabla `subscriptions` TAL CUAL estaba en producción antes de que existiera
            // `periodicidad` (verificado contra la base real). A mano y no con `SchemaUtils`:
            // el punto es que le falte la columna nueva.
            exec(
                """
                CREATE TABLE subscriptions (
                    id VARCHAR(50) NOT NULL PRIMARY KEY,
                    user_id VARCHAR(50) NOT NULL,
                    merchant_key VARCHAR(80) NOT NULL,
                    display_name VARCHAR(100) NOT NULL,
                    amount BIGINT NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    day_of_month INT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    confidence VARCHAR(10) NOT NULL,
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    occurrences INT NOT NULL,
                    account_id VARCHAR(50) NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun columnasDe(tabla: String): Set<String> = transaction {
        val encontradas = mutableSetOf<String>()
        exec(
            "SELECT column_name FROM information_schema.columns WHERE LOWER(table_name) = '$tabla'",
        ) { rs ->
            while (rs.next()) encontradas += rs.getString(1).lowercase()
        }
        encontradas
    }

    @Test
    fun `el arranque le agrega la columna nueva a una tabla que ya existia`() {
        assertTrue(
            "periodicidad" !in columnasDe("subscriptions"),
            "el test tiene que arrancar SIN la columna, si no no prueba nada",
        )

        DatabaseFactory.crearYActualizarSchema()

        assertTrue(
            "periodicidad" in columnasDe("subscriptions"),
            "`subscriptions` quedó sin `periodicidad`: falta su tabla en " +
                "createMissingTablesAndColumns, y en producción cada consulta de suscripciones " +
                "va a fallar con «column does not exist»",
        )
    }

    /**
     * Lo que ya estaba escrito queda como MENSUAL, que es la verdad: hasta que existió esta
     * columna, todo cobro era mensual por modelo. Si el ALTER dejara NULL, `toSubscription`
     * reventaría al leer una fila vieja.
     */
    @Test
    fun `las filas que ya existian quedan en MENSUAL, no en NULL`() {
        transaction {
            exec(
                """
                INSERT INTO subscriptions
                    (id, user_id, merchant_key, display_name, amount, currency, day_of_month,
                     status, confidence, first_seen, last_seen, occurrences)
                VALUES ('sub_vieja', 'usr_1', 'netflix', 'Netflix', 44900, 'COP', 5,
                        'CONFIRMED', 'HIGH', 0, 0, 3)
                """.trimIndent(),
            )
        }

        DatabaseFactory.crearYActualizarSchema()

        val periodicidad = transaction {
            var leida: String? = null
            exec("SELECT periodicidad FROM subscriptions WHERE id = 'sub_vieja'") { rs ->
                if (rs.next()) leida = rs.getString(1)
            }
            leida
        }
        assertEquals("MENSUAL", periodicidad)
    }
}
