package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.db.createSqlDriver
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Al cerrar sesión, la base del teléfono no se queda con la plata de quien se fue.**
 *
 * Hasta Ola 0 el logout limpiaba la memoria (cachés, la instantánea del Inicio) y dejaba el espejo
 * de SQLite entero: cuentas, movimientos, anulaciones y el `remote_cache` con créditos y
 * recurrentes. `deleteForUser` del caché existía y nadie lo llamaba.
 *
 * Se siembra con SQL crudo —como quedan las filas en un teléfono de verdad— a dos usuarios en la
 * misma base, y se mide que [LocalRepository.olvidarDatosLocales] se lleve todo lo de uno y nada
 * del otro.
 */
class OlvidarDatosLocalesTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: MoviDatabase
    private val a = "usr-a"
    private val b = "usr-b"

    @BeforeTest
    fun setup() {
        driver = createSqlDriver("olvidar.db")
        db = MoviDatabase(driver)
        for (uid in listOf(a, b)) {
            sql("INSERT INTO account(id, name, type, userId) VALUES ('cuenta-$uid', 'Ahorros', 'SAVINGS', '$uid')")
            sql(
                "INSERT INTO financial_event(id, accountId, type, amount, category, description, timestamp, userId) " +
                    "VALUES ('mov-$uid', 'cuenta-$uid', 'EXPENSE', 45000, 'Comida', 'Almuerzo', 1, '$uid')",
            )
            sql(
                "INSERT INTO financial_event(id, accountId, type, amount, category, description, timestamp, userId) " +
                    "VALUES ('anulado-$uid', 'cuenta-$uid', 'EXPENSE', 9000, 'Comida', 'Café', 2, '$uid')",
            )
            // Una anulación todavía sin subir: es la que el `SyncEngine` empujaría con el token
            // del siguiente que entre, porque `void_event.selectUnsynced` no filtra por usuario.
            sql("INSERT INTO void_event(id, originalEventId, timestamp) VALUES ('void-$uid', 'anulado-$uid', 3)")
            db.remoteCacheQueries.put("recurrentes", uid, "[{\"id\":\"rr-$uid\"}]", 4)
        }
    }

    private fun sql(s: String) { driver.execute(null, s, 0) }

    private fun repo() = LocalRepository(db = db, remote = NoOpRepository(), userId = { a })

    private fun anulacionesSinSubir() = db.voidEventQueries.selectUnsynced().executeAsList().map { it.id }

    @Test
    fun tras_olvidar_no_queda_ninguna_fila_del_usuario() {
        repo().olvidarDatosLocales(a)

        assertTrue(db.accountQueries.selectAll(a).executeAsList().isEmpty(), "quedaron cuentas")
        assertTrue(db.financialEventQueries.selectAll(a).executeAsList().isEmpty(), "quedaron movimientos")
        assertTrue("void-$a" !in anulacionesSinSubir(), "quedó su anulación sin subir")
        assertEquals(null, db.remoteCacheQueries.get("recurrentes", a).executeAsOneOrNull(), "quedó el caché")
    }

    @Test
    fun lo_de_otro_usuario_no_se_toca() {
        repo().olvidarDatosLocales(a)

        assertEquals(listOf("cuenta-$b"), db.accountQueries.selectAll(b).executeAsList().map { it.id })
        assertEquals(2, db.financialEventQueries.selectAll(b).executeAsList().size)
        assertEquals(listOf("void-$b"), anulacionesSinSubir())
        assertTrue(db.remoteCacheQueries.get("recurrentes", b).executeAsOneOrNull() != null)
    }

    @Test
    fun una_anulacion_huerfana_tambien_se_va() {
        sql("INSERT INTO void_event(id, originalEventId, timestamp) VALUES ('void-huerfano', 'no-esta', 5)")
        repo().olvidarDatosLocales(a)
        assertTrue("void-huerfano" !in anulacionesSinSubir())
    }

    @Test
    fun sin_usuario_no_se_borra_nada() {
        repo().olvidarDatosLocales("")
        assertEquals(1, db.accountQueries.selectAll(a).executeAsList().size)
        assertEquals(2, anulacionesSinSubir().size)
    }
}
