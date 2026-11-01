package com.jvillada.movi.avisos

import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import com.jvillada.movi.ui.dashboard.conElPerfil
import com.jvillada.movi.ui.dashboard.conResumenDelInicio
import kotlinx.coroutines.async

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
        val ajustes = perfil.ajustesDelPeriodo()
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

/**
 * **Los orígenes de captura que se callaron**, leídos desde un Worker (Ola 2 · banco mudo). Contra el
 * server directo, por lo mismo que [leerPagosDelPeriodo]: un Worker puede correr sin la base local.
 * Lanza si la lectura falla; quien llama decide reintentar.
 */
suspend fun leerOrigenesMudos(): List<com.jvillada.movi.shared.model.OrigenMudo> {
    val cliente = createHttpClient()
    try {
        return WalletRepositoryImpl(cliente, apiBaseUrl).getOrigenesMudos()
    } finally {
        cliente.close()
    }
}

/**
 * **Lo que el banco debió cobrar solo y nadie confirmó**, leído desde un Worker (`GET
 * /api/debitos-automaticos`, la misma lista que pinta «Por revisar»). Contra el server directo, por lo
 * mismo que [leerPagosDelPeriodo]. Lanza si la lectura falla; quien llama decide reintentar.
 */
suspend fun leerDebitosAutomaticos(): List<com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar> {
    val cliente = createHttpClient()
    try {
        return WalletRepositoryImpl(cliente, apiBaseUrl).getDebitosAutomaticos()
    } finally {
        cliente.close()
    }
}

/**
 * **El resumen del período que acaba de cerrar**, leído desde un Worker (`GET /api/cierre-del-periodo`,
 * lo mismo que pinta la tarjeta de Hoy). `null` = no hay nada que avisar (204). Contra el server
 * directo, por lo mismo que [leerPagosDelPeriodo]. Lanza si la lectura falla; quien llama decide
 * reintentar.
 */
suspend fun leerCierreDelPeriodo(): com.jvillada.movi.shared.model.CierreDelPeriodo? {
    val cliente = createHttpClient()
    try {
        return WalletRepositoryImpl(cliente, apiBaseUrl).getCierreDelPeriodo()
    } finally {
        cliente.close()
    }
}

/**
 * Vuelta 94 · **los extractos de tarjeta que faltan subir**, leídos desde un Worker (`GET
 * /api/extractos-por-subir`, la misma lista que propone Hoy). Contra el server directo, por lo mismo que
 * [leerPagosDelPeriodo]. Lanza si la lectura falla; quien llama decide reintentar.
 */
suspend fun leerExtractosPorSubir(): List<com.jvillada.movi.shared.model.ExtractoPorSubir> {
    val cliente = createHttpClient()
    try {
        return WalletRepositoryImpl(cliente, apiBaseUrl).getExtractosPorSubir()
    } finally {
        cliente.close()
    }
}

/**
 * Vuelta 68 · **lo que necesita el resumen de la mañana**, leído desde un Worker: las cifras del héroe
 * del Inicio y el conteo de «Por revisar» —las mismas lecturas y traducciones que el Inicio
 * (`conResumenDelInicio`, `conElPerfil`): perfil, `/api/finance/summary`, `/api/dashboard/summary`,
 * vencimientos, ocurrencias, cuentas y apartados, más los débitos y las propuestas de saldar—. Contra el
 * server directo, por lo mismo que [leerPagosDelPeriodo]. Lanza si alguna de las que sostienen la
 * cifra falla; los débitos y las propuestas son secundarios (sin ellos, no suman).
 */
suspend fun leerElInicioParaElResumen(ahora: Long = System.currentTimeMillis()): com.jvillada.movi.ui.dashboard.DashboardData {
    val cliente = createHttpClient()
    try {
        val repo = WalletRepositoryImpl(cliente, apiBaseUrl)
        return kotlinx.coroutines.coroutineScope {
            val perfil = async { repo.getUserProfile() }
            val resumen = async { repo.getFinanceSummary(com.jvillada.movi.shared.model.Scope.SELF) }
            val tablero = async { repo.getDashboardSummary(com.jvillada.movi.shared.model.Scope.SELF) }
            val vencimientos = async { repo.getUpcomingPayments() }
            val ocurrencias = async { repo.getOccurrenceStates() }
            val cuentas = async { repo.getAccounts() }
            val apartados = async { runCatching { repo.getApartados() }.getOrNull() }
            val debitos = async { runCatching { repo.getDebitosAutomaticos() }.getOrNull() }
            val saldar = async { runCatching { repo.getPropuestasDeSaldar() }.getOrNull() }
            com.jvillada.movi.ui.dashboard.DashboardData(
                summary = resumen.await(),
                upcoming = vencimientos.await(),
                ocurrencias = ocurrencias.await(),
                accounts = cuentas.await(),
                apartados = apartados.await(),
                debitosPorConfirmar = debitos.await(),
                propuestasDeSaldar = saldar.await(),
            ).conResumenDelInicio(tablero.await()).conElPerfil(perfil.await(), ahora)
        }
    } finally {
        cliente.close()
    }
}

/**
 * Vuelta 68 · **lo gastado ayer**, leído desde un Worker: los días de los últimos movimientos
 * (`/api/events/by-day?ultimos=N`, días enteros) y tu parte de lo que pagaron otros, con la regla de
 * «Gastos» de Actividad ([gastoDeAyer]). `null` si no se pudo leer o no alcanza a cubrir ayer: el
 * resumen sale sin esa parte.
 */
suspend fun leerElGastoDeAyer(
    tiposDeCuenta: Map<String, com.jvillada.movi.shared.model.AccountType>,
    ahora: Long = System.currentTimeMillis(),
): Long? {
    val cliente = createHttpClient()
    try {
        val repo = WalletRepositoryImpl(cliente, apiBaseUrl)
        val dias = runCatching { repo.getUltimosDias(MOVIMIENTOS_PARA_EL_GASTO_DE_AYER) }.getOrNull() ?: return null
        val tuParte = runCatching { repo.getTuParteDeOtros() }.getOrDefault(emptyList())
        return gastoDeAyer(dias, tuParte, ahora, tiposDeCuenta)
    } finally {
        cliente.close()
    }
}

/**
 * Vuelta 68 · **los grupos del dueño con su detalle**, leídos desde un Worker para el resumen semanal
 * (`GET /api/espacios` y `GET /api/espacios/{id}`, lo mismo que pinta la pantalla del grupo). Lanza si
 * la lista falla; un grupo cuyo detalle no contesta se salta.
 */
suspend fun leerLosGruposConSuDetalle(): List<com.jvillada.movi.shared.model.DetalleDelEspacio> {
    val cliente = createHttpClient()
    try {
        val repo = WalletRepositoryImpl(cliente, apiBaseUrl)
        return repo.getEspacios().mapNotNull { e -> runCatching { repo.getDetalleDelEspacio(e.id) }.getOrNull() }
    } finally {
        cliente.close()
    }
}
