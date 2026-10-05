package com.jvillada.movi.shared.model

/*
 * # Lo que el banco cobra o abona por su cuenta
 *
 * El 4x1000, la retención en la fuente, la cuota de manejo y su IVA, la comisión de un envío a otro
 * banco, los intereses de una tarjeta y los intereses o rendimientos de una cuenta de ahorros no
 * llegan por SMS, ni por notificación, ni por correo: solo aparecen en la app del banco y en el
 * extracto. En cinco semanas el dueño anotó 26 a mano (o Claude por SQL), y siempre en las mismas
 * tres categorías suyas: «Impuestos», «Comisiones del banco» e «Ingreso».
 *
 * Acá vive **el reconocimiento del nombre**, y nada más: Movi reconoce lo que el banco DICE. No
 * calcula ni estima ninguna cifra —ya se probó sacar el 4x1000 como 0,4 % de lo que salió y no da,
 * por las exenciones—; el monto es siempre el de la fila del banco.
 */

/** Las tres clases de cargo o abono que el banco hace solo. */
enum class ClaseDeCargo { IMPUESTO, COMISION, RENDIMIENTO }

/** La categoría del dueño para el 4x1000 y la retención en la fuente. */
const val CATEGORIA_DE_IMPUESTOS = "Impuestos"

/** La categoría del dueño para la cuota de manejo, su IVA, las comisiones y los intereses que cobra el banco. */
const val CATEGORIA_DE_COMISIONES_DEL_BANCO = "Comisiones del banco"

/** La categoría del dueño para los intereses de ahorros y los rendimientos. */
const val CATEGORIA_DE_INGRESO = "Ingreso"

/**
 * Lo que [cargoDelBanco] reconoció: la clase, el tipo que esa clase implica (un impuesto y una
 * comisión salen, un rendimiento entra) y la categoría del dueño de la tabla de la auditoría.
 *
 * [categoria] es el **nombre de referencia**, no una promesa: si el dueño no tiene esa categoría,
 * [categoriaDelCargo] devuelve `null` y Movi no la crea.
 */
data class CargoDelBanco(
    val clase: ClaseDeCargo,
) {
    val tipo: TransactionType
        get() = if (clase == ClaseDeCargo.RENDIMIENTO) TransactionType.INCOME else TransactionType.EXPENSE

    val categoria: String
        get() = CATEGORIAS_DEL_CARGO.getValue(clase).first()
}

/**
 * Los nombres con que el dueño puede tener cada categoría, en orden de preferencia. El primero es el
 * suyo de hoy; los demás son la misma idea escrita de otra forma («Ingresos», «Gastos bancarios»).
 * Ninguno está en el catálogo de la app ([PREDEFINED_CATEGORIES]) a propósito: si el dueño no tiene
 * ninguno, no hay nada que proponer.
 */
private val CATEGORIAS_DEL_CARGO: Map<ClaseDeCargo, List<String>> = mapOf(
    ClaseDeCargo.IMPUESTO to listOf(CATEGORIA_DE_IMPUESTOS, "Impuesto"),
    ClaseDeCargo.COMISION to listOf(
        CATEGORIA_DE_COMISIONES_DEL_BANCO, "Comisiones bancarias", "Gastos bancarios", "Comisiones", "Comisión",
    ),
    ClaseDeCargo.RENDIMIENTO to listOf(CATEGORIA_DE_INGRESO, "Ingresos", "Rendimientos"),
)

/**
 * **La categoría del dueño para este cargo**, escrita como él la escribe, o `null` si no tiene
 * ninguna de las de [CATEGORIAS_DEL_CARGO]. Nunca crea una: proponer «Impuestos» a quien no la usa
 * abriría una categoría de un solo movimiento que él no pidió (el mismo criterio que #435 le puso a
 * las palabras clave).
 *
 * [categoriasDelDueno] vacío es «no se sabe» y también da `null`: a diferencia de las palabras clave,
 * acá no hay una categoría del catálogo de la app a la que caer.
 */
fun categoriaDelCargo(cargo: CargoDelBanco, categoriasDelDueno: Set<String>): String? {
    val suyas = categoriasDelDueno.associateBy { claveComparableDeNombre(it) }
    return CATEGORIAS_DEL_CARGO.getValue(cargo.clase).firstNotNullOfOrNull { suyas[claveComparableDeNombre(it)] }
}

/**
 * Un rótulo del banco es corto: «IMPTO GOBIERNO 4X1000», «ABONO INTERESES AHORROS». Un texto más
 * largo es una frase —un SMS de promoción («… 0 % de interés …»), el aviso de una ampliación de plazo
 * («los intereses y comisiones causados a la fecha se difieren…»)— y no un cargo.
 */
private const val PALABRAS_DE_UN_ROTULO = 12

/** Lo que deshace un cargo no es un cargo: «DEVOLUCION GMF», «REVERSION CUOTA MANEJO». */
private val LO_QUE_DESHACE = listOf("revers", "devol", "anula", "reintegr", "exonera", "exencion", "exento", "ajust", "reembols")

private val IMPUESTO_DEL_GOBIERNO = Regex("""\bimp(to|uesto)?\s+(del\s+)?gob""")
private val RETENCION_EN_LA_FUENTE = Regex("""\b(retencion|retenc|ret)\s+(en\s+la\s+|en\s+)?(fuente|fte)\b|\brete\s?(fuente|fte)\b""")
private val CUOTA_DE_MANEJO = Regex("""\bcuota\s+(de\s+)?man""")
private val INTERESES_QUE_COBRA = Regex(
    """\bint(ereses|eres)?\s+(corrientes?|ctes?|de\s+mora|mora|por\s+mora|y\s+mora|de\s+financiacion|financiacion|facturados?)\b""",
)
private val ABONO_DE_INTERESES = Regex("""\babono\s+(de\s+)?(int|interes|intereses|rendimientos?)\b""")
private val INTERESES_QUE_PAGA = Regex(
    """\binteres(es)?\s+(de\s+)?(la\s+|los\s+|las\s+|tu\s+|tus\s+)?(ahorros?|afc|cdt|cuenta|cajita|bolsillo|fondo|ganados?|a\s+favor)\b""",
)

