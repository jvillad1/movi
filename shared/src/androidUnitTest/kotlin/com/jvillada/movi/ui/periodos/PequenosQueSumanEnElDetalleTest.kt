package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.GastoDeCategoria
import com.jvillada.movi.shared.model.PequenosQueSuman
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.formatCOP
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **«Pequeños que suman» en el detalle de un período**: se pinta con lo que mandó el server —«27
 * compras en Juan Valdez · $186.300»— y no aparece cuando ningún grupo suma algo que importe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class PequenosQueSumanEnElDetalleTest {

    @get:Rule val composeRule = createComposeRule()

    private val septiembre = DetalleDePeriodo(
        resumen = ResumenDePeriodo(
            id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-23",
            entradas = 9_000_000L, salidas = 2_000_000L, movimientos = 40,
        ),
        porCategoria = listOf(GastoDeCategoria("Comida", 600_000L)),
    )

    private val perfil = UserProfile(id = "u1", email = "a@b.c", name = "Juan", avatarColor = "#FF0000", periodCutoffDay = 25)

    private fun montar(detalle: DetalleDePeriodo) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo = detalle
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun getAccounts() = emptyList<com.jvillada.movi.shared.model.Account>()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    DetalleDePeriodoScreen(onNavigate = {}, id = "2026-09", hoy = LocalDate(2026, 10, 2))
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("En qué se fue") }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun filas(): Int =
        composeRule.onAllNodesWithTag(TAG_PEQUENO_QUE_SUMA, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `los gastos hormiga se pintan con cuantas veces y cuanto suman`() {
        montar(
            septiembre.copy(
                pequenosQueSuman = listOf(
                    PequenosQueSuman("Juan Valdez", porComercio = true, veces = 27, total = 186_300L),
                    PequenosQueSuman("Transporte", porComercio = false, veces = 9, total = 142_000L),
                ),
            ),
        )
        assertTrue(hay("Pequeños que suman"))
        assertEquals(2, filas())
        assertTrue(hay("27 compras en Juan Valdez"))
        assertTrue(hay("9 gastos chicos de Transporte"))
        assertTrue(hay(formatCOP(186_300L)))
        assertTrue(hay("Gastos de hasta \$50.000 que se repiten 3 veces o más."))
    }

    @Test
    fun `sin grupos que importen la seccion no aparece`() {
        montar(septiembre)
        assertTrue(!hay("Pequeños que suman"))
        assertEquals(0, filas())
    }
}
