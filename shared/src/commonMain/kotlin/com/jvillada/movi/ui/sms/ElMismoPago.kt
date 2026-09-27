package com.jvillada.movi.ui.sms

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.horaLegibleDeSms
import com.jvillada.movi.theme.Movi

/**
 * El origen de un aviso como lo diría una persona: «Notificación · Google Wallet» → «Google Wallet»,
 * «Correo · Bancolombia» → «Bancolombia». Un SMS lleva el código del remitente y se deja como está.
 */
internal fun origenLegibleDelAviso(bank: String): String =
    bank.substringAfter('·', bank).trim().ifBlank { bank.trim() }

/**
 * **La línea que avisa que dos avisos parecen el mismo pago**, antes de aprobar el segundo.
 *
 * El 25-sep el dueño aprobó dos avisos del mismo pago con la Glim —el de Google Wallet y el de la
 * app de Glim— y quedaron dos movimientos: nada le dijo que eran el mismo. El server marca el
 * parecido (`SmsMessage.parecidoA`); esto lo dice con el origen y la hora del otro, que es lo que
 * él puede reconocer. Si no se sabe cuál es el otro (no se pudo leer), se dice igual, sin detalle.
 */
internal fun avisoDelMismoPago(otro: SmsMessage?): String {
    if (otro == null) return "Parece el mismo pago que otro aviso."
    val origen = origenLegibleDelAviso(otro.bank)
    val hora = horaLegibleDeSms(otro.time)
    if (hora == null) return "Parece el mismo pago que el aviso de $origen."
    // «de la 1:15», no «de las 1:15». Y la hora ya termina en punto («a. m.»): no se le pone otro.
    val articulo = if (hora.startsWith("1:")) "la" else "las"
    val cierre = if (hora.endsWith(".")) "" else "."
    return "Parece el mismo pago que el aviso de $origen de $articulo $hora$cierre"
}

/** La línea de apoyo de [avisoDelMismoPago], con el color de aviso: es algo que revisar antes de aprobar. */
@Composable
internal fun LineaDelMismoPago(otro: SmsMessage?, modifier: Modifier = Modifier) {
    Text(
        avisoDelMismoPago(otro),
        style = Movi.textos.apoyo,
        color = Movi.colores.aviso,
        lineHeight = 17.sp,
        modifier = modifier,
    )
}
