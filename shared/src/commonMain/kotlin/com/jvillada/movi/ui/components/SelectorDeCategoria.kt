package com.jvillada.movi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.MAX_CATEGORIA_LENGTH
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.categorias.aparienciaDe
import com.jvillada.movi.ui.categorias.prefDeCategoria

/**
 * Ola B · Task 4: cuántas frecuentes encabezan la cuadrícula — dos filas de cuatro en un teléfono.
 * Más que los 6 chips de «Agregar» porque acá no compiten por lugar con el teclado numérico.
 */
const val FRECUENTES_EN_EL_SELECTOR: Int = 8

/**
 * El ancho mínimo de una celda: lo que pide un nombre de dos líneas debajo del ícono para no
 * cortarse en cada palabra. Con el margen de las hojas, un teléfono de 375 dp da 4 columnas.
 */
val ANCHO_MINIMO_DE_CELDA: Dp = 80.dp

/** El campo de búsqueda del selector — para que una prueba mire si tiene el foco. */
const val TAG_BUSCAR_CATEGORIA: String = "categoria:buscar"

/** La celda «Crear "…"». */
const val TAG_CREAR_CATEGORIA: String = "categoria:crear"

/** La celda «Usar "…"». */
const val TAG_USAR_CATEGORIA: String = "categoria:usar"

/** El `testTag` de la celda de una categoría que ya existe. */
fun tagDeCeldaDeCategoria(nombre: String): String = "categoria:celda:$nombre"

/**
 * Una celda de la cuadrícula. Tres clases y no un `String` porque las dos celdas especiales se
 * dibujan distinto y, sobre todo, **eligen algo distinto de lo que dicen**: «Crear "carro"» elige
 * «carro»; «Usar "Carro"» elige cómo lo tiene escrito Movi, no lo tecleado.
 */
sealed interface CeldaDeCategoria {
    /** Lo que se elige al tocarla. */
    val nombre: String

    /** Una categoría que ya existe y que se ofrece para este tipo. */
    data class Existente(override val nombre: String) : CeldaDeCategoria

    /** Ola 9 · A1: lo escrito no existe — crearla. Ver [shouldOfferCreateCategory]. */
    data class Crear(override val nombre: String) : CeldaDeCategoria

    /**
     * Ola 9 · A4: lo escrito existe pero no se ofrece (es del otro lado, o está escondida) —
     * usarla igual. [porQue] es la línea que explica por qué no estaba en la cuadrícula; sin ella
     * la celda se lee como un duplicado.
     */
    data class Usar(override val nombre: String, val porQue: String) : CeldaDeCategoria
}

/**
 * Lo que el selector dibuja para una búsqueda: las celdas en orden y, si lo escrito es una
 * reservada, su nombre (para el aviso — ahí no se ofrece ninguna celda especial).
 */
data class ContenidoDelSelectorDeCategoria(
    val celdas: List<CeldaDeCategoria>,
    val reservadaEscrita: String? = null,
)

