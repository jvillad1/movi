package com.jvillada.movi.data

/**
 * # «Entrar con huella», la parte que se puede probar sin un teléfono
 *
 * Todo lo de este archivo es **decisión pura**: funciones sin Compose, sin Android y sin estado
 * propio. Lo que sí necesita un lector de huellas vive en
 * [com.jvillada.movi.platform.HuellaDelAparato], y lo que hay que guardar vive en
 * [SessionManager]. Acá está lo único que puede equivocarse de verdad: **cuándo se pide la
 * huella, y qué pasa cuando no sale bien**.
 *
 * Está separado a propósito. Una pantalla que decide sola «¿pido la huella?» solo se puede probar
 * montando la pantalla, y el prompt biométrico no existe en la JVM: las cuatro ramas que importan
 * —cancelar, no tener huellas registradas, que cambien las huellas del teléfono, y un token que el
 * servidor ya no acepta— quedarían sin una sola prueba. Acá son cuatro llamadas a una función.
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

/** Cómo terminó un pedido de huella. */
enum class ResultadoDeHuella {
    EXITO,

    /** La persona tocó «Usar mi contraseña», el botón de atrás, o el sistema canceló el diálogo. */
    CANCELADA,

    /** El teléfono se quedó sin huellas registradas entre que se activó y ahora. */
    SIN_REGISTRAR,

    /**
     * La llave del Keystore ya no sirve: alguien agregó o borró una huella en el teléfono.
     * Android invalida la llave a propósito, y lo cifrado con ella **no se puede recuperar**.
     */
    LLAVE_INVALIDA,

    /** Cualquier otra cosa: lector ocupado, hardware con problemas, un descifrado que no cuadra. */
    FALLA,
}

/**
 * Lo que se guarda cifrado en el teléfono. **La contraseña no está acá, ni en ningún otro lado**:
 * lo que se guarda es el mismo token de sesión que el servidor ya había emitido, que caduca a los
 * 30 días y que se puede invalidar cerrando sesión.
 */
data class SesionGuardada(
    val token: String,
    val userId: String,
    val nombre: String,
    val correo: String,
)

/** Qué hacer cuando la app arranca sin sesión viva en memoria. */
enum class ArranqueDeSesion {
    /** Ya hay sesión: no hay nada que preguntar. */
    ENTRAR_DIRECTO,

    /** Hay una sesión guardada bajo llave y el aparato puede abrirla: se muestra el prompt. */
    PEDIR_HUELLA,

    /** No hay nada que abrir, o este teléfono ya no puede: el formulario de siempre. */
    PEDIR_CONTRASENA,
}

/**
 * **La decisión del arranque.**
 *
 * El orden de las guardas no es casual:
 *
 * 1. Una sesión viva gana sobre todo lo demás — pedir la huella con la sesión ya abierta sería
 *    un trámite que no protege nada.
 * 2. Sin nada guardado no hay nada que abrir, aunque el interruptor haya quedado prendido.
 * 3. Un teléfono al que le borraron las huellas **no puede** abrir lo guardado. Se pide la
 *    contraseña en vez de mostrar un prompt que va a fallar.
 */
fun decidirArranque(
    sesionViva: Boolean,
    huellaActivada: Boolean,
    haySesionGuardada: Boolean,
    estado: EstadoDeHuella,
): ArranqueDeSesion = when {
    sesionViva -> ArranqueDeSesion.ENTRAR_DIRECTO
    !huellaActivada || !haySesionGuardada -> ArranqueDeSesion.PEDIR_CONTRASENA
    estado != EstadoDeHuella.LISTA -> ArranqueDeSesion.PEDIR_CONTRASENA
    else -> ArranqueDeSesion.PEDIR_HUELLA
}

/**
 * Qué hacer con lo que devolvió el prompt.
 *
 * @param sesion lo que se pudo descifrar, si se pudo.
 * @param olvidarLoGuardado si lo cifrado ya no sirve y hay que borrarlo. **Sin esto se entra en
 *   bucle**: el próximo arranque volvería a ofrecer una huella que no puede abrir nada.
 * @param mensaje qué decirle a la persona en la pantalla de login. `null` cuando entró bien.
 */
data class TrasLaHuella(
    val sesion: SesionGuardada?,
    val olvidarLoGuardado: Boolean,
    val mensaje: String?,
)

fun quePasaTrasLaHuella(resultado: ResultadoDeHuella, sesion: SesionGuardada?): TrasLaHuella =
    when (resultado) {
        // Un éxito sin sesión adentro no es un éxito: el descifrado dio algo que no se pudo leer.
        // Se trata como llave rota, que es lo único que puede haberlo causado.
        ResultadoDeHuella.EXITO -> if (sesion != null) {
            TrasLaHuella(sesion, olvidarLoGuardado = false, mensaje = null)
        } else {
            TrasLaHuella(null, olvidarLoGuardado = true, mensaje = MENSAJE_LLAVE_INVALIDA)
        }
        // Cancelar NO borra nada: la próxima vez se vuelve a ofrecer.
        ResultadoDeHuella.CANCELADA ->
            TrasLaHuella(null, olvidarLoGuardado = false, mensaje = MENSAJE_CANCELADA)
        ResultadoDeHuella.SIN_REGISTRAR ->
            TrasLaHuella(null, olvidarLoGuardado = true, mensaje = MENSAJE_SIN_REGISTRAR)
        ResultadoDeHuella.LLAVE_INVALIDA ->
            TrasLaHuella(null, olvidarLoGuardado = true, mensaje = MENSAJE_LLAVE_INVALIDA)
        // Un fallo pasajero (lector ocupado) tampoco borra nada.
        ResultadoDeHuella.FALLA ->
            TrasLaHuella(null, olvidarLoGuardado = false, mensaje = MENSAJE_FALLA)
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
// y no una constante que podría decir otra cosa.
const val MENSAJE_CANCELADA = "Cancelaste la huella. Entra con tu contraseña."
const val MENSAJE_SIN_REGISTRAR =
    "Este teléfono ya no tiene huellas registradas, así que Movi olvidó la sesión guardada. Entra con tu contraseña."
const val MENSAJE_LLAVE_INVALIDA =
    "Cambiaron las huellas de este teléfono, así que Movi olvidó la sesión guardada. Entra con tu contraseña y vuelve a activarla."
const val MENSAJE_FALLA = "No se pudo leer la huella. Entra con tu contraseña."

/**
 * La línea honesta, la misma en el ofrecimiento y en Perfil: qué se guarda, dónde, y qué no.
 */
const val EXPLICACION_HUELLA =
    "Movi guarda tu sesión cifrada en este teléfono y la abre con tu huella. Tu contraseña no se guarda nunca."
