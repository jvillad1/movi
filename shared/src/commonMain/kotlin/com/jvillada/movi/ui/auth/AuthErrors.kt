package com.jvillada.movi.ui.auth

import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.ui.components.toUserMessage

/**
 * **Por qué esto no vive adentro del `@Composable`.**
 *
 * `LoginScreen` mapeaba **cualquier** fallo a «Correo o contraseña incorrectos». Con la red
 * caída, con el servidor dormido, con un 500 — la app acusaba a la contraseña. A un revisor le
 * costó veinte minutos de diagnóstico; al dueño le haría dudar de una contraseña que está bien y
 * cambiarla sin necesidad.
 *
 * Lo que se saca del `@Composable` a una función pura deja de romperse (pasó con el campo de
 * monto y con la máquina de sub-pickers), y esto es exactamente eso: una función de `Throwable` a
 * texto. Está acá, aparte, y con tests.
 *
 * **Lo que NO se distingue, a propósito.** Un 401 se contesta igual esté mal la contraseña o no
 * exista el correo. Separar esos dos casos le regalaría a un atacante saber qué direcciones están
 * registradas — el servidor ya se cuida de eso (una sola respuesta «Invalid credentials», con
 * verificación señuelo para que ni el tiempo lo delate) y sería absurdo deshacerlo en la pantalla.
 * Lo que se separa acá es **el fallo de red del rechazo de credenciales**, no un 401 de otro 401.
 */
enum class FalloDeAuth {
    /** 401: el servidor contestó y dijo que no. Es el único caso donde la contraseña es sospechosa. */
    CREDENCIALES,

    /** 429: el limitador del servidor cortó. Reintentar ya no sirve; hay que esperar. */
    DEMASIADOS_INTENTOS,

    /** Otro 4xx: el servidor explicó el rechazo en el cuerpo (política de contraseña, correo tomado). */
    MOTIVO_DEL_SERVIDOR,

    /** 5xx: llegamos al servidor y el servidor se cayó. Nada que ver con lo que se escribió. */
    SERVIDOR_FALLO,

    /**
     * Nunca hubo una respuesta HTTP utilizable: sin internet, DNS que no resuelve, servidor
     * dormido, timeout, o una respuesta que no se pudo leer. Tampoco tiene nada que ver con lo
     * que se escribió.
     */
    SIN_SERVIDOR,
}

/**
 * Clasifica el fallo por el **código de estado**, no por adivinanzas sobre el texto del error.
 *
 * Eso es posible porque `WalletRepositoryImpl.login`/`register` ahora lanzan [ApiException] con el
 * código cuando la respuesta no es 2xx. Antes reventaba `.body()` deserializando el cuerpo de
 * texto de un 401, y el error resultante era **indistinguible** del de un 500 con cuerpo HTML: los
 * dos eran un fallo de conversión sin ningún número adentro.
 *
 * Cualquier cosa que no sea [ApiException] significa que no hubo respuesta HTTP que valga:
 * [FalloDeAuth.SIN_SERVIDOR]. Puede ser la red, el DNS, un timeout o un cuerpo ilegible; el
 * detalle lo pone [toUserMessage], que ya sabe leer esos mensajes. Lo que importa para quien está
 * frente a la pantalla es que **no fue su contraseña**, y eso es igual en los cuatro casos.
 */
fun clasificarFalloDeAuth(error: Throwable): FalloDeAuth {
    val estado = (error as? ApiException)?.status ?: return FalloDeAuth.SIN_SERVIDOR
    return when {
        estado == 401 -> FalloDeAuth.CREDENCIALES
        estado == 429 -> FalloDeAuth.DEMASIADOS_INTENTOS
        estado >= 500 -> FalloDeAuth.SERVIDOR_FALLO
        estado >= 400 -> FalloDeAuth.MOTIVO_DEL_SERVIDOR
        // Un 2xx o un 3xx acá querría decir que la respuesta llegó pero no se pudo usar.
        else -> FalloDeAuth.SIN_SERVIDOR
    }
}

/** El único texto de credenciales rechazadas. Idéntico para «no existe» y «contraseña mala». */
const val CREDENCIALES_RECHAZADAS = "Correo o contraseña incorrectos"

const val DEMASIADOS_INTENTOS_AUTH =
    "Demasiados intentos. Espera unos minutos antes de volver a probar."

