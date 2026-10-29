package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El apartado para un gasto de un mes puntual (vuelta 64)
 *
 * «Para el SOAT de marzo: llevas $400.000 de $1.200.000 · aparta $160.000 al mes». La sugerencia es
 * (objetivo − lo apartado) ÷ los meses que faltan, y nada más: no es un fijo ni mueve plata.
 */
class ApartadoParaUnGastoTest {

    private val hoy = LocalDate(2026, 10, 29)

    @Test
    fun de_octubre_a_marzo_faltan_cinco_meses() {
        assertEquals(5, mesesHastaElGasto(PeriodoFinanciero(2027, 3), hoy))
        assertEquals(0, mesesHastaElGasto(PeriodoFinanciero(2026, 10), hoy))
        assertEquals(-1, mesesHastaElGasto(PeriodoFinanciero(2026, 9), hoy))
    }

    @Test
    fun el_aporte_es_lo_que_falta_entre_los_meses_redondeado_hacia_arriba() {
        assertEquals(160_000L, aporteMensualSugerido(objetivo = 1_200_000L, apartado = 400_000L, meses = 5))
        // 1.000.001 entre 3 no da exacto: hacia arriba, para que la suma alcance.
        val aporte = assertNotNull(aporteMensualSugerido(1_000_001L, 0L, 3))
        assertEquals(333_334L, aporte)
        assertTrue(aporte * 3 >= 1_000_001L)
    }

    @Test
    fun lo_apartado_que_ya_cubre_el_objetivo_no_pide_mas() {
        assertEquals(0L, aporteMensualSugerido(1_200_000L, 1_200_000L, 5))
        assertEquals(0L, aporteMensualSugerido(1_200_000L, 2_000_000L, 5))
    }

    @Test
    fun el_mismo_mes_pide_todo_y_un_mes_pasado_no_sugiere_nada() {
        assertEquals(800_000L, aporteMensualSugerido(1_200_000L, 400_000L, 0))
        assertNull(aporteMensualSugerido(1_200_000L, 400_000L, -1))
    }

    @Test
    fun el_mes_se_lee_solo_con_su_forma() {
        assertEquals(PeriodoFinanciero(2027, 3), mesDeTexto("2027-03"))
        assertNull(mesDeTexto("marzo"))
        assertNull(mesDeTexto("2027-13"))
        assertNull(mesDeTexto("2027-3"))
        assertNull(mesDeTexto(null))
    }

    @Test
    fun el_gasto_va_entero_o_no_va() {
        assertNull(problemaDelGastoDelApartado(null, null, hoy))
        assertNull(problemaDelGastoDelApartado(1_200_000L, "2027-03", hoy))
        assertNotNull(problemaDelGastoDelApartado(1_200_000L, null, hoy))
        assertNotNull(problemaDelGastoDelApartado(null, "2027-03", hoy))
        assertNotNull(problemaDelGastoDelApartado(0L, "2027-03", hoy))
        assertNotNull(problemaDelGastoDelApartado(MAX_OBJETIVO_APARTADO + 1, "2027-03", hoy))
        // La hoja no deja elegir un mes que ya pasó; el server (sin `hoy`) deja editar uno viejo.
        assertNotNull(problemaDelGastoDelApartado(1_200_000L, "2026-09", hoy))
        assertNull(problemaDelGastoDelApartado(1_200_000L, "2026-09"))
    }

    /**
     * Las dos claves viajan siempre, también en `null`: así el server distingue «lo quitó» de «es un
     * APK que no conoce el campo». Se mira el JSON serializado, no el objeto.
     */
    @Test
    fun las_claves_del_gasto_viajan_aunque_valgan_null() {
        val json = Json.encodeToString(Apartado.serializer(), Apartado(accountId = "acc-nu", proposito = "SOAT", monto = 1L))
        val claves = Json.parseToJsonElement(json).jsonObject.keys
        assertTrue("objetivo" in claves, json)
        assertTrue("mesDelGasto" in claves, json)
    }
}
