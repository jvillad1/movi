package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
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
 *
 * **La implementación se mudó a `:core`** ([claveComparableDeNombre]) sin cambiar ni una regla:
 * ahora el server también la necesita, para proponer qué movimiento fue la ocurrencia de un
 * recurrente. Dos copias que normalizaran distinto harían que el server propusiera
 * emparejamientos que esta pantalla no sabría explicar. Este alias se queda para no reescribir
 * los llamados que ya existen.
 */
fun claveDeNombre(nombre: String): String = claveComparableDeNombre(nombre)

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
