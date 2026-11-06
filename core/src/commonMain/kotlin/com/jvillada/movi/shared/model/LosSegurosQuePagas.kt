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
 *   deudor (u otro cargo mensual fijo) incluido en la cuota»). Es UN campo sin tipo (en un hipotecario
 *   suele sumar vida, incendio y terremoto), así que se dice «Seguro del crédito · X»: nunca un tipo
 *   que el dato no garantiza. Un crédito que paga otro ([CreditTerms.paidBy]) o ya pagado no entra: no es algo que pagues.
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
    /** El seguro que va dentro de la cuota de un crédito ([CreditTerms.insuranceMonthly], sin tipo). */
    DEL_CREDITO,
    /** El seguro que cobra una tarjeta de crédito (vida deudor de la tarjeta). */
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
    /** Legible: «Seguro del crédito · Vehículo 4083», «Seguro de la tarjeta Master Black», «SOAT». */
    val nombre: String,
    val cuentaId: String,
    val cuentaNombre: String = "",
    /** La moneda de los cobros. Solo los de pesos suman en el total; los demás se dicen aparte. */
    val moneda: String = "COP",
    /**
     * Cuánto pesa en un mes: el promedio de los meses en que se cobró, o el cobro repartido en su ciclo
     * ([mesesEntreCobros]). **0 en un [pagadoUnaVez]**: Movi no sabe cada cuánto se paga y no lo inventa.
     */
    val alMes: Long,
    /** 1 = todos los meses; 12 = una vez al año (el SOAT); otro, cada tantos meses. 0 en un [pagadoUnaVez]. */
    val mesesEntreCobros: Int = 1,
    /** El cobro de un seguro que no es mensual (el del último ciclo) o el de un [pagadoUnaVez]; `null` si es mensual. */
    val montoDelCiclo: Long? = null,
    /**
     * **Un solo cobro y ninguna señal de cada cuánto** (ni dos meses distintos, ni SOAT, ni «anual», ni una
     * regla o una suscripción, ni la respuesta del dueño): no suma «al mes», nunca «deja de cobrarse» y la
     * pantalla pregunta «¿Cada cuánto lo pagas?».
     */
    val pagadoUnaVez: Boolean = false,
    /**
     * Lo pagado en la ventana por un [pagadoUnaVez]: su único cobro o, si fueron varios sin ritmo mensual
     * («varios pagos»: marzo y octubre), todos. Es lo que suma [SegurosQuePagas.enPagosDeUnaVez].
     */
    val pagadoSinRitmo: Long? = null,
    /** ISO; `null` en un crédito sin cuotas anotadas en la ventana. */
    val ultimoCobro: String? = null,
    /** Cobrado hace [DIAS_PARA_SEGUIR_COBRANDOSE] días o menos (o dentro de su ciclo); un crédito vivo, siempre. */
    val sigueCobrandose: Boolean = true,
    /** El texto lo dice sin duda, o el dueño dijo «Sí, es un seguro». Si no, va en «¿Son seguros?». */
    val concluyente: Boolean = true,
    /** El dueño dijo «Sí, es un seguro» (o «Es otro» a [posibleDuplicadoDe]). */
    val confirmado: Boolean = false,
    /** Va dentro de la cuota de [creditoId]: no hay cobros sueltos, la prueba es el crédito. */
    val dentroDeLaCuota: Boolean = false,
    val creditoId: String? = null,
    /**
     * **Se parece al seguro que ya va dentro de la cuota de este crédito** (su nombre): mismo banco o un
     * texto de vida deudor, incendio, terremoto o hipoteca, y el monto a ±[TOLERANCIA_DEL_DUPLICADO_PORCIENTO] %.
     * No suma hasta que el dueño diga «Es otro»; con «Es el mismo» se descarta.
     */
    val posibleDuplicadoDe: String? = null,
    /** Los movimientos que lo prueban, del más reciente al más viejo. */
    val cobros: List<CobroDelSeguro> = emptyList(),
)

