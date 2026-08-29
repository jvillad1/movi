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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.LastAccountStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.TransferKind
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.transferKindFor
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
import com.jvillada.movi.ui.components.signedMoney
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
 * Cuentas que se pueden poner en cualquiera de las dos puntas: **todo menos las tarjetas de
 * crédito**.
 *
 * Se filtran del selector y no solo de la validación: ofrecer una tarjeta para después decir que
 * no se puede es peor que no ofrecerla. Pagar el extracto de la tarjeta se anota como un gasto
 * normal con la categoría «Pago de tarjeta» (ver [TRANSFER_CARD_BLOCKED]).
 *
 * **Ola 14 — los préstamos entran.** Antes esta lista sacaba el grupo DEUDA entero, y con eso se
 * iba el desembolso: un crédito nuevo entraba a Créditos como deuda y la plata que el banco
 * depositaba en la cuenta corriente no existía para Movi. Ahora los créditos aparecen en el
 * selector, en su propio grupo y al final (ver [TransferAccountPicker]), para las dos direcciones
 * reales — desembolso y abono extraordinario.
 */
fun transferableAccounts(accounts: List<Account>): List<Account> =
    accounts.filter { it.type != AccountType.CREDIT_CARD }

/**
 * De dónde puede salir la cuenta **preseleccionada** de cada lado: solo cuentas de dinero o
 * inversión, nunca un crédito.
 *
 * Es la mitad menos vistosa de dejar entrar los préstamos, y la que evita el accidente: si un
 * crédito pudiera quedar elegido solo —porque es el último que se usó, o porque quedó primero en
 * la lista—, dos toques distraídos anotarían un desembolso de $257.000.000 que nadie pidió. Un
 * crédito en un traspaso se elige **siempre con el dedo**; la app no lo propone jamás.
 *
 * Consecuencia buscada: quien tiene una sola cuenta de dinero y un crédito ve «Desde
 * Bancolombia» y «Hacia · Elegir cuenta», con el botón apagado hasta que elija. Antes ni siquiera
 * veía el formulario.
 */
fun defaultTransferAccounts(accounts: List<Account>): List<Account> =
    accounts.filter { it.type.group != AccountGroup.DEUDA }

/**
 * El renglón que dice **en qué queda la deuda del crédito** si se guarda este traspaso, o `null`
 * si ninguna punta es un crédito (o falta el monto).
 *
 * Es la pieza que hace que el error caro sea imposible de no ver. Un desembolso **sube** la
 * deuda, y el dueño que ya creó el crédito en Créditos con su deuda actual no tiene por qué
 * saber que registrarlo otra vez acá se la deja al doble. Un aviso genérico se lee y se olvida;
 * la aritmética con sus dos cifras, no: «Deuda de Libranza: $257.000.000 pasa a $514.000.000» se
 * discute sola con lo que él sabe que debe. (Dice «pasa a» y no «→» porque la flecha sale como ▯
 * en wasm — la fuente del canvas no trae el glifo, ver [transferRowSubtitle].)
 *
 * El saldo de una cuenta LOAN es deuda positiva y sale derivado de los eventos
 * (`enrichWith`/`computeBalances`), así que el «después» es una suma, no una predicción: es
 * exactamente lo que [signedDelta] le va a aplicar a esa pata.
 *
 * **Y sirve igual sobre un crédito con el saldo ajustado a mano.** «Ajustar saldo» (la deuda real
 * según el banco) no sobrescribe nada: registra un evento más, así que la deuda derivada ya
 * incluye el ajuste y este renglón parte de ahí. Lo que hay que saber es que la suma no mira
 * fechas —el saldo de una cuenta es la suma de TODOS sus eventos, en cualquier orden—, así que
 * anotar un desembolso VIEJO después de haber cuadrado la deuda contra el banco la sube por
 * encima de lo que el banco dice: ese desembolso ya estaba dentro de la cifra que se cuadró. Es
 * exactamente lo que este renglón deja ver antes de guardar, y si se guarda igual se arregla con
 * otro «Ajustar saldo». La alternativa —ignorar los eventos anteriores a un ajuste— sería
 * convertir el ajuste en un saldo escrito a mano, que es justo lo que este sistema no hace.
 */
