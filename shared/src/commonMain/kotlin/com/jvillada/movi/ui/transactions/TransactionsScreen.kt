package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.ReminderChannelsCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.showsInMovements
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.recurrentes.ReminderWarningBanner
import com.jvillada.movi.ui.recurrentes.ResumenRecurrentes
import com.jvillada.movi.ui.recurrentes.SeccionProximosPagos
import com.jvillada.movi.ui.recurrentes.SeccionYaOcurrieron
import com.jvillada.movi.ui.recurrentes.candidatasSinConfirmar
import com.jvillada.movi.ui.recurrentes.claveDeNombre
import com.jvillada.movi.ui.recurrentes.claveDescartada
import com.jvillada.movi.ui.recurrentes.hayRecordatoriosPedidos
import com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe
import com.jvillada.movi.ui.recurrentes.nombresDeSuscripcionesQueYaSuman
import com.jvillada.movi.ui.recurrentes.ocurrenciasSelladas
import com.jvillada.movi.ui.recurrentes.proximosQueUrgen
import com.jvillada.movi.ui.recurrentes.resumenRecurrentes
import com.jvillada.movi.ui.recurrentes.shouldShowReminderWarning
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.LocalRefreshTick

/**
 * F13: filtro puro detrás de la búsqueda de Movimientos, separado del `@Composable` para poder
 * testearlo en `:shared:commonTest` sin arrancar Compose. Compara sin acentos ni mayúsculas
 * (mismo criterio que `CategoryField`, ver [normalizeForMatch]) contra descripción, comercio
 * (si lo hay) y categoría. Una consulta en blanco matchea todo — así el filtro es un no-op
 * mientras el campo de búsqueda está vacío.
 */
fun matchesQuery(event: FinancialEvent, query: String): Boolean {
    val q = normalizeForMatch(query.trim())
    if (q.isEmpty()) return true
    return normalizeForMatch(event.description).contains(q) ||
        event.merchant?.let { normalizeForMatch(it).contains(q) } == true ||
        normalizeForMatch(event.category).contains(q)
}

/**
 * Un renglón de Movimientos. No es siempre un evento: un **traspaso** son dos eventos (ver
 * [collapseTransfers]) que tienen que leerse como un solo hecho.
 */
sealed class MovementRow {
    /** Clave estable para `key` de la lista — el id del evento, o el del traspaso si son dos. */
    abstract val key: String

    data class Single(val event: FinancialEvent) : MovementRow() {
        override val key: String get() = event.id
    }

    /**
     * Las dos patas de un traspaso, juntas. [out] es siempre el EXPENSE (de dónde salió la
     * plata) e [into] el INCOME (a dónde entró) — no el orden en que vinieron en la lista.
     */
    data class Transfer(val out: FinancialEvent, val into: FinancialEvent) : MovementRow() {
        override val key: String get() = out.transferId ?: out.id
        val amount: Long get() = out.amount
    }
}

/** ¿Este movimiento es una pata de traspaso? */
fun isTransferLeg(event: FinancialEvent): Boolean =
    event.transferId != null || event.category == TRANSFER_CATEGORY

/**
 * Los días que Movimientos pinta, ya filtrados por el chip [chip] y la búsqueda [query], con el
 * total de cada uno **recalculado sobre lo que quedó**.
 *
 * Estaba en línea dentro del `@Composable` y se extrajo en la Ola 16 para poder medirlo: es la
 * pieza donde el filtro de aperturas se cruza con el total del día, y la pregunta que hay que
 * poder contestar con un test —«¿sacar la fila cambia alguna cifra?»— no se contesta mirando el
 * predicado suelto.
 *
 * El total sigue el mismo criterio que el del server (ver `EventRoutes` `/by-day`):
 * `countsAsCashFlow` deja fuera los movimientos de cuentas de deuda. Sin ese recálculo el
 * encabezado del día decía $0 en «Todo» y +$60.000.000 en «Ingresos» — el mismo número engañoso
 * que esa rama vino a matar, una pestaña más allá. Y por esa misma bandera **quitar una apertura
 * no puede mover el total**: ya estaba excluida de la suma.
 *
 * Un día que se queda sin filas se descarta entero (encabezado incluido): un día vacío con su
 * «Flujo del día» no le dice nada a nadie.
 *
 * @param reglas y @param nombresDeSuscripcionesActivas: lo que hace falta para el chip
 *   [CHIP_RECURRENTES] (ver [matchesChip] y [com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe]).
 *   Vacíos por defecto — ningún otro chip los necesita, y así los tests de los chips que ya
 *   existían no tienen que cambiar una línea.
 */
fun diasVisibles(
    days: List<EventDay>,
    chip: Int,
    query: String,
    reglas: List<RecurringRule> = emptyList(),
    nombresDeSuscripcionesActivas: List<String> = emptyList(),
): List<EventDay> =
    days.mapNotNull { day ->
        val filtered = day.items
            // Ola 16: la apertura de una cuenta no se lista salvo que la busquen — ver
            // [showsInMovements], que también explica por qué el filtro vive acá y no en
            // `/by-day` ni en `LocalRepository`.
            .filter { showsInMovements(it, query) }
            .filter { matchesChip(it, chip, reglas, nombresDeSuscripcionesActivas) }
            .filter { matchesQuery(it, query) }
        if (filtered.isEmpty()) null
        else day.copy(
            items = filtered,
            total = filtered.filter { it.countsAsCashFlow }.sumOf {
                if (it.type == TransactionType.EXPENSE) -it.amount else it.amount
            },
        )
    }

/**
 * ¿Este renglón es una **pata de traspaso que se quedó sin la otra mitad** porque el dueño borró
 * la cuenta de la otra punta? (ver `ORPHANED_LEG_CATEGORY` en `:core`).
 *
 * Se pregunta por la categoría y no por `transferId`, que es justamente lo que el borrado le
 * saca: para el resto de la app ya es un movimiento suelto. `isTransferLeg` de arriba, entonces,
 * dice `false` para esta fila — a propósito: no hay hermana con la que juntarla en un solo renglón
 * y sí se puede recategorizar, que son las dos cosas que aquella pregunta decide.
 */
fun isOrphanedTransferLeg(event: FinancialEvent): Boolean = event.category == ORPHANED_LEG_CATEGORY

/**
 * **De qué color va el monto de un renglón**: rojo si es plata que salió, verde si es plata que
 * entró, azul si fue de una cuenta suya a otra, gris si no fue ninguna de las tres cosas.
 *
 * Hasta acá el gasto iba del color del texto normal y solo el ingreso iba en verde, así que a
 * simple vista un día de puros gastos y un día sin nada se parecían. El dueño lo pidió tal cual:
 * *«que el color de cada movimiento indique rojo gasto / verde ingreso»*.
 *
 * `ENTRE_CUENTAS` se sumó después: el dueño, mirando traspasos y cuotas ya en gris, preguntó si
 * no merecían su propio color — *«Ingresos verde, gastos rojo, pagos de cuotas / traspasos otro
 * color?»*. Antes de esto, un traspaso y la pata huérfana de uno (o la apertura de una cuenta)
 * se veían exactamente igual: los dos NEUTRO, los dos grises. Ahora el gris queda para lo que de
 * verdad no tiene nada que contar (ver [tonoDelEvento]) y el azul es solo para lo que SÍ es un
 * hecho identificable — plata entre sus propias cuentas — así que las dos cosas dejan de
 * confundirse a simple vista.
 */
enum class TonoDelMonto {
    /** Plata que salió del bolsillo. Rojo y con «−». */
    GASTO,
    /** Plata que entró al bolsillo. Verde y con «+». */
    INGRESO,
    /** Plata que fue de una cuenta suya a otra: traspaso, cuota o pago de tarjeta. Azul y sin signo. */
    ENTRE_CUENTAS,
    /** No movió plata del bolsillo. Gris y sin signo. */
    NEUTRO,
}

/**
 * El tono de un movimiento suelto, decidido por **una sola bandera**: `countsAsCashFlow`.
 *
 * Esa bandera la deriva el server (y el espejo local) con `isCashFlow`, que es la misma regla con
 * la que se suman «Gastos del mes» e «Ingresos del mes». Por eso acá no se mira la categoría a
 * mano: este proyecto ya tuvo dos pantallas con su propia copia de «qué cuenta y qué no», y se le
 * desincronizaron. Lo que la regla deja afuera —y por eso va gris y sin signo— es la apertura de
 * una cuenta (Ola 8 · V6), la pata huérfana de un traspaso (Ola 15: el borrado de un crédito
 * desembolsado dejaba un «+$257.000.000» en verde bajo un total que no lo contaba), la pata de un
 * traspaso vivo que un filtro dejó sola, el pago de tarjeta, la cuota que paga un tercero y todo
 * lo que pasa en una cuenta de deuda. El monto se sigue viendo: el saldo de la cuenta sí se movió
 * y la fila no puede esconderlo.
 *
 * La pata del dinero de una **cuota de crédito** sí cuenta (`CUOTA_CATEGORY` no es reservada: el
 * dueño decidió que «es plata que salió»), así que cuando aparece suelta —en «Gastos», donde su
 * hermana de la deuda no entra— va en rojo, como el gasto que es.
 */
