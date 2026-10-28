package com.jvillada.movi.ui.espacios

import androidx.compose.material.icons.rounded.Add
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import com.jvillada.movi.ui.components.PanelDeTablero
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.automirrored.rounded.Send
import com.jvillada.movi.platform.ComoSalioElMensaje
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.ui.destinos.RenglonDePersonasYComercios
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.MovimientoSinSubir
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.ds.AvatarDePersona
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
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
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Tags para las pruebas. */
const val TAG_SALDO_DEL_ESPACIO: String = "espacios:saldo"
const val TAG_MARCAR_COMO_SALDADO: String = "espacios:marcar-como-saldado"
const val TAG_PEDIRLE: String = "espacios:pedirle"
const val TAG_MENSAJE_COPIADO: String = "espacios:mensaje-copiado"
const val TAG_GASTO_DEL_ESPACIO: String = "espacios:gasto"
const val TAG_LO_QUE_PAGA_OTRA_PERSONA: String = "espacios:lo-que-paga-otra-persona"
const val TAG_ANOTAR_LO_QUE_PAGO_OTRO: String = "espacios:anotar-lo-que-pago-otro"

/** Lo que una escritura falló, dicho para el dueño. */
internal fun mensajeDelEspacio(e: Throwable): String = when {
    e is ApiException -> e.toUserMessage()
    // El movimiento no subió y el server dijo por qué (vuelta de diseño 24): eso, no «sin conexión».
    e is MovimientoSinSubir && e.rechazo != null -> "El servidor no recibió ese movimiento: ${e.rechazo}"
    else -> ESPACIOS_SIN_CONEXION
}

/** Lo que la pestaña lee: los espacios con su detalle y los créditos que paga otra persona. */
private class LecturaDeEspacios(
    val detalles: List<DetalleDelEspacio>,
    val queOtroPaga: List<CreditSummary>,
)

/**
 * # Espacios (R3)
 *
 * «¿Qué tengo y qué debo con los demás?». Con un solo espacio —el caso de hoy, «Casa con Caro»— la
 * pestaña lo muestra entero, como la maqueta p1; con varios, una fila por espacio que lleva a su
 * pantalla. Abajo, «Lo que paga otra persona»: las cuotas de tus créditos que paga alguien más
 * (`CreditTerms.paidBy`, el mismo dato de siempre, solo lectura).
 */
