package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.gastoVariablePorDia
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La tarjeta «Disponible» no puede contar un pago del checklist dos veces: una en los fijos y otra
 * en el gasto variable. Ver `PagosDelChecklist.kt`.
 *
 * El primer test es el período de septiembre del dueño (25-ago a 24-sep, corte 25), con sus reglas,
 * sus sellos y sus pagos tal como están en producción el 22-sep. Las categorías de las reglas no
 * venían en el dato: se pusieron las que usan sus movimientos.
 */
class PagosDelChecklistTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val hoy = LocalDate.of(2026, 9, 22)

    private fun regla(id: String, nombre: String, categoria: String, monto: Long, dia: Int) =
        RecurringRule(id, nombre, categoria, monto, dia, TransactionType.EXPENSE)

    private fun evento(
        id: String,
        descripcion: String,
        monto: Long,
        fecha: String,
        categoria: String,
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
    ) = FinancialEvent(
        id = id, accountId = "ahorros", type = TransactionType.EXPENSE, amount = monto, currency = "COP",
        category = categoria, description = descripcion,
        // Mediodía: la medianoche exacta queda en el borde de un día.
        timestamp = appDateToEpochMillis(LocalDate.parse(fecha)) + 12 * 3_600_000L,
        reconciliationStatus = estado, countsAsCashFlow = true,
    )

    private fun sello(ruleId: String, periodo: String, eventId: String?) =
        RecurringOccurrence(ruleId = ruleId, period = periodo, eventId = eventId, confirmedAt = 0L)

    /** Lo que hace `loadOccurredBy` con sellos cuyos movimientos están todos vivos. */
    private fun ocurridosDe(sellos: List<RecurringOccurrence>) =
        sellos.groupBy { it.ruleId }.mapValues { (_, v) -> v.map { it.period }.toSet() }

    private fun parteFija(
        reglas: List<RecurringRule>,
        sellos: List<RecurringOccurrence>,
        eventos: List<FinancialEvent>,
        hoy: LocalDate = this.hoy,
        settings: PeriodSettings = corte25,
    ) = parteFijaDelChecklist(reglas, sellos, ocurridosDe(sellos), eventos, hoy, settings)

    private fun gastoTotal(eventos: List<FinancialEvent>, parte: Map<String, Long>) =
        gastoVariablePorDia(eventos, parte) { epochMillisToAppDateString(it) }.values.sum()

    // ── El período del dueño ─────────────────────────────────────────────────

    private val reglasDelDueno = listOf(
        regla("rr_celular", "Celular", "Servicios", 53_077, 22),
        regla("rr_colegio", "Colegio Hija", "Hija", 4_000_000, 25),
        regla("rr_coomeva", "Coomeva Familiar", "Salud", 138_600, 30),
        regla("rr_mama", "Crédito Mamá", "Crédito", 1_300_000, 27),
        regla("rr_papa", "Crédito Papá", "Crédito", 4_280_000, 27),
        regla("rr_gym_cami", "Gimnasio Cami", "Gimnasio", 180_000, 5),
        regla("rr_gym_caro", "Gimnasio Caro", "Gimnasio", 180_000, 25),
        regla("rr_mercado", "Mercado", "Mercado", 2_000_000, 25),
        regla("rr_tia", "Tía Caro", "Familia", 100_000, 1),
    )

    private val sellosDelDueno = listOf(
        sello("rr_colegio", "2026-08", "ev_2f79"),
        sello("rr_gym_cami", "2026-09", null),
        sello("rr_gym_caro", "2026-08", "ev_f02f"),
        sello("rr_mercado", "2026-08", null),
        // Un sello viejo de una regla que ya no existe.
        sello("rr_a377", "2026-08", "ev_e528"),
    )

    private val pagosDelDueno = listOf(
        evento("ev_papa", "Crédito Papá", 4_280_000, "2026-08-27", "Crédito"),
        evento("ev_e528", "Crédito Mamá", 1_300_000, "2026-08-27", "Crédito"),
        evento("ev_mercado", "Mercado", 2_000_000, "2026-08-27", "Mercado"),
        evento("ev_2f79", "Colegio Hija", 3_000_000, "2026-08-31", "Hija"),
        evento("ev_colegio_parte", "Colegio Hija · parte desde Bancolombia", 1_000_000, "2026-08-31", "Hija"),
        evento("ev_gym_caro", "Gimnasio Caro", 180_000, "2026-08-25", "Gimnasio"),
        evento("ev_f02f", "Gimnasio", 180_000, "2026-08-27", "Gimnasio"),
        evento("ev_gym_cami", "Gimnasio Cami", 180_000, "2026-09-05", "Gimnasio"),
        evento("ev_coomeva", "Coomeva Familiar", 138_600, "2026-09-05", "Salud"),
        evento("ev_tia", "Tía Caro", 100_000, "2026-09-01", "Familia"),
        // Un gasto variable de verdad, para ver que sigue contando.
        evento("ev_almuerzo", "Almuerzo", 45_000, "2026-09-10", "Comida"),
    )

    @Test
    fun `las nueve reglas del dueno estan en el checklist de septiembre y suman 12,2M de fijos`() {
        val vencimientos = reglasDelDueno.associate { r ->
            r.name to vencimientoEnElChecklist(r, hoy, corte25, ocurridosDe(sellosDelDueno)[r.id].orEmpty())
        }
        assertEquals(
            mapOf(
                "Celular" to LocalDate.of(2026, 9, 22),
                "Colegio Hija" to LocalDate.of(2026, 8, 25),
                "Coomeva Familiar" to LocalDate.of(2026, 8, 30),
                "Crédito Mamá" to LocalDate.of(2026, 8, 27),
                "Crédito Papá" to LocalDate.of(2026, 8, 27),
                "Gimnasio Cami" to LocalDate.of(2026, 9, 5),
                "Gimnasio Caro" to LocalDate.of(2026, 8, 25),
                "Mercado" to LocalDate.of(2026, 8, 25),
                "Tía Caro" to LocalDate.of(2026, 9, 1),
            ),
            vencimientos,
        )
        // Los fijos de las reglas reales: las nueve por su monto, pagadas o no.
        assertEquals(12_231_677L, reglasDelDueno.sumOf { it.amount })
    }

    @Test
    fun `antes, $7,88M de pagos de fijos contaban tambien como gasto variable`() {
        // La regla de antes: fuera solo los movimientos atados a un sello, enteros.
        val antes = sellosDelDueno.mapNotNull { it.eventId }.associateWith { Long.MAX_VALUE }
        val variables = pagosDelDueno.filter { (antes[it.id] ?: 0L) == 0L }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "ev_papa", "ev_mercado", "ev_colegio_parte", "ev_gym_caro", "ev_gym_cami",
                "ev_coomeva", "ev_tia", "ev_almuerzo",
            ),
            variables,
        )
        assertEquals(7_878_600L + 45_000L, gastoTotal(pagosDelDueno, antes))
    }

    @Test
    fun `ahora cada pago del checklist sale del variable, y el segundo gimnasio de Caro no`() {
        val parte = parteFija(reglasDelDueno, sellosDelDueno, pagosDelDueno)
        assertEquals(
            mapOf(
                "ev_2f79" to 3_000_000L,          // Colegio, su sello
                "ev_colegio_parte" to 1_000_000L, // Colegio, lo que le faltaba
                "ev_f02f" to 180_000L,            // Gimnasio Caro, su sello
                "ev_mercado" to 2_000_000L,       // Mercado, sellado sin movimiento
                "ev_gym_cami" to 180_000L,        // Gimnasio Cami, sellado sin movimiento
                "ev_papa" to 4_280_000L,          // Crédito Papá, sin sellar
                "ev_e528" to 1_300_000L,          // Crédito Mamá, sin sellar (el sello huérfano no cuenta)
                "ev_coomeva" to 138_600L,         // Coomeva, sin sellar
                "ev_tia" to 100_000L,             // Tía Caro, sin sellar
            ),
            parte,
        )
        // Queda el gimnasio de Caro del 25-ago: la regla ya está completa con el del 27, así que
        // ese segundo pago de $180.000 es gasto de más, no un fijo. Y el almuerzo.
        assertEquals(180_000L + 45_000L, gastoTotal(pagosDelDueno, parte))
    }

    // ── Los casos, uno por uno ───────────────────────────────────────────────

    @Test
    fun `sellado sin movimiento saca su mejor candidato`() {
        val mercado = regla("rr_mercado", "Mercado", "Mercado", 2_000_000, 25)
        val parte = parteFija(
            listOf(mercado),
            listOf(sello("rr_mercado", "2026-08", null)),
            listOf(
                evento("otro", "Éxito", 1_900_000, "2026-08-28", "Mercado"),
                evento("el-mercado", "Mercado", 2_000_000, "2026-08-27", "Mercado"),
            ),
        )
        // El que se llama igual gana; el otro, con la regla ya completa, sigue siendo variable.
        assertEquals(mapOf("el-mercado" to 2_000_000L), parte)
    }

    @Test
    fun `un pago en dos movimientos saca las dos partes y nada mas`() {
        val colegio = regla("rr_colegio", "Colegio Hija", "Hija", 4_000_000, 25)
        val eventos = listOf(
            evento("tres", "Colegio Hija", 3_000_000, "2026-08-31", "Hija"),
            evento("uno", "Colegio Hija · parte desde Bancolombia", 1_000_000, "2026-08-31", "Hija"),
            evento("uniforme", "Uniforme", 250_000, "2026-09-02", "Hija"),
        )
        val parte = parteFija(listOf(colegio), listOf(sello("rr_colegio", "2026-08", "tres")), eventos)
        assertEquals(mapOf("tres" to 3_000_000L, "uno" to 1_000_000L), parte)
        assertEquals(250_000L, gastoTotal(eventos, parte))
    }

    @Test
    fun `sin sello, las dos partes salen igual`() {
        val colegio = regla("rr_colegio", "Colegio Hija", "Hija", 4_000_000, 25)
        val eventos = listOf(
            evento("tres", "Colegio Hija", 3_000_000, "2026-08-31", "Hija"),
            evento("uno", "Colegio Hija · parte desde Bancolombia", 1_000_000, "2026-08-31", "Hija"),
        )
        assertEquals(0L, gastoTotal(eventos, parteFija(listOf(colegio), emptyList(), eventos)))
    }

    @Test
    fun `un segundo gimnasio del mismo monto que no es el pago de ninguna regla sigue contando`() {
        val gimnasio = regla("rr_gym", "Gimnasio Caro", "Gimnasio", 180_000, 25)
        val eventos = listOf(
            evento("el-gym", "Gimnasio Caro", 180_000, "2026-08-25", "Gimnasio"),
            evento("otro-gym", "Gimnasio", 180_000, "2026-08-27", "Gimnasio"),
            // Y uno que no se parece a ninguna regla.
            evento("clase", "Clase de yoga", 180_000, "2026-08-26", "Deporte"),
        )
        val parte = parteFija(listOf(gimnasio), listOf(sello("rr_gym", "2026-08", null)), eventos)
        assertEquals(mapOf("el-gym" to 180_000L), parte)
        assertEquals(360_000L, gastoTotal(eventos, parte))
    }

    @Test
    fun `un pago mas grande que el fijo deja la diferencia como variable`() {
        val gimnasio = regla("rr_gym", "Gimnasio Caro", "Gimnasio", 180_000, 25)
        val eventos = listOf(evento("gym", "Gimnasio Caro", 200_000, "2026-08-25", "Gimnasio"))
        val parte = parteFija(listOf(gimnasio), listOf(sello("rr_gym", "2026-08", "gym")), eventos)
        assertEquals(mapOf("gym" to 180_000L), parte)
        assertEquals(20_000L, gastoTotal(eventos, parte))
    }

    /** El error contrario: sacar del variable algo que los fijos no cuentan. */
    @Test
    fun `un sello de una regla borrada no saca nada por si solo`() {
        val eventos = listOf(evento("viejo", "Arriendo", 1_800_000, "2026-09-01", "Vivienda"))
        val parte = parteFija(emptyList(), listOf(sello("rr_borrada", "2026-09", "viejo")), eventos)
        assertEquals(emptyMap(), parte)
        assertEquals(1_800_000L, gastoTotal(eventos, parte))
    }

    @Test
    fun `una regla cuyo vencimiento no cae en el periodo no reclama nada`() {
        // El 5-sep, con corte 25, el período va del 25-ago al 24-sep. Una regla que arranca el
        // 10-sep (creada desde un movimiento) todavía no tiene ocurrencia en él.
        val arriendo = RecurringRule(
            "rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 5, TransactionType.EXPENSE,
            activeFrom = "2026-09-10",
        )
        assertNull(vencimientoEnElChecklist(arriendo, LocalDate.of(2026, 9, 12), corte25, emptySet()))
        val eventos = listOf(evento("pago", "Arriendo", 1_800_000, "2026-09-10", "Vivienda"))
        assertEquals(emptyMap(), parteFija(listOf(arriendo), emptyList(), eventos, hoy = LocalDate.of(2026, 9, 12)))
    }

    @Test
    fun `un pago anotado antes del vencimiento sale del variable aunque el item siga pendiente`() {
        // El 20-sep el celular del 22 todavía no vence, pero ya está en el checklist (y en los fijos).
        val celular = regla("rr_celular", "Celular", "Servicios", 53_077, 22)
        val eventos = listOf(evento("cel", "Celular", 53_077, "2026-09-19", "Servicios"))
        assertEquals(
            mapOf("cel" to 53_077L),
            parteFija(listOf(celular), emptyList(), eventos, hoy = LocalDate.of(2026, 9, 20)),
        )
    }

    @Test
    fun `lo que espera en Por confirmar no le quita el lugar al pago real`() {
        val mercado = regla("rr_mercado", "Mercado", "Mercado", 2_000_000, 25)
        val eventos = listOf(
            evento("sin-confirmar", "Mercado", 2_000_000, "2026-08-25", "Mercado", ReconciliationStatus.UNCONFIRMED),
            evento("real", "Mercado", 2_000_000, "2026-08-27", "Mercado"),
        )
        assertEquals(mapOf("real" to 2_000_000L), parteFija(listOf(mercado), emptyList(), eventos))
    }

    @Test
    fun `un movimiento paga una sola regla, y gana la que lo nombra`() {
        val papa = regla("rr_papa", "Crédito Papá", "Crédito", 4_280_000, 27)
        val mama = regla("rr_mama", "Crédito Mamá", "Crédito", 1_300_000, 27)
        val eventos = listOf(evento("pago-papa", "Crédito Papá", 4_280_000, "2026-08-27", "Crédito"))
        // Mamá comparte la categoría y va primero en la lista: igual no se lo lleva.
        val parte = parteFija(listOf(mama, papa), emptyList(), eventos)
        assertEquals(mapOf("pago-papa" to 4_280_000L), parte)
    }

    /**
     * Una cuota anotada con la categoría «Cuota de crédito» no suma en el variable, pero si un
     * recurrente real la paga, su parte fija tiene que figurar: si no, `pagosDeDeudaFueraDelChecklist`
     * la restaría otra vez como «otro pago de deuda», encima de los fijos.
     */
    @Test
    fun `un recurrente real reclama una cuota anotada con la categoria de cuota`() {
        val reglas = listOf(regla("rr_papa", "Crédito Papá", "Crédito", 4_280_000, 27))
        val eventos = listOf(
            evento("ev_papa", "Crédito Papá", 4_280_000, "2026-08-27", CUOTA_CATEGORY),
            evento("ev_almuerzo", "Almuerzo", 45_000, "2026-09-10", "Comida"),
        )
        val parte = parteFija(reglas, emptyList(), eventos)
        assertEquals(4_280_000L, parte["ev_papa"])
        // El variable no cambia: la cuota ya salía entera por su categoría.
        assertEquals(45_000L, gastoTotal(eventos, parte))
    }
}

