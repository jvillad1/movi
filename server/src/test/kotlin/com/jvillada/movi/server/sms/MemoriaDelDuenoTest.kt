package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.routes.SIN_CATEGORIA
import com.jvillada.movi.server.routes.conLoQueMoviRecuerda
import com.jvillada.movi.shared.model.AnotacionPasada
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Lo que el dueño ya anotó vale más que cualquier tabla de palabras clave.**
 *
 * Él lo pidió mirando su lista: *«muchos movimientos quedan en Otro»*. Estas pruebas fijan las dos
 * mitades: que la memoria se arma con los movimientos correctos (y sin los anulados), y que al
 * aplicarla no se lleva por delante lo que no le toca — el pago de tarjeta, el monto, ni el nombre
 * que el banco sí mandó legible.
 */
class MemoriaDelDuenoTest {

    private val dueno = "user-memoria"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:memoria_del_dueno;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            // La base en memoria sobrevive entre métodos (`DB_CLOSE_DELAY=-1`): cada uno arranca
            // borrando, o el segundo choca contra el usuario que insertó el primero.
            SchemaUtils.drop(VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents)
            Users.insert {
                it[id] = dueno; it[email] = "dueno@memoria.test"; it[name] = "Camilo"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = "a1"; it[userId] = dueno; it[name] = "Ahorros"; it[type] = "SAVINGS"
                it[balance] = 1_000_000L
            }
        }
    }

    private fun anotar(
        id: String,
        descripcion: String,
        categoria: String,
        delBanco: String? = null,
        cuando: Long = 1_000,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = dueno
            it[accountId] = "a1"
            it[type] = TransactionType.EXPENSE.name
            it[amount] = 25_000L
            it[currency] = "COP"
            it[category] = categoria
            it[description] = descripcion
            it[merchant] = delBanco
            it[timestamp] = cuando
        }
    }

    private fun gasto(merchant: String, categoria: String = SIN_CATEGORIA) =
        ParsedSms(25_000.0, merchant, TransactionType.EXPENSE, categoria)

    // ── Lo que entra a la memoria ────────────────────────────────────────────

    @Test
    fun `la huella sale del texto del banco y el nombre de como lo dejo escrito el dueno`() {
        anotar("e1", descripcion = "Panadería de la 33", categoria = "Comida", delBanco = "Pago QR · llave 0092184713")

        val memoria = transaction { memoriaDe(dueno) }
        val recuerdo = assertNotNull(memoria.recuerdoDe("Pago QR · llave 0092184713"))

        assertEquals("Comida", recuerdo.categoria)
        assertEquals("Panadería de la 33", recuerdo.nombre, "el nombre que él reconoce, no el del banco")
    }

    /** Sin `merchant` —los movimientos anotados a mano— la huella sale del nombre, que es lo que hay. */
    @Test
    fun `un movimiento anotado a mano tambien ensena`() {
        anotar("e1", descripcion = "Mora Soccer", categoria = "Fútbol")

        assertEquals("Fútbol", assertNotNull(transaction { memoriaDe(dueno) }.recuerdoDe("Pago QR Mora Soccer")).categoria)
    }

    @Test
    fun `un movimiento anulado no ensena nada`() {
        anotar("e-anulado", descripcion = "Zelo Group", categoria = "Tecnología")
        transaction {
            VoidEvents.insert {
                it[id] = "v1"; it[userId] = dueno; it[originalEventId] = "e-anulado"; it[timestamp] = 1_000
            }
        }

        assertNull(transaction { memoriaDe(dueno) }.recuerdoDe("Zelo Group"))
    }

    @Test
    fun `la memoria es de cada dueno`() {
        anotar("e1", descripcion = "Mora Soccer", categoria = "Fútbol")

        assertNull(transaction { memoriaDe("otro-usuario") }.recuerdoDe("Mora Soccer"))
    }

    // ── Cómo se aplica ───────────────────────────────────────────────────────

    @Test
    fun `lo que iba a quedar en Otros sale con la categoria que el dueno usa`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                AnotacionPasada("Mora Soccer", "Mora Soccer", "Fútbol", 10),
                AnotacionPasada("Pago QR Mora Soccer", "Mora Soccer", "Fútbol", 20),
            ),
        )

        val propuesta = conLoQueMoviRecuerda(gasto("Pago QR MORA SOCCER"), memoria)

        assertEquals("Fútbol", propuesta.category)
        assertEquals("Así lo anotaste 2 veces", propuesta.aprendidoDe)
    }

    @Test
    fun `con una sola vez tambien propone, y lo dice en singular`() {
        val memoria = MemoriaDeCategorias.de(listOf(AnotacionPasada("Zelo Group", "Zelo Group", "Tecnología", 10)))

        assertEquals("Así lo anotaste la última vez", conLoQueMoviRecuerda(gasto("Zelo Group"), memoria).aprendidoDe)
    }

    /** Una llave no se lee. Si él ya le puso nombre a ese destinatario, vuelve ese nombre. */
    @Test
    fun `una llave sin nombre se propone con el nombre que el dueno le puso`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(AnotacionPasada("Pago QR · llave 0092184713", "Panadería de la 33", "Comida", 10)),
        )

        val propuesta = conLoQueMoviRecuerda(gasto("Pago QR · llave 0092184713"), memoria)

        assertEquals("Panadería de la 33", propuesta.merchant)
        assertEquals("Comida", propuesta.category)
    }

    /** Con un nombre de comercio de verdad manda el banco: es el dato más fresco de los dos. */
    @Test
    fun `un comercio con nombre conserva el nombre del mensaje`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(AnotacionPasada("MCDONALDS CALLE 10", "Almuerzo del sábado", "Comida", 10)),
        )

        val propuesta = conLoQueMoviRecuerda(gasto("MCDONALDS CALLE 10"), memoria)

        assertEquals("MCDONALDS CALLE 10", propuesta.merchant)
        assertEquals("Comida", propuesta.category)
    }

    /**
     * **El pago de tarjeta no es una categoría de gasto sino una regla de plata.** Si la memoria
     * pudiera moverlo, un abono a la AMEX volvería a contarse como gasto del mes.
     */
    @Test
    fun `la memoria no toca un pago de tarjeta`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(AnotacionPasada("Pago de tarjeta", "Pago de tarjeta", "Comida", 10)),
        )

        val propuesta = conLoQueMoviRecuerda(gasto("Pago de tarjeta", CARD_PAYMENT_CATEGORY), memoria)

        assertEquals(CARD_PAYMENT_CATEGORY, propuesta.category)
        assertNull(propuesta.aprendidoDe)
    }

    @Test
    fun `sin historia la propuesta queda igual que salio del mensaje`() {
        val propuesta = conLoQueMoviRecuerda(gasto("Zelo Group"), MemoriaDeCategorias.vacia)

        assertEquals(SIN_CATEGORIA, propuesta.category)
        assertNull(propuesta.aprendidoDe, "sin nada que recordar, no hay nada que explicar")
    }
}
