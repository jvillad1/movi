package com.jvillada.movi.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CorridaDeJob
import com.jvillada.movi.shared.model.IaDelDia
import com.jvillada.movi.shared.model.PanelDelServidor
import com.jvillada.movi.shared.model.PuntoDelServidor
import com.jvillada.movi.shared.model.RutaMedida
import com.jvillada.movi.shared.model.SaludDelServidor
import com.jvillada.movi.shared.model.ServicioMedido
import com.jvillada.movi.shared.model.conPuntosDeMiles
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Grafica
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDePildora
import kotlinx.datetime.Clock

/**
 * # La pestaña «Servidor» de «Fallos y uso» (vuelta 102)
 *
 * Cómo responde el servidor, para un admin: los pedidos de las últimas 24 horas o 7 días (por hora, el
 * % de 5xx y el p95), las 10 rutas más lentas y las 10 con más 5xx, los servicios de afuera (Anthropic
 * por modelo, Resend, la TRM) con la IA por día, los jobs del scheduler y un resumen de la salud. Todo
 * sale de `GET /api/admin/servidor/...` ([PanelDelServidor]); acá no se calcula nada que importe, solo
 * se dice.
 *
 * Los textos son funciones puras (`textoDeMs`, `textoDePorcentaje`…), probadas aparte.
 */

/** Los dos lapsos: el de los pedidos (horas) y el de los servicios (días). */
internal enum class LapsoDelServidor(val rotulo: String, val horas: Int, val dias: Int) {
    DIA("24 horas", 24, 1),
    SEMANA("7 días", 168, 7),
}

@Composable
fun PestanaServidor(modifier: Modifier = Modifier) {
    var lapso by remember { mutableStateOf(LapsoDelServidor.DIA) }
    var intento by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf<PanelDelServidor?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var fallo by remember { mutableStateOf(false) }

    LaunchedEffect(lapso, intento) {
        cargando = true
        fallo = false
        runCatching { Repositories.wallets.getPanelDelServidor(lapso.horas, lapso.dias) }
            .onSuccess { panel = it }
            .onFailure { fallo = true }
        cargando = false
    }

    val ahora = Clock.System.now().toEpochMilliseconds()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion),
        verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
    ) {
        item(key = "lapso") {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                LapsoDelServidor.entries.forEach { l ->
                    PildoraDeFiltro(texto = l.rotulo, seleccionada = l == lapso, onClick = { lapso = l })
                }
            }
        }
        val p = panel
        when {
            p == null && cargando -> item(key = "cargando") {
                Column { repeat(4) { EsqueletoDeFila(conInicio = false) } }
            }
            p == null && fallo -> item(key = "fallo") {
                NoSePudoLeer("No pude leer cómo está el servidor.", onReintentar = { intento++ })
            }
            p != null -> {
                item(key = "pedidos") { SeccionDePedidos(p, lapso) }
                item(key = "lentas") { SeccionDeRutas("Las más lentas", p.resumen.masLentas, conErrores = false) }
                item(key = "errores") { SeccionDeRutas("Con más errores 5xx", p.resumen.conMas5xx, conErrores = true) }
                item(key = "servicios") { SeccionDeServicios(p.servicios.servicios, p.servicios.iaPorDia) }
                item(key = "jobs") { SeccionDeJobs(p.jobs.ultimas, p.jobs.fallidas, ahora) }
                item(key = "salud") { SeccionDeSalud(p.salud) }
            }
        }
    }
}

