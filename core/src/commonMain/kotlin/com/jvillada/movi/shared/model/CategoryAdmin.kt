package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Administrar categorías — «Más → Categorías»
 *
 * **Una sola lista de categorías. El tipo es un filtro, no la identidad.**
 *
 * Hasta acá, una categoría "era" de gasto o de ingreso: [PREDEFINED_CATEGORIES] le clava un
 * `type` a cada una y esa decisión no se podía discutir. De ahí sale la duplicación más fea del
 * catálogo — «Otros» (EXPENSE) y «Otros ingresos» (INCOME) son la misma idea partida en dos por
 * una restricción técnica — y de ahí salió también un error real: una pata huérfana de traspaso
 * de tipo INCOME quedó con «Otros», que está tipada como gasto.
 *
 * Acá el tipo pasa a ser un **filtro con tres estados** («Gasto», «Ingreso», «Ambos» — el
 * `"BOTH"` que [Category.type] ya aceptaba y que ninguna categoría usaba), y además **se puede
 * fijar a mano** por encima de lo que diga el catálogo o de lo que se haya aprendido del uso.
 * Con eso, juntar «Otros ingresos» dentro de «Otros» y dejar «Otros» en «Ambos» es una decisión
 * del dueño y no un cambio de código.
 *
 * ## Por qué las preferencias son del usuario y no del catálogo
 *
 * [PREDEFINED_CATEGORIES] es una constante de código: la comparten todos los usuarios y no se
 * puede editar en caliente. Esconder «Freelance» porque este dueño no la usa, o fijar «Otros» en
 * «Ambos», son decisiones **suyas**. Por eso viven en una tabla propia por usuario
 * (`category_prefs`) y no tocan el catálogo — que sigue siendo el mismo para todos y sigue
 * siendo el punto de partida de quien recién empieza.
 */

/**
 * Las **cuatro categorías reservadas** de Movi: las escribe la app sola y de su nombre EXACTO
 * dependen las cifras del mes (ver [isCashFlow], que compara por string). Renombrar cualquiera
 * de ellas rompería el cálculo de ingresos y egresos de toda la historia; unificarla o esconderla
 * la sacaría de la vista sin sacarla de los datos.
 *
 * Por eso quedan **fuera de todas las acciones** de esta pantalla — se muestran, para que el
 * dueño entienda de dónde salen esos movimientos, pero con candado.
 */
val RESERVED_CATEGORIES: Set<String> = setOf(
    TRANSFER_CATEGORY,        // «Traspaso»
    OPENING_CATEGORY,         // «Saldo inicial»
    CARD_PAYMENT_CATEGORY,    // «Pago de tarjeta»
    ORPHANED_LEG_CATEGORY,    // «Cuenta eliminada»
)

/**
 * ¿Es una de las [RESERVED_CATEGORIES]?
 *
 * Compara **sin distinguir mayúsculas**, a propósito y aunque [isCashFlow] compare exacto. Acá la
 * pregunta no es "¿esta fila queda fuera del flujo de caja?" sino "¿puedo dejar que el dueño
 * escriba este nombre?", y la respuesta segura para «traspaso» en minúscula es que no: dejarlo
 * pasar fabricaría un nombre a un carácter de distancia del reservado, con la mitad de la app
 * tratándolo de una forma y la otra mitad de otra. El costo es que una categoría propia que se
 * llamara «traspaso» tampoco se puede administrar — un caso que, si existe, casi seguro ES una
 * pata de traspaso mal escrita.
 */
fun isReservedCategory(name: String): Boolean =
    RESERVED_CATEGORIES.any { it.equals(name.trim(), ignoreCase = true) }

/** El tercer estado del tipo: sirve para gastos y para ingresos. Ver [Category.type]. */
const val CATEGORY_TYPE_BOTH = "BOTH"

/** Los valores que acepta [CategoryUsage.pinnedType] (además de `null` = sin fijar). */
val CATEGORY_TYPE_VALUES: Set<String> =
    setOf(TransactionType.EXPENSE.name, TransactionType.INCOME.name, CATEGORY_TYPE_BOTH)

