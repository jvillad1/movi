package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.desglosarCuota
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.ui.buscar.unaPataPorPar
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **La fila de un par se pinta como cuenta en los totales** (vuelta 50), con las patas de los
 * constructores de verdad. La decisión es `comoSePresenta` de `:core` (ver `ComoSePresentaTest`); acá,
 * que la fila, su texto, el «Gastaste» del día y Buscar la usen.
 */
class ComoSePresentaLaFilaTest {

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069L)
    private val cdt = Account("acc-cdt", "CDT", AccountType.INVESTMENT, 5_000_000L)
    private val credito = Account("acc-credito", "Libre inversión 9695", AccountType.LOAN, 40_710_555L)
    private val tarjeta = Account("acc-tarjeta", "AMEX 9208", AccountType.CREDIT_CARD, 1_818_701L)
    private val tipos = listOf(banco, cdt, credito, tarjeta).associate { it.id to it.type }

    private fun pago(id: String, deuda: Account, monto: Long): Pair<FinancialEvent, FinancialEvent> = pagoDeCuotaLegs(
        CreatePagoDeCuotaRequest(
            fromAccountId = banco.id, debtAccountId = deuda.id, amount = monto, timestamp = 1_790_000_000_000L,
            transferId = "tr-$id", fromEventId = "$id-sale", toEventId = "$id-entra",
        ),
        banco,
        deuda,
        desglosarCuota(monto, deuda.type, deuda.balance, 11.27, null, null, yaCobradoEnElMes = 0L),
    )

    private fun traspaso(id: String, desde: Account, hacia: Account, monto: Long) = transferLegsFor(
        CreateTransferRequest(
            transferId = "tr-$id", fromEventId = "$id-sale", toEventId = "$id-entra",
            fromAccountId = desde.id, toAccountId = hacia.id, amount = monto, timestamp = 1_790_000_000_000L,
        ),
        desde,
        hacia,
    )

    private fun fila(par: Pair<FinancialEvent, FinancialEvent>) = MovementRow.Transfer(par.first, par.second)

    private val cuota = pago("cuota", credito, 980_000L)
    private val pagoDeTarjeta = pago("tarjeta", tarjeta, 100_000L)
    private val traspasoEntreCuentas = traspaso("traspaso", banco, cdt, 50_000L)
    private val desembolso = traspaso("desembolso", credito, banco, 10_000_000L)
    private val abono = traspaso("abono", banco, credito, 2_000_000L)

    @Test
    fun `la cuota es un gasto, con menos, por lo que salio de tu cuenta`() {
        assertEquals(TonoDelMonto.GASTO, tonoDelRenglon(fila(cuota), tipos))
        assertEquals(TonoDelMonto.GASTO, tonoDelRenglon(fila(cuota)))
        assertEquals("−\$980.000", textoDelMontoDeTraspaso(fila(cuota), tipos))
        // La cifra es la de la pata del dinero (la cuota entera), no la del abono a capital.
        assertEquals(cuota.first, pataQueSeMuestra(fila(cuota), tipos))
        assertEquals("Cuota de Libre inversión 9695", transferRowTitle(fila(cuota), tipos), "vuelta 67: por su crédito")
    }

    @Test
    fun `el pago de tarjeta sigue entre cuentas, sin signo`() {
        assertEquals(TonoDelMonto.ENTRE_CUENTAS, tonoDelRenglon(fila(pagoDeTarjeta), tipos))
        assertEquals("\$100.000", textoDelMontoDeTraspaso(fila(pagoDeTarjeta), tipos))
    }

    @Test
    fun `el traspaso sigue entre cuentas, sin signo`() {
        assertEquals(TonoDelMonto.ENTRE_CUENTAS, tonoDelRenglon(fila(traspasoEntreCuentas), tipos))
        assertEquals("\$50.000", textoDelMontoDeTraspaso(fila(traspasoEntreCuentas), tipos))
    }

    @Test
    fun `el desembolso es un ingreso, con mas, por la pata que entro a tu cuenta`() {
        assertEquals(TonoDelMonto.INGRESO, tonoDelRenglon(fila(desembolso), tipos))
        assertEquals("+\$10.000.000", textoDelMontoDeTraspaso(fila(desembolso), tipos))
        assertEquals(desembolso.second, pataQueSeMuestra(fila(desembolso), tipos))
        assertEquals(DESEMBOLSO_CATEGORY, pataQueSeMuestra(fila(desembolso), tipos).category)
    }

    @Test
    fun `el abono extraordinario sigue entre cuentas, sin signo`() {
        assertEquals(TonoDelMonto.ENTRE_CUENTAS, tonoDelRenglon(fila(abono), tipos))
        assertEquals("Abono extraordinario", transferRowTitle(fila(abono), tipos))
        assertEquals("\$2.000.000", textoDelMontoDeTraspaso(fila(abono), tipos))
    }

    /**
     * **El «Gastaste» del día es la suma de las filas con «−»**: suma la cuota y no el pago de tarjeta
     * ni el traspaso. Las dos cifras salen de la misma función.
     */
    @Test
    fun `el Gastaste del dia suma la cuota y no el pago de tarjeta`() {
        val items = listOf(cuota, pagoDeTarjeta, traspasoEntreCuentas).flatMap { listOf(it.first, it.second) }
        val resumen = resumenDelDia(items, tipos)
        assertEquals(980_000L, resumen.gastaste)
        assertEquals(RotuloDelDia.GASTASTE to 980_000L, cifraDelDia(resumen))

        val sumaDeLasFilasConMenos = collapseTransfers(items)
            .filter { tonoDelRenglon(it, tipos) == TonoDelMonto.GASTO }
            .sumOf { (it as MovementRow.Transfer).let { f -> pataQueSeMuestra(f, tipos).amount } }
        assertEquals(resumen.gastaste, sumaDeLasFilasConMenos)
    }

    @Test
    fun `en el chip Gastos la pata de la cuota queda suelta y sigue siendo un gasto`() {
        val enGastos = listOf(cuota.first, cuota.second).filter { matchesChip(it, CHIP_GASTOS) }
        assertEquals(listOf(cuota.first), enGastos)
        assertEquals(TonoDelMonto.GASTO, tonoDelEvento(enGastos.single()))
        assertEquals(CUOTA_CATEGORY, enGastos.single().category)
    }

    /** Buscar lista una pata por par: la que cuenta, aunque la del crédito venga primero. */
    @Test
    fun `Buscar muestra la cuota por la pata del dinero, aunque la del credito venga primero`() {
        val (dinero, deuda) = cuota
        val (tarjetaSale, tarjetaEntra) = pagoDeTarjeta
        val lista = unaPataPorPar(listOf(deuda, dinero, tarjetaEntra, tarjetaSale), tipos)
        assertEquals(listOf(dinero, tarjetaEntra), lista)
        assertEquals(TonoDelMonto.GASTO, tonoDelEvento(lista.first(), AccountType.SAVINGS))
        // Lo que no cuenta se queda con la primera pata, sin signo.
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(lista[1], AccountType.CREDIT_CARD))
    }
}
