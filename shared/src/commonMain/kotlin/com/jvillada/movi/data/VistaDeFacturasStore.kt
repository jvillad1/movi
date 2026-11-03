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
 * **Los grupos de «Por categoría» que el dueño plegó o desplegó**, por su clave (`GrupoDeFacturas.clave`), en
 * la sesión (vuelta 120). Solo lo que tocó: lo demás sigue la regla de siempre —desplegado lo que tiene algo
 * pendiente, plegado lo ya pagado—. En memoria y no en el aparato: un grupo plegado hoy puede tener una
 * factura nueva el mes que viene. Se olvida al cerrar sesión.
 */
object GruposPlegadosDeFacturas {
    private val elegidos = mutableStateMapOf<String, Boolean>()

    /** ¿Está plegado el grupo [clave]? Lo que el dueño eligió en la sesión, o [porDefecto]. */
    fun plegado(clave: String, porDefecto: Boolean): Boolean = elegidos[clave] ?: porDefecto

    /** Pliega o despliega [clave], partiendo de lo que hoy se ve. */
    fun alternar(clave: String, porDefecto: Boolean) {
        elegidos[clave] = !plegado(clave, porDefecto)
    }

    /** Cuántos grupos tocó el dueño en la sesión (para las pruebas: ¿quedó algo después del logout?). */
    val cuantos: Int get() = elegidos.size

    fun olvidar() = elegidos.clear()
}
