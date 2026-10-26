package com.jvillada.movi.ui.destinos

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton


/**
 * **Los botones de las tarjetas de «Personas y comercios»** («Guardar», «Es mía», «Ignorar», «Unir»).
 * Ola R4: son el [Boton] chico del sistema —van dentro de una tarjeta—, [principal] lleno de marca y
 * los demás en la capa 2. Antes eran pastillas con un lavado escrito a mano.
 */
@Composable
internal fun Pastilla(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    enabled: Boolean = true,
) {
    Boton(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        variante = if (principal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
        tamano = TamanoDeBoton.S,
        habilitado = enabled,
    )
}

/** Un enlace de texto, como «Ver 3 más» u «Ocultar»: el [Boton] fantasma chico del sistema. */
@Composable
internal fun Enlace(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Boton(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        variante = VarianteDeBoton.Fantasma,
        tamano = TamanoDeBoton.S,
        habilitado = enabled,
    )
}