/**
 * **Qué celdas muestra el selector y en qué orden.** Pura, para probarla sin Compose.
 *
 * - Primero, hasta [FRECUENTES_EN_EL_SELECTOR] **frecuentes** del tipo ([categoriasFrecuentes] —
 *   el mismo criterio que los chips de «Agregar»). Son las que el dueño toca casi siempre, y con
 *   ellas arriba la mayoría de las veces no hay que buscar nada.
 * - Después **el resto**, sin repetir, en el orden de [suggestCategoryMatches]: alfabético con el
 *   campo vacío; con algo escrito, lo que empieza con eso antes de lo que apenas lo contiene.
 *
 * El filtro es el de siempre y vive en un solo lugar: lo que no pasa [suggestCategoryMatches]
 * (reservadas, escondidas, del otro tipo — ver [seOfreceParaTipo]) no aparece, **tampoco como
 * frecuente**: una frecuente solo entra si también está entre las que coinciden. Con algo escrito,
 * las frecuentes que no coinciden se van como cualquier otra.
 *
 * Con algo escrito que no existe, la primera celda es «Crear "…"» ([shouldOfferCreateCategory]);
 * si existe pero no se ofrece, «Usar "…"» ([shouldOfferKnownFromOtherSide]). Nunca las dos. Una
 * reservada escrita no ofrece ninguna de las dos: se explica y se corta, como hacía el panel viejo
 * — elegirla saca el gasto de las cifras del mes.
 *
 * **Lo que ya no está, y por qué:** `categoriasParaElPanel` («si lo escrito es un nombre conocido,
 * mostrar todas») existía porque el campo viejo arrancaba PRELLENADO con la categoría actual, y
 * filtrar por ella dejaba el panel en una sola fila. La búsqueda de ahora arranca vacía —la
 * categoría actual se marca en la cuadrícula en vez de escribirse en el campo—, así que lo escrito
 * es siempre algo que el dueño tecleó para buscar, y filtrar por eso es lo correcto.
 */
fun contenidoDelSelectorDeCategoria(
    busqueda: String,
    tipo: TransactionType?,
    usadas: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
    usos: Map<String, Int> = emptyMap(),
): ContenidoDelSelectorDeCategoria {
    val escrito = busqueda.trim()
    val coinciden = suggestCategoryMatches(escrito, tipo, usadas, prefs)
    val frecuentes = if (tipo == null) {
        emptyList()
    } else {
        categoriasFrecuentes(tipo, usadas, prefs, usos, FRECUENTES_EN_EL_SELECTOR)
            // Con la grafía con la que la ofrece la lista: el caché y el catálogo pueden diferir en
            // una mayúscula («comida» usada, «Comida» del catálogo) y la lista ya eligió una.
            .mapNotNull { frecuente ->
                coinciden.firstOrNull { it == frecuente }
                    ?: coinciden.firstOrNull { normalizarParaBuscar(it) == normalizarParaBuscar(frecuente) }
            }
            .distinct()
    }
    val existentes = (frecuentes + coinciden.filterNot { it in frecuentes })
        .map { CeldaDeCategoria.Existente(it) }

    if (escrito.isNotEmpty() && isReservedCategory(escrito)) {
        return ContenidoDelSelectorDeCategoria(existentes, reservadaEscrita = escrito)
    }
    // Las propias de cualquier tipo Y el catálogo entero Y lo que tiene preferencias (una
    // escondida sigue siendo conocida): lo que ya existe no se «crea».
    val conocidas = usadas.keys + PREDEFINED_CATEGORIES.map { it.name } + prefs.keys
    val especial: CeldaDeCategoria? = when {
        shouldOfferCreateCategory(escrito, coinciden, conocidas) -> CeldaDeCategoria.Crear(escrito)
        shouldOfferKnownFromOtherSide(escrito, coinciden, conocidas) -> {
            val escondida = prefs.entries
                .firstOrNull { normalizarParaBuscar(it.key.trim()) == normalizarParaBuscar(escrito) }
                ?.value?.hidden == true
            CeldaDeCategoria.Usar(
                nombre = nombreCanonicoConocido(escrito, usadas, prefs) ?: escrito,
                porQue = if (escondida) {
                    "La escondiste en Categorías; puedes usarla igual"
                } else {
                    ladoConocidoDeCategoria(escrito, tipo, usadas, prefs) ?: "Ya la tienes anotada"
                },
            )
        }
        else -> null
    }
    return ContenidoDelSelectorDeCategoria(listOfNotNull(especial) + existentes)
}

/**
 * Cuántas columnas entran en [ancho]: la cuenta de `GridCells.Adaptive(ANCHO_MINIMO_DE_CELDA)`,
 * hecha a mano (ver [SelectorDeCategoria] para por qué no se usa la cuadrícula perezosa).
 */
