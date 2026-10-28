package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.porrevisar.loQueMoviEntendio
import com.jvillada.movi.ui.transactions.avisosPorConfirmar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 46, lo puro: Revisar aviso no le pone el ✓ a lo que Movi no entendió, y un aviso al que solo
 * le falta la categoría queda confirmable con la que el dueño elige en la tarjeta.
 */
class LoQueMoviNoEntendioSePideTest {

    private fun leido(categoria: String, aprendidoDe: String? = null) =
        ParsedSms(54_000.0, "PANAMERICANA LAURELES", TransactionType.EXPENSE, categoria, aprendidoDe = aprendidoDe)

    @Test
    fun `Otros no lleva el visto, dice Elige la categoria`() {
        val otros = categoriaEnReconciliar("Otros", leido("Otros"), listOf("Comida"))
        assertFalse(otros.entendida)
        assertEquals(ELIGE_LA_CATEGORIA, otros.valor)
        assertEquals("Movi no la entendió", otros.pista)
    }

    @Test
    fun `una del catalogo que nunca uso tampoco, y la de su memoria si`() {
        assertFalse(categoriaEnReconciliar("Restaurantes", leido("Restaurantes"), listOf("Comida")).entendida)
        assertTrue(categoriaEnReconciliar("Comida", leido("Comida"), listOf("Comida")).entendida)
        assertTrue(categoriaEnReconciliar("Comida", leido("Comida", aprendidoDe = "Así lo anotaste 4 veces"), emptyList()).entendida)
    }

    @Test
    fun `la que el duenno elige a mano en la pantalla si lleva el visto`() {
        val elegida = categoriaEnReconciliar("Papelería", leido("Otros"), listOf("Comida"))
        assertTrue(elegida.entendida)
        assertEquals("Papelería", elegida.valor)
        assertNull(elegida.pista)
    }

    @Test
    fun `la cuenta por defecto y el comercio sin leer tampoco llevan el visto`() {
        assertTrue(cuentaEntendidaEnReconciliar(true, OrigenDeLaCuentaDelBanco.POR_EL_NUMERO))
        assertTrue(cuentaEntendidaEnReconciliar(true, OrigenDeLaCuentaDelBanco.POR_EL_BANCO))
        assertFalse(cuentaEntendidaEnReconciliar(true, OrigenDeLaCuentaDelBanco.POR_DEFECTO))
        assertFalse(cuentaEntendidaEnReconciliar(false, OrigenDeLaCuentaDelBanco.NINGUNA))
        assertTrue(comercioEntendidoEnReconciliar("PANAMERICANA", null))
        assertFalse(comercioEntendidoEnReconciliar("Movimiento", null))
        assertTrue(comercioEntendidoEnReconciliar("Movimiento", "Panadería"))
    }

    private val sms = SmsMessage(
        id = "pana", time = "2026-10-28 14:05", bank = "85540", state = SMS_STATE_PENDING, det = "",
        text = "Bancolombia: Compraste \$54.000,00 en PANAMERICANA LAURELES con tu T.Deb *4057.",
    )
    private val cuenta = Account("c", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 0L, "COP")
    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(PropuestaDelAviso("pana", leido = leido("Otros"))),
        categoriasDelDueno = listOf("Comida", "Papelería"),
    )

    @Test
    fun `solo le falta la categoria, y con la elegida queda confirmable y entra al lote`() {
        val sinElegir = loQueMoviEntendio(sms, listOf(sms), propuestas, listOf(cuenta), ahora = 0L)
        assertNull(sinElegir.anotacion)
        assertTrue(sinElegir.faltaSoloLaCategoria)

        val conLaElegida = loQueMoviEntendio(sms, listOf(sms), propuestas, listOf(cuenta), ahora = 0L, categoriaElegida = "Papelería")
        assertEquals("Papelería", assertNotNull(conLaElegida.anotacion).categoria)
        assertEquals("Papelería · Bancolombia Ahorros 4057", conLaElegida.linea)

        val lote = avisosPorConfirmar(listOf(sms), propuestas, listOf(cuenta), ahora = 0L, categoriasElegidas = mapOf("pana" to "Papelería"))
        assertEquals(listOf("Papelería"), lote.confirmables.map { it.categoria })
    }

    @Test
    fun `si falta algo mas que la categoria no se pide en la tarjeta`() {
        // Sin una cuenta que el aviso nombre, falta también la cuenta: va a Revisar aviso.
        val otra = Account("x", "Davivienda", AccountType.SAVINGS, 0L, "COP")
        assertFalse(loQueMoviEntendio(sms, listOf(sms), propuestas, listOf(otra), ahora = 0L).faltaSoloLaCategoria)
    }
}
