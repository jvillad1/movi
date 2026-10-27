package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.profile.BORRAR_MI_CUENTA
import com.jvillada.movi.ui.profile.CERRAR_SESION_EN_TODOS
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Ajustes: una sola pantalla (vuelta de diseño 3)
 *
 * Hasta la vuelta 3 el avatar abría una cuadrícula de ocho fichas y la configuración de verdad
 * estaba escondida en «Perfil». Ahora Ajustes es la tarjeta del perfil arriba y las secciones con
 * sus filas, en un orden fijo; «Perfil» dejó de ser una pantalla (`Screen.Profile` no existe, así
 * que el compilador ya prueba que nadie navega ahí; el destino SDUI `"profile"` lleva acá, ver
 * `SduiRendererTest`).
 *
 * Se monta alto (3200 dp) para que la `LazyColumn` componga todas las secciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3200dp-xhdpi")
class MasScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val perfilDelDueno = UserProfile(
        id = "u1", email = "jvillad1@gmail.com", name = "Juan Villada", avatarColor = "#7C6CF0",
        periodCutoffDay = 25, reminderLeadDays = 5,
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile = perfilDelDueno
        override suspend fun isScreenAdmin(): Boolean = false
    }

    private var navegoA: Screen? = null
    private var sesionesCerradas = 0

    private fun montar(ancho: WindowWidthClass? = null) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            val pantalla = @androidx.compose.runtime.Composable {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        MasScreen(onNavigate = { navegoA = it }, onLogout = { sesionesCerradas++ })
                    }
                }
            }
            if (ancho != null) CompositionLocalProvider(LocalWindowWidthClass provides ancho) { pantalla() }
            else pantalla()
        }
        composeRule.waitUntil(5_000) { hay("Cada día 25") }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocarFila(titulo: String) {
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText(titulo)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentroDe(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentroDe)

    /** Los títulos de las secciones, de arriba abajo. */
    private fun secciones(): List<Pair<String, Float>> =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, TAG_SECCION), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { textosDentroDe(it).first() to it.boundsInRoot.top }
            .sortedBy { it.second }

    private fun arribaDe(texto: String): Float =
        composeRule.onNode(hasText(texto), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `arriba el perfil y despues las secciones en su orden`() {
        montar()

        val tarjeta = composeRule.onNodeWithTag(TAG_TARJETA_DEL_PERFIL, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(hay("Juan Villada"), "la tarjeta dice el nombre")
        assertTrue(hay("Editar"))

        val vistas = secciones()
        // En Android de prueba no hay lector de huella ni es administrador: sin «Entrar» ni «Administración».
        assertEquals(listOf("Tu plata", "Movi", "Cómo se ve", "Avisos", "Tus datos", "Cuenta"), vistas.map { it.first })
        assertEquals(SECCIONES_DE_AJUSTES.filter { it in vistas.map { v -> v.first } }, vistas.map { it.first }, "el orden de SECCIONES_DE_AJUSTES")
        assertTrue(tarjeta.boundsInRoot.bottom <= vistas.first().second, "el perfil va arriba de todo")

        // Cada fila en su sección, en el orden pedido.
        val enOrden = listOf(
            "Inicio del mes", "Categorías", "Personas y comercios", "Captura del banco",
            "Movi AI", "Lo que Movi sabe de ti", "Editar inicio",
            "Igual que el teléfono", "Ocultar los saldos",
            "Avisarme antes de un vencimiento", "Resumen del período por correo",
            "Documentos", "Descargar tus datos", "Compartir",
            "Cambiar contraseña", CERRAR_SESION, CERRAR_SESION_EN_TODOS,
            BORRAR_MI_CUENTA,
        )
        val alturas = enOrden.map { arribaDe(it) }
        assertEquals(alturas.sorted(), alturas, "las filas no están en el orden de las secciones")
    }

    @Test
    fun `Cerrar sesion sigue cerrando la sesion`() {
        montar()
        tocarFila(CERRAR_SESION)
        assertEquals(1, sesionesCerradas)
    }

    @Test
    fun `cada fila lleva a su pantalla`() {
        montar()
        mapOf(
            "Categorías" to Screen.Categorias,
            "Personas y comercios" to Screen.Destinos(),
            "Captura del banco" to Screen.CapturaDelBanco,
            "Movi AI" to Screen.AIChat(),
            "Lo que Movi sabe de ti" to Screen.LoQueMoviSabe,
            "Editar inicio" to Screen.EditarInicio,
            "Documentos" to Screen.Documentos,
            "Compartir" to Screen.Compartir,
            BORRAR_MI_CUENTA to Screen.BorrarLaCuenta,
        ).forEach { (fila, destino) ->
            navegoA = null
            tocarFila(fila)
            assertEquals(destino, navegoA, "«$fila»")
        }
    }

    @Test
    fun `la configuracion que estaba en Perfil esta en Ajustes`() {
        montar()
        listOf(
            "Cambiar contraseña", "Inicio del mes", "Cómo se ve", "Ocultar los saldos", "Editar inicio",
            "Avisarme antes de un vencimiento", "Descargar tus datos", CERRAR_SESION, CERRAR_SESION_EN_TODOS,
        ).forEach { assertTrue(hay(it), "falta «$it»") }
        // Ya no hay una ficha «Perfil» que lleve a otra pantalla.
        assertTrue(composeRule.onAllNodes(hasText("Perfil"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `Borrar mi cuenta va al final, aparte, como una fila`() {
        montar()
        val borrar = arribaDe(BORRAR_MI_CUENTA)
        assertTrue(borrar > arribaDe(CERRAR_SESION_EN_TODOS), "después de todo lo demás")
        // Es una fila (se toca entera), no un botón lleno.
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText(BORRAR_MI_CUENTA)), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Ajustes no ofrece lo que ya es una pestana ni Metas ni Extractos`() {
        montar()
        listOf("Cuentas", "Cuadre de saldos", "Presupuestos", "Créditos", "Recurrentes", "Metas", "Extractos")
            .forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    /** Ola C: en pantalla ancha y mediana tampoco se esconde nada. */
    @Test
    fun `en pantalla ancha y mediana Ajustes muestra lo mismo`() {
        montar(WindowWidthClass.Expanded)
        assertEquals(6, secciones().size)
    }

    /** Ajustes no es pestaña: lleva flecha (vuelve a donde se estaba), no el avatar que la abrió. */
    @Test
    fun `Ajustes lleva flecha de volver`() {
        montar()
        composeRule.onNodeWithText(TITULO_DE_AJUSTES, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Volver", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * En Android —donde corren estas pruebas— la fila se llama «Captura del banco» (en la web y en
     * iOS, «Mensajes del banco»).
     */
    @Test
    fun `la captura del banco vive en Ajustes`() {
        montar()
        assertTrue(hay("Captura del banco"))
        composeRule.onNodeWithText("Mensajes del banco", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Primeros pasos es condicional (Ola B, tarea 7) ─────────────────────────

    private fun cuentaConMovimiento(eventCount: Int) = DashboardData(
        accounts = if (eventCount > 0) listOf(Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 1)) else emptyList(),
        summary = FinanceSummary(scope = Scope.SELF, balance = 0L, ingresos = 0L, egresos = 0L, eventCount = eventCount),
        upcoming = emptyList(),
        credits = emptyList(),
        cards = emptyList(),
    )

    @Test
    fun `Primeros pasos aparece mientras la guia del Inicio este incompleta`() {
        DashboardDataCache.data = cuentaConMovimiento(eventCount = 0)
        montar()
        assertTrue(hay("Primeros pasos"))
        tocarFila("Primeros pasos")
        assertEquals(Screen.PrimerosPasos, navegoA)
    }

    @Test
    fun `Primeros pasos no aparece si ya hay cuenta y movimiento`() {
        DashboardDataCache.data = cuentaConMovimiento(eventCount = 3)
        montar()
        assertTrue(!hay("Primeros pasos"))
    }

    @Test
    fun `sin datos del Inicio todavia, Primeros pasos tampoco aparece`() {
        montar()
        assertTrue(!hay("Primeros pasos"))
    }
}
