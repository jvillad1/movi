package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.LETRA_MINIMA_QUE_SE_VE
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.fraccionDeLaMarca
import com.jvillada.movi.ui.porrevisar.ResultadoDelLote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Actividad por dentro (vuelta 40)
 *
 * El encabezado de cada día («Hoy · martes 28» y «Gastaste $X»), el chip «Personas» y la tarjeta de
 * los avisos del banco: qué cuenta, qué se confirma de una vez y qué se queda.
 */
class ActividadPorDentroTest {

    // ── El encabezado del día ────────────────────────────────────────────────────────────────────

    @Test
    fun `hoy y ayer llevan el dia de la semana, lo demas la fecha entera`() {
        // El 28 de octubre de 2026 es miércoles.
        assertEquals("Hoy · miércoles 28", encabezadoDelDiaDeActividad("2026-10-28", hoy = "2026-10-28"))
        assertEquals("Ayer · martes 27", encabezadoDelDiaDeActividad("2026-10-27", hoy = "2026-10-28"))
        assertEquals("Lunes 26 de octubre", encabezadoDelDiaDeActividad("2026-10-26", hoy = "2026-10-28"))
        assertEquals("Domingo 1 de noviembre", encabezadoDelDiaDeActividad("2026-11-01", hoy = "2026-11-03"))
        // El año, solo si no es el de hoy.
        assertEquals("Sábado 20 de diciembre de 2025", encabezadoDelDiaDeActividad("2025-12-20", hoy = "2026-01-05"))
        assertEquals("no-es-fecha", encabezadoDelDiaDeActividad("no-es-fecha", hoy = "2026-10-28"))
    }

    private val ahorros = Account("acc-8133", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 5_000_000L, "COP")
    private val tarjeta = Account("acc-mb", "Master Black", AccountType.CREDIT_CARD, -1_000_000L, "COP")
    private val tipos = mapOf(ahorros.id to ahorros.type, tarjeta.id to tarjeta.type)

    private fun evento(
        id: String,
        tipo: TransactionType,
        monto: Long,
        categoria: String = "Comida",
        cuenta: String = ahorros.id,
        cuentaEnElFlujo: Boolean = true,
        transferId: String? = null,
        parteAjena: Long = 0L,
        espacioId: String? = null,
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria, description = id,
        timestamp = 0L, transferId = transferId, countsAsCashFlow = cuentaEnElFlujo, reconciliationStatus = estado,
        parteAjena = parteAjena, espacioId = espacioId,
    )

    @Test
    fun `Gastaste cuenta los gastos con la regla de siempre y deja afuera traspasos y pagos de tarjeta`() {
        val dia = listOf(
            evento("almuerzo", TransactionType.EXPENSE, 46_000),
            evento("mercado", TransactionType.EXPENSE, 88_600, categoria = "Mercado", parteAjena = 44_300, espacioId = "esp_casa"),
            evento("traspaso-sale", TransactionType.EXPENSE, 500_000, categoria = TRANSFER_CATEGORY, cuentaEnElFlujo = false, transferId = "t1"),
            evento("pago-sale", TransactionType.EXPENSE, 386_902, categoria = CARD_PAYMENT_CATEGORY, cuentaEnElFlujo = false, transferId = "t2"),
            evento("pago-entra", TransactionType.INCOME, 386_902, categoria = CARD_PAYMENT_CATEGORY, cuenta = tarjeta.id, cuentaEnElFlujo = false, transferId = "t2"),
            // Lo que entró solo y espera en «Por confirmar» no suma hasta confirmarlo.
            evento("aviso", TransactionType.EXPENSE, 12_000, estado = ReconciliationStatus.UNCONFIRMED),
        )
        val resumen = resumenDelDia(dia, tipos)
        // El almuerzo y TU parte del mercado repartido.
        assertEquals(46_000L + 44_300L, resumen.gastaste)
        assertEquals(0L, resumen.entro)
        assertEquals(RotuloDelDia.GASTASTE to 90_300L, cifraDelDia(resumen))
    }

