package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.montoMensualEquivalente
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import kotlin.math.roundToLong

/**
 * Las reglas puras de los recurrentes (Ola 8), sin Compose, para poder testearlas — y, sobre
 * todo, para que la cifra del acceso «Recurrentes» del Inicio y el «Flujo libre» de Movimientos
 * salgan de la MISMA función y no puedan discrepar. Ese desacuerdo entre dos pantallas que dicen
 * contar lo mismo ya había pasado antes (Créditos vs. Inicio en la Ola 4).
 *
 * (Nacieron para la pantalla «Recurrentes», que el rediseño de 2026-09 disolvió dentro de
 * Movimientos; el archivo se queda donde está porque lo que hay acá nunca fue de una pantalla.)
 */

/**
 * Nombre comparable: sin mayúsculas, sin acentos y sin nada que no sea letra o número, para
 * que «Netflix», «netflix» y «NETFLIX  Premium.» no se lean como cosas distintas.
 *
 * A propósito NO intenta ser inteligente (nada de distancias de edición ni de subcadenas):
 * cuanto más suelta la comparación, más fácil es esconder una fila real del total, y
 * equivocarse hacia abajo en el dinero es peor que mostrar un duplicado marcado.
 *
 * Lo que esto SÍ deja pasar, y conviene tener presente: dos cosas distintas que se llaman
 * exactamente igual —el «Seguro» del carro anotado como regla y un «Seguro» de otra cosa que
 * cobra una tarjeta— se leen como la misma y la segunda queda fuera del total. No hay forma de
 * distinguirlas por el nombre, que es lo único que comparten los dos modelos; la fila queda
 * marcada en la lista («ya lo tienes como recurrente»), así que el caso es visible aunque no
 * sea evitable. Ver también el reparto uno-a-uno en [resumenRecurrentes].
 *
 * **La implementación se mudó a `:core`** ([claveComparableDeNombre]) sin cambiar ni una regla:
 * ahora el server también la necesita, para proponer qué movimiento fue la ocurrencia de un
 * recurrente. Dos copias que normalizaran distinto harían que el server propusiera
 * emparejamientos que esta pantalla no sabría explicar. Este alias se queda para no reescribir
 * los llamados que ya existen.
 */
fun claveDeNombre(nombre: String): String = claveComparableDeNombre(nombre)

/**
 * PR 2 del rediseño de Recurrentes (2026-09): las suscripciones que el detector propuso y el
 * dueño todavía no revisó — ni confirmó ni descartó.
 *
 * Extraída de lo que calculaba en línea la pantalla «Recurrentes» (`candidatas`) para que
 * Movimientos —que ahora también necesita esta misma lista, en su propia sección «Detectadas ·
 * por confirmar»— la comparta en vez de recalcularla a mano. Pura y testeada por la misma razón
 * que el resto de este archivo: dos copias del mismo filtro es exactamente el defecto que este
 * archivo existe para evitar.
 */
fun candidatasSinConfirmar(subs: List<Subscription>): List<Subscription> =
    subs.filter { it.status == SubStatus.CANDIDATE }

/**
 * Cuánto pesa una suscripción en pesos, o `null` si no se puede saber.
 *
 * Replica lo que hace el server en `resultFor` (mismo redondeo por fila), con una diferencia
 * deliberada: donde el server escribe `else -> 0L`, esto devuelve `null`. Un cero se suma sin
 * dejar rastro; un `null` obliga a quien llama a decidir qué hacer con una fila que no supo
 * convertir — y en esta pantalla, a decirlo (ver [ResumenRecurrentes.sinConvertir]).
 *
 * Devuelve `null` en dos casos: una moneda que no es COP ni USD (hoy imposible: el alta valida
 * y el detector solo produce esas dos, pero el día que entre un EUR el server lo contaría como
 * 0 y el cliente lo mostraría como faltante en vez de tragárselo), y un USD sin tasa.
 *
 * **Ola 16 — prorratea primero y convierte después**, en ese orden, porque es el orden exacto
 * que usa `resultFor`: al revés, el redondeo del medio daría otro número y los dos totales que
 * dicen contar lo mismo se separarían por pesos. La división en sí no está acá ni allá, está en
 * `:core` ([montoMensualEquivalente]), que es lo que hace que no puedan discrepar. Para una
 * suscripción MENSUAL devuelve `amount` sin tocarlo, así que nada de lo que ya existía cambió.
 */
fun copDeSuscripcion(sub: Subscription, usdToCop: Double): Long? {
    val mensual = sub.montoMensualEquivalente()
    return when {
        sub.currency == "COP" -> mensual
        sub.currency == "USD" && usdToCop > 0.0 -> (mensual * usdToCop).roundToLong()
        else -> null
    }
}

/**
 * Un recurrente contado por [resumenRecurrentes]: lo que escribió el dueño y lo que cobra una
 * suscripción, en una sola lista ordenada por día del mes.
 *
 * PR 4 del rediseño de Recurrentes (2026-09) dejó esta lista solo para CONTAR —el acceso
 * «Recurrentes» del Inicio dice «libre al mes · N recurrentes»— porque la pantalla que la pintaba
 * fila por fila ya no existía. Desde el PR 5 la mitad de suscripciones **vuelve a pintarse**, en
 * la sección «Suscripciones activas» del chip «Recurrentes» de Movimientos: ver
 * [suscripcionesActivas], que la lee de acá justamente para no recalcular el reparto.
 *
 * Se queda como lista y no como un `Int` porque el reparto uno-a-uno de [resumenRecurrentes]
 * necesita igual la fila armada (`yaEsRegla` decide qué entra al total), y porque el día del mes
 * es lo que fija el orden.
 */
sealed class Recurrente {
    abstract val dayOfMonth: Int

