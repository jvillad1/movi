package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.ComoVaLaDeuda
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.ResumenDeDeudas
import com.jvillada.movi.shared.model.deudaEnOtraMoneda
import com.jvillada.movi.shared.model.mas
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.ui.components.formatCOP
import kotlin.math.round

/**
 * F20 — deuda total en COP: préstamos + tarjetas, **una sola función** para que la pantalla de
 * Créditos y el acceso «Créditos» del Inicio sumen exactamente igual (en la Ola 4 se encontró
 * que daban números distintos; una sola fuente hace imposible que diverjan de nuevo).
 *
 * Por cuenta se usa `estimatedTotalCop ?: balance` — el mismo criterio que `assetsDebtsNet`
 * (Cuentas): una tarjeta en USD aporta su deuda convertida a TRM, no solo su componente COP
 * (que sería $0 y mentiría el total).
 */
fun totalDebtCop(credits: List<CreditSummary>, cards: List<CardSummary>): Long =
    credits.sumOf { debtCopOf(it.account) } + cards.sumOf { debtCopOf(it.account) }

private fun debtCopOf(account: Account): Long = account.estimatedTotalCop ?: account.balance

/**
 * Lo que dice la esquina derecha de la tarjeta de un préstamo, y cuánto llena su barra.
 *
 * @property etiqueta el texto que se lee: «18% pagado», o el aviso de que falta el desembolso.
 * @property fraccion cuánto de la barra se pinta, de 0 a 1.
 * @property esAviso si [etiqueta] es una frase y no una cifra. Lo decide esta función y no la
 *   pantalla: dibujar una frase con la fuente monoespaciada —que está para que los porcentajes se
 *   alineen entre tarjetas— solo la ensancha. Deducirlo allá de `hasMovements` daba mal el caso
 *   de un crédito sin términos, que no tiene movimientos y sin embargo muestra «0% pagado».
 * @property mostrarBarra si se dibuja la barra de progreso.
 *
 *   **Una barra que se satura en cero cuando la deuda creció comunica lo contrario de lo que
 *   pasa.** `paidPct` es `1 − deuda/principal` clampado a `[0, 1]`, así que una deuda por encima
 *   del capital original sale como 0 — y una barra vacía se lee «todavía no empezaste» cuando lo
 *   cierto es «vas para atrás». En el Hipotecario ·2334 del dueño (capital $200.000.000, deuda
 *   $204.183.376) y en la Libranza ·4818 ($257.000.000 contra $262.386.162) la barra decía 0 %
 *   sobre $4,18 y $5,39 millones de deuda **de más**.
 *
 *   La salida no es pintar la barra hacia el otro lado —una barra no puede ir hacia atrás sin
 *   inventarse una escala— sino **no pintarla**: donde no hay progreso que mostrar, la tarjeta
 *   dice en pesos cuánto se pasó y no dibuja nada. Ver [progresoDeCredito].
 */
data class ProgresoDeCredito(
    val etiqueta: String,
    val fraccion: Float,
    val esAviso: Boolean,
    val mostrarBarra: Boolean = true,
)

