package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **«Ocultar los saldos»: las cifras de plata se pintan como «$•••••»**, recordado en este aparato.
 *
 * Sirve para lo de todos los días: mostrarle el teléfono a otra persona, abrir Movi en el bus,
 * compartir la pantalla en una llamada. Se alterna con el ojo del héroe de Hoy (junto a «Te
 * quedan») y en Ajustes → Perfil → «Cómo se ve».
 *
 * ### Dónde se oculta
 *
 * **En el punto donde se pinta, no en la lógica**: las funciones de formato de la UI
 * (`formatCOP`, `formatMoney`, `formatMoneyCompact`, `signedMoney` en `ui/components`) leen
 * [ocultos] y devuelven `PLATA_OCULTA`; lo que llega ya armado de `:core` o del server pasa por
 * `conCifrasOcultas` donde se pinta. Lo que no se pinta —un texto que viaja al server, la pregunta
 * que se le manda a Movi AI, una notificación— usa las variantes `…SinOcultar`, que son las de
 * siempre. Al mostrar, todo vuelve exactamente como estaba.
 *
 * ### Por qué en el aparato
 *
 * Porque es del lugar donde se está mirando (como lo era el tema, hasta que la vuelta 29 dejó uno
 * solo). Ocultar en el
 * teléfono no tiene por qué ocultar en el escritorio.
 *
 * ### Por qué sí se limpia al cerrar sesión
 *
 * El brief lo pide así y tiene sentido: es una preferencia que acompaña a la plata de quien la
 * puso. El siguiente que entre en este aparato arranca viendo sus cifras, como en una instalación
 * nueva. Lo hace `SessionManager.clear()`, y lo vigila `ElForkLlegaLimpioTest`.
 *
 * ### Lo que no puede pasar
 *
 * Lo mismo que en `LastAccountStore`: `Settings()` explota al construirse en una JVM sin contexto o en
 * un navegador con el almacenamiento bloqueado. Construcción diferida y todo en `runCatching`:
 * sin dónde guardar, el ojo sigue andando durante la sesión.
 */
private const val KEY_SALDOS_OCULTOS = "saldos_ocultos"

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val saldosSettings: Settings by lazy { Settings() }

object SaldosOcultosStore {
    /**
     * `mutableStateOf`: lo leen las funciones de formato mientras se pinta, así que cambiarlo
     * recompone cada cifra a la vista. Desde afuera de una composición (el widget, un Worker) se
     * lee como cualquier campo.
     */
    var ocultos: Boolean by mutableStateOf(leerGuardado())
        private set

    /**
     * Quién se entera cuando cambia, fuera de Compose: el widget de Android, que pinta la misma
     * cifra en la pantalla de inicio. Lo conecta `MoviAplicacion`, igual que
     * `InstantaneaDelInicio.alCambiar`. En la web y en iOS queda en `null`.
     */
    var alCambiar: (() -> Unit)? = null

    /**
     * La costura de las pruebas, como `FormaRecordada.sustitutoDePrueba`: en Robolectric
     * `Settings()` no guarda nada, así que «se recuerda» solo se puede probar contra un almacén de
     * mentira. `AppDePrueba` la vuelve a `null` antes de cada método.
     */
    internal var almacenDePrueba: MutableMap<String, Boolean>? = null

    fun poner(valor: Boolean) {
        val cambio = valor != ocultos
        ocultos = valor
        guardar(valor)
        if (cambio) runCatching { alCambiar?.invoke() }
    }

    fun alternar() = poner(!ocultos)

    /** Lo guardado en el aparato, o `false` si no hay nada (o no hay dónde leerlo). */
    internal fun leerGuardado(): Boolean {
        almacenDePrueba?.let { return it[KEY_SALDOS_OCULTOS] ?: false }
        return runCatching { saldosSettings.getBooleanOrNull(KEY_SALDOS_OCULTOS) }.getOrNull() ?: false
    }

    /** `true` se guarda; `false` borra la clave (es el default, no hace falta escribirlo). */
    private fun guardar(valor: Boolean) {
        val almacen = almacenDePrueba
        if (almacen != null) {
            if (valor) almacen[KEY_SALDOS_OCULTOS] = true else almacen.remove(KEY_SALDOS_OCULTOS)
            return
        }
        runCatching {
            if (valor) saldosSettings[KEY_SALDOS_OCULTOS] = true else saldosSettings.remove(KEY_SALDOS_OCULTOS)
        }
    }

    /**
     * Vuelve a mostrar las cifras y lo olvida del aparato. Lo llama `SessionManager.clear()` (la
     * preferencia era de quien se fue) y, por eso mismo, `AppDePrueba` antes de cada prueba.
     */
    fun clear() {
        val cambio = ocultos
        ocultos = false
        guardar(false)
        if (cambio) runCatching { alCambiar?.invoke() }
    }
}
