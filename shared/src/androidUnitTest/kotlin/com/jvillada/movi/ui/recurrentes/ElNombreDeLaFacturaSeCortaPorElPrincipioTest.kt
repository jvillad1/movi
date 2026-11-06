package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.PUNTOS_DEL_PRINCIPIO
import com.jvillada.movi.ui.components.ds.TituloVisibleDeFilaKey
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Revisión de la vuelta 136 (I2) · el nombre de una factura se corta por el principio
 *
 * Con la cifra a la derecha, a 360 dp y la letra de la app (×1,12), «Pago tarjeta Master Black 36…» perdía los
 * cuatro dígitos que la distinguen de su hermana en USD y «Cuota Hipoteca Papá (Davi…» el banco. Ahora la
 * derecha lleva solo la cifra y, si el nombre igual no cabe, se corta por el principio: «…Master Black 3684».
 * Sin el cambio, lo visible terminaba en «…» (o no había [TituloVisibleDeFilaKey]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElNombreDeLaFacturaSeCortaPorElPrincipioTest {

    @get:Rule val composeRule = createComposeRule()

    private val hipoteca = PagoDelPeriodo(
        ruleId = "credit_acc_hip", nombre = "Cuota Hipoteca Papá (Davibank)", monto = 2_228_152, pagado = false,
        diasParaVencer = 4, vence = "2026-11-10", categoria = "Cuota de crédito",
    )
    private val master = PagoDelPeriodo(
        ruleId = "rr_master", nombre = "Pago tarjeta Master Black 3684", monto = 2_228_152, pagado = false,
        diasParaVencer = 8, vence = "2026-11-14", categoria = "Tarjetas",
    )

    private fun montarYMirar() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Column(Modifier.fillMaxWidth()) {
                        for (pago in listOf(hipoteca, master)) {
                            FilaDeFactura(pago = pago, estado = estadoDeLaFactura(pago, null), extracto = null, onAbrir = {})
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        for ((nombre, cola) in listOf(hipoteca.nombre to "(Davibank)", master.nombre to "3684")) {
            // El lector oye el nombre entero…
            val nodo = composeRule.onAllNodesWithText(nombre, useUnmergedTree = true).fetchSemanticsNodes().single()
            // …y lo que se ve termina en lo que distingue la factura.
            val visible = nodo.config.getOrNull(TituloVisibleDeFilaKey)
            assertTrue(visible != null && visible.endsWith(cola), "«$nombre» se ve como «$visible»")
            assertTrue(visible == nombre || visible.startsWith(PUNTOS_DEL_PRINCIPIO), "«$visible» se corta por el principio")
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 dp los digitos y el banco quedan a la vista`() = montarYMirar()

    @Test
    @Config(sdk = [34], qualifiers = "w384dp-h800dp-xhdpi")
    fun `a 384 dp tambien`() = montarYMirar()
}
