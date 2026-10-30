package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CuentasDeCadaUso
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.claveDeCategoriaParaCuenta
import com.jvillada.movi.shared.model.claveDeNombreParaCuenta
import com.jvillada.movi.shared.model.normalizarParaBuscar

/**
 * De dónde salió la cuenta que la hoja de Agregar está mostrando.
 *
 * No es decoración: es lo que decide si la hoja tiene que **decir** que ese valor lo puso ella
 * (ver [avisoDeCuenta]). Una cuenta que el dueño eligió con el dedo no necesita explicación;
 * una que puso la app, sí — y tiene que verse ANTES de guardar, no descubrirse después en
 * Movimientos.
 */
enum class OrigenCuenta {
    /** La trajo el contexto desde el que se abrió la hoja: el detalle de una cuenta. Manda sobre
     *  todo lo demás. */
    CONTEXTO,

    /** Vuelta 43 · modo pago: la cuenta de la regla que se está pagando. «La de la regla». */
    REGLA,

    /**
     * Vuelta 48 · Cuota de un crédito: la cuenta de la que el banco **debita solo** la cuota
     * (`CreditTerms.debitoAutomaticoDesde`). «Débito automático».
     */
    DEBITO_AUTOMATICO,

    /**
     * Vuelta 43 · modo pago con una regla SIN cuenta: la hoja **no elige** —poner una tarjeta en
     * silencio fue el error del día del dueño— y pide la cuenta, con la sugerencia arriba
     * ([CuentaElegida.sugerida]).
     */
    POR_ELEGIR,

    /** Vuelta 43: un gasto en «Efectivo» (la categoría o el título) va a la cuenta de efectivo. */
    EFECTIVO,

    /** Vuelta 43: la última cuenta con que anotó ese mismo nombre («La que usas para Rappi»). */
    POR_NOMBRE,

    /** Vuelta 43: la última cuenta con que anotó esa categoría («La que usas para Mercado»). */
    POR_CATEGORIA,

    /** La última en la que se anotó algo (ver `LastAccountStore`). */
    ULTIMA,

    /**
     * Ola A: la que más gastos tuvo en los últimos 30 días (ver `CuentaMasUsadaCache` y
     * `DashboardSummary.cuentaMasUsada`). Manda sobre «la primera de la lista» pero no sobre la
     * última en la que se anotó algo — un movimiento reciente es más fuerte que una costumbre.
     */
    MAS_USADA,

    /** No había ni contexto ni memoria utilizable: la primera de la lista. */
    PRIMERA,

    /** El dueño la eligió a mano en esta hoja. */
    ELEGIDA,

    /** No hay ninguna cuenta que elegir. */
    NINGUNA,
}

/**
 * Qué cuenta quedó elegida y por qué.
 *
 * @param para la categoría o el nombre que la decidió, en [OrigenCuenta.POR_CATEGORIA] y
 *   [OrigenCuenta.POR_NOMBRE]: el «Mercado» de «La que usas para Mercado».
 * @param sugerida en [OrigenCuenta.POR_ELEGIR], la que la hoja le sugiere al dueño (arriba en el
 *   selector), con su propio porqué. `null` en todo lo demás.
 */
