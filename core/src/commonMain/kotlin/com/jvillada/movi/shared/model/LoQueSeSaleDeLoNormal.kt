package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * # Lo que se sale de lo normal (Ola 4 · «Movi mira adelante»)
 *
 * Cuatro avisos, **cada uno con la evidencia a la vista** —los movimientos que lo disparan—, para
 * que el dueño los juzgue en un vistazo y descarte con «Está bien» lo que sí era normal:
 *
 * 1. **Cobro duplicado** ([cobrosDuplicados]): mismo monto, mismo comercio, misma cuenta, a dos
 *    días o menos.
 * 2. **Suscripción que subió de precio** ([suscripcionesQueSubieron]): el cobro de este período
 *    contra el del anterior.
 * 3. **Una categoría muy por encima de lo normal** ([categoriasPorEncima]): lo gastado en el
 *    período contra el promedio de los anteriores.
 * 4. **Comercio nuevo con monto alto** ([comerciosNuevosConMontoAlto]).
 * 5. **Una deuda que crece** ([deudasQueCrecen]): el saldo de un crédito o una tarjeta subió entre
 *    dos cierres sin compras que lo expliquen.
 * 6. **Un interés real lejos del estimado** ([interesesLejosDelEstimado]): el extracto cobra mucho
 *    más (o menos) de lo que Movi calcula con la tasa.
 *
 * Cada detector es una función pura; los umbrales son constantes con nombre y **son elegidos, no
 * medidos** (igual que el 20 % del semáforo de presupuestos): están para no avisar por $5.000.
 *
 * Cada aviso trae una [Anomalia.huella] estable: la misma situación da la misma huella en cada
 * lectura, y por eso descartarla una vez la saca para siempre (el server guarda las huellas
 * descartadas, `anomalias_descartadas`).
 */

@Serializable
enum class TipoDeAnomalia {
    COBRO_DUPLICADO, SUSCRIPCION_SUBIO, CATEGORIA_POR_ENCIMA, COMERCIO_NUEVO,
    /**
     * Las dos de las deudas. **Un APK instalado no las conoce** y no podría leer la lista entera si
     * llegaran, así que el server solo las manda a quien las pide (`GET /api/anomalias?conDeudas=1`).
     */
    DEUDA_QUE_CRECE, INTERES_LEJOS_DEL_ESTIMADO,
}

/** Un movimiento que respalda un aviso, como se lo muestra al dueño. */
@Serializable
data class EvidenciaDeAnomalia(
    val id: String,
    /** ISO, `"2026-09-28"`. */
    val fecha: String,
    val nombre: String,
    val monto: Long,
    val moneda: String = "COP",
    val cuenta: String? = null,
)

/**
 * Un aviso de algo que se sale de lo normal.
 *
 * @property huella lo identifica entre lecturas; ver el KDoc del archivo.
 * @property titulo la noticia, en una frase («¿Cobro duplicado en Rappi?»).
 * @property detalle de dónde sale, con las cifras («2 cobros de $45.900 en Bancolombia…»).
 */
@Serializable
data class Anomalia(
    val huella: String,
    val tipo: TipoDeAnomalia,
    val titulo: String,
    val detalle: String,
    val evidencia: List<EvidenciaDeAnomalia>,
)

/** `POST /api/anomalias/descartar`: «Está bien», no volver a avisar esta [huella]. */
@Serializable
data class DescartarAnomaliaRequest(val huella: String)

// ── Los umbrales (elegidos, no medidos) ─────────────────────────────────────

/** Dos cobros iguales más separados que esto no son un duplicado: son dos compras. */
const val VENTANA_DEL_DUPLICADO_MS: Long = 2L * 24 * 60 * 60 * 1000

/** Por debajo de esto, dos cobros iguales son dos tintos, no un error del banco. */
const val MINIMO_DEL_DUPLICADO: Long = 20_000L

/** Una suscripción «subió» si el cobro creció más del 2 %… */
const val SUBIDA_MINIMA_DE_SUSCRIPCION_PORCIENTO: Long = 2L

/** …y, en pesos, al menos esto (en otra moneda basta el porcentaje). */
const val SUBIDA_MINIMA_DE_SUSCRIPCION: Long = 1_000L

