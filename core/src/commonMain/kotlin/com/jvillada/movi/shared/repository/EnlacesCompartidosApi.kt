package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.EnlaceCompartido
import com.jvillada.movi.shared.model.EnlaceCompartidoCreado
import com.jvillada.movi.shared.model.NuevoEnlaceCompartido
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * # Los enlaces que el dueño comparte con un tercero
 *
 * **Una clase aparte y no tres métodos más en [WalletRepository]**, a propósito. Esa interfaz tiene
 * media docena de implementaciones —la remota, la local de SQLDelight que la envuelve, el
 * decorador que invalida el Inicio, los dobles de prueba de `:core` y de `:shared`— y cada método
 * nuevo obliga a tocarlas todas. Estos tres no tienen nada que hacer en ninguna de ellas:
 *
 * - **No hay espejo local posible.** El server guarda solo el hash del token; un enlace creado sin
 *   señal no existiría, y uno revocado sin señal seguiría abierto. Offline, la pantalla dice que no
 *   pudo, que es la verdad.
 * - **No mueven plata**, así que no tienen por qué invalidar la caché del Inicio.
 *
 * Se construye igual que [WalletRepositoryImpl]: un `HttpClient` de la plataforma (que ya pone la
 * sesión) y el `baseUrl`.
 */
class EnlacesCompartidosApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Los vigentes del dueño, el más nuevo primero. **Sin token**: ver `EnlaceCompartido`. */
    suspend fun listar(): List<EnlaceCompartido> =
        client.get("$baseUrl/api/enlaces-compartidos").exigirExito().body()

    /**
     * Crea un enlace y devuelve **la URL completa, con el token**: es la única vez que existe.
     *
     * El server manda la ruta relativa (`/compartido#…`) porque no sabe con qué origen lo llamaron;
     * acá se le antepone el `baseUrl`, que en la web es el propio origen y en el teléfono el de
     * producción. Mismo trato que `getDocumentLink`.
     */
    suspend fun crear(pedido: NuevoEnlaceCompartido): EnlaceCompartidoCreado {
        val creado: EnlaceCompartidoCreado = client.post("$baseUrl/api/enlaces-compartidos") {
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }.exigirExito().body()
        return creado.copy(ruta = baseUrl + creado.ruta)
    }

    /** Lo corta en el acto: quien lo tenga deja de ver los datos al recargar. */
    suspend fun revocar(id: String) {
        client.delete("$baseUrl/api/enlaces-compartidos/$id").exigirExito()
    }

    private suspend fun HttpResponse.exigirExito(): HttpResponse {
        if (!status.isSuccess()) throw ApiException(status.value, runCatching { bodyAsText() }.getOrNull())
        return this
    }
}
