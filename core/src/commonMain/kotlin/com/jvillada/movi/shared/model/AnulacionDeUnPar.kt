package com.jvillada.movi.shared.model

/**
 * **Qué le pasa a cada cuenta al anular una de las dos mitades de un par.**
 *
 * ### El defecto que cierra
 *
 * Anular cascadea a las dos patas por `transferId` y revierte cada una **por su propio monto**, así
 * que desde que la deuda baja solo por el capital ([DesgloseDeCuota]) los saldos quedan bien. Lo
 * que quedó mintiendo es lo que la pantalla *dice*: la hoja de anular mostraba el monto de la pata
 * que el dueño tocó y nada más. Tocando la cuota del vehículo desde la cuenta de ahorros leía
 * «$4.215.223» mientras desaparecían **$4.215.223 de la cuenta y $1.733.905 de la deuda**, sin que
 * la pantalla nombrara nunca el segundo número.
 *
 * Es exactamente el mismo defecto que `transferRowSubtitle` arregló en el renglón de Movimientos,
 * por la otra puerta: una sola cifra afirmando ser el efecto entero de la operación. Y es peor acá,
 * porque esta es la pantalla donde se confirma algo que no tiene deshacer.
 *
 * ### Por qué vive en `:core`
 *
 * Decide sobre plata y ya hay dos lugares que cuentan esta historia (el renglón y la hoja). La
 * pantalla solo aporta lo que `:core` no tiene: el **nombre** de la cuenta y el monto **formateado**
 * — ver [textoDeLoQuePasa], que arma la frase entera con esas dos piezas.
 *
 * ### Por qué no hacen falta ni el tipo de cuenta ni la tasa
 *
 * En un par asimétrico el EXPENSE es siempre la plata que salió y el INCOME siempre la deuda que
 * bajó: los pares asimétricos los escribe **únicamente** [pagoDeCuotaLegs], y los escribe así. Un
 * traspaso o un pago de tarjeta son simétricos y no llegan hasta acá ([loQuePasaAlAnular] los
 * devuelve vacíos).
 */
enum class EfectoDeAnular {
    /** La plata vuelve a la cuenta de donde salió: la cuota entera, intereses y seguro incluidos. */
    LA_CUENTA_RECUPERA,

    /**
     * El crédito vuelve a deber lo que esta cuota le había abonado — el **capital**, no la cuota.
     * Los intereses y el seguro de ese mes nunca bajaron la deuda, así que tampoco vuelven.
     */
    LA_DEUDA_VUELVE_A_SUBIR,
}

/** Una de las dos consecuencias de anular. Ver [EfectoDeAnular]. */
data class LoQuePasaAlAnular(
    val accountId: String,
    val monto: Long,
    val currency: String,
    val efecto: EfectoDeAnular,
)

/**
 * Por qué son dos cifras distintas. Sin esta frase, dos números sobre un solo pago se leen como un
 * error de la app y no como lo que son.
 */
const val ANULAR_DESHACE_LAS_DOS_MITADES: String =
    "Este pago tiene dos mitades que no valen lo mismo: de la cuenta salió la cuota entera y la " +
        "deuda bajó solo por lo que abonaste a capital. Al anular vuelven atrás las dos."

/**
 * Las dos consecuencias de anular esta pata, **la del dinero primero**.
 *
 * Devuelve la lista vacía cuando no hay una segunda cifra que nombrar: sin hermana (la lectura
 * falló o no terminó), con una hermana de otro par, o con un par simétrico —un traspaso, un pago de
 * tarjeta, una cuota anterior a este cambio—, donde la cifra de arriba ya dice el efecto entero. Un
 * aviso de más sobre algo que no cambia enseña a ignorarlos.
 *
 * El orden es siempre el mismo se entre por donde se entre: la plata primero, la deuda después. El
 * hecho es uno solo, y contarlo distinto según por qué pantalla se llegó sería contar dos historias
 * del mismo par.
 */
fun loQuePasaAlAnular(pata: FinancialEvent, hermana: FinancialEvent?): List<LoQuePasaAlAnular> {
    if (hermana == null) return emptyList()
    // El enlace es explícito justamente para que esto no sea una adivinanza: emparejar por «mismo
    // monto, mismo día» un día juntaría dos movimientos que no tenían nada que ver.
    if (pata.transferId == null || pata.transferId != hermana.transferId) return emptyList()
    if (pata.amount == hermana.amount) return emptyList()
    val patas = listOf(pata, hermana)
    val dinero = patas.firstOrNull { it.type == TransactionType.EXPENSE } ?: return emptyList()
    val deuda = patas.firstOrNull { it.type == TransactionType.INCOME } ?: return emptyList()
    return listOf(
        LoQuePasaAlAnular(dinero.accountId, dinero.amount, dinero.currency, EfectoDeAnular.LA_CUENTA_RECUPERA),
        LoQuePasaAlAnular(deuda.accountId, deuda.amount, deuda.currency, EfectoDeAnular.LA_DEUDA_VUELVE_A_SUBIR),
    )
}

/**
 * La frase de un efecto, ya lista para pintar.
 *
 * [nombreDeLaCuenta] `null` = «la lista de cuentas todavía no llegó»: se dice el rol y no un nombre
 * inventado, mismo criterio que el subtítulo del renglón de Movimientos. [montoFormateado] lo pone
 * la pantalla porque el formato de plata vive en la UI (`formatMoney`), no acá.
 */
fun textoDeLoQuePasa(
    loQuePasa: LoQuePasaAlAnular,
    nombreDeLaCuenta: String?,
    montoFormateado: String,
): String = when (loQuePasa.efecto) {
    EfectoDeAnular.LA_CUENTA_RECUPERA ->
        "${nombreDeLaCuenta ?: "Tu cuenta"} recupera $montoFormateado"
    EfectoDeAnular.LA_DEUDA_VUELVE_A_SUBIR ->
        if (nombreDeLaCuenta == null) "La deuda vuelve a subir $montoFormateado"
        else "La deuda de $nombreDeLaCuenta vuelve a subir $montoFormateado"
}
