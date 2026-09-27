package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.normalizarParaBuscar

/**
 * **Las pastillas de categoría de cualquier pantalla que las muestre** — una sola regla, para que
 * dos pantallas no vuelvan a ofrecerle al dueño listas distintas (Ola L: el detalle de un SMS
 * armaba la suya alfabética y con tope, y «Hija» y «Fútbol» quedaban fuera del corte).
 *
 * El orden, de más a menos cerca del dedo:
 *
 * 1. [primeras]: lo que la pantalla quiere adelante — la propuesta de Movi, o la categoría que ya
 *    tiene el movimiento. Va tal cual, aunque no esté en ningún catálogo (una importada), y sin
 *    pasar por el filtro de tipo/escondidas: es lo que YA está puesto o propuesto.
 * 2. **Las frecuentes**, por uso reciente ([categoriasFrecuentes], las mismas de los chips de
 *    «Agregar»).
 * 3. **Las propias** que no se usaron hace poco, alfabéticas: siguen siendo suyas.
 * 4. **El catálogo**, alfabético.
 *
 * Todo lo que no es de [primeras] pasa por el filtro de siempre ([suggestCategoryMatches] /
 * [seOfreceParaTipo]): nada reservado, nada escondido, nada del otro tipo. Sin [tipo] (un SMS que
 * todavía no se leyó) no hay frecuentes —no se sabe de qué lado— y el resto se ofrece sin filtrar
 * por lado.
 *
 * Pura, para probarla sin pintar nada.
 */
fun categoriasParaPastillas(
    primeras: List<String?>,
    tipo: TransactionType?,
    usadas: Map<String, Set<TransactionType>>,
    prefs: Map<String, CategoryPref>,
    usos: Map<String, Int>,
    cuantas: Int = 12,
): List<String> {
    val frecuentes = if (tipo == null) {
        emptyList()
    } else {
        categoriasFrecuentes(tipo, usadas, prefs, usos, cuantas = Int.MAX_VALUE)
    }
    val delCatalogo = PREDEFINED_CATEGORIES.map { normalizarParaBuscar(it.name) }.toSet()
    val (catalogo, propias) = suggestCategoryMatches("", tipo, usadas, prefs)
        .partition { normalizarParaBuscar(it) in delCatalogo }

    val vistas = mutableSetOf<String>()
    return (primeras.filterNotNull().map { it.trim() } + frecuentes + propias + catalogo)
        .filter { it.isNotEmpty() && vistas.add(normalizarParaBuscar(it)) }
        .take(cuantas)
}
