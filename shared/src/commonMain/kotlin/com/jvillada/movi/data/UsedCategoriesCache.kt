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
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory

/**
 * F35: categorías ya usadas por el dueño, para sugerirlas en [com.jvillada.movi.ui.components.CategoryField]
 * sin pedirle nada nuevo al server. Movi no tiene un endpoint de "categorías usadas" — en vez de
 * agregar uno, las pantallas que YA cargan movimientos, presupuestos o reglas recurrentes
 * (PresupuestosScreen, TransactionsScreen, RecurrentesScreen) alimentan este caché de paso con
 * [record] al terminar su propia carga; QuickAddScreen (que no carga nada de eso) lo lee tal
 * cual, sin fetch propio.
 *
 * **Ola 9 · A2 — las categorías propias ya están al abrir «Agregar».** Ese diseño tenía un
 * agujero que se notó el primer día de uso real: quien abre la app y va DIRECTO a Agregar no
 * pasó por ninguna de esas tres pantallas, así que el caché estaba vacío y sus propias
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

    /** Los nombres, sin el tipo — para quien solo necesita saber cuáles existen. */
    val categories: Set<String> get() = used.keys

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
     * Las dos últimas llegaron con la lista completa que ahora manda el Inicio (Ola 9 · A2).
     */
    private val RESERVADAS = setOf(TRANSFER_CATEGORY, ORPHANED_LEG_CATEGORY, OPENING_CATEGORY)

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
                if (!entry.hidden && entry.pinnedType == null) return@mapNotNull null
                nombre to CategoryPref(hidden = entry.hidden, pinnedType = entry.pinnedType)
            }
            .toMap()
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
        prefs = if (!pref.hidden && pref.pinnedType == null) prefs - nombre else prefs + (nombre to pref)
    }

    /**
     * Lo que la pantalla «Categorías» acaba de renombrar o unificar, aplicado al instante por el
     * mismo motivo que [applyPref]. Mueve los tipos observados al nombre nuevo y descarta el
     * viejo: el caché es una ayuda para escribir, y seguir ofreciendo «Trasnporte» después de
     * arreglarlo sería justo el error que el dueño vino a corregir.
     */
    fun applyRename(from: String, to: String) {
        val viejo = from.trim()
        val nuevo = to.trim()
        if (viejo.isEmpty() || nuevo.isEmpty()) return
        val tipos = used[viejo].orEmpty() + used[nuevo].orEmpty()
        used = (used - viejo) + (nuevo to tipos)
        // El destino acaba de recibir movimientos, así que **no puede quedar escondido** — es la
        // misma regla que aplica el server en `rewriteCategory`, espejada acá para que no haya una
        // ventana en la que el server ya la destapó y el teléfono la sigue escondiendo hasta el
        // próximo paso por el Inicio. El tipo fijado del destino, en cambio, se respeta.
        val prefDestino = prefs[nuevo]
        prefs = (prefs - viejo).let { sinViejo ->
            when {
                prefDestino == null -> sinViejo
                prefDestino.pinnedType == null -> sinViejo - nuevo
                else -> sinViejo + (nuevo to prefDestino.copy(hidden = false))
            }
        }
    }

}
