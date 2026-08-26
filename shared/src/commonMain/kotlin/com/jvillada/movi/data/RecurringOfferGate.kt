package com.jvillada.movi.data

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
import com.jvillada.movi.ui.recurrentes.prefillFrom
import com.jvillada.movi.ui.recurrentes.shouldOfferRecurring
import com.jvillada.movi.ui.recurrentes.throttleKeyFor

/**
 * Ola 9 · B: quién decide si, después de guardar un movimiento, se ofrece convertirlo en
 * recurrente. Las reglas del "sí o no" son puras y viven en
 * [com.jvillada.movi.ui.recurrentes.shouldOfferRecurring]; acá está lo que tiene estado: lo que
 * el dueño YA tiene anotado como recurrente y la memoria de lo que ya se ofreció en esta sesión.
 *
 * **Qué se necesita saber para no molestar de más.** Dos listas: las reglas recurrentes y las
 * suscripciones. Las dos, porque Recurrentes las muestra juntas y las SUMA juntas: ofrecer una
 * regla «Netflix» a quien ya tiene la suscripción «Netflix» le duplica el cobro en el flujo
 * libre.
 *
 * **Costo en red: como mucho un par de llamadas por sesión, y solo si el dueño anota algo.** No
 * se piden al arrancar ni desde el Inicio (que ya dispara demasiadas), sino la primera vez que
 * se guarda un movimiento; después se reusan. Y si el dueño pasó por Recurrentes, esa pantalla
 * ya las cargó y las deja acá con [recordarLoQueYaHay]: en ese camino no se pide nada.
 *
 * Si la carga falla, no se ofrece nada: es preferible perder un ofrecimiento que proponerle
 * crear un recurrente que ya tiene.
 */
object RecurringOfferGate {
    private var reglas: List<RecurringRule>? = null
    private var suscripciones: List<Subscription>? = null
    private val yaOfrecidas = mutableSetOf<String>()

    /**
     * Lo que Recurrentes acaba de cargar. Sirve para dos cosas: ahorrarse las llamadas de acá, y
     * —más importante— **mantener el gate al día cuando el dueño crea o borra un recurrente en
     * esa pantalla**. Sin esto, creaba «Gimnasio» ahí, anotaba el pago y la app le ofrecía crear
     * el recurrente que acababa de crear.
     */
    fun recordarLoQueYaHay(reglas: List<RecurringRule>?, suscripciones: List<Subscription>?) {
        reglas?.let { this.reglas = it }
        suscripciones?.let { this.suscripciones = it }
    }

    /** Después de crear o borrar una regla desde acá: lo cacheado quedó viejo. */
    fun olvidarReglas() {
        reglas = null
    }

    /** Al cerrar sesión: lo de acá es del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        reglas = null
        suscripciones = null
        yaOfrecidas.clear()
    }

    /**
     * El formulario prellenado que hay que ofrecer para [event], o `null` si no hay que ofrecer
     * nada. Marca la "cosa" como ya ofrecida: en esta sesión no vuelve a salir para esa
     * categoría, la haya tomado el dueño o no (ver la guarda 3 en `RecurringOffer.kt`).
     */
    suspend fun ofrecerPara(event: FinancialEvent): RecurringPrefill? {
        val reglasAlDia = reglas ?: runCatching { Repositories.wallets.getRecurringRules() }
            .onSuccess { reglas = it }
            .getOrNull()
            ?: return null
        // Las suscripciones son la mitad de la guarda contra duplicados, así que si no se
        // pudieron cargar tampoco se ofrece: proponer una regla que duplica un cobro ya contado
        // es peor que no proponer nada.
        val cobrosAlDia = suscripciones ?: runCatching { Repositories.wallets.getSubscriptions().subscriptions }
            .onSuccess { suscripciones = it }
            .getOrNull()
            ?: return null
        // Solo las que de verdad suman en «Gastos recurrentes» — mismo filtro que
        // `resumenRecurrentes`: una candidata que el dueño descartó no bloquea nada.
        val activas = cobrosAlDia
            .filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
            .map { it.displayName }
        if (!shouldOfferRecurring(event, reglasAlDia, yaOfrecidas, activas)) return null
        yaOfrecidas += throttleKeyFor(event)
        return prefillFrom(event)
    }
}
