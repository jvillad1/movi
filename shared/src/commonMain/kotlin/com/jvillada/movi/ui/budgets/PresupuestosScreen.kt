package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import kotlin.math.roundToLong
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.dashboard.spentByCategoryForPeriod
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ventanaDe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.shared.model.EstadoDePresupuesto
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.FinancialEvent
import androidx.compose.runtime.rememberCoroutineScope
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria

/** `internal` y no `private` para poder probar [estadoDelPresupuesto] — ver EstadoDelPresupuestoTest. */
internal data class BudgetProgress(
    val budget: Budget,
    val spent: Long,
) {
    val remaining: Long get() = budget.monthlyLimit - spent
    /**
     * Solo para el ANCHO de la barra, que es lo único que necesita un `Float` (`fillMaxWidth`
     * recibe una fracción). Ni el estado ni el porcentaje ni el orden de la lista salen de acá:
     * con montos de cientos de millones esta división pierde precisión — ver [pct].
     */
    val pctRaw: Float get() = if (budget.monthlyLimit == 0L) 0f else spent.toFloat() / budget.monthlyLimit.toFloat()
    /**
     * El porcentaje se calcula con enteros, no con el [pctRaw] de arriba.
     *
     * `Float` tiene 24 bits de mantisa: a partir de 16.777.216 ya no puede representar todos los
     * enteros. Con los montos del dueño —una hipoteca de 767.800.000— la división pierde
     * precisión, así que un porcentaje sacado de ahí puede estar mal por más de un punto. En pesos
     * colombianos eso no es una hipótesis de laboratorio: es el orden de magnitud normal.
     *
     * ### Pasado el límite, redondea hacia ARRIBA
     *
     * Hasta el límite se trunca, como siempre: 79,9 % dice «79%» y no promete un «80%» que todavía
     * no llegó. Pero pasado el límite truncar se contradice con el rótulo de al lado: Comida con
     * $1.008.737 de $1.000.000 decía «100% · Sobrepasado · $8.737». Así que cuando se pasó —aunque
     * sea por un peso— se redondea hacia arriba: 100,87 % → «101%», 116,3 % → «117%». Justo en el
     * límite la división es exacta y sigue diciendo «100%», que es lo que dice «Sin margen».
     */
    val pct: Int get() {
        val limite = budget.monthlyLimit
        if (limite == 0L) return 0
        val centuplo = spent * 100
        val truncado = centuplo / limite
        return (if (spent > limite && centuplo % limite != 0L) truncado + 1 else truncado).toInt()
    }
    val state: EstadoDePresupuesto get() = estadoDePresupuesto(spent, budget.monthlyLimit)
}


/**
 * F16: el encabezado y cada tarjeta necesitan decir "de qué mes" es el gasto — kotlinx-datetime
 * da el [Month] del sistema, pero no en español, así que se mapea a mano. Minúscula porque así
 * va en el encabezado ("Gastado en agosto"), sin punto porque no es abreviatura.
 */
private fun Month.spanishName(): String = when (this) {
    Month.JANUARY -> "enero"
    Month.FEBRUARY -> "febrero"
    Month.MARCH -> "marzo"
    Month.APRIL -> "abril"
    Month.MAY -> "mayo"
    Month.JUNE -> "junio"
    Month.JULY -> "julio"
    Month.AUGUST -> "agosto"
    Month.SEPTEMBER -> "septiembre"
    Month.OCTOBER -> "octubre"
    Month.NOVEMBER -> "noviembre"
    Month.DECEMBER -> "diciembre"
}

internal sealed class Sheet {
    data class Edit(val current: Budget) : Sheet()
    data object Add : Sheet()
}

/*
 * # Presupuestos, en tres piezas
 *
 * Ola C: el cuerpo de esta pantalla es también el segmento «Presupuestos» de la pestaña Plan. Para
 * que las dos no sean dos copias se parte igual que el tablero de Recurrentes
 * (`ui/plan/TableroDeRecurrentes.kt`):
 *
 * - [EstadoDePresupuestos] (con [rememberEstadoDePresupuestos]): lo que se lee y lo que el dueño
 *   le hace. Carga lo suyo.
 * - [presupuestos]: sus renglones, como extensión de `LazyListScope`, para ir dentro de la lista de
 *   quien lo monte —en Plan, debajo del disponible y del selector, con un solo scroll—.
 * - [HojasDePresupuestos]: crear y editar, afuera de la lista.
 *
 * [PresupuestosScreen] las junta con su encabezado. El «Nuevo» compacto va en el encabezado de quien
 * lo monta; cuándo se ofrece lo dice [EstadoDePresupuestos.nuevoEnElEncabezado].
 */

/**
 * **Lo que Presupuestos lee y lo que el dueño le hace.** Estaba suelto adentro de
 * [PresupuestosScreen] como una docena de `remember`; se juntó acá sin cambiar ni una clave ni un
 * orden de lectura.
 */
@Stable
class EstadoDePresupuestos internal constructor(private val alcance: CoroutineScope) {
    // `null` = la lectura todavía no contestó bien; `emptyList()` = contestó y no hay ninguno. Ver
    // [listo] para por qué con los presupuestos solos no alcanza para pintar la lista.
    internal var budgets by mutableStateOf<List<Budget>?>(null)
    internal var cutoffDay by mutableStateOf(1)
    /** Los meses que arrancaron otro día. Ver `PeriodSettings.iniciosPropios`. */
    internal var iniciosPropios by mutableStateOf(emptyMap<String, String>())
    internal var days by mutableStateOf<List<EventDay>>(emptyList())
    // Se incrementa al asociar un gasto, para volver a leer con el movimiento ya movido.
    internal var refreshKeyLocal by mutableStateOf(0)
    // Gasto del mes por categoría según el server (la misma fuente que el Inicio); null hasta
    // que llegue o si no hay red — ver `progresses`.
    internal var serverSpent by mutableStateOf<Map<String, Long>?>(null)
    internal var sheet by mutableStateOf<Sheet?>(null)
    // Error de guardar/renombrar que la hoja tiene que mostrar (409 del server, red).
    internal var sheetError by mutableStateOf<String?>(null)
    // Mientras una llamada de la hoja está en vuelo no sale otra: un doble toque en «Guardar»
    // mandaba el rename dos veces, y el segundo volvía 404 con un error sobre algo que sí se hizo.
    // Mismo `!saving` que las hojas de metas, recurrentes, tarjetas y créditos.
    internal var guardando by mutableStateOf(false)
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los presupuestos reales.
    internal var loading by mutableStateOf(true)