    data class Regla(val rule: RecurringRule) : Recurrente() {
        override val dayOfMonth get() = rule.dayOfMonth
    }

    /**
     * @param yaEsRegla el dueño YA tiene una regla recurrente con este mismo nombre. La fila se
     *   cuenta igual (el cobro existe de verdad) pero no vuelve a sumar al total.
     */
    data class Suscripcion(val sub: Subscription, val yaEsRegla: Boolean) : Recurrente() {
        override val dayOfMonth get() = sub.dayOfMonth
    }
}

/**
 * **Las reglas que no existen como filas**: las que el server fabrica al vuelo para «Próximos»
 * desde las condiciones de un crédito o de una tarjeta.
 *
 * Se sacan de `GET /api/payments/upcoming` porque es el único lugar donde llegan —
 * `GET /api/recurring-rules` devuelve la tabla, y ahí no están (ver [nombreDeCuotaPagada], que
 * documenta el mismo hueco desde el otro lado). Se reconocen por el prefijo de su id, que es lo
 * único que las distingue de las que el dueño escribió.
 *
 * Devuelve las DOS clases, tarjetas incluidas, y **no** decide cuál suma: eso es de
 * [cuentaComoCompromisoMensual], y tener un segundo filtro acá sería la forma de que algún día
 * discrepen. Acá solo se contesta «¿de dónde salió esta regla?».
 */
fun reglasSinteticas(upcoming: List<UpcomingPayment>): List<RecurringRule> =
    upcoming.map { it.rule }.filter {
        it.id.startsWith(CREDIT_RULE_PREFIX) || it.id.startsWith(CARD_RULE_PREFIX)
    }

/**
 * **¿Esta regla es plata que sale del bolsillo TODOS los meses?** — la puerta única del «Flujo
 * libre».
 *
 * Desde 2026-09 la lista de reglas que entra a [resumenRecurrentes] ya no son solo las que el
 * dueño escribió: trae también las **sintéticas**, las que el server fabrica al vuelo para
 * «Próximos» desde las condiciones de un crédito o de una tarjeta (`CREDIT_RULE_PREFIX` /
 * `CARD_RULE_PREFIX`). Eso fue el pedido del dueño —sus cuotas son lo más grande que le sale al
 * mes y el total las ignoraba— pero **no todas las sintéticas son un compromiso mensual**, y
 * meterlas todas habría cambiado un número incompleto por uno inflado.
 *
 * Dice que **no** en dos casos, y los dos son «este monto no significa lo que parece»:
 *
 * - **La regla de una tarjeta** ([RecurringRule.montoEsSaldo]): su monto es la DEUDA de la
 *   tarjeta, no un pago. Sumarla sería repetir el error que ese campo ya documenta haber
 *   arreglado una vez —Movi anunciando $27.501.150 como el próximo pago de una tarjeta cuyo
 *   mínimo ronda el 5 %—, esta vez adentro de un total. Y encima el monto viene en la moneda de
 *   la cuenta, así que una tarjeta en dólares habría entrado a un total en pesos como si nada.
 *   Se mira **también el prefijo del id**, y no solo la marca: son la misma decisión dicha dos
 *   veces, y sostener un total con un `Boolean` con default `false` que tiene que acordarse de
 *   viajar es apostar plata a que ningún server lo omita nunca.
 * - **Un crédito de pago único** ([RecurringRule.esPagoUnico]): plazo ≤ 1 mes. Contarlo diría
 *   que tiene ese monto menos todos los meses, para siempre, cuando vence una sola vez.
 *
 * **Lo que NO se decide acá, porque ya está decidido antes:** la cuota que retiene el empleador
 * (libranza) y la que paga un tercero (Skandia, la esposa) **nunca llegan al cliente**. El server
 * las filtra en `loadCreditRulePairs` con `entraAlBarridoDeAvisos`, por el mismo razonamiento que
 * vale acá: el sueldo que el dueño registra ya viene NETO de la libranza, así que contar además
 * la cuota como gasto la restaría dos veces. Repetir ese filtro acá sería una segunda copia de
 * una regla que ya tiene dueño, y el día que discreparan el error sería justamente el doble
 * descuento. Si alguna vez esas reglas empezaran a viajar, esta función habría que ampliarla.
 */
fun cuentaComoCompromisoMensual(rule: RecurringRule): Boolean =
    !rule.montoEsSaldo && !rule.esPagoUnico && !rule.id.startsWith(CARD_RULE_PREFIX)

