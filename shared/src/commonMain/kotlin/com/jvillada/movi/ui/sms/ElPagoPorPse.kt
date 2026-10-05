package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.CuentaDelBanco
import com.jvillada.movi.ui.components.deudaQueNombraElMensaje
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

/** Lo que dice la pantalla cuando la categoría es «Traspaso»: qué se va a anotar, o por qué así no se puede. */
fun avisoDelTraspaso(hacia: Account?, origen: Account?): String =
    if (hacia != null && origen != null) {
        "Se anota como traspaso de ${origen.name} a ${hacia.name}: no cuenta como gasto."
    } else {
        "Un traspaso necesita la cuenta tuya a la que fue la plata. Si no está en Movi, créala, o elige otra categoría."
    }

/**
 * **La deuda que queda elegida al revisar un aviso**: la que el mensaje nombra por su número (ver
 * [deudaQueNombraElMensaje]) y, si no nombra ninguna, la que Movi reconoció en el aviso
 * ([ParsedSms.deudaSugeridaId], el correo de PSE). El número escrito es un dato; lo otro, memoria.
 */
fun deudaPropuestaDelAviso(resuelta: CuentaDelBanco, deudaSugeridaId: String?, accounts: List<Account>): Account? =
    deudaQueNombraElMensaje(resuelta)
        ?: deudaSugeridaId?.let { id -> accounts.firstOrNull { it.id == id } }
