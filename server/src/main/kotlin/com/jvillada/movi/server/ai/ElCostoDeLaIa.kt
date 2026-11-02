package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.fx.TasaUsdCop
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.CostoDeLaIa
import com.jvillada.movi.shared.model.GastoDeIaDeUnUsuario
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * # Lo que cuesta la IA, en pesos, para el admin (vuelta 106)
 *
 * Dos fuentes, cada una para lo suyo:
 * - **`ia_por_dia`** (toda la IA: el chat, la clasificación de papeles, los extractos), con el costo
 *   que se calculó llamada por llamada al volcar las métricas: los totales de 7 y 30 días, el mes y
 *   su proyección, y el costo por usuario activo.
 * - **`ai_turns`** (el chat), con el costo de cada turno sacado de sus fichas (`PreciosDeLaIa`): las
 *   preguntas de 30 días y los diez usuarios que más gastan.
 *
 * Los pesos salen con la TRM que Movi ya usa ([FxRateService.tasaUsdCop]: la del día, la última
 * leída o `USD_COP_RATE`). Lo de antes de esta vuelta no tiene costo en `ia_por_dia` (la columna no
 * existía): [CostoDeLaIa.medidoDesde] dice desde cuándo hay.
 */
object ElCostoDeLaIa {


    suspend fun resumen(ahora: Long = System.currentTimeMillis(), tasa: TasaUsdCop? = null): CostoDeLaIa {
        val trm = tasa ?: FxRateService.tasaUsdCop()
        val hoy = Instant.ofEpochMilli(ahora).atZone(AppClock.zone).toLocalDate()
        return dbQuery {
            // ── ia_por_dia: los días con costo medido ────────────────────────────
            val desde30 = hoy.minusDays(29)
            val inicioDelMes = hoy.withDayOfMonth(1)
            val primero = minOf(desde30, inicioDelMes).toString()
            val dias = IaPorDia.selectAll().where { IaPorDia.dia greaterEq primero }
                .mapNotNull { r -> r[IaPorDia.costoMicrodolares]?.let { LocalDate.parse(r[IaPorDia.dia]) to it } }
            fun microEntre(desde: LocalDate): Long = dias.filter { (d, _) -> !d.isBefore(desde) && !d.isAfter(hoy) }.sumOf { it.second }
            val micro7 = microEntre(hoy.minusDays(6))
            val micro30 = microEntre(desde30)
            val microDelMes = microEntre(inicioDelMes)
            val medidoDesde = dias.minOfOrNull { it.first }?.toString()

            // ── Activos de 30 días: los mismos de la pestaña Uso ─────────────────
            val diaHoy = hoy.toEpochDay().toInt()
            val activos30 = UsoDeLaApp.select(UsoDeLaApp.userId)
                .where { (UsoDeLaApp.dia greater (diaHoy - 30)) and (UsoDeLaApp.plataforma neq "server") }
                .withDistinct()
                .count().toInt()

            // ── ai_turns: lo de cada usuario en 30 días ──────────────────────────
            val desdeMs = desde30.atStartOfDay(AppClock.zone).toInstant().toEpochMilli()
            data class Suma(var preguntas: Int = 0, var micro: Double = 0.0)
            val porUsuario = HashMap<String, Suma>()
            val sinPrecio = sortedSetOf<String>()
            AiTurns.selectAll()
                .where { (AiTurns.creadoEn greaterEq desdeMs) and (AiTurns.modelo neq MODELO_SIN_IA) }
                .forEach { r ->
                    val modelo = r[AiTurns.modelo]
                    val costo = PreciosDeLaIa.costoDeUnTurno(
                        modelo = modelo,
                        entrada = r[AiTurns.fichasEntrada],
                        escritas = r[AiTurns.fichasCacheEscritas] ?: 0L,
                        leidas = r[AiTurns.fichasCache],
                        salida = r[AiTurns.fichasSalida],
                        llamadas = r[AiTurns.vueltas],
                    )
                    if (costo == null) sinPrecio += modelo
                    porUsuario.getOrPut(r[AiTurns.userId]) { Suma() }.let { it.preguntas++; it.micro += costo ?: 0.0 }
                }
            val diez = porUsuario.entries.sortedWith(compareByDescending<Map.Entry<String, Suma>> { it.value.micro }.thenByDescending { it.value.preguntas }).take(10)
            val correos = if (diez.isEmpty()) emptyMap() else Users.selectAll().where { Users.id inList diez.map { it.key } }
                .associate { it[Users.id] to it[Users.email] }
            val preguntas30 = porUsuario.values.sumOf { it.preguntas }
            val microDelChat30 = porUsuario.values.sumOf { it.micro }

            val pesos30 = aPesos(micro30.toDouble(), trm.valor)
            val pesosDelMes = aPesos(microDelMes.toDouble(), trm.valor)
            val diasDelMes = hoy.lengthOfMonth()
            // El primer mes se mide desde el despliegue: la proyección divide por los días medidos.
            val primeroMedidoDelMes = dias.map { it.first }.filter { !it.isBefore(inicioDelMes) }.minOrNull()
            val diasMedidos = primeroMedidoDelMes?.let { hoy.dayOfMonth - it.dayOfMonth + 1 } ?: hoy.dayOfMonth
            CostoDeLaIa(
                trm = trm.valor,
                trmEsRespaldo = trm.esRespaldo,
                dolares7 = micro7 / 1_000_000.0,
                dolares30 = micro30 / 1_000_000.0,
                pesos7 = aPesos(micro7.toDouble(), trm.valor),
                pesos30 = pesos30,
                activos30 = activos30,
                pesosPorActivo30 = if (activos30 == 0) null else pesos30 / activos30,
                preguntas30 = preguntas30,
                pesosPorPregunta30 = if (preguntas30 == 0) null else microDelChat30 / preguntas30 / 1_000_000.0 * trm.valor,
                pesosDelMes = pesosDelMes,
                proyeccionDelMes = proyeccionDelMes(pesosDelMes, diasMedidos, diasDelMes),
                diaDelMes = hoy.dayOfMonth,
                diasDelMes = diasDelMes,
                diasMedidosDelMes = diasMedidos,
                quienesMasGastan = diez.map { (uid, s) ->
                    GastoDeIaDeUnUsuario(
                        usuarioId = uid,
                        correo = correos[uid].orEmpty(),
                        preguntas = s.preguntas,
                        dolares = s.micro / 1_000_000.0,
                        pesos = aPesos(s.micro, trm.valor),
                    )
                },
                medidoDesde = medidoDesde,
                sinPrecio = sinPrecio.toList(),
                precios = "Precios de ${PreciosDeLaIa.FUENTE}, del ${PreciosDeLaIa.FECHA}",
            )
        }
    }

    /** Microdólares a pesos, con la TRM [trm]. */
    fun aPesos(microdolares: Double, trm: Double): Long = (microdolares / 1_000_000.0 * trm).roundToLong()

    /** Lo que va del mes, llevado al mes entero: lo de [diasMedidos] días, por [diasDelMes]. */
    fun proyeccionDelMes(pesosDelMes: Long, diasMedidos: Int, diasDelMes: Int): Long =
        if (diasMedidos <= 0) pesosDelMes else (pesosDelMes.toDouble() / diasMedidos * diasDelMes).roundToLong()
}
