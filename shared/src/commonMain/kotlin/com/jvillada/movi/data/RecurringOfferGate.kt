package com.jvillada.movi.data

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
import com.jvillada.movi.ui.recurrentes.nombresDeSuscripcionesQueYaSuman
import com.jvillada.movi.ui.recurrentes.categoryThrottleKeyFor
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
    /** Las "cosas" (tipo + categoría + monto) que ya recibieron su barra en esta sesión. */
    private val yaOfrecidas = mutableSetOf<String>()
    /**
     * Categoría (con tipo) -> barras de esa categoría que se ofrecieron y NO se tomaron. Es el
     * techo de insistencia de la guarda 3; [seTomo] lo descuenta.
     */
    private val sinTomarPorCategoria = mutableMapOf<String, Int>()

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

    /**
     * Después de crear, editar o borrar un recurrente (regla o cobro) desde cualquier pantalla:
     * lo cacheado quedó viejo y hay que volver a preguntar.
     *
     * Se tiran las DOS listas y no solo la de reglas porque la hoja de recurrentes también crea
     * suscripciones manuales. Y se invalida en el momento de la mutación —no se espera a que la
     * recarga traiga lo nuevo—: si esa recarga falla, quedarse con lo viejo le esconde al dueño
     * un ofrecimiento legítimo por el recurrente que acaba de borrar. Sin cache, el gate vuelve
     * a pedirlo la próxima vez que haga falta, que es como mucho un par de llamadas.
     */
    fun olvidarLoCacheado() {
        reglas = null
        suscripciones = null
    }

    /** Al cerrar sesión: lo de acá es del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        reglas = null
        suscripciones = null
        yaOfrecidas.clear()
        sinTomarPorCategoria.clear()
    }

    /**
     * El dueño TOMÓ el ofrecimiento (tocó «Sí, anótalo»). Descuenta el techo de insistencia de
     * esa categoría: lo que se cuenta es cuántas veces se le insistió **al pedo**, no cuántas
     * barras salieron. Sin esto, quien configura la app cargando cuatro suscripciones de
     * «Entretenimiento» de una sentada chocaba contra el techo justo cuando la función está
     * haciendo exactamente lo que se le pidió.
     *
     * Se llama al ACEPTAR y no al guardar: aceptar ya es la señal de que la barra le sirvió, y
     * si además guarda, la regla nueva la tapa sola por nombre (guarda 2).
     */
    fun seTomo(prefill: RecurringPrefill) {
        val clave = categoryThrottleKeyFor(prefill)
        val quedan = (sinTomarPorCategoria[clave] ?: 0) - 1
        if (quedan <= 0) sinTomarPorCategoria.remove(clave) else sinTomarPorCategoria[clave] = quedan
    }

    /**
     * PR 1 del rediseño de Recurrentes: **las mismas dos listas de acá arriba, para Movimientos**
     * — el chip «Recurrentes» y la marca en cada fila (ver [com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe]).
     *
     * Se reusa este cache y no uno nuevo: son las MISMAS dos llamadas que ya hace este gate, y
     * Movimientos pintando su propia copia hubiera sido la clase de duplicación que el proyecto
     * ya pagó cara antes («dos copias de la misma regla»). Si ya están cargadas (por este gate o
     * por lo que dejó `recordarLoQueYaHay`), no hay ningún viaje de red; si no, se cargan una vez
     * y quedan cacheadas para el resto de la sesión, igual que [ofrecerPara].
     *
     * Si una lectura falla, esa lista vuelve vacía — un chip que no reconoce nada es preferible a
     * una pantalla que no carga por una función que solo iba a pintar un ícono de más.
     */
    suspend fun listasParaMovimientos(): Pair<List<RecurringRule>, List<Subscription>> {
        val reglasAlDia = reglas ?: runCatching { Repositories.wallets.getRecurringRules() }
            .onSuccess { reglas = it }
            .getOrNull()
        val cobrosAlDia = suscripciones ?: runCatching { Repositories.wallets.getSubscriptions().subscriptions }
            .onSuccess { suscripciones = it }
            .getOrNull()
        return (reglasAlDia ?: emptyList()) to (cobrosAlDia ?: emptyList())
    }

    /**
     * El formulario prellenado que hay que ofrecer para [event], o `null` si no hay que ofrecer
     * nada. Marca la "cosa" (tipo + categoría + monto) como ya ofrecida y le suma uno al contador
     * de insistencia de su categoría, que [seTomo] descuenta si el dueño la toma (guarda 3 en
     * `RecurringOffer.kt`).
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
        // Solo las que de verdad suman en «Gastos recurrentes». El filtro vive en
        // `nombresDeSuscripcionesQueYaSuman`, compartido con «Esto se repite» del detalle del
        // movimiento: los dos caminos que crean un recurrente tienen que medir lo mismo.
        val activas = nombresDeSuscripcionesQueYaSuman(cobrosAlDia)
        if (!shouldOfferRecurring(event, reglasAlDia, yaOfrecidas, activas, sinTomarPorCategoria)) return null
        yaOfrecidas += throttleKeyFor(event)
        val categoria = categoryThrottleKeyFor(event)
        sinTomarPorCategoria[categoria] = (sinTomarPorCategoria[categoria] ?: 0) + 1
        return prefillFrom(event)
    }
}
