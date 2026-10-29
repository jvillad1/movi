package com.jvillada.movi.ui.porrevisar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.FilaDeNavegacion
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
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.ui.espacios.TarjetaDeSaldar
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.NoSePudoActualizar
import com.jvillada.movi.ui.components.PanelDeTablero
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
import com.jvillada.movi.ui.transactions.AvisosPorConfirmar
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.SelectorDeCategoria
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.quickadd.categoriasParaLasPastillasDeAgregar
import com.jvillada.movi.ui.sms.EligeLaCategoria
import com.jvillada.movi.ui.sms.categoriasDeLaTarjeta
import com.jvillada.movi.shared.model.SmsMessage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.jvillada.movi.ui.transactions.HojaDeConfirmarLosAvisos
import com.jvillada.movi.ui.transactions.TarjetaDeAvisosDelBanco
import com.jvillada.movi.ui.transactions.avisosPorConfirmar
import com.jvillada.movi.ui.transactions.rememberConfirmarLosAvisos
import com.jvillada.movi.ui.transactions.textoDeLosConfirmados
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
fun RenglonPorRevisar(
    cuantos: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** De qué es el número («2 avisos · 1 movimiento», ver [desgloseDePorRevisar]); vuelta de diseño 22. */
    desglose: String? = null,
) {
    TarjetaDePropuesta(
        icono = Icons.Rounded.Inbox,
        titulo = textoDePorRevisar(cuantos),
        cuerpo = desglose ?: "Avisos y movimientos por confirmar",
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
    // Vuelta 44: «Confirmar los N» con la MISMA pregunta y el mismo lote de Actividad, y el aviso de
    // abajo («Anotado», «3 confirmados»).
    val confirmarLosAvisos = rememberConfirmarLosAvisos()
    val avisoAbajo = remember { SnackbarHostState() }
    fun confirmarDesdeLaFila(anotacion: AnotacionEntendida) {
        if (confirmarLosAvisos.confirmando) return
        sobreElPago(
            anotacion.cabezaId,
            { confirmarLoEntendido(anotacion, ahora = Clock.System.now().toEpochMilliseconds()) },
            alFallar = { fallaronAca = fallaronAca + anotacion.cabezaId },
        ) {
            vibrador.confirmar()
            avisosResueltos = avisosResueltos + anotacion.avisos.associateWith { SMS_STATE_CONFIRMED }
            // «Anotado»: sin «Deshacer» — confirmar un aviso no tiene vuelta en Movi (se anula el
            // movimiento, como siempre; ver `textoDeLosConfirmados`).
            alcance.launch { avisoAbajo.showSnackbar(textoDeAnotado(anotacion)) }
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
    // Vuelta 46: la categoría que el dueño eligió en la tarjeta de un aviso al que solo le faltaba
    // esa (cabeza del pago → categoría). Con ella el aviso queda confirmable ahí mismo y en el lote.
    var categoriasElegidasAca by remember { mutableStateOf(emptyMap<String, String>()) }
    // La cabeza del aviso cuyo «Todas ›» abrió el selector completo.
    var todasLasCategoriasPara by remember { mutableStateOf<String?>(null) }
    // Vuelta 51 · «Más antiguos»: plegada, y la pregunta de «Ignorar los N».
    var antiguosAbiertos by rememberSaveable { mutableStateOf(false) }
    var pidiendoIgnorarLosAntiguos by remember { mutableStateOf(false) }
    var ignorandoLosAntiguos by remember { mutableStateOf(false) }
    val entendimientos: Map<String, LoQueMoviEntendio> = remember(mensajes, propuestas, cuentas, fallaronAca, categoriasElegidasAca) {
        val todos = mensajes ?: return@remember emptyMap()
        val leidas = propuestas ?: return@remember emptyMap()
        val ahora = Clock.System.now().toEpochMilliseconds()
        pagosPorRevisar(todos)
            .filter { it.id !in fallaronAca }
            .associate { cabeza -> cabeza.id to loQueMoviEntendio(cabeza, todos, leidas, cuentas, ahora, categoriasElegidasAca[cabeza.id]) }
    }
    val entendidos: Map<String, AnotacionEntendida> =
        entendimientos.mapNotNull { (id, e) -> e.anotacion?.let { id to it } }.toMap()
    /** Vuelta 44: «Transporte · Bancolombia Ahorros», lo que Movi entendió de cada pago, entendido o no. */
    val lineas: Map<String, String> = entendimientos.mapNotNull { (id, e) -> e.linea?.let { id to it } }.toMap()
    /** Lo que pinta la tarjeta de «Confirmar los N» y lo que confirma su hoja: lo de Actividad. */
    val avisosDelLote: AvisosPorConfirmar? = remember(mensajes, propuestas, cuentas, fallaronAca, categoriasElegidasAca) {
        val todos = mensajes ?: return@remember null
        avisosPorConfirmar(todos, propuestas, cuentas, Clock.System.now().toEpochMilliseconds(), fallaronAca, categoriasElegidasAca)
    }
    /**
     * Vuelta 46: **las pastillas de una tarjeta que pide la categoría** —las frecuentes del tipo, como
     * en Agregar; sin el uso del Inicio a mano, las que el dueño usa (`categoriasDelDueno`)—, o `null`
     * si a ese aviso le falta algo más que la categoría.
     */
    fun eligeLaCategoriaDe(cabeza: SmsMessage): EligeLaCategoria? {
        val entendimiento = entendimientos[cabeza.id] ?: return null
        val elegida = categoriasElegidasAca[cabeza.id]
        // Vuelta 51: también un aviso entendido entero —«Cambiar» abre estas pastillas en la tarjeta—.
        if (!entendimiento.faltaSoloLaCategoria && elegida == null && entendimiento.anotacion == null) return null
        val leido = propuestaDelPago(propuestas, cabeza)?.leido
        val tipo = leido?.type ?: com.jvillada.movi.shared.model.TransactionType.EXPENSE
        val actual = entendimiento.anotacion?.categoria
        val frecuentes = categoriasDeLaTarjeta(
            frecuentes = categoriasParaLasPastillasDeAgregar(
                categoriaActual = elegida ?: actual.orEmpty(),
                tipo = tipo,
                usadas = UsedCategoriesCache.used,
                prefs = UsedCategoriesCache.prefs,
                usos = UsedCategoriesCache.usosRecientes,
            ),
            delDueno = propuestas?.categoriasDelDueno.orEmpty(),
        )
        return EligeLaCategoria(
            categorias = frecuentes,
            elegida = elegida,
            onElegir = { categoriasElegidasAca = categoriasElegidasAca + (cabeza.id to it) },
            onTodas = { todasLasCategoriasPara = cabeza.id },
            actual = actual,
        )
    }
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
    // ── Vuelta 66 · las transferencias que saldan un grupo ── antes solo se veían dentro del grupo (y
    // la que esperaba un saldado ya marcado, en ningún lado). Secundaria: si falla, no se dice nada.
    // Vuelta 67: es una lectura más de la bandeja ([LecturasPorRevisar]) y cuenta para «Todo al día»
    // y para «N por revisar»; lo que se resuelve acá se va en el acto, sin esperar la relectura.
    var saldadasAca by remember { mutableStateOf(emptySet<String>()) }
    val propuestasDeSaldar: List<PropuestaDeSaldar> =
        lecturas.propuestasDeSaldar.orEmpty().filterNot { it.eventoId in saldadasAca }
    val tiposDeCuentas = remember(cuentas) { cuentas.associate { it.id to it.type } }

    // La primera carga: alguna de las tres fuentes todavía no dijo nada. Con lo de antes en
    // pantalla (un reintento) no se vuelve al esqueleto.
    val cargando = (mensajes == null && lecturas.leyendoMensajes) ||
        (dias == null && leyendoDias) ||
        (lecturas.candidatos == null && lecturas.leyendoCandidatos) ||
        (lecturas.debitos == null && lecturas.leyendoDebitos) ||
        // Vuelta 67: sin esto, «Todo al día» asomaba un instante antes de que llegara la propuesta.
        (lecturas.propuestasDeSaldar == null && lecturas.leyendoSaldar)
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
        saldadasAca.isNotEmpty()
    val bandejaVacia: Boolean? = if (cargando || noSePudoActualizar) null else bandejaAlDia(mensajes, dias, candidatos, debitos, propuestasDeSaldar)
    LaunchedEffect(bandejaVacia) {
        val celebracion = Celebracion(ultimoEstadoDeLaBandeja)
        if (celebracion.observar(bandejaVacia, huboAccion)) {
            celebrar = true
            fueAReconciliar = false
        }
        if (bandejaVacia == false) celebrar = false
        ultimoEstadoDeLaBandeja = celebracion.ultimoConocido
    }
    /**
     * Abre Revisar aviso. Vuelta 44: desde «Cambiar» (un aviso que Movi entendió) abre con las
     * categorías a la vista ([Screen.SMSReconcile.abrirCategoria]): lo que se viene a cambiar casi
     * siempre es la categoría, y así son dos toques (la categoría y «Confirmar») y no tres.
     */
    val revisarEnReconciliar: (String, Boolean) -> Unit = { id, aCambiar ->
        fueAReconciliar = true
        onNavigate(Screen.SMSReconcile(id, abrirCategoria = aCambiar))
    }

    // Vuelta 42: las secciones de la bandeja, armadas una vez. En el teléfono (y en una columna) van en
    // el orden de siempre; a partir del ancho en que Patrimonio y una cuenta pasan a dos columnas
    // ([porRevisarEnDosColumnas]), «Mensajes del banco» va a la izquierda y lo demás a la derecha.
    fun LazyListScope.noSePudoActualizarArriba(ll: LlavesDeLaLista) {
        if (noSePudoActualizar) {
            fila(ll, "no-se-pudo-actualizar") {
                NoSePudoActualizar(onReintentar = { recarga++ }, modifier = Modifier.padding(bottom = Medida.entreSecciones))
            }
        }
    }

    fun LazyListScope.subirYCaptura(ll: LlavesDeLaLista) {
        // La puerta para lo que no llega solo: una captura de una transferencia, un recibo, un
        // PDF del banco. Arriba y siempre, también con la bandeja al día.
        fila(ll, "subir-papel") {
            FilaQueLleva(
                texto = "Subir comprobante o extracto",
                detalle = "Movi lo lee y lo deja aquí",
                icono = Icons.Rounded.UploadFile,
                onClick = elegirPapel,
                modifier = Modifier.padding(bottom = Medida.entreSecciones).testTag(TAG_SUBIR_PAPEL_EN_LA_BANDEJA),
            )
        }

        avisoDeCapturaEnLaBandeja(mensajes, capturaSilenciada)?.let { aviso ->
            fila(ll, "aviso-de-captura") {
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
    }

    fun LazyListScope.elLote(ll: LlavesDeLaLista) {
        // Vuelta 44 · «Confirmar los N», arriba: la tarjeta de Actividad (cuántos, cuánto suman, de
        // qué comercios), sin «Revisar uno por uno» —ya estás acá—. Desde dos que Movi entendió.
        val avisos = avisosDelLote?.takeIf { it.confirmables.size >= MINIMO_PARA_EL_LOTE }
        if (avisos != null) {
            fila(ll, "lote") {
                TarjetaDeAvisosDelBanco(
                    avisos = avisos,
                    ocupado = confirmarLosAvisos.confirmando,
                    onConfirmar = { if (pagoEnCurso == null) confirmarLosAvisos.pedir() },
                    onRevisar = null,
                    modifier = Modifier.padding(bottom = Medida.entreSecciones).testTag(TAG_LOTE_DE_LA_BANDEJA),
                )
            }
        }
    }

    /** Si la bandeja está al día: pinta el vacío (o nada, si no se pudo actualizar) y dice que sí. */
    fun LazyListScope.alDia(ll: LlavesDeLaLista): Boolean {
        // «Todo al día» es un vacío: se afirma solo con lecturas que contestaron en esta visita.
        if (bandejaAlDia(mensajes, dias, candidatos, debitos, propuestasDeSaldar) && noSePudoActualizar) return true
        if (bandejaAlDia(mensajes, dias, candidatos, debitos, propuestasDeSaldar)) {
            if (celebrar) {
                fila(ll, "celebracion") {
                    MomentoDeCelebracion(FRASE_DE_LA_BANDEJA_AL_DIA, Modifier.padding(bottom = Espacio.xs))
                }
            }
            fila(ll, "todo-al-dia") { TodoAlDia() }
            // `mensajes` no es `null` acá —[bandejaAlDia] lo exige— y contestó
            // vacía de verdad: nunca llegó un mensaje del banco a esta cuenta. Es la misma
            // condición que [avisoDeCapturaEnLaBandeja] usa arriba, pero SIN mirar si el dueño
            // la silenció: ese renglón es un reclamo que se puede apagar, este es una
            // invitación a configurar algo que todavía no existe, y silenciar el reclamo no
            // debería apagar también la invitación.
            if (mensajes != null && capturaDeSms(soloLoQueLlegoSolo(mensajes).map { it.time }).nuncaLlegoNada) {
                fila(ll, "vacio-de-la-captura") { VacioDeLaCapturaEnLaBandeja(onNavigate) }
            }
            return true
        }
        return false
    }

    fun LazyListScope.losSaldados(ll: LlavesDeLaLista) {
        if (propuestasDeSaldar.isEmpty()) return
        fila(ll, "titulo-de-saldar") { Seccion(titulo = TITULO_DE_SALDAR_EN_LOS_GRUPOS, conteo = propuestasDeSaldar.size) }
        propuestasDeSaldar.forEach { p ->
            fila(ll, "saldar-${p.eventoId}") {
                TarjetaDeSaldar(
                    p = p,
                    espacioId = p.espacioId,
                    quien = p.quien.ifBlank { "otra persona" },
                    nombreDelGrupo = p.nombreDelEspacio,
                    conElGrupo = true,
                    onHecho = {
                        saldadasAca = saldadasAca + p.eventoId
                        recarga++
                    },
                    modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
                )
            }
        }
        fila(ll, "despues-de-saldar") { Spacer(Modifier.height(Medida.entreSecciones - Medida.dentroDeSeccion)) }
    }

    fun LazyListScope.losDebitos(ll: LlavesDeLaLista) {
        // ── Lo que el banco cobró solo ────────────────────────────────────────
        // Cuotas (y recurrentes) con débito automático, vencidas y sin movimiento. Ver
        // [TarjetaDelDebitoAutomatico]: nada se anota hasta que el dueño confirma.
        if (debitos == null) {
            if (!lecturas.leyendoDebitos) {
                fila(ll, "no-se-leyeron-los-debitos") { SeccionQueNoSeLeyo("No pudimos revisar lo que el banco cobra solo") { recarga++ } }
            }
        } else if (debitos.isNotEmpty()) {
            fila(ll, "titulo-de-los-debitos") { Seccion(titulo = TITULO_DE_LOS_DEBITOS, conteo = debitos.size) }
            debitos.forEach { debito ->
                fila(ll, "debito-${debito.clave}") {
                    TarjetaDelDebitoAutomatico(debito, onResuelto = {
                        debitosResueltos = debitosResueltos + debito.clave
                        recarga++
                    })
                }
            }
            fila(ll, "despues-de-los-debitos") { Spacer(Modifier.height(Medida.entreSecciones - Medida.dentroDeSeccion)) }
        }
    }

    /** Vuelta 51: la tarjeta de un pago pendiente (suelto o avisado varias veces), reciente o antiguo. */
    fun LazyListScope.tarjetaDelPago(ll: LlavesDeLaLista, mensajes: List<SmsMessage>, sms: SmsMessage) {
        val grupoId = sms.grupoId
        if (grupoId != null) {
            fila(ll, "pago-$grupoId") {
                val avisos = avisosDelPago(sms, mensajes)
                val ids = avisos.map { it.id }
                val pendientesDelPago = avisos.filter { it.state == SMS_STATE_PENDING }.map { it.id }
                TarjetaDelMismoPago(
                    modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
                    avisos = avisos,
                    trabajando = pagoEnCurso == grupoId || confirmarLosAvisos.confirmando,
                    onRevisar = { revisarEnReconciliar(sms.id, entendidos[sms.id] != null) },
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
                    loEntendido = lineas[sms.id],
                )
            }
            return
        }
        fila(ll, "sms-${sms.id}") {
            TarjetaDeMensajeDelBanco(
                sms,
                onRevisar = { revisarEnReconciliar(sms.id, entendidos[sms.id] != null) },
                entendido = entendidos[sms.id]?.linea,
                onConfirmar = entendidos[sms.id]?.let { anotacion -> { confirmarDesdeLaFila(anotacion) } },
                onIgnorar = {
                    sobreElPago(sms.id, { Repositories.wallets.ignoreSms(sms.id) }) {
                        avisosResueltos = avisosResueltos + (sms.id to SMS_STATE_IGNORED)
                    }
                },
                trabajando = pagoEnCurso == sms.id || confirmarLosAvisos.confirmando,
                leido = propuestaDelPago(propuestas, sms)?.leido,
                sinLeer = propuestaDelPago(propuestas, sms)?.sinLeer,
                // En la bandeja todo está pendiente: la píldora no diría nada.
                conPildoraDePendiente = false,
                advertencia = advertencias[sms.id],
                loEntendido = lineas[sms.id],
                eligeLaCategoria = eligeLaCategoriaDe(sms),
                // Vuelta 51: lo que no es la categoría (cuenta, monto, comercio) sigue en Revisar aviso.
                onMasCambios = { revisarEnReconciliar(sms.id, false) },
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

    fun LazyListScope.losMensajes(ll: LlavesDeLaLista) {
        // ── Mensajes del banco ────────────────────────────────────────────────
        if (mensajes == null) {
            fila(ll, "no-se-leyeron-los-mensajes") { SeccionQueNoSeLeyo("No pudimos cargar tus mensajes del banco") { recarga++ } }
        } else {
            // Uno por pago: los avisos del mismo pago van en una sola tarjeta. Vuelta 51: los de más de
            // 30 días van aparte, en «Más antiguos», plegados.
            val ahora = Clock.System.now().toEpochMilliseconds()
            val pendientes = pagosRecientesPorRevisar(mensajes, ahora)
            val antiguos = pagosAntiguosPorRevisar(mensajes, ahora)
            if (pendientes.isNotEmpty() || antiguos.isNotEmpty()) {
                errorDeUnPago?.let { error ->
                    fila(ll, "error-de-un-pago") { Text(error, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(bottom = Medida.dentroDeSeccion)) }
                }
            }
            if (pendientes.isNotEmpty()) {
                fila(ll, "titulo-de-los-mensajes") { Seccion(titulo = "Mensajes del banco", conteo = pendientes.size) }
                pendientes.forEach { sms -> tarjetaDelPago(ll, mensajes, sms) }
                fila(ll, "despues-de-los-mensajes") { Spacer(Modifier.height(Medida.entreSecciones - Medida.dentroDeSeccion)) }
            }
            if (antiguos.isNotEmpty()) {
                fila(ll, "titulo-de-los-antiguos") {
                    Seccion(
                        titulo = TITULO_DE_LOS_ANTIGUOS,
                        conteo = antiguos.size,
                        accion = if (antiguosAbiertos) OCULTAR_LOS_ANTIGUOS else VER_LOS_ANTIGUOS,
                        onAccion = { antiguosAbiertos = !antiguosAbiertos },
                        modifier = Modifier.testTag(TAG_SECCION_DE_LOS_ANTIGUOS),
                    )
                }
                fila(ll, "ignorar-los-antiguos") {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Medida.dentroDeSeccion)) {
                        Text(AYUDA_DE_LOS_ANTIGUOS, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        Spacer(Modifier.height(Espacio.xs))
                        Boton(
                            textoDeIgnorarLosAntiguos(antiguos.size),
                            onClick = { pidiendoIgnorarLosAntiguos = true },
                            variante = VarianteDeBoton.Secundario,
                            tamano = TamanoDeBoton.S,
                            habilitado = pagoEnCurso == null && !confirmarLosAvisos.confirmando,
                            modifier = Modifier.testTag(TAG_IGNORAR_LOS_ANTIGUOS),
                        )
                    }
                }
                if (antiguosAbiertos) {
                    antiguos.forEach { sms -> tarjetaDelPago(ll, mensajes, sms) }
                }
                fila(ll, "despues-de-los-antiguos") { Spacer(Modifier.height(Medida.entreSecciones - Medida.dentroDeSeccion)) }
            }
        }
    }

    fun LazyListScope.entraronSolos(ll: LlavesDeLaLista) {
        // ── Entraron solos ────────────────────────────────────────────────────
        if (dias == null) {
            fila(ll, "no-se-leyeron-los-movimientos") { SeccionQueNoSeLeyo("No pudimos cargar tus movimientos") { recarga++ } }
        } else {
            // Se cuentan los renglones, no los eventos: un traspaso es uno (ver
            // [renglonesQueEntraronSolos]), el mismo número que el renglón de Movimientos.
            val renglones = renglonesQueEntraronSolos(dias)
            if (renglones.isNotEmpty()) {
                fila(ll, "entraron-solos") {
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
    }

    fun LazyListScope.losPagosDeTarjeta(ll: LlavesDeLaLista) {
        // ── Pagos de tarjeta ──────────────────────────────────────────────────
        if (candidatos == null) {
            fila(ll, "no-se-leyeron-los-pagos") { SeccionQueNoSeLeyo("No pudimos cargar los pagos de tarjeta") { recarga++ } }
        } else if (candidatos.isNotEmpty()) {
            fila(ll, "pagos-de-tarjeta") {
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

    // Dos columnas solo con algo a cada lado: sin mensajes del banco por revisar, la izquierda
    // quedaría vacía (un hueco, no un tablero), y la bandeja al día es un vacío de una columna.
    val hayMensajesPorRevisar = mensajes?.let { pagosPorRevisar(it).isNotEmpty() } == true
    val conDosLados = !cargando && hayMensajesPorRevisar && !bandejaAlDia(mensajes, dias, candidatos, debitos, propuestasDeSaldar)
    val alPie = Medida.botonFlotante + Medida.margenDePantalla
    // Vuelta 44: al volver de Revisar aviso, la lista queda donde la dejaste (ver [ListaQueSeConserva]).
    val lista = rememberListaQueSeConserva(conDatos = !cargando)
    val laDeLaDerecha = rememberListaQueSeConserva(conDatos = !cargando)

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
    PanelDeTablero(enDosColumnas = { porRevisarEnDosColumnas(it) && conDosLados }) { dosColumnas ->
    Column(modifier = Modifier.fillMaxSize()) {
        MinScreenHeader(
            title = TITULO_DE_POR_REVISAR,
            leading = HeaderLeading.Back(fallback = Screen.Transactions()),
            action = {
                if (actualizandoConAlgoALaVista) ActualizandoEnLaCabecera()
                BotonDeIcono(Icons.Rounded.Refresh, "Actualizar", onClick = { recarga++ })
            },
        )

        if (dosColumnas) {
            // Los rellenos suman los 48 de [RELLENO_DE_DOS_COLUMNAS], como Patrimonio y una cuenta.
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = lista.estado,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LOS_MENSAJES),
                    contentPadding = PaddingValues(
                        start = Medida.margenDePantalla,
                        end = Medida.margenDePantalla / 2,
                        top = Medida.dentroDeSeccion,
                        bottom = alPie,
                    ),
                ) {
                    lista.llaves.empezar()
                    elLote(lista.llaves)
                    losMensajes(lista.llaves)
                }
                LazyColumn(
                    state = laDeLaDerecha.estado,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LO_DEMAS),
                    contentPadding = PaddingValues(
                        start = Medida.margenDePantalla / 2,
                        end = Medida.margenDePantalla,
                        top = Medida.dentroDeSeccion,
                        bottom = alPie,
                    ),
                ) {
                    val ll = laDeLaDerecha.llaves
                    ll.empezar()
                    noSePudoActualizarArriba(ll)
                    subirYCaptura(ll)
                    losSaldados(ll)
                    losDebitos(ll)
                    entraronSolos(ll)
                    losPagosDeTarjeta(ll)
                }
            }
        } else {
            LazyColumn(
                state = lista.estado,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Medida.margenDePantalla,
                    end = Medida.margenDePantalla,
                    top = Medida.dentroDeSeccion,
                    bottom = alPie,
                ),
            ) {
                val ll = lista.llaves
                ll.empezar()
                if (cargando) {
                    fila(ll, LLAVE_DEL_ESQUELETO) { EsqueletoDePorRevisar() }
                    return@LazyColumn
                }
                noSePudoActualizarArriba(ll)
                subirYCaptura(ll)
                elLote(ll)
                losSaldados(ll)
                if (alDia(ll)) return@LazyColumn
                losDebitos(ll)
                losMensajes(ll)
                entraronSolos(ll)
                losPagosDeTarjeta(ll)
            }
        }
    }
    } // PanelDeTablero
    SnackbarHost(
        hostState = avisoAbajo,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Medida.dentroDeSeccion),
    )
    }

    // Vuelta 46: «Todas ›» de la tarjeta que pide la categoría: el selector completo, en una hoja.
    todasLasCategoriasPara?.let { cabezaId ->
        val cabeza = mensajes?.firstOrNull { it.id == cabezaId }
        MarcoDeHoja(onDismiss = { todasLasCategoriasPara = null }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                EncabezadoDeHoja("Categoría")
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                SelectorDeCategoria(
                    elegida = categoriasElegidasAca[cabezaId] ?: entendidos[cabezaId]?.categoria.orEmpty(),
                    onElegir = {
                        categoriasElegidasAca = categoriasElegidasAca + (cabezaId to it)
                        todasLasCategoriasPara = null
                    },
                    tipo = cabeza?.let { propuestaDelPago(propuestas, it)?.leido?.type },
                    usadas = UsedCategoriesCache.used,
                    prefs = UsedCategoriesCache.prefs,
                    usos = UsedCategoriesCache.usosRecientes,
                )
                Spacer(Modifier.height(Medida.margenDePantalla))
            }
        }
    }

    // Vuelta 51: «Ignorar los N» de «Más antiguos», con su pregunta. Es el «Ignorar» de siempre, uno por
    // pago: no se borra nada, y lo ignorado sigue en el historial de Captura del banco.
    if (pidiendoIgnorarLosAntiguos) {
        val antiguos = mensajes?.let { pagosAntiguosPorRevisar(it) }.orEmpty()
        if (antiguos.isEmpty()) {
            pidiendoIgnorarLosAntiguos = false
        } else {
            ConfirmarEnHoja(
                pregunta = preguntaDeIgnorarLosAntiguos(antiguos.size),
                detalle = DETALLE_DE_IGNORAR_LOS_ANTIGUOS,
                textoConfirmar = textoDeIgnorarLosAntiguos(antiguos.size),
                ocupado = ignorandoLosAntiguos,
                varianteConfirmar = VarianteDeBoton.Principal,
                onCancelar = { pidiendoIgnorarLosAntiguos = false },
                onConfirmar = {
                    val todos = mensajes.orEmpty()
                    ignorandoLosAntiguos = true
                    errorDeUnPago = null
                    alcance.launch {
                        val ignorados = mutableListOf<String>()
                        var fallo: Throwable? = null
                        for (cabeza in antiguos) {
                            val grupo = cabeza.grupoId
                            val avisos = if (grupo != null) avisosDelPago(cabeza, todos) else listOf(cabeza)
                            val pendientesDelPago = avisos.filter { it.state == SMS_STATE_PENDING }.map { it.id }
                            intentar {
                                if (grupo != null) Repositories.wallets.ignorarElMismoPago(grupo, avisos.map { it.id })
                                else Repositories.wallets.ignoreSms(cabeza.id)
                            }.onSuccess { ignorados += pendientesDelPago }
                                .onFailure { fallo = it }
                            if (fallo != null) break
                        }
                        avisosResueltos = avisosResueltos + ignorados.associateWith { SMS_STATE_IGNORED }
                        ignorandoLosAntiguos = false
                        pidiendoIgnorarLosAntiguos = false
                        fallo?.let { errorDeUnPago = it.toUserMessage() }
                        recarga++
                    }
                },
            )
        }
    }

    // Vuelta 44: la pregunta de «Confirmar los N», la de Actividad. Lo confirmado se va enseguida; lo
    // que falló vuelve a «Revisar».
    HojaDeConfirmarLosAvisos(confirmarLosAvisos, avisosDelLote) { resultado ->
        if (resultado.anotados.isNotEmpty()) vibrador.confirmar()
        avisosResueltos = avisosResueltos + resultado.anotados.flatMap { it.avisos }.associateWith { SMS_STATE_CONFIRMED }
        fallaronAca = fallaronAca + resultado.quedaron.map { it.first.cabezaId }
        recarga++
        avisoAbajo.showSnackbar(textoDeLosConfirmados(resultado))
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
 * Una fila que lleva a otro lado: la [FilaDeNavegacion] del sistema (vuelta 42: el círculo neutro;
 * era amarillo lleno). La usan «Subir comprobante o extracto», el aviso de la captura (en tono de
 * aviso) y la entrada a los pagos de tarjeta.
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
    FilaDeNavegacion(
        titulo = texto,
        icono = icono,
        onClick = onClick,
        modifier = modifier,
        subtitulo = detalle,
        aviso = esAlerta,
    )
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

// ── Vuelta 51 · los avisos antiguos ────────────────────────────────────────────

/** El título de la sección plegada de los avisos de más de 30 días («Más antiguos · 2»). */
const val TITULO_DE_LOS_ANTIGUOS: String = "Más antiguos"

/** La acción de la sección, plegada y abierta. */
const val VER_LOS_ANTIGUOS: String = "Ver"
const val OCULTAR_LOS_ANTIGUOS: String = "Ocultar"

/** Lo que dice la sección debajo del título. */
const val AYUDA_DE_LOS_ANTIGUOS: String = "Llegaron hace más de 30 días y no cuentan en «Por confirmar»."

/** «Ignorar el aviso», «Ignorar los 2». */
fun textoDeIgnorarLosAntiguos(cuantos: Int): String = if (cuantos == 1) "Ignorar el aviso" else "Ignorar los $cuantos"

/** «¿Ignorar los 2 avisos antiguos?». */
fun preguntaDeIgnorarLosAntiguos(cuantos: Int): String =
    if (cuantos == 1) "¿Ignorar el aviso antiguo?" else "¿Ignorar los $cuantos avisos antiguos?"

const val DETALLE_DE_IGNORAR_LOS_ANTIGUOS: String =
    "No se anota nada y no se borra nada: quedan como ignorados en Captura del banco."

const val TAG_SECCION_DE_LOS_ANTIGUOS: String = "por-revisar:antiguos"
const val TAG_IGNORAR_LOS_ANTIGUOS: String = "por-revisar:ignorar-los-antiguos"

/** Vuelta 66: el título de las transferencias que saldan un grupo, en Por revisar. */
const val TITULO_DE_SALDAR_EN_LOS_GRUPOS: String = "Para saldar en tus grupos"
