package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.montoContado
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.renglonTocable
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.colorDeLaCifra
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.intentar
import com.jvillada.movi.data.ahoraEnMs
import com.jvillada.movi.data.periodoVigenteSegun
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.MovimientoRechazado
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FiltroDeMovimientos
import com.jvillada.movi.shared.model.VistaDelAnalisis
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.showsInMovements
import com.jvillada.movi.ui.profile.InicioDelPeriodoSheet
import com.jvillada.movi.ui.profile.guardarInicioDelPeriodo
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.aporteAlFlujoDelDia
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.ui.porrevisar.RenglonPorRevisar
import com.jvillada.movi.ui.porrevisar.cuantosPorRevisar
import com.jvillada.movi.ui.porrevisar.rememberLecturasPorRevisar
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe
import com.jvillada.movi.ui.plan.rememberMarcasDeRecurrentes
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.shared.model.normalizarParaBuscar

/**
 * F13: filtro puro detrás de la búsqueda de Movimientos, separado del `@Composable` para poder
 * testearlo en `:shared:commonTest` sin arrancar Compose. Compara sin acentos ni mayúsculas
 * (mismo criterio que `CategoryField`, ver [normalizeForMatch]) contra descripción, comercio
 * (si lo hay) y categoría. Una consulta en blanco matchea todo — así el filtro es un no-op
 * mientras el campo de búsqueda está vacío.
 */
