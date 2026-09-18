package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **La mitad cliente de la carrera por el nombre de una cuenta.**
 *
 * El `POST /api/accounts` es un upsert por id y `SyncEngine.syncAccounts` reenvía cada 30 segundos
 * toda cuenta que el teléfono no logró sellar. El server ya sabe hacer perder al reenvío contra una
 * edición más nueva (`pisaElReenvio`), pero solo puede hacerlo con la edad que el teléfono le
 * mande: sin sello, una corrección hecha acá sale diciendo «yo no edité nada» y la pierde contra la
 * copia del server — el mismo defecto al revés, esta vez borrando lo que el dueño escribió en el
 * teléfono.
 *
 * Ver `Account.lastEditedAt`.
 */
class ElNombreDeLaCuentaNoSePisaTest {

    private val testUserId = "user-nombre-cuenta"

    /** Un "server" que renombra de verdad, para el caso de la cuenta ya sincronizada. */
    private class ServerQueRenombra(cuentas: List<Account>) : ServerAccountsRepository(cuentas) {
        override suspend fun renameAccount(id: String, name: String): Account {
            val renombrada = cuentas.first { it.id == id }.copy(name = name, lastEditedAt = 1_000L)
            cuentas = cuentas.map { if (it.id == id) renombrada else it }
            return renombrada
        }
    }

    @Test
    fun crear_una_cuenta_sin_red_no_la_deja_sellada_como_editada() = runBlocking {
        val db = createDatabase("nombre-cuenta.db")
        val repo = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })

        repo.createAccount(Account("acc-nueva", "Libranza 4817", AccountType.LOAN, 0L))

        assertNull(
            db.accountQueries.selectById("acc-nueva").executeAsOne().lastEditedAt,
            "crear una cuenta no es editarla: no hay ninguna versión anterior a la que ganarle",
        )
    }

    @Test
    fun renombrarla_sin_red_se_resuelve_local_y_sella_la_edad() = runBlocking<Unit> {
        val db = createDatabase("nombre-cuenta.db")
        // Sin red: la cuenta queda local y pendiente, y el server ni siquiera la conoce.
        val repo = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        repo.createAccount(Account("acc-libranza", "Libranza 4817", AccountType.LOAN, 0L))

        val renombrada = repo.renameAccount("acc-libranza", "Libranza 4818")

        assertEquals("Libranza 4818", renombrada.name, "sin esta rama el server contestaba 404 y no había forma")
        val fila = db.accountQueries.selectById("acc-libranza").executeAsOne()
        assertEquals("Libranza 4818", fila.name)
        assertNotNull(fila.lastEditedAt, "una corrección que no sella pierde contra la copia del server")
        assertNull(fila.syncedAt, "y sigue pendiente: la va a empujar el SyncEngine")
    }

    /**
     * El caso del enunciado, de punta a punta: el renombre hecho sin señal viaja al server **con su
     * edad**, que es lo único que le permite ganar la discusión allá.
     */
    @Test
    fun el_renombre_sin_senal_sube_con_su_edad() = runBlocking<Unit> {
        val db = createDatabase("nombre-cuenta.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-libranza", "Libranza 4817", AccountType.LOAN, 0L))
        local.renameAccount("acc-libranza", "Libranza 4818")

        val remote = CuentasEmpujadas()
        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncAccounts()

        val empujada = remote.empujadas.single()
        assertEquals("Libranza 4818", empujada.name)
        assertNotNull(empujada.lastEditedAt, "sin la edad, el server la trata como «no editada» y la hace perder")
    }

    /** Y una cuenta que nadie tocó sube con `null`, que es lo que la hace perder contra la web. */
    @Test
    fun una_cuenta_sin_editar_sube_sin_edad() = runBlocking {
        val db = createDatabase("nombre-cuenta.db")
        val local = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { testUserId })
        local.createAccount(Account("acc-nequi", "Nequi", AccountType.SAVINGS, 0L))

        val remote = CuentasEmpujadas()
        SyncEngine(db = db, remote = remote, userId = { testUserId }).syncAccounts()

        assertNull(
            remote.empujadas.single().lastEditedAt,
            "«esta copia es la original»: si el server tiene una edición, es posterior y gana",
        )
    }

    /**
     * La cuenta que el server YA conoce no cambia de camino: manda el server y la fila local se
     * pisa con lo que contestó — incluida la edad que él selló. Sin eso, el espejo local diría
     * «nunca editada» y la comparación del próximo reenvío arrancaría de cero.
     */
    @Test
    fun una_cuenta_ya_sincronizada_la_renombra_el_server() = runBlocking {
        val db = createDatabase("nombre-cuenta.db")
        val skandia = Account("acc-skandia", "Skandia", AccountType.INVESTMENT, 0L)
        val server = ServerQueRenombra(listOf(skandia))
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })
        repo.getAccounts()  // la espeja, sellada

        repo.renameAccount("acc-skandia", "Skandia pensión voluntaria")

        val fila = db.accountQueries.selectById("acc-skandia").executeAsOne()
        assertEquals("Skandia pensión voluntaria", fila.name)
        assertEquals(1_000L, fila.lastEditedAt, "la edad que selló el server viaja al espejo local")
    }

    /** Anota qué cuentas empujó el ciclo de sincronización, tal como salieron. */
    private class CuentasEmpujadas : NoOpRepository() {
        val empujadas = mutableListOf<Account>()
        override suspend fun createAccount(account: Account): Account {
            empujadas += account
            return account
        }
    }
}
