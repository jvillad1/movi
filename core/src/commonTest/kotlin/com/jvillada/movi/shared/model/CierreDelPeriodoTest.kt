package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Cerró tu período»**: el resumen de cierre, puro. Casos con el período real del dueño (septiembre
 * con corte 25: del 25-ago al 24-sep): con y sin período anterior, sin pagos, con lo que se salió de lo
 * normal, y lo que dice cada renglón.
 */
class CierreDelPeriodoTest {

    private val septiembre = ResumenDePeriodo(
        id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-24",
        entradas = 22_500_000, salidas = 18_200_000, movimientos = 140,
    )
    private val agosto = ResumenDePeriodo(
        id = "2026-08", nombre = "Agosto 2026", desde = "2026-07-25", hasta = "2026-08-24",
        entradas = 21_500_000, salidas = 18_700_000, movimientos = 120,
    )
    private val categorias = listOf(
        GastoDeCategoria("Comida", 900_000),
        GastoDeCategoria("Cuota de crédito", 12_900_000),
        GastoDeCategoria("Transporte", 400_000),
        GastoDeCategoria("Vivienda", 3_100_000),
        GastoDeCategoria("Regalos", 0),
    )

    private fun pago(nombre: String, pagado: Boolean, vence: String, ingreso: Boolean = false) = PagoDelPeriodo(
        ruleId = "r-$nombre", nombre = nombre, monto = 100_000, pagado = pagado, diasParaVencer = -1,
        vence = vence, esIngreso = ingreso,
    )

    private val pagos = listOf(
        pago("Arriendo", pagado = true, vence = "2026-09-05"),
        pago("Gimnasio", pagado = false, vence = "2026-09-10"),
        pago("Celular", pagado = true, vence = "2026-09-20"),
        pago("Salario", pagado = true, vence = "2026-08-26", ingreso = true),
    )

    private val duplicado = Anomalia(
        huella = "dup", tipo = TipoDeAnomalia.COBRO_DUPLICADO, titulo = "¿Cobro duplicado en Rappi?",
        detalle = "2 cobros de \$45.900", evidencia = emptyList(),
    )

    private fun cierre(
        anterior: ResumenDePeriodo? = agosto,
        pagos: List<PagoDelPeriodo> = this.pagos,
        anomalias: List<Anomalia> = emptyList(),
        disponible: DisponibleDelPeriodo? = null,
        dia: Int = 1,
    ) = cierreDelPeriodo(septiembre, categorias, anterior, pagos, anomalias, disponible, dia)

    @Test
    fun `las cifras salen tal cual de Tus periodos`() {
        val c = cierre()
        assertEquals("2026-09", c.periodo)
        assertEquals(22_500_000, c.entro)
        assertEquals(18_200_000, c.salio)
        assertEquals(CifrasDelAnterior("2026-08", "Agosto 2026", 21_500_000, 18_700_000), c.anterior)
    }

    @Test
    fun `las tres categorias de mas gasto, de mayor a menor y sin las de cero`() {
        assertEquals(listOf("Cuota de crédito", "Vivienda", "Comida"), cierre().categorias.map { it.category })
    }

    @Test
    fun `los pagos cuentan solo lo que se paga, no los ingresos`() {
        val c = cierre()
        assertEquals(2, c.pagosCumplidos)
        assertEquals(3, c.pagosEsperados)
        assertEquals(listOf("Gimnasio"), c.pagosQueFaltaron)
        assertEquals("Pagos: cumpliste 2 de 3 · faltó Gimnasio", pagosDelCierre(c))
    }

    @Test
    fun `sin pagos en la lista no hay renglon de pagos`() {
        val c = cierre(pagos = emptyList())
        assertEquals(0, c.pagosEsperados)
        assertNull(pagosDelCierre(c))
        assertTrue(lineasDelCierre(c).none { it.startsWith("Pagos") })
    }

    @Test
    fun `todos pagados se dice asi`() {
        val c = cierre(pagos = pagos.map { it.copy(pagado = true) })
        assertEquals("Pagos: cumpliste los 3", pagosDelCierre(c))
    }

