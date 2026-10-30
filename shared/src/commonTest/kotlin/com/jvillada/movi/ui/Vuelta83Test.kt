package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.LARGO_MAXIMO_DEL_NOMBRE_CORTO
import com.jvillada.movi.ui.components.cuentaHabitual
import com.jvillada.movi.ui.components.nombreCortoDeCuenta
import com.jvillada.movi.ui.quickadd.GastoReciente
import com.jvillada.movi.ui.quickadd.gastosRecientesParaRepetir
import com.jvillada.movi.ui.quickadd.textoDelReciente
import com.jvillada.movi.ui.transactions.textosDeLaFilaEnLaLista
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 83 · filas densas y limpias, Agregar útil y Pagos sin repetir (lo puro)
 *
 * El nombre corto de una cuenta, la cuenta habitual que las filas no repiten y los recientes que
 * Agregar ofrece repetir. Lo que se ve en pantalla lo mide `Vuelta83EnPantallaTest`.
 */
class Vuelta83Test {

    // ── (1) El nombre corto de la cuenta ─────────────────────────────────────

    @Test
    fun `el banco y los ultimos cuatro`() {
        assertEquals("Bancolombia ·4057", nombreCortoDeCuenta("Bancolombia Ahorros 4057"))
        assertEquals("Fiducuenta ·9586", nombreCortoDeCuenta("Fiducuenta 9586"))
        assertEquals("Vehículo ·4083", nombreCortoDeCuenta("Vehículo 4083"))
        // Un número largo: sus últimos cuatro.
        assertEquals("Bancolombia ·8133", nombreCortoDeCuenta("Bancolombia 02955068133"))
        // Dos cuentas con el mismo número en dos monedas no se confunden.
        assertEquals("Master ·3684", nombreCortoDeCuenta("Master Black 3684"))
        assertEquals("Master ·3684 USD", nombreCortoDeCuenta("Master Black 3684 USD"))
    }

    @Test
    fun `sin numero, el nombre, o recortado por el medio`() {
        assertEquals("Nu", nombreCortoDeCuenta("Nu"))
        assertEquals("Efectivo", nombreCortoDeCuenta(" Efectivo "))
        assertEquals("Bancolombia Ahorros", nombreCortoDeCuenta("Bancolombia Ahorros"))
        val largo = nombreCortoDeCuenta("Cuenta de ahorros de la casa de Chía")
        assertTrue("…" in largo, largo)
        assertTrue(largo.length <= LARGO_MAXIMO_DEL_NOMBRE_CORTO, largo)
        assertTrue(largo.startsWith("Cuenta de"), "el principio se queda: $largo")
        assertTrue(largo.endsWith("de Chía"), "y el final también: $largo")
    }

    // ── (2) La cuenta solo cuando difiere ────────────────────────────────────

    private fun gasto(
        id: String,
        cuenta: String,
        descripcion: String,
        categoria: String = "Transporte",
        monto: Long = 8_400,
        ts: Long = 1_000L,
        tipo: TransactionType = TransactionType.EXPENSE,
        moneda: String = "COP",
        traspaso: String? = null,
        comercio: String? = null,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, currency = moneda, category = categoria,
        description = descripcion, merchant = comercio, timestamp = ts, transferId = traspaso,
    )

    private val nombres = mapOf("ahorros" to "Bancolombia Ahorros 4057", "nu" to "Nu")

    @Test
    fun `un gasto en la cuenta habitual no la nombra, uno en Nu si`() {
        val habitual = cuentaHabitual(listOf("ahorros", "ahorros", "nu"), masUsada = "ahorros")
        assertEquals("Transporte", textosDeLaFilaEnLaLista(gasto("a", "ahorros", "Uber Rides"), nombres, habitual).subtitulo)
        assertEquals("Transporte · Nu", textosDeLaFilaEnLaLista(gasto("b", "nu", "Uber Rides"), nombres, habitual).subtitulo)
        // Sin cuenta habitual, todas la nombran, corta.
        assertEquals("Transporte · Bancolombia ·4057", textosDeLaFilaEnLaLista(gasto("a", "ahorros", "Uber Rides"), nombres, null).subtitulo)
    }

