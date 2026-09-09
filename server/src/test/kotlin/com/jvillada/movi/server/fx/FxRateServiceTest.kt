package com.jvillada.movi.server.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FxRateServiceTest {

    @Test
    fun `parseTrm reads valor from a Socrata TRM payload`() {
        val body = """[{"valor":"3950.55","unidad":"COP","vigenciadesde":"2026-06-08T00:00:00.000"}]"""
        assertEquals(3950.55, FxRateService.parseTrm(body))
    }

    @Test
    fun `parseTrm picks the most recent row when several are returned`() {
        val body = """
          [{"valor":"3900.00","vigenciadesde":"2026-06-07T00:00:00.000"},
           {"valor":"3950.55","vigenciadesde":"2026-06-08T00:00:00.000"}]
        """.trimIndent()
        assertEquals(3950.55, FxRateService.parseTrm(body))
    }

    @Test
    fun `parseTrm returns null for garbage`() {
        assertNull(FxRateService.parseTrm("not json"))
        assertNull(FxRateService.parseTrm("[]"))
    }

    /**
     * **Solo el último eslabón es un relleno, y se marca.** La cadena entera devuelve un `Double`
     * positivo pase lo que pase, así que sin este dato nadie puede distinguir la TRM de hoy de la
     * constante de $4.000 — y hay un lugar donde esa diferencia cuesta plata: `minimoEnPesos`
     * prefiere no convertir un mínimo en dólares antes que restarlo del disponible con un número
     * que no eligió nadie.
     */
    @Test
    fun `la constante del codigo se declara respaldo, los otros tres eslabones no`() {
        assertEquals(
            TasaUsdCop(3950.55, esRespaldo = false),
            FxRateService.resolverTasa(fetched = 3950.55, cacheada = 3900.0, delEntorno = 3800.0),
        )
        assertEquals(
            TasaUsdCop(3900.0, esRespaldo = false),
            FxRateService.resolverTasa(fetched = null, cacheada = 3900.0, delEntorno = 3800.0),
            "la de ayer envejece, pero la escribió la Superfinanciera",
        )
        assertEquals(
            TasaUsdCop(3800.0, esRespaldo = false),
            FxRateService.resolverTasa(fetched = null, cacheada = null, delEntorno = 3800.0),
            "USD_COP_RATE la escribió alguien a propósito",
        )
        // Fuente caída y `USD_COP_RATE` sin configurar (es opcional): esto es lo único que queda.
        assertEquals(
            TasaUsdCop(4000.0, esRespaldo = true),
            FxRateService.resolverTasa(fetched = null, cacheada = null, delEntorno = null),
        )
    }

    @Test
    fun `parseTrm rejects implausible rates so fallback can engage`() {
        assertNull(FxRateService.parseTrm("""[{"valor":"0","vigenciadesde":"2026-06-08T00:00:00.000"}]"""))
        assertNull(FxRateService.parseTrm("""[{"valor":"-5","vigenciadesde":"2026-06-08T00:00:00.000"}]"""))
        assertNull(FxRateService.parseTrm("""[{"valor":"39505500","vigenciadesde":"2026-06-08T00:00:00.000"}]"""))
    }
}
