package com.jvillada.movi.ui.plan

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.ui.components.graficaQueSeToca
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.apartadoFueraDeLosFijos
import com.jvillada.movi.shared.model.cajaHastaQueTePaguen
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.NoSePudoLeerEnLaPantalla
import com.jvillada.movi.ui.components.LocalErroresDeLaPantalla
import com.jvillada.movi.ui.components.formatMoneyAbreviada
import com.jvillada.movi.ui.accounts.ESPACIO_QUE_NO_SE_PARTE
import com.jvillada.movi.ui.accounts.UNION_QUE_NO_SE_PARTE
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.FormaDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.theme.Grafica
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * # «Tu plata, día a día»: la caja proyectada en Plan (Ola 4)
 *
 * Debajo de «Cuánto puedes gastar», una línea con lo que vas a tener cada día hasta el cierre del
 * período, el día más justo marcado y, si alguna fecha queda en negativo, cuál y por qué pago.
 *
 * Nada de esto calcula plata nuevo: la cuenta es [cajaHastaQueTePaguen] (en `:core`), sobre **la lista
 * «Pagos del período» que Plan tiene en pantalla** ([checklistDelPeriodoDe]) y la del período siguiente
 * sobre las mismas respuestas, y sobre lo de las cuentas de [patrimonioDe]. Lo único que se pide aparte
 * es el supuesto del gasto del día a día, que necesita la historia del server — y se dice como
 * supuesto, con de qué períodos sale.
 *
 * **Vuelta 70 · hasta que te paguen.** La línea llega al próximo salario (o a 30 días) y dibuja **lo
 * libre**: el saldo menos tu ahorro del período y lo apartado. Una sola frase dice la cifra
 * y la liga con «Te quedan»; el renglón de abajo ya no la repite.
 *
 * **Vuelta 75 · una cifra y una frase.** La cifra titular es lo libre del día más justo; debajo, «Hasta el
 * 25 nov · tu día más justo es el 5 nov»; todo lo demás, en «¿Qué supone?» ([loQueSupone]). Sin salario,
 * una línea encima lo dice con «Agregar» ([lineaSinSalario]).
 */

/** El título de la sección. */
const val TITULO_CAJA_PROYECTADA = "Tu plata, día a día"

const val TAG_CAJA_PROYECTADA = "caja-proyectada"
const val TAG_GRAFICA_DE_LA_CAJA = "grafica-de-la-caja"

/**
 * Lo que la sección necesita de [DashboardData] para proyectar, o `null` si todavía falta algo: el
 * período, la lista del período (vencimientos y ocurrencias) y Tu plata.
 *
 * **Sin cuentas no hay caja** (vuelta de diseño 11): las cuentas contestaron vacías y Tu plata sería
 * un $0 que nadie anotó. La sección decía «Hoy es tu día más justo: tienes $0» sobre una línea plana
 * en cero, una frase falsa para quien no cargó nada. Lo mismo vale para «Tu día más justo» de Hoy, que
 * sale de acá.
 */
internal fun cajaDelPeriodoDe(
    data: DashboardData,
    gasto: GastoDelDiaADia,
    hoy: LocalDate,
): CajaProyectada? {
    if (sinCuentas(data)) return null
    val periodo = data.periodoActual ?: return null
    val upcoming = data.upcoming ?: return null
    val ocurrencias = data.ocurrencias ?: return null
    val pagos = checklistDelPeriodoDe(data)
    // Vuelta 70: el período siguiente, sobre las MISMAS respuestas: lo que vence antes del salario.
    val delSiguiente = checklistDelPeriodo(upcoming, ocurrencias, periodoSiguiente(periodo), data.ajustesDePeriodo, conElSiguienteVencimiento = true)
    val ultimoDia = inicioDelPeriodo(periodoSiguiente(periodo), data.ajustesDePeriodo).minus(1, DateTimeUnit.DAY)
    // El ahorro del período: el mismo que resta «Te quedan» (cero sin elegir nada).
    val ahorro = data.plataDelDisponible?.ahorroDelPeriodo ?: 0L
    // Lo de las cuentas, con lo apartado adentro (un apartado no mueve plata); lo libre resta lo apartado
    // que ningún pago de la caja cubre, con la regla del cruce de siempre (`apartadoFueraDeLosFijos`).
    // Sin cuentas, lo del server: Tu plata + lo apartado, y lo que «Te quedan» resta de eso.
    val cuentas = data.accounts
    val (enLasCuentas, apartadoFuera) = if (cuentas != null) {
        val apartados = data.apartados.orEmpty()
        val p = patrimonioDe(cuentas, apartados)
        (p.tuPlata + p.apartado) to { filas: List<PagoDelPeriodo> -> apartadoFueraDeLosFijos(cuentas, apartados, filas) }
    } else {
        val p = data.patrimonio ?: return null
        val fuera = data.plataDelDisponible?.apartado ?: p.apartado
        (p.tuPlata + p.apartado) to { _: List<PagoDelPeriodo> -> fuera }
    }
    return cajaHastaQueTePaguen(
        enLasCuentasHoy = enLasCuentas,
        delPeriodo = pagos,
        delSiguiente = delSiguiente,
        gastoDiario = gasto.porDia,
        hoy = hoy.toString(),
        ultimoDiaDelPeriodo = ultimoDia.toString(),
        ahorro = ahorro,
        // Vuelta 75: sin salario, hasta el arranque del período siguiente si el dueño eligió su período.
        conElPeriodoElegido = !data.ajustesDePeriodo.esMesDeCalendario,
        apartadoFueraDe = apartadoFuera,
    )
}

