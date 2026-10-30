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
import com.jvillada.movi.theme.LaMDeMovi
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
import com.jvillada.movi.shared.model.laCuentaYaEstabaEnUso
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
import androidx.compose.runtime.mutableStateMapOf
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.heading
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.SelectorDeDiaDelMes
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.conCifrasOcultas
import com.jvillada.movi.ui.transactions.leerDatosDelDiaADia
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoSiguiente
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
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
    // Vuelta 75 · «¿Cuánto te pagan?» del paso del período: el monto y la cuenta a la que llega (`null` =
    // la primera cuenta de dinero que haya al terminar). La regla se crea al terminar ([crearElSalario]).
    var salario by remember { mutableStateOf<Long?>(null) }
    var cuentaDelSalario by remember { mutableStateOf<String?>(null) }
    var salarioCreado by remember { mutableStateOf(false) }
    // «Listo» con una cifra real: lo que dice el final, cuando se sabe.
    var fraseDelListo by remember { mutableStateOf<String?>(null) }

    // Lo que se creó en la guía, y lo que ya había (para quien la abre desde «Te faltan N pasos»).
    val cuentas = remember { mutableStateListOf<Account>() }
    val tarjetas = remember { mutableStateListOf<String>() }
    val recurrentes = remember { mutableStateListOf<RecurringRule>() }
    var yaHabia by remember { mutableStateOf(LoQueYaHabia()) }
    // Vuelta 75: con un ingreso fijo ya anotado, el período no vuelve a preguntar el salario.
    var tieneIngresoFijo by remember { mutableStateOf(false) }
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
            fijos = runCatching { Repositories.wallets.getRecurringRules() }.getOrNull()?.also { reglas ->
                tieneIngresoFijo = reglas.any { it.type == TransactionType.INCOME }
            }?.map { it.id }.orEmpty(),
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
            // Revisión de la vuelta 55: la misma regla que la fila del Inicio (`periodoHechoEn`): una
            // cuenta que ya estaba en uso y se quedó con el mes de calendario no tiene ese paso pendiente.
            periodoElegido = periodoGuardado != null || periodoDelPerfil != null ||
                laCuentaYaEstabaEnUso(yaHabia.movimientos > 0, yaHabia.cuentas.size, perfil?.primerUsoEn),
            cuentasDeDinero = cuentasDeDinero,
            capturaHecha = estadoDeLaCaptura in setOf(
                EstadoDeLaCaptura.ACTIVA, EstadoDeLaCaptura.EXTRACTO_SUBIDO, EstadoDeLaCaptura.CON_MOVIMIENTOS,
            ),
            fijos = fijos,
        ),
        modoDeCaptura,
    )

    // Los formularios de los pasos viven acá: el pie los mira (revisión de la vuelta 54, ver
    // `BorradoresDeLaGuia.kt`). Con algo escrito, el principal es «Guardar y seguir» y saltar pregunta.
    val formularioDeCuenta = rememberEstadoDelFormularioDeCuenta()
    val borradorDeCuenta = remember(formularioDeCuenta) { BorradorDeCuenta(formularioDeCuenta) { cuentas += it } }
    val borradorDeTarjeta = remember { BorradorDeTarjeta { tarjetas += it } }
    // Un borrador por pago de cada mes que se abrió (clave: el nombre del atajo, o "" para «Agregar
    // otro»): cerrar uno no borra lo escrito, y el pie guarda todos los escritos.
    val borradoresDeRecurrentes = remember { mutableStateMapOf<String, BorradorDeRecurrente>() }
    var recurrenteAbierto by remember { mutableStateOf<String?>(null) }
    // Lo que se hace después de «Descartar» en la pregunta (saltar el paso o salir de la guía).
    var porDescartar by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun cuentaPorDefecto(): String? = (cuentas + yaHabia.cuentas).firstOrNull { it.type in CUENTAS_QUE_PAGAN }?.id

    fun cuentasDeDineroAhora(): Int =
        (yaHabia.cuentas.map { it.id } + cuentas.filter { it.type in TIPOS_DE_CUENTA_DE_DINERO }.map { it.id }).toSet().size

    fun alternarRecurrente(clave: String, nombreFijo: String?, categoria: String) {
        if (recurrenteAbierto == clave) {
            recurrenteAbierto = null
            return
        }
        if (clave !in borradoresDeRecurrentes) {
            borradoresDeRecurrentes[clave] = BorradorDeRecurrente(nombreFijo, categoria, cuenta = ::cuentaPorDefecto) { regla ->
                recurrentes += regla
                borradoresDeRecurrentes.remove(clave)
                if (recurrenteAbierto == clave) recurrenteAbierto = null
            }
        }
        recurrenteAbierto = clave
    }

    fun borradoresDe(p: PasoDePrimerUso): List<Borrador> = when (p) {
        PasoDePrimerUso.CUENTAS -> listOf(borradorDeCuenta, borradorDeTarjeta)
        PasoDePrimerUso.RECURRENTES -> borradoresDeRecurrentes.values.toList()
        else -> emptyList()
    }

    /** Lo escrito en [p] se tira (después de «Descartar»). */
    fun descartarLoEscrito(p: PasoDePrimerUso) {
        when (p) {
            PasoDePrimerUso.CUENTAS -> {
                formularioDeCuenta.limpiar()
                borradorDeTarjeta.banco = null; borradorDeTarjeta.cupo = null; borradorDeTarjeta.diaDePago = ""
                borradorDeTarjeta.abierta = false
            }
            PasoDePrimerUso.RECURRENTES -> {
                borradoresDeRecurrentes.clear()
                recurrenteAbierto = null
            }
            else -> Unit
        }
    }

    /** Si hay algo escrito en el paso, pregunta antes de [accion]; si no, la hace. */
    fun preguntandoSiHayAlgoEscrito(accion: () -> Unit) {
        if (preguntaDeDescartar(borradoresDe(paso)) != null) porDescartar = accion else accion()
    }

    /**
     * Vuelta 75 · **el salario, al terminar**: la regla de ingreso recurrente de siempre
     * (`createRecurringRule`, sin escrituras propias), el día del corte —el que [fechaEsperadaDelIngreso]
     * reconoce como el salario— y la categoría «Salario». Llega a la cuenta elegida o a la primera cuenta de
     * dinero que haya. Una sola vez; si falla no frena la guía (queda en Pagos, «Agregar»).
     */
    suspend fun crearElSalario() {
        val monto = salario?.takeIf { it > 0L } ?: return
        val elDia = dia ?: return
        if (salarioCreado || periodoGuardado == null) return
        val cuenta = cuentaDelSalario ?: cuentaPorDefecto()
        runCatching { Repositories.wallets.createRecurringRule(reglaDelSalario(monto, elDia, cuenta)) }
            .onSuccess { salarioCreado = true }
    }

    /** «Te quedan $X hasta el 24 nov» con las lecturas de siempre del disponible, o «Ya puedes empezar». */
    suspend fun leerLaCifraDelListo() {
        val conCuentas = cuentasDeDineroAhora() > 0
        fraseDelListo = if (!conCuentas) YA_PUEDES_EMPEZAR else runCatching {
            val ahora = Clock.System.now().toEpochMilliseconds()
            val datos = leerDatosDelDiaADia(Repositories.wallets.getUserProfile(), ahora)
            val hoy = epochMillisToAppDate(ahora)
            val teQuedan = disponibleDelInicio(datos, hoy)?.periodo?.teQuedan
            val ultimo = datos.periodoActual?.let { inicioDelPeriodo(periodoSiguiente(it), datos.ajustesDePeriodo).minus(1, DateTimeUnit.DAY) }
            fraseDelFinalDeLaGuia(teQuedan, ultimo, hayCuentas = true)
        }.getOrDefault(YA_PUEDES_EMPEZAR)
    }

    fun irA(destino: PasoDePrimerUso) {
        error = null
        paso = destino
        // Vuelta 75: al llegar al final, el salario se crea y «Listo» lee su cifra.
        if (destino == PasoDePrimerUso.LISTO) {
            fraseDelListo = null
            alcance.launch {
                crearElSalario()
                leerLaCifraDelListo()
            }
        }
    }

    /** Terminada o descartada: se sella en el server y se va al Inicio, haya contestado o no. */
    fun salir() {
        if (guardando) return
        guardando = true
        alcance.launch {
            // Vuelta 75: el salario escrito no se pierde por salir antes del final.
            crearElSalario()
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

    /** «Guardar y seguir»: guarda todo lo escrito del paso con la creación de siempre, y avanza. */
    fun guardarYSeguir(escritos: List<Borrador>) {
        if (guardando) return
        guardando = true
        error = null
        alcance.launch {
            for (b in escritos) {
                if (!b.guardar()) {
                    error = b.error ?: "No pudimos guardar. Revisa tu conexión e inténtalo de nuevo."
                    guardando = false
                    return@launch
                }
            }
            guardando = false
            pasoSiguiente(paso, pasos)?.let(::irA)
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
    // Revisión de la vuelta 54: con un formulario escrito, el principal lo guarda y sigue; incompleto,
    // el pie dice qué falta.
    val escritos = borradoresDe(paso).filter { it.escrito }
    val razon = razonDelPie(escritos)

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // Vuelta 75: «Paso 2 de 6» es un rótulo chico; el título grande es el del paso (abajo), o el de la
        // bienvenida. Antes «Paso 1 de 6» era el título de la pantalla y le ganaba a «Te damos la bienvenida».
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medida.altoDeEncabezado)
                .padding(horizontal = Medida.margenDePantalla),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rotuloDelPaso(numeroDelPaso(paso, pasos), pasos.size),
                style = Movi.textos.apoyoMedio,
                color = Movi.colores.textoMedio,
                modifier = Modifier.weight(1f).testTag(TAG_ROTULO_DEL_PASO),
            )
            if (paso != PasoDePrimerUso.LISTO) {
                Boton(
                    AHORA_NO,
                    onClick = { preguntandoSiHayAlgoEscrito(::salir) },
                    variante = VarianteDeBoton.Fantasma,
                    habilitado = !guardando,
                )
            }
        }
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
                    Text(
                        p.titulo(modoDeCaptura),
                        style = Movi.textos.titular,
                        color = Movi.colores.texto,
                        modifier = Modifier.testTag(TAG_TITULO_DEL_PASO).semantics { heading() },
                    )
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
                        // Vuelta 75: el salario, opcional, si todavía no hay un ingreso fijo anotado.
                        preguntaElSalario = !tieneIngresoFijo,
                        salario = salario,
                        onSalario = { salario = it },
                        cuentas = yaHabia.cuentas,
                        cuentaDelSalario = cuentaDelSalario,
                        onCuentaDelSalario = { cuentaDelSalario = it },
                    )
                    PasoDePrimerUso.CUENTAS -> PasoCuentas(
                        cuentas = (yaHabia.cuentas + cuentas).distinctBy { it.id },
                        tarjetas = tarjetas,
                        borrador = borradorDeCuenta,
                        tarjeta = borradorDeTarjeta,
                    )
                    PasoDePrimerUso.CAPTURA -> PasoCaptura(
                        modo = modoDeCaptura,
                        extractoSubido = extractoSubido,
                        onExtracto = { extractoSubido = it },
                        onCopiada = { direccionCopiada = true },
                    )
                    PasoDePrimerUso.RECURRENTES -> PasoRecurrentes(
                        creados = recurrentes,
                        borradores = borradoresDeRecurrentes,
                        abierto = recurrenteAbierto,
                        onAlternar = ::alternarRecurrente,
                    )
                    PasoDePrimerUso.LISTO -> PasoListo(
                        fraseDelListo,
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
                    if (escritos.isNotEmpty()) GUARDAR_Y_SEGUIR else textoDeAvanzar,
                    onClick = { if (escritos.isNotEmpty()) guardarYSeguir(escritos) else avanzar() },
                    cargando = guardando,
                    habilitado = if (escritos.isNotEmpty()) razon == null else sePuedeAvanzar,
                    modifier = Modifier.weight(1f).testTag(TAG_AVANZAR_EN_LA_GUIA),
                )
            }
            // Lo que falta para guardar lo escrito, en una línea centrada justo debajo (como `PieDeHoja`).
            if (razon != null) {
                Text(
                    razon,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs).testTag(TAG_RAZON_DEL_PIE),
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
                    // Con algo escrito pregunta antes: el arriendo escrito no se pierde por un toque.
                    onClick = { preguntandoSiHayAlgoEscrito { descartarLoEscrito(paso); irA(siguiente) } },
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

    // «¿Descartar el arriendo que escribiste?»: antes de saltar o salir con algo escrito.
    porDescartar?.let { accion ->
        ConfirmarEnHoja(
            pregunta = preguntaDeDescartar(borradoresDe(paso)) ?: "¿Descartar lo que escribiste?",
            detalle = "Si sigues sin guardarlo, no queda anotado.",
            textoConfirmar = "Descartar",
            ocupado = false,
            onConfirmar = {
                porDescartar = null
                accion()
            },
            onCancelar = { porDescartar = null },
        )
    }
    }
}