    // Los movimientos del período contestaron bien al menos una vez: son el respaldo del gasto
    // cuando el server no contesta (ver [gastoPorCategoria]).
    internal var eventosLeidos by mutableStateOf(false)
    // La lectura del gasto del server y la del perfil ya contestaron (bien o mal) al menos una vez.
    // Hasta entonces, cualquier gasto que se pintara podía cambiar —el del aparato por el del
    // server, o el mes de calendario por el período del dueño— y con él el ORDEN de la lista.
    internal var gastoYPeriodoContestaron by mutableStateOf(false)

    // ── Lo que Movi propone presupuestar (solo sin presupuestos) ──────────────────────────────
    //
    // `null` = no llegaron (todavía, o la lectura falló): el vacío de siempre, con su «Nuevo
    // presupuesto». Una sugerencia caída no es un error de pantalla — no hay nada que el dueño haya
    // perdido — y no le puede tapar crear a mano. Ver [VacioDePresupuestos].
    internal var propuestas by mutableStateOf<List<PropuestaDePresupuesto>?>(null)
        private set
    // Las que el dueño desmarcó, por nombre. Se guarda lo desmarcado y no lo marcado porque todas
    // nacen marcadas: una propuesta que llega en una recarga entra marcada sin que nadie la toque.
    internal var desmarcadas by mutableStateOf(emptySet<String>())
        private set
    // Un «Crear estos N» en vuelo: el segundo toque no manda los POST otra vez.
    internal var creandoPropuestas by mutableStateOf(false)
        private set
    // Las que el server no aceptó en el último intento, para decir cuáles y reintentar solo esas.
    internal var propuestasQueFallaron by mutableStateOf(emptyList<PropuestaDePresupuesto>())
        private set

    internal val propuestasMarcadas: List<PropuestaDePresupuesto>
        get() = propuestas.orEmpty().filter { it.category !in desmarcadas }

    internal fun recibirPropuestas(nuevas: List<PropuestaDePresupuesto>) {
        propuestas = nuevas
        // Lo desmarcado de una categoría que ya no se propone no tiene a quién aplicarse.
        desmarcadas = desmarcadas.filterTo(mutableSetOf()) { c -> nuevas.any { it.category == c } }
    }

    internal fun alternarPropuesta(categoria: String) {
        desmarcadas = if (categoria in desmarcadas) desmarcadas - categoria else desmarcadas + categoria
    }

    /**
     * Crea [lista] con el MISMO `createBudget` de la hoja «Nuevo presupuesto», una por una.
     *
     * Una que falla no deshace las demás: lo creado es del dueño y ya está en el server. Las que no
     * entraron quedan en [propuestasQueFallaron], y la pantalla dice cuáles con «Reintentar». Si
     * alguna entró se recarga, y la pantalla pasa sola del vacío a la lista.
     */
    internal fun crearPropuestas(lista: List<PropuestaDePresupuesto> = propuestasMarcadas) {
        if (creandoPropuestas || lista.isEmpty()) return
        creandoPropuestas = true
        alcance.launch {
            try {
                val fallaron = lista.filter { p ->
                    intentar { Repositories.wallets.createBudget(Budget(p.category, p.amount.roundToLong())) }.isFailure
                }
                propuestasQueFallaron = fallaron
                if (fallaron.size < lista.size) reload()
            } finally {
                creandoPropuestas = false
            }
        }
    }

    /** El «Reintentar» de las propuestas que no se pudieron crear: solo esas. */
    internal fun reintentarPropuestas() = crearPropuestas(propuestasQueFallaron)

    internal suspend fun reload() {
        // F35: de paso, alimenta el caché de "categorías ya usadas" que lee CategoryField —
        // esta pantalla ya carga presupuestos y movimientos, no hace falta un fetch nuevo.
        intentar { Repositories.wallets.getBudgets() }.onSuccess {
            budgets = it
            // Ola 9 · A3: un presupuesto es, por definición, un límite de GASTO — así que sus
            // categorías se anotan con ese tipo y no como "no se sabe".
            UsedCategoriesCache.recordAll(it.map { b -> b.category to TransactionType.EXPENSE })
        }
    }

    // Mover un movimiento a la categoría del presupuesto. Va acá y no en la hoja porque después
    // hay que recargar la lista: el gasto recién asociado tiene que aparecer contado.
    internal fun asociarGasto(evento: FinancialEvent, categoria: String) {
        alcance.launch {
            runCatching { Repositories.wallets.updateEventCategory(evento.id, categoria) }
                .onSuccess { refreshKeyLocal++ }
                .onFailure { sheetError = it.toUserMessage() }
        }
    }

    /** «Nuevo» y «Nuevo presupuesto»: abren la hoja de crear. */
    fun abrirNuevo() {
        sheet = Sheet.Add
    }

    /** El «Reintentar» de la lectura que no se pudo hacer. */
    internal fun reintentar() {
        refreshKeyLocal++
    }

