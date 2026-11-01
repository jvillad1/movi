package com.jvillada.movi.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.EventoDelRecorrido
import com.jvillada.movi.shared.model.GrupoDeFallos
import com.jvillada.movi.shared.model.ResumenDeFallos
import com.jvillada.movi.shared.model.ResumenDeUso
import com.jvillada.movi.shared.model.SesionDeUso
import com.jvillada.movi.shared.model.UsuarioConUso
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.components.ds.CampoDeBusqueda
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.Vacio
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

const val TITULO_DE_FALLOS_Y_USO = "Fallos y uso"

/** Las pestañas de la pantalla. «Servidor» la llena otra vuelta, en [PestanaDelServidor]. */
val PESTANAS_DE_FALLOS_Y_USO = listOf("Fallos", "Uso", "Recorridos", "Servidor")

const val TAG_FALLOS_Y_USO = "fallos-y-uso"

/**
 * # «Fallos y uso» (vuelta 100, solo admin)
 *
 * Ajustes → Administración. Cuatro pestañas:
 *
 * - **Fallos**: los de los últimos 7 días agrupados por huella (tipo + primera línea propia del
 *   stack), con cuántos, cuántas personas, en qué versiones, la última vez y el detalle de uno.
 * - **Uso**: activos por día, semana y mes; las pantallas más abiertas; el embudo de la guía;
 *   la adopción de cada función; la retención por cohorte semanal; las sesiones y su duración.
 * - **Recorridos**: se elige una persona, se ven sus sesiones y, al abrir una, la línea de tiempo en
 *   orden de secuencia, con filtros por tipo y un buscador por nombre.
 * - **Servidor**: el lugar que llena la vuelta de la observabilidad del servidor.
 *
 * Todo sale de `GET /api/admin/…` ([ObservabilidadParaAdmin]); un no admin recibe 403 y la fila de
 * Ajustes ni se pinta.
 */
@Composable
fun FallosYUsoScreen(onNavigate: (Screen) -> Unit) {
    var pestana by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo).testTag(TAG_FALLOS_Y_USO)) {
        MinScreenHeader(title = TITULO_DE_FALLOS_Y_USO, leading = HeaderLeading.Back(fallback = Screen.Tu))
        Box(modifier = Modifier.padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs)) {
            SelectorSegmentado(labels = PESTANAS_DE_FALLOS_Y_USO, selected = pestana, onSelect = { pestana = it })
        }
        Box(modifier = Modifier.weight(1f)) {
            when (pestana) {
                0 -> PestanaDeFallos()
                1 -> PestanaDeUso()
                2 -> PestanaDeRecorridos()
                else -> PestanaDelServidor()
            }
        }
    }
}

/** Una lectura de la pantalla: cargando, falló o llegó. */
private sealed interface Lectura<out T> {
    data object Cargando : Lectura<Nothing>
    data object Fallo : Lectura<Nothing>
    data class Llego<T>(val valor: T) : Lectura<T>
}

@Composable
private fun <T> rememberLectura(clave: Any?, leer: suspend () -> T): Pair<Lectura<T>, () -> Unit> {
    var intento by remember { mutableIntStateOf(0) }
    var estado by remember(clave) { mutableStateOf<Lectura<T>>(Lectura.Cargando) }
    LaunchedEffect(clave, intento) {
        estado = Lectura.Cargando
        estado = runCatching { leer() }.fold({ Lectura.Llego(it) }, { Lectura.Fallo })
    }
    return estado to { intento++ }
}

private fun LazyListScope.segunLaLectura(lectura: Lectura<*>, reintentar: () -> Unit): Boolean {
    when (lectura) {
        Lectura.Cargando -> item { Column { repeat(3) { EsqueletoDeFila(conInicio = false, conFinal = false) } } }
        Lectura.Fallo -> item {
            Vacio(titulo = "No pudimos leerlo", detalle = "Revisa tu conexión o que esta cuenta sea de admin.", accion = "Reintentar", onAccion = reintentar)
        }
        is Lectura.Llego<*> -> return true
    }
    return false
}

