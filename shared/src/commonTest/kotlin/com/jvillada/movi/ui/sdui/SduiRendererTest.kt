package com.jvillada.movi.ui.sdui

import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Los remapeos de `screenForTarget` — Ola B, tarea 7 agregó dos más («goals» y «extractos») al
 * mismo patrón que ya tenían «investments» y «subscriptions»: el target sigue siendo válido en
 * `ScreenTaxonomy.NAVIGATE_TARGETS` (una definición vieja no se rompe), y quien lo toque termina
 * en otro lado, no en una pantalla que ya no tiene puerta propia.
 */
class SduiRendererTest {

    @Test
    fun metas_ya_no_tiene_pantalla_propia_y_manda_a_cuentas() {
        // A diferencia de "investments", acá no quedó un «donde ahora viven las metas» — Metas
        // salió de la navegación entera, así que el destino de reserva es Cuentas.
        assertEquals(Screen.Accounts, screenForTarget("goals"))
    }

    @Test
    fun extractos_se_unio_a_documentos() {
        assertEquals(Screen.Documentos, screenForTarget("extractos"))
    }

    @Test
    fun los_remapeos_anteriores_siguen_igual() {
        assertEquals(Screen.Accounts, screenForTarget("investments"))
    }

    /**
     * Ola C: los targets no cambian (viajan en definiciones guardadas) — cambia adónde llevan. Los
     * recurrentes y las suscripciones son Plan · Pagos del mes, Presupuestos es su otro segmento, y
     * «Más» es Ajustes (la misma `Screen.Mas`).
     */
    @Test
    fun los_destinos_de_la_ola_C() {
        assertEquals(Screen.Plan(SEGMENTO_PRESUPUESTOS), screenForTarget("budgets"))
        assertEquals(Screen.Plan(SEGMENTO_PAGOS), screenForTarget("recurrentes"))
        assertEquals(Screen.Plan(SEGMENTO_PAGOS), screenForTarget("subscriptions"))
        assertEquals(Screen.Accounts, screenForTarget("accounts"))
        assertEquals(Screen.Accounts, screenForTarget("investments"))
        assertEquals(Screen.Credits, screenForTarget("credits"))
        assertEquals(Screen.Mas, screenForTarget("mas"))
    }

    @Test
    fun un_target_desconocido_no_explota() {
        assertEquals(null, screenForTarget("algo-que-no-existe"))
    }
}
