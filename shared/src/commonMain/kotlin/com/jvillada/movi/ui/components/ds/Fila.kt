package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.jvillada.movi.ui.components.partirLaColaDelNombreCorto
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.theme.Radio
import androidx.compose.foundation.shape.RoundedCornerShape

/** El ícono chico antes del subtítulo de una [Fila] (vuelta 83: el ↻ de un recurrente). */
const val TAG_ICONO_DEL_SUBTITULO: String = "ds-fila-icono-del-subtitulo"

/** Vuelta 87: la cabeza y la cola («·4057») de un subtítulo que termina en un nombre corto de cuenta. */
const val TAG_CABEZA_DEL_SUBTITULO: String = "ds-fila-cabeza-del-subtitulo"
const val TAG_COLA_DEL_SUBTITULO: String = "ds-fila-cola-del-subtitulo"

/** Cuántos renglones puede ocupar el título de una [Fila] antes de cortarse con «…». */
const val LINEAS_DEL_TITULO_DE_FILA: Int = 2

/**
 * **El tono del subtítulo de una [Fila]**: [Normal] (texto medio) casi siempre; [Aviso] cuando el
 * subtítulo pide algo al dueño («Faltan datos», en Créditos).
 */
enum class TonoDeSubtitulo { Normal, Aviso }

/** Entre el `inicio` de una [Fila] (el avatar) y su texto: [Espacio.s]. */
val ESPACIO_ANTES_DEL_TEXTO_DE_FILA = Espacio.s

/**
 * **Entre el texto de una [Fila] y su `final`: [Espacio.xs]** (vuelta de diseño 15). Era
 * [Espacio.s], como entre el avatar y el texto. A 390 dp «Bancolombia Ahorros» al lado de
 * «$13.845.800» se partía en dos renglones con la fila medio vacía: con la letra de la app (×1,12)
 * el título pide ~154 dp y le quedaban ~155 en Android, y en la web, un par de px menos. Los px no
 * estaban en el relleno de la fila (16, el margen de la pantalla, el mismo de toda lista), ni en el
 * avatar (40, el de toda fila) ni en la [Cifra] (que no se recorta): estaban en el aire
 * entre el título y el monto, que se lee igual de separado con 8. El avatar sigue a 12 del texto:
 * esa sangría la usan las líneas que cuelgan de una fila (`SANGRIA_DEL_TEXTO_DE_LA_FILA`).
 */
val ESPACIO_ANTES_DEL_FINAL_DE_FILA = Espacio.xs

/** Tag estable de una [Fila]. */
const val TAG_FILA: String = "ds-fila"

/**
 * **El tono del título de una [Fila].** Casi siempre [Normal]. Vuelta de diseño 5: «Borrar mi
 * cuenta» no podía ir en rojo porque la fila no dejaba cambiar el color del título, y la única salida
 * era un `Color` suelto en la pantalla.
 */
enum class TonoDeFila {
    /** El color del texto: toda fila. */
    Normal,

    /** `sale`: una acción que no tiene vuelta —borrar la cuenta—. Con su ícono en `Peligro`. */
    Alerta,

    /** `textoMedio`: una fila que solo informa y no pide nada («Antes de agosto no hay movimientos»). */
    Apagado,
}

/**
 * **Qué tan alta es una [Fila]** (vuelta de diseño 20).
 *
 * - [Normal]: [Medida.altoDeFila] (56), [Medida.altoDeFilaDoble] (58, vuelta 29) con subtítulo. Toda lista.
 * - [Compacta]: [Medida.objetivoTactil] (48) con o sin subtítulo —crece si el texto lo pide—. Solo
 *   para el formulario de una hoja anclada abajo donde cada dp de más mueve un teclado numérico bajo
 *   el dedo y empuja el botón de guardar fuera de la pantalla (las filas de Agregar). Sigue siendo la
 *   misma fila: los mismos márgenes, letras, ranuras y toque de borde a borde.
 */
enum class DensidadDeFila { Normal, Compacta }

