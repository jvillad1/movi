package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.avanceDelChecklist
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.ingresosPendientes
import com.jvillada.movi.ui.dashboard.lineaDeLoQueFalta
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.pieDeLoYaPagado
import com.jvillada.movi.ui.dashboard.yaMarcados
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El checklist del período: qué entra, cómo se agrupa, cuánto suma y qué dice.
 *
 * El reclamo que abrió esto, con las palabras del dueño: *«En Inicio veo donde dice Pagos del
 * período y solo muestra los faltantes, no muestra todos … quiero ver los recurrentes también tipo
 * checklist del mes, con valor y fecha para ser realizado»*. Las dos mitades se prueban acá: que lo
 * ya pagado vuelva a la lista (antes se caía sola, ver `checklistDelPeriodo`) y que la tarjeta del
 * Inicio diga que lo suyo es solo lo que falta.
 */
class ChecklistDelPeriodoTest {

    private val mesDeCalendario = PeriodSettings()
    private val corte25 = PeriodSettings(cutoffDay = 25)

    private fun regla(
        id: String,
        nombre: String,
        monto: Long,
        dia: Int,
        tipo: TransactionType = TransactionType.EXPENSE,
        saldo: Boolean = false,
        moneda: String = "COP",
    ) = RecurringRule(
        id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = dia,
        type = tipo, montoEsSaldo = saldo, currency = moneda,
    )