    @Test
    fun `un dia de solo ingresos dice Entro, y uno sin nada que cuente no dice nada`() {
        val nomina = listOf(evento("nomina", TransactionType.INCOME, 6_200_000, categoria = "Salario"))
        assertEquals(RotuloDelDia.ENTRO to 6_200_000L, cifraDelDia(resumenDelDia(nomina, tipos)))

        val soloPagos = listOf(
            evento("pago-sale", TransactionType.EXPENSE, 386_902, categoria = CARD_PAYMENT_CATEGORY, cuentaEnElFlujo = false, transferId = "t2"),
            evento("pago-entra", TransactionType.INCOME, 386_902, categoria = CARD_PAYMENT_CATEGORY, cuenta = tarjeta.id, cuentaEnElFlujo = false, transferId = "t2"),
        )
        assertNull(cifraDelDia(resumenDelDia(soloPagos, tipos)))
    }

    // ── El chip «Personas» ───────────────────────────────────────────────────────────────────────

    @Test
    fun `Personas deja pasar solo lo repartido en un grupo`() {
        val repartido = evento("mercado", TransactionType.EXPENSE, 88_600, espacioId = "esp_casa", parteAjena = 44_300)
        val solo = evento("almuerzo", TransactionType.EXPENSE, 46_000)
        assertTrue(matchesChip(repartido, CHIP_PERSONAS))
        assertFalse(matchesChip(solo, CHIP_PERSONAS))
        assertTrue(CHIP_PERSONAS in CHIPS_VISIBLES)
        assertEquals("Personas", CHIPS_DE_MOVIMIENTOS[CHIP_PERSONAS])
        // Los índices de antes no se renumeran.
        assertEquals(listOf("Todo", "Gastos", "Ingresos"), CHIPS_DE_MOVIMIENTOS.take(3))
        assertEquals(CHIP_PERSONAS, chipInicialDeMovimientos(CHIP_PERSONAS))
        assertEquals("Nada con otras personas", vacioDeMovimientos(CHIP_PERSONAS, hayMovimientos = true).titulo)
    }

    // ── La tarjeta de los avisos del banco ───────────────────────────────────────────────────────

    private fun aviso(id: String, texto: String) =
        SmsMessage(id = id, time = "2026-10-10 08:10", bank = "85540", text = texto, state = SMS_STATE_PENDING, det = "")

