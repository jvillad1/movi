package com.jvillada.movi.server.observabilidad

import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.slf4j.LoggerFactory

/**
 * # Las consultas lentas (vuelta 102)
 *
 * Toda sentencia de Exposed pasa por acá (se registra como `GlobalStatementInterceptor` en
 * `META-INF/services`, así que cubre cada `transaction {}` sin tocar ninguno). Si tardó más de
 * [UMBRAL_MS], queda una línea en el log con **la ruta que la pidió** (la plantilla, sin parámetros),
 * el tipo de sentencia y las tablas — nunca el SQL con sus valores — y se cuenta en el minuto.
 *
 * El umbral de 500 ms es el del pedido del dueño. Se mide de `beforeExecution` a `afterExecution`: la
 * ejecución en la base, no la lectura de las filas que viene después.
 */
class ConsultasLentas : GlobalStatementInterceptor {

    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        transaction.putUserData(INICIO, System.nanoTime())
    }

    override fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {
        val inicio = transaction.getUserData(INICIO) ?: return
        transaction.removeUserData(INICIO)
        val ms = (System.nanoTime() - inicio) / 1_000_000
        if (ms < UMBRAL_MS) return
        Metricas.registrarConsultaLenta()
        val sentencia = contexts.firstOrNull()?.statement
        val tipo = sentencia?.type?.name ?: "?"
        val tablas = sentencia?.targets?.joinToString(",") { it.tableName }.orEmpty().take(120)
        val donde = ElPedidoEnCurso.actual()?.donde ?: "(fuera de un pedido)"
        log.warn("consulta lenta: {} ms · {} · {} [{}]", ms, donde, tipo, tablas)
    }

    companion object {
        const val UMBRAL_MS: Long = 500
        private val INICIO = Key<Long>()
        private val log = LoggerFactory.getLogger("com.jvillada.movi.server.observabilidad.ConsultasLentas")
    }
}
