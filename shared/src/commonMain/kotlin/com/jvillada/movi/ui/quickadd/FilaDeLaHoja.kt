package com.jvillada.movi.ui.quickadd

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.DensidadDeFila
import com.jvillada.movi.ui.components.ds.Fila

/**
 * Cuánto de una fila de la hoja puede ocupar el valor de la derecha. 55 % deja ~167 dp para el valor
 * y ~136 dp para la etiqueta y su aviso a 375 dp — medido: «Bancolombia Ahorros» entra entero,
 * «Última usada» también, y un nombre más largo se corta con «…» en vez de partir la etiqueta letra
 * por letra (era `rightMaxFraction` de `CardRow`, con el mismo porqué).
 */
internal const val FRACCION_DEL_VALOR_DE_LA_HOJA = 0.55f

/** El valor de una [FilaDeLaHoja] que ocupa lo que deja el rótulo (vuelta 83: la cuenta). */
internal const val TAG_VALOR_HASTA_EL_ROTULO: String = "agregar:valor-hasta-el-rotulo"

/** Desde qué escala de letra el valor de una fila de la hoja vuelve a partirse en dos (vuelta 80). */
internal const val ESCALA_DE_LETRA_QUE_PARTE_LA_CUENTA: Float = 1.2f

/**
 * **Una fila de la hoja de Agregar que abre un sub-editor** —«Cuenta», «Nota», y «Desde» / «Hacia»
 * del traspaso— (vuelta de diseño 20; la de «Categoría» se fue en la vuelta 34: la categoría la dicen
 * los chips, con «Todas ›» al final). Es la [Fila] del sistema, en su densidad
 * [DensidadDeFila.Compacta]: 48 dp —lo que medían los `CardRow` armados a mano que reemplaza—, el
 * margen de la pantalla a los lados y el toque de borde a borde. Con la densidad de siempre (56 / 64)
 * la hoja crecía ~30 dp y a 360 × 740 «Guardar movimiento» quedaba cortado por el borde: esta hoja
 * está anclada abajo y no puede medir más que antes (lo mide `HojaAgregarGeometriaTest`).
 *
 * - [titulo]: qué se elige («Categoría»).
 * - [valor]: lo elegido, a la derecha, con «…» si no entra y nunca más de
 *   [FRACCION_DEL_VALOR_DE_LA_HOJA] del ancho (sin techo, un nombre de cuenta largo se llevaba la
 *   fila entera y partía la etiqueta; [fraccionDelValor] lo cambia donde las etiquetas son más
 *   cortas, como «Desde» y «Hacia»). [valorVacio] lo pinta apagado («Agregar nota…»).
 * - [aviso] / [reservarAviso]: el renglón de «Última usada» debajo del título. **Reserva su alto**
 *   mientras pueda llegar a decir algo (con más de una cuenta): la hoja está anclada abajo y un
 *   cambio de alto mueve el teclado numérico bajo el dedo (ver la Ola 8 en `QuickAddScreen`). Con
 *   el renglón reservado y vacío la fila mide lo mismo que con el aviso escrito.
 */
@Composable
internal fun FilaDeLaHoja(
    titulo: String,
    valor: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valorVacio: Boolean = false,
    aviso: String? = null,
    reservarAviso: Boolean = false,
    conChevron: Boolean = true,
    fraccionDelValor: Float = FRACCION_DEL_VALOR_DE_LA_HOJA,
    /** Vuelta 80: la cuenta va en uno (con «…»); lo demás, hasta dos. */
    renglones: Int = 2,
    /**
     * Vuelta 83 · **el valor ocupa lo que deja el rótulo** ([titulo], medido): en vez de un tope fijo
     * ([fraccionDelValor]), el valor toma el ancho que necesita hasta el borde del rótulo, y el [aviso]
     * de debajo se corta con «…» si no le alcanza. Para la cuenta, cuyo nombre corto entra entero.
     */
    valorHastaElRotulo: Boolean = false,
) {
    val medidor = rememberTextMeasurer()
    val estiloDelRotulo = Movi.textos.cuerpo
    val anchoDelRotulo = remember(titulo, estiloDelRotulo, medidor) {
        medidor.measure(titulo, estiloDelRotulo, maxLines = 1).size.width
    }
    Fila(
        titulo = titulo,
        subtitulo = if (aviso != null || reservarAviso) aviso.orEmpty() else null,
        final = {
            Row(
                modifier = if (valorHastaElRotulo) {
                    Modifier.testTag(TAG_VALOR_HASTA_EL_ROTULO).layout { medible, restricciones ->
                        val tope = if (restricciones.hasBoundedWidth) {
                            (restricciones.maxWidth - anchoDelRotulo - Espacio.xs.roundToPx()).coerceAtLeast(0)
                        } else {
                            restricciones.maxWidth
                        }
                        val colocable = medible.measure(restricciones.copy(minWidth = 0, maxWidth = tope))
                        layout(colocable.width, colocable.height) { colocable.place(0, 0) }
                    }
                } else {
                    Modifier.fillMaxWidth(fraccionDelValor)
                },
                horizontalArrangement = Arrangement.spacedBy(Espacio.xxs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Vuelta 55: hasta dos renglones. Con la letra al 130 % a 360 dp «Bancolombia
                // Ahorros» se cortaba en «Bancolombia Ah…»; lo que entra en uno sigue en uno, así que
                // con la letra de siempre la hoja mide lo mismo.
                Text(
                    text = valor,
                    style = Movi.textos.cuerpo,
                    color = if (valorVacio) Movi.colores.textoApagado else Movi.colores.texto,
                    // Vuelta 80: con la letra agrandada (más que el ×1,12 de la app) vuelve a dos renglones: a
                    // 360 dp y ×1,3 «Bancolombia Ahorros» no entra en uno (vuelta 55).
                    maxLines = if (LocalDensity.current.fontScale > ESCALA_DE_LETRA_QUE_PARTE_LA_CUENTA) maxOf(renglones, 2) else renglones,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conChevron) Chevron()
            }
        },
        onClick = onClick,
        modifier = modifier,
        densidad = DensidadDeFila.Compacta,
    )
}
