package com.jvillada.movi.data

import kotlinx.io.IOException

/**
 * **Un fetch caído en la web, convertido en lo mismo que es en el teléfono: una [IOException].**
 *
 * En wasm, el motor JS de Ktor (3.0.3) rechaza un `fetch` que no llega a ningún lado con
 * `kotlin.Error("Fail to fetch", JsError(...))` —y el cuerpo que se corta a medio leer falla
 * igual—. Es un `Throwable` que **no** es `Exception`. En Android la misma caída es una
 * `IOException`. Resultado: cualquier `catch (e: Exception)` alrededor de una llamada al server
 * la atrapaba en el teléfono y la dejaba pasar en la web, donde subía hasta el Recomposer y
 * dejaba el canvas congelado en su último cuadro, sin decirle nada a nadie.
 *
 * Devuelve la [IOException] que hay que lanzar en lugar de [causa] —con el mensaje original, para
 * que `toUserMessage()` siga reconociendo «Fail to fetch», y con [causa] adentro para no perder
 * nada—, o `null` si [causa] ya es una `Exception` y no hay nada que traducir (incluida la
 * `CancellationException`, que en todas las plataformas es una `Exception` y tiene que seguir
 * siendo ella misma).
 *
 * Solo la usa el cliente HTTP de la web (`Platform.wasmjs.kt`): en la JVM un `Error` de verdad
 * (sin memoria, pila desbordada) no es un problema de red y no hay que disfrazarlo de uno.
 */
fun comoFalloDeRed(causa: Throwable): IOException? =
    if (causa is Exception) null
    else IOException(causa.message ?: "Fail to fetch", causa)
