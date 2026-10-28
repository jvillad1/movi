package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Un aviso que pagó varias cosas
 *
 * El caso del dueño, con cifras de prueba: un giro de $4.273.333 al papá cubre la cuota de la
 * Libranza ($1.900.000) y la de la Hipoteca ($2.350.000), y sobran $23.333.
 */
class ElAvisoEnPartesTest {

    private fun cuota(cuenta: String, nombre: String, monto: Long, asociado: Boolean = true) = PagoParaRepartir(
        ruleId = "$CREDIT_RULE_PREFIX$cuenta", nombre = nombre, monto = monto, categoria = CUOTA_CATEGORY,
        periodo = "2026-10", vence = "2026-10-27", cuentaDeLaDeudaId = cuenta, asociado = asociado,
    )

    private val libranza = cuota("acc_libranza", "Cuota Libranza Papá", 1_900_000L)
    private val hipoteca = cuota("acc_hipoteca", "Cuota Hipoteca Papá", 2_350_000L)
    private val giro = 4_273_333L

    private fun propuesta(monto: Long = giro, pagos: List<PagoParaRepartir> = listOf(libranza, hipoteca), exacto: List<String> = emptyList(), recordado: RepartoRecordado? = null) =
        PropuestaDeReparto(avisoId = "sms", monto = monto, destinoId = "dst_papa", destinoNombre = "Papá", pagos = pagos, exacto = exacto, recordado = recordado)

    // ── Faltan / te pasaste ──────────────────────────────────────────────────────────────────

    @Test
    fun `faltan, te pasaste y cuadra`() {
        assertEquals(CuadreDeLasPartes.Faltan(23_333L), cuadreDeLasPartes(giro, listOf(1_900_000L, 2_350_000L)))
        assertEquals(CuadreDeLasPartes.TePasaste(76_667L), cuadreDeLasPartes(giro, listOf(1_900_000L, 2_450_000L)))
        assertEquals(CuadreDeLasPartes.Cuadra, cuadreDeLasPartes(giro, listOf(1_900_000L, 2_350_000L, 23_333L)))
        // Un monto vacío (cero) cuenta como nada, no resta.
        assertEquals(CuadreDeLasPartes.Faltan(giro), cuadreDeLasPartes(giro, listOf(0L, -5L)))
    }

    @Test
    fun `se confirma solo con dos o mas partes completas que cuadran`() {
        val a = ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_libranza", monto = 1_900_000L)
        val b = ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_hipoteca", monto = 2_350_000L)
        val resto = ParteElegida(QueFueLaParte.A_UN_TERCERO, categoria = "Familia", nombre = "Transferencia a Papá", monto = 23_333L)
        assertTrue(sePuedenConfirmar(giro, listOf(a, b, resto)))
        assertFalse(sePuedenConfirmar(giro, listOf(a, b)), "faltan \$23.333")
        assertFalse(sePuedenConfirmar(giro, listOf(a, b, resto.copy(categoria = null))), "la del papá sin categoría")
        assertFalse(sePuedenConfirmar(giro, listOf(a, b.copy(destinoId = null), resto)), "una cuota sin crédito")
        assertFalse(sePuedenConfirmar(giro, listOf(a.copy(monto = giro))), "una sola parte no es un reparto")
    }

    @Test
    fun `el resto como una parte mas, o sumado a la ultima si es la misma`() {
        val a = ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_libranza", monto = 1_900_000L)
        val b = ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_hipoteca", monto = 2_350_000L)
        val alPapa = ParteElegida(QueFueLaParte.A_UN_TERCERO, categoria = "Familia", nombre = "Transferencia a Papá")
        val con = assertNotNull(conElResto(giro, listOf(a, b), alPapa))
        assertEquals(listOf(a, b, alPapa.copy(monto = 23_333L)), con)
        // Ya cuadra: no hay resto.
        assertNull(conElResto(giro, con, alPapa))
        // Si la última ya es al papá, se le suma.
        val conMenos = listOf(a, b.copy(monto = 2_300_000L), alPapa.copy(monto = 23_333L))
        assertEquals(listOf(a, b.copy(monto = 2_300_000L), alPapa.copy(monto = 73_333L)), conElResto(giro, conMenos, alPapa))
    }

    // ── La propuesta de entrada ──────────────────────────────────────────────────────────────