/**
 * Todo lo que la pantalla necesita mostrar arriba, calculado una sola vez.
 *
 * El punto delicado es [gastos]. Antes de la Ola 8, «gastos recurrentes» y «total de
 * suscripciones» eran dos cifras en dos pantallas distintas, y que el mismo Netflix estuviera
 * en las dos no producía ningún número malo. Al fundirlas en un solo «Flujo libre» sí: si el
 * dueño tiene la regla «Netflix» y además confirma la suscripción «Netflix» que le propuso el
 * detector, el mismo cobro entra dos veces y el flujo libre le queda por debajo de la realidad.
 * Las claves del comercio no lo delatan (`manual_netflix` vs `netflix`), así que la comparación
 * es por nombre normalizado — ver [claveDeNombre].
 *
 * Se resolvió EXCLUYENDO del total la suscripción solapada, y no bloqueando la confirmación,
 * porque la fila sigue siendo información legítima (el cobro existe, y el dueño quizá quiera
 * verlo con su día y su moneda). Lo que no puede pasar es que sume dos veces en silencio: la
 * fila queda marcada en la UI con «ya lo tienes como recurrente».
 *
 * @param sinConvertir cuántos cobros activos quedaron FUERA de [gastos] porque no se supo
 *   pasarlos a pesos. No es un detalle interno: el total está incompleto y la pantalla tiene
 *   que decirlo, en vez de restarlos en silencio y mostrar un flujo libre inflado.
 * @param hayMonedaExtranjera hay dólares que SÍ entraron a [gastos]. Mira lo que entró y no lo
 *   que existe: una suscripción en dólares excluida por duplicada no justifica avisar sobre una
 *   conversión que no se hizo.
 * @param hayCobrosAnuales hay cobros de una vez al año que SÍ entraron a [gastos], repartidos en
 *   doce. Es lo mismo que [hayMonedaExtranjera] pero para la otra transformación que le pasa a
 *   una fila entre la lista y el total, y existe por el mismo motivo: sin decirlo, el «Flujo
 *   libre» muestra $30.825 de algo que la lista de abajo dice que cuesta $369.900, y el dueño no
 *   tiene forma de saber cuál de los dos números está mal. Mira lo que ENTRÓ, así que un cobro
 *   anual excluido por duplicado no dispara una explicación sobre un prorrateo que no se usó.
 * @param cuotasDeCredito cuánto de [gastos] son cuotas de créditos. Desde 2026-09 entran al total
 *   (era el pedido del dueño: son lo más grande que le sale al mes) y este número existe para
 *   poder DECIRLO con una cifra que él pueda verificar contra sus créditos, en vez de que
 *   «Gastos recurrentes» crezca $5.445.772 de un mes al otro sin explicación. Mira lo que ENTRÓ,
 *   igual que los dos de arriba: sin créditos vale 0 y la pantalla no dice nada.
 * @param pagosUnicosFuera cuántas cuotas quedaron FUERA de [gastos] por ser de un crédito que se
 *   paga de una sola vez (ver [RecurringRule.esPagoUnico]). Se cuenta por el mismo motivo que
 *   [sinConvertir]: es una fila que existe, vence y aparece en «Próximos», y que este total no
 *   suma a propósito. Callarlo dejaría al dueño buscando $10.000.000 que no están.
 */
data class ResumenRecurrentes(
    val items: List<Recurrente>,
    val ingresos: Long,
    val gastos: Long,
    /**
     * **Cuánto de [gastos] son suscripciones** — el total que la sección «Suscripciones activas»
     * muestra al pie de su lista.
     *
     * Sale de acá y no de una suma propia de la pantalla por el mismo motivo por el que
     * [suscripcionesActivas] lee `items` en vez de filtrar la lista cruda: **el inventario y el
     * total que lo cierra tienen que salir del MISMO reparto.** Esta función no suma las filas
     * activas y ya — prorratea los cobros anuales, convierte los dólares con la tasa del server,
     * y saltea las que el dueño ya tiene anotadas como regla recurrente
     * ([Recurrente.Suscripcion.yaEsRegla]). Una suma hecha en la UI sobre las filas visibles
     * daría otro número, y las dos cifras se contradirían dentro de la misma pantalla: el defecto
     * exacto que este archivo existe para no repetir.
     *
     * Por eso **no es la suma de los montos que se ven en la lista**, y no debería serlo: un HBO
     * Max de $369.900 al año entra por $30.825, y una fila marcada «ya lo tienes como recurrente»
     * entra por cero. Las dos cosas ya están dichas fila por fila —ver `notaDeProrrateo` y
     * `contextoDeSuscripcionActiva` en TransactionsScreen—, así que el total no vuelve a
     * explicarlas: solo se anuncia como lo que es, una cifra del mes.
     *
     * Lo que [sinConvertir] dejó afuera de [gastos] está afuera de acá también, porque es el
     * mismo sumando. Un total incompleto se avisa; no se disimula.
     */
    val gastosDeSuscripciones: Long,
    val sinConvertir: Int,
    val hayMonedaExtranjera: Boolean,
    val hayCobrosAnuales: Boolean = false,
    val cuotasDeCredito: Long = 0L,
    val pagosUnicosFuera: Int = 0,
    /**
     * **Los pagos mínimos de las tarjetas con deuda**, sumados y en pesos. Ver
     * [RecurringRule.pagoMinimoCop].
     *
     * **No entra a [gastos], y ese es el punto entero.** El pago de una tarjeta no es un gasto del
     * mes —las compras ya contaron cuando se hicieron, y por eso `CARD_PAYMENT_CATEGORY` está
     * excluida de `isCashFlow` y [cuentaComoCompromisoMensual] deja afuera la regla `card_*`—,
     * pero sí es **plata comprometida**: el mínimo hay que pagarlo, no es una decisión. Meterlo
     * en «Gastos recurrentes» habría contado dos veces la misma plata (la compra y su pago) para
     * ganar una alarma; restarlo aparte da la alarma sin romper la regla.
     *
     * Por eso el número grande de la pantalla es [disponible] y no [flujoLibre], y los dos se
     * muestran: son dos preguntas distintas —cuánto sobra de lo recurrente, y cuánto sobra de
     * verdad— y colapsarlas en una sola cifra es lo que produjo el problema que esto arregla.
     */
    val minimosDeTarjeta: Long = 0L,
    /**
     * **Cuántas tarjetas con deuda no dijeron su mínimo**, y por eso no están en
     * [minimosDeTarjeta].
     *
     * Es la mitad honesta de la feature. Con las cinco tarjetas del dueño y ningún mínimo
     * cargado, [disponible] vale exactamente lo mismo que [flujoLibre] —**$601.574**— y sin este
     * contador la pantalla lo afirmaría como un hecho, que es el estado del que se viene: el
     * mínimo de su Master Black son $1.843.014, o sea que el número real es negativo, y Movi le
     * mostraba una cifra positiva donde tenía que haber una alarma.
     *
     * Cuenta las reglas `card_*` que llegaron sin [RecurringRule.pagoMinimoCop]. **Solo llegan
     * las tarjetas con deuda** (ver `loadCardRulePairs`), así que una tarjeta en $0 —el AMEX
     * ·9208, Nu, Davivienda ·9418— no pide un dato que no le hace falta a nadie.
     */
    val tarjetasSinMinimo: Int = 0,
) {
    /** Lo recurrente contra lo recurrente: ingresos menos gastos, sin las tarjetas. */
    val flujoLibre: Long get() = ingresos - gastos

    /**
     * **Lo que de verdad queda libre**: [flujoLibre] menos los mínimos de tarjeta que sí se
     * conocen. Es la cifra grande de la pantalla.
     *
     * Con [tarjetasSinMinimo] > 0 esto es un techo, no un hecho — puede ser tan optimista como
     * mínimos falten. La pantalla lo dice; este campo no puede.
     */
    val disponible: Long get() = flujoLibre - minimosDeTarjeta
}

