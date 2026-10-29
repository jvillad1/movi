package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * # Vuelta 67 · «Anulado · Deshacer»
 *
 * Anular era la única acción de la app sin vuelta atrás a la vista. Ahora, al anular, abajo aparece
 * un aviso «Anulado» con «Deshacer» durante [DURACION_DEL_AVISO_DE_DESHACER]: restaurar es borrar la
 * marca del anulado (`DELETE /api/events/{id}/void`), nada más. Lo anulado sigue en Actividad →
 * «Anulados», con «Restaurar», pasados esos segundos.
 *
 * El aviso vive en la cáscara (App.kt lo provee con [LocalAvisoDeDeshacer] y lo pinta abajo de la
 * pantalla): la hoja del movimiento se cierra al anular, y un aviso suyo se iría con ella.
 */

/** Cuánto dura el aviso a la vista (ms). */
const val DURACION_DEL_AVISO_DE_DESHACER: Long = 10_000L

/** El tag del aviso, y el de su «Deshacer». */
const val TAG_AVISO_DE_DESHACER: String = "aviso-de-deshacer"
const val TAG_DESHACER_DEL_AVISO: String = "aviso-de-deshacer:deshacer"

/** Lo que dice el aviso al anular. */
const val ANULADO: String = "Anulado"

/**
 * El aviso con «Deshacer» de la cáscara. [mostrar] lo pone (reemplaza al que hubiera); [alCambiar] se
 * llama cuando deshacer salió bien, para que las pantallas relean.
 */
@Stable
class AvisoDeDeshacer(private val alCambiar: () -> Unit = {}) {
    /** Lo que se ve, o `null`. [vez] distingue dos avisos con el mismo texto. */
    data class Aviso(val texto: String, val vez: Long, val deshacer: suspend () -> Unit)

    var actual by mutableStateOf<Aviso?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var trabajando by mutableStateOf(false)
        private set
    private var veces = 0L

    fun mostrar(texto: String, deshacer: suspend () -> Unit) {
        error = null
        trabajando = false
        actual = Aviso(texto, ++veces, deshacer)
    }

    fun cerrar(vez: Long? = null) {
        if (vez == null || actual?.vez == vez) {
            actual = null
            error = null
        }
    }

    internal suspend fun deshacer() {
        val aviso = actual ?: return
        if (trabajando) return
        trabajando = true
        error = null
        try {
            aviso.deshacer()
            cerrar(aviso.vez)
            alCambiar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toUserMessage()
        } finally {
            trabajando = false
        }
    }
}

/** El aviso de la cáscara; `null` donde no hay cáscara (una prueba que monta solo una pantalla). */
val LocalAvisoDeDeshacer = staticCompositionLocalOf<AvisoDeDeshacer?> { null }

/** El aviso, abajo de la pantalla: «Anulado» y «Deshacer». Se va solo a los [DURACION_DEL_AVISO_DE_DESHACER]. */
@Composable
fun AnfitrionDelAvisoDeDeshacer(aviso: AvisoDeDeshacer, modifier: Modifier = Modifier) {
    val actual = aviso.actual ?: return
    val coroutine = rememberCoroutineScope()
    LaunchedEffect(actual.vez) {
        delay(DURACION_DEL_AVISO_DE_DESHACER)
        if (!aviso.trabajando) aviso.cerrar(actual.vez)
    }
    Superficie(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs)
            .testTag(TAG_AVISO_DE_DESHACER),
        nivel = NivelDeSuperficie.Propuesta,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                aviso.error ?: actual.texto,
                style = Movi.textos.cuerpo,
                color = if (aviso.error != null) Movi.colores.sale else Movi.colores.texto,
                modifier = Modifier.weight(1f),
            )
            Boton(
                "Deshacer",
                onClick = { coroutine.launch { aviso.deshacer() } },
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                cargando = aviso.trabajando,
                modifier = Modifier.testTag(TAG_DESHACER_DEL_AVISO),
            )
        }
    }
}
