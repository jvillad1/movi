package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.MovimientoRechazado
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.showsInMovements
import com.jvillada.movi.ui.profile.InicioDelPeriodoSheet
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
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
 * Un día que se queda sin filas se descarta entero (encabezado incluido): un día vacío con su
 * «Flujo del día» no le dice nada a nadie.
 */
fun diasVisibles(
    days: List<EventDay>,
    chip: Int,
    query: String,
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
            // La misma función que usa el server en `/by-day`. Antes acá se recalculaba sin mirar
            // la moneda, y un cobro en dólares habría restado su monto como si fueran pesos.
            total = filtered.sumOf { aporteAlFlujoDelDia(it) },
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
 * El tag de la barra de carga de Movimientos (Task 7, fix round 1): la que se pinta cuando se
 * recarga con la lista ya en pantalla — sin tag, una prueba no tiene cómo distinguirla.
 */
const val TAG_BARRA_DE_CARGA_DE_MOVIMIENTOS: String = "barra-de-carga-de-movimientos"

/**
 * El tag de un renglón suelto de Movimientos (Task 3, Ola B): sin él, una prueba no tiene forma de
 * medir el alto real del renglón para verificar que agregarle [IconoDeCategoria] no lo hizo crecer.
 */
const val TAG_FILA_DE_MOVIMIENTO_SUELTO: String = "fila-de-movimiento-suelto"

/**
 * El tag del renglón de encabezado de un día esqueleto («HOY · Flujo del día …»), para contar
 * cuántos GRUPOS pinta [movimientosEsqueleto] sin depender de ningún texto — no hay ninguno
 * todavía, ver su KDoc.
 */
const val TAG_ENCABEZADO_DE_DIA_ESQUELETO: String = "encabezado-de-dia-esqueleto"

/**
 * El tag del renglón de encabezado de un día REAL («HOY · Flujo del día …», el que pliega y
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
 * DÍAS, cada uno con su renglón de encabezado («HOY · Flujo del día −$250.100», ver más abajo en
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
    GRUPOS_DEL_ESQUELETO.forEach { filas ->
        item {
            // `top = 20.dp` SIEMPRE, sin excepción para el primer grupo — fix round 1. El
            // `Column` de cada día real (más abajo, en el `forEach` de `visibleDays`) usa
            // `padding(top = 20.dp)` para TODOS los días, primero incluido: el `contentPadding`
            // de esta `LazyColumn` no trae ningún `top`, así que ese es el único aire arriba del
            // primer renglón. Un `0.dp` acá adelantaba el primer grupo 20 dp contra el primer día
            // real y la lista saltaba hacia abajo apenas llegaban los datos.
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    RenglonConCifraEsqueleto(fraccionDelRotulo = 0.2f, anchoDeLaCifra = 110.dp)
                }
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    // El mismo círculo que `IconoDeCategoria` — ver el KDoc de
                    // `FilaDeListaEsqueleto` (Task 3, fix round 1). Leído del tamaño y no copiado:
                    // si el ícono cambia de medida, el esqueleto no se queda atrás.
                    repeat(filas) { i ->
                        FilaDeListaEsqueleto(
                            isLast = i == filas - 1,
                            diametroIconoAlFrente = TamanoDeIconoDeCategoria.Normal.circulo,
                        )
                    }
                }
            }
        }
    }
}

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

    var allDays by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    /**
     * El día en que arranca el mes del dueño. Sale de su perfil, igual que en Presupuestos; si la
     * lectura falla queda en 1 —mes de calendario—, que es el comportamiento de siempre.
     */
    var cutoffDay by remember { mutableStateOf(1) }
    /** Los períodos que el dueño declaró que arrancaron otro día. Ver `PeriodSettings.iniciosPropios`. */
    var iniciosPropios by remember { mutableStateOf(emptyMap<String, String>()) }
    // Ola B, tarea 2: distingue «todavía no sabemos el corte» (`cutoffDay` en su default de 1) de
    // «ya se leyó y de verdad es corte 1» — sin esto, la línea del rango (`rangoLegibleDe`)
    // aparecía recién cuando el perfil contestaba y empujaba toda la lista de abajo. Se prende con
    // éxito O con fallo del perfil: los dos son «ya sabemos qué mostrar».
    var perfilLeido by remember { mutableStateOf(false) }
    /**
     * Whole-branch review, final fix wave: distinto de [perfilLeido] — este solo se prende con una
     * lectura que salió BIEN, porque es lo único que vale la pena recordar en `FormaRecordada`
     * (ver su KDoc: «los números... la última vez que su lectura salió bien»). Grabar también un
     * fallo dejaría una `FormaDeMovimientos` mintiendo sobre el corte real la próxima vez que se
     * abra la pantalla.
     */
    var perfilOk by remember { mutableStateOf(false) }
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
    var loading by remember { mutableStateOf(true) }  // true de entrada: antes de la primera lectura no se afirma ni vacío ni error
    val errorDeLaPantalla = remember { mutableStateOf<String?>(null) }
    var error by errorDeLaPantalla
    var refreshKey by remember { mutableStateOf(0) }
    // Se prende solo cuando `getEventsByDay` contestó de verdad (ver [NoSePudoLeer]). Sin esto, una
    // lectura caída dejaba «Sin movimientos aún · + Registrar el primero» a quien tiene cientos.
    var diasLeidos by remember { mutableStateOf(false) }
    /** Lo anotado en este teléfono que el server rechazó (ver [textoDeRechazados]). */
    var rechazados by remember { mutableStateOf<List<MovimientoRechazado>>(emptyList()) }
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

    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }

    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getUserProfile() }
            .onSuccess { cutoffDay = it.periodCutoffDay; iniciosPropios = it.periodStarts; perfilOk = true }
            // Sin el perfil la pantalla cae al mes de calendario, y con corte 25 eso es mostrar
            // otro período con otro total. Se dice, con el mismo «Reintentar» de siempre; si
            // además fallan los movimientos, ese error (abajo) es el que manda.
            .onFailure { error = PERIODO_NO_LEIDO }
        perfilLeido = true
        runCatching { Repositories.wallets.getEventsByDay() }
            .onSuccess {
                allDays = it
                diasLeidos = true
                // F35: de paso, alimenta el caché de "categorías ya usadas" que lee
                // CategoryField — esta pantalla ya carga los movimientos. Ola 9 · A3: con el
                // tipo de cada uno, para poder ofrecerlas del lado correcto.
                UsedCategoriesCache.recordAll(it.flatMap { d -> d.items }.map { ev -> ev.category to ev.type })
            }
            .onFailure { e -> error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(refreshKey, refreshTick) {
        runCatching { Repositories.wallets.getMovimientosRechazados() }.onSuccess { rechazados = it }
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

    val ajustesDelPeriodo = remember(cutoffDay, iniciosPropios) {
        PeriodSettings(cutoffDay = cutoffDay.coerceIn(1, 31), iniciosPropios = iniciosPropios)
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
     */
    var periodoVisible by remember(periodoDeHoy) { mutableStateOf(periodoDeHoy) }

    val visibleDays = remember(activeFilter, allDays, searchQuery, periodoVisible, ajustesDelPeriodo) {
        val filtrados = diasVisibles(allDays, activeFilter, searchQuery)
        // **Buscar atraviesa los períodos**, por cuarta vez en esta pantalla y por el mismo motivo
        // que las otras tres: escribir una consulta es pedir que algo aparezca, y encontrarlo solo
        // si además caía en el mes que estabas mirando es la peor forma de no encontrarlo.
        if (searchQuery.isNotBlank()) filtrados
        else diasDelPeriodo(filtrados, periodoVisible, ajustesDelPeriodo)
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // F60: encabezado único — Movimientos es raíz: avatar + rótulo del menú + la lupa.
        MinScreenHeader(
            title = "Movimientos",
            leading = HeaderLeading.Avatar(onNavigate),
            action = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = Movi.colores.textoMedio,
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
                    .background(Movi.colores.tarjeta)
                    .border(1.dp, Movi.colores.borde, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Movi.colores.textoApagado, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Descripción, comercio o categoría", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado)
                    }
                    // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                    // [esAtajoDeSeleccionarTodo].
                    val campo = rememberCampoConSeleccion(searchQuery) { searchQuery = it }
                    BasicTextField(
                        value = campo.valor,
                        onValueChange = campo::alCambiar,
                        singleLine = true,
                        textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                        cursorBrush = SolidColor(Movi.colores.texto),
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar búsqueda",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier.size(16.dp).clickable {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            }
        }

        // **De qué mes son estas cifras**, y cómo moverse entre meses.
        //
        // Con la búsqueda abierta no se pinta: buscar atraviesa los períodos (ver `visibleDays`),
        // así que un encabezado que dijera «septiembre» encima de resultados de marzo estaría
        // mintiendo sobre lo que se ve.
        if (!searchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = "Período anterior",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier.size(22.dp).clickable {
                        periodoVisible = periodoAnterior(periodoVisible)
                    },
                )
                // Tocar el mes abre «¿cuándo empezó este?». Va acá y no en Perfil a propósito: la
                // pregunta aparece justo cuando el dueño está mirando las cifras de ESE mes y nota
                // que el sueldo cayó del lado equivocado. Ver [InicioDelPeriodoSheet].
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { editandoElInicio = true },
                ) {
                    Text(
                        text = nombreDe(periodoVisible).replaceFirstChar { it.uppercase() },
                        style = Movi.textos.cuerpo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.texto,
                    )
                    // Ola B, tarea 2: mientras el perfil no contestó, un esqueleto reserva el
                    // lugar de esta línea — sin él, aparecía recién cuando el perfil traía el
                    // corte de verdad y empujaba toda la lista de abajo (el esqueleto de
                    // Movimientos incluido). Con corte 1 (mes de calendario, ya confirmado) no
                    // hay nada que aclarar y no se pinta nada, como siempre.
                    //
                    // Whole-branch review, final fix wave: reservarla SIEMPRE, para todos, tenía
                    // su propio costo — un dueño con corte 1 (nunca tuvo línea) veía el esqueleto
                    // subir ~18 dp apenas el perfil contestaba. `formaDeMovimientos` dice si la
                    // última carga que salió bien tenía línea; `null` (nada recordado, la primera
                    // vez) sigue reservando, como siempre.
                    if (!perfilLeido && formaDeMovimientos?.lineaDePeriodo != false) {
                        Spacer(Modifier.height(2.dp))
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
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = rango,
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoApagado,
                                modifier = Modifier.testTag(TAG_LINEA_DE_PERIODO),
                            )
                        }
                    }
                }
                val puedeAvanzar = puedeAvanzarDePeriodo(periodoVisible, periodoDeHoy)
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = "Período siguiente",
                    tint = if (puedeAvanzar) Movi.colores.textoMedio else Movi.colores.textoApagado,
                    modifier = Modifier.size(22.dp).then(
                        if (puedeAvanzar) Modifier.clickable {
                            periodoVisible = periodoSiguiente(periodoVisible)
                        } else Modifier
                    ),
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
            CHIPS_VISIBLES.forEach { chip ->
                val f = filters[chip]
                val isActive = chip == activeFilter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Movi.colores.tarjeta else Color.Transparent)
                        .then(
                            if (!isActive) Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { activeFilter = chip }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isActive) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = f,
                        style = Movi.textos.cuerpo,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Movi.colores.texto else Movi.colores.textoMedio,
                    )
                }
            }
        }

        textoDeRechazados(rechazados)?.let { texto ->
            MinCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                variant = MinCardVariant.Default,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(text = texto, style = Movi.textos.cuerpo, color = Movi.colores.aviso)
            }
        }
        // Ola C, tarea 5: **un solo renglón** por todo lo que entró solo y espera una decisión —los
        // movimientos por confirmar, los mensajes del banco y los pagos de tarjeta sin marcar— en
        // vez de un aviso por fuente. Abre la bandeja «Por revisar», donde se resuelve cada uno.
        //
        // Espera a que las tres lecturas terminen: pintarlo con la primera que llega y corregir el
        // número con la segunda sería una cifra que baila, y con las tres en cero no ocupa lugar.
        //
        // Durante una recarga (cada «Reintentar», cada guardado desde Agregar) se queda con el
        // último número que se contó: desmontarlo mientras se lee hacía saltar la lista ~56 dp y
        // volver. Solo se va cuando una lectura que terminó dice cero.
        val conteoFresco = if (!loading && porRevisar.terminaron) {
            cuantosPorRevisar(
                mensajes = porRevisar.mensajes,
                dias = if (diasLeidos) allDays else null,
                candidatos = porRevisar.candidatos,
            )
        } else {
            null
        }
        SideEffect { if (conteoFresco != null) ultimoConteoPorRevisar.value = conteoFresco }
        val cuantosPorRevisarAhora = conteoFresco ?: ultimoConteoPorRevisar.value
        if (cuantosPorRevisarAhora != null && cuantosPorRevisarAhora > 0) {
            RenglonPorRevisar(
                cuantos = cuantosPorRevisarAhora,
                onClick = { onNavigate(Screen.PorRevisar) },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }
        // Adentro de un modo sin chip («Entre cuentas»), el encabezado que dice dónde está y cómo
        // salir. Hace falta porque sin chip **ningún chip queda marcado**: la lista se vería
        // filtrada sin nada que explicara por qué.
        val tituloDelModo = tituloDelModoSinChip(activeFilter)
        if (tituloDelModo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tituloDelModo,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Text(
                    text = "Ver todos",
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.marca,
                    modifier = Modifier.clickable { activeFilter = CHIP_TODO },
                )
            }
        }

        // Task 7, fix round 1: con algo ya pintado (volver a este chip, un reintento con la lista
        // de antes en pantalla) la barra de siempre; sin nada pintado todavía, las filas esqueleto
        // de más abajo ya dicen «cargando» con la forma de lo que viene, y la barra sería la misma
        // señal dos veces.
        if (loading && visibleDays.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(TAG_BARRA_DE_CARGA_DE_MOVIMIENTOS))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            // Task 7 puso una tarjeta de 6 filas sueltas; la Task 9 (ola B) la cambia por la forma
            // real — 2-3 DÍAS, cada uno con su encabezado y su propia tarjeta. Ver
            // [movimientosEsqueleto].
            if (loading && visibleDays.isEmpty()) {
                movimientosEsqueleto()
            }

            if (!loading && visibleDays.isEmpty()) {
                item {
                    if (!diasLeidos) {
                        NoSePudoLeer(
                            "No pudimos cargar tus movimientos",
                            onReintentar = { refreshKey++ },
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 40.dp),
                        )
                    } else if (searchQuery.isNotBlank()) {
                        // F13: nada que ver acá con "no hay dónde anotar" — la búsqueda no dio
                        // resultados, así que el texto buscado es la pista que hace falta.
                        Column(
                            modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Nada coincide con \"${searchQuery.trim()}\"",
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.textoMedio,
                            )
                        }
                    } else {
                        // F10: el estado vacío ofrece la acción — registrar si ya hay dónde
                        // anotar, crear una cuenta primero si no. Pero solo cuando el vacío es
                        // «no hay nada anotado»: si es un chip el que dejó la lista vacía, se
                        // dice eso y no se ofrece nada (ver [vacioDeMovimientos]).
                        //
                        // Ola D, Task 1: el mismo vacío de siempre, dibujado con [VacioQueEnsena]
                        // en vez de con su propia Column — la prueba de que el componente sirve
                        // para lo que ya estaba bien. El texto del botón perdió el «+ » a mano
                        // (el componente ya pone su propio ícono de más; con los dos se leía
                        // «+ + Registrar el primero») pero la condición y el destino son los de
                        // siempre.
                        val hayMovimientos = allDays.any { d -> d.items.any { !isOpeningBalance(it) } }
                        val vacio = vacioDeMovimientos(activeFilter, hayMovimientos)
                        val hayCuentaParaRegistrar = accounts.isNotEmpty() || !accountsLoaded
                        VacioQueEnsena(
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
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 40.dp),
                        )
                    }
                }
            }

            visibleDays.forEach { day ->
                // Sin `key`: con la fecha como clave, `LazyColumn` ancla el primer día visible al
                // cambiar de chip, y pasar de «Gastos» a «Todo» dejaba el día nuevo de arriba
                // escondido por encima del tope (visto a ojo en la web). Posicional, como antes.
                item {
                    val rows = agruparAjustesDeSaldo(collapseTransfers(day.items), searchQuery)
                    val plegado = day.date in diasPlegados
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                        // El encabezado entero es el botón que pliega y despliega el día. Plegado
                        // sigue diciendo el «Flujo del día» y cuántos renglones esconde: plegar
                        // es para acortar la lista, no para perder la información.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Ola B, tarea 9 (fix round 1): el tag va ANTES de `clip`/
                                // `clickable`/`padding` — mismo lugar en la cadena que
                                // `TAG_ENCABEZADO_DE_DIA_ESQUELETO` en el esqueleto — para que
                                // los dos midan el mismo punto (el borde exterior del renglón,
                                // no adentro del relleno). Sin este orden, una prueba medía el
                                // esqueleto 8 dp más abajo que el real sin que el padding de
                                // ninguno de los dos hubiera cambiado.
                                .testTag(TAG_ENCABEZADO_DE_DIA)
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
                                    tint = Movi.colores.textoApagado,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    // V13: «23 DE AGOSTO» / «HOY», no la clave ISO del server.
                                    text = formatDayHeading(day.date, hoyIso).uppercase(),
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                )
                                if (plegado) {
                                    Text(
                                        text = cuantosMovimientosDice(rows).let {
                                            if (it == 1) "· 1 movimiento" else "· $it movimientos"
                                        },
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoApagado,
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
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoApagado,
                                )
                                Text(
                                    text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total)}",
                                    color = Movi.colores.textoMedio,
                                    // Plata en la línea de apoyo: la talla del apoyo, tabular como un monto.
                                    style = Movi.textos.apoyo.copy(fontFeatureSettings = "tnum"),
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
                                            // La pata del dinero, que es la que se reconoce como
                                            // cuota — ver [TransferRow].
                                            esRecurrente = nombreRecurrenteDe(
                                                row.out,
                                                reglasRecurrentes,
                                                nombresDeSuscripcionesActivas,
                                            ) != null,
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
                                                Hairline()
                                                MovementSingleRow(
                                                    tx = ajuste,
                                                    accountNames = accountNames,
                                                    onClick = { selectedEvent = ajuste },
                                                )
                                            }
                                        }
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

    if (editandoElInicio) {
        InicioDelPeriodoSheet(
            periodo = periodoVisible,
            ajustes = ajustesDelPeriodo,
            saving = guardandoInicio,
            error = errorDelInicio,
            onDismiss = { editandoElInicio = false; errorDelInicio = null },
            onSave = { inicio ->
                if (!guardandoInicio) {
                    guardandoInicio = true
                    errorDelInicio = null
                    // `null` = quitar la excepción. El mapa viaja entero, que es cómo la ruta
                    // distingue «no tocar» (no mandarlo) de «ninguno» (mandarlo vacío).
                    val nuevo =
                        if (inicio == null) iniciosPropios - periodoVisible.prefijo
                        else iniciosPropios + (periodoVisible.prefijo to inicio)
                    alcanceDeLaPantalla.launch {
                        runCatching { Repositories.wallets.updateUserProfile(UpdateProfileRequest(periodStarts = nuevo)) }
                            .onSuccess {
                                iniciosPropios = it.periodStarts
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
fun colorDelTono(tono: TonoDelMonto, colores: ColoresDeMovi): Color = when (tono) {
    TonoDelMonto.GASTO -> colores.sale
    TonoDelMonto.INGRESO -> colores.entra
    TonoDelMonto.ENTRE_CUENTAS -> colores.entreCuentas
    TonoDelMonto.NEUTRO -> colores.textoApagado
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
 *
 * Lleva **el mismo ícono de repetición** que [MovementSingleRow] cuando el par es una cuota de
 * crédito o el pago de una tarjeta. Un par plegado es UN renglón, así que marcarlo no duplica nada;
 * sin esto, el mismo pago se leía como recurrente en el chip «Recurrentes» (donde entra sola la
 * pata del dinero) y como un par mudo en «Todo», que es la misma fila diciendo dos cosas distintas
 * según el filtro. La marca sale de la pata del dinero — [MovementRow.Transfer.out] —, que es la
 * que reconocen [com.jvillada.movi.ui.recurrentes.nombreDeCuotaPagada] y
 * [com.jvillada.movi.ui.recurrentes.nombreDePagoDeTarjeta].
 *
 * Los dos pares se siguen viendo igual que antes —el azul de «entre cuentas», sin signo— y el
 * título los distingue («Cuota de crédito» / «Pago de tarjeta», ver [transferRowTitle]), que es lo
 * que hace falta: uno cuenta en el mes y el otro no.
 *
 * `internal` desde la ola C: la bandeja «Por revisar» pinta con este mismo renglón lo que entró
 * solo, para que un movimiento se vea igual allá y acá.
 */
@Composable
internal fun TransferRow(
    row: MovementRow.Transfer,
    accountNames: Map<String, String>,
    accountTypes: Map<String, AccountType>,
    esRecurrente: Boolean = false,
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
        // Neutro a propósito: un traspaso (o la cuota / el pago de tarjeta que se ven con esta
        // misma forma) no es una categoría del dueño, es plata que cambió de cuenta. Por eso el
        // ícono es siempre el de TRANSFER_CATEGORY y no el de la categoría real de la pata —
        // «Cuota de crédito» se vería violeta, como si fuera un gasto con carácter propio.
        IconoDeCategoria(TRANSFER_CATEGORY)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transferRowTitle(row, accountTypes),
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (esRecurrente) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Recurrente",
                        tint = Movi.colores.textoMedio,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = transferRowSubtitle(row, accountNames),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Igual que en [MovementSingleRow]: sin el weight, el Row que envuelve al
                    // ícono no le acota el ancho al texto y el ellipsis no tiene sobre qué cortar.
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Text(
            // En la moneda del movimiento: un traspaso de la Master Black USD no son pesos.
            text = formatMoney(row.amount, row.out.currency),
            style = Movi.textos.monto,
            fontWeight = FontWeight.Medium,
            color = colorDelTono(tonoDelRenglon(row), Movi.colores),
        )
    }
}

/**
 * El renglón que resume los ajustes de saldo de un día y los abre al tocarlo.
 *
 * **Sin monto a la derecha, y es lo honesto.** Cada ajuste lleva su delta, pero sumarlos no
 * significa nada: son deltas de cuentas distintas, algunas de activo y otras de deuda, y ninguno
 * es plata que entró o salió (`isCashFlow` los excluye). Una cifra ahí se leería como un total del
 * día y sería falsa. Lo que va en su lugar es cuántos hay, que es lo que el renglón promete abrir.
 */
@Composable
private fun RenglonDeAjustes(
    events: List<FinancialEvent>,
    abierto: Boolean,
    onAlternar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAlternar)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Mismo criterio que TransferRow: un ajuste de saldo no es una categoría, es una
        // corrección — el ícono es el neutro de ADJUSTMENT_CATEGORY, no el de ningún gasto.
        IconoDeCategoria(ADJUSTMENT_CATEGORY)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tituloDeLosAjustes(events),
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (events.size == 1) "$ADJUSTMENT_CATEGORY · 1 corrección"
                else "$ADJUSTMENT_CATEGORY · ${events.size} correcciones",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
        Icon(
            imageVector = if (abierto) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (abierto) "Ocultar los ajustes" else "Ver los ajustes",
            tint = Movi.colores.textoApagado,
            modifier = Modifier.size(20.dp),
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
 * en el rail y en Más ([Icons.Rounded.Repeat]), chico y sin color propio —el mismo `Movi.colores.textoMedio`
 * del subtítulo— junto a la categoría. No es un botón: solo informa: para editar el recurrente
 * hay que abrir el movimiento y usar «¿Se repite todos los meses?» (ver `SeccionEstoSeRepite`).
 *
 * `internal` desde la ola C: la bandeja «Por revisar» pinta con este mismo renglón lo que entró
 * solo, para que un movimiento se vea igual allá y acá.
 */
@Composable
internal fun MovementSingleRow(
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
            .testTag(TAG_FILA_DE_MOVIMIENTO_SUELTO)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // El ícono de la categoría — la apertura, el ajuste y la pata huérfana ya resuelven a un
        // ícono neutro por su nombre (ver `aparienciaDe`), así que acá no hace falta distinguir.
        IconoDeCategoria(tx.category)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = tx.description,
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    letterSpacing = (-0.1).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Sin este weight el Row no le acota el ancho al título y una descripción
                    // larga («Ajuste al saldo de Skandia — quedó en $95.812.553 al 21-sep…»)
                    // ocupaba cuatro renglones antes de recortarse; con él el punto de «sin
                    // confirmar» sigue visible al lado incluso cuando el título se corta.
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tx.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                    StatusDot(Movi.colores.aviso)
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
                        tint = Movi.colores.textoMedio,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = subtitulo,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
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
                // En la moneda del movimiento, como la hoja de anular y el detalle de la cuenta:
                // un cargo de US$20 en la tarjeta en dólares se leía «−$20».
                TonoDelMonto.INGRESO -> "+${formatMoney(tx.amount, tx.currency)}"
                TonoDelMonto.GASTO -> "−${formatMoney(tx.amount, tx.currency)}"
                // `tonoDelEvento` (lo único que alimenta `tono` acá) nunca devuelve
                // ENTRE_CUENTAS — ese tono es solo de [tonoDelRenglon], para el par de
                // [MovementRow.Transfer] que se pinta en TransferRow, no acá. Rama exhaustiva
                // igual, con el mismo criterio de NEUTRO: sin signo.
                TonoDelMonto.NEUTRO, TonoDelMonto.ENTRE_CUENTAS -> formatMoney(tx.amount, tx.currency)
            },
            style = Movi.textos.monto,
            fontWeight = FontWeight.Medium,
            color = colorDelTono(tono, Movi.colores),
        )
    }
}
