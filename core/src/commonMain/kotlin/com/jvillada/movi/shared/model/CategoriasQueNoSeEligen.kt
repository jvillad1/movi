package com.jvillada.movi.shared.model

/*
 * # Las categorías que solo escribe Movi no se ofrecen (vuelta 51)
 *
 * La revisión del 28-oct vio «Cuota de crédito» en el selector completo, entre Comida e Internet. Es una
 * categoría que escribe el flujo de dos patas (la cuota que baja la deuda), igual que «Desembolso de
 * crédito», «Traspaso» y «Pago de tarjeta»: elegirla en un gasto suelto deja un gasto que dice ser una
 * cuota sin la pata que baja el crédito. No es una regla de plata nueva: protege las que ya existen.
 */

/**
 * **Las categorías que ningún selector ofrece**: las reservadas de siempre ([RESERVED_CATEGORIES]:
 * «Traspaso», «Pago de tarjeta», «Saldo inicial», «Ajuste de saldo»…) más las que piden dos patas
 * ([CATEGORIAS_DE_DOS_PATAS]: «Cuota de crédito», «Desembolso de crédito», «Avance de tarjeta»). Una
 * sola lista: la leen las pastillas, la hoja «Todas», Agregar, Revisar aviso, la tarjeta de Por revisar,
 * Repartir y recategorizar en lote, y el server la usa para rechazar elegirlas a mano.
 *
 * «Cuota de crédito» y «Desembolso de crédito» **siguen sin ser reservadas** (cuentan en el mes como
 * cualquier categoría, ver [CUOTA_CATEGORY]): lo único que cambia es que no se eligen a mano.
 */
val CATEGORIAS_QUE_NO_SE_ELIGEN: Set<String> by lazy { RESERVED_CATEGORIES + CATEGORIAS_DE_DOS_PATAS }

private val CLAVES_QUE_NO_SE_ELIGEN: Set<String> by lazy {
    CATEGORIAS_QUE_NO_SE_ELIGEN.map { normalizarParaBuscar(it.trim()) }.toSet()
}

/**
 * ¿[nombre] es una de [CATEGORIAS_QUE_NO_SE_ELIGEN]? Sin distinguir mayúsculas ni tildes, como
 * [isReservedCategory]: «cuota de credito» escrita a mano tampoco pasa.
 */
fun esCategoriaQueNoSeElige(nombre: String): Boolean =
    normalizarParaBuscar(nombre.trim()) in CLAVES_QUE_NO_SE_ELIGEN

/**
 * **Por qué no se pone a mano una categoría de dos patas que hasta la vuelta 51 sí se dejaba**: la cuota
 * de un crédito y el avance de una tarjeta. El desembolso ya tenía su guarda
 * ([DESEMBOLSO_CATEGORY_NOT_MANUAL]) y las reservadas la suya. `null` si [nombre] no es ninguna de las
 * dos. Lo usan `POST /api/events` (un alta a mano), `PUT /api/events/{id}/category`, el lote y el espejo
 * local del teléfono, con el mismo texto.
 */
fun motivoDeLaCategoriaDeDosPatas(nombre: String): String? = when {
    esCategoriaDeCuota(nombre) -> CUOTA_CATEGORY_NOT_MANUAL
    normalizarParaBuscar(nombre.trim()) == normalizarParaBuscar(AVANCE_DE_TARJETA_CATEGORY) -> AVANCE_CATEGORY_NOT_MANUAL
    else -> null
}

const val CUOTA_CATEGORY_NOT_MANUAL: String =
    "«$CUOTA_CATEGORY» la escribe Movi cuando anotas la cuota de un crédito (Agregar → Cuota): suelta, " +
        "la deuda no baja. Elige otra categoría."

const val AVANCE_CATEGORY_NOT_MANUAL: String =
    "«$AVANCE_DE_TARJETA_CATEGORY» la escribe Movi cuando confirmas el avance con la cuenta a la que " +
        "entró la plata. Elige otra categoría."

// ── Vuelta 57 · una cuota sin crédito no se confirma desde un aviso ──────────────────────────────

/*
 * La revisión de la vuelta 56 confirmó una tercera cuota del mes con «Cuota de crédito» elegida (la
 * puso la memoria de categorías) y sin elegir el crédito: Revisar aviso escribió un gasto suelto de una
 * pata en «Cuota de crédito». Contaba como cuota anotada, no saldaba nada y, encima, la memoria lo
 * aprendía y el aviso siguiente llegaba otra vez como «Cuota de crédito · Así lo anotaste la última
 * vez». La vuelta 51 cerró esa puerta para lo MANUAL y dejó pasar los avisos (`SMS`, `OCR`) porque ahí
 * el APK ≤ 1.69 escribía la pata suelta y después la vinculaba (`vincular-deuda`). Desde #439 (APK
 * 1.70+) la cuota de un aviso viaja entera por `DosPatasDelAviso`, así que la pata suelta de un aviso
 * nuevo ya no tiene un camino legítimo. No es una regla de plata nueva: protege la de la cuota.
 */

/**
 * Lo que se le dice a quien confirma un aviso como «Cuota de crédito» sin el crédito: `POST /api/events`
 * con `source = SMS | OCR` (nuevo), `POST /api/sms/grupo/{id}/confirmar` con un `evento` suelto y el
 * espejo local del teléfono contestan 422 con esto.
 */
const val CUOTA_DEL_AVISO_SIN_CREDITO: String =
    "Una cuota se confirma con su crédito: elige cuál es en «Es la cuota de un crédito». Suelta, la deuda no baja."

/**
 * ¿[evento] es la pata suelta de una cuota que viene de un aviso? Un gasto en «Cuota de crédito» sin
 * `transferId`, escrito desde un mensaje del banco o un comprobante. Lo de dos patas (con `transferId`)
 * pasa: es la cuota de verdad.
 *
 * **«Pago de tarjeta» no lleva esta guarda, a propósito** (vuelta 58): una cuota suelta no baja ninguna
 * deuda y cuenta como gasto, así que es un error; un pago de tarjeta suelto es legítimo cuando la tarjeta
 * no está en Movi (se paga a un plástico ajeno o aún no cargado). Es una categoría reservada, no cuenta
 * como gasto, y Revisar aviso lo dice en su pie cuando no se eligió tarjeta.
 */
fun esCuotaSueltaDeUnAviso(evento: FinancialEvent): Boolean =
    (evento.source == EventSource.SMS || evento.source == EventSource.OCR) &&
        evento.transferId == null && esCategoriaDeCuota(evento.category)
