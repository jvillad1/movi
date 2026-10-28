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
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton

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
 * ícono y «Agregar» en el rail ancho). Lo usan los dos rails (vuelta 38: la barra del teléfono ya no
 * tiene «+»; ahí el mismo menú lo abre «Más formas de anotar» de Inicio, [MasFormasDeAnotar]).
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
            MenuDeAtajosDeAgregar(abierto = abierto, alCerrar = { abierto = false }, alElegir = alElegirAtajo)
        }
    }
}

/**
 * **El menú de los atajos de «Agregar»**: una fila por [atajos], y elegir una cierra el menú y hace
 * [alElegir]. Es el mismo en el «+» del rail ([BotonDeAgregarConAtajos]) y en «Más formas de anotar»
 * de Inicio ([MasFormasDeAnotar]).
 */
@Composable
fun MenuDeAtajosDeAgregar(
    abierto: Boolean,
    alCerrar: () -> Unit,
    alElegir: (AtajoDeAgregar) -> Unit,
    atajos: List<AtajoDeAgregar> = AtajoDeAgregar.entries,
) {
    DropdownMenu(
        expanded = abierto,
        onDismissRequest = alCerrar,
        modifier = Modifier.testTag(TAG_MENU_DE_AGREGAR),
        containerColor = Movi.colores.tarjeta,
    ) {
        atajos.forEach { atajo ->
            DropdownMenuItem(
                text = { Text(atajo.rotulo, style = Movi.textos.cuerpo, color = Movi.colores.texto) },
                onClick = {
                    alCerrar()
                    alElegir(atajo)
                },
            )
        }
    }
}

/**
 * Lo que hace elegir un atajo de «Agregar», puesto por la cáscara (App.kt es dueña de las hojas de
 * «Repartir» y del selector del comprobante, ver `hacerElAtajo`). `null` fuera de la app (una prueba
 * que monta una pantalla sola): ahí «Más formas de anotar» no se pinta.
 */
val LocalAtajosDeAgregar = staticCompositionLocalOf<((AtajoDeAgregar) -> Unit)?> { null }

/** Lo que dice el enlace de Inicio que abre los atajos de anotar (vuelta 38). */
const val MAS_FORMAS_DE_ANOTAR: String = "Más formas de anotar"

/**
 * Las formas de anotar que **no** están en las acciones de Inicio: Traspaso, Pago de tarjeta, Repartir
 * y Subir comprobante (Gasto e Ingreso ya son acciones).
 */
val ATAJOS_FUERA_DEL_INICIO: List<AtajoDeAgregar> = listOf(
    AtajoDeAgregar.TRASPASO,
    AtajoDeAgregar.PAGO_DE_TARJETA,
    AtajoDeAgregar.REPARTIR,
    AtajoDeAgregar.SUBIR_COMPROBANTE,
)

/**
 * **«Más formas de anotar»** (vuelta 38): sin el «+» de la barra del teléfono, Traspaso, Pago de
 * tarjeta, Repartir y Subir comprobante se alcanzan desde aquí, debajo de las acciones de Inicio. Un
 * botón de texto que abre el mismo menú de atajos del «+». La vuelta 39 lo reemplaza por las 8
 * acciones de la maqueta. Sin [LocalAtajosDeAgregar] (fuera de la app) no se pinta.
 */
@Composable
fun MasFormasDeAnotar(modifier: Modifier = Modifier) {
    val alElegir = LocalAtajosDeAgregar.current ?: return
    var abierto by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Boton(
            texto = MAS_FORMAS_DE_ANOTAR,
            onClick = { abierto = true },
            variante = VarianteDeBoton.Fantasma,
            modifier = Modifier.testTag(TAG_MAS_FORMAS_DE_ANOTAR),
        )
        MenuDeAtajosDeAgregar(
            abierto = abierto,
            alCerrar = { abierto = false },
            alElegir = alElegir,
            atajos = ATAJOS_FUERA_DEL_INICIO,
        )
    }
}

/** El `testTag` de «Más formas de anotar». */
const val TAG_MAS_FORMAS_DE_ANOTAR: String = "mas-formas-de-anotar"

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
