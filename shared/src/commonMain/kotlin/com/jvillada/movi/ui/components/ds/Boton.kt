package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.theme.Radio

/** Tag estable de un [Boton]. */
const val TAG_BOTON: String = "ds-boton"

/** Lo que un lector de pantalla dice de un botón que está cargando. */
const val ROTULO_CARGANDO: String = "Cargando"

/**
 * **Qué tan fuerte habla un botón.** Uno [Principal] por pantalla o por tarjeta, como mucho.
 */
enum class VarianteDeBoton {
    /** La acción principal: amarillo de la marca con la tinta encima (opción 3). */
    Principal,

    /** Una acción al lado de la principal: blanco con el borde de botón y texto normal. */
    Secundario,

    /**
     * **El botón de texto**: una acción que casi no pesa, solo el texto en el azul del enlace, sin
     * fondo —tampoco apagado—. «Saltar este paso» debajo de «Siguiente», «Ver» al lado de «Confirmar».
     */
    Fantasma,

    /** Borrar, anular, salir: el color de `sale` sobre su lavado. Nunca lleno de rojo. */
    Peligro,
}

/**
 * **La variante de un [Boton], en su semántica** (vuelta de diseño 18), como `VarianteDeCifraKey`:
 * deja vigilar que «Borrar» vaya en `Peligro` y «Cancelar» en `Fantasma` sin leer colores. No le
 * cambia nada al lector de pantalla.
 */
val VarianteDeBotonKey: SemanticsPropertyKey<VarianteDeBoton> = SemanticsPropertyKey("VarianteDeBoton")

/** Ver [VarianteDeBotonKey]. */
var SemanticsPropertyReceiver.varianteDeBoton: VarianteDeBoton by VarianteDeBotonKey

/**
 * **¿Lleva el amarillo lleno de la acción?** (vuelta 79): el `Boton` principal habilitado y la acción
 * destacada de la cuadrícula lo dicen en su semántica. La regla nueva del sistema es **un solo amarillo
 * por vista** (`UnSoloAmarilloPorVistaTest` lo cuenta en Inicio y en Pagos, sin leer píxeles).
 */
val LlevaElAmarilloKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("LlevaElAmarillo")

/** Ver [LlevaElAmarilloKey]. */
var SemanticsPropertyReceiver.llevaElAmarillo: Boolean by LlevaElAmarilloKey

/**
 * **El tamaño de un botón.** [M] (48 dp, el objetivo táctil) es el de siempre; [S] (36 dp) va solo
 * dentro de una tarjeta —una propuesta, una fila de acciones— y su área de toque igual llega a 48,
 * porque Compose la extiende sin ocupar lugar.
 */
enum class TamanoDeBoton(val alto: Dp) {
    M(Medida.objetivoTactil),
    S(Medida.altoChico),
}

/** El fondo y el texto de una variante, en la paleta del tema. Pura, para probarla. */
fun coloresDelBoton(variante: VarianteDeBoton, colores: ColoresDeMovi): Pair<Color, Color> = when (variante) {
    VarianteDeBoton.Principal -> colores.marca to colores.sobreMarca
    VarianteDeBoton.Secundario -> colores.tarjeta to colores.texto
    VarianteDeBoton.Fantasma -> Color.Transparent to colores.enlace
    VarianteDeBoton.Peligro -> colores.lavado(colores.sale) to colores.sale
}

/**
 * El borde de una variante: solo el [VarianteDeBoton.Secundario] lleva uno (el blanco sobre blanco
 * no se vería), `bordeDeBoton` a [Medida.trazoDeBoton]. Pura.
 */
fun bordeDelBoton(variante: VarianteDeBoton, colores: ColoresDeMovi): Color? =
    if (variante == VarianteDeBoton.Secundario) colores.bordeDeBoton else null

/**
 * **El fondo de un botón presionado con menos movimiento** (vuelta 79): sin la escala, el toque se ve en
 * el fondo. El principal pasa a `marcaPresionada` (un amarillo más hondo, no mostaza); los demás, a la
 * capa 3. Pura.
 */
