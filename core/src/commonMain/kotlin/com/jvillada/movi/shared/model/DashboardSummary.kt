package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * Lo que el Inicio necesita del server **ya reducido a números** — `GET /api/dashboard/summary`.
 *
 * Antes la pantalla se bajaba colecciones enteras para sacar una cifra: todos los SMS para
 * contar los pendientes, todos los candidatos a pago de tarjeta para un `.size`, todos los
 * eventos de la historia para sumar el gasto del mes por categoría. Con meses de uso real eso
 * crece lineal y hace lenta la pantalla más usada, sobre todo en el teléfono. Acá viaja solo el
 * resultado; el server lo calcula con las MISMAS reglas que el resto (`isCashFlow`,
 * `looksLikeCardPayment`, el mismo criterio de "pendiente" que el inbox de SMS).
 *
 * Todos los campos tienen default para que un cliente viejo y un server viejo sigan
 * deserializando sin romperse (el Json del server tiene `encodeDefaults=false`: un cero no viaja).
 */
@Serializable
data class DashboardSummary(
    val scope: Scope = Scope.SELF,
    /** Mes al que corresponden las cifras, "2026-08", con la misma zona horaria que usa el server. */
    val month: String = "",
    /** Ingresos del mes en COP que cuentan como flujo de caja — mismo número que `FinanceSummary.ingresos`. */
    val monthIncome: Long = 0,
    /** Egresos del mes en COP que cuentan como flujo de caja — mismo número que `FinanceSummary.egresos`. */
    val monthSpent: Long = 0,
    /**
     * Gasto del mes por categoría (egresos COP que son flujo de caja). Reemplaza al
     * `spentByCategoryForMonth(getEventsByDay(), mes)` que el Inicio calculaba del lado del
     * cliente; alimenta las alertas de presupuesto superado y la cifra del acceso «Presupuestos».
     */
    val spentByCategory: Map<String, Long> = emptyMap(),
    /** Cantidad que devolvería `GET /api/events/card-payment-candidates` — sin bajar la lista. */
    val cardPaymentCandidates: Int = 0,
    /** Mensajes del banco en estado `pending` — lo que el inbox de SMS llama «por confirmar». */
    val pendingSms: Int = 0,
)