private val rellenoDeLaLista = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion)

// ── Fallos ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PestanaDeFallos() {
    val (lectura, reintentar) = rememberLectura(Unit) { ObservabilidadParaAdmin.actual.fallos() }
    var abierto by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = rellenoDeLaLista, verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        if (!segunLaLectura(lectura, reintentar)) return@LazyColumn
        val resumen = (lectura as Lectura.Llego<ResumenDeFallos>).valor
        if (resumen.grupos.isEmpty()) {
            item { Vacio(titulo = "Sin fallos en ${resumen.dias} días", detalle = "Ni en Android, ni en iOS, ni en la web.") }
            return@LazyColumn
        }
        item { Seccion(titulo = "Últimos ${resumen.dias} días", conteo = resumen.grupos.sumOf { it.cuantos }) }
        items(resumen.grupos, key = { it.huella }) { grupo ->
            GrupoDeFallosEnLaLista(grupo, abierto = abierto == grupo.huella, onAlternar = {
                abierto = if (abierto == grupo.huella) null else grupo.huella
            })
        }
    }
}

@Composable
private fun GrupoDeFallosEnLaLista(grupo: GrupoDeFallos, abierto: Boolean, onAlternar: () -> Unit) {
    Superficie(relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = listOf(grupo.tipo, grupo.componente).filter { it.isNotBlank() }.joinToString(" · "),
            subtitulo = resumenDelGrupo(grupo),
            lineasDelSubtitulo = 3,
            onClick = onAlternar,
            chevron = false,
        )
        if (abierto) {
            Separador()
            val e = grupo.ejemplo
            Column(
                modifier = Modifier.fillMaxWidth().padding(Medida.rellenoDeSuperficie),
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                Text(grupo.huella, style = Movi.textos.apoyoMedio, color = Movi.colores.texto)
                if (e.mensaje.isNotBlank()) Text(e.mensaje, style = Movi.textos.apoyo, color = Movi.colores.texto)
                Text(
                    listOf(e.plataforma, e.version, e.modelo, e.sistema, "hilo ${e.hilo}", if (e.atrapado) "atrapado" else "tumbó la app")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Text(e.stack, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
    }
}

/** «3 veces · 2 personas · 1.90, 1.89 · android · la última el 1 nov, 9:40». */
internal fun resumenDelGrupo(g: GrupoDeFallos): String = listOf(
    if (g.cuantos == 1) "1 vez" else "${g.cuantos} veces",
    when (g.usuarios) { 0 -> "sin sesión"; 1 -> "1 persona"; else -> "${g.usuarios} personas" },
    g.versiones.take(4).joinToString(", ").ifEmpty { "sin versión" },
    g.plataformas.joinToString(", "),
    "la última ${fechaYHora(g.ultimaVez)}",
).joinToString(" · ")

// ── Uso ──────────────────────────────────────────────────────────────────────────────────────

/** El nombre de cada función clave, como lo lee el dueño. */
internal val NOMBRES_DE_LAS_FUNCIONES = mapOf(
    "CUENTA_CREADA" to "Crear una cuenta",
    "MOVIMIENTO_A_MANO" to "Agregar a mano",
    "POR_REVISAR" to "Confirmar o descartar avisos",
    "MOVI_AI" to "Movi AI",
    "ESPACIO_REPARTIDO" to "Repartir en un espacio",
    "PAPEL_SUBIDO" to "Subir un extracto o papel",
    "RECURRENTE_CREADO" to "Crear un recurrente",
    "GUIA_SALIDA" to "Terminar la guía",
    "CAPTURA_ACTIVA" to "Captura del banco",
    "INICIO_EDITADO" to "Editar el inicio",
)

@Composable
private fun PestanaDeUso() {
    val (lectura, reintentar) = rememberLectura(Unit) { ObservabilidadParaAdmin.actual.uso() }
    LazyColumn(contentPadding = rellenoDeLaLista, verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        if (!segunLaLectura(lectura, reintentar)) return@LazyColumn
        val r = (lectura as Lectura.Llego<ResumenDeUso>).valor
        item {
            Column {
                Seccion(titulo = "Personas activas")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    Fila(titulo = "Hoy", final = { Valor("${r.activosHoy}") })
                    Separador()
                    Fila(titulo = "Últimos 7 días", final = { Valor("${r.activos7}") })
                    Separador()
                    Fila(titulo = "Últimos 30 días", final = { Valor("${r.activos30}") })
                }
            }
        }
        item {
            Column {
                Seccion(titulo = "Activos por día")
                Superficie {
                    val maximo = (r.activosPorDia.maxOfOrNull { it.usuarios } ?: 0).coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                        r.activosPorDia.takeLast(14).forEach { d ->
                            Barra(rotulo = d.dia.takeLast(5), valor = d.usuarios, fraccion = d.usuarios.toFloat() / maximo)
                        }
                    }
                }
            }
        }
        item {
            Column {
                Seccion(titulo = "Sesiones (30 días)")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    Fila(titulo = "Por persona", final = { Valor(r.sesionesPorUsuario.toString().replace('.', ',')) })
                    Separador()
                    Fila(titulo = "Duración mediana", final = { Valor(r.duracionMedianaSegundos?.let(::duracion) ?: "—") })
                    r.sesionesPorPlataforma.forEach { p ->
                        Separador()
                        Fila(titulo = p.nombre, subtitulo = personas(p.usuarios), final = { Valor("${p.veces}") })
                    }
                }
            }
        }
        item {
            Column {
                Seccion(titulo = "La guía de primer uso (90 días)")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    val primero = r.embudo.firstOrNull()?.usuarios ?: 0
                    r.embudo.forEachIndexed { i, paso ->
                        if (i > 0) Separador()
                        Fila(
                            titulo = "${i + 1}. ${paso.nombre.lowercase().replaceFirstChar { it.uppercase() }}",
                            final = { Valor(if (primero == 0) "${paso.usuarios}" else "${paso.usuarios} · ${paso.usuarios * 100 / primero} %") },
                        )
                    }
                }
            }
        }
        item {
            Column {
                Seccion(titulo = "Adopción (de los activos de 30 días)")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    r.adopcion.forEachIndexed { i, a ->
                        if (i > 0) Separador()
                        Fila(titulo = NOMBRES_DE_LAS_FUNCIONES[a.funcion] ?: a.funcion, subtitulo = personas(a.usuarios), final = { Valor("${a.porcentaje} %") })
                    }
                }
            }
        }
        item {
            Column {
                Seccion(titulo = "Retención por cohorte semanal")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    if (r.cohortes.isEmpty()) Fila(titulo = "Todavía no hay cohortes")
                    r.cohortes.forEachIndexed { i, c ->
                        if (i > 0) Separador()
                        Fila(
                            titulo = "Semana del ${c.semana} · ${personas(c.usuarios)}",
                            subtitulo = textoDeLaCohorte(c),
                            lineasDelSubtitulo = 2,
                        )
                    }
                }
            }
        }
        item {
            Column {
                Seccion(titulo = "Pantallas más abiertas (30 días)")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    if (r.pantallas.isEmpty()) Fila(titulo = "Todavía no hay datos")
                    r.pantallas.forEachIndexed { i, p ->
                        if (i > 0) Separador()
                        Fila(titulo = p.nombre, subtitulo = personas(p.usuarios), final = { Valor("${p.veces}") })
                    }
                }
            }
        }
        item {
            Text("Calculado en ${r.milisegundos} ms", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

/** «S1 40 % · S2 30 % · S4 — · S8 —». */
internal fun textoDeLaCohorte(c: com.jvillada.movi.shared.model.CohorteSemanal): String =
    listOf(1 to c.semana1, 2 to c.semana2, 4 to c.semana4, 8 to c.semana8)
        .joinToString(" · ") { (k, v) -> "S$k ${v?.let { "$it %" } ?: "—"}" }

@Composable
private fun Valor(texto: String) {
    Text(texto, style = Movi.textos.cuerpoFuerte, color = Movi.colores.texto)
}

@Composable
private fun Barra(rotulo: String, valor: Int, fraccion: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraccion.coerceIn(0.02f, 1f))
                    .height(Medida.altoDeBarraEnTarjeta)
                    .clip(RoundedCornerShape(Radio.pill))
                    .background(Movi.colores.texto),
            )
        }
        Text("$valor", style = Movi.textos.apoyoMedio, color = Movi.colores.texto)
    }
}

