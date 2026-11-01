package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.platform.HojaDeCompartir
import com.jvillada.movi.platform.LocalHojaDeCompartir
import com.jvillada.movi.shared.model.FiltroDeGmail
import com.jvillada.movi.shared.model.NOMBRE_DEL_ARCHIVO_DEL_FILTRO
import com.jvillada.movi.shared.model.RemitenteFinanciero
import com.jvillada.movi.shared.model.filtroDeGmailPara
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 93 · la guía del reenvío con el filtro de Movi
 *
 * `ReenvioPorCorreo` (la pieza del primer uso y de Ajustes → «Recibe tus alertas por correo») en tres
 * pasos: la dirección de Movi, el código de confirmación que Gmail le manda, y el filtro (la búsqueda para
 * copiar y, en la web, el XML para importar). Sin el filtro (un server viejo) la guía sigue entera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class FiltroDeGmailEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val direccionDeMovi = "alertas+3f2a9c1d@movi.resend.app"

    /** Una hoja de mentira: anota lo copiado y lo descargado. `web` = puede descargar, como el navegador. */
    private class HojaDePrueba(web: Boolean) : HojaDeCompartir {
        override val abreLaHojaDelSistema: Boolean = false
        override val puedeDescargar: Boolean = web
        val copiados = mutableListOf<String>()
        val descargas = mutableListOf<Triple<String, String, String>>()
        override fun compartir(texto: String) { copiados += texto }
        override fun copiar(texto: String) { copiados += texto }
        override fun descargar(nombre: String, contenido: String, tipo: String): Boolean {
            descargas += Triple(nombre, contenido, tipo)
            return true
        }
    }

    private fun montar(filtro: FiltroDeGmail?, hoja: HojaDePrueba) {
        val direccion = DireccionDeMovi().apply {
            leida = true
            this.direccion = direccionDeMovi
            this.filtro = filtro
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalHojaDeCompartir provides hoja) {
                MoviTheme {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ReenvioPorCorreo(direccion) }
                }
            }
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasText(texto, substring = true))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private val filtroCompleto = filtroDeGmailPara(direccionDeMovi, configurado = true, codigoDeConfirmacion = "987654321")

    @Test
    fun `son tres pasos cortos, con la direccion y la linea de los bancos que piden activarlo`() {
        montar(filtroCompleto, HojaDePrueba(web = false))
        PASOS_DEL_REENVIO_EN_GMAIL.forEach { assertTrue(hay(it), "falta el paso «$it»") }
        assertEquals(3, PASOS_DEL_REENVIO_EN_GMAIL.size)
        composeRule.onNodeWithTag(TAG_DIRECCION_DE_MOVI, useUnmergedTree = true).assertExists()
        assertTrue(hay(direccionDeMovi))
        assertTrue(hay(ALGUNOS_BANCOS_PIDEN_ACTIVARLO))
        assertTrue(hay("bancos y billeteras de Colombia"))
    }

    @Test
    fun `el codigo de confirmacion se ve y se copia`() {
        val hoja = HojaDePrueba(web = false)
        montar(filtroCompleto, hoja)
        assertTrue(hay("987654321"))
        tocar("Copiar el código")
        assertEquals(listOf("987654321"), hoja.copiados)
        assertTrue(hay("Copiado"))
    }

    @Test
    fun `sin codigo todavia lo dice y deja buscarlo de nuevo`() {
        val llegado = filtroCompleto
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getFiltroDeCorreo(): FiltroDeGmail = llegado
        }
        montar(filtroCompleto.copy(codigoDeConfirmacion = null), HojaDePrueba(web = false))
        assertFalse(hay("987654321"))
        assertTrue(hay("Gmail manda un código a Movi"))
        tocar("Buscar el código")
        composeRule.waitUntil(5_000) { hay("987654321") }
    }

    @Test
    fun `si Buscar el codigo no encuentra nada, lo dice`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getFiltroDeCorreo(): FiltroDeGmail = filtroCompleto.copy(codigoDeConfirmacion = null)
        }
        montar(filtroCompleto.copy(codigoDeConfirmacion = null), HojaDePrueba(web = false))
        assertFalse(hay(TODAVIA_NO_LLEGA_EL_CODIGO), "antes de buscar no dice nada")
        tocar("Buscar el código")
        composeRule.waitUntil(5_000) { hay(TODAVIA_NO_LLEGA_EL_CODIGO) }
        assertEquals("Todavía no llega. Puede tardar un minuto.", TODAVIA_NO_LLEGA_EL_CODIGO)
    }

    @Test
    fun `contra un server viejo Buscar el codigo tambien lo dice`() {
        // El repositorio de prueba base lanza en getFiltroDeCorreo, como un 404 de un server sin /filtro.
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {}
        montar(null, HojaDePrueba(web = false))
        tocar("Buscar el código")
        composeRule.waitUntil(5_000) { hay(TODAVIA_NO_LLEGA_EL_CODIGO) }
    }

    @Test
    fun `avisa que el filtro reenvia todo el correo de esos bancos y que Movi solo usa los movimientos`() {
        montar(filtroCompleto, HojaDePrueba(web = false))
        assertTrue(hay(AVISO_DE_PRIVACIDAD_DEL_FILTRO))
        assertTrue(hay("también las promociones"))
        assertTrue(hay("Movi solo usa los movimientos"))
    }

    @Test
    fun `copiar la busqueda copia el from de Gmail`() {
        val hoja = HojaDePrueba(web = false)
        montar(filtroCompleto, hoja)
        tocar("Copiar la búsqueda")
        assertEquals(listOf(filtroCompleto.busquedas.single()), hoja.copiados)
        assertTrue(hoja.copiados.single().startsWith("from:("))
        assertTrue(hay("Copiada"))
    }

    @Test
    fun `en la web se descarga el filtro con la direccion del usuario`() {
        val hoja = HojaDePrueba(web = true)
        montar(filtroCompleto, hoja)
        assertTrue(hay("Importar filtros"), "con el paso de importación")
        tocar("Descargar el filtro")
        val (nombre, contenido, tipo) = hoja.descargas.single()
        assertEquals(NOMBRE_DEL_ARCHIVO_DEL_FILTRO, nombre)
        assertEquals(filtroCompleto.xml, contenido)
        assertTrue("value='$direccionDeMovi'" in contenido)
        assertEquals("text/xml", tipo)
    }

    @Test
    fun `fuera de la web no hay descarga, solo la busqueda`() {
        montar(filtroCompleto, HojaDePrueba(web = false))
        assertFalse(hay("Descargar el filtro"))
        assertFalse(hay("Importar filtros"))
        assertTrue(hay("Copiar la búsqueda"))
    }

    @Test
    fun `dos busquedas se copian una por una`() {
        val hoja = HojaDePrueba(web = false)
        val partido = filtroCompleto.copy(busquedas = listOf("from:(a.com)", "from:(b.com)"))
        montar(partido, hoja)
        assertTrue(hay("Son 2 filtros"))
        tocar("Copiar la búsqueda 2 de 2")
        assertEquals(listOf("from:(b.com)"), hoja.copiados)
    }

    @Test
    fun `contra un server viejo la guia sigue entera, sin los botones del filtro`() {
        montar(null, HojaDePrueba(web = true))
        PASOS_DEL_REENVIO_EN_GMAIL.forEach { assertTrue(hay(it)) }
        assertTrue(hay(direccionDeMovi))
        assertFalse(hay("Copiar la búsqueda"))
        assertFalse(hay("Descargar el filtro"))
        assertTrue(hay(ALGUNOS_BANCOS_PIDEN_ACTIVARLO))
    }

    @Test
    fun `un remitente solo cuenta una vez por nombre`() {
        val f = filtroCompleto.copy(
            remitentes = listOf(RemitenteFinanciero("a.com", "A"), RemitenteFinanciero("b.com", "A"), RemitenteFinanciero("c.com", "C")),
        )
        montar(f, HojaDePrueba(web = false))
        assertTrue(hay("cubre 2 bancos y billeteras"))
    }
}
