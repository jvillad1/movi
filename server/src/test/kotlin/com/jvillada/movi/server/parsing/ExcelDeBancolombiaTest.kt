package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.routes.leerElExtracto
import com.jvillada.movi.shared.model.ParsedTransaction
import kotlinx.coroutines.runBlocking
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Vuelta 107 · los movimientos de Bancolombia en Excel se leen igual que el PDF equivalente. No hay un
 * Excel real en el repo: este es sintético, con los encabezados del PDF; uno con otros encabezados no se
 * lee (sigue el camino de siempre). Cifras y comercios inventados.
 */
class ExcelDeBancolombiaTest {

    /** El PDF equivalente, con la forma que saca PDFBox (ver `MovimientosDeBancolombiaTest`). */
    private val pdf = """
        Sucursal Virtual Personas
        Movimientos: Cuentas
        Cuenta de Ahorros
        Ahorros 012 - 345678 - 90
        Fecha Descripción Referencia Valor
        30 oct 2026 IMPTO GOBIERNO 4X1000 -$ 1.234,56
        29 oct 2026 PAGO QR LA COCINA DE 0091234567 -$ 59.800,00
        28 oct 2026 COMPRA EN  TIENDA LIBERTARIO -$ 8.100,00
        27 oct 2026 ABONO INTERESES AHORROS $ 15,58
        26 oct 2026 ABONO INTERESES AHORROS $ 0,15
        25 oct 2026 TRANSF DE PERSONA EJEMPLO null $ 1.000.000,00
    """.trimIndent()

    private data class Fila(val fecha: Any, val descripcion: String, val referencia: String, val valor: Any)

    private val filas = listOf(
        Fila(LocalDate.of(2026, 10, 30), "IMPTO GOBIERNO 4X1000", "", -1234.56),
        Fila("29 oct 2026", "PAGO QR LA COCINA DE", "0091234567", "-$ 59.800,00"),
        Fila("28/10/2026", "COMPRA EN  TIENDA LIBERTARIO", "", -8100.0),
        Fila(LocalDate.of(2026, 10, 27), "ABONO INTERESES AHORROS", "", 15.58),
        Fila("2026-10-26", "ABONO INTERESES AHORROS", "", 0.15),
        Fila(LocalDate.of(2026, 10, 25), "TRANSF DE PERSONA EJEMPLO", "null", 1_000_000.0),
    )

    private fun excel(
        libro: Workbook = XSSFWorkbook(),
        encabezados: List<String> = listOf("Fecha", "Descripción", "Referencia", "Valor"),
        conTitulo: Boolean = true,
    ): ByteArray = libro.use { wb ->
        val hoja = wb.createSheet("Movimientos")
        val formatoDeFecha = wb.createCellStyle().apply { dataFormat = wb.creationHelper.createDataFormat().getFormat("dd/mm/yyyy") }
        var r = 0
        if (conTitulo) {
            listOf("Sucursal Virtual Personas", "Movimientos: Cuentas", "Ahorros 012 - 345678 - 90").forEach {
                hoja.createRow(r++).createCell(0).setCellValue(it)
            }
            r++ // un renglón en blanco
        }
        hoja.createRow(r++).let { fila -> encabezados.forEachIndexed { c, t -> fila.createCell(c).setCellValue(t) } }
        filas.forEach { f ->
            val fila = hoja.createRow(r++)
            when (val fecha = f.fecha) {
                is LocalDate -> fila.createCell(0).apply { setCellValue(fecha); cellStyle = formatoDeFecha }
                else -> fila.createCell(0).setCellValue(fecha.toString())
            }
            fila.createCell(1).setCellValue(f.descripcion)
            if (f.referencia.isNotEmpty()) fila.createCell(2).setCellValue(f.referencia)
            when (val valor = f.valor) {
                is Double -> fila.createCell(3).setCellValue(valor)
                else -> fila.createCell(3).setCellValue(valor.toString())
            }
        }
        ByteArrayOutputStream().also { wb.write(it) }.toByteArray()
    }

    /** Lo que importa de una fila, sin el id (al azar) ni el texto crudo. */
    private fun lo(t: ParsedTransaction) = listOf(t.date, t.amount, t.currency, t.type, t.description, t.merchant, t.category, t.cuotas)

    @Test
    fun `un xlsx con los encabezados del PDF se lee igual que el PDF equivalente`() {
        val delPdf = assertNotNull(leerLosMovimientosDeBancolombia(pdf))
        val delExcel = assertNotNull(leerElExcelDeBancolombia(excel()))
        assertEquals(delPdf.numero, delExcel.numero)
        assertEquals("7890", delExcel.numero)
        // El abono de $0,15 no se propone en ninguno de los dos.
        assertEquals(5, delExcel.filas.size)
        assertEquals(delPdf.filas.map(::lo), delExcel.filas.map(::lo))
    }

    @Test
    fun `un xls viejo se lee igual`() {
        val delPdf = assertNotNull(leerLosMovimientosDeBancolombia(pdf))
        val delXls = assertNotNull(leerElExcelDeBancolombia(excel(HSSFWorkbook())))
        assertEquals(delPdf.filas.map(::lo), delXls.filas.map(::lo))
    }

    @Test
    fun `sin el titulo de arriba tambien se lee, sin numero de cuenta`() {
        val leido = assertNotNull(leerElExcelDeBancolombia(excel(conTitulo = false)))
        assertNull(leido.numero)
        assertEquals(5, leido.filas.size)
    }

    @Test
    fun `con otros encabezados no se lee - sigue el camino de siempre`() {
        assertNull(leerElExcelDeBancolombia(excel(encabezados = listOf("Fecha", "Concepto", "Referencia", "Valor"))))
        assertNull(leerElExcelDeBancolombia(excel(encabezados = listOf("Fecha", "Descripción", "Referencia", "Débito", "Crédito"))))
        assertNull(leerElExcelDeBancolombia("no es un excel".toByteArray()))
    }

    @Test
    fun `al subirlo, el extracto sale de Bancolombia sin pasar por la IA`() = runBlocking {
        val leido = leerElExtracto("usuario", "Movimientos.xlsx", "application/octet-stream", excel())
        assertEquals("Bancolombia", leido.bankName)
        assertEquals(5, leido.filas.size)
        assertEquals(listOf("7890"), leido.numerosDeCuenta)
    }
}
