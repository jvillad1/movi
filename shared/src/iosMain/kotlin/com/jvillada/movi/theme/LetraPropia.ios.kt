package com.jvillada.movi.theme

import com.jvillada.movi.fallos.reportarAtrapado
import com.jvillada.movi.resources.Res
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * # Figtree en el iPhone (vuelta 115)
 *
 * **Dónde las busca Compose.** El `ComposeApp.framework` de Movi es **estático**: no lleva los
 * recursos adentro. Compose 1.8 (`components-resources`, `ResourceReader.ios.kt`) busca primero una
 * carpeta `composeResources` dentro de algún `Frameworks/<nombre>.framework` de la app (eso es para un
 * framework dinámico) y, si no hay, en `NSBundle.mainBundle.resourcePath + "/compose-resources"`.
 * La ruta de cada fuente se le pega detrás, así que en la app quedan en
 * `iosApp.app/compose-resources/composeResources/com.jvillada.movi.resources/font/figtree_regular.ttf`
 * (y los otros cuatro pesos).
 *
 * **Quién las pone ahí.** El plugin de Compose solo las copia con `embedAndSignAppleFrameworkForXcode`
 * o con CocoaPods; Movi usa un XCFramework (`MOVI_XCFRAMEWORK_DIR`, vuelta 112). Entonces:
 * `:shared:recursosDeComposeParaIphone<Debug|Release>` (corre solo después de
 * `assembleComposeApp…XCFramework`) las deja junto al XCFramework, y la fase «Copiar las fuentes de
 * Movi» del proyecto de Xcode las copia a la app. Si no las encuentra, el build de Xcode falla y lo
 * dice: nunca sale una app sin fuentes sin que nadie se entere. Lo vigila `LasFuentesDelIphoneTest`.
 *
 * **La red de seguridad.** `Font(recurso)` lee el archivo mientras compone, y en iOS un archivo que
 * falta es `MissingResourceException` adentro de `MoviTheme`: **la app se caería al abrir**. Un
 * `try` no puede rodear una llamada `@Composable`, así que se pregunta ANTES, sin componer: por cada
 * fuente, `Res.getUri` (la MISMA cuenta de ruta que hace Compose al leerla, no una copia) y si el
 * archivo existe. Si falta alguna, Movi usa la letra del sistema, se ve igual de bien que antes y el
 * hecho queda en los fallos atrapados (`reportarAtrapado`, componente `LetraPropia`), que suben al
 * próximo arranque. Se pregunta una vez por proceso.
 */
internal actual val laPlataformaTraeLasFuentes: Boolean by lazy { lasFuentesEstanEnLaApp() }

/** Cuando faltan fuentes en el paquete: va al reporte de fallos, nunca rompe nada. */
internal class FuentesQueFaltanEnLaApp(faltan: List<String>) :
    IllegalStateException("Faltan fuentes de Movi en la app: ${faltan.joinToString()} (se usa la letra del sistema)")

private fun lasFuentesEstanEnLaApp(): Boolean {
    val faltan = try {
        ARCHIVOS_DE_FIGTREE.filterNot { archivo ->
            val ruta = NSURL.URLWithString(Res.getUri("font/$archivo"))?.path
            ruta != null && NSFileManager.defaultManager.fileExistsAtPath(ruta)
        }
    } catch (e: Throwable) {
        println("Movi: no se pudo revisar las fuentes ($e); se usa la letra del sistema")
        reportarAtrapado(e, componente = "LetraPropia")
        return false
    }
    if (faltan.isEmpty()) return true
    val error = FuentesQueFaltanEnLaApp(faltan)
    println("Movi: ${error.message}")
    reportarAtrapado(error, componente = "LetraPropia")
    return false
}
