package com.jvillada.movi.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Id generado del lado del cliente, con prefijo por tipo (`"ev"`, `"acc"`).
 *
 * Hallazgo Crítico de la revisión de la Ola 1: en Android/iOS `Repositories.wallets` es
 * `LocalRepository` (offline-first, SQLDelight local + `SyncEngine`), y tres pantallas mandaban
 * `id = ""` confiando en que el server lo asignara — así funciona en la web, donde el server SÍ
 * es la única escritura (ver `EventRoutes`/`AccountRoutes`, que asignan un id solo si llega en
 * blanco). Pero `LocalRepository.postEvent` inserta con `INSERT OR REPLACE` por PK `id`: con
 * `id = ""` cada evento nuevo REEMPLAZABA al anterior en el teléfono antes de que le tocara
 * turno de sync. Generar el id acá, en el cliente, antes de que el evento/cuenta toque
 * SQLDelight, es lo que le da a cada fila una PK propia desde el primer instante.
 *
 * `kotlin.uuid.Uuid` (estable desde Kotlin 2.0.20, `@OptIn` porque el marcador experimental
 * sigue vigente) está disponible en `commonMain` para todos los targets de este proyecto —
 * Android, iOS y wasmJs—, así que no hace falta un `expect`/`actual` por plataforma.
 */
@OptIn(ExperimentalUuidApi::class)
fun newId(prefix: String): String = "${prefix}_${Uuid.random()}"
