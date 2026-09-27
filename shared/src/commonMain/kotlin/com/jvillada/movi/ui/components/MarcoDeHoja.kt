package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi
import kotlinx.coroutines.launch

/** El ancho máximo de una hoja centrada (mediano y expandido). */
val ANCHO_DE_HOJA: Dp = 560.dp

/** La hoja de «Agregar» es un poco más ancha: el teclado numérico respira mejor. */
val ANCHO_DE_LA_HOJA_DE_AGREGAR: Dp = 600.dp

/** Qué fracción del alto puede ocupar una hoja centrada: el velo se sigue viendo arriba y abajo. */
const val FRACCION_DE_ALTO_DE_HOJA_CENTRADA = 0.9f

/** El panel de la hoja, para que las pruebas lo midan. */
const val TAG_PANEL_DE_HOJA = "marco-de-hoja-panel"

/**
 * # El marco de toda hoja modal de Movi (Ola W1)
 *
 * Hasta acá unas veinticinco hojas armaban cada una su propio marco —velo, panel pegado abajo,
 * esquinas de arriba redondeadas, manija con la X— copiado a mano. En el teléfono eso está bien: la
 * hoja sube desde el pulgar. En la web, a 1.920 px, era una franja de 600 dp pegada al piso de la
 * ventana.
 *
 * - **Compacto** (< 600 dp): **idéntico** a lo que cada hoja dibujaba: velo al 60 %, panel de ancho
 *   completo pegado abajo, esquinas de arriba de 28 dp y [SheetHandleWithClose]. Las pruebas de
 *   geometría (`HojaAgregarGeometriaTest` y compañía) miden con margen cero, y no se movieron.
 * - **Mediano y expandido**: el mismo velo y el panel **centrado**, de a lo sumo [anchoMaximo] de
 *   ancho y el [FRACCION_DE_ALTO_DE_HOJA_CENTRADA] del alto, con las cuatro esquinas redondeadas y
 *   solo la X (no hay nada que arrastrar). **Escape** cierra, con la misma condición que el toque
 *   afuera ([dismissEnabled]) — salvo que la hoja pase [onEscape] (ver abajo).
 *
 * Nada de `Dialog` ni `Popup`: la hoja se sigue dibujando dentro del árbol, así que
 * [com.jvillada.movi.ui.PilaDeHojas] y las pruebas de geometría la siguen viendo. En el teléfono se
 * dibuja en la columna de la pantalla, como siempre; la centrada, en la cáscara, encima del rail, y
 * su velo cubre la ventana entera (ver [AnfitrionDeHojas]).
 *
 * [fraccionDeAltoMaximo] acota el alto también en el teléfono (la hoja de recurrentes necesita un
 * alto acotado para su propio `BoxWithConstraints`); sin él, en el teléfono la hoja mide lo que su
 * contenido. [conCierre] en `false` quita el renglón de la manija y la X: las confirmaciones cortas
 * se cierran con su «Cancelar». [relleno] es el del panel.
 *
 * ### Escape en una hoja con sub-editores
 *
 * En escritorio Escape es «salir de esto», y en una hoja con sub-editores propios (la Nota, la
 * Categoría o la Cuenta de «Agregar») «esto» es el sub-editor, no la hoja: cerrar la hoja entera
 * perdía el movimiento a medio anotar. Una hoja así pasa [onEscape] y decide ella: cierra primero lo
 * que tenga abierto y solo sin nada abierto llama a su `onDismiss` (respetando su propio
 * `dismissEnabled`, que en ese caso MarcoDeHoja no mira). Sin [onEscape], Escape es el toque afuera.
 *
 * ### El foco (y por qué importa el orden)
 *
 * Una tecla le llega solo a quien tiene el foco o a sus ancestros, así que el velo de la hoja
 * centrada es un destino de foco y **lo pide al abrir**. Escape se atrapa en el *preview* (de la
 * raíz hacia adentro), así que llega aunque el foco esté en un campo de la hoja: el campo no se lo
 * puede tragar.
 *
 * - Un campo de la hoja que pida el foco al abrir **desde adentro del contenido** lo pide después
 *   que el velo y se lo queda (la Nota de «Agregar»). Uno que lo pida **desde afuera** de
 *   `MarcoDeHoja` —un `LaunchedEffect` declarado arriba de la llamada— lo pierde: el velo vive en
 *   una subcomposición (`BoxWithConstraints`) y su efecto corre después. Pide el foco adentro.
 * - Si el foco se pierde con la hoja abierta (se cerró el sub-editor que lo tenía), la hoja de más
 *   arriba lo recupera; y al cerrarse la de arriba, la de abajo lo retoma. Así un segundo Escape
 *   siempre le llega a alguien. Quién está arriba lo lleva [PilaDeFocoDeHojas], que da
 *   [CascaraDeAncho].
 */
