package com.jvillada.movi.shared.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El catálogo de marcas: reconoce lo que tiene que reconocer, y nada más
 *
 * Los textos de los avisos imitan los reales de Bancolombia y Nu, con montos inventados. Ninguna
 * prueba toca la red: el catálogo es una tabla escrita a mano.
 */
class MarcasConocidasTest {

    private fun id(texto: String): String? = marcaDelTexto(texto)?.id

    @Test
    fun `reconoce los textos típicos de los avisos`() {
        assertEquals("rappi", id("Compraste \$27.500 en RAPPI*LAS DOCE"))
        assertEquals("d1", id("Pagaste en D1 SAS"))
        assertEquals("uber", id("UBER *TRIP"))
        assertEquals("netflix", id("NETFLIX.COM"))
        assertEquals("exito", id("Compraste \$85.200 en EXITO WOK con tu T.Cred *1234"))
        assertEquals("exito", id("Éxito Laureles"))
        assertEquals("mcdonalds", id("McDonald's Oviedo"))
        assertEquals("crepes_waffles", id("CREPES & WAFFLES PARQUE"))
        assertEquals("hm", id("H&M Santafé"))
        assertEquals("smart_fit", id("SMARTFIT MEDELLIN"))
        assertEquals("smart_fit", id("Smart Fit"))
        assertEquals("claude", id("ANTHROPIC* CLAUDE SUBSCRIPTION"))
        assertEquals("openai", id("OPENAI *CHATGPT SUBSCR"))
        assertEquals("mercado_libre", id("MERCPAGO*MERCADOLIBRE"))
        assertEquals("cruz_verde", id("Droguería Cruz Verde"))
    }

    @Test
    fun `no reconoce los falsos positivos de una palabra adentro de otra`() {
        assertNull(marcaDelTexto("Para el almuerzo"))
        assertNull(marcaDelTexto("Aclaro el pago"))
        assertNull(marcaDelTexto("Pago exitoso"))
        assertNull(marcaDelTexto("Transferencia a Caro"))
        assertNull(marcaDelTexto("Mercado de la semana"))
        assertNull(marcaDelTexto(""))
        // «Nu», «Visa» y «Porvenir» solo son el banco nombrando una cuenta.
        assertNull(marcaDelTexto("Visa para el viaje"))
        assertNull(marcaDelTexto("Ahorro para el porvenir"))
    }

    @Test
    fun `el orden importa cuando casan dos`() {
        assertEquals("rappi", id("Rappi · Las Doce"))
        assertEquals("rappipay", id("Pago Rappi Pay"))
        assertEquals("banco_falabella", id("Banco Falabella"))
        assertEquals("falabella", id("Falabella Santafé"))
        assertEquals("youtube", id("GOOGLE *YouTube"))
        assertEquals("prime_video", id("Amazon Prime Video"))
        assertEquals("exito", id("Compra en EXITO con tu tarjeta Bancolombia"))
        assertEquals("fiducuenta", marcaDelBanco("Fiducuenta Bancolombia")?.id)
        assertEquals("une", id("UNE EPM Telecomunicaciones"))
    }

    @Test
    fun `las cuentas y los avisos se reconocen por el banco`() {
        fun cuenta(nombre: String) = marcaDeLaCuenta(Account("a", nombre, AccountType.SAVINGS, 0L))?.id
        assertEquals("nu", cuenta("Nu"))
        assertEquals("bancolombia", cuenta("Bancolombia Ahorros"))
        assertEquals("fiducuenta", cuenta("Fiducuenta"))
        assertEquals("amex", cuenta("AMEX"))
        assertEquals("bancolombia", cuenta("Bancolombia Visa"))
        assertEquals("visa", cuenta("Visa"))
        assertEquals("banco_falabella", cuenta("Falabella"))
        assertEquals("skandia", cuenta("Skandia voluntaria"))
        assertEquals("davibank", cuenta("AFC Davibank 1234"))
        assertNull(cuenta("Efectivo"))
        assertNull(cuenta("Casa"))
        assertEquals("nu", marcaDelBanco("Notificación · Nu")?.id)
        assertEquals("bancolombia", marcaDelBanco("Correo · Bancolombia")?.id)
    }

    @Test
    fun `un movimiento se reconoce por el comercio y si no por la descripción`() {
        val base = FinancialEvent(
            id = "e", accountId = "a", type = TransactionType.EXPENSE, amount = 1_000L,
            category = "Comida", description = "Almuerzo", timestamp = 0L,
        )
        assertNull(marcaDe(base))
        assertEquals("rappi", marcaDe(base.copy(merchant = "RAPPI*LAS DOCE"))?.id)
        assertEquals("starbucks", marcaDe(base.copy(description = "Starbucks Oviedo"))?.id)
        assertEquals("starbucks", marcaDe(base.copy(merchant = "Comercio raro", description = "Starbucks"))?.id)
    }

    @Test
    fun `cubre al menos las sesenta marcas pedidas`() {
        assertTrue(COMERCIOS_CONOCIDOS.size >= 60, "comercios: ${COMERCIOS_CONOCIDOS.size}")
        assertTrue(BANCOS_CONOCIDOS.size >= 26, "bancos: ${BANCOS_CONOCIDOS.size}")
    }

    @Test
    fun `no hay ids duplicados`() {
        val repetidos = MARCAS_CONOCIDAS.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(repetidos.isEmpty(), "ids repetidos: $repetidos")
    }

    @Test
    fun `cada marca tiene una marca corta de uno a tres caracteres y al menos un patrón`() {
        for (m in MARCAS_CONOCIDAS) {
            assertTrue(m.corta.length in 1..3, "${m.id}: «${m.corta}»")
            assertTrue((m.patrones + m.patronesDeCuenta).isNotEmpty(), "${m.id} no se reconoce con nada")
            // Un patrón tiene que reconocer a su propia marca escrito tal cual.
            for (p in m.patrones) {
                assertEquals(m.id, marcaDelTexto(p)?.id ?: marcaDelBanco(p)?.id, "${m.id}: «$p»")
            }
        }
    }

    @Test
    fun `todas las marcas pasan contraste AA`() {
        val malas = MARCAS_CONOCIDAS.mapNotNull { m ->
            val c = contraste(m.fondo, m.texto)
            if (c < 4.5) "${m.id} (${(c * 100).toInt() / 100.0}:1)" else null
        }
        assertTrue(malas.isEmpty(), "no pasan 4,5:1: $malas")
    }

    // WCAG 2.x, como `ContrasteDeLosTokensTest` en `:shared` (que no se ve desde `:core`).
    private fun luminancia(argb: Long): Double {
        fun canal(v: Long): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * canal((argb shr 16) and 0xFF) + 0.7152 * canal((argb shr 8) and 0xFF) + 0.0722 * canal(argb and 0xFF)
    }

    private fun contraste(a: Long, b: Long): Double {
        val la = luminancia(a)
        val lb = luminancia(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
