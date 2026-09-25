package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.FormaDePeriodos
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.leadingFor
import com.jvillada.movi.ui.dashboard.fraccionQueEntro
import com.jvillada.movi.ui.sdui.BarraDeDosTramos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * # «Tus períodos»: cómo le fue al dueño en cada ciclo
 *
 * Lista uno por uno los períodos que trae `GET /api/periodos` (del en curso hacia
 * atrás), con las mismas cifras que ya usa el resto de la app — nada nuevo se calcula acá, es una
 * lectura del server. Tres puertas llevan hasta acá: el rango del hero del Inicio (que se vuelve
 * tocable), una fila en Plan, y «Ver tus períodos» en la hoja que explica el rango del mes en
 * Movimientos.
 *
 * Un período sin nada de flujo (solo un saldo inicial, o solo traspasos entre cuentas propias) no
 * dice «$0»: [fraccionQueEntro] ya distingue «no hay nada que pintar» de «entró/salió cero», y la
 * fila lo dice con palabras («Sin movimientos de flujo»), no con una cifra que sugeriría que el
 * período se midió y dio cero.
 */

const val TAG_FILA_DE_PERIODO: String = "fila-de-periodo"
const val TAG_FILA_DE_PERIODO_ESQUELETO: String = "fila-de-periodo-esqueleto"

/** Cuántas filas reserva el esqueleto sin nada recordado todavía (la primera vez en el aparato). */
private const val FILAS_POR_DEFECTO = 3

@Stable
internal class EstadoDePeriodos internal constructor(private val alcance: CoroutineScope) {
    internal var periodos by mutableStateOf<List<ResumenDePeriodo>?>(null)
    internal var loading by mutableStateOf(true)
    internal var refreshKey by mutableStateOf(0)

    internal val noSeLeyo: Boolean get() = !loading && periodos == null
    internal val cargando: Boolean get() = loading && periodos == null
    internal val vacio: Boolean get() = periodos?.isEmpty() == true

    internal fun reintentar() {
        refreshKey++
    }

    internal fun cargar() {
        alcance.launch {
            loading = true
            intentar { Repositories.wallets.getPeriodos() }.onSuccess {
                periodos = it
                FormaRecordada.delAparato.guardarPeriodos(SessionManager.userId, FormaDePeriodos(filas = it.size))
            }
            loading = false
        }
    }
}

@Composable
internal fun rememberEstadoDePeriodos(): EstadoDePeriodos {
    val alcance = rememberCoroutineScope()
    val estado = remember(alcance) { EstadoDePeriodos(alcance) }
    LaunchedEffect(estado.refreshKey) { estado.cargar() }
    return estado
}

@Composable
fun PeriodosScreen(onNavigate: (Screen) -> Unit) {
    val estado = rememberEstadoDePeriodos()
    // La cantidad de filas de la última carga que salió bien, para que el esqueleto no invente un
    // largo distinto del que el dueño va a ver — mismo patrón que Créditos/Categorías/Cuentas.
    val filasRecordadas = remember {
        FormaRecordada.delAparato.periodos(SessionManager.userId)?.filas?.takeIf { it > 0 } ?: FILAS_POR_DEFECTO
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = "Tus períodos",
            leading = leadingFor(Screen.Periodos, onNavigate, fallback = Screen.Plan()),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Movi.espacios.amplio,
                end = Movi.espacios.amplio,
                top = Movi.espacios.corto,
                bottom = 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
        ) {
            when {
                estado.noSeLeyo -> item {
                    NoSePudoLeer("No pudimos cargar tus períodos", onReintentar = { estado.reintentar() })
                }
                // Solo la PRIMERA fila reserva el renglón de la marca: es la única garantizada
                // («En curso» siempre está en el período de hoy); el resto casi nunca la trae
                // (`inicioPropio` es la excepción, no la regla — ver `PeriodSettings`), así que
                // reservarla en todas alejaría más el esqueleto de lo que de verdad se ve.
                estado.cargando -> items(filasRecordadas) { indice ->
                    FilaDePeriodoEsqueleto(conMarca = indice == 0)
                }
                estado.vacio -> item {
                    // No debería pasar — el server siempre manda al menos el período en curso —
                    // pero una lista vacía no puede quedar en blanco sin decir nada.
                    VacioQueEnsena(
                        titulo = "Todavía no hay períodos",
                        detalle = "En cuanto tengas movimientos anotados vas a ver aquí cómo te fue en cada ciclo.",
                    )
                }
                else -> items(estado.periodos.orEmpty(), key = { it.id }) { resumen ->
                    FilaDePeriodo(resumen, onClick = { onNavigate(Screen.DetalleDePeriodo(resumen.id)) })
                }
            }
        }
    }
}

/** «Del 24 de septiembre al 24 de octubre», o `null` si alguna de las dos fechas no se pudo leer. */
internal fun rangoDelResumen(resumen: ResumenDePeriodo): String? {
    val inicio = diaLegible(resumen.desde) ?: return null
    val fin = diaLegible(resumen.hasta) ?: return null
    return "Del $inicio al $fin"
}

