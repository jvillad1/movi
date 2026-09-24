package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.effectiveCategoryTypes
import com.jvillada.movi.theme.COLORES_DEL_CATALOGO
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.shared.model.normalizarParaBuscar

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
 * - **Las reservadas (`isReservedCategory`) no se listan, en ningún filtro.** Las escribe Movi
 *   sola para traspasos, saldos iniciales y ajustes, y no se pueden tocar (ni renombrar, ni
 *   unificar, ni esconder): mostrarlas era cuatro renglones muertos que el dueño no podía usar
 *   para nada. Si hace falta explicarlas, la pantalla lo dice en un pie, no fila por fila.
 * - **Todas** muestra todo lo demás, escondidas incluidas (con su etiqueta): un filtro llamado
 *   «todas» que esconde cosas sería mentir, y además es donde el dueño va a buscar la que
 *   escondió por error.
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
 * cada renglón sigue diciendo su uso («12 movimientos» o «Sin movimientos»), así que lo que sobra
 * se sigue reconociendo de un vistazo. Lo que sí se ganó es poder **encontrar una categoría por su
 * nombre**: apenas la lista pasa de lo que entra en una pantalla, «lo más usado primero» es
 * indistinguible de «cualquier orden» para quien busca «Ñoquis».
 *
 * Se conserva una cosa del orden viejo: **lo que empieza con lo buscado va antes que lo que apenas
 * lo contiene**, igual que en las sugerencias de `CategoryField`: buscando «co», «Comida» arriba
 * de «Bancolombia».
 */
fun filtrarCategorias(
    todas: List<CategoryUsage>,
    filtro: CategoryFilter,
    query: String = "",
): List<CategoryUsage> {
    val q = normalizarParaBuscar(query.trim())
    return todas
        .filter { !it.reserved }
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
        .filter { q.isEmpty() || normalizarParaBuscar(it.name).contains(q) }
        .sortedWith(
            compareBy<CategoryUsage> { if (q.isEmpty() || normalizarParaBuscar(it.name).startsWith(q)) 0 else 1 }
                .thenBy(CATEGORY_NAME_ORDER) { it.name },
        )
}

/**
 * De qué tipo se dice una categoría, con palabras («Gasto», «Ingreso», «Gasto e ingreso») y no con
 * la etiqueta «Ambos» que traía la pantalla vieja. Sale del tipo efectivo, así que dice lo que el
 * dueño fijó apenas lo fija — si dijera lo del catálogo, «Otros» seguiría diciendo «Gasto» después
 * de ponerla en «Gasto e ingreso» y la pantalla se contradiría a sí misma.
 *
 * **Ola B, tarea 5: esto ya no se dice en la fila.** La fila compacta no tiene lugar para una
 * etiqueta de tipo — se dice acá, en la hoja de detalle, y solo cuando ayuda: «Sin usar» no se
 * muestra, porque no dice nada que el dueño pueda usar.
 */
