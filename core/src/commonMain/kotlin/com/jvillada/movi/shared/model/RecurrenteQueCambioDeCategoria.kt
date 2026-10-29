package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * # El recurrente que cambió de categoría (vuelta 60)
 *
 * La revisión del 29-oct: la regla «Arriendo» tenía la categoría Otros y en septiembre el arriendo
 * había sido Hogar. Un solo dato mal puesto ensuciaba cuatro pantallas: Presupuestos decía «Hogar $0
 * de $1.850.000», Movi AI abría «En qué se me va» con «Otros +$1,8M más que el período pasado», el
 * widget del Inicio decía «Otros $1.800.000 nuevo» y salía la anomalía «Gasto nuevo en Otros».
 *
 * Movi **propone, nunca cambia solo**: «¿Arriendo va en Hogar, como en septiembre?» con «Sí, cambiar»
 * (cambia la categoría del movimiento y, si la regla tiene la otra, la de la regla) y «No». Mientras
 * no se resuelva, la anomalía de categoría no cuenta ese monto como gasto nuevo y Movi AI lo compara
 * donde estaba el período pasado (ver [comoEnElPeriodoAnterior]).
 *
 * **Cuándo es «el mismo»**: un gasto de este período y uno del anterior son el mismo recurrente si
 *
 * 1. los dos son ocurrencias de la misma regla (un sello del dueño o un emparejamiento de Movi), o
 * 2. tienen el mismo nombre ([claveDeComercio]) y el monto a ±[TOLERANCIA_DEL_MISMO_MONTO_PORCIENTO] %.
 *
 * Si están en categorías distintas, se propone volver a la del período anterior. Puro: los
 * movimientos, las reglas y qué movimiento es de qué regla entran por parámetro.
 */

/** «El mismo monto» de un recurrente sin regla que lo ate: ±5 % (elegido, no medido). */
const val TOLERANCIA_DEL_MISMO_MONTO_PORCIENTO: Long = 5L

/**
 * Un gasto de este período que el período anterior estaba en otra categoría.
 *
 * @property eventoId el movimiento de este período (el que «Sí, cambiar» recategoriza).
 * @property nombre como lo nombra el movimiento («Arriendo»).
 * @property categoria donde quedó este período («Otros»).
 * @property categoriaAnterior donde estuvo el período anterior («Hogar»).
 * @property periodoAnterior el prefijo del período anterior («2026-09»): «como en septiembre».
 * @property eventoAnteriorId el movimiento del período anterior, la evidencia.
 * @property reglaId la regla recurrente que «Sí, cambiar» también corrige: solo si su categoría no es
 *   ya [categoriaAnterior].
 */
@Serializable
data class CambioDeCategoriaDeUnRecurrente(
    val eventoId: String,
    val nombre: String,
    val monto: Long,
    val categoria: String,
    val categoriaAnterior: String,
    val periodoAnterior: String,
    val eventoAnteriorId: String,
    val reglaId: String? = null,
    val nombreDeLaRegla: String? = null,
) {
    /** La huella del aviso: por movimiento, así «No» vale para este gasto y no apaga los que vengan. */
    val huella: String get() = "recurrente-categoria:$eventoId"
}

/** Las categorías que no se proponen ni se corrigen: las reservadas y las de dos patas. */
private val CATEGORIAS_QUE_NO_SE_PROPONEN: Set<String> =
    RESERVED_CATEGORIES + setOf(CUOTA_CATEGORY, DESEMBOLSO_CATEGORY)

/** ¿[e] es un gasto que puede ser la ocurrencia de un recurrente? */
private fun esGastoDeUnRecurrente(e: FinancialEvent): Boolean =
    e.type == TransactionType.EXPENSE &&
        cuentaEnGastosEIngresos(e) &&
        e.transferId == null &&
        !isReservedCategory(e.category) &&
        e.category !in CATEGORIAS_QUE_NO_SE_PROPONEN

/** ¿[a] y [b] son el mismo monto, a ±[TOLERANCIA_DEL_MISMO_MONTO_PORCIENTO] % de [b]? */
private fun mismoMonto(a: Long, b: Long): Boolean =
    b > 0L && abs(a - b) * 100 <= b * TOLERANCIA_DEL_MISMO_MONTO_PORCIENTO

/** El nombre comparable de un movimiento o una regla. */
private fun claveDeNombre(texto: String): String? =
    normalizeMerchant(texto)?.key ?: claveComparableDeNombre(texto).takeIf { it.length >= 3 }

/**
 * **Los gastos de este período que el anterior estaban en otra categoría.** Ver el KDoc del archivo.
 *
 * @param actual los movimientos del período en curso (vivos).
 * @param anterior los del período anterior (vivos).
 * @param reglas las reglas recurrentes del dueño.
 * @param reglaDelMovimiento id de movimiento → id de la regla de la que es ocurrencia (los sellos del
 *   dueño y lo que Movi emparejó solo), de los dos períodos.
 * @param periodoAnterior el prefijo del período anterior.
 */
