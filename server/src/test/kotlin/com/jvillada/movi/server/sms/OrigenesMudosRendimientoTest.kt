package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * # Vuelta 90 (revisión) · el banco mudo no lee la bandeja entera en cada Inicio
 *
 * `origenesMudosDe` corre dentro de `/api/dashboard/summary`. Con la primera versión leía cada aviso
 * de los últimos 120 días con el parser; ahora lo confirmado o con movimiento cuenta sin leerse. Esto
 * mide las dos formas sobre una bandeja de 3.000 avisos con la mezcla del dueño (casi todo confirmado)
 * y lo imprime; solo afirma lo estructural: la forma nueva lee muchos menos avisos.
 */
class OrigenesMudosRendimientoTest {

    private val uid = "user-rendimiento"
    private val bogota = ZoneId.of("America/Bogota")
    private val formato = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val textos = listOf(
        "Bancolombia: Compraste COP86.900,00 en TIENDA LA 14 con tu T.Cred *3684, el 30/10/2026 a las 12:10.",
        "Bancolombia: Pagaste \$138,600.00 a Salud Total S A desde tu producto 3333 el 05/09/2026 17:16:47.",
        "Bancolombia: Transferiste \$75.000 desde tu cuenta *9999 a la cuenta *55500004444 el 01/10/2026 a las 09:00.",
        "Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim el 02/10/2026 a las 19:25 en TOSTAO CAFE Y PAN.",
        "Fondos insuficientes: Se rechazó tu pago por \$17.150,00 COP.",
    )

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:origenes_mudos_rendimiento;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(SmsMessages, Users)
            SchemaUtils.create(Users, SmsMessages)
            Users.insert { it[id] = uid; it[email] = "r@r.test"; it[name] = "r"; it[passwordHash] = "h" }
            val ahora = ZonedDateTime.now(bogota)
            // 3.000 avisos en 120 días: 90 % confirmados con su movimiento, 7 % ignorados, 3 % pendientes.
            SmsMessages.batchInsert((0 until 3_000).toList()) { i ->
                this[SmsMessages.id] = "sms_$i"
                this[SmsMessages.userId] = uid
                this[SmsMessages.time] = ahora.minusMinutes(i * 57L).format(formato)
                this[SmsMessages.bank] = if (i % 3 == 0) "Notificación · Glim" else "85540"
                this[SmsMessages.text] = textos[i % textos.size]
                val estado = when (i % 100) { in 0..89 -> SMS_STATE_CONFIRMED; in 90..96 -> "ignored"; else -> "pending" }
                this[SmsMessages.state] = estado
                this[SmsMessages.eventoId] = if (estado == SMS_STATE_CONFIRMED) "ev_$i" else null
                this[SmsMessages.det] = ""
            }
        }
    }

    private fun medir(veces: Int, bloque: () -> Unit): Long {
        bloque() // en frío una vez
        val tiempos = (1..veces).map { val t0 = System.nanoTime(); bloque(); (System.nanoTime() - t0) / 1_000_000 }
        return tiempos.sorted()[veces / 2]
    }

    @Test
    fun `la forma nueva no parsea lo confirmado`() {
        val ahora = System.currentTimeMillis()
        val filas = transaction {
            SmsMessages.selectAll().map { listOf(it[SmsMessages.text], it[SmsMessages.bank], it[SmsMessages.state], it[SmsMessages.eventoId]) }
        }
        // Antes: el parser sobre cada aviso (lo que hacía la primera versión de la vuelta 90).
        val antes = medir(5) {
            filas.forEach { f -> queTraeLaCaptura(f[0]!!, f[1]!!, null, estado = null, eventoId = null, conElParser = true) }
        }
        val despues = medir(5) { transaction { origenesMudosDe(uid, ahora) } }
        val leidasAntes = filas.size
        val leidasAhora = filas.count { it[2] != SMS_STATE_CONFIRMED && it[3] == null }
        println("banco mudo · 3.000 avisos · antes (parser en todo): $antes ms · ahora (origenesMudosDe completo): $despues ms · avisos parseados: $leidasAntes → $leidasAhora")
        assertTrue(leidasAhora * 10 <= leidasAntes, "lo confirmado no se vuelve a leer")
    }
}
