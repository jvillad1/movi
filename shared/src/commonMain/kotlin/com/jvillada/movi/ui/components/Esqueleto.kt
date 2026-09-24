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
import androidx.compose.ui.graphics.Shape
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
private fun CajaDelEsqueleto(modifier: Modifier, alto: Dp, forma: Shape = RoundedCornerShape(Movi.formas.minima)) {
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
            .background(Movi.colores.borde, forma),
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
 * Un círculo de [diametro] que todavía no llegó: el lugar de un ícono redondo —el de
 * `IconoDeCategoria`, hoy el único caso—. Mismo pulso y mismo color que [BloqueEsqueleto]; la
 * única diferencia es la forma, `Movi.formas.pleno` en vez de la mínima, para que el placeholder
 * sea un círculo y no un cuadrado con las puntas apenas cortadas.
 *
 * Ola B, tarea 3 (fix round 1): sin esto, la fila esqueleto de Movimientos y la de Presupuestos
 * medían el mismo alto que la real pero el título arrancaba más a la izquierda —sin el hueco del
 * ícono— y saltaba ~32–48 dp hacia la derecha al llegar el dato.
 */
@Composable
fun CirculoEsqueleto(diametro: Dp, modifier: Modifier = Modifier) {
    CajaDelEsqueleto(
        modifier = modifier.width(diametro),
        alto = diametro,
        forma = RoundedCornerShape(Movi.formas.pleno),
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
 * El alto de UN renglón de [estilo], en dp: su `lineHeight`. Es el número del que salen todos los
 * bloques de esta familia, así que una pantalla que arma su propio esqueleto (Créditos,
 * Presupuestos, Cuentas) lo pide acá en vez de repetir la cuenta de densidad.
 */
@Composable
fun altoDeUnRenglon(estilo: TextStyle): Dp = with(LocalDensity.current) { estilo.lineHeight.toDp() }

/**
 * **Un renglón «rótulo … cifra» que todavía no llegó**: una línea a la izquierda con el alto de
 * [estiloDelRotulo] y un bloque a la derecha, de [anchoDeLaCifra], con el alto de
 * [estiloDeLaCifra]. Es la forma de «Tu plata $…», «Intereses este mes $…», «Cuota · día 15 $…»:
 * la fila más repetida de las pantallas de plata. El `Row` se alinea al centro, igual que los
 * reales, así que mide lo que mide el más alto de los dos — lo mismo que la fila de verdad.
 */
@Composable
fun RenglonConCifraEsqueleto(
    modifier: Modifier = Modifier,
    fraccionDelRotulo: Float = 0.5f,
    anchoDeLaCifra: Dp = 88.dp,
    estiloDelRotulo: TextStyle = Movi.textos.apoyo,
    estiloDeLaCifra: TextStyle = Movi.textos.monto,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        Box(Modifier.weight(1f)) { LineaEsqueleto(fraccionDelAncho = fraccionDelRotulo, estilo = estiloDelRotulo) }
        BloqueEsqueleto(alto = altoDeUnRenglon(estiloDeLaCifra), ancho = anchoDeLaCifra)
    }
}

/**
 * El encabezado de una sección que todavía no llegó, con la forma de [MinSectionHeader]: mismos
 * rellenos y el alto de `Movi.textos.rotulo`. No dice «PRÉSTAMOS · 12» porque ni el título ni la
 * cuenta se saben todavía — un título sin sus filas ya es una afirmación («tienes préstamos»).
 */
@Composable
fun RotuloDeSeccionEsqueleto(fraccionDelAncho: Float = 0.3f) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Movi.espacios.corto, end = Movi.espacios.corto, bottom = Movi.espacios.medio),
    ) {
        LineaEsqueleto(fraccionDelAncho = fraccionDelAncho, estilo = Movi.textos.rotulo)
    }
}

/**
 * El tag de cada [FilaDeListaEsqueleto], para contarlas en una prueba sin depender de ningún
 * texto (no tienen — son bloques).
 */
const val TAG_FILA_DE_LISTA_ESQUELETO: String = "fila-de-lista-esqueleto"

