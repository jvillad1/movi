package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import kotlin.math.roundToLong

/**
 * Las reglas puras de la pantalla Recurrentes (Ola 8), sin Compose, para poder testearlas —
 * y, sobre todo, para que la cifra del acceso «Recurrentes» del Inicio y el «Flujo libre» de
 * la pantalla salgan de la MISMA función y no puedan discrepar. Ese desacuerdo entre dos
 * pantallas que dicen contar lo mismo ya había pasado antes (Créditos vs. Inicio en la Ola 4).
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
 */
fun claveDeNombre(nombre: String): String {
    val sinAcentos = nombre.map { c ->
        when (c.lowercaseChar()) {
            'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'; 'ñ' -> 'n'
            else -> c.lowercaseChar()
        }
    }
    return sinAcentos.filter { it.isLetterOrDigit() }.joinToString("")
}

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
 */
fun copDeSuscripcion(sub: Subscription, usdToCop: Double): Long? = when {
    sub.currency == "COP" -> sub.amount
    sub.currency == "USD" && usdToCop > 0.0 -> (sub.amount * usdToCop).roundToLong()
    else -> null
}

/** Una fila de la lista única: lo que escribió el dueño y lo que cobra una suscripción. */
sealed class Recurrente {
    abstract val dayOfMonth: Int

    data class Regla(val rule: RecurringRule) : Recurrente() {
        override val dayOfMonth get() = rule.dayOfMonth
    }

    /**
     * @param yaEsRegla el dueño YA tiene una regla recurrente con este mismo nombre. La fila se
     *   muestra igual (existe de verdad, el cobro está ahí) pero no vuelve a sumar al total.
     */
    data class Suscripcion(val sub: Subscription, val yaEsRegla: Boolean) : Recurrente() {
        override val dayOfMonth get() = sub.dayOfMonth
        /** Ver [MANUAL_SUB_PREFIX]: sin ese prefijo, la encontró el detector. */
        val laEncontroMovi get() = !sub.merchantKey.startsWith(MANUAL_SUB_PREFIX)
        /** Herencia de antes de F39: se activó sola, sin que el dueño la confirmara. */
        val seActivoSola get() = sub.status == SubStatus.AUTO
    }
}

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
 */
data class ResumenRecurrentes(
    val items: List<Recurrente>,
    val ingresos: Long,
    val gastos: Long,
    val sinConvertir: Int,
    val hayMonedaExtranjera: Boolean,
) {
    val flujoLibre: Long get() = ingresos - gastos
}

fun resumenRecurrentes(rules: List<RecurringRule>, subs: SubscriptionsResult): ResumenRecurrentes {
    // Reparto uno-a-uno: cada regla puede tapar UNA suscripción, no todas las que se llamen
    // igual. Con dos cobros «Netflix» distintos y una sola regla, excluir los dos borraría un
    // gasto real del total; así se excluye uno y el otro sigue contando.
    val reglasDisponibles = rules.map { claveDeNombre(it.name) }.toMutableList()
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
    if (!huboExclusiones) {
        // Nada que restar: el total que ya calculó el server es exacto — y esta rama es además
        // la que salva al cliente nuevo contra un server viejo que todavía no manda la tasa
        // (el APK se instala a mano y el server se despliega aparte, así que ese desfase pasa).
        // Ahí `monthlyTotalCop` sí trae los dólares convertidos; solo faltaba la tasa para
        // poder DESGLOSARLO, y sin exclusiones no hace falta desglosar nada.
        gastosDeSuscripciones = subs.monthlyTotalCop
        sinConvertir = 0
        dolaresEnElTotal = activas.any { it.currency != "COP" }
    } else {
        // Hay que sumar fila por fila para poder saltear las duplicadas, y eso sí necesita la
        // tasa. Lo que no se pueda convertir queda afuera Y contado, para que la pantalla avise.
        val aportes = suscripciones.filterNot { it.yaEsRegla }
            .map { it to copDeSuscripcion(it.sub, subs.usdToCop) }
        gastosDeSuscripciones = aportes.mapNotNull { it.second }.sum()
        sinConvertir = aportes.count { it.second == null }
        dolaresEnElTotal = aportes.any { it.second != null && it.first.sub.currency != "COP" }
    }

    return ResumenRecurrentes(
        items = (rules.map { Recurrente.Regla(it) } + suscripciones).sortedBy { it.dayOfMonth },
        ingresos = rules.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        // Las reglas son COP por modelo; las suscripciones entran según lo de arriba.
        gastos = rules.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } +
            gastosDeSuscripciones,
        sinConvertir = sinConvertir,
        hayMonedaExtranjera = dolaresEnElTotal,
    )
}
