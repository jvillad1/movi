package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.shared.model.Captura
import com.jvillada.movi.shared.model.DIAS_PARA_BANCO_MUDO_POR_DEFECTO
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.origenesMudos
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction

/**
 * **Los orígenes de captura de [uid] que se callaron** («banco mudo», Ola 2): la regla de `:core`
 * ([origenesMudos]) sobre lo que llegó a su bandeja, con los días que él eligió.
 *
 * - Cuenta **todo lo que llegó**, en cualquier estado: la pregunta es si el banco sigue mandando, no
 *   qué hizo el dueño con cada mensaje.
 * - **Sin los comprobantes** que el dueño compartió: esos no los manda el banco.
 * - Una hora que no se entiende no es una captura: `momentoDelSms` la fecharía «ahora», y una fila
 *   rota haría ver viva una captura muerta.
 */
fun Transaction.origenesMudosDe(uid: String, ahora: Long): List<OrigenMudo> {
    val dias = Users.select(Users.diasParaBancoMudo)
        .where { Users.id eq uid }
        .firstOrNull()?.get(Users.diasParaBancoMudo) ?: DIAS_PARA_BANCO_MUDO_POR_DEFECTO
    if (dias <= 0) return emptyList()
    val capturas = SmsMessages.select(SmsMessages.id, SmsMessages.bank, SmsMessages.time)
        .where { SmsMessages.userId eq uid }
        .mapNotNull { fila ->
            if (esIdDeComprobante(fila[SmsMessages.id])) return@mapNotNull null
            val momento = momentoDelSms(fila[SmsMessages.time], ahora = Long.MAX_VALUE)
            if (momento == Long.MAX_VALUE) return@mapNotNull null
            Captura(fila[SmsMessages.bank], momento)
        }
    return origenesMudos(capturas, ahora, dias)
}
