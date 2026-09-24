package com.jvillada.movi.ui.components

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola B · Task 4: [contenidoDelSelectorDeCategoria] es lo que decide qué celdas muestra la
 * cuadrícula de categorías y en qué orden. El filtro de fondo es [suggestCategoryMatches], que ya
 * tiene sus pruebas en [CategoryFieldTest]; acá se prueba lo que la cuadrícula agrega encima: las
 * frecuentes primero, las celdas «Crear» y «Usar», y el aviso de reservada.
 */
class SelectorDeCategoriaTest {

    private val gasto = TransactionType.EXPENSE

    /** Los nombres de las categorías existentes, en orden — sin las celdas «Crear» y «Usar». */
    private fun nombres(
        busqueda: String,
        usadas: Map<String, Set<TransactionType>> = emptyMap(),
        prefs: Map<String, CategoryPref> = emptyMap(),
        usos: Map<String, Int> = emptyMap(),
        tipo: TransactionType? = gasto,
    ) = contenidoDelSelectorDeCategoria(busqueda, tipo, usadas, prefs, usos).celdas
        .filterIsInstance<CeldaDeCategoria.Existente>()
        .map { it.nombre }

    @Test
    fun `sin usos, la cuadricula es la lista alfabetica de siempre`() {
        assertEquals(
            suggestCategoryMatches("", TransactionType.INCOME),
            nombres("", tipo = TransactionType.INCOME),
        )
    }

    @Test
    fun `las frecuentes van primero y despues el resto alfabetico, sin repetir`() {
        val usos = mapOf("Transporte" to 9, "Mercado" to 4)
        val usadas = mapOf("Mercado" to setOf(gasto))
        val celdas = nombres("", usadas = usadas, usos = usos)

        assertEquals(listOf("Transporte", "Mercado"), celdas.take(2))
        val resto = celdas.drop(2)
        assertEquals(resto.sortedWith(com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER), resto)
        assertEquals(celdas.distinct(), celdas)
        assertFalse("Transporte" in resto)
    }

    @Test
    fun `hasta ocho frecuentes, la novena va en su lugar alfabetico`() {
        val propias = (1..9).map { "Propia $it" }
        val usos = propias.withIndex().associate { (i, nombre) -> nombre to 100 - i }
        val usadas = propias.associateWith { setOf(gasto) }
        val celdas = nombres("", usadas = usadas, usos = usos)

        assertEquals(propias.take(FRECUENTES_EN_EL_SELECTOR), celdas.take(FRECUENTES_EN_EL_SELECTOR))
        // «Propia 9» no es de las 8: queda con el resto, en orden alfabético.
        val resto = celdas.drop(FRECUENTES_EN_EL_SELECTOR)
        assertTrue("Propia 9" in resto)
        assertEquals(resto.sortedWith(com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER), resto)
    }

    @Test
    fun `una escondida o una reservada no aparecen ni como frecuentes`() {
        val usos = mapOf("Mercado" to 10, TRANSFER_CATEGORY to 30, "Cine" to 2)
        val usadas = mapOf("Mercado" to setOf(gasto), "Cine" to setOf(gasto))
        val prefs = mapOf("Mercado" to CategoryPref(hidden = true))
        val celdas = nombres("", usadas = usadas, prefs = prefs, usos = usos)

        assertEquals("Cine", celdas.first())
        assertFalse("Mercado" in celdas)
        assertFalse(TRANSFER_CATEGORY in celdas)
        assertFalse(CARD_PAYMENT_CATEGORY in celdas)
    }

    @Test
    fun `una frecuente de ingreso no aparece al anotar un gasto`() {
        val usos = mapOf("Arriendo Gardenera" to 8)
        val usadas = mapOf("Arriendo Gardenera" to setOf(TransactionType.INCOME))
        assertFalse("Arriendo Gardenera" in nombres("", usadas = usadas, usos = usos))
        assertEquals("Arriendo Gardenera", nombres("", usadas = usadas, usos = usos, tipo = TransactionType.INCOME).first())
    }