    // El gasto del período por categoría, una sola vez: lo usan las barras de progreso y también
    // el aviso de la hoja de crear (ver [avisoDeCategoria]). Calcularlo en dos lados abriría la
    // puerta a que la pantalla y su hoja dijeran cifras distintas.
    // El período del usuario, no el mes de calendario. `serverSpent` ya viene calculado con la
    // ventana correcta (el server usa `currentPeriodWindow`); el cálculo local es el respaldo y
    // tiene que usar la MISMA ventana o las dos mitades de la app dirían cifras distintas.
    internal val ventanaDelPeriodo: LongRange by derivedStateOf {
        // El período entero, no solo el corte: si el dueño declaró que este mes arrancó otro día,
        // Presupuestos tiene que contar la misma ventana que Movimientos le está mostrando.
        val settings = PeriodSettings(cutoffDay = cutoffDay, iniciosPropios = iniciosPropios)
        ventanaDe(periodoDe(kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), settings), settings)
    }
    internal val gastoPorCategoria: Map<String, Long> by derivedStateOf {
        serverSpent ?: spentByCategoryForPeriod(days, ventanaDelPeriodo)
    }

    internal val progresses: List<BudgetProgress> by derivedStateOf {
        // countsAsCashFlow deja fuera los movimientos de cuentas de deuda. Sin él, un ajuste de
        // saldo de un crédito caía en la categoría "Otros" y ponía en OVER al instante a un
        // presupuesto con ese nombre.
        // Misma regla que el acceso «Presupuestos» del Inicio y que la alerta de sobrepasado
        // (spentByCategoryForPeriod): solo el período en curso y solo COP. Antes esta pantalla sumaba
        // TODO el historial mientras el encabezado decía «Gastado en agosto» — el Inicio y
        // Presupuestos daban cifras distintas para el mismo presupuesto.
        budgets.orEmpty().map { b -> BudgetProgress(b, gastoPorCategoria[b.category] ?: 0L) }
            // Se ordena por el porcentaje ENTERO, no por el `Float`.
            //
            // Era `pctRaw`, o sea exactamente el cálculo que este archivo argumenta que no es de
            // fiar con montos grandes: dos presupuestos de cientos de millones podían quedar
            // ordenados al revés porque su división en `Float` da el mismo valor. Se ordena por lo
            // mismo que se muestra.
            .sortedByDescending { it.pct }
    }

    // F16: "Gastado del mes" no decía CUÁL mes — el nombre lo hace explícito. Es el nombre del
    // **período** (el mismo que suma `ventanaDelPeriodo`), no del mes de calendario: con corte 25,
    // el 26 de septiembre ya es octubre en Movimientos, y el título tiene que decir lo mismo.
    internal val monthName: String by derivedStateOf {
        val settings = PeriodSettings(cutoffDay = cutoffDay, iniciosPropios = iniciosPropios)
        Month(periodoDe(Clock.System.now().toEpochMilliseconds(), settings).month).spanishName()
    }

    /**
     * **Ola B: nada se pinta hasta que se sabe lo gastado.** Los presupuestos llegan primero y el
     * gasto después, y en ese medio la pantalla decía «$0» gastado y cada categoría «$0 … 0 % …
     * $1.000.000 disponibles»; al llegar el gasto aparecía «2 Sobrepasados», las categorías se
     * REORDENABAN (van por porcentaje) y las filas crecían. Así que la lista espera a las tres
     * cosas —presupuestos, un gasto que contestó bien, y el período— con el esqueleto en su lugar.
     *
     * El gasto conocido es el del server o, si el server no contestó, el que se calcula con los
     * movimientos (el respaldo de siempre). Sin ninguno de los dos no hay gasto que decir: un «$0»
     * ahí sería inventado, así que la pantalla dice que no pudo leer, igual que sin presupuestos.
     * **Salvo sin presupuestos**: el vacío de siempre («Nuevo presupuesto») no depende del gasto
     * —no hay categoría a la que ponerle una cifra—, así que ahí un gasto caído no es un error.
     */
    internal val gastoConocido: Boolean get() = serverSpent != null || eventosLeidos
    internal val listo: Boolean get() =
        budgets != null && gastoYPeriodoContestaron && (gastoConocido || budgets.isNullOrEmpty())
    // Lo que la hoja de crear/editar sabe del gasto: nada (`null`) hasta que el gasto definitivo
    // contestó. Antes recibía el cálculo sobre `days = emptyList()` y, con «Nuevo» tocado en el
    // primer cuadro, decía «Todavía no tienes gastos registrados este mes» a quien sí tiene.
    internal val gastoParaLaHoja: Map<String, Long>? get() =
        gastoPorCategoria.takeIf { gastoConocido && gastoYPeriodoContestaron }
    // Ver [NoSePudoLeer]: sin esto una lectura caída pintaba «Gastado $0 de $0» y el botón de
    // crear, a quien ya tiene presupuestos.
    internal val noSeLeyo: Boolean get() = !loading && !listo
    internal val cargando: Boolean get() = loading && !listo
    internal val sinPresupuestos: Boolean get() = listo && budgets.isNullOrEmpty()

    /**
     * ¿Va el «Nuevo» compacto en el encabezado de quien monta esto?
     *
     * Ola B: está desde el primer cuadro, también mientras carga — si aparecía al llegar los datos,
     * el título se corría. Se va solo con la lectura que no se pudo hacer y con el vacío de verdad,
     * que tiene su botón ancho.
     */
    val nuevoEnElEncabezado: Boolean get() = !sinPresupuestos && !noSeLeyo

    internal val totalLimit: Long get() = budgets.orEmpty().sumOf { it.monthlyLimit }
    internal val totalSpent: Long get() = progresses.sumOf { it.spent }
    // «Al límite» cuenta como aviso, no como sobrepasado: el encabezado decía «2 Sobrepasados»
    // con uno de los dos exactamente en el límite.
    internal val warnCount: Int get() =
        progresses.count { it.state == EstadoDePresupuesto.CERCA || it.state == EstadoDePresupuesto.AL_LIMITE }
    internal val overCount: Int get() = progresses.count { it.state.estaSuperado }
}

/**
 * El estado de Presupuestos, atado a la composición, **con sus lecturas corriendo**.
 *
 * @param activo si el cuerpo se está mostrando. En Plan, con el segmento «Pagos del mes» elegido,
 *   no hay a quién servirle los movimientos del período (la lectura más pesada de las cuatro): no
 *   se pide nada hasta que el dueño elige «Presupuestos». [PresupuestosScreen] lo deja siempre en
 *   `true`.
 */
