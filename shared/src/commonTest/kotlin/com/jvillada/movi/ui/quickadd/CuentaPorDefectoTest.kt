package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ola 11 — la regla de qué cuenta arranca elegida en «Agregar».
 *
 * El porqué de cada prioridad está en `CuentaPorDefecto.kt`. Acá quedan clavados los casos que
 * motivaron la rama: la última usada manda, pero **nunca** por encima del contexto, y nunca si
 * esa cuenta ya no está entre las que se pueden elegir (borrada, de otro tipo, de otro usuario).
 */
class CuentaPorDefectoTest {

    private fun cuenta(id: String, name: String = id, type: AccountType = AccountType.SAVINGS) =
        Account(id = id, name = name, type = type, balance = 0L)

    private val bancolombia = cuenta("acc_b", "Bancolombia")
    private val nequi = cuenta("acc_n", "Nequi")
    private val efectivo = cuenta("acc_e", "Efectivo", AccountType.CASH)
    private val tres = listOf(bancolombia, nequi, efectivo)

    @Test
    fun `sin memoria y sin contexto cae en la primera de la lista`() {
        val elegida = resolverCuenta(tres)
        assertEquals("acc_b", elegida.id)
        assertEquals(OrigenCuenta.PRIMERA, elegida.origen)
    }

    @Test
    fun `la ultima usada le gana a la primera de la lista`() {
        val elegida = resolverCuenta(tres, ultima = "acc_n")
        assertEquals("acc_n", elegida.id)
        assertEquals(OrigenCuenta.ULTIMA, elegida.origen)
    }

    @Test
    fun `el contexto le gana a la ultima usada`() {
        // Abrir «Agregar» desde el detalle de Efectivo, o desde un recurrente que tiene esa
        // cuenta guardada, no puede terminar anotando en Nequi porque ayer se usó Nequi.
        val elegida = resolverCuenta(tres, contexto = "acc_e", ultima = "acc_n")
        assertEquals("acc_e", elegida.id)
        assertEquals(OrigenCuenta.CONTEXTO, elegida.origen)
    }

    @Test
    fun `un contexto que ya no existe cae en la ultima usada`() {
        val elegida = resolverCuenta(tres, contexto = "acc_borrada", ultima = "acc_n")
        assertEquals("acc_n", elegida.id)
        assertEquals(OrigenCuenta.ULTIMA, elegida.origen)
    }

    @Test
    fun `si la ultima usada fue borrada se cae a la primera, no a nada`() {
        val elegida = resolverCuenta(tres, ultima = "acc_borrada")
        assertEquals("acc_b", elegida.id)
        assertEquals(OrigenCuenta.PRIMERA, elegida.origen)
    }

    @Test
    fun `una ultima usada de otro tipo no llega a elegirse`() {
        // El traspaso solo pasa las cuentas traspasables (transferableAccounts saca las de
        // deuda), así que una tarjeta recordada como «última» simplemente no está en la lista.
        val conTarjeta = tres + cuenta("acc_visa", "Visa", AccountType.CREDIT_CARD)
        val elegibles = transferableAccounts(conTarjeta)
        val elegida = resolverCuenta(elegibles, ultima = "acc_visa")
        assertEquals("acc_b", elegida.id)
        assertEquals(OrigenCuenta.PRIMERA, elegida.origen)
    }

    @Test
    fun `sin cuentas no hay nada que elegir`() {
        val elegida = resolverCuenta(emptyList(), contexto = "acc_b", ultima = "acc_n")
        assertNull(elegida.id)
        assertEquals(OrigenCuenta.NINGUNA, elegida.origen)
    }

    @Test
    fun `con una sola cuenta se elige esa, con memoria o sin ella`() {
        val sola = listOf(bancolombia)
        assertEquals("acc_b", resolverCuenta(sola).id)
        assertEquals("acc_b", resolverCuenta(sola, ultima = "acc_n").id)
        assertEquals("acc_b", resolverCuenta(sola, contexto = "acc_n").id)
    }

    @Test
    fun `el destino de un traspaso no puede ser el origen`() {
        // Recuerdo del par Bancolombia→Nequi, pero el origen que quedó es Nequi: el destino no
        // puede volver a ser Nequi.
        val destino = resolverCuenta(tres, ultima = "acc_n", excluir = "acc_n")
        assertEquals("acc_b", destino.id)
        assertEquals(OrigenCuenta.PRIMERA, destino.origen)
    }

    @Test
    fun `el par recordado de un traspaso se reconstruye entero`() {
        val origen = resolverCuenta(tres, ultima = "acc_e")
        val destino = resolverCuenta(tres, ultima = "acc_n", excluir = origen.id)
        assertEquals("acc_e", origen.id)
        assertEquals("acc_n", destino.id)
        assertEquals(OrigenCuenta.ULTIMA, destino.origen)
    }

    @Test
    fun `con una sola cuenta el aviso no dice nada`() {
        assertNull(avisoDeCuenta(OrigenCuenta.ULTIMA, cuentasDisponibles = 1))
        assertNull(avisoDeCuenta(OrigenCuenta.PRIMERA, cuentasDisponibles = 1))
        assertNull(avisoDeCuenta(OrigenCuenta.PRIMERA, cuentasDisponibles = 0))
    }

    @Test
    fun `el aviso solo aparece cuando la cuenta la puso la app`() {
        assertEquals("La última que usaste", avisoDeCuenta(OrigenCuenta.ULTIMA, 3))
        assertEquals("Elegida por la app", avisoDeCuenta(OrigenCuenta.PRIMERA, 3))
        assertNull(avisoDeCuenta(OrigenCuenta.ELEGIDA, 3))
        assertNull(avisoDeCuenta(OrigenCuenta.CONTEXTO, 3))
        assertNull(avisoDeCuenta(OrigenCuenta.NINGUNA, 3))
    }
}
