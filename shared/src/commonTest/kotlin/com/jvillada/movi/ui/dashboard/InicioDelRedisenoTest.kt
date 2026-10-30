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
import com.jvillada.movi.ui.components.inicialesDelUsuario
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
                TipoDePropuesta.PAGO, TipoDePropuesta.DEBITO,
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
        // Vuelta de diseño 28: los 3 avisos por confirmar no son una tarjeta; los cuenta «Avisos».
        assertTrue(propuestas.none { "avisos" in it.titulo })
    }

    @Test
    fun `el tope son cuatro, y el resto queda para Ver todo`() {
        val todas = propuestasDelInicio(todoPendiente)
        assertEquals(5, todas.size)
        assertEquals(TOPE_DE_PROPUESTAS, propuestasALaVista(todas).size)
        assertEquals(todas.take(4), propuestasALaVista(todas))
    }

    @Test
    fun `sin nada pendiente no hay propuestas`() {
        assertEquals(emptyList(), propuestasDelInicio(DashboardData()))
    }

    @Test
    fun `cada propuesta tiene su accion real`() {
        val porTipo = propuestasDelInicio(todoPendiente).associateBy { it.tipo }
        // Anotar el pago abre «Agregar» con lo que el recurrente sabe.
        val anotar = (porTipo.getValue(TipoDePropuesta.PAGO).alTocar as AccionDeLaPropuesta.Ir).pantalla
        assertTrue(anotar is Screen.QuickAdd && anotar.presetNota == "Arriendo" && anotar.presetMonto == 1_850_000L)
        assertEquals(AccionDeLaPropuesta.Ir(Screen.PorRevisar), porTipo.getValue(TipoDePropuesta.DEBITO).alTocar)
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
    fun `un pago que vence manana va despues de lo que el banco cobro solo`() {
        val data = todoPendiente.copy(upcoming = listOf(pago("r_cel", "Celular", 53_000, 1)))
        val tipos = propuestasDelInicio(data).map { it.tipo }
        assertTrue(tipos.indexOf(TipoDePropuesta.DEBITO) < tipos.indexOf(TipoDePropuesta.PAGO))
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

    /**
     * Vuelta 39 (la opción 3, decisión del dueño: las 8): en dos filas de cuatro y en este orden. El
     * rótulo va en dos renglones; la frase entera es la del lector de pantalla.
     */
    @Test
    fun `Inicio tiene ocho acciones, en el orden de la maqueta`() {
        assertEquals(
            listOf(
                "Anotar gasto", "Anotar ingreso", "Traspasar", "Pagar tarjeta",
                "Repartir", "Comprobante", "Revisar avisos", "Preguntar",
            ),
            accionesDelInicio().map { it.rotulo },
        )
        assertEquals(8, accionesDelInicio().map { it.descripcion }.distinct().size, "cada una dice qué hace")
    }

    /** Cada acción abre lo mismo que su atajo del menú del «+» (o su pantalla de siempre). */
    @Test
    fun `cada accion abre lo que ya existe`() {
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO), destinoDeLaAccion(AccionDelInicio.ANOTAR_GASTO))
        assertEquals(
            Screen.QuickAdd(presetEsIngreso = true, presetPestana = PestanaDeAgregar.INGRESO),
            destinoDeLaAccion(AccionDelInicio.ANOTAR_INGRESO),
        )
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.TRASPASO), destinoDeLaAccion(AccionDelInicio.TRASPASAR))
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.PAGO_DE_TARJETA), destinoDeLaAccion(AccionDelInicio.PAGAR_TARJETA))
        // Repartir y el comprobante no son una pantalla: son las hojas de la cáscara, por su atajo.
        assertEquals(DestinoDeLaAccion.Atajo(AtajoDeAgregar.REPARTIR), AccionDelInicio.REPARTIR.destino)
        assertEquals(DestinoDeLaAccion.Atajo(AtajoDeAgregar.SUBIR_COMPROBANTE), AccionDelInicio.SUBIR_COMPROBANTE.destino)
        assertNull(destinoDeLaAccion(AccionDelInicio.REPARTIR))
        assertNull(destinoDeLaAccion(AccionDelInicio.SUBIR_COMPROBANTE))
        assertEquals(Screen.PorRevisar, destinoDeLaAccion(AccionDelInicio.REVISAR_AVISOS))
        assertEquals(Screen.AIChat(), destinoDeLaAccion(AccionDelInicio.PREGUNTAR_A_MOVI))
    }

    // ── Próximo pago (vuelta 39) ─────────────────────────────────────────────

    /** Lo vencido primero, y entre lo vencido el de más monto (la regla de la cola y del widget). */
    @Test
    fun `el proximo pago es el vencido mayor, y despues el que vence antes`() {
        val datos = todoPendiente.copy(
            upcoming = listOf(
                pago("r_cel", "Celular", 53_000, 1),
                pago("r_int", "Internet", 90_000, -2),
                pago("r_arr", "Arriendo", 1_850_000, -22),
                pago("r_gym", "Gimnasio", 139_900, 12),
            ),
        )
        val proximo = assertNotNull(proximoPagoDelInicio(datos))
        assertEquals("Arriendo", proximo.pago.nombre)
        assertEquals(3, proximo.masPagos)
        assertEquals("Anotar pago", proximo.accion)
        assertTrue(proximo.urgente)
        // Un pago vencido no es «próximo».
        assertEquals("Pago vencido · hace 22 días", proximo.titulo)
        // Sin vencidos, el que vence antes.
        val sinVencidos = datos.copy(upcoming = listOf(pago("r_gym", "Gimnasio", 139_900, 12), pago("r_cel", "Celular", 53_000, 1)))
        val celular = assertNotNull(proximoPagoDelInicio(sinVencidos))
        assertEquals("Celular", celular.pago.nombre)
        assertEquals("Próximo pago · vence mañana", celular.titulo)
        assertEquals(
            "Pago vencido · ayer",
            proximoPagoDelInicio(datos.copy(upcoming = listOf(pago("r_x", "Agua", 40_000, -1))))?.titulo,
        )
        // Sin pagos pendientes, no hay próximo pago.
        assertNull(proximoPagoDelInicio(datos.copy(upcoming = emptyList())))
    }

    /** «Movi propone hoy» no repite el pago que ya está en «Próximo pago». */
    @Test
    fun `con el proximo pago arriba las propuestas no traen el pago`() {
        val sinPago = propuestasDelInicio(todoPendiente, conElPago = false)
        assertTrue(sinPago.none { it.tipo == TipoDePropuesta.PAGO })
        assertEquals(
            listOf(TipoDePropuesta.DEBITO, TipoDePropuesta.CUADRE, TipoDePropuesta.ANOMALIA, TipoDePropuesta.CIERRE),
            sinPago.map { it.tipo },
        )
        // Y `visibleSections` lo decide por la definición: con PROXIMO_PAGO, sin el pago.
        val soloElPago = todoPendiente.copy(debitosPorConfirmar = emptyList(), cuadresPorAviso = emptyList(), anomalias = emptyList(), cierre = null,
            accounts = emptyList(), summary = com.jvillada.movi.shared.model.FinanceSummary(scope = com.jvillada.movi.shared.model.Scope.SELF, balance = 0, ingresos = 0, egresos = 0),
            credits = emptyList(), cards = emptyList())
        val tipos = visibleSections(com.jvillada.movi.shared.model.defaultDashboardDefinition(), soloElPago).map { it.type }
        assertTrue("PROXIMO_PAGO" in tipos)
        assertTrue("PROPUESTAS" !in tipos, "el único pendiente es el pago, y ya está arriba")
    }

    /**
     * **Dos columnas parejas** (vuelta 39): el héroe a la izquierda; cada bloque a la más corta; la derecha
     * arranca debajo de la banda; un bloque vacío no suma separación.
     */
    @Test
    fun `las columnas se reparten a la mas corta y la derecha arranca debajo de la banda`() {
        // Héroe 170, acciones 200, próximo pago 110, propuestas 500, últimos 230, cuentas 200, widgets 300.
        val altos = listOf(170, 200, 110, 500, 230, 200, 300)
        val lados = repartoEnDosColumnas(altos, fijosALaIzquierda = setOf(0), arribaDeLaDerecha = 116, separacion = 24)
        assertEquals(listOf(true, false, true, true, false, false, false), lados)
        val izquierda = altos.filterIndexed { i, _ -> lados[i] }.sum() + 24 * 2
        val derecha = 116 + altos.filterIndexed { i, _ -> !lados[i] }.sum() + 24 * 3
        assertTrue(minOf(izquierda, derecha) >= 0.7 * maxOf(izquierda, derecha), "$izquierda / $derecha")
        // Los fijos van a la izquierda aunque sea la más larga; un bloque vacío no cuenta.
        assertEquals(listOf(true, true, false), repartoEnDosColumnas(listOf(500, 0, 100), setOf(0, 1), 0, 24))
        assertFalse(inicioEnDosColumnas(8, 899.dp))
        assertTrue(inicioEnDosColumnas(8, 900.dp))
        assertFalse(inicioEnDosColumnas(1, 1_400.dp), "con un solo bloque, una columna")
    }

    // ── Últimos movimientos (vuelta 39) ──────────────────────────────────────

    private fun evento(id: String, dia: String, monto: Long, tipo: TransactionType = TransactionType.EXPENSE, traspaso: String? = null) =
        com.jvillada.movi.shared.model.FinancialEvent(
            id = id, accountId = "a1", type = tipo, amount = monto, currency = "COP", category = "Mercado",
            description = id, timestamp = 0L, transferId = traspaso,
        )

    @Test
    fun `los ultimos movimientos son las tres primeras filas de Actividad`() {
        val dias = listOf(
            com.jvillada.movi.shared.model.EventDay("2026-10-27", 0, listOf(evento("rappi", "27", 27_500), evento("sale", "27", 500_000, traspaso = "t1"), evento("entra", "27", 500_000, TransactionType.INCOME, traspaso = "t1"))),
            com.jvillada.movi.shared.model.EventDay("2026-10-26", 0, listOf(evento("d1", "26", 64_300), evento("uber", "26", 18_700))),
        )
        val ultimos = ultimosMovimientosDe(dias)
        // Rappi, el traspaso (sus dos patas) y D1: tres filas.
        assertEquals(listOf("rappi", "sale", "entra", "d1"), ultimos.map { it.id })
        val filas = filasDeUltimosMovimientos(ultimos)
        assertEquals(3, filas.size)
        assertTrue(filas[1] is com.jvillada.movi.ui.transactions.MovementRow.Transfer)
        assertEquals(emptyList(), ultimosMovimientosDe(emptyList()))
    }

    @Test
    fun `la cifra del heroe va de una cifra real a otra, nunca desde cero`() {
        assertEquals(3_240_000L, cifraEntre(3_240_000, 3_240_000, 0f))
        assertEquals(3_000_000L, cifraEntre(3_000_000, 3_400_000, 0f))
        assertEquals(3_200_000L, cifraEntre(3_000_000, 3_400_000, 0.5f))
        assertEquals(3_400_000L, cifraEntre(3_000_000, 3_400_000, 1f))
    }

    @Test
    fun `las iniciales del avatar son las del nombre y el apellido`() {
        assertEquals("JV", inicialesDelUsuario("Juan Villada"))
        assertEquals("JV", inicialesDelUsuario("  juan   villada restrepo "))
        assertEquals("C", inicialesDelUsuario("Caro"))
        assertEquals("?", inicialesDelUsuario(null))
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
        // Vuelta 75: el nombre único del período; con el mes de calendario, el mes (sin el año de hoy).
        assertEquals("Septiembre", periodoDelEncabezado(DashboardData(periodoActual = septiembre), anioDeHoy = 2026))
        assertEquals("Septiembre 2026", periodoDelEncabezado(DashboardData(periodoActual = septiembre), anioDeHoy = 2027))
        // Vuelta 54: con un corte a mitad de mes, el rango corto (el nombre solo, «Septiembre 2026»,
        // decía septiembre en agosto); el rango entero se cortaba a 360 dp (vuelta de diseño 11).
        val conCorte = DashboardData(periodoActual = septiembre, ajustesDePeriodo = PeriodSettings(cutoffDay = 25))
        assertEquals("25 ago – 24 sep", periodoDelEncabezado(conCorte, anioDeHoy = 2026))
        assertEquals("Tu período: del 25 de agosto al 24 de septiembre", descripcionDelPeriodoDelEncabezado(conCorte))
        assertEquals("Tu período: del 1 al 30 de septiembre", descripcionDelPeriodoDelEncabezado(DashboardData(periodoActual = septiembre)))
    }

    @Test
    fun `el heroe pide la cuenta solo cuando las cuentas contestaron vacias`() {
        assertEquals(false, elHeroePideLaCuenta(DashboardData()))
        assertEquals(true, elHeroePideLaCuenta(DashboardData(accounts = emptyList())))
        assertEquals(false, elHeroePideLaCuenta(DashboardData(accounts = listOf(Account("a1", "Nu", AccountType.SAVINGS, 0)))))
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
                listOf("HERO_BALANCE"), listOf("ACCIONES"), listOf("PROXIMO_PAGO"), listOf("PROPUESTAS"), listOf("ULTIMOS_MOVIMIENTOS"), listOf("CUENTAS"),
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

    /**
     * Vuelta de diseño 28: el detalle de una anomalía es una frase con su punto, y pegado a « · y 1
     * aviso más» quedaba «…anteriores). · y 1 aviso más». Se une sin el punto final.
     */
    @Test
    fun `el cuerpo de la anomalia une el detalle y el y N mas sin el punto final`() {
        val detalle = "Llevas \$480.000 en Restaurantes (el promedio de los 3 anteriores)."
        assertEquals(
            "Llevas \$480.000 en Restaurantes (el promedio de los 3 anteriores) · y 1 cosa más para revisar",
            cuerpoDeLaAnomalia(detalle, 1),
        )
        assertEquals("2 cobros de \$45.900 · y 3 cosas más para revisar", cuerpoDeLaAnomalia("2 cobros de \$45.900", 3))
        // Solo, el detalle queda como viene, con su punto.
        assertEquals(detalle, cuerpoDeLaAnomalia(detalle, 0))
        assertTrue(listOf(1, 2).none { ". ·" in cuerpoDeLaAnomalia(detalle, it) })
        // Y en la tarjeta de verdad.
        val dos = todoPendiente.copy(anomalias = listOf(anomalia.copy(detalle = detalle), anomalia.copy(huella = "otra")))
        assertEquals(
            "Llevas \$480.000 en Restaurantes (el promedio de los 3 anteriores) · y 1 cosa más para revisar",
            propuestasDelInicio(dos).single { it.tipo == TipoDePropuesta.ANOMALIA }.cuerpo,
        )
    }
}
