package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.components.formatMoneyCompactSinOcultar
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.ROTULO_TE_PASASTE
import com.jvillada.movi.ui.dashboard.conElPeriodoDe
import com.jvillada.movi.ui.dashboard.conteoPorRevisarDelInicio
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.teQuedanDeHoy
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.transactions.resumenDelDia
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/*
 * # El resumen de la mañana (vuelta 68)
 *
 * Decisión del dueño (29-oct): una notificación a las 8:00 —«Ayer gastaste $85.000 · te quedan $1,3M
 * para 4 días · 2 avisos por confirmar»—, apagable. **Sin IA y sin reglas de plata nuevas**: el gasto
 * de ayer es el «Gastaste» del encabezado de ese día en Actividad ([resumenDelDia], la regla de
 * «Gastos»), la cifra es la del héroe del Inicio ([teQuedanDeHoy] sobre [disponibleDelInicio]) y lo
 * pendiente es el número de «Por revisar» ([conteoPorRevisarDelInicio]).
 *
 * Acá vive lo que decide y lo que dice, puro y probado (`ResumenDeLaMananaTest`). Quién lo pide y lo
 * pinta es `AvisoDeLaMananaWorker`, en `:androidApp`: usa la instantánea del Inicio si es de hoy y, si
 * no, pide las cifras al server.
 */

/** La hora del resumen de la mañana, en Bogotá. */
const val HORA_DEL_RESUMEN_DE_LA_MANANA: Int = 8

/** Lo que abre tocar el resumen de la mañana: el Inicio. */
const val ABRIR_INICIO: String = "inicio"

/**
 * Cuántos movimientos recientes se piden para saber el gasto de ayer (`/api/events/by-day?ultimos=N`):
 * días enteros, del más nuevo al más viejo, hasta juntar N movimientos. Con 60 alcanzan a cubrir ayer
 * salvo un hoy con 60 movimientos, y ahí [gastoDeAyer] dice que no sabe en vez de inventar.
 */
const val MOVIMIENTOS_PARA_EL_GASTO_DE_AYER: Int = 60

/** Lo que suena: el texto y la huella con la que se recuerda que ya sonó (el día). */
data class ResumenDeLaManana(val huella: String, val texto: TextoDeAviso)

/** La huella del resumen de [ahora]: el día, en Bogotá. El mismo día no suena dos veces. */
fun huellaDelResumenDeLaManana(ahora: Long): String = "manana:" + epochMillisToAppDate(ahora).toString()

/** ¿[leidaEn] es de hoy (en Bogotá)? Una cifra de ayer no se dice como si fuera de hoy. */
fun esDeHoy(leidaEn: Long?, ahora: Long): Boolean =
    leidaEn != null && epochMillisToAppDate(leidaEn) == epochMillisToAppDate(ahora)

/**
 * **Lo gastado ayer**, con la regla de «Gastos» de Actividad ([resumenDelDia]): la suma del «Gastaste»
 * del encabezado de ese día, con tu parte de lo que pagó otra persona del grupo.
 *
 * [dias] son días **enteros**, del más nuevo al más viejo (lo que contesta `?ultimos=N`). Si ayer está,
 * su gasto; si no está pero hay un día más viejo, ayer no hubo movimientos (0); si la lista no llega
 * hasta ayer, `null`: no se sabe, y no se dice.
 */
fun gastoDeAyer(
    dias: List<EventDay>?,
    tuParteDeOtros: List<FinancialEvent>,
    ahora: Long,
    tiposDeCuenta: Map<String, com.jvillada.movi.shared.model.AccountType>,
): Long? {
    if (dias == null) return null
    val ayer = epochMillisToAppDate(ahora).minus(DatePeriod(days = 1))
    val fechas = dias.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
    val deAyer = dias.firstOrNull { it.date == ayer.toString() }
    if (deAyer == null && fechas.none { it < ayer }) return null
    val tuParte = tuParteDeOtros.filter { epochMillisToAppDate(it.timestamp) == ayer }
    return resumenDelDia(deAyer?.items.orEmpty() + tuParte, tiposDeCuenta).gastaste
}

