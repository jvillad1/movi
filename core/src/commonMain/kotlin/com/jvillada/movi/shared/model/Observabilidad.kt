package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Observabilidad (vuelta 100): cuándo y por qué se cae Movi, y cómo se usa
 *
 * Todo en casa: el server y el Postgres de Movi, sin terceros. Es una app de finanzas, y un
 * Sentry o un Firebase exigirían cuentas y mandarían datos afuera.
 *
 * Dos cosas viajan, y las dos **sin datos del usuario**:
 *
 * - **Fallos** ([FalloDeLaApp]): el tipo de la excepción, su mensaje y su stack, recortados y
 *   limpios ([limpiarTextoDelFallo]: los números de 5+ cifras, los correos y lo que va entre
 *   comillas salen). Se mandan siempre, aunque el interruptor de uso esté apagado.
 * - **Uso** ([EventoDeUsoEnviado]): nombres de una lista cerrada ([EventoDeUso]) con propiedades de
 *   otra lista cerrada de claves ([ClaveDeUso]), cada una con su forma. Nunca montos, descripciones,
 *   comercios, nombres de cuentas ni texto escrito por el usuario: [problemaDelEvento] lo rechaza
 *   en el cliente y otra vez en el server.
 *
 * Las reglas de limpieza y de huella viven acá, en `:core`, para que el teléfono y el server
 * digan lo mismo.
 */

// ── Fallos ───────────────────────────────────────────────────────────────────────────────────

/** Un fallo de la app: el que la tumbó o el que un Worker atrapó y siguió ([atrapado]). */
@Serializable
data class FalloDeLaApp(
    /** Id del cliente: el mismo reporte subido dos veces se guarda una. */
    val id: String,
    /** `android`, `ios` o `web`. */
    val plataforma: String,
    val version: String = "",
    /** El nombre de la clase de la excepción (`IllegalStateException`). */
    val tipo: String,
    /** Recortado y limpio ([limpiarTextoDelFallo]). */
    val mensaje: String = "",
    /** Las primeras [MAX_LINEAS_DEL_STACK] líneas, limpias ([stackRecortado]). */
    val stack: String = "",
    val hilo: String = "",
    /** La clase propia de arriba del stack (Activity, Worker, Receiver o Service): [componenteDelStack]. */
    val componente: String = "",
    /** Modelo del aparato («samsung SM-A155M»). */
    val modelo: String = "",
    /** «Android 14 (API 34)», «iOS 18.0». */
    val sistema: String = "",
    val ocurridoEn: Long,
    /** `true` si no tumbó la app (un Worker la atrapó). */
    val atrapado: Boolean = false,
)

const val MAX_MENSAJE_DEL_FALLO = 300
const val MAX_LINEAS_DEL_STACK = 60
const val MAX_LARGO_DE_LINEA_DEL_STACK = 200
const val MAX_HUELLA_DEL_FALLO = 300
private const val PAQUETE_PROPIO = "com.jvillada.movi."

private val NUMERO_LARGO = Regex("""\d(?:[.,\s]?\d){4,}""")
private val CORREO = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
private val ENTRE_COMILLAS = Regex("\"[^\"\\n]*\"|«[^»\\n]*»")
// Sin `\p{Cntrl}`: la expresión tiene que valer igual en la JVM, en Kotlin/Native y en wasm (JS).
private val ESPACIOS = Regex("[\\u0000-\\u001F\\u007F\\s]+")

/**
 * **El mensaje de una excepción puede traer datos**: un `NumberFormatException` dice
 * `For input string: "27500000"`, uno de JSON puede citar el cuerpo. Se borra lo que va entre
 * comillas, los correos y todo número de 5+ cifras (con o sin separadores: `27.500.000` también) se
 * vuelve `#`; después se aplana a un renglón y se recorta a [max].
 */
fun limpiarTextoDelFallo(texto: String?, max: Int = MAX_MENSAJE_DEL_FALLO): String =
    (texto ?: "")
        .replace(ENTRE_COMILLAS, "\"…\"")
        .replace(CORREO, "@")
        .replace(NUMERO_LARGO, "#")
        .replace(ESPACIOS, " ")
        .trim()
        .take(max)

