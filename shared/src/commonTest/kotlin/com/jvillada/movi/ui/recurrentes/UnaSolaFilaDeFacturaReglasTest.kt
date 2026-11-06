package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.LaCuotaQueCambia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import com.jvillada.movi.ui.credits.ANOTAR_ESTE_ABONO
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 136 · una sola fila de factura: las reglas puras
 *
 * El renglón de estado (un texto y un tono, siempre en el mismo lugar), la fecha de la derecha, la cifra con
 * su «unos», cuántos renglones reserva la fila y el único amarillo de la hoja. Ninguna existía antes de la
 * vuelta 136 (la prueba no compila sin el cambio).
 */
class UnaSolaFilaDeFacturaReglasTest {

    private val porVencer = PagoDelPeriodo(
        ruleId = "rr_celular", nombre = "Celular", monto = 53_077, pagado = false, diasParaVencer = 16,
        vence = "2026-11-22", periodoDelSello = null,
    )
    private val vencida = porVencer.copy(diasParaVencer = -3, vence = "2026-11-03", periodoDelSello = "2026-11")
    private val pagada = PagoDelPeriodo(
        ruleId = "rr_colegio", nombre = "Colegio Hija", monto = 4_000_000, pagado = true, diasParaVencer = -12,
        vence = "2026-10-25", periodoDelSello = "2026-10", eventId = "ev_colegio", pagadoEl = "2026-10-25",
        montoPagado = 4_000_000,
    )
    private val tanqueo = FinancialEvent(
        id = "ev_t", accountId = "acc", type = TransactionType.EXPENSE, amount = 4_000_000L,
        category = "Cuota de crédito", description = "Pago", timestamp = 1_793_000_000_000L,
    )

    @Test
    fun `el renglon de estado dice una cosa por estado, con su tono`() {
        assertEquals(EstadoDeLaFactura("Vence el 22 nov", TonoDeSubtitulo.Normal), estadoDeLaFactura(porVencer, null))
        assertEquals(EstadoDeLaFactura("Venció hace 3 días", TonoDeSubtitulo.Aviso), estadoDeLaFactura(vencida, null))
        assertEquals(EstadoDeLaFactura("¿Es el de \$4.000.000?", TonoDeSubtitulo.Enlace), estadoDeLaFactura(vencida, tanqueo))
        assertEquals(EstadoDeLaFactura("Pagado · 25 oct", TonoDeSubtitulo.Exito, conCheck = true), estadoDeLaFactura(pagada, null))
        assertEquals(GUARDANDO_LA_FACTURA, estadoDeLaFactura(porVencer, null, enVuelo = true).texto)
        // Se cobra sola: la pastilla de siempre.
        assertEquals("Se cobra sola el 22", estadoDeLaFactura(porVencer.copy(seCobraSola = true), null).texto)
        assertEquals(PILDORA_SE_COBRO, estadoDeLaFactura(vencida.copy(seCobraSola = true), null).texto)
        // Un abono planeado no vence.
        assertEquals("Abono planeado · el día 22", estadoDeLaFactura(porVencer.copy(haciaDeuda = "acc_carro"), null).texto)
        // Vuelta 86: una tarjeta sin mínimo no suma, y la fila lo dice.
        val tarjeta = porVencer.copy(ruleId = "card_nu", montoEsSaldo = true)
        assertEquals(
            EstadoDeLaFactura(NO_SUMA_FALTA_EL_MINIMO, TonoDeSubtitulo.Aviso),
            estadoDeLaFactura(tarjeta, null, noSuma = porQueNoSuma(tarjeta)),
        )
        // Un ingreso recibido.
        assertEquals("Recibido · 25 oct", estadoDeLaFactura(pagada.copy(esIngreso = true), null).texto)
    }

    @Test
    fun `una tarjeta pagada por debajo del minimo lo dice en el estado, en aviso`() {
        val tarjeta = porVencer.copy(
            ruleId = "card_nu", nombre = "Pago tarjeta Nu", monto = 1_200_000, montoEsSaldo = true, pagoMinimo = 180_000,
            pagado = true, montoPagado = 100_000, pagadoEnElPeriodo = 100_000, eventId = "ev_abono",
        )
        val estado = estadoDeLaFactura(tarjeta, null)
        assertEquals(TonoDeSubtitulo.Aviso, estado.tono)
        assertEquals(lineaDelMinimo(tarjeta), estado.texto)
    }

    /** Revisión de la 136 (C1): sellada por un abono menor que el extracto, la fila no se ve pagada. */
    @Test
    fun `una tarjeta abonada con el extracto abierto dice lo que falta, y su cifra es lo que salio`() {
        val extracto = com.jvillada.movi.shared.model.ExtractoDeLaTarjeta(
            corte = "2026-10-20", fechaDePago = "2026-11-05", facturado = 1_292_700L, pagadoDesdeElCorte = 100_000L, minimo = 180_000L,
        )
        val abonada = PagoDelPeriodo(
            ruleId = "card_nu", nombre = "Pago tarjeta Nu", monto = 1_583_000L, pagado = true, diasParaVencer = 7,
            montoEsSaldo = true, vence = "2026-11-05", montoPagado = 100_000L, derivado = true,
        )
        val estado = estadoDeLaFactura(abonada, null, extracto = extracto)
        assertEquals(TonoDeSubtitulo.Aviso, estado.tono)
        assertFalse(estado.conCheck)
        assertTrue(estado.texto.startsWith("Abonaste \$100.000 · faltan \$1.292.700"), estado.texto)
        assertEquals(CifraDeLaFactura("\$100.000"), cifraDeLaFactura(abonada, extracto))
    }