/** El tag de la razón del pie («Falta el monto del arriendo»). */
const val TAG_RAZON_DEL_PIE: String = "primer-uso-razon-del-pie"

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
 * **La marca de Movi**: el ícono de la app (vuelta 65), la «m» de Figtree ExtraBold en tinta sobre el
 * amarillo, en un cuadrado redondeado. La «m» es [LaMDeMovi], el mismo contorno del ícono de Android
 * y de la web hecho vector (no texto): así también en iOS, que todavía no tiene las fuentes propias.
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
            imageVector = LaMDeMovi,
            contentDescription = "Movi",
            tint = Movi.colores.sobreMarca,
            modifier = Modifier.fillMaxSize(),
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
    preguntaElSalario: Boolean,
    salario: Long?,
    onSalario: (Long?) -> Unit,
    cuentas: List<Account>,
    cuentaDelSalario: String?,
    onCuentaDelSalario: (String?) -> Unit,
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
        if (preguntaElSalario) {
            Spacer(Modifier.height(Medida.entreSecciones))
            SalarioDelPeriodo(dia, salario, onSalario, cuentas, cuentaDelSalario, onCuentaDelSalario, habilitado)
        }
    }
}

/**
 * Vuelta 75 · **«¿Cuánto te pagan? (opcional)»**: sin el salario, la caja supone que no te entra nada y
 * todo sale en rojo. La cifra y la cuenta a la que llega —con las cuentas que ya hay; si no hay ninguna,
 * la primera que agregues en el paso siguiente—. La regla se crea al terminar la guía, con la creación de
 * siempre, el día del corte.
 */
