package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.esDeTuPlata
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.alertaDeCapturaEnInicio
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.ui.recurrentes.resumenRecurrentes
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.isDebtAccount
import com.jvillada.movi.ui.components.signedMoney
import com.jvillada.movi.ui.components.saldoEnSuMoneda
import com.jvillada.movi.ui.credits.totalDebtCop
import com.jvillada.movi.ui.cuadre.cuentasSinCuadrar
import com.jvillada.movi.ui.cuadre.textoDelAvisoDeCuadre
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.serialization.Serializable

/**
 * Todo lo que el Inicio carga del server, junto, para que el renderer SDUI reciba un solo
 * valor y no doce parámetros. Cada campo arranca vacío y se va llenando a medida que llega
 * cada respuesta; una sección que no tiene todavía sus datos simplemente no se pinta (ver
 * [visibleSections]) o muestra la cifra en blanco — nunca un número inventado.
 *
 * `@Serializable` por la instantánea del Inicio (ver [InstantaneaDelInicio]): la última carga que
 * salió bien se guarda en el aparato para pintarla al abrir. Anotar la clase entera y no una copia
 * «instantánea» con sus propios campos es a propósito: un campo nuevo que no sea serializable no
 * compila, en vez de quedarse afuera de la instantánea sin que nadie lo note. Un campo nuevo
 * **con valor por defecto**, como todos los de acá: una instantánea escrita por la versión anterior
 * no lo trae.
 */
@Serializable
data class DashboardData(
    val summary: FinanceSummary? = null,
    /**
     * `null` = todavía no contestó; lista vacía = contestó y no hay cuentas.
     *
     * Misma disciplina que [upcoming], y por el mismo motivo, encontrado esta vez en el peor
     * lugar. En la **web** [DashboardDataCache] vive en memoria: recargar la página lo borra, así
     * que cada arranque en frío pintaba durante segundos «Tu plata $0», «Sin cuentas aún» y la
     * guía de Primeros pasos con «Crea tu primera cuenta» SIN tildar — a un dueño con cinco
     * créditos y 23 movimientos, mientras abajo ya se veían sus propias cuotas. No era una cifra
     * vieja: era la app afirmando que no tiene nada.
     *
     * `emptyList()` por defecto hacía indistinguible «no llegó» de «no hay», que es justo la
     * distinción que esta pantalla necesita antes de opinar sobre la plata de alguien. Es la
     * regla que la pantalla de Cuentas ya seguía (`cuentasLeidas`); el Inicio no.
     */
    val accounts: List<Account>? = null,
    /** `null` = todavía no contestó. Ver [accounts]: la guía tilda un paso con esto. */
    val credits: List<CreditSummary>? = null,
    /**
     * F20: las tarjetas también son deuda — el acceso «Créditos» las suma junto a los préstamos.
     * `null` = todavía no contestó, por el mismo motivo que [credits].
     */
    val cards: List<CardSummary>? = null,
    /**
     * `null` = todavía no llegó (o su carga falló); lista vacía = llegó y no hay nada.
     *
     * La distinción no era necesaria mientras cada cifra salía de UNA sola fuente: si algo no
     * llegaba, su acceso no se pintaba y listo. Dejó de serlo cuando el acceso «Recurrentes»
     * pasó a COMPONER dos fuentes (reglas + suscripciones): con `emptyList()` por defecto, que
     * se cayera `/api/payments/upcoming` era indistinguible de «este usuario no tiene reglas», y
     * el Inicio mostraba un flujo libre calculado solo con las suscripciones — un número
     * plausible, equivocado y sin nada que avisara. Ver `quickLinkFigure("recurrentes")`.
     */
    val upcoming: List<UpcomingPayment>? = null,
    /**
     * `null` = todavía no llegó (o su lectura falló), igual que [accounts], [credits] y [upcoming].
     * Con `emptyList()` por defecto, una lectura caída pintaba «Sin presupuestos» a quien sí tiene.
     */
    val budgets: List<Budget>? = null,
    /** Gasto del período en curso por categoría (ver [spentByCategoryForPeriod]). `null` = no llegó. */
    val spentByCategory: Map<String, Long>? = null,
    val cardCandidates: Int = 0,
    val pendingSms: Int = 0,
    /**
     * Qué se sabe de la captura de SMS: cuántos mensajes del banco llegaron alguna vez y cuándo
     * llegó el último (ver [CapturaDeSms] en `:core`). `null` = el resumen todavía no contestó.
     *
     * La distinción importa más que en otros campos: `CapturaDeSms()` vacío significa «nunca
     * llegó nada», que es justamente lo que dispara la alerta. Con un default no nulo, un
     * arranque en frío sin señal pintaría «Movi nunca ha recibido un mensaje de tu banco» a
     * alguien cuya captura funciona perfecto — la misma clase de afirmación sin datos que
     * [accounts] tuvo que dejar de hacer.
     */
    val captura: CapturaDeSms? = null,
    /** El dueño pidió no ver el aviso de captura en el Inicio (`users.sms_alert_muted`). */
    val capturaSilenciada: Boolean = false,
    /** `null` = no llegó; ver [budgets]. */
    val goals: List<Goal>? = null,
    val subscriptions: SubscriptionsResult? = null,
    /**
     * Los sellos de «ya ocurrió» de este período. Los pone el dueño en Movimientos; el Inicio solo
     * los lee, para poder tildar el checklist (ver `checklistDelPeriodo`).
     */
    val ocurrencias: List<OccurrenceState>? = null,
    /** El corte del dueño y sus inicios propios. Sin perfil, el mes de calendario. */
    val ajustesDePeriodo: PeriodSettings = PeriodSettings(),
    /** En qué período estamos, según [ajustesDePeriodo]. `null` mientras no se sepa la fecha. */
    val periodoActual: PeriodoFinanciero? = null,
    /**
     * El gasto variable de cada día del período (ver `DashboardSummary.gastoVariablePorDia`).
     * `null` = no llegó, o el server es anterior al campo: la tarjeta «Disponible» no se pinta.
     */
    val gastoVariablePorDia: Map<String, Long>? = null,
    /**
     * Lo que había en «Tu plata» al empezar el período y lo que entró (ver [PlataDelDisponible]).
     * `null` = no llegó o el server es anterior: la tarjeta «Disponible» vuelve a «ingresos menos
     * fijos».
     */
    val plataDelDisponible: PlataDelDisponible? = null,
    /**
     * El patrimonio ya partido que manda `/api/dashboard/summary` (entrega A). `null` = no llegó o
     * el server es anterior. La tarjeta del patrimonio lo usa solo si las cuentas no llegaron:
     * ver `patrimonioDelInicio`, que explica por qué prefiere `accounts`.
     */
    val patrimonio: Patrimonio? = null,
) {
    val hasAccount: Boolean get() = !accounts.isNullOrEmpty()
    /**
     * Si el Inicio ya sabe lo suficiente para **afirmar vacío**. Las dos lecturas que sostienen
     * la guía de Primeros pasos son las mismas dos que avisan con snackbar cuando fallan
     * (cuentas y resumen); hasta que contesten, la guía no se pinta. Nótese que una lectura que
     * FALLÓ tampoco habilita: `accounts` sigue en `null`, y eso es correcto — un error de red no
     * es evidencia de que el dueño no tenga cuentas.
     */
    val puedeAfirmarVacio: Boolean get() =
        accounts != null && summary != null && upcoming != null && credits != null && cards != null
    /** F20: una tarjeta registrada también tilda el paso de Créditos — es una deuda como cualquier préstamo. */
    val hasCredit: Boolean get() = !credits.isNullOrEmpty() || !cards.isNullOrEmpty()
    /**
     * La apertura de cuenta no cuenta (F54) y un traspaso cuenta una sola vez aunque sean dos
     * eventos — ver `FinanceSummary.eventCount` y `movementCount` en `:core`.
     */
    val hasMovement: Boolean get() = (summary?.eventCount ?: 0) > 0
    /**
     * F6: "Anota tus gastos recurrentes" se tilda con una regla recurrente REAL. `getUpcomingPayments`
     * también trae las cuotas sintéticas de los créditos (id con [CREDIT_RULE_PREFIX]) y, desde
     * F20, los pagos de tarjeta (id con [CARD_RULE_PREFIX]); esas tildan el paso de Créditos,
     * no este.
     */
    val hasRecurringRule: Boolean get() = upcoming.orEmpty().any {
        !it.rule.id.startsWith(CREDIT_RULE_PREFIX) && !it.rule.id.startsWith(CARD_RULE_PREFIX)
    }
    /**
     * Si la guía de Primeros pasos todavía tiene algo por tildar — la MISMA cuenta que decide si
     * el Inicio la pinta (`showGuide` en `DashboardScreen`). Ola B, tarea 7 la sacó de ahí para
     * que el mosaico de Más pueda usarla también, y ofrecer la ficha «Primeros pasos» solo
     * mientras haga falta: una copia a mano de esta condición en dos lugares es exactamente cómo
     * se desalinean silenciosamente el día que uno de los dos cambie.
     */
    val guiaIncompleta: Boolean get() = puedeAfirmarVacio && !(hasAccount && hasMovement)
}

