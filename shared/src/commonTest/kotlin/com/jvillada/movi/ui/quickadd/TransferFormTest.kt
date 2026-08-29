package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.validateTransfer
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lógica pura de la hoja de Traspaso.
 *
 * **Ola 13 — qué desapareció de acá y por qué.** Este archivo probaba `transferTimestampFor` (el
 * parseo de «AAAA-MM-DD» que escribía el dueño) y `transferMissingMessage` (que agregaba «La
 * fecha tiene que ser AAAA-MM-DD» a las reglas del traspaso). Las dos funciones se fueron con el
 * campo de texto: con el selector de fecha no hay forma de producir una fecha inválida, así que
 * esa rama era inalcanzable y su mensaje —un reclamo de formato sobre un formato correcto— era
 * peor que no tener mensaje. Probar código muerto no es cobertura, es lastre.
 *
 * Lo que queda: que la hoja use [validateTransfer] (:core) y no invente su propia versión de las
 * reglas del traspaso, y que la conversión de fecha a instante siga cayendo en el día correcto de
 * Bogotá — que ahora la hace `epochAlMediodia` y la cubre `FechaMovimientoTest`, así que acá solo
 * se verifica el helper que sigue vivo.
 */
class TransferFormTest {

    private val ahorros = Account("acc_ahorros", "Ahorros", AccountType.SAVINGS, balance = 1_000_000L)
    private val cdt = Account("acc_cdt", "CDT", AccountType.INVESTMENT, balance = 0L)
    private val tarjeta = Account("acc_tc", "Visa", AccountType.CREDIT_CARD, balance = 500_000L)
    private val libranza = Account("acc_loan", "Libranza", AccountType.LOAN, balance = 257_000_000L)
    private val todas = listOf(ahorros, cdt, tarjeta, libranza)

    // ── Fecha ─────────────────────────────────────────────────────────────────

    /**
     * `todayIsoInAppZone` sobrevivió al campo de texto porque Movimientos lo usa para decidir qué
     * día es «Hoy» en sus encabezados. Tiene que fechar en Bogotá, no en la zona del dispositivo:
     * si no, a las 9 pm del 31 el encabezado del día diría el mes siguiente.
     */
    @Test
    fun `hoy se calcula en la zona de la app y no en la del sistema`() {
        val hoy = LocalDate.parse(todayIsoInAppZone())
        val esperado = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(AppTimeZone.zone).date
        assertEquals(esperado, hoy)
    }

    @Test
    fun `un instante de las 11 de la noche en Bogota sigue siendo ese dia`() {
        // 2026-08-24T04:30Z = 2026-08-23 23:30 en Bogotá.
        val fecha = Instant.parse("2026-08-24T04:30:00Z")
            .toLocalDateTime(AppTimeZone.zone).date
        assertEquals(LocalDate(2026, 8, 23), fecha)
    }

    // ── Qué falta ─────────────────────────────────────────────────────────────

    @Test
    fun `con todo completo no falta nada`() {
        assertNull(validateTransfer(ahorros, cdt, 250_000L))
    }

    /**
     * El texto sale de `:core` y es el mismo que el server devuelve en su 422 — así la hoja y el
     * rechazo del server nunca dicen cosas distintas del mismo problema.
     */
    @Test
    fun `el motivo del traspaso invalido es el mismo texto que da el server`() {
        assertEquals(
            "El origen y el destino tienen que ser cuentas distintas",
            validateTransfer(ahorros, ahorros, 250_000L),
        )
        assertEquals(
            "El monto tiene que ser mayor que cero",
            validateTransfer(ahorros, cdt, 0L),
        )
    }

    // ── Ola 14 · qué se ofrece y qué se preselecciona ─────────────────────────

    @Test
    fun `el selector ofrece los creditos y deja fuera las tarjetas`() {
        assertEquals(listOf(ahorros, cdt, libranza), transferableAccounts(todas))
    }

    /**
     * La otra mitad: un crédito **nunca** queda elegido solo. Si pudiera, dos toques distraídos
     * anotarían un desembolso que nadie pidió.
     */
    @Test
    fun `lo que la app preselecciona sola jamas incluye un credito`() {
        assertEquals(listOf(ahorros, cdt), defaultTransferAccounts(todas))
        assertTrue(defaultTransferAccounts(listOf(libranza)).isEmpty())
    }

    // ── Ola 14 · en qué queda la deuda ────────────────────────────────────────

    @Test
    fun `un desembolso muestra la deuda subiendo`() {
        assertEquals(
            "Deuda de Libranza: $257.000.000 pasa a $277.000.000",
            deudaDespuesDelTraspaso(libranza, ahorros, 20_000_000L),
        )
    }

    @Test
    fun `un abono extraordinario muestra la deuda bajando`() {
        assertEquals(
            "Deuda de Libranza: $257.000.000 pasa a $252.000.000",
            deudaDespuesDelTraspaso(ahorros, libranza, 5_000_000L),
        )
    }

    @Test
    fun `sin credito, sin monto o con monto cero no hay nada que anticipar`() {
        assertNull(deudaDespuesDelTraspaso(ahorros, cdt, 250_000L))
        assertNull(deudaDespuesDelTraspaso(libranza, ahorros, null))
        assertNull(deudaDespuesDelTraspaso(libranza, ahorros, 0L))
        assertNull(deudaDespuesDelTraspaso(null, ahorros, 100L))
    }
}
