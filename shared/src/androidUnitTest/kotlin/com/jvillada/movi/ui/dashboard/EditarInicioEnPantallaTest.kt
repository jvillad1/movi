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
        composeRule.waitUntil(5_000) { hayTag(tagDeLaFilaDelEditor("ALERTS")) }
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

    @Test
    fun `ocultar Disponible lo saca de Hoy`() {
        montar()
        assertTrue(hayTag(TAG_TARJETA_DEL_DISPONIBLE), "antes, Hoy muestra el disponible")
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Disponible"), useUnmergedTree = true).assertIsOn()

        tocar(rotuloDeMostrar("Disponible"))
        composeRule.waitUntil(5_000) { !hayTag(TAG_TARJETA_DEL_DISPONIBLE) }

        assertEquals(listOf("DISPONIBLE_DEL_PERIODO"), server.guardada.ocultas)
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Disponible"), useUnmergedTree = true).assertIsOff()
        // Lo demás sigue: el héroe y la sección de al lado.
        assertTrue(hayTag(TAG_TARJETA_DEL_HERO))
        assertTrue(hayTexto("En qué se va"))
        // Hoy quedó donde Hoy la busca al volver: la memoria y el aparato.
        composeRule.waitUntil(5_000) { ScreenDefCache.dashboard?.sections?.none { it.type == "DISPONIBLE_DEL_PERIODO" } == true }
        assertEquals(ScreenDefCache.dashboard, InstantaneaDelInicio.delAparato.definicion("u1"))
    }

    @Test
    fun `subir En que se va la pone antes en Hoy y en la lista`() {
        montar()
        assertTrue(arriba("Disponible") < arriba("En qué se va"), "en el orden de Movi, el disponible va antes")

        tocar(rotuloDeSubir("En qué se va"))
        composeRule.waitUntil(5_000) { server.guardada.orden.isNotEmpty() }
        composeRule.waitForIdle()

        assertTrue(arriba("En qué se va") < arriba("Disponible"), "subida, «En qué se va» va antes del disponible")
        // La lista del editor dice el mismo orden que Hoy.
        val filaGasto = composeRule.onNodeWithTag(tagDeLaFilaDelEditor("GASTO_POR_CATEGORIA"), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val filaDisponible = composeRule.onNodeWithTag(tagDeLaFilaDelEditor("DISPONIBLE_DEL_PERIODO"), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertTrue(filaGasto < filaDisponible)
        // El héroe no se mueve: sigue primero.
        assertEquals("HERO_BALANCE", server.hoyServido.value.sections.first().type)
    }

    @Test
    fun `Volver al orden de Movi restaura`() {
        montar()
        tocar(rotuloDeSubir("En qué se va"))
        composeRule.waitUntil(5_000) { server.guardada.orden.isNotEmpty() }
        tocar(rotuloDeMostrar("Tu patrimonio"))
        composeRule.waitUntil(5_000) { server.guardada.ocultas.isNotEmpty() }

        composeRule.onNodeWithText(VOLVER_AL_ORDEN_DE_MOVI, useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) { server.guardada.esLaDeMovi }
        composeRule.waitForIdle()

        assertEquals(porDefecto, server.hoyServido.value, "Hoy vuelve a ser el de Movi, con su versión")
        assertTrue(arriba("Disponible") < arriba("En qué se va"))
        composeRule.onNodeWithContentDescription(rotuloDeMostrar("Tu patrimonio"), useUnmergedTree = true).assertIsOn()
    }

    @Test
    fun `con Para revisar oculta y algo urgente aparece la linea compacta`() {
        val data = datos(pendingSms = 12)
        montar(data)
        assertTrue(haySeccion("Para revisar"), "antes, la sección entera")

        tocar(rotuloDeMostrar("Para revisar"))
        composeRule.waitUntil(5_000) { hayTag(TAG_PARA_REVISAR_COMPACTO) }

        assertEquals(PARA_REVISAR_COMPACTO, server.hoyServido.value.sections.single { it.type == PARA_REVISAR_COMPACTO }.type)
        assertTrue(!haySeccion("Para revisar"), "la sección se fue…")
        val cuantas = cosasParaRevisarDe(data).size + anomaliasVisiblesDe(data).size
        assertTrue(hayTexto(textoDeParaRevisarCompacto(cuantas)), "…y queda una línea con cuántas cosas hay")
        assertTrue(hayTexto(PARA_REVISAR_ESCONDIDA), "el editor dice qué pasa con lo urgente")

        // Tocarla la abre ahí mismo.
        composeRule.onNodeWithTag(TAG_PARA_REVISAR_COMPACTO).performClick()
        composeRule.waitUntil(5_000) { haySeccion("Para revisar") }
    }

    @Test
    fun `con Para revisar oculta y nada urgente no ocupa lugar`() {
        // Dos mensajes por confirmar: es una sugerencia, no algo urgente.
        val data = datos(pendingSms = 2)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = inicioConPreferencias(porDefecto, PreferenciasDelInicio(ocultas = listOf("ALERTS"))),
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
