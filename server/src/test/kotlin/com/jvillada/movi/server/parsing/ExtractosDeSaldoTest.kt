package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ContratoDelExtracto
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Skandia y la AFC de Davibank se leen sin IA
 *
 * Los textos tienen **la forma** del texto que PDFBox saca de los extractos reales del dueño, con
 * todo inventado: persona, dirección, números de contrato y de cuenta, cifras. Nada de los papeles
 * reales entra al repo.
 */
class ExtractosDeSaldoTest {

    /** La primera página del extracto de Skandia, como la saca PDFBox: el total en su propio renglón. */
    private val skandia = """
        Cualquier inconformidad con la información presentada, agradecemos comunicarla a la Revisoría Fiscal.
        1/11
        Resumen
        Productos Skandia
        PERSONA DE PRUEBA
        CALLE FALSA 123
        CIUDAD DE PRUEBA
        0000000000
        Agosto 2026
        000000 ---
        Producto Contrato No. Objetivo Inversión Perfil Inversión Saldo
        Productos Voluntarios
        Fondo Voluntario de Pensión Skandia
        Multifund
        $12,345,678.90
        Contratos Bonificaciones OMNI 100000001111 $10,000,000.00
        Contratos Bonificaciones OMNI 100000002222 $2,345,678.90
        NOTAS DE INTERÉS
        - El presente extracto de cuenta es informativo.
        Contrato: 100000002222
        Periodo: Agosto 01 de 2026 a Agosto 31 de 2026
        Saldo Inicial $1,000
        Rendimientos $2,000
        Fondo Voluntario de Pensión Skandia Multifund
        Página 2 de 11
        Saldos Actualizados a
        Agosto 31 de 2026
    """.trimIndent()

    /** El extracto de una cuenta de ahorros de Davibank, como lo saca PDFBox. */
    private val davibank = """
        ESTIMADO CLIENTE
        PERSONA DE PRUEBA
        CALLE FALSA 123
        Pag 2
        PERIODO
        1 AL 31 AGO 2026
        CUENTA DE AHORROS
        No 1234561111
        RESUMEN CUENTA AHORROS
        SALDO ANTERIOR DEPOSITOS Y OTROS CREDITOS RETIROS Y OTROS DEBITOS NUEVO SALDO
        1.000.000,00 500.000,50 200.000,25 1.300.000,25
        DETALLE DE CUENTA
        FECHA OFICINA No DOCUM DESCRIPCION MONTO SALDO
        4/08/2026
        31/08/2026
        CENTRAL DE C
        TRASLADO DE PRUEBA
        PAGO DE INTERESES
        500.000,00
        -200.000,25
        1.500.000,00
        1.300.000,25
        A través de nuestra página web www.davibank.com podrás consultar los teléfonos.
    """.trimIndent()

    // ── Skandia ─────────────────────────────────────────────────────────────

    @Test
    fun `Skandia - lee el mes del corte, el total y los contratos`() {
        val r = assertNotNull(leerElExtractoDeSkandia(skandia))
        assertEquals(ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO, r.de)
        assertEquals("2026-08-31", r.fechaDeCorte)
        assertEquals(12_345_679L, r.saldo, "centavos redondeados al peso")
        assertEquals(
            listOf(ContratoDelExtracto("1111", 10_000_000L), ContratoDelExtracto("2222", 2_345_679L)),
            r.contratos,
            "solo los últimos cuatro del contrato",
        )
        assertNull(r.numero)
        assertEquals("COP", r.moneda)
    }

    @Test
    fun `Skandia - con puntos de miles y coma decimal, y el total en el renglon del fondo`() {
        val conColumnas = """
            Productos Skandia
            Septiembre 2026
            Fondo Voluntario de Pensión Skandia | Multifund | $12.345.678,90
            Contratos Bonificaciones OMNI | 100000001111 | $10.000.000,00
            Contratos Bonificaciones OMNI | 100000002222 | $2.345.678,90
        """.trimIndent()
        val r = assertNotNull(leerElExtractoDeSkandia(conColumnas))
        assertEquals(12_345_679L, r.saldo)
        assertEquals("2026-09-30", r.fechaDeCorte)
        assertEquals(2, r.contratos.size)
    }

    @Test
    fun `Skandia - los contratos que no suman el total se callan, el total se lee igual`() {
        val r = assertNotNull(leerElExtractoDeSkandia(skandia.replace("$2,345,678.90", "$2,345,000.00")))
        assertEquals(12_345_679L, r.saldo)
        assertTrue(r.contratos.isEmpty())
    }