    @Test
    fun `sin periodo anterior no hay comparacion`() {
        val c = cierre(anterior = null)
        assertNull(c.anterior)
        assertNull(comparacionDelCierre(c))
    }

    @Test
    fun `contra el anterior dice cuanto mas y cuanto menos`() {
        assertEquals(
            "Contra agosto: entró \$1.000.000 más y salió \$500.000 menos",
            comparacionDelCierre(cierre()),
        )
    }

    @Test
    fun `lo que se salio de lo normal se nombra`() {
        val c = cierre(anomalias = listOf(duplicado, duplicado.copy(huella = "otra", titulo = "Netflix subió")))
        assertEquals(listOf("¿Cobro duplicado en Rappi?", "Netflix subió"), c.fueraDeLoNormal)
        assertEquals("Fuera de lo normal: ¿Cobro duplicado en Rappi? y 1 más", fueraDeLoNormalDelCierre(c))
        assertNull(fueraDeLoNormalDelCierre(cierre()))
    }

    @Test
    fun `lo que quedo del disponible es la columna Periodo del ultimo dia`() {
        // $10M de plata, $3M de fijos (la regla del dueño: ingresos menos fijos), y $6,5M gastados en
        // el período: quedan $500.000.
        val disponible = disponibleAlCerrar(
            ingresosRecibidos = 10_000_000,
            pagos = listOf(pago("Arriendo", pagado = true, vence = "2026-09-05").copy(monto = 3_000_000)),
            gastoVariablePorDia = mapOf("2026-09-01" to 6_000_000, "2026-09-24" to 500_000),
            desde = "2026-08-25",
            hasta = "2026-09-24",
            plata = null,
        )
        val c = cierre(disponible = disponible)
        assertEquals(500_000, c.quedoDelDisponible)
        assertEquals("Te quedaron \$500.000 del disponible", disponibleDelCierre(c))
        // Y es la misma cifra que la tarjeta de Hoy decía esa noche.
        val hoyEsaNoche = disponibleDelPeriodo(
            ingresosRecibidos = 10_000_000,
            checklist = listOf(pago("Arriendo", pagado = true, vence = "2026-09-05").copy(monto = 3_000_000)),
            gastoVariablePorDia = mapOf("2026-09-01" to 6_000_000, "2026-09-24" to 500_000),
            inicio = LocalDate(2026, 8, 25),
            finExclusivo = LocalDate(2026, 9, 25),
            hoy = LocalDate(2026, 9, 24),
        )
        assertEquals(hoyEsaNoche?.periodo?.teQuedan, c.quedoDelDisponible)
    }

    @Test
    fun `pasarse del disponible se dice con la cifra en positivo`() {
        assertEquals(
            "Te pasaste del disponible por \$180.000",
            disponibleDelCierre(cierre().copy(quedoDelDisponible = -180_000)),
        )
        assertNull(disponibleDelCierre(cierre()))
    }

    @Test
    fun `el titulo, la frase y los renglones`() {
        val c = cierre(anomalias = listOf(duplicado)).copy(quedoDelDisponible = 420_000)
        assertEquals("Cerró tu período de septiembre", tituloDelCierre(c))
        assertEquals("Cerró tu período: entró \$22.500.000, salió \$18.200.000", fraseDelCierre(c))
        assertEquals(
            listOf(
                "Entró \$22.500.000 · salió \$18.200.000",
                "Contra agosto: entró \$1.000.000 más y salió \$500.000 menos",
                "Lo que más pesó: Cuota de crédito \$12.900.000 · Vivienda \$3.100.000 · Comida \$900.000",
                "Pagos: cumpliste 2 de 3 · faltó Gimnasio",
                "Fuera de lo normal: ¿Cobro duplicado en Rappi?",
                "Te quedaron \$420.000 del disponible",
            ),
            lineasDelCierre(c),
        )
    }

    @Test
    fun `la tarjeta de Hoy se ve los primeros cinco dias y no despues de descartarla`() {
        assertTrue(seMuestraEnHoy(cierre(dia = 1)))
        assertTrue(seMuestraEnHoy(cierre(dia = DIAS_DE_LA_TARJETA_DE_CIERRE)))
        assertFalse(seMuestraEnHoy(cierre(dia = DIAS_DE_LA_TARJETA_DE_CIERRE + 1)))
        assertFalse(seMuestraEnHoy(cierre(dia = 1).copy(descartado = true)))
    }

