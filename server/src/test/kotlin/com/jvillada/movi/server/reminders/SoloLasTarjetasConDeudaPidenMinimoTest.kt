package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Qué tarjetas llegan a `/api/payments/upcoming`, y por lo tanto de cuáles se pide el mínimo.**
 *
 * `ResumenRecurrentes.tarjetasSinMinimo` cuenta las reglas `card_*` que llegaron sin
 * `pagoMinimoCop`, y el aviso de «te falta este dato» sale de ese contador. O sea que **la lista de
 * tarjetas que el server manda es la que decide por cuántas se le pregunta al dueño**, y esa
 * decisión vive acá, en [loadCardRulePairs] — no del lado del cliente, donde solo se puede probar
 * que una lista sin tarjetas no pide nada.
 *
 * Hoy tres de sus cinco tarjetas están en $0 (el AMEX ·9208, Nu, Davivienda ·9418). Si esas
 * entraran, la pantalla le pediría cargar cuatro mínimos que no le hacen falta a nadie — y un aviso
 * que pide de más se apaga solo en la cabeza de quien lo lee.
 */
class SoloLasTarjetasConDeudaPidenMinimoTest {

    private val uid = "u-minimos"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:solo_tarjetas_con_deuda;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Cards, VoidEvents, Events, Accounts)
            SchemaUtils.create(Accounts, Events, VoidEvents, Cards)

            // La Master Black: debe $27.647.837 y su extracto exige $1.843.014 este mes.
            tarjeta("acc-master-black", "Master Black", pagoMinimo = 1_843_014L)
            deuda("ev-master", "acc-master-black", 27_647_837L)

            // El AMEX ·9208: existe, tiene términos, y hoy no debe nada.
            tarjeta("acc-amex-9208", "AMEX 9208", pagoMinimo = null)

            // Y Nu, que tuvo deuda y ya se pagó: los eventos se cancelan a $0, no desaparecen.
            tarjeta("acc-nu", "Nu", pagoMinimo = null)
            deuda("ev-nu-compra", "acc-nu", 500_000L)
            Events.insert {
                it[id] = "ev-nu-pago"
                it[userId] = uid
                it[accountId] = "acc-nu"
                it[type] = "INCOME"
                it[amount] = 500_000L
                it[currency] = "COP"
                it[category] = "Pago de tarjeta"
                it[description] = "Pago total"
                it[timestamp] = System.currentTimeMillis()
            }
        }
    }

    @Test
    fun `solo la tarjeta con deuda genera regla, y las que estan en cero no piden nada`() {
        val reglas = runBlocking { loadCardRulePairs(uid) }.map { it.first }

        assertEquals(
            listOf("card_acc-master-black"),
            reglas.map { it.id },
            "una tarjeta en \$0 no tiene nada que pagar: ni regla, ni mínimo por pedir",
        )
        assertEquals(1_843_014L, reglas.single().pagoMinimoCop)
        assertEquals(27_647_837L, reglas.single().amount, "el monto sigue siendo el saldo")
    }

    /**
     * **Y una tarjeta con deuda y sin mínimo cargado sí llega, con el campo en `null`.** Es el
     * estado real de cuatro de sus cinco tarjetas: es lo que hace que la pantalla diga «te falta
     * este dato» en vez de afirmar una cifra optimista.
     */
    @Test
    fun `una tarjeta con deuda y sin minimo llega igual, para poder pedirlo`() {
        transaction { deuda("ev-amex", "acc-amex-9208", 19_818_701L) }

        val reglas = runBlocking { loadCardRulePairs(uid) }.map { it.first }

        assertEquals(setOf("card_acc-master-black", "card_acc-amex-9208"), reglas.map { it.id }.toSet())
        assertNull(reglas.single { it.id == "card_acc-amex-9208" }.pagoMinimoCop)
    }

    /** Sin ninguna tarjeta con deuda no se arma nada — ni se sale a pedir la TRM. */
    @Test
    fun `sin ninguna tarjeta con deuda la lista llega vacia`() {
        transaction { Events.deleteAll() }

        assertTrue(runBlocking { loadCardRulePairs(uid) }.isEmpty())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun tarjeta(cuenta: String, nombre: String, pagoMinimo: Long?) {
        Accounts.insert {
            it[id] = cuenta
            it[userId] = uid
            it[name] = nombre
            it[type] = "CREDIT_CARD"
            it[currency] = "COP"
        }
        Cards.insert {
            it[accountId] = cuenta
            it[userId] = uid
            it[bank] = "Bancolombia"
            it[paymentDay] = 25
            it[Cards.pagoMinimo] = pagoMinimo
        }
    }

    private fun deuda(evento: String, cuenta: String, monto: Long) {
        Events.insert {
            it[id] = evento
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = "EXPENSE"
            it[amount] = monto
            it[currency] = "COP"
            it[category] = "Mercado"
            it[description] = "Compra"
            it[timestamp] = System.currentTimeMillis()
        }
    }
}
