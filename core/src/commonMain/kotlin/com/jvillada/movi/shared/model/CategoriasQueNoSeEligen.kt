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
