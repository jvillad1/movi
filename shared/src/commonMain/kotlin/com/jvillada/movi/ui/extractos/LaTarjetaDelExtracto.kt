package com.jvillada.movi.ui.extractos

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TarjetaDelExtracto
import com.jvillada.movi.shared.model.UsoDeCuenta
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
 * Lo que el dueño eligió con el dedo —o le puso al papel al subirlo— manda, como siempre; si no es
 * una tarjeta, la revisión lo advierte ([LoQueDiceLaTarjeta.NoEsUnaTarjeta]). Nada se bloquea.
 */
fun cuentaDelExtracto(
    accounts: List<Account>,
    result: StatementParseResult,
    elegidaAMano: String?,
): CuentaDelBanco {
    val numeros = result.numerosDeCuenta.joinToString(" ") { "*$it" }
    val tarjeta = result.tarjeta
        ?: return resolverCuentaDelBanco(
            accounts = accounts,
            uso = UsoDeCuenta.CUENTA_DEL_EXTRACTO,
            banco = result.bankName,
            elegidaAMano = elegidaAMano,
            textoDelMensaje = numeros,
        )
    accounts.firstOrNull { it.id == elegidaAMano }?.let { return CuentaDelBanco(it, OrigenDeLaCuentaDelBanco.A_MANO) }
    return resolverCuentaDelBanco(
        accounts = tarjetasDe(accounts, tarjeta),
        uso = UsoDeCuenta.CUENTA_DEL_EXTRACTO,
        banco = result.bankName,
        // «tarjeta» para que la regla de Nu (`tarjetaDeNu`) sepa que el papel habla de la tarjeta.
        textoDelMensaje = "tarjeta $numeros",
    )
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

/** Ver [LoQueDiceLaTarjeta]. [destino] es la cuenta a la que se importaría hoy. */
fun loQueDiceLaTarjeta(
    accounts: List<Account>,
    result: StatementParseResult,
    destino: Account?,
): LoQueDiceLaTarjeta {
    val tarjeta = result.tarjeta ?: return LoQueDiceLaTarjeta.Nada
    if (destino?.type == AccountType.CREDIT_CARD) return LoQueDiceLaTarjeta.Nada
    val delBanco = tarjetasDe(accounts, tarjeta).filter { esDelBanco(it, tarjeta) }
    if (destino == null) {
        // Sin destino y con tarjetas de ese banco, la regla no supo cuál: la elige él, como siempre.
        return if (delBanco.isEmpty()) LoQueDiceLaTarjeta.FaltaLaTarjeta(tarjeta) else LoQueDiceLaTarjeta.Nada
    }
    val suTarjeta = cuentaDelExtracto(accounts, result, elegidaAMano = null).cuenta
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
