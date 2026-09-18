package com.jvillada.movi.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * Config de los DOS sensores de captura del teléfono: el filtro de SMS bancarios y la lista de
 * apps cuyas notificaciones se leen (`appPackages`, abajo). Un solo endpoint para los dos a
 * propósito — el cliente lo cachea una vez, con un TTL y un Worker de refresco.
 *
 * Fuente única: editar estas
 * constantes + deploy web = el filtro cambia en los teléfonos SIN reinstalar APK
 * (el receiver la cachea con TTL 24h). OJO: los defaults compilados en el APK
 * (BankSenderFilter.DEFAULTS) son un PISO, no un espejo — el cliente siempre une esta
 * config con esos defaults (SmsFilterConfigStore.withDefaults). Por eso AGREGAR un
 * código o keyword acá sí llega a los teléfonos, pero QUITAR uno del piso compilado
 * (los códigos 85540, 891333, 87400 y la keyword "bancolombia") NO deja de capturarlo
 * — el piso sigue vivo hasta el próximo release del APK.
 *
 * Para revertir lo que agregaste acá, volvé a servir el piso explícitamente en vez de
 * listas vacías: el cliente ignora una config totalmente vacía (no pisa su último cache
 * bueno con nada), así que servir `[]` no borra nada y además lo deja refetcheando.
 * Pública a propósito: solo contiene códigos de remitentes bancarios, nada sensible.
 */
@Serializable
private data class SmsFilterConfig(
    val senderCodes: List<String>,
    val bodyKeywords: List<String>,
    /**
     * **Las apps cuyas NOTIFICACIONES se capturan** — el hermano del filtro de SMS, servido por el
     * mismo endpoint a propósito: un endpoint, un cache y un Worker de refresco para los dos
     * sensores (ver `SmsFilterConfigStore.loadNotificationApps`).
     *
     * **Esta lista sí se puede achicar desde acá, al revés que las dos de arriba.** Los códigos de
     * remitente compilados en el APK son un piso que el server solo puede ampliar; estos paquetes,
     * en cambio, el cliente los REEMPLAZA con lo que llegue acá —incluida una lista vacía, que
     * apaga la captura por notificaciones en todos los teléfonos—. El motivo está en el KDoc de
     * `AppsQueAvisan`: los paquetes compilados son conjeturas, y una conjetura equivocada no
     * captura de menos sino de más, así que quitarla no puede exigir un APK nuevo.
     *
     * Un APK viejo, que no conoce esta clave, la ignora y sigue capturando solo SMS.
     */
    val appPackages: List<String>,
)

/**
 * **`nubank` y no `nu`.** El cliente compara por SUBSTRING (`lower.contains(keyword)`, ver
 * `BankSenderFilter`), así que `nu` coincidiría con «número», «nuevo», «nunca», «continuar» —
 * es decir, con casi cualquier SMS en español. Eso no sería ruido: los mensajes que pasan el
 * filtro se SUBEN al server, así que una keyword corta le manda la bandeja personal entera.
 * Cualquier keyword nueva tiene que ser lo bastante larga para no aparecer dentro de palabras
 * comunes.
 *
 * Falta el código de remitente de Nu: sin un SMS suyo a la vista no se sabe cuál es, y adivinarlo
 * no cuesta nada pero tampoco captura nada. Mientras tanto esta keyword cubre los mensajes que
 * digan «Nubank» en el cuerpo.
 */
private val CURRENT_FILTER = SmsFilterConfig(
    senderCodes = listOf("85540", "891333", "87400"),
    bodyKeywords = listOf("bancolombia", "nubank"),
    // **Paquetes exactos, y hoy conjeturados.** `com.todo1.mobile` es el paquete publicado de
    // «Bancolombia App Personas» (Todo1 es su proveedor móvil), pero no está verificado contra el
    // teléfono del dueño: cuando esto se escribió el teléfono estaba desconectado. El de Glim no se
    // conoce — apenas se lea (Ajustes → Apps → Glim, o `adb shell pm list packages`), se agrega
    // acá y se despliega la web: los teléfonos lo toman en 24 h sin reinstalar el APK.
    //
    // El cliente compara por IGUALDAD, no por substring, así que acá no cabe ni un prefijo ni un
    // «bancolombia» suelto: tiene que ser el nombre de paquete completo.
    appPackages = listOf("com.todo1.mobile"),
)

fun Route.smsFilterConfigRoutes() {
    get("/api/sms/filter-config") { call.respond(CURRENT_FILTER) }
}