/** Una categoría está «muy por encima» desde 1,5 veces su promedio… */
const val VECES_SOBRE_EL_PROMEDIO_X10: Long = 15L

/** …y con al menos esto de diferencia: para no avisar por $5.000. */
const val DIFERENCIA_MINIMA_DE_CATEGORIA: Long = 300_000L

/** Un cobro en un comercio nunca visto avisa desde este monto. */
const val MONTO_ALTO_DE_COMERCIO_NUEVO: Long = 500_000L

/** «Nunca visto» solo significa algo con este tanto de historia antes del cobro. */
const val HISTORIA_MINIMA_PARA_COMERCIO_NUEVO_MS: Long = 60L * 24 * 60 * 60 * 1000

/** Categorías que no son consumo: su salto es una decisión (una cuota, un desembolso), no un aviso. */
private val CATEGORIAS_QUE_NO_SON_CONSUMO = setOf(CUOTA_CATEGORY, DESEMBOLSO_CATEGORY)

// ── Lo común ────────────────────────────────────────────────────────────────

/**
 * **El comercio de un movimiento**, como clave: la de [normalizeMerchant] (la misma de las
 * suscripciones) y, si no identifica uno, el nombre comparable. `null` si no hay con qué.
 */
fun claveDeComercio(evento: FinancialEvent): String? {
    val texto = evento.merchant?.takeIf { it.isNotBlank() } ?: evento.description
    normalizeMerchant(texto)?.let { return it.key }
    return claveComparableDeNombre(texto).takeIf { it.length >= 3 }
}

/** ¿Este movimiento es un gasto de consumo que un aviso puede mirar? */
private fun esGastoDeConsumo(e: FinancialEvent): Boolean =
    e.type == TransactionType.EXPENSE &&
        cuentaEnGastosEIngresos(e) &&
        e.transferId == null &&
        e.category !in CATEGORIAS_QUE_NO_SON_CONSUMO

private fun evidenciaDe(e: FinancialEvent, diaDe: (Long) -> String, nombreDeCuenta: Map<String, String>) =
    EvidenciaDeAnomalia(
        id = e.id,
        fecha = diaDe(e.timestamp),
        nombre = e.description,
        monto = e.amount,
        moneda = e.currency,
        cuenta = nombreDeCuenta[e.accountId],
    )

private fun plata(monto: Long, moneda: String): String =
    if (moneda == "COP") "$" + miles(monto) else "$moneda ${miles(monto)}"

private fun miles(monto: Long): String {
    val digitos = abs(monto).toString()
    val conPuntos = digitos.reversed().chunked(3).joinToString(".").reversed()
    return if (monto < 0) "−$conPuntos" else conPuntos
}

// ── 1. Cobro duplicado ──────────────────────────────────────────────────────

/**
 * Los cobros que se repiten: mismo monto, mismo comercio ([claveDeComercio]), misma cuenta y a
 * [VENTANA_DEL_DUPLICADO_MS] o menos uno del siguiente. Tres iguales seguidos son UN aviso con los
 * tres. Desde [MINIMO_DEL_DUPLICADO].
 */
fun cobrosDuplicados(
    eventos: List<FinancialEvent>,
    nombreDeCuenta: Map<String, String>,
    diaDe: (Long) -> String,
): List<Anomalia> =
    eventos
        .filter { esGastoDeConsumo(it) && it.amount >= MINIMO_DEL_DUPLICADO }
        .mapNotNull { e -> claveDeComercio(e)?.let { Triple(e.accountId, it, e.amount to e.currency) to e } }
        .groupBy({ it.first }, { it.second })
        .flatMap { (_, iguales) ->
            val ordenados = iguales.sortedBy { it.timestamp }
            val grupos = mutableListOf<MutableList<FinancialEvent>>()
            ordenados.forEach { e ->
                val ultimo = grupos.lastOrNull()?.last()
                if (ultimo != null && e.timestamp - ultimo.timestamp <= VENTANA_DEL_DUPLICADO_MS) grupos.last() += e
                else grupos += mutableListOf(e)
            }
            grupos.filter { it.size >= 2 }
        }
        .map { grupo ->
            val primero = grupo.first()
            val cuenta = nombreDeCuenta[primero.accountId]
            Anomalia(
                huella = "duplicado:" + grupo.map { it.id }.sorted().joinToString(","),
                tipo = TipoDeAnomalia.COBRO_DUPLICADO,
                titulo = "¿Cobro duplicado en ${primero.description}?",
                detalle = "${grupo.size} cobros de ${plata(primero.amount, primero.currency)}" +
                    (cuenta?.let { " en $it" } ?: "") + " con menos de dos días entre uno y otro.",
                evidencia = grupo.map { evidenciaDe(it, diaDe, nombreDeCuenta) },
            )
        }

