package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El relevo de scroll de los márgenes, con la misma geometría que la cáscara
 *
 * Una ventana de 1200 dp, una columna de 300 dp centrada y una lista adentro: los 450 dp de cada
 * lado son los márgenes que en `App.kt` no reenviaban nada. Se arrastra **sobre el margen** y se
 * afirma que la lista se movió.
 *
 * Lo que esto prueba es el mecanismo —el `scrollable` de afuera delegando en la lista registrada—
 * con el gesto táctil, que es el que Robolectric sabe inyectar. **La rueda del mouse no se puede
 * probar acá**: Robolectric no la sintetiza sobre Compose, y ese es el camino que el dueño usa.
 * Esa mitad se verifica a ojo en el navegador y queda dicho en el PR.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp-mdpi")
class ScrollDesdeLosMargenesTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var listState: LazyListState

    private fun montar(conRegistro: Boolean = true) {
        composeRule.setContent {
            val relevo = remember { RelevoDeScroll() }
            CompositionLocalProvider(LocalRelevoDeScroll provides relevo) {
                Box(
                    modifier = Modifier.fillMaxSize().recibeElScrollDeLosMargenes(relevo).testTag("margenes"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(Modifier.widthIn(max = 300.dp).fillMaxSize()) {
                        listState = rememberLazyListState()
                        if (conRegistro) ScrollDesdeLosMargenes(listState)
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("lista")) {
                            items(200) { Text("Renglón $it", Modifier.height(40.dp)) }
                        }
                    }
                }
            }
        }
    }

    /** Un arrastre hacia arriba a 30 dp del borde izquierdo: bien adentro del margen. */
    private fun arrastrarSobreElMargen() {
        composeRule.onNodeWithTag("margenes").performTouchInput {
            swipe(start = Offset(30f, height * 0.8f), end = Offset(30f, height * 0.2f), durationMillis = 300)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `arrastrar sobre el margen mueve la lista`() {
        montar()
        assertEquals(0, listState.firstVisibleItemIndex)

        arrastrarSobreElMargen()

        assertTrue(listState.firstVisibleItemIndex > 0, "el margen tenía que mover la lista")
    }

    @Test
    fun `la lista misma sigue scrolleando con el relevo puesto`() {
        montar()

        composeRule.onNodeWithTag("lista").performTouchInput {
            swipe(start = Offset(width / 2f, height * 0.8f), end = Offset(width / 2f, height * 0.2f), durationMillis = 300)
        }
        composeRule.waitForIdle()

        assertTrue(listState.firstVisibleItemIndex > 0, "la lista tiene que seguir moviéndose sola")
    }

    @Test
    fun `sin lista registrada el margen no hace nada y no explota`() {
        montar(conRegistro = false)

        arrastrarSobreElMargen()

        assertEquals(0, listState.firstVisibleItemIndex)
    }
}
