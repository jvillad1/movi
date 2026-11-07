package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory

/**
 * F35: categorías ya usadas por el dueño, para sugerirlas en [com.jvillada.movi.ui.components.CategoryField]
 * sin pedirle nada nuevo al server. Movi no tiene un endpoint de "categorías usadas" — en vez de
 * agregar uno, las pantallas que YA cargan movimientos, presupuestos o reglas recurrentes
 * (PresupuestosScreen y TransactionsScreen) alimentan este caché de paso con
 * [record] al terminar su propia carga; QuickAddScreen (que no carga nada de eso) lo lee tal
 * cual, sin fetch propio.
 *
 * **Ola 9 · A2 — las categorías propias ya están al abrir «Agregar».** Ese diseño tenía un
 * agujero que se notó el primer día de uso real: quien abre la app y va DIRECTO a Agregar no
 * pasó por ninguna de esas pantallas, así que el caché estaba vacío y sus propias
 * categorías («Carro») no se le ofrecían aunque las hubiera escrito diez veces. Ahora el Inicio
 * —la pantalla en la que la app arranca— también lo llena, con la lista que le viene DENTRO de
 * `GET /api/dashboard/summary` (ver [UsedCategory] y `DashboardSummary.usedCategories`): es un
 * campo más en una respuesta que esa pantalla ya pedía, o sea **cero llamadas nuevas** en la
 * pantalla que el dueño ya se quejó de que dispara diez.
 *
 * **Ola 9 · A3 — cada categoría recuerda con qué tipo se usó.** Una categoría propia no tiene
 * tipo declarado (las de `PREDEFINED_CATEGORIES` sí), así que «Carro» se ofrecía igual anotando
 * un ingreso que un gasto. Acá se guarda el conjunto de tipos con los que se la vio usada y
 * `suggestCategoryMatches` filtra con esa evidencia. La regla ante la duda es MOSTRAR: un vacío
 * (la vimos, no sabemos de qué lado — un presupuesto, una regla vieja) se ofrece siempre; una
 * categoría usada en los dos lados también. Solo se esconde lo que tiene evidencia de un solo
 * tipo, y del otro tipo.
 *
 * Solo vive en memoria del proceso — no persiste ni sincroniza. Arranca vacío en cada apertura
 * de la app y se repuebla en cuanto el Inicio (o cualquiera de esas pantallas) carga una vez: es
 * una ayuda para escribir más rápido, no una fuente de verdad, así que no vale la pena persistirlo.
 */
private const val KEY_CATEGORY_PREFS = "category_prefs"

/**
 * Top-level y no dentro del `object`: el estado inicial de `prefs` se lee en el inicializador del
 * object, y una propiedad declarada más abajo todavía valdría `null` en ese momento.
 */
private val prefsSettings: Settings by lazy { Settings() }
private val prefsJson = Json { ignoreUnknownKeys = true }

/** Explícito y no reificado: la sobrecarga reificada de `encodeToString` es ambigua acá. */
private val prefsSerializer = MapSerializer(String.serializer(), CategoryPref.serializer())

object UsedCategoriesCache {
    /**
     * Nombre limpio → tipos con los que se la vio usada. Un conjunto **vacío** significa
     * "la conocemos pero no sabemos de qué tipo", que no es lo mismo que no conocerla.
     */
    var used: Map<String, Set<TransactionType>> by mutableStateOf(emptyMap())
        private set

    /**
     * Vuelta 119: ¿llegó ya en esta sesión la lista del server ([recordFromServer])? Si no, el caché
     * solo tiene lo anotado de paso y el selector mostraría casi solo el catálogo; lo usa
     * [asegurarLasCategoriasDelDueno] para pedirla una vez. Lo baja [clear].
     */
    var llegaronDelServer: Boolean = false
        private set

    /** Cuándo falló la última vez esa lectura (ver [asegurarLasCategoriasDelDueno]); lo baja [clear]. */
    internal var ultimoFalloDeLasCategoriasMs: Long? = null

    /** Los nombres, sin el tipo — para quien solo necesita saber cuáles existen. */
    val categories: Set<String> get() = used.keys

