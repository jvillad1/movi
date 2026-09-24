package com.jvillada.movi.ui.sdui

import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES
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
        assertEquals(Screen.Transactions(CHIP_RECURRENTES), screenForTarget("subscriptions"))
        assertEquals(Screen.Transactions(CHIP_RECURRENTES), screenForTarget("recurrentes"))
    }

    @Test
    fun un_target_desconocido_no_explota() {
        assertEquals(null, screenForTarget("algo-que-no-existe"))
    }
}
