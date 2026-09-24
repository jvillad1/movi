package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.effectiveCategoryTypes
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.ui.LocalNavigate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.categorias.aparienciaDe
import com.jvillada.movi.ui.categorias.prefDeCategoria

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
 * **¿Esta categoría se ofrece para movimientos de [tipo]?** El único criterio de "sirve", usado
 * por las sugerencias del campo ([categoriasQueCoinciden]) y por los chips de frecuentes
 * ([categoriasFrecuentes]) — antes cada una tenía su propia copia y se desincronizaron: los chips
 * usaban [categoriaSirveParaTipo], que para una categoría **propia sin tipo fijado** siempre
 * contesta que sí, sin mirar [tiposUsados]. Una categoría propia usada solo en Ingreso («Arriendo
 * Gardenera») se colaba como chip de Gasto.
 *
 * No escondida, y si hay tipos efectivos conocidos (catálogo, tipo fijado o uso observado),
 * [tipo] tiene que estar entre ellos — vacío ("no se sabe de qué lado") se ofrece igual. Ver el
 * KDoc de [categoriasQueCoinciden] para el porqué completo de esta regla.
 *
 * `internal` (Task 5, fix round 1): `QuickAddScreen` la reutiliza para filtrar la memoria de
 * nombres antes de sugerir — mismo motivo que la dejó afuera de `categoriaSirveParaTipo`, ahí:
 * una categoría propia sin tipo fijado no puede colarse del lado equivocado.
 */
internal fun seOfreceParaTipo(
    name: String,
    tipo: TransactionType?,
    tiposUsados: Set<TransactionType>,
    prefs: Map<String, CategoryPref>,
): Boolean {
    val pref = prefs.entries.firstOrNull { (key, _) -> normalizarParaBuscar(key.trim()) == normalizarParaBuscar(name.trim()) }?.value
    if (pref?.hidden == true) return false
    if (tipo == null) return true
    val efectivos = effectiveCategoryTypes(name, pref?.pinnedType, tiposUsados)
    return efectivos.isEmpty() || tipo in efectivos
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
    // diferencia de tipeo no haga que una categoría escondida reaparezca — ver [seOfreceParaTipo].
    fun seOfrece(name: String, tiposUsados: Set<TransactionType>) = seOfreceParaTipo(name, type, tiposUsados, prefs)

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
    val q = normalizarParaBuscar(query)
    val predefinedMatches = predefined.filter { normalizarParaBuscar(it).contains(q) }
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
        .filter { normalizarParaBuscar(it).contains(q) }
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
    val q = normalizarParaBuscar(query.trim())
    return nombres.sortedWith(
        compareBy<String> { if (q.isEmpty() || normalizarParaBuscar(it).startsWith(q)) 0 else 1 }
            .then(CATEGORY_NAME_ORDER),
    )
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
    val pref = prefs.entries.firstOrNull { normalizarParaBuscar(it.key.trim()) == normalizarParaBuscar(limpio) }?.value
    if (pref?.hidden == true) return false
    val esDelCatalogo = PREDEFINED_CATEGORIES.any { it.name.equals(limpio, ignoreCase = true) }
    if (!esDelCatalogo && pref?.pinnedType == null) return true
    val tipos = effectiveCategoryTypes(limpio, pref?.pinnedType, usedCategories[limpio].orEmpty())
    return tipos.isEmpty() || type in tipos
}

/**
 * Ola A: **las categorías que el dueño más usa**, para ofrecerlas como chips en «Agregar» sin que
 * tenga que abrir el campo ni escribir nada.
 *
 * [usos] es `UsedCategoriesCache.usosRecientes` — movimientos no anulados de esa categoría en los
 * últimos 60 días (ver [com.jvillada.movi.shared.model.UsedCategory.usosRecientes]). **Sin ese
 * dato no hay lista**: un server viejo que todavía no manda `usosRecientes`, o un arranque en frío
 * antes de que el Inicio cargue, no tienen de dónde sacar «frecuente» — devolver una lista con
 * ceros sería inventar un orden que no significa nada.
 *
 * Nada reservado, nada escondido, nada del otro tipo — [seOfreceParaTipo], el mismo criterio que
 * usan las sugerencias del campo (no [categoriaSirveParaTipo]: esa función, para una categoría
 * propia sin tipo fijado, siempre contesta que sirve sin mirar [usadas] — ver su KDoc). El
 * desempate es alfabético con [CATEGORY_NAME_ORDER] — dos categorías con el mismo número de usos
 * no pueden depender del orden en que llegó el mapa.
 */