// ── Las dos cifras del Inicio ──────────────────────────────────────────────────────

/**
 * Lo que TIENES y lo que VALES, separadas y con nombre propio.
 *
 * Nacen de un reporte del dueño: cargó su primer crédito y dijo «lo que hizo fue descontarme
 * de la cuenta todo el saldo del crédito». La cuenta no se tocó —la deuda vive en su propia
 * cuenta `LOAN` y nunca entró al flujo de caja— pero el número grande del Inicio pasó de
 * +$20.308.659 a −$28.710.542 de un día para el otro, sin nada que lo explicara. Que un dato
 * sea correcto no lo hace legible: el Inicio mostraba el **patrimonio** bajo el rótulo
 * «Balance neto», y con cinco créditos por cargar (~$1.505 millones) la primera cifra de cada
 * mañana iba a ser −$1.493 millones.
 *
 * Por eso el Inicio muestra ahora [tuPlata] arriba y [patrimonio] debajo, rotulados distinto.
 *
 * **Qué cuenta como «tu plata»: Dinero + Inversión, o sea toda cuenta que no sea deuda.**
 * Tres razones, en orden de peso:
 * 1. Es plata suya. Un CDT o un fondo es plata guardada, no plata ajena; esconderla del número
 *    grande obligaría a sumar dos cifras de dos pantallas para saber cuánto tiene.
 * 2. Es exactamente lo que muestran la fila «Cuentas» de EXPLORA y el renglón «Tu plata» de la
 *    pantalla de Cuentas — y no por casualidad: los tres salen de ESTA función. Dejar el hero en
 *    solo-Dinero crearía un tercer número que no coincide con ninguno de los dos — el desacuerdo
 *    que la Ola 4 tuvo que arreglar entre Créditos y el Inicio. (Durante una ola las otras dos
 *    superficies llamaban a `assetsDebtsNet` por su cuenta, y en cuanto apareció
 *    [Account.condicionadaA] volvieron a divergir: el hero decía $31,6M y la fila de al lado
 *    $137,6M. Por eso el cálculo vive en un solo lugar y las tres lo consumen.)
 * 3. La distinción que de verdad hizo daño acá no es líquido vs. invertido, es **tuyo vs.
 *    debido**. Esa es la que separan estas dos cifras.
 *
 * Lo invertido no se pierde de vista: cuando hay algo en Inversión, el hero lo desglosa
 * ([disponible] y [invertido]) en una línea secundaria.
 */
data class HeroBalance(
    /**
     * **Lo que puedes usar**: toda cuenta que no sea deuda y que no esté condicionada.
     *
     * Antes era «toda cuenta que no sea deuda», sin más, y sumaba la pensión voluntaria del dueño:
     * decía $137.625.167 cuando él solo podía disponer de $31.625.167. Ver
     * [Account.condicionadaA] y [condicionado].
     */
    val tuPlata: Long,
    /** La parte de [tuPlata] en el grupo Dinero — efectivo, corriente, ahorros. */
    val disponible: Long,
    /** La parte de [tuPlata] en el grupo Inversión. */
    val invertido: Long,
    /** Lo que debes: tarjetas y préstamos, en COP (estimado cuando hay saldo en otra moneda). */
    val deudas: Long,
    /**
     * Plata suya que **solo se puede usar para algo**: la pensión voluntaria, una AFC, cesantías.
     * Fuera de [tuPlata], dentro de [patrimonio].
     */
    val condicionado: Long,
    /**
     * Para qué. Con una sola cuenta condicionada es su condición («Vivienda»); con varias
     * distintas, `null` y el renglón lo dice en genérico — inventar una condición común sería
     * decir algo que ninguna cuenta dice.
     */
    val condicionadoA: String?,
    /**
     * Lo que valen los **bienes**: la casa, el carro (ver `Bien` en `:core`). Fuera de [tuPlata] y
     * fuera de [condicionado] —no es plata, con o sin destino—; dentro de [patrimonio].
     *
     * Default en 0 para que un `HeroBalance` armado a mano en una prueba vieja siga diciendo lo
     * mismo que antes de que existieran.
     */
    val bienes: Long = 0L,
    /**
     * Activos **completos** (la plata condicionada y los bienes incluidos) − [deudas]. Puede ser
     * negativo, y con cinco créditos hipotecarios lo será.
     *
     * La plata condicionada cuenta acá y no en [tuPlata] a propósito: es suya —por eso suma al
     * patrimonio— pero no la puede gastar, así que anunciarla como disponible sería el error más
     * caro que puede cometer esta pantalla.
     */
    val patrimonio: Long,
) {
    /**
     * ¿Hay algo que decir sobre el patrimonio? Solo se esconde cuando [patrimonio] y [tuPlata]
     * son el MISMO número: ahí la línea repetiría la cifra grande.
     *
     * **Y desde que existe [condicionado] eso ya no es «sin deudas».** La condición era
     * `deudas != 0L`, escrita cuando el patrimonio solo podía separarse de «tu plata» por una
     * deuda. Alguien cuyo único producto es una pensión voluntaria —$0 en cuentas, $106M
     * condicionados, sin créditos— veía «Tu plata $0», «Además $106,0M solo para Vivienda», y
     * **el patrimonio no aparecía en ningún lado**: la plata que sí tiene no se decía en ninguna
     * cifra de la pantalla.
     *
     * El `!= 0` de las deudas se queda: una tarjeta **sobrepagada** las deja en negativo, y ahí
     * el patrimonio es MAYOR que «tu plata» — un dato que vale la pena mostrar y que un `> 0`
     * escondía justo cuando la línea dejaba de ser redundante. (Ver [patrimonioExplicacion],
     * que cambia «menos … en deudas» por «más … a favor en créditos» en ese caso.)
     */
    val muestraPatrimonio: Boolean get() = deudas != 0L || condicionado > 0L || bienes > 0L
    /** Sin nada invertido no hay nada que desglosar: el hero no pinta la línea del desglose. */
    val hasInvestments: Boolean get() = invertido != 0L
}

