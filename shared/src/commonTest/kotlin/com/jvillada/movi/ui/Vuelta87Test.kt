package com.jvillada.movi.ui

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.porcentajeDePresupuesto
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.MovimientoDeLaCaja
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.buscar.DatosDeBusqueda
import com.jvillada.movi.ui.buscar.FiltrosDeBusqueda
import com.jvillada.movi.ui.buscar.RangoDelPeriodo
import com.jvillada.movi.ui.buscar.lineasDeLosTotales
import com.jvillada.movi.ui.buscar.totalesDeLaBusqueda
import com.jvillada.movi.ui.components.partirLaColaDelNombreCorto
import com.jvillada.movi.ui.dashboard.AccionDeLaPropuesta
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.SIN_PAGOS_PENDIENTES
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import com.jvillada.movi.ui.dashboard.renglonMeAlcanza
import com.jvillada.movi.ui.primeruso.subtituloDeTuSalario
import com.jvillada.movi.ui.quickadd.gastosRecientesParaRepetir
import com.jvillada.movi.ui.quickadd.candidatosAlUltimoPago
import com.jvillada.movi.ui.sdui.CONTRA_EL_PASADO_MUY_CORTO
import com.jvillada.movi.ui.sdui.FormaDeLaCategoriaDelWidget
import com.jvillada.movi.ui.sdui.formaDeLaCategoriaDelWidget
import com.jvillada.movi.ui.sdui.textoDeContraQue
import com.jvillada.movi.ui.dashboard.CONTRA_EL_PERIODO_PASADO
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 87 · el día uno y los bordes (lo puro)
 *
 * La cola del nombre corto, la forma del widget «En qué se va» en media columna, los totales de Buscar con
 * lo compartido, los recientes por frecuencia, el reparto que «Anotar pago» recuerda, el primer día del
 * renglón «¿Me alcanza?» y el presupuesto pasado en «Movi propone hoy». Lo que se ve en pantalla lo mide
 * `Vuelta87EnPantallaTest`.
 */
class Vuelta87Test {

    // ── (8) la cola del nombre corto ─────────────────────────────────────────

    @Test
    fun `un subtitulo que termina en un nombre corto se parte en cabeza y cola`() {
        val p = partirLaColaDelNombreCorto("Vivienda · Bancolombia ·4057")
        assertEquals("Vivienda · Bancolombia", p?.cabeza)
        assertEquals("·4057", p?.cola)
        assertEquals("·3684 USD", partirLaColaDelNombreCorto("Compras · Master ·3684 USD")?.cola)
        assertEquals("Bancolombia", partirLaColaDelNombreCorto("Bancolombia ·4057")?.cabeza)
    }

    @Test
    fun `lo que no termina en un nombre corto con numero no se parte`() {
        assertNull(partirLaColaDelNombreCorto("Nu · Mercado · 28 oct"))
        assertNull(partirLaColaDelNombreCorto("Transporte · Nu"))
        assertNull(partirLaColaDelNombreCorto("·4057"))
        assertNull(partirLaColaDelNombreCorto("Bancolombia ·4057 · Mercado"))
    }

    // ── (9) En qué se va a media columna ─────────────────────────────────────

    @Test
    fun `a media columna la pildora baja y el nombre conserva su ancho`() {
        // 1280: el widget mide ~244 y sin márgenes ~212; un teléfono de 390, ~326; uno de 360, ~296.
        assertEquals(FormaDeLaCategoriaDelWidget.PILDORA_ABAJO, formaDeLaCategoriaDelWidget(212.dp, 1f))
        assertEquals(FormaDeLaCategoriaDelWidget.UN_RENGLON, formaDeLaCategoriaDelWidget(326.dp, 1f))
        assertEquals(FormaDeLaCategoriaDelWidget.UN_RENGLON, formaDeLaCategoriaDelWidget(296.dp, 1f))
        // Con la letra grande, a 360 baja antes de cortar el nombre.
        assertEquals(FormaDeLaCategoriaDelWidget.PILDORA_ABAJO, formaDeLaCategoriaDelWidget(296.dp, 1.3f))
        // La letra de la app (×1,12): a 390 y a 360 sigue en un renglón; a 1280, no.
        assertEquals(FormaDeLaCategoriaDelWidget.UN_RENGLON, formaDeLaCategoriaDelWidget(296.dp, 1.12f))
        assertEquals(FormaDeLaCategoriaDelWidget.PILDORA_ABAJO, formaDeLaCategoriaDelWidget(212.dp, 1.12f))
    }

