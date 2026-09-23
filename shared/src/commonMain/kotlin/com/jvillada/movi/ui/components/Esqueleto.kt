package com.jvillada.movi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/**
 * # Esqueletos: la forma de lo que viene, no una rueda que gira
 *
 * Task 7. Antes, la primera carga de la vida (sin instantánea guardada en el aparato, ver
 * `InstantaneaDelInicio`) mostraba una rueda o una barra de progreso y nada más: el dueño no sabía
 * si iba a ver una cifra, una lista de tres cosas o una de treinta. Un esqueleto SÍ lo dice: ocupa
 * el espacio que la cifra o la fila real van a ocupar, así que la pantalla no salta cuando llega el
 * dato.
 *
 * ## El color: [Movi.colores.borde], no [Movi.colores.hilo]
 *
 * `ContrasteDeLosTokensTest` mide `borde` contra el fondo Y contra la tarjeta (mínimo 1,25:1 en los
 * dos temas) porque es el token que la paleta usa para separar planos. `hilo` no tiene esa prueba —
 * es adrede más apagado, pensado para una línea de 1 dp entre dos filas que ya están juntas, no para
 * un bloque que tiene que notarse como «acá va a haber algo». Un bloque relleno con `hilo` se volvía
 * casi invisible en el tema claro; con `borde` se ve en los dos temas por construcción, y sigue
 * leyéndose apagado porque nunca es color de plata ni de marca.
 *
 * ## El pulso, no el barrido
 *
 * Un barrido (un brillo que cruza el bloque) sugiere movimiento de izquierda a derecha, que no es lo
 * que está pasando — nada se está moviendo, se está esperando. El pulso de opacidad dice eso: algo
 * quieto que respira. Sube y baja con [RepeatMode.Reverse] sobre un `tween` de
 * [DURACION_DE_UNA_MITAD_DEL_PULSO_MS] ms, así que un ciclo completo (subir y bajar) dura el doble:
 * ~1,2 s, como pide la especificación.
 *
 * Cada bloque corre su propia [rememberInfiniteTransition] — no hay un reloj compartido — porque
 * todos arrancan en el mismo frame de composición y eso alcanza para que se vean sincronizados sin
 * el costo de coordinar un estado aparte. En Android esta animación ya respeta la escala de
 * animación del sistema (es la misma infraestructura de Compose que usa el resto de la app).
 *
 * ## Cómo probarlos
 *
 * **`composeRule.waitForIdle()` NO se cuelga con un esqueleto en pantalla** — se sospechó lo
 * contrario al escribir esta tarea (una `InfiniteTransition` "nunca termina", así que sonaba
 * razonable que `waitForIdle` la esperara para siempre), pero `EsqueletoDeCuentasTest`,
 * `EsqueletoDeMovimientosTest` y `EsqueletosDelInicioTest` lo desmienten: las tres montan la
 * pantalla con el esqueleto pulsando y llaman `composeRule.waitForIdle()` sin problema, sin
 * tocar el reloj a mano en ningún lado. Esta versión de Compose/Robolectric excluye a
 * `InfiniteTransition` de lo que `waitForIdle` espera —el mismo mecanismo que ya necesitaba
 * cualquier `CircularProgressIndicator`/`LinearProgressIndicator` indeterminado, que también usa
 * una transición infinita por dentro.
 *
 * ## El alto de un bloque, medido contra el de un texto real: hace falta `@GraphicsMode(NATIVE)`
 *
 * Medido para `EsqueletosDelInicioTest` (fix round 1): en el modo `LEGACY` de Robolectric —el
 * default, un shadow liviano sin motor de texto de verdad— **todo `Text`/`BasicText` con estilo
 * propio mide ~17,5 dp de alto, sin importar su `lineHeight` declarado**: se probó
 * `Movi.textos.cuerpo` (18 sp), `.apoyo` (16 sp), `.titulo` (20 sp) y `.cifra` (46 sp, con
 * `autoSize`) y los cuatro dieron el mismo número. Estos bloques, en cambio, son un `Box` con
 * `.height(alto)`: ese alto SIEMPRE se respeta, acá y en el teléfono, porque no depende de
 * ninguna fuente — así que en `LEGACY` una tarjeta cargando (estos bloques) medía
 * sistemáticamente más que una cargada (texto real), sin que fuera un error de diseño.
 *
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)` en la clase de prueba activa el motor de texto real de
 * Robolectric: la misma cifra midió 50,5 dp, cerca de los 46 dp declarados, y `EsqueletosDelInicioTest`
 * pudo volver al margen de `±8 dp` de la especificación para el alto total. Las cuatro pruebas por
 * TAG (cada pieza está, ni una de menos) se mantienen igual: son más baratas y no dependen de
 * ninguna fuente ni de qué modo gráfico use la clase.
 */
