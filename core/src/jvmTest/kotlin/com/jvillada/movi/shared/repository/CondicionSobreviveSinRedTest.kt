package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **La condición tiene que sobrevivir a la fila local, o la cifra grande del Inicio oscila.**
 *
 * `heroBalance` saca de «Tu plata» las cuentas condicionadas —los $106.000.000 de la pensión
 * voluntaria del dueño—, pero la tabla `account` de SQLDelight no tenía la columna: al reconstruir
 * el `Account` desde la fila local, `condicionadaA` volvía `null` y esa plata volvía a sumar.
 *
 * **Y no se alcanzaba solo en modo avión.** `getAccounts` corta la espera del server en
 * `PRESUPUESTO_DE_RED_MS` (5 s) y contesta con lo local: con red mala —el modo normal en el bus—
 * la misma cifra saltaba entre $31,6M y $137,6M sin que nada cambiara.
 */
class CondicionSobreviveSinRedTest {

    private val testUserId = "user-condicion"

    private val skandia = Account(
        id = "acc-skandia",
        name = "Skandia pensión voluntaria",
        type = AccountType.INVESTMENT,
        balance = 106_000_000L,
        condicionadaA = "Vivienda",
    )

    @Test
    fun sin_red_la_cuenta_sigue_estando_condicionada() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(skandia))
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })

        repo.getAccounts()          // espeja la cuenta del server en la fila local
        server.falla = true         // y ahora no hay red

        val local = repo.getAccounts().single()
        assertEquals("Vivienda", local.condicionadaA, "sin esto el Inicio volvía a sumar los $106M")
        assertEquals(106_000_000L, local.balance)
    }

    @Test
    fun con_red_lenta_tambien() = runBlocking {
        // El otro camino, el que no es «modo avión»: el `withTimeoutOrNull` de getAccounts
        // devuelve lo local cuando el server tarda más que el presupuesto de red.
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(skandia))
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })
        repo.getAccounts()

        server.demoraMs = 30_000L
        val local = repo.getAccounts().single()

        assertEquals("Vivienda", local.condicionadaA)
    }

    @Test
    fun el_detalle_de_la_cuenta_sin_red_tambien_la_conserva() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(skandia))
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })
        repo.getAccount("acc-skandia")
        server.falla = true

        assertEquals("Vivienda", repo.getAccount("acc-skandia").condicionadaA)
    }

    @Test
    fun marcar_la_condicion_la_escribe_en_la_fila_local() = runBlocking {
        // El camino de escritura completo del lado del cliente: se marca contra el server y la
        // fila local se pisa con lo que contestó. Sin ese espejo, la primera lectura sin red
        // deshacía a la vista lo que el dueño acababa de guardar.
        val db = createDatabase("test.db")
        val sinCondicion = skandia.copy(condicionadaA = null)
        val server = object : ServerAccountsRepository(listOf(sinCondicion)) {
            override suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account {
                val marcada = cuentas.first { it.id == id }.copy(condicionadaA = condicionadaA)
                cuentas = cuentas.map { if (it.id == id) marcada else it }
                return marcada
            }
        }
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })
        repo.getAccounts()
        assertNull(repo.getAccounts().single().condicionadaA)

        repo.updateAccountCondition("acc-skandia", "Vivienda")
        server.falla = true

        assertEquals("Vivienda", repo.getAccounts().single().condicionadaA)
    }

    @Test
    fun quitarla_tambien_viaja_a_la_fila_local() = runBlocking {
        val db = createDatabase("test.db")
        val server = object : ServerAccountsRepository(listOf(skandia)) {
            override suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account {
                val marcada = cuentas.first { it.id == id }.copy(condicionadaA = condicionadaA)
                cuentas = cuentas.map { if (it.id == id) marcada else it }
                return marcada
            }
        }
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })
        repo.getAccounts()

        repo.updateAccountCondition("acc-skandia", null)
        server.falla = true

        assertNull(repo.getAccounts().single().condicionadaA, "esa plata vuelve a «Tu plata»")
    }

    @Test
    fun una_cuenta_creada_offline_conserva_su_condicion() = runBlocking {
        // `createAccount` sin red escribe la fila local sin sellar, para que el SyncEngine la
        // empuje después. La condición tiene que viajar en ESA fila también.
        val db = createDatabase("test.db")
        val server = object : ServerAccountsRepository(emptyList(), falla = true) {
            override suspend fun createAccount(account: Account): Account = error("sin red: el POST no llegó")
        }
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })

        repo.createAccount(Account("acc-afc", "AFC", AccountType.SAVINGS, 3_000_000L, condicionadaA = "Vivienda"))

        assertEquals("Vivienda", repo.getAccounts().single().condicionadaA)
    }
}
