package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_MOMENTO_DE_CELEBRACION
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ola 1 · «Que Movi se sienta viva»: el checklist del período celebra **cuando el dueño lo
 * completa** —pasa de «falta 1» a «todo pagado» por algo que hizo acá—, y no al abrirlo con todo ya
 * pagado. La regla pura está en `CelebracionTest`; esto la prueba montada en la sección de verdad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class CelebracionDelChecklistTest {

    @get:Rule val composeRule = createComposeRule()

    private val arriendo = PagoDelPeriodo(
        ruleId = "rr_arriendo",
        nombre = "Arriendo",
        monto = 1_850_000,
        pagado = false,
        diasParaVencer = 6,
        vence = "2026-10-05",
        categoria = "Vivienda",
        cuentaId = "a1",
    )
    private val luz = arriendo.copy(ruleId = "rr_luz", nombre = "Luz", monto = 180_000, pagado = true)

    private var checklist by mutableStateOf(emptyList<PagoDelPeriodo>())

    private fun montar(inicial: List<PagoDelPeriodo>) {
        checklist = inicial
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = checklist,
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun celebra(): Boolean =
        composeRule.onAllNodes(hasTestTag(TAG_MOMENTO_DE_CELEBRACION), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun anotarElArriendo() {
        composeRule.onAllNodes(hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasClickAction(), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun abrir_con_todo_ya_pagado_no_celebra() {
        montar(listOf(arriendo.copy(pagado = true), luz))
        assertFalse(celebra())
    }

    @Test
    fun completar_el_ultimo_pago_celebra() {
        montar(listOf(arriendo, luz))
        assertFalse(celebra(), "falta 1")

        anotarElArriendo()
        // El movimiento se guardó en «Agregar» y la lista se volvió a leer: ya no falta nada.
        checklist = listOf(arriendo.copy(pagado = true), luz)
        composeRule.waitForIdle()

        assertTrue(celebra(), "falta 1 → todo pagado")
    }

    @Test
    fun si_la_lista_se_completa_sin_que_el_duenio_haga_nada_no_celebra() {
        montar(listOf(arriendo, luz))
        checklist = listOf(arriendo.copy(pagado = true), luz)
        composeRule.waitForIdle()
        assertFalse(celebra())
    }
}
