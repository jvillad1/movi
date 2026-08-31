package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pagar la cuota de un crédito, o el extracto de una tarjeta, en una sola acción.
 *
 * Lo pidió el dueño después de pagar la AMEX y no saber cómo anotarlo: *«necesito poder agregar un
 * tipo de movimiento que sea pago de cuota, que pueda asociar a un crédito o tarjeta, y que vos
 * sepas cómo manejarlo por debajo»*.
 *
 * Lo que estas pruebas fijan no es la mecánica de las dos patas —eso se ve en el server— sino
 * **la decisión que él tomó**: la cuota de un crédito cuenta en sus gastos del mes, y el pago de
 * una tarjeta no. Si alguien invierte eso, sus números cambian en millones.
 */
class PagoDeCuotaTest {

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)
    private val amex = Account("c1", "AMEX 9208", AccountType.CREDIT_CARD, 19_818_701)

    private fun peticion(from: String, debt: String, monto: Long) = CreatePagoDeCuotaRequest(
        fromAccountId = from,
        debtAccountId = debt,
        amount = monto,
        timestamp = 1_788_000_000_000L,
        transferId = "tr1",
        fromEventId = "ev1",
        toEventId = "ev2",
    )

    // ── La decisión del dueño ──────────────────────────────────────────────────

    @Test
    fun la_cuota_de_un_credito_CUENTA_en_los_gastos_del_mes() {
        // «Sí, es plata que salió». Antes, registrar la cuota del carro como traspaso la sacaba
        // del mes —los dos lados excluidos— y sus gastos quedaban $4.215.223 por debajo de lo
        // real, con el «Flujo del mes» viéndose mejor de lo que era.
        val (dinero, _) = pagoDeCuotaLegs(peticion("a1", "l1", 4_215_223), ahorros, carro)

        assertEquals(CUOTA_CATEGORY, dinero.category)
        assertFalse(isReservedCategory(CUOTA_CATEGORY), "si fuera reservada, no contaría")
        assertTrue(isCashFlow(ahorros.type, dinero.type, dinero.category))
    }

    @Test
    fun el_pago_de_una_tarjeta_NO_cuenta() {
        // Las compras ya contaron cuando se hicieron. Contar también el pago sería contar la
        // misma plata dos veces.
        val (dinero, _) = pagoDeCuotaLegs(peticion("a1", "c1", 1_008_902), ahorros, amex)

        assertEquals(CARD_PAYMENT_CATEGORY, dinero.category)
        assertFalse(isCashFlow(ahorros.type, dinero.type, dinero.category))
    }

    @Test
    fun la_pata_de_la_deuda_la_BAJA_en_los_dos_casos() {
        // Es lo que el dueño vino a ver: que la deuda baje. `signedDelta` sobre una cuenta de
        // deuda resta un INCOME.
        listOf(carro to 4_215_223L, amex to 1_008_902L).forEach { (deuda, monto) ->
            val (_, pata) = pagoDeCuotaLegs(peticion("a1", deuda.id, monto), ahorros, deuda)

            assertEquals(TransactionType.INCOME, pata.type)
            assertEquals(-monto, signedDelta(deuda.type, pata.type, pata.amount))
            // Y nunca cuenta como ingreso del mes: vive en una cuenta de deuda.
            assertFalse(isCashFlow(deuda.type, pata.type, pata.category))
        }
    }

    @Test
    fun las_dos_patas_quedan_enlazadas() {
        // Sin el enlace serían dos movimientos sueltos que nadie puede volver a juntar — ni para
        // mostrarlos como una sola fila, ni para anular el pago entero.
        val (dinero, deuda) = pagoDeCuotaLegs(peticion("a1", "l1", 4_215_223), ahorros, carro)

        assertEquals("tr1", dinero.transferId)
        assertEquals("tr1", deuda.transferId)
        assertEquals(dinero.amount, deuda.amount)
    }

    // ── Lo que no se puede hacer ───────────────────────────────────────────────

    @Test
    fun un_pago_valido_no_tiene_motivo() {
        assertNull(validarPagoDeCuota(peticion("a1", "l1", 4_215_223), ahorros, carro))
    }

    @Test
    fun no_se_paga_una_deuda_con_otra_deuda() {
        // Mover deuda de un lado a otro no es pagar nada, y dejaría las dos cifras mintiendo.
        assertEquals(
            PAGO_DESDE_DEUDA_BLOQUEADO,
            validarPagoDeCuota(peticion("c1", "l1", 100_000), amex, carro),
        )
    }

    @Test
    fun lo_que_se_paga_tiene_que_ser_un_credito_o_una_tarjeta() {
        val otraCuenta = Account("a2", "Nu", AccountType.SAVINGS, 17_100_000)

        assertEquals(
            PAGO_A_NO_DEUDA_BLOQUEADO,
            validarPagoDeCuota(peticion("a1", "a2", 100_000), ahorros, otraCuenta),
        )
    }

    @Test
    fun no_se_mezclan_monedas() {
        // La tarjeta en dólares del dueño se paga con dólares. Convertir acá dejaría el saldo de
        // una de las dos cuentas mal por el tipo de cambio del día, en silencio.
        val amexUsd = Account("c2", "Master Black USD", AccountType.CREDIT_CARD, 1_257, currency = "USD")

        assertEquals(
            PAGO_MONEDAS_DISTINTAS,
            validarPagoDeCuota(peticion("a1", "c2", 100), ahorros, amexUsd),
        )
    }

    @Test
    fun un_monto_en_cero_no_es_un_pago() {
        assertTrue(validarPagoDeCuota(peticion("a1", "l1", 0), ahorros, carro) != null)
    }

    @Test
    fun la_moneda_de_las_patas_es_la_de_la_deuda() {
        // Las dos patas comparten moneda; se toma la de la deuda porque es la que no se puede
        // convertir. (Con monedas distintas la validación ya cortó antes.)
        val (dinero, deuda) = pagoDeCuotaLegs(peticion("a1", "c1", 1_008_902), ahorros, amex)

        assertEquals(amex.currency, dinero.currency)
        assertEquals(amex.currency, deuda.currency)
    }
}