fun matchesQuery(event: FinancialEvent, query: String): Boolean {
    val q = normalizarParaBuscar(query.trim())
    if (q.isEmpty()) return true
    return normalizarParaBuscar(event.description).contains(q) ||
        event.merchant?.let { normalizarParaBuscar(it).contains(q) } == true ||
        normalizarParaBuscar(event.category).contains(q)
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

    /**
     * **Los ajustes de saldo de un día, en un solo renglón.**
     *
     * Corregir el saldo de una cuenta no es plata que se movió: es una corrección a lo que Movi
     * creía. Pero se anota como movimiento y se listaba como movimiento, así que una tarde de
     * conciliar contra el portal del banco dejaba diez renglones «Ajuste al saldo del banco —
     * quedó en $…» compitiendo de igual a igual con «Carnes y Legumbres Santa Elena». El dueño lo
     * dijo así: *«Tantos movimientos de ajuste de saldo se ven horribles»*.
     *
     * Se agrupan en vez de esconderse —que es lo que se hizo con la apertura de una cuenta, ver
     * `showsInMovements`— porque no son lo mismo: una apertura la escribe Movi sola y el dueño
     * nunca la decidió, mientras que **cada ajuste es una edición suya** sobre sus propios
     * números. Esconderla del todo sería borrarle el rastro de lo que él mismo corrigió.
     */
    data class Ajustes(val events: List<FinancialEvent>) : MovementRow() {
        override val key: String get() = "ajustes-" + events.first().id
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
 * ### Ola W: el pago de tarjeta SÍ resta acá, aunque no cuente en el mes
 *
 * `aporteAlFlujoDelDia` (y `countsAsCashFlow` detrás) no cambian: siguen siendo la fuente de
 * «Salió»/Disponible por período, que no puede contar dos veces el gasto real (la compra ya restó
 * el mes cuando se hizo). Pero «Flujo del día» es OTRA cifra —el extracto del día, no el gasto del
 * período— y de esa cuenta sí salió la plata del pago. Por eso este total, que solo vive acá (en
 * el cliente, para esta cabecera), le resta también [montoPagoDeTarjetaEnElDia]: la misma suma que
 * ya usaba la línea de contexto de la Ola U, ahora sumada al total en vez de solo mencionada aparte.
 *
 * Un día que se queda sin filas se descarta entero (encabezado incluido): un día vacío con su
 * «Flujo del día» no le dice nada a nadie.
 */
fun diasVisibles(
    days: List<EventDay>,
    chip: Int,
    query: String,
    accountTypes: Map<String, AccountType> = emptyMap(),
): List<EventDay> =
    days.mapNotNull { day ->
        val filtered = day.items
            // Ola 16: la apertura de una cuenta no se lista salvo que la busquen — ver
            // [showsInMovements], que también explica por qué el filtro vive acá y no en
            // `/by-day` ni en `LocalRepository`.
            .filter { showsInMovements(it, query) }
            .filter { matchesChip(it, chip) }
            .filter { matchesQuery(it, query) }
        if (filtered.isEmpty()) null
        else day.copy(
            items = filtered,
            // La misma función que usa el server en `/by-day`, MENOS el pago de tarjeta del día
            // (ver nota de la Ola W arriba): un pago siempre resta, nunca suma, porque es plata
            // que salió de una cuenta de Tu plata.
            total = filtered.sumOf { aporteAlFlujoDelDia(it) } - montoPagoDeTarjetaEnElDia(filtered, accountTypes),
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
    /**
     * Plata que salió del bolsillo. **En el color del texto** y con «−» (Ola 2a, decisión del dueño
     * del 22-oct-2026): el rojo quedó solo para lo que pide atención, y gastar no es un problema.
     */
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
 * es `ENTRE_CUENTAS`, ni gasto ni ingreso (la única excepción es el desembolso de un crédito, que
 * es `INGRESO`: la plata prestada sí entró a tu cuenta): la plata no entró ni salió, cambió de
 * cuenta. Ponerle un signo obligaría a elegir el punto de vista de una de las dos cuentas, que es
 * justo la confusión que el renglón doble vino a sacar (ver [TransferRow]) — pero eso solo dice
 * que no lleva signo, no que tenga que verse igual que un NEUTRO real (la apertura de una cuenta,
 * una pata huérfana): son un hecho identificable y el dueño los quiere distinguibles a simple
 * vista (ver [TonoDelMonto]).
 */
fun tonoDelRenglon(
    row: MovementRow,
    accountTypes: Map<String, AccountType> = emptyMap(),
): TonoDelMonto = when (row) {
    // **El desembolso de un crédito sí es plata que entró a tu cuenta**, aunque no cuente como
    // ingreso del mes (es deuda): sin verde ni «+», la fila se leía como un movimiento cualquiera
    // y el dueño no encontraba de dónde había salido la plata que gastó. Sin los tipos de cuenta
    // todavía, se queda neutro como siempre — ver [transferRowTitle].
    is MovementRow.Transfer ->
        if (esDesembolso(row, accountTypes)) TonoDelMonto.INGRESO else TonoDelMonto.ENTRE_CUENTAS
    is MovementRow.Single -> tonoDelEvento(row.event)
    // Un grupo de ajustes no movió plata del bolsillo — ninguno de sus renglones lo hizo, por
    // `isCashFlow` — así que el gris es literal, no una elección estética.
    is MovementRow.Ajustes -> TonoDelMonto.NEUTRO
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
 * PR 1 del rediseño de Recurrentes (2026-09) lo puso como chip; la ola C lo sacó de Movimientos:
 * el tablero de lo que se repite es ahora **Plan · Pagos del mes**, y lo que queda acá de esa idea
 * es el ícono de repetición de cada fila (ver [com.jvillada.movi.ui.recurrentes.nombreRecurrenteDe]).
 *
 * **El índice no se borra ni se reusa**: viaja adentro de `Screen.Transactions`, y quien todavía
 * lo pida —un enlace viejo, una pila— termina en Plan (ver `destinoVigente` en Navigation.kt). Si
 * aun así llegara a Movimientos, [chipInicialDeMovimientos] lo trata como «Todo».
 */
const val CHIP_RECURRENTES = 5

/**
 * El tag de un renglón suelto de Movimientos (Task 3, Ola B): sin él, una prueba no tiene forma de
 * medir el alto real del renglón para verificar que agregarle [IconoDeCategoria] no lo hizo crecer.
 */
const val TAG_FILA_DE_MOVIMIENTO_SUELTO: String = "fila-de-movimiento-suelto"

/**
 * El tag del renglón de encabezado de un día esqueleto («Hoy · −$250.100»), para contar
 * cuántos GRUPOS pinta [movimientosEsqueleto] sin depender de ningún texto — no hay ninguno
 * todavía, ver su KDoc.
 */
const val TAG_ENCABEZADO_DE_DIA_ESQUELETO: String = "encabezado-de-dia-esqueleto"

/**
 * El tag del renglón de encabezado de un día REAL («Hoy · −$250.100», el que pliega y
 * despliega el día). Ola B, tarea 9 (fix round 1): sin él, una prueba no tenía cómo comparar el
 * TOP de este renglón contra el del encabezado esqueleto de arriba y verificar que el primer
 * grupo no salta al llegar los datos.
 */
const val TAG_ENCABEZADO_DE_DIA: String = "encabezado-de-dia"

/**
 * El tag de la línea del rango del período («Del 25 de agosto al 24 de septiembre…») mientras el
 * perfil no contestó — Ola B, tarea 2. Reserva su lugar para que la línea real (o su ausencia, con
 * corte 1) no empuje la lista de abajo al llegar el perfil.
 */
const val TAG_LINEA_DE_PERIODO_ESQUELETO: String = "linea-de-periodo-esqueleto"

/** El tag de la línea del rango del período ya con el dato real — ver [TAG_LINEA_DE_PERIODO_ESQUELETO]. */
const val TAG_LINEA_DE_PERIODO: String = "linea-de-periodo"

/**
 * **Movimientos mientras carga, con la forma de Movimientos** (Ola B, tarea 9).
 *
 * Task 7 (ola A) puso UNA tarjeta de 6 filas sueltas. La pantalla real no es una lista: son varios
 * DÍAS, cada uno con su renglón de encabezado («Hoy · −$250.100», ver más abajo en
 * el `forEach` real) y su propia tarjeta — así que la tarjeta única se convertía en 2-3 tarjetas
 * más chicas apenas llegaban los datos, el salto que reportó el dueño.
 *
 * [GRUPOS_DEL_ESQUELETO] imita eso con dos o tres grupos de tamaño distinto (ni todos los días
 * tienen el mismo número de movimientos, y repetir la misma forma se lee más a rueda que a lista
 * real). El encabezado usa [RenglonConCifraEsqueleto] —rótulo a la izquierda, cifra a la
 * derecha, la misma pieza que ya arma el resto de esta ola— en vez de decir «HOY» o un total: ni
 * la fecha ni el flujo del día se conocen todavía, y afirmar cualquiera de los dos sería la misma
 * falla que la Task 8 le corrigió a Créditos y Presupuestos.
 */
private val GRUPOS_DEL_ESQUELETO = listOf(3, 2, 2)

private fun LazyListScope.movimientosEsqueleto() {
    GRUPOS_DEL_ESQUELETO.forEachIndexed { indice, filas ->
        item {
            // El MISMO aire arriba que cada día real ([aireArribaDelDia]): si no, el primer grupo
            // arrancaba más arriba que el primer día y la lista saltaba al llegar los datos.
            Column(
                modifier = Modifier
                    .padding(horizontal = Medida.margenDePantalla)
                    .padding(top = aireArribaDelDia(indice))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO)
                        .heightIn(min = Medida.objetivoTactil),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    RenglonConCifraEsqueleto(fraccionDelRotulo = 0.2f, estiloDelRotulo = Movi.textos.titulo)
                }
                // La forma de la lista real: una superficie con filas del sistema (mismo alto).
                Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                    repeat(filas) { i ->
                        EsqueletoDeFila()
                        if (i < filas - 1) Separador()
                    }
                }
            }
        }
    }
}

/**
 * El aire arriba de un día de la lista: entre dos días, [Medida.entreSecciones] (cada día es una
 * sección); arriba del primero, [Medida.dentroDeSeccion], porque lo de arriba (los filtros) ya lo
 * separa. Lo usan el día real y el esqueleto, para que nada salte al llegar los datos.
 */
internal fun aireArribaDelDia(indice: Int): Dp =
    if (indice == 0) Medida.dentroDeSeccion else Medida.entreSecciones

/**
 * Los rótulos de los chips, en el orden de sus índices. «Recurrentes» ya no se dibuja (ver
 * [CHIP_RECURRENTES]) pero su rótulo se queda en su lugar: sacarlo correría los índices.
 */
val CHIPS_DE_MOVIMIENTOS = listOf("Todo", "Gastos", "Ingresos", "Por confirmar", "Entre cuentas", "Recurrentes")

/**
 * **Los chips que se dibujan**, que ya no son todos.
 *
 * El dueño miró la fila de seis y dijo: *«Me parece que ya tenemos muchos filtros o al menos en
 * ese nivel de jerarquía, se ve bastante mal»* — y sobre este en particular: *«Por confirmar
 * debería saltar en otro lugar no acá en esta misma vista»*.
 *
 * Tiene razón, y el motivo es que **no es un filtro, es una bandeja de entrada**. «Todo»,
 * «Gastos» e «Ingresos» son formas de mirar los movimientos que uno tiene; «Por confirmar» es una
 * tarea pendiente, y su estado normal —el de quien anota todo a mano— es *vacío*. Un chip que la
 * enorme mayoría de los días no lleva a ningún lado ocupa el mismo espacio que los que sí.
 *
 * Así que salió de la fila y pasó a ser un aviso arriba de la lista. Ola C, tarea 5: ese aviso y el
 * de los candidatos a pago de tarjeta se juntaron con los mensajes del banco en **una sola bandeja,
 * «Por revisar»** (`ui/porrevisar/PorRevisarScreen.kt`), y acá queda un solo renglón «N por
 * revisar» que la abre.
 *
 * ### Por qué la constante sigue existiendo y valiendo 3
 *
 * [CHIP_POR_CONFIRMAR] no se borra aunque Movimientos ya no tenga ese modo: [matchesChip] lo sigue
 * contestando, y sobre todo **los índices no se renumeran** — el número viaja adentro de
 * `Screen.Transactions` y puede volver desde una pila de navegación restaurada. Quien lo pida llega
 * a «Por revisar» (ver `destinoVigente` en Navigation.kt). Correr «Recurrentes» del 5 al 3 haría
 * que un 3 viejo signifique otra cosa, en silencio.
 *
 * ### «Entre cuentas» salió por otro motivo
 *
 * El dueño: *«Entre cuentas creo que no hace falta acá, debería ir en cuentas tal vez no?»*. Y
 * sí: un traspaso, una cuota o un pago de tarjeta son hechos **entre dos cuentas suyas**, no una
 * forma de mirar sus gastos. La lista sigue existiendo igual —el mismo filtro, la misma pantalla—
 * pero se entra desde Cuentas, que es de lo que habla. Ver `AccountsScreen`.
 *
 * ### «Recurrentes» se mudó a Plan (ola C)
 *
 * El tablero que ese chip mostraba —qué vence, qué ya ocurrió, qué falta confirmar— no es una forma
 * de mirar movimientos sino la respuesta a «¿qué me falta pagar?», que es la pregunta de la pestaña
 * Plan. Ahí vive ahora, como «Pagos del mes». Ver [CHIP_RECURRENTES].
 */
val CHIPS_VISIBLES = listOf(CHIP_TODO, CHIP_GASTOS, CHIP_INGRESOS)

/**
 * **El aviso de lo que no subió**, o `null` si todo subió. Lo que el server rechaza (la cuenta se
 * borró desde la web, una categoría que no se puede anotar) se queda solo en este teléfono: se ve en
 * Movimientos pero no llega al Inicio ni a la web. Antes el SyncEngine lo reintentaba en silencio
 * para siempre; ahora se dice, con el motivo del server y el nombre del movimiento.
 */
fun textoDeRechazados(rechazados: List<MovimientoRechazado>): String? {
    val primero = rechazados.firstOrNull() ?: return null
    val cabeza = if (rechazados.size == 1) "«${primero.evento.description}» no se pudo subir"
    else "${rechazados.size} movimientos no se pudieron subir"
    return "$cabeza: ${primero.motivo} Solo está en este teléfono; corrígelo o anúlalo."
}

/**
 * El rótulo del encabezado cuando se está adentro de un filtro que **no tiene chip**, o `null` si
 * el filtro activo sí es uno de los que se dibujan.
 *
 * Sin chip marcado, la lista se vería filtrada sin nada que dijera por qué ni cómo volver. Ver
 * [CHIPS_VISIBLES]. Hoy es solo «Entre cuentas»: «Por confirmar» dejó de ser un modo de esta
 * pantalla en la ola C (ver [CHIP_POR_CONFIRMAR]).
 */
fun tituloDelModoSinChip(chip: Int): String? = when (chip) {
    CHIP_ENTRE_CUENTAS -> CHIPS_DE_MOVIMIENTOS[CHIP_ENTRE_CUENTAS]
    else -> null
}

/**
 * **Los días que caen adentro de un período**, para que Movimientos muestre el mes que el dueño
 * vive y no el del calendario.
 *
 * El dueño: *«Como la periodicidad es mensual me debería dejar ver cada mes en las fechas que yo
 * establecí, de 25 a 25 o cuando comience el período»*. Su corte no es el 1 porque su plata no
 * empieza el 1: su salario está registrado el **26 de agosto** y se llama **«Salario Septiembre
 * 2026»**. Movi ya sabía calcular eso —`PeriodSettings` existe y Presupuestos lo respeta— pero la
 * lista de movimientos seguía siendo una tira infinita de días sin decir de qué mes hablaba.
 *
 * **Con corte 1 no cambia nada, por construcción**: el período ES el mes de calendario, así que
 * quien no toque el ajuste ve lo de siempre, solo que ahora con el mes escrito arriba.
 *
 * Un día con fecha ilegible se deja pasar en vez de descartarse: esconder un movimiento porque no
 * se entendió su fecha es peor que mostrarlo en el período equivocado — uno se ve y se corrige, el
 * otro no se ve nunca.
 */
fun diasDelPeriodo(
    days: List<EventDay>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
): List<EventDay> = days.filter { dia ->
    val suyo = periodoDeLaFecha(dia.date, settings)
    suyo == null || suyo == periodo
}

/**
 * ¿Se puede avanzar al período siguiente? **No más allá del actual**: el que viene todavía no
 * ocurrió, y una lista vacía con el nombre de un mes futuro no le dice nada a nadie.
 */
fun puedeAvanzarDePeriodo(visible: PeriodoFinanciero, actual: PeriodoFinanciero): Boolean =
    visible.prefijo < actual.prefijo

/**
 * PR 3 del rediseño de Recurrentes (2026-09): **con qué chip arranca Movimientos** cuando alguien
 * la abrió pidiendo uno — hoy, el modo sin chip «Entre cuentas», desde Patrimonio.
 *
 * `null` —el caso normal, entrar por la pestaña— es «Todo». Un índice fuera de rango también cae
 * en «Todo» y no explota: el valor viaja adentro de [com.jvillada.movi.ui.Screen.Transactions], y
 * una pila restaurada o una definición SDUI vieja podrían traer un número que hoy no existe.
 * Arrancar en «Todo» ahí es la caída correcta — es la pantalla completa, no un filtro que esconde
 * cosas sin decirlo. [CHIP_RECURRENTES] y [CHIP_POR_CONFIRMAR] caen igual: esos modos ya no
 * existen acá (ola C: uno se mudó a Plan, el otro a «Por revisar») y la navegación los desvía antes
 * de llegar; si aun así llegaran, «Todo» es lo honesto.
 */
fun chipInicialDeMovimientos(pedido: Int?): Int =
    if (pedido != null && pedido in CHIPS_DE_MOVIMIENTOS.indices && pedido != CHIP_RECURRENTES && pedido != CHIP_POR_CONFIRMAR) pedido
    else CHIP_TODO

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
 * «Recurrentes» fue el quinto hasta la ola C, cuando su tablero se mudó a Plan (ver
 * [CHIP_RECURRENTES]); lo que queda de él en esta pantalla es el ícono de repetición de cada fila.
 */
fun matchesChip(
    event: FinancialEvent,
    chip: Int,
): Boolean = when (chip) {
    CHIP_GASTOS -> event.type == TransactionType.EXPENSE && cuentaEnGastosEIngresos(event)
    // Igual que Gastos: lo que entró solo espera en «Por confirmar» y no se suma hasta confirmarlo.
    CHIP_INGRESOS -> event.type == TransactionType.INCOME && cuentaEnGastosEIngresos(event)
    CHIP_POR_CONFIRMAR -> esperaEnPorConfirmar(event.reconciliationStatus)
    CHIP_ENTRE_CUENTAS -> esEntreCuentas(event)
    else -> true
}

/** El aviso de cuando no se pudo leer el corte del perfil y Movimientos cae al mes de calendario. */
const val PERIODO_NO_LEIDO: String =
    "No pudimos leer el día en que empieza tu mes: te mostramos el mes del calendario."

/**
 * Lo que dice la lista cuando **no quedó nada que mostrar**, según por qué no quedó nada.
 *
 * Hasta acá todo vacío decía «Sin movimientos aún · + Registrar el primero», que es el estado de
 * una cuenta recién abierta. Con el chip «Por confirmar» activo y nada por confirmar —que es el
 * caso normal de quien anota todo a mano— ese texto mentía dos veces: sí hay movimientos, y
 * registrar uno nuevo no tiene nada que ver con confirmar los que entraron solos. El dueño lo
 * leyó exactamente así: *«¿Qué es Por confirmar?»*. Ola C: ese modo se mudó a la bandeja «Por
 * revisar», que tiene su propio vacío; acá quedan los de los chips.
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
 * **Los ajustes de saldo de un día, juntados en un renglón que se abre.**
 *
 * Se corre después de [collapseTransfers], sobre los renglones ya armados, y conserva el orden:
 * el grupo queda donde estaba el PRIMER ajuste del día, así que nada se mueve de lugar. Ver
 * [MovementRow.Ajustes] para el porqué de agrupar en vez de esconder.
 *
 * Dos reglas que no son obvias:
 *
 * 1. **Con una búsqueda escrita no se agrupa nada.** Buscar es pedirlos explícitamente, y una
 *    lista que esconde adentro de un grupo justo lo que acabás de buscar es peor que una que
 *    muestra de más. Es el mismo escape que ya tiene la apertura de una cuenta en
 *    `showsInMovements`, y por el mismo motivo.
 * 2. **Un ajuste solo no se agrupa.** Un grupo de uno ocupa el mismo renglón que el ajuste, no
 *    ahorra nada, y encima obliga a un toque más para leer lo que ya se veía. El problema del
 *    dueño empieza cuando son varios.
 */
fun agruparAjustesDeSaldo(rows: List<MovementRow>, query: String): List<MovementRow> {
    if (query.isNotBlank()) return rows
    val ajustes = rows.filterIsInstance<MovementRow.Single>()
        .filter { it.event.category == ADJUSTMENT_CATEGORY }
        .map { it.event }
    if (ajustes.size < 2) return rows

    var yaPuesto = false
    return rows.mapNotNull { row ->
        val esAjuste = row is MovementRow.Single && row.event.category == ADJUSTMENT_CATEGORY
        when {
            !esAjuste -> row
            yaPuesto -> null
            else -> {
                yaPuesto = true
                MovementRow.Ajustes(ajustes)
            }
        }
    }
}

/**
 * **Cuántos movimientos dice tener un día plegado.** Cuenta hechos, no renglones — y el grupo de
 * ajustes no es un hecho.
 */
fun cuantosMovimientosDice(rows: List<MovementRow>): Int = rows.sumOf { row ->
    when (row) {
        // Un par plegado es UN hecho: la plata cambió de cuenta una sola vez. Esa decisión es
        // anterior a los ajustes y no se toca.
        is MovementRow.Transfer -> 1
        is MovementRow.Single -> 1
        // Un grupo de ajustes NO es un hecho: son varias correcciones que se muestran juntas por
        // comodidad. Contarlo como uno le bajaría la cuenta al día en silencio el día que
        // aparecieron los grupos, y el dueño vería «2 movimientos» sobre un día que tiene cuatro.
        is MovementRow.Ajustes -> row.events.size
    }
}

/**
 * Lo que dice el renglón agrupado. **Cuenta cuentas, no movimientos**, porque es lo que el dueño
 * hizo: repasó el saldo de tantas cuentas contra el banco. Dos ajustes sobre la misma cuenta —se
 * equivocó y volvió a corregir— son una cuenta revisada, no dos.
 */
fun tituloDeLosAjustes(events: List<FinancialEvent>): String {
    val cuentas = events.map { it.accountId }.distinct().size
    return if (cuentas == 1) "Ajustaste el saldo de una cuenta"
    else "Ajustaste el saldo de $cuentas cuentas"
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
    // La categoría del desembolso también manda sobre el tipo de cuenta: dice «Desembolso» aunque la
    // lista de cuentas todavía no haya llegado (con el mapa vacío caería en «Traspaso»).
    row.out.category == DESEMBOLSO_CATEGORY -> TITULO_DE_DESEMBOLSO
    accountTypes[row.out.accountId] == AccountType.LOAN -> TITULO_DE_DESEMBOLSO
    accountTypes[row.into.accountId] == AccountType.LOAN -> "Abono extraordinario"
    else -> "Traspaso"
}

/** ¿Este par es el desembolso de un crédito: la plata prestada que entró a una cuenta tuya? */
fun esDesembolso(row: MovementRow.Transfer, accountTypes: Map<String, AccountType>): Boolean =
    // La categoría la escribe `transferLegsFor` solo cuando la plata sale de un crédito hacia una
    // cuenta que no es deuda, así que no hace falta mirar los tipos de cuenta (que pueden no haber
    // llegado). Sin ella (un par anterior a la categoría) se decide por los tipos.
    row.out.category == DESEMBOLSO_CATEGORY ||
        (
            transferRowTitle(row, accountTypes) == TITULO_DE_DESEMBOLSO &&
                // La plata tiene que llegar a una cuenta que NO es deuda: un par de un crédito a otro
                // crédito o a una tarjeta no es plata prestada que entró.
                accountTypes[row.into.accountId].let { it != null && it != AccountType.LOAN && it != AccountType.CREDIT_CARD }
            )

private const val TITULO_DE_DESEMBOLSO = "Desembolso"

/**
 * Lo que el renglón de un desembolso aclara: la plata entró a tu cuenta, pero es prestada — deuda,
 * no sueldo. Suma en «Entró» de su período (ver [DESEMBOLSO_CATEGORY]). Va en su propia línea, encima
 * del «De X a Y», para que el nombre del crédito no lo recorte la elipsis; y puede ocupar dos
 * renglones en un teléfono angosto.
 */
const val NOTA_DE_DESEMBOLSO: String = "Crédito · plata prestada que entró a tu cuenta"

/** El monto grande del par: con «+» si es un desembolso (entró plata), sin signo en los demás. */
fun textoDelMontoDeTraspaso(row: MovementRow.Transfer, accountTypes: Map<String, AccountType>): String {
    val monto = formatMoney(row.amount, row.out.currency)
    return if (esDesembolso(row, accountTypes)) "+$monto" else monto
}

/**
 * Minúsculas y sin tildes/eñe — mismo criterio que usa `CategoryField` (F35) para sus
 * sugerencias, pero definido acá aparte: esta pantalla no puede tocar `CategoryField.kt` en esta
 * tarea (Ola 4 la reserva para otro trabajo en paralelo), así que se duplica el normalizador en
 * vez de extraerlo a un helper compartido.
 */

@Composable
fun TransactionsScreen(
    onNavigate: (Screen) -> Unit,
    chipInicial: Int? = null,
    periodoInicial: String? = null,
    /** Ola 5: la búsqueda con que arranca (un comercio tocado en «Buscar»). Ver [Screen.Transactions]. */
    consultaInicial: String? = null,
    filtroInicial: FiltroDeMovimientos? = null,
) {
    // Ola 4 · Ver la plata: lo que pidió «En qué se va» al tocar una barra o una fila (ver
    // [Screen.Transactions.filtro]). Se quita con la × de su chip; volver a entrar pidiendo otro
    // filtro rearranca en el nuevo.
    var filtro by remember(filtroInicial) { mutableStateOf(filtroInicial) }
    // Con qué chip arranca — ver [chipInicialDeMovimientos]. `remember(chipInicial)` y no
    // `remember { }` a secas: si se vuelve a entrar pidiendo otro filtro, el estado tiene que
    // rearrancar en el que se pidió, no quedarse con el de la visita anterior.
    var activeFilter by remember(chipInicial) { mutableStateOf(chipInicialDeMovimientos(chipInicial)) }
    // Lo que se filtra por texto: lo que pidió «Buscar» (un comercio tocado allá) o «En este
    // comercio» del detalle. Se ve como un chip con su ×; la lupa abre «Buscar» (ola R4: una sola
    // búsqueda en la app).
    var searchQuery by remember(consultaInicial) { mutableStateOf(consultaInicial.orEmpty()) }
    // F12: "Pendientes" no decía qué es — son los movimientos que entraron solos (SMS, OCR,
    // extracto) y esperan que confirmes monto y categoría. "Por confirmar" sí lo dice.
    val filters = CHIPS_DE_MOVIMIENTOS
    // Los días que el dueño plegó, por fecha ISO. Se recuerdan entre visitas: ver
    // [DiasPlegadosStore] para el porqué.
    var diasPlegados by remember { mutableStateOf(DiasPlegadosStore.plegados()) }
    /**
     * Los grupos de ajustes que el dueño abrió, por clave de grupo. **Transitorio a propósito**, a
     * diferencia de [DiasPlegadosStore]: plegar un día es una decisión sobre ese día («el 28 ya lo
     * revisé»), mientras que abrir el grupo de ajustes es un vistazo — se mira qué se corrigió y
     * se sigue. Que vuelva a cerrarse en la próxima visita es el comportamiento correcto, no una
     * limitación.
     */
    var ajustesAbiertos by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    // Pantalla ancha: la rueda del mouse sobre los márgenes, a los lados de la columna, también
    // tiene que mover esta lista. Ver [ScrollDesdeLosMargenes].
    ScrollDesdeLosMargenes(listState)
    // Re-tocar «Movimientos» estando acá vuelve arriba (ver [SubirAlRetocarLaPestana]).
    SubirAlRetocarLaPestana(listState)

    var refreshKey by remember { mutableStateOf(0) }
    // **Lo último que se vio, al primer cuadro.** El perfil, la historia y las cuentas arrancan con
    // lo que la visita anterior dejó en [CacheDeLecturas] (si es de esta sesión y reciente) y se
    // releen igual: mientras la lectura nueva viaja la cabecera dice «Actualizando…», y si trae lo
    // mismo no se recompone nada. Se releen con `refreshKey` (el «Reintentar» de esta pantalla, o que
    // algo se guardó desde ella) y con `LocalRefreshTick` (se guardó algo desde la hoja de Agregar,
    // que es una modal: esta pantalla nunca sale de la composición). Ver [rememberLectura].
    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = refreshKey) { Repositories.wallets.getUserProfile() }
    val eventos = rememberLectura(ClaveDeLectura.EventosPorDia, reintento = refreshKey) { Repositories.wallets.getEventsByDay() }
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, reintento = refreshKey) { Repositories.wallets.getAccounts() }
    /**
     * El perfil a la vista: el leído, el recordado, o el que devolvió guardar el arranque de un
     * período desde esta pantalla (ver [Lectura.anotar] más abajo).
     */
    val perfilVigente = perfil.valor
    /**
     * El día en que arranca el mes del dueño. Sale de su perfil, igual que en Presupuestos; si la
     * lectura falla queda en 1 —mes de calendario—, que es el comportamiento de siempre.
     */
    val cutoffDay = perfilVigente?.periodCutoffDay ?: 1
    /** Los períodos que el dueño declaró que arrancaron otro día. Ver `PeriodSettings.iniciosPropios`. */
    val iniciosPropios = perfilVigente?.periodStarts ?: emptyMap()
    // Ola B, tarea 2: distingue «todavía no sabemos el corte» (`cutoffDay` en su default de 1) de
    // «ya se leyó y de verdad es corte 1» — sin esto, la línea del rango (`rangoLegibleDe`)
    // aparecía recién cuando el perfil contestaba y empujaba toda la lista de abajo. Se prende con
    // un perfil a la vista (leído o recordado) O con su lectura terminada, aunque haya fallado: los
    // dos son «ya sabemos qué mostrar».
    val perfilLeido = perfilVigente != null || perfil.terminada
    /**
     * Distinto de [perfilLeido]: este solo vale con un perfil
     * que salió BIEN de una lectura (de esta visita o recordada de una anterior), porque es lo único
     * que vale la pena recordar en `FormaRecordada` (ver su KDoc: «los números... la última vez que
     * su lectura salió bien»). Grabar también un fallo dejaría una `FormaDeMovimientos` mintiendo
     * sobre el corte real la próxima vez que se abra la pantalla.
     */
    val perfilOk = perfilVigente != null
    /**
     * La historia, recién cuando el perfil ya se sabe: filtrarla por período con el corte por
     * defecto y volver a filtrarla cuando llega el de verdad sería una lista que cambia de mes
     * delante del dueño. Con los dos recordados, están los dos al primer cuadro.
     */
    val allDays = if (perfilLeido) eventos.valor.orEmpty() else emptyList()
    /**
     * Si la última carga que salió bien mostraba la línea del rango del período, o nunca la
     * mostró (corte 1). `null` la primera vez en este aparato: reserva la línea, como siempre.
     * Whole-branch review, final fix wave — sin esto, un dueño con corte 1 (sin línea nunca) veía
     * el esqueleto de Movimientos subir ~18 dp apenas el perfil contestaba, porque la línea se
     * reservaba igual para todos mientras no se sabía el corte.
     */
    val formaDeMovimientos = remember { FormaRecordada.delAparato.movimientos(SessionManager.userId) }
    /** Está en vuelo el guardado de un arranque propio. */
    var guardandoInicio by remember { mutableStateOf(false) }
    var errorDelInicio by remember { mutableStateOf<String?>(null) }
    var editandoElInicio by remember { mutableStateOf(false) }
    // Hay una lectura en vuelo. Arranca prendido: antes de la primera lectura no se afirma ni vacío
    // ni error. Con algo ya pintado es «Actualizando…»; sin nada, el esqueleto.
    val loading = eventos.actualizando || perfil.actualizando
    val errorDeLaPantalla = remember { mutableStateOf<String?>(null) }
    var error by errorDeLaPantalla
    // Solo vale cuando `getEventsByDay` contestó de verdad, en esta visita o en una reciente (ver
    // [NoSePudoLeer]). Sin esto, una lectura caída dejaba «Sin movimientos aún · + Registrar el
    // primero» a quien tiene cientos.
    val diasLeidos = eventos.valor != null
    /**
     * **Lo que se ve es lo último que vimos, no lo actual**: falló la lectura de la historia, o la
     * del perfil —que decide qué días entran en el período— con uno recordado a la vista. Lo dice
     * [NoSePudoActualizar] encima de la lista hasta que una lectura conteste.
     */
    val noSePudoActualizar = eventos.valor != null &&
        (eventos.fallo || (perfil.fallo && perfil.valor != null))
    /** Lo anotado en este teléfono que el server rechazó (ver [textoDeRechazados]). */
    var rechazados by remember { mutableStateOf<List<MovimientoRechazado>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }

    // F10: el estado vacío necesita saber si hay cuentas para elegir entre "+ Registrar el
    // primero" y "Crear una cuenta primero" — sin esto no hay forma de saber si el problema es
    // "no hay movimientos" o "no hay dónde anotarlos".
    // Una lectura secundaria: si falla no se dice nada, igual que siempre.
    val accounts = cuentas.valor.orEmpty()
    // Solo se ofrece «Crear una cuenta primero» cuando la lista llegó y vino vacía; mientras
    // carga (o si falló) se ofrece registrar, que es la acción segura.
    val accountsLoaded = cuentas.valor != null
    var showCreateSheet by remember { mutableStateOf(false) }
    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando la lista de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    // Los nombres de las cuentas, para que el renglón de un traspaso diga de dónde a dónde fue la
    // plata (ver [transferRowSubtitle]). Un evento suelto no los necesita: la cuenta no se muestra.
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    // Y sus tipos, para que el renglón de un traspaso con un crédito de una punta se llame
    // «Desembolso» o «Abono extraordinario» en vez de «Traspaso» (ver [transferRowTitle]).
    val accountTypes = remember(accounts) { accounts.associate { it.id to it.type } }
    // V13: «hoy» en la zona de la app (Bogotá), para que los encabezados digan «HOY»/«AYER».
    // Se calcula una vez por composición y no dentro del bucle de días.
    val hoyIso = remember { todayIsoInAppZone() }

    /**
     * El movimiento tocado. En el teléfono (y sin lugar al lado) abre su hoja; en pantalla ancha es
     * el que muestra el panel de la derecha (Ola W4, ver `MovimientosListaYDetalle`), releído por id
     * cada vez que la lista se relee ([movimientoDelPanel]).
     */
    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
    /** Vuelta de diseño 9: tu parte de lo que pagaron otros, para el resumen del panel (ver abajo). */
    var tuParteDeOtros by remember { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    val focusManager = LocalFocusManager.current
    /** Si [selectedEvent] se eligió en el panel de al lado (y no para abrir la hoja). */
    var elegidoEnElPanel by remember { mutableStateOf(false) }
    /** A dónde va el foco al cambiar de movimiento en el panel (ver `elegir`). */
    val focoDelPanel = remember { FocusRequester() }
    /**
     * Ola 2b · con qué sección se abre la hoja de [selectedEvent]: `null` al tocar la fila (el
     * detalle se lee primero), [AccionDelMovimiento.Categoria] al deslizarla a la izquierda.
     */
    var accionAlAbrir by remember { mutableStateOf<AccionDelMovimiento?>(null) }
    /** Ola 2b · la vibración de confirmar un aviso deslizando su fila (ver `Vibrar.kt`). */
    val vibrador = rememberVibrador()

    // Los avisos de una lectura caída, con el «Reintentar» de siempre — solo cuando NO hay nada a
    // la vista. Con la lista pintada (de esta visita o recordada) lo dice [NoSePudoActualizar]
    // encima de la lista, que no se va con el snackbar: una lista vieja sin nada que lo diga se
    // haría pasar por actual. Cada falla es una instancia nueva, así que un segundo intento caído
    // vuelve a avisar.
    LaunchedEffect(perfil.error, eventos.error) {
        val deLosEventos = eventos.error
        error = when {
            deLosEventos != null && eventos.valor == null -> deLosEventos.toUserMessage()
            // Sin el perfil la pantalla cae al mes de calendario, y con corte 25 eso es mostrar
            // otro período con otro total. Se dice; si además fallan los movimientos, ese error
            // (arriba) es el que manda.
            perfil.error != null && perfil.valor == null && deLosEventos == null -> PERIODO_NO_LEIDO
            else -> return@LaunchedEffect
        }
    }
    // F35: de paso, alimenta el caché de "categorías ya usadas" que lee CategoryField — esta
    // pantalla ya carga los movimientos. Ola 9 · A3: con el tipo de cada uno, para poder ofrecerlas
    // del lado correcto.
    LaunchedEffect(eventos.valor) {
        val dias = eventos.valor ?: return@LaunchedEffect
        UsedCategoriesCache.recordAll(dias.flatMap { d -> d.items }.map { ev -> ev.category to ev.type })
    }

    LaunchedEffect(refreshKey, refreshTick) {
        intentar { Repositories.wallets.getMovimientosRechazados() }.onSuccess { rechazados = it }
    }

    // Ola C, tarea 5: los mensajes del banco y los candidatos a pago de tarjeta, solo para contar
    // lo que hay en «Por revisar» (ver [RenglonPorRevisar]). Se revisan allá, no acá.
    val porRevisar = rememberLecturasPorRevisar(recarga = refreshKey)
    // El último número del renglón que salió de lecturas terminadas; `null` hasta la primera.
    val ultimoConteoPorRevisar = remember { mutableStateOf<Int?>(null) }

    // Lo que hace falta para el ícono de repetición de cada fila. Ola C: el tablero de Recurrentes
    // se mudó a Plan y esta pantalla ya no lo pinta; de él solo lee estas dos listas (ver
    // [MarcasDeRecurrentes]). Su «Reintentar» (`refreshKey`) también las vuelve a leer.
    val marcas = rememberMarcasDeRecurrentes(recarga = refreshKey)
    val reglasRecurrentes = marcas.reglas
    val nombresDeSuscripcionesActivas = marcas.nombresDeSuscripcionesActivas
    // Para guardar el arranque propio de un período (ver [InicioDelPeriodoSheet]).
    val alcanceDeLaPantalla = rememberCoroutineScope()

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    val reglaDelFinDeSemana = perfilVigente?.periodWeekendRule
    val ajustesDelPeriodo = remember(cutoffDay, iniciosPropios, reglaDelFinDeSemana) {
        PeriodSettings(
            cutoffDay = cutoffDay.coerceIn(1, 31),
            iniciosPropios = iniciosPropios,
            siCaeEnFinDeSemana = siElCorteCaeEnFinDeSemanaDe(reglaDelFinDeSemana),
        )
    }
    val periodoDeHoy = remember(ajustesDelPeriodo) {
        periodoActual(Clock.System.now().toEpochMilliseconds(), ajustesDelPeriodo)
    }
    // Whole-branch review, final fix wave: graba si esta carga (la que salió bien) mostró la
    // línea del rango — la próxima vez que se abra esta pantalla, el esqueleto reserva o no según
    // lo que de verdad pasó la última vez, en vez de reservarla siempre para todos. Con
    // `periodoDeHoy`, no con `periodoVisible`: lo que importa es lo que se ve al ABRIR, no un
    // período al que el dueño haya navegado.
    LaunchedEffect(perfilOk, periodoDeHoy, ajustesDelPeriodo) {
        if (!perfilOk) return@LaunchedEffect
        FormaRecordada.delAparato.recordarLineaDePeriodo(SessionManager.userId, periodoDeHoy, ajustesDelPeriodo)
    }
    /**
     * Qué período se está mirando. `remember(periodoDeHoy)` y no `remember { }` a secas: si el
     * dueño cambia su día de corte en Perfil y vuelve, el mes que se ve tiene que rearrancar en el
     * que corresponde al corte nuevo, no quedarse en el que nombraba el viejo.
     *
     * Arranca en [periodoInicial] cuando alguien lo pidió (ver [Screen.Transactions]). El prefijo
     * nombra el período sin depender del corte, así que sigue siendo el mismo cuando el perfil
     * contesta y `periodoDeHoy` cambia.
     */
    var periodoVisible by remember(periodoDeHoy, periodoInicial) {
        mutableStateOf(periodoInicial?.let { periodoDelPrefijo(it) } ?: periodoDeHoy)
    }

    val visibleDays = remember(activeFilter, allDays, searchQuery, periodoVisible, ajustesDelPeriodo, accountTypes, filtro) {
        val filtrados = diasVisibles(conElFiltro(allDays, filtro), activeFilter, searchQuery, accountTypes)
        // **Buscar atraviesa los períodos**, por cuarta vez en esta pantalla y por el mismo motivo
        // que las otras tres: escribir una consulta es pedir que algo aparezca, y encontrarlo solo
        // si además caía en el mes que estabas mirando es la peor forma de no encontrarlo.
        if (searchQuery.isNotBlank()) filtrados
        else diasDelPeriodo(filtrados, periodoVisible, ajustesDelPeriodo)
    }

    /**
     * Ola 2b · «4 veces este período · $142.800» del movimiento abierto: con los movimientos del
     * período de ESE movimiento que esta pantalla ya leyó (ver [enEsteComercio]). Sin lectura nueva.
     */
    fun enEsteComercioDe(evento: FinancialEvent): EnEsteComercio? {
        val suPeriodo = periodoActual(evento.timestamp, ajustesDelPeriodo)
        return enEsteComercio(evento, diasDelPeriodo(allDays, suPeriodo, ajustesDelPeriodo).flatMap { it.items })
    }
    /** Ola 2b · tocar «En este comercio»: la búsqueda de siempre, con ese comercio escrito. */
    fun buscarElComercio(busqueda: String) {
        searchQuery = busqueda
    }

    // Ola W4: en pantalla ancha, la lista a la izquierda y el movimiento elegido a la derecha. Ver
    // `MovimientosListaYDetalle` para la cuenta del ancho; en el teléfono, todo como siempre.
    MedirPanelDeMovimientos { conPanel ->
    /**
     * Elegir un movimiento. Con el panel, **primero se saca el foco del campo que lo tenga**: si
     * uno del panel (la búsqueda de categorías, el monto) lo tenía, en la web el `<input>` oculto de
     * Compose seguía recibiendo lo que se teclea y lo pegaba al campo del movimiento anterior. Ver
     * `movi-teclado-en-la-web`. Sin panel se abre la hoja, como siempre.
     *
     * El foco se **lleva al panel** ([focoDelPanel]) y no se suelta a secas: en Android (una tablet),
     * soltarlo del todo hace que la vista lo reclame y Compose se lo da al primer campo que
     * encuentra —la búsqueda de categorías del movimiento nuevo, con el teclado arriba—. Lo midió
     * `MovimientosListaYDetalleEnPantallaTest`.
     */
    val elegir: (FinancialEvent?) -> Unit = { evento ->
        if (conPanel) {
            if (runCatching { focoDelPanel.requestFocus() }.isFailure) focusManager.clearFocus()
        }
        accionAlAbrir = null
        selectedEvent = evento
        elegidoEnElPanel = conPanel
    }
    /**
     * Ola 2b · confirmar un aviso deslizando su fila a la derecha: el mismo `confirmEvent` que el
     * botón «Confirmar» del detalle, la vibración de confirmar, y la lista se relee.
     */
    val confirmarDesdeLaFila: (FinancialEvent) -> Unit = { evento ->
        alcanceDeLaPantalla.launch {
            intentar { Repositories.wallets.confirmEvent(evento.id) }
                .onSuccess {
                    vibrador.confirmar()
                    refreshKey++
                }
                .onFailure { error = it.toUserMessage() }
        }
    }
    // Revisión final de la W4, M-3: lo elegido en el panel es del panel. Si la ventana se achica
    // (o una tablet rota) hasta quedar sin lugar al lado, no se abre una hoja modal que nadie
    // tocó —y menos sobre un movimiento que quizás ya no existe—: se suelta la elección.
    val soltarLoDelPanel = !conPanel && elegidoEnElPanel && selectedEvent != null
    if (soltarLoDelPanel) SideEffect { selectedEvent = null; elegidoEnElPanel = false }
    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
    ListaYPanelDeMovimientos(
        conPanel = conPanel,
        focoDelPanel = focoDelPanel,
        lista = {
    Column(modifier = Modifier.fillMaxSize()) {
        // Movimientos es raíz: avatar, título y la lupa. **Una sola búsqueda en la app** (ola R4):
        // la lupa abre «Buscar», que encuentra movimientos, comercios, personas, cuentas y
        // pantallas. Lo que «Buscar» o «En este comercio» piden filtrar llega acá como un chip.
        MinScreenHeader(
            title = "Movimientos",
            leading = HeaderLeading.Avatar(onNavigate),
            action = {
                // Se está pintando algo que esta visita todavía no confirmó (lo recordado, o la lista
                // de antes de un «Reintentar»). Sin nada pintado lo dice el esqueleto.
                if (loading && visibleDays.isNotEmpty()) ActualizandoEnLaCabecera()
                BotonDeIcono(Icons.Rounded.Search, "Buscar", onClick = { onNavigate(Screen.Buscar) })
            },
        )

        // **De qué mes son estas cifras**, y cómo moverse entre meses.
        //
        // Con una búsqueda puesta no se pinta: buscar atraviesa los períodos (ver `visibleDays`),
        // así que un encabezado que dijera «septiembre» encima de resultados de marzo estaría
        // mintiendo sobre lo que se ve.
        if (searchQuery.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Medida.margenDePantalla - (Medida.objetivoTactil - Icono.l) / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BotonDeIcono(
                    Icons.Rounded.KeyboardArrowLeft,
                    "Período anterior",
                    onClick = { periodoVisible = periodoAnterior(periodoVisible) },
                )
                // Tocar el mes abre «¿cuándo empezó este?». Va acá y no en Perfil a propósito: la
                // pregunta aparece justo cuando el dueño está mirando las cifras de ESE mes y nota
                // que el sueldo cayó del lado equivocado. Ver [InicioDelPeriodoSheet].
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Medida.objetivoTactil)
                        .clickable(onClickLabel = "Cambiar el inicio del período") { editandoElInicio = true },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = nombreDe(periodoVisible).replaceFirstChar { it.uppercase() },
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                    )
                    // Mientras el perfil no contestó, un esqueleto reserva el lugar de esta línea
                    // —si no, aparecía al llegar el corte y empujaba la lista—, salvo que la última
                    // carga que salió bien no la tuviera (corte 1, ver `formaDeMovimientos`).
                    if (!perfilLeido && formaDeMovimientos?.lineaDePeriodo != false) {
                        LineaEsqueleto(
                            fraccionDelAncho = 0.5f,
                            estilo = Movi.textos.apoyo,
                            modifier = Modifier.testTag(TAG_LINEA_DE_PERIODO_ESQUELETO),
                        )
                    } else if (perfilLeido) {
                        // Con corte 1 esto es `null` y no se pinta: no hay nada que aclarar sobre
                        // un mes de calendario. Con cualquier otro corte es lo único que explica
                        // por qué «septiembre» empieza en agosto.
                        rangoLegibleDe(periodoVisible, ajustesDelPeriodo)?.let { rango ->
                            Text(
                                text = rango,
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.testTag(TAG_LINEA_DE_PERIODO),
                            )
                        }
                    }
                }
                if (puedeAvanzarDePeriodo(periodoVisible, periodoDeHoy)) {
                    BotonDeIcono(
                        Icons.Rounded.KeyboardArrowRight,
                        "Período siguiente",
                        onClick = { periodoVisible = periodoSiguiente(periodoVisible) },
                    )
                } else {
                    // El período en curso no tiene siguiente: la flecha queda, apagada, para que el
                    // mes no se corra de lugar.
                    Box(Modifier.size(Medida.objetivoTactil), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.KeyboardArrowRight,
                            contentDescription = "Período siguiente",
                            tint = Movi.colores.textoApagado,
                            modifier = Modifier.size(Icono.l),
                        )
                    }
                }
            }
        }

        // Los filtros: el tipo, y lo que pidió otra pantalla (una búsqueda de «Buscar» o «En este
        // comercio», una categoría de «En qué se va»), cada uno con su × para quitarlo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Medida.margenDePantalla)
                .padding(top = Espacio.xs),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            if (searchQuery.isNotBlank()) {
                PildoraDeFiltro(
                    texto = "«${searchQuery.trim()}»",
                    seleccionada = true,
                    icono = Icons.Rounded.Close,
                    onClick = { searchQuery = "" },
                    modifier = Modifier.testTag(TAG_CHIP_DE_LA_BUSQUEDA).semantics { contentDescription = "Quitar la búsqueda" },
                )
            }
            filtro?.let { activo ->
                PildoraDeFiltro(
                    texto = "Solo ${activo.rotulo}",
                    seleccionada = true,
                    icono = Icons.Rounded.Close,
                    onClick = { filtro = null },
                    modifier = Modifier.testTag(TAG_CHIP_DEL_FILTRO).semantics { contentDescription = "Quitar el filtro" },
                )
            }
            CHIPS_VISIBLES.forEach { chip ->
                PildoraDeFiltro(
                    texto = filters[chip],
                    seleccionada = chip == activeFilter,
                    onClick = { activeFilter = chip },
                )
            }
        }

        // Lo que va arriba de las filas, cada cosa con su aire debajo. **Va adentro de la lista y se
        // va con el scroll** (vuelta de diseño 1): fijos quedan solo el encabezado, el selector del
        // período y los chips. Afuera, la tarjeta «N por revisar» (≈125 dp) se quedaba clavada arriba
        // para siempre, se comía un tercio de la pantalla y las filas se asomaban por sus esquinas
        // redondeadas al pasar por debajo. Lo prueba `LoDeArribaSeVaConElScrollTest`.
        val arribaDeLaLista = Modifier.padding(horizontal = Medida.margenDePantalla).padding(top = Medida.dentroDeSeccion)

        val textoDeLosRechazados = textoDeRechazados(rechazados)
        // Ola C, tarea 5: **una sola propuesta** por todo lo que entró solo y espera una decisión —los
        // movimientos por confirmar, los mensajes del banco y los pagos de tarjeta sin marcar— en
        // vez de un aviso por fuente. Abre la bandeja «Por revisar», donde se resuelve cada uno.
        //
        // Espera a que las tres lecturas terminen: pintarla con la primera que llega y corregir el
        // número con la segunda sería una cifra que baila, y con las tres en cero no ocupa lugar.
        //
        // Durante una recarga (cada «Reintentar», cada guardado desde Agregar) se queda con el
        // último número que se contó: desmontarla mientras se lee hacía saltar la lista y volver.
        // Solo se va cuando una lectura que terminó dice cero.
        //
        // Con lo recordado de la visita anterior —las tres fuentes a la vista desde el primer cuadro—
        // se cuenta ya, como se pinta ya la lista: esperar a que contesten haría aparecer la tarjeta
        // DESPUÉS de la lista y la empujaría hacia abajo.
        val lasTresALaVista = perfilLeido && diasLeidos && porRevisar.mensajes != null && porRevisar.candidatos != null
        val conteoFresco = if ((!loading && porRevisar.terminaron) || lasTresALaVista) {
            cuantosPorRevisar(
                mensajes = porRevisar.mensajes,
                dias = if (diasLeidos) allDays else null,
                candidatos = porRevisar.candidatos,
                debitos = porRevisar.debitos,
            )
        } else {
            null
        }
        SideEffect { if (conteoFresco != null) ultimoConteoPorRevisar.value = conteoFresco }
        val cuantosPorRevisarAhora = conteoFresco ?: ultimoConteoPorRevisar.value
        // Adentro de un modo sin chip («Entre cuentas»), el título que dice dónde está y cómo
        // salir. Hace falta porque sin chip **ningún chip queda marcado**: la lista se vería
        // filtrada sin nada que explicara por qué.
        val tituloDelModo = tituloDelModoSinChip(activeFilter)

        // Tirar hacia abajo pide lo mismo que «Reintentar»: `refreshKey` (ver [TirarParaActualizar]).
        TirarParaActualizar(
            cargando = loading,
            onActualizar = { refreshKey++ },
            modifier = Modifier.weight(1f),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // El aire de abajo deja la última fila por encima del botón de Agregar.
            contentPadding = PaddingValues(bottom = Medida.botonFlotante + Medida.margenDePantalla),
        ) {
            if (textoDeLosRechazados != null) {
                item(contentType = "arriba") {
                    Superficie(modifier = arribaDeLaLista.fillMaxWidth()) {
                        Text(text = textoDeLosRechazados, style = Movi.textos.cuerpo, color = Movi.colores.aviso)
                    }
                }
            }
            if (cuantosPorRevisarAhora != null && cuantosPorRevisarAhora > 0) {
                item(contentType = "arriba") {
                    RenglonPorRevisar(
                        cuantos = cuantosPorRevisarAhora,
                        onClick = { onNavigate(Screen.PorRevisar) },
                        modifier = arribaDeLaLista,
                    )
                }
            }
            if (tituloDelModo != null) {
                item(contentType = "arriba") {
                    Seccion(
                        titulo = tituloDelModo,
                        accion = "Ver todos",
                        onAccion = { activeFilter = CHIP_TODO },
                        modifier = arribaDeLaLista,
                    )
                }
            }
            // Con algo ya pintado, recargar lo dice «Actualizando…» en la cabecera (arriba), como el
            // Inicio; sin nada pintado todavía, las filas esqueleto de más abajo ya dicen «cargando»
            // con la forma de lo que viene. Lo que sí va acá es la lectura que FALLÓ con la lista a la
            // vista: lo que se ve es lo último que vimos, y tiene que decirlo hasta que una lectura
            // conteste.
            if (noSePudoActualizar) {
                item(contentType = "arriba") {
                    NoSePudoActualizar(onReintentar = { refreshKey++ }, modifier = arribaDeLaLista)
                }
            }

            // Mientras carga, la forma real: dos o tres DÍAS, cada uno con su encabezado y su
            // superficie. Ver [movimientosEsqueleto].
            if (loading && visibleDays.isEmpty()) {
                movimientosEsqueleto()
            }

            // Con el aviso de arriba puesto, un vacío no se afirma: saldría de lo que se leyó en
            // otra visita, y «Sin movimientos aún» tiene que venir de una lectura que contestó en
            // esta. Queda solo el aviso, con su «Reintentar».
            if (!loading && visibleDays.isEmpty() && !noSePudoActualizar) {
                item {
                    val alrededor = Modifier.padding(horizontal = Medida.margenDePantalla).padding(top = Espacio.xxxl)
                    if (!diasLeidos) {
                        NoSePudoLeer("No pudimos cargar tus movimientos", onReintentar = { refreshKey++ }, modifier = alrededor)
                    } else if (searchQuery.isNotBlank()) {
                        // La búsqueda no dio resultados: el texto buscado es la pista que hace falta,
                        // y quitarla es lo que lo arregla.
                        Vacio(
                            titulo = "Nada coincide con «${searchQuery.trim()}»",
                            detalle = null,
                            accion = "Ver todos los movimientos",
                            onAccion = { searchQuery = "" },
                            modifier = alrededor,
                        )
                    } else {
                        // F10: el vacío ofrece la acción — registrar si ya hay dónde anotar, crear
                        // una cuenta primero si no. Pero solo cuando el vacío es «no hay nada
                        // anotado»: si es un chip el que dejó la lista vacía, se dice eso y no se
                        // ofrece nada (ver [vacioDeMovimientos]).
                        val hayMovimientos = allDays.any { d -> d.items.any { !isOpeningBalance(it) } }
                        val vacio = vacioDeMovimientos(activeFilter, hayMovimientos)
                        val hayCuentaParaRegistrar = accounts.isNotEmpty() || !accountsLoaded
                        Vacio(
                            titulo = vacio.titulo,
                            detalle = vacio.detalle,
                            accion = if (vacio.ofreceRegistrar) {
                                if (hayCuentaParaRegistrar) "Registrar el primero" else "Crear una cuenta primero"
                            } else null,
                            onAccion = if (vacio.ofreceRegistrar) {
                                {
                                    if (hayCuentaParaRegistrar) onNavigate(Screen.QuickAdd())
                                    else showCreateSheet = true
                                }
                            } else null,
                            modifier = alrededor,
                        )
                    }
                }
            }

            visibleDays.forEachIndexed { indiceDelDia, day ->
                // Sin `key`: con la fecha como clave, `LazyColumn` ancla el primer día visible al
                // cambiar de chip, y pasar de «Gastos» a «Todo» dejaba el día nuevo de arriba
                // escondido por encima del tope (visto a ojo en la web). Posicional, como antes.
                item {
                    val rows = agruparAjustesDeSaldo(collapseTransfers(day.items), searchQuery)
                    val plegado = day.date in diasPlegados
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Medida.margenDePantalla)
                            .padding(top = aireArribaDelDia(indiceDelDia)),
                    ) {
                        // **El día en un renglón** (ola R4): la fecha y el flujo del día. Todo el
                        // renglón pliega y despliega el día; plegado sigue diciendo el flujo y
                        // cuántos renglones esconde. El «Día a día» (cuánto podías gastar) salió:
                        // esa comparación es de Plan.
                        EncabezadoDelDia(
                            fecha = formatDayHeading(day.date, hoyIso),
                            total = day.total,
                            plegado = plegado,
                            cuantos = cuantosMovimientosDice(rows),
                            onAlternar = { diasPlegados = DiasPlegadosStore.alternar(day.date) },
                        )
                        // Ola W: «Flujo del día» YA incluye el pago de tarjeta (ver `diasVisibles`) —
                        // esta línea aclara que esa parte no se duplica en el gasto del período.
                        val montoPagoDeTarjeta = montoPagoDeTarjetaEnElDia(day.items, accountTypes)
                        val textoPagoDeTarjeta = textoPagoDeTarjetaEnElDia(montoPagoDeTarjeta)
                        if (textoPagoDeTarjeta != null) LineaPagoDeTarjetaEnElDia(textoPagoDeTarjeta)
                        if (!plegado) Superficie(
                            modifier = Modifier.fillMaxWidth(),
                            relleno = RellenoDeSuperficie.Lista,
                        ) {
                            rows.forEachIndexed { i, row ->
                                Column {
                                    when (row) {
                                        is MovementRow.Transfer -> TransferRow(
                                            row = row,
                                            accountNames = accountNames,
                                            accountTypes = accountTypes,
                                            // La pata del dinero, que es la que se reconoce como
                                            // cuota — ver [TransferRow].
                                            esRecurrente = nombreRecurrenteDe(
                                                row.out,
                                                reglasRecurrentes,
                                                nombresDeSuscripcionesActivas,
                                            ) != null,
                                            // Al tocarlo se abre la hoja sobre la pata de origen —
                                            // que se niega a recategorizar y explica por qué.
                                            elegido = conPanel && selectedEvent?.id == row.out.id,
                                            onClick = { elegir(row.out) },
                                        )
                                        // Ola 2b: la fila se desliza — a la izquierda abre la
                                        // categoría, a la derecha confirma un aviso pendiente. En
                                        // pantalla ancha no: ahí se usa con mouse y el panel de al
                                        // lado ya es el detalle (ver [FilaDeslizable]).
                                        is MovementRow.Single -> FilaDeslizable(
                                            habilitada = !conPanel,
                                            puedeConfirmar = row.event.reconciliationStatus == ReconciliationStatus.UNCONFIRMED,
                                            onCategoria = {
                                                elegir(row.event)
                                                accionAlAbrir = AccionDelMovimiento.Categoria
                                            },
                                            onConfirmar = { confirmarDesdeLaFila(row.event) },
                                        ) {
                                            MovementSingleRow(
                                                tx = row.event,
                                                accountNames = accountNames,
                                                esRecurrente = nombreRecurrenteDe(
                                                    row.event,
                                                    reglasRecurrentes,
                                                    nombresDeSuscripcionesActivas,
                                                ) != null,
                                                elegido = conPanel && selectedEvent?.id == row.event.id,
                                                onClick = { elegir(row.event) },
                                            )
                                        }
                                        is MovementRow.Ajustes -> {
                                            val abierto = row.key in ajustesAbiertos
                                            RenglonDeAjustes(
                                                events = row.events,
                                                abierto = abierto,
                                                onAlternar = {
                                                    ajustesAbiertos = if (abierto) ajustesAbiertos - row.key
                                                    else ajustesAbiertos + row.key
                                                },
                                            )
                                            // Abiertos, los ajustes se ven y se tocan igual que
                                            // cualquier renglón: el grupo cambia dónde están, no
                                            // qué se puede hacer con ellos.
                                            if (abierto) row.events.forEach { ajuste ->
                                                Separador()
                                                MovementSingleRow(
                                                    tx = ajuste,
                                                    accountNames = accountNames,
                                                    elegido = conPanel && selectedEvent?.id == ajuste.id,
                                                    onClick = { elegir(ajuste) },
                                                )
                                            }
                                        }
                                    }
                                    if (i < rows.size - 1) Separador()
                                }
                            }
                        }
                    }
                }
            }
        }
        } // TirarParaActualizar

    }
        },
        panel = {
            val enElPanel = movimientoDelPanel(selectedEvent, eventos.valor)
            // Vuelta de diseño 9: sin nada elegido, el resumen del período que la lista tiene a la
            // vista (ver `ResumenEnElPanel.kt`), sobre la misma historia. Sin historia leída todavía,
            // la invitación de siempre.
            // Tu parte de lo que pagaron otros, para que el resumen cuente lo mismo que «En qué se va»
            // y el widget del Inicio (R3, «solo tu parte»): la MISMA lectura que hace «En qué se va»,
            // con la caché del repositorio. Solo con el panel a la vista: el teléfono no la pide.
            LaunchedEffect(refreshKey, refreshTick) {
                tuParteDeOtros = runCatching { Repositories.wallets.getTuParteDeOtros() }.getOrDefault(emptyList())
            }
            val historia = eventos.valor
            val resumen = remember(historia, tuParteDeOtros, periodoVisible, ajustesDelPeriodo, hoyIso, accountTypes, perfilLeido) {
                if (historia == null || !perfilLeido) null
                else resumenDelPanel(
                    historia, periodoVisible, ajustesDelPeriodo, LocalDate.parse(hoyIso), accountTypes,
                    tuParteDeOtros = tuParteDeOtros,
                )
            }
            if (enElPanel == null && resumen != null) {
                ResumenDelPeriodoEnElPanel(
                    resumen = resumen,
                    // Como desde «En qué se va»: los gastos de esa categoría, con su chip y su ×.
                    onCategoria = { fila ->
                        filtro = FiltroDeMovimientos.deLaFila(fila, VistaDelAnalisis.CATEGORIA)
                        activeFilter = CHIP_GASTOS
                    },
                    onVerEnQueSeVa = { onNavigate(Screen.EnQueSeVa(periodoVisible.prefijo)) },
                )
            } else if (enElPanel == null) {
                InvitacionAElegirUnMovimiento()
            } else {
                // `key`: otro movimiento es otro panel, con su estado desde cero (un campo a medio
                // escribir no pasa al siguiente). El mismo movimiento releído conserva el suyo.
                key(enElPanel.id) {
                    PanelDelMovimiento(
                        event = enElPanel,
                        cuentas = accounts,
                        onCerrar = { elegir(null) },
                        // No cierra nada: relee, y el panel sigue al movimiento por id.
                        onCambiado = { refreshKey++ },
                        onAnulado = { elegir(null) },
                        onVerCuenta = accountTypes[enElPanel.accountId]?.let { tipo ->
                            { onNavigate(Screen.AccountDetail(enElPanel.accountId, tipo.group)) }
                        },
                        enEsteComercio = remember(enElPanel, allDays, ajustesDelPeriodo) { enEsteComercioDe(enElPanel) },
                        // En el panel el movimiento sigue a la vista: solo cambia la lista de al lado.
                        onVerElComercio = ::buscarElComercio,
                    )
                }
            }
        },
    )

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Medida.dentroDeSeccion),
    )

    // Con el panel al lado, el movimiento elegido va ahí y no se abre ninguna hoja.
    if (!conPanel && !soltarLoDelPanel) selectedEvent?.let { event ->
        // El mismo juego de hojas que abre el detalle de la cuenta — categoría, fecha, monto,
        // cuenta, concepto, «esto se repite» y anular — porque es el mismo movimiento tocado.
        // Ver [HojaDelMovimiento] para por qué está afuera de esta pantalla.
        HojaDelMovimiento(
            event = event,
            // Para poder mover el movimiento de cuenta. Es la MISMA lista que ya alimenta
            // `accountNames`/`accountTypes` de esta pantalla — no una lectura nueva.
            cuentas = accounts,
            onDismiss = { selectedEvent = null; accionAlAbrir = null },
            onCambiado = { selectedEvent = null; accionAlAbrir = null; refreshKey++ },
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
            accionInicial = accionAlAbrir,
            enEsteComercio = remember(event, allDays, ajustesDelPeriodo) { enEsteComercioDe(event) },
            // La hoja se cierra y la lista queda filtrada por ese comercio.
            onVerElComercio = { busqueda ->
                selectedEvent = null
                accionAlAbrir = null
                buscarElComercio(busqueda)
            },
        )
    }

    if (editandoElInicio) {
        InicioDelPeriodoSheet(
            periodo = periodoVisible,
            ajustes = ajustesDelPeriodo,
            saving = guardandoInicio,
            error = errorDelInicio,
            onDismiss = { editandoElInicio = false; errorDelInicio = null },
            onVerPeriodos = { editandoElInicio = false; onNavigate(Screen.Periodos) },
            onSave = { inicio ->
                if (!guardandoInicio) {
                    guardandoInicio = true
                    errorDelInicio = null
                    // `null` = quitar la excepción. Ver [guardarInicioDelPeriodo]: relee el perfil
                    // antes de escribir, así que un perfil que no se pudo leer al abrir la pantalla
                    // no borra las demás excepciones.
                    val periodo = periodoVisible
                    alcanceDeLaPantalla.launch {
                        intentar { guardarInicioDelPeriodo(periodo, inicio) }
                            .onSuccess {
                                perfil.anotar(it)
                                editandoElInicio = false
                            }
                            .onFailure { errorDelInicio = it.toUserMessage() }
                        guardandoInicio = false
                    }
                }
            },
        )
    }

    if (showCreateSheet) {
        CreateAccountSheet(
            onDismiss = { showCreateSheet = false },
            onAccountCreated = { showCreateSheet = false; refreshKey++ },
        )
    }
    }
    } // MedirPanelDeMovimientos
}

