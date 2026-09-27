package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Lectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.periodoVigenteSegun
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import kotlinx.datetime.LocalDate

/**
 * # «Tus períodos» en lista + detalle (Ola W2)
 *
 * El dueño, en Chrome: Tus períodos y su detalle se veían como la app del teléfono estirada, una
 * columna angosta con márgenes enormes a los lados. Acá, en mediano y expandido, la lista va a la
 * izquierda ([anchoDeLaListaDePeriodos], con su propio scroll) y el detalle del período elegido a la
 * derecha, con todo lo que sobra.
 *
 * - **Teléfono**: las dos pantallas de siempre, sin tocar ([PeriodosScreen] sin [idPedido],
 *   [DetalleDePeriodoScreen] con él). Tocar una fila navega al detalle.
 * - **Mediano y expandido**: tocar una fila **elige** el período y el panel de la derecha lo
 *   muestra; no se apila nada en la navegación (ver [alTocarUnPeriodo]). La elección vive acá, en un
 *   `rememberSaveable`. Arranca en el período en curso, salvo que la pantalla traiga uno
 *   ([Screen.DetalleDePeriodo], desde un enlace o desde «Ver más» del Inicio): ese gana
 *   ([periodoElegido]).
 * - **Una ventana mediana angosta** (menos de [ANCHO_MINIMO_PARA_LISTA_Y_DETALLE] de contenido, o
 *   sea menos de ~760 dp de ventana con el rail): al detalle le quedarían menos de 320 dp, menos que
 *   el teléfono más chico, así que se ve como en el teléfono.
 *
 * El perfil se lee **una sola vez** para la lista y el detalle ([PerfilCompartido]): los dos lo
 * necesitan para saber el período vigente, y con dos lecturas se pediría dos veces lo mismo.
 * Cada período conserva su propia clave de caché (`ClaveDeLectura.DetalleDePeriodo(id)`), así que
 * volver a uno ya visto lo pinta al primer cuadro con «Actualizando…», igual que en el teléfono.
 */

const val TAG_PANEL_DE_LA_LISTA: String = "periodos-panel-de-la-lista"
const val TAG_PANEL_DEL_DETALLE: String = "periodos-panel-del-detalle"

/** El ancho de la lista en una ventana mediana: una tarjeta de teléfono de 328 dp. */
val ANCHO_DE_LA_LISTA_EN_MEDIANO: Dp = 360.dp

/**
 * El ancho de la lista en escritorio: el mismo que en mediano. Más ancha no se lee mejor, y cada dp
 * que se lleva la lista le falta al detalle para partirse en dos columnas en una laptop de 1280.
 */
val ANCHO_DE_LA_LISTA_EN_EXPANDIDO: Dp = 360.dp

/**
 * Por debajo de este ancho de contenido la lista no va al lado del detalle: 360 de lista más 320 de
 * detalle, el ancho del teléfono más chico que se soporta.
 */
val ANCHO_MINIMO_PARA_LISTA_Y_DETALLE: Dp = 680.dp

/**
 * Lo mínimo que tiene que medir cada columna del detalle para partirlo en dos: 320 dp, el teléfono
 * más chico que se soporta (el mismo de [ANCHO_MINIMO_PARA_LISTA_Y_DETALLE]). Las secciones del
 * detalle ya se leen en una tarjeta de ese ancho; más angostas no.
 */
val ANCHO_MINIMO_DE_COLUMNA_DEL_DETALLE: Dp = 320.dp

/**
 * Lo que del panel no es columna: el relleno de 16 a cada lado y los 16 entre las dos
 * (`Movi.espacios.amplio`).
 */
private val RELLENO_DE_LAS_DOS_COLUMNAS: Dp = 48.dp

/** El ancho fijo de la lista de períodos al lado del detalle. */
fun anchoDeLaListaDePeriodos(clase: WindowWidthClass): Dp =
    if (clase == WindowWidthClass.Expanded) ANCHO_DE_LA_LISTA_EN_EXPANDIDO else ANCHO_DE_LA_LISTA_EN_MEDIANO

/** Si en [anchoDelContenido] caben la lista y el detalle lado a lado. */
fun hayLugarParaListaYDetalle(anchoDelContenido: Dp): Boolean = anchoDelContenido >= ANCHO_MINIMO_PARA_LISTA_Y_DETALLE

/**
 * Si el detalle, en un panel de [anchoDelPanel], va en dos columnas: cuando a cada una le quedan al
 * menos [ANCHO_MINIMO_DE_COLUMNA_DEL_DETALLE], o sea con un panel de 688 dp o más. Se decide por el
 * ancho de la columna y no del panel porque es la columna la que tiene que caber.
 *
 * En la web con el rail ancho (216) y la lista (360 + 1 de divisor): 1024 dp de ventana → panel
 * 447, una columna; 1200 → 623, una; **1280 → 703, dos de ~327**; 1440 → 863, dos de ~407; 1920
 * (contenido topado en 1440) → 1079, dos de ~515. En mediano el panel no pasa de 479: una columna.
 */
