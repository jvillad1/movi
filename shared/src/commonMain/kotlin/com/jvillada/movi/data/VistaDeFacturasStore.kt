package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **Cómo ve el dueño sus facturas en Pagos** (vuelta 120): «Por fecha» (la lista de siempre) o «Por
 * categoría». Se recuerda la última que eligió, **en este aparato** —es una preferencia de cómo mirar, como
 * [DiasPlegadosStore]—, y por defecto es [VistaDeFacturas.POR_FECHA].
 *
 * Se lee **al construirse**, sin esperar a nadie: la vista elegida tiene que estar puesta en el primer cuadro,
 * o la lista se pintaría por fecha y saltaría a «por categoría» al leer la preferencia.
 *
 * Se limpia al cerrar sesión (`SessionManager.clear()`, vigilado por `ElForkLlegaLimpioTest`): el siguiente
 * que entre arranca como en una instalación nueva. Con `Settings()` lo de siempre: diferido y en
 * `runCatching` (ver [BusquedasRecientesStore]); sin dónde guardar, se recuerda solo durante la sesión.
 */
private const val KEY_VISTA_DE_FACTURAS = "pagos_vista_de_facturas"

/** Las dos vistas de «Tus facturas del período». */
enum class VistaDeFacturas(val guardada: String) {
    POR_FECHA("fecha"),
    POR_CATEGORIA("categoria"),
}

private val vistaSettings: Settings by lazy { Settings() }

object VistaDeFacturasStore {
    /** `mutableStateOf`: la sección la lee y se repinta al cambiar. */
    var vista: VistaDeFacturas by mutableStateOf(leer())
        private set

    /** La costura de las pruebas, como en [BusquedasRecientesStore]. `AppDePrueba` la vuelve a `null`. */
    internal var almacenDePrueba: MutableMap<String, String>? = null
        set(valor) {
            field = valor
            vista = leer()
        }

    fun elegir(nueva: VistaDeFacturas) {
        vista = nueva
        guardar(nueva)
    }

    /** Al cerrar sesión: vuelve a «Por fecha» y no deja nada guardado. */
    fun clear() {
        vista = VistaDeFacturas.POR_FECHA
        guardar(null)
    }

    /** Lo guardado en el aparato (para las pruebas). */
    internal fun leer(): VistaDeFacturas = runCatching {
        val crudo = almacenDePrueba?.get(KEY_VISTA_DE_FACTURAS)
            ?: if (almacenDePrueba != null) null else vistaSettings.getStringOrNull(KEY_VISTA_DE_FACTURAS)
        VistaDeFacturas.entries.firstOrNull { it.guardada == crudo }
    }.getOrNull() ?: VistaDeFacturas.POR_FECHA

    private fun guardar(valor: VistaDeFacturas?) {
        val prueba = almacenDePrueba
        if (prueba != null) {
            if (valor == null) prueba.remove(KEY_VISTA_DE_FACTURAS) else prueba[KEY_VISTA_DE_FACTURAS] = valor.guardada
            return
        }
        runCatching {
            if (valor == null) vistaSettings.remove(KEY_VISTA_DE_FACTURAS) else vistaSettings[KEY_VISTA_DE_FACTURAS] = valor.guardada
        }
    }
}

/**
 * **Los grupos de «Por categoría», plegados o desplegados**, por período y grupo (`GrupoDeFacturas.clave`), en la
 * sesión (vuelta 120). En memoria y no en el aparato: un grupo plegado hoy puede tener una factura nueva el mes
 * que viene. Se olvida al cerrar sesión (`SessionManager.clear`).
 *
 * Dos capas, con la misma clave (período + grupo):
 * - **El defecto se fija la primera vez que el grupo se ve** ([plegado]): desplegado lo que tiene algo pendiente,
 *   plegado lo pagado. Fijo para el resto de la sesión: anotar el último pago de un grupo abierto no lo pliega
 *   bajo el dedo, ni alternar «Por fecha ↔ Por categoría» o ir a ver un movimiento lo recalcula. Otro período es
 *   otra clave: arranca con su propio defecto.
 * - **Lo que el dueño toca** ([alternar]) gana sobre el defecto.
 */
object GruposPlegadosDeFacturas {
    private val elegidos = mutableStateMapOf<String, Boolean>()

    /**
     * Los defectos fijados. Un mapa común —no de estado— porque se llena DURANTE la composición, y fijar un
     * defecto no cambia nada a la vista; [version] avisa cuando se vacía.
     */
    private val fijados = HashMap<String, Boolean>()
    private var version by mutableStateOf(0)

    private fun clave(periodo: String, grupo: String) = "$periodo|$grupo"

    /**
     * ¿Está plegado el grupo [grupo] del [periodo]? Lo que el dueño eligió en la sesión, o el defecto fijado la
     * primera vez que se vio (que es [porDefecto] si se ve ahora por primera vez).
     */
    fun plegado(periodo: String, grupo: String, porDefecto: Boolean): Boolean {
        version // se lee: vaciar los defectos repinta a quien pregunta
        val k = clave(periodo, grupo)
        return elegidos[k] ?: fijados.getOrPut(k) { porDefecto }
    }

    /** Pliega o despliega [grupo] del [periodo], partiendo de lo que hoy se ve. */
    fun alternar(periodo: String, grupo: String, porDefecto: Boolean) {
        elegidos[clave(periodo, grupo)] = !plegado(periodo, grupo, porDefecto)
    }

    /** Cuántos grupos tienen algo guardado en la sesión, fijado o elegido (para las pruebas del logout). */
    val cuantos: Int get() = (elegidos.keys + fijados.keys).size

    fun olvidar() {
        elegidos.clear()
        fijados.clear()
        version++
    }
}