/**
 * **La aclaración que evita que el dueño cambie una contraseña que está bien.**
 *
 * Se agrega solo cuando el fallo NO fue de credenciales. Sin ella, un «Sin conexión» impreso al
 * lado de un campo de contraseña se sigue leyendo como sospecha sobre lo que uno escribió.
 */
private const val NO_ES_TU_CONTRASENA = "No es tu contraseña."

/**
 * El texto que ve el dueño cuando «Entrar» no prosperó.
 *
 * El detalle de los casos que no son credenciales lo pone [toUserMessage] —el mismo mapeo que usa
 * el resto de la app, que ya distingue «sin conexión» de «no se pudo conectar al servidor» de
 * «tardó demasiado»— y acá solo se le suma la aclaración de que la contraseña no está en duda.
 */
fun mensajeDeLogin(error: Throwable): String = when (clasificarFalloDeAuth(error)) {
    FalloDeAuth.CREDENCIALES -> CREDENCIALES_RECHAZADAS
    FalloDeAuth.DEMASIADOS_INTENTOS -> DEMASIADOS_INTENTOS_AUTH
    // TODO lo demás lleva la aclaración, no solo la red y el 5xx. Un 4xx que no es 401 tampoco es
    // un rechazo de credenciales —el 404 de un proxy mal apuntado, por ejemplo— y sin la coletilla
    // se leía «Recurso no encontrado.» a secas al lado del campo de contraseña, que es justo la
    // clase de mensaje que deja a alguien sospechando de lo que escribió.
    else -> "${error.toUserMessage()} $NO_ES_TU_CONTRASENA"
}

/**
 * Ídem para «Crear cuenta». Dos diferencias con el login:
 *
 * - **No hay caso de credenciales**: al registrarse no hay contraseña que pueda estar «mal», así
 *   que un 401 acá sería un error nuestro y cae en el saco genérico.
 * - Cuando no se llegó al servidor, lo que la persona necesita saber es **en qué estado quedó**:
 *   la cuenta no se creó, puede volver a intentar sin miedo a terminar con dos.
 */
fun mensajeDeRegistro(error: Throwable): String = when (clasificarFalloDeAuth(error)) {
    FalloDeAuth.DEMASIADOS_INTENTOS -> DEMASIADOS_INTENTOS_AUTH
    // 409 «Email already registered» viene en inglés del servidor (ver AuthRoutes.kt): se traduce
    // acá en vez de mostrarlo tal cual, igual que hace el overlay de index.html.
    FalloDeAuth.MOTIVO_DEL_SERVIDOR ->
        if ((error as? ApiException)?.status == 409) "Ese correo ya tiene una cuenta. Entra con tu contraseña."
        else error.toUserMessage()
    // Un 401 acá no debería poder pasar —al registrarse no hay credenciales que rechazar— pero
    // tiene rama propia igual, y no por prolijidad: 401 cae DENTRO del rango 400..422 con el que
    // [toUserMessage] contesta usando el cuerpo del servidor, así que mandarlo al saco de abajo
    // habría impreso «Invalid credentials» en inglés. Es la misma trampa que el login ya esquiva.
    FalloDeAuth.CREDENCIALES -> "No se pudo crear la cuenta. Intenta de nuevo."
    FalloDeAuth.SERVIDOR_FALLO, FalloDeAuth.SIN_SERVIDOR ->
        "${error.toUserMessage()} La cuenta no se creó."
}

/**
 * **El pedido del enlace de recuperación**, que no tiene credenciales en juego pero tenía el mismo
 * problema al revés: su `onFailure` decía «No se pudo conectar. Prueba de nuevo.» aunque el
 * servidor sí hubiera contestado (y hubiera fallado por otra cosa).
 *
 * Los códigos que la ruta contesta a propósito (202, 503, 429) los sigue leyendo la pantalla —
 * `requestPasswordReset` devuelve el número, no lanza. Esto es solo para cuando ni eso hubo.
 */
fun mensajeDeRecuperacion(error: Throwable): String = when (clasificarFalloDeAuth(error)) {
    FalloDeAuth.DEMASIADOS_INTENTOS -> DEMASIADOS_INTENTOS_AUTH
    FalloDeAuth.MOTIVO_DEL_SERVIDOR -> error.toUserMessage()
    // Rama propia por el mismo motivo que en el registro: sin ella, el cuerpo de un 401 se
    // imprimiría tal cual, en inglés.
    FalloDeAuth.CREDENCIALES -> "No se pudo pedir el enlace. Intenta de nuevo."
    else -> "${error.toUserMessage()} No se pudo pedir el enlace."
}