@Composable
private fun SeccionDePedidos(p: PanelDelServidor, lapso: LapsoDelServidor) {
    val r = p.resumen
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Seccion(titulo = "Pedidos")
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            Fila(
                titulo = "${conPuntosDeMiles(r.pedidos)} pedidos",
                subtitulo = lineaDePedidosPorHora(r.serie),
                final = { Text(textoDePorcentaje(r.parteDe5xx) + " con 5xx", style = Movi.textos.apoyo, color = colorDeErrores(r.parteDe5xx)) },
            )
            Fila(
                titulo = "p95 de todo",
                subtitulo = "${conPuntosDeMiles(r.errores4xx)} con 4xx · ${conPuntosDeMiles(r.consultasLentas)} consultas de más de 500 ms",
                final = { Text(textoDeMs(r.p95Ms), style = Movi.textos.apoyo, color = Movi.colores.texto) },
            )
            Fila(
                titulo = "Usuarios a la vez",
                subtitulo = "El pico de usuarios distintos en un mismo minuto",
                final = { Text(conPuntosDeMiles(r.usuariosPicoPorMinuto.toLong()), style = Movi.textos.apoyo, color = Movi.colores.texto) },
            )
        }
        GraficaDePedidos(r.serie)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hace ${lapso.rotulo}", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Text("Ahora", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
    }
}

/** Una barra por hora: el alto son los pedidos, y la parte con 5xx va en el color de lo que sale mal. */
@Composable
private fun GraficaDePedidos(serie: List<PuntoDelServidor>) {
    val colorDePedidos = Movi.colores.enlace
    val colorDeErrores = Movi.colores.sale
    val tope = serie.maxOfOrNull { it.pedidos }?.coerceAtLeast(1) ?: 1
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(Grafica.alto)
            .semantics { contentDescription = lineaDePedidosPorHora(serie) },
    ) {
        if (serie.isEmpty()) return@Canvas
        val ancho = size.width / serie.size
        val hueco = (ancho * 0.2f).coerceAtMost(Grafica.margen.toPx())
        serie.forEachIndexed { i, punto ->
            if (punto.pedidos == 0L) return@forEachIndexed
            val alto = size.height * punto.pedidos / tope
            val x = i * ancho + hueco / 2
            drawRect(colorDePedidos, topLeft = Offset(x, size.height - alto), size = Size(ancho - hueco, alto))
            if (punto.errores5xx > 0) {
                val altoDeErrores = size.height * punto.errores5xx / tope
                drawRect(colorDeErrores, topLeft = Offset(x, size.height - altoDeErrores), size = Size(ancho - hueco, altoDeErrores))
            }
        }
    }
}