/**
 * El color de cada tono, tomado de la paleta que esté puesta.
 *
 * ### Por qué recibe la paleta en vez de leerla
 *
 * La versión anterior devolvía constantes de `Color.kt` y su comentario decía que «si algún día
 * llega el tema claro, se cambia en Color.kt y esto lo sigue». **Eso era falso, y no por poco:**
 * los `Min*` son `val` de nivel superior, o sea un valor único por proceso. No hay forma de que
 * sigan a un tema, porque no pueden leer nada.
 *
 * Hacerla `@Composable` la arreglaría a medias y rompería lo que más importa: `ColorYEntreCuentasTest`
 * afirma que gasto, ingreso y entre-cuentas son **tres colores distintos** —el reporte del dueño
 * que le dio origen a esta función— y una función composable no se puede llamar desde una prueba
 * común.
 *
 * Recibiendo la paleta es una función pura: la pantalla le pasa `Movi.colores` y la prueba le pasa
 * las dos paletas, así que ahora la distinción se verifica **en los dos temas**, no en uno.
 */
fun colorDelTono(tono: TonoDelMonto, colores: ColoresDeMovi): Color =
    colorDeLaCifra(tonoDeCifraDelTono(tono), colores)

/**
 * **El tono del sistema que le toca a cada tono de un movimiento** (ola R4): el gasto en el color del
 * texto (lo distingue el «−»), el ingreso en `entra` con «+», el traspaso en `entreCuentas` y sin
 * signo, y lo que no movió plata del bolsillo (la apertura, la pata huérfana) en gris. Así ninguna
 * fila elige su color a mano: todas pintan con la [Cifra] del sistema.
 */
