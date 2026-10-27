package com.jvillada.movi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movimiento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
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
 *   completo pegado abajo, esquinas de arriba de `Radio.xl` (24; eran 28 hasta la ola R0) y
 *   [SheetHandleWithClose]. Las pruebas de
 *   geometría (`HojaAgregarGeometriaTest` y compañía) miden con margen cero, y no se movieron.
 * - **Mediano y expandido**: el mismo velo y el panel **centrado**, de a lo sumo [anchoMaximo] de
 *   ancho y el [FRACCION_DE_ALTO_DE_HOJA_CENTRADA] del alto, con las cuatro esquinas redondeadas y
 *   solo la X (no hay nada que arrastrar).
 *
 * En las dos, **Escape** cierra con la misma condición que el toque afuera ([dismissEnabled]) —
 * salvo que la hoja pase [onEscape] (ver abajo)—, también con un campo de la hoja enfocado. La del
 * teléfono lo atiende desde la vuelta de diseño 17: antes, en la web angosta, solo la X la cerraba.
 *
 * Nada de `Dialog` ni `Popup`: la hoja se sigue dibujando dentro del árbol, así que
 * [com.jvillada.movi.ui.PilaDeHojas] y las pruebas de geometría la siguen viendo. Las dos se dibujan
 * en la cáscara, encima de todo, y su velo cubre la ventana entera (ver [AnfitrionDeHojas]): la del
 * teléfono también desde la Ola 1 · «Que Movi se sienta viva», que es cuando **dejó de verse la barra
 * de pestañas debajo de una hoja abierta** (y de poder tocarse).
 *
 * Entran, salen y —la del teléfono— se cierran arrastrando hacia abajo: ver [EstadoDeLaHoja].
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
 *
 * ### Otras teclas: [onTecla]
 *
 * Una hoja que quiera atender el teclado físico (los dígitos del monto en «Agregar», Backspace,
 * Enter) pasa [onTecla]: le llegan en el *preview* todas las teclas que no son el Escape de arriba,
 * esté donde esté el foco dentro de la hoja, y devuelve `true` si la consumió. Decidir que un campo
 * de texto con el foco se quede con sus teclas es trabajo de la hoja, no del marco.
 *
 * En la hoja desde abajo (la web por debajo de 600 dp) el panel solo se vuelve destino de foco
 * cuando hay [onTecla] **y** la plataforma tiene teclado físico ([LocalHayTecladoFisico]): en el
 * teléfono Android no, porque un panel con el foco al abrir es exactamente lo que
 * `SelectorDeCategoriaEnAgregarTest` prohíbe (nada puede tener el foco al abrir un selector). Sin
 * [onTecla] y con teclado físico, el que toma el foco es el velo, y solo si nadie de adentro lo
 * tomó al abrir: así Escape le llega a la hoja aunque no se haya tocado ningún campo.
 */
