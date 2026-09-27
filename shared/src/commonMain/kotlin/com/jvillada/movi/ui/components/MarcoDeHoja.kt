package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 *   afuera ([dismissEnabled]).
 *
 * Nada de `Dialog` ni `Popup`: la hoja se sigue dibujando dentro del árbol de la pantalla, así que
 * [com.jvillada.movi.ui.PilaDeHojas], el `imePadding` de la columna raíz y las pruebas de geometría
 * la siguen viendo. El velo cubre la columna de la pantalla, igual que antes.
 *
 * [fraccionDeAltoMaximo] acota el alto también en el teléfono (la hoja de recurrentes necesita un
 * alto acotado para su propio `BoxWithConstraints`); sin él, en el teléfono la hoja mide lo que su
 * contenido. [conCierre] en `false` quita el renglón de la manija y la X: las confirmaciones cortas
 * se cierran con su «Cancelar». [relleno] es el del panel.
 */
@Composable
fun MarcoDeHoja(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    anchoMaximo: Dp = ANCHO_DE_HOJA,
    fraccionDeAltoMaximo: Float? = null,
    conCierre: Boolean = true,
    relleno: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        HojaDesdeAbajo(onDismiss, dismissEnabled, fraccionDeAltoMaximo, conCierre, relleno, content)
    } else {
        HojaCentrada(onDismiss, dismissEnabled, anchoMaximo, fraccionDeAltoMaximo, conCierre, relleno, content)
    }
}

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
    content: @Composable ColumnScope.() -> Unit,
) {
    val foco = remember { FocusRequester() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Escape cierra, como cualquier ventana modal de escritorio. En el «preview» (de la raíz
            // hacia adentro) para que llegue aunque el foco esté en un campo de la hoja.
            .onPreviewKeyEvent { evento ->
                if (evento.key == Key.Escape && evento.type == KeyEventType.KeyDown && dismissEnabled) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            // Una tecla solo le llega a quien tiene el foco o a sus ancestros: sin un destino de
            // foco adentro, Escape no le llegaría a nadie mientras no se toque un campo.
            .focusRequester(foco)
            .focusable()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = dismissEnabled, onClick = onDismiss),
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
