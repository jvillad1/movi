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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import com.jvillada.movi.data.ReminderChannelsCache
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.DEFAULT_REMINDER_LEAD_DAYS
import com.jvillada.movi.shared.model.ReminderChannels
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
 * Es cierto y accionable solo cuando hay al menos un recordatorio PEDIDO, **el server no tiene
 * canal de correo** y las notificaciones push tampoco están activas.
 *
 * ## La corrección: el cliente no puede afirmar que no hay canal si nunca preguntó
 *
 * Esta función miraba SOLO `pushStatus`, o sea el permiso de notificaciones del navegador, y de
 * ahí concluía «no hay otro canal activo». En producción esa conclusión era falsa: `RESEND_API_KEY`
 * está puesta, `ReminderScheduler` arranca y el correo sale a la dirección del dueño. El aviso le
 * anunciaba que algo no iba a funcionar cuando sí funcionaba, y lo empujaba a activar
 * notificaciones que no necesitaba. Un aviso que se equivoca del lado alarmista se termina
 * ignorando, y entonces no sirve el día que sí haga falta.
 *
 * Ahora entra [canales] ([com.jvillada.movi.shared.model.ReminderChannels], lo que contesta
 * `GET /api/reminders/channels`) y **`null` significa «todavía no se sabe»**, no «no hay nada»:
 * con `null` no se muestra el aviso. Es deliberado y es el corazón del arreglo — callarse mientras
 * no se sabe es peor que asustar en falso, pero muchísimo menos peor.
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
 *
 * **DEFERIDO (no resuelto, y no cerrado):** si el navegador dice "enabled" pero el server ya no
 * tiene claves VAPID (`canales.push == false`), el push tampoco saldría y acá no se avisa nada.
 * El estado se detecta —`canales.push` ya viene en el wire justamente para esto— pero falta lo
 * que hay que decir: los cuatro textos del cartel hablan de notificaciones apagadas y ninguno
 * sería cierto ahí, así que arreglarlo es **escribir un quinto texto**, no reusar uno.
 *
 * Que hoy se calle NO es la conclusión de esta rama, que sostiene lo contrario (callarse es
 * peor). Es lo que quedó afuera por alcance: llegar a este estado exige borrarle las claves al
 * server DESPUÉS de que alguien ya se suscribió, y estrenar un texto nuevo para él, sin poder
 * reproducirlo a ojo, era peor que dejarlo escrito. Queda pendiente, no decidido.
 */
fun shouldShowReminderWarning(
    pushStatus: String,
    hayRecordatoriosPedidos: Boolean,
    canales: ReminderChannels?,
): Boolean =
    hayRecordatoriosPedidos &&
        canales != null && !canales.email &&
        pushStatus != "enabled" && pushStatus != "unsupported"

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
fun shouldShowReminderOptInWarning(
    pushStatus: String,
    remindMe: Boolean,
    canales: ReminderChannels?,
): Boolean = shouldShowReminderWarning(pushStatus, hayRecordatoriosPedidos = remindMe, canales = canales)

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
 * Lo que dice la línea chica debajo de la casilla, **con el canal incluido cuando se lo conoce**.
 *
 * Es la otra mitad del arreglo. El aviso ámbar dejó de mentir por no preguntar; esta línea usa la
 * misma respuesta para decir algo útil en lugar de nada: por dónde va a salir el aviso.
 *
 * Cuatro estados, y ninguno afirma más de lo que se sabe:
 *
 * 1. **No se sabe todavía** (`canales == null`, la llamada no volvió o falló) → solo cuándo se
 *    avisa. No se nombra ningún canal.
 * 2. **Sin correo** → igual que arriba. Lo que falta lo dice el cartel ámbar, que en este estado
 *    sí corresponde.
 * 3. **Con correo** → «…, por correo a juan@…». Es el dato que el dueño no tenía y por el que
 *    llegó a creer que sus recordatorios no existían.
 * 4. **Con correo de prueba** (`emailSandbox`: `REMINDER_FROM` es un `@resend.dev`) → lo mismo,
 *    más una segunda línea. Resend, sin dominio verificado, **solo entrega a la dirección dueña
 *    de la cuenta**: para esa persona el correo llega, para cualquier otra no, y el server no
 *    tiene forma de saber cuál es. Se elige decir a quién alcanza en vez de (a) prometerle a
 *    todos que llega o (b) volver a decirle al dueño que no le va a llegar, que es la mentira que
 *    esta rama vino a sacar.
 *
 * Devuelve una o dos líneas para que la hoja las apile con el mismo estilo, sin `if` de layout.
 */
fun reminderDeliveryLines(canales: ReminderChannels?, leadDays: Int): List<String> {
    val cuando = reminderLeadHint(leadDays)
    if (canales == null || !canales.email) return listOf(cuando)
    val destino = canales.emailTo?.trim().orEmpty()
    val principal =
        if (destino.isEmpty()) cuando.trimEnd('.') + ", por correo."
        else cuando.trimEnd('.') + ", por correo a $destino."
    return if (canales.emailSandbox) {
        listOf(
            principal,
            "Por ahora el correo solo llega a la dirección con la que se configuró Movi.",
        )
    } else {
        listOf(principal)
    }
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
    // Qué canales tiene el server. Se pregunta acá —y no en cada hoja— porque esta casilla es el
    // único lugar donde se hace la promesa, y son tres las hojas que la usan (recurrentes,
    // créditos, tarjetas). [ReminderChannelsCache.cargar] es idempotente: una sola llamada por
    // sesión aunque se abran las tres.
    val canales = ReminderChannelsCache.canales
    LaunchedEffect(Unit) { ReminderChannelsCache.cargar() }
    // El número de días lo manda el server si lo sabemos; el parámetro queda como respaldo.
    // Antes se prometía «3 días antes» aunque `REMINDER_LEAD_DAYS` dijera otra cosa.
    val diasEfectivos = canales?.leadDays ?: leadDays

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
                // Con la casilla marcada, una línea por cosa que se sabe (ver
                // [reminderDeliveryLines]): cuándo se avisa y —cuando el server lo dijo— por
                // dónde. Con la casilla desmarcada no hay nada que prometer.
                val lineas =
                    if (checked) reminderDeliveryLines(canales, diasEfectivos)
                    else listOf("No te vamos a avisar de este pago.")
                lineas.forEachIndexed { i, linea ->
                    if (i > 0) Spacer(Modifier.height(2.dp))
                    Text(
                        text = linea,
                        fontSize = 12.sp,
                        color = MinTextMute,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        if (shouldShowReminderOptInWarning(pushStatus, checked, canales)) {
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
            // Ícono, no el carácter "✓": la fuente del canvas no lo trae y en el navegador la
            // casilla marcada se veía como un cuadradito vacío — o sea, justo lo contrario de
            // lo que quiere decir. (Se vio en la PWA, en «Nuevo recurrente».)
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MinOnPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
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