fun categoriasFrecuentes(
    tipo: TransactionType,
    usadas: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
    usos: Map<String, Int> = emptyMap(),
    cuantas: Int = 6,
): List<String> {
    if (usos.isEmpty()) return emptyList()
    return usos.entries
        .filter { (nombre, cantidad) ->
            cantidad > 0 &&
                !isReservedCategory(nombre) &&
                seOfreceParaTipo(nombre, tipo, usadas[nombre].orEmpty(), prefs)
        }
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .then(compareBy(CATEGORY_NAME_ORDER) { it.key }),
        )
        .take(cuantas)
        .map { it.key }
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
 *
 * **Ola A — con datos de uso, arranca en la más frecuente, no en la primera del catálogo.** Si
 * en los últimos 60 días casi todos los gastos del dueño fueron a una categoría propia (p. ej.
 * «Fútbol») y ninguno a «Comida», seguir arrancando en «Comida» solo porque encabeza el catálogo
 * ignoraba justo el dato nuevo que esta ola trae. (Los números reales del plan —71 en 60 días—
 * son de una CUENTA, «Bancolombia Ahorros», y justifican `resolverCuenta` en `ui/quickadd`, no
 * esto.) Sin [usos] (server viejo, o el Inicio no cargó todavía) cae en el comportamiento de
 * siempre — ver [categoriasFrecuentes].
 */
fun categoriaPorDefectoPara(
    type: TransactionType,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
    usos: Map<String, Int> = emptyMap(),
): String {
    categoriasFrecuentes(type, usedCategories, prefs, usos).firstOrNull()?.let { return it }
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
    val q = normalizarParaBuscar(query.trim())
    if (q.isEmpty()) return false
    if (conocidas.any { normalizarParaBuscar(it.trim()) == q }) return false
    return matches.none { normalizarParaBuscar(it) == q }
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
    val q = normalizarParaBuscar(query.trim())
    if (q.isEmpty()) return false
    if (matches.any { normalizarParaBuscar(it) == q }) return false
    return conocidas.any { normalizarParaBuscar(it.trim()) == q }
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
    val q = normalizarParaBuscar(query.trim())
    if (q.isEmpty()) return null
    val tiposUsados = mutableSetOf<TransactionType>()
    var nombre = query.trim()
    for ((name, types) in usedCategories) {
        if (normalizarParaBuscar(name.trim()) == q) {
            tiposUsados += types
            nombre = name.trim()
        }
    }
    PREDEFINED_CATEGORIES.firstOrNull { normalizarParaBuscar(it.name) == q }?.let { nombre = it.name }
    // Ola 10: la misma regla única que las sugerencias — lo fijado por el dueño gana sobre el
    // catálogo. Sin esto, «Otros» fijada en «Ambos» seguiría diciendo «Ya la tienes en Gastos»
    // al anotar un ingreso, contradiciendo lo que él mismo acababa de decidir.
    val pinned = prefs.entries.firstOrNull { normalizarParaBuscar(it.key.trim()) == q }?.value?.pinnedType
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
    val q = normalizarParaBuscar(query.trim())
    if (q.isEmpty()) return null
    PREDEFINED_CATEGORIES.firstOrNull { normalizarParaBuscar(it.name) == q }?.let { return it.name }
    usedCategories.keys.map { it.trim() }.firstOrNull { normalizarParaBuscar(it) == q }?.let { return it }
    return prefs.keys.map { it.trim() }.firstOrNull { normalizarParaBuscar(it) == q }
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

/**
 * Campo de categoría compartido: muestra la categoría puesta y, al tocarlo, despliega el
 * [SelectorDeCategoria] — la cuadrícula con las frecuentes primero y la búsqueda que no levanta
 * el teclado sola. Usado en Presupuestos, Recurrentes y la hoja de recategorizar (el sub-picker
 * de «Agregar» usa el selector directo, porque ahí ya es una pantalla entera); así una categoría
 * se llama igual en todos lados, lo que importa porque presupuestos y gastos se cruzan por nombre.
 *
 * **Ola B · Task 4 — ya no es un campo de texto.** Antes era texto libre con sugerencias: lo que
 * se tecleaba ERA el valor, así que enfocar el campo era la única forma de ver las categorías, y
 * enfocarlo levantaba el teclado. Ahora se elige tocando una celda, y escribir es buscar; una
 * categoría nueva se crea con la celda «Crear "…"», que es la misma capacidad dicha en voz alta
 * (Ola 9 · A1). Tocar una celda elige y pliega el selector.
 *
 * Con eso se fueron tres cosas que eran del campo de texto y no tienen de qué ocuparse ahora:
 * el foco pedido al abrir (Ola 2 #3c — justo lo contrario de lo que se quiere), la selección de
 * todo al enfocar (Ola 2 #3b — la búsqueda arranca vacía) y la demora al perder el foco antes de
 * esconder la lista (F62 — la cuadrícula no depende del foco, así que un toque en vuelo ya no se
 * puede quedar sin destino). ⌘A sigue funcionando en la búsqueda.
 *
 * **Sin tope de alto ni scroll propio**: se fue `maxSuggestionsHeight`. Su propia regla era
 * «¿la hoja que lo contiene se desplaza?» y hoy las cuatro se desplazan (las dos que conservaban
 * el tope, Recurrentes y la de recategorizar, recibieron su `verticalScroll` después), así que el
 * tope solo reproducía el scroll adentro de otro scroll de la Ola 14 — «al hacer scroll
 * desaparecen». La cuadrícula se estira y la desplaza la hoja.
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
    /** Ola B: usos de los últimos 60 días, para poner las frecuentes primero (ver `UsedCategoriesCache.usosRecientes`). */
    usos: Map<String, Int> = emptyMap(),
    label: String? = "CATEGORÍA",
    placeholder: String = "Ej: Vivienda, Suscripción, Salud",
) {
    var abierto by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(Movi.formas.normal)

    Column(modifier = modifier) {
        if (label != null) {
            Text(label, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(Movi.espacios.corto))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_CAMPO_DE_CATEGORIA)
                .clip(forma)
                .background(Movi.colores.tarjeta)
                .border(1.dp, if (abierto) Movi.colores.marca else Movi.colores.borde, forma)
                .clickable { abierto = !abierto }
                .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.medio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val puesta = value.trim()
            if (puesta.isNotEmpty()) {
                val apariencia = remember(puesta, prefs) { aparienciaDe(puesta, prefDeCategoria(puesta, prefs)) }
                IconoDeCategoria(apariencia = apariencia, tamano = TamanoDeIconoDeCategoria.Chico)
                Spacer(Modifier.width(Movi.espacios.corto))
            }
            Text(
                text = puesta.ifEmpty { placeholder },
                style = Movi.textos.titulo,
                color = if (puesta.isEmpty()) Movi.colores.textoApagado else Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (abierto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Movi.colores.textoMedio,
            )
        }
        if (abierto) {
            Spacer(Modifier.height(Movi.espacios.corto))
            SelectorDeCategoria(
                elegida = value,
                onElegir = {
                    onValueChange(it)
                    abierto = false
                },
                tipo = type,
                usadas = usedCategories,
                prefs = prefs,
                usos = usos,
            )
        }
        Spacer(Modifier.height(Movi.espacios.corto))
        EnlaceAdministrarCategorias()
    }
}

