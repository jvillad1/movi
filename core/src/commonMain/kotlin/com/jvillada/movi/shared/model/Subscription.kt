package com.jvillada.movi.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
enum class SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }

/**
 * F38: prefijo de [Subscription.merchantKey] que marca un alta MANUAL — la escribió el dueño,
 * no la encontró el detector.
 *
 * `normalizeMerchant` (SubscriptionDetector.kt) deriva su clave de la descripción del EVENTO
 * bancario y nunca antepone este prefijo, así que una fila `manual_*` queda estructuralmente
 * fuera de lo que el detector puede generar o re-escribir: un re-scan no la toca ni la duplica.
 *
 * Vive en `:core` (y no privado en el server, como nació) porque desde la Ola 8 el cliente
 * también lo necesita: en la lista única de Recurrentes, una suscripción SIN este prefijo lleva
 * la marca «la encontró Movi», y una con él no — es la única señal de origen que hay, porque
 * [SubStatus.CONFIRMED] cubre por igual «la detectó y la confirmé» y «la escribí yo».
 *
 * Es una heurística, no una garantía formal: `normalizeMerchant` podría producir `manual_algo`
 * a partir de un comercio que de verdad se llame «Manual …», y esa fila se mostraría sin la
 * marca. El costo de equivocarse es una etiqueta de menos en una fila —nunca un número mal
 * calculado ni un borrado indebido— así que no justifica una columna nueva en la tabla.
 */
const val MANUAL_SUB_PREFIX = "manual_"

@Serializable
enum class SubConfidence { HIGH, MEDIUM, LOW }

/**
 * **Cada cuánto llega el cobro.** Hasta la Ola 16 no existía: el modelo asumía que TODO era
 * mensual —[Subscription.amount] se documentaba como «gasto mensual típico»— y tanto el total
 * del server como el «Flujo libre» del cliente lo sumaban como una cifra del mes.
 *
 * El costo de esa suposición no era teórico. Un HBO Max de $369.900 **al año** anotado tal cual
 * le decía al dueño que gasta $369.900 todos los meses en eso: doce veces la plata real.
 *
 * Son dos valores y no un entero de meses a propósito. Un `mesesEntreCobros: Int` invita a un 0
 * (división por cero), a un 7 que nadie sabe pintar y a un −1; los dos casos que existen en el
 * mundo del dueño —y en el de casi cualquiera— son «me lo cobran todos los meses» y «me lo
 * cobran una vez al año». Si algún día aparece un cobro trimestral, agregarle un tercer valor a
 * este enum es un cambio chico y localizado: [montoMensualEquivalente] es el único lugar que
 * tiene que aprender a dividirlo.
 */
@Serializable
enum class PeriodicidadDeCobro {
    /** Llega todos los meses. Es lo que era TODO antes de la Ola 16, y sigue siendo el default. */
    MENSUAL,

    /** Llega una vez al año. */
    ANUAL,
}

/** Los meses que tiene un año, para no dejar el 12 suelto adentro de una fórmula. */
private const val MESES_DEL_ANO = 12L

/**
 * **Cuánto pesa este cobro en UN mes**, en su propia moneda.
 *
 * [Subscription.amount] guarda siempre el cobro REAL —el número que el dueño puede buscar en el
 * extracto y verificar—, así que HBO Max vive en la base como `369900` con
 * [PeriodicidadDeCobro.ANUAL] y nunca como `30825`, una cifra que no aparece en ninguna parte
 * del mundo real. Repartirla entre los meses es trabajo de la app, y este es el único lugar
 * donde se hace.
 *
 * ## Por qué es una función y no un `/ 12` en cada lado
 *
 * El total de suscripciones se calcula DOS veces: el server arma
 * [SubscriptionsResult.monthlyTotalCop] (`resultFor`, SubscriptionRoutes.kt) y el cliente vuelve
 * a sumar fila por fila cuando tiene que excluir alguna que el dueño ya tiene anotada como regla
 * recurrente (`resumenRecurrentes`, RecurrentesLogic.kt). Este proyecto ya se quemó con dos
 * superficies que calculaban «la misma» regla y se separaron —el resumen de presupuestos usaba
 * el mes civil mientras la pantalla usaba el período del dueño, y las dos cifras se contradecían
 * dentro de la misma pantalla—, así que la división vive acá y las dos la llaman.
 *
 * ## El redondeo: hacia ARRIBA, y por una razón
 *
 * $369.900 ÷ 12 da exacto, pero $112.900 ÷ 12 = $9.408,33 no. Hay que elegir un lado, y que los
 * dos lo elijan igual: si no, los totales se separan por pesos y la pantalla parece rota.
 *
 * Se redondea hacia arriba (techo). La propiedad que compra es concreta y verificable:
 * **apartar el equivalente mensual doce veces SIEMPRE alcanza para pagar el cobro anual.** Con
 * el NBA, 12 × $9.409 = $112.908 ≥ $112.900; redondeando hacia abajo, 12 × $9.408 = $112.896 y
 * al año le faltan $4 para el cobro que sí va a llegar. Es además el criterio que el resto de
 * este código ya aplica a la plata: equivocarse hacia abajo en un gasto —dejar el «Flujo libre»
 * más alto de lo que es— es peor que equivocarse hacia arriba.
 *
 * El error máximo es de 11 pesos al mes sobre un cobro anual.
 *
 * **No convierte monedas.** Primero se prorratea en la moneda nativa y después se aplica la TRM,
 * en los dos lados y en ese orden: hacerlo al revés cambia el resultado por el redondeo del
 * medio.
 */
