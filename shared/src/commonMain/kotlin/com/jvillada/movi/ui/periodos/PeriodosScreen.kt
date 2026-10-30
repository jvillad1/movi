package com.jvillada.movi.ui.periodos

import com.jvillada.movi.theme.Espacio
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.FormaDePeriodos
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Lectura
import com.jvillada.movi.data.periodoVigenteSegun
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.components.ActualizandoEnLaCabecera
import com.jvillada.movi.ui.components.NoSePudoActualizar
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.shared.model.nombreEntre
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.nombreDelPeriodo
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.Clock
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.CifraConRotulo
import com.jvillada.movi.ui.components.ds.EsqueletoDeCifraConRotulo
import com.jvillada.movi.ui.components.ds.FormaDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.TonoDeFila
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.leadingFor
import com.jvillada.movi.ui.dashboard.fraccionQueEntro
import com.jvillada.movi.ui.sdui.BarraDeDosTramos
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
const val TAG_LO_QUE_CUBRIO: String = "fila-de-periodo-lo-que-cubrio"
const val TAG_INCLUYE_CREDITOS: String = "periodo-incluye-creditos"
const val TAG_LISTA_DE_PERIODOS: String = "lista-de-periodos"
const val TAG_VACIOS_DEL_PRINCIPIO: String = "periodos-vacios-del-principio"
const val TAG_RACHA_DE_VACIOS: String = "periodos-racha-de-vacios"

/** Cuántas filas reserva el esqueleto sin nada recordado todavía (la primera vez en el aparato). */
private const val FILAS_POR_DEFECTO = 3

@Stable
internal class EstadoDePeriodos internal constructor(
    /** Lo que además hay que releer con «Reintentar»: el perfil compartido con el detalle (Ola W2). */
    private val alReintentar: () -> Unit = {},
) {
    internal var refreshKey by mutableStateOf(0)

    /** El perfil y los períodos. Los pone [rememberEstadoDePeriodos] en cada pasada. */
    private var perfil by mutableStateOf<Lectura<UserProfile>?>(null)
    private var lectura by mutableStateOf<Lectura<List<ResumenDePeriodo>>?>(null)

    internal fun conectar(perfil: Lectura<UserProfile>, periodos: Lectura<List<ResumenDePeriodo>>) {
        if (this.perfil !== perfil) this.perfil = perfil
        if (lectura !== periodos) lectura = periodos
    }

    internal val periodos: List<ResumenDePeriodo>? get() = lectura?.valor
    // Mientras el perfil no dice el período, los períodos ni se leen (ver `rememberLectura`): cargando.
    internal val loading: Boolean get() = lectura?.actualizando ?: true

    internal val noSeLeyo: Boolean get() = !loading && periodos == null
    internal val cargando: Boolean get() = loading && periodos == null
    /** Lo que se ve es lo último que vimos: la lectura falló con algo a la vista. */
    internal val noSePudoActualizar: Boolean get() =
        lectura?.falloConAlgoALaVista == true || (perfil?.falloConAlgoALaVista == true && periodos != null)
    // Un vacío solo se afirma con una lectura que contestó en esta visita.
    internal val vacio: Boolean get() = periodos?.isEmpty() == true && !noSePudoActualizar
    /** Hay algo pintado que esta visita todavía no confirmó: «Actualizando…» en la cabecera. */
    internal val actualizandoConAlgoALaVista: Boolean get() =
        periodos != null && (loading || perfil?.actualizando == true)

    internal fun reintentar() {
        refreshKey++
        alReintentar()
    }
}

/**
 * **Lo último que se vio, al primer cuadro** (ver [rememberLectura]). Los períodos dependen del
 * de hoy («En curso»), así que se recuerdan con él y no se leen ni se muestran hasta saberlo por el
 * perfil (ver [periodoVigenteSegun]). Se releen con «Reintentar» y con `LocalRefreshTick`.
 *
 * [perfilCompartido] es el perfil que ya leyó quien monta la lista al lado del detalle (Ola W2):
 * con él la lista no lo vuelve a pedir, y su «Reintentar» lo relee para los dos.
 */