/** Lo que contesta `GET /api/seguros` (y cada respuesta del dueño, ya con lo que dijo). */
@Serializable
data class SegurosQuePagas(
    /** Los seguros que cuentan: concluyentes o confirmados, sin los descartados. Por tipo y monto. */
    val seguros: List<SeguroDetectado> = emptyList(),
    /** Los que el texto no deja decir («Sura» puede ser la EPS) y los posibles duplicados del crédito. No suman. */
    val porConfirmar: List<SeguroDetectado> = emptyList(),
    /** Lo que pagas al mes en seguros, en pesos: los de [seguros] que siguen cobrándose, los anuales repartidos. */
    val alMes: Long = 0L,
    /** Lo mismo en un año: lo mensual × 12 más los no mensuales con su cobro anual. */
    val alAno: Long = 0L,
    /** ¿Algún seguro del total no es mensual? La frase lo dice: «los anuales, repartidos en 12 meses». */
    val conAnuales: Boolean = false,
    /** Lo pagado una vez sin saber cada cuánto ([SeguroDetectado.pagadoUnaVez]), en pesos, aparte del total. */
    val enPagosDeUnaVez: Long = 0L,
    /** Lo que pagas al mes en otra moneda («USD» → 12), fuera del total: nunca se convierte en silencio. */
    val otrasMonedas: Map<String, Long> = emptyMap(),
    /** El dueño ya tocó «Me interesa» (la huella `seguros-me-interesa`, su elección, no telemetría). */
    val meInteresa: Boolean = false,
)

/** El cuerpo de «No es un seguro», «Sí» y «¿Cada cuánto?»: la clave del detectado y, en el último, los meses. */
@Serializable
data class RespuestaDelSeguro(val clave: String, val meses: Int? = null)

/** La ventana: los movimientos de los últimos 12 meses. */
const val MESES_DE_LA_VENTANA_DE_SEGUROS: Int = 12

/**
 * Un seguro mensual **sigue cobrándose** si su último cobro fue hace esto o menos. Elegido, no medido:
 * un mes y medio deja pasar un cobro que llegó tarde sin dar por vivo uno que se canceló hace dos meses.
 */
const val DIAS_PARA_SEGUIR_COBRANDOSE: Int = 45

/** Un cargo «se parece» al seguro de un crédito con el monto a ±10 % del de la cuota. Elegido, no medido. */
const val TOLERANCIA_DEL_DUPLICADO_PORCIENTO: Long = 10L

/** Las respuestas a «¿Cada cuánto lo pagas?»: cada mes, cada 3 meses, cada 6 meses o cada año. */
val MESES_QUE_SE_PUEDEN_RESPONDER: Set<Int> = setOf(1, 3, 6, 12)

/**
 * Un grupo sin señal es **mensual** solo si sus cobros caen en meses casi seguidos: entre dos meses con
 * cobro hay a lo sumo esto. Elegido, no medido: deja pasar un mes que el banco no cobró (o que se cobró
 * doble el siguiente) sin llamar «mensual» a dos pagos de marzo y octubre.
 */
const val HUECO_MAXIMO_DE_UN_MENSUAL_EN_MESES: Int = 2

/** El prefijo de la huella de «No es un seguro» en `anomalias_descartadas`. */
const val PREFIJO_DE_LA_HUELLA_DEL_SEGURO: String = "seguro:"

/**
 * El prefijo de la huella de «Sí, es un seguro», en la **misma** tabla: la forma más simple que no pide
 * esquema nuevo. Una huella distinta (y no un estado) porque `anomalias_descartadas` ya es un conjunto de
 * (usuario, huella) con su upsert idempotente; confirmar quita la de «No es» y descartar quita la de «Sí»,
 * así la última respuesta del dueño es la que vale.
 */
const val PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO: String = "seguro-si:"

/** «¿Cada cuánto lo pagas?»: `seguro-cada:<clave>:1` o `:12`, en la misma tabla; gana la última. */
const val PREFIJO_DE_LA_HUELLA_DE_CADA_CUANTO: String = "seguro-cada:"

/** «Me interesa que Movi me ayude con mis seguros»: un estado del dueño, en la misma tabla. */
const val HUELLA_DE_ME_INTERESA: String = "seguros-me-interesa"

/** El largo máximo de una clave: la huella entra en los 300 de `anomalias_descartadas.huella`. */
const val MAX_CLAVE_DE_SEGURO: Int = 250

fun huellaDelSeguro(clave: String): String = PREFIJO_DE_LA_HUELLA_DEL_SEGURO + clave
fun huellaDelSeguroConfirmado(clave: String): String = PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO + clave
fun huellaDeCadaCuanto(clave: String, meses: Int): String = "$PREFIJO_DE_LA_HUELLA_DE_CADA_CUANTO$clave:$meses"

