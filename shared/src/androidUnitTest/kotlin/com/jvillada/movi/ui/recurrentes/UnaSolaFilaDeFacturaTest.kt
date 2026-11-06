package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.LaCuotaQueCambia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 136 · en Pagos, una factura por pagar y una pagada son la misma fila
 *
 * El dueño, con la captura de su Pixel: «El formato de lo por pagar y pagado debería ser consistente, es
 * decir, misma altura de la card de la row y componentes». Antes la fila por pagar medía de ~180 a ~370 px
 * (nombre, monto, pastilla, la estimación de la cuota, «¿Es el de $X?» y «Anotar pago») y la pagada ~130.
 *
 * - **El alto**: todas las filas de una lista con todos los estados miden lo mismo a 360 dp con la letra de
 *   la app (×1,12); con la letra al 130 % de verdad (×1,456) también miden lo mismo entre ellas, más que
 *   antes (la misma regla: dos renglones reservados), y nada de lo corto se corta.
 * - **La hoja**: tocar una fila la abre; «Anotar pago» hace lo de siempre; «Sí, fue este» sella con el
 *   movimiento; en la hoja hay un solo amarillo.
 * - **La tarjeta**: «Falta por pagar» lleva el «Anotar pago» del más urgente.
 *
 * Sin el cambio no compila (`FilaDeFactura(pago, estado, extracto, onAbrir)`, `TAG_HOJA_DE_LA_FACTURA`), y
 * con la fila de antes las alturas diferían en más de 50 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xhdpi")
class UnaSolaFilaDeFacturaTest {

    @get:Rule val composeRule = createComposeRule()

