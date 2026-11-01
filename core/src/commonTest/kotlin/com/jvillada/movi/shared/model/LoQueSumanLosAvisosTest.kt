package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** **«Por confirmar $103.600»** (vuelta 44): la suma de lo que dicen los avisos, entera o nada. */
class LoQueSumanLosAvisosTest {

    private fun pesos(monto: Long) = MontoDelAviso(monto, "COP")

    @Test
    fun `suma lo que dicen los avisos en pesos`() {
        assertEquals(103_600L, sumaDeLoPorConfirmar(listOf(pesos(18_300), pesos(61_900), pesos(23_400))))
    }

    @Test
    fun `sin avisos no hay suma`() {
        assertNull(sumaDeLoPorConfirmar(emptyList()))
    }

    @Test
    fun `si uno no trae monto no se dice una suma a medias`() {
        assertNull(sumaDeLoPorConfirmar(listOf(pesos(18_300), null, pesos(23_400))))
        assertNull(sumaDeLoPorConfirmar(listOf(pesos(18_300), pesos(0))))
    }

    @Test
    fun `si uno es en otra moneda tampoco`() {
        assertNull(sumaDeLoPorConfirmar(listOf(pesos(18_300), MontoDelAviso(20, "USD"))))
    }

    @Test
    fun `un servidor viejo no la manda y un cliente viejo la ignora`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        // Sin el campo: nulo, y la franja dice el conteo.
        assertNull(json.decodeFromString(DashboardSummary.serializer(), """{"pendingSms":3}""").porConfirmarSuman)
        // Con el campo, viaja.
        assertEquals(
            103_600L,
            json.decodeFromString(DashboardSummary.serializer(), """{"pendingSms":3,"porConfirmarSuman":103600}""").porConfirmarSuman,
        )
    }

    // ── Vuelta 85 · con signo ────────────────────────────────────────────────────

    private fun entra(monto: Long) = MontoDelAviso(monto, "COP", entra = true)

    @Test
    fun `lo que sale y lo que entra se suman aparte`() {
        // El caso de la revisión 9: cuatro gastos y dos ingresos sumaban $1.632.025 «en 6 avisos».
        val avisos = listOf(pesos(32_000), pesos(214_900), entra(539_625), pesos(350_000), entra(480_000), pesos(15_500))
        assertEquals(1_632_025L, sumaDeLoPorConfirmar(avisos), "la suma de antes no cambia (APKs viejos)")
        assertEquals(
            LoPorConfirmarPorSigno(salen = 612_400, avisosQueSalen = 4, entran = 1_019_625, avisosQueEntran = 2),
            loPorConfirmarPorSigno(avisos),
        )
    }

    @Test
    fun `con signo tambien es entera o nada`() {
        assertNull(loPorConfirmarPorSigno(emptyList()))
        assertNull(loPorConfirmarPorSigno(listOf(pesos(18_300), null, entra(23_400))))
        assertNull(loPorConfirmarPorSigno(listOf(entra(18_300), MontoDelAviso(20, "USD", entra = true))))
        assertEquals(LoPorConfirmarPorSigno(0, 0, 480_000, 1), loPorConfirmarPorSigno(listOf(entra(480_000))))
    }

    @Test
    fun `un servidor viejo no manda la particion y un cliente viejo la ignora`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val viejo = json.decodeFromString(DashboardSummary.serializer(), """{"pendingSms":3,"porConfirmarSuman":103600}""")
        assertNull(viejo.porConfirmarSalen)
        assertNull(viejo.porConfirmarEntran)
        assertEquals(0, viejo.avisosQueEntran)
        val nuevo = json.decodeFromString(
            DashboardSummary.serializer(),
            """{"pendingSms":3,"porConfirmarSuman":742400,"porConfirmarSalen":262400,"porConfirmarEntran":480000,"avisosQueSalen":2,"avisosQueEntran":1}""",
        )
        assertEquals(262_400L, nuevo.porConfirmarSalen)
        assertEquals(480_000L, nuevo.porConfirmarEntran)
        assertEquals(2, nuevo.avisosQueSalen)
        assertEquals(1, nuevo.avisosQueEntran)
        // Y lo que manda un server nuevo lo lee un cliente que todavía no conoce esos campos.
        val comoViejo = json.decodeFromString(ResumenViejo.serializer(), json.encodeToString(DashboardSummary.serializer(), nuevo))
        assertEquals(742_400L, comoViejo.porConfirmarSuman)
    }
}

/** Lo que un APK de antes de la vuelta 85 lee de `GET /api/dashboard/summary`. */
@kotlinx.serialization.Serializable
private data class ResumenViejo(val pendingSms: Int = 0, val porConfirmarSuman: Long? = null)