fun tonoDeCifraDelTono(tono: TonoDelMonto): TonoDeCifra = when (tono) {
    TonoDelMonto.GASTO -> TonoDeCifra.Neutro
    TonoDelMonto.INGRESO -> TonoDeCifra.Ingreso
    TonoDelMonto.ENTRE_CUENTAS -> TonoDeCifra.EntreCuentas
    TonoDelMonto.NEUTRO -> TonoDeCifra.Apagado
}

/** El monto con el signo que la [Cifra] tiene que pintar: negativo solo para un gasto. */
fun montoParaLaCifra(tono: TonoDelMonto, monto: Long): Long = if (tono == TonoDelMonto.GASTO) -monto else monto

/** **El monto de un movimiento**: la [Cifra] del sistema, en el tono y la moneda del movimiento. */
@Composable
internal fun CifraDelMovimiento(
    monto: Long,
    moneda: String,
    tono: TonoDelMonto,
    modifier: Modifier = Modifier,
    variante: VarianteDeCifra = VarianteDeCifra.Fila,
) {
    Cifra(montoParaLaCifra(tono, monto), variante, modifier, tonoDeCifraDelTono(tono), moneda)
}

/** El tag de lo que se toca (y se resalta) en el encabezado de un día: sale un poco hacia el margen. */
const val TAG_TOQUE_DEL_DIA: String = "toque-del-dia"

