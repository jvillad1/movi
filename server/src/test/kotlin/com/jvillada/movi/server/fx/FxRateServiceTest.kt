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

    @Test
    fun `parseTrm rejects implausible rates so fallback can engage`() {
        assertNull(FxRateService.parseTrm("""[{"valor":"0","vigenciadesde":"2026-06-08T00:00:00.000"}]"""))
        assertNull(FxRateService.parseTrm("""[{"valor":"-5","vigenciadesde":"2026-06-08T00:00:00.000"}]"""))
        assertNull(FxRateService.parseTrm("""[{"valor":"39505500","vigenciadesde":"2026-06-08T00:00:00.000"}]"""))
    }
}