data class CuentaElegida(
    val id: String?,
    val origen: OrigenCuenta,
    val para: String? = null,
    val sugerida: CuentaElegida? = null,
)

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
 * 3. **[masUsada]** — Ola A: la que más gastos tuvo en los últimos 30 días
 *    (`DashboardSummary.cuentaMasUsada`). Pierde contra la última usada a propósito: si el dueño
 *    anotó algo en otra cuenta hace un minuto, esa intención reciente pesa más que un promedio de
 *    30 días.
 * 4. **La primera de [cuentas]** — el comportamiento de siempre, ahora sobre una lista con
 *    orden definido (`GET /api/accounts` y el `selectAll` de SQLDelight ordenan por nombre).
 *
 * Los tres primeros valen **solo si esa cuenta sigue estando en [cuentas]**, y ahí se cubren
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
    masUsada: String? = null,
    excluir: String? = null,
): CuentaElegida {
    val elegibles = if (excluir == null) cuentas else cuentas.filter { it.id != excluir }
    fun existe(id: String?) = id != null && elegibles.any { it.id == id }
    return when {
        existe(contexto) -> CuentaElegida(contexto, OrigenCuenta.CONTEXTO)
        existe(ultima) -> CuentaElegida(ultima, OrigenCuenta.ULTIMA)
        existe(masUsada) -> CuentaElegida(masUsada, OrigenCuenta.MAS_USADA)
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
fun avisoDeCuenta(origen: OrigenCuenta, cuentasDisponibles: Int, para: String? = null): String? {
    if (cuentasDisponibles <= 1) return null
    return when (origen) {
        // **Cortos a propósito, y medido en la web, no a ojo.** Este renglón comparte la fila con
        // el nombre de la cuenta, que se lleva el ancho que necesite: a 375 dp, con «Bancolombia
        // Ahorros» al lado, la primera versión («La última que usaste») entraba justo hasta «La
        // última que …» y se comía la única palabra que decía algo. Con doce caracteres entra
        // entera y le sobra lugar a un nombre más largo todavía.
        OrigenCuenta.ULTIMA -> "Última usada"
        // Corto por la misma regla que las otras dos, no porque haya menos que decir: ver el
        // comentario de arriba.
        OrigenCuenta.MAS_USADA -> "Más usada"
        OrigenCuenta.PRIMERA -> "Por defecto"
        // Vuelta 43: el porqué de la cuenta que la hoja eligió mirando QUÉ se anota. Vuelta 83: la
        // cuenta (su nombre corto) se lleva lo que deja el rótulo «Cuenta», y si este porqué no entra
        // se corta con «…» sin partir la fila.
        OrigenCuenta.REGLA -> "La de la regla"
        OrigenCuenta.DEBITO_AUTOMATICO -> "Débito automático"
        OrigenCuenta.EFECTIVO -> "Es en efectivo"
        OrigenCuenta.POR_NOMBRE, OrigenCuenta.POR_CATEGORIA -> para?.let { "La que usas para $it" }
        OrigenCuenta.POR_ELEGIR -> "Elige de dónde sale"
        OrigenCuenta.CONTEXTO, OrigenCuenta.ELEGIDA, OrigenCuenta.NINGUNA -> null
    }
}


/**
 * **La cuenta que Agregar propone para un gasto o un ingreso** (vuelta 43), mirando QUÉ se anota.
 *
 * El día del dueño: *«Las cuentas por defecto son siempre "Más usada" (Nu, tarjeta de crédito). El
 * arriendo, el mercado y el efectivo arrancaron todos en Nu.»* El orden, de más fuerte a más débil:
 *
 * 1. **[contexto]** — el detalle de una cuenta desde el que se abrió la hoja (como siempre).
 * 2. **Modo pago: la cuenta de la regla** ([deLaRegla]) — «La de la regla». Si la regla no tiene
 *    cuenta, **vuelta 72**: la hoja propone la cuenta con la MISMA lógica que un gasto (los pasos que
 *    siguen, con su rótulo: «La que usas para Mercado», «Más usada»…), y la fila sigue editable. Hasta la
 *    vuelta 71 no elegía nada y pedía la cuenta cada mes («Falta la cuenta» en «Carro» todos los meses);
 *    ahora, después de guardar, Pagos ofrece «¿Usar siempre Nu para Carro?» (nunca se guarda sola).
 * 3. **«Efectivo»** en la categoría o en el título → la cuenta de efectivo, si hay una.
 * 4. **La última cuenta con ese nombre** ([CuentasDeCadaUso.porNombre]) — «La que usas para Rappi».
 * 5. **La última cuenta con esa categoría** ([CuentasDeCadaUso.porCategoria]) — «La que usas para
 *    Mercado».
 * 6. **La última cuenta usada** y 7. **la más usada**, como antes, solo si lo de arriba no dijo nada.
 * 8. La primera de la lista.
 *
 * Todo vale **solo si la cuenta está en [cuentas]** (las que esa pestaña ofrece): una tarjeta
 * recordada no aparece como destino de un ingreso.
 */
fun resolverCuentaDelMovimiento(
    cuentas: List<Account>,
    tipo: TransactionType,
    categoria: String,
    nombre: String,
    usos: CuentasDeCadaUso = CuentasDeCadaUso(),
    contexto: String? = null,
    modoPago: Boolean = false,
    deLaRegla: String? = null,
    ultima: String? = null,
    masUsada: String? = null,
): CuentaElegida {
    fun existe(id: String?) = id != null && cuentas.any { it.id == id }
    if (existe(contexto)) return CuentaElegida(contexto, OrigenCuenta.CONTEXTO)
    if (modoPago && existe(deLaRegla)) return CuentaElegida(deLaRegla, OrigenCuenta.REGLA)

    val porLoQueSeAnota: CuentaElegida? = run {
        if (esEnEfectivo(categoria) || esEnEfectivo(nombre)) {
            cuentas.firstOrNull { it.type == AccountType.CASH }?.let { return@run CuentaElegida(it.id, OrigenCuenta.EFECTIVO) }
        }
        val porNombre = claveDeNombreParaCuenta(tipo, nombre)?.let { usos.porNombre[it] }
        if (existe(porNombre)) return@run CuentaElegida(porNombre, OrigenCuenta.POR_NOMBRE, para = nombre.trim())
        val porCategoria = claveDeCategoriaParaCuenta(tipo, categoria)?.let { usos.porCategoria[it] }
        if (existe(porCategoria)) return@run CuentaElegida(porCategoria, OrigenCuenta.POR_CATEGORIA, para = categoria.trim())
        null
    }
    // Vuelta 72: también en modo pago sin cuenta en la regla, la misma propuesta que un gasto.
    return porLoQueSeAnota ?: resolverCuenta(cuentas, ultima = ultima, masUsada = masUsada)
}

/** «Efectivo», «Retiro en efectivo», «efectivo para el taxi»: la palabra, sin tildes ni mayúsculas. */
private fun esEnEfectivo(texto: String): Boolean =
    normalizarParaBuscar(texto).split(Regex("[^a-z0-9]+")).any { it == "efectivo" }

/**
 * Las cuentas del selector en modo pago, **con la sugerida arriba** (vuelta 43): la hoja no la
 * eligió, pero la pone primera para que sea un toque.
 */
fun conLaSugeridaArriba(cuentas: List<Account>, sugerida: String?): List<Account> {
    val primera = cuentas.firstOrNull { it.id == sugerida } ?: return cuentas
    return listOf(primera) + cuentas.filter { it.id != sugerida }
}
