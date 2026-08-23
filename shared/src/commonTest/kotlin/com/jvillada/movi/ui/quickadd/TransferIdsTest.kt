package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.repository.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * La mitad cliente de la idempotencia del traspaso.
 *
 * El escenario que esto blinda: el server commitea, la respuesta se pierde (timeout, cambio de
 * red, la app al fondo), el dueño ve «revisa tu conexión» y vuelve a tocar Guardar. Si el segundo
 * intento lleva ids nuevos, el server no tiene forma de saber que es el mismo traspaso y crea uno
 * segundo entero: origen −2×monto, destino +2×monto. El server ya está listo para rebotarlo con
 * 409 (ver `TransferRoutesTest`); lo que faltaba era que el cliente reintentara con los MISMOS
 * ids.
 */
class TransferIdsTest {

    private val ahorros = Account("acc_ahorros", "Ahorros", AccountType.SAVINGS, balance = 1_000_000L)
    private val cdt = Account("acc_cdt", "CDT", AccountType.INVESTMENT, balance = 0L)

    @Test
    fun `unos ids nuevos son tres, distintos entre si y con su prefijo`() {
        val ids = TransferDraftIds.new()

        assertTrue(ids.transferId.startsWith("tr_"))
        assertTrue(ids.fromEventId.startsWith("ev_"))
        assertTrue(ids.toEventId.startsWith("ev_"))
        assertNotEquals(ids.fromEventId, ids.toEventId, "las dos patas no pueden compartir id")
    }

    @Test
    fun `dos borradores distintos no comparten ningun id`() {
        val a = TransferDraftIds.new()
        val b = TransferDraftIds.new()

        assertNotEquals(a.transferId, b.transferId)
        assertNotEquals(a.fromEventId, b.fromEventId)
        assertNotEquals(a.toEventId, b.toEventId)
    }

    /**
     * El corazón de C1: reintentar con el mismo borrador produce **exactamente el mismo pedido**.
     * Así el server reconoce el reintento por la PK de las patas y responde 409 en vez de crear
     * un traspaso duplicado.
     */
    @Test
    fun `reintentar con el mismo borrador manda el mismo pedido, byte por byte`() {
        val ids = TransferDraftIds.new()

        val primero = transferRequestFor(ids, ahorros, cdt, 250_000L, 1_700_000_000_000L, "alquiler")
        val segundo = transferRequestFor(ids, ahorros, cdt, 250_000L, 1_700_000_000_000L, "alquiler")

        assertEquals(primero, segundo)
    }

    @Test
    fun `con un borrador nuevo el pedido ya es otro traspaso`() {
        val primero = transferRequestFor(TransferDraftIds.new(), ahorros, cdt, 250_000L, 1L, "")
        val segundo = transferRequestFor(TransferDraftIds.new(), ahorros, cdt, 250_000L, 1L, "")

        assertNotEquals(primero.transferId, segundo.transferId)
    }

    @Test
    fun `una nota en blanco viaja como nula, no como cadena vacia`() {
        assertEquals(null, transferRequestFor(TransferDraftIds.new(), ahorros, cdt, 1L, 1L, "   ").note)
        assertEquals("alquiler", transferRequestFor(TransferDraftIds.new(), ahorros, cdt, 1L, 1L, " alquiler ").note)
    }

    // ── El 409 es un éxito, no un error ───────────────────────────────────────

    /**
     * «Ese traspaso ya está registrado» significa que el traspaso **está**: mostrarlo como error
     * es mentirle al dueño y empujarlo a tocar Guardar una tercera vez.
     */
    @Test
    fun `un 409 del server cuenta como traspaso ya guardado`() {
        assertTrue(isAlreadyRegistered(ApiException(409, "Ese traspaso ya está registrado")))
    }

    @Test
    fun `cualquier otro fallo sigue siendo un fallo`() {
        assertFalse(isAlreadyRegistered(ApiException(422, "El monto tiene que ser mayor que cero")))
        assertFalse(isAlreadyRegistered(ApiException(404, "Cuenta no encontrada")))
        assertFalse(isAlreadyRegistered(ApiException(500, null)))
        assertFalse(isAlreadyRegistered(RuntimeException("sin red")))
    }
}
