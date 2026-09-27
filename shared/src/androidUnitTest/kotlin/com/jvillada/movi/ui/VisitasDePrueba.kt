package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.theme.MoviTheme

/**
 * # Entrar, salir y volver a entrar a una pantalla, como hace `App.kt` al navegar
 *
 * Solo la pantalla actual está compuesta: al navegar, la anterior sale de la composición con todos
 * sus `remember`. Acá se la saca y se la vuelve a poner con [enPantalla], que es lo mismo, para
 * probar lo que [com.jvillada.movi.data.CacheDeLecturas] recuerda de una visita a la siguiente.
 */
class VisitasDePrueba(
    private val regla: ComposeContentTestRule,
    private val pantalla: @Composable () -> Unit,
) {
    var enPantalla by mutableStateOf(true)

    fun montar() = regla.setContent {
        MoviTheme { Box(Modifier.fillMaxSize()) { if (enPantalla) pantalla() } }
    }

    /** La primera visita, hasta que todo contestó, y salir. */
    fun primeraYSalir() {
        montar()
        regla.waitForIdle()
        salir()
    }

    fun salir() {
        enPantalla = false
        regla.waitForIdle()
    }

    /**
     * Volver y quedarse en el PRIMER cuadro: el reloj se congela, y lo que se ve sale solo de lo
     * recordado — la lectura de la visita todavía no pudo contestar.
     */
    fun volverAlPrimerCuadro() {
        regla.mainClock.autoAdvance = false
        enPantalla = true
        // El cambio se hizo desde el hilo de la prueba: hay que avisarle al recompositor, que con
        // el reloj congelado no lo va a buscar solo.
        Snapshot.sendApplyNotifications()
        regla.mainClock.advanceTimeByFrame()
    }

    /** Suelta el reloj de [volverAlPrimerCuadro] y deja que todo conteste. */
    fun seguir() {
        regla.mainClock.autoAdvance = true
        regla.waitForIdle()
    }

    fun volver() {
        enPantalla = true
        regla.waitForIdle()
    }

    fun cuantas(texto: String, substring: Boolean = false): Int =
        regla.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().size

    fun cuantasConTag(tag: String): Int =
        regla.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
}
