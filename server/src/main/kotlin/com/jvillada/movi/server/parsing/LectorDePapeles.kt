package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * # Quién lee los papeles que el dueño comparte con Movi (Ola 2)
 *
 * Una interfaz y no llamadas sueltas a [ClaudeStatementParser] por una sola razón: **las pruebas no
 * pueden llamar a la API real de Anthropic**, nunca. [actual] es la costura — la app usa
 * [LectorDePapelesConClaude]; una prueba pone un lector falso y lo devuelve en su `@AfterTest`.
 *
 * Tres lecturas:
 *
 * - [queEs]: **qué es el papel** y, si es un comprobante de un solo movimiento, sus datos. Una sola
 *   llamada al modelo barato (Haiku): decidir y leer cinco campos no necesita el grande.
 * - [leerExtractoDeTexto] / [leerExtractoDeImagen]: el extracto entero, con el modelo y las reglas
 *   que ya usa el importador de siempre. Son las mismas funciones de [ClaudeStatementParser]; pasan
 *   por acá para que el camino del extracto también se pueda probar sin la API.
 */
interface LectorDePapeles {
    suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel
    suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura
    suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>): ClaudeStatementParser.Lectura

    companion object {
        /** El lector que usa el server. Las pruebas lo cambian y lo devuelven. */
        @Volatile
        var actual: LectorDePapeles = LectorDePapelesConClaude
    }
}

/** El de verdad: [ClaudeStatementParser], que sabe de la clave y del cliente de Anthropic. */
object LectorDePapelesConClaude : LectorDePapeles {
    override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = ClaudeStatementParser.queEsElPapel(contenido)
    override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>) = ClaudeStatementParser.leer(texto, reglas)
    override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>) =
        ClaudeStatementParser.leerImagen(bytes, mime, reglas)
}

/** Lo que se le muestra al modelo: la foto, o el texto de un PDF (más barato que mandarlo entero). */
sealed interface ContenidoDelPapel {
    class Imagen(val bytes: ByteArray, val mime: String) : ContenidoDelPapel
    data class Texto(val texto: String) : ContenidoDelPapel
}

/** La respuesta de [LectorDePapeles.queEs]. */
sealed interface QueDiceElPapel {
    /** Un solo movimiento, ya leído. */
    data class Comprobante(val leido: ComprobanteLeido) : QueDiceElPapel

    /** Varios movimientos: va por el importador de extractos. */
    data object Extracto : QueDiceElPapel

    /** No muestra ningún movimiento de plata (un certificado, un saldo, una publicidad). */
    data object SinMovimiento : QueDiceElPapel

    /** El server no tiene `ANTHROPIC_API_KEY`. No es culpa del archivo. */
    data object SinLlave : QueDiceElPapel

    /** El modelo contestó algo que no se entiende, o un comprobante sin monto. */
    data object Ilegible : QueDiceElPapel
}

/**
 * **Un comprobante de un solo movimiento, como lo leyó el modelo.** Es también lo que se guarda en
 * `lecturas_de_papeles.datos`: la segunda vez que llega el mismo archivo no se vuelve a mandar.
 */
