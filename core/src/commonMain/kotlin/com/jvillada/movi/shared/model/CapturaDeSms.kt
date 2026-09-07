package com.jvillada.movi.shared.model

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
 */
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
 * El `time` guardado, en la forma en que se lee en pantalla: `"2026-08-01 10:00"`.
 *
 * Un `time` que no tenga esa forma se devuelve tal cual, recortado. Es un varchar libre: antes
 * que inventar una fecha o mostrar un guion, se muestra lo que la fila dice.
 */
fun fechaLegibleDeSms(time: String): String {
    val normalizado = time.trim().replace('T', ' ')
    return if (normalizado.length >= 16) normalizado.take(16) else normalizado
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
        detalle = "El único mensaje de tu banco que ha llegado a Movi es del " +
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