    @Test
    fun `contra el periodo pasado se acorta con sentido cuando no cabe`() {
        assertEquals(CONTRA_EL_PERIODO_PASADO, textoDeContraQue(326.dp, 1f))
        assertEquals("vs. período pasado", textoDeContraQue(215.dp, 1f))
        assertEquals(CONTRA_EL_PASADO_MUY_CORTO, textoDeContraQue(190.dp, 1f))
        // La letra de la app es ×1,12: a media columna de 1280 (212 dp útiles) es la más corta; a 390 y 360, la entera.
        assertEquals(CONTRA_EL_PASADO_MUY_CORTO, textoDeContraQue(212.dp, 1.12f))
        assertEquals(CONTRA_EL_PERIODO_PASADO, textoDeContraQue(296.dp, 1.12f))
    }

    // ── (10) el primer día ───────────────────────────────────────────────────

    private val hoy = LocalDate(2026, 10, 30)

    @Test
    fun `sin nada por venir el renglon no dice tu dia mas justo hoy`() {
        val plana = CajaProyectada(
            tuPlataHoy = 3_250_000, gastoDiario = null,
            dias = listOf(DiaDeLaCaja("2026-10-30", 3_250_000), DiaDeLaCaja("2026-11-24", 3_250_000)),
            hastaElPeriodoSiguiente = true,
        )
        val renglon = renglonMeAlcanza(plana, hoy)
        assertEquals("Te alcanza hasta el fin del período · $SIN_PAGOS_PENDIENTES", renglon.texto)
    }

    @Test
    fun `con un pago por venir sigue diciendo el dia mas justo`() {
        val conPago = CajaProyectada(
            tuPlataHoy = 3_250_000, gastoDiario = null,
            dias = listOf(
                DiaDeLaCaja("2026-10-30", 3_250_000),
                DiaDeLaCaja("2026-11-05", 2_000_000, movimientos = listOf(MovimientoDeLaCaja("Arriendo", -1_250_000))),
            ),
            hastaElPeriodoSiguiente = true,
        )
        assertEquals("Te alcanza hasta el fin del período · tu día más justo, el 5 nov", renglonMeAlcanza(conPago, hoy).texto)
        // Con gasto del día a día estimado tampoco es una caja plana.
        val conGasto = CajaProyectada(
            tuPlataHoy = 3_250_000, gastoDiario = 20_000,
            dias = listOf(DiaDeLaCaja("2026-10-30", 3_250_000), DiaDeLaCaja("2026-11-24", 2_000_000)),
            hastaElPeriodoSiguiente = true,
        )
        assertTrue(renglonMeAlcanza(conGasto, hoy).texto.contains("tu día más justo"))
    }

    @Test
    fun `el subtitulo de Tu salario pide el dia, o dice cuanto y cuando`() {
        assertEquals("Elige antes el día de pago", subtituloDeTuSalario(null, null))
        assertEquals("Cuánto te pagan el día 25", subtituloDeTuSalario(25, null))
        assertEquals("\$6.500.000 · el día 25", subtituloDeTuSalario(25, 6_500_000))
    }

    // ── (11) Buscar con totales ──────────────────────────────────────────────

