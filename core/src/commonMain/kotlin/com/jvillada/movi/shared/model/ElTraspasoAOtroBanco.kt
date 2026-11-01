package com.jvillada.movi.shared.model

/*
 * # Vuelta 91 · la plata que el dueño se pasa a sí mismo, de un banco a otro
 *
 * El 31-oct-2026 el dueño pasó $1.500.000 de Bancolombia Ahorros 8133 a su cuenta Nu por PSE y le
 * llegaron **tres avisos del mismo movimiento**:
 *
 * 1. el SMS de Bancolombia: «Pagaste $1,500,000.00 a NU Compania de Financiamiento desde tu producto
 *    8133 …» —para Movi, un gasto en «Otros»—;
 * 2. el correo de PSE: «Empresa: NU Compañía de Financiamiento · Descripción: Depósito a tu cuenta NU»;
 * 3. la notificación de Nu: «Recibimos tu depósito por 1.500.000,00 …», que Movi no entendía y ofrecía
 *    «Anotarlo igual» como **ingreso**: $1.500.000 en «Entró» que no entraron.
 *
 * Un traspaso entre cuentas suyas no es ni ingreso ni gasto. Esto es lo que comparten el server (que
 * propone) y la app (que arma las dos patas): cuándo un aviso es el lado que entra de un traspaso
 * propio ([esUnDepositoPropio]), a qué banco va un pago que nombra a un banco ([marcaDelBeneficiario])
 * y a cuál de las cuentas del dueño en ese banco ([destinoEnOtroBanco]).
 */

/**
 * «Recibimos tu depósito por 1.500.000,00: ¡No te preocupes! …» (Nu): la plata que el dueño mismo puso
 * en esa cuenta —un depósito por PSE desde otro banco—, dicha por el banco que la **recibió**. Solo con
 * «recibimos» (revisión de la 91): «Tu depósito de $X a la cuenta de Pedro» es plata que sale hacia un
 * tercero. Lo que manda un tercero tampoco se escribe así («Te llegó dinero de …», «Recibiste $X de …»).
 */
private val DEPOSITO_PROPIO = Regex("""\brecibimos\s+tu\s+dep[oó]sito\b""", RegexOption.IGNORE_CASE)

/** «… a la cuenta de Pedro», «… a nombre de Pedro»: el depósito fue a otra persona, no a una cuenta suya. */
private val A_OTRA_PERSONA = Regex("""\ba\s+(?:la\s+cuenta\s+de|nombre\s+de)\b""", RegexOption.IGNORE_CASE)

/**
 * **¿El aviso es el lado que entra de un traspaso propio?** Un depósito del dueño a su propia cuenta
 * ([DEPOSITO_PROPIO]), que no nombra a otra persona ([A_OTRA_PERSONA]). Solo mira el texto; a qué cuenta
 * entró lo dice el banco que avisa.
 */
fun esUnDepositoPropio(texto: String): Boolean =
    DEPOSITO_PROPIO.containsMatchIn(texto) && !A_OTRA_PERSONA.containsMatchIn(texto)

/**
 * Más ancho que [esUnDepositoPropio]: el texto **habla** de un depósito del dueño («tu depósito», «su
 * depósito»), aunque Movi no lo sepa leer entero. Es lo que decide que «Anotarlo igual» no lo proponga
 * como ingreso (ver `AvisoSinLeer.deposito`). A propósito sin «recarga» ni «depositaste»: «Recargaste
 * tu celular» es un gasto y «Depositaste $X en la cuenta de …» es plata que sale.
 */
private val HABLA_DE_UN_DEPOSITO = Regex("""\b(?:tu|su)\s+dep[oó]sito\b""", RegexOption.IGNORE_CASE)

/** ¿El texto habla de un depósito del dueño? Ver [HABLA_DE_UN_DEPOSITO]. */
fun hablaDeUnDepositoPropio(texto: String): Boolean =
    HABLA_DE_UN_DEPOSITO.containsMatchIn(texto) && !A_OTRA_PERSONA.containsMatchIn(texto)

/** Las redes de tarjeta no son un banco donde se tenga plata: «a VISA» no es un traspaso. */
private val NO_RECIBEN_TRASPASOS = setOf("visa", "mastercard")

/**
 * **El banco o la billetera a la que se le pagó**, por el nombre del beneficiario: «NU Compania de
 * Financiamiento» → Nu, «DAVIVIENDA» → Davivienda, «BANCO DE BOGOTA» → Banco de Bogotá. Solo con los
 * patrones seguros del catálogo ([marcaDelTexto]): «Coomeva Medicina Prepagada» no es Bancoomeva ni
 * «Banco de Occidente S A ATH» una cuenta (es un crédito). `null` si no nombra un banco.
 */
fun marcaDelBeneficiario(beneficiario: String): Marca? =
    marcaDelTexto(beneficiario)?.takeIf { it.rubro == RubroDeMarca.BANCOS_Y_BILLETERAS && it.id !in NO_RECIBEN_TRASPASOS }

