package com.jvillada.movi.observabilidad

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InspectableValue
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.FormaDeDato
import com.jvillada.movi.shared.model.MAX_ID_DE_TOQUE
import com.jvillada.movi.shared.model.PREFIJO_DE_TOQUE
import com.jvillada.movi.shared.model.idDeToqueBienFormado

/**
 * # La autocaptura de toques (vuelta 104)
 *
 * Todo lo que se toca en `ui/components/ds/` —botones, filas, píldoras, tarjetas de propuesta, la
 * cuadrícula de acciones, los interruptores—, la barra de pestañas, el rail y el «+» pasa por
 * `alPresionar` / `alAlternar` / `toqueSinRipple` (`ds/AlPresionar.kt`), que piden un [ToqueDeUso]
 * **obligatorio**: así un componente tocable nuevo no puede olvidarse del registro (no compila), y
 * `LosToquesDelSistemaSeRegistranTest` vigila que en `ds/` no aparezca un `clickable` suelto. Ninguna
 * pantalla tiene que hacer nada.
 *
 * ## El nombre: `toque:<id>`
 *
 * El id es estable y legible, y sale, en este orden, de:
 *
 * 1. un **id fijo** que arma el propio componente con constantes de la app (`pestana:hoy`, `atras`);
 * 2. el **`testTag` de quien lo monta** (el del `Modifier` que recibe el componente), si es un tag del
 *    código ([RotulosDeUsoGenerados.tags]): «por-revisar:ignorar-los-antiguos». Si el tag lleva algo
 *    armado con `$` («cuenta:fila:acc_9c1d…», «categoria:fila:$nombre»), queda **solo la parte del
 *    código**; un id de Movi del final va a los datos (`cuenta_id`), un sufijo de la lista explícita
 *    [SUFIJOS_DE_TAG_PERMITIDOS] (las pestañas) se queda en el id, y **todo lo demás se tira** —una
 *    categoría «SOAT» en `categoria:fila:SOAT` queda `categoria:fila`—;
 * 3. el **texto** del componente, recortado ([slugDeUso]: «Guardar y seguir» → `guardar-y-seguir`), **solo
 *    si es un rótulo de la app**: un literal del código pasado como el rótulo de un componente
 *    **tocable** (`texto` de `Boton`/`Pildora…`, `titulo` de `Fila`, `accion` de una propuesta…;
 *    [RotulosDeUsoGenerados.rotulos], que arma la tarea `generarRotulosDeUso` leyendo solo
 *    `shared/commonMain` sin los comentarios). Nunca un texto con cifras, «$» o «@», nunca un ejemplo
 *    («Ej: …») ni un literal con un nombre propio en medio («Casa con Caro»), y nunca el de un componente con
 *    **contenido dinámico** ([ToqueDeUso.dinamico]: una fila de un movimiento, una tarjeta de cuenta, una
 *    píldora de categoría): ahí el nombre es el tipo de componente más el tag, y los datos llevan el id
 *    de la entidad si el componente lo conoce;
 * 4. si nada de eso, el **tipo de componente** (`boton`, `fila`).
 *
 * Un texto que coincide con un rótulo de la app es, por definición, un rótulo de la app: el riesgo que
 * queda es que alguien nombre algo suyo exactamente como un botón («Guardar»), y aun así lo que se
 * anota es ese rótulo genérico. Por eso la lista es corta y solo de rótulos de componentes tocables:
 * no entran guías, descripciones, ejemplos de campos ni constantes de `:core`, y un literal con una
 * palabra en mayúscula en medio (un nombre propio) se descarta salvo los nombres de la app
 * (`PALABRAS_PROPIAS_DE_LA_APP` en la tarea). `Fila` y `PildoraDeFiltro`, que suelen mostrar cosas del
 * usuario, solo se nombran por su texto si está en esa lista; si no, por su tag o su tipo.
 *
 * ## Lo que cuesta
 *
 * [Uso.toque] arma el id y encola, nada más: ninguna red, ningún disco. La lectura del `testTag` recorre
 * el `Modifier` de quien llama (unos pocos elementos) solo al tocar, nunca al componer.
 */
class ToqueDeUso(
    /** El tipo de componente, en minúsculas con guiones (`boton`, `fila`, `pildora-de-filtro`). */
    val componente: String,
    /** El `Modifier` que recibió el componente: de ahí sale el `testTag` de quien lo monta. */
    val modificador: Modifier = Modifier,
    /** El texto del componente: solo se usa si es un rótulo de la app y el componente no es [dinamico]. */
    val rotulo: String? = null,
    /** El componente muestra contenido del usuario (un movimiento, una cuenta, una categoría): nunca su texto. */
    val dinamico: Boolean = false,
    /** Un id armado por el componente con constantes de la app; gana sobre todo lo demás. */
    val idFijo: String? = null,
    /** Datos del toque (claves de la lista cerrada): el id de la entidad, la acción de una tarjeta. */
    val datos: Map<ClaveDeUso, String> = emptyMap(),
)

