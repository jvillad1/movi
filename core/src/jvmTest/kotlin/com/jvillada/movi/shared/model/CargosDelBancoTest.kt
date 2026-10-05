package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Los cargos y abonos del banco se reconocen por lo que el banco dice
 *
 * Los ejemplos son los de la tabla de la auditoría (26 movimientos que el dueño anotó a mano en cinco
 * semanas) y sus rótulos tal como los escribe el banco, recortes incluidos. Los negativos son los que
 * cuestan caro: equivocarse hacia «sí» le cambia la categoría a una compra de verdad.
 */
class CargosDelBancoTest {

    private val delDueno = setOf(
        "Comida", "Mercado", "Transporte", "Impuestos", "Comisiones del banco", "Ingreso", "Hija", "Fútbol",
    )

    private fun clase(texto: String, tipo: TransactionType? = null) = cargoDelBanco(texto, tipo)?.clase

    // ── La tabla de la auditoría ───────────────────────────────────────────────

    @Test
    fun `el 4x1000 y la retencion son impuestos, escritos como sea`() {
        listOf(
            "Gravamen a los movimientos financieros",
            "Gravamen a los movimientos financieros (4x1000)",
            "4x1000",
            "4x1000 del 27 al 30 de septiembre",
            "4x1000 del pago de la Tarjeta Nu",
            "GMF",
            "IMPTO GOBIERNO 4X1000",
            "IMPTO GOBIERNO 4 X 1000",
            "impuesto 4 x 1.000",
            "Retención en la fuente",
            "RETENCION EN LA FUENTE",
            "RETEFUENTE RENDIMIENTOS",
            "RET FTE",
        ).forEach { assertEquals(ClaseDeCargo.IMPUESTO, clase(it), it) }
    }

    @Test
    fun `la cuota de manejo, su IVA, las comisiones y los intereses que cobra son comisiones del banco`() {
        listOf(
            "Cuota de manejo cupo rotativo",
            "CUOTA MANEJO",
            "CUOTA MANEJO TARJETA DEBITO",
            "IVA cuota de manejo cupo rotativo",
            "IVA CUOTA MANEJO",
            "COMISION TRANSF OTRA ENTIDAD",
            "Comisión e IVA del traslado a otro banco (28 de septiembre)",
            "Comisión e IVA del envío a otro banco (Lavadero H2O)",
            "IVA COMIS TRASL OTRA ENT",
            "INTERESES CORRIENTES",
            "Intereses corrientes",
            "INT CORRIENTES",
            "Intereses y mora del Rappi de julio",
            "INTERESES DE MORA",
        ).forEach { assertEquals(ClaseDeCargo.COMISION, clase(it), it) }
    }

    @Test
    fun `los intereses de ahorros y los rendimientos son ingreso`() {
        listOf(
            "ABONO INTERESES AHORROS",
            "Abono intereses ahorros (15 al 19 de sept)",
            "Intereses de ahorros (28 de septiembre al 2 de octubre)",
            "Intereses de la AFC (30 de septiembre)",
            "Rendimientos Nu",
            "Rendimientos Nu (Cajita, 21 de septiembre al 4 de octubre)",
            "Rendimientos Fiducuenta (al 3 de octubre)",
            "Rendimientos Skandia (21 de septiembre al 4 de octubre)",
            "RENDIMIENTOS FINANCIEROS",
        ).forEach { assertEquals(ClaseDeCargo.RENDIMIENTO, clase(it), it) }
    }

    @Test
    fun `cada clase trae su tipo y la categoria del dueno`() {
        val impuesto = cargoDelBanco("IMPTO GOBIERNO 4X1000")!!
        assertEquals(TransactionType.EXPENSE, impuesto.tipo)
        assertEquals("Impuestos", categoriaDelCargo(impuesto, delDueno))
        val comision = cargoDelBanco("IVA CUOTA MANEJO")!!
        assertEquals(TransactionType.EXPENSE, comision.tipo)
        assertEquals("Comisiones del banco", categoriaDelCargo(comision, delDueno))
        val rendimiento = cargoDelBanco("ABONO INTERESES AHORROS")!!
        assertEquals(TransactionType.INCOME, rendimiento.tipo)
        assertEquals("Ingreso", categoriaDelCargo(rendimiento, delDueno))
    }

    @Test
    fun `con el tipo del banco, el rotulo pelado se entiende`() {
        assertEquals(ClaseDeCargo.RENDIMIENTO, clase("INTERESES", TransactionType.INCOME))
        assertEquals(ClaseDeCargo.COMISION, clase("INTERESES", TransactionType.EXPENSE))
        assertEquals(ClaseDeCargo.RENDIMIENTO, clase("RENDIMIENTOS", TransactionType.INCOME))
        assertNull(clase("INTERESES"), "sin el tipo, «Intereses» puede ser de los dos lados")
    }

    // ── Lo que NO es ───────────────────────────────────────────────────────────

