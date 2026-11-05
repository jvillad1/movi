package com.jvillada.movi.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install

/**
 * # Las cabeceras de seguridad de toda respuesta (revisión de lanzamiento, nov-2026)
 *
 * La web (el bundle wasm de `staticResources`) salía sin ninguna: se podía montar Movi dentro de un
 * `iframe` ajeno (clickjacking sobre «Borrar mi cuenta» o un «Confirmar») y el navegador quedaba libre
 * de adivinar tipos. Las páginas legales, la compartida, la descarga de papeles y el export ya ponían
 * las suyas; esto pone las básicas en TODO lo demás **sin pisar** lo que una ruta ya dijo (una ruta que
 * pone su `X-Frame-Options` o su `Referrer-Policy` gana).
 *
 * - `X-Content-Type-Options: nosniff` y `X-Frame-Options: DENY` (más `frame-ancestors 'none'` para los
 *   navegadores nuevos, en una CSP que **solo** dice eso: una CSP de scripts rompería el `index.html`,
 *   que tiene scripts en línea, y queda para cuando se saquen a un archivo).
 * - `Referrer-Policy: strict-origin-when-cross-origin`: el `?t=` de una descarga no sale a otro sitio.
 * - `Strict-Transport-Security` de un año, **sin** `includeSubDomains`: `up.railway.app` es un dominio
 *   compartido y no es nuestro decidir por los demás subdominios. Sobre http (desarrollo) el navegador
 *   la ignora.
 */
internal val CABECERAS_DE_SEGURIDAD: List<Pair<String, String>> = listOf(
    "X-Content-Type-Options" to "nosniff",
    "X-Frame-Options" to "DENY",
    "Content-Security-Policy" to "frame-ancestors 'none'",
    "Referrer-Policy" to "strict-origin-when-cross-origin",
    "Strict-Transport-Security" to "max-age=31536000",
)

private val CabecerasDeSeguridad = createApplicationPlugin("CabecerasDeSeguridad") {
    onCallRespond { call, _ ->
        for ((nombre, valor) in CABECERAS_DE_SEGURIDAD) {
            if (call.response.headers[nombre] == null) call.response.headers.append(nombre, valor)
        }
    }
}

fun Application.configureCabecerasDeSeguridad() {
    install(CabecerasDeSeguridad)
}
