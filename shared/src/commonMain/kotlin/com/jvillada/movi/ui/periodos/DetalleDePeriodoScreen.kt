package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PAGO_FIJO_CON_DUDAS
import com.jvillada.movi.shared.model.PAGO_FIJO_LISTO
import com.jvillada.movi.shared.model.PAGO_FIJO_PENDIENTE
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PresupuestoDelPeriodo
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.shared.model.empezarElSiguienteHoy
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.budgets.colorDelEstadoDePresupuesto
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.dashboard.categoriasDelPeriodo
import com.jvillada.movi.ui.profile.guardarInicioDelPeriodo
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.sdui.FilaDeCategoria
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * # El detalle de un período
 *
 * Todo lo que el server sabe de UN período (`GET /api/periodos/{id}`): cuánto entró y salió, en
 * qué se fue, cómo quedaron los pagos fijos y los presupuestos, y los gastos más grandes. Nada se
 * calcula acá — son las mismas reglas del resto de la app, aplicadas por el server a la ventana de
 * ese período (ver [DetalleDePeriodo]).
 *
 * Dos acciones cierran la pantalla: ver los movimientos de ese período en Movimientos, y —solo en
 * el período en curso— **empezar el siguiente hoy**, para el mes en que el sueldo llega antes del
 * corte. Esa segunda se confirma adentro de la pantalla, sin un diálogo del sistema.
 */

const val TAG_DETALLE_DE_PERIODO_ESQUELETO: String = "detalle-de-periodo-esqueleto"
const val TAG_VER_MOVIMIENTOS_DEL_PERIODO: String = "ver-movimientos-del-periodo"
const val TAG_EMPEZAR_PERIODO_HOY: String = "empezar-periodo-hoy"
const val TAG_CONFIRMAR_PERIODO_HOY: String = "confirmar-periodo-hoy"
const val TAG_GASTO_GRANDE: String = "gasto-grande"

@Stable
internal class EstadoDelDetalleDePeriodo internal constructor(
    private val alcance: CoroutineScope,
    private val id: String,
    private val hoy: LocalDate,
) {
    internal var detalle by mutableStateOf<DetalleDePeriodo?>(null)
    internal var loading by mutableStateOf(true)
    internal var refreshKey by mutableStateOf(0)

    /**
     * El corte y los arranques propios del dueño. `null` mientras no se leyó (o si falló): sin
     * ellos no se puede decir si hoy es un arranque válido, así que la acción de empezar hoy no se
     * ofrece — escribir un mapa armado sobre ajustes que no se leyeron podría borrar excepciones.
     */
    internal var ajustes by mutableStateOf<PeriodSettings?>(null)

    /** Las cuentas, para la hoja del movimiento. Vacía = no llegaron (la hoja lo tolera). */
    internal var cuentas by mutableStateOf<List<Account>>(emptyList())

    internal var confirmando by mutableStateOf(false)
    internal var guardando by mutableStateOf(false)
    internal var errorAlGuardar by mutableStateOf<String?>(null)
    internal var movimientoAbierto by mutableStateOf<FinancialEvent?>(null)
    internal var nuevoPagoFijoAbierto by mutableStateOf(false)

    internal val noSeLeyo: Boolean get() = !loading && detalle == null

    internal val periodo: PeriodoFinanciero? get() = periodoDelPrefijo(id)

    /**
     * Los ajustes que quedarían si el período siguiente empezara hoy, o `null` si no se ofrece:
     * solo en el período en curso, con los ajustes leídos y cuando la regla de :core lo acepta
     * (ver [empezarElSiguienteHoy]).
     */
    internal val empezarHoy: PeriodSettings?
        get() {
            val resumen = detalle?.resumen?.takeIf { it.enCurso } ?: return null
            val periodo = periodoDelPrefijo(resumen.id) ?: return null
            val ajustes = ajustes ?: return null
            return empezarElSiguienteHoy(periodo, hoy, ajustes)
        }

    internal fun reintentar() {
        refreshKey++
    }

    internal fun cargar() {
        alcance.launch {
            loading = true
            intentar { Repositories.wallets.getDetalleDePeriodo(id) }.onSuccess { detalle = it }
            intentar { Repositories.wallets.getUserProfile() }.onSuccess {
                ajustes = PeriodSettings(cutoffDay = it.periodCutoffDay.coerceIn(1, 31), iniciosPropios = it.periodStarts)
            }
            loading = false
        }
        alcance.launch {
            intentar { Repositories.wallets.getAccounts() }.onSuccess { cuentas = it }
        }
    }

    /**
     * Guarda el arranque del siguiente en hoy por el mismo camino que la hoja de Movimientos
     * ([guardarInicioDelPeriodo]) y recarga. El Inicio se entera solo: toda escritura por
     * `Repositories.wallets` invalida su caché (ver `InvalidaElInicioAlEscribir`), y «Tus
     * períodos» vuelve a leer al entrar.
     */
    internal fun confirmarEmpezarHoy() {
        if (guardando) return
        val ajustesActuales = ajustes ?: return
        val periodo = periodo ?: return
        if (empezarHoy == null) return
        guardando = true
        errorAlGuardar = null
        alcance.launch {
            intentar { guardarInicioDelPeriodo(ajustesActuales, periodoSiguiente(periodo), hoy.toString()) }
                .onSuccess {
                    ajustes = PeriodSettings(cutoffDay = it.periodCutoffDay.coerceIn(1, 31), iniciosPropios = it.periodStarts)
                    confirmando = false
                    reintentar()
                }
                .onFailure { errorAlGuardar = it.toUserMessage() }
            guardando = false
        }
    }
}

