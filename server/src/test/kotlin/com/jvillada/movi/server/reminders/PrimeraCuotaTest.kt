package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **El piso de `activeFrom` es el PERÍODO, no el día.**
 *
 * Nació de la primera cuota de un crédito: el dueño registró un préstamo desembolsado el 1 de
 * septiembre con pago el día 1, y Movi le anunció la primera cuota para ese mismo 1 de septiembre
 * —*«normalmente un desembolso es 1 mes aproximadamente antes de la primera cuota»*—. El remedio
 * de entonces fue «una ocurrencia anterior **o igual** a esa fecha no existe».
 *
 * Ese remedio se pasaba de largo: se comía el período ENTERO del movimiento que originó la regla,
 * y con eso las reglas del dueño creadas desde un pago del período en curso («Coomeva Familiar»,
 * «Tía Caro») desaparecían del checklist de ese período aunque el pago que las prueba estuviera
 * ahí. Hoy el piso es el arranque del período: **los períodos anteriores no existen, el del
 * arranque sí**.
 *
 * **Lo que eso le cuesta al caso original**, dicho de frente: un crédito desembolsado el 1 con
 * cuota el día 1 vuelve a tener su primera cuota ese mismo 1, porque el desembolso y la cuota caen
 * en el mismo período y son —vistos como fechas— indistinguibles del caso de «Tía Caro», que el
 * dueño sí quiere ver. Lo que cambió alrededor es que esa cuota ya no se da por ocurrida sola: sin
 * un movimiento que la pruebe queda abierta, y si el dueño la paga aparece emparejada. Lo que
 * sigue firme, y es para lo que `activeFrom` existe, está en la segunda clase de acá abajo: no se
 * inventan cuotas de agosto ni de julio.
 */
class PrimeraCuotaTest {

    private fun regla(activeFrom: String?) = RecurringRule(
        id = "rule-1",
        name = "Cuota Crédito",
        category = "Créditos",
        amount = 10_000_000L,
        dayOfMonth = 1,
        type = TransactionType.EXPENSE,
        activeFrom = activeFrom,
    )

    @Test
    fun el_periodo_del_arranque_si_tiene_ocurrencia() {
        assertTrue(
            ruleIsActiveOn(regla("2026-09-01"), LocalDate.of(2026, 9, 1)),
            "el período del arranque existe: esconderlo era lo que borraba «Tía Caro» del checklist",
        )
    }