fun deudaDespuesDelTraspaso(from: Account?, to: Account?, amount: Long?): String? {
    if (from == null || to == null || amount == null || amount <= 0L) return null
    val credito = listOf(from, to).firstOrNull { it.type == AccountType.LOAN } ?: return null
    // El saldo EN LA MONEDA DEL CRÉDITO, no `account.balance` a secas: ese campo es el componente
    // COP (ver `enrichWith`), así que sobre un préstamo en dólares el renglón leía «US$0 pasa a
    // US$1.000» — un rótulo en una moneda sobre una cifra de otra. Antes se callaba; decir el
    // número equivocado con confianza es peor. `balancesByCurrency` viene derivado junto al saldo,
    // y para COP vale exactamente lo mismo que `balance`.
    val moneda = credito.currency
    val deudaActual = credito.balancesByCurrency[moneda] ?: credito.balance
    val despues = deudaActual + if (credito.id == from.id) amount else -amount
    // [signedMoney] y no [formatCOP] por dos razones, las dos de la revisión de esta rama:
    //
    // 1. **Un crédito en otra moneda también tiene derecho a la aritmética.** La primera versión
    //    devolvía `null` cuando el préstamo no era COP, o sea que el renglón desaparecía justo
    //    donde más falta hace: en la cuenta cuyo monto el dueño NO puede verificar de memoria.
    //    Hoy solo se llega ahí por `POST /api/accounts` (la UI de Créditos crea únicamente
    //    cuentas COP), pero el silencio era la peor respuesta posible para ese caso.
    // 2. **El signo.** [formatMoney] lo descarta, y una deuda que queda negativa —abonar de más,
    //    que es exactamente lo que hace quien cancela un crédito— tiene que leerse «−$500.000»
    //    y no «$500.000».
    return "Deuda de ${credito.name}: ${signedMoney(deudaActual, moneda)} " +
        "pasa a ${signedMoney(despues, moneda)}"
}

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
    /**
     * El hueco donde este cuerpo se VE, que en una pantalla corta es más chico que el cuerpo.
     * Es el tope del alto que se le fija al sub-picker de cuentas: sin él, el mínimo era el alto
     * del formulario medido con altura infinita y el sub-picker quedaba más alto que la pantalla
     * — la losa vacía. `Dp.Unspecified` (el default) = sin tope, que es el comportamiento viejo.
     */
    alturaVisible: Dp = Dp.Unspecified,
    /**
     * Avisa que se abrió (`true`) o se cerró (`false`) el sub-picker de cuentas, para que
     * `QuickAddScreen` guarde y restaure el desplazamiento de la hoja igual que en Gasto/Ingreso.
     * `picking` es estado de acá adentro, así que sin este aviso el efecto de allá no se entera
     * y la hoja queda corrida bajo el dedo al volver.
     *
     * También se llama con `false` cuando este cuerpo SALE DE COMPOSICIÓN con el sub-picker
     * abierto — el `DisposableEffect` de más abajo—, porque ahí `picking` deja de existir sin
     * que nadie lo cierre.
     */
    onPickerAbierto: (Boolean) -> Unit = {},
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val elegibles = remember(accounts) { transferableAccounts(accounts) }
    // Ola 14: de acá salen los valores POR DEFECTO, y a propósito no incluye los créditos — ver
    // [defaultTransferAccounts]. `elegibles` (que sí los trae) es lo que se ofrece en el selector
    // y lo que valida el botón.
    val paraDefecto = remember(elegibles) { defaultTransferAccounts(elegibles) }

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
                cuentas = paraDefecto,
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
                cuentas = paraDefecto,
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

    // El aviso va ANTES de cambiar `picking`, en el toque: para cuando el estado cambie y la hoja
    // se vuelva a medir, el desplazamiento viejo ya está guardado del otro lado.
    fun abrirPicker(lado: TransferSide) {
        onPickerAbierto(true)
        picking = lado
    }

    fun cerrarPicker() {
        picking = null
        onPickerAbierto(false)
    }

    // Ola 13: el sub-picker de fecha pasa por el MISMO aviso que el de cuentas, y por el mismo
    // motivo. No es adorno: es otro sub-picker que reemplaza este cuerpo adentro del `Box` de
    // alto fijado, así que abrirlo con la hoja desplazada recorta el desplazamiento igual que
    // «Desde». Sin el aviso, `hayPicker` no cambia allá, el efecto no guarda ni restaura, y el
    // teclado vuelve corrido bajo el dedo — el bug caro, por un camino nuevo.
    //
    // Los dos reflejos no se pisan porque los dos sub-pickers no pueden estar abiertos a la vez:
    // la fecha se abre desde el formulario, que solo se ve con `picking == null`.
    fun abrirFecha() {
        onPickerAbierto(true)
        pickingDate = true
    }

    fun cerrarFecha() {
        pickingDate = false
        onPickerAbierto(false)
    }

    // ── El segundo cerrojo: si este cuerpo desaparece, su picker desaparece con él ──────
    //
    // `picking` muere cuando este `@Composable` sale de composición, pero el reflejo que
    // [QuickAddScreen] mantiene NO se entera solo — y un reflejo pegado en «abierto» mata la
    // restauración del desplazamiento en las tres pestañas (ver [PickersDeLaHoja]). El camino
    // que se midió es tocar «Gasto» con «Desde» abierto, y ese ya lo cierra `conTipo` allá,
    // que es donde tiene que estar: el estado se arregla en la misma composición y se puede
    // afirmar en una prueba. Esto es el cinturón además de los tirantes, para cualquier otra
    // salida de composición que alguien agregue mañana (una condición nueva alrededor de este
    // cuerpo, un formulario que se reemplace mientras carga) sin acordarse de este reflejo.
    //
    // `rememberUpdatedState` porque `onDispose` corre fuera de la composición y no puede
    // depender de qué instancia del lambda quedó capturada.
    val avisar by rememberUpdatedState(onPickerAbierto)
    DisposableEffect(Unit) { onDispose { avisar(false) } }
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
    // Ola 12 (revisión): el mínimo se acota al hueco visible. `formHeightPx` se mide con altura
    // infinita —este cuerpo vive adentro del scroll de la hoja—, así que en una pantalla corta
    // vale más que la pantalla: fijarlo tal cual dejaba el sub-picker de cuentas más alto que el
    // hueco y se abría mostrando el final de la lista, sin el título «Desde»/«Hacia» ni su X.
    // Cuando el formulario entra, `alturaVisible` es su propio alto y esto no cambia nada.
    val altoFijado = with(density) { formHeightPx.toDp() }
        .let { if (alturaVisible == Dp.Unspecified) it else minOf(it, alturaVisible) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (picking == null && !pickingDate) Modifier
                else Modifier.heightIn(min = altoFijado),
            ),
    ) {
        if (pickingDate) {
            // Mismo sub-picker que la pestaña Gasto/Ingreso, adentro del mismo Box de alto
            // fijado: abrirlo no cambia el alto de la hoja.
            Column(modifier = Modifier.fillMaxWidth()) {
                PickerHeader("Fecha", onClose = { cerrarFecha() })
                SelectorDeFecha(
                    seleccionada = fecha,
                    hoy = hoy,
                    onPick = { fecha = it; cerrarFecha() },
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
                                // Sobre `paraDefecto` y no sobre `elegibles`: lo que la app elige
                                // sola nunca puede ser un crédito (ver [defaultTransferAccounts]).
                                cuentas = paraDefecto,
                                // [destinoSugerido]: si la cuenta que acaba de pasar a origen era
                                // el destino habitual, lo que se propone es el traspaso de vuelta.
                                ultima = destinoSugerido(id),
                                excluir = id,
                            )
                            toId = reemplazo.id
                            origenTo = reemplazo.origen
                        }
                    } else {
                        // **Anotado, no arreglado (revisión de la Ola 12; es de master).** Esta
                        // rama NO tiene la guarda simétrica de la de arriba: si en «Hacia» se
                        // elige la MISMA cuenta que está en «Desde», las dos filas quedan iguales
                        // y el formulario se traba en «Elige dos cuentas distintas», con el dueño
                        // teniendo que adivinar cuál de las dos cambiar. Arreglarlo pide decidir
                        // qué se mueve —¿el origen, como hace la otra rama al revés?— y eso es
                        // producto, no geometría; no entra en la tanda del scroll.
                        toId = id
                        origenTo = OrigenCuenta.ELEGIDA
                    }
                    cerrarPicker()
                },
                onClose = { cerrarPicker() },
            )
        } else {
        Column(modifier = Modifier.fillMaxWidth().onSizeChanged { formHeightPx = it.height }) {

        // `paraDefecto.isEmpty()` además del conteo: con dos créditos y ninguna cuenta de dinero
        // hay dos elegibles, pero [validateTransfer] rechaza crédito↔crédito, así que el
        // formulario se abría con las dos puntas vacías y sin salida. El mensaje de abajo dice
        // exactamente lo que falta.
        if (accountsLoaded && (elegibles.size < 2 || paraDefecto.isEmpty())) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Un traspaso necesita dos cuentas tuyas. Un crédito sirve de punta " +
                    "—para el desembolso o para un abono extraordinario— pero la tarjeta no: el pago " +
                    "de la tarjeta se anota como gasto.",
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
                onClick = { abrirPicker(TransferSide.FROM) },
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
                onClick = { abrirPicker(TransferSide.TO) },
            )
        }

        Spacer(Modifier.height(16.dp))
        MoneyField(value = amount, onValueChange = { amount = it }, label = "MONTO")

        // ── Ola 14 · lo que este traspaso le hace al crédito ────────────────────────────────
        //
        // Solo aparece cuando una de las dos puntas ES un crédito, y entonces aparece SIEMPRE:
        // primero qué se está registrando (desembolso o abono extraordinario, con la frase que lo
        // separa de la cuota mensual) y, apenas hay monto, la deuda antes y después.
        //
        // El alto NO se reserva a propósito, al revés que el error y el «falta el monto» de más
        // abajo: esas dos cajas van pegadas al botón de guardar y su aparición lo corría bajo el
        // dedo. Este bloque vive arriba de la fecha, en medio del formulario, y solo cambia
        // cuando el dueño acaba de elegir un crédito con el dedo —o sea, mirando esta zona— así
        // que reservarle 60 dp a todo el mundo por un caso que la mayoría no usa habría dejado un
        // hueco permanente en la hoja para no mover nada que el dedo esté tocando.
        // «Exactamente uno» y no «alguno»: con un crédito de los dos lados el traspaso está
        // rechazado (ver validateTransfer) y explicarlo como desembolso sería contradecir el
        // motivo que ya se muestra debajo del botón.
        val unSoloCredito = from != null && to != null &&
            (from.type == AccountType.LOAN) != (to.type == AccountType.LOAN)
        val claseDeTraspaso = if (unSoloCredito) transferKindFor(from!!, to!!) else null
        if (claseDeTraspaso == TransferKind.DESEMBOLSO || claseDeTraspaso == TransferKind.ABONO_EXTRAORDINARIO) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (claseDeTraspaso == TransferKind.DESEMBOLSO) {
                    "Desembolso: la plata que el banco te prestó entra a tu cuenta y la deuda del " +
                        "crédito sube. No es un ingreso del mes."
                } else {
                    "Abono extraordinario: plata extra que baja el capital. La cuota mensual no va " +
                        "aquí — esa se anota como gasto en Agregar."
                },
                fontSize = 12.sp,
                color = MinTextMute,
            )
            deudaDespuesDelTraspaso(from, to, amount)?.let { renglon ->
                Spacer(Modifier.height(6.dp))
                Text(renglon, fontSize = 12.sp, color = MinText, fontWeight = FontWeight.Medium)
            }
        }

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
                .clickable(enabled = !saving) { abrirFecha() }
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