fun detalleEnDosColumnas(anchoDelPanel: Dp): Boolean =
    (anchoDelPanel - RELLENO_DE_LAS_DOS_COLUMNAS) / 2 >= ANCHO_MINIMO_DE_COLUMNA_DEL_DETALLE

/**
 * El período que muestra el panel de la derecha: el [elegido] (un id pedido, o una fila tocada); si
 * no hay, el vigente según el perfil —se sabe antes que la lista, así el detalle empieza a leerse
 * ya—; si tampoco, el «En curso» de la lista, y si no, su primero. `null` mientras no se sabe nada.
 */
internal fun periodoElegido(elegido: String?, periodoVigente: String?, periodos: List<ResumenDePeriodo>?): String? =
    elegido
        ?: periodoVigente
        ?: periodos?.firstOrNull { it.enCurso }?.id
        ?: periodos?.firstOrNull()?.id

/** Qué hace tocar una fila de la lista. */
internal sealed interface AlTocarUnPeriodo {
    /** En el teléfono: ir a la pantalla del detalle, como siempre. */
    data class Navegar(val destino: Screen) : AlTocarUnPeriodo

    /** Con el detalle al lado: mostrarlo ahí, sin navegar. */
    data class Elegir(val id: String) : AlTocarUnPeriodo
}

internal fun alTocarUnPeriodo(id: String, conDetalleAlLado: Boolean): AlTocarUnPeriodo =
    if (conDetalleAlLado) AlTocarUnPeriodo.Elegir(id) else AlTocarUnPeriodo.Navegar(Screen.DetalleDePeriodo(id))

/**
 * El perfil leído una vez para la lista y el detalle. [releer] lo vuelve a pedir: lo llama el
 * «Reintentar» de cualquiera de los dos, y el detalle tras una escritura.
 */
@Immutable
internal class PerfilCompartido(val lectura: Lectura<UserProfile>, val releer: () -> Unit)

/**
 * Lo que App.kt monta para [Screen.Periodos] (sin [idPedido]) y [Screen.DetalleDePeriodo] (con él).
 * Ver el KDoc del archivo.
 *
 * @param hoy el día de hoy en la zona de la app; solo lo fija una prueba.
 */
@Composable
fun PeriodosListaYDetalle(onNavigate: (Screen) -> Unit, idPedido: String?, hoy: LocalDate? = null) {
    val clase = LocalWindowWidthClass.current
    if (clase == WindowWidthClass.Compact) {
        EnUnaSolaHoja(onNavigate, idPedido, hoy)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (hayLugarParaListaYDetalle(maxWidth)) {
            ListaAlLadoDelDetalle(onNavigate, idPedido, hoy, anchoDeLaListaDePeriodos(clase))
        } else {
            EnUnaSolaHoja(onNavigate, idPedido, hoy)
        }
    }
}

/** Las pantallas del teléfono, tal cual. */
@Composable
private fun EnUnaSolaHoja(onNavigate: (Screen) -> Unit, idPedido: String?, hoy: LocalDate?) {
    if (idPedido == null) PeriodosScreen(onNavigate) else DetalleDePeriodoScreen(onNavigate, idPedido, hoy)
}

@Composable
private fun ListaAlLadoDelDetalle(onNavigate: (Screen) -> Unit, idPedido: String?, hoy: LocalDate?, anchoDeLaLista: Dp) {
    val recargaDelPerfil = remember { mutableIntStateOf(0) }
    val lecturaDelPerfil = rememberLectura(ClaveDeLectura.Perfil, recargaDelPerfil.intValue) {
        Repositories.wallets.getUserProfile()
    }
    val perfil = remember(lecturaDelPerfil) {
        PerfilCompartido(lecturaDelPerfil, releer = { recargaDelPerfil.intValue++ })
    }
    val lista = rememberEstadoDePeriodos(perfil)
    // El scroll de la lista vive acá: elegir otro período recompone la fila marcada, no la lista.
    val estadoDeLaLista = rememberLazyListState()
    var elegido by rememberSaveable(idPedido) { mutableStateOf(idPedido) }
    val mostrado = periodoElegido(elegido, periodoVigenteSegun(lecturaDelPerfil), lista.periodos)

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(anchoDeLaLista).fillMaxHeight().testTag(TAG_PANEL_DE_LA_LISTA)) {
            ListaDePeriodos(
                onNavigate = onNavigate,
                estado = lista,
                elegido = mostrado,
                onTocar = { id ->
                    when (val accion = alTocarUnPeriodo(id, conDetalleAlLado = true)) {
                        is AlTocarUnPeriodo.Elegir -> elegido = accion.id
                        is AlTocarUnPeriodo.Navegar -> onNavigate(accion.destino)
                    }
                },
                estadoDeLaLista = estadoDeLaLista,
            )
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Movi.colores.hilo))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_PANEL_DEL_DETALLE)) {
            if (mostrado != null) {
                ContenidoDelDetalleDePeriodo(
                    onNavigate = onNavigate,
                    id = mostrado,
                    conCabecera = true,
                    conFlecha = false,
                    hoy = hoy,
                    perfilCompartido = perfil,
                )
            }
        }
    }
}
