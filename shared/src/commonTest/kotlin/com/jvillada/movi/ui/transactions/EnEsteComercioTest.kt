package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola 2b · «En este comercio» y la línea de debajo del monto, sin Compose: lo que se cuenta, lo que
 * se suma y lo que se escribe en la búsqueda.
 */
class EnEsteComercioTest {

    private val zona: TimeZone = AppTimeZone.zone

    private fun gasto(
        id: String,
        descripcion: String,
        monto: Long,
        merchant: String? = null,
        tipo: TransactionType = TransactionType.EXPENSE,
        moneda: String = "COP",
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = "acc-nu",
        type = tipo,
        amount = monto,
        category = "Comida",
        description = descripcion,
        merchant = merchant,
        currency = moneda,
        timestamp = 1_760_000_000_000L,
        transferId = transferId,
    )

    @Test
    fun dos_del_mismo_comercio_y_uno_de_otro_cuentan_dos_y_suman_esos_dos() {
        val rappi1 = gasto("e1", "Rappi", 27_500L, merchant = "RAPPI*LAS DOCE")
        val rappi2 = gasto("e2", "Rappi Turbo", 41_300L, merchant = "RAPPI*TURBO")
        val otro = gasto("e3", "Carnes y Legumbres Santa Elena", 80_000L)

        val resumen = enEsteComercio(rappi1, listOf(rappi1, rappi2, otro))

        requireNotNull(resumen)
        assertEquals(2, resumen.veces)
        assertEquals(68_800L, resumen.total)
        assertEquals("COP", resumen.moneda)
        // La búsqueda encuentra a los dos con el mismo filtro de la pantalla, y no al otro.
        assertTrue(listOf(rappi1, rappi2).all { matchesQuery(it, resumen.busqueda) }, resumen.busqueda)
        assertTrue(!matchesQuery(otro, resumen.busqueda))
    }

    @Test
    fun sin_marca_agrupa_por_la_clave_de_comercio() {
        val a = gasto("e1", "Panadería La Esquina", 8_000L)
        val b = gasto("e2", "Panadería La Esquina", 12_000L)
        val c = gasto("e3", "Ferretería El Tornillo", 50_000L)

        val resumen = requireNotNull(enEsteComercio(b, listOf(a, b, c)))

        assertEquals(2, resumen.veces)
        assertEquals(20_000L, resumen.total)
        assertEquals("Panadería La Esquina", resumen.busqueda)
    }

    @Test
    fun el_abierto_cuenta_aunque_la_lista_no_lo_traiga() {
        val abierto = gasto("e1", "Rappi", 27_500L, merchant = "RAPPI")
        val otro = gasto("e2", "Rappi", 10_000L, merchant = "RAPPI")

        assertEquals(2, enEsteComercio(abierto, listOf(otro))?.veces)
    }

    @Test
    fun solo_el_abierto_no_dice_nada() {
        val solo = gasto("e1", "Rappi", 27_500L, merchant = "RAPPI")
        val otro = gasto("e2", "Netflix", 38_900L, merchant = "NETFLIX.COM")

        assertNull(enEsteComercio(solo, listOf(solo, otro)))
    }

    @Test
    fun no_mezcla_tipos_ni_monedas_ni_traspasos() {
        val gasto = gasto("e1", "Rappi", 27_500L, merchant = "RAPPI")
        val reembolso = gasto("e2", "Rappi", 27_500L, merchant = "RAPPI", tipo = TransactionType.INCOME)
        val enDolares = gasto("e3", "Rappi", 10L, merchant = "RAPPI", moneda = "USD")
        val pata = gasto("e4", "Rappi", 5_000L, merchant = "RAPPI", transferId = "tr-1")

        assertNull(enEsteComercio(gasto, listOf(gasto, reembolso, enDolares, pata)))
    }

    @Test
    fun el_texto_dice_cuantas_veces_y_cuanto() {
        val resumen = EnEsteComercio(veces = 4, total = 142_800L, moneda = "COP", busqueda = "Rappi")
        assertEquals("4 veces este período · \$142.800", textoDeEnEsteComercio(resumen, "\$142.800"))
    }

    // ── La línea de debajo del monto ────────────────────────────────────────────

    private fun a(fecha: LocalDateTime) = gasto("e1", "Rappi", 27_500L)
        .copy(timestamp = fecha.toInstant(zona).toEpochMilliseconds())

    @Test
    fun la_linea_dice_el_dia_la_hora_y_la_cuenta() {
        val hoy = LocalDate(2026, 10, 22)
        val evento = a(LocalDateTime(2026, 10, 22, 12, 40))
        assertEquals("Hoy, 12:40 · Nu", lineaDeCuandoYDonde(evento, "Nu", hoy, zona))
    }

    @Test
    fun el_mediodia_de_relleno_no_se_dice_como_hora() {
        val hoy = LocalDate(2026, 10, 22)
        val evento = a(LocalDateTime(2026, 10, 21, 12, 0))
        assertEquals("Ayer · Nu", lineaDeCuandoYDonde(evento, "Nu", hoy, zona))
    }

    @Test
    fun sin_la_cuenta_se_dice_solo_el_cuando() {
        val hoy = LocalDate(2026, 10, 22)
        val evento = a(LocalDateTime(2026, 10, 3, 7, 5))
        assertEquals("3 de octubre, 07:05", lineaDeCuandoYDonde(evento, null, hoy, zona))
    }

    // ── Deslizar la fila: qué pasa al soltar ────────────────────────────────────

    @Test
    fun soltar_pasado_el_umbral_a_la_izquierda_es_categoria() {
        assertEquals(AlSoltarLaFila.Categoria, alSoltarLaFila(-200f, 400, puedeConfirmar = false))
        assertEquals(AlSoltarLaFila.Nada, alSoltarLaFila(-100f, 400, puedeConfirmar = false))
    }

    @Test
    fun soltar_a_la_derecha_confirma_solo_un_pendiente() {
        assertEquals(AlSoltarLaFila.Confirmar, alSoltarLaFila(200f, 400, puedeConfirmar = true))
        assertEquals(AlSoltarLaFila.Nada, alSoltarLaFila(200f, 400, puedeConfirmar = false))
        assertEquals(AlSoltarLaFila.Nada, alSoltarLaFila(100f, 400, puedeConfirmar = true))
    }
}
