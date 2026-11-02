package com.jvillada.movi.observabilidad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.EventoDeUsoEnviado
import com.jvillada.movi.shared.model.LoteDeUso
import com.jvillada.movi.shared.model.MAX_EVENTOS_POR_LOTE
import com.jvillada.movi.shared.model.MINUTOS_PARA_SESION_NUEVA
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.problemaDelEvento
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val KEY_USO_ACTIVO = "uso_activo"
private val usoSettings: Settings by lazy { Settings() }

/** Lo que se guarda en memoria hasta que sube: cuántos eventos como mucho (se pierden los más viejos). */
internal const val MAX_PENDIENTES = 500

/** Uno de [SIN_REPETIR] igual a otro de hace menos de esto no se anota (un sync caído cada 30 s). */
internal const val VENTANA_SIN_REPETIR_MS = 60_000L

/** Los eventos que se repiten solos (un reintento cada 30 s) y se anotan una vez por minuto. */
internal val SIN_REPETIR = setOf(EventoDeUso.ESCRITURA_FALLIDA, EventoDeUso.ERROR_DE_RED, EventoDeUso.SINCRONIZACION)

/**
 * # `Uso`: el único punto de entrada del uso de la app (vuelta 100)
 *
 * Todo lo que se mide de cómo se usa Movi entra por [evento]: la navegación central de `App.kt`, la
 * guía de primer uso, Por revisar, Agregar, las lecturas de papeles, los errores de red. La vuelta
 * que agregue la autocaptura de toques en `ui/components/ds/` solo tiene que llamarlo desde ahí.
 *
 * ## Qué lleva cada evento
 *
 * `tipo` (PANTALLA / ACCION / SISTEMA, sale del nombre), `nombre` (lista cerrada [EventoDeUso]),
 * `ruta` (la pila de pantallas en ese momento, [ruta]), `sesionId` y `secuencia` (el orden dentro de
 * la sesión), los `datos` (claves de la lista cerrada [ClaveDeUso], cada una con su forma),
 * plataforma, versión y hora. **Nunca contenido**: un dato que no tiene la forma de su clave rechaza el
 * evento entero ([problemaDelEvento]), y el server lo vuelve a revisar.
 *
 * ## La sesión
 *
 * Una nueva en un arranque en frío (el primer evento del proceso) o cuando la app vuelve de segundo
 * plano tras más de [MINUTOS_PARA_SESION_NUEVA] minutos ([alVolverAPrimerPlano]); en la web, una por
 * pestaña (el proceso es la pestaña). Ir a segundo plano anota cuántos segundos estuvo a la vista.
 *
 * ## Cuándo sube
 *
 * Se junta en memoria (a lo sumo [MAX_PENDIENTES]) y sube en lotes a `POST /api/uso` ([enviar]): cada
 * minuto mientras la app está abierta, al irse a segundo plano y desde los Workers que anotan algo.
 * Sin sesión no sube nada (espera). Cerrar la sesión ([olvidar]) tira lo que no subió: no se le
 * atribuye a la persona siguiente.
 *
 * ## El interruptor
 *
 * Ajustes → Tus datos → «Mandar datos de uso para mejorar Movi», encendido por defecto, de este
 * aparato. Apagado, [evento] no anota nada y lo pendiente se tira. Los fallos no pasan por acá:
 * se mandan siempre, porque no llevan datos del usuario.
 *
 * Estado del proceso: [olvidar] lo limpia al cerrar la sesión (ver `ElForkLlegaLimpioTest`).
 */
@OptIn(ExperimentalAtomicApi::class)
object Uso {

    /** El estado entero, inmutable: se cambia con compare-and-set, así se puede anotar desde cualquier hilo. */
    private data class Estado(
        val pendientes: List<EventoDeUsoEnviado> = emptyList(),
        val sesionId: String = "",
        val secuencia: Int = 0,
        val aLaVistaDesde: Long? = null,
        val enSegundoPlanoDesde: Long? = null,
        val ultimos: Map<String, Long> = emptyMap(),
        val pila: List<String> = emptyList(),
        val detalleArriba: String? = null,
    )

    private val estado = AtomicReference(Estado())

    private inline fun cambiar(f: (Estado) -> Estado): Estado {
        while (true) {
            val antes = estado.load()
            val despues = f(antes)
            if (estado.compareAndSet(antes, despues)) return despues
        }
    }

    internal var reloj: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    /** Para las pruebas: qué hace [enviar] en vez de salir a la red. `true` = subió. */
    internal var enviadorDePrueba: (suspend (LoteDeUso) -> Boolean)? = null