@Composable
internal fun rememberEstadoDePeriodos(perfilCompartido: PerfilCompartido? = null): EstadoDePeriodos {
    val estado = remember { EstadoDePeriodos(alReintentar = perfilCompartido?.releer ?: {}) }
    val perfil = perfilCompartido?.lectura
        ?: rememberLectura(ClaveDeLectura.Perfil, estado.refreshKey) { Repositories.wallets.getUserProfile() }
    val periodos = rememberLectura(ClaveDeLectura.Periodos, estado.refreshKey, periodoVigenteSegun(perfil)) {
        Repositories.wallets.getPeriodos()
    }
    estado.conectar(perfil, periodos)
    LaunchedEffect(periodos.valor) {
        val leidos = periodos.valor ?: return@LaunchedEffect
        FormaRecordada.delAparato.guardarPeriodos(SessionManager.userId, FormaDePeriodos(filas = leidos.size))
    }
    return estado
}

@Composable
fun PeriodosScreen(onNavigate: (Screen) -> Unit) {
    ListaDePeriodos(
        onNavigate = onNavigate,
        estado = rememberEstadoDePeriodos(),
        elegido = null,
        onTocar = { id -> onNavigate(Screen.DetalleDePeriodo(id)) },
    )
}

/**
 * La lista con su cabecera: toda la pantalla en el teléfono, el panel de la izquierda en la web
 * (ver [PeriodosListaYDetalle]). [elegido] es el período que el panel de la derecha muestra (se
 * marca); `null` en el teléfono, donde no hay nada al lado. Tocar una fila llama a [onTocar] con su
 * id: en el teléfono navega al detalle, en la web lo elige (ver [alTocarUnPeriodo]).
 */