/** Lo que dijo el dueño, leído de sus huellas. */
data class RespuestasDelDueno(
    val descartados: Set<String> = emptySet(),
    val confirmados: Set<String> = emptySet(),
    /** clave → 1 o 12. */
    val cadaCuanto: Map<String, Int> = emptyMap(),
    val meInteresa: Boolean = false,
)

/** Las respuestas de [huellas] (las de `anomalias_descartadas` del dueño; las que no son de seguros se ignoran). */
fun respuestasDeLasHuellas(huellas: Collection<String>): RespuestasDelDueno = RespuestasDelDueno(
    descartados = huellas.filter { it.startsWith(PREFIJO_DE_LA_HUELLA_DEL_SEGURO) }
        .map { it.removePrefix(PREFIJO_DE_LA_HUELLA_DEL_SEGURO) }.toSet(),
    confirmados = huellas.filter { it.startsWith(PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO) }
        .map { it.removePrefix(PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO) }.toSet(),
    cadaCuanto = huellas.filter { it.startsWith(PREFIJO_DE_LA_HUELLA_DE_CADA_CUANTO) }
        .mapNotNull { h ->
            val resto = h.removePrefix(PREFIJO_DE_LA_HUELLA_DE_CADA_CUANTO)
            val meses = resto.substringAfterLast(':').toIntOrNull()?.takeIf { it in MESES_QUE_SE_PUEDEN_RESPONDER }
            meses?.let { resto.substringBeforeLast(':') to it }
        }.toMap(),
    meInteresa = HUELLA_DE_ME_INTERESA in huellas,
)

// ── Cómo se lee un texto ─────────────────────────────────────────────────────────────────────

/**
 * **Los rótulos que dicen «seguro» sin duda**, como palabras enteras del texto sin tildes: «SEGURO»,
 * «SEG », «PÓLIZA», «SOAT». No «seguridad» («SEGURIDAD PRIVADA» no es un seguro): se comparan palabras,
 * no pedazos.
 */
private val ROTULOS_DE_SEGURO = setOf("seguro", "seguros", "seg", "poliza", "polizas", "soat", "aseguradora")

/**
 * Rótulos que solos **no** alcanzan: «PROTECCIÓN» (también es el fondo de pensiones) y «ASISTENCIA»
 * («asistencia técnica», un taller). Con una aseguradora son concluyentes; si no, «¿Es un seguro?», y
 * junto a pensiones o a un comercio, nada.
 */
private val ROTULOS_DEBILES = setOf("proteccion", "asistencia")

/** **Las aseguradoras que solo pueden ser una aseguradora**: su nombre basta (frases, palabras seguidas). */
private val ASEGURADORAS_INEQUIVOCAS: List<List<String>> = listOf(
    listOf("allianz"), listOf("mapfre"), listOf("axa", "colpatria"), listOf("cardif"), listOf("metlife"),
    listOf("chubb"), listOf("qualitas"), listOf("pan", "american"), listOf("seguros", "del", "estado"),
    listOf("seguros", "mundial"),
)

/**
 * **Las aseguradoras cuyo nombre también es otra cosa**: «Bolívar» es una calle o un comercio, «Sura»
 * también es la EPS, la ARL y el fondo, «Colmena» y «Positiva» son ARL, «Equidad», «Solidaria», «Previsora»
 * y «Liberty» son palabras o comercios, «SBS» y «HDI» son siglas cortas. Solas, «¿Es un seguro?»; junto a
 * un comercio ([PALABRAS_DE_COMERCIO]), nada.
 */
private val ASEGURADORAS_AMBIGUAS: List<List<String>> = listOf(
    listOf("sura"), listOf("bolivar"), listOf("colmena"), listOf("positiva"), listOf("equidad"),
    listOf("solidaria"), listOf("previsora"), listOf("liberty"), listOf("sbs"), listOf("hdi"),
)

/**
 * Medicina prepagada y pólizas de salud por su nombre: [TipoDeSeguro.SALUD], sin duda. «Prepagada» sola
 * no («RECARGA TARJETA PREPAGADA»): la frase «medicina prepagada» o junto a una aseguradora.
 */