/** El tag de la fecha del encabezado de un día. */
const val TAG_FECHA_DEL_DIA: String = "fecha-del-dia"

/** Las piezas del renglón de [EncabezadoDelDia], para que su `Layout` las encuentre. */
private enum class PiezaDelDia { Fecha, Cuantos, Monto, Chevron }

/**
 * **El encabezado de un día, en un renglón** (ola R4): la fecha a la izquierda —«Hoy», «Ayer», «23
 * de agosto», como se escribe, no en mayúsculas— y el flujo del día a la derecha. Es el título de la
 * sección de ese día, con el estilo de [Seccion]; el renglón entero pliega y despliega el día, y
 * plegado dice cuántos renglones esconde. Mide [Medida.objetivoTactil], igual que su esqueleto.
 *
 * V6: el total suma el FLUJO (lo que entró y salió del bolsillo), no todos los renglones de abajo —
 * la apertura de una cuenta queda afuera—.
 *
 * **Lo que cede cuando no cabe** (vuelta de diseño 2): en producción, plegado, decía «20 d… · 2
 * movimientos Flujo del día −$57.400»: se recortaba la fecha, que es lo único que nombra el día.
 * Ahora la fecha, la cifra y el chevron se miden primero y enteros; con lo que sobra va «· N
 * movimientos», cortado con «…» si no alcanza. Plegado y desplegado es el mismo renglón: solo
 * cambian el chevron y el conteo.
 *
 * **Sin el rótulo «Flujo del día»** (vuelta de diseño 14): la vuelta 2 ya lo hacía caer cuando no
 * cabía, y la cifra sola —chica, con su signo y en el color de lo que entró o salió— se entiende en
 * todos los anchos. Sobraba en cada día.
 */
