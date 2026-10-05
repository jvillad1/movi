package com.jvillada.movi.server.sms

import com.jvillada.movi.server.correo.cuerpoDePse
import com.jvillada.movi.server.correo.textoDePse
import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.correo.asuntoDePse
import com.jvillada.movi.server.correo.sinEtiquetas
import com.jvillada.movi.server.correo.limpiarCuerpoDelCorreo
import com.jvillada.movi.server.routes.cusDelCorreoDePse
import com.jvillada.movi.server.routes.empresaLimpia
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **El correo de PSE se lee**: monto, empresa (limpia), descripción como nota, fecha de la
 * transacción, y qué es el pago según la descripción. Textos sintéticos con la forma del real.
 */
class ElCorreoDePseSeLeeTest {

    private val origen = "Correo · PSE"

    @Test
    fun `el pago a Coomeva se lee entero`() {
        val p = assertNotNull(parseSms(textoDePse(), origen))
        assertEquals(138_600.0, p.amount)
        assertEquals("COP", p.currency)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("Coomeva Medicina Prepagada", p.merchant, "sin el «S.A.» del final")
        assertEquals("Coomeva Pago de saldo plan familiar", p.nota)
        assertEquals("2026-10-03", p.fecha)
        assertEquals("Salud", p.category)
        assertNull(p.identificadorDelDestino)
    }

    @Test
    fun `las tres formas del monto`() {
        assertEquals(4_178_163.0, assertNotNull(parseSms(textoDePse(valor = "\$ 4.178.163,00"), origen)).amount)
        assertEquals(138_600.0, assertNotNull(parseSms(textoDePse(valor = "\$ 138.600"), origen)).amount)
        assertEquals(115_113.07, assertNotNull(parseSms(textoDePse(valor = "\$ 115.113,07"), origen)).amount)
    }

    @Test
    fun `la empresa se limpia de S A, SAS y ATH`() {
        assertEquals("Coomeva Medicina Prepagada", empresaLimpia("Coomeva Medicina Prepagada S.A."))
        assertEquals("Banco de Occidente", empresaLimpia("Banco de Occidente (ATH)"))
        assertEquals("Banco de Occidente", empresaLimpia("Banco de Occidente S A ATH"))
        assertEquals("PEXTO", empresaLimpia("PEXTO SAS"))
        assertEquals("Colombia Telecomunicaciones", empresaLimpia("Colombia Telecomunicaciones S.A. E.S.P."))
        assertEquals("NU Compañía de Financiamiento", empresaLimpia("NU Compañía de Financiamiento"))
    }

    @Test
    fun `la cuota de un credito se reconoce por la descripcion`() {
        listOf(
            "Banco de Occidente (ATH)" to "PAGO Banco de Occidente - Prestamo",
            "Banco Comercial AV Villas" to "PAGO AV VILLAS - CRÉDITO PERSONAL",
            "Scotiabank Colpatria" to "CRÉDITO HIPOTECARIO",
        ).forEach { (empresa, descripcion) ->
            val p = assertNotNull(parseSms(textoDePse(empresa = empresa, descripcion = descripcion), origen))
            assertEquals(CUOTA_CATEGORY, p.category, descripcion)
            assertEquals(TransactionType.EXPENSE, p.type)
        }
    }

    @Test
    fun `el pago de una tarjeta se reconoce, y el pago minimo solo si es a un banco`() {
        val nu = assertNotNull(parseSms(textoDePse(empresa = "NU Compañía de Financiamiento", descripcion = "Pago tarjeta Nu"), origen))
        assertEquals(CARD_PAYMENT_CATEGORY, nu.category)
        val minimo = assertNotNull(parseSms(textoDePse(empresa = "Banco Davivienda S.A.", descripcion = "PAGO MINIMO"), origen))
        assertEquals(CARD_PAYMENT_CATEGORY, minimo.category)
        val colegio = assertNotNull(parseSms(textoDePse(empresa = "Colegio Ejemplo", descripcion = "PAGO MINIMO pension"), origen))
        assertEquals("Otros", colegio.category)
    }

    @Test
    fun `una cuota de administracion no es la cuota de un credito`() {
        val p = assertNotNull(parseSms(textoDePse(empresa = "Edificio Ejemplo PH", descripcion = "Cuota de administración octubre"), origen))
        assertEquals("Otros", p.category)
    }

    @Test
    fun `el deposito a tu cuenta Nu es un traspaso, no un gasto`() {
        val p = assertNotNull(parseSms(textoDePse(empresa = "NU Compañía de Financiamiento", descripcion = "Depósito a tu cuenta NU"), origen))
        assertEquals(TRANSFER_CATEGORY, p.category)
    }

    @Test
    fun `lo demas es un gasto con la categoria que diga el nombre`() {
        val p = assertNotNull(parseSms(textoDePse(empresa = "Colombia Telecomunicaciones S.A. E.S.P.", descripcion = "Colombia Telecomunicaciones SA - Recaudo"), origen))
        assertEquals("Colombia Telecomunicaciones", p.merchant)
        assertEquals("Colombia Telecomunicaciones SA - Recaudo", p.nota)
        assertEquals(TransactionType.EXPENSE, p.type)
    }

    @Test
    fun `el CUS se lee del correo y de nada mas`() {
        assertEquals("709591889", cusDelCorreoDePse(textoDePse(cus = "709591889")))
        assertNull(cusDelCorreoDePse("Bancolombia: Pagaste \$138,600.00 a Coomeva desde tu producto 3333. CUS 709591889"))
    }

    @Test
    fun `solo el cuerpo, sin asunto, tambien se lee`() {
        val p = assertNotNull(parseSms(limpiarCuerpoDelCorreo(cuerpoDePse()), origen))
        assertEquals(138_600.0, p.amount)
        assertEquals("Coomeva Medicina Prepagada", p.merchant)
    }

    @Test
    fun `el correo que solo trae HTML, con todo en un renglon, tambien se lee`() {
        val html = "<table><tr><td>Valor:</td><td>\$ 4.178.163,00</td></tr><tr><td>Empresa: Banco de Occidente (ATH)</td>" +
            "<td>Descripción: PAGO Banco de Occidente - Prestamo</td><td>Fecha de la transacción: 19/09/2026</td><td>CUS: 700000002</td></tr></table>"
        val texto = textoDelCorreo(asuntoDePse("700000002"), limpiarCuerpoDelCorreo(sinEtiquetas(html).replace("\n", " ")))
        val p = assertNotNull(parseSms(texto, origen))
        assertEquals(4_178_163.0, p.amount)
        assertEquals("Banco de Occidente", p.merchant)
        assertEquals("PAGO Banco de Occidente - Prestamo", p.nota)
        assertEquals("2026-09-19", p.fecha)
        assertEquals(CUOTA_CATEGORY, p.category)
    }

    @Test
    fun `una transaccion rechazada o pendiente no es un movimiento`() {
        assertNull(parseSms(textoDePse().replace("Aprobada", "Rechazada"), origen))
        assertNull(parseSms(textoDePse().replace("Aprobada", "Pendiente"), origen))
    }

    @Test
    fun `el correo de PSE no se aparta, aunque la descripcion diga pago minimo`() {
        assertNull(motivoParaApartar(textoDePse(empresa = "Banco Davivienda S.A.", descripcion = "PAGO MINIMO"), origen))
        assertNull(motivoParaApartar(textoDePse(), origen))
    }
}
