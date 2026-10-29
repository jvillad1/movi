package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.espacios.fraseDelSaldo
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/*
 * # El cierre semanal de la pareja (vuelta 68)
 *
 * Decisión del dueño (29-oct): el domingo a las 19:00, «Esta semana gastaron $480.000 juntos en Casa
 * con Caro · Caro te debe $76.050», apagable. **Sin reglas de plata nuevas**: el monto es la suma de los
 * gastos del grupo de la semana (lo que muestra la lista del grupo, el gasto entero y no tu parte) y el
 * saldo es la frase de la tarjeta del grupo ([fraseDelSaldo]).
 *
 * Lo decide esto, puro y probado (`ResumenSemanalTest`). Lo pide y lo pinta
 * `AvisoSemanalDeLaParejaWorker`, en `:androidApp`, una vez al día a las 19:00: solo hace algo el
 * domingo, o el lunes si el domingo el teléfono no tuvo red.
 */

/** La hora del resumen semanal, en Bogotá: el domingo en la noche. */
const val HORA_DEL_RESUMEN_SEMANAL: Int = 19

/** El prefijo de lo que abre tocar el resumen de un grupo: `"grupo:<id>"`. */
const val PREFIJO_ABRIR_GRUPO: String = "grupo:"

/** Lo que abre tocar el resumen cuando habla de varios grupos: la lista de grupos. */
const val ABRIR_GRUPOS: String = "grupos"

/** Lo que guarda el aviso para abrir el grupo [id]. */
fun abrirElGrupo(id: String): String = "$PREFIJO_ABRIR_GRUPO$id"

/** La pantalla que abre un `"grupo:<id>"` o `"grupos"`, o `null` si no es eso. */
internal fun grupoDelAviso(valor: String): Screen? = when {
    valor == ABRIR_GRUPOS -> Screen.Espacios
    valor.startsWith(PREFIJO_ABRIR_GRUPO) -> valor.removePrefix(PREFIJO_ABRIR_GRUPO).takeIf { it.isNotBlank() }?.let { Screen.Espacio(it) }
    else -> null
}

/**
 * La semana de la que habla el resumen de [hoy]: **de lunes a domingo**. El domingo, la que termina
 * hoy («Esta semana»); el lunes, la que terminó ayer («La semana pasada»: el domingo el teléfono no
 * tuvo red y WorkManager corrió tarde). Cualquier otro día, `null`: no es día de resumen.
 */
data class SemanaDelResumen(val lunes: LocalDate, val domingo: LocalDate, val esLaQueTermina: Boolean) {
    /** «Esta semana» o «La semana pasada». */
    val cuando: String get() = if (esLaQueTermina) "Esta semana" else "La semana pasada"
}

fun semanaDelResumen(hoy: LocalDate): SemanaDelResumen? = when (hoy.dayOfWeek) {
    DayOfWeek.SUNDAY -> SemanaDelResumen(hoy.minus(DatePeriod(days = 6)), hoy, esLaQueTermina = true)
    DayOfWeek.MONDAY -> SemanaDelResumen(hoy.minus(DatePeriod(days = 7)), hoy.minus(DatePeriod(days = 1)), esLaQueTermina = false)
    else -> null
}

/** La huella de una semana: su lunes. La misma semana no suena dos veces. */
fun huellaDeLaSemana(semana: SemanaDelResumen): String = "semana:${semana.lunes}"

/** La huella de la semana del resumen de [ahora], o `null` si hoy no es día de resumen. */
fun huellaDeLaSemanaDe(ahora: Long): String? = semanaDelResumen(epochMillisToAppDate(ahora))?.let(::huellaDeLaSemana)

/** Un grupo con gastos en la semana: cuánto gastaron entre todos y qué dice su saldo. */
data class GrupoDeLaSemana(val id: String, val nombre: String, val gastaron: Long, val saldo: String)

/** Lo que suena, a dónde lleva tocarlo y su huella. */
data class ResumenSemanal(val huella: String, val texto: TextoDeAviso, val abrir: String)

