package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.platform.ComoSalioElMensaje
import com.jvillada.movi.platform.HojaDeCompartir
import com.jvillada.movi.platform.LocalHojaDeCompartir
import com.jvillada.movi.shared.model.ConexionDelEspacio
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.InvitacionCreada
import com.jvillada.movi.shared.model.InvitacionPendiente
import com.jvillada.movi.shared.model.InvitacionVista
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El hogar conectado en pantalla (vuelta 131)
 *
 * La fila «Invita a Caro a Movi» y su hoja (el mensaje con el código), la invitación pendiente con
 * «Mandar de nuevo» y «Cancelar», «Tengo un código» → ver → «Unirme», «Anotó Caro» sin quitar ni
 * deshacer, y «Desconectar» que pregunta antes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1600dp-xhdpi")
class HogarConectadoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )
    private val conectada = casa.copy(conexion = ConexionDelEspacio("dst_caro", "Caro", 1_760_000_000_000L))

    private fun gasto(id: String, pagadoPor: String, monto: Long, eventoId: String? = null, anotadoPor: String? = null) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
        descripcion = if (pagadoPor == MIEMBRO_YO) "Mercado Éxito" else "Mercado del sábado",
        fecha = 1_761_000_000_000L, eventoId = eventoId, partes = partesDelGasto(monto, casa, pagadoPor), anotadoPor = anotadoPor,
    )

    private fun detalleDe(espacio: Espacio, gastos: List<GastoDelEspacio>, liquidaciones: List<LiquidacionDelEspacio> = emptyList()) =
        DetalleDelEspacio(
            espacio = espacio,
            saldo = saldoDelEspacio(espacio, gastos, liquidaciones),
            delPeriodo = saldoDelEspacio(espacio, gastos, emptyList()),
            gastos = gastos,
            liquidaciones = liquidaciones,
        )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private class HojaDePrueba : HojaDeCompartir {
        val mandados = mutableListOf<String>()
        val copiados = mutableListOf<String>()
        override val abreLaHojaDelSistema: Boolean = true
        override fun compartir(texto: String) { mandados += texto }
        override fun copiar(texto: String) { copiados += texto }
        override fun mandarMensaje(texto: String): ComoSalioElMensaje = ComoSalioElMensaje.POR_LA_HOJA.also { mandados += texto }
    }

    private open inner class Repo(var detalle: DetalleDelEspacio) : RepositorioDePrueba() {
        var pendiente: InvitacionPendiente? = null
        val creadas = mutableListOf<String>()
        val canceladas = mutableListOf<String>()
        val aceptados = mutableListOf<String>()
        val desconectados = mutableListOf<String>()
        override suspend fun getEspacios(): List<Espacio> = listOf(detalle.espacio)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = detalle
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getInvitacionPendiente(espacioId: String): InvitacionPendiente? = pendiente
        override suspend fun crearInvitacion(espacioId: String): InvitacionCreada {
            creadas += espacioId
            return InvitacionCreada("K7P3Q9", 1_762_966_800_000L).also { pendiente = InvitacionPendiente(it.venceEn) }
        }
        override suspend fun cancelarInvitacion(espacioId: String) {
            canceladas += espacioId
            pendiente = null
        }
        override suspend fun getInvitacion(codigo: String): InvitacionVista = InvitacionVista("Casa con Caro", "Juan")
        override suspend fun aceptarInvitacion(codigo: String): Espacio {
            aceptados += codigo
            return Espacio(id = "esp_de_caro", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO)))
        }
        override suspend fun desconectarEspacio(espacioId: String) { desconectados += espacioId }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocarBoton(texto: String) {
        val nodos = composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
        val cuantos = nodos.fetchSemanticsNodes().size
        check(cuantos > 0) { "no hay un botón «$texto»" }
        nodos[cuantos - 1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarTag(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun amarillos(): Int {
        fun cuenta(n: SemanticsNode): Int = (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf(::cuenta)
        return cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    private fun montarElGrupo(hoja: HojaDeCompartir? = null) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHojaDeCompartir provides hoja) {
                MoviTheme { Box(Modifier.fillMaxSize()) { EspacioScreen(id = casa.id, pagoOtro = false) } }
            }
        }
    }

    @Test
    fun `la fila de invitar abre la hoja y Mandar la invitacion genera el codigo y manda el mensaje`() {
        val gastos = listOf(gasto("g1", MIEMBRO_YO, 2_100_000, "e1"), gasto("g2", "dst_caro", 1_320_000))
        val repo = Repo(detalleDe(casa, gastos))
        Repositories.sustitutoDePrueba = repo
        val hoja = HojaDePrueba()
        montarElGrupo(hoja)

        esperarTexto("Invita a Caro a Movi")
        esperarTexto(SUBTITULO_DE_INVITAR)
        assertEquals(1, amarillos(), "un solo amarillo: el del saldo")
        tocarTag(TAG_INVITAR)

        esperarTag(TAG_HOJA_DE_INVITAR)
        LO_QUE_VERAN_LOS_DOS.forEach { esperarTexto(it) }
        assertTrue(repo.creadas.isEmpty(), "abrir la hoja no genera nada")
        tocarBoton("Mandar la invitación")
        composeRule.waitUntil(timeoutMillis = 5_000) { hoja.mandados.isNotEmpty() }

        assertEquals(listOf("esp_casa"), repo.creadas)
        val mensaje = hoja.mandados.single()
        assertTrue(mensaje.startsWith("Te invito a «Casa con Caro» en Movi."), mensaje)
        assertTrue("escribe K7P3Q9. Vence el 12 nov." in mensaje, mensaje)

        // Ya mandada, la fila dice que está pendiente, con «Mandar de nuevo» y «Cancelar».
        esperarTag(TAG_INVITACION_PENDIENTE)
        esperarTexto("Invitación enviada · vence el 12 nov")
        assertEquals(1, amarillos(), "la pendiente no suma amarillo")
        tocarBoton("Cancelar")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.canceladas.isNotEmpty() }
        esperarTag(TAG_INVITAR)
    }

    @Test
    fun `Copiar el codigo copia el mismo codigo que se manda`() {
        val repo = Repo(detalleDe(casa, emptyList()))
        Repositories.sustitutoDePrueba = repo
        val hoja = HojaDePrueba()
        montarElGrupo(hoja)
        esperarTexto("Invita a Caro a Movi")
        tocarTag(TAG_INVITAR)
        esperarTag(TAG_HOJA_DE_INVITAR)

        tocarBoton("Copiar el código")
        composeRule.waitUntil(timeoutMillis = 5_000) { hoja.copiados.isNotEmpty() }
        assertEquals(listOf("K7P3Q9"), hoja.copiados)
        esperarTexto("Código copiado: K7P3Q9")
        tocarBoton("Mandar la invitación")
        assertEquals(1, repo.creadas.size, "mandar después de copiar usa el mismo código")
    }

    @Test
    fun `un grupo de tres dice que por ahora se conectan los de dos y no ofrece nada`() {
        val tres = casa.copy(miembros = casa.miembros + MiembroDelEspacio("dst_mama", "Mamá"))
        Repositories.sustitutoDePrueba = Repo(detalleDe(tres, emptyList()))
        montarElGrupo()
        esperarTag(TAG_GRUPOS_DE_DOS)
        esperarTexto(POR_AHORA_GRUPOS_DE_DOS)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_INVITAR, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `Tengo un codigo limpia lo escrito, muestra la invitacion y Unirme abre el grupo nuevo`() {
        val repo = Repo(detalleDe(casa, emptyList()))
        Repositories.sustitutoDePrueba = repo
        val navegado = mutableListOf<Screen>()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { EspaciosScreen(onNavigate = { navegado += it }) } } }

        esperarTag(TAG_TENGO_UN_CODIGO)
        tocarTag(TAG_TENGO_UN_CODIGO)
        esperarTag(TAG_CAMPO_DEL_CODIGO)
        composeRule.onNodeWithTag(TAG_CAMPO_DEL_CODIGO, useUnmergedTree = true).performTextInput("k7p-3q 9")
        composeRule.waitForIdle()
        esperarTexto("K7P3Q9")
        tocarBoton("Ver la invitación")

        esperarTexto("Juan te invita a «Casa con Caro»")
        LO_QUE_VERAN_LOS_DOS.forEach { esperarTexto(it) }
        assertTrue(hay("Ahora no"))
        tocarBoton("Unirme")
        composeRule.waitUntil(timeoutMillis = 5_000) { navegado.isNotEmpty() }
        assertEquals(listOf("K7P3Q9"), repo.aceptados)
        assertEquals(listOf<Screen>(Screen.Espacio("esp_de_caro")), navegado)
    }

    @Test
    fun `sin grupos tambien se puede escribir un codigo`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEspacios(): List<Espacio> = emptyList()
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { EspaciosScreen(onNavigate = {}) } } }
        esperarTexto("Comparte gastos con alguien")
        esperarTag(TAG_TENGO_UN_CODIGO)
    }

    @Test
    fun `en el grupo conectado lo que anoto Caro dice Anoto Caro y no se quita ni se deshace`() {
        val deCaro = gasto("g_caro", "dst_caro", 60_000, anotadoPor = "dst_caro")
        val propio = gasto("g1", MIEMBRO_YO, 100_000)
        val saldadoDeCaro = LiquidacionDelEspacio(id = "l_caro", espacioId = casa.id, de = "dst_caro", a = MIEMBRO_YO, monto = 5_000, fecha = 1_761_100_000_000L, anotadoPor = "dst_caro")
        Repositories.sustitutoDePrueba = Repo(detalleDe(conectada, listOf(propio, deCaro), listOf(saldadoDeCaro)))
        montarElGrupo()

        esperarTag(TAG_CONECTADO_CON)
        esperarTexto("Conectado con Caro")
        esperarTexto("Anotó Caro")
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_INVITAR, useUnmergedTree = true).fetchSemanticsNodes().size, "conectado, no se invita")
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_DESHACER_PAGO, useUnmergedTree = true).fetchSemanticsNodes().size, "el saldado de Caro no se deshace")

        // El gasto de Caro: su hoja dice quién lo anotó y no ofrece quitarlo.
        composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasText("Mercado del sábado")), useUnmergedTree = true)
            .fetchSemanticsNodes().let { check(it.isNotEmpty()) }
        composeRule.onAllNodesWithTag(TAG_GASTO_DEL_ESPACIO, useUnmergedTree = true).fetchSemanticsNodes()
        val filas = composeRule.onAllNodesWithTag(TAG_GASTO_DEL_ESPACIO, useUnmergedTree = true)
        val indice = (0 until filas.fetchSemanticsNodes().size).first { i ->
            filas[i].fetchSemanticsNode().let { n ->
                fun textos(x: SemanticsNode): String = (x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text } ?: "") + x.children.joinToString { textos(it) }
                "Mercado del sábado" in textos(n)
            }
        }
        filas[indice].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        esperarTag(TAG_DETALLE_DEL_GASTO)
        esperarTag(TAG_ANOTO_EL_OTRO)
        assertTrue(!hay(QUITAR_DEL_ESPACIO), "no se ofrece quitar lo que anotó Caro")
    }

    @Test
    fun `con el grupo conectado, Pago otro no ofrece a Caro y dice que ella lo anota desde su Movi`() {
        Repositories.sustitutoDePrueba = Repo(detalleDe(conectada, emptyList()))
        montarElGrupo()
        esperarTag(TAG_LO_ANOTA_DESDE_SU_MOVI)
        esperarTexto("Caro anota lo que paga desde su Movi")
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ANOTAR_LO_QUE_PAGO_OTRO, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `la hoja de Pago otro de un grupo conectado no deja anotar por Caro`() {
        Repositories.sustitutoDePrueba = Repo(detalleDe(conectada, emptyList()))
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { HojaPagoOtro(espacio = conectada, onDismiss = {}, onGuardado = {}) } } }
        esperarTag(TAG_LO_ANOTA_DESDE_SU_MOVI)
        assertTrue(!hay("Guardar"), "no hay nada que guardar")
    }

    @Test
    fun `la marca dice con quien y su nombre en Movi, y el saldo dice lo de antes de conectarse`() {
        val conNombre = conectada.copy(conexion = conectada.conexion!!.copy(nombreEnMovi = "Carolina"))
        val propio = gasto("g1", MIEMBRO_YO, 100_000, "e1")
        Repositories.sustitutoDePrueba = Repo(detalleDe(conNombre, listOf(propio)).copy(deAntesDeConectarse = 30_000))
        montarElGrupo()
        esperarTexto("Conectado con Caro · Carolina en Movi")
        esperarTag(TAG_DE_ANTES_DE_CONECTARSE)
        esperarTexto("Incluye \$30.000 de antes de conectarse: Caro no lo ve.")
    }

    @Test
    fun `Si saldar sobre un saldado de Caro lo vincula y no crea otro`() {
        val propuesta = com.jvillada.movi.shared.model.PropuestaDeSaldar(
            eventoId = "e_de_caro", de = "dst_caro", a = MIEMBRO_YO, monto = 50_000, fecha = 1_761_100_000_000L,
            descripcion = "Transferencia de Caro", liquidacionId = "l_caro", espacioId = casa.id, cruzada = true,
        )
        val repo = object : Repo(detalleDe(conectada, emptyList()).copy(propuestas = listOf(propuesta))) {
            val vinculados = mutableListOf<Triple<String, String, String>>()
            val liquidaciones = mutableListOf<LiquidacionDelEspacio>()
            override suspend fun vincularLiquidacion(espacioId: String, liquidacionId: String, eventoId: String): LiquidacionDelEspacio {
                vinculados += Triple(espacioId, liquidacionId, eventoId)
                return LiquidacionDelEspacio(id = liquidacionId, de = "dst_caro", a = MIEMBRO_YO, monto = 50_000, eventoId = eventoId)
            }
            override suspend fun liquidarEspacio(espacioId: String, liquidacion: LiquidacionDelEspacio): LiquidacionDelEspacio =
                liquidacion.also { liquidaciones += it }
        }
        Repositories.sustitutoDePrueba = repo
        montarElGrupo()
        esperarTexto("Sí, saldar")
        tocarBoton("Sí, saldar")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.vinculados.isNotEmpty() }
        assertEquals(listOf(Triple("esp_casa", "l_caro", "e_de_caro")), repo.vinculados)
        assertTrue(repo.liquidaciones.isEmpty(), "no se marcó otro saldado")
    }

    @Test
    fun `si Caro ya lo marco como saldado, se ofrece vincular la transferencia en vez de marcar otro`() {
        val propio = gasto("g1", MIEMBRO_YO, 100_000, "e1")
        val repo = object : Repo(detalleDe(conectada, listOf(propio))) {
            override suspend fun liquidarEspacio(espacioId: String, liquidacion: LiquidacionDelEspacio): LiquidacionDelEspacio =
                throw com.jvillada.movi.shared.repository.ApiException(409, com.jvillada.movi.shared.model.YA_LO_MARCO_LA_OTRA_PERSONA + "l_caro")
        }
        Repositories.sustitutoDePrueba = repo
        montarElGrupo()
        esperarTexto("Marcar como saldado")
        tocarBoton("Marcar como saldado")
        esperarTexto("¿Caro ya te pagó")
        tocarBoton("Sí, ya me pagó")
        esperarTag(TAG_YA_LO_MARCO)
        esperarTexto("Caro ya lo marcó como saldado")
        esperarTexto("Vincular tu transferencia")
    }

    @Test
    fun `el campo del codigo dice que letras no existen`() {
        Repositories.sustitutoDePrueba = Repo(detalleDe(casa, emptyList()))
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { HojaTengoUnCodigo(onDismiss = {}, onUnido = {}) } } }
        esperarTag(TAG_CAMPO_DEL_CODIGO)
        composeRule.onNodeWithTag(TAG_CAMPO_DEL_CODIGO, useUnmergedTree = true).performTextInput("k7p3o9")
        esperarTexto("Los códigos de Movi no usan 0, O, 1, I ni L")
    }

    @Test
    fun `Desconectar pregunta antes, dice cuanto deja de verse y como queda el saldo`() {
        val deCaro = gasto("g_caro", "dst_caro", 60_000, anotadoPor = "dst_caro")
        val propio = gasto("g1", MIEMBRO_YO, 100_000, "e1")
        val repo = Repo(detalleDe(conectada, listOf(propio, deCaro)))
        Repositories.sustitutoDePrueba = repo
        montarElGrupo()

        esperarTag(TAG_EDITAR_GRUPO)
        tocarTag(TAG_EDITAR_GRUPO)
        esperarTag(TAG_DESCONECTAR)
        tocarTag(TAG_DESCONECTAR)

        esperarTexto("¿Desconectar de Caro?")
        esperarTexto("Dejarás de ver 1 gasto que anotó Caro.")
        esperarTexto("El saldo pasa de \$20.000 a tu favor a \$50.000 a tu favor.")
        assertTrue(repo.desconectados.isEmpty(), "preguntar no desconecta")
        tocarBoton("Cancelar")
        assertTrue(repo.desconectados.isEmpty())

        tocarTag(TAG_EDITAR_GRUPO)
        esperarTag(TAG_DESCONECTAR)
        tocarTag(TAG_DESCONECTAR)
        esperarTexto("¿Desconectar de Caro?")
        tocarBoton("Desconectar")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.desconectados.isNotEmpty() }
        assertEquals(listOf("esp_casa"), repo.desconectados)
    }
}