internal fun columnasDeLaCuadricula(ancho: Dp, espacio: Dp): Int =
    (((ancho + espacio) / (ANCHO_MINIMO_DE_CELDA + espacio)).toInt()).coerceAtLeast(1)

/**
 * El tamaño mínimo legible al que se achica el rótulo de una celda cuando su palabra más larga
 * no entra al tamaño normal — Ola B, tarea 4. Más chico que esto deja de leerse.
 */
internal val TAMANO_MINIMO_DEL_ROTULO = 9.sp

/**
 * Las palabras de [rotulo], para medir el ANCHO de cada una y decidir con la más ancha —no con la
 * que tiene más caracteres, que no es lo mismo: «WWWWWWWWWW» pesa menos letras que
 * «iiiiiiiiiiiiiiiiiiii» pero es bastante más ancha al dibujarse. Sin espacio (una sola palabra,
 * como «Entretenimiento») devuelve una lista de una. Pura, para probarla sin Compose — la
 * medición real de anchos (con el estilo y el peso que se van a pintar) vive en
 * [CeldaDeLaCuadricula], que mide cada una con `TextMeasurer` y se queda con la más ancha.
 */
internal fun palabrasDe(rotulo: String): List<String> =
    rotulo.split(" ").filter { it.isNotEmpty() }

/**
 * Cómo dibujar el rótulo de una celda, según si su palabra más larga entra en el ancho
 * disponible ([anchoDisponible]) al tamaño normal ([anchoDePalabra]). Pura, para probarla sin
 * Compose — la medición real del ancho vive en [CeldaDeLaCuadricula].
 *
 * - [ModoDelRotulo.NORMAL]: como siempre, dos renglones a tamaño normal.
 * - [ModoDelRotulo.ACHICADO]: **un solo renglón**, con `autoSize` (`BasicText`) bajando hasta
 *   [TAMANO_MINIMO_DEL_ROTULO] y «…» si ni así entra. Nunca dos renglones: con `maxLines = 2` una
 *   palabra sin espacios entra «sin desborde» partiéndola entre los dos —Compose no necesita
 *   achicar nada para lograrlo—, que es exactamente el corte a mitad de palabra que esto corrige.
 *   Un solo renglón no le deja esa salida.
 */
internal enum class ModoDelRotulo { NORMAL, ACHICADO }

internal fun modoDelRotulo(anchoDePalabra: Float, anchoDisponible: Float): ModoDelRotulo =
    if (anchoDePalabra <= anchoDisponible) ModoDelRotulo.NORMAL else ModoDelRotulo.ACHICADO

/**
 * # El selector de categoría: una cuadrícula, y el teclado solo si lo pides
 *
 * Ola B · Task 4. Reemplaza a la lista de sugerencias de texto que se abría bajo el campo de
 * categoría. Lo usan todas las pantallas que eligen una categoría, desde un solo lugar: el
 * sub-picker de «Agregar» directamente, y Presupuestos, Recurrentes y la hoja de recategorizar a
 * través de [CategoryField].
 *
 * **La búsqueda NO toma el foco al abrir.** El sub-picker viejo pedía el foco del campo apenas se
 * abría, así que elegir «Comida» —una sola decisión, un toque— levantaba el teclado del sistema,
 * partía la ventana al medio y tapaba la mitad de las categorías. Ahora se abre con las
 * categorías a la vista (las frecuentes primero) y el teclado sale solo si el dueño toca
 * «Buscar o crear categoría».
 *
 * La búsqueda arranca **vacía**: la categoría actual se marca en la cuadrícula ([elegida]) en vez
 * de escribirse en el campo. Por eso ya no hace falta seleccionar todo al enfocar (Ola 2 #3b) —no
 * hay nada prellenado que reemplazar— pero ⌘A sigue funcionando ([rememberCampoConSeleccion]),
 * porque Compose-wasm no lo implementa.
 *
 * **Por qué no `LazyVerticalGrid`.** El selector vive adentro de hojas que se desplazan (la de
 * «Agregar», la de presupuesto, la de recurrentes, la de recategorizar), y una cuadrícula perezosa
 * dentro de un `verticalScroll` o revienta (alto infinito) o necesita un tope con scroll propio —
 * el scroll adentro de otro scroll que el dueño reportó como «al hacer scroll desaparecen» (Ola
 * 14). Son decenas de celdas, no miles: una cuadrícula común que se estira y la desplaza la hoja,
 * un solo desplazamiento, es lo correcto. [columnasDeLaCuadricula] hace la cuenta de
 * `GridCells.Adaptive`.
 *
 * Tocar una celda elige esa categoría y suelta el foco (baja el teclado si estaba arriba); cerrar
 * el selector es cosa de quien lo contiene, en [onElegir].
 */
