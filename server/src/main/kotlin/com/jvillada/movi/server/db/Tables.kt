package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id           = varchar("id", 50)
    val email        = varchar("email", 255).uniqueIndex()
    val name         = varchar("name", 100)
    val passwordHash = varchar("password_hash", 255)
    override val primaryKey = PrimaryKey(id)
}

object Accounts : Table("accounts") {
    val id       = varchar("id", 50)
    val userId   = varchar("user_id", 50)
    val name     = varchar("name", 100)
    val type     = varchar("type", 30)
    val balance  = long("balance").default(0)
    val currency = varchar("currency", 10).default("COP")
    override val primaryKey = PrimaryKey(id)
}

object StatementImports : Table("statement_imports") {
    val id              = varchar("id", 50)
    val userId          = varchar("user_id", 50)
    val accountId       = varchar("account_id", 50)
    val bankName        = varchar("bank_name", 100)
    val period          = varchar("period", 50)
    val importedAt      = long("imported_at")
    val importedCount   = integer("imported_count")
    val reconciledCount = integer("reconciled_count")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_statement_imports_user_id", false, userId) }
}

object Events : Table("financial_events") {
    val id                   = varchar("id", 50)
    val userId               = varchar("user_id", 50)
    val accountId            = varchar("account_id", 50)
    val type                 = varchar("type", 20)
    val amount               = long("amount")
    val currency             = varchar("currency", 10).default("COP")
    val category             = varchar("category", 100)
    val description          = varchar("description", 255)
    val merchant             = varchar("merchant", 255).nullable()
    val timestamp            = long("timestamp")
    val eventSource          = varchar("source", 20).default("MANUAL")
    val rawPayload           = text("raw_payload").nullable()
    val reconciliationStatus = varchar("reconciliation_status", 20).default("UNCONFIRMED")
    val syncedAt             = long("synced_at").nullable()
    val statementImportId    = varchar("statement_import_id", 50).nullable()
    override val primaryKey  = PrimaryKey(id)
    init { index("idx_events_statement_import_id", false, statementImportId) }
}

object VoidEvents : Table("void_events") {
    val id              = varchar("id", 50)
    val userId          = varchar("user_id", 50)
    val originalEventId = varchar("original_event_id", 50)
    val reason          = varchar("reason", 500).nullable()
    val timestamp       = long("timestamp")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("uq_void_events_original_user", originalEventId, userId) }
}

object Budgets : Table("budgets") {
    val userId       = varchar("user_id", 50)
    val category     = varchar("category", 100)
    val monthlyLimit = long("monthly_limit")
    override val primaryKey = PrimaryKey(userId, category)
}

object RecurringRules : Table("recurring_rules") {
    val id                 = varchar("id", 50)
    val userId             = varchar("user_id", 50)
    val name               = varchar("name", 100)
    val category           = varchar("category", 100)
    val amount             = long("amount")
    val dayOfMonth         = integer("day_of_month")
    val type               = varchar("type", 20)
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable()  // "YYYY-MM", server-only
    override val primaryKey = PrimaryKey(id)
    init { index("idx_recurring_rules_user_id", false, userId) }
}

object SmsMessages : Table("sms_messages") {
    val id     = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val time   = varchar("time", 50)
    val bank   = varchar("bank", 100)
    val text   = text("text")
    val state  = varchar("state", 20)
    val det    = varchar("det", 255)
    override val primaryKey = PrimaryKey(id, userId)  // per-user: the same SMS id may exist for different users
    init { index("idx_sms_messages_user_id", false, userId) }
}