fun fondoDelBotonPresionado(variante: VarianteDeBoton, colores: ColoresDeMovi): Color = when (variante) {
    VarianteDeBoton.Principal -> colores.marcaPresionada
    VarianteDeBoton.Peligro -> colores.lavado(colores.sale)
    else -> colores.capa3
}

/**
 * El fondo y el texto de una variante **apagada**: la capa 3 y el texto apagado, para que se siga
 * leyendo como botón pero no como tocable; el [VarianteDeBoton.Fantasma] sigue sin fondo, porque un
 * botón de texto que se apaga y se rellena de gris parece otro botón. Pura.
 */
fun coloresDelBotonApagado(variante: VarianteDeBoton, colores: ColoresDeMovi): Pair<Color, Color> = when (variante) {
    VarianteDeBoton.Fantasma -> Color.Transparent to colores.textoApagado
    else -> colores.capa3 to colores.textoApagado
}

/**
 * **El botón de Movi.** Reemplaza a los botones armados a mano —la auditoría encontró botones
 * principales de 46, 48, 50 y 54 dp de alto, con radios de 10, 12, 14 y píldora—.
 *
 * - **Píldora** ([Radio.pill], opción 3), alto **mínimo** por [tamano], texto en `cuerpoFuerte` (15/700),
 *   centrado por la fila. Vuelta 88: un rótulo que no cabe en un renglón baja a dos y el botón crece (antes el alto
 *   era fijo y el segundo renglón quedaba afuera, en el teléfono del papá del dueño).
 *   El secundario lleva el borde de botón ([bordeDelBoton]).
 * - [icono] opcional a la izquierda, a [Icono.m].
 * - [cargando]: la ruedita reemplaza al ícono, el botón no se toca y el lector de pantalla dice
 *   «Cargando». El ancho no cambia (el texto se queda), así que nada salta.
 * - Apagado ([habilitado] en `false`): fondo de la capa 3 y texto apagado, para que se siga leyendo
 *   como botón pero no como tocable; el de texto ([VarianteDeBoton.Fantasma]) sigue sin fondo (ver
 *   [coloresDelBotonApagado]).
 *
 * ```
 * Boton("Guardar", onClick = ::guardar, anchoCompleto = true, cargando = guardando)
 * Boton("Ver", onClick = ::ver, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
 * ```
 */
@Composable
fun Boton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variante: VarianteDeBoton = VarianteDeBoton.Principal,
    tamano: TamanoDeBoton = TamanoDeBoton.M,
    icono: ImageVector? = null,
    cargando: Boolean = false,
    habilitado: Boolean = true,
    anchoCompleto: Boolean = false,
) {
    val toque = remember { MutableInteractionSource() }
    // Vuelta 79: con el sistema pidiendo menos movimiento no hay escala, y el toque se ve en el fondo
    // (`fondoDelBotonPresionado`): nunca un velo de tinta sobre el amarillo.
    val presionadoSinEscala = toque.estaPresionado() && LocalMovimientoReducido.current
    val (fondo, color) = if (habilitado) {
        coloresDelBoton(variante, Movi.colores).let { (f, c) ->
            (if (presionadoSinEscala) fondoDelBotonPresionado(variante, Movi.colores) else f) to c
        }
    } else {
        coloresDelBotonApagado(variante, Movi.colores)
    }
    val forma = RoundedCornerShape(Radio.pill)
    val borde = if (habilitado) bordeDelBoton(variante, Movi.colores) else null
    Row(
        modifier = modifier
            .then(if (anchoCompleto) Modifier.fillMaxWidth() else Modifier)
            // Vuelta 88: alto **mínimo**, no fijo. Con la letra grande y un rótulo largo («Dar acceso a
            // las notificaciones» a 320 dp) el texto baja a un segundo renglón y el botón crece con él;
            // con alto fijo el segundo renglón quedaba afuera del botón.
            .heightIn(min = tamano.alto)
            .anilloDeFoco(forma)
            // Vuelta 79: se hunde al 0,97 sin ripple (ver [alPresionar]); el principal se queda en la marca.
            .alPresionar(
                onClick = onClick,
                habilitado = habilitado && !cargando,
                rol = Role.Button,
                escala = Movimiento.ESCALA_DEL_BOTON_AL_PRESIONAR,
                fuente = toque,
                velo = variante != VarianteDeBoton.Principal,
                forma = forma,
            )
            .clip(forma)
            .background(fondo)
            .then(if (borde != null) Modifier.border(Medida.trazoDeBoton, borde, forma) else Modifier)
            .then(if (cargando) Modifier.semantics { stateDescription = ROTULO_CARGANDO } else Modifier)
            .semantics {
                varianteDeBoton = variante
                if (variante == VarianteDeBoton.Principal && habilitado) llevaElAmarillo = true
            }
            .testTag(TAG_BOTON)
            .padding(horizontal = if (tamano == TamanoDeBoton.M) Espacio.l else Espacio.s, vertical = Espacio.xxs),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            cargando -> CircularProgressIndicator(
                modifier = Modifier.size(Icono.s),
                color = color,
                strokeWidth = Medida.trazoDeCarga,
            )
            icono != null -> Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(Icono.m))
        }
        // Vuelta 88: sin tope de renglones: si no cabe en uno, baja entero a dos (nunca se corta ni se
        // sale del botón). El texto va centrado por el `Row`; sin `TextAlign.Center` a propósito: un
        // párrafo centrado más angosto que su ancho de diseño se reportaba dibujado fuera de su caja, y
        // medirlo con `IntrinsicSize` dejaba la ruedita de «cargando» sin terminar nunca de medir.
        Text(
            text = texto,
            style = Movi.textos.cuerpoFuerte,
            color = color,
        )
    }
}

