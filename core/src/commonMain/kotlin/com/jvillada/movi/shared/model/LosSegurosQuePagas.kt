package com.jvillada.movi.shared.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

/*
 * # Vuelta 132 · «Tus seguros»: lo que ya pagas en seguros, sin vender nada
 *
 * La fase 0 del negocio de seguros (`docs/investigacion/reports/Movi como intermediario de seguros.md`):
 * validar sin vender. Movi le muestra a cada persona los seguros que **ya** paga, con lo que ya lee, y
 * mide si le interesa que la ayude. Nada acá vende, cotiza ni recomienda comprar.
 *
 * Las fuentes, verificadas en el código:
 * - **El seguro dentro de la cuota** de un crédito: [CreditTerms.insuranceMonthly] («Seguro de vida
 *   deudor (u otro cargo mensual fijo) incluido en la cuota»). Es UN campo sin tipo, así que se dice
 *   «Seguro de vida» —lo que el campo documenta— y nunca «incendio» o «terremoto», que el modelo no
 *   guarda. Un crédito que paga otro ([CreditTerms.paidBy]) o ya pagado no entra: no es algo que pagues.
 * - **Los cargos de seguros** en tarjetas y cuentas, por su texto ([lecturaDelTexto]: una sola lista de
 *   rótulos y de aseguradoras, acá).
 * - **Los movimientos en una categoría de seguros** que el dueño ya usa («Seguros», «Seguro del carro»,
 *   «Pólizas»): el nombre de la categoría viaja en cada movimiento y en cada regla, así que no hace falta
 *   la lista de categorías aparte.
 * - **Las reglas recurrentes** de seguros (por categoría o por nombre): no dan montos —el monto sale
 *   siempre de movimientos reales—, pero dan el nombre que el dueño eligió y cada cuántos meses se cobra.
 * - **Las suscripciones** que el detector encontró y son seguros: su nombre y si son anuales.
 *
 * **Nunca inventa una cifra ni un tipo**: el monto sale de movimientos reales o del seguro cargado en el
 * crédito, y lo que no se puede leer va como [TipoDeSeguro.OTRO].
 */

/** El tipo de un seguro, para agruparlo en la pantalla. */
@Serializable
enum class TipoDeSeguro {
    /** El seguro de vida deudor que va dentro de la cuota de un crédito. */
    VIDA_DEL_CREDITO,
    /** Incendio o terremoto cobrado aparte por un crédito (solo por texto: el crédito no lo guarda). */
    HOGAR_DEL_CREDITO,
    /** El seguro que cobra una tarjeta de crédito (vida deudor de la tarjeta, protección). */
    DE_TARJETA,
    /** SOAT o el seguro del vehículo. */
    AUTO,
    /** Medicina prepagada y pólizas de salud. */
    SALUD,
    VIDA,
    EXEQUIAS,
    DESEMPLEO,
    /** El hogar, cobrado aparte de un crédito. */
    HOGAR,
    /** Un seguro que Movi no sabe de qué es. */
    OTRO,
}

/** Un cobro que prueba un seguro: el movimiento real. */
@Serializable
data class CobroDelSeguro(
    val movimientoId: String,
    /** ISO, en Bogotá. */
    val fecha: String,
    val monto: Long,
    val descripcion: String,
)

/**
 * **Un seguro detectado.** Uno solo aunque se cobre muchas veces: misma cuenta + misma clave de comercio
 * ([claveDeComercio], la de las suscripciones y de «Lo que se sale de lo normal»).
 */