fun tonoDelEvento(event: FinancialEvent): TonoDelMonto = when {
    !event.countsAsCashFlow -> TonoDelMonto.NEUTRO
    event.type == TransactionType.INCOME -> TonoDelMonto.INGRESO
    else -> TonoDelMonto.GASTO
}

/**
 * El tono de un renglón. Un **par** —traspaso, cuota, pago de tarjeta, leído como un solo hecho—
 * es siempre `ENTRE_CUENTAS`, nunca gasto ni ingreso: la plata no entró ni salió, cambió de
 * cuenta. Ponerle un signo obligaría a elegir el punto de vista de una de las dos cuentas, que es
 * justo la confusión que el renglón doble vino a sacar (ver [TransferRow]) — pero eso solo dice
 * que no lleva signo, no que tenga que verse igual que un NEUTRO real (la apertura de una cuenta,
 * una pata huérfana): son un hecho identificable y el dueño los quiere distinguibles a simple
 * vista (ver [TonoDelMonto]).
 */
fun tonoDelRenglon(row: MovementRow): TonoDelMonto = when (row) {
    is MovementRow.Transfer -> TonoDelMonto.ENTRE_CUENTAS
    is MovementRow.Single -> tonoDelEvento(row.event)
}

/** ¿El renglón lleva signo y color de ingreso/gasto? Ver [tonoDelEvento]. */
fun rowShowsSign(event: FinancialEvent): Boolean = tonoDelEvento(event) != TonoDelMonto.NEUTRO

/**
 * ¿Este movimiento es plata que fue **de una cuenta suya a otra cuenta suya**?
 *
 * Son tres pares con la misma forma —traspaso, cuota de crédito y pago de tarjeta— más el pago
 * de tarjeta viejo, anotado suelto con la categoría reservada antes de que existiera la acción
 * de «Pagar cuota». Hasta acá caían todos en «Todo», sin signo y mezclados con lo demás, y el
 * dueño preguntó si no debería haber un filtro para el traspaso y otro para la cuota. Es uno
 * solo, porque para él son lo mismo: nada de esto es un gasto ni un ingreso, y es lo que quiere
 * mirar aparte cuando revisa si los saldos cuadran.
 *
 * **Es un filtro, no una reclasificación.** La cuota sigue contando en «Gastos» (ver
 * [tonoDelEvento]); acá solo se la agrupa además con sus pares. La pata huérfana no entra: la
 * otra cuenta ya no existe, así que dejó de ser «entre cuentas» y hoy se lee como un movimiento
 * suelto que se puede recategorizar.
 */
fun esEntreCuentas(event: FinancialEvent): Boolean =
    isTransferLeg(event) || event.category == CARD_PAYMENT_CATEGORY

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * El encabezado de un día: «HOY», «AYER», «23 DE AGOSTO» — nunca «2026-08-23».
 *
 * Ola 8 · V13: los encabezados mostraban la fecha ISO cruda que manda el server en
 * `EventDay.date`. Es la clave con la que se agrupa, no algo que alguien quiera leer.
 *
 * [hoy] entra por parámetro (y no se lee acá adentro) para que esto sea una función pura y
 * testeable sin relojes. Quien la llama pasa `todayIsoInAppZone()`, que ya resuelve la zona de
 * Bogotá con el plan B de `AppTimeZone`: en wasm no existe la base de zonas IANA y
 * `TimeZone.of("America/Bogota")` lanza, así que cae a UTC-5 fijo (exacto: Colombia no tiene
 * horario de verano). Acá abajo solo se compara y se resta un día con `LocalDate`, que es
 * aritmética de calendario pura — no toca la tabla de zonas y por eso no la puede romper.
 *
 * Un ISO que no se pueda parsear se devuelve tal cual: es preferible un encabezado feo a una
 * lista que no se pinta.
 */
fun formatDayHeading(iso: String, hoy: String): String {
    if (iso == hoy) return "Hoy"
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val hoyFecha = runCatching { LocalDate.parse(hoy) }.getOrNull()
    if (hoyFecha != null) {
        if (fecha == hoyFecha.minus(DatePeriod(days = 1))) return "Ayer"
        // El año solo se dice cuando NO es el corriente: repetir «de 2026» en cada
        // encabezado de 2026 es ruido.
        if (fecha.year != hoyFecha.year) return "${fecha.dayOfMonth} de ${MESES[fecha.monthNumber - 1]} de ${fecha.year}"
    }
    return "${fecha.dayOfMonth} de ${MESES[fecha.monthNumber - 1]}"
}

// Índices de los chips de arriba de Movimientos, con nombre para que el filtro de abajo (y sus
// tests) no dependan de recordar qué significaba el 1 y qué el 2.
const val CHIP_TODO = 0
const val CHIP_GASTOS = 1
const val CHIP_INGRESOS = 2
const val CHIP_POR_CONFIRMAR = 3
const val CHIP_ENTRE_CUENTAS = 4
/**
 * PR 1 del rediseño de Recurrentes (2026-09): el dueño pidió, palabra por palabra, poder «en esa
 * página revisar cuáles de todos los gastos por filtro o lo que sea son recurrentes» — este es
 * ese filtro. Ver [matchesChip] y [com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe].
 */
const val CHIP_RECURRENTES = 5

/** Los rótulos de los chips, en el orden de sus índices. */
val CHIPS_DE_MOVIMIENTOS = listOf("Todo", "Gastos", "Ingresos", "Por confirmar", "Entre cuentas", "Recurrentes")

/**
 * PR 2 del rediseño de Recurrentes (2026-09): ¿se pinta el card de «Flujo libre» y la sección de
 * candidatas por confirmar?
 *
 * Solo con el chip «Recurrentes» activo — en «Todo» o «Gastos» esas dos piezas hablan de una cosa
 * que no tiene nada que ver con lo que el chip pidió ver, y encima duplicarían el «Flujo libre»
 * que ya existía en la pantalla vieja: acá es un resumen DEL FILTRO, no un segundo total suelto
 * en medio de la lista. Función aparte (en vez de comparar `chip == CHIP_RECURRENTES` en el
 * `@Composable`) para poder testear la decisión sin montar Compose.
 */
fun mostrarResumenDeRecurrentes(chip: Int): Boolean = chip == CHIP_RECURRENTES

/**
 * PR 3 del rediseño de Recurrentes (2026-09): **con qué chip arranca Movimientos** cuando alguien
 * la abrió pidiendo uno.
 *
 * Existe porque los enlaces que antes llevaban a la pantalla de Recurrentes ahora llevan acá (el
 * «Ver todos» de Próximos pagos del Inicio, la campana, «Anota tus gastos recurrentes», los
 * targets SDUI). Si esos enlaces cayeran en Movimientos sin filtro, el dueño tocaría un pago que
 * vence y llegaría a la lista completa de sus movimientos, sin ninguna relación visible con lo que
 * acaba de tocar: el destino tiene que responder a lo que se tocó, no solo estar cerca.
 *
 * `null` —el caso normal, entrar por la pestaña— es «Todo». Un índice fuera de rango también cae
 * en «Todo» y no explota: el valor viaja adentro de [com.jvillada.movi.ui.Screen.Transactions], y
 * una pila restaurada o una definición SDUI vieja podrían traer un número que hoy no existe.
 * Arrancar en «Todo» ahí es la caída correcta — es la pantalla completa, no un filtro que esconde
 * cosas sin decirlo.
 */
fun chipInicialDeMovimientos(pedido: Int?): Int =
    if (pedido != null && pedido in CHIPS_DE_MOVIMIENTOS.indices) pedido else CHIP_TODO

