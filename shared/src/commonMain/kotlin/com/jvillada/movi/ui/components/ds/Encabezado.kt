package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.TextosDeMovi

/** Tag estable del [Encabezado] de una pantalla. */
const val TAG_ENCABEZADO: String = "ds-encabezado"

/** Lo que dice el lector de pantalla de la flecha de volver. */
const val ROTULO_VOLVER: String = "Volver"

/** Tag estable de un [BotonDeIcono]. */
const val TAG_BOTON_DE_ICONO: String = "ds-boton-de-icono"

/**
 * **Cómo se ve un [BotonDeIcono]** (vuelta de diseño 20).
 *
 * - [Plano]: solo el ícono, en un círculo de toque. El de un encabezado (la flecha, la lupa).
 * - [Secundario]: el ícono en un **círculo blanco con borde** de [Medida.objetivoTactil] (vuelta 29:
 *   el botón redondo de la lupa en la opción 3): es el [VarianteDeBoton.Secundario] sin texto, para ir
 *   al lado de un [Boton] de alto `M` («Pagó otro» y «Subir comprobante» junto a «Guardar movimiento»
 *   en Agregar).
 */
enum class VarianteDeBotonDeIcono { Plano, Secundario }

/**
 * **Un ícono que se toca**: [Icono.l] en un área de [Medida.objetivoTactil] (48 × 48). La flecha de
 * volver, la lupa, la campana de un encabezado; con [VarianteDeBotonDeIcono.Secundario], una acción
 * al lado de un botón. [descripcion] es lo que dice el lector de pantalla: un ícono solo nunca va sin
 * ella. Apagado ([habilitado] en `false`), el ícono va en texto apagado y no se toca.
 */
@Composable
fun BotonDeIcono(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variante: VarianteDeBotonDeIcono = VarianteDeBotonDeIcono.Plano,
    habilitado: Boolean = true,
) {
    val forma = CircleShape
    Box(
        modifier = modifier
            .size(Medida.objetivoTactil)
            .clip(forma)
            .then(
                if (variante == VarianteDeBotonDeIcono.Secundario) {
                    Modifier.background(Movi.colores.tarjeta).border(Medida.trazo, Movi.colores.borde, forma)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = habilitado, role = Role.Button, onClickLabel = descripcion, onClick = onClick)
            .testTag(TAG_BOTON_DE_ICONO),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icono,
            contentDescription = descripcion,
            tint = if (habilitado) Movi.colores.texto else Movi.colores.textoApagado,
            modifier = Modifier.size(Icono.l),
        )
    }
}

/** Tag estable de la [AccionDelEncabezado]: la mide la prueba que compara pantallas. */
const val TAG_ACCION_DEL_ENCABEZADO: String = "ds-accion-del-encabezado"

/**
 * **La acción principal de un encabezado**: lo que se crea o se hace desde esa pantalla («Nueva
 * cuenta», «Nuevo crédito», «Subir archivo»). Viaja como dato para que [Encabezado] la pinte siempre
 * igual —texto con ícono, en el azul del enlace, [AccionDelEncabezado]— y ninguna pantalla elija otro botón
 * (vuelta de diseño 4: Patrimonio la tenía como texto morado y Créditos como botón tonal relleno).
 */
data class AccionDeEncabezado(
    val texto: String,
    val icono: ImageVector,
    val onClick: () -> Unit,
    val habilitado: Boolean = true,
)

/**
 * **La forma de la [AccionDeEncabezado]**: un [Boton] [VarianteDeBoton.Fantasma] tamaño
 * [TamanoDeBoton.S] con su ícono (texto e ícono en el azul del enlace, sin relleno; el toque llega a 48).
 * Solo la pinta [Encabezado].
 */