/** Las cuentas ya contestaron y no hay ninguna (`null` es que todavía no se sabe). */
private fun sinCuentas(data: DashboardData): Boolean = data.accounts?.isEmpty() == true

// ── Lo que dice ─────────────────────────────────────────────────────────────
//
// Puro y aparte para que las pruebas lean las frases exactas. Todo en tuteo.
//
// Vuelta 75 · **una cifra y una frase.** La caja decía cinco renglones en rojo con seis cifras en dos
// formas («−$683.361» junto a «−$1,3M», `r5-dia-09-pagos-s2.png`). Ahora arriba va UNA cifra —lo libre
// del día más justo ([CajaProyectada.diaMasBajo]), en rojo solo ella si es negativa— y debajo una frase
// corta sin cifras ([fraseCortaDeLaCaja]). Todo lo demás —lo que habrá en las cuentas, el ahorro, la
// diferencia con «Te quedan» y su causa, el primer día en rojo, el supuesto— va a «¿Qué supone?»
// ([loQueSupone]). **Una sola forma de cifra**, la abreviada en todas las escalas ([formatMoneyAbreviada]:
// «$683 mil», «$1,3M»), en la cifra, las frases, la leyenda y la gráfica.

/**
 * **La frase de debajo de la cifra**: hasta cuándo mira la caja y cuál es el día más justo. «Hasta el 25
 * nov · tu día más justo es el 5 nov»; si ese día lo libre no alcanza, «Hasta el 25 nov · el 5 nov no te
 * alcanza». Sin cifras: la cifra ya está arriba. Es la misma regla del renglón «¿Me alcanza?» de Inicio.
 */
internal fun fraseCortaDeLaCaja(caja: CajaProyectada, hoy: LocalDate, ultimoDiaDelPeriodo: LocalDate? = null): String {
    // Vuelta 78: sin salario y con el rojo después del cierre, la misma frase que el renglón de Inicio.
    com.jvillada.movi.ui.dashboard.noTeAlcanzariaSinSalario(caja, ultimoDiaDelPeriodo)?.let { return it }
    val hasta = "Hasta el ${fechaCorta(caja.alCierre.fecha)}"
    val bajo = caja.diaMasBajo
    val cuando = if (bajo.fecha == hoy.toString()) "hoy" else "el ${fechaCorta(bajo.fecha)}"
    // Revisión de la 75: si el peor día es el último del horizonte, la fecha va una vez («ese día»).
    val ese = if (bajo.fecha == caja.alCierre.fecha && cuando != "hoy") "ese día" else cuando
    return if (bajo.libre < 0L) "$hasta · $ese no te alcanza" else "$hasta · tu día más justo es $cuando"
}

/** Lo que dice el lector de pantalla de la cifra titular: qué es, además de cuánto. */
internal fun descripcionDeLaCifraDeLaCaja(caja: CajaProyectada): String =
    "Libre en tu día más justo: ${formatMoneyAbreviada(caja.diaMasBajo.libre)}"

/**
 * **Lo que habrá en las cuentas el día más justo** y de dónde sale la diferencia con lo libre (el
 * ahorro, lo apartado), o `null` sin ahorro ni apartados. «En las cuentas habrá $9,4M el 5 nov; de eso
 * $8M es tu ahorro.»; con el ahorro más grande que el saldo, «… habrá $6,8M el 5 nov: menos que tu ahorro
 * de $8M.».
 */
internal fun lineaDeLasCuentas(caja: CajaProyectada, hoy: LocalDate): String? {
    if (caja.reservado <= 0L) return null
    val bajo = caja.diaMasBajo
    val cuando = if (bajo.fecha == hoy.toString()) "En las cuentas hay hoy ${cifraQueNoSeParte(bajo.saldo)}"
    else "En las cuentas habrá ${cifraQueNoSeParte(bajo.saldo)} el ${fechaCorta(bajo.fecha)}"
    // Revisión de la 70: con el ahorro más grande que el saldo, «de eso $8M es tu ahorro» sobre $6,8M se
    // contradecía. Lo que hay no alcanza a cubrir lo reservado, y eso es lo que se dice.
    if (bajo.saldo < caja.reservado) return "$cuando: menos que ${loReservado(caja)}."
    val deEso = buildList {
        if (caja.ahorro > 0L) add("${formatMoneyAbreviada(caja.ahorro)} es tu ahorro")
        if (caja.apartado > 0L) add("${formatMoneyAbreviada(caja.apartado)} está apartado")
    }.joinToString(" y ")
    return "$cuando; de eso $deEso."
}