    private val pago4M = FinancialEvent(
        id = "ev_4m", accountId = "acc", type = TransactionType.EXPENSE, amount = 4_000_000L,
        category = "Otros", description = "Transferencia", timestamp = 1_793_000_000_000L,
    )
    private val celular = PagoDelPeriodo(
        ruleId = "rr_celular", nombre = "Celular", monto = 53_077, pagado = false, diasParaVencer = 16,
        vence = "2026-11-22", categoria = "Celular",
    )
    private val vencida = PagoDelPeriodo(
        ruleId = "rr_internet", nombre = "Internet", monto = 145_000, pagado = false, diasParaVencer = -3,
        vence = "2026-11-03", periodoDelSello = "2026-11", categoria = "Servicios",
    )
    private val porConfirmar = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo", monto = 4_000_000, pagado = false, diasParaVencer = -1,
        vence = "2026-11-05", periodoDelSello = "2026-11", categoria = "Vivienda", candidatos = listOf(pago4M),
    )
    private val pagada = PagoDelPeriodo(
        ruleId = "rr_colegio", nombre = "Colegio Hija", monto = 4_000_000, pagado = true, diasParaVencer = -12,
        vence = "2026-10-25", periodoDelSello = "2026-10", eventId = "ev_colegio", pagadoEl = "2026-10-25",
        montoPagado = 4_000_000, categoria = "Educación",
    )
    private val cuotaEstimada = PagoDelPeriodo(
        ruleId = "${CREDIT_RULE_PREFIX}acc_veh", nombre = "Cuota Vehículo 8761", monto = 4_101_123, pagado = false,
        diasParaVencer = 11, vence = "2026-11-17", cuotaQueCambia = LaCuotaQueCambia(), categoria = "Cuota de crédito",
    )
    private val abono = PagoDelPeriodo(
        ruleId = "rr_abono", nombre = "Abono extra a Vehículo 8761", monto = 500_000, pagado = false,
        diasParaVencer = 9, vence = "2026-11-15", haciaDeuda = "acc_veh", categoria = "Traspaso",
    )
    private val seCobraSola = PagoDelPeriodo(
        ruleId = "${CREDIT_RULE_PREFIX}acc_hip", nombre = "Cuota Hipoteca Papá (Davibank)", monto = 2_228_152, pagado = false,
        diasParaVencer = 4, vence = "2026-11-10", seCobraSola = true, categoria = "Cuota de crédito",
    )
    // Revisión de la 136 (M5): también la tarjeta con su extracto, la abonada con el extracto abierto (C1), la que
    // no suma, la que va por debajo del mínimo, la que se está guardando y un ingreso.
    private val extracto = com.jvillada.movi.shared.model.ExtractoDeLaTarjeta(
        corte = "2026-10-20", fechaDePago = "2026-11-05", facturado = 1_292_700L, pagadoDesdeElCorte = 100_000L, minimo = 180_000L,
    )
    private val tarjetaConExtracto = PagoDelPeriodo(
        ruleId = "card_nu", nombre = "Pago tarjeta Nu", monto = 1_583_000, pagado = false, diasParaVencer = 7,
        montoEsSaldo = true, pagoMinimo = 180_000L, vence = "2026-11-05",
    )
    private val tarjetaAbonada = tarjetaConExtracto.copy(pagado = true, montoPagado = 100_000L, derivado = true, pagoMinimo = null)
    private val noSuma = PagoDelPeriodo(
        ruleId = "card_master", nombre = "Pago tarjeta Master Black 3684", monto = 2_400_000, pagado = false, diasParaVencer = 5,
        montoEsSaldo = true, vence = "2026-11-12",
    )
    private val bajoElMinimo = PagoDelPeriodo(
        ruleId = "card_amex", nombre = "Pago tarjeta AMEX", monto = 1_583_000, pagado = true, diasParaVencer = 7,
        montoEsSaldo = true, pagoMinimo = 180_000L, montoPagado = 100_000L, pagadoEnElPeriodo = 100_000L, derivado = true,
        eventId = "ev_amex", vence = "2026-11-05",
    )
    private val guardando = celular.copy(ruleId = "rr_gym", nombre = "Gimnasio")
    private val ingreso = PagoDelPeriodo(
        ruleId = "rr_salario", nombre = "Salario", monto = 9_000_000, pagado = false, diasParaVencer = 18,
        vence = "2026-11-24", esIngreso = true,
    )
    private val todas = listOf(
        celular, vencida, porConfirmar, pagada, cuotaEstimada, abono, seCobraSola,
        tarjetaConExtracto, tarjetaAbonada, noSuma, bajoElMinimo, guardando, ingreso,
    )
    private val extractos = mapOf("nu" to extracto)

    // ── El alto ────────────────────────────────────────────────────────────────

    /** Las filas, tal como las dibuja la lista (el estado sale de [loDeLaFactura], como en Pagos). */
    private fun montarLasFilas(escala: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        todas.forEach { pago ->
                            val lo = loDeLaFactura(pago, emptySet(), pago === guardando, emptyMap(), emptyMap(), extractos, enElTotal = true)
                            FilaDeFactura(pago = pago, estado = lo.estado, extracto = lo.extracto, onAbrir = {})
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun altos(): List<Float> = composeRule.onAllNodesWithTag(TAG_FILA_DE_FACTURA, useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.size.height / composeRule.density.density }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        val d = r.single()
        return (0 until d.lineCount).all { !d.isLineEllipsized(it) && d.getLineRight(it) - d.getLineLeft(it) <= nodo.size.width + 0.5f } &&
            !d.multiParagraph.didExceedMaxLines
    }

    @Test
    fun `todas las filas miden lo mismo a 360 con la letra de la app`() {
        montarLasFilas(1.12f)
        val altos = altos()
        assertEquals(todas.size, altos.size)
        val primero = altos.first()
        altos.forEachIndexed { i, alto ->
            assertTrue(abs(alto - primero) <= 0.5f, "«${todas[i].nombre}» mide $alto dp y la primera $primero dp")
        }
        // Los estados que la captura mostraba distintos, ahora cada uno en su renglón.
        for (estado in listOf(
            "Vence el 22 nov", "Venció hace 3 días", "¿Es el de \$4.000.000?", "Pagado · 25 oct", "Abono planeado · el día 15",
            "Se cobra sola el 10", "No suma: falta el mínimo", GUARDANDO_LA_FACTURA, "Llega el 24 nov",
        )) {
            assertTrue(composeRule.onAllNodesWithText(estado, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "«$estado»")
        }
        for (largo in listOf("Abonaste \$100.000 · faltan \$1.292.700", "Pagaste \$100.000 · faltan \$80.000")) {
            assertTrue(composeRule.onAllNodesWithText(largo, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "«$largo…»")
        }
        assertTrue(composeRule.onAllNodesWithText(com.jvillada.movi.ui.credits.ROTULO_DEL_EXTRACTO, useUnmergedTree = true).fetchSemanticsNodes().size == 1, "«extracto» solo en la que no está pagada")
        assertTrue(composeRule.onAllNodesWithTag(com.jvillada.movi.ui.components.ds.TAG_UNOS_DE_LA_CIFRA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "«unos» antes de la cuota estimada")
        // Ni botones ni «⋯» en la fila.
        for (boton in listOf(ETIQUETA_ANOTAR, ETIQUETA_SI_FUE_ESTE, ETIQUETA_QUITAR_LA_MARCA)) {
            assertTrue(composeRule.onAllNodesWithText(boton, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "«$boton» no está en la fila")
        }
    }

    /**
     * Revisión de la 136 (M2, M7): lo largo va solo si cabe entero en el renglón de estado; si no, lo corto,
     * entero. Vencida, gana el vencimiento sobre «No suma».
     */
    @Test
    fun `la cadencia y lo que no suma van en el estado si caben`() {
        val trimestral = celular.copy(ruleId = "rr_admin", nombre = "Administración", frecuencia = com.jvillada.movi.shared.model.FrecuenciaDelPago.TRIMESTRAL)
        val tarjeta = vencida.copy(ruleId = "card_nu", nombre = "Tarjeta Nu", montoEsSaldo = true)
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Column(Modifier.fillMaxWidth()) {
                        for (pago in listOf(trimestral, tarjeta)) {
                            val lo = loDeLaFactura(pago, emptySet(), false, emptyMap(), emptyMap(), emptyMap(), enElTotal = true)
                            FilaDeFactura(pago = pago, estado = lo.estado, extracto = null, onAbrir = {})
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // La cadencia cabe: va. «Venció hace 3 días · falta el mínimo» al lado de «$145.000 saldo» no cabe a 360:
        // gana el vencimiento, entero, y nada se corta a medias.
        for (texto in listOf("Vence el 22 nov · cada 3 meses", "Venció hace 3 días")) {
            val nodos = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, nodos.size, "«$texto»")
            assertTrue(seVeEntero(nodos.single()), "«$texto» entero")
        }
        assertTrue(composeRule.onAllNodesWithText("falta el mínimo", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `con la letra al 130 por ciento crecen todas igual y nada corto se corta`() {
        montarLasFilas(1.456f)
        val altos = altos()
        val primero = altos.first()
        altos.forEachIndexed { i, alto ->
            assertTrue(abs(alto - primero) <= 0.5f, "«${todas[i].nombre}» mide $alto dp y la primera $primero dp")
        }
        // La regla (con la letra grande, el nombre y el estado reservan dos renglones cada uno) la prueba
        // `UnaSolaFilaDeFacturaReglasTest` (`renglonesDeLaFactura`).
        // Las cifras, las fechas y los estados cortos se leen enteros.
        composeRule.onAllNodesWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNodes().forEach {
            assertTrue(seVeEntero(it), "«${texto(it)}» se corta a ×1,456")
        }
        for (corto in listOf("Vence el 22 nov", "Venció hace 3 días", "Pagado · 25 oct", "¿Es el de \$4.000.000?", "Celular")) {
            composeRule.onAllNodesWithText(corto, useUnmergedTree = true).fetchSemanticsNodes().forEach {
                assertTrue(seVeEntero(it), "«$corto» se corta a ×1,456")
            }
        }
    }

    // ── La hoja ────────────────────────────────────────────────────────────────

    private val anotados = mutableListOf<String>()
    private val confirmados = mutableListOf<Pair<String, String>>()

    @Composable
    private fun Seccion(lista: List<PagoDelPeriodo>) {
        SeccionChecklistDelPeriodo(
            checklist = lista,
            cargando = false,
            pudoLeer = true,
            marcando = emptySet(),
            onConfirmar = { p, id -> confirmados += p.ruleId to id },
            onNoFueEste = { _, _ -> },
            onAnotarMovimiento = { anotados += it.ruleId },
            onQuitarLaMarca = {},
            onReintentar = {},
            onAbrir = {},
            onVerMovimiento = {},
        )
    }

    private fun montarLaSeccion(lista: List<PagoDelPeriodo>) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Seccion(lista) } } }
        composeRule.waitForIdle()
    }

    private fun hoja(): SemanticsNode? =
        composeRule.onAllNodesWithTag(TAG_HOJA_DE_LA_FACTURA, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()

    private fun amarillosEn(nodo: SemanticsNode): Int =
        (if (nodo.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + nodo.children.sumOf(::amarillosEn)

    private fun tocarEnLaHoja(texto: String) {
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(texto)) and hasAnyAncestor(hasTestTag(TAG_HOJA_DE_LA_FACTURA)),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun enLaHoja(texto: String): Boolean = composeRule.onAllNodes(
        hasText(texto, substring = true) and hasAnyAncestor(hasTestTag(TAG_HOJA_DE_LA_FACTURA)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `tocar una factura por pagar abre su hoja, con Anotar pago en amarillo, que anota como antes`() {
        montarLaSeccion(listOf(vencida, celular))
        assertTrue(hoja() == null)

        composeRule.abrirLaFactura("Celular")
        composeRule.waitForIdle()
        val nodo = hoja()
        assertTrue(nodo != null, "la hoja se abre")
        assertEquals(1, amarillosEn(nodo!!), "un solo amarillo en la hoja")
        assertTrue(enLaHoja("Vence el 22 nov"))

        tocarEnLaHoja(ETIQUETA_ANOTAR)
        assertEquals(listOf("rr_celular"), anotados, "el «Anotar pago» de siempre (hojaParaAnotar)")
        assertTrue(hoja() == null, "la hoja se cierra")
    }

    @Test
    fun `con un candidato, la hoja pregunta y Si, fue este sella con ese movimiento`() {
        montarLaSeccion(listOf(porConfirmar, celular))

        composeRule.abrirLaFactura("Arriendo")
        composeRule.waitForIdle()
        val nodo = hoja()!!
        assertEquals(1, amarillosEn(nodo), "«Sí, fue este» es el amarillo de la hoja")
        assertFalse(enLaHoja(ETIQUETA_ANOTAR), "anotar duplicaría el gasto")
        assertTrue(enLaHoja(ETIQUETA_NO_FUE_ESTE))

        tocarEnLaHoja(ETIQUETA_SI_FUE_ESTE)
        assertEquals(listOf("rr_arriendo" to "ev_4m"), confirmados)
    }

    @Test
    fun `la hoja de una pagada no lleva amarillo`() {
        montarLaSeccion(listOf(pagada, celular))

        composeRule.abrirLaFactura("Colegio Hija")
        composeRule.waitForIdle()
        assertEquals(0, amarillosEn(hoja()!!))
        assertTrue(enLaHoja(ETIQUETA_VER_MOVIMIENTO))
        assertTrue(enLaHoja(ETIQUETA_QUITAR_LA_MARCA))
        assertTrue(enLaHoja("Editar el pago fijo"))
    }

    // ── La tarjeta ─────────────────────────────────────────────────────────────

    @Test
    fun `Falta por pagar lleva el Anotar pago del mas urgente`() {
        montarLaSeccion(listOf(celular, vencida))

        val tarjeta = composeRule.onAllNodesWithTag(TAG_LO_MAS_URGENTE, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, tarjeta.size)
        assertEquals(1, amarillosEn(tarjeta.single()), "el amarillo de Pagos es el pago más urgente")
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasAnyAncestor(hasTestTag(TAG_LO_MAS_URGENTE)),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("rr_internet"), anotados, "lo vencido primero")
    }

    // ── La ficha ancha (revisión, M8) ───────────────────────────────────────────

    /** Fuera del teléfono la hoja es la ficha de 520 dp, con «Anotar pago» a la izquierda y no a todo el ancho. */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h900dp-mdpi")
    fun `en la ficha ancha Anotar pago va a la izquierda, no a todo el ancho`() {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(com.jvillada.movi.ui.components.LocalWindowWidthClass provides com.jvillada.movi.ui.components.WindowWidthClass.Expanded) {
                    Box(Modifier.fillMaxSize()) { Seccion(listOf(celular)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.abrirLaFactura("Celular")
        composeRule.waitForIdle()
        val hoja = hoja()!!.boundsInRoot
        val boton = composeRule.onAllNodesWithTag(TAG_ANOTAR_EN_LA_HOJA, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        assertTrue(abs(boton.left - hoja.left) <= 1f, "a la izquierda de la ficha")
        assertTrue(boton.width < hoja.width / 2, "no a todo el ancho (${boton.width} de ${hoja.width})")
    }
}

