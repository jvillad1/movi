package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Los cargos del banco en un extracto, y el duplicado sumado
 *
 * El caso real: el dueño anotó «4x1000 del 27 al 30 de septiembre» por $17.819 en Bancolombia
 * Ahorros, y el extracto trae cuatro líneas diarias. Si suman exacto, ya están anotadas; si no, se
 * proponen con un aviso. Nada se calcula: se suman las cifras del banco y se comparan con la del dueño.
 */
class CargosDelExtractoTest {

    private val ahorros = "acc_ahorros"
    private val nu = "acc_nu"

    private fun momento(dia: LocalDate): Long =
        dia.atTime(LocalTime(12, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun evento(
        id: String,
        nombre: String,
        monto: Long,
        dia: LocalDate,
        categoria: String = "Impuestos",
        tipo: TransactionType = TransactionType.EXPENSE,
        cuenta: String = ahorros,
        comercio: String? = null,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria, description = nombre,
        merchant = comercio, timestamp = momento(dia),
    )

    private fun fila(
        id: String,
        rotulo: String,
        monto: Long,
        fecha: String,
        tipo: TransactionType = TransactionType.EXPENSE,
        categoria: String = "Otros",
    ) = ParsedTransaction(
        id = id, date = fecha, merchant = rotulo, amount = monto, type = tipo, category = categoria,
        description = "", rawText = "",
    )

    private val memoria = MemoriaDeCategorias.de(
        listOf("Impuestos", "Comisiones del banco", "Ingreso", "Comida").mapIndexed { i, c ->
            AnotacionPasada("algo $c", "algo $c", c, i.toLong())
        },
    )

    /** Las cuatro líneas diarias del 4x1000 del 27 al 30 de septiembre: $17.819 en total. */
    private val cuatroPorMil = listOf(
        fila("f27", "IMPTO GOBIERNO 4X1000", 4_000, "2026-09-27"),
        fila("f28", "IMPTO GOBIERNO 4X1000", 5_000, "2026-09-28"),
        fila("f29", "IMPTO GOBIERNO 4X1000", 4_819, "2026-09-29"),
        fila("f30", "IMPTO GOBIERNO 4X1000", 4_000, "2026-09-30"),
    )

    private val anotadoSumado = evento("ev_4x1000", "4x1000 del 27 al 30 de septiembre", 17_819, LocalDate(2026, 9, 30))

    // ── La categoría y el tipo ─────────────────────────────────────────────────

    @Test
    fun `las filas de cargos entran con la categoria del dueno y las demas no se tocan`() {
        val compra = fila("c1", "CARULLA RINCON OVIED", 50_000, "2026-09-28", categoria = "Comida")
        val iva = fila("i1", "IVA CUOTA MANEJO", 4_112, "2026-09-16")
        val intereses = fila("a1", "ABONO INTERESES AHORROS", 13, "2026-10-02", tipo = TransactionType.INCOME, categoria = "Transferencia")
        val (filas, cargos) = cargosDelExtracto(listOf(compra, iva, intereses), emptyList(), memoria)

        assertEquals(listOf("Comida", "Comisiones del banco", "Ingreso"), filas.map { it.category })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.EXPENSE, TransactionType.INCOME), filas.map { it.type })
        assertEquals(listOf("i1" to ClaseDeCargo.COMISION, "a1" to ClaseDeCargo.RENDIMIENTO), cargos.map { it.parsedId to it.clase })
    }

    @Test
    fun `si el dueno no tiene la categoria la fila se queda con la que traia, pero va al bloque`() {
        val sinCategorias = MemoriaDeCategorias.de(listOf(AnotacionPasada("Carulla", "Carulla", "Comida", 1L)))
        val (filas, cargos) = cargosDelExtracto(listOf(fila("i1", "IVA CUOTA MANEJO", 4_112, "2026-09-16")), emptyList(), sinCategorias)
        assertEquals("Otros", filas.single().category)
        assertEquals(ClaseDeCargo.COMISION, cargos.single().clase)
    }

    @Test
    fun `la memoria del dueno gana sobre la categoria del cargo`() {
        val conMemoria = MemoriaDeCategorias.de(
            listOf(
                AnotacionPasada("CUOTA MANEJO", "Cuota de manejo", "Tarjetas", 1L),
                AnotacionPasada("algo", "algo", "Comisiones del banco", 2L),
            ),
        )
        val (filas, _) = cargosDelExtracto(listOf(fila("m1", "CUOTA MANEJO", 21_640, "2026-09-16")), emptyList(), conMemoria)
        assertEquals("Tarjetas", filas.single().category)
    }

    @Test
    fun `la memoria no le cambia el sentido a un cargo`() {
        // «Intereses de ahorros (…)» es Ingreso; unos «INTERESES» que salen de la tarjeta se parecen por
        // el prefijo, pero son lo contrario.
        val conMemoria = MemoriaDeCategorias.de(
            listOf(
                AnotacionPasada("Intereses de ahorros (28 de septiembre al 2 de octubre)", "Intereses de ahorros", "Ingreso", 1L),
                AnotacionPasada("algo", "algo", "Comisiones del banco", 2L),
            ),
        )
        val (filas, _) = cargosDelExtracto(listOf(fila("t1", "INTERESES", 1_999, "2026-09-11")), emptyList(), conMemoria)
        assertEquals("Comisiones del banco", filas.single().category)
    }

    // ── Ya anotado, sumado ─────────────────────────────────────────────────────

