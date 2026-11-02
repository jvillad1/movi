package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Editar una cuenta (vuelta 98)
 *
 * El papá del dueño quería cambiarle el nombre y el saldo a una de sus cuentas y no encontró cómo: el
 * saldo se cambiaba con «Cuadrar con el banco», que nadie asocia con «editar», y el nombre solo se
 * podía corregir en un crédito (desde sus condiciones) o en un bien. Ahora el detalle de la cuenta
 * tiene «Editar la cuenta», que abre el mismo formulario de crear, lleno con lo de hoy.
 *
 * Las reglas viven acá, en `:core`, porque las aplican los dos lados: el server las hace cumplir en
 * `PUT /api/accounts/{id}` y la hoja las usa para no ofrecer lo que el server va a rechazar.
 */

/**
 * `PUT /api/accounts/{id}` — **editar una cuenta**: el nombre, el tipo (solo dentro de lo que no rompe
 * nada, ver [problemaDelCambioDeTipo]) y, si viene, el saldo de hoy.
 *
 * - [type] `null` = no se toca. Un APK que no sepa del tipo no lo puede cambiar sin querer.
 * - [saldoDeHoy] `null` = no se toca el saldo. **No es un campo crudo**: con un valor, el server
 *   escribe el MISMO ajuste que «Cuadrar con el banco» (`balanceAdjustmentEventFor`, contra los
 *   movimientos vigentes, sin anulados), en la moneda de la cuenta. Sin diferencia no escribe nada,
 *   así que dos guardados iguales dejan un solo ajuste. Nunca se reescribe un movimiento ni el saldo
 *   inicial: el saldo cambia solo por un movimiento visible.
 *
 * Contesta [AdjustAccountBalanceResponse]: la cuenta ya enriquecida (su nombre y su saldo nuevos) y el
 * ajuste que se escribió, o `null` si no hizo falta. Es la misma respuesta del cuadre a propósito: el
 * teléfono la espeja igual (la cuenta en su fila, el ajuste como movimiento ya sincronizado).
 */
@Serializable
data class EditarCuentaRequest(
    val name: String,
    val type: AccountType? = null,
    val saldoDeHoy: Long? = null,
)

/**
 * **La regla del nombre de una cuenta**, la misma al crear (`POST /api/accounts`), al renombrar
 * (`PUT /{id}/name`) y al editar (`PUT /{id}`): recortado, no vacío y hasta [MAX_ACCOUNT_NAME_LENGTH]
 * caracteres (el largo de la columna). `null` = está bien.
 *
 * **Dos cuentas con el mismo nombre sí se permiten**, al crear y al editar: «Ahorros» en dos bancos es
 * un caso real, y la lista desempata por id (ver el `GET` de `AccountRoutes`). Ninguna regla de crear
 * lo prohibía, y editar no inventa una más estricta.
 */
fun problemaDelNombreDeCuenta(nombre: String): String? {
    val limpio = nombre.trim()
    return when {
        limpio.isEmpty() -> "El nombre no puede estar vacío"
        limpio.length > MAX_ACCOUNT_NAME_LENGTH -> "El nombre no puede superar los $MAX_ACCOUNT_NAME_LENGTH caracteres"
        else -> null
    }
}

/** Lo que se dice cuando el tipo pasaría de plata a deuda o al revés. */
const val UNA_DEUDA_NO_CAMBIA_DE_TIPO: String =
    "Una cuenta de plata no se vuelve deuda, ni una deuda cuenta de plata: crea la otra cuenta."

/** Lo que se dice cuando el tipo de un bien cambiaría. */
const val UN_BIEN_NO_CAMBIA_DE_TIPO: String = "Un bien no cambia de tipo: se edita en su hoja."

/** Lo que se dice cuando el efectivo cambiaría de tipo, o una cuenta pasaría a ser efectivo. */
const val EL_EFECTIVO_NO_CAMBIA_DE_TIPO: String =
    "El efectivo no recibe extractos ni avisos del banco: no cambia de tipo. Crea la otra cuenta."

/** Lo que se dice cuando una inversión cambiaría de tipo, o una cuenta pasaría a ser inversión. */
const val UNA_INVERSION_NO_CAMBIA_DE_TIPO: String =
    "Una inversión no cuenta en tu disponible ni en tus períodos: no cambia de tipo. Crea la otra cuenta."

/** Los tipos entre los que una cuenta se puede cambiar: Ahorros y Corriente. */
val TIPOS_QUE_SE_INTERCAMBIAN: Set<AccountType> = setOf(AccountType.SAVINGS, AccountType.CHECKING)

