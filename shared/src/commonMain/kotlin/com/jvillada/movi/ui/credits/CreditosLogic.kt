package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.ComoVaLaDeuda
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.QueLograElAbono
import com.jvillada.movi.shared.model.ResumenDeDeudas
import com.jvillada.movi.shared.model.SimulacionDeAbono
import com.jvillada.movi.shared.model.abonoMinimoParaQueSeTermine
import com.jvillada.movi.shared.model.deudaEnOtraMoneda
import com.jvillada.movi.shared.model.mas
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.simularAbonoUnico
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

// ---------------------------------------------------------------- «¿y si le abono de más?»

/**
 * El rótulo de la acción que abre el simulador, en la tarjeta de cada préstamo.
 *
 * Es una pregunta y no un sustantivo («Simulador», «Abono extraordinario») porque lo que abre no
 * registra nada: contesta algo. Un botón que suena a operación en la misma fila que «Ajustar
 * saldo» —que sí escribe— invitaría a creer que Movi acaba de abonar la plata.
 */
const val ACCION_SIMULAR_ABONO: String = "¿Y si abonas de más?"

/** El encabezado de la hoja del simulador. */
const val TITULO_DEL_SIMULADOR: String = "SIMULAR UN ABONO"

/**
 * **El supuesto propio del abono**, además del que ya arrastra toda proyección de esta pantalla
 * ([SUPUESTO_DE_LA_PROYECCION]).
 *
 * Un abono a capital se aplica de dos formas y **el banco hace la que uno le pida**: bajando el
 * plazo (la cuota sigue igual, la deuda se acaba antes) o bajando la cuota (el plazo sigue igual).
 * Acá se supone lo primero, que es lo único compatible con proyectar con la cuota que está en los
 * términos. Si el dueño pide lo segundo —o si el banco lo aplica así por defecto, que pasa— ni la
 * fecha ni el ahorro de esta hoja valen.
 *
 * Va en la hoja y no en un comentario porque es la diferencia entre una simulación y una promesa,
 * y porque es **accionable**: le dice qué tiene que pedirle al banco para que esto se cumpla.
 */
const val SUPUESTO_DEL_ABONO: String =
    "Supone que el abono va todo a capital y que el banco te acorta el plazo, no la cuota. " +
        "Pídelo así: si te bajan la cuota, la fecha no se mueve."

/**
 * **El tercer supuesto: la estimación del interés se queda corta, y siempre para el mismo lado.**
 *
 * Los otros dos supuestos de esta hoja hablan del futuro —que la tasa y la cuota no se muevan
 * ([SUPUESTO_DE_LA_PROYECCION]), que el banco acorte el plazo ([SUPUESTO_DEL_ABONO])— y los dos
 * dejan creer que, si eso se cumple, la cifra es exacta. No lo es: **la estimación misma tiene
 * sesgo**. Movi calcula el interés como `saldo × tasa mensual`, y medido contra un extracto real
 * (Libre inversión ·9695) el banco cobró **$473.227 donde Movi estimaba $363.905** — un 30 % más,
 * y «siempre en la misma dirección» (ver `CreatePagoDeCuotaRequest.interesReal`).
 *
 * Un error del 30 % en el interés de un mes se apila a lo largo de sesenta o cuatrocientos meses
 * de proyección. Esta hoja convierte esa cifra en un **comparador entre créditos** —cuál matar
 * primero— y un comparador aguanta el sesgo mientras el sesgo apunte igual en los dos lados; lo
 * que no aguanta es presentarse al peso. Por eso, además de esta frase, el ahorro se muestra
 * redondeado y con un «unos» delante (ver [montoAproximado]).
 */
const val SUPUESTO_DE_LA_ESTIMACION: String =
    "El interés lo estimamos con la tasa, y contra el extracto se queda corto —en un crédito " +
        "medido, un 30 %—. Toma estos ahorros como un orden de magnitud, no como una cifra al peso."

