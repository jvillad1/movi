package com.jvillada.movi.server.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **El extracto de un crédito de Bancolombia, sin IA.** Las cifras y la forma son las de los
 * extractos reales del dueño (Libre inversión ·9695 y Libranza ·4818), sin sus datos personales;
 * la segunda variante es la misma página con las columnas en el mismo renglón, que es como la deja
 * un extractor que respeta la posición.
 */
class ExtractoDeCreditoBancolombiaTest {

    private val libranza4818 = """
        OBLIGACIÓN Nº:    290104818
        FECHA DE PAGO:    8/5/2026
        LÍNEA DE CRÉDITO LIBRANZA TASA FIJA
        CONCEPTO ABONO ANTERIOR CUOTA A PAGAR
        ABONO A CAPITAL .00 .00
        INTERÉS CORRIENTE .00 5,450,187.00
        INTERÉS MORA .00
        SEGURO VIDA .00 590,072.00
        OTROS CONCEPTOS .00 .00
        TOTAL .00 6,040,259.00
        262,386,162.00
        SALDO DE CRÉDITO
        FECHA CORTE EXTRACTO 7/27/2026
        SALDO DE CAPITAL 257,000,000.00
        TASA DE INTERÉS E.A. 18.01
    """.trimIndent()

    private val libreInversionConColumnas = """
        PERSONA DE PRUEBA                                            OBLIGACIÓN Nº:            290109695
                                                                     FECHA DE PAGO:              9/15/2026
                        LÍNEA DE CRÉDITO PREAPROBADO LIBRE INVERSION
        CONCEPTO                 ABONO ANTERIOR                            CUOTA A PAGAR          INFORMACIÓN DEL CRÉDITO
        ABONO A CAPITAL                             1,032,007.00                               452,697.00                FECHA DE DESEMBOLSO                                6/15/2021
        INTERÉS CORRIENTE                              136,857.00                              594,567.00                VALOR INICIAL                                   80,000,000.00
                                                                                                                         FECHA CORTE EXTRACTO                               8/20/2026
        INTERÉS MORA                                              .00
        SEGURO VIDA                                      35,200.00                             156,800.00
                                                                                                                         SALDO DE CAPITAL                                40,710,555.00
                                                                                                                         TASA DE INTERÉS E.A.                                    11.27
        TOTAL                                       1,204,064.00                            1,204,064.00                 SALDO EN MORA CAPITAL                                     .00
    """.trimIndent()

    @Test
    fun `la libranza con la cuota uno, toda interes y seguro`() {
        val r = assertNotNull(leerElExtractoDeCreditoDeBancolombia(libranza4818))
        assertEquals("290104818", r.numero)
        assertEquals(257_000_000L, r.saldoCapital, "el saldo de capital, no el «saldo de crédito»")
        assertEquals(5_450_187L, r.interesDelPeriodo, "la columna de la cuota que viene")
        assertEquals(590_072L, r.seguro)
        assertEquals(6_040_259L, r.cuota)
        assertEquals(18.01, r.tasaEa)
        assertEquals("2026-08-05", r.fechaDePago, "mes/día/año")
        assertEquals("2026-07-27", r.fechaDeCorte)
    }

    @Test
    fun `con las columnas en el mismo renglon se lee igual`() {
        val r = assertNotNull(leerElExtractoDeCreditoDeBancolombia(libreInversionConColumnas))
        assertEquals("290109695", r.numero)
        assertEquals(40_710_555L, r.saldoCapital)
        assertEquals(594_567L, r.interesDelPeriodo)
        assertEquals(156_800L, r.seguro)
        assertEquals(1_204_064L, r.cuota)
        assertEquals(11.27, r.tasaEa)
        assertEquals("2026-09-15", r.fechaDePago)
        assertEquals("2026-08-20", r.fechaDeCorte)
    }

    @Test
    fun `lo que no es el extracto de un credito no se lee`() {
        assertNull(leerElExtractoDeCreditoDeBancolombia("Fecha Descripción Valor\n15/07 COMPRA EN TIENDA 45.900\n"))
        assertNull(leerElExtractoDeCreditoDeBancolombia(""))
    }
}
