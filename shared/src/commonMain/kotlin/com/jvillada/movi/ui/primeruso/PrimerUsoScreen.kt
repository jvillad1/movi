package com.jvillada.movi.ui.primeruso

import com.jvillada.movi.ui.components.ds.coloresDeLoElegido
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.DireccionDeTransicion
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.FormularioDeCuenta
import com.jvillada.movi.ui.accounts.SelectorDeBanco
import com.jvillada.movi.ui.accounts.crearCuentaConSaldo
import com.jvillada.movi.ui.accounts.rememberEstadoDelFormularioDeCuenta
import com.jvillada.movi.ui.accounts.rotuloDelTipoDeCuenta
import com.jvillada.movi.ui.accounts.tipoSiElNombreNoLoDice
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Encabezado
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.profile.guardarElInicioDelMes
import com.jvillada.movi.ui.profile.textoDelInicioDelMes
import com.jvillada.movi.ui.sms.SmsSensorSetupSection
import com.jvillada.movi.ui.sms.rememberSmsCaptureReady
import com.jvillada.movi.ui.transicionEntrePantallas
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.Stable
import com.jvillada.movi.shared.model.EstadoDelPrimerPaso
import com.jvillada.movi.shared.model.LoQueYaHay
import com.jvillada.movi.shared.model.PrimerPaso
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TIPOS_DE_CUENTA_DE_DINERO
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.periodoElegidoEn
import com.jvillada.movi.shared.model.primerosPasos
import com.jvillada.movi.ui.accounts.EstadoDelFormularioDeCuenta
import com.jvillada.movi.ui.components.ds.VistaPrevia
import com.jvillada.movi.ui.extractos.TiposDeArchivo
import com.jvillada.movi.ui.extractos.rememberFilePicker
import com.jvillada.movi.ui.papeles.ArchivoCompartido
import com.jvillada.movi.ui.papeles.PapelesCompartidos
import com.jvillada.movi.ui.profile.detalleDelInicioDelPeriodo
import com.jvillada.movi.ui.profile.ejemploCortoDelCorte
import com.jvillada.movi.ui.profile.vistaPreviaDelPeriodo
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** El botón principal de la guía (Empezar, Siguiente, Ir a Inicio). Para las pruebas. */
const val TAG_AVANZAR_EN_LA_GUIA = "primer-uso-avanzar"

/** El botón de texto que pasa al paso siguiente sin guardar nada. Para las pruebas. */
const val TAG_SALTAR_EN_LA_GUIA = "primer-uso-saltar"

/** Lo que dice el botón de texto que salta un paso. */
const val SALTAR_ESTE_PASO = "Saltar este paso"

/** El título grande de la bienvenida. */
const val TITULO_DE_LA_BIENVENIDA = "Te damos la bienvenida a Movi"

/** Lo que dice el botón que sale de la guía sin terminarla. */
const val AHORA_NO = "Ahora no"

/**
 * # La guía «Primer uso», a pantalla completa
 *
 * Ver [PasoDePrimerUso] para qué hace cada paso y [GuiaDePrimerUso] para cuándo se abre. Arriba:
 * «Paso N de M», la barra de progreso y «Ahora no» (sale y la marca descartada). Abajo, **igual en
 * todos los pasos** (vuelta de diseño 3): «Atrás» y el botón principal, que dice «Empezar»,
 * «Siguiente» o «Ir a Inicio» y **se habilita solo cuando hay algo que guardar**; debajo, en todos los
 * pasos de en medio, el botón de texto «Saltar este paso» (vuelta 54: también en las cuentas, que antes
 * dejaban «Siguiente» apagado y solo «Ahora no» como salida).
 *
 * **Los pasos salen de la lista única** (`primerosPasos` en `:core`, vuelta 54): los mismos de la fila
 * del Inicio y de Tú → «Primeros pasos», en el mismo orden; sin captura posible en la plataforma, ese
 * paso no va. Al abrir, la guía lee lo que ya hay (perfil, cuentas, tarjetas, reglas, movimientos), así
 * que el resumen del final dice lo mismo que la fila del Inicio aunque se abra a mitad de camino
 * ([desde]).
 *
 * [pasoInicial] y [modoDeCaptura] existen para las pruebas: una prueba de la web corre en
 * Robolectric, o sea en Android.
 */
