package com.jvillada.movi.shared.model

import kotlinx.datetime.toLocalDateTime
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable

/**
 * # Qué se sabe —de verdad— de la captura de SMS del banco
 *
 * ## El defecto que cierra
 *
 * La captura de SMS existió durante varias entregas **sin haber entregado un solo mensaje**, y
 * nadie se enteró: la tabla `sms_messages` de producción estaba vacía y los 73 movimientos del
 * dueño tenían `source = MANUAL`. Estuvo semanas anotando a mano creyendo que la captura corría,
 * y lo descubrió por casualidad, al echar de menos cinco cobros.
 *
 * Se enteró tarde por una razón concreta: el ÚNICO estado de la captura vivía en la app de
 * Android (permisos, hibernación), y el actual de la web no pintaba nada. Él trabaja en Chrome.
 * La única superficie que podía avisarle era invisible justo en la plataforma que usa.
 *
 * Es el mismo silencio que este repo ya decidió no tolerar en otros lados: `/version` existe
 * porque «un merge no prueba un despliegue», y la guarda del dex existe porque un `BUILD
 * SUCCESSFUL` no prueba un paquete que arranque. Esto es esa misma idea aplicada a la captura.
 *
 * ## La regla, y lo que NO se afirma
 *
 * Aquí solo se dice lo **observado**: cuántos mensajes llegaron alguna vez y cuándo llegó el
 * último. Nunca «la captura está activa». Un permiso concedido con un receiver muerto es
 * exactamente el estado en el que estuvo el dueño, así que un permiso no es evidencia de nada:
 * lo único que prueba que la captura funciona es un mensaje que haya llegado.
 *
 * Vive en `:core` —y no en la pantalla— porque la calculan **dos lados**: el server, para el
 * resumen del Inicio (`GET /api/dashboard/summary`), y la bandeja de «Mensajes del banco», que
 * ya se baja la lista y no necesita un viaje nuevo. Es la disciplina que este repo aprendió a
 * los golpes con `estadoDePresupuesto`: dos superficies que calculan la misma regla por su
 * cuenta terminan diciendo cosas distintas.
 *
 * @param total cuántos mensajes del banco llegaron alguna vez (todos los estados: por confirmar,
 *   confirmados e ignorados — la pregunta es si LLEGARON, no qué se hizo con ellos).
 * @param ultimo el `time` del más reciente, tal como lo guardó el teléfono, o `null` si nunca
 *   llegó ninguno.
 *
 * `@Serializable` no porque viaje (el server manda `smsTotal`/`smsLastAt` sueltos en el resumen)
 * sino porque el cliente la guarda en el aparato dentro de la instantánea del Inicio.
 */
@Serializable
data class CapturaDeSms(
    val total: Int = 0,
    val ultimo: String? = null,
) {
    /** El estado que hay que gritar: ningún teléfono le mandó nunca nada a esta cuenta. */
    val nuncaLlegoNada: Boolean get() = total == 0
}

/**
 * Clave de orden de un `SmsMessage.time`.
 *
 * La columna es un varchar libre. Los dos caminos de captura escriben `"yyyy-MM-dd HH:mm"` (ver
 * `SmsSync.captureItem` y `SmsReader.rowToSmsMessage`), pero el server tolera además la variante
 * ISO con 'T' y con segundos, y hay filas sembradas a mano con ese formato. Comparar los strings
 * crudos mezclaría los dos: `' '` (0x20) va antes que `'T'`, así que un `"…-01 09:00"` ganaría
 * contra un `"…-01T10:00"` del mismo día y el «último mensaje» quedaría siendo el anteúltimo.
 *
 * Con el separador normalizado, el orden lexicográfico ES el cronológico: los dos formatos son de
 * ancho fijo y de mayor a menor significancia. Los segundos ausentes ordenan antes que `":00"`
 * (mismo minuto), que es una diferencia sin consecuencia para lo que esto decide.
 *
 * No se parsea a una fecha a propósito: esto corre en `commonMain` (JVM, Android, iOS y wasm) y
 * un `time` ilegible —que la columna permite— no debe tumbar la lectura del estado de la captura.
 */
internal fun claveDeTiempoDeSms(time: String): String = time.trim().replace(' ', 'T')

/** El estado de la captura a partir de los `time` de los mensajes que llegaron. */
fun capturaDeSms(tiempos: List<String>): CapturaDeSms = CapturaDeSms(
    total = tiempos.size,
    ultimo = tiempos.filter { it.isNotBlank() }.maxByOrNull { claveDeTiempoDeSms(it) },
)

