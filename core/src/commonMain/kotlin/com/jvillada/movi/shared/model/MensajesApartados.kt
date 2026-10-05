package com.jvillada.movi.shared.model

/**
 * # Los mensajes que Movi aparta solo
 *
 * A «Por revisar» llegaban avisos del banco que no son movimientos: el recordatorio de un pago, un
 * código de verificación, una promoción, un «te autenticaste», el «tu extracto está listo». El dueño
 * los tenía que ignorar uno por uno. Desde el 3-oct-2026 el server los reconoce al llegar
 * (`queEsEsteMensaje`, en `:server`) y los **aparta**: se guardan igual —no se pierde nada—, pero no
 * esperan una decisión.
 *
 * ### Cómo se modela «apartado», y por qué así
 *
 * Un mensaje apartado es un `ignored` (ver [SMS_STATE_IGNORED]) **con motivo**: [SmsMessage.apartadoPor].
 * No es un cuarto estado a propósito. Todo lo que cuenta lo pendiente —el «N por revisar», las
 * alertas del Inicio, la bandeja, el contexto de Movi AI— filtra por `pending`, así que un apartado
 * queda fuera de todos sin tocar ninguno; y un APK viejo, que no conoce el motivo, lo muestra en el
 * historial como «IGNORADO», que es verdad. Un estado nuevo (`apartado`) lo habría pintado con el
 * nombre crudo y habría obligado a revisar cada `when` sobre el estado.
 *
 * «Era un movimiento» lo devuelve a `pending` y deja la marca [MOTIVO_DEVUELTO] en la base: la pasada
 * que aparta los pendientes viejos no lo vuelve a apartar nunca.
 */
enum class MotivoDeApartado(
    /** Cómo se dice en el historial: «Movi lo apartó: recordatorio de pago». */
    val texto: String,
) {
    RECORDATORIO_DE_PAGO("recordatorio de pago"),
    CODIGO_DE_VERIFICACION("código de verificación"),
    PROMOCION("promoción"),
    AVISO_DE_SEGURIDAD("aviso de seguridad"),
    EXTRACTO_DISPONIBLE("extracto disponible"),
    RESUMEN_DE_GASTOS("resumen de tus gastos"),
    NO_PASO("rechazado, no pasó"),
    SIN_MONTO("aviso sin monto"),
    /**
     * El correo con que Gmail pide confirmar un reenvío («Gmail Forwarding Confirmation»): llega a
     * la dirección de Movi cuando el dueño arma el reenvío, y trae el código y el enlace que hay que
     * usar. No es un movimiento, pero se guarda entero para poder leer el código.
     */
    CONFIRMACION_DE_REENVIO("confirmación de reenvío de Gmail"),
    /** Una copia de un aviso que ya estaba (p. ej. el mismo SMS subido otra vez con la hora corrida). */
    DUPLICADO("copia de un aviso que ya tenías"),
}

/**
 * La marca que deja «Era un movimiento» en la columna del motivo (nunca viaja al cliente): el dueño
 * ya dijo que este mensaje sí es un movimiento, y Movi no lo vuelve a apartar.
 */
const val MOTIVO_DEVUELTO: String = "DEVUELTO"

/** El motivo de [codigo], o `null` si no es uno (vacío, [MOTIVO_DEVUELTO] o de una versión futura). */
fun motivoDeApartado(codigo: String?): MotivoDeApartado? =
    MotivoDeApartado.entries.firstOrNull { it.name == codigo }

/** ¿Lo apartó Movi (y no el dueño)? Solo un ignorado con motivo. */
fun loApartoMovi(sms: SmsMessage): Boolean =
    sms.state == SMS_STATE_IGNORED && !sms.apartadoPor.isNullOrBlank()

/**
 * «Movi lo apartó: recordatorio de pago». Un motivo que este cliente no conoce (de un server más
 * nuevo) se dice sin el detalle, en vez de no decir nada.
 */
fun textoDelApartado(sms: SmsMessage): String? {
    if (!loApartoMovi(sms)) return null
    val motivo = motivoDeApartado(sms.apartadoPor) ?: return "Movi lo apartó: no es un movimiento"
    return "Movi lo apartó: ${motivo.texto}"
}
