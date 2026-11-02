package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi

/** Tag estable de una [TarjetaDePropuesta]. */
const val TAG_TARJETA_DE_PROPUESTA: String = "ds-tarjeta-de-propuesta"

/** El subtítulo de una [TarjetaDePropuesta], debajo de la cifra. */
const val TAG_SUBTITULO_DE_PROPUESTA: String = "ds-subtitulo-de-propuesta"

/**
 * **Una propuesta de Movi**: el feed «Movi propone hoy» del camino 5. Es la forma de **toda**
 * propuesta —avisos del banco por confirmar, un pago por vencer, algo que se sale de lo normal, un
 * cuadre de saldo, lo que alguien del hogar le debe a otro—: Movi propone, el dueño decide.
 *
 * Sobre la superficie [NivelDeSuperficie.Propuesta] (el tinte de la marca), **siempre con la misma
 * anatomía**, cada pieza en su renglón y de arriba abajo:
 * - un [IconoEnCirculo] a la izquierda, en el [tono] que corresponda (marca si es de Movi, aviso si
 *   vence, peligro si se pasó);
 * - el [titulo], que dice lo que pasa en una frase («El arriendo vence el martes»);
 * - la [cifra], opcional, en su propio renglón (con «Ocultar los saldos» incluido);
 * - el subtítulo ([cuerpo]), opcional, debajo y a todo el ancho del texto;
 * - una acción principal y, si hace falta, una secundaria, en botones chicos ([TamanoDeBoton.S]).
 *   Vuelta 97: y una tercera ([accionTerciaria], «Nunca para este comercio» de la propuesta de
 *   repartir); con ella los botones van en una fila que se parte si no caben, nunca cortados.
 *
 * Vuelta de diseño 1: la cifra y el subtítulo iban en el mismo renglón, y «Arriendo venció hace 21
 * días» llevaba «$1.800.000» a la izquierda y «y 2 pagos más por vencer» partido en dos líneas a la
 * derecha. Ahora el subtítulo tiene todo el ancho: una frase corta entra en un renglón. No se corta
 * con «…»: hay propuestas cuyo subtítulo explica por qué decidir («si lo confirmas, se cuenta dos
 * veces») y esa frase no puede perderse.
 *
 * ```
 * TarjetaDePropuesta(
 *     icono = Icons.Rounded.Inbox,
 *     titulo = "Llegaron 3 avisos del banco",
 *     cifra = 186_400,
 *     cuerpo = "por confirmar",
 *     accion = "Confirmar los 3", onAccion = ::confirmar,
 *     accionSecundaria = "Ver", onAccionSecundaria = ::ver,
 * )
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaDePropuesta(
    icono: ImageVector,
    titulo: String,
    accion: String,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier,
    cifra: Long? = null,
    moneda: String = "COP",
    cuerpo: String? = null,
    /**
     * Vuelta 82 · **el amarillo se pide a propósito**: el círculo va neutro (la capa 3) salvo que quien
     * llama pida otro tono —[TonoDeIcono.Aviso] si urge—. Era [TonoDeIcono.Marca]: toda propuesta nueva
     * traía un círculo amarillo.
     */
    tono: TonoDeIcono = TonoDeIcono.Neutro,
    accionSecundaria: String? = null,
    onAccionSecundaria: (() -> Unit)? = null,
    /**
     * Cuántos renglones ocupó el título al dibujarse. Para la pantalla que reserva el lugar de la
     * tarjeta mientras carga (Categorías, con `FormaRecordada`): el esqueleto mide lo mismo.
     */
    alMedirElTitulo: ((renglones: Int) -> Unit)? = null,
    /**
     * Vuelta 79 · un solo amarillo por vista. Vuelta 82: **por defecto [VarianteDeBoton.Secundario]**;
     * quien llama pide [VarianteDeBoton.Principal] solo si es la única acción principal de su vista (la
     * tarjeta de avisos de Actividad con «Confirmar 2», el lote de Por revisar, «Revisar» en Categorías).
     */
    varianteDeLaAccion: VarianteDeBoton = VarianteDeBoton.Secundario,
    /** Vuelta 97 · una tercera acción, fantasma como la secundaria. */
    accionTerciaria: String? = null,
    onAccionTerciaria: (() -> Unit)? = null,
) {
    Superficie(
        modifier = modifier.fillMaxWidth().testTag(TAG_TARJETA_DE_PROPUESTA),
        nivel = NivelDeSuperficie.Propuesta,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            IconoEnCirculo(icono = icono, tono = tono)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
            ) {
                Text(
                    text = titulo,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    onTextLayout = { alMedirElTitulo?.invoke(it.lineCount) },
                )
                if (cifra != null) Cifra(cifra, VarianteDeCifra.Fila, moneda = moneda)
                if (cuerpo != null) {
                    Text(
                        text = cuerpo,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.fillMaxWidth().testTag(TAG_SUBTITULO_DE_PROPUESTA),
                    )
                }
                val botones: @Composable () -> Unit = {
                    Boton(texto = accion, onClick = onAccion, variante = varianteDeLaAccion, tamano = TamanoDeBoton.S)
                    if (accionSecundaria != null && onAccionSecundaria != null) {
                        Boton(
                            texto = accionSecundaria,
                            onClick = onAccionSecundaria,
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                        )
                    }
                    if (accionTerciaria != null && onAccionTerciaria != null) {
                        Boton(
                            texto = accionTerciaria,
                            onClick = onAccionTerciaria,
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                        )
                    }
                }
                if (accionTerciaria != null && onAccionTerciaria != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs),
                        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                    ) { botones() }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs),
                        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                    ) { botones() }
                }
            }
        }
    }
}
