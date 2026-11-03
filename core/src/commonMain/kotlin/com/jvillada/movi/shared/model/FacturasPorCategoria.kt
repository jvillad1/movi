package com.jvillada.movi.shared.model

/*
 * # Las facturas del período, por categoría (vuelta 120)
 *
 * Pedido de los probadores, aprobado por el dueño el 2-nov-2026: ver los pagos que se repiten cada período
 * **agrupados por categoría** («¿cuánto se me va en vivienda?, ¿cuánto en créditos?»). Es una **segunda
 * vista**: la lista de siempre (lo vencido primero, después por fecha, lo pagado al final) contesta «¿qué
 * tengo que pagar ahora?» y se queda como está.
 *
 * **Sin cifras nuevas.** Cada grupo suma con las MISMAS funciones de la tarjeta «Falta por pagar»:
 * [faltaPorPagar] y [yaPagadoEnElPeriodo]. Las dos son sumas fila por fila, así que repartir las filas en
 * grupos que no se pisan y sumar cada grupo da exactamente la cifra de la tarjeta (lo vigila
 * `FacturasPorCategoriaTest`). Nada de esto se suma a mano.
 *
 * Tres decisiones:
 *
 * 1. **Las deudas van juntas, aunque su categoría diga otra cosa.** La cuota de un crédito (regla
 *    `credit_…`, o una regla en «Cuota de crédito») va a «Créditos»; el pago de una tarjeta (regla
 *    `card_…`, una fila cuyo monto es el saldo, o una regla en «Pago de tarjeta») a «Tarjetas».
 * 2. **El abono planeado va con su deuda**: a «Tarjetas» si es a una tarjeta
 *    ([PagoDelPeriodo.abonoATarjeta], que pone la pantalla), si no a «Créditos». No suma en el «falta» del
 *    grupo —no es un fijo, como en la tarjeta amarilla— pero se ve junto a la cuota de la misma deuda, que
 *    es donde el dueño lo busca.
 * 3. **Los ingresos no entran.** Un sueldo no se paga: llega. Siguen en «Tus ingresos del período», debajo,
 *    igual que en la vista por fecha. Mezclarlos en un grupo de esta lista haría pensar que suman en lo que
 *    falta.
 */

/** El nombre del grupo de las cuotas de los créditos (y sus abonos planeados). */
const val GRUPO_DE_LOS_CREDITOS: String = "Créditos"

/** El nombre del grupo de los pagos de las tarjetas (y sus abonos planeados). */
const val GRUPO_DE_LAS_TARJETAS: String = "Tarjetas"

/** El nombre del grupo de las facturas sin categoría. */
const val GRUPO_SIN_CATEGORIA: String = "Otros"

/** Qué clase de grupo es: una categoría del dueño, o uno de los dos de deudas. */
enum class ClaseDeGrupoDeFacturas { CATEGORIA, CREDITOS, TARJETAS }

/**
 * **Un grupo de la vista «Por categoría»**: su nombre, sus filas y lo que suman.
 *
 * @property clave estable entre lecturas y períodos (`categoria:mercado`, `creditos`, `tarjetas`): con ella
 *   se recuerda si el dueño lo plegó.
 * @property filas en el orden en que llegaron a [facturasPorCategoria] (la pantalla le pasa las de la lista
 *   por fecha, así que adentro de cada grupo se leen igual: lo vencido, lo que vence por fecha, lo pagado).
 */
