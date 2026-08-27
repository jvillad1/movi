package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account

/**
 * De dónde salió la cuenta que la hoja de Agregar está mostrando.
 *
 * No es decoración: es lo que decide si la hoja tiene que **decir** que ese valor lo puso ella
 * (ver [avisoDeCuenta]). Una cuenta que el dueño eligió con el dedo no necesita explicación;
 * una que puso la app, sí — y tiene que verse ANTES de guardar, no descubrirse después en
 * Movimientos.
 */
enum class OrigenCuenta {
    /** La trajo el contexto desde el que se abrió la hoja: el detalle de una cuenta, o la regla
     *  recurrente que se está anotando. Manda sobre todo lo demás. */
    CONTEXTO,

    /** La última en la que se anotó algo (ver `LastAccountStore`). */
    ULTIMA,

    /** No había ni contexto ni memoria utilizable: la primera de la lista. */
    PRIMERA,

    /** El dueño la eligió a mano en esta hoja. */
    ELEGIDA,

    /** No hay ninguna cuenta que elegir. */
    NINGUNA,
}

/** Qué cuenta quedó elegida y por qué. */
data class CuentaElegida(val id: String?, val origen: OrigenCuenta)

/**
 * **La regla de qué cuenta arranca elegida, en un solo lugar y sin Compose adentro** para que se
 * pueda probar de verdad.
 *
 * Prioridad, de más fuerte a más débil:
 *
 * 1. **[contexto]** — el `presetAccountId` con el que se abrió la hoja: «+ Registrar el
 *    primero» desde el detalle de una cuenta, o (cuando exista ese camino) la cuenta que la
 *    regla recurrente ya tiene guardada. Es una intención explícita y reciente del dueño sobre
 *    ESTE movimiento; una costumbre vieja no puede ganarle.
 * 2. **[ultima]** — la última cuenta en la que anotó algo.
 * 3. **La primera de [cuentas]** — el comportamiento de siempre, ahora sobre una lista con
 *    orden definido (`GET /api/accounts` y el `selectAll` de SQLDelight ordenan por nombre).
 *
 * Los dos primeros valen **solo si esa cuenta sigue estando en [cuentas]**, y ahí se cubren
 * solos los casos feos: la cuenta se borró, o es de un tipo que no sirve para este formulario
 * (el llamador filtra la lista antes — un traspaso pasa solo las cuentas traspasables, así que
 * una tarjeta recordada no llega hasta acá), o es de otro usuario que usó este dispositivo.
 *
 * [excluir] es para el traspaso: el destino no puede ser el origen, así que la cuenta del otro
 * lado no participa ni como recuerdo ni como primera de la lista.
 */
fun resolverCuenta(
    cuentas: List<Account>,
    contexto: String? = null,
    ultima: String? = null,
    excluir: String? = null,
): CuentaElegida {
    val elegibles = if (excluir == null) cuentas else cuentas.filter { it.id != excluir }
    fun existe(id: String?) = id != null && elegibles.any { it.id == id }
    return when {
        existe(contexto) -> CuentaElegida(contexto, OrigenCuenta.CONTEXTO)
        existe(ultima) -> CuentaElegida(ultima, OrigenCuenta.ULTIMA)
        else -> elegibles.firstOrNull()
            ?.let { CuentaElegida(it.id, OrigenCuenta.PRIMERA) }
            ?: CuentaElegida(null, OrigenCuenta.NINGUNA)
    }
}

/**
 * El renglón chiquito que va debajo de la etiqueta «Cuenta» (y de «Desde»/«Hacia» en el
 * traspaso) cuando el valor lo puso la app.
 *
 * **Con una sola cuenta no dice nada**, y eso es deliberado: no hay decisión que confesar ni
 * alternativa que ofrecer, así que la hoja tiene que verse exactamente como antes de esta rama
 * para quien todavía no abrió una segunda cuenta.
 *
 * Con [OrigenCuenta.CONTEXTO] tampoco: el dueño llegó desde el detalle de esa misma cuenta, o
 * desde el recurrente que la tiene guardada — repetírselo sería ruido.
 */
fun avisoDeCuenta(origen: OrigenCuenta, cuentasDisponibles: Int): String? {
    if (cuentasDisponibles <= 1) return null
    return when (origen) {
        // **Cortos a propósito, y medido en la web, no a ojo.** Este renglón comparte la fila con
        // el nombre de la cuenta, que se lleva el ancho que necesite: a 375 dp, con «Bancolombia
        // Ahorros» al lado, la primera versión («La última que usaste») entraba justo hasta «La
        // última que …» y se comía la única palabra que decía algo. Con doce caracteres entra
        // entera y le sobra lugar a un nombre más largo todavía.
        OrigenCuenta.ULTIMA -> "Última usada"
        OrigenCuenta.PRIMERA -> "Por defecto"
        OrigenCuenta.CONTEXTO, OrigenCuenta.ELEGIDA, OrigenCuenta.NINGUNA -> null
    }
}