@Composable
fun EspaciosScreen(onNavigate: (Screen) -> Unit) {
    val refresco = LocalRefreshTick.current
    var reintento by remember { mutableStateOf(0) }
    var lectura by remember { mutableStateOf<LecturaDeEspacios?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creando by remember { mutableStateOf(false) }
    // Los nombres de «Personas y comercios», para el subtítulo de su fila. Lo recordado al primer
    // cuadro, como en todas partes; si falla, la fila invita a guardar (no afirma nada de plata).
    val destinos = rememberLectura(ClaveDeLectura.Destinos, reintento = reintento) { Repositories.wallets.getDestinos() }

    LaunchedEffect(refresco, reintento) {
        error = null
        try {
            val espacios = Repositories.wallets.getEspacios()
            val detalles = espacios.map { Repositories.wallets.getDetalleDelEspacio(it.id) }
            val creditos = runCatching { Repositories.wallets.getCredits() }.getOrDefault(emptyList())
            lectura = LecturaDeEspacios(detalles, creditos.filter { !it.terms?.paidBy.isNullOrBlank() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = mensajeDelEspacio(e)
        }
    }

    // Vuelta 33: con un solo espacio a la vista, en pantalla ancha va en dos columnas (ver
    // [espacioEnDosColumnas]); con varios, sin ninguno o sin poder leerlos, en una columna de lectura.
    val unSoloEspacio = lectura?.detalles?.size == 1
    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
    PanelDeTablero(enDosColumnas = { espacioEnDosColumnas(it) && unSoloEspacio }) { dosColumnas ->
    Column(modifier = Modifier.fillMaxSize()) {
        // Con un espacio o más, crear otro va arriba (antes solo se podía desde el vacío: con uno ya
        // creado no había cómo hacer el segundo). Sin ninguno, el vacío ya lo ofrece.
        MinScreenHeader(
            title = "Espacios",
            leading = HeaderLeading.Avatar(onNavigate),
            accion = if (lectura?.detalles?.isNotEmpty() == true) {
                AccionDeEncabezado("Nuevo espacio", Icons.Rounded.Add, onClick = { creando = true })
            } else null,
        )
        // Lo que va al final, debajo del espacio (en dos columnas, al final de la derecha).
        val alFinal: @Composable ColumnScope.() -> Unit = {
            val actual = lectura
            if (actual != null && actual.queOtroPaga.isNotEmpty()) LoQuePagaOtraPersona(actual.queOtroPaga)
            // Ola R4: la casa de los terceros. Al final, siempre: lo que tienes con los demás que no
            // está en un espacio (Caro, tu papá, los comercios). Desde la vuelta 31 es la única
            // puerta (Ajustes ya no la repite); ver [RenglonDePersonasYComercios].
            RenglonDePersonasYComercios(destinos = destinos.valor, onClick = { onNavigate(Screen.Destinos()) })
        }
        val actual = lectura
        if (dosColumnas && actual != null && actual.detalles.size == 1) {
            CuerpoDelEspacio(
                detalle = actual.detalles.single(),
                onCambio = { reintento++ },
                onVerTodo = { onNavigate(Screen.Espacio(actual.detalles.single().espacio.id)) },
                enDosColumnas = true,
                alFinalDeLaDerecha = alFinal,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Medida.margenDePantalla)
                    .padding(bottom = Medida.entreSecciones),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            ) {
                when {
                    actual == null && error != null -> Vacio(
                        titulo = "No pudimos leer tus espacios",
                        detalle = error,
                        accion = "Reintentar",
                        onAccion = { reintento++ },
                    )
                    actual == null -> Column { repeat(3) { EsqueletoDeFila() } }
                    actual.detalles.isEmpty() -> Vacio(
                        titulo = "Comparte gastos con alguien",
                        detalle = "Crea un espacio, como «Casa con Caro»: cuenta solo tu parte y Movi lleva quién le debe a quién.",
                        accion = "Crear espacio",
                        onAccion = { creando = true },
                        icono = Icons.Rounded.Groups,
                    )
                    actual.detalles.size == 1 -> CuerpoDelEspacio(
                        detalle = actual.detalles.single(),
                        onCambio = { reintento++ },
                        onVerTodo = { onNavigate(Screen.Espacio(actual.detalles.single().espacio.id)) },
                    )
                    else -> ListaDeEspacios(actual.detalles, onAbrir = { onNavigate(Screen.Espacio(it)) })
                }
                alFinal()
            }
        }
    }
    } // PanelDeTablero
    }

    if (creando) {
        HojaNuevoEspacio(
            onDismiss = { creando = false },
            onCreado = {
                creando = false
                reintento++
            },
            onIrATerceros = {
                creando = false
                onNavigate(Screen.Destinos())
            },
        )
    }
}

/**
 * La pantalla de un espacio (desde la lista, o «Ver todos» los gastos). [pagoOtro] abre de entrada
 * la hoja «Pagó otro» (la pide Agregar).
 */
@Composable
fun EspacioScreen(id: String, pagoOtro: Boolean) {
    val refresco = LocalRefreshTick.current
    var reintento by remember { mutableStateOf(0) }
    var detalle by remember { mutableStateOf<DetalleDelEspacio?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id, refresco, reintento) {
        error = null
        try {
            detalle = Repositories.wallets.getDetalleDelEspacio(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = mensajeDelEspacio(e)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
    PanelDeTablero(enDosColumnas = { espacioEnDosColumnas(it) && detalle != null }) { dosColumnas ->
    Column(modifier = Modifier.fillMaxSize()) {
        MinScreenHeader(title = detalle?.espacio?.nombre ?: "Espacio", leading = HeaderLeading.Back(fallback = Screen.Espacios))
        val actual = detalle
        if (dosColumnas && actual != null) {
            CuerpoDelEspacio(
                detalle = actual,
                onCambio = { reintento++ },
                onVerTodo = null,
                pagoOtroDeEntrada = pagoOtro,
                enDosColumnas = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Medida.margenDePantalla)
                    .padding(bottom = Medida.entreSecciones),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            ) {
                when {
                    actual == null && error != null -> Vacio(
                        titulo = "No pudimos leer este espacio",
                        detalle = error,
                        accion = "Reintentar",
                        onAccion = { reintento++ },
                    )
                    actual == null -> Column { repeat(3) { EsqueletoDeFila() } }
                    else -> CuerpoDelEspacio(
                        detalle = actual,
                        onCambio = { reintento++ },
                        onVerTodo = null,
                        pagoOtroDeEntrada = pagoOtro,
                    )
                }
            }
        }
    }
    } // PanelDeTablero
    }
}

/** Cuántos gastos se listan en la pestaña antes de «Ver todos». */
private const val GASTOS_A_LA_VISTA: Int = 8

/**
 * Todo lo de un espacio, en el orden de la regla de secciones: las propuestas arriba, después el
 * encabezado con el gasto del período, «Quién puso qué», el saldo con «Marcar como saldado» y la
 * lista de gastos. [onVerTodo] `null` = ya se están viendo todos.
 */
@Composable
internal fun CuerpoDelEspacio(
    detalle: DetalleDelEspacio,
    onCambio: () -> Unit,
    onVerTodo: (() -> Unit)?,
    pagoOtroDeEntrada: Boolean = false,
    /**
     * Vuelta 33: en pantalla ancha, el espacio, «Quién puso qué» y el saldo a la izquierda, y los
     * gastos (más [alFinalDeLaDerecha]) a la derecha, cada columna con su scroll. `false` = una
     * columna: lo emite todo en la columna de quien llama, como siempre.
     */
    enDosColumnas: Boolean = false,
    alFinalDeLaDerecha: @Composable ColumnScope.() -> Unit = {},
    /** El de las dos columnas (con `weight`): en una columna no se usa. */
    modifier: Modifier = Modifier,
) {
    val espacio = detalle.espacio
    val coroutine = rememberCoroutineScope()
    var saldando by remember { mutableStateOf(false) }
    var pagoOtro by remember(espacio.id) { mutableStateOf(pagoOtroDeEntrada) }
    var descartadas by remember(espacio.id) { mutableStateOf(emptySet<String>()) }
    var errorDePropuesta by remember { mutableStateOf<String?>(null) }
    // Vuelta de diseño 26 · «Pedirle a Caro» y saldar con confirmación.
    var confirmandoSaldado by remember(espacio.id) { mutableStateOf(false) }
    var saldandoSinVincular by remember(espacio.id) { mutableStateOf(false) }
    var mensajeCopiado by remember(espacio.id) { mutableStateOf<String?>(null) }
    val hoja = hojaDeCompartir()
    val uriHandler = LocalUriHandler.current

    // ── Lo que Movi propone, el espacio, quién puso qué y el saldo: la izquierda en dos columnas. ──
    val izquierda: @Composable ColumnScope.() -> Unit = {
        // ── Lo que Movi propone: vincular una transferencia que salda. ──
        val propuestas = detalle.propuestas.filterNot { it.eventoId in descartadas }
        propuestas.forEach { p ->
            TarjetaDePropuesta(
                icono = Icons.Rounded.SwapHoriz,
                titulo = preguntaDeSaldar(espacio, if (p.a == MIEMBRO_YO) p.de else p.a, entra = p.a == MIEMBRO_YO),
                cifra = p.monto,
                cuerpo = p.descripcion,
                accion = "Sí, saldar",
                onAccion = {
                    coroutine.launch {
                        try {
                            Repositories.wallets.liquidarEspacio(espacio.id, liquidacionDe(p))
                            onCambio()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            errorDePropuesta = mensajeDelEspacio(e)
                        }
                    }
                },
                accionSecundaria = "No",
                onAccionSecundaria = { descartadas = descartadas + p.eventoId },
            )
        }
        errorDePropuesta?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }

        EncabezadoDelEspacio(detalle)
        QuienPusoQue(detalle)
        SaldoDelEspacioTarjeta(
            detalle,
            onPedir = {
                mensajeParaPedir(detalle)?.let { mensaje ->
                    mensajeCopiado = if (hoja.mandarMensaje(mensaje) == ComoSalioElMensaje.COPIADO) mensaje else null
                }
            },
            onSaldar = { saldando = true },
            // Con transferencias que pueden ser el pago, la hoja de siempre (se elige cuál vincular); sin
            // ninguna, la pregunta antes de dejar el espacio en cero.
            onSaldadoSecundario = { if (detalle.propuestas.isEmpty()) confirmandoSaldado = true else saldando = true },
            mensajeCopiado = mensajeCopiado,
            onAbrirWhatsApp = { mensajeCopiado?.let { runCatching { uriHandler.openUri(enlaceDeWhatsApp(it)) } } },
        )
    }
    val derecha: @Composable ColumnScope.() -> Unit = {
        GastosDelEspacio(detalle, onPagoOtro = { pagoOtro = true }, onVerTodo = onVerTodo, onCambio = onCambio)
    }
    if (enDosColumnas) {
        // Cada columna con su propio scroll, como Patrimonio: los rellenos suman los 48 de
        // [RELLENO_DE_DOS_COLUMNAS] (16 afuera de cada una y 8 + 8 entre las dos).
        Row(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Medida.margenDePantalla, end = Medida.margenDePantalla / 2, bottom = Medida.entreSecciones)
                    .testTag(TAG_COLUMNA_IZQUIERDA_DEL_ESPACIO),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            ) { izquierda() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Medida.margenDePantalla / 2, end = Medida.margenDePantalla, bottom = Medida.entreSecciones)
                    .testTag(TAG_COLUMNA_DERECHA_DEL_ESPACIO),
                verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
            ) {
                derecha()
                alFinalDeLaDerecha()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones)) {
            izquierda()
            derecha()
        }
    }

    if (saldando) {
        HojaSaldar(
            detalle = detalle,
            onDismiss = { saldando = false },
            onSaldado = {
                saldando = false
                onCambio()
            },
        )
    }
    val confirmacion = confirmacionDeSaldado(espacio, detalle.saldo)
    val reembolso = fraseDelSaldo(espacio, detalle.saldo).reembolso
    if (confirmandoSaldado && confirmacion != null && reembolso != null) {
        // El id se elige al abrir la pregunta, como en [HojaSaldar]: dos toques son un saldado, y
        // un saldado de otro día es otra liquidación (no pisa esta).
        val idDelSaldado = remember { newId("liq") }
        ConfirmarEnHoja(
            pregunta = confirmacion.first,
            detalle = confirmacion.second,
            textoConfirmar = "Sí, ya me pagó",
            ocupado = saldandoSinVincular,
            varianteConfirmar = VarianteDeBoton.Principal,
            onCancelar = { if (!saldandoSinVincular) confirmandoSaldado = false },
            // La misma operación de siempre: la liquidación del reembolso, sin vincular movimiento
            // (como «Marcar como saldado» sin elegir transferencia).
            onConfirmar = {
                if (!saldandoSinVincular) {
                    saldandoSinVincular = true
                    errorDePropuesta = null
                    coroutine.launch {
                        try {
                            Repositories.wallets.liquidarEspacio(
                                espacio.id,
                                LiquidacionDelEspacio(
                                    id = idDelSaldado,
                                    de = reembolso.de,
                                    a = reembolso.a,
                                    monto = reembolso.monto,
                                    fecha = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                ),
                            )
                            confirmandoSaldado = false
                            mensajeCopiado = null
                            onCambio()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            errorDePropuesta = mensajeDelEspacio(e)
                            confirmandoSaldado = false
                        } finally {
                            saldandoSinVincular = false
                        }
                    }
                }
            },
        )
    }
    if (pagoOtro) {
        HojaPagoOtro(
            espacio = espacio,
            onDismiss = { pagoOtro = false },
            onGuardado = {
                pagoOtro = false
                onCambio()
            },
        )
    }
}