@Composable
fun MarcoDeHoja(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    anchoMaximo: Dp = ANCHO_DE_HOJA,
    fraccionDeAltoMaximo: Float? = null,
    conCierre: Boolean = true,
    relleno: PaddingValues = PaddingValues(horizontal = Medida.margenDePantalla),
    onEscape: (() -> Unit)? = null,
    onTecla: ((KeyEvent) -> Boolean)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val anfitrion = LocalAnfitrionDeHojas.current
    val compacta = LocalWindowWidthClass.current == WindowWidthClass.Compact
    val hoja: @Composable () -> Unit = if (compacta) {
        {
            HojaDesdeAbajo(
                onDismiss, dismissEnabled, fraccionDeAltoMaximo, conCierre, relleno, onEscape, onTecla,
                enLaCascara = anfitrion != null, content = content,
            )
        }
    } else {
        { HojaCentrada(onDismiss, dismissEnabled, anchoMaximo, fraccionDeAltoMaximo, conCierre, relleno, onEscape, onTecla, content) }
    }
    if (anfitrion == null) {
        hoja()
    } else {
        // La hoja se dibuja en la cáscara, encima de la barra de pestañas (o del rail) y de toda la
        // ventana, y no acá adentro de la columna de la pantalla (ver [AnfitrionDeHojas]). Se lleva
        // los `CompositionLocal` de este punto para que su contenido vea lo mismo que vería acá
        // (LocalNavigate, el cargando del Inicio, etc.).
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

/**
 * # Cómo entra, cómo sale y cómo se arrastra una hoja (Ola 1 · «Que Movi se sienta viva»)
 *
 * Hasta acá las hojas aparecían de golpe y se iban de golpe. Ahora:
 *
 * - **entran**: desde abajo (la del teléfono) o fundiéndose (la centrada), con el velo
 *   oscureciéndose al mismo tiempo. Duraciones y curvas de [Movimiento];
 * - **salen animadas** cuando las cierra el marco —el velo, la X, Escape o arrastrarlas—: primero se
 *   van y **después** se llama al `onDismiss`, una sola vez aunque se toque el velo dos veces
 *   mientras sale ([cerrar] ignora lo que llegue con la salida en curso). Si quien la abrió decide
 *   no cerrarla (su `onDismiss` no la saca de la composición), la hoja vuelve a su lugar;
 * - lo que la cierra **desde adentro** (un «Guardar», un «Cancelar», el «atrás» del sistema) la saca
 *   en el acto, como antes: el contenido es de cada hoja y el marco no puede retrasarlo sin arriesgar
 *   que algo de adentro se dibuje con un estado que su dueño ya soltó.
 *
 * Con el sistema pidiendo menos movimiento ([LocalMovimientoReducido]), todo esto pasa en el acto.
 */
@Stable
private class EstadoDeLaHoja(
    private val alcance: CoroutineScope,
    reducido: Boolean,
) {
    /** 0 = afuera (abajo, o transparente), 1 = en su lugar. */
    val presencia = Animatable(if (reducido) 1f else 0f)

    /** Cuánto la bajó el dedo, en píxeles. Solo la del teléfono. */
    var arrastre by mutableFloatStateOf(0f)

    /** El alto del panel, para saber cuándo un arrastre pasó el umbral. */
    var altoDelPanel by mutableIntStateOf(0)

    /** Con la salida en curso: el velo ya no responde y el `onDismiss` ya está en camino. */
    var cerrando by mutableStateOf(false)
        private set

    var onDismiss: () -> Unit = {}
    var dismissEnabled: Boolean = true
    var reducido: Boolean = reducido

    /** ¿Hay un dedo sobre el panel? Ver el `pointerInput` de la hoja desde abajo. */
    var dedoAbajo: Boolean = false

    private var asentando: Job? = null

    /**
     * El dedo se levantó. Si la hoja quedó corrida y nadie la soltó con una velocidad (ni la lista de
     * adentro ni el arrastre del panel, que llegan después del «arriba»), se suelta quieta.
     */
    fun dedoSoltado(umbralDeVelocidad: Float) {
        alcance.launch {
            withFrameNanos { }
            withFrameNanos { }
            if (!dedoAbajo && arrastre > 0f && asentando?.isActive != true) soltar(0f, umbralDeVelocidad)
        }
    }

    fun entrar() {
        alcance.launch { presencia.animateTo(1f, Movimiento.alEntrar(reducido)) }
    }

    /** Sale animada y después llama a `onDismiss`, una sola vez. Respeta [dismissEnabled]. */
    fun cerrar() {
        if (cerrando || !dismissEnabled) return
        cerrando = true
        asentando?.cancel()
        alcance.launch {
            presencia.animateTo(0f, Movimiento.alSalir(reducido))
            onDismiss()
            // Si la hoja sigue en la composición dos cuadros después, quien la abrió decidió no
            // cerrarla (preguntó antes, por ejemplo): vuelve a su lugar en vez de quedar invisible
            // tapando la pantalla. Si se fue, este alcance ya se canceló y nada de esto corre.
            withFrameNanos { }
            withFrameNanos { }
            arrastre = 0f
            cerrando = false
            presencia.animateTo(1f, Movimiento.alEntrar(reducido))
        }
    }

    /** El dedo la mueve [delta] píxeles (positivo = hacia abajo); nunca sube más allá de su lugar. */
    fun arrastrar(delta: Float): Float {
        if (cerrando || !dismissEnabled) return 0f
        val antes = arrastre
        arrastre = (arrastre + delta).coerceAtLeast(0f)
        return arrastre - antes
    }

    /**
     * El dedo la soltó con [velocidad] (px/s, positivo = hacia abajo): pasó el umbral → se cierra;
     * si no, vuelve a su lugar.
     */
    fun soltar(velocidad: Float, umbralDeVelocidad: Float) {
        if (cerrando || arrastre <= 0f) return
        val pasoElUmbral = arrastre > altoDelPanel * FRACCION_PARA_CERRAR_ARRASTRANDO || velocidad > umbralDeVelocidad
        if (pasoElUmbral && dismissEnabled) {
            cerrar()
        } else {
            asentando?.cancel()
            asentando = alcance.launch {
                animate(arrastre, 0f, animationSpec = Movimiento.alVolver(reducido)) { valor, _ -> arrastre = valor }
            }
        }
    }

    /** Lo que ya se ve de la hoja (para el velo): 1 en su lugar, menos mientras entra, sale o se arrastra. */
    fun fraccionVisible(): Float {
        val porElArrastre = if (altoDelPanel > 0) 1f - (arrastre / altoDelPanel).coerceIn(0f, 1f) else 1f
        return presencia.value * porElArrastre
    }
}

/**
 * Qué parte de su alto hay que bajar una hoja para que, al soltarla, se cierre. Un tercio: menos
 * cerraba hojas que solo se querían mirar más abajo; más obligaba a arrastrar casi hasta el borde.
 */
const val FRACCION_PARA_CERRAR_ARRASTRANDO = 1f / 3f

/** Soltarla bajando más rápido que esto la cierra aunque no haya llegado al umbral. */
private val VELOCIDAD_PARA_CERRAR_ARRASTRANDO = 1_200.dp

@Composable
private fun rememberEstadoDeLaHoja(onDismiss: () -> Unit, dismissEnabled: Boolean): EstadoDeLaHoja {
    val reducido = LocalMovimientoReducido.current
    val alcance = rememberCoroutineScope()
    val estado = remember { EstadoDeLaHoja(alcance, reducido) }
    // Siempre el último: quien la abrió puede pasar una lambda nueva en cada recomposición.
    estado.onDismiss = onDismiss
    estado.dismissEnabled = dismissEnabled
    estado.reducido = reducido
    LaunchedEffect(estado) { estado.entrar() }
    return estado
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
 * siendo parte del árbol (nada de `Dialog`/`Popup`), solo que de un punto más arriba.
 *
 * Ola 1 · «Que Movi se sienta viva»: **la hoja del teléfono también**. Dibujada en la columna de la
 * pantalla, la barra de pestañas quedaba visible —y tocable— debajo de cada hoja abierta. Ahora el
 * velo y la hoja tapan la barra, y la hoja descuenta sola la barra de estado, la de navegación y el
 * teclado. Sin anfitrión (una prueba que monta una pantalla suelta) la hoja se dibuja en su lugar.
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

/**
 * ¿Esta plataforma tiene (o puede tener) un teclado físico que valga la pena atender en una hoja
 * compacta? En la web y en iOS sí; en el teléfono Android no — ver «Otras teclas» en [MarcoDeHoja].
 * Un `CompositionLocal` y no una constante para que una prueba (que corre en Android) lo pueda
 * encender.
 */
val LocalHayTecladoFisico = staticCompositionLocalOf { !isAndroid }

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
    onEscape: (() -> Unit)?,
    onTecla: ((KeyEvent) -> Boolean)?,
    enLaCascara: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val estado = rememberEstadoDeLaHoja(onDismiss, dismissEnabled)
    // Escape, como en la centrada (vuelta de diseño 17): sin [onEscape] propio es el toque afuera.
    // Hasta acá la hoja desde abajo no lo atendía, y en la web angosta solo la X la cerraba.
    val escapeActual by rememberUpdatedState(onEscape ?: estado::cerrar)
    val densidad = LocalDensity.current
    val umbralDeVelocidad = with(densidad) { VELOCIDAD_PARA_CERRAR_ARRASTRANDO.toPx() }
    // **Arrastrarla hacia abajo la cierra**, desde la manija o desde el cuerpo, sin romper el scroll
    // de adentro: una lista que todavía puede subir se queda con el gesto (y solo lo que le SOBRA
    // —ya arriba de todo, el dedo sigue bajando— mueve la hoja), y con la hoja ya corrida, subir el
    // dedo la devuelve antes de que la lista se mueva.
    val conexion = remember(estado) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (estado.arrastre > 0f && available.y < 0f) Offset(0f, estado.arrastrar(available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (estado.dedoAbajo && available.y > 0f) Offset(0f, estado.arrastrar(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (estado.arrastre <= 0f) return Velocity.Zero
                estado.soltar(available.y, umbralDeVelocidad)
                return available
            }
        }
    }
    val arrastrable = rememberDraggableState { delta -> estado.arrastrar(delta) }
    // El teclado físico en la hoja desde abajo (ver «Otras teclas» en [MarcoDeHoja]). Solo con
    // [onTecla] y teclado físico: si no, el panel no es destino de foco, como siempre.
    val onTecla = onTecla.takeIf { LocalHayTecladoFisico.current }
    // La ÚLTIMA versión del manejador, no la de la primera composición: el `Velo` de abajo no se
    // recompone cuando la hoja cambia, y con la vieja Enter veía el botón deshabilitado de cuando
    // el monto estaba vacío (medido: los dígitos escribían y Enter no guardaba).
    val teclaActual by rememberUpdatedState(onTecla)
    val foco = remember { FocusRequester() }
    val alcance = rememberCoroutineScope()
    fun reclamarFoco() {
        alcance.launch { runCatching { foco.requestFocus() } }
    }
    val pila = LocalPilaDeFocoDeHojas.current ?: remember { PilaDeFocoDeHojas() }
    // **El panel con teclas también entra a la pila del foco** (vuelta de diseño 23). Antes recuperaba
    // el foco *siempre* que lo perdía; con otra hoja abierta encima —«Repartir este gasto» sobre
    // Agregar— la de arriba lo pedía, este lo recuperaba, la de arriba lo volvía a pedir… sin fin: en
    // la web la página dejaba de responder. Ahora solo lo recupera si es la hoja de arriba, y cuando
    // la de arriba se cierra la pila se lo devuelve ([PilaDeFocoDeHojas.cerrar]).
    val panelConTeclas = remember { PilaDeFocoDeHojas.Hoja(reclamar = { reclamarFoco() }) }
    val conTeclado = if (onTecla == null) {
        Modifier
    } else {
        Modifier
            .onPreviewKeyEvent { teclaActual?.invoke(it) ?: false }
            // Si el foco se va del panel —se cerró el sub-editor cuyo campo lo tenía—, vuelve:
            // si no, la tecla siguiente no le llega a nadie. Solo si nadie se abrió encima.
            .onFocusChanged { if (!it.hasFocus && pila.esLaDeArriba(panelConTeclas)) reclamarFoco() }
            .focusRequester(foco)
            .focusable()
    }
    if (onTecla != null) {
        DisposableEffect(pila) {
            pila.abrir(panelConTeclas)
            onDispose { pila.cerrar(panelConTeclas) }
        }
        LaunchedEffect(Unit) { reclamarFoco() }
    }
    // Con teclado físico y sin [onTecla] (que ya le da el foco al panel), el velo es un destino de
    // foco para que Escape le llegue a alguien aunque nadie haya tocado un campo. Lo pide al abrir
    // **solo si nadie de adentro lo tomó** (un buscador que se enfoca solo se lo queda), y lo
    // recupera como la centrada: al cerrarse el sub-editor que lo tenía o la hoja de arriba.
    val veloConFoco = onTecla == null && LocalHayTecladoFisico.current
    val focoDelVelo = remember { FocusRequester() }
    var adentroHayFoco by remember { mutableStateOf(false) }
    val estaHoja = remember {
        PilaDeFocoDeHojas.Hoja(reclamar = { alcance.launch { runCatching { focoDelVelo.requestFocus() } } })
    }
    if (veloConFoco) {
        DisposableEffect(pila) {
            pila.abrir(estaHoja)
            onDispose { pila.cerrar(estaHoja) }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!adentroHayFoco) estaHoja.reclamar()
        }
    }
    val focoDelVeloModifier = if (veloConFoco) {
        Modifier
            .onFocusChanged { foco ->
                adentroHayFoco = foco.hasFocus
                if (!foco.hasFocus && pila.esLaDeArriba(estaHoja)) estaHoja.reclamar()
            }
            .focusRequester(focoDelVelo)
            .focusable()
    } else {
        Modifier
    }

    @Composable
    fun Columna(altoMaximo: Dp?) {
        Column(modifier = Modifier.fillMaxSize()) {
            // El hueco de arriba empuja la hoja contra el borde de abajo. Un `Box(weight(1f))` y no
            // un `align`: la hoja se mide primero, con todo el alto, y el hueco se lleva lo que sobra.
            Box(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (altoMaximo != null) Modifier.heightIn(max = altoMaximo) else Modifier)
                    .onSizeChanged { estado.altoDelPanel = it.height }
                    // Entra desde abajo y baja con el dedo. En la capa de dibujo: no mueve el layout
                    // (las pruebas de geometría miden la hoja ya en su lugar).
                    .graphicsLayer { translationY = (1f - estado.presencia.value) * size.height + estado.arrastre }
                    .pointerInput(estado) {
                        // Solo para saber si hay un dedo puesto: así lo que le sobra a una lista
                        // de adentro mueve la hoja con el dedo, pero no con la rueda del mouse.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            estado.dedoAbajo = true
                            do {
                                val evento = awaitPointerEvent(PointerEventPass.Initial)
                            } while (evento.changes.any { it.pressed })
                            estado.dedoAbajo = false
                            estado.dedoSoltado(umbralDeVelocidad)
                        }
                    }
                    .nestedScroll(conexion)
                    .draggable(
                        state = arrastrable,
                        orientation = Orientation.Vertical,
                        enabled = dismissEnabled,
                        onDragStopped = { velocidad -> estado.soltar(velocidad, umbralDeVelocidad) },
                    )
                    .clip(RoundedCornerShape(topStart = Radio.xl, topEnd = Radio.xl))
                    .background(Movi.colores.tarjeta)
                    .clickable(enabled = false) {}
                    .then(conTeclado)
                    // En la cáscara la hoja llega hasta el borde de la ventana: su fondo pasa por
                    // detrás de la barra de navegación del sistema y lo de adentro queda arriba de
                    // ella. Con el teclado abierto esto da cero (el velo ya descontó el teclado).
                    .then(if (enLaCascara) Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)) else Modifier)
                    .padding(relleno)
                    .testTag(TAG_PANEL_DE_HOJA),
            ) {
                if (conCierre) SheetHandleWithClose(onClose = estado::cerrar, enabled = dismissEnabled)
                content()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Escape cierra (o lo que decida [onEscape]) desde cualquier lugar de la hoja: en el
            // *preview*, de la raíz hacia adentro, para que llegue aunque un campo tenga el foco. Un
            // menú desplegable abierto es un `Popup` con su propia raíz: su Escape no pasa por acá.
            .onPreviewKeyEvent { evento ->
                if (evento.key == Key.Escape && evento.type == KeyEventType.KeyDown) {
                    escapeActual()
                    true
                } else {
                    false
                }
            }
            .then(focoDelVeloModifier)
            // El velo se oscurece a la par de la hoja: entra, sale y se aclara mientras se arrastra.
            .drawBehind { drawRect(Color.Black.copy(alpha = OPACIDAD_DEL_VELO * estado.fraccionVisible())) }
            .clickable(enabled = dismissEnabled && !estado.cerrando, onClick = estado::cerrar)
            // Con el velo apagado (guardando) los toques tampoco pasan a la barra de abajo.
            .tapaLoDeAbajo()
            // En la cáscara (encima de la barra de pestañas, de borde a borde) la hoja descuenta sola
            // la barra de estado y el teclado; antes se lo daba la columna de la pantalla
            // (`statusBarsPadding` e `imePadding`). Montada suelta, como siempre.
            .then(
                if (enLaCascara) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.statusBars.only(WindowInsetsSides.Top)
                            .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                            .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (fraccionDeAltoMaximo == null) {
            Columna(altoMaximo = null)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Columna(altoMaximo = maxHeight * fraccionDeAltoMaximo)
            }
        }
    }
}