/**
 * **¿Se puede pasar [cuenta] a [nuevo]?** `null` = sí; si no, por qué.
 *
 * **Solo entre Ahorros y Corriente** ([TIPOS_QUE_SE_INTERCAMBIAN]): son el mismo dinero para toda la
 * app —se suman igual, cuentan igual en el disponible y reciben lo mismo—, así que cambiar uno por otro
 * solo cambia el rótulo. Cualquier otro cambio **sí mueve plata o lo que Movi hace con la cuenta**:
 *
 * - **Inversión, ni de ida ni de vuelta**: una inversión sale del disponible y de los saldos iniciales
 *   de los períodos (`esDeDinero` en `PeriodosRoutes.kt`), y no es origen de gastos
 *   (`sirveComoCuentaDePlata`). Pasarla a ahorros, o al revés, cambiaría «Te quedan» sin un movimiento.
 * - **Efectivo, ni de ida ni de vuelta**: no recibe extractos ni avisos del banco
 *   (`sirveComoCuentaDePlata`) y se cuadra como «Lo que tienes».
 * - **De plata a deuda o al revés, y entre tarjeta y préstamo**: el saldo de una deuda se lee con el
 *   signo al revés (lo que se debe), así que los mismos movimientos dirían otra cifra, y una tarjeta o
 *   un préstamo llevan sus condiciones aparte (`card_terms`, `credit_terms`).
 * - **Un bien no cambia de tipo**: el server lo fuerza a `INVESTMENT` (ver `Account.bien`).
 *
 * Para todo eso, lo honesto es crear la otra cuenta y pasar la plata con un traspaso.
 */
fun problemaDelCambioDeTipo(cuenta: Account, nuevo: AccountType): String? = when {
    nuevo == cuenta.type -> null
    cuenta.esBien -> UN_BIEN_NO_CAMBIA_DE_TIPO
    esCuentaDeDeuda(cuenta.type) || esCuentaDeDeuda(nuevo) -> UNA_DEUDA_NO_CAMBIA_DE_TIPO
    cuenta.type == AccountType.CASH || nuevo == AccountType.CASH -> EL_EFECTIVO_NO_CAMBIA_DE_TIPO
    cuenta.type == AccountType.INVESTMENT || nuevo == AccountType.INVESTMENT -> UNA_INVERSION_NO_CAMBIA_DE_TIPO
    cuenta.type in TIPOS_QUE_SE_INTERCAMBIAN && nuevo in TIPOS_QUE_SE_INTERCAMBIAN -> null
    else -> UNA_DEUDA_NO_CAMBIA_DE_TIPO
}

/**
 * **¿Por qué el tipo de [cuenta] no se ofrece para cambiar?** `null` si se ofrece (Ahorros y
 * Corriente); si no, la razón que la hoja escribe junto al tipo fijo.
 */
fun porQueElTipoEsFijo(cuenta: Account): String? = when {
    cuenta.esBien -> UN_BIEN_NO_CAMBIA_DE_TIPO
    cuenta.type in TIPOS_QUE_SE_INTERCAMBIAN -> null
    esCuentaDeDeuda(cuenta.type) -> UNA_DEUDA_NO_CAMBIA_DE_TIPO
    cuenta.type == AccountType.CASH -> EL_EFECTIVO_NO_CAMBIA_DE_TIPO
    else -> UNA_INVERSION_NO_CAMBIA_DE_TIPO
}

/**
 * **¿La hoja de editar ofrece «Saldo de hoy»?** Donde «Cuadrar con el banco» cuadra con el ajuste de
 * cuentas: Dinero e Inversión, nunca un bien ([sePuedeCuadrar]). Una tarjeta se cuadra con «Ajustar
 * saldo» de su ficha (la cifra que se pide es la deuda total, no un saldo) y un préstamo en Créditos.
 */
fun seEditaElSaldo(cuenta: Account): Boolean = sePuedeCuadrar(cuenta)

/**
 * Lo que dice la hoja si se cambia el saldo de una cuenta creada sin señal que todavía no llegó al
 * server: el ajuste lo calcula él contra sus movimientos, así que espera a la conexión.
 */
const val SALDO_DE_UNA_CUENTA_SIN_SUBIR: String =
    "Esta cuenta todavía no llegó a Movi: el saldo se cuadra cuando tengas conexión. Guarda el resto sin cambiar el saldo."

/** Un número de cuenta en un nombre: una corrida de cuatro o más dígitos («Fiducuenta 9586»). */
private val NUMERO_EN_EL_NOMBRE = Regex("""\d{4,}""")

/**
 * **Lo que el nombre de una cuenta le enseña a Movi**: los últimos cuatro de cada número que lleva
 * («Master Black 3684» → 3684) y «Skandia» si lo dice.
 *
 * El nombre no es solo un rótulo. Movi reconoce la cuenta **por lo que su nombre dice**, en vivo y sin
 * guardar nada aparte:
 * - los avisos del banco: «tu cuenta \*8133» es tuya si una cuenta se llama «… 8133»
 *   (`numerosPropios` y `cuentaPropiaPorNumero`);
 * - los extractos: la fila en dólares va a la hermana con los mismos cuatro dígitos
 *   (`cuentaParaLaFila`), la AFC de Davibank va a la cuenta con su número y el saldo de Skandia a la
 *   que se llama «Skandia» (`laQueNombra`);
 * - el extracto de un crédito, por los cuatro de la obligación.
 *
 * Renombrar no rompe ningún dato —todo se cruza por id— pero, si el nombre nuevo pierde uno de estos,
 * Movi deja de reconocer la cuenta por ahí (el extracto la pide a mano; el aviso no la reconoce como
 * tuya). Por eso la hoja lo dice antes de guardar ([loQueDejaDeReconocerse]).
 */
