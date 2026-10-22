package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **El tema de Movi: igual que el teléfono, claro u oscuro, elegido por el dueño y recordado en
 * este aparato.**
 *
 * El tema claro existe desde que existe `Tokens.kt` —son los mismos roles con otros valores—. Hasta
 * la Ola 1 · «Que Movi se sienta viva» era un interruptor claro/oscuro a mano; ahora son tres
 * opciones ([PreferenciaDeTema]), y la primera, **«Igual que el teléfono»**, sigue al sistema
 * (`isSystemInDarkTheme`): es lo que esperan las apps de hoy, y lo que hacen Revolut y Monzo.
 *
 * ### Por qué se guarda en el aparato y no en el servidor
 *
 * Porque no es una propiedad del dueño, es una del lugar donde está mirando. El mismo día quiere
 * oscuro en el teléfono de noche y claro en el navegador del escritorio con la ventana al lado. Un
 * ajuste sincronizado le impondría a cada pantalla la decisión de la otra.
 *
 * ### El default, y lo que ya se eligió
 *
 * - **Una instalación nueva arranca en «Igual que el teléfono».**
 * - **Lo que el dueño ya eligió se respeta**: el interruptor viejo guardaba un booleano en
 *   `tema_oscuro`; si está y la clave nueva no, se lee como Claro u Oscuro (ver [preferenciaGuardada]).
 *   Quien tenía Movi en oscuro —el único tema que tuvo siempre— no se encuentra con otra cosa.
 *
 * ### Lo que no puede pasar
 *
 * Nada de esto puede tumbar el arranque, por la lección de `LastAccountStore`: `Settings()`
 * explota **al construirse** en una JVM sin contexto o en un navegador con el almacenamiento
 * bloqueado. Por eso la construcción va diferida (`by lazy`) y toda lectura y escritura adentro de
 * un `runCatching`. Sin dónde guardar, la elección sigue andando durante la sesión y se olvida
 * al cerrar — que es infinitamente mejor que una pantalla en blanco.
 *
 * Este es el aparato entero, no el usuario: **no se limpia al cerrar sesión**, a diferencia de
 * [DiasPlegadosStore] o de la caché del Inicio. Que cerrar sesión te devuelva el tema que no
 * elegiste sería un defecto, no una limpieza.
 *
 * La web lee la misma clave antes del primer cuadro (`webApp/.../index.html`), y el widget de
 * Android también ([oscuroCon]).
 */
private const val KEY_TEMA = "tema"

/** La clave del interruptor viejo (claro/oscuro): se lee para migrarla, ya no se escribe. */
private const val KEY_TEMA_OSCURO_VIEJO = "tema_oscuro"

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val temaSettings: Settings by lazy { Settings() }

/** Las tres opciones de «Cómo se ve». [guardado] es lo que va en el aparato: no se renombra. */
enum class PreferenciaDeTema(val guardado: String, val titulo: String) {
    SISTEMA("sistema", "Igual que el teléfono"),
    CLARO("claro", "Claro"),
    OSCURO("oscuro", "Oscuro"),
    ;

    /** ¿Se pinta oscuro? «Igual que el teléfono» le pregunta al sistema ([sistemaOscuro]). */
    fun esOscuro(sistemaOscuro: Boolean): Boolean = when (this) {
        SISTEMA -> sistemaOscuro
        CLARO -> false
        OSCURO -> true
    }
}

/**
 * **Qué tema quedó elegido**, a partir de lo guardado: la clave nueva ([nuevo]) gana; sin ella, el
 * interruptor viejo ([viejoOscuro]) se migra a Claro u Oscuro; sin nada, «Igual que el teléfono».
 * Un valor nuevo que no se entiende cuenta como no guardado. Pura, para probarla.
 */
fun preferenciaGuardada(nuevo: String?, viejoOscuro: Boolean?): PreferenciaDeTema =
    PreferenciaDeTema.entries.firstOrNull { it.guardado == nuevo }
        ?: when (viejoOscuro) {
            true -> PreferenciaDeTema.OSCURO
            false -> PreferenciaDeTema.CLARO
            null -> PreferenciaDeTema.SISTEMA
        }

object TemaStore {
    /**
     * `mutableStateOf` y no un campo común: lo lee `App()` para elegir la paleta, así que cambiarlo
     * tiene que recomponer la app entera. Es el único estado de la app que hace eso a propósito.
     */
    var preferencia: PreferenciaDeTema by mutableStateOf(leer())
        private set

    fun poner(valor: PreferenciaDeTema) {
        preferencia = valor
        runCatching { temaSettings[KEY_TEMA] = valor.guardado }
    }

    /**
     * ¿Se pinta oscuro, con el sistema en [sistemaOscuro]? Para quien no está en una composición
     * (el widget de Android) y tiene que preguntarle al sistema por su cuenta.
     */
    fun oscuroCon(sistemaOscuro: Boolean): Boolean = preferencia.esOscuro(sistemaOscuro)

    private fun leer(): PreferenciaDeTema = runCatching {
        preferenciaGuardada(
            nuevo = temaSettings.getStringOrNull(KEY_TEMA),
            viejoOscuro = temaSettings.getBooleanOrNull(KEY_TEMA_OSCURO_VIEJO),
        )
    }.getOrNull() ?: PreferenciaDeTema.SISTEMA

    /**
     * Solo para las pruebas: vuelve al default sin escribir nada.
     *
     * `AppDePrueba` deja los `object` en cero antes de CADA método (ver el KDoc de
     * `HojaAgregarGeometriaTest`), y este también tiene que llegar limpio: una prueba que corriera
     * después de otra que puso el tema claro mediría otra paleta.
     */
    fun clear() {
        preferencia = PreferenciaDeTema.SISTEMA
    }
}
