package com.jvillada.movi.ui.periodos

import com.jvillada.movi.ui.transactions.tituloDelMovimiento
import com.jvillada.movi.shared.model.PequenosQueSuman
import com.jvillada.movi.shared.model.GASTO_CHICO_HASTA
import com.jvillada.movi.shared.model.VECES_PARA_SUMAR
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
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.shared.model.EstadoDePresupuesto
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.recurrentes.BajoLaFila
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import androidx.compose.material.icons.rounded.Tune
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
import com.jvillada.movi.ui.recurrentes.TITULO_GRUPO_POR_PAGAR
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
                    .padding(horizontal = Medida.margenDePantalla)
                    .padding(top = Espacio.xs, bottom = Medida.botonFlotante + Espacio.xl),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
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
            horizontalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f).testTag(TAG_COLUMNA_IZQUIERDA_DEL_DETALLE),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            ) { SeccionesDeComoTeFue(detalle, estado) }
            Column(
                modifier = Modifier.weight(1f).testTag(TAG_COLUMNA_DERECHA_DEL_DETALLE),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            ) { SeccionesDeLoQueTeniaQuePasar(detalle, estado, onNavigate) }
        }
    } else {
        SeccionesDeComoTeFue(detalle, estado)
        SeccionesDeLoQueTeniaQuePasar(detalle, estado, onNavigate)
    }
    AccionesDelDetalle(detalle, estado, onNavigate)
}

@Composable
private fun SeccionesDeComoTeFue(detalle: DetalleDePeriodo, estado: EstadoDelDetalleDePeriodo) {
    Cabecera(detalle, estado.ajustes)
    DeDondeSalioLoQueFalto(detalle)
    EnQueSeFue(detalle)
    // Solo si algún grupo suma algo que importe (el server ya lo decidió: ver `pequenosQueSuman`).
    if (detalle.pequenosQueSuman.isNotEmpty()) PequenosQueSumanSeccion(detalle.pequenosQueSuman)
}

@Composable
private fun SeccionesDeLoQueTeniaQuePasar(
    detalle: DetalleDePeriodo,
    estado: EstadoDelDetalleDePeriodo,
    onNavigate: (Screen) -> Unit,
) {
    // **El período en curso no repite Plan** (ola R4, «cada cifra tiene una casa»): sus pagos fijos y
    // sus presupuestos son los de Plan, que es dueño de «¿qué me falta pagar?». Acá van dos filas que
    // llevan allá. Un período cerrado sí los pinta: mirar atrás es lo que contesta esta pantalla.
    if (detalle.resumen.enCurso) {
        LoQueVaEnPlan(onNavigate)
    } else {
        PagosFijos(
            pagos = detalle.pagosFijos,
            enCurso = false,
            hoy = estado.hoyDeLasFilas,
            onAgregar = { estado.nuevoPagoFijoAbierto = true },
        )
        if (detalle.presupuestos.isNotEmpty()) Presupuestos(detalle.presupuestos)
    }
    // Sin gastos no hay «más grandes»: el vacío de «En qué se fue» ya lo dice, y dos tarjetas
    // vacías seguidas contarían lo mismo dos veces.
    if (detalle.masGrandes.isNotEmpty()) {
        LosMasGrandes(detalle.masGrandes, onAbrir = { estado.movimientoAbierto = it })
    }
}

/** El tag de la fila que lleva a Plan desde el detalle del período en curso. */
const val TAG_PAGOS_DEL_PERIODO_EN_PLAN: String = "detalle-pagos-del-periodo-en-plan"

/** El tag de la fila que lleva a Plan · Presupuestos desde el detalle del período en curso. */
const val TAG_PRESUPUESTOS_EN_PLAN: String = "detalle-presupuestos-en-plan"

/** El título de la sección que lleva a Plan desde el período en curso. */
const val TITULO_ESTE_PERIODO_EN_PLAN: String = "Este período, en Plan"