/**
 * **La cuota la paga un tercero, así que el ahorro en intereses tampoco sería suyo.**
 *
 * Son las dos hipotecas que gira Skandia ([CreditTerms.paidBy]). La deuda **es de él** y puede
 * abonarle —por eso la simulación no se bloquea— pero el interés que se deja de pagar lo deja de
 * pagar quien paga la cuota. Sin esta línea, la hoja le diría «te ahorras $949.733.944» sobre una
 * plata que hoy no sale de su bolsillo ni saldría después.
 *
 * **No aplica a las libranzas**, y esa distinción costó este aviso una revisión entera: ver
 * [AVISO_DE_ABONO_POR_LIBRANZA].
 */
const val AVISO_DE_ABONO_AJENO: String =
    "Esta cuota no la pagas tú: la deuda sí es tuya, pero el ahorro en intereses no sería tuyo."

/**
 * **Una libranza sí la paga él**, y decirle lo contrario le costaría plata.
 *
 * [saleDeTuBolsillo] es `false` para los dos casos —libranza y tercero— porque contesta una
 * pregunta de **flujo de caja**: si la cuota aparece o no como salida de la cuenta. Para el abono
 * la pregunta es otra, la de **de quién es la plata**, y ahí los dos casos se separan:
 *
 * - **Libranza** ([CreditTerms.payrollDeduction]): el empleador la retiene antes de depositar el
 *   sueldo, o sea que el salario que llega a la cuenta **ya viene neto**. Esa cuota sale de su
 *   plata, solo que antes de que la vea; el ahorro en intereses es suyo entero, y el abono además
 *   saldría de su bolsillo de la forma normal.
 * - **La paga un tercero** ([CreditTerms.paidBy]): ahí sí, ni la cuota ni el ahorro son suyos. Ver
 *   [AVISO_DE_ABONO_AJENO].
 *
 * Fundir los dos casos le decía a la Libranza ·4818 —$255.677.421 al 18,01 %, su **segundo**
 * crédito más grande— que abonarle no le ahorraba a él, que es exactamente lo contrario de lo
 * cierto, y sobre el crédito donde el ahorro es más grande. La tarjeta de atrás ya los distinguía
 * («de tu nómina» contra «la paga Skandia»); la hoja no.
 */
const val AVISO_DE_ABONO_POR_LIBRANZA: String =
    "Esta cuota te la descuentan de la nómina: el sueldo te llega neto, así que la plata es tuya " +
        "y el ahorro en intereses también. El abono sí saldría de tu bolsillo."

/**
 * Lo que la hoja aclara sobre quién paga esta cuota, o `null` cuando no hay nada que aclarar
 * (la paga él, de su cuenta, como cualquier otra).
 *
 * @property esAdvertencia si va con el color de aviso. **Solo cuando el ahorro no sería suyo**:
 *   una libranza no es una advertencia, es una aclaración — y pintarla de amarillo diría con el
 *   color lo que el texto acaba de negar.
 */
data class AvisoDeQuienPaga(val texto: String, val esAdvertencia: Boolean)

/**
 * Qué le aclara la hoja al dueño sobre quién paga esta cuota. Ver [AVISO_DE_ABONO_POR_LIBRANZA]
 * para por qué son dos casos y no uno.
 *
 * Con los dos marcados a la vez —que hoy no pasa en ningún crédito— gana [CreditTerms.paidBy]: un
 * tercero nombrado es un dato más específico que un booleano, y de los dos errores posibles este
 * es el barato (aclarar de más sobre una plata que sí es suya, en vez de prometerle un ahorro que
 * no lo es).
 */
fun avisoDeQuienPagaLaCuota(terms: CreditTerms): AvisoDeQuienPaga? = when {
    !terms.paidBy.isNullOrBlank() -> AvisoDeQuienPaga(AVISO_DE_ABONO_AJENO, esAdvertencia = true)
    terms.payrollDeduction -> AvisoDeQuienPaga(AVISO_DE_ABONO_POR_LIBRANZA, esAdvertencia = false)
    else -> null
}

/**
 * **¿El interés que se ahorra este abono lo deja de pagar él?**
 *
 * No es [saleDeTuBolsillo] negado: una libranza no sale de la cuenta y el ahorro **sí** es suyo.
 * Ver [AVISO_DE_ABONO_POR_LIBRANZA].
 */
