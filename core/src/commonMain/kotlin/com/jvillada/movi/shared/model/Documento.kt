package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * Qué es este archivo, para que la lista se pueda leer sin abrir nada.
 *
 * No es una taxonomía completa de papeles financieros —sería una lista infinita y ninguna
 * pantalla se beneficia de treinta opciones—: es la mínima que cambia cómo se ve la fila.
 */
@Serializable
enum class TipoDeDocumento {
    /** Un extracto bancario o de tarjeta. Los que Movi importa se archivan solos con este tipo. */
    EXTRACTO,

    /** Una nómina, un desprendible de pago. */
    NOMINA,

    /** Un contrato, una escritura, una promesa de compraventa, un pagaré. */
    CONTRATO,

    /** Todo lo demás: una carta del banco, un certificado, una foto de un recibo. */
    OTRO,
}

/**
 * Un archivo guardado en Movi — **los metadatos, nunca los bytes**.
 *
 * Sale de un pedido del dueño: *«me gustaría que guardemos en Movi extractos y documentos en
 * algún lugar y los podamos listar y acceder desde el sitio y la app»*. Hasta acá el importador
 * de extractos recibía el PDF, lo parseaba y **tiraba el archivo**: los movimientos quedaban,
 * el papel del que salieron no. Cuando una cifra no cuadra con el banco, ese papel es
 * exactamente lo que hace falta y no estaba.
 *
 * El contenido viaja aparte, por `GET /api/documents/{id}/content`, y nunca dentro de esta
 * clase. Un extracto de 2 MB en base64 dentro de un JSON de lista convertiría «ver mis
 * documentos» en bajar todos los documentos.
 */
@Serializable
data class Documento(
    val id: String,
    val nombre: String,
    val tipo: TipoDeDocumento,
    val mimeType: String,
    val bytes: Long,
    val subidoEn: Long,
    /** La cuenta a la que pertenece, si aplica: el extracto de la Bancolombia Ahorros. */
    val accountId: String? = null,
    /** El período que cubre, tal como lo escribió el dueño: «2026-08», «julio 2026». */
    val periodo: String? = null,
    val notas: String? = null,
)

/**
 * El permiso de descarga: una URL con vida corta.
 *
 * Existe porque abrir un archivo desde el navegador es una navegación del navegador —una pestaña
 * nueva, o el visor de PDF del sistema— y ahí no hay forma de poner el encabezado
 * `Authorization`. Las dos salidas conocidas son mandar el token de sesión en la URL o emitir
 * uno aparte; la primera pone un JWT de **30 días** en el historial del navegador, en los logs
 * del proxy y en cualquier referer. Esta es la segunda.
 *
 * El token que devuelve sirve para **un** documento, del **dueño** que lo pidió, y por pocos
 * minutos.
 */
@Serializable
data class EnlaceDeDescarga(
    val url: String,
    /** Epoch en milisegundos. Pasado ese momento hay que pedir otro. */
    val expiraEn: Long,
)

/**
 * Lo que se puede corregir de un documento ya subido: **cómo se llama, qué es, y qué anotaste**.
 * Nunca los bytes — para cambiar el archivo se sube otro y se borra este.
 *
 * Sale de un hueco que la revisión encontró en la primera versión: el tipo se adivinaba por el
 * nombre del archivo y el comentario decía «se puede corregir después», pero **no existía forma
 * de hacerlo**. Un `IMG_4821.jpg` que es la escritura del apartamento quedaba en «Otros» para
 * siempre, y `periodo` y `notas` no tenían manera de llenarse desde la app.
 *
 * Cada campo es opcional y `null` significa **«no lo toques»**, no «bórralo». Es la misma
 * distinción que ya costó un defecto en `PUT /api/credits/{id}`, donde un cliente viejo borraba
 * `paidBy` sin querer: acá se resuelve desde el diseño en vez de mirando las claves del JSON,
 * porque esta ruta nace hoy y puede darse el lujo.
 *
 * Para BORRAR un texto se manda la cadena vacía, que es distinguible de `null` y se guarda como
 * `null`.
 */
@Serializable
data class EdicionDeDocumento(
    val nombre: String? = null,
    val tipo: TipoDeDocumento? = null,
    val periodo: String? = null,
    val notas: String? = null,
)

/** Tope de tamaño, en bytes. Un extracto bancario típico pesa menos de 2 MB. */
const val MAX_DOCUMENTO_BYTES = 10L * 1024 * 1024
