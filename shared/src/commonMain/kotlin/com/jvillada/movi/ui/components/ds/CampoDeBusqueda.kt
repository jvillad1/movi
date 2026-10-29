package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.rememberCampoConSeleccion

/** Tag del texto editable de un [CampoDeBusqueda] (se puede cambiar con `tag`). */
const val TAG_CAMPO_DE_BUSQUEDA: String = "ds-busqueda"

/** La caja de un [CampoDeBusqueda] (la superficie con la lupa): para medirla en una prueba. */
const val TAG_CAJA_DE_BUSQUEDA: String = "ds-busqueda-caja"

/** El texto guía de un [CampoDeBusqueda] vacío (se puede cambiar con `tagDeLaGuia`). */
const val TAG_GUIA_DE_BUSQUEDA: String = "ds-busqueda-guia"

/** La × que borra lo escrito. Solo está cuando hay texto. */
const val TAG_BORRAR_LA_BUSQUEDA: String = "ds-busqueda-borrar"

/** Lo que dice la × a quien no la ve. */
const val DESCRIPCION_DE_BORRAR_LA_BUSQUEDA: String = "Borrar la búsqueda"

/**
 * **La caja de búsqueda de toda la app.** Vuelta 35.
 *
 * Había dos que no se parecían: la de Buscar (blanca, con lupa y una × para borrar) y la de
 * Categorías («Buscar categoría», el [Campo] gris de los formularios, sin lupa ni ×). El selector de
 * categoría de Agregar y la de Personas y comercios armaban la suya. Ahora es una, de izquierda a
 * derecha:
 *
 * 1. **La lupa**, [Icono.m] en `textoMedio`: dice «esto busca» antes de escribir nada.
 * 2. **El texto**, en `cuerpo`; vacío, la [guia] en `textoApagado` (como la de [Campo]), de un
 *    renglón y con «…» si no cabe. La guía también es lo que oye un lector de pantalla.
 * 3. **La ×** ([BotonDeIcono], 48 de toque), solo con texto: borra todo y deja el foco en la caja.
 *
 * Una superficie sin borde, [Radio.m], de [Medida.objetivoTactil] de alto **con la × o sin ella**:
 * la × mide lo mismo que la caja, así que escribir la primera letra no mueve nada.
 *
 * **El color** es el de una superficie ([nivel]): [NivelDeSuperficie.Capa1] sobre el fondo de una
 * pantalla (Buscar, Categorías, Personas y comercios); [NivelDeSuperficie.Capa2] dentro de una hoja,
 * que ya es capa 1 (el selector de categoría, «Unificar en…»).
 *
 * El texto lo guarda la pantalla, como en [Campo]; lleva el ⌘A de la web ([rememberCampoConSeleccion]).
 *
 * ```
 * CampoDeBusqueda(consulta, { consulta = it }, guia = "Gasto, comercio o persona", alBuscar = ::anotar)
 * CampoDeBusqueda(busqueda, { busqueda = it }, guia = "Buscar o crear categoría", nivel = NivelDeSuperficie.Capa2)
 * ```
 *
 * @param alBuscar la tecla «Buscar» del teclado. Sin ella, el teclado muestra la de siempre.
 */
@Composable
fun CampoDeBusqueda(
    valor: String,
    alCambiar: (String) -> Unit,
    guia: String,
    modifier: Modifier = Modifier,
    nivel: NivelDeSuperficie = NivelDeSuperficie.Capa1,
    alBuscar: (() -> Unit)? = null,
    foco: FocusRequester? = null,
    tag: String = TAG_CAMPO_DE_BUSQUEDA,
    tagDeLaGuia: String = TAG_GUIA_DE_BUSQUEDA,
) {
    val focoPropio = remember { FocusRequester() }
    val focoDeLaCaja = foco ?: focoPropio
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Medida.objetivoTactil)
            .anilloDeFoco(RoundedCornerShape(Radio.m), deLoDeAdentro = true)
            .clip(RoundedCornerShape(Radio.m))
            .background(colorDeLaSuperficie(nivel, Movi.colores))
            .testTag(TAG_CAJA_DE_BUSQUEDA)
            .padding(horizontal = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(Icono.m))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (valor.isEmpty()) {
                Text(
                    guia,
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(tagDeLaGuia),
                )
            }
            // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
            val campo = rememberCampoConSeleccion(valor, alCambiar)
            BasicTextField(
                value = campo.valor,
                onValueChange = campo::alCambiar,
                singleLine = true,
                textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                cursorBrush = SolidColor(Movi.colores.enlace),
                keyboardOptions = if (alBuscar != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
                keyboardActions = if (alBuscar != null) KeyboardActions(onSearch = { alBuscar() }) else KeyboardActions.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focoDeLaCaja)
                    .testTag(tag)
                    .semantics { contentDescription = guia }
                    .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
            )
        }
        if (valor.isNotEmpty()) {
            // El tag va en una caja propia: el botón ya trae el suyo (`TAG_BOTON_DE_ICONO`).
            Box(Modifier.testTag(TAG_BORRAR_LA_BUSQUEDA)) {
                BotonDeIcono(
                    Icons.Rounded.Close,
                    DESCRIPCION_DE_BORRAR_LA_BUSQUEDA,
                    onClick = {
                        alCambiar("")
                        runCatching { focoDeLaCaja.requestFocus() }
                    },
                )
            }
        }
    }
}
