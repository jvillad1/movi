package com.jvillada.movi.server.storage

import com.jvillada.movi.shared.model.MerchantRule
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MerchantRulesStore {

    private val stores = ConcurrentHashMap<String, JsonListStore<MerchantRule>>()

    private fun storeFor(userId: String): JsonListStore<MerchantRule> =
        stores.getOrPut(userId) {
            JsonListStore(
                file = File("movi-data", "merchant-rules-$userId.json"),
                elementSerializer = MerchantRule.serializer(),
                seed = emptyList(),
            )
        }

    suspend fun getRules(userId: String): List<MerchantRule> =
        storeFor(userId).snapshot()

    suspend fun saveRule(userId: String, rule: MerchantRule) {
        storeFor(userId).mutate { list ->
            val i = list.indexOfFirst { it.merchantPattern == rule.merchantPattern }
            if (i >= 0) list[i] = rule else list.add(rule)
        }
    }
}
