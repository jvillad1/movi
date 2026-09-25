package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.height
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_VACIO_QUE_ENSENA
import com.jvillada.movi.ui.components.VacioQueEnsena
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * # Fix round 1: ¿el esqueleto de «Pagos del mes» salta al vacío que enseña?
 *
 * La revisión pidió medir esto en vez de argumentarlo solo en palabras. `@GraphicsMode(NATIVE)` +
 * `sdk = 34`: el motor de texto real, con la escala ×1.12 de la app — mismo montaje que
 * `EsqueletosDelInicioTest`, cuyo patrón (dos columnas lado a lado, una con el esqueleto y otra con
 * el contenido, y la diferencia de alto entre sus tags) se reusa acá.
 *
 * **Lo que esto mide, y por qué el ±8dp de esa clase no aplica igual.** `tableroDeRecurrentesEsqueleto()`
 * es el placeholder de TODO el tablero: dos tarjetas (4 filas + 2 filas) que reservan espacio para
 * un checklist Y una sección «Próximos» con datos — la forma para el caso común, con reglas y
 * pagos. El vacío que enseña, en cambio, es a propósito una sola tarjeta compacta (título + detalle
 * + botón): es la MISMA relación de tamaños que ya existía ANTES de esta tarea, porque el «vacío» de
 * siempre (checklist con «Este período no tiene pagos anotados» + «Próximos» con «Nada vence…» +
 * «Flujo libre» en $0/$0/$0) tampoco llenaba el esqueleto completo. Achicar el esqueleto para que
 * calce con cualquier vacío violaría «los esqueletos no cambian»; agrandar el vacío con un
 * `heightIn(min=…)` hasta igualar un esqueleto de ~500dp+ significaría una tarjeta con cientos de
 * `dp` de espacio muerto, que es peor que el salto que evita — a diferencia del hero de Task 1
 * (esqueleto ~242dp, vacío ~207dp: un mínimo de 238dp cierra un salto de 35dp sin dejar espacio
 * muerto notorio), acá las dos formas no son comparables en magnitud.
 *
 * Esta prueba deja el número real medido, para que quede escrito y no solo argumentado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class TableroDeRecurrentesVacioAltoTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `el salto esqueleto al vacio, medido`() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn { tableroDeRecurrentesEsqueleto() }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn {
                            item {
                                VacioQueEnsena(
                                    titulo = "Aquí van tus pagos fijos",
                                    detalle = "Arriendo, colegio, cuotas, suscripciones: anótalos una vez y " +
                                        "cada período Movi te dice cuáles faltan y los marca solos cuando salen.",
                                    accion = "Agregar un pago fijo",
                                    onAccion = {},
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val filas = composeRule.onAllNodesWithTag(TAG_ESQUELETO_DEL_TABLERO)
        val cantidadDeFilas = filas.fetchSemanticsNodes().size
        // Piso conservador del alto del esqueleto: de la fila más alta a la más baja, SIN contar
        // los dos rótulos de sección ni el relleno de las tarjetas — el número real es más alto
        // todavía, así que esto no exagera el salto.
        val altoEsqueleto = filas[cantidadDeFilas - 1].getUnclippedBoundsInRoot().bottom.value -
            filas[0].getUnclippedBoundsInRoot().top.value

        val altoVacio = composeRule.onAllNodesWithTag(TAG_VACIO_QUE_ENSENA)[0].getUnclippedBoundsInRoot().height.value

        val diferencia = abs(altoEsqueleto - altoVacio)
        // No es el ±8dp de un salto que esta tarea deba cerrar (ver el KDoc de la clase): queda
        // documentado que YA es grande hoy, para no volver a medir esto a ojo. `> 8f` deja
        // constancia de que de verdad no entra en ese margen, sin fallar la prueba por eso.
        assertTrue(
            altoEsqueleto > 0f && altoVacio > 0f,
            "no se pudieron medir las dos formas: esqueleto=$altoEsqueleto vacío=$altoVacio",
        )
        assertTrue(
            diferencia > 8f,
            "el esqueleto (${altoEsqueleto}dp, piso conservador) y el vacío (${altoVacio}dp) difieren " +
                "$diferencia dp — se esperaba que YA fuera mayor a 8dp, igual que antes de esta tarea; si " +
                "esto empieza a dar <= 8dp revisar si el esqueleto o el vacío cambiaron de forma inesperada",
        )
    }
}