/**
 * El rótulo de la cifra grande del Inicio, **fijo en el binario y no leído de la definición SDUI**.
 *
 * Es lo único de la tarjeta que no se puede editar desde el Editor de pantallas, y la razón es
 * la asimetría del despliegue: la fila de `screen_definitions` llega a TODOS los clientes en el
 * instante del deploy, pero el renderer viaja en el binario. La PWA está a salvo (el mismo
 * deploy sirve el wasm y la fila); un APK ya instalado no.
 *
 * Si el rótulo viajara en el schema, cambiar la semilla a «Tu plata» habría hecho que el APK 1.7
 * —que sigue pintando el patrimonio— titulara **«Tu plata −$1.492.710.542»**: exactamente la
 * lectura que esta rama existe para evitar, ahora afirmada por el rótulo. Con el rótulo en el
 * binario no hay ventana en ninguna de las dos direcciones: el cliente viejo dice «Balance neto»
 * sobre el patrimonio y el nuevo dice «Tu plata» sobre los activos, y **cada binario rotula lo
 * que él mismo calcula**.
 *
 * Es el mismo trato que ya tenían los tres sub-rótulos de abajo («Ingresos», «Gastos», «Flujo
 * del mes»): están cableados en el renderer, y por eso cambiar «Egresos» por «Gastos» en la Ola
 * 8 no necesitó subir la generación del seed.
 *
 * El costo asumido: el hero no se puede renombrar desde el Editor. Se verificó contra producción
 * (2026-08-28) que el dueño nunca editó el Inicio, y el resto de la definición —orden de
 * secciones, accesos, títulos de las demás— sigue siendo editable como antes.
 */
const val HERO_BALANCE_TITLE = "Tu plata"

/**
 * El tag de la tarjeta entera del hero (Task 7): mide su alto cargando (esqueleto) contra su alto
 * cargado (veredicto + barra), sin depender de qué texto exacto cae al final de cada estado — el
 * uno tiene bloques que pulsan y ningún texto real, el otro tiene el monto de «Salió» con un
 * formato que cambia con los datos.
 */
const val TAG_TARJETA_DEL_HERO: String = "tarjeta-del-hero"

/**
 * Los cuatro tags de las piezas del esqueleto del hero (Task 7): además del alto total —que con
 * `@GraphicsMode(NATIVE)` (ver el KDoc de [Esqueleto][com.jvillada.movi.ui.components.BloqueEsqueleto]
 * sobre el modo `LEGACY` de Robolectric) sí se puede comparar con fidelidad, pero solo prueba la
 * SUMA— esto prueba lo que la suma sola no alcanza a probar: que las CUATRO piezas están, ni una
 * de menos. Cada una puede probarse por separado sin acoplarse al texto real que reemplazan, y es
 * más barato que montar el hero entero dos veces.
 */
const val TAG_ESQUELETO_CIFRA_DEL_HERO: String = "esqueleto-cifra-del-hero"
const val TAG_ESQUELETO_VEREDICTO_DEL_HERO: String = "esqueleto-veredicto-del-hero"
const val TAG_ESQUELETO_BARRA_DEL_HERO: String = "esqueleto-barra-del-hero"
const val TAG_ESQUELETO_FILA_DEL_HERO: String = "esqueleto-fila-del-hero"

/**
 * **¿Hay una carga del Inicio EN VUELO ahora mismo?** Lo provee `DashboardScreen`, con su propio
 * `loading`, alrededor de `SduiRenderer` — Task 7, fix round 1.
 *
 * `HeroDeUnVistazo` y `PreguntaleAMoviSection` solo reciben `data`, no `loading` (así las armó el
 * brief original), y con solo `data` no alcanza para decidir el esqueleto: `data.accounts == null`
 * es tan cierto en la primera carga (con la respuesta en camino) como después de una carga en frío
 * SIN RED que ya se rindió. Sin esta señal, esa segunda situación dejaba el esqueleto pulsando
 * para siempre — «cargando» y «error» no pueden verse a la vez, y el segundo ya tiene su snackbar
 * «Reintentar». Con la señal, una carga que terminó (falló o no) cae al estado de siempre para ese
 * dato: el guion en la cifra, o directamente nada.
 *
 * `compositionLocalOf`, no `staticCompositionLocalOf`: este valor SÍ cambia dentro de la vida del
 * Inicio (arranca en `true` o `false` según si hay algo cacheado, y pasa a `false` cuando la carga
 * termina), así que hace falta que Compose rastree quién lo lee. El default `false` es el lado
 * seguro para una vista previa o una prueba que monta una sección sola: sin la señal de que algo
 * viene en camino, no hay esqueleto, se ve el estado de siempre.
 */
val LocalCargandoElInicio: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * [HERO_BALANCE_TITLE], ignorando a propósito `section.title`.
 *
 * Existe como función —en vez de usar la constante directo en el renderer— para que el test
 * pueda fijar la decisión: alguien que "arregle" esto de vuelta a `section.title ?: …` reabre
 * la ventana de desalineación descrita arriba, y eso tiene que romper una prueba, no descubrirse
 * en el teléfono del dueño.
 */
fun heroBalanceTitle(@Suppress("UNUSED_PARAMETER") section: ScreenSection): String = HERO_BALANCE_TITLE

/**
 * La línea que explica de dónde sale el patrimonio — la resta escrita, que es lo que faltaba el
 * día del reporte del dueño.
 *
 * **Tiene que nombrar los tres términos, no dos.** Desde que existe [HeroBalance.condicionado] la
 * cuenta es `tuPlata + condicionado − deudas`, y la línea decía «Tu plata menos $1.505,1M en
 * deudas» pegada a la cifra: con los números del dueño faltaban $106.000.000 para que la resta
 * cerrara. Una explicación que no da el número que explica es peor que ninguna — es la única
 * línea de la pantalla cuyo trabajo es que el lector pueda verificar la cifra de arriba.
 *
 * Dos redacciones para las deudas porque [HeroBalance.deudas] puede ser negativo (una tarjeta
 * sobrepagada): «menos … en deudas» mentiría con un signo menos delante del monto. Y la parte
 * condicionada se nombra con su condición cuando hay una sola, igual que el renglón del hero.
 */