/**
 * Una categoría **con su uso real**, que es el dato sin el cual el dueño no puede decidir nada:
 * una lista de nombres pelados no dice cuál sobra, cuál está duplicada ni cuál nunca usó.
 *
 * Incluye tanto las del catálogo ([CategoryScope.PREDEFINED]) como las que escribió él
 * ([CategoryScope.CUSTOM]) — **una sola lista**, con el origen como una etiqueta más.
 */
@Serializable
data class CategoryUsage(
    val name: String,
    /** ¿Viene del catálogo de Movi o la escribió el dueño? */
    val scope: CategoryScope = CategoryScope.CUSTOM,
    /** Reservada de Movi: se muestra, no se toca. Ver [RESERVED_CATEGORIES]. */
    val reserved: Boolean = false,
    /** Tipos con los que se la vio usada en los movimientos. Vacío = no se sabe. */
    val usedTypes: List<TransactionType> = emptyList(),
    /** Tipo fijado a mano por el dueño: `"EXPENSE"`, `"INCOME"` o `"BOTH"`. `null` = sin fijar. */
    val pinnedType: String? = null,
    /** Escondida: deja de ofrecerse al escribir. **No borra ni toca un solo movimiento.** */
    val hidden: Boolean = false,
    /** Movimientos en COP no anulados con esta categoría, en toda la historia. */
    val movements: Int = 0,
    /** Cuánto suman esos movimientos (COP). Ingresos y gastos suman en positivo cada uno por su lado. */
    val total: Long = 0,
    /** Los mismos dos números, acotados al mes en curso (mes civil de Bogotá). */
    val monthMovements: Int = 0,
    val monthTotal: Long = 0,
    /**
     * Movimientos con esta categoría en una moneda que no es COP. Van aparte porque sumarlos a
     * [total] sería sumar peras con dólares: el total dice pesos o no dice nada.
     */
    val otherCurrencyMovements: Int = 0,
    /** ¿Hay un presupuesto con este nombre? (el cruce presupuesto↔gasto es por nombre). */
    val budgets: Int = 0,
    /** ¿Cuántas reglas recurrentes llevan este nombre? */
    val recurringRules: Int = 0,
) {
    /** ¿Tiene algo detrás — movimientos, presupuesto o recurrente? */
    val enUso: Boolean get() = movements > 0 || otherCurrencyMovements > 0 || budgets > 0 || recurringRules > 0
}

/**
 * Lo que el dueño decidió sobre una categoría, sin el uso: lo mínimo que el campo de categoría
 * necesita saber para dejar de ofrecer una escondida y para respetar un tipo fijado.
 *
 * Viaja dentro de [UsedCategory] (en el resumen del Inicio) y se guarda en
 * `com.jvillada.movi.data.UsedCategoriesCache.prefs`.
 */
data class CategoryPref(
    val hidden: Boolean = false,
    /** `"EXPENSE"`, `"INCOME"`, `"BOTH"` o `null` = sin fijar. */
    val pinnedType: String? = null,
)

/**
 * **De qué tipos es una categoría, ya resuelto.** Es la regla única que reemplaza al `type`
 * clavado del catálogo, y el orden importa:
 *
 * 1. Lo que el dueño **fijó** ([pinnedType]) gana siempre. Es el punto de la pantalla nueva.
 * 2. Si no fijó nada y la categoría está en el **catálogo**, vale el tipo del catálogo.
 * 3. Si no, valen los **tipos con los que se la vio usada** — lo que ya aprendía
 *    `UsedCategoriesCache` para las categorías propias.
 *
 * Un resultado **vacío** significa «no se sabe», que NO es lo mismo que «de ninguno»: quien
 * filtra por tipo tiene que mostrarla igual (ver `suggestCategoryMatches`). Esconder por falta de
 * datos es peor que sugerir de más.
 */
