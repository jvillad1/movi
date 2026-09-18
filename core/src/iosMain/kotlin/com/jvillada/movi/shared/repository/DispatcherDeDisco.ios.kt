package com.jvillada.movi.shared.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * En iOS es [Dispatchers.Default] y no `Dispatchers.IO`: en Kotlin/Native esa propiedad es
 * `internal` en kotlinx-coroutines 1.10 (compila en JVM y rompe acá con «Cannot access 'val IO'»).
 *
 * Lo que importa se cumple igual —el trabajo de SQLite no corre en el hilo que pinta— y el riesgo
 * clásico de usar el pool de CPU para algo que bloquea no aplica al tamaño de este trabajo: son
 * lecturas de una base local, no llamadas de red. El día que `IO` deje de ser `internal`, este
 * archivo es el único que cambia.
 */
internal actual fun dispatcherDeDisco(): CoroutineDispatcher = Dispatchers.Default
