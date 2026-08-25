package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.rem
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

/** Milisegundos de UTC-5 (Bogotá, sin horario de verano). */
private const val BOGOTA_OFFSET_MS = 5L * 3_600_000L
private const val DAY_MS = 86_400_000L

/** Nombre viejo del SMS recién llegado, reemplazado por `SMS_STATE_PENDING`. Solo lo usa la migración. */
private const val LEGACY_SMS_STATE_NEW = "new"

private val migrationsLog = LoggerFactory.getLogger("Migrations")

/** Índice único que expresa "una sola pata por traspaso y por lado" — ver su migración. */
const val UNIQUE_TRANSFER_LEG_INDEX = "uq_events_transfer_leg"

/**
 * Migraciones que corren al arrancar, después del schema, dentro de esa misma transacción. Todas
 * deben ser idempotentes por construcción (correrlas dos veces no cambia nada la segunda).
 *
 * Casi todas son de datos. La excepción es [createUniqueTransferLegIndex], que agrega un índice:
 * vive acá y no en `Tables.kt` justamente porque necesita mirar los datos antes de tocar el
 * esquema — un `CREATE UNIQUE INDEX` que falle acá aborta la transacción y deja el server sin
 * arrancar, así que primero pregunta y solo después crea. Cualquier migración de esquema que se
 * agregue después tiene que respetar lo mismo: **nada que pueda tumbar el arranque**.
 */
object Migrations {
    fun Transaction.runAll() {
        restampStatementEventsToBogota()
        renameLegacyNewSmsStateToPending()
        createUniqueTransferLegIndex()
    }

    /**
     * **Un traspaso tiene exactamente dos patas: una de salida y una de entrada.** Hasta acá esa
     * regla vivía solo en el código —el cliente, y `POST /api/transfers`, que inserta las dos en
     * una transacción— y el esquema no la conocía: `idx_events_transfer_id` es un índice común, no
     * único, así que nada impedía que un `transfer_id` terminara con tres o cuatro patas.
     *
     * **Por qué un único COMPUESTO y no un único a secas sobre `transfer_id`:** las dos patas
     * comparten el `transfer_id` a propósito. Un único simple sobre esa columna rechazaría la
     * segunda pata de TODOS los traspasos — rompería la feature entera y, sobre una base con
     * traspasos reales, ni siquiera se podría crear. La forma que sí expresa la regla es
     * `(user_id, transfer_id, type)`: como máximo un EXPENSE y un INCOME por traspaso, o sea como
     * máximo dos patas y nunca dos del mismo lado.
     *
     * Se incluye `user_id` porque toda lectura de este sistema ya está acotada por usuario: la
     * unicidad correcta es por dueño, no global. Dos usuarios que por una casualidad astronómica
     * generaran el mismo id no tienen por qué molestarse.
     *
     * **Los eventos normales no se ven afectados**: tienen `transfer_id` en NULL, y tanto
     * Postgres como H2 tratan como distintas todas las filas cuya clave tiene algún NULL. Es la
     * suposición riesgosa de este cambio, así que está fijada por test (`MigrationsTest`).
     *
     * **Lo que esto NO expresa** y por eso vive además en el POST: "al menos dos patas", "de
     * cuentas distintas" y "del mismo monto". Un índice no puede exigir la *existencia* de una
     * fila hermana; eso solo lo puede garantizar quien escribe las dos, que es el endpoint —
     * las inserta en una sola transacción, y desde este cambio además rechaza reusar un
     * `transferId` que ya tiene patas ajenas (`TRANSFER_ID_ALREADY_USED`).
     *
     * **Si los datos ya violan la regla, no se crea el índice y el server arranca igual.** Esta
     * migración corre dentro de la transacción del esquema: un `CREATE UNIQUE INDEX` que falle la
     * aborta entera y deja el server sin levantar. Un arranque caído es mucho peor que un índice
     * que falta —el POST sigue defendiendo la regla y los traspasos sanos siguen funcionando—, así
     * que primero se pregunta y solo después se crea. Si algún día pasa, el log dice qué
     * `transfer_id` hay que limpiar a mano.
     *
     * Idempotente por `IF NOT EXISTS` (soportado por Postgres y por H2 en modo PostgreSQL).
     *
     * @return `true` si el índice quedó creado (o ya estaba)
     */
    fun Transaction.createUniqueTransferLegIndex(): Boolean {
        val duplicados = duplicateTransferLegs()
        if (duplicados.isNotEmpty()) {
            // WARN de verdad y no un println: este mensaje dice "tus datos no quedaron
            // protegidos por el esquema", y tiene que salir con nivel y marca de tiempo entre el
            // resto del log del arranque, no como una línea suelta en stdout.
            migrationsLog.warn(
                "NO se creó {}: hay {} grupo(s) (user_id, transfer_id, type) con más de una pata. " +
                    "transfer_id afectados: {}. Hay que dejar dos patas por traspaso " +
                    "(una EXPENSE y una INCOME) y reiniciar.",
                UNIQUE_TRANSFER_LEG_INDEX,
                duplicados.size,
                duplicados.joinToString(),
            )
            return false
        }
        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS $UNIQUE_TRANSFER_LEG_INDEX " +
                "ON financial_events (user_id, transfer_id, type)",
        )
        return true
    }

    /**
     * Los `transfer_id` que hoy tienen más de una pata del mismo lado — o sea, los que impedirían
     * crear el índice de [createUniqueTransferLegIndex]. Vacío es lo esperado siempre.
     */
    fun Transaction.duplicateTransferLegs(): List<String> =
        exec(
            """
            SELECT transfer_id FROM financial_events
            WHERE transfer_id IS NOT NULL
            GROUP BY user_id, transfer_id, type
            HAVING COUNT(*) > 1
            """.trimIndent(),
        ) { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }.orEmpty()

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