    @Test
    fun `interes en un SMS de promocion no es un cargo`() {
        assertNull(clase("Bancolombia te ofrece 0% de interés en tus compras con tarjeta de crédito este fin de semana. Aplican condiciones"))
        assertNull(clase("Compra a 1 cuota sin interés"))
        // El aviso real de una ampliación de plazo (24-ago): habla de intereses y comisiones y no es ninguno.
        assertNull(
            clase(
                "Bancolombia confirma ampliacion de plazo por COP 26,807,543.00 en su TC MASTER *3684. La tasa es de 2.18%, " +
                    "el plazo de 36 meses y los intereses y comisiones causados a la fecha, se difieren a 3 meses a una tasa del 0%.",
            ),
        )
    }

    @Test
    fun `rendimientos como nombre de un comercio no es un rendimiento`() {
        assertNull(clase("RENDIMIENTOS"), "sin el tipo, el nombre pelado no alcanza")
        assertNull(clase("RENDIMIENTOS DEPORTIVOS"))
        assertNull(clase("Rendimientos", TransactionType.EXPENSE), "una compra en «Rendimientos» sale; un rendimiento entra")
        assertNull(clase("RENDIMIENTOS SAS", TransactionType.EXPENSE))
    }

    @Test
    fun `los comercios de verdad no son cargos`() {
        listOf(
            "Mora Soccer", "MORA SOCCER", "CARULLA RINCON OVIED", "PAGO COMISION ARRENDAMIENTO", "PAGO IMPUESTO PREDIAL",
            "Pago de tarjeta", "TIENDA LA CUOTA", "IVA", "BAR LA CUOTA", "Pago a Coomeva",
        ).forEach { assertNull(clase(it), it) }
    }

    @Test
    fun `lo que deshace un cargo no es un cargo`() {
        assertNull(clase("DEVOLUCION GMF"))
        assertNull(clase("REVERSION CUOTA MANEJO"))
        assertNull(clase("AJUSTE INTERESES"))
    }

    @Test
    fun `el tipo del banco que contradice la clase lo deja sin reconocer`() {
        assertNull(clase("IMPTO GOBIERNO 4X1000", TransactionType.INCOME))
        assertNull(clase("ABONO INTERESES AHORROS", TransactionType.EXPENSE))
        assertEquals(ClaseDeCargo.IMPUESTO, clase("IMPTO GOBIERNO 4X1000", TransactionType.EXPENSE))
    }

    // ── La categoría: solo si el dueño la tiene ────────────────────────────────

    @Test
    fun `si el dueno no tiene la categoria no se crea`() {
        val cargo = cargoDelBanco("IMPTO GOBIERNO 4X1000")!!
        assertNull(categoriaDelCargo(cargo, setOf("Comida", "Transporte")))
        assertNull(categoriaDelCargo(cargo, emptySet()), "sin saber sus categorías no hay catálogo al que caer")
        assertNull(categoriaProbablePorElNombre("IMPTO GOBIERNO 4X1000"))
        assertNull(categoriaProbablePorElNombre("CUOTA MANEJO", setOf("Comida")))
    }

    @Test
    fun `la categoria se devuelve como la escribe el dueno`() {
        val cargo = cargoDelBanco("CUOTA MANEJO")!!
        assertEquals("comisiones del banco", categoriaDelCargo(cargo, setOf("comisiones del banco")))
        assertEquals("Gastos bancarios", categoriaDelCargo(cargo, setOf("Gastos bancarios")))
    }

    @Test
    fun `categoriaProbablePorElNombre los reconoce en su propio bloque`() {
        assertEquals("Impuestos", categoriaProbablePorElNombre("IMPTO GOBIERNO 4X1000", delDueno))
        assertEquals("Comisiones del banco", categoriaProbablePorElNombre("IVA CUOTA MANEJO", delDueno))
        assertEquals("Comisiones del banco", categoriaProbablePorElNombre("COMISION TRANSF OTRA ENTIDAD", delDueno))
        assertEquals("Ingreso", categoriaProbablePorElNombre("ABONO INTERESES AHORROS", delDueno))
        // Y las palabras clave siguen igual.
        assertEquals("Mercado", categoriaProbablePorElNombre("CARULLA RINCON OVIED", setOf("Mercado")))
        assertNull(categoriaProbablePorElNombre("Mora Soccer", delDueno))
    }

    /**
     * **La memoria del dueño gana.** Quien llama mira la memoria antes que la red de seguridad: si él
     * anotó la cuota de manejo como «Tarjetas», eso es lo que se propone, no «Comisiones del banco».
     */
    @Test
    fun `la memoria del dueno gana sobre el cargo`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                AnotacionPasada("CUOTA MANEJO", "Cuota de manejo", "Tarjetas", 1L),
                AnotacionPasada("Almuerzo", "Almuerzo", "Comisiones del banco", 2L),
            ),
        )
        val propuesta = memoria.recuerdoDe("CUOTA MANEJO")?.categoria
            ?: categoriaProbablePorElNombre("CUOTA MANEJO", memoria.categoriasDelDueno)
        assertEquals("Tarjetas", propuesta)
        val sinRecuerdo = memoria.recuerdoDe("IVA CUOTA MANEJO")?.categoria
            ?: categoriaProbablePorElNombre("IVA CUOTA MANEJO", memoria.categoriasDelDueno)
        assertEquals("Comisiones del banco", sinRecuerdo)
    }
}
