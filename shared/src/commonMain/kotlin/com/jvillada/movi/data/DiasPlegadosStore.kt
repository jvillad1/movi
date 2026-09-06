package com.jvillada.movi.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **Los días que el dueño plegó en Movimientos**, por fecha ISO, recordados entre visitas.
 *
 * ### Por qué se recuerdan y no viven solo en la composición
 *
 * El dueño usa Movimientos a diario en la web, y la web **arranca de cero en cada visita**: un
 * F5, una pestaña nueva o el despliegue de la tarde vuelven a montar la app, y con ella se iría
 * cualquier `remember`. Plegar un día es una decisión sobre **ese día** —«el 28 ya lo revisé, no
 * quiero sus veinte renglones cada vez»—, no sobre la visita de hoy; si hubiera que volver a
 * plegarlo en cada apertura, el pliegue sería un gesto que se repite y no una memoria. Por eso la
 * clave es la fecha: mañana «Hoy» es otra fecha y nace desplegado, mientras el 28 sigue plegado
 * hasta que el dueño lo abra.
 *
 * ### Lo que no puede pasar
 *
 * Nada de esto puede tumbar el arranque, por la lección de `LastAccountStore`: `Settings()`
 * explota **al construirse** en una JVM sin contexto o en un navegador con el almacenamiento
 * bloqueado, así que la construcción queda diferida (`by lazy`) y siempre adentro de un
 * `runCatching`. Si no hay dónde guardar, esto sigue funcionando en memoria durante la sesión y
 * se olvida al cerrar — que es exactamente lo que hacía antes de existir.
 *
 * El conjunto se acota a [MAX_DIAS] fechas, las más recientes: una clave que solo crece es una
 * fuga lenta, y un día de hace dos años plegado no le sirve a nadie.
 */
private const val KEY_DIAS_PLEGADOS = "movimientos_dias_plegados"
private const val MAX_DIAS = 400

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val diasSettings: Settings by lazy { Settings() }

object DiasPlegadosStore {
    private var memoria: Set<String> = leer()

    /** Las fechas ISO plegadas hoy. */
    fun plegados(): Set<String> = memoria

    /** Pliega [dia] si estaba desplegado y al revés. Devuelve el conjunto nuevo. */
    fun alternar(dia: String): Set<String> {
        val nuevo = if (dia in memoria) memoria - dia else acotarConservando(memoria + dia, dia)
        memoria = nuevo
        guardar(nuevo)
        return nuevo
    }

    /** Al cerrar sesión: son los días del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        memoria = emptySet()
        guardar(emptySet())
    }

    /**
     * Se queda con las [MAX_DIAS] fechas más recientes, pero garantiza que [conservar] quede
     * adentro aunque sea la más vieja de todas: es el día que el dueño acaba de plegar, y un tap
     * que no se nota (`acotar` simple lo descartaría de inmediato si ya había 400 fechas más
     * recientes que él) es peor que perder el pliegue de otra fecha vieja.
     */
    private fun acotarConservando(dias: Set<String>, conservar: String): Set<String> {
        if (dias.size <= MAX_DIAS) return dias
        val recientes = dias.sortedDescending().take(MAX_DIAS)
        return if (conservar in recientes) recientes.toSet() else (recientes.dropLast(1) + conservar).toSet()
    }

    private fun leer(): Set<String> = runCatching {
        diasSettings.getStringOrNull(KEY_DIAS_PLEGADOS)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
    }.getOrNull() ?: emptySet()

    private fun guardar(dias: Set<String>) {
        runCatching {
            if (dias.isEmpty()) diasSettings.remove(KEY_DIAS_PLEGADOS)
            else diasSettings[KEY_DIAS_PLEGADOS] = dias.joinToString(",")
        }
    }
}
