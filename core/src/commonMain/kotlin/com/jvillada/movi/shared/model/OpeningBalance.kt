package com.jvillada.movi.shared.model

import kotlin.math.abs

/**
 * Evento que declara el saldo/deuda con el que arranca una cuenta, o null cuando no hay nada
 * que registrar. Los saldos se derivan de eventos, así que un saldo inicial declarado tiene que
 * existir como un evento real: activos abren con un INCOME ("Saldo inicial"), cuentas de deuda
 * (tarjeta de crédito / préstamo) con un EXPENSE ("Deuda inicial" — EXPENSE sube la deuda, ver
 * [signedDelta]).
 *
 * Vive en `:core`, no en `:server` (de donde se movió, hallazgo Critical de la revisión de la Ola
 * 1b): **el cliente es quien crea este evento, una sola vez, explícitamente** — no el server. El
 * escenario que forzó el cambio: una cuenta creada offline (`LocalRepository.createAccount`,
 * `syncedAt = null`) con un ingreso anotado antes del primer sync dejaba el saldo local en, por
 * ejemplo, $50.000. Cuando volvía la red, `SyncEngine.syncAccounts` mandaba esa cuenta con
 * `balance = 50.000`; si el server hubiera seguido fabricando un evento de apertura a partir de
 * ese balance (como hacía antes `AccountRoutes.kt` POST), el ingreso real que `syncEvents` empuja
 * a continuación se habría sumado ENCIMA — el server habría terminado en $100.000, el doble del
 * saldo real, una divergencia silenciosa y permanente entre teléfono y web. Con el cliente
 * generando la apertura una sola vez (ver `CreateAccountSheet.kt`, único call site) y
 * `POST /api/accounts` sin tocar esta función, ese doble conteo no puede ocurrir: `syncAccounts`
 * solo empuja la fila `accounts` (que el server ya no convierte en evento), y `syncEvents` sube
 * el opening y el ingreso real como los dos eventos independientes que son.
 *
 * `CreditRoutes.kt` POST (alta de crédito) es la excepción a propósito: ahí crear la cuenta LOAN
 * y su apertura son atómicos en una sola transacción del server, y ese endpoint no pasa por
 * `LocalRepository.createAccount` ni por ningún flujo offline — no hay ventana en la que el
 * cliente y el server puedan fabricar el mismo evento dos veces.
 *
 * Categoría [OPENING_CATEGORY] para los dos casos (F54): la descripción sigue distinguiendo
 * "Saldo inicial"/"Deuda inicial", pero es la categoría la que [isCashFlow] usa para excluir
 * este evento de ingresos/egresos del mes — abrir una cuenta con plata que ya tenías no es un
 * movimiento del mes en que se crea la cuenta.
 *
 * `id` generado con [newId] por defecto (no `java.util.UUID`, que es JVM-only): esta función vive
 * en `commonMain` y corre también en Android/iOS/wasmJs.
 */
fun openingEventFor(account: Account, now: Long, id: String = newId("ev")): FinancialEvent? {
    if (account.balance == 0L) return null
    val isDebt = account.type == AccountType.CREDIT_CARD || account.type == AccountType.LOAN
    return FinancialEvent(
        id                   = id,
        accountId            = account.id,
        type                 = if (isDebt) TransactionType.EXPENSE else TransactionType.INCOME,
        amount               = abs(account.balance),
        currency             = account.currency,
        category             = OPENING_CATEGORY,
        description          = if (isDebt) "Deuda inicial" else "Saldo inicial",
        timestamp            = now,
        source               = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
}
