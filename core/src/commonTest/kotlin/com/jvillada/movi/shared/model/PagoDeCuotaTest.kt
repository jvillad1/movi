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

    /**
     * Las dos patas de un pago, con el desglose calculado igual que en el server.
     *
     * `rateEa` en null por defecto = [MotivoDelDesglose.SIN_TASA], o sea el comportamiento de
     * siempre (la deuda baja por el monto completo). Los tests de esta clase miran categorías,
     * cuentas y enlace, que no dependen del reparto; los que sí lo miran pasan la tasa. Cuánto se
     * reparte se prueba en `DesgloseDeCuotaTest`, con sus créditos reales.
     */
    private fun patas(from: Account, debt: Account, monto: Long, rateEa: Double? = null, seguro: Long? = null) =
        pagoDeCuotaLegs(
            peticion(from.id, debt.id, monto),
            from,
            debt,
            desglosarCuota(monto, debt.type, debt.balance, rateEa, seguro),
        )

    // ── La decisión del dueño ──────────────────────────────────────────────────

    @Test
    fun la_cuota_de_un_credito_CUENTA_en_los_gastos_del_mes() {
        // «Sí, es plata que salió». Antes, registrar la cuota del carro como traspaso la sacaba
        // del mes —los dos lados excluidos— y sus gastos quedaban $4.215.223 por debajo de lo
        // real, con el «Flujo del mes» viéndose mejor de lo que era.
        val (dinero, _) = patas(ahorros, carro, 4_215_223)

        assertEquals(CUOTA_CATEGORY, dinero.category)
        assertFalse(isReservedCategory(CUOTA_CATEGORY), "si fuera reservada, no contaría")
        assertTrue(isCashFlow(ahorros.type, dinero.type, dinero.category))
    }

    @Test
    fun el_pago_de_una_tarjeta_NO_cuenta() {
        // Las compras ya contaron cuando se hicieron. Contar también el pago sería contar la
        // misma plata dos veces.
        val (dinero, _) = patas(ahorros, amex, 1_008_902)

        assertEquals(CARD_PAYMENT_CATEGORY, dinero.category)
        assertFalse(isCashFlow(ahorros.type, dinero.type, dinero.category))
    }

    @Test
    fun la_pata_de_la_deuda_la_BAJA_en_los_dos_casos() {
        // Es lo que el dueño vino a ver: que la deuda baje. `signedDelta` sobre una cuenta de
        // deuda resta un INCOME.
        listOf(carro to 4_215_223L, amex to 1_008_902L).forEach { (deuda, monto) ->
            val (_, pata) = patas(ahorros, deuda, monto)

            assertEquals(TransactionType.INCOME, pata.type)
            assertTrue(signedDelta(deuda.type, pata.type, pata.amount) < 0L)
            // Y nunca cuenta como ingreso del mes: vive en una cuenta de deuda.
            assertFalse(isCashFlow(deuda.type, pata.type, pata.category))
        }
    }

    @Test
    fun las_dos_patas_quedan_enlazadas() {
        // Sin el enlace serían dos movimientos sueltos que nadie puede volver a juntar — ni para
        // mostrarlos como una sola fila, ni para anular el pago entero.
        val (dinero, deuda) = patas(ahorros, carro, 4_215_223)

        assertEquals("tr1", dinero.transferId)
        assertEquals("tr1", deuda.transferId)
    }

    // ── La asimetría del par: lo que cambió en esta ola ────────────────────────

    @Test
    fun en_un_credito_que_amortiza_las_dos_patas_NO_valen_lo_mismo() {
        // **Acá estaba el error.** Antes las dos patas salían por $4.215.223 y la deuda del carro
        // bajaba esa cifra entera, interés incluido. Con las seis cuotas de un mes, Movi le
        // mostraba $18,7 millones menos de deuda de la que tiene, y el error se acumulaba.
        val (dinero, deuda) = patas(ahorros, carro, 4_215_223, rateEa = 18.16)

        assertEquals(4_215_223L, dinero.amount, "de la cuenta SÍ salió la cuota entera")
        assertEquals(1_733_905L, deuda.amount, "a la deuda solo le entra el capital")
        assertEquals(-1_733_905L, signedDelta(carro.type, deuda.type, deuda.amount))
    }

    @Test
    fun la_cuota_entera_sigue_contando_en_los_gastos_del_mes() {
        // La mitad de la decisión que NO cambió: esa plata salió de su bolsillo, toda. Si alguien
        // "arreglara" la asimetría bajando también la pata del dinero al capital, sus gastos del
        // mes caerían $18,7 millones — el mismo error, del otro lado.
        val (dinero, _) = patas(ahorros, carro, 4_215_223, rateEa = 18.16)

        assertTrue(dinero.countsAsCashFlow)
        assertEquals(4_215_223L, dinero.amount)
    }

    @Test
    fun el_pago_de_una_tarjeta_sigue_siendo_simetrico() {
        // Una tarjeta no amortiza: pagar $1.008.902 baja la deuda $1.008.902. Este test blinda lo
        // que NO tenía que cambiar.
        val (dinero, deuda) = patas(ahorros, amex, 1_008_902, rateEa = 32.0)

        assertEquals(dinero.amount, deuda.amount)
        assertEquals(1_008_902L, deuda.amount)
    }

    @Test
    fun la_pata_de_la_deuda_DICE_que_es_un_abono_a_capital() {
        // Si el renglón del crédito dijera «Pago desde Bancolombia Ahorros · $1.733.905» sobre una
        // cuota de $4.215.223, no habría en toda la app dónde enterarse de a dónde se fue la
        // diferencia. Cuando las dos patas valen lo mismo, en cambio, no hay nada que aclarar.
        val (_, conTasa) = patas(ahorros, carro, 4_215_223, rateEa = 18.16)
        val (_, sinTasa) = patas(ahorros, carro, 4_215_223)

        assertTrue(conTasa.description.startsWith("Abono a capital desde"), conTasa.description)
        assertTrue(sinTasa.description.startsWith("Pago desde"), sinTasa.description)
    }

    @Test
    fun la_pata_de_la_deuda_GUARDA_lo_que_no_amortizo() {
        // Sin esto, corregir el monto después tenía que deducir el interés restando las dos patas
        // — y la resta miente en cuanto el capital se clampa a cero. Ver
        // [FinancialEvent.noAmortiza] y [montoDeLaHermanaAlCorregir].
        val (dinero, deuda) = patas(ahorros, carro, 4_215_223, rateEa = 18.16, seguro = 108_800L)

        assertEquals(2_481_318L + 108_800L, deuda.noAmortiza, "interés + seguro, en un solo número")
        assertEquals(dinero.amount, deuda.amount + deuda.noAmortiza!!, "las dos patas cuadran con esto")
        assertNull(dinero.noAmortiza, "la pata del dinero no guarda nada: la plata salió entera")
    }

    @Test
    fun un_par_simetrico_no_guarda_nada_y_ese_null_ES_la_respuesta() {
        // Una tarjeta y un crédito sin tasa arman pares simétricos de verdad. Un 0 explícito diría
        // lo mismo pero con pinta de calculado, y la corrección del monto lo trataría distinto.
        val (_, tarjeta) = patas(ahorros, amex, 1_008_902, rateEa = 32.0)
        val (_, sinTasa) = patas(ahorros, carro, 4_215_223)

        assertNull(tarjeta.noAmortiza)
        assertNull(sinTasa.noAmortiza)
    }

    @Test
    fun una_cuota_que_no_cubre_el_interes_deja_la_pata_en_cero_PERO_guarda_el_interes() {
        // El caso que rompía la corrección: la resta de las dos patas da $3.000.000 y el interés
        // real es $3.646.011. Guardado, corregir el monto después vuelve al capital exacto.
        val libranza = Account("l2", "Libranza 4818", AccountType.LOAN, 283_000_000L)
        val (dinero, deuda) = patas(ahorros, libranza, 3_000_000, rateEa = 15.50)

        assertEquals(0L, deuda.amount, "nada de este pago abona a capital")
        assertTrue(
            deuda.noAmortiza!! > dinero.amount,
            "el interés del mes es MAYOR que lo pagado, y la resta de las patas no lo sabría",
        )
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
        // La primera versión comparaba contra una tarjeta en COP y una cuenta en COP: pasaba
        // igual si la implementación tomara la moneda del ORIGEN. Con la tarjeta en dólares el
        // test distingue de verdad cuál de las dos se usa.
        val ahorrosUsd = Account("a3", "Ahorros USD", AccountType.SAVINGS, 500, currency = "USD")
        val amexUsd = Account("c2", "Master Black USD", AccountType.CREDIT_CARD, 1_257, currency = "USD")

        val (dinero, deuda) = patas(ahorrosUsd, amexUsd, 100)

        assertEquals("USD", dinero.currency)
        assertEquals("USD", deuda.currency)
    }

    @Test
    fun cada_pata_va_a_SU_cuenta() {
        // El borde más caro, y no estaba cubierto: si las patas salieran invertidas —el EXPENSE en
        // la deuda y el INCOME en la cuenta— los demás tests seguirían verdes, porque miran
        // `type`, `signedDelta` y `isCashFlow` pasándoles a mano el tipo de cuenta correcto, nunca
        // el `accountId` que la pata trae. Invertidas, un pago SUBIRÍA la deuda y metería plata
        // que no existe en la cuenta de ahorros.
        val (dinero, deuda) = patas(ahorros, carro, 4_215_223)

        assertEquals(ahorros.id, dinero.accountId)
        assertEquals(TransactionType.EXPENSE, dinero.type)
        assertEquals(carro.id, deuda.accountId)
        assertEquals(TransactionType.INCOME, deuda.type)
    }

    @Test
    fun las_patas_declaran_bien_si_cuentan_en_el_mes() {
        // `countsAsCashFlow` viaja en la respuesta del server y el cliente la lee sin recalcular.
        // Salían las dos con el default `true`, así que la respuesta afirmaba que el pago de una
        // tarjeta cuenta en el mes — lo contrario de la decisión del dueño.
        val (dineroCredito, deudaCredito) = patas(ahorros, carro, 4_215_223)
        assertTrue(dineroCredito.countsAsCashFlow, "la cuota de un crédito SÍ cuenta")
        assertFalse(deudaCredito.countsAsCashFlow, "la pata de la deuda nunca")

        val (dineroTarjeta, deudaTarjeta) = patas(ahorros, amex, 1_008_902)
        assertFalse(dineroTarjeta.countsAsCashFlow, "el pago de una tarjeta NO cuenta")
        assertFalse(deudaTarjeta.countsAsCashFlow)
    }
}
