package com.jvillada.movi.server.storage

import com.jvillada.movi.shared.model.*
import java.io.File

private val DATA_DIR = File("movi-data")


object Stores {
    // Legacy wallet/transaction stores (used by WalletRoutes)
    val wallets      = JsonListStore(File(DATA_DIR, "wallets.json"),      Wallet.serializer(),         emptyList())
    val transactions = JsonListStore(File(DATA_DIR, "transactions.json"), Transaction.serializer(),    emptyList())

    val credits  = JsonListStore(File(DATA_DIR, "credits.json"),   Credit.serializer(),      emptyList())
    val goals    = JsonListStore(File(DATA_DIR, "goals.json"),     Goal.serializer(),        emptyList())
    val recurring = JsonListStore(File(DATA_DIR, "recurring.json"), RecurringRule.serializer(), emptyList())
    val sms      = JsonListStore(File(DATA_DIR, "sms.json"),       SmsMessage.serializer(),  emptyList())

    val merchantRules = MerchantRulesStore()
}