    @Test
    fun `sin la cuenta la fila se quedaria sin subtitulo, y la nombra igual`() {
        // «Internet» con la descripción igual a la categoría y sin comercio: el subtítulo era la cuenta.
        val internet = gasto("i", "ahorros", "Internet", categoria = "Internet")
        assertEquals("Bancolombia ·4057", textosDeLaFilaEnLaLista(internet, nombres, "ahorros").subtitulo)
    }

    @Test
    fun `la cuenta habitual es la mas usada, o la que mas aparece, y con un empate ninguna`() {
        assertEquals("nu", cuentaHabitual(listOf("ahorros", "ahorros"), masUsada = "nu"))
        assertEquals("ahorros", cuentaHabitual(listOf("ahorros", "nu", "ahorros"), masUsada = null))
        assertNull(cuentaHabitual(listOf("ahorros", "nu"), masUsada = null))
        assertNull(cuentaHabitual(emptyList(), masUsada = null))
    }

    // ── (3) Repetir uno reciente ─────────────────────────────────────────────

    @Test
    fun `los recientes son los ultimos gastos distintos, del mas nuevo al mas viejo`() {
        val eventos = listOf(
            gasto("u1", "ahorros", "Uber Rides", monto = 7_900, ts = 1_000),
            gasto("u2", "ahorros", "Uber Rides", monto = 8_400, ts = 5_000),
            gasto("d1", "nu", "Tienda D1 Laureles", categoria = "Mercado", monto = 41_000, ts = 4_000),
            gasto("jv", "ahorros", "Juan Valdez Cafe", categoria = "Comida", monto = 13_900, ts = 3_000),
            gasto("rp", "ahorros", "Rappi", categoria = "Comida", monto = 27_500, ts = 2_000),
        )
        val recientes = gastosRecientesParaRepetir(eventos, cuentas = setOf("ahorros", "nu"))
        assertEquals(listOf("Uber Rides", "Tienda D1 Laureles", "Juan Valdez Cafe"), recientes.map { it.rotulo })
        // Dos Uber son uno, con el monto del último.
        assertEquals(8_400L, recientes.first().monto)
        assertEquals(GastoReciente("Tienda D1 Laureles", 41_000, "Mercado", "nu", "Tienda D1 Laureles"), recientes[1])
        assertEquals("Uber Rides · \$8.400", textoDelReciente(recientes.first()))
    }

    @Test
    fun `no ofrece lo que no se anotaria igual a mano`() {
        val eventos = listOf(
            gasto("ing", "ahorros", "Salario", categoria = "Salario", tipo = TransactionType.INCOME, monto = 5_000_000, ts = 9_000),
            gasto("tr", "ahorros", "Traspaso", categoria = "Traspaso", traspaso = "t1", ts = 8_000),
            gasto("cu", "ahorros", "Cuota de Vehículo 4083", categoria = CUOTA_CATEGORY, ts = 7_000),
            gasto("usd", "ahorros", "Netflix", categoria = "Suscripciones", moneda = "USD", ts = 6_000),
            gasto("otra", "tarjeta-vieja", "Cine", categoria = "Ocio", ts = 5_000),
            // La descripción es la categoría: el rótulo es el comercio, y el concepto también.
            gasto("me", "ahorros", "Mercado", categoria = "Mercado", comercio = "EXITO SUBA", ts = 4_000),
        )
        val recientes = gastosRecientesParaRepetir(eventos, cuentas = setOf("ahorros"))
        assertEquals(1, recientes.size, "$recientes")
        assertEquals("Éxito Suba", recientes.single().rotulo)
        assertEquals("Éxito Suba", recientes.single().concepto)
        assertTrue(gastosRecientesParaRepetir(emptyList(), setOf("ahorros")).isEmpty(), "sin recientes, nada")
    }
}
