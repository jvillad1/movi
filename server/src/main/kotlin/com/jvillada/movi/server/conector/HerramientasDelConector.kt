package com.jvillada.movi.server.conector

import com.jvillada.movi.server.ai.datosDeLasSugeridas
import com.jvillada.movi.server.ai.filasDe
import com.jvillada.movi.server.apartados.tuPlataConApartados
import com.jvillada.movi.server.balance.hayMovimientosEnOtraMoneda
import com.jvillada.movi.server.compartir.sinNumerosCompletos
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.routes.cajaProyectadaDe
import com.jvillada.movi.server.routes.creditosDe
import com.jvillada.movi.server.routes.ocurrenciasDelPeriodo
import com.jvillada.movi.server.routes.proximosPagos
import com.jvillada.movi.server.routes.tarjetasDe
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.cuotaCambia
import com.jvillada.movi.shared.model.cuotaDeEsteMes
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.esCuentaDeDeuda
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuota
import com.jvillada.movi.shared.model.mesDeLaUltimaCuota
import com.jvillada.movi.shared.model.nombreDelPeriodo
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.pagosPendientes
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.pesosDelCierre
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.shared.model.textoDeLaCuotaQueCambia
import com.jvillada.movi.shared.model.valorEnPesosDe
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.server.time.AppClock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * # Vuelta 135 · Lo que Claude puede preguntarle a Movi
 *
 * Siete herramientas, **todas de solo lectura** (`readOnlyHint: true`): Claude puede leer, nunca anotar, borrar ni
 * mover plata. No hay ni una que escriba, y el único permiso que existe es `movi:leer`.
 *
 * **Las mismas cifras que la app**, porque son las mismas funciones: «Te quedan» y el período salen de
 * `datosDeLasSugeridas` (lo que contesta «¿Cómo voy?» sin el modelo, que a su vez usa la `disponibleDelPeriodo` del
 * héroe de Inicio); la lista de Pagos es `checklistDelPeriodo` sobre `/upcoming` y `/occurrences`; las cuentas son
 * `cuentasConSaldo` + `patrimonioDe` (Inicio, Patrimonio); los créditos y las tarjetas son lo de `GET /api/credits`
 * y `/api/cards` con `planDelCredito` y el extracto; la caja es `cajaProyectadaDe` (`cajaHastaQueTePaguen`, la de
 * Pagos); los movimientos, las filas de la herramienta de Movi AI (`filasDe`: sin anulados ni «Por confirmar»).
 * Acá no se suma nada que no sume también una pantalla.
 *
 * **Lo que viene del banco es dato, no instrucción.** Los nombres de comercio, las descripciones y los nombres de
 * cuenta viajan como campos de un JSON, recortados ([textoSeguro]): sin caracteres de control, cortos, y con todo
 * número de cinco dígitos o más reducido a sus últimos cuatro (`sinNumerosCompletos`, la de la página compartida).
 * Nunca el cuerpo de un SMS, una notificación o un correo (`rawPayload` no sale), ni ids de nadie más: el usuario
 * sale del token.
 */
internal data class DefinicionDeHerramienta(
    val nombre: String,
    val titulo: String,
    val descripcion: String,
    val entrada: JsonObject,
)

/** Lo que contesta una herramienta: una línea para leer y los datos. */
internal data class RespuestaDeHerramienta(val resumen: String, val datos: JsonElement, val esError: Boolean = false)

/** Un argumento que no se entiende. La herramienta contesta el porqué y cómo arreglarlo (`isError: true`). */
internal class ArgumentoInvalido(mensaje: String) : Exception(mensaje)

const val COMO_VOY = "como_voy"
const val CUENTAS = "cuentas"
const val MOVIMIENTOS = "movimientos"
const val PAGOS_DEL_PERIODO = "pagos_del_periodo"
const val EN_QUE_SE_VA = "en_que_se_va"
const val CREDITOS = "creditos"
const val CAJA_HASTA_QUE_TE_PAGUEN = "caja_hasta_que_te_paguen"

/** Lo más largo que sale un texto libre (un comercio, una descripción, un nombre). */
internal const val LARGO_MAXIMO_DE_UN_TEXTO = 120

/**
 * **Lo más que pesa el JSON de una respuesta**, en caracteres. Claude acepta ~150.000 por resultado en la web y
 * 25.000 tokens en Claude Code; el JSON va dos veces (en el texto y en `structuredContent`), así que esto deja
 * margen en los dos.
 */
internal const val TOPE_DE_LA_RESPUESTA = 25_000

internal const val MOVIMIENTOS_POR_PAGINA = 50
internal const val MOVIMIENTOS_POR_PAGINA_MAXIMO = 100
internal const val DIAS_POR_DEFECTO = 30
internal const val DIAS_MAXIMOS = 366

private val SIN_ARGUMENTOS = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
    put("additionalProperties", false)
}

