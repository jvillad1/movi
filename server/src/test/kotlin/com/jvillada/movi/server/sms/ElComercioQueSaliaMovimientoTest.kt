package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Lecturas sueltas que salían «Movimiento»** (auditoría con los datos reales, 4-oct-2026): el
 * débito programado de Bancolombia, el retiro de la Fiducuenta «hacia la cuenta *…», la promo de
 * Crediágil que no se apartaba y la cuenta propia ofrecida como «de otro». Textos sintéticos con la
 * forma de los reales; los números de producto están cambiados.
 */
class ElComercioQueSaliaMovimientoTest {

    @Test
    fun `el debito programado se llama como quien cobra`() {
        val sms = "Bancolombia informa pago Factura Programada IGS MULTIASISTE  Ref 12019266 por \$36.890,00 " +
            "desde Aho*3333. 15/09/2026. Inquietudes 6045109095/018000931987."
        val p = assertNotNull(parseSms(sms, "85784"))
        assertEquals("IGS MULTIASISTE", p.merchant)
        assertEquals(36_890.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
    }

    @Test
    fun `el retiro de la Fiducuenta nombra la cuenta a la que fue`() {
        val sms = "Bancolombia: Retiraste \$3,500,000.00 de tu cuenta *9999 Fiducuenta el 2026/09/10 13:35:32, " +
            "hacia la cuenta *25318620000. ¿Dudas? 6045109009"
        val p = assertNotNull(parseSms(sms, "85784"))
        assertEquals("Transferencia a la cuenta *25318620000", p.merchant)
        assertEquals(3_500_000.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
    }

    @Test
    fun `la oferta de credito con tasa especial se aparta como promocion`() {
        val sms = "Bancolombia: Septiembre trae planes y motivos para compartir. Con tu Crediagil, del 18 al 20 " +
            "aprovecha tasa especial de 1.5% M.V. (19.56% E.A.) Usalo desde app Mi Bancolombia"
        assertEquals(MotivoDeApartado.PROMOCION, motivoParaApartar(sms, "85630"))
    }

    @Test
    fun `un movimiento que menciona una tasa no se aparta`() {
        // La forma de un movimiento manda sobre la promoción.
        assertNull(motivoParaApartar("Bancolombia: Compraste \$50.000 en TIENDA. Aprovecha tasa especial en tu tarjeta.", "85540"))
    }

    // ── La cuenta propia no es «de otro» ─────────────────────────────────────

    private val avisosDelDueno = listOf(
        "Bancolombia: Transferiste \$20,417 desde tu cuenta *3333 a la cuenta *43087510000 el 11/08/2026.",
        "Bancolombia: Pagaste \$138,600.00 a Coomeva desde tu producto 3333 el 05/09/2026.",
    )

    @Test
    fun `los numeros propios salen de los nombres de las cuentas y de lo que el banco escribe como suyo`() {
        val propios = numerosPropios(listOf("Bancolombia Ahorros", "Fiducuenta 9999", "Nu Tarjeta"), avisosDelDueno)
        assertEquals(setOf("9999", "3333"), propios)
    }

    @Test
    fun `el retiro hacia la cuenta propia no ofrece guardarla como de otro`() {
        val propios = numerosPropios(listOf("Fiducuenta 9999"), avisosDelDueno)
        val alPropio = assertNotNull(
            parseSms("Bancolombia: Retiraste \$4,200,000.00 de tu cuenta *9999 Fiducuenta el 2026/09/18 19:38:31, hacia la cuenta *02955063333."),
        )
        assertEquals("02955063333", alPropio.identificadorDelDestino, "el parser lo sigue leyendo")
        assertNull(sinLaCuentaPropia(alPropio, propios).identificadorDelDestino, "pero no se ofrece")

        val aOtro = assertNotNull(
            parseSms("Bancolombia: Retiraste \$3,500,000.00 de tu cuenta *9999 Fiducuenta el 2026/09/10, hacia la cuenta *25318620000."),
        )
        assertEquals("25318620000", sinLaCuentaPropia(aOtro, propios).identificadorDelDestino)
    }

    @Test
    fun `una llave nunca se toma por una cuenta propia`() {
        val llave = ParsedSms(6_800.0, "Pago QR · llave 0088043333", TransactionType.EXPENSE, "Otros",
            identificadorDelDestino = "0088043333", identificadorEsLlave = true)
        assertEquals("0088043333", sinLaCuentaPropia(llave, setOf("3333")).identificadorDelDestino)
    }
}