/**
 * El `time` guardado, en la forma en que se lee en pantalla: `"23 de septiembre a las 7:52 p. m."`.
 *
 * Antes se mostraba el varchar crudo (`"2026-09-23 19:52"`) — legible para quien escribió el
 * parser, no para el dueño. Se reusa [diaLegible] (`PeriodoFinanciero.kt`) para el mes en español y
 * se arma la hora en formato de 12 con «a. m.»/«p. m.», como se dice hablando: 19:52 es «7:52 p.
 * m.», medianoche es «12:00 a. m.» y mediodía «12:00 p. m.» (las doce, no las cero).
 *
 * Un `time` que no se pueda leer como fecha se devuelve **tal cual** (recortado a lo sumo por
 * [claveDeTiempoDeSms]), nunca lanza: es un varchar libre, y antes que inventar una fecha se
 * muestra lo que la fila dice.
 */
/**
 * **Cuándo se movió la plata de un SMS**, en epoch-ms — no cuándo el dueño lo confirmó.
 *
 * Confirmar un mensaje de la bandeja creaba el movimiento con `Clock.System.now()`. Con captura en
 * tiempo real la diferencia son minutos, pero la bandeja también trae el historial del teléfono:
 * confirmar hoy un cobro del 10 de septiembre lo anotaba hoy, en el período equivocado y fuera del
 * día en que el banco lo cobró. El resto de la app fecha cada movimiento cuando pasó.
 *
 * `time` es la hora de pared de Bogotá que escribe el teléfono (`yyyy-MM-dd HH:mm`, ver `SmsSync`).
 * Si no se entiende, o cae en el futuro (reloj del teléfono adelantado), se usa [ahora]: un
 * movimiento no puede fecharse después de hoy.
 */
fun momentoDelSms(time: String, ahora: Long, zona: TimeZone = AppTimeZone.zone): Long {
    val texto = claveDeTiempoDeSms(time)
    val completo = if (texto.length == 16) "$texto:00" else texto
    val millis = runCatching { LocalDateTime.parse(completo).toInstant(zona).toEpochMilliseconds() }.getOrNull()
        ?: return ahora
    return minOf(millis, ahora)
}

/**
 * **¿Este SMS es de antes de que la cuenta empezara en Movi?** Devuelve el primer día de la cuenta
 * (su movimiento más viejo) si el mensaje es anterior a ese día, o `null` si no lo es.
 *
 * El primer movimiento de una cuenta es su saldo inicial (o el ajuste con que arrancó), y ese número
 * **ya incluye** todo lo que pasó antes. La bandeja trae el historial del teléfono —hoy, 44 SMS de
 * agosto de cuentas que arrancaron en Movi el 25 o el 30—, y confirmar uno de esos contaría la misma
 * plata dos veces: en el saldo inicial y en el movimiento nuevo.
 *
 * Se compara por día civil: un SMS del mismo día del saldo inicial no se da por incluido, porque no
 * se sabe si fue antes o después de la foto del saldo.
 */
fun inicioDeLaCuentaSiElSmsEsAnterior(momentoDelSms: Long, eventosDeLaCuenta: List<FinancialEvent>): kotlinx.datetime.LocalDate? {
    val primero = eventosDeLaCuenta.minOfOrNull { it.timestamp } ?: return null
    val zona = AppTimeZone.zone
    val diaDelSms = kotlinx.datetime.Instant.fromEpochMilliseconds(momentoDelSms).toLocalDateTime(zona).date
    val diaDeInicio = kotlinx.datetime.Instant.fromEpochMilliseconds(primero).toLocalDateTime(zona).date
    return if (diaDelSms < diaDeInicio) diaDeInicio else null
}

fun fechaLegibleDeSms(time: String): String {
    val normalizado = claveDeTiempoDeSms(time)
    val completo = if (normalizado.length == 16) "$normalizado:00" else normalizado
    val fecha = runCatching { LocalDateTime.parse(completo) }.getOrNull() ?: return time.trim()
    return "${diaLegible(fecha.date)} a las ${horaLegibleDeLasDoce(fecha.hour, fecha.minute)}"
}

/**
 * Solo la hora de un `time` guardado, como la dice [fechaLegibleDeSms]: «9:15 a. m.». `null` si el
 * `time` no se puede leer como fecha, para que quien la use no invente una hora.
 */