internal val HERRAMIENTAS_DEL_CONECTOR: List<DefinicionDeHerramienta> = listOf(
    DefinicionDeHerramienta(
        COMO_VOY, "Cómo voy este período",
        "Las cifras del período en curso como las muestra el Inicio de Movi: «Te quedan» y cuánto da por día, lo que " +
            "entró y salió, lo que falta por pagar, Tu plata, lo apartado, el ahorro del período y el patrimonio. En pesos (COP).",
        SIN_ARGUMENTOS,
    ),
    DefinicionDeHerramienta(
        CUENTAS, "Cuentas y saldos",
        "Las cuentas del usuario con su saldo de hoy (sin movimientos anulados): plata, inversiones, bienes y deudas " +
            "(tarjetas y préstamos, en positivo lo que se debe), con los totales del patrimonio.",
        SIN_ARGUMENTOS,
    ),
    DefinicionDeHerramienta(
        MOVIMIENTOS, "Movimientos",
        "Los movimientos de un rango de fechas (o de los últimos N días; por defecto 30), del más nuevo al más viejo, " +
            "sin anulados ni los que esperan confirmación. Filtros opcionales por categoría, cuenta, texto y tipo. " +
            "Paginado: si hay más, la respuesta trae `siguiente` para pedir la página que sigue.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("desde") { put("type", "string"); put("description", "Primer día, AAAA-MM-DD.") }
                putJsonObject("hasta") { put("type", "string"); put("description", "Último día incluido, AAAA-MM-DD. Por defecto, hoy.") }
                putJsonObject("ultimos_dias") {
                    put("type", "integer"); put("minimum", 1); put("maximum", DIAS_MAXIMOS)
                    put("description", "En vez de desde/hasta: los últimos N días, hoy incluido.")
                }
                putJsonObject("categoria") { put("type", "string"); put("description", "Nombre exacto de la categoría (sin importar mayúsculas ni tildes).") }
                putJsonObject("cuenta") { put("type", "string"); put("description", "Parte del nombre de la cuenta.") }
                putJsonObject("texto") { put("type", "string"); put("description", "Parte de la descripción o del comercio.") }
                putJsonObject("tipo") { put("type", "string"); putJsonArray("enum") { add("gasto"); add("ingreso") } }
                putJsonObject("limite") {
                    put("type", "integer"); put("minimum", 1); put("maximum", MOVIMIENTOS_POR_PAGINA_MAXIMO)
                    put("description", "Cuántos por página (por defecto $MOVIMIENTOS_POR_PAGINA).")
                }
                putJsonObject("siguiente") { put("type", "string"); put("description", "El `siguiente` de la respuesta anterior.") }
            }
            put("additionalProperties", false)
        },
    ),
    DefinicionDeHerramienta(
        PAGOS_DEL_PERIODO, "Pagos del período",
        "La lista de Pagos del período en curso: cada factura, cuota o ingreso esperado con su monto, vencimiento y si " +
            "ya se pagó, y el total que falta por pagar. Una cuota que cambia cada mes viene marcada como estimada («unos \$X»).",
        SIN_ARGUMENTOS,
    ),
    DefinicionDeHerramienta(
        EN_QUE_SE_VA, "En qué se va la plata",
        "El gasto del período en curso por categoría, comparado con el período pasado a la misma altura (cuando Movi " +
            "lo tiene completo), y los gastos pequeños que se repiten.",
        SIN_ARGUMENTOS,
    ),
    DefinicionDeHerramienta(
        CREDITOS, "Créditos y tarjetas",
        "Los créditos (saldo, tasa, cuota de este mes, cuántas cuotas faltan y el mes de la última) y las tarjetas de " +
            "crédito (deuda, cupo, corte, fecha de pago, pago mínimo, tasa y el extracto del último corte).",
        SIN_ARGUMENTOS,
    ),
    DefinicionDeHerramienta(
        CAJA_HASTA_QUE_TE_PAGUEN, "Caja hasta que te paguen",
        "La caja proyectada día a día hasta el próximo salario (o el arranque del período siguiente, o 30 días): lo que " +
            "habrá en las cuentas, lo libre (sin el ahorro ni lo apartado), el día más justo y si algún día queda en negativo, " +
            "con los pagos y el gasto diario que supone.",
        SIN_ARGUMENTOS,
    ),
)

internal val NOMBRES_DE_LAS_HERRAMIENTAS: Set<String> = HERRAMIENTAS_DEL_CONECTOR.map { it.nombre }.toSet()

internal val jsonDelConector = Json { encodeDefaults = true; explicitNulls = false }

/**
 * **Los números completos fuera, los montos y las fechas no** (M5 de la revisión, con la regla de N4 de la
 * re-revisión). Todo número de 5+ dígitos —con o sin puntos, comas, espacios, guiones o barras— sale reducido a
 * sus últimos cuatro, salvo:
 *
 * - **un monto con marca de moneda** («$», «COP», «USD», «US$») y forma estricta: grupos de miles de a tres
 *   («$1.500.000», «COP 27.500», «USD 1,234.56») o hasta 7 dígitos seguidos («$27500»), con hasta 2 decimales;
 * - **un monto sin marca** solo con grupos de miles exactos y **hasta 7 dígitos enteros** («1.500.000», «25,000»): una
 *   cédula o una cuenta con puntos (8 o más dígitos) se recorta;
 * - **una fecha** con mes y día válidos («2026-10-01», «01/10/2026», «1/10/26»).
 *
 * Una variante para el conector y no un cambio a `sinNumerosCompletos`: la página compartida no muestra textos
 * libres con montos, y su prueba fija lo que hace hoy. El monto de cada movimiento ya viaja aparte, en su campo.
 */
