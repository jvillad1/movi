package com.jvillada.movi.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders

/**
 * # La otra mitad del `Cache-Control` del bundle
 *
 * `configureRouting` le pone `no-cache` a todo lo estático: el navegador puede guardarlo, pero
 * tiene que **preguntar** antes de reusarlo. Eso resuelve el problema de quedarse con la app de
 * antes después de un despliegue… y, solo, crea otro: sin una etiqueta con la cual preguntar, la
 * respuesta a cada pregunta es el archivo entero. El bundle wasm pesa megas, y se bajaría completo
 * en cada visita.
 *
 * Ktor calcula la versión de cada archivo estático (fecha del recurso, y ETag donde lo tiene),
 * pero **no manda ninguna cabecera con ella hasta que este plugin está instalado** — verificado:
 * antes de esto, `GET /composeApp.js` contestaba sin `Last-Modified` y sin `ETag`. Con el plugin,
 * la petición condicional del navegador (`If-Modified-Since`) se contesta **304 sin cuerpo**
 * cuando nada cambió, y con el archivo nuevo cuando sí.
 *
 * Toca solo a las respuestas que traen versión, que hoy son las estáticas: el JSON de `/api/…` no
 * lleva ninguna y sale igual que siempre.
 */
fun Application.configureConditionalHeaders() {
    install(ConditionalHeaders)
}
