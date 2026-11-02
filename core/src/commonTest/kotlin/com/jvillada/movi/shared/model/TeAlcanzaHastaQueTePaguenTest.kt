package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 108 · «¿Te alcanza hasta que te paguen?»
 *
 * El caso real del 2-nov-2026 (`diagnostico-108.md`), al peso: período del 23-oct al 24-nov, salario el
 * 25-nov, libre para gastar $1.362.010, gastado en el día a día $1.950.929, $10.315.703 en pagos que faltan.
 */
class TeAlcanzaHastaQueTePaguenTest {

    private val hoy = LocalDate(2026, 11, 2)

    private fun pago(nombre: String, monto: Long, vence: String, pagado: Boolean = false, esIngreso: Boolean = false) = PagoDelPeriodo(
        ruleId = nombre, nombre = nombre, monto = monto, pagado = pagado, diasParaVencer = 0, vence = vence, esIngreso = esIngreso,
    )

    /** Los pagos del período del dueño: ocho ya pagados, nueve que faltan y la recarga de Glim por recibir. */
    private val checklist = listOf(
        pago("Colegio", 4_000_000L, "2026-10-25", pagado = true),
        pago("Mercado", 2_000_000L, "2026-10-25", pagado = true),
        pago("Hipoteca Papá", 2_228_152L, "2026-10-27", pagado = true),
        pago("Libranza Papá", 2_051_848L, "2026-10-27", pagado = true),
        pago("Mamá", 1_300_000L, "2026-10-25", pagado = true),
        pago("Techo", 419_745L, "2026-10-30", pagado = true),
        pago("Coomeva", 138_600L, "2026-10-28", pagado = true),
        pago("Tía Caro", 100_000L, "2026-10-28", pagado = true),
        pago("Gimnasio Caro", 180_000L, "2026-11-04"),
        pago("Gimnasio Cami", 180_000L, "2026-11-05"),
        pago("Cuota Hipotecario 2334", 2_613_714L, "2026-11-07"),
        pago("Cuota Libre inversión 9695", 1_204_064L, "2026-11-15"),
        pago("Cuota Crediágil 3090", 26_485L, "2026-11-15"),
        pago("Cuota de manejo", 25_752L, "2026-11-16"),
        pago("Cuota Vehículo 8761", 4_101_123L, "2026-11-17"),
        pago("Cuota Cotrafa 5413", 1_931_488L, "2026-11-22"),
        pago("Celular", 53_077L, "2026-11-22"),
        pago("Recarga Glim", 640_000L, "2026-11-02", esIngreso = true),
    )

    private fun disponible(gastado: Long = 1_950_929L, ahorro: Long = 0L) = assertNotNull(
        disponibleDelPeriodo(
            ingresosRecibidos = 20_033_661L,
            checklist = checklist,
            gastoVariablePorDia = mapOf("2026-10-28" to gastado),
            inicio = LocalDate(2026, 10, 23),
            finExclusivo = LocalDate(2026, 11, 25),
            hoy = hoy,
            plata = PlataDelDisponible(
                saldoAlInicio = 9_718_476L,
                entradas = 20_041_277L,
                guardado = 0L,
                otrosPagosDeDeuda = 2_483_695L,
                apartado = 4_000_000L,
                ahorroDelPeriodo = ahorro,
            ),
        ),
    )

    /**
     * Los dos supuestos del caso real (`ElGastoDeUnDiaNormalTest`): la caja, $90.688 en efectivo; el héroe,
     * $143.154 con las compras con tarjeta.
     */
    private val gastoNormal = GastoDelDiaADia(porDia = 90_688L, estimado = 90_688L, estimadoConTarjetas = 143_154L, porDiaConTarjetas = 143_154L)

    @Test
    fun `el caso real dice te faltan 588919 con su porque, y la proyeccion con su supuesto`() {
        val d = disponible()
        assertEquals(1_362_010L, d.disponible, "libre para gastar, al peso")
        val r = teAlcanzaHastaQueTePaguen(d, checklist, gastoNormal, hoy, conElPeriodoElegido = true)
        assertEquals("Hasta que te paguen el 25 nov", r.pregunta)
        assertEquals(ROTULO_TE_FALTAN, r.rotulo)
        assertEquals(-588_919L, r.cifra)
        assertEquals(588_919L, r.monto)
        assertTrue(r.enRojo)
        assertEquals(10_315_703L, r.pagosQueFaltan)
        assertEquals(9_726_784L, r.libres, "Tu plata $9.086.784 + la recarga de $640.000")
        assertEquals("Faltan $10,3M en pagos y tienes $9,7M libres, contando lo que te entra", r.explicacion)

        val p = assertNotNull(r.proyeccion)
        assertEquals(22, p.dias, "del 3 al 24 de noviembre")
        assertEquals(-3_738_307L, p.alFinal, "−588.919 − 22 × 143.154: el supuesto del héroe, con tarjetas")
        assertEquals("Con tu gasto de un día normal, el 24 nov te faltarían unos $3,7M", p.texto)
        assertEquals("Calculado con $143.000 por día", p.supuesto)
        assertFalse(p.enAlerta, "la cifra ya dice que falta: una sola alarma en rojo")
    }