internal fun sinNumerosCompletosSalvoMontosYFechas(texto: String): String =
    NUMEROS_MONTOS_Y_FECHAS.replace(texto) { m ->
        when {
            m.groups["fecha"] != null || m.groups["monto"] != null || m.groups["montoSinMarca"] != null -> m.value
            else -> {
                val digitos = m.value.filter(Char::isDigit)
                if (digitos.length >= 5) "••" + digitos.takeLast(4) else m.value
            }
        }
    }

/** Detrás de un número no puede seguir otro dígito ni un separador con un dígito («1.500.000.» sí termina). */
private const val FIN_DEL_NUMERO = """(?!\d|[.,]\d)"""

private val NUMEROS_MONTOS_Y_FECHAS = Regex(
    """(?<fecha>(?<![\d/.,-])(?:(?:19|20)\d{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])|(?:0?[1-9]|[12]\d|3[01])/(?:0?[1-9]|1[0-2])/(?:(?:19|20)\d{2}|\d{2}))(?![\d/.,-]))""" +
        """|(?<monto>(?:US\$|\$|COP|USD)\s?(?:\d{1,3}(?:[.,]\d{3})+|\d{1,7})(?:[.,]\d{1,2})?""" + FIN_DEL_NUMERO + ")" +
        """|(?<montoSinMarca>(?<![\d.,])(?:\d[.,]\d{3}[.,]\d{3}|\d{1,3}[.,]\d{3})(?:[.,]\d{1,2})?""" + FIN_DEL_NUMERO + ")" +
        // Lo demás: dígitos con a lo sumo un separador entre cada uno («1.020.304.050», «4513 2200 1234 5678»,
        // «12/34/5678»); «Cuotas 1, 2, 3, 4, 5» no se junta en un número.
        """|\d(?:[ .,/\-]?\d)*""",
)

/** **Un texto de afuera, listo para salir**: sin control, una línea, corto y sin números completos. */
internal fun textoSeguro(crudo: String?, max: Int = LARGO_MAXIMO_DE_UN_TEXTO): String =
    sinNumerosCompletosSalvoMontosYFechas(
        crudo.orEmpty()
            .filter { it.category != CharCategory.FORMAT }
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim(),
    ).let { if (it.length > max) it.take(max - 1).trimEnd() + "…" else it }

/** Ejecuta [nombre] para [uid]. El usuario sale del token: ninguna herramienta lo recibe por argumento. */
internal suspend fun ejecutarHerramientaDelConector(
    uid: String,
    nombre: String,
    argumentos: JsonObject,
    hoy: LocalDate = AppClock.today(),
): RespuestaDeHerramienta = try {
    when (nombre) {
        COMO_VOY -> comoVoy(uid, hoy)
        CUENTAS -> cuentas(uid)
        MOVIMIENTOS -> movimientos(uid, argumentos, hoy)
        PAGOS_DEL_PERIODO -> pagosDelPeriodo(uid, hoy)
        EN_QUE_SE_VA -> enQueSeVa(uid, hoy)
        CREDITOS -> creditos(uid, hoy)
        CAJA_HASTA_QUE_TE_PAGUEN -> caja(uid, hoy)
        else -> throw ArgumentoInvalido("No existe la herramienta «$nombre».")
    }
} catch (e: ArgumentoInvalido) {
    RespuestaDeHerramienta(e.message.orEmpty(), buildJsonObject { put("error", e.message.orEmpty()) }, esError = true)
}

private fun <T> datos(serializador: KSerializer<T>, valor: T): JsonElement = jsonDelConector.encodeToJsonElement(serializador, valor)

private const val MEDIO_DIA_MS = 12L * 60 * 60 * 1000

/** La tasa solo si hace falta, como el resumen del Inicio: sin un movimiento en otra moneda no se pide. */
private suspend fun tasaSiHaceFalta(uid: String): Double =
    if (dbQuery { hayMovimientosEnOtraMoneda(uid) }) FxRateService.usdToCop() else 0.0

private suspend fun tuPlata(uid: String) = run {
    val tasa = tasaSiHaceFalta(uid)
    dbQuery {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
        tuPlataConApartados(uid, anulados, tasa)
    }
}

// ── como_voy ───────────────────────────────────────────────────────────────────────────────────

@Serializable
internal data class PeriodoDelConector(val nombre: String, val desde: String, val hasta: String, val diasQueQuedan: Int? = null)

