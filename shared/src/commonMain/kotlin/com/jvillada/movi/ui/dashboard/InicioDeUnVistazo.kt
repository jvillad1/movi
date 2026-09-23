package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatMoneyCompact
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * # El Inicio de un vistazo: tres preguntas, una cifra cada una
 *
 * Entrega B de `docs/superpowers/specs/2026-09-23-movi-de-un-vistazo-design.md`. El 23-sep, con los
 * datos reales del dueño, el Inicio mostraba **doce cifras** arriba del pliegue: Tu plata, el uso
 * condicionado, cuatro saldos, el patrimonio neto, ingresos, gastos, flujo, el disponible del
 * período y tres metas. Tres de ellas competían por contestar «¿cuánto tengo?» (Tu plata, Disponible,
 * Flujo), y dos veredictos opuestos convivían a un renglón de distancia: «Te pasaste por $563.456» en
 * rojo y «Vas bien» justo abajo. No se podía contestar «¿cómo estoy?» sin leer todo.
 *
 * Lo que hay en este archivo son las **reglas puras** de la versión nueva —el veredicto del hero, la
 * frase única del disponible, el patrimonio partido en tramos y la disposición en columnas—, aparte
 * de la pantalla para que las pruebas lean las frases exactas y para que ninguna decisión sobre la
 * plata del dueño quede enterrada en un `@Composable`.
 */

// ── ¿Cómo estoy? — el veredicto del hero ─────────────────────────────────────

/**
 * De qué lado cae el veredicto. **No se pinta como color**: el hero tiene un solo acento y las
 * cifras de «Entró / Salió» ya llevan el verde y el coral. Existe como contrato: es lo que la prueba
 * de coherencia compara contra la tarjeta del disponible (ver [fraseDelDisponible]).
 */
enum class TonoDelVeredicto { A_FAVOR, PAREJO, EN_CONTRA }

/** La frase que va debajo de «Tu plata», y de qué lado cae. */
data class Veredicto(val frase: String, val tono: TonoDelVeredicto)

/**
 * **El veredicto del período, en una frase y de UNA sola regla.**
 *
 * Mira el flujo —lo que entró contra lo que salió, las mismas cifras «Entró / Salió» que se ven
 * justo abajo— y, si algo lo explica, lo nombra:
 *
 * - **Salió más de lo que entró**: «Este período salieron $11,7M más de los que entraron — las
 *   cuotas de crédito fueron $12,9M». Las cuotas se nombran **antes** que cualquier categoría porque
 *   son la explicación que más le sirve al dueño: no son consumo, son deuda que baja (ver
 *   [CUOTA_CATEGORY]: el dueño decidió que la cuota cuente como gasto). Con sus doce créditos, un
 *   período en rojo casi siempre es eso, y leerlo al lado de la cifra evita el susto de «¿en qué me
 *   gasté $11 millones?». Sin cuotas, se nombra la categoría que más pesó.
 * - **Entró más de lo que salió** (o lo mismo), dicho con la diferencia.
 *
 * **Y si el flujo está a favor pero ya se pasó del disponible, lo dice en la misma frase**
 * («…, pero te pasaste del disponible por $X»). Es la regla de coherencia: con el disponible más
 * abajo en la misma pantalla, un «entró más de lo que salió» a secas arriba y un «te pasaste»
 * abajo serían otra vez dos veredictos opuestos. Con el flujo en contra no hace falta agregarlo: la
 * frase ya es la mala noticia, y la tarjeta del disponible dice cuánto.
 *
 * `null` cuando no hay nada que medir (ni entró ni salió nada): un «entró lo mismo que salió» sobre
 * un período vacío sería una afirmación sin datos.
 *
 * @param cuotasDeCredito lo gastado en el período bajo [CUOTA_CATEGORY].
 * @param mayorGasto la categoría que más pesó en el período, con su monto; `null` si no hay.
 * @param excesoDelDisponible cuánto se pasó del disponible del período (ver [excesoDelDisponible]);
 *   cero si no se pasó o si la tarjeta no se puede calcular.
 */
