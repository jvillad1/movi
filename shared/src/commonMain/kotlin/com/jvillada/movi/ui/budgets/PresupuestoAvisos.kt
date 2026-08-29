package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.isReservedCategory

/**
 * Lo que la hoja le dice al dueño sobre la categoría que está escribiendo, **antes** de que guarde.
 *
 * ### De dónde sale
 *
 * El dueño creó un presupuesto de $2.000.000 en la categoría «Mercado» y le extrañó que la app no
 * le avisara que ese dinero ya estaba gastado. La causa era peor de lo que él sospechaba: **no
 * existe la categoría «Mercado»**. En su gasto del súper, «Mercado» es la *descripción*; la
 * categoría es «Comida», que ese mes ya llevaba $2.113.575 — por encima del límite que estaba
 * poniendo.
 *
 * El cruce presupuesto↔gasto es por **nombre de categoría** (ver `spentByCategoryForMonth`), así
 * que ese presupuesto iba a decir «$0 gastado» para siempre. La app tenía todo para avisarle y lo
 * dejó crear un presupuesto ciego, en silencio.
 *
 * ### Qué NO hace
 *
 * No inventa una relación entre el presupuesto y un gasto puntual. El cruce por categoría está
 * bien; lo que faltaba era decir la verdad al momento de crearlo.
 *
 * Y **nunca bloquea**: presupuestar una categoría que todavía no tiene gasto es legítimo —alguien
 * que va a empezar a usarla—, así que esto informa y sugiere, no impide.
 */
data class AvisoDeCategoria(
    val texto: String,
    /** `true` cuando la categoría no tiene gasto: se pinta como advertencia, no como dato. */
    val esAdvertencia: Boolean,
    /** Categorías con gasto que el dueño quizás quiso decir, de mayor a menor. Vacío si no aplica. */
    val sugerencias: List<String> = emptyList(),
)

/** Cuántas alternativas se ofrecen: suficientes para reconocer la buena, pocas para leerlas. */
private const val MAX_SUGERENCIAS = 3

/**
 * @param categoria lo que el dueño escribió (puede venir a medio escribir o vacío).
 * @param gastoPorCategoria gasto del período por categoría, tal como lo muestra Presupuestos.
 * @param formatear cómo se escribe un monto — se inyecta para que esto no dependa de la UI.
 */
fun avisoDeCategoria(
    categoria: String,
    gastoPorCategoria: Map<String, Long>,
    formatear: (Long) -> String,
): AvisoDeCategoria? {
    val escrita = categoria.trim()
    // Sin nada escrito no hay nada que decir, y las reservadas las gobierna Movi: avisar ahí
    // sería ruido sobre una categoría que el dueño no debería estar presupuestando.
    if (escrita.isEmpty() || isReservedCategory(escrita)) return null

    val gastado = gastoPorCategoria[escrita] ?: 0L
    if (gastado > 0L) {
        return AvisoDeCategoria(
            texto = "Ya llevas ${formatear(gastado)} gastados en \"$escrita\" este mes.",
            esAdvertencia = false,
        )
    }

    // Sin gasto en esa categoría. Las alternativas son las que SÍ tienen, de mayor a menor:
    // el nombre que el dueño buscaba suele ser el primero de esa lista.
    val conGasto = gastoPorCategoria
        .filterValues { it > 0L }
        .entries
        .sortedByDescending { it.value }
        .take(MAX_SUGERENCIAS)

    if (conGasto.isEmpty()) {
        return AvisoDeCategoria(
            texto = "Todavía no tienes gastos registrados este mes.",
            esAdvertencia = false,
        )
    }

    return AvisoDeCategoria(
        texto = "No tienes gastos en \"$escrita\" este mes. " +
            "El gasto se cruza por nombre de categoría, así que este presupuesto no vigilaría nada. " +
            "Con gasto este mes: " + conGasto.joinToString(" · ") { "${it.key} ${formatear(it.value)}" } + ".",
        esAdvertencia = true,
        sugerencias = conGasto.map { it.key },
    )
}