@Serializable
data class SeguroDetectado(
    /** Estable entre lecturas: `credito:<cuenta>` o `<cuenta>:<comercio>`. La huella es `seguro:<clave>`. */
    val clave: String,
    val tipo: TipoDeSeguro,
    /** Legible: «Seguro de vida · Vehículo 4083», «Seguro de la tarjeta Master Black», «SOAT». */
    val nombre: String,
    val cuentaId: String,
    val cuentaNombre: String = "",
    /** Cuánto pesa en un mes: el promedio de los meses en que se cobró, o el cobro repartido ([mesesEntreCobros]). */
    val alMes: Long,
    /** 1 = todos los meses; 12 = una vez al año (el SOAT); otro, cada tantos meses (lo dice su regla). */
    val mesesEntreCobros: Int = 1,
    /** El cobro de un seguro que no es mensual (el del último ciclo); `null` si es mensual. */
    val montoDelCiclo: Long? = null,
    /** ISO; `null` en un crédito sin cuotas anotadas. */
    val ultimoCobro: String? = null,
    /** Cobrado hace [DIAS_PARA_SEGUIR_COBRANDOSE] días o menos (o dentro de su ciclo); un crédito vivo, siempre. */
    val sigueCobrandose: Boolean = true,
    /** El texto lo dice sin duda, o el dueño dijo «Sí, es un seguro». Si no, va en «¿Son seguros?». */
    val concluyente: Boolean = true,
    /** El dueño dijo «Sí, es un seguro». */
    val confirmado: Boolean = false,
    /** Va dentro de la cuota de [creditoId]: no hay cobros sueltos, la prueba es el crédito. */
    val dentroDeLaCuota: Boolean = false,
    val creditoId: String? = null,
    /** Los movimientos que lo prueban, del más reciente al más viejo. */
    val cobros: List<CobroDelSeguro> = emptyList(),
)

/** Lo que contesta `GET /api/seguros`. */
@Serializable
data class SegurosQuePagas(
    /** Los seguros que cuentan: concluyentes o confirmados, sin los descartados. Por tipo y monto. */
    val seguros: List<SeguroDetectado> = emptyList(),
    /** Los que el texto no deja decir («Sura» puede ser la EPS): «¿Son seguros?». No suman en el total. */
    val porConfirmar: List<SeguroDetectado> = emptyList(),
    /** Lo que pagas al mes en seguros: los de [seguros] que siguen cobrándose, los anuales repartidos. */
    val alMes: Long = 0L,
    /** Lo mismo en un año: lo mensual × 12 más los no mensuales con su cobro anual. */
    val alAno: Long = 0L,
    /** ¿Algún seguro del total no es mensual? La frase lo dice: «los anuales, repartidos en 12 meses». */
    val conAnuales: Boolean = false,
    /** El dueño ya tocó «Me interesa» (lo dice el tracking propio; con «Mandar datos de uso» apagado, no). */
    val meInteresa: Boolean = false,
)

/** El cuerpo de «No es un seguro» y de «Sí, es un seguro»: la clave del detectado. */
@Serializable
data class RespuestaDelSeguro(val clave: String)

/** La ventana: los movimientos de los últimos 12 meses. */
const val MESES_DE_LA_VENTANA_DE_SEGUROS: Int = 12

/**
 * Un seguro mensual **sigue cobrándose** si su último cobro fue hace esto o menos. Elegido, no medido:
 * un mes y medio deja pasar un cobro que llegó tarde sin dar por vivo uno que se canceló hace dos meses.
 */
const val DIAS_PARA_SEGUIR_COBRANDOSE: Int = 45

/** El prefijo de la huella de «No es un seguro» en `anomalias_descartadas`. */
const val PREFIJO_DE_LA_HUELLA_DEL_SEGURO: String = "seguro:"

/**
 * El prefijo de la huella de «Sí, es un seguro», en la **misma** tabla: la forma más simple que no pide
 * esquema nuevo. Una huella distinta (y no un estado) porque `anomalias_descartadas` ya es un conjunto de
 * (usuario, huella) con su upsert idempotente; confirmar quita la de «No es» y descartar quita la de «Sí»,
 * así la última respuesta del dueño es la que vale.
 */
const val PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO: String = "seguro-si:"

/** El largo máximo de una clave: la huella entra en los 300 de `anomalias_descartadas.huella`. */
const val MAX_CLAVE_DE_SEGURO: Int = 250

