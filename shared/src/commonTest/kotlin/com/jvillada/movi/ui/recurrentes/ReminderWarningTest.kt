package com.jvillada.movi.ui.recurrentes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderWarningTest {

    @Test
    fun `push already enabled means nothing to warn about`() {
        assertFalse(shouldShowReminderWarning(pushStatus = "enabled", hayRecordatoriosPedidos = true))
    }

    @Test
    fun `no upcoming payments means the warning would not be actionable`() {
        assertFalse(shouldShowReminderWarning(pushStatus = "disabled", hayRecordatoriosPedidos = false))
        assertFalse(shouldShowReminderWarning(pushStatus = "denied", hayRecordatoriosPedidos = false))
    }

    @Test
    fun `unsupported platform has no fix to offer so we stay quiet`() {
        assertFalse(shouldShowReminderWarning(pushStatus = "unsupported", hayRecordatoriosPedidos = true))
    }

    @Test
    fun `disabled push with payments due is exactly the actionable case`() {
        assertTrue(shouldShowReminderWarning(pushStatus = "disabled", hayRecordatoriosPedidos = true))
    }

    @Test
    fun `denied push with payments due still warns, just with a different fix`() {
        assertTrue(shouldShowReminderWarning(pushStatus = "denied", hayRecordatoriosPedidos = true))
    }

    // ── La casilla «Recordarme unos días antes» en las hojas de crear/editar ──

    @Test
    fun `la casilla desmarcada no promete nada, asi que no hay nada que advertir`() {
        assertFalse(shouldShowReminderOptInWarning(pushStatus = "disabled", remindMe = false))
        assertFalse(shouldShowReminderOptInWarning(pushStatus = "denied", remindMe = false))
    }

    @Test
    fun `la casilla marcada sin canal activo tiene que decir la verdad`() {
        assertTrue(shouldShowReminderOptInWarning(pushStatus = "disabled", remindMe = true))
        assertTrue(shouldShowReminderOptInWarning(pushStatus = "denied", remindMe = true))
    }

    @Test
    fun `la casilla marcada con push activo no advierte nada`() {
        assertFalse(shouldShowReminderOptInWarning(pushStatus = "enabled", remindMe = true))
    }

    @Test
    fun `sin soporte de push no hay instruccion que dar, asi que no se advierte`() {
        assertFalse(shouldShowReminderOptInWarning(pushStatus = "unsupported", remindMe = true))
    }

    @Test
    fun `la linea chica dice cuantos dias antes avisa, en singular y en plural`() {
        assertEquals("Te avisamos 3 días antes del vencimiento.", reminderLeadHint(3))
        assertEquals("Te avisamos 1 día antes del vencimiento.", reminderLeadHint(1))
    }

    @Test
    fun `con cero dias de anticipacion la linea no miente — avisa el mismo dia`() {
        assertEquals("Te avisamos el día del vencimiento.", reminderLeadHint(0))
    }
}
