package com.jvillada.movi.shared.model

/*
 * # Vuelta 137 · en el iPhone, las notificaciones de cualquier banco
 *
 * El atajo de mensajes manda `tipo` = «mensaje», y el server le aplica la regla de un SMS: el texto tiene que
 * **empezar por el nombre de un banco** y traer un monto (`seGuardaElSmsDelAtajo`). Una notificación de app casi
 * nunca empieza así («Recibiste $50.000 de …», «Compra aprobada por $27.500,00»), y solo las de Nu tenían su
 * reconocedor (`notificacionDeNuDelAtajo`). En Android no pasa: el teléfono sabe de qué app viene cada
 * notificación (la lista de paquetes) y sube cualquier texto de una app de banco.
 *
 * El atajo nuevo, «Movi - Avisar una notificacion» ([NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES]), manda `tipo` =
 * «notificacion» y se ata **solo** a disparadores «Notificación» de las apps de banco que el dueño elige: elegir
 * la app cumple el papel de la lista de paquetes. Con eso, el server guarda lo que trae un monto que Movi sabe
 * leer (la regla del monto vive en el server, junto al parser: `montoYMonedaDelTexto`), y acá viven las dos
 * piezas puras: cómo se arma el texto ([textoDeLaNotificacionDelAtajo]) y de qué banco se dice que es
 * ([origenDeLaNotificacionPorAtajo]).
 */

/**
 * **El texto de una notificación que mandó el atajo**, como lo sube un Android («título: cuerpo»,
 * [textoDeUnaNotificacion]), o `null` si llegó vacío. Atajos manda título y cuerpo en dos renglones, pegados
 * con «: » o solo el cuerpo: dos renglones o más se vuelven «primer renglón: el resto»; uno solo queda como
 * está. Es lo mismo que hace [notificacionDeNuDelAtajo] con lo que no tiene un título de Nu, para que el dedupe
 * (texto + tiempo) vea igual la misma notificación por los dos caminos. Pura.
 */
fun textoDeLaNotificacionDelAtajo(crudo: String): String? {
    val limpio = crudo.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (limpio.isEmpty()) return null
    val renglones = limpio.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (renglones.size < 2) return limpio.take(MAX_TEXTO_DE_UNA_NOTIFICACION)
    return textoDeUnaNotificacion(renglones.first(), limpio.substringAfter('\n').trim())
}

/**
 * **El banco de una notificación que mandó el atajo**, o `null` si el texto no lo dice. Con las reglas de
 * siempre ([marcaDelRemitente]: lo que va antes de «:» o la primera palabra, «Nequi: Recibiste…»,
 * «Bancolombia» en el título), y **solo si es un banco o una billetera**: «Rappi: tu pedido…» no hace de Rappi
 * un banco. Nunca se busca el banco en el resto del texto: «Pagaste en Banco Davivienda SA con tu cuenta» (una
 * notificación de Nu) no es de Davivienda, y un rótulo equivocado juntaría avisos de bancos distintos y
 * confundiría al banco mudo.
 */
fun bancoDeLaNotificacionDelAtajo(texto: String): String? =
    marcaDelRemitente("", texto)?.takeIf { it.rubro == RubroDeMarca.BANCOS_Y_BILLETERAS }?.nombre

/**
 * **El rótulo `bank` de una notificación que mandó el atajo**: «Atajo · Notificación · Davivienda», o
 * «Atajo · Notificación · tu banco» ([BANCO_SIN_NOMBRE_DEL_ATAJO], como el SMS sin banco) si el texto no lo
 * dice. `origenSinElAtajo` lo vuelve «Notificación · Davivienda», así que el canal, el banco mudo, el mismo pago
 * y `queEsEsteMensaje` lo tratan como la notificación de un Android, igual que «Atajo · Notificación · Nu».
 */
fun origenDeLaNotificacionPorAtajo(texto: String): String =
    "$ORIGEN_DE_UNA_NOTIFICACION_POR_ATAJO · ${bancoDeLaNotificacionDelAtajo(texto) ?: BANCO_SIN_NOMBRE_DEL_ATAJO}".take(100)