    /**
     * Ola A: nombre → cuántos movimientos **no anulados** con esa categoría hubo en los últimos
     * 60 días (ver [UsedCategory.usosRecientes], que es de donde sale). Alimenta
     * [categoriasFrecuentes] en `CategoryField.kt` — la fila de chips de «Agregar».
     *
     * Igual que [prefs] y no como [used]: solo lo llena [recordFromServer], porque es la única
     * puerta que trae el dato del server. Las otras pantallas que alimentan [used] de paso
     * (Movimientos, Presupuestos, Recurrentes) no saben cuántas veces se usó cada una, así que no
     * pueden actualizar esto sin mentir con un número inventado.
     */
    var usosRecientes: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    /**
     * **Ola 10 — lo que el dueño decidió en «Más → Categorías».** Nombre → escondida y/o tipo
     * fijado (ver [CategoryPref]).
     *
     * Va separado de [used] a propósito: [used] es *evidencia* (lo que se observó de sus
     * movimientos) y esto es *decisión* (lo que él dijo que quería). Mezclarlas obligaría a
     * adivinar cuál gana en cada lectura; separadas, la regla es una sola y vive en un solo lugar
     * ([com.jvillada.movi.shared.model.effectiveCategoryTypes]): lo decidido manda.
     *
     * Solo lo llena [recordFromServer] — es lo único que sabe de preferencias, porque son del
     * server. Las otras pantallas que alimentan [used] de paso (Movimientos, Presupuestos,
     * Recurrentes) no lo tocan: no tienen el dato, y **borrarlo por no tenerlo sería peor que no
     * actualizarlo** — una categoría escondida volvería a aparecer sola apenas el dueño entra a
     * Movimientos.
     */
    private var prefsState: Map<String, CategoryPref> by mutableStateOf(leerPrefsGuardadas())

    var prefs: Map<String, CategoryPref>
        get() = prefsState
        private set(value) {
            prefsState = value
            guardarPrefs(value)
        }

    /** Varios nombres sueltos, sin saber de qué tipo. Todo pasa por [recordAll]. */
    fun record(names: Collection<String>) {
        recordAll(names.map { it to null })
    }

    /**
     * Las tres categorías que Movi **escribe solo** y que el dueño no debería poder elegir a
     * mano. Nunca entran acá, aunque vengan en los movimientos que alimentan el caché — el filtro
     * está en [recordAll], que es por donde pasan todos los caminos de entrada.
     *
     * - «Traspaso», que llevan las dos patas de un traspaso y que tanto Movimientos como
     *   Presupuestos vuelcan al cargar. Ofrecerla en el campo de categoría era invitar al dueño a
     *   escribir exactamente lo que la app después iba a rechazar — y si llegaba a guardarse, su
     *   gasto real desaparecía del mes (regla de `isCashFlow`) sin ninguna pata que lo explicara.
     * - «Cuenta eliminada», que el server le pone a la pata de traspaso que quedó sin hermana.
     * - «Saldo inicial», que marca la apertura de una cuenta y queda FUERA del flujo de caja.
     *
     * - «Desembolso de crédito», que llevan las dos patas de un desembolso y que cuenta como plata
     *   que entró: ofrecerla a mano es invitar a inflar «Entró» sin deuda detrás (el server rechaza
     *   escribirla suelta). No es reservada, pero tampoco se sugiere.
     *
     * «Cuenta eliminada» y «Saldo inicial» llegaron con la lista completa que ahora manda el Inicio
     * (Ola 9 · A2).
     */
    private val RESERVADAS = setOf(TRANSFER_CATEGORY, ORPHANED_LEG_CATEGORY, OPENING_CATEGORY, DESEMBOLSO_CATEGORY)

    /** Una sola categoría, con el tipo con el que se la acaba de usar (o `null` si no se sabe). */
    fun record(name: String, type: TransactionType?) {
        recordAll(listOf(name to type))
    }

    /**
     * Varias de golpe. `null` en el tipo = "no se sabe": NO borra lo que ya se sabía de esa
     * categoría (si «Carro» ya constaba como gasto, verla sin tipo no la vuelve ambigua).
     */
    fun recordAll(entries: Collection<Pair<String, TransactionType?>>) {
        val cleaned = entries
            .map { (name, type) -> name.trim() to type }
            .filter { (name, _) -> name.isNotEmpty() && name !in RESERVADAS }
        if (cleaned.isEmpty()) return
        val next = used.toMutableMap()
        var changed = false
        for ((name, type) in cleaned) {
            val before = next[name]
            val after = if (type == null) (before ?: emptySet()) else (before.orEmpty() + type)
            if (before == null || after != before) {
                next[name] = after
                changed = true
            }
        }
        if (changed) used = next
    }

