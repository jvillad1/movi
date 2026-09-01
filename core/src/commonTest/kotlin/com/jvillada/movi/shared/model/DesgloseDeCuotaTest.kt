package com.jvillada.movi.shared.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Cuánto de una cuota baja de verdad la deuda.**
 *
 * Hasta esta ola, registrar una cuota le bajaba a la deuda el monto completo, interés incluido. En
 * los números reales del dueño eso son **$18.671.083 de deuda que desaparecían cada mes** y nunca
 * existieron: de $28.526.537 en cuotas mensuales, solo $9.855.454 abonan a capital.
 *
 * Las cifras de acá abajo no son inventadas para el test: son sus créditos, con el saldo y la tasa
 * que tienen hoy, y los capitales esperados salieron de sus extractos. Un test con montos redondos
 * habría pasado igual con la fórmula equivocada (`tasa/12` en vez de la mensual equivalente);
 * estos no.
 */
class DesgloseDeCuotaTest {

    private fun deUnCredito(cuota: Long, saldo: Long, rateEa: Double, seguro: Long? = null) =
        desglosarCuota(cuota, AccountType.LOAN, saldo, rateEa, seguro)

    // ── Los créditos reales del dueño ──────────────────────────────────────────

    @Test
    fun el_credito_del_vehiculo_abona_solo_su_capital() {
        // Cuota $4.215.223 sobre un saldo de $177.200.000 al 18,16 % E.A.
        val d = deUnCredito(cuota = 4_215_223, saldo = 177_200_000, rateEa = 18.16)

        assertEquals(2_481_318L, d.interes)
        assertEquals(1_733_905L, d.capital, "la deuda baja el capital, no la cuota")
        assertEquals(MotivoDelDesglose.AMORTIZA, d.motivo)
    }

    @Test
    fun la_libranza_4818_tambien() {
        // El segundo crédito más caro: $6.040.259 de cuota sobre $262.386.162 al 18,01 % E.A.
        val d = deUnCredito(cuota = 6_040_259, saldo = 262_386_162, rateEa = 18.01)

        assertEquals(3_646_011L, d.interes)
        assertEquals(2_394_248L, d.capital)
    }

    @Test
    fun la_hipoteca_de_768_millones_da_el_peso_exacto() {
        // El crédito más grande, y el que más lejos está de cualquier número redondo. Las tres
        // cifras clavadas: si alguien toca la fórmula o el redondeo, este test lo dice con el peso.
        val d = deUnCredito(cuota = 9_147_408, saldo = 768_430_394, rateEa = 10.98)

        assertEquals(6_700_288L, d.interes)
        assertEquals(2_447_120L, d.capital)
        assertEquals(d.cuota, d.interes + d.seguro + d.capital, "las tres partes suman la cuota")
    }

    @Test
    fun el_seguro_de_vida_deudor_tampoco_baja_la_deuda() {
        // El libre inversión ·9695: la cuota son $1.177.748 de capital+interés MÁS $108.800 de
        // Seguro Vida Deudor. Sin el campo `insuranceMonthly`, esos $108.800 se contaban como
        // capital y la deuda bajaba de más todos los meses.
        val conSeguro = deUnCredito(cuota = 1_286_548, saldo = 41_093_905, rateEa = 11.27, seguro = 108_800)
        val sinDeclararlo = deUnCredito(cuota = 1_286_548, saldo = 41_093_905, rateEa = 11.27)

        // **El capital, fijado al peso, y no solo la diferencia entre los dos.** Que la diferencia
        // sea $108.800 se sigue de la resta y sería cierto aunque el interés estuviera mal: es una
        // identidad algebraica, no una medición. Lo que hay que clavar es la cifra que se le va a
        // escribir a la deuda.
        assertEquals(108_800L, conSeguro.seguro)
        assertEquals(367_332L, conSeguro.interes, "interés del mes sobre \$41.093.905 al 11,27 % E.A.")
        assertEquals(810_416L, conSeguro.capital, "1.286.548 − 367.332 − 108.800")
        assertEquals(conSeguro.cuota, conSeguro.interes + conSeguro.seguro + conSeguro.capital)
        assertEquals(919_216L, sinDeclararlo.capital, "sin declararlo, el seguro se cuenta como capital")
        assertEquals(
            108_800L,
            sinDeclararlo.capital - conSeguro.capital,
            "declarar el seguro tiene que bajar el capital exactamente por el seguro",
        )
    }

    // ── La fórmula ─────────────────────────────────────────────────────────────

