package com.jvillada.movi.observabilidad

import com.jvillada.movi.shared.model.FormaDeDato
import com.jvillada.movi.shared.model.MAX_RUTA
import com.jvillada.movi.shared.model.rutaBienFormada
import com.jvillada.movi.ui.Screen

/**
 * **El tramo de la ruta de una pantalla** (vuelta 100): su nombre (el de la clase, solo letras) y, si
 * hace falta para entender el recorrido, su parámetro entre paréntesis — el id de la cuenta o del
 * aviso, el período («2026-09»). Nunca un texto del usuario: un parámetro que no tiene la forma de un
 * id o de un período no se pone.
 */
fun tramoDeRuta(pantalla: Screen): String {
    val nombre = (pantalla::class.simpleName ?: "Pantalla").filter { it.isLetter() }.ifEmpty { "Pantalla" }
    val parametro = when (pantalla) {
        is Screen.AccountDetail -> pantalla.accountId
        is Screen.SMSReconcile -> pantalla.smsId
        is Screen.Espacio -> pantalla.id
        is Screen.DetalleDePeriodo -> pantalla.id
        is Screen.PrimerUso -> pantalla.desde?.name
        else -> null
    }
    val conParametro = parametro?.let { "$nombre($it)" }
    return if (conParametro != null && rutaBienFormada(conParametro)) conParametro else nombre
}

/** La pila de pantallas de la app como tramos de ruta. */
fun tramosDeLaPila(pila: List<Screen>): List<String> = pila.map(::tramoDeRuta)

/**
 * La ruta unida con «>», recortada desde el principio para que entre en [MAX_RUTA] (lo de arriba de la
 * pila es lo que importa), y sin los tramos que no tengan la forma (nunca debería pasar: así un
 * nombre raro no tira el evento entero).
 */
internal fun rutaRecortada(tramos: List<String>): String {
    val buenos = tramos.filter { rutaBienFormada(it) }.toMutableList()
    while (buenos.isNotEmpty() && buenos.joinToString(">").length > MAX_RUTA) buenos.removeAt(0)
    return buenos.joinToString(">")
}

/** ¿[valor] tiene la forma de un id de Movi? (Para quien arma los datos de un evento.) */
fun esIdDeMovi(valor: String?): Boolean = valor != null && FormaDeDato.ID.regex!!.matches(valor)
