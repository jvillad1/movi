package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.textoDeOrigenMudo

import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.UpcomingPayment

/**
 * # El Inicio contesta «¿cómo voy en este período?»
 *
 * Todo lo de este archivo es **puro**: recibe lo que el Inicio ya tenía cargado y devuelve lo que
 * hay que pintar. Sin red, sin reloj propio, sin Compose — para que las decisiones sobre la plata
 * del dueño se puedan probar sin abrir una pantalla.
 *
 * El pedido, con sus palabras: *«que la home sea tipo un resumen del periodo … en qué categorías
 * hice movimientos y en dónde se me fue la plata, qué me falta por pagar y qué ya pagué tipo
 * checklist … que también tire insights de qué cosas debería revisar»*.
 *
 * Las tres respuestas viven acá: [categoriasDelPeriodo], [checklistDelPeriodo] y
 * [cosasParaRevisar].
 */

// ── En qué se fue la plata ───────────────────────────────────────────────────

/**
 * Una categoría del período, con lo que pesa dentro del gasto total.
 *
 * @param fraccion de 0 a 1 sobre el gasto TOTAL del período, no sobre la categoría más grande. Una
 *   barra que llena el ancho porque es la mayor de tres categorías chicas diría algo falso.
 */
data class CategoriaDelPeriodo(
    val nombre: String,
    val gastado: Long,
    val fraccion: Float,
    val limite: Long? = null,
) {
    /** ¿Se pasó del presupuesto que el dueño le puso? Sin presupuesto no hay nada que decir. */
    val superada: Boolean get() = limite != null && limite > 0 && gastado > limite
}

/**
 * Las categorías del período, de mayor a menor, con el resto agrupado.
 *
 * **Agrupar la cola importa más que mostrarla.** Con veinte categorías, las quince últimas son una
 * lista que nadie lee y que empuja el resto del Inicio fuera de la pantalla; pero borrarlas haría
 * que las barras no sumen el gasto del período y el dueño no podría cuadrar la cifra de arriba con
 * lo de abajo. Van juntas en «Otras N categorías».
 *
 * Los montos llegan ya acotados al período: los calcula el server en `GET /api/dashboard/summary`,
 * con todo lo que sabe (todos los dispositivos, SMS, importaciones, anulados afuera).
 *
 * @param cuantas cuántas se muestran sueltas antes de agrupar.
 */
fun categoriasDelPeriodo(
    gastoPorCategoria: Map<String, Long>,
    presupuestos: List<Budget> = emptyList(),
    cuantas: Int = 5,
): List<CategoriaDelPeriodo> {
    val positivas = gastoPorCategoria.filterValues { it > 0 }
    val total = positivas.values.sum()
    if (total <= 0L) return emptyList()
    val limitePorCategoria = presupuestos.associate { it.category to it.monthlyLimit }

    val ordenadas = positivas.entries.sortedWith(
        // Por monto, y a igual monto por nombre: sin el desempate, dos categorías con la misma
        // cifra se intercambian de lugar entre recargas sin que nada haya cambiado.
        compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.lowercase() },
    )
    val sueltas = ordenadas.take(cuantas).map { (nombre, gastado) ->
        CategoriaDelPeriodo(
            nombre = nombre,
            gastado = gastado,
            fraccion = gastado.toFloat() / total,
            limite = limitePorCategoria[nombre],
        )
    }
    val cola = ordenadas.drop(cuantas)
    if (cola.isEmpty()) return sueltas
    val restante = cola.sumOf { it.value }
    return sueltas + CategoriaDelPeriodo(
        nombre = if (cola.size == 1) cola.first().key else "Otras ${cola.size} categorías",
        gastado = restante,
        fraccion = restante.toFloat() / total,
        limite = if (cola.size == 1) limitePorCategoria[cola.first().key] else null,
    )
}

// ── Qué pagué y qué me falta ─────────────────────────────────────────────────
//
// Ola 4: la fila del período, sus estados y la función que arma la lista se mudaron a `:core`
// (`shared/model/PagosDelPeriodo.kt`) para que el server —la caja proyectada de Movi AI— use la
// MISMA lista que Plan. Acá quedan con su nombre de siempre, para que ninguna pantalla cambie.