/** El alto mínimo de una [Fila] con [densidad], con subtítulo o sin él. Pura. */
fun altoMinimoDeFila(densidad: DensidadDeFila, conSubtitulo: Boolean): Dp = when (densidad) {
    DensidadDeFila.Normal -> if (conSubtitulo) Medida.altoDeFilaDoble else Medida.altoDeFila
    DensidadDeFila.Compacta -> Medida.objetivoTactil
}

/** El color del título de una [Fila] con [tono], en la paleta del tema. Pura, para probarla en los dos. */
fun colorDelTituloDeFila(tono: TonoDeFila, colores: ColoresDeMovi): Color = when (tono) {
    TonoDeFila.Normal -> colores.texto
    TonoDeFila.Alerta -> colores.sale
    TonoDeFila.Apagado -> colores.textoMedio
}

/**
 * **La fila de lista. Una sola para toda la app.**
 *
 * La auditoría contó filas tocables con nueve rellenos verticales distintos (2, 5, 6, 8, 9, 10, 12,
 * 13 y 14 dp) y sin alto mínimo. Las de Movimientos, Plan, Patrimonio, Por revisar y Ajustes
 * convergen acá en las olas siguientes.
 *
 * - **Alto**: [Medida.altoDeFila] (56) con un renglón, [Medida.altoDeFilaDoble] (58, vuelta 29) con
 *   [subtitulo]. Es un mínimo: con la letra del sistema agrandada la fila crece en vez de cortar.
 * - **Relleno**: [Medida.margenDePantalla] a los lados, siempre. Por eso va en una [Superficie]
 *   con [RellenoDeSuperficie.Lista] o directo sobre el fondo, nunca dentro de otro relleno.
 * - **Toque**: la fila entera, de borde a borde.
 *
 * Las ranuras:
 * - [inicio]: un avatar de comercio (`IconoDeMovimiento`), un [IconoEnCirculo] o el avatar de una
 *   persona. Mide [Medida.avatar].
 * - [titulo]: hasta [LINEAS_DEL_TITULO_DE_FILA] renglones, y recién ahí «…»: un nombre largo
 *   («Bancolombia Ahorros» al lado de «$13.845.800» a 360 dp) baja a un segundo renglón en vez de
 *   cortarse. La fila crece; el mínimo de alto sigue siendo el de arriba.
 * - [subtitulo]: un renglón, con «…» si no entra. Donde el subtítulo es la razón de la fila y no
 *   se puede acortar («Bancolombia Ahorros · Nunca la has cuadrado», en Herramientas),
 *   [lineasDelSubtitulo] = 2: la fila crece en vez de cortar la frase (vuelta de diseño 2).
 * - [tonoDelSubtitulo]: [TonoDeSubtitulo.Aviso] pinta el subtítulo en el color de aviso, para el
 *   que pide algo (vuelta de diseño 4). El color nunca es la única pista: la frase lo dice.
 * - El [final] se mide primero y entero: un monto nunca se recorta; lo que cede es el texto.
 *   Va a [ESPACIO_ANTES_DEL_FINAL_DE_FILA] (8) del texto; el inicio, a 12 (vuelta de diseño 15).
 * - [final]: un monto ([Cifra]), una [Pildora] o un [Chevron].
 * - [tonoDelTitulo]: [TonoDeFila.Alerta] para lo que no tiene vuelta («Borrar mi cuenta»),
 *   [TonoDeFila.Apagado] para una fila que solo informa. Nunca un `Color` suelto.
 * - [densidad]: [DensidadDeFila.Compacta] (48) solo en el formulario de Agregar; ver su KDoc.
 *
 * ```
 * Fila(
 *     titulo = "Rappi · Las Doce",
 *     subtitulo = "Restaurantes · Nu",
 *     inicio = { IconoDeMovimiento(evento) },
 *     final = { Cifra(-27_500) },
 *     onClick = { abrir(evento) },
 * )
 * ```
 */
