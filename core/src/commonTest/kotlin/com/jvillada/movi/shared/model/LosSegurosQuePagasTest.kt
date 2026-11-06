package com.jvillada.movi.shared.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 132 · el detector de «Tus seguros», sobre datos con la forma de los de verdad
 *
 * Los textos son los que escriben los bancos: el seguro de vida deudor de la tarjeta de Bancolombia
 * («SEGURO VIDA DEUDOR TC»), un SOAT pagado por PSE, una EPS de Sura, una panadería «Bolívar».
 *
 * La revisión de la vuelta sumó: un cobro único sin señal de cada cuánto no se vuelve mensual (C1), el
 * seguro del crédito cobrado también aparte no suma dos veces (I1), los falsos positivos de «Protección»,
 * «prepagada», «asistencia» y la cuota de manejo, los filtros de los demás cálculos, y los dólares aparte.
 */
class LosSegurosQuePagasTest {

    private val hoy = "2026-11-05"
    private val zona = TimeZone.UTC
    private val diaDe: (Long) -> String = { Instant.fromEpochMilliseconds(it).toLocalDateTime(zona).date.toString() }

    private val ahorros = Account("acc_ahorros", "Ahorros 8133", AccountType.SAVINGS, 0)
    private val master = Account("acc_master", "Master Black", AccountType.CREDIT_CARD, 0)
    private val vehiculo = Account("acc_vehiculo", "Vehículo 4083", AccountType.LOAN, 90_000_000)
    private val hipoteca = Account("acc_hipoteca", "Hipoteca casa", AccountType.LOAN, 200_000_000)
    private val cuentas = listOf(ahorros, master, vehiculo, hipoteca)

    private fun en(fecha: String): Long = LocalDate.parse(fecha).atStartOfDayIn(zona).toEpochMilliseconds() + 12 * 3_600_000L

