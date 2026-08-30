package com.jvillada.movi.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.effectiveCategoryTypes
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.theme.MinBorder
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute
import kotlinx.coroutines.delay

/**
 * F35: filtra y ordena las sugerencias de categoría para [CategoryField]. Separada del
 * `@Composable` para poder testearla en `:shared:commonTest` sin arrancar Compose.
 *
 * Coincide por "contiene", sin tildes ni mayúsculas ("compu" encuentra "Computador",
 * "medic" encuentra "Médico"). Devuelve las predefinidas ([PREDEFINED_CATEGORIES] filtradas por
 * [type] si se pasa) junto con las [usedCategories] que no dupliquen a una predefinida.
 *
 * **Esa deduplicación compara ignorando mayúsculas pero NO tildes** (`equals(ignoreCase = true)`),
 * al revés de lo que decía este KDoc: «Educacion» escrita a mano y «Educación» del catálogo son dos
 * entradas, no una. Se deja así —cambiarlo haría desaparecer de la lista una categoría en la que el
 * dueño tiene movimientos, sin decírselo—, y con la lista alfabética única las dos quedan **pegadas**
 * en vez de en bloques distintos, que es como se ve el duplicado y se puede unificar desde
 * «Más → Categorías».
 *
 * Sin recortar por defecto: el que llama decide si hace falta un scroll (ver [CategoryField]).
 *
 * **El orden — «cualquier orden» era el problema.** El dueño pidió orden alfabético, y acá es
 * [CATEGORY_NAME_ORDER]: sin tildes, sin mayúsculas, con la ñ después de la n. Dos cosas que
 * cambiaron con eso, y por qué:
 *
 * - **Una sola lista, sin separar catálogo de propias.** Antes iban primero las del catálogo «así
 *   una categoría nueva escrita a mano no compite por el primer lugar con el catálogo fijo». Ese
 *   argumento era sobre el PRIMER LUGAR, y con orden alfabético el primer lugar ya no es un premio
 *   —lo decide la letra—; lo único que de verdad dependía de «la primera que se ofrece» es el valor
 *   con el que arranca el campo, y eso ahora lo resuelve [categoriaPorDefectoPara] leyendo el
 *   catálogo en su orden, no esta lista. Mantener los dos grupos, en cambio, partía el alfabeto en
 *   dos y obligaba a recorrer la lista dos veces para encontrar «Colegio» — que es exactamente la
 *   queja que este cambio vino a resolver.
 * - **Lo que empieza con lo tecleado va antes que lo que apenas lo contiene.** Con «co» escrito,
 *   alfabético puro pondría «Bancolombia» arriba de «Comida». Es un solo desempate, dicho en una
 *   línea: primero las que empiezan con lo que escribiste, y dentro de cada grupo, alfabético. Con
 *   el campo vacío no hay dos grupos: es una sola lista alfabética.
 *
 * Lo que el orden **no** toca: nada reservado se sugiere, nada escondido se ofrece, y el filtro por
 * tipo sigue igual — ordenar es lo último que pasa, sobre lo que ya quedó filtrado.
 *
 * **Ola 9 · A3 — las propias también se filtran por tipo.** [usedCategories] no es una lista de
 * nombres sino nombre → tipos con los que se la vio usada (ver
 * `com.jvillada.movi.data.UsedCategoriesCache`). Una categoría propia solo se esconde cuando hay
 * evidencia de que es del OTRO lado: sin tipos conocidos (conjunto vacío) o usada en los dos, se
 * ofrece igual. Esconder por falta de datos sería peor que sugerir de más — en un arranque en
 * frío no sabemos nada de ninguna.
 *
 * **Ola 10 · Categorías — el tipo dejó de ser la identidad de una categoría.** Hasta acá el
 * catálogo mandaba: «Otros» era de gasto porque el código lo decía, y punto. Ahora manda
 * [prefs] — lo que el dueño decidió en «Más → Categorías» —, con una sola regla ([effectiveCategoryTypes]):
 * lo fijado gana sobre el catálogo, y el catálogo sobre lo aprendido del uso. Y una categoría
 * **escondida** no se ofrece nunca, venga del catálogo o sea propia. Ese es el mecanismo con el
 * que «Otros» pasa a servir para gastos y para ingresos y «Otros ingresos» deja de estorbar, sin
 * tocar una línea de [PREDEFINED_CATEGORIES] ni un movimiento de nadie.
 */
