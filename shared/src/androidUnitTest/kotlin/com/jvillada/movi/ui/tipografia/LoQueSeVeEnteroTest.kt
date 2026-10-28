package com.jvillada.movi.ui.tipografia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AvatarDePersona
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.espacios.MontoYTuParte
import com.jvillada.movi.ui.plan.AHORA_NO
import com.jvillada.movi.ui.plan.FilaDeRecordatoriosSinCanal
import com.jvillada.movi.ui.plan.SUBTITULO_RECORDATORIOS_APAGADOS
import com.jvillada.movi.ui.plan.TITULO_RECORDATORIOS_SIN_CANAL
import com.jvillada.movi.ui.transactions.AccionDelMovimiento
import com.jvillada.movi.ui.transactions.FilaDeAcciones
import com.jvillada.movi.ui.transactions.TAG_ROTULO_DE_ACCION_DEL_MOVIMIENTO
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
 * # Lo que con Figtree se partía o se cortaba, entero (vuelta 29)
 *
 * La revisión de la vuelta 29 a 390 y 360 dp vio, con la letra nueva: «Bancolombia Ahorros» en dos
 * renglones en Patrimonio, «Mercado del sábado» en dos en Espacios, «Categoría» recortado en la fila de
 * acciones de un movimiento, y «Tus recordatorios…» partido con «Toca para activar las notifi…» en
 * Plan. Se monta cada pieza **como la arma su pantalla** —la `Fila` del sistema en una `Superficie` de
 * lista, con el margen de la pantalla—, con el motor de texto real (`NATIVE`), Figtree y la letra de la
 * app (×1,12), y se mide.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LoQueSeVeEnteroTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(contenido: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun medida(texto: String): TextLayoutResult {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val medidas = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        return medidas.single()
    }

    private fun enteroEnUnRenglon(texto: String) {
        val m = medida(texto)
        assertEquals(1, m.lineCount, "«$texto» en ${m.lineCount} renglones")
        assertFalse(m.isLineEllipsized(0), "«$texto» con «…»")
        // A mano y no con `didOverflowWidth`, que da `true` por redondeo aunque el texto mida lo que pide.
        val ancho = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode().size.width
        assertTrue(m.getLineRight(0) - m.getLineLeft(0) <= ancho + 0.5f, "«$texto» más ancho que su lugar")
    }

    @Composable
    private fun EnLaPantalla(contenido: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) { contenido() }
    }

    // ── Patrimonio: una cuenta con su saldo ───────────────────────────────────────────────────

    private fun cuentaDePatrimonio() = montar {
        EnLaPantalla {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = "Bancolombia Ahorros",
                    inicio = { IconoEnCirculo(Icons.Rounded.AccountBalance, tono = TonoDeIcono.Neutro) },
                    final = { Cifra(13_760_800) },
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 Bancolombia Ahorros va en un renglon en Patrimonio`() {
        cuentaDePatrimonio()
        enteroEnUnRenglon("Bancolombia Ahorros")
    }

    /**
     * A 360 no entra en un renglón al lado de un saldo de ocho cifras a la letra de la maqueta (15 a la
     * vista): la `Fila` lo lleva a dos, entero, sin «…» —su regla de siempre—.
     */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 Bancolombia Ahorros se lee entero en Patrimonio`() {
        cuentaDePatrimonio()
        val m = medida("Bancolombia Ahorros")
        assertTrue(m.lineCount <= 2)
        assertFalse((0 until m.lineCount).any { m.isLineEllipsized(it) }, "«Bancolombia Ahorros» con «…»")
    }

    // ── Espacios: un gasto con su monto y tu parte ────────────────────────────────────────────

    private fun gastoDelEspacio() = montar {
        EnLaPantalla {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = "Mercado del sábado",
                    subtitulo = "26 oct · Caro",
                    inicio = { AvatarDePersona("Caro", tono = TonoDeIcono.Info) },
                    final = { MontoYTuParte(240_000, 120_000) },
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 Mercado del sabado va en un renglon en Espacios`() {
        gastoDelEspacio()
        enteroEnUnRenglon("Mercado del sábado")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 Mercado del sabado va en un renglon en Espacios`() {
        gastoDelEspacio()
        enteroEnUnRenglon("Mercado del sábado")
    }

    // ── El detalle de un movimiento: la fila de acciones ──────────────────────────────────────

    private fun accionesDelMovimiento() = montar {
        EnLaPantalla {
            FilaDeAcciones(acciones = AccionDelMovimiento.entries.toList().take(6), abierta = null, onElegir = {})
        }
    }

    private fun ningunRotuloDeAccionRecortado() {
        val nodos = composeRule.onAllNodesWithTag(TAG_ROTULO_DE_ACCION_DEL_MOVIMIENTO, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodos.isNotEmpty())
        nodos.forEach { nodo ->
            val medidas = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
            val m = medidas.single()
            assertTrue(m.getLineRight(0) - m.getLineLeft(0) <= nodo.size.width + 0.5f, "«${m.layoutInput.text}» más ancho que su columna")
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 ningun rotulo de las acciones del movimiento se recorta`() {
        accionesDelMovimiento()
        ningunRotuloDeAccionRecortado()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 ningun rotulo de las acciones del movimiento se recorta`() {
        accionesDelMovimiento()
        ningunRotuloDeAccionRecortado()
    }

    // ── Plan: los recordatorios que no van a llegar ───────────────────────────────────────────

    private fun recordatorios() = montar {
        EnLaPantalla {
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                FilaDeRecordatoriosSinCanal(bloqueadas = false, onActivar = {}, onAhoraNo = {})
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 los recordatorios se leen enteros`() {
        recordatorios()
        enteroEnUnRenglon(TITULO_RECORDATORIOS_SIN_CANAL)
        enteroEnUnRenglon(SUBTITULO_RECORDATORIOS_APAGADOS)
        composeRule.onNodeWithText(AHORA_NO, useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 los recordatorios se leen enteros`() {
        recordatorios()
        enteroEnUnRenglon(TITULO_RECORDATORIOS_SIN_CANAL)
        val sub = medida(SUBTITULO_RECORDATORIOS_APAGADOS)
        assertFalse((0 until sub.lineCount).any { sub.isLineEllipsized(it) }, "el subtítulo con «…»")
    }

    // ── El menos de una cifra, pegado al «$» ──────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `el menos queda pegado al signo de pesos`() {
        montar {
            Column {
                Cifra(-85_000, modifier = Modifier.testTag("con"))
                androidx.compose.foundation.text.BasicText(
                    "−\$85.000",
                    style = com.jvillada.movi.theme.Movi.textos.monto,
                    modifier = Modifier.testTag("sin"),
                )
            }
        }
        val con = composeRule.onAllNodesWithTag("con", useUnmergedTree = true).fetchSemanticsNodes().single().size.width
        val sin = composeRule.onAllNodesWithTag("sin", useUnmergedTree = true).fetchSemanticsNodes().single().size.width
        assertTrue(con < sin, "con el menos pegado mide $con px; sin el ajuste, $sin px")
    }
}
