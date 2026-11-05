package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.time.AppClock
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * # El tope diario de gasto en IA (revisión de lanzamiento, nov-2026)
 *
 * El tope del mes del chat ([ElTopeDelMes]) y el de los papeles ([ElTopeDeLosPapeles]) cuentan
 * preguntas y lecturas; ninguno sabe cuánto **cuesta** el día. Este sí: suma el costo de cada llamada a
 * Anthropic (el mismo que calcula `Metricas.registrarServicio` con [PreciosDeLaIa]) y, pasado el tope,
 * `crearMensaje` no llama: lanza [TopeDiarioDeLaIa], que [fallaDeLaIa] lee como `SIN_CREDITO` (no se
 * prueba el respaldo, la app dice lo de siempre y las tres preguntas sugeridas siguen funcionando).
 *
 * El tope sale de `IA_GASTO_MAXIMO_DIARIO_USD` (dólares; `0` lo apaga); sin la variable,
 * [TOPE_POR_DEFECTO_USD], **elegido, no medido**: holgado para el uso normal y un techo a lo que un abuso
 * puede gastar en un día. El día es el de la app (`AppClock`). La primera vez de cada día arranca desde
 * `ia_por_dia` (lo que ya se volcó hoy: sobrevive a un reinicio, con hasta un minuto de atraso) y de ahí
 * suma en memoria. Con una sola réplica alcanza.
 */
object ElGastoDelDia {
    const val TOPE_POR_DEFECTO_USD: Double = 25.0
    private val log = LoggerFactory.getLogger("ElGastoDelDia")

    /** Las pruebas cambian estas dos costuras. */
    internal var topeEnDolares: () -> Double? = { topeDeLaVariable(System.getenv("IA_GASTO_MAXIMO_DIARIO_USD")) }
    internal var loGuardadoDelDia: (String) -> Double = { dia ->
        transaction {
            IaPorDia.selectAll().where { IaPorDia.dia eq dia }.firstOrNull()?.get(IaPorDia.costoMicrodolares)?.toDouble()
        } ?: 0.0
    }
    internal var hoy: () -> String = { AppClock.today().toString() }

    private var dia: String? = null
    private var microdolares: Double = 0.0
    private var avisado: Boolean = false

    /** `null` = sin tope (la variable en 0); un valor que no se lee usa el de por defecto. */
    internal fun topeDeLaVariable(valor: String?): Double? {
        val v = valor?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() } ?: return TOPE_POR_DEFECTO_USD
        val n = v.toDoubleOrNull()?.takeIf { it >= 0 } ?: return TOPE_POR_DEFECTO_USD
        return if (n == 0.0) null else n
    }

    @Synchronized
    private fun alDia() {
        val h = hoy()
        if (dia == h) return
        dia = h
        avisado = false
        microdolares = runCatching { loGuardadoDelDia(h) }.getOrDefault(0.0)
    }

    /** Suma lo que costó una llamada, en microdólares. */
    @Synchronized
    fun sumar(costoMicrodolares: Double) {
        if (topeEnDolares() == null) return
        alDia()
        microdolares += costoMicrodolares
    }

    /** ¿Ya se gastó el tope de hoy? */
    @Synchronized
    fun seAcabo(): Boolean {
        val tope = topeEnDolares() ?: return false
        alDia()
        val acabo = microdolares >= tope * 1_000_000
        if (acabo && !avisado) {
            avisado = true
            log.warn("IA: se llegó al tope diario de US$ {} (IA_GASTO_MAXIMO_DIARIO_USD); hasta mañana no se llama a Anthropic", tope)
        }
        return acabo
    }

    /** Lanza [TopeDiarioDeLaIa] si ya se gastó el tope de hoy. Lo llama `crearMensaje` antes de cada llamada. */
    fun comprobar() {
        if (seAcabo()) throw TopeDiarioDeLaIa()
    }

    /** Para las pruebas: vuelve a empezar el día. */
    @Synchronized
    internal fun olvidar() {
        dia = null
        microdolares = 0.0
        avisado = false
    }
}

/** Se llegó al tope diario de gasto en IA: [fallaDeLaIa] lo lee como `SIN_CREDITO`. */
class TopeDiarioDeLaIa : RuntimeException("Se llegó al tope diario de gasto en IA")
