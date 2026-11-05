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
 * Sin el detector, `losSegurosQuePagas` no existe: cada prueba falla al compilar.
 */
class LosSegurosQuePagasTest {

    private val hoy = "2026-11-05"
    private val zona = TimeZone.UTC
    private val diaDe: (Long) -> String = { Instant.fromEpochMilliseconds(it).toLocalDateTime(zona).date.toString() }

    private val ahorros = Account("acc_ahorros", "Ahorros 8133", AccountType.SAVINGS, 0)
    private val master = Account("acc_master", "Master Black", AccountType.CREDIT_CARD, 0)
    private val vehiculo = Account("acc_vehiculo", "Vehículo 4083", AccountType.LOAN, -90_000_000)
    private val cuentas = listOf(ahorros, master, vehiculo)

    private fun en(fecha: String): Long = LocalDate.parse(fecha).atStartOfDayIn(zona).toEpochMilliseconds() + 12 * 3_600_000L

    private fun gasto(
        id: String,
        fecha: String,
        monto: Long,
        descripcion: String,
        cuenta: Account = ahorros,
        categoria: String = "Otros",
    ) = FinancialEvent(
        id = id, accountId = cuenta.id, type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = descripcion, timestamp = en(fecha),
    )

    private fun credito(seguro: Long?, paidBy: String? = null) = CreditSummary(
        account = vehiculo,
        terms = CreditTerms(
            accountId = vehiculo.id, bank = "Occidente", principal = 120_000_000, rateEa = 17.4, termMonths = 72,
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
        anulados: Set<String> = emptySet(),
    ) = losSegurosQuePagas(eventos, cuentas, creditos, reglas, suscripciones, hoy, diaDe, descartados, confirmados, anulados)

    @Test
    fun `el seguro de la cuota de un credito va dentro de la cuota`() {
        val cuota = FinancialEvent(
            id = "ev_cuota", accountId = vehiculo.id, type = TransactionType.INCOME, amount = 4_101_122,
            category = CUOTA_CATEGORY, description = "Cuota de Vehículo 4083", timestamp = en("2026-11-03"),
        )
        val r = detectar(eventos = listOf(cuota), creditos = listOf(credito(89_100)))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.VIDA_DEL_CREDITO, s.tipo)
        assertEquals("Seguro de vida · Vehículo 4083", s.nombre)
        assertEquals(89_100, s.alMes)
        assertTrue(s.dentroDeLaCuota)
        assertEquals(vehiculo.id, s.creditoId)
        assertEquals("2026-11-03", s.ultimoCobro)
        assertEquals(89_100, r.alMes)
        assertEquals(89_100L * 12, r.alAno)
    }

    @Test
    fun `un credito sin seguro, o que paga otro, no trae seguro`() {
        assertTrue(detectar(creditos = listOf(credito(null))).seguros.isEmpty())
        assertTrue(detectar(creditos = listOf(credito(0))).seguros.isEmpty())
        assertTrue(detectar(creditos = listOf(credito(89_100, paidBy = "Mi papá"))).seguros.isEmpty())
    }

