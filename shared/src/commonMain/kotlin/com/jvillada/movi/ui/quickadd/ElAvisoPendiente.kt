package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.AvisoPendienteParecido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import kotlinx.datetime.LocalDate

/*
 * # «¿Es el aviso de hace 2 h?» — Agregar ofrece el aviso pendiente
 *
 * La auditoría de la ingesta (4-oct-2026) encontró 34 movimientos tecleados en la app que ya tenían
 * su aviso del banco esperando en «Por revisar». El aviso se confirmaba después por separado (o se
 * quedaba ahí), y la categoría que el dueño eligió nunca se pegaba al comercio del banco: la memoria
 * no aprendía nada. Es el sentido contrario de «¿Ya lo anotaste?», que desde un aviso busca lo
 * anotado.
 *
 * Al teclear un monto que coincide con un aviso pendiente reciente (mismo monto, moneda y tipo, menos
 * de 48 h; lo busca el server, ver `AvisoPendienteParecido`), la hoja lo pregunta en el renglón que
 * ya reservaba su alto para el error —así el teclado no se mueve—. Si dice que sí, al guardar el
 * movimiento lleva el texto del banco y el aviso queda confirmado con él.
 */

/** «hace 5 min», «hace 2 h». Lo que la hoja ofrece llegó hace menos de 48 h. */
fun haceCuantoLlego(momento: Long, ahora: Long): String {
    val minutos = ((ahora - momento) / 60_000L).coerceAtLeast(0)
    return when {
        minutos < 1 -> "recién"
        minutos < 60 -> "hace $minutos min"
        else -> "hace ${minutos / 60} h"
    }
}

/** La pregunta de la hoja: «¿Es el aviso de hace 2 h? TOSTAO CAFE Y PAN». */
fun preguntaDelAvisoPendiente(aviso: AvisoPendienteParecido, ahora: Long): String =
    "¿Es el aviso de ${haceCuantoLlego(momentoDelSms(aviso.time, ahora), ahora)}? ${aviso.descripcion}"

/** Lo que dice la hoja cuando ya dijo que sí. */
fun avisoElegido(aviso: AvisoPendienteParecido, ahora: Long): String =
    "Va con el aviso de ${haceCuantoLlego(momentoDelSms(aviso.time, ahora), ahora)}"

/**
 * **El movimiento de la hoja, con el aviso.** Lo que el dueño escribió manda —categoría, cuenta, nota,
 * monto—; del aviso se toman tres cosas que él no tiene cómo saber:
 *
 * - `merchant`: el texto del banco que identifica al destinatario («Pago QR · llave 0047142708»). Es
 *   de donde aprende la memoria de categorías: el próximo aviso de esa llave ya sale con su categoría.
 * - `rawPayload`: el mensaje entero, como lo guarda un movimiento confirmado desde la bandeja (ver
 *   `movimientoConfirmadoDelSms`); con eso las cuentas de otros reconocen a quién fue.
 * - la hora: cuando llegó el aviso, que es cuando salió la plata (no cuando él se acordó de anotarlo).
 */
fun movimientoConElAviso(movimiento: FinancialEvent, aviso: AvisoPendienteParecido, ahora: Long): FinancialEvent =
    movimiento.copy(
        merchant = aviso.comercio,
        rawPayload = aviso.text,
        timestamp = momentoDelSms(aviso.time, ahora),
    )

/** El día en que llegó el aviso, en Bogotá: la fecha que la hoja muestra cuando dice «Es este». */
fun fechaDelAviso(aviso: AvisoPendienteParecido, ahora: Long): LocalDate =
    epochMillisToAppDate(momentoDelSms(aviso.time, ahora))

const val TAG_AVISO_PENDIENTE_EN_AGREGAR: String = "aviso-pendiente-en-agregar"
const val ES_ESTE_AVISO: String = "Es este"
const val NO_ES_ESTE_AVISO: String = "No"
const val QUITAR_EL_AVISO: String = "Quitar"

/**
 * El renglón del aviso: la pregunta con «Es este» / «No», o —si ya dijo que sí— que el movimiento
 * va con el aviso, con «Quitar». Cabe en una línea: va en el lugar de alto fijo del error.
 */
@Composable
internal fun FilaDelAvisoPendiente(
    texto: String,
    elegido: Boolean,
    onEsEste: () -> Unit,
    onNo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_AVISO_PENDIENTE_EN_AGREGAR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!elegido) PastillaDelAviso(ES_ESTE_AVISO, destacada = true, onClick = onEsEste)
        PastillaDelAviso(if (elegido) QUITAR_EL_AVISO else NO_ES_ESTE_AVISO, destacada = false, onClick = onNo)
    }
}

@Composable
private fun PastillaDelAviso(texto: String, destacada: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (destacada) Movi.colores.marca else Movi.colores.borde, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyo,
            color = if (destacada) Movi.colores.marca else Movi.colores.texto,
            fontWeight = FontWeight.Medium,
        )
    }
}
