package com.jvillada.movi.data

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
import com.jvillada.movi.ui.recurrentes.claveDeNombre
import com.jvillada.movi.ui.recurrentes.prefillFrom
import com.jvillada.movi.ui.recurrentes.prefillNameFor
import com.jvillada.movi.ui.recurrentes.shouldOfferRecurring

/**
 * Ola 9 · B: quién decide si, después de guardar un movimiento, se ofrece convertirlo en
 * recurrente. Las reglas del "sí o no" son puras y viven en
 * [com.jvillada.movi.ui.recurrentes.shouldOfferRecurring]; acá está lo que tiene estado: la
 * lista de recurrentes que ya existen y la memoria de lo que ya se ofreció en esta sesión.
 *
 * **Costo en red: una llamada por sesión, y solo si el dueño anota algo.** `GET /api/recurring-rules`
 * hace falta para no ofrecerle un «Arriendo» que ya tiene — sin eso la guarda contra duplicados
 * no existe. Se pide la primera vez que se guarda un movimiento (no al arrancar la app, y jamás
 * desde el Inicio, que ya dispara demasiadas) y se reusa el resto de la sesión. Al crear una
 * regla nueva se invalida con [olvidarReglas], así el ofrecimiento deja de salir de inmediato
 * para eso que el dueño acaba de anotar.
 *
 * Si la llamada falla, no se ofrece nada: es preferible perder un ofrecimiento que proponerle
 * crear un recurrente que ya tiene.
 */
object RecurringOfferGate {
    private var reglas: List<RecurringRule>? = null
    private val yaOfrecidas = mutableSetOf<String>()

    /** Después de crear o borrar una regla: la lista cacheada quedó vieja. */
    fun olvidarReglas() {
        reglas = null
    }

    /** Al cerrar sesión: lo de acá es del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        reglas = null
        yaOfrecidas.clear()
    }

    /**
     * El formulario prellenado que hay que ofrecer para [event], o `null` si no hay que ofrecer
     * nada. Marca la "cosa" como ya ofrecida: en esta sesión no vuelve a salir para ese nombre,
     * la haya tomado el dueño o no (ver la guarda 3 en `RecurringOffer.kt`).
     */
    suspend fun ofrecerPara(event: FinancialEvent): RecurringPrefill? {
        val cargadas = reglas ?: runCatching { Repositories.wallets.getRecurringRules() }
            .onSuccess { reglas = it }
            .getOrNull()
            ?: return null
        if (!shouldOfferRecurring(event, cargadas, yaOfrecidas)) return null
        yaOfrecidas += claveDeNombre(prefillNameFor(event))
        return prefillFrom(event)
    }
}