private const val DURACION_DE_UNA_MITAD_DEL_PULSO_MS = 600

/**
 * El estado de la opacidad, SIN leer `.value` acá — eso es a propósito, ver [CajaDelEsqueleto].
 * Devolver el `State<Float>` en vez del `Float` es lo que permite leerlo recién en la fase de
 * dibujo.
 */
@Composable
private fun estadoDelPulso(): State<Float> {
    val transicion = rememberInfiniteTransition(label = "esqueleto")
    return transicion.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DURACION_DE_UNA_MITAD_DEL_PULSO_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "opacidadDelEsqueleto",
    )
}

@Composable
private fun CajaDelEsqueleto(modifier: Modifier, alto: Dp) {
    val pulso = estadoDelPulso()
    Box(
        modifier
            .height(alto)
            // `Modifier.graphicsLayer { }` en vez de `Modifier.alpha(pulso.value)`: leer
            // `pulso.value` ACÁ, dentro del lambda que corre en la fase de dibujo de la capa, no
            // recompone `CajaDelEsqueleto` en cada frame del pulso — solo vuelve a dibujar esa
            // capa, que es lo único que cambió. Leer `pulso.value` afuera (como estaba, con
            // `by` en la función `@Composable`) hace exactamente lo que este comentario dice que
            // no hace falta: cada uno de los 60 frames por segundo del pulso recomponía TODAS las
            // filas esqueleto de la pantalla (hasta 6 en Cuentas/Movimientos, más las del hero y
            // «Pregúntale a Movi» juntas), por un cambio que solo necesitaba redibujar un `Box`.
            .graphicsLayer { alpha = pulso.value }
            .background(Movi.colores.borde, RoundedCornerShape(Movi.formas.minima)),
    )
}

/**
 * Un bloque redondeado del alto y —si se da— el ancho de lo que todavía no llegó. Sin [ancho],
 * ocupa todo el ancho disponible: es lo que necesita la barra de dos tramos del hero, que hoy va de
 * borde a borde.
 */
@Composable
fun BloqueEsqueleto(alto: Dp, ancho: Dp? = null, modifier: Modifier = Modifier) {
    CajaDelEsqueleto(
        modifier = if (ancho != null) modifier.width(ancho) else modifier.fillMaxWidth(),
        alto = alto,
    )
}

/**
 * Un renglón de texto que todavía no llegó: [fraccionDelAncho] del ancho disponible, con el alto de
 * [estilo] —`Movi.textos.cuerpo` si no se dice otro—, para que el bloque ocupe lo mismo que ocuparía
 * el texto real y nada salte cuando llegue.
 */
@Composable
fun LineaEsqueleto(fraccionDelAncho: Float, modifier: Modifier = Modifier, estilo: TextStyle = Movi.textos.cuerpo) {
    val alto = with(LocalDensity.current) { estilo.lineHeight.toDp() }
    CajaDelEsqueleto(modifier = modifier.fillMaxWidth(fraccionDelAncho), alto = alto)
}

/**
 * El tag de cada [FilaDeListaEsqueleto], para contarlas en una prueba sin depender de ningún
 * texto (no tienen — son bloques).
 */
const val TAG_FILA_DE_LISTA_ESQUELETO: String = "fila-de-lista-esqueleto"

/**
 * **La fila de una lista que todavía no llegó**: título y subtítulo a la izquierda, un monto a la
 * derecha — la forma de [CardRow], que es la fila real de Cuentas y de cada día de Movimientos. La
 * usan las dos pantallas donde hoy se ve una rueda antes de la primera lista (ver sus KDoc): mismo
 * relleno vertical (14 dp) y el mismo hairline entre filas que [CardRow], para que la lista no
 * salte de alto cuando la rueda se convierte en filas de verdad.
 */
@Composable
fun FilaDeListaEsqueleto(isLast: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .testTag(TAG_FILA_DE_LISTA_ESQUELETO),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LineaEsqueleto(fraccionDelAncho = 0.55f, estilo = Movi.textos.titulo)
                Spacer(Modifier.height(2.dp))
                LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.apoyo)
            }
            BloqueEsqueleto(alto = with(LocalDensity.current) { Movi.textos.monto.lineHeight.toDp() }, ancho = 64.dp)
        }
        if (!isLast) Hairline()
    }
}
