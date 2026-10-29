package com.jvillada.movi.ui.primeruso

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CapturaDelPrimerUso
import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.LoQueYaHay
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PrimerPaso
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.primerosPasos
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuiaDePrimerUsoTest {

    @Test
    fun `los pasos de la guia son los de la lista unica, con la bienvenida y el final`() {
        val pasos = pasosDeLaGuia(CapturaDelPrimerUso.SUBIR_UN_EXTRACTO)
        assertEquals(6, pasos.size)
        assertEquals(PasoDePrimerUso.entries.toList(), pasos)
        assertNull(pasoAnterior(PasoDePrimerUso.BIENVENIDA, pasos))
        assertNull(pasoSiguiente(PasoDePrimerUso.LISTO, pasos))
        assertEquals(PasoDePrimerUso.CUENTAS, pasoSiguiente(PasoDePrimerUso.PERIODO, pasos))
        assertEquals(1f, progresoDelPaso(PasoDePrimerUso.LISTO, pasos))
        assertTrue(progresoDelPaso(PasoDePrimerUso.BIENVENIDA, pasos) > 0f)
        // Los del medio, en el orden de la lista única.
        val delMedio = primerosPasos(LoQueYaHay(false, 0, false, 0), CapturaDelPrimerUso.SUBIR_UN_EXTRACTO).map { it.paso }
        assertEquals(delMedio, pasos.mapNotNull { it.primerPaso })
    }

    @Test
    fun `sin captura posible la guia se salta ese paso`() {
        val pasos = pasosDeLaGuia(CapturaDelPrimerUso.NINGUNA)
        assertEquals(5, pasos.size)
        assertTrue(PasoDePrimerUso.CAPTURA !in pasos)
        assertEquals(PasoDePrimerUso.RECURRENTES, pasoSiguiente(PasoDePrimerUso.CUENTAS, pasos))
    }

    @Test
    fun `todo paso de en medio se salta con el boton, tambien las cuentas`() {
        assertEquals(
            listOf(PasoDePrimerUso.PERIODO, PasoDePrimerUso.CUENTAS, PasoDePrimerUso.CAPTURA, PasoDePrimerUso.RECURRENTES),
            PasoDePrimerUso.entries.filter { it.seSaltaConUnBoton },
        )
    }

    @Test
    fun `Android pide los permisos del telefono y la web e iOS suben un extracto`() {
        assertEquals(ModoDeCaptura.PERMISOS_DEL_TELEFONO, modoDeCapturaDe(esAndroid = true))
        assertEquals(ModoDeCaptura.SUBIR_UN_EXTRACTO, modoDeCapturaDe(esAndroid = false))
        assertEquals("Sube un extracto", PasoDePrimerUso.CAPTURA.titulo(ModoDeCaptura.SUBIR_UN_EXTRACTO))
        assertEquals("Que Movi se entere solo", PasoDePrimerUso.CAPTURA.titulo(ModoDeCaptura.PERMISOS_DEL_TELEFONO))
    }

    @Test
    fun `los bancos de la guia existen en MarcasConocidas`() {
        val conocidos = com.jvillada.movi.shared.model.BANCOS_CONOCIDOS.map { it.id }
        com.jvillada.movi.ui.accounts.BANCOS_DE_UN_TOQUE.forEach { assertTrue(it in conocidos, it) }
    }

    @Test
    fun `el resumen dice lo que quedo y lo que falta, con los estados de la lista unica`() {
        val modo = CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO
        val listaLlena = primerosPasos(LoQueYaHay(true, 2, true, 3), modo)
        val todo = lineasDelResumen(
            listaLlena,
            ResumenDelPrimerUso(
                periodo = "Cada día 25 · o el día hábil anterior",
                cuentas = 2, tarjetas = 1, recurrentes = 3,
                captura = EstadoDeLaCaptura.ACTIVA,
                modo = modo,
            ),
        )
        assertEquals(listaLlena.map { it.hecho }, todo.map { it.hecho }, "lo hecho sale de la lista, no del resumen")
        assertTrue(todo.all { it.hecho })
        assertEquals("2 cuentas y 1 tarjeta", todo[1].detalle)
        assertEquals("3 pagos", todo[3].detalle)

        val listaVacia = primerosPasos(LoQueYaHay(false, 0, false, 0), modo)
        val nada = lineasDelResumen(listaVacia, ResumenDelPrimerUso(null, 0, 0, 0, EstadoDeLaCaptura.PENDIENTE, modo))
        assertTrue(nada.none { it.hecho })
        assertEquals("Sin elegir: mes de calendario", nada[0].detalle)
        assertEquals("Sin activar todavía", nada[2].detalle)
    }

    @Test
    fun `en la web el resumen habla del extracto y nunca de algo que ahi no se puede hacer`() {
        val modo = CapturaDelPrimerUso.SUBIR_UN_EXTRACTO
        val lista = primerosPasos(LoQueYaHay(false, 1, false, 0), modo)
        val lineas = lineasDelResumen(lista, ResumenDelPrimerUso(null, 1, 0, 0, EstadoDeLaCaptura.DIRECCION_COPIADA, modo))
        assertEquals("Sube un extracto", lineas[2].titulo)
        assertEquals(false, lineas[2].hecho, "copiar la dirección no prueba que el reenvío ande")
        assertTrue(lineas.none { "teléfono" in it.detalle })

        val sinCaptura = primerosPasos(LoQueYaHay(false, 1, false, 0), CapturaDelPrimerUso.NINGUNA)
        val tres = lineasDelResumen(sinCaptura, ResumenDelPrimerUso(null, 1, 0, 0, EstadoDeLaCaptura.PENDIENTE, CapturaDelPrimerUso.NINGUNA))
        assertEquals(3, tres.size, "sin captura posible no hay renglón «Pendiente» de captura")
    }

    // ── La lista con lo que el Inicio leyó ─────────────────────────────────────────────────────

    private val octubre = PeriodoFinanciero(2026, 10)

    private fun regla(id: String) = UpcomingPayment(
        rule = RecurringRule(id = id, name = "Arriendo", category = "Vivienda", amount = 1, dayOfMonth = 5, type = TransactionType.EXPENSE),
        dueDate = "2026-10-05",
        daysUntil = 3,
        status = PaymentStatus.UPCOMING,
    )

    private fun leido(
        cuentas: List<Account> = emptyList(),
        eventos: Int = 0,
        upcoming: List<UpcomingPayment> = emptyList(),
        ajustes: PeriodSettings = PeriodSettings(),
        periodo: PeriodoFinanciero? = octubre,
        captura: CapturaDeSms? = null,
    ) = DashboardData(
        accounts = cuentas,
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0, eventCount = eventos),
        upcoming = upcoming,
        credits = emptyList(),
        cards = emptyList(),
        ajustesDePeriodo = ajustes,
        periodoActual = periodo,
        captura = captura,
    )

    @Test
    fun `sin poder afirmar no hay lista`() {
        assertNull(primerosPasosDe(DashboardData(), CapturaDelPrimerUso.SUBIR_UN_EXTRACTO, false))
        assertNull(primerosPasosDe(leido(periodo = null), CapturaDelPrimerUso.SUBIR_UN_EXTRACTO, false), "sin perfil no se sabe el período")
    }

    @Test
    fun `la lista del Inicio se tilda con los datos de siempre`() {
        val cuenta = Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 1)
        val llena = leido(
            cuentas = listOf(cuenta),
            eventos = 3,
            upcoming = listOf(regla("r1")),
            ajustes = PeriodSettings(cutoffDay = 25),
        )
        assertTrue(primerosPasosDe(llena, CapturaDelPrimerUso.SUBIR_UN_EXTRACTO, false)!!.all { it.hecho })

        // Una tarjeta sola no es cuenta de dinero; una cuota de crédito no es un pago de cada mes.
        val soloDeuda = leido(
            cuentas = listOf(Account(id = "t", name = "Visa", type = AccountType.CREDIT_CARD, balance = 0)),
            upcoming = listOf(regla("credit_x")),
        )
        val lista = primerosPasosDe(soloDeuda, CapturaDelPrimerUso.SUBIR_UN_EXTRACTO, false)!!
        assertEquals(listOf(false, false, false, false), lista.map { it.hecho })

        // En Android: el permiso o un aviso que ya llegó.
        val captura = { d: DashboardData, permiso: Boolean ->
            primerosPasosDe(d, CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO, permiso)!!.first { it.paso == PrimerPaso.CAPTURA }.hecho
        }
        assertEquals(false, captura(leido(eventos = 5), false), "en Android un movimiento a mano no activa el teléfono")
        assertEquals(true, captura(leido(), true))
        assertEquals(true, captura(leido(captura = CapturaDeSms(total = 2)), false))
    }
}
