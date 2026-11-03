package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Lo que se desplaza se anota para el teclado (vuelta 118)
 *
 * En la web las flechas, Av Pág / Re Pág, el espacio, Inicio y Fin mueven el desplazamiento que la
 * pantalla de arriba (o la hoja abierta) anotó (`ui/components/DesplazarConElTeclado.kt`). Una pantalla
 * o una hoja nueva que se desplace sin anotarse queda sorda al teclado sin que nadie lo note —que es
 * exactamente lo que le pasó a Claudia—. Dos guardas:
 *
 * 1. `verticalScroll(rememberScrollState())` a secas solo queda en las listas de adentro que no
 *    deben mover el teclado ([LISTAS_DE_ADENTRO]); lo demás usa `desplazableConElTeclado()`.
 * 2. Todo archivo con una hoja (`MarcoDeHoja(`) o una pantalla (`fun …Screen(`) que se desplace anota
 *    algo (`desplazableConElTeclado`, `DesplazarConElTeclado`, `rememberListaConElTeclado` o
 *    `ScrollDesdeLosMargenes`, que también lo anota).
 */
class LoQueSeDesplazaSeAnotaParaElTecladoTest {

    /** Listas de adentro, a propósito sin el teclado: no son el desplazamiento principal de su capa. */
    private val LISTAS_DE_ADENTRO = mapOf(
        // El panel lateral del resumen en Actividad ancha: el teclado mueve la lista de movimientos.
        "transactions/ResumenEnElPanel.kt" to 1,
        // El buscador de categorías de 260 dp dentro de una hoja: la hoja ya anota su cuerpo.
        "categorias/CategoriasScreen.kt" to 1,
        // La columna de la derecha de un grupo en pantalla ancha: el teclado mueve la de la izquierda.
        "espacios/EspaciosScreen.kt" to 1,
    )

    private val crudo = Regex("""verticalScroll\(rememberScrollState\(\)\)""")
    private val seDesplaza = Regex("""verticalScroll\(|LazyColumn\(|desplazableConElTeclado\(""")
    private val seAnota = Regex("""desplazableConElTeclado\(|DesplazarConElTeclado\(|rememberListaConElTeclado\(|ScrollDesdeLosMargenes\(""")
    private val esCapa = Regex("""MarcoDeHoja\(|fun \w+Screen\(""")

    private fun archivos() = LecturaDelCodigo.archivos(LecturaDelCodigo.UI)
        .mapKeys { (ruta, _) -> ruta.substringAfter("${LecturaDelCodigo.UI}/") }

    @Test
    fun `verticalScroll a secas solo en las listas de adentro`() {
        val cuantos = archivos()
            .mapValues { (_, texto) -> crudo.findAll(texto).count() }
            .filterValues { it > 0 }
        assertEquals(
            LISTAS_DE_ADENTRO,
            cuantos,
            "Un `verticalScroll(rememberScrollState())` nuevo: si es el cuerpo de una pantalla o de una hoja, " +
                "usa `Modifier.desplazableConElTeclado()` (ui/components/DesplazarConElTeclado.kt); si es una " +
                "lista de adentro, agrégala a LISTAS_DE_ADENTRO con el porqué.",
        )
    }

    @Test
    fun `cada pantalla y cada hoja que se desplaza se anota`() {
        val sorda = archivos()
            .filter { (ruta, texto) -> !ruta.startsWith("components/") && esCapa.containsMatchIn(texto) }
            .filter { (_, texto) -> seDesplaza.containsMatchIn(texto) && !seAnota.containsMatchIn(texto) }
            .keys
        assertTrue(
            sorda.isEmpty(),
            "Pantallas u hojas que se desplazan sin anotarse para el teclado (ver DesplazarConElTeclado.kt):\n" +
                sorda.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `el detector ve lo crudo y lo anotado`() {
        assertTrue(crudo.containsMatchIn("Column(Modifier.verticalScroll(rememberScrollState()).weight(1f))"))
        assertTrue(!crudo.containsMatchIn("Column(Modifier.desplazableConElTeclado().weight(1f))"))
        assertTrue(seAnota.containsMatchIn("LazyColumn(state = rememberListaConElTeclado()) {"))
        assertTrue(esCapa.containsMatchIn("fun PorRevisarScreen(onNavigate: (Screen) -> Unit) {"))
    }
}