@Composable
internal fun rememberEstadoDelDetalleDePeriodo(id: String, hoy: LocalDate): EstadoDelDetalleDePeriodo {
    val alcance = rememberCoroutineScope()
    val estado = remember(alcance, id, hoy) { EstadoDelDetalleDePeriodo(alcance, id, hoy) }
    LaunchedEffect(estado, estado.refreshKey) { estado.cargar() }
    return estado
}

/**
 * @param id el prefijo del período («2026-09»).
 * @param hoy el día de hoy en la zona de la app; solo lo fija una prueba.
 */
@Composable
fun DetalleDePeriodoScreen(onNavigate: (Screen) -> Unit, id: String, hoy: LocalDate? = null) {
    val hoyEfectivo = remember(hoy) { hoy ?: epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) }
    val estado = rememberEstadoDelDetalleDePeriodo(id, hoyEfectivo)
    val titulo = estado.detalle?.resumen?.nombre ?: nombreDelId(id) ?: id

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(title = titulo, leading = HeaderLeading.Back(fallback = Screen.Periodos))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Movi.espacios.amplio)
                    .padding(top = Movi.espacios.corto, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            ) {
                val detalle = estado.detalle
                when {
                    estado.noSeLeyo -> NoSePudoLeer("No pudimos cargar este período", onReintentar = { estado.reintentar() })
                    detalle == null -> DetalleEsqueleto()
                    else -> DetalleCargado(detalle, estado, hoyEfectivo, onNavigate)
                }
            }
        }

        estado.movimientoAbierto?.let { event ->
            val tipos = estado.cuentas.associate { it.id to it.type }
            // La misma hoja que abre tocar un movimiento en Movimientos.
            HojaDelMovimiento(
                event = event,
                cuentas = estado.cuentas,
                onDismiss = { estado.movimientoAbierto = null },
                onCambiado = { estado.movimientoAbierto = null; estado.reintentar() },
                onVerCuenta = tipos[event.accountId]?.let { tipo ->
                    { onNavigate(Screen.AccountDetail(event.accountId, tipo.group)) }
                },
            )
        }
        if (estado.nuevoPagoFijoAbierto) {
            // La misma hoja, en su modo de alta, que abre «Agregar un pago fijo» en Plan.
            CreateRecurringRuleSheet(
                onDismiss = { estado.nuevoPagoFijoAbierto = false },
                onSaved = { estado.nuevoPagoFijoAbierto = false; estado.reintentar() },
            )
        }
    }
}

