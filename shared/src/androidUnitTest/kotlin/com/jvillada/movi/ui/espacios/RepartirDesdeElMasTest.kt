package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.quickadd.hacerElAtajo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # «Repartir» desde el menú del «+» abre la hoja (vuelta de diseño 7, mudada en la 26)
 *
 * Vuelta 7: con un espacio, la hoja «Pagó otro» sobre ese; con varios, primero se elige; sin ninguno,
 * la creación del espacio. Vuelta 26: «Repartir» salió de la cuadrícula del Inicio y vive en el menú
 * del «+»; abre **lo mismo que antes**. Se monta la barra con el «+» y sus atajos como la monta la
 * cáscara (`App.kt`: [hacerElAtajo] con un [EstadoDeRepartir] y sus [HojasDeRepartir]), y la pantalla
 * a la que navega, como lo haría la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class RepartirDesdeElMasTest {

    @get:Rule val composeRule = createComposeRule()

    private fun espacio(id: String, con: String) = Espacio(
        id = id,
        nombre = "Casa con $con",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_$con", con)),
    )

    private val casa = espacio("esp_casa", "Caro")
    private val oficina = espacio("esp_oficina", "Ana")

    private inner class ConEspacios(private val espacios: List<Espacio>?) : RepositorioDePrueba() {
        override suspend fun getEspacios(): List<Espacio> = espacios ?: throw ApiException(500, "caído")
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio {
            val e = espacios.orEmpty().first { it.id == id }
            return DetalleDelEspacio(
                espacio = e,
                saldo = saldoDelEspacio(e, emptyList(), emptyList()),
                delPeriodo = saldoDelEspacio(e, emptyList(), emptyList()),
                gastos = emptyList(),
            )
        }
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
    }

    private val navegado = mutableListOf<Screen>()
    private var pantalla by mutableStateOf<Screen?>(null)

    /** La barra con el «+» y sus atajos; al navegar a un espacio, la pantalla del espacio (como la app). */
    private fun montar(espacios: List<Espacio>?) {
        Repositories.sustitutoDePrueba = ConEspacios(espacios)
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Compact) {
                MoviTheme {
                    val onNavigate: (Screen) -> Unit = {
                        navegado += it
                        pantalla = it
                    }
                    val repartir = rememberEstadoDeRepartir(onNavigate)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        when (val p = pantalla) {
                            is Screen.Espacio -> EspacioScreen(p.id, pagoOtro = p.pagoOtro)
                            else -> MinBottomNav(
                                active = NavTab.HOY,
                                onTabSelected = {},
                                onAtajo = { atajo ->
                                    hacerElAtajo(atajo, navegar = onNavigate, repartir = repartir::tocar, elegirComprobante = {})
                                },
                            )
                        }
                        HojasDeRepartir(repartir)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(condicion: () -> Boolean) = composeRule.waitUntil(timeoutMillis = 5_000, condicion)

    /** Mantener presionado el «+» y elegir «Repartir» en su menú. */
    private fun tocarRepartir() {
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(AtajoDeAgregar.REPARTIR.rotulo, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `con un espacio, Repartir desde el mas abre la hoja de anotar lo que pago otro sobre ese`() {
        montar(listOf(casa))
        tocarRepartir()
        esperar { navegado.isNotEmpty() }
        assertEquals(listOf<Screen>(Screen.Espacio("esp_casa", pagoOtro = true)), navegado)
        // La hoja de siempre, abierta: «Pagó otro» con «¿Cuánto pagó Caro?».
        esperar { hay("Pagó otro") }
        esperar { hay("¿Cuánto pagó Caro?") }
    }

    @Test
    fun `con varios espacios, primero se elige y despues se abre la hoja`() {
        montar(listOf(casa, oficina))
        tocarRepartir()
        esperar { hay(TITULO_ELEGIR_ESPACIO) }
        assertEquals(emptyList(), navegado, "todavía no navega: falta elegir")
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_FILA_ELEGIR_ESPACIO, useUnmergedTree = true).fetchSemanticsNodes().size)

        // La segunda fila: «Casa con Ana». Por la acción de toque (la hoja todavía puede estar entrando).
        composeRule.onAllNodesWithTag(TAG_FILA_ELEGIR_ESPACIO)[1].assert(hasText("Casa con Ana", substring = true))
            .performSemanticsAction(SemanticsActions.OnClick)
        esperar { navegado.isNotEmpty() }
        assertEquals(listOf<Screen>(Screen.Espacio("esp_oficina", pagoOtro = true)), navegado)
        esperar { hay("¿Cuánto pagó Ana?") }
    }

    @Test
    fun `sin espacios, Repartir abre la creacion del espacio`() {
        montar(emptyList())
        tocarRepartir()
        esperar { hay("¿Con quién compartes?") }
        assertEquals(emptyList(), navegado)
    }

    @Test
    fun `si no se pudieron leer los espacios, lleva a la pestana Espacios como antes`() {
        montar(null)
        tocarRepartir()
        esperar { navegado.isNotEmpty() }
        assertEquals(listOf<Screen>(Screen.Espacios), navegado)
    }

    @Test
    fun `a donde lleva Repartir segun cuantos espacios hay`() {
        assertEquals(DestinoDeRepartir.CrearEspacio, destinoDeRepartir(emptyList()))
        assertEquals(DestinoDeRepartir.AnotarEn("esp_casa"), destinoDeRepartir(listOf(casa)))
        assertEquals(DestinoDeRepartir.ElegirEspacio(listOf(casa, oficina)), destinoDeRepartir(listOf(casa, oficina)))
        assertEquals(null, conQuienDe(casa), "«Casa con Caro» ya dice con quién")
        assertEquals("Con Caro", conQuienDe(casa.copy(nombre = "Apartamento")))
    }
}