fun suggestCategoryMatches(
    query: String,
    type: TransactionType? = null,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
): List<String> {
    val (delCatalogo, propias) = categoriasQueCoinciden(query, type, usedCategories, prefs)
    return ordenarSugerencias(delCatalogo + propias, query)
}

/**
 * **Qué coincide**, sin decidir todavía en qué orden se muestra: las del catálogo y las propias, por
 * separado y cada una en el orden en el que vino.
 *
 * Existe separada de [suggestCategoryMatches] por un solo motivo, y conviene que quede escrito: el
 * orden del catálogo sigue significando algo para [categoriaPorDefectoPara] —cuál viene prellenada
 * en «Agregar»— y ese significado se perdía si lo único disponible era la lista ya alfabetizada. Con
 * esto, las dos cosas comparten el filtro (reservadas, escondidas, tipo) y difieren solo en el
 * orden, que es justo lo que se quería.
 */
private fun categoriasQueCoinciden(
    query: String,
    type: TransactionType?,
    usedCategories: Map<String, Set<TransactionType>>,
    prefs: Map<String, CategoryPref>,
): Pair<List<String>, List<String>> {
    // El caché guarda los nombres tal cual los escribió el dueño; las preferencias vienen del
    // server con el mismo nombre. Se cruzan sin distinguir mayúsculas ni tildes para que una
    // diferencia de tipeo no haga que una categoría escondida reaparezca.
    val prefsNormalizadas = prefs.entries.associate { (name, pref) -> normalizeForMatch(name.trim()) to pref }
    fun prefDe(name: String): CategoryPref? = prefsNormalizadas[normalizeForMatch(name.trim())]

    fun seOfrece(name: String, tiposUsados: Set<TransactionType>): Boolean {
        val pref = prefDe(name)
        if (pref?.hidden == true) return false
        if (type == null) return true
        val efectivos = effectiveCategoryTypes(name, pref?.pinnedType, tiposUsados)
        // Vacío = "no se sabe de qué lado" → se muestra igual. Ver el KDoc de arriba.
        return efectivos.isEmpty() || type in efectivos
    }

    // Para deduplicar hace falta el catálogo ENTERO, no solo el visible: una categoría del
    // catálogo escondida no puede volver a colarse por la puerta de las propias.
    val todasLasDelCatalogo = PREDEFINED_CATEGORIES.map { it.name }
    val predefined = todasLasDelCatalogo
        // «Pago de tarjeta» está en el catálogo Y es reservada: la app se la ofrecía al anotar un
        // gasto y, si el dueño la elegía, `isCashFlow` sacaba ese gasto real de «Gastos del mes»
        // sin decir nada. Ninguna reservada se sugiere en este campo.
        //
        // **Esto cierra la SUGERENCIA, no la escritura** — decirlo así porque la primera versión
        // de este comentario daba el problema por resuelto y no lo estaba: el campo siempre
        // aceptó texto libre, así que «Pago de tarjeta» tecleado a mano seguía guardándose. Lo
        // que cierra la escritura es la guarda de `QuickAddScreen` (el botón no habilita) más la
        // del server (`POST /api/events` la rechaza con 422).
        //
        // (La hoja de CAMBIAR la categoría de un movimiento existente sí la sigue listando, a
        // propósito: ahí confirmar «esto fue el pago de mi tarjeta» es justo lo que hay que poder
        // hacer.)
        .filterNot { isReservedCategory(it) }
        .filter { seOfrece(it, emptySet()) }
    val q = normalizeForMatch(query)
    val predefinedMatches = predefined.filter { normalizeForMatch(it).contains(q) }
    val usedMatches = usedCategories.entries
        .mapNotNull { (name, types) ->
            val clean = name.trim()
            if (clean.isEmpty()) null else clean to types
        }
        .filterNot { (name, _) -> isReservedCategory(name) }
        .filter { (name, types) -> seOfrece(name, types) }
        .map { (name, _) -> name }
        .distinct()
        .filterNot { used -> todasLasDelCatalogo.any { it.equals(used, ignoreCase = true) } }
        .filter { normalizeForMatch(it).contains(q) }
    return predefinedMatches to usedMatches
}

