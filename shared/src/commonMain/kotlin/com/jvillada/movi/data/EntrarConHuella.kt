package com.jvillada.movi.data

/**
 * # «Entrar con huella», la parte que se puede probar sin un teléfono
 *
 * Todo lo de este archivo es **decisión pura**: funciones sin Compose, sin Android y sin estado
 * propio. Lo que necesita un lector vive en [com.jvillada.movi.platform.HuellaDelAparato]; el
 * interruptor vive en [SessionManager]. Acá está lo único que puede equivocarse de verdad:
 * **cuándo se pide la huella, y qué se dice cuando no sale bien**.
 *
 * ## Qué es y qué NO es esta función
 *
 * Es **la puerta para abrir Movi en este teléfono**, y nada más. La sesión sigue guardándose
 * exactamente donde la guardaba antes —el almacenamiento privado de la app—, sin cifrar y sin
 * depender de ninguna huella.
 *
 * Eso es a propósito, y el dueño lo eligió con el costo a la vista. La alternativa —dejar el
 * token bajo una llave del Keystore que solo suelta una huella— protegía contra alguien con el
 * teléfono desbloqueado y un cable, pero **rompía la captura de SMS**: los procesos que Android
 * levanta por su cuenta (`SmsBackfillWorker` cada 6 horas, `SmsSyncWorker` tras un reinicio)
 * arrancan sin nadie que ponga el dedo, así que se quedaban sin token y dejaban de subir los
 * mensajes del banco hasta que él abriera la app. Ese costo diario es peor, para él, que la
 * amenaza que la otra cubría.
 *
 * Decirlo de frente: **esto es comodidad, no blindaje**. Quien tenga el teléfono desbloqueado y
 * herramientas puede llegar a la sesión igual que antes de este cambio. Lo que la huella evita es
 * que cualquiera que agarre el teléfono desbloqueado **abra Movi y vea su plata**.
 */

/** Lo que el aparato puede hacer AHORA, preguntado cada vez (el dueño puede borrar sus huellas). */
enum class EstadoDeHuella {
    /** Hay lector y hay al menos una huella registrada. */
    LISTA,

    /** Hay lector, pero el teléfono no tiene ninguna huella registrada todavía. */
    SIN_REGISTRAR,

    /** Ni lector, ni plataforma que lo soporte (la web y iOS caen siempre acá). */
    NO_DISPONIBLE,
}

/** Para qué se está pidiendo la huella. Solo cambia lo que dice el diálogo del sistema. */
enum class PropositoDeHuella { ENTRAR, ACTIVAR }

/** Cómo terminó un pedido de huella. */
enum class ResultadoDeHuella {
    EXITO,

    /** Tocó «Usar mi contraseña», el botón de atrás, o el sistema canceló el diálogo. */
    CANCELADA,

    /** El teléfono se quedó sin huellas registradas entre que se activó y ahora. */
    SIN_REGISTRAR,

    /** Cualquier otra cosa: lector ocupado, hardware con problemas, un prompt que no se pudo abrir. */
    FALLA,
}

/** Qué se interpone —o no— entre abrir la app y ver el Inicio. */
enum class ArranqueDeSesion {
    /**
     * La puerta no se interpone y la app arranca como siempre: con el Inicio si hay sesión, o con
     * el formulario de siempre si no la hay.
     */
    SIN_PUERTA,

    /** Hay sesión y el interruptor está prendido: el prompt del sistema antes de mostrar nada. */
    PEDIR_HUELLA,

    /**
     * Hay sesión y el interruptor prendido, pero este teléfono ya no puede pedir huella (le
     * borraron las huellas, o el lector murió). Se muestra el formulario con el motivo.
     *
     * **No se apaga el interruptor solo, y no se borra la sesión.** Apagarlo sería bajarle la
     * guardia sin avisarle; borrarla, castigarlo por algo que no hizo. Cuando vuelva a registrar
     * una huella, la puerta funciona de nuevo sin que él toque nada.
     */
    PEDIR_CONTRASENA,
}

/**
 * **La decisión del arranque.**
 *
 * Sin sesión no hay nada que tapar: la app muestra el login de siempre, que ya es una puerta. El
 * interruptor apagado tampoco se interpone. Recién con sesión viva **y** el interruptor prendido
 * aparece el prompt — o, si el aparato ya no puede, el formulario con el motivo.
 */
fun decidirArranque(
    haySesion: Boolean,
    huellaActivada: Boolean,
    estado: EstadoDeHuella,
): ArranqueDeSesion = when {
    !haySesion || !huellaActivada -> ArranqueDeSesion.SIN_PUERTA
    estado == EstadoDeHuella.LISTA -> ArranqueDeSesion.PEDIR_HUELLA
    else -> ArranqueDeSesion.PEDIR_CONTRASENA
}

/**
 * Qué decirle cuando el prompt no terminó en un dedo aceptado. `null` para [ResultadoDeHuella.EXITO],
 * que no tiene nada que explicar.
 *
 * **Ningún resultado borra la sesión.** Es la diferencia entera con un token guardado bajo llave:
 * acá la huella no abre nada, solo deja pasar, así que fallar no puede costarle la sesión. Siempre
 * quedan las dos salidas a mano: volver a poner el dedo, o escribir la contraseña.
 */
fun motivoDeLaHuella(resultado: ResultadoDeHuella): String? = when (resultado) {
    ResultadoDeHuella.EXITO -> null
    ResultadoDeHuella.CANCELADA -> MENSAJE_CANCELADA
    ResultadoDeHuella.SIN_REGISTRAR -> MENSAJE_SIN_REGISTRAR
    ResultadoDeHuella.FALLA -> MENSAJE_FALLA
}

/**
 * ¿Se ofrece activar la huella después de entrar con contraseña?
 *
 * Solo si el aparato puede, si no está ya activada, y si no lo rechazó antes. Lo último importa:
 * ofrecer lo mismo en cada entrada es acoso, y el interruptor sigue estando en Perfil para quien
 * cambie de opinión.
 */
fun ofrecerHuellaTrasEntrar(
    estado: EstadoDeHuella,
    yaActivada: Boolean,
    yaLoRechazo: Boolean,
): Boolean = estado == EstadoDeHuella.LISTA && !yaActivada && !yaLoRechazo

// Los textos viven acá y no en la pantalla para que las pruebas afirmen lo que el dueño lee,
// y no una constante que podría decir otra cosa. Los tres nombran las DOS salidas: el dedo otra
// vez, o la contraseña. Ninguna pantalla puede quedar sin camino.
const val MENSAJE_CANCELADA =
    "Cancelaste la huella. Puedes ponerla otra vez o entrar con tu contraseña."
const val MENSAJE_SIN_REGISTRAR =
    "Este teléfono no tiene huellas registradas. Entra con tu contraseña, o registra una en los ajustes del teléfono."
const val MENSAJE_FALLA =
    "No se pudo leer la huella. Intenta otra vez o entra con tu contraseña."

/**
 * La línea honesta, la misma en el ofrecimiento y en Perfil.
 *
 * Dice las tres cosas que él necesita saber y que nadie adivina: para qué sirve la huella (abrir
 * Movi), dónde queda la sesión (donde la deja cualquier app), y qué NO se guarda (la contraseña).
 * No promete cifrado, porque no lo hay.
 */
const val EXPLICACION_HUELLA =
    "Tu huella abre Movi en este teléfono. La sesión se guarda como la guarda cualquier app, y tu contraseña no se guarda nunca."