/**
 * El velo es un destino de toques aunque no cierre nada (una hoja guardando): sin esto, con el
 * `clickable` apagado un toque podía caer en la barra de pestañas o el rail de debajo.
 */
private fun Modifier.tapaLoDeAbajo(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** Qué tanto oscurece el velo con la hoja en su lugar. */
private const val OPACIDAD_DEL_VELO = 0.6f

@Composable
private fun HojaCentrada(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    anchoMaximo: Dp,
    fraccionDeAltoMaximo: Float?,
    conCierre: Boolean,
    relleno: PaddingValues,
    onEscape: (() -> Unit)?,
    onTecla: ((KeyEvent) -> Boolean)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hoja = rememberEstadoDeLaHoja(onDismiss, dismissEnabled)
    // Sin [onEscape] propio, Escape es el toque afuera: sale animada (y respeta `dismissEnabled`).
    val alEscape = onEscape ?: hoja::cerrar
    val foco = remember { FocusRequester() }
    val alcance = rememberCoroutineScope()
    // Ver el mismo reflejo en [HojaDesdeAbajo]: siempre el manejador de la última composición.
    val teclaActual by rememberUpdatedState(onTecla)
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
                    // El resto, a la hoja si lo quiere (ver «Otras teclas» en [MarcoDeHoja]).
                    teclaActual?.invoke(evento) ?: false
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
            // El velo se oscurece a la par de la hoja (ver [EstadoDeLaHoja]).
            .drawBehind { drawRect(Color.Black.copy(alpha = OPACIDAD_DEL_VELO * hoja.fraccionVisible())) }
            .clickable(enabled = dismissEnabled && !hoja.cerrando, onClick = hoja::cerrar)
            .tapaLoDeAbajo()
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
                .padding(horizontal = Medida.margenDePantalla)
                .widthIn(max = anchoMaximo)
                .fillMaxWidth()
                .heightIn(max = maxHeight * fraccion)
                // Entra fundiéndose y creciendo apenas (de 96 % a 100 %), sale al revés. Sin
                // deslizar: una ventana en el centro no viene de ningún borde.
                .graphicsLayer {
                    val presencia = hoja.presencia.value
                    alpha = presencia
                    val escala = 0.96f + 0.04f * presencia
                    scaleX = escala
                    scaleY = escala
                }
                .clip(RoundedCornerShape(Radio.xl))
                .background(Movi.colores.tarjeta)
                .clickable(enabled = false) {}
                .padding(relleno)
                .testTag(TAG_PANEL_DE_HOJA),
        ) {
            if (conCierre) SheetHandleWithClose(onClose = hoja::cerrar, enabled = dismissEnabled, conManija = false)
            content()
        }
    }
}
