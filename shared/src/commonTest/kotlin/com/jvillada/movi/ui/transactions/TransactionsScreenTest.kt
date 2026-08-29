package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F13: [matchesQuery] es el filtro puro detrás de la búsqueda de Movimientos — sin acentos ni
 * mayúsculas, contra descripción, comercio y categoría.
 */
class TransactionsScreenTest {

    private fun event(
        description: String = "Almuerzo",
        merchant: String? = null,
        category: String = "Comida",
    ) = FinancialEvent(
        id = "1",
        accountId = "a1",
        type = TransactionType.EXPENSE,
        amount = 25_000,
        category = category,
        description = description,
        merchant = merchant,
        timestamp = 0L,
    )

    @Test
    fun `consulta en blanco matchea todo`() {
        assertTrue(matchesQuery(event(), ""))
        assertTrue(matchesQuery(event(), "   "))
    }

    @Test
    fun `matchea por descripcion sin importar tildes`() {
        // "Éxito" con tilde en el evento, "exito" sin tilde en la búsqueda.
        assertTrue(matchesQuery(event(description = "Compra en Éxito"), "exito"))
        assertTrue(matchesQuery(event(description = "Almuerzo frisby"), "Frisby"))
    }

    @Test
    fun `matchea por mayusculas indistintas`() {
        assertTrue(matchesQuery(event(description = "NETFLIX MENSUAL"), "netflix"))
        assertTrue(matchesQuery(event(description = "netflix mensual"), "NETFLIX"))
    }

    @Test
    fun `comercio nulo no rompe la busqueda y simplemente no matchea por ese campo`() {
        val ev = event(description = "Pago manual", merchant = null, category = "Otros")
        assertFalse(matchesQuery(ev, "mercado"))
        // Sigue matcheando por descripción aunque no haya comercio.
        assertTrue(matchesQuery(ev, "manual"))
    }

    @Test
    fun `matchea por comercio cuando existe`() {
        val ev = event(description = "Compra", merchant = "Mercado Fresco")
        assertTrue(matchesQuery(ev, "fresco"))
    }

    @Test
    fun `matchea por categoria`() {
        val ev = event(description = "Colegiatura marzo", category = "Educación")
        assertTrue(matchesQuery(ev, "educacion"))
    }

    @Test
    fun `una consulta sin coincidencia en ningun campo no matchea`() {
        val ev = event(description = "Almuerzo", merchant = "Frisby", category = "Comida")
        assertFalse(matchesQuery(ev, "xyzxyz"))
    }

    // ── Ola 8 · V6 — el saldo inicial no es un ingreso ────────────────────────────

    private fun apertura() = FinancialEvent(
        id = "ap1",
        accountId = "a1",
        type = TransactionType.INCOME,
        amount = 3_500_000,
        category = OPENING_CATEGORY,
        description = "Saldo inicial",
        timestamp = 0L,
    )

    @Test
    fun `el saldo inicial no entra en el chip Ingresos`() {
        // Aparecía en verde y con «+» bajo un filtro llamado Ingresos, mientras el total de
        // arriba lo excluía a propósito: la contradicción que V6 vino a sacar.
        assertFalse(matchesChip(apertura(), CHIP_INGRESOS))
    }

    @Test
    fun `el saldo inicial si aparece en Todo`() {
        assertTrue(matchesChip(apertura(), CHIP_TODO))
    }

    @Test
    fun `un ingreso de verdad si entra en el chip Ingresos`() {
        val nomina = FinancialEvent(
            id = "n1",
            accountId = "a1",
            type = TransactionType.INCOME,
            amount = 4_500_000,
            category = "Salario",
            description = "Nomina agosto",
            timestamp = 0L,
        )
        assertTrue(matchesChip(nomina, CHIP_INGRESOS))
    }

    @Test
    fun `el renglon del saldo inicial no lleva signo`() {
        assertFalse(rowShowsSign(apertura()))
        assertTrue(rowShowsSign(event()))
    }