@Composable
private fun SalarioDelPeriodo(
    dia: Int,
    salario: Long?,
    onSalario: (Long?) -> Unit,
    cuentas: List<Account>,
    cuentaDelSalario: String?,
    onCuentaDelSalario: (String?) -> Unit,
    habilitado: Boolean,
) {
    Seccion(PREGUNTA_DEL_SALARIO)
    MoneyField(
        value = salario,
        onValueChange = onSalario,
        label = "Cuánto te pagan",
        modifier = Modifier.testTag(TAG_SALARIO_EN_LA_GUIA),
    )
    Spacer(Modifier.height(Espacio.xs))
    val deDinero = cuentas.filter { it.type in CUENTAS_QUE_PAGAN }
    if (deDinero.isEmpty()) {
        Text(
            "Llega el día $dia a la primera cuenta que agregues en el paso siguiente.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
    } else {
        Text("¿A qué cuenta te llega?", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.xs))
        val elegida = cuentaDelSalario ?: deDinero.first().id
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            deDinero.forEach { c ->
                PildoraDeFiltro(
                    texto = c.name,
                    seleccionada = c.id == elegida,
                    onClick = { if (habilitado) onCuentaDelSalario(c.id) },
                )
            }
        }
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
 * fila de chips que se desplaza de lado ([FormularioDeCuenta] `enUnaFila`), y «Agregar cuenta» va
 * **dentro de la tarjeta del formulario**, debajo de «Saldo de hoy» (revisión de la vuelta 54: fijo en el
 * pie quedaba justo debajo de «Tarjetas de crédito» y se leía como agregar una tarjeta). A 390 dp se ven
 * el nombre, el saldo y el botón sin desplazar. Con algo escrito, el pie ofrece «Guardar y seguir».
 */
@Composable
private fun PasoCuentas(
    cuentas: List<Account>,
    tarjetas: List<String>,
    borrador: BorradorDeCuenta,
    tarjeta: BorradorDeTarjeta,
) {
    val alcance = rememberCoroutineScope()
    Text(
        "Las cuentas donde tienes tu plata: el banco, Nequi, el efectivo. Con lo que tienen hoy.",
        style = Movi.textos.cuerpo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Medida.entreSecciones))

    // El mismo bloque y la misma creación que Patrimonio → «Nueva cuenta» (vuelta de diseño 16): ver
    // [FormularioDeCuenta] y crearCuentaConSaldo. La guía no ofrece «Bien»: aquí lo que hace falta es
    // dónde está la plata.
    Seccion(if (cuentas.isEmpty()) "Agrega una cuenta" else "Agrega otra")
    Superficie(modifier = Modifier.fillMaxWidth()) {
        FormularioDeCuenta(borrador.formulario, habilitado = !borrador.creando, enUnaFila = true)
        borrador.error?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            AGREGAR_CUENTA,
            onClick = { alcance.launch { borrador.guardar() } },
            variante = VarianteDeBoton.Secundario,
            icono = Icons.Rounded.Add,
            cargando = borrador.creando,
            habilitado = borrador.formulario.sePuedeCrear,
            anchoCompleto = true,
            modifier = Modifier.testTag(TAG_AGREGAR_CUENTA_EN_LA_GUIA),
        )
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
    PasoTarjetas(tarjetas = tarjetas, borrador = tarjeta)
}

