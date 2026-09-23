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
import com.jvillada.movi.shared.model.planDeUnaDeuda
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

    /**
     * **Cada categoría viaja con su proporción ya calculada.** El 23-sep el asistente escribió
     * «Cuota de crédito $12.920.200 (ya casi iguala todo lo que entró)» con ingresos de $22.217.770:
     * es el 58 %, no «casi». La cifra estaba bien y el verificador no tenía qué marcar — lo que
     * exageró fue la palabra. Con el porcentaje en el bloque, el modelo lo dice en vez de estimarlo
     * a ojo (y el verificador puede comprobar ese porcentaje).
     */
    @Test
    fun `cada categoria dice cuanto pesa sobre los gastos y sobre los ingresos`() {
        gasto("Cuota de crédito", 12_920_200)
        gasto("Hija", 7_079_800)
        transaction {
            Events.insert {
                it[Events.id] = "ingreso-1"; it[userId] = dueno; it[accountId] = "a1"
                it[type] = TransactionType.INCOME.name; it[amount] = 22_217_770L; it[currency] = "COP"
                it[category] = "Salario"; it[description] = "Salario"; it[timestamp] = ahora
                it[reconciliationStatus] = "RECONCILED"
            }
        }

        val texto = contexto()

        // 12.920.200 / 20.000.000 = 64,6 % de los gastos; 12.920.200 / 22.217.770 = 58,2 % de los ingresos.
        assertTrue("Cuota de crédito: \$12920200 (65 % de los gastos; 58 % de los ingresos)" in texto, texto)
        assertTrue("Hija: \$7079800 (35 % de los gastos; 32 % de los ingresos)" in texto, texto)
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

    /**
     * **Lo que Movi emparejó SOLO también está pagado.** El 23-sep el asistente le dijo al dueño
     * «todavía te faltan $291.677 de recurrentes (Tía Caro y Coomeva Familiar)» mientras su Inicio
     * decía «Pagaste los 12 del período»: esos pagos los había emparejado Movi automáticamente, y
     * eso no se escribe en `recurring_occurrences` — el contexto solo miraba los sellos guardados.
     *
     * Acá no hay ningún sello: solo la regla y un movimiento que se llama igual. La pantalla la da
     * por pagada; el contexto tiene que decir lo mismo.
     */
    @Test
    fun `un recurrente que Movi emparejo solo cuenta como ocurrido, sin sello guardado`() {
        val primeroDelMes = AppClock.now().toLocalDate().withDayOfMonth(1)
            .atTime(12, 0).atZone(AppClock.zone).toInstant().toEpochMilli()
        transaction {
            RecurringRules.insert {
                it[id] = "r-tia"; it[userId] = dueno; it[name] = "Tía Caro"; it[category] = "Familia"
                it[amount] = 100_000L; it[dayOfMonth] = 1; it[type] = TransactionType.EXPENSE.name
            }
            Events.insert {
                it[Events.id] = "ev-tia"
                it[userId] = dueno
                it[accountId] = "cta"
                it[type] = TransactionType.EXPENSE.name
                it[amount] = 100_000L
                it[currency] = "COP"
                it[category] = "Familia"
                it[description] = "Tía Caro"
                it[timestamp] = primeroDelMes
                it[reconciliationStatus] = "RECONCILED"
            }
        }

        val texto = contexto()
        val renglon = texto.lineSequence().first { "Tía Caro" in it && "ocurri" in it }

        assertTrue("YA ocurrió en este período" in renglon, "Movi lo emparejó solo; el contexto no puede decir que falta:\n$renglon")
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
        // «Seguros», no «seguro de vida»: el campo suma todos los seguros de la cuota.
        assertTrue("incluye seguros por \$52000 al mes" in texto, texto)
        assertFalse("seguro de vida" in texto, "el rótulo viejo le hizo decir al modelo algo falso:\n$texto")
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

    // ── Movi como asesor (entrega C de «Movi de un vistazo») ─────────────────────────────────

    /** Una deuda de verdad: la cuenta LOAN con su saldo DERIVADO de un movimiento, y sus condiciones. */
    private fun deuda(
        id: String,
        nombre: String,
        saldo: Long,
        tasa: Double,
        cuota: Long,
        pagaNomina: Boolean = false,
        laPaga: String? = null,
        otros: Long? = null,
        seguro: Long? = null,
    ) = transaction {
        Accounts.insert {
            it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = "LOAN"; it[balance] = 0L
        }
        // El saldo sale de los movimientos, como en la lista de Cuentas: un desembolso viejo, fuera
        // del período (y una cuenta LOAN nunca cuenta como gasto del mes de todos modos).
        Events.insert {
            it[Events.id] = "desembolso-$id"
            it[userId] = dueno
            it[accountId] = id
            it[type] = TransactionType.EXPENSE.name
            it[amount] = saldo
            it[currency] = "COP"
            it[category] = "Desembolso"
            it[description] = "Desembolso"
            it[timestamp] = ahora - 400L * 24 * 60 * 60 * 1000
            it[reconciliationStatus] = "RECONCILED"
        }
        Credits.insert {
            it[accountId] = id; it[userId] = dueno; it[bank] = "Banco"
            it[principal] = saldo; it[rateEa] = tasa; it[termMonths] = 120
            it[installment] = cuota; it[dayOfMonth] = 5; it[startDate] = "2024-01-05"
            it[payrollDeduction] = pagaNomina
            it[paidBy] = laPaga
            it[otrosCargosMensuales] = otros
            it[insuranceMonthly] = seguro
        }
    }

    /**
     * **Con datos parecidos a los del dueño, el contexto tiene lo que hace falta para aconsejar.**
     *
     * Es la prueba de la entrega C: una hipoteca que gira Skandia, una libranza que descuenta la
     * nómina, un vehículo que paga él, la casa como bien y un presupuesto de Fútbol pasado. Un
     * asesor sin cualquiera de esas piezas aconseja mal: sin quién paga, le pide recortar gastos
     * para una cuota que no sale de su cuenta; sin la casa, le dice que su patrimonio es −$2.074M;
     * sin lo gastado del presupuesto, tiene que restar él (y resta mal).
     */
    @Test
    fun `con datos como los del dueno, el contexto lleva tasas, quien paga, bienes, patrimonio y presupuestos`() {
        deuda("h1254", "Hipoteca 1254", 400_000_000L, tasa = 12.0, cuota = 4_500_000L, laPaga = "Skandia")
        deuda("lib", "Libranza 5521", 30_000_000L, tasa = 16.0, cuota = 1_000_000L, pagaNomina = true)
        deuda("v8761", "Vehículo 8761", 40_000_000L, tasa = 18.5, cuota = 1_500_000L, otros = 25_000L)
        transaction {
            Accounts.insert {
                it[id] = "casa"; it[userId] = dueno; it[name] = "Casa Almendros"; it[type] = "INVESTMENT"
                it[balance] = 0L
                it[assetKind] = "INMUEBLE"; it[assetValue] = 1_411_903_920L
                it[assetValuedOn] = "2026-08-28"; it[assetDebtId] = "h1254"
            }
            Budgets.insert { it[userId] = dueno; it[category] = "Fútbol"; it[monthlyLimit] = 400_000L }
            Budgets.insert { it[userId] = dueno; it[category] = "Mercado"; it[monthlyLimit] = 1_000_000L }
        }
        gasto("Fútbol", 963_456)
        gasto("Mercado", 600_000)

        val texto = contexto()

        // Las tasas, y en orden de la más alta a la más baja: el orden ya es media respuesta a
        // «¿qué deuda abono primero?».
        assertTrue("tasa 18.5 % EA" in texto && "tasa 16.0 % EA" in texto && "tasa 12.0 % EA" in texto, texto)
        assertTrue(
            texto.indexOf("Vehículo 8761") < texto.indexOf("Libranza 5521") &&
                texto.indexOf("Libranza 5521") < texto.indexOf("- Hipoteca 1254"),
            "los créditos van de la tasa más alta a la más baja:\n$texto",
        )
        // Quién paga cada cuota, en palabras, también cuando es él.
        assertTrue("La cuota la paga Skandia: NO sale de su cuenta." in texto, texto)
        assertTrue("La cuota la descuenta la nómina antes de que llegue el sueldo: NO sale de su cuenta." in texto)
        assertTrue("La cuota sale de su bolsillo." in texto)
        assertTrue("incluye otros cargos \$25000 al mes" in texto)
        // El saldo y lo que hace la cuota con él, con la MISMA cuenta que la pantalla de Créditos.
        assertTrue("Vehículo 8761 (Banco): debe \$40000000;" in texto, texto)
        val interesDelVehiculo = planDeUnaDeuda(40_000_000L, 18.5, 1_500_000L, null, 25_000L, saleDeTuBolsillo = true).interes
        assertTrue("unos \$$interesDelVehiculo son interés este mes" in texto, "falta el interés del mes:\n$texto")
        // Los totales partidos por quién paga: uno solo le cobraría a su bolsillo las cuotas de Skandia.
        assertTrue("Cuotas al mes: \$1500000 salen de su bolsillo; \$5500000 las paga la nómina o un tercero" in texto, texto)
        assertTrue("Intereses estimados de un mes: \$$interesDelVehiculo en los créditos que salen de su bolsillo" in texto, texto)
        // Bienes y patrimonio.
        assertTrue("Casa Almendros (BIEN" in texto && "vale \$1411903920" in texto, texto)
        assertTrue("lo financia «Hipoteca 1254»" in texto)
        assertTrue("- Bienes (inmuebles, vehículos; no es plata): \$1411903920" in texto, texto)
        assertTrue("Patrimonio neto" in texto)
        // Presupuestos con lo gastado y la resta hecha.
        assertTrue("- Fútbol: límite \$400000, gastado \$963456 — SE PASÓ por \$563456" in texto, texto)
        assertTrue("- Mercado: límite \$1000000, gastado \$600000 — le quedan \$400000" in texto, texto)
    }

    /**
     * Un crédito cuya cuota no cubre los intereses se dice con todas las letras: es lo más urgente
     * que un asesor puede ver, y la pantalla de Créditos ya lo dice.
     */
    @Test
    fun `una deuda que crece se nombra como tal`() {
        deuda("mama", "Crédito Mamá", 50_000_000L, tasa = 30.0, cuota = 100_000L)

        val texto = contexto()

        assertTrue("la deuda CRECE aunque pague" in texto, texto)
    }

    /**
     * **El caso real del 23-sep: el Hipotecario 2334.** Saldo $204.183.376 al 15,24 % EA, cuota
     * $2.613.714 que incluye $209.219 de seguros. La cuota SÍ es mayor que los intereses del mes
     * (unos $2.427.883); lo que no los alcanza es lo que queda de la cuota DESPUÉS del seguro
     * ($2.404.495). El contexto decía «la cuota no alcanza a cubrir los intereses», el modelo lo
     * repitió y en el renglón siguiente mostró que la cuota era más grande: una respuesta que se
     * contradice sola. El renglón tiene que traer la cuenta hecha, con el seguro adentro.
     */
    @Test
    fun `una deuda que crece por el seguro dice la cuenta con el seguro, no una frase falsa`() {
        deuda("h2334", "Hipotecario 2334", 204_183_376L, tasa = 15.24, cuota = 2_613_714L, laPaga = "Skandia", seguro = 209_219L)

        val texto = contexto()
        val renglon = texto.lineSequence().first { it.startsWith("- Hipotecario 2334") }

        assertFalse("la cuota no alcanza a cubrir los intereses" in renglon, renglon)
        val plan = planDeUnaDeuda(204_183_376L, 15.24, 2_613_714L, 209_219L, null, saleDeTuBolsillo = false)
        val queda = 2_613_714L - 209_219L
        val crece = plan.interes - queda
        assertTrue(crece > 0, "con estos números la deuda crece: interés ${plan.interes}, queda $queda")
        assertTrue("después de \$209219 de seguros le quedan \$$queda" in renglon, renglon)
        assertTrue("intereses del mes son unos \$${plan.interes}" in renglon, renglon)
        assertTrue("la deuda CRECE aunque pague (unos \$$crece al mes)" in renglon, renglon)
    }

    /**
     * «Skandia» paga dos hipotecas del dueño, y Skandia es **su** fondo de pensión voluntaria: una
     * cuenta suya en Movi. Dicho como «la paga Skandia» a secas, el modelo la llamó «tu seguro» y le
     * dijo que no estaba poniendo plata suya. Cuando quien paga se llama como una cuenta del dueño,
     * el renglón lo dice.
     */
    @Test
    fun `si quien paga es una cuenta del dueno, se dice que es plata suya`() {
        transaction {
            Accounts.insert {
                it[id] = "skandia"; it[userId] = dueno; it[name] = "Skandia pensión voluntaria"
                it[type] = "INVESTMENT"; it[balance] = 0L; it[conditionedTo] = "pensión voluntaria"
            }
        }
        deuda("h1254", "Hipoteca 1254", 400_000_000L, tasa = 11.0, cuota = 9_147_408L, laPaga = "Skandia")

        val renglon = contexto().lineSequence().first { it.startsWith("- Hipoteca 1254") }

        assertTrue("con plata de su propia cuenta «Skandia pensión voluntaria»" in renglon, renglon)
        assertTrue("no es un seguro ni un tercero" in renglon, renglon)
    }

    /** Sin saldo derivado (la deuda no tiene movimientos) no se estima interés sobre un cero. */
    @Test
    fun `sin saldo no se inventa el interes del mes`() {
        transaction {
            Accounts.insert { it[id] = "sin"; it[userId] = dueno; it[name] = "Crédito nuevo"; it[type] = "LOAN"; it[balance] = 0L }
            Credits.insert {
                it[accountId] = "sin"; it[userId] = dueno; it[bank] = "Banco"
                it[principal] = 10_000_000L; it[rateEa] = 20.0; it[termMonths] = 12
                it[installment] = 900_000L; it[dayOfMonth] = 5; it[startDate] = "2026-09-01"
            }
        }

        val texto = contexto()

        assertTrue("Crédito nuevo (Banco): cuota" in texto, "sin desembolso no se afirma «debe \$0»:\n$texto")
        assertFalse("son interés este mes" in texto, "sin deuda no hay interés que estimar:\n$texto")
    }

    /** Lo que falta de los recurrentes va sumado: «¿me alcanza?» se contesta con ese número. */
    @Test
    fun `el total de recurrentes pendientes viaja sumado`() {
        transaction {
            RecurringRules.insert {
                it[id] = "r1"; it[userId] = dueno; it[name] = "Arriendo"; it[category] = "Vivienda"
                it[amount] = 1_850_000L; it[dayOfMonth] = 5; it[type] = TransactionType.EXPENSE.name
            }
            RecurringRules.insert {
                it[id] = "r2"; it[userId] = dueno; it[name] = "Internet"; it[category] = "Servicios"
                it[amount] = 120_000L; it[dayOfMonth] = 10; it[type] = TransactionType.EXPENSE.name
            }
        }

        val texto = contexto()

        assertTrue("Total de gastos recurrentes que TODAVÍA no ocurrieron en este período: \$1970000" in texto, texto)
    }
}
