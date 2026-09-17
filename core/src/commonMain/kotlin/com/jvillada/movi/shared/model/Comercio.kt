package com.jvillada.movi.shared.model

/**
 * **Qué comercio identifica un texto de banco**, ya normalizado: la clave canónica con la que se
 * agrupan los cargos de un mismo servicio y el nombre con el que se le muestra al dueño.
 *
 * @param key clave canónica (`"netflix"`, `"anthropic_claude"`) — la misma que termina en
 *   [Subscription.merchantKey] cuando el detector arma una suscripción.
 * @param displayName el nombre presentable (`"Netflix"`, `"Claude"`).
 * @param known el comercio está en la tabla de servicios conocidos, o sea que la clave es un
 *   canónico de verdad y no una limpieza de la descripción. El detector solo deja llegar a
 *   confianza alta a lo conocido.
 */
data class MerchantId(val key: String, val displayName: String, val known: Boolean)

// Servicios conocidos: substring (lowercase) → (key canónico, nombre de display).
// Se matchea sobre la descripción COMPLETA antes de limpiar, para que un prefijo de
// gateway que también identifica al servicio (DTV*) no se pierda al recortarlo.
private val KNOWN_SERVICES: List<Pair<String, Pair<String, String>>> = listOf(
    "netflix"     to ("netflix" to "Netflix"),
    "spotify"     to ("spotify" to "Spotify"),
    "youtube"     to ("youtube" to "YouTube"),
    "anthropic"   to ("anthropic_claude" to "Claude"),
    "claude"      to ("anthropic_claude" to "Claude"),
    "openai"      to ("openai" to "OpenAI"),
    "chatgpt"     to ("openai" to "OpenAI"),
    "microsoft"   to ("microsoft" to "Microsoft"),
    "directv"     to ("directv" to "DirecTV"),
    "dtv"         to ("directv" to "DirecTV"),
    "disney"      to ("disney" to "Disney+"),
    "hbo"         to ("hbo_max" to "Max"),
    "prime video" to ("prime_video" to "Prime Video"),
    "icloud"      to ("apple_icloud" to "iCloud"),
    "apple.com"   to ("apple" to "Apple"),
    "google one"  to ("google_one" to "Google One"),
    "github"      to ("github" to "GitHub"),
    "canva"       to ("canva" to "Canva"),
)

private val GATEWAY_PREFIXES = listOf(
    "paypal *", "paypal*", "google *", "google ", "mercpago*", "mercpago ",
    "generic dlocalgo*", "dlocalgo*", "dlo*", "payu*", "payu ", "ebanx*",
)

/**
 * Comercio normalizado, o null si la descripción no identifica un comercio usable.
 *
 * **Vive en `:core` y no en el server, donde nació** (`SubscriptionDetector.kt`), porque los dos
 * lados necesitan leer la misma identidad de un cargo:
 *
 * - El **server** la usa para agrupar los cargos de un mismo servicio y darle su clave a la
 *   suscripción que detecta.
 * - El **cliente** la usa para reconocer que «COMPRA NETFLIX.COM BOGOTA» y la suscripción
 *   «Netflix» son el mismo cobro (ver `clavesDeCobroDe`, RecurringOffer.kt). Sin eso, el nombre
 *   que Movi guarda —el canónico— y el texto crudo del banco no se parecían en nada, así que la
 *   pantalla le ofrecía crear una regla «Netflix» encima de la suscripción «Netflix» y el mismo
 *   $44.900 contaba dos veces en «Flujo libre».
 *
 * El comportamiento es exactamente el que tenía en el server —mismas tablas, mismo orden, mismos
 * recortes—: se mudó de archivo, no se reescribió.
 */
fun normalizeMerchant(description: String): MerchantId? {
    val raw = description.trim().lowercase()
    if (raw.isBlank()) return null
    for ((needle, id) in KNOWN_SERVICES) {
        if (raw.contains(needle)) return MerchantId(id.first, id.second, known = true)
    }
    var d = raw
    for (p in GATEWAY_PREFIXES) {
        if (d.startsWith(p)) { d = d.removePrefix(p).trim(); break }
    }
    d = d.replace(Regex("[*#][a-z0-9 .\\-]*$"), "").trim()  // sufijos/códigos tras * o #
    d = d.replace(Regex("\\s+\\d{4,}$"), "").trim()           // números largos finales
    if (d.length < 3) return null
    val key = d.replace(Regex("[^a-z0-9]+"), "_").trim('_')
    if (key.length < 3) return null
    val display = d.split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    // La tabla declara merchant_key varchar(80) / display_name varchar(100); una descripción
    // recurrente larga (>255 originalmente) no debe tronar el insert/update con un 500.
    return MerchantId(key.take(80), display.take(100), known = false)
}
