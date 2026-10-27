package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El Inicio del rediseño (ola R1)**, sin pantalla: qué propone «Movi propone hoy» y en qué orden, la
 * cuadrícula de acciones y a dónde lleva cada una, el carrusel de cuentas y cómo se agrupan los widgets.
 */
class InicioDelRedisenoTest {

    // ── Movi propone hoy ─────────────────────────────────────────────────────

    private val septiembre = PeriodoFinanciero(2026, 9)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = 10, type = TransactionType.EXPENSE),
        dueDate = "2026-09-10",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private val debito = DebitoAutomaticoPorConfirmar(
        ruleId = "cr_1", periodo = "2026-09", origen = OrigenDelDebito.CUOTA_DE_CREDITO, nombre = "Libre inversión 9695",
        monto = 1_204_064, vence = "2026-09-05", cuentaId = "a1", cuentaNombre = "Bancolombia Ahorros",
        categoria = "Cuota de crédito", pataDelDineroId = "p1",
    )

    private val cuadre = PropuestaDeCuadrePorAviso(
        avisoId = "s1", cuentaId = "a1", cuentaNombre = "Bancolombia", origen = "85540",
        saldoDelBanco = 1_000_000, saldoEnMovi = 960_000, momento = 0,
    )

    private val anomalia = Anomalia(
        huella = "dup:a,b", tipo = TipoDeAnomalia.COBRO_DUPLICADO, titulo = "¿Cobro duplicado en RAPPI?",
        detalle = "2 cobros de \$45.900", evidencia = listOf(EvidenciaDeAnomalia("a", "2026-09-28", "RAPPI", 45_900)),
    )

    private val cierre = CierreDelPeriodo(periodo = "2026-08", nombre = "Agosto 2026", entro = 10, salio = 5, diaDelPeriodoNuevo = 1)

    /** Una cosa de cada tipo, todo a la vez. */
    private val todoPendiente = DashboardData(
        upcoming = listOf(pago("r_arr", "Arriendo", 1_850_000, 0), pago("r_cel", "Celular", 53_000, 1)),
        ocurrencias = emptyList(),
        periodoActual = septiembre,
        ajustesDePeriodo = PeriodSettings(),
        debitosPorConfirmar = listOf(debito),
        pendingSms = 3,
        cuadresPorAviso = listOf(cuadre),
        anomalias = listOf(anomalia),
        cierre = cierre,
    )

    @Test
    fun `una propuesta por tipo, de lo mas urgente a lo menos`() {
        val propuestas = propuestasDelInicio(todoPendiente)
        assertEquals(
            listOf(
                TipoDePropuesta.PAGO, TipoDePropuesta.DEBITO, TipoDePropuesta.AVISOS,
                TipoDePropuesta.CUADRE, TipoDePropuesta.ANOMALIA, TipoDePropuesta.CIERRE,
            ),
            propuestas.map { it.tipo },
        )
        // Los dos pagos por vencer van en una sola tarjeta, con el más urgente adelante.
        val pago = propuestas.first()
        assertEquals("Arriendo vence hoy", pago.titulo)
        assertEquals(1_850_000L, pago.cifra)
        assertEquals("y 1 pago más por vencer", pago.cuerpo)
        assertTrue(pago.urgente)
        assertEquals("Llegaron 3 avisos del banco", propuestas[2].titulo)
    }

    @Test
    fun `el tope son cuatro, y el resto queda para Ver todo`() {
        val todas = propuestasDelInicio(todoPendiente)
        assertEquals(6, todas.size)
        assertEquals(TOPE_DE_PROPUESTAS, propuestasALaVista(todas).size)
        assertEquals(todas.take(4), propuestasALaVista(todas))
    }

    @Test
    fun `sin nada pendiente no hay propuestas`() {
        assertEquals(emptyList(), propuestasDelInicio(DashboardData()))
        assertEquals("Nada por decidir hoy", NADA_POR_DECIDIR)
    }

    @Test
    fun `cada propuesta tiene su accion real`() {
        val porTipo = propuestasDelInicio(todoPendiente).associateBy { it.tipo }
        // Anotar el pago abre «Agregar» con lo que el recurrente sabe.
        val anotar = (porTipo.getValue(TipoDePropuesta.PAGO).alTocar as AccionDeLaPropuesta.Ir).pantalla
        assertTrue(anotar is Screen.QuickAdd && anotar.presetNota == "Arriendo" && anotar.presetMonto == 1_850_000L)
        assertEquals(AccionDeLaPropuesta.Ir(Screen.PorRevisar), porTipo.getValue(TipoDePropuesta.DEBITO).alTocar)
        assertEquals(AccionDeLaPropuesta.Ir(Screen.PorRevisar), porTipo.getValue(TipoDePropuesta.AVISOS).alTocar)
        assertEquals(AccionDeLaPropuesta.Ir(Screen.CuadreDeSaldos), porTipo.getValue(TipoDePropuesta.CUADRE).alTocar)
        assertEquals(40_000L, porTipo.getValue(TipoDePropuesta.CUADRE).cifra, "la diferencia que ya trae el cuadre")
        assertEquals(AccionDeLaPropuesta.Ir(Screen.Transactions(consultaInicial = "RAPPI")), porTipo.getValue(TipoDePropuesta.ANOMALIA).alTocar)
        assertEquals(AccionDeLaPropuesta.DescartarAnomalia("dup:a,b"), porTipo.getValue(TipoDePropuesta.ANOMALIA).alTocarSecundaria)
        assertEquals(AccionDeLaPropuesta.Ir(Screen.DetalleDePeriodo("2026-08")), porTipo.getValue(TipoDePropuesta.CIERRE).alTocar)
        assertEquals(AccionDeLaPropuesta.DescartarCierre("2026-08"), porTipo.getValue(TipoDePropuesta.CIERRE).alTocarSecundaria)
    }

    @Test
    fun `un pago que vence en dos dias todavia no es una propuesta`() {
        val data = todoPendiente.copy(upcoming = listOf(pago("r_cel", "Celular", 53_000, 2)))
        assertTrue(propuestasDelInicio(data).none { it.tipo == TipoDePropuesta.PAGO })
    }

    @Test
    fun `un pago que vence manana va despues de los avisos`() {
        val data = todoPendiente.copy(upcoming = listOf(pago("r_cel", "Celular", 53_000, 1)))
        val tipos = propuestasDelInicio(data).map { it.tipo }
        assertTrue(tipos.indexOf(TipoDePropuesta.AVISOS) < tipos.indexOf(TipoDePropuesta.PAGO))
        assertEquals("Celular vence mañana", propuestasDelInicio(data).first { it.tipo == TipoDePropuesta.PAGO }.titulo)
    }

    @Test
    fun `una anomalia descartada en la sesion no se propone`() {
        AnomaliasDescartadasEnLaSesion.descartar("dup:a,b")
        try {
            assertTrue(propuestasDelInicio(todoPendiente).none { it.tipo == TipoDePropuesta.ANOMALIA })
        } finally {
            AnomaliasDescartadasEnLaSesion.olvidar()
        }
    }

    // ── La cuadrícula ────────────────────────────────────────────────────────

    @Test
    fun `con la ola de Espacios Repartir se muestra en su lugar, y apagado se salta`() {
        assertEquals(true, REPARTIR_ESTA_LISTO)
        assertEquals(
            listOf("Gasto", "Ingreso", "Traspaso", "Pagar tarjeta", "Repartir", "Comprobante", "Avisos", "Preguntar"),
            accionesDelInicio().map { it.rotulo },
        )
        // Vuelta de diseño 2: el rótulo es corto; la frase entera es la del lector de pantalla.
        assertEquals(
            listOf(
                "Anotar un gasto", "Anotar un ingreso", "Anotar un traspaso", "Pagar una tarjeta",
                "Repartir un gasto", "Subir un comprobante", "Revisar los avisos", "Preguntarle a Movi",
            ),
            accionesDelInicio().map { it.descripcion },
        )
        assertEquals(7, accionesDelInicio(repartirListo = false).size)
        assertTrue(accionesDelInicio(repartirListo = false).none { it == AccionDelInicio.REPARTIR })
    }

    @Test
    fun `cada accion abre lo que ya existe`() {
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO), destinoDeLaAccion(AccionDelInicio.ANOTAR_GASTO))
        assertEquals(
            Screen.QuickAdd(presetEsIngreso = true, presetPestana = PestanaDeAgregar.INGRESO),
            destinoDeLaAccion(AccionDelInicio.ANOTAR_INGRESO),
        )
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.TRASPASO), destinoDeLaAccion(AccionDelInicio.TRASPASAR))
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.PAGO_DE_TARJETA), destinoDeLaAccion(AccionDelInicio.PAGAR_TARJETA))
        assertNull(destinoDeLaAccion(AccionDelInicio.SUBIR_COMPROBANTE), "elegir un archivo no es una pantalla")
        assertEquals(Screen.PorRevisar, destinoDeLaAccion(AccionDelInicio.REVISAR_AVISOS))
        assertEquals(Screen.AIChat(), destinoDeLaAccion(AccionDelInicio.PREGUNTAR_A_MOVI))
        assertEquals(Screen.Espacios, destinoDeLaAccion(AccionDelInicio.REPARTIR))
    }

    @Test
    fun `la insignia de Revisar avisos cuenta lo que espera en Por revisar`() {
        assertEquals(4, pendientesPorRevisar(todoPendiente))
        assertEquals(0, pendientesPorRevisar(DashboardData()))
    }

    // ── El encabezado ────────────────────────────────────────────────────────

    @Test
    fun `el saludo usa el primer nombre`() {
        assertEquals("Hola, Juan", saludoDelInicio("Juan Villada"))
        assertEquals("Hola", saludoDelInicio(null))
        assertEquals("Hola", saludoDelInicio("  "))
    }

    @Test
    fun `el periodo del encabezado`() {
        assertNull(periodoDelEncabezado(DashboardData()))
        assertEquals("Septiembre 2026", periodoDelEncabezado(DashboardData(periodoActual = septiembre)))
        assertEquals(
            "Del 25 de agosto al 24 de septiembre",
            periodoDelEncabezado(DashboardData(periodoActual = septiembre, ajustesDePeriodo = PeriodSettings(cutoffDay = 25))),
        )
    }

    // ── Tus cuentas ──────────────────────────────────────────────────────────

    @Test
    fun `el carrusel va dinero, inversion y tarjetas, sin prestamos ni bienes`() {
        val cuentas = listOf(
            Account("t1", "Master Black", AccountType.CREDIT_CARD, -27_350_341),
            Account("a1", "Nu", AccountType.SAVINGS, 2_890_000),
            Account("l1", "Hipoteca", AccountType.LOAN, -200_000_000),
            Account("i1", "Skandia", AccountType.INVESTMENT, 5_000_000),
            Account("a2", "Bancolombia", AccountType.SAVINGS, 13_845_800),
            Account("b1", "La casa", AccountType.INVESTMENT, 800_000_000, bien = Bien(valor = 800_000_000)),
        )
        val carrusel = cuentasDelCarrusel(cuentas)!!
        assertEquals(listOf("Bancolombia", "Nu", "Skandia", "Master Black"), carrusel.map { it.cuenta.name })
        val tarjeta = carrusel.last()
        assertEquals("Debes", tarjeta.rotuloDelSaldo)
        assertEquals(27_350_341L, tarjeta.saldo)
        assertEquals("Tarjeta", tarjeta.tipo)
        assertEquals(Screen.AccountDetail("t1", AccountGroup.DEUDA), tarjeta.destino)
        assertEquals("Saldo", carrusel.first().rotuloDelSaldo)
        assertNull(cuentasDelCarrusel(null), "sin cuentas leídas no se afirma un carrusel vacío")
    }

    // ── Los bloques ──────────────────────────────────────────────────────────

    @Test
    fun `los dos widgets seguidos van juntos en un bloque`() {
        val bloques = bloquesDelInicio(defaultDashboardDefinition().sections)
        assertEquals(
            listOf(
                listOf("HERO_BALANCE"), listOf("ACCIONES"), listOf("PROPUESTAS"), listOf("CUENTAS"),
                listOf("CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA"), listOf("ALERTS"), listOf("BANNER"),
            ),
            bloques.map { b -> b.map { it.type } },
        )
        // Separados por otra sección, cada uno va solo.
        val separados = listOf(ScreenSection("CHECKLIST_DEL_PERIODO"), ScreenSection("CUENTAS"), ScreenSection("GASTO_POR_CATEGORIA"))
        assertEquals(3, bloquesDelInicio(separados).size)
    }

    @Test
    fun `un pago sin candidatos se ofrece anotar, y Ver pagos lleva a Plan`() {
        val soloUno = todoPendiente.copy(upcoming = listOf(pago("r_arr", "Arriendo", 1_850_000, -3)))
        val pago = propuestasDelInicio(soloUno).first { it.tipo == TipoDePropuesta.PAGO }
        assertEquals("Arriendo venció hace 3 días", pago.titulo)
        assertEquals("Anotar pago", pago.accion)
        assertNull(pago.cuerpo)
        assertEquals(AccionDeLaPropuesta.Ir(Screen.Plan(SEGMENTO_PAGOS)), pago.alTocarSecundaria)
    }
}
