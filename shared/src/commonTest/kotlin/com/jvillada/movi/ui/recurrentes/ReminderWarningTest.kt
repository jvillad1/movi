package com.jvillada.movi.ui.recurrentes

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderWarningTest {

    @Test
    fun `push already enabled means nothing to warn about`() {
        assertFalse(shouldShowReminderWarning(pushStatus = "enabled", hasUpcomingPayments = true))
    }

    @Test
    fun `no upcoming payments means the warning would not be actionable`() {
        assertFalse(shouldShowReminderWarning(pushStatus = "disabled", hasUpcomingPayments = false))
        assertFalse(shouldShowReminderWarning(pushStatus = "denied", hasUpcomingPayments = false))
    }

    @Test
    fun `unsupported platform has no fix to offer so we stay quiet`() {
        assertFalse(shouldShowReminderWarning(pushStatus = "unsupported", hasUpcomingPayments = true))
    }

    @Test
    fun `disabled push with payments due is exactly the actionable case`() {
        assertTrue(shouldShowReminderWarning(pushStatus = "disabled", hasUpcomingPayments = true))
    }

    @Test
    fun `denied push with payments due still warns, just with a different fix`() {
        assertTrue(shouldShowReminderWarning(pushStatus = "denied", hasUpcomingPayments = true))
    }
}
