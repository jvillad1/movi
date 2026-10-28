package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # La bandeja que se vacía sola: qué avisos Movi ya entendió
 *
 * Revisión «Lo que le falta a Movi» (29-sep-2026), ítem #8: cada aviso del banco costaba dos
 * pantallas —«Revisar» y «Confirmar»— y con tres SMS eran de 10 a 13 toques, aunque Movi ya supiera
 * todo: el monto, la cuenta y la categoría. Esto decide **cuándo Movi sabe lo suficiente** para que
 * la fila de «Por revisar» ofrezca «Confirmar» directo, y el lote «Confirmar los N que Movi
 * entendió».
 *
 * Lo que la bandeja necesita para decidirlo lo trae `GET /api/sms/propuestas` en una sola lectura
 * ([PropuestasDeLaBandeja]): la misma propuesta que `GET /api/sms/{id}/parse` y las mismas
 * coincidencias que `GET /api/sms/{id}/coincidencias`, para cada pago pendiente. La cuenta la
 * resuelve la app con las mismas funciones que Reconciliar, porque es ahí donde viven.
 */

/**
 * **Lo que Movi propone para un pago pendiente**, armado igual que en Reconciliar.
 *
 * @property avisoId el aviso del que sale la propuesta: el aviso suelto, o el que más dice de un pago
 *   avisado varias veces (`propuestaDelGrupo` en el server, el mismo que `GrupoDeAvisos.propuestaDe`).
 * @property grupoId el pago al que pertenece, si es uno de varios avisos.
 * @property leido lo que contesta `GET /api/sms/{avisoId}/parse`; `null` si el aviso no trae un
 *   movimiento ([noSePudoLeer] dice por qué).
 * @property coincidencias lo que contesta `GET /api/sms/{avisoId}/coincidencias`: los movimientos ya
 *   anotados que parecen este («¿Ya lo anotaste?»).
 * @property destinoSinNombre el aviso nombra una cuenta o una llave de alguien que el dueño no tiene
 *   guardado («¿De quién es la cuenta ·0756?»): la misma condición con que la tarjeta ofrece guardarlo.
 */
@Serializable
data class PropuestaDelAviso(
    val avisoId: String,
    val grupoId: String? = null,
    val leido: ParsedSms? = null,
    val noSePudoLeer: String? = null,
    val coincidencias: List<FinancialEvent> = emptyList(),
    val destinoSinNombre: Boolean = false,
    /**
     * El envío es **exactamente** la suma de 2 o 3 pagos que el dueño asoció a ese tercero (ver
     * `RepartirUnEnvio.kt`): Reconciliar propone repartirlo, así que la fila no lo anota como uno.
     */
    val seReparte: Boolean = false,
    /**
     * El aviso no se pudo leer pero trae un monto que se puede anotar igual ([AvisoSinLeer], ver
     * `AnotarloIgual.kt`). La fila **no** lo confirma: sin [leido], [dudasDelAviso] dice
     * [DudaDelAviso.NO_SE_LEYO] y lo manda a Reconciliar, que es donde se anota igual.
     */
    val sinLeer: AvisoSinLeer? = null,
)

/**
 * La respuesta de `GET /api/sms/propuestas`: una [PropuestaDelAviso] por pago pendiente, y lo que la
 * app necesita para decidir sin otra lectura.
 *
 * @property categoriasDelDueno las categorías que el dueño usa en sus movimientos: una categoría que
 *   él nunca usó no se anota sin que la mire (se crearía sola).
 * @property inicioDeLasCuentas cuenta → el primer movimiento que tiene en Movi (epoch ms). Un aviso
 *   de antes ya está dentro del saldo con que arrancó la cuenta: confirmarlo lo contaría dos veces,
 *   y por eso Reconciliar lo advierte (ver [inicioDeLaCuentaSiElSmsEsAnterior]).
 */
@Serializable
data class PropuestasDeLaBandeja(
    val propuestas: List<PropuestaDelAviso> = emptyList(),
    val categoriasDelDueno: List<String> = emptyList(),
    val inicioDeLasCuentas: Map<String, Long> = emptyMap(),
)

/** **Por qué un aviso todavía no se puede confirmar desde la fila.** Cada una manda a «Revisar». */
enum class DudaDelAviso {
    /** El aviso no trae un movimiento, o no se pudo leer. */
    NO_SE_LEYO,

    /** Sin un monto mayor que cero no hay nada que anotar. */
    SIN_MONTO,

    /** Movi no sabe de qué cuenta es: la adivinó (la primera de banco) o no tiene ninguna. */
    SIN_CUENTA,

    /** La categoría no la propuso la memoria del dueño ni una regla con una categoría suya. */
    SIN_CATEGORIA,

    /** «¿Ya lo anotaste?» tiene candidatos: anotarlo de nuevo podría duplicarlo. */
    YA_ANOTADO,

    /** Nombra a alguien que el dueño no ha guardado: primero hay que decir quién es. */
    DESTINO_SIN_NOMBRE,

    /** Es de dos patas (pago de tarjeta, cuota, traspaso, avance): hay que elegir a dónde entra. */
    DOS_PATAS,

    /** El aviso es de antes de que la cuenta arrancara en Movi: se contaría dos veces. */
    ANTES_DE_LA_CUENTA,

    /** Movi propone repartir el envío (la suma exacta de varios pagos, las cuotas que cubre con lo que sobra, o como la última vez): se reparte en Reconciliar. */
    SE_REPARTE,
}