@Composable
internal fun EncabezadoDelDia(
    fecha: String,
    total: Long,
    plegado: Boolean,
    cuantos: Int,
    onAlternar: () -> Unit,
) {
    // El tag va en una caja aparte: mide el borde del renglón, el mismo punto que
    // `TAG_ENCABEZADO_DE_DIA_ESQUELETO` en el esqueleto, sin el aire del resaltado.
    Box(modifier = Modifier.fillMaxWidth().testTag(TAG_ENCABEZADO_DE_DIA)) {
        SubcomposeLayout(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medida.objetivoTactil)
                .testTag(TAG_TOQUE_DEL_DIA)
                // Vuelta de diseño 16: en escritorio, el mouse encima pintaba una banda gris recta,
                // pegada a la fecha. El resaltado ahora sale un poco hacia el margen y lleva el radio
                // del sistema; la fecha y la cifra no se mueven (ver [renglonTocable]).
                .renglonTocable(
                    onClickLabel = if (plegado) "Desplegar el día" else "Plegar el día",
                    onClick = onAlternar,
                ),
        ) { restricciones ->
            val medibles = subcompose(PiezaDelDia.Fecha) {
                Text(
                    text = fecha,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.layoutId(PiezaDelDia.Fecha).testTag(TAG_FECHA_DEL_DIA).semantics { heading() },
                )
                if (plegado) {
                    Text(
                        text = if (cuantos == 1) "· 1 movimiento" else "· $cuantos movimientos",
                        style = Movi.textos.tituloRegular,
                        color = Movi.colores.textoMedio,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.layoutId(PiezaDelDia.Cuantos),
                    )
                }
                Cifra(
                    total,
                    VarianteDeCifra.Chica,
                    modifier = Modifier.layoutId(PiezaDelDia.Monto),
                    tono = if (total > 0) TonoDeCifra.Ingreso else TonoDeCifra.Neutro,
                )
                Icon(
                    imageVector = if (plegado) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                    contentDescription = if (plegado) "Desplegar el día" else "Plegar el día",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier.size(Icono.m).layoutId(PiezaDelDia.Chevron),
                )
            }
            fun pieza(cual: PiezaDelDia) = medibles.firstOrNull { it.layoutId == cual }
            val ancho = restricciones.maxWidth
            val suelta = restricciones.copy(minWidth = 0, minHeight = 0)
            val entre = Espacio.xs.roundToPx()
            val junto = Espacio.xxs.roundToPx()

            // Primero lo que nunca se recorta: el chevron, la cifra y la fecha.
            val chevron = pieza(PiezaDelDia.Chevron)!!.measure(suelta)
            val monto = pieza(PiezaDelDia.Monto)!!.measure(suelta)
            val paraLaFecha = (ancho - monto.width - chevron.width - 2 * entre).coerceAtLeast(0)
            val fechaP = pieza(PiezaDelDia.Fecha)!!.measure(suelta.copy(maxWidth = paraLaFecha))
            val sobra = paraLaFecha - fechaP.width

            // Después, con lo que sobra: el conteo, cortado con «…» si no entra entero.
            val cuantosP = pieza(PiezaDelDia.Cuantos)?.takeIf { sobra > junto }?.measure(suelta.copy(maxWidth = sobra - junto))

            val alto = maxOf(
                restricciones.minHeight,
                fechaP.height,
                cuantosP?.height ?: 0,
                monto.height,
                chevron.height,
            )
            layout(ancho, alto) {
                fun centrado(p: Placeable) = (alto - p.height) / 2
                fechaP.placeRelative(0, centrado(fechaP))
                cuantosP?.placeRelative(fechaP.width + junto, centrado(cuantosP))
                val xChevron = ancho - chevron.width
                chevron.placeRelative(xChevron, centrado(chevron))
                val xMonto = xChevron - entre - monto.width
                monto.placeRelative(xMonto, centrado(monto))
            }
        }
    }
}

