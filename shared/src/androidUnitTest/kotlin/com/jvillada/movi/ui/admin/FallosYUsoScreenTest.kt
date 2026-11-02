package com.jvillada.movi.ui.admin

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.shared.model.ActivosDelDia
import com.jvillada.movi.shared.model.AdopcionDeFuncion
import com.jvillada.movi.shared.model.CohorteSemanal
import com.jvillada.movi.shared.model.Conteo
import com.jvillada.movi.shared.model.EventoDelRecorrido
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.GrupoDeFallos
import com.jvillada.movi.shared.model.ResumenDeFallos
import com.jvillada.movi.shared.model.ResumenDeUso
import com.jvillada.movi.shared.model.SesionDeUso
import com.jvillada.movi.shared.model.UsuarioConUso
import com.jvillada.movi.theme.MoviTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vuelta 100 · «Fallos y uso»: las pestañas, el detalle de un fallo y el visor del recorrido. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class FallosYUsoScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val ejemplo = FalloDeLaApp(
        id = "f1", plataforma = "android", version = "1.90", tipo = "IllegalStateException", mensaje = "Se cayó",
        stack = "java.lang.IllegalStateException\n\tat com.jvillada.movi.sms.SmsSyncWorker.doWork(SmsSyncWorker.kt:43)",
        componente = "SmsSyncWorker", ocurridoEn = 1_800_000_000_000L,
    )

    private val eventos = listOf(
        EventoDelRecorrido(1, 1_800_000_000_000L, "SISTEMA", "SESION_EMPEZADA", "", mapOf("origen" to "FRIO")),
        EventoDelRecorrido(2, 1_800_000_001_234L, "PANTALLA", "PANTALLA", "PrimerUso(paso 1)", mapOf("pantalla" to "PrimerUso")),
        EventoDelRecorrido(3, 1_800_000_002_000L, "ACCION", "TOQUE", "PrimerUso(paso 1)", mapOf("accion" to "SIGUIENTE")),
        // Vuelta 104: un toque autocapturado y lo que hizo el server mientras tanto.
        EventoDelRecorrido(4, 1_800_000_003_000L, "ACCION", "toque:guardar-y-seguir", "PrimerUso(paso 2)", mapOf("componente" to "Boton", "boton" to "PRINCIPAL")),
        EventoDelRecorrido(0, 1_800_000_003_500L, "SISTEMA", "AVISO_CAPTURADO", "", mapOf("origen" to "SMS"), plataforma = "server", sinSesion = true),
    )

    /** La hoja de compartir de mentira: guarda lo que se descargó o se compartió. */
    private class HojaDePrueba(override val puedeDescargar: Boolean) : com.jvillada.movi.platform.HojaDeCompartir {
        val descargas = mutableListOf<Triple<String, String, String>>()
        val compartidos = mutableListOf<String>()
        override val abreLaHojaDelSistema = !puedeDescargar
        override fun compartir(texto: String) { compartidos += texto }
        override fun copiar(texto: String) {}
        override fun descargar(nombre: String, contenido: String, tipo: String): Boolean { descargas += Triple(nombre, contenido, tipo); return true }
    }

    private fun montar(hoja: com.jvillada.movi.platform.HojaDeCompartir? = null) {
        com.jvillada.movi.data.Repositories.sustitutoDePrueba = com.jvillada.movi.data.RepositorioDePrueba()
        ObservabilidadParaAdmin.sustitutoDePrueba = object : ObservabilidadParaAdmin {
            override suspend fun fallos() = ResumenDeFallos(
                grupos = listOf(GrupoDeFallos("IllegalStateException · com.jvillada.movi.sms.SmsSyncWorker.doWork(SmsSyncWorker.kt)", "IllegalStateException", "SmsSyncWorker", listOf("android"), 3, 2, listOf("1.90"), 1_800_000_000_000L, ejemplo)),
            )
            override suspend fun uso() = ResumenDeUso(
                activosHoy = 4, activos7 = 9, activos30 = 20,
                activosPorDia = listOf(ActivosDelDia("2026-11-01", 4)),
                embudo = listOf(Conteo("BIENVENIDA", 0, 10), Conteo("PERIODO", 0, 7)),
                adopcion = listOf(AdopcionDeFuncion("MOVI_AI", 5, 25)),
                cohortes = listOf(CohorteSemanal("2026-10-19", 6, 50, 33)),
                sesionesPorUsuario = 2.5, duracionMedianaSegundos = 200,
                masTocados = listOf(Conteo("guardar", 40, 3), Conteo("pestana:plan", 12, 2)),
                toquesSinEfecto = listOf(Conteo("confirmar", 2, 1)),
            )
            override suspend fun usuarios() = listOf(UsuarioConUso("u1", "papa@movi.test", 2, 30, 1_800_000_000_000L, listOf("android")))
            override suspend fun sesiones(usuarioId: String) = listOf(SesionDeUso("s1", 1_800_000_000_000L, 1_800_000_002_000L, 2, "android", "1.90", 3))
            override suspend fun eventos(usuarioId: String, sesionId: String) = eventos
        }
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.jvillada.movi.platform.LocalHojaDeCompartir provides hoja) {
                MoviTheme { FallosYUsoScreen(onNavigate = {}) }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun losFallosSeAgrupanYSeAbreElDetalle() {
        montar()
        composeRule.onNodeWithText("IllegalStateException · SmsSyncWorker").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Se cayó").assertExists()
    }

    @Test
    fun elUsoMuestraActivosEmbudoAdopcionYCohortes() {
        montar()
        composeRule.onNodeWithText("Uso").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Últimos 30 días").assertExists()
        composeRule.onNodeWithText("Movi AI").performScrollTo().assertExists()
        composeRule.onNodeWithText("S1 50 % · S2 33 % · S4 — · S8 —").performScrollTo().assertExists()
    }

    @Test
    fun elVisorLlegaALaLineaDeTiempoYFiltra() {
        montar()
        composeRule.onNodeWithText("Recorridos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("papa@movi.test").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 s · android 1.90 · 3 eventos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3. TOQUE").assertExists()
        composeRule.onNodeWithText("Acciones").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1. SESION_EMPEZADA").assertDoesNotExist()
        composeRule.onNodeWithText("3. TOQUE").assertExists()
    }

    @Test
    fun laPestanaDelServidorTieneSuLugar() {
        montar()
        composeRule.onNodeWithText("Servidor").performClick()
        composeRule.waitForIdle()
        // Vuelta 102: la pestaña la llena `PestanaServidor`; sin el panel del server dice que no pudo leerlo.
        composeRule.onNodeWithText("No pude leer cómo está el servidor.", substring = true).assertExists()
    }

    @Test
    fun filtrarYBuscarEnElRecorrido() {
        assertEquals(listOf(3, 4), filtrarElRecorrido(eventos, "ACCION", "").map { it.secuencia })
        assertEquals(listOf(1), filtrarElRecorrido(eventos, null, "sesion").map { it.secuencia })
        assertEquals("2 min 5 s", duracion(125))
        assertEquals(true, detalleDelEvento(eventos[2]).endsWith("ACCION · PrimerUso(paso 1) · accion=SIGUIENTE"))
        assertEquals(true, Regex("\\d\\d:\\d\\d:\\d\\d\\.234").containsMatchIn(detalleDelEvento(eventos[1])))
    }

    private fun abrirLaSesion() {
        composeRule.onNodeWithText("Recorridos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("papa@movi.test").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 s · android 1.90 · 3 eventos").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun soloToquesYUnToqueEnUnaLinea() {
        montar()
        abrirLaSesion()
        composeRule.onNodeWithText("4. guardar-y-seguir").performScrollTo().assertExists()
        composeRule.onNodeWithText("PrimerUso(paso 2) · boton=PRINCIPAL, componente=Boton").performScrollTo().assertExists()
        composeRule.onNodeWithText("· AVISO_CAPTURADO").performScrollTo().assertExists()
        composeRule.onNodeWithText("Solo toques").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3. TOQUE").assertDoesNotExist()
        composeRule.onNodeWithText("4. guardar-y-seguir").assertExists()
    }

    @Test
    fun laSesionSeExportaEnCsvEnLaWebYSeComparteEnElTelefono() {
        val web = HojaDePrueba(puedeDescargar = true)
        montar(web)
        abrirLaSesion()
        composeRule.onNodeWithText(EXPORTAR_LA_SESION).performClick()
        composeRule.waitForIdle()
        val (nombre, csv, tipo) = web.descargas.single()
        assertEquals(true, nombre.startsWith("recorrido-") && nombre.endsWith(".csv"))
        assertEquals("text/csv", tipo)
        assertEquals(eventos.size + 1, csv.trimEnd().lines().size)
        assertEquals(true, "toque:guardar-y-seguir" in csv && "server" in csv)
    }

    @Test
    fun enElTelefonoElCsvSeComparte() {
        val telefono = HojaDePrueba(puedeDescargar = false)
        montar(telefono)
        abrirLaSesion()
        composeRule.onNodeWithText(EXPORTAR_LA_SESION).performClick()
        composeRule.waitForIdle()
        assertEquals(true, telefono.compartidos.single().startsWith("secuencia,hora_ms,tipo,nombre"))
    }

    @Test
    fun elUsoMuestraLoMasTocadoYLosToquesSinEfecto() {
        montar()
        composeRule.onNodeWithText("Uso").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TITULO_DE_LO_MAS_TOCADO).performScrollTo().assertExists()
        composeRule.onNodeWithText("pestana:plan").performScrollTo().assertExists()
        composeRule.onNodeWithText(TITULO_DE_LOS_TOQUES_SIN_EFECTO).performScrollTo().assertExists()
        composeRule.onNodeWithText("2 veces").performScrollTo().assertExists()
    }

    @Test
    fun cadaTipoTieneSuIconoYSuColor() {
        val toque = eventos[3]
        assertEquals(com.jvillada.movi.ui.components.ds.TonoDeIcono.Info, estiloDelEvento(toque).tono)
        assertEquals("Toque", estiloDelEvento(toque).rotulo)
        assertEquals(com.jvillada.movi.ui.components.ds.TonoDeIcono.Neutro, estiloDelEvento(eventos[1]).tono)
        assertEquals("El server", estiloDelEvento(eventos[4]).rotulo)
        assertEquals("La app sola", estiloDelEvento(eventos[0]).rotulo)
        assertEquals(listOf(4), filtrarElRecorrido(eventos, FILTRO_SOLO_TOQUES, "").map { it.secuencia })
        assertEquals("PrimerUso(paso 2) · boton=PRINCIPAL, componente=Boton", lineaDelToque(toque))
    }
}