// ── Recorridos ───────────────────────────────────────────────────────────────────────────────

/** Los filtros de la línea de tiempo: `null` = todos. */
internal val FILTROS_DEL_RECORRIDO = listOf<Pair<String, String?>>("Todo" to null, "Pantallas" to "PANTALLA", "Acciones" to "ACCION", "Sistema" to "SISTEMA")

/** La línea de tiempo con el filtro por tipo y el buscador por nombre (sin distinguir mayúsculas). */
internal fun filtrarElRecorrido(eventos: List<EventoDelRecorrido>, tipo: String?, busqueda: String): List<EventoDelRecorrido> {
    val texto = busqueda.trim().lowercase()
    return eventos.filter { e ->
        (tipo == null || e.tipo == tipo) && (texto.isEmpty() || texto in e.nombre.lowercase())
    }
}

/** El renglón de un evento: «09:40:12.345 · ACCION · PrimerUso(paso 2) · accion=SIGUIENTE». */
internal fun detalleDelEvento(e: EventoDelRecorrido): String = listOf(
    horaConMilisegundos(e.hora),
    e.tipo,
    e.ruta.ifEmpty { "sin ruta" },
    e.datos.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" },
).filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
private fun PestanaDeRecorridos() {
    var persona by remember { mutableStateOf<UsuarioConUso?>(null) }
    var sesion by remember { mutableStateOf<SesionDeUso?>(null) }
    val elegida = sesion
    val quien = persona
    when {
        quien == null -> ListaDePersonas(onElegir = { persona = it })
        elegida == null -> SesionesDeLaPersona(quien, onVolver = { persona = null }, onElegir = { sesion = it })
        else -> LineaDeTiempo(quien, elegida, onVolver = { sesion = null })
    }
}

