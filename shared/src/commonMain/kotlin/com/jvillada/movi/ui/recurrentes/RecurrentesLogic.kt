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
 * un falso positivo escondería una fila real del total, y equivocarse hacia abajo en el dinero
 * es peor que mostrar un duplicado marcado. Solo colapsa las diferencias tipográficas.
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
 * Cuánto pesa una suscripción en pesos. Replica exactamente lo que hace el server en
 * `resultFor` (mismo redondeo por fila), así que sumar un subconjunto acá da el mismo número
 * que `monthlyTotalCop` da para el conjunto entero.
 *
 * Con [usdToCop] en 0 —server viejo, o ninguna activa en dólares— una fila en dólares aporta 0
 * en vez de un número inventado: el server tampoco la convirtió.
 */
fun copDeSuscripcion(sub: Subscription, usdToCop: Double): Long =
    if (sub.currency == "COP") sub.amount else (sub.amount * usdToCop).roundToLong()

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
 */
data class ResumenRecurrentes(
    val items: List<Recurrente>,
    val ingresos: Long,
    val gastos: Long,
    val duplicadas: Int,
    val hayMonedaExtranjera: Boolean,
) {
    val flujoLibre: Long get() = ingresos - gastos
}

fun resumenRecurrentes(rules: List<RecurringRule>, subs: SubscriptionsResult): ResumenRecurrentes {
    val clavesDeReglas = rules.map { claveDeNombre(it.name) }.toSet()
    val activas = subs.subscriptions.filter {
        it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED
    }
    val suscripciones = activas.map {
        Recurrente.Suscripcion(it, yaEsRegla = claveDeNombre(it.displayName) in clavesDeReglas)
    }
    return ResumenRecurrentes(
        items = (rules.map { Recurrente.Regla(it) } + suscripciones).sortedBy { it.dayOfMonth },
        ingresos = rules.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        // Las reglas son COP por modelo; de las suscripciones solo entran las que no están ya
        // contadas como regla, cada una convertida con la misma tasa que usó el server.
        gastos = rules.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } +
            suscripciones.filterNot { it.yaEsRegla }.sumOf { copDeSuscripcion(it.sub, subs.usdToCop) },
        duplicadas = suscripciones.count { it.yaEsRegla },
        hayMonedaExtranjera = activas.any { it.currency != "COP" },
    )
}
