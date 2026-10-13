package com.jvillada.movi.server.storage

import com.jvillada.movi.server.db.ReglasDeComercio
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.MerchantRule
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("MerchantRulesStore")

/**
 * **Las reglas de comercio del importador, en la base** (`reglas_de_comercio`).
 *
 * Antes vivían en `movi-data/merchant-rules-<usuario>.json`, un archivo del contenedor de Railway:
 * cada despliegue arma un contenedor nuevo, así que lo que el importador aprendía se perdía en el
 * despliegue siguiente. Lo que todavía quede en un archivo se pasa a la base al arrancar
 * (`Migrations.pasarLasReglasDeComercioALaBase`).
 *
 * Por usuario, como antes: cada dueño ve solo las suyas. La misma regla de antes para guardar: el
 * mismo patrón reemplaza a la regla que ya estaba.
 *
 * **Una falla acá no tumba nada**, igual que cuando eran un archivo: las reglas son una ayuda para
 * la próxima lectura (ver `POST /api/statements/import`), y perder una no le mueve un peso a nadie.
 * Leer sin poder da la lista vacía; guardar sin poder queda en el log.
 */
class MerchantRulesStore {

    suspend fun getRules(userId: String): List<MerchantRule> = try {
        dbQuery {
            ReglasDeComercio.selectAll()
                .where { ReglasDeComercio.userId eq userId }
                .orderBy(ReglasDeComercio.actualizadaEn to SortOrder.ASC, ReglasDeComercio.patron to SortOrder.ASC)
                .map { MerchantRule(merchantPattern = it[ReglasDeComercio.patron], category = it[ReglasDeComercio.categoria]) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("no se pudieron leer las reglas de comercio de $userId", e)
        emptyList()
    }

    suspend fun saveRule(userId: String, rule: MerchantRule) {
        val patron = rule.merchantPattern.trim().take(255)
        val categoria = rule.category.trim().take(100)
        if (patron.isEmpty() || categoria.isEmpty()) return
        try {
            dbQuery {
                ReglasDeComercio.upsert {
                    it[ReglasDeComercio.userId] = userId
                    it[ReglasDeComercio.patron] = patron
                    it[ReglasDeComercio.categoria] = categoria
                    it[actualizadaEn] = System.currentTimeMillis()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("no se pudo guardar la regla de comercio «$patron» de $userId", e)
        }
    }
}
