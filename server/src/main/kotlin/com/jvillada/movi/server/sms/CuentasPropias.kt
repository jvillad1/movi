package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.shared.model.ParsedSms
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/**
 * Las frases con que el banco nombra **una cuenta del dueño**: de dónde salió la plata («desde tu
 * cuenta *8133», «desde tu producto 8133», «desde la cuenta *8133», «desde Aho*8133») o a cuál
 * llegó («en tu cuenta *8133», «de tu cuenta *9586 Fiducuenta»). Nunca la de un tercero: esa va
 * detrás de un « a » o un « hacia ».
 */
private val cuentaPropiaEnElTexto = Regex(
    """\b(?:desde\s+tu\s+(?:cuenta|producto)|desde\s+la\s+cuenta|de\s+tu\s+cuenta|en\s+tu\s+cuenta|aho|cte)\s*\*?\s?(\d{4,})""",
    RegexOption.IGNORE_CASE,
)

/** Las corridas de 4+ dígitos del nombre de una cuenta: «Fiducuenta 9586» → 9586. */
private val digitosDelNombre = Regex("""\d{4,}""")

/**
 * **Los números de las cuentas del dueño** (sus últimos cuatro dígitos), según lo que él mismo dejó
 * ver: los dígitos de los nombres de sus cuentas («Fiducuenta 9586») y los que el banco escribe como
 * SUYOS en sus avisos («desde tu cuenta *8133»). Hace falta lo segundo porque la cuenta principal
 * del dueño se llama «Bancolombia Ahorros», sin número.
 */
fun numerosPropios(nombresDeCuentas: List<String>, textosDeAvisos: List<String>): Set<String> = buildSet {
    nombresDeCuentas.forEach { nombre -> digitosDelNombre.findAll(nombre).forEach { add(it.value.takeLast(4)) } }
    textosDeAvisos.forEach { texto -> cuentaPropiaEnElTexto.findAll(texto).forEach { add(it.groupValues[1].takeLast(4)) } }
}

/**
 * **Una cuenta del dueño no es «una cuenta de otros».** El retiro de la Fiducuenta «hacia la cuenta
 * *02955068133» va a su propia cuenta de ahorros (la 8133), y la pantalla le ofrecía guardarla como
 * la cuenta de un tercero. Si el identificador que leyó el server es un número y termina como una de
 * sus cuentas, no se ofrece. Las llaves y los nombres no se tocan: no son números de cuenta.
 *
 * No cambia cómo se lee el identificador (eso es `identificadorDelDestinoEn`, en `:core`): solo
 * decide, ya en la ruta y con las cuentas del dueño a mano, si vale la pena ofrecerlo.
 */
fun sinLaCuentaPropia(parsed: ParsedSms, propios: Set<String>): ParsedSms {
    val id = parsed.identificadorDelDestino ?: return parsed
    if (parsed.identificadorEsLlave || id.length < 4 || !id.all { it.isDigit() }) return parsed
    return if (id.takeLast(4) in propios) parsed.copy(identificadorDelDestino = null, identificadorEsLlave = false) else parsed
}

/** [numerosPropios] de [uid], leído de sus cuentas y de su bandeja. Dentro de una transacción. */
fun Transaction.numerosPropiosDe(uid: String): Set<String> = numerosPropios(
    nombresDeCuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it[Accounts.name] },
    textosDeAvisos = SmsMessages.select(SmsMessages.text).where { SmsMessages.userId eq uid }.map { it[SmsMessages.text] },
)