    /**
     * Las preferencias **sí se persisten** (a diferencia de [used], que es una ayuda para escribir
     * y se repuebla sola en cuanto cualquier pantalla carga).
     *
     * El motivo es que degradaban en silencio justo donde peor se nota: [prefs] lo llena solo
     * `recordFromServer`, y a ese solo lo llama el Inicio con el resumen. En el teléfono sin
     * señal, o si esa llamada falla, el caché quedaba vacío y **todas las categorías escondidas
     * reaparecían** en «Agregar» — la app deshaciendo sola una decisión del dueño, sin decir nada.
     * Guardadas, lo que escondió sigue escondido hasta que el server diga lo contrario.
     *
     * Mismo `Settings` que usa `SessionManager`, y se borran en [clear] junto con la sesión: son
     * del usuario que se va.
     */
    /**
     * Lectura **a prueba de todo**: esto corre en el inicializador del object, así que cualquier
     * cosa que tire acá se lleva puesta la clase entera y con ella la app (o, como se vio, la
     * suite de tests: en un test de JVM puro no hay `Settings` que valga). Un almacenamiento que
     * no está, o un JSON viejo o corrupto, valen exactamente lo mismo: se arranca sin nada y se
     * repuebla en el primer paso por el Inicio. Es una ayuda para escribir, no una fuente de
     * verdad — no puede ser el motivo de que nada arranque.
     */
    /**
     * **Aquel vecino ya está arreglado:** `SessionManager` leía `auth_token` en el inicializador
     * de su object y sin protección, así que con el almacenamiento del sitio bloqueado esa clase
     * moría antes de que este caché llegara a cargarse —y el `runCatching` de acá abajo no lo
     * tapaba, porque son dos objects distintos—. Ahora usa este mismo patrón (`by lazy` +
     * `runCatching`, ver `SessionManager.kt`).
     */
    private fun leerPrefsGuardadas(): Map<String, CategoryPref> = runCatching {
        val raw = prefsSettings.getStringOrNull(KEY_CATEGORY_PREFS) ?: return@runCatching emptyMap()
        prefsJson.decodeFromString(prefsSerializer, raw)
    }.getOrDefault(emptyMap())

    private fun guardarPrefs(value: Map<String, CategoryPref>) {
        runCatching {
            if (value.isEmpty()) prefsSettings.remove(KEY_CATEGORY_PREFS)
            else prefsSettings[KEY_CATEGORY_PREFS] = prefsJson.encodeToString(prefsSerializer, value)
        }
    }

    /** Al cerrar sesión: estas son las categorías del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        used = emptyMap()
        prefs = emptyMap()
        usosRecientes = emptyMap()
        llegaronDelServer = false
        ultimoFalloDeLasCategoriasMs = null
    }

    /**
     * Lo que llega del Inicio dentro del resumen (ver el KDoc de arriba) — y, desde la Ola 10,
     * también las preferencias.
     *
     * Las preferencias se **reemplazan enteras**, no se acumulan: esta lista es la verdad completa
     * del server sobre lo que el dueño decidió, así que fusionarla con lo viejo dejaría vivo un
     * «escondida» que él acaba de deshacer.
     */
    fun recordFromServer(entries: Collection<UsedCategory>) {
        recordAll(entries.flatMap { entry ->
            if (entry.types.isEmpty()) listOf(entry.name to null)
            else entry.types.map { entry.name to it }
        })
        prefs = entries
            .mapNotNull { entry ->
                val nombre = entry.name.trim()
                if (nombre.isEmpty()) return@mapNotNull null
                if (!entry.hidden && entry.pinnedType == null && entry.icono == null && entry.color == null) {
                    return@mapNotNull null
                }
                nombre to CategoryPref(
                    hidden = entry.hidden,
                    pinnedType = entry.pinnedType,
                    icono = entry.icono,
                    color = entry.color,
                    renombradaA = entry.renombradaA,
                )
            }
            .toMap()
        // Ola A: reemplazada entera, igual que [prefs] y por el mismo motivo — esta lista es la
        // verdad completa del server sobre los últimos 60 días, así que fusionarla con lo viejo
        // dejaría vivo un número que ya no es cierto.
        usosRecientes = entries
            .mapNotNull { entry ->
                val nombre = entry.name.trim()
                if (nombre.isEmpty()) null else nombre to entry.usosRecientes
            }
            .toMap()
        llegaronDelServer = true
    }

    /**
     * Lo que la pantalla «Categorías» acaba de cambiar, aplicado al instante — sin esperar al
     * próximo paso por el Inicio. Sin esto, esconder una categoría y volver a «Agregar» la
     * seguiría ofreciendo hasta la próxima carga del resumen, y se leería como que el botón no
     * hizo nada.
     */
    fun applyPref(name: String, pref: CategoryPref) {
        val nombre = name.trim()
        if (nombre.isEmpty()) return
        // Vuelta 138: el alias de una del catálogo vive mientras siga escondida, como en el server
        // (`PUT /api/categories/prefs`); la respuesta de esa ruta no lo trae.
        val conAlias = if (pref.hidden && pref.renombradaA == null) pref.copy(renombradaA = prefs[nombre]?.renombradaA) else pref
        val esDefault = !conAlias.hidden && conAlias.pinnedType == null && conAlias.icono == null && conAlias.color == null
        prefs = if (esDefault) prefs - nombre else prefs + (nombre to conAlias)
    }