@Composable
fun Fila(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    inicio: (@Composable () -> Unit)? = null,
    final: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    lineasDelSubtitulo: Int = 1,
    tonoDelTitulo: TonoDeFila = TonoDeFila.Normal,
    tonoDelSubtitulo: TonoDeSubtitulo = TonoDeSubtitulo.Normal,
    densidad: DensidadDeFila = DensidadDeFila.Normal,
    /**
     * Vuelta 80: **toda fila tocable lleva el chevrón al final**. Sola, cuando tiene [onClick] y no
     * trae [final]; con un [final] (un monto que se abre), quien llama lo pide con `true` y va
     * después del monto. `false` lo quita (una fila que se elige, no que se abre).
     */
    chevron: Boolean = onClick != null && final == null,
    /** Vuelta 83: las filas de movimientos van en uno, con «…» (todas del mismo alto). */
    lineasDelTitulo: Int = LINEAS_DEL_TITULO_DE_FILA,
    /**
     * Vuelta 83: un ícono chico antes del subtítulo (el ↻ de un movimiento recurrente, que al final de la
     * fila le quitaba ancho al título y lo partía). Dice [descripcionDelIconoDelSubtitulo] al lector.
     */
    iconoDelSubtitulo: ImageVector? = null,
    descripcionDelIconoDelSubtitulo: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = altoMinimoDeFila(densidad, conSubtitulo = subtitulo != null))
            .then(
                if (onClick != null) {
                    // Hacia adentro: la superficie de la lista recorta lo que sale de la fila.
                    Modifier
                        .anilloDeFoco(RoundedCornerShape(Radio.m), adentro = true)
                        // Vuelta 79: sin ripple; se hunde apenas y lleva el velo (hover, o presionada con
                        // menos movimiento) a lo ancho de la fila, que la superficie recorta.
                        .alPresionar(
                            onClick = onClick,
                            onClickLabel = onClickLabel,
                            escala = Movimiento.ESCALA_DE_LA_FILA_AL_PRESIONAR,
                            velo = true,
                        )
                } else {
                    Modifier
                },
            )
            .testTag(TAG_FILA)
            // Vuelta 29: 4 arriba y abajo (era 8). La fila de la opción 3 mide 58 con el texto
            // centrado; con 8, el título a 15 y el subtítulo a 12,5 la empujaban por encima del alto.
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inicio != null) {
            Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) { inicio() }
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_TEXTO_DE_FILA))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = Movi.textos.cuerpo,
                color = colorDelTituloDeFila(tonoDelTitulo, Movi.colores),
                maxLines = lineasDelTitulo,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitulo != null) {
                val colorDelSubtitulo = when (tonoDelSubtitulo) {
                    TonoDeSubtitulo.Normal -> Movi.colores.textoMedio
                    TonoDeSubtitulo.Aviso -> Movi.colores.aviso
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconoDelSubtitulo != null) {
                        Icon(
                            imageVector = iconoDelSubtitulo,
                            contentDescription = descripcionDelIconoDelSubtitulo,
                            tint = colorDelSubtitulo,
                            modifier = Modifier.size(Icono.s).testTag(TAG_ICONO_DEL_SUBTITULO),
                        )
                        Spacer(Modifier.width(Espacio.xxs))
                    }
                    // Vuelta 87: un subtítulo que termina en «·4057» recorta la cabeza, nunca esa cola.
                    val partido = if (lineasDelSubtitulo == 1) partirLaColaDelNombreCorto(subtitulo) else null
                    if (partido == null) {
                        Text(
                            text = subtitulo,
                            style = Movi.textos.apoyo,
                            color = colorDelSubtitulo,
                            maxLines = lineasDelSubtitulo,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // El lector (y una prueba) ve el subtítulo entero, no dos pedazos.
                        Row(modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(subtitulo) }) {
                            Text(
                                text = partido.cabeza,
                                style = Movi.textos.apoyo,
                                color = colorDelSubtitulo,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).testTag(TAG_CABEZA_DEL_SUBTITULO),
                            )
                            Text(
                                text = " ${partido.cola}",
                                style = Movi.textos.apoyo,
                                color = colorDelSubtitulo,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.testTag(TAG_COLA_DEL_SUBTITULO),
                            )
                        }
                    }
                }
            }
        }
        if (final != null) {
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_FINAL_DE_FILA))
            final()
        }
        if (chevron) {
            Spacer(Modifier.width(if (final != null) Espacio.xxs else ESPACIO_ANTES_DEL_FINAL_DE_FILA))
            Chevron()
        }
    }
}

