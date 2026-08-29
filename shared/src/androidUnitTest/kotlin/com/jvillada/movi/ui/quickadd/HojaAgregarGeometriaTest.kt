package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.MoviTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # La hoja de «Agregar», medida — no mirada.
 *
 * Lo que rompe esta hoja no es lógica: es **geometría**. Si el botón de guardar cae dentro de la
 * ventana; si el teclado numérico se movió entre dos estados. Nueve rondas de arreglos de esta
 * hoja fueron atrapadas por una persona midiendo píxeles a mano, y **ninguna** por una prueba,
 * porque hasta acá la única evidencia de seguridad de un commit era «BUILD SUCCESSFUL», que no
 * mira nada de eso.
 *
 * Corre en la JVM con Robolectric, dentro de `:shared:testDebugUnitTest`, que ya está en el set
 * de verificación. No hace falta ningún comando nuevo ni ningún emulador.
 *
 * ## LA REGLA: todo se prueba a tamaño de teléfono chico
 *
 * **Ninguna prueba de esta hoja vale a 800×1000.** Ahí entra todo, no se desborda nada, el
 * `verticalScroll` tiene `maxValue = 0` y las tres afirmaciones de abajo pasan sin ejercitar una
 * sola línea del código que las hace ciertas. Es exactamente por eso que nueve rondas no vieron
 * nada. Los tamaños que importan, medidos en el teléfono y en el navegador:
 *
 * | Ventana        | De dónde sale                     | Qué pasa ahí                                |
 * |----------------|-----------------------------------|---------------------------------------------|
 * | 411×731 dp     | el AVD `Movi_Sensor`              | la hoja se desborda; el botón queda recortado |
 * | 393×852 pt     | un iPhone 16                      | el botón entra entero                        |
 * | 800×1000 dp    | el navegador en el escritorio     | **no prueba nada**: sobran 150 dp            |
 *
 * Si agregas una prueba acá, ponele un `@Config(qualifiers = …)` de teléfono chico. Sin eso, es
 * una prueba que no prueba nada.
 *
 * ## Qué NO cubre esto (y hay que decirlo, porque este repo ya pagó caro los comentarios que
 * afirmaban de más)
 *
 * - **No prueba iOS ni la web.** Robolectric es Android. Las métricas de fuente, las áreas
 *   seguras y el reloj de cuadros son otros. El defecto de «no se podía guardar en un iPhone» se
 *   descubrió justamente en iOS, y un verde acá **no** lo habría descartado: acá no existen ni
 *   la barra de estado ni el indicador de inicio, que en un iPhone 16 se comen ~93 dp más de los
 *   852 — más de lo que sobra en la afirmación de [guardarSeVeEnteroSinNingunGesto].
 * - **No prueba el gesto real.** Los toques van por la acción semántica `OnClick`, no por un
 *   dedo: no hay *fling*, no hay *touch slop*, no hay arrastre. Justamente el arrastre sobre el
 *   teclado es el riesgo que la Ola 12 dejó anotado y sin cerrar, y esta clase **no lo toca**.
 *   (No es una elección de estilo: bajo Robolectric `performClick()` no llega al composable —
 *   ver la nota de [tocar].)
 * - **No prueba nada horizontal.** Robolectric no hace disposición de texto de verdad: el
 *   encabezado «Fecha» mide 3 dp de ancho acá. O sea que el defecto de «MANUAL partido en una
 *   letra por renglón» **no lo atrapa esta clase**. Lo vertical sí es confiable porque esta hoja
 *   está construida con altos fijos y `lineHeight` explícitos, a propósito.
 * - **No prueba colores, sombras ni nada visual.** Para eso harían falta imágenes de referencia,
 *   que no hay.
 *
 * ## El andamio
 *
 * [montarHoja] reproduce lo que hace `App.kt`: la hoja vive dentro del área de contenido, y
 * debajo queda la barra inferior de 64 dp (`MinBottomNav`), que la hoja no puede usar. Sin ese
 * hueco reservado la ventana da 64 dp de más y las medidas dejan de parecerse al teléfono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR)
class HojaAgregarGeometriaTest {

    @get:Rule val composeRule = createComposeRule()

    // ── Los tres defectos reales, uno por prueba ──────────────────────────────────────────

