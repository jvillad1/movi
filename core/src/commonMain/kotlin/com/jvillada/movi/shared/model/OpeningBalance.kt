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

/**
 * Lo que la hoja de un saldo inicial le dice al dueño cuando lo abre desde Movimientos.
 *
 * Vive acá y no en la pantalla por la misma razón que [ORPHANED_LEG_EXPLAINER]: es la explicación
 * de una regla de `:core` ([isCashFlow] excluye [OPENING_CATEGORY], [movementCount] no lo cuenta),
 * y tiene que poder cambiar junto con la regla y no con el diseño de una hoja.
 *
 * Dice las dos cosas que el dueño necesita y ninguna más: **qué es** (por qué no suma, por qué no
 * está en la lista) y **dónde se arregla**. Los dos caminos de arreglo son reales, distintos y no
 * intercambiables, así que se nombran los dos: el detalle de la cuenta es el único lugar de la app
 * donde esta fila se puede anular, y en un crédito «Ajustar saldo» (pantalla Créditos) deja la
 * deuda en la cifra del banco **registrando un movimiento más** —ver `debtAdjustmentEventFor` en
 * el server— sin tocar la apertura. Para el caso que originó esto (una deuda inicial cargada de
 * más) el segundo camino es el bueno: la apertura sigue contando la historia real y el ajuste la
 * corrige, que es como Movi arregla todo lo demás.
 */
const val OPENING_BALANCE_EXPLAINER: String =
    "Es el saldo con el que esta cuenta entró a Movi, no algo que pasó ese día: por eso no cuenta " +
        "como ingreso ni como gasto, y no se lista entre tus movimientos. Si el monto quedó mal, " +
        "ábrelo en el detalle de la cuenta y anúlalo. En un crédito también puedes dejar la deuda " +
        "en la cifra real con «Ajustar saldo», sin tocar esta fila."