@Composable
fun PrimerUsoScreen(
    onNavigate: (Screen) -> Unit,
    pasoInicial: PasoDePrimerUso = PasoDePrimerUso.BIENVENIDA,
    modoDeCaptura: ModoDeCaptura = modoDeCapturaDe(isAndroid),
    desde: PrimerPaso? = null,
) {
    val alcance = rememberCoroutineScope()
    val pasos = remember(modoDeCaptura) { pasosDeLaGuia(modoDeCaptura) }
    val arranque = desde?.let(::pasoDeLaGuia) ?: pasoInicial
    var paso by rememberSaveable { mutableStateOf(if (arranque in pasos) arranque else PasoDePrimerUso.BIENVENIDA) }
    var perfil by remember { mutableStateOf<UserProfile?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Paso «Tu período»: nada elegido hasta que se toca un día (o hasta que el perfil traiga uno).
    var dia by remember { mutableStateOf<Int?>(null) }
    var regla by remember { mutableStateOf(SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR) }
    var periodoGuardado by remember { mutableStateOf<String?>(null) }

    // Lo que se creó en la guía, y lo que ya había (para quien la abre desde «Te faltan N pasos»).
    val cuentas = remember { mutableStateListOf<Account>() }
    val tarjetas = remember { mutableStateListOf<String>() }
    val recurrentes = remember { mutableStateListOf<RecurringRule>() }
    var yaHabia by remember { mutableStateOf(LoQueYaHabia()) }
    var direccionCopiada by remember { mutableStateOf(false) }
    var extractoSubido by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Llegar acá por cualquier camino la pone «en curso»: la fila «Primeros pasos» no compite.
        GuiaDePrimerUso.abrir()
        runCatching { Repositories.wallets.getUserProfile() }.onSuccess { p ->
            perfil = p
            // Un período que ya se había elegido (una cuenta vieja sin cuentas) arranca elegido.
            val propia = siElCorteCaeEnFinDeSemanaDe(p.periodWeekendRule)
            if (p.periodCutoffDay != 1 || propia != SiElCorteCaeEnFinDeSemana.ESE_DIA) {
                dia = p.periodCutoffDay
                regla = propia
            }
        }
        // Lo que ya había. Cada lectura por su cuenta: una que falla deja ese dato en cero, como antes.
        yaHabia = LoQueYaHabia(
            cuentas = runCatching { Repositories.wallets.getAccounts() }.getOrNull()
                ?.filter { it.type in TIPOS_DE_CUENTA_DE_DINERO }.orEmpty(),
            tarjetas = runCatching { Repositories.wallets.getCards() }.getOrNull()?.size ?: 0,
            fijos = runCatching { Repositories.wallets.getRecurringRules() }.getOrNull()?.map { it.id }.orEmpty(),
            movimientos = runCatching { Repositories.wallets.getFinanceSummary(Scope.SELF) }.getOrNull()?.eventCount ?: 0,
        )
    }

    // Android: el permiso concedido es lo único que la guía puede afirmar.
    val permisoDeSms = rememberSmsCaptureReady()
    val estadoDeLaCaptura = when {
        modoDeCaptura == ModoDeCaptura.PERMISOS_DEL_TELEFONO && permisoDeSms -> EstadoDeLaCaptura.ACTIVA
        modoDeCaptura == ModoDeCaptura.SUBIR_UN_EXTRACTO && extractoSubido != null -> EstadoDeLaCaptura.EXTRACTO_SUBIDO
        modoDeCaptura == ModoDeCaptura.SUBIR_UN_EXTRACTO && yaHabia.movimientos > 0 -> EstadoDeLaCaptura.CON_MOVIMIENTOS
        direccionCopiada -> EstadoDeLaCaptura.DIRECCION_COPIADA
        else -> EstadoDeLaCaptura.PENDIENTE
    }
    val cuentasDeDinero = (yaHabia.cuentas.map { it.id } + cuentas.filter { it.type in TIPOS_DE_CUENTA_DE_DINERO }.map { it.id }).toSet().size
    val fijos = (yaHabia.fijos + recurrentes.map { it.id }).toSet().size
    val periodoDelPerfil = perfil?.takeIf { periodoElegidoEn(it.ajustesDelPeriodo()) }
        ?.let { textoDelInicioDelMes(it.periodCutoffDay, siElCorteCaeEnFinDeSemanaDe(it.periodWeekendRule)) }
    // La lista única, con lo que hay ahora: la misma regla que la fila del Inicio.
    val lista = primerosPasos(
        LoQueYaHay(
            periodoElegido = periodoGuardado != null || periodoDelPerfil != null,
            cuentasDeDinero = cuentasDeDinero,
            capturaHecha = estadoDeLaCaptura in setOf(
                EstadoDeLaCaptura.ACTIVA, EstadoDeLaCaptura.EXTRACTO_SUBIDO, EstadoDeLaCaptura.CON_MOVIMIENTOS,
            ),
            fijos = fijos,
        ),
        modoDeCaptura,
    )

    // «Agregar cuenta» va en el pie, así que su estado vive acá (vuelta 54).
    val agregarCuenta = rememberAgregarCuenta()

    fun irA(destino: PasoDePrimerUso) {
        error = null
        paso = destino
    }

    /** Terminada o descartada: se sella en el server y se va al Inicio, haya contestado o no. */
    fun salir() {
        if (guardando) return
        guardando = true
        alcance.launch {
            runCatching { Repositories.wallets.marcarPrimerUso() }
            GuiaDePrimerUso.cerrar()
            guardando = false
            onNavigate(Screen.Dashboard)
        }
    }

    fun avanzar() {
        if (guardando) return
        val siguiente = pasoSiguiente(paso, pasos)
        when {
            paso == PasoDePrimerUso.LISTO -> salir()
            paso == PasoDePrimerUso.PERIODO && dia != null -> {
                val elegido = dia ?: return
                guardando = true
                error = null
                alcance.launch {
                    runCatching { guardarElInicioDelMes(elegido, regla) }
                        .onSuccess {
                            periodoGuardado = textoDelInicioDelMes(elegido, regla)
                            guardando = false
                            siguiente?.let(::irA)
                        }
                        .onFailure {
                            guardando = false
                            error = "No pudimos guardar el día. Revisa tu conexión e inténtalo de nuevo."
                        }
                }
            }
            siguiente != null -> irA(siguiente)
        }
    }

    // El «atrás» del teléfono vuelve un paso, como el botón de abajo.
    val anterior = pasoAnterior(paso, pasos)
    BackHandlerEffect(enabled = anterior != null) { anterior?.let(::irA) }

    val hayAlgoHecho = when (paso) {
        PasoDePrimerUso.PERIODO -> dia != null
        PasoDePrimerUso.CUENTAS -> cuentasDeDinero > 0 || cuentas.isNotEmpty()
        PasoDePrimerUso.CAPTURA -> estadoDeLaCaptura != EstadoDeLaCaptura.PENDIENTE
        PasoDePrimerUso.RECURRENTES -> fijos > 0
        else -> true
    }
    val textoDeAvanzar = when (paso) {
        PasoDePrimerUso.BIENVENIDA -> "Empezar"
        PasoDePrimerUso.LISTO -> "Ir a Inicio"
        else -> "Siguiente"
    }
    // El principal solo cuando hay algo que guardar (o que seguir: la bienvenida y el final). Apagado,
    // «Saltar este paso» está siempre justo debajo.
    val sePuedeAvanzar = hayAlgoHecho

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Encabezado(
            titulo = "Paso ${numeroDelPaso(paso, pasos)} de ${pasos.size}",
            acciones = {
                if (paso != PasoDePrimerUso.LISTO) {
                    Boton(AHORA_NO, onClick = ::salir, variante = VarianteDeBoton.Fantasma, habilitado = !guardando)
                }
            },
        )
        BarraDeProgreso(
            fraccion = progresoDelPaso(paso, pasos),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla),
        )
        val reducido = LocalMovimientoReducido.current
        AnimatedContent(
            targetState = paso,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val adelante = targetState.ordinal > initialState.ordinal
                transicionEntrePantallas(
                    if (adelante) DireccionDeTransicion.APILAR else DireccionDeTransicion.VOLVER,
                    reducido,
                )
            },
            label = "pasos del primer uso",
        ) { p ->
            // El alto del espacio entre la barra de arriba y los botones: la bienvenida se centra en
            // él (y si no entra, se desplaza como cualquier paso).
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val altoDisponible = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Medida.margenDePantalla, vertical = Medida.entreSecciones),
            ) {
                // La bienvenida lleva su propio título, grande y centrado (ver [PasoBienvenida]).
                if (p != PasoDePrimerUso.BIENVENIDA) {
                    Text(p.titulo(modoDeCaptura), style = Movi.textos.titular, color = Movi.colores.texto)
                    Spacer(Modifier.height(Espacio.xs))
                }
                when (p) {
                    PasoDePrimerUso.BIENVENIDA -> PasoBienvenida(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = (altoDisponible - Medida.entreSecciones * 2).coerceAtLeast(Dp.Hairline)),
                    )
                    PasoDePrimerUso.PERIODO -> PasoPeriodo(
                        dia = dia,
                        regla = regla,
                        habilitado = !guardando,
                        onDia = { dia = it },
                        onRegla = { regla = it },
                    )
                    PasoDePrimerUso.CUENTAS -> PasoCuentas(
                        cuentas = (yaHabia.cuentas + cuentas).distinctBy { it.id },
                        tarjetas = tarjetas,
                        formulario = agregarCuenta,
                        onTarjeta = { tarjetas += it },
                    )
                    PasoDePrimerUso.CAPTURA -> PasoCaptura(
                        modo = modoDeCaptura,
                        extractoSubido = extractoSubido,
                        onExtracto = { extractoSubido = it },
                        onCopiada = { direccionCopiada = true },
                    )
                    PasoDePrimerUso.RECURRENTES -> PasoRecurrentes(
                        creados = recurrentes,
                        cuentaPorDefecto = (cuentas + yaHabia.cuentas).firstOrNull { it.type in CUENTAS_QUE_PAGAN }?.id,
                        onCreado = { recurrentes += it },
                    )
                    PasoDePrimerUso.LISTO -> PasoListo(
                        lista,
                        ResumenDelPrimerUso(
                            periodo = periodoGuardado ?: periodoDelPerfil,
                            cuentas = cuentasDeDinero,
                            tarjetas = yaHabia.tarjetas + tarjetas.size,
                            recurrentes = fijos,
                            captura = estadoDeLaCaptura,
                            modo = modoDeCaptura,
                        ),
                    )
                }
            }
            }
        }

        // La barra de abajo: «Atrás» y el botón de avanzar, siempre a la vista.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion),
        ) {
            error?.let {
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                Spacer(Modifier.height(Espacio.xs))
            }
            // Vuelta 54: en el paso de las cuentas, «Agregar cuenta» va en el pie, siempre a la vista
            // (con los bancos en varios renglones quedaba debajo del pliegue a 390 dp).
            if (paso == PasoDePrimerUso.CUENTAS) {
                agregarCuenta.error?.let {
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                    Spacer(Modifier.height(Espacio.xs))
                }
                Boton(
                    AGREGAR_CUENTA,
                    onClick = { agregarCuenta.agregar(alcance) { cuentas += it } },
                    variante = if (sePuedeAvanzar) VarianteDeBoton.Secundario else VarianteDeBoton.Principal,
                    icono = Icons.Rounded.Add,
                    cargando = agregarCuenta.creando,
                    habilitado = agregarCuenta.formulario.sePuedeCrear,
                    anchoCompleto = true,
                    modifier = Modifier.testTag(TAG_AGREGAR_CUENTA_EN_LA_GUIA),
                )
                Spacer(Modifier.height(Espacio.xs))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                if (anterior != null && paso != PasoDePrimerUso.LISTO) {
                    Boton(
                        "Atrás",
                        onClick = { irA(anterior) },
                        variante = VarianteDeBoton.Secundario,
                        habilitado = !guardando,
                        modifier = Modifier.weight(1f),
                    )
                }
                Boton(
                    textoDeAvanzar,
                    onClick = ::avanzar,
                    cargando = guardando,
                    habilitado = sePuedeAvanzar,
                    modifier = Modifier.weight(1f).testTag(TAG_AVANZAR_EN_LA_GUIA),
                )
            }
            // Saltar no es la acción principal: un botón de texto debajo, en todos los pasos de en
            // medio. Pasa al siguiente sin guardar nada (el día elegido y no confirmado queda sin
            // guardar). Vuelta de diseño 5: chico y centrado, sin ancho completo, para que «Siguiente»
            // sea la única acción con peso (a todo el ancho se leía como un tercer botón).
            val siguiente = pasoSiguiente(paso, pasos)
            if (paso.seSaltaConUnBoton && siguiente != null) {
                Boton(
                    SALTAR_ESTE_PASO,
                    onClick = { irA(siguiente) },
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    habilitado = !guardando,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = Espacio.xs)
                        .testTag(TAG_SALTAR_EN_LA_GUIA),
                )
            }
        }
    }
}

