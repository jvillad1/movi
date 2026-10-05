package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ClaseDeCargo
import com.jvillada.movi.shared.model.EstadoDelCargo
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.estadoDelCargo
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Un extracto con cargos del banco, por el camino entero
 *
 * `procesarExtracto` con un lector falso ([LectorDePapeles.actual]): ninguna prueba llama a Anthropic.
 * Lo que se fija: las filas de cargos llegan con la categoría del dueño y su tipo, marcadas aparte; las
 * cuatro líneas del 4x1000 que el dueño ya anotó sumadas por $17.819 salen «ya anotadas» en su cuenta;
 * con otra suma, salen con el aviso; y la memoria del dueño le gana a la categoría del cargo.
 */
class CargosDelBancoEnElExtractoTest {

    private val dueno = "user-dueno-cargos"
    private val ahorros = "acc_ahorros_cargos"
    private val nu = "acc_nu_cargos"

    private var filasDelExtracto: List<ParsedTransaction> = emptyList()

    private val lectorFalso = object : LectorDePapeles {
        override suspend fun queEs(contenido: ContenidoDelPapel): QueDiceElPapel = QueDiceElPapel.Extracto
        override suspend fun leerExtractoDeTexto(texto: String, reglas: List<MerchantRule>) =
            ClaudeStatementParser.Lectura.Ok(filasDelExtracto)
        override suspend fun leerExtractoDeImagen(bytes: ByteArray, mime: String, reglas: List<MerchantRule>) =
            ClaudeStatementParser.Lectura.Ok(filasDelExtracto)
    }

