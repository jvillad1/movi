package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.math.abs
import com.jvillada.movi.server.time.epochMillisToAppDate

data class MerchantId(val key: String, val displayName: String, val known: Boolean)

data class DetectedSub(
    val merchantKey: String,
    val displayName: String,
    val amount: Long,        // mediana de la suma mensual (moneda nativa)
    val currency: String,
    val dayOfMonth: Int,
    val occurrences: Int,    // meses distintos
    val firstSeen: Long,
    val lastSeen: Long,
    val confidence: SubConfidence,
    val accountId: String?,
)

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

/** Comercio normalizado, o null si la descripción no identifica un comercio usable. */
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

private const val DAY_MS = 86_400_000L

private fun dateOf(ts: Long): LocalDate = epochMillisToAppDate(ts)

/**
 * Detección determinística: agrupa EXPENSE por (merchantKey, currency) y marca como
 * suscripción los grupos con ≥2 meses distintos, suma mensual estable (dispersión ≤15%
 * sobre la mediana) y cadencia ~mensual (ningún hueco > 45 días entre cargos; los cargos
 * separados ≤3 días cuentan como el mismo ciclo). HIGH = comercio conocido (known == true)
 * + ≥3 meses + dispersión ≤5% + huecos regulares (26–35 días); un comercio desconocido nunca
 * pasa de MEDIUM, sin importar qué tan bien encaje con la heurística. Eventos futuros a
 * [today] se ignoran.
 */
fun detectSubscriptions(events: List<FinancialEvent>, today: LocalDate): List<DetectedSub> {
    val expenses = events.asSequence()
        .filter { it.type == TransactionType.EXPENSE && !dateOf(it.timestamp).isAfter(today) }
        .mapNotNull { ev -> normalizeMerchant(ev.merchant ?: ev.description)?.let { it to ev } }
        .toList()

    return expenses
        .groupBy({ (m, ev) -> m.key to ev.currency }, { it })
        .mapNotNull { (groupKey, pairs) ->
            val (merchantKey, currency) = groupKey
            val display = pairs.first().first.displayName
            // Todos los pares del grupo comparten merchantKey, y KNOWN_SERVICES se evalúa
            // antes que el fallback, así que `known` es uniforme dentro del grupo.
            val known = pairs.first().first.known
            val evs = pairs.map { it.second }.sortedBy { it.timestamp }

            val byMonth = evs.groupBy { dateOf(it.timestamp).let { d -> d.year to d.monthValue } }
            if (byMonth.size < 2) return@mapNotNull null

            val gaps = evs.zipWithNext { a, b -> (b.timestamp - a.timestamp) / DAY_MS }
                .filter { it > 3 }   // mismo ciclo (p.ej. 3 cuentas Claude el mismo día) no es gap
            if (gaps.isEmpty() || gaps.any { it > 45 }) return@mapNotNull null

            val monthlySums = byMonth.values.map { l -> l.sumOf { it.amount } }.sorted()
            val median = monthlySums[monthlySums.size / 2]
            if (median <= 0) return@mapNotNull null
            val maxDev = monthlySums.maxOf { abs(it - median).toDouble() / median }
            if (maxDev > 0.15) return@mapNotNull null

            val days = evs.map { dateOf(it.timestamp).dayOfMonth }.sorted()
            val regular = gaps.all { it in 26..35 }
            // Solo comercios conocidos (known == true) pueden llegar a HIGH → AUTO. Un
            // comercio desconocido que cumple toda la heurística (p.ej. un agregado mensual
            // estable que no es en realidad una suscripción) cae en MEDIUM → CANDIDATE, para
            // que el usuario lo revise en vez de auto-confirmarlo.
            val confidence = when {
                known && byMonth.size >= 3 && maxDev <= 0.05 && regular -> SubConfidence.HIGH
                else -> SubConfidence.MEDIUM
            }
            DetectedSub(
                merchantKey = merchantKey,
                displayName = display,
                amount      = median,
                currency    = currency,
                dayOfMonth  = days[days.size / 2],
                occurrences = byMonth.size,
                firstSeen   = evs.first().timestamp,
                lastSeen    = evs.last().timestamp,
                confidence  = confidence,
                accountId   = evs.map { it.accountId }.distinct().singleOrNull(),
            )
        }
}
