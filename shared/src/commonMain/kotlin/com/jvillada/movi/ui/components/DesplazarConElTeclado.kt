package com.jvillada.movi.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movimiento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * # Bajar con el teclado, como en cualquier página (vuelta 118)
 *
 * Claudia, probadora, usó Movi en la web del computador: quiso bajar con las flechas, Av Pág y la
 * barra espaciadora, y **no pasó nada**. La web de Movi es un lienzo de Compose: una tecla solo le
 * llega a lo que tiene el foco (o a sus ancestros), y un `verticalScroll` o una `LazyColumn` no se
 * mueven con el teclado si nadie los enfocó. Sin foco en ningún lado, la tecla no le llegaba a nadie.
 *
 * ### Las teclas ([queHaceLaTecla])
 *
 * | Tecla | Efecto |
 * |---|---|
 * | ↑ / ↓ | un paso ([Medida.pasoDelTeclado], 48 dp) |
 * | Re Pág / Av Pág, Shift+Espacio / Espacio | casi una pantalla: el alto visible menos un paso |
 * | Inicio / Fin | al principio / al final |
 *
 * Con Ctrl, ⌘ o Alt no se toca nada (son atajos del navegador o de la app), y Shift solo cuenta con
 * el Espacio. Solo se atiende el `KeyDown` (mantener la tecla apretada repite el `KeyDown`).
 *
 * ### Qué se desplaza: capas
 *
 * Cada pantalla de la pila y cada hoja ([MarcoDeHoja]) es una **capa** ([CapaDelTeclado]), y cada
 * una anota su desplazamiento principal con [DesplazarConElTeclado] (o con los atajos
 * [desplazableConElTeclado] y [rememberListaConElTeclado], o con [ScrollDesdeLosMargenes], que ya lo
 * anotaba para la rueda en los márgenes). [TecladoQueDesplaza] —uno por app, lo da la cáscara— mueve
 * el de la capa de arriba: **la hoja abierta más reciente** si hay alguna (aunque no tenga nada que
 * desplazar: la pantalla de atrás no se mueve con una hoja encima) y si no **la pantalla de arriba de
 * la pila** (`App.kt` le dice cuál con [CapaDeLaPantalla]). Dentro de una capa gana lo último que se
 * anotó y sigue en composición: una pantalla que cambia de lista (cargando → lista, una columna → dos)
 * mueve la que está a la vista.
 *
 * - **En dos columnas se anota la principal**: la lista larga (los gastos de En qué se va, los de un
 *   grupo), no la columna del resumen.
 * - **Con un detalle abierto al lado de su lista, manda el detalle** (decisión de la vuelta 118): la
 *   ficha de un movimiento en el panel de Actividad, el detalle de un período junto a «Tus períodos».
 *   Se compone después de la lista, así que queda arriba de lo anotado; es lo que se está leyendo.
 *   Al cerrarlo, la lista vuelve a mandar. Lo fija `DesplazarConElTecladoTest`.
 *
 * ### Nunca le roba las teclas a quien escribe
 *
 * El manejador es un `onKeyEvent` de la raíz de la cáscara: corre **último**, cuando la tecla ya pasó
 * por lo que tiene el foco y nadie la quiso. Un campo de texto con el foco se queda con las flechas, el
 * espacio, Inicio, Fin y las de página (las usa para el cursor, el texto o el renglón) y la tecla nunca
 * sube. Escape, Enter, ⌘A y los dígitos de «Agregar» se atienden en el *preview* de sus dueños, antes,
 * y además aquí no se miran. Lo vigila `DesplazarConElTecladoTest`.
 *
 * ### El foco de la raíz, y por qué no cambia el Tab
 *
 * Para que la tecla le llegue a alguien sin nada enfocado, la raíz de la cáscara es un destino de foco
 * ([elTecladoDesplaza]) y lo pide al abrir la app y cada vez que el foco se va de todo (se cerró la
 * hoja que lo tenía, un campo lo soltó). **Nunca se lo quita a nadie**: solo lo pide sin foco adentro.
 * Es un `focusTarget()` desnudo —sin semántica, sin anillo, sin interacción— y **solo se puede enfocar
 * cuando ella misma lo pide** (o mientras ya lo tiene): `focusProperties { canFocus = … }`. Tab y
 * Shift+Tab recorren lo mismo que antes y la raíz nunca es una parada, tampoco al dar la vuelta desde el
 * último elemento (ahí Compose suelta el foco un instante y busca hacia adelante desde la raíz).
 *
 * Pedir el foco de Compose no hace nada en la plataforma: en la web y en iOS el `PlatformContext` de
 * Compose 1.8 no mueve el foco del navegador ni del sistema, y el teclado en pantalla solo lo abre un
 * campo de texto (que pide su sesión de texto). Por eso tampoco abre el teclado ni mueve nada en un
 * iPhone ni en la web del teléfono. El foco del **navegador** (sin el cual la tecla ni le llega a
 * Compose) lo pone `enfocarMovi` en `index.html`, al cargar con sesión y al esconder el ingreso.
 *
 * Solo donde **puede** haber teclado físico ([LocalHayTecladoFisico] = todo lo que no es Android: la
 * web —también la del teléfono— y iOS —iPhone y iPad—). En el teléfono Android la raíz ni siquiera es
 * destino de foco.
 *
 * ### Con poco movimiento
 *
 * Con el sistema pidiendo menos movimiento ([LocalMovimientoReducido]) salta en el acto; si no, con
 * [Movimiento.MEDIO_MS] y la curva [Movimiento.estandar]. Una tecla que llega con el desplazamiento
 * anterior en curso suma lo que faltaba (mantener ↓ apretado baja parejo, no a tirones).
 */
