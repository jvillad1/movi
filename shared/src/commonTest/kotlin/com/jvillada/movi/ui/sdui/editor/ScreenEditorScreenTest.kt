package com.jvillada.movi.ui.sdui.editor

import com.jvillada.movi.shared.model.ScreenTaxonomy
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(ScreenTaxonomy.NAVIGATE_TARGETS.filterNot { it == "goals" || it == "profile" }, NAVEGABLES_DESDE_EL_EDITOR)
    }

    /** Vuelta de diseño 3: «Perfil» es Ajustes, que el Editor ya ofrece como «mas». Sigue válido. */
    @Test
    fun el_editor_no_ofrece_perfil_aparte_de_Ajustes() {
        assertFalse("profile" in NAVEGABLES_DESDE_EL_EDITOR)
        assertTrue("profile" in ScreenTaxonomy.NAVIGATE_TARGETS)
    }

    /**
     * Ola C: el selector nombra el LUGAR al que se llega hoy. «Presupuestos», «Cuentas» y «Más»
     * dejaron de ser pestañas: son Plan, Patrimonio y Ajustes.
     */
    @Test
    fun los_rotulos_nombran_los_lugares_de_la_ola_C() {
        assertEquals("Inicio", NAVIGATE_TARGET_LABELS["dashboard"])
        // Vuelta 38: las pestañas de la opción 3.
        assertEquals("Actividad", NAVIGATE_TARGET_LABELS["transactions"])
        assertEquals("Tú", NAVIGATE_TARGET_LABELS["mas"])
        assertEquals("Patrimonio", NAVIGATE_TARGET_LABELS["accounts"])
        assertTrue(NAVIGATE_TARGET_LABELS.getValue("budgets").startsWith("Pagos"))
        assertTrue(NAVIGATE_TARGET_LABELS.getValue("recurrentes").startsWith("Pagos"))
        assertTrue(NAVIGATE_TARGET_LABELS.getValue("subscriptions").startsWith("Pagos"))
        // Y todo target que el Editor ofrece tiene rótulo propio.
        NAVEGABLES_DESDE_EL_EDITOR.forEach { assertTrue(it in NAVIGATE_TARGET_LABELS, it) }
    }
}
