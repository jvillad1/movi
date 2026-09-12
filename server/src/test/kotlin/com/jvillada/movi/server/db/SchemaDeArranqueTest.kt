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
        // Ola 19 — la marca de «este monto lo corregí yo», que el barrido consulta en CADA
        // detección. Sin la columna, «Buscar cobros» falla entero en producción.
        "subscriptions" to "monto_corregido_a_mano",
        // Ola 19 — el cuarto renglón de la cuota (los $25.000 de «otros conceptos» del Vehículo
        // 8761). `credit_terms` existe en producción desde hace olas, y TODA consulta de créditos
        // nombra esta columna: sin el ALTER, la pantalla de créditos entera deja de cargar.
        "credit_terms" to "otros_cargos_mensuales",
        // El pago mínimo del extracto. `card_terms` existe en producción con las cinco tarjetas
        // del dueño adentro, y TODA consulta de tarjetas nombra esta columna (`toCardTerms`):
        // sin el ALTER, la pantalla de Créditos y «Próximos pagos» dejan de cargar enteras.
        "card_terms" to "pago_minimo",
    )

    /**
     * Columnas de texto que se **ensancharon** sobre una tabla que ya existía en producción:
     * (tabla, columna, largo viejo, largo nuevo).
     *
     * Es una trampa distinta —y peor— que la de la lista de arriba. Una columna que falta revienta
     * ruidosamente («column does not exist»); un largo que se quedó en el valor viejo no se nota
     * hasta que alguien escribe un texto largo, y ahí el `PUT` falla con un 500 sin explicación
     * mientras el resto de la app anda perfecto. Y a diferencia de la lista de arriba, acá el
     * `create` del arranque tampoco ayuda en local: en una base vacía la columna nace con el largo
     * nuevo y el test pasaría igual sin haber probado nada.
     */
    private val columnasDeTextoEnsanchadas = listOf(
        Ensanche("credit_terms", "notes", de = 300, a = 500),   // #TBD — la nota mutilada del Vehículo 8761
        // La misma trampa, en la tabla hermana y descubierta del mismo modo: cargando los mínimos
        // de la Master Black (2026-09-12) el UPDATE reventó con «value too long». La nota de una
        // tarjeta tiene que caber el corte, el pago, el cupo, la tasa y DE QUÉ EXTRACTO salieron —
        // y esto último es justo lo que se cortaba, o sea lo único que evita leer el mínimo de un
        // corte viejo como si fuera el de este mes.
        Ensanche("card_terms", "notes", de = 300, a = 500),
    )

    data class Ensanche(val tabla: String, val columna: String, val de: Int, val a: Int)

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

    /** El schema completo, pero con [columna] angosta como en la base vieja de producción. */
    private fun schemaConLaColumnaAngosta(e: Ensanche) = transaction {
        exec("DROP ALL OBJECTS")
        SchemaUtils.create(tables = todasLasTablas)
        exec("ALTER TABLE ${e.tabla} ALTER COLUMN ${e.columna} SET DATA TYPE VARCHAR(${e.de})")
    }

    private fun anchoDe(tabla: String, columna: String): Int? = transaction {
        var ancho: Int? = null
        exec(
            "SELECT character_maximum_length FROM information_schema.columns " +
                "WHERE LOWER(table_name) = '$tabla' AND LOWER(column_name) = '$columna'",
        ) { rs -> if (rs.next()) ancho = rs.getInt(1) }
        ancho
    }

    @Test
    fun `el arranque ensancha una columna de texto que se quedo corta`() {
        columnasDeTextoEnsanchadas.forEach { e ->
            schemaConLaColumnaAngosta(e)
            assertEquals(
                e.de, anchoDe(e.tabla, e.columna),
                "el caso «${e.tabla}.${e.columna}» tiene que arrancar angosto, si no no prueba nada",
            )

            DatabaseFactory.crearYActualizarSchema()

            assertEquals(
                e.a, anchoDe(e.tabla, e.columna),
                "«${e.tabla}.${e.columna}» siguió en ${e.de}: el largo nuevo se quedó en el código y " +
                    "en producción cualquier texto más largo revienta el guardado con un 500",
            )
        }
    }

    /**
     * Y lo que ya estaba escrito sigue ahí. Ensanchar un `varchar` en Postgres es metadata y no
     * reescribe la tabla, pero eso es una promesa del motor: acá se comprueba, porque este DDL
     * corre DENTRO de la transacción de arranque y sobre las notas reales de seis créditos.
     */
    @Test
    fun `ensanchar no le toca una letra a las notas que ya estaban`() {
        val e = columnasDeTextoEnsanchadas.single { it.tabla == "credit_terms" && it.columna == "notes" }
        schemaConLaColumnaAngosta(e)
        val notaVieja = "Préstamo de papá · un solo pago"
        transaction {
            exec(
                """
                INSERT INTO credit_terms
                    (account_id, user_id, bank, principal, rate_ea, term_months, installment,
                     day_of_month, start_date, notes)
                VALUES ('acc-techo', 'usr_1', 'Papá', 10000000, 0.0, 1, 10000000, 27,
                        '2026-09-01', '$notaVieja')
                """.trimIndent(),
            )
        }

        DatabaseFactory.crearYActualizarSchema()

        val leida = transaction {
            var v: String? = null
            exec("SELECT notes FROM credit_terms WHERE account_id = 'acc-techo'") { rs ->
                if (rs.next()) v = rs.getString(1)
            }
            v
        }
        assertEquals(notaVieja, leida)

        // Y la nota que antes no cabía ahora entra entera: 500 caracteres exactos.
        val notaLarga = "x".repeat(e.a)
        transaction {
            exec("UPDATE credit_terms SET notes = '$notaLarga' WHERE account_id = 'acc-techo'")
        }
        val larga = transaction {
            var v: String? = null
            exec("SELECT notes FROM credit_terms WHERE account_id = 'acc-techo'") { rs ->
                if (rs.next()) v = rs.getString(1)
            }
            v
        }
        assertEquals(e.a, larga?.length, "una nota de ${e.a} caracteres tiene que entrar sin cortarse")
    }

    /**
     * **Y la marca del monto queda en `false`, no en NULL.** Es la misma clase de trampa que la
     * de arriba con un final peor: `toSubscription` lee esta columna como `Boolean` no nulo, así
     * que un NULL tumbaría el `GET /api/subscriptions` entero —la lista de suscripciones y el
     * total del mes— y no solo una fila. Lo que lo evita es el `.default(false)`, que viaja
     * dentro del mismo `ALTER TABLE … ADD COLUMN`.
     */
    @Test
    fun `las filas que ya existian no quedan marcadas como corregidas`() {
        schemaSinLaColumna("subscriptions", "monto_corregido_a_mano")
        transaction {
            exec(
                """
                INSERT INTO subscriptions
                    (id, user_id, merchant_key, display_name, amount, currency, day_of_month,
                     status, confidence, first_seen, last_seen, occurrences)
                VALUES ('sub_vieja_monto', 'usr_1', 'netflix', 'Netflix', 44900, 'COP', 19,
                        'CONFIRMED', 'HIGH', 0, 0, 3)
                """.trimIndent(),
            )
        }

        DatabaseFactory.crearYActualizarSchema()

        val marcada = transaction {
            var leida: Boolean? = null
            var eraNull = true
            exec("SELECT monto_corregido_a_mano FROM subscriptions WHERE id = 'sub_vieja_monto'") { rs ->
                if (rs.next()) { leida = rs.getBoolean(1); eraNull = rs.wasNull() }
            }
            eraNull to leida
        }
        assertEquals(false to false, marcada, "una fila vieja no puede nacer con el monto congelado")
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