private val SALUD_POR_NOMBRE: List<List<String>> = listOf(
    listOf("colsanitas"), listOf("medisanitas"), listOf("colmedica"), listOf("medicina", "prepagada"),
    listOf("coomeva", "mp"), listOf("allianz", "salud"), listOf("bolivar", "salud"), listOf("salud", "bolivar"),
)

/** Lo que dice que el texto es del sistema de salud, riesgos o pensiones, no un seguro privado. */
private val PALABRAS_DE_SEGURIDAD_SOCIAL = setOf(
    "eps", "arl", "ips", "pila", "pension", "pensiones", "pensional", "cesantias", "voluntario", "voluntaria",
    "aporte", "aportes", "fondo", "afp", "obligatoria",
)

/** Lo que dice que «Bolívar» (o «Sura», o «Asistencia») es un comercio o una dirección, no una aseguradora. */
private val PALABRAS_DE_COMERCIO = setOf(
    "panaderia", "restaurante", "drogueria", "farmacia", "supermercado", "mercado", "almacen", "tienda",
    "parqueadero", "hotel", "bar", "cafe", "plaza", "centro", "cc", "calle", "cl", "cra", "carrera", "kr",
    "avenida", "av", "estacion", "ferreteria", "papeleria", "licores", "pizzeria", "asadero", "comidas",
    "gimnasio", "peluqueria", "colegio", "universidad", "parque", "teatro", "simon", "cine", "cinema",
    "taller", "tecnica", "tecnico", "servicio", "lavadero", "montallantas",
)

/** Las palabras que dicen el tipo. */
private val PALABRAS_DE_AUTO = setOf("soat", "vehiculo", "carro", "autos", "moto", "automovil")
private val PALABRAS_DE_VIDA = setOf("vida")
private val PALABRAS_DE_EXEQUIAS = setOf("exequias", "exequial", "exequiales", "funerario", "funerarios")
private val PALABRAS_DE_DESEMPLEO = setOf("desempleo")
private val PALABRAS_DE_HOGAR = setOf("hogar", "vivienda", "incendio", "terremoto")
private val PALABRAS_DE_SALUD = setOf("salud", "medico", "medica", "hospitalizacion")
private val PALABRAS_DE_TARJETA = setOf("tarjeta", "tc", "deudor")

/** Lo que dice que un cargo puede ser el mismo seguro de un crédito (I1). */
private fun hablaDelSeguroDeUnCredito(p: List<String>): Boolean =
    p.any { it == "deudor" || it == "incendio" || it == "terremoto" || it.startsWith("hipotec") }

/** Palabras que no nombran un banco («Banco de Occidente» → «occidente»). */
private val PALABRAS_QUE_NO_NOMBRAN_UN_BANCO = setOf("banco", "de", "del", "la", "el", "s", "a", "sa", "colombia")

/** ¿El nombre de esta categoría es de seguros? («Seguros», «Seguro del carro», «Pólizas», «SOAT»). */
fun esCategoriaDeSeguros(nombre: String): Boolean = palabrasDe(nombre).any { it in ROTULOS_DE_SEGURO }

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
    // «SEG SOCIAL» es la seguridad social (la PILA); la cuota de manejo es una comisión del banco.
    if (p.contieneFrase(listOf("seg", "social")) || p.contieneFrase(listOf("seguridad", "social"))) return null
    if (p.contieneFrase(listOf("cuota", "manejo")) || p.contieneFrase(listOf("cuota", "de", "manejo"))) return null
    val tipo = tipoDeLasPalabras(p)
    if (SALUD_POR_NOMBRE.any { p.contieneFrase(it) }) return LecturaDeSeguro(true, TipoDeSeguro.SALUD)
    val inequivoca = ASEGURADORAS_INEQUIVOCAS.any { p.contieneFrase(it) }
    val ambigua = ASEGURADORAS_AMBIGUAS.any { p.contieneFrase(it) }
    if ("prepagada" in p && (inequivoca || ambigua)) return LecturaDeSeguro(true, TipoDeSeguro.SALUD)
    if (p.any { it in ROTULOS_DE_SEGURO } || inequivoca) return LecturaDeSeguro(true, tipo)
    val debil = p.any { it in ROTULOS_DEBILES }
    val social = p.any { it in PALABRAS_DE_SEGURIDAD_SOCIAL }
    val comercio = p.any { it in PALABRAS_DE_COMERCIO }
    if (ambigua) {
        if (social) return LecturaDeSeguro(false, tipo)
        if (comercio) return null
        // «Protección»/«Asistencia» con una aseguradora: la aseguradora lo vuelve un seguro.
        return LecturaDeSeguro(debil, tipo)
    }
    if (debil) {
        // «APORTE VOLUNTARIO PROTECCION» es el fondo de pensiones; «ASISTENCIA TECNICA», un servicio.
        if (social || comercio) return null
        return LecturaDeSeguro(false, tipo)
    }
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