fun veredictoDelPeriodo(
    ingresos: Long,
    egresos: Long,
    cuotasDeCredito: Long = 0L,
    mayorGasto: Pair<String, Long>? = null,
    excesoDelDisponible: Long = 0L,
): Veredicto? {
    if (ingresos <= 0L && egresos <= 0L) return null
    val flujo = ingresos - egresos
    if (flujo < 0L) {
        val porque = when {
            cuotasDeCredito > 0L -> " — las cuotas de crédito fueron ${formatMoneyCompact(cuotasDeCredito)}"
            mayorGasto != null && mayorGasto.second > 0L ->
                " — lo que más pesó fue ${mayorGasto.first}: ${formatMoneyCompact(mayorGasto.second)}"
            else -> ""
        }
        return Veredicto(
            "Este período salieron ${formatMoneyCompact(-flujo)} más de los que entraron$porque",
            TonoDelVeredicto.EN_CONTRA,
        )
    }
    val base = if (flujo == 0L) "Este período entró lo mismo que salió"
    else "Este período entró ${formatMoneyCompact(flujo)} más de lo que salió"
    if (excesoDelDisponible > 0L) {
        return Veredicto(
            "$base, pero te pasaste del disponible por ${formatMoneyCompact(excesoDelDisponible)}",
            TonoDelVeredicto.EN_CONTRA,
        )
    }
    return Veredicto(base, if (flujo == 0L) TonoDelVeredicto.PAREJO else TonoDelVeredicto.A_FAVOR)
}

/**
 * [veredictoDelPeriodo] con lo que el Inicio ya cargó. `null` mientras el resumen no conteste: un
 * veredicto sobre cifras que no llegaron sería inventado.
 *
 * El disponible entra por [disponibleDelInicio], la MISMA cuenta que pinta la tarjeta: si la tarjeta
 * no se puede calcular, el veredicto no opina sobre ella.
 */
fun veredictoDelInicio(
    data: DashboardData,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
): Veredicto? {
    val summary = data.summary ?: return null
    val gasto = data.spentByCategory.orEmpty()
    return veredictoDelPeriodo(
        ingresos = summary.ingresos,
        egresos = summary.egresos,
        cuotasDeCredito = gasto[CUOTA_CATEGORY] ?: 0L,
        mayorGasto = mayorGastoDelPeriodo(gasto),
        excesoDelDisponible = disponibleDelInicio(data, hoy)?.let(::excesoDelDisponible) ?: 0L,
    )
}

/**
 * La categoría que más pesó, sin contar «Otros»: «lo que más pesó fue Otros» no le dice nada a nadie.
 * A igual monto, por nombre, para que no cambie entre recargas sin que nada haya cambiado.
 */
internal fun mayorGastoDelPeriodo(gasto: Map<String, Long>): Pair<String, Long>? =
    gasto.entries
        .filter { it.value > 0L && it.key.isNotBlank() }
        .filterNot { it.key.trim().equals("Otros", ignoreCase = true) || it.key.trim().equals("Otro", ignoreCase = true) }
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.lowercase() })
        .firstOrNull()
        ?.let { it.key to it.value }

/**
 * La fracción de «Entró» en la barra del hero, de 0 a 1, o `null` si no hay nada que dibujar.
 *
 * Una barra, dos tramos: lo que entró y lo que salió, cada uno sobre la suma de los dos. Con
 * $22,2M contra $33,9M se ve 40/60 sin leer un número.
 */
fun fraccionQueEntro(ingresos: Long, egresos: Long): Float? {
    val entro = ingresos.coerceAtLeast(0L)
    val salio = egresos.coerceAtLeast(0L)
    val total = entro + salio
    if (total <= 0L) return null
    return (entro.toDouble() / total).toFloat()
}

