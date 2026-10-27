package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.EstadoDePresupuesto
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vuelta de diseño 14: debajo de la barra de un presupuesto, el texto dice lo que importa —«Te
 * pasaste por $X» o «Te quedan $X»— y la barra lleva el color del estado. Sin «Sobrepasado ·» ni
 * «disponibles».
 */
class LoQueDiceElPresupuestoTest {

    private fun progreso(gastado: Long, limite: Long) = BudgetProgress(Budget("Comida", limite), gastado)

    @Test
    fun `pasado, dice por cuanto`() {
        val p = progreso(190_700, 70_000)
        assertEquals(EstadoDePresupuesto.EXCEDIDO_MUCHO, p.state)
        assertEquals("Te pasaste por \$120.700", loQueDiceElPresupuesto(p))
        assertEquals("Te pasaste por \$8.737", loQueDiceElPresupuesto(progreso(1_008_737, 1_000_000)))
    }

    @Test
    fun `con margen o cerca, dice cuanto queda`() {
        assertEquals("Te quedan \$16.100", loQueDiceElPresupuesto(progreso(23_200, 39_300)))
        assertEquals("Te quedan \$100.000", loQueDiceElPresupuesto(progreso(900_000, 1_000_000)))
    }

    @Test
    fun `justo en el limite no dice ni que se paso ni que queda cero`() {
        assertEquals("Llegaste justo al límite", loQueDiceElPresupuesto(progreso(1_000_000, 1_000_000)))
    }

    @Test
    fun `la barra lleva el semaforo del duenno`() {
        assertEquals(TonoDeBarra.Peligro, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.EXCEDIDO_MUCHO))
        assertEquals(TonoDeBarra.Aviso, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.EXCEDIDO_POCO))
        assertEquals(TonoDeBarra.Aviso, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.AL_LIMITE))
        assertEquals(TonoDeBarra.Aviso, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.CERCA))
        assertEquals(TonoDeBarra.Exito, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.DENTRO))
    }
}