/** Las marcas que son del mismo banco: la tarjeta o la fiducia que el banco emite con otro nombre. */
private val GRUPOS_DE_BANCOS: Map<String, String> = mapOf(
    "amex" to "bancolombia",
    "fiducuenta" to "bancolombia",
    "nequi" to "bancolombia",
    "daviplata" to "davivienda",
    "davibank" to "davivienda",
)

/** El banco al que pertenece una marca: la AMEX y la Fiducuenta son de Bancolombia. */
fun grupoDelBanco(id: String): String = GRUPOS_DE_BANCOS[id] ?: id

/**
 * **El banco que mandó el aviso**: por su rótulo («Notificación · Nu», «85540» → Bancolombia) o, en
 * un SMS con otro código, por cómo empieza el texto («Bancolombia: Pagaste …»). `null` si no se sabe
 * (el correo de PSE lo manda ACH, que no es un banco).
 */
fun marcaDelRemitente(origen: String, texto: String): Marca? {
    nombreDelOrigenDeCaptura(origen)?.let { nombre -> marcaDelBanco(nombre)?.let { return it } }
    val prefijo = texto.substringBefore(':', missingDelimiterValue = "").trim()
    prefijo.takeIf { it.isNotEmpty() && it.length <= 30 }?.let(::marcaDelBanco)?.let { return it }
    // Vuelta 99: Davivienda firma sin dos puntos («DAVIVIENDA Descuento en internet, $64,900,…»), y su
    // código corto (871…) no es uno que Movi conozca. La primera palabra, si es un banco.
    val primeraPalabra = primeraPalabraDelTexto.find(texto)?.value ?: return null
    return marcaDelBanco(primeraPalabra)?.takeIf { it.rubro == RubroDeMarca.BANCOS_Y_BILLETERAS }
}

private val primeraPalabraDelTexto = Regex("""^\s*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+""")

/**
 * Las cuentas del dueño que son de [marca], por su nombre ([marcaDeLaCuenta]): las de plata (sin el
 * efectivo ni los bienes), las tarjetas de crédito y los créditos (revisión de la 91: «Pagaste $X a
 * DAVIBANK» puede ser la cuota del hipotecario, no un depósito a la AFC).
 */
data class CuentasDelBanco(val dePlata: List<Account>, val tarjetas: List<Account>, val creditos: List<Account> = emptyList())

fun cuentasDelBanco(marca: Marca, cuentas: List<Account>): CuentasDelBanco {
    val delBanco = cuentas.filter { !it.esBien && marcaDeLaCuenta(it)?.id == marca.id }
    return CuentasDelBanco(
        dePlata = delBanco.filter { it.type.group == AccountGroup.DINERO && it.type != AccountType.CASH },
        tarjetas = delBanco.filter { it.type == AccountType.CREDIT_CARD },
        creditos = delBanco.filter { it.type == AccountType.LOAN },
    )
}

/**
 * A dónde fue un traspaso a otro banco: [haciaId] la cuenta propuesta (`null` = a elegir) y
 * [posibles] las cuentas suyas de ese banco que se ofrecen primero (las de plata y, si las hay, las
 * tarjetas y los créditos: elegir una tarjeta lo vuelve su pago; un crédito, su cuota).
 */
data class DestinoEnOtroBanco(val haciaId: String?, val posibles: List<String>)

/**
 * **¿El beneficiario de este pago es una cuenta del propio dueño en otro banco?** Las reglas, en
 * orden:
 *
 * 1. El beneficiario nombra un banco o billetera ([marcaDelBeneficiario]) que **no es el que avisa**
 *    ([remitente]: «Pagaste $X a BANCOLOMBIA» en un SMS de Bancolombia es otra cosa — un crédito, una
 *    comisión—).
 * 2. El dueño tiene **al menos una cuenta de plata** en ese banco ([cuentasDelBanco]). Sin ninguna, el
 *    pago sigue siendo lo que era (a una tarjeta o un crédito de ese banco se le paga por su camino).
 * 3. **El nombre solo no elige el destino** (revisión de la 91): «Pagaste $9.147.408 a DAVIBANK desde
 *    tu producto 8133» es la cuota del hipotecario, no un depósito a la «AFC Davibank 9497». El
 *    destino queda **a elegir**, con las cuentas de ese banco primero —las de plata, las tarjetas y los
 *    créditos—, salvo que el aviso ([texto]) escriba los últimos cuatro de **una** cuenta de plata de
 *    ese banco: esa es [DestinoEnOtroBanco.haciaId]. La otra evidencia (el depósito que entró en el mismo
 *    pago, el «Depósito a tu cuenta NU» del correo de PSE) la ponen quienes llaman.
 *
 * `null` si no es un traspaso propio. Pura.
 */