/** «tu ahorro de $8M», «lo apartado ($500 mil)» o «tu ahorro y lo apartado ($8,5M)». */
private fun loReservado(caja: CajaProyectada): String = when {
    caja.apartado <= 0L -> "tu ahorro de ${formatMoneyAbreviada(caja.ahorro)}"
    caja.ahorro <= 0L -> "lo apartado (${formatMoneyAbreviada(caja.apartado)})"
    else -> "tu ahorro y lo apartado (${formatMoneyAbreviada(caja.reservado)})"
}

/**
 * Lo que se sabe para comparar lo libre de hoy con «Te quedan» (revisión de la 70).
 *
 * «Lo libre de hoy» es el del primer día de la caja —al cerrar hoy, con lo que vence hoy o ya venció
 * restado—: una sola cifra de «hoy».
 *
 * @property teQuedan la cifra del héroe ([DisponibleDelPeriodo.periodo] `.teQuedan`).
 * @property fijosQueFaltan los pagos del período, en pesos, que vencen **después de hoy**: «Te quedan» ya
 *   los restó y la caja de hoy todavía no.
 * @property porRecibir los ingresos del período, en pesos, que se esperan después de hoy: «Te quedan» ya
 *   los cuenta y las cuentas no.
 * @property comprasConTarjetaSinPagar las compras con tarjeta del período todavía sin pagar, medidas por
 *   el server (`DashboardSummary.comprasConTarjetaSinPagar`): «Te quedan» las contó como gastadas y las
 *   cuentas no. `null` si no se pudieron medir: entonces esa causa no se nombra.
 */
internal data class ComparacionConTeQuedan(
    val teQuedan: Long,
    val fijosQueFaltan: Long,
    val porRecibir: Long,
    val comprasConTarjetaSinPagar: Long?,
)

/** Hasta cuánto lo libre de hoy y «Te quedan» se dan por iguales: un redondeo no es una diferencia. */
internal const val TOLERANCIA_CON_TE_QUEDAN: Long = 1_000L

/** El rótulo de «Te quedan» en la cláusula: lo que el héroe dice que te queda. */
internal const val LO_QUE_TE_QUEDA_PARA_GASTAR: String = "lo que te queda para gastar"

/**
 * **La diferencia entre lo libre de hoy y «Te quedan»**, en una frase, o `null` si no la hay (más de
 * [TOLERANCIA_CON_TE_QUEDAN]). Nombra una causa solo si **una medida** la explica, con un margen del
 * 10 % (o $1.000):
 *
 * - **los pagos que faltan del período** menos lo que falta por cobrar: si explican la diferencia entera;
 * - **las compras con tarjeta sin pagar** (medidas, no supuestas): si lo que queda después de lo de
 *   arriba es la mayor parte de la diferencia y las compras lo explican. No basta con tener deuda en la
 *   tarjeta: la de una tarjeta con diferidos a 36 cuotas siempre «alcanzaría», y lo que sobra puede ser
 *   tu parte de un gasto que pagó otro, una liquidación que te pagaron o un apartado cruzado con un fijo
 *   del período siguiente;
 * - si no, la diferencia sola, sin inventar la causa.
 *
 * Es una explicación, no una cuenta nueva: ninguna cifra de la app cambia por esto. Vuelta 75: va en
 * «¿Qué supone?», como frase propia.
 */
internal fun clausulaDeLaDiferencia(caja: CajaProyectada, c: ComparacionConTeQuedan): String? {
    val libreDeHoy = caja.dias.first().libre
    val diferencia = libreDeHoy - c.teQuedan
    if (kotlin.math.abs(diferencia) <= TOLERANCIA_CON_TE_QUEDAN) return null
    val cifra = formatMoneyAbreviada(kotlin.math.abs(diferencia))
    val sentido = if (diferencia > 0L) "más" else "menos"
    val base = "Hoy tienes ${formatMoneyAbreviada(libreDeHoy)} libres, $cifra $sentido que $LO_QUE_TE_QUEDA_PARA_GASTAR"
    val margen = maxOf(TOLERANCIA_CON_TE_QUEDAN, kotlin.math.abs(diferencia) / 10)
    val porLoQueFalta = c.fijosQueFaltan - c.porRecibir
    val resto = diferencia - porLoQueFalta
    val compras = c.comprasConTarjetaSinPagar
    val causa = when {
        porLoQueFalta != 0L && kotlin.math.abs(resto) <= margen ->
            if (porLoQueFalta > 0L) "ahí ya están descontados los pagos que faltan del período"
            else "ahí ya cuenta lo que falta por cobrar"
        diferencia > 0L && compras != null && compras > 0L && resto * 2 >= diferencia &&
            kotlin.math.abs(resto - compras) <= margen ->
            "tus compras con tarjeta se pagan después"
        else -> null
    }
    return if (causa != null) "$base: $causa." else "$base."
}