/**
 * **El tono de un [IconoEnCirculo] o de un [AvatarDePersona].**
 *
 * - [Neutro] (el de siempre, vuelta 42): el círculo en `capa3` con el ícono en la tinta. Es el de
 *   **toda fila que lleva a otro lado** ([FilaDeNavegacion]) y el de Ajustes.
 * - [Propio]: el tinte (el amarillo **suave**) con la letra en el ocre del aviso. Lo propio: «Tú»
 *   entre los miembros de un grupo, que se distingue de Caro sin pesar (en la tinta llena, cuatro
 *   gastos tuyos seguidos eran cuatro círculos negros).
 * - [Aviso]: el tinte con el ocre. Una fila que avisa algo; el amarillo suave es solo para eso.
 * - [Info], [Peligro], [Exito]: un estado, sobre el lavado de su color.
 * - [Marca]: el círculo **lleno de amarillo**. Solo el ícono de una propuesta de Movi
 *   ([TarjetaDePropuesta], la tarjeta de los avisos del banco en Actividad): el amarillo lleno es de la
 *   acción, nunca de una fila, una persona ni un estado (`ElAmarilloEsDeLaAccionTest`).
 */
enum class TonoDeIcono { Marca, Neutro, Propio, Info, Aviso, Peligro, Exito }

/**
 * El color del ícono (o la inicial) de un tono, en la paleta del tema. Pura. La marca (vuelta 29) es
 * la tinta: va sobre el círculo amarillo ([fondoDelTonoDeIcono]), porque el amarillo no se lee
 * como trazo sobre el blanco. El neutro, la tinta sobre la capa 3 (vuelta 42; era el texto medio
 * sobre su lavado); lo propio, el ocre del aviso sobre el tinte.
 */
fun colorDelTonoDeIcono(tono: TonoDeIcono, colores: ColoresDeMovi): Color = when (tono) {
    TonoDeIcono.Marca -> colores.sobreMarca
    TonoDeIcono.Neutro -> colores.texto
    TonoDeIcono.Propio -> colores.aviso
    TonoDeIcono.Info -> colores.entreCuentas
    TonoDeIcono.Aviso -> colores.aviso
    TonoDeIcono.Peligro -> colores.sale
    TonoDeIcono.Exito -> colores.entra
}

/**
 * El círculo de detrás de un tono. Pura. La marca va **llena de amarillo** (el ícono de una propuesta
 * de la opción 3); el neutro, en la capa 3; lo propio y el aviso, en el tinte (el amarillo suave);
 * lo demás, el lavado de su color.
 */
fun fondoDelTonoDeIcono(tono: TonoDeIcono, colores: ColoresDeMovi): Color = when (tono) {
    TonoDeIcono.Marca -> colores.marca
    TonoDeIcono.Neutro -> colores.capa3
    TonoDeIcono.Propio -> colores.tinte
    else -> colores.lavado(colorDelTonoDeIcono(tono, colores))
}

/**
 * **El tono de un [IconoEnCirculo] o un [AvatarDePersona], en su semántica** (vuelta 42), como
 * `VarianteDeCifraKey`: deja vigilar que una fila de navegación vaya en el círculo neutro y que
 * ninguna persona vaya en amarillo, sin leer píxeles.
 */
val TonoDeIconoKey: SemanticsPropertyKey<TonoDeIcono> = SemanticsPropertyKey("TonoDeIcono")

/** Ver [TonoDeIconoKey]. */
var SemanticsPropertyReceiver.tonoDeIcono: TonoDeIcono by TonoDeIconoKey

/** Tag estable de un [IconoEnCirculo] (vuelta 31: para probar que cada fila de Ajustes lleva el suyo). */
const val TAG_ICONO_EN_CIRCULO: String = "ds-icono-en-circulo"

