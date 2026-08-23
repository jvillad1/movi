package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Lógica pura de la hoja de Traspaso: la fecha que escribe el dueño y el mensaje que explica qué
 * falta. Las reglas del traspaso en sí (origen ≠ destino, monto, moneda, nada de deudas) viven en
 * `validateTransfer` (:core) y las cubre `TransferTest` — acá solo se verifica que la hoja las use
 * y no invente su propia versión.
 */
class TransferFormTest {

    private val ahorros = Account("acc_ahorros", "Ahorros", AccountType.SAVINGS, balance = 1_000_000L)
    private val cdt = Account("acc_cdt", "CDT", AccountType.INVESTMENT, balance = 0L)

    // ── Fecha ─────────────────────────────────────────────────────────────────

    @Test
    fun `una fecha AAAA-MM-DD se convierte a un instante de ese mismo dia en Bogota`() {
        val millis = assertNotNull(transferTimestampFor("2026-08-23"))

        assertEquals(
            "2026-08-23",
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(AppTimeZone.zone).date.toString(),
        )
    }

    @Test
    fun `la barra tambien vale como separador`() {
        assertEquals(transferTimestampFor("2026-08-23"), transferTimestampFor("2026/08/23"))
    }

    @Test
    fun `una fecha incompleta o imposible no pasa`() {
        assertNull(transferTimestampFor(""))
        assertNull(transferTimestampFor("2026-08"))
        assertNull(transferTimestampFor("2026-13-01"))
        assertNull(transferTimestampFor("2026-02-30"))
        assertNull(transferTimestampFor("mañana"))
    }

    @Test
    fun `un año fuera de rango no pasa`() {
        assertNull(transferTimestampFor("0026-08-23"))
        assertNull(transferTimestampFor("9026-08-23"))
    }

    // ── Qué falta ─────────────────────────────────────────────────────────────

    @Test
    fun `con todo completo no falta nada`() {
        assertNull(transferMissingMessage(ahorros, cdt, 250_000L, "2026-08-23"))
    }

    @Test
    fun `el motivo del traspaso invalido es el mismo texto que da el server`() {
        assertEquals(
            "El origen y el destino tienen que ser cuentas distintas",
            transferMissingMessage(ahorros, ahorros, 250_000L, "2026-08-23"),
        )
        assertEquals(
            "El monto tiene que ser mayor que cero",
            transferMissingMessage(ahorros, cdt, 0L, "2026-08-23"),
        )
    }

    @Test
    fun `una fecha invalida se reporta despues de las reglas del traspaso`() {
        assertEquals(
            "La fecha tiene que ser AAAA-MM-DD",
            transferMissingMessage(ahorros, cdt, 250_000L, "23 de agosto"),
        )
        // Si además el traspaso es inválido, manda el motivo del traspaso: es el problema de
        // fondo, y arreglar la fecha no lo destraba.
        assertEquals(
            "El origen y el destino tienen que ser cuentas distintas",
            transferMissingMessage(ahorros, ahorros, 250_000L, "23 de agosto"),
        )
    }
}