@Composable
fun rememberEstadoDePresupuestos(activo: Boolean = true): EstadoDePresupuestos {
    val alcance = rememberCoroutineScope()
    val estado = remember(alcance) { EstadoDePresupuestos(alcance) }
    val refreshTick = LocalRefreshTick.current
    // `refreshTick` y no `Unit`: con Unit esta pantalla no recargaba NUNCA mientras estuviera
    // compuesta, y desde que Agregar es una modal se puede registrar un gasto parado acá y ver
    // la barra del presupuesto sin moverse. Ver [LocalRefreshTick].
    LaunchedEffect(refreshTick, estado.refreshKeyLocal, activo) {
        if (!activo) return@LaunchedEffect
        estado.loading = true
        estado.reload()
        intentar { Repositories.wallets.getEventsByDay() }.onSuccess {
            estado.days = it
            estado.eventosLeidos = true
            // Ola 9 · A3: con el tipo de cada movimiento, así una categoría propia se ofrece
            // del lado en que de verdad se usó.
            UsedCategoriesCache.recordAll(it.flatMap { d -> d.items }.map { ev -> ev.category to ev.type })
        }
        // Misma cifra que el Inicio: el server suma con TODO lo que sabe (todos los dispositivos,
        // SMS, importaciones; anulados fuera). En el teléfono `getEventsByDay` es local y solo
        // conoce lo de este aparato, así que el Inicio podía decir «Comida superado» y esta
        // pantalla no. Si falla (sin red) queda el cálculo local de abajo como fallback.
        intentar { Repositories.wallets.getDashboardSummary(Scope.SELF) }.onSuccess { estado.serverSpent = it.spentByCategory }
        // El corte del período: define qué ventana usa el cálculo local de respaldo. Si falla,
        // queda en 1 —mes de calendario— que es el comportamiento de siempre.
        intentar { Repositories.wallets.getUserProfile() }
            .onSuccess { estado.cutoffDay = it.periodCutoffDay; estado.iniciosPropios = it.periodStarts }
        estado.gastoYPeriodoContestaron = true
        estado.loading = false
    }
    // Las propuestas se piden SOLO sin presupuestos: a quien ya tiene no le cuestan ni una llamada.
    // Una lectura caída deja lo que había (nada, la primera vez): el vacío de siempre.
    LaunchedEffect(estado.sinPresupuestos, refreshTick, estado.refreshKeyLocal, activo) {
        if (!activo || !estado.sinPresupuestos) return@LaunchedEffect
        intentar { Repositories.wallets.getPropuestasDePresupuesto() }.onSuccess { estado.recibirPropuestas(it) }
    }
    return estado
}

/**
 * **Los renglones de Presupuestos**, para pintarlos dentro de una `LazyColumn` ajena: el aviso de
 * que no se pudo leer (o el vacío que enseña), y después el esqueleto o la tarjeta de «Gastado
 * en …» con sus categorías.
 */