fun loQueReconoceElNombre(nombre: String): Set<String> = buildSet {
    NUMERO_EN_EL_NOMBRE.findAll(nombre).forEach { add(it.value.takeLast(4)) }
    if ("skandia" in palabrasDeMarca(nombre)) add("Skandia")
}

/** Lo que el nombre [antes] le enseñaba a Movi y el nombre [despues] ya no. Vacío = nada se pierde. */
fun loQueDejaDeReconocerse(antes: String, despues: String): List<String> =
    (loQueReconoceElNombre(antes) - loQueReconoceElNombre(despues)).sorted()

/** Un número de cuenta de cuatro dígitos sueltos («3684» en «Master Black 3684»; no en «02955068133»). */
private val CUATRO_DIGITOS_SUELTOS = Regex("""(?<!\d)\d{4}(?!\d)""")

/** El nombre sin la moneda al final: «Master Black 3684 USD» → «Master Black 3684». */
private fun sinLaMonedaDelNombre(nombre: String): String =
    nombre.replace(Regex("""\s+(USD|COP)$""", RegexOption.IGNORE_CASE), "").trim()

/**
 * **¿[otra] es la hermana en otra moneda de [elegida], por el nombre?** La regla con que la importación
 * de un extracto manda una fila en otra moneda a su cuenta hermana (`cuentaParaLaFila` en
 * `StatementRoutes.kt`, que la llama): los **últimos cuatro dígitos sueltos** del nombre de [elegida]
 * aparecen sueltos en el de [otra] («Master Black 3684» ↔ «Master Black 3684 USD») o los dos nombres
 * son el mismo sin la moneda al final. Una corrida de 5+ dígitos («02955068133») no cuenta como número.
 * La moneda y el tipo los mira quien llama. Pura.
 */
fun emparejaEnOtraMoneda(elegida: String, otra: String): Boolean {
    val digitos = CUATRO_DIGITOS_SUELTOS.findAll(elegida).lastOrNull()?.value
    return (digitos != null && Regex("""(?<!\d)$digitos(?!\d)""").containsMatchIn(otra)) ||
        sinLaMonedaDelNombre(otra).equals(sinLaMonedaDelNombre(elegida), ignoreCase = true)
}

/** ¿Se emparejan, en cualquiera de los dos sentidos? (cada una puede ser la del extracto). */
private fun seEmparejan(a: String, b: String): Boolean = emparejaEnOtraMoneda(a, b) || emparejaEnOtraMoneda(b, a)

/**
 * **Las hermanas en otra moneda que dejan de emparejarse** si [cuenta] pasa a llamarse [nombreNuevo]:
 * las de [cuentas] del mismo tipo y otra moneda que hoy se emparejan con ella ([emparejaEnOtraMoneda],
 * en los dos sentidos) y con el nombre nuevo ya no.
 */
fun hermanasQueSeDesemparejan(cuenta: Account, nombreNuevo: String, cuentas: List<Account>): List<Account> =
    cuentas.filter { otra ->
        otra.id != cuenta.id && otra.type == cuenta.type && !otra.currency.equals(cuenta.currency, ignoreCase = true) &&
            seEmparejan(cuenta.name, otra.name) && !seEmparejan(nombreNuevo, otra.name)
    }

/** «La cuenta en USD «Master Black 3684 USD» deja de emparejarse con esta.», o `null`. */
fun avisoDeLasHermanas(cuenta: Account, nombreNuevo: String, cuentas: List<Account>): String? {
    if (nombreNuevo.isBlank()) return null
    return hermanasQueSeDesemparejan(cuenta, nombreNuevo, cuentas)
        .joinToString(" ") { "La cuenta en ${it.currency} «${it.name}» deja de emparejarse con esta." }
        .ifEmpty { null }
}

/**
 * **La línea de aviso de la hoja** cuando el nombre nuevo pierde algo de [loQueDejaDeReconocerse], o
 * `null` si no pierde nada. Dice qué deja de pasar, no prohíbe: el nombre es del dueño.
 */
fun avisoAlRenombrar(antes: String, despues: String): String? {
    val pierde = loQueDejaDeReconocerse(antes, despues)
    if (pierde.isEmpty() || despues.isBlank()) return null
    val dicho = pierde.joinToString(" y ") { if (it.all(Char::isDigit)) "el $it" else "«$it»" }
    return "Movi reconoce esta cuenta en los avisos y extractos por $dicho del nombre: sin eso, deja de reconocerla sola."
}
