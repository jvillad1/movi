package com.jvillada.movi.server.sms

import com.jvillada.movi.shared.model.AVANCE_DE_TARJETA_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuentaDelDepositoPropio
import com.jvillada.movi.shared.model.destinoEnOtroBanco
import com.jvillada.movi.shared.model.esElPagoDeLaTarjetaNu
import com.jvillada.movi.shared.model.marcaDelRemitente
import com.jvillada.movi.shared.model.puntasDelPagoDeLaTarjetaNu

/**
 * # Vuelta 91 · lo que Movi propone cuando la plata va a otra cuenta del dueño
 *
 * Se aplica a la propuesta de `GET /api/sms/{id}/parse` (y de la bandeja), con las cuentas del dueño y
 * los otros avisos del mismo pago ([otrosDelPago]). Las reglas son de `:core`
 * (`ElTraspasoAOtroBanco.kt`); esto decide en qué orden y qué campo llena. **El server no escribe
 * nada**: propone, y el dueño confirma las dos patas en Reconciliar.
 *
 * 1. **El pago de la tarjeta Nu, avisado por Nu** («¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos
 *    tu pago por $X»): la tarjeta Nu como la deuda que baja ([ParsedSms.deudaSugeridaId]) y la cuenta
 *    Nu como de donde salió ([ParsedSms.cuentaSugeridaId]), cada una solo si es la única. Sin esto la
 *    app lo anotaba como un «pago de tarjeta» suelto DENTRO de la tarjeta, que le subía la deuda.
 * 2. **Un pago que salió hacia otro banco** («Pagaste $1,500,000.00 a NU Compania de Financiamiento
 *    desde tu producto 8133»), si nada lo dijo antes (el correo de PSE ya dice si es un depósito, una
 *    tarjeta o una cuota):
 *    - con el aviso de **ese pago de la tarjeta Nu** en el mismo pago: es el pago de la tarjeta;
 *    - con el **depósito que entró** en el mismo pago («Recibimos tu depósito…» de Nu): un traspaso a
 *      la cuenta que lo avisa;
 *    - si no, por el nombre del banco ([destinoEnOtroBanco]): un traspaso con el destino **a elegir**
 *      entre las suyas de ese banco ([ParsedSms.destinosPosibles], con tarjetas y créditos); solo los
 *      últimos cuatro escritos en el aviso lo dejan elegido. El nombre solo no basta.
 *
 * El lado que entra suelto (el depósito de Nu sin el aviso de salida) ya sale de `parseSms` como un
 * ingreso en «Traspaso»: la app pide de qué cuenta vino. Nunca un ingreso.
 */
fun conElTraspasoPropio(
    parsed: ParsedSms,
    sms: SmsMessage,
    otrosDelPago: List<SmsMessage>,
    cuentas: List<Account>,
): ParsedSms {
    if (esElPagoDeLaTarjetaNu(sms.bank, sms.text) && parsed.category == CARD_PAYMENT_CATEGORY) {
        val puntas = puntasDelPagoDeLaTarjetaNu(cuentas, parsed.currency)
        val desde = puntas.desde?.takeIf { parsed.cuentaSugeridaId == null }
        return parsed.copy(
            deudaSugeridaId = parsed.deudaSugeridaId ?: puntas.tarjeta?.id,
            cuentaSugeridaId = parsed.cuentaSugeridaId ?: desde?.id,
            cuentaSugeridaPor = if (desde != null) "La cuenta ${desde.name}: de ahí paga la app de Nu" else parsed.cuentaSugeridaPor,
        )
    }
    if (parsed.type != TransactionType.EXPENSE) return parsed
    if (parsed.traspasoHaciaId != null || parsed.deudaSugeridaId != null) return parsed
    if (parsed.category in LO_QUE_YA_DIJO_EL_AVISO) return parsed

    if (otrosDelPago.any { esElPagoDeLaTarjetaNu(it.bank, it.text) }) {
        val tarjeta = puntasDelPagoDeLaTarjetaNu(cuentas, parsed.currency).tarjeta
        return parsed.copy(category = CARD_PAYMENT_CATEGORY, deudaSugeridaId = tarjeta?.id, identificadorDelDestino = null, identificadorEsLlave = false)
    }
    val delDeposito = otrosDelPago.firstNotNullOfOrNull { cuentaDelDepositoPropio(it.bank, it.text, cuentas) }
    if (delDeposito != null) return comoTraspaso(parsed, delDeposito.id, listOf(delDeposito.id))

    // Con solo el nombre del banco, el destino queda a elegir (incluidos sus créditos: «Pagaste $X a
    // DAVIBANK» puede ser la cuota del hipotecario); lo llena solo un número de cuenta escrito en el aviso.
    val destino = destinoEnOtroBanco(parsed.merchant, cuentas, marcaDelRemitente(sms.bank, sms.text), sms.text) ?: return parsed
    return comoTraspaso(parsed, destino.haciaId, destino.posibles)
}

/** Lo que el aviso (o el correo de PSE del mismo pago) ya dijo que es: no se cambia por el nombre. */
private val LO_QUE_YA_DIJO_EL_AVISO = setOf(CARD_PAYMENT_CATEGORY, CUOTA_CATEGORY, AVANCE_DE_TARJETA_CATEGORY)

/**
 * Un traspaso: ni ingreso ni gasto, así que la memoria del dueño para ese nombre no habla (`aprendidoDe`
 * fuera) y la cuenta de destino no es «de otro» (sin identificador).
 */
private fun comoTraspaso(parsed: ParsedSms, haciaId: String?, posibles: List<String>): ParsedSms = parsed.copy(
    category = TRANSFER_CATEGORY,
    traspasoHaciaId = haciaId,
    destinosPosibles = posibles,
    identificadorDelDestino = null,
    identificadorEsLlave = false,
    aprendidoDe = null,
    porMarca = false,
)
