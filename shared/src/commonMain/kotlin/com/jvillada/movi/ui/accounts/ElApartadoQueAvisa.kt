package com.jvillada.movi.ui.accounts

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.aporteMensualSugerido
import com.jvillada.movi.shared.model.apartadoFueraDeLosFijos
import com.jvillada.movi.shared.model.mas
import com.jvillada.movi.shared.model.mesesHastaElGasto
import com.jvillada.movi.shared.model.nombreDelMesDe
import com.jvillada.movi.shared.model.restanteDelApartado
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.TTL_DEL_INICIO_MS
import com.jvillada.movi.ui.dashboard.alcanzaParaElDisponible
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.dashboard.teQuedanDelInicio
import com.jvillada.movi.ui.profile.AHORRO_DE_CADA_PERIODO
import com.jvillada.movi.ui.transactions.leerDatosDelDiaADia
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

/*
 * # El apartado que avisa antes de guardar (vuelta 64)
 *
 * La revisión del 29-oct: crear «Colegio $2M» pasó el Inicio de «Te quedan $1.450.950» a «Te pasaste
 * por $549.050», en rojo, sin que la hoja dijera nada. Ahora la hoja dice, mientras se escribe el
 * monto, cómo queda «Te quedan» — con **la misma función que pinta el Inicio** ([teQuedanDelInicio])
 * y lo apartado contado con **la misma regla del server** ([apartadoFueraDeLosFijos]): no hay una
 * cuenta nueva, solo la de siempre con el apartado propuesto adentro.
 *
 * ## ¿Sale de tu ahorro del período? — no, todavía
 *
 * El encargo pedía preguntar, cuando «Te quedan» quedaría negativo y hay ahorro del período, si el
 * apartado sale de ese ahorro, y en ese caso contarlo dentro del ahorro sin restarlo dos veces. Hoy
 * los dos se restan **por separado**, cada uno una vez (`PlataDelDisponible.apartado` sale de `neto`;
 * `ahorroDelPeriodo` se resta después de los fijos — `AhorroDelPeriodoTest`), y nada en el modelo dice
 * que un apartado «sale de» un ahorro. Contarlo adentro pediría una regla de plata nueva (una marca en
 * el apartado y un `max`/una resta entre los dos en `disponibleDelPeriodo`, en el server y en el
 * cliente), y el encargo dice que en ese caso no se invente. Así que la hoja muestra la vista previa y
 * una advertencia que dice la verdad —el ahorro se resta aparte— y qué hacer si esa plata sale de ahí:
 * bajar el ahorro en Ajustes, que es una decisión del dueño con las herramientas que ya existen.
 */

/** Lo que dice la vista previa de la hoja de apartar: «Te quedan» hoy y con el apartado propuesto. */
data class TeQuedanConElApartado(
    /** «Te quedan» del Inicio, hoy: la cifra del héroe. */
    val antes: Long,
    /**
     * «Te quedan» con el apartado guardado, o `null` si con él el Inicio ya no puede afirmar un
     * disponible (no queda plata en el período: el héroe vuelve a mostrar «Tu plata»).
     */
    val despues: Long?,
    /** El ahorro del período que se resta aparte (cero sin elegir uno). */
    val ahorroDelPeriodo: Long,
)

/**
 * El apartado [propuesto] con el `restante` que el server le calcularía: uno nuevo, entero; uno que se
 * edita ([actual]) con la misma categoría, el monto nuevo menos lo que ya consumió. Con otra categoría
 * no se sabe cuánto consumió desde su arranque, así que cuenta entero (el server lo corrige al guardar).
 */
internal fun conSuRestante(propuesto: Apartado, actual: Apartado?): Apartado {
    val consumido = if (actual != null && actual.categoria == propuesto.categoria) actual.monto - actual.restante else 0L
    return propuesto.copy(restante = restanteDelApartado(propuesto.monto, consumido.coerceAtLeast(0L)))
}

/**
 * **«Te quedan» antes y después de guardar [propuesto]**, o `null` si el Inicio no tiene con qué
 * afirmarlo (falta una lectura, un server viejo sin la plata del disponible, o no hay disponible que
 * decir hoy).
 *
 * El «después» es [teQuedanDelInicio] —la función del héroe— sobre los mismos datos, con lo apartado de
 * [PlataDelDisponible] movido en lo que cambia [apartadoFueraDeLosFijos] (la regla que usa el server
 * para mandar esa cifra) al sumar el propuesto, o al reemplazar el que se edita. Así «antes» es
 * exactamente lo que dice el Inicio ahora y «después» lo que va a decir cuando el server recalcule.
 */
