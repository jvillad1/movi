package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ANCHO_DE_LECTURA
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass

/**
 * # Movimientos en lista + detalle (Ola W4; ancho de la lista y el detalle ajustados en la Ola X)
 *
 * En pantalla ancha la lista de movimientos va a la izquierda —con su encabezado, sus filtros, su
 * scroll y sus días tal cual, «Flujo del día» y «Día a día» incluidos— y a la derecha el movimiento
 * elegido, con **el mismo contenido** que la hoja que se abre en el teléfono
 * ([ContenidoDelMovimiento], vía [PanelDelMovimiento]). Tocar una fila lo elige; no se abre ninguna
 * hoja. Anular y «¿se repite?» siguen abriéndose como diálogo encima de todo.
 *
 * ### La cuenta, desde la ventana (con el rail, SIEMPRE)
 *
 * Movimientos es una `Disposicion.ListaYDetalle`: 840 dp de tope en una ventana mediana, 1.440 en
 * escritorio. Lo que le queda es la ventana menos el rail (80 en mediano, 216 en escritorio),
 * topado ahí (`anchoDelPanelEnLaCascara`); **y se mide adentro con `BoxWithConstraints`**
 * ([MedirPanelDeMovimientos]), nunca se calcula a mano. De eso salen la lista
 * ([ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS], 420 desde la Ola X — el dueño la vio apretada), el divisor
 * (1) y lo que sobra para el detalle, que tiene que medir al menos
 * [ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO] (380, bajado un poco para compensar: ver ahí):
 *
 * | Ventana | Clase     | Rail | Ancho         | Detalle | Resultado          |
 * |---------|-----------|------|---------------|---------|--------------------|
 * | 768     | mediana   | 80   | 688           | —       | hoja, como hoy     |
 * | 860     | mediana   | 80   | 780           | —       | hoja (más ancho: el umbral subió con la lista)|
 * | 881     | mediana   | 80   | 801           | 380     | **lista + detalle** (el nuevo borde) |
 * | 999     | mediana   | 80   | 840 (tope)    | 419     | **lista + detalle**|
 * | 1000    | escritorio| 216  | 784           | —       | hoja (el salto del rail; ver [UMBRAL_DE_MOVIMIENTOS_CON_PANEL]) |
 * | 1017    | escritorio| 216  | 801           | 380     | **lista + detalle** (vuelve a aparecer) |
 * | 1024    | escritorio| 216  | 808           | 387     | **lista + detalle**|
 * | 1280    | escritorio| 216  | 1064          | 643     | **lista + detalle**|
 * | 1440    | escritorio| 216  | 1224          | 803     | **lista + detalle**|
 * | 1920    | escritorio| 216  | 1440 (tope)   | 1019    | **lista + detalle**|
 *
 * Con un detalle de más de [ANCHO_DE_LECTURA] (720), su contenido va centrado en esa columna: es
 * un formulario de una columna, y estirarlo a 1.019 no se lee mejor.
 *
 * Sin lugar para los dos (el teléfono, o una ventana mediana angosta) Movimientos se ve exactamente
 * como antes: la lista sola —en su columna de lectura fuera del teléfono— y tocar una fila abre la
 * hoja modal ([HojaDelMovimiento]).
 */

/**
 * El ancho de la lista al lado del detalle: 420 dp desde la Ola X (antes 360, el de un teléfono).
 * El dueño la vio apretada en la web ancha, en ancho y en alto (ver `paddingVerticalDeLaFila` en
 * `TransactionsScreen.kt` para el alto). Subir esto sube el umbral de aparición del panel —ver
 * [ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO], que se bajó un poco para compensar.
 */
val ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS: Dp = 420.dp

/** El hilo entre la lista y el detalle. */
val ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS: Dp = 1.dp

/**
 * Lo mínimo que mide el detalle al lado de la lista: 380 dp (antes 420). Ola X: al subir
 * [ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS] a 420, dejarlo en 420 habría subido el umbral de aparición del
 * panel por encima de lo que deja una ventana de escritorio angosta (1024 dp: con el rail ancho de
 * 216 dp, a esa ventana le quedan apenas 808 dp - ver la tabla de arriba) — el panel habría dejado
 * de aparecer justo en un ancho real que W4 ya probaba. Se bajó lo justo para que 1024 (y todo lo
 * que sigue) siga entrando: 420 + 1 + 380 = 801 ≤ 808. El umbral de ventana sube de ~861 a ~881 dp,
 * un corrimiento chico. Con eso el detalle nunca baja de 380 (más angosto que el de W4, pero sigue
 * siendo un formulario de una columna, no una hoja recortada) y a 1280/1440 —donde de verdad importa
 * verse cómodo— sigue midiendo 643/803, muy por encima del mínimo.
 */
val ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO: Dp = 380.dp