/**
 * «En curso» cuando es el período de hoy, «Empezó el 24» cuando el dueño movió su arranque —
 * pueden darse los dos a la vez. Ninguna se pinta como afirmación de plata: son datos del período,
 * no del flujo.
 */
internal fun marcasDe(resumen: ResumenDePeriodo): List<String> = buildList {
    if (resumen.enCurso) add("En curso")
    if (resumen.inicioPropio) {
        val dia = runCatching { LocalDate.parse(resumen.desde).dayOfMonth }.getOrNull()
        if (dia != null) add("Empezó el $dia")
    }
}

/** `entradas − salidas`: lo que le quedó al dueño en ese período. */
internal fun teQuedoDe(resumen: ResumenDePeriodo): Long = resumen.entradas - resumen.salidas

/**
 * «+$1,2M» / «−$1,2M» / «$0» — compacta, como Entró/Salió a su lado (misma [formatMoneyCompact]
 * que usa el hero del Inicio), con signo explícito salvo en cero.
 */
internal fun textoDeTeQuedo(monto: Long): String =
    if (monto > 0) "+" + formatMoneyCompact(monto) else formatMoneyCompact(monto)

@Composable
private fun colorDeTeQuedo(monto: Long): Color = when {
    monto > 0 -> Movi.colores.entra
    monto < 0 -> Movi.colores.sale
    else -> Movi.colores.textoMedio
}

@Composable
private fun FilaDePeriodo(resumen: ResumenDePeriodo, onClick: () -> Unit) {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_PERIODO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resumen.nombre,
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                val rango = rangoDelResumen(resumen)
                if (rango != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(text = rango, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
                val marcas = marcasDe(resumen)
                if (marcas.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = marcas.joinToString(" · "),
                        style = Movi.textos.apoyo,
                        color = Movi.colores.marca,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            ChevronRight()
        }
        val fraccion = fraccionQueEntro(resumen.entradas, resumen.salidas)
        if (fraccion == null) {
            Spacer(Modifier.height(10.dp))
            Text(text = "Sin movimientos de flujo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        } else {
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ParDeCifraDePeriodo("Entró", formatMoneyCompact(resumen.entradas), Movi.colores.entra)
                ParDeCifraDePeriodo("Salió", formatMoneyCompact(resumen.salidas), Movi.colores.sale)
                val teQuedo = teQuedoDe(resumen)
                ParDeCifraDePeriodo("Te quedó", textoDeTeQuedo(teQuedo), colorDeTeQuedo(teQuedo), alFinal = true)
            }
            Spacer(Modifier.height(8.dp))
            BarraDeDosTramos(
                fraccionIzquierda = fraccion,
                colorIzquierda = Movi.colores.entra,
                colorDerecha = Movi.colores.sale,
                entrada = 1f,
            )
        }
    }
}

@Composable
private fun ParDeCifraDePeriodo(rotulo: String, cifra: String, color: Color, alFinal: Boolean = false) {
    Column(horizontalAlignment = if (alFinal) Alignment.End else Alignment.Start) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Cifra(cifra, Movi.textos.titulo, color = color)
    }
}

/**
 * [FilaDePeriodo] mientras no llegó: misma forma, sin una sola cifra. [conMarca] reserva el
 * renglón de «En curso»/«Empezó el…» — ver el comentario en [PeriodosScreen] sobre por qué solo la
 * primera fila lo pide.
 */
@Composable
private fun FilaDePeriodoEsqueleto(conMarca: Boolean) {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_PERIODO_ESQUELETO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.titulo)
                Spacer(Modifier.height(3.dp))
                LineaEsqueleto(fraccionDelAncho = 0.65f, estilo = Movi.textos.apoyo)
                if (conMarca) {
                    Spacer(Modifier.height(2.dp))
                    LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(3) {
                Column {
                    LineaEsqueleto(fraccionDelAncho = 0.5f, estilo = Movi.textos.apoyo)
                    LineaEsqueleto(fraccionDelAncho = 0.7f, estilo = Movi.textos.titulo)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BloqueEsqueleto(alto = Movi.espacios.corto)
    }
}

/** [id] es el prefijo del período («2026-09»); `null` si no se pudo leer. */
internal fun nombreDelId(id: String): String? {
    val partes = id.split("-")
    if (partes.size != 2) return null
    val year = partes[0].toIntOrNull() ?: return null
    val month = partes[1].toIntOrNull() ?: return null
    return runCatching { tituloDelPeriodo(PeriodoFinanciero(year, month)) }.getOrNull()
}

/**
 * El detalle de un período — por ahora un marcador mínimo con su nombre, para que la navegación
 * desde [PeriodosScreen] sea probable de punta a punta. La tarea 5 lo reemplaza por el detalle de
 * verdad (categorías, pagos fijos, presupuestos, los gastos más grandes).
 */
@Composable
fun DetalleDePeriodoScreen(onNavigate: (Screen) -> Unit, id: String) {
    val nombre = remember(id) { nombreDelId(id) ?: id }
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(title = nombre, leading = HeaderLeading.Back(fallback = Screen.Periodos))
    }
}
