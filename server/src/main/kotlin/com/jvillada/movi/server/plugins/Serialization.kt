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
 * un despliegue) tira excepción, y sin `StatusPages` eso llega como un 500 sin mensaje.
 *
 * `ignoreUnknownKeys` no debilita la guarda de claves: esa mira el `JsonObject` crudo, que sigue
 * teniendo todas las claves que llegaron.
 */
val jsonDeLaApi: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}