/** La liquidación que propone [p]: el monto de la transferencia, vinculada. */
internal fun liquidacionDe(p: PropuestaDeSaldar): LiquidacionDelEspacio =
    LiquidacionDelEspacio(id = newId("liq"), de = p.de, a = p.a, monto = p.monto, fecha = p.fecha, eventoId = p.eventoId)

/** Los avatares, el nombre, quiénes comparten y el gasto del período. */
@Composable
private fun EncabezadoDelEspacio(detalle: DetalleDelEspacio) {
    val espacio = detalle.espacio
    Superficie {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xxs)) {
                espacio.miembros.forEach { m -> AvatarDePersona(m.nombre, tono = tonoDe(m.id)) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(espacio.nombre, style = Movi.textos.titulo, color = Movi.colores.texto, maxLines = 1)
                Text(quienesComparten(espacio), style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
            }
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Text("Gastos de este período", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Cifra(detalle.delPeriodo.total, VarianteDeCifra.Grande)
    }
}

/** El dueño en la marca; los demás en neutro. */
private fun tonoDe(miembroId: String): TonoDeIcono = if (miembroId == MIEMBRO_YO) TonoDeIcono.Marca else TonoDeIcono.Info

/** «Quién puso qué»: una barra por miembro, con la marca de lo que le tocaba. */
@Composable
private fun QuienPusoQue(detalle: DetalleDelEspacio) {
    val espacio = detalle.espacio
    val periodo = detalle.delPeriodo
    if (periodo.total <= 0L) return
    Column {
        Seccion(titulo = "Quién puso qué")
        Superficie {
            Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                periodo.miembros.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.s)) {
                        AvatarDePersona(nombreDelPagador(espacio, m.miembroId), tono = tonoDe(m.miembroId))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xxs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (m.miembroId == MIEMBRO_YO) "Tú" else nombreEnElEspacio(espacio, m.miembroId),
                                    style = Movi.textos.cuerpo,
                                    color = Movi.colores.texto,
                                    modifier = Modifier.weight(1f),
                                )
                                Cifra(m.puso)
                            }
                            BarraDeProgreso(
                                fraccion = m.puso.toFloat() / periodo.total,
                                marca = m.leTocaba.toFloat() / periodo.total,
                            )
                        }
                    }
                }
                val tuParte = periodo.miembros.firstOrNull { it.miembroId == MIEMBRO_YO }?.leTocaba ?: 0L
                Text(
                    "${rotuloDeTuParte(espacio)} ${formatCOP(tuParte)}",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
        }
    }
}

