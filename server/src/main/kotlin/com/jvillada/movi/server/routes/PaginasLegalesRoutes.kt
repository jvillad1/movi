package com.jvillada.movi.server.routes

import com.jvillada.movi.server.legal.PaginasLegales
import com.jvillada.movi.server.legal.ResponsableDeMovi
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Vuelta 109 · **`/privacidad`, `/terminos` y `/borrar-cuenta`**: las tres URL públicas que piden
 * Google Play y el App Store (ver `legal/PaginasLegales.kt`).
 *
 * Públicas a propósito —las abre un revisor de la tienda o alguien sin cuenta— y por eso van fuera del
 * bloque `authenticate`. No leen nada de nadie: el único dato variable es el responsable y el correo
 * de contacto ([ResponsableDeMovi], de las variables de entorno). `CallLogging` y `MetricasPorRuta`
 * las ven como cualquier ruta pública: plantilla fija, sin token ni dato personal en el camino.
 *
 * Se sirven con `no-cache` (el navegador pregunta antes de reusarlas: un cambio de texto o del correo
 * se ve en el acto) y la política de contenido de la página, que no deja correr ningún script.
 */
fun Route.paginasLegalesRoutes() {
    get(PaginasLegales.RUTA_PRIVACIDAD) {
        call.responderPaginaLegal(PaginasLegales.privacidad(ResponsableDeMovi.leer()))
    }
    get(PaginasLegales.RUTA_TERMINOS) {
        call.responderPaginaLegal(PaginasLegales.terminos(ResponsableDeMovi.leer()))
    }
    get(PaginasLegales.RUTA_BORRAR_CUENTA) {
        call.responderPaginaLegal(PaginasLegales.borrarCuenta(ResponsableDeMovi.leer()))
    }
}

private suspend fun ApplicationCall.responderPaginaLegal(html: String) {
    response.header(HttpHeaders.CacheControl, "no-cache")
    response.header("Content-Security-Policy", PaginasLegales.POLITICA_DE_CONTENIDO)
    response.header("X-Content-Type-Options", "nosniff")
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
}
