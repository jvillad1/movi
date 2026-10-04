package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.identificadoresDelDestino
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.MotivoDeDescarte
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.clave
import com.jvillada.movi.shared.model.comoSeDice
import com.jvillada.movi.shared.model.comoSeLeeEnLaFicha
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.soloLosDigitos
import com.jvillada.movi.shared.model.tipoOPersona
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NewItemButton
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.components.TAG_TITULO_DE_FILA_ESQUELETO
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * # «Personas y comercios»
 *
 * El dueño lo pidió así: *«Es la cuenta de Caro, yo le transferí a ella lo de Cotrafa. Guarda esa
 * cuenta como una cuenta no mía pero sí de mi esposa, me interesa tenerla guardada y poder ver los
 * movimientos hacia esa cuenta.»* Y el 4-oct-2026: *«que funcione de forma perfecta… ser ágil y no
 * ofrecer campos basura… un acceso muy fácil de encontrar»*.
 *
 * - **La lista**: cada ficha dice el nombre, cómo la reconoce el banco y **cuánto se le envió este
 *   período** (y lo que te envió) — la pregunta que trae al dueño acá. Personas y comercios aparte.
 * - **Lo que Movi encontró solo** (los sugeridos) va arriba en **una sola línea** que se abre: hasta
 *   esta fecha eran hasta cinco tarjetas grandes encima, y lo guardado quedaba debajo del pliegue.
 * - **La ficha** ([DetalleDelDestinoSheet]) es para leer; **Editar** ([DestinoSheet]) para cambiar,
 *   unir y borrar. Un movimiento de la ficha se abre con un toque ([HojaDelMovimiento]).
 *
 * [abrir] es el id de la ficha que se abre al llegar («Ver su ficha» desde un movimiento).
 *
 * ## Solo en línea, y dicho en voz alta
 *
 * No hay espejo local (ver `WalletRepository.getDestinos`): el total sale de cruzar TODOS los
 * movimientos, y el teléfono tiene una foto que puede estar corrida. Sin señal, la pantalla dice
 * que no pudo leer y ofrece reintentar — no muestra una cifra vieja como si fuera la de hoy.
 */
