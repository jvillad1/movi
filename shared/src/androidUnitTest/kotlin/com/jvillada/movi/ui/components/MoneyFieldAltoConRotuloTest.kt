package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

private const val TAG_CAMPO_REAL = "money-field-real-para-medir"

/**
 * # `altoDeMoneyFieldConRotulo`: el número que reemplazó un `48.dp` puesto a ojo
 *
 * Ola B, tarea 9 (fix round 1). El esqueleto de Cuadre de saldos imita el campo «LO QUE DICE EL
 * BANCO» con un bloque sin teclado — no puede montar un [MoneyField] real, porque eso abriría
 * edición donde solo hace falta una forma. Antes su alto era `48.dp`, un número puesto a ojo que
 * un cambio futuro en `Movi.textos.apoyo` o `Movi.textos.monto` podía desalinear en silencio.
 * [altoDeMoneyFieldConRotulo] lo calcula desde esos mismos tokens; esta prueba confirma que el
 * cálculo mide lo mismo que el campo real, montando los dos en la misma composición (mismo truco
 * que `EsqueletosDelInicioTest` usa para comparar cargando contra cargado).
 *
 * `@GraphicsMode(NATIVE)` + `sdk = [34]`: hace falta el motor de texto real para que
 * `altoDeUnRenglon` mida algo parecido a lo que el campo real ocupa — ver el KDoc de
 * `Esqueleto.kt`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class MoneyFieldAltoConRotuloTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `el alto calculado mide lo mismo que un MoneyField real, vacio y con rotulo`() {
        var altoCalculado = 0f
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxWidth()) {
                    // Leído ACÁ, dentro de la misma composición que el campo real: así los dos
                    // corren con el mismo tema, la misma escala de letra y la misma densidad.
                    altoCalculado = altoDeMoneyFieldConRotulo().value
                    MoneyField(
                        value = null,
                        onValueChange = {},
                        label = "LO QUE DICE EL BANCO",
                        placeholder = "Sin cuadrar",
                        modifier = Modifier.testTag(TAG_CAMPO_REAL),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val altoReal = composeRule.onNodeWithTag(TAG_CAMPO_REAL).getUnclippedBoundsInRoot().height.value

        val diferencia = abs(altoReal - altoCalculado)
        assertTrue(
            diferencia <= 2f,
            "altoDeMoneyFieldConRotulo() calculó $altoCalculado dp y el campo real midió $altoReal dp " +
                "— diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }
}