@Composable
internal fun AccionDelEncabezado(accion: AccionDeEncabezado, modifier: Modifier = Modifier) {
    Boton(
        texto = accion.texto,
        onClick = accion.onClick,
        modifier = modifier.testTag(TAG_ACCION_DEL_ENCABEZADO),
        variante = VarianteDeBoton.Fantasma,
        tamano = TamanoDeBoton.S,
        icono = accion.icono,
        habilitado = accion.habilitado,
    )
}

/**
 * **La [AccionDeEncabezado] solo con su ícono**: en una pestaña siempre (vuelta 29), y en una pantalla
 * con flecha cuando el título no deja lugar para su texto (vuelta de diseño 8). Es el botón redondo de
 * la opción 3 —blanco con borde y el ícono en tinta, como la lupa de Actividad—, en un área de
 * [Medida.objetivoTactil]. El texto no se pierde: es lo que dice el lector de pantalla. Lleva el mismo
 * tag que [AccionDelEncabezado].
 */
@Composable
internal fun AccionDelEncabezadoSoloIcono(accion: AccionDeEncabezado, modifier: Modifier = Modifier) {
    val color = if (accion.habilitado) Movi.colores.texto else Movi.colores.textoApagado
    Box(
        modifier = modifier
            .size(Medida.objetivoTactil)
            .clip(CircleShape)
            .background(Movi.colores.tarjeta)
            .border(Medida.trazo, Movi.colores.borde, CircleShape)
            .clickable(
                enabled = accion.habilitado,
                role = Role.Button,
                onClickLabel = accion.texto,
                onClick = accion.onClick,
            )
            .testTag(TAG_ACCION_DEL_ENCABEZADO),
        contentAlignment = Alignment.Center,
    ) {
        Icon(accion.icono, contentDescription = accion.texto, tint = color, modifier = Modifier.size(Icono.l))
    }
}

/**
 * **Cuánto mide la [AccionDelEncabezado] con su texto**, sin componerla: el relleno de un [Boton]
 * [TamanoDeBoton.S] a los dos lados, el ícono, el espacio hasta el texto y el texto en la letra del
 * botón. [Encabezado] decide con esto si la acción va con texto o solo con el ícono; componer las dos
 * formas para medirlas dejaría la que no se usa en el árbol del lector de pantalla.
 * `LaAccionDelEncabezadoCedeAlTituloTest` vigila que coincida con lo que mide el botón de verdad.
 */
internal fun anchoDeLaAccionConTexto(medidor: TextMeasurer, texto: String, estilo: TextStyle, densidad: Density): Int =
    with(densidad) {
        val anchoDelTexto = medidor.measure(texto, estilo, maxLines = 1, softWrap = false).size.width
        (Espacio.s * 2 + Icono.m + Espacio.xs).roundToPx() + anchoDelTexto
    }

/**
 * ¿Título, subtítulo y acción con texto caben en [disponible]? Pura: la regla de quién cede.
 *
 * **La prioridad es título > subtítulo > texto de la acción** (vuelta de diseño 8 para el título, 12
 * para el subtítulo): la acción conserva su texto solo si el título **y** el subtítulo caben enteros
 * a su lado. Movi AI decía «Conoce tus fi…» porque «Nueva conversación» se medía solo contra «Movi AI».
 */
fun laAccionVaConTexto(
    anchoDelTitulo: Int,
    anchoDeLaAccion: Int,
    separacion: Int,
    disponible: Int,
    anchoDelSubtitulo: Int = 0,
): Boolean = maxOf(anchoDelTitulo, anchoDelSubtitulo) + separacion + anchoDeLaAccion <= disponible