/**
 * Lo que va al final de una fila de movimiento: el punto de «por confirmar», el ícono de repetición
 * y el monto (con «Tu parte» debajo si el gasto se repartió). El punto y el ícono dicen su nombre al
 * lector de pantalla: el color nunca es la única pista.
 */
@Composable
private fun FinalDeLaFila(
    monto: Long,
    moneda: String,
    tono: TonoDelMonto,
    porConfirmar: Boolean,
    esRecurrente: Boolean,
    tuParte: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xxs),
    ) {
        if (porConfirmar) {
            StatusDot(Movi.colores.aviso, modifier = Modifier.semantics { contentDescription = "Por confirmar" })
        }
        if (esRecurrente) {
            Icon(
                imageVector = Icons.Rounded.Repeat,
                contentDescription = "Recurrente",
                tint = Movi.colores.textoMedio,
                modifier = Modifier.size(Icono.s),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            CifraDelMovimiento(monto, moneda, tono)
            if (tuParte != null) {
                Text(
                    text = tuParte,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    modifier = Modifier.testTag(TAG_TU_PARTE),
                )
            }
        }
    }
}

/**
 * Un traspaso, leído como un solo hecho: "Traspaso · Ahorros → CDT" y el monto **sin signo**, en
 * el azul de [TonoDelMonto.ENTRE_CUENTAS].
 *
 * Sin `+` ni `−` a propósito: la plata no entró ni salió del bolsillo, solo cambió de cuenta.
 * (El desembolso de un crédito es la excepción: entró plata prestada, va en verde con «+» y una
 * nota que aclara que es prestada — suma en «Entró» — ver [esDesembolso].)
 *
 * Lleva **el mismo ícono de repetición** que [MovementSingleRow] cuando el par es una cuota de
 * crédito o el pago de una tarjeta. La marca sale de la pata del dinero — [MovementRow.Transfer.out].
 *
 * Es una [Fila] del sistema (ola R4). `internal`: la bandeja «Por revisar» pinta con este mismo
 * renglón lo que entró solo, para que un movimiento se vea igual allá y acá.
 */
