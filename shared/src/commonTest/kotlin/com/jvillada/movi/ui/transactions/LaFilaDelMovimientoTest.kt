package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.montoContado
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta de diseño 30: en un gasto repartido la cifra principal es tu parte («de $88.600 · con Caro»
 * debajo), y «Mercado / Mercado» no repite la categoría. Presentación pura: nada de lo guardado cambia.
 */
class LaFilaDelMovimientoTest {

    private fun gasto(descripcion: String = "Mercado", categoria: String = "Mercado", merchant: String? = null, parteAjena: Long = 0L) =
        FinancialEvent(
            id = "e1", accountId = "acc-nu", type = TransactionType.EXPENSE, amount = 88_600, category = categoria,
            description = descripcion, timestamp = 0L, merchant = merchant, parteAjena = parteAjena,
            espacioId = if (parteAjena > 0) "esp_casa" else null,
        )

    private val conCaro = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_caro", "Caro")))
    private val deTres = Espacio(
        id = "esp_casa", nombre = "Apartamento",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("a", "Caro"), MiembroDelEspacio("b", "Nata")),
    )

    @Test
    fun `en un gasto repartido la cifra es tu parte y debajo de cuanto y con quien`() {
        val repartido = gasto(parteAjena = 44_300)
        assertEquals(44_300, montoPrincipalDelMovimiento(repartido))
        assertEquals(montoContado(repartido), montoPrincipalDelMovimiento(repartido), "la misma regla que los totales")
        assertEquals("de \$88.600 · con Caro", lineaDelRepartido(repartido, conCaro))
        assertEquals("de \$88.600 · Apartamento", lineaDelRepartido(repartido, deTres))
        assertEquals("de \$88.600", lineaDelRepartido(repartido, null), "sin el espacio, solo el total")
        assertEquals("Salieron \$88.600 de Nu", salieronDeLaCuenta(repartido, "Nu"))
        // El monto del movimiento (lo que mueve el saldo) no cambia.
        assertEquals(88_600, repartido.amount)
    }

    @Test
    fun `un gasto sin repartir sigue igual`() {
        val solo = gasto()
        assertEquals(88_600, montoPrincipalDelMovimiento(solo))
        assertNull(lineaDelRepartido(solo, conCaro))
        assertNull(salieronDeLaCuenta(solo, "Nu"))
        assertFalse(esGastoRepartido(solo.copy(type = TransactionType.INCOME, parteAjena = 10)))
    }

    @Test
    fun `la fila de siempre dice la descripcion y categoria con la cuenta`() {
        assertEquals(TextosDeLaFila("Rappi", "Restaurantes · Nu"), textosDeLaFila(gasto("Rappi", "Restaurantes"), "Nu"))
        assertEquals(TextosDeLaFila("Rappi", "Restaurantes"), textosDeLaFila(gasto("Rappi", "Restaurantes"), null))
    }

    @Test
    fun `Mercado sobre Mercado muestra el comercio o la cuenta y no repite la categoria`() {
        assertTrue(laDescripcionEsLaCategoria(gasto(" mercado ", "Mercado")))
        assertEquals(TextosDeLaFila("Éxito", "Mercado · Nu"), textosDeLaFila(gasto(merchant = "Éxito"), "Nu"))
        assertEquals(TextosDeLaFila("Nu", "Mercado"), textosDeLaFila(gasto(), "Nu"))
        assertEquals(TextosDeLaFila("Nu", "Mercado"), textosDeLaFila(gasto(merchant = "MERCADO"), "Nu"), "un comercio igual a la categoría no sirve")
        assertEquals(TextosDeLaFila("Mercado", null), textosDeLaFila(gasto(), null))
    }

    /** Vuelta de diseño 32: lo que el banco manda en mayúsculas se lee en forma de título; lo guardado no cambia. */
    @Test
    fun `el comercio en mayusculas se lee en forma de titulo, y lo guardado no cambia`() {
        val exito = gasto("EXITO SUBA", "Mercado")
        assertEquals(TextosDeLaFila("Éxito Suba", "Mercado · Nu"), textosDeLaFila(exito, "Nu"))
        assertEquals("EXITO SUBA", exito.description, "es solo presentación")
        assertEquals(TextosDeLaFila("Éxito Suba", "Mercado · Nu"), textosDeLaFila(gasto(merchant = "EXITO SUBA"), "Nu"))
        assertEquals("Pago EPM", descripcionParaMostrar(gasto("PAGO EPM", "Servicios")))
        assertEquals("Almuerzo con el equipo", descripcionParaMostrar(gasto("Almuerzo con el equipo", "Comida")))
    }
}