// ── 2. Suscripción que subió ────────────────────────────────────────────────

/**
 * Las suscripciones activas cuyo cobro de este período es más caro que el del anterior.
 *
 * Los cobros de una suscripción son los gastos cuyo [claveDeComercio] es su `merchantKey`, en su
 * moneda. Se compara **el último cobro del período actual** contra **el último del período
 * anterior**; sin cobro en alguno de los dos no hay nada que comparar.
 *
 * @param periodoDe el prefijo del período del dueño de un instante (`"2026-10"`).
 * @param periodoActual el prefijo del período en curso.
 * @param periodoAnterior el prefijo del período anterior.
 */
fun suscripcionesQueSubieron(
    suscripciones: List<Subscription>,
    eventos: List<FinancialEvent>,
    periodoDe: (Long) -> String,
    periodoActual: String,
    periodoAnterior: String,
    nombreDeCuenta: Map<String, String>,
    diaDe: (Long) -> String,
): List<Anomalia> =
    suscripciones
        .filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
        .mapNotNull { s ->
            val cobros = eventos.filter {
                it.type == TransactionType.EXPENSE && it.currency == s.currency && claveDeComercio(it) == s.merchantKey
            }
            val ahora = cobros.filter { periodoDe(it.timestamp) == periodoActual }.maxByOrNull { it.timestamp } ?: return@mapNotNull null
            val antes = cobros.filter { periodoDe(it.timestamp) == periodoAnterior }.maxByOrNull { it.timestamp } ?: return@mapNotNull null
            val subida = ahora.amount - antes.amount
            if (subida <= 0L) return@mapNotNull null
            if (subida * 100 <= antes.amount * SUBIDA_MINIMA_DE_SUSCRIPCION_PORCIENTO) return@mapNotNull null
            if (s.currency == "COP" && subida < SUBIDA_MINIMA_DE_SUSCRIPCION) return@mapNotNull null
            Anomalia(
                huella = "suscripcion:${s.id}:${ahora.id}",
                tipo = TipoDeAnomalia.SUSCRIPCION_SUBIO,
                titulo = "${s.displayName} subió de precio",
                detalle = "Te cobró ${plata(ahora.amount, s.currency)} este período; el anterior fueron " +
                    "${plata(antes.amount, s.currency)} (${plata(subida, s.currency)} más).",
                evidencia = listOf(evidenciaDe(antes, diaDe, nombreDeCuenta), evidenciaDe(ahora, diaDe, nombreDeCuenta)),
            )
        }

// ── 3. Categoría muy por encima ─────────────────────────────────────────────

/**
 * Las categorías en las que el período actual va **muy por encima** del promedio de los anteriores:
 * desde [VECES_SOBRE_EL_PROMEDIO_X10] / 10 veces el promedio y con al menos
 * [DIFERENCIA_MINIMA_DE_CATEGORIA] de diferencia. Una categoría sin historia (promedio cero) avisa
 * con la diferencia sola.
 *
 * El período actual va a medias, así que se compara lo que ya lleva contra períodos enteros: es el
 * lado prudente — si a mitad de mes ya pasó el umbral, de verdad va por encima.
 *
 * @param actual los gastos de consumo del período en curso, en pesos.
 * @param anteriores los de los períodos anteriores que Movi cubrió enteros (el server decide
 *   cuáles); sin ninguno no hay «normal» contra qué medir y no se avisa nada.
 * @param periodoActual el prefijo del período en curso: va en la huella, para que el descarte valga
 *   para este período y no apague la categoría para siempre.
 */