/**
 * **El stack, recortado**: las primeras [MAX_LINEAS_DEL_STACK] líneas, cada una limpia como el
 * mensaje (la primera línea y los «Caused by:» llevan mensajes) y de a lo sumo
 * [MAX_LARGO_DE_LINEA_DEL_STACK] caracteres. Los números de línea (`Archivo.kt:43`) tienen menos
 * de cinco cifras y sobreviven.
 */
fun stackRecortado(stack: String): String =
    stack.lineSequence()
        .map { limpiarTextoDelFallo(it, MAX_LARGO_DE_LINEA_DEL_STACK) }
        .filter { it.isNotEmpty() }
        .take(MAX_LINEAS_DEL_STACK)
        .joinToString("\n")

/**
 * La primera línea del stack (un renglón `at …`, en la JVM y en Kotlin/Native) que nombra código de
 * Movi (`com.jvillada.movi.…`), o `null`. El renglón del mensaje no cuenta aunque nombre una clase
 * propia (un `ClassCastException` lo hace).
 */
fun primeraLineaPropia(stack: String): String? =
    stack.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("at ") && PAQUETE_PROPIO in it }

/**
 * **El componente**: la clase propia de arriba del stack. En la JVM la línea es
 * `at com.jvillada.movi.sms.SmsSyncWorker.doWork(SmsSyncWorker.kt:43)`; en Kotlin/Native,
 * `kfun:com.jvillada.movi.App#App(){}`. En los dos se toma el último segmento con mayúscula antes
 * del método, sin las clases anónimas (`$doWork$2`). Sin código propio en el stack: `""`.
 */
fun componenteDelStack(stack: String): String {
    val linea = primeraLineaPropia(stack) ?: return ""
    val desde = linea.indexOf(PAQUETE_PROPIO)
    val calificado = linea.substring(desde).takeWhile { it != '(' && it != '#' && !it.isWhitespace() }
    return calificado.split('.')
        .map { it.substringBefore('$') }
        .lastOrNull { it.firstOrNull()?.isUpperCase() == true }
        .orEmpty()
}

/**
 * **La huella de un fallo**: tipo + la primera línea propia del stack **sin el número de línea**,
 * para que el mismo defecto se agrupe aunque el archivo se haya movido entre versiones. Legible a
 * propósito: la pantalla de admin la muestra.
 */
fun huellaDelFallo(tipo: String, stack: String): String {
    val linea = primeraLineaPropia(stack)
        ?: stack.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("at ") }
        ?: ""
    val sinNumero = linea.removePrefix("at ").replace(Regex(""":\d+\)"""), ")")
    return "$tipo · $sinNumero".trim().take(MAX_HUELLA_DEL_FALLO)
}

/** El fallo con su texto limpio y recortado: lo aplica el cliente al armarlo y el server al recibirlo. */
fun FalloDeLaApp.limpio(): FalloDeLaApp {
    val stackLimpio = stackRecortado(stack)
    return copy(
        id = id.take(80),
        plataforma = plataforma.take(20),
        version = version.take(30),
        tipo = limpiarTextoDelFallo(tipo, 120),
        mensaje = limpiarTextoDelFallo(mensaje),
        stack = stackLimpio,
        hilo = limpiarTextoDelFallo(hilo, 80),
        componente = (componente.ifBlank { componenteDelStack(stackLimpio) }).take(120),
        modelo = limpiarTextoDelFallo(modelo, 80),
        sistema = limpiarTextoDelFallo(sistema, 60),
    )
}

/**
 * **Arma el reporte de un [Throwable]**, ya limpio. El nombre de la clase sale del propio
 * `Throwable` y el stack de `stackTraceToString()`, que existe en todas las plataformas.
 */
fun falloDe(
    error: Throwable,
    id: String,
    plataforma: String,
    version: String,
    hilo: String,
    modelo: String,
    sistema: String,
    ahora: Long,
    atrapado: Boolean = false,
    componente: String = "",
): FalloDeLaApp = FalloDeLaApp(
    id = id,
    plataforma = plataforma,
    version = version,
    tipo = error::class.simpleName ?: "Throwable",
    mensaje = error.message.orEmpty(),
    stack = error.stackTraceToString(),
    hilo = hilo,
    componente = componente,
    modelo = modelo,
    sistema = sistema,
    ocurridoEn = ahora,
    atrapado = atrapado,
).limpio()