    @Test
    fun `dos cuotas y un sobrante - se proponen las cuotas y el resto al papa`() {
        val r = assertNotNull(repartoDeEntrada(propuesta(), origenId = "acc_ahorros", nombreDelTercero = "Transferencia a Papá", categoriaDelAviso = "Familia"))
        assertEquals(DeDondeSaleElReparto.LAS_CUOTAS, r.deDonde)
        assertEquals(23_333L, r.sobrante)
        assertEquals(setOf(libranza, hipoteca), r.pagos.toSet())
        assertEquals(giro, r.partes.sumOf { it.monto })
        val (cuotas, resto) = r.partes.partition { it.tipo == QueFueLaParte.CUOTA }
        assertEquals(setOf("acc_libranza", "acc_hipoteca"), cuotas.map { it.destinoId }.toSet())
        assertTrue(cuotas.all { it.ruleId.startsWith(CREDIT_RULE_PREFIX) && it.periodo == "2026-10" })
        assertEquals(ParteElegida(QueFueLaParte.A_UN_TERCERO, destinoId = "dst_papa", categoria = "Familia", nombre = "Transferencia a Papá", monto = 23_333L), resto.single())
        assertTrue(sePuedenConfirmar(giro, r.partes))
    }

    @Test
    fun `sin nombre del tercero, el sobrante es un gasto con la categoria leida`() {
        val r = assertNotNull(repartoDeEntrada(propuesta(), origenId = null, nombreDelTercero = null, categoriaDelAviso = "Otros"))
        assertEquals(ParteElegida(QueFueLaParte.GASTO, categoria = "Otros", monto = 23_333L), r.partes.last())
    }

    @Test
    fun `una sola cuota no se propone`() {
        assertNull(repartoDeEntrada(propuesta(pagos = listOf(libranza)), "acc_ahorros", "Transferencia a Papá", "Familia"))
        // Ni con la otra cuota pero que no cabe en el giro.
        assertNull(repartoDeEntrada(propuesta(monto = 2_400_000L), "acc_ahorros", "Transferencia a Papá", "Familia"))
    }

    @Test
    fun `ninguna - sin cuotas, o sobra demasiado`() {
        assertNull(repartoDeEntrada(propuesta(pagos = emptyList()), "acc_ahorros", null, null))
        // Sobra más del 5 %: que esas cuotas expliquen el giro ya es adivinar.
        assertNull(repartoDeEntrada(propuesta(monto = 5_000_000L), "acc_ahorros", null, null))
        // Un recurrente común no entra a «las cuotas que cubre».
        val mercado = PagoParaRepartir("rr-mercado", "Mercado", 2_350_000L, "Mercado", "2026-10", "2026-10-27", asociado = true)
        assertNull(repartoDeEntrada(propuesta(pagos = listOf(libranza, mercado)), "acc_ahorros", null, null))
    }

    @Test
    fun `entre dos combinaciones con la misma sobra no adivina, y las asociadas van primero`() {
        val otra = cuota("acc_otra", "Cuota Otra", 1_900_000L)
        // Libranza+Hipoteca y Otra+Hipoteca suman lo mismo.
        assertNull(cuotasQueCubre(giro, listOf(libranza, hipoteca, otra)))
        // Si la otra no es del papá, gana la combinación de las del papá.
        assertEquals(setOf(libranza, hipoteca), cuotasQueCubre(giro, listOf(libranza, hipoteca, otra.copy(asociado = false)))?.toSet())
        // Sin ninguna asociada, igual se busca entre todas.
        val sinAsociar = cuotasQueCubre(giro, listOf(libranza.copy(asociado = false), hipoteca.copy(asociado = false)))
        assertEquals(setOf(libranza.ruleId, hipoteca.ruleId), sinAsociar?.map { it.ruleId }?.toSet())
    }

    @Test
    fun `la suma exacta de #447 sigue proponiendose`() {
        val r = assertNotNull(repartoDeEntrada(propuesta(monto = 4_250_000L, exacto = listOf(libranza.ruleId, hipoteca.ruleId)), "acc_ahorros", null, null))
        assertEquals(DeDondeSaleElReparto.SUMA_EXACTA, r.deDonde)
        assertEquals(0L, r.sobrante)
        assertEquals(2, r.partes.size)
    }

