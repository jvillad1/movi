package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Las frases de la propuesta del saldo de un extracto, con cifras inventadas. */
class ExtractoDeSaldoTest {

    private val skandia = ResumenDelExtractoDeSaldo(
        de = ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO,
        saldo = 12_345_679L,
        fechaDeCorte = "2026-08-31",
        contratos = listOf(ContratoDelExtracto("1111", 10_000_000L), ContratoDelExtracto("2222", 2_345_679L)),
    )
    private val cuenta = CuentaParaElSaldo(id = "acc", nombre = "Skandia FPV", saldoEnMovi = 12_000_000L)

    @Test
    fun `la frase dice lo del extracto y lo de Movi`() {
        assertEquals(
            "El extracto dice $12.345.679 al 31 de agosto de 2026; Movi tiene $12.000.000.",
            fraseDelExtractoDeSaldo(skandia, cuenta),
        )
        assertEquals("El extracto dice $12.345.679 al 31 de agosto de 2026.", fraseDelExtractoDeSaldo(skandia, null))
    }

    @Test
    fun `los contratos y el nombre`() {
        assertEquals(listOf("Contrato ·1111: $10.000.000", "Contrato ·2222: $2.345.679"), renglonesDeLosContratos(skandia))
        assertEquals("Fondo voluntario de pensión Skandia", nombreDelExtractoDeSaldo(skandia))
        assertEquals(
            "Cuenta de ahorros Davibank ·1111",
            nombreDelExtractoDeSaldo(skandia.copy(de = ExtractoDeSaldoDe.DAVIBANK_AHORROS, numero = "1111")),
        )
    }

    @Test
    fun `hay que actualizar solo con cuenta, con diferencia y sin cuadre posterior`() {
        assertTrue(hayQueActualizarElSaldo(skandia, cuenta))
        assertFalse(hayQueActualizarElSaldo(skandia, null))
        assertFalse(hayQueActualizarElSaldo(skandia, cuenta.copy(saldoEnMovi = 12_345_679L)))
        assertFalse(hayQueActualizarElSaldo(skandia, cuenta.copy(cuadradaDespues = true)))
    }

    @Test
    fun `la cuenta nombrada sale de las opciones`() {
        val p = PropuestaDelExtractoDeSaldo(skandia, cuentaId = "acc", opciones = listOf(cuenta))
        assertEquals(cuenta, cuentaNombrada(p))
        assertNull(cuentaNombrada(p.copy(cuentaId = null)))
    }
}