/** Un grupo de fallos de la pantalla de admin: los de la misma huella en los últimos 7 días. */
@Serializable
data class GrupoDeFallos(
    val huella: String,
    val tipo: String,
    val componente: String,
    val plataformas: List<String>,
    val cuantos: Int,
    val usuarios: Int,
    val versiones: List<String>,
    val ultimaVez: Long,
    /** El más reciente del grupo, entero. */
    val ejemplo: FalloDeLaApp,
)

@Serializable
data class ResumenDeFallos(
    val dias: Int = 7,
    val grupos: List<GrupoDeFallos> = emptyList(),
)

// ── Uso ──────────────────────────────────────────────────────────────────────────────────────

/**
 * El tipo de un evento de uso. [PANTALLA] es abrir una pantalla, [ACCION] lo que la persona tocó o
 * hizo, [SISTEMA] lo que pasó sin que tocara nada (la sesión, una lectura, una sincronización, un error
 * de red).
 */
enum class TipoDeUso { PANTALLA, ACCION, SISTEMA }

/**
 * **La forma que puede tener el valor de una clave.** Ninguna admite texto libre: no hay forma que
 * deje pasar un espacio entre palabras, una minúscula suelta con cifras ni un monto.
 */
enum class FormaDeDato(val regex: Regex?) {
    /** Uno de los valores de la clave ([ClaveDeUso.valores]). */
    LISTA(null),
    /** Un nombre de clase o de pantalla: letras, empieza con mayúscula, sin cifras. */
    NOMBRE(Regex("[A-Z][A-Za-z]{1,39}")),
    /** Un estado o un código de error de Movi: MAYÚSCULAS_CON_GUION_BAJO, sin cifras (`IA_SIN_CREDITO`). */
    ESTADO(Regex("[A-Z][A-Z_]{1,39}")),
    /** Un código HTTP. */
    CODIGO_HTTP(Regex("[1-5][0-9][0-9]")),
    /** Un segmento de ruta de la API (`events`, `debitos-automaticos`). */
    SEGMENTO(Regex("[a-z][a-z-]{0,39}")),
    /**
     * Un id de Movi: un prefijo de letras y el resto hexadecimal (`ev_3f2a…`, `sms_rt_9c1d…`,
     * `cmp_doc_…`), como los arman `newId` y los sensores. Un número de cuenta o un comercio no
     * tienen esa forma.
     */
    ID(Regex("[a-z]{2,12}(?:_[a-z]{2,12})*_[0-9a-fA-F-]{8,80}")),
    /** Un conteo (0 a 999.999). */
    CONTEO(Regex("[0-9]{1,6}")),
    /** Una duración en segundos o milisegundos (hasta 9 cifras: un día en ms). */
    DURACION(Regex("[0-9]{1,9}")),
}

/**
 * **La lista cerrada de CLAVES que un evento puede llevar en sus datos**, cada una con su forma
 * ([FormaDeDato]). Una clave que no está acá, o un valor que no tiene la forma de su clave, rechaza
 * el evento entero ([problemaDelEvento]).
 *
 * Permitido: enums, ids de entidades (documento, movimiento, aviso, cuenta), estados, resultados,
 * códigos HTTP o de error, conteos y duraciones. Prohibido: montos, descripciones, comercios, nombres
 * y todo texto escrito por el usuario — ninguna forma los deja pasar.
 */