/**
 * El sub-picker de «Desde»/«Hacia».
 *
 * **Ola 14 — dos grupos, no una lista larga.** Al dejar entrar los préstamos, el dueño con cinco
 * créditos reales pasaba de ver tres cuentas a ver ocho renglones donde antes solo había plata
 * disponible, sin nada que dijera cuáles eran cuáles. Las cuentas de dinero e inversión van
 * primero (es lo que se elige el 99 % de las veces) y los créditos al final, bajo un rótulo con
 * una línea que explica para qué sirven ahí — que es la pregunta que se hace quien los ve
 * aparecer por primera vez en esta hoja.
 *
 * El orden dentro de cada grupo es el que trae la lista (`GET /api/accounts` ordena por nombre):
 * agrupar no reordena nada más.
 */
@Composable
private fun TransferAccountPicker(
    title: String,
    accounts: List<Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val creditos = accounts.filter { it.type == AccountType.LOAN }
    val cuentas = accounts.filter { it.type != AccountType.LOAN }
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader(title, onClose)
        if (cuentas.isNotEmpty()) {
            GrupoDeCuentas(cuentas, selectedId, onPick)
        }
        if (creditos.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "CRÉDITOS",
                fontSize = 11.sp,
                color = MinTextMute,
                letterSpacing = 0.4.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Elige uno para registrar el desembolso que te entró a la cuenta, o un abono " +
                    "extraordinario que sale de ella.",
                fontSize = 11.5.sp,
                color = MinTextFaint,
            )
            Spacer(Modifier.height(8.dp))
            GrupoDeCuentas(creditos, selectedId, onPick)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GrupoDeCuentas(
    accounts: List<Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
) {
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
}
