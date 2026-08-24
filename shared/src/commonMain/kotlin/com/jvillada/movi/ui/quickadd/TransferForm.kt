package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.validateTransfer
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MinExpense
import com.jvillada.movi.theme.MinOnPrimaryContainer
import com.jvillada.movi.theme.MinPrimaryContainer
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.credits.filterDateInput
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * "AAAA-MM-DD" → el instante del **mediodía** de ese día en la zona de la app (Bogotá), o `null`
 * si la fecha no sirve.
 *
 * Mediodía y no medianoche a propósito: los timestamps se guardan en epoch-ms y cada pantalla los
 * vuelve a fechar en su zona. Un traspaso sellado a las 00:00 de Bogotá cae, mirado en UTC, a las
 * 05:00 del mismo día — pero uno sellado a las 00:00 UTC (que es lo que da una conversión
 * descuidada) se ve como las 7 pm del día ANTERIOR en Bogotá, y el movimiento aparece un día
 * antes de cuando pasó. El mediodía deja doce horas de margen para los dos lados: ninguna zona
 * razonable lo corre de día.
 *
 * Acepta la barra como separador por el mismo motivo que `filterDateInput` (F24): «2026/08/23» es
 * lo que mucha gente escribe, y rechazarlo dejaba el botón en gris sin explicar por qué.
 */
fun transferTimestampFor(date: String, zone: TimeZone = AppTimeZone.zone): Long? {
    val normalized = date.trim().replace('/', '-')
    val parsed = runCatching { LocalDate.parse(normalized) }.getOrNull() ?: return null
    // Mismo rango razonable que la fecha de desembolso de un crédito: un año de tres dígitos es
    // casi siempre un tipeo a medio terminar, no una intención.
    if (parsed.year !in 2000..2100) return null
    return parsed.atTime(12, 0).toInstant(zone).toEpochMilliseconds()
}

/** Hoy, en la zona de la app, como "AAAA-MM-DD" — el valor con el que arranca el campo. */
fun todayIsoInAppZone(clock: Clock = Clock.System, zone: TimeZone = AppTimeZone.zone): String =
    clock.now().toLocalDateTime(zone).date.toString()

/**
 * Lo primero que falta para poder guardar el traspaso, o `null` si no falta nada. Mismo patrón
 * que `missingFieldMessage` en la hoja de movimiento (F24): una sola frase, la más urgente.
 *
 * Las reglas del traspaso van **antes** que la fecha porque son el problema de fondo: si el
 * origen y el destino son la misma cuenta, arreglar la fecha no destraba nada. Y el texto sale de
 * [validateTransfer] (:core), el mismo que devuelve el server en su 422 — así la hoja y el
 * rechazo del server nunca dicen cosas distintas del mismo problema.
 */
fun transferMissingMessage(from: Account?, to: Account?, amount: Long, date: String): String? =
    validateTransfer(from, to, amount)
        ?: if (transferTimestampFor(date) == null) "La fecha tiene que ser AAAA-MM-DD" else null

/**
 * Los tres ids del traspaso que se está escribiendo. **Se generan una vez por borrador, no una
 * vez por toque de Guardar.**
 *
 * El escenario que esto arregla: el server commitea las dos patas, la respuesta se pierde
 * (timeout, cambio de red, la app al fondo), el dueño ve «revisa tu conexión» y vuelve a tocar
 * Guardar. Si el segundo intento lleva ids nuevos, el server no tiene cómo saber que es el mismo
 * traspaso y crea uno segundo entero: origen −2×monto, destino +2×monto, y el dueño con dos
 * renglones idénticos que no pidió. Con los mismos ids, el INSERT choca contra la PK de las patas,
 * el server relee y —si las dos están— devuelve el traspaso real con 200, o sea que el reintento
 * termina por el camino normal, sin error que mostrar.
 *
 * Se renuevan **solo después de un éxito**: recién ahí el traspaso siguiente es otro traspaso.
 */
data class TransferDraftIds(
    val transferId: String,
    val fromEventId: String,
    val toEventId: String,
) {
    companion object {
        fun new() = TransferDraftIds(
            transferId = newId("tr"),
            fromEventId = newId("ev"),
            toEventId = newId("ev"),
        )
    }
}

/** El pedido que se manda al server. Función aparte para que un reintento sea, literalmente, el mismo pedido. */
fun transferRequestFor(
    ids: TransferDraftIds,
    from: Account,
    to: Account,
    amount: Long,
    timestamp: Long,
    note: String,
): CreateTransferRequest = CreateTransferRequest(
    transferId = ids.transferId,
    fromEventId = ids.fromEventId,
    toEventId = ids.toEventId,
    fromAccountId = from.id,
    toAccountId = to.id,
    amount = amount,
    timestamp = timestamp,
    note = note.trim().ifBlank { null },
)