/** Lo que acompaña a «comisión» o a «IVA» cuando lo cobra el banco. */
private val DE_UN_SERVICIO_DEL_BANCO: List<Regex> = listOf(
    "transf", "trasl", "envio", "giro", "ach", "otra ent", "otro banco", "interbanc", "retiro",
    "cajero", "consulta", "pse", "pagos? automatico", "sucursal", "chequera", "manejo", "avance",
).map { Regex("""\b$it""") }

/** Lo que dice que unos rendimientos son plata que la cuenta ganó, y no el nombre de un comercio. */
private val DE_UNA_INVERSION = setOf(
    "financieros", "financiero", "nu", "cajita", "fiducuenta", "fiduciaria", "fidu", "skandia", "ahorro", "ahorros",
    "afc", "cdt", "fondo", "fic", "inversion", "inversiones", "colectiva", "bolsillo", "pension", "voluntaria",
)

/**
 * **¿Este rótulo es un cargo o un abono que el banco hace solo?** Devuelve la clase, o `null`.
 *
 * Robusto a mayúsculas, tildes y a los recortes del banco: «IMPTO GOBIERNO 4X1000», «GMF», «CUOTA
 * MANEJO», «IVA CUOTA MANEJO», «COMISION TRANSF OTRA ENTIDAD», «INTERESES CORRIENTES», «ABONO
 * INTERESES AHORROS», «Rendimientos Nu».
 *
 * Es estricto a propósito —equivocarse hacia «sí» le cambiaría la categoría a una compra—, y por eso:
 * - una palabra suelta no alcanza: «Mora Soccer» no es mora, «Rendimientos» como nombre de un
 *   comercio no es un rendimiento, «Pago comisión arriendo» no es del banco;
 * - una frase larga (más de [PALABRAS_DE_UN_ROTULO] palabras) no es un rótulo;
 * - lo que deshace un cargo («DEVOLUCION GMF») no se reconoce.
 *
 * [tipo], si se sabe (la fila de un extracto lo trae), tiene que coincidir con la clase: un «4x1000»
 * que entra es una devolución, y unos «Rendimientos» que salen son una compra. Con el tipo, además,
 * el rótulo pelado se entiende: «INTERESES» que entran son de la cuenta, y que salen, de la tarjeta.
 */
fun cargoDelBanco(texto: String, tipo: TransactionType? = null): CargoDelBanco? {
    val limpio = normalizarParaBuscar(texto).replace(Regex("[^a-z0-9]+"), " ").trim()
    val palabras = limpio.split(' ').filter { it.isNotEmpty() }
    if (palabras.isEmpty() || palabras.size > PALABRAS_DE_UN_ROTULO) return null
    if (palabras.any { palabra -> LO_QUE_DESHACE.any { palabra.startsWith(it) } }) return null
    val clase = claseDelRotulo(limpio, palabras, tipo) ?: return null
    val cargo = CargoDelBanco(clase)
    if (tipo != null && tipo != cargo.tipo) return null
    return cargo
}

private fun claseDelRotulo(limpio: String, palabras: List<String>, tipo: TransactionType?): ClaseDeCargo? {
    val pegado = palabras.joinToString("")
    // Los impuestos primero: «RETENCION FTE RENDIMIENTOS» es la retención, no el rendimiento.
    val cuatroPorMil = listOf("4x1000", "4xmil", "4por1000", "4pormil", "cuatropormil").any { it in pegado }
    if (cuatroPorMil || "gmf" in palabras || palabras.any { it.startsWith("gravamen") } ||
        IMPUESTO_DEL_GOBIERNO.containsMatchIn(limpio) || RETENCION_EN_LA_FUENTE.containsMatchIn(limpio) ||
        "retefuente" in palabras || "retefte" in palabras
    ) {
        return ClaseDeCargo.IMPUESTO
    }

    val deUnServicio = DE_UN_SERVICIO_DEL_BANCO.any { it.containsMatchIn(limpio) }
    val esComision = palabras.any { it.startsWith("comis") } &&
        (limpio.startsWith("comis") || limpio.startsWith("iva comis") || deUnServicio)
    if (CUOTA_DE_MANEJO.containsMatchIn(limpio) || esComision || INTERESES_QUE_COBRA.containsMatchIn(limpio) ||
        ("iva" in palabras && (deUnServicio || "cuota" in palabras))
    ) {
        return ClaseDeCargo.COMISION
    }

    val rendimientos = palabras.any { it.startsWith("rendimiento") }
    if (ABONO_DE_INTERESES.containsMatchIn(limpio) || INTERESES_QUE_PAGA.containsMatchIn(limpio) ||
        (rendimientos && palabras.any { it in DE_UNA_INVERSION })
    ) {
        return ClaseDeCargo.RENDIMIENTO
    }

    // El rótulo pelado solo con el tipo que dice el banco: sin él, «Intereses» puede ser de los dos lados.
    if (limpio.startsWith("rendimiento") && tipo == TransactionType.INCOME) return ClaseDeCargo.RENDIMIENTO
    if (palabras.first() == "interes" || palabras.first() == "intereses") {
        return when (tipo) {
            TransactionType.INCOME -> ClaseDeCargo.RENDIMIENTO
            TransactionType.EXPENSE -> ClaseDeCargo.COMISION
            null -> null
        }
    }
    return null
}