    private val periodo = RangoDelPeriodo(desde = 10_000L, hasta = 20_000L)
    private val casa = Espacio(id = "esp", nombre = "Casa con Andrés", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_a", "Andrés")))

    private fun ev(
        id: String,
        descripcion: String,
        monto: Long,
        ts: Long,
        tipo: TransactionType = TransactionType.EXPENSE,
        categoria: String = "Transporte",
        parteAjena: Long = 0L,
        moneda: String = "COP",
        cuenta: String = "nu",
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
        traspaso: String? = null,
        flujo: Boolean = true,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, currency = moneda, category = categoria, description = descripcion,
        timestamp = ts, parteAjena = parteAjena, espacioId = if (parteAjena > 0) casa.id else null, createdAt = ts,
        reconciliationStatus = estado, transferId = traspaso, countsAsCashFlow = flujo,
    )

    private val cuentas = listOf(Account("nu", "Nu", AccountType.SAVINGS, 0))

    @Test
    fun `los totales suman tu parte de lo compartido, en total y en el periodo`() {
        val datos = DatosDeBusqueda(
            cuentas = cuentas,
            eventos = listOf(
                ev("u1", "Uber", 20_000, ts = 15_000),
                ev("u2", "Uber", 30_000, ts = 5_000),
                // Repartido: de $40.000, $20.000 son del otro; suma tu parte.
                ev("u3", "Uber", 40_000, ts = 16_000, parteAjena = 20_000),
            ),
        )
        val t = totalesDeLaBusqueda("uber", datos, periodo = periodo)!!
        assertEquals(3, t.cuantos)
        assertEquals(70_000L, t.gastos?.total)
        assertEquals(40_000L, t.gastos?.delPeriodo, "solo lo del período: 20.000 + tu parte de 40.000")
        assertNull(t.ingresos)
        assertEquals(listOf("Gastaste \$70.000 en total · \$40.000 este período"), lineasDeLosTotales(t))
    }

    @Test
    fun `los traspasos, lo pendiente y otra moneda no entran a la suma`() {
        val datos = DatosDeBusqueda(
            cuentas = cuentas,
            eventos = listOf(
                ev("a", "Uber", 20_000, ts = 15_000),
                ev("b", "Uber traspaso", 99_000, ts = 15_000, categoria = "Traspaso", traspaso = "t1", flujo = false),
                ev("c", "Uber", 77_000, ts = 15_000, estado = ReconciliationStatus.UNCONFIRMED),
                ev("d", "Uber", 12, ts = 15_000, moneda = "USD"),
            ),
        )
        val t = totalesDeLaBusqueda("uber", datos, periodo = periodo)!!
        assertEquals(20_000L, t.gastos?.total)
        assertEquals(1, t.enOtraMoneda)
        assertEquals(1, t.porConfirmar)
        assertEquals(
            listOf("Gastaste \$20.000 en total · \$20.000 este período", "Sin contar 1 por confirmar", "Sin contar 1 en otra moneda"),
            lineasDeLosTotales(t),
        )
    }

    @Test
    fun `con ingresos van aparte y con el chip del periodo se dice una sola vez`() {
        val datos = DatosDeBusqueda(
            cuentas = cuentas,
            eventos = listOf(
                ev("g", "Rappi", 30_000, ts = 15_000),
                ev("i", "Rappi reembolso", 10_000, ts = 15_000, tipo = TransactionType.INCOME, categoria = "Reembolso"),
            ),
        )
        val t = totalesDeLaBusqueda("rappi", datos, periodo = periodo)!!
        assertEquals(
            listOf("Gastaste \$30.000 en total · \$30.000 este período", "Te entró \$10.000 en total · \$10.000 este período"),
            lineasDeLosTotales(t),
        )
        val delPeriodo = totalesDeLaBusqueda("rappi", datos, FiltrosDeBusqueda(soloEstePeriodo = true), periodo)
        assertEquals(listOf("Gastaste \$30.000 este período", "Te entró \$10.000 este período"), lineasDeLosTotales(delPeriodo, soloEstePeriodo = true))
    }

    @Test
    fun `si hay mas movimientos que los listados lo dice, y sin coincidencias no hay totales`() {
        val muchos = (1..40).map { ev("m$it", "Uber", 1_000, ts = 15_000L + it) }
        val t = totalesDeLaBusqueda("uber", DatosDeBusqueda(cuentas = cuentas, eventos = muchos), periodo = periodo)!!
        assertEquals(40, t.cuantos)
        assertEquals(40_000L, t.gastos?.total, "suma los 40, no solo los 30 listados")
        assertEquals("Los 30 más recientes de 40", lineasDeLosTotales(t).first())
        assertNull(totalesDeLaBusqueda("zzz", DatosDeBusqueda(cuentas = cuentas, eventos = muchos), periodo = periodo))
        assertTrue(lineasDeLosTotales(null).isEmpty())
    }

    // ── Menores: Repetir uno reciente por frecuencia ─────────────────────────

    private fun gasto(id: String, comercio: String, ts: Long, monto: Long = 10_000, cuenta: String = "nu") = FinancialEvent(
        id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = "Transporte", description = comercio,
        timestamp = ts, createdAt = ts,
    )

    @Test
    fun `los recientes son los que mas se repiten, no el ultimo confirmado`() {
        val eventos = buildList {
            // Uber, 30 veces; Rappi, 3; y un aviso recién confirmado de Carulla (una vez, el más reciente).
            repeat(30) { add(gasto("u$it", "Uber Rides", ts = 1_000L + it, monto = 8_000L + it)) }
            repeat(3) { add(gasto("r$it", "Rappi Colombia", ts = 500L + it)) }
            add(gasto("c", "Carulla Calle 85", ts = 9_000, monto = 186_450))
        }
        val recientes = gastosRecientesParaRepetir(eventos, setOf("nu"))
        assertEquals(listOf("Uber Rides", "Rappi Colombia", "Carulla Calle 85"), recientes.map { it.rotulo })
        assertEquals(8_029L, recientes.first().monto, "el monto es el del último Uber")
    }

    @Test
    fun `con una sola vez cada uno el orden es el de siempre, el mas nuevo primero`() {
        val eventos = listOf(gasto("a", "Panadería", 1_000), gasto("b", "Cine", 3_000), gasto("c", "Taxi", 2_000))
        assertEquals(listOf("Cine", "Taxi", "Panadería"), gastosRecientesParaRepetir(eventos, setOf("nu")).map { it.rotulo })
    }

    // ── (12) Anotar pago recuerda el reparto ─────────────────────────────────

    private val ahora = 100L * 24 * 60 * 60 * 1000
    private val dia = 24L * 60 * 60 * 1000

    private fun pago(id: String, descripcion: String, ts: Long, monto: Long = 1_800_000, espacio: Boolean = false) = FinancialEvent(
        id = id, accountId = "nu", type = TransactionType.EXPENSE, amount = monto, category = "Vivienda",
        description = descripcion, timestamp = ts, createdAt = ts,
        parteAjena = if (espacio) 900_000 else 0, espacioId = if (espacio) casa.id else null,
    )

    @Test
    fun `los candidatos al ultimo pago se eligen por monto y fecha, nunca por nombre`() {
        val historia = listOf(
            pago("viejo", "Arriendo", ahora - 100 * dia),
            pago("aviso", "Transferencia a Andrés", ahora - 30 * dia),
            pago("otro-monto", "Arriendo", ahora - 20 * dia, monto = 50_000),
            pago("reciente", "Arriendo", ahora - 2 * dia, monto = 1_850_000),
            pago("traspaso", "Arriendo", ahora - 1 * dia).copy(transferId = "t"),
        )
        // Más reciente primero; sin el viejo (fuera de 75 días), el de otro monto ni la pata de un traspaso.
        assertEquals(listOf("reciente", "aviso"), candidatosAlUltimoPago(1_800_000, historia, ahora).map { it.id })
        assertTrue(candidatosAlUltimoPago(0, historia, ahora).isEmpty())
    }

    // ── Menores: un presupuesto pasado se propone ────────────────────────────

    @Test
    fun `un presupuesto pasado aparece en Movi propone hoy con la regla de Presupuestos`() {
        val datos = DashboardData(
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            budgets = listOf(Budget("Restaurantes", 600_000), Budget("Mercado", 1_000_000)),
            spentByCategory = mapOf("Restaurantes" to 1_002_000L, "Mercado" to 400_000L),
        )
        val propuesta = propuestasDelInicio(datos).single { it.tipo == TipoDePropuesta.PRESUPUESTO }
        assertEquals("Restaurantes va al 167\u00A0% de su presupuesto", propuesta.titulo)
        assertEquals(402_000L, propuesta.cifra, "lo que se pasó")
        assertEquals(AccionDeLaPropuesta.Ir(Screen.Budgets), propuesta.alTocar)
        assertEquals(1, propuesta.cuantas)
        // Dentro del límite o justo en él no se propone (la misma regla de Presupuestos).
        val enElLimite = datos.copy(spentByCategory = mapOf("Restaurantes" to 600_000L, "Mercado" to 400_000L))
        assertTrue(propuestasDelInicio(enElLimite).none { it.tipo == TipoDePropuesta.PRESUPUESTO })
        assertTrue(propuestasDelInicio(datos.copy(budgets = null)).none { it.tipo == TipoDePropuesta.PRESUPUESTO })
    }

    @Test
    fun `con varios presupuestos pasados la tarjeta es del que mas se paso y cuenta los demas`() {
        val datos = DashboardData(
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            budgets = listOf(Budget("Restaurantes", 600_000), Budget("Ocio", 100_000)),
            spentByCategory = mapOf("Restaurantes" to 1_002_000L, "Ocio" to 150_000L),
        )
        val propuesta = propuestasDelInicio(datos).single { it.tipo == TipoDePropuesta.PRESUPUESTO }
        assertTrue(propuesta.titulo.startsWith("Restaurantes"))
        assertEquals(2, propuesta.cuantas)
        assertEquals("de más · y 1 presupuesto más", propuesta.cuerpo)
    }

    @Test
    fun `el porcentaje de la propuesta es el de Presupuestos, redondeado hacia arriba al pasarse`() {
        assertEquals(167, porcentajeDePresupuesto(1_001_000, 600_000), "166,8 % pasado el límite sube a 167")
        assertEquals(100, porcentajeDePresupuesto(600_000, 600_000))
        assertEquals(79, porcentajeDePresupuesto(479_999, 600_000), "hasta el límite se trunca")
        assertEquals(0, porcentajeDePresupuesto(10, 0))
        val datos = DashboardData(
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            budgets = listOf(Budget("Restaurantes", 600_000)),
            spentByCategory = mapOf("Restaurantes" to 1_001_000L),
        )
        assertEquals("Restaurantes va al 167\u00A0% de su presupuesto", propuestasDelInicio(datos).single().titulo)
    }

    private fun porEncima(categoria: String) = Anomalia(
        huella = "categoria:$categoria:2026-10", tipo = TipoDeAnomalia.CATEGORIA_POR_ENCIMA,
        titulo = "$categoria va muy por encima de lo normal", detalle = "Llevas \$1.000.000.", evidencia = emptyList(),
    )

    @Test
    fun `una categoria que ya sale por presupuesto no sale tambien como por encima de lo normal`() {
        val datos = DashboardData(
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            budgets = listOf(Budget("Restaurantes", 600_000)),
            spentByCategory = mapOf("Restaurantes" to 1_002_000L),
            anomalias = listOf(porEncima("Restaurantes")),
        )
        val tipos = propuestasDelInicio(datos).map { it.tipo }
        assertEquals(listOf(TipoDePropuesta.PRESUPUESTO), tipos, "una sola tarjeta para la misma plata")
        // Otra categoría por encima, sin presupuesto pasado: sigue saliendo.
        val conOtra = datos.copy(anomalias = listOf(porEncima("Restaurantes"), porEncima("Salud")))
        val propuesta = propuestasDelInicio(conOtra).single { it.tipo == TipoDePropuesta.ANOMALIA }
        assertTrue(propuesta.titulo.startsWith("Salud"))
        assertEquals(1, propuesta.cuantas)
    }

    // ── Buscar: la cuenta al final ───────────────────────────────────────────

    @Test
    fun `en Buscar la cuenta va al final y su cola queda protegida`() {
        val e = ev("m", "Uber", 8_000, ts = 15_000)
        val linea = com.jvillada.movi.ui.buscar.subtituloDeUnMovimientoEnBuscar(e, "Bancolombia ·4057", "27 oct")
        assertEquals("Transporte · 27 oct · Bancolombia ·4057", linea)
        assertEquals("·4057", partirLaColaDelNombreCorto(linea)?.cola)
        assertEquals("Transporte · 27 oct", com.jvillada.movi.ui.buscar.subtituloDeUnMovimientoEnBuscar(e, null, "27 oct"))
    }
}
