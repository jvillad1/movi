package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import com.jvillada.movi.server.time.AppClock

/**
 * Corre la detección de suscripciones y el upsert por estados para [uid].
 * Best-effort: el caller decide si un fallo importa (el import de extractos lo
 * envuelve en runCatching; la ruta /detect lo deja propagar).
 */
suspend fun runSubscriptionDetection(uid: String) {
    val events = loadNonVoidedEvents(uid)
        .filterNot { it.description.startsWith(FAMIRIOS_STAMP_PREFIX) }
    val detected = detectSubscriptions(events, AppClock.today())
    dbQuery {
        val existing = Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .associateBy { it[Subscriptions.merchantKey] to it[Subscriptions.currency] }
        // **Lo que el dueño ya anotó a mano como recurrente no se vuelve a descubrir.**
        //
        // Recurrentes muestra reglas y suscripciones en UNA sola lista y las suma juntas en
        // «Gastos recurrentes». Desde que se puede decir «esto se repite» sobre un movimiento ya
        // guardado, el camino a la duplicación quedó corto y realista: el dueño anota la regla
        // «Netflix» desde el movimiento de este mes, dos meses después el detector ve los tres
        // cargos y propone la suscripción «Netflix», él la confirma sin sospechar que es la misma
        // — y a partir de ahí Movi le cuenta $44.900 dos veces y le muestra dos filas en
        // «Próximos pagos».
        //
        // La guarda es simétrica de la que ya existía del otro lado (`shouldOfferRecurring` no
        // ofrece una regla cuando ya hay una suscripción con ese nombre) y usa la MISMA
        // comparación de nombres, [claveComparableDeNombre], para que las dos coincidan siempre.
        //
        // **Solo frena las ALTAS.** Una suscripción que ya existe se sigue refrescando (monto,
        // último cobro, ocurrencias) aunque hoy haya una regla con ese nombre: puede ser un par
        // que el dueño ya convivía antes de esta ola, y dejar de actualizarla sería empeorar
        // datos que él ya está mirando en vez de evitar una fila nueva.
        val nombresDeReglas = RecurringRules.selectAll()
            .where { RecurringRules.userId eq uid }
            .mapTo(mutableSetOf()) { claveComparableDeNombre(it[RecurringRules.name]) }
            .filterNotTo(mutableSetOf()) { it.isEmpty() }
        for (d in detected) {
            val yaEsUnaRegla = claveComparableDeNombre(d.displayName) in nombresDeReglas
            upsertDetected(uid, d, existing[d.merchantKey to d.currency], yaEsUnaRegla)
        }
    }
}

// SQLSTATE estándar (Postgres y H2) para violación de índice único.
private const val UNIQUE_VIOLATION_SQLSTATE = "23505"

// FamiriosParser.kt stampa cada ParsedTransaction que genera con este prefijo exacto en
// `description` ("Famirios · $label · $mes $año"). Son agregados mensuales de presupuesto
// (un EXPENSE por categoría×mes, monto estable, fecha de fin de mes) — cumplen toda la
// heurística del detector pero NO son suscripciones reales, así que se excluyen del pool de
// eventos ANTES de detectar. Se filtra por `description` (no por `category`, que varía por
// categoría de gasto, ni por `rawPayload`, que no se usa) porque es el único campo con un
// discriminador fijo y determinístico para todos los eventos de este origen.
private const val FAMIRIOS_STAMP_PREFIX = "Famirios · "

// F39: antes, confianza HIGH entraba directo como AUTO (activa, sin que el dueño la viera ni
// la confirmara) — justo lo que el feedback pidió cambiar ("nada nace activo"). Ahora TODO lo
// detectado nace CANDIDATE sin importar la confianza; el dueño confirma o descarta desde
// la pantalla Recurrentes (sección "Detectadas · por confirmar"), igual que ya pasaba con
// confianza media/baja. SubStatus.AUTO deja de producirse acá, pero el enum se queda: las
// filas AUTO que ya existían de antes de este cambio se tratan como confirmadas en todos
// lados (ver `resultFor` en SubscriptionRoutes.kt, que ya sumaba AUTO+CONFIRMED) — no hay
// migración de datos, simplemente se las deja de fabricar.
private fun statusForNew(@Suppress("UNUSED_PARAMETER") d: DetectedSub): SubStatus = SubStatus.CANDIDATE

