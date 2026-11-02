package com.jvillada.movi.observabilidad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.Boton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vuelta 104 · **lo que cuesta un toque y que no se cuente dos veces.**
 *
 * - Anotar un toque es armar su id y encolarlo: mil seguidos, con un `Modifier` de varios elementos para
 *   leerle el tag, no pueden tardar lo que se nota (el tope es holgado: la máquina de CI no es esta).
 *   La cola no crece sin límite (la tapa `MAX_PENDIENTES`).
 * - La vuelta 100 anotaba a mano los toques de la guía, de Por revisar y el «abrir Agregar» como
 *   `TOQUE`: la autocaptura los cubre, así que ya no queda ninguno en el código de la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ElToqueNoCuestaNiSeRepiteTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun preparar() {
        Uso.almacenDePrueba = mutableMapOf()
        Uso.releerElInterruptor()
        Uso.ponerLaPila(listOf("Dashboard"))
    }

    @Test
    fun milToquesNoBloqueanNada() {
        val toque = ToqueDeUso(
            "boton",
            modificador = Modifier.padding(4.dp).testTag("t-mil").padding(2.dp),
            rotulo = "Guardar",
            datos = mapOf(ClaveDeUso.BOTON to "PRINCIPAL"),
        )
        Uso.toque(toque) // la primera arma los rótulos (una vez por proceso)
        val desde = System.nanoTime()
        repeat(1_000) { Uso.toque(toque) }
        val ms = (System.nanoTime() - desde) / 1_000_000
        println("ElToqueNoCuestaNiSeRepiteTest · 1.000 toques: $ms ms")
        assertTrue(ms < 1_500, "1.000 toques tardaron $ms ms")
        assertEquals(MAX_PENDIENTES, Uso.pendientes.size, "la cola se tapa en $MAX_PENDIENTES")
        assertEquals(setOf("toque:guardar"), Uso.pendientes.filter { it.nombre.startsWith("toque:") }.map { it.nombre }.toSet())
    }

    @Test
    fun milToquesDeVerdadSobreUnBotonSeAnotanTodos() {
        var veces = 0
        composeRule.setContent { MoviTheme { Column { Boton("Guardar", onClick = { veces++ }, modifier = Modifier.testTag("t-boton")) } } }
        val desde = System.nanoTime()
        repeat(200) { composeRule.onNodeWithTag("t-boton", useUnmergedTree = true).performClick() }
        val ms = (System.nanoTime() - desde) / 1_000_000
        println("ElToqueNoCuestaNiSeRepiteTest · 200 toques sobre un Boton: $ms ms")
        assertEquals(200, veces, "el botón respondió a todos")
        assertEquals(200, Uso.pendientes.count { it.nombre == "toque:guardar" }, "y cada uno quedó anotado una vez")
    }

    @Test
    fun yaNoQuedanToquesAnotadosAMano() {
        val raiz = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val carpetas = listOf("shared/src/commonMain", "shared/src/androidMain", "androidApp/src/main").map { File(raiz, it) }
        val aMano = carpetas.flatMap { c -> c.walkTopDown().filter { it.extension == "kt" }.toList() }
            .filter { "EventoDeUso.TOQUE" in it.readText() }
            .map { it.name }
        assertEquals(emptyList(), aMano, "un `TOQUE` a mano además del autocapturado se contaría dos veces")
        // Y el evento sigue en la lista cerrada para lo que manda un APK viejo.
        assertEquals(EventoDeUso.TOQUE, EventoDeUso.deNombre("TOQUE"))
    }
}
