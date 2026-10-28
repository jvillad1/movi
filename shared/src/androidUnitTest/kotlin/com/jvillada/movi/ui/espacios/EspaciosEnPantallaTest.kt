package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import com.jvillada.movi.platform.ComoSalioElMensaje
import com.jvillada.movi.platform.HojaDeCompartir
import com.jvillada.movi.platform.LocalHojaDeCompartir
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.transactions.AccionDelMovimiento
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.tagDeAccionDelMovimiento
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TAG_TU_PARTE
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Espacios en pantalla (R3)
 *
 * La pantalla del espacio dice el saldo con «Pedirle a Caro» (vuelta de diseño 26) y «Marcar como
 * saldado», que pregunta antes y llama al repositorio; cada gasto dice su fecha;
 * «Repartir» desde el detalle de un movimiento lo manda al espacio; y la fila de un movimiento
 * repartido dice «Tu parte» bajo el monto entero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class EspaciosEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun gasto(id: String, pagadoPor: String, monto: Long, eventoId: String? = null) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
        descripcion = if (pagadoPor == MIEMBRO_YO) "Mercado Éxito" else "Mercado del sábado",
        fecha = 1_761_000_000_000L, eventoId = eventoId, partes = partesDelGasto(monto, casa, pagadoPor),
    )

    private val gastos = listOf(gasto("g1", MIEMBRO_YO, 2_100_000, "e1"), gasto("g2", "dst_caro", 1_320_000))
    private val detalle = DetalleDelEspacio(
        espacio = casa,
        saldo = saldoDelEspacio(casa, gastos, emptyList()),
        delPeriodo = saldoDelEspacio(casa, gastos, emptyList()),
        gastos = gastos,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Toca el botón que dice [texto]; con varios a la vista, el último (el de la hoja de encima). */
    private fun tocarBoton(texto: String) {
        composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no hay un botón «$texto»" } }
        val nodos = composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
        val cuantos = nodos.fetchSemanticsNodes().size
        nodos[cuantos - 1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private open inner class ConLaCasa : RepositorioDePrueba() {
        val liquidaciones = mutableListOf<LiquidacionDelEspacio>()
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = detalle
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun liquidarEspacio(espacioId: String, liquidacion: LiquidacionDelEspacio): LiquidacionDelEspacio =
            liquidacion.also { liquidaciones += it }
    }

    /**
     * El texto de cada botón a la vista (lo tocable con un texto adentro), con su variante (la dice su
     * semántica, `VarianteDeBotonKey`; `null` si no es un `Boton`). Se busca por el toque y no por el
     * `testTag` del sistema: un `Boton` con su propio tag ya no lleva `TAG_BOTON`.
     */
    private fun botones(): Map<String, VarianteDeBoton?> =
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.getOrNull(VarianteDeBotonKey) != null }
            .associate { nodo ->
                val texto = nodo.children.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString("") { it.text }
                texto to nodo.config.getOrNull(VarianteDeBotonKey)
            }

    /** Una hoja de compartir de mentira: guarda lo que se le mandó y dice que se copió (la web sin `navigator.share`). */
    private class HojaDePrueba(private val como: ComoSalioElMensaje) : HojaDeCompartir {
        val mandados = mutableListOf<String>()
        override val abreLaHojaDelSistema: Boolean = como == ComoSalioElMensaje.POR_LA_HOJA
        override fun compartir(texto: String) { mandados += texto }
        override fun copiar(texto: String) { mandados += texto }
        override fun mandarMensaje(texto: String): ComoSalioElMensaje = como.also { mandados += texto }
    }

    private fun montarLaPestana(hoja: HojaDeCompartir? = null) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHojaDeCompartir provides hoja) {
                MoviTheme { Box(Modifier.fillMaxSize()) { EspaciosScreen(onNavigate = {}) } }
            }
        }
    }

    /**
     * Vuelta de diseño 26: con Caro debiendo, el principal es «Pedirle a Caro» y «Marcar como saldado»
     * pasa a `Fantasma`; saldar pregunta antes («¿Caro ya te pagó $390.000?») y después hace lo de
     * siempre: la liquidación de Caro al dueño, sin vincular movimiento.
     */
    @Test
    fun `la pantalla del espacio dice quien debe, cuanto y deja marcar como saldado`() {
        val repo = ConLaCasa()
        Repositories.sustitutoDePrueba = repo
        montarLaPestana()

        esperarTexto("Caro te debe")
        esperarTexto("para quedar 50/50")
        esperarTexto("Quién puso qué")
        esperarTexto("Mitad")
        esperarTexto("Mercado del sábado")
        esperarTexto("Pedirle a Caro")
        assertEquals(VarianteDeBoton.Principal, botones()["Pedirle a Caro"])
        assertEquals(VarianteDeBoton.Fantasma, botones()["Marcar como saldado"])

        // Antes de saldar, la pregunta. «Cancelar» no salda nada.
        tocarBoton("Marcar como saldado")
        esperarTexto("¿Caro ya te pagó \$390.000?")
        esperarTexto("Esto deja el espacio en cero.")
        tocarBoton("Cancelar")
        composeRule.waitForIdle()
        assertEquals(emptyList(), repo.liquidaciones, "cancelar no salda")

        tocarBoton("Marcar como saldado")
        esperarTexto("¿Caro ya te pagó \$390.000?")
        tocarBoton("Sí, ya me pagó")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.liquidaciones.isNotEmpty() }

        val l = repo.liquidaciones.single()
        assertEquals("dst_caro", l.de)
        assertEquals(MIEMBRO_YO, l.a)
        assertEquals(390_000L, l.monto)
        assertNull(l.eventoId, "sin una transferencia elegida, no se vincula ninguna")
    }

    /** «Pedirle a Caro» manda el mensaje; en la web sin hoja, dice «Mensaje copiado» y ofrece WhatsApp. */
    @Test
    fun `Pedirle a Caro manda el mensaje y, si se copio, lo dice`() {
        Repositories.sustitutoDePrueba = ConLaCasa()
        val hoja = HojaDePrueba(ComoSalioElMensaje.COPIADO)
        montarLaPestana(hoja)
        esperarTexto("Pedirle a Caro")
        composeRule.onAllNodesWithTag(TAG_MENSAJE_COPIADO, useUnmergedTree = true).fetchSemanticsNodes()
            .let { assertEquals(0, it.size, "antes de pedir no dice nada") }

        tocarBoton("Pedirle a Caro")
        val mensaje = hoja.mandados.single()
        assertTrue(mensaje.startsWith("Hola, Caro. Me debes \$390.000 de Casa con Caro:"), mensaje)
        assertTrue("Mercado Éxito · 20 oct · tu parte \$1.050.000" in mensaje, mensaje)
        esperarTexto("Mensaje copiado")
        esperarTexto("Abrir WhatsApp")
    }

    /** Con la hoja del sistema (Android, iOS, `navigator.share`) no hay aviso: la hoja ya se ve. */
    @Test
    fun `con la hoja del sistema no dice Mensaje copiado`() {
        Repositories.sustitutoDePrueba = ConLaCasa()
        val hoja = HojaDePrueba(ComoSalioElMensaje.POR_LA_HOJA)
        montarLaPestana(hoja)
        esperarTexto("Pedirle a Caro")
        tocarBoton("Pedirle a Caro")
        assertEquals(1, hoja.mandados.size)
        composeRule.onAllNodesWithText("Mensaje copiado", useUnmergedTree = true).fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
    }

    /** Cuando tú le debes, no hay nada que pedir: el principal sigue siendo marcar que pagaste. */
    @Test
    fun `cuando tu le debes, el principal es marcar como saldado`() {
        val alReves = listOf(gasto("g1", MIEMBRO_YO, 1_320_000, "e1"), gasto("g2", "dst_caro", 2_100_000))
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = detalle.copy(
                saldo = saldoDelEspacio(casa, alReves, emptyList()),
                delPeriodo = saldoDelEspacio(casa, alReves, emptyList()),
                gastos = alReves,
            )
        }
        montarLaPestana()
        esperarTexto("Le debes a Caro")
        esperarTexto("Marcar como saldado")
        assertEquals(VarianteDeBoton.Principal, botones()["Marcar como saldado"])
        assertNull(botones()["Pedirle a Caro"], "no hay nada que pedir")
    }

    /**
     * Vuelta de diseño 26: cada gasto dice su fecha en el subtítulo; desde la vuelta 31, primero. Vuelta
     * 32: quién pagó es solo el nombre, y «Mercado del sábado» / «Mercado Éxito» ya dicen la categoría.
     */
    @Test
    fun `cada gasto del espacio dice su fecha`() {
        Repositories.sustitutoDePrueba = ConLaCasa()
        montarLaPestana()
        esperarTexto("20 oct · Caro")
        esperarTexto("20 oct · Tú")
    }

    // ── Vuelta 31: los gastos se tocan ───────────────────────────────────────

    /** Toca la fila de gasto que dice [titulo]. */
    private fun tocarGasto(titulo: String) {
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, TAG_GASTO_DEL_ESPACIO) and hasText(titulo) and hasClickAction(),
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `cada gasto del espacio es una fila tocable`() {
        Repositories.sustitutoDePrueba = ConLaCasa()
        montarLaPestana()
        esperarTexto("Mercado del sábado")
        val gastos = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, TAG_GASTO_DEL_ESPACIO))
            .fetchSemanticsNodes()
        assertEquals(2, gastos.size)
        gastos.forEach { assertTrue(it.config.contains(SemanticsActions.OnClick), "un gasto que no responde al tocarlo") }
    }

    /** Un gasto que pagó Caro no tiene movimiento: abre su hoja, y «Quitar del espacio» pregunta y lo quita. */
    @Test
    fun `un gasto que pago otro abre su hoja y se puede quitar`() {
        val quitados = mutableListOf<Pair<String, String>>()
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun quitarGastoDelEspacio(espacioId: String, gastoId: String) {
                quitados += espacioId to gastoId
            }
        }
        montarLaPestana()
        esperarTexto("Mercado del sábado")
        tocarGasto("Mercado del sábado")

        esperarTag(TAG_DETALLE_DEL_GASTO)
        listOf("Pagó Caro", "No salió de tus cuentas", "Tu parte", "Mitad", "Categoría", "Fecha", "20 de octubre de 2025")
            .forEach { esperarTexto(it) }
        tocarBoton(QUITAR_DEL_ESPACIO)
        esperarTexto("¿Quitar «Mercado del sábado» de Casa con Caro?")
        tocarBoton("Quitar")
        composeRule.waitUntil(timeoutMillis = 5_000) { quitados.isNotEmpty() }
        assertEquals(listOf("esp_casa" to "g2"), quitados)
    }

    /** Un gasto con movimiento propio abre el MISMO detalle de Movimientos. */
    @Test
    fun `un gasto con movimiento abre el detalle del movimiento`() {
        val repartido = mercado.copy(id = "e1", amount = 2_100_000L, parteAjena = 1_050_000L, espacioId = casa.id)
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getEventsByDay(): List<EventDay> = listOf(EventDay("2025-10-20", -2_100_000L, listOf(repartido)))
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        }
        montarLaPestana()
        esperarTexto("Mercado Éxito")
        tocarGasto("Mercado Éxito")

        esperarTexto("Repartido en Casa con Caro")
        composeRule.onNodeWithTag(TAG_REPARTIR, useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag(TAG_DETALLE_DEL_GASTO, useUnmergedTree = true).fetchSemanticsNodes()
            .let { assertEquals(0, it.size, "no es la hoja del gasto sin movimiento") }
    }

    /** Si el movimiento no aparece (anulado, o falló la lectura), la hoja del gasto: nunca un toque mudo. */
    @Test
    fun `si el movimiento no aparece, abre la hoja del gasto`() {
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        }
        montarLaPestana()
        esperarTexto("Mercado Éxito")
        tocarGasto("Mercado Éxito")
        esperarTag(TAG_DETALLE_DEL_GASTO)
        esperarTexto("Pagaste tú")
    }

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val mercado = FinancialEvent(
        id = "e_mercado",
        accountId = bancolombia.id,
        type = TransactionType.EXPENSE,
        amount = 240_000L,
        category = "Mercado",
        description = "Mercado Éxito",
        timestamp = 1_761_000_000_000L,
    )

    @Test
    fun `Repartir desde el detalle de un movimiento lo manda al espacio`() {
        val repartidos = mutableListOf<Pair<String, String>>()
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
            override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
                repartidos += espacioId to eventoId
                return gasto("g_nuevo", MIEMBRO_YO, 240_000, eventoId)
            }
        }
        var cambiado = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = mercado, cuentas = listOf(bancolombia), onDismiss = {}, onCambiado = { cambiado = true })
                }
            }
        }
        // Vuelta de diseño 17: «Repartir» es una acción de la fila; abre una fila por espacio.
        esperarTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Repartir))
        composeRule.onNodeWithTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Repartir)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        esperarTexto("Tu parte \$120.000")
        composeRule.onAllNodesWithText("Casa con Caro").filter(hasClickAction()).onLast()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { repartidos.isNotEmpty() }

        assertEquals(listOf("esp_casa" to "e_mercado"), repartidos)
        composeRule.waitUntil(timeoutMillis = 5_000) { cambiado }
    }

    @Test
    fun `un gasto comun no lleva la tarjeta grande pero si la accion Repartir`() {
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { HojaDelMovimiento(event = mercado, cuentas = listOf(bancolombia), onDismiss = {}, onCambiado = {}) } }
        }
        esperarTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Repartir))

        composeRule.onAllNodesWithText("¿Es un gasto compartido?", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertEquals(0, it.size, "la tarjeta grande ya no va en cada gasto") }
        composeRule.onAllNodesWithTag(TAG_REPARTIR, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        composeRule.onAllNodesWithTag(TAG_SECCION_DE_REPARTIR, useUnmergedTree = true).fetchSemanticsNodes()
            .let { assertEquals(0, it.size, "la sección solo se abre desde la acción") }
    }

    @Test
    fun `un gasto repartido muestra su bloque y no la accion`() {
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        }
        val repartido = mercado.copy(parteAjena = 120_000L, espacioId = casa.id)
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { HojaDelMovimiento(event = repartido, cuentas = listOf(bancolombia), onDismiss = {}, onCambiado = {}) } }
        }
        esperarTexto("Repartido en Casa con Caro")
        esperarTexto("Tu parte \$120.000")
        composeRule.onNodeWithTag(TAG_REPARTIR, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Repartir), useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertEquals(0, it.size, "ya está repartido: se quita desde el bloque") }
    }

    @Test
    fun `sin espacios el detalle no ofrece repartir`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { HojaDelMovimiento(event = mercado, cuentas = listOf(bancolombia), onDismiss = {}, onCambiado = {}) } }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TAG_REPARTIR, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        composeRule.onAllNodesWithTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Repartir), useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `la fila de un movimiento repartido dice tu parte y debajo el monto entero`() {
        // Vuelta de diseño 30: la cifra principal es tu parte; el total va debajo, con quién.
        val repartido = mercado.copy(parteAjena = 120_000L, espacioId = casa.id)
        composeRule.setContent {
            MoviTheme {
                MovementSingleRow(
                    tx = repartido, accountNames = mapOf(bancolombia.id to "Bancolombia"),
                    espacios = mapOf(casa.id to casa), onClick = {},
                )
            }
        }
        composeRule.onNodeWithTag(TAG_TU_PARTE, useUnmergedTree = true).assertIsDisplayed()
        esperarTexto("−\$120.000")
        esperarTexto("de \$240.000 · ")
    }

    @Test
    fun `una fila sin repartir no dice Tu parte`() {
        composeRule.setContent {
            MoviTheme { MovementSingleRow(tx = mercado, accountNames = emptyMap(), onClick = {}) }
        }
        composeRule.onAllNodesWithTag(TAG_TU_PARTE, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }
}