/**
 * Los grupos con al menos un gasto en [semana] (en Bogotá), del que más gastó al que menos: la suma de
 * sus gastos (el gasto entero, no tu parte) y su saldo («Caro te debe $76.050», «Le debes a Caro
 * $40.000», «Están a mano»).
 */
fun gruposDeLaSemana(detalles: List<DetalleDelEspacio>, semana: SemanaDelResumen, ocultos: Boolean): List<GrupoDeLaSemana> =
    detalles.mapNotNull { d ->
        val deLaSemana = d.gastos.filter { g ->
            g.fecha > 0L && epochMillisToAppDate(g.fecha, AppTimeZone.zone).let { it >= semana.lunes && it <= semana.domingo }
        }
        if (deLaSemana.isEmpty()) return@mapNotNull null
        GrupoDeLaSemana(
            id = d.espacio.id,
            nombre = d.espacio.nombre,
            gastaron = deLaSemana.sumOf { it.monto },
            saldo = fraseDelSaldoParaElAviso(d, ocultos),
        )
    }.sortedByDescending { it.gastaron }

/** El saldo del grupo como lo dice su tarjeta, con la cifra pegada: «Caro te debe $76.050». */
internal fun fraseDelSaldoParaElAviso(d: DetalleDelEspacio, ocultos: Boolean): String {
    val frase = fraseDelSaldo(d.espacio, d.saldo)
    if (frase.monto <= 0L) return frase.titulo
    return "${frase.titulo} ${plata(frase.monto, ocultos)}"
}

private fun plata(monto: Long, ocultos: Boolean): String = if (ocultos) PLATA_OCULTA else formatCOPSinOcultar(monto)

/**
 * **¿Suena el resumen semanal, y qué dice?** `null` si no es domingo (o el lunes de rezago), si esa
 * semana ya sonó ([yaAvisado]) o si **ningún grupo tuvo gastos esa semana** (sin grupos, también).
 *
 * - Un grupo: «Esta semana gastaron $480.000 juntos en Casa con Caro» y «Caro te debe $76.050» (o
 *   «Le debes a Caro $40.000», o «Están a mano»). «Juntos» dice que es el gasto del grupo, no tu
 *   parte. Tocarlo abre el grupo.
 * - Varios: «Esta semana gastaron $600.000 juntos en 2 grupos», el primero en el texto y uno por
 *   renglón al desplegar. Tocarlo abre la lista de grupos.
 */
fun resumenSemanal(
    detalles: List<DetalleDelEspacio>,
    ahora: Long,
    yaAvisado: String?,
    ocultos: Boolean,
): ResumenSemanal? {
    val semana = semanaDelResumen(epochMillisToAppDate(ahora)) ?: return null
    val huella = huellaDeLaSemana(semana)
    if (yaAvisado == huella) return null
    val grupos = gruposDeLaSemana(detalles, semana, ocultos)
    if (grupos.isEmpty()) return null
    val uno = grupos.singleOrNull()
    if (uno != null) {
        return ResumenSemanal(
            huella = huella,
            texto = TextoDeAviso(
                titulo = "${semana.cuando} gastaron ${plata(uno.gastaron, ocultos)} juntos en ${uno.nombre}",
                texto = uno.saldo,
            ),
            abrir = abrirElGrupo(uno.id),
        )
    }
    val total = grupos.sumOf { it.gastaron }
    val lineas = grupos.map { "${it.nombre} · ${plata(it.gastaron, ocultos)} · ${it.saldo}" }
    return ResumenSemanal(
        huella = huella,
        texto = TextoDeAviso(
            titulo = "${semana.cuando} gastaron ${plata(total, ocultos)} juntos en ${grupos.size} grupos",
            texto = lineas.first() + " y ${grupos.size - 1} más",
            lineas = lineas,
        ),
        abrir = ABRIR_GRUPOS,
    )
}