    /**
     * **1 — el defecto del iPhone: «Guardar movimiento» tiene que verse ENTERO sin ningún gesto.**
     *
     * En un iPhone la hoja no se desplazaba y el botón caía bajo el borde: se podía llenar el
     * formulario entero y quedarse sin forma de guardarlo. Se descubrió recién cuando alguien
     * corrió iOS por primera vez.
     *
     * Se afirma a 393×852 (un iPhone 16), que es el tamaño donde el botón **hoy** entra entero.
     * Ver el aviso de arriba: acá no existen las áreas seguras de iOS, así que esto guarda la
     * disposición, no el iPhone.
     *
     * El `assertSeVeEntero` compara los límites recortados contra los SIN recortar a propósito:
     * `assertIsDisplayed()` a secas **pasa con un botón cortado a la mitad**, y en el AVD eso es
     * literalmente lo que pasa (ver [guardarSeAlcanzaEnElTelefonoChico]). Una prueba que se
     * conformara con `assertIsDisplayed()` acá sería verde y no probaría nada.
     */
    @Test
    @Config(qualifiers = IPHONE_16)
    fun guardarSeVeEnteroSinNingunGesto() {
        montarHoja()
        composeRule.onNodeWithText(GUARDAR).assertSeVeEntero("«$GUARDAR»")
    }

    /**
     * **1b — en el AVD el botón NO entra, pero se alcanza.** Y eso hay que sostenerlo.
     *
     * Medido acá, hoy, a 411×731 con la barra inferior puesta: sin tocar nada se ven 23 dp de los
     * 54 del botón — **31 dp recortados**. O sea que la afirmación de arriba **no vale** a este
     * tamaño, y decir lo contrario sería la clase de promesa de más que esta clase vino a evitar.
     * Lo que sí vale, y es la promesa que hizo la Ola 12, es que se pueda LLEGAR: en el APK 1.7
     * el botón quedaba entero afuera y no había ningún gesto que lo trajera.
     *
     * No se afirma «está recortado»: esa prueba se pondría roja el día que alguien lo arregle,
     * que es exactamente al revés de lo que queremos.
     */
    @Test
    fun guardarSeAlcanzaEnElTelefonoChico() {
        montarHoja()
        composeRule.onNodeWithText(GUARDAR).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(GUARDAR).assertSeVeEntero("«$GUARDAR» tras desplazar")
    }

    /**
     * **2 — el invariante de las nueve rondas: el teclado numérico no se mueve.**
     *
     * Es el que habría atajado el «escribías 0 y salía 8» original. La hoja está anclada abajo,
     * así que cualquier cambio de alto le corre TODAS las teclas bajo el dedo: el segundo toque
     * en el mismo punto cae en otra tecla, sin ningún aviso.
     *
     * Se mide la tecla «9» antes de abrir un sub-picker y después de cerrarlo, y se exige que sea
     * el MISMO rectángulo. Se hace con la hoja **ya desplazada hasta el botón de guardar**, que
     * es el caso difícil: ahí el sub-picker recorta el desplazamiento contra su propio
     * `maxValue = 0` y hay que restaurarlo a mano (ver `recordarScroll`/`pasarA` en
     * `QuickAddScreen`). Con la hoja en su lugar el invariante se cumple solo y la prueba no
     * ejercitaría nada — por eso primero se afirma que el desplazamiento de verdad ocurrió.
     *
     * Los tres sub-pickers se abren desde tres filas distintas: el embudo `pasarA` existe
     * justamente porque basta que UNO se olvide de grabar el desplazamiento para que el teclado
     * se mueva en ese camino y nada más.
     */
    @Test
    fun elTecladoNoSeMueveAlAbrirYCerrarUnSubPicker() {
        montarHoja()
        val enReposo = tecla9()

        composeRule.onNodeWithText(GUARDAR).performScrollTo()
        composeRule.waitForIdle()
        val desplazado = tecla9()
        assertTrue(
            "La hoja no se desplazó, así que esta prueba no está probando nada. " +
                "¿Se agrandó la ventana de la prueba? (ver la regla del teléfono chico)",
            abs(desplazado.top.value - enReposo.top.value) > 1f,
        )

        SUB_PICKERS.forEach { (fila, titulo) ->
            tocar(fila)
            cerrarSubPicker()
            val despues = tecla9()
            assertTrue(
                "La tecla «9» se movió al ir y volver del sub-picker «$titulo»: " +
                    "antes $desplazado, después $despues. Ese es el «escribías 0 y salía 8».",
                mismoRect(desplazado, despues),
            )
        }
    }

