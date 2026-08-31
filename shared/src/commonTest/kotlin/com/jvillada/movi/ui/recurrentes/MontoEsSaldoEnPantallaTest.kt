package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Que la bandera exista no sirve de nada si un renderer no la mira.**
 *
 * `RecurringRule.montoEsSaldo` nació con tres pantallas que lo respetaban y una —el push, el canal
 * que suena— que seguía anunciando la deuda entera como el pago del mes. Nadie lo testeaba: los
 * tests miraban que la regla naciera marcada, no que alguien hiciera algo con la marca.
 *
 * Acá se fija el comportamiento de los dos renderers de `:shared` (el Inicio y Recurrentes, que
 * comparten [textoDelMonto]) y el de la hoja de «¿ya ocurrió?». El push y el correo viven en
 * `:server` y tienen sus propios tests, por la misma razón y con las mismas cifras.
 */
class MontoEsSaldoEnPantallaTest {

    /** La regla sintética real de la tarjeta del dueño: el monto es su deuda, no su cuota. */
    private val tarjeta = RecurringRule(
        id = "${CARD_RULE_PREFIX}acc-amex",
        name = "Pago tarjeta AMEX 9208",
        category = "Créditos",
        amount = 27_501_150,
        dayOfMonth = 2,
        type = TransactionType.EXPENSE,
        montoEsSaldo = true,
    )

    /** Una cuota de crédito: acá el monto SÍ es lo que va a salir de la cuenta. */
    private val cuota = RecurringRule(
        id = "credit_acc-carro",
        name = "Cuota Vehículo 4083",
        category = "Créditos",
        amount = 4_215_223,
        dayOfMonth = 17,
        type = TransactionType.EXPENSE,
    )

    private val sueldo = RecurringRule(
        id = "r-sueldo",
        name = "Sueldo",
        category = "Salario",
        amount = 12_000_000,
        dayOfMonth = 25,
        type = TransactionType.INCOME,
    )

    // ── El texto que pintan el Inicio y Recurrentes ────────────────────────────

    @Test
    fun el_monto_de_una_tarjeta_se_dice_saldo_y_nunca_lleva_signo() {
        // Bajo «Próximos pagos», $27.501.150 a secas dice «esto te sale este mes». Y el signo
        // menos lo afirmaría todavía más: un saldo no es un movimiento.
        assertEquals("saldo $27.501.150", textoDelMonto(tarjeta))
        assertEquals("saldo $27.501.150", textoDelMonto(tarjeta, conSigno = true))
    }

    @Test
    fun una_cuota_de_credito_se_sigue_diciendo_como_siempre() {
        assertEquals("$4.215.223", textoDelMonto(cuota))
        assertEquals("−$4.215.223", textoDelMonto(cuota, conSigno = true))
    }

    @Test
    fun un_ingreso_recurrente_conserva_su_mas() {
        assertEquals("+$12.000.000", textoDelMonto(sueldo, conSigno = true))
        assertEquals("$12.000.000", textoDelMonto(sueldo))
    }

    // ── La hoja de «¿ya ocurrió?» ──────────────────────────────────────────────

    @Test
    fun la_hoja_de_ya_ocurrio_no_avisa_que_el_pago_de_una_tarjeta_difiere() {
        // El pago real de una tarjeta —el mínimo, el total, o algo en el medio— casi nunca es
        // igual al saldo, así que la advertencia salía TODOS los meses: «No es el monto que
        // anotaste ($27.501.150)» sobre un pago perfectamente normal, repitiéndole encima la
        // cifra que el resto de la app dejó de mostrar como su pago.
        assertFalse(avisaMontoDistinto(tarjeta, real = 1_400_000))
        assertFalse(avisaMontoDistinto(tarjeta, real = 27_501_150), "ni siquiera cuando coincide")
    }

    @Test
    fun pero_en_una_regla_con_monto_esperado_el_aviso_se_conserva() {
        // La razón por la que el aviso existe (un sueldo con retenciones distintas cada mes) no
        // cambió: donde hay un monto esperado de verdad, la diferencia se sigue diciendo.
        assertTrue(avisaMontoDistinto(sueldo, real = 11_780_000))
        assertFalse(avisaMontoDistinto(sueldo, real = 12_000_000))
        assertTrue(avisaMontoDistinto(cuota, real = 4_300_000))
    }

    @Test
    fun y_la_propuesta_se_sigue_ofreciendo_igual() {
        // Lo que se apaga es la ADVERTENCIA, no la pregunta: el dueño sigue pudiendo confirmar
        // qué movimiento fue el pago de la tarjeta.
        val estado = OccurrenceState(
            ruleId = tarjeta.id,
            period = "2026-08",
            dueDate = "2026-08-02",
            occurred = false,
            candidates = listOf(
                FinancialEvent(
                    id = "ev-1",
                    accountId = "acc-corriente",
                    amount = 1_400_000,
                    type = TransactionType.EXPENSE,
                    category = "Créditos",
                    description = "Pago American Express",
                    timestamp = 1_756_000_000_000,
                ),
            ),
        )

        assertTrue(hayQuePreguntar(estado))
        assertEquals("ev-1", propuestaActual(estado)?.id)
    }
}
