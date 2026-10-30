package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.CurvaConRellenoKey
import com.jvillada.movi.ui.accounts.CurvaDelPatrimonio
import com.jvillada.movi.ui.accounts.TAG_CURVA_DEL_PATRIMONIO
import com.jvillada.movi.ui.components.LocalPunteroFino
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_MARCA
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.ClaveDelIconoKey
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoDeMovimiento
import com.jvillada.movi.ui.components.ds.InicialesDelAvatarKey
import com.jvillada.movi.ui.components.ds.QuienEsElAvatar
import com.jvillada.movi.ui.components.ds.QuienEsElAvatarKey
import com.jvillada.movi.ui.components.ds.TAG_AVATAR_DE_PERSONA
import com.jvillada.movi.ui.components.ds.TAG_BARRA_DE_PROGRESO
import com.jvillada.movi.ui.components.ds.TAG_CHEVRON
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_ICONO_DE_MOVIMIENTO
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_PROPUESTA
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDeIconoKey
import com.jvillada.movi.ui.credits.ElPrestamoSinSaldo
import com.jvillada.movi.ui.credits.TAG_PRESTAMO_SIN_SALDO
import com.jvillada.movi.ui.credits.TAG_TITULO_SIN_SALDO
import com.jvillada.movi.ui.credits.loQueSeDiceSinSaldo
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.EncabezadoDelInicio
import com.jvillada.movi.ui.espacios.AvatarDelMiembro
import com.jvillada.movi.ui.mas.MasScreen
import com.jvillada.movi.ui.porrevisar.AnotacionEntendida
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.quickadd.TAG_CABEZA_DEL_MONTO
import com.jvillada.movi.ui.sdui.TAG_CATEGORIA_DEL_WIDGET
import com.jvillada.movi.ui.sdui.TAG_DIFERENCIA_DEL_WIDGET
import com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA
import com.jvillada.movi.ui.sdui.WidgetsDelInicio
import com.jvillada.movi.ui.sms.AvatarDeLaCabeza
import com.jvillada.movi.ui.sms.CabezaDelAviso
import com.jvillada.movi.ui.transactions.AvisosPorConfirmar
import com.jvillada.movi.ui.transactions.EncabezadoDelMovimiento
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.REVISAR_LOS_AVISOS
import com.jvillada.movi.ui.transactions.TAG_TARJETA_DE_AVISOS
import com.jvillada.movi.ui.transactions.TarjetaDeAvisosDelBanco
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 80, montada
 *
 * Un solo ícono por movimiento, un solo avatar del dueño, «En qué se va» con filas y barras y sin «Tus
 * widgets», Agregar sin hueco a 390, la tarjeta de avisos de un renglón, la curva de pocos puntos sin
 * relleno, el chevrón de toda fila tocable y la pieza del préstamo sin saldo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class Vuelta80EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun hay(texto: String, substring: Boolean = true): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun descendientes(n: SemanticsNode): List<SemanticsNode> = n.children + n.children.flatMap(::descendientes)

    private fun textos(n: SemanticsNode): List<String> =
        (listOf(n) + descendientes(n)).mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

    // ── (1) Un solo ícono por movimiento ─────────────────────────────────────

    /** «Cuota de Vehículo 4083» con el banco en el comercio: la «O» de Occidente en el detalle de la cuenta. */
    private val cuota = FinancialEvent(
        id = "ev_cuota", accountId = "ahorros", type = TransactionType.EXPENSE, amount = 980_000, category = "Cuota de crédito",
        description = "Cuota de Vehículo 4083", merchant = "Banco de Occidente S A", timestamp = 1_761_600_000_000,
    )
    private val ahorros = Account("ahorros", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 10_581_200)

    @Test
    fun `el mismo movimiento lleva el mismo icono en todos los lugares`() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    // Inicio («Últimos movimientos»), Actividad y el detalle de la cuenta: la misma fila.
                    MovementSingleRow(tx = cuota, accountNames = mapOf(ahorros.id to ahorros.name), onClick = {})
                    // La hoja del movimiento.
                    EncabezadoDelMovimiento(evento = cuota, cuentas = listOf(ahorros), onEditar = {})
                    // Un gasto de un grupo y un aviso de Por revisar, que no son un `FinancialEvent`.
                    IconoDeMovimiento(cuota.merchant, cuota.description, cuota.category)
                    AvatarDeLaCabeza(CabezaDelAviso(titulo = "Cuota", subtitulo = "Nu · hoy", categoria = cuota.category))
                }
            }
        }
        composeRule.waitForIdle()
        val claves = conTag(TAG_ICONO_DE_MOVIMIENTO).map { it.config[ClaveDelIconoKey] }
        assertEquals(4, claves.size, "cada lugar lo pinta con IconoDeMovimiento: $claves")
        assertEquals(setOf("categoria:Cuota de crédito"), claves.toSet(), "el mismo ícono en todos")
        assertTrue(conTag(TAG_AVATAR_DE_MARCA).isEmpty(), "un banco no es el comercio: nada de la «O» azul")
    }

    // ── (2) Un solo avatar del dueño ─────────────────────────────────────────

    private fun avataresDelDueno() = conTag(TAG_AVATAR_DE_PERSONA).filter { it.config.getOrNull(QuienEsElAvatarKey) == QuienEsElAvatar.Dueno }

    private fun esElDelDueno(donde: String, iniciales: String) {
        val avatar = avataresDelDueno().singleOrNull() ?: error("$donde: no encontré el avatar del dueño")
        assertEquals(TonoDeIcono.Propio, avatar.config[TonoDeIconoKey], "$donde: el dueño va en el tinte, nunca en rojo")
        assertEquals(iniciales, avatar.config[InicialesDelAvatarKey], "$donde: sus iniciales")
    }

    @Test
    fun `el avatar del dueno va en el tinte en el Inicio`() {
        composeRule.setContent {
            MoviTheme {
                EncabezadoDelInicio(saludo = "Hola, Juan", subtitulo = "Octubre", iniciales = "JV", conPunto = false, onAvatar = {}, onCampana = {})
            }
        }
        composeRule.waitForIdle()
        esElDelDueno("Inicio", "JV")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h3200dp-xhdpi")
    fun `el avatar del dueno va en el tinte en Tu, aunque el perfil guarde rojo`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan Villada", avatarColor = "#E53935", periodCutoffDay = 25)
            override suspend fun isScreenAdmin(): Boolean = false
            override suspend fun getDireccionDeCorreo(): DireccionDeCorreo = DireccionDeCorreo(token = "abc", direccion = null, configurado = false)
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { com.jvillada.movi.ui.mas.MasScreen(onNavigate = {}, onLogout = {}, esAndroid = false) } } }
        composeRule.waitUntil(5_000) { hay("Juan Villada") }
        esElDelDueno("Tú", "JV")
    }

    @Test
    fun `el avatar del dueno va en el tinte en Grupos, y Caro en su tono`() {
        composeRule.setContent {
            MoviTheme {
                Column {
                    AvatarDelMiembro(MIEMBRO_YO, "Juan Villada")
                    AvatarDelMiembro("dst_caro", "Caro")
                }
            }
        }
        composeRule.waitForIdle()
        esElDelDueno("Grupos", "JV")
        val caro = conTag(TAG_AVATAR_DE_PERSONA).single { it.config.getOrNull(QuienEsElAvatarKey) == QuienEsElAvatar.Tercero }
        assertEquals(TonoDeIcono.Info, caro.config[TonoDeIconoKey])
        assertEquals("C", caro.config[InicialesDelAvatarKey])
    }

    // ── (3) «En qué se va»: filas con barras, y sin «Tus widgets» ────────────

    @Test
    fun `En que se va lleva una fila con su barra por categoria y nada de Tus widgets`() {
        val data = DashboardData(
            spentByCategory = mapOf("Otros" to 1_800_000L, "Cuota de crédito" to 980_000L, "Mercado" to 499_050L),
            gastoDelAnterior = mapOf("Otros" to 1_000_000L, "Mercado" to 184_300L),
            diasComparados = 12,
        )
        composeRule.setContent { MoviTheme { WidgetsDelInicio(tipos = listOf("DONDE_SE_VA"), data = data, onNavigate = {}) } }
        composeRule.waitForIdle()
        assertFalse(hay("Tus widgets"), "«Tus widgets» es palabra de desarrollador")
        assertTrue(hay("En qué se va"), "el widget lleva su propio título")
        val categorias = conTag(TAG_CATEGORIA_DEL_WIDGET)
        assertEquals(3, categorias.size)
        categorias.forEach { c ->
            val adentro = descendientes(c)
            // Vuelta 83: ya no es una Fila (medía ~72 con la barra aparte): el nombre y el monto en un renglón.
            assertTrue(textos(c).size >= 2, "cada categoría dice su nombre y su monto: ${textos(c)}")
            assertTrue(adentro.any { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_BARRA_DE_PROGRESO }, "con su barra: ${textos(c)}")
        }
        // La diferencia va en una píldora con su texto adentro.
        val diferencias = conTag(TAG_DIFERENCIA_DEL_WIDGET).flatMap(::textos)
        assertTrue(diferencias.isNotEmpty(), "la diferencia contra el período anterior")
        assertTrue(conTag(TAG_TARJETA_EN_QUE_SE_VA).isNotEmpty())
    }

    // ── (5) Agregar sin hueco a 390 ──────────────────────────────────────────

    @Test
    fun `Agregar a 390 sin teclado en pantalla no deja un hueco y la cifra es la protagonista`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalPunteroFino provides true) {
                MoviTheme {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}) }
                        Spacer(Modifier.height(64.dp))
                    }
                }
            }
        }
        // Vuelta 83: la cuenta va en su nombre corto («Bancolombia ·4057»).
        val corto = com.jvillada.movi.ui.components.nombreCortoDeCuenta(ahorros.name)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(corto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val nota = composeRule.onNodeWithText("Nota", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val guardar = composeRule.onNodeWithText("Guardar movimiento", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hueco = guardar.top - nota.bottom
        assertTrue(hueco < 200.dp, "entre «Nota» y «Guardar movimiento» quedan $hueco (eran ~350)")
        val cabeza = composeRule.onNodeWithTag(TAG_CABEZA_DEL_MONTO, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(cabeza.bottom - cabeza.top > 85.dp, "la cifra protagonista (~54 sp; sin ella mide ~69): ${cabeza.bottom - cabeza.top}")
        // La cuenta en un renglón.
        val cuenta = composeRule.onAllNodesWithText(corto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().first()
        val r = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        cuenta.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        assertEquals(1, r.single().lineCount, "«$corto» en un renglón")
    }

    // ── (6) La tarjeta de avisos, de un renglón ──────────────────────────────

    private fun entendida(id: String, comercio: String) = AnotacionEntendida(
        cabezaId = id,
        sms = SmsMessage(id = id, time = "2026-10-28T09:00:00", bank = "Bancolombia", text = "Compra", state = SMS_STATE_PENDING, det = ""),
        pago = null,
        leido = ParsedSms(amount = 27_500.0, merchant = comercio, type = TransactionType.EXPENSE, category = "Comida"),
        cuenta = ahorros,
        categoria = "Comida",
    )

    @Test
    fun `la tarjeta de avisos es una propuesta de un renglon con Confirmar 2 y Revisar`() {
        val avisos = AvisosPorConfirmar(cuantos = 5, suman = 1_100_200L, comercios = "Juan Valdez y 3 más", confirmables = listOf(entendida("s1", "Rappi"), entendida("s2", "D1")))
        composeRule.setContent { MoviTheme { TarjetaDeAvisosDelBanco(avisos = avisos, onConfirmar = {}, onRevisar = {}) } }
        composeRule.waitForIdle()
        assertTrue(conTag(TAG_TARJETA_DE_PROPUESTA).isNotEmpty(), "es una TarjetaDePropuesta")
        assertTrue(hay("Confirmar 2", substring = false))
        assertTrue(hay(REVISAR_LOS_AVISOS, substring = false))
        assertFalse(hay("Revisar uno por uno"))
        val titulo = composeRule.onNodeWithText("5 avisos del banco por confirmar", useUnmergedTree = true).fetchSemanticsNode()
        val r = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        titulo.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        assertEquals(1, r.single().lineCount, "el título en un renglón")
        val alto = composeRule.onNodeWithTag(TAG_TARJETA_DE_AVISOS, useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue(alto < 150.dp, "la tarjeta ya no domina Actividad: $alto (eran ~210)")
    }

    // ── (7) Patrimonio ───────────────────────────────────────────────────────

    @Test
    fun `la curva con tres puntos va sin relleno`() {
        val historia = HistoriaDelPatrimonio(
            puntos = listOf(
                PuntoDelPatrimonio(mes = "2026-08", fecha = "2026-08-31", neto = 9_148_800),
                PuntoDelPatrimonio(mes = "2026-09", fecha = "2026-09-30", neto = 9_500_000),
                PuntoDelPatrimonio(mes = "2026-10", fecha = "2026-10-30", neto = 9_006_700, esHoy = true),
            ),
        )
        composeRule.setContent { MoviTheme { CurvaDelPatrimonio(historia) } }
        composeRule.waitForIdle()
        assertEquals(false, composeRule.onNodeWithTag(TAG_CURVA_DEL_PATRIMONIO).fetchSemanticsNode().config[CurvaConRellenoKey])
    }

    @Test
    fun `toda fila tocable lleva el chevron, y con monto lo pide`() {
        composeRule.setContent {
            MoviTheme {
                Column {
                    Fila(titulo = "Cerrar sesión", onClick = {})
                    Fila(titulo = "Bancolombia Ahorros 4057", final = { Cifra(10_581_200) }, onClick = {}, chevron = true)
                    Fila(titulo = "Solo informa", final = { Cifra(1) })
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(2, conTag(TAG_CHEVRON).size, "las dos tocables, y la que solo informa no")
    }

    // ── (8) El préstamo sin saldo, una sola pieza ────────────────────────────

    @Test
    fun `la pieza del prestamo sin saldo dice lo mismo y lleva el boton secundario chico`() {
        val vehiculo = Account(id = "veh", name = "Vehículo 4083", type = AccountType.LOAN, balance = -980_000)
        val dice = loQueSeDiceSinSaldo(listOf(vehiculo), setOf(vehiculo.id), emptyMap())!!
        composeRule.setContent { MoviTheme { ElPrestamoSinSaldo(dice, onAccion = {}) } }
        composeRule.waitForIdle()
        assertEquals(listOf("Falta el saldo de Vehículo 4083"), conTag(TAG_TITULO_SIN_SALDO).flatMap(::textos))
        val boton = composeRule.onNodeWithText("Cargarlo", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(conTag(TAG_PRESTAMO_SIN_SALDO).isNotEmpty())
        assertTrue(boton.config.contains(SemanticsProperties.Text))
    }
}