/**
 * **Un crédito sin un solo movimiento no está pagado: está sin registrar.**
 *
 * `paidPct` es `1 - deuda/principal`, así que un crédito con capital original de $257.000.000 y
 * deuda derivada $0 da 1.0 — y la tarjeta anunciaba **«100% pagado»**, con la barra llena, sobre
 * un crédito que el dueño acababa de crear y al que todavía no le había registrado el desembolso.
 *
 * Eso es peor que el error que esta ola vino a evitar, no mejor. La deuda contada dos veces es
 * ruidosa —la hoja de Traspaso muestra la aritmética antes de guardar, y $514.000.000 saltan a la
 * vista—; esta era **callada y optimista**: la deuda total subestimada en el monto entero del
 * crédito y el patrimonio sobreestimado por lo mismo, sin que nada en la pantalla pidiera
 * completar nada. Y el flujo tiene dos pasos (crear el crédito en $0, después registrar el
 * desembolso), así que el estado intermedio es normal, va a existir, y hay que decirlo.
 *
 * **El patrimonio neto no delata ninguno de los dos errores**, y por eso las dos defensas tienen
 * que estar en la pantalla donde se comete cada uno: si la deuda se cuenta dos veces, deuda y
 * efectivo se inflan a la par y el neto queda igual; si el desembolso falta, faltan los dos y el
 * neto también queda igual. Lo único que se mueve son las cifras por cuenta.
 *
 * La distinción no puede salir de la deuda sola —$0 es «pagado» y «sin registrar» a la vez— y por
 * eso sale de [CreditSummary.hasMovements]: un crédito de verdad pagado llegó a $0 **con
 * eventos** (su apertura, sus cuotas, sus abonos) y sigue diciendo «100% pagado», que ahí sí es
 * cierto.
 *
 * ## Las otras dos formas de que «100% pagado» sea mentira
 *
 * La primera versión de esta función tapaba un solo caso —cero movimientos— y la re-revisión
 * encontró que la misma familia entraba por otras dos puertas. Todas comparten la forma: `paidPct`
 * clampa a `[0, 1]`, así que **cualquier** deuda que no sea positiva sale como 1.0.
 *
 * - **Deuda negativa.** Reproducido: crédito creado en $0 y después un abono extraordinario —la
 *   operación que esta ola estrena— deja la deuda en −$1.500.000 sobre un crédito de
 *   $60.000.000 que nunca recibió su desembolso. Ahí `hasMovements` vale `true` (hubo un
 *   movimiento) y la guarda de arriba no dispara. Es el mismo escenario del bloqueante anterior
 *   —el dueño interrumpido entre el paso 1 y el paso 2— con los dos pasos en orden invertido, y
 *   esta rama lo vuelve alcanzable: antes un crédito nacía siempre con deuda y ningún traspaso
 *   podía tocarlo, así que pasarse de la deuda entera era inverosímil.
 *
 * - **Deuda en otra moneda** (preexistente, no la trae esta rama). `account.balance` es el
 *   **componente COP** del saldo (ver `enrichWith`), así que un préstamo cuyos movimientos son
 *   todos en dólares tiene `balance = 0` con `hasMovements = true`, y daba «100% pagado» sobre
 *   una deuda intacta. El porcentaje compara contra un `principal` en COP: sobre un saldo que no
 *   está en COP no hay nada que comparar, y decirlo es más honesto que calcularlo.
 *
 * En los dos casos se **suprime el porcentaje** en vez de inventarle un número: la tarjeta pasa a
 * pedir que se revise, que es lo único cierto que se puede decir.
 *
 * Lo que NO se toca es `totalDebtCop`: una deuda negativa le resta al total, y eso es la suma
 * honesta de lo que hay registrado. Corregirla ahí sería tapar la anomalía justo en la cifra que
 * el dueño usa para confiar; el lugar donde se señala es la tarjeta del crédito que la causó.
 *
 * Solo aplica con términos y capital original cargados: sin eso no hay porcentaje que calcular ni
 * desembolso que reclamar, y la tarjeta se comporta como siempre.
 */
fun progresoDeCredito(credit: CreditSummary): ProgresoDeCredito {
    val capital = credit.terms?.principal ?: 0L
    if (capital <= 0L) return porcentajePagado(credit)

    if (!credit.hasMovements) return aviso("Falta registrar el desembolso")
    // Antes que el signo: con saldo en otra moneda el componente COP es 0 y nunca sería negativo,
    // así que este es el motivo real y el que se le debe explicar.
    if (deudaEnOtraMoneda(credit)) return aviso("Deuda en otra moneda")
    if (credit.account.balance < 0L) return aviso("Deuda en negativo — revísala")

    // **La deuda pasó por encima del capital original.** No hay progreso que pintar, y «0 % pagado»
    // dice justo lo contrario de lo que pasó. Ver [ProgresoDeCredito.mostrarBarra].
    val deMas = credit.account.balance - capital
    if (deMas > 0L) {
        return ProgresoDeCredito(
            etiqueta = formatCOP(deMas) + " más que al inicio",
            fraccion = 0f,
            esAviso = true,
            mostrarBarra = false,
        )
    }

    return porcentajePagado(credit)
}

private fun aviso(texto: String) = ProgresoDeCredito(texto, fraccion = 0f, esAviso = true)

private fun porcentajePagado(credit: CreditSummary): ProgresoDeCredito {
    val pct = (credit.paidPct ?: 0.0).toFloat()
    return ProgresoDeCredito("${(pct * 100).toInt()}% pagado", fraccion = pct, esAviso = false)
}

/**
 * **«$2.479.256 de interés · 60 % de la cuota»** — lo que de la cuota es alquiler de la plata.
 *
 * [desglosarCuota] ya calculaba esto cada vez que se registraba un pago, lo usaba para mover el
 * saldo y lo tiraba. Es la primera de las tres preguntas que la pantalla no contestaba, y en la
 * cartera del dueño la respuesta va del **29,8 %** (Libre inversión ·9695) al **100 %** (Crédito
 * Mamá).
 *
 * El porcentaje va con **un decimal**: entre 29,8 % y 30 % hay $2.500 al mes, y redondear al entero
 * los haría desaparecer justo en el crédito donde más importan. El decimal se omite cuando es cero,
 * para que «100 %» no se lea «100,0 %».
 */
