package com.jvillada.movi.server.screens

import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenSection
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenValidationTest {
    private fun banner(text: String = "hola") = ScreenSection(type = "BANNER", text = text)

    @Test fun `valid definition passes`() {
        assertNull(validateDefinition(listOf(ScreenSection(type = "HERO_BALANCE"), banner())))
    }

    @Test fun `unknown section type is rejected`() {
        val msg = validateDefinition(listOf(banner(), ScreenSection(type = "HOLOGRAM_3D")))
        assertNotNull(msg); assertTrue(msg.contains("HOLOGRAM_3D"))
    }

    @Test fun `navigate outside whitelist is rejected`() {
        val msg = validateDefinition(listOf(ScreenSection(type = "LINK_LIST",
            cards = listOf(ScreenCard(title = "x", action = ScreenAction("NAVIGATE", "settings"))))))
        assertNotNull(msg); assertTrue(msg.contains("settings"))
    }

    @Test fun `non-https open_url is rejected`() {
        val msg = validateDefinition(listOf(ScreenSection(type = "LINK_LIST",
            cards = listOf(ScreenCard(title = "x", action = ScreenAction("OPEN_URL", "http://inseguro"))))))
        assertNotNull(msg); assertTrue(msg.contains("https"))
    }

    @Test fun `unknown action type is rejected`() {
        val msg = validateDefinition(listOf(ScreenSection(type = "LINK_LIST",
            cards = listOf(ScreenCard(title = "x", action = ScreenAction("EXPLODE", "x"))))))
        assertNotNull(msg); assertTrue(msg.contains("EXPLODE"))
    }

    @Test fun `empty definition is rejected`() {
        assertNotNull(validateDefinition(emptyList()))
    }
}