/**
 * **El orden de las sugerencias**: alfabético, con un solo desempate arriba — las que empiezan con
 * lo que se escribió van antes que las que apenas lo contienen. Ver el KDoc de
 * [suggestCategoryMatches] para el porqué de cada mitad de esa regla.
 *
 * Con [query] en blanco no hay desempate posible (todas «empiezan» con la nada) y queda una sola
 * lista alfabética, que es como se ve el panel apenas se abre el campo.
 */
private fun ordenarSugerencias(nombres: List<String>, query: String): List<String> {
    val q = normalizeForMatch(query.trim())
    return nombres.sortedWith(
        compareBy<String> { if (q.isEmpty() || normalizeForMatch(it).startsWith(q)) 0 else 1 }
            .then(CATEGORY_NAME_ORDER),
    )
}

/**
 * **Lo que el panel de sugerencias muestra de verdad**, ya decidido: la lista completa o la
 * filtrada por lo escrito.
 *
 * Vivía suelto adentro del `@Composable`, y ahí se escondió un defecto que ningún test podía ver:
 * con una categoría **escondida** escrita en el campo (p. ej. «Comida», que era el valor inicial),
 * lo escrito no coincidía con ninguna sugerencia visible, así que se caía a la lista filtrada —
 * vacía, porque la única que matchea está escondida— y el panel se reducía a un solo renglón,
 * «Usar "Comida"». Para elegir otra había que borrar el campo primero: esconder UNA categoría
 * hacía desaparecer TODAS las demás, que es lo contrario de lo que el botón promete.
 *
 * La regla correcta no es «coincide con una sugerencia» sino **«Movi ya conoce este nombre»**,
 * visible o no: si lo conoce, no hay nada que filtrar y se muestran todas.
 */
fun categoriasParaElPanel(
    query: String,
    type: TransactionType? = null,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
): List<String> {
    val todas = suggestCategoryMatches("", type, usedCategories, prefs)
    val q = normalizeForMatch(query.trim())
    val conocidas = usedCategories.keys + PREDEFINED_CATEGORIES.map { it.name } + prefs.keys
    val esNombreConocido = q.isNotEmpty() && conocidas.any { normalizeForMatch(it.trim()) == q }
    val mostrarTodas = query.isBlank() || esNombreConocido || todas.any { normalizeForMatch(it) == q }
    return if (mostrarTodas) todas else suggestCategoryMatches(query, type, usedCategories, prefs)
}

/**
 * **¿Esta categoría sirve para anotar un movimiento de [type]?**
 *
 * La usa toda pantalla que arranca con una categoría ya puesta o que la reconcilia al cambiar de
 * Gasto a Ingreso (hoy `QuickAddScreen`). Antes cada una miraba `Category.type` del catálogo por
 * su cuenta, y eso dejaba dos agujeros que se vieron en el uso real:
 *
 * - Fijar «Otros» en «Ambos» no servía de nada: al pasar de Gasto a Ingreso, `QuickAdd` leía el
 *   `EXPENSE` clavado del catálogo y **se la reemplazaba en silencio por «Salario»** — el caso
 *   exacto que esta ola vino a resolver, roto en la pantalla donde el dueño anota todos los días.
 * - Una categoría escondida seguía sirviendo de valor inicial, así que el campo arrancaba
 *   diciendo justo lo que él acababa de retirar.
 *
 * Una categoría **propia** sin nada declarado (ni tipo fijado ni catálogo) siempre sirve: no hay
 * evidencia de que no, y esconder por falta de datos es peor que ofrecer de más.
 */
fun categoriaSirveParaTipo(
    name: String,
    type: TransactionType,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
): Boolean {
    val limpio = name.trim()
    if (limpio.isEmpty()) return false
    if (isReservedCategory(limpio)) return false
    val pref = prefs.entries.firstOrNull { normalizeForMatch(it.key.trim()) == normalizeForMatch(limpio) }?.value
    if (pref?.hidden == true) return false
    val esDelCatalogo = PREDEFINED_CATEGORIES.any { it.name.equals(limpio, ignoreCase = true) }
    if (!esDelCatalogo && pref?.pinnedType == null) return true
    val tipos = effectiveCategoryTypes(limpio, pref?.pinnedType, usedCategories[limpio].orEmpty())
    return tipos.isEmpty() || type in tipos
}

