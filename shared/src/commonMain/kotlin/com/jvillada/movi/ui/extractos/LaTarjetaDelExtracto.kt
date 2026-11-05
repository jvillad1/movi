package com.jvillada.movi.ui.extractos

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TarjetaDelExtracto
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.grupoDelBanco
import com.jvillada.movi.shared.model.marcaDeLaCuenta
import com.jvillada.movi.shared.model.marcaDelBanco
import com.jvillada.movi.ui.components.CuentaDelBanco
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.resolverCuentaDelBanco

/**
 * # Vuelta 129 · el extracto de una tarjeta va a una tarjeta
 *
 * Una probadora sin su tarjeta Nu en Movi subió el extracto de la tarjeta. La revisión resolvía la
 * cuenta con la regla de los avisos ([resolverCuentaDelBanco]): el número del extracto no era de
 * ninguna cuenta suya y el banco era «Nu», así que caía en «Nu Ahorros» (`cuentaDeNu`), y las
 * compras de la tarjeta se habrían anotado en la cuenta de ahorros.
 *
 * Cuando la lectura dice que el extracto es de una tarjeta ([StatementParseResult.tarjeta]), la
 * misma regla se corre **solo entre las tarjetas** del dueño (en la moneda del extracto: la hermana
 * en dólares la elige el importe fila por fila). Si no tiene ninguna de ese banco, no se elige nada y
 * la revisión ofrece crearla ([LoQueDiceLaTarjeta.FaltaLaTarjeta]).
 *
 * Lo que el dueño eligió con el dedo en esta pantalla manda, como siempre; si no es una tarjeta, la
 * revisión lo advierte ([LoQueDiceLaTarjeta.NoEsUnaTarjeta]). La cuenta que le puso al papel al
 * subirlo es solo una sugerencia ([OrigenDeLaCuentaDelBanco.LA_DEL_PAPEL]). Nada se bloquea.
 */
fun cuentaDelExtracto(
    accounts: List<Account>,
    result: StatementParseResult,
    elegidaAMano: String?,
): CuentaDelBanco {
    // Lo que el dueño eligió en esta pantalla manda, como siempre. Sin tarjeta, por la regla de
    // siempre (que además dice el destino que nombra el extracto).
    if (result.tarjeta == null && accounts.any { it.id == elegidaAMano }) {
        return resolverCuentaDelBanco(accounts, UsoDeCuenta.CUENTA_DEL_EXTRACTO, result.bankName, elegidaAMano, numerosDe(result))
    }
    accounts.firstOrNull { it.id == elegidaAMano }?.let { return CuentaDelBanco(it, OrigenDeLaCuentaDelBanco.A_MANO) }
    val porElContenido = porElContenido(accounts, result)
    // Vuelta 129 (revisión): la cuenta que se le puso al papel al subirlo es una **sugerencia**, con su
    // origen. Si el contenido la contradice —el extracto es de una tarjeta, o nombra el número de
    // otra cuenta— gana el contenido y la revisión dice por qué ([porQueNoLaDelPapel]). Sin cuenta en
    // el papel, exactamente lo de antes.
    val delPapel = accounts.firstOrNull { it.id == result.cuentaDelPapel } ?: return porElContenido
    if (contradiccion(delPapel, porElContenido, result) != null) return porElContenido
    return CuentaDelBanco(delPapel, OrigenDeLaCuentaDelBanco.LA_DEL_PAPEL)
}

/**
 * **Por qué no se usó la cuenta que se le puso al papel al subirlo**, o `null` si se usó (o no había,
 * o el dueño eligió otra en esta pantalla). Ver [cuentaDelExtracto].
 */
fun porQueNoLaDelPapel(accounts: List<Account>, result: StatementParseResult, elegidaAMano: String?): String? {
    if (accounts.any { it.id == elegidaAMano }) return null
    val delPapel = accounts.firstOrNull { it.id == result.cuentaDelPapel } ?: return null
    return contradiccion(delPapel, porElContenido(accounts, result), result)
}

private fun numerosDe(result: StatementParseResult): String = result.numerosDeCuenta.joinToString(" ") { "*$it" }

/** La cuenta que dice el contenido del extracto: la regla de siempre, y si es de una tarjeta, entre las tarjetas. */
private fun porElContenido(accounts: List<Account>, result: StatementParseResult): CuentaDelBanco {
    val tarjeta = result.tarjeta
        ?: return resolverCuentaDelBanco(accounts, UsoDeCuenta.CUENTA_DEL_EXTRACTO, result.bankName, null, numerosDe(result))
    return resolverCuentaDelBanco(
        accounts = tarjetasDe(accounts, tarjeta),
        uso = UsoDeCuenta.CUENTA_DEL_EXTRACTO,
        banco = result.bankName,
        // «tarjeta» para que la regla de Nu (`tarjetaDeNu`) sepa que el papel habla de la tarjeta.
        textoDelMensaje = "tarjeta ${numerosDe(result)}",
    )
}