/**
 * Un movimiento que habla de un seguro. [meses] es lo que dice una señal de cada cuánto: 12 con
 * SOAT/«anual»/«renovación», lo de su regla o su suscripción (también 1, mensual); `null` sin señal.
 */
private data class Candidato(val evento: FinancialEvent, val lectura: LecturaDeSeguro, val nombreDeRegla: String?, val meses: Int?)

/** Un crédito con seguro en la cuota, para reconocer el mismo seguro cobrado aparte (I1). */
private data class CreditoConSeguro(val id: String, val nombre: String, val seguro: Long, val banco: List<String>)

/**
 * **¿Este movimiento entra al detector?** Los filtros de los demás cálculos de gasto: un gasto que cuenta
 * en «Gastos» ([cuentaEnGastosEIngresos]: ni por confirmar, ni una liquidación a otra persona, ni una
 * cuenta de deuda), que no es de una categoría reservada (pago de tarjeta, traspaso, ajuste…) ni de dos
 * patas, que te tocó a ti ([montoContado]) y que no es parte de un traspaso.
 *
 * Por eso **nada de lo anotado dentro de una cuenta de crédito entra** (`isCashFlow` saca las `LOAN`): un
 * todo riesgo financiado dentro del crédito del carro no se ve. Es un falso negativo conocido; el seguro
 * que sí se ve de un crédito es el de sus condiciones ([CreditTerms.insuranceMonthly]).
 */
private fun entraAlDetector(e: FinancialEvent): Boolean =
    e.type == TransactionType.EXPENSE &&
        cuentaEnGastosEIngresos(e) &&
        e.saldaEspacioId == null &&
        e.transferId == null &&
        !esCategoriaQueNoSeElige(e.category) &&
        montoContado(e) > 0L

