package com.jvillada.movi.theme

/**
 * **iOS todavía no: falta verlo corriendo, no la versión de Kotlin.**
 *
 * Hasta Kotlin 2.1.21 los recursos de Compose no entraban a un XCFramework, y el plugin lo decía al
 * configurar el build. Desde 2.2 ese aviso ya no aparece, pero que los archivos entren al paquete no
 * alcanza: el framework de Movi es **estático**, y lo que importa es que `Font(recurso)` los
 * encuentre al correr. Si no los encuentra tira `MissingResourceException` adentro de `MoviTheme` y
 * **la app se cae al abrir**.
 *
 * Eso solo se sabe abriendo la app en un iPhone o en el simulador. Al subir a 2.2 no se pudo: en la
 * Mac de desarrollo `xcrun -f ld` falla porque la licencia de Xcode no está aceptada, así que ni
 * siquiera enlaza el framework.
 *
 * Para prenderlo: aceptar la licencia, armar el XCFramework, poner esto en `true` y abrir la app.
 * Si abre y los títulos se ven en Space Grotesk, queda.
 */
internal actual val laPlataformaTraeLasFuentes: Boolean = false