fun huellaDelSeguro(clave: String): String = PREFIJO_DE_LA_HUELLA_DEL_SEGURO + clave
fun huellaDelSeguroConfirmado(clave: String): String = PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO + clave

// ── Cómo se lee un texto ─────────────────────────────────────────────────────────────────────

/**
 * **Los rótulos que dicen «seguro» sin duda**, como palabras enteras del texto sin tildes: «SEGURO»,
 * «SEG », «PÓLIZA», «SOAT». No «seguridad» («SEGURIDAD PRIVADA» no es un seguro): se comparan palabras,
 * no pedazos.
 */
private val ROTULOS_DE_SEGURO = setOf("seguro", "seguros", "seg", "poliza", "polizas", "soat", "aseguradora")

/**
 * Rótulos que solos **no** alcanzan: «PROTECCIÓN» y «ASISTENCIA» también son de cosas que no son
 * seguros («asistencia técnica»). Con una aseguradora, otro rótulo o el tipo del seguro sí cuentan; solos,
 * el seguro va con «¿Es un seguro?».
 */
private val ROTULOS_DEBILES = setOf("proteccion", "asistencia")

/**
 * **Las aseguradoras que solo pueden ser una aseguradora**: su nombre basta. Frases como listas de
 * palabras (se buscan seguidas en el texto).
 */
private val ASEGURADORAS_INEQUIVOCAS: List<List<String>> = listOf(
    listOf("allianz"), listOf("mapfre"), listOf("axa", "colpatria"), listOf("cardif"), listOf("metlife"),
    listOf("chubb"), listOf("qualitas"), listOf("pan", "american"), listOf("seguros", "del", "estado"),
    listOf("seguros", "mundial"),
)

/**
 * **Las aseguradoras cuyo nombre también es otra cosa**: «Bolívar» es una calle o un comercio, «Sura»
 * también es la EPS y la ARL, «Colmena» y «Positiva» son ARL, «Equidad», «Solidaria», «Previsora» y
 * «Liberty» son palabras o comercios, «SBS» y «HDI» son siglas cortas. Solas, «¿Es un seguro?»; junto a
 * un comercio ([PALABRAS_DE_COMERCIO]), nada.
 */
private val ASEGURADORAS_AMBIGUAS: List<List<String>> = listOf(
    listOf("sura"), listOf("bolivar"), listOf("colmena"), listOf("positiva"), listOf("equidad"),
    listOf("solidaria"), listOf("previsora"), listOf("liberty"), listOf("sbs"), listOf("hdi"),
)

/** Medicina prepagada y pólizas de salud por su nombre: van como [TipoDeSeguro.SALUD], sin duda. */
private val SALUD_POR_NOMBRE: List<List<String>> = listOf(
    listOf("colsanitas"), listOf("medisanitas"), listOf("colmedica"), listOf("prepagada"),
    listOf("coomeva", "mp"), listOf("allianz", "salud"), listOf("bolivar", "salud"), listOf("salud", "bolivar"),
)

/** Lo que dice que una aseguradora ambigua es otra cosa del sistema de salud o de pensiones. */
private val PALABRAS_DE_SEGURIDAD_SOCIAL = setOf("eps", "arl", "ips", "pension", "pensiones", "cesantias", "pila")

/** Lo que dice que «Bolívar» (o «Sura», o «Liberty») es un comercio o una dirección, no una aseguradora. */
private val PALABRAS_DE_COMERCIO = setOf(
    "panaderia", "restaurante", "drogueria", "farmacia", "supermercado", "mercado", "almacen", "tienda",
    "parqueadero", "hotel", "bar", "cafe", "plaza", "centro", "cc", "calle", "cl", "cra", "carrera", "kr",
    "avenida", "av", "estacion", "ferreteria", "papeleria", "licores", "pizzeria", "asadero", "comidas",
    "gimnasio", "peluqueria", "colegio", "universidad", "parque", "teatro", "simon", "cine", "cinema",
)