enum class ClaveDeUso(val clave: String, val forma: FormaDeDato, val valores: Set<String> = emptySet()) {
    PANTALLA("pantalla", FormaDeDato.NOMBRE),
    COMPONENTE("componente", FormaDeDato.NOMBRE),
    PASO("paso", FormaDeDato.LISTA, setOf("BIENVENIDA", "PERIODO", "CUENTAS", "CAPTURA", "RECURRENTES", "LISTO")),
    RESULTADO(
        "resultado", FormaDeDato.LISTA,
        setOf("OK", "ERROR", "SIN_CONEXION", "TERMINADA", "ABANDONADA", "CONFIRMADO", "DESCARTADO", "YA_ANOTADO", "NADA", "LLEGO"),
    ),
    CLASE(
        "clase", FormaDeDato.LISTA,
        setOf("GASTO", "INGRESO", "TRASPASO", "AVISO", "DEBITO", "PAGO_DE_TARJETA", "COMPROBANTE", "EXTRACTO", "PAPEL", "OTRO"),
    ),
    ORIGEN(
        "origen", FormaDeDato.LISTA,
        setOf("LIBRE", "SUGERIDA", "EXTRACTO", "PAPEL", "SMS", "NOTIFICACION", "CORREO", "YO", "OTRO", "FRIO", "VOLVIO"),
    ),
    ACCION(
        "accion", FormaDeDato.LISTA,
        setOf(
            "AGREGAR", "GUARDAR", "SIGUIENTE", "ATRAS", "SALTAR", "AHORA_NO", "GUARDAR_Y_SEGUIR",
            "CONFIRMAR", "DESCARTAR", "REVISAR", "CAMBIAR_TIPO", "ABRIR", "CERRAR",
        ),
    ),
    ESTADO("estado", FormaDeDato.ESTADO),
    CODIGO("codigo", FormaDeDato.CODIGO_HTTP),
    ERROR("error", FormaDeDato.ESTADO),
    METODO("metodo", FormaDeDato.LISTA, setOf("GET", "POST", "PUT", "PATCH", "DELETE")),
    RECURSO("recurso", FormaDeDato.SEGMENTO),
    DOCUMENTO_ID("documento_id", FormaDeDato.ID),
    MOVIMIENTO_ID("movimiento_id", FormaDeDato.ID),
    AVISO_ID("aviso_id", FormaDeDato.ID),
    CUENTA_ID("cuenta_id", FormaDeDato.ID),
    CUANTOS("cuantos", FormaDeDato.CONTEO),
    SEGUNDOS("segundos", FormaDeDato.DURACION),
    MS("ms", FormaDeDato.DURACION),
    ;

    fun admite(valor: String): Boolean =
        if (forma == FormaDeDato.LISTA) valor in valores else forma.regex!!.matches(valor)

    companion object {
        fun deClave(clave: String): ClaveDeUso? = entries.firstOrNull { it.clave == clave }
    }
}

/**
 * **Los eventos de uso: la lista cerrada de nombres**, cada uno con su [tipo] y la clave [principal]
 * (la que va a la columna `detalle` para contar sin leer el JSON). Cualquier evento puede llevar
 * cualquier clave de [ClaveDeUso]; los nombres sí son cerrados.
 *
 * Los que están en [FUNCIONES_CLAVE] son la adopción de funciones de la pantalla de admin.
 */
enum class EventoDeUso(val tipo: TipoDeUso, val principal: ClaveDeUso? = null) {
    /** Empezó una sesión: arranque en frío ([ClaveDeUso.ORIGEN] FRIO) o vuelta tras más de 30 min (VOLVIO). */
    SESION_EMPEZADA(TipoDeUso.SISTEMA, ClaveDeUso.ORIGEN),
    /** La app se fue a segundo plano; [ClaveDeUso.SEGUNDOS] es el tiempo a la vista de este tramo. */
    SEGUNDO_PLANO(TipoDeUso.SISTEMA, ClaveDeUso.SEGUNDOS),
    /** Volvió antes de 30 min: sigue la misma sesión. */
    PRIMER_PLANO(TipoDeUso.SISTEMA),
    PANTALLA(TipoDeUso.PANTALLA, ClaveDeUso.PANTALLA),
    /** Un toque: [ClaveDeUso.ACCION] dice cuál y [ClaveDeUso.PANTALLA] o [ClaveDeUso.COMPONENTE] dónde. */
    TOQUE(TipoDeUso.ACCION, ClaveDeUso.ACCION),
    /** Llegó a un paso de la guía de primer uso (el embudo). */
    GUIA_PASO(TipoDeUso.PANTALLA, ClaveDeUso.PASO),
    /** Salió de la guía: en el último paso, TERMINADA; antes («Ahora no»), ABANDONADA en ese paso. */
    GUIA_SALIDA(TipoDeUso.ACCION, ClaveDeUso.RESULTADO),
    /** Confirmó o descartó algo de «Por revisar» (un aviso, un débito automático, un pago de tarjeta). */
    POR_REVISAR(TipoDeUso.ACCION, ClaveDeUso.RESULTADO),
    /** Una escritura que el server contestó con 4xx o 5xx. */
    ESCRITURA_FALLIDA(TipoDeUso.SISTEMA, ClaveDeUso.CODIGO),
    /** Un pedido que no llegó al server (sin conexión, se cortó). */
    ERROR_DE_RED(TipoDeUso.SISTEMA, ClaveDeUso.RECURSO),
    /** Movi leyó un papel o un extracto, con su resultado y lo que tardó. */
    LECTURA_DE_PAPEL(TipoDeUso.SISTEMA, ClaveDeUso.RESULTADO),
    /** El teléfono subió lo que tenía guardado sin conexión. */
    SINCRONIZACION(TipoDeUso.SISTEMA, ClaveDeUso.RESULTADO),
    /** El teléfono capturó un aviso del banco y lo subió. */
    AVISO_CAPTURADO(TipoDeUso.SISTEMA, ClaveDeUso.ORIGEN),
    CUENTA_CREADA(TipoDeUso.ACCION),
    MOVIMIENTO_A_MANO(TipoDeUso.ACCION, ClaveDeUso.CLASE),
    MOVI_AI(TipoDeUso.ACCION, ClaveDeUso.ORIGEN),
    ESPACIO_REPARTIDO(TipoDeUso.ACCION, ClaveDeUso.ORIGEN),
    PAPEL_SUBIDO(TipoDeUso.ACCION, ClaveDeUso.ORIGEN),
    RECURRENTE_CREADO(TipoDeUso.ACCION),
    INICIO_EDITADO(TipoDeUso.ACCION),
    /**
     * La captura del banco funcionó ese día: lo escribe el **server**, una fila por usuario, origen y
     * día, cuando llega un SMS, una notificación o un correo. No cuenta como uso activo.
     */
    CAPTURA_ACTIVA(TipoDeUso.SISTEMA, ClaveDeUso.ORIGEN),
    ;