/** Tag del par de botones (para medirlo en una prueba). */
const val TAG_PAR_DE_BOTONES: String = "ds-par-de-botones"

/**
 * **Dos botones lado a lado, o uno encima del otro si no caben** (vuelta 88): el «Atrás» + «Siguiente»
 * del pie de la guía. Lado a lado, cada uno lleva la mitad del ancho ([secundario] a la izquierda,
 * [principal] a la derecha), los dos del mismo alto. Si alguno de los dos rótulos **no cabe en un
 * renglón** en su mitad —la letra grande del sistema a 320 o 360 dp: «Guardar y seguir» a ×1,5—, van
 * apilados a todo el ancho, **el principal arriba**. Así ningún rótulo se parte ni se sale del botón.
 *
 * Los dos contenidos son un [Boton] sin `weight` ni `anchoCompleto`: el ancho lo pone el par. Sin
 * [secundario], el principal va solo a todo el ancho.
 */
@Composable
fun ParDeBotones(
    principal: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    secundario: (@Composable () -> Unit)? = null,
) {
    Layout(
        content = {
            principal()
            secundario?.invoke()
        },
        modifier = modifier.fillMaxWidth().testTag(TAG_PAR_DE_BOTONES),
    ) { medibles, constraints ->
        val ancho = constraints.maxWidth
        val separacion = Espacio.xs.roundToPx()
        val p = medibles.firstOrNull() ?: return@Layout layout(ancho, 0) {}
        val s = medibles.getOrNull(1)
        if (s == null) {
            val pp = p.measure(Constraints(minWidth = ancho, maxWidth = ancho))
            return@Layout layout(ancho, pp.height) { pp.place(0, 0) }
        }
        val mitad = (ancho - separacion) / 2
        val caben = p.maxIntrinsicWidth(Constraints.Infinity) <= mitad && s.maxIntrinsicWidth(Constraints.Infinity) <= mitad
        if (caben) {
            val alto = maxOf(p.maxIntrinsicHeight(mitad), s.maxIntrinsicHeight(mitad))
            val pp = p.measure(Constraints.fixed(mitad, alto))
            val ps = s.measure(Constraints.fixed(mitad, alto))
            layout(ancho, alto) {
                ps.place(0, 0)
                pp.place(ancho - mitad, 0)
            }
        } else {
            val pp = p.measure(Constraints(minWidth = ancho, maxWidth = ancho))
            val ps = s.measure(Constraints(minWidth = ancho, maxWidth = ancho))
            layout(ancho, pp.height + separacion + ps.height) {
                pp.place(0, 0)
                ps.place(0, pp.height + separacion)
            }
        }
    }
}