    private fun pago(rule: RecurringRule, vence: String, dias: Int) = UpcomingPayment(
        rule = rule,
        dueDate = vence,
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private fun ocurrencia(
        ruleId: String,
        vence: String,
        ocurrio: Boolean,
        derivada: Boolean = false,
    ) = OccurrenceState(
        ruleId = ruleId,
        period = vence.take(7),
        dueDate = vence,
        occurred = ocurrio,
        derivadaDeUnMovimiento = derivada,
    )

    // ── Qué entra al período ─────────────────────────────────────────────────

    /**
     * **El bug que el dueño vio.** Apenas se sella el arriendo de septiembre, `/api/payments/upcoming`
     * empieza a contestar con el vencimiento de OCTUBRE — así que mirando solo esa fecha, lo pagado
     * se caía del período y la tarjeta listaba nada más que lo pendiente, para siempre.
     */
    @Test
    fun lo_ya_pagado_sigue_en_el_periodo_aunque_su_vencimiento_haya_rodado() {
        val arriendo = regla("rr_arriendo", "Arriendo", 1_850_000, 5)

        val checklist = checklistDelPeriodo(
            upcoming = listOf(pago(arriendo, "2026-10-05", 15)),
            ocurrencias = listOf(ocurrencia("rr_arriendo", "2026-09-05", ocurrio = true)),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        )

        val fila = checklist.single()
        assertTrue(fila.pagado, "el sello de septiembre es de ESTE período")
        assertEquals("2026-09-05", fila.vence, "la fecha buena es la de la ocurrencia, no la rodada")
        assertEquals(-15, fila.diasParaVencer, "recalculada contra el mismo hoy del que salió la respuesta")
        assertFalse(fila.vencido, "lo pagado no está vencido, aunque su fecha ya pasó")
        assertEquals("2026-09", fila.periodoDelSello)
    }

    /**
     * La otra mitad del mismo rodado: el gimnasio del día 5, **sin marcar**, se le pasa la ventana
     * de gracia y su vencimiento vigente salta a octubre. Seguía debiéndose y desaparecía del mes.
     */
    @Test
    fun lo_vencido_pasada_la_gracia_sigue_contando_en_su_periodo() {
        val gimnasio = regla("rr_gym", "Gimnasio", 139_900, 5)

        val checklist = checklistDelPeriodo(
            upcoming = listOf(pago(gimnasio, "2026-10-05", 15)),
            ocurrencias = listOf(ocurrencia("rr_gym", "2026-09-05", ocurrio = false)),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        )

        val fila = checklist.single()
        assertFalse(fila.pagado)
        assertTrue(fila.vencido)
        assertEquals("venció hace 15 días", estadoDelChecklist(fila))
        assertTrue(fila.seMarca, "y se puede tildar: su ocurrencia está abierta")
    }

    @Test
    fun un_pago_que_vence_hoy_esta_pendiente_y_lo_dice() {
        val celular = regla("rr_cel", "Celular", 53_000, 12)

        val checklist = checklistDelPeriodo(
            upcoming = listOf(pago(celular, "2026-09-12", 0)),
            ocurrencias = listOf(ocurrencia("rr_cel", "2026-09-12", ocurrio = false)),
            periodo = periodoDeLaFecha("2026-09-12", mesDeCalendario)!!,
            settings = mesDeCalendario,
        )

        val fila = checklist.single()
        assertEquals(0, fila.diasParaVencer)
        assertFalse(fila.vencido, "vencer hoy todavía no es estar vencido")
        assertEquals("12 de septiembre · vence hoy", subtituloDeLaFila(fila))
    }

    /**
     * Un pago que todavía no venció no tiene ocurrencia —el server no pregunta por algo que no
     * pasó— y por eso su casilla no se puede tildar: el POST lo rechazaría.
     */
    @Test
    fun lo_que_todavia_no_vencio_se_lista_pero_no_se_puede_tildar() {
        val colegio = regla("rr_colegio", "Colegio", 1_320_000, 20)

        val fila = checklistDelPeriodo(
            upcoming = listOf(pago(colegio, "2026-09-20", 8)),
            ocurrencias = emptyList(),
            periodo = periodoDeLaFecha("2026-09-12", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        assertFalse(fila.pagado)
        assertNull(fila.periodoDelSello)
        assertFalse(fila.seMarca)
    }

    /** La cuota de un crédito se da por pagada leyendo el movimiento: no hay sello que deshacer. */
    @Test
    fun una_cuota_pagada_por_su_movimiento_no_se_puede_destildar() {
        val cuota = regla("credit_1", "Cuota Vehículo", 4_101_123, 10)

        val fila = checklistDelPeriodo(
            upcoming = listOf(pago(cuota, "2026-10-10", 20)),
            ocurrencias = listOf(ocurrencia("credit_1", "2026-09-10", ocurrio = true, derivada = true)),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        assertTrue(fila.pagado)
        assertTrue(fila.derivado)
        assertFalse(fila.seMarca, "destildarla contestaría 404: el control estaría muerto")
    }

    /**
     * **El período del dueño cruza dos meses de calendario.** Con corte 25, «octubre» va del 25 de
     * septiembre al 24 de octubre: el pago del 28 de septiembre es de octubre y el del 20 no.
     */
    @Test
    fun con_corte_25_el_periodo_toma_dos_meses_de_calendario() {
        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                pago(regla("rr_1", "Internet", 120_000, 28), "2026-09-28", 2),
                pago(regla("rr_2", "Seguro", 90_000, 20), "2026-09-20", -6),
                pago(regla("rr_3", "Gas", 60_000, 10), "2026-10-10", 14),
            ),
            ocurrencias = emptyList(),
            periodo = periodoDeLaFecha("2026-09-26", corte25)!!,
            settings = corte25,
        )

        assertEquals(listOf("Internet", "Gas"), checklist.map { it.nombre })
    }

    // ── Agrupación, orden y totales ──────────────────────────────────────────

    /**
     * El orden y los tres grupos que pinta la sección, con lo que cada total puede decir.
     *
     * Un SALDO de tarjeta se muestra pero no suma: no es una cuota. Un INGRESO tampoco — «te falta
     * pagar» no puede incluir el sueldo del dueño, que fue justo lo que hacía la cifra del Inicio.
     */
    @Test
    fun los_tres_grupos_su_orden_y_lo_que_suma_cada_total() {
        val hoy = periodoDeLaFecha("2026-09-12", mesDeCalendario)!!
        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                pago(regla("rr_gym", "Gimnasio", 139_900, 20), "2026-09-20", 8),
                pago(regla("rr_arriendo", "Arriendo", 1_850_000, 5), "2026-10-05", 23),
                pago(regla("rr_cel", "Celular", 53_000, 10), "2026-09-10", -2),
                pago(regla("rr_sueldo", "Salario", 6_000_000, 15, TransactionType.INCOME), "2026-09-15", 3),
                pago(regla("card_1", "Master Black", 27_501_150, 18, saldo = true), "2026-09-18", 6),
            ),
            ocurrencias = listOf(ocurrencia("rr_arriendo", "2026-09-05", ocurrio = true)),
            periodo = hoy,
            settings = mesDeCalendario,
        )

        // Primero lo pendiente, y dentro de eso lo que vence antes; lo tildado al final.
        assertEquals(
            listOf("Celular", "Salario", "Master Black", "Gimnasio", "Arriendo"),
            checklist.map { it.nombre },
        )
        assertEquals(listOf("Celular", "Master Black", "Gimnasio"), pagosPendientes(checklist).map { it.nombre })
        assertEquals(listOf("Salario"), ingresosPendientes(checklist).map { it.nombre })
        assertEquals(listOf("Arriendo"), yaMarcados(checklist).map { it.nombre })

        // El saldo de la tarjeta y el sueldo se ven, pero no son plata que falte pagar.
        assertEquals(53_000 + 139_900, faltaPorPagar(checklist))
        // Y el avance cuenta pagos: el sueldo no es uno de ellos.
        assertEquals(1 to 4, avanceDelChecklist(checklist))
    }