/** Un toque ya resuelto: el nombre (`toque:<id>`) y sus datos. */
internal data class ToqueResuelto(val nombre: String, val datos: Map<ClaveDeUso, String>)

/**
 * **El texto como id**: minúsculas sin tildes, lo que no es letra ni cifra se vuelve un guion, sin
 * guiones al principio ni al final ni repetidos, recortado a [max] sin cortar a la mitad de un guion.
 * «¿Ya lo anotaste?» → `ya-lo-anotaste`. Pura.
 */
fun slugDeUso(texto: String, max: Int = MAX_ID_DE_TOQUE, conDosPuntos: Boolean = false): String {
    val b = StringBuilder()
    for (c in texto.lowercase()) {
        val simple = SIN_TILDE[c] ?: c
        when {
            (simple in 'a'..'z') || (simple in '0'..'9') -> b.append(simple)
            conDosPuntos && simple == ':' -> if (b.isNotEmpty() && b.last() != ':' && b.last() != '-') b.append(':') else if (b.isNotEmpty() && b.last() == '-') b[b.lastIndex] = ':'
            else -> if (b.isNotEmpty() && b.last() != '-' && b.last() != ':') b.append('-')
        }
    }
    var id = b.toString().trim('-', ':')
    // Recortado en el último guion o dos puntos que entra: nunca a la mitad de una palabra.
    if (id.length > max) {
        val corte = id.take(max)
        id = (if (id[max] == '-' || id[max] == ':') corte else corte.substringBeforeLast('-', corte)).trimEnd('-', ':')
    }
    return id.trimStart { it.isDigit() }.trim('-', ':')
}

private val SIN_TILDE = mapOf(
    'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
)

/** ¿[texto] puede nombrar un toque? Un rótulo de la app, sin cifras, «$» ni «@». */
internal fun esRotuloDeLaApp(texto: String, rotulos: Set<String> = RotulosDeUsoGenerados.rotulos): Boolean {
    val t = texto.trim()
    return t.isNotEmpty() && t.none { it.isDigit() || it == '$' || it == '@' } && t in rotulos
}

/**
 * **Los únicos sufijos que pueden seguir a un tag armado con `$`** y quedarse en el id: los nombres de
 * las pestañas (`barra-de-pestanas:HOY` → `barra-de-pestanas:hoy`). Una lista explícita y no una forma
 * («cualquier palabra en mayúsculas»): un tag como `categoria:fila:$nombre` lleva el nombre que el
 * dueño le puso a su categoría, y «SOAT» tiene la forma de un enum.
 */
internal val SUFIJOS_DE_TAG_PERMITIDOS: Set<String> =
    com.jvillada.movi.ui.components.NavTab.entries.map { it.name }.toSet()

/** La clave de los datos para un id de Movi, por su prefijo; `null` si no es de una entidad que se nombre. */
internal fun claveDeLaEntidad(id: String): ClaveDeUso? = when (id.substringBefore('_')) {
    "ev", "tr" -> ClaveDeUso.MOVIMIENTO_ID
    "acc" -> ClaveDeUso.CUENTA_ID
    "sms", "cmp" -> ClaveDeUso.AVISO_ID
    "doc" -> ClaveDeUso.DOCUMENTO_ID
    else -> null
}

/**
 * **El `testTag` de quien lo monta, como id** (ver el KDoc del archivo): la parte más larga que es un
 * tag del código, y de lo que sigue un sufijo de [SUFIJOS_DE_TAG_PERMITIDOS] (al id) o un id de Movi (a
 * los datos); lo demás nunca.
 * `null` si el tag no es del código. Pura.
 */
internal fun idDelTag(tag: String, tags: Set<String> = RotulosDeUsoGenerados.tags): Pair<String, Map<ClaveDeUso, String>>? {
    val crudo = tag.trim()
    if (crudo.isEmpty()) return null
    val tagsNormalizados = tagsEnSlug(tags)
    val completo = slugDeUso(crudo, max = 80, conDosPuntos = true)
    if (completo in tagsNormalizados) return completo.take(MAX_ID_DE_TOQUE).trimEnd('-', ':') to emptyMap()
    val tramos = crudo.split(':')
    for (k in tramos.size - 1 downTo 1) {
        val cabeza = slugDeUso(tramos.take(k).joinToString(":"), max = 80, conDosPuntos = true)
        if (cabeza.isEmpty() || cabeza !in tagsNormalizados) continue
        val datos = mutableMapOf<ClaveDeUso, String>()
        val cola = mutableListOf<String>()
        for (resto in tramos.drop(k)) {
            when {
                FormaDeDato.ID.regex!!.matches(resto) -> claveDeLaEntidad(resto)?.let { datos[it] = resto }
                resto in SUFIJOS_DE_TAG_PERMITIDOS -> cola += resto.lowercase().replace('_', '-')
                else -> break
            }
        }
        val id = (listOf(cabeza) + cola).joinToString(":").take(MAX_ID_DE_TOQUE).trimEnd('-', ':')
        return id to datos
    }
    return null
}

private var tagsEnSlugCache: Pair<Set<String>, Set<String>>? = null

