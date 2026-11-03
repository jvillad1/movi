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
 * 1. **Lista por lista**: cada `verticalScroll(x)` y cada `LazyColumn(…)` de `ui/` está anotado
 *    (`desplazableConElTeclado`, `state = rememberListaConElTeclado()`, o su estado pasado a
 *    `DesplazarConElTeclado(…)` / `ScrollDesdeLosMargenes(…)` en el mismo archivo), salvo las listas
 *    de [SIN_TECLADO], contadas por archivo y cada una con su porqué.
 * 2. Todo archivo con una hoja (`MarcoDeHoja(`) o una pantalla (`fun …Screen(`) que se desplace anota
 *    algo.
 */
class LoQueSeDesplazaSeAnotaParaElTecladoTest {

    /**
     * Las listas que a propósito no mueve el teclado, por archivo: no son la principal de su capa, o
     * están fuera de la cáscara (donde el teclado no llega).
     */
    private val SIN_TECLADO = mapOf(
        // Patrimonio ancho: la columna del resumen; manda la de las cuentas.
        "accounts/AccountsScreen.kt" to 1,
        // La lista de conversaciones: en la hoja la anota quien la llama; en la columna ancha manda el chat.
        "ai/LasConversaciones.kt" to 1,
        // En qué se va ancho: la columna del resumen y la gráfica; manda la de la lista.
        "analisis/EnQueSeVaScreen.kt" to 1,
        // La puerta de la huella se dibuja fuera de la cáscara, encima de todo.
        "auth/PuertaAlVolver.kt" to 1,
        // El buscador de categorías de 260 dp dentro de una hoja: la hoja ya anota su cuerpo.
        "categorias/CategoriasScreen.kt" to 1,
        // Una lista quieta (`userScrollEnabled = false`) dentro de la página de Créditos.
        "credits/CreditosScreen.kt" to 1,
        // Un grupo ancho: la columna del saldo; manda la de los gastos.
        "espacios/EspaciosScreen.kt" to 1,
        // Tú ancho: la columna de Ajustes; manda la de «Lo tuyo».
        "mas/MasScreen.kt" to 1,
        // Pagos ancho: la columna del Disponible; manda la de las facturas.
        "plan/PlanScreen.kt" to 1,
        // Por revisar ancho: la columna de lo demás; manda la de los avisos.
        "porrevisar/PorRevisarScreen.kt" to 1,
        // El selector de 360 dp de Agregar: la hoja anota su cuerpo.
        "quickadd/QuickAddScreen.kt" to 1,
        // El panel lateral del resumen en Actividad ancha: manda la lista de movimientos.
        "transactions/ResumenEnElPanel.kt" to 1,
    )

    private val seDesplaza = Regex("""verticalScroll\(|LazyColumn\(|desplazableConElTeclado\(""")
    private val seAnota = Regex("""desplazableConElTeclado\(|DesplazarConElTeclado\(|rememberListaConElTeclado\(|ScrollDesdeLosMargenes\(""")
    private val esCapa = Regex("""MarcoDeHoja\(|fun \w+Screen\(""")
    private val anotacion = Regex("""\b(?:DesplazarConElTeclado|ScrollDesdeLosMargenes)\(""")
    private val nombre = Regex("""[A-Za-z_][\w.]*(?:\(\))?""")
    private val estadoDeLaLista = Regex("""\bstate\s*=\s*([A-Za-z_][\w.]*(?:\(\))?)""")

    private fun archivos() = LecturaDelCodigo.archivos(LecturaDelCodigo.UI)
        .mapKeys { (ruta, _) -> ruta.substringAfter("${LecturaDelCodigo.UI}/") }

    /** Lo que va entre el paréntesis que abre en [desde] y el que lo cierra. */
    private fun argumentos(texto: String, desde: Int): String {
        var profundidad = 0
        for (i in desde until texto.length) {
            when (texto[i]) {
                '(' -> profundidad++
                ')' -> if (--profundidad == 0) return texto.substring(desde + 1, i)
            }
        }
        return ""
    }

    /** Cuántas listas de [texto] no están anotadas para el teclado. */
    internal fun listasSinAnotar(texto: String): Int {
        val anotados = anotacion.findAll(texto)
            .flatMap { nombre.findAll(argumentos(texto, it.range.last)).map { n -> n.value } }
            .toSet()
        val columnas = Regex("""\bverticalScroll\(""").findAll(texto)
            .count { argumentos(texto, it.range.last).trim() !in anotados }
        val listas = Regex("""\bLazyColumn\(""").findAll(texto).count {
            val estado = estadoDeLaLista.find(argumentos(texto, it.range.last))?.groupValues?.get(1)
            estado != "rememberListaConElTeclado()" && estado !in anotados
        }
        return columnas + listas
    }

    @Test
    fun `cada lista esta anotada salvo las de SIN_TECLADO`() {
        val sinAnotar = archivos()
            .mapValues { (_, texto) -> listasSinAnotar(texto) }
            .filterValues { it > 0 }
        assertEquals(
            SIN_TECLADO,
            sinAnotar,
            "Una lista nueva sin el teclado: si es el cuerpo de una pantalla o de una hoja, usa " +
                "`Modifier.desplazableConElTeclado()` o `state = rememberListaConElTeclado()` " +
                "(ui/components/DesplazarConElTeclado.kt); si no es la principal de su capa, agrégala a " +
                "SIN_TECLADO con el porqué.",
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
    fun `el detector ve lo crudo, lo nombrado y lo anotado`() {
        assertEquals(1, listasSinAnotar("Column(Modifier.verticalScroll(rememberScrollState()).weight(1f))"))
        assertEquals(0, listasSinAnotar("Column(Modifier.desplazableConElTeclado().weight(1f))"))
        // Un estado con nombre: sin anotar cuenta, anotado no.
        assertEquals(1, listasSinAnotar("val s = rememberScrollState()\nColumn(Modifier.verticalScroll(s))"))
        assertEquals(0, listasSinAnotar("val s = rememberScrollState()\nDesplazarConElTeclado(s)\nColumn(Modifier.verticalScroll(s))"))
        // Dos listas en el mismo archivo: la anotada no tapa a la otra.
        assertEquals(
            1,
            listasSinAnotar(
                "LazyColumn(state = rememberListaConElTeclado(), modifier = m) { a() }\n" +
                    "LazyColumn(modifier = m) { b() }",
            ),
        )
        assertEquals(1, listasSinAnotar("LazyColumn(\n    state = otra,\n    modifier = m,\n) { }"))
        assertEquals(0, listasSinAnotar("ScrollDesdeLosMargenes(if (dos) col else lista)\nLazyColumn(state = lista) { }"))
        assertTrue(esCapa.containsMatchIn("fun PorRevisarScreen(onNavigate: (Screen) -> Unit) {"))
    }
}
