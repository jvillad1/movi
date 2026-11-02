package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.EditarCuentaRequest
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SALDO_DE_UNA_CUENTA_SIN_SUBIR
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Vuelta 98 · «Editar la cuenta» en el teléfono
 *
 * El patrón de las otras escrituras de cuentas (`renameAccount`, `updateAccountCondition`): una
 * cuenta que el server ya tiene la edita él y se espeja lo que contesta —el ajuste incluido, como
 * movimiento ya sincronizado—; una que todavía no subió se edita acá, sellada, salvo el saldo, que
 * sale del server.
 */
class EditarLaCuentaSinRedTest {

    private val uid = "user-editar-sin-red"

    @Test
    fun una_cuenta_sin_subir_se_edita_aca_menos_el_saldo() = runBlocking<Unit> {
        val db = createDatabase("editar-sin-red.db")
        val repo = LocalRepository(db = db, remote = FailingCreateAccountRepository(), userId = { uid })
        repo.createAccount(Account("acc-papa", "Bancolombia Ahorros", AccountType.SAVINGS, 0L))

        val editada = repo.editarCuenta("acc-papa", EditarCuentaRequest("Ahorros del papá", type = AccountType.CHECKING))

        assertEquals("Ahorros del papá", editada.account.name)
        val fila = db.accountQueries.selectById("acc-papa").executeAsOne()
        assertEquals("CHECKING", fila.type)
        assertNotNull(fila.lastEditedAt, "sellada, para que el reenvío no pierda contra nada")
        assertNull(fila.syncedAt, "sigue pendiente: la empuja el SyncEngine")

        val conSaldo = assertFailsWith<ApiException> {
            repo.editarCuenta("acc-papa", EditarCuentaRequest("Otro nombre", saldoDeHoy = 5_000L))
        }
        assertEquals(SALDO_DE_UNA_CUENTA_SIN_SUBIR, conSaldo.serverMessage)
        assertEquals("Ahorros del papá", db.accountQueries.selectById("acc-papa").executeAsOne().name, "nada a medias")

        val aDeuda = assertFailsWith<ApiException> {
            repo.editarCuenta("acc-papa", EditarCuentaRequest("Ahorros del papá", type = AccountType.LOAN))
        }
        assertEquals(422, aDeuda.status)
    }

    @Test
    fun una_cuenta_del_server_espeja_la_cuenta_y_el_ajuste() = runBlocking<Unit> {
        val db = createDatabase("editar-sin-red.db")
        val ahorros = Account("acc-s", "Davibank", AccountType.SAVINGS, 1_000_000L)
        val ajuste = FinancialEvent(
            id = "ev-ajuste-1", accountId = "acc-s", type = TransactionType.INCOME, amount = 250_000L,
            category = ADJUSTMENT_CATEGORY, description = "Ajuste al saldo del banco", timestamp = 5_000L,
        )
        val server = object : ServerAccountsRepository(listOf(ahorros)) {
            override suspend fun editarCuenta(id: String, pedido: EditarCuentaRequest): AdjustAccountBalanceResponse {
                val editada = cuentas.first { it.id == id }.copy(name = pedido.name, balance = 1_250_000L, lastEditedAt = 9L)
                cuentas = cuentas.map { if (it.id == id) editada else it }
                return AdjustAccountBalanceResponse(editada, ajuste)
            }
        }
        val repo = LocalRepository(db = db, remote = server, userId = { uid })
        repo.getAccounts()

        repo.editarCuenta("acc-s", EditarCuentaRequest("Davibank Ahorros", saldoDeHoy = 1_250_000L))

        val fila = db.accountQueries.selectById("acc-s").executeAsOne()
        assertEquals("Davibank Ahorros", fila.name)
        val local = db.financialEventQueries.selectById("ev-ajuste-1", uid).executeAsOneOrNull()
        assertNotNull(local, "el ajuste se ve sin red, con el id del server")
        assertNotNull(local.syncedAt, "ya sincronizado: el SyncEngine no lo vuelve a subir")
    }
}
