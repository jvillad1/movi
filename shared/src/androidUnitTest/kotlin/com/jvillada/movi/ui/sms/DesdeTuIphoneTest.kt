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

    /** Lo que «Desde tu iPhone» le avisa a la pantalla que la pinta (vuelta 114). */
    private val avisados = mutableListOf<CodigoDeCaptura?>()

    private fun montar(server: ServerDeCodigos, hoja: HojaDePrueba = HojaDePrueba(), versionDeIos: Int? = null) {
        Repositories.sustitutoDePrueba = server
        composeRule.setContent {
            CompositionLocalProvider(LocalHojaDeCompartir provides hoja) {
                MoviTheme {
                    if (montada) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            DesdeTuIphone(
                                palabras = listOf("Bancolombia", "DAVIVIENDA"),
                                ahora = { AHORA },
                                versionDeIos = versionDeIos,
                                alCambiarElCodigo = { avisados += it },
                            )
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
        assertTrue(hay("«DAVIVIENDA»"))
        assertTrue(hay("«$NOMBRE_DEL_ATAJO_DE_MENSAJES»"))
        assertTrue(hay(PRIVACIDAD_DE_LOS_ATAJOS))
        // Vuelta 114 · la línea no promete de más: el atajo no sabe quién manda, así que no puede decir
        // «solo los mensajes de bancos»; dice la regla de verdad (empieza como un aviso y trae un monto).
        assertEquals(
            "Solo se guardan los mensajes que empiezan como un aviso del banco y traen un monto; los demás se " +
                "descartan sin guardarlos. Nada se anota sin que lo confirmes.",
            PRIVACIDAD_DE_LOS_ATAJOS,
        )
        assertFalse("mensajes de bancos" in PRIVACIDAD_DE_LOS_ATAJOS)
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
        val mensajes = pasosDeLaAutomatizacionDeMensajes(listOf("Bancolombia"), VarianteDeLaAutomatizacion.IOS_17_A_26)
        assertEquals(5, mensajes.size)
        assertEquals(PROBAR_CON_UN_SMS_REAL, mensajes.last())
        assertTrue(mensajes[1].contains("«El mensaje contiene»") && mensajes[1].contains("«Bancolombia»"))
    }

    // ── Vuelta 114 ────────────────────────────────────────────────────────────────────────────

    private fun existe(tag: String) =
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun arribaDe(tag: String) =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `los pasos de iOS 27 son los que se vieron en el iPhone, y los de iOS 17 a 26 los de la pestana`() {
        val de27 = pasosDeLaAutomatizacionDeMensajes(listOf("Bancolombia", "DAVIVIENDA", "BBVA"), VarianteDeLaAutomatizacion.IOS_27_O_MAS)
        assertEquals("En Atajos abre el atajo «$NOMBRE_DEL_ATAJO_DE_MENSAJES».", de27[0])
        assertTrue("«Buscar»" in de27[1] && "«Automatización»" in de27[1])
        assertTrue("«Comunicación»" in de27[2] && "«Al recibir un mensaje en el que Remitente es Remitente»" in de27[2])
        assertTrue("«Mensaje contiene Texto»" in de27[3] && "«Bancolombia»" in de27[3])
        assertTrue("prendido «Automatización»" in de27[4] && "apagado «Confirmar antes de ejecutar»" in de27[4])
        assertTrue("otra tarjeta «Mensaje»" in de27[5] && "«o»" in de27[5] && "«DAVIVIENDA», «BBVA»" in de27[5])
        // Un filtro en la misma tarjeta se une con «y»: la guía lo advierte.
        assertTrue("no «+ Agregar filtro»" in de27[5] && "«y»" in de27[5], de27[5])
        assertEquals(PROBAR_CON_UN_SMS_REAL, de27.last())
        assertTrue(de27.none { "pestaña" in it }, "en iOS 27 no hay pestaña «Automatización»")

        val de17 = pasosDeLaAutomatizacionDeMensajes(listOf("Bancolombia", "DAVIVIENDA"), VarianteDeLaAutomatizacion.IOS_17_A_26)
        assertTrue("pestaña «Automatización»" in de17[0])
        assertTrue(de17.any { "«Ejecutar de inmediato»" in it })
        assertTrue(de17.any { "«$NOMBRE_DEL_ATAJO_DE_MENSAJES»" in it })
        assertTrue(de17.any { "Haz una automatización más con cada uno: «DAVIVIENDA»" in it })
        assertEquals(listOf(ANTES_DE_IOS_17), pasosDeLaAutomatizacionDeMensajes(listOf("Bancolombia"), VarianteDeLaAutomatizacion.ANTES_DE_IOS_17))
        assertTrue("notificación" in ANTES_DE_IOS_17 && "correos" in ANTES_DE_IOS_17)
    }

    @Test
    fun `sin saber la version se ven las dos variantes con su rotulo, la de iOS 27 primero, y la linea de antes de iOS 17`() {
        montar(ServerDeCodigos(CodigoDeCaptura()), versionDeIos = null)
        assertTrue(existe(TAG_PASOS_IOS_27) && existe(TAG_PASOS_IOS_17_A_26) && existe(TAG_ANTES_DE_IOS_17))
        assertTrue(arribaDe(TAG_PASOS_IOS_27) < arribaDe(TAG_PASOS_IOS_17_A_26), "la de iOS 27 primero")
        assertTrue(hay(ROTULO_IOS_27_O_MAS))
        assertTrue(hay(ROTULO_IOS_17_A_26))
        assertFalse(hay(TU_IPHONE_MUESTRA_OTRA_COSA))
    }

    @Test
    fun `en iOS 27 se ve su variante y la otra queda detras de Tu iPhone muestra otra cosa`() {
        montar(ServerDeCodigos(CodigoDeCaptura()), versionDeIos = 27)
        assertTrue(existe(TAG_PASOS_IOS_27))
        assertFalse(existe(TAG_PASOS_IOS_17_A_26))
        assertFalse(existe(TAG_ANTES_DE_IOS_17))
        tocar(TU_IPHONE_MUESTRA_OTRA_COSA)
        assertTrue(existe(TAG_PASOS_IOS_17_A_26))
        assertTrue(hay(ROTULO_IOS_17_A_26))
    }

    @Test
    fun `en iOS 18 se ve la pestana y la de iOS 27 queda detras`() {
        montar(ServerDeCodigos(CodigoDeCaptura()), versionDeIos = 18)
        assertTrue(existe(TAG_PASOS_IOS_17_A_26))
        assertFalse(existe(TAG_PASOS_IOS_27))
        tocar(TU_IPHONE_MUESTRA_OTRA_COSA)
        assertTrue(existe(TAG_PASOS_IOS_27))
    }

    @Test
    fun `antes de iOS 17 dice que el iPhone pide tocar una notificacion y propone los correos`() {
        montar(ServerDeCodigos(CodigoDeCaptura()), versionDeIos = 16)
        assertTrue(existe(TAG_ANTES_DE_IOS_17))
        assertTrue(existe(TAG_PASOS_IOS_17_A_26))
    }

    @Test
    fun `el paso de agregar el atajo trae el plan B y donde cambiar el codigo`() {
        montar(ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO, atajoDeMensajes = true)))
        assertTrue(hay(AL_AGREGAR_EL_ATAJO))
        assertTrue(hay(PLAN_B_DEL_CODIGO))
        assertTrue("«Omitir configuración»" in PLAN_B_DEL_CODIGO && "acción «Texto»" in PLAN_B_DEL_CODIGO)
        assertTrue(hay(CAMBIAR_EL_CODIGO_DEL_ATAJO))
        assertEquals("Si tocas «Crear otro», abre el atajo y cambia el código de la acción «Texto».", CAMBIAR_EL_CODIGO_DEL_ATAJO)
        // Y la pregunta de «Crear otro» lo recuerda.
        tocar(CREAR_OTRO_CODIGO)
        assertTrue(hay(DETALLE_CREAR_OTRO))
        assertTrue("acción «Texto»" in DETALLE_CREAR_OTRO)
        // Apple Pay en iOS 27 no se inventa: se dice que falta verificarlo.
        assertTrue(hay(APPLE_PAY_EN_IOS_27))
        assertFalse("Transacción" in APPLE_PAY_EN_IOS_27, "no adelanta dónde está «Transacción»: nadie lo probó")
    }

    @Test
    fun `el nombre que dice la guia es el del archivo que baja el boton`() {
        montar(ServerDeCodigos(CodigoDeCaptura()), versionDeIos = 27)
        assertTrue(hay("«$NOMBRE_DEL_ATAJO_DE_MENSAJES»"))
        assertEquals("$NOMBRE_DEL_ATAJO_DE_MENSAJES.shortcut", com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_MENSAJES)
        // El botón abre RUTA_DEL_ATAJO_DE_MENSAJES: decodificada, termina en el archivo con el nombre del atajo.
        assertEquals(
            "/atajos/${com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_MENSAJES}",
            com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_MENSAJES.replace("%20", " "),
        )
        assertFalse(hay("Movi · Avisar"), "el nombre viejo con «·» ya no está")
    }

    @Test
    fun `lo que nombraba un banco sin forma de aviso tiene su frase`() {
        montar(
            ServerDeCodigos(
                CodigoDeCaptura(vigente = true, creadoEn = CREADO),
                ResumenDeAtajosDescartados(grupos = listOf(GrupoDeAtajosDescartados("SIN_FORMA_DE_AVISO", 3))),
            ),
        )
        assertEquals(
            "3 mensajes nombraban un banco pero no tenían la forma de un aviso en los últimos 30 días.",
            textoDe(TAG_ATAJOS_DESCARTADOS),
        )
    }

    /**
     * La versión de iOS por el user agent: solo lo seguro. Los de Safari 26 son los de las fuentes (WebKit bug
     * 298260 y 51Degrees): el sistema congelado en 18_6 y «Version/26.0».
     */
    @Test
    fun `la version de iOS sale del user agent solo cuando es segura`() {
        fun ua(sistema: String, cola: String) =
            "Mozilla/5.0 (iPhone; CPU iPhone OS $sistema like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) $cola"
        // Safari 26 y 27: el sistema congelado, la versión de Safari manda.
        assertEquals(26, versionDeIosDelUserAgent(ua("18_6", "Version/26.0 Mobile/15E148 Safari/604.1")))
        assertEquals(27, versionDeIosDelUserAgent(ua("18_7", "Version/27.0.1 Mobile/15E148 Safari/604.1")))
        // Antes del congelamiento, el sistema es el real.
        assertEquals(17, versionDeIosDelUserAgent(ua("17_4", "Version/17.4 Mobile/15E148 Safari/604.1")))
        assertEquals(16, versionDeIosDelUserAgent(ua("16_6", "Version/16.6 Mobile/15E148 Safari/604.1")))
        assertEquals(18, versionDeIosDelUserAgent(ua("18_5", "Version/18.5 Mobile/15E148 Safari/604.1")))
        // Chrome en iOS: sin «Version/»; un 26 no puede ser el valor congelado.
        assertEquals(26, versionDeIosDelUserAgent(ua("26_0", "CriOS/140.0.7339.122 Mobile/15E148 Safari/604.1")))
        // Un 18 sin la versión de Safari puede ser un iOS 26 o 27 congelado: no se sabe.
        assertEquals(null, versionDeIosDelUserAgent(ua("18_6", "CriOS/140.0.7339.122 Mobile/15E148 Safari/604.1")))
        assertEquals(null, versionDeIosDelUserAgent(ua("18_6", "Mobile/15E148 Instagram 300.0")))
        // Un «Version/» de otro navegador no cuenta.
        assertEquals(null, versionDeIosDelUserAgent(ua("18_6", "Version/26.0 Mobile/15E148 FxiOS/140.0 Safari/605.1.15")))
        // Tampoco el de Opera, Yandex ni Firefox Focus/Klar, que pueden traer su propio «Version/».
        listOf(
            "Version/26.0 Mobile/15E148 OPT/5.3.0 Safari/604.1",
            "Version/26.0 YaBrowser/25.6.0.0 Mobile/15E148 Safari/604.1",
            "Version/26.0 Mobile/15E148 Focus/140.0 Safari/605.1.15",
        ).forEach { assertEquals(null, versionDeIosDelUserAgent(ua("18_6", it)), it) }
        // Ni un iPad (se presenta como Mac) ni una computadora.
        assertEquals(null, versionDeIosDelUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/26.0 Safari/605.1.15"))
        assertEquals(null, versionDeIosDelUserAgent("Mozilla/5.0 (Linux; Android 14) Chrome/140 Mobile Safari/537.36"))

        assertEquals(listOf(VarianteDeLaAutomatizacion.IOS_27_O_MAS), variantesDeLaGuia(27).aLaVista)
        assertEquals(listOf(VarianteDeLaAutomatizacion.IOS_27_O_MAS), variantesDeLaGuia(28).aLaVista)
        assertEquals(listOf(VarianteDeLaAutomatizacion.IOS_17_A_26), variantesDeLaGuia(26).aLaVista)
        assertEquals(listOf(VarianteDeLaAutomatizacion.IOS_17_A_26), variantesDeLaGuia(17).aLaVista)
        assertEquals(VarianteDeLaAutomatizacion.ANTES_DE_IOS_17, variantesDeLaGuia(16).aLaVista.first())
        assertEquals(VarianteDeLaAutomatizacion.IOS_27_O_MAS, variantesDeLaGuia(null).aLaVista.first())
        assertTrue(variantesDeLaGuia(null).detras.isEmpty())
    }

    /**
     * Vuelta 114 · la línea de arriba de «Mensajes del banco» sale del código que lee esta sección (una sola
     * lectura por pantalla): se entera de lo leído y de cada código creado o desactivado.
     */
    @Test
    fun `la seccion le avisa a la pantalla el codigo leido, el creado y el desactivado`() {
        val server = object : ServerDeCodigos(CodigoDeCaptura(vigente = true, creadoEn = CREADO, ultimoUso = AHORA)) {
            var lecturas = 0
            override suspend fun getCodigoDeCaptura(): CodigoDeCaptura { lecturas++; return actual }
        }
        montar(server)
        assertEquals(1, server.lecturas, "una sola lectura")
        assertEquals(AHORA, avisados.last()?.ultimoUso)
        tocar(DESACTIVAR_EL_CODIGO)
        tocar(DESACTIVAR_EL_CODIGO, elUltimo = true)
        assertEquals(false, avisados.last()?.vigente, "al desactivar, la pantalla deja de decir que llegan por el atajo")
        tocar(CREAR_TU_CODIGO)
        assertEquals(true, avisados.last()?.vigente)
        assertEquals(null, avisados.last()?.ultimoUso, "un código nuevo todavía no mandó nada")
        assertEquals(1, server.lecturas, "crear y desactivar no vuelven a leer")
    }
}
