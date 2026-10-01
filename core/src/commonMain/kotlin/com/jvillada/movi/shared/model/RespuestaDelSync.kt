package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Lo que contesta `POST /api/sms/sync`**: cuántos mensajes insertó y, de esos, cuáles quedaron
 * esperando en «Por revisar» con un movimiento que se puede leer.
 *
 * [porRevisar] existe por la Ola 1 · Movi avisa: el teléfono avisa «Movi anotó $180.000» apenas la
 * captura sube algo, y el texto de ese aviso tiene que decir lo MISMO que la bandeja —el monto que
 * leyó el server, el nombre del destino guardado («Transferencia a Caro»)—, no una segunda lectura
 * hecha en el teléfono. Quedan afuera los que el server dejó resueltos al llegar (un aviso de app
 * sin ningún número entra ya ignorado, ver `estadoAlLlegar`) y los que no traen un movimiento.
 *
 * Con valor por defecto: un server anterior contesta solo `synced`, y el APK sigue andando (sin
 * aviso con detalle). Un APK anterior lee solo `synced` y le pasa de largo a lo demás.
 */
@Serializable
data class RespuestaDelSync(
    val synced: Int,
    val porRevisar: List<AvisoPorRevisar> = emptyList(),
)

/**
 * Un movimiento que la captura acaba de dejar en «Por revisar», ya leído por el server.
 *
 * @property origen el rótulo `bank` de la fila, tal cual llegó («85540», «Notificación · Nu»).
 * @property descripcion lo que la bandeja propone como nombre: el comercio, o el destino guardado.
 */
@Serializable
data class AvisoPorRevisar(
    val id: String,
    val origen: String,
    val monto: Double,
    val moneda: String = "COP",
    val descripcion: String,
    val tipo: TransactionType,
)
