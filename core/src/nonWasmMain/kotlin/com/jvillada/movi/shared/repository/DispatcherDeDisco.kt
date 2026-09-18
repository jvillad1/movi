package com.jvillada.movi.shared.repository

import kotlinx.coroutines.CoroutineDispatcher

/**
 * **El pool donde se bloquea esperando disco**, que en los tres clientes es `Dispatchers.IO`.
 *
 * Es `expect`/`actual` y no un `Dispatchers.IO` escrito una sola vez porque `IO` no existe en el
 * denominador común de `nonWasmMain`: kotlinx-coroutines lo declara por plataforma (JVM y Native),
 * no en el juego de declaraciones que comparten JVM, Android e iOS. Escribirlo derecho compila el
 * JVM y rompe la compilación de metadatos —o sea, iOS— con «Unresolved reference 'IO'».
 *
 * Lo usa [LocalRepository] como valor por omisión de su dispatcher. Ver el KDoc de esa clase para
 * el porqué: sin esto, cada lectura del espejo local corría en el hilo que pinta.
 */
internal expect fun dispatcherDeDisco(): CoroutineDispatcher
