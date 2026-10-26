package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # La ficha de una persona dice para qué le mandaste plata este período
 *
 * A Caro se le gira por tres motivos (el mercado, la cuota de un crédito compartido, la mesada de la
 * tía). El motivo es la categoría de cada envío: la ficha suma lo del período por motivo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LaFichaPorMotivoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "31973270756")

    private fun envio(id: String, monto: Long, categoria: String, hace: Long) = FinancialEvent(
        id = id, accountId = "acc", type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = "Transferencia a Caro", timestamp = ahora - hace, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getMovimientosDelDestino(id: String) = MovimientosDelDestino(
            destino = caro,
            movimientos = listOf(
                envio("m1", 1_000_000L, "Mercado", 60_000L),
                envio("m2", 1_000_000L, "Mercado", 120_000L),
                envio("c1", 1_300_000L, "Cuota de crédito", 180_000L),
                envio("t1", 500_000L, "Tía", 240_000L),
            ),
        )
        override suspend fun getRenombrablesDelDestino(id: String) = MovimientosParaRenombrar("", "", emptyList())
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `lo enviado este periodo, por motivo`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { DetalleDelDestinoSheet(caro, PeriodSettings(), onDismiss = {}, onEditar = null) } }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_POR_MOTIVO_DEL_TERCERO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, cuantos("Lo que le enviaste, por motivo"))
        assertEquals(1, cuantos("Mercado (2)"))
        assertEquals(1, cuantos("Cuota de crédito"))
        assertEquals(1, cuantos("Tía"))
    }
}