/** Lo que el dueño ya tenía al abrir la guía (para el resumen y para tildar la lista única). */
private data class LoQueYaHabia(
    val cuentas: List<Account> = emptyList(),
    val tarjetas: Int = 0,
    val fijos: List<String> = emptyList(),
    val movimientos: Int = 0,
)

/** Lo que dice el botón del pie que agrega la cuenta del formulario. */
const val AGREGAR_CUENTA: String = "Agregar cuenta"

/** El tag de «Agregar cuenta» en el pie de la guía. */
const val TAG_AGREGAR_CUENTA_EN_LA_GUIA: String = "primer-uso-agregar-cuenta"

/** El formulario del paso de las cuentas y su creación, que el pie de la guía dispara. */
@Stable
private class AgregarCuenta(val formulario: EstadoDelFormularioDeCuenta) {
    var creando by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var yaCreada: Account? = null

    /** La creación de siempre ([crearCuentaConSaldo]), como la hoja «Nueva cuenta». */
    fun agregar(alcance: kotlinx.coroutines.CoroutineScope, onCuenta: (Account) -> Unit) {
        if (!formulario.sePuedeCrear || creando) return
        creando = true
        error = null
        val cuenta = formulario.cuentaNueva()
        alcance.launch {
            runCatching { crearCuentaConSaldo(cuenta, yaCreada = yaCreada) { yaCreada = it } }
                .onSuccess { creada ->
                    onCuenta(creada.copy(balance = cuenta.balance))
                    formulario.limpiar()
                    yaCreada = null
                }
                .onFailure {
                    error = if (yaCreada != null) {
                        "La cuenta quedó creada, pero no se pudo guardar el saldo. Toca «$AGREGAR_CUENTA» otra vez."
                    } else {
                        it.toUserMessage()
                    }
                }
            creando = false
        }
    }
}