    @Test
    fun `escribir filtra con la misma normalizacion de siempre`() {
        // (Era `el panel sigue filtrando cuando lo escrito NO es una categoria conocida`.)
        assertEquals(listOf("Transporte"), nombres("Trans"))
        assertEquals(listOf("Transporte"), nombres("TRANSPÓRTE"))
    }

    @Test
    fun `con algo escrito, las frecuentes que coinciden siguen primero y las otras se van`() {
        val usos = mapOf("Colegio" to 5, "Mercado" to 9)
        val usadas = mapOf("Colegio" to setOf(gasto), "Mercado" to setOf(gasto))
        val celdas = nombres("co", usadas = usadas, usos = usos)

        assertEquals("Colegio", celdas.first())
        assertFalse("Mercado" in celdas)
    }

    @Test
    fun `lo que no existe ofrece Crear, primero`() {
        val contenido = contenidoDelSelectorDeCategoria("Carro", gasto)
        assertEquals(CeldaDeCategoria.Crear("Carro"), contenido.celdas.first())
        assertNull(contenido.reservadaEscrita)
    }

    @Test
    fun `coincidencia parcial - Crear primero y la que coincide despues`() {
        val celdas = contenidoDelSelectorDeCategoria("Trans", gasto).celdas
        assertEquals(listOf(CeldaDeCategoria.Crear("Trans"), CeldaDeCategoria.Existente("Transporte")), celdas)
    }

    @Test
    fun `coincidencia exacta sin tildes ni mayusculas no ofrece Crear`() {
        val celdas = contenidoDelSelectorDeCategoria("  transpórte ", gasto).celdas
        assertEquals(listOf(CeldaDeCategoria.Existente("Transporte")), celdas)
    }

    @Test
    fun `lo que existe del otro lado se ofrece como Usar, con el porque`() {
        val celdas = contenidoDelSelectorDeCategoria("salario", gasto).celdas
        assertEquals(CeldaDeCategoria.Usar("Salario", "Ya la tienes en Ingresos"), celdas.first())
        assertTrue(celdas.none { it is CeldaDeCategoria.Crear })
    }

    @Test
    fun `una escondida no esconde a las demas, y escribirla ofrece usarla`() {
        // (Era `con una escondida escrita, el panel sigue mostrando todas las demas`: el campo
        // viejo arrancaba prellenado con la categoría actual, y si era una escondida el panel
        // quedaba en una sola fila. La búsqueda de ahora arranca vacía.)
        val prefs = mapOf("Comida" to CategoryPref(hidden = true))
        val vacia = nombres("", prefs = prefs)
        assertFalse("Comida" in vacia)
        assertTrue("Transporte" in vacia)

        val escrita = contenidoDelSelectorDeCategoria("Comida", gasto, prefs = prefs).celdas
        assertEquals(CeldaDeCategoria.Usar("Comida", "La escondiste en Categorías; puedes usarla igual"), escrita.single())
    }

    @Test
    fun `una reservada escrita se avisa y no se ofrece ni crear ni usar`() {
        val contenido = contenidoDelSelectorDeCategoria("pago de tarjeta", gasto)
        assertEquals("pago de tarjeta", contenido.reservadaEscrita)
        assertTrue(contenido.celdas.none { it !is CeldaDeCategoria.Existente })
        assertFalse(contenido.celdas.any { it.nombre.equals(CARD_PAYMENT_CATEGORY, ignoreCase = true) })
    }

    @Test
    fun `las columnas salen como en GridCells Adaptive de 80 dp`() {
        val espacio = 4.dp
        // Un teléfono de 375 dp menos los 20 dp de margen de cada lado de la hoja.
        assertEquals(4, columnasDeLaCuadricula(335.dp, espacio))
        assertEquals(4, columnasDeLaCuadricula(371.dp, espacio)) // el AVD de 411 dp
        assertEquals(1, columnasDeLaCuadricula(80.dp, espacio))
        assertEquals(1, columnasDeLaCuadricula(10.dp, espacio)) // nunca cero columnas
    }
}
