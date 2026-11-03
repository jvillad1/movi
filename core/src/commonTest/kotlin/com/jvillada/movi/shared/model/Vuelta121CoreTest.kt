package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 121 · lo puro de la vuelta en `:core`: desde cuándo corre una regla guardada (el arranque
 * guardado o, sin él, la fecha de creación) y cuándo la hoja de un pago fijo nuevo pregunta «¿Ya pagaste
 * el de este período?».
 */
class Vuelta121CoreTest {

    private val calendario = PeriodSettings()
    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val dosDeNoviembre = LocalDate(2026, 11, 2)

    // ── El piso ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sin arranque guardado la regla corre desde el dia en que se creo`() {
        assertEquals("2026-11-02", arranqueDeLaReglaGuardada(activeFrom = null, creadaEl = "2026-11-02"))
        assertEquals("2026-11-02", arranqueDeLaReglaGuardada(activeFrom = "  ", creadaEl = "2026-11-02"))
    }

    @Test
    fun `con arranque guardado manda ese, tambien si es anterior a la creacion`() {
        // Desde un movimiento del período pasado: su ocurrencia existe y la salda ese movimiento.
        assertEquals("2026-10-15", arranqueDeLaReglaGuardada(activeFrom = "2026-10-15", creadaEl = "2026-11-02"))
        // «Ya pagué»: el período siguiente.
        assertEquals("2026-12-01", arranqueDeLaReglaGuardada(activeFrom = "2026-12-01", creadaEl = "2026-11-02"))
    }

    @Test
    fun `una regla de antes de created_at sigue corriendo desde siempre`() {
        assertNull(arranqueDeLaReglaGuardada(activeFrom = null, creadaEl = null))
        assertNull(arranqueDeLaReglaGuardada(activeFrom = "28/10/2026", creadaEl = "ayer"), "lo ilegible no es una fecha")
        assertEquals("2026-11-02", arranqueDeLaReglaGuardada(activeFrom = "basura", creadaEl = "2026-11-02"))
    }

    @Test
    fun `lo sellado antes de la creacion baja el piso, nunca lo sube`() {
        assertEquals("2026-10-28", arranqueDeLaReglaGuardada(null, "2026-11-02", primerSello = "2026-10-28"))
        assertEquals("2026-11-02", arranqueDeLaReglaGuardada(null, "2026-11-02", primerSello = "2026-12-28"))
        // Un sello no toca un arranque guardado ni le pone piso a una regla sin fecha.
        assertEquals("2026-11-02", arranqueDeLaReglaGuardada("2026-11-02", "2026-11-02", primerSello = "2026-09-10"))
        assertNull(arranqueDeLaReglaGuardada(null, null, primerSello = "2026-09-10"))
    }

    // ── La pregunta ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `se pregunta en un gasto escrito a mano cuya fecha de este periodo ya paso o es hoy`() {
        assertTrue(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 1, dosDeNoviembre, calendario))
        assertTrue(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 2, dosDeNoviembre, calendario), "vence hoy")
        assertFalse(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 28, dosDeNoviembre, calendario), "el 28 todavía no")
        // Con corte 25 el período del 2-nov va del 25-oct al 24-nov: el 28 de octubre ya pasó, el 20 de noviembre no.
        assertTrue(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 28, dosDeNoviembre, corte25))
        assertFalse(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 20, dosDeNoviembre, corte25))
    }

    @Test
    fun `no se pregunta en un ingreso ni en una regla desde un movimiento`() {
        assertFalse(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.INCOME, false, 1, dosDeNoviembre, calendario))
        assertFalse(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, true, 1, dosDeNoviembre, calendario))
        assertFalse(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 0, dosDeNoviembre, calendario))
    }

    @Test
    fun `la version del server lee hoy en ISO`() {
        assertTrue(sePreguntaSiYaPagasteElDeEstePeriodoEl(TransactionType.EXPENSE, false, 1, "2026-11-02", calendario))
        assertFalse(sePreguntaSiYaPagasteElDeEstePeriodoEl(TransactionType.EXPENSE, false, 1, "2-nov", calendario))
        // Con «Sí», la regla arranca en el período siguiente (la cuenta de la vuelta 77).
        assertEquals("2026-12-01", arranqueDeLasCuotasSiYaPagasteEl("2026-11-02", calendario))
        assertEquals("2026-11-25", arranqueDeLasCuotasSiYaPagasteEl("2026-11-02", corte25))
    }
}
