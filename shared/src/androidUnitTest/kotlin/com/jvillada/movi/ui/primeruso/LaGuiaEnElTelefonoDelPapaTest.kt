package com.jvillada.movi.ui.primeruso

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.OTRO_BANCO
import com.jvillada.movi.ui.accounts.TAG_BUSCAR_BANCO
import com.jvillada.movi.ui.accounts.TAG_FORMULARIO_DE_CUENTA
import com.jvillada.movi.ui.accounts.TAG_LISTA_DE_BANCOS
import com.jvillada.movi.ui.accounts.TAG_OTRO_BANCO
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.components.ds.TAG_PILDORA_DE_FILTRO
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 88 · la guía de primer uso en el teléfono del papá del dueño
 *
 * Un Samsung con navegación de tres botones y la letra grande. Se vio: «Empezar» debajo de la barra del
 * sistema, las píldoras de los bancos cortadas por los dos lados (una fila que se desplazaba de lado) y
 * «Dar acceso a las notificaciones» partido en dos renglones con el segundo afuera del botón.
 *
 * A 320, 360 y 390 dp, con la letra del sistema a ×1,0, ×1,3 y ×1,5 (encima del ×1,12 de la app), en
 * cada paso de la guía de Android:
 * - **ningún rótulo de botón se corta ni se sale de su botón** (se mide el texto contra el botón);
 * - **un solo amarillo** por paso;
 * - en las cuentas, **ninguna píldora se sale de la tarjeta** del formulario.
 *
 * Y aparte, con una barra de navegación de 48 dp simulada, el pie queda por encima de ella.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaGuiaEnElTelefonoDelPapaTest {

    @get:Rule val composeRule = createComposeRule()

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getUserProfile() =
            UserProfile(id = "u1", email = "papa@movi.test", name = "Papá", avatarColor = "#7C6CF0")
    }

    private val escalas = listOf(1.0f, 1.3f, 1.5f)

    private var paso by mutableStateOf(PasoDePrimerUso.BIENVENIDA)
    private var escala by mutableStateOf(1.0f)

    private fun montar() {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            key(paso, escala) {
                ConClaseDeAncho(escala = escala * 1.12f) {
                    Box(Modifier.fillMaxSize()) {
                        PrimerUsoScreen(onNavigate = {}, pasoInicial = paso, modoDeCaptura = ModoDeCaptura.PERMISOS_DEL_TELEFONO)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun ir(nuevo: PasoDePrimerUso, nuevaEscala: Float) {
        paso = nuevo
        escala = nuevaEscala
        composeRule.waitForIdle()
    }

    /** Los límites de un nodo **sin recortar** por sus padres: lo que el texto ocupa de verdad. */
    private fun limites(nodo: SemanticsNode): Rect = Rect(
        nodo.positionInRoot.x, nodo.positionInRoot.y,
        nodo.positionInRoot.x + nodo.size.width, nodo.positionInRoot.y + nodo.size.height,
    )

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult? {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.singleOrNull()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun descendientes(nodo: SemanticsNode): List<SemanticsNode> = nodo.children.flatMap { listOf(it) + descendientes(it) }

    private fun Rect.contiene(otro: Rect): Boolean =
        otro.left >= left - 1f && otro.top >= top - 1f && otro.right <= right + 1f && otro.bottom <= bottom + 1f

    /** Cada rótulo de cada botón cabe entero en su botón: sin «…», sin renglones de más, sin salirse. */
    private fun losBotonesNoSeCortan(contexto: String) {
        val botones = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(VarianteDeBotonKey), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(botones.isNotEmpty(), "$contexto: hay botones")
        val malos = mutableListOf<String>()
        for (boton in botones) {
            val caja = limites(boton)
            for (t in descendientes(boton).filter { it.config.getOrNull(SemanticsProperties.Text) != null }) {
                val d = disposicion(t)
                // Sin «…», sin renglones que no entran de alto (el segundo renglón afuera de una caja de alto
                // fijo) y cada renglón del ancho que su texto tiene en pantalla.
                val entero = d == null || (
                    (0 until d.lineCount).none(d::isLineEllipsized) && !d.didOverflowHeight &&
                        (0 until d.lineCount).all { d.getLineRight(it) - d.getLineLeft(it) <= t.size.width + 0.5f }
                    )
                if (!entero || !caja.contiene(limites(t))) malos += "«${texto(t)}» (${limites(t)} en $caja)"
            }
        }
        assertTrue(malos.isEmpty(), "$contexto: rótulos cortados o afuera del botón: $malos")
    }

    private fun unSoloAmarillo(contexto: String) {
        val amarillos = composeRule.onAllNodes(SemanticsMatcher.expectValue(LlevaElAmarilloKey, true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(amarillos.size <= 1, "$contexto: ${amarillos.size} amarillos")
    }

    private fun lasPildorasNoSeSalen(contexto: String) {
        val tarjeta = limites(composeRule.onNodeWithTag(TAG_FORMULARIO_DE_CUENTA).fetchSemanticsNode())
        val pildoras = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, TAG_PILDORA_DE_FILTRO), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { limites(it).top >= tarjeta.top && limites(it).bottom <= tarjeta.bottom }
        assertTrue(pildoras.size >= 8, "$contexto: las píldoras del formulario (${pildoras.size})")
        val afuera = pildoras.filterNot { p -> limites(p).left >= tarjeta.left - 1f && limites(p).right <= tarjeta.right + 1f }
        assertTrue(afuera.isEmpty(), "$contexto: píldoras fuera de la tarjeta ($tarjeta): ${afuera.map { limites(it) }}")
    }

    private fun recorrer(ancho: Int) {
        montar()
        for (e in escalas) {
            for (p in pasosDeLaGuia(ModoDeCaptura.PERMISOS_DEL_TELEFONO)) {
                ir(p, e)
                val contexto = "$ancho dp ×$e · $p"
                losBotonesNoSeCortan(contexto)
                unSoloAmarillo(contexto)
                if (p == PasoDePrimerUso.CUENTAS) lasPildorasNoSeSalen(contexto)
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    fun `a 320 dp ningun boton se corta, un amarillo por paso y las pildoras en la tarjeta`() = recorrer(320)

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun `a 360 dp tambien`() = recorrer(360)

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
    fun `a 390 dp tambien`() = recorrer(390)

    /** «Atrás» y «Siguiente» lado a lado si caben; con la letra grande a 320, apilados con el principal arriba. */
    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    fun `con la letra grande a 320 Atras y Siguiente van apilados, el principal arriba`() {
        montar()
        ir(PasoDePrimerUso.PERIODO, 1.0f)
        val avanzar = composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        val atras = composeRule.onNodeWithTag(TAG_ATRAS_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        assertEquals(avanzar.top, atras.top, 1f, "a ×1,0 caben lado a lado")
        assertTrue(atras.right <= avanzar.left, "«Atrás» a la izquierda")

        // «Guardar y seguir» es el rótulo más largo del pie: con la cuenta escrita, a ×1,5.
        ir(PasoDePrimerUso.CUENTAS, 1.5f)
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("Billetera")
        composeRule.waitForIdle()
        val avanzar2 = composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        val atras2 = composeRule.onNodeWithTag(TAG_ATRAS_EN_LA_GUIA).fetchSemanticsNode().boundsInRoot
        assertTrue(avanzar2.bottom <= atras2.top, "apilados, «Guardar y seguir» arriba ($avanzar2 / $atras2)")
        losBotonesNoSeCortan("pie apilado")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun `Otro banco abre la lista de todos los bancos con busqueda`() {
        montar()
        ir(PasoDePrimerUso.CUENTAS, 1.3f)
        composeRule.onNodeWithTag(TAG_OTRO_BANCO).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_BUSCAR_BANCO).performTextInput("bogota")
        composeRule.waitForIdle()
        val enLaLista = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(TAG_LISTA_DE_BANCOS))
        val resultados = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(TAG_PILDORA_DE_FILTRO) and enLaLista).fetchSemanticsNodes()
        assertEquals(1, resultados.size, "«bogota» encuentra un solo banco")
        assertTrue(composeRule.onAllNodes(androidx.compose.ui.test.hasText("Banco de Bogotá") and enLaLista).fetchSemanticsNodes().size == 1)
        // Lo que no está: «Usar «lo escrito»».
        composeRule.onNodeWithTag(TAG_BUSCAR_BANCO).performTextInput("zz")
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodes(androidx.compose.ui.test.hasText("Usar «bogotazz»")).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(TAG_BUSCAR_BANCO).performTextClearance()
        composeRule.onNodeWithTag(TAG_BUSCAR_BANCO).performTextInput("bogota")
        composeRule.waitForIdle()
        // Elegirlo lo deja a la vista y propone el nombre.
        composeRule.onNode(androidx.compose.ui.test.hasTestTag(TAG_PILDORA_DE_FILTRO) and enLaLista)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Banco de Bogotá Ahorros"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "el nombre propuesto sale del banco elegido",
        )
        assertTrue(
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(TAG_LISTA_DE_BANCOS)).fetchSemanticsNodes().isEmpty(),
            "la lista se cierra al elegir",
        )
        assertTrue(composeRule.onAllNodes(androidx.compose.ui.test.hasText(OTRO_BANCO)).fetchSemanticsNodes().isNotEmpty())
        lasPildorasNoSeSalen("con un banco de la lista elegido")
    }

    /**
     * **El pie, por encima de la barra de tres botones**: la guía montada como la monta App.kt (la
     * cáscara sin pestañas) con 48 dp de barra de navegación simulada abajo.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun `el pie queda por encima de la barra de navegacion del sistema`() {
        Repositories.sustitutoDePrueba = repo
        var vista: View? = null
        composeRule.setContent {
            vista = LocalView.current
            key(paso) {
                ConClaseDeAncho(escala = 1.5f * 1.12f) {
                    EsqueletoDeLaCascara(
                        pantalla = Screen.PrimerUso(),
                        activeTab = null,
                        conNavegacion = false,
                        onTabSelected = {},
                        relevoDeScroll = remember { RelevoDeScroll() },
                    ) {
                        PrimerUsoScreen(onNavigate = {}, pasoInicial = paso, modoDeCaptura = ModoDeCaptura.PERMISOS_DEL_TELEFONO)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val barra = (48 * composeRule.density.density).toInt()
        composeRule.runOnIdle {
            val v = checkNotNull(vista)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, barra))
                .build()
            ViewCompat.dispatchApplyWindowInsets(v, insets)
        }
        composeRule.waitForIdle()
        val alto = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        for (p in listOf(PasoDePrimerUso.BIENVENIDA, PasoDePrimerUso.CAPTURA)) {
            paso = p
            composeRule.waitForIdle()
            val avanzar = limites(composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).fetchSemanticsNode())
            assertTrue(avanzar.bottom <= alto - barra + 1f, "$p: el principal ($avanzar) queda arriba de la barra (alto $alto, barra $barra)")
            if (p == PasoDePrimerUso.CAPTURA) {
                val saltar = limites(composeRule.onNodeWithTag(TAG_SALTAR_EN_LA_GUIA).fetchSemanticsNode())
                assertTrue(saltar.bottom <= alto - barra + 1f, "«Saltar este paso» ($saltar) queda arriba de la barra")
            }
        }
    }
}