fun teQuedanConElApartado(data: DashboardData, propuesto: Apartado, hoy: LocalDate): TeQuedanConElApartado? {
    val plata = data.plataDelDisponible ?: return null
    val cuentas = data.accounts ?: return null
    val apartados = data.apartados ?: return null
    val antes = teQuedanDelInicio(data, hoy) ?: return null
    val checklist = checklistDelPeriodoDe(data)
    val actual = apartados.firstOrNull { it.id == propuesto.id }
    val sin = apartadoFueraDeLosFijos(cuentas, apartados, checklist)
    val conElPropuesto = apartados.filterNot { it.id == propuesto.id } + conSuRestante(propuesto, actual)
    val con = apartadoFueraDeLosFijos(cuentas, conElPropuesto, checklist)
    val conElApartado = data.copy(
        apartados = conElPropuesto,
        plataDelDisponible = plata.copy(apartado = plata.apartado + (con - sin)),
    )
    return TeQuedanConElApartado(
        antes = antes.teQuedan,
        despues = teQuedanDelInicio(conElApartado, hoy)?.teQuedan,
        ahorroDelPeriodo = plata.ahorroDelPeriodo.coerceAtLeast(0L),
    )
}

/** Pega dos caracteres sin dejar que el renglón se parta entre ellos (WORD JOINER, U+2060). */
const val UNION_QUE_NO_SE_PARTE: String = "\u2060"

/** El espacio que no se parte (U+00A0): «a −$549.050» va junto. */
const val ESPACIO_QUE_NO_SE_PARTE: String = "\u00A0"

/**
 * Una cifra que no se parte al final del renglón (revisión de la vuelta 64, rev64-06): a 360 dp el
 * «−» de «−$549.050» quedaba solo al final de la línea y la cifra bajaba sin su signo. El menos va
 * pegado al «$» con [UNION_QUE_NO_SE_PARTE], que no se ve.
 */
fun cifraQueNoSeParte(monto: Long): String =
    formatCOP(monto).replace("\u2212$", "\u2212$UNION_QUE_NO_SE_PARTE$")

/**
 * El renglón principal de la vista previa: «Te quedan pasa de $1.450.950 a −$549.050», con la cifra
 * nueva pegada a su «a» y su signo pegado al «$» (si el renglón se parte, baja «a −$549.050» entero).
 */
fun fraseDeLaVistaPrevia(v: TeQuedanConElApartado): String = when {
    v.despues == null -> "Con este apartado no te queda plata para gastar en el período"
    v.despues == v.antes -> "Te quedan sigue en${ESPACIO_QUE_NO_SE_PARTE}${cifraQueNoSeParte(v.antes)}"
    else -> "Te quedan pasa de ${cifraQueNoSeParte(v.antes)} a$ESPACIO_QUE_NO_SE_PARTE${cifraQueNoSeParte(v.despues)}"
}

/**
 * La advertencia debajo, o `null` si no hace falta: solo cuando con el apartado «Te quedan» quedaría
 * negativo (o sin disponible). Con ahorro del período, dice que el ahorro se resta aparte y cómo no
 * contar dos veces la misma plata si sale de ahí (ver la nota de arriba: no hay regla que los cruce).
 */
fun advertenciaDeLaVistaPrevia(v: TeQuedanConElApartado): String? {
    val queda = v.despues
    if (queda != null && (queda >= 0L || queda >= v.antes)) return null
    if (v.ahorroDelPeriodo <= 0L) return "Te pasarías del disponible del período."
    return "Tu ahorro del período (${formatCOP(v.ahorroDelPeriodo)}) se resta aparte. Si esta plata sale " +
        "de ese ahorro, bájalo en Tú → $AHORRO_DE_CADA_PERIODO para no contarla dos veces."
}

/**
 * Lo que la vista previa necesita, o `null` si no se pudo leer (la hoja se guarda igual, sin aviso):
 * lo del Inicio si está fresco —con sus cuentas y sus apartados, leídos juntos con su «Te quedan»—, y
 * si no, las lecturas del disponible ([leerDatosDelDiaADia], las mismas de Plan y Movimientos) más las
 * cuentas y los apartados. Guardar un apartado invalida el Inicio, así que después de editar uno se
 * lee de nuevo.
 */