/**
 * **Los seguros detectados**, sin aplicar todavía lo que dijo el dueño sobre si son seguros (descartados
 * y confirmados): lo usa el server para saber si una clave existe antes de guardar una respuesta. Lo que
 * dijo de cada cuánto ([cadaCuanto]) sí entra: cambia la cifra. Ver [losSegurosQuePagas].
 *
 * @param eventos los movimientos sin anulados (los de [anulados] se quitan igual); basta la ventana.
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
    cadaCuanto: Map<String, Int> = emptyMap(),
): List<SeguroDetectado> {
    val hoyFecha = LocalDate.parse(hoy)
    val desde = hoyFecha.minus(DatePeriod(months = MESES_DE_LA_VENTANA_DE_SEGUROS)).toString()
    val cuentaPorId = cuentas.associateBy { it.id }
    val vivos = eventos.filter { it.id !in anulados }

    val delCredito = segurosDeLosCreditos(creditos, vivos, diaDe)
    val creditosConSeguro = creditos.filter { c -> delCredito.any { it.creditoId == c.account.id } }.map { c ->
        CreditoConSeguro(
            id = c.account.id,
            nombre = c.account.name,
            seguro = c.terms?.insuranceMonthly ?: 0L,
            banco = palabrasDe(c.terms?.bank.orEmpty()).filterNot { it in PALABRAS_QUE_NO_NOMBRAN_UN_BANCO },
        )
    }

    // Las reglas de seguros: su nombre comparable → (nombre, cada cuántos meses).
    val reglasDeSeguro = reglas.filter { it.type == TransactionType.EXPENSE }
        .filter { esCategoriaDeSeguros(it.category) || lecturaDelTexto(it.name)?.concluyente == true }
        .associate { claveComparableDeNombre(it.name) to (it.name to (it.frecuenciaEfectiva.meses ?: 1)) }
    // Las suscripciones vivas que son seguros: su clave de comercio → (nombre, cada cuántos meses).
    val suscripcionesDeSeguro = suscripciones.filter { it.status != SubStatus.DISMISSED }
        .filter { lecturaDelTexto(it.displayName)?.concluyente == true || lecturaDelTexto(it.merchantKey.replace('_', ' '))?.concluyente == true }
        .associate { it.merchantKey to (it.displayName to if (it.periodicidad == PeriodicidadDeCobro.ANUAL) 12 else 1) }

    val candidatos = vivos.asSequence()
        .filter(::entraAlDetector)
        .filter { diaDe(it.timestamp) in desde..hoy }
        .mapNotNull { e ->
            val texto = listOfNotNull(e.description, e.merchant).joinToString(" ")
            val comercio = claveDeComercio(e)
            val regla = reglasDeSeguro[claveComparableDeNombre(e.description)]
            val suscripcion = comercio?.let { suscripcionesDeSeguro[it] }
            val porCategoria = if (esCategoriaDeSeguros(e.category)) {
                LecturaDeSeguro(true, tipoDeLasPalabras(palabrasDe("$texto ${e.category}")))
            } else null
            val lectura = when {
                regla != null || suscripcion != null ->
                    LecturaDeSeguro(true, tipoDeLasPalabras(palabrasDe(listOfNotNull(texto, regla?.first, suscripcion?.first).joinToString(" "))))
                porCategoria != null -> porCategoria
                else -> lecturaDelTexto(texto)
            } ?: return@mapNotNull null
            val meses = when {
                diceQueEsAnual(texto) -> 12
                regla != null -> regla.second
                suscripcion != null -> suscripcion.second
                else -> null
            }
            Candidato(e, lectura, regla?.first ?: suscripcion?.first, meses)
        }
        .toList()

    val porCobros = candidatos
        // La moneda va en la clave cuando no es pesos: un comercio cobrado en COP y en USD son dos grupos.
        .groupBy {
            val moneda = it.evento.currency.takeIf { m -> m != "COP" }?.let { m -> ":$m" }.orEmpty()
            "${it.evento.accountId}:${(claveDeComercio(it.evento) ?: it.evento.id).take(120)}$moneda"
        }
        .map { (clave, delGrupo) ->
            val c = clave.take(MAX_CLAVE_DE_SEGURO)
            seguroDelGrupo(c, delGrupo, cuentaPorId, hoyFecha, diaDe, cadaCuanto[c], creditosConSeguro)
        }

    return delCredito + porCobros
}

/**
 * **Lo que pagas en seguros**: [segurosDetectados] con lo que dijo el dueño. Lo descartado («No es un
 * seguro», «Es el mismo») sale de todo; lo confirmado («Sí», «Es otro») cuenta como concluyente. El total
 * suma, en pesos, lo que cuenta y sigue cobrándose; lo pagado una vez va aparte ([SegurosQuePagas.enPagosDeUnaVez]),
 * lo de otra moneda también ([SegurosQuePagas.otrasMonedas]), y lo que dejó de cobrarse se ve, marcado, sin sumar.
 */
fun losSegurosQuePagas(
    eventos: List<FinancialEvent>,
    cuentas: List<Account>,
    creditos: List<CreditSummary>,
    reglas: List<RecurringRule>,
    suscripciones: List<Subscription>,
    hoy: String,
    diaDe: (Long) -> String,
    respuestas: RespuestasDelDueno = RespuestasDelDueno(),
    anulados: Set<String> = emptySet(),
): SegurosQuePagas = conLoQueDijoElDueno(
    segurosDetectados(eventos, cuentas, creditos, reglas, suscripciones, hoy, diaDe, anulados, respuestas.cadaCuanto),
    respuestas,
)

/**
 * [detectados] (con el «cada cuánto» ya aplicado) con lo demás que dijo el dueño, y los totales. Aparte
 * para que el server, que ya detectó para validar una respuesta, no vuelva a detectar.
 */