/** Las palabras que dicen el tipo. */
private val PALABRAS_DE_AUTO = setOf("soat", "vehiculo", "carro", "autos", "moto", "automovil")
private val PALABRAS_DE_VIDA = setOf("vida")
private val PALABRAS_DE_EXEQUIAS = setOf("exequias", "exequial", "exequiales", "funerario", "funerarios")
private val PALABRAS_DE_DESEMPLEO = setOf("desempleo")
private val PALABRAS_DE_HOGAR = setOf("hogar", "vivienda", "incendio", "terremoto")
private val PALABRAS_DE_SALUD = setOf("salud", "medico", "medica", "hospitalizacion")
private val PALABRAS_DE_TARJETA = setOf("tarjeta", "tc", "deudor", "saldo", "pagos")

/** ¿El nombre de esta categoría es de seguros? («Seguros», «Seguro del carro», «Pólizas», «SOAT»). */
fun esCategoriaDeSeguros(nombre: String): Boolean {
    val palabras = palabrasDe(nombre)
    return palabras.any { it in ROTULOS_DE_SEGURO }
}

/** Las palabras de un texto: sin tildes, en minúsculas, partidas por todo lo que no es letra o cifra. */
private fun palabrasDe(texto: String): List<String> =
    normalizarParaBuscar(texto).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

private fun List<String>.contieneFrase(frase: List<String>): Boolean {
    if (frase.isEmpty() || frase.size > size) return false
    for (i in 0..size - frase.size) if (frase.indices.all { this[i + it] == frase[it] }) return true
    return false
}

/** Lo que un texto dice de un seguro: si lo es (y si sin duda) y de qué tipo. `null` = no habla de seguros. */
internal data class LecturaDeSeguro(val concluyente: Boolean, val tipo: TipoDeSeguro?)

/**
 * **¿Este texto habla de un seguro?** Pura, la única lista de rótulos y aseguradoras de Movi. [tipo] es
 * el que dicen sus palabras, o `null` si no dicen ninguno (quien llama decide: en una tarjeta, el de la
 * tarjeta; si no, [TipoDeSeguro.OTRO]).
 */
internal fun lecturaDelTexto(texto: String): LecturaDeSeguro? {
    val p = palabrasDe(texto)
    if (p.isEmpty()) return null
    // «SEG SOCIAL» es la seguridad social (la PILA), no un seguro.
    if (p.contieneFrase(listOf("seg", "social")) || p.contieneFrase(listOf("seguridad", "social"))) return null
    val tipo = tipoDeLasPalabras(p)
    if (SALUD_POR_NOMBRE.any { p.contieneFrase(it) }) return LecturaDeSeguro(true, TipoDeSeguro.SALUD)
    val rotulo = p.any { it in ROTULOS_DE_SEGURO }
    val inequivoca = ASEGURADORAS_INEQUIVOCAS.any { p.contieneFrase(it) }
    if (rotulo || inequivoca) return LecturaDeSeguro(true, tipo)
    val ambigua = ASEGURADORAS_AMBIGUAS.any { p.contieneFrase(it) }
    val debil = p.any { it in ROTULOS_DEBILES }
    if (ambigua) {
        if (debil && tipo != null) return LecturaDeSeguro(true, tipo)
        if (p.any { it in PALABRAS_DE_SEGURIDAD_SOCIAL }) return LecturaDeSeguro(false, tipo)
        if (p.any { it in PALABRAS_DE_COMERCIO }) return null
        return LecturaDeSeguro(false, tipo)
    }
    if (debil) return LecturaDeSeguro(tipo != null, tipo)
    return null
}