fun categoriasPorEncima(
    actual: List<FinancialEvent>,
    anteriores: List<List<FinancialEvent>>,
    periodoActual: String,
    nombreDeCuenta: Map<String, String>,
    diaDe: (Long) -> String,
): List<Anomalia> {
    if (anteriores.isEmpty()) return emptyList()
    fun porCategoria(eventos: List<FinancialEvent>) = eventos
        .filter { esGastoDeConsumo(it) && it.currency == "COP" }
        .groupBy { it.category }
    val ahora = porCategoria(actual)
    val antes = anteriores.map { periodo -> porCategoria(periodo).mapValues { (_, es) -> es.sumOf { montoContado(it) } } }
    return ahora.mapNotNull { (categoria, eventos) ->
        val gastado = eventos.sumOf { montoContado(it) }
        val promedio = antes.sumOf { it[categoria] ?: 0L } / antes.size
        val diferencia = gastado - promedio
        if (diferencia < DIFERENCIA_MINIMA_DE_CATEGORIA) return@mapNotNull null
        if (promedio > 0L && gastado * 10 < promedio * VECES_SOBRE_EL_PROMEDIO_X10) return@mapNotNull null
        val deCuantos = if (antes.size == 1) "el período anterior" else "el promedio de los ${antes.size} anteriores"
        Anomalia(
            huella = "categoria:$categoria:$periodoActual",
            tipo = TipoDeAnomalia.CATEGORIA_POR_ENCIMA,
            titulo = "$categoria va muy por encima de lo normal",
            detalle = "Llevas ${plata(gastado, "COP")} este período; lo normal es ${plata(promedio, "COP")} ($deCuantos).",
            evidencia = eventos.sortedByDescending { it.amount }.take(3).map { evidenciaDe(it, diaDe, nombreDeCuenta) },
        )
    }.sortedByDescending { a -> a.evidencia.sumOf { it.monto } }
}

// ── 4. Comercio nuevo con monto alto ────────────────────────────────────────

/**
 * Los cobros de [MONTO_ALTO_DE_COMERCIO_NUEVO] o más en un comercio que no aparece en [historia]
 * (ni antes en el mismo período). «Nunca visto» solo cuenta si Movi ya llevaba
 * [HISTORIA_MINIMA_PARA_COMERCIO_NUEVO_MS] de historia antes del cobro ([desde] = el primer
 * movimiento del dueño): sin eso, todo comercio es nuevo.
 */
fun comerciosNuevosConMontoAlto(
    delPeriodo: List<FinancialEvent>,
    historia: List<FinancialEvent>,
    desde: Long,
    nombreDeCuenta: Map<String, String>,
    diaDe: (Long) -> String,
): List<Anomalia> {
    val conocidos = historia.mapNotNull { claveDeComercio(it) }.toMutableSet()
    return delPeriodo.sortedBy { it.timestamp }.mapNotNull { e ->
        val clave = claveDeComercio(e) ?: return@mapNotNull null
        val esNuevo = clave !in conocidos
        conocidos += clave
        if (!esNuevo || !esGastoDeConsumo(e) || e.amount < MONTO_ALTO_DE_COMERCIO_NUEVO || e.currency != "COP") return@mapNotNull null
        if (e.timestamp - desde < HISTORIA_MINIMA_PARA_COMERCIO_NUEVO_MS) return@mapNotNull null
        Anomalia(
            huella = "nuevo:${e.id}",
            tipo = TipoDeAnomalia.COMERCIO_NUEVO,
            titulo = "Primera vez en ${e.description}, y por ${plata(e.amount, e.currency)}",
            detalle = "No hay ningún movimiento anterior en ese comercio" +
                (nombreDeCuenta[e.accountId]?.let { "; salió de $it." } ?: "."),
            evidencia = listOf(evidenciaDe(e, diaDe, nombreDeCuenta)),
        )
    }
}

// ── 5. Una deuda que crece ──────────────────────────────────────────────────

/**
 * Una deuda «crece» si al cierre del período subió al menos esto **más de lo que explican sus
 * compras**. Elegido, no medido: en pesos, menos que esto son los intereses de un mes de una tarjeta
 * chica, y avisar por eso enseña a ignorar el aviso.
 */
