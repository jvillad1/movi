package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.patrimonioDe
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Un bien tiene que sobrevivir a la fila local**, por el mismo motivo que la condición de uso
 * (ver `CondicionSobreviveSinRedTest`): `getAccounts` contesta con lo local sin red y también
 * cuando la red tarda más que `PRESUPUESTO_DE_RED_MS`. Si la fila no supiera que la casa es un
 * bien, volvería como una inversión en $0 y el patrimonio del Inicio saltaría $1.412M según la
 * señal del bus.
 */
class BienSobreviveSinRedTest {

    private val testUserId = "user-bien"

    private val casa = Account(
        id = "acc-casa",
        name = "Casa Almendros",
        type = AccountType.INVESTMENT,
        balance = 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    )

    @Test
    fun sin_red_la_casa_sigue_siendo_un_bien_con_su_valor() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(casa))
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })

        repo.getAccounts()
        server.falla = true

        val local = repo.getAccounts().single()
        assertEquals(casa.bien, local.bien)
        assertEquals(1_411_903_920L, patrimonioDe(listOf(local)).bienes)
        assertEquals(0L, patrimonioDe(listOf(local)).tuPlata)
    }

    @Test
    fun actualizar_el_avaluo_se_espeja_en_la_fila_local() = runBlocking {
        val db = createDatabase("test.db")
        val server = ServerAccountsRepository(listOf(casa))
        val repo = LocalRepository(db = db, remote = server, userId = { testUserId })
        repo.getAccounts()

        val nuevo = casa.bien!!.copy(valor = 1_500_000_000L, valorAl = "2027-08-28")
        repo.updateBien("acc-casa", nuevo)
        server.falla = true

        assertEquals(nuevo, repo.getAccounts().single().bien)
    }

    @Test
    fun un_bien_creado_sin_red_sube_con_el_bien_adentro() = runBlocking {
        // Sin red la fila queda sin sellar y la empuja el SyncEngine. Si el reenvío saliera sin el
        // bien, la casa llegaría al server como una inversión en $0 y el patrimonio la perdería.
        val db = createDatabase("test.db")
        val subidas = mutableListOf<Account>()
        val sinRed = object : ServerAccountsRepository(emptyList(), falla = true) {
            override suspend fun createAccount(account: Account): Account = error("sin red: el POST no llegó")
        }
        val repo = LocalRepository(db = db, remote = sinRed, userId = { testUserId })
        repo.createAccount(casa)

        val conRed = object : ServerAccountsRepository(emptyList()) {
            override suspend fun createAccount(account: Account): Account = account.also { subidas += it }
        }
        SyncEngine(db = db, remote = conRed, userId = { testUserId }).syncAccounts()

        assertEquals(casa.bien, subidas.single().bien)
    }
}