data class GrupoDeFacturas(
    val clave: String,
    val nombre: String,
    val clase: ClaseDeGrupoDeFacturas,
    val filas: List<PagoDelPeriodo>,
) {
    /** Lo que falta de este grupo: [faltaPorPagar] sobre sus filas. Sin abonos ni saldos de tarjeta. */
    val falta: Long get() = faltaPorPagar(filas)

    /** Lo ya pagado de este grupo: [yaPagadoEnElPeriodo] sobre sus filas («Llevas $X pagados»). */
    val pagado: Long get() = yaPagadoEnElPeriodo(filas)

    /** El total del período de este grupo: lo que falta y lo pagado, como el «de $X» de la tarjeta amarilla. */
    val total: Long get() = falta + pagado

    /** Ningún pago del grupo queda por hacer ([pagosPendientes] vacío): el encabezado dice «Pagado ✓». */
    val todoPagado: Boolean get() = pagosPendientes(filas).isEmpty()

    /**
     * Algo del grupo sigue abierto, abonos planeados incluidos. Decide si arranca desplegado: lo que pide
     * algo del dueño se ve; lo resuelto se pliega.
     */
    val tieneAlgoPendiente: Boolean get() = filas.any { !it.hecho }
}

/** **A qué clase de grupo va [pago]** (ver la decisión 1 del encabezado del archivo). Pura. */
fun claseDeGrupoDe(pago: PagoDelPeriodo): ClaseDeGrupoDeFacturas = when {
    pago.ruleId.startsWith(CREDIT_RULE_PREFIX) -> ClaseDeGrupoDeFacturas.CREDITOS
    pago.ruleId.startsWith(CARD_RULE_PREFIX) || pago.montoEsSaldo -> ClaseDeGrupoDeFacturas.TARJETAS
    pago.esAbono -> if (pago.abonoATarjeta) ClaseDeGrupoDeFacturas.TARJETAS else ClaseDeGrupoDeFacturas.CREDITOS
    pago.categoria.trim() == CUOTA_CATEGORY -> ClaseDeGrupoDeFacturas.CREDITOS
    pago.categoria.trim() == CARD_PAYMENT_CATEGORY -> ClaseDeGrupoDeFacturas.TARJETAS
    else -> ClaseDeGrupoDeFacturas.CATEGORIA
}

/**
 * **Las facturas del período agrupadas por categoría**, ordenadas por su total de mayor a menor (a igual
 * total, por nombre). Los ingresos no entran (ver la decisión 3). Dos categorías que solo difieren en
 * mayúsculas, tildes o espacios («Servicios» y «servicios ») son un grupo, con el nombre de la primera fila.
 *
 * La suma de [GrupoDeFacturas.falta] de todos los grupos es [faltaPorPagar] de [filas], y la de
 * [GrupoDeFacturas.pagado], [yaPagadoEnElPeriodo]: cada fila va a un solo grupo. Pura.
 */
fun facturasPorCategoria(filas: List<PagoDelPeriodo>): List<GrupoDeFacturas> {
    val grupos = LinkedHashMap<String, Pair<String, ClaseDeGrupoDeFacturas>>()
    val filasPorClave = LinkedHashMap<String, MutableList<PagoDelPeriodo>>()
    filas.filter { !it.esIngreso }.forEach { pago ->
        val clase = claseDeGrupoDe(pago)
        val (clave, nombre) = when (clase) {
            ClaseDeGrupoDeFacturas.CREDITOS -> "creditos" to GRUPO_DE_LOS_CREDITOS
            ClaseDeGrupoDeFacturas.TARJETAS -> "tarjetas" to GRUPO_DE_LAS_TARJETAS
            ClaseDeGrupoDeFacturas.CATEGORIA -> {
                val nombre = pago.categoria.trim().ifBlank { GRUPO_SIN_CATEGORIA }
                "categoria:${normalizarParaBuscar(nombre)}" to nombre
            }
        }
        grupos.getOrPut(clave) { nombre to clase }
        filasPorClave.getOrPut(clave) { mutableListOf() }.add(pago)
    }
    return grupos.map { (clave, nombreYClase) ->
        GrupoDeFacturas(clave = clave, nombre = nombreYClase.first, clase = nombreYClase.second, filas = filasPorClave.getValue(clave))
    }.sortedWith(compareByDescending<GrupoDeFacturas> { it.total }.thenBy { normalizarParaBuscar(it.nombre) })
}
