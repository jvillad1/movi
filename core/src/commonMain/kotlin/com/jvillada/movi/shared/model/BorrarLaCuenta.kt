package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Lo que hay que escribir para borrar la cuenta.** En mayúsculas y sin nada más: es un freno, no un
 * acertijo. La app no habilita el botón sin esta palabra y el server la vuelve a exigir.
 */
const val PALABRA_PARA_BORRAR_LA_CUENTA: String = "BORRAR"

/**
 * `DELETE /api/me` — borrar la cuenta entera.
 *
 * La contraseña viaja en el cuerpo, **nunca en la URL**: la ruta queda escrita en el log de cada
 * pedido (`CallLogging`) y en el del borde de Railway.
 *
 * @property contrasena la de entrar. Un token robado no alcanza para borrar a nadie.
 * @property confirmacion lo que escribió en la casilla; tiene que ser [PALABRA_PARA_BORRAR_LA_CUENTA].
 */
@Serializable
data class BorrarLaCuentaRequest(
    val contrasena: String,
    val confirmacion: String,
)

/** ¿Lo escrito en la casilla habilita el borrado? Lo usan la pantalla y el server: una sola regla. */
fun confirmaElBorrado(texto: String): Boolean = texto.trim() == PALABRA_PARA_BORRAR_LA_CUENTA