/** Lo que el contenido dice en contra de [delPapel], en una frase; `null` si no la contradice. */
private fun contradiccion(delPapel: Account, porElContenido: CuentaDelBanco, result: StatementParseResult): String? {
    val tarjeta = result.tarjeta
    if (tarjeta != null && delPapel.type != AccountType.CREDIT_CARD) {
        return "Al subir el archivo elegiste ${delPapel.name}, pero este extracto es de una tarjeta de crédito."
    }
    // Una tarjeta de otro banco: la del papel es de una marca y el extracto de otra.
    if (tarjeta != null && esDeOtroBanco(delPapel, tarjeta)) {
        return "Al subir el archivo elegiste ${delPapel.name}, pero este extracto es de una tarjeta ${tarjeta.banco}."
    }
    val nombrada = porElContenido.cuenta
    if (porElContenido.origen == OrigenDeLaCuentaDelBanco.POR_EL_NUMERO && nombrada != null && nombrada.id != delPapel.id) {
        return "Al subir el archivo elegiste ${delPapel.name}, pero el extracto es de ${nombrada.name}: lo dice su número."
    }
    return null
}

/** Las tarjetas de crédito del dueño en la moneda del extracto. */
private fun tarjetasDe(accounts: List<Account>, tarjeta: TarjetaDelExtracto): List<Account> =
    accounts.filter { it.type == AccountType.CREDIT_CARD && it.currency == tarjeta.moneda }

/** ¿[cuenta] es una tarjeta del banco de [tarjeta]? Por la marca del catálogo, o por el nombre. */
private fun esDelBanco(cuenta: Account, tarjeta: TarjetaDelExtracto): Boolean {
    val marca = marcaDelBanco(tarjeta.banco)
    return if (marca != null) marcaDeLaCuenta(cuenta)?.id == marca.id
    else cuenta.name.contains(tarjeta.banco, ignoreCase = true)
}

/**
 * ¿[cuenta] es de otro banco que el de [tarjeta]? Solo si las dos marcas se conocen y sus bancos
 * ([grupoDelBanco]) difieren: la AMEX es de Bancolombia, así que con el extracto de Bancolombia no se
 * contradice; una tarjeta con un nombre sin marca («Mi tarjeta morada») tampoco.
 */
internal fun esDeOtroBanco(cuenta: Account, tarjeta: TarjetaDelExtracto): Boolean {
    val delExtracto = marcaDelBanco(tarjeta.banco) ?: return false
    val deLaCuenta = marcaDeLaCuenta(cuenta) ?: return false
    return grupoDelBanco(deLaCuenta.id) != grupoDelBanco(delExtracto.id)
}

/** Lo que la revisión de un extracto tiene que decir de la tarjeta. */
sealed interface LoQueDiceLaTarjeta {
    /** No es de una tarjeta, o va a una tarjeta: nada. */
    data object Nada : LoQueDiceLaTarjeta

    /** Es de una tarjeta que el dueño no tiene en Movi: se ofrece crearla con [tarjeta]. */
    data class FaltaLaTarjeta(val tarjeta: TarjetaDelExtracto) : LoQueDiceLaTarjeta

    /**
     * Es de una tarjeta y la cuenta elegida ([elegida]) no lo es. Se ofrece [suTarjeta] (la que la
     * regla elegiría entre sus tarjetas) o, si no tiene una de ese banco, crearla con [tarjeta].
     */
    data class NoEsUnaTarjeta(
        val elegida: Account,
        val suTarjeta: Account?,
        val tarjeta: TarjetaDelExtracto,
        val puedeCrearla: Boolean,
    ) : LoQueDiceLaTarjeta
}

/**
 * Ver [LoQueDiceLaTarjeta]. [destino] es la cuenta a la que se importaría hoy. [accounts] es `null`
 * mientras las cuentas no llegaron (o si la lectura falló): sin saber qué tarjetas tiene, no se dice
 * que falta ninguna ni se ofrece crearla —una tarjeta repetida es peor que esperar—.
 */
fun loQueDiceLaTarjeta(
    accounts: List<Account>?,
    result: StatementParseResult,
    destino: Account?,
): LoQueDiceLaTarjeta {
    if (accounts == null) return LoQueDiceLaTarjeta.Nada
    val tarjeta = result.tarjeta ?: return LoQueDiceLaTarjeta.Nada
    if (destino?.type == AccountType.CREDIT_CARD) return LoQueDiceLaTarjeta.Nada
    val delBanco = tarjetasDe(accounts, tarjeta).filter { esDelBanco(it, tarjeta) }
    if (destino == null) {
        // Sin destino y con tarjetas de ese banco, la regla no supo cuál: la elige él, como siempre.
        return if (delBanco.isEmpty()) LoQueDiceLaTarjeta.FaltaLaTarjeta(tarjeta) else LoQueDiceLaTarjeta.Nada
    }
    val suTarjeta = porElContenido(accounts, result).cuenta
    return LoQueDiceLaTarjeta.NoEsUnaTarjeta(destino, suTarjeta, tarjeta, puedeCrearla = delBanco.isEmpty())
}

/** «Crear la tarjeta Nu». */
fun textoDeCrearLaTarjeta(tarjeta: TarjetaDelExtracto): String = "Crear la tarjeta ${tarjeta.banco}"

/** Lo que la revisión dice arriba, junto al destino. */
fun fraseDeLaTarjeta(lo: LoQueDiceLaTarjeta): String? = when (lo) {
    LoQueDiceLaTarjeta.Nada -> null
    is LoQueDiceLaTarjeta.FaltaLaTarjeta ->
        "Este extracto es de una tarjeta de crédito ${lo.tarjeta.banco} que no tienes en Movi. " +
            "Créala para que sus movimientos se anoten ahí."
    is LoQueDiceLaTarjeta.NoEsUnaTarjeta ->
        "Este extracto es de una tarjeta de crédito, no de ${lo.elegida.name}."
}
