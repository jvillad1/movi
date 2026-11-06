package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Vuelta 133 · los créditos cuya cuota cambia
 *
 * El papá del dueño probó Movi: «en créditos la cuota siempre es fija; no se puede poner una cuota
 * variable». Y el dueño: «las cuotas pueden ser con tasa fija o variable». Hasta acá [CreditTerms] tenía
 * una sola `installment`, y todo —«Te quedan», Pagos, la caja, «Falta por pagar», el plan— la daba por
 * igual todos los meses.
 *
 * Lo que dice la investigación (`docs/investigacion/research_notes/Créditos de tasa variable en
 * Colombia/`), en corto:
 *
 * - **Vivienda (Ley 546/1999)**: la tasa es fija por ley. La cuota en pesos cambia por el sistema de
 *   amortización (abono constante a capital: baja cada mes) o porque el crédito está en UVR (sube con la
 *   inflación), además de los seguros.
 * - **Consumo**: puede ser **IBR + puntos**, con una tasa que se reajusta; al reajustarse cambia la
 *   cuota o cambia el plazo (Bancolombia lo dice así).
 * - **La verdad de cada mes sale del extracto o del pago real.** El modelo solo proyecta.
 *
 * Por eso esto es chico a propósito: **el tipo de cuota** ([TipoDeCuota]) y, para la tasa variable, el
 * índice, los puntos y qué cambia al reajustarse. La tasa vigente sigue en [CreditTerms.rateEa] (la
 * actualiza el dueño o el extracto). Y **la cuota de este mes** sale de UNA función, [cuotaDeEsteMes]:
 * fija, la de siempre; cambiante, la última que se pagó de verdad. Todos los lados la usan.
 *
 * **Fuera, y dicho**: pronosticar el IBR o la UVR con datos en vivo, períodos de gracia, cuotas extra
 * pactadas, cuota balón, y los seguros recalculados por saldo (siguen como `insuranceMonthly` fijo; la
 * cuota real del pago ya los incluye).
 */

/**
 * **Cómo se mueve la cuota de un crédito.** `null` en [CreditTerms.tipoDeCuota] = [FIJA] (los créditos
 * de antes, y lo que manda un APK viejo).
 */
@Serializable
enum class TipoDeCuota {
    /** La de siempre: la misma cuota todos los meses. */
    FIJA,

    /** La tasa es un índice más unos puntos (IBR + puntos) y se reajusta: cambia la cuota o el plazo. */
    TASA_VARIABLE,

    /** El crédito está en UVR: la tasa es fija sobre la UVR y la cuota en pesos sube con la inflación. */
    UVR,

    /** Abono constante a capital: cada mes se abona lo mismo a capital y el interés baja, así que la cuota baja. */
    ABONO_CONSTANTE,
}

/**
 * El índice de una tasa variable. **La DTF no se ofrece**: deja de publicarse el 1-ene-2027 y los
 * créditos atados a ella pasan a IBR (Bancolombia, 9-sep-2026).
 */
@Serializable
enum class IndiceDeLaTasa { IBR, OTRO }

/** Qué cambia cuando se reajusta una tasa variable: la cuota, o el plazo (la cuota se queda). */
@Serializable
enum class AlCambiarLaTasa { CAMBIA_LA_CUOTA, CAMBIA_EL_PLAZO }

/** ¿Este crédito tiene una cuota que cambia? `null` o [TipoDeCuota.FIJA], no. */
fun CreditTerms.cuotaCambia(): Boolean = tipoDeCuota != null && tipoDeCuota != TipoDeCuota.FIJA

/**
 * Revisión de la vuelta 133 · ¿se puede comparar un pago contra la cuota pactada («pagaste $X más que la
 * cuota»)? Solo con una cuota fija: en una que cambia, la pactada ya no es la cuota del mes.
 */
fun esCuotaQueSeComparaConLaPactada(terms: CreditTerms): Boolean = !terms.cuotaCambia()

/**
 * **Lo último que se pagó de verdad por la cuota de un crédito**: la pata del dinero del último pago de
 * cuota (lo que salió de la cuenta, seguros incluidos), y la cuota que saldó (`"2026-10"`).
 */
@Serializable
data class CuotaPagada(val monto: Long, val periodo: String)

/**
 * Lo que viaja con la cuota de un crédito de cuota que cambia, en la regla sintética de Pagos
 * ([RecurringRule.cuotaQueCambia]) y en su fila ([PagoDelPeriodo.cuotaQueCambia]): que su monto es
 * estimado («unos $X») y, si la hay, la última que se pagó («la de octubre fue $Y»).
 */
