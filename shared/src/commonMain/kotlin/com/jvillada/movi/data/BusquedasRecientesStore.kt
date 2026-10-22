package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **Las últimas búsquedas de «Buscar»** (Ola 5): las [MAX_RECIENTES] más recientes, la última
 * primero, recordadas en este aparato.
 *
 * Por aparato y no en el server por lo mismo que [DiasPlegadosStore]: es del lugar donde se busca. Y
 * **se limpia al cerrar sesión** (`SessionManager.clear()`, vigilado por `ElForkLlegaLimpioTest`):
 * «Caro», «colegio» o «$27.500» dicen cosas de la plata de quien buscó, y el siguiente que entre no
 * tiene por qué verlas.
 *
 * Lo que no puede pasar es lo de siempre con `Settings()`: que explote al construirse (una JVM sin
 * contexto, un navegador con el almacenamiento bloqueado). Diferido y en `runCatching`: sin dónde
 * guardar, las recientes viven solo durante la sesión.
 */
private const val KEY_BUSQUEDAS_RECIENTES = "busquedas_recientes"

/** Cuántas se recuerdan. */
const val MAX_RECIENTES: Int = 8

/** Separa una búsqueda de otra en el texto guardado: un carácter que nadie teclea. */
private const val SEPARADOR = '\u001F'

private val recientesSettings: Settings by lazy { Settings() }

object BusquedasRecientesStore {
    /** `mutableStateOf`: la pantalla de «Buscar» las pinta y se repinta al cambiar. */
    var recientes: List<String> by mutableStateOf(leer())
        private set

    /**
     * La costura de las pruebas, como en [SaldosOcultosStore]: en Robolectric `Settings()` no guarda
     * nada entre lecturas. `AppDePrueba` la vuelve a `null` antes de cada método.
     */
    internal var almacenDePrueba: MutableMap<String, String>? = null

    /**
     * Anota [consulta] como la más reciente. En blanco no se anota; si ya estaba (sin importar
     * mayúsculas ni espacios de más), sube al primer lugar en vez de repetirse.
     */
    fun anotar(consulta: String) {
        val limpia = consulta.trim().replace(Regex("\\s+"), " ").replace(SEPARADOR, ' ')
        if (limpia.isEmpty()) return
        val nuevas = (listOf(limpia) + recientes.filterNot { it.equals(limpia, ignoreCase = true) }).take(MAX_RECIENTES)
        recientes = nuevas
        guardar(nuevas)
    }

    /** Quita una («×» junto a la reciente). */
    fun quitar(consulta: String) {
        val nuevas = recientes.filterNot { it == consulta }
        recientes = nuevas
        guardar(nuevas)
    }

    /** Al cerrar sesión: son las búsquedas de quien se va. */
    fun clear() {
        recientes = emptyList()
        guardar(emptyList())
    }

    /** Lo guardado en el aparato (para las pruebas: ¿quedó algo después de `clear`?). */
    internal fun leer(): List<String> = runCatching {
        val crudo = almacenDePrueba?.get(KEY_BUSQUEDAS_RECIENTES)
            ?: if (almacenDePrueba != null) null else recientesSettings.getStringOrNull(KEY_BUSQUEDAS_RECIENTES)
        crudo?.split(SEPARADOR)?.filter { it.isNotBlank() }?.take(MAX_RECIENTES)
    }.getOrNull() ?: emptyList()

    private fun guardar(lista: List<String>) {
        val prueba = almacenDePrueba
        if (prueba != null) {
            if (lista.isEmpty()) prueba.remove(KEY_BUSQUEDAS_RECIENTES) else prueba[KEY_BUSQUEDAS_RECIENTES] = lista.joinToString(SEPARADOR.toString())
            return
        }
        runCatching {
            if (lista.isEmpty()) recientesSettings.remove(KEY_BUSQUEDAS_RECIENTES)
            else recientesSettings[KEY_BUSQUEDAS_RECIENTES] = lista.joinToString(SEPARADOR.toString())
        }
    }
}
