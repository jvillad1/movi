package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.Vacio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import com.jvillada.movi.ui.destinos.FilaGuardarElDestino
import com.jvillada.movi.ui.destinos.nombreParaLaFila
import com.jvillada.movi.ui.destinos.rememberDestinosParaGuardar
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.NoSePudoActualizar
import com.jvillada.movi.ui.components.ActualizandoEnLaCabecera
import com.jvillada.movi.ui.components.RotuloDeSeccionEsqueleto
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.Celebracion
import com.jvillada.movi.ui.components.MomentoDeCelebracion
import com.jvillada.movi.ui.components.rememberVibrador
import com.jvillada.movi.ui.sms.TarjetaDeMensajeDelBanco
import com.jvillada.movi.ui.sms.TarjetaDelMismoPago
import com.jvillada.movi.ui.sms.avisosDelPago
import com.jvillada.movi.ui.sms.conLoResueltoEnLaBandeja
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.data.intentar
import com.jvillada.movi.ui.components.toUserMessage
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import com.jvillada.movi.shared.model.soloLoQueLlegoSolo
import com.jvillada.movi.ui.extractos.TiposDeArchivo
import com.jvillada.movi.ui.extractos.rememberFilePicker
import com.jvillada.movi.ui.papeles.ArchivoCompartido
import com.jvillada.movi.ui.papeles.PapelesCompartidos
import com.jvillada.movi.ui.transactions.CardPaymentCandidatesSheet
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.MovementRow
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TransferRow

/** El título de la bandeja, y el rótulo con que la nombran los demás. */
const val TITULO_DE_POR_REVISAR: String = "Por revisar"

/** Lo que dice la bandeja cuando las tres lecturas contestaron y ninguna trajo nada. */
const val TODO_AL_DIA: String = "Todo al día"

/** El tag del renglón «N por revisar» de Movimientos. */
const val TAG_RENGLON_POR_REVISAR: String = "renglon-por-revisar"

/** El tag del esqueleto de la bandeja, para verificar que está mientras nada contestó. */
const val TAG_ESQUELETO_DE_POR_REVISAR: String = "esqueleto-de-por-revisar"

/** El tag del renglón «Subir comprobante o extracto» (Ola 2). */
const val TAG_SUBIR_PAPEL_EN_LA_BANDEJA: String = "subir-papel-en-la-bandeja"

/** El tag del renglón «la captura dejó de andar» de la bandeja. */
const val TAG_AVISO_DE_CAPTURA_EN_LA_BANDEJA: String = "aviso-de-captura-en-la-bandeja"

/**
 * **La propuesta de Movimientos que abre la bandeja**: «N por revisar», con la suma de las tres
 * fuentes (ver [cuantosPorRevisar]). Reemplaza a los avisos sueltos que había antes —uno por los
 * movimientos que entraron solos, otro por los pagos de tarjeta—. Ola R4: es una
 * [TarjetaDePropuesta], la misma forma que «Movi propone hoy» del Inicio, porque es lo mismo: algo
 * que llegó solo y espera que el dueño decida.
 */
@Composable
fun RenglonPorRevisar(cuantos: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TarjetaDePropuesta(
        icono = Icons.Rounded.Inbox,
        titulo = textoDePorRevisar(cuantos),
        cuerpo = "Avisos y movimientos por confirmar",
        accion = "Revisar",
        onAccion = onClick,
        modifier = modifier.testTag(TAG_RENGLON_POR_REVISAR),
    )
}

/**
 * **«Por revisar»** — ola C, tarea 5: una sola bandeja para lo que entró solo y espera que el dueño
 * decida. Tres secciones, cada una con su propia acción de siempre:
 *
 * 1. **Mensajes del banco** por confirmar: la misma tarjeta que el historial, y «Revisar» abre
 *    [Screen.SMSReconcile] como siempre.
 * 2. **Entraron solos**: los movimientos sin confirmar, con el mismo renglón de Movimientos; tocarlo
 *    abre [HojaDelMovimiento], que es donde vive «Confirmar».
 * 3. **Pagos de tarjeta** sin marcar: abre [CardPaymentCandidatesSheet], uno por uno.
 *
 * Arriba de todo, solo si la captura dejó de andar —la misma condición que el aviso del Hoy, ver
 * [avisoDeCapturaEnLaBandeja]— un renglón que lleva a «Captura del banco». La configuración de la
 * captura no vive acá: esto es una bandeja, no un panel de ajustes.
 *
 * **Nada se afirma antes de leer.** Mientras las lecturas no contestaron hay un esqueleto; una
 * lectura caída dice «No pudimos cargar…» con su «Reintentar» en el lugar de su sección; y
 * [TODO_AL_DIA] sale solo cuando las tres contestaron bien y vacías (ver [bandejaAlDia]).
 */