fun textoDelInteres(plan: PlanDelCredito): String = when (plan.comoVa) {
    // Sin tasa no es 0 %: es que no se sabe. Misma postura que [MotivoDelDesglose.SIN_TASA].
    ComoVaLaDeuda.SIN_TASA -> "Sin tasa registrada: no se sabe cuánto de la cuota es interés"

    // **Con tasa y sin cuota el interés SÍ se sabe.** Antes esto decía «Sin tasa registrada» tres
    // líneas debajo de la tasa, que estaba en la misma tarjeta y se veía.
    ComoVaLaDeuda.SIN_CUOTA -> formatCOP(plan.interes) + " de interés al mes · sin cuota registrada"

    ComoVaLaDeuda.SIN_DEUDA -> "Sin deuda registrada: todavía no corren intereses"

    else -> plan.fraccionDeInteres
        ?.let { formatCOP(plan.interes) + " de interés · " + porcentaje(it) + " de la cuota" }
        ?: "Sin tasa registrada: no se sabe cuánto de la cuota es interés"
}

/** «29,8 %», «100 %». Ver [textoDelInteres] para por qué el decimal. */
private fun porcentaje(fraccion: Double): String {
    val decimas = round(fraccion * 1000).toLong()
    val entero = decimas / 10
    val decima = decimas % 10
    return if (decima == 0L) "$entero %" else "$entero,$decima %"
}

/**
 * Lo que la tarjeta dice **debajo** del interés: cuándo se termina la deuda, o por qué no se
 * termina.
 *
 * @property texto la frase, ya armada.
 * @property esAlerta si va con el color de aviso. **Solo la amortización negativa de verdad.**
 *   Ver [ComoVaLaDeuda.LA_DEUDA_CRECE] para dónde queda el límite, y por qué el Crédito Mamá —cuya
 *   cuota también es interés puro— no lo cruza.
 */
data class ComoVaEstaDeuda(val texto: String, val esAlerta: Boolean)

/**
 * @param periodoActual el mes en curso, para poder decir «enero de 2046» en vez de «232 cuotas».
 *   Entra como parámetro y no se lee el reloj acá adentro para que esto siga siendo una función
 *   pura y probable sin congelar el tiempo.
 */
fun comoVaEstaDeuda(plan: PlanDelCredito, periodoActual: PeriodoFinanciero): ComoVaEstaDeuda? = when (plan.comoVa) {
    // Los tres estados sin proyección ya los dijo [textoDelInteres] en la línea de arriba;
    // repetirlos con otras palabras no agrega nada. En particular [ComoVaLaDeuda.SIN_DEUDA]:
    // un crédito al que le falta el desembolso ya dice eso en su etiqueta de progreso, y antes
    // decía además **«Ya está pagada»** acá abajo.
    ComoVaLaDeuda.SIN_TASA, ComoVaLaDeuda.SIN_CUOTA, ComoVaLaDeuda.SIN_DEUDA -> null

    // **La alerta.** En pesos y por mes, que es como se siente: una barra en 0 % no dice que a esta
    // cuota le faltan $21.894 para cubrir siquiera los intereses.
    ComoVaLaDeuda.LA_DEUDA_CRECE -> ComoVaEstaDeuda(
        "La cuota no alcanza: tu deuda crece " + formatCOP(-plan.capital) + " cada mes",
        esAlerta = true,
    )

    // Ni alerta ni fecha: la deuda se queda donde está. Es lo que pasa con el préstamo de su mamá,
    // y es el acuerdo, no un accidente.
    ComoVaLaDeuda.SOLO_INTERESES -> ComoVaEstaDeuda(
        "La cuota se va toda en intereses: la deuda se queda donde está",
        esAlerta = false,
    )

    ComoVaLaDeuda.AMORTIZA -> {
        val meses = plan.mesesHastaLaUltimaCuota
        if (meses == null || meses <= 0) {
            // Amortiza, pero tan despacio que una fecha sería una burla. Ver [MAX_MESES_PROYECTADOS].
            // (`meses <= 0` ya no es alcanzable —sin deuda no hay AMORTIZA— y queda como defensa.)
            ComoVaEstaDeuda("A este ritmo tardarías más de cien años", esAlerta = false)
        } else {
            val cuantas = if (meses == 1) "Te falta 1 cuota" else "Te faltan $meses cuotas"
            // **El supuesto viaja adentro de la frase.** [SUPUESTO_DE_LA_PROYECCION] vive en la
            // tarjeta de resumen, decenas de dp más arriba, y para cuando el dueño llega a la fecha
            // de un crédito ya no lo tiene a la vista. Una fecha sin su condición es una promesa.
            ComoVaEstaDeuda(
                cuantas + " · la última en " + nombreDe(periodoActual.mas(meses - 1)) +
                    " si la cuota y la tasa no cambian",
                esAlerta = false,
            )
        }
    }
}