/** Las tarjetas de crédito, opcionales: banco, cupo y día de pago, con la creación de siempre. */
@Composable
private fun PasoTarjetas(tarjetas: List<String>, borrador: BorradorDeTarjeta) {
    val alcance = rememberCoroutineScope()

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
    if (!borrador.abierta) {
        Boton(
            "Agregar una tarjeta",
            onClick = { borrador.abierta = true },
            variante = VarianteDeBoton.Fantasma,
            icono = Icons.Rounded.Add,
        )
        return
    }
    Superficie(modifier = Modifier.fillMaxWidth()) {
        RotuloDelCampo("Banco")
        SelectorDeBanco(elegido = borrador.banco, onElegir = { borrador.banco = it }, enUnaFila = true)
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        MoneyField(value = borrador.cupo, onValueChange = { borrador.cupo = it }, label = "Cupo")
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        CampoDeTexto(
            "Día de pago (1 a 31)",
            borrador.diaDePago,
            { nuevo -> borrador.diaDePago = nuevo.filter(Char::isDigit).take(2) },
            marcador = "Ej: 15",
            teclado = KeyboardType.Number,
        )
        borrador.error?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            "Agregar tarjeta",
            onClick = { alcance.launch { borrador.guardar() } },
            variante = VarianteDeBoton.Secundario,
            cargando = borrador.creando,
            habilitado = borrador.queFalta == null,
            anchoCompleto = true,
        )
        borrador.queFalta?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
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
    borradores: Map<String, BorradorDeRecurrente>,
    abierto: String?,
    onAlternar: (clave: String, nombreFijo: String?, categoria: String) -> Unit,
) {
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
                onClick = { onAlternar(atajo.nombre, atajo.nombre, atajo.categoria) },
            )
            val borrador = borradores[atajo.nombre]
            if (abierto == atajo.nombre && borrador != null) FormularioDeRecurrente(borrador)
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
            onClick = { onAlternar(CLAVE_DE_OTRO_PAGO, null, "Otros") },
        )
        val otro = borradores[CLAVE_DE_OTRO_PAGO]
        if (abierto == CLAVE_DE_OTRO_PAGO && otro != null) FormularioDeRecurrente(otro)
    }
}