/**
 * **El encabezado de una pantalla. El mismo alto y los mismos márgenes en todas.**
 *
 * - Alto [Medida.altoDeEncabezado] (56), como mínimo: con la letra del sistema agrandada crece en
 *   vez de cortar.
 * - Margen [Medida.margenDePantalla] a los dos lados. Con [onVolver], la flecha va en un área de
 *   toque de 48 y el margen se corre lo justo para que **el dibujo** de la flecha quede en el
 *   margen, alineado con todo lo de abajo.
 * - El título según [TamanoDelTitulo] (vuelta 29): `titular` (28 a la vista) en una pestaña —con el
 *   avatar—, `titularDePantalla` (22) en una pantalla con flecha y `saludo` (17/700) en Hoy. **Nunca se corta** (vuelta de diseño 8: «Personas y comerc…»): si no
 *   cabe al lado de la acción con texto, la acción pasa a solo ícono ([AccionDelEncabezadoSoloIcono],
 *   con su texto para el lector de pantalla y el mismo tag); si ni así cabe, baja a dos renglones.
 *   El subtítulo opcional, en `apoyo`, tampoco se corta (vuelta de diseño 12): también hace ceder
 *   el texto de la acción, y si ni así cabe baja a dos renglones. Título > subtítulo > acción.
 * - [accion] a la derecha, al final: **la** acción principal, siempre con la misma forma
 *   ([AccionDelEncabezado]: texto con ícono, o solo el ícono cuando el título lo pide). Una pantalla
 *   con dos o más acciones pone las demás en [acciones], como [BotonDeIcono] con su descripción.
 * - [acciones] antes de [accion]: [BotonDeIcono]s, una [Pildora] («Beta») o «Actualizando…». Nunca
 *   un [Boton] con texto: para eso está [accion].
 * - Sin línea debajo: superficies por capas, sin bordes duros (camino 1).
 *
 * `MinScreenHeader` —el encabezado de todas las pantallas de hoy— delega acá.
 *
 * ```
 * Encabezado("Movimientos", acciones = { BotonDeIcono(Icons.Rounded.Search, "Buscar", ::buscar) })
 * Encabezado("Detalle", onVolver = { volver() })
 * Encabezado("Patrimonio", accion = AccionDeEncabezado("Nueva cuenta", Icons.Rounded.Add, ::crear))
 * ```
 *
 * @param inicio lo que va a la izquierda cuando no es la flecha: el avatar de una pantalla raíz.
 *   Si llegan los dos, gana [onVolver].
 */