fun patrimonioExplicacion(balance: HeroBalance): String {
    val condicionado = when {
        balance.condicionado <= 0L -> ""
        balance.condicionadoA != null ->
            " más ${formatMoneyCompact(balance.condicionado)} solo para ${balance.condicionadoA}"
        else -> " más ${formatMoneyCompact(balance.condicionado)} de uso condicionado"
    }
    // **Los bienes, con nombre.** Sin esta parte, con la casa cargada la línea decía «Tu plata
    // más $116,2M…, menos $2.191,0M en deudas» debajo de un patrimonio de −$662M: faltaban
    // $1.411,9M para que la resta cerrara, que es el único trabajo de esta línea.
    val bienes = if (balance.bienes > 0L) {
        (if (condicionado.isEmpty()) " " else ", ") + "más ${formatMoneyCompact(balance.bienes)} en bienes"
    } else ""
    val sumandos = condicionado + bienes
    // Con una parte condicionada (o bienes) ya dicha, la coma separa los sumandos: sin ella
    // («…solo para Vivienda menos $1.505,1M en deudas») las dos frases se leen como una sola.
    val separador = if (sumandos.isEmpty()) " " else ", "
    val deudas = when {
        balance.deudas > 0L -> "${separador}menos ${formatMoneyCompact(balance.deudas)} en deudas"
        balance.deudas < 0L -> "${separador}más ${formatMoneyCompact(-balance.deudas)} a favor en créditos"
        // Sin deudas no se escribe «menos $0»: con algo condicionado la línea ya dice todo lo que
        // separa el patrimonio de «tu plata», y sin nada condicionado no se pinta (ver
        // [HeroBalance.muestraPatrimonio]).
        else -> ""
    }
    return "Tu plata$sumandos$deudas"
}

/**
 * Cuentas que **sí puede usar** el dueño: ni deuda ni condicionada a algo puntual (ver
 * [Account.condicionadaA]). [heroBalance] y [cuentasDelHero] llaman a ESTA función en vez de
 * repetir el filtro cada una por su cuenta — dos copias del mismo predicado ya se
 * desalinearon dos veces en este proyecto (Créditos vs. Inicio en la Ola 4, `quickLinkFigure`
 * vs. `assetsDebtsNet` después) y la tercera no iba a ser distinta.
 *
 * El predicado mismo vive en `:core` ([esDeTuPlata], en `PlataDelPeriodo.kt`) porque el server
 * lo necesita para la tarjeta «Disponible»: lo que tenías al empezar el período se suma sobre
 * estas mismas cuentas.
 */
private fun cuentasLibres(accounts: List<Account>): List<Account> =
    accounts.filter { it.esDeTuPlata() }

/**
 * Deriva [HeroBalance] de las cuentas. **Es [patrimonioDe] (`:core`) con la forma que pinta el
 * Inicio**, no una suma propia: la regla de qué cuenta cae en qué balde —plata, condicionada,
 * bien, deuda— vive en un solo lugar y la usan también el server (`/api/dashboard/summary`) y el
 * contexto de Movi AI. Así el hero, la fila «Cuentas» del Inicio, el «Patrimonio neto» de la
 * pantalla de Cuentas y lo que contesta el asistente no pueden dar cuatro números distintos.
 */
fun heroBalance(accounts: List<Account>): HeroBalance {
    val p = patrimonioDe(accounts)
    return HeroBalance(
        tuPlata = p.tuPlata,
        disponible = p.disponible,
        invertido = p.invertido,
        condicionado = p.condicionado,
        condicionadoA = p.condicionadoA,
        bienes = p.bienes,
        deudas = p.deudas,
        patrimonio = p.neto,
    )
}

/** Una fila del desglose por cuenta del hero: el nombre y su saldo, ya formateado con signo y moneda. */
data class CuentaHero(val nombre: String, val monto: String)

/**
 * Las cuentas que componen [HeroBalance.tuPlata], una por una — lo que pidió el dueño viendo
 * «Tu plata» sumar todas las cuentas en un solo número: *«realmente me gustaría ver no el
 * total sino el disponible en cada cuenta allí listado»*. Reemplaza la línea que antes
 * agregaba por GRUPO («Dinero $X · Inversión $Y»); la cifra grande de arriba no cambia.
 *
 * **Llama a [cuentasLibres], la MISMA función que usa [heroBalance]** — no una copia del
 * predicado (ver su KDoc). El orden con el que separa disponible de invertido (`!= INVERSION`
 * primero, `== INVERSION` después) es también el de la pantalla de Cuentas: Dinero antes que
 * Inversión (ver `AccountsScreen`).
 *
 * `null` = todavía no contestó — igual que [DashboardData.accounts], para no afirmar una lista
 * vacía cuando en realidad no se sabe todavía.
 */
fun cuentasDelHero(accounts: List<Account>?): List<CuentaHero>? {
    if (accounts == null) return null
    val libres = cuentasLibres(accounts)
    val disponibles = libres.filter { it.type.group != AccountGroup.INVERSION }
    val invertidas = libres.filter { it.type.group == AccountGroup.INVERSION }
    // Cada cuenta en SU moneda (una cuenta en dólares decía «US$0»: era su parte en pesos con el
    // rótulo de dólares). Ver [saldoEnSuMoneda].
    return (disponibles + invertidas).map {
        val (monto, moneda) = saldoEnSuMoneda(it)
        CuentaHero(it.name, signedMoney(monto, moneda))
    }
}

// ── Próximos pagos ─────────────────────────────────────────────────────────────────

/**
 * Pagos (egresos) que vencen dentro de [days] días, incluidos los ya vencidos que el server
 * todavía considera de este periodo (daysUntil negativo): son los más urgentes y van primero.
 * Los ingresos recurrentes (sueldo) no son "pagos" y quedan fuera.
 */
fun upcomingPaymentsWithin(payments: List<UpcomingPayment>, days: Int = 7, max: Int = 3): List<UpcomingPayment> =
    payments
        .filter { it.rule.type == TransactionType.EXPENSE && it.daysUntil <= days }
        .sortedBy { it.daysUntil }
        .take(max)

fun dueLabel(daysUntil: Int): String = when {
    daysUntil < -1 -> "Vencido hace ${-daysUntil} días"
    daysUntil == -1 -> "Vencido ayer"
    daysUntil == 0 -> "Vence hoy"
    daysUntil == 1 -> "Vence mañana"
    else -> "Vence en $daysUntil días"
}

// ── Alertas ────────────────────────────────────────────────────────────────────────

data class DashboardAlert(val text: String, val target: Screen)

/**
 * Los presupuestos de verdad superados.
 *
 * Llamaba a su propia comparación (`gastado >= límite`) con un comentario que decía «misma regla
 * que Presupuestos». Cuando esa pantalla dejó de contar el empate como exceso, el comentario pasó
 * a ser mentira y las dos se contradijeron: el dueño veía «Presupuesto de Mercado superado» en el
 * Inicio y «Sin margen · gastaste justo el límite» al entrar. Ahora las dos llaman a
 * [estadoDePresupuesto], en `:core`, que es donde vive una regla sobre su plata.
 */
