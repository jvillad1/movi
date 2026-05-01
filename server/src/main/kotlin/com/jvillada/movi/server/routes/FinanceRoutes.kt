package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.Scope
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

private val holdings = listOf(
    Holding("CDT Bancolombia", "12 meses · 11,8% E.A.", 5_000_000, 0.0),
    Holding("Acciones Globales", "Renta variable · Skandia", 4_280_000, 12.4),
    Holding("Renta Fija COL", "Bajo riesgo · Fiduciaria", 2_100_000, 4.2),
    Holding("Bitcoin", "Cripto · Binance", 1_100_000, -8.6),
)

private val summaries = mapOf(
    Scope.SELF to FinanceSummary(Scope.SELF, balance = 1_840_000, ingresos = 4_500_000, egresos = 2_660_000),
    Scope.FAMILY to FinanceSummary(Scope.FAMILY, balance = 4_870_000, ingresos = 9_200_000, egresos = 4_330_000),
)

fun Route.financeRoutes() {
    get("/api/holdings") { call.respond(holdings) }
    get("/api/credits") { call.respond(Stores.credits.snapshot()) }
    get("/api/goals") { call.respond(Stores.goals.snapshot()) }
    get("/api/recurring-rules") { call.respond(Stores.recurring.snapshot()) }

    get("/api/budgets") { call.respond(Stores.budgets.list()) }
    post("/api/budgets") {
        val body = call.receive<Budget>()
        val saved = Stores.budgets.add(body)
        if (saved == null) call.respond(HttpStatusCode.Conflict, "Category exists: ${body.category}")
        else call.respond(HttpStatusCode.Created, saved)
    }
    put("/api/budgets/{category}") {
        val category = call.parameters["category"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receive<Budget>()
        val updated = Stores.budgets.update(category, body)
        if (updated == null) call.respond(HttpStatusCode.NotFound)
        else call.respond(updated)
    }
    delete("/api/budgets/{category}") {
        val category = call.parameters["category"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val removed = Stores.budgets.remove(category)
        if (removed) call.respond(HttpStatusCode.NoContent)
        else call.respond(HttpStatusCode.NotFound)
    }

    get("/api/finance-summary") {
        val raw = call.request.queryParameters["scope"] ?: "SELF"
        val scope = runCatching { Scope.valueOf(raw.uppercase()) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown scope: $raw")
        call.respond(summaries.getValue(scope))
    }
}
