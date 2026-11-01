package com.jvillada.movi.observabilidad

/**
 * **La plataforma con que se anota el uso y se reporta un fallo**: `android`, `ios` o `web`.
 */
expect val plataformaDeUso: String

/**
 * **Lo que se sabe de esta instalación**, para los fallos y el uso (vuelta 100). Lo pone el arranque
 * de cada plataforma: `MoviAplicacion` en Android (la versión del APK), `MainViewController` en iOS.
 * En la web queda vacío: la versión de la web es el commit del server.
 */
object InfoDeLaApp {
    var version: String = ""
    /** «samsung SM-A155M», «iPhone». */
    var modelo: String = ""
    /** «Android 14 (API 34)», «iOS 18.0». */
    var sistema: String = ""
}
