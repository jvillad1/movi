package com.jvillada.movi.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.CostoDeLaIa
import com.jvillada.movi.shared.model.GastoDeIaDeUnUsuario
import com.jvillada.movi.shared.model.conPuntosDeMiles
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.formatMoneySinOcultar
import kotlin.math.roundToLong

/**
 * # «Lo que cuesta la IA», en la pestaña Servidor (vuelta 106)
 *
 * El costo de la IA en pesos para el admin: 7 y 30 días, por usuario activo y por pregunta, lo que va
 * del mes con su proyección, y los diez usuarios que más gastan con cuántas preguntas hicieron. Se
 * lee aparte del resto del panel (`GET /api/admin/ia/costo`): si falla, la pestaña sigue.
 */
@Composable
internal fun SeccionDelCostoDeLaIa() {
    var intento by remember { mutableStateOf(0) }
    var costo by remember { mutableStateOf<CostoDeLaIa?>(null) }
    var fallo by remember { mutableStateOf(false) }
    LaunchedEffect(intento) {
        fallo = false
        runCatching { MoviAiParaAdmin.actual.costo() }
            .onSuccess { costo = it }
            .onFailure { fallo = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Seccion(titulo = TITULO_DEL_COSTO_DE_LA_IA)
        val c = costo
        when {
            c != null -> CostoDeLaIaEnFilas(c)
            fallo -> NoSePudoLeer("No pude leer lo que cuesta la IA.", onReintentar = { intento++ })
            else -> Column { repeat(3) { EsqueletoDeFila(conInicio = false) } }
        }
    }
}

@Composable
internal fun CostoDeLaIaEnFilas(c: CostoDeLaIa) {
    Superficie(relleno = RellenoDeSuperficie.Lista) {
        Fila(titulo = "Últimos 30 días", subtitulo = lineaDeLosTreintaDias(c), lineasDelSubtitulo = 2, final = { Cifra(pesos(c.pesos30)) })
        Fila(titulo = "Últimos 7 días", subtitulo = dolares(c.dolares7), final = { Cifra(pesos(c.pesos7)) })
        Fila(titulo = "Por usuario activo", subtitulo = lineaPorActivo(c), lineasDelSubtitulo = 2, final = { Cifra(c.pesosPorActivo30?.let(::pesos) ?: "sin activos") })
        Fila(titulo = "Por pregunta al modelo", subtitulo = "${conPuntosDeMiles(c.preguntas30.toLong())} preguntas en 30 días, sin las tres sugeridas", lineasDelSubtitulo = 2, final = { Cifra(c.pesosPorPregunta30?.let(::pesosConDecimales) ?: "sin preguntas") })
        Fila(titulo = "Este mes", subtitulo = lineaDelMes(c), lineasDelSubtitulo = 2, final = { Cifra(pesos(c.proyeccionDelMes)) })
    }
    if (c.quienesMasGastan.isNotEmpty()) {
        Seccion(titulo = "Los que más gastan", conteo = c.quienesMasGastan.size)
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            c.quienesMasGastan.forEach { u ->
                Fila(titulo = u.correo.ifBlank { u.usuarioId }, subtitulo = lineaDeUnUsuario(u), final = { Cifra(pesos(u.pesos)) })
            }
        }
    }
    Text(notaDelCosto(c), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
}

@Composable
private fun Cifra(texto: String) = Text(texto, style = Movi.textos.apoyo, color = Movi.colores.texto)

// ── Los textos, puros ────────────────────────────────────────────────────────

internal const val TITULO_DEL_COSTO_DE_LA_IA = "Lo que cuesta la IA"

internal fun pesos(n: Long): String = formatMoneySinOcultar(n, "COP")

/** Pesos con dos decimales, para lo que cuesta una pregunta («$1,58»): en pesos enteros sería «$2». */
internal fun pesosConDecimales(d: Double): String {
    val centavos = (d * 100).roundToLong()
    return "$" + conPuntosDeMiles(centavos / 100) + "," + (centavos % 100).toString().padStart(2, '0')
}

/** «US$ 1,25» con dos decimales y coma; «US$ 0,0042» si es menos de un centavo. */
internal fun dolares(d: Double): String {
    if (d > 0 && d < 0.01) {
        val diezmilesimas = (d * 10_000).roundToLong().coerceAtLeast(1)
        return "US$ 0,${diezmilesimas.toString().padStart(4, '0')}"
    }
    val centavos = (d * 100).roundToLong()
    return "US$ ${conPuntosDeMiles(centavos / 100)},${(centavos % 100).toString().padStart(2, '0')}"
}

internal fun lineaDeLosTreintaDias(c: CostoDeLaIa): String =
    dolares(c.dolares30) + " · toda la IA: el chat y los papeles"

internal fun lineaPorActivo(c: CostoDeLaIa): String =
    "${conPuntosDeMiles(c.activos30.toLong())} ${if (c.activos30 == 1) "usuario activo" else "usuarios activos"} en 30 días"

/** «Van $1.200 en 2 días · al ritmo de hoy cierra en $18.000». */
internal fun lineaDelMes(c: CostoDeLaIa): String =
    "Van ${pesos(c.pesosDelMes)} en ${c.diaDelMes} de ${c.diasDelMes} días · al ritmo de hoy cierra en ${pesos(c.proyeccionDelMes)}"

internal fun lineaDeUnUsuario(u: GastoDeIaDeUnUsuario): String =
    "${conPuntosDeMiles(u.preguntas.toLong())} ${if (u.preguntas == 1) "pregunta" else "preguntas"} · ${dolares(u.dolares)}"

/** De dónde salen las cifras: la TRM, desde cuándo se mide, los precios y lo que no tiene precio. */
internal fun notaDelCosto(c: CostoDeLaIa): String = buildList {
    add("Pesos con la TRM de ${pesos(c.trm.roundToLong())}" + if (c.trmEsRespaldo) " (aproximada: no se pudo leer la de hoy)." else ".")
    c.medidoDesde?.let { add("El costo se mide desde el $it.") } ?: add("Todavía no hay días con el costo medido.")
    if (c.sinPrecio.isNotEmpty()) add("Sin precio en la tabla, no cuentan: ${c.sinPrecio.joinToString(", ")}.")
    if (c.precios.isNotBlank()) add(c.precios + ".")
}.joinToString(" ")