    @Test
    fun `el seguro de la tarjeta cobrado seis meses es uno, con su promedio`() {
        val montos = listOf(31_000L, 31_000L, 32_000L, 32_000L, 33_000L, 33_000L)
        val meses = listOf("2026-06-02", "2026-07-02", "2026-08-02", "2026-09-02", "2026-10-02", "2026-11-02")
        val eventos = meses.zip(montos).mapIndexed { i, (f, m) -> gasto("ev_seg$i", f, m, "SEGURO VIDA DEUDOR TC", master) }
        val r = detectar(eventos = eventos)
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.DE_TARJETA, s.tipo)
        assertEquals("Seguro de la tarjeta Master Black", s.nombre)
        assertEquals(6, s.cobros.size)
        assertEquals(montos.sum() / 6, s.alMes)
        assertEquals("2026-11-02", s.ultimoCobro)
        assertTrue(s.sigueCobrandose)
        assertEquals(s.alMes, r.alMes)
    }

    @Test
    fun `el SOAT es anual y se reparte en doce meses`() {
        val soat = gasto("ev_soat", "2026-03-15", 1_200_000, "PAGO PSE SOAT SURA")
        val r = detectar(eventos = listOf(soat))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.AUTO, s.tipo)
        assertEquals("SOAT", s.nombre)
        assertEquals(12, s.mesesEntreCobros)
        assertEquals(1_200_000, s.montoDelCiclo)
        assertEquals(100_000, s.alMes)
        assertTrue(s.sigueCobrandose, "un anual de marzo sigue vigente en noviembre")
        assertEquals(100_000, r.alMes)
        assertEquals(1_200_000, r.alAno)
        assertTrue(r.conAnuales)
    }

    @Test
    fun `Sura de una EPS no es un seguro concluyente`() {
        val eps = gasto("ev_eps", "2026-10-30", 380_000, "PAGO SURA EPS")
        val r = detectar(eventos = listOf(eps))
        assertTrue(r.seguros.isEmpty())
        val duda = r.porConfirmar.single()
        assertFalse(duda.concluyente)
        assertEquals(0, r.alMes)
        // Con «Sí, es un seguro» cuenta.
        val confirmado = detectar(eventos = listOf(eps), confirmados = setOf(duda.clave))
        assertTrue(confirmado.seguros.single().confirmado)
        assertEquals(380_000, confirmado.alMes)
    }

    @Test
    fun `Sura de una ARL tampoco`() {
        val arl = gasto("ev_arl", "2026-10-30", 9_000, "APORTE ARL SURA")
        val r = detectar(eventos = listOf(arl))
        assertTrue(r.seguros.isEmpty())
        assertEquals(1, r.porConfirmar.size)
    }

    @Test
    fun `una panaderia Bolivar no es un seguro`() {
        val pan = gasto("ev_pan", "2026-11-01", 12_500, "PANADERIA BOLIVAR")
        val calle = gasto("ev_calle", "2026-11-01", 40_000, "PARQUEADERO CALLE BOLIVAR")
        val r = detectar(eventos = listOf(pan, calle))
        assertTrue(r.seguros.isEmpty())
        assertTrue(r.porConfirmar.isEmpty())
        assertEquals(0, r.alMes)
        // Seguros Bolívar sí.
        val seguro = detectar(eventos = listOf(gasto("ev_bol", "2026-11-01", 60_000, "SEGUROS BOLIVAR VIDA")))
        assertEquals(TipoDeSeguro.VIDA, seguro.seguros.single().tipo)
    }

    @Test
    fun `la seguridad social y la seguridad privada no son seguros`() {
        val pila = gasto("ev_pila", "2026-11-01", 450_000, "PAGO SEG SOCIAL")
        val vigilancia = gasto("ev_vig", "2026-11-01", 90_000, "SEGURIDAD PRIVADA EDIFICIO")
        val r = detectar(eventos = listOf(pila, vigilancia))
        assertTrue(r.seguros.isEmpty() && r.porConfirmar.isEmpty())
    }

    @Test
    fun `los anulados no cuentan`() {
        val cobro = gasto("ev_a", "2026-11-01", 50_000, "SEGURO EXEQUIAS")
        val anulado = gasto("ev_b", "2026-10-01", 70_000, "SEGURO EXEQUIAS")
        val r = detectar(eventos = listOf(cobro, anulado), anulados = setOf("ev_b"))
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.EXEQUIAS, s.tipo)
        assertEquals(listOf("ev_a"), s.cobros.map { it.movimientoId })
        assertEquals(50_000, r.alMes)
    }

    @Test
    fun `lo descartado sale de la lista y del total`() {
        val vida = gasto("ev_v", "2026-11-01", 50_000, "ALLIANZ VIDA")
        val desempleo = gasto("ev_d", "2026-11-01", 20_000, "SEGURO DESEMPLEO CARDIF")
        val antes = detectar(eventos = listOf(vida, desempleo))
        assertEquals(70_000, antes.alMes)
        val clave = antes.seguros.first { it.tipo == TipoDeSeguro.VIDA }.clave
        val despues = detectar(eventos = listOf(vida, desempleo), descartados = setOf(clave))
        assertEquals(listOf(TipoDeSeguro.DESEMPLEO), despues.seguros.map { it.tipo })
        assertEquals(20_000, despues.alMes)
    }

    @Test
    fun `lo que dejo de cobrarse se marca y no suma`() {
        val viejos = listOf("2026-05-10", "2026-06-10", "2026-07-10").mapIndexed { i, f -> gasto("ev_m$i", f, 40_000, "COLSANITAS MEDICINA PREPAGADA") }
        val r = detectar(eventos = viejos)
        val s = r.seguros.single()
        assertEquals(TipoDeSeguro.SALUD, s.tipo)
        assertEquals("Medicina prepagada", s.nombre)
        assertFalse(s.sigueCobrandose)
        assertEquals(0, r.alMes)
        assertEquals(0, r.alAno)
    }

    @Test
    fun `un movimiento en la categoria Seguros cuenta aunque el texto no lo diga`() {
        val aMano = gasto("ev_tr", "2026-10-20", 250_000, "Todo riesgo del carro", categoria = "Seguros")
        val s = detectar(eventos = listOf(aMano)).seguros.single()
        assertEquals(TipoDeSeguro.AUTO, s.tipo)
        assertTrue(s.concluyente)
    }

    @Test
    fun `fuera de los ultimos doce meses no cuenta`() {
        val viejo = gasto("ev_viejo", "2025-10-01", 50_000, "SEGURO VIDA ALLIANZ")
        assertTrue(detectar(eventos = listOf(viejo)).seguros.isEmpty())
    }

    @Test
    fun `una regla de seguros da el nombre y cada cuantos meses`() {
        val regla = RecurringRule(
            id = "rr_poliza", name = "Póliza del apartamento", category = "Seguros", amount = 600_000, dayOfMonth = 10,
            type = TransactionType.EXPENSE, frecuencia = FrecuenciaDelPago.SEMESTRAL, primerVencimiento = "2026-02-10",
        )
        val pago = gasto("ev_pol", "2026-08-10", 600_000, "Póliza del apartamento")
        val s = detectar(eventos = listOf(pago), reglas = listOf(regla)).seguros.single()
        assertEquals("Póliza del apartamento", s.nombre)
        assertEquals(6, s.mesesEntreCobros)
        assertEquals(100_000, s.alMes)
    }

    @Test
    fun `protecciones y asistencias solas preguntan`() {
        val r = detectar(eventos = listOf(gasto("ev_as", "2026-11-01", 15_000, "ASISTENCIA HOGAR TOTAL")))
        // «Asistencia» con el tipo (hogar) es concluyente; sola, no.
        assertEquals(TipoDeSeguro.HOGAR, r.seguros.single().tipo)
        val sola = detectar(eventos = listOf(gasto("ev_as2", "2026-11-01", 15_000, "ASISTENCIA PREMIUM")))
        assertTrue(sola.seguros.isEmpty())
        assertEquals(1, sola.porConfirmar.size)
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
        assertTrue(huellaDelSeguroConfirmado(s.clave).length <= 300)
        assertNull(detectar(eventos = listOf(largo), descartados = setOf(s.clave)).seguros.firstOrNull())
        assertNotNull(s.ultimoCobro)
    }
}
