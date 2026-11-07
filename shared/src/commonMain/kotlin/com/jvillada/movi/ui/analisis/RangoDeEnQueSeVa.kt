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
 * **Abrirla siempre arranca en «Este período»** ([alAbrir], que llama la cáscara al apilar una
 * pantalla): todas sus entradas hablan del período —el Inicio y su «Ver todas», la alerta «Mira en qué
 * se fue ›» de Pagos (revisión de la vuelta 140: abría en el rango recordado), Actividad pidiendo un
 * período—. Lo que se recuerda en la sesión es la vuelta: ir a Movimientos desde una fila y volver no
 * apila nada, así que no pasa por acá y la pantalla sigue en «3 meses».
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
 * **El rango con que se abre «En qué se va»** al apilar [destino] desde [origen]: «Este período»,
 * venga de donde venga (ver el KDoc de [RangoDeEnQueSeVa]). Apilar cualquier otra pantalla no lo toca.
 * [origen] queda en la firma para quien quiera distinguir una entrada en el futuro.
 */
@Suppress("UNUSED_PARAMETER")
fun rangoAlAbrir(destino: Screen, origen: Screen?, recordado: RangoDelAnalisis): RangoDelAnalisis =
    if (destino is Screen.EnQueSeVa) RangoDelAnalisis.PERIODO else recordado