@Composable
internal fun TransferRow(
    row: MovementRow.Transfer,
    accountNames: Map<String, String>,
    accountTypes: Map<String, AccountType>,
    esRecurrente: Boolean = false,
    elegido: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitulo = transferRowSubtitle(row, accountNames)
    Fila(
        titulo = transferRowTitle(row, accountTypes),
        // El desembolso dice que la plata es prestada; el resto, de dónde a dónde fue.
        subtitulo = if (esDesembolso(row, accountTypes)) "$NOTA_DE_DESEMBOLSO · $subtitulo" else subtitulo,
        modifier = Modifier.marcaDeElegido(elegido),
        // Neutro a propósito: un traspaso (o la cuota / el pago de tarjeta que se ven con esta
        // misma forma) no es una categoría del dueño, es plata que cambió de cuenta.
        inicio = { IconoDeCategoria(TRANSFER_CATEGORY) },
        final = {
            FinalDeLaFila(
                // En la moneda del movimiento: un traspaso de la Master Black USD no son pesos. Sin
                // signo y en el azul de entre cuentas; el desembolso, en verde con «+».
                monto = row.amount,
                moneda = row.out.currency,
                tono = tonoDelRenglon(row, accountTypes),
                porConfirmar = false,
                esRecurrente = esRecurrente,
            )
        },
        onClick = onClick,
    )
}

/**
 * El renglón que resume los ajustes de saldo de un día y los abre al tocarlo.
 *
 * **Sin monto a la derecha, y es lo honesto.** Cada ajuste lleva su delta, pero sumarlos no
 * significa nada: son deltas de cuentas distintas, algunas de activo y otras de deuda, y ninguno
 * es plata que entró o salió (`isCashFlow` los excluye). Lo que va en su lugar es cuántos hay, que
 * es lo que el renglón promete abrir.
 */
@Composable
private fun RenglonDeAjustes(
    events: List<FinancialEvent>,
    abierto: Boolean,
    onAlternar: () -> Unit,
) {
    Fila(
        titulo = tituloDeLosAjustes(events),
        subtitulo = if (events.size == 1) "$ADJUSTMENT_CATEGORY · 1 corrección"
        else "$ADJUSTMENT_CATEGORY · ${events.size} correcciones",
        // Un ajuste de saldo no es una categoría, es una corrección: el ícono neutro.
        inicio = { IconoDeCategoria(ADJUSTMENT_CATEGORY) },
        final = {
            Icon(
                imageVector = if (abierto) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (abierto) "Ocultar los ajustes" else "Ver los ajustes",
                tint = Movi.colores.textoApagado,
                modifier = Modifier.size(Icono.m),
            )
        },
        onClick = onAlternar,
    )
}

/**
 * Un renglón suelto de la lista: una [Fila] del sistema (ola R4) con la marca del comercio (o el
 * ícono de la categoría), la descripción, «categoría · cuenta» y el monto.
 *
 * Ola 8 · V7: el subtítulo dice **de qué cuenta es el movimiento** (con dos cuentas abiertas había
 * dos «Saldo inicial» idénticos). El punto de «por confirmar» y el ícono de repetición van junto al
 * monto ([FinalDeLaFila]); no son botones: para editar el recurrente se abre el movimiento.
 *
 * `internal`: la bandeja «Por revisar» y el detalle de una cuenta pintan con este mismo renglón, para
 * que un movimiento se vea igual en todas partes.
 */
@Composable
internal fun MovementSingleRow(
    tx: FinancialEvent,
    accountNames: Map<String, String>,
    esRecurrente: Boolean = false,
    /** Es el que muestra el panel de la derecha (Ola W4). Solo en pantalla ancha. */
    elegido: Boolean = false,
    /** Una línea más debajo de la fila (el detalle de una cuenta explica ahí un pago que no fue la cuota). */
    nota: String? = null,
    /**
     * El tono decidido por quien llama. El detalle de una cuenta lo usa para pintar el signo
     * siempre: ahí el total del día cuenta todo lo que mueve el saldo, no solo el flujo de caja.
     */
    tonoForzado: TonoDelMonto? = null,
    onClick: () -> Unit,
) {
    // Texto el gasto, verde el ingreso, gris lo que no movió plata del bolsillo — la apertura de una
    // cuenta, la pata huérfana, la cuota que paga un tercero. Una sola regla, ver [tonoDelEvento].
    val tono = tonoForzado ?: tonoDelEvento(tx)
    val subtitulo = accountNames[tx.accountId]
        ?.let { "${tx.category} · $it" }
        ?: tx.category
    Column {
        Fila(
            titulo = conCifrasOcultas(tx.description),
            subtitulo = subtitulo,
            // El tag de la fila va en la fila misma —la que se toca—, no en lo que la envuelve.
            modifier = Modifier.testTag(TAG_FILA_DE_MOVIMIENTO_SUELTO).marcaDeElegido(elegido),
            // La marca del comercio si Movi la reconoce (Ola 2a), y si no el ícono de la categoría.
            inicio = { AvatarDelComercio(tx) },
            final = {
                FinalDeLaFila(
                    // En la moneda del movimiento: un cargo de US$20 en la tarjeta en dólares no son pesos.
                    monto = tx.amount,
                    moneda = tx.currency,
                    tono = tono,
                    porConfirmar = tx.reconciliationStatus == ReconciliationStatus.UNCONFIRMED,
                    esRecurrente = esRecurrente,
                    // R3 · un gasto repartido: la plata salió entera (el monto), pero en tus gastos
                    // cuenta solo tu parte (ver `montoContado` en :core).
                    tuParte = if (tx.parteAjena > 0L) "Tu parte ${formatMoney(montoContado(tx), tx.currency)}" else null,
                )
            },
            onClick = onClick,
        )
        nota?.let {
            Text(
                text = it,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier
                    .padding(start = Medida.margenDePantalla + Medida.avatar + Espacio.s, end = Medida.margenDePantalla)
                    .padding(bottom = Espacio.xs),
            )
        }
    }
}

/** El `testTag` de «Tu parte $X» bajo el monto de un movimiento repartido. */
const val TAG_TU_PARTE: String = "tu-parte"

/**
 * La fila que muestra el panel de la derecha (Ola W4): el lavado de la marca y la semántica de
 * «elegida».
 */
@Composable
private fun Modifier.marcaDeElegido(elegido: Boolean): Modifier =
    if (!elegido) this
    else this
        .semantics { selected = true }
        .clip(RoundedCornerShape(Radio.s))
        .background(Movi.colores.lavado(Movi.colores.marca))

/**
 * Ola 4 · Ver la plata: [dias] con solo lo que deja pasar [filtro] (ver `FiltroDeMovimientos` en
 * `:core`), o tal cual sin filtro. Va antes de [diasVisibles], que recalcula el total de cada día
 * sobre lo que quedó.
 */
fun conElFiltro(dias: List<EventDay>, filtro: FiltroDeMovimientos?): List<EventDay> {
    if (filtro == null) return dias
    return dias.mapNotNull { dia ->
        val quedan = dia.items.filter { filtro.deja(it) }
        if (quedan.isEmpty()) null else dia.copy(items = quedan)
    }
}

/** El tag del chip del filtro que mandó «En qué se va», para las pruebas. */
const val TAG_CHIP_DEL_FILTRO: String = "chip-del-filtro"

/** El tag del chip de la búsqueda que llegó de «Buscar» o de «En este comercio». */
const val TAG_CHIP_DE_LA_BUSQUEDA: String = "chip-de-la-busqueda"