@Composable
private fun rememberAgregarCuenta(): AgregarCuenta {
    val formulario = rememberEstadoDelFormularioDeCuenta()
    return remember(formulario) { AgregarCuenta(formulario) }
}

/** Los tipos de cuenta de los que sale un pago de cada mes. */
private val CUENTAS_QUE_PAGAN = setOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH)

// ── 1 · Bienvenida ─────────────────────────────────────────────────────────────────────────────

/** El `testTag` de la marca de Movi en la bienvenida. */
const val TAG_MARCA_DE_MOVI = "marca-de-movi"

/**
 * **La bienvenida, con presencia** (vuelta de diseño 3). Era un título chico y tres filas con más de
 * media pantalla vacía debajo. Ahora: la marca de Movi grande arriba ([MarcaDeMovi]), el título en
 * `portada`, el saludo, las tres filas y la nota, **centrado verticalmente** en el espacio entre la
 * barra de arriba y los botones ([modifier] trae ese alto como mínimo). Si no entra —letra muy
 * grande—, se desplaza como cualquier paso.
 */
@Composable
private fun PasoBienvenida(modifier: Modifier) {
    val nombre = SessionManager.userName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarcaDeMovi()
        Spacer(Modifier.height(Medida.entreSecciones))
        Text(
            TITULO_DE_LA_BIENVENIDA,
            style = Movi.textos.portada,
            color = Movi.colores.texto,
            textAlign = TextAlign.Center,
            // A todo el ancho: centrado por el párrafo, no por la caja (que mide el texto justo).
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(
            if (nombre != null) "Hola, $nombre. Esto es lo que hace Movi:" else "Esto es lo que hace Movi:",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            textAlign = TextAlign.Center,
            // A todo el ancho: centrado por el párrafo, no por la caja (que mide el texto justo).
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Medida.entreSecciones))
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Fila(titulo = "Anota lo que pasa en tu plata", inicio = { IconoEnCirculo(Icons.Rounded.EditNote) })
            Separador()
            Fila(titulo = "Te dice cuánto te queda para gastar", inicio = { IconoEnCirculo(Icons.Rounded.Savings) })
            Separador()
            Fila(titulo = "Te propone, pero nunca hace nada sin ti", inicio = { IconoEnCirculo(Icons.Rounded.AutoAwesome) })
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Text(
            "Son unos pasos cortos. Puedes saltar cualquiera y hacerlo después desde «Primeros pasos».",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            textAlign = TextAlign.Center,
            // A todo el ancho: centrado por el párrafo, no por la caja (que mide el texto justo).
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * **La marca de Movi**: la billetera del ícono de la app (el `ic_launcher` de Android y el
 * `movi-192.png` de la web son una billetera), en blanco sobre la marca, en un cuadrado redondeado
 * como el de un ícono de app. Con el ícono del sistema y no con el PNG: un recurso de imagen de
 * Compose todavía no llega a iOS (ver `laPlataformaTraeLasFuentes`), y ahí la app se caería.
 */
@Composable
private fun MarcaDeMovi() {
    Box(
        modifier = Modifier
            .size(Medida.accionRapida * 2)
            .clip(RoundedCornerShape(Radio.xl))
            .background(Movi.colores.marca)
            .testTag(TAG_MARCA_DE_MOVI),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AccountBalanceWallet,
            contentDescription = "Movi",
            tint = Movi.colores.sobreMarca,
            modifier = Modifier.size(Icono.l * 2),
        )
    }
}

// ── 2 · Tu período ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasoPeriodo(
    dia: Int?,
    regla: SiElCorteCaeEnFinDeSemana,
    habilitado: Boolean,
    onDia: (Int) -> Unit,
    onRegla: (SiElCorteCaeEnFinDeSemana) -> Unit,
) {
    Text(
        "¿Qué día te pagan o empieza tu mes? Movi cuenta tu mes desde ese día. Si te pagan el 25, toca el 25.",
        style = Movi.textos.cuerpo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    // Cinco filas de siete, cada día en su parte del ancho: a 360 dp con la letra grande siguen
    // entrando los siete.
    (1..31).chunked(7).forEach { semana ->
        Row(modifier = Modifier.fillMaxWidth()) {
            semana.forEach { d ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DiaParaElegir(d, elegido = d == dia, habilitado = habilitado, onClick = { onDia(d) })
                }
            }
            repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(Espacio.xs))
    }
    Spacer(Modifier.height(Espacio.xs))
    val esElDiaDePago = regla != SiElCorteCaeEnFinDeSemana.ESE_DIA
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = "Es el día que me pagan",
            final = { Casilla(marcada = esElDiaDePago) },
            onClick = if (habilitado) ({
                onRegla(if (esElDiaDePago) SiElCorteCaeEnFinDeSemana.ESE_DIA else SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
            }) else null,
            modifier = Modifier.semantics { selected = esElDiaDePago },
        )
    }
    // Lo que la fila no alcanza a decir va debajo, como nota alineada con su texto.
    Text(
        "Si ese día cae en fin de semana o festivo, tu mes empieza el día hábil anterior, que es cuando te pagan.",
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        modifier = Modifier.padding(start = Medida.margenDePantalla, end = Medida.margenDePantalla, top = Espacio.xs),
    )
    if (dia != null) {
        // Vuelta 54: **la misma vista previa de la hoja de Tú** («Tu período: del 23 de octubre al 24 de
        // noviembre», con `rangoEntre`). Antes decía «Hoy estarías en noviembre de 2026» el 28 de
        // octubre, y el mes siguiente sin el rango confundía.
        val settings = remember(dia, regla) { PeriodSettings(cutoffDay = dia, siCaeEnFinDeSemana = regla) }
        val hoy = remember(settings) { periodoDe(Clock.System.now().toEpochMilliseconds(), settings) }
        val ejemplo = remember(settings, hoy) { ejemploCortoDelCorte(hoy, settings) }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        VistaPrevia(vistaPreviaDelPeriodo(hoy, settings), detalle = detalleDelInicioDelPeriodo(dia, ejemplo))
    }
}