    companion object {
        fun deNombre(nombre: String): EventoDeUso? = entries.firstOrNull { it.name == nombre }
    }
}

/** Las funciones cuya adopción mide la pantalla de admin (% de los activos de 30 días que la usó). */
val FUNCIONES_CLAVE: List<EventoDeUso> = listOf(
    EventoDeUso.CUENTA_CREADA,
    EventoDeUso.MOVIMIENTO_A_MANO,
    EventoDeUso.POR_REVISAR,
    EventoDeUso.MOVI_AI,
    EventoDeUso.ESPACIO_REPARTIDO,
    EventoDeUso.PAPEL_SUBIDO,
    EventoDeUso.RECURRENTE_CREADO,
    EventoDeUso.GUIA_SALIDA,
    EventoDeUso.CAPTURA_ACTIVA,
    EventoDeUso.INICIO_EDITADO,
)

/** Plataformas que escriben eventos; `server` es la de [EventoDeUso.CAPTURA_ACTIVA]. */
val PLATAFORMAS_DE_USO: Set<String> = setOf("android", "ios", "web", "server")

/** Una sesión nueva cuando la app vuelve de segundo plano tras más de esto (o en un arranque en frío). */
const val MINUTOS_PARA_SESION_NUEVA = 30

/**
 * **Un tramo de la ruta**: el nombre de la pantalla y, si hace falta, su parámetro entre paréntesis
 * —un id, un período («2026-09»), un enum o «paso 4»—. Nunca un texto del usuario.
 */
private val TRAMO_DE_RUTA = Regex(
    "[A-Z][A-Za-z]{1,39}(?:\\((?:[A-Za-z_]{1,30}(?: [0-9]{1,3})?|[0-9]{4}-[0-9]{2}|" +
        "[a-z]{2,12}(?:_[a-z]{2,12})*_[0-9a-fA-F-]{8,80})\\))?",
)
const val MAX_RUTA = 300

/** ¿[ruta] es una pila de pantallas bien formada («Dashboard>PorRevisar>SMSReconcile(sms_rt_9c1d…)»)? */
fun rutaBienFormada(ruta: String): Boolean =
    ruta.isEmpty() || (ruta.length <= MAX_RUTA && ruta.split('>').all { TRAMO_DE_RUTA.matches(it) })

const val MAX_EVENTOS_POR_LOTE = 200

/**
 * **¿Qué tiene de malo este evento?** `null` si nada. Un nombre fuera de la lista, una clave fuera de
 * la lista, un valor que no tiene la forma de su clave o una ruta mal formada lo rechazan entero:
 * mejor perder un evento que guardar un monto por descuido.
 */
