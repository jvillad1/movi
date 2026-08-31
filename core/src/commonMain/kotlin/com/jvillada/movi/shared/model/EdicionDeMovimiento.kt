package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Corregir un movimiento ya anotado: su monto, su cuenta y su concepto.**
 *
 * ### El hueco que cierra
 *
 * Hasta esta ola, de un movimiento guardado solo se podía cambiar la **categoría**
 * (`PUT /api/events/{id}/category`) y la **fecha** (`PUT /api/events/{id}/timestamp`). El monto,
 * la cuenta y el concepto eran de piedra: para corregir una cifra había que **anular y volver a
 * crear**, o sea perder el id del movimiento —y con él su sello de recurrente, su descarte de
 * «no es pago de tarjeta» y su renglón de historia— para arreglar un número.
 *
 * El dueño lo pidió con un caso concreto: *«Necesito editar el valor del movimiento de Hija
 * porque voy a pagar 3 millones desde NU y 1 millón desde Bancolombia»*. Dos correcciones sobre
 * el mismo renglón —el monto y de qué cuenta sale— que hoy cuestan borrar y rehacer.
 *
 * ### Los tres campos son nullable, y eso ES el contrato
 *
 * `null` significa **«no toques este campo»**, no «ponelo en blanco». Así el cliente manda solo
 * lo que el dueño cambió y una versión vieja de la app —o una nueva que agregue un campo más—
 * nunca pisa un dato que no quiso tocar. Mismo criterio que [EdicionDeDocumento].
 *
 * ### Lo que el cliente NO puede mandar por esta puerta
 *
 * Ni la **categoría** ni la **fecha** (cada una tiene su ruta, con sus propias guardas de
 * categoría reservada y de fecha futura), ni el **tipo** (INCOME/EXPENSE), ni la **moneda**, ni
 * `countsAsCashFlow` —que es derivado y se recalcula en cada lectura, ver
 * [FinancialEvent.countsAsCashFlow]—, ni `transferId`, que es la identidad del par y no un dato
 * editable.
 *
 * El **tipo** queda afuera a propósito y no por olvido: dar vuelta un gasto en ingreso sobre un
 * movimiento ya contado cambia el signo en todas las cifras del mes de una sola vez, y no es la
 * corrección que nadie pidió — anularlo y anotarlo bien es más honesto y ya se puede hacer.
 */
@Serializable
data class EdicionDeMovimiento(
    val amount: Long? = null,
    val accountId: String? = null,
    val description: String? = null,
)

/** Lo que se le dice a quien manda un monto que no es plata. */
const val MONTO_INVALIDO: String = "El monto tiene que ser mayor que cero."

/**
 * Tope del monto editable.
 *
 * No es una regla de negocio, es una guarda de cordura contra el campo que ya se equivocó una vez
 * (ver `MoneyField`: un cursor mal reanclado dejó $500.007.000 donde el dueño quiso $7.000). Un
 * billón de pesos es varios órdenes de magnitud más que cualquier movimiento real del hogar, así
 * que rechazarlo no le quita nada a nadie y sí atrapa el dedo o el bug.
 */
const val MONTO_MAXIMO: Long = 1_000_000_000_000L

/** Lo que se le dice a quien manda un monto absurdo. */
const val MONTO_DEMASIADO_GRANDE: String =
    "Ese monto es demasiado grande. Revisa el número que escribiste."

/** Largo de `financial_events.description` en el server, y de su espejo local. */
const val MAX_CONCEPTO_LENGTH: Int = 255

/** Lo que se le dice a quien borra el concepto entero. */
const val CONCEPTO_VACIO: String = "El concepto no puede quedar vacío."

/** Lo que se le dice a quien manda un concepto más largo que la columna. */
const val CONCEPTO_DEMASIADO_LARGO: String =
    "El concepto no puede superar $MAX_CONCEPTO_LENGTH caracteres."

/**
 * Lo que se le dice a quien intenta **mover de cuenta una de las dos mitades** de un traspaso o de
 * un pago de cuota.
 *
 * ### La decisión, y por qué no es la misma que para el monto
 *
 * Un par (traspaso, pago de cuota, pago de tarjeta) son **dos** movimientos enlazados por
 * [FinancialEvent.transferId]. Editar uno solo tiene dos formas muy distintas de salir mal:
 *
 * - **El monto** es *un solo hecho con dos anotaciones*: las dos patas nacen con la misma cifra
 *   (ver [transferLegsFor] y [pagoDeCuotaLegs]). Cambiarlo en una sola descuadra el par —la plata
 *   sale de una cuenta y entra otra distinta en la otra—, así que se cambia **en las dos a la
 *   vez**. Eso deja el par tan cuadrado como nació y es la corrección que el dueño de verdad
 *   quiere: la cuota fue de $4.215.223, no de $4.125.223.
 * - **La cuenta**, en cambio, no es un solo hecho: cada pata vive en la suya, y *cuál* pata puede
 *   vivir en *qué tipo de cuenta* es lo que validan [validarPagoDeCuota] y `validateTransfer` al
 *   crear el par. Mover una pata suelta se salta esas reglas enteras: la pata del dinero de un
 *   pago de cuota aterrizando en otra deuda deja dos patas de deuda y ningún peso saliendo de
 *   ningún lado; una punta de traspaso aterrizando en la cuenta de la otra punta deja un traspaso
 *   de una cuenta a sí misma.
 *
 * Reimplementar esas validaciones acá —con las dos direcciones, los dos grupos de cuenta y la
 * moneda— sería una tercera copia de una regla que ya vive en dos lados y que decide sobre plata.
 * Se prefiere **decir que no, con todas las letras y con la salida a mano**: anular el par (que
 * ya cascadea a las dos patas) y volver a registrarlo bien desde Agregar.
 */
