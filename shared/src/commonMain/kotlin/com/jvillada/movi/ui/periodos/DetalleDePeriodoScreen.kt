package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.saveable.rememberSaveable
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Lectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.periodoVigenteSegun
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.components.ActualizandoEnLaCabecera
import com.jvillada.movi.ui.components.NoSePudoActualizar
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FUENTE_SALDO_INICIAL
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PAGO_FIJO_CON_DUDAS
import com.jvillada.movi.shared.model.PAGO_FIJO_LISTO
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ajustesDelPeriodo
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
import com.jvillada.movi.ui.profile.LosPeriodosCambiaron
import com.jvillada.movi.ui.profile.cambiarLosIniciosPropios
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.recurrentes.EstadoVisualDelPago
import com.jvillada.movi.ui.recurrentes.FilaDePagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.GrupoDePagos
import com.jvillada.movi.ui.recurrentes.ResumenDeLaLista
import com.jvillada.movi.ui.recurrentes.TITULO_CHECKLIST_DEL_PERIODO
import com.jvillada.movi.ui.recurrentes.TITULO_FALTA_POR_PAGAR
import com.jvillada.movi.ui.recurrentes.TITULO_NO_LLEGO
import com.jvillada.movi.ui.recurrentes.TITULO_NO_SE_PAGO
import com.jvillada.movi.ui.recurrentes.TITULO_POR_COBRAR
import com.jvillada.movi.ui.recurrentes.TITULO_YA_PAGASTE
import com.jvillada.movi.ui.recurrentes.TITULO_YA_RECIBISTE
import com.jvillada.movi.ui.recurrentes.evidenciaDeLaFila
import com.jvillada.movi.ui.recurrentes.fechaDeLaFila
import com.jvillada.movi.ui.recurrentes.fechaLegibleDelChecklist
import com.jvillada.movi.ui.recurrentes.textoDelMontoDelChecklist
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Schedule
import kotlinx.datetime.daysUntil
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
const val TAG_DE_DONDE_SALIO_LO_QUE_FALTO: String = "de-donde-salio-lo-que-falto"

