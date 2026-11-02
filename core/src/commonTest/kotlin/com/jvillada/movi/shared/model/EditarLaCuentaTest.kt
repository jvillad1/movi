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
    fun `el tipo solo cambia entre Ahorros y Corriente`() {
        assertNull(problemaDelCambioDeTipo(cuenta(AccountType.SAVINGS), AccountType.CHECKING))
        assertNull(problemaDelCambioDeTipo(cuenta(AccountType.CHECKING), AccountType.SAVINGS))
        for (t in AccountType.entries) assertNull(problemaDelCambioDeTipo(cuenta(t), t), "el mismo tipo siempre pasa: $t")
        // Inversión sale del disponible y de los períodos; el efectivo no recibe extractos: ni de ida ni de vuelta.
        assertEquals(UNA_INVERSION_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.SAVINGS), AccountType.INVESTMENT))
        assertEquals(UNA_INVERSION_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.INVESTMENT), AccountType.CHECKING))
        assertEquals(EL_EFECTIVO_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CHECKING), AccountType.CASH))
        assertEquals(EL_EFECTIVO_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CASH), AccountType.SAVINGS))
        assertEquals(EL_EFECTIVO_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CASH), AccountType.INVESTMENT))
        for (d in listOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH, AccountType.INVESTMENT)) {
            assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(d), AccountType.LOAN))
            assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CREDIT_CARD), d))
        }
        assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(cuenta(AccountType.CREDIT_CARD), AccountType.LOAN))
        val casa = cuenta(AccountType.INVESTMENT, Bien(clase = CLASE_DE_BIEN_INMUEBLE, valor = 1L, valorAl = "2026-10-01"))
        assertEquals(UN_BIEN_NO_CAMBIA_DE_TIPO, problemaDelCambioDeTipo(casa, AccountType.SAVINGS))
        // Lo que la hoja ofrece dice lo mismo.
        assertNull(porQueElTipoEsFijo(cuenta(AccountType.SAVINGS)))
        assertNull(porQueElTipoEsFijo(cuenta(AccountType.CHECKING)))
        assertEquals(EL_EFECTIVO_NO_CAMBIA_DE_TIPO, porQueElTipoEsFijo(cuenta(AccountType.CASH)))
        assertEquals(UNA_INVERSION_NO_CAMBIA_DE_TIPO, porQueElTipoEsFijo(cuenta(AccountType.INVESTMENT)))
        assertEquals(UNA_DEUDA_NO_CAMBIA_DE_TIPO, porQueElTipoEsFijo(cuenta(AccountType.LOAN)))
    }

    @Test
    fun `la hermana en otra moneda se empareja con la regla de los extractos`() {
        assertTrue(emparejaEnOtraMoneda("Master Black 3684", "Master Black 3684 USD"))
        assertTrue(emparejaEnOtraMoneda("Master Black 3684 USD", "Master Black 3684"))
        assertTrue(emparejaEnOtraMoneda("Amex", "Amex USD"), "el mismo nombre sin la moneda")
        assertEquals(false, emparejaEnOtraMoneda("Visa 3684", "Visa 13684 USD"), "3684 dentro de otro número no es el mismo")
        // 5+ dígitos no son «los cuatro»: «02955068133» no empareja con «8133 USD».
        assertEquals(false, emparejaEnOtraMoneda("Bancolombia 02955068133", "Bancolombia 8133 USD"))

        val cop = Account("a", "Master Black 3684", AccountType.CREDIT_CARD, 0L, currency = "COP")
        val usd = Account("b", "Master Black 3684 USD", AccountType.CREDIT_CARD, 0L, currency = "USD")
        val otra = Account("c", "Ahorros 3684", AccountType.SAVINGS, 0L, currency = "USD")
        val cuentas = listOf(cop, usd, otra)
        assertEquals(emptyList(), hermanasQueSeDesemparejan(cop, "Mastercard Black 3684", cuentas), "los cuatro siguen")
        assertEquals(listOf(usd), hermanasQueSeDesemparejan(cop, "Tarjeta negra", cuentas), "solo la del mismo tipo")
        assertEquals(
            "La cuenta en USD «Master Black 3684 USD» deja de emparejarse con esta.",
            avisoDeLasHermanas(cop, "Tarjeta negra", cuentas),
        )
        // Desde la hermana también.
        assertEquals(listOf(cop), hermanasQueSeDesemparejan(usd, "Dólares", cuentas))
        assertNull(avisoDeLasHermanas(cop, "   ", cuentas))
        // Una que no se emparejaba no se nombra.
        val sinPar = Account("d", "Bancolombia 02955068133", AccountType.SAVINGS, 0L)
        val dolares = Account("e", "Bancolombia 8133 USD", AccountType.SAVINGS, 0L, currency = "USD")
        assertEquals(emptyList(), hermanasQueSeDesemparejan(sinPar, "Ahorros", listOf(sinPar, dolares)))
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