@Composable
fun Encabezado(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    onVolver: (() -> Unit)? = null,
    inicio: (@Composable () -> Unit)? = null,
    acciones: (@Composable RowScope.() -> Unit)? = null,
    accion: AccionDeEncabezado? = null,
    tamanoDelTitulo: TamanoDelTitulo = if (onVolver != null) TamanoDelTitulo.Pantalla else TamanoDelTitulo.Pestana,
) {
    // La flecha dibuja 24 en un área de 48: el margen se achica en la mitad de la diferencia para
    // que el dibujo, no el área, quede alineado con el resto de la pantalla.
    val margenDeInicio = if (onVolver != null) {
        Medida.margenDePantalla - (Medida.objetivoTactil - Icono.l) / 2
    } else {
        Medida.margenDePantalla
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeEncabezado)
            .testTag(TAG_ENCABEZADO)
            .padding(start = margenDeInicio, end = Medida.margenDePantalla),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        when {
            onVolver != null -> BotonDeIcono(Icons.AutoMirrored.Rounded.ArrowBack, ROTULO_VOLVER, onVolver)
            inicio != null -> inicio()
        }
        TituloYAcciones(
            titulo = titulo,
            estiloDelTitulo = estiloDelTitulo(tamanoDelTitulo, Movi.textos),
            // Vuelta 29: en una pestaña la acción va siempre en su botón redondo («+» en Patrimonio
            // y en Espacios por igual); con el título de 28, que cupiera o no dependía del largo del
            // nombre de la pestaña, y dos pestañas vecinas se veían distintas.
            accionSiempreSoloIcono = tamanoDelTitulo != TamanoDelTitulo.Pantalla,
            subtitulo = subtitulo,
            acciones = acciones,
            accion = accion,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * **El tamaño del título de un [Encabezado]** (vuelta 29). El de 28 de la opción 3 es solo para el
 * título de una pestaña; una pantalla con flecha lleva uno más chico, y Hoy no lleva título sino el
 * saludo, que es un nombre.
 */
enum class TamanoDelTitulo {
    /** Una pestaña (el encabezado con el avatar): `titular`. */
    Pestana,

    /** Una pantalla con flecha: `titularDePantalla`. */
    Pantalla,

    /** «Hola, Juan» con la fecha debajo: `saludo`. */
    Saludo,
}

/** El estilo de un [TamanoDelTitulo], en la escala de textos. Pura. */
fun estiloDelTitulo(tamano: TamanoDelTitulo, textos: TextosDeMovi): TextStyle = when (tamano) {
    TamanoDelTitulo.Pestana -> textos.titular
    TamanoDelTitulo.Pantalla -> textos.titularDePantalla
    TamanoDelTitulo.Saludo -> textos.saludo
}

/** Las piezas de [TituloYAcciones], por nombre: cada una se compone una sola vez por medida. */
private enum class PiezaDelEncabezado { Titulo, Subtitulo, Acciones, AccionConTexto, AccionSoloIcono }

/**
 * **El título y lo que va a su derecha, con el título primero.** Se mide así:
 * 1. [acciones] (íconos, una píldora): ocupan lo suyo, siempre.
 * 2. La [accion], con texto si el título y el subtítulo enteros caben al lado ([laAccionVaConTexto]);
 *    si no, solo el ícono. Su ancho con texto se calcula sin componerla ([anchoDeLaAccionConTexto]).
 * 3. El título y el subtítulo con lo que queda, en hasta dos renglones cada uno.
 */
@Composable
private fun TituloYAcciones(
    titulo: String,
    estiloDelTitulo: TextStyle,
    accionSiempreSoloIcono: Boolean,
    subtitulo: String?,
    acciones: (@Composable RowScope.() -> Unit)?,
    accion: AccionDeEncabezado?,
    modifier: Modifier = Modifier,
) {
    val medidor = rememberTextMeasurer()
    val estiloDelBoton = Movi.textos.cuerpoFuerte
    val colorDelTitulo = Movi.colores.texto
    val estiloDelSubtitulo = Movi.textos.apoyo
    val colorDelSubtitulo = Movi.colores.textoMedio
    SubcomposeLayout(modifier) { constraints ->
        val separacion = Espacio.s.roundToPx()
        val disponible = constraints.maxWidth
        val libre = constraints.copy(minWidth = 0, minHeight = 0)

        val accionesMedidas = acciones?.let { contenido ->
            subcompose(PiezaDelEncabezado.Acciones) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                    verticalAlignment = Alignment.CenterVertically,
                    content = contenido,
                )
            }.map { it.measure(libre) }
        }.orEmpty()
        val anchoDeAcciones = accionesMedidas.sumOf { it.width } +
            if (accionesMedidas.isEmpty()) 0 else separacion

        val tituloMedible = subcompose(PiezaDelEncabezado.Titulo) {
            Text(
                text = titulo,
                style = estiloDelTitulo,
                color = colorDelTitulo,
                maxLines = RENGLONES_DEL_TITULO_DEL_ENCABEZADO,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }.single()
        val anchoDelTitulo = tituloMedible.maxIntrinsicWidth(constraints.maxHeight)
        // El subtítulo se compone una vez, antes de decidir la acción: también manda sobre ella.
        val subtituloMedible = subtitulo?.let { sub ->
            subcompose(PiezaDelEncabezado.Subtitulo) {
                Text(
                    text = sub,
                    style = estiloDelSubtitulo,
                    color = colorDelSubtitulo,
                    maxLines = RENGLONES_DEL_SUBTITULO_DEL_ENCABEZADO,
                    overflow = TextOverflow.Ellipsis,
                )
            }.single()
        }
        val anchoDelSubtitulo = subtituloMedible?.maxIntrinsicWidth(constraints.maxHeight) ?: 0

        val accionMedida = accion?.let { a ->
            val conTexto = !accionSiempreSoloIcono && laAccionVaConTexto(
                anchoDelTitulo = anchoDelTitulo,
                anchoDeLaAccion = anchoDeLaAccionConTexto(medidor, a.texto, estiloDelBoton, this),
                separacion = separacion,
                disponible = disponible - anchoDeAcciones,
                anchoDelSubtitulo = anchoDelSubtitulo,
            )
            val pieza = if (conTexto) PiezaDelEncabezado.AccionConTexto else PiezaDelEncabezado.AccionSoloIcono
            subcompose(pieza) {
                if (conTexto) AccionDelEncabezado(a) else AccionDelEncabezadoSoloIcono(a)
            }.single().measure(libre)
        }
        val anchoDeLaAccion = accionMedida?.let { it.width + separacion } ?: 0

        val anchoDelTexto = (disponible - anchoDeAcciones - anchoDeLaAccion).coerceAtLeast(0)
        val restriccionDelTexto = Constraints(maxWidth = anchoDelTexto)
        val tituloMedido = tituloMedible.measure(restriccionDelTexto)
        val subtituloMedido = subtituloMedible?.measure(restriccionDelTexto)

        val altoDelTexto = tituloMedido.height + (subtituloMedido?.height ?: 0)
        val alto = maxOf(
            altoDelTexto,
            accionesMedidas.maxOfOrNull { it.height } ?: 0,
            accionMedida?.height ?: 0,
            constraints.minHeight,
        )
        layout(disponible, alto) {
            val arribaDelTexto = (alto - altoDelTexto) / 2
            tituloMedido.placeRelative(0, arribaDelTexto)
            subtituloMedido?.placeRelative(0, arribaDelTexto + tituloMedido.height)
            var x = anchoDelTexto
            accionesMedidas.forEach { pieza ->
                x += separacion
                pieza.placeRelative(x, (alto - pieza.height) / 2)
                x += pieza.width
            }
            accionMedida?.let { pieza ->
                pieza.placeRelative(disponible - pieza.width, (alto - pieza.height) / 2)
            }
        }
    }
}

/** El título de un encabezado baja a dos renglones antes de cortarse (vuelta de diseño 8). */
const val RENGLONES_DEL_TITULO_DEL_ENCABEZADO: Int = 2

/**
 * El subtítulo también (vuelta de diseño 12): si ni con la acción en su ícono cabe, baja a un segundo
 * renglón antes de cortarse.
 */
const val RENGLONES_DEL_SUBTITULO_DEL_ENCABEZADO: Int = 2

/** El `testTag` de [EncabezadoDeHoja]. */
const val TAG_ENCABEZADO_DE_HOJA: String = "ds-encabezado-de-hoja"

/**
 * **El encabezado de una hoja** (vuelta de diseño 17): el nombre de lo que se edita en `titulo`
 * medio y, debajo, qué es («Tarjeta», «Préstamo») o una frase corta, en `cuerpo` y `textoMedio`.
 * Va primero en el contenido, debajo del renglón de la X que pone `MarcoDeHoja`, y declara el título
 * como encabezado para el lector de pantalla.
 *
 * Es el patrón que las hojas con título ya escribían a mano (Apartado, Bien, «¿Con quién
 * compartes?»). La hoja de la tarjeta decía «La tarjeta» como título de grupo y «Nu» suelto debajo:
 * el nombre parecía un dato más y el título, una sección.
 *
 * ```
 * EncabezadoDeHoja("Nu", subtitulo = "Tarjeta")
 * Spacer(Modifier.height(Medida.entreSecciones))
 * ```
 */
@Composable
fun EncabezadoDeHoja(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_ENCABEZADO_DE_HOJA)) {
        Text(
            titulo,
            style = Movi.textos.tituloMedio,
            color = Movi.colores.texto,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitulo != null) {
            Spacer(Modifier.height(Espacio.xxs))
            Text(subtitulo, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        }
    }
}