fun overBudgetCategories(budgets: List<Budget>?, spentByCategory: Map<String, Long>?): List<String> =
    // Sin alguna de las dos respuestas no se puede afirmar que un presupuesto se pasó.
    if (budgets == null || spentByCategory == null) emptyList() else budgets.filter { estadoDePresupuesto(spentByCategory[it.category] ?: 0L, it.monthlyLimit).estaSuperado }
        .map { it.category }

/**
 * Cada alerta es una fila tocable que lleva a donde se resuelve. Sin nada pendiente devuelve
 * vacío y la sección entera no se pinta — nada de "Sin alertas por ahora".
 *
 * @param captura qué se sabe de la captura de SMS; `null` = el resumen no contestó todavía y no
 *   se afirma nada. Ver [alertaDeCapturaEnInicio] para por qué esa fila solo aparece en el caso
 *   «nunca llegó nada» y cómo deja de aparecer.
 */
fun dashboardAlerts(
    overBudget: List<String>,
    cardCandidates: Int,
    pendingSms: Int,
    captura: CapturaDeSms? = null,
    capturaSilenciada: Boolean = false,
): List<DashboardAlert> = buildList {
    when (overBudget.size) {
        0 -> Unit
        // Ola C: Presupuestos es un segmento de Plan — la alerta abre Plan con ese segmento puesto.
        1 -> add(DashboardAlert("Presupuesto de ${overBudget[0]} superado", Screen.Plan(SEGMENTO_PRESUPUESTOS)))
        else -> add(DashboardAlert("${overBudget.size} presupuestos superados", Screen.Plan(SEGMENTO_PRESUPUESTOS)))
    }
    // Ola C: las dos llevan a la misma bandeja, «Por revisar», que junta lo que entró solo. Antes
    // cada una abría un lugar distinto (Movimientos, Mensajes del banco) y ninguno tenía lo otro.
    if (cardCandidates > 0) {
        add(DashboardAlert(plural(cardCandidates, "pago de tarjeta", "pagos de tarjeta") + " por confirmar", Screen.PorRevisar))
    }
    if (pendingSms > 0) {
        add(DashboardAlert(plural(pendingSms, "mensaje del banco", "mensajes del banco") + " por confirmar", Screen.PorRevisar))
    }
    // Va última y nunca convive con la de arriba: si hay algo por confirmar, es que llegó algo.
    // El dueño pasó semanas anotando a mano creyendo que la captura corría, y no se enteró
    // porque el único indicador vivía en una pantalla a la que no tenía motivo para entrar. La
    // fila es el motivo. Lleva a «Captura del banco», donde se arregla y donde se silencia.
    captura?.let { c ->
        alertaDeCapturaEnInicio(c, capturaSilenciada)?.let { add(DashboardAlert(it, Screen.CapturaDelBanco)) }
    }
}

// ── Accesos con cifra ──────────────────────────────────────────────────────────────

/** Cifra (y línea secundaria) de un acceso; `value` null = no hay nada que mostrar todavía. */
data class LinkFigure(val value: String? = null, val sub: String? = null, val isAlert: Boolean = false)

/**
 * La cifra que acompaña a cada acceso según su destino de navegación — lo que antes mostraba
 * "Análisis" (F40). Un destino sin cifra conocida (Movi AI, Perfil…) devuelve todo en null
 * y la fila se pinta solo con el título.
 */
