package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El caso que motivó todo esto es el primer test: el «Salario» del 25 del dueño aparecía «Vencido
 * hace 1 día» mientras el ingreso ya estaba anotado, por un monto **parecido pero no igual** (una
 * retención). Si este test empieza a exigir monto exacto, la función dejó de servir para lo que
 * fue pedida.
 */
class OccurrenceMatchingTest {

    private val due = LocalDate.of(2026, 8, 25)

    private fun regla(
        name: String = "Salario",
        category: String = "Salario",
        amount: Long = 5_000_000,
        type: TransactionType = TransactionType.INCOME,
        accountId: String? = null,
        destinoConocidoId: String? = null,
    ) = RecurringRule("rr_1", name, category, amount, 25, type, accountId = accountId, destinoConocidoId = destinoConocidoId)

    private fun evento(
        id: String = "ev_1",
        day: Int = 25,
        month: Int = 8,
        amount: Long = 5_000_000,
        category: String = "Salario",
        description: String = "Salario",
        type: TransactionType = TransactionType.INCOME,
        accountId: String = "acc_1",
        transferId: String? = null,
        merchant: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = accountId,
        type = type,
        amount = amount,
        category = category,
        description = description,
        merchant = merchant,
        timestamp = appDateToEpochMillis(LocalDate.of(2026, month, day)),
        transferId = transferId,
    )

    /** Ola V: un destino conocido, con el mismo número que usa `Tía Caro` en producción. */
    private fun destino(id: String = "dst_1", nombre: String = "Caro", numero: String = "31973270756") =
        DestinoConocido(id = id, nombre = nombre, numero = numero)

    // ── El caso del dueño ─────────────────────────────────────────────────────