/**
 * [ComparacionConTeQuedan] de lo que la pantalla tiene, o `null` si no hay «Te quedan» que comparar.
 * Las mismas funciones del héroe ([disponibleDelInicio]) y de la lista ([checklistDelPeriodoDe]); las
 * compras con tarjeta, del server.
 */
internal fun comparacionConTeQuedan(data: DashboardData, hoy: LocalDate): ComparacionConTeQuedan? {
    val disponible = disponibleDelInicio(data, hoy) ?: return null
    // Lo que vence hoy o ya venció lo resta la caja hoy: queda fuera de la explicación.
    val porVenir = checklistDelPeriodoDe(data)
        .filter { !it.pagado && !it.esAbono && !it.montoEsSaldo && it.moneda == "COP" && it.vence > hoy.toString() }
    return ComparacionConTeQuedan(
        teQuedan = disponible.periodo.teQuedan,
        fijosQueFaltan = porVenir.filter { !it.esIngreso }.sumOf { it.monto },
        porRecibir = porVenir.filter { it.esIngreso }.sumOf { it.monto },
        comprasConTarjetaSinPagar = data.comprasConTarjetaSinPagar,
    )
}

/**
 * **El primer día en que no alcanza**, con la fecha y lo que lo causa, cuando NO es el día más justo
 * (ese ya lo dice la cifra); `null` si lo libre no queda en negativo o si es el mismo día.
 */
internal fun fraseDelRojo(caja: CajaProyectada, hoy: LocalDate): String? {
    val rojo = caja.primerDiaEnRojo ?: return null
    if (rojo.fecha == caja.diaMasBajo.fecha) return null
    val cuando = if (rojo.fecha == hoy.toString()) "Hoy" else "El ${fechaEnPalabras(rojo.fecha, hoy)}"
    val porque = rojo.pagoMasGrande?.let { "por ${it.nombre} (${formatMoneyAbreviada(-it.monto)})" } ?: "por tu gasto del día a día"
    return "$cuando ya quedarías en$ESPACIO_QUE_NO_SE_PARTE${cifraQueNoSeParte(rojo.libre)} libres $porque."
}

/**
 * **El supuesto**, dicho como supuesto, con de dónde sale. Vuelta de diseño 28: «gastas en promedio $X
 * por día», no «unos $X por día»: la única cifra «por día» de la app sin su nombre es la del héroe
 * (lo que queda entre los días que faltan); esta es tu promedio, y lo dice.
 */
internal fun fraseDelSupuesto(gasto: GastoDelDiaADia): String {
    val porDia = gasto.porDia
        ?: return "Todavía no hay un período cerrado para estimar tu gasto del día a día: la línea solo resta tus pagos del período."
    val deDonde = when (gasto.periodos.size) {
        1 -> "en tu período de ${mesDelPrefijo(gasto.periodos.single().periodo)}"
        else -> "en tus últimos ${gasto.periodos.size} períodos"
    }
    return "Supone que sigues como siempre: $GASTAS_EN_PROMEDIO ${formatMoneyAbreviada(porDia)} por día ($deDonde)."
}

/** «2026-09» → «septiembre», o el prefijo tal cual si no se entiende. */
private fun mesDelPrefijo(prefijo: String): String =
    periodoDelPrefijo(prefijo)?.let { MESES_DEL_ANIO[it.month - 1] } ?: prefijo

/** De dónde sale la línea, en renglones cortos. */
internal fun deDondeSaleLaCaja(caja: CajaProyectada): List<String> = buildList {
    val hasta = when {
        caja.hastaElSalario -> "hasta el día que llega tu salario"
        // Vuelta 75: sin salario y con tu período elegido, hasta que arranca el siguiente.
        caja.hastaElPeriodoSiguiente -> "hasta el día que empieza tu próximo período, sin sumar ningún ingreso"
        caja.salarioQueNoCuenta -> "30 días (tu ingreso fijo no está anotado en pesos con su monto)"
        else -> "30 días (no tienes un ingreso fijo anotado)"
    }
    add(
        "Parte de lo que hay hoy en tus cuentas (${formatMoneyAbreviada(caja.tuPlataHoy)}) y mira $hasta. Resta lo que " +
            "falta de «Pagos del período», y del siguiente lo que vence antes, el día que vence (una tarjeta, su pago " +
            "mínimo), y suma lo que falta por cobrar.",
    )
    if (caja.reservado > 0L) {
        val que = buildList {
            if (caja.ahorro > 0L) add("tu ahorro del período (${formatMoneyAbreviada(caja.ahorro)})")
            if (caja.apartado > 0L) add("lo apartado (${formatMoneyAbreviada(caja.apartado)})")
        }.joinToString(" y ")
        add("Lo libre es ese saldo menos $que: siguen en tus cuentas, pero no son para gastar.")
    }
    add("Tu gasto del día a día se resta desde mañana, como si saliera de tu plata ese mismo día aunque lo pagues con tarjeta.")
}