// ── El disponible, compacto y con UNA frase ──────────────────────────────────

/**
 * Cuánto se pasó del disponible del período: lo gastado menos el disponible, o cero.
 *
 * Sirve igual con margen (el disponible es la meta del período) que sin él (el disponible es cero o
 * negativo, y todo lo gastado es exceso). Es el número que comparten el veredicto del hero y la frase
 * de la tarjeta, y por eso hay una sola función que lo calcula.
 */
fun excesoDelDisponible(d: DisponibleDelPeriodo): Long =
    (d.periodo.gastado - d.disponible).coerceAtLeast(0L)

/** La frase de la tarjeta del disponible y el nivel con el que se pinta. */
data class FraseDelDisponible(val texto: String, val nivel: NivelDelGasto)

/**
 * **Una sola frase para las tres ventanas, y manda la peor.**
 *
 * Antes cada fila —el período, la semana, hoy— decía la suya, y con el período pasado el dueño leía
 * «Te pasaste por $563.456» en rojo y, un renglón abajo, «Vas bien: te quedan $1,7M para esta
 * semana». Las dos eran ciertas cada una en su ventana y juntas no significaban nada. Ahora las tres
 * filas muestran sus cifras (lo gastado contra la meta, con su barra) y la tarjeta dice UNA cosa:
 *
 * 1. sin margen, lo que ya decía ([sinMargen]);
 * 2. el período pasado → cuánto;
 * 3. la semana pasada → cuánto, y lo que le queda al período;
 * 4. hoy pasado → cuánto, y lo que le queda a la semana;
 * 5. lo demás → lo que queda y cuánto da por día, avisando si va por encima de lo previsto.
 *
 * **Nunca dice «vas bien».** Una frase de ánimo es la que se contradice con cualquier otra cosa roja
 * de la pantalla; «te quedan $X, unos $Y por día» dice lo mismo sin opinar.
 */
fun fraseDelDisponible(d: DisponibleDelPeriodo): FraseDelDisponible {
    if (!d.hayMargen) {
        return FraseDelDisponible(
            sinMargen(d),
            if (excesoDelDisponible(d) > 0L) NivelDelGasto.PASADO else NivelDelGasto.CERCA,
        )
    }
    val periodo = d.periodo
    if (periodo.nivel == NivelDelGasto.PASADO) {
        return FraseDelDisponible(
            "Te pasaste del disponible del período por ${formatMoneyCompact(-periodo.teQuedan)}",
            NivelDelGasto.PASADO,
        )
    }
    if (d.semana.nivel == NivelDelGasto.PASADO) {
        return FraseDelDisponible(
            "Esta semana te pasaste de la meta por ${formatMoneyCompact(-d.semana.teQuedan)} · " +
                "al período le quedan ${formatMoneyCompact(periodo.teQuedan)}",
            NivelDelGasto.PASADO,
        )
    }
    if (d.hoy.nivel == NivelDelGasto.PASADO) {
        return FraseDelDisponible(
            "Hoy te pasaste de la meta por ${formatMoneyCompact(-d.hoy.teQuedan)} · " +
                "a la semana le quedan ${formatMoneyCompact(d.semana.teQuedan)}",
            NivelDelGasto.PASADO,
        )
    }
    if (periodo.teQuedan == 0L) {
        return FraseDelDisponible("Ya usaste todo el disponible del período", NivelDelGasto.CERCA)
    }
    // El último día lo previsto a hoy ES la meta, y «por día» sería lo mismo que «para cerrar».
    if (d.diasQueQuedan <= 1) {
        return FraseDelDisponible(
            "Te quedan ${formatMoneyCompact(periodo.teQuedan)} para cerrar el período",
            periodo.nivel,
        )
    }
    val porDia = formatMoneyCompact(d.porDiaParaLoQueQueda ?: 0L)
    val queda = "te quedan ${formatMoneyCompact(periodo.teQuedan)}, unos $porDia por día"
    return if (periodo.contraElRitmo > 0L) {
        FraseDelDisponible(
            "Vas ${formatMoneyCompact(periodo.contraElRitmo)} por encima de lo previsto a hoy · $queda",
            NivelDelGasto.CERCA,
        )
    } else {
        FraseDelDisponible(queda.replaceFirstChar { it.uppercase() }, periodo.nivel)
    }
}

