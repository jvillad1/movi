package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.TransactionSource
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.Wallet
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

// In-memory store — replace with a real DB later
private val wallets = mutableListOf(
    Wallet("1", "Efectivo", 580_000.0, "COP"),
    Wallet("2", "Bancolombia Ahorros", 1_260_000.0, "COP"),
)

// Seed transactions: 3 days mirroring the original FakeData ordering.
// timestamp values are illustrative — they preserve order within and across days.
private val transactions = mutableListOf(
    // Hoy · 28 abr
    Transaction("t1", "2", "Crepes & Waffles", 42_300.0, "Restaurantes",
        TransactionType.EXPENSE, TransactionSource.SMS, false, 1_745_870_400_000L),
    Transaction("t2", "2", "Uber", 28_500.0, "Transporte",
        TransactionType.EXPENSE, TransactionSource.SMS, true, 1_745_866_800_000L),

    // Ayer · 27 abr
    Transaction("t3", "2", "Éxito Country", 312_400.0, "Mercado",
        TransactionType.EXPENSE, TransactionSource.OCR, false, 1_745_784_000_000L),
    Transaction("t4", "2", "Daviplata", 80_000.0, "Transferencia",
        TransactionType.INCOME, TransactionSource.SMS, false, 1_745_780_400_000L),

    // 26 abr
    Transaction("t5", "2", "Globant", 4_500_000.0, "Nómina",
        TransactionType.INCOME, TransactionSource.SMS, false, 1_745_697_600_000L),
    Transaction("t6", "2", "Netflix", 28_900.0, "Suscripción",
        TransactionType.EXPENSE, TransactionSource.MANUAL, false, 1_745_694_000_000L),
    Transaction("t7", "2", "Drogas La Rebaja", 47_200.0, "Salud",
        TransactionType.EXPENSE, TransactionSource.OCR, true, 1_745_690_400_000L),
)

// Day labels keyed by the calendar day each timestamp falls on.
// Hard-coded for the seed dataset — when real data lands this becomes a
// proper date format.
private val dayLabelByTimestamp: (Long) -> String = { ts ->
    when {
        ts >= 1_745_812_800_000L -> "Hoy · 28 abr"
        ts >= 1_745_726_400_000L -> "Ayer · 27 abr"
        else -> "26 abr"
    }
}

private fun signedAmount(tx: Transaction): Double =
    if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount

fun Route.walletRoutes() {
    route("/api/wallets") {
        get {
            call.respond(wallets)
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wallet = wallets.find { it.id == id }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(wallet)
        }

        get("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(transactions.filter { it.walletId == id })
        }

        post("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val tx = call.receive<Transaction>()
            val wallet = wallets.find { it.id == id }
                ?: return@post call.respond(HttpStatusCode.NotFound)

            val updated = wallet.copy(balance = wallet.balance + signedAmount(tx))
            wallets[wallets.indexOf(wallet)] = updated

            transactions.add(tx)
            call.respond(HttpStatusCode.Created, tx)
        }
    }

    route("/api/transactions") {
        get("/by-day") {
            val grouped = transactions
                .sortedByDescending { it.timestamp }
                .groupBy { dayLabelByTimestamp(it.timestamp) }
                .map { (date, items) ->
                    TransactionDay(
                        date = date,
                        total = items.sumOf { signedAmount(it) },
                        items = items,
                    )
                }
            call.respond(grouped)
        }
    }
}
