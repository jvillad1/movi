package com.jvillada.movi.ui.sms

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.newId

/*
 * # Anotar el movimiento de un aviso: un solo camino
 *
 * Esto vivía adentro de `confirm()` de Reconciliar. La bandeja que se vacía sola confirma desde la
 * fila y en lote, y el brief lo pidió explícito: **exactamente el mismo camino que Reconciliar —mismo
 * endpoint, mismas patas y misma idempotencia—, nada de un segundo camino.** Por eso salió a un lugar
 * propio y lo llaman las tres: Reconciliar, la fila y el lote.
 */

/**
 * **Cuándo pasó el movimiento de un aviso**: cuando llegó —de un pago avisado varias veces, el primer
 * aviso— y, si el aviso dice otro día (el correo de PSE que llegó tarde), ese día. Ver [momentoDelSms]
 * y [momentoConLaFechaDelAviso].
 */
internal fun momentoDelMovimientoDelAviso(sms: SmsMessage?, pago: GrupoDeAvisos?, leido: ParsedSms, ahora: Long): Long =
    momentoConLaFechaDelAviso(
        momentoDelSms(
            pago?.miembros?.minByOrNull { momentoDelSms(it.time, Long.MAX_VALUE) }?.time ?: sms?.time.orEmpty(),
            ahora = ahora,
        ),
        leido.fecha,
    )

/**
 * **El movimiento suelto que nace de confirmar un aviso**, como lo arma Reconciliar: el monto, la
 * moneda y el tipo del banco; la cuenta, la categoría y el comercio que quedaron; el texto del aviso
 * del que sale la propuesta en `raw_payload` (ver [movimientoConfirmadoDelSms]).
 */
internal fun movimientoDelAviso(
    leido: ParsedSms,
    cuentaId: String,
    categoria: String,
    comercio: String?,
    momento: Long,
    textoDelAviso: String,
    esComprobante: Boolean,
    id: String = newId("ev"),
): FinancialEvent = movimientoConfirmadoDelSms(
    // Mismo motivo que en QuickAddScreen — ver newId().
    id = id,
    cuentaId = cuentaId,
    leido = leido.copy(merchant = comercio ?: leido.merchant),
    categoria = categoria,
    momento = momento,
    textoDelSms = textoDelAviso,
    origen = if (esComprobante) EventSource.OCR else EventSource.SMS,
)

/**
 * **Anota [evento] y confirma el aviso con él** —el camino de un movimiento suelto, el de siempre de
 * Reconciliar—:
 *
 * - **Un pago avisado varias veces** ([pago]): `POST /api/sms/grupo/{id}/confirmar` con el movimiento.
 *   Una transacción del server crea el movimiento y cierra todos los avisos; si ya estaba anotado (un
 *   doble toque, otro teléfono) no crea otro.
 * - **Un aviso suelto**: `POST /api/events` y después `POST /api/sms/{id}/confirm?eventoId=` (o el de
 *   comprobantes, que además cuelga el papel de la cuenta). El server escribe el movimiento del aviso
 *   una sola vez: un segundo confirmar no lo mueve.
 *
 * [alCrearElMovimiento] avisa que el movimiento suelto ya se creó, por si marcar el aviso falla
 * después: quien llama vuelve a revisar «¿Ya lo anotaste?», donde aparece el recién creado con «Es
 * este», y así un segundo intento no crea otro en silencio.
 */
internal suspend fun anotarElMovimientoDelAviso(
    smsId: String,
    pago: GrupoDeAvisos?,
    evento: FinancialEvent,
    esComprobante: Boolean,
    alCrearElMovimiento: () -> Unit = {},
) {
    if (pago != null) {
        // Un solo movimiento para todos los avisos, en una transacción del server.
        Repositories.wallets.confirmarElMismoPago(
            pago.grupoId, ConfirmarElMismoPago(pago.miembros.map { it.id }, evento = evento),
        )
        UsedCategoriesCache.record(evento.category, evento.type)
        return
    }
    Repositories.wallets.postEvent(evento)
    alCrearElMovimiento()
    // Una categoría creada acá tiene que ser conocida en el siguiente aviso: sin esto la bandeja no
    // recarga el caché y «Colegio» se volvía a ofrecer como «Crear».
    UsedCategoriesCache.record(evento.category, evento.type)
    if (esComprobante) Repositories.wallets.confirmarComprobante(smsId, evento.id)
    else Repositories.wallets.confirmSmsCon(smsId, evento.id)
}
