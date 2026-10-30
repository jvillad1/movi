package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.sdui.TAG_GUIA_EN_LA_COLUMNA_IZQUIERDA
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import androidx.compose.runtime.remember
import com.jvillada.movi.ui.ai.preguntasSugeridas
import com.jvillada.movi.ui.ai.preguntasParaTocar
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.inicioConPreferencias
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **El Inicio montado entero** (el renderer SDUI con la definición de la generación 9, ola R1),
 * en un teléfono de 390 dp y en un escritorio de 1.400 dp.
 *
 * En `sdk = [34]` a propósito: en el SDK 24 de Robolectric el texto con interlineado mide ancho cero
 * (ver la memoria del repo sobre SDK 24), y estas pruebas comparan posiciones de texto en pantalla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InicioDeUnVistazoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 22_152_488, egresos = 33_882_000),
        accounts = listOf(
            Account(id = "a1", name = "Cuenta de ahorros", type = AccountType.SAVINGS, balance = 558_350),
            Account(id = "l1", name = "Hipoteca", type = AccountType.LOAN, balance = 2_191_000_000),
        ),
        spentByCategory = mapOf(CUOTA_CATEGORY to 12_915_000L, "Comida" to 2_000_000L),
    )

    /**
     * Dos cuentas de «Tu plata» en vez de una — misma suma ($558.350) que [datos], para que la
     * cifra grande del hero y el veredicto no cambien y las pruebas de siempre sigan valiendo.
     */
    private val datosConDosCuentasDeTuPlata = datos.copy(
        accounts = listOf(
            Account(id = "a1", name = "Cuenta de ahorros", type = AccountType.SAVINGS, balance = 300_000),
            Account(id = "a2", name = "Nequi", type = AccountType.CHECKING, balance = 258_350),
            Account(id = "l1", name = "Hipoteca", type = AccountType.LOAN, balance = 2_191_000_000),
        ),
    )

    private var navegoA: Screen? = null

    /**
     * Una definición de la generación 8, con «Pregúntale a Movi» y el patrimonio: el Editor de
     * pantallas o la instantánea de un aparato la pueden traer, y sus secciones se siguen pintando.
     */
    private val deLaGeneracion8 = defaultDashboardDefinition().let { d ->
        d.copy(
            sections = listOf(d.sections.first(), ScreenSection(type = "PREGUNTALE_A_MOVI", title = "Pregúntale a Movi")) +
                d.sections.drop(1) + ScreenSection(type = "PATRIMONIO", title = "Tu patrimonio"),
        )
    }

    private fun montar(data: DashboardData = datos, definicion: ScreenDefinition = defaultDashboardDefinition()) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = definicion,
                        data = data,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { navegoA = it },
                    )
                }
            }
        }
    }

    private fun arriba(texto: String): Dp =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    /** «Tu plata» y su cifra se repiten en la tarjeta del patrimonio: el hero es el primero. */
    private fun primero(texto: String) = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).onFirst()

    private fun cuantas(texto: String): Int =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Dónde está el cuerpo de la tarjeta héroe (vuelta 39: el veredicto salió de ella). */
    private fun heroe() = composeRule.onNodeWithTag(TAG_TARJETA_DEL_HERO, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun izquierda(texto: String): Dp =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).getUnclippedBoundsInRoot().left

    /**
     * **Ola R1 (generación 9)**: en el teléfono una columna, en el orden del rediseño —el héroe, la
     * cuadrícula de acciones, «Movi propone hoy», «Tus cuentas» y los widgets—. La barra Entró/Salió,
     * «Para revisar» y el BANNER de Movi AI ya no se pintan.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `en el telefono una columna, en el orden del rediseño`() {
        montar()

        primero("\$558.350").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TARJETA_DEL_HERO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Entró", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Salió", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Flujo del mes", useUnmergedTree = true).assertDoesNotExist()

        val hero = heroe().top
        val acciones = arriba("Anotar gasto")
        val propuestas = arriba("Movi propone hoy")
        val cuentas = arriba("Tus cuentas")
        val widgets = arriba(com.jvillada.movi.ui.sdui.TITULO_DEL_WIDGET_EN_QUE_SE_VA)
        assertTrue(hero < acciones, "la cuadrícula va debajo del héroe")
        assertTrue(acciones < propuestas, "las propuestas debajo de la cuadrícula")
        assertTrue(propuestas < cuentas, "las cuentas debajo de las propuestas")
        assertTrue(cuentas < widgets, "y los widgets al final")
        assertEquals(izquierda("Movi propone hoy"), izquierda("Tus cuentas"))
        // Lo que quedó solo para los APK viejos no se pinta: ni «Para revisar» ni el banner.
        composeRule.onNodeWithText("Pregúntale a Movi AI", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Para revisar", useUnmergedTree = true).assertDoesNotExist()
        // Vuelta 68: «Tus widgets» ya no lleva su propio «Editar»; la única puerta es «Editar inicio».
        composeRule.onNodeWithText("Editar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1400dp-h1200dp-mdpi")
    fun `en escritorio dos columnas, el heroe a la izquierda y la cuadricula a su lado`() {
        montar()

        // Vuelta 39: dos columnas parejas. El héroe a la izquierda y la cuadrícula arriba de la derecha,
        // a la altura del héroe (debajo de la banda); lo demás, a la columna más corta.
        val heroIzq = heroe().left
        assertTrue(izquierda("Anotar gasto").value > heroIzq.value + 300f, "las acciones a la derecha del héroe")
        assertTrue(arriba("Anotar gasto") < heroe().bottom, "y a su misma altura")
    }

    /**
     * Ola W1: en la cáscara real. A 1.280 dp el rail ancho se lleva 216 y a Hoy —un tablero— le
     * quedan 1.064, sobre el umbral de 900 del contenido: dos columnas.
     */
    @Test
    @Config(qualifiers = "w1280dp-h1200dp-mdpi")
    fun `a 1280 dp en la cascara, dos columnas`() {
        montarEnLaCascara()

        assertTrue(
            izquierda("Anotar gasto").value > heroe().left.value + 300f,
            "la cuadrícula va en la columna derecha",
        )
    }

    /**
     * Y en una ventana mediana (768 dp, rail compacto de 80) quedan 688 dp: por debajo del umbral,
     * una columna, en el orden del teléfono.
     */
    @Test
    @Config(qualifiers = "w768dp-h2400dp-mdpi")
    fun `a 768 dp en la cascara, una columna`() {
        montarEnLaCascara()

        assertEquals(izquierda("Movi propone hoy"), izquierda("Tus cuentas"))
        assertTrue(heroe().top < arriba("Tus cuentas"), "las cuentas van debajo del héroe")
    }

    /** Un lugar para la guía de primeros pasos: lo que importa acá es dónde cae, no qué dice. */
    private val guia: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(120.dp).testTag("guia"))
    }

    private fun limitesDeLaGuia() = composeRule.onNodeWithTag("guia", useUnmergedTree = true).getUnclippedBoundsInRoot()

    /**
     * Ola W3: con dos columnas la guía de primeros pasos va arriba de la izquierda, con sus bordes, y
     * no centrada en 720 encima de las dos (a 1.280 caía entre las dos columnas, sin alinear con
     * ninguna).
     */
    @Test
    @Config(qualifiers = "w1280dp-h1200dp-mdpi")
    fun `a 1280 dp la guia va arriba de la columna izquierda`() {
        montarEnLaCascara(header = guia)

        val laGuia = limitesDeLaGuia()
        val derecha = izquierda("Anotar gasto")
        assertEquals(216f, laGuia.left.value, 0.5f, "empieza en el borde izquierdo del panel, junto al rail")
        assertTrue(laGuia.right <= derecha, "no se mete en la columna derecha: ${laGuia.right} / $derecha")
        assertTrue(laGuia.bottom <= heroe().top, "va arriba del hero")
        // La derecha arranca arriba, sin esperar a la guía.
        assertTrue(arriba("Anotar gasto") < laGuia.bottom, "la columna derecha (las acciones) no baja por la guía")
    }

    /** En una columna la guía sigue como siempre: arriba de todo, con el ancho de la columna. */
    @Test
    @Config(qualifiers = "w768dp-h2400dp-mdpi")
    fun `a 768 dp la guia va arriba de la unica columna`() {
        montarEnLaCascara(header = guia)

        val laGuia = limitesDeLaGuia()
        assertTrue(laGuia.bottom <= heroe().top)
        // 768 − 80 del rail compacto = 688, menos que la columna de 720: la guía llena el panel.
        assertEquals(80f, laGuia.left.value, 0.5f)
        assertEquals(688f, (laGuia.right - laGuia.left).value, 0.5f)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_GUIA_EN_LA_COLUMNA_IZQUIERDA, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private fun montarEnLaCascara(header: (@Composable () -> Unit)? = null) {
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Dashboard,
                    activeTab = NavTab.HOY,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = datos,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { navegoA = it },
                        header = header,
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `las preguntas y el campo abren el chat`() {
        montar(definicion = deLaGeneracion8)
        // Ola 3: el chip dice la versión corta, y lo que se manda es la pregunta completa.
        val primera = preguntasParaTocar(datos).first()
        assertEquals(preguntasSugeridas(datos).first(), primera.texto)
        assertTrue(primera.corta != primera.texto, "la de las reglas tiene su versión corta")
        composeRule.onNodeWithText(primera.texto, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(primera.corta, useUnmergedTree = true).performClick()
        assertEquals(Screen.AIChat(preguntaInicial = primera.texto), navegoA)

        composeRule.onNodeWithText("Escribe tu pregunta…", useUnmergedTree = true).performClick()
        assertEquals(Screen.AIChat(), navegoA)
    }

    /**
     * Una de las tres de respaldo lleva su id al chat: con él el server la contesta con los números
     * de Movi, sin la IA. Lo que pasa al llegar al chat lo prueba `LasPreguntasSugeridasEnElChatTest`.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `una de las tres de respaldo abre el chat con su id`() {
        montar(definicion = deLaGeneracion8)
        val deRespaldo = preguntasParaTocar(datos).first { it.sugerida != null }
        composeRule.onNodeWithText(deRespaldo.corta, useUnmergedTree = true).performClick()
        assertEquals(Screen.AIChat(preguntaInicial = deRespaldo.texto, sugerida = deRespaldo.sugerida?.id), navegoA)
        assertTrue(deRespaldo.sugerida?.id != null)
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `el patrimonio honesto con sus tramos`() {
        // Ola R1: el patrimonio ya no viene en el Inicio por defecto; quien lo prende en «Editar
        // inicio» lo recibe en su definición.
        montar(definicion = inicioConPreferencias(defaultDashboardDefinition(), PreferenciasDelInicio(visibles = listOf("PATRIMONIO"))))
        composeRule.onNodeWithText("Tienes", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Debes", useUnmergedTree = true).assertIsDisplayed()
        // Vuelta de diseño 19: las cifras de «Tu patrimonio» van enteras, como el héroe.
        primero("\$2.191.000.000").assertIsDisplayed()
        composeRule.onNodeWithText("Deudas", useUnmergedTree = true).performClick()
        assertEquals(Screen.Credits, navegoA)
    }

    /**
     * La línea del rango del período («Del 25 de agosto al 24 de septiembre · …») es parte de la
     * tarjeta, y la tarjeta entera lleva a «Tus períodos». Solo existe con un corte distinto de 1
     * ([PeriodSettings.esMesDeCalendario]); con el corte de siempre la línea ni se pinta.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `el rango del periodo es tocable y lleva a Tus periodos`() {
        val ajustes = PeriodSettings(cutoffDay = 25)
        val conRangoDePeriodo = datos.copy(ajustesDePeriodo = ajustes, periodoActual = PeriodoFinanciero(2026, 9))
        montar(conRangoDePeriodo)

        // Vuelta 75: el nombre único del período.
        composeRule.onNodeWithText("25 ago – 24 sep", substring = true, useUnmergedTree = true)
            .performClick()
        assertEquals(Screen.Periodos, navegoA)
    }

    /**
     * **Sin «Te quedan», la cifra («Tu plata») lleva a «Tus períodos»**, y en ningún caso a Patrimonio
     * (Patrimonio tiene su lugar en «Tú» y el carrusel su «Ver todo»).
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `la cifra del heroe lleva a Tus periodos y no a Patrimonio`() {
        montar()

        // «$558.350» también está en la tarjeta de la cuenta, más abajo: el primero es el héroe.
        primero("\$558.350").performClick()
        assertEquals(Screen.Periodos, navegoA)
    }

    /**
     * Ola T: con una sola cuenta de «Tu plata» ([datos] — la Hipoteca es deuda, no cuenta) no hay
     * nada que desglosar (mismo criterio `cuentas.size > 1` que ya usa «Tu patrimonio»): el chevron
     * ni se pinta.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `con una sola cuenta de Tu plata el hero no ofrece desglose`() {
        montar()
        composeRule.onNodeWithContentDescription("Ver el detalle de tu plata", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /**
     * Ola T pedía el desglose de «Tu plata» en el héroe. **Ola R1: el desglose salió del héroe** —las
     * cuentas tienen su carrusel justo debajo, cada una con su saldo—, así que con dos cuentas el héroe
     * no ofrece chevron, y cada cuenta aparece una sola vez, en su tarjeta.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `con mas de una cuenta el heroe no desglosa, las cuentas estan en el carrusel`() {
        montar(datosConDosCuentasDeTuPlata)

        composeRule.onNodeWithContentDescription("Ver el detalle de tu plata", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(1, cuantas("\$300.000"))
        assertEquals(1, cuantas("\$258.350"))
        assertEquals(1, cuantas("Nequi"))
        primero("\$558.350").assertIsDisplayed()
    }

    /**
     * **Vuelta 39: la cifra grande nunca arranca en $0.** Antes entraba contando desde cero, y en la web
     * el héroe decía «$0» varios segundos al recargar. Ahora, con el reloj quieto, el primer cuadro ya
     * tiene la cifra entera.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `la cifra nunca arranca en cero`() {
        composeRule.mainClock.autoAdvance = false
        montar()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(0, cuantas("\$0"), "ni un cuadro con $0")
        primero("\$558.350").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `al volver al Inicio la cifra ya esta en su lugar`() {
        DashboardDataCache.entradasHechas += "hero.cifra"
        composeRule.mainClock.autoAdvance = false
        montar()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(0, cuantas("\$0"), "sin contar de nuevo desde cero: la cifra ya está en su lugar")
    }
}