@Serializable
data class ComprobanteLeido(
    val monto: Double,
    val moneda: String = "COP",
    val tipo: TransactionType = TransactionType.EXPENSE,
    /** `YYYY-MM-DD`, o `null` si el papel no la dice. */
    val fecha: String? = null,
    /** `HH:mm` en 24 horas, o `null`. */
    val hora: String? = null,
    /** A quién se le pagó o de quién llegó. Vacío si solo hay un número o una llave. */
    val comercio: String = "",
    val categoria: String? = null,
    val banco: String? = null,
    /** Los últimos dígitos de la cuenta o tarjeta del dueño. */
    val cuentaPropia: String? = null,
    /** El número de la cuenta de destino de una transferencia a otra persona. */
    val cuentaDestino: String? = null,
    val llave: String? = null,
    val concepto: String? = null,
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * **Lo que contestó el modelo, leído.** Pura y aparte para probarla sin la API: el JSON puede venir
 * envuelto en texto («Aquí está: {…}») o con números como cadenas, y nada de eso puede convertirse
 * en un movimiento inventado. Un comprobante sin monto positivo es [QueDiceElPapel.Ilegible].
 */
fun queDiceLaRespuesta(crudo: String): QueDiceElPapel {
    val abre = crudo.indexOf('{')
    val cierra = crudo.lastIndexOf('}')
    if (abre == -1 || cierra <= abre) return QueDiceElPapel.Ilegible
    val objeto = runCatching { json.parseToJsonElement(crudo.substring(abre, cierra + 1)).jsonObject }.getOrNull()
        ?: return QueDiceElPapel.Ilegible
    return when (objeto.texto("tipo")?.uppercase()) {
        "EXTRACTO" -> QueDiceElPapel.Extracto
        "NADA" -> QueDiceElPapel.SinMovimiento
        "COMPROBANTE" -> comprobanteDe(objeto) ?: QueDiceElPapel.Ilegible
        else -> QueDiceElPapel.Ilegible
    }
}

private fun JsonObject.texto(clave: String): String? =
    this[clave]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        ?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

private fun comprobanteDe(o: JsonObject): QueDiceElPapel.Comprobante? {
    val monto = o["monto"]?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
        ?: o.texto("monto")?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        ?: return null
    if (monto <= 0.0) return null
    val tipo = when (o.texto("movimiento")?.uppercase()) {
        "INCOME" -> TransactionType.INCOME
        else -> TransactionType.EXPENSE
    }
    val moneda = o.texto("moneda")?.uppercase()?.takeIf { it == "USD" } ?: "COP"
    return QueDiceElPapel.Comprobante(
        ComprobanteLeido(
            monto = monto,
            moneda = moneda,
            tipo = tipo,
            fecha = o.texto("fecha")?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) },
            hora = o.texto("hora")?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) }?.padStart(5, '0'),
            comercio = o.texto("comercio").orEmpty().take(120),
            categoria = o.texto("categoria")?.take(100),
            banco = o.texto("banco")?.take(60),
            cuentaPropia = o.texto("cuentaPropia")?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 },
            cuentaDestino = o.texto("cuentaDestino")?.filter { it.isDigit() }?.takeIf { it.length >= 4 },
            llave = o.texto("llave")?.take(60),
            concepto = o.texto("concepto")?.take(80),
        ),
    )
}

/**
 * Una línea con una fecha y un monto: la forma de una fila de extracto.
 *
 * Las fechas que reconoce, cada una sacada de un extracto real del dueño (benchmark 2026-10-04: con
 * solo las dos primeras reconocía 2 de 10 extractos, y los otros 8 pagaban una clasificación de más):
 * - `15/07`, `15/07/2026`, `15-07-26` — las tarjetas de Bancolombia;
 * - `2026-09-07` y `2026/09/07` — ISO, y el CSV de Davibank;
 * - `06 sept 2026`, `21 AGO 2026` — el listado de Bancolombia y la Nu;
 * - `20260828` — Davivienda.
 */
private val FILA_CON_FECHA_Y_MONTO = Regex(
    "(" +
        """\b\d{1,2}[/-]\d{1,2}([/-]\d{2,4})?\b""" +
        """|\b\d{4}[-/]\d{2}[-/]\d{2}\b""" +
        """|\b\d{1,2}\s+(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)[a-z]*\.?\s+\d{4}\b""" +
        """|\b20\d{6}\b""" +
        ")" +
        """.*\d{1,3}([.,]\d{3})+""",
    RegexOption.IGNORE_CASE,
)

/**
 * **¿El texto de este PDF ya se ve como un extracto?** Cuatro o más líneas con una fecha y un monto
 * son una lista de movimientos, no un comprobante: se manda derecho al importador y se ahorra la
 * llamada de clasificación. Ante la duda (menos de cuatro) decide el modelo.
 */
fun pareceUnExtracto(texto: String): Boolean =
    texto.lineSequence().count { FILA_CON_FECHA_Y_MONTO.containsMatchIn(it) } >= 4
