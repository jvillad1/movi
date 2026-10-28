package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # La cuenta con que el dueño anota cada cosa (vuelta 43)
 *
 * El día del dueño: *«Las cuentas por defecto son siempre "Más usada" (Nu, tarjeta de crédito). El
 * arriendo, el mercado y el efectivo arrancaron todos en Nu.»* La hoja de Agregar elegía la cuenta
 * sin mirar QUÉ se estaba anotando. Pero el dueño sí tiene costumbres por cosa: el mercado sale de
 * Bancolombia, el Rappi de Nu. Eso está escrito en lo que ya anotó, igual que la categoría que la
 * memoria de categorías ([MemoriaDeCategorias]) reconoce por la huella del nombre.
 *
 * Esto es la memoria equivalente para la cuenta: **la última cuenta con que anotó cada categoría y
 * cada nombre** (la misma [huellaDeUnMovimiento]), separada por tipo —de un gasto la plata sale, a un
 * ingreso entra, y no son las mismas cuentas—. Gana la más reciente: si se cambió de banco, la
 * costumbre cambia con él desde el primer movimiento.
 *
 * Es pura, en `:core`, para que el server la arme (`GET /api/cuentas/de-cada-uso`) y el cliente la
 * lea con las mismas claves ([claveDeCategoriaParaCuenta], [claveDeNombreParaCuenta]).
 */
@Serializable
data class CuentasDeCadaUso(
    /** [claveDeCategoriaParaCuenta] → id de la cuenta del último movimiento con esa categoría. */
    val porCategoria: Map<String, String> = emptyMap(),
    /** [claveDeNombreParaCuenta] → id de la cuenta del último movimiento con ese nombre. */
    val porNombre: Map<String, String> = emptyMap(),
)

/**
 * La clave de una categoría para [CuentasDeCadaUso.porCategoria], o `null` si la categoría no dice
 * nada de la costumbre: «Otros» (la ausencia de categoría) y las que pone Movi sola —las mismas que la
 * memoria de categorías no aprende ([CATEGORIAS_QUE_NO_SE_APRENDEN])—.
 */
fun claveDeCategoriaParaCuenta(tipo: TransactionType, categoria: String): String? {
    val limpia = categoria.trim()
    if (limpia.isEmpty() || limpia in CATEGORIAS_QUE_NO_SE_APRENDEN || isReservedCategory(limpia)) return null
    val clave = claveComparableDeNombre(limpia)
    return if (clave.isEmpty()) null else "${tipo.name}:$clave"
}

/** La clave de un nombre para [CuentasDeCadaUso.porNombre]: su huella, o `null` si no identifica nada. */
fun claveDeNombreParaCuenta(tipo: TransactionType, nombre: String): String? =
    huellaDeUnMovimiento(nombre)?.let { "${tipo.name}:$it" }

/**
 * **La última cuenta de cada categoría y de cada nombre**, sobre los movimientos vivos del dueño.
 *
 * Cuentan los gastos y los ingresos que él anotó o confirmó: sin traspasos (una pata no es una
 * costumbre de gasto), sin categorías reservadas. El nombre es el texto del banco si lo hay (el que
 * va a volver a llegar), y si no la nota.
 */
fun cuentasDeCadaUso(movimientos: List<FinancialEvent>): CuentasDeCadaUso {
    val porCategoria = mutableMapOf<String, String>()
    val porNombre = mutableMapOf<String, String>()
    movimientos
        .asSequence()
        .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
        .filter { it.transferId == null && !isReservedCategory(it.category) && it.accountId.isNotBlank() }
        // Del más viejo al más nuevo: el último que escribe la clave gana.
        .sortedWith(compareBy<FinancialEvent> { it.timestamp }.thenBy { it.createdAt ?: 0L })
        .forEach { e ->
            claveDeCategoriaParaCuenta(e.type, e.category)?.let { porCategoria[it] = e.accountId }
            val nombre = e.merchant?.takeIf { it.isNotBlank() } ?: e.description
            claveDeNombreParaCuenta(e.type, nombre)?.let { porNombre[it] = e.accountId }
        }
    return CuentasDeCadaUso(porCategoria, porNombre)
}
