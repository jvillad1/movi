package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.time.AppTimeZone
import kotlin.math.roundToLong
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * **El día del movimiento, cuando el aviso lo dice** ([ParsedSms.fecha], la «Fecha de la
 * transacción» del correo de PSE). El correo puede llegar al día siguiente —pasa por el reenvío de
 * Gmail y por el proveedor—, y anotarlo con la hora de llegada lo pondría en otro día (o en otro
 * período). Si el aviso dice otro día, se usa ese día con la misma hora; si dice el mismo, o no dice
 * nada, o algo ilegible, vale [momento] tal cual.
 */
fun momentoConLaFechaDelAviso(momento: Long, fecha: String?, zona: TimeZone = AppTimeZone.zone): Long {
    val dia = fecha?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return momento
    val llegada = Instant.fromEpochMilliseconds(momento).toLocalDateTime(zona)
    if (llegada.date == dia) return momento
    return LocalDateTime(dia, llegada.time).toInstant(zona).toEpochMilliseconds()
}

/** El nombre del movimiento, con lo que dijo el aviso de para qué fue: «Coomeva · Pago de saldo plan familiar». */
fun descripcionConLaNota(comercio: String, nota: String?): String {
    val n = nota?.trim().orEmpty()
    return if (n.isEmpty() || n.equals(comercio.trim(), ignoreCase = true)) comercio else "$comercio · $n"
}

/**
 * **¿Este aviso se anota como un traspaso entre cuentas suyas?** Sí cuando la categoría elegida es
 * [TRANSFER_CATEGORY], el server dijo a qué cuenta propia fue ([ParsedSms.traspasoHaciaId], el
 * «Depósito a tu cuenta NU» de PSE) y las dos puntas sirven para un traspaso: son distintas y
 * ninguna es una tarjeta (un traspaso no las acepta). Devuelve la cuenta de destino, o `null`.
 *
 * Sin destino un «Traspaso» no se puede anotar como un gasto suelto (el server lo rechaza, y está
 * bien: no es un gasto); la pantalla lo dice y deja elegir otra categoría.
 */
fun destinoDelTraspaso(leido: ParsedSms?, categoria: String?, origen: Account?, accounts: List<Account>): Account? {
    if (leido == null || categoria != TRANSFER_CATEGORY || origen == null) return null
    val hacia = leido.traspasoHaciaId?.let { id -> accounts.firstOrNull { it.id == id } } ?: return null
    if (hacia.id == origen.id) return null
    if (hacia.type == AccountType.CREDIT_CARD || origen.type == AccountType.CREDIT_CARD) return null
    return hacia
}

/** El traspaso que se crea al confirmar el aviso: la pata que sale de [origen] es la que queda enlazada al aviso. */
fun traspasoDelAviso(
    leido: ParsedSms,
    origen: Account,
    hacia: Account,
    momento: Long,
    nuevoId: (String) -> String,
): CreateTransferRequest = CreateTransferRequest(
    transferId = nuevoId("tr"),
    fromEventId = nuevoId("ev"),
    toEventId = nuevoId("ev"),
    fromAccountId = origen.id,
    toAccountId = hacia.id,
    amount = leido.amount.roundToLong(),
    timestamp = momento,
    note = leido.nota,
)

/** Lo que dice la pantalla cuando la categoría es «Traspaso»: qué se va a anotar, o por qué así no se puede. */
fun avisoDelTraspaso(hacia: Account?, origen: Account?): String =
    if (hacia != null && origen != null) {
        "Se anota como traspaso de ${origen.name} a ${hacia.name}: no cuenta como gasto."
    } else {
        "Un traspaso necesita la cuenta tuya a la que fue la plata. Si no está en Movi, créala, o elige otra categoría."
    }
