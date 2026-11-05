package com.jvillada.movi.ui.seguros

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CobroDelSeguro
import com.jvillada.movi.shared.model.SeguroDetectado
import com.jvillada.movi.shared.model.SegurosQuePagas
import com.jvillada.movi.shared.model.TipoDeSeguro
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.mas.LoTuyo
import com.jvillada.movi.ui.mas.TAG_FILA_TUS_SEGUROS
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 132 · «Tus seguros» en pantalla
 *
 * La fila de Tú con lo que pagas al mes, la lista por tipo, el vacío, «No es un seguro» que lo saca,
 * «Me interesa» que se registra una sola vez, ningún amarillo y nada cortado a 360 dp con la letra del
 * teléfono al 130 % (1,456). Sin la pantalla, nada de esto compila.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class TusSegurosScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val vidaDelCredito = SeguroDetectado(
        clave = "credito:acc_vehiculo", tipo = TipoDeSeguro.VIDA_DEL_CREDITO, nombre = "Seguro de vida · Vehículo 4083",
        cuentaId = "acc_vehiculo", cuentaNombre = "Vehículo 4083", alMes = 89_100, ultimoCobro = "2026-11-03",
        dentroDeLaCuota = true, creditoId = "acc_vehiculo",
    )
    private val deLaTarjeta = SeguroDetectado(
        clave = "acc_master:seguro_vida_deudor_tc", tipo = TipoDeSeguro.DE_TARJETA, nombre = "Seguro de la tarjeta Master Black",
        cuentaId = "acc_master", cuentaNombre = "Master Black", alMes = 32_000, ultimoCobro = "2026-11-02",
        cobros = listOf(CobroDelSeguro("ev_seg_1", "2026-11-02", 32_000, "SEGURO VIDA DEUDOR TC")),
    )
    private val soat = SeguroDetectado(
        clave = "acc_ahorros:pago_pse_soat_sura", tipo = TipoDeSeguro.AUTO, nombre = "SOAT", cuentaId = "acc_ahorros",
        cuentaNombre = "Ahorros 8133", alMes = 100_000, mesesEntreCobros = 12, montoDelCiclo = 1_200_000, ultimoCobro = "2026-03-15",
        cobros = listOf(CobroDelSeguro("ev_soat", "2026-03-15", 1_200_000, "PAGO PSE SOAT SURA")),
    )
    private val eps = SeguroDetectado(
        clave = "acc_ahorros:pago_sura_eps", tipo = TipoDeSeguro.OTRO, nombre = "Pago Sura Eps", cuentaId = "acc_ahorros",
        cuentaNombre = "Ahorros 8133", alMes = 380_000, ultimoCobro = "2026-10-30", concluyente = false,
        cobros = listOf(CobroDelSeguro("ev_eps", "2026-10-30", 380_000, "PAGO SURA EPS")),
    )

    private val conSeguros = SegurosQuePagas(
        seguros = listOf(vidaDelCredito, deLaTarjeta, soat),
        porConfirmar = listOf(eps),
        alMes = 221_100,
        alAno = 89_100L * 12 + 32_000L * 12 + 1_200_000,
        conAnuales = true,
    )

    private inner class Repo(var respuesta: SegurosQuePagas) : RepositorioDePrueba() {
        val respuestas = mutableListOf<Pair<String, Boolean>>()
        var interesados = 0
        override suspend fun getSeguros(enLaPantalla: Boolean): SegurosQuePagas = respuesta
        override suspend fun responderSiEsUnSeguro(clave: String, esUnSeguro: Boolean) {
            respuestas += clave to esUnSeguro
            respuesta = respuesta.copy(
                seguros = respuesta.seguros.filterNot { it.clave == clave && !esUnSeguro },
                porConfirmar = respuesta.porConfirmar.filterNot { it.clave == clave },
            )
        }
        override suspend fun meInteresaLaAyudaConLosSeguros() { interesados++ }
    }

    private fun montar(escala: Float = 1f, contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                    Box(Modifier.fillMaxSize()) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun layoutDe(texto: String): TextLayoutResult {
        val nodo = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().first()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    private fun amarillos(): Int {
        fun cuenta(n: SemanticsNode): Int =
            (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf(::cuenta)
        return cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    @Test
    fun `la fila de Tu dice lo que pagas al mes, o invita a mirar`() {
        Repositories.sustitutoDePrueba = Repo(conSeguros)
        montar { Box(Modifier.padding(horizontal = Medida.margenDePantalla)) { LoTuyo(onNavigate = {}) } }
        composeRule.waitUntil(5_000) { hay("221.100 al mes") }
        composeRule.onNodeWithTag(TAG_FILA_TUS_SEGUROS, useUnmergedTree = true).assertExists()
        assertTrue(hay(TITULO_TUS_SEGUROS))
    }

    @Test
    fun `sin datos la fila de Tu dice mira cuales pagas`() {
        Repositories.sustitutoDePrueba = Repo(SegurosQuePagas())
        montar { LoTuyo(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay(SIN_DATOS_DE_SEGUROS) }
        assertEquals(SIN_DATOS_DE_SEGUROS, subtituloDeLaFilaDeSeguros(null))
    }

    @Test
    fun `la lista va por tipo, con los que no se sabe aparte y sin amarillo`() {
        Repositories.sustitutoDePrueba = Repo(conSeguros)
        montar { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay("Seguro de vida · Vehículo 4083") }
        listOf("Pagas", "al mes en seguros", DE_DONDE_SALEN_LOS_SEGUROS, "Dentro de tus créditos", "De tus tarjetas", "Vehículo",
            "Seguro de la tarjeta Master Black", "SOAT", "al año", "Último cobro: 2 nov", TITULO_SON_SEGUROS, "Pago Sura Eps",
            PREGUNTA_DE_AYUDA, TEXTO_DE_AYUDA, MOVI_NO_VENDE_SEGUROS,
        ).forEach { assertTrue(hay(it), "falta «$it»") }
        assertTrue(hay("los anuales, repartidos en 12 meses"))
        assertFalse(hay(VACIO_DE_SEGUROS))
        assertEquals(0, amarillos(), "aquí no hay acción principal")
    }

    @Test
    fun `sin seguros dice como los veria`() {
        Repositories.sustitutoDePrueba = Repo(SegurosQuePagas())
        montar { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay(VACIO_DE_SEGUROS) }
        assertTrue(hay("subas el extracto"))
        assertTrue(hay(MOVI_NO_VENDE_SEGUROS))
        assertEquals(0, amarillos())
    }

    @Test
    fun `no es un seguro lo saca de la lista`() {
        val repo = Repo(conSeguros)
        Repositories.sustitutoDePrueba = repo
        montar { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay("Seguro de la tarjeta Master Black") }
        composeRule.onNodeWithText("Seguro de la tarjeta Master Black", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) { hay("Los cobros que lo prueban") }
        composeRule.onNodeWithTag(TAG_NO_ES_UN_SEGURO, useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) { !hay("Seguro de la tarjeta Master Black") }
        assertEquals(listOf(deLaTarjeta.clave to false), repo.respuestas)
    }

    @Test
    fun `si en un no concluyente lo confirma`() {
        val repo = Repo(conSeguros)
        Repositories.sustitutoDePrueba = repo
        montar { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay("Pago Sura Eps") }
        composeRule.onNodeWithTag(TAG_SI_ES, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(5_000) { repo.respuestas.isNotEmpty() }
        assertEquals(listOf(eps.clave to true), repo.respuestas)
    }

    @Test
    fun `me interesa se registra una sola vez y da las gracias`() {
        val repo = Repo(conSeguros)
        Repositories.sustitutoDePrueba = repo
        montar { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay(ME_INTERESA) }
        composeRule.onNodeWithTag(TAG_ME_INTERESA, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(5_000) { hay(GRACIAS_TE_AVISAREMOS) }
        // Ya no hay botón que tocar: el texto ocupa su lugar.
        composeRule.onNodeWithTag(TAG_ME_INTERESA, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, repo.interesados)
    }

    @Test
    fun `quien ya dijo que le interesa ve las gracias`() {
        Repositories.sustitutoDePrueba = Repo(conSeguros.copy(meInteresa = true))
        montar { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay(GRACIAS_TE_AVISAREMOS) }
    }

    @Test
    fun `a 360 dp con la letra al 130 por ciento nada se corta`() {
        Repositories.sustitutoDePrueba = Repo(conSeguros)
        montar(ESCALA_DEL_TELEFONO_AL_130) { TusSegurosScreen(onNavigate = {}) }
        composeRule.waitUntil(5_000) { hay("Seguro de vida · Vehículo 4083") }
        listOf(
            "Seguro de vida · Vehículo 4083", "Seguro de la tarjeta Master Black", "Pago Sura Eps",
            DE_DONDE_SALEN_LOS_SEGUROS, PREGUNTA_DE_AYUDA, TEXTO_DE_AYUDA, MOVI_NO_VENDE_SEGUROS, TITULO_SON_SEGUROS,
            lineaDeLaFilaDelSeguro(deLaTarjeta), ME_INTERESA,
        ).forEach { texto ->
            val layout = layoutDe(texto)
            (0 until layout.lineCount).forEach { assertFalse(layout.isLineEllipsized(it), "«$texto» sin «…»") }
            assertFalse(layout.multiParagraph.didExceedMaxLines, "«$texto» entero")
        }
    }
}