@Stable
internal class EstadoDelDetalleDePeriodo internal constructor(
    private val alcance: CoroutineScope,
    private val id: String,
    /** Lo que además hay que releer con [reintentar]: el perfil compartido con la lista (Ola W2). */
    private val alReintentar: () -> Unit = {},
    /**
     * El día de hoy en la zona de la app, leído cada vez que se pregunta — no al montar. Va último
     * para que se pueda pasar como lambda final.
     */
    private val hoy: () -> LocalDate,
) {
    /** Hoy, para decir «venció hace 2 días» en las filas de pagos fijos del período en curso. */
    internal val hoyDeLasFilas: LocalDate get() = hoy()

    /**
     * El perfil, el detalle y las cuentas (ver [rememberEstadoDelDetalleDePeriodo]). Los pone la
     * composición en cada pasada: el detalle se rehace cuando cambia el período vigente, y este
     * estado —con la confirmación abierta— no.
     */
    private var lecturas by mutableStateOf<LecturasDelDetalle?>(null)

    internal fun conectar(nuevas: LecturasDelDetalle) {
        if (lecturas != nuevas) lecturas = nuevas
    }

    internal val detalle: DetalleDePeriodo? get() = lecturas?.detalle?.valor
    /** Hay una lectura en vuelo (o el detalle espera el período para salir). */
    internal val loading: Boolean get() = lecturas?.let { it.detalle.actualizando || it.perfil.actualizando } ?: true
    internal var refreshKey by mutableStateOf(0)

    /**
     * El corte y los arranques propios del dueño. `null` mientras no se sabe (o si falló): sin
     * ellos no se puede decir si hoy es un arranque válido, así que la acción de empezar hoy no se
     * ofrece — escribir un mapa armado sobre ajustes que no se leyeron podría borrar excepciones.
     */
    internal val ajustes: PeriodSettings? get() = lecturas?.perfil?.valor?.ajustesDelPeriodo()

    /** Las cuentas, para la hoja del movimiento. Vacía = no llegaron (la hoja lo tolera). */
    internal val cuentas: List<Account> get() = lecturas?.cuentas?.valor.orEmpty()

    /** Lo que se ve es lo último que vimos: una lectura falló con el detalle a la vista. */
    internal val noSePudoActualizar: Boolean get() {
        val l = lecturas ?: return false
        return l.detalle.falloConAlgoALaVista || (l.perfil.falloConAlgoALaVista && detalle != null)
    }

    /** Hay algo pintado que esta visita todavía no confirmó: «Actualizando…» en la cabecera. */
    internal val actualizandoConAlgoALaVista: Boolean get() = detalle != null && loading

    internal var confirmando by mutableStateOf(false)
    /** El día que dice la confirmación: el de cuando se abrió, y el de cuando se confirmó. */
    internal var hoyDeLaConfirmacion by mutableStateOf<LocalDate?>(null)
    internal var guardando by mutableStateOf(false)
    internal var errorAlGuardar by mutableStateOf<String?>(null)
    internal var movimientoAbierto by mutableStateOf<FinancialEvent?>(null)
    internal var nuevoPagoFijoAbierto by mutableStateOf(false)

    internal val noSeLeyo: Boolean get() = !loading && detalle == null

    /**
     * El período en curso según lo que contestó el server, o `null` si este no lo es. Es el mismo
     * para decidir si se ofrece empezar hoy y para guardarlo: nunca se revisa uno y se escribe otro.
     */
    internal val periodoEnCurso: PeriodoFinanciero?
        get() = detalle?.resumen?.takeIf { it.enCurso }?.let { periodoDelPrefijo(it.id) }

    /**
     * Los ajustes que quedarían si el período siguiente empezara hoy, o `null` si no se ofrece:
     * solo en el período en curso, con los ajustes leídos y cuando la regla de :core lo acepta
     * (ver [empezarElSiguienteHoy]).
     */
    internal val empezarHoy: PeriodSettings?
        get() {
            val periodo = periodoEnCurso ?: return null
            val ajustes = ajustes ?: return null
            return empezarElSiguienteHoy(periodo, hoy(), ajustes)
        }

    internal fun abrirConfirmacion() {
        errorAlGuardar = null
        hoyDeLaConfirmacion = hoy()
        confirmando = true
    }

    internal fun reintentar() {
        refreshKey++
        alReintentar()
    }

    /**
     * Guarda el arranque del siguiente en hoy por el mismo camino que la hoja de Movimientos
     * ([cambiarLosIniciosPropios], que relee el perfil antes de escribir) y recarga. «Hoy» se lee
     * al confirmar, no al montar: una pantalla abierta desde anoche no escribe la fecha de ayer.
     * El Inicio se entera solo: toda escritura por `Repositories.wallets` invalida su caché (ver
     * `InvalidaElInicioAlEscribir`), y «Tus períodos» vuelve a leer al entrar.
     */
    internal fun confirmarEmpezarHoy() {
        if (guardando) return
        val periodo = periodoEnCurso ?: return
        if (ajustes == null) return
        val hoyAlConfirmar = hoy()
        hoyDeLaConfirmacion = hoyAlConfirmar
        guardando = true
        errorAlGuardar = null
        alcance.launch {
            intentar { cambiarLosIniciosPropios { empezarElSiguienteHoy(periodo, hoyAlConfirmar, it) } }
                .onSuccess {
                    // Lo que devolvió la escritura es el perfil nuevo: se muestra ya.
                    lecturas?.perfil?.anotar(it)
                    confirmando = false
                    reintentar()
                }
                .onFailure { errorAlGuardar = if (it is LosPeriodosCambiaron) it.message else it.toUserMessage() }
            guardando = false
        }
    }
}

/** Las tres lecturas del detalle, juntas. Ver [EstadoDelDetalleDePeriodo.conectar]. */
internal data class LecturasDelDetalle(
    val perfil: Lectura<UserProfile>,
    val detalle: Lectura<DetalleDePeriodo>,
    val cuentas: Lectura<List<Account>>,
)

/**
 * **Lo último que se vio, al primer cuadro** (ver [rememberLectura]). El detalle depende del período
 * de hoy (el en curso cambia de naturaleza cuando cierra), así que se recuerda con él y no se lee
 * ni se muestra hasta saberlo por el perfil. Se relee con «Reintentar», tras una escritura desde la
 * pantalla, y con `LocalRefreshTick`.
 *
 * [perfilCompartido]: el perfil que ya leyó la lista de al lado (Ola W2). Con él el detalle no lo
 * vuelve a pedir, y lo que el detalle relee o anota en él lo ve también la lista.
 */