/**
 * **«¿Qué supone?»**, en orden (vuelta 75): lo que habrá en las cuentas y tu ahorro, la diferencia con
 * «Te quedan» y su causa medida, el primer día en rojo si no es el más justo, el supuesto del gasto del
 * día a día y de dónde sale la línea. Todo lo que antes iba en el párrafo rojo de arriba.
 */
internal fun loQueSupone(
    caja: CajaProyectada,
    gasto: GastoDelDiaADia,
    hoy: LocalDate,
    comparacion: ComparacionConTeQuedan?,
): List<String> = listOfNotNull(
    lineaDeLasCuentas(caja, hoy),
    comparacion?.let { clausulaDeLaDiferencia(caja, it) },
    fraseDelRojo(caja, hoy),
    fraseDelSupuesto(gasto),
) + deDondeSaleLaCaja(caja)

/**
 * Una cifra corta con el menos pegado al «$» (revisión de la 75): «quedarías en −» y «$1,2M» quedaban en
 * dos renglones. [UNION_QUE_NO_SE_PARTE] no se ve.
 */
private fun cifraQueNoSeParte(monto: Long): String =
    formatMoneyAbreviada(monto).replace("\u2212$", "\u2212$UNION_QUE_NO_SE_PARTE$")

/** Lo que no entró a la cuenta, en una frase, o `null` si entró todo. */
internal fun fraseDeLoQueFalta(caja: CajaProyectada): String? =
    caja.sinContar.takeIf { it.isNotEmpty() }?.let { "No entra a la cuenta: ${it.joinToString(" · ")}." }

/**
 * Vuelta 75 · **la línea de encima de la caja sin salario**: con un ingreso fijo en pesos anotado no hay
 * nada que decir (`null`); sin él, Movi no suma ningún ingreso, y lo dice antes de la cifra con «Agregar».
 * Con un ingreso fijo en otra moneda o sin monto tampoco se dice: ya hay uno, y «¿Qué supone?» explica por
 * qué no cuenta.
 */
internal fun lineaSinSalario(caja: CajaProyectada): String? =
    if (caja.hastaElSalario || caja.salarioQueNoCuenta) null else SIN_TU_SALARIO

/** La línea de [lineaSinSalario]. */
const val SIN_TU_SALARIO: String = "Sin tu salario, Movi supone que no te entra nada"

/** El enlace de [lineaSinSalario]: abre la hoja de ingreso recurrente con el día del corte. */
const val AGREGAR_SALARIO: String = "Agregar"

/** El tag de la línea sin salario. */
const val TAG_LINEA_SIN_SALARIO: String = "caja-sin-salario"

/** El tag de la cifra titular de la caja (vuelta 75). */
const val TAG_CIFRA_DE_LA_CAJA: String = "cifra-de-la-caja"

// ── La sección ──────────────────────────────────────────────────────────────

/**
 * @param recarga sube con cada acción del tablero de pagos (igual que la tarjeta de arriba).
 */
