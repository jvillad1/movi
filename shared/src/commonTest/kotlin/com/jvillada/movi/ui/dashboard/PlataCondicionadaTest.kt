package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Plata que es suya pero que **no puede gastar**.
 *
 * El dueño, viendo «Tu plata $137.625.167» en el Inicio: *«esa plata no la tengo disponible; la de
 * Skandia es dinero que deberías referenciar en patrimonio para el cálculo pero no mostrarle como
 * disponible en mi balance, sino como un dinero disponible condicionado a uso en Vivienda»*.
 *
 * Son $106.000.000 de una pensión voluntaria: solo los puede retirar para vivienda sin perder el
 * beneficio tributario. La cifra grande del Inicio anunciaba cuatro veces lo que podía disponer.
 */
class PlataCondicionadaTest {

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 14_525_167)
    private val nu = Account("a2", "Nu", AccountType.SAVINGS, 17_100_000)
    private val skandia = Account(
        "a3", "Skandia pensión voluntaria", AccountType.INVESTMENT, 106_000_000,
        condicionadaA = "Vivienda",
    )
    private val hipoteca = Account("l1", "Hipoteca 1254", AccountType.LOAN, 767_800_000)

    @Test
    fun la_plata_condicionada_sale_de_Tu_plata() {
        // Las cifras reales del dueño: $31.625.167 disponibles, no $137.625.167.
        val b = heroBalance(listOf(ahorros, nu, skandia))

        assertEquals(31_625_167, b.tuPlata)
        assertEquals(106_000_000, b.condicionado)
        assertEquals("Vivienda", b.condicionadoA)
    }

    @Test
    fun pero_SIGUE_contando_en_el_patrimonio() {
        // La otra mitad, y la que hace que esto no sea esconder plata: es suya, así que suma a lo
        // que vale. Si saliera también del patrimonio, su foto quedaría $106M peor de lo real.
        val b = heroBalance(listOf(ahorros, nu, skandia, hipoteca))

        assertEquals(137_625_167 - 767_800_000, b.patrimonio)
    }

    @Test
    fun una_cuenta_libre_de_inversion_sigue_en_Tu_plata() {
        // No es «toda inversión sale»: un CDT que puede retirar cuando quiera es plata suya y
        // disponible. Lo que la saca es la CONDICIÓN, no el tipo de cuenta.
        val cdt = Account("a4", "CDT", AccountType.INVESTMENT, 5_000_000)

        val b = heroBalance(listOf(ahorros, cdt))

        assertEquals(19_525_167, b.tuPlata)
        assertEquals(5_000_000, b.invertido)
        assertEquals(0, b.condicionado)
    }

    @Test
    fun con_dos_condiciones_distintas_no_se_inventa_una_comun() {
        // Cesantías y pensión voluntaria no se usan para lo mismo. El renglón lo dice en genérico
        // en vez de elegir una de las dos.
        val cesantias = Account("a5", "Cesantías", AccountType.INVESTMENT, 8_000_000, condicionadaA = "Educación")

        val b = heroBalance(listOf(ahorros, skandia, cesantias))

        assertEquals(114_000_000, b.condicionado)
        assertNull(b.condicionadoA, "con condiciones distintas no hay una sola que nombrar")
    }

    @Test
    fun una_condicion_en_blanco_es_plata_libre() {
        // El campo es texto libre: un espacio no puede sacar plata del balance.
        val rara = Account("a6", "Rara", AccountType.SAVINGS, 1_000_000, condicionadaA = "   ")

        val b = heroBalance(listOf(rara))

        assertEquals(1_000_000, b.tuPlata)
        assertEquals(0, b.condicionado)
    }

    @Test
    fun sin_condiciones_todo_sigue_como_antes() {
        // La garantía para quien no usa el campo: su Inicio no cambia.
        val b = heroBalance(listOf(ahorros, nu, hipoteca))

        assertEquals(31_625_167, b.tuPlata)
        assertEquals(0, b.condicionado)
        assertNull(b.condicionadoA)
    }
}
