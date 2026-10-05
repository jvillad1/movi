package com.jvillada.movi.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.fechaCortaDeSms
import com.jvillada.movi.shared.model.horaLegibleDeSms
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.fecha.hoyEnAppZone

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

// ── Un pago, una tarjeta (4-oct-2026) ───────────────────────────────────────────────────────────

/**
 * **El canal de un aviso, como se nombra en la tarjeta del mismo pago**: «85540» → «SMS»,
 * «Notificación · Google Wallet» → «Google Wallet», «Correo · Bancolombia» → «Correo». Así la línea
 * «SMS · Bancolombia · Google Wallet» dice por dónde llegó cada aviso, que es lo que distingue a
 * uno de otro (el monto es el mismo en todos).
 */
internal fun canalLegibleDelAviso(bank: String): String {
    val limpio = bank.replace(' ', ' ').trim()
    return when {
        limpio.isEmpty() || limpio.equals("SMS", ignoreCase = true) || limpio.all { it.isDigit() } -> "SMS"
        limpio.startsWith("Correo", ignoreCase = true) -> "Correo"
        else -> origenLegibleDelAviso(limpio)
    }
}

/** «Mismo pago avisado 3 veces». */
internal fun tituloDelMismoPago(cuantos: Int): String = "Mismo pago avisado $cuantos veces"

/** «SMS · Bancolombia · Google Wallet», en el orden en que llegaron. */
internal fun canalesDelMismoPago(miembros: List<SmsMessage>): String =
    miembros.map { canalLegibleDelAviso(it.bank) }.distinct().joinToString(" · ")

/** «Ya lo anotaste con el aviso de Google Wallet de las 9:15 a. m.» */
internal fun avisoYaAnotado(otro: SmsMessage?): String {
    if (otro == null) return "Ya lo anotaste con otro aviso de este pago."
    val canal = canalLegibleDelAviso(otro.bank)
    val deQuien = if (canal == "SMS") "el SMS" else if (canal == "Correo") "el correo" else "el aviso de $canal"
    val hora = horaLegibleDeSms(otro.time) ?: return "Ya lo anotaste con $deQuien."
    val articulo = if (hora.startsWith("1:")) "la" else "las"
    val cierre = if (hora.endsWith(".")) "" else "."
    return "Ya lo anotaste con $deQuien de $articulo $hora$cierre"
}

/**
 * **Los avisos de un pago, en el orden en que llegaron**, sacados de la lista que ya se tiene a mano
 * con [SmsMessage.miembrosDelGrupo]. Los que no estén en la lista (no debería pasar) se omiten.
 */
internal fun avisosDelPago(cabeza: SmsMessage, mensajes: List<SmsMessage>): List<SmsMessage> {
    val porId = mensajes.associateBy { it.id }
    return cabeza.miembrosDelGrupo.mapNotNull { porId[it] }.ifEmpty { listOf(cabeza) }
}

/**
 * **La bandeja con lo que el dueño ya resolvió acá**, mientras la relectura no llega (o falla):
 * los avisos de [resueltos] toman ese estado, y los de [separados] dejan de ser parte de un pago.
 * Un pago que se queda con un solo aviso deja de serlo también. Sin esto, «No son el mismo pago»
 * no cambiaría nada a la vista hasta la próxima lectura.
 */
fun conLoResueltoEnLaBandeja(
    mensajes: List<SmsMessage>,
    resueltos: Map<String, String>,
    separados: Set<String>,
): List<SmsMessage> {
    if (resueltos.isEmpty() && separados.isEmpty()) return mensajes
    fun suelto(sms: SmsMessage) = sms.copy(grupoId = null, miembrosDelGrupo = emptyList(), parecidoA = null, yaAnotadoCon = null)
    val conEstado = mensajes.map { sms -> resueltos[sms.id]?.let { sms.copy(state = it) } ?: sms }
    return conEstado.map { sms ->
        if (sms.grupoId == null) return@map sms
        if (sms.id in separados) return@map suelto(sms)
        val quedan = sms.miembrosDelGrupo.filter { it !in separados }
        if (quedan.size < 2) suelto(sms) else sms.copy(miembrosDelGrupo = quedan)
    }
}

/**
 * **La cuenta de un pago avisado varias veces**: se resuelve con cada aviso y gana la mejor
 * lectura —la elegida a mano, después la que salió del número escrito, después la del banco, y
 * recién al final la de por defecto—. Así un pago por QR toma la cuenta *8133 del SMS aunque la
 * propuesta (el comercio) salga de Google Wallet. A igualdad, el primero de [avisos] (la propuesta).
 */
