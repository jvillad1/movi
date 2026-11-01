package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # «Anotarlo igual»: un aviso que Movi no entiende no deja al dueño trabado
 *
 * El 20-oct-2026 llegó «Nu: Acabas de enviar $1.000,00 💰 Se los enviaste a Carolina R.», un formato
 * que Movi no conocía. Reconciliar decía «Este mensaje no trae un movimiento para anotar», el botón
 * de confirmar quedaba apagado y la única salida era «Ignorar». Ese formato ya se lee (#458), pero
 * cada formato nuevo de un banco lo vuelve a dejar trabado: de Nu solo se lee una lista de formas
 * conocidas.
 *
 * Lo que pidió el dueño: cuando Movi no entiende un aviso **que sí trae un monto**, Reconciliar deja
 * anotarlo igual, con el monto ya puesto, y él elige el resto. Lo que trae el aviso lo calcula el
 * server (`AnotarloIgual.kt` en `:server`, que escribe el criterio de cuándo se ofrece); al confirmar,
 * la app usa el mismo camino que cualquier movimiento suelto de un aviso (`AnotarElAviso.kt`).
 */

/**
 * **Lo que trae un aviso que Movi no supo leer**: el monto que se encontró en el texto, su moneda y
 * la fecha del aviso, más lo poco que se puede proponer sin entenderlo.
 *
 * @property monto el monto que dice el texto, leído con las mismas regex y la misma regla de
 *   separadores que un aviso que sí se entiende.
 * @property moneda `"USD"` si el texto lo dice; `"COP"` en lo demás.
 * @property tipo gasto, salvo que el texto diga que la plata llegó («recibiste», «te llegó»). El dueño
 *   lo cambia en Reconciliar.
 * @property momento cuándo llegó el aviso (epoch ms): la fecha del movimiento.
 * @property comercio a quién fue, si el lector genérico lo encontró (o el nombre de un tercero
 *   guardado); `null` si no, y la pantalla dice «Movimiento» hasta que él lo escriba.
 * @property categoria la que propone la memoria del dueño para ese comercio; `null` = a elegir.
 * @property aprendidoDe de dónde salió [categoria] («Así lo anotaste la última vez»).
 */
@Serializable
data class AvisoSinLeer(
    val monto: Double,
    val moneda: String = "COP",
    val tipo: TransactionType = TransactionType.EXPENSE,
    val momento: Long = 0L,
    val comercio: String? = null,
    val categoria: String? = null,
    val aprendidoDe: String? = null,
    /**
     * Vuelta 91 · **el aviso habla de un depósito del dueño** («Recibimos tu depósito…», «tu depósito»,
     * `hablaDeUnDepositoPropio`): es el lado que entra de un traspaso entre sus cuentas, nunca un
     * ingreso. [comoLectura] lo propone como «Traspaso» y la app pide de qué cuenta vino. Un APK viejo
     * lo ignora.
     */
    val deposito: Boolean = false,
)

/**
 * El cuerpo del 422 de `GET /api/sms/{id}/parse?anotarloIgual=1`: por qué no se pudo leer y, si el
 * aviso trae un monto que se puede anotar, [sinLeer]. Sin el parámetro el 422 sigue siendo el texto
 * de siempre: el APK instalado lo muestra tal cual, y un JSON le saldría crudo en la pantalla.
 */
@Serializable
data class NoSePudoLeerElAviso(
    val motivo: String,
    val sinLeer: AvisoSinLeer? = null,
)

/** El parámetro con que la app pide [NoSePudoLeerElAviso] en vez del texto (ver arriba). */
const val PARAMETRO_ANOTARLO_IGUAL: String = "anotarloIgual"

/**
 * **Lo que lee Reconciliar de un aviso**: la propuesta de siempre ([leido]) o, si no la hay, por qué
 * ([noSePudoLeer]) y lo que trae para anotarlo igual ([sinLeer], `null` si no se ofrece).
 */
data class LecturaParaReconciliar(
    val leido: ParsedSms? = null,
    val noSePudoLeer: String? = null,
    val sinLeer: AvisoSinLeer? = null,
)

/** El comercio que se propone cuando el aviso no nombra a nadie: el mismo de `parseSms`. */
const val COMERCIO_DE_UN_AVISO_SIN_LEER: String = "Movimiento"

/**
 * **La lectura con que se anota un aviso sin leer**: un [ParsedSms] como el de cualquier aviso, para
 * que confirmar vaya por el MISMO camino (`movimientoDelAviso` y la confirmación de siempre en
 * `:shared`). El monto y el tipo son los que dejó el dueño; la categoría, la propuesta si la hay
 * (vacía si no: la pantalla pide elegir una).
 */
fun AvisoSinLeer.comoLectura(monto: Double = this.monto, tipo: TransactionType = this.tipo): ParsedSms = ParsedSms(
    amount = monto,
    merchant = comercio?.takeIf { it.isNotBlank() } ?: COMERCIO_DE_UN_AVISO_SIN_LEER,
    type = tipo,
    // Vuelta 91: un depósito propio que entra es el lado que entra de un traspaso, nunca un ingreso.
    category = if (deposito && tipo == TransactionType.INCOME) TRANSFER_CATEGORY else categoria.orEmpty(),
    currency = moneda,
    aprendidoDe = aprendidoDe,
)
