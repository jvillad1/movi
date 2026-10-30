package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.dashboard.CATEGORIA_NUEVA
import com.jvillada.movi.ui.dashboard.CategoriaDelPeriodo
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TONO_DE_LO_NUEVO
import com.jvillada.movi.ui.dashboard.categoriasDelPeriodo
import com.jvillada.movi.ui.dashboard.categoriasFijas
import com.jvillada.movi.ui.dashboard.cuantasCosasProponen
import com.jvillada.movi.ui.dashboard.cuerpoDeLaAnomalia
import com.jvillada.movi.ui.dashboard.diferenciaDeLaCategoria
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import com.jvillada.movi.ui.dashboard.visibleSections
import com.jvillada.movi.ui.destinos.elTerceroEnLosGrupos
import com.jvillada.movi.ui.destinos.lineaDelTerceroEnLaLista
import com.jvillada.movi.ui.destinos.losTercerosEnLosGrupos
import com.jvillada.movi.ui.mas.seVeLaFilaDeCorreo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 76, lo puro
 *
 * - La fila del tercero en «Personas y comercios» dice lo de su ficha («Te debe $168.050 · Casa con Caro»).
 * - El widget «Falta por pagar» con todo pagado no ocupa lugar.
 * - «Nuevo» con sentido: solo con el período pasado cubierto, nunca en un fijo ni en la cuota, y en un
 *   solo tono (nunca rojo).
 * - «Movi propone hoy · N» cuenta lo que dicen las tarjetas, y la anomalía dice «cosas para revisar».
 * - La fila de las alertas por correo solo con dirección.
 */
class Vuelta76Test {

