package com.jvillada.movi.shared.model

/*
 * # Vuelta 113 · un SMS personal nunca sale del teléfono
 *
 * La regla de qué SMS se suben vive **acá, en un solo lugar**, y la llaman los dos lados que deciden:
 *
 * - el teléfono Android (`BankSenderFilter.matches` en `:shared` androidMain), en la captura en tiempo
 *   real (`SmsRealtimeReceiver`) y en el barrido del inbox (`filterBankMessages` de `SmsBackfill`):
 *   [seSubeElSms];
 * - el server, para lo que manda un atajo del iPhone (`pasaElFiltroDeBancos` en
 *   `SmsFilterConfigRoutes.kt`, que usa `POST /api/captura/atajo`): [seGuardaElSmsDelAtajo].
 *
 * Los códigos y las palabras siguen siendo los de siempre (el piso compilado del APK unido con lo que
 * sirve `GET /api/sms/filter-config`); lo nuevo es la condición sobre el remitente, que va **compilada**:
 * un APK anterior a esta vuelta sigue con la regla vieja (código **o** palabra, de cualquier remitente).
 *
 * Por qué: para pedirle a Google Play la excepción «SMS-based money management» solo pueden salir del
 * teléfono los SMS financieros, y con la regla vieja un SMS de un celular que nombrara un banco («ya te
 * pagué por Bancolombia») salía por la palabra. Medido en producción el 2-nov-2026: los 359 SMS
 * capturados venían de 16 remitentes, **todos códigos cortos de 5 o 6 dígitos**; ninguno de un celular.
 */

/** Lo que se quita del remitente antes de mirarlo: espacios, guiones, paréntesis y el «+». */
private fun remitenteLimpio(remitente: String?): String =
    remitente.orEmpty().filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' || it == '+' }

/**
 * **¿El remitente es el número de una persona?** Quitando espacios, guiones, paréntesis y el «+», son
 * **solo dígitos y 10 o más**: un celular colombiano («3001234567», «+57 300 123 4567», «573001234567»),
 * un fijo con indicativo o cualquier número internacional largo. Un banco manda desde un código corto (4
 * a 6 dígitos) o con su nombre («Bancolombia», «DAVIVIENDA»), y una persona no puede usar ninguno de los
 * dos. Sin remitente, `false` (no se sabe quién es: lo decide quien llama).
 */
fun esUnRemitentePersonal(remitente: String?): Boolean {
    val limpio = remitenteLimpio(remitente)
    return limpio.length >= 10 && limpio.all { it in '0'..'9' }
}

/**
 * ¿El remitente contiene un código conocido? Por substring sobre el remitente limpio, como siempre hizo el
 * teléfono («+5787400» contiene «87400»). Un código vacío no cuenta: `contains("")` dejaría pasar todo.
 */
private fun traeUnCodigoConocido(remitente: String?, codigos: List<String>): Boolean {
    val limpio = remitenteLimpio(remitente)
    if (limpio.isEmpty()) return false
    return codigos.any { codigo -> remitenteLimpio(codigo).let { it.isNotEmpty() && limpio.contains(it) } }
}

/** ¿El cuerpo nombra un banco? En minúsculas, por substring y **sin quitar tildes**, como siempre. */
private fun nombraUnBanco(cuerpo: String, palabras: List<String>): Boolean {
    val minusculas = cuerpo.lowercase()
    return palabras.any { it.isNotBlank() && minusculas.contains(it.lowercase()) }
}

/**
 * **¿Este SMS sale del teléfono?** (la regla del teléfono Android)
 *
 * 1. Un remitente personal ([esUnRemitentePersonal]) **nunca** sale: ni por la palabra ni por el código.
 *    Lo segundo es a propósito: con el substring de siempre, el celular «3008554012» contiene «85540»;
 *    ningún banco manda desde un número de 10 dígitos.
 * 2. Si el remitente contiene un código conocido ([codigos]), sale.
 * 3. Si el cuerpo trae una palabra de banco ([palabras]) **y hay remitente** (un código corto que Movi no
 *    conoce, como el 871… de Davivienda, o un nombre como «Bancolombia»), sale.
 * 4. Lo demás no sale. **Sin remitente** (vacío o nulo) no pasa por la palabra: el teléfono siempre sabe
 *    de quién es un SMS, y si no lo sabe no hay cómo descartar que sea de una persona.
 */
fun seSubeElSms(remitente: String?, cuerpo: String, codigos: List<String>, palabras: List<String>): Boolean {
    if (esUnRemitentePersonal(remitente)) return false
    if (traeUnCodigoConocido(remitente, codigos)) return true
    if (remitenteLimpio(remitente).isEmpty()) return false
    return nombraUnBanco(cuerpo, palabras)
}