fun horaLegibleDeSms(time: String): String? {
    val normalizado = claveDeTiempoDeSms(time)
    val completo = if (normalizado.length == 16) "$normalizado:00" else normalizado
    val fecha = runCatching { LocalDateTime.parse(completo) }.getOrNull() ?: return null
    return horaLegibleDeLasDoce(fecha.hour, fecha.minute)
}

/**
 * «7:52 p. m.» — la hora de 24 en la forma de 12 que se dice hablando. Las 0 horas son «las doce»
 * de la madrugada, no «las cero»: `hour == 0` se muestra como `12`, no como `0`.
 */
private fun horaLegibleDeLasDoce(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "a. m." else "p. m."
    val hora12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$hora12:${minute.toString().padStart(2, '0')} $amPm"
}

/**
 * Lo que la pantalla «Mensajes del banco» dice sobre la captura, en TODAS las plataformas.
 *
 * @param esAlerta el estado que amerita ruido: nunca llegó nada. Un «llegó el 1 de agosto» es un
 *   dato, no una alarma — decidir si eso ya es demasiado viejo exigiría un umbral inventado, y
 *   esta función solo dice lo observado.
 */
data class AvisoDeCaptura(
    val rotulo: String,
    val detalle: String,
    val esAlerta: Boolean,
)

/**
 * El aviso de la bandeja. Independiente de la plataforma: «este dispositivo no puede leer SMS» y
 * «ningún teléfono mandó nunca nada» son afirmaciones distintas, y solo la segunda es la
 * preocupante. La primera la agrega la pantalla según dónde corra.
 */
fun avisoDeCaptura(captura: CapturaDeSms): AvisoDeCaptura = when {
    captura.nuncaLlegoNada -> AvisoDeCaptura(
        rotulo = "NUNCA HA LLEGADO UN MENSAJE",
        detalle = "Movi todavía no ha recibido ningún mensaje de tu banco. La captura la hace un " +
            "teléfono Android con Movi instalado y con el permiso de mensajes otorgado; mientras " +
            "no llegue el primero, tus movimientos solo entran a mano.",
        esAlerta = true,
    )

    captura.total == 1 -> AvisoDeCaptura(
        rotulo = "ÚLTIMO MENSAJE RECIBIDO",
        detalle = "El único mensaje de tu banco llegó el " +
            "${fechaLegibleDeSms(captura.ultimo.orEmpty())}. Esto dice lo que llegó, no que la " +
            "captura siga andando.",
        esAlerta = false,
    )

    else -> AvisoDeCaptura(
        rotulo = "ÚLTIMO MENSAJE RECIBIDO",
        detalle = "El último de los ${captura.total} mensajes de tu banco llegó el " +
            "${fechaLegibleDeSms(captura.ultimo.orEmpty())}. Esto dice lo que llegó, no que la " +
            "captura siga andando.",
        esAlerta = false,
    )
}

/**
 * La fila del Inicio, o `null` cuando no hay nada que decir ahí.
 *
 * ## Por qué solo el caso «nunca llegó nada»
 *
 * Porque es el único que se puede afirmar sin inventar un umbral. «Hace mucho que no llega
 * ninguno» exigiría decidir cuánto es mucho, y un banco puede pasar dos semanas sin mandarle un
 * SMS a alguien que no gastó. «Nunca» no admite interpretación.
 *
 * ## Por qué esto no se vuelve un reto permanente
 *
 * Dos frenos, y hacen falta los dos:
 *
 * 1. **Se apaga sola para siempre en cuanto llegue el primer mensaje.** Quien de verdad use la
 *    captura ve esta fila una vez —mientras está rota, que es cuando sirve— y nunca más.
 * 2. **[silenciada] la apaga a mano**, y es lo que salva a quien nunca va a usar la captura
 *    (alguien de iOS, alguien que solo usa la web, alguien fuera de Colombia): para esa persona
 *    el freno 1 no se dispara nunca y la fila sería un reproche eterno por algo que no piensa
 *    hacer. Se apaga desde la propia pantalla de «Mensajes del banco» —donde el hecho sigue
 *    visible— y no desde el Inicio: lo que se silencia es el recordatorio, no el dato.
 */
fun alertaDeCapturaEnInicio(captura: CapturaDeSms, silenciada: Boolean): String? =
    if (captura.nuncaLlegoNada && !silenciada) "Movi nunca ha recibido un mensaje de tu banco" else null
