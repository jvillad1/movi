package com.jvillada.movi.server.sms

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.cuentaPropiaDeLaQueVinoElIngreso
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/**
 * **Un ingreso que vino de una cuenta del dueño** (hueco de #439): «Recibiste $500.000 de tu cuenta
 * \*9586 en tu cuenta \*8133», con una «Fiducuenta 9586» entre sus cuentas, se propone como traspaso.
 * La regla es de `:core` ([cuentaPropiaDeLaQueVinoElIngreso]); esto la aplica a la propuesta de
 * `GET /api/sms/{id}/parse`:
 *
 * - [ParsedSms.traspasoDesdeId] con esa cuenta: la app arma las dos patas (sale de ella, entra a la
 *   del aviso) en vez de un ingreso suelto. **El server no escribe nada**: confirma el dueño.
 * - El nombre deja de ser lo que el parser recortó del texto («tu cuenta \*9586 en tu cuenta…») y
 *   pasa a decir de dónde vino: «Desde Fiducuenta 9586».
 * - Y no se ofrece «¿De quién es esta cuenta?»: el otro lado es él mismo.
 *
 * Un origen que no es suyo —o un aviso que no nombra ninguno— vuelve tal cual: sigue siendo un
 * ingreso.
 */
fun conElOrigenPropioDelIngreso(parsed: ParsedSms, texto: String, cuentas: List<Account>): ParsedSms {
    val origen = cuentaPropiaDeLaQueVinoElIngreso(parsed.type, texto, cuentas) ?: return parsed
    return parsed.copy(
        traspasoDesdeId = origen.id,
        merchant = "Desde ${origen.name}",
        identificadorDelDestino = null,
        identificadorEsLlave = false,
    )
}

/** [conElOrigenPropioDelIngreso] con las cuentas de [uid]. Dentro de una transacción. */
fun Transaction.conElOrigenPropioDelIngresoDe(uid: String, parsed: ParsedSms, texto: String): ParsedSms =
    conElOrigenPropioDelIngreso(parsed, texto, Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() })
