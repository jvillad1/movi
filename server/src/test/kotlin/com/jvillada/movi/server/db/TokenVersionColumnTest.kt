package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `users.token_version` es una columna NUEVA en una tabla VIEJA, y la pregunta que importa al
 * desplegar es qué pasa con las cuentas que ya existen: **tienen que quedar en 0**, porque 0 es lo
 * que vale un token sin el claim `tv` —el que tiene hoy el teléfono del dueño—. Si el ALTER las
 * dejara en otro valor (o en NULL), el despliegue desloguearía a todo el mundo.
 *
 * Mismo montaje que `RemindMeColumnTest`: el esquema nuevo, se le quita la columna, se insertan
 * filas con SQL crudo como las de producción, y recién ahí corre el mecanismo del arranque.
 */
class TokenVersionColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:token_version_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
            exec("ALTER TABLE users DROP COLUMN token_version")
            exec(
                """INSERT INTO users (id, email, "name", password_hash)
                   VALUES ('usr-dueno', 'dueno@movi.test', 'Dueño', 'hash')""",
            )
            SchemaUtils.createMissingTablesAndColumns(Users)
        }
    }

    @Test
    fun `una cuenta que ya existia queda en la version 0`() {
        transaction {
            assertEquals(0, Users.selectAll().single()[Users.tokenVersion])
        }
    }
}
