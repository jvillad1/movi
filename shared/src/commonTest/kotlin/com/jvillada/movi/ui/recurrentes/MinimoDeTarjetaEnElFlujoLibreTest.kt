package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que la tarjeta te obliga a pagar sale del disponible, sin volverse un gasto del mes.**
 *
 * El problema, con sus números: Movi le decía **$601.574 libres al mes**. El mínimo del extracto
 * de su Master Black son **$1.843.014**, o sea que lo que de verdad le queda son **−$1.241.440**.
 * Veía una cifra positiva donde tenía que haber una alarma, y no por un error de suma: la regla
 * de la tarjeta estaba deliberadamente fuera del total, porque su monto es la DEUDA
 * ([RecurringRule.montoEsSaldo]) y sumarla habría sido peor.
 *
 * ### La tensión que este archivo fija
 *
 * El pago de una tarjeta **no es un gasto del mes** —las compras ya contaron cuando se hicieron, y
 * por eso `CARD_PAYMENT_CATEGORY` está fuera de `isCashFlow` y [cuentaComoCompromisoMensual] deja
 * afuera la regla `card_*`—. Esa regla no se toca: el mínimo **no entra a `gastos`**. Entra como
 * una **deducción rotulada aparte**, que es lo único que da la alarma sin contar dos veces la
 * misma plata. Las dos mitades se prueban acá, y la primera es la que se rompe sola si alguien
 * «simplifica» esto mañana.
 */
class MinimoDeTarjetaEnElFlujoLibreTest {

    private fun tarjeta(cuenta: String, saldo: Long, minimo: Long? = null) = RecurringRule(
        id = "$CARD_RULE_PREFIX$cuenta",
        name = "Pago tarjeta $cuenta",
        category = "Créditos",
        amount = saldo,
        dayOfMonth = 25,
        type = TransactionType.EXPENSE,
        montoEsSaldo = true,
        pagoMinimoCop = minimo,
    )

    private fun cuota(cuenta: String, monto: Long) = RecurringRule(
        id = "$CREDIT_RULE_PREFIX$cuenta", name = "Cuota $cuenta", category = "Créditos",
        amount = monto, dayOfMonth = 1, type = TransactionType.EXPENSE,
    )

    private fun regla(nombre: String, monto: Long, tipo: TransactionType) = RecurringRule(
        id = "rr_$nombre", name = nombre, category = "Otros", amount = monto,
        dayOfMonth = 5, type = tipo,
    )

    private val sinSuscripciones = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

    /** Ingresos − gastos = $601.574, que es lo que la pantalla le decía. */
    private val sueldo = regla("Sueldo", 12_000_000L, TransactionType.INCOME)
    private val vehiculo = cuota("Vehículo 8761", 4_101_123L)
    private val libreInversion = cuota("Libre inversión 9695", 1_204_064L)
    private val arriendo = regla("Arriendo", 6_093_239L, TransactionType.EXPENSE)
    private val loQueYaContaba = listOf(sueldo, vehiculo, libreInversion, arriendo)

    /** Master Black: debe $27.647.837 y el extracto le exige $1.843.014 este mes. */
    private val masterBlack = tarjeta("Master Black", saldo = 27_647_837L, minimo = 1_843_014L)

    private val FLUJO_LIBRE = 601_574L

    // ── La cifra que cambia ──────────────────────────────────────────────────

    @Test
    fun `el minimo del master black convierte 601574 libres en 1241440 en rojo`() {
        val r = resumenRecurrentes(loQueYaContaba + masterBlack, sinSuscripciones)

        assertEquals(FLUJO_LIBRE, r.flujoLibre, "lo recurrente contra lo recurrente no cambió")
        assertEquals(1_843_014L, r.minimosDeTarjeta)
        assertEquals(-1_241_440L, r.disponible)
        assertEquals(0, r.tarjetasSinMinimo)
    }

    /**
     * **Y `gastos` no se movió ni un peso.** Es la mitad que sostiene la regla del repo: si el
     * mínimo se hubiera metido en «Gastos recurrentes», la compra con tarjeta y su pago contarían
     * los dos, y el número saldría bien por casualidad mientras el desglose mentiría.
     */
    @Test
    fun `el minimo no se cuela en los gastos del mes`() {
        val con = resumenRecurrentes(loQueYaContaba + masterBlack, sinSuscripciones)
        val sin = resumenRecurrentes(loQueYaContaba, sinSuscripciones)

        assertEquals(sin.gastos, con.gastos, "el pago de una tarjeta no es gasto del mes")
        assertEquals(sin.flujoLibre, con.flujoLibre)
        assertEquals(0, con.items.size - sin.items.size, "ni entra al inventario de recurrentes")
        // La regla de la tarjeta sigue afuera del total por donde siempre estuvo.
        assertFalse(cuentaComoCompromisoMensual(masterBlack))
    }

    // ── Cuando el dato falta ─────────────────────────────────────────────────

