package com.jvillada.movi.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar

/** El `testTag` del menú de atajos del «+». */
const val TAG_MENU_DE_AGREGAR: String = "menu-de-agregar"

/** Lo que dice un lector de pantalla sobre mantener presionado el «+». */
const val ROTULO_ATAJOS_DE_AGREGAR: String = "Atajos para agregar"

/**
 * **El «+» con sus atajos** (Ola 5): tocarlo hace [alTocar] —abrir «Agregar», como siempre— y
 * mantenerlo presionado abre un menú corto con [AtajoDeAgregar] (Gasto · Ingreso · Traspaso · Pago de
 * tarjeta · Subir comprobante). Sin [alElegirAtajo] el «+» es el de antes: solo se toca.
 *
 * [modifier] es el del botón (tamaño, forma, fondo), y [contenido] lo que va adentro (el ícono, o el
 * ícono y «Agregar» en el rail ancho). Lo usan la barra del teléfono y los dos rails: el mismo menú en
 * los tres lugares donde está el «+».
 */
@Composable
fun BotonDeAgregarConAtajos(
    alTocar: () -> Unit,
    alElegirAtajo: ((AtajoDeAgregar) -> Unit)?,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = modifier.combinedClickable(
                onClickLabel = "Agregar",
                onLongClickLabel = if (alElegirAtajo != null) ROTULO_ATAJOS_DE_AGREGAR else null,
                onLongClick = if (alElegirAtajo != null) ({ abierto = true }) else null,
                onClick = alTocar,
            ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { contenido() }
        if (alElegirAtajo != null) {
            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                modifier = Modifier.testTag(TAG_MENU_DE_AGREGAR),
                containerColor = Movi.colores.tarjeta,
            ) {
                AtajoDeAgregar.entries.forEach { atajo ->
                    DropdownMenuItem(
                        text = { Text(atajo.rotulo, style = Movi.textos.cuerpo, color = Movi.colores.texto) },
                        onClick = {
                            abierto = false
                            alElegirAtajo(atajo)
                        },
                    )
                }
            }
        }
    }
}
