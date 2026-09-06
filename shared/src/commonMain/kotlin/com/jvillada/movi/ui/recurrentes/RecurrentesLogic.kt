package com.jvillada.movi.ui.recurrentes

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
 */
data class ResumenRecurrentes(
    val items: List<Recurrente>,
    val ingresos: Long,
    val gastos: Long,
    val sinConvertir: Int,
    val hayMonedaExtranjera: Boolean,
    val hayCobrosAnuales: Boolean = false,
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

    return ResumenRecurrentes(
        items = (rules.map { Recurrente.Regla(it) } + suscripciones).sortedBy { it.dayOfMonth },
        ingresos = rules.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        // Las reglas son COP por modelo; las suscripciones entran según lo de arriba.
        gastos = rules.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } +
            gastosDeSuscripciones,
        sinConvertir = sinConvertir,
        hayMonedaExtranjera = dolaresEnElTotal,
        hayCobrosAnuales = anualesEnElTotal,
    )
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
 * La línea de contexto de una suscripción activa: de dónde salió, o —si el dueño ya tiene una
 * regla con ese nombre— que NO está sumando.
 *
 * El aviso de duplicado va primero porque es lo primero que hay que decir: sin esa línea, el
 * «Flujo libre» de arriba parece no cuadrar con la lista de abajo (ver [resumenRecurrentes], que
 * es quien decide excluirla del total).
 */
fun contextoDeSuscripcionActiva(item: Recurrente.Suscripcion): String = when {
    item.yaEsRegla -> "Ya lo tienes como recurrente · no se suma dos veces"
    else -> when (origenDeSuscripcion(item.sub)) {
        OrigenDeSuscripcion.LA_ENCONTRO_MOVI_Y_LA_ACTIVO_SOLA ->
            "Suscripción · la encontró Movi y la activó sola"
        OrigenDeSuscripcion.LA_ENCONTRO_MOVI -> "Suscripción · la encontró Movi"
        OrigenDeSuscripcion.LA_ESCRIBIO_EL_DUENO -> "Suscripción"
    }
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
