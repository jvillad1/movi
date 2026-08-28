package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.LastAccountStore
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
import com.jvillada.movi.theme.MinBorder
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
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.fecha.timestampParaFecha
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Hoy, en la zona de la app, como "AAAA-MM-DD".
 *
 * Nació como el valor inicial del campo de fecha del traspaso; ese campo ya no existe (lo
 * reemplazó el selector) pero la función sigue viva y usada: Movimientos la usa para saber qué
 * día es «Hoy» al armar sus encabezados. Vive acá por historia, no por pertenencia.
 */
fun todayIsoInAppZone(clock: Clock = Clock.System, zone: TimeZone = AppTimeZone.zone): String =
    clock.now().toLocalDateTime(zone).date.toString()

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
 *
 * **Para que quede sin ambigüedad (revisión de la Ola 11): el `TransferRoutes` de hoy NUNCA
 * responde 409.** El reintento idempotente da 200 y un `transferId` reusado para otro traspaso da
 * 422 (`TRANSFER_ID_ALREADY_USED`), que sí es un error de verdad y se muestra como tal. O sea que
 * esta rama —y el `recordTransfer` que cuelga de ella— está muerta contra el server actual y
 * existe solo para el cliente que le pegue a uno viejo.
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
 * Igual que `FRACCION_VALOR_FILA` en la hoja de un movimiento, y por el mismo motivo. Un poco más
 * generoso porque acá las etiquetas son más cortas («Desde», «Hacia») y no llevan más que su
 * aviso debajo.
 */
private const val FRACCION_VALOR_FILA_TRASPASO = 0.6f

/**
 * **Qué cuenta proponer del otro lado del traspaso.**
 *
 * El recuerdo natural del destino es [LastAccountStore.lastTransferToId] — salvo cuando el origen
 * que quedó elegido ES esa cuenta. Ahí lo que el dueño está armando es el traspaso de vuelta
 * (siempre fue Bancolombia→Nequi y ahora puso Desde=Nequi), así que el destino que tiene sentido
 * proponerle es el origen viejo, no la primera cuenta del alfabeto.
 *
 * Sin esto, el par recordado quedaba excluido de su propia sugerencia: el destino se resolvía con
 * `ultima = lastTransferToId` y `excluir = origen`, que son la misma cuenta, y caía en `PRIMERA`.
 * Se veía —con su «Por defecto»—, pero era una trampa para el dedo rápido: dos toques y el
 * traspaso salía hacia una cuenta que nadie eligió.
 */