    /**
     * **El héroe contra una simulación independiente** (revisión de la 108, P3): la plata libre de hoy —Tu
     * plata, $9.086.784 con lo gastado del caso real, sin el apartado y sin el ahorro—, día por día hasta el
     * 24 nov: entra lo que se espera, salen los pagos que faltan, y desde mañana un día normal de gasto. No
     * usa `loQueQueda` ni el disponible: si el héroe mezclara dos bases (la cifra con tarjetas y el supuesto
     * sin ellas), no daría lo mismo.
     */
    @Test
    fun `la proyeccion del heroe da lo mismo que simular los dias, con apartado y ahorro`() {
        val ultimoDia = LocalDate(2026, 11, 24)
        listOf(0L, 300_000L).forEach { ahorro ->
            listOf(500_000L, 1_950_929L).forEach { gastado ->
                var libre = 9_086_784L + (1_950_929L - gastado) - ahorro
                var dia = hoy
                while (dia <= ultimoDia) {
                    checklist.filter { !it.pagado && LocalDate.parse(it.vence) == dia }.forEach { libre += if (it.esIngreso) it.monto else -it.monto }
                    if (dia > hoy) libre -= 143_154L
                    dia = dia.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
                }
                val r = teAlcanzaHastaQueTePaguen(disponible(gastado, ahorro), checklist, gastoNormal, hoy, conElPeriodoElegido = true)
                assertEquals(libre, r.proyeccion?.alFinal, "ahorro $ahorro, gastado $gastado")
                assertEquals(r.cifra, r.libres - r.pagosQueFaltan, "la línea resta exactamente la cifra")
            }
        }
    }

    @Test
    fun `cuando sobra dice te quedan, para cuantos dias y la proyeccion en alerta si empeora`() {
        val d = disponible(gastado = 100_000L)
        val r = teAlcanzaHastaQueTePaguen(d, checklist, gastoNormal, hoy, conElPeriodoElegido = true)
        assertEquals(ROTULO_TE_QUEDAN_HASTA_QUE_TE_PAGUEN, r.rotulo)
        assertEquals(1_262_010L, r.cifra)
        assertEquals("Para 23 días · $54.870 por día", r.explicacion)
        val p = assertNotNull(r.proyeccion)
        assertEquals(1_262_010L - 22 * 143_154L, p.alFinal)
        assertEquals("Hoy te alcanza; con tu gasto de un día normal, el 24 nov te faltarían unos $1,9M", p.texto)
        assertTrue(p.enAlerta, "hoy alcanza y con el gasto de un día normal no: empeora el veredicto")

        val holgado = teAlcanzaHastaQueTePaguen(d, checklist, GastoDelDiaADia(porDia = 20_000L, elegido = 20_000L), hoy, true)
        val ph = assertNotNull(holgado.proyeccion)
        assertEquals("Con tu gasto de un día normal te alcanza: el 24 nov te quedarían unos $822.010", ph.texto)
        assertEquals("Calculado con tus $20.000 por día", ph.supuesto)
        assertFalse(ph.enAlerta)
    }

    @Test
    fun `sin supuesto o el ultimo dia no hay proyeccion, y con el mes de calendario la pregunta dice el cierre`() {
        val d = disponible()
        assertNull(teAlcanzaHastaQueTePaguen(d, checklist, null, hoy, true).proyeccion)
        assertNull(teAlcanzaHastaQueTePaguen(d, checklist, GastoDelDiaADia(), hoy, true).proyeccion)
        val ultimo = assertNotNull(
            disponibleDelPeriodo(1_000_000L, emptyList(), emptyMap(), LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), LocalDate(2026, 11, 30)),
        )
        val r = teAlcanzaHastaQueTePaguen(ultimo, emptyList(), gastoNormal, LocalDate(2026, 11, 30), conElPeriodoElegido = false)
        assertNull(r.proyeccion)
        assertEquals("Hasta el 30 nov", r.pregunta)
        assertEquals("Para hoy", r.explicacion)
    }

    /** Sin margen ya no se cuenta «desde $0»: lo que faltó para los fijos también falta. */
    @Test
    fun `sin margen la cifra suma lo que falto para los fijos y lo gastado`() {
        val d = assertNotNull(
            disponibleDelPeriodo(
                ingresosRecibidos = 1_000_000L,
                checklist = listOf(pago("Arriendo", 1_500_000L, "2026-11-20")),
                gastoVariablePorDia = mapOf("2026-11-01" to 200_000L),
                inicio = LocalDate(2026, 11, 1),
                finExclusivo = LocalDate(2026, 12, 1),
                hoy = hoy,
            ),
        )
        val r = teAlcanzaHastaQueTePaguen(d, listOf(pago("Arriendo", 1_500_000L, "2026-11-20")), null, hoy, conElPeriodoElegido = false)
        assertEquals(-700_000L, r.cifra)
        assertEquals("Faltan $1,5M en pagos y tienes $800.000 libres, contando lo que te entra", r.explicacion)
    }

    @Test
    fun `pesos cortos y el redondeo del supuesto`() {
        assertEquals("$588.919", pesosCortos(588_919L))
        assertEquals("−$2,6M", pesosCortos(-2_584_055L))
        assertEquals("$12M", pesosCortos(12_000_000L))
        assertEquals("$1.500M", pesosCortos(1_500_000_000L))
        assertEquals(91_000L, redondeadoAMil(90_688L))
        assertEquals(9_999L, redondeadoAMil(9_999L))
    }

    @Test
    fun `los pagos que faltan son los fijos pendientes en pesos, del mas grande al mas chico`() {
        val faltan = pagosQueFaltanDelPeriodo(checklist)
        assertEquals(9, faltan.size)
        assertEquals(listOf("Cuota Vehículo 8761", "Cuota Hipotecario 2334", "Cuota Cotrafa 5413"), faltan.take(3).map { it.nombre })
        assertEquals(10_315_703L, faltan.sumOf { it.monto })
    }
}
