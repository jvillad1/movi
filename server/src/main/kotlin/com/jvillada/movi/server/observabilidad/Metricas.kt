package com.jvillada.movi.server.observabilidad

import java.util.concurrent.ConcurrentHashMap

/**
 * # Las métricas del servidor, en memoria (vuelta 102)
 *
 * Lo que se mide durante un minuto se junta acá y [VolcadoDeMetricas] lo guarda en la base cada
 * minuto. Nada de esto lleva datos de nadie: una ruta es su **plantilla** (`/api/events/{id}`), un
 * servicio es su nombre y su modelo, y los usuarios distintos se cuentan con un número que sale del id
 * y **no se guarda**: a la base llega la cuenta, nunca quiénes.
 *
 * Todo es concurrente: muchos pedidos terminan a la vez. Cada agregado se cuida con su propio
 * `synchronized` (sin contención casi nunca: dos pedidos de la misma ruta en el mismo milisegundo).
 */
object Metricas {

    /** El reloj, cambiable desde una prueba. Milisegundos desde la época. */
    @Volatile
    var reloj: () -> Long = System::currentTimeMillis

    const val UN_MINUTO: Long = 60_000L
    const val UNA_HORA: Long = 3_600_000L

    fun minutoDe(ms: Long): Long = ms - Math.floorMod(ms, UN_MINUTO)
    fun horaDe(ms: Long): Long = ms - Math.floorMod(ms, UNA_HORA)

    // ── Rutas ────────────────────────────────────────────────────────────────

    data class ClaveDeRuta(val minuto: Long, val metodo: String, val ruta: String)

    class AgregadoDeRuta {
        var total: Long = 0
        var errores4xx: Long = 0
        var errores5xx: Long = 0
        var maximoMs: Long = 0
        val cubetas: LongArray = LongArray(Cubetas.CUANTAS)

        @Synchronized
        fun sumar(status: Int, ms: Long) {
            total++
            if (status in 400..499) errores4xx++
            if (status >= 500) errores5xx++
            if (ms > maximoMs) maximoMs = ms
            cubetas[Cubetas.indiceDe(ms)]++
        }

        @Synchronized
        fun copia(): AgregadoDeRuta = AgregadoDeRuta().also {
            it.total = total; it.errores4xx = errores4xx; it.errores5xx = errores5xx; it.maximoMs = maximoMs
            cubetas.copyInto(it.cubetas)
        }
    }

    private val rutas = ConcurrentHashMap<ClaveDeRuta, AgregadoDeRuta>()

    /**
     * Un pedido terminado. [usuario] es el id del autenticado (o `null`): solo se usa para contar
     * usuarios distintos del minuto, y de él queda un `hashCode`, no el id.
     */
    fun registrarPedido(metodo: String, ruta: String, status: Int, duracionMs: Long, usuario: String?, usoLaIa: Boolean = false) {
        val ahora = reloj()
        val minuto = minutoDe(ahora)
        rutas.computeIfAbsent(ClaveDeRuta(minuto, metodo, ruta)) { AgregadoDeRuta() }.sumar(status, duracionMs)
        if (usuario != null) {
            actividad(minuto).sumarUsuario(usuario.hashCode())
            if (usoLaIa) usuariosDeIa.computeIfAbsent(diaDe(ahora)) { ConcurrentHashMap.newKeySet() }.add(usuario.hashCode())
        }
    }

    // ── Servicios de afuera ──────────────────────────────────────────────────

    data class ClaveDeServicio(val minuto: Long, val servicio: String, val operacion: String)

    class AgregadoDeServicio {
        var llamadas: Long = 0
        var sinCredito: Long = 0
        var limite: Long = 0
        var servidor: Long = 0
        var otros: Long = 0
        var fichasEntrada: Long = 0
        var fichasSalida: Long = 0
        var fichasCache: Long = 0
        var maximoMs: Long = 0
        val cubetas: LongArray = LongArray(Cubetas.CUANTAS)

        val errores: Long get() = sinCredito + limite + servidor + otros

        @Synchronized
        fun sumar(ms: Long, error: TipoDeError?, fichas: Fichas?) {
            llamadas++
            when (error) {
                null -> Unit
                TipoDeError.SIN_CREDITO -> sinCredito++
                TipoDeError.LIMITE -> limite++
                TipoDeError.SERVIDOR -> servidor++
                TipoDeError.CLIENTE, TipoDeError.RED -> otros++
            }
            if (fichas != null) {
                fichasEntrada += fichas.entrada
                fichasSalida += fichas.salida
                fichasCache += fichas.cache
            }
            if (ms > maximoMs) maximoMs = ms
            cubetas[Cubetas.indiceDe(ms)]++
        }

        @Synchronized
        fun copia(): AgregadoDeServicio = AgregadoDeServicio().also {
            it.llamadas = llamadas; it.sinCredito = sinCredito; it.limite = limite; it.servidor = servidor
            it.otros = otros; it.fichasEntrada = fichasEntrada; it.fichasSalida = fichasSalida
            it.fichasCache = fichasCache; it.maximoMs = maximoMs
            cubetas.copyInto(it.cubetas)
        }
    }

    private val servicios = ConcurrentHashMap<ClaveDeServicio, AgregadoDeServicio>()

