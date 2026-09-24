package com.jvillada.movi.data

import kotlinx.coroutines.CancellationException

/**
 * **`runCatching` que no se traga la cancelación.**
 *
 * `runCatching` atrapa todo, `CancellationException` incluida. Dentro de un `LaunchedEffect` eso
 * convierte «la pantalla ya no quiere esta lectura» (se cambió de segmento, se navegó a otro lado,
 * una clave cambió) en «la lectura falló», y el código que sigue corre igual: en Presupuestos, la
 * carga cancelada daba por contestados el gasto y el período, y al volver se pintaba el gasto del
 * mes de calendario antes del real; en el tablero de pagos, salía el aviso rojo de un error que
 * nadie tuvo.
 *
 * Con esto la cancelación sigue de largo —el efecto termina ahí, sin tocar ningún estado— y
 * cualquier otra falla vuelve como `Result.failure`, igual que con `runCatching`.
 */
inline fun <T> intentar(bloque: () -> T): Result<T> =
    try {
        Result.success(bloque())
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }
