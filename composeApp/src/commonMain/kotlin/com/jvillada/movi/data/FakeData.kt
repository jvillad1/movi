package com.jvillada.movi.data

data class TxItem(
    val name: String,
    val category: String,
    val amount: Long,
    val source: String,
    val pending: Boolean = false,
)

data class TxDay(
    val date: String,
    val total: Long,
    val items: List<TxItem>,
)

data class Holding(
    val name: String,
    val sub: String,
    val amount: Long,
    val change: Double,
)

data class Credit(
    val name: String,
    val bank: String,
    val total: Long,
    val paid: Long,
    val rate: String,
    val nextDate: String,
    val nextAmt: String,
)

data class Goal(
    val name: String,
    val target: Long,
    val saved: Long,
    val deadline: String,
    val monthly: Long,
)

data class SMSItem(
    val time: String,
    val bank: String,
    val text: String,
    val state: String,
    val det: String,
)

object FakeData {
    val txDays = listOf(
        TxDay("Hoy · 28 abr", -70_800L, listOf(
            TxItem("Crepes & Waffles", "Restaurantes", -42_300, "SMS"),
            TxItem("Uber", "Transporte", -28_500, "SMS", pending = true),
        )),
        TxDay("Ayer · 27 abr", -232_400L, listOf(
            TxItem("Éxito Country", "Mercado", -312_400, "OCR"),
            TxItem("Daviplata", "Transferencia", 80_000, "SMS"),
        )),
        TxDay("26 abr", 4_423_900L, listOf(
            TxItem("Globant", "Nómina", 4_500_000, "SMS"),
            TxItem("Netflix", "Suscripción", -28_900, "Manual"),
            TxItem("Drogas La Rebaja", "Salud", -47_200, "OCR", pending = true),
        )),
    )

    val holdings = listOf(
        Holding("CDT Bancolombia", "12 meses · 11,8% E.A.", 5_000_000, 0.0),
        Holding("Acciones Globales", "Renta variable · Skandia", 4_280_000, 12.4),
        Holding("Renta Fija COL", "Bajo riesgo · Fiduciaria", 2_100_000, 4.2),
        Holding("Bitcoin", "Cripto · Binance", 1_100_000, -8.6),
    )

    val credits = listOf(
        Credit("Crédito de vivienda", "Bancolombia", 240_000_000, 86_400_000, "11,2% E.A.", "30 abr", "\$1.860.000"),
        Credit("Tarjeta Falabella", "CMR", 4_320_000, 2_680_000, "24,5% E.A.", "5 may", "\$580.000"),
        Credit("Libre inversión", "Davivienda", 12_000_000, 7_200_000, "18,9% E.A.", "15 may", "\$420.000"),
    )

    val goals = listOf(
        Goal("Viaje a Cartagena", 5_000_000, 3_400_000, "Junio 2026", 320_000),
        Goal("Cuota inicial apto", 30_000_000, 8_600_000, "Diciembre 2027", 1_200_000),
        Goal("Fondo de emergencia", 12_000_000, 12_000_000, "Completado", 0),
        Goal("Cumpleaños Mateo", 800_000, 220_000, "Agosto 2026", 145_000),
    )

    val smsItems = listOf(
        SMSItem("hace 2 min", "Bancolombia", "Compra aprobada \$42.300 en Crepes & Waffles", "pending", "Crepes & Waffles · \$42.300"),
        SMSItem("1 h", "Davivienda", "Recibiste \$80.000 de Daviplata", "pending", "Daviplata · +\$80.000"),
        SMSItem("3 h", "Bancolombia", "Compra aprobada \$28.500 en Uber BV", "auto", "Uber · \$28.500"),
        SMSItem("ayer", "Bancolombia", "Nómina recibida \$4.500.000", "auto", "Globant · +\$4.500.000"),
    )
}