fun elAhorroSeriaTuyo(terms: CreditTerms): Boolean = terms.paidBy.isNullOrBlank()

/** Lo que la hoja dice mientras todavía no hay monto escrito. */
const val PIDE_UN_MONTO: String = "Escribe cuánto abonarías, o toca uno de los montos de arriba."

/**
 * Lo que la hoja del simulador contesta.
 *
 * @property titular la respuesta en una línea: el ahorro, la fecha nueva, o que no alcanza.
 * @property detalle la segunda línea, con lo que hace falta para entender el titular (contra qué
 *   fecha se compara, cuánto sobra, cuánto haría falta). `null` cuando el titular se explica solo.
 * @property esAlerta si va con el color de aviso. **Solo cuando el abono no alcanza y la deuda
 *   sigue creciendo**: el mismo criterio que [ComoVaEstaDeuda.esAlerta], para que las dos partes de
 *   esta pantalla no llamen alerta a cosas distintas.
 */
data class ResultadoDelAbono(val titular: String, val detalle: String?, val esAlerta: Boolean)

/**
 * **El mínimo que le pone fecha a una deuda eterna, con la fecha que compra.** Ver
 * [abonoMinimoParaQueSeTermine].
 *
 * Los dos datos viajan **juntos y en el mismo objeto** a propósito. El monto solo —$2.549.401
 * sobre una deuda de $204 millones— se lee «un abono chico resuelve el ·2334», y lo que de verdad
 * compra son **479 cuotas, hasta 2066**. Tenerlos separados fue lo que dejó que la rama de «no
 * alcanza» mostrara el número desnudo mientras la descripción del cambio prometía lo contrario.
 */
data class MinimoConSuFecha(val monto: Long, val cuotas: Int)

/**
 * El mínimo de este crédito con su fecha, o `null` si la deuda ya se termina (o si ningún abono
 * la termina). **Es una búsqueda binaria sobre treinta y pico de proyecciones más una proyección
 * más**: se calcula una vez por crédito, no en cada tecla.
 */
fun minimoConSuFecha(credit: CreditSummary): MinimoConSuFecha? {
    val monto = abonoMinimoParaQueSeTermine(credit) ?: return null
    val cuotas = simularAbonoUnico(credit, monto)?.despues?.mesesHastaLaUltimaCuota ?: return null
    return MinimoConSuFecha(monto, cuotas)
}

/**
 * La respuesta de la hoja, ya en palabras. `null` cuando no hay nada que contestar todavía
 * ([QueLograElAbono.NO_SE_PUEDE_SIMULAR]).
 *
 * @param minimo lo que haría falta para que la deuda se termine, con su fecha
 *   ([minimoConSuFecha]), o `null`. Entra como parámetro y no se calcula acá porque es una
 *   búsqueda binaria sobre la deuda —treinta y pico de proyecciones— y esto lo llama cada tecla
 *   que el dueño escribe en el monto. La hoja lo calcula una vez por crédito.
 * @param elAhorroSeriaTuyo si el interés que se ahorra lo deja de pagar él ([elAhorroSeriaTuyo]).
 *   Cuando no, el titular **no dice «te ahorras»**: decirlo a 14sp mientras el aviso de arriba
 *   niega lo mismo a 11,5sp deja a la pantalla contradiciéndose, y gana la cifra grande.
 */