@Composable
private fun DiaParaElegir(dia: Int, elegido: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Medida.avatar)
            .clip(CircleShape)
            .background(if (elegido) coloresDeLoElegido(Movi.colores).first else Movi.colores.capa1)
            .clickable(enabled = habilitado, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = elegido },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dia.toString(),
            style = if (elegido) Movi.textos.cuerpoFuerte else Movi.textos.cuerpoRegular,
            color = if (elegido) coloresDeLoElegido(Movi.colores).second else Movi.colores.texto,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // A lo ancho del círculo: centrado por el párrafo y no por la caja, que a la letra
            // agrandada medía el número con un pelo menos de lo que ocupa.
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── 3 · Tus cuentas ────────────────────────────────────────────────────────────────────────────

/**
 * **Las cuentas, con lo esencial a la vista** (vuelta 54): los bancos y los tipos van cada uno en una
 * fila de chips que se desplaza de lado ([FormularioDeCuenta] `enUnaFila`), y «Agregar cuenta» va en el
 * pie de la guía. A 390 dp se ven el nombre, el saldo y el botón sin desplazar.
 */
@Composable
private fun PasoCuentas(
    cuentas: List<Account>,
    tarjetas: List<String>,
    formulario: AgregarCuenta,
    onTarjeta: (String) -> Unit,
) {
    Text(
        "Las cuentas donde tienes tu plata: el banco, Nequi, el efectivo. Con lo que tienen hoy.",
        style = Movi.textos.cuerpo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Medida.entreSecciones))

    // El mismo bloque y la misma creación que Patrimonio → «Nueva cuenta» (vuelta de diseño 16): ver
    // [FormularioDeCuenta] y crearCuentaConSaldo. La guía no ofrece «Bien»: aquí lo que hace falta es
    // dónde está la plata. El botón que la crea va en el pie.
    Seccion(if (cuentas.isEmpty()) "Agrega una cuenta" else "Agrega otra")
    Superficie(modifier = Modifier.fillMaxWidth()) {
        FormularioDeCuenta(formulario.formulario, habilitado = !formulario.creando, enUnaFila = true)
    }

    if (cuentas.isNotEmpty()) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion("Tus cuentas", conteo = cuentas.size)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            cuentas.forEachIndexed { i, c ->
                if (i > 0) Separador()
                Fila(
                    titulo = c.name,
                    // Lo que el nombre no dice: «Billetera» dice «Efectivo»; «Bancolombia Ahorros», nada.
                    subtitulo = tipoSiElNombreNoLoDice(c.name, rotuloDelTipo(c.type)),
                    inicio = { AvatarDeLaCuenta(c) },
                    final = { Cifra(c.balance) },
                )
            }
        }
    }

    Spacer(Modifier.height(Medida.entreSecciones))
    PasoTarjetas(tarjetas = tarjetas, onTarjeta = onTarjeta)
}

