package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EstadoDePresupuesto
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.COLORES_OSCUROS
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CASILLA
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_PILDORA
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifraKey
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Presupuestos con jerarquía y una sola señal por estado (vuelta de diseño 14)
 *
 * A 360 dp —el teléfono más angosto—, con la letra real (`sdk = [34]`, `NATIVE`):
 *
 * - el resumen «Gastado en …» es una `Cifra` `Grande`, no una héroe (en Plan competía con
 *   «Disponible del período»);
 * - una categoría pasada tiene **una** señal roja: la barra (en `Peligro`) y el texto de abajo («Te
 *   pasaste por $120.700», en `sale`). El porcentaje va neutro y chico, sin píldora; la única píldora
 *   es la del resumen («Sobrepasados · 1»). Una que va bien dice «Te quedan $16.100» en el texto medio;
 * - cada propuesta es una `Fila`: el tope con `Cifra` a la derecha, la casilla del sistema después,
 *   y la fila entera se marca (toque de 48 o más, con semántica de casilla);
 * - nada se recorta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class PresupuestosConJerarquiaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    /** 190.700 de 70.000 → 273 %, pasada por $120.700; 23.200 de 39.300 → 59 %, quedan $16.100. */
    private inner class ConPresupuestos : RepositorioDePrueba() {
        override suspend fun getBudgets(): List<Budget> = listOf(
            Budget("Restaurantes y domicilios", 70_000L),
            Budget("Mercado", 39_300L),
        )
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = DashboardSummary(
            spentByCategory = mapOf("Restaurantes y domicilios" to 190_700L, "Mercado" to 23_200L),
        )
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
    }

    private val propuestas = listOf(
        PropuestaDePresupuesto("Arriendo", 1_850_000.0, 1_850_000.0, "2026-09-25", "2026-10-24"),
        PropuestaDePresupuesto("Restaurantes y domicilios", 450_000.0, 432_800.0, "2026-09-25", "2026-10-24"),
        PropuestaDePresupuesto("Mercado", 1_050_000.0, 1_020_000.0, "2026-09-25", "2026-10-24"),
        PropuestaDePresupuesto("Fútbol", 100_000.0, 99_001.0, "2026-09-25", "2026-10-24"),
    )

    private inner class SinPresupuestos : RepositorioDePrueba() {
        override suspend fun getBudgets(): List<Budget> = emptyList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = DashboardSummary(scope = Scope.SELF)
        override suspend fun getPropuestasDePresupuesto(): List<PropuestaDePresupuesto> = propuestas
    }

    private fun montar(repo: RepositorioDePrueba, esperar: String) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) { conTexto(esperar, substring = true).isNotEmpty() }
        composeRule.waitForIdle()
    }

    private fun conTexto(texto: String, substring: Boolean = false): List<SemanticsNode> =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes()

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentro(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentro)

    private fun descendientes(nodo: SemanticsNode): List<SemanticsNode> = nodo.children.flatMap { listOf(it) + descendientes(it) }

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    private fun colorDe(nodo: SemanticsNode): Color = disposicion(nodo).layoutInput.style.color

    /** El texto se dibuja entero: sin «…» y cada renglón cabe en el ancho del nodo. */
    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo)
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    /** Todo texto montado (con su disposición) se ve entero a 360 dp. */
    private fun nadaSeRecorta() {
        val textos = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { SemanticsActions.GetTextLayoutResult in it.config && texto(it).isNotEmpty() }
        assertTrue(textos.isNotEmpty())
        textos.forEach { assertTrue(seVeEntero(it), "«${texto(it)}» se recorta a 360 dp") }
    }

    @Test
    fun `el resumen es una cifra Grande y no compite con una heroe`() {
        montar(ConPresupuestos(), esperar = "Restaurantes y domicilios")
        val tarjeta = nodos(TAG_TARJETA_DEL_GASTO_DEL_PERIODO).single()
        val cifras = (listOf(tarjeta) + descendientes(tarjeta)).filter { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_CIFRA }
        assertEquals(listOf(VarianteDeCifra.Grande), cifras.map { it.config[VarianteDeCifraKey] }, "la cifra de «Gastado en …»")
        assertEquals("\$213.900", texto(cifras.single()))
    }

    @Test
    fun `una categoria pasada lleva una sola senal roja, barra y texto, sin pildora`() {
        montar(ConPresupuestos(), esperar = "Restaurantes y domicilios")
        val colores = COLORES_OSCUROS

        // El texto de abajo dice lo que importa, en el tono del estado.
        val pasada = conTexto("Te pasaste por \$120.700").single()
        assertEquals(colores.sale, colorDe(pasada))
        assertEquals(TonoDeBarra.Peligro, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.EXCEDIDO_MUCHO))
        assertTrue(conTexto("Sobrepasado ·", substring = true).isEmpty(), "el rótulo viejo se fue")

        // El porcentaje: neutro, chico, y no en una píldora.
        val porcentajes = nodos(TAG_PORCENTAJE_DEL_PRESUPUESTO)
        assertEquals(listOf("273 %", "59 %"), porcentajes.map(::texto))
        porcentajes.forEach { assertEquals(colores.textoMedio, colorDe(it), "«${texto(it)}» no va en el color del estado") }
        val pildoras = nodos(TAG_PILDORA).map { textosDentro(it).joinToString(" ") }
        assertEquals(listOf("Sobrepasados · 1"), pildoras, "la única píldora es la del resumen")

        // La que va bien: «Te quedan», en el texto medio (el verde lo dice la barra).
        val bien = conTexto("Te quedan \$16.100").single()
        assertEquals(colores.textoMedio, colorDe(bien))
        assertEquals(TonoDeBarra.Exito, tonoDeLaBarraDelPresupuesto(EstadoDePresupuesto.DENTRO))
        nadaSeRecorta()
    }

    @Test
    fun `cada propuesta es una fila con el tope a la derecha y una casilla de 48`() {
        montar(SinPresupuestos(), esperar = "Movi te propone 4")
        val densidad = composeRule.density.density
        assertTrue(conTexto("· tope", substring = true).isEmpty(), "el tope ya no va en el subtítulo")
        propuestas.forEach { p ->
            val fila = nodos(tagDePropuestaDePresupuesto(p.category)).single()
            // El toque: la fila entera, con semántica de casilla y 48 o más por lado.
            assertEquals(Role.Checkbox, fila.config[SemanticsProperties.Role])
            assertTrue(fila.size.height / densidad >= Medida.objetivoTactil.value, "${p.category}: el toque mide ${fila.size.height / densidad} dp")
            assertTrue(fila.size.width / densidad >= Medida.objetivoTactil.value)

            val adentro = descendientes(fila)
            val titulo = adentro.single { texto(it) == p.category }
            val subtitulo = adentro.single { texto(it).startsWith("Gastaste") }
            val tope = adentro.single { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_TOPE_DE_LA_PROPUESTA }
            val casilla = adentro.single { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_CASILLA }
            assertEquals(subtituloDeLaPropuesta(p), texto(subtitulo))
            assertEquals(VarianteDeCifra.Fila, tope.config[VarianteDeCifraKey])
            assertTrue(texto(tope).startsWith("$"), "el tope es una cifra: «${texto(tope)}»")
            assertTrue(tope.boundsInRoot.left >= titulo.boundsInRoot.right, "${p.category}: el tope va a la derecha del nombre")
            assertTrue(tope.boundsInRoot.left >= subtitulo.boundsInRoot.right, "${p.category}: el tope va a la derecha del subtítulo")
            assertTrue(casilla.boundsInRoot.left >= tope.boundsInRoot.right, "${p.category}: la casilla va después del tope")
            assertTrue(casilla.boundsInRoot.right <= fila.boundsInRoot.right)
        }
        assertEquals("\$1.850.000", texto(descendientes(nodos(tagDePropuestaDePresupuesto("Arriendo")).single())
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_TOPE_DE_LA_PROPUESTA }))
        assertTrue(conTexto("Crear estos 4").isNotEmpty())
        assertTrue(conTexto("Crear uno a mano").isNotEmpty())
        nadaSeRecorta()
    }
}
