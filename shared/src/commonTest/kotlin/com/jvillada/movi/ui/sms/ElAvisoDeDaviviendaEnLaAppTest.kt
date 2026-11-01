package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Vuelta 99 · un SMS de Davivienda en la app**: el banco se nombra por cómo firma el texto (su
 * código corto 871… Movi no lo conoce) y la cuenta sale de los dígitos «*0136».
 */
class ElAvisoDeDaviviendaEnLaAppTest {

    private val descuento =
        "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA."
    private val abono =
        "DAVIVIENDA Abono Transferencia ACH, \$419,745,Cta de Ahorros *0136,Hora 17:18,Lugar 0000000000000000000000000."

    private val davivienda = Account("d1", "Davivienda Ahorros 0136", AccountType.SAVINGS, 0)
    private val otra = Account("b1", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0)

    @Test
    fun `el banco se dice Davivienda y no SMS`() {
        assertEquals("Davivienda", bancoDelAviso("87100", descuento))
        assertEquals("SMS", bancoDelAviso("87100", "hola"), "sin texto que lo diga, el código no se adivina")
        assertEquals("Bancolombia", bancoDelAviso("85540", descuento), "un código conocido manda")
    }

    @Test
    fun `la cuenta sale de los digitos del aviso`() {
        val gasto = resolverCuentaDelBanco(listOf(otra, davivienda), UsoDeCuenta.ORIGEN_DE_GASTO, "87100", textoDelMensaje = descuento)
        assertEquals(davivienda, gasto.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, gasto.origen)
        val ingreso = resolverCuentaDelBanco(listOf(otra, davivienda), UsoDeCuenta.DESTINO_DE_INGRESO, "87100", textoDelMensaje = abono)
        assertEquals(davivienda, ingreso.cuenta)
    }
}