fun conLoQueDijoElDueno(detectados: List<SeguroDetectado>, respuestas: RespuestasDelDueno): SegurosQuePagas {
    val todos = detectados
        .filter { it.clave !in respuestas.descartados }
        .map { if (it.clave in respuestas.confirmados) it.copy(concluyente = true, confirmado = true) else it }
    val (cuentan, porConfirmar) = todos.partition { it.concluyente }
    val vivos = cuentan.filter { it.sigueCobrandose && !it.pagadoUnaVez }
    val enPesos = vivos.filter { it.moneda == "COP" }
    return SegurosQuePagas(
        seguros = cuentan.sortedWith(
            compareBy<SeguroDetectado> { it.tipo.ordinal }.thenBy { !it.sigueCobrandose }.thenByDescending { it.alMes },
        ),
        porConfirmar = porConfirmar.sortedByDescending { it.ultimoCobro ?: "" },
        alMes = enPesos.sumOf { it.alMes },
        alAno = enPesos.sumOf { s -> if (s.mesesEntreCobros > 1) (s.montoDelCiclo ?: 0L) * (12 / s.mesesEntreCobros) else s.alMes * 12 },
        conAnuales = enPesos.any { it.mesesEntreCobros > 1 },
        enPagosDeUnaVez = cuentan.filter { it.pagadoUnaVez && it.moneda == "COP" }.sumOf { it.pagadoSinRitmo ?: it.montoDelCiclo ?: 0L },
        otrasMonedas = vivos.filter { it.moneda != "COP" }.groupBy { it.moneda }.mapValues { (_, l) -> l.sumOf { it.alMes } },
        meInteresa = respuestas.meInteresa,
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
            tipo = TipoDeSeguro.DEL_CREDITO,
            nombre = "Seguro del crédito · ${c.account.name}",
            cuentaId = c.account.id,
            cuentaNombre = c.account.name,
            alMes = seguro,
            ultimoCobro = ultimaCuota?.let { diaDe(it.timestamp) },
            sigueCobrandose = true,
            dentroDeLaCuota = true,
            creditoId = c.account.id,
        )
    }

/**
 * ¿Los meses con cobro ([meses], «2026-09») dicen «todos los meses»? Dos o más, y entre dos seguidos a lo
 * sumo [HUECO_MAXIMO_DE_UN_MENSUAL_EN_MESES]. Marzo y octubre no; un trimestral tampoco; la renovación de
 * un anual que llegó unos días antes (noviembre y noviembre) tampoco.
 */
internal fun pareceMensual(meses: Collection<String>): Boolean {
    val indices = meses.mapNotNull { m ->
        val anio = m.take(4).toIntOrNull()
        val mes = m.drop(5).take(2).toIntOrNull()
        if (anio == null || mes == null) null else anio * 12 + (mes - 1)
    }.distinct().sorted()
    if (indices.size < 2) return false
    return indices.zipWithNext().all { (a, b) -> b - a <= HUECO_MAXIMO_DE_UN_MENSUAL_EN_MESES }
}