private fun tipoDeLasPalabras(p: List<String>): TipoDeSeguro? = when {
    p.any { it in PALABRAS_DE_EXEQUIAS } -> TipoDeSeguro.EXEQUIAS
    p.any { it in PALABRAS_DE_DESEMPLEO } -> TipoDeSeguro.DESEMPLEO
    "soat" in p -> TipoDeSeguro.AUTO
    p.contieneFrase(listOf("todo", "riesgo")) -> TipoDeSeguro.AUTO
    p.any { it in PALABRAS_DE_AUTO } -> TipoDeSeguro.AUTO
    p.any { it in PALABRAS_DE_SALUD } -> TipoDeSeguro.SALUD
    p.any { it in PALABRAS_DE_HOGAR } -> TipoDeSeguro.HOGAR
    // «Vida deudor» de una tarjeta lo decide quien llama (la cuenta): acá, vida.
    p.any { it in PALABRAS_DE_VIDA } -> TipoDeSeguro.VIDA
    p.any { it in PALABRAS_DE_TARJETA } -> TipoDeSeguro.DE_TARJETA
    else -> null
}

/** ¿El texto dice que el cobro es anual? (el SOAT siempre lo es por ley). */
private fun diceQueEsAnual(texto: String): Boolean {
    val p = palabrasDe(texto)
    return "soat" in p || "anual" in p || "renovacion" in p
}

// ── El detector ──────────────────────────────────────────────────────────────────────────────

private data class Candidato(val evento: FinancialEvent, val lectura: LecturaDeSeguro, val nombreDeRegla: String?, val meses: Int)

/**
 * **Los seguros detectados**, sin aplicar todavía lo que dijo el dueño (descartados y confirmados): lo
 * usa el server para saber si una clave existe antes de guardar un «No es» o un «Sí». Ver
 * [losSegurosQuePagas].
 *
 * @param eventos los movimientos sin anulados (los de [anulados] se quitan igual).
 * @param hoy ISO, en Bogotá. @param diaDe la fecha ISO en Bogotá de un instante.
 */
fun segurosDetectados(
    eventos: List<FinancialEvent>,
    cuentas: List<Account>,
    creditos: List<CreditSummary>,
    reglas: List<RecurringRule>,
    suscripciones: List<Subscription>,
    hoy: String,
    diaDe: (Long) -> String,
    anulados: Set<String> = emptySet(),
): List<SeguroDetectado> {
    val hoyFecha = LocalDate.parse(hoy)
    val desde = hoyFecha.minus(DatePeriod(months = MESES_DE_LA_VENTANA_DE_SEGUROS)).toString()
    val cuentaPorId = cuentas.associateBy { it.id }
    val vivos = eventos.filter { it.id !in anulados }

    val delCredito = segurosDeLosCreditos(creditos, vivos, diaDe)
    val creditosConSeguro = delCredito.mapNotNull { it.creditoId }.toSet()

    // Las reglas de seguros: su nombre comparable → (nombre, cada cuántos meses).
    val reglasDeSeguro = reglas.filter { it.type == TransactionType.EXPENSE }
        .filter { esCategoriaDeSeguros(it.category) || lecturaDelTexto(it.name)?.concluyente == true }
        .associate { claveComparableDeNombre(it.name) to (it.name to (it.frecuenciaEfectiva.meses ?: 1)) }
    // Las suscripciones vivas que son seguros: su clave de comercio → (nombre, cada cuántos meses).
    val suscripcionesDeSeguro = suscripciones.filter { it.status != SubStatus.DISMISSED }
        .filter { lecturaDelTexto(it.displayName)?.concluyente == true || lecturaDelTexto(it.merchantKey.replace('_', ' '))?.concluyente == true }
        .associate { it.merchantKey to (it.displayName to if (it.periodicidad == PeriodicidadDeCobro.ANUAL) 12 else 1) }

    val candidatos = vivos.asSequence()
        .filter { it.type == TransactionType.EXPENSE && it.currency == "COP" && montoContado(it) > 0L && it.transferId == null }
        .filter { it.category != CUOTA_CATEGORY && it.category != PAYROLL_DEDUCTION_CATEGORY }
        .filter { it.accountId !in creditosConSeguro }
        .filter { diaDe(it.timestamp) in desde..hoy }
        .mapNotNull { e ->
            val texto = listOfNotNull(e.description, e.merchant).joinToString(" ")
            val comercio = claveDeComercio(e)
            val regla = reglasDeSeguro[claveComparableDeNombre(e.description)]
            val suscripcion = comercio?.let { suscripcionesDeSeguro[it] }
            val porTexto = lecturaDelTexto(texto)
            val porCategoria = if (esCategoriaDeSeguros(e.category)) {
                LecturaDeSeguro(true, tipoDeLasPalabras(palabrasDe("$texto ${e.category}")))
            } else null
            val lectura = when {
                regla != null || suscripcion != null ->
                    LecturaDeSeguro(true, tipoDeLasPalabras(palabrasDe(listOfNotNull(texto, regla?.first, suscripcion?.first).joinToString(" "))))
                porCategoria != null -> porCategoria
                else -> porTexto
            } ?: return@mapNotNull null
            val meses = when {
                diceQueEsAnual(texto) -> 12
                regla != null && regla.second > 1 -> regla.second
                suscripcion != null && suscripcion.second > 1 -> suscripcion.second
                else -> 1
            }
            Candidato(e, lectura, regla?.first ?: suscripcion?.first, meses)
        }
        .toList()

    val porCobros = candidatos
        .groupBy { "${it.evento.accountId}:${(claveDeComercio(it.evento) ?: it.evento.id).take(120)}" }
        .map { (clave, delGrupo) -> seguroDelGrupo(clave.take(MAX_CLAVE_DE_SEGURO), delGrupo, cuentaPorId, hoyFecha, diaDe) }

    return delCredito + porCobros
}

