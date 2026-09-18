package com.jvillada.movi.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse

/**
 * # Un HEAD sobre un archivo que existe no puede contestar 404
 *
 * Medido contra producción: `curl -I /composeApp.js` devolvía **404** mientras el `GET` del mismo
 * archivo contestaba 200 sano, con su `Cache-Control: no-cache` y su `Last-Modified`. Ktor no
 * atiende `HEAD` por su cuenta —una ruta `get` responde solo a GET— así que el pedido caía hasta el
 * final del router y salía por el 404. No es un detalle de protocolo: HEAD es lo que usan los
 * proxys, los verificadores de enlaces y algunos precargadores para preguntar «¿esto existe, y
 * cambió?» sin bajarse los megas del bundle. Contestarles 404 es **mentirles sobre un archivo que
 * está ahí**, y del lado del que pregunta se ve como la app caída.
 *
 * Este plugin es la respuesta de la propia Ktor a eso: convierte cada HEAD en el GET equivalente,
 * deja que la ruta real lo resuelva y después **vacía el cuerpo conservando las cabeceras**
 * (estado, `Content-Type`, `Content-Length`, `Last-Modified`, `Cache-Control`), que es exactamente
 * lo que pide HTTP. Va instalado a nivel aplicación, así que vale para todo el router y no solo
 * para lo estático.
 *
 * **Qué se revisó antes de ponerlo parejo:**
 *
 * - **Las rutas `/api/…`** son lecturas sin efecto: un HEAD ejecuta el mismo `get` y devuelve sus
 *   cabeceras sin cuerpo. Nada se escribe. Lo único que cambia es que un HEAD a una ruta que
 *   existe ya no cae en el 404 JSON de `apiNotFound` — que es el arreglo, no un efecto colateral.
 *   Las que no existen siguen cayendo ahí, porque ese `handle` atiende cualquier método.
 * - **El contenido de un documento** (`GET /api/documents/{id}/content`, fuera del bloque
 *   autenticado) sigue exigiendo su token de descarga: el HEAD corre el mismo handler, así que sin
 *   `?t=` contesta 401 igual que hoy. Lo único que hace de más es leer el blob para después
 *   descartarlo — un costo real pero acotado, contra un enlace de cinco minutos y un solo
 *   documento, y el precio de no tener dos caminos distintos para la misma pregunta.
 * - **Los 304 condicionales** de `configureConditionalHeaders` siguen funcionando: un HEAD con
 *   `If-Modified-Since` contesta 304 sin cuerpo, igual que el GET.
 */
fun Application.configureAutoHead() {
    install(AutoHeadResponse)
}