/** Las dos filas que llevan a Plan desde el período en curso (ver [SeccionesDeLoQueTeniaQuePasar]). */
@Composable
private fun LoQueVaEnPlan(onNavigate: (Screen) -> Unit) {
    Column {
        Seccion(titulo = TITULO_ESTE_PERIODO_EN_PLAN)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Fila(
                titulo = TITULO_CHECKLIST_DEL_PERIODO,
                subtitulo = "Lo que falta y lo que ya pagaste",
                inicio = { IconoEnCirculo(Icons.Rounded.Schedule) },
                final = { Chevron() },
                onClick = { onNavigate(Screen.Plan(SEGMENTO_PAGOS)) },
                modifier = Modifier.testTag(TAG_PAGOS_DEL_PERIODO_EN_PLAN),
            )
            Separador(SangriaDeSeparador.ConInicio)
            Fila(
                titulo = "Presupuestos",
                subtitulo = "Cuánto llevas de cada tope",
                inicio = { IconoEnCirculo(Icons.Rounded.Tune) },
                final = { Chevron() },
                onClick = { onNavigate(Screen.Plan(SEGMENTO_PRESUPUESTOS)) },
                modifier = Modifier.testTag(TAG_PRESUPUESTOS_EN_PLAN),
            )
        }
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
    Superficie(modifier = Modifier.fillMaxWidth()) {
        rangoDelResumen(resumen)?.let {
            Text(text = it, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        }
        fraseDelInicioPropio(resumen, ajustes)?.let {
            Spacer(Modifier.height(Espacio.xxs))
            Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        if (resumen.enCurso) {
            Spacer(Modifier.height(Espacio.xs))
            Pildora("En curso", TonoDePildora.Aviso)
        }
        CifrasDelPeriodo(resumen)
        LineaDeCreditosDesembolsados(resumen)
        lineaDeTuPlata(detalle)?.let {
            Spacer(Modifier.height(Espacio.xs))
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
 * Existe porque «Neto del período −$11,5M» (antes «Te quedó») se lee como si se hubiera gastado de más, cuando lo que faltó lo
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
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_DE_DONDE_SALIO_LO_QUE_FALTO)) {
        Text(
            text = "De dónde salió lo que faltó",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
        )
        filas.forEach { fila ->
            Spacer(Modifier.height(Movi.espacios.corto))
            Text(text = fila, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        }
        Spacer(Modifier.height(Espacio.xs))
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
        Seccion(titulo = "En qué se fue")
        if (categorias.isEmpty()) {
            VacioQueEnsena(
                titulo = "Sin gastos en este período",
                detalle = "Cuando haya gastos entre estas fechas, aquí vas a ver en qué categorías se fue tu plata.",
            )
        } else {
            Superficie(modifier = Modifier.fillMaxWidth()) {
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
 *
 * **Lo que tiene dudas depende de si el período sigue abierto.** En curso va con lo que falta —como
 * en Plan, donde la fila pregunta «¿fue este?»—. En un período cerrado NO puede ir bajo «No se
 * pagó»: Movi encontró movimientos que podrían ser el pago, así que afirmar que no se pagó sería
 * inventar. Va en [sinConfirmar], con su propio título.
 */
internal data class GruposDePagosFijos(
    val faltan: List<PagoFijoDelPeriodo>,
    val pagados: List<PagoFijoDelPeriodo>,
    val porCobrar: List<PagoFijoDelPeriodo>,
    val recibidos: List<PagoFijoDelPeriodo>,
    val sinConfirmar: List<PagoFijoDelPeriodo> = emptyList(),
)

internal fun gruposDePagosFijos(pagos: List<PagoFijoDelPeriodo>, enCurso: Boolean = true): GruposDePagosFijos {
    val (listos, abiertos) = pagos.sortedBy { it.vencimiento }.partition { it.estado == PAGO_FIJO_LISTO }
    val (dudosos, sinPago) =
        if (enCurso) emptyList<PagoFijoDelPeriodo>() to abiertos
        else abiertos.partition { it.estado == PAGO_FIJO_CON_DUDAS }
    return GruposDePagosFijos(
        faltan = sinPago.filter { !it.esIngreso },
        pagados = listos.filter { !it.esIngreso },
        porCobrar = sinPago.filter { it.esIngreso },
        recibidos = listos.filter { it.esIngreso },
        sinConfirmar = dudosos,
    )
}

/** El título de lo que, en un período cerrado, Movi no pudo dar ni por pagado ni por no pagado. */
const val TITULO_SIN_CONFIRMAR = "Sin confirmar"

/** El título de lo abierto: en el período en curso todavía falta; en uno cerrado, no se pagó. */
internal fun tituloDeLoQueFalta(enCurso: Boolean, ingresos: Boolean): String = when {
    ingresos -> if (enCurso) TITULO_POR_COBRAR else TITULO_NO_LLEGO
    else -> if (enCurso) TITULO_FALTA_POR_PAGAR else TITULO_NO_SE_PAGO
}

/**
 * El título del GRUPO de lo abierto, debajo del resumen. En el período en curso el resumen ya dice
 * «Falta por pagar $X», así que el grupo es «Por pagar» (como en Plan); lo demás no cambia.
 */
internal fun tituloDelGrupoQueFalta(enCurso: Boolean, ingresos: Boolean): String =
    if (enCurso && !ingresos) TITULO_GRUPO_POR_PAGAR else tituloDeLoQueFalta(enCurso, ingresos)

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
        Seccion(titulo = TITULO_CHECKLIST_DEL_PERIODO, conteo = pagos.size.takeIf { it > 0 })
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
        val grupos = gruposDePagosFijos(pagos, enCurso)
        val fila: @Composable (PagoFijoDelPeriodo) -> Unit = { FilaDePagoFijo(it, enCurso, hoy) }
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            if (grupos.faltan.isNotEmpty() || grupos.pagados.isNotEmpty()) {
                ResumenDeLaLista(
                    izquierda = tituloDeLoQueFalta(enCurso, ingresos = false) to totalDeLaRegla(grupos.faltan),
                    derecha = TITULO_YA_PAGASTE to totalMovido(grupos.pagados),
                )
            }
            // (título, filas, ya están listas) y si el grupo lleva total. Lo que falta (o no se
            // pagó) y lo pagado tienen su cifra en el resumen de arriba, y el grupo no la repite; los
            // ingresos sí la llevan, que el resumen no los suma.
            listOf(
                Triple(tituloDelGrupoQueFalta(enCurso, false), grupos.faltan, false) to false,
                Triple(TITULO_YA_PAGASTE, grupos.pagados, true) to false,
                Triple(tituloDeLoQueFalta(enCurso, true), grupos.porCobrar, false) to true,
                Triple(TITULO_YA_RECIBISTE, grupos.recibidos, true) to true,
            ).filter { it.first.second.isNotEmpty() }.forEach { (grupo, conTotal) ->
                val (titulo, filas, listos) = grupo
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
                    total = when {
                        !conTotal -> null
                        listos -> totalMovido(filas)
                        else -> totalDeLaRegla(filas)
                    },
                    filas = filas,
                    fila = fila,
                )
            }
            if (grupos.sinConfirmar.isNotEmpty()) {
                GrupoDePagos(
                    titulo = TITULO_SIN_CONFIRMAR,
                    icono = Icons.AutoMirrored.Rounded.HelpOutline,
                    colorDelIcono = Movi.colores.aviso,
                    // Sin total: no se sabe si es plata que salió o que faltó.
                    total = null,
                    filas = grupos.sinConfirmar,
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
            // Con dudas no se afirma nada: ni pagado ni «no se pagó».
            pago.estado == PAGO_FIJO_CON_DUDAS && !enCurso -> EstadoVisualDelPago.FALTA
            !enCurso -> EstadoVisualDelPago.NO_SE_PAGO
            comoFila.vencido -> EstadoVisualDelPago.VENCIDO
            else -> EstadoVisualDelPago.FALTA
        },
        esIngreso = pago.esIngreso,
        nombre = pago.nombre,
        fecha = fechaDePagoFijo(pago, enCurso, hoy),
        // Sin «+» en el ingreso y rojo en lo vencido: igual que la fila de Plan.
        monto = textoDelMontoDelChecklist(comoFila),
        colorDelMonto = when {
            comoFila.pagado -> Movi.colores.textoMedio
            pago.esIngreso -> Movi.colores.entra
            comoFila.vencido && pago.estado != PAGO_FIJO_CON_DUDAS -> Movi.colores.sale
            else -> Movi.colores.texto
        },
        evidencia = evidenciaDePagoFijo(pago),
        pildora = when {
            comoFila.pagado -> (if (pago.esIngreso) "Recibido" else "Pagado") to TonoDePildora.Exito
            pago.estado == PAGO_FIJO_CON_DUDAS -> "Sin confirmar" to TonoDePildora.Aviso
            !enCurso -> (if (pago.esIngreso) "No llegó" else "No se pagó") to TonoDePildora.Peligro
            comoFila.vencido -> "Vencido" to TonoDePildora.Peligro
            else -> "Falta" to TonoDePildora.Neutra
        },
    )
}

// ── Presupuestos ─────────────────────────────────────────────────────────────

@Composable
private fun Presupuestos(presupuestos: List<PresupuestoDelPeriodo>) {
    Column {
        Seccion(titulo = "Presupuestos")
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            presupuestos.forEachIndexed { i, p ->
                FilaDePresupuesto(p)
                if (i < presupuestos.lastIndex) Separador(SangriaDeSeparador.ConInicio)
            }
        }
    }
}

/**
 * Un presupuesto de un período cerrado: la misma fila de Presupuestos (ícono, nombre, «$X de $Y», y
 * la barra con el semáforo colgando), sin toque — ya pasó.
 */
@Composable
private fun FilaDePresupuesto(p: PresupuestoDelPeriodo) {
    val estadoDelTope = estadoDePresupuesto(p.gastado, p.limite)
    val fraccion = if (p.limite > 0) p.gastado.toFloat() / p.limite else 0f
    Fila(
        titulo = p.category,
        subtitulo = "${formatCOP(p.gastado)} de ${formatCOP(p.limite)}",
        inicio = { IconoDeCategoria(p.category) },
    )
    BajoLaFila {
        BarraDeProgreso(
            fraccion = fraccion,
            tono = when (estadoDelTope) {
                EstadoDePresupuesto.EXCEDIDO_MUCHO -> TonoDeBarra.Peligro
                EstadoDePresupuesto.DENTRO -> TonoDeBarra.Exito
                else -> TonoDeBarra.Aviso
            },
        )
    }
}


/** El título de la sección de los gastos hormiga. */
const val TITULO_PEQUENOS_QUE_SUMAN: String = "Pequeños que suman"

/** El tag de una fila de «Pequeños que suman», para las pruebas. */
const val TAG_PEQUENO_QUE_SUMA: String = "pequeno-que-suma"

/**
 * El renglón de un grupo: «27 compras en Juan Valdez» o «12 gastos chicos de Comida». El total va
 * aparte, a la derecha, como en el resto de las listas de plata.
 */
internal fun textoDePequenos(p: PequenosQueSuman): String =
    if (p.porComercio) "${p.veces} compras en ${p.nombre}" else "${p.veces} gastos chicos de ${p.nombre}"

/** Lo que la sección explica debajo del título: qué es «chico». */
internal val PIE_DE_PEQUENOS_QUE_SUMAN: String =
    "Gastos de hasta ${formatCOP(GASTO_CHICO_HASTA)} que se repiten ${VECES_PARA_SUMAR} veces o más."

/**
 * **Los gastos hormiga del período**: los chicos que se repiten, por comercio o por categoría, con
 * cuántas veces y cuánto suman. Los arma el server ([DetalleDePeriodo.pequenosQueSuman]); acá solo se
 * pintan.
 */
@Composable
private fun PequenosQueSumanSeccion(grupos: List<PequenosQueSuman>) {
    Column {
        Seccion(titulo = TITULO_PEQUENOS_QUE_SUMAN)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            grupos.forEach { grupo ->
                Fila(
                    titulo = textoDePequenos(grupo),
                    final = { Cifra(grupo.total) },
                    modifier = Modifier.testTag(TAG_PEQUENO_QUE_SUMA),
                )
            }
            Text(
                text = PIE_DE_PEQUENOS_QUE_SUMAN,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
                modifier = Modifier.padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
            )
        }
    }
}

// ── Los más grandes ──────────────────────────────────────────────────────────

@Composable
private fun LosMasGrandes(eventos: List<FinancialEvent>, onAbrir: (FinancialEvent) -> Unit) {
    Column {
        Seccion(titulo = "Los más grandes")
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            eventos.forEach { evento ->
                Fila(
                    titulo = tituloDelMovimiento(evento),
                    subtitulo = diaLegible(epochMillisToAppDate(evento.timestamp)),
                    inicio = { IconoDeCategoria(evento.category) },
                    final = { Cifra(evento.amount) },
                    onClick = { onAbrir(evento) },
                    modifier = Modifier.testTag(TAG_GASTO_GRANDE),
                )
            }
        }
    }
}

// ── Acciones ─────────────────────────────────────────────────────────────────

@Composable
private fun FilaDeAccion(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Superficie(modifier = modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(titulo = texto, final = { Chevron() }, onClick = onClick)
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
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = textoDeEmpezarHoy(periodo, hoy),
            style = Movi.textos.cuerpoRegular,
            color = Movi.colores.texto,
        )
        estado.errorAlGuardar?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(Espacio.s))
        Boton(
            texto = if (estado.guardando) "Guardando…" else "Empezar ${mesDe(periodoSiguiente(periodo))} hoy",
            onClick = { estado.confirmarEmpezarHoy() },
            modifier = Modifier.testTag(TAG_CONFIRMAR_PERIODO_HOY),
            habilitado = !estado.guardando,
            anchoCompleto = true,
        )
        Spacer(Modifier.height(Espacio.xs))
        Boton(
            texto = "Cancelar",
            onClick = { estado.confirmando = false },
            variante = VarianteDeBoton.Fantasma,
            habilitado = !estado.guardando,
            anchoCompleto = true,
        )
    }
}

/** La forma del detalle mientras no llegó: la cabecera y una sección con tres categorías. */
@Composable
private fun DetalleEsqueleto() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_DETALLE_DE_PERIODO_ESQUELETO),
        verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
    ) {
        Superficie(modifier = Modifier.fillMaxWidth()) {
            LineaEsqueleto(fraccionDelAncho = 0.65f, estilo = Movi.textos.cuerpo)
            CifrasDelPeriodoEsqueleto()
        }
        Column {
            LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.titulo)
            Spacer(Modifier.height(Movi.espacios.corto))
            Superficie(modifier = Modifier.fillMaxWidth()) {
                repeat(3) { i ->
                    LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.cuerpo)
                    Spacer(Modifier.height(Espacio.xs))
                    BloqueEsqueleto(alto = Medida.altoDeBarraDeProgreso)
                    if (i < 2) Spacer(Modifier.height(Movi.espacios.medio))
                }
            }
        }
    }
}
