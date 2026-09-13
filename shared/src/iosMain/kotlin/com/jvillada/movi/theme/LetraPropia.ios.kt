package com.jvillada.movi.theme

/**
 * **iOS todavía no, y no por gusto.** Movi entrega iOS como XCFramework, y el plugin de Compose lo
 * dice textual al configurar el build: los recursos entran a un XCFramework recién desde Kotlin
 * 2.2.0. Movi está en 2.1.21.
 *
 * Sin esta guarda, `Font(recurso)` buscaría un archivo que no está en el paquete de la app, tiraría
 * `MissingResourceException` adentro de `MoviTheme`, y **la app se caería al abrir**. Con la guarda,
 * iOS usa San Francisco, que es lo que venía usando.
 *
 * Cuando Kotlin suba a 2.2, esto pasa a `true` y hay que abrirla en el simulador para verlo.
 */
internal actual val laPlataformaTraeLasFuentes: Boolean = false
