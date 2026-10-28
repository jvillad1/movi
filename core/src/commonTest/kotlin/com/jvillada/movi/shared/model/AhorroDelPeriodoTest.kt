package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El ahorro del período entra al Disponible por un solo lugar
 *
 * Decisión del dueño (27-oct-2026): «Te quedan» es lo libre después de los fijos y del ahorro que él
 * elige en Ajustes. La cuenta es [montoDelAhorro] (de ajuste a pesos) y [PlataDelDisponible.ahorroDelPeriodo]
 * (el tramo que [DisponibleDelPeriodo.disponible] resta). Sin elegir nada, todo da lo de siempre.
 */
class AhorroDelPeriodoTest {

    private val inicio = LocalDate(2026, 9, 25)
    private val fin = LocalDate(2026, 10, 25)
    private val hoy = LocalDate(2026, 10, 15)

    private val arriendo = PagoDelPeriodo(
        ruleId = "r-arriendo", nombre = "Arriendo", monto = 2_000_000L, pagado = false, diasParaVencer = 3,
        categoria = "Vivienda", cuentaId = "acc-1",
    )

    private fun disponible(
        ahorro: Long = 0L,
        apartado: Long = 0L,
        saldo: Long = 3_000_000L,
        entradas: Long = 10_000_000L,
        gasto: Map<String, Long> = mapOf("2026-10-01" to 1_500_000L),
        checklist: List<PagoDelPeriodo> = listOf(arriendo),
    ): DisponibleDelPeriodo? = disponibleDelPeriodo(
        ingresosRecibidos = entradas,
        checklist = checklist,
        gastoVariablePorDia = gasto,
        inicio = inicio,
        finExclusivo = fin,
        hoy = hoy,
        plata = PlataDelDisponible(
            saldoAlInicio = saldo, entradas = entradas, guardado = 0L, apartado = apartado, ahorroDelPeriodo = ahorro,
        ),
    )

    // ── De ajuste a pesos ─────────────────────────────────────────────────────

    @Test
    fun sin_elegir_nada_no_aparta_nada() {
        assertEquals(0L, montoDelAhorro(AhorroDelPeriodo(), 10_000_000L))
        assertEquals(TipoDeAhorro.NINGUNO, tipoDeAhorroDe(null))
        assertEquals(TipoDeAhorro.NINGUNO, tipoDeAhorroDe("ALGO_QUE_NO_EXISTE"))
        assertFalse(AhorroDelPeriodo(TipoDeAhorro.MONTO, 0L).apartaAlgo)
    }

    @Test
    fun un_monto_aparta_el_monto_haya_entrado_o_no() {
        val quinientos = AhorroDelPeriodo(TipoDeAhorro.MONTO, 500_000L)
        assertEquals(500_000L, montoDelAhorro(quinientos, 10_000_000L))
        assertEquals(500_000L, montoDelAhorro(quinientos, 0L))
    }

