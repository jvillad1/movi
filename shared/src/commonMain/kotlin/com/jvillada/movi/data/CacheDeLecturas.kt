package com.jvillada.movi.data

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.DetalleDePeriodo as DetalleDePeriodoLeido
import kotlin.concurrent.Volatile

/**
 * **Qué lectura se recuerda**, y de qué tipo es lo que vuelve.
 *
 * El parámetro de tipo es lo que hace imposible guardar una lista de cuentas bajo la clave de los
 * movimientos y leerla después como movimientos: [CacheDeLecturas.ultima] devuelve el `T` de la
 * clave, y el compilador no deja pasar otro.
 *
 * Una clave por **lectura del repositorio**, no por pantalla: `getEventsByDay` —la historia
 * entera— la piden Movimientos, Presupuestos y Por revisar, y con una sola clave es una lectura
 * recordada y no tres copias de lo mismo.
 */
sealed interface ClaveDeLectura<T : Any> {
    data object EventosPorDia : ClaveDeLectura<List<EventDay>>
    data object Cuentas : ClaveDeLectura<List<Account>>
    data object Creditos : ClaveDeLectura<List<CreditSummary>>
    data object Tarjetas : ClaveDeLectura<List<CardSummary>>
    data object Destinos : ClaveDeLectura<List<DestinoConocido>>
    data object Presupuestos : ClaveDeLectura<List<Budget>>
    data object Perfil : ClaveDeLectura<UserProfile>
    data object Periodos : ClaveDeLectura<List<ResumenDePeriodo>>
    data class DetalleDePeriodo(val id: String) : ClaveDeLectura<DetalleDePeriodoLeido>
    data object MensajesDelBanco : ClaveDeLectura<List<SmsMessage>>
    data object CandidatosPagoDeTarjeta : ClaveDeLectura<List<FinancialEvent>>
    data class ResumenDelTablero(val scope: Scope) : ClaveDeLectura<DashboardSummary>
}

/**
 * # Lo último que se leyó, para no volver a mostrar un esqueleto cada vez que se navega
 *
 * Solo la pantalla actual está compuesta, así que volver a Movimientos desde Hoy tiraba todo lo
 * que la pantalla había leído y la pintaba cargando desde cero — la historia entera, otra vez, con
 * sus filas esqueleto. Acá queda lo último que contestó cada lectura, para pintarlo al primer
 * cuadro **con «Actualizando…» encima** mientras la lectura nueva viaja (ver [rememberLectura]).
 * Es lo que ya hacían el Inicio y el disponible de Plan, llevado al resto.
 *
 * ## Qué NO se muestra nunca como «lo último»
 *
 * [ultima] devuelve `null` —y la pantalla hace lo de siempre, esqueleto incluido— cuando:
 *
 * - **es de otra persona**: la entrada lleva el id de quien la leyó y se compara con la sesión;
 * - **es vieja**: más de [EDAD_MAXIMA_PARA_MOSTRAR]. Pasado ese rato, pintar lo de antes aunque sea
 *   un instante se parece más a mentir que a recordar;
 * - **es de otro período**: las lecturas cuyas cifras dependen del período vigente se guardan con
 *   él, y si el período cambió desde entonces lo guardado describe el mes anterior;
 * - **una escritura propia la volvió vieja**: [borrarTodo] corre tras CUALQUIER escritura que pasa
 *   por `Repositories.wallets` (ver `InvalidaElInicioAlEscribir`). Anular un movimiento y volver a
 *   Movimientos no puede mostrar, ni un cuadro, el movimiento que se acaba de anular.
 *
 * ## Solo en memoria
 *
 * Nada de esto toca el aparato: muere con el proceso (o la pestaña). Guardar la historia de
 * movimientos en disco es otra conversación, con otras preguntas —cifrado, tamaño, qué pasa con
 * un teléfono prestado— que este mecanismo no necesita para cumplir lo que promete.
 *
 * ## Hilos
 *
 * Todas las escrituras de la app salen de la UI y todas las lecturas se guardan desde efectos de
 * Compose, o sea el hilo principal. Igual el mapa nunca se muta: se reemplaza la referencia por uno
 * nuevo e inmutable (como `sessionMemoria` en `SessionManager.kt`), así que quien lea desde otro
 * hilo ve una foto entera y nunca un mapa a medio redimensionar.
 */
