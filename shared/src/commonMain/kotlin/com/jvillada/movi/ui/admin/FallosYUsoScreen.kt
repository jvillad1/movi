package com.jvillada.movi.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.components.ds.Vacio

/**
 * # «Fallos y uso» (solo admin)
 *
 * **Esqueleto de la vuelta 102.** La pantalla es de la vuelta 100 (fallos de la app y uso), que corre en
 * paralelo y todavía no está en master; la 102 solo trae la pestaña «Servidor» ([PestanaServidor]). Esto
 * es lo mínimo para engancharla: el encabezado, las tres pestañas y, en «Fallos» y «Uso», un vacío. Al
 * juntar las dos vueltas, esta pantalla se queda con la de la 100 y la pestaña «Servidor» se engancha
 * en la suya.
 */

internal const val TITULO_FALLOS_Y_USO = "Fallos y uso"
internal val PESTANAS_DE_FALLOS_Y_USO = listOf("Fallos", "Uso", "Servidor")
internal const val PESTANA_SERVIDOR = 2

@Composable
fun FallosYUsoScreen(onNavigate: (Screen) -> Unit) {
    // Abre en «Servidor»: en este esqueleto es la única con datos.
    var pestana by rememberSaveable { mutableStateOf(PESTANA_SERVIDOR) }
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(title = TITULO_FALLOS_Y_USO, leading = HeaderLeading.Back(fallback = Screen.Tu))
        Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion)) {
            SelectorSegmentado(labels = PESTANAS_DE_FALLOS_Y_USO, selected = pestana, onSelect = { pestana = it })
        }
        when (pestana) {
            PESTANA_SERVIDOR -> PestanaServidor(modifier = Modifier.weight(1f))
            else -> Column(modifier = Modifier.padding(Medida.margenDePantalla)) {
                Vacio(
                    titulo = "Todavía no hay datos aquí",
                    detalle = "Los fallos y el uso de la app se muestran en esta pestaña cuando lleguen.",
                )
            }
        }
    }
}
