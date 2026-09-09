package com.jvillada.movi.server.db

import com.jvillada.movi.server.credits.toCardTerms
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `pago_minimo` es una columna NUEVA en una tabla VIEJA con las cinco tarjetas reales del dueño
 * adentro.
 *
 * Es el gemelo exacto de [OtrosCargosColumnTest], y existe por lo mismo: **las migraciones de este
 * server corren DENTRO de la transacción de arranque**, así que un DDL que falle la aborta entera
 * y deja el server sin levantar — al dueño sin app, no con un bug. La pregunta que se contesta acá
 * no es «¿queda la columna?» sino «¿qué SQL exactamente va a ejecutar Exposed sobre `card_terms`
 * con cinco tarjetas adentro?».
 *
 * (`SchemaDeArranqueTest` cubre la otra mitad: que `Cards` esté en la lista de
 * `createMissingTablesAndColumns`, sin la cual este ALTER no se emite nunca.)
 */
class PagoMinimoColumnTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:pago_minimo_column_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Cards)
            SchemaUtils.create(Cards)
            exec("ALTER TABLE card_terms DROP COLUMN pago_minimo")
            // El Master Black tal como está hoy en producción: con cupo y días declarados, y sin
            // ningún lugar donde poner los $1.843.014 que el extracto le exige este mes.
            exec(
                """INSERT INTO card_terms
                   (account_id, user_id, bank, credit_limit, cutoff_day, payment_day, remind_me)
                   VALUES ('acc-master-black', 'u1', 'Bancolombia', 40000000, 10, 25, TRUE)""",
            )
        }
    }

    @Test
    fun `el unico DDL es un ADD COLUMN nullable, sin indices ni NOT NULL`() {
        // La verificación que el arranque necesita, hecha ANTES de ejecutar nada: se le pregunta a
        // Exposed qué sentencias piensa correr. Un `CREATE INDEX` sobre datos o un `NOT NULL` sin
        // default son las dos formas conocidas de tumbar esta transacción — y `card_terms` tiene
        // un índice declarado en su `init`, así que la pregunta no es retórica.
        val sentencias = transaction { SchemaUtils.addMissingColumnsStatements(Cards) }

        assertEquals(1, sentencias.size, "una sola sentencia: $sentencias")
        val ddl = sentencias.single().uppercase()
        assertTrue(ddl.contains("ADD") && ddl.contains("PAGO_MINIMO"), ddl)
        assertTrue(!ddl.contains("NOT NULL"), "una columna nullable no puede fallar sobre filas existentes: $ddl")
        assertTrue(!ddl.contains("CREATE INDEX"), ddl)
    }

    @Test
    fun `las tarjetas que ya existian quedan sin minimo, no en cero ni rotas`() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Cards)
            val terms = Cards.selectAll().single().toCardTerms()

            // **La distinción entera de esta feature.** Un 0 diría «esta tarjeta no te exige nada
            // este mes» y el «Flujo libre» seguiría afirmando una cifra optimista sin avisar;
            // `null` dice «no se sabe», que es lo que la pantalla convierte en un aviso.
            assertNull(terms.pagoMinimo, "sin declarar no es lo mismo que declarar 0")
            // Y el resto de la fila sobrevivió: la migración no reescribe nada. En particular el
            // cupo, que es el campo con el que más fácil se confunde este.
            assertEquals(40_000_000L, terms.creditLimit)
            assertEquals(25, terms.paymentDay)
            assertEquals(10, terms.cutoffDay)
        }
    }

    @Test
    fun `correrla dos veces no cambia nada`() {
        // Idempotencia: el server arranca muchas veces con la columna ya puesta.
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Cards)
            SchemaUtils.createMissingTablesAndColumns(Cards)
            assertEquals(0, SchemaUtils.addMissingColumnsStatements(Cards).size)
        }
    }
}
