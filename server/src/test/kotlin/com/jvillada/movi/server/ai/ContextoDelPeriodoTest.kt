package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.routes.buildUserContext
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.YearMonth
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Movi AI tiene que saber de la plata del dueño lo mismo que sabe la pantalla.**
 *
 * Él lo pidió así: *«el asistente de IA debería poder tener el contexto de todos los datos de la
 * app, no ser un chat de IA y ya»*. Antes llevaba tres cifras del mes, las cuentas, los
 * presupuestos y los documentos; con eso no podía contestar en qué se fue la plata, qué recurrente
 * le falta pagar ni a qué tasa está endeudado.
 *
 * Estas pruebas fijan lo que entra Y lo que NO: un anulado o algo en «Por confirmar» no cuentan,
 * porque tampoco cuentan en el Inicio, y un asistente que diga otra cifra que la pantalla es peor
 * que uno que no sepa.
 */
class ContextoDelPeriodoTest {

    private val dueno = "user-contexto-periodo"
    private val ahora = AppClock.now().toInstant().toEpochMilli()

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:contexto_periodo_ai;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            // La base en memoria sobrevive entre métodos (`DB_CLOSE_DELAY=-1`), así que cada uno
            // arranca borrando: si no, el segundo test choca contra el usuario que insertó el
            // primero. Solo las tablas de esta clase, en su propia base.
            SchemaUtils.drop(
                Documents, VoidEvents, Events, Budgets, Goals, Subscriptions, SmsMessages,
                RecurringOccurrences, OccurrenceRejections, RecurringRules, Credits, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, Budgets, Documents, RecurringRules,
                RecurringOccurrences, OccurrenceRejections, Credits, Subscriptions, Goals, SmsMessages,
            )
            Users.insert {
                it[id] = dueno
                it[email] = "dueno@contexto.test"
                it[name] = "Camilo"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = "a1"
                it[userId] = dueno
                it[name] = "Ahorros Nómina"
                it[type] = "SAVINGS"
                it[balance] = 5_000_000L
            }
        }
    }

    private fun gasto(categoria: String, monto: Long, estado: String = "RECONCILED", id: String = "e-$categoria-$monto") =
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = dueno
                it[accountId] = "a1"
                it[type] = TransactionType.EXPENSE.name
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = categoria
                it[timestamp] = ahora
                it[reconciliationStatus] = estado
            }
        }

    private fun contexto() = runBlocking { buildUserContext(dueno) }

    @Test
    fun `dice en que se fue la plata del periodo, por categoria`() {
        gasto("Mercado", 612_300)
        gasto("Vivienda", 1_850_000)

        val texto = contexto()

        assertTrue("En qué se fue la plata este período" in texto)
        assertTrue("Vivienda: \$1850000" in texto, "falta la categoría más pesada:\n$texto")
        assertTrue("Mercado: \$612300" in texto)
    }

    /** Un movimiento anulado no cuenta en el Inicio; tampoco puede contar acá. */
    @Test
    fun `lo anulado no entra en el contexto`() {
        gasto("Mercado", 500_000, id = "e-anulado")
        transaction {
            VoidEvents.insert {
                it[id] = "v1"
                it[userId] = dueno
                it[originalEventId] = "e-anulado"
                it[timestamp] = ahora
            }
        }

        assertFalse("Mercado" in contexto(), "un anulado no puede aparecer como gasto")
    }

    /** «Por confirmar» tampoco: la pantalla lo deja afuera de las cifras hasta que el dueño decide. */
    @Test
    fun `lo que espera en Por confirmar no entra`() {
        gasto("Restaurantes", 42_500, estado = "UNCONFIRMED")
        assertFalse("Restaurantes: " in contexto())
    }

    @Test
    fun `los recurrentes van con su dia y con si ya ocurrieron en este periodo`() {
        transaction {
            RecurringRules.insert {
                it[id] = "r1"; it[userId] = dueno; it[name] = "Arriendo"; it[category] = "Vivienda"
                it[amount] = 1_850_000L; it[dayOfMonth] = 5; it[type] = TransactionType.EXPENSE.name
            }
            RecurringRules.insert {
                it[id] = "r2"; it[userId] = dueno; it[name] = "Gimnasio"; it[category] = "Salud"
                it[amount] = 139_900L; it[dayOfMonth] = 20; it[type] = TransactionType.EXPENSE.name
            }
            val periodo = "%04d-%02d".format(AppClock.now().year, AppClock.now().monthValue)
            RecurringOccurrences.insert {
                it[userId] = dueno; it[ruleId] = "r1"; it[period] = periodo
                it[eventId] = null; it[confirmedAt] = ahora
            }
        }

        val texto = contexto()

        assertTrue("Arriendo" in texto && "YA ocurrió en este período" in texto)
        assertTrue("Gimnasio" in texto && "TODAVÍA no ocurrió en este período" in texto)
    }

    // ── El sello del período, con el corte del dueño ─────────────────────────

    /** El corte del dueño es 25, no 1. Sin esto, todas estas pruebas corren sobre el mes de calendario. */
    private fun conCorte25() = transaction {
        Users.update({ Users.id eq dueno }) { it[periodCutoffDay] = 25 }
    }

    /**
     * El mes con el que se sella la cuota de un recurrente del día 5 cuando el corte es 25: el del
     * VENCIMIENTO. El período va del 25 de un mes al 24 del siguiente, así que la cuota del día 5
     * siempre cae en el mes de cierre — nunca en el del arranque.
     */
    private fun mesDelVencimientoDeUnDia5(): YearMonth {
        val hoy = AppClock.now().toLocalDate()
        return if (hoy.dayOfMonth >= 25) YearMonth.from(hoy).plusMonths(1) else YearMonth.from(hoy)
    }

    private fun arriendoDia5(sellado: String?) = transaction {
        RecurringRules.insert {
            it[id] = "r-arriendo"; it[userId] = dueno; it[name] = "Arriendo"; it[category] = "Vivienda"
            it[amount] = 1_850_000L; it[dayOfMonth] = 5; it[type] = TransactionType.EXPENSE.name
        }
        if (sellado != null) {
            RecurringOccurrences.insert {
                it[userId] = dueno; it[ruleId] = "r-arriendo"; it[period] = sellado
                it[eventId] = null; it[confirmedAt] = ahora
            }
        }
    }

    /**
     * **El sello es el del vencimiento, y el vencimiento no vive en el mes del arranque.**
     *
     * Con corte 25 el período va del 25 de agosto al 24 de septiembre, pero un arriendo del día 5
     * vence el 5 de septiembre y `recurring_occurrences.period` lo sella «2026-09» (ver
     * `periodOf`). El contexto preguntaba por el mes del ARRANQUE —«2026-08»—, así que no
     * encontraba el sello y le hacía decir al asistente que el arriendo no está pagado cuando sí
     * lo está. El dueño usa corte 25: no es un caso de borde, es su caso.
     */
    @Test
    fun `con corte 25, el arriendo sellado en el mes del vencimiento se lee como pagado`() {
        conCorte25()
        arriendoDia5(sellado = mesDelVencimientoDeUnDia5().toString())

        val texto = contexto()

        assertTrue("Arriendo" in texto)
        assertTrue("YA ocurrió en este período" in texto, "el sello del vencimiento es el que manda:\n$texto")
    }

    /** Y al revés: un sello del mes del ARRANQUE es de otra cuota, no de esta. */
    @Test
    fun `con corte 25, un sello del mes del arranque no da por pagada la cuota en juego`() {
        conCorte25()
        arriendoDia5(sellado = mesDelVencimientoDeUnDia5().minusMonths(1).toString())

        val texto = contexto()

        assertTrue("TODAVÍA no ocurrió en este período" in texto, "ese sello es de la cuota anterior:\n$texto")
    }

    // ── Suscripciones ────────────────────────────────────────────────────────

    private fun suscripcion(
        id: String,
        nombre: String,
        monto: Long,
        moneda: String = "COP",
        periodicidad: String = "MENSUAL",
        estado: String = "CONFIRMED",
        dia: Int = 5,
    ) = transaction {
        Subscriptions.insert {
            it[Subscriptions.id] = id
            it[userId] = dueno
            it[merchantKey] = "key-$id"
            it[displayName] = nombre
            it[amount] = monto
            it[currency] = moneda
            it[dayOfMonth] = dia
            it[status] = estado
            it[confidence] = "HIGH"
            it[firstSeen] = ahora
            it[lastSeen] = ahora
            it[occurrences] = 3
            it[Subscriptions.periodicidad] = periodicidad
        }
    }

    /**
     * El monto mensual que el texto le atribuye a una suscripción, en pesos — del renglón
     * «- Nombre: $44900 al mes, el día 5».
     */
    private fun montoMensualEnElTexto(texto: String, nombre: String): Long {
        val renglon = texto.lines().firstOrNull { it.startsWith("- $nombre:") }
            ?: error("«$nombre» no está en el contexto:\n$texto")
        return renglon.substringAfter("\$").takeWhile { it.isDigit() }.toLongOrNull()
            ?: error("el renglón no dice un monto mensual: $renglon")
    }

    /**
     * **Un cobro en dólares no son pesos.** `amount` está en la moneda nativa: US$12 llegaba al
     * asistente como «$12» y él lo leía como doce pesos. Se convierte con la TRM, igual que
     * `SubscriptionRoutes.resultFor`, y el cobro real se dice aparte para que el dueño reconozca
     * lo que ve en su extracto.
     */
    @Test
    fun `una suscripcion en dolares llega convertida a pesos`() {
        suscripcion("s-usd", "Spotify", 12L, moneda = "USD")

        val texto = contexto()

        assertTrue("(el cobro real es USD \$12 al mes" in texto, texto)
        // La TRM viene de la red (o del respaldo del código); lo que se fija acá es que NO se
        // dijeron doce pesos. Con la tasa más baja que el servicio acepta ($100) ya son $1.200.
        assertTrue(
            montoMensualEnElTexto(texto, "Spotify") >= 1_200L,
            "doce dólares no pueden llegar como doce pesos:\n$texto",
        )
    }

    /**
     * **Un cobro anual no es un gasto del mes.** Se prorratea con [montoMensualEquivalente]
     * —redondeando hacia arriba, igual que el cliente y que `/api/subscriptions`—, y el cobro real
     * se dice aparte: el dueño ve $112.900 una vez al año en su extracto, no $9.409 todos los
     * meses.
     */
    @Test
    fun `un cobro anual llega prorrateado al mes, y dice cuanto es el cobro real`() {
        suscripcion("s-anual", "NBA League Pass", 112_900L, periodicidad = "ANUAL", dia = 3)

        val texto = contexto()

        assertEquals(9_409L, montoMensualEnElTexto(texto, "NBA League Pass"), texto)
        assertTrue("(el cobro real es \$112900 una vez al año)" in texto, texto)
    }

    /**
     * **El total va sumado desde el server.** Con los diez renglones correctos delante, el modelo
     * contestó $880.361 donde la suma era $900.295 (medido el 17-sep en el teléfono del dueño):
     * sumar diez números es justo lo que un modelo hace mal. Ahora el total viaja hecho.
     */
    @Test
    fun `el total de las suscripciones viaja sumado, no lo suma el modelo`() {
        suscripcion("s-net", "Netflix", 44_900L, dia = 19)
        suscripcion("s-yt", "YouTube Premium", 47_900L, dia = 29)
        suscripcion("s-nba", "NBA League Pass", 112_900L, periodicidad = "ANUAL", dia = 16)
        suscripcion("s-cand", "Claro Video", 24_900L, estado = "CANDIDATE")

        val texto = contexto()

        // 44.900 + 47.900 + 9.409 (la anual prorrateada); la candidata no entra, como en resultFor.
        assertTrue("Total de suscripciones activas al mes: \$102209" in texto, texto)
    }

    /**
     * Una CANDIDATE es una sospecha del detector que el dueño todavía no aceptó. Dicha como un
     * hecho, le pone al asistente en la boca un gasto que quizá no existe — y `resultFor`, que es
     * lo que el dueño ve en pantalla, tampoco la cuenta.
     */
    @Test
    fun `una suscripcion candidata no se cuenta como un hecho`() {
        suscripcion("s-cand", "Claro Video", 24_900L, estado = "CANDIDATE")
        suscripcion("s-auto", "Netflix", 44_900L, estado = "AUTO")

        val texto = contexto()

        assertTrue("Netflix" in texto, "una AUTO sí va")
        assertFalse("Claro Video" in texto, "una candidata no puede llegar como un hecho:\n$texto")
    }

    @Test
    fun `un credito viaja con su tasa, su cuota y su plazo`() {
        transaction {
            Accounts.insert {
                it[id] = "a2"; it[userId] = dueno; it[name] = "Vehículo 8761"; it[type] = "LOAN"; it[balance] = 40_000_000L
            }
            Credits.insert {
                it[accountId] = "a2"; it[userId] = dueno; it[bank] = "Banco de ejemplo"
                it[principal] = 60_000_000L; it[rateEa] = 18.5; it[termMonths] = 60
                it[installment] = 4_101_123L; it[dayOfMonth] = 15; it[startDate] = "2024-01-15"
                it[insuranceMonthly] = 52_000L
            }
        }

        val texto = contexto()

        assertTrue("Vehículo 8761" in texto)
        assertTrue("18.5 % EA" in texto, "sin la tasa no se puede opinar de una deuda:\n$texto")
        assertTrue("cuota \$4101123" in texto)
        assertTrue("seguro de vida \$52000" in texto)
    }

    @Test
    fun `el periodo del dueno se nombra, no el mes de calendario`() {
        val texto = contexto()
        assertTrue("El período en curso" in texto)
        assertTrue("cierra su mes el día" in texto)
        assertTrue("Quedan" in texto)
    }

    @Test
    fun `lo que espera al dueno se cuenta aparte`() {
        gasto("Mercado", 10_000, estado = "UNCONFIRMED", id = "e-pendiente")
        transaction {
            SmsMessages.insert {
                it[id] = "s1"; it[userId] = dueno; it[time] = "2026-09-14 10:00"; it[bank] = "Bancolombia"
                it[text] = "Compra por \$10.000"; it[state] = "pending"; it[det] = ""
            }
        }

        val texto = contexto()

        assertTrue("Lo que está esperando al dueño" in texto)
        assertTrue("1 mensajes del banco sin confirmar" in texto)
        assertTrue("1 movimientos en «Por confirmar»" in texto)
    }

    /**
     * **Los movimientos uno por uno ya no van en el contexto.** Estuvieron unas horas: eran 126
     * renglones viajando en CADA mensaje para contestar una pregunta de cada cinco. Desde que el
     * asistente puede consultarlos (`buscar_movimientos`), mandarlos siempre es pagar por
     * adelantado algo que casi nunca se usa — y el dueño pidió que esto fuera barato.
     *
     * Lo que sí queda es el total por categoría, que es lo que contesta «¿en qué se me fue?» sin
     * una sola consulta.
     */
    @Test
    fun `el contexto lleva los totales, no los movimientos uno por uno`() {
        gasto("Mercado", 612_300)
        gasto("Vivienda", 1_850_000)

        val texto = contexto()

        assertTrue("Vivienda: \$1850000" in texto)
        assertFalse("uno por uno" in texto, "ese bloque se fue:\n$texto")
    }

    @Test
    fun `sin datos el contexto no inventa nada`() {
        val texto = contexto()
        assertTrue("(todavía sin gastos registrados en este período)" in texto)
        assertTrue("(sin recurrentes cargados)" in texto)
        assertFalse("Créditos, con sus condiciones" in texto, "sin créditos, la sección no va")
    }
}
