package com.jvillada.movi.server.storage

import com.jvillada.movi.shared.model.Budget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class BudgetStorage(
    private val file: File,
    seed: List<Budget>,
) {
    private val lock = Mutex()
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val serializer = ListSerializer(Budget.serializer())
    private val budgets: MutableList<Budget> = load(seed).toMutableList()

    private fun load(seed: List<Budget>): List<Budget> = runCatching {
        if (!file.exists()) return@runCatching seed
        json.decodeFromString(serializer, file.readText())
    }.getOrDefault(seed)

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, budgets.toList()))
    }

    suspend fun list(): List<Budget> = lock.withLock { budgets.toList() }

    suspend fun add(budget: Budget): Budget? = lock.withLock {
        if (budgets.any { it.category.equals(budget.category, ignoreCase = true) }) return@withLock null
        budgets.add(budget)
        persist()
        budget
    }

    suspend fun update(category: String, budget: Budget): Budget? = lock.withLock {
        val idx = budgets.indexOfFirst { it.category.equals(category, ignoreCase = true) }
        if (idx == -1) return@withLock null
        budgets[idx] = budget
        persist()
        budget
    }

    suspend fun remove(category: String): Boolean = lock.withLock {
        val removed = budgets.removeAll { it.category.equals(category, ignoreCase = true) }
        if (removed) persist()
        removed
    }
}