@Composable
fun PorRevisarScreen(onNavigate: (Screen) -> Unit) {
    var recarga by remember { mutableStateOf(0) }
    // **Lo último que se vio, al primer cuadro** (ver [rememberLectura]): todo arranca con lo que
    // dejó la visita anterior y se relee igual, con «Actualizar» y con `LocalRefreshTick`.
    val lecturas = rememberLecturasPorRevisar(recarga)

    /**
     * La historia: la MISMA entrada que Movimientos y Presupuestos, una lectura y no tres. `null`
     * hasta que contesta bien; un reintento que falla conserva lo último.
     */
    val eventos = rememberLectura(ClaveDeLectura.EventosPorDia, recarga) { Repositories.wallets.getEventsByDay() }
    val diasLeidos = eventos.valor
    val leyendoDias = eventos.actualizando
    // Secundarias: sin las cuentas los renglones dicen solo la categoría, y sin el perfil no se
    // sabe si el dueño silenció el aviso de la captura, así que ese renglón no se pinta.
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, recarga) { Repositories.wallets.getAccounts() }.valor.orEmpty()
    val capturaSilenciada = rememberLectura(ClaveDeLectura.Perfil, recarga) { Repositories.wallets.getUserProfile() }
        .valor?.smsAlertMuted
    // Lo que se ve es lo último que vimos: una de las tres fuentes falló con lo de antes a la vista.
    val noSePudoActualizar = lecturas.falloConAlgoALaVista || eventos.falloConAlgoALaVista

    // Los candidatos que el dueño ya resolvió acá —«Marcar» o «No es»—. Se descuentan de la lista
    // porque el refetch puede fallar y dejarla vieja: sin esto, un pago recién resuelto volvía a
    // aparecer con sus botones activos, como si la acción no se hubiera guardado.
    var candidatosResueltos by remember { mutableStateOf(emptySet<String>()) }
    // Lo mismo para los movimientos que se confirmaron o anularon desde su hoja: la llave es el
    // traspaso cuando lo hay, porque confirmar una pata confirma el par (y el renglón es uno).
    var movimientosResueltos by remember { mutableStateOf(emptySet<String>()) }
    var viendoCandidatos by remember { mutableStateOf(false) }
    var movimientoAbierto by remember { mutableStateOf<FinancialEvent?>(null) }

    // Ola 2 · «Compartir con Movi» desde la bandeja: el archivo va a la misma cola que lo que se
    // comparte desde Android, y `App()` abre la hoja que lo lee.
    val elegirPapel = rememberFilePicker(TiposDeArchivo.EXTRACTOS) { nombre, bytes, mime ->
        PapelesCompartidos.recibir(listOf(ArchivoCompartido(nombre, bytes, mime)))
    }

    // «Un pago, una tarjeta»: lo que el dueño resolvió acá sobre un pago avisado varias veces
    // (ignorarlo, cerrarlo, «No son el mismo pago») se ve enseguida, sin esperar la relectura.
    var avisosResueltos by remember { mutableStateOf(emptyMap<String, String>()) }
    var avisosSeparados by remember { mutableStateOf(emptySet<String>()) }
    var pagoEnCurso by remember { mutableStateOf<String?>(null) }
    var errorDeUnPago by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    // Confirmar un aviso desde la fila (o el lote) vibra, como en Reconciliar (ver [Vibrador]).
    val vibrador = rememberVibrador()
    fun sobreElPago(grupoId: String, accion: suspend () -> Unit, alFallar: () -> Unit = {}, alTerminar: () -> Unit) {
        if (pagoEnCurso != null) return
        pagoEnCurso = grupoId
        errorDeUnPago = null
        alcance.launch {
            intentar { accion() }
                .onSuccess { alTerminar(); recarga++ }
                .onFailure { alFallar(); errorDeUnPago = it.toUserMessage() }
            pagoEnCurso = null
        }
    }

    // ── La bandeja que se vacía sola ─────────────────────────────────────────────────────────
    // Lo que Movi propone para cada pago pendiente (la misma propuesta y las mismas coincidencias de
    // Reconciliar), para saber cuáles se confirman desde la fila. Sin esta lectura, todo es «Revisar».
    val propuestas = rememberLectura(ClaveDeLectura.PropuestasDeLaBandeja, recarga) {
        Repositories.wallets.getPropuestasDeLaBandeja()
    }.valor
    // Lo que falló acá desde la fila o en el lote: vuelve a «Revisar» hasta que el dueño lo mire. Si
    // el movimiento alcanzó a crearse, la relectura lo trae como «¿Ya lo anotaste?»; mientras tanto,
    // un segundo «Confirmar» no puede crear otro.
    var fallaronAca by remember { mutableStateOf(emptySet<String>()) }
    var lote by remember { mutableStateOf<EstadoDelLote>(EstadoDelLote.Cerrado) }
    fun confirmarDesdeLaFila(anotacion: AnotacionEntendida) {
        sobreElPago(
            anotacion.cabezaId,
            { confirmarLoEntendido(anotacion, ahora = Clock.System.now().toEpochMilliseconds()) },
            alFallar = { fallaronAca = fallaronAca + anotacion.cabezaId },
        ) {
            vibrador.confirmar()
            avisosResueltos = avisosResueltos + anotacion.avisos.associateWith { SMS_STATE_CONFIRMED }
        }
    }

    val mensajes = lecturas.mensajes?.let { conLoResueltoEnLaBandeja(it, avisosResueltos, avisosSeparados) }
    /**
     * **A quién fue cada mensaje pendiente**, leído del texto con la misma función de `:core` que
     * usa el server para `ParsedSms.identificadorDelDestino` — la bandeja tiene el texto y no el
     * parseo, y pedir un parseo por tarjeta sería una ida al server por mensaje. Con esto la
     * tarjeta ofrece «¿De quién es la cuenta ·0756?» sin abrir «Revisar». Las cuentas guardadas se
     * leen solo si algún pendiente nombra una.
     */
    val aQuienFue = remember(mensajes) {
        mensajesPorRevisar(mensajes.orEmpty()).mapNotNull { sms ->
            identificadorDelDestinoEn(sms.text)?.let { sms.id to it }
        }.toMap()
    }
    val destinosParaGuardar = rememberDestinosParaGuardar(hacenFalta = aQuienFue.isNotEmpty())
    /** Cabeza de cada pago → lo que Movi va a anotar, para los pagos que entendió (ver [loQueMoviEntendio]). */
    val entendimientos: Map<String, LoQueMoviEntendio> = remember(mensajes, propuestas, cuentas, fallaronAca) {
        val todos = mensajes ?: return@remember emptyMap()
        val leidas = propuestas ?: return@remember emptyMap()
        val ahora = Clock.System.now().toEpochMilliseconds()
        pagosPorRevisar(todos)
            .filter { it.id !in fallaronAca }
            .associate { cabeza -> cabeza.id to loQueMoviEntendio(cabeza, todos, leidas, cuentas, ahora) }
    }
    val entendidos: Map<String, AnotacionEntendida> =
        entendimientos.mapNotNull { (id, e) -> e.anotacion?.let { id to it } }.toMap()
    /** Vuelta de diseño 23: la cuenta que propone Movi es de otro banco que el del aviso. */
    val advertencias: Map<String, String> =
        entendimientos.mapNotNull { (id, e) -> e.advertencia?.let { id to it } }.toMap()
    val dias = diasLeidos?.map { dia ->
        dia.copy(items = dia.items.filterNot { (it.transferId ?: it.id) in movimientosResueltos })
    }
    val candidatos = lecturas.candidatos?.filterNot { it.id in candidatosResueltos }
    // ── Lo que el banco cobró solo (débitos automáticos) ── bloque aparte: no son avisos del banco.
    // Lo confirmado o descartado acá se va enseguida, sin esperar la relectura.
    var debitosResueltos by remember { mutableStateOf(emptySet<String>()) }
    val debitos = lecturas.debitos?.filterNot { it.clave in debitosResueltos }
    val nombresDeCuentas = remember(cuentas) { cuentas.associate { it.id to it.name } }
    val tiposDeCuentas = remember(cuentas) { cuentas.associate { it.id to it.type } }

    // La primera carga: alguna de las tres fuentes todavía no dijo nada. Con lo de antes en
    // pantalla (un reintento) no se vuelve al esqueleto.
    val cargando = (mensajes == null && lecturas.leyendoMensajes) ||
        (dias == null && leyendoDias) ||
        (lecturas.candidatos == null && lecturas.leyendoCandidatos) ||
        (lecturas.debitos == null && lecturas.leyendoDebitos)
    // Con todo pintado, alguna lectura sigue en vuelo: lo pintado es lo de antes.
    val actualizandoConAlgoALaVista = !cargando &&
        (lecturas.leyendoMensajes || leyendoDias || lecturas.leyendoCandidatos || lecturas.leyendoDebitos)

    // ── El momento de celebrar (Ola 1 · «Que Movi se sienta viva») ──────────────────────────────
    // Cuando la bandeja queda vacía **por algo que el dueño hizo** —acá o en Reconciliar—, una vez,
    // en la transición (ver [Celebracion]). Lo último que se supo y la ida a Reconciliar sobreviven
    // a irse y volver (`rememberSaveable`: la pantalla queda guardada mientras está en la pila), así
    // que confirmar el último aviso allá y volver acá también celebra. Abrir la bandeja ya vacía, no.
    var ultimoEstadoDeLaBandeja by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var fueAReconciliar by rememberSaveable { mutableStateOf(false) }
    var celebrar by remember { mutableStateOf(false) }
    val huboAccion = fueAReconciliar || avisosResueltos.isNotEmpty() || avisosSeparados.isNotEmpty() ||
        candidatosResueltos.isNotEmpty() || movimientosResueltos.isNotEmpty() || debitosResueltos.isNotEmpty() ||
        lote !is EstadoDelLote.Cerrado
    val bandejaVacia: Boolean? = if (cargando || noSePudoActualizar) null else bandejaAlDia(mensajes, dias, candidatos, debitos)
    LaunchedEffect(bandejaVacia) {
        val celebracion = Celebracion(ultimoEstadoDeLaBandeja)
        if (celebracion.observar(bandejaVacia, huboAccion)) {
            celebrar = true
            fueAReconciliar = false
        }
        if (bandejaVacia == false) celebrar = false
        ultimoEstadoDeLaBandeja = celebracion.ultimoConocido
    }
    val revisarEnReconciliar: (String) -> Unit = { id ->
        fueAReconciliar = true
        onNavigate(Screen.SMSReconcile(id))
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = TITULO_DE_POR_REVISAR,
            leading = HeaderLeading.Back(fallback = Screen.Transactions()),
            action = {
                if (actualizandoConAlgoALaVista) ActualizandoEnLaCabecera()
                BotonDeIcono(Icons.Rounded.Refresh, "Actualizar", onClick = { recarga++ })
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Medida.margenDePantalla,
                end = Medida.margenDePantalla,
                top = Medida.dentroDeSeccion,
                bottom = Medida.botonFlotante + Medida.margenDePantalla,
            ),
        ) {
            if (cargando) {
                item { EsqueletoDePorRevisar() }
                return@LazyColumn
            }

            if (noSePudoActualizar) {
                item {
                    NoSePudoActualizar(onReintentar = { recarga++ }, modifier = Modifier.padding(bottom = Medida.entreSecciones))
                }
            }

            // La puerta para lo que no llega solo: una captura de una transferencia, un recibo, un
            // PDF del banco. Arriba y siempre, también con la bandeja al día.
            item {
                FilaQueLleva(
                    texto = "Subir comprobante o extracto",
                    detalle = "Movi lo lee y lo deja aquí",
                    icono = Icons.Rounded.UploadFile,
                    onClick = elegirPapel,
                    modifier = Modifier.padding(bottom = Medida.entreSecciones).testTag(TAG_SUBIR_PAPEL_EN_LA_BANDEJA),
                )
            }

            avisoDeCapturaEnLaBandeja(mensajes, capturaSilenciada)?.let { aviso ->
                item {
                    FilaQueLleva(
                        texto = aviso,
                        detalle = "Toca para ver cómo está la captura",
                        icono = Icons.Rounded.WarningAmber,
                        esAlerta = true,
                        onClick = { onNavigate(Screen.CapturaDelBanco) },
                        modifier = Modifier.padding(bottom = Medida.entreSecciones).testTag(TAG_AVISO_DE_CAPTURA_EN_LA_BANDEJA),
                    )
                }
            }

            // «Confirmar los N que Movi entendió»: arriba, con la lista de lo que va a anotar.
            val delLote = mensajes?.let { m -> pagosPorRevisar(m).mapNotNull { entendidos[it.id] } }.orEmpty()
            if (delLote.size >= MINIMO_PARA_EL_LOTE || lote !is EstadoDelLote.Cerrado) {
                item(key = "lote") {
                    TarjetaDelLote(
                        lote = delLote,
                        estado = lote,
                        habilitado = pagoEnCurso == null,
                        onPedir = { lote = EstadoDelLote.Preguntando },
                        onCancelar = { lote = EstadoDelLote.Cerrado },
                        onConfirmar = {
                            val estos = delLote
                            if (pagoEnCurso != null || estos.isEmpty()) return@TarjetaDelLote
                            lote = EstadoDelLote.Anotando(0, estos.size)
                            // Mientras corre el lote, las filas no confirman ni ignoran nada.
                            pagoEnCurso = CLAVE_DEL_LOTE
                            alcance.launch {
                                val resultado = confirmarElLote(estos, alAvanzar = { lote = EstadoDelLote.Anotando(it, estos.size) }) {
                                    confirmarLoEntendido(it, ahora = Clock.System.now().toEpochMilliseconds())
                                }
                                if (resultado.anotados.isNotEmpty()) vibrador.confirmar()
                                avisosResueltos = avisosResueltos +
                                    resultado.anotados.flatMap { it.avisos }.associateWith { SMS_STATE_CONFIRMED }
                                fallaronAca = fallaronAca + resultado.quedaron.map { it.first.cabezaId }
                                lote = EstadoDelLote.Terminado(textoDelResultadoDelLote(resultado), quedaron = resultado.quedaron.isNotEmpty())
                                pagoEnCurso = null
                                recarga++
                            }
                        },
                        onCerrar = { lote = EstadoDelLote.Cerrado },
                    )
                }
            }
            // «Todo al día» es un vacío: se afirma solo con lecturas que contestaron en esta visita.
            if (bandejaAlDia(mensajes, dias, candidatos, debitos) && noSePudoActualizar) return@LazyColumn
            if (bandejaAlDia(mensajes, dias, candidatos, debitos)) {
                if (celebrar) {
                    item(key = "celebracion") {
                        MomentoDeCelebracion(FRASE_DE_LA_BANDEJA_AL_DIA, Modifier.padding(bottom = Espacio.xs))
                    }
                }
                item { TodoAlDia() }
                // `mensajes` no es `null` acá —[bandejaAlDia] lo exige— y contestó
                // vacía de verdad: nunca llegó un mensaje del banco a esta cuenta. Es la misma
                // condición que [avisoDeCapturaEnLaBandeja] usa arriba, pero SIN mirar si el dueño
                // la silenció: ese renglón es un reclamo que se puede apagar, este es una
                // invitación a configurar algo que todavía no existe, y silenciar el reclamo no
                // debería apagar también la invitación.
                if (mensajes != null && capturaDeSms(soloLoQueLlegoSolo(mensajes).map { it.time }).nuncaLlegoNada) {
                    item { VacioDeLaCapturaEnLaBandeja(onNavigate) }
                }
                return@LazyColumn
            }

            // ── Lo que el banco cobró solo ────────────────────────────────────────
            // Cuotas (y recurrentes) con débito automático, vencidas y sin movimiento. Ver
            // [TarjetaDelDebitoAutomatico]: nada se anota hasta que el dueño confirma.
            if (debitos == null) {
                if (!lecturas.leyendoDebitos) {
                    item { SeccionQueNoSeLeyo("No pudimos revisar lo que el banco cobra solo") { recarga++ } }
                }
            } else if (debitos.isNotEmpty()) {
                item { Seccion(titulo = TITULO_DE_LOS_DEBITOS, conteo = debitos.size) }
                debitos.forEach { debito ->
                    item(key = "debito-${debito.clave}") {
                        TarjetaDelDebitoAutomatico(debito, onResuelto = {
                            debitosResueltos = debitosResueltos + debito.clave
                            recarga++
                        })
                    }
                }
                item { Spacer(Modifier.height(Medida.entreSecciones - Medida.dentroDeSeccion)) }
            }

            // ── Mensajes del banco ────────────────────────────────────────────────
            if (mensajes == null) {
                item { SeccionQueNoSeLeyo("No pudimos cargar tus mensajes del banco") { recarga++ } }
            } else {
                // Uno por pago: los avisos del mismo pago van en una sola tarjeta.
                val pendientes = pagosPorRevisar(mensajes)
                if (pendientes.isNotEmpty()) {
                    item { Seccion(titulo = "Mensajes del banco", conteo = pendientes.size) }
                    errorDeUnPago?.let { error ->
                        item { Text(error, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(bottom = Medida.dentroDeSeccion)) }
                    }
                    pendientes.forEach { sms ->
                        val grupoId = sms.grupoId
                        if (grupoId != null) {
                            item(key = "pago-$grupoId") {
                                val avisos = avisosDelPago(sms, mensajes)
                                val ids = avisos.map { it.id }
                                val pendientesDelPago = avisos.filter { it.state == SMS_STATE_PENDING }.map { it.id }
                                TarjetaDelMismoPago(
                                    modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
                                    avisos = avisos,
                                    trabajando = pagoEnCurso == grupoId,
                                    onRevisar = { revisarEnReconciliar(sms.id) },
                                    onIgnorar = {
                                        sobreElPago(grupoId, { Repositories.wallets.ignorarElMismoPago(grupoId, ids) }) {
                                            avisosResueltos = avisosResueltos + pendientesDelPago.associateWith { SMS_STATE_IGNORED }
                                        }
                                    },
                                    onNoSonElMismoPago = {
                                        sobreElPago(grupoId, { Repositories.wallets.noSonElMismoPago(grupoId, ids) }) {
                                            avisosSeparados = avisosSeparados + ids
                                        }
                                    },
                                    onCerrar = {
                                        sobreElPago(grupoId, { Repositories.wallets.confirmarElMismoPago(grupoId, ConfirmarElMismoPago(ids)) }) {
                                            avisosResueltos = avisosResueltos + pendientesDelPago.associateWith { SMS_STATE_CONFIRMED }
                                        }
                                    },
                                    entendido = entendidos[sms.id]?.linea,
                                    onConfirmar = entendidos[sms.id]?.let { anotacion -> { confirmarDesdeLaFila(anotacion) } },
                                    leido = propuestaDelPago(propuestas, sms)?.leido,
                                    sinLeer = propuestaDelPago(propuestas, sms)?.sinLeer,
                                    advertencia = advertencias[sms.id],
                                )
                            }
                            return@forEach
                        }
                        item(key = "sms-${sms.id}") {
                            TarjetaDeMensajeDelBanco(
                                sms,
                                onRevisar = { revisarEnReconciliar(sms.id) },
                                entendido = entendidos[sms.id]?.linea,
                                onConfirmar = entendidos[sms.id]?.let { anotacion -> { confirmarDesdeLaFila(anotacion) } },
                                onIgnorar = {
                                    sobreElPago(sms.id, { Repositories.wallets.ignoreSms(sms.id) }) {
                                        avisosResueltos = avisosResueltos + (sms.id to SMS_STATE_IGNORED)
                                    }
                                },
                                trabajando = pagoEnCurso == sms.id,
                                leido = propuestaDelPago(propuestas, sms)?.leido,
                                sinLeer = propuestaDelPago(propuestas, sms)?.sinLeer,
                                // En la bandeja todo está pendiente: la píldora no diría nada.
                                conPildoraDePendiente = false,
                                advertencia = advertencias[sms.id],
                                // El otro aviso puede estar ya confirmado: se busca en la lista
                                // entera, no solo entre los pendientes.
                                parecidoA = sms.parecidoA?.let { id -> mensajes.firstOrNull { it.id == id } },
                                pie = aQuienFue[sms.id]
                                    ?.takeIf { destinosParaGuardar.ofrece(it, cuentas) }
                                    ?.let { identificador ->
                                        {
                                            FilaGuardarElDestino(
                                                identificador = identificador,
                                                // La tarjeta no tiene el nombre que leyó Movi: se
                                                // prellena con el que trae el texto del banco («… a
                                                // MARTA RUIZ», «Te llegó dinero de …»). Hasta el
                                                // 4-oct quedaba vacío aunque el banco lo dijera.
                                                nombreSugerido = nombreParaLaFila(identificador, nombreDelBanco = null),
                                                textoDelAviso = sms.text,
                                                destinos = destinosParaGuardar.guardados.orEmpty(),
                                                // Guardada, la fila se va sola; al tocar «Revisar»
                                                // el server ya propone «Transferencia a <nombre>».
                                                // Y la propuesta se relee: con el nombre, el aviso puede quedar entendido.
                                                onGuardado = { guardado -> destinosParaGuardar.alGuardar(guardado); recarga++ },
                                            )
                                        }
                                    },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(Medida.entreSecciones - Medida.dentroDeSeccion)) }
                }
            }

            // ── Entraron solos ────────────────────────────────────────────────────
            if (dias == null) {
                item { SeccionQueNoSeLeyo("No pudimos cargar tus movimientos") { recarga++ } }
            } else {
                // Se cuentan los renglones, no los eventos: un traspaso es uno (ver
                // [renglonesQueEntraronSolos]), el mismo número que el renglón de Movimientos.
                val renglones = renglonesQueEntraronSolos(dias)
                if (renglones.isNotEmpty()) {
                    item {
                        Seccion(titulo = "Entraron solos", conteo = renglones.size)
                        Text(
                            "Llegaron por SMS, por un extracto o por una foto. Hasta confirmarlos no cuentan en " +
                                "tus gastos ni ingresos: tócalos para revisar el monto y la categoría.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
                        )
                        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                            renglones.forEachIndexed { i, renglon ->
                                when (renglon) {
                                    is MovementRow.Transfer -> TransferRow(
                                        row = renglon,
                                        accountNames = nombresDeCuentas,
                                        accountTypes = tiposDeCuentas,
                                        onClick = { movimientoAbierto = renglon.out },
                                    )
                                    is MovementRow.Single -> MovementSingleRow(
                                        tx = renglon.event,
                                        accountNames = nombresDeCuentas,
                                        onClick = { movimientoAbierto = renglon.event },
                                    )
                                    // `collapseTransfers` no agrupa ajustes (eso lo hace
                                    // `agruparAjustesDeSaldo`, que acá no se llama); la rama está
                                    // para que el `when` siga siendo exhaustivo.
                                    is MovementRow.Ajustes -> renglon.events.forEach { ajuste ->
                                        MovementSingleRow(
                                            tx = ajuste,
                                            accountNames = nombresDeCuentas,
                                            onClick = { movimientoAbierto = ajuste },
                                        )
                                    }
                                }
                                if (i < renglones.size - 1) Separador()
                            }
                        }
                        Spacer(Modifier.height(Medida.entreSecciones))
                    }
                }
            }

            // ── Pagos de tarjeta ──────────────────────────────────────────────────
            if (candidatos == null) {
                item { SeccionQueNoSeLeyo("No pudimos cargar los pagos de tarjeta") { recarga++ } }
            } else if (candidatos.isNotEmpty()) {
                item {
                    Seccion(titulo = "Pagos de tarjeta", conteo = candidatos.size)
                    // Abre una lista donde cada uno se confirma por separado (ver
                    // CardPaymentCandidatesSheet) — nunca se marcan todos de una.
                    FilaQueLleva(
                        texto = if (candidatos.size == 1) "1 pago de tarjeta sin marcar"
                        else "${candidatos.size} pagos de tarjeta sin marcar",
                        detalle = "Marcarlos evita contar la misma plata dos veces",
                        icono = Icons.Rounded.CreditCard,
                        onClick = { viendoCandidatos = true },
                    )
                }
            }
        }
    }

    movimientoAbierto?.let { evento ->
        // El mismo juego de hojas que Movimientos: ahí vive «Confirmar» (ver `PorConfirmar` en
        // CategorySheets.kt), junto con corregir el monto, la categoría o la cuenta antes.
        HojaDelMovimiento(
            event = evento,
            cuentas = cuentas,
            onDismiss = { movimientoAbierto = null },
            onCambiado = { movimientoAbierto = null; recarga++ },
            onResuelto = { resuelto -> movimientosResueltos = movimientosResueltos + (resuelto.transferId ?: resuelto.id) },
            onVerCuenta = tiposDeCuentas[evento.accountId]?.let { tipo ->
                { onNavigate(Screen.AccountDetail(evento.accountId, tipo.group)) }
            },
        )
    }

    if (viendoCandidatos && candidatos != null) {
        CardPaymentCandidatesSheet(
            candidates = candidatos,
            onDismiss = { viendoCandidatos = false },
            onConfirmed = { id -> candidatosResueltos = candidatosResueltos + id; recarga++ },
            onDismissedCandidate = { id -> candidatosResueltos = candidatosResueltos + id; recarga++ },
        )
    }
}