@Serializable
data class LaCuotaQueCambia(val anterior: CuotaPagada? = null)

/**
 * **La cuota de este mes de un crédito**: lo que restan «Te quedan», Pagos, la caja y «Falta por pagar»,
 * y lo que propone Agregar → Pagar. Una sola función, y todos los lados la llaman.
 *
 * - [TipoDeCuota.FIJA] (o sin tipo): [CreditTerms.installment], como siempre.
 * - Los demás: **la última cuota pagada de verdad** ([ultimaPagada]) si la hay; si no, `installment`.
 *   La cuota que cambia no se adivina: la del mes pasado es la mejor estimación de la de este, y se dice
 *   «unos $X» ([textoDeLaCuotaQueCambia]). En un abono constante eso estima un poco de más (la cuota
 *   baja), que es el lado barato de equivocarse para «Te quedan».
 *
 * **El pago real manda**: pagar otro monto sigue saldando la cuota del período (la regla de siempre va
 * por fecha, no por monto), y desde entonces esa es la cifra.
 */
fun cuotaDeEsteMes(terms: CreditTerms, ultimaPagada: CuotaPagada?): Long =
    if (!terms.cuotaCambia()) terms.installment
    else ultimaPagada?.monto?.takeIf { it > 0L } ?: terms.installment

/** «$1.234.567», sin depender de la pantalla (el server también arma estas frases). */
private fun pesos(valor: Long): String = (if (valor < 0) "−" else "") + "$" + conPuntosDeMiles(kotlin.math.abs(valor))

/** El mes de un `"2026-10"` («octubre»), o `null` si no se entiende. */
fun mesDeLaCuotaPagada(periodo: String): String? {
    val partes = periodo.split("-")
    val anio = partes.getOrNull(0)?.toIntOrNull() ?: return null
    val mes = partes.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return nombreDelMesDe(PeriodoFinanciero(anio, mes))
}

/**
 * **«unos $1.250.000 (la de octubre fue $1.262.300)»**: el monto de una cuota que cambia, dicho como lo
 * que es, una estimación. Sin una anterior, «unos $1.250.000». La usan la fila de Pagos y la ficha del
 * crédito; el server, Movi AI. Pura.
 */
fun textoDeLaCuotaQueCambia(monto: Long, anterior: CuotaPagada?): String {
    val base = conUnosSiEsEstimada(pesos(monto), estimada = true)
    val delMes = anterior?.takeIf { it.monto > 0L }?.let { a -> mesDeLaCuotaPagada(a.periodo)?.let { it to a.monto } }
    return when {
        delMes == null -> base
        // Revisión de la vuelta 133: la estimación ES la anterior; repetir la cifra no decía nada.
        delMes.second == monto -> "$base, como la de ${delMes.first}"
        else -> "$base (la de ${delMes.first} fue ${pesos(delMes.second)})"
    }
}

/**
 * Vuelta 134 (M-2) · **lo que va delante de una cifra estimada**: «unos $1.262.300». Una sola palabra para
 * la fila de Pagos, «Próximo pago», el widget «Falta por pagar», «Movi propone hoy», la campana, el aviso
 * del teléfono y la página compartida.
 */
const val UNOS: String = "unos"

/**
 * Vuelta 134 (M-2) · **¿la cifra de [pago] es una estimación?** Solo la cuota de un crédito que cambia
 * ([PagoDelPeriodo.cuotaQueCambia]: su monto es [cuotaDeEsteMes], la última pagada) mientras está
 * pendiente: pagada, lo que se muestra es lo que salió de verdad. Es **la** regla: ningún lado decide
 * por su cuenta si dice «unos». Pura.
 */
fun laCifraDelPagoEsEstimada(pago: PagoDelPeriodo): Boolean =
    pago.cuotaQueCambia != null && !pago.hecho && !pago.montoEsSaldo

/** «unos $1.262.300» si [estimada]; si no, [cifra] tal cual. Para los textos planos (avisos, página). */
fun conUnosSiEsEstimada(cifra: String, estimada: Boolean): String = if (estimada) "$UNOS $cifra" else cifra

/**
 * **«tu cuota es $X» o «tu cuota es de unos $X»**: el monto de la cuota de [terms] como lo dicen los textos
 * de Créditos y Movi AI. Fija, la cifra tal cual; cambiante, con «unos». Pura.
 */
fun cuotaEnPalabras(terms: CreditTerms, ultimaPagada: CuotaPagada?): String {
    val monto = cuotaDeEsteMes(terms, ultimaPagada)
    return conUnosSiEsEstimada(pesos(monto), terms.cuotaCambia())
}

