package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Account

/**
 * Imita "sin red" para [LocalRepository.createAccount]: cualquier intento de crear una cuenta
 * contra el server explota, como haría un `HttpClient` sin conectividad. Se usa tanto en
 * [LocalRepositoryTest] (camino offline de `createAccount`) como en `SyncEngineTest`
 * (`syncAccounts` no debe sellar una fila cuyo push falló).
 */
class FailingCreateAccountRepository : NoOpRepository() {
    override suspend fun createAccount(account: Account): Account =
        error("sin red: no se pudo crear la cuenta en el server")
}
