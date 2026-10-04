package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Un aviso del banco pendiente que parece ser lo que el dueño está anotando a mano** en «Agregar»:
 * mismo monto, misma moneda y mismo tipo, llegado hace menos de [HORAS_PARA_OFRECER_EL_AVISO] horas.
 *
 * Es el sentido contrario de «¿Ya lo anotaste?» (la revisión de un aviso busca movimientos ya
 * anotados). La auditoría de la ingesta (4-oct-2026) encontró 34 movimientos tecleados en la app que
 * ya tenían su aviso esperando en «Por revisar»: el aviso se confirmaba después por separado o se
 * quedaba ahí, y la categoría que él eligió nunca se asociaba al comercio del banco. Con esto la hoja
 * pregunta «¿Es el aviso de hace 2 h?» y, si dice que sí, el movimiento se guarda CON el aviso: el
 * aviso queda confirmado con ese movimiento y Movi aprende el comercio.
 *
 * Lo devuelve `GET /api/sms/pendientes-parecidos`.
 *
 * @property descripcion el comercio como lo leyó Movi («TOSTAO CAFE Y PAN»), o el destino guardado.
 * @property comercio el texto del banco que identifica al destinatario («Pago QR · llave 0047142708»):
 *   va al `merchant` del movimiento, que es de donde aprende la memoria de categorías.
 */
@Serializable
data class AvisoPendienteParecido(
    val id: String,
    val time: String,
    val bank: String,
    val text: String,
    val monto: Double,
    val moneda: String = "COP",
    val tipo: TransactionType,
    val descripcion: String,
    val comercio: String,
)

/** Cuánto atrás mira «Agregar» por un aviso pendiente con el mismo monto. */
const val HORAS_PARA_OFRECER_EL_AVISO: Long = 48