fun textoDeLaSimulacion(
    sim: SimulacionDeAbono,
    periodoActual: PeriodoFinanciero,
    minimo: MinimoConSuFecha?,
    elAhorroSeriaTuyo: Boolean,
): ResultadoDelAbono? = when (sim.logro) {
    QueLograElAbono.NO_SE_PUEDE_SIMULAR -> null

    // **La deuda se acaba hoy.** El ahorro es todo lo que faltaba… cuando faltaba algo finito: si
    // hoy la deuda no se terminaba, no hay cifra que restar y el titular no la promete.
    QueLograElAbono.SALDA_LA_DEUDA -> ResultadoDelAbono(
        titular = sim.interesQueSeAhorra
            ?.let { "Con esto la saldas hoy y " + fraseDelAhorro(it, elAhorroSeriaTuyo) }
            ?: "Con esto la saldas hoy: se acaba una deuda que a este ritmo no se terminaba",
        detalle = listOfNotNull(
            sim.cuotasQueSeAhorra?.let { if (it == 1) "Te quitas 1 cuota de encima." else "Te quitas $it cuotas de encima." },
            // Lo que sobra no ahorró un peso, y decir «te ahorras» sobre el total lo sugeriría.
            // Acá sí van las dos cifras al peso: el sobrante es `abono − saldo`, aritmética entre
            // dos números que Movi conoce exactos, y no una estimación de interés.
            sim.sobrante.takeIf { it > 0L }
                ?.let { "Te sobran " + formatCOP(it) + ": la deuda es de " + formatCOP(sim.antes.saldo) + "." },
        ).joinToString(" ").ifBlank { null },
        esAlerta = false,
    )

    // El caso normal: dos fechas que restar. El ahorro va de titular porque es lo que se compara
    // entre créditos; las fechas van debajo porque sin la de hoy la nueva no dice nada.
    QueLograElAbono.ACORTA_EL_PLAZO -> ResultadoDelAbono(
        titular = fraseDelAhorro(sim.interesQueSeAhorra ?: 0L, elAhorroSeriaTuyo).replaceFirstChar { it.uppercase() },
        detalle = textoDeLasDosFechas(sim, periodoActual),
        esAlerta = false,
    )

    // **De «nunca» a una fecha.** Es lo más valioso que dice esta hoja, y por eso va de titular sin
    // el ahorro al lado: no hay ahorro que calcular contra un plazo infinito.
    QueLograElAbono.LE_PONE_FECHA -> ResultadoDelAbono(
        titular = "Esta deuda pasa a tener final: " + fechaFinal(sim.despues, periodoActual),
        detalle = comoEstaHoy(sim.antes) + " Con este abono te faltarían " +
            cuotas(sim.despues.mesesHastaLaUltimaCuota ?: 0) + ".",
        esAlerta = false,
    )

    // No alcanzó. La cifra que sigue es cuánto haría falta — sin ella el dueño no sabe si le faltó
    // poco o le faltó todo. **Y va con su fecha en la misma frase**: $2.549.401 leídos solos
    // parecen alcanzables, y lo que compran son 479 cuotas. Ver [MinimoConSuFecha].
    QueLograElAbono.NO_ALCANZA -> ResultadoDelAbono(
        titular = "Con este abono la deuda sigue sin terminarse",
        detalle = comoQuedaria(sim.despues) + (minimo?.let { conQueFecha(it, periodoActual) } ?: ""),
        esAlerta = sim.despues.comoVa == ComoVaLaDeuda.LA_DEUDA_CRECE,
    )
}

/**
 * «te ahorras unos $970.000 en intereses», o la misma cifra sin dueño cuando el ahorro no es suyo.
 * Ver [elAhorroSeriaTuyo] y [montoAproximado].
 */
private fun fraseDelAhorro(interes: Long, elAhorroSeriaTuyo: Boolean): String = if (elAhorroSeriaTuyo) {
    "te ahorras " + montoAproximado(interes) + " en intereses"
} else {
    "esta deuda pagaría " + montoAproximado(interes) + " menos en intereses"
}

/** «Harían falta $2.549.401, y aun así te faltarían 479 cuotas: hasta agosto de 2066.» */
private fun conQueFecha(minimo: MinimoConSuFecha, periodoActual: PeriodoFinanciero): String =
    " Harían falta " + formatCOP(minimo.monto) + ", y aun así te faltarían " + cuotas(minimo.cuotas) +
        ": hasta " + nombreDe(periodoActual.mas((minimo.cuotas - 1).coerceAtLeast(0))) + "."