@Serializable
internal data class TeQuedanDelConector(
    /** «Te quedan» o «Te pasaste por», como lo dice el héroe de Inicio. */
    val rotulo: String,
    /** Lo que queda del disponible; negativo = te pasaste. */
    val monto: Long,
    /** Lo que da por día lo que queda, hoy incluido. `null` sin nada que repartir. */
    val porDia: Long? = null,
)

@Serializable
internal data class PatrimonioDelConector(
    val neto: Long,
    val loQueTienes: Long,
    val deudas: Long,
    val bienes: Long,
    val condicionado: Long,
    val apartado: Long,
)

@Serializable
internal data class ComoVoyDelConector(
    val moneda: String = "COP",
    val periodo: PeriodoDelConector,
    /** `null` = Movi no tiene ingresos ni plata anotados con qué decirlo (la app tampoco lo dice). */
    val teQuedan: TeQuedanDelConector? = null,
    val entro: Long,
    val salio: Long,
    val faltaPorPagar: Long,
    val pagosPendientes: Int,
    val tuPlata: Long,
    val apartado: Long,
    val ahorroDelPeriodo: Long,
    val patrimonio: PatrimonioDelConector,
)

private suspend fun comoVoy(uid: String, hoy: LocalDate): RespuestaDeHerramienta {
    val d = datosDeLasSugeridas(uid, PreguntaSugerida.COMO_VOY, hoy)
    val ajustes = ajustesDePeriodoDe(uid)
    val periodo = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)
    val ventana = ventanaDe(periodo, ajustes)
    val p = tuPlata(uid).patrimonio
    val disponible = d.disponible
    val alcanza = d.alcanza
    val r = ComoVoyDelConector(
        periodo = PeriodoDelConector(
            nombre = nombreDelPeriodo(periodo, ajustes),
            desde = epochMillisToAppDate(ventana.first).toString(),
            hasta = epochMillisToAppDate(ventana.last).toString(),
            diasQueQuedan = disponible?.diasQueQuedan,
        ),
        teQuedan = alcanza?.let { TeQuedanDelConector(it.rotulo, it.cifra, disponible?.porDiaParaLoQueQueda) },
        entro = d.entro,
        salio = d.salio,
        faltaPorPagar = faltaPorPagar(d.pagos),
        pagosPendientes = pagosPendientes(d.pagos).size,
        tuPlata = p.tuPlata,
        apartado = p.apartado,
        ahorroDelPeriodo = disponible?.ahorroDelPeriodo ?: 0L,
        patrimonio = PatrimonioDelConector(p.neto, p.loQueTienes, p.deudas, p.bienes, p.condicionado, p.apartado),
    )
    val resumen = r.teQuedan?.let { "${it.rotulo} ${pesosDelCierre(kotlin.math.abs(it.monto))} en ${r.periodo.nombre}." }
        ?: "Movi no tiene ingresos ni plata anotados para decir cuánto queda en ${r.periodo.nombre}."
    return RespuestaDeHerramienta(resumen, datos(ComoVoyDelConector.serializer(), r))
}

// ── cuentas ────────────────────────────────────────────────────────────────────────────────────

@Serializable
internal data class CuentaDelConector(
    val id: String,
    val nombre: String,
    /** `ahorros`, `corriente`, `efectivo`, `inversion`, `tarjeta`, `prestamo`. */
    val tipo: String,
    /** `plata`, `inversion`, `bien` o `deuda`. */
    val grupo: String,
    val moneda: String,
    /** En la moneda de la cuenta. En una deuda, positivo = lo que se debe. */
    val saldo: Long,
    /** En pesos (los dólares a la TRM del día, como la lista de Cuentas). */
    val enPesos: Long,
    val condicionadaA: String? = null,
)

@Serializable
internal data class CuentasDelConector(val moneda: String = "COP", val cuentas: List<CuentaDelConector>, val patrimonio: PatrimonioDelConector, val tuPlata: Long)

private fun nombreDelTipo(t: AccountType): String = when (t) {
    AccountType.SAVINGS -> "ahorros"
    AccountType.CHECKING -> "corriente"
    AccountType.CASH -> "efectivo"
    AccountType.INVESTMENT -> "inversion"
    AccountType.CREDIT_CARD -> "tarjeta"
    AccountType.LOAN -> "prestamo"
}

private suspend fun cuentas(uid: String): RespuestaDeHerramienta {
    val t = tuPlata(uid)
    val p = t.patrimonio
    val lista = t.cuentas.map { a ->
        CuentaDelConector(
            id = a.id,
            nombre = textoSeguro(a.name),
            tipo = nombreDelTipo(a.type),
            grupo = when {
                a.esBien -> "bien"
                esCuentaDeDeuda(a.type) -> "deuda"
                a.type == AccountType.INVESTMENT -> "inversion"
                else -> "plata"
            },
            moneda = a.currency,
            saldo = a.balancesByCurrency[a.currency] ?: a.balance,
            enPesos = valorEnPesosDe(a),
            condicionadaA = a.condicionadaA?.takeIf { it.isNotBlank() }?.let { textoSeguro(it) },
        )
    }
    val r = CuentasDelConector(
        cuentas = lista,
        patrimonio = PatrimonioDelConector(p.neto, p.loQueTienes, p.deudas, p.bienes, p.condicionado, p.apartado),
        tuPlata = p.tuPlata,
    )
    return RespuestaDeHerramienta("${lista.size} cuentas · Tu plata ${pesosDelCierre(p.tuPlata)} · patrimonio ${pesosDelCierre(p.neto)}.", datos(CuentasDelConector.serializer(), r))
}

