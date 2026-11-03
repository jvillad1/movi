package com.jvillada.movi.ui.auth

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi

const val TAG_COLUMNA_DE_ENTRADA: String = "entrada-columna"
const val TAG_ERROR_DE_ENTRADA: String = "entrada-error"

/**
 * # La columna de la entrada y del registro (vuelta 115)
 *
 * Un usuario con un Samsung S25 Ultra (Android 17, letra grande) no pudo crear su cuenta, y el
 * server nunca recibió su registro. La pantalla era una `Column(fillMaxSize, Center)` sin
 * desplazamiento: con el teclado abierto (la raíz de `App.kt` le descuenta su alto con
 * `imePadding`) y la letra grande, lo de abajo —el error y «Crear cuenta»— quedaba recortado. Con
 * una contraseña de menos de 12 caracteres `submit()` cortaba antes de mandar y el aviso no se
 * veía: «no pasa nada».
 *
 * Ahora: centrada cuando sobra alto (`heightIn(min = el alto visible)` detrás del `verticalScroll`)
 * y desplazable cuando no. El `imePadding` propio no descuenta dos veces —la raíz ya consumió el
 * inset— y cubre la pantalla montada sola.
 */
@Composable
internal fun ColumnaDeEntrada(
    scroll: ScrollState = rememberScrollState(),
    contenido: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Movi.colores.fondo).imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_COLUMNA_DE_ENTRADA)
                .verticalScroll(scroll)
                .heightIn(min = maxHeight)
                .padding(Espacio.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = contenido,
        )
    }
}

/**
 * **El error, pegado al botón y a la vista.** Va justo arriba del botón principal; en cada intento
 * que deja un error desplaza la columna hasta que se ven el error y el botón, y se anuncia a quien
 * usa un lector de pantalla (`liveRegion`). Sin error no ocupa lugar.
 *
 * [intento] lo sube la pantalla cada vez que se toca el botón: el MISMO error dos veces seguidas
 * (otra vez la contraseña corta) tiene que volver a desplazar y a anunciarse. Por eso la clave del
 * efecto lleva el intento, y el texto se vuelve a crear con él (`key`): un nodo nuevo con
 * `liveRegion`, no el mismo texto que el lector ya leyó.
 */
@Composable
internal fun ErrorYBotonDeEntrada(
    error: String?,
    intento: Int,
    boton: @Composable () -> Unit,
) {
    val traer = remember { BringIntoViewRequester() }
    LaunchedEffect(error, intento) { if (error != null) traer.bringIntoView() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(traer)) {
        if (error != null) {
            key(intento) {
                Text(
                    error,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.sale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_ERROR_DE_ENTRADA)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(Espacio.s))
        }
        boton()
    }
}