internal suspend fun datosParaLaVistaPrevia(ahora: Long): DashboardData? {
    val delInicio = DashboardDataCache.data
    if (delInicio != null && ahora - DashboardDataCache.cargadoEn in 0..TTL_DEL_INICIO_MS &&
        alcanzaParaElDisponible(delInicio) && delInicio.accounts != null && delInicio.apartados != null &&
        delInicio.plataDelDisponible != null
    ) {
        return delInicio
    }
    return runCatching {
        coroutineScope {
            val perfil = async { Repositories.wallets.getUserProfile() }
            val cuentas = async { Repositories.wallets.getAccounts() }
            val apartados = async { Repositories.wallets.getApartados() }
            leerDatosDelDiaADia(perfil.await(), ahora).copy(accounts = cuentas.await(), apartados = apartados.await())
        }
    }.getOrNull()
}

// ── El gasto de un mes puntual ───────────────────────────────────────────────

/** Cuántos meses ofrece la hoja para el gasto: este y los once que siguen. */
const val MESES_PARA_EL_GASTO: Int = 12

/** Los meses que se pueden elegir para el gasto, desde el de [hoy]. */
fun mesesParaElGasto(hoy: LocalDate): List<PeriodoFinanciero> {
    val este = PeriodoFinanciero(hoy.year, hoy.monthNumber)
    return (0 until MESES_PARA_EL_GASTO).map { este.mas(it) }
}

/** «marzo», o «marzo de 2027» si no es del año de [hoy]. */
fun nombreDelMesDelGasto(mes: PeriodoFinanciero, hoy: LocalDate): String =
    if (mes.year == hoy.year) nombreDelMesDe(mes) else "${nombreDelMesDe(mes)} de ${mes.year}"

/** La pastilla del mes: «mar», o «mar 2027» si no es del año de [hoy]. */
fun pastillaDelMes(mes: PeriodoFinanciero, hoy: LocalDate): String {
    val corto = nombreDelMesDe(mes).take(3)
    return if (mes.year == hoy.year) corto else "$corto ${mes.year}"
}

/**
 * **El renglón del apartado para un gasto**, en el detalle de la cuenta: «Para marzo: llevas $400.000
 * de $1.200.000 · aparta $160.000 al mes». Lo que lleva es lo que le queda ([Apartado.restante]). Sin
 * nada más que apartar, «ya lo tienes»; con el mes ya pasado, sin sugerencia. `null` si el apartado no
 * es para un gasto.
 */
fun lineaDelGasto(apartado: Apartado, mes: PeriodoFinanciero?, hoy: LocalDate): String? {
    val objetivo = apartado.objetivo ?: return null
    mes ?: return null
    val base = "Para ${nombreDelMesDelGasto(mes, hoy)}: llevas ${formatCOP(apartado.restante)} de ${formatCOP(objetivo)}"
    val aporte = aporteMensualSugerido(objetivo, apartado.restante, mesesHastaElGasto(mes, hoy))
    return when {
        aporte == null -> base
        aporte == 0L -> "$base · ya lo tienes"
        else -> "$base · aparta ${formatCOP(aporte)} al mes"
    }
}

/**
 * Lo que dice la hoja mientras se arma el gasto: «Aparta $160.000 al mes» y, debajo, de dónde sale.
 * `null` si falta el costo o el mes.
 */
fun sugerenciaDelGasto(objetivo: Long?, apartado: Long, mes: PeriodoFinanciero?, hoy: LocalDate): Pair<String, String>? {
    if (objetivo == null || objetivo <= 0L || mes == null) return null
    val meses = mesesHastaElGasto(mes, hoy)
    val aporte = aporteMensualSugerido(objetivo, apartado, meses) ?: return null
    val cuando = nombreDelMesDelGasto(mes, hoy)
    if (aporte == 0L) return "Ya tienes lo del gasto de $cuando" to "Lo apartado cubre los ${formatCOP(objetivo)}."
    val falta = objetivo - apartado.coerceAtLeast(0L)
    val enCuantos = when {
        meses <= 0 -> "este mes"
        meses == 1 -> "en 1 mes"
        else -> "en $meses meses"
    }
    return "Aparta ${formatCOP(aporte)} al mes" to
        "Para llegar a ${formatCOP(objetivo)} en $cuando te faltan ${formatCOP(falta)} $enCuantos. Es una sugerencia: Movi no aparta nada solo."
}
