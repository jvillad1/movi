package com.jvillada.movi.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * Config del filtro de SMS bancarios del APK sensor. Fuente única: editar estas
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
private data class SmsFilterConfig(val senderCodes: List<String>, val bodyKeywords: List<String>)

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
)

fun Route.smsFilterConfigRoutes() {
    get("/api/sms/filter-config") { call.respond(CURRENT_FILTER) }
}