fun quickLinkFigure(target: String, data: DashboardData): LinkFigure = when (target) {
    "accounts" -> {
        // El conteo nombra EXACTAMENTE el conjunto que suma la cifra: las cuentas que no son
        // deuda. Mismo predicado que usa [assetsDebtsNet] para sus «activos» —no una copia que
        // pueda separarse— y mismo conjunto que lista la pantalla de destino, que desde F61
        // muestra solo los grupos Dinero e Inversión (las deudas viven en Créditos).
        //
        // Contar `data.accounts.size` era correcto en la Ola 4, cuando Cuentas listaba TODO.
        // F61 (Ola 7) dejó las deudas afuera de esa pantalla y el conteo quedó hablando de otra
        // cosa que la cifra: con un ahorro y cinco créditos, la fila decía «$12.383.363 ·
        // 6 cuentas» y al tocarla aparecía «DINERO · 1 / INVERSIÓN · 0». Las deudas no se
        // pierden de vista: la fila «Créditos», justo debajo, las cuenta y las suma.
        // Sin respuesta todavía no se afirma nada: ni la cifra ni el «Sin cuentas aún».
        //
        // **La cifra sale de [heroBalance], no de [assetsDebtsNet].** Esta línea sumaba TODO lo
        // que no fuera deuda, así que en la misma pantalla —y a dos dedos de distancia— el hero
        // decía «Tu plata $31.625.167» y esta fila decía «$137.625.167»: los $106M de la pensión
        // voluntaria que el hero acababa de sacar. Es la tercera vez en este proyecto que dos
        // superficies calculan la misma regla por su cuenta y terminan diciendo cosas distintas
        // (Créditos vs. Inicio en la Ola 4, los presupuestos en la Ola 16), así que no se copia
        // el filtro: las dos consumen la MISMA función.
        val cuentas = data.accounts
        val hero = heroBalance(cuentas.orEmpty())
        // Sin los bienes: la casa no es una cuenta que «Tu plata» sume, y Cuentas la lista aparte.
        val propias = cuentas.orEmpty().count { !isDebtAccount(it.type) && !it.esBien }
        // Solo deudas cargadas (el estado de quien arranca por sus créditos) es «sin cuentas»,
        // no «0 cuentas» al lado de un $0: es lo que dicen los dos grupos vacíos de la pantalla
        // de destino, y un cero grande en el Inicio se lee como que algo se perdió. Sigue la
        // regla de toda esta función: sin nada que contar, no se pinta cifra.
        if (cuentas == null) LinkFigure()
        else if (propias == 0) LinkFigure(sub = "Sin cuentas aún")
        else LinkFigure(
            formatCOP(hero.tuPlata),
            // El conteo dice cuántas cuentas lista la pantalla de destino; cuando alguna está
            // condicionada, la cifra ya no es la suma de todas ellas, y la línea lo dice en vez
            // de dejar la diferencia sin explicar.
            if (hero.condicionado > 0L) {
                "${plural(propias, "cuenta", "cuentas")} · ${formatMoneyCompact(hero.condicionado)} condicionados"
            } else {
                plural(propias, "cuenta", "cuentas")
            },
        )
    }
    "credits" -> {
        // F20: préstamos + tarjetas, con la MISMA función que usa la pantalla de Créditos para
        // su «Deuda total» — hallazgo de la Ola 4: la pantalla y el Inicio daban números
        // distintos (acá se sumaba solo LOAN).
        // Basta con que UNA de las dos no haya contestado para no poder decir «Sin créditos»:
        // el conteo suma las dos, así que con media respuesta el cero no significa nada.
        val sinRespuesta = data.credits == null || data.cards == null
        val count = data.credits.orEmpty().size + data.cards.orEmpty().size
        if (sinRespuesta) LinkFigure()
        else if (count == 0) LinkFigure(sub = "Sin créditos")
        else LinkFigure(formatCOP(totalDebtCop(data.credits.orEmpty(), data.cards.orEmpty())), plural(count, "crédito", "créditos"))
    }
    "budgets" -> {
        val budgets = data.budgets
        val gastado = data.spentByCategory
        // Mismo criterio que «credits» y «accounts»: sin respuesta no se afirma vacío. Y sin el
        // gasto, «$0 de $X» sería una cifra inventada, así que tampoco se pinta.
        if (budgets == null) LinkFigure()
        else if (budgets.isEmpty()) LinkFigure(sub = "Sin presupuestos")
        else if (gastado == null) LinkFigure()
        else {
            val limit = budgets.sumOf { it.monthlyLimit }
            val spent = budgets.sumOf { gastado[it.category] ?: 0L }
            // **La tercera regla, y estaba 70 líneas debajo de la segunda.** Esta línea comparaba
            // `spent >= limit` sobre los TOTALES, así que fallaba de dos maneras a la vez:
            //
            // - Con un solo presupuesto justo en el límite ($2.000.000 de $2.000.000) pintaba el
            //   acceso en alerta, mientras Presupuestos lo mostraba verde. La misma contradicción
            //   que este PR vino a matar, movida del panel de alertas a la tarjeta de al lado.
            // - Y al revés: sumar todo esconde el caso real. Con Mercado en $3.000.000 de
            //   $2.000.000 y Salidas en $100.000 de $2.000.000, el total da por debajo y el acceso
            //   se veía tranquilo mientras la alerta decía «Presupuesto de Mercado superado».
            //
            // La alerta se decide POR CATEGORÍA, que es como se vive: un presupuesto excedido no
            // se compensa con otro que sobró.
            LinkFigure(
                formatCOP(spent),
                "de ${formatCOP(limit)} este mes",
                isAlert = overBudgetCategories(budgets, gastado).isNotEmpty(),
            )
        }
    }
    "goals" -> {
        val goals = data.goals
        if (goals == null) LinkFigure()
        else if (goals.isEmpty()) LinkFigure(sub = "Sin metas")
        else LinkFigure(formatCOP(goals.sumOf { it.saved }), plural(goals.size, "meta", "metas"))
    }
    "investments" -> {
        // F50: cuentas tipo INVESTMENT, no el modelo de "posiciones" (holdings) que el server
        // siempre devolvía vacío. F61: el target abre Cuentas (grupo Inversión).
        // Mismo criterio que la rama «accounts» de acá arriba: sin respuesta no se afirma
        // «Sin inversiones». Se veía menos porque este acceso no está en el layout por defecto
        // —hay que agregarlo desde el Editor de pantallas— pero la afirmación era igual de falsa.
        val cuentasInv = data.accounts
        // Un bien viaja como INVESTMENT (ver `Bien` en :core) y no es una inversión.
        val investmentAccounts = cuentasInv.orEmpty().filter { it.type == AccountType.INVESTMENT && !it.esBien }
        if (cuentasInv == null) LinkFigure()
        else if (investmentAccounts.isEmpty()) LinkFigure(sub = "Sin inversiones")
        else LinkFigure(formatCOP(investmentAccounts.sumOf { it.balance }), plural(investmentAccounts.size, "cuenta", "cuentas"))
    }
    "recurrentes" -> {
        // Ola 8: el acceso muestra exactamente el número grande de la pantalla de destino —
        // «Flujo libre»— calculado con la MISMA función pura que usa Recurrentes, para que
        // tocar la tarjeta no lleve a una cifra distinta de la que se tocó. (La Ola 4 ya había
        // encontrado ese desacuerdo entre Créditos y el Inicio.)
        //
        // `upcoming` trae UNA entrada por regla (ver `upcomingPayments`: mapea 1:1), así que
        // sirve de lista de reglas sin pedir nada nuevo — el Inicio sigue liviano.
        //
        // **Se manda entero, sintéticas incluidas.** Este acceso descartaba por prefijo de id las
        // cuotas de créditos y los pagos de tarjeta antes de llamar; desde que las cuotas SÍ
        // entran al «Flujo libre» (pedido del dueño: son lo más grande que le sale al mes), ese
        // filtro de acá habría dejado al Inicio mostrando un número $5.445.772 más alto que la
        // pantalla que se abre al tocarlo — el desacuerdo entre dos cifras que dicen contar lo
        // mismo que `resumenRecurrentes` existe justamente para hacer imposible. Quién entra y
        // quién no lo decide una sola función, allá adentro: `cuentaComoCompromisoMensual`.
        //
        // Esta cifra COMPONE dos fuentes, así que exige las dos. Con una sola —la otra se cayó,
        // o todavía no llegó— el número saldría plausible y equivocado: sin las reglas, un
        // sueldo de +$5.000.000 desaparece y «libre al mes» queda en −$44.900, en rojo, contra
        // los $2.955.100 que muestra la pantalla de destino. Sin las dos no se pinta cifra
        // (título solo), que es la regla de toda esta función: nunca un número inventado.
        val reglas = data.upcoming?.map { it.rule }
        val subs = data.subscriptions
        if (reglas == null || subs == null) LinkFigure()
        else {
            val resumen = resumenRecurrentes(reglas, subs)
            if (resumen.items.isEmpty()) LinkFigure(sub = "Sin recurrentes")
            else LinkFigure(
                // La MISMA cifra grande que la pantalla de destino, que desde los mínimos de
                // tarjeta es `disponible` y no `flujoLibre`: mostrar acá el número de antes
                // dejaría al Inicio $1.843.014 por encima de lo que se abre al tocarlo, que es el
                // desacuerdo exacto que esta función existe para hacer imposible.
                formatCOP(resumen.disponible),
                // Y cuando falta un mínimo, el rótulo deja de contar recurrentes y dice qué le
                // falta a la cifra. Es el único renglón que hay para explicarla, y «te faltan
                // datos» es más útil que «14 recurrentes» sobre un número que puede tener el
                // signo cambiado. Ver [avisoDeMinimosQueFaltan], que dice lo mismo con espacio.
                sub = if (resumen.tarjetasSinMinimo == 1) {
                    "libre al mes · falta 1 mínimo de tarjeta"
                } else if (resumen.tarjetasSinMinimo > 1) {
                    "libre al mes · faltan ${resumen.tarjetasSinMinimo} mínimos de tarjeta"
                } else {
                    "libre al mes · " + plural(resumen.items.size, "recurrente", "recurrentes")
                },
                isAlert = resumen.disponible < 0,
            )
        }
    }
    // Se queda para los Inicios ya guardados que todavía traen el acceso viejo: el target sigue
    // siendo válido y abre Movimientos con el chip «Recurrentes» (ver SduiRenderer.screenForTarget).
    "subscriptions" -> {
        val subs = data.subscriptions
        // Solo las activas (AUTO/CONFIRMED) — es lo que suma monthlyTotalCop y lo que el chip
        // «Recurrentes» cuenta como activas. Las candidatas y las descartadas no son suscripciones
        // todavía (o ya no): contarlas acá daba «$0 · 4 suscripciones al mes» con cero activas.
        val active = subs?.subscriptions?.count { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED } ?: 0
        val candidates = subs?.subscriptions?.count { it.status == SubStatus.CANDIDATE } ?: 0
        when {
            subs == null -> LinkFigure()
            active == 0 && candidates == 0 -> LinkFigure(sub = "Sin suscripciones")
            active == 0 -> LinkFigure(sub = plural(candidates, "por confirmar", "por confirmar"))
            else -> LinkFigure(formatCOP(subs.monthlyTotalCop), plural(active, "suscripción", "suscripciones") + " al mes")
        }
    }
    else -> LinkFigure()
}

