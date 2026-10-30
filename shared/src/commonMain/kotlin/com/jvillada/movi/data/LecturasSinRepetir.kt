package com.jvillada.movi.data

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * # Una lectura no se pide dos veces seguidas (vuelta 78)
 *
 * La revisión 6 contó las peticiones de la web contra la API local: al arrancar se pedían **dos veces**
 * `users/me` y `accounts` (el Inicio y la guía de primer uso, a la vez), Pagos pedía `accounts` **tres**
 * veces (su tablero, la caja y el disponible), y Actividad bajaba la historia entera cada vez que se
 * abría. En el teléfono, contra Railway y con datos móviles, cada una de esas es tiempo y plata.
 *
 * Esto envuelve el repositorio de la app con dos reglas, y nada más:
 *
 * 1. **Unir lo que ya viaja** (*single-flight*): si una lectura igual —el mismo método con los mismos
 *    argumentos— ya salió y no volvió, la segunda espera la respuesta de la primera en vez de salir.
 * 2. **Fresca por [FRESCURA_DE_UNA_LECTURA_MS]**: lo que contestó bien hace menos de 30 s se devuelve
 *    sin salir. Es el mismo medio minuto que el Inicio ya usaba para no recargarse al volver
 *    (`TTL_DEL_INICIO_MS`), llevado a cada lectura y a cada pantalla.
 *
 * ## Lo que la vuelve vieja al instante
 *
 * - **Cualquier escritura propia**: `InvalidaElInicioAlEscribir` (que va POR FUERA de esto) sube
 *   [CacheDeLecturas.generacion] después de escribir, y una lectura de otra generación no se devuelve ni
 *   se une. Anotar un gasto y volver al Inicio lo muestra, como siempre. Una lectura que salió ANTES de
 *   la escritura tampoco se guarda (la generación cambió mientras viajaba).
 * - **Otra sesión**: cada lectura lleva el id de quien la pidió; cerrar sesión además sube la
 *   generación (`SessionManager.clear` → `CacheDeLecturas.borrarTodo`).
 *
 * Lo que queda afuera, con 30 s de retraso como mucho: lo que cambia sin que el dueño haga nada en este
 * aparato (un aviso que entró en segundo plano, otro dispositivo, la sincronización del teléfono).
 * **Por eso la bandeja «Por revisar» no pasa por acá** —los avisos del banco, los candidatos a pago de
 * tarjeta, las propuestas de la bandeja y las de saldar un grupo—: tocar la notificación de un aviso que
 * acaba de llegar tiene que mostrarlo, no la bandeja de hace 20 s.
 *
 * ## Sin conexión, igual que antes
 *
 * **Una falla no se recuerda**: la lectura siguiente vuelve a salir, y el «Reintentar» de cada pantalla
 * sigue reintentando de verdad. Quien esperaba la misma lectura recibe la misma falla. En el teléfono
 * el repositorio de abajo es el local (`LocalRepository`): esto solo evita volver a preguntarle lo mismo
 * dentro de medio minuto; qué contesta sin señal no cambia.
 *
 * ## Si quien la pidió primero se va
 *
 * Una pantalla que se cierra cancela su lectura. Las demás que la esperaban no heredan esa
 * cancelación: salen ellas ([LecturaAbandonada]).
 *
 * Solo en memoria, y dentro del repositorio de la app (`Repositories`): no hay `object` que limpiar al
 * cerrar sesión, porque la generación ya lo invalida todo.
 */
internal class LecturasSinRepetir(
    private val delegado: WalletRepository,
    private val lecturas: LecturasCompartidas = LecturasCompartidas(),
) : WalletRepository by delegado {

    override suspend fun getUserProfile(): UserProfile = lecturas.leer("perfil") { delegado.getUserProfile() }
    override suspend fun getAccounts(): List<Account> = lecturas.leer("cuentas") { delegado.getAccounts() }
    override suspend fun getCredits(): List<CreditSummary> = lecturas.leer("creditos") { delegado.getCredits() }
    override suspend fun getCards(): List<CardSummary> = lecturas.leer("tarjetas") { delegado.getCards() }
    override suspend fun getSubscriptions(): SubscriptionsResult = lecturas.leer("suscripciones") { delegado.getSubscriptions() }
    override suspend fun getApartados(): List<Apartado> = lecturas.leer("apartados") { delegado.getApartados() }
    override suspend fun getEspacios(): List<Espacio> = lecturas.leer("espacios") { delegado.getEspacios() }
    override suspend fun getTuParteDeOtros(): List<FinancialEvent> = lecturas.leer("tu-parte") { delegado.getTuParteDeOtros() }
    override suspend fun getDestinos(): List<DestinoConocido> = lecturas.leer("destinos") { delegado.getDestinos() }
    override suspend fun getDestinosSugeridos(): List<DestinoSugerido> = lecturas.leer("destinos-sugeridos") { delegado.getDestinosSugeridos() }
    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary = lecturas.leer("finanzas:$scope") { delegado.getFinanceSummary(scope) }
    override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = lecturas.leer("inicio:$scope") { delegado.getDashboardSummary(scope) }
    override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = lecturas.leer("gasto-del-dia-a-dia") { delegado.getGastoDelDiaADia() }
    override suspend fun getHistoriaDelPatrimonio(): HistoriaDelPatrimonio = lecturas.leer("historia-del-patrimonio") { delegado.getHistoriaDelPatrimonio() }
    override suspend fun getAnomalias(): List<Anomalia> = lecturas.leer("anomalias") { delegado.getAnomalias() }
    override suspend fun getCierreDelPeriodo(): CierreDelPeriodo? = lecturas.leer("cierre") { delegado.getCierreDelPeriodo() }
    override suspend fun getCuadresPorAviso(): List<PropuestaDeCuadrePorAviso> = lecturas.leer("cuadres-por-aviso") { delegado.getCuadresPorAviso() }
    override suspend fun getPeriodos(): List<ResumenDePeriodo> = lecturas.leer("periodos") { delegado.getPeriodos() }
    override suspend fun getBudgets(): List<Budget> = lecturas.leer("presupuestos") { delegado.getBudgets() }
    override suspend fun getCategories(): List<CategoryUsage> = lecturas.leer("categorias") { delegado.getCategories() }
    override suspend fun getRecurringRules(): List<RecurringRule> = lecturas.leer("recurrentes") { delegado.getRecurringRules() }
    override suspend fun getUpcomingPayments(): List<UpcomingPayment> = lecturas.leer("proximos-pagos") { delegado.getUpcomingPayments() }
    override suspend fun getOccurrenceStates(): List<OccurrenceState> = lecturas.leer("ocurrencias") { delegado.getOccurrenceStates() }
    override suspend fun getDebitosAutomaticos(): List<DebitoAutomaticoPorConfirmar> = lecturas.leer("debitos") { delegado.getDebitosAutomaticos() }
    override suspend fun getEventsByDay(): List<EventDay> = lecturas.leer("historia") { delegado.getEventsByDay() }
    override suspend fun getUltimosDias(ultimos: Int): List<EventDay> = lecturas.leer("ultimos:$ultimos") { delegado.getUltimosDias(ultimos) }
}