/**
 * Las categorías que piden elegir **dos patas** en Reconciliar: el pago de una tarjeta, la cuota de un
 * crédito, un traspaso entre cuentas suyas y el avance de una tarjeta. Ninguna se confirma desde la
 * fila: un pago de tarjeta suelto contaría como gasto, y una cuota sin su crédito no baja la deuda.
 */
val CATEGORIAS_DE_DOS_PATAS: Set<String> =
    setOf(CARD_PAYMENT_CATEGORY, CUOTA_CATEGORY, TRANSFER_CATEGORY, AVANCE_DE_TARJETA_CATEGORY, DESEMBOLSO_CATEGORY)

/** «Otros» es la ausencia de categoría (ver `SIN_CATEGORIA` del parser): nunca cuenta como entendida. */
private val CATEGORIAS_QUE_NO_DICEN_NADA: Set<String> = setOf("otros", "otro", "")

/** ¿[categoria] no dice nada —«Otros», o vacía—? Vuelta 46: Revisar aviso la pide en vez de mostrarla. */
fun categoriaQueNoDiceNada(categoria: String?): Boolean =
    categoria == null || claveComparableDeNombre(categoria.trim()) in CATEGORIAS_QUE_NO_DICEN_NADA

/**
 * **¿La categoría de [leido] la decidió algo que sabe del dueño?** Sí cuando la propuso su memoria
 * (`aprendidoDe`: «Así lo anotaste 4 veces», o el motivo de un envío), y sí cuando una regla
 * —las palabras clave, el correo de PSE, los cargos del banco— propuso una categoría que **él ya
 * usa**. Una del catálogo que nunca usó («Restaurantes» para quien anota «Comida») no: confirmarla
 * sin mirar le crearía una categoría paralela.
 */
fun categoriaEntendida(leido: ParsedSms, categoriasDelDueno: Collection<String>): Boolean {
    val clave = claveComparableDeNombre(leido.category.trim())
    if (clave in CATEGORIAS_QUE_NO_DICEN_NADA) return false
    if (leido.aprendidoDe != null) return true
    return categoriasDelDueno.any { claveComparableDeNombre(it.trim()) == clave }
}

/**
 * **Las dudas de un aviso**, o un conjunto vacío si Movi lo entendió y se puede confirmar desde la fila.
 *
 * El criterio, entero:
 *
 * 1. Hay **monto** (mayor que cero), **cuenta resuelta** por un dato —el número que escribió el banco,
 *    el banco o la marca, o lo que Movi sabe de ese pago— y no por defecto ([cuentaSegura]), y una
 *    **categoría** de su memoria o de una regla con una categoría suya ([categoriaEntendida]).
 * 2. Y **ninguna** de estas dudas: «¿Ya lo anotaste?» con candidatos, un destino sin nombre, o un pago
 *    que necesita elegir patas —por la categoría ([CATEGORIAS_DE_DOS_PATAS]), por lo que el server ya
 *    dedujo (la deuda, el traspaso hacia o desde una cuenta suya) o porque el aviso nombra otra
 *    cuenta suya ([nombraOtraCuentaSuya], que resuelve la app)—.
 * 3. Y no es de antes de que la cuenta arrancara en Movi ([antesDeLaCuenta]), ni un envío que es la
 *    suma exacta de varios pagos del período ([seReparte]: ese se reparte en Reconciliar).
 *
 * Lo usan la fila y el lote: los dos confirman solo lo que sale vacío de acá.
 */
fun dudasDelAviso(
    leido: ParsedSms?,
    coincidencias: Int,
    destinoSinNombre: Boolean,
    cuentaSegura: Boolean,
    nombraOtraCuentaSuya: Boolean,
    categoriasDelDueno: Collection<String>,
    antesDeLaCuenta: Boolean = false,
    seReparte: Boolean = false,
): Set<DudaDelAviso> {
    if (leido == null) return setOf(DudaDelAviso.NO_SE_LEYO)
    val dudas = mutableSetOf<DudaDelAviso>()
    if (leido.amount <= 0.0) dudas += DudaDelAviso.SIN_MONTO
    if (!cuentaSegura) dudas += DudaDelAviso.SIN_CUENTA
    if (!categoriaEntendida(leido, categoriasDelDueno)) dudas += DudaDelAviso.SIN_CATEGORIA
    if (coincidencias > 0) dudas += DudaDelAviso.YA_ANOTADO
    if (destinoSinNombre) dudas += DudaDelAviso.DESTINO_SIN_NOMBRE
    val dosPatas = leido.category in CATEGORIAS_DE_DOS_PATAS ||
        leido.deudaSugeridaId != null || leido.traspasoHaciaId != null || leido.traspasoDesdeId != null ||
        nombraOtraCuentaSuya
    if (dosPatas) dudas += DudaDelAviso.DOS_PATAS
    if (antesDeLaCuenta) dudas += DudaDelAviso.ANTES_DE_LA_CUENTA
    if (seReparte) dudas += DudaDelAviso.SE_REPARTE
    return dudas
}

/**
 * ¿El aviso es de antes del primer movimiento de la cuenta en Movi? La misma regla de
 * [inicioDeLaCuentaSiElSmsEsAnterior], con el primer movimiento ya leído por el server.
 */
fun esAntesDeLaCuenta(momentoDelAviso: Long, inicioDeLaCuenta: Long?): Boolean {
    if (inicioDeLaCuenta == null) return false
    val falso = FinancialEvent(
        id = "", accountId = "", type = TransactionType.EXPENSE, amount = 0L, category = "", description = "",
        timestamp = inicioDeLaCuenta,
    )
    return inicioDeLaCuentaSiElSmsEsAnterior(momentoDelAviso, listOf(falso)) != null
}
