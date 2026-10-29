package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientoAnulado
import com.jvillada.movi.shared.model.NO_SE_RESTAURA_UN_AJUSTE
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
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
        // Vuelta 67 (revisión): con la fecha del movimiento y la de la anulación.
        val conFechas = lista.first().copy(evento = lista.first().evento.copy(timestamp = alMediodia("2026-10-12")), anuladoEn = alMediodia("2026-10-29"))
        assertEquals("\$40.000 · 12 oct · anulado el 29 oct · Duplicado", lineaDelAnulado(conFechas))
        assertEquals("\$40.000", lineaDelAnulado(lista.last()), "un motivo en blanco no se dice")
        // Lo que no se restaura dice por qué, en el renglón de abajo.
        val ajuste = lista.last().copy(porQueNo = NO_SE_RESTAURA_UN_AJUSTE)
        assertEquals("\$40.000\n$NO_SE_RESTAURA_UN_AJUSTE", lineaDelAnulado(ajuste))
    }

    private fun alMediodia(dia: String): Long =
        LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L

    @Test
    fun `al final de Actividad, cuantos anulados hay en el periodo`() {
        val corte25 = PeriodSettings(cutoffDay = 25)
        val noviembre = PeriodoFinanciero(2026, 11)
        val lista = listOf(
            MovimientoAnulado(ev("en-el-periodo").copy(timestamp = alMediodia("2026-10-28"))),
            MovimientoAnulado(ev("tr-sale", par = "tr").copy(timestamp = alMediodia("2026-11-02"))),
            MovimientoAnulado(ev("tr-entra", par = "tr").copy(timestamp = alMediodia("2026-11-02"))),
            MovimientoAnulado(ev("de-antes").copy(timestamp = alMediodia("2026-10-10"))),
        )
        val delPeriodo = anuladosDelPeriodo(lista, noviembre, corte25)
        assertEquals(listOf("en-el-periodo", "tr-sale"), delPeriodo.map { it.evento.id }, "un par una vez")
        assertEquals("2 anulados en noviembre", textoDeLosAnuladosDelPeriodo(delPeriodo.size, noviembre))
        assertEquals("1 anulado en noviembre", textoDeLosAnuladosDelPeriodo(1, noviembre))
    }

    @Test
    fun `al anular un traspaso, la hoja dice que la otra mitad tambien se anula`() {
        assertEquals("También se anula la entrada en CDT.", com.jvillada.movi.ui.accounts.loQueTambienSeAnula(ev("s"), "CDT"))
        assertEquals("También se anula la salida de Ahorros.", com.jvillada.movi.ui.accounts.loQueTambienSeAnula(ev("e", tipo = TransactionType.INCOME), "Ahorros"))
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