/** Un monto como lo escriben los avisos: «$», «COP» o «USD» pegado (o con un espacio) a una cifra. */
private val montoDelAviso = Regex("""(\$|\bcop|\busd)\s?[0-9]""", RegexOption.IGNORE_CASE)

/** ¿El texto trae un monto («$27.500», «$ 116,600», «COP86.900,00», «USD 12.50»)? */
fun traeUnMontoDelAviso(texto: String): Boolean = montoDelAviso.containsMatchIn(texto)

/**
 * ¿El texto **empieza** por una palabra de banco, como firman los avisos? «Bancolombia: Compraste…»,
 * «DAVIVIENDA Descuento en internet…», «Bancolombia te informa…». Antes de la palabra solo se saltan
 * espacios y signos («¡», «*», «"») y un «Banco » o «Banco de » inicial («Banco Falabella: …», «Banco AV
 * Villas …»), nunca otras letras ni cifras: «Hola, ya te pagué por Bancolombia» no empieza por el banco.
 */
fun empiezaPorUnBanco(texto: String, palabras: List<String>): Boolean {
    val desde = texto.indexOfFirst { it.isLetterOrDigit() }
    if (desde < 0) return false
    val inicio = texto.substring(desde).lowercase()
    val comienzos = listOf(inicio, inicio.removePrefix("banco de "), inicio.removePrefix("banco "))
    return palabras.any { palabra ->
        palabra.isNotBlank() && comienzos.any { it.startsWith(palabra.lowercase()) }
    }
}

/**
 * **¿Se guarda el SMS que mandó un atajo del iPhone?** El remitente es opcional: el atajo firmado
 * («Movi - Avisar un mensaje») manda solo el texto, y uno armado a mano puede mandar el remitente como lo
 * ve el iPhone, que para un número guardado es **el nombre del contacto** («Mamá», «Asesora Bancolombia»).
 *
 * - Remitente personal → no.
 * - Remitente de **solo cifras** que no es personal (un código corto) → la regla del teléfono, [seSubeElSms].
 * - **Sin remitente, o con un remitente que tiene letras** → pasa solo si el texto **empieza por una palabra
 *   de banco** ([empiezaPorUnBanco]) **y trae un monto** ([traeUnMontoDelAviso]). En el iPhone un nombre no
 *   prueba nada: puede ser un contacto, y uno que se llame «Asesora Bancolombia» no hace de su mensaje un
 *   aviso. Así firman los avisos («Bancolombia: Compraste $46.000,00 en…», «DAVIVIENDA Descuento en
 *   internet, $64,900,…»), y «Hola, ya te pagué por Bancolombia» no entra. Lo que no tiene monto (una clave
 *   dinámica, «tu clave fue actualizada») tampoco: no es un movimiento.
 *
 * (En el teléfono Android el remitente alfanumérico lo pone la red, no la libreta de contactos: ahí sí
 * cuenta como remitente, ver [seSubeElSms].)
 */
fun seGuardaElSmsDelAtajo(remitente: String?, texto: String, codigos: List<String>, palabras: List<String>): Boolean {
    if (esUnRemitentePersonal(remitente)) return false
    val limpio = remitenteLimpio(remitente)
    if (limpio.isNotEmpty() && limpio.all { it in '0'..'9' }) return seSubeElSms(remitente, texto, codigos, palabras)
    return empiezaPorUnBanco(texto, palabras) && traeUnMontoDelAviso(texto)
}

/**
 * Vuelta 114 · **por qué no se guarda** lo que mandó un atajo, o `null` si se guarda ([seGuardaElSmsDelAtajo]).
 *
 * - [MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO]: sin remitente que pruebe nada (el atajo firmado no lo manda,
 *   o es un nombre con letras), el texto **nombra un banco** pero no empieza por él o no trae monto: una
 *   promoción, una clave dinámica, «tu clave fue actualizada», o un mensaje personal que nombra al banco.
 *   Antes se contaba como «no era de un banco», que para una promoción del banco es falso.
 * - [MotivoDelAtajoDescartado.NO_ES_DEL_BANCO]: todo lo demás (un remitente personal, un código que no es de
 *   un banco y un texto que no nombra a ninguno).
 */
fun porQueNoSeGuardaElSmsDelAtajo(
    remitente: String?,
    texto: String,
    codigos: List<String>,
    palabras: List<String>,
): MotivoDelAtajoDescartado? {
    if (seGuardaElSmsDelAtajo(remitente, texto, codigos, palabras)) return null
    val limpio = remitenteLimpio(remitente)
    val elRemitenteDecide = esUnRemitentePersonal(remitente) || (limpio.isNotEmpty() && limpio.all { it in '0'..'9' })
    return if (!elRemitenteDecide && nombraUnBanco(texto, palabras)) {
        MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO
    } else {
        MotivoDelAtajoDescartado.NO_ES_DEL_BANCO
    }
}