// Check-then-insert de por sí no es atómico: dos detects concurrentes (doble tap en
// "Re-escanear") pueden ver `row == null` a la vez y ambos intentar el mismo
// (userId, merchantKey, currency). El índice único uq_subscriptions_user_merchant_currency
// deja pasar solo a uno; el otro cae en ExposedSQLException (23505). En vez de propagar
// un 500, el perdedor re-lee la fila ganadora y aplica la MISMA rama que habría aplicado
// si la hubiera visto desde el inicio (DISMISSED se respeta, CONFIRMED se actualiza
// parcialmente, AUTO/CANDIDATE se refresca por completo) — ambas transacciones calculan el
// mismo DetectedSub a partir de los mismos eventos, así que converger en el update es
// equivalente a haber ganado el insert.
//
// El insert va detrás de un SAVEPOINT: en Postgres, un error dentro de una transacción la
// deja "abortada" (cualquier statement posterior falla) salvo que se haga rollback a un
// savepoint — por eso no basta con un try/catch simple si se quiere seguir usando la misma
// transacción externa (dbQuery) para el re-read + update.
private fun Transaction.upsertDetected(
    uid: String,
    d: DetectedSub,
    row: ResultRow?,
    /** ¿El dueño ya tiene una regla recurrente con este nombre? Ver [runSubscriptionDetection]. */
    yaEsUnaRegla: Boolean = false,
) {
    if (row != null) {
        applyExisting(row, d)
        return
    }
    // Alta frenada: ya está anotado como regla, y dos filas para el mismo cobro le duplican el
    // gasto en «Gastos recurrentes» y en «Próximos pagos».
    if (yaEsUnaRegla) return
    val savepoint = connection.setSavepoint("sub_detect_${d.merchantKey}_${d.currency}")
    try {
        insertNew(uid, d)
        connection.releaseSavepoint(savepoint)
    } catch (e: ExposedSQLException) {
        if (e.sqlState != UNIQUE_VIOLATION_SQLSTATE) throw e
        connection.rollback(savepoint)
        val winner = Subscriptions.selectAll()
            .where {
                (Subscriptions.userId eq uid) and
                    (Subscriptions.merchantKey eq d.merchantKey) and
                    (Subscriptions.currency eq d.currency)
            }
            .firstOrNull() ?: throw e
        applyExisting(winner, d)
    }
}

private fun applyExisting(row: ResultRow, d: DetectedSub) {
    when (row[Subscriptions.status]) {
        SubStatus.DISMISSED.name -> Unit  // el usuario dijo que no
        // F39: AUTO se trata igual que CONFIRMED acá — son las filas AUTO que quedaron de
        // antes de este cambio (ya no se fabrican nuevas). Sin este caso, refreshRow las
        // bajaría a CANDIDATE en cada re-scan (porque statusForNew ahora siempre devuelve
        // CANDIDATE) y una suscripción que el dueño nunca vio como "pendiente" reaparecería
        // pidiendo confirmación — exactamente lo que el enum congelado busca evitar.
        SubStatus.CONFIRMED.name, SubStatus.AUTO.name ->
            Subscriptions.update({ Subscriptions.id eq row[Subscriptions.id] }) {
                it[amount]      = d.amount
                it[lastSeen]    = d.lastSeen
                it[occurrences] = d.occurrences
                it[confidence]  = d.confidence.name
            }
        else -> refreshRow(row[Subscriptions.id], d)  // CANDIDATE: refrescar todo (status se queda en CANDIDATE)
    }
}

// Ola 16 — **la periodicidad NO se toca en ninguna de las ramas de acá, ni siquiera en el
// refresco completo de una CANDIDATE.**
//
// El detector infiere suscripciones de cargos que se repiten MES a MES (ver `detectSubscriptions`:
// agrupa por mes y pide meses consecutivos), así que todo lo que produce es, por construcción,
// mensual — y una fila nueva nace MENSUAL por el default de la columna, sin que estas funciones
// tengan que escribir nada. Lo que un `it[periodicidad] = MENSUAL` agregaría no es corrección
// sino la capacidad de PISAR: cualquier fila que el dueño hubiera marcado como anual volvería a
// mensual en el próximo barrido, y su costo se multiplicaría por doce sola.
//
// Es el mismo razonamiento que ya protege al alta manual (clave `manual_*`, que el detector nunca
// genera) y al DISMISSED de `applyExisting`: **lo que decidió el dueño gana sobre lo que infiere
// el barrido.** Una CANDIDATE es siempre del detector y siempre mensual, así que ni ahí hay algo
// que reescribir; dejarla fuera del update es lo que hace que la regla valga sin excepciones.
private fun refreshRow(rowId: String, d: DetectedSub) {
    Subscriptions.update({ Subscriptions.id eq rowId }) {
        it[displayName] = d.displayName
        it[amount]      = d.amount
        it[dayOfMonth]  = d.dayOfMonth
        it[status]      = statusForNew(d).name
        it[confidence]  = d.confidence.name
        it[firstSeen]   = d.firstSeen
        it[lastSeen]    = d.lastSeen
        it[occurrences] = d.occurrences
        it[accountId]   = d.accountId
    }
}

// Sin `periodicidad`: el default de la columna la deja MENSUAL, que es lo único que el detector
// puede afirmar. Ver el comentario de [refreshRow].
private fun insertNew(uid: String, d: DetectedSub) {
    Subscriptions.insert {
        it[id]          = "sub_${UUID.randomUUID()}"
        it[userId]      = uid
        it[merchantKey] = d.merchantKey
        it[displayName] = d.displayName
        it[amount]      = d.amount
        it[currency]    = d.currency
        it[dayOfMonth]  = d.dayOfMonth
        it[status]      = statusForNew(d).name
        it[confidence]  = d.confidence.name
        it[firstSeen]   = d.firstSeen
        it[lastSeen]    = d.lastSeen
        it[occurrences] = d.occurrences
        it[accountId]   = d.accountId
    }
}
