package com.jvillada.movi.server.db

import com.jvillada.movi.server.cuenta.TABLAS_DEL_USUARIO
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * # Las tablas de la observabilidad del servidor nacen en el arranque (vuelta 102)
 *
 * Son seis tablas **nuevas**: entran por el `SchemaUtils.create` de `crearYActualizarSchema` (no por
 * `createMissingTablesAndColumns`, que es para columnas nuevas de tablas viejas), así que sobre una base
 * de producción sin ellas el arranque las crea enteras. Y **ninguna tiene `user_id`**: no guardan nada de
 * nadie, por eso no están en `TABLAS_DEL_USUARIO` y borrar una cuenta no tiene nada que borrar acá.
 */
class TablasDeObservabilidadTest {

    @BeforeTest
    fun conectar() {
        Database.connect(
            url = "jdbc:h2:mem:tablas_de_observabilidad;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction { exec("DROP ALL OBJECTS") }
    }

    private fun tablas(): Set<String> = transaction {
        val encontradas = mutableSetOf<String>()
        exec("SELECT LOWER(table_name) FROM information_schema.tables WHERE table_schema = 'public'") { rs ->
            while (rs.next()) encontradas += rs.getString(1)
        }
        encontradas
    }

    @Test
    fun `el arranque crea las seis tablas sobre una base que no las tiene`() {
        DatabaseFactory.crearYActualizarSchema()
        val hay = tablas()
        TABLAS_DE_OBSERVABILIDAD.forEach { assertTrue(it.tableName in hay, "falta ${it.tableName} después del arranque") }
        // Y un segundo arranque, con las tablas ya creadas, no falla.
        DatabaseFactory.crearYActualizarSchema()
    }

    @Test
    fun `ninguna tiene user_id ni esta en las tablas del usuario`() {
        TABLAS_DE_OBSERVABILIDAD.forEach { t ->
            assertTrue(t.columns.none { it.name == "user_id" || it.name == "ip" }, "${t.tableName} no puede guardar quién")
            assertTrue(TABLAS_DEL_USUARIO.none { it.tabla == t }, "${t.tableName} no es una tabla del usuario")
        }
    }
}
