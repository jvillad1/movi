package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 98 · las reglas de «Editar la cuenta», las mismas en el server y en la hoja. */
class EditarLaCuentaTest {

    private fun cuenta(tipo: AccountType, bien: Bien? = null) = Account("a", "Cuenta", tipo, 0L, bien = bien)

    @Test
    fun `el nombre sigue la regla de crear`() {
        assertEquals("El nombre no puede estar vacío", problemaDelNombreDeCuenta("   "))
        assertNotNull(problemaDelNombreDeCuenta("x".repeat(MAX_ACCOUNT_NAME_LENGTH + 1)))
        assertNull(problemaDelNombreDeCuenta("x".repeat(MAX_ACCOUNT_NAME_LENGTH)))
        assertNull(problemaDelNombreDeCuenta("  Ahorros  "), "se recorta antes de medir")
    }

    @Test
    fun `dentro de la plata el tipo cambia, entre plata y deuda no`() {
        val dinero = listOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH, AccountType.INVESTMENT)
        for (desde in dinero) for (hacia in dinero) assertNull(problemaDelCambioDeTipo(cuenta(desde), hacia), "$desde → $hacia")
        for (d in dinero) {
            assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(d), AccountType.LOAN))
            assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CREDIT_CARD), d))
        }
        assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CREDIT_CARD), AccountType.LOAN))
        assertNull(problemaDelCambioDeTipo(cuenta(AccountType.LOAN), AccountType.LOAN), "el mismo tipo siempre pasa")
        val casa = cuenta(AccountType.INVESTMENT, Bien(clase = CLASE_DE_BIEN_INMUEBLE, valor = 1L, valorAl = "2026-10-01"))
        assertEquals(UN_BIEN_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(casa, AccountType.SAVINGS))
    }

    @Test
    fun `el saldo se edita donde se cuadra`() {
        assertTrue(seEditaElSaldo(cuenta(AccountType.SAVINGS)))
        assertTrue(seEditaElSaldo(cuenta(AccountType.INVESTMENT)))
        assertEquals(false, seEditaElSaldo(cuenta(AccountType.CREDIT_CARD)))
        assertEquals(false, seEditaElSaldo(cuenta(AccountType.LOAN)))
    }

    @Test
    fun `renombrar avisa lo que Movi deja de reconocer`() {
        assertEquals(listOf("8133"), loQueDejaDeReconocerse("Bancolombia 02955068133", "Ahorros del papá"))
        assertEquals(emptyList(), loQueDejaDeReconocerse("Master Black 3684", "Mastercard Black 3684"))
        assertEquals(listOf("Skandia"), loQueDejaDeReconocerse("Skandia", "Pensión voluntaria"))
        assertEquals(emptyList(), loQueDejaDeReconocerse("Skandia", "Skandia pensión voluntaria"))
        assertNull(avisoAlRenombrar("Nequi", "Nequi del papá"))
        val aviso = avisoAlRenombrar("AFC Davibank 9497", "AFC")
        assertNotNull(aviso)
        assertTrue("el 9497" in aviso, aviso)
        assertNull(avisoAlRenombrar("AFC 9497", "  "), "con el nombre vacío lo que se dice es que falta")
    }
}