/** Las tarjetas de crédito, opcionales: banco, cupo y día de pago, con la creación de siempre. */
@Composable
private fun PasoTarjetas(tarjetas: List<String>, onTarjeta: (String) -> Unit) {
    val alcance = rememberCoroutineScope()
    var abierta by remember { mutableStateOf(false) }
    var banco by remember { mutableStateOf<String?>(null) }
    var cupo by remember { mutableStateOf<Long?>(null) }
    var diaDePago by remember { mutableStateOf("") }
    var creando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Seccion("Tarjetas de crédito", conteo = tarjetas.size.takeIf { it > 0 })
    Text(
        "Opcional. Si tienes tarjetas, agrégalas con su cupo para que Movi sepa cuánto debes.",
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    if (tarjetas.isNotEmpty()) {
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            tarjetas.forEachIndexed { i, nombre ->
                if (i > 0) Separador()
                Fila(
                    titulo = nombre,
                    subtitulo = "Tarjeta de crédito",
                    inicio = { AvatarDeLaCuenta(nombre = nombre, tipo = AccountType.CREDIT_CARD) },
                )
            }
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
    }
    if (!abierta) {
        Boton(
            "Agregar una tarjeta",
            onClick = { abierta = true },
            variante = VarianteDeBoton.Fantasma,
            icono = Icons.Rounded.Add,
        )
        return
    }
    val diaValido = diaDePago.toIntOrNull()?.takeIf { it in 1..31 }
    Superficie(modifier = Modifier.fillMaxWidth()) {
        RotuloDelCampo("Banco")
        SelectorDeBanco(elegido = banco, onElegir = { banco = it }, enUnaFila = true)
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        MoneyField(value = cupo, onValueChange = { cupo = it }, label = "Cupo")
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        CampoDeTexto(
            "Día de pago (1 a 31)",
            diaDePago,
            { nuevo -> diaDePago = nuevo.filter(Char::isDigit).take(2) },
            marcador = "Ej: 15",
            teclado = KeyboardType.Number,
        )
        error?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            "Agregar tarjeta",
            onClick = {
                val elBanco = banco ?: return@Boton
                val elDia = diaValido ?: return@Boton
                if (creando) return@Boton
                creando = true
                error = null
                val nombre = "Tarjeta $elBanco"
                alcance.launch {
                    runCatching {
                        Repositories.wallets.createCard(
                            CreateCardRequest(
                                name = nombre,
                                terms = CardTerms(accountId = "", bank = elBanco, creditLimit = cupo, paymentDay = elDia),
                            ),
                        )
                    }.onSuccess {
                        onTarjeta(nombre)
                        banco = null; cupo = null; diaDePago = ""; abierta = false
                    }.onFailure { error = it.toUserMessage() }
                    creando = false
                }
            },
            variante = VarianteDeBoton.Secundario,
            cargando = creando,
            habilitado = banco != null && diaValido != null,
            anchoCompleto = true,
        )
        if (banco == null || diaValido == null) {
            Spacer(Modifier.height(Espacio.xs))
            Text(
                if (banco == null) "Falta el banco" else "Falta el día de pago",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
    }
}

private fun rotuloDelTipo(tipo: AccountType): String =
    rotuloDelTipoDeCuenta(tipo) ?: when (tipo) {
        AccountType.CREDIT_CARD -> "Tarjeta de crédito"
        AccountType.LOAN -> "Préstamo"
        else -> "Cuenta"
    }

// ── 4 · Que Movi se entere solo ────────────────────────────────────────────────────────────────

@Composable
private fun PasoCaptura(
    modo: ModoDeCaptura,
    extractoSubido: String?,
    onExtracto: (String) -> Unit,
    onCopiada: () -> Unit,
) {
    when (modo) {
        ModoDeCaptura.PERMISOS_DEL_TELEFONO -> {
            Text(
                "Movi puede leer los mensajes y las notificaciones de tu banco en este teléfono. " +
                    "Lo que llegue te espera en «Por revisar» para que lo confirmes. Es opcional.",
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
            )
            // La misma sección de Ajustes → «Captura del banco»: los permisos se piden en un solo lugar.
            SmsSensorSetupSection(onSynced = {})
        }
        ModoDeCaptura.SUBIR_UN_EXTRACTO -> PasoCapturaConUnExtracto(extractoSubido, onExtracto, onCopiada)
        // Sin captura posible el paso no está en la lista (ver `pasosDeLaGuia`): nunca se llega acá.
        ModoDeCaptura.NINGUNA -> Unit
    }
}

/** Lo que dice el botón que elige el extracto. */
const val SUBIR_UN_EXTRACTO_EN_LA_GUIA: String = "Subir un extracto"

/** El tag del botón que elige el extracto en la guía. */
const val TAG_SUBIR_EXTRACTO_EN_LA_GUIA: String = "primer-uso-subir-extracto"

/**
 * **En la web y en iOS: «Sube un extracto y Movi lo lee»** (vuelta 54). Era el reenvío de correos, que
 * sin la dirección configurada en el server decía «todavía no está disponible» y dejaba el paso sin
 * nada que hacer. Subir un extracto sí existe en todas partes: el archivo va a la misma puerta de
 * papeles que «Subir comprobante» del Inicio ([PapelesCompartidos]), y la hoja «Movi está leyendo…»
 * de App.kt lo toma encima de la guía. El reenvío sigue debajo, **solo si el server tiene la
 * dirección** (la misma pieza de Ajustes, `ReenvioPorCorreo`).
 */
@Composable
private fun PasoCapturaConUnExtracto(extractoSubido: String?, onExtracto: (String) -> Unit, onCopiada: () -> Unit) {
    val elegir = rememberFilePicker(TiposDeArchivo.EXTRACTOS) { nombre, bytes, mime ->
        PapelesCompartidos.recibir(listOf(ArchivoCompartido(nombre, bytes, mime)))
        onExtracto(nombre)
    }
    Text(
        "Sube el extracto de tu banco (el PDF que te mandan cada mes) y Movi lo lee: los movimientos te " +
            "esperan en «Por revisar» para que los confirmes. Es opcional.",
        style = Movi.textos.cuerpo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Medida.entreSecciones))
    if (extractoSubido != null) {
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Fila(
                titulo = "Movi está leyendo tu extracto",
                subtitulo = extractoSubido,
                inicio = { IconoEnCirculo(Icons.Rounded.CheckCircle) },
                final = { Pildora("Listo", TonoDePildora.Exito) },
            )
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
    }
    Boton(
        if (extractoSubido == null) SUBIR_UN_EXTRACTO_EN_LA_GUIA else "Subir otro",
        onClick = elegir,
        variante = VarianteDeBoton.Secundario,
        icono = Icons.Rounded.UploadFile,
        anchoCompleto = true,
        modifier = Modifier.testTag(TAG_SUBIR_EXTRACTO_EN_LA_GUIA),
    )
    val direccion = rememberDireccionDeMovi()
    if (direccion.direccion != null) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Text(
            "¿Tu banco te manda un correo con cada compra? También puedes reenviarlo a Movi.",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        ReenvioPorCorreo(direccion = direccion, onCopiada = onCopiada)
    }
}

// ── 5 · Lo que pagas cada mes ──────────────────────────────────────────────────────────────────

@Composable
private fun PasoRecurrentes(
    creados: List<RecurringRule>,
    cuentaPorDefecto: String?,
    onCreado: (RecurringRule) -> Unit,
) {
    // Qué atajo está abierto: su nombre, o "" para «Agregar otro». `null` = ninguno.
    var abierto by remember { mutableStateOf<String?>(null) }
    Text(
        "Lo que pagas todos los meses. Movi te avisa antes de que venza y lo descuenta de lo que te queda. Es opcional.",
        style = Movi.textos.cuerpo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Medida.entreSecciones))
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        ATAJOS_DE_RECURRENTES.forEachIndexed { i, atajo ->
            if (i > 0) Separador()
            val creado = creados.lastOrNull { it.name == atajo.nombre }
            Fila(
                titulo = atajo.nombre,
                subtitulo = creado?.let { "${formatCOP(it.amount)} · el día ${it.dayOfMonth}" } ?: atajo.categoria,
                final = { if (creado != null) Pildora("Listo", TonoDePildora.Exito) else Chevron() },
                onClick = { abierto = if (abierto == atajo.nombre) null else atajo.nombre },
            )
            if (abierto == atajo.nombre) {
                FormularioDeRecurrente(
                    nombreFijo = atajo.nombre,
                    categoria = atajo.categoria,
                    cuenta = cuentaPorDefecto,
                    onCreado = { onCreado(it); abierto = null },
                )
            }
        }
        val otros = creados.filter { r -> ATAJOS_DE_RECURRENTES.none { it.nombre == r.name } }
        otros.forEach { r ->
            Separador()
            Fila(
                titulo = r.name,
                subtitulo = "${formatCOP(r.amount)} · el día ${r.dayOfMonth}",
                final = { Pildora("Listo", TonoDePildora.Exito) },
            )
        }
        Separador()
        Fila(
            titulo = "Agregar otro",
            inicio = { IconoEnCirculo(Icons.Rounded.Add) },
            onClick = { abierto = if (abierto == "") null else "" },
        )
        if (abierto == "") {
            FormularioDeRecurrente(
                nombreFijo = null,
                categoria = "Otros",
                cuenta = cuentaPorDefecto,
                onCreado = { onCreado(it); abierto = null },
            )
        }
    }
}