/**
 * ¿Este movimiento entra en el chip [chip]?
 *
 * **«Gastos» e «Ingresos» son exactamente lo que suma el mes.** Hasta acá cada chip llevaba su
 * propia lista de exclusiones escrita a mano —pata de traspaso, apertura, pata huérfana— y esa
 * lista se fue quedando corta cada vez que `isCashFlow` aprendía una regla nueva: la cuota que
 * paga un tercero, el descuento de nómina y los intereses de un crédito seguían entrando a los
 * chips con signo y color, mientras el total del mes —correctamente— no los contaba. Y al revés:
 * la pata del dinero de una **cuota de crédito** lleva `transferId`, así que la exclusión de
 * «pata de traspaso» la sacaba de «Gastos», cuando el dueño decidió que la cuota SÍ es plata que
 * salió y el mes la suma. El chip decía una cosa y la cifra de arriba otra.
 *
 * Ahora los dos chips leen `countsAsCashFlow`, la bandera que el server deriva con la misma
 * `isCashFlow` que usa el mes. Se conservan, por construcción y no por lista, las decisiones que
 * ya estaban: las patas de un traspaso no aparecen ni en «Gastos» ni en «Ingresos» (cada chip
 * dejaba pasar UNA pata, [collapseTransfers] se quedaba sin la hermana y el traspaso volvía a
 * leerse como «−$500.000 · Traspaso»); la apertura de una cuenta no es un ingreso (Ola 8 · V6:
 * dos «Saldo inicial» en verde bajo un total que no los contaba); la pata huérfana tampoco
 * (Ola 15, con la cifra de un crédito entero). En «Todo» todo eso sí aparece, que es donde tiene
 * sentido verlo y donde se lo puede tocar.
 *
 * «Entre cuentas» es el cuarto filtro: los tres pares y el pago de tarjeta suelto, ver
 * [esEntreCuentas]. Las dos patas de cada par pasan, así que [collapseTransfers] las junta.
 *
 * «Recurrentes» es el quinto: lo que ya reconocemos como una regla o una suscripción confirmada,
 * ver [com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe] — con las mismas dos listas ya
 * cargadas, sin ningún viaje de red por fila (ese es el precio, y está documentado ahí).
 *
 * @param reglas y @param nombresDeSuscripcionesActivas solo los usa [CHIP_RECURRENTES]; el resto
 *   de los chips ni los mira, así que quedan con default vacío y no rompen ningún llamado viejo.
 */
fun matchesChip(
    event: FinancialEvent,
    chip: Int,
    reglas: List<RecurringRule> = emptyList(),
    nombresDeSuscripcionesActivas: List<String> = emptyList(),
): Boolean = when (chip) {
    CHIP_GASTOS -> event.type == TransactionType.EXPENSE &&
        event.countsAsCashFlow &&
        event.reconciliationStatus != ReconciliationStatus.UNCONFIRMED
    CHIP_INGRESOS -> event.type == TransactionType.INCOME && event.countsAsCashFlow
    CHIP_POR_CONFIRMAR -> event.reconciliationStatus == ReconciliationStatus.UNCONFIRMED
    CHIP_ENTRE_CUENTAS -> esEntreCuentas(event)
    CHIP_RECURRENTES -> nombreRecurrenteDe(event, reglas, nombresDeSuscripcionesActivas) != null
    else -> true
}

/**
 * Lo que dice la lista cuando **no quedó nada que mostrar**, según por qué no quedó nada.
 *
 * Hasta acá todo vacío decía «Sin movimientos aún · + Registrar el primero», que es el estado de
 * una cuenta recién abierta. Con el chip «Por confirmar» activo y nada por confirmar —que es el
 * caso normal de quien anota todo a mano— ese texto mentía dos veces: sí hay movimientos, y
 * registrar uno nuevo no tiene nada que ver con confirmar los que entraron solos. El dueño lo
 * leyó exactamente así: *«¿Qué es Por confirmar?»*.
 *
 * [ofreceRegistrar] solo cuando de verdad no hay nada anotado: es la única situación en la que
 * el botón contesta la pregunta que el vacío plantea.
 */
data class VacioDeMovimientos(
    val titulo: String,
    val detalle: String?,
    val ofreceRegistrar: Boolean,
)

fun vacioDeMovimientos(chip: Int, hayMovimientos: Boolean): VacioDeMovimientos = when {
    chip == CHIP_POR_CONFIRMAR -> VacioDeMovimientos(
        titulo = "Nada por confirmar",
        detalle = "Todo lo que hay lo registraste tú. Aquí caen los movimientos que entran solos, " +
            "por SMS o por extracto, hasta que los confirmes.",
        ofreceRegistrar = false,
    )
    !hayMovimientos -> VacioDeMovimientos("Sin movimientos aún", null, ofreceRegistrar = true)
    chip == CHIP_GASTOS -> VacioDeMovimientos(
        titulo = "Sin gastos",
        detalle = "Hay movimientos, pero ninguno es plata que salió del bolsillo.",
        ofreceRegistrar = false,
    )
    chip == CHIP_INGRESOS -> VacioDeMovimientos(
        titulo = "Sin ingresos",
        detalle = "Hay movimientos, pero ninguno es plata que entró al bolsillo.",
        ofreceRegistrar = false,
    )
    chip == CHIP_ENTRE_CUENTAS -> VacioDeMovimientos(
        titulo = "Nada entre cuentas",
        detalle = "Aquí van los traspasos, las cuotas de crédito y los pagos de tarjeta: plata que " +
            "fue de una cuenta tuya a otra.",
        ofreceRegistrar = false,
    )
    chip == CHIP_RECURRENTES -> VacioDeMovimientos(
        titulo = "Nada recurrente",
        detalle = "Aquí aparecen los movimientos que ya reconocemos como un recurrente que tienes " +
            "anotado o una suscripción confirmada.",
        ofreceRegistrar = false,
    )
    else -> VacioDeMovimientos("Sin movimientos aún", null, ofreceRegistrar = true)
}

/**
 * Junta las dos patas de cada traspaso en **un solo renglón**.
 *
 * Sin esto, mover $5.000.000 de Ahorros al CDT aparecía en Movimientos como un egreso de
 * $5.000.000 y un ingreso de $5.000.000 sin ninguna relación visible: dos renglones que se leen
 * como plata que se gastó y plata que llegó, cuando es la misma plata que cambió de cuenta. Es la
 * forma más simple de arreglarlo sin inventar una pantalla nueva: la lista sigue siendo una lista
 * de hechos, y un traspaso es un hecho.
 *
 * Se junta **por `transferId`**, no por "un egreso y un ingreso del mismo monto el mismo día":
 * el enlace es explícito justamente para que esto no sea una adivinanza que un día empareje dos
 * movimientos que no tenían nada que ver.
 *
 * El renglón queda en el lugar de la primera pata que aparecía en la lista, así el orden
 * cronológico no se altera. Y si de un traspaso solo se ve una pata —porque un chip o la búsqueda
 * filtró a la otra— se muestra suelta, con su descripción ("Traspaso a CDT"), en vez de
 * desaparecer: la lista tiene que seguir mostrando lo que el filtro pidió.
 */
fun collapseTransfers(items: List<FinancialEvent>): List<MovementRow> {
    val porTraspaso = items.filter { it.transferId != null }.groupBy { it.transferId!! }
    val yaEmitidos = mutableSetOf<String>()
    return items.mapNotNull { event ->
        val transferId = event.transferId
        if (transferId == null) return@mapNotNull MovementRow.Single(event)
        val patas = porTraspaso[transferId].orEmpty()
        val salida = patas.firstOrNull { it.type == TransactionType.EXPENSE }
        val entrada = patas.firstOrNull { it.type == TransactionType.INCOME }
        if (salida == null || entrada == null) return@mapNotNull MovementRow.Single(event)
        if (!yaEmitidos.add(transferId)) null
        else MovementRow.Transfer(out = salida, into = entrada)
    }
}

/**
 * "De Ahorros a CDT": de qué cuenta a qué cuenta se movió la plata.
 *
 * Con palabras y no con una flecha: en wasm «→» sale como ▯ (la fuente del canvas no trae ese
 * glifo — el mismo problema que ya obligó a reemplazar el «›» por un ícono Material, ver
 * `ChevronRight`), y verificado en la web local antes de este cambio.
 *
 * Los nombres salen del mapa de cuentas y no de la descripción de las patas: si la lista de
 * cuentas todavía no llegó, se dicen los roles ("De Origen a Destino") en vez de inventar un
 * nombre que después resulte ser otro. Desde la Ola 8 (V7) el MISMO mapa alimenta el subtítulo
 * de un evento suelto — ver [MovementSingleRow]—, que antes no decía de qué cuenta era.
 */
fun transferRowSubtitle(row: MovementRow.Transfer, accountNames: Map<String, String>): String {
    val origen = accountNames[row.out.accountId] ?: "Origen"
    val destino = accountNames[row.into.accountId] ?: "Destino"
    val base = "De $origen a $destino"
    // **Cuando las dos patas NO valen lo mismo, el renglón lo dice.**
    //
    // El monto grande de la derecha es el de la pata del dinero: la cuota entera, que es la plata
    // que de verdad salió. Pero desde que la deuda baja solo por el capital (ver
    // `DesgloseDeCuota`), un renglón que muestre $1.286.548 y nada más estaría afirmando que la
    // deuda bajó $1.286.548 — el mismo número plausible y falso que esta ola vino a matar, ahora
    // en la lista. La diferencia son los intereses y el seguro del mes.
    if (row.out.amount == row.into.amount) return base
    return "$base · abona ${formatMoney(row.into.amount, row.into.currency)} a capital"
}