@Composable
private fun ListaDePersonas(onElegir: (UsuarioConUso) -> Unit) {
    val (lectura, reintentar) = rememberLectura(Unit) { ObservabilidadParaAdmin.actual.usuarios() }
    LazyColumn(contentPadding = rellenoDeLaLista, verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        if (!segunLaLectura(lectura, reintentar)) return@LazyColumn
        val personas = (lectura as Lectura.Llego<List<UsuarioConUso>>).valor
        if (personas.isEmpty()) {
            item { Vacio(titulo = "Todavía nadie mandó uso", detalle = "Aparece apenas alguien abre la app con el interruptor encendido.") }
            return@LazyColumn
        }
        item { Seccion(titulo = "Personas", conteo = personas.size) }
        item {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                personas.forEachIndexed { i, p ->
                    if (i > 0) Separador()
                    Fila(
                        titulo = p.correo.ifBlank { p.id },
                        subtitulo = "${p.sesiones} sesiones · ${p.eventos} eventos · ${p.plataformas.joinToString(", ")} · ${fechaYHora(p.ultimaVez)}",
                        lineasDelSubtitulo = 2,
                        onClick = { onElegir(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SesionesDeLaPersona(persona: UsuarioConUso, onVolver: () -> Unit, onElegir: (SesionDeUso) -> Unit) {
    val (lectura, reintentar) = rememberLectura(persona.id) { ObservabilidadParaAdmin.actual.sesiones(persona.id) }
    LazyColumn(contentPadding = rellenoDeLaLista, verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        item { Seccion(titulo = persona.correo.ifBlank { persona.id }, accion = "Personas", onAccion = onVolver) }
        if (!segunLaLectura(lectura, reintentar)) return@LazyColumn
        val sesiones = (lectura as Lectura.Llego<List<SesionDeUso>>).valor
        item {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                if (sesiones.isEmpty()) Fila(titulo = "Sin sesiones")
                sesiones.forEachIndexed { i, s ->
                    if (i > 0) Separador()
                    Fila(
                        titulo = fechaYHora(s.inicio),
                        subtitulo = "${duracion(s.segundos)} · ${s.plataforma} ${s.version} · ${s.eventos} eventos".replace("  ", " "),
                        onClick = { onElegir(s) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LineaDeTiempo(persona: UsuarioConUso, sesion: SesionDeUso, onVolver: () -> Unit) {
    val (lectura, reintentar) = rememberLectura(sesion.id) { ObservabilidadParaAdmin.actual.eventos(sesion.id) }
    var filtro by remember { mutableStateOf<String?>(null) }
    var busqueda by remember { mutableStateOf("") }
    LazyColumn(contentPadding = rellenoDeLaLista, verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        item {
            Column {
                Seccion(titulo = "${fechaYHora(sesion.inicio)} · ${duracion(sesion.segundos)}", accion = "Sesiones", onAccion = onVolver)
                Text(persona.correo.ifBlank { persona.id }, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                items(FILTROS_DEL_RECORRIDO) { (rotulo, tipo) ->
                    PildoraDeFiltro(rotulo, seleccionada = filtro == tipo, onClick = { filtro = tipo })
                }
            }
        }
        item { CampoDeBusqueda(valor = busqueda, alCambiar = { busqueda = it }, guia = "Buscar por nombre") }
        if (!segunLaLectura(lectura, reintentar)) return@LazyColumn
        val eventos = filtrarElRecorrido((lectura as Lectura.Llego<List<EventoDelRecorrido>>).valor, filtro, busqueda)
        item {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                if (eventos.isEmpty()) Fila(titulo = "Nada con ese filtro")
                eventos.forEachIndexed { i, e ->
                    if (i > 0) Separador()
                    Fila(titulo = "${e.secuencia}. ${e.nombre}", subtitulo = detalleDelEvento(e), lineasDelSubtitulo = 4)
                }
            }
        }
    }
}

// ── Formatos ─────────────────────────────────────────────────────────────────────────────────

private fun personas(n: Int) = if (n == 1) "1 persona" else "$n personas"

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

private fun dos(n: Int) = n.toString().padStart(2, '0')

/** «1 nov, 09:40» en la hora de Bogotá. */
internal fun fechaYHora(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(AppTimeZone.zone)
    return "${t.dayOfMonth} ${MESES[t.monthNumber - 1]}, ${dos(t.hour)}:${dos(t.minute)}"
}

/** «09:40:12.345» en la hora de Bogotá. */
internal fun horaConMilisegundos(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(AppTimeZone.zone)
    return "${dos(t.hour)}:${dos(t.minute)}:${dos(t.second)}.${(t.nanosecond / 1_000_000).toString().padStart(3, '0')}"
}

/** «45 s», «3 min 20 s», «1 h 5 min». */
internal fun duracion(segundos: Long): String = when {
    segundos < 60 -> "$segundos s"
    segundos < 3_600 -> "${segundos / 60} min ${segundos % 60} s"
    else -> "${segundos / 3_600} h ${(segundos % 3_600) / 60} min"
}