/**
 * «Caro te debe $390.000 para quedar 50/50» y lo que se hace con eso (vuelta de diseño 26, ver
 * [PrincipalDelSaldo]): si el otro te debe, «Pedirle a Caro» arma el mensaje y «Marcar como saldado»
 * queda de secundario (`Fantasma`), con confirmación; si tú le debes, «Marcar como saldado» sigue
 * siendo el principal. En la web sin hoja de compartir, el mensaje se copia: «Mensaje copiado» y
 * «Abrir WhatsApp».
 */
@Composable
private fun SaldoDelEspacioTarjeta(
    detalle: DetalleDelEspacio,
    onPedir: () -> Unit,
    onSaldar: () -> Unit,
    onSaldadoSecundario: () -> Unit,
    mensajeCopiado: String?,
    onAbrirWhatsApp: () -> Unit,
) {
    val frase = fraseDelSaldo(detalle.espacio, detalle.saldo)
    Superficie(modifier = Modifier.testTag(TAG_SALDO_DEL_ESPACIO)) {
        Text(frase.titulo, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        if (frase.monto > 0L) Cifra(frase.monto, VarianteDeCifra.Grande)
        frase.apoyo?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio) }
        when (val principal = principalDelSaldo(detalle.espacio, detalle.saldo)) {
            is PrincipalDelSaldo.Pedir -> {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    texto = principal.texto,
                    onClick = onPedir,
                    icono = Icons.AutoMirrored.Rounded.Send,
                    anchoCompleto = true,
                    // Vuelta 33: fuera del teléfono no se estira más que un teléfono grande.
                    modifier = topeDelBoton().align(Alignment.CenterHorizontally).testTag(TAG_PEDIRLE),
                )
                if (mensajeCopiado != null) {
                    Spacer(Modifier.height(EspacioDeDiseno.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
                        modifier = Modifier.fillMaxWidth().testTag(TAG_MENSAJE_COPIADO),
                    ) {
                        Text("Mensaje copiado", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.weight(1f))
                        Boton("Abrir WhatsApp", onClick = onAbrirWhatsApp, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
                    }
                }
                Spacer(Modifier.height(EspacioDeDiseno.xs))
                Boton(
                    texto = "Marcar como saldado",
                    onClick = onSaldadoSecundario,
                    variante = VarianteDeBoton.Fantasma,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag(TAG_MARCAR_COMO_SALDADO),
                )
            }
            PrincipalDelSaldo.MarcarPagado -> {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    texto = "Marcar como saldado",
                    onClick = onSaldar,
                    anchoCompleto = true,
                    modifier = topeDelBoton().align(Alignment.CenterHorizontally).testTag(TAG_MARCAR_COMO_SALDADO),
                )
            }
            PrincipalDelSaldo.Nada -> Unit
        }
    }
}