object CacheDeLecturas {

    /**
     * Más vieja que esto, no se muestra. Media hora cubre lo que el pedido describe —ir y volver
     * entre pantallas— sin que un teléfono que quedó abierto desde el almuerzo pinte la mañana.
     */
    const val EDAD_MAXIMA_PARA_MOSTRAR: Long = 30 * 60 * 1000L

    private class Entrada(
        val valor: Any,
        val cargadoEn: Long,
        val usuario: String,
        val periodo: String?,
    )

    @Volatile
    private var entradas: Map<ClaveDeLectura<*>, Entrada> = emptyMap()

    /**
     * Sube con cada [borrarTodo]. Una lectura anota el número al SALIR y lo entrega al volver
     * (ver [guardar]): si en el medio hubo una escritura, lo que trae pudo haberse leído antes de
     * ella y no se guarda. Sin esto, anular un movimiento mientras la historia viajaba dejaba en el
     * cache la historia con el movimiento vivo, y la visita siguiente lo pintaba como «lo último».
     */
    @Volatile
    var generacion: Int = 0
        private set

    /**
     * Lo último que contestó [clave] para la sesión abierta, o `null` si no hay nada que se pueda
     * mostrar (ver el KDoc del objeto para los cuatro motivos).
     *
     * @param ahora epoch ms; se pasa para que las pruebas no dependan del reloj.
     * @param periodoVigente el id del período que la pantalla está por mostrar, para las lecturas
     *   que dependen de él. Si se pasa, lo guardado tiene que ser de ese mismo período.
     */
    fun <T : Any> ultima(clave: ClaveDeLectura<T>, ahora: Long, periodoVigente: String? = null): T? {
        val entrada = entradas[clave] ?: return null
        val usuario = SessionManager.userId ?: return null
        if (entrada.usuario != usuario) return null
        val edad = ahora - entrada.cargadoEn
        // Una edad negativa es un reloj que se movió para atrás: no se sabe cuán vieja es.
        if (edad < 0 || edad > EDAD_MAXIMA_PARA_MOSTRAR) return null
        if (periodoVigente != null && entrada.periodo != periodoVigente) return null
        @Suppress("UNCHECKED_CAST") // la clave fija el tipo: solo [guardar] escribe, y con el mismo T
        return entrada.valor as T
    }

    /**
     * Recuerda lo que acaba de contestar [clave].
     *
     * No guarda nada si la sesión ya no es la de [usuario] —una lectura que vuelve tarde, después
     * de cerrar sesión o de entrar con otra cuenta, no puede dejarle al siguiente la plata del
     * anterior—, si no hay sesión, o si desde que salió la lectura hubo una escritura
     * ([generacionAlLeer], ver [generacion]).
     */
    fun <T : Any> guardar(
        clave: ClaveDeLectura<T>,
        valor: T,
        usuario: String?,
        ahora: Long,
        periodo: String? = null,
        generacionAlLeer: Int = generacion,
    ) {
        if (usuario == null || SessionManager.userId != usuario) return
        if (generacionAlLeer != generacion) return
        entradas = entradas + (clave to Entrada(valor, ahora, usuario, periodo))
    }

    /**
     * Olvida todo. La llaman toda escritura propia (`InvalidaElInicioAlEscribir`) y el cierre de
     * sesión (`SessionManager.clear`). Después de esto cada pantalla vuelve a su esqueleto de
     * siempre: lo que había se volvió viejo, y lo viejo no se pinta como si fuera lo último.
     */
    fun borrarTodo() {
        generacion++
        entradas = emptyMap()
    }

    /** Para `ElForkLlegaLimpioTest`: cuántas lecturas hay recordadas, sin importar de quién. */
    internal val cuantas: Int get() = entradas.size
}