@Composable
internal fun SeccionCajaProyectada(
    data: DashboardData,
    cargando: Boolean,
    recarga: Int,
    onNavigate: (Screen) -> Unit,
    /** El aire de arriba ([Medida.entreSecciones]): en la columna del disponible separa de «Tus períodos». */
    aireArriba: Boolean = true,
    /** El aire de abajo, solo cuando la sección se pinta: en la lista la sigue el resto del tablero. */
    aireAbajo: Boolean = false,
    /**
     * Vuelta 70 · **la fuente está caída**: algo de lo que la caja sale (los pagos, el resumen, el perfil)
     * no contestó en su última lectura. La caja no se pinta con lo de antes: va al aviso único de la
     * pantalla, como las demás secciones (`r4-err-pagos.png`: «$10,5M» debajo del error).
     */
    fuenteCaida: Boolean = false,
    /**
     * Vuelta 75 · «Agregar» de la línea sin salario: abre la hoja de ingreso recurrente ya en Ingreso y
     * con el día del corte. `null` = no se ofrece (sin la línea).
     */
    onAgregarSalario: (() -> Unit)? = null,
) {
    // Sin cuentas no hay nada que proyectar, y se sabe sin esperar el gasto del día a día: ni el
    // esqueleto, ni el error de esa lectura (ver [cajaDelPeriodoDe]).
    if (sinCuentas(data)) return
    var reintento by remember { mutableStateOf(0) }
    val gastoLeido = rememberLectura(ClaveDeLectura.GastoDelDiaADia, reintento = recarga + reintento) {
        Repositories.wallets.getGastoDelDiaADia()
    }
    var verDeDondeSale by rememberSaveable { mutableStateOf(false) }
    val hoy = remember { epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) }
    val gasto = gastoLeido.valor
    val caja = gasto?.let { cajaDelPeriodoDe(data, it, hoy) }
    val noSePudo = gastoLeido.valor == null && gastoLeido.terminada && !gastoLeido.actualizando
    // Vuelta 70: con su fuente caída la caja no se pinta, ni con lo de antes: lo que proyectaría ya no se
    // puede dar por actual. En Pagos lo dice el aviso único de arriba; suelta, su tarjeta.
    val sinFuente = fuenteCaida && !cargando
    // Sin período, sin lista o sin cuentas no hay nada honesto que proyectar: no se pinta nada, ni
    // el título (un título sobre un hueco es una promesa que no se cumple).
    if (caja == null && !noSePudo && !sinFuente && !(gastoLeido.actualizando || cargando)) return
    // Vuelta 68: en Pagos, el aviso único de arriba lo dice; esta sección no pinta ni su título.
    if ((sinFuente || (caja == null && noSePudo)) && LocalErroresDeLaPantalla.current != null) {
        NoSePudoLeerEnLaPantalla(clave = "caja", texto = "No pudimos proyectar tu plata", onReintentar = { reintento++ })
        return
    }

    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla).testTag(TAG_CAJA_PROYECTADA)) {
        if (aireArriba) Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(
            titulo = TITULO_CAJA_PROYECTADA,
            // No «¿De dónde sale?»: esa es la de la tarjeta de arriba, y dos iguales en la misma
            // pantalla no se distinguen. Acá lo que se abre son los supuestos.
            accion = if (verDeDondeSale) "Ocultar supuestos" else "¿Qué supone?",
            onAccion = { if (caja != null) verDeDondeSale = !verDeDondeSale },
        )
        when {
            sinFuente ->
                NoSePudoLeer("No pudimos proyectar tu plata", onReintentar = { reintento++ })
            caja != null -> Superficie(modifier = Modifier.fillMaxWidth()) {
                CuerpoDeLaCaja(
                    caja, gasto, hoy, verDeDondeSale, onNavigate, remember(data, hoy) { comparacionConTeQuedan(data, hoy) }, onAgregarSalario,
                    ultimoDiaDelPeriodo = remember(data) { com.jvillada.movi.ui.dashboard.ultimoDiaDelPeriodoDe(data) },
                )
            }
            noSePudo ->
                NoSePudoLeer("No pudimos proyectar tu plata", onReintentar = { reintento++ })
            gastoLeido.actualizando || cargando -> Superficie(modifier = Modifier.fillMaxWidth()) {
                // Vuelta 75: el mismo esqueleto que la cifra y su frase.
                LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.cifraDeTarjeta)
                LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
                Spacer(Modifier.height(Movi.espacios.medio))
                Spacer(Modifier.height(ALTO_DE_LA_GRAFICA))
            }
            else -> Unit
        }
        if (aireAbajo) Spacer(Modifier.height(Medida.entreSecciones))
    }
}

private val ALTO_DE_LA_GRAFICA = Grafica.alto

@Composable
private fun CuerpoDeLaCaja(
    caja: CajaProyectada,
    gasto: GastoDelDiaADia,
    hoy: LocalDate,
    verDeDondeSale: Boolean,
    onNavigate: (Screen) -> Unit,
    comparacion: ComparacionConTeQuedan?,
    onAgregarSalario: (() -> Unit)?,
    ultimoDiaDelPeriodo: LocalDate? = null,
) {
    // Vuelta 75: sin salario, antes de la cifra, lo que la caja supone y cómo cambiarlo.
    val sinSalario = lineaSinSalario(caja)
    if (sinSalario != null && onAgregarSalario != null) {
        Row(
            modifier = Modifier.fillMaxWidth().testTag(TAG_LINEA_SIN_SALARIO),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
        ) {
            Text(sinSalario, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.weight(1f))
            Boton(
                texto = AGREGAR_SALARIO,
                onClick = onAgregarSalario,
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
            )
        }
        Spacer(Modifier.height(Movi.espacios.corto))
    }
    // Vuelta 75: UNA cifra —lo libre del día más justo, en rojo solo ella si no alcanza— y una frase corta.
    val descripcion = descripcionDeLaCifraDeLaCaja(caja)
    Cifra(
        caja.diaMasBajo.libre,
        VarianteDeCifra.Grande,
        tono = if (caja.diaMasBajo.libre < 0L) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
        forma = FormaDeCifra.Abreviada,
        modifier = Modifier.testTag(TAG_CIFRA_DE_LA_CAJA).semantics { contentDescription = descripcion },
    )
    Text(
        fraseCortaDeLaCaja(caja, hoy, ultimoDiaDelPeriodo),
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        modifier = Modifier.testTag(TAG_FRASE_DE_PUENTE),
    )
    Spacer(Modifier.height(Movi.espacios.medio))
    // Ola 4 · Ver la plata: el día elegido al deslizar (o tocar un pago) se dice en el renglón de
    // abajo, en el lugar de «Al cierre»: el mismo alto, nada se mueve.
    var elegido by remember(caja) { mutableStateOf<Int?>(null) }
    GraficaDeLaCaja(caja, elegido = elegido, alElegir = { elegido = it })
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fechaCorta(caja.dias.first().fecha), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        val dia = elegido?.let { caja.dias.getOrNull(it) }
        Text(
            text = dia?.let(::leyendaDelDiaDeLaCaja) ?: leyendaDelCierre(caja),
            style = Movi.textos.apoyo,
            color = when {
                (dia?.libre ?: 0L) < 0 -> Movi.colores.sale
                dia != null -> Movi.colores.texto
                else -> Movi.colores.textoMedio
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_LEYENDA_DE_LA_CAJA)
                .then(if (dia == null) Modifier.semantics { contentDescription = descripcionDelCierre(caja) } else Modifier),
        )
        Text(fechaCorta(caja.dias.last().fecha), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
    }
    fraseDeLoQueFalta(caja)?.let { falta ->
        Spacer(Modifier.height(Movi.espacios.medio))
        Text(falta, style = Movi.textos.apoyo, color = Movi.colores.aviso)
        Spacer(Modifier.height(Movi.espacios.corto))
        Boton(
            texto = "Cargar en Créditos",
            onClick = { onNavigate(Screen.Credits) },
            variante = VarianteDeBoton.Secundario,
            tamano = TamanoDeBoton.S,
        )
    }
    if (verDeDondeSale) {
        Spacer(Modifier.height(Movi.espacios.medio))
        Column(modifier = Modifier.testTag(TAG_LO_QUE_SUPONE)) {
            loQueSupone(caja, gasto, hoy, comparacion).forEach {
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Movi.espacios.minimo))
            }
        }
    }
}

