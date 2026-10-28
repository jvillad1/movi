package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ai.PREGUNTAS_DE_RESPALDO
import com.jvillada.movi.ui.ai.PREGUNTAS_PARA_TOCAR_DE_RESPALDO
import com.jvillada.movi.ui.ai.preguntasParaTocar
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FILA_ESQUELETO_PREGUNTA
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Task 7: los esqueletos del Inicio
 *
 * El hero mide lo mismo cargando (esqueleto) que cargado (veredicto + barra + fila) — y también en
 * los dos estados A MEDIO CAMINO, cuando las cuentas o el resumen llegan antes que el otro (fix
 * round 1) —, y «Pregúntale a Movi» muestra sus tres filas esqueleto —no las tres preguntas de
 * respaldo— mientras no hay ni una cuenta ni un resumen Y hay una carga en vuelo.
 *
 * `sdk = [34]`: la memoria del repo documenta que en el SDK 24 de Robolectric el texto con
 * interlineado mide ancho cero.
 *
 * `@GraphicsMode(NATIVE)`: en el modo por default de Robolectric (`LEGACY`, un shadow liviano sin
 * motor de texto de verdad) `Text`/`BasicText` con un `lineHeight` propio medía ~17,5 dp de alto
 * SIEMPRE, sin importar el estilo — se probó `cuerpo` (18 sp), `apoyo` (16 sp), `titulo` (20 sp) y
 * `cifra` (46 sp, con `autoSize`) por separado y los cuatro daban el mismo número, así que
 * comparar el alto de una tarjeta con texto real contra una de bloques (que sí honran su
 * `.height()`) no servía para nada — la fix round 1 anterior de este archivo lo trabajaba con un
 * margen de 60 dp y cuatro pruebas por tag en vez de uno de ±8 dp. Con `NATIVE` (el motor de texto
 * real de Robolectric, disponible desde hace varias versiones) la misma cifra midió 50,5 dp —cerca
 * de los 46 dp declarados—, así que el margen vuelve a ser el ±8 dp que pedía la especificación.
 * Las cuatro pruebas por tag se mantienen: son más baratas y no dependen de ninguna fuente.
 *
 * `waitForIdle()` funciona con un esqueleto en pantalla sin colgarse (ver el KDoc de
 * `Esqueleto.kt`), así que estas pruebas lo usan sin reparos.
 *
 * `LocalCargandoElInicio`: el hero y «Pregúntale a Movi» leen esta señal además de `data` (fix
 * round 1 — ver su KDoc). Toda prueba que quiera ver un esqueleto tiene que envolver el montaje en
 * `CompositionLocalProvider(LocalCargandoElInicio provides true)`; sin eso el default (`false`) hace
 * que ninguna pieza se muestre como esqueleto, sea cual sea `data`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class EsqueletosDelInicioTest {

    @get:Rule val composeRule = createComposeRule()

    /** Corte el 25, no mes de calendario: así `encabezadoDelPeriodo` tiene algo real que decir en
     * el estado cargado, y la comparación de alto de más abajo no mezcla el esqueleto del período
     * (fix round 1, punto 6) con el del resto del hero — los dos lados tienen la MISMA línea de
     * período, una de esqueleto y otra real, en vez de que solo uno la tenga. */
    private val ajustesDelPeriodo = PeriodSettings(cutoffDay = 25)
    private val periodoDePrueba = periodoDe(Clock.System.now().toEpochMilliseconds(), ajustesDelPeriodo)

    /** Sin préstamo, sin bien, sin cuenta condicionada: `muestraPatrimonio` da falso a propósito —
     * así el hero cargado no suma el renglón de patrimonio, que el esqueleto no representa, y la
     * comparación de alto queda pareja. */
    private val datosCargados = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 15_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 500_000)),
        spentByCategory = mapOf(CUOTA_CATEGORY to 5_000_000L),
        ajustesDePeriodo = ajustesDelPeriodo,
        periodoActual = periodoDePrueba,
        // Ola 1: con las lecturas del disponible el hero abre con «Te quedan» —el caso de todos los
        // días—, que es la forma que copia el esqueleto.
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = emptyMap(),
    )

    /** Monta el hero (y el resto del Inicio) con [data], marcando una carga en vuelo si [cargando]. */
    private fun montarHero(
        data: DashboardData,
        cargando: Boolean,
        definicion: ScreenDefinition = defaultDashboardDefinition(),
    ) {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides cargando) {
                    Box(Modifier.fillMaxSize()) {
                        SduiRenderer(
                            definition = definicion,
                            data = data,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    // Sin árbol fusionado: la tarjeta del hero es clickeable (lleva a «Tus períodos»), y un
    // clickeable fusiona a sus hijos: las etiquetas de adentro solo se ven en el árbol sin fusionar.
    private fun contarTag(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `el hero cargando mide lo mismo que el hero cargado, con veredicto y barra`() {
        // Las dos versiones del hero en una sola composición —no se puede llamar `setContent` dos
        // veces en la misma prueba— cada una en su mitad de una pantalla bien alta, para que
        // ninguna se recorte.
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalCargandoElInicio provides true) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            SduiRenderer(
                                definition = defaultDashboardDefinition(),
                                // Cargando: sin cuentas ni resumen, pero CON el período —el perfil
                                // pudo contestar antes que las cuentas—, para que la comparación de
                                // abajo no incluya el esqueleto del período (punto 6, otra prueba
                                // aparte lo cubre): los dos lados muestran la misma línea real.
                                data = DashboardData(ajustesDePeriodo = ajustesDelPeriodo, periodoActual = periodoDePrueba),
                                modifier = Modifier.fillMaxSize(),
                                onNavigate = {},
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = datosCargados,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Las piezas del esqueleto están, ni una de menos (vuelta 39: rótulo, cifra y detalle; el
        // veredicto salió de la tarjeta).
        assertEquals(1, contarTag(TAG_ESQUELETO_CIFRA_DEL_HERO))
        assertEquals(1, contarTag(TAG_ESQUELETO_DETALLE_DEL_HERO))
        // Y el lado cargado abre con «Te quedan», no con «Tu plata» de cifra grande.
        assertEquals(1, contarTag(TAG_CIFRA_TE_QUEDAN))

        val tarjetas = composeRule.onAllNodesWithTag(TAG_TARJETA_DEL_HERO)
        val altoCargando = tarjetas[0].getUnclippedBoundsInRoot().height
        val altoCargado = tarjetas[1].getUnclippedBoundsInRoot().height

        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "El hero cargando mide ${altoCargando.value} dp y cargado ${altoCargado.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 8 dp",
        )
    }

    /**
     * **Fix round 1**, reescrita en la ola R1. Las cuentas llegan antes que el resto: la cifra grande
     * —«Te quedan»— sigue de esqueleto hasta que lleguen sus lecturas, y el veredicto también. «Tu
     * plata» ya no está en el héroe: la cuenta que llegó se ve en el carrusel «Tus cuentas».
     */
    @Test
    fun `con cuentas pero sin resumen, la cifra grande sigue esqueleto y la cuenta ya está en el carrusel`() {
        val soloCuentas = DashboardData(
            accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 500_000)),
        )
        montarHero(soloCuentas, cargando = true)

        assertEquals(1, contarTag(TAG_ESQUELETO_CIFRA_DEL_HERO))
        assertEquals(1, contarTag(TAG_ESQUELETO_DETALLE_DEL_HERO))
        // Mientras carga, nada dice $0 (vuelta 39).
        assertEquals(0, composeRule.onAllNodesWithText("\$0", useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onAllNodesWithText("\$500.000", useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    /** Fix round 1, el caso reverso: resumen SÍ, cuentas NO. La cifra sigue esqueleto, nunca $0. */
    @Test
    fun `con resumen pero sin cuentas, la cifra sigue esqueleto`() {
        val soloResumen = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 15_000_000),
        )
        montarHero(soloResumen, cargando = true)

        assertEquals(1, contarTag(TAG_ESQUELETO_CIFRA_DEL_HERO))
        assertEquals(0, composeRule.onAllNodesWithText("\$0", useUnmergedTree = true).fetchSemanticsNodes().size)
        // La barra Entró/Salió salió del Inicio (vive en Plan y en «Tus períodos»).
        composeRule.onNodeWithText("Entró", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Salió", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * **Fix round 1.** Una carga en frío sin red que ya se rindió (`loading = false`, sin datos)
     * no puede dejar el esqueleto pulsando para siempre: sin `LocalCargandoElInicio` en `true`,
     * el hero cae al guion de antes de Task 7 y «Pregúntale a Movi» cae a las preguntas de
     * respaldo — nada de esqueleto sin nada en camino.
     */
    @Test
    fun `sin datos y sin carga en vuelo, no hay esqueleto — el guion de antes de Task 7`() {

        montarHero(DashboardData(), cargando = false, definicion = conPreguntale)
        assertEquals(0, contarTag(TAG_ESQUELETO_CIFRA_DEL_HERO))
        assertEquals(0, contarTag(TAG_ESQUELETO_VEREDICTO_DEL_HERO))
        assertEquals(0, contarTag(TAG_FILA_ESQUELETO_PREGUNTA))
        composeRule.onNodeWithText("—", useUnmergedTree = true).assertIsDisplayed()
        // Ola 3 · Hoy en una mirada: el chip dice la versión corta de cada una (la completa es la
        // que se manda; ver `PreguntaParaTocar.corta`).
        PREGUNTAS_PARA_TOCAR_DE_RESPALDO.forEach { pregunta ->
            composeRule.onNodeWithText(pregunta.corta, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /**
     * **Fix round 1, punto 6**, reescrita en la ola R1. El período ya no va en el héroe —lo dice el
     * encabezado, debajo del saludo—, así que saber el período no mueve el héroe: mide lo mismo con
     * el perfil en camino que con el período ya sabido.
     */
    @Test
    fun `el periodo no mueve el heroe, porque vive en el encabezado`() {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides true) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            SduiRenderer(
                                definition = defaultDashboardDefinition(),
                                data = DashboardData(), // sin período todavía
                                modifier = Modifier.fillMaxSize(),
                                onNavigate = {},
                            )
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            SduiRenderer(
                                definition = defaultDashboardDefinition(),
                                data = DashboardData(ajustesDePeriodo = ajustesDelPeriodo, periodoActual = periodoDePrueba),
                                modifier = Modifier.fillMaxSize(),
                                onNavigate = {},
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val tarjetas = composeRule.onAllNodesWithTag(TAG_TARJETA_DEL_HERO)
        val diferencia = abs(tarjetas[0].getUnclippedBoundsInRoot().height.value - tarjetas[1].getUnclippedBoundsInRoot().height.value)
        assertTrue(diferencia <= 1f, "el héroe cambió $diferencia dp al saberse el período")
    }

    /**
     * «Pregúntale a Movi» salió del Inicio por defecto (es una acción de la cuadrícula), pero una
     * definición de la generación 8 —la del Editor de pantallas, o la instantánea de un aparato— la
     * puede traer todavía, y se sigue pintando con sus esqueletos.
     */
    private val conPreguntale = defaultDashboardDefinition().let { d ->
        d.copy(sections = listOf(d.sections.first(), ScreenSection(type = "PREGUNTALE_A_MOVI", title = "Pregúntale a Movi")) + d.sections.drop(1))
    }

    @Test
    fun `sin cuentas ni resumen, Preguntale a Movi muestra tres esqueletos y no las preguntas de respaldo`() {
        montarHero(DashboardData(), cargando = true, definicion = conPreguntale)

        assertEquals(3, contarTag(TAG_FILA_ESQUELETO_PREGUNTA))
        // El relleno genérico (`PREGUNTAS_DE_RESPALDO`) es justamente lo que el esqueleto tiene que
        // reemplazar: sin datos, antes se veía como si fuera una respuesta real.
        PREGUNTAS_DE_RESPALDO.forEach { pregunta ->
            composeRule.onNodeWithText(pregunta, useUnmergedTree = true).assertDoesNotExist()
        }
        PREGUNTAS_PARA_TOCAR_DE_RESPALDO.forEach { pregunta ->
            composeRule.onNodeWithText(pregunta.corta, useUnmergedTree = true).assertDoesNotExist()
        }
        // El campo se puede usar igual, sin datos.
        composeRule.onNodeWithText("Escribe tu pregunta…", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `con cuentas y resumen, Preguntale a Movi muestra las preguntas y no el esqueleto`() {
        montarHero(datosCargados, cargando = false, definicion = conPreguntale)

        assertEquals(0, contarTag(TAG_FILA_ESQUELETO_PREGUNTA))
        // Ola 3: el chip dice la versión corta (ver `PreguntaParaTocar.corta`).
        preguntasParaTocar(datosCargados).forEach { pregunta ->
            composeRule.onNodeWithText(pregunta.corta, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // ── Ola D, Task 1: el hero, vacío, enseña ───────────────────────────────────────────────

    /**
     * `accounts` CONTESTÓ vacía (no `null` — ese es el esqueleto, cubierto en las pruebas de
     * arriba). El hero no puede decir «$0»: eso sería la afirmación más fuerte de la pantalla,
     * y sería falsa para quien todavía no anotó ni una cuenta.
     */
    @Test
    fun `con cuentas vacias, el hero no dice dollar cero y muestra el vacio que ensena`() {
        montarHero(DashboardData(accounts = emptyList()), cargando = false)

        composeRule.onNodeWithText("\$0", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Aquí vas a ver tu plata", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(com.jvillada.movi.ui.sdui.DETALLE_DEL_HEROE_VACIO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Crear mi primera cuenta", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Tocar el botón del vacío abre la MISMA hoja de crear cuenta que «Primeros pasos». */
    @Test
    fun `tocar el boton del hero vacio abre la hoja de crear cuenta`() {
        var seAbrioLaHoja = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = DashboardData(accounts = emptyList()),
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                        onShowCreateSheet = { seAbrioLaHoja = true },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Crear mi primera cuenta", useUnmergedTree = true).performClick()

        assertTrue(seAbrioLaHoja)
    }

    /** Con `accounts == null` (todavía no contestó), el hero sigue de esqueleto: nada de vacío. */
    @Test
    fun `con accounts null el hero sigue de esqueleto, no el vacio`() {
        montarHero(DashboardData(), cargando = true)

        assertEquals(1, contarTag(TAG_ESQUELETO_CIFRA_DEL_HERO))
        composeRule.onNodeWithText("Aquí vas a ver tu plata", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Crear mi primera cuenta", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * **Fix round 1.** Mismo patrón que «el hero cargando mide lo mismo que el hero cargado» de
     * más arriba, ahora entre el esqueleto (todavía no se sabe si hay cuentas) y el vacío que
     * enseña (contestó que no hay ninguna): el dueño ve una transición, no un salto. El vacío
     * quedó envuelto en un `Box` con [TAG_TARJETA_DEL_HERO] (ver `HeroDeUnVistazo`) justo para que
     * este test pueda reusar el mismo tag.
     *
     * El lado esqueleto fija el período (como el primer test de esta clase) para no mezclar la
     * línea reservada del período con la comparación — acá interesa solo cifra + veredicto +
     * barra + fila contra el vacío.
     */
    @Test
    fun `el hero cargando mide lo mismo que el hero vacio, sin cuentas`() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalCargandoElInicio provides true) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            SduiRenderer(
                                definition = defaultDashboardDefinition(),
                                data = DashboardData(ajustesDePeriodo = ajustesDelPeriodo, periodoActual = periodoDePrueba),
                                modifier = Modifier.fillMaxSize(),
                                onNavigate = {},
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = DashboardData(accounts = emptyList()),
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val tarjetas = composeRule.onAllNodesWithTag(TAG_TARJETA_DEL_HERO)
        val altoCargando = tarjetas[0].getUnclippedBoundsInRoot().height
        val altoVacio = tarjetas[1].getUnclippedBoundsInRoot().height

        val diferencia = abs(altoVacio.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "El hero cargando mide ${altoCargando.value} dp y el vacío (sin cuentas) mide " +
                "${altoVacio.value} dp — diferencia de $diferencia dp, el máximo son 8 dp",
        )
    }
}
