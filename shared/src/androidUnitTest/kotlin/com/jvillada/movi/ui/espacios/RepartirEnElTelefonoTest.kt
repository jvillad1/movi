package com.jvillada.movi.ui.espacios

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.db.DatabaseDriverFactory
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.LocalRepository
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # La hoja «Repartir este gasto» en el teléfono (vuelta de diseño 24)
 *
 * En Android e iOS el gasto se guarda primero en la base local y sube después; el reparto lo hace el
 * server. Acá la hoja se monta con los mismos caminos que le pasa Agregar, pero contra el
 * [LocalRepository] de verdad (SQLite de Robolectric) y un server falso que solo reparte lo que ya
 * recibió, como el real. Antes de esta vuelta, «Guardar y repartir» terminaba siempre en «Reintentar
 * el reparto»: el server no conocía el movimiento.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class RepartirEnElTelefonoTest {

    @get:Rule
    val compose = createComposeRule()

    private val uid = "user-hoja-24"

    private class Servidor : RepositorioDePrueba() {
        var conRed = true
        val movimientos = linkedMapOf<String, FinancialEvent>()
        val llamadas = mutableListOf<String>()

        override suspend fun createAccount(account: Account): Account = account

        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            if (!conRed) throw IOException("sin red")
            llamadas += "subir:${event.id}"
            movimientos[event.id] = event
            return event
        }

        override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
            if (!conRed) throw IOException("sin red")
            llamadas += "repartir:$eventoId"
            val e = movimientos[eventoId] ?: throw ApiException(404, "Movimiento no encontrado")
            return GastoDelEspacio(id = "gde_$eventoId", espacioId = espacioId, monto = e.amount, eventoId = eventoId)
        }
    }

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private val gasto = GastoParaRepartir(
        monto = 45_000, categoria = "Mercado", nota = "", momento = 1_790_000_000_000, fecha = "Hoy", cuenta = "Nu",
    )

    private val evento = FinancialEvent(
        id = "ev_hoja", accountId = "nu", type = TransactionType.EXPENSE, amount = 45_000,
        category = "Mercado", description = "Mercado", timestamp = 1_790_000_000_000,
    )

    private val servidor = Servidor()

    private val local: LocalRepository = run {
        DatabaseDriverFactory.init(RuntimeEnvironment.getApplication())
        LocalRepository(createDatabase("repartir-hoja-${System.nanoTime()}.db"), servidor, { uid })
    }

    private var guardados = 0
    private var listo = false
    private var eventoListo: FinancialEvent? = null

    /** Espera [cond]; si no llega, dice qué hay en pantalla (el error de la hoja, si lo hay). */
    private fun esperar(cond: () -> Boolean) {
        runCatching { compose.waitUntil(timeoutMillis = 5_000, condition = cond) }.onFailure {
            val textos = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("todo") { true }, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .mapNotNull { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { t -> t.text } }
            throw AssertionError("no llegó; en pantalla: $textos · server: ${servidor.llamadas}", it)
        }
    }

    private fun montar() {
        compose.setContent {
            MoviTheme {
                HojaRepartirEsteGasto(
                    gasto = gasto,
                    espacios = listOf(casa),
                    // Los mismos dos caminos que Agregar: su guardado (local primero) y el «Repartir».
                    caminos = CaminosDeRepartir(
                        guardarElGasto = { guardados++; local.postEvent(evento) },
                        repartirMovimiento = { espacioId, eventoId -> local.repartirMovimiento(espacioId, eventoId) },
                        anotarGastoDeOtro = { _, _ -> error("pagaste tú") },
                    ),
                    onMonto = {},
                    onDismiss = {},
                    onListo = { eventoListo = it; listo = true },
                )
            }
        }
    }

    @Test
    fun `con señal guardar y repartir sube el gasto, lo reparte y cierra sin reintentar`() {
        montar()
        compose.onNodeWithText("Guardar y repartir").performSemanticsAction(SemanticsActions.OnClick)
        esperar { listo }

        assertEquals(listOf("subir:ev_hoja", "repartir:ev_hoja"), servidor.llamadas)
        assertEquals("ev_hoja", eventoListo?.id)
        assertEquals(1, guardados)
        compose.onNodeWithText("Reintentar el reparto").assertDoesNotExist()
    }

    @Test
    fun `sin señal dice que quedo anotado y que falta conexion, y el reintento no guarda otra vez`() {
        servidor.conRed = false
        montar()
        compose.onNodeWithText("Guardar y repartir").performSemanticsAction(SemanticsActions.OnClick)
        esperar { compose.onAllNodesWithText(REPARTO_SIN_CONEXION, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reintentar el reparto").assertExists()
        assertEquals(emptyList(), servidor.llamadas, "sin el gasto allá, el reparto no se pide")

        servidor.conRed = true
        compose.onNodeWithText("Reintentar el reparto").performSemanticsAction(SemanticsActions.OnClick)
        esperar { listo }

        assertEquals(1, guardados, "el gasto se guardó una sola vez")
        assertEquals(listOf("subir:ev_hoja", "repartir:ev_hoja"), servidor.llamadas)
        assertNull(servidor.movimientos.keys.firstOrNull { it != "ev_hoja" }, "un solo movimiento en el server")
    }
}