/**
 * @param rules **todas** las reglas que el cliente conoce, incluidas las sintéticas de créditos y
 *   tarjetas — quién entra al total lo decide [cuentaComoCompromisoMensual], acá adentro y en un
 *   solo lugar. Antes cada pantalla filtraba por su cuenta antes de llamar (el Inicio descartaba
 *   por prefijo de id, Movimientos ni siquiera las pedía), que es la forma exacta en que las dos
 *   cifras que dicen contar lo mismo se separan.
 */
fun resumenRecurrentes(rules: List<RecurringRule>, subs: SubscriptionsResult): ResumenRecurrentes {
    val cuentan = rules.filter { cuentaComoCompromisoMensual(it) }
    // Reparto uno-a-uno: cada regla puede tapar UNA suscripción, no todas las que se llamen
    // igual. Con dos cobros «Netflix» distintos y una sola regla, excluir los dos borraría un
    // gasto real del total; así se excluye uno y el otro sigue contando.
    //
    // Sobre `cuentan` y no sobre `rules`: una regla que NO suma tampoco puede tapar una
    // suscripción que sí sumaba — eso borraría un gasto real del total en vez de evitar un
    // duplicado que no existe.
    val reglasDisponibles = cuentan.map { claveDeNombre(it.name) }.toMutableList()
    val activas = subs.subscriptions.filter {
        it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED
    }
    val suscripciones = activas.map { s ->
        val i = reglasDisponibles.indexOf(claveDeNombre(s.displayName))
        if (i >= 0) reglasDisponibles.removeAt(i)
        Recurrente.Suscripcion(s, yaEsRegla = i >= 0)
    }

    val huboExclusiones = suscripciones.any { it.yaEsRegla }
    val gastosDeSuscripciones: Long
    val sinConvertir: Int
    val dolaresEnElTotal: Boolean
    val anualesEnElTotal: Boolean
    if (!huboExclusiones) {
        // Nada que restar: el total que ya calculó el server es exacto — y esta rama es además
        // la que salva al cliente nuevo contra un server viejo que todavía no manda la tasa
        // (el APK se instala a mano y el server se despliega aparte, así que ese desfase pasa).
        // Ahí `monthlyTotalCop` sí trae los dólares convertidos; solo faltaba la tasa para
        // poder DESGLOSARLO, y sin exclusiones no hace falta desglosar nada.
        gastosDeSuscripciones = subs.monthlyTotalCop
        sinConvertir = 0
        dolaresEnElTotal = activas.any { it.currency != "COP" }
        // El total viene del server, que ya prorrateó (ver `resultFor`); acá solo hay que saber
        // si adentro hay algún cobro anual para poder explicarlo. Un server anterior a la Ola 16
        // no manda el campo y todas las filas llegan MENSUAL, así que esto da `false` y no se
        // explica un prorrateo que ese server tampoco hizo: las dos mitades del desfase dicen lo
        // mismo.
        anualesEnElTotal = activas.any { it.periodicidad == PeriodicidadDeCobro.ANUAL }
    } else {
        // Hay que sumar fila por fila para poder saltear las duplicadas, y eso sí necesita la
        // tasa. Lo que no se pueda convertir queda afuera Y contado, para que la pantalla avise.
        val aportes = suscripciones.filterNot { it.yaEsRegla }
            .map { it to copDeSuscripcion(it.sub, subs.usdToCop) }
        gastosDeSuscripciones = aportes.mapNotNull { it.second }.sum()
        sinConvertir = aportes.count { it.second == null }
        dolaresEnElTotal = aportes.any { it.second != null && it.first.sub.currency != "COP" }
        anualesEnElTotal = aportes.any {
            it.second != null && it.first.sub.periodicidad == PeriodicidadDeCobro.ANUAL
        }
    }

    val gastosDeReglas = cuentan.filter { it.type == TransactionType.EXPENSE }
    // **Sobre `rules` y no sobre `cuentan`, a propósito**: las reglas de tarjeta son justamente
    // las que `cuentaComoCompromisoMensual` deja afuera del total, y ahí se quedan — su monto es
    // la deuda. Lo que se saca de ellas es el otro campo, el mínimo, que no es un gasto del mes
    // pero sí plata comprometida. Ver [ResumenRecurrentes.minimosDeTarjeta].
    val reglasDeTarjeta = rules.filter { it.id.startsWith(CARD_RULE_PREFIX) }
    return ResumenRecurrentes(
        // `items` sale de lo mismo que el total, no de `rules`: el acceso «Recurrentes» del Inicio
        // lo lee para decir «libre al mes · N recurrentes», y un conteo que incluyera lo que la
        // cifra de al lado no suma sería la contradicción de siempre, en chiquito. La cuota de un
        // crédito SÍ cuenta como recurrente —entra al total, así que también al rótulo—; el pago
        // de una tarjeta y el crédito de pago único no entran a ninguno de los dos.
        items = (cuentan.map { Recurrente.Regla(it) } + suscripciones).sortedBy { it.dayOfMonth },
        ingresos = cuentan.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        // Las reglas son COP por modelo; las suscripciones entran según lo de arriba.
        gastos = gastosDeReglas.sumOf { it.amount } + gastosDeSuscripciones,
        gastosDeSuscripciones = gastosDeSuscripciones,
        sinConvertir = sinConvertir,
        hayMonedaExtranjera = dolaresEnElTotal,
        hayCobrosAnuales = anualesEnElTotal,
        // Lo que ENTRÓ, y por eso se filtra sobre `gastosDeReglas` y no sobre `rules`.
        cuotasDeCredito = gastosDeReglas
            .filter { it.id.startsWith(CREDIT_RULE_PREFIX) }
            .sumOf { it.amount },
        pagosUnicosFuera = rules.count { it.esPagoUnico },
        minimosDeTarjeta = reglasDeTarjeta.sumOf { it.pagoMinimoCop ?: 0L },
        tarjetasSinMinimo = reglasDeTarjeta.count { it.pagoMinimoCop == null },
    )
}

