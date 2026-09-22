package com.jvillada.movi.notificaciones

import org.json.JSONObject
import java.security.MessageDigest

/**
 * # Movi lee las notificaciones de las apps del banco
 *
 * ## El defecto que cierra
 *
 * El banco dejó de mandar SMS. El mensaje de Bancolombia más nuevo del teléfono del dueño es del
 * 15-sep 20:26, y su cuenta tuvo movimientos el 16, el 17 y el 18: esos tres los tuvo que teclear a
 * mano mirando capturas de pantalla. La tarjeta de alimentación Glim nunca mandó un SMS en su vida.
 * Las dos apps, en cambio, **sí publican una notificación de Android por cada movimiento**.
 *
 * Así que esto es el hermano del sensor de SMS, no una copia: reusa el MISMO uploader
 * (`SmsSync.postSmsSync` → `POST /api/sms/sync`), el MISMO Worker (`SmsSyncWorker`), la MISMA
 * bandeja («Mensajes del banco») y el MISMO parser del server. Superficie nueva del server: cero.
 *
 * ## La regla de privacidad, que es lo único no negociable acá
 *
 * Un `NotificationListenerService` ve **todas** las notificaciones del teléfono: WhatsApp, correo,
 * mensajes de la pareja. Por eso la decisión se toma en este orden y el primer paso es el paquete:
 * una notificación de una app que no está en la lista **no se lee, no se cuenta, no se registra y
 * no sale del teléfono**. Ni siquiera se le miran los `extras`. Ver [decidirNotificacion] y el
 * `onNotificationPosted` del servicio.
 *
 * ## Por qué la lista NO es un piso, a diferencia del filtro de SMS
 *
 * `BankSenderFilter.DEFAULTS` es un PISO: el server solo puede agregar remitentes, nunca quitarlos,
 * porque quedarse ciego (perder un movimiento) es peor que capturar de más, y esos 3 códigos son
 * remitentes bancarios verificados.
 *
 * Acá la aritmética se da vuelta. Los paquetes de abajo son **conjeturas**: cuando esto se escribió
 * el teléfono del dueño estaba desconectado y nadie pudo leer el nombre real del paquete de su app
 * de Bancolombia ni el de Glim. Una conjetura equivocada no es «no captura nada»: es «captura las
 * notificaciones de OTRA app». Si eso pasa, el remedio tiene que ser una línea en el server, no un
 * APK nuevo. Por eso [appsQueAvisan] **reemplaza** la lista en vez de unirla: el server puede
 * agregar Y quitar. Los defaults son solo el fallback de cuando no hay cache (ver
 * `SmsFilterConfigStore.loadNotificationApps`).
 */
data class AppsQueAvisan(val paquetes: List<String>)

/** Por qué una notificación no se sube. Existe para poder afirmarlo en una prueba. */
enum class MotivoDeDescarte {
    /** La app no está en la lista. **Es el único que se decide sin mirar el contenido.** */
    APP_FUERA_DE_LA_LISTA,

    /** Persistente/en curso: la barra de progreso de una transferencia, no el resultado. */
    PERSISTENTE,

    /** El cabezal que Android arma cuando una app agrupa varias: no trae el movimiento. */
    RESUMEN_DE_GRUPO,

    /**
     * Sin cuerpo. Un título solo («Bancolombia») no dice cuánta plata se movió: no hay parser que
     * lo vuelva un movimiento, así que subirlo es peor que no subirlo —ensucia la bandeja del
     * dueño con una fila que solo se puede borrar a mano—. Una descartada no llega al Worker, y
     * por lo tanto tampoco corre la marca de «última captura»: ver el KDoc de
     * `AlmacenDeNotificaciones.marcarUltima`.
     */
    SIN_TEXTO,
}

sealed interface DecisionDeNotificacion {
    /** [texto] es lo que va al campo `text` del wire — o sea, lo que el parser del server lee. */
    data class Subir(val texto: String) : DecisionDeNotificacion

    data class Descartar(val motivo: MotivoDeDescarte) : DecisionDeNotificacion
}

/**
 * Lo que el servicio le pasa a la decisión. Deliberadamente NO es un `StatusBarNotification`: así
 * la regla es una función pura que se prueba sin Android, igual que `BankSenderFilter.matches`.
 *
 * @param cuando el `when` de la notificación (epoch-ms) — el mismo reloj que `System.currentTimeMillis`.
 */
