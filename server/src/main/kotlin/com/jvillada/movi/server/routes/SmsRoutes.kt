package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionSource
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private val amountRegex = Regex("""\$\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]+)?)""")
private val merchantInRegex = Regex("""en\s+(.+?)(?:\s+el\s|\s+a\s+las|\.|$)""", RegexOption.IGNORE_CASE)
private val merchantOfRegex = Regex("""de\s+(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE)

internal fun parseSms(text: String): ParsedSms? {
    val rawAmount = amountRegex.find(text)?.groupValues?.get(1) ?: return null
    val amount = rawAmount.replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null

    val type = when {
        text.contains("Recibiste", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Nómina recibida", ignoreCase = true) -> TransactionType.INCOME
        text.contains("Compra", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Pago", ignoreCase = true) -> TransactionType.EXPENSE
        text.contains("Retiro", ignoreCase = true) -> TransactionType.EXPENSE
        else -> TransactionType.EXPENSE
    }

    val merchant = when {
        text.contains("Nómina recibida", ignoreCase = true) -> "Nómina"
        type == TransactionType.INCOME -> merchantOfRegex.find(text)?.groupValues?.get(1)?.trim() ?: "Transferencia recibida"
        else -> merchantInRegex.find(text)?.groupValues?.get(1)?.trim() ?: "Movimiento"
    }

    val category = categoryFor(merchant, type)
    return ParsedSms(amount, merchant, type, category)
}

private fun categoryFor(merchant: String, type: TransactionType): String {
    if (type == TransactionType.INCOME) {
        return if (merchant.equals("Nómina", true)) "Nómina" else "Transferencia"
    }
    val m = merchant.lowercase()
    return when {
        "uber" in m || "didi" in m || "taxi" in m -> "Transporte"
        "crepes" in m || "waffles" in m || "rappi" in m || "mcdonald" in m -> "Restaurantes"
        "éxito" in m || "exito" in m || "carulla" in m || "olímpica" in m || "olimpica" in m || "d1" in m || "ara" in m -> "Mercado"
        "drogas" in m || "farma" in m || "salud" in m || "medi" in m -> "Salud"
        "netflix" in m || "spotify" in m || "disney" in m || "hbo" in m || "youtube" in m -> "Suscripción"
        "claro" in m || "movistar" in m || "tigo" in m || "epm" in m || "energía" in m -> "Servicios"
        else -> "Otro"
    }
}

private suspend fun walletIdForBank(bank: String): String? {
    val list = Stores.wallets.snapshot()
    return list.firstOrNull { it.name.contains(bank, ignoreCase = true) }?.id
        ?: list.firstOrNull { it.id != "1" }?.id
        ?: list.firstOrNull()?.id
}

fun Route.smsRoutes() {
    get("/api/sms") { call.respond(Stores.sms.snapshot()) }

    get("/api/sms/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = Stores.sms.snapshot().find { it.id == id }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(sms)
    }

    get("/api/sms/{id}/parse") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val sms = Stores.sms.snapshot().find { it.id == id }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val parsed = parseSms(sms.text)
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, "No se pudo parsear")
        call.respond(parsed)
    }

    post("/api/sms/{id}/confirm") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val sms = Stores.sms.snapshot().find { it.id == id }
            ?: return@post call.respond(HttpStatusCode.NotFound)
        val parsed = parseSms(sms.text)
            ?: return@post call.respond(HttpStatusCode.UnprocessableEntity, "No se pudo parsear")
        val category = call.request.queryParameters["category"] ?: parsed.category
        val walletId = call.request.queryParameters["walletId"]
            ?: walletIdForBank(sms.bank)
            ?: return@post call.respond(HttpStatusCode.UnprocessableEntity, "No hay cuenta")

        val now = System.currentTimeMillis()
        val tx = Transaction(
            id = "sms-$id-$now",
            walletId = walletId,
            name = parsed.merchant,
            amount = parsed.amount,
            category = category,
            type = parsed.type,
            source = TransactionSource.SMS,
            pending = false,
            timestamp = now,
        )
        if (!applyTransaction(tx)) return@post call.respond(HttpStatusCode.UnprocessableEntity, "Wallet no existe")

        Stores.sms.mutate { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i != -1) list[i] = list[i].copy(state = "auto")
        }
        call.respond(HttpStatusCode.Created, tx)
    }

    post("/api/sms/{id}/ignore") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val updated = Stores.sms.mutate { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i == -1) return@mutate false
            list[i] = list[i].copy(state = "ignored")
            true
        }
        if (!updated) call.respond(HttpStatusCode.NotFound)
        else call.respond(HttpStatusCode.NoContent)
    }
}
