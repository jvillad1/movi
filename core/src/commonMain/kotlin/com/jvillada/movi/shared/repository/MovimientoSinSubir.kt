package com.jvillada.movi.shared.repository

/**
 * **El movimiento todavía vive solo en este teléfono** (vuelta de diseño 24).
 *
 * En Android e iOS un gasto se guarda primero en la base local y sube después (el `SyncEngine`).
 * Lo que solo hace el server —repartirlo en un espacio— necesita que ya esté allá: antes de pedirlo,
 * `LocalRepository.repartirMovimiento` da una vuelta de subida, y si el movimiento sigue sin sello
 * lanza esto en vez de mandar un reparto que el server contestaría con «no lo conozco».
 *
 * [rechazo] es lo que el server dijo de ese movimiento (el `syncError` de la fila) si lo rechazó;
 * `null` si simplemente no llegó —sin señal, o el server no contestó—, que es lo que se arregla
 * reintentando con conexión.
 */
class MovimientoSinSubir(
    val eventoId: String,
    val rechazo: String? = null,
) : Exception(rechazo ?: "El movimiento $eventoId todavía no subió al servidor")