    @BeforeTest
    fun setUp() {
        LectorDePapeles.actual = lectorFalso
        Database.connect(
            url = "jdbc:h2:mem:cargos_del_banco_extracto;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Documents, VoidEvents, Events, StatementImports, Accounts, Users, KnownDestinations)
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, Documents, KnownDestinations)
            Users.insert { it[id] = dueno; it[email] = "$dueno@cargos.test"; it[name] = dueno; it[passwordHash] = "h" }
            listOf(ahorros to "Bancolombia Ahorros", nu to "Nu").forEach { (id, nombre) ->
                Accounts.insert {
                    it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = "SAVINGS"; it[currency] = "COP"
                }
            }
            // Su vocabulario: las tres categorías de los cargos, anotadas a mano antes.
            evento("ev_4x1000", ahorros, "EXPENSE", 17_819, "Impuestos", "4x1000 del 27 al 30 de septiembre", LocalDate.of(2026, 9, 30))
            evento("ev_cuota", ahorros, "EXPENSE", 21_640, "Comisiones del banco", "Cuota de manejo cupo rotativo", LocalDate.of(2026, 9, 16))
            evento("ev_int", ahorros, "INCOME", 15, "Ingreso", "Abono intereses ahorros", LocalDate.of(2026, 9, 6))
        }
    }

    @AfterTest
    fun devolverElLector() {
        LectorDePapeles.actual = LectorDePapelesConClaude
    }

    private fun evento(
        id: String, cuenta: String, tipo: String, monto: Long, categoria: String, nombre: String, dia: LocalDate,
        comercio: String? = null,
    ) = Events.insert {
        it[Events.id] = id; it[userId] = dueno; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
        it[currency] = "COP"; it[category] = categoria; it[description] = nombre; it[merchant] = comercio
        it[timestamp] = appDateToEpochMillis(dia) + 12 * 3_600_000L
        it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
    }

    private fun fila(id: String, rotulo: String, monto: Long, fecha: String, tipo: TransactionType = TransactionType.EXPENSE) =
        ParsedTransaction(
            id = id, date = fecha, merchant = rotulo, amount = monto, type = tipo,
            // Lo que el lector propone: nada que se parezca a las categorías del dueño.
            category = if (tipo == TransactionType.INCOME) "Transferencia" else "Otros",
            description = rotulo, rawText = rotulo,
        )

    private val cuatroPorMil = listOf(
        fila("f27", "IMPTO GOBIERNO 4X1000", 4_000, "2026-09-27"),
        fila("f28", "IMPTO GOBIERNO 4X1000", 5_000, "2026-09-28"),
        fila("f29", "IMPTO GOBIERNO 4X1000", 4_819, "2026-09-29"),
        fila("f30", "IMPTO GOBIERNO 4X1000", 4_000, "2026-09-30"),
    )

    private fun leer(filas: List<ParsedTransaction>, nombre: String = "extracto-${filas.hashCode()}.csv"): StatementParseResult {
        filasDelExtracto = filas
        val csv = "fecha,descripcion,valor\n" + filas.joinToString("\n") { "${it.date},${it.merchant},${it.amount}" }
        return runBlocking { procesarExtracto(dueno, nombre, "text/csv", csv.toByteArray(), log = { _, _ -> }) }
    }

    @Test
    fun `las filas de cargos llegan con la categoria del dueno, su tipo y marcadas aparte`() {
        val compra = fila("c1", "CARULLA RINCON OVIED", 50_000, "2026-10-01")
        val iva = fila("i1", "IVA CUOTA MANEJO", 4_112, "2026-10-16")
        val intereses = fila("a1", "ABONO INTERESES AHORROS", 13, "2026-10-02", TransactionType.INCOME)
        val resultado = leer(listOf(compra, iva, intereses))

        val porId = resultado.newTransactions.associateBy { it.id }
        assertEquals("Otros", porId.getValue("c1").category, "una compra no es un cargo: no se toca")
        assertEquals("Comisiones del banco", porId.getValue("i1").category)
        assertEquals(TransactionType.EXPENSE, porId.getValue("i1").type)
        assertEquals("Ingreso", porId.getValue("a1").category)
        assertEquals(TransactionType.INCOME, porId.getValue("a1").type)
        assertEquals(
            mapOf("i1" to ClaseDeCargo.COMISION, "a1" to ClaseDeCargo.RENDIMIENTO),
            resultado.cargosDelBanco.associate { it.parsedId to it.clase },
        )
        resultado.cargosDelBanco.forEach { assertEquals(EstadoDelCargo.Nuevo, estadoDelCargo(it, ahorros)) }
    }

    @Test
    fun `el 4x1000 ya anotado sumado por la cifra exacta sale ya anotado en su cuenta`() {
        val resultado = leer(cuatroPorMil)

        assertEquals(4, resultado.cargosDelBanco.size)
        assertTrue(resultado.newTransactions.all { it.category == "Impuestos" })
        resultado.cargosDelBanco.forEach { cargo ->
            val estado = estadoDelCargo(cargo, ahorros)
            assertIs<EstadoDelCargo.YaAnotado>(estado, cargo.parsedId)
            assertEquals("ev_4x1000", estado.en.eventoId)
            // En otra cuenta, el mismo movimiento no las cubre.
            assertEquals(EstadoDelCargo.Nuevo, estadoDelCargo(cargo, nu))
        }
    }

    @Test
    fun `si la suma no coincide exacto se proponen con el aviso`() {
        val otraSuma = cuatroPorMil.dropLast(1) + fila("f30", "IMPTO GOBIERNO 4X1000", 3_990, "2026-09-30")
        val resultado = leer(otraSuma)

        resultado.cargosDelBanco.forEach { cargo ->
            val estado = estadoDelCargo(cargo, ahorros)
            assertIs<EstadoDelCargo.PuedeEstarSumado>(estado, cargo.parsedId)
            assertEquals(17_819, estado.en.monto)
        }
    }

    @Test
    fun `una linea que el emparejador de siempre ya encontro no va como nueva`() {
        // La cuota de manejo del 16-sep ya está anotada por el mismo monto: es una coincidencia, no un cargo nuevo.
        val resultado = leer(listOf(fila("m1", "CUOTA MANEJO", 21_640, "2026-09-16")))
        assertEquals(listOf("ev_cuota"), resultado.matches.map { it.existingEventId })
        assertTrue(resultado.cargosDelBanco.isEmpty())
    }

    @Test
    fun `la memoria del dueno le gana a la categoria del cargo`() {
        transaction {
            evento("ev_mem", nu, "EXPENSE", 9_000, "Tarjetas", "Cuota de manejo Nu", LocalDate.of(2026, 8, 16), comercio = "CUOTA MANEJO TARJETA")
        }
        val resultado = leer(listOf(fila("m2", "CUOTA MANEJO TARJETA", 9_500, "2026-10-16")))
        assertEquals("Tarjetas", resultado.newTransactions.single().category)
        assertEquals(ClaseDeCargo.COMISION, resultado.cargosDelBanco.single().clase)
    }
}