data class NotificacionEntrante(
    val paquete: String,
    val titulo: String,
    val texto: String,
    val esPersistente: Boolean,
    val esResumenDeGrupo: Boolean,
    val cuando: Long,
)

/**
 * Tope del texto que se sube. Un SMS son 160 caracteres; una notificación puede traer un
 * `bigText` de varios párrafos. El movimiento siempre está en las primeras líneas, y el texto es
 * además la clave de dedupe del server: subir kilobytes no compra nada.
 */
internal const val MAX_TEXTO_DE_NOTIFICACION = 500

/** Tope del campo `bank` del wire (la columna `bank` es varchar(100)). */
internal const val MAX_MARCA_DE_ORIGEN = 100

object FiltroDeNotificaciones {

    /**
     * **El fallback de cuando no hay cache**, no la lista que manda: la que manda es la del server
     * (`SmsFilterConfigRoutes.CURRENT_FILTER`), que la REEMPLAZA en cuanto el teléfono la baja una
     * vez. Por eso agregar una app —como Nu— es editar el server y desplegar la web, sin APK.
     *
     * Hasta el APK 1.35 esto decía `com.todo1.mobile`, una conjetura que resultó falsa (Todo1 es el
     * proveedor de Davivienda). Ahora es el espejo de los paquetes leídos del teléfono del dueño el
     * 19-sep, más Nu: así un teléfono que todavía no bajó la config tampoco queda ciego.
     *
     * Leé el KDoc de [AppsQueAvisan] antes de convertir esto en un piso al estilo de
     * `BankSenderFilter.DEFAULTS`: acá el server TIENE que poder quitar.
     */
    val DEFAULTS = AppsQueAvisan(
        paquetes = listOf(
            "co.com.bancolombia.personas.superapp",
            "com.app.prontomas",
            "com.google.android.apps.walletnfcrel",
            "com.nu.production",
        ),
    )

    /** Igualdad exacta de paquete, nunca `contains`: `com.malo.com.todo1.mobile` no es Bancolombia. */
    fun laAppEstaEnLaLista(paquete: String, config: AppsQueAvisan = DEFAULTS): Boolean =
        config.paquetes.any { it.isNotBlank() && it == paquete }
}

/**
 * **La regla completa, y en este orden.** El paquete primero: ver el KDoc de [AppsQueAvisan].
 *
 * Los tres descartes de contenido son contra el ruido, no contra la privacidad — una app de banco
 * repite, actualiza y reemplaza sus notificaciones, y cada re-publicación vuelve a entrar por acá.
 * Lo que atrapa la repetición exacta es la memoria del teléfono ([recordarHuella]), que es un paso
 * aparte porque necesita estado.
 */
fun decidirNotificacion(
    notificacion: NotificacionEntrante,
    config: AppsQueAvisan = FiltroDeNotificaciones.DEFAULTS,
): DecisionDeNotificacion {
    if (!FiltroDeNotificaciones.laAppEstaEnLaLista(notificacion.paquete, config)) {
        return DecisionDeNotificacion.Descartar(MotivoDeDescarte.APP_FUERA_DE_LA_LISTA)
    }
    if (notificacion.esPersistente) {
        return DecisionDeNotificacion.Descartar(MotivoDeDescarte.PERSISTENTE)
    }
    if (notificacion.esResumenDeGrupo) {
        return DecisionDeNotificacion.Descartar(MotivoDeDescarte.RESUMEN_DE_GRUPO)
    }
    if (notificacion.texto.isBlank()) {
        return DecisionDeNotificacion.Descartar(MotivoDeDescarte.SIN_TEXTO)
    }
    return DecisionDeNotificacion.Subir(textoDeLaNotificacion(notificacion.titulo, notificacion.texto))
}

/**
 * **Los extras de Android de los que puede salir el título o el cuerpo.** Los valores son los
 * mismos de `Notification.EXTRA_*`, escritos acá como literales para que [cuerpoDeLaNotificacion]
 * sea una función pura —sin `Bundle` ni `android.jar` de por medio— y se pueda fijar en una prueba
 * común. `FiltroDeNotificacionesTest` las compara contra las constantes reales: si alguna se
 * desincroniza, el cuerpo volvería a llegar vacío y eso tiene que romper una prueba, no un mes de
 * capturas.
 */
object LlavesDeNotificacion {
    /** `Notification.EXTRA_TITLE`. */
    const val TITULO = "android.title"

    /** `Notification.EXTRA_TITLE_BIG`: el título que reemplaza al otro cuando la alerta se expande. */
    const val TITULO_GRANDE = "android.title.big"

