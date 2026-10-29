package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.unit.Dp
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ANCHO_PARA_DOS_COLUMNAS
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
 * **Y si el flujo está a favor pero el disponible está en rojo, lo dice en la misma frase**
 * («…, pero te pasaste del disponible por $X», o «…, pero los fijos del período superan lo que
 * tenías y lo que entró por $X»). Es la regla de coherencia: con el disponible más abajo en la
 * misma pantalla, un «entró más de lo que salió» a secas arriba y un «te pasaste» abajo serían otra
 * vez dos veredictos opuestos. Con el flujo en contra no hace falta agregarlo: la frase ya es la
 * mala noticia, y la tarjeta del disponible dice cuánto.
 *
 * `null` cuando no hay nada que medir (ni entró ni salió nada): un «entró lo mismo que salió» sobre
 * un período vacío sería una afirmación sin datos.
 *
 * @param cuotasDeCredito lo gastado en el período bajo [CUOTA_CATEGORY].
 * @param mayorGasto la categoría que más pesó en el período, con su monto; `null` si no hay.
 * @param avisoDelDisponible lo que el hero agrega cuando el disponible está en rojo, ya escrito y
 *   con la MISMA cifra que la tarjeta (ver [avisoDelDisponible]); `null` si no está en rojo o si la
 *   tarjeta no se puede calcular.
 */