    @Test
    fun los_periodos_siguientes_tambien() {
        assertTrue(ruleIsActiveOn(regla("2026-09-01"), LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun los_periodos_ANTERIORES_al_arranque_no_existen() {
        assertFalse(
            ruleIsActiveOn(regla("2026-09-01"), LocalDate.of(2026, 8, 1)),
            "esto es para lo que activeFrom existe: nada de historia inventada hacia atrás",
        )
    }

    /**
     * **El período es el del DUEÑO, no el mes de calendario.** Con corte 25 el período que
     * contiene al 5 de septiembre arranca el 25 de agosto, así que el vencimiento del 30 de agosto
     * cae adentro y existe. Es exactamente «Coomeva Familiar», día 30, creada desde un movimiento
     * del 5 de septiembre — la regla que había que destrabar a mano en la base.
     */
    @Test
    fun con_corte_25_el_piso_es_el_arranque_del_periodo_del_dueno() {
        val corte25 = PeriodSettings(cutoffDay = 25)
        val coomeva = regla("2026-09-05").copy(dayOfMonth = 30)

        assertTrue(
            ruleIsActiveOn(coomeva, LocalDate.of(2026, 8, 30), corte25),
            "el 30-ago está en el período del dueño que contiene al 5-sep (25-ago a 24-sep)",
        )
        // Y por mes de calendario habría quedado afuera: la diferencia entre los dos criterios es
        // justo el mes que el dueño reclamó.
        assertFalse(ruleIsActiveOn(coomeva, LocalDate.of(2026, 8, 30)))
        // El período anterior sigue sin existir.
        assertFalse(ruleIsActiveOn(coomeva, LocalDate.of(2026, 7, 30), corte25))
    }

    /** «Tía Caro»: día 1, creada desde un movimiento del 1-sep. Ese mismo vencimiento existe. */
    @Test
    fun la_regla_creada_desde_un_movimiento_del_mismo_dia_tiene_su_ocurrencia() {
        val corte25 = PeriodSettings(cutoffDay = 25)
        val tia = regla("2026-09-01").copy(dayOfMonth = 1)

        assertTrue(ruleIsActiveOn(tia, LocalDate.of(2026, 9, 1), corte25))
        assertFalse(ruleIsActiveOn(tia, LocalDate.of(2026, 8, 1), corte25), "agosto no se inventa")
    }

    /** Un salario o un gimnasio no tienen desembolso: corren desde siempre, como hasta ahora. */
    @Test
    fun una_regla_escrita_a_mano_corre_desde_siempre() {
        assertTrue(ruleIsActiveOn(regla(null), LocalDate.of(2020, 1, 1)))
    }

    /** Una fecha ilegible no puede apagar un recordatorio: ante la duda, la regla corre. */
    @Test
    fun una_fecha_invalida_no_apaga_la_regla() {
        assertTrue(ruleIsActiveOn(regla("no-es-una-fecha"), LocalDate.of(2026, 9, 1)))
    }

    /** La regla sintética de un crédito hereda la fecha de desembolso de sus términos. */
    @Test
    fun la_regla_del_credito_toma_la_fecha_de_desembolso() {
        val terms = CreditTerms(
            accountId = "acc-1", bank = "Papá", principal = 10_000_000L, rateEa = 0.0,
            termMonths = 1, installment = 10_000_000L, dayOfMonth = 1, startDate = "2026-09-01",
        )

        val regla = virtualRuleFor(terms, "Crédito Techo Gardenera")

        // El período del desembolso ya tiene cuota (ver el KDoc de la clase: es el precio de que
        // el piso sea el período), pero los anteriores siguen sin existir, que es lo que el campo
        // vino a garantizar.
        assertTrue(ruleIsActiveOn(regla, LocalDate.of(2026, 9, 1)))
        assertFalse(ruleIsActiveOn(regla, LocalDate.of(2026, 8, 1)), "agosto no le debe nada")
        assertTrue(ruleIsActiveOn(regla, LocalDate.of(2026, 10, 1)))
    }
}

/**
 * El piso de `activeFrom`, en **todos** los endpoints.
 *
 * `ruleIsActiveOn` vivió un tiempo como filtro suelto en `/api/payments/occurrences`, y «Próximos
 * pagos» del Inicio y el barrido de avisos no lo aplicaban. Desde entonces lo sabe `dueDateFor`,
 * así que lo saben los tres consumidores sin que ninguno tenga que acordarse; esta clase es la que
 * fija eso.
 *
 * Lo que fija hoy es el piso por PERÍODO: **el desembolso del 1 de septiembre no le debe cuotas a
 * agosto ni a julio**, que es para lo que el campo existe. Que la cuota del propio septiembre
 * exista es deliberado — ver el KDoc de [PrimeraCuotaTest].
 */
class PrimeraCuotaEnTodosLosEndpointsTest {

    private fun reglaDelTecho(inicio: String) = RecurringRule(
        id = "cred_techo",
        name = "Cuota Crédito Techo Gardenera",
        amount = 10_000_000,
        dayOfMonth = 1,
        type = TransactionType.EXPENSE,
        category = "Cuota de crédito",
        activeFrom = inicio,
    )

    @Test
    fun `no se inventan cuotas de agosto ni de julio`() {
        // El caso del dueño, con sus fechas: desembolso 1-sep, día de pago 1, mirado el 30-ago.
        val due = dueDateFor(reglaDelTecho("2026-09-01"), today = LocalDate.of(2026, 8, 30))

        assertEquals(LocalDate.of(2026, 9, 1), due, "la primera cuota es la de su propio período")
        assertTrue(due.isAfter(LocalDate.of(2026, 8, 31)), "y nunca una de agosto")
    }

    @Test
    fun `mirado desde el dia del desembolso da lo mismo`() {
        val due = dueDateFor(reglaDelTecho("2026-09-01"), today = LocalDate.of(2026, 9, 1))

        assertEquals(LocalDate.of(2026, 9, 1), due)
    }

    @Test
    fun `y aparece en Proximos pagos con la misma fecha`() {
        // El endpoint que el dueño mira en el Inicio, y el que el arreglo de entonces no tocaba:
        // lo que importa es que los tres consumidores digan lo MISMO.
        val pagos = upcomingPayments(
            rules = listOf(reglaDelTecho("2026-09-01")),
            today = LocalDate.of(2026, 8, 30),
            leadDays = 3,
        )

        assertEquals("2026-09-01", pagos.single().dueDate)
        assertEquals(2, pagos.single().daysUntil)
    }

    /**
     * **La ocurrencia que se salteaba un período entero: el bug que esta rama cierra.**
     *
     * «Coomeva Familiar», día 30, creada desde un movimiento del 5 de septiembre, con el corte 25
     * del dueño. El vencimiento del 30 de agosto cae en el período que contiene a ese movimiento
     * (25-ago a 24-sep), así que **existe** — con el piso por día se lo saltaba y la fila no salía
     * en el checklist del período en curso.
     *
     * Se mira `ocurrenciaPorPreguntar` + `ruleIsActiveOn`, que es el par que usan el checklist
     * (`/api/payments/occurrences`) y `vencimientoEnElChecklist`; `dueDateFor` está abajo, con un
     * «hoy» dentro de la ventana de gracia, porque pasada la gracia rueda igual y por otro motivo.
     */
    @Test
    fun `la ocurrencia de dia 30 de una regla creada el 5-sep con corte 25 es la del 30-ago`() {
        val corte25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)
        val coomeva = reglaDelTecho("2026-09-05").copy(id = "rr_coomeva", dayOfMonth = 30)
        val hoy = LocalDate.of(2026, 9, 22)

        val vence = ocurrenciaPorPreguntar(hoy, coomeva, corte25)
        assertEquals(LocalDate.of(2026, 8, 30), vence)
        assertTrue(ruleIsActiveOn(coomeva, vence!!, corte25), "y la regla ya corre en ella")
    }

    /** «Tía Caro»: día 1, creada desde un movimiento del 1-sep, corte 25. */
    @Test
    fun `la ocurrencia de dia 1 de una regla creada el 1-sep con corte 25 es la del 1-sep`() {
        val corte25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)
        val tia = reglaDelTecho("2026-09-01").copy(id = "rr_tia", dayOfMonth = 1)
        val hoy = LocalDate.of(2026, 9, 22)

        val vence = ocurrenciaPorPreguntar(hoy, tia, corte25)
        assertEquals(LocalDate.of(2026, 9, 1), vence)
        assertTrue(ruleIsActiveOn(tia, vence!!, corte25))
    }

    /**
     * **El mismo `dueDateFor`, con el piso por período.** Mirado el 2 de septiembre —dentro de la
     * ventana de gracia del 30 de agosto— el vencimiento vigente de Coomeva es el del 30 de agosto.
     * Con el piso por día era el 30 de septiembre: la regla se saltaba su propio período.
     */
    @Test
    fun `dueDateFor tambien respeta el periodo del movimiento que la origino`() {
        val corte25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)
        val coomeva = reglaDelTecho("2026-09-05").copy(id = "rr_coomeva", dayOfMonth = 30)
        val hoy = LocalDate.of(2026, 9, 2)

        assertEquals(LocalDate.of(2026, 8, 30), dueDateFor(coomeva, hoy, settings = corte25))
        // Y sellado —el movimiento que la originó es su evidencia— rueda al período siguiente.
        assertEquals(
            LocalDate.of(2026, 9, 30),
            dueDateFor(coomeva, hoy, occurredPeriods = setOf("2026-08"), settings = corte25),
        )
    }

