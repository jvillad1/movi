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
 *
 * Dos frases se afinaron en la revisión de la ola, y las dos por lo mismo: **quien lee esto está
 * mirando la fila en Movimientos** (llegó por la búsqueda). Decía «no se lista entre tus
 * movimientos» —cierto, pero se lee como una contradicción con lo que la persona tiene en
 * pantalla— y ahora dice «solo aparece aquí si lo buscas», que además le enseña la regla. Y decía
 * «ábrelo en el detalle de la cuenta», que competía con el botón «Ver la cuenta» dibujado tres
 * líneas más abajo: ahora la frase nombra la acción («anúlalo desde la cuenta») y el botón se
 * encarga de llevar.
 */
const val OPENING_BALANCE_EXPLAINER: String =
    "Es el saldo con el que esta cuenta entró a Movi, no algo que pasó ese día: por eso no cuenta " +
        "como ingreso ni como gasto, y solo aparece aquí si lo buscas. Si el monto quedó mal, " +
        "anúlalo desde la cuenta. En un crédito también puedes dejar la deuda en la cifra real " +
        "con «Ajustar saldo», sin tocar esta fila."

/**
 * ¿Este evento es la **apertura de una cuenta** y no plata que entró o salió?
 *
 * Vivía en `TransactionsScreen.kt` y bajó a `:core` en la revisión de la Ola 16, junto con
 * [showsInMovements]: es la misma regla que [isCashFlow] y [movementCount] ya aplican acá abajo,
 * y tenerla enunciada en un archivo de UI dejaba dos de las tres juntas y la tercera sola. La
 * pantalla la sigue usando igual, importada.
 */
fun isOpeningBalance(event: FinancialEvent): Boolean = event.category == OPENING_CATEGORY

/**
 * ¿Este renglón entra en la **lista** de Movimientos?
 *
 * Ola 16. Movimientos contesta «¿qué hice?». Un saldo inicial no es algo que el dueño hizo: es el
 * ancla desde la que Movi deriva el saldo de la cuenta —la app nunca guarda saldos, los suma de
 * los eventos— o sea la foto de lo que ya existía el día que la cuenta entró a la app.
 *
 * Lo que lo volvió urgente no fue un número mal sumado: **los números ya estaban bien**. Medido
 * contra producción el 2026-08-29, el día 2026-08-28 tenía 8 filas y «Flujo del día −$4.558.789»,
 * que es exactamente la suma de las OTRAS 7 — la «Deuda inicial · Libre inversión 9695» de
 * $41.093.905 no estaba adentro, porque [isCashFlow] la excluye desde F54 y el renglón ya se
 * pintaba sin signo ni color. El problema era de ubicación: la cifra más grande de la pantalla,
 * encabezando el día del dueño, sin participar de ningún total. «Si no es un desembolso, ¿para
 * qué lo estamos contando como movimiento?».
 *
 * **Sacarla de la lista no esconde nada, y además destapa una contradicción que ya existía**:
 * [movementCount] —la misma función que alimenta `FinanceSummary.eventCount`, o sea el contador de
 * movimientos del Inicio y la guía de primeros pasos— nunca contó las aperturas. Con los datos de
 * arriba el server decía **15** y la lista mostraba **16** renglones. Después de este cambio dicen
 * los dos 15. El caso extremo es el que más se notaba: un dueño que solo creó cuentas con saldo
 * veía renglones mientras la guía le seguía diciendo «Registra un movimiento»; ahora ve el estado
 * vacío, que es lo que el resto de la app ya afirmaba.
 *
 * **La búsqueda es la excepción, a propósito.** Con una consulta escrita el saldo inicial vuelve a
 * aparecer: buscar es pedirlo explícitamente, y una app que no encuentra algo que sí existe es
 * peor que una que lo lista de más. La regla se cuelga de «hay consulta» y no de «la consulta dice
 * *saldo inicial*» para no depender de que el dueño adivine las palabras exactas: buscar el nombre
 * de la cuenta, o «deuda», o «inicial», llega igual. Lo que se elimina es que aparezca **sin que
 * la pida**.
 *
 * **Dónde se aplica, y por qué ahí.** La decisión vive acá, con las otras dos reglas, pero se
 * aplica en el cliente (`diasVisibles`, en `TransactionsScreen.kt`) y NO en `GET
 * /api/events/by-day` ni en `LocalRepository.getEventsByDay`. La razón es concreta: esos dos son
 * el mismo dato por dos caminos —con red y sin red— y filtrar en la capa de datos obligaría a
 * escribir la aplicación de la regla dos veces y a mantenerlas iguales para siempre. Aplicada
 * donde los dos caminos ya convergen, **no pueden** discrepar. Y los endpoints siguen devolviendo
 * la historia completa, que es lo correcto para sus otros consumidores (Presupuestos) y para
 * cualquiera que mañana necesite re-derivar un saldo.
 */
fun showsInMovements(event: FinancialEvent, query: String): Boolean =
    !isOpeningBalance(event) || query.isNotBlank()

/**
 * El rechazo de **escribir** [OPENING_CATEGORY] sobre un movimiento que no es una apertura.
 *
 * Ola 16 · hallazgo de la revisión, y es el peor de los dos sentidos. `PUT
 * /api/events/{id}/category` bloqueaba [TRANSFER_CATEGORY] y [ORPHANED_LEG_CATEGORY] pero no
 * esta, así que un **gasto real de $50.000** podía recibir la categoría «Saldo inicial»: el
 * server contestaba 200, `countsAsCashFlow` pasaba a `false` y «Gastos del mes» bajaba de
 * $165.289 a $115.289 sin que nada lo dijera. Medido contra el server local.
 *
 * Es exactamente el daño que la Ola 10 cerró en `POST /api/events` («el aviso era un cartel: se
 * cerraba el selector con la categoría puesta, el botón seguía habilitado, y el gasto quedaba
 * anotado y FUERA de "Gastos del mes"»), por la otra puerta. Y desde que [showsInMovements] saca
 * las aperturas de la lista, la fila envenenada además **desaparece de la vista** en vez de
 * quedar ahí sin signo: por eso la guarda entra en la misma rama que ese cambio.
 */
const val OPENING_CATEGORY_RESERVED: String =
    "«Saldo inicial» la escribe Movi sola cuando abres una cuenta con saldo. Ponérsela a un " +
        "movimiento tuyo lo sacaría de tus gastos e ingresos del mes sin dejar rastro."

/**
 * El rechazo de **sacar** un movimiento de [OPENING_CATEGORY].
 *
 * El sentido inverso del anterior, y confirmado igual contra el server local: recategorizar el
 * «Saldo inicial» de una cuenta de activo a «Otros ingresos» lo convertía de golpe en un ingreso
 * del mes —los ingresos pasaron de $0 a $3.000.000 en la medición— porque [isCashFlow] decide por
 * el nombre de la categoría. En una cuenta de deuda no se notaba (LOAN/CREDIT_CARD nunca son
 * flujo), lo que hacía al agujero más difícil de ver, no menos real.
 *
 * La apertura **no se recategoriza: se anula** (detalle de la cuenta) o se corrige con «Ajustar
 * saldo» en un crédito — los dos caminos que [OPENING_BALANCE_EXPLAINER] nombra.
 */
const val OPENING_RECATEGORIZE_BLOCKED: String =
    "El saldo inicial de una cuenta no cambia de categoría: es el punto de partida del saldo, no " +
        "un gasto ni un ingreso mal clasificado. Si el monto quedó mal, anúlalo desde el detalle " +
        "de la cuenta."
