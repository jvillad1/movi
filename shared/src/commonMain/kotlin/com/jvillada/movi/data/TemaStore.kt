package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **Claro u oscuro, elegido por el dueño y recordado en este aparato.**
 *
 * El tema claro existe desde que existe `Tokens.kt` —son los mismos roles con otros valores— pero
 * hasta acá no había forma de encenderlo sin tocar código, y eso no cuenta: la app la van a usar
 * personas que no tienen a Claude al lado.
 *
 * ### Por qué se guarda en el aparato y no en el servidor
 *
 * Porque no es una propiedad del dueño, es una del lugar donde está mirando. El mismo día quiere
 * oscuro en el teléfono de noche y claro en el navegador del escritorio con la ventana al lado. Un
 * ajuste sincronizado le impondría a cada pantalla la decisión de la otra.
 *
 * ### Y por qué arranca en oscuro
 *
 * Porque es el único tema que Movi tuvo siempre. Cambiarle el aspecto a alguien que nunca pidió
 * nada no es un default, es una sorpresa. Lulo Bank es oscuro por decisión y lo defiende por
 * descanso visual; Bancolombia sumó el claro en 2025 **con elección explícita**, encuadrado como
 * accesibilidad. Las dos cosas caben: el oscuro se queda de default y el claro está a un toque.
 *
 * ### Lo que no puede pasar
 *
 * Nada de esto puede tumbar el arranque, por la lección de `LastAccountStore`: `Settings()`
 * explota **al construirse** en una JVM sin contexto o en un navegador con el almacenamiento
 * bloqueado. Por eso la construcción va diferida (`by lazy`) y toda lectura y escritura adentro de
 * un `runCatching`. Sin dónde guardar, el interruptor sigue andando durante la sesión y se olvida
 * al cerrar — que es infinitamente mejor que una pantalla en blanco.
 *
 * Este es el aparato entero, no el usuario: **no se limpia al cerrar sesión**, a diferencia de
 * [DiasPlegadosStore] o de la caché del Inicio. Que cerrar sesión te devuelva el tema que no
 * elegiste sería un defecto, no una limpieza.
 */
private const val KEY_TEMA_OSCURO = "tema_oscuro"

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val temaSettings: Settings by lazy { Settings() }

object TemaStore {
    /**
     * `mutableStateOf` y no un campo común: lo lee `App()` para elegir la paleta, así que cambiarlo
     * tiene que recomponer la app entera. Es el único estado de la app que hace eso a propósito.
     */
    var oscuro: Boolean by mutableStateOf(leer())
        private set

    fun poner(valor: Boolean) {
        oscuro = valor
        runCatching { temaSettings[KEY_TEMA_OSCURO] = valor }
    }

    fun alternar() = poner(!oscuro)

    private fun leer(): Boolean =
        runCatching { temaSettings.getBooleanOrNull(KEY_TEMA_OSCURO) }.getOrNull() ?: true

    /**
     * Solo para las pruebas: vuelve al default sin escribir nada.
     *
     * `AppDePrueba` deja los `object` en cero antes de CADA método (ver el KDoc de
     * `HojaAgregarGeometriaTest`), y este también tiene que llegar limpio: una prueba que corriera
     * después de otra que puso el tema claro mediría otra paleta.
     */
    fun clear() {
        oscuro = true
    }
}