@Composable
internal fun ListaDePeriodos(
    onNavigate: (Screen) -> Unit,
    estado: EstadoDePeriodos,
    elegido: String?,
    onTocar: (String) -> Unit,
    estadoDeLaLista: LazyListState = rememberLazyListState(),
) {
    // La cantidad de filas de la última carga que salió bien, para que el esqueleto no invente un
    // largo distinto del que el dueño va a ver — mismo patrón que Créditos/Categorías/Cuentas.
    val filasRecordadas = remember {
        FormaRecordada.delAparato.periodos(SessionManager.userId)?.filas?.takeIf { it > 0 } ?: FILAS_POR_DEFECTO
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = "Tus períodos",
            leading = leadingFor(Screen.Periodos, onNavigate, fallback = Screen.Plan()),
            action = if (estado.actualizandoConAlgoALaVista) {
                { ActualizandoEnLaCabecera() }
            } else null,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).testTag(TAG_LISTA_DE_PERIODOS),
            state = estadoDeLaLista,
            contentPadding = PaddingValues(
                start = Movi.espacios.amplio,
                end = Movi.espacios.amplio,
                top = Movi.espacios.corto,
                bottom = Medida.botonFlotante + Espacio.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
        ) {
            if (estado.noSePudoActualizar) {
                item { NoSePudoActualizar(onReintentar = { estado.reintentar() }) }
            }
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
                // Lo recordado vacío con la lectura de ahora caída: solo el aviso de arriba.
                estado.periodos?.isEmpty() == true && !estado.vacio -> Unit
                estado.vacio -> item {
                    // No debería pasar — el server siempre manda al menos el período en curso —
                    // pero una lista vacía no puede quedar en blanco sin decir nada.
                    VacioQueEnsena(
                        titulo = "Todavía no hay períodos",
                        detalle = "En cuanto tengas movimientos anotados vas a ver aquí cómo te fue en cada ciclo.",
                    )
                }
                else -> {
                    items(piezasDeLosPeriodos(estado.periodos.orEmpty()), key = { it.clave }) { pieza ->
                        when (pieza) {
                            is PiezaDePeriodos.Tarjeta -> FilaDePeriodo(
                                pieza.resumen,
                                elegida = pieza.resumen.id == elegido,
                                onClick = { onTocar(pieza.resumen.id) },
                            )
                            // Una fila apagada y sin toque: no hay nada que abrir. Sin tarjeta, directo
                            // sobre el fondo: su texto queda alineado con el de las tarjetas.
                            is PiezaDePeriodos.Vacios -> Fila(
                                titulo = pieza.texto,
                                tonoDelTitulo = TonoDeFila.Apagado,
                                modifier = Modifier.testTag(
                                    if (pieza.delPrincipio) TAG_VACIOS_DEL_PRINCIPIO else TAG_RACHA_DE_VACIOS,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Si en [resumen] entró o salió algo: lo mismo que decide «Sin movimientos de flujo» en [CifrasDelPeriodo]. */
internal fun tieneMovimientosDeFlujo(resumen: ResumenDePeriodo): Boolean =
    fraccionQueEntro(resumen.entradas, resumen.salidas) != null

/**
 * Una pieza de la lista de Tus períodos: la tarjeta de un período, o una racha de vacíos en una fila.
 * [clave] es la de la lista perezosa.
 */
internal sealed interface PiezaDePeriodos {
    val clave: String

    data class Tarjeta(val resumen: ResumenDePeriodo) : PiezaDePeriodos {
        override val clave: String get() = resumen.id
    }

    /**
     * Períodos vacíos seguidos, del más nuevo al más viejo (como los manda el server).
     * [delPrincipio]: son los de antes del primer período con movimientos.
     */
    data class Vacios(val periodos: List<ResumenDePeriodo>, val delPrincipio: Boolean, val texto: String) : PiezaDePeriodos {
        override val clave: String get() = "vacios-" + periodos.first().id
    }
}

/** Desde cuántos vacíos seguidos en el medio de la lista se juntan en una fila. */
internal const val VACIOS_PARA_JUNTAR: Int = 2

/**
 * **Los meses vacíos no ocupan una tarjeta cada uno** (vuelta de diseño 5, generalizada en la 7).
 *
 * Parte [periodos] (del en curso hacia atrás, como los manda el server) en tarjetas y filas:
 * - **los del principio** —los que quedan después (más viejos) del último con movimientos de flujo—
 *   van juntos al final, en una fila: «Antes de agosto 2026 no hay movimientos» (aunque sea uno: el
 *   dueño empezó a usar Movi en agosto, y eso se dice una vez);
 * - **una racha de [VACIOS_PARA_JUNTAR] o más en el medio** va en una fila en su lugar: «Octubre 2025 a
 *   julio 2026 · sin movimientos». En la demo eran diez tarjetas «Sin movimientos de flujo» seguidas;
 * - **un vacío solo** entre dos con movimientos se queda como tarjeta, porque ahí sí dice algo.
 *
 * El primero (el en curso) es siempre tarjeta, tenga o no movimientos. Solo decide cómo se pinta: no
 * quita ni cambia ningún período ni sus cifras (aplanadas, las piezas dan la misma lista).
 */
internal fun piezasDeLosPeriodos(periodos: List<ResumenDePeriodo>): List<PiezaDePeriodos> {
    if (periodos.isEmpty()) return emptyList()
    val ultimoConMovimientos = periodos.indexOfLast(::tieneMovimientosDeFlujo)
    val corte = maxOf(ultimoConMovimientos, 0) + 1
    val piezas = mutableListOf<PiezaDePeriodos>(PiezaDePeriodos.Tarjeta(periodos.first()))
    var racha = mutableListOf<ResumenDePeriodo>()
    fun cerrarLaRacha() {
        if (racha.size >= VACIOS_PARA_JUNTAR) {
            piezas += PiezaDePeriodos.Vacios(racha.toList(), delPrincipio = false, texto = textoDeLaRachaDeVacios(racha))
        } else {
            racha.forEach { piezas += PiezaDePeriodos.Tarjeta(it) }
        }
        racha = mutableListOf()
    }
    for (resumen in periodos.subList(1, corte)) {
        if (tieneMovimientosDeFlujo(resumen)) {
            cerrarLaRacha()
            piezas += PiezaDePeriodos.Tarjeta(resumen)
        } else {
            racha += resumen
        }
    }
    cerrarLaRacha()
    val delPrincipio = periodos.drop(corte)
    if (delPrincipio.isNotEmpty()) {
        piezas += PiezaDePeriodos.Vacios(
            delPrincipio,
            delPrincipio = true,
            texto = textoDeLosVaciosDelPrincipio(periodos[corte - 1]),
        )
    }
    return piezas
}

/** «Antes de agosto 2026 no hay movimientos»: [primero] es la última tarjeta, el primero con movimientos. */
internal fun textoDeLosVaciosDelPrincipio(primero: ResumenDePeriodo): String =
    "Antes de ${primero.nombre.replaceFirstChar { it.lowercase() }} no hay movimientos"

/**
 * «Octubre 2025 a julio 2026 · sin movimientos»: del más viejo al más nuevo de [racha] (que llega del
 * más nuevo al más viejo). Si los dos son del mismo año, el año va una vez: «Marzo a julio 2026».
 */
internal fun textoDeLaRachaDeVacios(racha: List<ResumenDePeriodo>): String {
    val desde = racha.last().nombre
    val hasta = racha.first().nombre.replaceFirstChar { it.lowercase() }
    val anio = Regex(""" (\d{4})$""")
    val anioDesde = anio.find(desde)?.groupValues?.get(1)
    val anioHasta = anio.find(hasta)?.groupValues?.get(1)
    val inicio = if (anioDesde != null && anioDesde == anioHasta) desde.removeSuffix(" $anioDesde") else desde
    return "$inicio a $hasta · sin movimientos"
}

/**
 * **El nombre del período de un resumen**, el mismo de toda la app (vuelta 75, [nombreEntre] sobre sus
 * bordes): «23 oct – 24 nov», «Octubre», y el año solo si no es el de hoy. Sale de `desde` y `hasta`
 * —que el server manda con el corte y las excepciones del dueño ya aplicados—, así que un APK nuevo lo
 * dice igual con un server viejo. Si las fechas no se entienden, el nombre que mandó el server.
 */
internal fun nombreDelResumen(
    resumen: ResumenDePeriodo,
    anioDeHoy: Int? = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).year,
): String {
    val inicio = runCatching { LocalDate.parse(resumen.desde) }.getOrNull() ?: return resumen.nombre
    val ultimo = runCatching { LocalDate.parse(resumen.hasta) }.getOrNull() ?: return resumen.nombre
    val anio = periodoDelPrefijo(resumen.id)?.year ?: ultimo.year
    return nombreEntre(inicio, ultimo, anio.takeIf { anioDeHoy != null && it != anioDeHoy })
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

/**
 * **El rótulo de `entradas − salidas`** (vuelta de diseño 28). Decía «Te quedó», y «Te quedó −$817.250»
 * en rojo se leía como lo que quedó para gastar —otra versión de «Te quedan» de Hoy— o como una
 * alarma. Es el neto del flujo del período: lo que entró menos lo que salió.
 */
const val ROTULO_NETO_DEL_PERIODO: String = "Neto del período"

/** `entradas − salidas`: el neto del flujo del período. */
internal fun netoDelPeriodoDe(resumen: ResumenDePeriodo): Long = resumen.entradas - resumen.salidas

/**
 * El tono del neto del período: «+$1,2M» en `entra` si entró más de lo que salió, «−$1,2M» **en el
 * color del texto** si salió más, «$0» apagado. El signo y el color los pone la
 * [com.jvillada.movi.ui.components.ds.Cifra] con este tono.
 *
 * **Un neto negativo no es una alerta** (vuelta de diseño 28): un período en que salió más de lo que
 * entró puede haberse pagado con lo que ya había (ver [loQueCubrioLoDemas]); el rojo queda para lo
 * que pide algo, como gastar más de lo que había para el período. Es la regla del «Neto» de
 * Movimientos ([com.jvillada.movi.ui.transactions.tonoDelNeto]): el mismo concepto, el mismo tono.
 */
internal fun tonoDelNetoDelPeriodo(monto: Long): TonoDeCifra = com.jvillada.movi.ui.transactions.tonoDelNeto(monto)

/**
 * [elegida]: es el período que muestra el detalle de al lado (solo en la web). Se marca con un borde
 * del color de la marca, y se anuncia como seleccionada.
 */
@Composable
private fun FilaDePeriodo(resumen: ResumenDePeriodo, elegida: Boolean, onClick: () -> Unit) {
    // La elegida (solo en la web, con el detalle al lado) se marca con el foco de la marca: un
    // trazo de `marca` —la marca es acción y foco— y se anuncia como seleccionada.
    val marcaDeElegida = if (elegida) {
        Modifier
            .border(Medida.trazoDeCarga, Movi.colores.texto, RoundedCornerShape(Radio.l))
            .semantics { selected = true }
    } else {
        Modifier
    }
    Superficie(
        modifier = Modifier.fillMaxWidth().then(marcaDeElegida).testTag(TAG_FILA_DE_PERIODO),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Vuelta 75: el nombre único del período (`nombreDelPeriodo`), sin el rango largo debajo.
                Text(text = nombreDelResumen(resumen), style = Movi.textos.titulo, color = Movi.colores.texto)
                // «En curso» y «Empezó el 24» son estados del período: píldoras, no texto de color.
                val marcas = marcasDe(resumen)
                if (marcas.isNotEmpty()) {
                    Spacer(Modifier.height(Espacio.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                        marcas.forEach { Pildora(it, if (it == "En curso") TonoDePildora.Aviso else TonoDePildora.Neutra) }
                    }
                }
            }
            Chevron()
        }
        CifrasDelPeriodo(resumen)
        LineaDeCreditosDesembolsados(resumen)
        val cubierto = loQueCubrioLoDemas(resumen)
        if (cubierto != null) {
            Spacer(Modifier.height(Espacio.xs))
            Text(
                text = cubierto,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_LO_QUE_CUBRIO),
            )
        }
    }
}

/**
 * La línea que explica el hueco de un período que salió más de lo que entró: «Lo demás lo cubrieron
 * $1,5M de saldos que ya tenías.» `null` cuando no salió de más o no hay saldos que decir — ni «$0»
 * ni una explicación que no existe.
 *
 * **La cifra es lo que faltó, como mucho:** `minOf(saldosIniciales, salidas − entradas)`. Los saldos
 * que Movi conoció en el período pueden ser muchísimo más que el hueco ($22,2M contra un faltante
 * de $1,5M), y decir «lo cubrieron $22,2M» al lado de «Neto del período −$1,5M» se lee como un hueco de
 * $22,2M. Los saldos no cubren más de lo que faltó. El detalle nombra las cuentas en «De dónde salió
 * lo que faltó» ([filasDeLoQueFalto]) con el mismo tope.
 *
 * Los créditos ya no van acá: el desembolso es plata que entró y suma en «Entró»; su parte se dice
 * aparte, en [textoDeCreditosDesembolsados].
 */
internal fun loQueCubrioLoDemas(resumen: ResumenDePeriodo): String? {
    val faltante = resumen.salidas - resumen.entradas
    if (faltante <= 0) return null
    val cubierto = minOf(resumen.saldosIniciales, faltante)
    if (cubierto <= 0) return null
    return "Lo demás lo cubrieron ${formatMoneyCompact(cubierto)} de saldos que ya tenías."
}

/**
 * «Incluye $10M de créditos desembolsados.»: cuánto de «Entró» es plata prestada y no sueldo
 * ([ResumenDePeriodo.creditosRecibidos]). `null` sin créditos, o si no hay nada que haya entrado
 * (la línea explica una cifra que tiene que estar).
 */
internal fun textoDeCreditosDesembolsados(resumen: ResumenDePeriodo): String? {
    if (resumen.creditosRecibidos <= 0 || resumen.entradas <= 0) return null
    return "Incluye ${formatMoneyCompact(resumen.creditosRecibidos)} de créditos desembolsados."
}

/** La línea de [textoDeCreditosDesembolsados], la misma en la fila de la lista y en el detalle. */
@Composable
internal fun LineaDeCreditosDesembolsados(resumen: ResumenDePeriodo) {
    val texto = textoDeCreditosDesembolsados(resumen) ?: return
    Spacer(Modifier.height(Espacio.xs))
    Text(
        text = texto,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        modifier = Modifier.testTag(TAG_INCLUYE_CREDITOS),
    )
}

/**
 * Entró / salió / neto del período con la barra de dos tramos, o «Sin movimientos de flujo» con palabras.
 * La misma pieza en la fila de la lista y arriba del detalle: un período se lee igual en los dos.
 */
@Composable
internal fun CifrasDelPeriodo(resumen: ResumenDePeriodo) {
    val fraccion = fraccionQueEntro(resumen.entradas, resumen.salidas)
    if (fraccion == null) {
        Spacer(Modifier.height(Espacio.xs))
        Text(text = "Sin movimientos de flujo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    } else {
        Spacer(Modifier.height(Espacio.s))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Vuelta de diseño 19: `CifraConRotulo`, compacta porque son tres en un renglón (§6).
            CifraConRotulo("Entró", resumen.entradas, tono = TonoDeCifra.Ingreso, forma = FormaDeCifra.Compacta)
            // Ola 2a: lo que salió en neutro. Vuelta de diseño 28: el neto negativo también.
            CifraConRotulo("Salió", resumen.salidas, forma = FormaDeCifra.Compacta)
            val neto = netoDelPeriodoDe(resumen)
            CifraConRotulo(
                ROTULO_NETO_DEL_PERIODO,
                neto,
                tono = tonoDelNetoDelPeriodo(neto),
                forma = FormaDeCifra.Compacta,
                alFinal = true,
            )
        }
        Spacer(Modifier.height(Espacio.xs))
        BarraDeDosTramos(
            fraccionIzquierda = fraccion,
            colorIzquierda = Movi.colores.entra,
            colorDerecha = Movi.colores.neutro,
            entrada = 1f,
        )
    }
}

/**
 * [FilaDePeriodo] mientras no llegó: misma forma, sin una sola cifra. [conMarca] reserva el
 * renglón de «En curso»/«Empezó el…» — ver el comentario en [PeriodosScreen] sobre por qué solo la
 * primera fila lo pide.
 */
@Composable
private fun FilaDePeriodoEsqueleto(conMarca: Boolean) {
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_PERIODO_ESQUELETO)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Vuelta 75: el nombre del período solo, sin el rango largo debajo (como la fila cargada).
                LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.titulo)
                if (conMarca) {
                    Spacer(Modifier.height(Espacio.xs))
                    BloqueEsqueleto(alto = Medida.altoDePildora, ancho = Medida.avatar * 2)
                }
            }
        }
        CifrasDelPeriodoEsqueleto()
    }
}

/** [CifrasDelPeriodo] mientras no llegó: los tres pares y la barra, sin una sola cifra. */
@Composable
internal fun CifrasDelPeriodoEsqueleto() {
    Spacer(Modifier.height(Espacio.s))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(3) { i -> EsqueletoDeCifraConRotulo(alFinal = i == 2) }
    }
    Spacer(Modifier.height(Espacio.xs))
    BloqueEsqueleto(alto = Movi.espacios.corto)
}

/** [id] es el prefijo del período («2026-09») → «Septiembre 2026»; `null` si no se pudo leer. */
internal fun nombreDelId(id: String, ajustes: PeriodSettings? = null): String? = periodoDelPrefijo(id)?.let {
    // Vuelta 75: con los ajustes del dueño, el nombre único del período; sin ellos, el del mes.
    if (ajustes != null) nombreDelPeriodo(it, ajustes) else tituloDelPeriodo(it)
}