// ── movimientos ────────────────────────────────────────────────────────────────────────────────

@Serializable
internal data class MovimientoDelConector(
    val fecha: String,
    val descripcion: String,
    val categoria: String,
    /** Lo que salió o entró de la cuenta, en su moneda. */
    val monto: Long,
    val moneda: String,
    /** `gasto` o `ingreso`. */
    val tipo: String,
    val cuenta: String,
    /** ¿Suma en lo que entró o salió del período? Un pago de tarjeta o un traspaso, no. */
    val cuentaEnElPeriodo: Boolean,
    val porQueNoCuenta: String? = null,
    /** De un gasto repartido con otras personas, lo que cuenta como tuyo. */
    val tuParte: Long? = null,
)

@Serializable
internal data class MovimientosDelConector(
    val desde: String,
    val hasta: String,
    /** Cuántos cumplen el filtro en todo el rango. */
    val total: Int,
    val movimientos: List<MovimientoDelConector>,
    /** Para pedir la página que sigue; `null` = no hay más. */
    val siguiente: String? = null,
)

private fun fechaDe(argumentos: JsonObject, campo: String): LocalDate? {
    val valor = (argumentos[campo] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        LocalDate.parse(valor)
    } catch (_: DateTimeParseException) {
        throw ArgumentoInvalido("No entiendo la fecha «${textoSeguro(valor, 20)}» en `$campo`: usa AAAA-MM-DD, por ejemplo 2026-10-01.")
    }
}

private fun textoDe(argumentos: JsonObject, campo: String): String? =
    (argumentos[campo] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.take(100)

private fun enteroDe(argumentos: JsonObject, campo: String, rango: IntRange): Int? {
    val crudo = argumentos[campo] ?: return null
    val n = (crudo as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
        ?: throw ArgumentoInvalido("`$campo` es un número entero.")
    if (n !in rango) throw ArgumentoInvalido("`$campo` va de ${rango.first} a ${rango.last}.")
    return n
}

private suspend fun movimientos(uid: String, argumentos: JsonObject, hoy: LocalDate): RespuestaDeHerramienta {
    val ultimos = enteroDe(argumentos, "ultimos_dias", 1..DIAS_MAXIMOS)
    val hasta = fechaDe(argumentos, "hasta") ?: hoy
    val desde = fechaDe(argumentos, "desde")
        ?: hasta.minusDays(((ultimos ?: DIAS_POR_DEFECTO) - 1).toLong())
    if (desde > hasta) throw ArgumentoInvalido("`desde` (${desde}) es después de `hasta` (${hasta}).")
    if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) >= DIAS_MAXIMOS) {
        throw ArgumentoInvalido("El rango es de $DIAS_MAXIMOS días como mucho: pide un rango más corto.")
    }
    val categoria = textoDe(argumentos, "categoria")
    val cuenta = textoDe(argumentos, "cuenta")
    val texto = textoDe(argumentos, "texto")
    val tipo = textoDe(argumentos, "tipo")?.lowercase()
    if (tipo != null && tipo != "gasto" && tipo != "ingreso") throw ArgumentoInvalido("`tipo` es «gasto» o «ingreso».")
    val limite = enteroDe(argumentos, "limite", 1..MOVIMIENTOS_POR_PAGINA_MAXIMO) ?: MOVIMIENTOS_POR_PAGINA
    val desdeLaFila = textoDe(argumentos, "siguiente")?.let {
        it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: throw ArgumentoInvalido("`siguiente` es el que trajo la respuesta anterior.")
    } ?: 0

    // M4 · `cuenta` y `texto` buscan sobre lo MISMO que se devuelve (ya recortado): buscar sobre el original dejaba
    // reconstruir un número completo dígito a dígito («0098», «00987»…) aunque la respuesta dijera «••5432».
    val todas = filasDe(uid, desde, hasta)
        .filter { categoria == null || normalizarParaBuscar(it.categoria) == normalizarParaBuscar(categoria) }
        .filter { cuenta == null || normalizarParaBuscar(cuenta) in normalizarParaBuscar(textoSeguro(it.cuenta, 60)) }
        .filter { texto == null || normalizarParaBuscar(texto) in normalizarParaBuscar(textoSeguro(it.nombre)) }
        .filter { tipo == null || (tipo == "ingreso") == it.esIngreso }

    var pagina = todas.drop(desdeLaFila).take(limite).map { f ->
        MovimientoDelConector(
            fecha = f.fecha,
            descripcion = textoSeguro(f.nombre),
            categoria = textoSeguro(f.categoria, 60),
            monto = f.monto,
            moneda = f.moneda,
            tipo = if (f.esIngreso) "ingreso" else "gasto",
            cuenta = textoSeguro(f.cuenta, 60),
            cuentaEnElPeriodo = f.cuentaEnElMes,
            porQueNoCuenta = f.noCuenta,
            tuParte = f.contado.takeIf { it != f.monto },
        )
    }
    fun armar(filas: List<MovimientoDelConector>) = MovimientosDelConector(
        desde = desde.toString(),
        hasta = hasta.toString(),
        total = todas.size,
        movimientos = filas,
        siguiente = (desdeLaFila + filas.size).takeIf { it < todas.size }?.toString(),
    )
    // El tope de tamaño: si la página no cabe, va más corta (y `siguiente` sigue desde donde se cortó).
    var r = armar(pagina)
    while (pagina.size > 1 && jsonDelConector.encodeToString(MovimientosDelConector.serializer(), r).length > TOPE_DE_LA_RESPUESTA) {
        pagina = pagina.take(pagina.size / 2)
        r = armar(pagina)
    }
    val resumen = "${todas.size} movimientos del $desde al $hasta" +
        (if (r.siguiente != null) "; van del ${desdeLaFila + 1} al ${desdeLaFila + pagina.size}." else ".")
    return RespuestaDeHerramienta(resumen, datos(MovimientosDelConector.serializer(), r))
}