const val PATA_NO_CAMBIA_DE_CUENTA: String =
    "Este movimiento es una de las dos mitades de un traspaso o del pago de una cuota, y cada " +
        "mitad vive en su propia cuenta. Para cambiarle la cuenta, anúlalo y vuelve a " +
        "registrarlo desde Agregar. El monto y el concepto sí se pueden corregir aquí."

/** Lo que la hoja le avisa al dueño antes de tocar el monto de una pata. */
const val MONTO_DE_UN_PAR_SE_MUEVE_JUNTO: String =
    "Es la mitad de un par: el monto se corrige en las dos mitades a la vez, para que la plata " +
        "que sale sea la misma que entra."

/** Lo que se le dice a quien manda una cuenta que no existe o no es suya. */
const val CUENTA_NO_ENCONTRADA: String = "Esa cuenta no existe."

/**
 * Lo que se le dice a quien intenta mover un movimiento a una cuenta de **otra moneda**.
 *
 * Sin esta guarda el movimiento no se pierde ni se rompe —`computeBalances` agrupa por moneda, así
 * que quedaría formando un saldo en dólares dentro de una cuenta en pesos— pero el saldo que el
 * dueño lee arriba de la cuenta dejaría de incluirlo, en silencio. Mismo criterio que
 * [PAGO_MONEDAS_DISTINTAS]: antes que inventar una conversión con la tasa del día, se dice que no.
 */
fun mensajeDeMonedaDistinta(monedaDeLaCuenta: String, monedaDelMovimiento: String): String =
    "Esa cuenta está en $monedaDeLaCuenta y este movimiento está en $monedaDelMovimiento. " +
        "Elige una cuenta en $monedaDelMovimiento."

/**
 * ¿Este cambio de cuenta le cambia al movimiento **si cuenta o no en el mes**? Devuelve el aviso,
 * o `null` si no cambia nada.
 *
 * ### Por qué hay que avisarlo antes y no después
 *
 * `isCashFlow` decide por **tipo de cuenta** además de por categoría: en una cuenta LOAN nada es
 * flujo de caja, y en una CREDIT_CARD solo la compra lo es. Así que mover un gasto de $3.000.000
 * de la cuenta de ahorros a un crédito lo saca de «Gastos del mes» **sin tocarle ni la categoría
 * ni el monto**, y el dueño no tiene por qué saber esa regla de memoria. Es exactamente el mismo
 * daño silencioso que las guardas de categoría reservada vinieron a cerrar por la otra puerta —
 * solo que este no se puede prohibir, porque mover un gasto a la tarjeta con la que de verdad se
 * pagó es una corrección legítima y frecuente.
 *
 * Entonces no se prohíbe: **se anuncia**. La hoja lo muestra encima del botón de guardar, antes
 * de que el cambio ocurra, igual que el aviso de cambio de mes al corregir una fecha.
 *
 * ### Lo que este aviso NO dice, porque no pasa
 *
 * No dice nada del patrimonio, y a propósito. Es tentador pensar que mover un ingreso a una cuenta
 * de deuda le invierte el signo —en una LOAN un INGRESO **baja** la deuda, ver [signedDelta]— pero
 * el patrimonio no se invierte: `netWorth` resta las cuentas de deuda, así que una deuda que baja
 * $100.000 y un activo que sube $100.000 mueven el patrimonio en la misma dirección y en la misma
 * cifra. Lo que sí se invierte es el signo del renglón **dentro del saldo de esa cuenta**, y eso
 * es correcto: ahí abajo la cifra es deuda, no plata.
 *
 * Función pura y afuera del `@Composable` para poder probarla: es una regla sobre plata, no un
 * detalle de dibujo.
 */
fun avisoDeCambioDeCuenta(
    event: FinancialEvent,
    tipoActual: AccountType?,
    tipoNuevo: AccountType?,
): String? {
    if (tipoActual == null || tipoNuevo == null || tipoActual == tipoNuevo) return null
    val contabaAntes = isCashFlow(tipoActual, event.type, event.category)
    val contaraDespues = isCashFlow(tipoNuevo, event.type, event.category)
    if (contabaAntes == contaraDespues) return null
    val lado = if (event.type == TransactionType.INCOME) "ingresos" else "gastos"
    return if (contabaAntes) {
        "Con esa cuenta, este movimiento deja de contar en tus $lado del mes: lo que pasa en un " +
            "crédito o una tarjeta no es plata que entra o sale de tu bolsillo. El saldo de la " +
            "cuenta sí se mueve."
    } else {
        "Con esa cuenta, este movimiento pasa a contar en tus $lado del mes."
    }
}

