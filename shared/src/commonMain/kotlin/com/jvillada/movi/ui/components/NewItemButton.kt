package com.jvillada.movi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton

/**
 * F18: el "+" chiquito arriba a la derecha era la única forma de crear en Presupuestos,
 * Recurrentes y Créditos, y no se leía como botón. Un mismo componente con dos formas:
 * [full] cuando la lista está vacía (es cuando más falta la acción, así que va abajo del
 * encabezado, a todo el ancho) y compacto (ícono + texto) arriba a la derecha cuando ya hay
 * elementos. (Metas lo recibió en F26; Inversiones dejó de existir como pantalla en la Ola 7.)
 *
 * [enabled] apaga el botón cuando la acción no tiene sobre qué obrar (Presupuestos vacío: «Crear
 * estos 0» con todas las propuestas desmarcadas).
 */
@Composable
fun NewItemButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    full: Boolean = false,
    enabled: Boolean = true,
) {
    // Vuelta de diseño 20: el `Boton` del sistema. A todo el ancho es el principal (era una fila
    // armada a mano de 14 dp de relleno, con su propio apagado en `hilo` y borde); compacto, el de
    // texto (`Fantasma` `S`), con el «+» como ícono. Apagado, el `Boton` ya se sigue leyendo como
    // botón sin parecer tocable.
    Boton(
        texto = label,
        onClick = onClick,
        modifier = modifier,
        variante = if (full) VarianteDeBoton.Principal else VarianteDeBoton.Fantasma,
        tamano = if (full) TamanoDeBoton.M else TamanoDeBoton.S,
        icono = Icons.Rounded.Add,
        habilitado = enabled,
        anchoCompleto = full,
    )
}