// ── pagos_del_periodo ──────────────────────────────────────────────────────────────────────────

@Serializable
internal data class PagoDelConector(
    val nombre: String,
    /** `pago`, `ingreso` o `abono` (un abono extra planeado a una deuda: no es un fijo). */
    val tipo: String,
    val monto: Long,
    val moneda: String,
    /** «unos $1.250.000»: una cuota que cambia cada mes. `null` = el monto es el de siempre. */
    val montoEstimado: String? = null,
    val vence: String,
    val diasParaVencer: Int,
    /** `pagado`, `pendiente`, `vencido` o `pausado`. */
    val estado: String,
    val pagado: Long? = null,
    /** De una tarjeta: el mínimo del extracto. */
    val pagoMinimo: Long? = null,
    /** De una tarjeta sin mínimo: el monto es la deuda entera, no lo de este mes. */
    val montoEsLaDeuda: Boolean = false,
    val seCobraSola: Boolean = false,
)

@Serializable
internal data class PagosDelConector(
    val moneda: String = "COP",
    val periodo: String,
    val faltaPorPagar: Long,
    val pendientes: Int,
    val pagos: List<PagoDelConector>,
)

internal fun pagoDelConector(p: PagoDelPeriodo): PagoDelConector = PagoDelConector(
    nombre = textoSeguro(p.nombre, 80),
    tipo = when {
        p.esIngreso -> "ingreso"
        p.esAbono -> "abono"
        else -> "pago"
    },
    monto = p.monto,
    moneda = p.moneda,
    montoEstimado = p.cuotaQueCambia?.let { textoDeLaCuotaQueCambia(p.monto, it.anterior) },
    vence = p.vence,
    diasParaVencer = p.diasParaVencer,
    estado = when {
        p.pausada -> "pausado"
        p.hecho -> "pagado"
        p.vencido -> "vencido"
        else -> "pendiente"
    },
    pagado = p.pagadoParaMostrar,
    pagoMinimo = p.pagoMinimo,
    montoEsLaDeuda = p.montoEsSaldo,
    seCobraSola = p.seCobraSola,
)

private suspend fun pagosDelPeriodo(uid: String, hoy: LocalDate): RespuestaDeHerramienta {
    val ajustes = ajustesDePeriodoDe(uid)
    val periodo = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)
    val lista = checklistDelPeriodo(
        upcoming = proximosPagos(uid, hoy),
        ocurrencias = ocurrenciasDelPeriodo(uid, hoy),
        periodo = periodo,
        settings = ajustes,
    )
    val r = PagosDelConector(
        periodo = nombreDelPeriodo(periodo, ajustes),
        faltaPorPagar = faltaPorPagar(lista),
        pendientes = pagosPendientes(lista).size,
        pagos = lista.sortedBy { it.vence }.map(::pagoDelConector),
    )
    return RespuestaDeHerramienta(
        "Falta por pagar ${pesosDelCierre(r.faltaPorPagar)} en ${r.pendientes} pagos de ${r.periodo}.",
        datos(PagosDelConector.serializer(), r),
    )
}

// ── en_que_se_va ───────────────────────────────────────────────────────────────────────────────

@Serializable
internal data class CategoriaDelConector(
    val categoria: String,
    val gastado: Long,
    /** Lo gastado en esa categoría el período pasado, a la misma altura. `null` = sin comparación. */
    val periodoPasado: Long? = null,
    /** Una categoría de pagos fijos (una cuota, el arriendo): no es «gastar de más». */
    val esFija: Boolean = false,
)

@Serializable
internal data class PequenoDelConector(val nombre: String, val veces: Int, val total: Long)