fun veredictoDelPeriodo(
    ingresos: Long,
    egresos: Long,
    cuotasDeCredito: Long = 0L,
    mayorGasto: Pair<String, Long>? = null,
    avisoDelDisponible: String? = null,
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
    if (avisoDelDisponible != null) {
        return Veredicto("$base, pero $avisoDelDisponible", TonoDelVeredicto.EN_CONTRA)
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
    // Ola 1: cuando el hero ya abre con «Te quedan / Te pasaste por» ([teQuedanDelInicio]), el
    // aviso del disponible está dicho arriba con su cifra, y repetirlo en el veredicto lo decía
    // dos veces seguidas en la misma tarjeta.
    conAvisoDelDisponible: Boolean = true,
): Veredicto? {
    val summary = data.summary ?: return null
    val gasto = data.spentByCategory.orEmpty()
    return veredictoDelPeriodo(
        ingresos = summary.ingresos,
        egresos = summary.egresos,
        cuotasDeCredito = gasto[CUOTA_CATEGORY] ?: 0L,
        mayorGasto = mayorGastoDelPeriodo(gasto),
        avisoDelDisponible = if (conAvisoDelDisponible) disponibleDelInicio(data, hoy)?.let(::avisoDelDisponible) else null,
    )
}

/**
 * **El veredicto en una línea corta, con cifras y con verbo** (Ola 3 · Hoy en una mirada, 22-oct-2026;
 * vuelta de diseño 22).
 *
 * Es la línea de respaldo de [fraseDelHeroe]: la que va cuando el Disponible no se puede calcular y no
 * hay un «gastaste» de Plan que repetir. Dice el flujo del período con su verbo —la Ola 3 lo había dejado
 * en «−$893.300 este período · Mercado $392.100», y la revisión del 27-oct encontró que no se entendía si
 * era neto, gasto o déficit, ni por qué aparecía Mercado—:
 *
 * - En contra: «Salió $893.300 más de lo que entró · lo que más: Mercado $392.100» (o las cuotas).
 * - A favor: «Entró $3M más de lo que salió», y si el disponible está en rojo, «· te pasaste…».
 * - Parejo: «Entró lo mismo que salió».
 *
 * **La misma regla, los mismos datos** que [veredictoDelPeriodo] (la frase larga, para el lector de
 * pantalla): si una cambia sin la otra, `HoyEnUnaMiradaTest` lo nota. Movi AI, el correo y la tarjeta
 * de cierre tienen sus propias frases y no se tocan.
 */
fun veredictoCortoDelPeriodo(
    ingresos: Long,
    egresos: Long,
    cuotasDeCredito: Long = 0L,
    mayorGasto: Pair<String, Long>? = null,
    avisoDelDisponible: String? = null,
): String? {
    if (ingresos <= 0L && egresos <= 0L) return null
    val flujo = ingresos - egresos
    if (flujo < 0L) {
        val porque = when {
            cuotasDeCredito > 0L -> " · cuotas de crédito: ${formatMoneyCompact(cuotasDeCredito)}"
            mayorGasto != null && mayorGasto.second > 0L -> " · lo que más: ${mayorGasto.first} ${formatMoneyCompact(mayorGasto.second)}"
            else -> ""
        }
        return "Salió ${formatMoneyCompact(-flujo)} más de lo que entró$porque"
    }
    if (flujo == 0L) return "Entró lo mismo que salió"
    return "Entró ${formatMoneyCompact(flujo)} más de lo que salió" + (avisoDelDisponible?.let { " · $it" } ?: "")
}

/**
 * [veredictoCortoDelPeriodo] con lo que el Inicio ya cargó; mismas condiciones que
 * [veredictoDelInicio] (sin resumen, `null`).
 */
fun veredictoCortoDelInicio(
    data: DashboardData,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
    conAvisoDelDisponible: Boolean = true,
): String? {
    val summary = data.summary ?: return null
    val gasto = data.spentByCategory.orEmpty()
    return veredictoCortoDelPeriodo(
        ingresos = summary.ingresos,
        egresos = summary.egresos,
        cuotasDeCredito = gasto[CUOTA_CATEGORY] ?: 0L,
        mayorGasto = mayorGastoDelPeriodo(gasto),
        avisoDelDisponible = if (conAvisoDelDisponible) disponibleDelInicio(data, hoy)?.let(::avisoDelDisponible) else null,
    )
}

/**
 * **La frase de abajo del héroe de Hoy**: lo que se ve ([corta]) y lo que oye el lector de pantalla
 * ([larga]), que dicen lo mismo.
 */
data class FraseDelHeroe(val corta: String, val larga: String)

/**
 * **La frase de abajo del héroe, con la misma cifra que Plan** (vuelta de diseño 22).
 *
 * La revisión del 27-oct: Hoy decía «−$893.300 este período · Mercado $316.050» y Plan, en la tarjeta
 * del Disponible, «gastaste $1,7M». Dos cifras del mismo período sin decir qué era cada una. Ahora, si
 * el Disponible se puede calcular, Hoy dice **lo que Plan dice**: «Gastaste $1,7M · lo que más: Mercado
 * $316.050» —[DisponibleDelPeriodo.periodo] `.gastado`, el gasto del día a día del período (sin los
 * pagos fijos, que el Disponible ya restó), escrito igual que la columna «Período» de Plan
 * (`formatMoneyCompact`)—. «Lo que más» es la categoría más grande **que cabe en esa cifra**: el
 * arriendo o una cuota son fijos, no gasto del día a día, y «Gastaste $1,7M · lo que más: Vivienda
 * $1,8M» sería una parte más grande que el todo.
 *
 * Sin Disponible (sin ingresos ni plata, o con una lectura que no llegó), la línea de respaldo
 * ([veredictoCortoDelPeriodo]) con su verbo. `null` mientras el resumen no contesta, o sin nada que
 * medir. El héroe («Te quedan…») no se toca: su definición es una decisión pendiente del dueño.
 */
fun fraseDelHeroe(
    data: DashboardData,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
    conAvisoDelDisponible: Boolean = true,
): FraseDelHeroe? {
    // Sin resumen o sin nada que medir (ni entró ni salió nada) no hay frase: lo decide el veredicto.
    val larga = veredictoDelInicio(data, hoy, conAvisoDelDisponible)?.frase ?: return null
    val disponible = disponibleDelInicio(data, hoy)
        ?: return veredictoCortoDelInicio(data, hoy, conAvisoDelDisponible)?.let { FraseDelHeroe(it, larga) }
    val gastado = disponible.periodo.gastado
    if (gastado <= 0L) return FraseDelHeroe(SIN_GASTO_EN_EL_PERIODO, "Este período todavía no gastaste nada en el día a día")
    val mayor = mayorGastoDelPeriodo(
        data.spentByCategory.orEmpty().filter { (categoria, monto) -> categoria != CUOTA_CATEGORY && monto <= gastado },
    )
    val cifra = formatMoneyCompact(gastado)
    val corta = "Gastaste $cifra" + (mayor?.let { " · lo que más: ${it.first} ${formatMoneyCompact(it.second)}" } ?: "")
    val larguisima = "Este período gastaste $cifra en el día a día" +
        (mayor?.let { "; lo que más pesó fue ${it.first}, con ${formatMoneyCompact(it.second)}" } ?: "")
    return FraseDelHeroe(corta, larguisima)
}

/** Lo que dice la frase del héroe cuando el período todavía no tiene gasto del día a día. */
const val SIN_GASTO_EN_EL_PERIODO: String = "Todavía no gastas nada este período"

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
 * **Lo que el hero dice cuando el disponible está en rojo, con la cifra y las palabras de la
 * tarjeta**, o `null` si no lo está.
 *
 * Antes el hero sumaba en una sola cifra dos cosas distintas: lo que los fijos superan (la cifra
 * grande de la tarjeta, «−$629.882») y lo variable ya gastado. El dueño leía arriba «te pasaste por
 * $841.832» y abajo «−$629.882», dos «te pasaste» que no coincidían (revisión del 29-sep). Ahora el
 * hero repite lo que dice la tarjeta:
 *
 * - **Sin margen** (los fijos se llevan todo): la frase de la tarjeta, [sinMargen], con su cifra
 *   `−disponible`. Lo variable gastado se sigue viendo en la tarjeta, en la columna del período.
 * - **Con margen** y el período pasado: «te pasaste del disponible por $X», la misma cifra que la
 *   frase de la tarjeta («Te pasaste del disponible del período por $X»).
 */
fun avisoDelDisponible(d: DisponibleDelPeriodo): String? = when {
    !d.hayMargen -> if (d.disponible < 0L || d.periodo.gastado > 0L) {
        sinMargen(d).replaceFirstChar { it.lowercase() }
    } else {
        null
    }
    d.periodo.teQuedan < 0L -> "te pasaste del disponible por ${formatMoneyCompact(-d.periodo.teQuedan)}"
    else -> null
}

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
 * 5. lo demás → lo que queda y para cuántos días, avisando si va por encima de lo previsto.
 *
 * **Nunca dice «vas bien».** Una frase de ánimo es la que se contradice con cualquier otra cosa roja
 * de la pantalla; «te quedan $X para N días» dice lo mismo sin opinar.
 *
 * **La cifra diaria al lado de «para N días» es lo que queda entre esos días**
 * ([DisponibleDelPeriodo.porDiaParaLoQueQueda], hoy incluido, igual que «para N días»). Del 29-sep a
 * la vuelta de diseño 1 iba la meta diaria ([DisponibleDelPeriodo.metaPorDia], el disponible entre
 * los días del período ENTERO), y junto a «para 6 días» se leía «puedes gastar $404.412 por día en los
 * 6 días que faltan» cuando en realidad eran $1,8M. Pegada a «para N días», la única cifra que se lee
 * bien es la división de esos dos números. La columna «Meta de hoy» usa la misma regla desde la vuelta
 * 60: lo que quedaba al empezar el día entre los días que quedan.
 */
fun fraseDelDisponible(d: DisponibleDelPeriodo): FraseDelDisponible {
    if (!d.hayMargen) {
        return FraseDelDisponible(
            sinMargen(d),
            // En rojo si los fijos superan lo que hay o si ya se gastó algo sin margen para gastar.
            if (avisoDelDisponible(d) != null) NivelDelGasto.PASADO else NivelDelGasto.CERCA,
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
    // El último día «por día» sería lo mismo que lo que queda: «para hoy» lo dice solo.
    if (d.diasQueQuedan <= 1) {
        return FraseDelDisponible(
            "Te quedan ${formatMoneyCompact(periodo.teQuedan)} para hoy",
            periodo.nivel,
        )
    }
    // Acá quedan dos días o más (el último ya salió arriba), así que siempre es «días».
    val queda = "te quedan ${formatMoneyCompact(periodo.teQuedan)} para ${d.diasQueQuedan} días"
    return if (periodo.contraElRitmo > 0L) {
        FraseDelDisponible(
            "Vas ${formatMoneyCompact(periodo.contraElRitmo)} por encima de lo previsto a hoy · $queda",
            NivelDelGasto.CERCA,
        )
    } else {
        FraseDelDisponible(
            "${queda.replaceFirstChar { it.uppercase() }} · ${porDiaQueQueda(d)}",
            periodo.nivel,
        )
    }
}

/**
 * «$1,8M por día»: lo que queda del período entre los días que faltan, hoy incluido
 * ([DisponibleDelPeriodo.porDiaParaLoQueQueda]). Va pegado a «para N días» en el héroe y en la frase
 * de Plan; solo se llama con margen y algo por gastar (si no, `null` → $0).
 */
internal fun porDiaQueQueda(d: DisponibleDelPeriodo): String =
    "${formatMoneyCompact(d.porDiaParaLoQueQueda ?: 0L)} por día"

// ── «Te quedan»: la cifra principal de Hoy ───────────────────────────────────

/**
 * **La cifra grande de Hoy**: cuánto te queda por gastar en el período, o cuánto te pasaste.
 *
 * Ola 1 · Movi avisa (1-oct). Hoy abría con «Tu plata», el saldo total, que no contesta la pregunta
 * del día: ¿cuánto puedo gastar? El dueño decidió que la cifra principal sea «Te quedan $X».
 *
 * **No es una cuenta nueva: es lo que le queda al período en la tarjeta Disponible de Plan** (su frase)
 * ([DisponibleDelPeriodo.periodo], [VentanaDelDisponible.teQuedan]), leída del MISMO
 * [DisponibleDelPeriodo] que pinta esa tarjeta ([disponibleDelInicio]). Si Hoy y Plan pudieran dar
 * dos números distintos para «¿cuánto me queda?», ninguno de los dos se podría creer. Lo fija
 * `TeQuedanEnHoyTest` (pura) y `TeQuedanEnHoyEnPantallaTest` (las dos pantallas montadas).
 *
 * @property teQuedan la cifra con signo, idéntica a `periodo.teQuedan`.
 * @property monto lo que se escribe después del [rotulo]: sin signo, porque el rótulo ya dice de qué
 *   lado está («Te pasaste por $629.882» y no «Te quedan −$629.882»). Hoy lo escribe entero
 *   (`formatCOP`, «$4.963.400»); la frase de Plan, compacto («$5M»): mismo valor, otro ancho.
 * @property enRojo el mismo criterio que pinta de rojo la frase y la columna «Período» en Plan.
 * @property detalle la línea chica de abajo: para cuántos días y cuánto da por día, o qué ventana se
 *   pasó.
 */
data class TeQuedanDeHoy(
    val rotulo: String,
    val teQuedan: Long,
    val enRojo: Boolean,
    val detalle: String,
    val nivelDelDetalle: NivelDelGasto,
) {
    val monto: Long get() = kotlin.math.abs(teQuedan)
}

/** El rótulo cuando queda algo (o nada). */
const val ROTULO_TE_QUEDAN: String = "Te quedan"

/** El rótulo cuando lo que le queda al período está en negativo. */
const val ROTULO_TE_PASASTE: String = "Te pasaste por"

/**
 * [TeQuedanDeHoy] desde la tarjeta del disponible. Puro: lo prueban las pruebas sin pantalla.
 *
 * El detalle no repite la cifra grande —ya está arriba— y **la cifra diaria es lo que queda entre los
 * días que faltan** (ver [fraseDelDisponible]): «para 6 días · $1,8M por día». Lo que manda, en orden:
 *
 * 1. sin margen: la frase de la tarjeta ([sinMargen]), porque es la explicación de por qué no queda;
 * 2. el período pasado: cuántos días faltan todavía para el corte;
 * 3. el ritmo por encima de lo previsto: lo dice después de los días;
 * 4. lo demás: para cuántos días y cuánto da por día; el último día, «para hoy».
 *
 * **La semana o el día pasados ya no van acá** (vuelta 45: una sola alarma). «Esta semana te pasaste
 * de la meta por $1,3M» salía en el héroe del Inicio Y en la frase de Pagos, y Pagos además pintaba
 * tres barras rojas. Ahora esa alarma sale una vez, en Pagos, con qué hacer ([alertaDelDisponible]):
 * el detalle dice lo de siempre —para cuántos días y cuánto da por día— en Inicio y en Pagos.
 */
fun teQuedanDeHoy(d: DisponibleDelPeriodo): TeQuedanDeHoy {
    val periodo = d.periodo
    val enRojo = periodo.nivel == NivelDelGasto.PASADO
    val rotulo = if (periodo.teQuedan < 0L) ROTULO_TE_PASASTE else ROTULO_TE_QUEDAN
    val dias = if (d.diasQueQuedan <= 1) "para hoy" else "para ${d.diasQueQuedan} días"
    val (detalle, nivel) = when {
        !d.hayMargen -> sinMargen(d) to
            (if (avisoDelDisponible(d) != null) NivelDelGasto.PASADO else NivelDelGasto.CERCA)
        periodo.teQuedan < 0L -> (
            if (d.diasQueQuedan <= 1) "del disponible del período · hoy es el último día"
            else "del disponible del período · faltan ${d.diasQueQuedan} días para el corte"
            ) to NivelDelGasto.PASADO
        periodo.teQuedan == 0L -> "Ya usaste todo el disponible del período" to NivelDelGasto.CERCA
        periodo.contraElRitmo > 0L ->
            "$dias · vas ${formatMoneyCompact(periodo.contraElRitmo)} por encima de lo previsto a hoy" to
                NivelDelGasto.CERCA
        d.diasQueQuedan <= 1 -> dias to periodo.nivel
        else -> "$dias · ${porDiaQueQueda(d)}" to periodo.nivel
    }
    return TeQuedanDeHoy(
        rotulo = rotulo,
        teQuedan = periodo.teQuedan,
        enRojo = enRojo,
        detalle = detalle,
        nivelDelDetalle = nivel,
    )
}

// ── La alarma del disponible: una vez, en Pagos, y con qué hacer ─────────────

/** El enlace de la alarma: abre «En qué se va» (vuelta 45). */
const val MIRA_EN_QUE_SE_FUE: String = "Mira en qué se fue"

/**
 * **La alarma de la tarjeta del disponible en Pagos** (vuelta 45): una frase de qué pasó —o ninguna,
 * si ya la dice la cifra de arriba— y el enlace [MIRA_EN_QUE_SE_FUE], que abre «En qué se va».
 *
 * @property texto «Esta semana te pasaste de la meta por $1,3M.», o `null` cuando la cifra protagonista
 *   ya lo dice («Te pasaste por $X»): ahí la alarma es solo qué hacer.
 */
data class AlertaDelDisponible(val texto: String?)

/**
 * **Una sola alarma, y que diga qué hacer** (vuelta 45). Antes «Te pasaste de la meta por $1,3M»
 * salía en el héroe del Inicio y en la frase de Pagos, y Pagos pintaba además tres barras rojas
 * (período, semana y meta de hoy). Ahora:
 *
 * - **sin margen**: no hay alarma aparte; el detalle de la cifra ya explica por qué no queda
 *   ([sinMargen]) y no hay meta contra qué pasarse;
 * - **el período pasado**: la cifra dice «Te pasaste por $X» en rojo; la alarma es solo el enlace;
 * - **la semana o el día pasados** (el período no): «Esta semana te pasaste de la meta por $1,3M.» o
 *   «Hoy te pasaste de la meta por $64.268.», con el enlace;
 * - lo demás: `null`.
 *
 * El Inicio no la repite: su héroe dice la cifra y, si el período se pasó, «Te pasaste por…».
 */
fun alertaDelDisponible(d: DisponibleDelPeriodo): AlertaDelDisponible? = when {
    !d.hayMargen -> null
    d.periodo.teQuedan < 0L -> AlertaDelDisponible(texto = null)
    d.semana.nivel == NivelDelGasto.PASADO ->
        AlertaDelDisponible("Esta semana te pasaste de la meta por ${formatMoneyCompact(-d.semana.teQuedan)}.")
    d.hoy.nivel == NivelDelGasto.PASADO ->
        AlertaDelDisponible("Hoy te pasaste de la meta por ${formatMoneyCompact(-d.hoy.teQuedan)}.")
    else -> null
}

/**
 * **Qué barra de las tres va en alerta** (vuelta 45): la del período si te pasaste del período; si no,
 * ninguna. Las demás van en tinta: una barra llena dice que se gastó la meta, sin otro color.
 */
fun barraEnAlerta(d: DisponibleDelPeriodo, delPeriodo: Boolean): Boolean =
    delPeriodo && d.hayMargen && d.periodo.nivel == NivelDelGasto.PASADO

/**
 * [teQuedanDeHoy] con lo que el Inicio ya cargó, o `null` si la tarjeta del disponible no se puede
 * afirmar (falta una lectura, o no hay ingresos ni plata anotados). En ese caso el hero vuelve a
 * mostrar «Tu plata» como cifra grande: es lo único honesto que hay para decir.
 *
 * Pasa por [disponibleDelInicio] —la misma función que llama la pestaña Plan—, nunca por una copia.
 */
fun teQuedanDelInicio(
    data: DashboardData,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
): TeQuedanDeHoy? = disponibleDelInicio(data, hoy)?.let(::teQuedanDeHoy)

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
    val p = data.accounts?.let { patrimonioDe(it, data.apartados.orEmpty()) } ?: data.patrimonio ?: return null
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
        // Lo apartado (el colegio de nov-dic) es tuyo pero ya tiene dueño: va aparte, al lado de Tu plata.
        p.apartado.takeIf { it > 0L }?.let {
            TramoDelPatrimonio(p.apartadoPara?.let { a -> "Apartado · $a" } ?: "Apartado", it, esDeuda = false)
        },
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

// Los anchos (el umbral de las dos columnas, el tope del Inicio y el de una columna) viven desde
// la Ola W1 en `ui/components/Disposicion.kt`, con la tabla de topes de todas las pantallas: el
// Inicio es un `Disposicion.Tablero`, y lo que mide lo decide la cáscara con esa tabla.

/**
 * **¿El Inicio va en dos columnas?** Desde [ANCHO_PARA_DOS_COLUMNAS], y solo si hay al menos dos
 * bloques: una columna vacía al lado no es escritorio, es un hueco. Por debajo, una columna en el orden
 * de la definición —el del teléfono—.
 */
fun inicioEnDosColumnas(bloques: Int, ancho: Dp): Boolean = ancho >= ANCHO_PARA_DOS_COLUMNAS && bloques >= 2

/**
 * **Cómo se reparten los bloques del Inicio en dos columnas parejas** (vuelta 39). Devuelve, para cada
 * bloque en el orden de la definición, `true` si va a la izquierda.
 *
 * Los de [fijosALaIzquierda] (el héroe y la guía) van a la izquierda; cada uno de los demás va a la
 * columna que en ese momento es **más corta**, así las dos terminan parejas con los datos que haya (la
 * revisión: la izquierda acababa en ~600 px y la derecha seguía con movimientos, cuentas y widgets). A
 * igual alto, la izquierda. Dentro de cada columna se conserva el orden.
 *
 * La derecha arranca en [arribaDeLaDerecha]. Desde la vuelta 41 la banda amarilla va solo detrás de la
 * izquierda (`anchoDeLaBanda`), así que la derecha —la cuadrícula, con su círculo amarillo— arranca
 * arriba, sobre el crema, y nunca sobre el amarillo. Entre bloques, [separacion]; un bloque de alto cero
 * (una sección que esta vez no pintó nada) no suma separación.
 */
fun repartoEnDosColumnas(
    altos: List<Int>,
    fijosALaIzquierda: Set<Int>,
    arribaDeLaDerecha: Int,
    separacion: Int,
): List<Boolean> {
    var izquierda = 0
    var derecha = arribaDeLaDerecha
    var hayIzquierda = false
    var hayDerecha = false
    return altos.mapIndexed { i, alto ->
        val aLaIzquierda = i in fijosALaIzquierda || izquierda <= derecha
        if (alto > 0) {
            if (aLaIzquierda) {
                izquierda += alto + if (hayIzquierda) separacion else 0
                hayIzquierda = true
            } else {
                derecha += alto + if (hayDerecha) separacion else 0
                hayDerecha = true
            }
        }
        aLaIzquierda
    }
}