    /** Una tarjeta en dólares no se dice en pesos: la moneda llega hasta el texto de la fila. */
    @Test
    fun el_saldo_de_una_tarjeta_en_dolares_se_dice_en_dolares() {
        val fila = checklistDelPeriodo(
            upcoming = listOf(
                pago(regla("card_usd", "Amex USD", 1_200, 18, saldo = true, moneda = "USD"), "2026-09-18", 6),
            ),
            ocurrencias = emptyList(),
            periodo = periodoDeLaFecha("2026-09-12", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        assertEquals("USD", fila.moneda)
        assertTrue(fila.montoEsSaldo)
        assertEquals("US\$1.200", textoDelMontoDelChecklist(fila))
        assertEquals(0, faltaPorPagar(listOf(fila)), "un saldo no es lo que falta pagar este mes")
    }

    @Test
    fun un_monto_en_pesos_se_dice_entero_y_no_abreviado() {
        val fila = PagoDelPeriodo("r", "Arriendo", 1_850_000, pagado = false, diasParaVencer = 3)
        assertEquals("\$1.850.000", textoDelMontoDelChecklist(fila))
    }

    // ── Lo que dice la tarjeta del Inicio ────────────────────────────────────

    @Test
    fun la_tarjeta_dice_que_lo_suyo_es_solo_lo_que_falta() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Celular", 53_000, pagado = false, diasParaVencer = -2),
            PagoDelPeriodo("r2", "Gimnasio", 139_900, pagado = false, diasParaVencer = 8),
            PagoDelPeriodo("r3", "Arriendo", 1_850_000, pagado = true, diasParaVencer = -15),
        )

        assertEquals("Te faltan 2 de 3 pagos de este período", lineaDeLoQueFalta(checklist))
        assertEquals("Ya marcaste 1. El checklist completo está en «Ver todos».", pieDeLoYaPagado(checklist))
    }

    @Test
    fun con_un_solo_pago_pendiente_habla_en_singular() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Celular", 53_000, pagado = false, diasParaVencer = -2),
            PagoDelPeriodo("r2", "Arriendo", 1_850_000, pagado = true, diasParaVencer = -15),
        )
        assertEquals("Te falta 1 de 2 pagos de este período", lineaDeLoQueFalta(checklist))
    }

    @Test
    fun sin_nada_pendiente_lo_celebra_y_no_manda_a_ninguna_parte() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Celular", 53_000, pagado = true, diasParaVencer = -2),
            PagoDelPeriodo("r2", "Arriendo", 1_850_000, pagado = true, diasParaVencer = -15),
        )
        assertEquals("Marcaste los 2 pagos de este período", lineaDeLoQueFalta(checklist))
        assertNull(pieDeLoYaPagado(checklist), "no hay nada escondido que valga la pena anunciar")
    }

    /** Un período con solo un sueldo anotado no tiene pagos: la línea no puede inventar ninguno. */
    @Test
    fun un_periodo_sin_pagos_lo_dice_sin_numeros() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Salario", 6_000_000, pagado = false, diasParaVencer = 3, esIngreso = true),
        )
        assertEquals("Este período no tiene pagos anotados", lineaDeLoQueFalta(checklist))
        assertEquals(0, faltaPorPagar(checklist))
    }

    // ── Los textos de una fila ───────────────────────────────────────────────

    @Test
    fun la_fila_dice_la_fecha_y_el_estado() {
        assertEquals("5 de septiembre", fechaLegibleDelChecklist("2026-09-05"))
        assertEquals("", fechaLegibleDelChecklist(""), "sin fecha entendible no se inventa ninguna")

        val pendiente = PagoDelPeriodo("r", "Gimnasio", 139_900, pagado = false, diasParaVencer = 1, vence = "2026-09-13")
        assertEquals("13 de septiembre · vence mañana", subtituloDeLaFila(pendiente))
        assertEquals("venció ayer", estadoDelChecklist(pendiente.copy(diasParaVencer = -1)))
        assertEquals("pagado", estadoDelChecklist(pendiente.copy(pagado = true)))
        assertEquals("recibido", estadoDelChecklist(pendiente.copy(pagado = true, esIngreso = true)))
    }
}