const val MINIMO_DE_DEUDA_QUE_CRECE: Long = 100_000L

/**
 * ¿Este movimiento de una deuda **explica** que suba? Una compra, un avance, un desembolso, la deuda
 * inicial: sí, son plata que el dueño pidió. Un ajuste del saldo, los intereses, la cuota de manejo,
 * el 4x1000: no, la deuda subió sola — que es justamente lo que el aviso viene a decir.
 */
private fun explicaQueLaDeudaSuba(e: FinancialEvent): Boolean {
    if (e.type != TransactionType.EXPENSE) return false
    if (e.category == ADJUSTMENT_CATEGORY) return false
    if (e.category == CATEGORIA_DE_COMISIONES_DEL_BANCO || e.category == CATEGORIA_DE_IMPUESTOS) return false
    val cargo = cargoDelBanco(e.description, TransactionType.EXPENSE)
    return cargo == null
}

/**
 * **Las deudas que crecieron entre dos cierres sin compras que lo expliquen.**
 *
 * Para cada cuenta de [deudas] (préstamos y tarjetas): el saldo al cierre del período anterior al
 * de [cierre] (todo lo anterior a `cierre.first`) contra el saldo a su cierre (hasta `cierre.last`),
 * en la moneda de la cuenta y sin anulados (los [eventos] ya llegan sin ellos). Lo que subió menos
 * lo que explican sus compras ([explicaQueLaDeudaSuba]) es lo que creció solo; desde
 * [MINIMO_DE_DEUDA_QUE_CRECE], es un aviso. La evidencia son los movimientos que la subieron sin
 * ser compras: un ajuste contra el extracto, los intereses.
 *
 * Una deuda que no existía antes del cierre anterior no se mira: no hay contra qué comparar (y que
 * aparezca no es crecer, es que Movi se enteró).
 *
 * El caso que lo pidió: la libranza ·4818 se desembolsó por $257.000.000 y dos meses después debía
 * $262.386.162 — $5,4 M más, con cuotas que eran todo interés y seguro.
 *
 * @param cierre la ventana del último período cerrado, en epoch-ms.
 * @param periodo su prefijo (`"2026-09"`): va en la huella, así un «Está bien» vale para ese cierre.
 */
fun deudasQueCrecen(
    deudas: List<Account>,
    eventos: List<FinancialEvent>,
    cierre: LongRange,
    periodo: String,
    diaDe: (Long) -> String,
): List<Anomalia> {
    val porCuenta = eventos.groupBy { it.accountId }
    return deudas
        .filter { it.type == AccountType.LOAN || it.type == AccountType.CREDIT_CARD }
        .mapNotNull { cuenta ->
            val suyos = porCuenta[cuenta.id].orEmpty().filter { it.currency == cuenta.currency }
            val antes = suyos.filter { it.timestamp < cierre.first }
            if (antes.isEmpty()) return@mapNotNull null
            val delPeriodo = suyos.filter { it.timestamp in cierre }
            fun saldo(es: List<FinancialEvent>) = es.sumOf { signedDelta(cuenta.type, it.type, it.amount) }
            val alAbrir = saldo(antes)
            val alCerrar = alAbrir + saldo(delPeriodo)
            val compras = delPeriodo.filter { explicaQueLaDeudaSuba(it) }.sumOf { it.amount }
            val solo = alCerrar - alAbrir - compras
            if (solo < MINIMO_DE_DEUDA_QUE_CRECE) return@mapNotNull null
            val nombre = mapOf(cuenta.id to cuenta.name)
            Anomalia(
                huella = "deuda:${cuenta.id}:$periodo",
                tipo = TipoDeAnomalia.DEUDA_QUE_CRECE,
                titulo = "La deuda de ${cuenta.name} creció ${plata(solo, cuenta.currency)} sin compras",
                detalle = "Al cierre del período pasó de ${plata(alAbrir, cuenta.currency)} a " +
                    "${plata(alCerrar, cuenta.currency)}" +
                    (if (compras > 0L) "; las compras solo explican ${plata(compras, cuenta.currency)}." else ", sin compras en el período."),
                evidencia = delPeriodo
                    .filter { it.type == TransactionType.EXPENSE && !explicaQueLaDeudaSuba(it) }
                    .sortedByDescending { it.amount }
                    .take(3)
                    .map { evidenciaDe(it, diaDe, nombre) },
            )
        }
}

