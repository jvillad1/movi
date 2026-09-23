package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.ui.Screen
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Las reglas del Inicio de un vistazo (entrega B), sin pantalla: el veredicto del hero, su
 * coherencia con la tarjeta del disponible, el patrimonio en tramos y la disposición por ancho.
 */
class InicioDeUnVistazoTest {

    // ── El veredicto ─────────────────────────────────────────────────────────

    /**
     * **Con los números reales del dueño al 23-sep**: entró $22,2M, salió $33,9M, y $12,9M de lo que
     * salió fueron cuotas de crédito. El texto exacto es el que se ve debajo de «Tu plata».
     */
    @Test
    fun `el veredicto con los numeros reales del dueno`() {
        val v = veredictoDelPeriodo(
            ingresos = 22_152_488,
            egresos = 33_882_000,
            cuotasDeCredito = 12_915_000,
            // Aunque otra categoría pese más, se nombran las cuotas: son deuda que baja, no consumo.
            mayorGasto = "Vivienda" to 14_000_000,
        )
        assertEquals(
            Veredicto(
                "Este período salieron \$11,7M más de los que entraron — las cuotas de crédito fueron \$12,9M",
                TonoDelVeredicto.EN_CONTRA,
            ),
            v,
        )
    }

    @Test
    fun `el veredicto sale de lo que ya cargo el Inicio`() {
        val data = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 22_152_488, egresos = 33_882_000),
            spentByCategory = mapOf(CUOTA_CATEGORY to 12_915_000L, "Vivienda" to 14_000_000L, "Comida" to 2_000_000L),
        )
        assertEquals(
            "Este período salieron \$11,7M más de los que entraron — las cuotas de crédito fueron \$12,9M",
            veredictoDelInicio(data, hoy = LocalDate(2026, 9, 21))?.frase,
        )
        // Sin resumen no hay veredicto: sería opinar sobre cifras que no llegaron.
        assertNull(veredictoDelInicio(DashboardData(), hoy = LocalDate(2026, 9, 21)))
    }

    @Test
    fun `sin cuotas nombra la categoria que mas peso, sin contar Otros`() {
        val v = veredictoDelPeriodo(
            ingresos = 5_000_000,
            egresos = 6_500_000,
            mayorGasto = mayorGastoDelPeriodo(mapOf("Otros" to 3_000_000L, "Mercado" to 2_000_000L, "Salud" to 1_500_000L)),
        )
        assertEquals(
            "Este período salieron \$1,5M más de los que entraron — lo que más pesó fue Mercado: \$2M",
            v?.frase,
        )
    }

    @Test
    fun `a favor, parejo y sin nada que medir`() {
        assertEquals(
            Veredicto("Este período entró \$3M más de lo que salió", TonoDelVeredicto.A_FAVOR),
            veredictoDelPeriodo(ingresos = 10_000_000, egresos = 7_000_000),
        )
        assertEquals(
            Veredicto("Este período entró lo mismo que salió", TonoDelVeredicto.PAREJO),
            veredictoDelPeriodo(ingresos = 2_000_000, egresos = 2_000_000),
        )
        assertNull(veredictoDelPeriodo(ingresos = 0, egresos = 0), "un período vacío no tiene veredicto")
    }

    @Test
    fun `a favor pero pasado del disponible lo dice en la misma frase`() {
        assertEquals(
            Veredicto(
                "Este período entró \$3M más de lo que salió, pero te pasaste del disponible por \$563.456",
                TonoDelVeredicto.EN_CONTRA,
            ),
            veredictoDelPeriodo(ingresos = 10_000_000, egresos = 7_000_000, excesoDelDisponible = 563_456),
        )
    }

    @Test
    fun `la barra de lo que entro contra lo que salio`() {
        assertEquals(0.4f, fraccionQueEntro(20_000_000, 30_000_000)!!, 0.0001f)
        assertEquals(1f, fraccionQueEntro(1_000, 0)!!, 0.0001f)
        assertNull(fraccionQueEntro(0, 0), "sin nada que dibujar, no hay barra")
    }

    // ── Coherencia entre el hero y la tarjeta del disponible ─────────────────

    private val hoy = LocalDate(2026, 9, 21)

    /** El Inicio del 21-sep con el período 25-ago/24-sep, un arriendo ya pagado y lo que se pida. */
    private fun inicio(
        ingresos: Long,
        egresos: Long,
        gastoVariable: Map<String, Long>,
        plata: PlataDelDisponible? = null,
        arriendo: Long = 3_100_000,
    ) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = ingresos, egresos = egresos),
        spentByCategory = mapOf("Vivienda" to arriendo, CUOTA_CATEGORY to (egresos - arriendo).coerceAtLeast(0)),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = arriendo,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(
            OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true),
        ),
        gastoVariablePorDia = gastoVariable,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
        plataDelDisponible = plata,
    )

    /**
     * La escena que motivó la entrega: el disponible del período pasado por $563.456 y, en la tarjeta
     * vieja, «Vas bien» para la semana un renglón abajo. Ahora hay UNA frase en la tarjeta, que dice
     * que se pasó, y el hero —con el flujo en contra— tampoco dice nada a favor.
     */
    @Test
    fun `con el disponible pasado ni el hero ni la tarjeta dicen que va bien`() {
        val data = inicio(
            ingresos = 22_152_488,
            egresos = 33_882_000,
            // 13,8M de disponible (tenías + entró − guardado − arriendo) y 14,4M ya gastados.
            gastoVariable = mapOf("2026-09-01" to 14_363_456L, "2026-09-21" to 0L),
            plata = PlataDelDisponible(saldoAlInicio = 1_000_000, entradas = 15_900_000, guardado = 0),
        )
        val d = assertNotNull(disponibleDelInicio(data, hoy))
        assertEquals(13_800_000, d.disponible)
        assertEquals(563_456, excesoDelDisponible(d))

        val frase = fraseDelDisponible(d)
        assertEquals(
            FraseDelDisponible("Te pasaste del disponible del período por \$563.456", NivelDelGasto.PASADO),
            frase,
        )
        val veredicto = assertNotNull(veredictoDelInicio(data, hoy))
        assertEquals(TonoDelVeredicto.EN_CONTRA, veredicto.tono)
        assertFalse("bien" in veredicto.frase.lowercase())
        assertFalse("bien" in frase.texto.lowercase())
    }

    /**
     * La regla de coherencia, en una grilla: para cualquier combinación de flujo y gasto,
     * - la tarjeta nunca dice «bien» (ni «vas bien» ni nada de ánimo);
     * - si la tarjeta dice que el período se pasó, el hero no puede estar a favor;
     * - y si el hero está a favor, el período no se pasó.
     */
    @Test
    fun `el veredicto y el disponible nunca se contradicen`() {
        val flujos = listOf(10_000_000L to 7_000_000L, 7_000_000L to 10_000_000L, 5_000_000L to 5_000_000L)
        val gastos = listOf(0L, 1_000_000L, 3_000_000L, 6_000_000L, 12_000_000L)
        val hoyes = listOf(0L, 50_000L, 400_000L, 2_000_000L)
        var casos = 0
        for ((ingresos, egresos) in flujos) for (antes in gastos) for (deHoy in hoyes) {
            val data = inicio(
                ingresos = ingresos, egresos = egresos,
                gastoVariable = mapOf("2026-09-01" to antes, "2026-09-21" to deHoy),
            )
            val d = disponibleDelInicio(data, hoy) ?: continue
            val frase = fraseDelDisponible(d)
            val veredicto = assertNotNull(veredictoDelInicio(data, hoy))
            val contexto = "ingresos=$ingresos egresos=$egresos antes=$antes hoy=$deHoy"
            assertFalse("bien" in frase.texto.lowercase(), "la tarjeta opinó: ${frase.texto} ($contexto)")
            if (excesoDelDisponible(d) > 0L) {
                assertEquals(TonoDelVeredicto.EN_CONTRA, veredicto.tono, "período pasado con hero a favor ($contexto)")
            }
            if (veredicto.tono == TonoDelVeredicto.A_FAVOR) {
                assertEquals(0L, excesoDelDisponible(d), "hero a favor con el período pasado ($contexto)")
                assertTrue(d.periodo.nivel != NivelDelGasto.PASADO, contexto)
            }
            casos++
        }
        assertTrue(casos >= 40, "la grilla tiene que haber medido algo: $casos casos")
    }

    /**
     * «Para revisar» decía «Vas gastando más de lo que entró este período» debajo de un hero que ya
     * lo dice con el número. Se vio en la web, en escritorio, con los dos a la vista: el Inicio no
     * repite una noticia.
     */
    @Test
    fun `para revisar no repite el veredicto del hero`() {
        val data = inicio(ingresos = 22_152_488, egresos = 33_882_000, gastoVariable = emptyMap())
        assertNotNull(veredictoDelInicio(data, hoy))
        assertTrue(cosasParaRevisarDe(data).none { "más de lo que entró" in it.texto })
    }

    // ── Tu patrimonio ────────────────────────────────────────────────────────

    /** Los números del dueño con la casa cargada: tienes $1.528,7M, debes $2.191M, neto −$662,3M. */
    @Test
    fun `el patrimonio del dueno en tramos`() {
        val p = assertNotNull(
            patrimonioDelInicio(
                Patrimonio(
                    tuPlata = 558_350,
                    condicionado = 116_200_000,
                    condicionadoA = "Vivienda",
                    bienes = 1_411_903_920,
                    deudas = 2_191_000_000,
                ),
            ),
        )
        assertEquals(1_528_662_270, p.tienes)
        assertEquals(2_191_000_000, p.debes)
        assertEquals(-662_337_730, p.neto)
        assertEquals("\$1.528,7M", com.jvillada.movi.ui.components.formatMoneyCompact(p.tienes))
        assertEquals("−\$662,3M", com.jvillada.movi.ui.components.formatMoneyCompact(p.neto))
        assertEquals(
            listOf(
                TramoDelPatrimonio("Tu plata", 558_350, esDeuda = false),
                TramoDelPatrimonio("Uso condicionado · Vivienda", 116_200_000, esDeuda = false),
                TramoDelPatrimonio("Bienes", 1_411_903_920, esDeuda = false),
                TramoDelPatrimonio("Deudas", 2_191_000_000, esDeuda = true),
            ),
            p.tramos,
        )
        assertEquals(0.411f, p.fraccionQueTienes, 0.001f)
        assertEquals(Screen.Credits, destinoDelTramo(p.tramos.last()))
        assertEquals(Screen.Accounts, destinoDelTramo(p.tramos.first()))
    }

    @Test
    fun `una tarjeta sobrepagada va del lado de lo que tienes`() {
        val p = assertNotNull(patrimonioDelInicio(Patrimonio(tuPlata = 1_000_000, deudas = -500_000)))
        assertEquals(1_500_000, p.tienes)
        assertEquals(0, p.debes)
        assertEquals(1_500_000, p.neto)
        assertEquals(TramoDelPatrimonio("A favor en créditos", 500_000, esDeuda = false), p.tramos.last())
    }

    @Test
    fun `sin nada que tener ni deber no hay tarjeta`() {
        assertNull(patrimonioDelInicio(Patrimonio()))
        assertNull(patrimonioDelInicio(DashboardData()), "sin cuentas ni resumen no se afirma un patrimonio")
    }

    /** Con cuentas y resumen, manda la lista de cuentas: la misma que suma «Tu plata» arriba. */
    @Test
    fun `el patrimonio sale de las mismas cuentas que el hero`() {
        val cuenta = Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 700_000)
        val data = DashboardData(accounts = listOf(cuenta), patrimonio = Patrimonio(tuPlata = 1))
        assertEquals(700_000, patrimonioDelInicio(data)?.tienes)
        // Sin cuentas todavía, el del resumen.
        assertEquals(1, patrimonioDelInicio(DashboardData(patrimonio = Patrimonio(tuPlata = 1)))?.tienes)
    }

    // ── Qué se pinta ─────────────────────────────────────────────────────────

    @Test
    fun `el banner de Movi AI se esconde solo cuando esta Preguntale a Movi`() {
        val nueva = visibleSections(defaultDashboardDefinition(), DashboardData()).map { it.type }
        assertTrue("PREGUNTALE_A_MOVI" in nueva)
        assertTrue("BANNER" !in nueva, "dos puertas al mismo chat")

        // Una fila guardada de la generación 7 (el server todavía sin desplegar): el cliente nuevo
        // pinta el banner, que es la única puerta a Movi AI que trae esa definición.
        val generacion7 = ScreenDefinition(
            "dashboard", 7,
            defaultDashboardDefinition().sections.filter { it.type != "PREGUNTALE_A_MOVI" && it.type != "PATRIMONIO" },
        )
        assertTrue("BANNER" in visibleSections(generacion7, DashboardData()).map { it.type })

        // Un aviso del Editor que lleva a otro lado se sigue pintando: lo que se esconde es la puerta
        // repetida, no cualquier aviso.
        val conOtroAviso = defaultDashboardDefinition().let { d ->
            d.copy(sections = d.sections + ScreenSection(
                type = "BANNER", text = "Mira tus metas",
                cards = listOf(ScreenCard("", action = ScreenAction("NAVIGATE", "goals"))),
            ))
        }
        assertEquals(1, visibleSections(conOtroAviso, DashboardData()).count { it.type == "BANNER" })
    }

    @Test
    fun `la tarjeta del patrimonio aparece cuando hay cuentas`() {
        val sinCuentas = visibleSections(defaultDashboardDefinition(), DashboardData()).map { it.type }
        assertTrue("PATRIMONIO" !in sinCuentas)
        val conCuentas = DashboardData(
            accounts = listOf(Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 700_000)),
        )
        assertTrue("PATRIMONIO" in visibleSections(defaultDashboardDefinition(), conCuentas).map { it.type })
    }

    // ── Dos columnas en escritorio ───────────────────────────────────────────

    private val todas = defaultDashboardDefinition().sections.filter { it.type != "BANNER" }

    @Test
    fun `en el telefono una columna en el orden de la definicion`() {
        val c = columnasDelInicio(todas, 390.dp)
        assertFalse(c.sonDos)
        assertEquals(
            listOf(
                "HERO_BALANCE", "PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "DISPONIBLE_DEL_PERIODO",
                "GASTO_POR_CATEGORIA", "PATRIMONIO", "ALERTS",
            ),
            c.izquierda.map { it.type },
        )
        assertFalse(columnasDelInicio(todas, 899.dp).sonDos, "por debajo de 900 dp sigue siendo una columna")
    }

    @Test
    fun `en escritorio dos columnas, estado a la izquierda y lo que viene a la derecha`() {
        val c = columnasDelInicio(todas, 1_400.dp)
        assertTrue(c.sonDos)
        assertEquals(listOf("HERO_BALANCE", "GASTO_POR_CATEGORIA", "ALERTS"), c.izquierda.map { it.type })
        assertEquals(
            listOf("PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "DISPONIBLE_DEL_PERIODO", "PATRIMONIO"),
            c.derecha.map { it.type },
        )
        assertTrue(columnasDelInicio(todas, 900.dp).sonDos, "desde 900 dp, dos")
    }

    @Test
    fun `con una sola columna llena no se deja un hueco al lado`() {
        val soloElHero = todas.filter { it.type == "HERO_BALANCE" }
        assertFalse(columnasDelInicio(soloElHero, 1_400.dp).sonDos)
    }

    @Test
    fun `solo el Inicio se ensancha en escritorio`() {
        assertEquals(1_200.dp, anchoMaximoDeLaPantalla(Screen.Dashboard))
        assertEquals(600.dp, anchoMaximoDeLaPantalla(Screen.Transactions()))
        assertEquals(600.dp, anchoMaximoDeLaPantalla(Screen.Accounts))
    }

    // ── La cifra que cuenta ──────────────────────────────────────────────────

    @Test
    fun `la cifra que cuenta termina exacta`() {
        assertEquals(0, cifraContando(1_528_662_270, 0f))
        assertEquals(1_528_662_270, cifraContando(1_528_662_270, 1f))
        assertEquals(279_175, cifraContando(558_350, 0.5f))
        assertEquals(-279_175, cifraContando(-558_350, 0.5f))
    }
}