    /**
     * **3 — la losa vacía: el encabezado del sub-picker se ve al abrirlo.**
     *
     * Al sub-picker se le fija un alto mínimo igual al del hueco visible para que la hoja no
     * cambie de tamaño (ese es el arreglo de la prueba 2). El precio de equivocarse en ese
     * cálculo es que el sub-picker abra desplazado y lo primero que se vea sea una losa vacía,
     * sin título y sin la X para salir — y la X es la única salida en iOS.
     *
     * Solo se afirma lo vertical: acá el ancho del texto es ficticio (ver el aviso de arriba).
     */
    @Test
    fun elEncabezadoDelSubPickerSeVeAlAbrirlo() {
        // Una sola vez: `setContent` no se puede llamar dos veces sobre la misma regla, así que
        // los tres se recorren abriendo y cerrando sobre la misma hoja.
        montarHoja()
        SUB_PICKERS.forEach { (fila, titulo) ->
            tocar(fila)
            // Sin `useUnmergedTree` el texto lo absorbe el nodo fusionado de la hoja entera
            // (el `clickable` de la Column fusiona a sus descendientes) y se mediría la hoja,
            // no el encabezado.
            composeRule.onNodeWithText(titulo, useUnmergedTree = true)
                .assertSeVeEntero("el encabezado «$titulo»")
            composeRule.onNodeWithTag(TAG_CERRAR_SUB_PICKER).assertSeVeEntero("la X de «$titulo»")
            cerrarSubPicker()
        }
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    /**
     * La hoja como la monta `App.kt`: dentro del área de contenido, con la barra inferior de
     * 64 dp ocupando su lugar debajo. Ese hueco es la diferencia entre medir un teléfono y medir
     * una ventana que no existe.
     *
     * `QuickAddScreen` pide las cuentas al abrirse; acá esa llamada falla (no hay base ni red) y
     * la hoja lo absorbe con su `runCatching`, que es el mismo camino que en un teléfono sin
     * señal. La hoja queda sin cuentas y con el botón de guardar deshabilitado — no importa: lo
     * que se mide es dónde CAE el botón, no si guarda.
     */
    private fun montarHoja() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {})
                    }
                    Spacer(Modifier.height(ALTO_BARRA_INFERIOR))
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * **Por qué `performSemanticsAction` y no `performClick`.**
     *
     * Bajo Robolectric, `performClick()` sobre estas filas no llega al composable: no tira
     * ninguna excepción, simplemente el estado no cambia y el sub-picker no abre — o sea que una
     * prueba escrita con `performClick` sería verde y no habría probado nada. La acción
     * semántica sí llega, a costa de saltearse el *hit testing* real (ver el aviso de arriba).
     */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun cerrarSubPicker() {
        composeRule.onNodeWithTag(TAG_CERRAR_SUB_PICKER)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /**
     * La tecla «9» del teclado numérico — la caja tocable de 50 dp, no la letra.
     *
     * Se mide con la hoja SIN ningún sub-picker abierto, y eso no es un detalle: el calendario
     * del sub-picker de Fecha tiene su propio día «9», así que medir «9» con el picker abierto
     * mediría otra cosa.
     */
    private fun tecla9(): DpRect = composeRule.onNodeWithText("9").getBoundsInRoot()

    /**
     * Se ve **entero**: dentro de la ventana y sin que ningún ancestro le recorte un pedazo.
     *
     * `assertIsDisplayed()` solo exige que asome algo. Un botón cortado al 43 % lo pasa — es el
     * caso real del AVD. Por eso además se comparan los límites recortados contra los enteros.
     */
    private fun SemanticsNodeInteraction.assertSeVeEntero(que: String) {
        assertIsDisplayed()
        val recortado = getBoundsInRoot()
        val entero = getUnclippedBoundsInRoot()
        assertTrue(
            "$que no se ve entero: se ve $recortado de $entero. " +
                "Falta ${entero.bottom.value - recortado.bottom.value} dp abajo.",
            mismoRect(recortado, entero),
        )
    }

    private fun mismoRect(a: DpRect, b: DpRect): Boolean =
        abs(a.left.value - b.left.value) < TOLERANCIA_DP &&
            abs(a.top.value - b.top.value) < TOLERANCIA_DP &&
            abs(a.right.value - b.right.value) < TOLERANCIA_DP &&
            abs(a.bottom.value - b.bottom.value) < TOLERANCIA_DP
}

/** El AVD `Movi_Sensor`: 411×731 dp. Ahí la hoja se corta en la fila «7 8 9». */
private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"

/** Un iPhone 16: 393×852 pt. Sin sus áreas seguras — Robolectric no las modela. */
private const val IPHONE_16 = "w393dp-h852dp-xhdpi"

/** `MinBottomNav.kt:63` — leído del código, no estimado. */
private val ALTO_BARRA_INFERIOR = 64.dp

private const val GUARDAR = "Guardar movimiento"

/** Medio dp: alcanza para el ruido de coma flotante y no para esconder un salto de verdad. */
private const val TOLERANCIA_DP = 0.5f

/**
 * Los tres sub-pickers que se abren desde el editor, como `fila a encabezado`. La fila «Fecha»
 * es la pastilla que dice «Hoy» al lado del monto; la de nota dice «Agregar nota…» mientras esté
 * vacía. (El cuarto, «Cuenta», abre una lista que sin cuentas cargadas no tiene nada que medir.)
 */
private val SUB_PICKERS = listOf(
    "Hoy" to "Fecha",
    "Agregar nota…" to "Nota",
    "Categoría" to "Categoría",
)