    /**
     * **Corte 25 contra corte 1 sobre el mismo dato.** El período del dueño es el que decide qué
     * ocurrencia está en juego, así que las dos configuraciones no contestan igual — y esa
     * diferencia es exactamente el mes que el dueño reclamó.
     */
    @Test
    fun `el periodo usado es el del dueno y no el mes de calendario`() {
        val corte25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)
        val calendario = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 1)
        val coomeva = reglaDelTecho("2026-09-05").copy(dayOfMonth = 30)
        val hoy = LocalDate.of(2026, 9, 22)

        assertEquals(LocalDate.of(2026, 8, 30), ocurrenciaPorPreguntar(hoy, coomeva, corte25))
        assertEquals(LocalDate.of(2026, 9, 30), ocurrenciaPorPreguntar(hoy, coomeva, calendario))
        // Y el piso mismo: el 30-ago existe con corte 25 y no con corte 1.
        assertTrue(ruleIsActiveOn(coomeva, LocalDate.of(2026, 8, 30), corte25))
        assertFalse(ruleIsActiveOn(coomeva, LocalDate.of(2026, 8, 30), calendario))
    }

    @Test
    fun `un credito que ya venia pagandose no se corre`() {
        // La otra mitad: el arreglo no puede empujar las cuotas de un crédito viejo. La libranza
        // del dueño se desembolsó en 2024 y su cuota de este mes es de este mes.
        val libranza = reglaDelTecho("2024-07-22").copy(id = "cred_libranza", dayOfMonth = 25)

        val due = dueDateFor(libranza, today = LocalDate.of(2026, 8, 20))

        assertEquals(LocalDate.of(2026, 8, 25), due)
    }

    @Test
    fun `una fecha de inicio absurda no cuelga el server`() {
        // Un año mal tecleado —2400 en vez de 2026— daría un bucle infinito sin el tope. Devuelve
        // una fecha rara, que es un dato raro y no un server caído.
        val futuro = reglaDelTecho("2400-01-01")

        val due = dueDateFor(futuro, today = LocalDate.of(2026, 8, 30))

        assertTrue(due.isAfter(LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun `sin fecha de inicio se comporta como siempre`() {
        // Los recurrentes normales (el colegio, el gimnasio) no tienen `activeFrom`.
        val gimnasio = reglaDelTecho("2026-09-01").copy(activeFrom = null, dayOfMonth = 5)

        assertEquals(LocalDate.of(2026, 9, 5), dueDateFor(gimnasio, today = LocalDate.of(2026, 9, 1)))
    }
}
