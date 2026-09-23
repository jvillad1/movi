package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ola A: **la cuenta con más gastos de los últimos 30 días**, para que «Agregar» arranque ahí en
 * vez de en la primera del abecedario. Ver `DashboardSummary.cuentaMasUsada` (`:core`) — el
 * server la calcula con las mismas reglas que el resto (`isCashFlow`, movimientos no anulados) y
 * viaja en la respuesta que el Inicio ya pide (`GET /api/dashboard/summary`), así que llenar este
 * caché no cuesta una llamada nueva.
 *
 * Mismo patrón que `UsedCategoriesCache.used`, y por el mismo motivo: solo vive en memoria del
 * proceso, no persiste ni sincroniza. No es una fuente de verdad — es una ayuda que se repuebla
 * sola en cuanto el Inicio carga una vez, así que arrancar vacío en cada apertura de la app no
 * pierde nada que valga la pena guardar.
 *
 * Lo llena `DashboardScreen`, en el mismo `onSuccess` donde llama a
 * `UsedCategoriesCache.recordFromServer` — la misma respuesta, sin pedir nada nuevo. Lo usa
 * `resolverCuenta` en `CuentaPorDefecto.kt`.
 */
object CuentaMasUsadaCache {
    var id: String? by mutableStateOf(null)
        private set

    fun recordFromServer(cuentaId: String?) {
        id = cuentaId
    }

    /** Al cerrar sesión: es la cuenta más usada del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        id = null
    }
}
