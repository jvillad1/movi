package com.jvillada.movi.ui.mas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.launch

/** Lo que dice la fila de Ajustes → Tus datos (vuelta 103). */
const val CLAVE_DE_TUS_EXTRACTOS: String = "Clave de tus extractos"
const val OLVIDAR_LA_CLAVE: String = "Olvidarla"
const val TAG_FILA_DE_LA_CLAVE_DE_EXTRACTOS: String = "ajustes-clave-de-extractos"

/**
 * **«Clave de tus extractos · Guardada»** en Ajustes → Tus datos (vuelta 103), con «Olvidarla», que la
 * borra del server. Solo aparece si hay una guardada —si no hay, o si la pregunta falla, no se pinta
 * nada (tampoco el separador)—. La clave nunca llega a la app: el server solo dice si hay una.
 */
@Composable
internal fun FilaDeLaClaveDeExtractos() {
    var guardada by remember { mutableStateOf(false) }
    var olvidando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        guardada = intentar { Repositories.wallets.getEstadoDeLaClaveDeExtractos() }.getOrNull()?.guardada == true
    }
    if (!guardada) return
    Separador()
    Fila(
        titulo = CLAVE_DE_TUS_EXTRACTOS,
        subtitulo = "Guardada",
        inicio = { IconoEnCirculo(Icons.Rounded.Key, tono = TonoDeIcono.Neutro) },
        final = {
            Boton(
                OLVIDAR_LA_CLAVE,
                onClick = {
                    if (!olvidando) {
                        olvidando = true
                        alcance.launch {
                            intentar { Repositories.wallets.olvidarLaClaveDeExtractos() }
                                .onSuccess { guardada = false }
                            olvidando = false
                        }
                    }
                },
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                cargando = olvidando,
            )
        },
        modifier = Modifier.testTag(TAG_FILA_DE_LA_CLAVE_DE_EXTRACTOS),
    )
}