/**
 * **El rótulo dice de qué habla cada cifra del resumen, y no una nota al pie.**
 *
 * Las cuatro cifras de arriba tienen **cuatro alcances distintos** —dos cortes cruzados: de quién
 * sale la plata, y si la deuda se termina— y antes solo uno de ellos estaba dicho, en letra chica y
 * solo cuando era mayor que cero. Así, «Te falta en intereses $549.605.074» era el 36 % de los
 * $1.511.826.418 que de verdad faltan en la cartera que sí termina, sin decirlo en ninguna parte; y
 * si el dueño solo tuviera los créditos que gira Skandia, la fila titular habría dicho «$0».
 *
 * Son constantes y no literales adentro del `@Composable` para que se puedan afirmar desde una
 * prueba: es exactamente el pedazo que se puede borrar sin que ninguna prueba de aritmética caiga.
 */
const val ALCANCE_INTERES_PROPIO: String = "Los pagas tú"
const val ALCANCE_INTERES_AJENO: String = "Los paga tu nómina o un tercero"
const val ALCANCE_FALTA_PROPIO: String = "En tus créditos que se terminan"
const val ALCANCE_FALTA_AJENO: String = "En los que paga otro y se terminan"
const val ALCANCE_ULTIMA_CUOTA: String = "Contando todas tus deudas"

/** El título del grupo de filas del interés del mes. */
const val TITULO_INTERES_DEL_MES: String = "Intereses este mes"

/** El título del grupo de filas del interés que falta por pagar. */
const val TITULO_INTERES_POR_PAGAR: String = "Te falta en intereses"

/** El título de la fila de la fecha final. */
const val TITULO_ULTIMA_CUOTA: String = "Tu última cuota"

/**
 * Cuándo cae la última cuota de toda su deuda, o **por qué no hay fecha**.
 *
 * Una deuda que no se termina le gana a cualquier fecha: con los doce créditos reales, decir
 * «diciembre de 2045» mientras $304.183.376 no bajan es contestar otra pregunta. Acá se contesta
 * «Sin fecha» y el aviso de abajo dice cuánto y en cuántos créditos.
 */
fun textoDeLaUltimaCuota(resumen: ResumenDeDeudas, periodoActual: PeriodoFinanciero): String =
    resumen.mesesHastaLaUltimaCuota
        ?.let { nombreDe(periodoActual.mas((it - 1).coerceAtLeast(0))) }
        ?: "Sin fecha"

/**
 * **La plata que no se acaba nunca, dicha en pesos.**
 *
 * `ResumenDeDeudas.creditosQueNoSeTerminan` se calculaba y se probaba, y no se dibujaba en ninguna
 * pantalla: la única advertencia visible contaba 1 (la amortización negativa del ·2334) y el
 * Crédito Mamá —$100.000.000 que no bajan— no aparecía en ningún lado del resumen.
 */
fun textoDeLoQueNoSeTermina(cuantos: Int, deuda: Long): String {
    val cabeza = if (cuantos == 1) "1 crédito no se termina a este ritmo" else "$cuantos créditos no se terminan a este ritmo"
    return cabeza + ": " + formatCOP(deuda) + " que no bajan"
}

/**
 * **La alerta de arriba, contada en créditos.** Si hay uno solo en el que la deuda crece sola, el
 * dueño tiene que salir de esta pantalla sabiéndolo.
 */
fun textoDeLaAmortizacionNegativa(cuantos: Int): String = if (cuantos == 1) {
    "En 1 crédito la cuota no cubre los intereses: esa deuda crece sola"
} else {
    "En $cuantos créditos la cuota no cubre los intereses: esas deudas crecen solas"
}

/**
 * La frase que acompaña a toda proyección de esta pantalla.
 *
 * **No es letra chica y por eso no está en letra chica**: una fecha a veinte años sale de suponer
 * que la cuota y la tasa no se mueven, y en Colombia la tasa de un hipotecario se recalcula. Decir
 * la fecha sin decir el supuesto es inventar precisión, que es la forma más cara de mentir con
 * números que se ven bien.
 */
const val SUPUESTO_DE_LA_PROYECCION: String =
    "Proyectado con la cuota y la tasa de hoy. Si el banco las recalcula, las fechas cambian."

/**
 * La barra de «% pagado» de la tarjeta de un préstamo.
 *
 * Existe solo para poder afirmar en una prueba que **no está**: una barra de 2 dp sin texto no
 * tiene nada por dónde agarrarla desde `onNodeWithText`, y sin esta etiqueta la regla de
 * [ProgresoDeCredito.mostrarBarra] quedaba probada en el dato y no en la pantalla — que es
 * justo donde importa, porque lo que engañaba era el dibujo.
 */
const val TAG_BARRA_DE_PROGRESO: String = "barra-de-progreso-del-credito"
