package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Las preguntas sugeridas: una regla por señal, ordenadas por lo que más le urge, siempre tres
 *
 * Se prueban sobre [SenalesParaPreguntar] —dos números por regla, sin armar un Inicio entero— y al
 * final una vez sobre un [DashboardData] parecido al del dueño, para fijar que el destilado lee los
 * campos correctos.
 */
class PreguntasSugeridasTest {

    private fun tres(senales: SenalesParaPreguntar) = preguntasSugeridas(senales).also {
        assertEquals(CUANTAS_PREGUNTAS_SUGERIDAS, it.size, "siempre son tres: $it")
        assertEquals(it.distinct(), it, "sin repetidas: $it")
    }

    @Test
    fun `sin ningun dato salen las tres de respaldo, en su orden`() {
        assertEquals(PREGUNTAS_DE_RESPALDO, tres(SenalesParaPreguntar()))
    }

    @Test
    fun `si salio mas de lo que entro, la pregunta lo dice con la diferencia`() {
        val preguntas = tres(SenalesParaPreguntar(ingresosDelPeriodo = 22_200_000L, gastosDelPeriodo = 33_900_000L))
        assertEquals("¿Por qué este período salieron \$11,7M más de los que entraron?", preguntas.first())
    }

    @Test
    fun `si entro mas de lo que salio, no se inventa un problema`() {
        val preguntas = tres(SenalesParaPreguntar(ingresosDelPeriodo = 10_000_000L, gastosDelPeriodo = 4_000_000L))
        assertTrue(preguntas.none { "salieron" in it }, "$preguntas")
    }

    @Test
    fun `un presupuesto pasado pregunta por esa categoria`() {
        val preguntas = tres(SenalesParaPreguntar(presupuestoMasPasado = PresupuestoPasado("Fútbol", 563_456L)))
        assertEquals("¿Qué hago para no pasarme en Fútbol el próximo período?", preguntas.first())
    }

    @Test
    fun `con deudas de tasas distintas pregunta cual abonar primero`() {
        val preguntas = tres(
            SenalesParaPreguntar(
                deudas = listOf(
                    DeudaParaPreguntar("Vehículo 8761", 18.5, noBaja = false),
                    DeudaParaPreguntar("Hipoteca 1254", 12.0, noBaja = false),
                ),
            ),
        )
        assertEquals("¿Qué deuda me conviene abonar primero?", preguntas.first())
    }

    @Test
    fun `con una sola tasa no hay nada que elegir`() {
        val preguntas = tres(
            SenalesParaPreguntar(
                deudas = listOf(
                    DeudaParaPreguntar("A", 18.5, noBaja = false),
                    DeudaParaPreguntar("B", 18.5, noBaja = false),
                ),
            ),
        )
        assertTrue("¿Qué deuda me conviene abonar primero?" !in preguntas)
    }

    @Test
    fun `intereses altos preguntan como bajarlos, con la cifra`() {
        val preguntas = tres(SenalesParaPreguntar(interesesPropiosAlMes = 2_300_000L))
        assertEquals("Pago \$2,3M de intereses al mes, ¿qué hago para bajarlo?", preguntas.first())
    }

    @Test
    fun `intereses bajos no se sugieren`() {
        val preguntas = tres(SenalesParaPreguntar(ingresosDelPeriodo = 20_000_000L, interesesPropiosAlMes = 90_000L))
        assertTrue(preguntas.none { "intereses" in it }, "$preguntas")
    }

    /** Quien gana poco nota los intereses antes: el umbral baja a la décima parte de lo que entró. */
    @Test
    fun `el umbral de intereses se ajusta a lo que entra`() {
        val preguntas = tres(SenalesParaPreguntar(ingresosDelPeriodo = 3_000_000L, interesesPropiosAlMes = 320_000L))
        assertTrue(preguntas.any { "intereses" in it }, "$preguntas")
    }

    @Test
    fun `una deuda que no baja es lo primero, y se nombra la mas grande`() {
        val preguntas = tres(
            SenalesParaPreguntar(
                ingresosDelPeriodo = 1L,
                gastosDelPeriodo = 2L,
                presupuestoMasPasado = PresupuestoPasado("Fútbol", 1L),
                deudas = listOf(
                    DeudaParaPreguntar("Crédito Mamá", 0.0, noBaja = true, saldo = 20_000_000L),
                    DeudaParaPreguntar("Techo", 24.0, noBaja = true, saldo = 50_000_000L),
                ),
            ),
        )
        assertEquals("¿Por qué Techo no baja aunque pago la cuota?", preguntas.first())
    }

    @Test
    fun `si lo que falta por pagar no cabe en tu plata, eso va arriba`() {
        val preguntas = tres(
            SenalesParaPreguntar(
                ingresosDelPeriodo = 1L,
                gastosDelPeriodo = 2L,
                faltaPorPagar = 4_100_000L,
                tuPlata = 558_350L,
            ),
        )
        assertEquals("¿Me alcanza para los \$4,1M que me faltan por pagar este período?", preguntas.first())
    }

