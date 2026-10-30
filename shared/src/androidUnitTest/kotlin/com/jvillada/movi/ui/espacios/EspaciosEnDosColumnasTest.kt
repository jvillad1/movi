package com.jvillada.movi.ui.espacios

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.disposicionDe
import com.jvillada.movi.ui.destinos.TAG_ACCESO_A_PERSONAS_Y_COMERCIOS
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Espacios en dos columnas (vuelta 33), montado DENTRO de la cáscara y medido
 *
 * A 1.280 px Espacios era una columna de ~690 dp y «Pedirle a Caro» un botón de 656. Ahora: el espacio,
 * «Quién puso qué» y el saldo a la izquierda; «Gastos» con «Anotar lo que pagó Caro» y «Personas y
 * comercios» a la derecha; y ningún botón más ancho que [Medida.anchoMaximoDeBoton]. A 390 dp, igual
 * que antes. Mismo arnés que `PatrimonioEnColumnasEnPantallaTest` (el rail de verdad, la letra ×1,12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EspaciosEnDosColumnasTest {

    @get:Rule val composeRule = createComposeRule()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun gasto(id: String, pagadoPor: String, monto: Long) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
        descripcion = "Mercado", fecha = 1_761_000_000_000L, partes = partesDelGasto(monto, casa, pagadoPor),
    )

    // Juan puso más: Caro le debe, y el principal del saldo es «Pedirle a Caro».
    private val gastos = listOf(gasto("g1", MIEMBRO_YO, 2_100_000), gasto("g2", "dst_caro", 1_320_000))
    private val detalle = DetalleDelEspacio(
        espacio = casa,
        saldo = saldoDelEspacio(casa, gastos, emptyList()),
        delPeriodo = saldoDelEspacio(casa, gastos, emptyList()),
        gastos = gastos,
    )

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEspacios(): List<Espacio> = listOf(casa)
            override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = detalle
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
        }
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Espacios,
                    activeTab = NavTab.TU,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    EspaciosScreen(onNavigate = {})
                }
            }
        }
        composeRule.waitUntil(5_000) { hayTag(TAG_PEDIRLE) }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun dentro(adentro: Rect, columna: Rect): Boolean =
        adentro.left >= columna.left - 0.5f && adentro.right <= columna.right + 0.5f

    @Test
    fun `Espacios es un tablero`() {
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Espacios))
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Espacio("esp_casa")))
    }

    /** 1280 − 216 del rail = 1064 de panel: dos columnas de 508. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp el saldo va a la izquierda y los gastos a la derecha`() {
        montar()
        val izquierda = limites(TAG_COLUMNA_IZQUIERDA_DEL_ESPACIO)
        val derecha = limites(TAG_COLUMNA_DERECHA_DEL_ESPACIO)
        assertTrue(derecha.left >= izquierda.right - 0.5f, "$izquierda / $derecha")
        assertEquals(izquierda.top, derecha.top, 0.5f)
        assertEquals(izquierda.width, derecha.width, 0.5f, "las dos miden lo mismo")
        assertEquals(1_064f, izquierda.width + derecha.width, 0.5f, "y llenan el panel")

        assertTrue(dentro(limites(TAG_SALDO_DEL_ESPACIO), izquierda), "el saldo con «Pedirle a Caro» va a la izquierda")
        assertTrue(dentro(limites(TAG_PEDIRLE), izquierda))
        // La tarjeta llena su columna aunque su botón tenga tope: antes medía lo que el botón.
        assertTrue(
            limites(TAG_SALDO_DEL_ESPACIO).width > Medida.anchoMaximoDeBoton.value + 2 * Medida.rellenoDeSuperficie.value + 0.5f,
            "la tarjeta del saldo mide ${limites(TAG_SALDO_DEL_ESPACIO).width}",
        )
        val primerGasto = composeRule.onAllNodesWithTag(TAG_GASTO_DEL_ESPACIO, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue(dentro(primerGasto, derecha), "los gastos van a la derecha")
        assertTrue(dentro(limites(TAG_ANOTAR_LO_QUE_PAGO_OTRO), derecha), "con «Anotar lo que pagó Caro»")
        // Vuelta 73: «Personas y comercios» ya no vive al final de un grupo (es una fila de Tú).
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ACCESO_A_PERSONAS_Y_COMERCIOS, useUnmergedTree = true).fetchSemanticsNodes().size)

        // Ningún botón se estira más que un teléfono grande (antes: 656 dp).
        listOf(TAG_PEDIRLE, TAG_ANOTAR_LO_QUE_PAGO_OTRO).forEach {
            assertTrue(limites(it).width <= Medida.anchoMaximoDeBoton.value + 0.5f, "$it mide ${limites(it).width}")
        }
    }

    /** A 390 dp no cambia nada: una columna, y el botón llena su tarjeta. */
    @Test
    @Config(qualifiers = "w390dp-h900dp-mdpi")
    fun `a 390 dp una sola columna, como siempre`() {
        montar()
        assertTrue(!hayTag(TAG_COLUMNA_IZQUIERDA_DEL_ESPACIO) && !hayTag(TAG_COLUMNA_DERECHA_DEL_ESPACIO))
        val saldo = limites(TAG_SALDO_DEL_ESPACIO)
        val pedir = limites(TAG_PEDIRLE)
        assertEquals(saldo.width - 2 * Medida.rellenoDeSuperficie.value, pedir.width, 0.5f, "el botón llena su tarjeta")
        // El saldo y los gastos, uno debajo del otro.
        val primerGasto = composeRule.onAllNodesWithTag(TAG_GASTO_DEL_ESPACIO, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue(primerGasto.top > saldo.bottom)
    }
}
