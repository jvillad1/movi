package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.DEFAULT_REMINDER_LEAD_DAYS
import com.jvillada.movi.theme.MinBorder
import com.jvillada.movi.theme.MinOnPrimaryContainer
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinPrimaryContainer
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextDim
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.theme.MinWarn
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant

/**
 * Decide si la pantalla de Recurrentes debe mostrar el aviso de
 * "tus recordatorios no te van a llegar".
 *
 * Es cierto y accionable solo cuando hay al menos un recordatorio PEDIDO Y las
 * notificaciones push no están activas.
 *
 * «Pedido», y no «hay algo en Próximos»: el aviso promete que no va a llegar algo que
 * el dueño espera, así que si nadie pidió que le avisen no hay promesa rota que anunciar.
 * Un recurrente de INGRESO lo dejaba en evidencia — el sueldo nunca genera recordatorio
 * (el barrido solo mira gastos, ver `selectDueForReminder`) y la hoja ni siquiera ofrece
 * la casilla, pero la tarjeta ámbar salía igual diciendo que no podíamos avisar de «estos
 * pagos». Quien llama filtra por tipo Gasto Y `remindMe`. `pushStatus` viene de
 * [com.jvillada.movi.platform.PushOptIn.status]: "enabled" | "disabled" | "denied" | "unsupported".
 *
 * - "enabled"     -> ya funciona, no hay nada que avisar.
 * - "unsupported" -> la plataforma (iOS/Android nativo hoy) no tiene ninguna
 *                    instrucción posible para el usuario, así que tampoco se avisa.
 * - "disabled" / "denied" -> el aviso aplica; RecurrentesScreen distingue esos dos
 *                    para mostrar una acción ("Activar") o una instrucción
 *                    ("reactiva en el navegador"), pero ambos cuentan como "avisar".
 */
fun shouldShowReminderWarning(pushStatus: String, hayRecordatoriosPedidos: Boolean): Boolean =
    hayRecordatoriosPedidos && pushStatus != "enabled" && pushStatus != "unsupported"

/**
 * La misma pregunta, hecha desde la hoja de crear/editar en vez de desde la pantalla.
 *
 * Ahí lo que dispara el aviso no es "hay pagos próximos" sino "acabas de pedir que te
 * recordemos": si la casilla queda marcada y no hay canal por el que avisar, la casilla estaría
 * prometiendo algo que no va a pasar. Con la casilla desmarcada no hay promesa que romper, así
 * que no se advierte nada.
 *
 * Delega en [shouldShowReminderWarning] a propósito — la regla de qué cuenta como "hay canal"
 * es una sola y vive en un solo lugar.
 */
fun shouldShowReminderOptInWarning(pushStatus: String, remindMe: Boolean): Boolean =
    shouldShowReminderWarning(pushStatus, hayRecordatoriosPedidos = remindMe)

/**
 * La línea chica debajo de la casilla: *cuántos* días antes avisa el barrido.
 *
 * El número sale de [DEFAULT_REMINDER_LEAD_DAYS], el mismo que usa el server como fallback de
 * `REMINDER_LEAD_DAYS`. Con 0 la frase cambia entera en vez de decir "0 días antes", que sería
 * falso: con 0 el aviso sale el mismo día del vencimiento.
 */
fun reminderLeadHint(leadDays: Int): String = when {
    leadDays <= 0 -> "Te avisamos el día del vencimiento."
    leadDays == 1 -> "Te avisamos 1 día antes del vencimiento."
    else -> "Te avisamos $leadDays días antes del vencimiento."
}

/**
 * Casilla «Recordarme unos días antes», marcada por defecto, para las hojas de crear/editar un
 * recurrente, un crédito o una tarjeta.
 *
 * Es una casilla y no un diálogo aparte: preguntar con un modal interrumpiría el alta para algo
 * que casi siempre se responde que sí. Al editar refleja el valor guardado.
 *
 * Con la casilla marcada y sin ningún canal activo aparece, debajo, el MISMO aviso que ya muestra
 * la pantalla de Recurrentes ([ReminderWarningBanner]) — no una versión distinta ni un canal
 * inventado. Una casilla que promete un recordatorio que nadie va a entregar es peor que no
 * ofrecerla.
 */