    @Test
    fun `Skandia - sin el mes del resumen, el corte sale de Saldos Actualizados`() {
        val r = assertNotNull(leerElExtractoDeSkandia(skandia.replace("Agosto 2026\n", "")))
        assertEquals("2026-08-31", r.fechaDeCorte)
    }

    @Test
    fun `Skandia - sin total no hay lectura`() {
        assertNull(leerElExtractoDeSkandia(skandia.replace("$12,345,678.90\n", "")))
    }

    @Test
    fun `Skandia - un texto que no es el suyo da null`() {
        assertNull(leerElExtractoDeSkandia(davibank))
        assertNull(leerElExtractoDeSkandia("Fecha Descripción Valor\n15/07 COMPRA EN TIENDA 45.900\n"))
        assertNull(leerElExtractoDeSkandia(""))
        // Nombrar a Skandia no alcanza: hace falta el resumen de productos.
        assertNull(leerElExtractoDeSkandia("Pago a Fondo Voluntario de Pensión Skandia $100.000"))
    }

    // ── Davibank ────────────────────────────────────────────────────────────

    @Test
    fun `Davibank - lee el corte, el nuevo saldo y el numero de la cuenta`() {
        val r = assertNotNull(leerElExtractoAfcDavibank(davibank))
        assertEquals(ExtractoDeSaldoDe.DAVIBANK_AHORROS, r.de)
        assertEquals("2026-08-31", r.fechaDeCorte)
        assertEquals(1_300_000L, r.saldo, "1.300.000,25 redondeado al peso")
        assertEquals("1111", r.numero)
        assertTrue(r.contratos.isEmpty())
    }

    @Test
    fun `Davibank - con comas de miles y punto decimal`() {
        val r = assertNotNull(
            leerElExtractoAfcDavibank(
                davibank.replace("1.000.000,00 500.000,50 200.000,25 1.300.000,25", "1,000,000.00 500,001.00 200,000.25 1,300,000.75"),
            ),
        )
        assertEquals(1_300_001L, r.saldo, "1,300,000.75 redondeado al peso")
    }

    @Test
    fun `Davibank - otro mes y debitos con signo`() {
        val r = assertNotNull(
            leerElExtractoAfcDavibank(
                davibank.replace("1 AL 31 AGO 2026", "1 AL 30 SEP 2026")
                    .replace("1.000.000,00 500.000,50 200.000,25 1.300.000,25", "1.000.000,00 500.000,50 -200.000,25 1.300.000,25"),
            ),
        )
        assertEquals("2026-09-30", r.fechaDeCorte)
        assertEquals(1_300_000L, r.saldo)
    }

    @Test
    fun `Davibank - un resumen que no cuadra no se lee`() {
        assertNull(leerElExtractoAfcDavibank(davibank.replace("1.000.000,00 500.000,50", "1.000.000,00 900.000,50")))
    }

    @Test
    fun `Davibank - un texto que no es el suyo da null`() {
        assertNull(leerElExtractoAfcDavibank(skandia))
        assertNull(leerElExtractoAfcDavibank(davibank.replace("www.davibank.com", "www.otrobanco.com")))
        assertNull(leerElExtractoAfcDavibank("Fecha Descripción Valor\n15/07 COMPRA EN TIENDA 45.900\n"))
        assertNull(leerElExtractoAfcDavibank(""))
    }

    // ── Los dos ─────────────────────────────────────────────────────────────

    @Test
    fun `leerUnExtractoDeSaldo reconoce a cada uno y a nada mas`() {
        assertEquals(ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO, leerUnExtractoDeSaldo(skandia)?.de)
        assertEquals(ExtractoDeSaldoDe.DAVIBANK_AHORROS, leerUnExtractoDeSaldo(davibank)?.de)
        assertNull(leerUnExtractoDeSaldo("OBLIGACIÓN Nº: 290100000\nABONO A CAPITAL 1.00 2.00\n"))
    }

    @Test
    fun `los montos se leen igual en los dos formatos`() {
        assertEquals(10_678_754_287L, centavosDelExtracto("106,787,542.87"))
        assertEquals(10_678_754_287L, centavosDelExtracto("106.787.542,87"))
        assertEquals(10_678_754_287L, centavosDelExtracto("$106,787,542.87"))
        assertEquals(-1_895_804L, centavosDelExtracto("-18.958,04"))
        assertEquals(1_183_639_200L, centavosDelExtracto("$11,836,392"))
    }
}
