package com.jvillada.movi.ui.ai

import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.ui.Screen

/**
 * # Las pantallas que nombra una respuesta de Movi AI, tocables (vuelta 61)
 *
 * «Si ya lo pagaste, anótalo y Movi lo tilda solo en Pagos.» —y el dueño tenía que salir del chat y
 * buscar Pagos—. Ahora el nombre de la pantalla es un enlace a ella.
 *
 * **Conservador a propósito**: el nombre tiene que ir con su mayúscula, como lo escribe la app, y
 * **después de una preposición** («en Pagos», «de Pagos», «desde Actividad», «en «Por revisar»»),
 * porque «Pagos del período» al empezar una frase o «pagos» en minúscula son palabras, no pantallas.
 * Lo que no se reconoce se queda como texto: el peor caso es el de antes.
 */
internal data class PantallaNombrada(val nombre: String, val pantalla: Screen)

/** Un enlace en un texto: los índices `[desde, hasta)` del nombre (sin las comillas) y su pantalla. */
internal data class EnlaceAPantalla(val desde: Int, val hasta: Int, val pantalla: Screen)

/** Las que Movi AI puede nombrar, con el nombre que les da la app. Los más largos primero. */
internal val PANTALLAS_QUE_SE_NOMBRAN: List<PantallaNombrada> = listOf(
    PantallaNombrada("Cuadre de saldos", Screen.CuadreDeSaldos),
    PantallaNombrada("Tus períodos", Screen.Periodos),
    PantallaNombrada("Por revisar", Screen.PorRevisar),
    // «Para revisar» es la sección del Inicio.
    PantallaNombrada("Para revisar", Screen.Dashboard),
    PantallaNombrada("Presupuestos", Screen.Budgets),
    PantallaNombrada("Patrimonio", Screen.Accounts),
    PantallaNombrada("Actividad", Screen.Transactions()),
    PantallaNombrada("Créditos", Screen.Credits),
    PantallaNombrada("Inicio", Screen.Dashboard),
    PantallaNombrada("Grupos", Screen.Espacios),
    PantallaNombrada("Pagos", Screen.Plan()),
    PantallaNombrada("Tú", Screen.Tu),
)

private val PREPOSICIONES = listOf("en", "de", "desde", "a")

/**
 * Los enlaces a pantallas en [texto]. Un nombre cuenta solo si va precedido de una preposición
 * ([PREPOSICIONES], en minúscula, como palabra suelta) y un espacio —con «"«"» o no en el medio— y
 * si después no sigue una letra o un dígito («Pagosx» no es Pagos).
 */
internal fun enlacesAPantallas(texto: String): List<EnlaceAPantalla> {
    val enlaces = mutableListOf<EnlaceAPantalla>()
    var i = 0
    while (i < texto.length) {
        val encontrada = PANTALLAS_QUE_SE_NOMBRAN.firstOrNull { texto.startsWith(it.nombre, i) }
        if (encontrada != null && seCierra(texto, i + encontrada.nombre.length) && vieneDeUnaPreposicion(texto, i)) {
            enlaces += EnlaceAPantalla(i, i + encontrada.nombre.length, encontrada.pantalla)
            i += encontrada.nombre.length
        } else {
            i++
        }
    }
    return enlaces
}

private fun seCierra(texto: String, fin: Int): Boolean = fin >= texto.length || !texto[fin].isLetterOrDigit()

private fun vieneDeUnaPreposicion(texto: String, inicio: Int): Boolean {
    var j = inicio
    if (j > 0 && texto[j - 1] == '«') j--
    if (j == 0 || texto[j - 1] != ' ') return false
    val antes = texto.substring(0, j - 1)
    val palabra = antes.takeLastWhile { it.isLetter() }
    if (palabra !in PREPOSICIONES) return false
    val previo = antes.getOrNull(antes.length - palabra.length - 1)
    return previo == null || !previo.isLetterOrDigit()
}

/**
 * Adónde lleva un enlace de una respuesta de Movi AI. `null` (fuera del chat, en una prueba): los
 * nombres se pintan como texto.
 */
internal val LocalAbrirPantallaDesdeElChat = staticCompositionLocalOf<((Screen) -> Unit)?> { null }
