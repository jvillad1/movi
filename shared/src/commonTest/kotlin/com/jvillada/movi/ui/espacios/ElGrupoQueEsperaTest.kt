package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
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
 * # Vuelta 67 · lo que espera un grupo se ve, y renombrar avisa como crear
 *
 * La revisión de la vuelta 66: la tarjeta del saldo decía solo «Están a mano» mientras el saldado
 * esperaba la transferencia, y el historial lo cortaba en «Esperando la transfere…».
 */
class ElGrupoQueEsperaTest {

    private val zona = TimeZone.of("America/Bogota")
    private fun dia(d: Int): Long = LocalDateTime(2026, 10, d, 12, 0).toInstant(zona).toEpochMilliseconds()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun gasto(monto: Long, fecha: Long) = GastoDelEspacio(
        id = "g", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = monto, categoria = "Mercado",
        descripcion = "Mercado", fecha = fecha, eventoId = "e_g", partes = partesDelGasto(monto, casa, MIEMBRO_YO),
    )

    private fun detalle(liquidaciones: List<LiquidacionDelEspacio>): DetalleDelEspacio {
        val gastos = listOf(gasto(336_100, dia(20)))
        val saldo = saldoDelEspacio(casa, gastos, liquidaciones)
        return DetalleDelEspacio(casa, saldo, saldo, gastos, liquidaciones = liquidaciones)
    }

    @Test
    fun `a mano y esperando la transferencia, la tarjeta lo dice`() {
        val espera = LiquidacionDelEspacio("l1", casa.id, "dst_caro", MIEMBRO_YO, 168_050, fecha = dia(29))
        val d = detalle(listOf(espera))
        assertEquals("Están a mano · esperando \$168.050 de Caro", tituloDeLaTarjetaDelSaldo(d, dia(30)))
    }

    @Test
    fun `vinculado, o vencida la espera, la tarjeta dice solo el saldo`() {
        val vinculado = LiquidacionDelEspacio("l1", casa.id, "dst_caro", MIEMBRO_YO, 168_050, fecha = dia(29), eventoId = "tr")
        assertEquals("Están a mano", tituloDeLaTarjetaDelSaldo(detalle(listOf(vinculado)), dia(30)))
        val viejo = vinculado.copy(eventoId = null)
        assertNull(loQueEspera(detalle(listOf(viejo)), dia(29) + 60L * 86_400_000L), "pasados 45 días ya no espera")
    }

    @Test
    fun `si el que debe pagar soy yo, espera mi transferencia`() {
        val mia = LiquidacionDelEspacio("l2", casa.id, MIEMBRO_YO, "dst_caro", 90_000, fecha = dia(29))
        assertEquals("esperando tu transferencia de \$90.000 a Caro", loQueEspera(detalle(listOf(mia)), dia(30)))
    }

    @Test
    fun `el detalle del pago va en dos renglones`() {
        val espera = LiquidacionDelEspacio("l1", casa.id, "dst_caro", MIEMBRO_YO, 168_050, fecha = dia(29))
        val renglones = detalleDelPago(espera, dia(30), zona).lines()
        assertEquals(RENGLONES_DEL_DETALLE_DEL_PAGO, renglones.size)
        assertEquals(ESPERANDO_LA_TRANSFERENCIA, renglones[1])
    }

    @Test
    fun `renombrar avisa si otro grupo ya se llama asi con las mismas personas`() {
        val otro = casa.copy(id = "esp_viaje", nombre = "Viaje")
        assertEquals("Ya tienes «Casa con Caro» con Caro.", avisoDeNombreRepetido("casa con caro", otro, listOf(casa, otro)))
        assertNull(avisoDeNombreRepetido("Casa con Caro", casa, listOf(casa, otro)), "el mismo grupo no es repetido")
        assertNull(avisoDeNombreRepetido("Paseo", otro, listOf(casa, otro)))
    }

    @Test
    fun `el No dice que tambien apaga la propuesta para un saldo futuro`() {
        assertTrue("saldo futuro" in DETALLE_DEL_NO_DE_SALDAR)
    }
}