@Composable
fun MarcoDeHoja(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    anchoMaximo: Dp = ANCHO_DE_HOJA,
    fraccionDeAltoMaximo: Float? = null,
    conCierre: Boolean = true,
    relleno: PaddingValues = PaddingValues(horizontal = 20.dp),
    onEscape: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        HojaDesdeAbajo(onDismiss, dismissEnabled, fraccionDeAltoMaximo, conCierre, relleno, content)
    } else {
        val alEscape = onEscape ?: { if (dismissEnabled) onDismiss() }
        val hoja: @Composable () -> Unit = {
            HojaCentrada(onDismiss, dismissEnabled, anchoMaximo, fraccionDeAltoMaximo, conCierre, relleno, alEscape, content)
        }
        val anfitrion = LocalAnfitrionDeHojas.current
        if (anfitrion == null) {
            hoja()
        } else {
            // La hoja se dibuja en la cáscara, encima del rail y de toda la ventana, y no acá adentro
            // de la columna de la pantalla (ver [AnfitrionDeHojas]). Se lleva los `CompositionLocal`
            // de este punto para que su contenido vea lo mismo que vería acá (LocalNavigate, el
            // cargando del Inicio, etc.).
            val contexto = currentCompositionLocalContext
            val conContexto: @Composable () -> Unit = { CompositionLocalProvider(contexto) { hoja() } }
            val entrada = remember(anfitrion) { AnfitrionDeHojas.Entrada(conContexto) }
            SideEffect { entrada.contenido = conContexto }
            DisposableEffect(anfitrion, entrada) {
                anfitrion.abrir(entrada)
                onDispose { anfitrion.cerrar(entrada) }
            }
        }
    }
}

/**
 * # Dónde se dibujan las hojas centradas (Ola W1)
 *
 * Cada hoja se escribe adentro de su pantalla, y la pantalla vive en la columna topada de la
 * cáscara. Dibujada ahí, la hoja centrada oscurecía solo esa columna: a 1.920 dp quedaban dos franjas
 * claras a los lados, y a 768/1.280 el rail seguía encendido y se podía tocar con la hoja abierta.
 *
 * El anfitrión lo pone `EsqueletoDeLaCascara` (App.kt) **encima de todo**: rail, márgenes y columna.
 * [MarcoDeHoja], en mediano y expandido, se anota acá en vez de dibujarse en su lugar; la hoja sigue
 * siendo parte del árbol (nada de `Dialog`/`Popup`), solo que de un punto más arriba. En el teléfono
 * no cambia nada: la hoja desde abajo se dibuja donde siempre. Sin anfitrión (una prueba que monta
 * una pantalla suelta) la hoja se dibuja en su lugar.
 */
class AnfitrionDeHojas {
    /** Una hoja anotada; [contenido] se actualiza en cada recomposición de quien la abrió. */
    class Entrada(contenido: @Composable () -> Unit) {
        var contenido by mutableStateOf(contenido)
    }

    /** Las hojas abiertas, de abajo hacia arriba: en el orden en que se abrieron. */
    val abiertas = mutableStateListOf<Entrada>()

    fun abrir(entrada: Entrada) {
        abiertas.add(entrada)
    }

    fun cerrar(entrada: Entrada) {
        abiertas.remove(entrada)
    }

    /** Dibuja las hojas abiertas. Va al final de la cáscara, para quedar encima de todo. */
    @Composable
    fun Hojas() {
        abiertas.forEach { entrada -> key(entrada) { entrada.contenido() } }
    }
}

/** El anfitrión de la cáscara; `null` fuera de ella. */
val LocalAnfitrionDeHojas = compositionLocalOf<AnfitrionDeHojas?> { null }

/** El velo de la hoja centrada, para que las pruebas midan qué cubre. */
const val TAG_VELO_DE_HOJA = "marco-de-hoja-velo"

/**
 * Las hojas centradas abiertas, de abajo hacia arriba, para saber a cuál le toca el foco. Ver
 * «El foco» en [MarcoDeHoja]. Vive en la composición (la da [CascaraDeAncho]), no en un `object`:
 * se arma y se desarma con la app.
 */
class PilaDeFocoDeHojas {
    /** Una hoja abierta; [reclamar] le devuelve el foco a su velo. */
    class Hoja(val reclamar: () -> Unit)

    private val abiertas = mutableListOf<Hoja>()

    fun abrir(hoja: Hoja) {
        abiertas.add(hoja)
    }