    // ── Ola 15 · la pata huérfana tampoco es un ingreso ni un gasto ───────────────
    //
    // Misma decisión y mismo motivo que V6, un año después y con una cifra mucho más cara: el
    // borrado del crédito de $257.000.000 desembolsado dejaba un «+$257.000.000» verde arriba de
    // todo, bajo el chip Ingresos, mientras el total del mes —correctamente— ya no lo contaba.

    /**
     * **`reconciliationStatus` va explícito y es lo que hace real al test del chip Gastos.**
     *
     * El default de [FinancialEvent] es `UNCONFIRMED`, y `CHIP_GASTOS` descarta lo no confirmado
     * ANTES de mirar la categoría: con el default, ese test pasaba sin tocar la cláusula que dice
     * probar — medido, borrando `!isOrphanedTransferLeg` de los dos chips solo se ponía rojo el de
     * Ingresos. Y no es un tecnicismo del fixture: `transferLegsFor` crea las patas `RECONCILED`,
     * así que la pata huérfana real SÍ llega hasta la cláusula nueva.
     */
    private fun pataHuerfana(tipo: TransactionType = TransactionType.INCOME) = FinancialEvent(
        id = "ph1",
        accountId = "a1",
        type = tipo,
        amount = 257_000_000,
        category = ORPHANED_LEG_CATEGORY,
        description = "Desembolso desde Crédito · cuenta eliminada",
        timestamp = 0L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    @Test
    fun `la pata huerfana no entra en el chip Ingresos`() {
        assertFalse(matchesChip(pataHuerfana(), CHIP_INGRESOS))
    }

    @Test
    fun `la pata huerfana de salida no entra en el chip Gastos`() {
        assertFalse(matchesChip(pataHuerfana(TransactionType.EXPENSE), CHIP_GASTOS))
    }

    @Test
    fun `la pata huerfana si aparece en Todo, que es donde se la puede arreglar`() {
        // No puede desaparecer de la lista: es la única puerta para recategorizarla si esa plata
        // sí se movió de verdad.
        assertTrue(matchesChip(pataHuerfana(), CHIP_TODO))
    }

    @Test
    fun `el renglon de la pata huerfana no lleva signo`() {
        assertFalse(rowShowsSign(pataHuerfana()))
    }

    /**
     * `isTransferLeg` dice `false` para ella **a propósito**: el borrado le sacó el `transferId`,
     * no hay hermana con la que juntarla en un renglón único y sí se puede recategorizar. Las dos
     * preguntas son distintas y este test las mantiene distintas.
     */
    @Test
    fun `la pata huerfana ya no es una pata de traspaso para collapseTransfers`() {
        assertFalse(isTransferLeg(pataHuerfana()))
        assertTrue(isOrphanedTransferLeg(pataHuerfana()))
    }

    // ── Ola 8 · V13 — encabezados de día legibles ─────────────────────────────────

    @Test
    fun `el encabezado del dia dice Hoy y Ayer`() {
        assertEquals("Hoy", formatDayHeading("2026-08-23", hoy = "2026-08-23"))
        assertEquals("Ayer", formatDayHeading("2026-08-22", hoy = "2026-08-23"))
    }

    @Test
    fun `el encabezado del dia dice la fecha en palabras`() {
        assertEquals("15 de agosto", formatDayHeading("2026-08-15", hoy = "2026-08-23"))
        assertEquals("1 de enero", formatDayHeading("2026-01-01", hoy = "2026-08-23"))
    }

    @Test
    fun `el ano solo se dice cuando no es el corriente`() {
        assertEquals("20 de diciembre de 2025", formatDayHeading("2025-12-20", hoy = "2026-08-23"))
    }

    @Test
    fun `cruzando el fin de mes Ayer sigue siendo Ayer`() {
        assertEquals("Ayer", formatDayHeading("2026-07-31", hoy = "2026-08-01"))
    }

    @Test
    fun `una fecha ilegible se devuelve tal cual en vez de tumbar la lista`() {
        assertEquals("no-es-fecha", formatDayHeading("no-es-fecha", hoy = "2026-08-23"))
    }
}