@Composable
internal fun rememberEstadoDelDetalleDePeriodo(
    id: String,
    hoy: () -> LocalDate,
    perfilCompartido: PerfilCompartido? = null,
): EstadoDelDetalleDePeriodo {
    val alcance = rememberCoroutineScope()
    val estado = remember(alcance, id, hoy) {
        EstadoDelDetalleDePeriodo(alcance, id, alReintentar = perfilCompartido?.releer ?: {}, hoy = hoy)
    }
    val perfil = perfilCompartido?.lectura
        ?: rememberLectura(ClaveDeLectura.Perfil, estado.refreshKey) { Repositories.wallets.getUserProfile() }
    val detalle = rememberLectura(ClaveDeLectura.DetalleDePeriodo(id), estado.refreshKey, periodoVigenteSegun(perfil)) {
        Repositories.wallets.getDetalleDePeriodo(id)
    }
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, estado.refreshKey) { Repositories.wallets.getAccounts() }
    estado.conectar(LecturasDelDetalle(perfil, detalle, cuentas))
    return estado
}

/**
 * El detalle a pantalla completa, con su cabecera y la flecha ‹ a «Tus períodos». Es lo que se ve en
 * el teléfono; en la web, al lado de la lista, va [ContenidoDelDetalleDePeriodo] sin la flecha (ver
 * [PeriodosListaYDetalle]).
 *
 * @param id el prefijo del período («2026-09»).
 * @param hoy el día de hoy en la zona de la app; solo lo fija una prueba.
 */
@Composable
fun DetalleDePeriodoScreen(onNavigate: (Screen) -> Unit, id: String, hoy: LocalDate? = null) {
    ContenidoDelDetalleDePeriodo(onNavigate = onNavigate, id = id, conCabecera = true, conFlecha = true, hoy = hoy)
}

/** Las dos columnas del detalle cuando el panel es ancho (ver [detalleEnDosColumnas]). */
const val TAG_COLUMNA_IZQUIERDA_DEL_DETALLE: String = "detalle-de-periodo-columna-izquierda"
const val TAG_COLUMNA_DERECHA_DEL_DETALLE: String = "detalle-de-periodo-columna-derecha"

/**
 * # El cuerpo del detalle de un período
 *
 * [conCabecera] dibuja arriba el título del período y «Actualizando…» mientras lo recordado se
 * confirma; [conFlecha] le pone la flecha ‹ (solo en el teléfono: al lado de la lista no hay a
 * dónde volver). [perfilCompartido] es el perfil que ya leyó la lista de al lado.
 *
 * En el teléfono se compone exactamente como siempre. En mediano y expandido mide el panel que le
 * tocó: si a cada columna le quedan al menos [ANCHO_MINIMO_DE_COLUMNA_DEL_DETALLE] (ver
 * [detalleEnDosColumnas]) las secciones van en dos columnas, arriba de las acciones, que siguen a lo
 * ancho.
 */
@Composable
internal fun ContenidoDelDetalleDePeriodo(
    onNavigate: (Screen) -> Unit,
    id: String,
    conCabecera: Boolean,
    conFlecha: Boolean = true,
    hoy: LocalDate? = null,
    perfilCompartido: PerfilCompartido? = null,
) {
    val reloj: () -> LocalDate = remember(hoy) { { hoy ?: epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) } }
    val estado = rememberEstadoDelDetalleDePeriodo(id, reloj, perfilCompartido)
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        CuerpoDelDetalle(onNavigate, id, estado, conCabecera, conFlecha, enDosColumnas = false)
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            CuerpoDelDetalle(onNavigate, id, estado, conCabecera, conFlecha, enDosColumnas = detalleEnDosColumnas(maxWidth))
        }
    }
}

