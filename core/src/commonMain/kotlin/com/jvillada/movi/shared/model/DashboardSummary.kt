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
     * cálculo por mes de calendario que el Inicio calculaba del lado del
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
    /**
     * **El gasto variable de cada día del período** (`"YYYY-MM-DD"` → pesos): lo que cuenta en
     * «Gastos» menos los pagos del checklist. Ver [gastoVariablePorDia].
     *
     * Alimenta la tarjeta «Disponible» del Inicio, que mide lo gastado en el período, la semana y
     * hoy contra lo que queda de «ingresos menos fijos».
     *
     * `null` = el server es anterior a este campo: la tarjeta no se pinta, porque sin él no hay
     * con qué medir lo gastado — un mapa vacío por defecto diría «gastaste $0», que es falso.
     * Un APK viejo lo ignora (`ignoreUnknownKeys`).
     */
    val gastoVariablePorDia: Map<String, Long>? = null,
    /**
     * **Lo que había en «Tu plata» a las 00:00 del primer día del período**, en pesos. Ver
     * [PlataDelPeriodo.saldoAlInicio].
     *
     * Con [entradasDelPeriodo] y [guardadoDelPeriodo] cambia el Disponible de «ingresos menos
     * fijos» a «lo que tenías + lo que entró − lo que guardaste − fijos». `null` = el server es
     * anterior a estos campos, y el cliente vuelve a la cuenta de antes. Solo se AGREGAN campos:
     * un APK viejo los ignora (`ignoreUnknownKeys`).
     */
    val saldoTuPlataAlInicio: Long? = null,
    /** «Entraron» en el período: ver [PlataDelPeriodo.entradas]. */
    val entradasDelPeriodo: Long? = null,
    /** Lo que salió de Tu plata a un ahorro o inversión de afuera: ver [PlataDelPeriodo.guardado]. */
    val guardadoDelPeriodo: Long? = null,
    /**
     * Lo que salió de Tu plata a una deuda y que ni los fijos ni el gasto variable cuentan: cuotas
     * que ningún ítem del checklist reclama y pagos de tarjeta por encima de lo comprado con esa
     * tarjeta en el período (deuda de antes). Ver [pagosDeDeudaFueraDelChecklist]. El Disponible
     * lo resta. `null` = server anterior al campo: se toma como cero. Solo se AGREGA.
     */
    val pagosDeDeudaFueraDelChecklist: Long? = null,
    /**
     * **El patrimonio honesto**: tu plata, lo condicionado, los bienes y las deudas, ya partidos
     * con [patrimonioDe] — la MISMA función que usa el cliente sobre la lista de cuentas.
     *
     * Viaja acá para quien no tiene la lista de cuentas a mano y necesita las cifras de un
     * vistazo: la página para compartir (entrega D, HTML servido por el server) y el Inicio nuevo
     * (entrega B). El Inicio de hoy sigue calculándolo de `GET /api/accounts`, que ya pide.
     *
     * `null` = server anterior a este campo. Un APK viejo lo ignora (`ignoreUnknownKeys`).
     */
    val patrimonio: Patrimonio? = null,
    /**
     * Ola A: **la cuenta con más gastos en los últimos 30 días**, para que «Agregar» arranque ahí
     * en vez de la primera por orden alfabético. El dueño tiene 71 gastos en 60 días en
     * «Bancolombia Ahorros» y 0 en «AMEX 9208», y hoy el campo arranca en AMEX porque es la
     * primera de la lista — no la que usa. Ver [cuentaMasUsada] en `DashboardRoutes.kt`.
     *
     * `null` = sin gastos en la ventana (cuenta nueva) o server anterior a este campo; un APK
     * viejo lo ignora (`ignoreUnknownKeys`) y un APK nuevo sin este dato cae al orden de siempre.
     */
    val cuentaMasUsada: String? = null,
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
    /** Ícono elegido a mano (ver [CategoryPref.icono]). `null` = el que Movi asigna por defecto. */
    val icono: String? = null,
    /** Color elegido a mano (ver [CategoryPref.color]). `null` = el que Movi asigna por defecto. */
    val color: String? = null,
    /**
     * Ola A: movimientos **no anulados** con esta categoría en los últimos 60 días, de
     * cualquier tipo. Una fila que existe solo por una preferencia ([hidden]/[pinnedType], sin
     * movimientos) queda en 0 — es el caso normal de esconder una categoría del catálogo que
     * nunca se usó.
     */
    val usosRecientes: Int = 0,
)