/** «La tasa es variable (IBR + puntos)», «Está en UVR», «Baja cada mes (abono constante a capital)». */
fun porQueCambiaLaCuota(tipo: TipoDeCuota): String? = when (tipo) {
    TipoDeCuota.FIJA -> null
    TipoDeCuota.TASA_VARIABLE -> "La tasa es variable (IBR + puntos)"
    TipoDeCuota.UVR -> "Está en UVR"
    TipoDeCuota.ABONO_CONSTANTE -> "Baja cada mes (abono constante a capital)"
}

/** El rótulo corto de cada respuesta a «Cuando cambia la tasa, ¿cambia la cuota o el plazo?». */
fun rotuloDeAlCambiarLaTasa(que: AlCambiarLaTasa): String = when (que) {
    AlCambiarLaTasa.CAMBIA_LA_CUOTA -> "Cambia la cuota"
    AlCambiarLaTasa.CAMBIA_EL_PLAZO -> "Cambia el plazo"
}

/** El tope de los puntos sobre el índice que se aceptan (IBR + 30 ya pasa la usura). */
const val MAXIMO_DE_PUNTOS_SOBRE_EL_INDICE: Double = 30.0

/** El rechazo de unos puntos fuera de 0..[MAXIMO_DE_PUNTOS_SOBRE_EL_INDICE]. */
const val PUNTOS_FUERA_DE_RANGO: String = "Los puntos sobre el IBR van de 0 a 30."

/**
 * ¿Las condiciones de la cuota que cambia son coherentes? El motivo, o `null`. La hoja y el server
 * (`POST`/`PUT /api/credits`) llaman a esta misma función: crear y editar validan igual.
 */
fun validarLaCuotaQueCambia(terms: CreditTerms): String? {
    val puntos = terms.puntosSobreElIndice ?: return null
    return if (!puntos.isFinite() || puntos < 0.0 || puntos > MAXIMO_DE_PUNTOS_SOBRE_EL_INDICE) PUNTOS_FUERA_DE_RANGO else null
}

/**
 * **Las condiciones como se guardan**: los datos de la tasa variable (índice, puntos, qué cambia) solo
 * valen con [TipoDeCuota.TASA_VARIABLE]; con otro tipo se borran, para que un «Está en UVR» no arrastre
 * los puntos de antes. [TipoDeCuota.FIJA] se guarda como `null` (lo de siempre). Pura.
 */
fun CreditTerms.conLaCuotaQueCambiaLimpia(): CreditTerms {
    val tipo = tipoDeCuota?.takeIf { it != TipoDeCuota.FIJA }
    return if (tipo == TipoDeCuota.TASA_VARIABLE) copy(tipoDeCuota = tipo)
    else copy(tipoDeCuota = tipo, indiceDeLaTasa = null, puntosSobreElIndice = null, alCambiarLaTasa = null)
}

/**
 * **La inflación que se supone para proyectar un crédito en UVR**: la meta del Banco de la República,
 * 3 % al año. Es un supuesto, no un pronóstico, y la pantalla lo dice en «¿Qué supone?»: con la
 * inflación de hoy por encima de la meta, la cuota real probablemente suba más rápido al principio.
 */
const val INFLACION_SUPUESTA_PARA_LA_UVR: Double = 3.0

/** Lo que el plan de un crédito de cuota que cambia supone, dicho en una frase; `null` en uno fijo. */
fun loQueSuponeElPlan(tipo: TipoDeCuota?, rateEa: Double): String? = when (tipo) {
    null, TipoDeCuota.FIJA -> null
    TipoDeCuota.TASA_VARIABLE ->
        "Tu tasa es variable: Movi proyecta con la tasa de hoy (${formatearTasa(rateEa)} % EA), sin adivinar el IBR. Cuando cambie, actualízala o sube el extracto."
    TipoDeCuota.UVR ->
        "Está en UVR: Movi supone una inflación de ${formatearTasa(INFLACION_SUPUESTA_PARA_LA_UVR)} % al año (la meta del Banco de la República) y toma tu tasa como la tasa sobre la UVR."
    TipoDeCuota.ABONO_CONSTANTE ->
        "Abono constante a capital: cada mes abonas lo mismo a capital (lo prestado entre el plazo) y el interés baja con el saldo."
}

private fun formatearTasa(t: Double): String {
    val redondeada = kotlin.math.round(t * 100.0) / 100.0
    val texto = if (redondeada == kotlin.math.floor(redondeada)) redondeada.toLong().toString() else redondeada.toString()
    return texto.replace('.', ',')
}
