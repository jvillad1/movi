package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Lo que el asistente puede preguntarle a la base
 *
 * Estas dos consultas son la diferencia entre un asistente que lee un resumen y uno al que se le
 * puede preguntar «¿y en julio?». Lo que se fija acá es lo mismo que en el Inicio: **las cifras
 * tienen que ser las de la pantalla**. Un asistente que conteste otra cosa es peor que uno que no
 * sepa — y como responde con seguridad, el error no se nota.
 */
class HerramientasDelAsistenteTest {

    private val dueno = "user-herramientas"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:herramientas_asistente;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents)
            Users.insert {
                it[id] = dueno; it[email] = "dueno@herramientas.test"; it[name] = "Camilo"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = "a1"; it[userId] = dueno; it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"; it[balance] = 5_000_000L
            }
            Accounts.insert {
                it[id] = "tc"; it[userId] = dueno; it[name] = "Master Black"
                it[type] = "CREDIT_CARD"; it[balance] = -2_000_000L
            }
        }
    }

    private fun anotar(
        id: String,
        nombre: String,
        categoria: String,
        monto: Long,
        fecha: String,
        cuenta: String = "a1",
        tipo: TransactionType = TransactionType.EXPENSE,
        moneda: String = "COP",
        estado: String = "RECONCILED",
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = dueno
            it[accountId] = cuenta
            it[type] = tipo.name
            it[amount] = monto
            it[currency] = moneda
            it[category] = categoria
            it[description] = nombre
            it[timestamp] = appDateToEpochMillis(LocalDate.parse(fecha)) + 12 * 3_600_000L
            it[reconciliationStatus] = estado
        }
    }

    private fun preguntar(nombre: String, vararg args: Pair<String, String>, uid: String = dueno) =
        runBlocking { ejecutarHerramienta(uid, LlamadaDeHerramienta("tu_1", nombre, args.toMap())) }

    // ── Buscar movimientos ───────────────────────────────────────────────────

    @Test
    fun `encuentra por nombre, sin importar tildes ni mayusculas`() {
        anotar("e1", "Café 9 ¾ Bookstore", "Hija", 32_000, "2026-08-10")
        anotar("e2", "McDonald's", "Comida", 28_000, "2026-08-11")

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31", "texto" to "cafe")

        assertTrue("Café 9 ¾ Bookstore" in texto, texto)
        assertFalse("McDonald" in texto, "solo lo que coincide:\n$texto")
    }

    @Test
    fun `respeta las fechas que le piden`() {
        anotar("e-julio", "Almuerzo de julio", "Comida", 20_000, "2026-07-15")
        anotar("e-agosto", "Almuerzo de agosto", "Comida", 21_000, "2026-08-15")

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31")

        assertTrue("Almuerzo de agosto" in texto)
        assertFalse("Almuerzo de julio" in texto)
    }

    /** Las mismas reglas que el Inicio: si acá entrara, el asistente contestaría otra cifra. */
    @Test
    fun `lo anulado, lo que espera en Por confirmar y el pago de tarjeta no entran`() {
        anotar("e-anulado", "Compra anulada", "Comida", 50_000, "2026-08-10")
        transaction {
            VoidEvents.insert {
                it[id] = "v1"; it[userId] = dueno; it[originalEventId] = "e-anulado"
                it[timestamp] = appDateToEpochMillis(LocalDate.parse("2026-08-10"))
            }
        }
        anotar("e-espera", "Compra por confirmar", "Comida", 60_000, "2026-08-11", estado = "UNCONFIRMED")
        anotar("e-tarjeta", "Pago a la Master", CARD_PAYMENT_CATEGORY, 9_000_000, "2026-08-12", cuenta = "tc", tipo = TransactionType.INCOME)
        anotar("e-real", "Almuerzo", "Comida", 25_000, "2026-08-13")

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31")

        assertTrue("Almuerzo" in texto)
        assertFalse("anulada" in texto, texto)
        assertFalse("por confirmar" in texto.lowercase(), texto)
        assertFalse("Master" in texto, "un abono a la tarjeta no es un movimiento del mes:\n$texto")
    }

    @Test
    fun `sin resultados lo dice, no devuelve una lista vacia`() {
        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31", "texto" to "Rappi")
        assertTrue("Sin movimientos" in texto, texto)
        assertTrue("Rappi" in texto, "que diga qué buscó, para que el modelo no lo repita igual")
    }

    /** El tope se anuncia y manda a la otra herramienta: la que sí suma todo. */
    @Test
    fun `cuando hay mas de los que caben, avisa y dice donde esta la cifra completa`() {
        repeat(TOPE_DE_RESULTADOS + 3) { i ->
            anotar("e-muchos-$i", "Almuerzo $i", "Comida", 10_000, "2026-08-10")
        }

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31")

        assertTrue("${TOPE_DE_RESULTADOS + 3} movimientos" in texto, texto.take(200))
        assertTrue("3 más que no se listan" in texto, texto.takeLast(300))
        assertTrue(TOTALES_POR_CATEGORIA in texto)
    }

    // ── Totales ──────────────────────────────────────────────────────────────

    @Test
    fun `los totales suman todo y separan lo que entro de lo que salio`() {
        anotar("e1", "Mercado", "Comida", 300_000, "2026-08-05")
        anotar("e2", "Restaurante", "Comida", 200_000, "2026-08-06")
        anotar("e3", "Fútbol del jueves", "Fútbol", 50_000, "2026-08-07")
        anotar("e4", "Nómina", "Salario", 8_500_000, "2026-08-01", tipo = TransactionType.INCOME)

        val texto = preguntar(TOTALES_POR_CATEGORIA, "desde" to "2026-08-01", "hasta" to "2026-08-31")

        assertTrue("Entró: 8500000" in texto, texto)
        assertTrue("Salió: 550000" in texto, texto)
        assertTrue("Comida: 500000" in texto, texto)
        assertTrue("Fútbol: 50000" in texto, texto)
    }

    /**
     * **Las monedas nunca se suman entre sí.** Sus cobros de Anthropic y Railway son en dólares;
     * sumarlos a los pesos daría una cifra que no significa nada — y el asistente la diría con la
     * misma seguridad que las demás.
     */
    @Test
    fun `los dolares van aparte de los pesos`() {
        anotar("e-cop", "Mercado", "Comida", 300_000, "2026-08-05")
        anotar("e-usd", "Anthropic", "Tecnología", 20, "2026-08-06", moneda = "USD")

        val texto = preguntar(TOTALES_POR_CATEGORIA, "desde" to "2026-08-01", "hasta" to "2026-08-31")

        assertTrue("== En COP ==" in texto, texto)
        assertTrue("== En USD ==" in texto, texto)
        assertFalse("300020" in texto, "no se pueden haber sumado:\n$texto")
    }

    // ── Los bordes ───────────────────────────────────────────────────────────

    @Test
    fun `una fecha ilegible se le explica al modelo en vez de reventar`() {
        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "agosto")
        assertTrue("AAAA-MM-DD" in texto, texto)
    }

    /**
     * **Lo que se le ofrece al modelo es exactamente lo que se sabe ejecutar.** Es el defecto
     * clásico de esta clase de código: se renombra la herramienta en un lado y el modelo llama a
     * algo que no existe — y como el error viaja como resultado, la conversación sigue y la falla
     * se ve como un asistente que «no supo».
     */
    @Test
    fun `las herramientas que se ofrecen son las que se saben ejecutar`() {
        val ofrecidas = LAS_HERRAMIENTAS.map { it.name() }

        assertEquals(setOf(BUSCAR_MOVIMIENTOS, TOTALES_POR_CATEGORIA), ofrecidas.toSet())
        ofrecidas.forEach { nombre ->
            assertFalse(
                "No existe una herramienta" in preguntar(nombre, "desde" to "2026-08-01", "hasta" to "2026-08-31"),
                "se ofrece «$nombre» y no se sabe ejecutar",
            )
        }
        // Y cada una se explica: una descripción vacía es una herramienta que el modelo no usa.
        LAS_HERRAMIENTAS.forEach { herramienta ->
            assertTrue(herramienta.description().orElse("").length > 40, "«${herramienta.name()}» sin descripción")
        }
    }

    @Test
    fun `una herramienta que no existe se contesta, no explota`() {
        assertTrue("No existe una herramienta" in preguntar("borrar_todo"))
    }

    /** El `uid` sale del token. Otro usuario no ve un solo movimiento de este dueño. */
    @Test
    fun `cada quien ve solo lo suyo`() {
        anotar("e1", "Mercado", "Comida", 300_000, "2026-08-05")

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31", uid = "otro-usuario")

        assertTrue("Sin movimientos" in texto, texto)
    }

    @Test
    fun `sin fechas mira hacia atras, no adivina`() {
        val hoy = AppClock.today()
        anotar("e-reciente", "Compra reciente", "Comida", 10_000, hoy.minusDays(3).toString())
        anotar("e-vieja", "Compra vieja", "Comida", 10_000, hoy.minusMonths(MESES_HACIA_ATRAS_POR_DEFECTO + 2).toString())

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "texto" to "Compra")

        assertTrue("Compra reciente" in texto, texto)
        assertFalse("Compra vieja" in texto, "la ventana por defecto es de $MESES_HACIA_ATRAS_POR_DEFECTO meses:\n$texto")
    }

    @Test
    fun `el limite que pide el modelo se respeta, con tope`() {
        repeat(10) { i -> anotar("e-$i", "Almuerzo $i", "Comida", 10_000, "2026-08-10") }

        val texto = preguntar(BUSCAR_MOVIMIENTOS, "desde" to "2026-08-01", "hasta" to "2026-08-31", "limite" to "3")

        assertEquals(3, texto.lines().count { it.startsWith("- ") }, texto)
        assertTrue("7 más que no se listan" in texto)
    }
}