fun destinoEnOtroBanco(beneficiario: String, cuentas: List<Account>, remitente: Marca?, texto: String = ""): DestinoEnOtroBanco? {
    val marca = marcaDelBeneficiario(beneficiario) ?: return null
    if (remitente != null && grupoDelBanco(remitente.id) == grupoDelBanco(marca.id)) return null
    val (dePlata, tarjetas, creditos) = cuentasDelBanco(marca, cuentas)
    if (dePlata.isEmpty()) return null
    val numeros = NUMEROS_DEL_AVISO.findAll(texto).map { it.value.takeLast(4) }.toSet()
    val hacia = dePlata.filter { cuenta -> NUMEROS_DEL_AVISO.findAll(cuenta.name).any { it.value.takeLast(4) in numeros } }.singleOrNull()
    return DestinoEnOtroBanco(haciaId = hacia?.id, posibles = (dePlata + tarjetas + creditos).map { it.id })
}

/** Las corridas de 4+ dígitos de un aviso o del nombre de una cuenta («AFC Davibank 9497» → 9497). */
private val NUMEROS_DEL_AVISO = Regex("""\d{4,}""")

/**
 * **La cuenta a la que entró un depósito propio** ([esUnDepositoPropio]): la única cuenta de plata del
 * dueño en el banco que lo avisa («Notificación · Nu» → «Nu»). `null` si no tiene ninguna o tiene
 * varias: entonces la elige él.
 */
fun cuentaDelDepositoPropio(origen: String, texto: String, cuentas: List<Account>): Account? {
    if (!esUnDepositoPropio(texto)) return null
    val marca = marcaDelRemitente(origen, texto) ?: return null
    return cuentasDelBanco(marca, cuentas).dePlata.singleOrNull()
}

/**
 * **El pago de la tarjeta de Nu, avisado por Nu**: «¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos
 * tu pago por $1.306.937,91 …». La plata sale de la cuenta de ahorros Nu (de ahí paga la app) y entra a
 * la tarjeta: son dos patas, nunca un «pago de tarjeta» suelto dentro de la tarjeta, que le SUBÍA la
 * deuda (así quedó el de $150.000 del 31-oct).
 */
private val PAGO_DE_LA_TARJETA_NU = Regex(
    """\bpagaste\s+tu\s+tarjeta\b|\brecibimos\s+tu\s+pago\b""",
    RegexOption.IGNORE_CASE,
)

/** ¿[texto], avisado desde [origen], es el pago de la tarjeta de Nu? El origen tiene que nombrar a Nu. */
fun esElPagoDeLaTarjetaNu(origen: String, texto: String): Boolean =
    marcaDelBanco(origen)?.id == "nu" && PAGO_DE_LA_TARJETA_NU.containsMatchIn(texto)

/**
 * Las dos puntas del pago de la tarjeta Nu ([esElPagoDeLaTarjetaNu]): la **única** cuenta de plata Nu
 * (de donde sale) y la **única** tarjeta Nu en la moneda del pago (la que baja). Cada una `null` si no
 * hay exactamente una: la elige el dueño.
 */
data class PuntasDelPagoNu(val desde: Account?, val tarjeta: Account?)

fun puntasDelPagoDeLaTarjetaNu(cuentas: List<Account>, moneda: String): PuntasDelPagoNu {
    val nu = BANCOS_CONOCIDOS.first { it.id == "nu" }
    val (dePlata, tarjetas) = cuentasDelBanco(nu, cuentas)
    return PuntasDelPagoNu(
        desde = dePlata.singleOrNull(),
        tarjeta = tarjetas.filter { it.currency == moneda }.singleOrNull(),
    )
}

/**
 * Vuelta 91 · **la comisión del traslado** que cobra Bancolombia al pasar plata por PSE a otro banco
 * («COMISION TRASLADO OTROS BANCOS» + su IVA). No se avisa: llega en el extracto. Movi no la calcula ni
 * la anota; solo lo dice al confirmar un traspaso así.
 */
const val COMISION_DEL_TRASLADO: String = "Bancolombia suele cobrar la comisión del traslado: llega en el extracto."

/**
 * ¿Hay que decir [COMISION_DEL_TRASLADO]? Un traspaso que sale de una cuenta de Bancolombia hacia una
 * de otro banco, avisado como un pago por PSE (el correo de PSE, o el «Pagaste $X a … desde tu
 * producto N» de Bancolombia). Pura.
 */
fun diceLaComisionDelTraslado(origen: Account, destino: Account, textos: List<String>): Boolean {
    val desde = marcaDeLaCuenta(origen)?.id ?: return false
    if (grupoDelBanco(desde) != "bancolombia") return false
    val hacia = marcaDeLaCuenta(destino)?.id
    if (hacia != null && grupoDelBanco(hacia) == "bancolombia") return false
    return textos.any { PAGO_POR_PSE.containsMatchIn(it) }
}

/** «PSE - Transacción Aprobada», o el «Pagaste $X a … desde tu producto N» con que Bancolombia avisa un PSE. */
private val PAGO_POR_PSE = Regex("""\bPSE\b|\bpagaste\s+\S+\s+a\s+.+\s+desde\s+tu\s+producto\b""", RegexOption.IGNORE_CASE)