fun recurrentesQueCambiaronDeCategoria(
    actual: List<FinancialEvent>,
    anterior: List<FinancialEvent>,
    reglas: List<RecurringRule>,
    reglaDelMovimiento: Map<String, String>,
    periodoAnterior: String,
): List<CambioDeCategoriaDeUnRecurrente> {
    val delAnterior = anterior.filter(::esGastoDeUnRecurrente)
    if (delAnterior.isEmpty()) return emptyList()
    val reglasPorId = reglas.associateBy { it.id }
    val usados = mutableSetOf<String>()
    return actual.filter(::esGastoDeUnRecurrente).sortedByDescending { it.amount }.mapNotNull { e ->
        val regla = reglaDelMovimiento[e.id]?.let { reglasPorId[it] }
        val clave = claveDeNombre(e.merchant?.takeIf { it.isNotBlank() } ?: e.description)
        val candidatos = delAnterior.filter { a -> a.id !in usados && a.currency == e.currency }
        // 1. La misma regla: su ocurrencia del período anterior, sin pedir el monto (un recibo varía).
        val porLaRegla = regla?.let { r -> candidatos.filter { reglaDelMovimiento[it.id] == r.id } }.orEmpty()
        // 2. El mismo nombre y el mismo monto.
        val porElNombre = if (clave == null) {
            emptyList()
        } else {
            candidatos.filter { a ->
                claveDeNombre(a.merchant?.takeIf { it.isNotBlank() } ?: a.description) == clave && mismoMonto(e.amount, a.amount)
            }
        }
        val equivalente = (porLaRegla.ifEmpty { porElNombre }).minByOrNull { abs(it.amount - e.amount) }
            ?: return@mapNotNull null
        if (equivalente.category == e.category) return@mapNotNull null
        usados += equivalente.id
        // La regla que «Sí, cambiar» también corrige: la de la ocurrencia, o la que se llama igual y
        // cobra lo mismo. Solo si tiene otra categoría que la del período anterior.
        val laRegla = regla ?: reglas.firstOrNull { r ->
            r.type == TransactionType.EXPENSE && clave != null && claveDeNombre(r.name) == clave && mismoMonto(e.amount, r.amount)
        }
        val reglaQueCorregir = laRegla?.takeIf { it.category != equivalente.category }
        CambioDeCategoriaDeUnRecurrente(
            eventoId = e.id,
            nombre = (regla?.name ?: e.description).ifBlank { e.description },
            monto = montoContado(e),
            categoria = e.category,
            categoriaAnterior = equivalente.category,
            periodoAnterior = periodoAnterior,
            eventoAnteriorId = equivalente.id,
            reglaId = reglaQueCorregir?.id,
            nombreDeLaRegla = reglaQueCorregir?.name,
        )
    }
}

/**
 * **El aviso de un cambio de categoría**, como una [Anomalia] más: «¿Arriendo va en Hogar, como en
 * septiembre?». Viaja con la anomalía el [CambioDeCategoriaDeUnRecurrente] entero, para que «Sí,
 * cambiar» sepa qué cambiar sin otra lectura.
 */
fun anomaliaDelCambioDeCategoria(
    cambio: CambioDeCategoriaDeUnRecurrente,
    evidencia: List<EvidenciaDeAnomalia>,
): Anomalia {
    val mes = periodoDelPrefijo(cambio.periodoAnterior)?.let { nombreDelMesDe(it) } ?: "el período pasado"
    val regla = cambio.nombreDeLaRegla?.let { " «Sí, cambiar» también corrige la regla «$it»." }.orEmpty()
    return Anomalia(
        huella = cambio.huella,
        tipo = TipoDeAnomalia.RECURRENTE_CAMBIO_DE_CATEGORIA,
        titulo = "¿${cambio.nombre} va en ${cambio.categoriaAnterior}, como en $mes?",
        detalle = "Este período quedó en ${cambio.categoria}; en $mes fue ${cambio.categoriaAnterior}.$regla",
        evidencia = evidencia,
        cambioDeCategoria = cambio,
    )
}

/**
 * **El gasto por categoría con los recurrentes que cambiaron donde estaban el período anterior**, solo
 * para comparar (vuelta 60). Mientras el dueño no decide, «Otros +$1,8M más que el período pasado» no
 * es una noticia: es el arriendo, que el período pasado era Hogar. Las cifras que se muestran siguen
 * siendo las de siempre; esto es la vara de la comparación.
 */
fun comoEnElPeriodoAnterior(
    gastoPorCategoria: Map<String, Long>,
    cambios: List<CambioDeCategoriaDeUnRecurrente>,
): Map<String, Long> {
    val resultado = gastoPorCategoria.toMutableMap()
    cambios.forEach { c ->
        resultado[c.categoria] = (resultado[c.categoria] ?: 0L) - c.monto
        resultado[c.categoriaAnterior] = (resultado[c.categoriaAnterior] ?: 0L) + c.monto
    }
    return resultado.filterValues { it != 0L }
}
