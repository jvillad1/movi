package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id           = varchar("id", 36)
    val email        = varchar("email", 255).uniqueIndex()
    val name         = varchar("name", 100)
    val passwordHash = varchar("password_hash", 255)
    override val primaryKey = PrimaryKey(id)
}

object Accounts : Table("accounts") {
    val id       = varchar("id", 36)
    val userId   = varchar("user_id", 36)
    val name     = varchar("name", 100)
    val type     = varchar("type", 30)
    val balance  = long("balance").default(0)
    val currency = varchar("currency", 10).default("COP")
    override val primaryKey = PrimaryKey(id)
}

object Events : Table("financial_events") {
    val id                   = varchar("id", 36)
    val userId               = varchar("user_id", 36)
    val accountId            = varchar("account_id", 36)
    val type                 = varchar("type", 20)
    val amount               = long("amount")
    val category             = varchar("category", 100)
    val description          = varchar("description", 255)
    val merchant             = varchar("merchant", 255).nullable()
    val timestamp            = long("timestamp")
    val eventSource          = varchar("source", 20).default("MANUAL")
    val rawPayload           = text("raw_payload").nullable()
    val reconciliationStatus = varchar("reconciliation_status", 20).default("UNCONFIRMED")
    val syncedAt             = long("synced_at").nullable()
    override val primaryKey  = PrimaryKey(id)
}

object VoidEvents : Table("void_events") {
    val id              = varchar("id", 36)
    val userId          = varchar("user_id", 36)
    val originalEventId = varchar("original_event_id", 36)
    val reason          = varchar("reason", 500).nullable()
    val timestamp       = long("timestamp")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("uq_void_events_original_user", originalEventId, userId) }
}
