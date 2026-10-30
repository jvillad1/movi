package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * # Vuelta 71 · las cuotas de una compra con tarjeta, hasta que suben
 *
 * Agregar pregunta «¿A cuántas cuotas?» con una tarjeta de crédito, y el dato va en
 * `FinancialEvent.cuotas`. El teléfono guarda el movimiento en SQLDelight, y la tabla
 * `financial_event` **no tiene esa columna**: agregarla pide una migración (16.sqm) que esta vuelta no
 * hace a propósito — romper el esquema local es lo único que no se puede deshacer en el teléfono.
 *
 * Lo que se hace en cambio: las cuotas del movimiento que todavía no subió se guardan aparte, en una
 * fila de `remote_cache` (una tabla que ya existe; clave [CLAVE]) como `{ id → cuotas }`, y el
 * `SyncEngine` las pone en el `POST /api/events` de la subida. Subido el movimiento, la entrada se
 * borra. Nada de lo local las lee: solo las usa `extractoDeLaTarjeta`, que corre en el server.
 *
 * Cerrar la sesión borra `remote_cache` del usuario (`deleteForUser`), igual que lo no subido.
 */
internal object CuotasPorSubir {
    /** La fila de `remote_cache` con el mapa `{ id del movimiento → cuotas }`. */
    const val CLAVE: String = "cuotas-por-subir"

    private val serializador = MapSerializer(String.serializer(), Int.serializer())

    /** Lo que espera subir, para [uid]. Vacío si no hay nada o si la fila no se entiende. */
    fun todas(db: MoviDatabase, uid: String): Map<String, Int> {
        val fila = db.remoteCacheQueries.get(CLAVE, uid).executeAsOneOrNull() ?: return emptyMap()
        return runCatching { Json.decodeFromString(serializador, fila.payload) }.getOrDefault(emptyMap())
    }

    /** Anota que [eventoId] fue a [cuotas] cuotas. */
    fun guardar(db: MoviDatabase, uid: String, eventoId: String, cuotas: Int) {
        escribir(db, uid, todas(db, uid) + (eventoId to cuotas))
    }

    /** Ya subió: se olvida. */
    fun olvidar(db: MoviDatabase, uid: String, eventoId: String) {
        val antes = todas(db, uid)
        if (eventoId in antes) escribir(db, uid, antes - eventoId)
    }

    private fun escribir(db: MoviDatabase, uid: String, mapa: Map<String, Int>) {
        db.remoteCacheQueries.put(
            cacheKey = CLAVE,
            userId = uid,
            payload = Json.encodeToString(serializador, mapa),
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }
}
