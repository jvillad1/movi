package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** **«Por confirmar $103.600»** (vuelta 44): la suma de lo que dicen los avisos, entera o nada. */
class LoQueSumanLosAvisosTest {

    private fun pesos(monto: Long) = MontoDelAviso(monto, "COP")

    @Test
    fun `suma lo que dicen los avisos en pesos`() {
        assertEquals(103_600L, sumaDeLoPorConfirmar(listOf(pesos(18_300), pesos(61_900), pesos(23_400))))
    }

    @Test
    fun `sin avisos no hay suma`() {
        assertNull(sumaDeLoPorConfirmar(emptyList()))
    }

    @Test
    fun `si uno no trae monto no se dice una suma a medias`() {
        assertNull(sumaDeLoPorConfirmar(listOf(pesos(18_300), null, pesos(23_400))))
        assertNull(sumaDeLoPorConfirmar(listOf(pesos(18_300), pesos(0))))
    }

    @Test
    fun `si uno es en otra moneda tampoco`() {
        assertNull(sumaDeLoPorConfirmar(listOf(pesos(18_300), MontoDelAviso(20, "USD"))))
    }

    @Test
    fun `un servidor viejo no la manda y un cliente viejo la ignora`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        // Sin el campo: nulo, y la franja dice el conteo.
        assertNull(json.decodeFromString(DashboardSummary.serializer(), """{"pendingSms":3}""").porConfirmarSuman)
        // Con el campo, viaja.
        assertEquals(
            103_600L,
            json.decodeFromString(DashboardSummary.serializer(), """{"pendingSms":3,"porConfirmarSuman":103600}""").porConfirmarSuman,
        )
    }
}