/**
 * Una fila que lleva a otro lado: una [Fila] del sistema en su superficie, con su ícono, el texto,
 * una línea de apoyo y la flecha. La usan «Subir comprobante o extracto», el aviso de la captura y la
 * entrada a los pagos de tarjeta.
 */
@Composable
private fun FilaQueLleva(
    texto: String,
    onClick: () -> Unit,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    esAlerta: Boolean = false,
) {
    Superficie(modifier = modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = texto,
            subtitulo = detalle,
            inicio = { IconoEnCirculo(icono, tono = if (esAlerta) TonoDeIcono.Aviso else TonoDeIcono.Marca) },
            final = { Chevron() },
            onClick = onClick,
        )
    }
}

/** Una fuente que no contestó: se dice en el lugar de su sección, con su «Reintentar». */
@Composable
private fun SeccionQueNoSeLeyo(texto: String, onReintentar: () -> Unit) {
    NoSePudoLeer(texto, onReintentar = onReintentar, modifier = Modifier.padding(bottom = Medida.entreSecciones))
}

/** Lo que dice la celebración cuando el dueño deja la bandeja vacía. */
const val FRASE_DE_LA_BANDEJA_AL_DIA = "¡Listo! Revisaste todo lo que había."

/**
 * El vacío de verdad: las tres fuentes contestaron y no hay nada. No ofrece ninguna acción —no hay
 * nada que hacer, que es la buena noticia— y explica qué caería acá, para que la bandeja vacía no
 * sea un misterio.
 */