@Composable
fun DestinosScreen(onNavigate: (Screen) -> Unit, abrir: String? = null) {
    var destinos by remember { mutableStateOf<List<DestinoConocido>>(emptyList()) }
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    var ajustes by remember { mutableStateOf(PeriodSettings()) }
    // Lo que Movi encontró solo. Secundario: si falla, la pantalla sigue con lo guardado.
    var sugeridos by remember { mutableStateOf<List<DestinoSugerido>>(emptyList()) }
    var verSugeridos by remember { mutableStateOf(false) }
    var verTodosLosSugeridos by remember { mutableStateOf(false) }
    var busqueda by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(true) }
    // Ver [NoSePudoLeer]: «Aún no hay ninguna» solo si la lectura contestó.
    var leidos by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var errorDeSugerido by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()

    // Qué hoja está abierta. La ficha (leer), la hoja de guardar/editar, y un movimiento abierto
    // desde la ficha: al cerrarlo, se vuelve a la ficha.
    var detalle by remember { mutableStateOf<DestinoConocido?>(null) }
    var formulario by remember { mutableStateOf<DestinoConocido?>(null) }
    var formularioAbierto by remember { mutableStateOf(false) }
    var movimientoAbierto by remember { mutableStateOf<FinancialEvent?>(null) }
    var yaSeAbrioLaPedida by remember { mutableStateOf(false) }

    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(loadKey, refreshTick) {
        cargando = true
        runCatching { Repositories.wallets.getDestinos() }
            .onSuccess { destinos = it; leidos = true }
        // Las cuentas son para la guarda del alta («ese número es de tu Fiducuenta») y para abrir un
        // movimiento. Si no se pudieron leer, el server rechaza igual.
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { cuentas = it }
        runCatching { Repositories.wallets.getUserProfile() }
            .onSuccess { ajustes = PeriodSettings(it.periodCutoffDay, it.periodStarts) }
        runCatching { Repositories.wallets.getDestinosSugeridos() }.onSuccess { sugeridos = it }
        cargando = false
    }
    // «Ver su ficha» desde un movimiento: la ficha pedida se abre sola, una vez.
    LaunchedEffect(destinos, abrir) {
        if (abrir != null && !yaSeAbrioLaPedida && leidos) {
            destinos.firstOrNull { it.id == abrir }?.let { detalle = it }
            yaSeAbrioLaPedida = true
        }
    }
    val noSeLeyo = !cargando && !leidos

    /** Sacar un sugerido de la vista apenas se resolvió, sin esperar la próxima lectura. */
    fun resuelto(s: DestinoSugerido) {
        sugeridos = sugeridos.filterNot { it.identificador.clave == s.identificador.clave }
    }

    fun guardarSugerido(s: DestinoSugerido, nombre: String) {
        errorDeSugerido = null
        alcance.launch {
            // Sin tipo: lo deduce el server de los avisos al leer (ver `tipoInferido`).
            val nuevo = DestinoConocido(nombre = nombre.trim(), numero = "")
                .conIdentificadores(listOf(s.identificador) + s.otrosIdentificadores)
            runCatching { Repositories.wallets.createDestino(nuevo) }
                .onSuccess { creado ->
                    resuelto(s)
                    destinos = (destinos + creado).sortedBy { normalizarParaBuscar(it.nombre) }
                    // La ficha abre sola: ahí está «Ponerle el nombre a N movimientos anteriores».
                    if (s.renombrables > 0) detalle = creado
                }
                .onFailure { errorDeSugerido = it.toUserMessage() }
        }
    }

    fun unirSugerido(s: DestinoSugerido, tercero: DestinoConocido) {
        errorDeSugerido = null
        alcance.launch {
            var actualizado: DestinoConocido? = null
            val resultado = runCatching {
                actualizado = Repositories.wallets.agregarIdentificador(tercero.id, AgregarIdentificador(s.identificador))
                // El nombre que acompaña a la llave también la reconoce; si ya era de otro, se deja.
                s.otrosIdentificadores.forEach { otro ->
                    runCatching { Repositories.wallets.agregarIdentificador(tercero.id, AgregarIdentificador(otro)) }
                        .onSuccess { actualizado = it }
                }
            }
            resultado.onSuccess {
                resuelto(s)
                actualizado?.let { a -> destinos = destinos.map { if (it.id == a.id) a else it } }
                if (s.renombrables > 0) detalle = actualizado
            }.onFailure { errorDeSugerido = it.toUserMessage() }
        }
    }

    fun descartar(s: DestinoSugerido, motivo: MotivoDeDescarte) {
        errorDeSugerido = null
        alcance.launch {
            runCatching { Repositories.wallets.descartarSugerido(DescartarSugerido(s.identificador, motivo)) }
                .onSuccess { resuelto(s) }
                .onFailure { errorDeSugerido = it.toUserMessage() }
        }
    }

    val visibles = remember(destinos, busqueda) { filtrarTerceros(destinos, busqueda) }
    val personas = visibles.filter { it.tipoOPersona() == TipoDeTercero.PERSONA }
    val comercios = visibles.filter { it.tipoOPersona() == TipoDeTercero.COMERCIO }
    // Sin nada guardado, lo que Movi encontró es lo único que hay para hacer: se ve abierto.
    val sugeridosAbiertos = verSugeridos || (leidos && destinos.isEmpty())

    CompositionLocalProvider(LocalAbrirFichaDelTercero provides { t -> movimientoAbierto = null; detalle = t }) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
            MinScreenHeader(
                title = PERSONAS_Y_COMERCIOS,
                // La puerta principal es Movimientos (ver `RenglonDePersonasYComercios`); «volver» sigue
                // el historial real y solo cae acá cuando no hay ninguno.
                leading = HeaderLeading.Back(fallback = Screen.Transactions()),
                subtitle = when {
                    cargando || noSeLeyo -> null
                    destinos.size == 1 -> "1 guardado"
                    destinos.isNotEmpty() -> "${destinos.size} guardados"
                    else -> null
                },
                action = if (!noSeLeyo) {
                    {
                        NewItemButton(
                            label = "Nueva",
                            onClick = { formulario = null; formularioAbierto = true },
                        )
                    }
                } else null,
            )

            if (noSeLeyo) {
                Spacer(Modifier.height(14.dp))
                NoSePudoLeer(
                    "No pudimos cargar tus personas y comercios",
                    onReintentar = { loadKey++ },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (!noSeLeyo) LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(6.dp)) }

                // ── Lo que Movi encontró solo: una línea que se abre ─────────────────
                if (sugeridos.isNotEmpty()) {
                    item {
                        LoQueEncontroMovi(
                            cuantos = sugeridos.size,
                            abierto = sugeridosAbiertos,
                            puedeCerrarse = destinos.isNotEmpty(),
                            error = errorDeSugerido,
                            onAlternar = { verSugeridos = !sugeridosAbiertos },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (sugeridosAbiertos) {
                        val aMostrar = if (verTodosLosSugeridos) sugeridos else sugeridos.take(SUGERIDOS_A_LA_VISTA)
                        items(aMostrar, key = { "sug-" + it.identificador.clave }) { s ->
                            Column {
                                TarjetaDeSugerido(
                                    sugerido = s,
                                    pareceDe = s.pareceDe?.let { id -> destinos.firstOrNull { it.id == id } },
                                    onGuardar = { nombre -> guardarSugerido(s, nombre) },
                                    onUnir = { tercero -> unirSugerido(s, tercero) },
                                    onDescartar = { motivo -> descartar(s, motivo) },
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        if (sugeridos.size > SUGERIDOS_A_LA_VISTA) {
                            item {
                                Enlace(
                                    if (verTodosLosSugeridos) "Ver menos" else "Ver ${sugeridos.size - SUGERIDOS_A_LA_VISTA} más",
                                    { verTodosLosSugeridos = !verTodosLosSugeridos },
                                    modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                if (destinos.size > TERCEROS_PARA_BUSCAR) {
                    item {
                        FieldBox("Buscar por nombre, número o llave", busqueda, { busqueda = it })
                        Spacer(Modifier.height(14.dp))
                    }
                }

                if (destinos.isEmpty() && !cargando) {
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(18.dp),
                        ) {
                            Text(QUE_ES_ESTO, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                            Spacer(Modifier.height(14.dp))
                            NewItemButton(
                                label = "Guardar una persona o comercio",
                                onClick = { formulario = null; formularioAbierto = true },
                                full = true,
                            )
                        }
                    }
                }
                // Ola B, tarea 9: antes de la primera lectura buena, la forma de las fichas.
                if (cargando && !leidos) {
                    destinosEsqueleto()
                } else {
                    seccionDeTerceros("Personas", personas) { detalle = it }
                    seccionDeTerceros("Comercios", comercios) { detalle = it }
                    if (busqueda.isNotBlank() && visibles.isEmpty()) {
                        item {
                            Text(
                                "Nadie guardado coincide con «${busqueda.trim()}».",
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        val d = detalle
        if (d != null && movimientoAbierto == null && !formularioAbierto) {
            DetalleDelDestinoSheet(
                destino = d,
                ajustes = ajustes,
                onDismiss = { detalle = null },
                onEditar = { formulario = destinos.firstOrNull { it.id == d.id } ?: d; formularioAbierto = true },
                onAbrirMovimiento = { movimientoAbierto = it },
                onCambio = { loadKey++ },
            )
        }

        movimientoAbierto?.let { ev ->
            // La misma hoja que abre tocar un movimiento en Movimientos; al cerrarla vuelve la ficha.
            HojaDelMovimiento(
                event = ev,
                cuentas = cuentas,
                onDismiss = { movimientoAbierto = null },
                onCambiado = { movimientoAbierto = null; loadKey++ },
            )
        }

        if (formularioAbierto) {
            val editado = formulario
            DestinoSheet(
                cuentas = cuentas,
                existente = editado,
                otros = if (editado == null) emptyList() else destinos.filter { it.id != editado.id },
                onDismiss = { formularioAbierto = false },
                onGuardado = {
                    formularioAbierto = false
                    // Borrado o unido, la ficha de antes ya no existe: no se vuelve a ella.
                    detalle = null
                    loadKey++
                },
            )
        }
    }
    }
}

/**
 * **Lo que Movi encontró solo, en una línea**: «Movi encontró 4 sin nombre · Les envías plata o te
 * envían, y no sabe quiénes son · Revisarlos». Abierta, debajo van las tarjetas.
 */
@Composable
private fun LoQueEncontroMovi(
    cuantos: Int,
    abierto: Boolean,
    puedeCerrarse: Boolean,
    error: String?,
    onAlternar: () -> Unit,
) {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_SUGERIDOS),
        variant = MinCardVariant.Default,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = if (abierto && !puedeCerrarse) null else onAlternar,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tituloDeLosSugeridos(cuantos), style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                Text(
                    "Les envías plata o te envían, y Movi no sabe quiénes son.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
            if (!abierto) Pastilla("Revisar", onAlternar, principal = true)
            else if (puedeCerrarse) Enlace("Ocultar", onAlternar)
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
    }
}

/** Cuántos sugeridos se ven antes de «Ver N más»: una lista larga de propuestas se ignora entera. */
internal const val SUGERIDOS_A_LA_VISTA: Int = 5

/** Desde cuántos terceros guardados aparece el buscador. */
internal const val TERCEROS_PARA_BUSCAR: Int = 8

/** El tag del bloque de lo que Movi encontró. */
const val TAG_SUGERIDOS: String = "destinos-sugeridos"

/** «Movi encontró 3 sin nombre». */
internal fun tituloDeLosSugeridos(n: Int): String =
    if (n == 1) "Movi encontró 1 sin nombre" else "Movi encontró $n sin nombre"

/** Los terceros que coinciden con [busqueda] por nombre, nota o cualquiera de sus identificadores. */
internal fun filtrarTerceros(destinos: List<DestinoConocido>, busqueda: String): List<DestinoConocido> {
    val q = normalizarParaBuscar(busqueda.trim())
    if (q.isEmpty()) return destinos
    val digitos = soloLosDigitos(q)
    return destinos.filter { d ->
        normalizarParaBuscar(d.nombre).contains(q) ||
            normalizarParaBuscar(d.deQuien.orEmpty()).contains(q) ||
            d.todosLosIdentificadores().any { id ->
                normalizarParaBuscar(id.valor).contains(q) || (digitos.length >= 3 && soloLosDigitos(id.valor).contains(digitos))
            }
    }
}

private fun LazyListScope.seccionDeTerceros(
    titulo: String,
    terceros: List<DestinoConocido>,
    onAbrir: (DestinoConocido) -> Unit,
) {
    if (terceros.isEmpty()) return
    item { MinSectionHeader(title = titulo, count = terceros.size) }
    items(terceros, key = { "dst-" + it.id }) { d ->
        Column {
            FichaDelDestino(d, onClick = { onAbrir(d) })
            Spacer(Modifier.height(10.dp))
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/**
 * **Una cuenta que Movi encontró sola** (4-oct-2026): cómo la nombra el banco, cuántas veces y
 * cuánto, y tres salidas — **Guardar** (con el nombre prellenado, un toque), **Es mía** e
 * **Ignorar**. Si se parece a un tercero guardado, primero la pregunta: **«¿Es Caro?»** — nunca se
 * une sola.
 */
@Composable
private fun TarjetaDeSugerido(
    sugerido: DestinoSugerido,
    pareceDe: DestinoConocido?,
    onGuardar: (String) -> Unit,
    onUnir: (DestinoConocido) -> Unit,
    onDescartar: (MotivoDeDescarte) -> Unit,
) {
    val hoy = remember { hoyEnAppZone() }
    var noEsEse by remember(sugerido.identificador.clave) { mutableStateOf(false) }
    var nombre by remember(sugerido.identificador.clave) { mutableStateOf(sugerido.nombrePropuesto) }
    var escribiendoNombre by remember(sugerido.identificador.clave) { mutableStateOf(false) }
    var ocupado by remember(sugerido.identificador.clave) { mutableStateOf(false) }

    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_SUGERIDOS + ":" + sugerido.identificador.clave),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sugerido.nombrePropuesto.ifEmpty { sugerido.identificador.comoSeDice.replaceFirstChar { it.uppercaseChar() } },
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detalleDelSugerido(sugerido, hoy),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (sugerido.enviado.isNotEmpty()) TotalesEnColumna(sugerido.enviado, alineadoAlFinal = true)
                if (sugerido.recibido.isNotEmpty()) {
                    Text(
                        "te envió " + sugerido.recibido.entries.sortedBy { it.key }.joinToString(" · ") { formatMoney(it.value, it.key) },
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (pareceDe != null && !noEsEse) {
            Text("¿Es ${pareceDe.nombre}?", style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Text(
                "Se le suma esta forma de reconocerla. No se crea otra.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pastilla("Sí, es ${pareceDe.nombre}", { ocupado = true; onUnir(pareceDe) }, principal = true, enabled = !ocupado)
                Pastilla("No, es otra", { noEsEse = true }, enabled = !ocupado)
            }
            return@MinCard
        }

        if (escribiendoNombre) {
            FieldBox("Nombre. Ej: Caro", nombre, { nombre = it })
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pastilla(
                if (nombre.isBlank() && !escribiendoNombre) "Ponerle nombre" else "Guardar",
                {
                    if (nombre.isBlank()) {
                        escribiendoNombre = true
                    } else {
                        ocupado = true
                        onGuardar(nombre)
                    }
                },
                principal = true,
                enabled = !ocupado && (!escribiendoNombre || nombre.isNotBlank()),
            )
            Pastilla(ES_MIA, { ocupado = true; onDescartar(MotivoDeDescarte.ES_MIA) }, enabled = !ocupado)
            Pastilla("Ignorar", { ocupado = true; onDescartar(MotivoDeDescarte.IGNORADO) }, enabled = !ocupado)
        }
    }
}

/** «·3068 · Persona · 2 veces · último 19 sep». */
internal fun detalleDelSugerido(s: DestinoSugerido, hoy: kotlinx.datetime.LocalDate): String =
    listOfNotNull(
        // El dato solo si el título es otro: «Ana Prueba Salazar · Ana Prueba Salazar» lo repetía.
        comoSeLeeEnLaFicha(s.identificador).takeIf {
            s.nombrePropuesto.isNotEmpty() && normalizarParaBuscar(it) != normalizarParaBuscar(s.nombrePropuesto)
        },
        s.tipo.comoSeDice(),
        if (s.veces == 1) "1 vez" else "${s.veces} veces",
        "último " + etiquetaDeFecha(fechaDeEpoch(s.ultimo), hoy).replaceFirstChar { it.lowercaseChar() },
    ).joinToString(" · ")

/**
 * Lo que la pantalla vacía explica, en dos frases: qué es y que no suma en su plata. Cómo se usa lo
 * dice el botón de abajo.
 */
internal const val QUE_ES_ESTO: String =
    "Guarda la cuenta o la llave de tu pareja, tu papá o la cancha: Movi les pone el nombre en tus " +
        "avisos y movimientos, y aquí ves cuánto les envías. No suman en tu plata."

/**
 * El renglón de debajo del nombre: cómo lo reconoce el banco (la cola del número, la llave — ver
 * [identificadoresDelDestino]) y la nota, si la tiene.
 */
internal fun subtituloDelDestino(destino: DestinoConocido): String =
    (identificadoresDelDestino(destino) + listOfNotNull(destino.deQuien)).joinToString(" · ")

/** «Último: ayer · $1.170.560» o «Último: te envió · 2 oct · $400.000», o `null` sin ninguno. */
internal fun ultimoDeLaFicha(destino: DestinoConocido, hoy: kotlinx.datetime.LocalDate): String? {
    val ultimo = listOfNotNull(destino.ultimo, destino.ultimoRecibido).maxByOrNull { it.timestamp } ?: return null
    val recibido = ultimo === destino.ultimoRecibido
    return "Último: " + (if (recibido) "te envió · " else "") +
        etiquetaDeFecha(fechaDeEpoch(ultimo.timestamp), hoy).replaceFirstChar { it.lowercaseChar() } +
        " · " + formatMoney(ultimo.monto, ultimo.moneda)
}

/** El tag de cada ficha de la lista: `TAG:<id>`. */
const val TAG_FICHA_EN_LA_LISTA: String = "ficha-en-la-lista"

/**
 * **Una persona o comercio, de un vistazo** (4-oct-2026): el nombre y cómo lo reconoce el banco; a
 * la derecha **lo de este período** — lo que le enviaste, y debajo lo que te envió — que es la
 * pregunta que trae al dueño a esta pantalla; y una línea con el último movimiento. El total
 * histórico vive en la ficha: con tres cifras por tarjeta no se sabía cuál mirar.
 */
@Composable
private fun FichaDelDestino(destino: DestinoConocido, onClick: () -> Unit) {
    val hoy = remember { hoyEnAppZone() }
    val enviado = destino.totalesDelPeriodo.filterValues { it != 0L }
    val recibido = destino.recibidosDelPeriodo.filterValues { it != 0L }
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FICHA_EN_LA_LISTA + ":" + destino.id),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(destino.nombre, style = Movi.textos.titulo, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtituloDelDestino(destino),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                // Sin nada este período NO se escribe «$0»: un cero con la letra de una cifra se lee
                // como un dato, y es una ausencia.
                if (enviado.isEmpty() && recibido.isEmpty()) {
                    Text("Nada este período", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                } else {
                    if (enviado.isNotEmpty()) TotalesEnColumna(enviado, alineadoAlFinal = true)
                    if (recibido.isNotEmpty()) {
                        Text(
                            "te envió " + recibido.entries.sortedBy { it.key }.joinToString(" · ") { formatMoney(it.value, it.key) },
                            style = Movi.textos.apoyo,
                            color = Movi.colores.entra,
                        )
                    }
                    Text("este período", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
            }
        }
        ultimoDeLaFicha(destino, hoy)?.let { ultimo ->
            Spacer(Modifier.height(8.dp))
            Text(ultimo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Los totales, **una línea por moneda**. Sumar pesos con dólares daría una cifra que no existe
 * (mismo criterio que `computeBalances`), y casi siempre va a haber una sola línea.
 */
@Composable
internal fun TotalesEnColumna(totales: Map<String, Long>, alineadoAlFinal: Boolean) {
    Column(
        horizontalAlignment = if (alineadoAlFinal) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Orden fijo por moneda: sin esto el mapa podría pintar COP arriba una vez y USD arriba la
        // siguiente, y una cifra que cambia de lugar se lee como una cifra que cambió.
        totales.entries.sortedBy { it.key }.forEach { (moneda, total) ->
            Text(
                formatMoney(total, moneda),
                style = Movi.textos.monto,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
            )
        }
    }
}

/** Cuántas fichas pinta [destinosEsqueleto] mientras la lista no llegó ni una vez. */
private const val FICHAS_DE_DESTINO_ESQUELETO = 3

/**
 * **Cuentas de otros mientras carga, con la forma de [FichaDelDestino]** (Ola B, tarea 9). Antes
 * de esta tarea la pantalla quedaba en blanco debajo de «Guardadas» hasta que los destinos
 * contestaban. Mismo `MinCard` de 18 dp, mismo reparto 55/45 entre el nombre+número y los
 * totales+conteo del lado derecho.
 */
private fun LazyListScope.destinosEsqueleto() {
    items(FICHAS_DE_DESTINO_ESQUELETO) {
        Column {
            FichaDelDestinoEsqueleto()
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FichaDelDestinoEsqueleto() {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_LISTA_ESQUELETO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                LineaEsqueleto(
                    fraccionDelAncho = 0.7f,
                    estilo = Movi.textos.titulo,
                    modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                )
                Spacer(Modifier.height(4.dp))
                LineaEsqueleto(fraccionDelAncho = 0.5f, estilo = Movi.textos.apoyo)
            }
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.monto), ancho = 96.dp)
                Spacer(Modifier.height(4.dp))
                LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
            }
        }
    }
}
