package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.rem
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update

/** Milisegundos de UTC-5 (Bogotá, sin horario de verano). */
private const val BOGOTA_OFFSET_MS = 5L * 3_600_000L
private const val DAY_MS = 86_400_000L

/** Nombre viejo del SMS recién llegado, reemplazado por `SMS_STATE_PENDING`. Solo lo usa la migración. */
private const val LEGACY_SMS_STATE_NEW = "new"

/**
 * Migraciones de datos que corren al arrancar, después del schema. Todas deben ser
 * idempotentes por construcción (correrlas dos veces no cambia nada la segunda).
 */
object Migrations {
    fun Transaction.runAll() {
        restampStatementEventsToBogota()
        renameLegacyNewSmsStateToPending()
    }

    /**
     * Antes de AppClock, los eventos de extracto (source = STATEMENT) se sellaban a las 00:00Z
     * del día del extracto. Ahora toda fecha civil se lee en Bogotá, y 00:00Z son las 7 pm del
     * día ANTERIOR: un gasto del 01/08 contaba en julio en /by-day, el resumen del mes, el
     * detector de suscripciones y el offline. Se corren esas filas +5 h, a la medianoche de
     * Bogotá, que es donde las sella hoy StatementRoutes.
     *
     * Idempotente: solo toca timestamps múltiplos exactos de un día (00:00Z); tras sumar 5 h
     * dejan de serlo. Un evento de otro origen (SMS, MANUAL) a 00:00Z exacto NO se toca.
     *
     * @return filas actualizadas
     */
    fun Transaction.restampStatementEventsToBogota(): Int =
        Events.update({ (Events.eventSource eq "STATEMENT") and ((Events.timestamp rem DAY_MS) eq 0L) }) {
            it[timestamp] = timestamp + BOGOTA_OFFSET_MS
        }

    /**
     * El SMS recién capturado tenía dos nombres: la ingesta (`POST /api/sms/sync`) escribía
     * `"new"` y TODOS los lectores filtraban por `"pending"` — el contador del Inicio, el
     * subtítulo de la bandeja y el botón «Revisar» de cada tarjeta. Resultado: los SMS
     * capturados de verdad nunca disparaban la alerta «mensajes del banco por confirmar» y
     * ni siquiera se podían abrir para conciliarlos.
     *
     * Ahora el único nombre es [SMS_STATE_PENDING]; esta migración pone al día las filas que
     * quedaron con el nombre viejo. `"confirmed"` e `"ignored"` no se tocan: esos ya los
     * decidió el dueño.
     *
     * Idempotente: la segunda corrida no encuentra ninguna fila en `"new"`.
     *
     * @return filas actualizadas
     */
    fun Transaction.renameLegacyNewSmsStateToPending(): Int =
        SmsMessages.update({ SmsMessages.state eq LEGACY_SMS_STATE_NEW }) {
            it[state] = SMS_STATE_PENDING
        }
}
