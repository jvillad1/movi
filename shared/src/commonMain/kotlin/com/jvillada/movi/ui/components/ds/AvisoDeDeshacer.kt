package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * # El aviso de abajo: «Anotado · Deshacer» (vuelta 67, generalizado en la vuelta 74)
 *
 * Vuelta 67: anular era la única acción de la app sin vuelta atrás a la vista; al anular, abajo
 * aparecía «Anulado» con «Deshacer».
 *
 * Vuelta 74 («un toque por movimiento»): **es EL aviso de la app** para todo lo que guarda un
 * movimiento —Agregar (gasto, ingreso, traspaso), confirmar un aviso del banco, «Es este»— y para
 * anular. Dice qué quedó («Anotado · Restaurantes $32.000»), ofrece «Deshacer» durante
 * [DURACION_DEL_AVISO_DE_DESHACER] y, si hace falta, **una acción más** ([AvisoDeDeshacer.Accion]):
 * «Repartir con Caro», que al hacerse cambia el aviso por el suyo («Repartido con Caro · tu parte
 * $16.000 · Deshacer»). Una acción con varias opciones (varios grupos) abre una hoja para elegir.
 *
 * **Vive en la cáscara** (App.kt lo provee con [LocalAvisoDeDeshacer] y lo pinta abajo del hueco de
 * la pantalla): así no tapa la barra de pestañas (está encima de ella) ni el «+» (que vive en el rail),
 * y sobrevive a que se cierre la hoja o la pantalla que lo pidió. Entra y sale con el movimiento de
 * siempre ([Movimiento]), que con el sistema pidiendo menos movimiento pasa en el acto.
 */

/** Cuánto dura el aviso a la vista (ms). Vuelta 74: unos 8 s para todo (antes, 10 s el de anular). */
const val DURACION_DEL_AVISO_DE_DESHACER: Long = 8_000L

/** El tag del aviso, el de su «Deshacer» y el de su acción. */
const val TAG_AVISO_DE_DESHACER: String = "aviso-de-deshacer"
const val TAG_DESHACER_DEL_AVISO: String = "aviso-de-deshacer:deshacer"
const val TAG_ACCION_DEL_AVISO: String = "aviso-de-deshacer:accion"
const val TAG_ELEGIR_EN_EL_AVISO: String = "aviso-de-deshacer:elegir"

/** Lo que dice el aviso al anular. */
const val ANULADO: String = "Anulado"

/** El botón de deshacer. */
const val DESHACER: String = "Deshacer"

/**
 * Una falla que trae su frase para el aviso (por ejemplo, [com.jvillada.movi.ui.espacios.REPARTO_SIN_CONEXION]):
 * el aviso la dice tal cual, sin pasarla por `toUserMessage`.
 */
class FalloDelAviso(val frase: String) : Exception(frase)

/**
 * El aviso con «Deshacer» de la cáscara. [mostrar] lo pone (reemplaza al que hubiera); [alCambiar] se
 * llama cuando deshacer o la acción salieron bien, para que las pantallas relean.
 */
@Stable
class AvisoDeDeshacer(private val alCambiar: () -> Unit = {}) {
    /** Una opción de una [Accion] que pide elegir (un grupo de varios). */
    data class Opcion(val rotulo: String, val detalle: String? = null, val hacer: suspend () -> Unit)

    /**
     * La acción del aviso, además de «Deshacer» (vuelta 74: «Repartir con Caro»). Con [opciones], tocarla
     * abre una hoja para elegir; sin ellas, hace [hacer].
     */
    data class Accion(
        val rotulo: String,
        val hacer: suspend () -> Unit = {},
        val opciones: List<Opcion>? = null,
        val tituloDeLaEleccion: String = rotulo,
    )

    /** Lo que se ve, o `null`. [vez] distingue dos avisos con el mismo texto. Sin [deshacer], sin el botón. */
    data class Aviso(
        val texto: String,
        val vez: Long,
        val deshacer: (suspend () -> Unit)?,
        val accion: Accion? = null,
    )

    var actual by mutableStateOf<Aviso?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var trabajando by mutableStateOf(false)
        private set
    /** La hoja de elegir de una [Accion] con opciones está abierta: el aviso espera. */
    var eligiendo by mutableStateOf(false)
        private set
    /**
     * Cuántas veces se deshizo algo en esta sesión: quien ofrece algo después de guardar (el «¿se
     * repite?» de App.kt) lo mira para no ofrecerlo sobre un movimiento que ya no está.
     */
    var deshechos by mutableStateOf(0)
        private set
    private var veces = 0L

    fun mostrar(texto: String, accion: Accion? = null, deshacer: (suspend () -> Unit)? = null) {
        error = null
        trabajando = false
        eligiendo = false
        actual = Aviso(texto, ++veces, deshacer, accion)
    }

    fun cerrar(vez: Long? = null) {
        if (vez == null || actual?.vez == vez) {
            actual = null
            error = null
            eligiendo = false
        }
    }

    internal suspend fun deshacer() {
        val aviso = actual ?: return
        val accion = aviso.deshacer ?: return
        correr(aviso) {
            accion()
            deshechos++
        }
    }