// ── Tu patrimonio, honesto ───────────────────────────────────────────────────

/** Un tramo del patrimonio: su nombre, cuánto es y de qué lado de la barra va. */
data class TramoDelPatrimonio(val nombre: String, val monto: Long, val esDeuda: Boolean)

/**
 * La tarjeta del patrimonio: lo que tienes contra lo que debes, el neto y los tramos con nombre.
 *
 * [tienes] y [debes] son los dos lados de la barra y nunca son negativos: una tarjeta sobrepagada
 * deja las deudas en negativo, y eso es plata **a favor** —va del lado de lo que tienes, con su
 * nombre—, no una deuda de menos. Así [neto] sigue siendo exactamente `Patrimonio.neto`.
 */
data class PatrimonioDelInicio(
    val tienes: Long,
    val debes: Long,
    val tramos: List<TramoDelPatrimonio>,
) {
    val neto: Long get() = tienes - debes

    /** El largo del lado «Tienes» de la barra, de 0 a 1. */
    val fraccionQueTienes: Float get() {
        val total = tienes + debes
        return if (total <= 0L) 0f else (tienes.toDouble() / total).toFloat()
    }
}

/**
 * [PatrimonioDelInicio] desde la regla única del patrimonio ([patrimonioDe], `:core`).
 *
 * **Se parte de las cuentas cuando ya llegaron, y del `patrimonio` del resumen solo si no.** Los dos
 * salen de la misma regla, pero el hero suma «Tu plata» sobre `data.accounts`: si la tarjeta leyera
 * del resumen, un movimiento que llegara entre una respuesta y la otra dejaría «Tu plata $558.350»
 * arriba y «Tu plata $600.000» en el tramo de abajo. Con la misma lista, coinciden por construcción.
 *
 * `null` si todavía no se sabe nada, o si no hay nada que mostrar (ni tienes ni debes).
 */
fun patrimonioDelInicio(data: DashboardData): PatrimonioDelInicio? {
    val p = data.accounts?.let { patrimonioDe(it) } ?: data.patrimonio ?: return null
    return patrimonioDelInicio(p)
}

/** La misma regla sobre un [Patrimonio] ya partido. Es la que prueban las pruebas. */
fun patrimonioDelInicio(p: Patrimonio): PatrimonioDelInicio? {
    val aFavor = (-p.deudas).coerceAtLeast(0L)
    val tienes = (p.loQueTienes + aFavor).coerceAtLeast(0L)
    val debes = p.deudas.coerceAtLeast(0L)
    if (tienes == 0L && debes == 0L) return null
    val tramos = listOfNotNull(
        p.tuPlata.takeIf { it != 0L }?.let { TramoDelPatrimonio("Tu plata", it, esDeuda = false) },
        p.condicionado.takeIf { it > 0L }?.let {
            TramoDelPatrimonio(
                p.condicionadoA?.let { a -> "Uso condicionado · $a" } ?: "Uso condicionado",
                it,
                esDeuda = false,
            )
        },
        p.bienes.takeIf { it > 0L }?.let { TramoDelPatrimonio("Bienes", it, esDeuda = false) },
        aFavor.takeIf { it > 0L }?.let { TramoDelPatrimonio("A favor en créditos", it, esDeuda = false) },
        debes.takeIf { it > 0L }?.let { TramoDelPatrimonio("Deudas", it, esDeuda = true) },
    )
    return PatrimonioDelInicio(tienes = tienes, debes = debes, tramos = tramos)
}