fun problemaDelEvento(nombre: String, datos: Map<String, String>, ruta: String = ""): String? {
    if (EventoDeUso.deNombre(nombre) == null) return "el evento «${nombre.take(40)}» no está en la lista"
    for ((clave, valor) in datos) {
        val c = ClaveDeUso.deClave(clave) ?: return "la clave «${clave.take(40)}» no está en la lista"
        if (!c.admite(valor)) return "«$clave» no admite ese valor"
    }
    if (!rutaBienFormada(ruta)) return "la ruta no tiene la forma de una pila de pantallas"
    return null
}

/** Un evento de uso como viaja al server. */
@Serializable
data class EventoDeUsoEnviado(
    /** Id del cliente: el mismo lote reenviado no duplica. */
    val id: String,
    val nombre: String,
    /** Los datos del evento: claves de [ClaveDeUso], cada valor con su forma. */
    val propiedades: Map<String, String> = emptyMap(),
    /** La pila de pantallas cuando pasó ([rutaBienFormada]). */
    val ruta: String = "",
    /** Uno por sesión: arranque en frío, o vuelta de segundo plano tras [MINUTOS_PARA_SESION_NUEVA] min. En la web, por pestaña. */
    val sesionId: String = "",
    /** El orden dentro de la sesión, desde 1. */
    val secuencia: Int = 0,
    val plataforma: String,
    val version: String = "",
    val ocurridoEn: Long,
)

@Serializable
data class LoteDeUso(val eventos: List<EventoDeUsoEnviado>)

@Serializable
data class RespuestaDeUso(val guardados: Int, val descartados: Int)

// ── Lo que ve la pantalla de admin ──────────────────────────────────────────────────────────

@Serializable
data class ActivosDelDia(val dia: String, val usuarios: Int)

@Serializable
data class Conteo(val nombre: String, val veces: Int, val usuarios: Int)

@Serializable
data class AdopcionDeFuncion(val funcion: String, val usuarios: Int, val porcentaje: Int)

/**
 * Una cohorte semanal: los que usaron Movi por primera vez la semana que empieza el lunes [semana], y
 * qué porcentaje volvió en la semana 1, 2, 4 y 8 después. `null` = esa semana todavía no pasó.
 */
@Serializable
data class CohorteSemanal(
    val semana: String,
    val usuarios: Int,
    val semana1: Int? = null,
    val semana2: Int? = null,
    val semana4: Int? = null,
    val semana8: Int? = null,
)

@Serializable
data class ResumenDeUso(
    val activosHoy: Int = 0,
    val activos7: Int = 0,
    val activos30: Int = 0,
    /** Los últimos 30 días, del más viejo al de hoy. */
    val activosPorDia: List<ActivosDelDia> = emptyList(),
    /** Las más abiertas en 30 días. */
    val pantallas: List<Conteo> = emptyList(),
    /** Cuántos llegaron a cada paso de la guía (90 días), en el orden de la guía. */
    val embudo: List<Conteo> = emptyList(),
    val adopcion: List<AdopcionDeFuncion> = emptyList(),
    val cohortes: List<CohorteSemanal> = emptyList(),
    /** Sesiones terminadas por usuario activo (30 días). */
    val sesionesPorUsuario: Double = 0.0,
    val duracionMedianaSegundos: Long? = null,
    /** Sesiones por plataforma y versión («android 1.90»), 30 días. */
    val sesionesPorPlataforma: List<Conteo> = emptyList(),
    /** Cuánto tardó el server en armar esto: el costo de las consultas. */
    val milisegundos: Long = 0,
)

// ── El visor del recorrido (solo admin) ──────────────────────────────────────────────────────

/** Un usuario con uso en los últimos 90 días, para elegirlo en el visor. */
@Serializable
data class UsuarioConUso(
    val id: String,
    /** El correo de la cuenta: el admin tiene que poder reconocer a quién mira. */
    val correo: String = "",
    val sesiones: Int,
    val eventos: Int,
    val ultimaVez: Long,
    val plataformas: List<String> = emptyList(),
)

/** Una sesión de un usuario: del primer evento al último. */
@Serializable
data class SesionDeUso(
    val id: String,
    val inicio: Long,
    val fin: Long,
    val segundos: Long,
    val plataforma: String,
    val version: String,
    val eventos: Int,
)

/** Un evento de la línea de tiempo de una sesión, en orden de [secuencia]. */
@Serializable
data class EventoDelRecorrido(
    val secuencia: Int,
    val hora: Long,
    val tipo: String,
    val nombre: String,
    val ruta: String,
    val datos: Map<String, String> = emptyMap(),
)
