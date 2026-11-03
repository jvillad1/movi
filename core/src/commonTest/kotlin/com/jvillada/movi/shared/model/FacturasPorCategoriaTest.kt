package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 120 · la vista «Por categoría» de Pagos ([facturasPorCategoria]): cómo se agrupa, que la suma de los
 * «falta» de los grupos es la de la tarjeta amarilla, y el orden.
 */
class FacturasPorCategoriaTest {

    private fun fila(
        ruleId: String,
        nombre: String,
        monto: Long,
        categoria: String = "",
        pagado: Boolean = false,
        dias: Int = 5,
        montoEsSaldo: Boolean = false,
        pagoMinimo: Long? = null,
        restoDelMinimo: Long? = null,
        pagadoDesdeElCorte: Long = 0L,
        esIngreso: Boolean = false,
        haciaDeuda: String? = null,
        abonoATarjeta: Boolean = false,
        montoPagado: Long? = null,
        pagadoEnElPeriodo: Long? = null,
        moneda: String = "COP",
        minimoDelExtractoEnLaRegla: Boolean = false,
    ) = PagoDelPeriodo(
        ruleId = ruleId,
        nombre = nombre,
        monto = monto,
        pagado = pagado,
        diasParaVencer = dias,
        montoEsSaldo = montoEsSaldo,
        pagoMinimo = pagoMinimo,
        restoDelMinimo = restoDelMinimo,
        pagadoDesdeElCorte = pagadoDesdeElCorte,
        esIngreso = esIngreso,
        haciaDeuda = haciaDeuda,
        abonoATarjeta = abonoATarjeta,
        montoPagado = montoPagado,
        pagadoEnElPeriodo = pagadoEnElPeriodo,
        moneda = moneda,
        categoria = categoria,
        minimoDelExtractoEnLaRegla = minimoDelExtractoEnLaRegla,
    )

    /** Un período como el del dueño: de todo un poco, incluidos los casos raros de la tarjeta amarilla. */
    private val periodo = listOf(
        fila("r_arriendo", "Arriendo", 1_800_000, categoria = "Vivienda", dias = -2),
        fila("r_admin", "Administración", 450_000, categoria = "vivienda ", pagado = true, montoPagado = 450_000),
        fila("r_luz", "Luz", 120_000, categoria = "Servicios", dias = 10),
        fila("r_agua", "Agua", 80_000, categoria = "Servicios", pagado = true),
        fila("r_gym", "Gimnasio", 150_000, categoria = "", dias = 3),
        // La cuota de un crédito, con una categoría que no dice «crédito»: va a Créditos igual.
        fila("${CREDIT_RULE_PREFIX}veh", "Cuota Vehículo 4083", 980_000, categoria = "Transporte", dias = 1),
        fila("${CREDIT_RULE_PREFIX}hip", "Cuota Hipotecario", 2_100_000, categoria = CUOTA_CATEGORY, pagado = true, montoPagado = 2_099_000),
        // Una regla vieja escrita a mano en «Cuota de crédito».
        fila("r_moto", "Cuota moto", 300_000, categoria = CUOTA_CATEGORY, dias = 8),
        // El abono planeado a un crédito: con su deuda, sin sumar.
        fila("r_abono_veh", "Abono extra a Vehículo 4083", 500_000, categoria = "Traspaso", haciaDeuda = "acc_veh"),
        // Tarjetas: con el mínimo de su extracto a medio pagar, sin mínimo (no suma) y pagada por debajo sin extracto.
        fila(
            "${CARD_RULE_PREFIX}nu", "Pago tarjeta Nu", 2_400_000, montoEsSaldo = true, pagoMinimo = 180_000,
            restoDelMinimo = 80_000, pagadoDesdeElCorte = 100_000, minimoDelExtractoEnLaRegla = true,
        ),
        fila("${CARD_RULE_PREFIX}master", "Pago tarjeta Master", 5_000_000, montoEsSaldo = true),
        fila(
            "${CARD_RULE_PREFIX}amex", "Pago tarjeta Amex", 900_000, montoEsSaldo = true, pagoMinimo = 120_000,
            pagado = true, pagadoEnElPeriodo = 50_000,
        ),
        fila("${CARD_RULE_PREFIX}usd", "Pago tarjeta USD", 1_200, montoEsSaldo = true, moneda = "USD", pagado = true, montoPagado = 300),
        fila("r_abono_nu", "Abono a Nu", 200_000, categoria = "Traspaso", haciaDeuda = "acc_nu", abonoATarjeta = true),
        // En «Pago de tarjeta» a mano: también es de Tarjetas.
        fila("r_tarjeta_papa", "Tarjeta del papá", 250_000, categoria = CARD_PAYMENT_CATEGORY, dias = 4),
        // El sueldo no entra a ningún grupo.
        fila("r_salario", "Salario", 6_000_000, categoria = "Salario", esIngreso = true),
    )