private fun plural(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

// ── F5: panel de notificaciones ────────────────────────────────────────────────────

/** Una fila del panel de la campana: el texto y a dónde lleva tocarla. */
data class NotificationRow(val text: String, val target: Screen)

/**
 * Todo lo que la campana muestra — una vista DERIVADA de los datos que el Inicio ya carga,
 * sin modelo de notificaciones persistente ni "marcar leído". Combina los pagos que vencen
 * pronto (cada uno a su propio destino, Recurrentes o Créditos según de qué regla venga) con
 * las mismas alertas que ya calcula [dashboardAlerts] — candidatos de pago de tarjeta, SMS
 * pendientes y presupuestos superados —, así el punto rojo y el panel nunca dicen algo que la
 * campana no pueda resolver.
 */
fun notificationRows(data: DashboardData): List<NotificationRow> = buildList {
    upcomingPaymentsWithin(data.upcoming.orEmpty()).forEach { p ->
        // F20: las cuotas de crédito y los pagos de tarjeta son sintéticos (UpcomingPayment
        // generado por el server, no una regla que viva en Recurrentes) — se resuelven en
        // Créditos, no en Recurrentes.
        // Ola C: una regla se resuelve en Plan · Pagos del mes — ahí está su «¿ya ocurrió?». La
        // campana habla de UN pago, y caer en la lista completa de movimientos no responde nada.
        val target = if (p.rule.id.startsWith(CREDIT_RULE_PREFIX) || p.rule.id.startsWith(CARD_RULE_PREFIX)) {
            Screen.Credits
        } else {
            Screen.Plan(SEGMENTO_PAGOS)
        }
        add(NotificationRow("${p.rule.name} · ${dueLabel(p.daysUntil)}", target))
    }
    dashboardAlerts(
        overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms,
        data.captura, data.capturaSilenciada,
    ).forEach { add(NotificationRow(it.text, it.target)) }
}

// ── Secciones visibles ─────────────────────────────────────────────────────────────

/**
 * Secciones de la definición que tienen algo que mostrar con estos datos. Próximos pagos y
 * Alertas desaparecen del todo cuando están vacías (así pidió el dueño: nada de cajas vacías
 * ocupando lugar); un bloque de accesos sin tarjetas tampoco se pinta.
 */
fun visibleSections(def: ScreenDefinition, data: DashboardData): List<ScreenSection> {
    val renderizables = renderableSections(def)
    // Generación 8: «Pregúntale a Movi» va arriba y el BANNER de Movi AI se queda al final de la
    // lista solo para los APK anteriores, que no conocen el tipo nuevo (ver el KDoc de
    // `DASHBOARD_LAYOUT_VERSION`). Este cliente sí lo conoce: pintar los dos serían dos puertas al
    // mismo chat, una arriba y otra abajo.
    val hayPreguntale = renderizables.any { it.type == "PREGUNTALE_A_MOVI" }
    return renderizables.filter { section ->
        when (section.type) {
            "UPCOMING_PAYMENTS" -> upcomingPaymentsWithin(data.upcoming.orEmpty()).isNotEmpty()
            // «Para revisar» se pinta con lo mismo que antes eran las alertas, más lo que el
            // checklist sabe de vencidos. Ver `cosasParaRevisar`: sin nada que sugerir, no ocupa
            // lugar.
            "ALERTS" -> cosasParaRevisarDe(data).isNotEmpty()
            "CHECKLIST_DEL_PERIODO" -> checklistDelPeriodoDe(data).isNotEmpty()
            "GASTO_POR_CATEGORIA" -> data.spentByCategory.orEmpty().any { it.value > 0 }
            // Solo con todo lo que la cuenta necesita ya leído, y con ingresos que medir. Ver
            // `disponibleDelInicio`: sin datos no se afirma un disponible.
            "DISPONIBLE_DEL_PERIODO" -> disponibleDelInicio(data) != null
            // Siempre: sin datos salen las preguntas de respaldo, y el campo para escribir sirve igual.
            "PREGUNTALE_A_MOVI" -> true
            // Sin cuentas leídas no se afirma un patrimonio; sin nada que tener ni deber, no hay barra.
            "PATRIMONIO" -> patrimonioDelInicio(data) != null
            "BANNER" -> !(hayPreguntale && esElBannerDeMoviAi(section))
            "QUICK_LINKS_WITH_TOTALS", "LINK_LIST", "CARD_ROW", "CARD_LIST" -> section.cards.isNotEmpty()
            else -> true
        }
    }
}

/**
 * ¿Es el aviso que lleva a Movi AI? Un BANNER del Editor que lleve a otro lado (o a ninguno) se
 * sigue pintando: lo que se esconde es la puerta repetida, no cualquier aviso.
 */
internal fun esElBannerDeMoviAi(section: ScreenSection): Boolean =
    section.cards.firstOrNull()?.action?.let { it.type == "NAVIGATE" && it.target == "aichat" } == true


/**
 * Gasto por categoría del **período** [ventana], en vez del mes de calendario.
 *
 * Solo egresos en COP que cuentan como flujo, y la pertenencia se decide por la **ventana del
 * período**, no por un prefijo de fecha («2026-08»): el prefijo no sirve cuando el período cruza
 * dos meses de calendario, que es justo lo que pasa con cualquier corte que no sea el día 1.
 *
 * Había una hermana, `spentByCategoryForMonth`, que decidía por ese prefijo. Ninguna pantalla la
 * usaba ya —solo una prueba—, y se borró: una función del mes de calendario que compila y está a
 * mano es la forma más fácil de reintroducir el desacuerdo entre Inicio y Presupuestos que el
 * período vino a cerrar.
 *
 * El filtro va por `timestamp` contra la ventana, que es exactamente lo que hace el server
 * (`currentPeriodWindow`). Las dos mitades tienen que coincidir o Inicio y Presupuestos vuelven a
 * decir cifras distintas del mismo presupuesto.
 *
 * Lo que espera en «Por confirmar» no suma ([cuentaEnGastosEIngresos]), igual que en el chip
 * «Gastos» y en `monthCashFlow` del server.
 */
fun spentByCategoryForPeriod(days: List<EventDay>, ventana: LongRange): Map<String, Long> =
    days.flatMap { it.items }
        .filter { it.timestamp in ventana }
        .filter { it.type == TransactionType.EXPENSE && cuentaEnGastosEIngresos(it) && it.currency == "COP" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

/**
 * **Lo que el Inicio recomienda mirar hoy**, armado con lo que ya cargó la pantalla.
 *
 * Existe para que la sección y la regla que decide si la sección se pinta usen exactamente la misma
 * cuenta: estaban escritas dos veces, y la de acá ya se había quedado sin el gasto sin categoría.
 */
internal fun cosasParaRevisarDe(
    data: DashboardData,
    // El reloj entra por parámetro para que la lista siga siendo una función de sus datos: el
    // aviso de cuadre depende del tiempo transcurrido, y una prueba no puede esperar 45 días.
    ahora: Long = Clock.System.now().toEpochMilliseconds(),
): List<CosaParaRevisar> = cosasParaRevisar(
    checklist = checklistDelPeriodoDe(data),
    categorias = categoriasDelPeriodo(data.spentByCategory.orEmpty(), data.budgets.orEmpty()),
    // Generación 8: si el hero ya da su veredicto, «Vas gastando más de lo que entró» sería la misma
    // noticia dicha dos veces en la pantalla —arriba con el número, abajo sin él—. Se vio a ojo en la
    // web con los dos a la vista en escritorio. Sin veredicto (sin resumen) la regla ya no se
    // dispararía de todos modos: el flujo sería cero.
    flujoDelPeriodo = if (veredictoDelInicio(data) != null) 0L
    else (data.summary?.ingresos ?: 0L) - (data.summary?.egresos ?: 0L),
    smsPorConfirmar = data.pendingSms,
    candidatosAPagoDeTarjeta = data.cardCandidates,
    gastoSinCategoria = gastoSinCategoriaDe(data.spentByCategory.orEmpty()),
    // `data.accounts` en null = las cuentas todavía no contestaron, y entonces no se afirma que
    // haya ninguna sin cuadrar. Misma disciplina que el resto de este archivo.
    avisoDeCuadre = textoDelAvisoDeCuadre(cuentasSinCuadrar(data.accounts.orEmpty(), ahora)),
)

/**
 * El checklist del período de [data], o vacío mientras no se sepa en qué período estamos.
 *
 * Está acá y no en `ResumenDelPeriodo.kt` porque es el puente entre el `DashboardData` y esa
 * lógica pura: el archivo de lógica no conoce al Inicio, y así sigue.
 */
internal fun checklistDelPeriodoDe(data: DashboardData): List<PagoDelPeriodo> {
    val periodo = data.periodoActual ?: return emptyList()
    return checklistDelPeriodo(
        upcoming = data.upcoming.orEmpty(),
        ocurrencias = data.ocurrencias.orEmpty(),
        periodo = periodo,
        settings = data.ajustesDePeriodo,
    )
}

/**
 * La tarjeta «Disponible» de [data], o `null` si falta algo para afirmarla (ver
 * [disponibleDelPeriodo]).
 *
 * Pide las cinco lecturas que la cuenta usa —el período, el resumen, los vencimientos, las
 * ocurrencias y el gasto variable—, y no se conforma con menos: sin las ocurrencias todo el
 * checklist parece pendiente, y un sueldo ya cobrado se sumaría dos veces como «por recibir».
 *
 * Los fijos salen de [checklistDelPeriodoDe], la MISMA lista que pinta «Falta por pagar».
 */
internal fun disponibleDelInicio(
    data: DashboardData,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
): DisponibleDelPeriodo? {
    if (!alcanzaParaElDisponible(data)) return null
    val periodo = data.periodoActual ?: return null
    val summary = data.summary ?: return null
    val gasto = data.gastoVariablePorDia ?: return null
    return disponibleDelPeriodo(
        ingresosRecibidos = summary.ingresos,
        checklist = checklistDelPeriodoDe(data),
        gastoVariablePorDia = gasto,
        inicio = inicioDelPeriodo(periodo, data.ajustesDePeriodo),
        finExclusivo = inicioDelPeriodo(periodoSiguiente(periodo), data.ajustesDePeriodo),
        hoy = hoy,
        plata = data.plataDelDisponible,
    )
}

/**
 * ¿Llegaron las cinco lecturas de las que sale la tarjeta «Disponible»? (ver [disponibleDelInicio]).
 *
 * Aparte porque «no hay disponible» quiere decir dos cosas que la pestaña Plan tiene que distinguir:
 * que **falta un dato** —la carga no terminó o se cayó, y ahí no se afirma nada— o que los datos
 * están y **no hay nada honesto que decir** (sin ingresos ni plata, ver [disponibleDelPeriodo]). El
 * Inicio no pinta la tarjeta en ninguno de los dos casos; Plan, que la tiene como protagonista, dice
 * cuál de los dos es.
 */
internal fun alcanzaParaElDisponible(data: DashboardData): Boolean =
    data.periodoActual != null && data.summary != null && data.gastoVariablePorDia != null &&
        data.upcoming != null && data.ocurrencias != null

/**
 * Lo que trae `GET /api/dashboard/summary`, puesto en [DashboardData].
 *
 * Ola C: lo leen el Inicio y la pestaña Plan (que necesita de ahí el gasto variable y la plata del
 * disponible). Una sola traducción de la respuesta: si mañana el server manda un campo más para la
 * tarjeta, no puede llegar a una pantalla y no a la otra.
 */
internal fun DashboardData.conResumenDelInicio(s: DashboardSummary): DashboardData = copy(
    spentByCategory = s.spentByCategory,
    cardCandidates = s.cardPaymentCandidates,
    pendingSms = s.pendingSms,
    // Lo que se sabe de la captura de SMS. Viene en esta MISMA respuesta —no es una llamada
    // nueva— y es lo que le permite al Inicio decir «Movi nunca ha recibido un mensaje de tu
    // banco». Ver CapturaDeSms en :core: la captura estuvo muda semanas y el único lugar que
    // podía delatarlo era una pantalla de Android que el dueño no abre.
    captura = CapturaDeSms(total = s.smsTotal, ultimo = s.smsLastAt),
    capturaSilenciada = s.smsAlertMuted,
    // La tarjeta «Disponible». Misma respuesta, ninguna llamada nueva.
    gastoVariablePorDia = s.gastoVariablePorDia,
    // Lo que tenías al empezar el período y lo que entró. Un server viejo no lo manda y la
    // tarjeta vuelve a «ingresos menos fijos».
    plataDelDisponible = plataDelDisponibleDe(s),
    // El patrimonio ya partido (entrega A). La tarjeta lo usa solo si las cuentas no llegaron:
    // ver `patrimonioDelInicio`.
    patrimonio = s.patrimonio,
)

/**
 * El período del dueño —su día de corte y los inicios que movió a mano— sacado de su perfil, y el
 * período en curso a [ahora] según esos ajustes. Sin esto el Inicio (y Plan) hablarían del mes de
 * calendario, que es justo lo que dejó de hacer el resto de la app.
 */
internal fun DashboardData.conElPerfil(perfil: UserProfile, ahora: Long): DashboardData {
    val ajustes = PeriodSettings(perfil.periodCutoffDay, perfil.periodStarts)
    return copy(ajustesDePeriodo = ajustes, periodoActual = periodoDe(ahora, ajustes))
}