// ------------------------------------------------- lo que la pantalla dice de los mínimos

/** El rótulo de la deducción, en la tarjeta del «Flujo libre» y en cualquier otra que la muestre. */
const val ETIQUETA_MINIMOS_DE_TARJETA: String = "Mínimos de tarjeta"

/**
 * **De qué está hecha la cifra grande**, dicho debajo de ella.
 *
 * Cambia cuando hay mínimos adentro porque si no la resta no cerraría a la vista: el dueño puede
 * sumar los dos números de abajo y ver que no dan. Una cifra que no cuadra con su propio desglose
 * es la forma más rápida de que deje de creerle a la pantalla.
 */
fun subtituloDelFlujoLibre(cifras: ResumenRecurrentes): String =
    if (cifras.minimosDeTarjeta > 0L) {
        "Ingresos − Gastos recurrentes − Mínimos de tarjeta"
    } else {
        "Ingresos recurrentes − Gastos recurrentes"
    }

/**
 * **El aviso de que la cifra grande no es un hecho**, o `null` si no falta ningún mínimo.
 *
 * Mismo criterio que [ResumenRecurrentes.sinConvertir] y que `pagosUnicosFuera`: un total al que
 * le falta un sumando se dice, no se disimula. La diferencia es el tamaño del sumando — con las
 * cinco tarjetas del dueño sin mínimo cargado, lo que falta son $1.843.014 sobre un disponible de
 * $601.574, o sea que el signo de la respuesta está mal y no solo su magnitud.
 *
 * Dice **dónde se carga**, no solo que falta: un aviso que no se puede accionar se vuelve
 * decorado a la segunda vez que se lee.
 */
fun avisoDeMinimosQueFaltan(cifras: ResumenRecurrentes): String? {
    if (cifras.tarjetasSinMinimo <= 0) return null
    val cuantas = if (cifras.tarjetasSinMinimo == 1) {
        "1 tarjeta con deuda"
    } else {
        "${cifras.tarjetasSinMinimo} tarjetas con deuda"
    }
    return "Falta el pago mínimo de $cuantas: esta cifra es lo más que te podría quedar, no lo " +
        "que te queda. Ese dato está en tu extracto y se carga en Créditos, con el lápiz de la tarjeta."
}

/**
 * **De dónde salió una suscripción** — la única señal de origen que hay, y la que decide dos
 * cosas a la vez: qué dice la fila y qué pasa al tocar «Quitar».
 *
 * Nació como dos propiedades (`laEncontroMovi` / `seActivoSola`) sobre [Recurrente.Suscripcion],
 * porque la pantalla «Recurrentes» —la que el rediseño de 2026-09 disolvió dentro de
 * Movimientos— era la única que las leía. Vuelve como función libre sobre [Subscription], y no
 * como propiedades de la fila, por un motivo concreto: **«Quitar» también necesita esta misma
 * distinción y no tiene la fila en la mano, solo la suscripción**. Antes eso eran dos copias de
 * `startsWith(MANUAL_SUB_PREFIX)` —una para la etiqueta y otra para decidir entre borrar y
 * marcar DISMISSED—, que es exactamente la clase de duplicado que este archivo existe para
 * evitar: si alguna vez discreparan, la fila diría «la encontró Movi» sobre algo que se borra
 * de verdad.
 *
 * La prioridad es **el prefijo primero**, y después el estado. Es lo que hace que la etiqueta y
 * el borrado no puedan contradecirse: lo que se muestra como escrito por el dueño es exactamente
 * lo que «Quitar» borra. En la práctica no hay diferencia con el orden de la pantalla vieja —el
 * alta manual siempre nace [SubStatus.CONFIRMED] (ver `SubscriptionRoutes`) y el detector nunca
 * produce una clave `manual_*` (ver [MANUAL_SUB_PREFIX])—, así que el caso donde los dos órdenes
 * discreparían no lo produce ningún camino de hoy.
 */
enum class OrigenDeSuscripcion {
    /** La escribió el dueño a mano (clave `manual_*`). */
    LA_ESCRIBIO_EL_DUENO,

    /** La encontró el detector y el dueño la confirmó. */
    LA_ENCONTRO_MOVI,

    /**
     * La encontró el detector y quedó activa **sin que nadie la confirmara** — herencia de antes
     * de F39, cuando el barrido activaba solo. Está sumando en «Flujo libre» hoy, así que
     * conviene que se siga notando: es la única de las tres que el dueño nunca aprobó.
     */
    LA_ENCONTRO_MOVI_Y_LA_ACTIVO_SOLA,
}