/**
 * Con qué categoría **arranca** un campo para un tipo dado: la primera que de verdad se le va a
 * ofrecer. Pasa por el mismo filtro que las sugerencias y no por `PREDEFINED_CATEGORIES.first { … }`
 * a secas, para que no pueda volver a pasar lo de antes — el campo prellenado con una categoría
 * escondida, a un toque de «Guardar» de anotar un gasto en la que el dueño acababa de retirar.
 *
 * **«La primera» es la primera del catálogo, no la primera del alfabeto.** Desde que las
 * sugerencias se ordenan alfabéticamente, tomar el primer elemento de [suggestCategoryMatches]
 * habría cambiado el valor inicial al anotar un ingreso de «Salario» a «Arriendo recibido»: un
 * cambio que nadie pidió, en la pantalla que el dueño usa todos los días. Por eso lee el catálogo
 * en su orden (ver el KDoc de [PREDEFINED_CATEGORIES], que es hoy su único significado) y recién
 * después cae a lo que sea que se esté ofreciendo.
 *
 * Si escondió TODAS las del catálogo de ese lado, cae a las propias y por último a la primera del
 * catálogo igual: quedarse sin ningún valor inicial sería peor que uno imperfecto.
 */
fun categoriaPorDefectoPara(
    type: TransactionType,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
): String {
    val (delCatalogo, propias) = categoriasQueCoinciden("", type, usedCategories, prefs)
    return delCatalogo.firstOrNull()
        ?: propias.firstOrNull()
        ?: PREDEFINED_CATEGORIES.first { it.type == type.name }.name
}

/**
 * Ola 9 · A1: ¿hay que ofrecer «Crear "lo que escribió"» arriba de las sugerencias?
 *
 * El campo de categoría **siempre** aceptó texto libre —ni el cliente ni el server validan
 * contra el catálogo— pero nada lo decía: el dueño escribió «Carro», la lista de sugerencias
 * quedó vacía y se leyó como un callejón sin salida (preguntó, textualmente, si se podían crear
 * categorías). La opción de crear es esa misma capacidad, dicha en voz alta.
 *
 * Se ofrece cuando lo escrito no coincide EXACTAMENTE (normalizado) con ninguna sugerencia
 * visible. Que sea "exactamente" y no "no hay sugerencias" es lo que cubre la coincidencia
 * parcial: con «Sal» escrito y «Salario» en el catálogo se ven las dos cosas —la sugerencia y
 * crear «Sal»— sin que una tape a la otra.
 */
fun shouldOfferCreateCategory(
    query: String,
    matches: List<String>,
    /**
     * Todo lo que Movi ya conoce, incluido lo que el filtro por tipo esconde. Sin esto, escribir
     * «Carro» al anotar un INGRESO ofrecía «Crear "Carro"» para una categoría que ya existe
     * (solo que como gasto, ver [suggestCategoryMatches]): una oferta que promete algo nuevo y
     * no crea nada.
     */
    conocidas: Collection<String> = emptyList(),
): Boolean {
    val q = normalizeForMatch(query.trim())
    if (q.isEmpty()) return false
    if (conocidas.any { normalizeForMatch(it.trim()) == q }) return false
    return matches.none { normalizeForMatch(it) == q }
}

/**
 * Ola 9 · A4: **la salida del panel vacío.** El filtro por tipo y la guarda anti-duplicado de
 * [shouldOfferCreateCategory] se tapaban entre sí: en Agregar -> Ingreso, escribir «Carro» —una
 * categoría que el dueño solo usó como gasto— escondía la que existe (por tipo) Y escondía el
 * «Crear» (porque ya existe). El resultado era una pantalla completa sin nada que tocar y sin
 * ninguna explicación; el texto no se perdía, pero se leía como roto.
 *
 * Devuelve `true` exactamente en ese hueco: lo escrito no está entre las sugerencias visibles
 * pero Movi sí lo conoce. Es el complemento de [shouldOfferCreateCategory] — nunca son las dos
 * cosas a la vez.
 */
