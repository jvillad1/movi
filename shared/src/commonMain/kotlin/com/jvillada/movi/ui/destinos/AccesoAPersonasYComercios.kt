package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.shared.model.nombresDeLasCuentasDeOtros
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono

/** El tag de la fila que abre «Personas y comercios» (al final de Espacios). */
const val TAG_ACCESO_A_PERSONAS_Y_COMERCIOS: String = "acceso-personas-y-comercios"

/**
 * Lo que dice el acceso a la derecha: los nombres —primero a quien más se le mandó este período—
 * o, sin ninguno guardado, la invitación. Mientras no contestó, lo mismo que sin ninguno: el
 * renglón no cambia de alto por eso (es de una sola línea).
 */
internal fun resumenDelAcceso(destinos: List<DestinoConocido>?): String =
    if (destinos.isNullOrEmpty()) "Guarda a quién le envías plata" else nombresDeLasCuentasDeOtros(destinos)

/**
 * # La puerta a «Personas y comercios»
 *
 * El dueño (4-oct-2026): *«que tenga un acceso muy fácil de encontrar para ver cuentas de
 * terceros»*. Llegó a tener cuatro puertas (Movimientos, Patrimonio, Ajustes y la hoja de un
 * espacio). **Ola R4: quedan dos** — esta fila, al final de **Espacios** (lo que tienes con los
 * demás), y la ficha de Ajustes. Patrimonio contesta «¿qué tengo yo?» y Movimientos «¿en qué se
 * fue?»: ninguna de las dos es la casa de los terceros.
 *
 * Es una [Fila] del sistema con los nombres a la vista («Hernán, Ana, Cancha El Gol y 2 más») de
 * subtítulo. Siempre está y siempre mide lo mismo: no aparece cuando contesta la lectura, así que no
 * empuja nada. Sin nadie guardado invita a guardar.
 */
@Composable
fun RenglonDePersonasYComercios(
    destinos: List<DestinoConocido>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Superficie(modifier = modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = PERSONAS_Y_COMERCIOS,
            subtitulo = resumenDelAcceso(destinos),
            inicio = { IconoEnCirculo(Icons.Rounded.Groups, tono = TonoDeIcono.Info) },
            final = { Chevron() },
            onClick = onClick,
            modifier = Modifier.testTag(TAG_ACCESO_A_PERSONAS_Y_COMERCIOS),
        )
    }
}