/**
 * **Lo que pagas en seguros**: [segurosDetectados] con lo que dijo el dueño. Lo descartado («No es un
 * seguro») sale de todo; lo confirmado («Sí») cuenta como concluyente. El total suma solo lo que cuenta y
 * sigue cobrándose; lo que dejó de cobrarse se ve, marcado, sin sumar.
 */
fun losSegurosQuePagas(
    eventos: List<FinancialEvent>,
    cuentas: List<Account>,
    creditos: List<CreditSummary>,
    reglas: List<RecurringRule>,
    suscripciones: List<Subscription>,
    hoy: String,
    diaDe: (Long) -> String,
    descartados: Set<String> = emptySet(),
    confirmados: Set<String> = emptySet(),
    anulados: Set<String> = emptySet(),
): SegurosQuePagas {
    val todos = segurosDetectados(eventos, cuentas, creditos, reglas, suscripciones, hoy, diaDe, anulados)
        .filter { it.clave !in descartados }
        .map { if (it.clave in confirmados) it.copy(concluyente = true, confirmado = true) else it }
    val (cuentan, porConfirmar) = todos.partition { it.concluyente }
    val vivos = cuentan.filter { it.sigueCobrandose }
    val alMes = vivos.sumOf { it.alMes }
    val alAno = vivos.sumOf { s -> if (s.mesesEntreCobros > 1) (s.montoDelCiclo ?: 0L) * (12 / s.mesesEntreCobros) else s.alMes * 12 }
    return SegurosQuePagas(
        seguros = cuentan.sortedWith(compareBy<SeguroDetectado> { it.tipo.ordinal }.thenBy { !it.sigueCobrandose }.thenByDescending { it.alMes }),
        porConfirmar = porConfirmar.sortedByDescending { it.ultimoCobro ?: "" },
        alMes = alMes,
        alAno = alAno,
        conAnuales = vivos.any { it.mesesEntreCobros > 1 },
    )
}