fun shouldOfferKnownFromOtherSide(
    query: String,
    matches: List<String>,
    conocidas: Collection<String> = emptyList(),
): Boolean {
    val q = normalizeForMatch(query.trim())
    if (q.isEmpty()) return false
    if (matches.any { normalizeForMatch(it) == q }) return false
    return conocidas.any { normalizeForMatch(it.trim()) == q }
}

/**
 * De qué lado la conoce Movi, para poder decirlo en una línea («Ya la tienes en Gastos») en vez
 * de dejar el panel mudo. Mira las propias ([usedCategories], que guardan con qué tipos se las
 * vio) y el catálogo fijo. `null` si no hay nada del OTRO lado que contar — ahí la sugerencia ya
 * se está viendo y no hay nada que explicar.
 */
fun ladoConocidoDeCategoria(
    query: String,
    type: TransactionType?,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
): String? {
    val q = normalizeForMatch(query.trim())
    if (q.isEmpty()) return null
    val tiposUsados = mutableSetOf<TransactionType>()
    var nombre = query.trim()
    for ((name, types) in usedCategories) {
        if (normalizeForMatch(name.trim()) == q) {
            tiposUsados += types
            nombre = name.trim()
        }
    }
    PREDEFINED_CATEGORIES.firstOrNull { normalizeForMatch(it.name) == q }?.let { nombre = it.name }
    // Ola 10: la misma regla única que las sugerencias — lo fijado por el dueño gana sobre el
    // catálogo. Sin esto, «Otros» fijada en «Ambos» seguiría diciendo «Ya la tienes en Gastos»
    // al anotar un ingreso, contradiciendo lo que él mismo acababa de decidir.
    val pinned = prefs.entries.firstOrNull { normalizeForMatch(it.key.trim()) == q }?.value?.pinnedType
    val tipos = effectiveCategoryTypes(nombre, pinned, tiposUsados)
    val delOtroLado = tipos - setOfNotNull(type)
    return when {
        delOtroLado.isEmpty() -> null
        delOtroLado.size > 1 -> "Ya la tienes en Gastos y en Ingresos"
        delOtroLado.first() == TransactionType.EXPENSE -> "Ya la tienes en Gastos"
        else -> "Ya la tienes en Ingresos"
    }
}

/**
 * Cómo está escrita la categoría que Movi ya conoce («Carro» cuando el dueño acaba de escribir
 * «carro»). Se elige esa y no lo tecleado para no partir una categoría en dos por una mayúscula
 * —presupuestos y gastos se cruzan por nombre—. `null` si no la conoce.
 */
fun nombreCanonicoConocido(
    query: String,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    /**
     * Ola 10: las preferencias también saben cómo se escribe una categoría, y **sobreviven a un
     * arranque en frío** — se persisten, mientras que [usedCategories] arranca vacío en cada
     * apertura. Sin mirarlas acá, tras una recarga sin red pasaba justo lo que esta función
     * existe para impedir: el panel reconocía «carro» (porque las conocidas incluyen las claves de
     * `prefs`), pero esto devolvía `null` y se guardaba **«carro»** en vez de «Carro» — la
     * categoría partida en dos por una mayúscula, que es como se cruzan presupuestos y gastos.
     */
    prefs: Map<String, CategoryPref> = emptyMap(),
): String? {
    val q = normalizeForMatch(query.trim())
    if (q.isEmpty()) return null
    PREDEFINED_CATEGORIES.firstOrNull { normalizeForMatch(it.name) == q }?.let { return it.name }
    usedCategories.keys.map { it.trim() }.firstOrNull { normalizeForMatch(it) == q }?.let { return it }
    return prefs.keys.map { it.trim() }.firstOrNull { normalizeForMatch(it) == q }
}

