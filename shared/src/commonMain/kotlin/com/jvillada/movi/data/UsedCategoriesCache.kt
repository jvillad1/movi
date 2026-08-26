package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
     * La categoría reservada del traspaso NUNCA entra acá, aunque venga en los movimientos que
     * alimentan este caché (las patas de un traspaso la llevan, y tanto Movimientos como
     * Presupuestos vuelcan todo lo que cargan).
     *
     * Sin este filtro, «Traspaso» aparecía como sugerencia en el campo de categoría de Agregar,
     * en Cambiar categoría y en Presupuestos: la app le ofrecía al dueño escribir exactamente la
     * categoría que después iba a rechazar — y si llegaba a guardarse, su gasto real desaparecía
     * del mes (regla de `isCashFlow`) sin ninguna pata del otro lado que lo explicara.
     */
    fun record(names: Collection<String>) {
        recordAll(names.map { it to null })
    }

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
            .filter { (name, _) -> name.isNotEmpty() && name != TRANSFER_CATEGORY }
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

    /** Al cerrar sesión: estas son las categorías del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        used = emptyMap()
    }

    /** Lo que llega del Inicio dentro del resumen (ver el KDoc de arriba). */
    fun recordFromServer(entries: Collection<UsedCategory>) {
        recordAll(entries.flatMap { entry ->
            if (entry.types.isEmpty()) listOf(entry.name to null)
            else entry.types.map { entry.name to it }
        })
    }

}