typealias PagoDelPeriodo = com.jvillada.movi.shared.model.PagoDelPeriodo
typealias EstadoDeLaFila = com.jvillada.movi.shared.model.EstadoDeLaFila

/** Ver [com.jvillada.movi.shared.model.checklistDelPeriodo]. */
fun checklistDelPeriodo(
    upcoming: List<UpcomingPayment>,
    ocurrencias: List<OccurrenceState>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
): List<PagoDelPeriodo> = com.jvillada.movi.shared.model.checklistDelPeriodo(upcoming, ocurrencias, periodo, settings)

/** Ver [com.jvillada.movi.shared.model.pendientesDePeriodosAnteriores]. */
fun pendientesDePeriodosAnteriores(
    upcoming: List<UpcomingPayment>,
    ocurrencias: List<OccurrenceState>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
): List<PagoDelPeriodo> =
    com.jvillada.movi.shared.model.pendientesDePeriodosAnteriores(upcoming, ocurrencias, periodo, settings)

/**
 * Cuánto falta por pagar de este período.
 *
 * Fuera quedan dos cosas que no son plata que vaya a salir: el SALDO de una tarjeta (es una deuda,
 * no una cuota) y todo INGRESO. Lo segundo hacía que el «Falta $X» del Inicio incluyera el sueldo
 * del dueño mientras no lo marcara — la cifra afirmaba que le faltaba pagar su propio salario.
 */