    @Test
    fun `el aviso y el correo salen solo los primeros tres dias`() {
        assertTrue(esHoraDeAvisarElCierre(1))
        assertTrue(esHoraDeAvisarElCierre(3))
        assertFalse(esHoraDeAvisarElCierre(4))
        assertFalse(esHoraDeAvisarElCierre(0))
    }

    @Test
    fun `los pesos se escriben enteros y con el menos tipografico`() {
        assertEquals("\$1.234.567", pesosDelCierre(1_234_567))
        assertEquals("−\$500", pesosDelCierre(-500))
        assertEquals("\$0", pesosDelCierre(0))
    }

    // ── La foto del cierre (vuelta 95) ───────────────────────────────────────

    @Test
    fun `sin diferencia en lo que entro y salio no hay cambio`() {
        val foto = cierre()
        assertNull(cambioDesdeElCierre(foto, cierre()))
        assertNull(lineaDelCambioDelCierre(foto))
    }

    @Test
    fun `la linea del cambio nombra solo lo que cambio`() {
        val foto = cierre()
        val hoy = foto.copy(salio = foto.salio + 150_000)
        val cambio = cambioDesdeElCierre(foto, hoy)
        assertEquals(CambioDesdeElCierre(22_500_000, 22_500_000, 18_200_000, 18_350_000), cambio)
        assertEquals(
            "Cambió desde el cierre: Salió \$18.200.000 → \$18.350.000",
            lineaDelCambioDelCierre(foto.copy(cambio = cambio)),
        )
        val los2 = cambioDesdeElCierre(foto, hoy.copy(entro = 23_000_000))
        assertEquals(
            "Cambió desde el cierre: Entró \$22.500.000 → \$23.000.000 · Salió \$18.200.000 → \$18.350.000",
            lineaDelCambioDelCierre(foto.copy(cambio = los2)),
        )
    }

    @Test
    fun `si hoy no queda nada que contar, cuenta como cero`() {
        val cambio = assertNotNull(cambioDesdeElCierre(cierre(), null))
        assertEquals(0, cambio.entroAhora)
        assertEquals(0, cambio.salioAhora)
    }

    @Test
    fun `el cambio no entra en las lineas del correo`() {
        val c = cierre().copy(cambio = CambioDesdeElCierre(1, 2, 3, 4))
        assertTrue(lineasDelCierre(c).none { it.contains("Cambió desde el cierre") })
    }

    @Test
    fun `la hoja de la IA se abre solo si no se vio y la pregunta va a la IA`() {
        assertTrue(hayQueAvisarDeLaIa(null))
        assertFalse(hayQueAvisarDeLaIa(1_700_000_000_000L))
        assertFalse(hayQueAvisarDeLaIa(null, vaALaIa = false), "las tres sugeridas no van a la IA")
    }

    @Test
    fun `la frase de lo que Movi sabe cuenta las cuatro cosas`() {
        assertEquals(
            "Movi tiene 133 movimientos desde el 12 de agosto de 2026, 3 cuentas, 24 reglas de categoría aprendidas y 2 recuerdos.",
            fraseDeLoQueMoviSabe(ResumenDeLoQueMoviSabe(133, "2026-08-12", 3, 24, 2)),
        )
        assertEquals(
            "Movi tiene 1 movimiento desde el 3 de enero de 2027, 1 cuenta, 1 regla de categoría aprendida y 1 recuerdo.",
            fraseDeLoQueMoviSabe(ResumenDeLoQueMoviSabe(1, "2027-01-03", 1, 1, 1)),
        )
        assertEquals(
            "Movi tiene 0 movimientos, 2 cuentas, 0 reglas de categoría aprendidas y 0 recuerdos.",
            fraseDeLoQueMoviSabe(ResumenDeLoQueMoviSabe(0, null, 2, 0, 0)),
        )
        assertEquals("Movi todavía no sabe nada de ti.", fraseDeLoQueMoviSabe(ResumenDeLoQueMoviSabe()))
    }
}
