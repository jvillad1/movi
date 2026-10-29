package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.accounts.TAG_BANCOS_DE_UN_TOQUE
import com.jvillada.movi.ui.accounts.TAG_TIPOS_DE_CUENTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * **El paso de las cuentas deja a la vista lo esencial** (vuelta 54). Con los siete bancos en varios
 * renglones, a 390 dp el nombre, el saldo y «Agregar cuenta» quedaban debajo del pliegue (nuevo-04).
 * Ahora los bancos y los tipos van cada uno en una fila que se desplaza de lado y «Agregar cuenta» va en
 * el pie: sin desplazar nada, el campo del saldo y el botón se ven enteros, a 390 × 844 y a 360 × 760,
 * con la letra de la app (×1,12).
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
        assertTrue(nombre.bottom <= boton.top, "el nombre ($nombre) se ve sin desplazar, arriba del pie ($boton)")
        assertTrue(saldo.bottom <= boton.top, "el saldo ($saldo) se ve sin desplazar, arriba del pie ($boton)")
        assertTrue(boton.bottom <= avanzar.top && avanzar.bottom <= alto, "«Agregar cuenta» y «Siguiente» en el pie, a la vista")

        // Los bancos y los tipos: una fila cada uno, del alto de un chip.
        val bancos = composeRule.onNodeWithTag(TAG_BANCOS_DE_UN_TOQUE).getUnclippedBoundsInRoot()
        val tipos = composeRule.onNodeWithTag(TAG_TIPOS_DE_CUENTA).getUnclippedBoundsInRoot()
        assertTrue(bancos.height < 56.dp, "los bancos van en una sola fila: ${bancos.height}")
        assertTrue(tipos.height < 56.dp, "los tipos van en una sola fila: ${tipos.height}")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
    fun `a 390 el nombre, el saldo y Agregar cuenta se ven sin desplazar`() = loEsencialSeVe()

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
    fun `a 360 tambien`() = loEsencialSeVe()
}