private fun seguroDelGrupo(
    clave: String,
    delGrupo: List<Candidato>,
    cuentaPorId: Map<String, Account>,
    hoy: LocalDate,
    diaDe: (Long) -> String,
    respuesta: Int?,
    creditosConSeguro: List<CreditoConSeguro>,
): SeguroDetectado {
    val ordenados = delGrupo.sortedByDescending { it.evento.timestamp }
    val ultimo = ordenados.first()
    val cuenta = cuentaPorId[ultimo.evento.accountId]
    val esTarjeta = cuenta?.type == AccountType.CREDIT_CARD
    val leido = ordenados.firstNotNullOfOrNull { it.lectura.tipo }
    val textos = ordenados.map { listOfNotNull(it.evento.description, it.evento.merchant).joinToString(" ") }
    val tipo = when {
        esTarjeta && (leido == null || leido == TipoDeSeguro.DE_TARJETA ||
            (leido == TipoDeSeguro.VIDA && textos.any { "deudor" in palabrasDe(it) })) -> TipoDeSeguro.DE_TARJETA
        leido == TipoDeSeguro.DE_TARJETA && !esTarjeta -> TipoDeSeguro.OTRO
        else -> leido ?: TipoDeSeguro.OTRO
    }
    val porMes = ordenados.groupBy { diaDe(it.evento.timestamp).take(7) }.mapValues { (_, l) -> l.sumOf { montoContado(it.evento) } }
    val fechaUltima = diaDe(ultimo.evento.timestamp)
    val delUltimoMes = porMes[fechaUltima.take(7)] ?: montoContado(ultimo.evento)
    // Cada cuánto: lo que dijo el dueño; si no, una señal (SOAT, «anual», su regla o su suscripción); si no,
    // dos meses distintos = mensual. Un solo cobro sin señal no se sabe: «pagado una vez».
    val meses: Int? = respuesta
        ?: ordenados.mapNotNull { it.meses }.maxOrNull()
        ?: if (pareceMensual(porMes.keys)) 1 else null
    val pagadoUnaVez = meses == null
    val montoDelCiclo = if (pagadoUnaVez || (meses ?: 1) > 1) delUltimoMes else null
    // Lo pagado sin saber cada cuánto: todo lo de la ventana (va aparte, en «pagos de una vez»).
    val sinRitmo = if (pagadoUnaVez) porMes.values.sum() else null
    val alMes = when {
        pagadoUnaVez -> 0L
        meses!! > 1 -> (delUltimoMes + meses / 2) / meses
        else -> (porMes.values.sum() + porMes.size / 2) / porMes.size.coerceAtLeast(1)
    }
    val dias = (hoy.toEpochDays() - LocalDate.parse(fechaUltima).toEpochDays()).toInt()
    val sigue = when {
        pagadoUnaVez -> true
        meses!! > 1 -> dias <= meses * 31 + DIAS_PARA_SEGUIR_COBRANDOSE
        else -> dias <= DIAS_PARA_SEGUIR_COBRANDOSE
    }
    val duplicadoDe = if (ultimo.evento.currency == "COP") {
        val palabras = textos.flatMap(::palabrasDe)
        creditosConSeguro.firstOrNull { c ->
            val parecido = kotlin.math.abs(montoContado(ultimo.evento) - c.seguro) * 100 <= c.seguro * TOLERANCIA_DEL_DUPLICADO_PORCIENTO
            val delMismoBanco = c.banco.isNotEmpty() && c.banco.all { it in palabras }
            // En una tarjeta, «deudor» es el vida deudor de la propia tarjeta, no el del crédito: ahí solo
            // cuenta el banco (o incendio, terremoto, hipoteca).
            val porTexto = if (esTarjeta) hablaDelSeguroDeUnCredito(palabras.filter { it != "deudor" }) else hablaDelSeguroDeUnCredito(palabras)
            parecido && (delMismoBanco || porTexto)
        }?.nombre
    } else null
    val concluyente = ordenados.any { it.lectura.concluyente } && duplicadoDe == null
    val nombreDeRegla = ordenados.firstNotNullOfOrNull { it.nombreDeRegla }
    return SeguroDetectado(
        clave = clave,
        tipo = tipo,
        nombre = nombreDeRegla ?: nombreDelSeguro(tipo, cuenta?.name, textos.first(), ultimo.evento, concluyente || duplicadoDe != null),
        cuentaId = ultimo.evento.accountId,
        cuentaNombre = cuenta?.name.orEmpty(),
        moneda = ultimo.evento.currency,
        alMes = alMes,
        mesesEntreCobros = meses ?: 0,
        montoDelCiclo = montoDelCiclo,
        pagadoUnaVez = pagadoUnaVez,
        pagadoSinRitmo = sinRitmo,
        ultimoCobro = fechaUltima,
        sigueCobrandose = sigue,
        concluyente = concluyente,
        posibleDuplicadoDe = duplicadoDe,
        cobros = ordenados.map { CobroDelSeguro(it.evento.id, diaDe(it.evento.timestamp), montoContado(it.evento), it.evento.description) },
    )
}

/** Las palabras de relleno al principio de una descripción de banco: «PAGO PSE SEGUROS BOLIVAR» → «Seguros Bolivar». */
private val RELLENO_AL_PRINCIPIO = setOf("pago", "pse", "compra", "debito", "cargo", "cobro", "pag")

/** El nombre legible de un seguro por su tipo. Lo que no se sabe leer se nombra por su comercio. */
private fun nombreDelSeguro(tipo: TipoDeSeguro, cuenta: String?, texto: String, evento: FinancialEvent, concluyente: Boolean): String {
    val crudo = normalizeMerchant(evento.merchant?.takeIf { it.isNotBlank() } ?: evento.description)?.displayName
        ?: evento.description.trim()
    val palabras = crudo.split(Regex("\\s+"))
    val sinRelleno = palabras.dropWhile { normalizarParaBuscar(it) in RELLENO_AL_PRINCIPIO }.joinToString(" ")
    val delComercio = sinRelleno.ifBlank { crudo }
    if (!concluyente) return delComercio
    val p = palabrasDe(texto)
    return when (tipo) {
        TipoDeSeguro.DEL_CREDITO -> "Seguro del crédito · ${cuenta.orEmpty()}"
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
    TipoDeSeguro.DEL_CREDITO -> "Dentro de tus créditos"
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
