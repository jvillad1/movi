package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.CATEGORIA_NUEVA
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.destinos.DestinosScreen
import com.jvillada.movi.ui.destinos.TAG_FICHA_EN_LA_LISTA
import com.jvillada.movi.ui.espacios.ELIGE_A_LA_PERSONA
import com.jvillada.movi.ui.espacios.HojaNuevoEspacio
import com.jvillada.movi.ui.espacios.NUEVA_PERSONA
import com.jvillada.movi.ui.mas.MasScreen
import com.jvillada.movi.ui.mas.RECIBE_TUS_ALERTAS_POR_CORREO
import com.jvillada.movi.ui.mas.TITULO_DE_NOTIFICACIONES
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.quickadd.TAG_BARRA_DE_GUARDAR_DEL_TRASPASO
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_DIFERENCIA_DEL_WIDGET
import com.jvillada.movi.ui.sdui.TAG_PROPUESTAS_DEL_INICIO
import com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA
import com.jvillada.movi.ui.sdui.TAG_WIDGET_FALTA_POR_PAGAR
import com.jvillada.movi.ui.sdui.WidgetEnQueSeVa
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 76, montada
 *
 * La fila de Caro con su saldo de grupo; el widget «Falta por pagar» que no se pinta con todo pagado;
 * «nuevo» en un solo color; «Notificaciones» en Tú; «cosas para revisar» y el número del encabezado;
 * Traspaso con la barra de abajo; la fila del correo según el server; y elegir a la persona de un grupo
 * sin texto cortado a 360.
 *
 * `sdk = [34]` y `NATIVE`: hace falta el motor de texto real para medir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class Vuelta76EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String, substring: Boolean = true): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentroDe(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentroDe)

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult? {
        val r = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(r)
        return r.firstOrNull()
    }

    /** Los textos dentro de [raiz] que se cortan con «…» o no les caben los renglones. */
    private fun cortados(raiz: SemanticsNode): List<String> {
        fun textos(n: SemanticsNode): List<SemanticsNode> =
            (if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(n) else emptyList()) + n.children.flatMap(::textos)
        return textos(raiz).mapNotNull { nodo ->
            val d = layoutDe(nodo) ?: return@mapNotNull null
            when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "«${texto(nodo)}» se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "«${texto(nodo)}» no le caben los renglones"
                else -> null
            }
        }
    }

    // ── (1) La fila del tercero con su saldo de grupo ────────────────────────

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "31973270756")
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio("dst_caro", "Caro")))
    private val gastos = listOf(
        GastoDelEspacio(id = "g1", espacioId = casa.id, pagadoPor = "dst_caro", monto = 240_000, partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L)),
        GastoDelEspacio(id = "g2", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 576_100, partes = mapOf(MIEMBRO_YO to 288_050L, "dst_caro" to 288_050L)),
    )

    @Test
    fun `la fila de Caro dice lo de su ficha y no Nada este periodo`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDestinos(): List<DestinoConocido> = listOf(caro)
            override suspend fun getEspacios(): List<Espacio> = listOf(casa)
            override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio {
                val saldo = saldoDelEspacio(casa, gastos, emptyList())
                return DetalleDelEspacio(espacio = casa, saldo = saldo, delPeriodo = saldo, gastos = gastos)
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Te debe \$168.050 · Casa con Caro") }
        val fila = conTag("$TAG_FICHA_EN_LA_LISTA:dst_caro").single()
        val enLaFila = textosDentroDe(fila)
        assertTrue("Te debe \$168.050 · Casa con Caro" in enLaFila, "la fila dice lo del grupo: $enLaFila")
        assertTrue(enLaFila.none { it == "Nada este período" }, "«Nada este período» contradecía a la ficha: $enLaFila")
        assertTrue(cortados(fila).isEmpty(), cortados(fila).joinToString())
    }

    // ── (2) El widget con todo pagado ────────────────────────────────────────

    private fun regla(id: String, nombre: String, categoria: String = "Vivienda", monto: Long = 53_000) =
        RecurringRule(id = id, name = nombre, category = categoria, amount = monto, dayOfMonth = 10, type = TransactionType.EXPENSE)

    @Test
    fun `con todo pagado el widget no se pinta y En que se va toma el ancho`() {
        val data = DashboardData(
            upcoming = listOf(UpcomingPayment(regla("rr_cel", "Celular"), "2026-10-10", 23, PaymentStatus.UPCOMING)),
            ocurrencias = listOf(OccurrenceState(ruleId = "rr_cel", period = "2026-09", dueDate = "2026-09-10", occurred = true)),
            ajustesDePeriodo = PeriodSettings(),
            periodoActual = PeriodoFinanciero(2026, 9),
            spentByCategory = mapOf("Mercado" to 300_000L, "Otros" to 120_000L),
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(definition = defaultDashboardDefinition(), data = data, modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true)
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, conTag(TAG_WIDGET_FALTA_POR_PAGAR).size, "«Ya salió todo lo del período» ocupaba una tarjeta")
        assertTrue(!hay("Ya salió todo lo del período"))
        val enQueSeVa = conTag(TAG_TARJETA_EN_QUE_SE_VA).single().boundsInRoot
        val raiz = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(enQueSeVa.width > raiz.width * 0.8f, "a lo ancho: ${enQueSeVa.width} de ${raiz.width}")
    }

    // ── (3) «nuevo» sin color mixto ──────────────────────────────────────────

    @Test
    fun `nuevo va en un solo color y la cuota no es nueva`() {
        val data = DashboardData(
            // Otros $1.800.000 (antes en rojo) y Salud $271.900 (antes en el color del texto): las dos «nuevo».
            spentByCategory = mapOf("Otros" to 1_800_000L, CUOTA_CATEGORY to 980_000L, "Salud" to 271_900L),
            gastoDelAnterior = emptyMap(),
            diasComparados = 12,
            upcoming = emptyList(),
        )
        composeRule.setContent { MoviTheme { Column(Modifier.fillMaxWidth()) { WidgetEnQueSeVa(data, onNavigate = {}) } } }
        composeRule.waitForIdle()
        val diferencias = conTag(TAG_DIFERENCIA_DEL_WIDGET)
        assertEquals(listOf(CATEGORIA_NUEVA, CATEGORIA_NUEVA), diferencias.map(::texto), "la cuota no dice «nuevo»")
        val colores = diferencias.map { layoutDe(it)!!.layoutInput.style.color }.toSet()
        assertEquals(1, colores.size, "un solo color para «nuevo»: $colores")
        assertTrue(colores.single() != Color.Unspecified)
    }

    // ── (4) «Notificaciones» en Tú, y «cosas para revisar» ───────────────────

    private val perfil = UserProfile(
        id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25, reminderLeadDays = 5,
    )

    private fun montarTu(configurado: Boolean) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun isScreenAdmin(): Boolean = false
            override suspend fun getDireccionDeCorreo(): DireccionDeCorreo =
                DireccionDeCorreo(token = "abc", direccion = if (configurado) "alertas+abc@movi.resend.app" else null, configurado = configurado)
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { MasScreen(onNavigate = {}, onLogout = {}, esAndroid = false) } }
        }
        composeRule.waitUntil(5_000) { hay("Cada día 25") }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h3200dp-xhdpi")
    fun `Tu dice Notificaciones y no Avisos`() {
        montarTu(configurado = true)
        composeRule.onNodeWithText(TITULO_DE_NOTIFICACIONES, useUnmergedTree = true).assertExists()
        assertTrue(!hay("Avisos", substring = false), "«Avisos» son los mensajes del banco")
        assertTrue(!hay("Activar avisos"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h3200dp-xhdpi")
    fun `la fila del correo solo con direccion, y lleva a Copiar`() {
        montarTu(configurado = true)
        composeRule.waitUntil(5_000) { hay("alertas+abc@movi.resend.app") }
        assertTrue(hay(RECIBE_TUS_ALERTAS_POR_CORREO))
        assertTrue(!hay("Todavía no está disponible"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h3200dp-xhdpi")
    fun `sin reenvio en el server no hay fila muerta`() {
        montarTu(configurado = false)
        composeRule.waitForIdle()
        assertTrue(!hay(RECIBE_TUS_ALERTAS_POR_CORREO))
        assertTrue(!hay("Todavía no está disponible"))
    }

    private fun anomalia(i: Int) = Anomalia(
        huella = "h$i", tipo = TipoDeAnomalia.CATEGORIA_POR_ENCIMA, titulo = "Gasto alto en Otros",
        detalle = "Llevas \$1.800.000.", evidencia = emptyList(),
    )

    @Test
    fun `Movi propone hoy cuenta lo mismo que su tarjeta`() {
        val data = DashboardData(
            anomalias = (1..4).map(::anomalia),
            upcoming = emptyList(),
            ajustesDePeriodo = PeriodSettings(),
            periodoActual = PeriodoFinanciero(2026, 10),
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(definition = defaultDashboardDefinition(), data = data, modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true)
                }
            }
        }
        composeRule.waitUntil(5_000) { conTag(TAG_PROPUESTAS_DEL_INICIO).isNotEmpty() }
        val textos = textosDentroDe(conTag(TAG_PROPUESTAS_DEL_INICIO).single())
        assertTrue(" · 4" in textos, "el encabezado cuenta las 4: $textos")
        assertTrue(textos.any { it.endsWith("y 3 cosas más para revisar") }, "$textos")
        assertTrue(textos.none { "avisos más" in it })
    }

    // ── (5) Traspaso con la barra de abajo ───────────────────────────────────

    private val ahorros = Account("a1", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 13_845_800)
    private val nu = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    private fun montarAgregar(pestana: String) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, nu, libre)
            override suspend fun getCredits(): List<CreditSummary> = listOf(CreditSummary(account = libre, terms = null, paidPct = null))
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}) }
                    Spacer(Modifier.height(Medida.altoDeBarraDePestanas))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(pestana).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { hay(ahorros.name) }
    }

    private fun laBarraVaAbajo(pestana: String, boton: String) {
        montarAgregar(pestana)
        val barra = composeRule.onNodeWithTag(TAG_BARRA_DE_GUARDAR_DEL_TRASPASO, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(hay(boton), "el botón dice «$boton»")
        val pantalla = composeRule.onRoot().getUnclippedBoundsInRoot()
        val piso = pantalla.bottom - Medida.altoDeBarraDePestanas
        assertTrue(barra.bottom <= piso, "$pestana: la barra se ve sin desplazar")
        // Pegada abajo, como la de Gasto: debajo solo el renglón reservado. Lo que no cabe (el teclado,
        // en Pagar a 360 × 740) se desplaza por arriba de ella; la barra no se mueve.
        assertTrue(piso - barra.bottom < 48.dp, "$pestana: la barra va abajo de la hoja (${piso - barra.bottom})")
        assertTrue(!hay("Se guarda en línea"), "la línea larga se fue")
    }

    @Test
    fun `Traspaso usa la barra de abajo`() = laBarraVaAbajo("Traspaso", "Guardar traspaso")

    @Test
    fun `Pagar usa la barra de abajo`() = laBarraVaAbajo("Pagar", "Registrar pago")

    // ── (6) Elegir a la persona de un grupo, a 360 ───────────────────────────

    private fun montarNuevoGrupo(personas: List<DestinoConocido>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDestinos(): List<DestinoConocido> = personas
            override suspend fun getEspacios(): List<Espacio> = emptyList()
            override suspend fun getAccounts(): List<Account> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { HojaNuevoEspacio(onDismiss = {}, onCreado = {}, onIrATerceros = {}) } }
        }
        composeRule.waitUntil(5_000) { hay(NUEVA_PERSONA) }
    }

    @Test
    fun `elegir a la persona no se corta a 360, sin nadie todavia`() {
        montarNuevoGrupo(emptyList())
        assertTrue(hay(ELIGE_A_LA_PERSONA))
        assertTrue(!hay("número o su llave"), "pedir número o llave es de la hoja de la persona")
        val fallas = cortados(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(fallas.isEmpty(), fallas.joinToString("\n"))
    }

    @Test
    fun `elegir a la persona no se corta a 360, con la lista`() {
        montarNuevoGrupo(listOf(caro))
        assertTrue(hay("Caro", substring = false))
        val fallas = cortados(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(fallas.isEmpty(), fallas.joinToString("\n"))
    }
}