    /** Toca la acción: abre la elección si tiene opciones, o la hace. */
    internal suspend fun tocarLaAccion() {
        val aviso = actual ?: return
        val accion = aviso.accion ?: return
        if (accion.opciones != null) {
            if (!trabajando) eligiendo = true
            return
        }
        correr(aviso, accion.hacer)
    }

    /** Eligió [opcion] en la hoja de la acción. */
    internal suspend fun elegir(opcion: Opcion) {
        val aviso = actual ?: return
        eligiendo = false
        correr(aviso, opcion.hacer)
    }

    internal fun dejarDeElegir() {
        eligiendo = false
    }

    /**
     * Corre [bloque] con el aviso «trabajando». Bien: se cierra —salvo que el bloque haya puesto otro
     * aviso (la acción que se dice a sí misma: «Repartido con Caro»)— y las pantallas releen. Mal: el
     * aviso se queda y dice por qué.
     */
    private suspend fun correr(aviso: Aviso, bloque: suspend () -> Unit) {
        if (trabajando) return
        trabajando = true
        error = null
        try {
            bloque()
            if (actual?.vez == aviso.vez) cerrar(aviso.vez)
            alCambiar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FalloDelAviso) {
            if (actual?.vez == aviso.vez) error = e.frase
        } catch (e: Throwable) {
            if (actual?.vez == aviso.vez) error = e.toUserMessage()
        } finally {
            trabajando = false
        }
    }
}

/** El aviso de la cáscara; `null` donde no hay cáscara (una prueba que monta solo una pantalla). */
val LocalAvisoDeDeshacer = staticCompositionLocalOf<AvisoDeDeshacer?> { null }

/**
 * El aviso, abajo de la pantalla: el texto, la acción (si hay) y «Deshacer». Se va solo a los
 * [DURACION_DEL_AVISO_DE_DESHACER] —la cuenta se para mientras trabaja o mientras se elige—.
 */
@Composable
fun AnfitrionDelAvisoDeDeshacer(aviso: AvisoDeDeshacer, modifier: Modifier = Modifier) {
    val actual = aviso.actual
    val reducido = LocalMovimientoReducido.current
    // Lo último que se vio, para que el aviso salga animado con su texto (no en blanco). Un simple
    // recuerdo, no un estado: escribirlo mientras se compone no tiene que recomponer nada.
    val ultimo = remember { arrayOfNulls<AvisoDeDeshacer.Aviso>(1) }
    if (actual != null) ultimo[0] = actual
    val coroutine = rememberCoroutineScope()
    if (actual != null) {
        LaunchedEffect(actual.vez, aviso.trabajando, aviso.eligiendo) {
            if (aviso.trabajando || aviso.eligiendo) return@LaunchedEffect
            delay(DURACION_DEL_AVISO_DE_DESHACER)
            aviso.cerrar(actual.vez)
        }
    }
    AnimatedVisibility(
        visible = actual != null,
        modifier = modifier,
        enter = fadeIn(Movimiento.alEntrar(reducido)) + slideInVertically(Movimiento.alEntrar(reducido)) { it / 2 },
        exit = fadeOut(Movimiento.alSalir(reducido)) + slideOutVertically(Movimiento.alSalir(reducido)) { it / 2 },
    ) {
        val visto = actual ?: ultimo[0] ?: return@AnimatedVisibility
        Superficie(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs)
                .testTag(TAG_AVISO_DE_DESHACER),
            nivel = NivelDeSuperficie.Propuesta,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Text(
                    aviso.error ?: visto.texto,
                    style = Movi.textos.cuerpo,
                    color = if (aviso.error != null) Movi.colores.sale else Movi.colores.texto,
                    modifier = Modifier.weight(1f),
                )
                visto.accion?.let { accion ->
                    Boton(
                        accion.rotulo,
                        onClick = { coroutine.launch { aviso.tocarLaAccion() } },
                        variante = VarianteDeBoton.Secundario,
                        tamano = TamanoDeBoton.S,
                        habilitado = !aviso.trabajando,
                        modifier = Modifier.testTag(TAG_ACCION_DEL_AVISO),
                    )
                }
                if (visto.deshacer != null) {
                    Boton(
                        DESHACER,
                        onClick = { coroutine.launch { aviso.deshacer() } },
                        variante = VarianteDeBoton.Fantasma,
                        tamano = TamanoDeBoton.S,
                        cargando = aviso.trabajando,
                        modifier = Modifier.testTag(TAG_DESHACER_DEL_AVISO),
                    )
                }
            }
        }
    }
    // La elección de una acción con opciones (varios grupos): una hoja con una fila por opción.
    val opciones = actual?.accion?.opciones
    if (aviso.eligiendo && actual != null && opciones != null) {
        MarcoDeHoja(onDismiss = { aviso.dejarDeElegir() }) {
            EncabezadoDeHoja(actual.accion.tituloDeLaEleccion)
            Column(modifier = Modifier.padding(top = Espacio.s, bottom = Medida.margenDePantalla)) {
                Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                    opciones.forEachIndexed { i, opcion ->
                        if (i > 0) Separador()
                        Fila(
                            titulo = opcion.rotulo,
                            subtitulo = opcion.detalle,
                            onClick = { coroutine.launch { aviso.elegir(opcion) } },
                            modifier = Modifier.testTag("$TAG_ELEGIR_EN_EL_AVISO:$i"),
                        )
                    }
                }
            }
        }
    }
}