/** El renglón del campo que muestra la categoría puesta — tocarlo abre y cierra el selector. */
const val TAG_CAMPO_DE_CATEGORIA: String = "categoria:campo"

/**
 * El editor completo de categorías, SIEMPRE visible bajo el campo (y bajo el selector de
 * «Agregar»).
 *
 * El dueño: «Necesito un editor de categorías en las diferentes secciones». El editor ya existía y
 * hace todo lo que hace falta —renombrar el error de tipeo, unificar duplicados, esconder lo que
 * no quiere ver, fijar el tipo— pero vivía en «Más → Categorías» y solo se llegaba abandonando lo
 * que uno estaba haciendo.
 *
 * Va en el componente compartido y no cuatro veces en cuatro pantallas: este campo ES donde la
 * pregunta «¿qué categoría?» se hace en las cuatro secciones (Movimientos, Agregar, Presupuestos,
 * Recurrentes), así que un solo renglón las cubre a todas y no puede desincronizarse.
 *
 * Fuera del selector y no adentro: adentro solo se veía al abrirlo, y en la hoja de cambiar
 * categoría —la sección desde la que el dueño lo pidió— la categoría se elige tocando la lista, sin
 * pasar nunca por el campo. O sea que justo ahí el acceso no habría existido.
 */
@Composable
fun EnlaceAdministrarCategorias() {
    // Ver [com.jvillada.movi.ui.LocalNavigate]: por qué un local y no un callback más en la firma.
    val navegar = LocalNavigate.current
    Text(
        text = "Administrar categorías",
        style = Movi.textos.apoyo,
        color = Movi.colores.marca,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Movi.formas.minima))
            .clickable { navegar(Screen.Categorias) }
            .padding(horizontal = Movi.espacios.corto, vertical = Movi.espacios.minimo),
    )
}