/** El tag de lo que abre «¿Qué supone?» (vuelta 75). */
const val TAG_LO_QUE_SUPONE: String = "caja-lo-que-supone"

/** El rótulo de lo que se gasta por día en la caja: un promedio, dicho como promedio. */
const val GASTAS_EN_PROMEDIO: String = "gastas en promedio"

/** La frase de puente de la caja (vuelta 70). */
const val TAG_FRASE_DE_PUENTE: String = "frase-de-puente-de-la-caja"

/** El renglón de abajo sin un día elegido y sin salario: cómo leer la línea, sin otra cifra. */
const val TOCA_UN_DIA: String = "Toca un día para verlo"

/**
 * **El renglón de abajo de la caja**, sin un día elegido. Vuelta 70: ya no repite una cifra de saldo
 * («Saldo proyectado: $10,5M» era la tercera vez que salía la misma cifra en la sección): con salario,
 * dice lo que lo sube al final («Llega tu salario: +$7M»); sin él, cómo leer la línea.
 */
internal fun leyendaDelCierre(caja: CajaProyectada): String =
    caja.salario?.let { "Llega tu salario: +${formatMoneyAbreviada(it.monto)}" } ?: TOCA_UN_DIA

/** Lo que oye el lector de pantalla en el renglón de abajo: lo libre del último día, con su fecha. */
internal fun descripcionDelCierre(caja: CajaProyectada): String {
    val cuando = if (caja.salario != null) "el ${fechaCorta(caja.alCierre.fecha)}, al llegar tu salario" else "el ${fechaCorta(caja.alCierre.fecha)}"
    return "Libre $cuando: ${formatMoneyAbreviada(caja.alCierre.libre)}"
}

/** «17 oct». */
private fun fechaCorta(iso: String): String {
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return "${fecha.dayOfMonth} ${MESES_DEL_ANIO[fecha.monthNumber - 1].take(3)}"
}

/** El renglón de la leyenda de la caja. */
const val TAG_LEYENDA_DE_LA_CAJA = "leyenda-de-la-caja"

/**
 * **Lo que se dice de un día elegido**: la fecha, el saldo proyectado y, si ese día vence algo, qué.
 * «17 oct: $1,2M · Arriendo, Netflix».
 */
internal fun leyendaDelDiaDeLaCaja(dia: DiaDeLaCaja): String {
    // Vuelta 70: lo libre de ese día (la cifra titular), no el saldo de las cuentas.
    val pagos = dia.movimientos.filter { it.monto < 0 }.joinToString(", ") { it.nombre }
    val base = "${fechaCorta(dia.fecha)}: ${cifraQueNoSeParte(dia.libre)} libres"
    return if (pagos.isEmpty()) base else "$base · $pagos"
}

/** Los días con un pago pendiente: los marcadores sobre la línea. */
internal fun diasConPago(caja: CajaProyectada): List<Int> =
    caja.dias.indices.filter { i -> caja.dias[i].movimientos.any { it.monto < 0 } }

/**
 * **La escala vertical de la caja**: el rango de los datos con un margen arriba y abajo, no desde
 * cero. Una caja de $8M que baja a $6M dibujada desde cero es una raya casi plana; dibujada entre
 * $5,7M y $8,3M dice cuánto baja y cuándo. El cero solo entra si los datos lo cruzan.
 */
internal fun escalaDeLaCaja(saldos: List<Long>): Pair<Long, Long> {
    val maximo = saldos.max()
    val minimo = saldos.min()
    // Un sexto del rango de aire; con una línea plana, un poco del valor (o $1, si es cero).
    val margen = ((maximo - minimo) / 6).takeIf { it > 0 } ?: (kotlin.math.abs(maximo) / 10).coerceAtLeast(1L)
    return (minimo - margen) to (maximo + margen)
}

