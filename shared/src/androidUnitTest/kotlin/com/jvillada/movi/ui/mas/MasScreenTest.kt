package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_ICONO_EN_CIRCULO
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import com.jvillada.movi.ui.primeruso.PASOS_DEL_REENVIO_EN_GMAIL
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
import com.jvillada.movi.ui.espacios.TITULO_DE_PERSONAS
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
 * Vuelta 38: es la pestaña **«Tú»**. Arriba del todo, después del perfil, «Lo tuyo» (Tu patrimonio,
 * Personas y Cuadre de saldos); debajo, las secciones de Ajustes tal cual.
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
        override suspend fun getDireccionDeCorreo(): DireccionDeCorreo =
            DireccionDeCorreo(token = "abc", direccion = "alertas+abc@movi.resend.app", configurado = true)
    }

    private var navegoA: Screen? = null
    private var colorDeAlerta: Color = Color.Unspecified
    private var colorDelTexto: Color = Color.Unspecified
    private var sesionesCerradas = 0

    private fun montar(ancho: WindowWidthClass? = null, esAndroid: Boolean = true) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            val pantalla = @androidx.compose.runtime.Composable {
                MoviTheme {
                    colorDeAlerta = Movi.colores.sale
                    colorDelTexto = Movi.colores.texto
                    Box(Modifier.fillMaxSize()) {
                        MasScreen(onNavigate = { navegoA = it }, onLogout = { sesionesCerradas++ }, esAndroid = esAndroid)
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
        // Vuelta 38: «Lo tuyo» primero, y después las de Ajustes.
        assertEquals(listOf(SECCION_LO_TUYO, "Tu plata", "Movi", "Cómo se ve", "Avisos", "Tus datos", "Cuenta"), vistas.map { it.first })
        assertEquals(SECCIONES_DE_AJUSTES.filter { it in vistas.map { v -> v.first } }, vistas.drop(1).map { it.first }, "el orden de SECCIONES_DE_AJUSTES")
        assertTrue(tarjeta.boundsInRoot.bottom <= vistas.first().second, "el perfil va arriba de todo")

        // Cada fila en su sección, en el orden pedido.
        val enOrden = listOf(
            FILA_TU_PATRIMONIO, TITULO_DE_PERSONAS, FILA_CUADRE_DE_SALDOS,
            "Inicio del período", "Ahorro de cada período", "Categorías", "Captura del banco",
            "Movi AI", "Lo que Movi sabe de ti", "Editar inicio",
            "Ocultar los saldos",
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
            "Cambiar contraseña", "Inicio del período", "Cómo se ve", "Ocultar los saldos", "Editar inicio",
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

    /** Vuelta de diseño 5: el título de «Borrar mi cuenta» va en el color de alerta del tema. */
    @Test
    fun `el titulo de Borrar mi cuenta usa el color de alerta del tema`() {
        montar()
        val nodo = composeRule.onNodeWithText(BORRAR_MI_CUENTA, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val color = resultados.single().layoutInput.style.color
        assertEquals(colorDeAlerta, color, "«Borrar mi cuenta» en `sale`")
        // Las demás filas siguen en el color del texto.
        val otra = composeRule.onNodeWithText(CERRAR_SESION_EN_TODOS, useUnmergedTree = true).fetchSemanticsNode()
        val deLaOtra = mutableListOf<TextLayoutResult>()
        otra.config[SemanticsActions.GetTextLayoutResult].action?.invoke(deLaOtra)
        assertEquals(colorDelTexto, deLaOtra.single().layoutInput.style.color)
    }

    @Test
    fun `Tu no repite lo que vive en Pagos ni Metas ni Extractos`() {
        montar()
        // Vuelta 38: Patrimonio, Personas y el cuadre SÍ están (son de Tú); lo de Pagos, no.
        listOf("Cuentas", "Presupuestos", "Créditos", "Recurrentes", "Metas", "Extractos")
            .forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    /** Ola C: en pantalla ancha y mediana tampoco se esconde nada. */
    @Test
    fun `en pantalla ancha y mediana Tu muestra lo mismo`() {
        montar(WindowWidthClass.Expanded)
        assertEquals(7, secciones().size)
    }

    /** Vuelta 38: «Tú» es la raíz de su pestaña: ni flecha ni avatar (sería ella misma). */
    @Test
    fun `Tu es la raiz de su pestana, sin flecha ni avatar`() {
        montar()
        composeRule.onNodeWithText(TITULO_DE_TU, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Volver", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(TITULO_DE_AJUSTES, useUnmergedTree = true).assertDoesNotExist()
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

    // ── Vuelta 31: más corta y con íconos ─────────────────────────────────────

    /** Los nodos de una fila de Ajustes que no llevan un ícono en su inicio. */
    private fun filasSinIcono(): List<String> {
        fun conIcono(nodo: SemanticsNode): Boolean =
            nodo.config.getOrNull(SemanticsProperties.TestTag) == TAG_ICONO_EN_CIRCULO || nodo.children.any(::conIcono)
        return composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, TAG_FILA), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filterNot(::conIcono)
            .map { textosDentroDe(it).firstOrNull().orEmpty() }
    }

    @Test
    fun `cada fila de Ajustes lleva su icono`() {
        DashboardDataCache.data = cuentaConMovimiento(eventCount = 0)
        montar()
        assertTrue(hay("Primeros pasos"))
        assertEquals(emptyList(), filasSinIcono(), "filas sin ícono")
    }

    @Test
    fun `en la web tambien cada fila lleva su icono`() {
        montar(esAndroid = false)
        composeRule.waitUntil(5_000) { hay("alertas+abc@movi.resend.app") }
        assertEquals(emptyList(), filasSinIcono(), "filas sin ícono")
    }

    /**
     * **Vuelta 29: no hay tema que elegir.** La opción 3 es clara y nada más: «Cómo se ve» deja solo
     * «Ocultar los saldos», sin la fila «Tema» ni sus tres opciones.
     */
    @Test
    fun `como se ve deja solo ocultar los saldos, sin elegir tema`() {
        montar()
        assertTrue(hay("Cómo se ve"))
        assertTrue(hay("Ocultar los saldos"))
        listOf("Tema", "Igual que el teléfono", "Claro", "Oscuro").forEach {
            composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `Personas y comercios vive en Espacios, no tambien en Ajustes`() {
        montar()
        composeRule.onNodeWithText("Personas y comercios", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `la fila dice Inicio del periodo y su hoja pregunta por el periodo`() {
        montar()
        composeRule.onNodeWithText("Inicio del mes", useUnmergedTree = true).assertDoesNotExist()
        tocarFila("Inicio del período")
        composeRule.waitUntil(5_000) { hay("¿Qué día empieza tu período?") }
    }

    @Test
    fun `en la web Tu plata ofrece recibir las alertas por correo con la direccion`() {
        montar(esAndroid = false)
        composeRule.waitUntil(5_000) { hay("alertas+abc@movi.resend.app") }
        assertTrue(hay(RECIBE_TUS_ALERTAS_POR_CORREO))
        assertTrue(arribaDe(RECIBE_TUS_ALERTAS_POR_CORREO) < arribaDe("Movi AI"), "va en «Tu plata»")

        tocarFila(RECIBE_TUS_ALERTAS_POR_CORREO)
        composeRule.onNodeWithTag(TAG_HOJA_DE_ALERTAS_POR_CORREO, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(hay("Copiar"))
        assertTrue(hay(PASOS_DEL_REENVIO_EN_GMAIL.first()))
    }

    @Test
    fun `en Android no ofrece el correo porque ahi se leen los mensajes`() {
        montar()
        composeRule.onNodeWithText(RECIBE_TUS_ALERTAS_POR_CORREO, useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Vuelta 38: «Lo tuyo», arriba de Tú ─────────────────────────────────────

    private val conPlata = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile = perfilDelDueno
        override suspend fun isScreenAdmin(): Boolean = false
        override suspend fun getAccounts(): List<Account> = listOf(
            Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 2_500_000L),
            Account(id = "a2", name = "Bancolombia", type = AccountType.CHECKING, balance = 1_000_000L),
        )
        override suspend fun getEspacios(): List<com.jvillada.movi.shared.model.Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): com.jvillada.movi.shared.model.DetalleDelEspacio {
            val gastos = listOf(
                com.jvillada.movi.shared.model.GastoDelEspacio(
                    id = "g1", espacioId = casa.id, pagadoPor = com.jvillada.movi.shared.model.MIEMBRO_YO, monto = 780_000L,
                    partes = mapOf(com.jvillada.movi.shared.model.MIEMBRO_YO to 390_000L, "dst_caro" to 390_000L),
                ),
            )
            val saldo = com.jvillada.movi.shared.model.saldoDelEspacio(casa, gastos, emptyList())
            return com.jvillada.movi.shared.model.DetalleDelEspacio(espacio = casa, saldo = saldo, delPeriodo = saldo, gastos = gastos)
        }
    }

    private val casa = com.jvillada.movi.shared.model.Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(
            com.jvillada.movi.shared.model.MiembroDelEspacio(com.jvillada.movi.shared.model.MIEMBRO_YO, "Juan"),
            com.jvillada.movi.shared.model.MiembroDelEspacio("dst_caro", "Caro"),
        ),
    )

    @Test
    fun `Tu muestra el patrimonio neto, el saldo con las personas y el cuadre`() {
        Repositories.sustitutoDePrueba = conPlata
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { MasScreen(onNavigate = { navegoA = it }) } }
        }
        composeRule.waitUntil(5_000) { hay("Caro te debe") && hay("3.500.000") }

        assertTrue(hay(FILA_TU_PATRIMONIO))
        // La MISMA cifra de Patrimonio: `patrimonioDe` de las dos cuentas.
        assertTrue(hay("3.500.000"), "el neto de las dos cuentas")
        assertTrue(hay(TITULO_DE_PERSONAS))
        assertTrue(hay("390.000"), "lo que Caro debe para quedar 50/50")
        assertTrue(hay(FILA_CUADRE_DE_SALDOS))
        // Y lo tuyo va antes que lo que era Ajustes.
        assertTrue(arribaDe(FILA_CUADRE_DE_SALDOS) < arribaDe("Inicio del período"))
    }

    @Test
    fun `cada fila de lo tuyo abre su pantalla`() {
        montar()
        listOf(
            FILA_TU_PATRIMONIO to Screen.Accounts,
            TITULO_DE_PERSONAS to Screen.Espacios,
            FILA_CUADRE_DE_SALDOS to Screen.CuadreDeSaldos,
        ).forEach { (fila, destino) ->
            navegoA = null
            tocarFila(fila)
            assertEquals(destino, navegoA, "«$fila»")
        }
    }
}
