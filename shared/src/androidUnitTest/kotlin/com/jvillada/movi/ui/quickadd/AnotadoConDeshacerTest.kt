package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.repository.MovimientoSinSubir
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AnfitrionDelAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.LocalAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_AVISO_DE_DESHACER
import com.jvillada.movi.ui.components.ds.TAG_DESHACER_DEL_AVISO
import com.jvillada.movi.ui.espacios.REPARTIR
import com.jvillada.movi.ui.espacios.REPARTO_SIN_CONEXION
import com.jvillada.movi.ui.espacios.accionDeRepartir
import com.jvillada.movi.ui.espacios.deshacerLoAnotado
import com.jvillada.movi.ui.espacios.textoDeLoAnotado
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 74 · «Anotado · Restaurantes $32.000 · Repartir con Caro · Deshacer»
 *
 * Guardar en Agregar no confirmaba nada: el gasto se iba sin aviso y sin vuelta atrás. Ahora el aviso
 * de la cáscara lo dice, con «Deshacer» (la anulación de siempre) y, si el dueño tiene grupos y es un
 * gasto, «Repartir con Caro» en un toque (el `…/compartir` de siempre, 50/50 en el grupo), que a su
 * vez se deshace sacándolo del grupo. Sin grupos, nada de repartir; sin señal, el mensaje de la
 * vuelta 24.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xhdpi")
class AnotadoConDeshacerTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val casa = Espacio(
        id = "esp-casa", nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dest-caro", "Caro")),
    )
    private val viaje = Espacio(
        id = "esp-viaje", nombre = "Viaje",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dest-pedro", "Pedro"), MiembroDelEspacio("dest-ana", "Ana")),
    )
    private val gasto = FinancialEvent(
        id = "ev-1", accountId = "a1", type = TransactionType.EXPENSE, amount = 32_000L,
        category = "Restaurantes", description = "Restaurantes", timestamp = 0L,
    )

    private val guardados = mutableListOf<FinancialEvent>()
    private val anulados = mutableListOf<String>()
    private val repartidos = mutableListOf<Pair<String, String>>()
    private val quitados = mutableListOf<Pair<String, String>>()
    private val desvinculados = mutableListOf<List<String>>()

    private inner class Repo(
        private val espacios: List<Espacio> = emptyList(),
        private val sinSenal: Boolean = false,
    ) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getEspacios(): List<Espacio> = espacios
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { guardados += it }
        override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
            anulados += id
            return VoidEvent(id = "void-$id", originalEventId = id, reason = reason, timestamp = 0L)
        }
        override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
            if (sinSenal) throw MovimientoSinSubir(eventoId, rechazo = null)
            repartidos += espacioId to eventoId
            return GastoDelEspacio(
                id = "g1", espacioId = espacioId, monto = 32_000L, eventoId = eventoId,
                partes = mapOf(MIEMBRO_YO to 16_000L, "dest-caro" to 16_000L),
            )
        }
        override suspend fun quitarDelEspacio(espacioId: String, eventoId: String) {
            quitados += espacioId to eventoId
        }
        override suspend fun desvincularAvisos(avisos: List<String>) {
            desvinculados += avisos
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val aviso = AvisoDeDeshacer()

    private fun montarHoja(repo: Repo) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalAvisoDeDeshacer provides aviso) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                QuickAddScreen(onDismiss = {}, onSaved = {})
                            }
                            Spacer(Modifier.height(64.dp))
                        }
                        AnfitrionDelAvisoDeDeshacer(aviso, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(ahorros.name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun guardarUnGasto() {
        tocar("3"); tocar("2"); tocar("000")
        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { guardados.size == 1 }
        composeRule.waitForIdle()
    }

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `guardar un gasto en Agregar dice Anotado y Deshacer lo anula`() {
        montarHoja(Repo())
        guardarUnGasto()

        val guardado = guardados.single()
        assertEquals(textoDeLoAnotado(guardado), aviso.actual?.texto)
        assertTrue(aviso.actual!!.texto.startsWith("Anotado \$"))
        assertEquals(1, conTag(TAG_AVISO_DE_DESHACER))
        assertEquals(0, conTag(TAG_ACCION_DEL_AVISO), "sin grupos no se ofrece repartir")

        composeRule.onNodeWithTag(TAG_DESHACER_DEL_AVISO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { anulados.isNotEmpty() }
        assertEquals(listOf(guardado.id), anulados, "deshacer es la anulación de siempre")
        assertTrue(desvinculados.isEmpty(), "no había aviso del banco que devolver")
    }

    @Test
    fun `con un grupo, Repartir con Caro en un toque y se dice tu parte`() {
        montarHoja(Repo(espacios = listOf(casa)))
        guardarUnGasto()

        composeRule.onNodeWithTag(TAG_ACCION_DEL_AVISO, useUnmergedTree = true).assertExists()
        // Vuelta 79: el aviso de un renglón dice «Repartir»; el lector de pantalla, la acción entera.
        composeRule.onNodeWithContentDescription("Repartir con Caro", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_ACCION_DEL_AVISO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { repartidos.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals(listOf(casa.id to guardados.single().id), repartidos)
        assertEquals("Repartido con Caro · tu parte \$16.000", aviso.actual?.texto)
        composeRule.onNodeWithTag(TAG_DESHACER_DEL_AVISO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { quitados.isNotEmpty() }
        assertEquals(listOf(casa.id to guardados.single().id), quitados, "deshacer el reparto lo saca del grupo")
        assertTrue(anulados.isEmpty(), "deshacer el reparto no anula el gasto")
    }

    // ── La acción, pura ──────────────────────────────────────────────────────────────────

    @Test
    fun `sin grupos o con un traspaso no hay Repartir`() {
        val a = AvisoDeDeshacer()
        assertNull(accionDeRepartir(gasto, emptyList(), a, Repo()))
        assertNull(accionDeRepartir(gasto.copy(transferId = "tr"), listOf(casa), a, Repo()))
        assertNull(accionDeRepartir(gasto.copy(type = TransactionType.INCOME), listOf(casa), a, Repo()))
        assertNull(accionDeRepartir(gasto.copy(espacioId = casa.id), listOf(casa), a, Repo()), "ya está repartido")
    }

    @Test
    fun `con varios grupos, Repartir abre la eleccion`() {
        val accion = accionDeRepartir(gasto, listOf(casa, viaje), AvisoDeDeshacer(), Repo(espacios = listOf(casa, viaje)))
        assertNotNull(accion)
        assertEquals(REPARTIR, accion.rotulo)
        assertEquals(listOf("Casa con Caro", "Viaje"), accion.opciones?.map { it.rotulo })
        // Con más de una persona más, el rótulo nombra el grupo.
        assertEquals("Repartir en Viaje", accionDeRepartir(gasto, listOf(viaje), AvisoDeDeshacer(), Repo())?.rotulo)
    }

    @Test
    fun `sin senal el reparto no se pide y se dice el mensaje de la vuelta 24`() = runBlocking {
        val a = AvisoDeDeshacer()
        a.mostrar(textoDeLoAnotado(gasto), accion = accionDeRepartir(gasto, listOf(casa), a, Repo(sinSenal = true))) {}
        a.tocarLaAccion()
        assertEquals(REPARTO_SIN_CONEXION, a.error)
        assertEquals(textoDeLoAnotado(gasto), a.actual?.texto, "el aviso se queda, con lo anotado")
        assertTrue(repartidos.isEmpty())
    }

    @Test
    fun `deshacer lo de un aviso devuelve el aviso y anula el movimiento`() = runBlocking {
        val repo = Repo()
        deshacerLoAnotado("ev-9", listOf("sms-1"), repo)
        assertEquals(listOf(listOf("sms-1")), desvinculados)
        assertEquals(listOf("ev-9"), anulados)
    }

    @Test
    fun `el texto dice el comercio o, sin el, la categoria`() {
        assertEquals("Anotado \$32.000 · Restaurantes", textoDeLoAnotado(gasto))
        assertEquals("Anotado \$186.400 · Carulla Laureles", textoDeLoAnotado(gasto.copy(merchant = "CARULLA LAURELES", amount = 186_400L)))
    }
}