@Composable
fun ReminderOptInField(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadDays: Int = DEFAULT_REMINDER_LEAD_DAYS,
) {
    var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
    var pushRefreshTick by remember { mutableStateOf(0) }

    if (PushOptIn.supported) {
        LaunchedEffect(pushRefreshTick) {
            // Mismo gate y misma cadencia que RecurrentesScreen: el permiso del navegador se
            // resuelve async, así que se relee un rato para que el aviso desaparezca solo.
            repeat(20) {
                kotlinx.coroutines.delay(600)
                pushStatus = PushOptIn.status()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CheckBox(checked = checked)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recordarme unos días antes",
                    fontSize = 14.sp,
                    color = MinText,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (checked) reminderLeadHint(leadDays) else "No te vamos a avisar de este pago.",
                    fontSize = 12.sp,
                    color = MinTextMute,
                    lineHeight = 16.sp,
                )
            }
        }

        if (shouldShowReminderOptInWarning(pushStatus, checked)) {
            Spacer(Modifier.height(10.dp))
            ReminderWarningBanner(
                pushStatus = pushStatus,
                onEnable = {
                    PushOptIn.enable()
                    pushRefreshTick++
                },
                source = ReminderWarningSource.OPT_IN_CHECKBOX,
            )
        }
    }
}

/** Casilla cuadrada, dibujada a mano para no arrastrar el Checkbox de Material a estas hojas. */
@Composable
private fun CheckBox(checked: Boolean) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(if (checked) Modifier else Modifier.border(1.dp, MinBorder, RoundedCornerShape(6.dp))),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", fontSize = 12.sp, color = MinOnPrimaryContainer, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * De dónde sale el aviso. Cambia SOLO el texto, no la decisión: la pantalla habla de los pagos
 * que ya están cargados, la casilla habla del recordatorio que se acaba de pedir. Con un texto
 * único, la casilla diría "tienes pagos próximos" mientras el dueño está creando el primero —
 * exactamente el tipo de afirmación falsa que este aviso existe para evitar.
 */
enum class ReminderWarningSource { UPCOMING_LIST, OPT_IN_CHECKBOX }

/**
 * Aviso de "tus recordatorios no te van a llegar", con la acción de activar notificaciones
 * cuando la hay.
 *
 * Vive acá y no dentro de `RecurrentesScreen` porque lo usan dos lugares: la pantalla (cuando
 * hay pagos próximos y no hay canal) y [ReminderOptInField] (cuando la casilla queda marcada y
 * no hay canal). Duplicarlo habría dejado dos textos que se pueden desincronizar.
 */
@Composable
fun ReminderWarningBanner(
    pushStatus: String,
    onEnable: () -> Unit,
    source: ReminderWarningSource = ReminderWarningSource.UPCOMING_LIST,
) {
    val denied = pushStatus == "denied"
    val optIn = source == ReminderWarningSource.OPT_IN_CHECKBOX
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MinWarn),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (optIn) "Este recordatorio no te va a llegar" else "Tus recordatorios no te van a llegar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        denied && optIn ->
                            "Bloqueaste las notificaciones de Movi en el navegador, así que no hay por dónde avisarte. Reactívalas desde la configuración del sitio en tu navegador."
                        denied ->
                            "Bloqueaste las notificaciones de Movi en el navegador, así que no podemos avisarte de estos pagos. Reactívalas desde la configuración del sitio en tu navegador."
                        optIn ->
                            "Las notificaciones están apagadas y no hay otro canal activo, así que este aviso no te va a llegar. Actívalas para no perderte el vencimiento."
                        else ->
                            "Tienes pagos próximos, pero las notificaciones están apagadas y no hay otro canal activo para avisarte. Actívalas para no perderte un vencimiento."
                    },
                    fontSize = 12.5.sp,
                    color = MinTextDim,
                    lineHeight = 17.sp,
                )
                if (!denied) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Activar notificaciones",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinPrimary,
                        modifier = Modifier.clickable(onClick = onEnable),
                    )
                }
            }
        }
    }
}