    private fun gasto(
        id: String,
        fecha: String,
        monto: Long,
        descripcion: String,
        cuenta: Account = ahorros,
        categoria: String = "Otros",
        moneda: String = "COP",
    ) = FinancialEvent(
        id = id, accountId = cuenta.id, type = TransactionType.EXPENSE, amount = monto, currency = moneda, category = categoria,
        description = descripcion, timestamp = en(fecha), reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    /** El mismo cobro en varios meses (el día 2 de cada uno). */
    private fun mensual(prefijo: String, meses: List<String>, monto: Long, descripcion: String, cuenta: Account = ahorros) =
        meses.mapIndexed { i, m -> gasto("ev_${prefijo}_$i", "$m-02", monto, descripcion, cuenta) }

    private fun credito(cuenta: Account, seguro: Long?, banco: String = "Occidente", paidBy: String? = null) = CreditSummary(
        account = cuenta,
        terms = CreditTerms(
            accountId = cuenta.id, bank = banco, principal = 300_000_000, rateEa = 17.4, termMonths = 72,
            installment = 4_101_122, dayOfMonth = 3, startDate = "2025-01-03", insuranceMonthly = seguro, paidBy = paidBy,
        ),
        paidPct = 0.25,
    )

    private fun detectar(
        eventos: List<FinancialEvent> = emptyList(),
        creditos: List<CreditSummary> = emptyList(),
        reglas: List<RecurringRule> = emptyList(),
        suscripciones: List<Subscription> = emptyList(),
        descartados: Set<String> = emptySet(),
        confirmados: Set<String> = emptySet(),
        cadaCuanto: Map<String, Int> = emptyMap(),
        anulados: Set<String> = emptySet(),
    ) = losSegurosQuePagas(
        eventos, cuentas, creditos, reglas, suscripciones, hoy, diaDe,
        RespuestasDelDueno(descartados, confirmados, cadaCuanto), anulados,
    )

    // ── El crédito ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `el seguro de la cuota de un credito va dentro de la cuota, sin decir de que es`() {
        val cuota = FinancialEvent(
            id = "ev_cuota", accountId = vehiculo.id, type = TransactionType.INCOME, amount = 4_101_122,
            category = CUOTA_CATEGORY, description = "Cuota de Vehículo 4083", timestamp = en("2026-11-03"),
        )
        val r = detectar(eventos = listOf(cuota), creditos = listOf(credito(vehiculo, 89_100)))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.DEL_CREDITO, s.tipo)
        assertEquals("Seguro del crédito · Vehículo 4083", s.nombre)
        assertEquals(89_100, s.alMes)
        assertTrue(s.dentroDeLaCuota)
        assertEquals(vehiculo.id, s.creditoId)
        assertEquals("2026-11-03", s.ultimoCobro)
        assertEquals(89_100, r.alMes)
        assertEquals(89_100L * 12, r.alAno)
    }

    @Test
    fun `un credito sin seguro, o que paga otro, no trae seguro`() {
        assertTrue(detectar(creditos = listOf(credito(vehiculo, null))).seguros.isEmpty())
        assertTrue(detectar(creditos = listOf(credito(vehiculo, 0))).seguros.isEmpty())
        assertTrue(detectar(creditos = listOf(credito(vehiculo, 89_100, paidBy = "Mi papá"))).seguros.isEmpty())
    }

    @Test
    fun `I1 · el seguro incendio y terremoto debitado de ahorros pregunta si es el del credito y no suma`() {
        val cargos = mensual("inc", listOf("2026-09", "2026-10"), 248_000, "SEGURO INCENDIO TERREMOTO")
        val r = detectar(eventos = cargos, creditos = listOf(credito(hipoteca, 250_000, banco = "Davivienda")))
        assertEquals(listOf("credito:acc_hipoteca"), r.seguros.map { it.clave })
        assertEquals(250_000, r.alMes, "solo el del crédito")
        val duda = r.porConfirmar.single()
        assertEquals("Hipoteca casa", duda.posibleDuplicadoDe)
        // «Es otro»: suma. «Es el mismo»: sale.
        assertEquals(250_000L + 248_000L, detectar(eventos = cargos, creditos = listOf(credito(hipoteca, 250_000, banco = "Davivienda")), confirmados = setOf(duda.clave)).alMes)
        val mismo = detectar(eventos = cargos, creditos = listOf(credito(hipoteca, 250_000, banco = "Davivienda")), descartados = setOf(duda.clave))
        assertTrue(mismo.porConfirmar.isEmpty())
        assertEquals(250_000, mismo.alMes)
    }

    @Test
    fun `I1 · el anotado a mano en Seguros del mismo banco tambien pregunta`() {
        val aMano = mensual("mano", listOf("2026-09", "2026-10"), 255_000, "Seguro casa Davivienda").map { it.copy(category = "Seguros") }
        val r = detectar(eventos = aMano, creditos = listOf(credito(hipoteca, 250_000, banco = "Banco Davivienda")))
        assertEquals("Hipoteca casa", r.porConfirmar.single().posibleDuplicadoDe)
        assertEquals(250_000, r.alMes)
        // Lejos del monto (más del 10 %): es otro seguro y suma.
        val otro = mensual("otro", listOf("2026-09", "2026-10"), 600_000, "Seguro casa Davivienda").map { it.copy(category = "Seguros") }
        val r2 = detectar(eventos = otro, creditos = listOf(credito(hipoteca, 250_000, banco = "Banco Davivienda")))
        assertTrue(r2.porConfirmar.isEmpty())
        assertEquals(850_000, r2.alMes)
    }

    // ── C1 · cada cuánto ─────────────────────────────────────────────────────────────────────

    @Test
    fun `el seguro de la tarjeta cobrado seis meses es uno, mensual, con su promedio`() {
        val montos = listOf(31_000L, 31_000L, 32_000L, 32_000L, 33_000L, 33_000L)
        val meses = listOf("2026-06-02", "2026-07-02", "2026-08-02", "2026-09-02", "2026-10-02", "2026-11-02")
        val eventos = meses.zip(montos).mapIndexed { i, (f, m) -> gasto("ev_seg$i", f, m, "SEGURO VIDA DEUDOR TC", master) }
        val r = detectar(eventos = eventos)
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.DE_TARJETA, s.tipo)
        assertEquals("Seguro de la tarjeta Master Black", s.nombre)
        assertEquals(6, s.cobros.size)
        assertEquals(1, s.mesesEntreCobros)
        assertFalse(s.pagadoUnaVez)
        assertEquals(montos.sum() / 6, s.alMes)
        assertTrue(s.sigueCobrandose)
        assertEquals(s.alMes, r.alMes)
    }

    @Test
    fun `el SOAT es anual y se reparte en doce meses`() {
        val r = detectar(eventos = listOf(gasto("ev_soat", "2026-03-15", 1_200_000, "PAGO PSE SOAT SURA")))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.AUTO, s.tipo)
        assertEquals("SOAT", s.nombre)
        assertEquals(12, s.mesesEntreCobros)
        assertEquals(1_200_000, s.montoDelCiclo)
        assertEquals(100_000, s.alMes)
        assertTrue(s.sigueCobrandose, "un anual de marzo sigue vigente en noviembre")
        assertEquals(1_200_000, r.alAno)
        assertTrue(r.conAnuales)
    }

    @Test
    fun `C1 · un cobro unico reciente sin señal no suma al mes, va aparte`() {
        val r = detectar(eventos = listOf(gasto("ev_tr", "2026-10-25", 2_400_000, "PAGO PSE SEGUROS BOLIVAR")))
        val s = r.seguros.single()
        assertTrue(s.pagadoUnaVez)
        assertEquals(0, s.alMes)
        assertEquals(2_400_000, s.montoDelCiclo)
        assertTrue(s.sigueCobrandose)
        assertEquals("Seguros Bolivar", s.nombre, "sin «Pago Pse» adelante")
        assertEquals(0, r.alMes)
        assertEquals(0, r.alAno)
        assertEquals(2_400_000, r.enPagosDeUnaVez)
    }

    @Test
    fun `C1 · un cobro unico de hace seis meses nunca dice que dejo de cobrarse`() {
        val s = detectar(eventos = listOf(gasto("ev_viejo", "2026-05-10", 900_000, "POLIZA HOGAR ALLIANZ"))).seguros.single()
        assertTrue(s.pagadoUnaVez)
        assertTrue(s.sigueCobrandose)
    }

    @Test
    fun `C1 · con la respuesta del duenio pasa a anual o a mensual`() {
        val cobro = gasto("ev_tr", "2026-10-25", 2_400_000, "PAGO PSE SEGUROS BOLIVAR")
        val clave = detectar(eventos = listOf(cobro)).seguros.single().clave
        val anual = detectar(eventos = listOf(cobro), cadaCuanto = mapOf(clave to 12))
        assertEquals(200_000, anual.alMes)
        assertEquals(2_400_000, anual.alAno)
        assertEquals(0, anual.enPagosDeUnaVez)
        val mensual = detectar(eventos = listOf(cobro), cadaCuanto = mapOf(clave to 1))
        assertEquals(2_400_000, mensual.alMes)
        // Mensual y el único cobro hace más de 45 días: dejó de cobrarse, con la regla de siempre.
        val viejo = gasto("ev_v", "2026-05-10", 900_000, "POLIZA HOGAR ALLIANZ")
        val claveVieja = detectar(eventos = listOf(viejo)).seguros.single().clave
        assertFalse(detectar(eventos = listOf(viejo), cadaCuanto = mapOf(claveVieja to 1)).seguros.single().sigueCobrandose)
    }

    @Test
    fun `N1 · marzo y octubre no son mensuales, van como pagos de una vez, todos sumados aparte`() {
        val r = detectar(eventos = listOf(
            gasto("ev_mar", "2026-03-10", 2_400_000, "PAGO PSE SEGUROS BOLIVAR"),
            gasto("ev_oct", "2026-10-25", 1_800_000, "PAGO PSE SEGUROS BOLIVAR"),
        ))
        val s = r.seguros.single()
        assertTrue(s.pagadoUnaVez, "dos cobros con siete meses de hueco no dicen «todos los meses»")
        assertEquals(2, s.cobros.size)
        assertEquals(0, r.alMes)
        assertEquals(1_800_000, s.montoDelCiclo, "el último")
        assertEquals(4_200_000, r.enPagosDeUnaVez)
    }

    @Test
    fun `N1 · un trimestral sin regla pregunta, y con cada 3 meses se reparte`() {
        val trimestral = listOf("2026-02", "2026-05", "2026-08", "2026-11").mapIndexed { i, m ->
            gasto("ev_tri_$i", "$m-03", 300_000, "POLIZA HOGAR ALLIANZ")
        }
        val antes = detectar(eventos = trimestral)
        assertTrue(antes.seguros.single().pagadoUnaVez)
        assertEquals(0, antes.alMes)
        val clave = antes.seguros.single().clave
        val cada3 = detectar(eventos = trimestral, cadaCuanto = mapOf(clave to 3))
        assertEquals(100_000, cada3.alMes)
        assertEquals(1_200_000, cada3.alAno)
        assertTrue(cada3.seguros.single().sigueCobrandose)
        assertEquals(0, cada3.enPagosDeUnaVez)
    }

    @Test
    fun `N1 · la renovacion de un anual que llego unos dias antes no lo vuelve mensual`() {
        val renovacion = listOf(
            gasto("ev_r1", "2025-11-08", 1_200_000, "POLIZA VIDA ALLIANZ"),
            gasto("ev_r2", "2026-11-03", 1_250_000, "POLIZA VIDA ALLIANZ"),
        )
        val r = detectar(eventos = renovacion)
        assertTrue(r.seguros.single().pagadoUnaVez)
        assertEquals(0, r.alMes)
        val anual = detectar(eventos = renovacion, cadaCuanto = mapOf(r.seguros.single().clave to 12))
        assertEquals((1_250_000L + 6) / 12, anual.alMes)
        assertEquals(1_250_000, anual.alAno)
    }

    @Test
    fun `N1 · meses casi seguidos si son mensuales`() {
        assertTrue(pareceMensual(listOf("2026-09", "2026-10")))
        assertTrue(pareceMensual(listOf("2026-09", "2026-11")), "un mes sin cobro de por medio")
        assertTrue(pareceMensual(listOf("2025-12", "2026-01", "2026-02")), "cruza el año")
        assertFalse(pareceMensual(listOf("2026-03", "2026-10")))
        assertFalse(pareceMensual(listOf("2026-02", "2026-05", "2026-08")))
        assertFalse(pareceMensual(listOf("2025-11", "2026-11")))
        assertFalse(pareceMensual(listOf("2026-10")))
        val dosSeguidos = detectar(eventos = mensual("sep", listOf("2026-09", "2026-11"), 40_000, "SEGURO VIDA ALLIANZ"))
        assertEquals(40_000, dosSeguidos.alMes)
    }

    @Test
    fun `el vida deudor de una tarjeta no se toma por el seguro del credito`() {
        val tarjeta = mensual("tc", listOf("2026-10", "2026-11"), 250_000, "SEGURO VIDA DEUDOR TC", master)
        val r = detectar(eventos = tarjeta, creditos = listOf(credito(hipoteca, 250_000, banco = "Davivienda")))
        assertTrue(r.porConfirmar.isEmpty())
        assertEquals(500_000, r.alMes)
    }

    @Test
    fun `el mismo comercio en pesos y en dolares son dos seguros`() {
        val r = detectar(eventos =
            mensual("cop", listOf("2026-10", "2026-11"), 40_000, "ALLIANZ ASISTENCIA VIAJE", master) +
                mensual("usd", listOf("2026-10", "2026-11"), 10, "ALLIANZ ASISTENCIA VIAJE", master).map { it.copy(currency = "USD") },
        )
        assertEquals(setOf("COP", "USD"), r.seguros.map { it.moneda }.toSet())
        assertEquals(40_000, r.alMes)
        assertEquals(mapOf("USD" to 10L), r.otrasMonedas)
    }

    @Test
    fun `una regla mensual de seguros basta para decir mensual con un solo cobro`() {
        val regla = RecurringRule(id = "rr_vida", name = "Seguro de vida", category = "Seguros", amount = 80_000, dayOfMonth = 5, type = TransactionType.EXPENSE)
        val s = detectar(eventos = listOf(gasto("ev_vida", "2026-11-01", 80_000, "Seguro de vida")), reglas = listOf(regla)).seguros.single()
        assertFalse(s.pagadoUnaVez)
        assertEquals(80_000, s.alMes)
    }

    @Test
    fun `una regla de seguros semestral da el nombre y cada cuantos meses`() {
        val regla = RecurringRule(
            id = "rr_poliza", name = "Póliza del apartamento", category = "Seguros", amount = 600_000, dayOfMonth = 10,
            type = TransactionType.EXPENSE, frecuencia = FrecuenciaDelPago.SEMESTRAL, primerVencimiento = "2026-02-10",
        )
        val s = detectar(eventos = listOf(gasto("ev_pol", "2026-08-10", 600_000, "Póliza del apartamento")), reglas = listOf(regla)).seguros.single()
        assertEquals("Póliza del apartamento", s.nombre)
        assertEquals(6, s.mesesEntreCobros)
        assertEquals(100_000, s.alMes)
    }

    // ── Lo que no es seguro ──────────────────────────────────────────────────────────────────

    @Test
    fun `Sura de una EPS o una ARL no es un seguro concluyente`() {
        val eps = gasto("ev_eps", "2026-10-30", 380_000, "PAGO SURA EPS")
        val r = detectar(eventos = listOf(eps, gasto("ev_arl", "2026-10-30", 9_000, "APORTE ARL SURA")))
        assertTrue(r.seguros.isEmpty())
        assertEquals(2, r.porConfirmar.size)
        assertEquals(0, r.alMes)
        val clave = r.porConfirmar.first { it.cobros.single().movimientoId == "ev_eps" }.clave
        val confirmado = detectar(eventos = listOf(eps), confirmados = setOf(clave), cadaCuanto = mapOf(clave to 1))
        assertTrue(confirmado.seguros.single().confirmado)
        assertEquals(380_000, confirmado.alMes)
    }

    @Test
    fun `una panaderia Bolivar no es un seguro, Seguros Bolivar si`() {
        val r = detectar(eventos = listOf(
            gasto("ev_pan", "2026-11-01", 12_500, "PANADERIA BOLIVAR"),
            gasto("ev_calle", "2026-11-01", 40_000, "PARQUEADERO CALLE BOLIVAR"),
        ))
        assertTrue(r.seguros.isEmpty() && r.porConfirmar.isEmpty())
        assertEquals(TipoDeSeguro.VIDA, detectar(eventos = listOf(gasto("ev_bol", "2026-11-01", 60_000, "SEGUROS BOLIVAR VIDA"))).seguros.single().tipo)
    }

    @Test
    fun `la seguridad social, la seguridad privada y la cuota de manejo no son seguros`() {
        val r = detectar(eventos = listOf(
            gasto("ev_pila", "2026-11-01", 450_000, "PAGO SEG SOCIAL"),
            gasto("ev_vig", "2026-11-01", 90_000, "SEGURIDAD PRIVADA EDIFICIO"),
            gasto("ev_cm", "2026-11-01", 45_000, "CUOTA DE MANEJO TARJETA", master),
            gasto("ev_cm2", "2026-11-01", 45_000, "CUOTA MANEJO PROTECCION", master),
        ))
        assertTrue(r.seguros.isEmpty() && r.porConfirmar.isEmpty())
    }

    @Test
    fun `Proteccion el fondo de pensiones no es un seguro`() {
        val r = detectar(eventos = listOf(
            gasto("ev_p1", "2026-11-01", 500_000, "APORTE VOLUNTARIO PROTECCION"),
            gasto("ev_p2", "2026-11-01", 300_000, "PROTECCION PENSIONES OBLIGATORIAS"),
        ))
        assertTrue(r.seguros.isEmpty() && r.porConfirmar.isEmpty())
    }

    @Test
    fun `prepagada sola no es salud, medicina prepagada si`() {
        assertTrue(detectar(eventos = listOf(gasto("ev_rec", "2026-11-01", 50_000, "RECARGA TARJETA PREPAGADA"))).let { it.seguros.isEmpty() && it.porConfirmar.isEmpty() })
        val s = detectar(eventos = mensual("mp", listOf("2026-10", "2026-11"), 420_000, "MEDICINA PREPAGADA SURA")).seguros.single()
        assertEquals(TipoDeSeguro.SALUD, s.tipo)
        assertEquals("Medicina prepagada", s.nombre)
    }

    @Test
    fun `asistencia con una palabra de tipo pregunta, y en un taller no es nada`() {
        val carro = detectar(eventos = listOf(gasto("ev_as", "2026-11-01", 80_000, "ASISTENCIA CARRO")))
        assertTrue(carro.seguros.isEmpty())
        assertEquals(1, carro.porConfirmar.size)
        val taller = detectar(eventos = listOf(gasto("ev_t", "2026-11-01", 80_000, "TALLER ASISTENCIA CARRO")))
        assertTrue(taller.seguros.isEmpty() && taller.porConfirmar.isEmpty())
        val tecnica = detectar(eventos = listOf(gasto("ev_t2", "2026-11-01", 80_000, "ASISTENCIA TECNICA HOGAR")))
        assertTrue(tecnica.seguros.isEmpty() && tecnica.porConfirmar.isEmpty())
    }

    // ── Los filtros de siempre ───────────────────────────────────────────────────────────────

    @Test
    fun `por confirmar, pagos de tarjeta, traspasos y liquidaciones no entran`() {
        val r = detectar(eventos = listOf(
            gasto("ev_pc", "2026-11-01", 50_000, "SEGURO VIDA ALLIANZ").copy(reconciliationStatus = ReconciliationStatus.UNCONFIRMED),
            gasto("ev_pt", "2026-11-01", 50_000, "PAGO SEGURO TARJETA", categoria = CARD_PAYMENT_CATEGORY),
            gasto("ev_tr", "2026-11-01", 50_000, "SEGURO CARRO", categoria = TRANSFER_CATEGORY),
            gasto("ev_li", "2026-11-01", 50_000, "Seguro del carro a Caro").copy(saldaEspacioId = "esp_casa", countsAsCashFlow = false),
        ))
        assertTrue(r.seguros.isEmpty() && r.porConfirmar.isEmpty())
    }

    @Test
    fun `un seguro compartido cuenta solo tu parte`() {
        val compartido = mensual("comp", listOf("2026-10", "2026-11"), 100_000, "SEGURO HOGAR ALLIANZ").map { it.copy(parteAjena = 50_000) }
        assertEquals(50_000, detectar(eventos = compartido).alMes)
    }

    @Test
    fun `los anulados no cuentan`() {
        val eventos = mensual("ex", listOf("2026-09", "2026-10", "2026-11"), 50_000, "SEGURO EXEQUIAS") +
            gasto("ev_b", "2026-10-15", 70_000, "SEGURO EXEQUIAS")
        val r = detectar(eventos = eventos, anulados = setOf("ev_b"))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.EXEQUIAS, s.tipo)
        assertFalse("ev_b" in s.cobros.map { it.movimientoId })
        assertEquals(50_000, r.alMes)
    }

    @Test
    fun `lo descartado sale de la lista y del total`() {
        val eventos = mensual("v", listOf("2026-10", "2026-11"), 50_000, "ALLIANZ VIDA") +
            mensual("d", listOf("2026-10", "2026-11"), 20_000, "SEGURO DESEMPLEO CARDIF")
        val antes = detectar(eventos = eventos)
        assertEquals(70_000, antes.alMes)
        val clave = antes.seguros.first { it.tipo == TipoDeSeguro.VIDA }.clave
        val despues = detectar(eventos = eventos, descartados = setOf(clave))
        assertEquals(listOf(TipoDeSeguro.DESEMPLEO), despues.seguros.map { it.tipo })
        assertEquals(20_000, despues.alMes)
    }

    @Test
    fun `lo que dejo de cobrarse se marca y no suma`() {
        val r = detectar(eventos = mensual("m", listOf("2026-05", "2026-06", "2026-07"), 40_000, "COLSANITAS MEDICINA PREPAGADA"))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.SALUD, s.tipo)
        assertFalse(s.sigueCobrandose)
        assertEquals(0, r.alMes)
        assertEquals(0, r.alAno)
    }

    @Test
    fun `un movimiento en la categoria Seguros cuenta aunque el texto no lo diga`() {
        val s = detectar(eventos = listOf(gasto("ev_tr", "2026-10-20", 250_000, "Todo riesgo del carro", categoria = "Seguros"))).seguros.single()
        assertEquals(TipoDeSeguro.AUTO, s.tipo)
        assertTrue(s.concluyente)
    }

    @Test
    fun `fuera de los ultimos doce meses no cuenta`() {
        assertTrue(detectar(eventos = listOf(gasto("ev_viejo", "2025-10-01", 50_000, "SEGURO VIDA ALLIANZ"))).seguros.isEmpty())
    }

    @Test
    fun `los dolares van aparte, en su moneda`() {
        val viaje = mensual("usd", listOf("2026-10", "2026-11"), 12, "ALLIANZ TRAVEL INSURANCE", master).map { it.copy(currency = "USD") }
        val r = detectar(eventos = viaje + mensual("cop", listOf("2026-10", "2026-11"), 30_000, "SEGURO VIDA DEUDOR TC", master))
        assertEquals(30_000, r.alMes, "los dólares no se suman a los pesos")
        assertEquals(mapOf("USD" to 12L), r.otrasMonedas)
        assertEquals("USD", r.seguros.single { it.cobros.first().movimientoId.startsWith("ev_usd") }.moneda)
    }

    // ── Las huellas y el tramo ───────────────────────────────────────────────────────────────

    @Test
    fun `las respuestas salen de las huellas, y gana la ultima de cada cuanto`() {
        val r = respuestasDeLasHuellas(listOf(
            huellaDelSeguro("acc_a:x"), huellaDelSeguroConfirmado("acc_b:y"), huellaDeCadaCuanto("acc_c:z", 12),
            HUELLA_DE_ME_INTERESA, "extracto:acc_nu:2026-10-12", huellaDeCadaCuanto("acc_d:w", 7), huellaDeCadaCuanto("acc_e:v", 3),
        ))
        assertEquals(setOf("acc_a:x"), r.descartados)
        assertEquals(setOf("acc_b:y"), r.confirmados)
        assertEquals(mapOf("acc_c:z" to 12, "acc_e:v" to 3), r.cadaCuanto, "un 7 no es una respuesta")
        assertTrue(r.meInteresa)
    }

    @Test
    fun `el tramo nunca es el monto`() {
        assertEquals("SIN_SEGUROS", tramoDeLoQuePagasEnSeguros(0))
        assertEquals("MENOS_DE_50K", tramoDeLoQuePagasEnSeguros(49_999))
        assertEquals("DE_50K_A_150K", tramoDeLoQuePagasEnSeguros(89_100))
        assertEquals("MAS_DE_600K", tramoDeLoQuePagasEnSeguros(2_000_000))
        for (monto in listOf(0L, 1L, 60_000L, 200_000L, 400_000L, 9_000_000L)) {
            assertTrue(ClaveDeUso.TRAMO.admite(tramoDeLoQuePagasEnSeguros(monto)))
        }
    }

    @Test
    fun `la clave entra en la huella`() {
        val largo = gasto("ev_l", "2026-11-01", 10_000, "SEGURO " + "X".repeat(400))
        val s = detectar(eventos = listOf(largo)).seguros.single()
        assertTrue(huellaDeCadaCuanto(s.clave, 12).length <= 300)
        assertNull(detectar(eventos = listOf(largo), descartados = setOf(s.clave)).seguros.firstOrNull())
        assertNotNull(s.ultimoCobro)
    }
}