/**
 * ¿Este fallo de `createTransfer` es en realidad «el traspaso ya está guardado»?
 *
 * Red de seguridad, no el camino habitual: este server relee las patas ante el choque de PK y
 * devuelve el traspaso real con 200, así que un reintento sale por el camino de éxito. El 409
 * queda contemplado para un server anterior a ese cambio (una versión vieja todavía desplegada):
 * también significa que las dos patas existen, y mostrarlo como error sería mentirle al dueño y
 * empujarlo a tocar Guardar una tercera vez.
 */
fun isAlreadyRegistered(error: Throwable): Boolean =
    error is ApiException && error.status == 409

/**
 * Cuentas donde tiene sentido mover plata: todo lo que no sea del grupo DEUDA.
 *
 * Se filtran del selector y no solo de la validación: ofrecer una tarjeta para después decir que
 * no se puede es peor que no ofrecerla. Pagar la tarjeta y abonar a un préstamo ya tienen su
 * propio camino en Créditos, con su propia regla de flujo de caja.
 */
fun transferableAccounts(accounts: List<Account>): List<Account> =
    accounts.filter { it.type.group != AccountGroup.DEUDA }

/**
 * El cuerpo de la pestaña **Traspaso** de la hoja de Agregar.
 *
 * Es una hoja aparte de [EditorBody] y no un modo más del mismo formulario: un traspaso no tiene
 * categoría (la suya es reservada y nadie la elige) ni tipo (es siempre salida de una cuenta y
 * entrada a otra), y en cambio necesita dos cuentas en vez de una. Compartir el formulario habría
 * significado media docena de `if (esTraspaso)` adentro de cada fila.
 */