/** La clave del borrador de «Agregar otro». */
private const val CLAVE_DE_OTRO_PAGO = ""

/**
 * Monto y día (y el nombre, si es «Agregar otro»). «Guardar» de adentro guarda este y deja seguir
 * agregando; el pie ofrece «Guardar y seguir» con lo escrito.
 */
@Composable
private fun FormularioDeRecurrente(borrador: BorradorDeRecurrente) {
    val alcance = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .padding(bottom = Medida.dentroDeSeccion),
    ) {
        if (borrador.nombreFijo == null) {
            CampoDeTexto("Nombre", borrador.nombre, { borrador.nombre = it }, marcador = "Ej: Gimnasio")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
        }
        MoneyField(value = borrador.monto, onValueChange = { borrador.monto = it }, label = "Cuánto pagas")
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        // Vuelta 75: el día se elige, no se escribe: el selector de la vuelta 72 (`SelectorDeDiaDelMes`).
        RotuloDelCampo("Qué día del mes")
        SelectorDeDiaDelMes(
            elegido = borrador.diaValido,
            onElegir = { borrador.dia = it.toString() },
            habilitado = !borrador.guardando,
        )
        borrador.error?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            "Guardar",
            onClick = { alcance.launch { borrador.guardar() } },
            variante = VarianteDeBoton.Secundario,
            cargando = borrador.guardando,
            habilitado = borrador.queFalta == null,
            anchoCompleto = true,
        )
        borrador.queFalta?.let { falta ->
            Spacer(Modifier.height(Espacio.xs))
            Text(falta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

// ── 6 · Listo ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasoListo(frase: String?, lista: List<EstadoDelPrimerPaso>, resumen: ResumenDelPrimerUso) {
    // Vuelta 75: el final dice una cifra real —«Te quedan $X hasta el 24 nov»—, la del héroe de Inicio, o
    // «Ya puedes empezar» sin cuentas. Mientras se lee, un esqueleto del mismo alto.
    if (frase != null) {
        Text(
            conCifrasOcultas(frase),
            style = Movi.textos.titulo,
            color = Movi.colores.texto,
            modifier = Modifier.testTag(TAG_CIFRA_DEL_LISTO),
        )
    } else {
        LineaEsqueleto(fraccionDelAncho = 0.7f, estilo = Movi.textos.titulo)
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
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