    // ── (1) La fila del tercero ──────────────────────────────────────────────

    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio("dst_caro", "Caro")))
    private val viaje = Espacio(id = "esp_viaje", nombre = "Viaje", miembros = listOf(MiembroDelEspacio("dst_caro", "Caro")))

    /** Caro puso $240.000 y tú $576.100, mitad y mitad: Caro te debe $168.050. */
    private val gastos = listOf(
        GastoDelEspacio(id = "g1", espacioId = casa.id, pagadoPor = "dst_caro", monto = 240_000, partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L)),
        GastoDelEspacio(id = "g2", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 576_100, partes = mapOf(MIEMBRO_YO to 288_050L, "dst_caro" to 288_050L)),
    )

    private fun detalle(e: Espacio, g: List<GastoDelEspacio>): DetalleDelEspacio {
        val saldo = saldoDelEspacio(e, g, emptyList())
        return DetalleDelEspacio(espacio = e, saldo = saldo, delPeriodo = saldo, gastos = g)
    }

    @Test
    fun `la fila del tercero dice lo mismo que su ficha`() {
        val detalles = listOf(detalle(casa, gastos))
        val ficha = elTerceroEnLosGrupos("dst_caro", detalles)
        assertEquals("En Casa con Caro: puso \$240.000 · te debe \$168.050", ficha.single().texto)
        // La fila: la misma deuda, de las MISMAS líneas.
        assertEquals("Te debe \$168.050 · Casa con Caro", lineaDelTerceroEnLaLista(ficha))
        val porTercero = losTercerosEnLosGrupos(detalles)
        assertEquals(setOf("dst_caro"), porTercero.keys, "yo no soy un tercero")
        assertEquals(ficha, porTercero.getValue("dst_caro"))
    }

    @Test
    fun `sin grupos la fila dice lo de siempre, y con varios primero el que tiene saldo`() {
        assertNull(lineaDelTerceroEnLaLista(emptyList()))
        val parejo = listOf(GastoDelEspacio(id = "g", espacioId = viaje.id, pagadoPor = "dst_caro", monto = 0, partes = emptyMap()))
        val lineas = elTerceroEnLosGrupos("dst_caro", listOf(detalle(viaje, parejo), detalle(casa, gastos)))
        assertEquals("Te debe \$168.050 · Casa con Caro · y 1 grupo más", lineaDelTerceroEnLaLista(lineas))
    }

    // ── (2) El widget con todo pagado ────────────────────────────────────────

    private fun regla(id: String, nombre: String, categoria: String = "Vivienda") =
        RecurringRule(id = id, name = nombre, category = categoria, amount = 53_000, dayOfMonth = 10, type = TransactionType.EXPENSE)

    private val todoPagado = DashboardData(
        upcoming = listOf(UpcomingPayment(regla("rr_cel", "Celular"), "2026-10-10", 23, PaymentStatus.UPCOMING)),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_cel", period = "2026-09", dueDate = "2026-09-10", occurred = true)),
        ajustesDePeriodo = PeriodSettings(),
        periodoActual = PeriodoFinanciero(2026, 9),
        spentByCategory = mapOf("Mercado" to 300_000L),
    )

    @Test
    fun `con todo pagado el widget Falta por pagar no ocupa lugar`() {
        val tipos = visibleSections(defaultDashboardDefinition(), todoPagado).map { it.type }
        assertFalse("CHECKLIST_DEL_PERIODO" in tipos, "era una tarjeta de 300 px para decir «Ya salió todo»")
        assertTrue("GASTO_POR_CATEGORIA" in tipos)
        // Con dos pendientes, sigue (con uno solo ya lo dice «Próximo pago»: vuelta 73).
        val conDos = todoPagado.copy(
            ocurrencias = emptyList(),
            upcoming = listOf(
                UpcomingPayment(regla("rr_cel", "Celular"), "2026-09-10", 3, PaymentStatus.UPCOMING),
                UpcomingPayment(regla("rr_net", "Internet"), "2026-09-12", 5, PaymentStatus.UPCOMING),
            ),
        )
        assertTrue("CHECKLIST_DEL_PERIODO" in visibleSections(defaultDashboardDefinition(), conDos).map { it.type })
    }

    // ── (3) «Nuevo» con sentido ──────────────────────────────────────────────

    private fun categoria(nombre: String, gastado: Long, anterior: Long?) =
        CategoriaDelPeriodo(nombre = nombre, gastado = gastado, fraccion = 0.1f, anterior = anterior)

    @Test
    fun `nuevo solo con el periodo pasado cubierto`() {
        // Sin anterior (Movi no cubrió el período pasado: la guarda de siempre deja `null`), nada.
        assertNull(diferenciaDeLaCategoria(categoria("Ropa", 312_000, anterior = null)))
        assertTrue(categoriasDelPeriodo(mapOf("Ropa" to 312_000L), gastoDelAnterior = null).all { diferenciaDeLaCategoria(it) == null })
        // Cubierto y sin gasto a esta altura: «nuevo».
        assertEquals(CATEGORIA_NUEVA, diferenciaDeLaCategoria(categoria("Ropa", 312_000, anterior = 0))?.texto)
    }

    @Test
    fun `un fijo o una cuota nunca es nuevo`() {
        val fijas = categoriasFijas(listOf(regla("rr_net", "Internet", categoria = "Internet")))
        assertTrue(CUOTA_CATEGORY in fijas, "la cuota siempre es fija")
        assertNull(diferenciaDeLaCategoria(categoria(CUOTA_CATEGORY, 980_000, anterior = 0), fijas))
        assertNull(diferenciaDeLaCategoria(categoria("Internet", 148_500, anterior = 0), fijas))
        // Lo que salda un pago del período también.
        val conElPago = categoriasFijas(emptyList(), saldanUnPago = listOf("Gimnasio"))
        assertNull(diferenciaDeLaCategoria(categoria("Gimnasio", 98_000, anterior = 0), conElPago))
        // Un ingreso recurrente no hace fija su categoría.
        val ingreso = RecurringRule(id = "rr_sal", name = "Salario", category = "Salario", amount = 1, dayOfMonth = 25, type = TransactionType.INCOME)
        assertFalse("Salario" in categoriasFijas(listOf(ingreso)))
        // Lo que no es fijo sigue diciendo «nuevo», y un fijo que sube sigue diciendo cuánto.
        assertEquals(CATEGORIA_NUEVA, diferenciaDeLaCategoria(categoria("Ropa", 312_000, anterior = 0), fijas)?.texto)
        assertEquals("\$100.000 más", diferenciaDeLaCategoria(categoria("Internet", 248_500, anterior = 148_500), fijas)?.texto)
    }

    @Test
    fun `nuevo va siempre en un solo tono, y el rojo queda para lo que sube mucho`() {
        val nuevos = listOf(1_800_000L, 312_000L, 271_900L, 9_000L).map { diferenciaDeLaCategoria(categoria("X$it", it, anterior = 0))!! }
        assertEquals(setOf(TONO_DE_LO_NUEVO), nuevos.map { it.tono }.toSet(), "Otros $1.800.000 y Salud $271.900 iban en tonos distintos")
        assertTrue(TONO_DE_LO_NUEVO != TonoDeCifra.Alerta)
        // La regla de «sube mucho» no cambió: 1,5× y $300.000.
        assertEquals(TonoDeCifra.Alerta, diferenciaDeLaCategoria(categoria("Mercado", 592_250, anterior = 184_300))!!.tono)
        assertEquals(TonoDeCifra.Neutro, diferenciaDeLaCategoria(categoria("Mercado", 250_000, anterior = 184_300))!!.tono)
    }

    // ── (4) Una palabra por cosa ─────────────────────────────────────────────

    private fun anomalia(i: Int) = Anomalia(
        huella = "h$i", tipo = TipoDeAnomalia.CATEGORIA_POR_ENCIMA, titulo = "Gasto alto en Otros $i",
        detalle = "Llevas \$1.800.000.", evidencia = emptyList(),
    )

    @Test
    fun `la anomalia dice cosas para revisar y el encabezado cuenta lo mismo que la tarjeta`() {
        assertEquals("Llevas \$1.800.000 · y 3 cosas más para revisar", cuerpoDeLaAnomalia("Llevas \$1.800.000.", 3))
        assertEquals("Llevas \$1.800.000 · y 1 cosa más para revisar", cuerpoDeLaAnomalia("Llevas \$1.800.000.", 1))
        val data = DashboardData(anomalias = (1..4).map(::anomalia))
        val propuestas = propuestasDelInicio(data)
        assertEquals(1, propuestas.size, "una tarjeta por tipo")
        assertTrue(propuestas.single().cuerpo!!.endsWith("y 3 cosas más para revisar"))
        assertEquals(4, cuantasCosasProponen(propuestas), "«Movi propone hoy · 1» con «y 3 más» debajo no cuadraba")
        assertTrue(propuestas.none { "aviso" in it.cuerpo.orEmpty() })
    }

    // ── (5) La fila de las alertas por correo ────────────────────────────────

    @Test
    fun `la fila de correo solo con una direccion del server`() {
        assertTrue(seVeLaFilaDeCorreo(esAndroid = false, direccion = "alertas+abc@movi.resend.app"))
        assertFalse(seVeLaFilaDeCorreo(esAndroid = false, direccion = null), "sin reenvío en el server, ninguna fila muerta")
        assertFalse(seVeLaFilaDeCorreo(esAndroid = true, direccion = "alertas+abc@movi.resend.app"), "en Android se leen los SMS")
    }
}