@Composable
private fun DetalleCargado(
    detalle: DetalleDePeriodo,
    estado: EstadoDelDetalleDePeriodo,
    hoy: LocalDate,
    onNavigate: (Screen) -> Unit,
) {
    Cabecera(detalle, estado.ajustes)
    EnQueSeFue(detalle)
    PagosFijos(detalle.pagosFijos, onAgregar = { estado.nuevoPagoFijoAbierto = true })
    if (detalle.presupuestos.isNotEmpty()) Presupuestos(detalle.presupuestos)
    // Sin gastos no hay «más grandes»: el vacío de «En qué se fue» ya lo dice, y dos tarjetas
    // vacías seguidas contarían lo mismo dos veces.
    if (detalle.masGrandes.isNotEmpty()) {
        LosMasGrandes(detalle.masGrandes, onAbrir = { estado.movimientoAbierto = it })
    }

    FilaDeAccion(
        texto = "Ver los movimientos de este período",
        modifier = Modifier.testTag(TAG_VER_MOVIMIENTOS_DEL_PERIODO),
        onClick = { onNavigate(Screen.Transactions(periodoInicial = detalle.resumen.id)) },
    )

    val periodo = estado.periodo
    if (periodo != null && estado.empezarHoy != null) {
        if (estado.confirmando) {
            ConfirmarEmpezarHoy(periodo, hoy, estado)
        } else {
            FilaDeAccion(
                texto = "Empezar un período nuevo hoy",
                modifier = Modifier.testTag(TAG_EMPEZAR_PERIODO_HOY),
                onClick = { estado.errorAlGuardar = null; estado.confirmando = true },
            )
        }
    }
}

// ── Arriba ───────────────────────────────────────────────────────────────────

/**
 * «Este período empezó el 24 de septiembre (antes del día 25)», o `null` si no arrancó distinto.
 * Sin los ajustes no se sabe cuál era el día de siempre y se dice solo la fecha.
 */
internal fun fraseDelInicioPropio(resumen: ResumenDePeriodo, ajustes: PeriodSettings?): String? {
    if (!resumen.inicioPropio) return null
    val desde = runCatching { LocalDate.parse(resumen.desde) }.getOrNull() ?: return null
    val base = "Este período empezó el ${diaLegible(desde)}"
    val periodo = periodoDelPrefijo(resumen.id) ?: return base
    val natural = ajustes?.let { inicioDelPeriodo(periodo, it.copy(iniciosPropios = emptyMap())) } ?: return base
    return when {
        desde < natural -> "$base (antes del día ${natural.dayOfMonth})"
        desde > natural -> "$base (después del día ${natural.dayOfMonth})"
        else -> base
    }
}

/**
 * «Tu plata: $12,3M al empezar → $10,1M al cerrar» («hoy» en el período en curso), o `null` cuando
 * el server no puede decir alguna de las dos sin mentir — ver [DetalleDePeriodo.tuPlataAlEmpezar].
 */
internal fun lineaDeTuPlata(detalle: DetalleDePeriodo): String? {
    val alEmpezar = detalle.tuPlataAlEmpezar ?: return null
    val alCerrar = detalle.tuPlataAlCerrar ?: return null
    val cuando = if (detalle.resumen.enCurso) "hoy" else "al cerrar"
    return "Tu plata: ${formatMoneyCompact(alEmpezar)} al empezar → ${formatMoneyCompact(alCerrar)} $cuando"
}

@Composable
private fun Cabecera(detalle: DetalleDePeriodo, ajustes: PeriodSettings?) {
    val resumen = detalle.resumen
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        rangoDelResumen(resumen)?.let {
            Text(text = it, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        }
        fraseDelInicioPropio(resumen, ajustes)?.let {
            Spacer(Modifier.height(3.dp))
            Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.marca, fontWeight = FontWeight.Medium)
        }
        if (resumen.enCurso) {
            Spacer(Modifier.height(3.dp))
            Text(text = "En curso", style = Movi.textos.apoyo, color = Movi.colores.marca, fontWeight = FontWeight.Medium)
        }
        CifrasDelPeriodo(resumen)
        lineaDeTuPlata(detalle)?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

// ── En qué se fue ────────────────────────────────────────────────────────────

@Composable
private fun EnQueSeFue(detalle: DetalleDePeriodo) {
    // La misma pieza del Inicio, con todas las categorías (acá no hay un resumen que proteger) y
    // los límites de los presupuestos, para que una categoría pasada se pinte igual que allá.
    val categorias = categoriasDelPeriodo(
        gastoPorCategoria = detalle.porCategoria.associate { it.category to it.monto },
        presupuestos = detalle.presupuestos.map { Budget(category = it.category, monthlyLimit = it.limite) },
        cuantas = Int.MAX_VALUE,
    )
    Column {
        MinSectionHeader(title = "En qué se fue")
        if (categorias.isEmpty()) {
            VacioQueEnsena(
                titulo = "Sin gastos en este período",
                detalle = "Cuando haya gastos entre estas fechas, aquí vas a ver en qué categorías se fue tu plata.",
            )
        } else {
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(Movi.espacios.margen),
            ) {
                categorias.forEachIndexed { i, categoria ->
                    FilaDeCategoria(categoria, entrada = 1f)
                    if (i < categorias.lastIndex) Spacer(Modifier.height(Movi.espacios.medio))
                }
            }
        }
    }
}