/**
 * **Cómo se llama este renglón** — «Traspaso», «Desembolso» o «Abono extraordinario».
 *
 * Ola 14: desde que un crédito puede ser una de las dos puntas ([validateTransfer]), «Traspaso» a
 * secas dejó de alcanzar. Los tres hechos se guardan igual —dos patas con la categoría reservada,
 * fuera del mes— pero para el dueño no son lo mismo: **un desembolso es plata prestada que le
 * entró** y un abono extraordinario **es capital que pagó de más**. Confundir el segundo con la
 * cuota mensual (que sí se anota como gasto normal y sí cuenta en el mes) es el error que este
 * nombre evita a la vista, sin abrir el renglón.
 *
 * Sale de los **tipos de cuenta**, no de la descripción de las patas: la descripción es texto que
 * quedó guardado el día del traspaso y podría venir de una versión anterior de la app; el tipo de
 * la cuenta es el dato de hoy. Si la lista de cuentas todavía no llegó, el mapa está vacío y se
 * dice «Traspaso», que es lo que ya se decía — nunca un nombre inventado.
 */
fun transferRowTitle(row: MovementRow.Transfer, accountTypes: Map<String, AccountType>): String = when {
    // **La categoría manda sobre el tipo de cuenta, y va primero.** Un pago de cuota también es un
    // par con la pata de entrada en una cuenta LOAN, así que caía en «Abono extraordinario»: la
    // cuota mensual rotulada justo como lo contrario de lo que es, que es la confusión que este
    // nombre existe para evitar. La categoría la escribe `pagoDeCuotaLegs` y no hay otra forma de
    // llegar a ella, así que distingue exacto.
    row.out.category == CUOTA_CATEGORY -> "Cuota de crédito"
    row.out.category == CARD_PAYMENT_CATEGORY -> "Pago de tarjeta"
    accountTypes[row.out.accountId] == AccountType.LOAN -> "Desembolso"
    accountTypes[row.into.accountId] == AccountType.LOAN -> "Abono extraordinario"
    else -> "Traspaso"
}

/**
 * Minúsculas y sin tildes/eñe — mismo criterio que usa `CategoryField` (F35) para sus
 * sugerencias, pero definido acá aparte: esta pantalla no puede tocar `CategoryField.kt` en esta
 * tarea (Ola 4 la reserva para otro trabajo en paralelo), así que se duplica el normalizador en
 * vez de extraerlo a un helper compartido.
 */
private fun normalizeForMatch(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) {
        append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ñ' -> 'n'
                else -> c
            },
        )
    }
}

