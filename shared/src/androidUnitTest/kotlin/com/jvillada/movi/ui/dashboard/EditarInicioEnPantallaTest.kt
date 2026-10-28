package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PARA_REVISAR_COMPACTO
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.ui.sdui.TAG_CARRUSEL_DE_CUENTAS
import com.jvillada.movi.ui.sdui.TAG_PROPUESTAS_DEL_INICIO
import com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.inicioConPreferencias
import com.jvillada.movi.shared.model.limpias
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.seccionesConfigurablesDe
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_PARA_REVISAR_COMPACTO
import com.jvillada.movi.ui.sdui.TAG_TARJETA_DEL_DISPONIBLE
import com.jvillada.movi.ui.sdui.textoDeParaRevisarCompacto
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Editar inicio» montado, con Hoy debajo (Ola 3 · Hoy en una mirada)
 *
 * Arriba la pantalla «Editar inicio»; abajo Hoy, pintado con la definición que «sirve» un repositorio
 * de prueba que hace lo mismo que el server: guarda la preferencia y arma Hoy con
 * [inicioConPreferencias]. Así cada prueba recorre el camino entero —tocar el interruptor o la flecha,
 * guardar, y ver Hoy cambiar— sin red.
 *
 * `sdk = [34]`: estas pruebas comparan posiciones de texto (ver la memoria del repo sobre SDK 24).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3600dp-xhdpi")
class EditarInicioEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val porDefecto = defaultDashboardDefinition()

    /** Lo que haría el server: guarda la preferencia limpia y sirve Hoy con ella puesta. */
    private inner class ServerDePrueba : RepositorioDePrueba() {
        var guardada = PreferenciasDelInicio()
        val hoyServido = mutableStateOf<ScreenDefinition>(porDefecto)
        var guardados = 0

        override suspend fun getPreferenciasDelInicio() =
            guardada.copy(ordenDeMovi = seccionesConfigurablesDe(porDefecto))

        override suspend fun guardarPreferenciasDelInicio(preferencias: PreferenciasDelInicio): PreferenciasDelInicio {
            guardados++
            guardada = preferencias.limpias()
            hoyServido.value = inicioConPreferencias(porDefecto, guardada)
            return guardada.copy(ordenDeMovi = seccionesConfigurablesDe(porDefecto))
        }

        override suspend fun getScreen(slug: String, cachedVersion: Int?) = inicioConPreferencias(porDefecto, guardada)
    }

    private val ajustes = PeriodSettings(cutoffDay = 25)

    /**
     * Un Hoy con todo a la vista: el disponible (con sus lecturas), el gasto por categoría y doce
     * mensajes del banco sin confirmar —lo que vuelve urgente a «Para revisar»—.
     */
    private fun datos(pendingSms: Int = 12) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_500_000)),
        spentByCategory = mapOf("Mercado" to 1_500_000L, "Transporte" to 400_000L),
        ajustesDePeriodo = ajustes,
        periodoActual = periodoDe(Clock.System.now().toEpochMilliseconds(), ajustes),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = emptyMap(),
        pendingSms = pendingSms,
    )

    private lateinit var server: ServerDePrueba

    private fun montar(data: DashboardData = datos()) {
        server = ServerDePrueba()
        Repositories.sustitutoDePrueba = server
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(900.dp)) { EditarInicioScreen(onNavigate = {}) }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        SduiRenderer(
                            definition = server.hoyServido.value,
                            data = data,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { hayTag(tagDeLaFilaDelEditor("CUENTAS")) }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    private fun hayTag(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hayTexto(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * El título de la sección [texto] en Hoy. Desde la ola R0 los títulos de sección se escriben
     * como el rótulo de su fila en el editor («Disponible»), así que se buscan por `TAG_SECCION`.
     */
    private fun seccion(texto: String) = composeRule.onAllNodes(
        hasTestTag(TAG_SECCION) and hasAnyDescendant(hasText(texto)),
        useUnmergedTree = true,
    )

    private fun haySeccion(texto: String) = seccion(texto).fetchSemanticsNodes().isNotEmpty()

    private fun arriba(texto: String): Dp = seccion(texto)[0].getUnclippedBoundsInRoot().top

    private fun tocar(rotulo: String) = composeRule.onNodeWithContentDescription(rotulo, useUnmergedTree = true).performClick()

    private fun arribaDelTag(tag: String): Dp =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot().top

    /**
     * Ola R1: «Disponible» salió del Inicio por defecto (repetía Plan), pero sigue en «Editar inicio»,
     * apagado. Prenderlo lo agrega: el server lo pone en la definición de quien lo pide.
     */
    @Test
    fun `prender Disponible lo agrega al Inicio`() {
        montar()
        assertTrue(!hayTag(TAG_TARJETA_DEL_DISPONIBLE), "por defecto el Inicio no trae el disponible")
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Disponible"), useUnmergedTree = true).assertIsOff()

        tocar(rotuloDeMostrar("Disponible"))
        composeRule.waitUntil(5_000) { hayTag(TAG_TARJETA_DEL_DISPONIBLE) }

        assertEquals(listOf("DISPONIBLE_DEL_PERIODO"), server.guardada.visibles)
        assertEquals(emptyList(), server.guardada.ocultas)
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Disponible"), useUnmergedTree = true).assertIsOn()
        // El Inicio quedó donde lo busca al volver: la memoria y el aparato.
        composeRule.waitUntil(5_000) { ScreenDefCache.dashboard?.sections?.any { it.type == "DISPONIBLE_DEL_PERIODO" } == true }
        assertEquals(ScreenDefCache.dashboard, InstantaneaDelInicio.delAparato.definicion("u1"))
    }

    @Test
    fun `ocultar Tus cuentas lo saca del Inicio`() {
        montar()
        assertTrue(hayTag(TAG_CARRUSEL_DE_CUENTAS), "antes, el Inicio muestra el carrusel")
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Tus cuentas"), useUnmergedTree = true).assertIsOn()

        tocar(rotuloDeMostrar("Tus cuentas"))
        composeRule.waitUntil(5_000) { !hayTag(TAG_CARRUSEL_DE_CUENTAS) }

        assertEquals(listOf("CUENTAS"), server.guardada.ocultas)
        // Lo demás sigue: el héroe, la cuadrícula y las propuestas.
        assertTrue(hayTag(TAG_TARJETA_DEL_HERO))
        assertTrue(hayTag(TAG_PROPUESTAS_DEL_INICIO))
        assertTrue(hayTag(TAG_TARJETA_EN_QUE_SE_VA))
    }

    @Test
    fun `subir En que se va la pone antes que las cuentas en el Inicio y en la lista`() {
        montar()
        assertTrue(arribaDelTag(TAG_CARRUSEL_DE_CUENTAS) < arribaDelTag(TAG_TARJETA_EN_QUE_SE_VA), "en el orden de Movi, las cuentas van antes")

        tocar(rotuloDeSubir("En qué se va"))
        composeRule.waitUntil(5_000) { server.guardada.orden.isNotEmpty() }
        composeRule.waitForIdle()
        tocar(rotuloDeSubir("En qué se va"))
        composeRule.waitUntil(5_000) { server.guardada.orden.firstOrNull() == "GASTO_POR_CATEGORIA" }
        composeRule.waitForIdle()

        assertTrue(arribaDelTag(TAG_TARJETA_EN_QUE_SE_VA) < arribaDelTag(TAG_CARRUSEL_DE_CUENTAS), "subida, «En qué se va» va antes de las cuentas")
        // La lista del editor dice el mismo orden que el Inicio.
        val filaGasto = composeRule.onNodeWithTag(tagDeLaFilaDelEditor("GASTO_POR_CATEGORIA"), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val filaCuentas = composeRule.onNodeWithTag(tagDeLaFilaDelEditor("CUENTAS"), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertTrue(filaGasto < filaCuentas)
        // Lo fijo no se mueve: el héroe, la cuadrícula y las propuestas siguen arriba.
        assertEquals(listOf("HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS"), server.hoyServido.value.sections.take(5).map { it.type })
    }

    @Test
    fun `Volver al orden de Movi restaura`() {
        montar()
        tocar(rotuloDeSubir("En qué se va"))
        composeRule.waitUntil(5_000) { server.guardada.orden.isNotEmpty() }
        tocar(rotuloDeMostrar("Tu patrimonio"))
        composeRule.waitUntil(5_000) { server.guardada.visibles.isNotEmpty() }

        composeRule.onNodeWithText(VOLVER_AL_ORDEN_DE_MOVI, useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) { server.guardada.esLaDeMovi }
        composeRule.waitForIdle()

        assertEquals(porDefecto, server.hoyServido.value, "el Inicio vuelve a ser el de Movi, con su versión")
        assertTrue(arribaDelTag(TAG_CARRUSEL_DE_CUENTAS) < arribaDelTag(TAG_TARJETA_EN_QUE_SE_VA))
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Tu patrimonio"), useUnmergedTree = true).assertIsOff()
    }

    /**
     * Ola R1: «Para revisar» dejó de ser una sección configurable —lo que pide decisión está en «Movi
     * propone hoy», fijo debajo del héroe—, así que el editor ya no la lista, y lo urgente se ve igual.
     */
    @Test
    fun `Para revisar ya no se configura, y lo que espera lo cuenta la insignia de Avisos`() {
        montar(datos(pendingSms = 12))
        assertTrue(!hayTag(tagDeLaFilaDelEditor("ALERTS")))
        assertTrue(!haySeccion("Para revisar"))
        // Vuelta de diseño 28: lo que entró solo no es una tarjeta de «Movi propone hoy»; lo cuenta la
        // insignia de «Avisos», siempre en la cuadrícula.
        assertTrue(!hayTexto("Llegaron 12 avisos del banco"))
        assertTrue(hayTexto("12"), "la insignia de «Avisos»")
    }

    /**
     * Una definición vieja que todavía trae la línea compacta de «Para revisar» (un tipo personal que
     * un server anterior armaba): sin nada urgente, no ocupa lugar.
     */
    @Test
    fun `con Para revisar compacta y nada urgente no ocupa lugar`() {
        // Dos mensajes por confirmar: es una sugerencia, no algo urgente.
        val data = datos(pendingSms = 2)
        val conCompacta = porDefecto.copy(sections = porDefecto.sections.map {
            if (it.type == "ALERTS") ScreenSection(type = PARA_REVISAR_COMPACTO, title = it.title) else it
        })
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = conCompacta,
                        data = data,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(cosasParaRevisarDe(data).isNotEmpty() && cosasParaRevisarDe(data).none { it.urgente })
        assertTrue(!hayTag(TAG_PARA_REVISAR_COMPACTO))
        assertTrue(!hayTexto("Para revisar"))
    }
}