@Serializable
internal data class EnQueSeVaDelConector(
    val moneda: String = "COP",
    val periodo: String,
    val total: Long,
    /** Lo del período pasado en los mismos días; `null` si Movi no lo tiene completo. */
    val totalDelPeriodoPasado: Long? = null,
    val diasComparados: Int? = null,
    val categorias: List<CategoriaDelConector>,
    val pequenosQueSuman: List<PequenoDelConector>,
)

private suspend fun enQueSeVa(uid: String, hoy: LocalDate): RespuestaDeHerramienta {
    val d = datosDeLasSugeridas(uid, PreguntaSugerida.EN_QUE_SE_VA, hoy)
    val ajustes = ajustesDePeriodoDe(uid)
    val periodo = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)
    val anterior = d.anterior
    val r = EnQueSeVaDelConector(
        periodo = nombreDelPeriodo(periodo, ajustes),
        total = d.salio,
        totalDelPeriodoPasado = anterior?.values?.sum(),
        diasComparados = d.diasComparados.takeIf { anterior != null },
        categorias = d.gastoPorCategoria.entries
            .sortedByDescending { it.value }
            .map { (c, v) -> CategoriaDelConector(textoSeguro(c, 60), v, anterior?.let { it[c] ?: 0L }, c in d.fijas) },
        pequenosQueSuman = d.pequenos.map { PequenoDelConector(textoSeguro(it.nombre, 80), it.veces, it.total) },
    )
    return RespuestaDeHerramienta(
        "Salió ${pesosDelCierre(r.total)} en ${r.periodo}, en ${r.categorias.size} categorías.",
        datos(EnQueSeVaDelConector.serializer(), r),
    )
}

// ── creditos ───────────────────────────────────────────────────────────────────────────────────

@Serializable
internal data class CreditoDelConector(
    val nombre: String,
    val banco: String? = null,
    val moneda: String,
    /** Lo que se debe hoy, en pesos. Negativo = Movi no tiene el saldo de este crédito. */
    val debes: Long,
    val tasaEa: Double? = null,
    val sinIntereses: Boolean = false,
    val cuota: Long? = null,
    /** «unos $X»: la cuota cambia cada mes y esta es la estimación. */
    val cuotaEstimada: String? = null,
    val diaDePago: Int? = null,
    val plazoMeses: Int? = null,
    val cuotasQueFaltan: Int? = null,
    /** AAAA-MM de la última cuota, contada desde la primera sin pagar. */
    val ultimaCuota: String? = null,
    val interesDelMes: Long? = null,
    /** Una tercera persona la paga (Movi no la cuenta como gasto tuyo). */
    val laPagaOtro: Boolean = false,
    val descuentoDeNomina: Boolean = false,
)

@Serializable
internal data class ExtractoDelConector(
    val corte: String,
    val fechaDePago: String,
    val facturado: Long,
    val despuesDelCorte: Long,
    val minimo: Long? = null,
    val pagadoDesdeElCorte: Long,
    val moneda: String,
    val puedeIncluirDiferidos: Boolean,
)

@Serializable
internal data class TarjetaDelConector(
    val nombre: String,
    val banco: String? = null,
    val moneda: String,
    /** Lo que se debe hoy, en la moneda de la tarjeta. Negativo = saldo a favor. */
    val debes: Long,
    val debesEnPesos: Long,
    val cupo: Long? = null,
    val disponible: Long? = null,
    val diaDeCorte: Int? = null,
    val diaDePago: Int? = null,
    val pagoMinimo: Long? = null,
    val tasaEa: Double? = null,
    val extracto: ExtractoDelConector? = null,
)

@Serializable
internal data class CreditosDelConector(val creditos: List<CreditoDelConector>, val tarjetas: List<TarjetaDelConector>)