    /** `Notification.EXTRA_TEXT`: el renglón corto de la vista colapsada. */
    const val TEXTO = "android.text"

    /** `Notification.EXTRA_BIG_TEXT`: el texto completo de una `BigTextStyle`. */
    const val TEXTO_GRANDE = "android.bigText"

    /** `Notification.EXTRA_TEXT_LINES`: las líneas de una `InboxStyle` (un `Array<CharSequence>`). */
    const val LINEAS = "android.textLines"

    /** `Notification.EXTRA_SUMMARY_TEXT`: el resumen de la alerta. */
    const val RESUMEN = "android.summaryText"
}

/** El orden en que se busca el cuerpo. El porqué de cada escalón, en [cuerpoDeLaNotificacion]. */
private val ORDEN_DEL_CUERPO = listOf(
    LlavesDeNotificacion.TEXTO_GRANDE,
    LlavesDeNotificacion.TEXTO,
    LlavesDeNotificacion.LINEAS,
    LlavesDeNotificacion.RESUMEN,
)

/**
 * **De dónde sale el cuerpo que se sube, y en qué orden.** [extras] es el bundle de la notificación
 * pasado a mapa plano: plano a propósito, para que esta regla —como [decidirNotificacion]— se
 * pruebe sin Android.
 *
 * 1. **[LlavesDeNotificacion.TEXTO_GRANDE] primero**, y esto es lo importante: las alertas del banco
 *    son largas —«Compraste $12.345,00 en PRUEBA DE MOVI con tu T.Deb *4057, el 21/09/2026 a las
 *    14:00.»— y Android guarda la cadena entera ahí, mientras que `EXTRA_TEXT` lleva el renglón de
 *    la vista colapsada, que la app recorta (a veces con un «…») para que entre en una línea. Leer
 *    el corto primero le comería al parser el comercio, la tarjeta o la fecha —justo lo que hace
 *    que un mensaje se vuelva un movimiento— y nadie lo notaría: llegaría una fila, solo que mocha.
 * 2. [LlavesDeNotificacion.TEXTO] cuando no hay texto grande, que es la forma más común.
 * 3. [LlavesDeNotificacion.LINEAS] para las apps que juntan varios avisos en una `InboxStyle`. Se
 *    unen en UNA línea porque el parser del server lee un SMS, y un SMS es una sola.
 * 4. [LlavesDeNotificacion.RESUMEN] al final: casi siempre es un rótulo («3 movimientos nuevos»),
 *    pero un rótulo con plata adentro es más que nada.
 *
 * Cada escalón se saltea si viene **en blanco**, no solo si viene nulo. Un `?:` sobre nulos no
 * alcanza: una app que publica `bigText("")` junto a un `text` bueno existe, y ahí el elvis se
 * quedaría con el vacío y [decidirNotificacion] tiraría un movimiento de verdad por [MotivoDeDescarte.SIN_TEXTO].
 */
fun cuerpoDeLaNotificacion(extras: Map<String, Any?>): String =
    ORDEN_DEL_CUERPO.firstNotNullOfOrNull { llave -> comoTextoPlano(extras[llave]).ifBlank { null } }.orEmpty()

/**
 * El título, con su propia caída: una notificación expandida puede traerlo solo en
 * [LlavesDeNotificacion.TITULO_GRANDE]. Que quede vacío no rompe nada —[textoDeLaNotificacion] sube
 * el cuerpo tal cual—; el que no puede quedar vacío es el cuerpo.
 */
fun tituloDeLaNotificacion(extras: Map<String, Any?>): String =
    comoTextoPlano(extras[LlavesDeNotificacion.TITULO])
        .ifBlank { comoTextoPlano(extras[LlavesDeNotificacion.TITULO_GRANDE]) }

/**
 * Un valor del bundle, como texto. Android guarda `CharSequence`, no `String` —un `SpannableString`
 * con el monto en negrita es lo normal—, así que se aplana; un arreglo o una lista (las líneas de
 * una `InboxStyle`) se juntan en una sola línea salteando las vacías; cualquier otra cosa no es
 * texto y no se inventa.
 */
private fun comoTextoPlano(valor: Any?): String = when (valor) {
    null -> ""
    is CharSequence -> valor.toString().trim()
    is Array<*> -> valor.mapNotNull { comoTextoPlano(it).ifBlank { null } }.joinToString(" ")
    is Iterable<*> -> valor.mapNotNull { comoTextoPlano(it).ifBlank { null } }.joinToString(" ")
    else -> ""
}