/** Cuánto vale lo que contestó una lectura antes de volver a pedirla: medio minuto, como el Inicio. */
const val FRESCURA_DE_UNA_LECTURA_MS: Long = 30_000L

/** Quien pidió primero una lectura se fue (se canceló): quien la esperaba la pide por su cuenta. */
internal class LecturaAbandonada : Exception("La lectura compartida se canceló")

/**
 * El motor de [LecturasSinRepetir]: unir lo que viaja y recordar lo fresco, por clave. Aparte para
 * probarlo sin repositorio, con su reloj, su generación y su usuario.
 */
internal class LecturasCompartidas(
    private val reloj: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    // Una escritura propia (CacheDeLecturas) o algo que entró por fuera (LoFresco): cualquiera de las dos.
    private val generacion: () -> Int = { CacheDeLecturas.generacion + LoFresco.vueltas },
    private val usuario: () -> String? = { SessionManager.userId },
    private val frescura: Long = FRESCURA_DE_UNA_LECTURA_MS,
) {
    private class Fresca(val valor: Any?, val en: Long, val generacion: Int, val usuario: String)
    private class EnVuelo(val generacion: Int, val usuario: String, val resultado: CompletableDeferred<Any?>)

    private val candado = Mutex()
    private var frescas: Map<String, Fresca> = emptyMap()
    private var enVuelo: Map<String, EnVuelo> = emptyMap()

    @Suppress("UNCHECKED_CAST") // la clave fija el tipo: cada clave la usa un solo método
    suspend fun <T> leer(clave: String, pedir: suspend () -> T): T {
        // Sin sesión no se recuerda ni se une nada: no hay de quién sería.
        val quien = usuario() ?: return pedir()
        val gen = generacion()
        var propio: CompletableDeferred<Any?>? = null
        var fresca: Fresca? = null
        val ajeno: CompletableDeferred<Any?>? = candado.withLock {
            frescas[clave]?.let { f ->
                val edad = reloj() - f.en
                // Una edad negativa es un reloj que se movió para atrás: no se sabe cuán vieja es.
                if (f.usuario == quien && f.generacion == gen && edad in 0..frescura) fresca = f
                else frescas = frescas - clave
            }
            if (fresca != null) return@withLock null
            val v = enVuelo[clave]
            if (v != null && v.usuario == quien && v.generacion == gen) {
                v.resultado
            } else {
                val nuevo = CompletableDeferred<Any?>()
                enVuelo = enVuelo + (clave to EnVuelo(gen, quien, nuevo))
                propio = nuevo
                null
            }
        }
        fresca?.let { return it.valor as T }
        if (ajeno != null) {
            return try {
                ajeno.await() as T
            } catch (e: LecturaAbandonada) {
                leer(clave, pedir)
            }
        }
        val miVuelo = propio!!
        val valor = try {
            pedir()
        } catch (e: Throwable) {
            withContext(NonCancellable) { candado.withLock { soltar(clave, miVuelo) } }
            miVuelo.completeExceptionally(if (e is CancellationException) LecturaAbandonada() else e)
            throw e
        }
        withContext(NonCancellable) {
            candado.withLock {
                soltar(clave, miVuelo)
                // Solo si en el medio no hubo una escritura ni otra sesión (ver el KDoc de la clase).
                if (usuario() == quien && generacion() == gen) {
                    frescas = frescas + (clave to Fresca(valor, reloj(), gen, quien))
                }
            }
        }
        miVuelo.complete(valor)
        return valor
    }

    private fun soltar(clave: String, vuelo: CompletableDeferred<Any?>) {
        if (enVuelo[clave]?.resultado === vuelo) enVuelo = enVuelo - clave
    }

    /** Para las pruebas: cuántas lecturas hay recordadas. */
    internal val cuantasFrescas: Int get() = frescas.size
}