/**
 * La lista de gastos del espacio, con «Pagó otro» para anotar lo que pagó Caro. Vuelta 31: cada gasto
 * se toca y abre su detalle ([AlTocarUnGasto]). Vuelta 32: el subtítulo empieza por la fecha y cabe en
 * un renglón ([quienPagoElGasto]); si no cabe, lo que se corta es la categoría, al final.
 */
@Composable
private fun GastosDelEspacio(
    detalle: DetalleDelEspacio,
    onPagoOtro: () -> Unit,
    onVerTodo: (() -> Unit)?,
    onCambio: () -> Unit,
) {
    val espacio = detalle.espacio
    val otro = espacio.miembros.firstOrNull { it.id != MIEMBRO_YO }?.nombre
    var abierto by remember(espacio.id) { mutableStateOf<GastoDelEspacio?>(null) }
    Column {
        Seccion(
            titulo = "Gastos",
            conteo = detalle.gastos.size.takeIf { it > 0 },
            accion = if (onVerTodo != null && detalle.gastos.size > GASTOS_A_LA_VISTA) "Ver todos" else null,
            onAccion = onVerTodo,
        )
        if (detalle.gastos.isEmpty()) {
            Text(
                "Reparte un gasto desde su detalle en Movimientos, o anota lo que pagó ${otro ?: "otra persona"}.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        } else {
            val aLaVista = if (onVerTodo != null) detalle.gastos.take(GASTOS_A_LA_VISTA) else detalle.gastos
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                aLaVista.forEachIndexed { i, g ->
                    if (i > 0) Separador()
                    Fila(
                        titulo = tituloDelGasto(g),
                        // Vuelta de diseño 32: un renglón; si no cabe, se corta la categoría (va al final).
                        subtitulo = quienPagoElGasto(espacio, g),
                        inicio = { AvatarDePersona(nombreDelPagador(espacio, g.pagadoPor), tono = tonoDe(g.pagadoPor)) },
                        final = { MontoYTuParte(g.monto, g.partes[MIEMBRO_YO] ?: 0L) },
                        onClick = { abierto = g },
                        onClickLabel = "Ver el gasto",
                        modifier = Modifier.testTag(TAG_GASTO_DEL_ESPACIO),
                    )
                }
            }
        }
        abierto?.let { g -> AlTocarUnGasto(espacio, g, onCerrar = { abierto = null }, onCambio = onCambio) }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            texto = if (otro != null) "Anotar lo que pagó $otro" else "Anotar lo que pagó otra persona",
            onClick = onPagoOtro,
            variante = VarianteDeBoton.Secundario,
            anchoCompleto = true,
            modifier = topeDelBoton().align(Alignment.CenterHorizontally).testTag(TAG_ANOTAR_LO_QUE_PAGO_OTRO),
        )
    }
}

