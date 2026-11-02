package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.platform.HojaDeCompartir
import com.jvillada.movi.platform.LocalHojaDeCompartir
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.CodigoDeCapturaNuevo
import com.jvillada.movi.shared.model.GrupoDeAtajosDescartados
import com.jvillada.movi.shared.model.ResumenDeAtajosDescartados
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_MENSAJES
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
 * # Vuelta 111 · «Desde tu iPhone»
 *
 * La sección que arma los atajos del iPhone: solo fuera de Android, el código se ve una sola vez, y
 * «Crear otro» y «Desactivar» preguntan antes (el código de ahora deja de servir).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class DesdeTuIphoneTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private class HojaDePrueba : HojaDeCompartir {
        override val abreLaHojaDelSistema: Boolean = false
        override val puedeDescargar: Boolean = false
        val copiados = mutableListOf<String>()
        override fun compartir(texto: String) { copiados += texto }
        override fun copiar(texto: String) { copiados += texto }
        override fun descargar(nombre: String, contenido: String, tipo: String): Boolean = false
    }

    /** Un server de mentira con un código: cuenta cuántos se crearon y cuántos se revocaron. */
    private open class ServerDeCodigos(
        var actual: CodigoDeCaptura,
        val descartados: ResumenDeAtajosDescartados? = ResumenDeAtajosDescartados(),
    ) : RepositorioDePrueba() {
        override suspend fun getAtajosDescartados(): ResumenDeAtajosDescartados? = descartados
        var creados = 0
        var revocados = 0
        override suspend fun getCodigoDeCaptura(): CodigoDeCaptura = actual
        override suspend fun crearCodigoDeCaptura(): CodigoDeCapturaNuevo {
            creados++
            actual = actual.copy(vigente = true, creadoEn = CREADO, ultimoUso = null)
            return CodigoDeCapturaNuevo(codigo = "codigo-nuevo-$creados", creadoEn = CREADO)
        }
        override suspend fun revocarCodigoDeCaptura() {
            revocados++
            actual = actual.copy(vigente = false, creadoEn = null)
        }
    }

    private companion object {
        /** 2-nov-2026, 9:00 en Bogotá. */
        const val CREADO = 1_793_628_000_000L
        const val AHORA = CREADO + 2 * 3_600_000L
    }

    private var montada by mutableStateOf(true)

    private fun montar(server: ServerDeCodigos, hoja: HojaDePrueba = HojaDePrueba()) {
        Repositories.sustitutoDePrueba = server
        composeRule.setContent {
            CompositionLocalProvider(LocalHojaDeCompartir provides hoja) {
                MoviTheme {
                    if (montada) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            DesdeTuIphone(palabras = listOf("Bancolombia", "Davivienda"), ahora = { AHORA })
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String, elUltimo: Boolean = false) {
        val nodos = composeRule.onAllNodes(hasClickAction() and hasText(texto))
        val n = nodos.fetchSemanticsNodes().size
        nodos[if (elUltimo) n - 1 else 0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun textoDe(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun `la seccion va en la web y en iOS, no en Android, y la guia la ofrece solo en un iPhone`() {
        assertTrue(seVeLaSeccionDesdeTuIphone(esAndroid = false))
        assertFalse(seVeLaSeccionDesdeTuIphone(esAndroid = true))
        assertTrue(laGuiaOfreceLosAtajos(esAndroid = false, esIphone = true))
        assertFalse(laGuiaOfreceLosAtajos(esAndroid = false, esIphone = false), "en una computadora no hay SMS que reenviar")
        assertFalse(laGuiaOfreceLosAtajos(esAndroid = true, esIphone = false))
    }

    @Test
    fun `el codigo se muestra una sola vez, con Copiar`() {
        val server = ServerDeCodigos(CodigoDeCaptura(vigente = false, atajoDeMensajes = true))
        val hoja = HojaDePrueba()
        montar(server, hoja)
        assertTrue(hay(QUE_ES_EL_CODIGO))
        tocar(CREAR_TU_CODIGO)
        assertEquals(1, server.creados)
        assertEquals("codigo-nuevo-1", textoDe(TAG_CODIGO_DE_CAPTURA))
        assertTrue(hay(GUARDALO_AHORA))
        tocar("Copiar")
        assertEquals(listOf("codigo-nuevo-1"), hoja.copiados)

        // Al salir y volver a entrar, el server ya no lo da: solo la línea con la fecha.
        montada = false
        composeRule.waitForIdle()
        montada = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CODIGO_DE_CAPTURA, useUnmergedTree = true).assertDoesNotExist()
        assertFalse(hay("codigo-nuevo-1"))
        assertEquals("Código creado el 2 nov · todavía no llega ningún aviso", textoDe(TAG_LINEA_DEL_CODIGO))
    }

    @Test
    fun `con un codigo vigente dice cuando se creo y el ultimo aviso`() {
        montar(ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO, ultimoUso = AHORA - 2 * 3_600_000L)))
        assertEquals("Código creado el 2 nov · último aviso hace 2 h", textoDe(TAG_LINEA_DEL_CODIGO))
        composeRule.onNodeWithTag(TAG_CODIGO_DE_CAPTURA, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Crear otro pregunta antes, y cancelar no cambia nada`() {
        val server = ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO))
        montar(server)
        tocar(CREAR_OTRO_CODIGO)
        assertTrue(hay(PREGUNTA_CREAR_OTRO))
        assertTrue(hay(DETALLE_CREAR_OTRO))
        assertEquals(0, server.creados, "preguntar no crea nada")
        tocar("Cancelar")
        assertEquals(0, server.creados)
        assertFalse(hay(PREGUNTA_CREAR_OTRO))

        tocar(CREAR_OTRO_CODIGO)
        tocar(CREAR_OTRO_CODIGO, elUltimo = true)
        assertEquals(1, server.creados)
        assertEquals("codigo-nuevo-1", textoDe(TAG_CODIGO_DE_CAPTURA))
    }

    @Test
    fun `Desactivar pregunta antes y despues ofrece crear uno`() {
        val server = ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO))
        montar(server)
        tocar(DESACTIVAR_EL_CODIGO)
        assertTrue(hay(PREGUNTA_DESACTIVAR))
        assertEquals(0, server.revocados)
        tocar(DESACTIVAR_EL_CODIGO, elUltimo = true)
        assertEquals(1, server.revocados)
        assertTrue(hay(CREAR_TU_CODIGO))
    }

    @Test
    fun `si desactivar falla lo dice y vuelve a leer el estado del server`() {
        // La respuesta se perdió pero el server sí revocó: la pantalla no puede seguir diciendo que hay código.
        val server = object : ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO)) {
            var lecturas = 0
            override suspend fun getCodigoDeCaptura(): CodigoDeCaptura { lecturas++; return actual }
            override suspend fun revocarCodigoDeCaptura() {
                actual = actual.copy(vigente = false, creadoEn = null)
                error("se cortó la conexión")
            }
        }
        montar(server)
        val antes = server.lecturas
        tocar(DESACTIVAR_EL_CODIGO)
        tocar(DESACTIVAR_EL_CODIGO, elUltimo = true)
        assertEquals(ERROR_AL_DESACTIVAR_EL_CODIGO, textoDe(TAG_ERROR_DEL_CODIGO))
        assertTrue(server.lecturas > antes, "vuelve a leer")
        assertTrue(hay(CREAR_TU_CODIGO), "dice lo que de verdad hay en el server")
        composeRule.onNodeWithTag(TAG_LINEA_DEL_CODIGO, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `si crear falla lo dice y no muestra ningun codigo`() {
        val server = object : ServerDeCodigos(CodigoDeCaptura(vigente = false)) {
            override suspend fun crearCodigoDeCaptura(): CodigoDeCapturaNuevo = error("sin señal")
        }
        montar(server)
        tocar(CREAR_TU_CODIGO)
        assertEquals(ERROR_AL_CREAR_EL_CODIGO, textoDe(TAG_ERROR_DEL_CODIGO))
        composeRule.onNodeWithTag(TAG_CODIGO_DE_CAPTURA, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sin el atajo firmado, el boton abre la receta a mano con la direccion`() {
        montar(ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO, atajoDeApplePay = false)))
        tocar(AGREGAR_EL_ATAJO_DE_APPLE_PAY)
        assertTrue(hay("Obtener contenido de URL"))
        assertTrue(hay("/api/captura/atajo"))
        assertTrue(hay("«tipo» con «transaccion»"))
    }

    @Test
    fun `los pasos dicen las palabras de los bancos, el atajo y la linea de privacidad`() {
        montar(ServerDeCodigos(CodigoDeCaptura()))
        assertTrue(hay("«Bancolombia»"))
        assertTrue(hay("«Davivienda»"))
        assertTrue(hay("«$NOMBRE_DEL_ATAJO_DE_MENSAJES»"))
        assertTrue(hay(PRIVACIDAD_DE_LOS_ATAJOS))
        assertTrue(hay(TEXTO_DE_PRUEBA_DEL_ATAJO))
    }

    @Test
    fun `lo que no se guardo se dice en una linea, y sin nada no se dice nada`() {
        montar(
            ServerDeCodigos(
                CodigoDeCaptura(vigente = true, creadoEn = CREADO),
                ResumenDeAtajosDescartados(grupos = listOf(GrupoDeAtajosDescartados("NO_ES_DEL_BANCO", 12))),
            ),
        )
        assertEquals("12 mensajes no eran de un banco en los últimos 30 días.", textoDe(TAG_ATAJOS_DESCARTADOS))
    }

    @Test
    fun `sin descartes no hay linea`() {
        montar(ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO)))
        composeRule.onNodeWithTag(TAG_ATAJOS_DESCARTADOS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `las recetas a mano llevan la direccion y nunca el codigo en ella`() {
        val url = "https://movi.example/api/captura/atajo"
        listOf(true, false).forEach { applePay ->
            val pasos = recetaDelAtajo(url, applePay)
            assertTrue(pasos.any { url in it })
            assertTrue(pasos.any { "Authorization" in it && "Bearer" in it })
            assertFalse(pasos.any { "$url?" in it })
        }
        val mensajes = pasosDeLaAutomatizacionDeMensajes(listOf("Bancolombia"))
        assertEquals(4, mensajes.size)
        assertTrue(mensajes[1].contains("«El mensaje contiene»") && mensajes[1].contains("«Bancolombia»"))
    }
}