/** Ver [OrigenDeSuscripcion]. */
fun origenDeSuscripcion(sub: Subscription): OrigenDeSuscripcion = when {
    sub.merchantKey.startsWith(MANUAL_SUB_PREFIX) -> OrigenDeSuscripcion.LA_ESCRIBIO_EL_DUENO
    sub.status == SubStatus.AUTO -> OrigenDeSuscripcion.LA_ENCONTRO_MOVI_Y_LA_ACTIVO_SOLA
    else -> OrigenDeSuscripcion.LA_ENCONTRO_MOVI
}

/**
 * **«Quitar» esta suscripción, ¿es borrarla o marcarla DISMISSED?**
 *
 * Las dos ramas y su porqué, tal cual las razonó la pantalla vieja:
 *
 * - **La escribió el dueño** (`manual_`): se BORRA. Marcarla DISMISSED la dejaba en un limbo —
 *   invisible en la lista, imposible de recuperar desde ninguna pantalla, y todavía chocando
 *   con el alta si volvía a contratar el servicio («Ya tienes una suscripción llamada "Claude"»
 *   sobre algo que no ve). El detector nunca produce esa clave, así que no hay ningún barrido al
 *   que haga falta decirle «esta no».
 * - **La encontró el detector**: sigue siendo [SubStatus.DISMISSED], que ahí sí significa algo —
 *   es el «no me la propongas más» que respeta `SubscriptionSync` en cada re-scan. Borrarla haría
 *   que el próximo barrido la volviera a proponer.
 */
fun quitarBorraLaSuscripcion(sub: Subscription): Boolean =
    origenDeSuscripcion(sub) == OrigenDeSuscripcion.LA_ESCRIBIO_EL_DUENO

/**
 * **Las suscripciones que hoy están activas y sumando**, listas para pintar.
 *
 * Sale de [ResumenRecurrentes.items] y no de un filtro propio sobre la lista cruda, a propósito:
 * así el inventario que se muestra y el total que se muestra encima salen del MISMO reparto —el
 * de [resumenRecurrentes], que además de filtrar AUTO+CONFIRMED reparte uno-a-uno qué fila queda
 * tapada por una regla del dueño ([Recurrente.Suscripcion.yaEsRegla]). Recalcular el filtro acá
 * habría sido una segunda copia de la misma regla, y con dos cobros que se llaman igual y una
 * sola regla las dos copias ni siquiera coincidirían.
 *
 * Viene ya ordenada por día del mes, que es el orden en que [resumenRecurrentes] arma `items`.
 */
fun suscripcionesActivas(resumen: ResumenRecurrentes): List<Recurrente.Suscripcion> =
    resumen.items.filterIsInstance<Recurrente.Suscripcion>()

/**
 * **El nombre de la cuenta que paga esto**, o `null` si no hay nada que decir.
 *
 * Devuelve `null` en los dos casos donde afirmar algo sería inventar: la suscripción no tiene
 * cuenta (ver [Subscription.accountId], donde `null` es una respuesta legítima y no un dato
 * pendiente), o la tiene pero [accountNames] todavía no la conoce — la lista de cuentas se lee
 * aparte y puede llegar tarde, fallar, o no traer una cuenta que se borró.
 *
 * **Los dos casos se tratan igual a propósito.** Un `accountId` que no resuelve NO se pinta como
 * el id crudo («acc-9f3a…», que no le dice nada a nadie) ni como un «cuenta desconocida» que
 * suena a error del dueño: se calla, exactamente como lo hacía la fila de la pantalla vieja de
 * Recurrentes. Una fila que espera medio segundo por la lista de cuentas no debería parpadear un
 * mensaje de falla.
 */
private fun nombreDeLaCuenta(sub: Subscription, accountNames: Map<String, String>): String? =
    sub.accountId?.let { accountNames[it] }

/**
 * La línea de contexto de una suscripción activa: con qué se paga y de dónde salió, o —si el
 * dueño ya tiene una regla con ese nombre— que NO está sumando.
 *
 * El aviso de duplicado va primero porque es lo primero que hay que decir: sin esa línea, el
 * «Flujo libre» de arriba parece no cuadrar con la lista de abajo (ver [resumenRecurrentes], que
 * es quien decide excluirla del total). Y se lleva la línea ENTERA: es la explicación de un
 * número que no cuadra, ya usa dos segmentos, y meterle un tercero la parte en dos renglones
 * justo en la fila donde más importa que se lea de un vistazo. La cuenta es contexto; esa frase
 * es una corrección.
 *
 * ## Por qué la cuenta REEMPLAZA la palabra «Suscripción» en vez de sumarse
 *
 * La línea tiene dos segmentos y sigue teniendo dos: nunca se hace más larga que antes de la Ola
 * 17. El primero era la palabra «Suscripción», que dentro de una sección titulada
 * «Suscripciones activas» —y con su contador al lado— no informa nada; el nombre de la tarjeta
 * que paga el cobro sí, y es justamente el dato que el dueño pidió. Cuando no hay cuenta que
 * mostrar, el rótulo genérico vuelve a ocupar su lugar y la fila se ve igual que siempre: no hay
 * «sin cuenta» ni un hueco, porque a una suscripción que nunca tuvo cuenta no le falta nada.
 *
 * El segmento de origen no se toca — es la marca que distingue lo que encontró Movi de lo que
 * escribió el dueño, y [quitarBorraLaSuscripcion] lee esa misma distinción para decidir si
 * «Quitar» borra o descarta.
 */
fun contextoDeSuscripcionActiva(
    item: Recurrente.Suscripcion,
    accountNames: Map<String, String>,
): String = when {
    item.yaEsRegla -> "Ya lo tienes como recurrente · no se suma dos veces"
    else -> {
        // La cuenta si la sabemos; si no, el rótulo genérico de siempre.
        val cabeza = nombreDeLaCuenta(item.sub, accountNames) ?: "Suscripción"
        when (origenDeSuscripcion(item.sub)) {
            OrigenDeSuscripcion.LA_ENCONTRO_MOVI_Y_LA_ACTIVO_SOLA ->
                "$cabeza · la encontró Movi y la activó sola"
            OrigenDeSuscripcion.LA_ENCONTRO_MOVI -> "$cabeza · la encontró Movi"
            OrigenDeSuscripcion.LA_ESCRIBIO_EL_DUENO -> cabeza
        }
    }
}