    /** Para las pruebas: el interruptor sin tocar `Settings`. */
    internal var almacenDePrueba: MutableMap<String, Boolean>? = null

    private val cliente: HttpClient by lazy { createHttpClient() }

    // ── El interruptor ───────────────────────────────────────────────────────────────────────

    /** «Mandar datos de uso para mejorar Movi». Encendido por defecto. */
    var activo: Boolean by mutableStateOf(leerActivo())
        private set

    private fun leerActivo(): Boolean {
        almacenDePrueba?.let { return it[KEY_USO_ACTIVO] ?: true }
        return runCatching { usoSettings.getBooleanOrNull(KEY_USO_ACTIVO) }.getOrNull() ?: true
    }

    /** Lo prende o lo apaga en este aparato. Apagado, lo que no subió se tira. */
    fun poner(valor: Boolean) {
        activo = valor
        val almacen = almacenDePrueba
        if (almacen != null) almacen[KEY_USO_ACTIVO] = valor
        else runCatching { usoSettings[KEY_USO_ACTIVO] = valor }
        if (!valor) cambiar { it.copy(pendientes = emptyList()) }
    }

    /** Vuelve a leer el interruptor (lo usan las pruebas tras cambiar el almacén). */
    internal fun releerElInterruptor() { activo = leerActivo() }

    // ── La ruta ──────────────────────────────────────────────────────────────────────────────

    /** La pila de pantallas como texto («Dashboard>PorRevisar»), con el detalle de la de arriba si lo hay. */
    val ruta: String get() = rutaDe(estado.load())

    private fun rutaDe(e: Estado): String {
        val pila = e.pila.toMutableList()
        val detalle = e.detalleArriba
        if (pila.isNotEmpty() && detalle != null && '(' !in pila.last()) pila[pila.lastIndex] = "${pila.last()}($detalle)"
        return rutaRecortada(pila)
    }

    /** La pila de pantallas de `App.kt`, ya como tramos ([tramoDeRuta]). Lo llama la navegación central. */
    fun ponerLaPila(tramos: List<String>) { cambiar { it.copy(pila = tramos) } }

    /**
     * El detalle de la pantalla de arriba cuando no sale de la pila: la guía de primer uso dice
     * «paso 4». `null` lo quita. Tiene que tener la forma de un parámetro de ruta (`rutaBienFormada`).
     */
    fun ponerElDetalleDeArriba(detalle: String?) { cambiar { it.copy(detalleArriba = detalle) } }

    // ── Anotar ───────────────────────────────────────────────────────────────────────────────

    /**
     * **Anota un evento.** Devuelve `false` si no se anotó: el interruptor apagado, un dato fuera de la
     * lista o con otra forma, o uno de [SIN_REPETIR] igual a otro de hace menos de [VENTANA_SIN_REPETIR_MS].
     * Nunca lanza: medir no puede romper la pantalla que mide.
     */
    fun evento(nombre: EventoDeUso, datos: Map<ClaveDeUso, String> = emptyMap()): Boolean {
        if (!activo) return false
        val ahora = reloj()
        val crudos = datos.mapKeys { it.key.clave }
        asegurarLaSesion(ahora)
        if (problemaDelEvento(nombre.name, crudos, ruta) != null) return false
        val clave = nombre.name + crudos.entries.sortedBy { it.key }.joinToString()
        var anotado = false
        cambiar { e ->
            anotado = false
            if (nombre in SIN_REPETIR) {
                val antes = e.ultimos[clave]
                if (antes != null && ahora - antes < VENTANA_SIN_REPETIR_MS) return@cambiar e
            }
            anotado = true
            val secuencia = e.secuencia + 1
            val nuevo = EventoDeUsoEnviado(
                id = newId("uso"),
                nombre = nombre.name,
                propiedades = crudos,
                ruta = rutaDe(e),
                sesionId = e.sesionId,
                secuencia = secuencia,
                plataforma = plataformaDeUso,
                version = InfoDeLaApp.version,
                ocurridoEn = ahora,
            )
            e.copy(
                pendientes = (e.pendientes + nuevo).takeLast(MAX_PENDIENTES),
                secuencia = secuencia,
                ultimos = if (nombre in SIN_REPETIR) (e.ultimos + (clave to ahora)).filterValues { ahora - it < VENTANA_SIN_REPETIR_MS } else e.ultimos,
            )
        }
        return anotado
    }

    /** Un arranque en frío: el primer evento del proceso abre la sesión. */
    private fun asegurarLaSesion(ahora: Long) {
        if (estado.load().sesionId.isNotEmpty()) return
        empezarSesion(ahora, origen = "FRIO")
    }

