package com.jvillada.movi.server.routes

import kotlin.math.roundToLong
import kotlin.math.abs
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.push.WebPushSender
import com.jvillada.movi.server.push.buildSmsPushPayload
import com.jvillada.movi.server.sms.SmsDedupeIndex
import com.jvillada.movi.server.sms.memoriaDe
import com.jvillada.movi.server.sms.destinosDelDueno
import com.jvillada.movi.server.sms.SmsKey
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.conElDestinoConocido
import com.jvillada.movi.shared.model.categoriaProbablePorElNombre
import com.jvillada.movi.shared.model.huellaDeUnMovimiento
import com.jvillada.movi.shared.model.laHuellaEsUnNumero
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * El monto y su moneda. Bancolombia escribe **tres prefijos**: `$132.347,00`, `COP249.000,00` y
 * `USD20,00` —estos dos en las compras con tarjeta de crédito—, y la regex de antes solo conocía el
 * `$`: 23 de los 98 SMS pendientes del dueño no se leían (sep-2026), entre ellos todos sus cobros de
 * Microsoft, Uber, Google, Anthropic y Railway.
 */
private val amountRegex = Regex("""(\$|\bCOP|\bUSD)\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
/** «Recibimos pago por 9.809.799 a tu tarjeta»: sin prefijo, pero con separador de miles. */
private val amountPorRegex = Regex("""\bpor\s+([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
private val merchantInRegex = Regex("""\ben\s+(.+?)(?:\s+el\s|\s+a\s+las|\s+con\s+tu\s|\s+de\s+tu\s|,|\.|$)""", RegexOption.IGNORE_CASE)
private val merchantOfRegex = Regex("""\bde\s+(.+?)(?:\s+por\s|\.|$)""", RegexOption.IGNORE_CASE)
/**
 * **La compra de Nu**: «Tu compra en CREPES Y WAFFLES LEMON por $130.200,00 con tu tarjeta terminada
 * en 1336 ha sido APROBADA.» El comercio va entre «compra en» y «por $…». [merchantInRegex] no
 * sirve acá: corta en el primer punto, y el primer punto es el de miles del monto, así que leía
 * «CREPES Y WAFFLES LEMON por $130».
 */
private val compraEnPorRegex = Regex("""\bcompra\s+en\s+(?!tu\s)(.+?)\s+por\s+(?:\$|COP\b|USD\b)""", RegexOption.IGNORE_CASE)
/** «Pagaste $138,600.00 a Coomeva Medicina Prepagada S A desde tu producto 8133». */
private val destinatarioDesdeRegex = Regex("""\ba\s+(?!la\s|las\s|tu\s)(.+?)\s+desde\s""", RegexOption.IGNORE_CASE)
/** «… desde tu cuenta *8133 a DANIEL LEONETT el 10/09/26». */
private val destinatarioElRegex = Regex("""\ba\s+(?!la\s|las\s|tu\s)([^*@\d].+?)\s+el\s""", RegexOption.IGNORE_CASE)

/**
 * **La llave a la que se pagó, y la cuenta a la que se transfirió.** Sin esto, todos los pagos por
 * QR del dueño se llamaban «Pago QR» y todas sus transferencias sin nombre, «Transferencia»: el
 * único dato que separa uno de otro —la llave, el número de cuenta— se tiraba al leer el mensaje.
 *
 * Es lo que hace posible [com.jvillada.movi.shared.model.MemoriaDeCategorias]: sin un nombre que
 * distinga dos destinatarios, no hay nada que recordar de ninguno.
 */
private val llaveRegex = Regex("""\bllave\s+(@?[A-Za-z0-9._-]{3,})""", RegexOption.IGNORE_CASE)

/**
 * La cuenta **de destino**, que no es la de origen: `desde tu cuenta *3333` es de dónde salió la
 * plata y no identifica a nadie. Solo cuenta la que viene detrás de un « a ».
 */
private val cuentaDestinoRegex = Regex("""\ba\s+(?:la\s+)?cuenta\s+\*?\s?(\d{4,})""", RegexOption.IGNORE_CASE)

/**
 * Avisos del banco que traen plata en el texto pero **no son un movimiento**: confirmarlos crearía
 * uno falso. La ampliación de plazo es el caso caro («por USD 1,202.49»): no salió ni entró un peso,
 * se refinanció una deuda.
 */
private val NO_SON_MOVIMIENTOS = listOf("ampliacion de plazo", "ampliación de plazo", "bienvenido", "inscribiste")

/**
 * **Lo que el banco intentó y no pasó**, de cualquier banco: una compra rechazada trae el monto y el
 * comercio igual que una aprobada, y leída como gasto era plata que nunca salió. Nació con Nu
 * («Compra rechazada: Tu compra en RAPPI por $45.000,00 … fue rechazada.»), que se captura entera
 * desde #346, pero Bancolombia manda lo mismo («Transacción rechazada»). Mismo mecanismo que
 * [NO_SON_MOVIMIENTOS]: aparece la frase y no hay movimiento.
 */
private val NO_PASARON = listOf(
    "rechazada", "rechazado",
    "declinada", "declinado",
    "no aprobada", "no aprobado", "no fue aprobada", "no fue aprobado",
    "no exitosa", "no exitoso", "no fue exitosa", "no fue exitoso",
)

/** El rótulo de origen nombra a Nu como palabra («Notificación · Nu»). El mismo criterio que `tarjetaDeNu`. */
private val origenNu = Regex("""\bnu(?:bank)?\b""", RegexOption.IGNORE_CASE)

/**
 * Avisos de Nu que traen plata pero no son una compra ni un pago: la factura que vence, el pago
 * mínimo, lo que rindió la Cajita. Van ANTES de la lista de lo que sí es movimiento porque «tu pago
 * mínimo» dice «pago».
 */
private val NU_NO_SON_MOVIMIENTOS = listOf(
    "pago mínimo", "pago minimo", "fecha límite", "fecha limite", "rendimiento", "cajita",
)

/** «Tu pago de $X fue recibido», «Recibimos tu pago»: el abono a la tarjeta. */
private val pagoDeNu = Regex("""recibimos tu pago|\bpago\b.*\b(recibido|aplicado|abonado)\b""", RegexOption.IGNORE_CASE)

/**
 * **De Nu solo se lee lo que es una compra aprobada o un pago.** Desde #346 el teléfono sube TODAS
 * las notificaciones de `com.nu.production`, y el lector genérico convierte en gasto cualquier
 * texto con un monto: la factura del mes, una promoción, lo que rindió la Cajita. En vez de ir
 * tachando avisos a medida que aparecen, con Nu se pide la forma de un movimiento: la compra que
 * dice «aprobada» o el pago recibido. Lo demás no es un movimiento.
 *
 * Solo aplica cuando el origen dice Nu: un SMS de Bancolombia no pasa por acá.
 */
private fun loDeNuEsUnMovimiento(minusculas: String): Boolean {
    if (NU_NO_SON_MOVIMIENTOS.any { it in minusculas }) return false
    val esCompra = "compra" in minusculas && ("aprobada" in minusculas || "aprobado" in minusculas)
    return esCompra || pagoDeNu.containsMatchIn(minusculas)
}

/**
 * **Cuánta plata dice un SMS**, sin importar si el banco escribió a la colombiana o a la gringa.
 *
 * ### El bug que esto arregla, y por qué era peor de lo que parecía
 *
 * Antes acá había una línea: `raw.replace(".", "").replace(",", ".")` — o sea, dar por sentado que
 * el punto separa miles y la coma decimales. Bancolombia manda **las dos formas**, y con las de
 * coma esa línea no fallaba: mentía.
 *
 * | SMS | De verdad | Lo que se leía |
 * |---|---|---|
 * | `$3,500,000.00` | 3.500.000 | nada: `null`, «no pude parsear» |
 * | `$20,417` | 20.417 | **20,42** |
 * | `$24,000.00` | 24.000 | **24** |
 * | `$80.894` | 80.894 | 80.894 |
 *
 * El único que se notaba era el primero. Los otros dos entraban como sugerencia mil veces más
 * chica, y el dueño los tenía a la vista en una bandeja de 96 mensajes esperando confirmación.
 *
 * ### Cómo se decide cuál separador es cuál
 *
 * 1. **Si aparecen los dos caracteres**, el ÚLTIMO es el decimal y el otro es de miles. Cubre
 *    `1.234.567,89` y `3,500,000.00` sin saber de qué país viene ninguno.
 * 2. **Si aparece uno solo y más de una vez**, es de miles: `3,500,000`.
 * 3. **Si aparece una sola vez**, decide cuántos dígitos lo siguen: exactamente tres son miles
 *    (`20,417`, `80.894`), cualquier otra cantidad son decimales (`3,5`, `1.50`).
 *
 * La regla 3 es la única con una zona gris de verdad —`1,234` podría ser mil doscientos treinta y
 * cuatro o uno con doscientos treinta y cuatro milésimos— y se resuelve del lado de los miles a
 * propósito: estos mensajes hablan de pesos colombianos, donde tres decimales no existen y los
 * montos de cuatro cifras son el pan de cada día.
 */
internal fun montoDelSms(raw: String): Double? {
    val ultimaComa = raw.lastIndexOf(',')
    val ultimoPunto = raw.lastIndexOf('.')
    if (ultimaComa < 0 && ultimoPunto < 0) return raw.toDoubleOrNull()

    val separadorDecimal: Char? = when {
        // Los dos están: manda el último.
        ultimaComa >= 0 && ultimoPunto >= 0 -> if (ultimaComa > ultimoPunto) ',' else '.'
        else -> {
            val cual = if (ultimaComa >= 0) ',' else '.'
            val veces = raw.count { it == cual }
            val digitosDespues = raw.length - raw.lastIndexOf(cual) - 1
            // Repetido = miles. Una sola vez = miles solo si separa un grupo de tres.
            if (veces > 1 || digitosDespues == 3) null else cual
        }
    }
    val entero = raw.filter { it.isDigit() || it == separadorDecimal }
    return (if (separadorDecimal == null) entero else entero.replace(separadorDecimal, '.'))
        .toDoubleOrNull()
}

/**
 * @param origen el rótulo `bank` de la fila («85540», «Notificación · Nu», «Correo · Bancolombia»).
 *   Solo decide si aplica la regla de Nu ([loDeNuEsUnMovimiento]); sin él se lee como siempre.
 */
internal fun parseSms(text: String, origen: String? = null): ParsedSms? {
    val minusculas = text.lowercase()
    if (NO_SON_MOVIMIENTOS.any { it in minusculas }) return null
    if (NO_PASARON.any { it in minusculas }) return null
    if (origen != null && origenNu.containsMatchIn(origen) && !loDeNuEsUnMovimiento(minusculas)) return null
    val conPrefijo = amountRegex.find(text)
    val rawAmount = conPrefijo?.groupValues?.get(2) ?: amountPorRegex.find(text)?.groupValues?.get(1) ?: return null
    val amount = montoDelSms(rawAmount) ?: return null
    val currency = if (conPrefijo?.groupValues?.get(1)?.equals("USD", ignoreCase = true) == true) "USD" else "COP"

    val type = when {
        text.contains("Recibiste", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Nómina recibida", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Compra", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Pago", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Retiro", ignoreCase = true) -> TransactionType.EXPENSE
        else -> TransactionType.EXPENSE
    }

    fun limpio(m: String?) = m?.trim()?.trimEnd(',', '.')?.trim()?.takeIf { it.isNotEmpty() }
    val merchant = when {
        text.contains("Nómina recibida", ignoreCase = true) -> "Nómina"
        type == TransactionType.INCOME -> limpio(merchantOfRegex.find(text)?.groupValues?.get(1)) ?: "Transferencia recibida"
        looksLikeCardPayment(text, category = "") -> "Pago de tarjeta"
        // Un pago por QR puede venir con el nombre del comercio («por codigo QR en Mora Soccer»);
        // cuando no, la llave es lo único que lo distingue del pago por QR de mañana.
        "codigo qr" in minusculas || "código qr" in minusculas ->
            limpio(merchantInRegex.find(text)?.groupValues?.get(1))
                ?: llaveRegex.find(text)?.let { "Pago QR · llave ${it.groupValues[1]}" }
                ?: "Pago QR"
        else -> limpio(compraEnPorRegex.find(text)?.groupValues?.get(1))
            ?: limpio(destinatarioDesdeRegex.find(text)?.groupValues?.get(1))
            ?: limpio(destinatarioElRegex.find(text)?.groupValues?.get(1))
            ?: limpio(merchantInRegex.find(text)?.groupValues?.get(1))
            ?: cuentaDestinoRegex.find(text)?.let { "Transferencia a la cuenta *${it.groupValues[1]}" }
            ?: if ("transferiste" in minusculas) "Transferencia" else "Movimiento"
    }

    val category = categoryFor(text, merchant, type)
    return ParsedSms(amount, merchant, type, category, currency)
}

/**
 * [text] es el SMS completo, no solo [merchant]: "pago tc"/"pago tarjeta" viven en frases como
 * "Pago autom TC ...1234 por $80.894" que `merchantInRegex` no captura (no tiene "en <algo>"),
 * así que el merchant extraído llega como "Movimiento" y perdería la señal. Se revisa el texto
 * crudo antes de caer en las reglas por merchant.
 */
private fun categoryFor(text: String, merchant: String, type: TransactionType): String {
    if (type == TransactionType.EXPENSE && looksLikeCardPayment(text, category = "")) {
        return CARD_PAYMENT_CATEGORY
    }
    if (type == TransactionType.INCOME) {
        return if (merchant.equals("Nómina", true)) "Nómina" else "Transferencia"
    }
    return categoriaProbablePorElNombre(merchant) ?: SIN_CATEGORIA
}

/**
 * **Lo que Movi propone cuando no sabe.** Se llamaba «Otro», en singular, y era el único lugar de
 * toda la app donde se llamaba así: los selectores, las predefinidas y los datos del dueño dicen
 * «Otros». Un SMS confirmado abría entonces una categoría paralela de un solo movimiento, y el
 * gráfico de «en qué se fue la plata» la mostraba aparte.
 */
internal const val SIN_CATEGORIA = "Otros"

/**
 * **La propuesta, ya pasada por la memoria del dueño.** Lo que él anotó antes para este mismo
 * destinatario le gana a cualquier tabla de palabras clave — y le gana también a `Otros`, que es
 * justo el renglón que él quería ver desaparecer.
 *
 * Tres cosas quedan **fuera** del alcance de la memoria a propósito:
 *
 * - **El pago de tarjeta**, que no es una categoría de gasto sino una regla de plata (ver
 *   `looksLikeCardPayment` y `isCashFlow`): si la memoria pudiera moverlo, un abono a la AMEX
 *   volvería a contarse como gasto del mes.
 * - **El monto y el tipo**, que los dice el banco y no se adivinan.
 * - **El nombre, cuando el banco mandó uno de verdad.** Solo se reemplaza cuando la huella es un
 *   número (una llave, una cuenta): «llave 0092184713» no lo puede leer nadie, y si el dueño ya le
 *   puso nombre a ese destinatario, ese nombre es suyo.
 */
internal fun conLoQueMoviRecuerda(parsed: ParsedSms, memoria: MemoriaDeCategorias): ParsedSms {
    if (parsed.category == CARD_PAYMENT_CATEGORY) return parsed
    val recuerdo = memoria.recuerdoDe(parsed.merchant) ?: return parsed
    val huella = huellaDeUnMovimiento(parsed.merchant)
    return parsed.copy(
        category = recuerdo.categoria,
        merchant = if (huella != null && laHuellaEsUnNumero(huella)) recuerdo.nombre else parsed.merchant,
        aprendidoDe = if (recuerdo.cuantos == 1) {
            "Así lo anotaste la última vez"
        } else {
            "Así lo anotaste ${recuerdo.cuantos} veces"
        },
    )
}

/** Cuántos días alrededor del mensaje se busca lo ya anotado: un gasto se anota el día o un par después. */
internal const val DIAS_PARA_COINCIDIR: Long = 3

/**
 * **¿Este SMS ya está anotado?** Los movimientos vivos con el mismo monto (redondeado, como se
 * guarda), la misma moneda y el mismo tipo, a [DIAS_PARA_COINCIDIR] días o menos del mensaje, del
 * más cercano al más lejano. Máximo tres: más es una lista, no una propuesta.
 *
 * El monto sí filtra acá, a diferencia del emparejador de recurrentes: un SMS dice la cifra exacta
 * que se movió, así que un movimiento con otro monto no es este.
 */
internal fun coincidenciasDelSms(parsed: ParsedSms, momento: Long, eventos: List<FinancialEvent>): List<FinancialEvent> {
    val monto = parsed.amount.roundToLong()
    val margen = DIAS_PARA_COINCIDIR * 86_400_000L
    return eventos
        // Un pago de tarjeta se anota en Movi como abono a la cuenta de la tarjeta (un ingreso ahí),
        // mientras el SMS lo lee como salida: con el tipo exigido, los abonos a la AMEX de 9.000.000 y
        // 9.809.799 nunca se encontraban. En esa categoría el tipo no identifica.
        .filter { it.amount == monto && it.currency == parsed.currency }
        .filter { parsed.category == CARD_PAYMENT_CATEGORY || it.type == parsed.type }
        .filter { abs(it.timestamp - momento) <= margen }
        .sortedBy { abs(it.timestamp - momento) }
        .take(3)
}

fun Route.smsRoutes() {
    /**
     * La bandeja, **del más nuevo al más viejo**.
     *
     * Hasta acá esta consulta no tenía `ORDER BY`, y sin uno Postgres devuelve las filas en el
     * orden que le convenga — que no es el de inserción ni ningún otro que signifique algo. El
     * dueño lo vio con 96 mensajes adentro: *«el último mensaje recibido queda de último en la
     * lista, debe ser el primero»*. En su pantalla ni siquiera quedaba ascendente: dos de agosto
     * arriba y uno del 10 de septiembre abajo.
     *
     * **Se ordena por el texto de `time`, y está bien.** La columna es `varchar` y la escribe el
     * teléfono con `SimpleDateFormat("yyyy-MM-dd HH:mm")` (ver `SmsSync`): en ese formato el orden
     * alfabético **es** el cronológico, porque cada campo va de más significativo a menos y con
     * ancho fijo. Cambiar la columna a timestamp sería una migración sobre una tabla con datos
     * para no ganar nada acá.
     *
     * El cliente vuelve a ordenar por su cuenta (ver `mensajesMasRecientesPrimero`): no por
     * desconfianza de este `ORDER BY`, sino porque el orden de una lista que el dueño lee no
     * debería depender de que un endpoint se acuerde.
     */
    get("/api/sms") {
        val uid = call.userId()
        val list = dbQuery {
            SmsMessages.selectAll()
                .where { SmsMessages.userId eq uid }
                .orderBy(SmsMessages.time to SortOrder.DESC)
                .map { it.toSmsMessage() }
        }
        call.respond(list)
    }

    get("/api/sms/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(sms)
    }

    get("/api/sms/{id}/parse") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        val parsed = parseSms(sms.text, sms.bank)
            // No es un error de la app: el mensaje no trae un movimiento (un aviso, una ampliación de
            // plazo). Se dice así, porque la pantalla muestra este texto.
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, "Este mensaje no trae un movimiento para anotar. Puedes ignorarlo.")
        // La historia del dueño entra acá y no adentro de `parseSms`: ese mismo parseo lo usan el
        // sync y la push, donde no hay a quién consultarle nada.
        //
        // **Y las cuentas de otros van DESPUÉS de la memoria**, no antes. Ese orden es la decisión:
        // si el dueño a ese número ya lo llamó «Mercado», sigue diciendo «Mercado» — el nombre que
        // él puso es suyo. El destino solo habla donde no hablaba nadie, que es el caso real («a la
        // cuenta *31973270756» no lo puede leer ningún humano). Ver `conElDestinoConocido`.
        val memoria = dbQuery { memoriaDe(uid) }
        val destinos = dbQuery { destinosDelDueno(uid) }
        call.respond(conElDestinoConocido(conLoQueMoviRecuerda(parsed, memoria), sms.text, destinos))
    }

    get("/api/sms/{id}/coincidencias") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        val parsed = parseSms(sms.text, sms.bank) ?: return@get call.respond(emptyList<FinancialEvent>())
        val momento = momentoDelSms(sms.time, ahora = System.currentTimeMillis())
        val margen = DIAS_PARA_COINCIDIR * 86_400_000L
        val eventos = dbQuery { loadEventsBetween(uid, momento - margen, momento + margen + 1) }
        call.respond(coincidenciasDelSms(parsed, momento, eventos))
    }

    post("/api/sms/{id}/confirm") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = dbQuery {
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = SMS_STATE_CONFIRMED
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.OK)
    }

    post("/api/sms/{id}/ignore") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = dbQuery {
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = SMS_STATE_IGNORED
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }

    post("/api/sms/sync") {
        val uid = call.userId()
        val messages = call.receive<List<SmsMessage>>()
        val inserted = mutableListOf<SmsMessage>()
        val insertedCount = dbQuery {
            // Collect existing rows for this user so we can skip duplicates
            // without touching rows that may already have a user-set state.
            // One query per sync, never one per message.
            val existingRows = SmsMessages
                .selectAll()
                .where { SmsMessages.userId eq uid }
                .map { Triple(it[SmsMessages.id], it[SmsMessages.text], it[SmsMessages.time]) }

            val existingIds = existingRows.map { it.first }.toSet()

            // Dedupe cross-esquema por texto + tiempo (issue #27).
            //
            // El camino realtime inserta ids `sms_rt_<16hex>` y el backfill del inbox
            // inserta ids `sms_<32hex>` para el MISMO SMS físico: hashean fuentes de
            // timestamp distintas, así que los ids nunca coinciden y el chequeo por id
            // de arriba deja pasar el mismo SMS bancario dos veces.
            //
            // El texto tampoco alcanza como clave. Los SMS de compra no traen fecha, ni
            // hora, ni referencia ("Compra aprobada $28.500 en Uber BV."), así que dos
            // transacciones REALES distintas producen texto byte-idéntico y el dedupe por
            // texto se comía la segunda en silencio, con `synced` mintiendo. En una app de
            // finanzas personales perder un movimiento sin señal es peor que mostrar un
            // duplicado que el usuario puede ignorar.
            //
            // Lo que separa los dos casos es el tiempo: las dos fuentes fechan el mismo
            // SMS físico a lo sumo un minuto aparte tras truncar a minutos, mientras que
            // dos transacciones distintas están a horas o días. Ver SMS_DEDUPE_TOLERANCE
            // para el residual conocido (delay de entrega) y por qué no se ensancha para
            // cubrirlo. `bank` sigue fuera de la clave: los dos caminos divergen en el
            // remitente vacío (backfill "" vs realtime "SMS").
            val dedupe = SmsDedupeIndex(existingRows.map { SmsKey(it.second, it.third) })

            // Repeats del mismo id dentro de UN payload: el chequeo por texto+tiempo de
            // arriba solo los atrapa si el tiempo parsea (mismo id ⇒ mismo texto y tiempo
            // ⇒ mismo SmsKey). Si no parsea, ambos caen en el fallback "ilegible → insertar"
            // y el segundo insert choca contra la primary key, abortando la transacción
            // entera del sync. seenIds los filtra antes de llegar ahí, sin depender del parseo.
            val seenIds = mutableSetOf<String>()

            var count = 0
            for (msg in messages) {
                if (msg.id in existingIds) continue
                if (!seenIds.add(msg.id)) continue
                val key = SmsKey(msg.text, msg.time)
                if (dedupe.isDuplicate(key)) continue
                SmsMessages.insert {
                    it[id]     = msg.id
                    it[userId] = uid
                    it[time]   = msg.time
                    it[bank]   = msg.bank
                    it[text]   = msg.text
                    // El server es dueño del estado: /confirm y /ignore lo mueven, el cliente
                    // nunca lo decide. "pending" es el nombre único del recién llegado en todo
                    // el sistema (ver SmsMessages en Tables.kt) — salvo el aviso de una app que
                    // no puede ser plata, que entra ya ignorado: ver [estadoAlLlegar].
                    it[state]  = estadoAlLlegar(msg)
                    it[det]    = msg.det
                }
                dedupe.add(key)
                inserted += msg
                count++
            }
            count
        }

        // Hook de push (spec sms-realtime): SMS nuevos parseables → una push agrupada.
        // Best-effort — jamás falla el sync; los que no parsean quedan en el inbox como siempre.
        //
        // Scoped to realtime captures only (final review fix): manual pull syncs
        // (SmsReader.android.kt) upload historical, unfiltered inbox contents — pushing
        // for those would spam the user with notifications for old SMS. Only messages
        // captured live by SmsRealtimeReceiver (ids prefixed `sms_rt_`) participate.
        val realtimeCaptures = inserted.filter { it.id.startsWith("sms_rt_") }
        if (realtimeCaptures.isNotEmpty() && WebPushSender.isConfigured()) {
            runCatching {
                // La push también dice el nombre del destino: sin esto el aviso del teléfono decía
                // «Transferencia a la cuenta *31973270756» mientras la pantalla —que sí pasa por
                // /parse— decía «Transferencia a Caro». El mismo hecho, con dos nombres.
                val destinos = dbQuery { destinosDelDueno(uid) }
                val parsed = realtimeCaptures.mapNotNull { msg ->
                    parseSms(msg.text, msg.bank)?.let { conElDestinoConocido(it, msg.text, destinos) }
                }
                if (parsed.isNotEmpty()) WebPushSender.sendToUser(uid, buildSmsPushPayload(parsed))
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                call.application.log.warn("push de sms-sync falló para $uid", it)
            }
        }

        call.respond(mapOf("synced" to insertedCount))
    }
}