class TecladoQueDesplaza {
    /** La capa de la pantalla de arriba de la pila; la pone [CapaDeLaPantalla]. */
    internal var capaDeLaPantalla: CapaDelTeclado? = null

    /** Las hojas abiertas, de abajo hacia arriba (en el orden en que se abrieron). */
    private val capasDeLasHojas = mutableListOf<CapaDelTeclado>()

    internal fun abrirHoja(capa: CapaDelTeclado) {
        capasDeLasHojas.add(capa)
    }

    internal fun cerrarHoja(capa: CapaDelTeclado) {
        capasDeLasHojas.remove(capa)
    }

    /** Lo que mueve el teclado ahora: lo de la hoja de arriba, o lo de la pantalla. */
    fun objetivo(): ScrollableState? {
        val hojaDeArriba = capasDeLasHojas.lastOrNull()
        return if (hojaDeArriba != null) hojaDeArriba.objetivo else capaDeLaPantalla?.objetivo
    }

    private var trabajo: Job? = null
    private var falta = 0f
    private var ultimo: ScrollableState? = null

    /**
     * Atiende [evento] si es una tecla de desplazamiento y hay qué mover. `true` = consumida. Un
     * desplazamiento que no tiene a dónde ir (ya arriba con ↑) también la consume: la tecla era de
     * desplazar y no debe caer en otro lado.
     */
    internal fun atender(evento: KeyEvent, alcance: CoroutineScope, paso: Float, reducido: Boolean): Boolean {
        val accion = queHaceLaTecla(
            evento.key,
            evento.type,
            conModificador = evento.isCtrlPressed || evento.isMetaPressed || evento.isAltPressed,
            conShift = evento.isShiftPressed,
        ) ?: return false
        val estado = objetivo() ?: return false
        hacer(accion, estado, alcance, paso, reducido)
        return true
    }

    private fun hacer(accion: AccionDelTeclado, estado: ScrollableState, alcance: CoroutineScope, paso: Float, reducido: Boolean) {
        val siguia = trabajo?.isActive == true && ultimo === estado
        trabajo?.cancel()
        ultimo = estado
        val delta = when (accion) {
            AccionDelTeclado.BAJAR -> paso
            AccionDelTeclado.SUBIR -> -paso
            AccionDelTeclado.PAGINA_ABAJO -> max(paso, altoVisible(estado, paso) - paso)
            AccionDelTeclado.PAGINA_ARRIBA -> -max(paso, altoVisible(estado, paso) - paso)
            AccionDelTeclado.AL_PRINCIPIO, AccionDelTeclado.AL_FINAL -> {
                falta = 0f
                trabajo = alcance.launch { alExtremo(estado, alFinal = accion == AccionDelTeclado.AL_FINAL, reducido) }
                return
            }
        }
        // Lo que faltaba de la tecla anterior se suma, siempre que vaya en el mismo sentido.
        val total = delta + if (siguia && falta * delta > 0f) falta else 0f
        falta = 0f
        trabajo = alcance.launch {
            if (reducido) {
                estado.scrollBy(total)
                return@launch
            }
            var hecho = 0f
            falta = total
            estado.scroll {
                animate(0f, total, animationSpec = tween(Movimiento.MEDIO_MS, easing = Movimiento.estandar)) { valor, _ ->
                    val pedido = valor - hecho
                    val movio = scrollBy(pedido)
                    hecho += pedido
                    // Llegó al borde: no queda nada que sumarle a la tecla siguiente.
                    falta = if (abs(movio - pedido) > 0.5f) 0f else total - hecho
                }
            }
            falta = 0f
        }
    }
}

