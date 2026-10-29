package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 66 · lo que dicen el historial, la propuesta y las hojas de un grupo, sin dibujo
 */
class TextosDelGrupoTest {

    private val zona = TimeZone.of("America/Bogota")
    private fun dia(d: Int): Long = LocalDateTime(2026, 10, d, 12, 0).toInstant(zona).toEpochMilliseconds()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun gasto(id: String, monto: Long, fecha: Long) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = monto, categoria = "Mercado",
        descripcion = "Mercado", fecha = fecha, eventoId = "e_$id", partes = partesDelGasto(monto, casa, MIEMBRO_YO),
    )

    @Test
    fun `el historial dice quien le pago a quien, cuando y con que`() {
        val vinculado = LiquidacionDelEspacio("l1", casa.id, "dst_caro", MIEMBRO_YO, 168_050, fecha = dia(29), eventoId = "e", movimiento = "Transferencia de Caro")
        assertEquals("Caro te pagó $168.050", tituloDelPago(casa, vinculado))
        assertEquals("29 oct\nTransferencia de Caro", detalleDelPago(vinculado, dia(30), zona))
        val espera = vinculado.copy(eventoId = null, movimiento = null)
        assertEquals("29 oct\n$ESPERANDO_LA_TRANSFERENCIA", detalleDelPago(espera, dia(30), zona))
        assertEquals("29 oct\n$SIN_TRANSFERENCIA", detalleDelPago(espera, dia(29) + 60L * 86_400_000L, zona))
        assertEquals("Le pagaste $90.000 a Caro", tituloDelPago(casa, LiquidacionDelEspacio("l2", casa.id, MIEMBRO_YO, "dst_caro", 90_000)))
        assertEquals("Saldaron $168.050 · 29 oct", corteDelSaldado(vinculado, zona))
    }

    @Test
    fun `la lista de gastos lleva un corte en cada saldado, entre los gastos`() {
        val gastos = listOf(gasto("g3", 30_000, dia(30)), gasto("g2", 20_000, dia(20)), gasto("g1", 10_000, dia(10)))
        val liq = LiquidacionDelEspacio("l", casa.id, "dst_caro", MIEMBRO_YO, 15_000, fecha = dia(25))
        val viejo = LiquidacionDelEspacio("v", casa.id, "dst_caro", MIEMBRO_YO, 5_000, fecha = dia(1))
        val renglones = gastosConSusCortes(gastos, listOf(liq, viejo))
        assertEquals(listOf("g3", "corte:l", "g2", "g1"), renglones.map {
            when (it) {
                is RenglonDeLosGastos.Gasto -> it.gasto.id
                is RenglonDeLosGastos.Corte -> "corte:${it.liquidacion.id}"
            }
        })
    }

    @Test
    fun `la propuesta dice si es la del saldado que espera`() {
        val p = PropuestaDeSaldar("e", "dst_caro", MIEMBRO_YO, 168_050, dia(29), "Transferencia de Caro", nombreDelEspacio = "Casa con Caro")
        assertEquals("¿Esta transferencia de Caro salda Casa con Caro?", preguntaDeLaPropuesta(p, "Caro", "Casa con Caro"))
        assertEquals("¿Es la transferencia de Caro que marcaste como saldada?", preguntaDeLaPropuesta(p.copy(liquidacionId = "l"), "Caro", "Casa con Caro"))
        assertEquals("Transferencia de Caro · Casa con Caro", cuerpoDeLaPropuesta(p, conElGrupo = true))
        assertEquals("Transferencia de Caro", cuerpoDeLaPropuesta(p, conElGrupo = false))
    }

    @Test
    fun `borrar dice que pasa, con y sin saldo pendiente`() {
        val vacio = DetalleDelEspacio(casa, saldoDelEspacio(casa, emptyList(), emptyList()), saldoDelEspacio(casa, emptyList(), emptyList()), emptyList())
        assertEquals("¿Borrar «Casa con Caro»?" to "No tiene gastos ni pagos.", confirmacionDeBorrarElGrupo(vacio))
        assertEquals("Borrar grupo", botonDeBorrarElGrupo(vacio))

        val gastos = listOf(gasto("g", 152_100, dia(20)))
        val conDeuda = DetalleDelEspacio(casa, saldoDelEspacio(casa, gastos, emptyList()), saldoDelEspacio(casa, gastos, emptyList()), gastos)
        val (_, cuerpo) = confirmacionDeBorrarElGrupo(conDeuda)
        assertTrue(cuerpo.startsWith("Caro te debe $76.050: al borrarlo, Movi deja de llevar esa cuenta."), cuerpo)
        assertTrue("vuelven a contar enteros" in cuerpo)
        assertEquals("Borrar de todas formas", botonDeBorrarElGrupo(conDeuda))
    }

    @Test
    fun `nuevo grupo - el nombre sugerido y el aviso de repetido`() {
        val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "")
        val ana = DestinoConocido(id = "dst_ana", nombre = "Ana", numero = "")
        assertEquals("Casa con Caro", nombreSugeridoDelGrupo(listOf(caro)))
        assertEquals("Casa con Caro y Ana", nombreSugeridoDelGrupo(listOf(caro, ana)))
        assertEquals("Ya tienes «Casa con Caro» con Caro.", avisoDeGrupoRepetido("casa con caro", listOf(caro), listOf(casa)))
        assertNull(avisoDeGrupoRepetido("Viaje", listOf(caro), listOf(casa)), "otro nombre no es repetido")
        assertNull(avisoDeGrupoRepetido("Casa con Caro", listOf(caro, ana), listOf(casa)), "otras personas no es repetido")
    }
}