    @Test
    fun un_porcentaje_es_de_lo_que_entro_en_el_periodo() {
        val veinte = AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 20L)
        assertEquals(2_000_000L, montoDelAhorro(veinte, 10_000_000L))
        // Redondea hacia abajo al peso.
        assertEquals(19L, montoDelAhorro(veinte, 99L))
    }

    @Test
    fun un_porcentaje_sin_ingresos_da_cero() {
        assertEquals(0L, montoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 20L), 0L))
        assertEquals(0L, montoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 20L), -5_000L))
    }

    @Test
    fun el_porcentaje_tiene_tope_aunque_venga_uno_guardado_de_mas() {
        assertEquals(9_000L, montoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 150L), 10_000L))
    }

    @Test
    fun lo_que_se_puede_guardar() {
        assertNull(problemaDelAhorro(TipoDeAhorro.NINGUNO, null))
        assertNull(problemaDelAhorro(TipoDeAhorro.MONTO, 500_000L))
        assertNull(problemaDelAhorro(TipoDeAhorro.PORCENTAJE, 90L))
        assertNotNull(problemaDelAhorro(TipoDeAhorro.MONTO, null))
        assertNotNull(problemaDelAhorro(TipoDeAhorro.MONTO, 0L))
        assertNotNull(problemaDelAhorro(TipoDeAhorro.PORCENTAJE, 0L))
        assertNotNull(problemaDelAhorro(TipoDeAhorro.PORCENTAJE, 91L))
    }

    @Test
    fun el_perfil_sin_tocar_no_aparta_nada() {
        val perfil = UserProfile(id = "u", email = "a@b.c", name = "A", avatarColor = "#000000")
        assertEquals(AhorroDelPeriodo(), perfil.ahorroDelPeriodo())
    }

    // ── El tramo del Disponible ──────────────────────────────────────────────

    /** El candado de la decisión: sin elegir nada, todas las cifras de la tarjeta dan lo de siempre. */
    @Test
    fun sin_ahorro_todo_da_exactamente_lo_mismo_que_antes() {
        val d = assertNotNull(disponible())
        // Lo de siempre, a mano: 3M + 10M − 2M de fijos.
        assertEquals(11_000_000L, d.disponible)
        assertEquals(0L, d.ahorroDelPeriodo)
        // Un resumen de un server que no manda el campo es lo mismo que uno con cero.
        val viejo = plataDelDisponibleDe(DashboardSummary(saldoTuPlataAlInicio = 3_000_000L, entradasDelPeriodo = 10_000_000L))
        assertEquals(0L, viejo!!.ahorroDelPeriodo)
        assertEquals(d, disponible(ahorro = 0L))
    }

    @Test
    fun con_un_monto_te_quedan_baja_exactamente_el_ahorro() {
        val sin = assertNotNull(disponible())
        val con = assertNotNull(disponible(ahorro = 500_000L))

        assertEquals(sin.disponible - 500_000L, con.disponible)
        assertEquals(sin.periodo.teQuedan - 500_000L, con.periodo.teQuedan)
        assertEquals(500_000L, con.ahorroDelPeriodo)
        // Lo que tenías y lo que entró no cambian: el ahorro no mueve plata.
        assertEquals(sin.recursos, con.recursos)
        assertEquals(sin.plata!!.neto, con.plata!!.neto)
        // La cifra diaria del héroe y de Plan sale de lo que queda con el ahorro restado.
        assertEquals((con.disponible - con.periodo.gastado) / con.diasQueQuedan, con.porDiaParaLoQueQueda)
        assertTrue(con.porDiaParaLoQueQueda!! < sin.porDiaParaLoQueQueda!!)
    }

    @Test
    fun con_un_porcentaje_resta_lo_que_da_sobre_lo_que_entro() {
        val pesos = montoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 20L), 10_000_000L)
        val sin = assertNotNull(disponible())
        val con = assertNotNull(disponible(ahorro = pesos))
        assertEquals(sin.disponible - 2_000_000L, con.disponible)
    }

    @Test
    fun un_porcentaje_sin_ingresos_no_cambia_nada() {
        val pesos = montoDelAhorro(AhorroDelPeriodo(TipoDeAhorro.PORCENTAJE, 20L), 0L)
        assertEquals(disponible(entradas = 0L), disponible(entradas = 0L, ahorro = pesos))
    }

    /**
     * Un ahorro más grande que lo libre no esconde la tarjeta ni inventa un estado: queda sin margen,
     * igual que cuando los fijos se llevan todo, y lo gastado se lee como «te pasaste».
     */
    @Test
    fun un_ahorro_mayor_que_lo_libre_deja_la_tarjeta_sin_margen() {
        val d = assertNotNull(disponible(ahorro = 12_000_000L), "un ahorro grande no esconde la tarjeta")
        assertEquals(-1_000_000L, d.disponible)
        assertFalse(d.hayMargen)
        assertEquals(0L, d.periodo.meta)
        assertEquals(-1_500_000L, d.periodo.teQuedan, "lo gastado sin margen es lo que te pasaste")
        assertEquals(NivelDelGasto.PASADO, d.periodo.nivel)
        assertNull(d.porDiaParaLoQueQueda)
    }

    /** Con un apartado y un ahorro, cada uno se resta una vez: ninguno se descuenta dos veces. */
    @Test
    fun con_un_apartado_cada_uno_se_resta_una_sola_vez() {
        val base = assertNotNull(disponible()).disponible
        val soloApartado = assertNotNull(disponible(apartado = 1_000_000L)).disponible
        val soloAhorro = assertNotNull(disponible(ahorro = 500_000L)).disponible
        val ambos = assertNotNull(disponible(apartado = 1_000_000L, ahorro = 500_000L))

        assertEquals(base - 1_000_000L, soloApartado)
        assertEquals(base - 500_000L, soloAhorro)
        assertEquals(base - 1_500_000L, ambos.disponible)
        // Lo apartado sigue en `neto` (sale de Tu plata); el ahorro no.
        assertEquals(1_000_000L, assertNotNull(disponible()).plata!!.neto - ambos.plata!!.neto)
    }

    @Test
    fun un_ahorro_negativo_que_llegue_no_suma() {
        assertEquals(assertNotNull(disponible()).disponible, assertNotNull(disponible(ahorro = -300_000L)).disponible)
    }

    @Test
    fun el_resumen_del_server_lleva_el_ahorro_al_tramo() {
        val s = DashboardSummary(saldoTuPlataAlInicio = 1_000L, entradasDelPeriodo = 2_000L, ahorroDelPeriodo = 400L)
        assertEquals(400L, plataDelDisponibleDe(s)!!.ahorroDelPeriodo)
        // Y el neto no lo incluye.
        assertEquals(3_000L, plataDelDisponibleDe(s)!!.neto)
    }
}