/**
 * La línea: un punto por día, la escala ajustada a los datos ([escalaDeLaCaja]), el cero punteado si
 * la línea lo cruza, el tramo bajo cero en rojo, un marcador chico en cada día con un pago pendiente
 * y el día más justo resaltado con su cifra. Deslizar (o pasar el mouse) elige un día; tocar también.
 */
@Composable
private fun GraficaDeLaCaja(caja: CajaProyectada, elegido: Int?, alElegir: (Int?) -> Unit) {
    val linea = Movi.colores.textoMedio
    val rojo = Movi.colores.sale
    val cero = Movi.colores.hilo
    val marcador = Movi.colores.textoApagado
    val guia = Movi.colores.hilo
    val fondo = Movi.colores.tarjeta
    val punto = if (caja.diaMasBajo.libre < 0) rojo else Movi.colores.aviso
    // Vuelta 70: la línea dibuja lo libre de cada día, la cifra titular.
    val saldos = caja.dias.map { it.libre }
    val conPago = remember(caja) { diasConPago(caja) }
    val medidor = rememberTextMeasurer()
    val estiloDeLaCifra = Movi.textos.apoyo.copy(color = if (caja.diaMasBajo.libre < 0) rojo else Movi.colores.texto)
    val cifraDelMasBajo = formatMoneyAbreviada(caja.diaMasBajo.libre)
    val descripcion = "Gráfica de tu plata libre día a día: de ${formatMoneyAbreviada(saldos.first())} hoy a " +
        "${formatMoneyAbreviada(saldos.last())} el ${fechaCorta(caja.alCierre.fecha)}, el punto más bajo $cifraDelMasBajo"
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTO_DE_LA_GRAFICA)
            .testTag(TAG_GRAFICA_DE_LA_CAJA)
            .semantics { contentDescription = descripcion }
            .graficaQueSeToca(cuantos = saldos.size, datos = caja, alElegir = alElegir, alTocar = alElegir, enPuntos = true),
    ) {
        val (piso, tope) = escalaDeLaCaja(saldos)
        val rango = (tope - piso).toFloat().takeIf { it > 0f } ?: 1f
        val etiqueta = medidor.measure(cifraDelMasBajo, estiloDeLaCifra)
        // Arriba queda lugar para la cifra del día más justo cuando cae cerca del techo.
        val arriba = Grafica.margen.toPx()
        val abajo = Grafica.margen.toPx()
        val alto = size.height - arriba - abajo
        fun x(i: Int) = if (saldos.size == 1) size.width / 2 else size.width * i / (saldos.size - 1)
        fun y(v: Long) = arriba + alto * (tope - v).toFloat() / rango
        if (piso < 0L && tope > 0L) {
            drawLine(
                color = cero,
                start = Offset(0f, y(0L)),
                end = Offset(size.width, y(0L)),
                strokeWidth = Medida.trazo.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
        elegido?.let { i ->
            drawLine(guia, Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = Medida.trazo.toPx())
        }
        val trazo = Stroke(width = Grafica.trazo.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Cada tramo con el color de su punta derecha: lo que baja de cero se ve rojo desde el día
        // en que cruza.
        saldos.indices.drop(1).forEach { i ->
            val path = Path().apply {
                moveTo(x(i - 1), y(saldos[i - 1]))
                lineTo(x(i), y(saldos[i]))
            }
            drawPath(path, color = if (saldos[i] < 0) rojo else linea, style = trazo)
        }
        // Los pagos pendientes: un punto chico con aro, sobre la línea.
        conPago.forEach { i ->
            val c = Offset(x(i), y(saldos[i]))
            drawCircle(fondo, radius = Grafica.aroDelPunto.toPx(), center = c)
            drawCircle(marcador, radius = Grafica.punto.toPx(), center = c)
        }
        elegido?.let { i -> drawCircle(linea, radius = Grafica.aroDelPunto.toPx(), center = Offset(x(i), y(saldos[i]))) }
        // El día más justo: su punto y su cifra, encima del punto (o debajo, si no cabe arriba).
        val iBajo = caja.dias.indexOf(caja.diaMasBajo)
        val centro = Offset(x(iBajo), y(saldos[iBajo]))
        drawCircle(punto, radius = Grafica.puntoDestacado.toPx(), center = centro)
        val ancho = etiqueta.size.width.toFloat()
        val altoDeLaEtiqueta = etiqueta.size.height.toFloat()
        val izquierda = (centro.x - ancho / 2).coerceIn(0f, (size.width - ancho).coerceAtLeast(0f))
        val encima = centro.y - Grafica.margen.toPx() - altoDeLaEtiqueta
        val top = if (encima >= 0f) encima else centro.y + Espacio.xs.toPx()
        drawText(etiqueta, topLeft = Offset(izquierda, top))
    }
}
