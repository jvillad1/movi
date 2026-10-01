package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.db.createSqlDriver
import com.jvillada.movi.shared.model.VoidEvent
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Al cerrar sesión, la base del teléfono suelta lo que el server ya tiene y guarda lo que no.**
 *
 * Hasta Ola 0 el logout dejaba el espejo de SQLite entero. Ahora [LocalRepository.olvidarDatosLocales]
 * borra los movimientos, cuentas y anulaciones **ya sellados** y todo el `remote_cache`; lo que no
 * subió (anotado sin señal, o pendiente porque la sesión se cerró sola tras tres 401) **se queda**,
 * porque no existe en ningún otro lado. Para que esperar sea seguro, ninguno de los tres
 * `selectUnsynced` le devuelve a otra persona lo pendiente de esta: el `SyncEngine` de quien entre
 * después nunca lo sube con su token.
 *
 * Se siembra con SQL crudo —como quedan las filas en un teléfono de verdad— a dos usuarios en la
 * misma base.
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
            // Sellado: el server ya lo tiene.
            sql("INSERT INTO account(id, name, type, userId, syncedAt) VALUES ('cuenta-$uid', 'Ahorros', 'SAVINGS', '$uid', 1)")
            evento("mov-$uid", "cuenta-$uid", uid, syncedAt = 1)
            evento("anulado-$uid", "cuenta-$uid", uid, syncedAt = 1)
            sql("INSERT INTO void_event(id, originalEventId, timestamp, syncedAt, userId) VALUES ('void-$uid', 'anulado-$uid', 3, 3, '$uid')")
            db.remoteCacheQueries.put("recurrentes", uid, "[{\"id\":\"rr-$uid\"}]", 4)

            // Pendiente: anotado sin señal. Una cuenta nueva con su movimiento, un movimiento en una
            // cuenta sellada, y la anulación sin subir de un movimiento sellado.
            sql("INSERT INTO account(id, name, type, userId) VALUES ('cuenta-nueva-$uid', 'Bolsillo', 'CASH', '$uid')")
            evento("pendiente-$uid", "cuenta-nueva-$uid", uid, syncedAt = null)
            sql("INSERT INTO account(id, name, type, userId, syncedAt) VALUES ('cuenta-con-pendiente-$uid', 'Nómina', 'SAVINGS', '$uid', 1)")
            evento("pendiente-2-$uid", "cuenta-con-pendiente-$uid", uid, syncedAt = null)
            evento("anulado-sin-subir-$uid", "cuenta-$uid", uid, syncedAt = 1)
            sql(
                "INSERT INTO void_event(id, originalEventId, timestamp, userId) " +
                    "VALUES ('void-pendiente-$uid', 'anulado-sin-subir-$uid', 5, '$uid')",
            )
            // Y un pendiente anulado antes de subir: su anulación quedó sellada (el 404 de «nunca
            // subió», ver SyncEngine.syncVoids). Borrarla resucitaría el movimiento.
            evento("pendiente-anulado-$uid", "cuenta-nueva-$uid", uid, syncedAt = null)
            sql(
                "INSERT INTO void_event(id, originalEventId, timestamp, syncedAt, userId) " +
                    "VALUES ('void-de-pendiente-$uid', 'pendiente-anulado-$uid', 6, 6, '$uid')",
            )
        }
    }

    private fun sql(s: String) { driver.execute(null, s, 0) }

    private fun evento(id: String, cuenta: String, uid: String, syncedAt: Long?) = sql(
        "INSERT INTO financial_event(id, accountId, type, amount, category, description, timestamp, userId, syncedAt) " +
            "VALUES ('$id', '$cuenta', 'EXPENSE', 45000, 'Comida', 'Almuerzo', 1, '$uid', ${syncedAt ?: "NULL"})",
    )

    private fun repo(uid: String = a) = LocalRepository(db = db, remote = NoOpRepository(), userId = { uid })

    private fun idsDeVoids(): Set<String> = buildSet {
        driver.executeQuery(null, "SELECT id FROM void_event", { c ->
            while (c.next().value) add(c.getString(0)!!)
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
    }

    private fun cuentas(uid: String) = db.accountQueries.selectAll(uid).executeAsList().map { it.id }.toSet()
    private fun movimientos(uid: String) = db.financialEventQueries.selectAll(uid).executeAsList().map { it.id }.toSet()

    @Test
    fun tras_olvidar_no_queda_nada_sellado_del_usuario() {
        repo().olvidarDatosLocales(a)

        assertTrue("cuenta-$a" !in cuentas(a), "quedó una cuenta sellada")
        assertTrue("mov-$a" !in movimientos(a) && "anulado-$a" !in movimientos(a), "quedaron movimientos sellados")
        assertTrue("void-$a" !in idsDeVoids(), "quedó una anulación sellada")
        assertEquals(null, db.remoteCacheQueries.get("recurrentes", a).executeAsOneOrNull(), "quedó el caché")
    }

    @Test
    fun lo_que_no_subio_se_queda_en_el_telefono() {
        repo().olvidarDatosLocales(a)

        assertEquals(
            setOf("pendiente-$a", "pendiente-2-$a", "pendiente-anulado-$a"),
            movimientos(a) - setOf("anulado-sin-subir-$a"),
            "un movimiento sin subir es plata que no existe en ningún otro lado",
        )
        // Las cuentas de las que cuelga algo pendiente se quedan, aunque estén selladas.
        assertEquals(setOf("cuenta-nueva-$a", "cuenta-con-pendiente-$a"), cuentas(a))
        assertTrue("void-pendiente-$a" in idsDeVoids(), "la anulación sin subir se quedó")
        assertTrue("void-de-pendiente-$a" in idsDeVoids(), "la anulación que esconde un pendiente se quedó")
    }

    @Test
    fun el_mismo_usuario_al_volver_sube_lo_suyo_como_siempre() {
        repo().olvidarDatosLocales(a)

        val pendientes = db.financialEventQueries.selectUnsynced(a).executeAsList().map { it.id }.toSet()
        assertEquals(setOf("pendiente-$a", "pendiente-2-$a"), pendientes, "el anulado antes de subir no se sube")
        assertEquals(listOf("void-pendiente-$a"), db.voidEventQueries.selectUnsynced(a).executeAsList().map { it.id })
        assertEquals(listOf("cuenta-nueva-$a"), db.accountQueries.selectUnsynced(a).executeAsList().map { it.id })
    }

    @Test
    fun los_selectores_de_pendientes_no_le_dan_a_B_nada_de_A() {
        repo().olvidarDatosLocales(a)

        val eventosDeB = db.financialEventQueries.selectUnsynced(b).executeAsList().map { it.id }
        val voidsDeB = db.voidEventQueries.selectUnsynced(b).executeAsList().map { it.id }
        val cuentasDeB = db.accountQueries.selectUnsynced(b).executeAsList().map { it.id }
        assertTrue((eventosDeB + voidsDeB + cuentasDeB).none { it.endsWith(a) }, "a B le llega lo pendiente de A")
        assertEquals(listOf("void-pendiente-$b"), voidsDeB)
    }

    /** El `SyncEngine` de B, con el token de B, no empuja la anulación pendiente de A. */
    @Test
    fun el_sync_de_B_no_sube_la_anulacion_de_A() = runBlocking {
        val anulados = mutableListOf<String>()
        val server = object : NoOpRepository() {
            override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
                anulados += id
                return VoidEvent(id = "v", originalEventId = id, reason = reason, timestamp = 0L)
            }
        }
        SyncEngine(db = db, remote = server, userId = { b }).syncVoids()

        assertEquals(listOf("anulado-sin-subir-$b"), anulados)
    }

    @Test
    fun lo_de_otro_usuario_no_se_toca() {
        val antes = Triple(cuentas(b), movimientos(b), db.remoteCacheQueries.get("recurrentes", b).executeAsOneOrNull())
        repo().olvidarDatosLocales(a)
        assertEquals(antes, Triple(cuentas(b), movimientos(b), db.remoteCacheQueries.get("recurrentes", b).executeAsOneOrNull()))
        assertTrue(idsDeVoids().containsAll(listOf("void-$b", "void-pendiente-$b", "void-de-pendiente-$b")))
    }

    @Test
    fun sin_usuario_no_se_borra_nada() {
        val antes = idsDeVoids()
        repo().olvidarDatosLocales("")
        assertEquals(3, cuentas(a).size)
        assertEquals(antes, idsDeVoids())
    }

    /**
     * La migración 15 le pone dueño a las anulaciones que ya estaban en el teléfono, por su
     * movimiento. La que apunta a un movimiento que no está queda sin dueño y no la sube nadie.
     */
    @Test
    fun la_migracion_le_pone_duenio_a_las_anulaciones_viejas() {
        val viejo = createSqlDriver("migracion.db")
        MoviDatabase(viejo)
        // La tabla como estaba en la versión 15, sin `userId`.
        viejo.execute(null, "DROP TABLE void_event", 0)
        viejo.execute(
            null,
            "CREATE TABLE void_event (id TEXT NOT NULL PRIMARY KEY, originalEventId TEXT NOT NULL, " +
                "reason TEXT, timestamp INTEGER NOT NULL, syncedAt INTEGER, origen TEXT)",
            0,
        )
        viejo.execute(
            null,
            "INSERT INTO financial_event(id, accountId, type, amount, category, description, timestamp, userId) " +
                "VALUES ('mov-viejo', 'c', 'EXPENSE', 1, 'x', 'x', 1, '$a')",
            0,
        )
        viejo.execute(null, "INSERT INTO void_event(id, originalEventId, timestamp) VALUES ('v-con-mov', 'mov-viejo', 1)", 0)
        viejo.execute(null, "INSERT INTO void_event(id, originalEventId, timestamp) VALUES ('v-huerfana', 'no-esta', 1)", 0)

        MoviDatabase.Schema.migrate(viejo, 15L, 16L)

        val migrada = MoviDatabase(viejo)
        assertEquals(listOf("v-con-mov"), migrada.voidEventQueries.selectUnsynced(a).executeAsList().map { it.id })
        assertTrue(migrada.voidEventQueries.selectUnsynced(b).executeAsList().isEmpty())
    }
}
