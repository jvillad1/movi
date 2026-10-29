package com.jvillada.movi.ui.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.LLEGA_TU_SALARIO
import com.jvillada.movi.shared.model.MovimientoDeLaCaja
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.periodos.ROTULO_NETO_DEL_PERIODO
import com.jvillada.movi.ui.sdui.ROTULO_META_DE_HOY
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 28: un nombre por concepto
 *
 * La revisión encontró cinco cifras de «plata disponible» que no se distinguían. Cada una conserva su
 * cuenta y cambia el nombre, para que diga qué es:
 *
 * | dónde | antes | ahora |
 * |---|---|---|
 * | Plan, columna de hoy | «Hoy» | «Meta de hoy» |
 * | Tu plata, día a día (abajo) | «Al cierre: $11,9M» | «Saldo proyectado: $11,9M» (el lector oye «al cierre, el 24 oct») |
 * | Tu plata, día a día (el día más justo) | «llegas a $11,9M» | «tu saldo proyectado es $11,9M» |
 * | Tu plata, día a día (el supuesto) | «unos $26.523 por día» | «gastas en promedio $26.523 por día» |
 * | Tus períodos | «Te quedó» (rojo si negativo) | «Neto del período» (neutro si negativo) |
 *
 * Los textos se prueban acá y en sus pruebas puras (`CajaDelPeriodoTest`, `ElNetoDelPeriodoTest`); acá
 * además se mide que el renglón de abajo de la caja entre en un renglón a 360 dp con la letra de la app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class UnNombrePorConceptoTest {

    @get:Rule val composeRule = createComposeRule()

    private val caja = CajaProyectada(
        tuPlataHoy = 12_900_000L,
        gastoDiario = 26_523L,
        dias = listOf(DiaDeLaCaja("2026-09-25", 12_900_000L, emptyList()), DiaDeLaCaja("2026-10-24", 11_900_000L, emptyList())),
    )

    @Test
    fun `los rotulos nuevos`() {
        assertEquals("Meta de hoy", ROTULO_META_DE_HOY)
        assertEquals("Neto del período", ROTULO_NETO_DEL_PERIODO)
        // Vuelta 70: el renglón de abajo ya no repite la cifra (la dice la frase de puente, una vez).
        assertEquals(TOCA_UN_DIA, leyendaDelCierre(caja))
        assertEquals("Libre el 24 oct: \$11,9M", descripcionDelCierre(caja))
    }

    /**
     * El renglón de abajo de la caja va entre la fecha del primer día y la del último, en un renglón y
     * con «…» si no entra. A 360 dp con la letra ×1,12 tiene que entrar entero.
     */
    @Test
    fun `el renglon de abajo entra entero a 360 dp`() {
        var cabe = false
        var detalle = ""
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                val densidad = Density(base.density, base.fontScale * 1.12f)
                CompositionLocalProvider(LocalDensity provides densidad) {
                    val medidor = rememberTextMeasurer()
                    val estilo = Movi.textos.apoyo
                    fun ancho(texto: String) = medidor.measure(texto, estilo, density = densidad).size.width
                    // La tarjeta: el margen de la pantalla y el relleno de la superficie a cada lado; y las
                    // dos fechas con su espacio (ver `CuerpoDeLaCaja`).
                    val disponible = with(densidad) {
                        (360.dp - Medida.margenDePantalla * 2 - Medida.rellenoDeSuperficie * 2 - Movi.espacios.corto * 2).roundToPx()
                    } - ancho("25 sep") - ancho("24 oct")
                    // Lo más largo que puede decir: el salario más grande, o cómo leer la línea.
                    val conSalario = caja.copy(
                        dias = caja.dias.dropLast(1) + DiaDeLaCaja(
                            "2026-10-24", 999_900_000L, listOf(MovimientoDeLaCaja(LLEGA_TU_SALARIO, 999_900_000L)),
                        ),
                        hastaElSalario = true,
                    )
                    val pide = maxOf(ancho(leyendaDelCierre(conSalario)), ancho(leyendaDelCierre(caja)))
                    cabe = pide <= disponible
                    detalle = "pide $pide px y hay $disponible px"
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(cabe, "El renglón de abajo de la caja no entra a 360 dp: $detalle")
    }
}