    /** Saca [hoja]; si era la de arriba, la que queda arriba retoma el foco. */
    fun cerrar(hoja: Hoja) {
        val eraLaDeArriba = abiertas.lastOrNull() === hoja
        abiertas.remove(hoja)
        if (eraLaDeArriba) abiertas.lastOrNull()?.reclamar()
    }

    fun esLaDeArriba(hoja: Hoja): Boolean = abiertas.lastOrNull() === hoja
}

/** `null` fuera de [CascaraDeAncho]: cada hoja se arregla sola, sin apilarse. */
val LocalPilaDeFocoDeHojas = compositionLocalOf<PilaDeFocoDeHojas?> { null }

@Composable
private fun HojaDesdeAbajo(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    fraccionDeAltoMaximo: Float?,
    conCierre: Boolean,
    relleno: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    @Composable
    fun Velo(altoMaximo: Dp?) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = dismissEnabled, onClick = onDismiss),
        ) {
            // El hueco de arriba empuja la hoja contra el borde de abajo. Un `Box(weight(1f))` y no
            // un `align`: la hoja se mide primero, con todo el alto, y el hueco se lleva lo que sobra.
            Box(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (altoMaximo != null) Modifier.heightIn(max = altoMaximo) else Modifier)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Movi.colores.tarjeta)
                    .clickable(enabled = false) {}
                    .padding(relleno)
                    .testTag(TAG_PANEL_DE_HOJA),
            ) {
                if (conCierre) SheetHandleWithClose(onClose = onDismiss, enabled = dismissEnabled)
                content()
            }
        }
    }
    if (fraccionDeAltoMaximo == null) {
        Velo(altoMaximo = null)
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Velo(altoMaximo = maxHeight * fraccionDeAltoMaximo)
        }
    }
}

@Composable
private fun HojaCentrada(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    anchoMaximo: Dp,
    fraccionDeAltoMaximo: Float?,
    conCierre: Boolean,
    relleno: PaddingValues,
    alEscape: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val foco = remember { FocusRequester() }
    val alcance = rememberCoroutineScope()
    val pila = LocalPilaDeFocoDeHojas.current ?: remember { PilaDeFocoDeHojas() }
    val estaHoja = remember {
        PilaDeFocoDeHojas.Hoja(reclamar = { alcance.launch { runCatching { foco.requestFocus() } } })
    }
    DisposableEffect(pila) {
        pila.abrir(estaHoja)
        onDispose { pila.cerrar(estaHoja) }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Escape es «salir de esto», como en cualquier ventana modal de escritorio. En el
            // «preview» (de la raíz hacia adentro) para que llegue aunque el foco esté en un campo
            // de la hoja. Qué cierra lo decide [alEscape] (ver [MarcoDeHoja]).
            .onPreviewKeyEvent { evento ->
                if (evento.key == Key.Escape && evento.type == KeyEventType.KeyDown) {
                    alEscape()
                    true
                } else {
                    false
                }
            }
            // Si el foco se va de esta hoja mientras sigue abierta y arriba —se cerró el sub-editor
            // cuyo campo lo tenía—, vuelve al velo: si no, el Escape siguiente no le llega a nadie.
            .onFocusChanged { estado ->
                if (!estado.hasFocus && pila.esLaDeArriba(estaHoja)) estaHoja.reclamar()
            }
            // Una tecla solo le llega a quien tiene el foco o a sus ancestros: sin un destino de
            // foco adentro, Escape no le llegaría a nadie mientras no se toque un campo.
            .focusRequester(foco)
            .focusable()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = dismissEnabled, onClick = onDismiss)
            .testTag(TAG_VELO_DE_HOJA)
            // El velo va de borde a borde; el panel, dentro de lo que dejan las barras del sistema
            // y el teclado (antes se lo daban la columna de la pantalla: `statusBarsPadding` e
            // `imePadding`).
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        // Antes del contenido a propósito: si un campo de la hoja pide el foco al abrir, lo pide
        // después y se lo queda.
        LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
        val fraccion = minOf(fraccionDeAltoMaximo ?: FRACCION_DE_ALTO_DE_HOJA_CENTRADA, FRACCION_DE_ALTO_DE_HOJA_CENTRADA)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = anchoMaximo)
                .fillMaxWidth()
                .heightIn(max = maxHeight * fraccion)
                .clip(RoundedCornerShape(28.dp))
                .background(Movi.colores.tarjeta)
                .clickable(enabled = false) {}
                .padding(relleno)
                .testTag(TAG_PANEL_DE_HOJA),
        ) {
            if (conCierre) SheetHandleWithClose(onClose = onDismiss, enabled = dismissEnabled, conManija = false)
            content()
        }
    }
}