// ── Pagos fijos ──────────────────────────────────────────────────────────────

/** «Pagos fijos · 3 de 5»: cuántos quedaron listos de todos los del período. */
internal fun tituloDePagosFijos(pagos: List<PagoFijoDelPeriodo>): String =
    if (pagos.isEmpty()) "Pagos fijos"
    else "Pagos fijos · ${pagos.count { it.estado == PAGO_FIJO_LISTO }} de ${pagos.size}"

/**
 * Lo que se lee del lado derecho de una fila: el monto (el real si el movimiento que lo prueba
 * difiere de la regla) y su estado. Un estado que esta versión no conoce no dice nada, en vez de
 * afirmar uno que no es.
 */
internal fun estadoDePagoFijo(pago: PagoFijoDelPeriodo): Pair<Long, String?> = when (pago.estado) {
    PAGO_FIJO_LISTO -> {
        val real = pago.montoReal
        if (real != null && real != pago.monto) real to "✓ la regla dice ${formatCOP(pago.monto)}"
        else pago.monto to "✓"
    }
    PAGO_FIJO_PENDIENTE -> pago.monto to "pendiente"
    PAGO_FIJO_CON_DUDAS -> pago.monto to "con dudas"
    else -> pago.monto to null
}

@Composable
private fun PagosFijos(pagos: List<PagoFijoDelPeriodo>, onAgregar: () -> Unit) {
    Column {
        MinSectionHeader(title = tituloDePagosFijos(pagos))
        if (pagos.isEmpty()) {
            VacioQueEnsena(
                titulo = "Sin pagos fijos en este período",
                detalle = "Arriendo, colegio, cuotas, suscripciones: anótalos una vez y cada período Movi " +
                    "te dice cuáles faltan y los marca solos cuando salen.",
                accion = "Agregar un pago fijo",
                onAccion = onAgregar,
            )
        } else {
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                pagos.forEachIndexed { i, pago ->
                    FilaDePagoFijo(pago)
                    if (i < pagos.lastIndex) Spacer(Modifier.height(Movi.espacios.corto))
                }
            }
        }
    }
}

@Composable
private fun FilaDePagoFijo(pago: PagoFijoDelPeriodo) {
    val (monto, estado) = estadoDePagoFijo(pago)
    val colorDelEstado = when (pago.estado) {
        PAGO_FIJO_LISTO -> Movi.colores.entra
        PAGO_FIJO_CON_DUDAS -> Movi.colores.aviso
        else -> Movi.colores.textoMedio
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pago.nombre,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            diaLegible(pago.vencimiento)?.let {
                Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            }
        }
        Spacer(Modifier.size(Movi.espacios.corto))
        Column(horizontalAlignment = Alignment.End) {
            Cifra(
                if (pago.esIngreso) "+" + formatCOP(monto) else formatCOP(monto),
                Movi.textos.monto,
                color = if (pago.esIngreso) Movi.colores.entra else Movi.colores.texto,
            )
            estado?.let { Text(text = it, style = Movi.textos.apoyo, color = colorDelEstado) }
        }
    }
}

// ── Presupuestos ─────────────────────────────────────────────────────────────

@Composable
private fun Presupuestos(presupuestos: List<PresupuestoDelPeriodo>) {
    Column {
        MinSectionHeader(title = "Presupuestos")
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            presupuestos.forEachIndexed { i, p ->
                FilaDePresupuesto(p)
                if (i < presupuestos.lastIndex) Spacer(Modifier.height(Movi.espacios.medio))
            }
        }
    }
}

