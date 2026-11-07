package com.jvillada.movi.ui.analisis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.RangoDelAnalisis
import com.jvillada.movi.ui.Screen

/**
 * # Cuánto tiempo mira «En qué se va», en la sesión (vuelta 140, issue #696)
 *
 * El selector «Este período · 3 meses · 12 meses» se recuerda **en la sesión**: tocar una fila abre
 * Movimientos, y al volver la pantalla sigue en «3 meses». No va en `rememberSaveable`: la pantalla se
 * guarda con su clave (`Screen.EnQueSeVa()`) durante toda la sesión, y entonces el «Ver todas» del
 * Inicio la abriría en el rango de la última vez.
 *
 * **Quién arranca en «Este período»** ([alAbrir], que llama la cáscara al apilar una pantalla): el
 * Inicio (su «En qué se va» habla del período en curso) y quien pide un período por nombre (Actividad
 * mirando un período). Desde otro lado (Pagos) se respeta lo elegido. Volver a la pantalla no pasa por
 * acá: no se apila nada.
 *
 * Lo limpia `SessionManager.clear` (es una preferencia de quien se va).
 */
object RangoDeEnQueSeVa {
    var elegido: RangoDelAnalisis by mutableStateOf(RangoDelAnalisis.PERIODO)

    /** La cáscara apila [destino] estando en [origen]. Ver [rangoAlAbrir]. */
    fun alAbrir(destino: Screen, origen: Screen?) {
        elegido = rangoAlAbrir(destino, origen, elegido)
    }

    fun olvidar() {
        elegido = RangoDelAnalisis.PERIODO
    }
}

/**
 * **El rango con que se abre «En qué se va»** al apilar [destino] desde [origen]: «Este período» si se
 * abre desde el Inicio o pidiendo un período; si no, [recordado]. Cualquier otra pantalla no lo toca.
 */
fun rangoAlAbrir(destino: Screen, origen: Screen?, recordado: RangoDelAnalisis): RangoDelAnalisis = when {
    destino !is Screen.EnQueSeVa -> recordado
    origen == Screen.Dashboard || destino.periodo != null -> RangoDelAnalisis.PERIODO
    else -> recordado
}