/**
 * Título + cuerpo en una sola línea, que es la forma en que el parser del server lee un SMS.
 *
 * El título se antepone porque muchas apps ponen ahí la mitad de la frase («Compra aprobada» /
 * «$28.500 en Rappi»). Si el cuerpo ya empieza con el título no se repite: eso solo le daría al
 * dedupe por texto una cadena distinta para el mismo hecho.
 */
fun textoDeLaNotificacion(titulo: String, texto: String): String {
    val t = titulo.trim()
    val c = texto.trim()
    val junto = when {
        t.isEmpty() -> c
        c.startsWith(t, ignoreCase = true) -> c
        else -> "$t: $c"
    }
    return junto.take(MAX_TEXTO_DE_NOTIFICACION)
}

/**
 * **De dónde vino esta fila de la bandeja.** Va al campo `bank`, que es el rótulo que «Mensajes del
 * banco» pinta arriba de cada mensaje; con los SMS ahí va el código del remitente («85540»).
 *
 * El `bank` NO entra en la clave de dedupe del server (que es texto + tiempo, ver `SmsDedupe`), así
 * que marcar el origen acá no puede partir en dos un movimiento que ya llegó por SMS.
 */
fun marcaDeOrigen(etiquetaDeLaApp: String): String {
    val nombre = etiquetaDeLaApp.trim().ifBlank { "otra app" }
    return "Notificación · $nombre".take(MAX_MARCA_DE_ORIGEN)
}

/**
 * Lo que identifica «esta misma notificación otra vez»: app, título, cuerpo y el MINUTO.
 *
 * El minuto y no el instante porque una app que actualiza su notificación (progreso → resultado, o
 * un simple re-post al desbloquear el teléfono) la vuelve a publicar con un `when` corrido unos
 * milisegundos. Y el minuto y no «para siempre» porque dos cobros idénticos y reales existen: un
 * doble swipe en el POS produce dos notificaciones byte por byte iguales, y colapsarlas sería
 * perder plata en silencio — el mismo criterio, y por el mismo motivo, que `SMS_DEDUPE_TOLERANCE`.
 */
fun huellaDeLaNotificacion(paquete: String, titulo: String, texto: String, cuando: Long): String =
    "$paquete|${titulo.trim()}|${texto.trim()}|${cuando / 60_000L}"

/**
 * Id estable del wire: la MISMA notificación re-publicada da el mismo id, así que el
 * `enqueueUniqueWork` del servicio, el chequeo por id de `/api/sms/sync` y la memoria de abajo
 * dicen todos lo mismo sin ponerse de acuerdo.
 *
 * Prefijo propio y distinto de `sms_` y `sms_rt_`: el hook de push del server está acotado a
 * `sms_rt_` justo para que un backfill no dispare notificaciones, y una captura de notificación
 * tampoco debería (el teléfono del dueño ya se la mostró — la del banco). Ver `SmsRoutes`.
 */
fun idDeNotificacion(huella: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(huella.toByteArray())
    return "notif_" + digest.joinToString("") { "%02x".format(it) }.take(16)
}

/** Cuántas huellas recuerda el teléfono. Un banco activo no publica 200 avisos en un día. */
internal const val TOPE_DE_MEMORIA = 200

/**
 * **¿Ya subimos esta?** Devuelve `null` si la huella ya estaba —o sea, no subir— y la lista nueva
 * (la vieja + la huella, recortada al [tope] por el extremo más viejo) si hay que subir.
 *
 * Pura a propósito: el estado vive en `SharedPreferences` ([AlmacenDeNotificaciones]), pero la
 * regla se prueba sin Android.
 */
fun recordarHuella(vistas: List<String>, huella: String, tope: Int = TOPE_DE_MEMORIA): List<String>? {
    if (huella in vistas) return null
    return (vistas + huella).takeLast(tope)
}

/**
 * La lista de apps que sirve el server, leída del MISMO JSON del filtro de SMS
 * (`GET /api/sms/filter-config`) — un endpoint, un cache, un Worker de refresco.
 *
 * Devuelve `null` si la clave falta o está mal tipada, que es el caso de un server viejo: ahí el
 * teléfono se queda con los defaults compilados en vez de con una lista vacía.
 */
fun appsQueAvisan(json: String): AppsQueAvisan? = runCatching {
    val arreglo = JSONObject(json).getJSONArray("appPackages")
    AppsQueAvisan((0 until arreglo.length()).map { arreglo.getString(it) })
}.getOrNull()
