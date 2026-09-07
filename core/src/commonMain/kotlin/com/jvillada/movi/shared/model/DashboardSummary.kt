package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * Lo que el Inicio necesita del server **ya reducido a números** — `GET /api/dashboard/summary`.
 *
 * Antes la pantalla se bajaba colecciones enteras para sacar una cifra: todos los SMS para
 * contar los pendientes, todos los candidatos a pago de tarjeta para un `.size`, todos los
 * eventos de la historia para sumar el gasto del mes por categoría. Con meses de uso real eso
 * crece lineal y hace lenta la pantalla más usada, sobre todo en el teléfono. Acá viaja solo el
 * resultado; el server lo calcula con las MISMAS reglas que el resto (`isCashFlow`,
 * `looksLikeCardPayment`, el mismo criterio de "pendiente" que el inbox de SMS).
 *
 * Todos los campos tienen default para que un cliente viejo y un server viejo sigan
 * deserializando sin romperse (el Json del server tiene `encodeDefaults=false`: un cero no viaja).
 */
@Serializable
data class DashboardSummary(
    val scope: Scope = Scope.SELF,
    /** Mes al que corresponden las cifras, "2026-08", con la misma zona horaria que usa el server. */
    val month: String = "",
    /** Ingresos del mes en COP que cuentan como flujo de caja — mismo número que `FinanceSummary.ingresos`. */
    val monthIncome: Long = 0,
    /** Egresos del mes en COP que cuentan como flujo de caja — mismo número que `FinanceSummary.egresos`. */
    val monthSpent: Long = 0,
    /**
     * Gasto del mes por categoría (egresos COP que son flujo de caja). Reemplaza al
     * `spentByCategoryForMonth(getEventsByDay(), mes)` que el Inicio calculaba del lado del
     * cliente; alimenta las alertas de presupuesto superado y la cifra del acceso «Presupuestos».
     */
    val spentByCategory: Map<String, Long> = emptyMap(),
    /** Cantidad que devolvería `GET /api/events/card-payment-candidates` — sin bajar la lista. */
    val cardPaymentCandidates: Int = 0,
    /** Mensajes del banco en estado `pending` — lo que el inbox de SMS llama «por confirmar». */
    val pendingSms: Int = 0,
    /**
     * **Cuántos mensajes del banco han llegado alguna vez** (cualquier estado), y el `time` del
     * más reciente. Ver [CapturaDeSms]: durante varias entregas la captura de SMS no entregó ni
     * un mensaje y nadie lo supo, porque su único indicador vivía en la app de Android y el
     * dueño trabaja en la web.
     *
     * Viajan en esta respuesta y no en un endpoint propio porque el Inicio ya la pide y es donde
     * la app arranca: la alerta «Movi nunca ha recibido un mensaje de tu banco» aparece sin una
     * llamada nueva en la pantalla que más se abre. La bandeja de SMS no consume estos campos —
     * ya se baja la lista completa y saca lo mismo con la MISMA función de `:core`
     * ([capturaDeSms]), sin depender del resumen del Inicio.
     *
     * `smsLastAt` es el string crudo de la columna (varchar libre, `"yyyy-MM-dd HH:mm"` o su
     * variante ISO); `null` = nunca llegó ninguno.
     */
    val smsTotal: Int = 0,
    val smsLastAt: String? = null,
    /**
     * El dueño pidió no ver el aviso de captura en el Inicio (preferencia de la cuenta, columna
     * `users.sms_alert_muted`). Viaja acá y no se pide aparte por lo mismo que los dos campos de
     * arriba: la decisión de pintar la alerta se toma con UNA sola respuesta.
     *
     * Silencia el recordatorio del Inicio, nunca el hecho: «Mensajes del banco» sigue diciendo
     * que no ha llegado nada.
     */
    val smsAlertMuted: Boolean = false,
    /**
     * Ola 9 · A2: **las categorías que el dueño ya usó alguna vez**, con el tipo (o los tipos)
     * con los que las usó. Viaja acá y no en un endpoint propio a propósito: el Inicio ya pide
     * esta respuesta y es la pantalla en la que la app arranca, así que las categorías propias
     * están disponibles en «Agregar» sin una sola llamada nueva — y el Inicio ya dispara
     * demasiadas. Del lado del server es un `DISTINCT (category, type)` sobre los movimientos
     * del usuario: unas decenas de filas como mucho, no la historia entera.
     *
     * Lo consume `com.jvillada.movi.data.UsedCategoriesCache`.
     */
    val usedCategories: List<UsedCategory> = emptyList(),
)

/**
 * Una categoría escrita por el dueño (o del catálogo) y **con qué tipos se la vio usada**.
 *
 * El tipo importa porque una categoría propia no tiene uno declarado, a diferencia de las de
 * `PREDEFINED_CATEGORIES`: sin esto, «Carro» se ofrecía igual al anotar un ingreso que un gasto.
 * [types] puede traer los dos (una categoría usada de los dos lados) o venir vacío (se la conoce
 * pero no se sabe de qué lado) — y ese vacío significa «mostrala igual», nunca «escondela».
 *
 * **Ola 10 — lo que el dueño decidió en «Más → Categorías» viaja acá.** [hidden] y [pinnedType]
 * son sus preferencias (tabla `category_prefs`), y llegan por este mismo campo en vez de por una
 * llamada nueva: sin ellas, esconder una categoría o fijarle el tipo no tendría ningún efecto
 * donde importa —el campo de categoría de «Agregar»—, que es precisamente para lo que sirven. Por
 * eso el server además emite una fila acá para toda categoría CON preferencia aunque no tenga
 * ningún movimiento (esconder «Freelance» sin haberla usado nunca es un caso normal); esa fila
 * viene con [types] vacío.
 */
@Serializable
data class UsedCategory(
    val name: String,
    val types: List<TransactionType> = emptyList(),
    /** El dueño la escondió: deja de ofrecerse al escribir. No toca ningún movimiento. */
    val hidden: Boolean = false,
    /** Tipo fijado a mano: `"EXPENSE"`, `"INCOME"` o `"BOTH"`. Manda sobre catálogo y uso. */
    val pinnedType: String? = null,
)
