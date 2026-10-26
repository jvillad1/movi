package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Quién ve la guía «Primer uso»: sin terminarla y sin ninguna cuenta de dinero. */
class LaGuiaDePrimerUsoTest {

    private val nuevo = UserProfile(id = "u", email = "u@movi.test", name = "U", avatarColor = AvatarPalette.DEFAULT)

    @Test
    fun `una cuenta nueva sin cuentas la ve`() {
        assertTrue(debeAbrirseLaGuiaDePrimerUso(nuevo, emptyList()))
    }

    @Test
    fun `quien ya tiene una cuenta de dinero no la ve aunque nunca la haya terminado`() {
        listOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH).forEach { tipo ->
            assertFalse(debeAbrirseLaGuiaDePrimerUso(nuevo, listOf(Account("a", "Bancolombia", tipo, 0))), "$tipo")
        }
    }

    @Test
    fun `una deuda o una inversion sola no cuentan como cuenta de dinero`() {
        assertTrue(
            debeAbrirseLaGuiaDePrimerUso(
                nuevo,
                listOf(Account("t", "Visa", AccountType.CREDIT_CARD, 0), Account("i", "CDT", AccountType.INVESTMENT, 0)),
            ),
        )
    }

    @Test
    fun `terminada o descartada no vuelve`() {
        assertFalse(debeAbrirseLaGuiaDePrimerUso(nuevo.copy(primerUsoEn = 1L), emptyList()))
    }

    @Test
    fun `sin saber el perfil o las cuentas no se abre`() {
        assertFalse(debeAbrirseLaGuiaDePrimerUso(null, emptyList()))
        assertFalse(debeAbrirseLaGuiaDePrimerUso(nuevo, null))
    }
}