    @Test
    fun `el orden va por relevancia y lo que sobra lo llena el respaldo`() {
        val preguntas = tres(
            SenalesParaPreguntar(
                ingresosDelPeriodo = 22_200_000L,
                gastosDelPeriodo = 33_900_000L,
                presupuestoMasPasado = PresupuestoPasado("Fútbol", 563_456L),
                deudas = listOf(
                    DeudaParaPreguntar("Vehículo 8761", 18.5, noBaja = false),
                    DeudaParaPreguntar("Hipoteca 1254", 12.0, noBaja = false),
                ),
                bienes = 1_411_903_920L,
                deudasTotales = 2_191_000_000L,
            ),
        )
        assertEquals(
            listOf(
                "¿Qué hago para no pasarme en Fútbol el próximo período?",
                "¿Por qué este período salieron \$11,7M más de los que entraron?",
                "¿Qué deuda me conviene abonar primero?",
            ),
            preguntas,
        )

        val soloUna = tres(SenalesParaPreguntar(presupuestoMasPasado = PresupuestoPasado("Fútbol", 1L)))
        assertEquals(listOf(soloUna.first()) + PREGUNTAS_DE_RESPALDO.take(2), soloUna)
    }

    /** Todo lo que se puede sugerir, junto: ninguna forma voseante (el texto es de usuario). */
    @Test
    fun `ninguna sugerencia vosea`() {
        val voseo = Regex(
            "(?<!\\p{L})(vos|sos|tenés|podés|querés|sabés|hacés|decís|mirá|tocá|probá|acá|pagás|debés|gastás)(?!\\p{L})",
            RegexOption.IGNORE_CASE,
        )
        // Una señal por vez, para que cada plantilla salga seguro entre las tres (juntas, el tope de
        // tres dejaría afuera las de menos peso y la prueba no las vería nunca).
        val todas = listOf(
            SenalesParaPreguntar(ingresosDelPeriodo = 1_000_000L, gastosDelPeriodo = 2_000_000L),
            SenalesParaPreguntar(presupuestoMasPasado = PresupuestoPasado("Fútbol", 1L)),
            SenalesParaPreguntar(deudas = listOf(DeudaParaPreguntar("A", 18.5, noBaja = true), DeudaParaPreguntar("B", 12.0, noBaja = false))),
            SenalesParaPreguntar(interesesPropiosAlMes = 2_000_000L),
            SenalesParaPreguntar(faltaPorPagar = 9_000_000L, tuPlata = 1L),
            SenalesParaPreguntar(bienes = 1L, deudasTotales = 1L),
        ).flatMap { preguntasSugeridas(it) } +
            listOf(saludoDelChat("Camilo"), QUE_MIRA_MOVI, ROTULO_DE_LAS_SUGERENCIAS, NO_REEMPLAZA_A_UN_ASESOR)
        assertTrue(todas.any { "patrimonio" in it }, "la de menos peso también tiene que haberse visto: $todas")
        todas.forEach { assertTrue(voseo.find(it) == null, "voseo en «$it»") }
    }

    // ── Desde el Inicio de verdad ────────────────────────────────────────────

    private fun deuda(id: String, nombre: String, saldo: Long, tasa: Double, cuota: Long, laPaga: String? = null) =
        CreditSummary(
            account = Account(id, nombre, AccountType.LOAN, saldo),
            terms = CreditTerms(
                accountId = id, bank = "Banco", principal = saldo, rateEa = tasa, termMonths = 120,
                installment = cuota, dayOfMonth = 5, startDate = "2024-01-05", paidBy = laPaga,
            ),
            paidPct = 0.0,
        )

    @Test
    fun `desde un Inicio parecido al del duenio salen sus tres preguntas`() {
        val data = DashboardData(
            summary = FinanceSummary(Scope.SELF, balance = 0L, ingresos = 22_200_000L, egresos = 33_900_000L),
            accounts = listOf(
                Account("nu", "Nu", AccountType.SAVINGS, 558_350L),
                Account(
                    "casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
                    bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "h"),
                ),
            ),
            credits = listOf(
                deuda("v", "Vehículo 8761", 40_000_000L, 18.5, 1_500_000L),
                deuda("h", "Hipoteca 1254", 400_000_000L, 12.0, 4_500_000L, laPaga = "Skandia"),
            ),
            budgets = listOf(Budget("Fútbol", 400_000L), Budget("Mercado", 1_000_000L)),
            spentByCategory = mapOf("Fútbol" to 963_456L, "Mercado" to 1_200_000L),
        )

        val senales = senalesDe(data)
        // El que MÁS se pasó, no el primero de la lista.
        assertEquals(PresupuestoPasado("Fútbol", 563_456L), senales.presupuestoMasPasado)
        // Los intereses de la hipoteca los gira Skandia: no son de su bolsillo.
        val soloElVehiculo = senalesDe(data.copy(credits = data.credits!!.take(1))).interesesPropiosAlMes
        assertEquals(soloElVehiculo, senales.interesesPropiosAlMes)
        assertEquals(1_411_903_920L, senales.bienes)
        assertTrue(soloElVehiculo >= 500_000L, "el vehículo tiene que pasar el umbral: $soloElVehiculo")

        assertEquals(
            listOf(
                "¿Qué hago para no pasarme en Fútbol el próximo período?",
                "¿Por qué este período salieron \$11,7M más de los que entraron?",
                "Pago ${formatMoneyCompact(soloElVehiculo)} de intereses al mes, ¿qué hago para bajarlo?",
            ),
            preguntasSugeridas(data),
        )
    }

    @Test
    fun `un Inicio que todavia no cargo no afirma nada`() {
        assertEquals(PREGUNTAS_DE_RESPALDO, preguntasSugeridas(DashboardData()))
    }
}
