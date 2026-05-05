package com.jvillada.movi.server.storage

import com.jvillada.movi.shared.model.*
import java.io.File

private val DATA_DIR = File("movi-data")

private val walletSeed = listOf(
    Wallet("1", "Efectivo", 580_000.0, "COP"),
    Wallet("2", "Bancolombia Ahorros", 1_260_000.0, "COP"),
)

private val transactionSeed = listOf(
    Transaction("t1", "2", "Crepes & Waffles", 42_300.0, "Restaurantes", TransactionType.EXPENSE, TransactionSource.SMS, false, 1_745_870_400_000L),
    Transaction("t2", "2", "Uber", 28_500.0, "Transporte", TransactionType.EXPENSE, TransactionSource.SMS, true, 1_745_866_800_000L),
    Transaction("t3", "2", "Éxito Country", 312_400.0, "Mercado", TransactionType.EXPENSE, TransactionSource.OCR, false, 1_745_784_000_000L),
    Transaction("t4", "2", "Daviplata", 80_000.0, "Transferencia", TransactionType.INCOME, TransactionSource.SMS, false, 1_745_780_400_000L),
    Transaction("t5", "2", "Globant", 4_500_000.0, "Nómina", TransactionType.INCOME, TransactionSource.SMS, false, 1_745_697_600_000L),
    Transaction("t6", "2", "Netflix", 28_900.0, "Suscripción", TransactionType.EXPENSE, TransactionSource.MANUAL, false, 1_745_694_000_000L),
    Transaction("t7", "2", "Drogas La Rebaja", 47_200.0, "Salud", TransactionType.EXPENSE, TransactionSource.OCR, true, 1_745_690_400_000L),
)

private val creditSeed = listOf(
    Credit("Crédito de vivienda", "Bancolombia", 240_000_000, 86_400_000, "11,2% E.A.", "30 abr", "\$1.860.000"),
    Credit("Tarjeta Falabella", "CMR", 4_320_000, 2_680_000, "24,5% E.A.", "5 may", "\$580.000"),
    Credit("Libre inversión", "Davivienda", 12_000_000, 7_200_000, "18,9% E.A.", "15 may", "\$420.000"),
)

private val goalSeed = listOf(
    Goal("Viaje a Cartagena", 5_000_000, 3_400_000, "Junio 2026", 320_000),
    Goal("Cuota inicial apto", 30_000_000, 8_600_000, "Diciembre 2027", 1_200_000),
    Goal("Fondo de emergencia", 12_000_000, 12_000_000, "Completado", 0),
    Goal("Cumpleaños Mateo", 800_000, 220_000, "Agosto 2026", 145_000),
)

private val recurringSeed = listOf(
    RecurringRule("r1", "Salario Globant",      "Nómina",       4_500_000, 25, TransactionType.INCOME),
    RecurringRule("r2", "Netflix",              "Suscripción",  28_900,    1,  TransactionType.EXPENSE),
    RecurringRule("r3", "Arriendo apartamento", "Servicios",    1_500_000, 5,  TransactionType.EXPENSE),
    RecurringRule("r4", "Internet Claro",       "Servicios",    89_000,    10, TransactionType.EXPENSE),
    RecurringRule("r5", "Spotify Family",       "Suscripción",  19_900,    15, TransactionType.EXPENSE),
)

private val smsSeed = listOf(
    SmsMessage("s1", "hace 2 min", "Bancolombia", "Compra aprobada \$42.300 en Crepes & Waffles el 28/04 a las 13:24.", "pending", "Crepes & Waffles · \$42.300"),
    SmsMessage("s2", "1 h", "Davivienda", "Recibiste \$80.000 de Daviplata.", "pending", "Daviplata · +\$80.000"),
    SmsMessage("s3", "3 h", "Bancolombia", "Compra aprobada \$28.500 en Uber BV.", "auto", "Uber · \$28.500"),
    SmsMessage("s4", "ayer", "Bancolombia", "Nómina recibida \$4.500.000.", "auto", "Globant · +\$4.500.000"),
)

object Stores {
    // Legacy wallet/transaction stores (used by WalletRoutes)
    val wallets      = JsonListStore(File(DATA_DIR, "wallets.json"),      Wallet.serializer(),         walletSeed)
    val transactions = JsonListStore(File(DATA_DIR, "transactions.json"), Transaction.serializer(),    transactionSeed)

    // Informational read-only stores (no per-user data yet)
    val credits  = JsonListStore(File(DATA_DIR, "credits.json"),   Credit.serializer(),      creditSeed)
    val goals    = JsonListStore(File(DATA_DIR, "goals.json"),     Goal.serializer(),        goalSeed)
    val recurring = JsonListStore(File(DATA_DIR, "recurring.json"), RecurringRule.serializer(), recurringSeed)
    val sms      = JsonListStore(File(DATA_DIR, "sms.json"),       SmsMessage.serializer(),  smsSeed)

    val merchantRules = MerchantRulesStore()
}
