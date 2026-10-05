package com.jvillada.movi.ui.papeles

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import com.jvillada.movi.compartir.MAX_ARCHIVOS_COMPARTIDOS
import com.jvillada.movi.compartir.esCompartirConMovi
import com.jvillada.movi.compartir.leerLoCompartido
import com.jvillada.movi.compartir.urisCompartidos
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.IA_NO_DISPONIBLE
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.shared.model.NO_SE_PUDO_LEER_EL_PAPEL
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.QueEsElPapel
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Compartir con Movi» del lado de la app (Ola 2)
 *
 * - **La puerta**: lo compartido se lee solo con sesión y fuera del login. Con «Entrar con huella»
 *   la app arranca en el login aunque haya sesión, y ahí no se sube nada.
 * - **De punta a punta, sin red**: [leerUnPapel] contra un repositorio de mentira — guarda primero,
 *   lee después, y solo promete «queda guardado» cuando es verdad.
 * - **El Intent de Android**: qué cuenta como compartir con Movi y cómo se leen los archivos.
 * - **La hoja**: dice lo que pasó y «Revisar» lleva a la propuesta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class CompartirConMoviTest {

    @get:Rule val composeRule = createComposeRule()

    private val documento = Documento(
        id = "doc_1", nombre = "transferencia.png", tipo = TipoDeDocumento.OTRO,
        mimeType = "image/png", bytes = 5, subidoEn = 0,
    )

    private val propuesta = LecturaDelPapel(
        documentoId = "doc_1",
        que = QueEsElPapel.COMPROBANTE,
        porRevisarId = "cmp_doc_1",
        leido = ParsedSms(amount = 138_600.0, merchant = "Coomeva", type = TransactionType.EXPENSE, category = "Salud"),
        cuando = "2026-09-30 14:05",
    )

    /** Un repositorio que guarda y lee lo que se le diga, y anota qué se le pidió. */
    private class RepoDePapeles(
        val subir: () -> Documento,
        val leer: (Boolean) -> LecturaDelPapel,
    ) : RepositorioDePrueba() {
        val subidos = mutableListOf<String>()
        val leidos = mutableListOf<Pair<String, Boolean>>()
        override suspend fun subirPapel(fileName: String, bytes: ByteArray, mimeType: String): Documento {
            subidos += fileName
            return subir()
        }
        override suspend fun leerPapel(documentoId: String, anotarAunqueEsteAnotado: Boolean): LecturaDelPapel {
            leidos += documentoId to anotarAunqueEsteAnotado
            return leer(anotarAunqueEsteAnotado)
        }
    }

    private fun archivo(bytes: ByteArray = byteArrayOf(1, 2, 3)) = ArchivoCompartido("transferencia.png", bytes, "image/png")

    // ── La puerta ──────────────────────────────────────────────────────────────

    @Test
    fun lo_compartido_espera_a_que_se_pase_la_puerta() {
        assertFalse(sePuedeLeerLoCompartido(conSesion = false, pantalla = Screen.Login), "sin sesión no se sube nada")
        // Con «Entrar con huella» la app arranca en el login AUNQUE haya sesión: ahí tampoco.
        assertFalse(sePuedeLeerLoCompartido(conSesion = true, pantalla = Screen.Login))
        assertFalse(sePuedeLeerLoCompartido(conSesion = true, pantalla = Screen.Register))
        // Pasó el dedo y la pila llegó al Inicio: ahora sí.
        assertTrue(sePuedeLeerLoCompartido(conSesion = true, pantalla = Screen.Dashboard))
        assertTrue(sePuedeLeerLoCompartido(conSesion = true, pantalla = Screen.PorRevisar))
    }

    @Test
    fun la_cola_se_vacia_al_tomarla() {
        PapelesCompartidos.recibir(listOf(archivo(), archivo()))
        assertEquals(2, PapelesCompartidos.tomarTodos().size)
        assertTrue(PapelesCompartidos.pendientes.isEmpty(), "lo que tomó la hoja no lo vuelve a tomar otra")
    }

    // ── De punta a punta ───────────────────────────────────────────────────────

    @Test
    fun guarda_y_despues_lee() = runBlocking {
        val repo = RepoDePapeles(subir = { documento }, leer = { propuesta })
        val resultado = leerUnPapel(repo, archivo())

        val enLaBandeja = assertIs<ResultadoDelPapel.EnLaBandeja>(resultado)
        assertEquals("cmp_doc_1", enLaBandeja.lectura.porRevisarId)
        assertEquals(listOf("transferencia.png"), repo.subidos)
        assertEquals(listOf("doc_1" to false), repo.leidos)
        assertEquals("Quedó en Por revisar", tituloDelResultado(resultado))
    }

    @Test
    fun si_no_se_pudo_leer_dice_que_quedo_guardado() = runBlocking {
        val repo = RepoDePapeles(
            subir = { documento },
            leer = { throw ApiException(422, "Este PDF tiene contraseña y Movi no puede abrirlo.") },
        )
        val resultado = assertIs<ResultadoDelPapel.NoSePudo>(leerUnPapel(repo, archivo()))
        assertTrue(resultado.guardado)
        assertEquals("Este PDF tiene contraseña y Movi no puede abrirlo.", resultado.motivo)
        assertEquals(NO_SE_PUDO_LEER_EL_PAPEL, tituloDelResultado(resultado))
    }

    @Test
    fun si_la_ia_no_esta_disponible_lo_dice_y_que_el_archivo_quedo_guardado() = runBlocking {
        listOf(IA_SIN_CREDITO, IA_NO_DISPONIBLE).forEach { codigo ->
            val repo = RepoDePapeles(subir = { documento }, leer = { throw ApiException(503, codigo) })
            val resultado = assertIs<ResultadoDelPapel.NoSePudo>(leerUnPapel(repo, archivo()))
            assertTrue(resultado.guardado)
            assertEquals(
                "La lectura con IA no está disponible ahora. Tu archivo quedó guardado en Documentos.",
                resultado.motivo,
            )
        }
        // Un 503 cualquiera (el proxy de Railway, un despliegue) sigue siendo un error del server.
        val repo = RepoDePapeles(subir = { documento }, leer = { throw ApiException(503, "<html>Bad gateway</html>") })
        val resultado = assertIs<ResultadoDelPapel.NoSePudo>(leerUnPapel(repo, archivo()))
        assertEquals("Error en el servidor. Intenta en unos minutos.", resultado.motivo)
    }

    @Test
    fun si_no_se_pudo_guardar_no_promete_que_quedo_guardado() = runBlocking {
        val repo = RepoDePapeles(subir = { throw ApiException(500) }, leer = { propuesta })
        val resultado = assertIs<ResultadoDelPapel.NoSePudo>(leerUnPapel(repo, archivo()))
        assertFalse(resultado.guardado)
        assertTrue(repo.leidos.isEmpty(), "sin documento no hay nada que leer")
        assertEquals("No pudimos guardar el archivo", tituloDelResultado(resultado))
    }

    @Test
    fun lo_que_pesa_de_mas_no_viaja() = runBlocking {
        val repo = RepoDePapeles(subir = { documento }, leer = { propuesta })
        val resultado = leerUnPapel(repo, archivo(ByteArray(MAX_DOCUMENTO_BYTES.toInt() + 1)))
        assertIs<ResultadoDelPapel.NoSePudo>(resultado)
        assertTrue(repo.subidos.isEmpty())
    }

    @Test
    fun ya_anotado_y_extracto_y_lo_que_ya_estaba() {
        val evento = FinancialEvent(
            id = "ev1", accountId = "a", type = TransactionType.EXPENSE, amount = 138_600,
            category = "Salud", description = "Coomeva", timestamp = 0,
        )
        assertIs<ResultadoDelPapel.YaAnotado>(resultadoDe("x", propuesta.copy(porRevisarId = null, yaAnotado = listOf(evento))))
        assertIs<ResultadoDelPapel.Extracto>(resultadoDe("x", LecturaDelPapel("doc", QueEsElPapel.EXTRACTO)))
        val confirmado = resultadoDe("x", propuesta.copy(yaEstabaEnLaBandeja = true, estadoEnLaBandeja = SMS_STATE_CONFIRMED))
        assertEquals("Ya lo habías confirmado", tituloDelResultado(confirmado))
    }

    // ── El Intent de Android ───────────────────────────────────────────────────

    @Test
    fun que_cuenta_como_compartir_con_movi() {
        assertTrue(esCompartirConMovi(Intent.ACTION_SEND, "image/jpeg"))
        assertTrue(esCompartirConMovi(Intent.ACTION_SEND_MULTIPLE, "image/*"))
        assertTrue(esCompartirConMovi(Intent.ACTION_SEND, "application/pdf"))
        assertFalse(esCompartirConMovi(Intent.ACTION_SEND, "text/plain"))
        assertFalse(esCompartirConMovi(Intent.ACTION_MAIN, "image/png"))
        assertFalse(esCompartirConMovi(Intent.ACTION_SEND, null))
    }

    @Test
    fun lee_los_archivos_compartidos_con_tope() {
        val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uno = Uri.parse("content://fotos/compartida/recibo.jpg")
        val dos = Uri.parse("content://fotos/compartida/extracto.pdf")
        val resolver = shadowOf(contexto.contentResolver)
        resolver.registerInputStream(uno, ByteArrayInputStream(byteArrayOf(9, 8, 7)))
        resolver.registerInputStream(dos, ByteArrayInputStream(ByteArray(MAX_DOCUMENTO_BYTES.toInt() + 10)))

        val varios = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList((1..15).map { if (it == 1) uno else dos }))
        }
        assertEquals(MAX_ARCHIVOS_COMPARTIDOS, urisCompartidos(varios).size, "compartir quince fotos no son quince lecturas")

        val leidos = leerLoCompartido(contexto, listOf(uno, dos), fallbackDeTipo = "image/*")
        assertEquals("recibo.jpg", leidos[0].nombre)
        assertEquals(listOf<Byte>(9, 8, 7), leidos[0].bytes.toList())
        assertNull(leidos[0].problema)
        assertTrue(leidos[1].problema != null, "un archivo de más de 10 MB se dice, no se sube")
        assertTrue(leidos[1].bytes.isEmpty())
    }

    // ── La hoja ────────────────────────────────────────────────────────────────

    @Test
    fun la_hoja_lee_y_lleva_a_la_propuesta() {
        Repositories.sustitutoDePrueba = RepoDePapeles(subir = { documento }, leer = { propuesta })
        var destino: Screen? = null
        var cerrada = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaLeyendoElPapel(
                        archivos = listOf(archivo()),
                        onCerrar = { cerrada = true },
                        onNavigate = { destino = it },
                        onCambio = {},
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Quedó en Por revisar")).fetchSemanticsNodes().isNotEmpty()
        }
        tocar("Revisar")
        assertTrue(cerrada)
        assertEquals(Screen.SMSReconcile("cmp_doc_1"), destino)
    }

    @Test
    fun la_hoja_ofrece_anotarlo_igual_sin_volver_a_subirlo() {
        val evento = FinancialEvent(
            id = "ev1", accountId = "a", type = TransactionType.EXPENSE, amount = 138_600,
            category = "Salud", description = "Coomeva", timestamp = 0,
        )
        val repo = RepoDePapeles(
            subir = { documento },
            leer = { igual -> if (igual) propuesta else propuesta.copy(porRevisarId = null, yaAnotado = listOf(evento)) },
        )
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaLeyendoElPapel(archivos = listOf(archivo()), onCerrar = {}, onNavigate = {}, onCambio = {})
                }
            }
        }
        runCatching {
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasText("Ya lo tienes anotado")).fetchSemanticsNodes().isNotEmpty()
            }
        }.onFailure { error(composeRule.onRoot(useUnmergedTree = true).printToString() + " leidos=" + repo.leidos) }
        tocar("Anotarlo de todas formas")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Quedó en Por revisar")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, repo.subidos.size, "anotarlo igual no vuelve a subir el archivo")
        assertEquals(listOf("doc_1" to false, "doc_1" to true), repo.leidos)
    }

    /** `performSemanticsAction`: bajo Robolectric `performClick` no llega al composable (ver `ReconciliarSinDuplicarTest`). */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
