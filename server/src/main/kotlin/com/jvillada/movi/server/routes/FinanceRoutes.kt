package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.Credit
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.SmsMessage
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val holdings = listOf(
    Holding("CDT Bancolombia", "12 meses · 11,8% E.A.", 5_000_000, 0.0),
    Holding("Acciones Globales", "Renta variable · Skandia", 4_280_000, 12.4),
    Holding("Renta Fija COL", "Bajo riesgo · Fiduciaria", 2_100_000, 4.2),
    Holding("Bitcoin", "Cripto · Binance", 1_100_000, -8.6),
)

private val credits = listOf(
    Credit("Crédito de vivienda", "Bancolombia", 240_000_000, 86_400_000, "11,2% E.A.", "30 abr", "\$1.860.000"),
    Credit("Tarjeta Falabella", "CMR", 4_320_000, 2_680_000, "24,5% E.A.", "5 may", "\$580.000"),
    Credit("Libre inversión", "Davivienda", 12_000_000, 7_200_000, "18,9% E.A.", "15 may", "\$420.000"),
)

private val goals = listOf(
    Goal("Viaje a Cartagena", 5_000_000, 3_400_000, "Junio 2026", 320_000),
    Goal("Cuota inicial apto", 30_000_000, 8_600_000, "Diciembre 2027", 1_200_000),
    Goal("Fondo de emergencia", 12_000_000, 12_000_000, "Completado", 0),
    Goal("Cumpleaños Mateo", 800_000, 220_000, "Agosto 2026", 145_000),
)

private val smsMessages = listOf(
    SmsMessage("hace 2 min", "Bancolombia", "Compra aprobada \$42.300 en Crepes & Waffles", "pending", "Crepes & Waffles · \$42.300"),
    SmsMessage("1 h", "Davivienda", "Recibiste \$80.000 de Daviplata", "pending", "Daviplata · +\$80.000"),
    SmsMessage("3 h", "Bancolombia", "Compra aprobada \$28.500 en Uber BV", "auto", "Uber · \$28.500"),
    SmsMessage("ayer", "Bancolombia", "Nómina recibida \$4.500.000", "auto", "Globant · +\$4.500.000"),
)

fun Route.financeRoutes() {
    get("/api/holdings") { call.respond(holdings) }
    get("/api/credits") { call.respond(credits) }
    get("/api/goals") { call.respond(goals) }
    get("/api/sms") { call.respond(smsMessages) }
}