@Composable
fun TransactionsScreen(onNavigate: (Screen) -> Unit, chipInicial: Int? = null) {
    // Con qué chip arranca — ver [chipInicialDeMovimientos]. `remember(chipInicial)` y no
    // `remember { }` a secas: si se vuelve a entrar pidiendo otro filtro, el estado tiene que
    // rearrancar en el que se pidió, no quedarse con el de la visita anterior.
    var activeFilter by remember(chipInicial) { mutableStateOf(chipInicialDeMovimientos(chipInicial)) }
    // F13: la lupa era un dibujo sin acción — ahora despliega un campo que filtra en memoria
    // mientras se escribe (no hay ida al servidor: la lista ya está en pantalla).
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    // F12: "Pendientes" no decía qué es — son los movimientos que entraron solos (SMS, OCR,
    // extracto) y esperan que confirmes monto y categoría. "Por confirmar" sí lo dice.
    val filters = CHIPS_DE_MOVIMIENTOS
    // Los días que el dueño plegó, por fecha ISO. Se recuerdan entre visitas: ver
    // [DiasPlegadosStore] para el porqué.
    var diasPlegados by remember { mutableStateOf(DiasPlegadosStore.plegados()) }
    val listState = rememberLazyListState()
    // Pantalla ancha: la rueda del mouse sobre los márgenes, a los lados de la columna, también
    // tiene que mover esta lista. Ver [ScrollDesdeLosMargenes].
    ScrollDesdeLosMargenes(listState)

    var allDays by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // F10: el estado vacío necesita saber si hay cuentas para elegir entre "+ Registrar el
    // primero" y "Crear una cuenta primero" — sin esto no hay forma de saber si el problema es
    // "no hay movimientos" o "no hay dónde anotarlos".
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    // Solo se ofrece «Crear una cuenta primero» cuando la lista llegó y vino vacía; mientras
    // carga (o si falló) se ofrece registrar, que es la acción segura.
    var accountsLoaded by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando la lista de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(refreshKey, refreshTick) {
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { accounts = it; accountsLoaded = true }
    }
    // Los nombres de las cuentas, para que el renglón de un traspaso diga de dónde a dónde fue la
    // plata (ver [transferRowSubtitle]). Un evento suelto no los necesita: la cuenta no se muestra.
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    // Y sus tipos, para que el renglón de un traspaso con un crédito de una punta se llame
    // «Desembolso» o «Abono extraordinario» en vez de «Traspaso» (ver [transferRowTitle]).
    val accountTypes = remember(accounts) { accounts.associate { it.id to it.type } }
    // V13: «hoy» en la zona de la app (Bogotá), para que los encabezados digan «HOY»/«AYER».
    // Se calcula una vez por composición y no dentro del bucle de días.
    val hoyIso = remember { todayIsoInAppZone() }

    // Candidatos a pago de tarjeta sin marcar (Task 2 del plan): entrada opcional, así que un
    // fallo al traerlos no debe tapar Movimientos con un snackbar.
    var candidates by remember { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    // Los que el dueño ya resolvió en esta pantalla — confirmados con "Marcar" o descartados con
    // "No es", mismo tratamiento para los dos. Se descuentan de `candidates` porque el refetch
    // puede fallar y dejar la lista vieja: sin esto, un pago recién resuelto volvía a aparecer
    // con sus botones activos y el contador seguía diciendo el número de antes — o sea, la app le
    // decía que su acción no se había guardado, cuando sí se guardó. Para "No es" en particular,
    // sin este descuento el falso positivo revivía en cuanto el refetch fallaba.
    var resolvedIds by remember { mutableStateOf(emptySet<String>()) }
    val pendingCandidates = candidates.filterNot { it.id in resolvedIds }
    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
    var showCandidatesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getEventsByDay() }
            .onSuccess {
                allDays = it
                // F35: de paso, alimenta el caché de "categorías ya usadas" que lee
                // CategoryField — esta pantalla ya carga los movimientos. Ola 9 · A3: con el
                // tipo de cada uno, para poder ofrecerlas del lado correcto.
                UsedCategoriesCache.recordAll(it.flatMap { d -> d.items }.map { ev -> ev.category to ev.type })
            }
            .onFailure { e -> error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(refreshKey, refreshTick) {
        runCatching { Repositories.wallets.getCardPaymentCandidates() }
            .onSuccess { candidates = it }
    }

    // PR 1 del rediseño de Recurrentes: lo que hace falta para el chip «Recurrentes» y la marca
    // en cada fila (ver [nombreRecurrenteDe]). Sale del MISMO cache que ya usa la barra de
    // «¿esto se repite?» de después de guardar — ver [RecurringOfferGate.listasParaMovimientos]:
    // si esta pantalla es la primera en pedirlas esta sesión, las carga UNA vez; si ya las cargó
    // otra pantalla (o esta misma en una visita anterior), no hay ningún viaje de red de más.
    var reglasRecurrentes by remember { mutableStateOf<List<RecurringRule>>(emptyList()) }
    var nombresDeSuscripcionesActivas by remember { mutableStateOf<List<String>>(emptyList()) }
    // Sube tras cada Confirmar / No es / Buscar cobros, para volver a traer las listas sin
    // esperar a `refreshKey` (que dispararía además una recarga innecesaria de los movimientos).
    var recurrentesReloadKey by remember { mutableStateOf(0) }
    // `recurrentesReloadKey` también es clave acá, y no solo de las candidatas: confirmar una
    // candidata la vuelve un cobro ACTIVO, y de eso dependen el filtro del chip y la marca de
    // cada fila (ver [nombreRecurrenteDe]). Sin esta clave, el dueño confirmaba «Netflix» y sus
    // movimientos seguían sin reconocerse hasta salir de la pantalla y volver a entrar.
    LaunchedEffect(refreshKey, refreshTick, recurrentesReloadKey) {
        val (reglas, cobros) = RecurringOfferGate.listasParaMovimientos()
        reglasRecurrentes = reglas
        nombresDeSuscripcionesActivas = nombresDeSuscripcionesQueYaSuman(cobros)
    }

    // PR 2 del rediseño de Recurrentes: el «Flujo libre» y las candidatas «por confirmar» que
    // vivían solo en la pantalla vieja. A diferencia de `reglasRecurrentes` de arriba —que solo
    // necesita reconocer un nombre, y ahí una lista de hace un rato no hace daño— acá el dueño
    // viene a hacer algo con lo que ve (confirmar o descartar una candidata), así que se trae
    // FRESCO cada vez que el chip se activa, sin pasar por el cache de `RecurringOfferGate`: una
    // candidata que el detector ya encontró en otra sesión, o que otro dispositivo ya resolvió,
    // tiene que verse tal cual está, no la última que ese cache recuerde. Es el mismo endpoint
    // que `listasParaMovimientos` ya usa; la diferencia es que acá SÍ se repite la llamada.
    var subsParaRecurrentes by remember { mutableStateOf(SubscriptionsResult(emptyList(), 0)) }
    var subsParaRecurrentesOk by remember { mutableStateOf(false) }
    // Ids con una acción en vuelo, para no dejar tocar dos veces la misma candidata mientras se
    // guarda — mismo motivo que `marcando` en la pantalla vieja.
    var candidatasEnVuelo by remember { mutableStateOf<Set<String>>(emptySet()) }
    var buscandoCobros by remember { mutableStateOf(false) }
    val coroutineRecurrentes = rememberCoroutineScope()

    LaunchedEffect(activeFilter, recurrentesReloadKey, refreshTick) {
        if (activeFilter != CHIP_RECURRENTES) return@LaunchedEffect
        runCatching { Repositories.wallets.getSubscriptions() }
            .onSuccess {
                subsParaRecurrentes = it
                subsParaRecurrentesOk = true
                // El gate queda con lo mismo que se acaba de traer — igual que hace el
                // re-escaneo de la pantalla vieja (`rescan()`), para que la barra de «¿esto se
                // repite?» de después de guardar no vuelva a proponer una candidata recién
                // confirmada acá.
                RecurringOfferGate.recordarLoQueYaHay(reglas = null, suscripciones = it.subscriptions)
            }
            .onFailure { error = it.toUserMessage() }
    }

    // PR 3 del rediseño de Recurrentes: los vencimientos y «¿esto ya ocurrió?», la última pieza
    // que solo vivía en la pantalla vieja. Misma disciplina que las candidatas de arriba: se
    // traen FRESCAS con el chip activo (acá el dueño viene a sellar un periodo, no a mirar) y se
    // vuelven a traer tras cada marca con `recurrentesReloadKey`.
    var upcomingRecurrentes by remember { mutableStateOf<List<UpcomingPayment>>(emptyList()) }
    var vencimientosOk by remember { mutableStateOf(false) }
    var ocurrencias by remember { mutableStateOf<List<OccurrenceState>>(emptyList()) }
    var ocurrenciasOk by remember { mutableStateOf(false) }
    // Lo que el dueño rechazó con «no fue este», mientras dure esta pantalla. Las claves son
    // (regla, movimiento) — ver [claveDescartada]. No se persiste: rechazar una propuesta no es un
    // hecho sobre su plata, a diferencia de confirmarla.
    var descartadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Reglas con una marca en vuelo. Un conjunto y no un id: sellar el salario no puede congelar
    // el «Ya lo pagué» del arriendo — son dos hechos independientes.
    var marcando by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Para el aviso ámbar de «pediste que te recordemos y no tenemos por dónde». Ver
    // [shouldShowReminderWarning]: `canales == null` es «todavía no se sabe» y ahí NO se avisa.
    var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
    var pushRefreshTick by remember { mutableStateOf(0) }
    val canalesDeAviso = ReminderChannelsCache.canales
    /**
     * La regla que el dueño pidió editar tocando su renglón en «Próximos».
     *
     * En la pantalla vieja ese toque abría la hoja de editar (`RecurrentesScreen.editar()`), y esa
     * es la única acción que la fila prometía: relocalizarla como «no hace nada» habría sido
     * perder función, y mandarla a la pantalla vieja habría sido justo lo que este PR viene a
     * terminar. Es la misma hoja, en modo edición, que ya abre [HojaDelMovimiento] desde el
     * detalle de un movimiento (PR 1).
     *
     * La regla que se pasa sale de `upcomingRecurrentes`, que se recarga al activar el chip y tras
     * cada cambio (`recurrentesReloadKey`) — la precaución que `editar()` documentaba: prellenar el
     * formulario con una fila vieja hace que «Guardar cambios» reescriba lo que el dueño ya había
     * corregido.
     */
    var reglaRecurrenteAEditar by remember { mutableStateOf<RecurringRule?>(null) }

    LaunchedEffect(activeFilter, recurrentesReloadKey, refreshTick) {
        if (activeFilter != CHIP_RECURRENTES) return@LaunchedEffect
        ReminderChannelsCache.cargar()
        // En paralelo, como las hace la pantalla vieja: en serie son dos viajes encadenados y la
        // sección se queda a medias el doble de tiempo.
        coroutineScope {
            val porVencer = async { runCatching { Repositories.wallets.getUpcomingPayments() } }
            val porOcurrir = async { runCatching { Repositories.wallets.getOccurrenceStates() } }
            porVencer.await()
                .onSuccess { upcomingRecurrentes = it; vencimientosOk = true }
                .onFailure { error = it.toUserMessage() }
            // Si esta falla no se pinta ninguna propuesta ni ninguna marca: la sección se ve como
            // antes de que existiera. Un «ya ocurrió» que en realidad no se pudo leer sería una
            // afirmación sin respaldo, que es lo único que esta pieza no puede permitirse.
            porOcurrir.await()
                .onSuccess { ocurrencias = it; ocurrenciasOk = true }
                .onFailure { if (error == null) error = it.toUserMessage() }
        }
    }

    // El flujo de permisos del navegador es async (moviPush.js): tras pedirlo se refresca unas
    // veces para que el aviso desaparezca sin reabrir la app. Solo donde el push existe Y con el
    // chip activo — en Android/iOS `status()` es una constante, y en el resto de Movimientos este
    // bucle no tendría a quién servir.
    if (PushOptIn.supported) {
        LaunchedEffect(pushRefreshTick, activeFilter) {
            if (activeFilter != CHIP_RECURRENTES) return@LaunchedEffect
            repeat(20) {
                kotlinx.coroutines.delay(600)
                pushStatus = PushOptIn.status()
            }
        }
    }

    // Las CIFRAS solo se pintan con la fuente fresca ya cargada — un total a medias es peor que
    // ningún total, mismo criterio que la pantalla vieja (`reglasOk && cobrosOk`).
    val resumenRecurrentesDelChip = if (subsParaRecurrentesOk) {
        resumenRecurrentes(reglasRecurrentes, subsParaRecurrentes)
    } else null
    val candidatasRecurrentes = remember(subsParaRecurrentes) {
        candidatasSinConfirmar(subsParaRecurrentes.subscriptions)
    }
    // Para avisar en una candidata que el dueño ya la tiene anotada a mano, antes de confirmarla.
    val clavesDeReglasRecurrentes = remember(reglasRecurrentes) {
        reglasRecurrentes.map { claveDeNombre(it.name) }.toSet()
    }

    // «Próximos» muestra lo que URGE, no todas las reglas: el server manda una entrada por regla
    // (ver [proximosQueUrgen]). Y lo ya sellado va aparte, con su «Deshacer» — apenas se sella, el
    // recurrente desaparece de «Próximos», así que sin esa sección marcar por error no tendría
    // vuelta atrás hasta el mes siguiente (ver [SeccionYaOcurrieron]).
    val proximosRecurrentes = remember(upcomingRecurrentes) { proximosQueUrgen(upcomingRecurrentes) }
    val selladasRecurrentes = remember(upcomingRecurrentes, ocurrencias, ocurrenciasOk) {
        if (ocurrenciasOk) ocurrenciasSelladas(upcomingRecurrentes, ocurrencias) else emptyList()
    }
    // El aviso ámbar mira lo que se PIDIÓ, no lo que existe: promete una promesa rota, y sin
    // promesa no hay nada que anunciar. Ver [hayRecordatoriosPedidos].
    val pidieronRecordatorios = hayRecordatoriosPedidos(upcomingRecurrentes)

    /**
     * Sellar «esto ya ocurrió» — con el movimiento que el dueño confirmó, o sin ninguno.
     *
     * Después de esto el recurrente deja de leerse como vencido y deja de avisar **ese mes**: su
     * vencimiento vigente pasa a ser el del mes que viene (lo decide el server, ver `dueDateFor`).
     * Al mes siguiente vuelve a estar pendiente solo.
     */
    fun marcarOcurrio(ruleId: String, period: String, eventId: String?) {
        if (ruleId in marcando) return
        marcando = marcando + ruleId
        coroutineRecurrentes.launch {
            runCatching { Repositories.wallets.markOccurrence(ruleId, period, eventId) }
                .onSuccess { recurrentesReloadKey++ }
                .onFailure { error = it.toUserMessage() }
            marcando = marcando - ruleId
        }
    }

    /** Deshacer: marcar por error tiene que poder revertirse sin ceremonia. */
    fun deshacerOcurrio(ruleId: String, period: String) {
        if (ruleId in marcando) return
        marcando = marcando + ruleId
        coroutineRecurrentes.launch {
            runCatching { Repositories.wallets.unmarkOccurrence(ruleId, period) }
                .onSuccess { recurrentesReloadKey++ }
                .onFailure { error = it.toUserMessage() }
            marcando = marcando - ruleId
        }
    }

    /**
     * **Volver a barrer los movimientos buscando cobros que se repiten.**
     *
     * Se mudó acá con el resto de Recurrentes, y no era opcional: el barrido automático corre en
     * UN solo lugar del server —después de importar un extracto (`StatementRoutes`)— y el día a
     * día del dueño entra por SMS, que nunca lo dispara. Sin este botón, sacar la pantalla vieja
     * del menú dejaba el detector sin ninguna forma de correr para el camino que él más usa.
     *
     * Vive junto al resumen y no dentro de «Detectadas · por confirmar»: esa sección solo existe
     * cuando YA hay candidatas, y buscar cobros es justamente lo que se hace cuando no hay
     * ninguna todavía.
     */
    fun buscarCobros() {
        if (buscandoCobros) return
        buscandoCobros = true
        error = null
        coroutineRecurrentes.launch {
            runCatching { Repositories.wallets.detectSubscriptions() }
                .onSuccess {
                    subsParaRecurrentes = it
                    subsParaRecurrentesOk = true
                    // Un barrido puede DESCUBRIR cobros: el gate tiene que enterarse, o la barra
                    // de «¿esto se repite?» ofrecería una regla que duplica uno recién detectado.
                    RecurringOfferGate.recordarLoQueYaHay(reglas = null, suscripciones = it.subscriptions)
                    // Y las listas del chip también, que es lo que decide qué filas se reconocen.
                    recurrentesReloadKey++
                }
                .onFailure { error = it.toUserMessage() }
            buscandoCobros = false
        }
    }

    fun confirmarCandidata(sub: Subscription, status: SubStatus) {
        if (sub.id in candidatasEnVuelo) return
        candidatasEnVuelo = candidatasEnVuelo + sub.id
        coroutineRecurrentes.launch {
            runCatching { Repositories.wallets.updateSubscription(sub.id, sub.copy(status = status)) }
                .onSuccess { RecurringOfferGate.olvidarLoCacheado(); recurrentesReloadKey++ }
                .onFailure { error = it.toUserMessage() }
            candidatasEnVuelo = candidatasEnVuelo - sub.id
        }
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    val visibleDays = remember(activeFilter, allDays, searchQuery, reglasRecurrentes, nombresDeSuscripcionesActivas) {
        diasVisibles(allDays, activeFilter, searchQuery, reglasRecurrentes, nombresDeSuscripcionesActivas)
    }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // F60: encabezado único — Movimientos es raíz: avatar + rótulo del menú + la lupa.
        MinScreenHeader(
            title = "Movimientos",
            leading = HeaderLeading.Avatar(onClick = { onNavigate(Screen.Profile) }),
            action = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MinTextDim,
                    modifier = Modifier.size(22.dp).clickable {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                )
            },
        )
        Spacer(Modifier.height(12.dp))

        // F13: campo de búsqueda, debajo del encabezado — solo aparece al tocar la lupa.
        if (searchActive) {
            LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MinTextFaint, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Descripción, comercio o categoría", fontSize = 14.sp, color = MinTextFaint)
                    }
                    // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                    // [esAtajoDeSeleccionarTodo].
                    val campo = rememberCampoConSeleccion(searchQuery) { searchQuery = it }
                    BasicTextField(
                        value = campo.valor,
                        onValueChange = campo::alCambiar,
                        singleLine = true,
                        textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                        cursorBrush = SolidColor(MinText),
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar búsqueda",
                    tint = MinTextMute,
                    modifier = Modifier.size(16.dp).clickable {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { i, f ->
                val isActive = i == activeFilter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                        .then(
                            if (!isActive) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { activeFilter = i }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isActive) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = f,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) MinText else MinTextDim,
                    )
                }
            }
        }

        // Entrada discreta a los candidatos de pago de tarjeta: solo aparece si hay algo que
        // proponer, y abre una lista donde cada uno se confirma por separado (ver
        // CardPaymentCandidatesSheet) — nunca se marcan todos de una.
        if (pendingCandidates.isNotEmpty()) {
            MinCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                variant = MinCardVariant.Default,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                onClick = { showCandidatesSheet = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (pendingCandidates.size == 1) "1 pago de tarjeta sin marcar"
                               else "${pendingCandidates.size} pagos de tarjeta sin marcar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                    )
                    ChevronRight()
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            // PR 2 del rediseño de Recurrentes: el resumen del filtro y lo que falta revisar.
            // Solo con el chip activo — ver [mostrarResumenDeRecurrentes] — y ARRIBA de la lista
            // de días (que acá abajo son los movimientos que YA se reconocen como recurrentes;
            // esto es lo que resume ese total y lo que todavía no se confirmó ni descartó).
            if (mostrarResumenDeRecurrentes(activeFilter)) {
                // ── El orden: primero lo que pide algo, después lo que solo informa ──────
                //
                // El dueño abre este chip para responder «¿qué se repite, y qué necesita algo de
                // mí?». Así que arriba va lo accionable —el aviso de que sus recordatorios no van
                // a llegar, los pagos que urgen con su «¿ya ocurrió?», las candidatas por
                // confirmar— y el resumen pasivo queda abajo, pegado a la lista de movimientos
                // que resume. En el orden anterior (PR 2) el «Flujo libre» era lo único que había
                // y por eso encabezaba; con la mudanza del PR 3, dejar una cifra que no pide nada
                // por encima de una propuesta abierta sería enterrar lo urgente bajo lo bonito.

                // ── Aviso: pediste recordatorios y no hay por dónde mandártelos ──────────
                // Se muda con el resto: era la única pantalla que lo mostraba, y sacarla del menú
                // lo habría dejado sin ningún lugar donde salir. No es hipotético — hoy no hay
                // ninguna suscripción de push activa. Ver [shouldShowReminderWarning]: con
                // `canales == null` («todavía no se sabe») NO se avisa nada.
                if (shouldShowReminderWarning(pushStatus, pidieronRecordatorios, canalesDeAviso)) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                            ReminderWarningBanner(
                                pushStatus = pushStatus,
                                onEnable = {
                                    PushOptIn.enable()
                                    pushRefreshTick++
                                },
                            )
                        }
                    }
                }

                // ── Próximos + «¿esto ya ocurrió?» ──────────────────────────────────────
                item {
                    SeccionProximosPagos(
                        proximos = proximosRecurrentes,
                        ocurrencias = ocurrencias,
                        ocurrenciasOk = ocurrenciasOk,
                        descartadas = descartadas,
                        marcando = marcando,
                        // «Todavía no llegó la lista», no «la pantalla está cargando»: mientras
                        // no haya respuesta no se dibuja una tarjeta vacía que diga que no hay
                        // nada por vencer, porque eso no se sabe todavía.
                        cargando = !vencimientosOk,
                        conteoVisible = vencimientosOk,
                        onAbrirPago = { payment ->
                            // F20: la cuota de un crédito y el pago de una tarjeta son reglas
                            // sintéticas del server, no algo que se edite acá — se gestionan en
                            // Créditos. Misma distinción que hacía la pantalla vieja.
                            if (payment.rule.id.startsWith(CREDIT_RULE_PREFIX) ||
                                payment.rule.id.startsWith(CARD_RULE_PREFIX)
                            ) {
                                onNavigate(Screen.Credits)
                            } else {
                                reglaRecurrenteAEditar = payment.rule
                            }
                        },
                        onMarcar = { ruleId, period, eventId -> marcarOcurrio(ruleId, period, eventId) },
                        onDescartarPropuesta = { ruleId, eventId ->
                            descartadas = descartadas + claveDescartada(ruleId, eventId)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    )
                }

                // ── Detectadas · por confirmar ──────────────────────────────────────────
                if (candidatasRecurrentes.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                            MinSectionHeader(title = "Detectadas · por confirmar", count = candidatasRecurrentes.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                candidatasRecurrentes.forEach { s ->
                                    CandidataSuscripcionCard(
                                        sub = s,
                                        yaEsRegla = claveDeNombre(s.displayName) in clavesDeReglasRecurrentes,
                                        enVuelo = s.id in candidatasEnVuelo,
                                        onConfirmar = { confirmarCandidata(s, SubStatus.CONFIRMED) },
                                        onDescartar = { confirmarCandidata(s, SubStatus.DISMISSED) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Ya ocurrieron · con su «Deshacer» ───────────────────────────────────
                if (selladasRecurrentes.isNotEmpty()) {
                    item {
                        SeccionYaOcurrieron(
                            selladas = selladasRecurrentes,
                            marcando = marcando,
                            onDeshacer = { ruleId, period -> deshacerOcurrio(ruleId, period) },
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        )
                    }
                }

                // ── El resumen, abajo, pegado a lo que resume ───────────────────────────
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        // «Buscar cobros» va en este encabezado —que se pinta siempre con el chip
                        // activo— y no en el de las candidatas, que solo existe cuando ya hay
                        // alguna. Ver [buscarCobros].
                        MinSectionHeader(
                            title = "Recurrentes",
                            action = if (buscandoCobros) "Buscando…" else "Buscar cobros",
                            onAction = { buscarCobros() },
                        )
                        ResumenFlujoLibreCard(resumenRecurrentesDelChip)
                    }
                }
            }

            if (!loading && visibleDays.isEmpty()) {
                item {
                    if (searchQuery.isNotBlank()) {
                        // F13: nada que ver acá con "no hay dónde anotar" — la búsqueda no dio
                        // resultados, así que el texto buscado es la pista que hace falta.
                        Column(
                            modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Nada coincide con \"${searchQuery.trim()}\"",
                                fontSize = 14.sp,
                                color = MinTextMute,
                            )
                        }
                    } else {
                        // F10: el estado vacío ofrece la acción — registrar si ya hay dónde
                        // anotar, crear una cuenta primero si no. Pero solo cuando el vacío es
                        // «no hay nada anotado»: si es un chip el que dejó la lista vacía, se
                        // dice eso y no se ofrece nada (ver [vacioDeMovimientos]).
                        val hayMovimientos = allDays.any { d -> d.items.any { !isOpeningBalance(it) } }
                        val vacio = vacioDeMovimientos(activeFilter, hayMovimientos)
                        Column(
                            modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp).padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(vacio.titulo, fontSize = 14.sp, color = MinTextMute)
                            vacio.detalle?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    color = MinTextFaint,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp,
                                )
                            }
                            if (vacio.ofreceRegistrar) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MinPrimaryContainer)
                                        .clickable {
                                            if (accounts.isNotEmpty() || !accountsLoaded) onNavigate(Screen.QuickAdd())
                                            else showCreateSheet = true
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (accounts.isNotEmpty() || !accountsLoaded) "+ Registrar el primero" else "Crear una cuenta primero",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MinOnPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            visibleDays.forEach { day ->
                // Sin `key`: con la fecha como clave, `LazyColumn` ancla el primer día visible al
                // cambiar de chip, y pasar de «Gastos» a «Todo» dejaba el día nuevo de arriba
                // escondido por encima del tope (visto a ojo en la web). Posicional, como antes.
                item {
                    val rows = collapseTransfers(day.items)
                    val plegado = day.date in diasPlegados
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                        // El encabezado entero es el botón que pliega y despliega el día. Plegado
                        // sigue diciendo el «Flujo del día» y cuántos renglones esconde: plegar
                        // es para acortar la lista, no para perder la información.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { diasPlegados = DiasPlegadosStore.alternar(day.date) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (plegado) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = if (plegado) "Desplegar el día" else "Plegar el día",
                                    tint = MinTextFaint,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    // V13: «23 DE AGOSTO» / «HOY», no la clave ISO del server.
                                    text = formatDayHeading(day.date, hoyIso).uppercase(),
                                    fontSize = 11.sp,
                                    color = MinTextMute,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                )
                                if (plegado) {
                                    Text(
                                        text = if (rows.size == 1) "· 1 movimiento" else "· ${rows.size} movimientos",
                                        fontSize = 11.sp,
                                        color = MinTextFaint,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // V6: el total del día suma el FLUJO (lo que entró y salió del
                                // bolsillo), no todos los renglones de abajo — la apertura de
                                // una cuenta queda afuera. Decirlo con todas las letras cuesta
                                // dos palabras y evita que la cifra parezca una suma mal hecha.
                                Text(
                                    text = "Flujo del día",
                                    fontSize = 11.sp,
                                    color = MinTextFaint,
                                )
                                Text(
                                    text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total)}",
                                    fontSize = 11.sp,
                                    color = MinTextMute,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        if (!plegado) MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            rows.forEachIndexed { i, row ->
                                Column {
                                    when (row) {
                                        is MovementRow.Transfer -> TransferRow(
                                            row = row,
                                            accountNames = accountNames,
                                            accountTypes = accountTypes,
                                            // Al tocarlo se abre la hoja de categoría sobre la
                                            // pata de origen — que se niega a recategorizar y
                                            // explica por qué. Es la única acción que un traspaso
                                            // ofrece hoy desde acá, y es mejor que un renglón
                                            // muerto que no responde al toque.
                                            onClick = { selectedEvent = row.out },
                                        )
                                        is MovementRow.Single -> MovementSingleRow(
                                            tx = row.event,
                                            accountNames = accountNames,
                                            esRecurrente = nombreRecurrenteDe(
                                                row.event,
                                                reglasRecurrentes,
                                                nombresDeSuscripcionesActivas,
                                            ) != null,
                                            onClick = { selectedEvent = row.event },
                                        )
                                    }
                                    if (i < rows.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
    )

    selectedEvent?.let { event ->
        // El mismo juego de hojas que abre el detalle de la cuenta — categoría, fecha, monto,
        // cuenta, concepto, «esto se repite» y anular — porque es el mismo movimiento tocado.
        // Ver [HojaDelMovimiento] para por qué está afuera de esta pantalla.
        HojaDelMovimiento(
            event = event,
            // Para poder mover el movimiento de cuenta. Es la MISMA lista que ya alimenta
            // `accountNames`/`accountTypes` de esta pantalla — no una lectura nueva.
            cuentas = accounts,
            onDismiss = { selectedEvent = null },
            onCambiado = { selectedEvent = null; refreshKey++ },
            // Ola 16: el saldo inicial ya no se lista acá, pero la búsqueda lo sigue
            // encontrando — y quien lo busca es justamente el que se pregunta «¿de dónde
            // salieron estos $41 millones?». La hoja lo explica y esto lo lleva a donde de
            // verdad se arregla, en un toque, en vez de dejarle una instrucción para seguir a
            // mano. Null si la lista de cuentas todavía no llegó: sin el tipo no se sabe a qué
            // grupo pertenece el detalle, y un botón que navega al lugar equivocado es peor
            // que ningún botón.
            onVerCuenta = accountTypes[event.accountId]?.let { tipo ->
                { onNavigate(Screen.AccountDetail(event.accountId, tipo.group)) }
            },
        )
    }

    // Editar un recurrente desde su renglón de «Próximos» — la misma hoja, en modo edición, que
    // abre el detalle de un movimiento. Al guardar se invalida el cache del gate y se recarga la
    // sección: el monto o el día nuevos tienen que verse en el mismo renglón que se acaba de
    // tocar, no en la próxima visita.
    reglaRecurrenteAEditar?.let { regla ->
        CreateRecurringRuleSheet(
            onDismiss = { reglaRecurrenteAEditar = null },
            onSaved = {
                reglaRecurrenteAEditar = null
                RecurringOfferGate.olvidarLoCacheado()
                recurrentesReloadKey++
            },
            existing = regla,
        )
    }

    if (showCandidatesSheet) {
        CardPaymentCandidatesSheet(
            candidates = pendingCandidates,
            onDismiss = { showCandidatesSheet = false },
            onConfirmed = { id -> resolvedIds = resolvedIds + id; refreshKey++ },
            onDismissedCandidate = { id -> resolvedIds = resolvedIds + id; refreshKey++ },
        )
    }

    if (showCreateSheet) {
        CreateAccountSheet(
            onDismiss = { showCreateSheet = false },
            onAccountCreated = { showCreateSheet = false; refreshKey++ },
        )
    }
    }
}

/**
 * El color de cada tono, con los tokens semánticos del tema — [MinExpense] y [MinIncome] son los
 * mismos que ya usan el resumen de SMS y el error de los formularios, no un hex suelto de esta
 * pantalla. Hoy la app trae un solo esquema (oscuro, ver `Theme.kt`); si algún día llega el
 * claro, se cambia en `Color.kt` y esto lo sigue.
 */
fun colorDelTono(tono: TonoDelMonto): Color = when (tono) {
    TonoDelMonto.GASTO -> MinExpense
    TonoDelMonto.INGRESO -> MinIncome
    TonoDelMonto.ENTRE_CUENTAS -> MinTransfer
    TonoDelMonto.NEUTRO -> MinTextMute
}

/**
 * Un traspaso, leído como un solo hecho: "Traspaso · Ahorros → CDT" y el monto **sin signo**, en
 * el azul de [TonoDelMonto.ENTRE_CUENTAS].
 *
 * Sin `+` ni `−` a propósito: la plata no entró ni salió del bolsillo, solo cambió de cuenta.
 * Ponerle un signo obligaría a elegir el punto de vista de una de las dos cuentas, que es
 * exactamente la confusión que este renglón viene a sacar. El signo de cada pata sí aparece, con
 * su cuenta al lado, en el detalle de cada cuenta. El color, en cambio, sí distingue esto de un
 * NEUTRO real (ver [tonoDelRenglon]): el dueño lo pidió para no confundir un traspaso con un
 * movimiento que de verdad no cuenta nada.
 */
@Composable
private fun TransferRow(
    row: MovementRow.Transfer,
    accountNames: Map<String, String>,
    accountTypes: Map<String, AccountType>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transferRowTitle(row, accountTypes),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(transferRowSubtitle(row, accountNames), fontSize = 12.sp, color = MinTextMute)
        }
        Text(
            text = formatCOP(row.amount),
            fontSize = 14.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = colorDelTono(tonoDelRenglon(row)),
            letterSpacing = (-0.3).sp,
        )
    }
}

/**
 * Un renglón suelto de la lista.
 *
 * Ola 8 · V7: el subtítulo dice ahora **de qué cuenta es el movimiento**. Con dos cuentas
 * abiertas había dos «Saldo inicial» idénticos y nada los distinguía — el renglón de un
 * traspaso sí decía «De Bancolombia Ahorros a Nequi» y los demás no decían nada. En su lugar
 * sale `EventSource` («MANUAL», «SMS»…), que además de no aportar nada acá es el nombre crudo
 * de un enum en una app que habla español; el punto naranja al lado de la descripción ya avisa
 * lo único que importaba de ahí: que el movimiento entró solo y falta confirmarlo.
 *
 * PR 1 del rediseño de Recurrentes: el mismo ícono de repetición que ya identifica a Recurrentes
 * en el rail y en Más ([Icons.Rounded.Repeat]), chico y sin color propio —el mismo `MinTextMute`
 * del subtítulo— junto a la categoría. No es un botón: solo informa: para editar el recurrente
 * hay que abrir el movimiento y usar «¿Se repite todos los meses?» (ver `SeccionEstoSeRepite`).
 */
@Composable
private fun MovementSingleRow(
    tx: FinancialEvent,
    accountNames: Map<String, String>,
    esRecurrente: Boolean = false,
    onClick: () -> Unit,
) {
    // Rojo gasto, verde ingreso, gris lo que no movió plata del bolsillo — la apertura de una
    // cuenta, la pata huérfana, la cuota que paga un tercero. Una sola regla, ver [tonoDelEvento].
    val tono = tonoDelEvento(tx)
    val subtitulo = accountNames[tx.accountId]
        ?.let { "${tx.category} · $it" }
        ?: tx.category
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = tx.description,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.1).sp,
                )
                if (tx.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                    StatusDot(MinWarn)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (esRecurrente) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Recurrente",
                        tint = MinTextMute,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = subtitulo,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Sin este weight, el Row que ahora envuelve el ícono no le da al texto un
                    // ancho acotado y el ellipsis de arriba no tiene sobre qué recortar.
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Text(
            text = when (tono) {
                TonoDelMonto.INGRESO -> "+${formatCOP(tx.amount)}"
                TonoDelMonto.GASTO -> "−${formatCOP(tx.amount)}"
                // `tonoDelEvento` (lo único que alimenta `tono` acá) nunca devuelve
                // ENTRE_CUENTAS — ese tono es solo de [tonoDelRenglon], para el par de
                // [MovementRow.Transfer] que se pinta en TransferRow, no acá. Rama exhaustiva
                // igual, con el mismo criterio de NEUTRO: sin signo.
                TonoDelMonto.NEUTRO, TonoDelMonto.ENTRE_CUENTAS -> formatCOP(tx.amount)
            },
            fontSize = 14.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = colorDelTono(tono),
            letterSpacing = (-0.3).sp,
        )
    }
}

/**
 * PR 2 del rediseño de Recurrentes (2026-09): el card de «Flujo libre», mudado de la pantalla
 * `RecurrentesScreen` a Movimientos —solo visible con el chip «Recurrentes» activo, ver
 * [mostrarResumenDeRecurrentes]—. Las cifras salen de [resumenRecurrentes], la misma función
 * pura que ya usaba esa pantalla y el acceso «Recurrentes» del Inicio: mudar DÓNDE se muestra
 * no puede hacer que el número discrepe de los demás lugares que cuentan lo mismo.
 *
 * `cifras == null` mientras la fuente fresca todavía no llegó (ver el `LaunchedEffect` que la
 * carga en [TransactionsScreen]) — un total a medias es peor que un guion.
 */
@Composable
private fun ResumenFlujoLibreCard(cifras: ResumenRecurrentes?) {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(20.dp),
    ) {
        Text("Flujo libre", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = cifras?.let { formatCOP(it.flujoLibre) } ?: "—",
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            color = MinText,
            letterSpacing = (-1.1).sp,
            lineHeight = 28.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ingresos recurrentes − Gastos recurrentes",
            fontSize = 12.sp,
            color = MinTextMute,
        )
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ingresos recurrentes", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cifras?.let { formatCOP(it.ingresos) } ?: "—",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MinIncome,
                    letterSpacing = (-0.3).sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Gastos recurrentes", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cifras?.let { formatCOP(it.gastos) } ?: "—",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.3).sp,
                )
            }
        }
        // Mismo criterio que la pantalla vieja: un total al que le faltan filas se dice, no se
        // disimula. Ver el KDoc de [ResumenRecurrentes.sinConvertir].
        if (cifras != null && cifras.sinConvertir > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (cifras.sinConvertir == 1) {
                    "Este total no incluye 1 cobro en otra moneda: no pudimos convertirlo a pesos."
                } else {
                    "Este total no incluye ${cifras.sinConvertir} cobros en otra moneda: no pudimos " +
                        "convertirlos a pesos."
                },
                fontSize = 11.sp,
                color = MinWarn,
                lineHeight = 15.sp,
            )
        } else if (cifras != null && cifras.hayMonedaExtranjera) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Lo que te cobran en dólares entra al total convertido a pesos con la tasa " +
                    "de cambio más reciente que pudimos consultar.",
                fontSize = 11.sp,
                color = MinTextMute,
                lineHeight = 15.sp,
            )
        }
    }
}

