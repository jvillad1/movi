package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import androidx.compose.ui.graphics.Color
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.coloresDelBoton
import com.jvillada.movi.ui.components.ds.coloresDelBotonApagado
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.auth.RegisterScreen
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_MARCA
import com.jvillada.movi.ui.components.ds.TAG_SELECTOR_DE_DIA_DEL_MES
import com.jvillada.movi.ui.components.ds.tagDelDiaDelMes
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UpcomingPayment
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La guía «Primer uso», montada de verdad con un repositorio de prueba: cuándo aparece, que cada
 * paso se salta, y que cada paso guarda con lo de siempre (la creación de cuenta de la hoja, el
 * «Inicio del mes» de Perfil, la regla recurrente de Recurrentes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class PrimerUsoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val perfilNuevo = UserProfile(id = "u1", email = "papa@movi.test", name = "Papá", avatarColor = "#7C6CF0")

    /** El repositorio de la guía: anota lo que se escribe; lo demás explota con su nombre. */
    private open inner class RepoDeLaGuia : RepositorioDePrueba() {
        val marcas = mutableListOf<Unit>()
        val perfiles = mutableListOf<UpdateProfileRequest>()
        val cuentasCreadas = mutableListOf<Account>()
        val eventos = mutableListOf<FinancialEvent>()
        val reglas = mutableListOf<RecurringRule>()

        override suspend fun getUserProfile(): UserProfile = perfilNuevo
        override suspend fun marcarPrimerUso(): UserProfile { marcas += Unit; return perfilNuevo.copy(primerUsoEn = 1L) }
        override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
            perfiles += request
            return perfilNuevo.copy(periodCutoffDay = request.periodCutoffDay ?: 1)
        }
        override suspend fun createAccount(account: Account): Account { cuentasCreadas += account; return account }
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent { eventos += event; return event }
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            reglas += rule
            return rule.copy(id = "r${reglas.size}")
        }
        override suspend fun getDireccionDeCorreo(): DireccionDeCorreo =
            DireccionDeCorreo(token = "abc", direccion = "alertas+abc@movi.resend.app", configurado = true)
    }

    private val navegaciones = mutableListOf<Screen>()

    private fun montar(
        repo: RepositorioDePrueba,
        paso: PasoDePrimerUso = PasoDePrimerUso.BIENVENIDA,
        modo: ModoDeCaptura = ModoDeCaptura.SUBIR_UN_EXTRACTO,
    ) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            ConClaseDeAncho {
                Box(Modifier.fillMaxSize()) {
                    PrimerUsoScreen(onNavigate = { navegaciones += it }, pasoInicial = paso, modoDeCaptura = modo)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun avanzar() {
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()
    }

    /** «Saltar este paso»: el botón de texto, no el principal. */
    private fun saltar() {
        composeRule.onNodeWithTag(TAG_SALTAR_EN_LA_GUIA).assertTextContains(SALTAR_ESTE_PASO)
        composeRule.onNodeWithTag(TAG_SALTAR_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()
    }

    /** El principal dice «Siguiente» y está apagado: no hay nada que guardar todavía. */
    private fun principalApagado() {
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente")
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsNotEnabled()
    }

    /** Vuelta 87: en los pasos opcionales, «Siguiente» avanza aunque no haya nada que guardar. */
    private fun principalHabilitado() {
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente")
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsEnabled()
    }

    private fun sinSaltar(): Boolean =
        composeRule.onAllNodesWithTag(TAG_SALTAR_EN_LA_GUIA).fetchSemanticsNodes().isEmpty()

    private fun seVe(texto: String, parcial: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = parcial).fetchSemanticsNodes().isNotEmpty()

    // ── Cuándo aparece ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `crear la cuenta abre la guia`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun register(request: RegisterRequest): AuthResponse =
                AuthResponse(token = "t", userId = "u1", name = request.name, email = request.email)
        }
        composeRule.setContent { MoviTheme { RegisterScreen(onNavigate = { navegaciones += it }) } }
        val campos = composeRule.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("Papá")
        campos[1].performTextInput("papa@movi.test")
        campos[2].performTextInput("una-contrasena-larga")
        composeRule.onAllNodesWithText("Crear cuenta").filter(hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Screen>(Screen.PrimerUso()), navegaciones)
        assertTrue(GuiaDePrimerUso.enCurso, "la guía queda en curso: la tarjeta de Primeros pasos no compite")
    }

    @Test
    fun `se abre para quien no tiene cuentas de dinero y no para quien ya tiene`() = runBlocking {
        val sinCuentas = object : RepositorioDePrueba() {
            override suspend fun getUserProfile() = perfilNuevo
            override suspend fun getAccounts(): List<Account> = emptyList()
        }
        assertTrue(revisarLaGuiaDePrimerUso(sinCuentas))
        // Una vez por sesión: llegar otra vez al Inicio no la vuelve a abrir.
        assertFalse(revisarLaGuiaDePrimerUso(sinCuentas))

        GuiaDePrimerUso.olvidar()
        val conCuentas = object : RepositorioDePrueba() {
            override suspend fun getUserProfile() = perfilNuevo
            override suspend fun getAccounts() = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 1_000_000))
        }
        assertFalse(revisarLaGuiaDePrimerUso(conCuentas), "el dueño, con sus cuentas, no la ve")

        GuiaDePrimerUso.olvidar()
        val yaLaTermino = object : RepositorioDePrueba() {
            override suspend fun getUserProfile() = perfilNuevo.copy(primerUsoEn = 1L)
            override suspend fun getAccounts(): List<Account> = emptyList()
        }
        assertFalse(revisarLaGuiaDePrimerUso(yaLaTermino), "terminada o descartada en otro aparato")

        GuiaDePrimerUso.olvidar()
        val sinRed = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = error("sin red")
        }
        assertFalse(revisarLaGuiaDePrimerUso(sinRed))
        assertFalse(GuiaDePrimerUso.revisada, "una lectura caída no decide: se vuelve a preguntar")
    }

    // ── Cada paso se salta ─────────────────────────────────────────────────────────────────────

    @Test
    fun `bienvenida y periodo se saltan, y sin cuentas Ahora no sale al Inicio`() {
        val repo = RepoDeLaGuia()
        montar(repo)
        assertTrue(seVe("Te propone, pero nunca hace nada sin ti"))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Empezar")
        assertTrue(sinSaltar(), "la bienvenida no tiene nada que saltar")
        avanzar()

        // El principal dice «Siguiente» y se apaga sin un día elegido; saltar es el botón de texto.
        assertTrue(seVe("Paso 2 de 6"))
        principalApagado()
        saltar()
        assertTrue(repo.perfiles.isEmpty(), "saltar el período no guarda nada")

        assertTrue(seVe("Paso 3 de 6"))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsNotEnabled()
        // Vuelta 54: «Siguiente» apagado nunca va sin salida: las cuentas también se saltan.
        assertFalse(sinSaltar(), "las cuentas también tienen «Saltar este paso»")
        composeRule.onNodeWithText(AHORA_NO).performClick()
        composeRule.waitForIdle()

        assertEquals(1, repo.marcas.size, "«Ahora no» marca la guía descartada")
        assertEquals(listOf<Screen>(Screen.Dashboard), navegaciones)
        assertFalse(GuiaDePrimerUso.enCurso)
    }

    @Test
    fun `captura y pagos de cada mes se saltan y el final lleva al Inicio marcando la guia`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.CAPTURA)
        principalHabilitado()
        saltar()
        assertTrue(seVe("Paso 5 de 6"))
        principalHabilitado()
        saltar()

        assertTrue(seVe("Paso 6 de 6"))
        assertTrue(sinSaltar(), "el final no tiene nada que saltar")
        assertTrue(seVe("Ninguna todavía"), "el resumen dice que no quedó ninguna cuenta")
        // Vuelta 75: sin cuentas, el final no inventa una cifra.
        composeRule.waitUntil(5_000) { seVe(YA_PUEDES_EMPEZAR) }
        assertTrue(seVe("Primeros pasos", parcial = true), "lo saltado queda en Primeros pasos")
        assertFalse(seVe(AHORA_NO), "en el final ya no hay «Ahora no»")
        assertTrue(repo.reglas.isEmpty())

        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Ir a Inicio")
        avanzar()
        assertEquals(1, repo.marcas.size, "terminarla la marca en el server")
        assertEquals(listOf<Screen>(Screen.Dashboard), navegaciones)
        assertFalse(GuiaDePrimerUso.enCurso)
        assertTrue(GuiaDePrimerUso.revisada)
    }

    @Test
    fun `Atras vuelve al paso anterior`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.RECURRENTES)
        composeRule.onNodeWithText("Atrás").performClick()
        composeRule.waitForIdle()
        assertTrue(seVe("Paso 4 de 6"))
    }

    // ── Cada paso guarda con lo de siempre ─────────────────────────────────────────────────────

    @Test
    fun `el periodo guarda con lo mismo que Perfil`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        // Vuelta 54: la vista previa de la hoja de Tú, con el rango, no «Hoy estarías en noviembre».
        assertTrue(seVe("Tu período: del", parcial = true), "la consecuencia se ve en vivo")
        assertFalse(seVe("Hoy estarías en", parcial = true))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente")
        avanzar()

        assertEquals(
            listOf(UpdateProfileRequest(periodCutoffDay = 25, periodWeekendRule = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR.name)),
            repo.perfiles,
        )
        assertTrue(seVe("Paso 3 de 6"))
    }

    @Test
    fun `sin la casilla del dia de pago el periodo arranca ese mismo dia`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("15").performClick()
        composeRule.onNodeWithText("Es el día que me pagan").performClick()
        composeRule.waitForIdle()
        avanzar()
        assertEquals(SiElCorteCaeEnFinDeSemana.ESE_DIA.name, repo.perfiles.single().periodWeekendRule)
    }

    @Test
    fun `el paso de cuentas crea la cuenta con la creacion de siempre`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.CUENTAS)
        composeRule.onNodeWithText("Bancolombia").performClick()
        composeRule.waitForIdle()
        // El nombre lo propuso el banco con el tipo (vuelta de diseño 16); el saldo es el segundo campo.
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("1500000")
        // Vuelta 54: «Agregar cuenta» va en el pie de la guía, siempre a la vista.
        composeRule.onNodeWithTag(TAG_AGREGAR_CUENTA_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()

        val creada = repo.cuentasCreadas.single()
        assertEquals("Bancolombia Ahorros", creada.name)
        assertEquals(AccountType.SAVINGS, creada.type)
        assertEquals(0L, creada.balance, "la cuenta se crea en \$0, como en la hoja de siempre")
        val apertura = repo.eventos.single()
        assertEquals(1_500_000L, apertura.amount, "el saldo de hoy entra como la apertura de siempre")
        assertEquals(creada.id, apertura.accountId)
        assertEquals(TransactionType.INCOME, apertura.type)

        assertTrue(seVe("Tus cuentas", parcial = true))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsEnabled()
        avanzar()
        assertTrue(seVe("Paso 4 de 6"))
    }

    @Test
    fun `un pago de cada mes se guarda como la regla recurrente de siempre`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.RECURRENTES)
        composeRule.onNodeWithText("Arriendo").performClick()
        composeRule.waitForIdle()
        val campos = composeRule.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("1200000")
        // Vuelta 75: el día se elige en el selector de la vuelta 72, no se escribe.
        composeRule.onNodeWithTag(TAG_SELECTOR_DE_DIA_DEL_MES).assertExists()
        composeRule.onNodeWithTag(tagDelDiaDelMes(5)).performScrollTo().performClick()
        composeRule.onNodeWithText("Guardar").performScrollTo().performClick()
        composeRule.waitForIdle()

        val regla = repo.reglas.single()
        assertEquals("Arriendo", regla.name)
        assertEquals("Vivienda", regla.category)
        assertEquals(1_200_000L, regla.amount)
        assertEquals(5, regla.dayOfMonth)
        assertEquals(TransactionType.EXPENSE, regla.type)
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente")
    }

    // ── Revisión de la vuelta 54: lo escrito no se pierde ──────────────────────────────────────

    /**
     * Un botón de una hoja: el velo y el panel son `clickable` y funden lo de adentro, así que el toque va
     * por la acción semántica del botón (como en `CrearUnaCuentaEsIgualEnTodosLadosTest`).
     */
    private fun tocarEnLaHoja(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun escribirElArriendo(dia: String = "5") {
        composeRule.onNodeWithText("Arriendo").performClick()
        composeRule.waitForIdle()
        val campos = composeRule.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("1200000")
        if (dia.isNotEmpty()) composeRule.onNodeWithTag(tagDelDiaDelMes(dia.toInt())).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `con el arriendo escrito el principal es Guardar y seguir, y lo guarda`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.RECURRENTES)
        escribirElArriendo()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains(GUARDAR_Y_SEGUIR).assertIsEnabled()
        avanzar()

        val regla = repo.reglas.single()
        assertEquals("Arriendo", regla.name)
        assertEquals(1_200_000L, regla.amount)
        assertEquals(5, regla.dayOfMonth)
        assertTrue(seVe("Paso 6 de 6"))
        assertTrue(seVe("1 pago"), "el resumen cuenta el arriendo")
    }

    @Test
    fun `con el arriendo a medias el pie dice que falta`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.RECURRENTES)
        escribirElArriendo(dia = "")
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains(GUARDAR_Y_SEGUIR).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_RAZON_DEL_PIE).assertTextContains("Falta el día del arriendo")
    }

    @Test
    fun `saltar con el arriendo escrito pregunta antes de descartarlo`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.RECURRENTES)
        escribirElArriendo()
        saltar()
        assertTrue(seVe("¿Descartar el arriendo que escribiste?"))
        assertTrue(seVe("Paso 5 de 6"), "todavía no saltó")

        // «Cancelar» lo deja como estaba: el principal sigue guardándolo.
        tocarEnLaHoja("Cancelar")
        composeRule.waitForIdle()
        assertFalse(seVe("¿Descartar el arriendo que escribiste?"))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains(GUARDAR_Y_SEGUIR)

        saltar()
        tocarEnLaHoja("Descartar")
        composeRule.waitForIdle()
        val textos = textosDe(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(seVe("Paso 6 de 6"), "TEXTOS: $textos")
        assertTrue(repo.reglas.isEmpty(), "descartado, no se guardó")
        assertTrue(seVe("Ninguno todavía"), "TEXTOS: $textos")
    }

    @Test
    fun `con la cuenta escrita el principal la guarda y sigue, y saltar pregunta`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.CUENTAS)
        composeRule.onNodeWithText("Bancolombia").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("1500000")
        composeRule.waitForIdle()

        saltar()
        assertTrue(seVe("¿Descartar la cuenta que escribiste?"))
        tocarEnLaHoja("Cancelar")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains(GUARDAR_Y_SEGUIR).assertIsEnabled()
        avanzar()
        assertEquals("Bancolombia Ahorros", repo.cuentasCreadas.single().name)
        assertEquals(1_500_000L, repo.eventos.single().amount)
        assertTrue(seVe("Paso 4 de 6"))
    }

    // ── Que Movi se entere solo ────────────────────────────────────────────────────────────────

    @Test
    fun `en la web el paso de captura sube un extracto, y el reenvio va debajo si el server lo tiene`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.CAPTURA, modo = ModoDeCaptura.SUBIR_UN_EXTRACTO)
        assertTrue(seVe("Paso 4 de 6"))
        assertTrue(seVe("Sube un extracto"))
        composeRule.onNodeWithTag(TAG_SUBIR_EXTRACTO_EN_LA_GUIA).assertExists()
        assertTrue(seVe("alertas+abc@movi.resend.app"))
        assertFalse(seVe("Captura en este teléfono"))

        composeRule.onNodeWithText("Copiar").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente")
    }

    @Test
    fun `en Android el paso de captura es la seccion de permisos de Captura del banco`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.CAPTURA, modo = ModoDeCaptura.PERMISOS_DEL_TELEFONO)
        assertTrue(seVe("Captura en este teléfono"))
        assertTrue(seVe("Permiso de SMS"))
        assertFalse(seVe("Copiar"))
        assertFalse(seVe(SUBIR_UN_EXTRACTO_EN_LA_GUIA))
    }

    @Test
    fun `sin la direccion configurada el paso sigue teniendo que hacer y se puede saltar`() {
        val repo = object : RepoDeLaGuia() {
            override suspend fun getDireccionDeCorreo() = DireccionDeCorreo(token = "abc", configurado = false)
        }
        montar(repo, paso = PasoDePrimerUso.CAPTURA)
        // Vuelta 54: ya no «El reenvío por correo todavía no está disponible»: subir un extracto sí existe.
        assertFalse(seVe("todavía no está disponible", parcial = true))
        composeRule.onNodeWithTag(TAG_SUBIR_EXTRACTO_EN_LA_GUIA).assertIsEnabled()
        assertNull(composeRule.onAllNodesWithText("Copiar").fetchSemanticsNodes().firstOrNull())
        principalHabilitado()
        saltar()
        assertTrue(seVe("Paso 5 de 6"))
    }

    // ── Vuelta 54: la lista única ──────────────────────────────────────────────────────────────

    @Test
    fun `abierta desde la fila del Inicio arranca en el paso pedido y el resumen cuenta lo que ya habia`() {
        val repo = object : RepoDeLaGuia() {
            override suspend fun getUserProfile(): UserProfile = perfilNuevo.copy(periodCutoffDay = 25)
            override suspend fun getAccounts() = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 1_000_000))
        }
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            ConClaseDeAncho {
                Box(Modifier.fillMaxSize()) {
                    PrimerUsoScreen(
                        onNavigate = { navegaciones += it },
                        modoDeCaptura = ModoDeCaptura.SUBIR_UN_EXTRACTO,
                        desde = com.jvillada.movi.shared.model.PrimerPaso.FIJOS,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(seVe("Paso 5 de 6"))
        assertTrue(seVe("Lo que pagas cada mes"))
        saltar()
        // El resumen dice lo mismo que la lista única: el período y la cuenta ya estaban.
        assertTrue(seVe("1 cuenta"))
        assertTrue(seVe("Cada día 25", parcial = true))
        assertTrue(seVe("Sin subir todavía"))
        assertTrue(seVe("Ninguno todavía"))
        assertFalse(seVe("Activo en este teléfono"))
    }

    // ── Vuelta de diseño 3 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `la bienvenida lleva la marca de Movi y el titulo grande`() {
        montar(RepoDeLaGuia())
        composeRule.onNodeWithTag(TAG_MARCA_DE_MOVI).assertExists()
        assertEquals(1, composeRule.onAllNodesWithText(TITULO_DE_LA_BIENVENIDA).fetchSemanticsNodes().size, "un solo título")
    }

    @Test
    fun `la bienvenida queda centrada en el espacio entre la barra y los botones`() {
        montar(RepoDeLaGuia())
        val marca = composeRule.onNodeWithTag(TAG_MARCA_DE_MOVI).fetchSemanticsNode().boundsInRoot
        val boton = composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        val nota = composeRule.onNodeWithText("Son unos pasos cortos", substring = true).fetchSemanticsNode().boundsInRoot
        val encabezado = composeRule.onNodeWithText("Paso 1 de 6").fetchSemanticsNode().boundsInRoot
        // Arriba de la marca y debajo de la nota queda el mismo aire (± unos dp por el redondeo).
        val arriba = marca.top - encabezado.bottom
        val abajo = boton.top - nota.bottom
        assertTrue(arriba > 0f && abajo > 0f)
        assertTrue(kotlin.math.abs(arriba - abajo) < arriba * 0.5f, "arriba $arriba px, abajo $abajo px: el bloque no está centrado")
    }

    @Test
    fun `el periodo con un dia elegido sigue con Siguiente y saltarlo no guarda nada`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsEnabled()
        saltar()
        assertTrue(repo.perfiles.isEmpty(), "«Saltar este paso» no guarda el día elegido")
        assertTrue(seVe("Paso 3 de 6"))
    }

    // ── Vuelta de diseño 5 ─────────────────────────────────────────────────────────────────────

    /**
     * «Saltar este paso» es un botón de texto chico y centrado debajo de «Atrás» y «Siguiente»: más
     * bajo y más angosto que la fila de botones, para que «Siguiente» sea la única acción con peso.
     */
    @Test
    fun `Saltar este paso es un boton de texto chico y centrado`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.PERIODO)
        val saltar = composeRule.onNodeWithTag(TAG_SALTAR_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        val avanzar = composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        val ancho = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val densidad = composeRule.density.density
        assertEquals(Medida.altoChico.value, saltar.height / densidad, 0.5f, "el tamaño S, no el M de «Siguiente»")
        assertTrue(saltar.height < avanzar.height, "más bajo que «Siguiente»")
        assertTrue(saltar.width < avanzar.width, "no va a todo el ancho")
        assertTrue(saltar.top >= avanzar.bottom, "debajo de los botones")
        // Centrado bajo la fila «Atrás · Siguiente», que tiene el mismo margen a los dos lados.
        assertEquals(ancho / 2f, saltar.center.x, densidad, "centrado")
        // El botón de texto no tiene fondo, ni siquiera apagado.
        assertEquals(Color.Transparent, coloresDelBoton(VarianteDeBoton.Fantasma, COLORES_CLAROS).first)
        assertEquals(Color.Transparent, coloresDelBotonApagado(VarianteDeBoton.Fantasma, COLORES_CLAROS).first)
    }

    @Test
    fun `los bancos llevan el avatar de su marca`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.CUENTAS)
        val avatares = composeRule.onAllNodesWithTag(TAG_AVATAR_DE_MARCA, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(com.jvillada.movi.ui.accounts.BANCOS_DE_UN_TOQUE.size, avatares.size, "un avatar de marca por banco")
    }

    @Test
    fun `los tipos de cuenta se entienden y van a los tipos que ya existen`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.CUENTAS)
        listOf("Ahorros", "Corriente", "Efectivo", "Inversión").forEach { assertTrue(seVe(it), "falta «$it»") }
        assertFalse(seVe("Dinero"), "«Dinero» no lo entiende alguien nuevo")

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Billetera")
        composeRule.onNodeWithText("Efectivo").performClick()
        composeRule.onNodeWithTag(TAG_AGREGAR_CUENTA_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()

        assertEquals(AccountType.CASH, repo.cuentasCreadas.single().type)
        assertTrue(seVe("Efectivo"), "la fila de la cuenta creada dice su tipo")
    }
    // ── Vuelta 75 · el salario como ancla ──────────────────────────────────────────────────────

    /** La guía con lo que el final necesita para su cifra: las lecturas de siempre del disponible. */
    private open inner class RepoConCifra : RepoDeLaGuia() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = DashboardSummary(gastoVariablePorDia = emptyMap())
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
    }

    @Test
    fun `el periodo pregunta el salario y al terminar crea la regla de ingreso de siempre`() {
        val repo = RepoConCifra()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        assertFalse(seVe(PREGUNTA_DEL_SALARIO), "sin un día elegido todavía no se pregunta")
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        assertTrue(seVe(PREGUNTA_DEL_SALARIO))
        assertTrue(seVe("primera cuenta que agregues", parcial = true), "sin cuentas, dice a cuál llega")
        composeRule.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("7000000")
        composeRule.waitForIdle()
        avanzar()
        assertTrue(repo.reglas.isEmpty(), "sin escrituras propias: la regla se crea al terminar, con las cuentas")

        // Las cuentas: la del salario es la primera de dinero que se agrega.
        composeRule.onNodeWithText("Bancolombia").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("1500000")
        avanzar()
        saltar()
        saltar()
        assertTrue(seVe("Paso 6 de 6"))
        composeRule.waitUntil(5_000) { repo.reglas.isNotEmpty() }

        val salario = repo.reglas.single()
        assertEquals(TransactionType.INCOME, salario.type)
        assertEquals(7_000_000L, salario.amount)
        assertEquals(25, salario.dayOfMonth, "el día del corte: el que reconoce fechaEsperadaDelIngreso")
        assertEquals("Salario", salario.category)
        assertEquals(repo.cuentasCreadas.single().id, salario.accountId)
        // Revisión de la 75: «¿Ya te pagaron este período?» viene en sí: la regla arranca en el siguiente,
        // y el salario de este (que ya está en el saldo) no cuenta como plata por llegar.
        assertEquals(
            arranqueDelPeriodoSiguiente(25, SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR, kotlinx.datetime.Clock.System.now().toEpochMilliseconds()),
            salario.activeFrom,
        )

        // «Listo» con una cifra real, la del héroe de Inicio.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(TAG_CIFRA_DEL_LISTO).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(TAG_CIFRA_DEL_LISTO).assertTextContains("Te quedan \$", substring = true)
        composeRule.onNodeWithTag(TAG_CIFRA_DEL_LISTO).assertTextContains("hasta el", substring = true)
        avanzar()
        assertEquals(1, repo.reglas.size, "terminar no la vuelve a crear")
    }

    /** Escribe el salario, crea una cuenta y llega al final. */
    private fun hastaElFinalConElSalario(repo: RepoDeLaGuia, yaMePagaron: Boolean = true) {
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("7000000")
        composeRule.waitForIdle()
        if (!yaMePagaron) {
            composeRule.onNodeWithTag(TAG_YA_TE_PAGARON).performScrollTo().performClick()
            composeRule.waitForIdle()
        }
        avanzar()
        composeRule.onNodeWithText("Bancolombia").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("1500000")
        avanzar()
        saltar()
        saltar()
    }

    /** Revisión de la 75: «Ir a Inicio» a los 30 ms creaba dos reglas «Salario». */
    @Test
    fun `tocar Ir a Inicio mientras se crea el salario no lo crea dos veces`() {
        val puerta = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = object : RepoConCifra() {
            override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
                puerta.await()
                return super.createRecurringRule(rule)
            }
        }
        hastaElFinalConElSalario(repo)
        assertTrue(seVe("Paso 6 de 6"))
        // Mientras se crea, «Ir a Inicio» está apagado: un toque no hace nada.
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).performClick()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()
        assertTrue(navegaciones.isEmpty())

        puerta.complete(Unit)
        composeRule.waitUntil(5_000) { repo.reglas.isNotEmpty() }
        composeRule.waitForIdle()
        avanzar()
        assertEquals(1, repo.reglas.size, "una sola regla «Salario»")
        assertEquals(listOf<Screen>(Screen.Dashboard), navegaciones)
    }

    @Test
    fun `si todavia no te pagaron, el salario cuenta desde este periodo`() {
        val repo = RepoConCifra()
        hastaElFinalConElSalario(repo, yaMePagaron = false)
        composeRule.waitUntil(5_000) { repo.reglas.isNotEmpty() }
        assertNull(repo.reglas.single().activeFrom, "sin arranque: la ocurrencia de este período cuenta")
    }

    /** Revisión de la 75: la guía promete «a la primera cuenta que agregues»; sin ninguna, no se crea. */
    @Test
    fun `Ahora no antes de agregar cuentas no deja un salario sin cuenta`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("7000000")
        composeRule.waitForIdle()
        avanzar()
        composeRule.onNodeWithText(AHORA_NO).performClick()
        composeRule.waitForIdle()
        assertTrue(repo.reglas.isEmpty())
        assertEquals(listOf<Screen>(Screen.Dashboard), navegaciones)
    }

    @Test
    fun `sin escribir el salario no se crea ninguna regla`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        avanzar()
        composeRule.onNodeWithText(AHORA_NO).performClick()
        composeRule.waitForIdle()
        assertTrue(repo.reglas.isEmpty())
    }

    @Test
    fun `el paso es un rotulo chico y el titulo grande es el del paso`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithTag(TAG_ROTULO_DEL_PASO).assertTextContains("Paso 2 de 6")
        val rotulo = composeRule.onNodeWithTag(TAG_ROTULO_DEL_PASO).getUnclippedBoundsInRoot()
        val titulo = composeRule.onNodeWithTag(TAG_TITULO_DEL_PASO).getUnclippedBoundsInRoot()
        assertTrue(rotulo.height < titulo.height, "el rótulo (${rotulo.height}) es más chico que el título (${titulo.height})")
        assertTrue(rotulo.bottom <= titulo.top, "el rótulo va arriba del título")
    }

    // ── Vuelta 87: el día uno ──────────────────────────────────────────────────────────────────

    @Test
    fun `en los pasos opcionales Siguiente avanza aunque no haya nada que guardar`() {
        val repo = RepoDeLaGuia()
        montar(repo, paso = PasoDePrimerUso.CAPTURA)
        principalHabilitado()
        avanzar()
        assertTrue(seVe("Paso 5 de 6"))
        principalHabilitado()
        avanzar()
        assertTrue(seVe("Paso 6 de 6"))
        assertTrue(repo.reglas.isEmpty() && repo.cuentasCreadas.isEmpty(), "avanzar sin nada no guarda nada")
    }

    @Test
    fun `el periodo y las cuentas siguen pidiendo algo antes de Siguiente`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsNotEnabled()
        composeRule.onNodeWithText("Atrás").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `Tu salario en Lo que pagas cada mes crea la regla de ingreso de siempre`() {
        val repo = RepoConCifra()
        montar(repo, paso = PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        avanzar()
        composeRule.onNodeWithText("Bancolombia").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("1500000")
        avanzar()
        saltar()
        assertTrue(seVe("Paso 5 de 6"))

        composeRule.onNodeWithTag(TAG_FILA_DEL_SALARIO).assertExists()
        assertTrue(seVe("Cuánto te pagan el día 25"), "con el día del período ya puesto")
        assertTrue(repo.reglas.isEmpty(), "sin escrituras propias: la regla se crea al terminar")
        composeRule.onNodeWithTag(TAG_FILA_DEL_SALARIO).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("6500000")
        composeRule.waitForIdle()
        assertTrue(seVe("\$6.500.000 · el día 25"))
        avanzar()
        assertTrue(seVe("Paso 6 de 6"))
        composeRule.waitUntil(5_000) { repo.reglas.isNotEmpty() }

        val salario = repo.reglas.single()
        assertEquals(TransactionType.INCOME, salario.type)
        assertEquals(6_500_000L, salario.amount)
        assertEquals(25, salario.dayOfMonth)
        assertEquals(NOMBRE_DEL_SALARIO, salario.name)
    }

    @Test
    fun `con un ingreso fijo ya anotado la fila del salario no se pregunta`() {
        val repo = object : RepoConCifra() {
            override suspend fun getRecurringRules(): List<RecurringRule> =
                listOf(RecurringRule(id = "s", name = "Salario", category = "Salario", amount = 5_000_000, dayOfMonth = 25, type = TransactionType.INCOME))
        }
        montar(repo, paso = PasoDePrimerUso.RECURRENTES)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_FILA_DEL_SALARIO).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `sin el dia del periodo Tu salario manda a elegirlo`() {
        montar(RepoConCifra(), paso = PasoDePrimerUso.RECURRENTES)
        assertTrue(seVe("Elige antes el día de pago"))
        composeRule.onNodeWithTag(TAG_FILA_DEL_SALARIO).performClick()
        composeRule.waitForIdle()
        assertTrue(seVe("Paso 2 de 6"), "lleva al paso del período")
    }
}

/** Todos los textos debajo de [nodo], para el mensaje de una prueba que falla. */
private fun textosDe(nodo: androidx.compose.ui.semantics.SemanticsNode): List<String> {
    val propios: List<androidx.compose.ui.text.AnnotatedString> =
        nodo.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null } ?: emptyList()
    return propios.map { a -> a.text }.filter { t -> t.isNotBlank() } + nodo.children.flatMap { c -> textosDe(c) }
}
