package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.effectiveCategoryTypes
import com.jvillada.movi.ui.components.formatCOP

/**
 * Las reglas puras de «Más → Categorías», separadas del `@Composable` para poder probarlas en
 * `:shared:commonTest` sin arrancar Compose — mismo criterio que `suggestCategoryMatches` y que
 * `RecurrentesLogic`.
 */

/**
 * Los cuatro filtros de la pantalla. **Son la respuesta a la pregunta que la abrió**: el dueño
 * preguntó si las listas de categorías deberían estar discriminadas por tipo, y la respuesta es
 * que el tipo es esto — un filtro sobre una sola lista — y no cuatro catálogos separados que se
 * desincronizan entre sí y obligan a tener «Otros» y «Otros ingresos» por duplicado.
 */
enum class CategoryFilter { TODAS, GASTOS, INGRESOS, ESCONDIDAS }

/** El rótulo de cada filtro, para las pastillas de arriba. */
fun etiquetaDeFiltro(filtro: CategoryFilter): String = when (filtro) {
    CategoryFilter.TODAS -> "Todas"
    CategoryFilter.GASTOS -> "Gastos"
    CategoryFilter.INGRESOS -> "Ingresos"
    CategoryFilter.ESCONDIDAS -> "Escondidas"
}

/** Los tipos que valen para esta categoría, ya resueltos (fijado > catálogo > uso). */
fun tiposEfectivos(c: CategoryUsage): Set<TransactionType> =
    effectiveCategoryTypes(c.name, c.pinnedType, c.usedTypes.toSet())

/**
 * Filtra y ordena la lista.
 *
 * - **Todas** muestra todo, escondidas incluidas (con su etiqueta): un filtro llamado «todas» que
 *   esconde cosas sería mentir, y además es donde el dueño va a buscar la que escondió por error.
 * - **Gastos / Ingresos** usan el tipo EFECTIVO, no el del catálogo. Una categoría sin evidencia
 *   de ningún lado (tipos vacíos) aparece en los dos: ante la duda, mostrar.
 * - **Escondidas** es la lista de lo que dejó de sugerirse — el único lugar desde el que se puede
 *   deshacer.
 *
 * [query] busca por nombre sin distinguir mayúsculas ni tildes, igual que las sugerencias.
 *
 * **El orden lo decide acá, alfabético** ([CATEGORY_NAME_ORDER]: sin tildes, sin mayúsculas, la ñ
 * después de la n). Antes se respetaba el que llega del server — lo más usado primero — y ese orden
 * tenía su razón escrita: la pregunta que trae al dueño a esta pantalla es «¿qué sobra?», y lo que
 * sobra se reconoce por contraste con lo que de verdad usa. La razón no se pierde con este cambio:
 * cada renglón sigue diciendo su uso («12 movimientos · $…» o «Sin movimientos»), así que lo que
 * sobra se sigue reconociendo de un vistazo. Lo que sí se ganó es poder **encontrar una categoría
 * por su nombre**: apenas la lista pasa de lo que entra en una pantalla, «lo más usado primero» es
 * indistinguible de «cualquier orden» para quien busca «Ñoquis».
 *
 * Se conservan dos cosas del orden viejo:
 * - **Las reservadas al final.** No se pueden tocar (ni renombrar, ni unificar, ni esconder), así
 *   que intercaladas entre las demás serían cuatro renglones muertos en medio de la lista.
 * - **Lo que empieza con lo buscado va antes que lo que apenas lo contiene**, igual que en las
 *   sugerencias de `CategoryField`: buscando «co», «Comida» arriba de «Bancolombia».
 */
fun filtrarCategorias(
    todas: List<CategoryUsage>,
    filtro: CategoryFilter,
    query: String = "",
): List<CategoryUsage> {
    val q = normalizar(query.trim())
    return todas
        .filter { c ->
            when (filtro) {
                CategoryFilter.TODAS -> true
                CategoryFilter.ESCONDIDAS -> c.hidden
                CategoryFilter.GASTOS -> !c.hidden && tiposEfectivos(c).let {
                    it.isEmpty() || TransactionType.EXPENSE in it
                }
                CategoryFilter.INGRESOS -> !c.hidden && tiposEfectivos(c).let {
                    it.isEmpty() || TransactionType.INCOME in it
                }
            }
        }
        .filter { q.isEmpty() || normalizar(it.name).contains(q) }
        .sortedWith(
            compareBy<CategoryUsage> { it.reserved }
                .thenBy { if (q.isEmpty() || normalizar(it.name).startsWith(q)) 0 else 1 }
                .thenBy(CATEGORY_NAME_ORDER) { it.name },
        )
}

/**
 * De qué tipo se muestra una categoría, en una palabra. Sale del tipo efectivo, así que dice lo
 * que el dueño fijó apenas lo fija — si dijera lo del catálogo, «Otros» seguiría diciendo «Gasto»
 * después de ponerla en «Ambos» y la pantalla se contradiría a sí misma.
 */
fun etiquetaDeTipo(c: CategoryUsage): String {
    val tipos = tiposEfectivos(c)
    return when {
        tipos.size > 1 -> "Ambos"
        tipos.singleOrNull() == TransactionType.EXPENSE -> "Gasto"
        tipos.singleOrNull() == TransactionType.INCOME -> "Ingreso"
        else -> "Sin usar"
    }
}

