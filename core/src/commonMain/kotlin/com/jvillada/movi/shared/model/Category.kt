package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class CategoryScope { PREDEFINED, CUSTOM }

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,       // "INCOME" | "EXPENSE" | "BOTH"
    val scope: CategoryScope = CategoryScope.PREDEFINED,
)

/**
 * El catálogo fijo.
 *
 * **Este orden NO es el orden en que se muestran.** Toda lista visible de categorías se ordena
 * alfabéticamente al mostrarla (ver [CATEGORY_NAME_ORDER], `suggestCategoryMatches` y
 * `filtrarCategorias`), justamente porque este de acá es el orden en que alguien las escribió y
 * el dueño lo leyó como «cualquier orden».
 *
 * Lo único que sigue dependiendo de este orden es **cuál viene prellenada** en «Agregar»: la
 * primera de cada tipo que de verdad se le vaya a ofrecer (ver `categoriaPorDefectoPara`). Por eso
 * «Comida» y «Salario» están primeras en su grupo y por eso esta lista no se ordena alfabéticamente
 * acá: hacerlo cambiaría el valor inicial del campo a «Arriendo recibido» al anotar un ingreso, que
 * es lo contrario de lo que quiere alguien que anota su sueldo.
 */
val PREDEFINED_CATEGORIES: List<Category> = listOf(
    // Expenses
    Category("cat_food",       "Comida",          "🍔", "#FF6B35", "EXPENSE"),
    Category("cat_transport",  "Transporte",      "🚗", "#4ECDC4", "EXPENSE"),
    Category("cat_health",     "Salud",           "💊", "#45B7D1", "EXPENSE"),
    Category("cat_education",  "Educación",       "📚", "#96CEB4", "EXPENSE"),
    Category("cat_entertain",  "Entretenimiento", "🎮", "#DDA0DD", "EXPENSE"),
    Category("cat_services",   "Servicios",       "💡", "#1E90FF", "EXPENSE"),
    Category("cat_housing",    "Vivienda",        "🏠", "#F0E68C", "EXPENSE"),
    Category("cat_clothing",   "Ropa",            "👗", "#FFB6C1", "EXPENSE"),
    Category("cat_tech",       "Tecnología",      "💻", "#87CEEB", "EXPENSE"),
    Category("cat_card_payment", "Pago de tarjeta", "💳", "#B0A8B9", "EXPENSE"),
    Category("cat_other_exp",  "Otros",           "📦", "#D3D3D3", "EXPENSE"),
    // Incomes
    Category("cat_salary",     "Salario",         "💼", "#90EE90", "INCOME"),
    Category("cat_freelance",  "Freelance",       "🖥️", "#98FB98", "INCOME"),
    Category("cat_rent",       "Arriendo recibido","🏘️","#8FBC8F", "INCOME"),
    Category("cat_invest_inc", "Inversiones",     "📈", "#3CB371", "INCOME"),
    Category("cat_other_inc",  "Otros ingresos",  "💰", "#2E8B57", "INCOME"),
)

/**
 * **La clave con la que se ordena una lista de categorías**, pensada para el español.
 *
 * El dueño dijo, textualmente, que no le gusta que las categorías «no estén tipo orden alfabético
 * sino en cualquier orden». Ordenar por el nombre crudo no alcanza para cumplirlo: comparar
 * `String`s en Kotlin es comparar unidades UTF-16, y con eso «Ñu» cae **después de «Zeta»** (la ñ
 * vive en 0x00F1, arriba de toda la a–z), «Educación» se ordena por la `ó` en vez de por la `o`, y
 * «otros» en minúscula queda detrás de «Zapatos».
 *
 * Por eso la clave:
 * - pasa todo a minúsculas — el orden no puede depender de cómo se tecleó;
 * - saca tildes y diéresis (á→a, ü→u), así «Educación» ordena como «Educacion»;
 * - manda la **ñ justo después de la n** (`ñ` → `n~`, y `~` está arriba de la `z`), que es donde la
 *   pone el alfabeto español: «Nube», «Ñu», «Oso» — y nunca al final de todo.
 *
 * Es una tabla a mano y no una colación Unicode porque no hay una común a los tres targets
 * (Android, iOS, wasm) — el mismo motivo por el que la normalización de búsqueda de `CategoryField`
 * también es a mano. Cubre lo que el español necesita.
 */
fun categorySortKey(name: String): String = buildString(name.length + 2) {
    for (c in name.trim().lowercase()) {
        when (c) {
            'á', 'à', 'ä', 'â' -> append('a')
            'é', 'è', 'ë', 'ê' -> append('e')
            'í', 'ì', 'ï', 'î' -> append('i')
            'ó', 'ò', 'ö', 'ô' -> append('o')
            'ú', 'ù', 'ü', 'û' -> append('u')
            'ñ' -> append("n~")
            else -> append(c)
        }
    }
}

/**
 * El orden alfabético de las categorías, **con desempate estable**: dos nombres que normalizan
 * igual («Otros» y «otros», que pueden convivir porque el nombre es texto libre) se desempatan por
 * el nombre crudo. Sin ese segundo criterio, el orden entre ellas dependería del orden de llegada y
 * podía cambiar entre dos recargas de la misma pantalla.
 */
val CATEGORY_NAME_ORDER: Comparator<String> = compareBy({ categorySortKey(it) }, { it })