private fun nombreDelPagador(espacio: Espacio, miembroId: String): String =
    espacio.miembros.firstOrNull { it.id == miembroId }?.nombre?.ifBlank { null } ?: nombreEnElEspacio(espacio, miembroId)

/** El monto y, debajo, «Tu parte $X». */
@Composable
internal fun MontoYTuParte(monto: Long, tuParte: Long) {
    Column(horizontalAlignment = Alignment.End) {
        Cifra(monto)
        Text("Tu parte ${formatCOP(tuParte)}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

/** Varios espacios: una fila por espacio, con su saldo dicho corto. */
@Composable
private fun ListaDeEspacios(detalles: List<DetalleDelEspacio>, onAbrir: (String) -> Unit) {
    Superficie(relleno = RellenoDeSuperficie.Lista) {
        detalles.forEachIndexed { i, d ->
            if (i > 0) Separador()
            val frase = fraseDelSaldo(d.espacio, d.saldo)
            Fila(
                titulo = d.espacio.nombre,
                subtitulo = if (frase.monto > 0L) "${frase.titulo} ${formatCOP(frase.monto)}" else frase.titulo,
                inicio = { IconoEnCirculo(Icons.Rounded.Groups) },
                final = { Chevron() },
                onClick = { onAbrir(d.espacio.id) },
            )
        }
    }
}

/** Las cuotas de tus créditos que paga otra persona (`paidBy`), solo lectura. */
@Composable
private fun ColumnScope.LoQuePagaOtraPersona(creditos: List<CreditSummary>) {
    Column(modifier = Modifier.testTag(TAG_LO_QUE_PAGA_OTRA_PERSONA)) {
        Seccion(titulo = "Lo que paga otra persona")
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            creditos.forEachIndexed { i, c ->
                if (i > 0) Separador()
                val quien = c.terms?.paidBy.orEmpty()
                Fila(
                    titulo = c.account.name,
                    subtitulo = "La paga $quien · cada mes",
                    inicio = { AvatarDePersona(quien, tono = TonoDeIcono.Info) },
                    final = { Cifra(c.terms?.installment ?: 0L) },
                )
            }
        }
    }
}
