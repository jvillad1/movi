package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.accounts.TAG_BANCOS_DE_UN_TOQUE
import com.jvillada.movi.ui.accounts.TAG_FORMULARIO_DE_CUENTA
import com.jvillada.movi.ui.accounts.TAG_TIPOS_DE_CUENTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * **El paso de las cuentas, en orden** (vuelta 54): «Agregar cuenta» va dentro de la tarjeta del
 * formulario, debajo del saldo (revisión: fijo en el pie se confundía con agregar una tarjeta), antes de
 * «Tarjetas de crédito», y «Siguiente» se ve sin desplazar, a 390 × 844 y a 360 × 760, con la letra de la
 * app (×1,12).
 *
 * Vuelta 88: **los bancos y los tipos bajan en renglones dentro de la tarjeta**. La vuelta 54 los había
 * puesto en una fila que se desplazaba de lado para que «Agregar cuenta» se viera sin desplazar; en el
 * teléfono del papá del dueño esa fila se veía con píldoras cortadas por los dos lados y solo dos
 * bancos. Ganó que se vea todo: «Agregar cuenta» puede quedar un desplazamiento más abajo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElPasoDeCuentasALaVistaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile() =
                UserProfile(id = "u1", email = "ana@movi.test", name = "Ana Prueba", avatarColor = "#7C6CF0")
        }
        composeRule.setContent {
            ConClaseDeAncho {
                Box(Modifier.fillMaxSize()) {
                    PrimerUsoScreen(onNavigate = {}, pasoInicial = PasoDePrimerUso.CUENTAS, modoDeCaptura = ModoDeCaptura.SUBIR_UN_EXTRACTO)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun loEsencialSeVe() {
        montar()
        // Sin recortar: un campo debajo del pliegue tiene límites recortados de alto cero.
        val alto = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val campos = composeRule.onAllNodes(hasSetTextAction())
        val nombre = campos[0].getUnclippedBoundsInRoot()
        val saldo = campos[1].getUnclippedBoundsInRoot()
        val boton = composeRule.onNodeWithTag(TAG_AGREGAR_CUENTA_EN_LA_GUIA).getUnclippedBoundsInRoot()
        val avanzar = composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).getUnclippedBoundsInRoot()
        val tarjetas = composeRule.onNodeWithText("Tarjetas de crédito").getUnclippedBoundsInRoot()
        assertTrue(nombre.bottom <= saldo.top && saldo.bottom <= boton.top, "el botón va debajo del saldo, en el formulario")
        assertTrue(boton.bottom <= tarjetas.top, "«Agregar cuenta» va antes de la sección de tarjetas, no debajo")
        assertTrue(avanzar.bottom <= alto, "«Siguiente» a la vista")

        // Vuelta 88: los bancos y los tipos bajan en renglones, a lo ancho del formulario (no se salen).
        val formulario = composeRule.onNodeWithTag(TAG_FORMULARIO_DE_CUENTA).getUnclippedBoundsInRoot()
        val bancos = composeRule.onNodeWithTag(TAG_BANCOS_DE_UN_TOQUE).getUnclippedBoundsInRoot()
        val tipos = composeRule.onNodeWithTag(TAG_TIPOS_DE_CUENTA).getUnclippedBoundsInRoot()
        assertTrue(bancos.height > 56.dp, "los bancos bajan en más de un renglón: ${bancos.height}")
        assertTrue(bancos.left >= formulario.left && bancos.right <= formulario.right, "los bancos, dentro del formulario")
        assertTrue(tipos.left >= formulario.left && tipos.right <= formulario.right, "los tipos, dentro del formulario")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
    fun `a 390 el formulario va en orden y los bancos bajan en renglones`() = loEsencialSeVe()

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
    fun `a 360 tambien`() = loEsencialSeVe()
}
