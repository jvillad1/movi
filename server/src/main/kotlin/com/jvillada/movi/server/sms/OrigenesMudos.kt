package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.Captura
import com.jvillada.movi.shared.model.DIAS_PARA_BANCO_MUDO_POR_DEFECTO
import com.jvillada.movi.shared.model.MOTIVO_DEVUELTO
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.motivoDeApartado
import com.jvillada.movi.shared.model.origenesMudos
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction

/**
 * **Los orígenes de captura de [uid] que se callaron** («banco mudo», Ola 2): la regla de `:core`
 * ([origenesMudos]) sobre lo que llegó a su bandeja, con los días que él eligió.
 *
 * - Cuenta lo que llegó **en cualquier estado**: la pregunta es si el banco sigue mandando, no qué
 *   hizo el dueño con cada mensaje.
 * - **Vuelta 90 · pero solo los movimientos prueban que el origen vive** ([queTraeLaCaptura]): un
 *   rechazo, una promoción o un código no. Glim mandaba rechazos y se veía vivo sin avisar compras.
 * - **Sin los comprobantes** que el dueño compartió: esos no los manda el banco.
 * - Una hora que no se entiende no es una captura: `momentoDelSms` la fecharía «ahora», y una fila
 *   rota haría ver viva una captura muerta.
 */
fun Transaction.origenesMudosDe(uid: String, ahora: Long): List<OrigenMudo> {
    val dias = Users.select(Users.diasParaBancoMudo)
        .where { Users.id eq uid }
        .firstOrNull()?.get(Users.diasParaBancoMudo) ?: DIAS_PARA_BANCO_MUDO_POR_DEFECTO
    if (dias <= 0) return emptyList()
    val desdeQueSeLee = ahora - DIAS_QUE_SE_LEEN_CON_EL_PARSER * 86_400_000L
    val capturas = SmsMessages.select(SmsMessages.id, SmsMessages.bank, SmsMessages.time, SmsMessages.text, SmsMessages.motivoApartado)
        .where { SmsMessages.userId eq uid }
        .mapNotNull { fila ->
            if (esIdDeComprobante(fila[SmsMessages.id])) return@mapNotNull null
            val momento = momentoDelSms(fila[SmsMessages.time], ahora = Long.MAX_VALUE)
            if (momento == Long.MAX_VALUE) return@mapNotNull null
            val bank = fila[SmsMessages.bank]
            Captura(
                origen = bank,
                momento = momento,
                noEsMovimiento = queTraeLaCaptura(
                    texto = fila[SmsMessages.text],
                    origen = bank,
                    motivoGuardado = fila[SmsMessages.motivoApartado],
                    conElParser = momento >= desdeQueSeLee,
                ),
            )
        }
    return origenesMudos(capturas, ahora, dias)
}

/**
 * Hasta cuántos días atrás se lee cada aviso con el parser para saber si es un movimiento. Más atrás
 * solo vale el motivo guardado al llegar (`motivo_apartado`): el resumen del Inicio pide esto en cada
 * carga, y la bandeja entera de años no se vuelve a leer en cada una. 120 días cubren la ventana de
 * regularidad (30) más un silencio de tres meses; un origen callado desde antes ya se habría avisado.
 * Elegido, no medido.
 */
internal const val DIAS_QUE_SE_LEEN_CON_EL_PARSER: Int = 120

/**
 * **¿Esta captura es un movimiento?** `null` si lo es (o podría serlo); si no, qué es.
 *
 * 1. «Era un movimiento» del dueño ([MOTIVO_DEVUELTO]) manda: es movimiento.
 * 2. El motivo con que Movi la apartó al llegar (`motivo_apartado`).
 * 3. Si se lee con el parser ([conElParser]): lo que reconoce [motivoParaApartar] (rechazo, promoción,
 *    código, seguridad, confirmación de reenvío…), y si `parseSms` no saca un monto, «aviso sin monto».
 *
 * Ante la duda, movimiento: la misma regla de oro que `queEsEsteMensaje`. Un origen que se ve vivo de
 * más es el error de antes; uno que se ve mudo sin estarlo sería una alarma falsa.
 */
internal fun queTraeLaCaptura(texto: String, origen: String, motivoGuardado: String?, conElParser: Boolean): MotivoDeApartado? {
    if (motivoGuardado == MOTIVO_DEVUELTO) return null
    motivoDeApartado(motivoGuardado)?.let { return it }
    if (!conElParser) return null
    motivoParaApartar(texto, origen)?.let { return it }
    val leido = parseSms(texto, origen)
    return if (leido == null || leido.amount <= 0.0) MotivoDeApartado.SIN_MONTO else null
}
