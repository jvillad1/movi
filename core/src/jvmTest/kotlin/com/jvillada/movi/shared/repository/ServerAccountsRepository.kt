package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Account

/**
 * Un "server" que sí tiene cuentas: las que le pasan por [cuentas], devueltas por
 * `getAccounts`/`getAccount` como haría `GET /api/accounts`.
 *
 * [NoOpRepository] devuelve una lista vacía, que sirve para los tests que solo miran el espejo
 * local pero no para los de esta rama: lo que hay que ejercitar es justamente el caso en que el
 * server conoce una cuenta que el teléfono nunca vio.
 *
 * Con [falla] en `true` imita "sin red": las dos lecturas explotan, igual que
 * [FailingCreateAccountRepository] hace con la escritura.
 */
class ServerAccountsRepository(
    var cuentas: List<Account> = emptyList(),
    var falla: Boolean = false,
) : NoOpRepository() {
    /** Cuántas veces se preguntó al server — para probar que la lectura no se repite de más. */
    var lecturas: Int = 0
        private set

    override suspend fun getAccounts(): List<Account> {
        lecturas++
        if (falla) error("sin red: no se pudo leer la lista de cuentas")
        return cuentas
    }

    override suspend fun getAccount(id: String): Account {
        if (falla) error("sin red: no se pudo leer la cuenta")
        return cuentas.firstOrNull { it.id == id } ?: error("404: la cuenta no existe")
    }
}