/**
 * «Terminas 3 cuotas antes: en junio de 2030 en vez de septiembre de 2030.»
 *
 * ### Y cuando no adelanta ninguna, que es el caso más común
 *
 * «Me sobraron cien mil» no mueve el plazo: $100.000 al ·9695 ahorran $50.585 de interés y las
 * cuotas siguen siendo 46. Con la plantilla de arriba eso salía **«en junio de 2030 en vez de
 * junio de 2030»** —la misma fecha dos veces, que no es una comparación sino una errata— y además
 * dejaba sin explicar de dónde sale el ahorro: si el plazo no se mueve, sale de que **la última
 * cuota queda más pequeña** (medido en el ·9695: de $519.503 a $368.918). O sea de lo contrario de
 * lo que dice [SUPUESTO_DEL_ABONO], que es el supuesto de qué hace el banco con el abono, no una
 * promesa de que el plazo siempre se mueva. Decir las dos cosas es más honesto que repetir un mes.
 */
private fun textoDeLasDosFechas(sim: SimulacionDeAbono, periodoActual: PeriodoFinanciero): String {
    val menos = sim.cuotasQueSeAhorra ?: 0
    if (menos <= 0) {
        return "No te adelanta ninguna cuota: la última sigue siendo la de " +
            fechaFinal(sim.despues, periodoActual) + ", solo que más pequeña. De ahí sale el ahorro."
    }
    val cabeza = if (menos == 1) "Terminas 1 cuota antes" else "Terminas $menos cuotas antes"
    return cabeza + ": en " + fechaFinal(sim.despues, periodoActual) +
        " en vez de " + fechaFinal(sim.antes, periodoActual) + "."
}

/** El mes de la última cuota de un plan, o «sin fecha» si no la tiene. */
private fun fechaFinal(plan: PlanDelCredito, periodoActual: PeriodoFinanciero): String =
    plan.mesesHastaLaUltimaCuota
        ?.let { nombreDe(periodoActual.mas((it - 1).coerceAtLeast(0))) }
        ?: "sin fecha"

/**
 * Cómo está la deuda **hoy**, sin el abono. Se usa para las dos que no se terminan, y **no** son
 * el mismo caso: una crece sola y la otra se queda quieta.
 */
private fun comoEstaHoy(plan: PlanDelCredito): String = when (plan.comoVa) {
    ComoVaLaDeuda.LA_DEUDA_CRECE -> "Hoy la deuda crece " + formatCOP(-plan.capital) + " cada mes."
    ComoVaLaDeuda.SOLO_INTERESES -> "Hoy la cuota se va toda en intereses."
    else -> "Hoy esta deuda no se termina a este ritmo."
}

/**
 * Cómo quedaría la deuda **con el abono puesto**, cuando el abono no alcanzó.
 *
 * Va aparte de [comoEstaHoy] aunque mire los mismos dos estados: el tiempo verbal es la mitad de
 * la información. Decir «hoy la deuda crece $10.011» sobre el saldo YA abonado mezcla las dos
 * cifras que esta hoja está comparando —el ·2334 crece $21.894 hoy y $10.011 con el millón
 * encima— y deja al dueño sin saber si el abono sirvió de algo.
 */
private fun comoQuedaria(plan: PlanDelCredito): String = when (plan.comoVa) {
    ComoVaLaDeuda.LA_DEUDA_CRECE -> "Con ese abono seguiría creciendo " + formatCOP(-plan.capital) + " cada mes."
    ComoVaLaDeuda.SOLO_INTERESES -> "Con ese abono la cuota se seguiría yendo toda en intereses."
    else -> "Con ese abono esta deuda seguiría sin terminarse."
}

private fun cuotas(meses: Int): String = if (meses == 1) "1 cuota" else "$meses cuotas"

