package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi

/** Tag estable de una [TarjetaDePropuesta]. */
const val TAG_TARJETA_DE_PROPUESTA: String = "ds-tarjeta-de-propuesta"

/**
 * **Una propuesta de Movi**: el feed «Movi propone hoy» del camino 5. Es la forma de **toda**
 * propuesta —avisos del banco por confirmar, un pago por vencer, algo que se sale de lo normal, un
 * cuadre de saldo, lo que alguien del hogar le debe a otro—: Movi propone, el dueño decide.
 *
 * Sobre la superficie [NivelDeSuperficie.Propuesta] (el tinte de la marca), con:
 * - un [IconoEnCirculo] a la izquierda, en el [tono] que corresponda (marca si es de Movi, aviso si
 *   vence, peligro si se pasó);
 * - el [titulo], que dice lo que pasa en una frase («El arriendo vence el martes»);
 * - el cuerpo: la [cifra] (con «Ocultar los saldos» incluido) y/o el [cuerpo] en texto;
 * - una acción principal y, si hace falta, una secundaria, en botones chicos ([TamanoDeBoton.S]).
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
    tono: TonoDeIcono = TonoDeIcono.Marca,
    accionSecundaria: String? = null,
    onAccionSecundaria: (() -> Unit)? = null,
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
                Text(text = titulo, style = Movi.textos.titulo, color = Movi.colores.texto)
                if (cifra != null || cuerpo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                    ) {
                        if (cifra != null) Cifra(cifra, VarianteDeCifra.Fila, moneda = moneda)
                        if (cuerpo != null) {
                            Text(
                                text = cuerpo,
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    Boton(texto = accion, onClick = onAccion, tamano = TamanoDeBoton.S)
                    if (accionSecundaria != null && onAccionSecundaria != null) {
                        Boton(
                            texto = accionSecundaria,
                            onClick = onAccionSecundaria,
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                        )
                    }
                }
            }
        }
    }
}