/**
 * Minúsculas y sin tildes/diéresis/eñe — no hay normalización Unicode común a los 3 targets acá.
 *
 * **Cubre las mismas letras que [categorySortKey], y difiere en una sola cosa a propósito:** acá la
 * `ñ` se aplasta contra la `n` (buscar «nono» tiene que encontrar «Ñoño»), y allá se manda justo
 * DESPUÉS de la n, que es donde la pone el alfabeto. Buscar y ordenar no piden lo mismo.
 *
 * La `ü` estaba de más acá hasta esta ola: «Pingüinos» ya ordenaba como «pinguinos» pero **no se
 * encontraba** escribiendo «pinguinos», que es justo como se teclea sin pensarlo.
 */
private fun normalizeForMatch(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) {
        append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'; 'ñ' -> 'n'
                else -> c
            },
        )
    }
}

/**
 * Campo de categoría compartido: texto libre con sugerencias, en vez de un picker de lista
 * fija o un texto libre sin ayuda. Usado en QuickAdd, Presupuestos (crear) y Recurrentes
 * (crear/editar) — así una categoría se llama igual en todos lados, lo que importa porque
 * presupuestos y gastos se cruzan por nombre. [ChangeCategorySheet] se queda como lista: ahí
 * se elige entre el catálogo para UN movimiento existente, no se escribe una categoría nueva.
 *
 * Tocar una sugerencia la elige y cierra la lista; escribir algo que no matchea se acepta tal
 * cual (el recorte de espacios lo hace quien guarda, como ya hacían estas pantallas).
 */
