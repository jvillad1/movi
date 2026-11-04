package com.jvillada.movi.server.db

import com.jvillada.movi.server.conectarBaseDePrueba
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vuelta 105 · **la clave de los cierres crece de `varchar(7)` a `varchar(10)`** para que quepa el id de
 * una quincena («2026-10-15»). Es DDL sobre tablas que existen en producción con filas, y las migraciones
 * corren dentro de la transacción de arranque: un ALTER que falle deja el server sin arrancar. Por eso
 * se prueba como está en producción —las tablas viejas, con filas— y que la segunda corrida no haga nada.
 */
class LaClaveDeLosCierresTest {

    @BeforeTest
    fun setUp() {
        conectarBaseDePrueba(
            url = "jdbc:h2:mem:la_clave_de_los_cierres;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(CierresEnviados, CierresGuardados, CierresDescartados)
            // Como nacieron en producción: la clave en 7.
            exec("CREATE TABLE cierres_enviados (user_id VARCHAR(50) NOT NULL, periodo VARCHAR(7) NOT NULL, enviado_en BIGINT NOT NULL, PRIMARY KEY (user_id, periodo))")
            exec("CREATE TABLE cierres_guardados (user_id VARCHAR(50) NOT NULL, periodo VARCHAR(7) NOT NULL, datos TEXT NOT NULL, guardado_en BIGINT NOT NULL, PRIMARY KEY (user_id, periodo))")
            exec("CREATE TABLE cierres_descartados (user_id VARCHAR(50) NOT NULL, periodo VARCHAR(7) NOT NULL, descartado_en BIGINT NOT NULL, PRIMARY KEY (user_id, periodo))")
            exec("INSERT INTO cierres_enviados VALUES ('u1', '2026-10', 1)")
            exec("INSERT INTO cierres_descartados VALUES ('u1', '2026-09', 1)")
        }
    }

    @Test
    fun `la migracion agranda las tres claves, conserva las filas y la segunda vez no hace nada`() {
        transaction {
            assertEquals(7, with(Migrations) { largoDeLaColumna("cierres_enviados", "periodo") })
            val tocadas = with(Migrations) { agrandarLaClaveDeLosCierres() }
            assertEquals(Migrations.TABLAS_CON_ID_DE_PERIODO, tocadas)
            for (tabla in Migrations.TABLAS_CON_ID_DE_PERIODO) {
                assertEquals(LARGO_DEL_ID_DEL_PERIODO, with(Migrations) { largoDeLaColumna(tabla, "periodo") }, tabla)
            }
            assertEquals(emptyList(), with(Migrations) { agrandarLaClaveDeLosCierres() }, "idempotente")
            // Las filas viejas siguen ahí, y ahora cabe una quincena.
            assertEquals("2026-10", CierresEnviados.selectAll().single()[CierresEnviados.periodo])
            CierresEnviados.insert {
                it[userId] = "u1"
                it[periodo] = "2026-10-15"
                it[enviadoEn] = 2
            }
            assertTrue(CierresEnviados.selectAll().any { it[CierresEnviados.periodo] == "2026-10-15" })
        }
    }

    @Test
    fun `una base nueva ya nace con 10 y la migracion no la toca`() {
        transaction {
            SchemaUtils.drop(CierresEnviados, CierresGuardados, CierresDescartados)
            SchemaUtils.create(CierresEnviados, CierresGuardados, CierresDescartados)
            assertEquals(emptyList(), with(Migrations) { agrandarLaClaveDeLosCierres() })
        }
    }
}
