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
import com.jvillada.movi.server.sms.origenesMudosDe
import com.jvillada.movi.server.sms.destinosDelDueno
import com.jvillada.movi.server.sms.SmsKey
import com.jvillada.movi.server.sms.motivoParaApartar
import com.jvillada.movi.server.sms.loQueMoviSabeDelPagoDe
import com.jvillada.movi.server.sms.numerosPropiosDe
import com.jvillada.movi.server.sms.sinLaCuentaPropia
import com.jvillada.movi.shared.model.MOTIVO_DEVUELTO
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.motivoDeApartado
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.conElDestinoConocido
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.categoriaProbablePorElNombre
import com.jvillada.movi.shared.model.huellaDeUnMovimiento
import com.jvillada.movi.shared.model.laHuellaEsUnNumero
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.AvisoPorRevisar
import com.jvillada.movi.shared.model.AvisosDelMismoPago
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.RespuestaDelSync
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.esUnComprobante
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * El monto y su moneda. Bancolombia escribe **tres prefijos**: `$132.347,00`, `COP249.000,00` y
 * `USD20,00` —estos dos en las compras con tarjeta de crédito—, y la regex de antes solo conocía el
 * `$`: 23 de los 98 SMS pendientes del dueño no se leían (sep-2026), entre ellos todos sus cobros de
 * Microsoft, Uber, Google, Anthropic y Railway.
 *
 * **Y el monto sin separadores** (4-oct-2026): el correo de Bancolombia escribe «Pagaste $386902 en
 * la tarjeta…», todo pegado. La forma de arriba exigía grupos de tres después del primer bloque, así
 * que se quedaba con «386»: un pago de $386.902 se proponía de $386, mil veces más chico y sin
 * ninguna señal. Ahora hay dos formas: la de siempre, con al menos un separador de miles, y una
 * corrida de dígitos con decimales opcionales. La primera se prueba antes, así que todo lo que ya se
 * leía con separadores se sigue leyendo igual; lo que no los trae se lee entero.
 */
