package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.COMO_BAJARLO
import com.jvillada.movi.shared.model.COMO_BAJAR_EL_ARCHIVO_QUE_MOVI_LEE_MEJOR
import com.jvillada.movi.shared.model.COMO_BAJO_MI_EXTRACTO
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.ExtractoPorSubir
import com.jvillada.movi.shared.model.FRASE_EXTRACTO_INCOMPLETO
import com.jvillada.movi.shared.model.GUIA_DE_BANCOLOMBIA
import com.jvillada.movi.shared.model.GUIA_DE_BANCOLOMBIA_TARJETA
import com.jvillada.movi.shared.model.GUIA_DE_NU
import com.jvillada.movi.shared.model.GUIA_DE_NU_CUENTA
import com.jvillada.movi.shared.model.GuiaDelExtracto
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MOVI_LO_LEE_AL_INSTANTE
import com.jvillada.movi.shared.model.PASOS_GENERICOS
import com.jvillada.movi.shared.model.PDF_CON_CLAVE
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import com.jvillada.movi.ui.dashboard.AccionDeLaPropuesta
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.dashboard.SUBIR_EL_EXTRACTO
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import com.jvillada.movi.ui.documentos.ArchivoElegido
import com.jvillada.movi.ui.documentos.DocumentosScreen
import com.jvillada.movi.ui.documentos.SubirDocumentoSheet
import com.jvillada.movi.ui.papeles.ArchivoCompartido
import com.jvillada.movi.ui.papeles.HojaLeyendoElPapel
import com.jvillada.movi.ui.papeles.ResultadoDelPapel
import com.jvillada.movi.ui.papeles.ofreceLaGuiaDelExtracto
import com.jvillada.movi.ui.sdui.PropuestasDelInicioSection
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Vuelta 130 · «Cómo bajar tu extracto», banco por banco, en la app
 *
 * (Reemplaza la prueba de la vuelta 107, que tenía la guía en tres lugares y solo para Bancolombia y Nu.)
 *
 * - **Elegir la guía**: la cuenta propuesta o elegida dice el banco y el producto; sin ella, la lista.
 * - **La hoja**: el banco, la pastilla, los pasos, qué subir, la clave, «Si no lo encuentras», el
 *   selector de producto y **un solo amarillo**, «Subir el extracto».
 * - **Las entradas**: la hoja de subida, la propuesta del extracto que falta (sin quitarle el amarillo a
 *   «Subir el extracto»), el error de lectura, la fila fija de Documentos, el detalle de una cuenta y la
 *   revisión de un extracto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class ComoSacarElExtractoEnLaAppTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private val nuAhorros = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 0)
    private val nuTarjeta = Account("n2", "Nu Tarjeta", AccountType.CREDIT_CARD, 0)
    private val bancolombia = Account("b1", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0)

    private fun amarillos(): List<SemanticsNode> {
        fun bajar(n: SemanticsNode): List<SemanticsNode> =
            (if (n.config.getOrNull(LlevaElAmarilloKey) == true) listOf(n) else emptyList()) + n.children.flatMap(::bajar)
        return bajar(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    private fun textoDe(n: SemanticsNode): String {
        fun bajar(x: SemanticsNode): List<String> =
            x.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + x.children.flatMap(::bajar)
        return bajar(n).joinToString(" ")
    }

    /** Toca lo tocable que dice [texto] (un `Boton`, una píldora, una fila): con la acción, como el resto del repo. */
    private fun tocar(texto: String) {
        // El más adentro de los que dicen [texto] (la hoja entera y su panel también son tocables).
        val nodo = composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyDescendant(hasText(texto))), useUnmergedTree = true)
            .fetchSemanticsNodes().last()
        composeRule.runOnIdle { nodo.config[SemanticsActions.OnClick].action!!.invoke() }
        composeRule.waitForIdle()
    }

    private fun tocarTag(tag: String) {
        composeRule.onNode(hasTestTag(tag), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** La variante del `Boton` que dice [texto]. */
    private fun varianteDe(texto: String): VarianteDeBoton? {
        fun bajar(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::bajar)
        return bajar(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            .firstOrNull { it.config.getOrNull(VarianteDeBotonKey) != null && textoDe(it) == texto }
            ?.config?.getOrNull(VarianteDeBotonKey)
    }

    private fun esperarTexto(texto: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    // ── Elegir la guía ───────────────────────────────────────────────────────────────────────────

    @Test
    fun la_hoja_de_subida_abre_la_guia_de_la_cuenta_y_sin_cuenta_la_lista() {
        val cuentas = listOf(nuAhorros, nuTarjeta, bancolombia)
        assertSame(GUIA_DE_NU_CUENTA, guiaParaLaHojaDeSubida("n1", cuentas))
        assertSame(GUIA_DE_NU, guiaParaLaHojaDeSubida("n2", cuentas))
        assertSame(GUIA_DE_BANCOLOMBIA, guiaParaLaHojaDeSubida("b1", cuentas))
        assertNull(guiaParaLaHojaDeSubida(null, cuentas))
        assertNull(guiaParaLaHojaDeSubida("no-existe", cuentas))
        // Un papel que no se leyó: su cuenta; si no, lo que dice el nombre del archivo; si no, la lista.
        assertSame(GUIA_DE_NU_CUENTA, guiaDelPapelQueNoSeLeyo("CuentaNu_CRS119_2026-09.pdf"))
        assertSame(GUIA_DE_NU, guiaDelPapelQueNoSeLeyo("Nu_2026-09-17.pdf"))
        assertSame(GUIA_DE_BANCOLOMBIA_TARJETA, guiaDelPapelQueNoSeLeyo("extracto.pdf", Account("t", "AMEX", AccountType.CREDIT_CARD, 0)))
        assertNull(guiaDelPapelQueNoSeLeyo("extracto.pdf"))
    }

    // ── La hoja ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun la_hoja_dice_todo_y_lleva_un_solo_amarillo_que_sube_el_extracto() {
        var subio = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = UserProfile(id = "u", email = "u@movi.test", name = "Dueño", avatarColor = "#000000", periodCutoffDay = 25)
        }
        composeRule.setContent {
            MoviTheme {
                HojaDeComoSacarElExtracto(
                    inicial = GUIA_DE_BANCOLOMBIA_TARJETA, onDismiss = {}, onSubir = { subio++ },
                    corteDeLaTarjeta = LocalDate(2026, 1, 3),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cómo bajar tu extracto de Bancolombia").assertExists()
        composeRule.onNodeWithTag(TAG_LECTURA_DE_LA_GUIA, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(MOVI_LO_LEE_AL_INSTANTE).assertExists()
        composeRule.onNodeWithText("Ve a «Tarjetas de crédito» y toca la tarjeta.").assertExists()
        composeRule.onNodeWithText(QUE_ARCHIVO_SUBIR).assertExists()
        composeRule.onNodeWithText(GUIA_DE_BANCOLOMBIA_TARJETA.queSubir).assertExists()
        composeRule.onNodeWithText(LA_CLAVE).assertExists()
        composeRule.onNodeWithText(SI_NO_LO_ENCUENTRAS).assertExists()
        // El corte de la tarjeta (3 de enero) es más viejo que el inicio del período: manda ese.
        composeRule.onNodeWithTag(TAG_RANGO_IDEAL_DEL_EXTRACTO, useUnmergedTree = true)
            .assertTextContains("Rango ideal: desde tu último corte, el 3 de enero, hasta hoy.")
        // Un solo amarillo, el de subir.
        val amarillos = amarillos()
        assertEquals(1, amarillos.size)
        assertTrue(textoDe(amarillos.single()).contains("Subir el extracto"))
        tocarTag(TAG_SUBIR_DESDE_LA_GUIA)
        assertEquals(1, subio)
    }

    @Test
    fun el_selector_de_producto_cambia_la_guia_del_banco() {
        composeRule.setContent { MoviTheme { HojaDeComoSacarElExtracto(inicial = GUIA_DE_BANCOLOMBIA_TARJETA, onDismiss = {}) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_PRODUCTOS_DE_LA_GUIA, useUnmergedTree = true).assertExists()
        tocar("Cuenta")
        composeRule.onNodeWithText("Abre la pestaña «Movimientos» y toca «Descargar».").assertExists()
        // La segunda forma de Bancolombia: el extracto mensual, con IA.
        composeRule.onNodeWithText("También sirve el extracto mensual").assertExists()
        // Sin onSubir ni la cáscara, no hay botón (ningún amarillo que no haga nada).
        assertTrue(amarillos().isEmpty())
    }

    @Test
    fun sin_banco_la_hoja_ofrece_la_lista_con_buscador_y_mi_banco_no_esta() {
        composeRule.setContent { MoviTheme { HojaDeComoSacarElExtracto(inicial = null, onDismiss = {}, onSubir = {}) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(ELIGE_TU_BANCO).assertExists()
        composeRule.onNodeWithTag(TAG_LISTA_DE_BANCOS_DE_LA_GUIA, useUnmergedTree = true).assertExists()
        // En la lista no hay amarillo: todavía no hay qué subir.
        assertTrue(amarillos().isEmpty())
        composeRule.onNodeWithTag(TAG_BUSCAR_BANCO_DE_LA_GUIA).performTextInput("bogota")
        composeRule.waitForIdle()
        tocar("Banco de Bogotá")
        composeRule.onNodeWithText("Cómo bajar tu extracto de Banco de Bogotá").assertExists()
        composeRule.onNodeWithText("Abre «Documentos» → «Extractos».").assertExists()
        // «Otro banco» vuelve a la lista, y «Mi banco no está» abre lo genérico, sin pasos inventados.
        tocarTag(TAG_OTRO_BANCO_DE_LA_GUIA)
        tocarTag(TAG_MI_BANCO_NO_ESTA)
        composeRule.onNodeWithText("Cómo bajar tu extracto de tu banco").assertExists()
        composeRule.onNodeWithText(PASOS_GENERICOS.first()).assertExists()
    }

    // ── 1. La hoja de subida ─────────────────────────────────────────────────────────────────────

    @Test
    fun la_hoja_de_subida_tiene_el_enlace_y_abre_la_guia_de_la_cuenta_propuesta() {
        var abierta: GuiaDelExtracto? = null
        var pedida = false
        composeRule.setContent {
            MoviTheme {
                SubirDocumentoSheet(
                    archivo = ArchivoElegido("Extracto_CuentaNu_2026-09.pdf", ByteArray(10), "application/pdf"),
                    cuentas = listOf(nuAhorros, nuTarjeta),
                    onDismiss = {},
                    onSubir = { _, _ -> },
                    onComoBajarlo = { abierta = it; pedida = true },
                )
            }
        }
        composeRule.waitForIdle()
        tocarTag(TAG_ENLACE_A_LA_GUIA)
        assertTrue("el enlace no abrió la guía", pedida)
        assertSame(GUIA_DE_NU_CUENTA, abierta)
        // El enlace es fantasma: el único amarillo sigue siendo «Subir».
        assertEquals(1, amarillos().size)
        assertTrue(textoDe(amarillos().single()), textoDe(amarillos().single()).contains("Subir"))
        assertEquals(VarianteDeBoton.Fantasma, varianteDe(COMO_BAJO_MI_EXTRACTO))
    }

    // ── 2. La propuesta del extracto que falta ───────────────────────────────────────────────────

    private fun extracto(banco: String, cuenta: String = "Master Black 3684") = ExtractoPorSubir("tc", cuenta, banco, "2026-10-17")

    @Test
    fun la_propuesta_ofrece_como_bajarlo_de_todo_banco() {
        fun propuesta(e: ExtractoPorSubir) =
            propuestasDelInicio(DashboardData(extractosPorSubir = listOf(e))).single { it.tipo == TipoDePropuesta.EXTRACTO }
        val p = propuesta(extracto("Bancolombia"))
        assertEquals(SUBIR_EL_EXTRACTO, p.accion)
        assertEquals(COMO_BAJARLO, p.accionTerciaria)
        val accion = p.alTocarTerciaria as AccionDeLaPropuesta.VerComoSacarElExtracto
        assertSame(GUIA_DE_BANCOLOMBIA_TARJETA, accion.guia)
        assertEquals("2026-10-17", accion.corte)
        // La tarjeta Nu.
        assertSame(GUIA_DE_NU, (propuesta(extracto("", "Nu Tarjeta")).alTocarTerciaria as AccionDeLaPropuesta.VerComoSacarElExtracto).guia)
        // Un banco que Movi no conoce: la genérica, con el nombre que escribió el dueño.
        val otro = (propuesta(extracto("Banco Inventado")).alTocarTerciaria as AccionDeLaPropuesta.VerComoSacarElExtracto).guia
        assertEquals("Banco Inventado", otro.banco)
        assertEquals(PASOS_GENERICOS, otro.pasos)
    }

    @Test
    fun el_enlace_de_la_propuesta_no_le_gana_a_subir_el_extracto_y_abre_la_guia() {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides false) {
                    Box(Modifier.fillMaxSize()) {
                        PropuestasDelInicioSection(
                            data = DashboardData(extractosPorSubir = listOf(extracto("Bancolombia"))),
                            onNavigate = {},
                            onDescartarCierre = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(COMO_BAJARLO).assertIsDisplayed()
        // En el Inicio el amarillo es de «Gasto» (vuelta 79): «Subir el extracto» sigue siendo la acción de
        // la tarjeta (la secundaria con borde, como toda propuesta), y «Cómo bajarlo», un enlace fantasma.
        // El enlace no agrega ningún amarillo.
        assertEquals(VarianteDeBoton.Secundario, varianteDe(SUBIR_EL_EXTRACTO))
        assertEquals(VarianteDeBoton.Fantasma, varianteDe(COMO_BAJARLO))
        assertTrue(amarillos().isEmpty())
        tocar(COMO_BAJARLO)
        composeRule.onNodeWithTag(TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Ve a «Tarjetas de crédito» y toca la tarjeta.", useUnmergedTree = true).assertExists()
    }

    // ── 3. El error de lectura ───────────────────────────────────────────────────────────────────

    @Test
    fun el_error_de_lectura_ofrece_la_guia_solo_cuando_lo_que_fallo_es_el_archivo() {
        assertTrue(ofreceLaGuiaDelExtracto(ResultadoDelPapel.NoSePudo("x.pdf", FRASE_EXTRACTO_INCOMPLETO, guardado = true)))
        assertTrue(ofreceLaGuiaDelExtracto(ResultadoDelPapel.ConClave("x.pdf", "doc_1")))
        assertFalse(ofreceLaGuiaDelExtracto(ResultadoDelPapel.NoSePudo("x.pdf", com.jvillada.movi.ui.papeles.LECTURA_CON_IA_NO_DISPONIBLE, guardado = true)))
        assertFalse(ofreceLaGuiaDelExtracto(ResultadoDelPapel.NoSePudo("x.pdf", "No pudimos guardarlo: sin conexión", guardado = false)))
    }

    private class RepoQueNoLee(val falla: ApiException) : RepositorioDePrueba() {
        override suspend fun subirPapel(fileName: String, bytes: ByteArray, mimeType: String): Documento =
            Documento(id = "doc_1", nombre = fileName, tipo = TipoDeDocumento.EXTRACTO, mimeType = mimeType, bytes = 5, subidoEn = 0)
        override suspend fun leerPapel(documentoId: String, anotarAunqueEsteAnotado: Boolean): LecturaDelPapel = throw falla
        override suspend fun getEstadoDeLaClaveDeExtractos() = com.jvillada.movi.shared.model.EstadoDeLaClaveDeExtractos()
    }

    private fun leerConFalla(falla: ApiException, nombre: String) {
        Repositories.sustitutoDePrueba = RepoQueNoLee(falla)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaLeyendoElPapel(
                        archivos = listOf(ArchivoCompartido(nombre, byteArrayOf(1, 2, 3), "application/pdf")),
                        onCerrar = {}, onNavigate = {}, onCambio = {},
                    )
                }
            }
        }
    }

    @Test
    fun un_extracto_que_se_corto_ofrece_como_bajar_el_archivo_que_movi_lee_mejor() {
        leerConFalla(ApiException(422, FRASE_EXTRACTO_INCOMPLETO), "Nu_2026-09-17.pdf")
        esperarTexto(COMO_BAJAR_EL_ARCHIVO_QUE_MOVI_LEE_MEJOR)
        tocarTag(TAG_ENLACE_A_LA_GUIA)
        composeRule.onNodeWithText("Cómo bajar tu extracto de Nu").assertExists()
        composeRule.onNodeWithText("Abre «Lo que debes de tu tarjeta».").assertExists()
    }

    @Test
    fun un_pdf_con_clave_tambien_la_ofrece_y_la_ia_caida_no() {
        leerConFalla(ApiException(422, PDF_CON_CLAVE), "CuentaNu_2026-09.pdf")
        esperarTexto(COMO_BAJAR_EL_ARCHIVO_QUE_MOVI_LEE_MEJOR)
    }

    @Test
    fun con_la_ia_caida_no_se_ofrece_la_guia() {
        leerConFalla(ApiException(503, IA_SIN_CREDITO), "Nu_2026-09-17.pdf")
        esperarTexto(com.jvillada.movi.ui.papeles.LECTURA_CON_IA_NO_DISPONIBLE)
        assertTrue(composeRule.onAllNodesWithTag(TAG_ENLACE_A_LA_GUIA, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // ── 4. La entrada fija en Documentos ─────────────────────────────────────────────────────────

    @Test
    fun documentos_tiene_la_entrada_fija_que_abre_la_lista_de_bancos() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = emptyList()
            override suspend fun getAccounts(): List<Account> = emptyList()
            override suspend fun getStatementImports(): List<StatementImport> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) } } }
        esperarTexto(com.jvillada.movi.shared.model.COMO_BAJAR_TUS_EXTRACTOS)
        tocar(com.jvillada.movi.shared.model.COMO_BAJAR_TUS_EXTRACTOS)
        composeRule.onNodeWithText(ELIGE_TU_BANCO, useUnmergedTree = true).assertExists()
    }

    // ── 5. El detalle de una cuenta y la revisión de un extracto ─────────────────────────────────

    private fun revision(banco: String, tarjeta: Boolean = false) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getUserProfile(): UserProfile = UserProfile(id = "u", email = "u@movi.test", name = "Dueño", avatarColor = "#000000")
        }
        val result = StatementParseResult(
            statementId = "st_1",
            bankName = banco,
            period = "Octubre 2026",
            newTransactions = listOf(
                ParsedTransaction("f1", "2026-10-28", "TIENDA LIBERTARIO", 8_100, "COP", TransactionType.EXPENSE, "Comida", "COMPRA EN TIENDA LIBERTARIO", "x"),
            ),
            matches = emptyList(),
            tarjeta = if (tarjeta) com.jvillada.movi.shared.model.TarjetaDelExtracto(banco = banco) else null,
        )
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { StatementReviewScreen(onNavigate = {}, result = result) } }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun la_revision_de_un_extracto_de_la_tarjeta_nu_abre_la_guia_de_la_tarjeta() {
        revision("Nu", tarjeta = true)
        composeRule.onNodeWithTag(TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO).performScrollTo().assertIsDisplayed()
        tocar(TE_FALTAN_MOVIMIENTOS)
        composeRule.onNodeWithTag(TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Abre «Lo que debes de tu tarjeta».", useUnmergedTree = true).assertExists()
    }

    @Test
    fun la_revision_de_un_banco_que_movi_no_reconoce_no_ofrece_nada() {
        revision("Famirios")
        composeRule.onNodeWithTag(TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO).assertDoesNotExist()
    }

    @Test
    fun el_detalle_de_una_cuenta_tiene_la_guia_de_su_banco_y_producto() {
        val g = com.jvillada.movi.shared.model.guiaDelExtractoDeLaCuenta(Account("d", "Davivienda 9418", AccountType.CREDIT_CARD, 0), "Davivienda")
        assertEquals("Davivienda", g!!.banco)
        assertSame(GUIA_DE_BANCOLOMBIA_TARJETA, com.jvillada.movi.shared.model.guiaDelExtractoDeLaCuenta(Account("t", "Master Black 3684", AccountType.CREDIT_CARD, 0), "Bancolombia"))
        assertNull(com.jvillada.movi.shared.model.guiaDelExtractoDeLaCuenta(Account("e", "Efectivo", AccountType.CASH, 0)))
    }
}
