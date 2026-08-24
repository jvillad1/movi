package com.jvillada.movi.shared.model

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

@Serializable
data class Subscription(
    val id: String,
    val merchantKey: String,    // canónico: "youtube", "anthropic_claude"
    val displayName: String,    // "YouTube", "Claude"
    val amount: Long,           // gasto mensual típico (mediana de la suma mensual, moneda nativa)
    val currency: String,       // "COP" | "USD"
    val dayOfMonth: Int,        // día típico de cobro
    val status: SubStatus,
    val confidence: SubConfidence,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int,       // meses distintos detectados
    val accountId: String? = null,
)

@Serializable
data class SubscriptionsResult(
    val subscriptions: List<Subscription>,
    val monthlyTotalCop: Long,  // suma AUTO+CONFIRMED en COP (USD × TRM)
    /**
     * La tasa USD→COP que el server usó para armar [monthlyTotalCop], o `0.0` si no hizo falta
     * (ninguna activa en dólares).
     *
     * Se manda al cliente porque [monthlyTotalCop] es un total cerrado y la Ola 8 necesita
     * sumar SUBCONJUNTOS: en la lista única de Recurrentes, una suscripción que el dueño ya
     * tiene anotada como regla recurrente se excluye del total para no contarla dos veces (ver
     * `resumenRecurrentes`). Sin la tasa, el cliente no puede restar una fila en dólares.
     *
     * Default `0.0` para que un server viejo (o un test) siga deserializando: con tasa 0 solo
     * se puede convertir lo que ya está en pesos, que es exactamente lo que un server que no
     * manda el campo tampoco convirtió.
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
)