@Composable
fun CategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    /** Nombre → tipos con los que se la vio usada (ver `UsedCategoriesCache.used`). */
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    /** Ola 10: lo que el dueño decidió en «Más → Categorías» (ver `UsedCategoriesCache.prefs`). */
    prefs: Map<String, CategoryPref> = emptyMap(),
    label: String? = "CATEGORÍA",
    placeholder: String = "Ej: Vivienda, Suscripción, Salud",
    /** Además de [onValueChange]: se dispara solo al tocar una sugerencia, nunca al tipear —
     *  para que quien tiene un sub-picker de pantalla completa (QuickAdd) pueda cerrarlo solo. */
    onSuggestionPicked: () -> Unit = {},
    /** Ola 2 #3: si se pasa, QuickAdd la usa para pedir foco al abrir el sub-picker (el campo
     *  arranca prellenado — sin esto nunca se veía el teclado ni la lista de sugerencias). */
    focusRequester: FocusRequester? = null,
    /**
     * Alto máximo del panel de sugerencias, **con scroll propio adentro**; `null` = sin tope y
     * **sin scroll propio**, o sea el panel se estira con sus filas y lo desplaza quien lo
     * contenga.
     *
     * Ola 14 — nace de un reporte del dueño desde el navegador del teléfono: «cuando quiero ver
     * las categorías para hacer un nuevo movimiento, al hacer scroll desaparecen». El tope de
     * 220 dp nació cuando este campo era un renglón más de un formulario que NO se desplazaba:
     * ahí acotarlo era lo correcto, porque si no el panel tapaba la pantalla entera. Desde que
     * la hoja de «Agregar» se desplaza (Ola 12), en el sub-picker de Categoría ese mismo tope
     * dejó dos defectos juntos, medidos en un teléfono de 375×812:
     *
     * - **Se ven 4 categorías** en una ventanita de 220 dp… con ~370 px de losa vacía justo
     *   debajo, adentro del mismo sub-picker (medido en el navegador a 375×812, con una cuenta
     *   sembrada que ofrece 24). Para ver el resto hay que desplazar la ventanita.
     * - Y desplazarla es **un scroll adentro de otro scroll**: el gesto del dedo lo puede tomar
     *   la hoja o la lista, y de ahí sale el «desaparecen».
     *
     * Por eso el sub-picker de pantalla completa pasa `null`: una sola área desplazable —la de
     * la hoja— y la lista entera a la vista.
     *
     * **La regla es «¿la hoja que lo contiene se desplaza?», no qué pantalla es.** `BudgetSheet`
     * conservaba el tope con el argumento de que ahí el panel se abre encima de un formulario
     * que no se desplaza — y eso dejó de ser cierto cuando esa hoja recibió su propio
     * `verticalScroll` (hacía falta: con la lista de movimientos del presupuesto, el teclado y
     * «Guardar» se salían de la pantalla). Con la hoja desplazándose, el tope reproducía exacto
     * el scroll-adentro-de-scroll de arriba, así que ahora también pasa `null`.
     *
     * Las otras dos (`CreateRecurringRuleSheet` y `CategorySheets`) sí conservan su tope: sus
     * hojas siguen sin desplazarse.
     */
    maxSuggestionsHeight: Dp? = 220.dp,
) {
    var focused by remember { mutableStateOf(false) }
    // F62: la lista NO puede desmontarse en el mismo frame en que el campo pierde el foco.
    // En táctil (web/PWA), el down del tap sobre una sugerencia desenfoca el campo ANTES de
    // que llegue el up: si la lista se condiciona a `focused` a secas, se desmonta entre el
    // down y el up y el tap muere en la nada — "toco la sugerencia y no pasa nada" (con
    // mouse no se reproduce porque el click no roba el foco en el down). La lista se oculta
    // con una pequeña demora tras perder el foco, para que el tap en vuelo se complete.
    var suggestionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            suggestionsVisible = true
        } else if (suggestionsVisible) {
            delay(350)
            suggestionsVisible = false
        }
    }
    // El desplazamiento del panel acotado. Se recuerda acá arriba y no adentro de la rama que lo
    // usa, porque un `remember` colgado de una rama que aparece y desaparece es frágil de leer.
    //
    // **La clave está en el `suggestionsVisible`**: con él, el estado se rehace cada vez que el
    // panel se muestra, que es exactamente lo que hacía el `rememberScrollState()` de adentro del
    // `if` — el panel se abría arriba de todo. Sin la clave, en las tres pantallas acotadas el
    // panel reabría donde el dueño lo había dejado desplazado, y este cambio dejaba de ser
    // «no toca nada» para ellas.
    val suggestionsScroll = remember(suggestionsVisible) { ScrollState(0) }
    val focusManager = LocalFocusManager.current
    // Estado interno de texto+selección: separado de [value] para poder seleccionar todo el
    // texto al enfocar (Ola 2 #3b) sin pelearse con el `value: String` que ya usan las 3
    // pantallas que llaman a este campo. Solo se resincroniza con [value] cuando el cambio vino
    // de AFUERA (p. ej. QuickAdd reseteando la categoría al cambiar Gasto/Ingreso) — si viene de
    // nuestro propio tipeo, `textFieldValue.text` ya coincide y no hace falta tocar el cursor.
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    // Ola 9 · A1: lo escrito no coincide con nada → arriba de todo, la opción de crearlo. Ver
    // [shouldOfferCreateCategory] para el porqué y para el caso de la coincidencia parcial.
    val nuevaCategoria = value.trim()
    // Las propias de cualquier tipo Y el catálogo entero: lo que ya existe no se "crea".
    // Ola 10: escondida sigue siendo conocida — ofrecerle «Crear "Ropa"» a alguien que acaba de
    // esconder «Ropa» sería prometerle algo nuevo y devolverle exactamente lo que sacó de la vista.
    val conocidas = usedCategories.keys + PREDEFINED_CATEGORIES.map { it.name } + prefs.keys

    // Ola 2 #3a + Ola 10: qué se lista lo decide [categoriasParaElPanel] — con el campo
    // prellenado o con un nombre que Movi ya conoce (aunque esté escondido) se muestran TODAS las
    // disponibles; si no, las que coinciden con lo escrito. Ver ahí el porqué de cada rama.
    val matches = remember(value, type, usedCategories, prefs) {
        categoriasParaElPanel(value, type, usedCategories, prefs)
    }

    // Una reservada escrita a mano no se crea ni se "usa": se explica y se corta. Es la única
    // rama del panel que no ofrece nada tocable, y a propósito — elegirla saca el gasto del mes.
    val esReservada = isReservedCategory(value)
    val ofrecerCrear = !esReservada &&
        shouldOfferCreateCategory(query = value, matches = matches, conocidas = conocidas)
    // Ola 9 · A4: y si no se crea porque YA existe (del otro lado), se dice — sin esto el panel
    // quedaba completamente vacío. Ver [shouldOfferKnownFromOtherSide].
    val ofrecerConocida = !esReservada && shouldOfferKnownFromOtherSide(value, matches, conocidas)
    // ¿Está escondida? Entonces el renglón «Usar…» no puede decir «ya la tienes en Gastos» y
    // callarse lo único que explica por qué no aparece en la lista de abajo.
    val estaEscondida = prefs.entries
        .firstOrNull { normalizeForMatch(it.key.trim()) == normalizeForMatch(value.trim()) }?.value?.hidden == true
    val ladoConocido = when {
        !ofrecerConocida -> null
        estaEscondida -> "La escondiste en Categorías; puedes usarla igual"
        else -> ladoConocidoDeCategoria(value, type, usedCategories, prefs)
    }
    val nombreConocido = if (ofrecerConocida) nombreCanonicoConocido(value, usedCategories, prefs) ?: nuevaCategoria else nuevaCategoria

    fun pick(name: String) {
        onValueChange(name)
        textFieldValue = TextFieldValue(name, selection = TextRange(name.length))
        // Ola 2 #3d: antes esto ponía `focused = false` a mano sin soltar el foco real del
        // campo — el cursor seguía ahí, así que un onFocusChanged posterior nunca volvía a
        // dispararse y las sugerencias no reaparecían al seguir tipeando. clearFocus() sí baja
        // el foco de verdad; `focused` se actualiza solo, vía onFocusChanged.
        focusManager.clearFocus()
        // F62: al elegir sí se cierra al instante — la demora es solo para taps en vuelo.
        suggestionsVisible = false
        onSuggestionPicked()
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(label, fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerLow)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onValueChange(it.text)
                },
                singleLine = true,
                cursorBrush = SolidColor(MinText),
                textStyle = TextStyle(color = MinText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { state ->
                        // Ola 2 #3b: al ganar el foco (no en cada recomposición), seleccionar
                        // todo el texto — con el campo prellenado, tipear reemplaza en vez de
                        // insertarse en medio de "Comida".
                        if (state.isFocused && !focused) {
                            textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
                        }
                        focused = state.isFocused
                    },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = MinTextFaint)
                    inner()
                },
            )
        }
        // Visible mientras el campo tiene foco (más la demora de gracia de F62): tocar una
        // sugerencia la elige y lo quita del medio.
        if (suggestionsVisible && (matches.isNotEmpty() || ofrecerCrear || ofrecerConocida || esReservada)) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Ver [maxSuggestionsHeight]: con tope, el panel trae su propio scroll; sin
                    // tope no trae ninguno, y ese es justamente el punto — dos áreas
                    // desplazables anidadas es lo que se lleva el gesto del dedo.
                    .then(
                        if (maxSuggestionsHeight != null) {
                            Modifier.heightIn(max = maxSuggestionsHeight).verticalScroll(suggestionsScroll)
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerHigh)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp)),
            ) {
                if (esReservada) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            "«$nuevaCategoria» la usa Movi sola",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                        )
                        Text(
                            "Es una categoría reservada y de ella dependen las cifras de tu mes. Elige otra.",
                            fontSize = 11.sp,
                            color = MinTextMute,
                        )
                    }
                    if (matches.isNotEmpty()) Hairline()
                }
                if (ofrecerCrear) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pick(nuevaCategoria) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Crear \"$nuevaCategoria\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinPrimary,
                        )
                        Text(
                            "Se guarda tal cual, como categoría tuya",
                            fontSize = 11.sp,
                            color = MinTextMute,
                        )
                    }
                    if (matches.isNotEmpty()) Hairline()
                }
                if (ofrecerConocida) {
                    // Se puede TOCAR, no es un cartel: elegirla es lo que el dueño vino a hacer
                    // (la categoría es texto libre y usarla de los dos lados es válido), y así el
                    // panel deja de ser una pantalla sin salida.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pick(nombreConocido) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Usar \"$nombreConocido\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinPrimary,
                        )
                        Text(
                            ladoConocido ?: "Ya la tienes anotada",
                            fontSize = 11.sp,
                            color = MinTextMute,
                        )
                    }
                    if (matches.isNotEmpty()) Hairline()
                }
                matches.forEachIndexed { i, name ->
                    Text(
                        name,
                        fontSize = 14.sp,
                        color = MinText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pick(name) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    if (i < matches.size - 1) Hairline()
                }
            }
        }
    }
}
