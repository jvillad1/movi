package com.jvillada.movi.server.plugins

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.CompressionConfig
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize

/**
 * # Las respuestas viajan comprimidas (vuelta 78)
 *
 * Medido en la revisión 6 contra la API local: `/api/events/by-day` pesaba **61,9 KB** con la sangría
 * del JSON; sin sangría, 35,8 KB; con gzip, **5,6 KB**. En el teléfono, contra Railway y con datos
 * móviles, cada KB es tiempo y plata del dueño. El JSON ya sale compacto (ver `configureSerialization`)
 * y esto lo comprime cuando el cliente dice que lo acepta (`Accept-Encoding`).
 *
 * **Qué se comprime:** el JSON de la API y el texto —el HTML de la SPA y de `/compartido`, el CSS y el
 * JavaScript del bundle—, solo si pasa de [TAMANO_MINIMO_PARA_COMPRIMIR] (por debajo, la cabecera del
 * gzip pesa más de lo que ahorra). **Qué no:** lo binario o ya comprimido —el `.wasm`
 * (`application/wasm`), las fuentes, las imágenes y los PDF de un documento—: el `matchContentType` solo
 * deja pasar los tipos de arriba.
 *
 * **Solo respuestas** ([CompressionConfig.Mode.CompressResponse]). El plugin de Ktor también sabe
 * DEScomprimir los pedidos, y eso no se quiere: el webhook de Resend firma el cuerpo crudo
 * (`FirmaDeSvix`) y lo lee tal cual llegó. Así ninguna ruta ve un cuerpo distinto del que se mandó.
 *
 * **Los clientes no hacen nada**: el navegador (fetch), Android (`HttpURLConnection`, que pide gzip y lo
 * abre solo) y iOS (`NSURLSession`, igual) mandan `Accept-Encoding` y descomprimen por su cuenta. Un
 * cliente que no lo mande (las pruebas, `curl` sin `--compressed`) recibe el JSON plano, como siempre.
 */
fun Application.configureCompression() {
    install(Compression) {
        mode = CompressionConfig.Mode.CompressResponse
        gzip {
            priority = 1.0
            minimumSize(TAMANO_MINIMO_PARA_COMPRIMIR)
            matchContentType(*TIPOS_QUE_SE_COMPRIMEN)
        }
        deflate {
            priority = 0.9
            minimumSize(TAMANO_MINIMO_PARA_COMPRIMIR)
            matchContentType(*TIPOS_QUE_SE_COMPRIMEN)
        }
    }
}

/** Por debajo de esto, la respuesta va tal cual: ~1 KB, donde la cabecera del gzip ya no compensa. */
const val TAMANO_MINIMO_PARA_COMPRIMIR: Long = 1024L

/** El JSON de la API y el texto del bundle y de las páginas; nada binario (ver el KDoc de arriba). */
private val TIPOS_QUE_SE_COMPRIMEN: Array<ContentType> = arrayOf(
    ContentType.Application.Json,
    ContentType.Text.Any,
    ContentType.Application.JavaScript,
)