fun faltaPorPagar(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { !it.pagado && !it.montoEsSaldo && !it.esIngreso }.sumOf { it.monto }

// ── Cómo se agrupa y qué dice la tarjeta ─────────────────────────────────────

/** Lo que falta pagar, en el orden en que sale del checklist. Un ingreso no se paga: no va acá. */
fun pagosPendientes(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist.filter { !it.pagado && !it.esIngreso }

/** Lo que falta que LLEGUE: el sueldo, un arriendo que cobra. Se tilda igual, pero no se «paga». */
fun ingresosPendientes(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist.filter { !it.pagado && it.esIngreso }

/** Lo ya tildado, pagos e ingresos juntos: la mitad del checklist que prueba el avance. */
fun yaMarcados(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> = checklist.filter { it.pagado }

/** «Ya pagaste»: los pagos (no los ingresos) que tienen un pago en el período. */
fun pagosHechos(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist.filter { it.pagado && !it.esIngreso }

/** «Ya recibiste»: los ingresos que ya llegaron en el período. */
fun ingresosRecibidos(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist.filter { it.pagado && it.esIngreso }

/**
 * **Cuánto ya pagaste en el período**, en pesos: lo que de verdad salió cuando se sabe
 * ([PagoDelPeriodo.montoPagado]) y si no, lo que dice la regla.
 *
 * El saldo de una tarjeta no es un pago: sin el monto del movimiento que la pagó no suma nada, en
 * vez de sumar la deuda entera como si se hubiera pagado. Y fuera de pesos no se suma: una cifra en
 * pesos que esconde dólares adentro es la clase de total que este archivo no da.
 */
fun yaPagadoEnElPeriodo(checklist: List<PagoDelPeriodo>): Long =
    pagosHechos(checklist).filter { it.moneda == "COP" }.sumOf { it.montoPagado ?: if (it.montoEsSaldo) 0L else it.monto }

/** Lo que falta que llegue: el total de «Por cobrar», con el mismo criterio que [faltaPorPagar]. */
fun faltaPorCobrar(checklist: List<PagoDelPeriodo>): Long =
    ingresosPendientes(checklist).filter { it.moneda == "COP" }.sumOf { it.monto }

/** Lo que ya llegó: el total de «Ya recibiste», con lo que de verdad entró cuando se sabe. */
fun yaRecibidoEnElPeriodo(checklist: List<PagoDelPeriodo>): Long =
    ingresosRecibidos(checklist).filter { it.moneda == "COP" }.sumOf { it.montoPagado ?: it.monto }

/**
 * El avance **sobre los pagos**: cuántos están tildados de cuántos hay.
 *
 * Los ingresos quedan afuera del conteo por la misma razón por la que quedan afuera de
 * [faltaPorPagar]: «te faltan 2 de 5 pagos» tiene que hablar de plata que sale. Los ingresos siguen
 * estando en el checklist y se tildan igual — no se cuentan, que es distinto de esconderlos.
 */
fun avanceDelChecklist(checklist: List<PagoDelPeriodo>): Pair<Int, Int> {
    val pagos = checklist.filter { !it.esIngreso }
    return pagos.count { it.pagado } to pagos.size
}

/**
 * **La línea que hace honesta a la tarjeta del Inicio**: dice que lo listado es lo que FALTA, y de
 * cuántos pagos del período se trata.
 *
 * El reclamo del dueño, textual: *«solo muestra los faltantes, no muestra todos; debería indicar
 * que esos son los faltantes nada más»*. La tarjeta no pasa a listarlo todo —para eso está el
 * checklist completo, a un toque— pero deja de presentar una parte como si fuera el total.
 *
 * **Sin nada pendiente y con ingresos, la línea cuenta las dos cosas.** «Ya salieron los 12
 * pagos» con dos ingresos también recibidos dejaba sin contar lo que llegó: la lista del período
 * también tiene los ingresos, y esta línea solo hablaba de pagos. Sin ingresos en el checklist no
 * hay nada que agregar, y sigue diciendo lo de siempre. Y «ya está todo» solo si TAMBIÉN entraron
 * los ingresos: con uno pendiente, la línea habla solo de los pagos (que sí salieron todos) y no
 * contradice al «Por cobrar» de la lista.
 *
 * En Plan es también lo que dice «Falta por pagar» cuando ya no falta nada.
 */
fun lineaDeLoQueFalta(checklist: List<PagoDelPeriodo>): String {
    val (pagados, total) = avanceDelChecklist(checklist)
    val faltan = total - pagados
    val pagos = if (total == 1) "pago" else "pagos"
    val totalIngresos = checklist.count { it.esIngreso }
    return when {
        total == 0 -> "Este período no tiene pagos anotados"
        faltan == 0 && totalIngresos > 0 && ingresosPendientes(checklist).isEmpty() -> {
            val ingresos = if (totalIngresos == 1) "ingreso" else "ingresos"
            "Ya está todo lo de este período: $total $pagos y $totalIngresos $ingresos"
        }
        faltan == 0 && total == 1 -> "Ya salió el único pago de este período"
        faltan == 0 -> "Ya salieron los $total pagos de este período"
        else -> "Te ${if (faltan == 1) "falta" else "faltan"} $faltan de $total $pagos de este período"
    }
}

/**
 * El pie que dice dónde está lo que esta tarjeta NO muestra, o `null` si no falta nada por contar.
 *
 * Sin él, «te faltan 3 de 7» deja al dueño con la pregunta de dónde quedaron los otros cuatro.
 */
fun pieDeLoYaPagado(checklist: List<PagoDelPeriodo>): String? {
    val (pagados, total) = avanceDelChecklist(checklist)
    if (pagados == 0 || pagados == total) return null
    return if (pagados == 1) "Ya salió 1. El checklist completo está en «Ver todos»."
    else "Ya salieron $pagados. El checklist completo está en «Ver todos»."
}

// ── Qué debería revisar ──────────────────────────────────────────────────────

/** Algo que el Inicio sugiere mirar, con la pantalla donde se resuelve. */
data class CosaParaRevisar(
    val texto: String,
    val detalle: String,
    val destino: DestinoDeRevision,
    /** Las urgentes van primero y en ámbar; el resto es una sugerencia, no una alarma. */
    val urgente: Boolean = false,
)

/**
 * A dónde lleva tocar una sugerencia. Un enum y no una `Screen` para que esto siga siendo puro.
 *
 * Ola C: [POR_REVISAR] reemplaza a `SMS` — los mensajes del banco por confirmar y los candidatos a
 * pago de tarjeta se revisan en la misma bandeja. No viaja ni se guarda: vive solo en la UI.
 */
enum class DestinoDeRevision { MOVIMIENTOS, RECURRENTES, PRESUPUESTOS, CREDITOS, POR_REVISAR, SUSCRIPCIONES, CUADRE, CAPTURA, CUENTAS_DE_OTROS }

/**
 * **Lo que el Inicio recomienda mirar hoy**, de lo más urgente a lo más opcional.
 *
 * La regla que ordena todo esto: **cada sugerencia tiene que poder accionarse**. «Gastaste más que
 * el mes pasado» no es una sugerencia, es una observación; «la cuota del vehículo venció y sigue
 * sin marcarse» sí lo es, porque hay algo que hacer y una pantalla donde hacerlo.
 *
 * Por eso ninguna sale de una corazonada: todas salen de un dato que ya está cargado y llevan a la
 * pantalla donde se resuelve. Y son **como mucho [cuantas]**: una lista larga de consejos es ruido,
 * y el ruido enseña a ignorar la sección entera.
 */
fun cosasParaRevisar(
    checklist: List<PagoDelPeriodo>,
    categorias: List<CategoriaDelPeriodo>,
    flujoDelPeriodo: Long,
    smsPorConfirmar: Int,
    candidatosAPagoDeTarjeta: Int,
    gastoSinCategoria: Long = 0,
    /**
     * El aviso de las cuentas que llevan más de un período sin cuadrarse contra el banco, ya
     * escrito (ver `textoDelAvisoDeCuadre`); `null` = no hay ninguna y no se dice nada.
     *
     * Llega hecho en vez de calcularse acá para que este archivo siga sin saber de cuentas ni de
     * relojes: la regla vive en `ui/cuadre`, que es donde se resuelve.
     */
    avisoDeCuadre: String? = null,
    /**
     * Ola 2 · «banco mudo»: los orígenes de captura que se callaron (`origenesMudos`, en :core, lo
     * calcula el server). Urgentes: mientras la captura esté muda, lo que el dueño pague no entra
     * solo y el período se ve más barato de lo que es.
     */
    bancosMudos: List<OrigenMudo> = emptyList(),
    /**
     * 4-oct-2026: cuántas cuentas a las que les envía plata no tienen nombre (lo que Movi encontró
     * solo). No es urgente: va abajo, junto al cuadre.
     */
    cuentasDeOtrosSinNombre: Int = 0,
    cuantas: Int = 4,
): List<CosaParaRevisar> {
    val todas = buildList {
        bancosMudos.forEach { mudo ->
            add(
                CosaParaRevisar(
                    texto = textoDeOrigenMudo(mudo),
                    detalle = "Mientras tanto, lo que pagues no entra solo a Por revisar.",
                    destino = DestinoDeRevision.CAPTURA,
                    urgente = true,
                ),
            )
        }
        val vencidos = checklist.filter { it.vencido }
        if (vencidos.isNotEmpty()) {
            add(
                CosaParaRevisar(
                    texto = if (vencidos.size == 1) "«${vencidos.first().nombre}» venció y no está marcado"
                    else "${vencidos.size} pagos vencidos sin marcar",
                    detalle = "Si ya lo pagaste, márcalo para que deje de avisarte.",
                    destino = DestinoDeRevision.RECURRENTES,
                    urgente = true,
                ),
            )
        }
        if (smsPorConfirmar > 0) {
            add(
                CosaParaRevisar(
                    texto = "$smsPorConfirmar ${if (smsPorConfirmar == 1) "mensaje" else "mensajes"} del banco sin confirmar",
                    detalle = "Hasta confirmarlos no cuentan en el gasto del período.",
                    destino = DestinoDeRevision.POR_REVISAR,
                    urgente = smsPorConfirmar >= 10,
                ),
            )
        }
        val superadas = categorias.filter { it.superada }
        if (superadas.isNotEmpty()) {
            add(
                CosaParaRevisar(
                    texto = if (superadas.size == 1) "Te pasaste del presupuesto de ${superadas.first().nombre}"
                    else "Te pasaste en ${superadas.size} presupuestos",
                    detalle = "Todavía estás en el período: puedes ajustar el límite o frenar el gasto.",
                    destino = DestinoDeRevision.PRESUPUESTOS,
                ),
            )
        }
        if (candidatosAPagoDeTarjeta > 0) {
            add(
                CosaParaRevisar(
                    texto = "$candidatosAPagoDeTarjeta ${if (candidatosAPagoDeTarjeta == 1) "movimiento parece" else "movimientos parecen"} pago de tarjeta",
                    detalle = "Marcarlos evita contarlos dos veces: como gasto y como menos deuda.",
                    destino = DestinoDeRevision.POR_REVISAR,
                ),
            )
        }
        if (gastoSinCategoria > 0) {
            add(
                CosaParaRevisar(
                    texto = "Hay gastos sin categoría este período",
                    // El «y la próxima vez» no es una promesa de marketing: es literalmente lo que
                    // hace `MemoriaDeCategorias`. Ponerle categoría a uno le enseña a Movi el
                    // destinatario entero, y por eso vale la pena decirle que el rato invertido
                    // rinde más de una vez.
                    detalle = "Ponles una y Movi reconoce sola a ese mismo destinatario la próxima vez.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
        // No es urgente y va abajo de lo que vence: nadie pierde plata hoy por no haber cuadrado.
        // Lo que sí pasa —y por eso está— es que la diferencia se compone en silencio: los
        // rendimientos de una cuenta de ahorros no llegan por SMS, así que si nadie los anota, el
        // saldo de Movi se va quedando corto mes a mes.
        if (avisoDeCuadre != null) {
            add(
                CosaParaRevisar(
                    texto = avisoDeCuadre,
                    detalle = "Compara con el saldo del banco: los rendimientos y las cuotas de manejo no avisan.",
                    destino = DestinoDeRevision.CUADRE,
                ),
            )
        }
        if (cuentasDeOtrosSinNombre > 0) {
            add(
                CosaParaRevisar(
                    texto = textoDeCuentasSinNombre(cuentasDeOtrosSinNombre),
                    detalle = "Les envías plata seguido. Ponles nombre en Personas y comercios y Movi los reconoce en tus avisos.",
                    destino = DestinoDeRevision.CUENTAS_DE_OTROS,
                ),
            )
        }
        if (flujoDelPeriodo < 0) {
            add(
                CosaParaRevisar(
                    texto = "Vas gastando más de lo que entró este período",
                    detalle = "La categoría más pesada es ${categorias.firstOrNull()?.nombre ?: "la primera de la lista"}.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
    }
    return todas.sortedByDescending { it.urgente }.take(cuantas)
}

/** «3 cuentas a las que les envías plata no tienen nombre». */
fun textoDeCuentasSinNombre(n: Int): String =
    if (n == 1) "1 persona o comercio sin nombre"
    else "$n personas o comercios sin nombre"

/**
 * **Cuánta plata del período quedó sin categoría.** Los dos nombres, porque durante un tiempo Movi
 * escribió «Otro» en singular al confirmar un SMS mientras el resto de la app decía «Otros»: hay
 * movimientos viejos con cada uno, y los dos significan lo mismo — que nadie decidió todavía.
 *
 * No se confunde con el renglón «Otras N categorías» que arma [categoriasDelPeriodo] para la cola
 * del gráfico: eso es un agrupado de categorías que sí existen, y se compara contra el mapa crudo.
 */
fun gastoSinCategoriaDe(gastoPorCategoria: Map<String, Long>): Long =
    gastoPorCategoria.entries
        .filter { it.key.trim().equals("Otros", ignoreCase = true) || it.key.trim().equals("Otro", ignoreCase = true) }
        .sumOf { it.value }
        .coerceAtLeast(0)

/** El monto que el checklist dice que ya se pagó, para el rótulo de avance. */
fun yaPagado(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { it.pagado && !it.montoEsSaldo && !it.esIngreso }.sumOf { it.monto }

/** Un pago de [checklist] que sirva de ejemplo de lo que urge, o `null` si no falta nada. */
fun loQueUrge(checklist: List<PagoDelPeriodo>): PagoDelPeriodo? =
    checklist.firstOrNull { !it.pagado && !it.esIngreso }