/**
 * **Un ahorro proyectado, dicho con la precisión que tiene**: «unos $970.000», no «$971.366».
 *
 * Se redondea a **dos cifras significativas** y se le antepone «unos». No es cosmética: el interés
 * de cada mes sale de `saldo × tasa mensual`, que contra el extracto se queda corto —un 30 % en el
 * único crédito que se pudo medir, y siempre para el mismo lado (ver [SUPUESTO_DE_LA_ESTIMACION]).
 * Un sesgo así, apilado sobre cuarenta y seis o cuatrocientas setenta y nueve cuotas, no deja en
 * pie el séptimo dígito de nada. Dos cifras alcanzan de sobra para lo que esta hoja hace, que es
 * **ordenar créditos entre sí**.
 *
 * ### Qué NO se redondea, y por qué no es inconsistente
 *
 * - **Los montos que son instrucciones**: el abono mínimo, los chips, el sobrante. Ahí la cifra no
 *   es un pronóstico sino un número que el dueño va a teclear en el banco o restar de su cuenta, y
 *   redondear un umbral le cambia el valor de verdad («$2.550.000» ya no es *el mínimo*).
 * - **Las cifras de un solo mes** («la deuda crece $21.894»): son un mes de la misma estimación
 *   que la tarjeta de atrás ya muestra al peso, sin sesgo acumulado, y redondear acá lo que allá
 *   se muestra exacto haría que la misma pantalla dijera dos números para lo mismo.
 */
fun montoAproximado(monto: Long): String = "unos " + formatCOP(redondeadoADosCifras(monto))

/** El redondeo de [montoAproximado], en Long y sin logaritmos: 971.366 → 970.000. */
internal fun redondeadoADosCifras(monto: Long): Long {
    if (monto <= 0L) return monto
    var escala = 1L
    while (monto / escala >= 100L) escala *= 10L
    return (monto + escala / 2) / escala * escala
}

/**
 * Un monto que la hoja ofrece con un toque.
 *
 * @property etiqueta lo que dice el chip. **Dice qué es el monto, no cuánto es**: «Una cuota más»
 *   se entiende sin leer siete dígitos, y el monto aparece igual en el campo apenas se toca.
 */
data class MontoSugerido(val etiqueta: String, val monto: Long)

/**
 * **Los montos que significan algo para ESTE crédito**, para que elegir cuánto abonar no sea
 * teclear siete dígitos a ciegas.
 *
 * No son cifras redondas genéricas ($500.000, $1.000.000): sobre el Crediágil ·3090, que debe
 * $507.553, la mitad de esa lista estaría por encima de la deuda entera. Son los cuatro montos que
 * cambian la respuesta:
 *
 * - **Una cuota más** y **tres cuotas más**: la unidad en la que el dueño ya piensa este crédito, y
 *   lo que de verdad le sobra un mes bueno.
 * - **Lo mínimo para que se termine**, solo en las deudas que hoy no se terminan. Es la cifra que
 *   convierte «nunca» en una fecha, y no hay forma de que se le ocurra sola: en el Hipotecario
 *   ·2334 son $2.549.401 sobre una deuda de $204 millones. Ver [abonoMinimoParaQueSeTermine].
 * - **Saldarla**: el techo, y la única forma de saber cuánto es «todo».
 *
 * Se saltean los que no aplican: nada de ofrecer «tres cuotas» cuando tres cuotas son más que la
 * deuda —«Saldarla» ya cubre ese caso y con el número correcto—, y nada de repetir el mismo monto
 * dos veces con dos nombres (en el ·3090, con $507.553 de deuda y cuota de $26.485, eso no pasa;
 * en un crédito al que le queda **una** cuota, «Una cuota más» y «Saldarla» son el mismo peso).
 */
fun montosSugeridosDeAbono(saldo: Long, cuota: Long, abonoMinimo: Long?): List<MontoSugerido> {
    if (saldo <= 0L) return emptyList()
    val sugeridos = listOfNotNull(
        cuota.takeIf { it > 0L }?.let { MontoSugerido("Una cuota más", it) },
        cuota.takeIf { it > 0L }?.let { MontoSugerido("Tres cuotas", it * 3) },
        abonoMinimo?.let { MontoSugerido("Lo mínimo para que se termine", it) },
        MontoSugerido("Saldarla", saldo),
    )
    val vistos = mutableSetOf<Long>()
    return sugeridos.filter { it.monto in 1L..saldo && vistos.add(it.monto) }
}
