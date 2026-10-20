package com.jvillada.movi.server.apartados

import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.restanteDelApartado
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum

/**
 * **Los apartados del usuario con su `restante` derivado**: `monto` menos lo gastado (EXPENSE, COP, sin
 * anulados) en la cuenta, en la categoría del apartado y desde su `desde`. Un apartado sin
 * categoría no consume nada solo: su restante es su monto.
 *
 * Una suma por apartado, en SQL: el usuario tiene pocos y no vale traerse la historia entera.
 */
fun Transaction.apartadosDelUsuario(uid: String, voidedIds: Set<String>): List<Apartado> {
    val total = Events.amount.sum()
    return Apartados.selectAll()
        .where { Apartados.userId eq uid }
        .orderBy(Apartados.createdAt)
        .map { fila ->
            val categoria = fila[Apartados.categoria]
            val consumido = if (categoria.isNullOrBlank()) 0L else {
                Events.select(total)
                    .where {
                        val base = (Events.userId eq uid) and
                            (Events.accountId eq fila[Apartados.accountId]) and
                            (Events.type eq "EXPENSE") and
                            (Events.currency eq "COP") and
                            (Events.category eq categoria) and
                            (Events.timestamp greaterEq fila[Apartados.desde])
                        if (voidedIds.isEmpty()) base else base and (Events.id notInList voidedIds.toList())
                    }
                    .firstOrNull()?.get(total) ?: 0L
            }
            Apartado(
                id = fila[Apartados.id],
                accountId = fila[Apartados.accountId],
                proposito = fila[Apartados.proposito],
                monto = fila[Apartados.monto],
                categoria = categoria,
                desde = fila[Apartados.desde],
                restante = restanteDelApartado(fila[Apartados.monto], consumido),
            )
        }
}