/** Monto y día (y el nombre, si es «Agregar otro»), guardado con la creación de reglas de siempre. */
@Composable
private fun FormularioDeRecurrente(
    nombreFijo: String?,
    categoria: String,
    cuenta: String?,
    onCreado: (RecurringRule) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(nombreFijo ?: "") }
    var monto by remember { mutableStateOf<Long?>(null) }
    var dia by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val diaValido = dia.toIntOrNull()?.takeIf { it in 1..31 }
    val falta = when {
        nombre.isBlank() -> "Falta el nombre"
        (monto ?: 0L) <= 0L -> "Falta el monto"
        diaValido == null -> "Falta el día"
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .padding(bottom = Medida.dentroDeSeccion),
    ) {
        if (nombreFijo == null) {
            CampoDeTexto("Nombre", nombre, { nombre = it }, marcador = "Ej: Gimnasio")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
        }
        MoneyField(value = monto, onValueChange = { monto = it }, label = "Cuánto pagas")
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        CampoDeTexto(
            "Qué día del mes (1 a 31)",
            dia,
            { nuevo -> dia = nuevo.filter(Char::isDigit).take(2) },
            marcador = "Ej: 5",
            teclado = KeyboardType.Number,
        )
        error?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            "Guardar",
            onClick = {
                val elMonto = monto ?: return@Boton
                val elDia = diaValido ?: return@Boton
                if (falta != null || guardando) return@Boton
                guardando = true
                error = null
                val regla = RecurringRule(
                    id = "",
                    name = nombre.trim(),
                    category = categoria,
                    amount = elMonto,
                    dayOfMonth = elDia,
                    type = TransactionType.EXPENSE,
                    accountId = cuenta,
                )
                alcance.launch {
                    runCatching { Repositories.wallets.createRecurringRule(regla) }
                        .onSuccess { onCreado(it) }
                        .onFailure { error = it.toUserMessage() }
                    guardando = false
                }
            },
            variante = VarianteDeBoton.Secundario,
            cargando = guardando,
            habilitado = falta == null,
            anchoCompleto = true,
        )
        if (falta != null) {
            Spacer(Modifier.height(Espacio.xs))
            Text(falta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

// ── 6 · Listo ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasoListo(lista: List<EstadoDelPrimerPaso>, resumen: ResumenDelPrimerUso) {
    // Los renglones y si quedaron hechos salen de la lista única: la misma de «Te faltan N pasos».
    val lineas = lineasDelResumen(lista, resumen)
    Text("Esto es lo que quedó:", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        lineas.forEachIndexed { i, linea ->
            if (i > 0) Separador()
            // Como una `Fila`, pero el detalle baja entero en vez de cortarse con «…»: es el resumen
            // de lo que quedó, y «Cada día 25 · o el día hábil anterior» no se puede leer a medias.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medida.altoDeFilaDoble)
                    .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (linea.hecho) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (linea.hecho) "Hecho" else "Pendiente",
                        tint = if (linea.hecho) Movi.colores.entra else Movi.colores.textoApagado,
                        modifier = Modifier.size(Icono.l),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(linea.titulo, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    Text(linea.detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
        }
    }
    if (lineas.any { !it.hecho }) {
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Text(
            "Lo que te faltó queda en el Inicio, en «Primeros pasos», para cuando quieras.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
    }
}

// ── Piezas ─────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun RotuloDelCampo(texto: String) {
    Text(texto, style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
    Spacer(Modifier.height(Espacio.xs))
}

/** Un campo de texto de una línea, con su rótulo arriba, como los de las hojas de crear. */
@Composable
private fun CampoDeTexto(
    rotulo: String,
    valor: String,
    onCambio: (String) -> Unit,
    marcador: String,
    teclado: KeyboardType = KeyboardType.Text,
) {
    RotuloDelCampo(rotulo)
    val forma = RoundedCornerShape(Radio.m)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Movi.colores.capa2)
            .border(Medida.trazo, Movi.colores.borde, forma)
            .padding(horizontal = Espacio.s, vertical = Espacio.s),
    ) {
        // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
        val campo = rememberCampoConSeleccion(valor, onCambio)
        BasicTextField(
            value = campo.valor,
            onValueChange = campo::alCambiar,
            cursorBrush = SolidColor(Movi.colores.texto),
            textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = teclado),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
            decorationBox = { interno ->
                if (valor.isEmpty()) Text(marcador, style = Movi.textos.cuerpo, color = Movi.colores.textoApagado)
                interno()
            },
        )
    }
}
