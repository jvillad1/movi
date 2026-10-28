package com.jvillada.movi.ui.dashboard

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **«¿De dónde sale?»: los renglones suman la cifra del héroe, al peso** (vuelta de diseño 28).
 *
 * La cascada no es una cuenta nueva: es [DisponibleDelPeriodo] puesto en renglones. Si un renglón
 * faltara o sobrara, la hoja diría una cosa y el héroe otra, que es justo lo que esta vuelta vino a
 * quitar. Por eso cada caso exige dos igualdades: lo de arriba suma «Disponible del período» (la cifra
 * de Plan) y todo junto suma «Te quedan» (la cifra del héroe, [teQuedanDeHoy]).
 */
class CascadaDelDisponibleTest {

    private val inicio = LocalDate(2026, 9, 25)
    private val finExclusivo = LocalDate(2026, 10, 25)
    private val hoy = LocalDate(2026, 10, 20)

    private fun pago(id: String, monto: Long, pagado: Boolean, dias: Int, montoPagado: Long? = null) = PagoDelPeriodo(
        ruleId = id, nombre = id, monto = monto, pagado = pagado, diasParaVencer = dias, montoPagado = montoPagado,
    )

    /** Arriendo vencido y sin pagar, Internet pagado por un poco más, y el colegio por vencer. */
    private val fijos = listOf(
        pago("Arriendo", 1_800_000, pagado = false, dias = -22),
        pago("Internet", 120_000, pagado = true, dias = -10, montoPagado = 125_000),
        pago("Colegio", 900_000, pagado = false, dias = 3),
    )

    private val gasto = mapOf("2026-09-30" to 400_000L, "2026-10-10" to 250_000L, "2026-10-20" to 37_500L)

    private fun disponible(plata: PlataDelDisponible?, ingresos: Long = 0L, checklist: List<PagoDelPeriodo> = fijos) =
        assertNotNull(disponibleDelPeriodo(ingresos, checklist, gasto, inicio, finExclusivo, hoy, plata))

    private fun sumaQueCuadra(d: DisponibleDelPeriodo): CascadaDelDisponible {
        val cascada = cascadaDelDisponible(d)
        assertEquals(d.disponible, cascada.deDondeSale.sumOf { it.monto }, "lo de arriba suma «Disponible del período»")
        assertEquals(d.disponible, cascada.disponible)
        val heroe = teQuedanDeHoy(d)
        assertEquals(heroe.teQuedan, cascada.total, "el total es la cifra del héroe")
        assertEquals(heroe.teQuedan, cascada.disponible + cascada.despues.sumOf { it.monto }, "todo junto suma «Te quedan»")
        assertEquals(heroe.rotulo, cascada.rotuloDelTotal)
        assertEquals(heroe.enRojo, cascada.enRojo)
        return cascada
    }

    private fun rotulos(c: CascadaDelDisponible) = c.deDondeSale.map { it.rotulo }

    @Test
    fun `sin ahorro, con fijos pendientes, suma lo del heroe`() {
        val d = disponible(PlataDelDisponible(saldoAlInicio = 2_000_000, entradas = 15_000_000, guardado = 0))
        val c = sumaQueCuadra(d)
        assertEquals(listOf("Tenías el 25", "Lo que entró", ROTULO_FIJOS_DEL_PERIODO), rotulos(c))
        // Los fijos son los pagados (por lo que salió) y los pendientes: el arriendo vencido incluido.
        assertEquals(-(1_800_000L + 125_000L + 900_000L), c.deDondeSale.last().monto)
        assertEquals(listOf(ROTULO_GASTASTE_EN_EL_PERIODO to -687_500L), c.despues.map { it.rotulo to it.monto })
        assertEquals(ROTULO_TE_QUEDAN, c.rotuloDelTotal)
        assertEquals(2_000_000L + 15_000_000L - 2_825_000L - 687_500L, c.total)
    }

    @Test
    fun `con el ahorro del periodo, el ahorro es un renglon y la suma sigue dando`() {
        val d = disponible(PlataDelDisponible(saldoAlInicio = 2_000_000, entradas = 15_000_000, guardado = 0, ahorroDelPeriodo = 3_000_000))
        val c = sumaQueCuadra(d)
        assertEquals(listOf("Tenías el 25", "Lo que entró", ROTULO_FIJOS_DEL_PERIODO, "Tu ahorro del período"), rotulos(c))
        assertEquals(-3_000_000L, c.deDondeSale.last().monto)
    }

    @Test
    fun `con un apartado, lo guardado, otros pagos de deuda y lo que falta por recibir`() {
        val sueldo = PagoDelPeriodo(ruleId = "Sueldo", nombre = "Sueldo", monto = 4_000_000, pagado = false, diasParaVencer = 3, esIngreso = true)
        val d = disponible(
            PlataDelDisponible(
                saldoAlInicio = 5_000_000, entradas = 9_000_000, guardado = 500_000,
                otrosPagosDeDeuda = 700_000, apartado = 1_200_000, ahorroDelPeriodo = 1_000_000,
            ),
            checklist = fijos + sueldo,
        )
        val c = sumaQueCuadra(d)
        assertEquals(
            listOf(
                "Tenías el 25", "Lo que entró", "Por recibir", "Guardaste en un ahorro", "Apartaste",
                "Otros pagos de deuda", ROTULO_FIJOS_DEL_PERIODO, "Tu ahorro del período",
            ),
            rotulos(c),
        )
        // Lo que suma lleva su «+»; lo que resta, su «−».
        assertTrue(c.deDondeSale.filter { it.suma }.map { it.rotulo }.containsAll(listOf("Lo que entró", "Por recibir")))
        assertTrue(c.deDondeSale.filter { !it.suma && it.rotulo != "Tenías el 25" }.all { it.monto <= 0L })
    }

    @Test
    fun `sin margen, el ajuste a cero hace que la suma de lo que dice el heroe`() {
        // $1M contra $2,8M de fijos: no hay margen, y el héroe dice «Te pasaste por» lo gastado.
        val d = disponible(PlataDelDisponible(saldoAlInicio = 1_000_000, entradas = 0, guardado = 0))
        val c = sumaQueCuadra(d)
        assertEquals(ROTULO_TE_PASASTE, c.rotuloDelTotal)
        assertEquals(-687_500L, c.total)
        val ajuste = c.despues.first()
        assertEquals(ROTULO_SIN_MARGEN, ajuste.rotulo)
        assertEquals(2_825_000L - 1_000_000L, ajuste.monto)
        assertTrue(ajuste.apagado)
    }

    @Test
    fun `con un server viejo, lo que entro, lo que falta por recibir y los fijos`() {
        val d = disponible(plata = null, ingresos = 10_000_000)
        val c = sumaQueCuadra(d)
        assertEquals(listOf("Lo que entró", ROTULO_FIJOS_DEL_PERIODO), rotulos(c))
    }
}
