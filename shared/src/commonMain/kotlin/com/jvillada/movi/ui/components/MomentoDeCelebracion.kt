package com.jvillada.movi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import kotlinx.coroutines.launch

/** La tarjeta de la celebración, para que las pruebas la encuentren. */
const val TAG_MOMENTO_DE_CELEBRACION = "momento-de-celebracion"

/**
 * # ¿Hay que celebrar? Solo en la transición
 *
 * Ola 1 · «Que Movi se sienta viva»: **un solo momento de celebración**, en dos lugares —el
 * checklist del período que se completa y «Por revisar» que queda vacío—, y **solo cuando pasa**,
 * no cada vez que se abre la pantalla con todo ya hecho. Una celebración que se repite deja de
 * celebrar algo.
 *
 * [observar] recibe cada estado que la pantalla ve: `true` = completo, `false` = falta algo, `null`
 * = todavía no se sabe (cargando, o la lectura falló). Devuelve `true` **una sola vez**: cuando lo
 * último que se supo fue «falta algo», ahora es «completo» y el dueño hizo algo en esta visita
 * ([huboAccion]). El primer estado conocido nunca celebra —es lo que había al abrir—, y lo
 * desconocido no borra lo último que se supo: «falta 1» → cargando → «todo pagado» sí celebra.
 *
 * Puro y sin Compose, para probarlo (`CelebracionTest`). [ultimoConocido] es lo que una pantalla
 * guarda en su estado para no olvidarlo al irse y volver (Por revisar → Reconciliar → Por revisar).
 */
class Celebracion(var ultimoConocido: Boolean? = null) {
    fun observar(completo: Boolean?, huboAccion: Boolean): Boolean {
        if (completo == null) return false
        val antes = ultimoConocido
        ultimoConocido = completo
        return antes == false && completo && huboAccion
    }
}

/**
 * La celebración: un check que se dibuja dentro de un círculo que rebota apenas, y [frase] al lado.
 * Breve (lo que dura [Movimiento.LARGO_MS] más un poco) y una sola vez por aparición. Con el sistema
 * pidiendo menos movimiento, aparece ya dibujada.
 */
@Composable
fun MomentoDeCelebracion(frase: String, modifier: Modifier = Modifier) {
    val reducido = LocalMovimientoReducido.current
    val escala = remember { Animatable(if (reducido) 1f else 0.6f) }
    val trazo = remember { Animatable(if (reducido) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reducido) return@LaunchedEffect
        launch {
            // Un rebote corto: pasa un poco del tamaño y vuelve.
            escala.animateTo(1.08f, Movimiento.alEntrar(false, Movimiento.MEDIO_MS))
            escala.animateTo(1f, Movimiento.alVolver(false, Movimiento.CORTO_MS))
        }
        trazo.animateTo(1f, Movimiento.alEntrar(false))
    }
    // El check del color de la tarjeta sobre el verde de «entra»: oscuro sobre verde claro en el
    // tema oscuro, blanco sobre verde oscuro en el claro.
    val colorDelCheck = Movi.colores.tarjeta
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Movi.colores.tarjeta)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(TAG_MOMENTO_DE_CELEBRACION),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = escala.value
                    scaleY = escala.value
                }
                .clip(CircleShape)
                .background(Movi.colores.entra),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val camino = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.55f)
                    lineTo(size.width * 0.40f, size.height * 0.82f)
                    lineTo(size.width * 0.90f, size.height * 0.20f)
                }
                val medida = PathMeasure().apply { setPath(camino, false) }
                val parcial = Path()
                medida.getSegment(0f, medida.length * trazo.value, parcial, true)
                drawPath(
                    parcial,
                    color = colorDelCheck,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Text(
            text = frase,
            style = Movi.textos.cuerpoFuerte,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
    }
}