@Composable
private fun TodoAlDia() {
    Vacio(
        titulo = TODO_AL_DIA,
        detalle = "Aquí caen los mensajes del banco, los movimientos que entran solos y los pagos de tarjeta " +
            "que Movi reconoce, hasta que los revises.",
        modifier = Modifier.padding(top = Espacio.xxl),
    )
}

/**
 * Debajo de [TodoAlDia], cuando además nunca llegó un mensaje del banco —ver el
 * llamador—, invita a configurar la captura en vez de dejar la bandeja vacía sin explicar por qué
 * ese mensaje nunca va a entrar solo.
 *
 * **El texto cambia por plataforma, con la MISMA señal que [tituloDeCapturaDelBanco]**: en Android
 * la captura lee SMS y notificaciones del banco; en la web y en iOS —que no leen nada del
 * teléfono— lo único que llega son los correos que reenvía el banco (ver
 * `CorreoEntranteRoutes.kt`), así que prometer «mensajes» ahí sería prometer algo que ese aparato
 * no hace.
 */
@Composable
private fun VacioDeLaCapturaEnLaBandeja(onNavigate: (Screen) -> Unit) {
    VacioQueEnsena(
        titulo = "Que tus movimientos entren solos",
        detalle = if (isAndroid) {
            "Movi puede leer los mensajes y avisos de tu banco y dejarlos aquí para que los revises. " +
                "Así no tienes que anotar cada compra."
        } else {
            "Movi puede leer los correos de tu banco y dejarlos aquí para que los revises. Así no " +
                "tienes que anotar cada compra."
        },
        accion = "Configurar la captura",
        onAccion = { onNavigate(Screen.CapturaDelBanco) },
        modifier = Modifier.padding(top = Medida.entreSecciones),
    )
}

