package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.auth.RegisterScreen
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
        modo: ModoDeCaptura = ModoDeCaptura.REENVIO_DE_CORREO,
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

        assertEquals(listOf<Screen>(Screen.PrimerUso), navegaciones)
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
        avanzar()

        assertTrue(seVe("Paso 2 de 6"))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Saltar")
        avanzar()
        assertTrue(repo.perfiles.isEmpty(), "saltar el período no guarda nada")

        assertTrue(seVe("Paso 3 de 6"))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertIsNotEnabled()
        assertTrue(seVe("Agrega al menos una cuenta para seguir", parcial = true))
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
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Saltar")
        avanzar()
        assertTrue(seVe("Paso 5 de 6"))
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Saltar")
        avanzar()

        assertTrue(seVe("Paso 6 de 6"))
        assertTrue(seVe("Ninguna todavía"), "el resumen dice que no quedó ninguna cuenta")
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
        assertTrue(seVe("Hoy estarías en", parcial = true), "la consecuencia se ve en vivo")
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
        // El nombre lo escribió el banco; el saldo es el segundo campo.
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("1500000")
        composeRule.onNodeWithText("Agregar cuenta").performScrollTo().performClick()
        composeRule.waitForIdle()

        val creada = repo.cuentasCreadas.single()
        assertEquals("Bancolombia", creada.name)
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
        campos[1].performTextInput("5")
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

    // ── Que Movi se entere solo ────────────────────────────────────────────────────────────────

    @Test
    fun `en la web el paso de captura da la direccion de correo para copiar y el paso a paso de Gmail`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.CAPTURA, modo = ModoDeCaptura.REENVIO_DE_CORREO)
        assertTrue(seVe("alertas+abc@movi.resend.app"))
        assertTrue(seVe("Copiar"))
        assertTrue(seVe(PASOS_DEL_REENVIO_EN_GMAIL.first()))
        assertFalse(seVe("Captura en este teléfono"))

        composeRule.onNodeWithText("Copiar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente")
    }

    @Test
    fun `en Android el paso de captura es la seccion de permisos de Captura del banco`() {
        montar(RepoDeLaGuia(), paso = PasoDePrimerUso.CAPTURA, modo = ModoDeCaptura.PERMISOS_DEL_TELEFONO)
        assertTrue(seVe("Captura en este teléfono"))
        assertTrue(seVe("Permiso de SMS"))
        assertFalse(seVe("Copiar"))
    }

    @Test
    fun `sin la direccion configurada en el server se dice y se puede saltar`() {
        val repo = object : RepoDeLaGuia() {
            override suspend fun getDireccionDeCorreo() = DireccionDeCorreo(token = "abc", configurado = false)
        }
        montar(repo, paso = PasoDePrimerUso.CAPTURA)
        assertTrue(seVe("todavía no está disponible", parcial = true))
        assertNull(composeRule.onAllNodesWithText("Copiar").fetchSemanticsNodes().firstOrNull())
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Saltar")
    }
}
