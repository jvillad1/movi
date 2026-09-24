package com.jvillada.movi.ui.sdui.editor

import com.jvillada.movi.shared.model.ScreenTaxonomy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ola B, tarea 7: el Editor de pantallas deja de ofrecer «Metas» como opción nueva. El target
 * sigue siendo válido en `ScreenTaxonomy.NAVIGATE_TARGETS` —una definición guardada de antes de
 * esta tanda no se rompe— así que la poda es solo en lo que el Editor OFRECE, no en la taxonomía.
 */
class ScreenEditorScreenTest {

    @Test
    fun el_editor_ya_no_ofrece_metas() {
        assertFalse("goals" in NAVEGABLES_DESDE_EL_EDITOR)
    }

    @Test
    fun pero_el_target_sigue_siendo_valido_para_una_definicion_vieja() {
        assertTrue("goals" in ScreenTaxonomy.NAVIGATE_TARGETS)
    }

    @Test
    fun el_resto_de_los_targets_se_sigue_ofreciendo() {
        assertEqualsSinGoals(ScreenTaxonomy.NAVIGATE_TARGETS, NAVEGABLES_DESDE_EL_EDITOR)
    }

    private fun assertEqualsSinGoals(todos: List<String>, ofrecidos: List<String>) {
        assertTrue(todos.filterNot { it == "goals" } == ofrecidos)
    }
}