/**
 * La bandeja mientras nada contestó: un rótulo de sección y una tarjeta con tres renglones, la
 * forma de «Entraron solos» — la sección de renglones, que es la que más se repite. No dice ni
 * cuántos hay ni de qué fuente: nada de eso se sabe todavía.
 */
@Composable
private fun EsqueletoDePorRevisar() {
    Column(modifier = Modifier.testTag(TAG_ESQUELETO_DE_POR_REVISAR)) {
        RotuloDeSeccionEsqueleto()
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            repeat(3) { i ->
                EsqueletoDeFila()
                if (i < 2) Separador()
            }
        }
    }
}

/**
 * **Lo que Movi leyó de un pago de la bandeja**: la propuesta del server para ese aviso suelto o para
 * su pago entero (la misma búsqueda que [loQueMoviEntendio]). La cabeza de la tarjeta saca de acá el
 * comercio y el monto; `null` mientras la lectura no llega o si no hay propuesta.
 */
internal fun propuestaDelPago(
    propuestas: com.jvillada.movi.shared.model.PropuestasDeLaBandeja?,
    cabeza: com.jvillada.movi.shared.model.SmsMessage,
): com.jvillada.movi.shared.model.PropuestaDelAviso? {
    val todas = propuestas?.propuestas ?: return null
    val grupo = cabeza.grupoId
    return (if (grupo != null) todas.firstOrNull { it.grupoId == grupo } else null)
        ?: todas.firstOrNull { it.avisoId == cabeza.id }
}