private fun destinoSugerido(origenElegido: String?): String? =
    if (origenElegido != null && origenElegido == LastAccountStore.lastTransferToId) {
        LastAccountStore.lastTransferFromId
    } else {
        LastAccountStore.lastTransferToId
    }

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
    presetAccountId: String? = null,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val elegibles = remember(accounts) { transferableAccounts(accounts) }

    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var origenFrom by remember { mutableStateOf(OrigenCuenta.NINGUNA) }
    var origenTo by remember { mutableStateOf(OrigenCuenta.NINGUNA) }

    /**
     * **Ola 11 — el traspaso también recuerda su par, y también deja de depender del orden.**
     *
     * Antes esto era `elegibles.firstOrNull()` y `elegibles.getOrNull(1)`, o sea las dos
     * primeras cuentas de una lista que nadie ordenaba: el mismo problema que la fila «Cuenta»
     * del editor, duplicado, y con la vuelta de tuerca de que acá **dos cuentas equivocadas**
     * salen mal de una y entran mal en otra.
     *
     * El par origen→destino se repite (siempre el mismo banco al mismo bolsillo), así que se
     * recuerda entero y aparte del de un movimiento — ver `LastAccountStore`, donde está escrito
     * por qué un traspaso no puede definir «la última cuenta usada» de un gasto.
     *
     * El destino se resuelve **excluyendo** el origen: no hay traspaso de una cuenta a sí misma,
     * y ofrecerlo para después bloquear el botón sería peor que no ofrecerlo.
     *
     * Corre cuando cambia la lista de cuentas (que llega después de que esta pestaña se compuso)
     * y respeta lo que el dueño haya elegido a mano, salvo que esa cuenta ya no exista.
     */
    LaunchedEffect(elegibles) {
        val origenFirme = origenFrom == OrigenCuenta.ELEGIDA && elegibles.any { it.id == fromId }
        if (!origenFirme) {
            val elegida = resolverCuenta(
                cuentas = elegibles,
                contexto = presetAccountId,
                ultima = LastAccountStore.lastTransferFromId,
            )
            fromId = elegida.id
            origenFrom = elegida.origen
        }
        val destinoFirme = origenTo == OrigenCuenta.ELEGIDA &&
            elegibles.any { it.id == toId } && toId != fromId
        if (!destinoFirme) {
            val elegida = resolverCuenta(
                cuentas = elegibles,
                ultima = destinoSugerido(fromId),
                excluir = fromId,
            )
            toId = elegida.id
            origenTo = elegida.origen
        }
    }

    var amount by remember { mutableStateOf<Long?>(null) }
    // Ola 13 — LA FECHA DEL TRASPASO SE ELIGE, NO SE ESCRIBE.
    //
    // Acá había un campo de texto donde se tecleaba «AAAA-MM-DD» a mano, con su
    // `filterDateInput` y su «La fecha tiene que ser AAAA-MM-DD» cuando no se acertaba. Era la
    // única forma de poner una fecha en toda la app, y dejarla convivir en la MISMA hoja con el
    // selector nuevo de Gasto/Ingreso habría sido peor que no tocar nada: dos maneras distintas
    // de decir lo mismo, a dos toques de distancia.
    //
    // Con el selector, `transferTimestampFor` y `transferMissingMessage` quedaron sin llamador y
    // se fueron con el campo: la rama «La fecha tiene que ser AAAA-MM-DD» de esa validación era
    // inalcanzable —el selector no puede producir una fecha inválida— y un mensaje de formato
    // sobre un formato correcto es peor que no tener mensaje. Lo que queda valida el traspaso en
    // sí ([validateTransfer], el mismo texto que devuelve el server en su 422).
    val hoy = remember { hoyEnAppZone() }
    var fecha by remember { mutableStateOf(hoy) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<TransferSide?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    // Los ids viven en el borrador, NO adentro de `save()`: un reintento tras un fallo tiene que
    // llevar los mismos, o el server crea un traspaso duplicado (ver [TransferDraftIds]).
    var ids by remember { mutableStateOf(TransferDraftIds.new()) }

    val from = elegibles.firstOrNull { it.id == fromId }
    val to = elegibles.firstOrNull { it.id == toId }
    val missing = validateTransfer(from, to, amount ?: 0L)
    val canSave = missing == null && !saving

    fun save() {
        val origen = from ?: return
        val destino = to ?: return
        // Con «Hoy» (el default) queda la hora real, como siempre; cualquier otro día va al
        // mediodía de Bogotá — ver [timestampParaFecha] y [epochAlMediodia].
        val timestamp = timestampParaFecha(fecha, hoy)
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
                    // Ola 11: el próximo traspaso arranca con este mismo par. Igual que en el
                    // editor de un movimiento, solo después de que el server lo confirmó.
                    LastAccountStore.recordTransfer(origen.id, destino.id)
                    onSaved()
                }
                .onFailure { fallo ->
                    // Un 409 no es un fallo: quiere decir que el traspaso YA quedó registrado
                    // (esta es la respuesta del server a un reintento con los mismos ids). Se
                    // cierra la hoja igual que en el camino feliz — decirle «revisa tu conexión»
                    // a alguien cuyo traspaso sí se guardó es lo que lo empuja a guardarlo otra vez.
                    if (isAlreadyRegistered(fallo)) {
                        ids = TransferDraftIds.new()
                        // El traspaso SÍ quedó registrado, así que este par cuenta como usado
                        // igual que en el camino feliz.
                        LastAccountStore.recordTransfer(origen.id, destino.id)
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
                if (picking == null && !pickingDate) Modifier
                else Modifier.heightIn(min = with(density) { formHeightPx.toDp() }),
            ),
    ) {
        if (pickingDate) {
            // Mismo sub-picker que la pestaña Gasto/Ingreso, adentro del mismo Box de alto
            // fijado: abrirlo no cambia el alto de la hoja.
            Column(modifier = Modifier.fillMaxWidth()) {
                PickerHeader("Fecha", onClose = { pickingDate = false })
                SelectorDeFecha(
                    seleccionada = fecha,
                    hoy = hoy,
                    onPick = { fecha = it; pickingDate = false },
                    enabled = !saving,
                )
                Spacer(Modifier.height(8.dp))
            }
        } else if (picking != null) {
            TransferAccountPicker(
                title = if (picking == TransferSide.FROM) "Desde" else "Hacia",
                accounts = elegibles,
                selectedId = if (picking == TransferSide.FROM) fromId else toId,
                onPick = { id ->
                    if (picking == TransferSide.FROM) {
                        fromId = id
                        origenFrom = OrigenCuenta.ELEGIDA
                        // Ola 11: si el destino era justo esa cuenta, se corre a otra en vez de
                        // dejar el formulario trabado en «Elige dos cuentas distintas» con el
                        // dueño teniendo que adivinar cuál de las dos filas arreglar. El cambio
                        // se ve —la fila «Hacia» pasa a decir otro nombre, con su aviso de que
                        // lo eligió la app— así que no es una decisión escondida.
                        if (toId == id) {
                            val reemplazo = resolverCuenta(
                                cuentas = elegibles,
                                // [destinoSugerido]: si la cuenta que acaba de pasar a origen era
                                // el destino habitual, lo que se propone es el traspaso de vuelta.
                                ultima = destinoSugerido(id),
                                excluir = id,
                            )
                            toId = reemplazo.id
                            origenTo = reemplazo.origen
                        }
                    } else {
                        toId = id
                        origenTo = OrigenCuenta.ELEGIDA
                    }
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
                left = {
                    Text("Desde", fontSize = 14.5.sp, color = MinTextMute)
                    // Ola 11: mismo aviso que la fila «Cuenta» del editor y por el mismo motivo
                    // —el valor por defecto es una decisión de la app y tiene que poder leerse
                    // antes de guardar—, con el mismo alto reservado para que elegir una cuenta
                    // no mueva el formulario bajo el dedo. Ver [avisoDeCuenta].
                    AvisoDeCuentaRow(avisoDeCuenta(origenFrom, elegibles.size), elegibles.size > 1)
                },
                right = {
                    Text(
                        text = from?.name ?: "Elegir cuenta",
                        fontSize = 14.5.sp,
                        color = if (from == null) MinTextFaint else MinText,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // Mismo techo que las filas del editor de un movimiento: sin él, un nombre de
                // cuenta largo se lleva la fila entera y parte «Desde» letra por letra (ver
                // `rightMaxFraction` en CardRow).
                rightMaxFraction = FRACCION_VALOR_FILA_TRASPASO,
                showChevron = true,
                onClick = { picking = TransferSide.FROM },
            )
            CardRow(
                left = {
                    Text("Hacia", fontSize = 14.5.sp, color = MinTextMute)
                    // Menos una: el destino no puede ser el origen, así que con dos cuentas el
                    // destino no tiene alternativa y no hay ninguna decisión que confesar —
                    // misma regla que «con una sola cuenta el aviso no dice nada».
                    AvisoDeCuentaRow(
                        avisoDeCuenta(origenTo, elegibles.size - 1),
                        elegibles.size - 1 > 1,
                    )
                },
                right = {
                    Text(
                        text = to?.name ?: "Elegir cuenta",
                        fontSize = 14.5.sp,
                        color = if (to == null) MinTextFaint else MinText,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                rightMaxFraction = FRACCION_VALOR_FILA_TRASPASO,
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
        // Misma caja que tenía el campo de texto (mismo alto, mismo borde, mismo lugar) pero se
        // toca en vez de escribirse: así el formulario no cambia de alto respecto de master y el
        // dedo la encuentra donde ya estaba.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerLow)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = !saving) { pickingDate = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text = etiquetaDeFecha(fecha, hoy),
                fontSize = 14.sp,
                color = MinText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

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
