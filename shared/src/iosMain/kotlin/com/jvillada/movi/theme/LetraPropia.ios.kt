package com.jvillada.movi.theme

/**
 * **iOS lleva la letra propia.**
 *
 * Hasta Kotlin 2.1.21 los recursos de Compose no entraban a un XCFramework. Desde 2.2 sí, y está
 * comprobado en el paquete armado: `ComposeApp.xcframework/<variante>/ComposeApp.framework/
 * composeResources/com.jvillada.movi.resources/font/` trae las cinco fuentes, en `ios-arm64` y
 * en `ios-arm64_x86_64-simulator`.
 *
 * Y la biblioteca las busca justo ahí: su lector de iOS (`findComposeResourcesPath`) recorre
 * `<app>/Frameworks/*.framework/composeResources` antes de caer a `compose-resources` en la raíz
 * del paquete. Xcode copia la carpeta del framework entera al embeberlo.
 *
 * Si algo de eso fallara, `Font(recurso)` tiraría `MissingResourceException` adentro de
 * `MoviTheme` y la app se cerraría al abrir. Por eso este cambio no se mergea sin abrir la app en
 * un iPhone.
 */
internal actual val laPlataformaTraeLasFuentes: Boolean = true
