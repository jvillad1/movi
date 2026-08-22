package com.jvillada.movi.sms

/**
 * Reensambla el cuerpo de un SMS multiparte en el camino de TIEMPO REAL: los PDUs que trae el
 * broadcast, en orden, tal como los devuelve `Telephony.Sms.Intents.getMessagesFromIntent`.
 *
 * Por qué es una función aparte y vive acá: el otro camino de captura (el backfill) NO
 * reensambla nada — lee la columna `body` del inbox, que el proveedor ya guardó completa. Los
 * dos textos tienen que salir idénticos, porque el dedupe del server compara (texto, tiempo)
 * y los ids de los dos caminos son distintos a propósito (`sms_rt_` vs `sms_`). Un carácter de
 * diferencia y el mismo movimiento aparece dos veces. Adentro del BroadcastReceiver esto no se
 * puede testear (depende de `android.telephony.SmsMessage`); como función pura de `String?`, sí
 * — ver `SmsMultipartTest`.
 *
 * Las dos reglas, que son las mismas que aplica el proveedor al armar la fila del inbox
 * (`buildMessageBodyFromPdus` + `replaceFormFeeds` en AOSP, copiado tal cual en la app de
 * Mensajes):
 *  1. concatenar sin separador y sin recortar — un espacio al final de una parte es parte del
 *     mensaje, y un `trim()` acá desalinearía el texto respecto del inbox;
 *  2. el salto de página (U+000C, que el alfabeto GSM 7-bit sí puede traer) se guarda como
 *     salto de línea. Es raro en un SMS bancario, pero es la única transformación que el
 *     proveedor hace y nosotros no hacíamos: sin ella los dos caminos divergen.
 */
fun joinMultipartBody(parts: List<String?>): String =
    parts.joinToString("") { it.orEmpty() }.replace('\u000C', '\n')