@Composable
private fun CuerpoDelDetalle(
    onNavigate: (Screen) -> Unit,
    id: String,
    estado: EstadoDelDetalleDePeriodo,
    conCabecera: Boolean,
    conFlecha: Boolean,
    enDosColumnas: Boolean,
) {
    val titulo = estado.detalle?.resumen?.nombre ?: nombreDelId(id) ?: id
    // Por período: al elegir otro en la lista de al lado, el detalle nuevo arranca desde arriba.
    val desplazamiento = rememberSaveable(id, saver = ScrollState.Saver) { ScrollState(0) }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (conCabecera) {
                MinScreenHeader(
                    title = titulo,
                    leading = if (conFlecha) HeaderLeading.Back(fallback = Screen.Periodos) else HeaderLeading.Ninguno,
                    action = if (estado.actualizandoConAlgoALaVista) {
                        { ActualizandoEnLaCabecera() }
                    } else null,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(desplazamiento)
                    .padding(horizontal = Movi.espacios.amplio)
                    .padding(top = Movi.espacios.corto, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            ) {
                val detalle = estado.detalle
                if (estado.noSePudoActualizar) NoSePudoActualizar(onReintentar = { estado.reintentar() })
                when {
                    estado.noSeLeyo -> NoSePudoLeer("No pudimos cargar este período", onReintentar = { estado.reintentar() })
                    detalle == null -> DetalleEsqueleto()
                    else -> DetalleCargado(detalle, estado, onNavigate, enDosColumnas)
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

/**
 * [enDosColumnas]: a la izquierda cómo le fue (la cabecera, de dónde salió lo que faltó y en qué se
 * fue), a la derecha lo que tenía que pasar (pagos fijos, presupuestos) y los gastos más grandes.
 * Sin él, todo en una columna, en ese mismo orden, como siempre.
 */
@Composable
private fun DetalleCargado(
    detalle: DetalleDePeriodo,
    estado: EstadoDelDetalleDePeriodo,
    onNavigate: (Screen) -> Unit,
    enDosColumnas: Boolean,
) {
    if (enDosColumnas) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.amplio),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f).testTag(TAG_COLUMNA_IZQUIERDA_DEL_DETALLE),
                verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            ) { SeccionesDeComoTeFue(detalle, estado) }
            Column(
                modifier = Modifier.weight(1f).testTag(TAG_COLUMNA_DERECHA_DEL_DETALLE),
                verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            ) { SeccionesDeLoQueTeniaQuePasar(detalle, estado) }
        }
    } else {
        SeccionesDeComoTeFue(detalle, estado)
        SeccionesDeLoQueTeniaQuePasar(detalle, estado)
    }
    AccionesDelDetalle(detalle, estado, onNavigate)
}

@Composable
private fun SeccionesDeComoTeFue(detalle: DetalleDePeriodo, estado: EstadoDelDetalleDePeriodo) {
    Cabecera(detalle, estado.ajustes)
    DeDondeSalioLoQueFalto(detalle)
    EnQueSeFue(detalle)
}

@Composable
private fun SeccionesDeLoQueTeniaQuePasar(detalle: DetalleDePeriodo, estado: EstadoDelDetalleDePeriodo) {
    PagosFijos(
        pagos = detalle.pagosFijos,
        enCurso = detalle.resumen.enCurso,
        hoy = estado.hoyDeLasFilas,
        onAgregar = { estado.nuevoPagoFijoAbierto = true },
    )
    if (detalle.presupuestos.isNotEmpty()) Presupuestos(detalle.presupuestos)
    // Sin gastos no hay «más grandes»: el vacío de «En qué se fue» ya lo dice, y dos tarjetas
    // vacías seguidas contarían lo mismo dos veces.
    if (detalle.masGrandes.isNotEmpty()) {
        LosMasGrandes(detalle.masGrandes, onAbrir = { estado.movimientoAbierto = it })
    }
}

