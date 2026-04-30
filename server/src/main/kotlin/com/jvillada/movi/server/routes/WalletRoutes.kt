package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.JsonListStore
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
import java.io.File

private val walletSeed = listOf(
    Wallet("1", "Efectivo", 580_000.0, "COP"),
    Wallet("2", "Bancolombia Ahorros", 1_260_000.0, "COP"),
)

private val transactionSeed = listOf(
    Transaction("t1", "2", "Crepes & Waffles", 42_300.0, "Restaurantes",
        TransactionType.EXPENSE, TransactionSource.SMS, false, 1_745_870_400_000L),
    Transaction("t2", "2", "Uber", 28_500.0, "Transporte",
        TransactionType.EXPENSE, TransactionSource.SMS, true, 1_745_866_800_000L),
    Transaction("t3", "2", "Éxito Country", 312_400.0, "Mercado",
        TransactionType.EXPENSE, TransactionSource.OCR, false, 1_745_784_000_000L),
    Transaction("t4", "2", "Daviplata", 80_000.0, "Transferencia",
        TransactionType.INCOME, TransactionSource.SMS, false, 1_745_780_400_000L),
    Transaction("t5", "2", "Globant", 4_500_000.0, "Nómina",
        TransactionType.INCOME, TransactionSource.SMS, false, 1_745_697_600_000L),
    Transaction("t6", "2", "Netflix", 28_900.0, "Suscripción",
        TransactionType.EXPENSE, TransactionSource.MANUAL, false, 1_745_694_000_000L),
    Transaction("t7", "2", "Drogas La Rebaja", 47_200.0, "Salud",
        TransactionType.EXPENSE, TransactionSource.OCR, true, 1_745_690_400_000L),
)

private val walletStore = JsonListStore(
    file = File("movi-data/wallets.json"),
    elementSerializer = Wallet.serializer(),
    seed = walletSeed,
)

private val transactionStore = JsonListStore(
    file = File("movi-data/transactions.json"),
    elementSerializer = Transaction.serializer(),
    seed = transactionSeed,
)

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
        get { call.respond(walletStore.snapshot()) }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wallet = walletStore.snapshot().find { it.id == id }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(wallet)
        }

        get("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(transactionStore.snapshot().filter { it.walletId == id })
        }

        post("/{id}/transactions") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val tx = call.receive<Transaction>()
            val notFound = walletStore.mutate { wallets ->
                val idx = wallets.indexOfFirst { it.id == id }
                if (idx == -1) return@mutate true
                val w = wallets[idx]
                wallets[idx] = w.copy(balance = w.balance + signedAmount(tx))
                false
            }
            if (notFound) return@post call.respond(HttpStatusCode.NotFound)
            transactionStore.mutate { it.add(tx) }
            call.respond(HttpStatusCode.Created, tx)
        }
    }

    route("/api/transactions") {
        get("/by-day") {
            val grouped = transactionStore.snapshot()
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