/** A dónde lleva tocar un tramo: las deudas se miran en Créditos, lo demás en Cuentas. */
fun destinoDelTramo(tramo: TramoDelPatrimonio): Screen = if (tramo.esDeuda) Screen.Credits else Screen.Accounts

// ── En escritorio, dos columnas ──────────────────────────────────────────────

/**
 * Desde qué ancho el Inicio se parte en dos columnas. 900 dp: con el rail de la izquierda, dos
 * columnas de ~400 dp, que es lo que una tarjeta del Inicio necesita para que «$14,4M de $13,8M» no
 * se parta. Por debajo, una columna.
 */
val ANCHO_PARA_DOS_COLUMNAS: Dp = 900.dp

/**
 * El ancho máximo del Inicio. Las demás pantallas siguen en la columna de 600 dp de siempre (ver
 * [anchoMaximoDeLaPantalla]); el Inicio es el único que tiene algo que poner al lado.
 */
val ANCHO_MAXIMO_DEL_INICIO: Dp = 1200.dp

/** El ancho de la columna de siempre, la del teléfono. */
val ANCHO_DE_UNA_COLUMNA: Dp = 600.dp

/**
 * El ancho máximo del contenido de [pantalla] en la cáscara (`App.kt`).
 *
 * En escritorio el Inicio era una tira de 500 px en el medio de un lienzo de 1.400: el dueño tiene
 * la web abierta en el computador, y ahí todo lo que mira quedaba apretado en un tercio de la
 * pantalla. Solo el Inicio se ensancha: una lista de movimientos a 1.200 dp de ancho no se lee mejor,
 * se lee peor.
 */
fun anchoMaximoDeLaPantalla(pantalla: Screen): Dp =
    if (pantalla == Screen.Dashboard) ANCHO_MAXIMO_DEL_INICIO else ANCHO_DE_UNA_COLUMNA

/**
 * Qué va en la columna derecha en escritorio: lo que **viene** y lo que se **pregunta**. La izquierda
 * se queda con el estado —¿cómo estoy? y ¿en qué se va?— y con «Para revisar». Todo tipo que no está
 * acá —los genéricos del Editor, un tipo nuevo— va a la izquierda: es la columna que se lee primero.
 */
val TIPOS_DE_LA_COLUMNA_DERECHA: Set<String> = setOf(
    "PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "UPCOMING_PAYMENTS", "DISPONIBLE_DEL_PERIODO", "PATRIMONIO",
)

/** Las secciones del Inicio repartidas en columnas. Con una sola, [derecha] va vacía. */
data class ColumnasDelInicio(val izquierda: List<ScreenSection>, val derecha: List<ScreenSection>) {
    val sonDos: Boolean get() = derecha.isNotEmpty()
}

/**
 * Reparte [secciones] (ya filtradas por [visibleSections]) según el [ancho] disponible.
 *
 * - Menos de [ANCHO_PARA_DOS_COLUMNAS]: una columna, en el orden de la definición — el orden del
 *   teléfono, que es el que viaja en `screen_definitions`.
 * - Desde ahí: izquierda y derecha por tipo ([TIPOS_DE_LA_COLUMNA_DERECHA]), **conservando el orden
 *   de la definición dentro de cada una**. Si una de las dos quedaría vacía (una base recién
 *   estrenada tiene hero y poco más), se vuelve a una columna: una columna vacía al lado no es
 *   escritorio, es un hueco.
 */
fun columnasDelInicio(secciones: List<ScreenSection>, ancho: Dp): ColumnasDelInicio {
    if (ancho < ANCHO_PARA_DOS_COLUMNAS) return ColumnasDelInicio(secciones, emptyList())
    val (derecha, izquierda) = secciones.partition { it.type in TIPOS_DE_LA_COLUMNA_DERECHA }
    if (izquierda.isEmpty() || derecha.isEmpty()) return ColumnasDelInicio(secciones, emptyList())
    return ColumnasDelInicio(izquierda, derecha)
}
