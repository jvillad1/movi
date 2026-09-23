package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.periodoDe
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * # Lo último que se supo del Inicio, guardado en el aparato
 *
 * ## El defecto que cierra
 *
 * En el Pixel del dueño, un arranque en frío pintaba 1,8 s de «Tu plata —» con las preguntas
 * genéricas de «Pregúntale a Movi»; a los ~4 s llegaban los datos, la tarjeta saltaba ~270 px y sus
 * preguntas cambiaban mientras se leían. [DashboardDataCache] y `ScreenDefCache` ya pintaban al
 * instante **al volver** al Inicio, pero viven en memoria: cerrar la app o recargar la web los
 * borra, y el arranque en frío —el caso de todas las mañanas— quedaba siempre en blanco.
 *
 * Esto guarda la última [DashboardData] que **salió bien** y la última definición SDUI válida, para
 * que el Inicio arranque con ellas y recargue por detrás. Es una comodidad para la primera pintura,
 * **no una fuente de verdad**: nunca sella `DashboardDataCache.cargadoEn`, así que el Inicio pide
 * las cifras nuevas igual, y la pantalla dice «Actualizando…» mientras tanto.
 *
 * ## Por usuario, y borrada al cerrar sesión
 *
 * La clave lleva el id del usuario ([SessionManager.userId][com.jvillada.movi.data.SessionManager]):
 * en un aparato compartido, el que entra no puede ver ni por un cuadro la plata del anterior. Sin
 * usuario no se lee ni se escribe nada. `SessionManager.clear()` la borra con el id que se va —
 * **antes** de borrar el id, que si no ya no sabría cuál—. El cambio de tema no la toca: es del
 * aparato, no de la sesión.
 *
 * ## Lo que no puede pasar
 *
 * Lo mismo que en `TemaStore` y `LastAccountStore` (leer sus KDoc): `Settings()` explota **al
 * construirse** en una JVM sin contexto o en un navegador con el almacenamiento bloqueado, así que
 * va `by lazy` y todo acceso adentro de un `runCatching`. Y una instantánea que no deserializa —la
 * escribió una versión anterior de la app y el modelo cambió— vale `null`: el Inicio arranca como
 * antes de que esto existiera, sin error ni pantalla rota.
 *
 * La lectura y la escritura entran por parámetro para poder probar la lógica en la JVM pelada, donde
 * `Settings()` no existe; la app usa [delAparato].
 */
class InstantaneaDelInicio(
    private val leer: (clave: String) -> String?,
    private val escribir: (clave: String, valor: String?) -> Unit,
) {
    /** La última carga completa del Inicio de [userId], o `null` si no hay (o no se puede leer). */
    fun datos(userId: String?): DashboardData? =
        decodificar(userId?.takeIf { it.isNotBlank() }?.let { leer(claveDeDatos(it)) }, DashboardData.serializer())

    /**
     * Guarda [data] como la instantánea de [userId]. Quien llama decide **cuándo**: solo con una
     * carga que salió bien (ver el sello en `DashboardScreen`), o la próxima apertura pintaría como
     * «lo último que se supo» una pantalla a medio cargar.
     */
    fun guardarDatos(userId: String?, data: DashboardData) {
        val id = userId?.takeIf { it.isNotBlank() } ?: return
        codificar(data, DashboardData.serializer())?.let { escribir(claveDeDatos(id), it) }
    }

    /** La última definición SDUI válida del Inicio de [userId], o `null`. */
    fun definicion(userId: String?): ScreenDefinition? =
        decodificar(userId?.takeIf { it.isNotBlank() }?.let { leer(claveDeDefinicion(it)) }, ScreenDefinition.serializer())

    fun guardarDefinicion(userId: String?, definicion: ScreenDefinition) {
        val id = userId?.takeIf { it.isNotBlank() } ?: return
        codificar(definicion, ScreenDefinition.serializer())?.let { escribir(claveDeDefinicion(id), it) }
    }

    /**
     * Solo la definición: el editor SDUI la acaba de cambiar en el server, y una guardada haría que
     * el Inicio la pidiera en paralelo en vez de primero — o sea, un cuadro con la vieja.
     */
    fun olvidarDefinicion(userId: String?) {
        val id = userId?.takeIf { it.isNotBlank() } ?: return
        escribir(claveDeDefinicion(id), null)
    }

    /** Al cerrar sesión: las dos cosas de [userId]. */
    fun borrar(userId: String?) {
        val id = userId?.takeIf { it.isNotBlank() } ?: return
        escribir(claveDeDatos(id), null)
        escribir(claveDeDefinicion(id), null)
    }

    private fun <T> codificar(valor: T, serializer: KSerializer<T>): String? =
        runCatching { json.encodeToString(serializer, valor) }.getOrNull()

    private fun <T> decodificar(crudo: String?, serializer: KSerializer<T>): T? {
        if (crudo.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(serializer, crudo) }.getOrNull()
    }

    companion object {
        /** La de la app: el `Settings` del aparato (SharedPreferences, NSUserDefaults, localStorage). */
        private val real = InstantaneaDelInicio(
            leer = { clave -> runCatching { instantaneaSettings.getStringOrNull(clave) }.getOrNull() },
            escribir = { clave, valor ->
                runCatching {
                    if (valor == null) instantaneaSettings.remove(clave) else instantaneaSettings[clave] = valor
                }
            },
        )

        /**
         * La costura de las pruebas de pantalla, como `Repositories.sustitutoDePrueba`: en
         * Robolectric `Settings()` tampoco se construye (nadie corre su inicializador), así que sin
         * esto ninguna prueba podría montar el Inicio con una instantánea guardada. `AppDePrueba` la
         * vuelve a `null` antes de cada método, **después** del logout que la vacía.
         */
        internal var sustitutoDePrueba: InstantaneaDelInicio? = null

        /** La que usa la app. Ver [sustitutoDePrueba]. */
        val delAparato: InstantaneaDelInicio get() = sustitutoDePrueba ?: real

        private fun claveDeDatos(userId: String) = "inicio_instantanea_$userId"
        private fun claveDeDefinicion(userId: String) = "inicio_definicion_$userId"
    }
}

/**
 * La instantánea con el período recalculado para [ahora] según sus propios ajustes de corte.
 *
 * Una instantánea del 24 leída el 25 traería el período que ya terminó, y si en esa carga falla el
 * perfil (que es lo que lo recalcula) el Inicio seguiría hablando del período anterior. El corte sí
 * se puede confiar a la instantánea —cambia casi nunca—; la fecha de hoy no. Si la instantánea no
 * sabía el período (el perfil nunca contestó), se deja en `null`: no se inventa uno.
 */
fun DashboardData.conElPeriodoDe(ahora: Long): DashboardData =
    if (periodoActual == null) this else copy(periodoActual = periodoDe(ahora, ajustesDePeriodo))

/**
 * `ignoreUnknownKeys`: una instantánea escrita por una versión POSTERIOR (volver a un APK anterior)
 * trae campos que esta no conoce, y eso no es motivo para perderla.
 */
private val json = Json { ignoreUnknownKeys = true }

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val instantaneaSettings: Settings by lazy { Settings() }