fun LazyListScope.presupuestos(estado: EstadoDePresupuestos) {
    item {
        if (estado.noSeLeyo) {
            Spacer(Modifier.height(14.dp))
            NoSePudoLeer(
                "No pudimos cargar tus presupuestos",
                onReintentar = { estado.reintentar() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else if (estado.sinPresupuestos) {
            VacioDePresupuestos(estado)
        } else {
            Spacer(Modifier.height(14.dp))
        }
        // Va afuera del vacío: si alguna propuesta sí se creó, la pantalla ya pasó a la lista y el
        // aviso de las que faltan tiene que seguir ahí, arriba de ella.
        if (estado.propuestasQueFallaron.isNotEmpty() && !estado.noSeLeyo) {
            AvisoDePropuestasQueFallaron(estado)
        }
    }

    // Ola D, Task 3: la tarjeta «Gastado en …» es un hecho sobre categorías que no existen sin
    // presupuestos — hasta acá se pintaba igual, y con `totalSpent`/`totalLimit` en cero decía
    // «$0 de $0», justo el «$0 presentado como un hecho» que el vacío de arriba vino a evitar.
    if (estado.cargando) {
        presupuestosEsqueleto()
    } else if (estado.listo && !estado.sinPresupuestos) {
        item {
            MinCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(TAG_TARJETA_DEL_GASTO_DEL_PERIODO),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(22.dp),
            ) {
                Text("Gastado en ${estado.monthName}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                // La protagonista de esta pantalla, como «Tu plata» en el Inicio y la deuda
                // total en Créditos: misma letra y un renglón siempre.
                CifraProtagonista(formatCOP(estado.totalSpent), color = Movi.colores.texto)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "de ${formatCOP(estado.totalLimit)}",
                    color = Movi.colores.textoMedio,
                    style = Movi.textos.monto,
                )
                val warnCount = estado.warnCount
                val overCount = estado.overCount
                if (warnCount + overCount > 0) {
                    Spacer(Modifier.height(14.dp))
                    Hairline()
                    Spacer(Modifier.height(14.dp))
                    // Las insignias dicen lo MISMO que las tarjetas de abajo.
                    //
                    // Antes el encabezado rotulaba «Cerca del límite» en amarillo a los
                    // presupuestos que las tarjetas pintan verdes — y uno de ellos es el
                    // que está justo en el límite, del que el comentario de la tarjeta
                    // dice literalmente «no está cerca, está justo ahí». La misma pantalla
                    // se contradecía a sí misma en color y en palabra.
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (overCount > 0) {
                            AlertBadge("Sobrepasados", overCount, Movi.colores.sale)
                        }
                        if (warnCount > 0) {
                            AlertBadge("Sin margen o cerca", warnCount, Movi.colores.entra)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val progresses = estado.progresses
                MinSectionHeader(title = "Categorías", count = progresses.size)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    progresses.forEach { p ->
                        BudgetCard(p, onClick = { estado.sheet = Sheet.Edit(p.budget) })
                    }
                }
            }
        }
    }
}

/** Crear y editar un presupuesto: van afuera de la lista, como toda hoja. */
@Composable
fun HojasDePresupuestos(estado: EstadoDePresupuestos) {
    val scope = rememberCoroutineScope()
    when (val s = estado.sheet) {
        is Sheet.Edit -> BudgetSheet(
            error = estado.sheetError,
            title = "Editar presupuesto",
            gastoPorCategoria = estado.gastoParaLaHoja,
            dias = estado.days,
            ventana = estado.ventanaDelPeriodo,
            onAsociar = estado::asociarGasto,
            initialCategory = s.current.category,
            // F17: la categoría dejó de ser de solo lectura — antes era una limitación
            // técnica filtrada a la pantalla (la categoría es la PK en el server), ahora
            // PUT /api/budgets/{category}/rename la resuelve del lado del servidor.
            categoryEditable = true,
            initialAmount = s.current.monthlyLimit,
            onDismiss = { estado.sheet = null; estado.sheetError = null },
            guardando = estado.guardando,
            onDelete = {
                if (estado.guardando) return@BudgetSheet
                estado.guardando = true
                scope.launch {
                    // Igual que guardar: si el borrado falla, la hoja queda abierta con el
                    // motivo. Antes se tragaba el error, recargaba y cerraba — el presupuesto
                    // seguía ahí y nada decía por qué.
                    runCatching { Repositories.wallets.deleteBudget(s.current.category) }
                        .onSuccess { estado.reload(); estado.sheet = null; estado.sheetError = null }
                        .onFailure { estado.sheetError = it.toUserMessage() }
                    estado.guardando = false
                }
            },
            onSave = { cat, amt ->
                if (estado.guardando) return@BudgetSheet
                estado.guardando = true
                scope.launch {
                    val result = runCatching {
                        // F17: renombrar y cambiar el monto son dos llamadas separadas
                        // porque son dos endpoints separados — rename conserva el límite
                        // viejo, así que si además cambió el monto hay que pisarlo después.
                        val renamed = cat != s.current.category
                        val finalCategory = if (renamed) {
                            Repositories.wallets.renameBudget(s.current.category, cat).category
                        } else {
                            s.current.category
                        }
                        if (!renamed || amt != s.current.monthlyLimit) {
                            Repositories.wallets.updateBudget(finalCategory, Budget(finalCategory, amt))
                        }
                    }
                    // El 409 del server («Ya existe un presupuesto llamado…») tiene que
                    // llegarle a la persona: cerrar la hoja en silencio era decirle que se
                    // guardó cuando no. La hoja queda abierta con el mensaje; reintenta o cierra.
                    result.onSuccess { estado.reload(); estado.sheet = null; estado.sheetError = null }
                        .onFailure { estado.sheetError = it.toUserMessage() }
                    estado.guardando = false
                }
            },
        )
        Sheet.Add -> BudgetSheet(
            error = estado.sheetError,
            title = "Nuevo presupuesto",
            initialCategory = "",
            categoryEditable = true,
            initialAmount = 0,
            gastoPorCategoria = estado.gastoParaLaHoja,
            dias = estado.days,
            ventana = estado.ventanaDelPeriodo,
            onAsociar = estado::asociarGasto,
            onDismiss = { estado.sheet = null; estado.sheetError = null },
            onDelete = null,
            guardando = estado.guardando,
            onSave = { cat, amt ->
                if (cat.isBlank() || amt <= 0L || estado.guardando) return@BudgetSheet
                estado.guardando = true
                scope.launch {
                    // Lo mismo que editar: sin red o con un 409 («ya hay un presupuesto para
                    // esa categoría») la hoja no se cierra como si se hubiera guardado.
                    runCatching { Repositories.wallets.createBudget(Budget(cat.trim(), amt)) }
                        .onSuccess { estado.reload(); estado.sheet = null; estado.sheetError = null }
                        .onFailure { estado.sheetError = it.toUserMessage() }
                    estado.guardando = false
                }
            },
        )
        null -> {}
    }
}

@Composable
fun PresupuestosScreen(onNavigate: (Screen) -> Unit) {
    val estado = rememberEstadoDePresupuestos()

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // F60: encabezado único. Ola C: Presupuestos es un segmento de Plan, así que esta
            // pantalla suelta lleva flecha y vuelve a Plan · Presupuestos. Con presupuestos ya creados, el alta
            // compacta a la derecha (F18), desde el primer cuadro (ver
            // [EstadoDePresupuestos.nuevoEnElEncabezado]).
            MinScreenHeader(
                title = "Presupuestos",
                leading = leadingFor(Screen.Budgets, onNavigate, fallback = Screen.Plan(SEGMENTO_PRESUPUESTOS)),
                action = if (estado.nuevoEnElEncabezado) {
                    // «Nuevo» y no «Nuevo presupuesto»: con el rótulo largo, el título de la
                    // pantalla quedaba cortado en «Presupues…» a 390 dp. Visto en la web. En esta
                    // pantalla no hay otra cosa que se pueda crear, así que la palabra alcanza.
                    { NewItemButton(label = "Nuevo", onClick = { estado.abrirNuevo() }) }
                } else null,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                presupuestos(estado)
            }
        }

        HojasDePresupuestos(estado)
    }
}

/**
 * El número de una insignia de [AlertBadge]. Tamaño suelto a propósito: el número grande de un
 * contador en la tarjeta; `cifra` (42) no cabe y `titular` (19) es para títulos.
 *
 * **Con su interlineado declarado** (ola B). Antes eran 22 sp sobre el interlineado de `monto`
 * (18 sp): el renglón medía lo que pedía la fuente —28 dp, medido con el motor de texto real— y
 * el esqueleto de la tarjeta no tenía de dónde sacar ese número. Declarado, el renglón mide lo
 * mismo que antes y [presupuestosEsqueleto] lo lee de acá.
 */
@Composable
private fun estiloDelContador(): TextStyle = Movi.textos.monto.copy(fontSize = 22.sp, lineHeight = 28.sp)

@Composable
private fun AlertBadge(label: String, count: Int, color: Color) {
    Column {
        Text(
            text = "$count",
            style = estiloDelContador(),
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = (-0.4).sp,
        )
        Text(label, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

@Composable
private fun BudgetCard(p: BudgetProgress, onClick: () -> Unit) {
    val barColor = when (p.state) {
        // El semáforo, tal como lo pidió el dueño: «verde si estoy igual o por debajo, amarillo
        // si superé un poco y rojo si superé mucho».
        //
        // Los TRES estados de «por debajo o igual» son verdes, DENTRO incluido. La primera
        // versión lo dejaba caer en un `else` gris, y quedaba al revés de lo pedido: 40 % gris,
        // 85 % verde, 100 % verde. El presupuesto menos gastado se veía menos verde que el que
        // estaba justo en el límite.
        //
        // Sin `else`: así el `when` es exhaustivo y un estado nuevo no puede colarse sin color.
        EstadoDePresupuesto.EXCEDIDO_MUCHO -> Movi.colores.sale
        EstadoDePresupuesto.EXCEDIDO_POCO -> Movi.colores.aviso
        EstadoDePresupuesto.AL_LIMITE -> Movi.colores.entra
        EstadoDePresupuesto.CERCA -> Movi.colores.entra
        EstadoDePresupuesto.DENTRO -> Movi.colores.entra
    }
    val pctColor = when (p.state) {
        // El semáforo, tal como lo pidió el dueño: «verde si estoy igual o por debajo, amarillo
        // si superé un poco y rojo si superé mucho».
        //
        // Los TRES estados de «por debajo o igual» son verdes, DENTRO incluido. La primera
        // versión lo dejaba caer en un `else` gris, y quedaba al revés de lo pedido: 40 % gris,
        // 85 % verde, 100 % verde. El presupuesto menos gastado se veía menos verde que el que
        // estaba justo en el límite.
        //
        // Sin `else`: así el `when` es exhaustivo y un estado nuevo no puede colarse sin color.
        EstadoDePresupuesto.EXCEDIDO_MUCHO -> Movi.colores.sale
        EstadoDePresupuesto.EXCEDIDO_POCO -> Movi.colores.aviso
        EstadoDePresupuesto.AL_LIMITE -> Movi.colores.entra
        EstadoDePresupuesto.CERCA -> Movi.colores.entra
        EstadoDePresupuesto.DENTRO -> Movi.colores.entra
    }
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                // Task 3 (Ola B): el ícono de la categoría, chico — el mismo que ya identifica a
                // «Comida» en Movimientos y en el Inicio, acá junto a su nombre.
                IconoDeCategoria(p.budget.category, tamano = TamanoDeIconoDeCategoria.Chico)
                Text(
                    text = p.budget.category,
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    letterSpacing = (-0.1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // F15: el chevron es lo que insinúa que la tarjeta se toca — mismo ícono que usa la
            // guía de primeros pasos del Inicio (ver ChevronRight).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${p.pct}%",
                    // Dato de apoyo junto al chevron: la talla del apoyo, tabular como un monto.
                    style = Movi.textos.apoyo.copy(fontFeatureSettings = "tnum"),
                    color = pctColor,
                    fontWeight = FontWeight.Medium,
                )
                ChevronRight()
            }
        }
        Spacer(Modifier.height(10.dp))
        // **Cada lado con su parte del ancho.** Antes los dos iban sin peso: con «Sobrepasado ·
        // $50.000» a la derecha, ese texto se partía en tres renglones y se dibujaba ENCIMA de
        // «este mes». Visto en la web a 390 dp. Ahora el monto se lleva más ancho, el aviso menos,
        // y los dos parten renglón adentro de su espacio en vez de pisarse.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // F16: "de $2.000.000 este mes" en vez de "/ $2.000.000" — deja explícito que el
            // límite es mensual sin depender solo del texto chico bajo el monto en la hoja.
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Movi.colores.texto)) {
                        append(formatCOP(p.spent))
                    }
                    // Espacios que no cortan: si hace falta partir, se parte antes de «de» y no
                    // entre «este» y «mes», que quedaba «este» arriba y «mes» solo abajo.
                    append(" de\u00A0${formatCOP(p.budget.monthlyLimit)} este\u00A0mes")
                },
                style = Movi.textos.monto,
                color = Movi.colores.textoMedio,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1.7f),
            )
            val tail = when (p.state) {
                EstadoDePresupuesto.EXCEDIDO_MUCHO,
                EstadoDePresupuesto.EXCEDIDO_POCO -> "Sobrepasado · ${formatCOP(-p.remaining)}"
                // Ni «sobrepasado» (no se pasó) ni «cerca» (no está cerca, está justo ahí).
                EstadoDePresupuesto.AL_LIMITE -> "Sin margen · gastaste justo el límite"
                EstadoDePresupuesto.CERCA -> "Cerca del límite"
                else -> "${formatCOP(p.remaining)} disponibles"
            }
            Text(
                tail,
                style = Movi.textos.apoyo,
                color = pctColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Movi.colores.hilo)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(p.pctRaw.coerceAtMost(1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun BudgetSheet(
    title: String,
    initialCategory: String,
    categoryEditable: Boolean,
    initialAmount: Long,
    /**
     * Gasto del período por categoría, para poder decir la verdad antes de guardar. `null` mientras
     * el gasto no contestó: entonces la hoja no dice nada sobre él (ni el aviso de la categoría ni
     * el renglón de lo que falta), porque cualquier cosa que dijera sería sobre un cero inventado.
     */
    gastoPorCategoria: Map<String, Long>?,
    /** Los días del período, para poder ofrecer los movimientos que se llaman como la categoría. */
    dias: List<EventDay>,
    ventana: LongRange,
    onAsociar: (FinancialEvent, String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Long) -> Unit,
    error: String? = null,
    guardando: Boolean = false,
) {
    var category by remember { mutableStateOf(initialCategory) }
    var amount by remember { mutableStateOf(if (initialAmount > 0L) initialAmount.toString() else "") }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    val parsedAmount = amount.toLongOrNull() ?: 0L
    val canSave = category.isNotBlank() && parsedAmount > 0L && !guardando
    // F24: mismo patrón que las demás hojas de crear — la primera cosa que falta.
    val missingFieldMessage = when {
        category.isBlank() -> "Falta la categoría"
        parsedAmount <= 0L -> "Falta el monto"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Esta hoja NO tenía scroll, y con la lista de movimientos deja de alcanzar: bajo
                // ella van el teclado (192 dp) y «Guardar»/«Eliminar», así que con tres
                // movimientos los botones caían fuera de la pantalla, recortados por el `clip` de
                // la propia hoja — el presupuesto quedaba imposible de editar. Y la lista existe
                // justamente para las categorías con muchos movimientos. Lo midió la revisión
                // antes de que llegara a producción.
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
            SheetHandleWithClose(onClose = onDismiss)

            Text(
                text = title,
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            // Category
            if (categoryEditable) {
                // F35/F17: el mismo campo sirve para crear (categoría nueva) y para editar
                // (renombrar): desde la Ola B, escribiendo el nombre nuevo en la búsqueda y
                // tocando «Crear "…"».
                // Solo EXPENSE: no tiene sentido presupuestar una categoría de ingreso.
                CategoryField(
                    value = category,
                    onValueChange = { category = it },
                    type = TransactionType.EXPENSE,
                    usedCategories = UsedCategoriesCache.used,
                    prefs = UsedCategoriesCache.prefs,
                    label = "Categoría",
                    usos = UsedCategoriesCache.usosRecientes,
                    placeholder = "Mercado, Salud, Restaurantes…",
                )
                // F17: onDelete solo viene no-nulo al editar un presupuesto EXISTENTE (Sheet.Add
                // lo manda null) — ahí es donde "cambiar el nombre" significa renombrar una
                // categoría que ya tiene gasto acumulado, así que solo ahí hace falta la
                // advertencia. El cruce presupuesto↔gasto es por NOMBRE de categoría
                // (spentByCategoryForPeriod), no por un id estable — renombrar corta ese cruce
                // para los movimientos viejos.
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "El gasto se cruza por nombre: si renombras \"$initialCategory\" a otra cosa, " +
                            "los movimientos que digan \"$initialCategory\" dejan de contar aquí.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        lineHeight = 15.sp,
                    )
                }
                // La verdad sobre la categoría escrita, ANTES de guardar. El dueño creó un
                // presupuesto en «Mercado» —que es la descripción de su gasto, no su categoría—
                // y la app lo dejó crear algo que no vigilaba nada, en silencio. Ver
                // [avisoDeCategoria].
                gastoPorCategoria?.let { avisoDeCategoria(category, it, ::formatCOP) }?.let { aviso ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = aviso.texto,
                        style = Movi.textos.apoyo,
                        color = if (aviso.esAdvertencia) Movi.colores.aviso else Movi.colores.textoMedio,
                        lineHeight = 15.sp,
                    )
                    // Los movimientos del período que se LLAMAN como la categoría pero están en
                    // otra. Es la otra mitad de lo que el dueño pidió: el aviso le dice qué
                    // categorías tienen gasto, y esto le deja traer el gasto a la categoría que
                    // eligió vigilar. Ver [gastosQueSuenanA].
                    val candidatos = gastosQueSuenanA(category, dias, ventana)
                    if (candidatos.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        // El texto dice CÓMO coincidió, no solo que coincidió: «llamado» cuando
                        // la descripción es el nombre de la categoría, «que mencionan» cuando
                        // apenas la nombra («Mercado Éxito»). Si el dueño no puede ver por qué se
                        // lo proponemos, no puede desconfiar de la propuesta.
                        val soloExactas = candidatos.all { it.coincidencia == Coincidencia.EXACTA }
                        Text(
                            text = when {
                                candidatos.size == 1 && soloExactas ->
                                    "Tienes un movimiento llamado \"${category.trim()}\" en otra categoría:"
                                candidatos.size == 1 ->
                                    "Tienes un movimiento que menciona \"${category.trim()}\" en otra categoría:"
                                soloExactas ->
                                    "Tienes ${candidatos.size} movimientos llamados \"${category.trim()}\" en otras categorías:"
                                else ->
                                    "Tienes ${candidatos.size} movimientos que mencionan \"${category.trim()}\" en otras categorías:"
                            },
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            lineHeight = 16.sp,
                        )
                        candidatos.take(3).forEach { (ev, _) ->
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onAsociar(ev, category.trim()) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ev.description, style = Movi.textos.cuerpo, color = Movi.colores.texto, fontWeight = FontWeight.Medium)
                                    Text("Hoy en \"${ev.category}\" · toca para moverlo aquí", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                }
                                Text(formatCOP(ev.amount), style = Movi.textos.monto, color = Movi.colores.texto)
                            }
                        }
                    }
                    if (aviso.sugerencias.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            aviso.sugerencias.forEach { sugerida ->
                                Text(
                                    text = sugerida,
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.marca,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { category = sugerida }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // Al editar un presupuesto existente la categoría es su clave — no se cambia acá.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Categoría", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = category,
                        style = Movi.textos.titulo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.texto,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            // El dueño: «En presupuestos yo debería ver cada uno de los movimientos asociados a
            // ese presupuesto». Había una barra y dos números, sin manera de contestar la
            // pregunta que sigue: ¿en qué? Un presupuesto excedido sin la lista es una acusación
            // sin pruebas — no se distingue un gasto mal archivado de uno real.
            //
            // Va FUERA del `if (categoryEditable)`. La primera versión de esto quedó dentro del
            // `else`, que **no se dibuja nunca**: los dos call sites (Sheet.Add y Sheet.Edit)
            // pasan `categoryEditable = true`. La feature compilaba, su test pasaba —prueba la
            // función pura, no la pantalla— y en la app no aparecía nada. Lo encontró la
            // revisión. Ese `else` es deuda anterior: la rama «al editar la categoría es su
            // clave» quedó inalcanzable cuando editar pasó a permitir renombrar.
            //
            // Sirve igual al crear: escribir «Mercado» y ver ahí mismo lo que ya se gastó es
            // justo lo que hace falta para elegir el monto.
            val movimientos = gastosDelPresupuesto(category, dias, ventana)
            // Se calcula FUERA del `if`: el caso en que este mensaje más falta hace es
            // justamente cuando no hay nada que listar —la barra dice \$2.000.000 y este
            // dispositivo no bajó ni un movimiento— y ahí un bloque vacío sin explicación es
            // peor que el problema que la lista vino a resolver.
            // Sin el gasto todavía no hay «total de arriba» contra el cual comparar: cero, y ningún
            // renglón de diferencia.
            val faltante = gastoPorCategoria?.let { gasto ->
                faltanMovimientosPorVer(gasto[category.trim()] ?: 0L, movimientos.sumOf { it.amount })
            } ?: 0L
            if (faltante != 0L && movimientos.isEmpty() && category.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                Text(
                    // Igual que el renglón de más abajo: se nombra la diferencia, no una causa
                    // que no se puede verificar desde acá.
                    text = "Hay ${formatCOP(faltante)} contados en esta categoría que no aparecen en esta lista.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    lineHeight = 15.sp,
                )
            }
            if (movimientos.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (movimientos.size == 1) "1 movimiento" else "${movimientos.size} movimientos",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCOP(movimientos.sumOf { it.amount }),
                        // Plata en la línea del rótulo: la talla del apoyo, tabular como un monto.
                        style = Movi.textos.apoyo.copy(fontFeatureSettings = "tnum"),
                        color = Movi.colores.textoMedio,
                    )
                }
                // Si los dos totales no coinciden se dice — no se deja que el dueño reste dos
                // números y desconfíe de los dos.
                //
                // **Se nombra la diferencia, no su causa.** Antes esto afirmaba que la plata
                // «todavía no bajó a este dispositivo», y esa explicación fue falsa justo cuando
                // más importaba: el dueño puso su corte de período en el día 25 y el resumen del
                // server seguía sumando el mes civil (ver `DashboardRoutes`), así que las dos
                // mitades de esta misma pantalla miraban meses distintos. El renglón culpaba a
                // una desincronización inexistente y mandaba a buscar el problema al lado
                // equivocado. La causa está arreglada; el renglón queda, porque un desacuerdo
                // real (un SMS que otro aparato ya subió) sigue siendo posible — pero ahora
                // constata lo que se puede ver y no adivina por qué.
                if (faltante != 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (faltante > 0L)
                            "El total de arriba cuenta ${formatCOP(faltante)} más de lo que suma esta lista."
                        else
                            "Esta lista suma ${formatCOP(-faltante)} más de lo que cuenta el total de arriba.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        lineHeight = 15.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                movimientos.forEach { ev ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ev.description, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                            Text(etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoyEnAppZone()), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        }
                        Text(
                            text = formatCOP(ev.amount),
                            style = Movi.textos.monto,
                            color = Movi.colores.texto,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Amount display
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // F14: separador de miles mientras se escribe, no solo al guardar.
                    text = "$" + formatAmountKeypadDisplay(amount),
                    // Tamaño suelto a propósito: el monto que se está tecleando; ningún estilo de la escala llega a 48.
                    fontSize = 48.sp,
                    style = Movi.textos.monto,
                    fontWeight = FontWeight.Normal,
                    color = Movi.colores.texto,
                    letterSpacing = (-1.8).sp,
                    lineHeight = 48.sp,
                )
                Spacer(Modifier.height(6.dp))
                // F16: decía "Límite mensual · COP" — la moneda ya es obvia en toda la app, pero
                // que se reinicia cada mes no, y es la pregunta real ("¿por cuánto tiempo?").
                Text("Límite mensual · se reinicia cada mes", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, letterSpacing = 0.4.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Numpad
            Column {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("000", "0", "⌫"),
                ).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable { onKey(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == "⌫") {
                                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", tint = Movi.colores.texto, modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        text = key,
                                        // Tamaño suelto a propósito: tecla del teclado numérico; `titular` (19) no es para dígitos y `cifra` (42) no cabe.
                                        fontSize = 20.sp,
                                        style = Movi.textos.monto,
                                        fontWeight = FontWeight.Normal,
                                        color = Movi.colores.texto,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
            Spacer(Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp))
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Eliminar",
                            style = Movi.textos.cuerpo,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.sale,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(if (onDelete != null) 1.4f else 1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSave) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
                        // Ola 2 #2: recorte al guardar — canSave ya exige no-vacío, pero
                        // "  Comida  " pasaba esa guarda y se guardaba con espacios.
                        .clickable(enabled = canSave) { onSave(category.trim(), parsedAmount) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Guardar",
                        style = Movi.textos.cuerpo,
                        fontWeight = FontWeight.Medium,
                        color = if (canSave) Movi.colores.marca else Movi.colores.textoMedio,
                    )
                }
            }
            if (!canSave && missingFieldMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = missingFieldMessage,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

/** La tarjeta de «Gastado en …», cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DEL_GASTO_DEL_PERIODO: String = "tarjeta-del-gasto-del-periodo"

/** La cifra esqueleto de «Gastado en …» — está solo mientras carga. */
const val TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO: String = "esqueleto-del-gasto-del-periodo"

/** Cada categoría que todavía no llegó. */
const val TAG_ESQUELETO_FILA_DE_PRESUPUESTO: String = "esqueleto-fila-de-presupuesto"

/**
 * **Presupuestos mientras carga: la forma, sin «$0» ni «0 %».**
 *
 * Ola B. La tarjeta de «Gastado en …» con su cifra, el «de $…» y la fila de sobrepasados/sin
 * margen **reservada** —es la que más saltaba: aparecía de golpe con el gasto—, y cuatro categorías
 * con su barra. Los rellenos y estilos son los de la tarjeta real y los de [BudgetCard], para que
 * nada cambie de alto cuando llega el dato (±8 dp, lo mide `PresupuestosNoAfirmanMientrasCarganTest`).
 * Un mes sin nada sobrepasado encoge esa fila al llegar; nunca crece.
 *
 * Ola C: son renglones de la misma lista que después pinta los datos (ver [presupuestos]), no una
 * lista aparte, para que en Plan vayan debajo del disponible con el mismo scroll.
 */
private fun LazyListScope.presupuestosEsqueleto() {
    item {
        MinCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(TAG_TARJETA_DEL_GASTO_DEL_PERIODO),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(22.dp),
        ) {
            // El nombre del mes también espera: sale del período del dueño, que llega con el
            // perfil — con corte 25, el 26 ya es el mes siguiente.
            LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.apoyo)
            Spacer(Modifier.height(10.dp))
            LineaEsqueleto(
                fraccionDelAncho = 0.55f,
                estilo = Movi.textos.cifra,
                modifier = Modifier.testTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO),
            )
            Spacer(Modifier.height(6.dp))
            LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.monto)
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            // Las dos insignias de [AlertBadge]: el número y su rótulo, a 20 dp una de otra.
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(2) {
                    Column {
                        BloqueEsqueleto(alto = altoDeUnRenglon(estiloDelContador()), ancho = 24.dp)
                        BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.apoyo), ancho = 76.dp)
                    }
                }
            }
        }
    }
    item {
        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            RotuloDeSeccionEsqueleto()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { FilaDePresupuestoEsqueleto() }
            }
        }
    }
}

/** Una categoría que todavía no llegó, con la forma de [BudgetCard]: ícono, nombre y porcentaje, «$… de $…» y la barra. */
@Composable
private fun FilaDePresupuestoEsqueleto() {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Ola B, tarea 3 (fix round 1): el círculo de 24 dp de `IconoDeCategoria` (tamaño
            // `Chico`), al mismo `Movi.espacios.corto` (8 dp) del nombre que usa `BudgetCard` —
            // sin esto el título arrancaba ~32 dp más a la izquierda que en la fila real.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
            ) {
                CirculoEsqueleto(24.dp)
                LineaEsqueleto(
                    fraccionDelAncho = 0.45f,
                    estilo = Movi.textos.titulo,
                    modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                )
            }
            BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.apoyo), ancho = 32.dp)
            Spacer(Modifier.width(6.dp))
            // El lugar del chevron (18 dp).
            Spacer(Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1.7f)) { LineaEsqueleto(fraccionDelAncho = 0.8f, estilo = Movi.textos.monto) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                LineaEsqueleto(fraccionDelAncho = 0.8f, estilo = Movi.textos.apoyo)
            }
        }
        Spacer(Modifier.height(8.dp))
        BloqueEsqueleto(alto = 2.dp)
    }
}
