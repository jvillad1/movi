package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.planDeUnaDeuda
import com.jvillada.movi.ui.quickadd.desgloseDelPago
import com.jvillada.movi.ui.quickadd.textoDelDesglose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Lo que dicen la tarjeta de Créditos y la hoja de «Cuota» de un crédito marcado «No cobra
 * intereses». La aritmética está en `CreditoSinInteresesTest` (`:core`); acá, que las frases no
 * digan «$0 de interés · 0 % de la cuota» ni «sin tasa registrada».
 */
class CreditoSinInteresesEnLaTarjetaTest {

    @Test
    fun `la tarjeta dice que no cobra intereses y cuando se termina`() {
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 1_500_000L, null, null, saleDeTuBolsillo = true, sinIntereses = true)

        assertEquals(NO_COBRA_INTERESES, textoDelInteres(plan))
        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertTrue(comoVa.texto.startsWith("Te faltan 7 cuotas"), comoVa.texto)
        assertFalse(comoVa.esAlerta)
    }

    @Test
    fun `tasa cero sin la casilla sigue diciendo sin tasa registrada`() {
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 1_500_000L, null, null, saleDeTuBolsillo = true)

        assertTrue(textoDelInteres(plan).startsWith("Sin tasa registrada"))
    }

    @Test
    fun `si la cuota solo cubre el seguro no dice que se va en intereses`() {
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 100_000L, 100_000L, null, saleDeTuBolsillo = true, sinIntereses = true)

        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertFalse("intereses" in comoVa.texto, comoVa.texto)
    }

    @Test
    fun `la hoja de cuota no habla de intereses ni de sin tasa`() {
        val prestamo = Account("acc_papa", "Préstamo papá", AccountType.LOAN, 10_000_000L)
        val terms = CreditTerms(
            accountId = prestamo.id, bank = "Papá", principal = 10_000_000L, rateEa = 0.0,
            termMonths = 7, installment = 1_500_000L, dayOfMonth = 5, startDate = "2026-01-05",
            sinIntereses = true,
        )
        val d = assertNotNull(desgloseDelPago(prestamo, terms, 1_500_000L))
        assertEquals(1_500_000L, d.capital)

        val texto = assertNotNull(textoDelDesglose(d, "COP"))
        assertFalse("interés" in texto || "intereses" in texto || "tasa" in texto, texto)
        assertTrue("bajan la deuda" in texto, texto)
    }
}