    @Test
    fun la_tasa_mensual_NO_es_la_anual_dividida_por_doce() {
        // El error clásico, y caro: dividir la E.A. por 12 da una mensual más alta que la real
        // (para 18,16 % daría 1,513 % en vez de 1,400 %) y le inventaría al dueño interés de más
        // en cada cuota. Se comparan las DOS fórmulas sobre su saldo real.
        val correcta = tasaMensualDeUnaEA(18.16)
        val ingenua = 18.16 / 100.0 / 12.0

        assertTrue(correcta < ingenua, "la mensual equivalente es MENOR que la anual sobre 12")
        val deMas = (177_200_000 * (ingenua - correcta)).toLong()
        assertTrue(deMas > 150_000L, "la diferencia sobre su crédito del carro son cientos de miles: $deMas")
    }

    @Test
    fun capitalizar_doce_veces_la_mensual_devuelve_la_anual() {
        // La propiedad que DEFINE la conversión, y la única forma de fijarla sin repetir la
        // fórmula dentro del assert (que sería compararla consigo misma).
        var acumulado = 1.0
        repeat(12) { acumulado *= (1.0 + tasaMensualDeUnaEA(17.46)) }

        assertTrue(abs(acumulado - 1.1746) < 0.000_000_1, "12 meses componen la E.A.: $acumulado")
    }

    // ── Los bordes ─────────────────────────────────────────────────────────────

    @Test
    fun sin_terminos_la_deuda_baja_por_todo_y_se_avisa() {
        // Un crédito que el dueño anotó sin condiciones: no hay con qué separar el interés. Se
        // conserva el comportamiento de siempre —la deuda baja por el monto completo— pero el
        // motivo lo dice, y de ahí la pantalla saca el aviso. Inventar un interés plausible acá
        // sería el mismo error que esta ola vino a matar, con otro disfraz.
        val d = desglosarCuota(1_000_000, AccountType.LOAN, 50_000_000, rateEa = null, seguroMensual = null)

        assertEquals(MotivoDelDesglose.SIN_TASA, d.motivo)
        assertEquals(1_000_000L, d.capital)
        assertEquals(0L, d.interes)
    }

    @Test
    fun tasa_en_cero_es_lo_mismo_que_no_tener_tasa() {
        // `rate_ea = 0` existe de verdad en su base: el «Crédito Techo Gardenera» que le prestó el
        // papá se cargó así. Un 0 no es una tasa, es un dato que falta.
        val d = deUnCredito(cuota = 10_000_000, saldo = 10_000_000, rateEa = 0.0)

        assertEquals(MotivoDelDesglose.SIN_TASA, d.motivo)
        assertEquals(10_000_000L, d.capital)
    }

    @Test
    fun pagar_una_tarjeta_baja_la_deuda_por_TODO_lo_pagado() {
        // Y eso ya era correcto: los intereses de una tarjeta se causan como un movimiento aparte,
        // no escondidos adentro del pago. Este test existe para que nadie "arregle" la tarjeta por
        // simetría con el crédito — se le pasan tasa y seguro a propósito, y tiene que ignorarlos.
        val d = desglosarCuota(1_008_902, AccountType.CREDIT_CARD, 19_818_701, rateEa = 32.0, seguroMensual = 50_000)

        assertEquals(MotivoDelDesglose.TARJETA, d.motivo)
        assertEquals(1_008_902L, d.capital)
        assertEquals(0L, d.interes, "no se le inventa un interés a una tarjeta")
    }

    @Test
    fun una_cuota_que_es_toda_interes_no_baja_nada_y_tampoco_sube_la_deuda() {
        // Caso real: la libranza ·4818 arrancó con cuotas 100 % interés (desembolso de
        // $257.000.000 y saldo de $262.386.162 dos meses después). El capital se planta en 0: la
        // deuda no baja, pero esta puerta tampoco la SUBE — que suba es un hecho del banco y se
        // anota con «Ajustar saldo».
        val d = deUnCredito(cuota = 3_000_000, saldo = 262_386_162, rateEa = 18.01)

        assertEquals(0L, d.capital)
        assertTrue(d.interes > d.cuota, "el interés del mes supera lo pagado: ${d.interes}")
    }

    @Test
    fun un_credito_pagado_de_mas_no_causa_intereses() {
        // Saldo negativo = abonó de más. Un interés negativo le habría SUBIDO el capital abonado.
        val d = deUnCredito(cuota = 500_000, saldo = -1_200_000, rateEa = 18.16)

        assertEquals(0L, d.interes)
        assertEquals(500_000L, d.capital)
    }
}
