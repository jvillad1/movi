package com.jvillada.movi.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * **La misma configuración que usa el `ContentNegotiation`**, expuesta para las rutas que
 * deserializan a mano.
 *
 * `CardRoutes` y `CreditRoutes` reciben el `JsonObject` crudo además del objeto —es la única forma
 * de distinguir «el cliente mandó este campo» de «el cliente no lo conoce»— y después lo decodifican
 * ellas mismas. Con la instancia por defecto de `Json` eso quedaba **más estricto que el resto de
 * la API**: una clave desconocida (un cliente más nuevo que el server, que es el sentido normal de
 * un despliegue) tiraba excepción, y antes de `configureStatusPages` eso llegaba como un 500 sin mensaje.
 *
 * `ignoreUnknownKeys` no debilita la guarda de claves: esa mira el `JsonObject` crudo, que sigue
 * teniendo todas las claves que llegaron.
 */
val jsonDeLaApi: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

/**
 * Vuelta 78: **el JSON sale compacto**, sin sangría. Con `prettyPrint` la historia de movimientos
 * (`/api/events/by-day`) pesaba 61,9 KB, y 35,8 KB sin los espacios: el 42 % de cada respuesta eran
 * blancos que ningún cliente lee. Para mirarlo a mano, `curl … | jq`. Va junto con la compresión
 * (`configureCompression`).
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}