internal fun resolverCuentaDelPago(
    accounts: List<com.jvillada.movi.shared.model.Account>,
    uso: com.jvillada.movi.shared.model.UsoDeCuenta,
    avisos: List<SmsMessage>,
    elegidaAMano: String?,
): com.jvillada.movi.ui.components.CuentaDelBanco {
    val lecturas = avisos.map { aviso ->
        com.jvillada.movi.ui.components.resolverCuentaDelBanco(
            accounts = accounts, uso = uso, banco = aviso.bank, elegidaAMano = elegidaAMano, textoDelMensaje = aviso.text,
        )
    }
    val orden = listOf(
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.A_MANO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.POR_EL_NUMERO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.POR_EL_BANCO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.POR_DEFECTO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.NINGUNA,
    )
    return lecturas.minByOrNull { orden.indexOf(it.origen).let { i -> if (i < 0) orden.size else i } }
        ?: com.jvillada.movi.ui.components.CuentaDelBanco(null, com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.NINGUNA)
}

/** Los textos de la tarjeta del mismo pago, para las pruebas. */
const val NO_SON_EL_MISMO_PAGO: String = "No son el mismo pago"
const val ESTE_ES_OTRO_PAGO: String = "Este es otro pago"
const val TAG_TARJETA_DEL_MISMO_PAGO: String = "tarjeta-del-mismo-pago"

/**
 * **Un pago avisado varias veces, como una sola tarjeta** de «Por revisar»: cuántas veces y por
 * dónde llegó, los textos plegados (se abren con «Ver los N avisos»), y las acciones del pago
 * entero —«Revisar», «Ignorar», «No son el mismo pago»—. Si uno de los avisos ya se confirmó, el
 * pago ya tiene su movimiento: lo dice, y en vez de «Revisar» ofrece «Cerrar».
 */
@Composable
internal fun TarjetaDelMismoPago(
    avisos: List<SmsMessage>,
    onRevisar: () -> Unit,
    onIgnorar: () -> Unit,
    onNoSonElMismoPago: () -> Unit,
    onCerrar: () -> Unit,
    trabajando: Boolean = false,
) {
    var abiertos by remember { mutableStateOf(false) }
    val yaAnotadoCon = avisos.firstNotNullOfOrNull { it.yaAnotadoCon }?.let { id -> avisos.firstOrNull { it.id == id } }
    val anotado = yaAnotadoCon != null || avisos.any { it.state == SMS_STATE_CONFIRMED }
    val primero = avisos.first()
    MinCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag(TAG_TARJETA_DEL_MISMO_PAGO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                tituloDelMismoPago(avisos.size),
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.weight(1f),
            )
            Text(
                fechaCortaDeSms(primero.time, hoyEnAppZone()),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(canalesDelMismoPago(avisos), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        if (anotado) {
            Spacer(Modifier.height(8.dp))
            Text(avisoYaAnotado(yaAnotadoCon), style = Movi.textos.apoyo, color = Movi.colores.aviso, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        // El texto del primero siempre; los demás, plegados: dicen lo mismo con otras palabras.
        TextoDelAviso(primero)
        if (abiertos) {
            avisos.drop(1).forEach { aviso ->
                Spacer(Modifier.height(10.dp))
                TextoDelAviso(aviso)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (abiertos) "Ocultar los otros avisos" else "Ver los ${avisos.size} avisos",
            style = Movi.textos.apoyo,
            color = Movi.colores.marca,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(role = Role.Button) { abiertos = !abiertos }.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (anotado) {
                PastillaDelPago("Cerrar", principal = true, habilitada = !trabajando, onClick = onCerrar)
            } else {
                PastillaDelPago("Revisar", principal = true, habilitada = !trabajando, onClick = onRevisar)
                PastillaDelPago("Ignorar", habilitada = !trabajando, onClick = onIgnorar)
            }
            PastillaDelPago(NO_SON_EL_MISMO_PAGO, habilitada = !trabajando, onClick = onNoSonElMismoPago)
        }
    }
}

/** Un aviso dentro de la tarjeta: por dónde y cuándo llegó, y su texto con lo importante en negrita. */
@Composable
internal fun TextoDelAviso(aviso: SmsMessage, accion: (@Composable () -> Unit)? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${canalLegibleDelAviso(aviso.bank)} · ${horaLegibleDeSms(aviso.time) ?: aviso.time}",
                style = Movi.textos.rotulo.copy(letterSpacing = 0.8.sp),
                color = Movi.colores.textoMedio,
                modifier = Modifier.weight(1f),
            )
            accion?.invoke()
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Movi.colores.hilo))
            Text(
                resaltadoDelTextoDelBanco(aviso.text),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun PastillaDelPago(texto: String, habilitada: Boolean, onClick: () -> Unit, principal: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (principal) Modifier.background(Movi.colores.texto)
                else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp)),
            )
            .clickable(enabled = habilitada, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyo,
            color = if (principal) Movi.colores.fondo else Movi.colores.texto,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