    /**
     * **Sin el mínimo cargado, la cifra vuelve a ser $601.574 — y la pantalla deja de afirmarla.**
     * Es el estado real de sus cinco tarjetas hoy, y el motivo por el que el campo es nullable en
     * vez de estimarse con el 5 % del saldo.
     */
    @Test
    fun `una tarjeta con deuda y sin minimo no baja la cifra pero la marca`() {
        val sinCargar = tarjeta("Master Black", saldo = 27_647_837L, minimo = null)
        val r = resumenRecurrentes(loQueYaContaba + sinCargar, sinSuscripciones)

        assertEquals(0L, r.minimosDeTarjeta, "no se inventa un 5 %")
        assertEquals(FLUJO_LIBRE, r.disponible)
        assertEquals(1, r.tarjetasSinMinimo)
        assertEquals(
            "Falta el pago mínimo de 1 tarjeta con deuda: esta cifra es lo más que te podría " +
                "quedar, no lo que te queda. Ese dato está en tu extracto y se carga en Créditos, " +
                "con el lápiz de la tarjeta.",
            avisoDeMinimosQueFaltan(r),
        )
    }

    @Test
    fun `con dos tarjetas sin minimo lo dice en plural`() {
        val r = resumenRecurrentes(
            loQueYaContaba + tarjeta("Master Black", 27_647_837L) + tarjeta("AMEX 9208", 19_818_701L),
            sinSuscripciones,
        )

        assertEquals(2, r.tarjetasSinMinimo)
        assertTrue(avisoDeMinimosQueFaltan(r)!!.contains("2 tarjetas con deuda"))
    }

    /** Con todo cargado no hay nada que avisar: un aviso permanente se vuelve decorado. */
    @Test
    fun `sin tarjetas o con todas cargadas no hay aviso`() {
        assertNull(avisoDeMinimosQueFaltan(resumenRecurrentes(loQueYaContaba, sinSuscripciones)))
        assertNull(avisoDeMinimosQueFaltan(resumenRecurrentes(loQueYaContaba + masterBlack, sinSuscripciones)))
    }

    /**
     * **Solo llegan las tarjetas CON deuda** (`loadCardRulePairs` no fabrica regla para una en
     * $0), así que el AMEX ·9208, Nu y Davivienda ·9418 —los tres en $0 hoy— no piden un dato que
     * no le hace falta a nadie. Se fija acá porque este contador es lo único que decide si la
     * pantalla dice «te faltan datos», y un contador que gritara por tres tarjetas al día se
     * apagaría solo en la cabeza del dueño.
     */
    @Test
    fun `una tarjeta en cero no llega y por lo tanto no pide nada`() {
        val r = resumenRecurrentes(loQueYaContaba, sinSuscripciones)
        assertEquals(0, r.tarjetasSinMinimo)
        assertEquals(FLUJO_LIBRE, r.disponible)
    }

    // ── Mezclas y bordes ─────────────────────────────────────────────────────

    /** Dos tarjetas, una cargada y otra no: se descuenta lo que se sabe y se avisa lo que falta. */
    @Test
    fun `los minimos se suman entre tarjetas y lo que falta se cuenta aparte`() {
        val r = resumenRecurrentes(
            loQueYaContaba + masterBlack + tarjeta("Visa", 3_000_000L, minimo = 150_000L) +
                tarjeta("AMEX 9208", 19_818_701L),
            sinSuscripciones,
        )

        assertEquals(1_993_014L, r.minimosDeTarjeta)
        assertEquals(FLUJO_LIBRE - 1_993_014L, r.disponible)
        assertEquals(1, r.tarjetasSinMinimo)
    }

    /**
     * El subtítulo dice de qué está hecha la cifra grande, y cambia con ella: si no, el dueño suma
     * los dos números del desglose y le da otra cosa. Una cifra que no cuadra con su propio
     * desglose es la forma más rápida de que deje de creerle a la pantalla.
     */
    @Test
    fun `el subtitulo nombra los minimos solo cuando hay minimos`() {
        assertEquals(
            "Ingresos − Gastos recurrentes − Mínimos de tarjeta",
            subtituloDelFlujoLibre(resumenRecurrentes(loQueYaContaba + masterBlack, sinSuscripciones)),
        )
        assertEquals(
            "Ingresos recurrentes − Gastos recurrentes",
            subtituloDelFlujoLibre(resumenRecurrentes(loQueYaContaba, sinSuscripciones)),
        )
    }

    /**
     * **Un crédito no tiene mínimo, y su cuota no puede colarse por esta puerta.** El campo viaja
     * en `RecurringRule`, que es el mismo tipo para las dos clases de regla sintética; si el
     * filtro mirara algo que no sea el prefijo `card_`, una cuota de crédito se restaría dos veces
     * — una en `gastos` y otra acá.
     */
    @Test
    fun `una cuota de credito no aporta a los minimos aunque traiga el campo`() {
        val cuotaRara = vehiculo.copy(pagoMinimoCop = 999_999L)
        val r = resumenRecurrentes(listOf(sueldo, cuotaRara), sinSuscripciones)

        assertEquals(0L, r.minimosDeTarjeta)
        assertEquals(0, r.tarjetasSinMinimo)
        assertEquals(r.flujoLibre, r.disponible)
    }

    /** Sin ninguna regla el resumen no inventa nada, ni siquiera un cero con aviso. */
    @Test
    fun `sin reglas todo queda en cero y sin aviso`() {
        val r = resumenRecurrentes(emptyList(), sinSuscripciones)
        assertEquals(0L, r.minimosDeTarjeta)
        assertEquals(0, r.tarjetasSinMinimo)
        assertEquals(0L, r.disponible)
        assertNull(avisoDeMinimosQueFaltan(r))
    }
}