/** El ancho mínimo de Movimientos (ya sin el rail) para ir en lista + detalle: 420 + 1 + 380 = 801. */
val UMBRAL_DE_MOVIMIENTOS_CON_PANEL: Dp =
    ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS + ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS + ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO

/** Si Movimientos, con [anchoDeLaPantalla] (ya sin el rail), va en lista + detalle. */
fun movimientosConPanel(anchoDeLaPantalla: Dp): Boolean = anchoDeLaPantalla >= UMBRAL_DE_MOVIMIENTOS_CON_PANEL

const val TAG_LISTA_DE_MOVIMIENTOS_AL_LADO: String = "movimientos-panel-de-la-lista"
const val TAG_PANEL_DEL_MOVIMIENTO: String = "movimientos-panel-del-detalle"
const val TAG_INVITACION_A_ELEGIR_UN_MOVIMIENTO: String = "movimientos-invitacion-a-elegir"

/** Lo que dice el panel sin nada elegido. */
const val TITULO_DE_LA_INVITACION_A_ELEGIR: String = "Elige un movimiento"
const val DETALLE_DE_LA_INVITACION_A_ELEGIR: String =
    "Tócalo en la lista para ver su detalle, corregirlo o anularlo."

/**
 * El movimiento que muestra el panel: el [elegido], **tal como está ahora** en [dias]. Tras un
 * cambio (categoría, fecha, monto) la lista se relee y el panel sigue a la versión nueva por id; si
 * ya no está (se anuló), `null` y el panel vuelve a la invitación. Sin lista leída todavía, el
 * elegido tal cual.
 */
internal fun movimientoDelPanel(elegido: FinancialEvent?, dias: List<EventDay>?): FinancialEvent? {
    if (elegido == null) return null
    if (dias == null) return elegido
    return dias.firstNotNullOfOrNull { dia -> dia.items.firstOrNull { it.id == elegido.id } }
}

/**
 * Mide lo que le quedó a Movimientos y le dice a [contenido] si va con el panel del detalle al lado.
 * En el teléfono no mide nada (no agrega un `SubcomposeLayout` a la pantalla que más se usa) y dice
 * que no.
 */
@Composable
internal fun MedirPanelDeMovimientos(contenido: @Composable (conPanel: Boolean) -> Unit) {
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        contenido(false)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        contenido(movimientosConPanel(maxWidth))
    }
}

/**
 * La lista y, con [conPanel], el detalle a su derecha. En el teléfono, [lista] a secas: el árbol de
 * siempre. Fuera del teléfono la lista ocupa el mismo lugar de la composición con panel o sin él
 * —su estado sobrevive a agrandar la ventana— y sin panel va en la columna de lectura, como iba.
 */
@Composable
internal fun ListaYPanelDeMovimientos(
    conPanel: Boolean,
    /**
     * El panel entero es un destino de foco: al elegir otro movimiento el foco viene acá, fuera de
     * todo campo, en vez de quedar suelto (ver `elegir` en [TransactionsScreen]).
     */
    focoDelPanel: FocusRequester,
    lista: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        lista()
        return
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = if (conPanel) {
                Modifier.width(ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS).fillMaxHeight().testTag(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO)
            } else {
                Modifier.weight(1f).fillMaxHeight()
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = if (conPanel) Modifier.fillMaxSize() else Modifier.widthIn(max = ANCHO_DE_LECTURA).fillMaxSize()) {
                lista()
            }
        }
        if (conPanel) {
            Box(modifier = Modifier.width(ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS).fillMaxHeight().background(Movi.colores.hilo))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(TAG_PANEL_DEL_MOVIMIENTO)
                    // El tope de la rueda: lo que el contenido del panel no usa —o toda la rueda,
                    // con la invitación, que no se desplaza— se queda acá. Sin esto seguía hasta
                    // el relevo de los márgenes de la cáscara y movía la LISTA de la izquierda
                    // (visto en la web). El relevo es para los márgenes, no para el panel.
                    .scrollable(rememberScrollableState { it }, Orientation.Vertical)
                    .focusRequester(focoDelPanel)
                    .focusable(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(modifier = Modifier.widthIn(max = ANCHO_DE_LECTURA).fillMaxSize()) { panel() }
            }
        }
    }
}

/**
 * El panel sin nada elegido. Movimientos arranca así a propósito: elegir solo el más reciente
 * dispararía una lectura de más (sus parecidos, su marca de recurrente) y daría la impresión de que
 * «ya se abrió algo» que nadie tocó.
 */
@Composable
internal fun InvitacionAElegirUnMovimiento() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 120.dp)
            .testTag(TAG_INVITACION_A_ELEGIR_UN_MOVIMIENTO),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = TITULO_DE_LA_INVITACION_A_ELEGIR,
            style = Movi.textos.titulo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.texto,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = DETALLE_DE_LA_INVITACION_A_ELEGIR,
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            textAlign = TextAlign.Center,
        )
    }
}
