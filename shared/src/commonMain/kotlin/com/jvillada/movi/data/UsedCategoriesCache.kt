package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * F35: categorías ya usadas por el dueño, para sugerirlas en [com.jvillada.movi.ui.components.CategoryField]
 * sin pedirle nada nuevo al server. Movi no tiene un endpoint de "categorías usadas" — en vez de
 * agregar uno, las pantallas que YA cargan movimientos, presupuestos o reglas recurrentes
 * (PresupuestosScreen, TransactionsScreen, RecurrentesScreen) alimentan este caché de paso con
 * [record] al terminar su propia carga; QuickAddScreen (que no carga nada de eso) lo lee tal
 * cual, sin fetch propio.
 *
 * Solo vive en memoria del proceso — no persiste ni sincroniza. Arranca vacío en cada apertura
 * de la app y se repuebla en cuanto cualquiera de esas pantallas carga una vez: es una ayuda
 * para escribir más rápido, no una fuente de verdad, así que no vale la pena persistirlo.
 */
object UsedCategoriesCache {
    var categories: Set<String> by mutableStateOf(emptySet())
        private set

    fun record(names: Collection<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        categories = categories + cleaned
    }
}