private fun segurosDeLosCreditos(creditos: List<CreditSummary>, eventos: List<FinancialEvent>, diaDe: (Long) -> String): List<SeguroDetectado> =
    creditos.mapNotNull { c ->
        val terms = c.terms ?: return@mapNotNull null
        val seguro = terms.insuranceMonthly?.takeIf { it > 0L } ?: return@mapNotNull null
        if (c.account.currency != "COP") return@mapNotNull null
        // Lo paga otro: no es un seguro que tú pagues. Pagado: ya no se cobra.
        if (!terms.paidBy.isNullOrBlank()) return@mapNotNull null
        if ((c.paidPct ?: 0.0) >= 1.0 && c.hasMovements) return@mapNotNull null
        val ultimaCuota = eventos
            .filter { it.accountId == c.account.id && (it.category == CUOTA_CATEGORY || it.category == PAYROLL_DEDUCTION_CATEGORY) }
            .maxByOrNull { it.timestamp }
        SeguroDetectado(
            clave = "credito:${c.account.id}".take(MAX_CLAVE_DE_SEGURO),
            tipo = TipoDeSeguro.VIDA_DEL_CREDITO,
            nombre = "Seguro de vida · ${c.account.name}",
            cuentaId = c.account.id,
            cuentaNombre = c.account.name,
            alMes = seguro,
            ultimoCobro = ultimaCuota?.let { diaDe(it.timestamp) },
            sigueCobrandose = true,
            dentroDeLaCuota = true,
            creditoId = c.account.id,
        )
    }

private fun seguroDelGrupo(
    clave: String,
    delGrupo: List<Candidato>,
    cuentaPorId: Map<String, Account>,
    hoy: LocalDate,
    diaDe: (Long) -> String,
): SeguroDetectado {
    val ordenados = delGrupo.sortedByDescending { it.evento.timestamp }
    val ultimo = ordenados.first()
    val cuenta = cuentaPorId[ultimo.evento.accountId]
    val esTarjeta = cuenta?.type == AccountType.CREDIT_CARD
    val esCredito = cuenta?.type == AccountType.LOAN
    val leido = ordenados.firstNotNullOfOrNull { it.lectura.tipo }
    val tipo = when {
        leido == TipoDeSeguro.HOGAR && esCredito -> TipoDeSeguro.HOGAR_DEL_CREDITO
        esTarjeta && (leido == null || leido == TipoDeSeguro.DE_TARJETA ||
            (leido == TipoDeSeguro.VIDA && ordenados.any { "deudor" in palabrasDe(it.evento.description) })) -> TipoDeSeguro.DE_TARJETA
        leido == TipoDeSeguro.DE_TARJETA && !esTarjeta -> TipoDeSeguro.OTRO
        else -> leido ?: TipoDeSeguro.OTRO
    }
    val meses = ordenados.maxOf { it.meses }
    val porMes = ordenados.groupBy { diaDe(it.evento.timestamp).take(7) }.mapValues { (_, l) -> l.sumOf { montoContado(it.evento) } }
    val fechaUltima = diaDe(ultimo.evento.timestamp)
    val montoDelCiclo = if (meses > 1) porMes[fechaUltima.take(7)] ?: montoContado(ultimo.evento) else null
    val alMes = if (montoDelCiclo != null) (montoDelCiclo + meses / 2) / meses
    else (porMes.values.sum() + porMes.size / 2) / porMes.size.coerceAtLeast(1)
    val dias = (hoy.toEpochDays() - LocalDate.parse(fechaUltima).toEpochDays()).toInt()
    val limite = if (meses > 1) meses * 31 + DIAS_PARA_SEGUIR_COBRANDOSE else DIAS_PARA_SEGUIR_COBRANDOSE
    val concluyente = ordenados.any { it.lectura.concluyente }
    val nombreDeRegla = ordenados.firstNotNullOfOrNull { it.nombreDeRegla }
    val textoDelUltimo = listOfNotNull(ultimo.evento.description, ultimo.evento.merchant).joinToString(" ")
    return SeguroDetectado(
        clave = clave,
        tipo = tipo,
        nombre = nombreDeRegla ?: nombreDelSeguro(tipo, cuenta?.name, textoDelUltimo, ultimo.evento, concluyente),
        cuentaId = ultimo.evento.accountId,
        cuentaNombre = cuenta?.name.orEmpty(),
        alMes = alMes,
        mesesEntreCobros = meses,
        montoDelCiclo = montoDelCiclo,
        ultimoCobro = fechaUltima,
        sigueCobrandose = dias <= limite,
        concluyente = concluyente,
        cobros = ordenados.map { CobroDelSeguro(it.evento.id, diaDe(it.evento.timestamp), montoContado(it.evento), it.evento.description) },
    )
}

