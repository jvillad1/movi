package com.jvillada.movi.data

import io.ktor.http.Url

/**
 * **¿Esta respuesta dice que la sesión ya no existe?**
 *
 * El 26-oct-2026 se rotó `JWT_SECRET` en producción y todos los tokens quedaron muertos. La web
 * pidió entrar; Android no: abrió por la huella, pintó Hoy desde el caché, y cada pedido volvía
 * 401 sin que nada cambiara en pantalla. Peor, los Workers que suben los SMS del banco fallaban
 * con el mismo token y nadie se enteraba — el dueño creía estar adentro.
 *
 * Hasta entonces la regla era «tres 401 seguidos», pensada para no echar a nadie por un 401
 * «transitorio» en segundo plano. Ese 401 no existe: el server contesta 401 solo cuando el token
 * no pasa (firma, vencimiento, versión de sesiones — ver `Auth.kt`); si la base falla al mirar la
 * versión, eso es un 500. **Un 401 de Movi con el token guardado es definitivo**, y esperar al
 * tercero solo dejaba más espacio para que algo se interpusiera.
 *
 * La regla, entera:
 * - **sin respuesta** (`codigo == null`: sin red, tiempo agotado) → la sesión sigue. Movi funciona
 *   sin conexión con lo que tiene guardado, y eso no se toca;
 * - cualquier código que no sea 401 (5xx, 403, 404…) → sigue;
 * - un 401 de **otro host** → sigue: no habla de la sesión de Movi;
 * - un 401 de `/api/auth/` → sigue: es una contraseña equivocada, no una sesión que venció (ver
 *   [cuentaComoSesionVencida]);
 * - un 401 de un pedido que **no llevaba token**, o que llevaba **otro** que el guardado → sigue.
 *   El segundo caso es real: un pedido que salió con el token viejo y vuelve después de que el
 *   dueño entró de nuevo no puede cerrar la sesión nueva.
 * - todo lo demás → [LaSesionTras.SE_CERRO].
 */
enum class LaSesionTras { SIGUE, SE_CERRO }

fun laSesionTras(
    codigo: Int?,
    url: Url,
    tokenDelPedido: String?,
    tokenGuardado: String?,
    hostDeMovi: String = hostDeMoviDePrueba ?: hostDe(apiBaseUrl),
): LaSesionTras = when {
    codigo != 401 -> LaSesionTras.SIGUE
    !url.host.equals(hostDeMovi, ignoreCase = true) -> LaSesionTras.SIGUE
    !cuentaComoSesionVencida(url.encodedPath) -> LaSesionTras.SIGUE
    tokenDelPedido.isNullOrBlank() || tokenDelPedido != tokenGuardado -> LaSesionTras.SIGUE
    else -> LaSesionTras.SE_CERRO
}

/** El token que llevaba un pedido en su `Authorization: Bearer …`, o `null`. */
fun tokenDelEncabezado(authorization: String?): String? =
    authorization?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()?.ifBlank { null }

/**
 * La costura de las pruebas que montan el cliente HTTP de verdad contra un server local
 * (`127.0.0.1`): sin ella, su 401 sería «de otro host» y no cerraría nada. `null` en la app.
 */
internal var hostDeMoviDePrueba: String? = null

internal fun hostDe(baseUrl: String): String = runCatching { Url(baseUrl).host }.getOrDefault("")

/**
 * La línea del login cuando fue el server el que cerró la sesión (ver
 * [SessionManager.sesionRechazada]). En Android lo que más se pierde es la captura de los mensajes
 * del banco, así que eso es lo que dice; en iOS y la web no hay captura que nombrar.
 */
fun avisoDeLaSesionRechazada(rechazada: Boolean, conCapturaDelBanco: Boolean): String? = when {
    !rechazada -> null
    conCapturaDelBanco -> "Tu sesión se cerró. Entra de nuevo para seguir capturando los mensajes del banco."
    else -> "Tu sesión se cerró. Entra de nuevo."
}
