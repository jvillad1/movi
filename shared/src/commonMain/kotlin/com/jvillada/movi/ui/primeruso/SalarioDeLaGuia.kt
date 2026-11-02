package com.jvillada.movi.ui.primeruso

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import kotlinx.datetime.LocalDate

/*
 * # Vuelta 75 · el salario como ancla, en la guía
 *
 * Una usuaria nueva eligió «25 · Es el día que me pagan» y la guía nunca le preguntó cuánto: la caja
 * supuso que no le entraba nada y todo salió en rojo. Ahora el paso del período pregunta «¿Cuánto te
 * pagan? (opcional)», y al terminar la guía se crea la regla de ingreso recurrente **con la creación de
 * siempre** (`createRecurringRule`): sin escrituras propias, como el resto de la guía.
 *
 * **Por qué el orden de los pasos no cambió**: el período va antes que las cuentas, y el salario necesita
 * una cuenta a la que llegar. En vez de mover los pasos (la lista única de `primerosPasos` la comparten la
 * fila del Inicio y Tú → «Primeros pasos»), el monto se pregunta donde se elige el día, y la regla se crea
 * al terminar, cuando las cuentas ya existen: llega a la que se eligió o, si no se eligió ninguna (o no
 * había), a la primera cuenta de dinero.
 */

/** El título de la pregunta del salario en el paso del período. */
const val PREGUNTA_DEL_SALARIO: String = "¿Cuánto te pagan? (opcional)"

/** El nombre y la categoría de la regla del salario: la categoría de ingreso «Salario» de siempre. */
const val NOMBRE_DEL_SALARIO: String = "Salario"

/**
 * **La regla del salario**: un ingreso recurrente el **día del corte** —el día que el dueño dijo que le
 * pagan—, que es justo como `fechaEsperadaDelIngreso` reconoce al salario («un ingreso que cae el día del
 * corte es el que abre el período») y lo espera el día que arranca el período, corrido si cae en fin de
 * semana o festivo. Pura.
 */
fun reglaDelSalario(monto: Long, diaDelCorte: Int, cuenta: String?, arranque: String? = null): RecurringRule = RecurringRule(
    id = "",
    name = NOMBRE_DEL_SALARIO,
    category = NOMBRE_DEL_SALARIO,
    amount = monto,
    dayOfMonth = diaDelCorte,
    type = TransactionType.INCOME,
    accountId = cuenta,
    activeFrom = arranque,
)

/**
 * **Desde cuándo corre el salario cuando ya te pagaron este período** (revisión de la 75): el arranque
 * del período siguiente, ISO. Con `activeFrom` en esa fecha (y `arranqueEsDesembolso` en `false`, el de
 * siempre para una regla escrita a mano), el server toma como piso **el primer día del período de esa
 * fecha** (`arranqueDeLaRegla`): la ocurrencia de este período no existe —ni vencida, ni por cobrar, ni en
 * «Te quedan», ni en la caja— y la del siguiente sí. No inventa historia: solo dice que la regla empieza
 * a esperar el salario desde el próximo período, porque el de este ya está en el saldo que escribiste.
 *
 * Los ajustes son los que el dueño acaba de elegir (el día y la regla del fin de semana). Pura.
 */
fun arranqueDelPeriodoSiguiente(
    diaDelCorte: Int,
    regla: SiElCorteCaeEnFinDeSemana,
    ahoraMillis: Long,
    /** Vuelta 105 · el segundo día de la quincena; `null` = cada mes, lo de siempre. */
    segundoDia: Int? = null,
): String {
    val ajustes = ajustesDeLaGuia(diaDelCorte, regla, segundoDia)
    return inicioDelPeriodo(periodoSiguiente(periodoDe(ahoraMillis, ajustes), ajustes), ajustes).toString()
}

/** La pregunta de debajo del monto del salario. */
const val YA_TE_PAGARON_ESTE_PERIODO: String = "¿Ya te pagaron este período?"

/** El tag de esa fila. */
const val TAG_YA_TE_PAGARON: String = "primer-uso-ya-te-pagaron"

/** Lo que dice el final de la guía sin cuentas (o sin poder leer la cifra). */
const val YA_PUEDES_EMPEZAR: String = "Ya puedes empezar"