/** El nombre legible de un seguro por su tipo. Lo que no se sabe leer se nombra por su comercio. */
private fun nombreDelSeguro(tipo: TipoDeSeguro, cuenta: String?, texto: String, evento: FinancialEvent, concluyente: Boolean): String {
    val delComercio = normalizeMerchant(evento.merchant?.takeIf { it.isNotBlank() } ?: evento.description)?.displayName
        ?: evento.description.trim()
    if (!concluyente) return delComercio
    val p = palabrasDe(texto)
    return when (tipo) {
        TipoDeSeguro.VIDA_DEL_CREDITO -> "Seguro de vida · ${cuenta.orEmpty()}"
        TipoDeSeguro.HOGAR_DEL_CREDITO -> "Seguro de hogar · ${cuenta.orEmpty()}"
        TipoDeSeguro.DE_TARJETA -> if (cuenta.isNullOrBlank()) "Seguro de la tarjeta" else "Seguro de la tarjeta $cuenta"
        TipoDeSeguro.AUTO -> if ("soat" in p) "SOAT" else "Seguro del vehículo"
        TipoDeSeguro.SALUD -> if ("prepagada" in p || p.contieneFrase(listOf("coomeva", "mp"))) "Medicina prepagada" else "Seguro de salud"
        TipoDeSeguro.VIDA -> "Seguro de vida"
        TipoDeSeguro.EXEQUIAS -> "Plan exequial"
        TipoDeSeguro.DESEMPLEO -> "Seguro de desempleo"
        TipoDeSeguro.HOGAR -> "Seguro de hogar"
        TipoDeSeguro.OTRO -> delComercio
    }
}

// ── Lo que se dice ───────────────────────────────────────────────────────────────────────────

/** El título del bloque de cada tipo en la pantalla, en este orden. */
fun tituloDelTipoDeSeguro(tipo: TipoDeSeguro): String = when (tipo) {
    TipoDeSeguro.VIDA_DEL_CREDITO, TipoDeSeguro.HOGAR_DEL_CREDITO -> "Dentro de tus créditos"
    TipoDeSeguro.DE_TARJETA -> "De tus tarjetas"
    TipoDeSeguro.AUTO -> "Vehículo"
    TipoDeSeguro.SALUD -> "Salud"
    TipoDeSeguro.VIDA -> "Vida"
    TipoDeSeguro.EXEQUIAS -> "Exequias"
    TipoDeSeguro.DESEMPLEO -> "Desempleo"
    TipoDeSeguro.HOGAR -> "Hogar"
    TipoDeSeguro.OTRO -> "Otros"
}

// ── Vuelta 132 · el tramo del total, para el tracking ────────────────────────────────────────

/**
 * **El tramo de lo que pagas al mes en seguros**, para el tracking propio (`SEGUROS_VISTOS` y
 * `SEGUROS_ME_INTERESA`): nunca el monto exacto. Los cortes son elegidos, no medidos.
 */
fun tramoDeLoQuePagasEnSeguros(alMes: Long): String = when {
    alMes <= 0L -> "SIN_SEGUROS"
    alMes < 50_000L -> "MENOS_DE_50K"
    alMes < 150_000L -> "DE_50K_A_150K"
    alMes < 300_000L -> "DE_150K_A_300K"
    alMes < 600_000L -> "DE_300K_A_600K"
    else -> "MAS_DE_600K"
}