@Composable
fun SelectorDeCategoria(
    elegida: String,
    onElegir: (String) -> Unit,
    tipo: TransactionType?,
    usadas: Map<String, Set<TransactionType>>,
    prefs: Map<String, CategoryPref>,
    usos: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    var busqueda by remember { mutableStateOf("") }
    // El tope es el de la COLUMNA (`varchar(100)`): una categoría más larga reventaba el insert del
    // server. Se corta acá, en lo único que se puede escribir, y así vale para todas las pantallas.
    val campo = rememberCampoConSeleccion(busqueda) { busqueda = it.take(MAX_CATEGORIA_LENGTH) }
    val focusManager = LocalFocusManager.current
    val contenido = remember(busqueda, tipo, usadas, prefs, usos) {
        contenidoDelSelectorDeCategoria(busqueda, tipo, usadas, prefs, usos)
    }
    val forma = RoundedCornerShape(Movi.formas.normal)

    fun elegir(nombre: String) {
        focusManager.clearFocus()
        onElegir(nombre)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(forma)
                .background(Movi.colores.tarjeta)
                .border(1.dp, Movi.colores.borde, forma)
                .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.medio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = Movi.colores.textoMedio,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Movi.espacios.corto))
            BasicTextField(
                value = campo.valor,
                onValueChange = campo::alCambiar,
                singleLine = true,
                cursorBrush = SolidColor(Movi.colores.texto),
                textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_BUSCAR_CATEGORIA)
                    // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
                    .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                decorationBox = { inner ->
                    if (busqueda.isEmpty()) {
                        Text("Buscar o crear categoría", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado)
                    }
                    inner()
                },
            )
        }

        contenido.reservadaEscrita?.let { reservada ->
            Spacer(Modifier.height(Movi.espacios.corto))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Movi.espacios.minimo)) {
                Text(
                    "«$reservada» la usa Movi sola",
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Text(
                    "Es una categoría reservada y de ella dependen las cifras de tu mes. Elige otra.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
        }
        // «Usar» se explica en una línea propia: la celda tiene dos renglones para el nombre, y
        // sin el porqué se lee como un duplicado de algo que no está en la cuadrícula.
        (contenido.celdas.firstOrNull() as? CeldaDeCategoria.Usar)?.let { usar ->
            Spacer(Modifier.height(Movi.espacios.corto))
            Text(
                usar.porQue,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.padding(horizontal = Movi.espacios.minimo),
            )
        }

        Spacer(Modifier.height(Movi.espacios.medio))
        CuadriculaDeCategorias(
            celdas = contenido.celdas,
            elegida = elegida,
            prefs = prefs,
            onElegir = ::elegir,
        )
    }
}

/**
 * ¿Esta celda es la categoría que ya está puesta? Solo una categoría existente puede serlo: las
 * especiales son lo escrito, no lo que ya estaba puesto.
 *
 * Sin mayúsculas **ni tildes** ([normalizarParaBuscar], la misma vara que usa la búsqueda): un
 * movimiento viejo que dice «Educacion» tiene que ver marcada la celda «Educación». Comparar solo
 * sin mayúsculas dejaba la cuadrícula sin ninguna marcada, como si la categoría puesta no
 * existiera.
 */