    @Test
    fun `cuatro lineas que suman exacto lo anotado a mano estan ya anotadas`() {
        val (_, cargos) = cargosDelExtracto(cuatroPorMil, listOf(anotadoSumado), memoria)
        assertEquals(4, cargos.size)
        cargos.forEach { cargo ->
            val estado = estadoDelCargo(cargo, ahorros)
            assertIs<EstadoDelCargo.YaAnotado>(estado, cargo.parsedId)
            assertEquals("ev_4x1000", estado.en.eventoId)
            assertEquals("4x1000 del 27 al 30 de septiembre", estado.en.nombre)
        }
    }

    @Test
    fun `si la suma no coincide exacto se proponen con el aviso`() {
        val distinto = anotadoSumado.copy(amount = 17_800)
        val (_, cargos) = cargosDelExtracto(cuatroPorMil, listOf(distinto), memoria)
        cargos.forEach { assertIs<EstadoDelCargo.PuedeEstarSumado>(estadoDelCargo(it, ahorros), it.parsedId) }
    }

    @Test
    fun `lo anotado en otra cuenta no cuenta, y sin cuenta elegida nada esta anotado`() {
        val (_, cargos) = cargosDelExtracto(cuatroPorMil, listOf(anotadoSumado), memoria)
        cargos.forEach {
            assertEquals(EstadoDelCargo.Nuevo, estadoDelCargo(it, nu))
            assertEquals(EstadoDelCargo.Nuevo, estadoDelCargo(it, null))
        }
    }

    @Test
    fun `las lineas fuera del rango, o de otra categoria, no cuentan para la suma`() {
        val delPrimero = fila("f01", "IMPTO GOBIERNO 4X1000", 700, "2026-10-01")
        val iva = fila("i29", "IVA CUOTA MANEJO", 4_112, "2026-09-29")
        val (_, cargos) = cargosDelExtracto(cuatroPorMil + delPrimero + iva, listOf(anotadoSumado), memoria)
        val porId = cargos.associateBy { it.parsedId }
        assertIs<EstadoDelCargo.YaAnotado>(estadoDelCargo(porId.getValue("f29"), ahorros))
        assertEquals(EstadoDelCargo.Nuevo, estadoDelCargo(porId.getValue("f01"), ahorros))
        assertEquals(EstadoDelCargo.Nuevo, estadoDelCargo(porId.getValue("i29"), ahorros))
    }

    @Test
    fun `sin rango en el nombre cubre su dia`() {
        val comision = fila("k1", "COMISION TRANSF OTRA ENTIDAD", 7_590, "2026-09-28")
        val ivaComision = fila("k2", "IVA COMIS TRASL OTRA ENT", 1_442, "2026-09-28")
        val anotado = evento(
            "ev_com", "Comisión e IVA del traslado a otro banco (28 de septiembre)", 9_032, LocalDate(2026, 9, 28),
            categoria = "Comisiones del banco",
        )
        val (_, cargos) = cargosDelExtracto(listOf(comision, ivaComision), listOf(anotado), memoria)
        cargos.forEach { assertIs<EstadoDelCargo.YaAnotado>(estadoDelCargo(it, ahorros), it.parsedId) }
    }

    @Test
    fun `los dias que cubre un movimiento salen de lo que escribio el dueno`() {
        fun dias(nombre: String, dia: LocalDate) = diasQueCubre(evento("e", nombre, 1, dia))
        assertEquals(LocalDate(2026, 9, 27)..LocalDate(2026, 9, 30), dias("4x1000 del 27 al 30 de septiembre", LocalDate(2026, 9, 30)))
        assertEquals(LocalDate(2026, 9, 15)..LocalDate(2026, 9, 19), dias("Abono intereses ahorros (15 al 19 de sept)", LocalDate(2026, 9, 19)))
        assertEquals(
            LocalDate(2026, 9, 21)..LocalDate(2026, 10, 4),
            dias("Rendimientos Nu (Cajita, 21 de septiembre al 4 de octubre)", LocalDate(2026, 10, 4)),
        )
        assertEquals(
            LocalDate(2025, 12, 28)..LocalDate(2026, 1, 2),
            dias("Intereses de ahorros (28 de diciembre al 2 de enero)", LocalDate(2026, 1, 2)),
        )
        assertEquals(LocalDate(2026, 10, 3)..LocalDate(2026, 10, 3), dias("Rendimientos Fiducuenta (al 3 de octubre)", LocalDate(2026, 10, 3)))
        assertEquals(LocalDate(2026, 9, 10)..LocalDate(2026, 9, 10), dias("Retención en la fuente", LocalDate(2026, 9, 10)))
    }

    @Test
    fun `la fecha de la fila se lee en los formatos del extracto`() {
        assertEquals(LocalDate(2026, 9, 27), fechaDeLaFila("2026-09-27"))
        assertEquals(LocalDate(2026, 9, 7), fechaDeLaFila("2026-9-7"))
        assertEquals(LocalDate(2026, 9, 27), fechaDeLaFila("27/09/2026"))
        assertEquals(null, fechaDeLaFila("ayer"))
    }

    @Test
    fun `una fila que no es cargo no va al bloque`() {
        val (_, cargos) = cargosDelExtracto(
            listOf(fila("x", "Mora Soccer", 80_000, "2026-09-28"), fila("y", "RENDIMIENTOS", 30_000, "2026-09-28")),
            emptyList(), memoria,
        )
        assertTrue(cargos.isEmpty())
    }
}
