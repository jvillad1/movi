package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Reconciliar con un traspaso avisado del lado que entra** (hueco de #439): «Recibiste $X de tu
 * cuenta *9586 en tu cuenta *8133». La cuenta del aviso es la que recibe; la que nombra «de tu
 * cuenta» es el origen, y si es suya se propone un traspaso.
 */
class ElTraspasoQueEntraEnReconciliarTest {

    private val ahorros = Account("a", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0)
    private val fidu = Account("f", "Fiducuenta 9586", AccountType.INVESTMENT, 0)
    private val dolares = Account("d", "Ahorros USD 7777", AccountType.SAVINGS, 0, currency = "USD")
    private val todas = listOf(ahorros, fidu, dolares)

    private val texto = "Bancolombia: Recibiste \$500.000 de tu cuenta *9586 en tu cuenta *8133 el 04/10/2026 a las 10:15."
    private val leido = ParsedSms(500_000.0, "Desde Fiducuenta 9586", TransactionType.INCOME, "Transferencia", traspasoDesdeId = fidu.id)

    @Test
    fun la_cuenta_del_aviso_es_la_que_recibe_no_la_de_origen() {
        val resuelta = resolverCuentaDelBanco(todas, UsoDeCuenta.DESTINO_DE_INGRESO, "85540", textoDelMensaje = texto)
        assertEquals(ahorros, resuelta.cuenta)
        // En un gasto, «de tu cuenta *9586» sí es la cuenta que el movimiento toca: no cambia nada.
        val retiro = "Bancolombia: Retiraste \$4.200.000 de tu cuenta *9586 Fiducuenta hacia la cuenta *02955068133"
        assertEquals(fidu, resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, "85540", textoDelMensaje = retiro).cuenta)
    }

    @Test
    fun con_origen_propio_propone_el_traspaso_que_entra() {
        val resuelta = resolverCuentaDelBanco(todas, UsoDeCuenta.DESTINO_DE_INGRESO, "85540", textoDelMensaje = texto)
        val origen = assertNotNull(origenPropuestoDelAviso(leido, resuelta, todas))
        assertEquals(fidu, origen)
        assertTrue(pideLaCuentaDeOrigen(leido, TRANSFER_CATEGORY, ahorros))
        val propuestas = assertNotNull(dosPatasPropuestas(leido, TRANSFER_CATEGORY, ahorros, null, null, origenElegido = origen))
        assertEquals(OperacionDelAviso.TRASPASO, propuestas.operacion)
        assertTrue(propuestas.entrante)
        assertEquals("Sale de Fiducuenta 9586 · entra a Bancolombia Ahorros 8133 como traspaso", propuestas.resumen)

        var n = 0
        val pedido = pedidoDeDosPatas(propuestas, leido, momento = 1L) { "${it}_${++n}" }
        assertEquals(fidu.id, pedido.origenId)
        assertEquals(ahorros.id, pedido.destinoId)
        assertTrue(pedido.avisoDelLadoQueEntra)
        assertEquals(500_000L, pedido.monto)
    }

    @Test
    fun con_origen_ajeno_sigue_siendo_un_ingreso() {
        val ajeno = leido.copy(merchant = "Transferencia recibida", traspasoDesdeId = null)
        val resuelta = resolverCuentaDelBanco(todas, UsoDeCuenta.DESTINO_DE_INGRESO, "85540", textoDelMensaje = texto)
        assertNull(origenPropuestoDelAviso(ajeno, resuelta, todas))
        assertNull(dosPatasPropuestas(ajeno, "Transferencia", ahorros, null, null))
        assertFalse(pideLaCuentaDeOrigen(ajeno, "Transferencia", ahorros))
    }

    @Test
    fun traspaso_sin_origen_o_entre_monedas_no_se_confirma() {
        assertNull(dosPatasPropuestas(leido, TRANSFER_CATEGORY, ahorros, null, null, origenElegido = null))
        assertNull(dosPatasPropuestas(leido, TRANSFER_CATEGORY, ahorros, null, null, origenElegido = dolares))
        assertNull(dosPatasPropuestas(leido, TRANSFER_CATEGORY, ahorros, null, null, origenElegido = ahorros), "no de la misma cuenta")
    }
}
