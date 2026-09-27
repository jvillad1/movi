package com.jvillada.movi.data

import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UpcomingPayment

/**
 * # Para las pruebas que montan Movimientos sin hablar del «Día a día»
 *
 * Movimientos, con el período en curso a la vista, lee las cuatro cosas de las que sale la meta del
 * «Día a día» (ver `leerDatosDelDiaADia`). Con [RepositorioDePrueba] a secas cada una explota con su
 * nombre, y `intentar` se traga la explosión: la prueba pasa, pero la guarda de «esta prueba no
 * esperaba que la pantalla llamara a…» queda muda justo donde más importa.
 *
 * Esta clase **declara esas cuatro lecturas y las contesta «sin margen»** (cero ingresos: no hay
 * disponible que dividir, o sea ninguna línea), y todo lo demás sigue explotando como siempre. Una
 * prueba de Movimientos que no habla del «Día a día» la usa; una que sí, sobrescribe lo que
 * necesita (ver `DiaADiaEnMovimientosTest`).
 */
open class RepositorioDePruebaDeMovimientos : RepositorioDePrueba() {
    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
        FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0)

    override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = DashboardSummary()

    override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()

    override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
}