/**
 * Una candidata «detectada · por confirmar», en su nuevo hogar dentro de Movimientos.
 *
 * Mismo contenido que la fila que tenía `RecurrentesScreen` (nombre, monto en su propia moneda,
 * cuántos meses la vio el detector y su día de cobro, el aviso de «ya la tienes anotada» cuando
 * corresponde) pero con el lenguaje visual de [RecurringOfferBar] —un card compacto, no una hoja
 * modal— que es lo que esta pantalla ya usa para ofrecimientos de esta misma familia.
 */
@Composable
private fun CandidataSuscripcionCard(
    sub: Subscription,
    yaEsRegla: Boolean,
    enVuelo: Boolean,
    onConfirmar: () -> Unit,
    onDescartar: () -> Unit,
) {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sub.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
            Text(
                text = formatMoney(sub.amount, sub.currency),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MinText,
            )
        }
        Text(
            text = "Visto ${sub.occurrences} ${if (sub.occurrences == 1) "mes" else "meses"} · día ${sub.dayOfMonth}",
            fontSize = 12.sp,
            color = MinTextMute,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (yaEsRegla) {
            Text(
                text = "Ya lo tienes como recurrente",
                fontSize = 12.sp,
                color = MinWarn,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccionCandidataChip(
                label = when {
                    enVuelo -> "Guardando…"
                    yaEsRegla -> "Confirmar igual"
                    else -> "Confirmar"
                },
                primary = true,
                habilitado = !enVuelo,
                onClick = onConfirmar,
            )
            AccionCandidataChip(label = "No es", primary = false, habilitado = !enVuelo, onClick = onDescartar)
        }
    }
}

/** Los botones «Confirmar» / «No es» de una candidata — mismo lenguaje que el de la hoja vieja. */
@Composable
private fun AccionCandidataChip(label: String, primary: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinText else MinSurfaceContainerLow)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (primary) MinBg else MinText)
    }
}