/** Lo que pide una tecla. */
enum class AccionDelTeclado { SUBIR, BAJAR, PAGINA_ARRIBA, PAGINA_ABAJO, AL_PRINCIPIO, AL_FINAL }

/**
 * La tecla, en lo que pide (ver la tabla en [TecladoQueDesplaza]); `null` si no es de desplazar. Pura:
 * la prueba la recorre sin componer nada.
 */
fun queHaceLaTecla(tecla: Key, tipo: KeyEventType, conModificador: Boolean, conShift: Boolean): AccionDelTeclado? {
    if (tipo != KeyEventType.KeyDown || conModificador) return null
    if (tecla == Key.Spacebar) return if (conShift) AccionDelTeclado.PAGINA_ARRIBA else AccionDelTeclado.PAGINA_ABAJO
    if (conShift) return null
    return when (tecla) {
        Key.DirectionDown -> AccionDelTeclado.BAJAR
        Key.DirectionUp -> AccionDelTeclado.SUBIR
        Key.PageDown -> AccionDelTeclado.PAGINA_ABAJO
        Key.PageUp -> AccionDelTeclado.PAGINA_ARRIBA
        Key.MoveHome -> AccionDelTeclado.AL_PRINCIPIO
        Key.MoveEnd -> AccionDelTeclado.AL_FINAL
        else -> null
    }
}

/** El alto que se ve del desplazamiento; sin saberlo (otro tipo de estado), ocho pasos. */
private fun altoVisible(estado: ScrollableState, paso: Float): Float = when (estado) {
    is ScrollState -> estado.viewportSize.toFloat()
    is LazyListState -> estado.layoutInfo.viewportSize.height.toFloat()
    else -> 0f
}.takeIf { it > 0f } ?: (paso * 8)

/**
 * Lo que la raíz sabe de su foco. Variables comunes (no estado de Compose) a propósito: las lee
 * `focusProperties` en el momento de cada búsqueda de foco, y cambiarlas no debe invalidar el foco.
 */
private class FocoDeLaRaiz {
    var conFoco = false
    var enfocada = false
    var pidiendo = false
}

/** Un número de píxeles más grande que cualquier lista de Movi, para «hasta el borde». */
private const val HASTA_EL_BORDE = 1_000_000f