/**
 * **La frase del final** («Listo»): «Te quedan $1.234.000 hasta el 24 nov», la cifra del héroe de Inicio
 * (`teQuedanDeHoy` sobre `disponibleDelInicio`) hasta el último día del período; «Te pasaste por $X en el
 * período, que va hasta el 24 nov» si es negativa; «Ya puedes empezar» sin cuentas o sin cifra. Pura.
 */
fun fraseDelFinalDeLaGuia(teQuedan: Long?, ultimoDia: LocalDate?, hayCuentas: Boolean): String {
    if (!hayCuentas || teQuedan == null || ultimoDia == null) return YA_PUEDES_EMPEZAR
    val hasta = "${ultimoDia.dayOfMonth} ${MESES_DEL_ANIO[ultimoDia.monthNumber - 1].take(3)}"
    return if (teQuedan >= 0L) "Te quedan ${formatCOPSinOcultar(teQuedan)} hasta el $hasta"
    else "Te pasaste por ${formatCOPSinOcultar(-teQuedan)} en el período, que va hasta el $hasta"
}

/** «Paso 2 de 6»: el rótulo chico de arriba (vuelta 75; era el título de la pantalla). Pura. */
fun rotuloDelPaso(numero: Int, de: Int): String = "Paso $numero de $de"

/** El tag del rótulo «Paso N de M». */
const val TAG_ROTULO_DEL_PASO: String = "primer-uso-rotulo-del-paso"

/** El tag del título grande del paso. */
const val TAG_TITULO_DEL_PASO: String = "primer-uso-titulo-del-paso"

/** El tag del monto del salario en el paso del período. */
const val TAG_SALARIO_EN_LA_GUIA: String = "primer-uso-salario"

/** El tag de la cifra del final. */
const val TAG_CIFRA_DEL_LISTO: String = "primer-uso-cifra-del-listo"

/**
 * Vuelta 105 · **los ajustes del período que se están eligiendo** en la guía o en Ajustes: cada mes con
 * su día, o cada quincena con sus dos días ([segundoDia] no nulo). La misma regla del fin de semana para
 * todos los arranques. Pura.
 */
fun ajustesDeLaGuia(dia: Int, regla: SiElCorteCaeEnFinDeSemana, segundoDia: Int? = null): PeriodSettings =
    if (segundoDia == null) PeriodSettings(cutoffDay = dia, siCaeEnFinDeSemana = regla)
    else PeriodSettings(
        cutoffDay = dia,
        siCaeEnFinDeSemana = regla,
        frecuencia = com.jvillada.movi.shared.model.FrecuenciaDelPeriodo.QUINCENAL,
        segundoDia = segundoDia,
    )

/**
 * Vuelta 105 · **el salario por quincenas son dos reglas** (D9 del diseño): la del día A y la del B, cada
 * una con el monto de una quincena. Con «ya te pagaron» cada regla arranca en la próxima quincena SUYA
 * (la siguiente que abre su día), no en la siguiente a secas: el piso de una obligación es el mes de
 * referencia que contiene `activeFrom` (`arranqueDeLaRegla` en el server), y con la siguiente a secas la
 * regla del día A volvería a esperar el pago que ya llegó. Sin «ya te pagaron», `null`: corren desde
 * siempre. Pura; el orden es (día A, día B).
 */
fun reglasDelSalarioQuincenal(
    monto: Long,
    primerDia: Int,
    segundoDia: Int,
    regla: SiElCorteCaeEnFinDeSemana,
    cuenta: String?,
    yaTePagaron: Boolean,
    ahoraMillis: Long,
): List<RecurringRule> {
    val ajustes = ajustesDeLaGuia(primerDia, regla, segundoDia)
    val (arranqueA, arranqueB) = if (!yaTePagaron) null to null else {
        val siguiente = periodoSiguiente(periodoDe(ahoraMillis, ajustes), ajustes)
        val laOtra = periodoSiguiente(siguiente, ajustes)
        val (delA, delB) = if (com.jvillada.movi.shared.model.esLaQuincenaDelDiaA(siguiente, ajustes)) siguiente to laOtra else laOtra to siguiente
        inicioDelPeriodo(delA, ajustes).toString() to inicioDelPeriodo(delB, ajustes).toString()
    }
    return listOf(
        reglaDelSalario(monto, primerDia, cuenta, arranqueA),
        reglaDelSalario(monto, segundoDia, cuenta, arranqueB),
    )
}