    /**
     * Lo que la pantalla «Categorías» acaba de renombrar o unificar, aplicado al instante por el
     * mismo motivo que [applyPref]. Mueve los tipos observados al nombre nuevo y descarta el
     * viejo: el caché es una ayuda para escribir, y seguir ofreciendo «Trasnporte» después de
     * arreglarlo sería justo el error que el dueño vino a corregir.
     */
    fun applyRename(
        from: String,
        to: String,
        escondeElOrigen: Boolean = false,
        /** Vuelta 138: la apariencia que la app mandó al renombrar ([com.jvillada.movi.shared.model.RenameCategoryRequest.icono]). */
        icono: String? = null,
        color: String? = null,
    ) {
        val viejo = from.trim()
        val nuevo = to.trim()
        if (viejo.isEmpty() || nuevo.isEmpty()) return
        val tipos = used[viejo].orEmpty() + used[nuevo].orEmpty()
        used = (used - viejo) + (nuevo to tipos)

        // Espejo EXACTO de lo que hace el server en `rewriteCategory`, y las tres partes importan:
        //
        // 1. El destino acaba de recibir movimientos, así que **no puede quedar escondido**.
        // 2. Si el destino no tenía tipo fijado, **hereda el del origen** (el server hace eso).
        //    Ola B: ícono y color siguen la MISMA regla — el destino gana y, si no tiene nada
        //    propio, hereda lo del origen.
        // 3. Si el origen era del catálogo, el server lo **esconde** para que deje de sugerirse —
        //    y esto es lo que faltaba. Borrar su preferencia en vez de marcarla escondida
        //    deshacía la operación insignia de la rama: unificar «Otros ingresos» en «Otros»
        //    dejaba la lista diciendo «Escondida» y, al mismo tiempo, «Agregar → Ingreso» seguía
        //    sugiriendo «Otros ingresos» — a un toque de volver a partir en dos justo lo que el
        //    dueño acababa de juntar. Sanaba recién al pasar por Inicio.
        //
        // Que sea un espejo y no una regla propia es el punto: dos reglas paralelas para el mismo
        // efecto se desincronizan, y esta ya se desincronizó una vez.
        //
        // Vuelta 138, el mismo espejo de lo nuevo del server: volver a «Vivienda» borra su alias
        // (la fila del destino era el alias, no una preferencia suya); renombrar una del catálogo
        // deja la vieja escondida con el alias; los alias que
        // apuntaban al nombre viejo pasan al nuevo; y la apariencia que mandó la app queda si el
        // destino no tenía.
        val delCatalogo = PREDEFINED_CATEGORIES.firstOrNull { it.name == viejo }
        val prefOrigen = prefs[viejo]
        val prefDestino = prefs[nuevo]?.takeUnless { it.renombradaA != null && it.renombradaA.equals(viejo, ignoreCase = true) }
        // El tipo de una del catálogo renombrada no se fija: lo resuelve `effectiveCategoryTypes` con su
        // nombre de Movi (el alias de abajo), como en el server.
        val tipoFijadoDestino = prefDestino?.pinnedType ?: prefOrigen?.pinnedType
        val iconoDestino = prefDestino?.icono ?: prefOrigen?.icono ?: icono
        val colorDestino = prefDestino?.color ?: prefOrigen?.color ?: color
        val escondidoDestino = !escondeElOrigen && prefOrigen?.hidden == true
        var siguiente = (prefs - viejo - nuevo).mapValues { (_, p) ->
            if (p.renombradaA != null && p.renombradaA.equals(viejo, ignoreCase = true)) p.copy(renombradaA = nuevo) else p
        }
        if (tipoFijadoDestino != null || iconoDestino != null || colorDestino != null || escondidoDestino) {
            siguiente = siguiente + (nuevo to CategoryPref(
                hidden = escondidoDestino,
                pinnedType = tipoFijadoDestino,
                icono = iconoDestino,
                color = colorDestino,
            ))
        }
        if (delCatalogo != null) {
            siguiente = siguiente + (viejo to CategoryPref(hidden = true, renombradaA = nuevo))
            // El nombre viejo vuelve a la lista de conocidas, pero escondido: lo que NO puede
            // pasar es que se siga ofreciendo. `used` ya no lo tiene (arriba se movió al nuevo).
        }
        prefs = siguiente
    }

}