fun etiquetaDeTipo(c: CategoryUsage): String {
    val tipos = tiposEfectivos(c)
    return when {
        tipos.size > 1 -> "Gasto e ingreso"
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
 * El uso, pero pelado a cuántos movimientos — para la fila compacta de Ola B, que no tiene lugar
 * para una oración entera (ver [resumenDeUso], la versión larga que sigue usando la hoja de
 * detalle). Cuenta también los de otra moneda: acá no hay plata que mezclar, solo un conteo.
 *
 * **Fix round 1: «Escondida · …» cuando corresponde.** Sin las etiquetas de la fila vieja, lo
 * único que decía «está escondida» era el nombre en gris — un color que no todos los ojos leen
 * igual de bien. Acá se dice con palabras, en el mismo renglón corto que ya existía, en vez de
 * agregar un renglón o una etiqueta nueva.
 */
fun resumenDeUsoCorto(c: CategoryUsage): String {
    val total = c.movements + c.otherCurrencyMovements
    val uso = when (total) {
        0 -> "Sin movimientos"
        1 -> "1 movimiento"
        else -> "$total movimientos"
    }
    return if (c.hidden) "Escondida · $uso" else uso
}

/**
 * La cifra de este mes, sin la oración de [resumenDelMes] — para el costado derecho de la fila
 * compacta, que solo tiene lugar para un número. `null` si no la usó este mes o si el mes no dejó
 * plata que mostrar (por ejemplo, movimientos en otra moneda).
 */
fun cifraDelMes(c: CategoryUsage): String? {
    if (c.monthMovements <= 0) return null
    return when {
        c.monthTotal > 0 && c.monthIncomeTotal > 0 ->
            "${formatCOP(c.monthTotal)} · +${formatCOP(c.monthIncomeTotal)}"
        c.monthTotal > 0 -> formatCOP(c.monthTotal)
        c.monthIncomeTotal > 0 -> "+${formatCOP(c.monthIncomeTotal)}"
        else -> null
    }
}

/**
 * La categoría que ya existe con el nombre que el dueño escribió al renombrar — otra, no la que
 * está renombrando —, o `null` si el nombre está libre. Con una colisión la hoja ofrece unificar
 * en vez de chocar contra un 409.
 *
 * **Compara como el campo de categoría al anotar un movimiento** ([normalizarParaBuscar]: sin
 * mayúsculas, sin tildes, sin espacios de más). Antes solo ignoraba mayúsculas, así que renombrar
 * «Transporte» a «Alimentacion» cuando ya existía «Alimentación» no avisaba nada y dejaba dos
 * categorías que al anotar un gasto se ven como una sola.
 */
fun colisionAlRenombrar(
    categoria: CategoryUsage,
    nombreEscrito: String,
    existentes: List<CategoryUsage>,
): CategoryUsage? {
    val buscado = normalizarParaBuscar(nombreEscrito)
    if (buscado.isEmpty()) return null
    return existentes.firstOrNull { it.name != categoria.name && normalizarParaBuscar(it.name) == buscado }
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

/** El rótulo del catálogo para una clave de ícono guardada. Una desconocida se muestra tal cual. */
fun rotuloDeIcono(clave: String): String = ICONOS_DEL_CATALOGO.firstOrNull { it.clave == clave }?.rotulo ?: clave

/** El rótulo del catálogo para una clave de color guardada. Ver [rotuloDeIcono]. */
fun rotuloDeColor(clave: String): String = COLORES_DEL_CATALOGO.firstOrNull { it.clave == clave }?.rotulo ?: clave

// ── Ola B · tarea 6: «Ordena tus categorías» ────────────────────────────────────

/**
 * Una propuesta de la tarjeta «Movi encontró N cosas para ordenar». Cada variante ya trae **todo
 * resuelto** —quién es el origen y quién el destino, o cuál categoría— así que la hoja de revisión
 * no vuelve a decidir nada: el dueño solo toca un botón o «Ahora no». Ver [propuestasDeOrden].
 */
sealed class PropuestaDeOrden {
    /**
     * Dos categorías no reservadas ni escondidas cuyo nombre normalizado de una está contenido
     * como palabra completa en el de la otra, y que comparten tipo efectivo (regla 1). [origen]
     * es la de menos movimientos — la que desaparece — y [destino] la de más — la que se queda.
     */
    data class UnificarParecidas(val origen: CategoryUsage, val destino: CategoryUsage) : PropuestaDeOrden()

    /**
     * Una categoría **del catálogo** que el dueño nunca usó: sin movimientos, presupuesto ni
     * recurrente, y todavía no escondida (regla 2).
     */
    data class EsconderNuncaUsada(val categoria: CategoryUsage) : PropuestaDeOrden()

    /**
     * Una categoría **propia** con un solo movimiento en toda su historia, sin presupuesto ni
     * recurrente (regla 3). Se ofrece «Unificar con…» —fix round 1: pasa por la misma hoja de
     * unificar que usa el detalle de una categoría, con su búsqueda, su aviso previo y su botón de
     * confirmar, así que elegir el destino nunca es un solo toque que ya reescribió la historia— o
     * «Ahora no».
     */
    data class UnUso(val categoria: CategoryUsage) : PropuestaDeOrden()
}

/** Cuántos movimientos lleva [c] **en total**, mezclando COP y otra moneda — solo para contar, no para sumar plata. Mismo criterio que [resumenDeUsoCorto] y [avisoDeUnificacion]. */
private fun totalMovimientos(c: CategoryUsage): Int = c.movements + c.otherCurrencyMovements

/**
 * ¿El nombre normalizado de [corto] aparece como una secuencia de **palabras completas y
 * consecutivas** dentro del de [largo]? («Crédito» ⊂ «Cuota de crédito»: sí, es la última
 * palabra; «Cine» NO estaría contenido en «Cocina», porque ahí ni siquiera son palabras
 * separadas — [normalizarParaBuscar] ya partió por espacios antes de llegar acá.)
 */
private fun contenidoComoPalabras(corto: List<String>, largo: List<String>): Boolean {
    if (corto.isEmpty() || corto.size >= largo.size) return false
    for (i in 0..(largo.size - corto.size)) {
        if (largo.subList(i, i + corto.size) == corto) return true
    }
    return false
}

/** ¿El nombre de [contenida] es una secuencia de palabras completas dentro del de [contenedora]? Ver [contenidoComoPalabras]. */
private fun estaContenidaEn(contenida: CategoryUsage, contenedora: CategoryUsage): Boolean {
    val palabrasContenida = normalizarParaBuscar(contenida.name).split(' ').filter { it.isNotEmpty() }
    val palabrasContenedora = normalizarParaBuscar(contenedora.name).split(' ').filter { it.isNotEmpty() }
    return contenidoComoPalabras(palabrasContenida, palabrasContenedora)
}

/** «Comparten tipo efectivo»: la intersección no es vacía, o a alguna no se le conoce ninguno. */
private fun comparteTipoEfectivo(a: CategoryUsage, b: CategoryUsage): Boolean {
    val tiposA = tiposEfectivos(a)
    val tiposB = tiposEfectivos(b)
    return tiposA.isEmpty() || tiposB.isEmpty() || tiposA.intersect(tiposB).isNotEmpty()
}

/** El máximo de propuestas que ofrece la tarjeta a la vez — para que no se vuelva una lista sin fin. */
const val MAX_PROPUESTAS_DE_ORDEN: Int = 12

/**
 * **Las propuestas de orden**, ya resueltas y en el orden en que se muestran. Pura — sin red, sin
 * `Settings` — pero SÍ recibe [descartados] ([com.jvillada.movi.data.PropuestasDescartadasStore.descartadas],
 * las claves de [claveDePropuesta] que el dueño ya rechazó con «Ahora no»): fix round 1, hallazgo
 * 1. Antes el llamador filtraba los descartados DESPUÉS de que esta función ya había cortado en
 * [MAX_PROPUESTAS_DE_ORDEN] — con más de 12 propuestas reales, decir «Ahora no» a una de las
 * primeras 12 nunca dejaba lugar para la 13ª, que existía pero jamás se mostraba. Filtrar tiene
 * que pasar ANTES del corte, así que el corte se mueve acá adentro.
 *
 * Tres reglas, en este orden (1, 2, 3 al numerarlas — el mismo orden de la tarea — pero se
 * **muestran** 1, 3, 2, como pide el brief; ver el `return` al final):
 *
 * 1. **Unificar parecidas**: ver [estaContenidaEn] y [comparteTipoEfectivo]. Ninguna de las dos
 *    puede estar escondida (fix round 1, hallazgo 4: una escondida es un destino que el dueño ya
 *    decidió sacar de circulación, mal candidato para recibir historia nueva sin que se lo
 *    pregunten). No propone un par donde las DOS tienen 5 movimientos o más — a esa altura de uso
 *    es más probable que sean categorías distintas a propósito («Mercado» y «Mercado extra», las
 *    dos vivas) que un duplicado por descuido. Tampoco propone si el DESTINO se queda en 0
 *    movimientos (fix round 1, hallazgo 5): unificar dos categorías que nunca se usaron no tiene
 *    nada que ordenar, y esconder la que corresponda ya lo hace la regla 2. [origen]/[destino] los
 *    decide la cantidad de movimientos; en un empate, la CONTENIDA (el nombre más corto) se
 *    unifica en la que la contiene — determinístico, no depende del orden en que el server
 *    devuelva la lista.
 * 2. **Esconder nunca usadas**: del catálogo, sin movimientos, sin presupuesto, sin recurrente, y
 *    todavía no escondida.
 * 3. **Un solo uso**: propias, exactamente 1 movimiento en toda su historia, sin presupuesto ni
 *    recurrente.
 *
 * Las reservadas nunca entran (se sacan antes de aplicar ninguna regla) — no se pueden tocar. Una
 * categoría del catálogo con uso (como «Tecnología» con 1 movimiento) no cae en la regla 3 —no es
 * propia— ni en la 2 —tiene uso—, y eso es a propósito: no hay nada que ordenar ahí.
 *
 * **Una categoría que ya entró en la regla 1 no vuelve a proponerse en la 2 ni en la 3.** «Crédito»
 * con 1 movimiento cumple también el criterio de «un solo uso» —propia, 1 movimiento, sin
 * presupuesto ni recurrente— pero ya tiene una propuesta concreta («unificar en Cuota de
 * crédito»): repetirla como «un solo uso, elige destino» sería preguntar dos veces lo mismo con
 * dos botones distintos.
 */
fun propuestasDeOrden(
    categorias: List<CategoryUsage>,
    descartados: Set<String> = emptySet(),
): List<PropuestaDeOrden> {
    val utiles = categorias.filterNot { it.reserved }

    val unificarParecidas = mutableListOf<PropuestaDeOrden.UnificarParecidas>()
    for (i in utiles.indices) {
        for (j in (i + 1) until utiles.size) {
            val a = utiles[i]
            val b = utiles[j]
            if (a.hidden || b.hidden) continue
            val aContenida = estaContenidaEn(a, b)
            val bContenida = estaContenidaEn(b, a)
            if (!aContenida && !bContenida) continue
            if (!comparteTipoEfectivo(a, b)) continue
            val movA = totalMovimientos(a)
            val movB = totalMovimientos(b)
            if (movA >= 5 && movB >= 5) continue
            val (origen, destino) = when {
                movA < movB -> a to b
                movB < movA -> b to a
                // Empate: la contenida (el nombre más corto) se unifica en la que la contiene.
                aContenida -> a to b
                else -> b to a
            }
            if (totalMovimientos(destino) == 0) continue
            unificarParecidas += PropuestaDeOrden.UnificarParecidas(origen, destino)
        }
    }
    unificarParecidas.sortWith(compareBy(CATEGORY_NAME_ORDER) { it.origen.name })

    val yaPropuestas = unificarParecidas
        .flatMap { listOf(normalizarParaBuscar(it.origen.name), normalizarParaBuscar(it.destino.name)) }
        .toSet()

    val unUso = utiles
        .filter {
            it.scope == CategoryScope.CUSTOM && totalMovimientos(it) == 1 &&
                it.budgets == 0 && it.recurringRules == 0 &&
                normalizarParaBuscar(it.name) !in yaPropuestas
        }
        .sortedWith(compareBy(CATEGORY_NAME_ORDER) { it.name })
        .map { PropuestaDeOrden.UnUso(it) }

    val esconderNuncaUsadas = utiles
        .filter {
            it.scope == CategoryScope.PREDEFINED && !it.hidden && !it.enUso &&
                normalizarParaBuscar(it.name) !in yaPropuestas
        }
        .sortedWith(compareBy(CATEGORY_NAME_ORDER) { it.name })
        .map { PropuestaDeOrden.EsconderNuncaUsada(it) }

    // El orden que se MUESTRA es 1, 3, 2 (así lo pidió la tarea); filtrar los «Ahora no» y recién
    // ACÁ cortar en el máximo — nunca al revés (ver el KDoc de arriba, hallazgo 1).
    return (unificarParecidas + unUso + esconderNuncaUsadas)
        .filterNot { claveDePropuesta(it) in descartados }
        .take(MAX_PROPUESTAS_DE_ORDEN)
}

/**
 * La clave estable con la que se recuerda un «Ahora no» ([com.jvillada.movi.data.PropuestasDescartadasStore]):
 * tipo de regla + nombres, **normalizados** — así sigue reconociendo la misma propuesta aunque el
 * dueño la vuelva a ver con otro caso o tildes.
 */
fun claveDePropuesta(p: PropuestaDeOrden): String = when (p) {
    is PropuestaDeOrden.UnificarParecidas ->
        "unificar:${normalizarParaBuscar(p.origen.name)}>${normalizarParaBuscar(p.destino.name)}"
    is PropuestaDeOrden.UnUso -> "unUso:${normalizarParaBuscar(p.categoria.name)}"
    is PropuestaDeOrden.EsconderNuncaUsada -> "esconder:${normalizarParaBuscar(p.categoria.name)}"
}

/** La explicación en una línea que acompaña a cada propuesta en la hoja de revisión. */
fun explicacionDePropuesta(p: PropuestaDeOrden): String = when (p) {
    is PropuestaDeOrden.UnificarParecidas -> {
        val movOrigen = totalMovimientos(p.origen)
        val movDestino = totalMovimientos(p.destino)
        val dichoOrigen = if (movOrigen == 1) "1 movimiento" else "$movOrigen movimientos"
        "«${p.origen.name}» tiene $dichoOrigen; «${p.destino.name}» tiene $movDestino."
    }
    is PropuestaDeOrden.UnUso ->
        "«${p.categoria.name}» tiene un solo movimiento, sin presupuesto ni recurrente."
    is PropuestaDeOrden.EsconderNuncaUsada ->
        "«${p.categoria.name}» es del catálogo de Movi y nunca la usaste."
}

/** El texto de la tarjeta de arriba, con singular/plural (nunca «1 cosas»). */
fun textoDeLaTarjetaDeOrden(cantidad: Int): String =
    "Movi encontró $cantidad ${if (cantidad == 1) "cosa" else "cosas"} para ordenar"

/**
 * Minúsculas y sin tildes/diéresis — misma normalización que las sugerencias de categoría (ver
 * `normalizeForMatch`), y **distinta de [CATEGORY_NAME_ORDER] en un solo punto**: para BUSCAR, la
 * `ñ` se aplasta contra la `n`; para ORDENAR va justo después de la n.
 */
