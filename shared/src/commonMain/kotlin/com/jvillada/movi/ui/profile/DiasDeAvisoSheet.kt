package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.recurrentes.reminderLeadHint

/**
 * Con cuántos días de anticipación avisar un vencimiento.
 *
 * Existe por la regla del dueño: *«otros usuarios no van a poder gestionar desde Claude cambios en
 * la app»*. Este valor vivía **solo** como variable de entorno del server — global para todos y
 * fuera del alcance de cualquiera que use Movi.
 *
 * El **0 es una opción legítima**, no un caso de borde: hay quien quiere el aviso el mismo día y
 * no antes. Por eso la frase cambia entera en vez de decir «0 días antes».
 */
/** Los días que se ofrecen. */
private val DIAS_DE_AVISO: List<Int> = listOf(0, 1, 2, 3, 5, 7)

@Composable
fun DiasDeAvisoSheet(
    diasActuales: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    // Con clave, por lo mismo que `PeriodoSheet`: sin ella una hoja abierta antes de que llegara
    // el perfil se quedaba en el valor por defecto y «Guardar» lo escribía como si fuera elección
    // del dueño.
    var dias by remember(diasActuales) { mutableStateOf(diasActuales.coerceIn(0, 30)) }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        // El contenido de la hoja se desplaza.
        //
        // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
        // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
        // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
        // propia hoja. Sin manera de llegar a él.
        //
        // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
        // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
        // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {

            // El subtítulo es la frase de lo que va a pasar («Te avisamos 3 días antes del
            // vencimiento»): cambia con el día elegido.
            EncabezadoDeHoja(
                "¿Con cuánta anticipación te avisamos?",
                subtitulo = reminderLeadHint(dias),
            )
            Spacer(Modifier.height(Medida.entreSecciones))

            // Vuelta 41: los días son píldoras del sistema (antes cajas blancas sobre la hoja blanca).
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                DIAS_DE_AVISO.forEach { d ->
                    PildoraDeFiltro(
                        texto = d.toString(),
                        seleccionada = d == dias,
                        onClick = { if (!saving) dias = d },
                    )
                }
            }

            Spacer(Modifier.height(Espacio.l))
            // Vuelta 41: el pie del sistema (era una caja amarilla con «Guardando…» hecha a mano).
            PieDeHoja(
                texto = "Guardar",
                onClick = { onSave(dias) },
                cargando = saving,
                error = error,
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