/**
 * **Un ícono en un círculo lavado de su color**: el inicio de una [Fila] que no tiene comercio, y
 * el de una [TarjetaDePropuesta]. Mide [Medida.avatar], con el ícono a [Icono.m]. **Neutro por
 * defecto** (vuelta 42; era la marca, y cada fila que no decía su tono salía con un círculo amarillo).
 */
@Composable
fun IconoEnCirculo(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    tono: TonoDeIcono = TonoDeIcono.Neutro,
) {
    val color = colorDelTonoDeIcono(tono, Movi.colores)
    Box(
        modifier = modifier
            .testTag(TAG_ICONO_EN_CIRCULO)
            .semantics { tonoDeIcono = tono }
            .size(Medida.avatar)
            .background(fondoDelTonoDeIcono(tono, Movi.colores), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(Icono.m))
    }
}

/** El `testTag` de un [Chevron] (vuelta 80: para vigilar que toda fila tocable lo lleve). */
const val TAG_CHEVRON: String = "ds:chevron"

/**
 * **La flecha de «esto se abre»**, al final de una [Fila]. [Icono.m], en texto apagado. Con [abierto]
 * (lo que abre ya está a la vista: «Tu salario» con su campo) mira hacia abajo (vuelta 87).
 */
@Composable
fun Chevron(modifier: Modifier = Modifier, abierto: Boolean = false) {
    Icon(
        if (abierto) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = Movi.colores.textoApagado,
        modifier = modifier.size(Icono.m).testTag(TAG_CHEVRON),
    )
}

/**
 * **Una fila que lleva a otra pantalla** (vuelta 42): una [Fila] sola en su [Superficie] de lista,
 * con su ícono en el **círculo neutro** (`capa3` y el ícono en la tinta), el título, una línea de apoyo
 * y la flecha. «Cómo salir de tus deudas» en Créditos, «Tus períodos» en Pagos, «Subir comprobante o
 * extracto» en Por revisar, «Personas y comercios» en Grupos, «Para qué es esta plata» en una cuenta.
 *
 * Antes cada pantalla la armaba a mano y el ícono salía lleno de amarillo en unas (el tono por
 * defecto de [IconoEnCirculo] era la marca) y en gris en Tú: el mismo gesto con dos caras. Ahora es
 * una sola. Con [aviso] (algo que pide atención: «La captura dejó de andar») el ícono y el subtítulo
 * van en el tono de aviso, el tinte con el ocre: **el amarillo suave es solo para eso**, y el lleno,
 * para la acción principal.
 *
 * ```
 * FilaDeNavegacion("Tus períodos", Icons.Rounded.History, onClick = ::abrir, subtitulo = "Cómo te fue en cada ciclo")
 * ```
 *
 * @param modifier va en la superficie (el ancho, el espacio de abajo, el `testTag` de la pantalla).
 * @param lineasDelSubtitulo como en [Fila]: 2 solo si el subtítulo es la razón de la fila.
 */
@Composable
fun FilaDeNavegacion(
    titulo: String,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    aviso: Boolean = false,
    lineasDelSubtitulo: Int = 1,
) {
    Superficie(modifier = modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = titulo,
            subtitulo = subtitulo,
            // Sin un tag propio: la fila lleva el de toda [Fila] (`TAG_FILA`), que cuentan las guardas.
            inicio = { IconoEnCirculo(icono, tono = tonoDeLaFilaDeNavegacion(aviso)) },
            final = { Chevron() },
            onClick = onClick,
            lineasDelSubtitulo = lineasDelSubtitulo,
            tonoDelSubtitulo = if (aviso) TonoDeSubtitulo.Aviso else TonoDeSubtitulo.Normal,
        )
    }
}

/** El tono del ícono de una [FilaDeNavegacion]: el neutro, o el de aviso si la fila avisa algo. Pura. */
fun tonoDeLaFilaDeNavegacion(aviso: Boolean): TonoDeIcono = if (aviso) TonoDeIcono.Aviso else TonoDeIcono.Neutro