internal fun esLaCeldaElegida(celda: CeldaDeCategoria, elegida: String): Boolean =
    celda is CeldaDeCategoria.Existente && normalizarParaBuscar(celda.nombre) == normalizarParaBuscar(elegida)

// `internal` y no `private`: fix round 1 de la tarea 4 — una prueba NATIVE arma una fila con dos
// celdas a mano (`CeldaDeCategoria.Existente` sueltas) para medir que las dos midan lo mismo, sin
// pasar por todo `contenidoDelSelectorDeCategoria` para conseguir que caigan en la misma fila.
@Composable
internal fun CuadriculaDeCategorias(
    celdas: List<CeldaDeCategoria>,
    elegida: String,
    prefs: Map<String, CategoryPref>,
    onElegir: (String) -> Unit,
) {
    val espacio = Movi.espacios.minimo
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnas = columnasDeLaCuadricula(maxWidth, espacio)
        // El ancho real de una celda, la misma cuenta que hace `GridCells.Adaptive` por dentro:
        // lo necesita [CeldaDeLaCuadricula] para decidir si el nombre entra en dos renglones o si
        // la palabra más larga no cabe y hay que achicar la letra (Ola B, tarea 4).
        val anchoDeCelda = (maxWidth - espacio * (columnas - 1)) / columnas
        Column(verticalArrangement = Arrangement.spacedBy(espacio)) {
            celdas.chunked(columnas).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(espacio),
                ) {
                    fila.forEach { celda ->
                        CeldaDeLaCuadricula(
                            celda = celda,
                            elegida = esLaCeldaElegida(celda, elegida),
                            prefs = prefs,
                            onClick = { onElegir(celda.nombre) },
                            anchoDeCelda = anchoDeCelda,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // La última fila, incompleta, no estira sus celdas: ocupa su lugar en la grilla.
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CeldaDeLaCuadricula(
    celda: CeldaDeCategoria,
    elegida: Boolean,
    prefs: Map<String, CategoryPref>,
    onClick: () -> Unit,
    anchoDeCelda: Dp,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(Movi.formas.normal)
    val tag = when (celda) {
        is CeldaDeCategoria.Existente -> tagDeCeldaDeCategoria(celda.nombre)
        is CeldaDeCategoria.Crear -> TAG_CREAR_CATEGORIA
        is CeldaDeCategoria.Usar -> TAG_USAR_CATEGORIA
    }
    val rotulo = when (celda) {
        is CeldaDeCategoria.Existente -> celda.nombre
        is CeldaDeCategoria.Crear -> "Crear \"${celda.nombre}\""
        is CeldaDeCategoria.Usar -> "Usar \"${celda.nombre}\""
    }
    Column(
        modifier = modifier
            .testTag(tag)
            .clip(forma)
            .then(if (elegida) Modifier.border(2.dp, Movi.colores.marca, forma) else Modifier)
            .selectable(selected = elegida, role = Role.Button, onClick = onClick)
            .padding(vertical = Movi.espacios.corto, horizontal = Movi.espacios.minimo),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (celda is CeldaDeCategoria.Crear) {
            Box(
                modifier = Modifier
                    .size(TamanoDeIconoDeCategoria.Normal.circulo)
                    .border(1.dp, Movi.colores.marca, RoundedCornerShape(Movi.formas.pleno)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(TamanoDeIconoDeCategoria.Normal.icono))
            }
        } else {
            // Con las preferencias que recibió el selector, no las del caché: las dos son la misma
            // fuente en la app, pero así lo que se dibuja y lo que se filtra no pueden diferir.
            val apariencia = remember(celda.nombre, prefs) { aparienciaDe(celda.nombre, prefDeCategoria(celda.nombre, prefs)) }
            IconoDeCategoria(apariencia = apariencia)
        }
        Spacer(Modifier.height(Movi.espacios.minimo))
        val colorDelRotulo = if (elegida || celda !is CeldaDeCategoria.Existente) Movi.colores.marca else Movi.colores.texto
        val pesoDelRotulo = if (elegida || celda !is CeldaDeCategoria.Existente) FontWeight.Medium else FontWeight.Normal
        // Ola B, tarea 4: «Entretenimiento» se partía a mitad de palabra («Entretenimient / o»)
        // porque el `Text` de siempre ajusta a dos renglones sin mirar si la palabra más larga
        // entra. Se mide la palabra más larga contra el ancho real de la celda al tamaño normal;
        // si no entra, [modoDelRotulo] manda al renglón único con `autoSize` — nunca a los dos
        // renglones de siempre con `maxLines = 2`: con una sola palabra (sin espacio en el medio),
        // Compose la cuenta como «entra sin desborde» partiéndola entre los dos renglones, así que
        // `autoSize` ni se molesta en achicar la letra. Un solo renglón no le deja esa salida:
        // `autoSize` achica hasta el mínimo legible, y si ni así entra, «…» — nunca la palabra
        // partida a la mitad.
        val medidor = rememberTextMeasurer()
        val densidad = LocalDensity.current
        val estiloDelRotulo = Movi.textos.apoyo
        val anchoDisponiblePx = with(densidad) { (anchoDeCelda - Movi.espacios.minimo * 2).toPx() }
        val palabras = remember(rotulo) { palabrasDe(rotulo) }
        // Fix round 1, hallazgo 1: se medía con el peso NORMAL siempre, pero la celda elegida (y
        // «Crear»/«Usar») se dibuja en Medium — más ancho. La categoría puesta es justo la que el
        // dueño ve cada vez que reabre el selector, así que medir con el peso que de verdad se va
        // a pintar no es un detalle: es el caso que más se ve.
        //
        // Whole-branch review, final fix wave: se medía UNA palabra —la de más caracteres— y no
        // necesariamente la más ANCHA («WWWWWWWWWW» tiene menos letras que
        // «iiiiiiiiiiiiiiiiiiii» pero es más ancha al dibujarse). Ahora se mide cada palabra y se
        // usa la que de verdad pesa más en píxeles.
        val modo = remember(palabras, anchoDisponiblePx, estiloDelRotulo, pesoDelRotulo) {
            val estiloDeRenderizado = estiloDelRotulo.copy(fontWeight = pesoDelRotulo)
            val anchoDeLaPalabraMasAncha = palabras.maxOfOrNull { palabra ->
                medidor.measure(palabra, estiloDeRenderizado, softWrap = false, maxLines = 1).size.width.toFloat()
            } ?: 0f
            modoDelRotulo(anchoDeLaPalabraMasAncha, anchoDisponiblePx)
        }
        when (modo) {
            ModoDelRotulo.NORMAL -> Text(
                rotulo,
                style = estiloDelRotulo,
                fontWeight = pesoDelRotulo,
                color = colorDelRotulo,
                textAlign = TextAlign.Center,
                // Dos renglones siempre: así todas las celdas de una fila miden lo mismo y el borde
                // de la elegida no queda más bajo que sus vecinas.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Fix round 1, hallazgo 2: un solo renglón sin alto reservado dejaba esta celda más
            // baja que sus vecinas NORMAL (que sí reservan dos con `minLines = 2`) cuando las dos
            // conviven en la misma fila. El `Box` reserva el mismo alto de dos renglones del
            // tamaño base y centra adentro el renglón único, ya achicado.
            ModoDelRotulo.ACHICADO -> Box(
                modifier = Modifier.fillMaxWidth().height(altoDeUnRenglon(estiloDelRotulo) * 2),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = rotulo,
                    style = estiloDelRotulo.copy(color = colorDelRotulo, fontWeight = pesoDelRotulo, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = TAMANO_MINIMO_DEL_ROTULO, maxFontSize = estiloDelRotulo.fontSize, stepSize = 0.5.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