    @Test fun `el salario con retencion sigue siendo candidato`() {
        // Anotó 5.000.000 como recurrente y este mes le entraron 4.780.000.
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(evento(amount = 4_780_000)))
        assertEquals(listOf("ev_1"), candidatos.map { it.id })
    }

    @Test fun `entre varios candidatos, el mas cercano al monto esperado va primero`() {
        val lejos = evento(id = "ev_lejos", amount = 1_000_000)
        val cerca = evento(id = "ev_cerca", amount = 4_900_000)
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(lejos, cerca))
        assertEquals(listOf("ev_cerca", "ev_lejos"), candidatos.map { it.id })
    }

    @Test fun `el nombre identifica mas que la categoria`() {
        // El de categoría suelta tiene el monto exacto; el que se llama igual, no. Gana el nombre:
        // el monto es lo variable, la identidad es lo estable.
        val soloCategoria = evento(id = "ev_cat", description = "Reintegro", amount = 5_000_000)
        val nombreIgual = evento(id = "ev_nom", category = "Otros ingresos", amount = 4_500_000)
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(soloCategoria, nombreIgual))
        assertEquals(listOf("ev_nom", "ev_cat"), candidatos.map { it.id })
    }

    // ── Las puertas cerradas ──────────────────────────────────────────────────

    @Test fun `un gasto nunca es la ocurrencia de un ingreso`() {
        val gasto = evento(type = TransactionType.EXPENSE)
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(gasto)).isEmpty())
    }

    @Test fun `una pata de traspaso no es una ocurrencia`() {
        val pata = evento(transferId = "tr_1", category = TRANSFER_CATEGORY, description = "Traspaso")
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(pata)).isEmpty())
    }

    @Test fun `las categorias reservadas nunca son candidatas`() {
        val apertura = evento(id = "ev_ap", category = OPENING_CATEGORY, description = "Salario")
        val pagoTarjeta = evento(
            id = "ev_tj",
            category = CARD_PAYMENT_CATEGORY,
            description = "Salario",
            type = TransactionType.EXPENSE,
        )
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(apertura)).isEmpty())
        assertTrue(
            occurrenceCandidatesFor(
                regla(type = TransactionType.EXPENSE),
                due,
                listOf(pagoTarjeta),
            ).isEmpty(),
        )
    }

    @Test fun `un movimiento ya usado como ocurrencia no se vuelve a proponer`() {
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(evento()), usedEventIds = setOf("ev_1"))
        assertTrue(candidatos.isEmpty())
    }

    @Test fun `fuera de la ventana no hay propuesta`() {
        // El 5 de agosto está a 20 días del vencimiento del 25: es el sueldo de otro mes.
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(evento(day = 5))).isEmpty())
        // Y el borde exacto sí entra.
        assertEquals(1, occurrenceCandidatesFor(regla(), due, listOf(evento(day = 15))).size)
    }

    @Test fun `sin ninguna sena de identidad no se propone nada`() {
        // Mismo tipo, misma ventana, pero ni el nombre ni la categoría pegan y la regla no dice
        // cuenta. Proponer esto sería invitar a cerrar el mes con cualquier ingreso.
        val ajeno = evento(description = "Venta de la moto", category = "Otros ingresos")
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(ajeno)).isEmpty())
    }

    /**
     * **Regresión del hallazgo ALTA-2.** La cuenta no mira el movimiento: decir «esta regla tiene
     * cuenta» no dice NADA sobre si un gasto de esa cuenta es el arriendo. Con la cuenta como seña
     * suficiente, la lista de gastos que tiene una cuenta de verdad entraba entera.
     *
     * El test de antes probaba la cuenta con UN movimiento, que es exactamente el escenario donde
     * el bug no se ve.
     */
    @Test fun `una cuenta poblada no convierte cualquier gasto en el arriendo`() {
        val arriendo = RecurringRule(
            "rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 25,
            TransactionType.EXPENSE, accountId = "acc_1",
        )
        val gastosDeLaCuenta = listOf(
            evento(id = "ev_exito", amount = 1_750_000, category = "Mercado", description = "Compra Exito", type = TransactionType.EXPENSE),
            evento(id = "ev_monitor", amount = 2_100_000, category = "Tecnologia", description = "Monitor", type = TransactionType.EXPENSE),
            evento(id = "ev_energia", amount = 260_000, category = "Servicios", description = "Energia", type = TransactionType.EXPENSE),
        )
        assertTrue(
            occurrenceCandidatesFor(arriendo, due, gastosDeLaCuenta).isEmpty(),
            "el mercado del Éxito no puede proponerse como el arriendo solo por estar en la misma cuenta",
        )
        // Y el arriendo de verdad, que sí comparte la categoría, sí se propone.
        val elArriendo = evento(id = "ev_arriendo", amount = 1_800_000, category = "Vivienda", description = "Pago arriendo", type = TransactionType.EXPENSE)
        assertEquals(
            listOf("ev_arriendo"),
            occurrenceCandidatesFor(arriendo, due, gastosDeLaCuenta + elArriendo).map { it.id },
        )
    }

    @Test fun `la cuenta suma como sena pero ya no filtra`() {
        val reglaConCuenta = regla(accountId = "acc_1")
        // Mismo nombre pero en OTRA cuenta: se propone igual. Antes desaparecía, y un nombre
        // idéntico pesando menos que la cuenta es raro.
        val enOtra = evento(id = "ev_nequi", accountId = "acc_2")
        assertEquals(listOf("ev_nequi"), occurrenceCandidatesFor(reglaConCuenta, due, listOf(enOtra)).map { it.id })
        // Y con dos iguales, el que está en la cuenta de la regla va primero.
        val enLaCuenta = evento(id = "ev_banco", accountId = "acc_1")
        assertEquals(
            listOf("ev_banco", "ev_nequi"),
            occurrenceCandidatesFor(reglaConCuenta, due, listOf(enOtra, enLaCuenta)).map { it.id },
        )
    }

    /**
     * **Regresión del hallazgo ALTA-1.** La ventana de ±10 días también iba hacia atrás, así que
     * para una regla de día bajo proponía el pago del mes ANTERIOR para cerrar este. Con el monto
     * exacto, además, así que ni siquiera salía el aviso de «no es el monto que anotaste».
     */
    @Test fun `nunca se propone un movimiento anterior al mes del vencimiento`() {
        val arriendo = RecurringRule(
            "rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 1, TransactionType.EXPENSE,
        )
        val vencimientoDeSeptiembre = LocalDate.of(2026, 9, 1)
        val pagoDeAgosto = evento(
            id = "ev_agosto", day = 25, month = 8, amount = 1_800_000,
            category = "Vivienda", description = "Arriendo", type = TransactionType.EXPENSE,
        )
        assertTrue(
            occurrenceCandidatesFor(arriendo, vencimientoDeSeptiembre, listOf(pagoDeAgosto)).isEmpty(),
            "el arriendo de agosto no puede cerrar el vencimiento de septiembre",
        )
        // El de septiembre sí, aunque llegue tarde.
        val pagoDeSeptiembre = pagoDeAgosto.copy(
            id = "ev_septiembre",
            timestamp = appDateToEpochMillis(LocalDate.of(2026, 9, 3)),
        )
        assertEquals(
            listOf("ev_septiembre"),
            occurrenceCandidatesFor(arriendo, vencimientoDeSeptiembre, listOf(pagoDeSeptiembre)).map { it.id },
        )
    }

    /**
     * Un recurrente no tiene moneda: sus montos son pesos. Un cobro en dólares que se llamara
     * igual entraba igual, y el orden terminaba comparando 12 con 5.000.000 mientras la tarjeta
     * anunciaba «no es el monto que anotaste ($5.000.000)» contra un US$12.
     */
    @Test fun `un cobro en otra moneda no se propone`() {
        val enDolares = evento(id = "ev_usd", amount = 1_200).copy(currency = "USD")
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(enDolares)).isEmpty())
    }

    @Test fun `se muestran como mucho tres propuestas`() {
        val muchos = (1..6).map { evento(id = "ev_$it", amount = 5_000_000L + it) }
        assertEquals(3, occurrenceCandidatesFor(regla(), due, muchos).size)
    }

    // ── El rodado del vencimiento y el barrido ────────────────────────────────

    @Test fun `un periodo dado por ocurrido deja de leerse como vencido`() {
        val rule = regla(type = TransactionType.EXPENSE)
        val hoy = LocalDate.of(2026, 8, 26)
        // Sin cerrar: vencido ayer.
        assertEquals(PaymentStatus.OVERDUE, statusFor(dueDateFor(rule, hoy), hoy, 3))
        // Cerrado agosto: el vencimiento vigente pasa a ser el 25 de septiembre.
        val con = dueDateFor(rule, hoy, DEFAULT_GRACE_DAYS, setOf("2026-08"))
        assertEquals(LocalDate.of(2026, 9, 25), con)
        assertEquals(PaymentStatus.UPCOMING, statusFor(con, hoy, 3))
    }

    @Test fun `al mes siguiente vuelve a estar pendiente`() {
        val rule = regla(type = TransactionType.EXPENSE)
        val enSeptiembre = LocalDate.of(2026, 9, 26)
        val due = dueDateFor(rule, enSeptiembre, DEFAULT_GRACE_DAYS, setOf("2026-08"))
        assertEquals(LocalDate.of(2026, 9, 25), due)
        assertEquals(PaymentStatus.OVERDUE, statusFor(due, enSeptiembre, 3))
    }

    @Test fun `el barrido no avisa de lo ya ocurrido, y si del mes siguiente`() {
        val rule = regla(name = "Arriendo", category = "Vivienda", type = TransactionType.EXPENSE)
        val hoy = LocalDate.of(2026, 8, 26)
        val pares = listOf(rule to null)
        assertEquals(listOf("rr_1"), selectDueForReminder(pares, hoy, 3).map { it.id })
        assertTrue(selectDueForReminder(pares, hoy, 3, mapOf("rr_1" to setOf("2026-08"))).isEmpty())
        // Septiembre sí, aunque agosto siga cerrado.
        val enSeptiembre = LocalDate.of(2026, 9, 25)
        assertEquals(
            listOf("rr_1"),
            selectDueForReminder(pares, enSeptiembre, 3, mapOf("rr_1" to setOf("2026-08"))).map { it.id },
        )
    }

    @Test fun `el sello del aviso y el filtro miran el MISMO periodo`() {
        // Si divergieran, el mismo vencimiento se notificaría dos veces — el bug que
        // `reminderKeyFor` ya arregló una vez. Con un periodo cerrado la clave tiene que rodar
        // igual que la fecha.
        val rule = regla(type = TransactionType.EXPENSE)
        val hoy = LocalDate.of(2026, 8, 26)
        val ocurridos = setOf("2026-08")
        assertEquals("2026-09", reminderKeyFor(rule, hoy, DEFAULT_GRACE_DAYS, ocurridos))
        assertEquals("2026-08", reminderKeyFor(rule, hoy))
    }

    @Test fun `un ingreso nunca entra al barrido, ocurrido o no`() {
        // Regresión: el barrido solo mira gastos. Cerrar el salario no debe cambiar eso.
        val pares = listOf(regla() to null)
        assertFalse(selectDueForReminder(pares, LocalDate.of(2026, 8, 26), 3).any { it.id == "rr_1" })
    }

    // ── El nombre con el mes/año adentro ──────────────────────────────────────

    /**
     * **El caso real del dueño.** La regla se llama «Salario»; el movimiento, como lo anota el
     * banco, «Salario Octubre 2026». Antes de `nombreDeMovimientoPegaConRegla` esto solo se
     * emparejaba si el monto también daba exacto — y el monto de un sueldo varía mes a mes.
     */
    @Test fun `Salario Octubre 2026 es la ocurrencia solo, sin exigir el monto`() {
        val reglaDelDueno = regla(
            name = "Salario",
            category = "Salario",
            amount = 20_038_658,
            accountId = "acc_1",
        )
        val vencimiento = LocalDate.of(2026, 9, 25)
        val elSueldo = evento(
            id = "ev_salario",
            day = 24,
            month = 9,
            amount = 20_038_658,
            category = "Salario",
            description = "Salario Octubre 2026",
            accountId = "acc_1",
        )
        assertEquals(
            "ev_salario",
            ocurrenciaConcluyente(reglaDelDueno, vencimiento, listOf(elSueldo))?.id,
        )
    }

    /**
     * La variante que de verdad prueba que fue el NOMBRE el que emparejó: el monto no da exacto
     * (así que la puerta de «las tres circunstancias» no aplica), y aun así se empareja solo.
     */
    @Test fun `con monto distinto tambien se empareja solo por el nombre`() {
        val reglaDelDueno = regla(
            name = "Salario",
            category = "Salario",
            amount = 20_038_658,
            accountId = "acc_1",
        )
        val vencimiento = LocalDate.of(2026, 9, 25)
        val elSueldo = evento(
            id = "ev_salario",
            day = 24,
            month = 9,
            amount = 20_500_000,
            category = "Salario",
            description = "Salario Octubre 2026",
            accountId = "acc_1",
        )
        assertEquals(
            "ev_salario",
            ocurrenciaConcluyente(reglaDelDueno, vencimiento, listOf(elSueldo))?.id,
        )
    }

    /** Con dos movimientos que dicen «Salario …», Movi no puede saber cuál es cuál: pregunta. */
    @Test fun `con dos Salario en la ventana sigue preguntando`() {
        val reglaDelDueno = regla(
            name = "Salario",
            category = "Salario",
            amount = 20_038_658,
            accountId = "acc_1",
        )
        val vencimiento = LocalDate.of(2026, 9, 25)
        val primero = evento(
            id = "ev_salario_1",
            day = 24,
            month = 9,
            amount = 20_038_658,
            category = "Salario",
            description = "Salario Octubre 2026",
            accountId = "acc_1",
        )
        val segundo = evento(
            id = "ev_salario_2",
            day = 20,
            month = 9,
            amount = 20_038_658,
            category = "Salario",
            description = "Salario",
            accountId = "acc_1",
        )
        assertNull(ocurrenciaConcluyente(reglaDelDueno, vencimiento, listOf(primero, segundo)))
    }

    // ── Palabras pegadas o separadas ──────────────────────────────────────────

    /**
     * El banco escribe el comercio con las palabras pegadas: la regla «Smart Fit» contra el
     * `SMARTFIT` del SMS. Pegaba por la clave comparable antes de que el nombre perdonara el
     * mes/año, y tiene que seguir pegando — en las dos direcciones — con un monto que no da exacto,
     * para que sea el NOMBRE el que empareja.
     */
    @Test fun `Smart Fit y SMARTFIT se emparejan solos en las dos direcciones`() {
        val gimnasio = regla(name = "Smart Fit", category = "Deporte", amount = 120_000,
            type = TransactionType.EXPENSE)
        val vencimiento = LocalDate.of(2026, 9, 25)
        val delBanco = evento(id = "ev_gym", day = 24, month = 9, amount = 125_000, category = "Deporte",
            description = "Compra", merchant = "SMARTFIT", type = TransactionType.EXPENSE)
        assertEquals("ev_gym", ocurrenciaConcluyente(gimnasio, vencimiento, listOf(delBanco))?.id)

        val pegada = regla(name = "SmartFit", category = "Deporte", amount = 120_000,
            type = TransactionType.EXPENSE)
        val separado = delBanco.copy(merchant = "SMART FIT")
        assertEquals("ev_gym", ocurrenciaConcluyente(pegada, vencimiento, listOf(separado))?.id)
    }

    @Test fun `Gimnasio Caro sigue sin ser la ocurrencia de Gimnasio`() {
        val gimnasio = regla(name = "Gimnasio", category = "Deporte", amount = 120_000,
            type = TransactionType.EXPENSE)
        val deOtro = evento(id = "ev_otro", day = 24, month = 9, amount = 125_000, category = "Deporte",
            description = "Gimnasio Caro", type = TransactionType.EXPENSE)
        assertNull(ocurrenciaConcluyente(gimnasio, LocalDate.of(2026, 9, 25), listOf(deOtro)))
    }

    // ── Ola S: la cuota de un crédito/tarjeta, emparejada solo por su categoría ───────────────

    /**
     * **El bug real (2026-09-27).** La regla sintética de un crédito o de una tarjeta —armada al
     * vuelo por `virtualRuleFor`/`virtualRuleForCard`, nunca una fila de `recurring_rules`— llevaba
     * `category = "Créditos"`, un texto que NINGÚN movimiento real usa: la categoría que de verdad
     * anotan los pagos de cuota es [CUOTA_CATEGORY] («Cuota de crédito»). Como el texto de un SMS de
     * transferencia nunca dice el nombre del crédito, el nombre tampoco pegaba casi nunca — así que
     * un movimiento cuya única evidencia es la categoría correcta ni siquiera entraba como
     * candidato. Ver `virtualRuleFor`/`virtualRuleForCard` para el arreglo.
     */
    @Test fun `un movimiento cuya unica evidencia es la categoria de cuota entra como candidato`() {
        // La regla sintética de verdad, armada como la arma `virtualRuleFor` — no una a mano con
        // la categoría ya corregida: si esta prueba no llama a `virtualRuleFor`, no prueba nada
        // sobre el bug real.
        val terms = com.jvillada.movi.shared.model.CreditTerms(
            accountId = "acc-mama", bank = "Bancolombia", principal = 15_000_000,
            rateEa = 18.0, termMonths = 24, installment = 1_300_000,
            dayOfMonth = 27, startDate = "2025-01-27",
        )
        val cuotaDelCredito = virtualRuleFor(terms, accountName = "Crédito Mamá")

        // El texto de la transferencia no dice «Crédito Mamá» —nunca lo dice— y la categoría es
        // la única seña. Antes del arreglo, ni el nombre ni la categoría pegaban y no se proponía
        // nada; el dueño terminó con un pago real que el checklist seguía marcando pendiente.
        val transferencia = evento(
            id = "ev_mama", day = 27, month = 8, amount = 1_300_000,
            category = CUOTA_CATEGORY, description = "Transferencia a Mamá",
            type = TransactionType.EXPENSE,
        )
        assertEquals(
            listOf("ev_mama"),
            occurrenceCandidatesFor(cuotaDelCredito, LocalDate.of(2026, 8, 27), listOf(transferencia)).map { it.id },
        )
    }

    /** Lo mismo del lado de una tarjeta, cuya regla sintética arma `virtualRuleForCard`. */
    @Test fun `lo mismo con el pago sintetico de una tarjeta`() {
        val terms = com.jvillada.movi.shared.model.CardTerms(
            accountId = "acc-master", bank = "Bancolombia",
            creditLimit = 20_000_000, cutoffDay = 10, paymentDay = 25,
        )
        val pagoDeLaTarjeta = virtualRuleForCard(
            terms, accountName = "Master Black", currentDebt = 500_000, accountCurrency = "COP", tasa = null,
        )
        val abono = evento(
            id = "ev_master", day = 25, month = 8, amount = 500_000,
            category = CUOTA_CATEGORY, description = "Pago", type = TransactionType.EXPENSE,
        )
        assertEquals(
            listOf("ev_master"),
            occurrenceCandidatesFor(pagoDeLaTarjeta, LocalDate.of(2026, 8, 25), listOf(abono)).map { it.id },
        )
    }

    // ── Ola V: el destino conocido, para un traspaso a un tercero ────────────────────

    /**
     * **El caso del brief.** «Tía Caro» ($100.000/mes) asociada al destino «Caro»
     * (`*31973270756`): un traspaso futuro cuyo texto SOLO trae el número de esa cuenta —nunca
     * «Tía Caro», que es justo lo que un SMS de transferencia no dice— tiene que reconocerse solo,
     * igual que si el nombre pegara.
     */
    @Test fun `un traspaso que solo trae el numero del destino se empareja solo`() {
        val tiaCaro = regla(
            name = "Tía Caro",
            category = "Familia",
            amount = 100_000,
            type = TransactionType.EXPENSE,
            destinoConocidoId = "dst_caro",
        )
        val destinos = mapOf("dst_caro" to destino(id = "dst_caro"))
        val vencimiento = LocalDate.of(2026, 9, 25)
        // El texto real de un SMS de Bancolombia: nombra el número, nunca a quién le pusiste tú.
        // Categoría deliberadamente DISTINTA a la de la regla, para que lo único que pueda
        // proponer o emparejar este movimiento sea la seña del destino, no la categoría.
        val transferencia = evento(
            id = "ev_caro",
            day = 25,
            month = 9,
            amount = 100_000,
            category = "Otra categoría",
            description = "Transferencia a la cuenta *31973270756",
            type = TransactionType.EXPENSE,
        )
        // Entra como candidato…
        assertEquals(
            listOf("ev_caro"),
            occurrenceCandidatesFor(tiaCaro, vencimiento, listOf(transferencia), destinos = destinos).map { it.id },
        )
        // … y completa el ítem solo, como si el nombre hubiera pegado.
        assertEquals(
            "ev_caro",
            ocurrenciaConcluyente(tiaCaro, vencimiento, listOf(transferencia), destinos = destinos)?.id,
        )
    }

    /** Sin el mapa de destinos (el default de todo llamador viejo), el comportamiento no cambia. */
    @Test fun `sin destinos resueltos, el mismo movimiento no se distingue de cualquier otro`() {
        val tiaCaro = regla(
            name = "Tía Caro",
            category = "Familia",
            amount = 100_000,
            type = TransactionType.EXPENSE,
            destinoConocidoId = "dst_caro",
        )
        val vencimiento = LocalDate.of(2026, 9, 25)
        val transferencia = evento(
            id = "ev_caro",
            day = 25,
            month = 9,
            amount = 100_000,
            category = "Otra categoría",
            description = "Transferencia a la cuenta *31973270756",
            type = TransactionType.EXPENSE,
        )
        assertNull(ocurrenciaConcluyente(tiaCaro, vencimiento, listOf(transferencia)))
    }

    /** Sin destino ASOCIADO a la regla (aunque el dueño tenga otros guardados), tampoco cambia nada. */
    @Test fun `una regla sin destino asociado no gana nada por los destinos de otra`() {
        val otraRegla = regla(
            name = "Mercado",
            category = "Comida",
            amount = 100_000,
            type = TransactionType.EXPENSE,
        )
        val destinos = mapOf("dst_caro" to destino(id = "dst_caro"))
        val vencimiento = LocalDate.of(2026, 9, 25)
        val transferencia = evento(
            id = "ev_caro",
            day = 25,
            month = 9,
            amount = 100_000,
            category = "Otra categoría",
            description = "Transferencia a la cuenta *31973270756",
            type = TransactionType.EXPENSE,
        )
        assertTrue(occurrenceCandidatesFor(otraRegla, vencimiento, listOf(transferencia), destinos = destinos).isEmpty())
    }

    /** Un destino que no es el asociado a la regla no cuenta, aunque el número aparezca en el texto. */
    @Test fun `el numero de un destino distinto al asociado no pega`() {
        val tiaCaro = regla(
            name = "Tía Caro",
            category = "Familia",
            amount = 100_000,
            type = TransactionType.EXPENSE,
            destinoConocidoId = "dst_caro",
        )
        // Solo el destino del PAPÁ está resuelto en el mapa — el de Caro no llegó (borrado, o el
        // llamador no lo trajo). El id que la regla guarda no encuentra nada en el mapa.
        val destinos = mapOf("dst_papa" to destino(id = "dst_papa", nombre = "Papá", numero = "999999"))
        val vencimiento = LocalDate.of(2026, 9, 25)
        val transferencia = evento(
            id = "ev_caro",
            day = 25,
            month = 9,
            amount = 100_000,
            category = "Otra categoría",
            description = "Transferencia a la cuenta *31973270756",
            type = TransactionType.EXPENSE,
        )
        assertTrue(occurrenceCandidatesFor(tiaCaro, vencimiento, listOf(transferencia), destinos = destinos).isEmpty())
    }
}