/**
 * El tag del **título** de una fila esqueleto — [FilaDeListaEsqueleto] y la de Presupuestos
 * (`FilaDePresupuestoEsqueleto`, que tiene su propia forma pero pide prestado este tag). Ola B,
 * tarea 3 (fix round 1): sin un tag propio para el título (a diferencia del bloque completo, que
 * ya tenía [TAG_FILA_DE_LISTA_ESQUELETO]) una prueba no tenía cómo medir en qué X arranca contra
 * el título real, que es lo que prueba que el hueco del ícono es el mismo de los dos lados.
 */
const val TAG_TITULO_DE_FILA_ESQUELETO: String = "titulo-de-fila-esqueleto"

/**
 * **La fila de una lista que todavía no llegó**: título y subtítulo a la izquierda, un monto a la
 * derecha — la forma de [CardRow], que es la fila real de Cuentas y de cada día de Movimientos. La
 * usan las dos pantallas donde hoy se ve una rueda antes de la primera lista (ver sus KDoc): mismo
 * relleno vertical (14 dp) y el mismo hairline entre filas que [CardRow], para que la lista no
 * salte de alto cuando la rueda se convierte en filas de verdad.
 *
 * [conIcono] agrega el ícono de 20 dp que Cuentas pone delante del nombre, DENTRO de la columna de
 * texto (ola B): sin él, el esqueleto de Cuentas decía «una lista» cuando la pantalla real dice
 * «una lista de cuentas».
 *
 * [diametroIconoAlFrente] es la otra posición: un círculo AL FRENTE de toda la fila, afuera de la
 * columna de texto — la forma de `IconoDeCategoria` en `MovementSingleRow` de Movimientos (Ola B,
 * tarea 3, fix round 1), que va a la izquierda del título y no pegado a él. Los dos parámetros son
 * independientes (uno adentro de la columna, el otro afuera) porque son dos filas reales
 * distintas — Cuentas no cambia con este fix.
 */
@Composable
fun FilaDeListaEsqueleto(isLast: Boolean = false, conIcono: Boolean = false, diametroIconoAlFrente: Dp? = null) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Ola B, tarea 2: el tag va ANTES del `padding` — mismo lugar en la cadena que
                // `TAG_FILA_DE_CUENTA` en la fila real de `CardRow` (y que `TAG_FILA_DE_DOCUMENTO`
                // en `FilaDeDocumento`), para que los dos midan el mismo punto (el borde exterior
                // de la fila, relleno incluido) en vez de que uno mida adentro del relleno y el
                // otro no. La fila real y la esqueleto siempre midieron lo mismo visualmente — lo
                // que estaba mal era el PUNTO de medición: con el tag después del `padding`, una
                // prueba que comparaba `TAG_FILA_DE_LISTA_ESQUELETO` contra la fila real (que sí
                // media desde el borde exterior) reportaba 27 dp de diferencia — los 28 dp del
                // relleno vertical (14 arriba + 14 abajo) que el tag se perdía por quedar adentro
                // del `padding` —, un artefacto de la medición, no un salto real en la lista.
                .testTag(TAG_FILA_DE_LISTA_ESQUELETO)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            // El mismo `Movi.espacios.medio` (12 dp) que separa `IconoDeCategoria` del texto en
            // `MovementSingleRow`: con `diametroIconoAlFrente` puesto, este `spacedBy` ya deja el
            // hueco correcto entre el círculo y la columna sin ningún ajuste extra.
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
        ) {
            if (diametroIconoAlFrente != null) {
                CirculoEsqueleto(diametroIconoAlFrente)
            }
            Column(modifier = Modifier.weight(1f)) {
                if (conIcono) {
                    // El ícono de 20 dp de la fila de una cuenta, a 8 dp del nombre: los dos
                    // valores de `AccountsGroup`. El renglón mide el más alto de los dos, como el
                    // real.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BloqueEsqueleto(alto = 20.dp, ancho = 20.dp)
                        LineaEsqueleto(
                            fraccionDelAncho = 0.55f,
                            estilo = Movi.textos.titulo,
                            modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                        )
                    }
                } else {
                    LineaEsqueleto(
                        fraccionDelAncho = 0.55f,
                        estilo = Movi.textos.titulo,
                        modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                    )
                }
                Spacer(Modifier.height(2.dp))
                LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.apoyo)
            }
            BloqueEsqueleto(alto = with(LocalDensity.current) { Movi.textos.monto.lineHeight.toDp() }, ancho = 64.dp)
        }
        if (!isLast) Hairline()
    }
}