@Composable
private fun FilaDePresupuesto(p: PresupuestoDelPeriodo) {
    // El mismo semáforo que la tarjeta de Presupuestos.
    val color = colorDelEstadoDePresupuesto(estadoDePresupuesto(p.gastado, p.limite))
    val fraccion = if (p.limite > 0) p.gastado.toFloat() / p.limite else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoDeCategoria(p.category, tamano = TamanoDeIconoDeCategoria.Chico)
            Spacer(Modifier.size(Movi.espacios.minimo))
            Text(
                text = p.category,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(Movi.espacios.corto))
            Text(
                text = "${formatCOP(p.gastado)} de ${formatCOP(p.limite)}",
                style = Movi.textos.apoyo,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Movi.colores.hilo),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

// ── Los más grandes ──────────────────────────────────────────────────────────

@Composable
private fun LosMasGrandes(eventos: List<FinancialEvent>, onAbrir: (FinancialEvent) -> Unit) {
    Column {
        MinSectionHeader(title = "Los más grandes")
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(vertical = 4.dp),
        ) {
            eventos.forEach { evento ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_GASTO_GRANDE)
                        .clickable { onAbrir(evento) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = evento.description,
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = diaLegible(epochMillisToAppDate(evento.timestamp)),
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoApagado,
                        )
                    }
                    Spacer(Modifier.size(Movi.espacios.corto))
                    Cifra(formatCOP(evento.amount), Movi.textos.monto, color = Movi.colores.texto)
                }
            }
        }
    }
}

// ── Acciones ─────────────────────────────────────────────────────────────────

@Composable
private fun FilaDeAccion(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MinCard(
        modifier = modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(texto, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            ChevronRight()
        }
    }
}

/** «Octubre» — el mes que nombra al período, con mayúscula, como en su título. */
private fun mesDe(periodo: PeriodoFinanciero): String = tituloDelPeriodo(periodo).substringBefore(" ")

/**
 * «Octubre termina hoy y Noviembre empieza hoy, 20 de octubre. Úsalo cuando…» — lo que la
 * confirmación dice antes de guardar.
 */
internal fun textoDeEmpezarHoy(periodo: PeriodoFinanciero, hoy: LocalDate): String =
    "${mesDe(periodo)} termina hoy y ${mesDe(periodoSiguiente(periodo))} empieza hoy, ${diaLegible(hoy)}. " +
        "Úsalo cuando tu plata del mes nuevo ya llegó (por ejemplo, el sueldo se pagó antes)."

@Composable
private fun ConfirmarEmpezarHoy(periodo: PeriodoFinanciero, hoy: LocalDate, estado: EstadoDelDetalleDePeriodo) {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Text(
            text = textoDeEmpezarHoy(periodo, hoy),
            style = Movi.textos.cuerpo,
            fontWeight = FontWeight.Normal,
            color = Movi.colores.texto,
        )
        estado.errorAlGuardar?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag(TAG_CONFIRMAR_PERIODO_HOY)
                .clip(RoundedCornerShape(999.dp))
                .background(if (!estado.guardando) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
                .clickable(enabled = !estado.guardando) { estado.confirmarEmpezarHoy() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (estado.guardando) "Guardando…" else "Empezar ${mesDe(periodoSiguiente(periodo))} hoy",
                style = Movi.textos.cuerpo,
                color = if (!estado.guardando) Movi.colores.marca else Movi.colores.textoApagado,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Cancelar",
            style = Movi.textos.apoyo,
            fontWeight = FontWeight.Medium,
            color = if (estado.guardando) Movi.colores.textoApagado else Movi.colores.textoMedio,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(enabled = !estado.guardando) { estado.confirmando = false },
        )
    }
}

// ── Esqueleto ────────────────────────────────────────────────────────────────

/** La forma del detalle mientras no llegó: la cabecera y una sección con tres categorías. */
@Composable
private fun DetalleEsqueleto() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_DETALLE_DE_PERIODO_ESQUELETO),
        verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(16.dp),
        ) {
            LineaEsqueleto(fraccionDelAncho = 0.65f, estilo = Movi.textos.cuerpo)
            CifrasDelPeriodoEsqueleto()
        }
        Column {
            LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.titulo)
            Spacer(Modifier.height(Movi.espacios.corto))
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(Movi.espacios.margen),
            ) {
                repeat(3) { i ->
                    LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.cuerpo)
                    Spacer(Modifier.height(Movi.espacios.minimo + 2.dp))
                    BloqueEsqueleto(alto = 6.dp)
                    if (i < 2) Spacer(Modifier.height(Movi.espacios.medio))
                }
            }
        }
    }
}
