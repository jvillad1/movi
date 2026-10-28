package com.jvillada.movi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar

/** El `testTag` del menú de atajos del «+». */
const val TAG_MENU_DE_AGREGAR: String = "menu-de-agregar"

/** El `testTag` de la flecha de «Agregar» que abre los atajos (rail ancho, vuelta de diseño 26). */
const val TAG_FLECHA_DE_AGREGAR: String = "flecha-de-agregar"

/** Lo que dice un lector de pantalla sobre mantener presionado el «+» (y sobre su flecha). */
const val ROTULO_ATAJOS_DE_AGREGAR: String = "Atajos para agregar"

/**
 * **El «+» con sus atajos** (Ola 5): tocarlo hace [alTocar] —abrir «Agregar», como siempre— y
 * mantenerlo presionado abre un menú corto con [AtajoDeAgregar] (Gasto · Ingreso · Traspaso · Pago de
 * tarjeta · Repartir · Subir comprobante). Sin [alElegirAtajo] el «+» es el de antes: solo se toca.
 *
 * **En la web y el escritorio** (vuelta de diseño 26), donde mantener presionado con el mouse no es
 * natural, el mismo menú abre también con el **clic derecho** sobre el «+», y con [conFlecha] el botón
 * lleva al final una flecha que lo abre de un clic (el rail ancho: «Agregar ▾»). Es el mismo menú, no
 * otro: lo que se elige hace lo mismo desde cualquier gesto.
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
    conFlecha: Boolean = false,
    contenido: @Composable () -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    val conAtajos = alElegirAtajo != null
    val gestos = Modifier
        .let { if (conAtajos) it.abreConClicDerecho { abierto = true } else it }
        .combinedClickable(
            onClickLabel = "Agregar",
            onLongClickLabel = if (conAtajos) ROTULO_ATAJOS_DE_AGREGAR else null,
            onLongClick = if (conAtajos) ({ abierto = true }) else null,
            onClick = alTocar,
        )
    Box {
        if (conFlecha && conAtajos) {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).then(gestos), contentAlignment = Alignment.CenterStart) { contenido() }
                FlechaDeAtajos(onClick = { abierto = true })
            }
        } else {
            Box(modifier = modifier.then(gestos), contentAlignment = Alignment.Center) { contenido() }
        }
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

/** La flecha «▾» al final de «Agregar»: abre los atajos de un clic. */
@Composable
private fun FlechaDeAtajos(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Medida.objetivoTactil)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = ROTULO_ATAJOS_DE_AGREGAR }
            .testTag(TAG_FLECHA_DE_AGREGAR),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = Movi.colores.sobreMarca,
            modifier = Modifier.size(Icono.m),
        )
    }
}

/**
 * El clic derecho (el botón secundario de un mouse) hace [abrir]. Se mira en el pase `Initial`, antes
 * que el `combinedClickable` que va después en la cadena, y se consume: así el clic derecho no cuenta
 * además como un toque que abre «Agregar». Con el dedo no pasa nada (no hay botón secundario).
 */
private fun Modifier.abreConClicDerecho(abrir: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val evento = awaitPointerEvent(PointerEventPass.Initial)
            if (evento.type == PointerEventType.Press && evento.buttons.isSecondaryPressed) {
                evento.changes.forEach { it.consume() }
                abrir()
            }
        }
    }
}
