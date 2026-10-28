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
 * **Vuelta 29 (Figtree), medido:** la licencia de Xcode ya está aceptada y `linkDebugFrameworkIosX64`
 * enlaza, y `assembleIosX64MainResources` arma los cinco `figtree_*.ttf` en
 * `build/generated/compose/resourceGenerator/assembledResources/iosX64Main/composeResources/`. Pero
 * **el `ComposeApp.framework` enlazado no los trae adentro** (solo el binario, `Headers`, `Modules` e
 * `Info.plist`), y el proyecto de Xcode no tiene un paso que copie `composeResources` al paquete de la
 * app. Prender esto sin ese paso es la caída al abrir de arriba.
 *
 * Para prenderlo: sumar al proyecto de Xcode la copia de `composeResources` al paquete de la app (o
 * pasar al `embedAndSignAppleFrameworkForXcode`, que la hace), poner esto en `true` y abrir la app en
 * el simulador. Si abre y los títulos se ven en Figtree, queda.
 */
internal actual val laPlataformaTraeLasFuentes: Boolean = false