/** Las acciones que cierran el detalle, siempre a lo ancho y abajo de todo. */
@Composable
private fun AccionesDelDetalle(
    detalle: DetalleDePeriodo,
    estado: EstadoDelDetalleDePeriodo,
    onNavigate: (Screen) -> Unit,
) {

    FilaDeAccion(
        texto = "Ver los movimientos de este período",
        modifier = Modifier.testTag(TAG_VER_MOVIMIENTOS_DEL_PERIODO),
        onClick = { onNavigate(Screen.Transactions(periodoInicial = detalle.resumen.id)) },
    )

    val periodo = estado.periodoEnCurso
    val hoyDeLaConfirmacion = estado.hoyDeLaConfirmacion
    if (periodo != null && estado.empezarHoy != null) {
        if (estado.confirmando && hoyDeLaConfirmacion != null) {
            ConfirmarEmpezarHoy(periodo, hoyDeLaConfirmacion, estado)
        } else {
            FilaDeAccion(
                texto = "Empezar un período nuevo hoy",
                modifier = Modifier.testTag(TAG_EMPEZAR_PERIODO_HOY),
                onClick = { estado.abrirConfirmacion() },
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
        LineaDeCreditosDesembolsados(resumen)
        lineaDeTuPlata(detalle)?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

// ── De dónde salió lo que faltó ──────────────────────────────────────────────

/**
 * Una fila por cada fuente de plata que no es ingreso, **solo si el período salió más de lo que
 * entró** — «Saldos que ya tenías y cargaste en el período · $1,5M — Nu, AFC Davibank». El monto de
 * la fila no pasa de lo que faltó (con $22,2M de saldos y un faltante de $1,5M dice $1,5M); las
 * cuentas se listan enteras. Vacía cuando entró lo
 * mismo o más (no faltó nada que explicar), sin fuentes, o con fuentes que esta versión no conoce o
 * que vienen en cero: la tarjeta no dice «$0» ni nombra un tipo que no sabe leer. Los créditos ya no
 * son una fila: el desembolso suma en «Entró» y se dice bajo el encabezado.
 *
 * Existe porque «Te quedó −$11,5M» se lee como si se hubiera gastado de más, cuando lo que faltó lo
 * pagó un saldo que ya estaba en las cuentas. Las cifras de arriba no cambian: esto
 * solo cuenta de dónde salió la diferencia (ver [DetalleDePeriodo.fuentesQueNoSonIngreso]).
 */
internal fun filasDeLoQueFalto(detalle: DetalleDePeriodo): List<String> {
    val faltante = detalle.resumen.salidas - detalle.resumen.entradas
    if (faltante <= 0) return emptyList()
    // Lo que se dice haber cubierto no pasa de lo que faltó (ver [loQueCubrioLoDemas]): las cuentas
    // se listan enteras, pero el total de cada fila se topa con lo que quede por explicar.
    var porExplicar = faltante
    return detalle.fuentesQueNoSonIngreso.mapNotNull { fuente ->
        if (fuente.monto <= 0) return@mapNotNull null
        val rotulo = when (fuente.tipo) {
            FUENTE_SALDO_INICIAL -> "Saldos que ya tenías y cargaste en el período"
            else -> return@mapNotNull null
        }
        if (porExplicar <= 0) return@mapNotNull null
        val cubierto = minOf(fuente.monto, porExplicar)
        porExplicar -= cubierto
        val base = "$rotulo · ${formatMoneyCompact(cubierto)}"
        if (fuente.detalle.isEmpty()) base else "$base — ${fuente.detalle.joinToString(", ")}"
    }
}

/**
 * La tarjeta de [filasDeLoQueFalto], justo debajo de las cifras que explica. Solo aparece con el
 * detalle ya cargado y no reserva lugar antes: el esqueleto no la anticipa, porque casi ningún
 * período la tiene.
 */
@Composable
private fun DeDondeSalioLoQueFalto(detalle: DetalleDePeriodo) {
    val filas = filasDeLoQueFalto(detalle)
    if (filas.isEmpty()) return
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_DE_DONDE_SALIO_LO_QUE_FALTO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Text(
            text = "De dónde salió lo que faltó",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            fontWeight = FontWeight.Medium,
        )
        filas.forEach { fila ->
            Spacer(Modifier.height(Movi.espacios.corto))
            Text(text = fila, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Esta plata ya estaba en tus cuentas cuando Movi la conoció, así que no cuenta como " +
                "ingreso.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
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

/*
 * Los pagos fijos del período se dibujan con las MISMAS piezas que la lista de Plan
 * ([GrupoDePagos], [FilaDePagoDelPeriodo]) y con los mismos títulos: «Falta por pagar» / «Ya
 * pagaste», o «No se pagó» cuando el período ya cerró. Antes decían «Pagos fijos · 3 de 5» con un
 * «✓», «pendiente» o «con dudas» al lado del monto: una tercera forma de contar lo mismo, que es
 * justo lo que la ola «una sola lista» vino a sacar (ver `ChecklistDelPeriodo.kt`).
 *
 * Cada fila se convierte a un [PagoDelPeriodo] para que la fecha y la evidencia salgan de las
 * mismas funciones que en Plan ([fechaDeLaFila], [evidenciaDeLaFila]): si una se ajusta, se
 * ajustan las dos pantallas.
 */

/** Una fila del detalle como la entiende la lista de Plan. [hoy] es para contar días al vencimiento. */
internal fun comoPagoDelPeriodo(pago: PagoFijoDelPeriodo, hoy: LocalDate?): PagoDelPeriodo {
    val vence = runCatching { LocalDate.parse(pago.vencimiento) }.getOrNull()
    return PagoDelPeriodo(
        ruleId = pago.ruleId,
        nombre = pago.nombre,
        monto = pago.monto,
        pagado = pago.estado == PAGO_FIJO_LISTO,
        diasParaVencer = if (vence != null && hoy != null) hoy.daysUntil(vence) else 0,
        vence = pago.vencimiento,
        esIngreso = pago.esIngreso,
        eventId = pago.eventId,
        montoPagado = pago.montoReal,
    )
}

/**
 * Los grupos del detalle: lo que falta (o no se pagó), lo pagado, y lo mismo para los ingresos.
 * Lo que tiene dudas va con lo que falta —todavía no se sabe que se pagó—, diciendo por qué.
 */
internal data class GruposDePagosFijos(
    val faltan: List<PagoFijoDelPeriodo>,
    val pagados: List<PagoFijoDelPeriodo>,
    val porCobrar: List<PagoFijoDelPeriodo>,
    val recibidos: List<PagoFijoDelPeriodo>,
)

internal fun gruposDePagosFijos(pagos: List<PagoFijoDelPeriodo>): GruposDePagosFijos {
    val (listos, abiertos) = pagos.sortedBy { it.vencimiento }.partition { it.estado == PAGO_FIJO_LISTO }
    return GruposDePagosFijos(
        faltan = abiertos.filter { !it.esIngreso },
        pagados = listos.filter { !it.esIngreso },
        porCobrar = abiertos.filter { it.esIngreso },
        recibidos = listos.filter { it.esIngreso },
    )
}

/** El título de lo abierto: en el período en curso todavía falta; en uno cerrado, no se pagó. */
internal fun tituloDeLoQueFalta(enCurso: Boolean, ingresos: Boolean): String = when {
    ingresos -> if (enCurso) TITULO_POR_COBRAR else TITULO_NO_LLEGO
    else -> if (enCurso) TITULO_FALTA_POR_PAGAR else TITULO_NO_SE_PAGO
}

/** Lo que la regla dice que se paga (sin lo pagado), en pesos. */
private fun totalDeLaRegla(pagos: List<PagoFijoDelPeriodo>): Long = pagos.sumOf { it.monto }

/** Lo que de verdad se movió cuando se sabe, si no lo que dice la regla. */
private fun totalMovido(pagos: List<PagoFijoDelPeriodo>): Long = pagos.sumOf { it.montoReal ?: it.monto }

/**
 * La fecha de una fila del detalle. En el período en curso, la misma de Plan («venció hace 2
 * días»); en uno cerrado lo abierto dice el día («venció el 22 de agosto»): contar días hacia atrás
 * desde hoy sobre un período que ya terminó no ayuda a nada.
 */
internal fun fechaDePagoFijo(pago: PagoFijoDelPeriodo, enCurso: Boolean, hoy: LocalDate?): String {
    val fila = comoPagoDelPeriodo(pago, hoy)
    if (enCurso || fila.pagado) return fechaDeLaFila(fila)
    val dia = fechaLegibleDelChecklist(pago.vencimiento)
    if (dia.isEmpty()) return ""
    return if (pago.esIngreso) "se esperaba el $dia" else "venció el $dia"
}

/**
 * La evidencia de una fila del detalle: con qué se sabe que se pagó ([evidenciaDeLaFila], la misma
 * de Plan), o por qué Movi no lo da por pagado cuando tiene dudas. Si lo que se movió difiere de lo
 * que dice la regla, lo dice: el monto de la derecha es el real.
 */
internal fun evidenciaDePagoFijo(pago: PagoFijoDelPeriodo): String? = when (pago.estado) {
    PAGO_FIJO_LISTO -> {
        val base = evidenciaDeLaFila(comoPagoDelPeriodo(pago, null))
        val real = pago.montoReal
        if (base != null && real != null && real != pago.monto) "$base · la regla dice ${formatCOP(pago.monto)}"
        else base
    }
    PAGO_FIJO_CON_DUDAS -> TEXTO_CON_DUDAS
    else -> null
}

/** Lo que dice una fila con dudas en el detalle, donde no hay propuesta que mostrar. */
const val TEXTO_CON_DUDAS = "Con dudas: Movi encontró más de un movimiento que podría ser este."

@Composable
private fun PagosFijos(pagos: List<PagoFijoDelPeriodo>, enCurso: Boolean, hoy: LocalDate?, onAgregar: () -> Unit) {
    Column {
        MinSectionHeader(title = TITULO_CHECKLIST_DEL_PERIODO, count = pagos.size.takeIf { it > 0 })
        if (pagos.isEmpty()) {
            VacioQueEnsena(
                titulo = "Sin pagos fijos en este período",
                detalle = "Arriendo, colegio, cuotas, suscripciones: anótalos una vez y cada período Movi " +
                    "te dice cuáles faltan y los marca solos cuando salen.",
                accion = "Agregar un pago fijo",
                onAccion = onAgregar,
            )
            return@Column
        }
        val grupos = gruposDePagosFijos(pagos)
        val fila: @Composable (PagoFijoDelPeriodo) -> Unit = { FilaDePagoFijo(it, enCurso, hoy) }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (grupos.faltan.isNotEmpty() || grupos.pagados.isNotEmpty()) {
                ResumenDeLaLista(
                    izquierda = tituloDeLoQueFalta(enCurso, ingresos = false) to totalDeLaRegla(grupos.faltan),
                    derecha = TITULO_YA_PAGASTE to totalMovido(grupos.pagados),
                )
            }
            listOf(
                Triple(tituloDeLoQueFalta(enCurso, false), grupos.faltan, false),
                Triple(TITULO_YA_PAGASTE, grupos.pagados, true),
                Triple(tituloDeLoQueFalta(enCurso, true), grupos.porCobrar, false),
                Triple(TITULO_YA_RECIBISTE, grupos.recibidos, true),
            ).filter { it.second.isNotEmpty() }.forEach { (titulo, filas, listos) ->
                Spacer(Modifier.height(Movi.espacios.amplio))
                GrupoDePagos(
                    titulo = titulo,
                    icono = when {
                        listos -> Icons.Rounded.CheckCircle
                        enCurso -> Icons.Rounded.Schedule
                        else -> Icons.Rounded.HighlightOff
                    },
                    colorDelIcono = when {
                        listos -> Movi.colores.entra
                        enCurso -> Movi.colores.textoMedio
                        else -> Movi.colores.sale
                    },
                    total = if (listos) totalMovido(filas) else totalDeLaRegla(filas),
                    filas = filas,
                    fila = fila,
                )
            }
        }
    }
}

@Composable
private fun FilaDePagoFijo(pago: PagoFijoDelPeriodo, enCurso: Boolean, hoy: LocalDate?) {
    val comoFila = comoPagoDelPeriodo(pago, hoy)
    FilaDePagoDelPeriodo(
        estado = when {
            comoFila.pagado -> EstadoVisualDelPago.PAGADO
            !enCurso -> EstadoVisualDelPago.NO_SE_PAGO
            comoFila.vencido -> EstadoVisualDelPago.VENCIDO
            else -> EstadoVisualDelPago.FALTA
        },
        esIngreso = pago.esIngreso,
        nombre = pago.nombre,
        fecha = fechaDePagoFijo(pago, enCurso, hoy),
        monto = (if (pago.esIngreso) "+" else "") + textoDelMontoDelChecklist(comoFila),
        colorDelMonto = when {
            comoFila.pagado -> Movi.colores.textoMedio
            pago.esIngreso -> Movi.colores.entra
            else -> Movi.colores.texto
        },
        evidencia = evidenciaDePagoFijo(pago),
    )
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