    @Test
    fun `las cuotas van a Creditos y los pagos de tarjeta a Tarjetas, aunque su categoria diga otra cosa`() {
        val grupos = facturasPorCategoria(periodo).associateBy { it.nombre }

        assertEquals(
            setOf("${CREDIT_RULE_PREFIX}veh", "${CREDIT_RULE_PREFIX}hip", "r_moto", "r_abono_veh"),
            grupos.getValue(GRUPO_DE_LOS_CREDITOS).filas.map { it.ruleId }.toSet(),
        )
        assertEquals(
            setOf("${CARD_RULE_PREFIX}nu", "${CARD_RULE_PREFIX}master", "${CARD_RULE_PREFIX}amex", "${CARD_RULE_PREFIX}usd", "r_abono_nu", "r_tarjeta_papa"),
            grupos.getValue(GRUPO_DE_LAS_TARJETAS).filas.map { it.ruleId }.toSet(),
        )
        assertEquals(ClaseDeGrupoDeFacturas.CREDITOS, grupos.getValue(GRUPO_DE_LOS_CREDITOS).clase)
        assertEquals(ClaseDeGrupoDeFacturas.TARJETAS, grupos.getValue(GRUPO_DE_LAS_TARJETAS).clase)
        assertFalse("Transporte" in grupos, "la cuota del vehículo no arma un grupo «Transporte»")
        assertFalse("Traspaso" in grupos, "un abono va con su deuda, no en «Traspaso»")
    }

    @Test
    fun `el abono planeado va con su deuda y no suma en lo que falta del grupo`() {
        val creditos = facturasPorCategoria(periodo).single { it.clase == ClaseDeGrupoDeFacturas.CREDITOS }
        assertTrue(creditos.filas.any { it.esAbono })
        assertEquals(faltaPorPagar(creditos.filas.filter { !it.esAbono }), creditos.falta)
        assertEquals(980_000L + 300_000L, creditos.falta, "la cuota del vehículo y la de la moto; el abono, no")
    }

    @Test
    fun `los ingresos no entran a ningun grupo`() {
        val grupos = facturasPorCategoria(periodo)
        assertTrue(grupos.flatMap { it.filas }.none { it.esIngreso })
        assertFalse(grupos.any { it.nombre == "Salario" })
        assertEquals(periodo.count { !it.esIngreso }, grupos.sumOf { it.filas.size }, "cada pago, en un solo grupo")
    }

    @Test
    fun `la suma de lo que falta de los grupos es Falta por pagar, y la de lo pagado es Llevas X pagados`() {
        val grupos = facturasPorCategoria(periodo)
        assertEquals(faltaPorPagar(periodo), grupos.sumOf { it.falta })
        assertEquals(yaPagadoEnElPeriodo(periodo), grupos.sumOf { it.pagado })
        // Y la cifra no es trivial: lleva el resto del mínimo de Nu y lo que falta del mínimo de Amex.
        assertEquals(1_800_000L + 120_000L + 150_000L + 980_000L + 300_000L + 80_000L + 70_000L + 250_000L, faltaPorPagar(periodo))
    }

    @Test
    fun `la suma tambien cuadra con todo pagado, con nada y con una sola fila`() {
        val todoPagado = periodo.map { it.copy(pagado = true, restoDelMinimo = it.restoDelMinimo?.let { 0L }) }
        for (lista in listOf(todoPagado, emptyList(), periodo.take(1), periodo.filter { it.montoEsSaldo })) {
            val grupos = facturasPorCategoria(lista)
            assertEquals(faltaPorPagar(lista), grupos.sumOf { it.falta })
            assertEquals(yaPagadoEnElPeriodo(lista), grupos.sumOf { it.pagado })
        }
    }

    @Test
    fun `los grupos van por total, de mayor a menor`() {
        val grupos = facturasPorCategoria(periodo)
        val totales = grupos.map { it.total }
        assertEquals(totales.sortedDescending(), totales)
        assertEquals(listOf(GRUPO_DE_LOS_CREDITOS, "Vivienda", GRUPO_DE_LAS_TARJETAS, "Servicios", GRUPO_SIN_CATEGORIA), grupos.map { it.nombre })
        assertEquals(2_099_000L + 980_000L + 300_000L, grupos.first().total, "lo pagado cuenta con lo que de verdad salió")
    }

    @Test
    fun `a igual total, por nombre`() {
        val grupos = facturasPorCategoria(
            listOf(
                fila("a", "Netflix", 40_000, categoria = "Suscripciones"),
                fila("b", "Gas", 40_000, categoria = "Hogar"),
            ),
        )
        assertEquals(listOf("Hogar", "Suscripciones"), grupos.map { it.nombre })
    }