@Composable
private fun SeccionDeRutas(titulo: String, rutas: List<RutaMedida>, conErrores: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Seccion(titulo = titulo)
        if (rutas.isEmpty()) {
            Text(
                if (conErrores) "Ninguna ruta contestó 5xx en este lapso." else "Todavía no hay pedidos en este lapso.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            return@Column
        }
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            rutas.forEach { r ->
                Fila(
                    titulo = "${r.metodo} ${r.ruta}",
                    subtitulo = "${conPuntosDeMiles(r.pedidos)} pedidos · máximo ${textoDeMs(r.maximoMs)}",
                    final = {
                        Text(
                            if (conErrores) "${conPuntosDeMiles(r.errores5xx)} con 5xx" else "p95 ${textoDeMs(r.p95Ms)}",
                            style = Movi.textos.apoyo,
                            color = if (conErrores) Movi.colores.sale else Movi.colores.texto,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SeccionDeServicios(servicios: List<ServicioMedido>, iaPorDia: List<IaDelDia>) {
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Seccion(titulo = "Servicios de afuera")
        if (servicios.isEmpty()) {
            Text("Ninguna llamada a Anthropic, Resend ni la TRM en este lapso.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        } else {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                servicios.forEach { s ->
                    Fila(
                        titulo = "${nombreDelServicio(s.servicio)} · ${s.operacion}",
                        subtitulo = subtituloDelServicio(s),
                        lineasDelSubtitulo = 2,
                        final = { Text("p95 ${textoDeMs(s.p95Ms)}", style = Movi.textos.apoyo, color = Movi.colores.texto) },
                    )
                }
            }
        }
        if (iaPorDia.isNotEmpty()) {
            Seccion(titulo = "La IA por día")
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                iaPorDia.forEach { d ->
                    Fila(
                        titulo = d.dia,
                        subtitulo = lineaDeLaIa(d),
                        lineasDelSubtitulo = 2,
                        final = { Text(fichasPorUsuario(d), style = Movi.textos.apoyo, color = Movi.colores.texto) },
                    )
                }
            }
            Text(
                "Movi no tiene los precios por ficha en su código: el costo se lee en fichas, no en dólares.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
        }
    }
}

@Composable
private fun SeccionDeJobs(ultimas: List<CorridaDeJob>, fallidas: List<CorridaDeJob>, ahora: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Seccion(titulo = "Jobs")
        if (ultimas.isEmpty()) {
            Text("Todavía no corrió ningún job.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            return@Column
        }
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            ultimas.forEach { c ->
                Fila(
                    titulo = c.nombre,
                    subtitulo = "${haceCuanto(c.inicio, ahora)} · ${textoDeMs(c.duracionMs)}" + (c.error?.let { " · $it" } ?: ""),
                    lineasDelSubtitulo = 2,
                    final = { Pildora(if (c.bien) "Bien" else "Falló", tono = if (c.bien) TonoDePildora.Exito else TonoDePildora.Peligro) },
                )
            }
        }
        if (fallidas.isNotEmpty()) {
            Seccion(titulo = "Las que fallaron", conteo = fallidas.size)
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                fallidas.forEach { c ->
                    Fila(
                        titulo = c.nombre,
                        subtitulo = "${haceCuanto(c.inicio, ahora)} · ${c.error ?: "sin mensaje"}",
                        lineasDelSubtitulo = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeccionDeSalud(s: SaludDelServidor) {
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Seccion(titulo = "Salud")
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            Fila(
                titulo = "Base de datos",
                subtitulo = if (s.baseOk) "Contesta en ${textoDeMs(s.baseMs)}" else "No contesta",
                final = { Pildora(if (s.baseOk) "Bien" else "Caída", tono = if (s.baseOk) TonoDePildora.Exito else TonoDePildora.Peligro) },
            )
            Fila(
                titulo = "Conexiones",
                subtitulo = lineaDelPool(s),
            )
            Fila(
                titulo = "Versión",
                subtitulo = (s.version?.take(8) ?: "No se sabe") + " · arriba hace " + textoDeUptime(s.uptimeMs),
            )
            Fila(
                titulo = "Memoria",
                subtitulo = "${conPuntosDeMiles(s.memoriaUsadaMb)} MB usados de ${conPuntosDeMiles(s.memoriaMaximaMb)} MB",
            )
            Fila(
                titulo = "Variables de entorno",
                subtitulo = lineaDeVariables(s),
                lineasDelSubtitulo = 3,
            )
        }
    }
}

@Composable
private fun colorDeErrores(parte: Double) = if (parte > 0.0) Movi.colores.sale else Movi.colores.textoMedio

// ── Los textos, puros ────────────────────────────────────────────────────────

/** «850 ms», «1,2 s», o «sin datos». */
internal fun textoDeMs(ms: Long?): String = when {
    ms == null -> "sin datos"
    ms < 1_000 -> "$ms ms"
    else -> "${ms / 1_000},${(ms % 1_000) / 100} s"
}

/** «3,4 %» con una cifra decimal; «0 %» si no hubo. */
internal fun textoDePorcentaje(parte: Double): String {
    val decimas = kotlin.math.round(parte * 1_000).toLong()
    if (decimas == 0L) return "0 %"
    return if (decimas % 10 == 0L) "${decimas / 10} %" else "${decimas / 10},${decimas % 10} %"
}

/** «3 d 4 h», «5 h 12 min», «12 min». */
internal fun textoDeUptime(ms: Long): String {
    val minutos = ms / 60_000
    val horas = minutos / 60
    val dias = horas / 24
    return when {
        dias > 0 -> "$dias d ${horas % 24} h"
        horas > 0 -> "$horas h ${minutos % 60} min"
        else -> "$minutos min"
    }
}

/** «hace 5 min», «hace 3 h», «hace 2 d». */
internal fun haceCuanto(inicio: Long, ahora: Long): String {
    val minutos = ((ahora - inicio) / 60_000).coerceAtLeast(0)
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace $minutos min"
        minutos < 60 * 24 -> "hace ${minutos / 60} h"
        else -> "hace ${minutos / (60 * 24)} d"
    }
}

/** «Unos 40 por hora · pico de 120 en una hora». */
internal fun lineaDePedidosPorHora(serie: List<PuntoDelServidor>): String {
    if (serie.isEmpty() || serie.all { it.pedidos == 0L }) return "Sin pedidos en este lapso"
    val promedio = serie.sumOf { it.pedidos } / serie.size
    val pico = serie.maxOf { it.pedidos }
    return "Unos ${conPuntosDeMiles(promedio)} por hora · pico de ${conPuntosDeMiles(pico)} en una hora"
}

internal fun nombreDelServicio(servicio: String): String = when (servicio) {
    "anthropic" -> "Anthropic"
    "resend" -> "Resend"
    "trm" -> "TRM"
    else -> servicio
}

/** «120 llamadas · 3 errores: 1 sin crédito, 2 por límite». */
internal fun subtituloDelServicio(s: ServicioMedido): String {
    val llamadas = "${conPuntosDeMiles(s.llamadas)} ${if (s.llamadas == 1L) "llamada" else "llamadas"}"
    if (s.errores == 0L) return "$llamadas · sin errores"
    val partes = listOfNotNull(
        s.sinCredito.takeIf { it > 0 }?.let { "$it sin crédito" },
        s.limite.takeIf { it > 0 }?.let { "$it por límite" },
        s.servidor.takeIf { it > 0 }?.let { "$it del otro lado (5xx)" },
        s.otros.takeIf { it > 0 }?.let { "$it otros" },
    )
    val errores = "${conPuntosDeMiles(s.errores)} ${if (s.errores == 1L) "error" else "errores"}"
    return "$llamadas · $errores: ${partes.joinToString(", ")}"
}

/** «12 llamadas · 2 usuarios · 45.000 fichas de entrada, 3.000 de salida». */
internal fun lineaDeLaIa(d: IaDelDia): String =
    "${conPuntosDeMiles(d.llamadas)} llamadas · ${d.usuarios} ${if (d.usuarios == 1) "usuario" else "usuarios"} · " +
        "${conPuntosDeMiles(d.fichasEntrada)} fichas de entrada, ${conPuntosDeMiles(d.fichasSalida)} de salida"

/** Las fichas (entrada y salida) por usuario del día: lo que dice cuánto cuesta la IA por usuario activo. */
internal fun fichasPorUsuario(d: IaDelDia): String {
    if (d.usuarios == 0) return "sin usuarios"
    return "${conPuntosDeMiles((d.fichasEntrada + d.fichasSalida) / d.usuarios)} por usuario"
}

internal fun lineaDelPool(s: SaludDelServidor): String {
    val activas = s.poolActivas ?: return "Sin pool (no se mide)"
    return "$activas en uso de ${s.poolTotal ?: 0} · ${s.poolEsperando ?: 0} esperando"
}

/** «Están las 15» o «Faltan: RESEND_API_KEY, VAPID_PUBLIC_KEY». Nunca un valor: el server no lo manda. */
internal fun lineaDeVariables(s: SaludDelServidor): String {
    val faltan = s.variables.filterNot { it.presente }.map { it.nombre }
    return if (faltan.isEmpty()) "Están las ${s.variables.size}" else "Faltan: ${faltan.joinToString(", ")}"
}