    private fun empezarSesion(ahora: Long, origen: String) {
        var empezo = false
        cambiar { e ->
            empezo = e.sesionId.isEmpty() || origen == "VOLVIO"
            if (!empezo) e else e.copy(sesionId = newId("ses"), secuencia = 0, aLaVistaDesde = ahora, enSegundoPlanoDesde = null)
        }
        if (empezo) evento(EventoDeUso.SESION_EMPEZADA, mapOf(ClaveDeUso.ORIGEN to origen))
    }

    /** `ON_START` de la app: si estuvo afuera más de 30 min, sesión nueva; si no, sigue la misma. */
    fun alVolverAPrimerPlano(ahora: Long = reloj()) {
        if (!activo) return
        val e = estado.load()
        val afuera = e.enSegundoPlanoDesde
        when {
            e.sesionId.isEmpty() -> empezarSesion(ahora, "FRIO")
            afuera != null && ahora - afuera > MINUTOS_PARA_SESION_NUEVA * 60_000L -> empezarSesion(ahora, "VOLVIO")
            afuera != null -> {
                cambiar { it.copy(aLaVistaDesde = ahora, enSegundoPlanoDesde = null) }
                evento(EventoDeUso.PRIMER_PLANO)
            }
            else -> cambiar { it.copy(aLaVistaDesde = it.aLaVistaDesde ?: ahora) }
        }
    }

    /** `ON_STOP`: anota cuántos segundos estuvo a la vista este tramo. */
    fun alIrASegundoPlano(ahora: Long = reloj()) {
        if (!activo) return
        val desde = estado.load().aLaVistaDesde ?: ahora
        evento(EventoDeUso.SEGUNDO_PLANO, mapOf(ClaveDeUso.SEGUNDOS to ((ahora - desde).coerceAtLeast(0) / 1000).toString()))
        cambiar { it.copy(enSegundoPlanoDesde = ahora, aLaVistaDesde = null) }
    }

    /** El id de la sesión en curso (`""` si todavía no empezó). */
    val sesionId: String get() = estado.load().sesionId

    /** Lo que espera subir (para las pruebas y la pantalla de admin). */
    internal val pendientes: List<EventoDeUsoEnviado> get() = estado.load().pendientes

    // ── Subir ────────────────────────────────────────────────────────────────────────────────

    /**
     * **Sube lo pendiente** en lotes de [MAX_EVENTOS_POR_LOTE] a `POST /api/uso`. Sin sesión o con el
     * interruptor apagado no sale nada. Si un lote no sube, vuelve adelante de la cola (en orden) y
     * espera la próxima vuelta. Devuelve cuántos subieron.
     */
    suspend fun enviar(): Int {
        if (!activo || !SessionManager.isLoggedIn) return 0
        var subidos = 0
        while (true) {
            var lote: List<EventoDeUsoEnviado> = emptyList()
            cambiar { e ->
                lote = e.pendientes.take(MAX_EVENTOS_POR_LOTE)
                e.copy(pendientes = e.pendientes.drop(lote.size))
            }
            if (lote.isEmpty()) return subidos
            val subio = runCatching { mandar(LoteDeUso(lote)) }.getOrDefault(false)
            if (!subio) {
                cambiar { e -> e.copy(pendientes = (lote + e.pendientes).takeLast(MAX_PENDIENTES)) }
                return subidos
            }
            subidos += lote.size
        }
    }

    private suspend fun mandar(lote: LoteDeUso): Boolean {
        enviadorDePrueba?.let { return it(lote) }
        val respuesta = cliente.post("$apiBaseUrl/api/uso") {
            contentType(ContentType.Application.Json)
            setBody(lote)
        }
        // Un 4xx que no es de límite ni un 404 no se va a arreglar reintentando: se da por subido (se
        // pierde). Un 404 (un APK nuevo contra un server que todavía no tiene la ruta) o un 5xx esperan.
        val codigo = respuesta.status.value
        return respuesta.status.isSuccess() || (codigo in 400..499 && codigo != 429 && codigo != 404)
    }

    /** Cierra la sesión de la app: lo que no subió se tira (no es de quien entre después). La sesión de uso sigue. */
    fun olvidar() { cambiar { it.copy(pendientes = emptyList(), ultimos = emptyMap()) } }

    /** Para las pruebas: todo en cero, también la sesión de uso. */
    internal fun reiniciarParaPruebas() {
        estado.store(Estado())
        enviadorDePrueba = null
        almacenDePrueba = null
        reloj = { Clock.System.now().toEpochMilliseconds() }
        releerElInterruptor()
    }
}