/**
 * El rechazo de una edición: el código HTTP y **las palabras exactas** que ve el dueño.
 *
 * Los dos juntos y no solo el texto, porque el código también viaja hasta la pantalla: el espejo
 * local lanza `ApiException(status, mensaje)` y la UI traduce ese par (ver `toUserMessage`). Si
 * cada lado eligiera su propio código, el mismo error se leería distinto con red y sin ella.
 */
data class RechazoDeEdicion(val status: Int, val mensaje: String)

/**
 * **La única definición de qué edición se acepta.** La usan el server (`PUT /api/events/{id}`) y
 * el espejo local (`LocalRepository.updateEvent`), que es lo que hace que corregir un movimiento
 * sin señal y corregirlo con señal contesten lo mismo.
 *
 * Devuelve `null` cuando la edición está bien.
 *
 * Las reglas, en orden, y el porqué de cada una está en la constante que devuelve:
 *
 * 1. **Monto > 0 y por debajo de [MONTO_MAXIMO]** — 400, es la forma del dato.
 * 2. **Concepto no vacío y de a lo sumo [MAX_CONCEPTO_LENGTH]** — 400, ídem (el largo es el de la
 *    columna: más largo se truncaría o explotaría en la base).
 * 3. **Una pata de un par no cambia de cuenta** — 422, ver [PATA_NO_CAMBIA_DE_CUENTA].
 * 4. **La cuenta destino existe y es del dueño** — 404, igual que el resto de este server: quien
 *    pide algo de otro usuario recibe «no existe», no «no puedes».
 * 5. **Misma moneda** — 422, ver [mensajeDeMonedaDistinta].
 *
 * [monedaDelMovimiento] es la del evento en el server. El espejo local pasa la de la **cuenta
 * actual** porque la tabla local no guarda moneda por movimiento; las dos coinciden para todo lo
 * que la app escribe (un movimiento nace en la moneda de su cuenta) y ese camino local solo corre
 * para movimientos que la app escribió y todavía no subió.
 */
fun validarEdicionDeMovimiento(
    cambios: EdicionDeMovimiento,
    esPataDeUnPar: Boolean,
    cuentaActualId: String,
    monedaDelMovimiento: String,
    /** La cuenta pedida en [EdicionDeMovimiento.accountId], o `null` si no existe o es de otro. */
    cuentaNueva: Account?,
): RechazoDeEdicion? {
    cambios.amount?.let { monto ->
        if (monto <= 0L) return RechazoDeEdicion(400, MONTO_INVALIDO)
        if (monto > MONTO_MAXIMO) return RechazoDeEdicion(400, MONTO_DEMASIADO_GRANDE)
    }
    cambios.description?.let { texto ->
        val limpio = texto.trim()
        if (limpio.isEmpty()) return RechazoDeEdicion(400, CONCEPTO_VACIO)
        if (limpio.length > MAX_CONCEPTO_LENGTH) return RechazoDeEdicion(400, CONCEPTO_DEMASIADO_LARGO)
    }
    val pedida = cambios.accountId
    // Mandar la MISMA cuenta no es cambiar de cuenta: la hoja manda los tres campos juntos, así
    // que sin esto corregir el monto de una pata rebotaría por una cuenta que nadie tocó.
    if (pedida != null && pedida != cuentaActualId) {
        if (esPataDeUnPar) return RechazoDeEdicion(422, PATA_NO_CAMBIA_DE_CUENTA)
        if (cuentaNueva == null) return RechazoDeEdicion(404, CUENTA_NO_ENCONTRADA)
        if (cuentaNueva.currency != monedaDelMovimiento) {
            return RechazoDeEdicion(422, mensajeDeMonedaDistinta(cuentaNueva.currency, monedaDelMovimiento))
        }
    }
    return null
}

/**
 * La edición con lo que de verdad cambia respecto de [event] — los campos que quedaron iguales se
 * vuelven `null`.
 *
 * Existe para que «guardar sin haber cambiado nada» no escriba: sin esto, la hoja manda siempre
 * los tres campos y cada guardado reescribiría la fila (y, en una pata, cascadearía el monto a la
 * hermana) por nada. También es lo que hace que el aviso de «se mueve en las dos mitades» solo
 * aparezca cuando el monto de verdad se tocó.
 */
fun soloLoQueCambia(event: FinancialEvent, cambios: EdicionDeMovimiento): EdicionDeMovimiento =
    EdicionDeMovimiento(
        amount = cambios.amount?.takeIf { it != event.amount },
        accountId = cambios.accountId?.takeIf { it != event.accountId },
        description = cambios.description?.trim()?.takeIf { it != event.description },
    )

/** ¿Esta edición pide cambiar algo? */
fun EdicionDeMovimiento.tieneCambios(): Boolean =
    amount != null || accountId != null || description != null
