package com.jvillada.movi.server.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class JsonListStore<T>(
    private val file: File,
    elementSerializer: KSerializer<T>,
    seed: List<T>,
) {
    private val lock = Mutex()
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val serializer = ListSerializer(elementSerializer)
    private val items: MutableList<T> = load(seed).toMutableList()

    private fun load(seed: List<T>): List<T> = runCatching {
        if (!file.exists()) return@runCatching seed
        json.decodeFromString(serializer, file.readText())
    }.getOrDefault(seed)

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, items.toList()))
    }

    suspend fun snapshot(): List<T> = lock.withLock { items.toList() }

    suspend fun <R> mutate(block: (MutableList<T>) -> R): R = lock.withLock {
        val result = block(items)
        persist()
        result
    }
}
