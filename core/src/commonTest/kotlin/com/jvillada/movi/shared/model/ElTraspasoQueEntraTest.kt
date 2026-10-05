package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El traspaso entre cuentas propias, avisado del lado que entra** (hueco de #439). Textos
 * sintéticos con la forma de los avisos: «Recibiste $X de tu cuenta *9586 en tu cuenta *8133».
 */
class ElTraspasoQueEntraTest {

    private val ahorros = Account("a", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0)
    private val fiducuenta = Account("f", "Fiducuenta 9586", AccountType.INVESTMENT, 0)
    private val libre = Account("l", "Libre inversión 9586", AccountType.LOAN, 0)
    private val cuentas = listOf(ahorros, fiducuenta)

    private val propio = "Bancolombia: Recibiste \$500.000 de tu cuenta *9586 en tu cuenta *8133 el 04/10/2026 a las 10:15."
    private val ajeno = "Bancolombia: Recibiste \$80.000 de la cuenta *4321 en tu cuenta *8133 el 04/10/2026 a las 10:20."

    @Test
    fun el_ingreso_que_nombra_una_cuenta_suya_vino_de_ella() {
        assertEquals("9586", numeroDelOrigenDelIngreso(propio))
        assertEquals(fiducuenta, cuentaPropiaDeLaQueVinoElIngreso(TransactionType.INCOME, propio, cuentas))
    }

    @Test
    fun un_origen_ajeno_no_es_de_ninguna_cuenta_suya() {
        assertEquals("4321", numeroDelOrigenDelIngreso(ajeno))
        assertNull(cuentaPropiaDeLaQueVinoElIngreso(TransactionType.INCOME, ajeno, cuentas))
    }

    @Test
    fun un_gasto_no_es_un_traspaso_que_entra() {
        val retiro = "Bancolombia: Retiraste \$4.200.000 de tu cuenta *9586 Fiducuenta hacia la cuenta *02955068133"
        assertNull(cuentaPropiaDeLaQueVinoElIngreso(TransactionType.EXPENSE, retiro, cuentas))
    }

    @Test
    fun una_deuda_no_es_el_origen_y_un_empate_no_elige() {
        // La Libre inversión lleva el mismo número, pero es una deuda: no cuenta, y queda la Fiducuenta.
        assertEquals(fiducuenta, cuentaPropiaDeLaQueVinoElIngreso(TransactionType.INCOME, propio, cuentas + libre))
        val otraFidu = Account("f2", "Fiducuenta vieja 9586", AccountType.INVESTMENT, 0)
        assertNull(cuentaPropiaDeLaQueVinoElIngreso(TransactionType.INCOME, propio, cuentas + otraFidu))
    }

    @Test
    fun los_avisos_reales_de_recibido_no_nombran_una_cuenta_de_origen() {
        // Las formas reales: el origen es una persona o «tu llave», no un número.
        assertNull(numeroDelOrigenDelIngreso("Bancolombia: DUENO, recibiste una transferencia de PERSONA DE PRUEBA por \$95,000.00 en tu cuenta *8133 conectada a la llave @LLAVE el 23/08/26 a las 16:49."))
        assertNull(numeroDelOrigenDelIngreso("Recibiste 300.000,00 en tu cuenta: Te llegó dinero de PERSONA DE PRUEBA con tu llave."))
    }

    @Test
    fun sin_el_origen_queda_la_cuenta_que_recibe() {
        val sin = sinElOrigenDelIngreso(propio)
        assertFalse("9586" in sin)
        assertTrue("*8133" in sin)
    }

    @Test
    fun el_aviso_del_lado_que_entra_queda_en_la_pata_que_entra() {
        val base = DosPatasDelAviso(
            OperacionDelAviso.TRASPASO, "f", "a", 500_000L, 0L, "tr", "ev-sale", "ev-entra",
        )
        assertEquals("ev-sale", base.pataDelAviso)
        assertEquals("ev-entra", base.copy(avisoDelLadoQueEntra = true).pataDelAviso)
        // Solo en un traspaso: en un pago de tarjeta la marca no cambia nada.
        assertEquals("ev-sale", base.copy(operacion = OperacionDelAviso.PAGO_DE_TARJETA, avisoDelLadoQueEntra = true).pataDelAviso)
    }
}