    @Test
    fun `el reparto de la ultima vez gana, con la diferencia en la ultima parte`() {
        val recordado = RepartoRecordado(
            origenId = "acc_ahorros",
            monto = 4_260_000L,
            partes = listOf(
                ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_libranza", monto = 1_900_000L, ruleId = libranza.ruleId),
                ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_hipoteca", monto = 2_350_000L, ruleId = "credit_ya_pagado"),
                ParteElegida(QueFueLaParte.A_UN_TERCERO, categoria = "Familia", nombre = "Transferencia a Papá", monto = 10_000L),
            ),
        )
        val r = assertNotNull(repartoDeEntrada(propuesta(recordado = recordado), "acc_ahorros", null, null))
        assertEquals(DeDondeSaleElReparto.LA_ULTIMA_VEZ, r.deDonde)
        assertEquals(listOf(1_900_000L, 2_350_000L, 23_333L), r.partes.map { it.monto })
        // El pago de este período se vuelve a buscar: uno que ya no está pendiente queda sin pago.
        assertEquals("2026-10", r.partes[0].periodo)
        assertEquals("", r.partes[1].ruleId)
        // Otra cuenta de origen, u otro monto: no es «la misma vez».
        assertEquals(DeDondeSaleElReparto.LAS_CUOTAS, repartoDeEntrada(propuesta(recordado = recordado), "acc_nu", null, null)?.deDonde)
        assertEquals(DeDondeSaleElReparto.LAS_CUOTAS, repartoDeEntrada(propuesta(recordado = recordado.copy(monto = 9_000_000L)), "acc_ahorros", null, null)?.deDonde)
    }

    @Test
    fun `montos parecidos con el diez por ciento`() {
        assertTrue(montosParecidos(4_273_333L, 4_260_000L))
        assertTrue(montosParecidos(100L, 110L))
        assertFalse(montosParecidos(100L, 112L))
        assertFalse(montosParecidos(0L, 0L))
    }

    // ── Lo que viaja ─────────────────────────────────────────────────────────────────────────

    private val ahorros = Account("acc_ahorros", "Ahorros", AccountType.SAVINGS, 0L, "COP")

    @Test
    fun `cada parte viaja con su forma - dos patas o un gasto, con el texto del aviso`() {
        var n = 0
        fun id(prefijo: String) = "${prefijo}_${n++}"
        val cuota = aParteDelReparto(
            ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_libranza", nombre = "Cuota Libranza Papá", monto = 1_900_000L, ruleId = libranza.ruleId, periodo = "2026-10"),
            ahorros, 1_000L, "Transferiste a la cuenta *0136", "Papá", EventSource.SMS, ::id,
        )
        val patas = assertNotNull(cuota.patas)
        assertEquals(OperacionDelAviso.CUOTA, patas.operacion)
        assertEquals("acc_ahorros" to "acc_libranza", patas.origenId to patas.destinoId)
        assertEquals(1_900_000L, patas.monto)
        assertNull(cuota.evento)
        assertEquals(QueFueLaParte.CUOTA, cuota.tipo)

        val alPapa = aParteDelReparto(
            ParteElegida(QueFueLaParte.A_UN_TERCERO, categoria = "Familia", nombre = "Transferencia a Papá", monto = 23_333L),
            ahorros, 1_000L, "Transferiste a la cuenta *0136", "Papá", EventSource.SMS, ::id,
        )
        val evento = assertNotNull(alPapa.evento)
        assertEquals(TransactionType.EXPENSE, evento.type)
        assertEquals("Transferencia a Papá", evento.description)
        assertEquals("Transferencia a Papá", evento.merchant)
        assertEquals("Transferiste a la cuenta *0136", evento.rawPayload)
        assertEquals("", alPapa.ruleId)

        // Y lo que se recuerda es lo que se eligió, sin el período.
        assertEquals(
            ParteElegida(QueFueLaParte.CUOTA, destinoId = "acc_libranza", nombre = "Cuota Libranza Papá", monto = 1_900_000L, ruleId = libranza.ruleId),
            parteParaRecordar(cuota),
        )
        assertEquals(ParteElegida(QueFueLaParte.A_UN_TERCERO, categoria = "Familia", nombre = "Transferencia a Papá", monto = 23_333L), parteParaRecordar(alPapa))
    }

    @Test
    fun `nombres para la pregunta`() {
        assertEquals("Libranza Papá", nombreSinCuota("Cuota Libranza Papá"))
        assertEquals("Hipoteca", nombreSinCuota("Cuota de Hipoteca"))
        assertEquals("Libranza Papá y Hipoteca Papá", enumerar(listOf("Libranza Papá", "Hipoteca Papá")))
        assertEquals("A, B y C", enumerar(listOf("A", "B", "C")))
    }
}
