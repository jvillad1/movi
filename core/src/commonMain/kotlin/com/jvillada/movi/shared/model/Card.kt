package com.jvillada.movi.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * F20 — términos de una tarjeta de crédito (1:1 con su cuenta CREDIT_CARD), el equivalente de
 * [CreditTerms] para tarjetas. Son cosas distintas a propósito: una tarjeta no tiene «capital
 * original», tasa contractual ni plazo — tiene cupo, día de corte y día de pago. Meterlas en la
 * misma tabla habría llenado credit_terms de columnas nullable que mienten sobre qué es cada fila.
 *
 * `creditLimit` y `cutoffDay` son opcionales: no todo el mundo se sabe su cupo o su corte de
 * memoria, y exigirlos dejaría tarjetas sin registrar. `paymentDay` sí es obligatorio — es lo
 * que alimenta el recordatorio de pago, la razón de existir de esta tabla.
 *
 * Montos en la moneda de la cuenta (las Mastercard en USD existen): el cupo de una tarjeta USD
 * es un número en USD, igual que su deuda derivada.
 *
 * ### El campo que SIEMPRE viaja, aunque valga su default
 *
 * El mismo agujero que [CreditTerms] ya documenta, en esta tabla: `PUT /api/cards/{id}` distingue
 * **«el cliente no conoce este campo»** de **«el cliente lo borró»** mirando las claves del JSON
 * recibido, porque `fillCardTerms` sobrescribe todas las columnas y el APK instalado manda cuerpos
 * incompletos. Esa guarda protege bien la primera mitad y rompía la segunda:
 * kotlinx-serialization **omite** una propiedad que vale igual que su default —los tres `Platform`
 * usan `Json { ignoreUnknownKeys = true }`, que no cambia `encodeDefaults`—, así que el cliente de
 * verdad, el que sí conoce [pagoMinimo], al borrar el campo mandaba un cuerpo **sin la clave**:
 * indistinguible de un APK viejo. El server le reponía el valor anterior, la hoja cerraba sin
 * error, y el «Flujo libre» seguía restando el mínimo que él acababa de quitar.
 *
 * [EncodeDefault] con `ALWAYS` hace que la clave viaje siempre, así que un `null` explícito llega
 * como tal. Ya había pasado con `Subscription.accountId` (Ola 18) y con los tres campos de
 * [CreditTerms]. Lo fija `MinimoEnElWireTest`, que mira el **JSON serializado** y no el objeto: una
 * prueba que arma el cuerpo a mano manda algo que ningún cliente produce y no ve nada.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CardTerms(
    val accountId: String,
    val bank: String,
    val creditLimit: Long? = null,  // cupo total, en la moneda de la cuenta
    val cutoffDay: Int? = null,     // día de corte (1–31)
    val paymentDay: Int,            // día límite de pago (1–31)
    /**
     * **El pago mínimo del extracto**, en la moneda de la cuenta. `null` = todavía no se cargó.
     *
     * ### Por qué es un dato tecleado y no una cuenta
     *
     * Es la primera cifra de una tarjeta que Movi **no puede derivar**. La deuda sale de los
     * eventos, el cupo disponible sale del cupo menos la deuda; el mínimo no sale de nada que la
     * app tenga: cada banco lo arma distinto y cambia con los diferidos y los avances. El mínimo
     * de Bancolombia ronda el 5 % del saldo, y estimarlo con ese 5 % es exactamente el error que
     * [RecurringRule.montoEsSaldo] documenta haber cometido en la otra dirección — ahí Movi
     * anunció la deuda entera como el próximo pago; acá anunciaría un porcentaje inventado sobre
     * la plata del dueño que él no puede verificar contra ningún papel.
     *
     * Misma postura que [CreditTerms.insuranceMonthly] y [CreditTerms.otrosCargosMensuales], y
     * por el mismo motivo: **es un dato del extracto**. Se teclea, es nullable, y cuando es
     * `null` no se inventa nada — se dice que falta. Esa segunda mitad es la que hace que la
     * decisión no le cueste al dueño una cifra optimista: `ResumenRecurrentes.tarjetasSinMinimo`
     * (en `:shared`) hace que el «Flujo libre» deje de afirmar un número y diga cuántas tarjetas
     * con deuda le faltan por cargar.
     *
     * ### Y por qué el mínimo y no «la cuota»
     *
     * Una tarjeta no tiene cuota (ver [RecurringRule.montoEsSaldo]). Tiene un piso —lo que hay
     * que pagar para no entrar en mora— y un techo —la deuda entera—. El mínimo es el único de
     * los dos que es un compromiso: pagarlo no es opcional, y por eso es el que le come el
     * disponible del mes. Lo que pague por encima del mínimo es una decisión, no una obligación.
     *
     * Viaja siempre, aunque valga `null`: sin eso **borrar el campo desde la hoja no lo borraba en
     * la base**, porque el PUT leía la clave ausente como «cliente viejo» y reponía el valor
     * anterior. Ver el KDoc de la clase.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val pagoMinimo: Long? = null,
    /**
     * Lo que la tarjeta tiene de particular y no cabe en ningún otro campo. **Viaja siempre,
     * aunque valga `null`**, por lo mismo que [pagoMinimo] y con el mismo costo si no lo hiciera:
     * la guarda por clave del PUT leería el campo vaciado como «cliente viejo» y repondría la nota
     * anterior, así que borrarla sería imposible desde la app. Ver el KDoc de la clase.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val notes: String? = null,
    /**
     * Ver [com.jvillada.movi.shared.model.RecurringRule.remindMe]: el pago de esta tarjeta entra
     * (o no) al barrido de avisos. Default `true` — las tarjetas que ya existían siguen avisando.
     *
     * **Viaja siempre, aunque valga ese default**, por lo mismo que [pagoMinimo] y [notes], y con
     * una vuelta de tuerca: acá el valor omitido —`true`— es el que el dueño elige al marcar la
     * casilla. Sin la anotación, volver a prender el aviso manda un cuerpo sin la clave, la guarda
     * del PUT lo lee como «cliente viejo» y repone el `false`: la casilla se podría apagar y nunca
     * más prender.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val remindMe: Boolean = true,
)

/**
 * Una tarjeta como la lista `GET /api/cards`: la cuenta CREDIT_CARD con su deuda derivada de
 * eventos, los términos si ya los tiene, y el cupo disponible cuando hay cupo declarado.
 */
@Serializable
data class CardSummary(
    val account: Account,     // cuenta CREDIT_CARD con deuda derivada en balance
    val terms: CardTerms?,    // null si la tarjeta aún no tiene términos (creada desde Cuentas)
    /**
     * Cupo − deuda, en la moneda de la cuenta; null sin cupo declarado. Puede ser negativo
     * (deuda por encima del cupo): se devuelve tal cual — recortarlo a 0 sería un número que
     * miente sobre un sobregiro real.
     */
    val available: Long? = null,
)

/**
 * Prefijo de los ids de las reglas recurrentes sintéticas derivadas de card_terms — el
 * equivalente de [CREDIT_RULE_PREFIX] para tarjetas. Compartido entre el server (que las
 * genera) y la UI (que las distingue de las reglas reales editables).
 */
const val CARD_RULE_PREFIX = "card_"

/**
 * Alta atómica de una tarjeta: cuenta CREDIT_CARD + evento de deuda inicial (si la hay) +
 * términos en una sola operación server-side — mismo patrón que [CreateCreditRequest].
 *
 * A diferencia de un préstamo (que sin deuda no existe), una tarjeta recién sacada puede estar
 * en $0: `initialDebt = 0` es válido y simplemente no genera evento de apertura.
 */
@Serializable
data class CreateCardRequest(
    val name: String,           // nombre de la cuenta CREDIT_CARD a crear
    val initialDebt: Long = 0,  // deuda actual; 0 = tarjeta al día, sin evento de apertura
    val currency: String = "COP",
    val terms: CardTerms,       // accountId se ignora; el server asigna el de la cuenta nueva
)
