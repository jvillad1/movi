package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
 * no estaba en la segunda lista, y CI estaba en verde.
 *
 * ### Por qué es una lista a mano y no un barrido de todas las tablas
 *
 * La primera versión de este archivo probaba **una sola** columna, con su DDL escrito a mano. Eso
 * daba una falsa sensación de cobertura: protegía `subscriptions` y nada más, y la siguiente
 * columna sobre otra tabla vieja habría pasado igual de silenciosa (lo notó la revisión de #168,
 * que agregó `users.sms_alert_muted`).
 *
 * Lo obvio sería exigir que **toda** tabla esté en `createMissingTablesAndColumns`, pero eso
 * contradice una decisión deliberada del arranque: una tabla NUEVA entra solo al `create`, porque
 * `createMissingTablesAndColumns` puede emitir un `CREATE INDEX` que falla sobre una tabla con
 * datos, y ese fallo **deja el server sin arrancar** (corre dentro de la transacción de arranque).
 * Ver los comentarios en `DatabaseFactory`. Automatizarlo forzaría ese riesgo.
 *
 * Así que la lista se mantiene a mano, pero **agregar un caso es una línea**: el par (tabla,
 * columna). No hace falta escribir DDL — el test arma el schema completo, le BORRA la columna para
 * simular la base vieja de producción, y verifica que el arranque la reponga.
 */
class SchemaDeArranqueTest {

    /**
     * Columnas agregadas sobre tablas que YA existían en producción. Cada vez que agregues una,
     * sumá su par acá: si su tabla no está en `createMissingTablesAndColumns`, este test falla.
     */
    private val columnasNuevasSobreTablasViejas = listOf(
        "subscriptions" to "periodicidad",   // #155 — periodicidad mensual/anual
        "users" to "sms_alert_muted",        // #168 — silenciar el aviso de captura de SMS
    )

    private val todasLasTablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
        RecurringOccurrences, SmsMessages, Credits, Cards, Subscriptions, PushSubscriptions,
        Screens, PasswordResetTokens, CardPaymentDismissals, Goals, CategoryPrefs, Documents,
    )

    @BeforeTest
    fun conectar() {
        Database.connect(
            url = "jdbc:h2:mem:schema_de_arranque_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
    }

    private fun columnasDe(tabla: String): Set<String> = transaction {
        val encontradas = mutableSetOf<String>()
        exec("SELECT column_name FROM information_schema.columns WHERE LOWER(table_name) = '$tabla'") { rs ->
            while (rs.next()) encontradas += rs.getString(1).lowercase()
        }
        encontradas
    }

    /** El schema completo, menos [columna] en [tabla]: la foto de la base de producción vieja. */
    private fun schemaSinLaColumna(tabla: String, columna: String) = transaction {
        exec("DROP ALL OBJECTS")
        SchemaUtils.create(tables = todasLasTablas)
        exec("ALTER TABLE $tabla DROP COLUMN $columna")
    }

    @Test
    fun `el arranque repone toda columna nueva sobre una tabla que ya existia`() {
        columnasNuevasSobreTablasViejas.forEach { (tabla, columna) ->
            schemaSinLaColumna(tabla, columna)
            assertTrue(
                columna !in columnasDe(tabla),
                "el caso «$tabla.$columna» tiene que arrancar SIN la columna, si no no prueba nada",
            )

            DatabaseFactory.crearYActualizarSchema()

            assertTrue(
                columna in columnasDe(tabla),
                "«$tabla» quedó sin «$columna»: falta esa tabla en createMissingTablesAndColumns, " +
                    "y en producción cada consulta que la nombre va a fallar con «column does not exist»",
            )
        }
    }

    /**
     * Lo que ya estaba escrito conserva un valor con sentido, no NULL. Si el ALTER dejara NULL,
     * `toSubscription` reventaría al leer una fila vieja — el caso concreto que motivó el test.
     */
    @Test
    fun `las filas que ya existian quedan en MENSUAL, no en NULL`() {
        schemaSinLaColumna("subscriptions", "periodicidad")
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
