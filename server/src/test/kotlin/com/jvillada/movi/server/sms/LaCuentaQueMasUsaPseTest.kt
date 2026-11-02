package com.jvillada.movi.server.sms

import com.jvillada.movi.server.correo.textoDePse
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 107 · un correo de PSE solo propone la cuenta de la que más sale PSE, nunca una poco usada
 *
 * El correo de PSE no dice desde qué cuenta se pagó. Sin un pago anterior a esa deuda, sin una regla de
 * esa empresa y sin un pago anterior a ella, ahora propone la cuenta de la que más pagos por PSE salieron
 * —con al menos dos, y sin empate—; si no, ninguna, y la app pide elegir. Los avisos son sintéticos con
 * la forma de los del 25-oct (el SMS del 85784 y el correo de PSE de Coomeva).
 */
class LaCuentaQueMasUsaPseTest {

    private fun cuenta(id: String, nombre: String, tipo: AccountType = AccountType.SAVINGS) =
        Account(id = id, name = nombre, type = tipo, balance = 0, currency = "COP")

    private val b0031 = cuenta("b0031", "Bancolombia Ahorros 0031")
    private val b8133 = cuenta("b8133", "Bancolombia Ahorros 8133 · 4057")
    private val nu = cuenta("nu", "Nu")
    private val tarjeta = cuenta("tc", "Master Black 3684", AccountType.CREDIT_CARD)
    private val cuentas = listOf(b0031, b8133, nu, tarjeta)

    private fun evento(id: String, cuenta: Account) = FinancialEvent(
        id = id, accountId = cuenta.id, type = TransactionType.EXPENSE, amount = 1, currency = "COP",
        category = "c", description = "x", timestamp = 1_000,
    )

    private fun smsDePse(empresa: String, producto: String) =
        "Bancolombia: Pagaste \$50,000.00 a $empresa desde tu producto $producto el 01/10/2026 10:00:00. ¿Dudas? Llamanos al 6045109095."

    /** Una empresa que nunca se pagó antes: ni deuda, ni regla, ni memoria. */
    private val nuevo = ParsedSms(200_000.0, "Gimnasio del Barrio", TransactionType.EXPENSE, "Salud", nota = null)

    @Test
    fun `cuenta los pagos por PSE confirmados, un movimiento una vez aunque lo avisen el SMS y el correo`() {
        val eventos = listOf(evento("e1", b8133), evento("e2", b8133), evento("e3", nu), evento("e4", b0031))
        val avisos = listOf(
            smsDePse("Empresa Uno", "8133") to "e1",
            textoDePse(valor = "\$ 50.000,00", empresa = "Empresa Uno") to "e1",
            smsDePse("Empresa Dos", "8133") to "e2",
            textoDePse(valor = "\$ 70.000,00", empresa = "Empresa Tres", cus = "700000003") to "e3",
            // Una compra con tarjeta débito no es PSE.
            "Bancolombia: Compraste \$8.100,00 en TIENDA con tu T.Deb *4057, el 28/10/2026 a las 14:41." to "e4",
            // Un aviso cuyo movimiento se anuló (no está en los vivos) no cuenta.
            smsDePse("Empresa Cuatro", "0031") to "anulado",
        )
        assertEquals(mapOf("b8133" to 2, "nu" to 1), pagosPorPseDe(avisos, eventos))
    }

    @Test
    fun `sin nada de esa empresa, propone la cuenta de la que mas sale PSE`() {
        val p = loQueMoviSabeDelPago(nuevo, cuentas, emptyList(), emptyList(), emptyList(), pagosPorPse = mapOf("b8133" to 4, "b0031" to 1))
        assertEquals("b8133", p.cuentaSugeridaId)
        assertEquals(CUENTA_QUE_MAS_USA_PSE, p.cuentaSugeridaPor)
    }

    @Test
    fun `con poca historia o un empate no propone nada - la app pide elegir`() {
        assertNull(loQueMoviSabeDelPago(nuevo, cuentas, emptyList(), emptyList(), emptyList(), pagosPorPse = mapOf("b0031" to 1)).cuentaSugeridaId)
        assertNull(loQueMoviSabeDelPago(nuevo, cuentas, emptyList(), emptyList(), emptyList(), pagosPorPse = mapOf("b0031" to 2, "b8133" to 2)).cuentaSugeridaId)
        assertNull(loQueMoviSabeDelPago(nuevo, cuentas, emptyList(), emptyList(), emptyList()).cuentaSugeridaId)
        // Sin mayoría clara (3 de 6 = 50 %, menos del 60 %): ninguna. Con 3 de 5 (60 %), sí.
        assertNull(cuentaQueMasUsaPse(mapOf("b8133" to 3, "b0031" to 2, "nu" to 1), setOf("b0031", "b8133", "nu")))
        assertEquals("b8133", cuentaQueMasUsaPse(mapOf("b8133" to 3, "b0031" to 2), setOf("b0031", "b8133", "nu")))
        // Una tarjeta no paga por PSE: aunque tenga más, no entra.
        assertNull(cuentaQueMasUsaPse(mapOf("tc" to 9), setOf("b0031", "b8133", "nu")))
    }

    @Test
    fun `la regla de la empresa le sigue ganando a la costumbre`() {
        // El nombre de la regla del dueño («Coomeva Familiar») se reconoce por la nota del correo de PSE.
        val coomeva = ParsedSms(138_600.0, "Coomeva Medicina Prepagada", TransactionType.EXPENSE, "Salud", nota = "Coomeva Pago de saldo plan familiar")
        val reglas = listOf(ReglaDelDueno("Coomeva Familiar", "b8133", TransactionType.EXPENSE))
        val p = loQueMoviSabeDelPago(coomeva, cuentas, emptyList(), reglas, emptyList(), pagosPorPse = mapOf("nu" to 5))
        assertEquals("b8133", p.cuentaSugeridaId)
        assertEquals("La de tu recurrente «Coomeva Familiar»", p.cuentaSugeridaPor)
    }

    @Test
    fun `la ultima vez no vuelve a proponer una cuenta casi sin pagos por PSE`() {
        // El caso del 25-oct: el último pago a Coomeva quedó (mal) en la 0031, que tiene ese único pago por PSE.
        val coomeva = ParsedSms(138_600.0, "Coomeva Medicina Prepagada", TransactionType.EXPENSE, "Salud", nota = null)
        val ultimo = evento("coomeva-oct", b0031).copy(merchant = "Coomeva Medicina Prepagada S A")
        val p = loQueMoviSabeDelPago(coomeva, cuentas, emptyList(), emptyList(), listOf(ultimo), pagosPorPse = mapOf("b0031" to 1, "b8133" to 4))
        assertEquals("b8133", p.cuentaSugeridaId)
        assertEquals(CUENTA_QUE_MAS_USA_PSE, p.cuentaSugeridaPor)
        // Sin saber de dónde sale PSE, la última vez sigue valiendo.
        assertEquals("b0031", loQueMoviSabeDelPago(coomeva, cuentas, emptyList(), emptyList(), listOf(ultimo)).cuentaSugeridaId)
    }
}
