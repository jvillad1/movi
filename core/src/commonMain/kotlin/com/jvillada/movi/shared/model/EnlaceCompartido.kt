package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Compartir con un tercero: un enlace de solo lectura
 *
 * El dueño lo pidió así: poder mostrarle su situación a Caro o a un asesor **sin darle su
 * contraseña ni mandar capturas**. La respuesta es un enlace que abre una página liviana servida
 * por el server —sin login, sin wasm— con un resumen de su plata. Vence solo y se puede revocar.
 *
 * ## El enlace es una capacidad, y por eso el token NO viaja en este tipo
 *
 * Quien tiene el enlace ve los datos: no hay usuario ni contraseña detrás, el enlace ES el permiso.
 * Así que el server guarda **solo el hash** del token (si la base se filtra, los enlaces no), y el
 * token en claro existe una sola vez: en la respuesta de crearlo ([EnlaceCompartidoCreado.ruta]).
 * Este tipo, el que se lista, no lo lleva — no puede llevarlo, el server ya no lo tiene. La
 * consecuencia para la pantalla es que un enlace se copia **al crearlo o nunca**; si se pierde, se
 * revoca y se crea otro. Es el mismo trato que dan GitHub o Stripe a sus llaves, por el mismo motivo.
 *
 * ## Por qué [alcance] es texto y no un enum
 *
 * Hoy hay un solo valor ([ALCANCE_RESUMEN]) y mañana va a haber otros («solo deudas» para un
 * banco, «todo» para un contador). Un valor nuevo en un `enum` serializado **revienta al APK
 * viejo** que lo deserializa —la regla dura del proyecto— así que va como texto con default.
 */
@Serializable
data class EnlaceCompartido(
    val id: String,
    /** Qué se ve con este enlace. Ver el KDoc del tipo: texto, no enum, a propósito. */
    val alcance: String = ALCANCE_RESUMEN,
    /** Epoch en milisegundos. */
    val creadoEn: Long,
    /** Epoch en milisegundos. Pasado este momento la página contesta lo mismo que a un enlace inventado. */
    val venceEn: Long,
    /**
     * Cuándo se abrió por última vez; `null` si nadie lo abrió todavía. Es lo que le deja al dueño
     * saber si Caro ya lo miró — y, sobre todo, notar si alguien lo está abriendo cuando no debería.
     */
    val ultimaVista: Long? = null,
    /** Cuántas veces se abrió. */
    val vistas: Int = 0,
)

/** El pedido de crear un enlace. Los dos campos con default: un cliente que no los mande pide lo de siempre. */
@Serializable
data class NuevoEnlaceCompartido(
    /** Cuántos días vale. Solo [VIGENCIAS_DE_ENLACE]; cualquier otro valor es un 400. */
    val dias: Int = VIGENCIA_POR_DEFECTO,
    val alcance: String = ALCANCE_RESUMEN,
)

/**
 * La respuesta de crear: el enlace y **la única vez que existe su ruta con el token**.
 *
 * [ruta] viaja relativa (`/compartido#…`) porque el server no sabe con qué origen lo llamaron —
 * detrás del borde de Railway ve su propio host interno—; el cliente, que sí conoce su `baseUrl`,
 * la vuelve absoluta. Mismo trato que [EnlaceDeDescarga].
 *
 * El token va en el **fragmento** (`#`), no en la ruta ni en la consulta: el navegador nunca manda
 * el fragmento al server, así que no queda en ningún log —ni en el nuestro ni en el del proxy de
 * Railway— ni en un `Referer`. Ver `EnlaceCompartidoRoutes.kt` en el server para el porqué entero.
 */
@Serializable
data class EnlaceCompartidoCreado(
    val enlace: EnlaceCompartido,
    val ruta: String,
)

/** El único alcance que existe hoy: el resumen de la plata, sin movimientos uno por uno. */
const val ALCANCE_RESUMEN: String = "resumen"

/** Los alcances que el server acepta. Sumar uno nuevo es agregarlo acá y pintarlo en el server. */
val ALCANCES_DE_ENLACE: List<String> = listOf(ALCANCE_RESUMEN)

/**
 * Cuánto puede valer un enlace, en días. **Tres opciones y no un campo libre**: un día para
 * mostrarle algo a alguien hoy, una semana para una conversación que se alarga, un mes para un
 * asesor. Un enlace de un año es un enlace que nadie se acuerda de revocar.
 */
val VIGENCIAS_DE_ENLACE: List<Int> = listOf(1, 7, 30)

/** La que viene marcada: una semana. */
const val VIGENCIA_POR_DEFECTO: Int = 7