    private val cafe = aviso("cafe", "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 08:10.")
    private val pan = aviso("pan", "Bancolombia: Compraste \$9.000,00 en PANADERIA con tu T.Deb *8133, el 10/10/2026 a las 09:10.")
    private val exito = aviso("exito", "Bancolombia: Compraste \$80.000,00 en EXITO con tu T.Deb *8133, el 10/10/2026 a las 10:00.")
    private val pagoDeTarjeta = aviso("tarjeta", "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133 el 10/10/2026.")
    private val yaAnotado = FinancialEvent(
        id = "ev-exito", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 80_000L,
        category = "Mercado", description = "Éxito", timestamp = 0L,
    )
    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("cafe", leido = ParsedSms(46_000.0, "TOSTAO CAFE", TransactionType.EXPENSE, "Comida", aprendidoDe = "x")),
            PropuestaDelAviso("pan", leido = ParsedSms(9_000.0, "PANADERIA", TransactionType.EXPENSE, "Comida", aprendidoDe = "x")),
            // «¿Ya lo anotaste?» tiene un candidato: la fila lo manda a revisar.
            PropuestaDelAviso("exito", leido = ParsedSms(80_000.0, "EXITO", TransactionType.EXPENSE, "Mercado", aprendidoDe = "x"), coincidencias = listOf(yaAnotado)),
            // De dos patas: hay que elegir a dónde entra.
            PropuestaDelAviso("tarjeta", leido = ParsedSms(386_902.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY)),
        ),
        categoriasDelDueno = listOf("Comida", "Mercado"),
    )
    private val ahora = 1_791_000_000_000L

    @Test
    fun `Confirmar los N toma solo lo que la fila confirmaria, y lo demas se queda`() {
        val avisos = avisosPorConfirmar(listOf(cafe, pan, exito, pagoDeTarjeta), propuestas, listOf(ahorros), ahora)

        assertEquals(4, avisos.cuantos)
        assertEquals(listOf("cafe", "pan"), avisos.confirmables.map { it.cabezaId })
        assertEquals("4 avisos del banco por confirmar", tituloDeLosAvisos(avisos.cuantos))
        assertEquals("Confirmar los 2 que Movi entendió", botonDeConfirmarLosAvisos(avisos))
        assertEquals(REVISAR_UNO_POR_UNO, botonDeRevisarLosAvisos(avisos))
        assertEquals(46_000L + 9_000L + 80_000L + 386_902L, avisos.suman)
        assertTrue(detalleDeLosAvisos(avisos)!!.startsWith("Suman \$521.902 · "), detalleDeLosAvisos(avisos))

        val hoja = detalleDeLaConfirmacion(avisos)
        assertTrue(hoja.contains("Total \$55.000"), hoja)
        assertTrue(hoja.contains("2 avisos quedan para revisar uno por uno."), hoja)
        assertEquals("¿Confirmar estos 2 avisos?", preguntaDeConfirmarLosAvisos(avisos.confirmables.size))
    }

    @Test
    fun `con todos entendidos dice Confirmar los N, y sin ninguno no hay boton`() {
        val todos = avisosPorConfirmar(listOf(cafe, pan), propuestas, listOf(ahorros), ahora)
        assertEquals("Confirmar los 2", botonDeConfirmarLosAvisos(todos))

        val ninguno = avisosPorConfirmar(listOf(exito, pagoDeTarjeta), propuestas, listOf(ahorros), ahora)
        assertTrue(ninguno.confirmables.isEmpty())
        assertNull(botonDeConfirmarLosAvisos(ninguno))
        // Sin un «Confirmar» al lado, o con un solo aviso, el botón dice «Revisar» a secas.
        assertEquals("Revisar", botonDeRevisarLosAvisos(ninguno))
        assertEquals("Revisar", botonDeRevisarLosAvisos(avisosPorConfirmar(listOf(cafe), propuestas, listOf(ahorros), ahora)))

        // Sin la lectura de las propuestas no se confirma nada (ni se suma lo que no se leyó).
        val sinLeer = avisosPorConfirmar(listOf(cafe, pan), propuestas = null, cuentas = listOf(ahorros), ahora = ahora)
        assertEquals(2, sinLeer.cuantos)
        assertTrue(sinLeer.confirmables.isEmpty())
        assertNull(sinLeer.suman)

        // Lo que falló acá no se vuelve a ofrecer en el lote.
        val conUnaFalla = avisosPorConfirmar(listOf(cafe, pan), propuestas, listOf(ahorros), ahora, fallaron = setOf("pan"))
        assertEquals(listOf("cafe"), conUnaFalla.confirmables.map { it.cabezaId })
    }

    @Test
    fun `lo que dice al terminar`() {
        val avisos = avisosPorConfirmar(listOf(cafe, pan), propuestas, listOf(ahorros), ahora)
        val (uno, otro) = avisos.confirmables
        assertEquals("2 confirmados", textoDeLosConfirmados(ResultadoDelLote(listOf(uno, otro), emptyList())))
        assertEquals(
            "1 confirmado. 1 no se pudo: quedó en Por revisar.",
            textoDeLosConfirmados(ResultadoDelLote(listOf(uno), listOf(otro to "Sin señal"))),
        )
    }

    @Test
    fun `la lista corta de comercios`() {
        assertNull(listaDeComercios(emptyList()))
        assertEquals("Rappi", listaDeComercios(listOf("Rappi")))
        assertEquals("Rappi y D1", listaDeComercios(listOf("Rappi", "D1")))
        assertEquals("Rappi, D1 y Uber", listaDeComercios(listOf("Rappi", "D1", "Uber")))
        assertEquals("Rappi, D1 y 3 más", listaDeComercios(listOf("Rappi", "D1", "Uber", "Éxito", "Terpel")))
    }

    // ── El mínimo de letra ───────────────────────────────────────────────────────────────────────

    /** La marca de tres letras en el círculo de una fila («KFC») ya no baja de 12 px (vuelta 40). */
    @Test
    fun `la marca de tres letras de una fila no baja del minimo`() {
        val seVe = TamanoDeAvatar.Fila.circulo.value * fraccionDeLaMarca(3)
        assertTrue(seVe >= LETRA_MINIMA_QUE_SE_VE, "se ve a $seVe px")
    }
}