/**
 * El uso en un renglón: cuántos movimientos y cuánto suman **en total**. Es el dato por el que
 * existe esta pantalla — con una lista de nombres pelados no se puede decidir qué sobra.
 *
 * Los movimientos en otra moneda se cuentan aparte y no se suman: mezclar dólares y pesos en un
 * solo número sería mentir.
 */
fun resumenDeUso(c: CategoryUsage): String {
    if (!c.enUso) return "Sin movimientos"
    val partes = mutableListOf<String>()
    if (c.movements > 0) {
        partes += if (c.movements == 1) "1 movimiento" else "${c.movements} movimientos"
        // Gastos e ingresos **por separado**. Un solo total los sumaba en positivo (el signo lo
        // lleva `type`, no el importe), así que una categoría de tipo «Ambos» —el caso que esta
        // ola habilita— mostraba un número que no significaba nada, en la única pantalla que
        // existe para decidir mirando números. Si solo hay de un lado, se muestra una sola cifra.
        if (c.total > 0) partes += formatCOP(c.total)
        if (c.incomeTotal > 0) partes += "+${formatCOP(c.incomeTotal)} de ingresos"
    }
    if (c.otherCurrencyMovements > 0) {
        partes += if (c.otherCurrencyMovements == 1) "1 en otra moneda"
        else "${c.otherCurrencyMovements} en otra moneda"
    }
    if (c.budgets > 0) partes += "presupuesto"
    if (c.recurringRules > 0) {
        partes += if (c.recurringRules == 1) "1 recurrente" else "${c.recurringRules} recurrentes"
    }
    return partes.joinToString(" · ")
}

/** El uso de ESTE mes, en un renglón. `null` si no la usó este mes — no hay nada que decir. */
fun resumenDelMes(c: CategoryUsage): String? {
    if (c.monthMovements <= 0) return null
    val cuantos = if (c.monthMovements == 1) "1 movimiento" else "${c.monthMovements} movimientos"
    val plata = buildList {
        if (c.monthTotal > 0) add(formatCOP(c.monthTotal))
        if (c.monthIncomeTotal > 0) add("+${formatCOP(c.monthIncomeTotal)} de ingresos")
    }
    return (listOf("Este mes: $cuantos") + plata).joinToString(" · ")
}

/**
 * Lo que hay que decirle antes de unificar, con números. Se calcula acá y no en la hoja para
 * poder fijarlo por test: es el aviso de una operación que reescribe la historia del dueño, y no
 * puede quedar dependiendo de que alguien no rompa una interpolación.
 */
fun avisoDeUnificacion(origen: CategoryUsage, destino: CategoryUsage): String {
    val partes = mutableListOf<String>()
    val movimientos = origen.movements + origen.otherCurrencyMovements
    if (movimientos > 0) {
        partes += if (movimientos == 1) "1 movimiento" else "$movimientos movimientos"
    }
    if (origen.budgets > 0) partes += "su presupuesto"
    if (origen.recurringRules > 0) {
        partes += if (origen.recurringRules == 1) "1 recurrente" else "${origen.recurringRules} recurrentes"
    }
    val que = if (partes.isEmpty()) "Nada cambia de nombre: «${origen.name}» no tiene movimientos."
    else "${partes.joinToString(", ")} de «${origen.name}» pasan a decir «${destino.name}»."
    val base = "$que No se borra nada: los movimientos siguen ahí, con el nombre nuevo."
    // Lo único de toda la operación que NO es «el mismo dato con otro nombre»: si las dos tienen
    // presupuesto, los dos límites se suman en uno y los originales dejan de existir. Es
    // irreversible y le cambia un número que puso a propósito, así que se dice ANTES y con la
    // cifra final, no después.
    if (origen.budgets > 0 && destino.budgets > 0) {
        // Con la CIFRA, no con un «se suman» a secas: el dueño está por cambiar un límite que
        // puso a propósito, no puede deshacerlo, y «se suman» lo obliga a hacer la cuenta de
        // cabeza justo cuando lo que necesita es decidir. El monto viaja en `budgetLimit`.
        val suma = origen.budgetLimit + destino.budgetLimit
        return "$base Ojo: las dos tienen presupuesto y los dos límites se suman en uno solo — " +
            "el de «${destino.name}» queda en ${formatCOP(suma)}. Eso no se puede deshacer."
    }
    return base
}

/** El texto del selector de tipo, incluida la opción de no fijar nada. */
fun etiquetaDeTipoFijado(pinned: String?): String = when (pinned) {
    TransactionType.EXPENSE.name -> "Gasto"
    TransactionType.INCOME.name -> "Ingreso"
    CATEGORY_TYPE_BOTH -> "Ambos"
    else -> "Automático"
}

/**
 * Minúsculas y sin tildes/diéresis — misma normalización que las sugerencias de categoría (ver
 * `normalizeForMatch`), y **distinta de [CATEGORY_NAME_ORDER] en un solo punto**: para BUSCAR, la
 * `ñ` se aplasta contra la `n`; para ORDENAR va justo después de la n.
 */
private fun normalizar(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) {
        append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'; 'ñ' -> 'n'
                else -> c
            },
        )
    }
}