private suspend fun alExtremo(estado: ScrollableState, alFinal: Boolean, reducido: Boolean) {
    when (estado) {
        is ScrollState -> {
            val destino = if (alFinal) estado.maxValue else 0
            if (reducido) estado.scrollTo(destino) else estado.animateScrollTo(destino, tween(Movimiento.MEDIO_MS, easing = Movimiento.estandar))
        }
        is LazyListState -> {
            val ultimo = (estado.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            if (!alFinal) {
                if (reducido) estado.scrollToItem(0) else estado.animateScrollToItem(0)
            } else {
                if (reducido) estado.scrollToItem(ultimo) else estado.animateScrollToItem(ultimo)
                // El último renglón arriba no es el final si mide más que lo que falta: hasta el borde.
                estado.scrollBy(HASTA_EL_BORDE)
            }
        }
        else -> estado.scrollBy(if (alFinal) HASTA_EL_BORDE else -HASTA_EL_BORDE)
    }
}

/**
 * Una capa del teclado: una pantalla de la pila o una hoja. Guarda los desplazamientos que se anotaron
 * en ella; mueve el último que sigue en composición.
 */
class CapaDelTeclado {
    private val anotados = mutableListOf<ScrollableState>()

    val objetivo: ScrollableState? get() = anotados.lastOrNull()

    internal fun anotar(estado: ScrollableState) {
        anotados.add(estado)
    }

    internal fun quitar(estado: ScrollableState) {
        anotados.remove(estado)
    }
}

/** El de la app; lo da la cáscara (`EsqueletoDeLaCascara`). `null` fuera de ella. */
val LocalTecladoQueDesplaza = staticCompositionLocalOf<TecladoQueDesplaza?> { null }

/** La capa donde se está componiendo: la pantalla o la hoja. `null` fuera de la cáscara. */
val LocalCapaDelTeclado = staticCompositionLocalOf<CapaDelTeclado?> { null }

/**
 * «Mi desplazamiento principal es este»: lo mueven las flechas, las de página, el espacio, Inicio y
 * Fin mientras esta pantalla (u hoja) esté arriba. Va junto al `verticalScroll` o la `LazyColumn`
 * principal; las listas de adentro (un selector, una columna lateral) no se anotan.
 */
@Composable
fun DesplazarConElTeclado(estado: ScrollableState) {
    val capa = LocalCapaDelTeclado.current ?: return
    DisposableEffect(capa, estado) {
        capa.anotar(estado)
        onDispose { capa.quitar(estado) }
    }
}

/**
 * `verticalScroll(estado)` que además se anota para el teclado ([DesplazarConElTeclado]). Es el
 * reemplazo de `Modifier.verticalScroll(rememberScrollState())` en el cuerpo de una pantalla o de una
 * hoja.
 */
@Composable
fun Modifier.desplazableConElTeclado(estado: ScrollState = rememberScrollState()): Modifier {
    DesplazarConElTeclado(estado)
    return verticalScroll(estado)
}

/** `rememberLazyListState()` que además se anota para el teclado ([DesplazarConElTeclado]). */
@Composable
fun rememberListaConElTeclado(): LazyListState {
    val estado = rememberLazyListState()
    DesplazarConElTeclado(estado)
    return estado
}

/**
 * La capa de una pantalla de la pila (la arma `App.kt` alrededor de cada una). [arriba] dice si es la
 * de arriba: durante la transición entre dos pantallas están las dos en composición, y el teclado
 * mueve la que entra.
 */
@Composable
fun CapaDeLaPantalla(arriba: Boolean, contenido: @Composable () -> Unit) {
    val teclado = LocalTecladoQueDesplaza.current
    val capa = remember { CapaDelTeclado() }
    if (teclado != null && arriba) {
        DisposableEffect(teclado, capa) {
            teclado.capaDeLaPantalla = capa
            onDispose { if (teclado.capaDeLaPantalla === capa) teclado.capaDeLaPantalla = null }
        }
    }
    CompositionLocalProvider(LocalCapaDelTeclado provides capa, content = contenido)
}

/**
 * La capa de una hoja: se anota en el teclado mientras la hoja esté abierta. La usa [MarcoDeHoja]; la
 * capa se le da al contenido de la hoja con [LocalCapaDelTeclado].
 */
@Composable
internal fun rememberCapaDeLaHoja(): CapaDelTeclado {
    val teclado = LocalTecladoQueDesplaza.current
    val capa = remember { CapaDelTeclado() }
    if (teclado != null) {
        DisposableEffect(teclado, capa) {
            teclado.abrirHoja(capa)
            onDispose { teclado.cerrarHoja(capa) }
        }
    }
    return capa
}

/**
 * Para la raíz de la cáscara: atiende las teclas de desplazar que nadie quiso y, sin nada enfocado,
 * se queda con el foco para que le lleguen (ver «El foco de la raíz» en [TecladoQueDesplaza]). Sin
 * teclado físico ([LocalHayTecladoFisico]) no hace nada.
 */
@Composable
fun Modifier.elTecladoDesplaza(teclado: TecladoQueDesplaza): Modifier {
    if (!LocalHayTecladoFisico.current) return this
    val alcance = rememberCoroutineScope()
    val paso = with(LocalDensity.current) { Medida.pasoDelTeclado.toPx() }
    val reducido by rememberUpdatedState(LocalMovimientoReducido.current)
    val foco = remember { FocusRequester() }
    val raiz = remember { FocoDeLaRaiz() }
    fun reclamar() {
        alcance.launch {
            // Un cuadro después: si en el medio alguien de adentro pidió el foco, se lo queda.
            withFrameNanos { }
            if (raiz.conFoco) return@launch
            raiz.pidiendo = true
            try {
                runCatching { foco.requestFocus() }
            } finally {
                raiz.pidiendo = false
            }
        }
    }
    LaunchedEffect(Unit) { reclamar() }
    return this
        .onKeyEvent { evento -> teclado.atender(evento, alcance, paso, reducido) }
        .onFocusChanged { estado ->
            raiz.conFoco = estado.hasFocus
            raiz.enfocada = estado.isFocused
            if (!estado.hasFocus) reclamar()
        }
        // La raíz solo se puede enfocar cuando ella misma lo pide (o mientras ya lo tiene, para soltarlo
        // bien): así el Tab nunca se para en ella, tampoco al dar la vuelta desde el último elemento,
        // cuando Compose suelta el foco un instante y busca hacia adelante desde la raíz.
        .focusProperties { canFocus = raiz.pidiendo || raiz.enfocada }
        .focusRequester(foco)
        .focusTarget()
}