/**
 * La línea de contexto de una candidata «detectada · por confirmar»: cuántos meses la vio el
 * detector, qué día cobra y —si se sabe— con qué cuenta.
 *
 * **La cuenta vale más acá que en cualquier otra fila**, y por eso se muestra aunque el card ya
 * esté lleno de datos. Lo que la candidata le pide al dueño es una decisión —¿esto es una
 * suscripción tuya, sí o no?— y «lo cobran en la Nubank» es lo que la vuelve reconocible cuando
 * el nombre normalizado del comercio no alcanza. A diferencia de una activa, además, una
 * candidata casi siempre TIENE cuenta: la copia del evento que la originó.
 *
 * Se calla igual que la fila de una activa cuando no hay cuenta o no se conoce su nombre — entre
 * otras cosas porque el detector resuelve la cuenta con `singleOrNull()`, así que un cobro que
 * apareció en dos tarjetas llega acá sin ninguna, y eso no es un error que haya que anunciar.
 */
fun contextoDeCandidata(sub: Subscription, accountNames: Map<String, String>): String {
    val visto = "Visto ${sub.occurrences} ${if (sub.occurrences == 1) "mes" else "meses"} · día ${sub.dayOfMonth}"
    val cuenta = nombreDeLaCuenta(sub, accountNames)
    return if (cuenta != null) "$visto · $cuenta" else visto
}

/**
 * **El texto del monto de una suscripción, con su periodicidad puesta.**
 *
 * `amount` es el cobro REAL, y para un cobro anual eso significa que la fila dice $369.900 al
 * lado de filas que dicen $47.900 y cobran todos los meses. Sin las dos palabras del final, esa
 * columna miente por doce sin que nada lo delate: es exactamente el mismo número, en la misma
 * tipografía, en la misma posición. Por eso la periodicidad va PEGADA al monto y no en la línea
 * de contexto de abajo — lo que hay que desambiguar es la cifra, no la fila.
 *
 * Lo que NO va acá es el prorrateado: la fila muestra lo que el dueño puede buscar en el
 * extracto. La cifra del mes es una cuenta de Movi y se dice aparte, ver [notaDeProrrateo].
 *
 * En SU moneda, sin convertir — solo el total de arriba pasa por la TRM, y lo dice.
 *
 * Existe como función y no como un `if` en cada renderer por lo mismo que [textoDelMonto]: hoy
 * la leen la fila de «Suscripciones activas» y la candidata «por confirmar», y un `if` que falte
 * en el tercero es un cobro anual mostrado como mensual.
 *
 * @param conSigno ¿la fila pone `−` delante? El inventario de activas sí (son gastos), la
 *   candidata no.
 */
fun textoDelMontoDeSuscripcion(sub: Subscription, conSigno: Boolean = false): String {
    val monto = (if (conSigno) "−" else "") + formatMoney(sub.amount, sub.currency)
    return when (sub.periodicidad) {
        PeriodicidadDeCobro.MENSUAL -> monto
        PeriodicidadDeCobro.ANUAL -> "$monto al año"
    }
}

/**
 * **Cuánto de un cobro anual entra al total de este mes**, o `null` si no hay nada que aclarar.
 *
 * Es la línea que cierra la distancia entre «$369.900 al año» en la fila y los $30.825 que esa
 * fila aporta al «Flujo libre» de arriba. Sin ella, el total no es la suma de lo que se ve y no
 * hay forma de saber por qué — la misma confusión que este archivo ya documenta haber peleado
 * con las filas excluidas por duplicadas.
 *
 * Devuelve `null` en tres casos, y los tres son «no hay nada que explicar» o «esto sería
 * mentira»:
 * - **Un cobro mensual**: el monto de la fila YA es lo que aporta.
 * - **Una fila que no suma** ([Recurrente.Suscripcion.yaEsRegla]): decirle cuánto aporta a un
 *   total al que no entra sería contradecir, en la línea de al lado, el «no se suma dos veces»
 *   que pone [contextoDeSuscripcionActiva].
 * - **Una fila que no se pudo convertir a pesos**: un cobro anual en dólares sin tasa queda
 *   FUERA del total, y el card de arriba ya lo dice («Este total no incluye 1 cobro en otra
 *   moneda»). Prometer que entra, tres líneas más abajo, sería la misma pantalla diciendo dos
 *   cosas opuestas sobre la misma fila. Por eso hace falta [usdToCop] acá: sin la tasa, esta
 *   función no puede saber si su frase es cierta.
 *
 * El monto va en la moneda de la suscripción, no en pesos: lo que se explica es la división por
 * doce, y la conversión a pesos ya la explica el card de arriba por su cuenta. Mezclar las dos
 * transformaciones en una sola línea haría que ninguna de las dos se entienda.
 */
fun notaDeProrrateo(item: Recurrente.Suscripcion, usdToCop: Double): String? = when {
    item.sub.periodicidad != PeriodicidadDeCobro.ANUAL -> null
    item.yaEsRegla -> null
    copDeSuscripcion(item.sub, usdToCop) == null -> null
    else -> "Entra al total como " +
        formatMoney(item.sub.montoMensualEquivalente(), item.sub.currency) + " al mes"
}