private fun tagsEnSlug(tags: Set<String>): Set<String> {
    tagsEnSlugCache?.let { (de, en) -> if (de === tags) return en }
    val en = tags.map { slugDeUso(it, max = 80, conDosPuntos = true) }.filter { it.isNotEmpty() }.toHashSet()
    tagsEnSlugCache = tags to en
    return en
}

/** El `testTag` del `Modifier` (el primero, de afuera hacia adentro); `null` si no tiene. Se lee al tocar. */
internal fun tagDelModificador(modificador: Modifier): String? =
    runCatching {
        modificador.foldIn<String?>(null) { encontrado, elemento ->
            encontrado ?: (elemento as? InspectableValue)
                ?.takeIf { it.nameFallback == "testTag" }
                ?.let { valor -> valor.inspectableElements.firstOrNull { it.name == "tag" }?.value as? String }
        }
    }.getOrNull()

/** **Resuelve un toque** en su nombre y sus datos (ver el KDoc del archivo). Pura salvo la lectura del tag. */
internal fun resolverElToque(
    toque: ToqueDeUso,
    tag: String? = tagDelModificador(toque.modificador),
    rotulos: Set<String> = RotulosDeUsoGenerados.rotulos,
    tags: Set<String> = RotulosDeUsoGenerados.tags,
): ToqueResuelto {
    val componente = slugDeUso(toque.componente).ifEmpty { "componente" }
    val datos = toque.datos.toMutableMap()
    // El tipo de componente siempre va en los datos («Boton», «PildoraDeFiltro»): el server lo pone en
    // `detalle` para contar sin leer el JSON.
    nombreDelComponente(componente)?.let { if (ClaveDeUso.COMPONENTE !in datos) datos[ClaveDeUso.COMPONENTE] = it }
    val fijo = toque.idFijo?.let { slugDeUso(it, conDosPuntos = true) }?.takeIf { it.isNotEmpty() }
    val delTag = if (fijo == null && tag != null) idDelTag(tag, tags) else null
    delTag?.second?.forEach { (clave, valor) -> if (clave !in datos) datos[clave] = valor }
    val id = when {
        fijo != null -> fijo
        delTag != null -> if (toque.dinamico) "$componente:${delTag.first}" else delTag.first
        !toque.dinamico && toque.rotulo != null && esRotuloDeLaApp(toque.rotulo, rotulos) -> slugDeUso(toque.rotulo).ifEmpty { componente }
        else -> componente
    }
    val acotado = id.take(MAX_ID_DE_TOQUE).trimEnd('-', ':')
    val bueno = if (idDeToqueBienFormado(acotado)) acotado else componente
    return ToqueResuelto(PREFIJO_DE_TOQUE + bueno, datos)
}

/**
 * El id de un campo o de una hoja con las mismas reglas que un toque: el tag de quien lo monta, si no
 * el rótulo de la app, si no [porDefecto]. Sin el prefijo `toque:`.
 */
internal fun idDeUsoDe(tag: String?, rotulo: String?, porDefecto: String): String {
    val delTag = tag?.let { idDelTag(it) }?.first
    val id = delTag ?: rotulo?.takeIf { esRotuloDeLaApp(it) }?.let { slugDeUso(it) }?.takeIf { it.isNotEmpty() } ?: porDefecto
    return if (idDeToqueBienFormado(id)) id else porDefecto
}

/** Los datos de un toque sobre [id] (un id de Movi): su clave por el prefijo, o nada. */
fun datosDeLaEntidad(id: String?): Map<ClaveDeUso, String> {
    if (id == null || !FormaDeDato.ID.regex!!.matches(id)) return emptyMap()
    val clave = claveDeLaEntidad(id) ?: return emptyMap()
    return mapOf(clave to id)
}

/**
 * **La entidad de lo que está adentro** (un aviso `sms_…`, un movimiento `ev_…`, una cuenta `acc_…`):
 * la pone quien monta una tarjeta de contenido dinámico («Por revisar», alrededor de cada aviso), y todo
 * toque de `ds/` de adentro la lleva en sus datos (`aviso_id`…). Una [com.jvillada.movi.ui.components.ds.Fila]
 * adentro es dinámica: nunca se nombra por su título.
 */
val LocalEntidadDeUso = staticCompositionLocalOf<String?> { null }

/** [toque] con la entidad de [LocalEntidadDeUso], si hay una y el toque no trae la suya. */
internal fun ToqueDeUso.conLaEntidad(entidad: String?): Map<ClaveDeUso, String> =
    if (entidad == null) emptyMap() else datosDeLaEntidad(entidad).filterKeys { it !in datos }

/** `pildora-de-filtro` → `PildoraDeFiltro` (la forma de [ClaveDeUso.COMPONENTE]); `null` si no la tiene. */
internal fun nombreDelComponente(componente: String): String? =
    componente.split('-').joinToString("") { tramo -> tramo.replaceFirstChar { it.uppercase() } }
        .filter { it.isLetter() }
        .takeIf { ClaveDeUso.COMPONENTE.admite(it) }
