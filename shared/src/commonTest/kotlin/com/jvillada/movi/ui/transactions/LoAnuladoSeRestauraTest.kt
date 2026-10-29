package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientoAnulado
import com.jvillada.movi.shared.model.RepartoDeUnMovimiento
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.claveDeLaCompra
import com.jvillada.movi.ui.components.AvisoDeDeshacer
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 67 · lo anulado se restaura y lo dividido se deshace
 */
class LoAnuladoSeRestauraTest {

    /** Corre [bloque] hasta el final: lo que se prueba no se suspende nunca. */
    private fun sinEsperar(bloque: suspend () -> Unit) {
        var resultado: Result<Unit>? = null
        bloque.startCoroutine(Continuation(EmptyCoroutineContext) { resultado = it })
        resultado!!.getOrThrow()
    }

    private fun ev(id: String, cuenta: String = "ahorros", monto: Long = 40_000, tipo: TransactionType = TransactionType.EXPENSE, par: String? = null, reparto: String? = null) =
        FinancialEvent(id = id, accountId = cuenta, type = tipo, amount = monto, category = "Mercado", description = "Olimpica", timestamp = 0L, transferId = par, repartoDe = reparto)

    @Test
    fun `Anulados lista un par una vez y dice cuanto, cuando y por que`() {
        val lista = listOf(
            MovimientoAnulado(ev("tr-sale", par = "tr"), motivo = "Duplicado", anuladoEn = 0L),
            MovimientoAnulado(ev("tr-entra", cuenta = "cdt", tipo = TransactionType.INCOME, par = "tr"), anuladoEn = 0L),
            MovimientoAnulado(ev("almuerzo"), motivo = "  ", anuladoEn = 0L),
        )
        assertEquals(listOf("tr-sale", "almuerzo"), anuladosUnoPorPar(lista).map { it.evento.id })
        assertEquals("\$40.000 · Duplicado", lineaDelAnulado(lista.first()))
        assertEquals("\$40.000", lineaDelAnulado(lista.last()), "un motivo en blanco no se dice")
    }

    @Test
    fun `una parte dice de que compra es y cuantas partes tiene`() {
        val reparto = RepartoDeUnMovimiento(
            original = ev("orig", monto = 100_000),
            partes = listOf(ev("p1", monto = 60_000, reparto = "orig"), ev("p2", monto = 40_000, reparto = "orig")),
        )
        assertEquals("Parte de una compra de \$100.000 · 2 partes", textoDeLaParte(reparto))
        val (pregunta, detalle) = queHaceDeshacerLaDivision(reparto)
        assertEquals("¿Volver a un solo movimiento?", pregunta)
        assertTrue("\$100.000" in detalle && "2 partes" in detalle)
        // Las partes de una compra cuentan como una sola visita al comercio.
        assertEquals(claveDeLaCompra(reparto.partes[0]), claveDeLaCompra(reparto.partes[1]))
    }

    @Test
    fun `el aviso de anulado deshace y se cierra`() = sinEsperar {
        var cambios = 0
        var restaurado = false
        val aviso = AvisoDeDeshacer(alCambiar = { cambios++ })
        aviso.mostrar("Anulado") { restaurado = true }
        assertEquals("Anulado", aviso.actual?.texto)
        aviso.deshacer()
        assertTrue(restaurado)
        assertNull(aviso.actual)
        assertEquals(1, cambios, "las pantallas releen")
    }

    @Test
    fun `si deshacer falla, el aviso lo dice y se queda`() = sinEsperar {
        val aviso = AvisoDeDeshacer()
        aviso.mostrar("Anulado") { error("sin red") }
        aviso.deshacer()
        assertTrue(aviso.actual != null)
        assertTrue(aviso.error != null)
    }
}