    fun registrarServicio(servicio: String, operacion: String, duracionMs: Long, error: TipoDeError?, fichas: Fichas?) {
        val minuto = minutoDe(reloj())
        servicios.computeIfAbsent(ClaveDeServicio(minuto, servicio, operacion)) { AgregadoDeServicio() }
            .sumar(duracionMs, error, fichas)
    }

    // ── Actividad: usuarios distintos, consultas lentas, pool ────────────────

    class ActividadDelMinuto {
        private val usuarios: MutableSet<Int> = ConcurrentHashMap.newKeySet()
        var consultasLentas: Long = 0
            private set
        var poolActivasMax: Int = 0
            private set
        var poolEsperandoMax: Int = 0
            private set

        fun sumarUsuario(huella: Int) { usuarios.add(huella) }
        val usuariosDistintos: Int get() = usuarios.size

        @Synchronized fun sumarConsultaLenta() { consultasLentas++ }

        @Synchronized
        fun verPool(activas: Int, esperando: Int) {
            if (activas > poolActivasMax) poolActivasMax = activas
            if (esperando > poolEsperandoMax) poolEsperandoMax = esperando
        }
    }

    private val actividades = ConcurrentHashMap<Long, ActividadDelMinuto>()

    private fun actividad(minuto: Long): ActividadDelMinuto = actividades.computeIfAbsent(minuto) { ActividadDelMinuto() }

    fun registrarConsultaLenta() = actividad(minutoDe(reloj())).sumarConsultaLenta()

    fun registrarPool(activas: Int, esperando: Int) = actividad(minutoDe(reloj())).verPool(activas, esperando)

    // ── Usuarios de la IA por día ────────────────────────────────────────────

    /** Día civil de la app (`yyyy-MM-dd`), en la zona de `AppClock`. */
    fun diaDe(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(com.jvillada.movi.server.time.AppClock.zone).toLocalDate().toString()

    private val usuariosDeIa = ConcurrentHashMap<String, MutableSet<Int>>()

    /** Cuántos usuarios distintos usaron la IA en [dia], desde que arrancó este proceso. */
    fun usuariosDeIaEn(dia: String): Int = usuariosDeIa[dia]?.size ?: 0

    // ── Lo que se vuelca ─────────────────────────────────────────────────────

    /** Una foto de lo cerrado: copias, para que el volcado no lea mientras alguien suma. */
    data class LoCerrado(
        val rutas: Map<ClaveDeRuta, AgregadoDeRuta>,
        val servicios: Map<ClaveDeServicio, AgregadoDeServicio>,
        val actividad: Map<Long, ActividadCerrada>,
    ) {
        val minutos: Set<Long> get() = rutas.keys.map { it.minuto }.toSet() + servicios.keys.map { it.minuto } + actividad.keys
    }

    data class ActividadCerrada(val usuarios: Int, val consultasLentas: Long, val poolActivasMax: Int, val poolEsperandoMax: Int)

    /** Lo de los minutos anteriores a [antesDe]: ya no cambia, porque un pedido se anota al terminar. */
    fun cerrado(antesDe: Long): LoCerrado = LoCerrado(
        rutas = rutas.filterKeys { it.minuto < antesDe }.mapValues { it.value.copia() },
        servicios = servicios.filterKeys { it.minuto < antesDe }.mapValues { it.value.copia() },
        actividad = actividades.filterKeys { it < antesDe }.mapValues { (_, a) ->
            ActividadCerrada(a.usuariosDistintos, a.consultasLentas, a.poolActivasMax, a.poolEsperandoMax)
        },
    )

    /** Después de guardarlo: se suelta lo de los minutos ya escritos y los días de IA viejos. */
    fun olvidar(antesDe: Long) {
        rutas.keys.removeIf { it.minuto < antesDe }
        servicios.keys.removeIf { it.minuto < antesDe }
        actividades.keys.removeIf { it < antesDe }
        // Los usuarios de la IA se recuerdan por día: se guarda hoy y ayer (el volcado de las 00:00
        // todavía cierra el día anterior), lo demás ya está en la base.
        val hoy = diaDe(reloj())
        val ayer = diaDe(reloj() - 86_400_000L)
        usuariosDeIa.keys.removeIf { it != hoy && it != ayer }
    }

    /** Solo pruebas: arranca de cero. */
    fun reiniciar() {
        rutas.clear(); servicios.clear(); actividades.clear(); usuariosDeIa.clear()
        reloj = System::currentTimeMillis
    }
}

/** Qué clase de error devolvió un servicio de afuera. Sin texto: solo la clase. */
enum class TipoDeError {
    /** Anthropic sin saldo (400 «credit balance» o 402). */
    SIN_CREDITO,
    /** 429 (o el 529 «saturada» de Anthropic). */
    LIMITE,
    /** Un 5xx del otro lado. */
    SERVIDOR,
    /** Otro 4xx: el pedido, la clave o un permiso. */
    CLIENTE,
    /** No hubo respuesta: timeout, conexión caída, DNS. */
    RED,
}

/** Las fichas de una llamada a un modelo. */
data class Fichas(val entrada: Long, val salida: Long, val cache: Long = 0)