fun effectiveCategoryTypes(
    name: String,
    pinnedType: String?,
    usedTypes: Set<TransactionType> = emptySet(),
): Set<TransactionType> {
    val ambos = setOf(TransactionType.EXPENSE, TransactionType.INCOME)
    if (pinnedType != null) {
        if (pinnedType == CATEGORY_TYPE_BOTH) return ambos
        return setOfNotNull(runCatching { TransactionType.valueOf(pinnedType) }.getOrNull())
    }
    val delCatalogo = PREDEFINED_CATEGORIES.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    if (delCatalogo != null) {
        if (delCatalogo.type == CATEGORY_TYPE_BOTH) return ambos
        return setOfNotNull(runCatching { TransactionType.valueOf(delCatalogo.type) }.getOrNull())
    }
    return usedTypes
}

/** Renombrar: la misma categoría con otro nombre. El destino **no** puede existir ya. */
@Serializable
data class RenameCategoryRequest(val from: String, val to: String)

/** Unificar: todo lo de [from] pasa a llamarse [into], que normalmente ya existe. */
@Serializable
data class MergeCategoryRequest(val from: String, val into: String)

/** Fijar tipo y/o esconder. `pinnedType = null` = sin fijar (vuelve a mandar catálogo/uso). */
@Serializable
data class CategoryPrefsRequest(
    val name: String,
    val hidden: Boolean = false,
    val pinnedType: String? = null,
)

/**
 * Qué se reescribió de verdad. Se devuelve para poder decírselo al dueño con números («12
 * movimientos, 1 presupuesto y 1 recurrente ahora dicen Transporte») en vez de un «listo» mudo.
 */
@Serializable
data class CategoryRewriteResult(
    /** El nombre que quedó. */
    val name: String,
    val movements: Int = 0,
    val budgets: Int = 0,
    /**
     * `true` si el destino YA tenía presupuesto y los dos límites se sumaron en uno solo (ver
     * `rewriteCategory` en el server). Es la única parte de la operación que no es "el mismo dato
     * con otro nombre", así que se dice.
     */
    val budgetsMerged: Boolean = false,
    val recurringRules: Int = 0,
)

// ── Los textos de los rechazos ────────────────────────────────────────────────
// Viven acá y no en el handler porque hacen falta iguales en tres lugares: el 422 del server, la
// guarda del cliente (que responde sin red) y la pantalla, que los muestra tal cual.

fun categoriaReservadaMensaje(name: String): String =
    "«$name» es una categoría reservada de Movi: la escribe la app sola para traspasos, saldos " +
        "iniciales y pagos de tarjeta, y de su nombre exacto dependen las cifras de tu mes. " +
        "No se puede renombrar, unificar ni esconder."

const val CATEGORY_CATALOG_RENAME_BLOCKED: String =
    "Las categorías del catálogo de Movi no se renombran: el catálogo es el mismo para todos y " +
        "volvería a sugerirte el nombre viejo. Si quieres juntarla con otra, únela; si no la " +
        "usas, escóndela."

const val CATEGORY_NAME_REQUIRED: String = "Falta el nombre de la categoría."

/**
 * El mismo tope que ya impone `PUT /api/events/{id}/category` — la columna aguanta 100, pero un
 * nombre de categoría de 100 caracteres no se lee en ninguna lista. Vale la pena que sea el mismo
 * número en los dos lados: dos topes distintos para el mismo dato terminan dejando pasar por una
 * puerta lo que la otra rechaza.
 */
const val CATEGORY_NAME_MAX_LENGTH: Int = 60

const val CATEGORY_NAME_TOO_LONG: String =
    "El nombre de una categoría no puede pasar de $CATEGORY_NAME_MAX_LENGTH caracteres."

fun categoriaDestinoOcupadoMensaje(name: String): String =
    "Ya tienes una categoría «$name». Si quieres juntar las dos, usa Unificar en vez de renombrar."

const val CATEGORY_MERGE_SAME: String =
    "Es la misma categoría: elige otra para unificarla."
