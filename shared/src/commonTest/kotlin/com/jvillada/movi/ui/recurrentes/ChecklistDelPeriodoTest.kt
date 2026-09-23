package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.ui.dashboard.EstadoDeLaFila
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.avanceDelChecklist
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.ingresosPendientes
import com.jvillada.movi.ui.dashboard.lineaDeLoQueFalta
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.pieDeLoYaPagado
import com.jvillada.movi.ui.dashboard.tituloDeLosListos
import com.jvillada.movi.ui.dashboard.yaMarcados
import com.jvillada.movi.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
        cuenta: String? = null,
    ) = RecurringRule(
        id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = dia,
        type = tipo, montoEsSaldo = saldo, currency = moneda, accountId = cuenta,
    )

    private fun movimiento(id: String, monto: Long, nota: String = "Arriendo") = FinancialEvent(
        id = id,
        accountId = "acc_1",
        type = TransactionType.EXPENSE,
        amount = monto,
        category = "Vivienda",
        description = nota,
        source = EventSource.MANUAL,
        timestamp = 1_757_000_000_000,
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
        automatica: Boolean = false,
        eventId: String? = null,
        candidatos: List<FinancialEvent> = emptyList(),
        montoDelPago: Long? = null,
    ) = OccurrenceState(
        ruleId = ruleId,
        period = vence.take(7),
        dueDate = vence,
        occurred = ocurrio,
        derivadaDeUnMovimiento = derivada,
        automatica = automatica,
        eventId = eventId,
        candidates = candidatos,
        montoDelPago = montoDelPago,
        monedaDelPago = if (montoDelPago == null) null else "COP",
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
        assertEquals(
            EstadoDeLaFila.SIN_MOVIMIENTO,
            fila.estado,
            "su ocurrencia está abierta y Movi no le encontró nada: la fila ofrece anotarlo",
        )
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
        assertEquals(EstadoDeLaFila.AUN_NO_VENCE, fila.estado)
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
        assertEquals(EstadoDeLaFila.LISTO, fila.estado)
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
        assertEquals("Ya salió 1. El checklist completo está en «Ver todos».", pieDeLoYaPagado(checklist))
    }

    /** El pie en plural: dos o más pagos ya salieron, sin decir que el dueño los marcó. */
    @Test
    fun el_pie_habla_en_plural_con_mas_de_un_pago_ya_salido() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Celular", 53_000, pagado = false, diasParaVencer = -2),
            PagoDelPeriodo("r2", "Gimnasio", 139_900, pagado = true, diasParaVencer = -8),
            PagoDelPeriodo("r3", "Arriendo", 1_850_000, pagado = true, diasParaVencer = -15),
        )
        assertEquals("Ya salieron 2. El checklist completo está en «Ver todos».", pieDeLoYaPagado(checklist))
    }

    /**
     * Revisión final: el grupo de lo ya tildado lista pagos E ingresos, así que su título no
     * puede decir «salieron» ni contar solo los pagos. Con dos pagos tildados de tres y el sueldo
     * ya recibido, el grupo muestra TRES filas: el título tiene que decir 3, sobre las 4 del
     * checklist — no «Ya salieron · 2 de 3», que era lo que decía.
     */
    @Test
    fun el_grupo_de_lo_ya_tildado_cuenta_lo_que_lista_incluido_el_ingreso() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Celular", 53_000, pagado = false, diasParaVencer = -2),
            PagoDelPeriodo("r2", "Gimnasio", 139_900, pagado = true, diasParaVencer = -8),
            PagoDelPeriodo("r3", "Arriendo", 1_850_000, pagado = true, diasParaVencer = -15),
            PagoDelPeriodo("r4", "Sueldo", 9_000_000, pagado = true, diasParaVencer = -1, esIngreso = true),
        )

        assertEquals(3, yaMarcados(checklist).size, "el grupo lista los dos pagos y el sueldo")
        assertEquals("Listos · 3 de 4", tituloDeLosListos(checklist))
        // Las líneas que hablan de pagos siguen contando sin el ingreso: esas sí son de plata que sale.
        assertEquals("Te falta 1 de 3 pagos de este período", lineaDeLoQueFalta(checklist))
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
        assertEquals("Ya salieron los 2 pagos de este período", lineaDeLoQueFalta(checklist))
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
    }

    // ── Los tres estados de una fila, y que NINGUNA se tilde ─────────────────

    /**
     * **La regla que abrió esta ola, dicha como invariante.**
     *
     * El dueño: *«no me debería dejar hacer check sin que el movimiento asociado exista, y esto
     * debería ser read only»*. `PagoDelPeriodo` ya no tiene ningún `seMarca`, y esta prueba es la
     * guarda de que no vuelva por la ventana: sea cual sea el estado de una fila, lo único que
     * decide si está tildada es la EVIDENCIA, y una fila pendiente nunca puede pasar a tildada sin
     * que aparezca un movimiento.
     *
     * Las cinco situaciones posibles, en una sola pasada.
     */
    @Test
    fun ninguna_fila_se_tilda_sola_el_estado_lo_decide_la_evidencia() {
        val hoy = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!
        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                // (a) lo emparejó Movi sola
                pago(regla("rr_arriendo", "Arriendo", 1_850_000, 5), "2026-10-05", 15),
                // (b) hay dudas: dos candidatos
                pago(regla("rr_agua", "Agua", 58_000, 8), "2026-09-08", -12),
                // (c) Movi no encontró nada
                pago(regla("rr_gym", "Gimnasio", 139_900, 10), "2026-09-10", -10),
                // (d) sello viejo hecho a mano, sin movimiento
                pago(regla("rr_cel", "Celular", 53_000, 12), "2026-10-12", 22),
                // (e) todavía no vence
                pago(regla("rr_colegio", "Colegio", 1_320_000, 28), "2026-09-28", 8),
            ),
            ocurrencias = listOf(
                ocurrencia(
                    "rr_arriendo", "2026-09-05", ocurrio = true, derivada = true,
                    automatica = true, eventId = "ev_arriendo", montoDelPago = 1_850_000,
                ),
                ocurrencia(
                    "rr_agua", "2026-09-08", ocurrio = false,
                    candidatos = listOf(movimiento("ev_gas", 58_000), movimiento("ev_luz", 58_000)),
                ),
                ocurrencia("rr_gym", "2026-09-10", ocurrio = false),
                ocurrencia("rr_cel", "2026-09-12", ocurrio = true),
            ),
            periodo = hoy,
            settings = mesDeCalendario,
        ).associateBy { it.nombre }

        assertEquals(EstadoDeLaFila.LISTO, checklist.getValue("Arriendo").estado)
        assertEquals(EstadoDeLaFila.CON_DUDAS, checklist.getValue("Agua").estado)
        assertEquals(EstadoDeLaFila.SIN_MOVIMIENTO, checklist.getValue("Gimnasio").estado)
        assertEquals(EstadoDeLaFila.MARCADA_A_MANO, checklist.getValue("Celular").estado)
        assertEquals(EstadoDeLaFila.AUN_NO_VENCE, checklist.getValue("Colegio").estado)

        // Y lo tildado es exactamente lo que tiene algo detrás o un sello viejo — nunca lo que
        // Movi no pudo respaldar.
        assertTrue(checklist.getValue("Arriendo").pagado)
        assertTrue(checklist.getValue("Celular").pagado)
        assertFalse(checklist.getValue("Agua").pagado)
        assertFalse(checklist.getValue("Gimnasio").pagado)
        assertFalse(checklist.getValue("Colegio").pagado)
    }

    /**
     * **`automatica` y `candidatos` llegan hasta la fila.** Son los dos datos que el server agregó
     * al emparejar solo, y sin ellos la fila no puede ni decir de dónde salió el tilde ni ofrecer
     * el «no fue este» — que es la única salida cuando Movi dedujo mal.
     */
    @Test
    fun lo_que_movi_emparejo_solo_llega_entero_a_la_fila() {
        val fila = checklistDelPeriodo(
            upcoming = listOf(pago(regla("rr_arriendo", "Arriendo", 1_850_000, 5, cuenta = "acc_1"), "2026-10-05", 15)),
            ocurrencias = listOf(
                ocurrencia(
                    "rr_arriendo", "2026-09-05", ocurrio = true, derivada = true,
                    automatica = true, eventId = "ev_1", montoDelPago = 1_800_000,
                ),
            ),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        assertTrue(fila.automatica)
        assertEquals("ev_1", fila.eventId)
        assertEquals("Vivienda", fila.categoria)
        assertEquals("acc_1", fila.cuentaId)
        // El subtítulo lo dice con todas las letras, y con la plata: un abono parcial no se puede
        // esconder detrás de un «pagado».
        assertEquals(
            "5 de septiembre · Movi lo emparejó con un movimiento de \$1.800.000",
            subtituloDeLaFila(fila),
        )
    }

    /** Los candidatos viajan en orden y el primero es el que la fila va a mostrar. */
    @Test
    fun los_candidatos_viajan_a_la_fila_en_su_orden() {
        val fila = checklistDelPeriodo(
            upcoming = listOf(pago(regla("rr_agua", "Agua", 58_000, 8), "2026-09-08", -12)),
            ocurrencias = listOf(
                ocurrencia(
                    "rr_agua", "2026-09-08", ocurrio = false,
                    candidatos = listOf(movimiento("ev_gas", 58_000), movimiento("ev_luz", 61_000)),
                ),
            ),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        assertEquals(listOf("ev_gas", "ev_luz"), fila.candidatos.map { it.id })
        assertEquals("ev_gas", propuestaDeLaFila(fila, emptySet())?.id)
        // Decir «no fue este» sobre el primero deja el segundo a la vista, no la fila muda.
        assertEquals(
            "ev_luz",
            propuestaDeLaFila(fila, setOf(claveDescartada("rr_agua", "ev_gas")))?.id,
        )
        // Y rechazados los dos, la fila cae a «sin movimiento»: ofrece anotarlo.
        assertNull(
            propuestaDeLaFila(
                fila,
                setOf(claveDescartada("rr_agua", "ev_gas"), claveDescartada("rr_agua", "ev_luz")),
            ),
        )
    }

    /** Un sello viejo sin movimiento lo dice, que es la mitad de poder quitarlo. */
    @Test
    fun un_sello_viejo_sin_movimiento_lo_dice() {
        val fila = checklistDelPeriodo(
            upcoming = listOf(pago(regla("rr_cel", "Celular", 53_000, 12), "2026-10-12", 22)),
            ocurrencias = listOf(ocurrencia("rr_cel", "2026-09-12", ocurrio = true)),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        assertEquals(EstadoDeLaFila.MARCADA_A_MANO, fila.estado)
        assertEquals("12 de septiembre · marcado a mano, sin movimiento", subtituloDeLaFila(fila))
        assertEquals("2026-09", fila.periodoDelSello, "sin esto no habría qué borrar")
    }

    // ── «Anotar el movimiento» ───────────────────────────────────────────────

    /**
     * La salida que el dueño eligió para la fila sin evidencia: que el movimiento EXISTA. La hoja
     * se abre con los cinco datos puestos —y no por comodidad: la categoría y la cuenta son
     * exactamente lo que el server compara para volver a emparejarlo solo.
     */
    @Test
    fun anotar_el_movimiento_abre_la_hoja_con_los_datos_del_recurrente() {
        val fila = checklistDelPeriodo(
            upcoming = listOf(
                pago(regla("rr_gym", "Gimnasio", 139_900, 10, cuenta = "acc_1"), "2026-09-10", -10),
            ),
            ocurrencias = listOf(ocurrencia("rr_gym", "2026-09-10", ocurrio = false)),
            periodo = periodoDeLaFecha("2026-09-20", mesDeCalendario)!!,
            settings = mesDeCalendario,
        ).single()

        val hoja = assertIs<Screen.QuickAdd>(hojaParaAnotar(fila))
        assertEquals("Gimnasio", hoja.presetNota)
        assertEquals(139_900, hoja.presetMonto)
        assertEquals("Vivienda", hoja.presetCategoria)
        assertEquals("acc_1", hoja.presetAccountId)
        assertEquals("2026-09-10", hoja.presetFecha, "la fecha del vencimiento, no hoy")
        assertFalse(hoja.presetEsIngreso)
    }

    /** Un sueldo no se paga: llega. La hoja abre en «Ingreso» o el signo queda al revés. */
    @Test
    fun anotar_un_ingreso_abre_la_hoja_en_ingreso() {
        val hoja = assertIs<Screen.QuickAdd>(
            hojaParaAnotar(
                regla("rr_sueldo", "Salario", 6_000_000, 25, TransactionType.INCOME),
                "2026-09-25",
            ),
        )
        assertTrue(hoja.presetEsIngreso)
    }

    /**
     * **La cuota de un crédito se anota en Créditos.** Un gasto suelto no baja ninguna deuda, así
     * que la fila se quedaría sin tildar igual y encima habría quedado un movimiento duplicado.
     */
    @Test
    fun la_cuota_de_un_credito_se_anota_en_creditos() {
        assertEquals(
            Screen.Credits,
            hojaParaAnotar(regla("credit_1", "Cuota Vehículo", 4_101_123, 10), "2026-09-10"),
        )
        assertEquals(
            Screen.Credits,
            hojaParaAnotar(regla("card_1", "Master Black", 27_501_150, 18, saldo = true), "2026-09-18"),
        )
    }

    /**
     * **El «monto» de una tarjeta es su SALDO**, no lo que se va a pagar. Prellenarlo sería
     * escribirle $27.501.150 en la caja del monto a alguien que va a pagar el mínimo.
     */
    @Test
    fun un_saldo_no_se_prellena_como_monto() {
        val hoja = assertIs<Screen.QuickAdd>(
            hojaParaAnotar(
                ruleId = "rr_tarjeta_propia",
                nombre = "Tarjeta",
                monto = 27_501_150,
                montoEsSaldo = true,
                categoria = "Vivienda",
                cuentaId = null,
                venceIso = "2026-09-18",
                esIngreso = false,
            ),
        )
        assertNull(hoja.presetMonto, "sin un monto honesto que sugerir, el campo va vacío")
    }
}
