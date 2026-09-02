package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.shared.model.group

/**
 * De dónde salió la cuenta que una pantalla de confirmación está mostrando.
 *
 * Es el mismo papel que cumple `OrigenCuenta` en la hoja de «Agregar», y por el mismo motivo: lo
 * que decide si la pantalla tiene que **decir** que ese valor lo puso ella. Acá pesa todavía más,
 * porque el movimiento no lo escribió nadie — lo leyó la app de un SMS o de un PDF, y la cuenta a
 * la que va es el único dato que no viene en el papel.
 */
enum class OrigenDeLaCuentaDelBanco {
    /** El nombre de la cuenta coincide con el banco que mandó el mensaje o el extracto. */
    POR_EL_BANCO,

    /** No coincidió ninguna: Movi puso la primera cuenta de banco que sirve para esto. */
    POR_DEFECTO,

    /** El dueño la eligió con el dedo en esta pantalla. */
    A_MANO,

    /** No hay ninguna cuenta que sirva. No se resuelve nada: la elige él. */
    NINGUNA,
}

/** Qué cuenta quedó puesta, y por qué. */
data class CuentaDelBanco(
    val cuenta: Account?,
    val origen: OrigenDeLaCuentaDelBanco,
)

/**
 * **A qué cuenta va esto que llegó del banco** — un SMS por confirmar o un extracto por importar—,
 * en un solo lugar y sin Compose adentro para que se pueda probar de verdad.
 *
 * ## Lo que había, y por qué era peligroso
 *
 * Las dos pantallas resolvían esto solas, con la misma cadena copiada, y las dos terminaban en
 * `accounts.firstOrNull()`. Esa última línea es la que hacía daño: la lista de cuentas viene
 * ordenada por nombre, así que «la primera» es la primera del abecedario, y en las cuentas reales
 * del dueño eso puede ser un crédito ya desembolsado. Un SMS de un banco que él no tiene anotado
 * —o cuya cuenta se llama «Ahorros principal» y no «Bancolombia»— se confirmaba contra el
 * «Vehículo 4083», que es lo que DEBE por un carro, no una cuenta de la que salga un gasto.
 *
 * Peor todavía: ninguna de las dos pantallas dejaba corregirlo. La cuenta se mostraba de solo
 * lectura, así que la única salida era no confirmar.
 *
 * ## Lo que hace ahora
 *
 * Todas las candidatas salen de `cuentasPara(accounts, uso).principales` — el criterio de
 * `:core`, el mismo que usa el selector de la hoja de «Agregar». Y entonces, en orden:
 *
 * 1. **[elegidaAMano]** manda sobre todo lo demás, y se busca en la lista **entera**: si él la
 *    sacó del «Ver todas», fue una decisión suya y esta función no revoca decisiones suyas.
 * 2. **El nombre del banco**, pero ya solo entre las candidatas. Que el filtro pese también acá
 *    no es de más: quien tiene un solo producto de Davivienda y es el crédito hipotecario veía
 *    ese crédito elegido por coincidencia de nombre, que es el mismo accidente por otra puerta.
 * 3. **La primera cuenta de banco** (Dinero que no sea Efectivo). Es una suposición, no una
 *    deducción, y por eso vuelve rotulada [OrigenDeLaCuentaDelBanco.POR_DEFECTO]: la pantalla lo
 *    dice en voz alta y la cuenta se puede cambiar con el dedo.
 * 4. **Nada.** Sin candidatas no se inventa un destino: se devuelve `null`, el botón queda
 *    apagado —como ya estaba— y la pantalla pide que la elija. Esa es la diferencia de fondo con
 *    `firstOrNull()`: antes, no saber y equivocarse se veían igual.
 *
 * @param banco el nombre del banco que mandó el SMS o el extracto. En blanco, el paso 2 no corre
 *   (contener la cadena vacía es cierto para toda cuenta, y eso volvería a elegir la primera del
 *   abecedario disfrazada de coincidencia).
 */
fun resolverCuentaDelBanco(
    accounts: List<Account>,
    uso: UsoDeCuenta,
    banco: String,
    elegidaAMano: String? = null,
): CuentaDelBanco {
    val aMano = accounts.firstOrNull { it.id == elegidaAMano }
    if (aMano != null) return CuentaDelBanco(aMano, OrigenDeLaCuentaDelBanco.A_MANO)

    val candidatas = cuentasPara(accounts, uso).principales

    val porElBanco = banco.takeIf { it.isNotBlank() }
        ?.let { nombre -> candidatas.firstOrNull { it.name.contains(nombre, ignoreCase = true) } }
    if (porElBanco != null) return CuentaDelBanco(porElBanco, OrigenDeLaCuentaDelBanco.POR_EL_BANCO)

    val deBanco = candidatas.firstOrNull {
        it.type.group == AccountGroup.DINERO && it.type != AccountType.CASH
    }
    return if (deBanco != null) {
        CuentaDelBanco(deBanco, OrigenDeLaCuentaDelBanco.POR_DEFECTO)
    } else {
        CuentaDelBanco(null, OrigenDeLaCuentaDelBanco.NINGUNA)
    }
}

/**
 * El renglón chiquito que va al lado de la cuenta cuando el valor no lo puso el dueño.
 *
 * Solo habla en los dos casos en los que hay algo que confesar. Con [OrigenDeLaCuentaDelBanco
 * .POR_EL_BANCO] el nombre de la cuenta ya dice por qué está ahí, y con
 * [OrigenDeLaCuentaDelBanco.A_MANO] la eligió él hace dos segundos: repetírselo sería ruido.
 *
 * `when` exhaustivo y sin `else`, por lo mismo que el resto de esta ola.
 */
fun avisoDeLaCuentaDelBanco(origen: OrigenDeLaCuentaDelBanco): String? = when (origen) {
    OrigenDeLaCuentaDelBanco.POR_DEFECTO -> "La puso Movi"
    OrigenDeLaCuentaDelBanco.NINGUNA -> "Elígela tú"
    OrigenDeLaCuentaDelBanco.POR_EL_BANCO, OrigenDeLaCuentaDelBanco.A_MANO -> null
}