@Composable
internal fun TransferBody(
    accounts: List<Account>,
    accountsLoaded: Boolean,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val elegibles = remember(accounts) { transferableAccounts(accounts) }

    var fromId by remember(elegibles) { mutableStateOf(elegibles.firstOrNull()?.id) }
    var toId by remember(elegibles) { mutableStateOf(elegibles.getOrNull(1)?.id) }
    var amount by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(todayIsoInAppZone()) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<TransferSide?>(null) }
    // Los ids viven en el borrador, NO adentro de `save()`: un reintento tras un fallo tiene que
    // llevar los mismos, o el server crea un traspaso duplicado (ver [TransferDraftIds]).
    var ids by remember { mutableStateOf(TransferDraftIds.new()) }

    val from = elegibles.firstOrNull { it.id == fromId }
    val to = elegibles.firstOrNull { it.id == toId }
    val missing = transferMissingMessage(from, to, amount ?: 0L, date)
    val canSave = missing == null && !saving

    fun save() {
        val origen = from ?: return
        val destino = to ?: return
        val timestamp = transferTimestampFor(date) ?: return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.createTransfer(
                    transferRequestFor(ids, origen, destino, amount ?: 0L, timestamp, note),
                )
            }
            saving = false
            result
                .onSuccess {
                    // Ids nuevos recién ACÁ: el traspaso siguiente es otro traspaso. Mientras el
                    // anterior no haya llegado, cada reintento tiene que ser el mismo pedido.
                    ids = TransferDraftIds.new()
                    onSaved()
                }
                .onFailure { fallo ->
                    // Un 409 no es un fallo: quiere decir que el traspaso YA quedó registrado
                    // (esta es la respuesta del server a un reintento con los mismos ids). Se
                    // cierra la hoja igual que en el camino feliz — decirle «revisa tu conexión»
                    // a alguien cuyo traspaso sí se guardó es lo que lo empuja a guardarlo otra vez.
                    if (isAlreadyRegistered(fallo)) {
                        ids = TransferDraftIds.new()
                        onSaved()
                    } else {
                        error = fallo.toUserMessage()
                    }
                }
        }
    }

    // Ola 8 · V2 (N4 de la revisión) — la pestaña Traspaso recibe LOS MISMOS DOS ARREGLOS.
    //
    // Se los había perdido por un detalle de estructura: este `TransferAccountPicker` y su
    // `return` viven ADENTRO de la rama `Picker.None` de QuickAddScreen, así que el `Box` de
    // alto fijado que arregla los sub-pickers de Gasto/Ingreso no llegaba hasta acá. El bug
    // original seguía intacto una pestaña más allá: abrir «Desde» encogía la hoja de altura
    // completa a una franja, y cerrarla la estiraba de golpe.
    //
    // Mismo remedio, aplicado localmente: se mide el alto del formulario y se le pone como
    // mínimo al picker de cuentas, así la hoja no cambia de alto al elegir una cuenta.
    var formHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (picking == null) Modifier
                else Modifier.heightIn(min = with(density) { formHeightPx.toDp() }),
            ),
    ) {
        if (picking != null) {
            TransferAccountPicker(
                title = if (picking == TransferSide.FROM) "Desde" else "Hacia",
                accounts = elegibles,
                selectedId = if (picking == TransferSide.FROM) fromId else toId,
                onPick = { id ->
                    if (picking == TransferSide.FROM) fromId = id else toId = id
                    picking = null
                },
                onClose = { picking = null },
            )
        } else {
        Column(modifier = Modifier.fillMaxWidth().onSizeChanged { formHeightPx = it.height }) {

        if (accountsLoaded && elegibles.size < 2) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Un traspaso necesita dos cuentas tuyas. Las tarjetas y los préstamos no cuentan: " +
                    "esos se manejan en Créditos.",
                fontSize = 13.sp,
                color = MinTextMute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
        return@Column
        }

        Spacer(Modifier.height(18.dp))

        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        ) {
            CardRow(
                left = { Text("Desde", fontSize = 14.5.sp, color = MinTextMute) },
                right = {
                    Text(
                        text = from?.name ?: "Elegir cuenta",
                        fontSize = 14.5.sp,
                        color = if (from == null) MinTextFaint else MinText,
                        fontWeight = FontWeight.Medium,
                    )
                },
                showChevron = true,
                onClick = { picking = TransferSide.FROM },
            )
            CardRow(
                left = { Text("Hacia", fontSize = 14.5.sp, color = MinTextMute) },
                right = {
                    Text(
                        text = to?.name ?: "Elegir cuenta",
                        fontSize = 14.5.sp,
                        color = if (to == null) MinTextFaint else MinText,
                        fontWeight = FontWeight.Medium,
                    )
                },
                showChevron = true,
                isLast = true,
                onClick = { picking = TransferSide.TO },
            )
        }

        Spacer(Modifier.height(16.dp))
        MoneyField(value = amount, onValueChange = { amount = it }, label = "MONTO")

        Spacer(Modifier.height(14.dp))
        Text("FECHA", fontSize = 11.sp, color = MinTextMute, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        FieldBox("AAAA-MM-DD", date, onValueChange = { date = filterDateInput(it) })

        Spacer(Modifier.height(14.dp))
        Text("NOTA (OPCIONAL)", fontSize = 11.sp, color = MinTextMute, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        FieldBox("Concepto del traspaso", note, onValueChange = { note = it })

        // Ola 8 · V2 (N4): igual que el error del editor de un movimiento — alto reservado,
        // dos renglones, para que un fallo de red no corra el botón de guardar bajo el dedo.
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            if (error != null) {
                Text(error!!, fontSize = 12.sp, color = MinExpense)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                .clickable(enabled = canSave) { save() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (saving) "Guardando…" else "Guardar traspaso",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
            )
        }

        // Ola 8 · V2 (N4): este renglón vive DEBAJO del botón y aparecía/desaparecía al
        // escribir el monto, bajando el formulario entero ~40 dp en una hoja anclada abajo.
        // Mismo criterio que «Falta el monto» en el editor de un movimiento: reserva su alto.
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            if (!saving && missing != null) {
                Text(
                    text = missing,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Un traspaso exige conexión (ver LocalRepository.createTransfer): las dos patas nacen juntas
        // en la transacción del server o no nacen. Decirlo acá, antes de intentarlo, es más honesto
        // que dejar que falle y mostrar "revisa tu conexión" como si fuera un imprevisto.
        Spacer(Modifier.height(10.dp))
        Text(
            text = "El traspaso se guarda en línea: las dos puntas se registran juntas o no se registra ninguna.",
            fontSize = 11.sp,
            color = MinTextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        } // Column del formulario
        } // else de `picking != null`
    } // Box del alto fijado
}

internal enum class TransferSide { FROM, TO }

@Composable
private fun TransferAccountPicker(
    title: String,
    accounts: List<Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader(title, onClose)
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            accounts.forEach { account ->
                CardRow(
                    left = {
                        Text(
                            text = account.name,
                            fontSize = 14.5.sp,
                            color = MinText,
                            fontWeight = if (account.id == selectedId) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    right = {
                        if (account.id == selectedId) {
                            Text("Elegida", fontSize = 12.sp, color = MinTextMute)
                        }
                    },
                    onClick = { onPick(account.id) },
                    isLast = account.id == accounts.last().id,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