private suspend fun creditos(uid: String, hoy: LocalDate): RespuestaDeHerramienta {
    val dia = kotlinx.datetime.LocalDate.parse(hoy.toString())
    val creditos = creditosDe(uid).map { c ->
        val t = c.terms
        val plan = planDelCredito(c, dia)
        val primera = mesDeLaPrimeraCuota(c, dia)
        val ultima = plan?.mesesHastaLaUltimaCuota?.let { n -> primera?.let { mesDeLaUltimaCuota(it, n) } }
        CreditoDelConector(
            nombre = textoSeguro(c.account.name, 80),
            banco = t?.bank?.takeIf { it.isNotBlank() }?.let { textoSeguro(it, 60) },
            moneda = c.account.currency,
            debes = valorEnPesosDe(c.account),
            tasaEa = t?.rateEa?.takeIf { it > 0.0 },
            sinIntereses = t?.sinIntereses == true,
            cuota = t?.let { cuotaDeEsteMes(it, c.ultimaCuotaPagada) },
            cuotaEstimada = t?.takeIf { it.cuotaCambia() }?.let { textoDeLaCuotaQueCambia(cuotaDeEsteMes(it, c.ultimaCuotaPagada), c.ultimaCuotaPagada) },
            diaDePago = t?.dayOfMonth,
            plazoMeses = t?.termMonths?.takeIf { it > 0 },
            cuotasQueFaltan = plan?.mesesHastaLaUltimaCuota,
            ultimaCuota = ultima?.let { "${it.year}-" + it.month.toString().padStart(2, '0') },
            interesDelMes = plan?.interes,
            laPagaOtro = !t?.paidBy.isNullOrBlank(),
            descuentoDeNomina = t?.payrollDeduction == true,
        )
    }
    val tarjetas = tarjetasDe(uid).map { s ->
        val t = s.terms
        val e = s.extracto
        TarjetaDelConector(
            nombre = textoSeguro(s.account.name, 80),
            banco = t?.bank?.takeIf { it.isNotBlank() }?.let { textoSeguro(it, 60) },
            moneda = s.account.currency,
            debes = s.account.balancesByCurrency[s.account.currency] ?: s.account.balance,
            debesEnPesos = valorEnPesosDe(s.account),
            cupo = t?.creditLimit,
            disponible = s.available,
            diaDeCorte = t?.cutoffDay,
            diaDePago = t?.paymentDay,
            pagoMinimo = t?.pagoMinimo,
            tasaEa = t?.tasaEa,
            extracto = e?.let {
                ExtractoDelConector(it.corte, it.fechaDePago, it.facturado, it.despuesDelCorte, it.minimo, it.pagadoDesdeElCorte, it.moneda, it.puedeIncluirDiferidos)
            },
        )
    }
    return RespuestaDeHerramienta(
        "${creditos.size} créditos y ${tarjetas.size} tarjetas.",
        datos(CreditosDelConector.serializer(), CreditosDelConector(creditos, tarjetas)),
    )
}

// ── caja_hasta_que_te_paguen ───────────────────────────────────────────────────────────────────

@Serializable
internal data class MovimientoDeLaCajaDelConector(val nombre: String, val monto: Long)

@Serializable
internal data class DiaDeLaCajaDelConector(val fecha: String, val saldo: Long, val libre: Long, val movimientos: List<MovimientoDeLaCajaDelConector> = emptyList())

@Serializable
internal data class CajaDelConector(
    val moneda: String = "COP",
    val desde: String,
    val hasta: String,
    /** `salario` (hasta el próximo salario, ese día incluido), `periodo_siguiente` o `30_dias`. */
    val horizonte: String,
    val enLasCuentasHoy: Long,
    val libreHoy: Long,
    val ahorro: Long,
    val apartado: Long,
    /** El gasto de un día normal que se resta desde mañana; `null` = no hay con qué estimarlo. */
    val gastoDiario: Long? = null,
    val diaMasJusto: DiaDeLaCajaDelConector,
    val primerDiaEnNegativo: DiaDeLaCajaDelConector? = null,
    val alCierre: DiaDeLaCajaDelConector,
    /** Lo que no entra a la cuenta porque le falta un dato (una tarjeta sin mínimo). */
    val sinContar: List<String> = emptyList(),
    val dias: List<DiaDeLaCajaDelConector>,
)

private suspend fun caja(uid: String, hoy: LocalDate): RespuestaDeHerramienta {
    val caja = cajaProyectadaDe(uid, hoy).caja
        ?: return RespuestaDeHerramienta(
            "No hay días que proyectar: el período no está en curso.",
            buildJsonObject { put("dias", kotlinx.serialization.json.JsonArray(emptyList())) },
        )
    fun dia(d: com.jvillada.movi.shared.model.DiaDeLaCaja) = DiaDeLaCajaDelConector(
        fecha = d.fecha,
        saldo = d.saldo,
        libre = d.libre,
        movimientos = d.movimientos.map { MovimientoDeLaCajaDelConector(textoSeguro(it.nombre, 80), it.monto) },
    )
    val r = CajaDelConector(
        desde = caja.dias.first().fecha,
        hasta = caja.dias.last().fecha,
        horizonte = when {
            caja.hastaElSalario -> "salario"
            caja.hastaElPeriodoSiguiente -> "periodo_siguiente"
            else -> "30_dias"
        },
        enLasCuentasHoy = caja.tuPlataHoy,
        libreHoy = caja.libreHoy,
        ahorro = caja.ahorro,
        apartado = caja.apartado,
        gastoDiario = caja.gastoDiario,
        diaMasJusto = dia(caja.diaMasBajo),
        primerDiaEnNegativo = caja.primerDiaEnRojo?.let(::dia),
        alCierre = dia(caja.alCierre),
        sinContar = caja.sinContar.map { textoSeguro(it) },
        dias = caja.dias.map(::dia),
    )
    val resumen = r.primerDiaEnNegativo?.let { "El ${it.fecha} lo libre queda en ${pesosDelCierre(it.libre)}." }
        ?: "Te alcanza hasta el ${r.hasta}: el día más justo es el ${r.diaMasJusto.fecha}, con ${pesosDelCierre(r.diaMasJusto.libre)} libres."
    return RespuestaDeHerramienta(resumen, datos(CajaDelConector.serializer(), r))
}