/** «Ayer gastaste $85.000», o «Ayer no anotaste gastos» con cero. */
internal fun fraseDelGastoDeAyer(gasto: Long, ocultos: Boolean): String =
    if (gasto <= 0L) "Ayer no anotaste gastos"
    else "Ayer gastaste ${if (ocultos) PLATA_OCULTA else formatCOPSinOcultar(gasto)}"

/**
 * «te quedan $1,3M para 4 días», «te quedan $40.000 para hoy» o «te pasaste por $200.000»: la cifra del
 * héroe del Inicio, con los días que dice su detalle. `null` si el Inicio no puede afirmarla (vuelve a
 * «Tu plata», que no es lo que este resumen cuenta).
 */
internal fun fraseDeLoQueQueda(data: DashboardData, ahora: Long, ocultos: Boolean): String? {
    val hoy = epochMillisToAppDate(ahora)
    val disponible = runCatching { disponibleDelInicio(data.conElPeriodoDe(ahora), hoy) }.getOrNull() ?: return null
    val t = teQuedanDeHoy(disponible)
    val cifra = if (ocultos) PLATA_OCULTA else formatMoneyCompactSinOcultar(t.monto)
    if (t.rotulo == ROTULO_TE_PASASTE) return "te pasaste por $cifra"
    val dias = if (disponible.diasQueQuedan <= 1) "para hoy" else "para ${disponible.diasQueQuedan} días"
    return "te quedan $cifra $dias"
}

/**
 * Lo que espera en «Por revisar», dicho corto: «2 avisos por confirmar» si todo son avisos del banco;
 * si hay otras cosas, «3 por revisar». `null` sin nada.
 */
internal fun fraseDeLoPendiente(conteo: ConteoPorRevisar): String? = when {
    conteo.total <= 0 -> null
    conteo.total == conteo.avisos -> if (conteo.avisos == 1) "1 aviso por confirmar" else "${conteo.avisos} avisos por confirmar"
    else -> "${conteo.total} por revisar"
}

/**
 * **¿Suena el resumen de la mañana, y qué dice?** `null` si no:
 *
 * - sin cifras ([data] nulo: sin sesión, o nada que leer);
 * - con cifras que **no son de hoy** ([leidaEn] de otro día): quien llama pide las nuevas antes, y si
 *   no llegan, se calla en vez de decir lo de ayer como si fuera de hoy;
 * - si hoy ya sonó ([yaAvisado] es la huella de hoy);
 * - si no hay nada útil que decir: ni el gasto de ayer, ni la cifra del héroe, ni algo pendiente.
 *
 * El título es lo primero que hay para decir («Ayer gastaste $85.000») y el texto lo demás («Te quedan
 * $1,3M para 4 días · 2 avisos por confirmar»). Con «Ocultar los saldos» ([ocultos]) ninguna cifra sale
 * en claro.
 */
fun resumenDeLaManana(
    data: DashboardData?,
    leidaEn: Long?,
    gastoDeAyer: Long?,
    ahora: Long,
    yaAvisado: String?,
    ocultos: Boolean,
): ResumenDeLaManana? {
    if (data == null || !esDeHoy(leidaEn, ahora)) return null
    val huella = huellaDelResumenDeLaManana(ahora)
    if (yaAvisado == huella) return null
    val partes = listOfNotNull(
        gastoDeAyer?.let { fraseDelGastoDeAyer(it, ocultos) },
        fraseDeLoQueQueda(data, ahora, ocultos),
        fraseDeLoPendiente(conteoPorRevisarDelInicio(data)),
    )
    if (partes.isEmpty()) return null
    val titulo = partes.first().replaceFirstChar { it.uppercase() }
    val resto = partes.drop(1).joinToString(" · ").replaceFirstChar { it.uppercase() }
    return ResumenDeLaManana(
        huella = huella,
        texto = TextoDeAviso(titulo = titulo, texto = resto.ifEmpty { TOCA_PARA_VER_TU_INICIO }),
    )
}

/** El texto cuando el título ya lo dijo todo. */
const val TOCA_PARA_VER_TU_INICIO: String = "Toca para ver tu inicio"

/** La pantalla que abre [ABRIR_INICIO]. */
internal fun inicioDelAviso(valor: String): Screen? = if (valor == ABRIR_INICIO) Screen.Dashboard else null