    @Test
    fun `la misma categoria con otras mayusculas, tildes o espacios es un grupo, y sin categoria es Otros`() {
        val grupos = facturasPorCategoria(
            listOf(
                fila("a", "Luz", 100_000, categoria = "Energía"),
                fila("b", "Planta", 50_000, categoria = " energia"),
                fila("c", "Gimnasio", 70_000, categoria = "  "),
                fila("d", "Clases", 30_000, categoria = "Otros"),
            ),
        )
        assertEquals(listOf("Energía", GRUPO_SIN_CATEGORIA), grupos.map { it.nombre })
        assertEquals(listOf("a", "b"), grupos[0].filas.map { it.ruleId })
        assertEquals(listOf("c", "d"), grupos[1].filas.map { it.ruleId })
        assertEquals("categoria:energia", grupos[0].clave)
    }

    @Test
    fun `adentro de cada grupo las filas quedan en el orden en que llegaron`() {
        val vivienda = facturasPorCategoria(periodo).single { it.nombre == "Vivienda" }
        assertEquals(listOf("r_arriendo", "r_admin"), vivienda.filas.map { it.ruleId })
    }

    @Test
    fun `un grupo con todo pagado lo dice, y uno con algo abierto tambien`() {
        val grupos = facturasPorCategoria(periodo).associateBy { it.nombre }
        val pagado = facturasPorCategoria(listOf(fila("a", "Agua", 80_000, categoria = "Servicios", pagado = true))).single()
        assertTrue(pagado.todoPagado)
        assertFalse(pagado.tieneAlgoPendiente)
        assertEquals(0L, pagado.falta)
        assertEquals(80_000L, pagado.total)

        assertFalse(grupos.getValue("Servicios").todoPagado)
        assertTrue(grupos.getValue("Servicios").tieneAlgoPendiente)
        // Una tarjeta sin mínimo no suma, pero sigue pendiente: el grupo no está «pagado».
        val sinMinimo = facturasPorCategoria(listOf(fila("${CARD_RULE_PREFIX}m", "Pago tarjeta Master", 5_000_000, montoEsSaldo = true))).single()
        assertEquals(0L, sinMinimo.falta)
        assertFalse(sinMinimo.todoPagado)
        // Solo un abono abierto: los pagos están hechos, pero el grupo arranca desplegado.
        val soloAbono = facturasPorCategoria(listOf(fila("r", "Abono", 500_000, haciaDeuda = "acc"))).single()
        assertTrue(soloAbono.todoPagado)
        assertTrue(soloAbono.tieneAlgoPendiente)
    }

    @Test
    fun `un abono a una tarjeta de la lista va a Tarjetas sin esperar la lectura de las tarjetas`() {
        val lista = listOf(
            fila("${CARD_RULE_PREFIX}acc_nu", "Pago tarjeta Nu", 900_000, montoEsSaldo = true, pagoMinimo = 90_000),
            fila("r_abono_nu", "Abono a Nu", 200_000, categoria = "Traspaso", haciaDeuda = "acc_nu"),
            fila("r_abono_veh", "Abono al carro", 500_000, categoria = "Traspaso", haciaDeuda = "acc_veh"),
            fila("r_luz", "Luz", 120_000, categoria = "Servicios"),
        )
        assertEquals(faltaPorPagar(lista), facturasPorCategoria(lista).sumOf { it.falta }, "la suma de los grupos es «Falta por pagar»")
        val grupos = facturasPorCategoria(lista).associateBy { it.nombre }
        assertEquals(listOf("${CARD_RULE_PREFIX}acc_nu", "r_abono_nu"), grupos.getValue(GRUPO_DE_LAS_TARJETAS).filas.map { it.ruleId })
        assertEquals(listOf("r_abono_veh"), grupos.getValue(GRUPO_DE_LOS_CREDITOS).filas.map { it.ruleId })
    }

    @Test
    fun `las categorias de deuda se comparan normalizadas, y una categoria llamada Creditos o Tarjetas no arma otro grupo`() {
        val lista = listOf(
            fila("a", "Cuota moto", 300_000, categoria = "cuota de credito "),
            fila("b", "Tarjeta del papá", 250_000, categoria = "PAGO DE TARJETA"),
            fila("c", "Seguro de la tarjeta", 20_000, categoria = "tarjetas"),
            fila("d", "Crédito Icetex", 400_000, categoria = "Créditos", pagado = true, montoPagado = 400_000),
            fila("${CREDIT_RULE_PREFIX}veh", "Cuota Vehículo", 980_000),
        )
        val grupos = facturasPorCategoria(lista)
        assertEquals(faltaPorPagar(lista), grupos.sumOf { it.falta }, "la suma de los grupos es «Falta por pagar»")
        assertEquals(listOf(GRUPO_DE_LOS_CREDITOS, GRUPO_DE_LAS_TARJETAS), grupos.map { it.nombre })
        assertEquals(setOf("a", "d", "${CREDIT_RULE_PREFIX}veh"), grupos[0].filas.map { it.ruleId }.toSet())
        assertEquals(setOf("b", "c"), grupos[1].filas.map { it.ruleId }.toSet())
    }
}
