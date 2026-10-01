package com.jvillada.movi.avisos

import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo

/**
 * **Los pagos del período, leídos desde un Worker**, para decidir qué vence hoy o mañana.
 *
 * Las mismas tres lecturas que usa Plan —el perfil (de donde sale el período del dueño),
 * `/api/payments/upcoming` y `/api/payments/occurrences`— y la MISMA función que arma su lista
 * («Pagos del período»): [checklistDelPeriodo]. Nada se recalcula acá; lo único nuevo es quién
 * pregunta.
 *
 * Contra el server directo ([WalletRepositoryImpl]) y no contra `Repositories.wallets`: ese abre
 * la base de SQLDelight, que necesita `DatabaseDriverFactory.init` de la `MainActivity`, y un
 * Worker puede correr con el proceso levantado solo (ver `Repositories.olvidarDatosLocales`). El
 * token lo pone `createHttpClient` desde `SessionManager`, como en toda la app.
 *
 * Lanza si alguna lectura falla: sin las ocurrencias todo parecería pendiente y se avisaría de algo
 * ya pagado. Quien llama decide reintentar.
 */
suspend fun leerPagosDelPeriodo(ahora: Long = System.currentTimeMillis()): List<PagoDelPeriodo> {
    val cliente = createHttpClient()
    try {
        val repo = WalletRepositoryImpl(cliente, apiBaseUrl)
        val perfil = repo.getUserProfile()
        val ajustes = PeriodSettings(perfil.periodCutoffDay, perfil.periodStarts)
        return checklistDelPeriodo(
            upcoming = repo.getUpcomingPayments(),
            ocurrencias = repo.getOccurrenceStates(),
            periodo = periodoDe(ahora, ajustes),
            settings = ajustes,
        )
    } finally {
        cliente.close()
    }
}