fun montoMensualEquivalente(amount: Long, periodicidad: PeriodicidadDeCobro): Long =
    when (periodicidad) {
        // El cobro real YA es el del mes: no hay nada que repartir ni nada que redondear, así
        // que toda fila que existía antes de la Ola 16 vale exactamente lo mismo que valía.
        PeriodicidadDeCobro.MENSUAL -> amount
        PeriodicidadDeCobro.ANUAL -> techoDeLaDivision(amount, MESES_DEL_ANO)
    }

/** Ver [montoMensualEquivalente]. */
fun Subscription.montoMensualEquivalente(): Long = montoMensualEquivalente(amount, periodicidad)

/**
 * La división entera redondeada hacia arriba, sin pasar por coma flotante — un `Double` con
 * montos grandes es justo la clase de detalle que hace que dos plataformas den cifras distintas.
 *
 * `/` y `%` truncan hacia cero en Kotlin, así que sumar 1 solo cuando el resto es positivo da el
 * techo tanto para positivos como para negativos. Los montos son siempre positivos —el alta los
 * valida y el detector no produce otra cosa—, pero una función de aritmética no debería tener un
 * rango de validez que nadie enuncia.
 */
private fun techoDeLaDivision(dividendo: Long, divisor: Long): Long {
    val cociente = dividendo / divisor
    return if (dividendo % divisor > 0L) cociente + 1 else cociente
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Subscription(
    val id: String,
    val merchantKey: String,    // canónico: "youtube", "anthropic_claude"
    val displayName: String,    // "YouTube", "Claude"
    /**
     * **El cobro REAL, en su moneda nativa** — el número que llega al extracto, no el
     * prorrateado. Con [periodicidad] `MENSUAL` (todo lo que existía antes de la Ola 16) es lo
     * que sale cada mes; con `ANUAL` es lo que sale una vez al año, y la cifra del mes la
     * calcula [montoMensualEquivalente]. Lo que detecta el detector es la mediana de la suma
     * mensual, y siempre es mensual.
     */
    val amount: Long,
    val currency: String,       // "COP" | "USD"
    val dayOfMonth: Int,        // día típico de cobro
    val status: SubStatus,
    val confidence: SubConfidence,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int,       // meses distintos detectados
    /**
     * **Con qué cuenta se paga esto** — la tarjeta o el banco de donde sale el cobro, o `null`
     * si no se sabe.
     *
     * `null` es un valor de primera clase, no un dato faltante que haya que completar. Tres
     * caminos legítimos llegan a él y ninguno es un error:
     * - Toda fila anterior a la Ola 17: el alta manual escribía `accountId = null` a la fuerza,
     *   aunque el dueño hubiera elegido una cuenta en la hoja.
     * - Un alta donde el dueño tocó «Sin cuenta», que es una opción y no una omisión.
     * - Una detectada cuyos cargos aparecieron en VARIAS cuentas: `detectSubscriptions` la
     *   resuelve con `singleOrNull()` justamente para no elegir una al azar entre dos.
     *
     * Por eso nada exige que tenga valor, y la fila que la muestra se calla cuando no lo hay
     * (ver `contextoDeSuscripcionActiva`): pintar un «sin cuenta» le daría forma de dato faltante
     * a algo que no falta, e inventar una cuenta sería afirmar algo que Movi no sabe sobre de
     * dónde sale la plata del dueño.
     */
    val accountId: String? = null,
    /**
     * **Cada cuánto llega [amount]**, y por lo tanto qué significa ese número. Ver
     * [PeriodicidadDeCobro] y [montoMensualEquivalente].
     *
     * **Default `MENSUAL`, y eso es un requisito duro, no una comodidad.** Todo lo que existía
     * antes de la Ola 16 es mensual, el detector solo produce cobros mensuales, y en este
     * proyecto el APK se entrega a mano por Drive mientras el server se despliega aparte: un
     * server nuevo sirviéndole a un cliente viejo —y al revés— es un estado normal, no teórico.
     * Campo ausente tiene que seguir queriendo decir «mensual» en los dos sentidos.
     *
     * **Con [EncodeDefault] `ALWAYS`**, así que la clave viaja aunque valga `MENSUAL`. Sin eso,
     * kotlinx la omite cuando vale su default y el `PUT /api/subscriptions/{id}` no podría
     * distinguir «este cliente no conoce la periodicidad» de «esta suscripción es mensual» — la
     * misma trampa exacta que en la Ola 15 le borraba el seguro y el «la paga otro» a un crédito
     * (ver el KDoc de `CreditTerms`). La ruta igual se defiende mirando las claves del JSON
     * crudo, porque un APK anterior a esta ola no va a mandar el campo por más anotaciones que
     * le pongamos acá: las dos capas cubren cosas distintas y ninguna sobra.
     *
     * **No rompe a nadie leyendo la respuesta**: un cliente anterior ignora la clave (los tres
     * `Platform` configuran `ignoreUnknownKeys = true`). Lo que ese cliente viejo no sabe es que
     * un cobro anual no es un gasto del mes — y por eso el total prorrateado lo calcula el
     * server y le llega ya resuelto en [SubscriptionsResult.monthlyTotalCop].
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val periodicidad: PeriodicidadDeCobro = PeriodicidadDeCobro.MENSUAL,
)

@Serializable
data class SubscriptionsResult(
    val subscriptions: List<Subscription>,
    /**
     * Suma AUTO+CONFIRMED en COP (USD × TRM), ya **prorrateada**: lo que se suma de cada fila es
     * su [montoMensualEquivalente], no su `amount`, así que un cobro anual entra dividido en
     * doce y no doce veces de más. Ver `resultFor` en SubscriptionRoutes.kt.
     */
    val monthlyTotalCop: Long,
    /**
     * La tasa USD→COP que el server usó para armar [monthlyTotalCop], o `0.0` si no hizo falta
     * (ninguna activa en dólares).
     *
     * Se manda al cliente porque [monthlyTotalCop] es un total cerrado y la Ola 8 necesita
     * sumar SUBCONJUNTOS: en la lista única de Recurrentes, una suscripción que el dueño ya
     * tiene anotada como regla recurrente se excluye del total para no contarla dos veces (ver
     * `resumenRecurrentes`). Sin la tasa, el cliente no puede restar una fila en dólares.
     *
     * Default `0.0` para que un server viejo (o un test) siga deserializando. Ojo con lo que
     * ese 0 significa y con lo que NO: un server viejo sí convertía los dólares —van adentro de
     * [monthlyTotalCop]—, lo único que no hacía era exponer la tasa. O sea que tasa 0 no
     * equivale al comportamiento viejo; equivale a «no puedo DESGLOSAR este total». Por eso
     * `resumenRecurrentes` usa [monthlyTotalCop] tal cual cuando no hay nada que excluir (ahí el
     * total del server es exacto, con dólares y todo) y solo necesita la tasa cuando tiene que
     * saltear una fila. Si en ese caso falta, las filas que no se pudieron convertir se cuentan
     * y se avisan en pantalla — nunca se restan en silencio.
     *
     * Esto importa en este proyecto en particular: el APK se entrega a mano por Drive y el
     * server se despliega aparte, así que un cliente nuevo contra un server viejo es un estado
     * real, no teórico.
     */
    val usdToCop: Double = 0.0,
)

/**
 * F38: alta manual — `POST /api/subscriptions`. La creó el dueño, así que nace CONFIRMED (no
 * hay nada que confirmar); el server deriva `merchantKey` del nombre normalizado con el prefijo
 * `manual_` (ver `SubscriptionRoutes.kt`) para que quede fuera del dominio del detector — este
 * nunca produce ese prefijo, así que un re-scan no la toca ni la duplica.
 */
@Serializable
data class CreateSubscriptionRequest(
    val displayName: String,
    val amount: Long,
    val currency: String,   // "COP" | "USD"
    val dayOfMonth: Int,
    /**
     * Ver [Subscription.periodicidad]. `amount` es el cobro real: un HBO Max anual se manda como
     * `369900` + [PeriodicidadDeCobro.ANUAL], nunca ya dividido.
     *
     * **Sin [EncodeDefault]** a propósito, al revés que en [Subscription]: acá la ausencia de la
     * clave no tiene nada que preservar del otro lado —es un alta, no hay fila previa que
     * pisar— y un cliente anterior a la Ola 16 que mande un cuerpo sin el campo está diciendo la
     * verdad cuando el default lo lee como mensual: esa hoja vieja solo sabía anotar cobros
     * mensuales.
     */
    val periodicidad: PeriodicidadDeCobro = PeriodicidadDeCobro.MENSUAL,
    /**
     * **Con qué cuenta se paga** — ver [Subscription.accountId]. Opcional, y **sin
     * [EncodeDefault]** por el mismo motivo que [periodicidad]: es un alta, no hay fila previa
     * que pisar, y un cliente anterior a la Ola 17 que no manda la clave está diciendo la verdad
     * — esa hoja descartaba la cuenta antes de llegar al wire, así que «ausente» y `null`
     * significan exactamente lo mismo.
     *
     * El server NO confía en este id: si la cuenta no es del dueño guarda `null` en vez de
     * rechazar el alta (ver `accountIdIfOwned`). Perder la cuenta es mucho menos malo que perder
     * la suscripción entera por un id que mandó mal un cliente viejo.
     */
    val accountId: String? = null,
)