// ── 6. Un interés real lejos del estimado ───────────────────────────────────

/** El interés del extracto está «lejos» si se aparta de la estimación más de este porcentaje… */
const val INTERES_LEJOS_PORCIENTO: Long = 15L

/** …y al menos esto, en pesos. Elegidos, no medidos: el ·9695 pasó de $363.905 a $594.567. */
const val INTERES_LEJOS_MINIMO: Long = 50_000L

/**
 * **Los créditos cuyo interés del extracto se aparta mucho de lo que estima Movi** — más de
 * [INTERES_LEJOS_PORCIENTO] % y al menos [INTERES_LEJOS_MINIMO]. Con la tasa bien cargada no
 * debería pasar; cuando pasa, o la tasa de Movi no es la que cobra el banco (el hipotecario ·1254:
 * 13,48 % pactada, 10,98 % cobrada), o el banco cobró algo que conviene preguntar (el ·9695: cuatro
 * veces más interés en agosto que en julio sobre el mismo saldo).
 *
 * El interés del extracto es el de [CreditTerms.interesRealVigente] —el de la cuota que viene—; el
 * estimado, `saldo × tasa mensual` con el saldo de hoy (el mismo cálculo del plan). Un crédito sin
 * tasa no se mira; uno marcado «no cobra intereses» se estima en cero.
 *
 * La huella lleva la cuota: «Está bien» vale para esa cuota, y el extracto del mes siguiente vuelve
 * a mirarse.
 */
fun interesesLejosDelEstimado(creditos: List<CreditSummary>, hoy: kotlinx.datetime.LocalDate): List<Anomalia> =
    interesesLejosDelEstimado(creditos, hoy.year, hoy.monthNumber, hoy.dayOfMonth)

/** Lo mismo con la fecha suelta, para quien no tiene kotlinx-datetime (el server usa java.time). */
fun interesesLejosDelEstimado(creditos: List<CreditSummary>, anio: Int, mes: Int, dia: Int): List<Anomalia> {
    val hoy = kotlinx.datetime.LocalDate(anio, mes, dia)
    return creditos.mapNotNull { c ->
        val terms = c.terms ?: return@mapNotNull null
        val real = terms.interesRealVigente(hoy) ?: return@mapNotNull null
        val cuota = terms.interesRealCuota ?: return@mapNotNull null
        val estimado = when {
            terms.sinIntereses -> 0L
            terms.rateEa > 0.0 -> interesDelPeriodo(c.account.balance.coerceAtLeast(0L), tasaMensualDeUnaEA(terms.rateEa))
            else -> return@mapNotNull null
        }
        val diferencia = real - estimado
        if (abs(diferencia) < INTERES_LEJOS_MINIMO) return@mapNotNull null
        if (abs(diferencia) * 100 <= estimado * INTERES_LEJOS_PORCIENTO) return@mapNotNull null
        val vence = cuota + "-" + terms.dayOfMonth.coerceIn(1, 28).toString().padStart(2, '0')
        Anomalia(
            huella = "interes:${c.account.id}:$cuota",
            tipo = TipoDeAnomalia.INTERES_LEJOS_DEL_ESTIMADO,
            titulo = "El interés de ${c.account.name} no es el que Movi calcula",
            detalle = "El extracto cobra ${plata(real, "COP")} en la cuota; con la tasa de " +
                "${terms.rateEa.toString().replace('.', ',')} % Movi estimaba ${plata(estimado, "COP")} " +
                "(${if (diferencia > 0) "${plata(diferencia, "COP")} más" else "${plata(-diferencia, "COP")} menos"}). " +
                "Revisa la tasa del crédito o pregúntale al banco.",
            evidencia = listOf(
                EvidenciaDeAnomalia("interes-real:${c.account.id}", vence, "Interés según el extracto", real, cuenta = c.account.name),
                EvidenciaDeAnomalia("interes-estimado:${c.account.id}", vence, "Lo que Movi estimaba", estimado, cuenta = c.account.name),
            ),
        )
    }
}