/**
 * **Con qué estado entra un mensaje a la bandeja.** Casi siempre `pending`: la bandeja es del
 * dueño y lo que no se sabe leer se le muestra, porque perder un movimiento sin señal es peor que
 * enseñarle un mensaje de más (ver el dedupe de arriba, que razona igual).
 *
 * La excepción es **el aviso de una app que no trae ni un número**. El 22-sep Google Wallet
 * publicó «Set up a shortcut to pay: Now you can double press the power button…», la captura de
 * notificaciones lo subió como cualquier otra, y quedó en la bandeja del dueño esperando que lo
 * confirmara como movimiento. Las apps de pago publican de todo —consejos, promociones, pasos de
 * configuración— y un movimiento, en cambio, **siempre** trae un monto.
 *
 * Por eso el criterio es tan corto, y es a propósito: **ni un dígito**. No se le pide al parser
 * que decida, porque el parser puede no conocer el formato de un pago nuevo y entonces una compra
 * de verdad se iría a ignorados sin que nadie la viera. Sin ningún número, en cambio, no hay monto
 * posible — es el único lado en el que este filtro no puede equivocarse. Una promo que diga «5 %
 * de descuento» sigue entrando pendiente, y está bien: es el error barato.
 *
 * **Solo para notificaciones.** Un SMS del banco sin números —«actualizaste tu clave»— el dueño
 * lo quiere ver: es su canal con el banco y ahí decide él.
 *
 * Y **ignorar no es borrar**: la fila se guarda con `ignored`, igual que cuando el dueño toca
 * «Ignorar». Si algún día aparece algo que no debió caer acá, está en la base.
 */
internal fun estadoAlLlegar(msg: SmsMessage): String =
    if (esUnaNotificacion(msg.bank) && msg.text.none { it.isDigit() }) SMS_STATE_IGNORED
    else SMS_STATE_PENDING

/**
 * ¿La fila la subió la captura de notificaciones? El teléfono las rotula «Notificación · Nombre de
 * la app» (`FiltroDeNotificaciones.kt`, en `:shared`); los SMS llegan con el código del remitente
 * («85540») y los correos con «Correo · …».
 */
internal fun esUnaNotificacion(origen: String): Boolean =
    origen.trimStart().startsWith("Notificación", ignoreCase = true)

private fun org.jetbrains.exposed.sql.ResultRow.toSmsMessage() = SmsMessage(
    id    = this[SmsMessages.id],
    time  = this[SmsMessages.time],
    bank  = this[SmsMessages.bank],
    text  = this[SmsMessages.text],
    state = this[SmsMessages.state],
    det   = this[SmsMessages.det],
)
