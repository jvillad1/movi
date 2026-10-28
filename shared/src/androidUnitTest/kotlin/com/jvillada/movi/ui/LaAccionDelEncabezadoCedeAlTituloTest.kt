package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.ai.AIChatScreen
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import com.jvillada.movi.ui.components.ds.AccionDelEncabezado
import com.jvillada.movi.ui.components.ds.Encabezado
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO
import com.jvillada.movi.ui.components.ds.anchoDeLaAccionConTexto
import com.jvillada.movi.ui.components.ds.laAccionVaConTexto
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.destinos.DestinosScreen
import com.jvillada.movi.ui.documentos.DocumentosScreen
import com.jvillada.movi.ui.espacios.EspaciosScreen
import org.junit.After
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
 * # Un título de pantalla nunca se corta (vuelta de diseño 8)
 *
 * «Personas y comerc…»: la acción «+ Nueva» le comía el ancho al título del encabezado. Ahora el
 * título manda: si título y acción con texto no caben, la acción queda en el ícono (con su nombre
 * para el lector de pantalla, el mismo tag y 48 de toque); si ni así cabe, el título baja a dos
 * renglones. Se mide con el motor de texto real (`NATIVE`, sdk 34) a 360 dp con la letra a ×1,3,
 * en cada pantalla que tiene acción de encabezado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xhdpi")