private val amountRegex = Regex(
    """(\$|\bCOP|\bUSD)\s*([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]+)?|[0-9]+(?:[.,][0-9]+)?)""",
    RegexOption.IGNORE_CASE,
)
/** «Recibimos pago por 9.809.799 a tu tarjeta»: sin prefijo, pero con separador de miles. */
private val amountPorRegex = Regex("""\bpor\s+([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
/** «Recibiste 300.000,00 en tu cuenta» (Nu): sin prefijo ni «por», pero con separador de miles. */
private val amountRecibisteRegex = Regex("""\brecibiste\s+([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
/**
 * «recargó 640.000,00 COP en tu tarjeta de beneficios» (Glim): la moneda va DESPUÉS del número. Sin
 * esto el aviso no tenía monto y quedaba en «Por revisar» sin poder leerse (1-oct-2026).
 */
private val amountSufijoRegex = Regex("""([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]+)?)\s*(COP|USD)\b""", RegexOption.IGNORE_CASE)

/**
 * **La recarga de una tarjeta de beneficios** (Glim: «Aleluya: ¡tienes nuevo saldo! 💳: Mercado Libre
 * Colombia Ltda recargó 640.000,00 COP en tu tarjeta de beneficios.»). Es plata que ENTRA, y quien
 * recarga es el empleador: el nombre es lo que va entre el último «:» y «recargó». El dueño anotaba
 * estas recargas a mano como «Salario» (ver `ev_glim_20260902`), por eso esa es la categoría.
 */
private val recargaDeBeneficiosRegex = Regex("""([^:]+?)\s+recarg[oó]\s""", RegexOption.IGNORE_CASE)
private val merchantInRegex = Regex("""\ben\s+(.+?)(?:\s+el\s|\s+a\s+las|\s+con\s+tu\s|\s+de\s+tu\s|,|\.|$)""", RegexOption.IGNORE_CASE)
private val merchantOfRegex = Regex("""\bde\s+(.+?)(?:\s+por\s|\s+con\s+tu\s|\.|$)""", RegexOption.IGNORE_CASE)
/**
 * **La compra de Nu**: «Tu compra en CREPES Y WAFFLES LEMON por $130.200,00 con tu tarjeta terminada
 * en 1336 ha sido APROBADA.» El comercio va entre «compra en» y «por $…». [merchantInRegex] no
 * sirve acá: corta en el primer punto, y el primer punto es el de miles del monto, así que leía
 * «CREPES Y WAFFLES LEMON por $130».
 */
private val compraEnPorRegex = Regex("""\bcompra\s+en\s+(?!tu\s)(.+?)\s+por\s+(?:\$|COP\b|USD\b)""", RegexOption.IGNORE_CASE)
/**
 * **El aviso de Google Wallet**: «TOSTAO CAFE Y PAN VISC: COP15,100 with Glim ••3037». El comercio es
 * todo lo que va antes de los dos puntos, y se toma entero: Wallet lo copia tal cual lo manda la
 * red de la tarjeta, y cortarle palabras («VISC») sería inventar un nombre que nadie escribió. Sin
 * esto el aviso no tenía ninguna frase conocida («compra en», «en …») y se anotaba como «Movimiento».
 */
private val pagoDeWalletRegex = Regex("""^\s*([^:]+?)\s*:\s*(?:COP|USD)\s*[0-9][0-9.,]*\s+with\s+""", RegexOption.IGNORE_CASE)
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
 * plata y no identifica a nadie. Solo cuenta la que viene detrás de un « a » o de un « hacia »: el
 * retiro de la Fiducuenta dice «Retiraste $3,500,000.00 de tu cuenta *9586 Fiducuenta …, hacia la
 * cuenta *25318624146», y sin el «hacia» su comercio salía «Movimiento».
 */
private val cuentaDestinoRegex = Regex("""\b(?:a|hacia)\s+(?:la\s+)?cuenta\s+\*?\s?(\d{4,})""", RegexOption.IGNORE_CASE)

/**
 * **El débito programado de Bancolombia**: «Bancolombia informa pago Factura Programada IGS
 * MULTIASISTE Ref 12019266 por $36.890,00 desde Aho*8133.» El nombre de quien cobra va entre
 * «Factura Programada» y «Ref»; sin esto el comercio salía «Movimiento» (2 SMS en septiembre, y el
 * mismo texto llega por correo con «Notificación Informativa» adelante).
 */
private val facturaProgramadaRegex = Regex("""\bfactura\s+programada\s+(.+?)\s+ref\b""", RegexOption.IGNORE_CASE)

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
internal val NO_PASARON = listOf(
    "rechazada", "rechazado",
    "declinada", "declinado",
    // Google Wallet, en inglés: «FARMATODO: DECLINED - COP17,150 with Glim ••3037». Se leía como un
    // gasto de $17.150 que nunca salió (el dueño lo tuvo que ignorar a mano, 2-oct-2026).
    "declined",
    "no aprobada", "no aprobado", "no fue aprobada", "no fue aprobado",
    "no exitosa", "no exitoso", "no fue exitosa", "no fue exitoso",
    // Glim: «Fondos insuficientes ⛔: Se rechazó tu pago por $17.150,00 COP.» — el pretérito no
    // contiene «rechazada» y se leía como un gasto de $17.150 que nunca salió.
    "se rechazó", "se rechazo", "fondos insuficientes",
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
 * «Recibiste 300.000,00 en tu cuenta: Te llegó dinero de …»: plata que entra a la cuenta de Nu.
 * Hasta el 23-sep esto no era «la forma de un movimiento» y el dueño lo veía en «Reconciliar
 * movimiento» sin que Movi lo pudiera leer. Lo que rinde la Cajita también dice «recibiste», pero
 * [NU_NO_SON_MOVIMIENTOS] lo saca antes.
 */
private val plataQueLlegaANu = Regex("""te lleg[oó] dinero|\brecibiste\s+\$?\s*[0-9]""", RegexOption.IGNORE_CASE)

/**
 * **El pago que sale de la cuenta de ahorros de Nu** (PSE, facturas): «Nu: Pago aprobado por
 * $139.154,40 Pagaste en Coomeva Medicina Prepagada S.A. con tu cuenta de ahorros.». Es un gasto
 * real, y hasta el 3-oct-2026 no tenía «la forma de un movimiento»: Reconciliar decía «Este mensaje
 * no trae un movimiento para anotar» y el dueño no lo podía ni categorizar.
 *
 * No se confunde con el recordatorio («Tienes un pago por $138.600,00 de …: Completa tu pago…»),
 * que no dice ni «aprobado» ni «pagaste en».
 */
private val pagoDesdeLaCuentaDeNu = Regex("""\bpago\s+aprobado\b|\bpagaste\s+en\s""", RegexOption.IGNORE_CASE)

/**
 * **El abono que llega A la tarjeta** (4-oct-2026): «Bancolombia: Recibimos pago por $9,000,000.00 a
 * tu tarjeta de credito **9208 desde Wompi-PSE». Es plata que ENTRA a la tarjeta —le baja la
 * deuda—, y se leía como gasto: confirmado así, le **sumaba** $9 M de deuda a la AMEX. Pasó dos
 * veces en septiembre ($18,8 M, los dos abonos de un tercero que el dueño terminó armando a mano).
 *
 * Solo la forma de Bancolombia, que dice «a tu tarjeta». El «Recibimos tu pago» de Nu no: ese lo
 * captura el teléfono del lado de la cuenta de ahorros de Nu, de donde SALIÓ la plata, y ahí sí es
 * una salida (ver [pagoDeNu]).
 */
private val abonoALaTarjeta = Regex("""\brecibimos\s+pago\b.*?\ba\s+tu\s+tarjeta\b""", RegexOption.IGNORE_CASE)

/**
 * **El avance de una tarjeta de crédito** (4-oct-2026): «Bancolombia: Hiciste un avance de $6,200,000
 * en tu SUC VIRTUAL el 17:43 03/10/2026 desde tu T.Credito *9208 a la cuenta *8133.» Llegó solo por
 * correo, y se leía como un GASTO de $6,2 M llamado «tu SUC VIRTUAL», con la cuenta propia *8133
 * ofrecida como «¿de quién es esta cuenta?».
 *
 * Es plata prestada que ENTRA a la cuenta de ahorros: el dueño lo anotó como un desembolso de la
 * tarjeta a la cuenta (la pata del banco con «Desembolso de crédito», que cuenta como plata que
 * entra; la de la tarjeta como traspaso). Confirmar un aviso todavía no arma esas dos patas —un
 * traspaso no acepta una tarjeta en ninguna punta, y un desembolso suelto no se deja escribir—, así
 * que se propone lo que sí se puede: un **ingreso** en la cuenta de destino, «Avance de la tarjeta
 * *9208», con [AVANCE_CATEGORY]. La deuda de la tarjeta queda por cargar aparte.
 */
private val avanceRegex = Regex("""\bhiciste\s+un\s+avance\b""", RegexOption.IGNORE_CASE)

/** La tarjeta de la que salió el avance: «desde tu T.Credito *9208». */
private val tarjetaDelAvanceRegex = Regex("""\bT\.?\s*Cred(?:ito)?\.?\s*\*+\s?(\d{4,})""", RegexOption.IGNORE_CASE)

/** La categoría de un avance de tarjeta, mientras confirmar no arme el desembolso de dos patas. */
internal const val AVANCE_CATEGORY = "Avance de tarjeta"

/**
 * A quién se le pagó desde la cuenta de Nu: lo que va entre «Pagaste en» y « con tu cuenta» (la
 * notificación) o « con Cuenta Nu» (el correo: «Pagaste en Coomeva Medicina Prepagada S.A. con
 * Cuenta Nu»).
 */
private val pagasteEnRegex = Regex("""\bpagaste\s+en\s+(.+?)\s+con\s+(?:tu\s+)?cuenta\b""", RegexOption.IGNORE_CASE)

/**
 * **De Nu solo se lee lo que es una compra aprobada, un pago (a la tarjeta o desde la cuenta) o plata que llega.** Desde #346 el teléfono sube TODAS
 * las notificaciones de `com.nu.production`, y el lector genérico convierte en gasto cualquier
 * texto con un monto: la factura del mes, una promoción, lo que rindió la Cajita. En vez de ir
 * tachando avisos a medida que aparecen, con Nu se pide la forma de un movimiento: la compra que
 * dice «aprobada», el pago recibido o la plata que llega a la cuenta. Lo demás no es un movimiento.
 *
 * Solo aplica cuando el origen dice Nu: un SMS de Bancolombia no pasa por acá.
 */
private fun loDeNuEsUnMovimiento(minusculas: String): Boolean {
    if (NU_NO_SON_MOVIMIENTOS.any { it in minusculas }) return false
    val esCompra = "compra" in minusculas && ("aprobada" in minusculas || "aprobado" in minusculas)
    return esCompra || pagoDeNu.containsMatchIn(minusculas) || plataQueLlegaANu.containsMatchIn(minusculas) ||
        pagoDesdeLaCuentaDeNu.containsMatchIn(minusculas)
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
    // El correo de PSE tiene su propia forma (Valor/Empresa/Descripción/CUS): ver `ElCorreoDePse.kt`.
    if (esUnCorreoDePse(text)) return leerElCorreoDePse(text)
    if (origen != null && origenNu.containsMatchIn(origen) && !loDeNuEsUnMovimiento(minusculas)) return null
    val conPrefijo = amountRegex.find(text)
    val rawAmount = conPrefijo?.groupValues?.get(2)
        ?: amountPorRegex.find(text)?.groupValues?.get(1)
        ?: amountRecibisteRegex.find(text)?.groupValues?.get(1)
        ?: amountSufijoRegex.find(text)?.groupValues?.get(1)
        ?: return null
    val amount = montoDelSms(rawAmount) ?: return null
    val monedaDelSms = conPrefijo?.groupValues?.get(1) ?: amountSufijoRegex.find(text)?.groupValues?.get(2)
    val currency = if (monedaDelSms?.equals("USD", ignoreCase = true) == true) "USD" else "COP"
    val recargaDeBeneficios = if ("tarjeta de beneficios" in minusculas) recargaDeBeneficiosRegex.find(text) else null

    val esAbonoALaTarjeta = abonoALaTarjeta.containsMatchIn(text)
    val esAvance = avanceRegex.containsMatchIn(text)

    val type = when {
        recargaDeBeneficios != null -> TransactionType.INCOME
        esAbonoALaTarjeta -> TransactionType.INCOME
        esAvance -> TransactionType.INCOME
        text.contains("Recibiste", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Nómina recibida", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Compra", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Pago", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Retiro", ignoreCase = true) -> TransactionType.EXPENSE
        else -> TransactionType.EXPENSE
    }

    fun limpio(m: String?) = m?.trim()?.trimEnd(',', '.')?.trim()?.takeIf { it.isNotEmpty() }
    val esPagoDeNu = origen != null && origenNu.containsMatchIn(origen) && pagoDeNu.containsMatchIn(text)
    val merchant = when {
        text.contains("Nómina recibida", ignoreCase = true) -> "Nómina"
        esAbonoALaTarjeta -> "Pago de tarjeta"
        esAvance -> tarjetaDelAvanceRegex.find(text)?.let { "Avance de la tarjeta *${it.groupValues[1].takeLast(4)}" }
            ?: "Avance de tarjeta"
        recargaDeBeneficios != null ->
            limpio(recargaDeBeneficios.groupValues[1])?.let { "Recarga de beneficios · $it" } ?: "Recarga de beneficios"
        type == TransactionType.INCOME -> limpio(merchantOfRegex.find(text)?.groupValues?.get(1)) ?: "Transferencia recibida"
        looksLikeCardPayment(text, category = "") || esPagoDeNu -> "Pago de tarjeta"
        // Un pago por QR puede venir con el nombre del comercio («por codigo QR en Mora Soccer»);
        // cuando no, la llave es lo único que lo distingue del pago por QR de mañana.
        "codigo qr" in minusculas || "código qr" in minusculas ->
            limpio(merchantInRegex.find(text)?.groupValues?.get(1))
                ?: llaveRegex.find(text)?.let { "Pago QR · llave ${it.groupValues[1]}" }
                ?: "Pago QR"
        else -> facturaProgramadaRegex.find(text)?.groupValues?.get(1)?.replace(Regex("""\s+"""), " ")?.let(::limpio)
            ?: limpio(pagoDeWalletRegex.find(text)?.groupValues?.get(1))
            // Antes que el «en …» genérico: ese corta en el primer punto y de «Coomeva Medicina
            // Prepagada S.A.» dejaba «Coomeva Medicina Prepagada S».
            // Y sin [limpio]: el punto final de «S.A.» es parte del nombre, no del mensaje.
            ?: pagasteEnRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: limpio(compraEnPorRegex.find(text)?.groupValues?.get(1))
            ?: limpio(destinatarioDesdeRegex.find(text)?.groupValues?.get(1))
            ?: limpio(destinatarioElRegex.find(text)?.groupValues?.get(1))
            ?: limpio(merchantInRegex.find(text)?.groupValues?.get(1))
            ?: cuentaDestinoRegex.find(text)?.let { "Transferencia a la cuenta *${it.groupValues[1]}" }
            ?: if ("transferiste" in minusculas) "Transferencia" else "Movimiento"
    }

    val category = when {
        esAbonoALaTarjeta -> CARD_PAYMENT_CATEGORY
        esAvance -> AVANCE_CATEGORY
        else -> categoryFor(text, merchant, type, esPagoDeNu)
    }
    // A quién fue (o de quién vino): la misma lectura que hacen la bandeja y el detalle de un
    // movimiento, en `:core`. Un pago de tarjeta no es a una persona, así que no lo lleva; un avance
    // tampoco: la cuenta que nombra («a la cuenta *8133») es la del dueño, no la de un tercero.
    val identificador = if (category == CARD_PAYMENT_CATEGORY || esAvance) null else identificadorDelDestinoEn(text)
    return ParsedSms(
        amount, merchant, type, category, currency,
        identificadorDelDestino = identificador?.valor,
        identificadorEsLlave = identificador?.tipo == TipoDeIdentificador.LLAVE,
    )
}

/**
 * [text] es el SMS completo, no solo [merchant]: "pago tc"/"pago tarjeta" viven en frases como
 * "Pago autom TC ...1234 por $80.894" que `merchantInRegex` no captura (no tiene "en <algo>"),
 * así que el merchant extraído llega como "Movimiento" y perdería la señal. Se revisa el texto
 * crudo antes de caer en las reglas por merchant.
 *
 * [esPagoDeNu]: ya calculado en [parseSms] con [pagoDeNu] y [origenNu] — Nu no escribe ninguna de
 * las frases de [looksLikeCardPayment] (esas salen de extractos de Bancolombia). Sin esto, «¡Bravo!
 * Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por $1.998,96. En un rato podrás verlo en tu
 * app.» caía en `Otros`, con «en tu app» como comercio: el pie del mensaje, capturado por
 * [merchantInRegex] al no encontrar ninguna otra regla que aplicara primero.
 */
private fun categoryFor(text: String, merchant: String, type: TransactionType, esPagoDeNu: Boolean): String {
    if (type == TransactionType.EXPENSE && (looksLikeCardPayment(text, category = "") || esPagoDeNu)) {
        return CARD_PAYMENT_CATEGORY
    }
    if (type == TransactionType.INCOME) {
        if (merchant.startsWith("Recarga de beneficios")) return "Salario"
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
    if (parsed.category in CATEGORIAS_QUE_LA_MEMORIA_NO_TOCA) return parsed
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

/**
 * Las categorías que dice el aviso y no la costumbre: el pago de tarjeta (ver arriba), y el avance
 * de tarjeta, que nombra la tarjeta por su número —«Avance de la tarjeta *9208»— y con eso la
 * memoria lo confundiría con cualquier otro movimiento de esa tarjeta, cambiándole hasta el nombre.
 *
 * Y lo que el correo de PSE clasifica por la descripción (ver `ElCorreoDePse.kt`): la cuota de un
 * crédito y el depósito a una cuenta propia. A la misma empresa se le paga de dos formas —a «NU
 * Compañía de Financiamiento» el pago de la tarjeta y el depósito a la cuenta—, así que lo que el
 * dueño anotó la vez pasada para esa empresa no dice qué es este pago.
 */
private val CATEGORIAS_QUE_LA_MEMORIA_NO_TOCA =
    setOf(CARD_PAYMENT_CATEGORY, AVANCE_CATEGORY, CUOTA_CATEGORY, TRANSFER_CATEGORY)

/**
 * **Un pago avisado por el banco y por PSE: la cuenta del uno, el nombre del otro.** El SMS de
 * Bancolombia dice de qué cuenta salió («desde tu producto 8133») pero nombra a la empresa a medias
 * («Banco de Occidente S A ATH») y no dice para qué fue; el correo de PSE del mismo pago dice
 * «Empresa: Banco de Occidente» y «Descripción: PAGO Banco de Occidente - Prestamo». La propuesta de
 * un pago avisado varias veces sale del aviso que nombra la cuenta (`propuestaDelGrupo`, que no se
 * toca); esto le pone encima el comercio, la categoría y la nota del correo de PSE. El monto, la
 * moneda y el tipo ya son los mismos: por eso se juntaron.
 *
 * **La fecha NO**: la del correo es solo un día, y el aviso del banco trae día y hora. Con otro aviso
 * en el pago, el movimiento va a la hora del más viejo de ellos (lo hace la app con los miembros del
 * pago); la «Fecha de la transacción» de PSE solo vale cuando el correo es el único aviso.
 */
internal fun conLoQueDiceElCorreoDePse(leido: ParsedSms, delCorreoDePse: ParsedSms?): ParsedSms =
    if (delCorreoDePse == null) leido
    else leido.copy(
        merchant = delCorreoDePse.merchant,
        category = delCorreoDePse.category,
        nota = delCorreoDePse.nota,
        fecha = null,
        // Un pago a una empresa no es «una cuenta de otros».
        identificadorDelDestino = null,
        identificadorEsLlave = false,
    )

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
        // Los avisos del mismo pago salen marcados (`grupoId`): la bandeja muestra uno por pago.
        call.respond(dbQuery { bandejaConLosPagos(uid, ahora = System.currentTimeMillis()) })
    }

    /**
     * Ola 2 · «banco mudo»: los orígenes de captura que se callaron. Lo pide el aviso diario del
     * teléfono; el Inicio los trae en su resumen. Literal, así que gana sobre `/api/sms/{id}`.
     */
    get("/api/sms/origenes-mudos") {
        val uid = call.userId()
        call.respond(dbQuery { origenesMudosDe(uid, System.currentTimeMillis()) })
    }

    get("/api/sms/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        if (sms.state != SMS_STATE_PENDING) return@get call.respond(sms)
        // Para saber de qué pago es parte hacen falta los demás avisos; la marca es la misma que en
        // la bandeja, con la misma regla ([bandejaConLosPagos]).
        val marcado = dbQuery { bandejaConLosPagos(uid, ahora = System.currentTimeMillis()) }
            .firstOrNull { it.id == sms.id }
        call.respond(marcado ?: sms)
    }

    /**
     * **«Este es otro pago»**: el aviso [id] no va con los demás avisos con que Movi lo juntó, y
     * nunca más se junta con ninguno (`no_es_el_mismo_pago`). Desde ahí es su propia tarjeta en «Por
     * revisar». Idempotente; 404 si no es del usuario.
     */
    post("/api/sms/{id}/no-es-el-mismo-pago") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val n = dbQuery { separarDelMismoPago(uid, listOf(id)) }
        call.respond(if (n == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
    }

    /**
     * **«No son el mismo pago»**, para todos los avisos de la tarjeta de una vez: cada uno vuelve a
     * ser su propio pago y no se junta más. Solo toca los avisos del usuario; 404 si ninguno lo es.
     */
    post("/api/sms/grupo/{grupoId}/desagrupar") {
        val uid = call.userId()
        val cuerpo = runCatching { call.receive<AvisosDelMismoPago>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val n = dbQuery { separarDelMismoPago(uid, cuerpo.miembros) }
        call.respond(if (n == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
    }

    get("/api/sms/{id}/parse") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        // Ola 2: la propuesta de un comprobante no se lee con el parser de SMS sino con lo que leyó
        // Claude del papel, ya pasado por la memoria y las cuentas de otros (ver `PapelesRoutes`).
        if (esUnComprobante(sms)) {
            dbQuery { parsedDeUnComprobante(uid, sms.id, sms.text) }?.let { return@get call.respond(it) }
        }
        val leido = parseSms(sms.text, sms.bank)
            // No es un error de la app: el mensaje no trae un movimiento (un aviso, una ampliación de
            // plazo). Se dice así, porque la pantalla muestra este texto.
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, "Este mensaje no trae un movimiento para anotar. Puedes ignorarlo.")
        // El correo de PSE del mismo pago, si este aviso no lo es: ver [conLoQueDiceElCorreoDePse].
        val otrosDelPago = if (sms.state != SMS_STATE_PENDING) emptyList() else dbQuery {
            val bandeja = bandejaConLosPagos(uid, ahora = System.currentTimeMillis())
            val miembros = bandeja.firstOrNull { it.id == sms.id }?.miembrosDelGrupo.orEmpty()
            bandeja.filter { it.id in miembros && it.id != sms.id }
        }
        val correoDePse = if (esUnCorreoDePse(sms.text)) null else otrosDelPago.firstOrNull { esUnCorreoDePse(it.text) }
        val parsed = conLoQueDiceElCorreoDePse(leido, correoDePse?.let { leerElCorreoDePse(it.text) })
            // Si el propio correo de PSE es la propuesta de un pago con otros avisos, tampoco manda su
            // fecha: el día y la hora salen del aviso del banco más viejo (ver [conLoQueDiceElCorreoDePse]).
            .let { if (otrosDelPago.isNotEmpty()) it.copy(fecha = null) else it }
        // La historia del dueño entra acá y no adentro de `parseSms`: ese mismo parseo lo usan el
        // sync y la push, donde no hay a quién consultarle nada.
        //
        // **Y las cuentas de otros van DESPUÉS de la memoria**, no antes. Ese orden es la decisión:
        // si el dueño a ese número ya lo llamó «Mercado», sigue diciendo «Mercado» — el nombre que
        // él puso es suyo. El destino solo habla donde no hablaba nadie, que es el caso real («a la
        // cuenta *31973270756» no lo puede leer ningún humano). Ver `conElDestinoConocido`.
        val memoria = dbQuery { memoriaDe(uid) }
        val destinos = dbQuery { destinosDelDueno(uid) }
        // Y al final, una cuenta del dueño no se le ofrece como «de otro» (ver [sinLaCuentaPropia]).
        val propios = dbQuery { numerosPropiosDe(uid) }
        val propuesta = sinLaCuentaPropia(conElDestinoConocido(conLoQueMoviRecuerda(parsed, memoria), sms.text, destinos), propios)
        // Lo que el correo de PSE no dice —desde qué cuenta, qué crédito o tarjeta abona, a qué cuenta
        // propia fue un depósito— lo completa lo que el dueño ya tiene en Movi. Solo para los pagos
        // de PSE: es donde falta, y así nada cambia para los demás avisos.
        val esDePse = esUnCorreoDePse(sms.text) || correoDePse != null
        call.respond(if (esDePse) dbQuery { loQueMoviSabeDelPagoDe(uid, propuesta) } else propuesta)
    }

    get("/api/sms/{id}/coincidencias") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = dbQuery {
            SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull()?.toSmsMessage()
        } ?: return@get call.respond(HttpStatusCode.NotFound)
        val parsed = (if (esUnComprobante(sms)) dbQuery { parsedDeUnComprobante(uid, sms.id, sms.text) } else null)
            ?: parseSms(sms.text, sms.bank)
            ?: return@get call.respond(emptyList<FinancialEvent>())
        val momento = momentoDelSms(sms.time, ahora = System.currentTimeMillis())
        val margen = DIAS_PARA_COINCIDIR * 86_400_000L
        val eventos = dbQuery { loadEventsBetween(uid, momento - margen, momento + margen + 1) }
        call.respond(coincidenciasDelSms(parsed, momento, eventos))
    }

    post("/api/sms/{id}/confirm") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        // Ola 2: con qué movimiento se confirmó un comprobante (el que se acaba de crear, o el que
        // ya estaba, «Es este»). Opcional: un cliente viejo no lo manda y todo sigue igual.
        val eventoId = call.request.queryParameters["eventoId"]?.takeIf { it.isNotBlank() }
        val updated = dbQuery {
            val n = SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = SMS_STATE_CONFIRMED
            }
            if (n > 0 && eventoId != null && esIdDeComprobante(id)) enlazarElComprobante(uid, id, eventoId)
            n
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

    /**
     * **«Era un movimiento»**: devuelve a la bandeja un mensaje que Movi apartó solo (ver
     * `MensajesApartados.kt` en `:core`). Queda `pending` y con la marca [MOTIVO_DEVUELTO], así que
     * ni la pasada del arranque ni ninguna regla futura lo vuelve a apartar: el dueño ya decidió.
     *
     * Solo sirve para lo que apartó Movi. Lo que ignoró el dueño se queda como él lo dejó (409),
     * y lo de otro usuario no existe (404).
     */
    post("/api/sms/{id}/era-un-movimiento") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val resultado = dbQuery {
            val fila = SmsMessages.selectAll()
                .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
                .firstOrNull() ?: return@dbQuery HttpStatusCode.NotFound
            val apartado = fila[SmsMessages.state] == SMS_STATE_IGNORED &&
                motivoDeApartado(fila[SmsMessages.motivoApartado]) != null
            if (!apartado) return@dbQuery HttpStatusCode.Conflict
            SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                it[state] = SMS_STATE_PENDING
                it[motivoApartado] = MOTIVO_DEVUELTO
            }
            HttpStatusCode.NoContent
        }
        call.respond(resultado)
    }

    post("/api/sms/sync") {
        val uid = call.userId()
        val messages = call.receive<List<SmsMessage>>()
        val inserted = mutableListOf<SmsMessage>()
        // Lo que entró apartado no se avisa: ni la push, ni el «Movi anotó» del teléfono.
        val apartadosAhora = mutableSetOf<String>()
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
                // Los `cmp_` son de los comprobantes que lee el server (Ola 2): el teléfono no
                // puede inventar uno.
                if (esIdDeComprobante(msg.id)) continue
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
                    // el sistema (ver SmsMessages en Tables.kt) — salvo lo que no es un
                    // movimiento (un recordatorio, un código, una promo), que entra apartado:
                    // `ignored` con su motivo. Ver `queEsEsteMensaje`.
                    val motivo = motivoParaApartar(msg.text, msg.bank)
                    it[state]  = if (motivo == null) SMS_STATE_PENDING else SMS_STATE_IGNORED
                    it[motivoApartado] = motivo?.name
                    it[det]    = msg.det
                    if (motivo != null) apartadosAhora += msg.id
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
        val realtimeCaptures = inserted.filter { it.id.startsWith("sms_rt_") && it.id !in apartadosAhora }
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

        // Ola 1 · Movi avisa: lo que quedó esperando en «Por revisar», ya leído, para que el
        // teléfono avise «Movi anotó $180.000» con las mismas palabras que la bandeja. Solo lo
        // insertado ahora, en `pending` (lo que entró apartado no se avisa) y con movimiento.
        // Best-effort como la push: sin destinos (o si la consulta falla) se avisa con el nombre
        // que mandó el banco, y nunca se cae el sync por esto.
        //
        // Un pago, un aviso: lo que se juntó con un aviso que ya estaba (el SMS que llegó un minuto
        // después de la notificación) no se vuelve a avisar. Ver [losQueAbrenUnPago].
        val pendientes = inserted.filter { it.id !in apartadosAhora }.let { recien ->
            if (recien.isEmpty()) recien
            else runCatching { losQueAbrenUnPago(recien, dbQuery { bandejaConLosPagos(uid, System.currentTimeMillis()) }) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrDefault(recien)
        }
        val destinos = if (pendientes.isEmpty()) emptyList() else runCatching { dbQuery { destinosDelDueno(uid) } }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
        call.respond(RespuestaDelSync(synced = insertedCount, porRevisar = avisosPorRevisar(pendientes, destinos)))
    }
}

/**
 * Los movimientos de [pendientes] que se pueden leer, con el nombre del destino guardado cuando lo
 * hay (la misma lectura que la push y que `/parse`, sin la memoria de categorías: el aviso dice el
 * monto y a quién, no la categoría). Lo que no trae un movimiento no se avisa —igual que la push—:
 * un aviso de «llegó algo» sin monto sería ruido, y sigue en la bandeja de todas formas.
 */
internal fun avisosPorRevisar(pendientes: List<SmsMessage>, destinos: List<DestinoConocido>): List<AvisoPorRevisar> =
    pendientes.mapNotNull { msg ->
        val leido = parseSms(msg.text, msg.bank)?.let { conElDestinoConocido(it, msg.text, destinos) } ?: return@mapNotNull null
        AvisoPorRevisar(
            id = msg.id,
            origen = msg.bank,
            monto = leido.amount,
            moneda = leido.currency,
            descripcion = leido.merchant,
            tipo = leido.type,
        )
    }

/**
 * **Con qué estado entra un mensaje a la bandeja**, y con qué motivo si entra apartado. Casi siempre
 * `pending`: la bandeja es del dueño y lo que no se sabe leer se le muestra, porque perder un
 * movimiento sin señal es peor que enseñarle un mensaje de más.
 *
 * La excepción es lo que [com.jvillada.movi.server.sms.queEsEsteMensaje] reconoce como no-movimiento
 * —un recordatorio de pago, un código, una promoción, el aviso de una app sin un solo número (era la
 * única excepción hasta el 3-oct-2026)—: entra `ignored` **con su motivo**, para que el historial
 * diga «Movi lo apartó: …» y ofrezca «Era un movimiento». Ignorar no es borrar: la fila se guarda.
 *
 * La usan el correo entrante y las pruebas; el sync hace lo mismo en línea.
 */
internal fun comoLlega(texto: String, origen: String): Pair<String, MotivoDeApartado?> {
    val motivo = motivoParaApartar(texto, origen)
    return (if (motivo == null) SMS_STATE_PENDING else SMS_STATE_IGNORED) to motivo
}

/**
 * Marca [ids] (los del usuario) como «no es el mismo pago»: nunca más se juntan con otros avisos.
 * Devuelve cuántas filas eran suyas. Dentro de una transacción.
 */
internal fun separarDelMismoPago(uid: String, ids: List<String>): Int {
    if (ids.isEmpty()) return 0
    return SmsMessages.update({ (SmsMessages.userId eq uid) and (SmsMessages.id inList ids.distinct()) }) {
        it[noEsElMismoPago] = true
    }
}

/**
 * ¿La fila la subió la captura de notificaciones? El teléfono las rotula «Notificación · Nombre de
 * la app» (`FiltroDeNotificaciones.kt`, en `:shared`); los SMS llegan con el código del remitente
 * («85540») y los correos con «Correo · …».
 */
internal fun esUnaNotificacion(origen: String): Boolean =
    origen.trimStart().startsWith("Notificación", ignoreCase = true)

internal fun org.jetbrains.exposed.sql.ResultRow.toSmsMessage() = SmsMessage(
    id    = this[SmsMessages.id],
    time  = this[SmsMessages.time],
    bank  = this[SmsMessages.bank],
    text  = this[SmsMessages.text],
    state = this[SmsMessages.state],
    det   = this[SmsMessages.det],
    // Solo lo que apartó Movi y sigue apartado: `DEVUELTO` no viaja, ni un motivo sobre un
    // mensaje que el dueño ya movió de estado.
    apartadoPor = this[SmsMessages.motivoApartado]
        ?.takeIf { this[SmsMessages.state] == SMS_STATE_IGNORED && motivoDeApartado(it) != null },
)