/**
 * Lo que va en «Próximos»: lo que vence PRONTO, no todo lo que existe.
 *
 * `GET /api/payments/upcoming` devuelve una entrada por regla (mapea 1:1, ver `upcomingPayments`),
 * así que pintarlas todas convertía la sección en una copia de «Por día del mes»: el mismo
 * «Arriendo» dos veces en la misma pantalla, una debajo de la otra y justo debajo del número
 * «Flujo libre». No se contaba dos veces, pero invitaba a creer que sí — y en una pantalla cuyo
 * trabajo es explicar un total, esa sospecha es el defecto.
 *
 * El corte es el del barrido de avisos (`leadDays`, ver DueDates.kt): [PaymentStatus.UPCOMING]
 * significa «todavía falta». Lo que queda —vencido, vence hoy, vence pronto— sí merece salir dos
 * veces: una como alerta y otra en el inventario de abajo.
 */
fun proximosQueUrgen(upcoming: List<UpcomingPayment>): List<UpcomingPayment> =
    upcoming.filter { it.status != PaymentStatus.UPCOMING }

/**
 * ¿Alguien pidió que le recuerden algo? Es la pregunta que decide el aviso ámbar de
 * «tus recordatorios no te van a llegar».
 *
 * Mira lo PEDIDO y no lo que existe, porque el aviso anuncia una promesa rota: sin promesa no
 * hay nada que anunciar. Solo un GASTO con `remindMe` entra al barrido (`selectDueForReminder`
 * ignora los ingresos), así que con un recurrente de ingreso —que ni siquiera ofrece la casilla—
 * el aviso salía prometiendo incumplir algo que nadie había pedido.
 */
fun hayRecordatoriosPedidos(upcoming: List<UpcomingPayment>): Boolean =
    upcoming.any { it.rule.type == TransactionType.EXPENSE && it.rule.remindMe }

/**
 * Qué mandar en `RecurringRule.accountId` cuando se guarda la hoja de un recurrente.
 *
 * **Existe para que `""` no pueda significar dos cosas.** El campo es de tres estados en el wire
 * (ver el KDoc de [RecurringRule.accountId] y el PUT de `ReminderRoutes.kt`):
 *   · `null`  → «no lo toques» — nadie habló de cuentas en este guardado
 *   · `""`    → «quítala» — el dueño eligió «Sin cuenta» a propósito
 *   · un id   → esa cuenta
 *
 * La hoja mandaba `accountId ?: ""`, o sea que **«no pude corroborar esta cuenta» y «el dueño la
 * quitó» viajaban con el mismo valor**. Y no corroborar era el caso NORMAL en Android: la lista
 * de cuentas salía de la DB local, donde una cuenta nacida en el server nunca estaba (ver
 * `LocalRepository.getAccounts`), así que la hoja borraba la elección del dueño y después el
 * `""` le pedía al server que la quitara. Corregirle el monto a un recurrente desde el teléfono
 * le borraba la cuenta que había puesto desde la web: la protección de tres estados que ese
 * endpoint documenta, derrotada desde el otro lado.
 *
 * Ahora los tres estados de la hoja se mapean uno a uno con los tres del wire, y **el único
 * camino que produce `""` es que el dueño abra el selector y toque «Sin cuenta»**. Ninguna falla
 * de lectura puede producir ese valor: si la lista de cuentas no llegó, [cuentaEnLaHoja] conserva
 * lo que la regla ya tenía y eso es lo que se manda. Por eso esta capa protege aunque la de
 * abajo (traer las cuentas del server) falle o se rompa después.
 *
 * En un POST no hay nada que preservar —`null` y `""` significan lo mismo, sin cuenta— así que la
 * misma función sirve para las dos puertas.
 *
 * @param cuentaEnLaHoja el id que muestra el campo, o `null` si el campo dice «Sin cuenta».
 * @param elDuenoEligioSinCuenta ¿ese `null` salió de que el dueño tocó «Sin cuenta» en el
 *   selector? Si no, el `null` significa «acá no se habló de cuentas».
 */
fun cuentaParaElWire(cuentaEnLaHoja: String?, elDuenoEligioSinCuenta: Boolean): String? = when {
    cuentaEnLaHoja != null -> cuentaEnLaHoja
    elDuenoEligioSinCuenta -> ""
    else -> null
}

/**
 * **El texto del monto de un recurrente, en cualquiera de las tres pantallas que lo pintan.**
 *
 * Existe como función —y no como un `if` copiado en cada renderer— porque el defecto que arregla
 * es exactamente el de un `if` que faltó en uno de ellos. El monto de la regla sintética de una
 * tarjeta es su SALDO, no su cuota (ver [RecurringRule.montoEsSaldo]): el Inicio, Recurrentes y
 * el correo lo dicen bien, y el **push** —el canal que suena— lo seguía anunciando como el pago
 * del mes: «Pago tarjeta AMEX 9208 — $27.501.150 (vence hoy)».
 *
 * Con un solo lugar que lo decide, el `if` no puede faltar en el cuarto renderer que aparezca. Lo
 * que cada pantalla sigue eligiendo por su cuenta es el ESTILO —el saldo va en gris y más chico,
 * porque no es una cifra que vaya a salir de la cuenta—: eso es Compose y no cabe acá.
 *
 * (El push vive en `:server`, que no puede importar `:shared`; ahí la misma decisión se toma en
 * `buildPushPayload` con su propio formato de miles, y su test la fija.)
 *
 * @param conSigno ¿la fila pone `+`/`−` delante? El inventario de Recurrentes sí, la lista de
 *   «Próximos pagos» del Inicio no. Un saldo nunca lleva signo: no es un movimiento.
 */
fun textoDelMonto(rule: RecurringRule, conSigno: Boolean = false): String = when {
    rule.montoEsSaldo -> "saldo ${formatCOP(rule.amount)}"
    conSigno -> "${if (rule.type == TransactionType.INCOME) "+" else "−"}${formatCOP(rule.amount)}"
    else -> formatCOP(rule.amount)
}
