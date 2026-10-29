package com.jvillada.movi.platform

/**
 * **¿Un lector de pantalla puede leer Movi en esta plataforma?** (vuelta 55, accesibilidad)
 *
 * En Android (TalkBack) y en iOS (VoiceOver) sí: Compose les pasa la semántica de cada pantalla. En la
 * web no: la app entera es un `<canvas>` y Compose Multiplatform 1.8 —la que usa Movi— no expone su
 * semántica al DOM. La 1.9 trae una accesibilidad web experimental (`ComposeViewport` con
 * `isA11YEnabled`), pero exige dejar `CanvasBasedWindow` y subir todo Compose de versión; no es un
 * cambio menor, así que mientras tanto la web lo dice (el `index.html` al lector, y Ajustes a la vista).
 */
expect val elLectorDePantallaLeeLaApp: Boolean

/**
 * Lo que la web le dice a quien usa un lector de pantalla. El mismo texto va oculto a la vista en
 * `webApp/.../index.html` (lo primero que oye el lector) y a la vista en Ajustes → «Cómo se ve».
 */
const val LECTOR_DE_PANTALLA_EN_LA_WEB: String =
    "Movi en la web todavía no funciona con lectores de pantalla. Usa la app de Android."

/** La nota de Ajustes → «Cómo se ve»: solo donde el lector no lee la app. Pura. */
fun notaDelLectorDePantalla(leeLaApp: Boolean): String? = if (leeLaApp) null else LECTOR_DE_PANTALLA_EN_LA_WEB
