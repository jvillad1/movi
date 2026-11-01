package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.montoYMonedaDelTexto
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.huellaDeUnMovimiento
import com.jvillada.movi.shared.model.marcaDelRemitente
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Vuelta 99 · los SMS de Davivienda del papá del dueño.** Los tres textos son los reales de su
 * teléfono (Samsung, APK 1.87), transcritos tal cual: coma de miles, sin espacio después de las comas,
 * «Hora» y «Lugar». Llegan de un código corto 871… que Movi no conoce.
 */
class ElSmsDeDaviviendaSeLeeTest {

    private val descuento =
        "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA."
    private val abono =
        "DAVIVIENDA Abono Transferencia ACH, \$419,745,Cta de Ahorros *0136,Hora 17:18,Lugar 0000000000000000000000000."
    private val retiro = "DAVIVIENDA . Retiro . \$320,000,Cta de Ahorros *0136,Hora 12:04,Lugar OVIEDO."
    private val codigoCorto = "87100"

    @Test
    fun `el descuento en internet es un gasto de 64900 en Mercado Pago`() {
        val p = assertNotNull(parseSms(descuento, codigoCorto))
        assertEquals(64_900.0, p.amount, "la coma es de miles: no 64,9")
        assertEquals("COP", p.currency)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("Mercado Pago", p.merchant)
        assertNull(p.identificadorDelDestino, "*0136 es la cuenta del dueño, no la de un tercero")
    }

    @Test
    fun `el abono por ACH es un ingreso de 419745 sin comercio de ceros`() {
        val p = assertNotNull(parseSms(abono, codigoCorto))
        assertEquals(419_745.0, p.amount)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("Transferencia recibida", p.merchant, "una corrida de ceros no es un comercio")
        assertEquals("Transferencia", p.category)
        assertNull(p.identificadorDelDestino)
    }

    /** Como un retiro de Bancolombia: un gasto que nombra el cajero (la huella `cajero:`). */
    @Test
    fun `el retiro es un gasto de 320000 en el cajero de OVIEDO`() {
        val p = assertNotNull(parseSms(retiro, codigoCorto))
        assertEquals(320_000.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("Retiro en cajero OVIEDO", p.merchant)
        assertEquals("cajero:oviedo", huellaDeUnMovimiento(p.merchant))
    }

    @Test
    fun `de cada aviso se leen la cuenta, la hora y el lugar`() {
        val d = assertNotNull(leerElAvisoDeDavivienda(descuento))
        assertEquals("Descuento en internet", d.descripcion)
        assertEquals("Cta de Ahorros", d.producto)
        assertEquals("0136", d.numero)
        assertEquals("16:49", d.hora)
        assertEquals("PSE MERCADOPAGO COLOMBIA", d.lugar)

        val a = assertNotNull(leerElAvisoDeDavivienda(abono))
        assertEquals("Abono Transferencia ACH", a.descripcion)
        assertEquals("0136", a.numero)
        assertEquals("17:18", a.hora)
        assertNull(a.lugar)

        val r = assertNotNull(leerElAvisoDeDavivienda(retiro))
        assertEquals("Retiro", r.descripcion)
        assertEquals("0136", r.numero)
        assertEquals("12:04", r.hora)
        assertEquals("OVIEDO", r.lugar)
    }

    @Test
    fun `el banco es Davivienda aunque el codigo corto no se conozca`() {
        for (texto in listOf(descuento, abono, retiro)) {
            assertEquals("Davivienda", marcaDelRemitente(codigoCorto, texto)?.nombre, texto)
        }
        // Y la regla de la primera palabra no inventa bancos: un comercio no es el remitente.
        assertNull(marcaDelRemitente(codigoCorto, "RAPPI te regala envío gratis hoy"))
        assertNull(marcaDelRemitente(codigoCorto, "Compraste \$10.000 en RAPPI"))
    }

    /** «Descuento» es una palabra de promoción: sin la forma de Davivienda, el gasto real se apartaba. */
    @Test
    fun `ninguno de los tres se aparta`() {
        for (texto in listOf(descuento, abono, retiro)) {
            assertNull(motivoParaApartar(texto, codigoCorto), texto)
        }
    }

    @Test
    fun `la clave dinamica y la promo de Davivienda si se apartan`() {
        assertEquals(
            MotivoDeApartado.CODIGO_DE_VERIFICACION,
            motivoParaApartar("DAVIVIENDA: Su clave dinamica es 482913. No la comparta con nadie.", codigoCorto),
        )
        assertEquals(
            MotivoDeApartado.CODIGO_DE_VERIFICACION,
            motivoParaApartar("Davivienda informa: 482913 es tu clave temporal para ingresar. No la compartas.", codigoCorto),
        )
        assertEquals(
            MotivoDeApartado.PROMOCION,
            motivoParaApartar("DAVIVIENDA te da 20% de descuento en tu proxima compra. Aplican TyC.", codigoCorto),
        )
        assertNull(leerElAvisoDeDavivienda("DAVIVIENDA: Su clave dinamica es 482913. No la comparta con nadie."))
    }

    /** La coma de miles de Davivienda no rompe los montos con decimales de los otros bancos. */
    @Test
    fun `los montos de los otros bancos se siguen leyendo igual`() {
        assertEquals(1_234.56 to "COP", montoYMonedaDelTexto("Compraste \$1.234,56 en TIENDA"))
        assertEquals(12.5 to "USD", montoYMonedaDelTexto("Compraste USD 12.50 en SERVICIO"))
        assertEquals(20_417.0 to "COP", montoYMonedaDelTexto("Pagaste \$20,417 en TIENDA"))
        assertEquals(3_500_000.0 to "COP", montoYMonedaDelTexto("Retiraste \$3,500,000.00 de tu cuenta"))
        assertEquals(64_900.0, assertNotNull(parseSms(descuento.replace("64,900", "64,900.00"))).amount)
    }

    /** Un SMS de Bancolombia que empieza por su nombre sigue por su camino de siempre. */
    @Test
    fun `Bancolombia no pasa por el lector de Davivienda`() {
        assertNull(leerElAvisoDeDavivienda("Bancolombia: Compraste \$43.210,00 en TIENDA con tu T.Deb *1111, el 01/10/2026 a las 10:15."))
        val p = assertNotNull(parseSms("Bancolombia: Retiraste \$200.000,00 en CAJERO DE PRUEBA de tu T.Deb **1111 el 15/09/2026 a las 16:20."))
        assertEquals(200_000.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
    }
}