class LaAccionDelEncabezadoCedeAlTituloTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val hipoteca = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)
    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> =
            listOf(Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L), hipoteca)
        override suspend fun getCredits(): List<CreditSummary> = listOf(CreditSummary(account = hipoteca, terms = null, paidPct = null))
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> =
            listOf(DestinoConocido(id = "d1", nombre = "Caro", numero = "55500000756"))
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25, reminderLeadDays = 5)
        override suspend fun isScreenAdmin(): Boolean = false
        override suspend fun getBudgets(): List<Budget> = listOf(Budget("Mercado", 1_000_000L))
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = mapOf("Mercado" to 250_000L))
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDocuments(): List<Documento> = emptyList()
        override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(
            listOf(ChatMessage(ChatRole.USER, "¿Cómo voy?"), ChatMessage(ChatRole.ASSISTANT, "Vas bien.")),
        )
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = DetalleDelEspacio(
            espacio = casa,
            saldo = saldoDelEspacio(casa, emptyList(), emptyList()),
            delPeriodo = saldoDelEspacio(casa, emptyList(), emptyList()),
            gastos = emptyList(),
        )
    }

    private fun montar(escala: Float = 1.3f, contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                    Box(Modifier.fillMaxSize()) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun textosDentro(nodo: SemanticsNode): List<String> =
        listOfNotNull(nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }) +
            nodo.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            nodo.children.flatMap(::textosDentro)

    private fun contiene(afuera: Rect, adentro: Rect): Boolean =
        adentro.left >= afuera.left - 0.5f && adentro.right <= afuera.right + 0.5f &&
            adentro.top >= afuera.top - 0.5f && adentro.bottom <= afuera.bottom + 0.5f

    /** El título del encabezado: el nodo con `heading()` dentro de [TAG_ENCABEZADO]. */
    private fun tituloDelEncabezado(): SemanticsNode =
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasAnyAncestor(hasTestTag(TAG_ENCABEZADO)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().first()

    private fun medidaDe(nodo: SemanticsNode): TextLayoutResult {
        val medidas = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        return medidas.single()
    }

    /**
     * Lo que se exige en cada pantalla: el título entero (ningún renglón con «…» ni más ancho que
     * su caja) y la acción tocable, dentro del encabezado y de la pantalla, con su nombre.
     */
    private fun revisar(titulo: String, accion: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            nodos(TAG_ACCION_DEL_ENCABEZADO).any { accion in textosDentro(it) }
        }
        composeRule.waitForIdle()
        val nodoDelTitulo = tituloDelEncabezado()
        assertEquals(titulo, nodoDelTitulo.config[SemanticsProperties.Text].joinToString("") { it.text })
        val medida = medidaDe(nodoDelTitulo)
        assertTrue(medida.lineCount <= 2, "«$titulo» en más de dos renglones")
        (0 until medida.lineCount).forEach { renglon ->
            assertFalse(medida.isLineEllipsized(renglon), "«$titulo» sale cortado a 360 dp con la letra a ×1,3")
            assertTrue(
                medida.getLineRight(renglon) <= medida.size.width + 1f,
                "«$titulo» se sale de su caja: ${medida.getLineRight(renglon)} de ${medida.size.width} px",
            )
        }
        val nodoDeLaAccion = nodos(TAG_ACCION_DEL_ENCABEZADO).single()
        assertTrue(SemanticsActions.OnClick in nodoDeLaAccion.config, "$titulo: la acción se puede tocar")
        val encabezado = nodos(TAG_ENCABEZADO).first()
        assertTrue(contiene(encabezado.boundsInRoot, nodoDeLaAccion.boundsInRoot), "$titulo: la acción va dentro del encabezado")
        val densidad = composeRule.density.density
        assertTrue(
            nodoDeLaAccion.size.height / densidad >= Medida.altoChico.value - 0.5f,
            "$titulo: la acción mide ${nodoDeLaAccion.size.height / densidad} dp de alto",
        )
    }

    /** El subtítulo del encabezado, entero: ningún renglón con «…» ni más ancho que su caja. */
    private fun subtituloEntero(subtitulo: String) {
        val nodo = composeRule.onAllNodes(
            hasText(subtitulo) and hasAnyAncestor(hasTestTag(TAG_ENCABEZADO)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single()
        val medida = medidaDe(nodo)
        (0 until medida.lineCount).forEach { renglon ->
            assertFalse(medida.isLineEllipsized(renglon), "«$subtitulo» sale cortado a 360 dp con la letra a ×1,3")
            assertTrue(medida.getLineRight(renglon) <= medida.size.width + 1f, "«$subtitulo» se sale de su caja")
        }
    }

    // ── Las pantallas con acción de encabezado ─────────────────────────────────────────

    @Test
    fun `Patrimonio`() {
        Repositories.sustitutoDePrueba = repo
        montar { AccountsScreen(onNavigate = {}) }
        revisar("Patrimonio", "Nueva cuenta")
    }

    @Test
    fun `Creditos`() {
        Repositories.sustitutoDePrueba = repo
        montar { CreditosScreen(onNavigate = {}) }
        revisar("Créditos", "Nuevo crédito")
    }

    @Test
    fun `Personas y comercios`() {
        Repositories.sustitutoDePrueba = repo
        montar { DestinosScreen(onNavigate = {}) }
        revisar("Personas y comercios", "Nueva")
    }

    @Test
    fun `Presupuestos`() {
        Repositories.sustitutoDePrueba = repo
        montar { PresupuestosScreen(onNavigate = {}) }
        revisar("Presupuestos", "Nuevo")
    }

    @Test
    fun `Documentos`() {
        Repositories.sustitutoDePrueba = repo
        montar { DocumentosScreen(onNavigate = {}) }
        revisar("Documentos", "Subir archivo")
    }

    @Test
    fun `Movi AI`() {
        Repositories.sustitutoDePrueba = repo
        montar { AIChatScreen(onNavigate = {}) }
        revisar("Movi AI", "Nueva conversación")
        // Vuelta de diseño 12: «Conoce tus fi…». El subtítulo también manda sobre la acción.
        subtituloEntero("Conoce tus finanzas")
    }

    @Test
    fun `Espacios`() {
        Repositories.sustitutoDePrueba = repo
        montar { EspaciosScreen(onNavigate = {}) }
        revisar("Grupos", "Nuevo grupo")
    }

    // ── La regla, en el componente ─────────────────────────────────────────────────────

    @Test
    fun `con lugar, la accion conserva su texto`() {
        montar(escala = 1f) {
            Encabezado("Créditos", onVolver = {}, accion = AccionDeEncabezado("Nuevo crédito", Icons.Rounded.Add, onClick = {}))
        }
        val accion = nodos(TAG_ACCION_DEL_ENCABEZADO).single()
        // El texto es un nodo de texto, no solo una descripción: se ve.
        assertTrue(
            accion.children.any { hijo -> hijo.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Nuevo crédito" } == true } ||
                accion.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Nuevo crédito" } == true,
            "«Nuevo crédito» se ve: ${textosDentro(accion)}",
        )
    }

    /**
     * **Vuelta 29: en una pestaña la acción va siempre en su botón redondo**, con su nombre para el
     * lector de pantalla y 48 de toque. Patrimonio quedaba con «+» y Espacios con «+ Nuevo espacio»
     * según cupiera el título; ahora las dos van igual.
     */
    @Test
    fun `en una pestana la accion va en su boton redondo aunque haya lugar`() {
        montar(escala = 1f) {
            Encabezado("Espacios", inicio = {}, accion = AccionDeEncabezado("Nuevo espacio", Icons.Rounded.Add, onClick = {}))
        }
        val accion = nodos(TAG_ACCION_DEL_ENCABEZADO).single()
        assertTrue(
            accion.children.none { hijo -> hijo.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Nuevo espacio" } == true },
            "en una pestaña la acción no lleva texto",
        )
        assertTrue("Nuevo espacio" in textosDentro(accion), "la acción conserva su nombre: ${textosDentro(accion)}")
        val densidad = composeRule.density.density
        assertEquals(Medida.objetivoTactil.value, accion.size.width / densidad, 0.5f)
    }

    @Test
    fun `sin lugar, la accion queda en el icono con su nombre y 48 de toque`() {
        var tocada = false
        montar {
            Encabezado(
                "Personas y comercios",
                onVolver = {},
                accion = AccionDeEncabezado("Nueva", Icons.Rounded.Add, onClick = { tocada = true }),
            )
        }
        val accion = nodos(TAG_ACCION_DEL_ENCABEZADO).single()
        assertEquals(listOf("Nueva"), textosDentro(accion), "solo el ícono, con su nombre para el lector de pantalla")
        val densidad = composeRule.density.density
        assertTrue(abs(accion.size.width / densidad - Medida.objetivoTactil.value) <= 0.5f, "48 de ancho")
        assertTrue(abs(accion.size.height / densidad - Medida.objetivoTactil.value) <= 0.5f, "48 de alto")
        composeRule.onAllNodesWithTag(TAG_ACCION_DEL_ENCABEZADO, useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(tocada, "el ícono hace lo mismo que el botón con texto")
        val medida = medidaDe(tituloDelEncabezado())
        assertFalse((0 until medida.lineCount).any { medida.isLineEllipsized(it) }, "el título entero")
    }

    @Test
    fun `ni con el icono cabe, el titulo baja a dos renglones sin cortarse`() {
        val largo = "Lo que Movi sabe de ti y de tu plata"
        montar {
            Encabezado(largo, onVolver = {}, accion = AccionDeEncabezado("Nueva", Icons.Rounded.Add, onClick = {}))
        }
        val medida = medidaDe(tituloDelEncabezado())
        assertEquals(2, medida.lineCount, "el título largo baja a un segundo renglón")
        assertFalse((0 until medida.lineCount).any { medida.isLineEllipsized(it) }, "sin «…»")
        assertTrue(SemanticsActions.OnClick in nodos(TAG_ACCION_DEL_ENCABEZADO).single().config)
    }

    @Test
    fun `el ancho calculado de la accion es el del boton de verdad`() {
        var calculado = -1
        val accion = AccionDeEncabezado("Nueva conversación", Icons.Rounded.Add, onClick = {})
        montar {
            val medidor = rememberTextMeasurer()
            calculado = anchoDeLaAccionConTexto(
                medidor,
                accion.texto,
                Movi.textos.cuerpoFuerte,
                LocalDensity.current,
            )
            Box { AccionDelEncabezado(accion) }
        }
        val real = nodos(TAG_ACCION_DEL_ENCABEZADO).single().size.width
        assertTrue(abs(real - calculado) <= 2, "calculado $calculado px, el botón mide $real px")
    }

    @Test
    fun `la regla de quien cede`() {
        assertTrue(laAccionVaConTexto(anchoDelTitulo = 100, anchoDeLaAccion = 80, separacion = 12, disponible = 192))
        assertFalse(laAccionVaConTexto(anchoDelTitulo = 100, anchoDeLaAccion = 80, separacion = 12, disponible = 191))
        // Título > subtítulo > texto de la acción (vuelta de diseño 12).
        assertTrue(laAccionVaConTexto(anchoDelTitulo = 60, anchoDeLaAccion = 80, separacion = 12, disponible = 192, anchoDelSubtitulo = 100))
        assertFalse(laAccionVaConTexto(anchoDelTitulo = 60, anchoDeLaAccion = 80, separacion = 12, disponible = 192, anchoDelSubtitulo = 101))
    }

    @Test
    fun `un subtitulo que no cabe al lado del texto de la accion la hace ceder`() {
        montar {
            Encabezado(
                "Movi AI",
                subtitulo = "Conoce tus finanzas",
                onVolver = {},
                accion = AccionDeEncabezado("Nueva conversación", Icons.Rounded.Add, onClick = {}),
            )
        }
        assertEquals(listOf("Nueva conversación"), textosDentro(nodos(TAG_ACCION_DEL_ENCABEZADO).single()), "la acción, en su ícono")
        subtituloEntero("Conoce tus finanzas")
    }

    @Test
    fun `ni con el icono cabe, el subtitulo baja a dos renglones sin cortarse`() {
        val largo = "Lo que Movi sabe de tus cuentas y tus deudas"
        montar {
            Encabezado("Movi AI", subtitulo = largo, onVolver = {}, accion = AccionDeEncabezado("Nueva", Icons.Rounded.Add, onClick = {}))
        }
        val nodo = composeRule.onAllNodes(hasText(largo) and hasAnyAncestor(hasTestTag(TAG_ENCABEZADO)), useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        assertEquals(2, medidaDe(nodo).lineCount, "el subtítulo largo baja a un segundo renglón")
        subtituloEntero(largo)
    }
}