    /** Revisión de la 136 (I2): un nombre que no cabe se corta por el principio, y lo que distingue queda. */
    @Test
    fun `un nombre que no cabe se corta por el principio`() {
        val cabeEn = { n: Int -> { t: String -> t.length <= n } }
        assertEquals("Celular", com.jvillada.movi.ui.components.ds.recortarPorElPrincipio("Celular", cabeEn(20)))
        assertEquals("…Master Black 3684", com.jvillada.movi.ui.components.ds.recortarPorElPrincipio("Pago tarjeta Master Black 3684", cabeEn(18)))
        assertEquals("…Papá (Davibank)", com.jvillada.movi.ui.components.ds.recortarPorElPrincipio("Cuota Hipoteca Papá (Davibank)", cabeEn(16)))
    }

    @Test
    fun `la cifra de una cuota que cambia dice unos, y una tarjeta su saldo`() {
        val cuota = porVencer.copy(ruleId = "credit_acc", cuotaQueCambia = LaCuotaQueCambia())
        assertEquals(CifraDeLaFactura("\$53.077", estimada = true), cifraDeLaFactura(cuota, null))
        assertEquals(CifraDeLaFactura("\$53.077", despues = "saldo"), cifraDeLaFactura(porVencer.copy(montoEsSaldo = true), null))
        assertEquals(CifraDeLaFactura("\$4.000.000"), cifraDeLaFactura(pagada, null))
    }

    @Test
    fun `la fila reserva uno o dos renglones, la misma regla para todas`() {
        assertEquals(1, renglonesDeLaFactura(1.12f))
        assertEquals(2, renglonesDeLaFactura(1.456f))
    }

    @Test
    fun `la hoja tiene un solo amarillo, segun el estado`() {
        fun lo(pago: PagoDelPeriodo, propuesta: FinancialEvent? = null, conPropuesta: Boolean = false) = LoDeLaFactura(
            pago = pago, propuesta = propuesta, conPropuesta = conPropuesta, parecido = null, enVuelo = false,
            estimacion = null, extracto = null, noSuma = null, evidencia = null,
        )
        assertEquals(PrincipalDeLaHoja.ANOTAR, principalDeLaHoja(lo(porVencer)))
        assertEquals(PrincipalDeLaHoja.EL_CANDIDATO, principalDeLaHoja(lo(vencida, tanqueo, conPropuesta = true)))
        // La lejana: «Anotar pago» sigue siendo lo principal.
        assertEquals(PrincipalDeLaHoja.ANOTAR, principalDeLaHoja(lo(vencida, tanqueo, conPropuesta = false)))
        assertEquals(PrincipalDeLaHoja.NINGUNA, principalDeLaHoja(lo(pagada)))
        assertEquals(ANOTAR_ESTE_ABONO, etiquetaDeAnotarLaFactura(porVencer.copy(haciaDeuda = "acc_carro")))
        assertEquals(ETIQUETA_ANOTAR, etiquetaDeAnotarLaFactura(porVencer))
    }

    @Test
    fun `la hoja de una pagada ofrece ver el movimiento y lo de su menu de antes`() {
        assertEquals(
            listOf(AccionDeLaFilaPagada.VER_MOVIMIENTO, AccionDeLaFilaPagada.QUITAR_LA_MARCA),
            accionesDeLaHojaPagada(pagada),
        )
        val emparejada = pagada.copy(automatica = true, derivado = true)
        assertEquals(listOf(AccionDeLaFilaPagada.VER_MOVIMIENTO, AccionDeLaFilaPagada.NO_FUE_ESTE), accionesDeLaHojaPagada(emparejada))
        val selloViejo = pagada.copy(eventId = null)
        assertEquals(listOf(AccionDeLaFilaPagada.QUITAR_LA_MARCA), accionesDeLaHojaPagada(selloViejo))
        assertTrue(accionesDeLaHojaPagada(porVencer).isEmpty())
    }

    @Test
    fun `lo de la factura separa la propuesta cercana de la lejana`() {
        val cerca = vencida.copy(candidatos = listOf(tanqueo.copy(amount = 53_000)))
        val lejos = vencida.copy(candidatos = listOf(tanqueo))
        fun lo(p: PagoDelPeriodo) = loDeLaFactura(p, emptySet(), false, emptyMap(), emptyMap(), emptyMap(), enElTotal = true)
        assertTrue(lo(cerca).conPropuesta)
        assertFalse(lo(lejos).conPropuesta)
        // Revisión de la 136 (I1): la cercana se pregunta en la fila; la lejana no tapa el vencimiento.
        assertEquals("¿Es el de \$53.000?", lo(cerca).estado.texto)
        assertEquals(EstadoDeLaFactura("Venció hace 3 días", TonoDeSubtitulo.Aviso), lo(lejos).estado)
        assertEquals(tanqueo, lo(lejos).candidato)
        assertNull(lo(porVencer).candidato)
        // «No fue este» en esta sesión: la fila deja de preguntar.
        assertNull(loDeLaFactura(lejos, setOf(claveDescartada(lejos.ruleId, tanqueo.id)), false, emptyMap(), emptyMap(), emptyMap(), true).candidato)
    }
}
