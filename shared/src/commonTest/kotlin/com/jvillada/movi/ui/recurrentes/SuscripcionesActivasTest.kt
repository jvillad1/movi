package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PR 5 del rediseño de Recurrentes (2026-09): la etiqueta de origen que se perdió al borrar la
 * pantalla, y la selección de las suscripciones ACTIVAS que se quedaron sin ninguna superficie.
 *
 * Lo que se fija acá no es cosmético: [origenDeSuscripcion] decide a la vez qué dice la fila y si
 * «Quitar» BORRA o marca DISMISSED (ver [quitarBorraLaSuscripcion]). Que las dos lecturas salgan
 * de la misma función es justamente lo que estos tests protegen.
 */
class SuscripcionesActivasTest {

    private fun sub(
        nombre: String,
        monto: Long = 20_000,
        moneda: String = "COP",
        dia: Int = 5,
        estado: SubStatus = SubStatus.CONFIRMED,
        clave: String = nombre.lowercase(),
    ) = Subscription(
        id = "s_$nombre", merchantKey = clave, displayName = nombre, amount = monto, currency = moneda,
        dayOfMonth = dia, status = estado, confidence = SubConfidence.HIGH,
        firstSeen = 0, lastSeen = 0, occurrences = 3,
    )

    private fun regla(nombre: String, monto: Long = 20_000, dia: Int = 5) = RecurringRule(
        id = "r_$nombre", name = nombre, category = "Otros", amount = monto,
        dayOfMonth = dia, type = TransactionType.EXPENSE,
    )

    // ── Las tres variantes de origen ──────────────────────────────────────────

    @Test
    fun `una que confirmo el dueno dice que la encontro Movi`() {
        val s = sub("Netflix", estado = SubStatus.CONFIRMED)

        assertEquals(OrigenDeSuscripcion.LA_ENCONTRO_MOVI, origenDeSuscripcion(s))
        assertEquals(
            "Suscripción · la encontró Movi",
            contextoDeSuscripcionActiva(Recurrente.Suscripcion(s, yaEsRegla = false)),
        )
    }

    /** La única de las tres que el dueño nunca aprobó, y que sigue sumando en «Flujo libre». */
    @Test
    fun `una AUTO dice ademas que se activo sola`() {
        val s = sub("YouTube", estado = SubStatus.AUTO)

        assertEquals(OrigenDeSuscripcion.LA_ENCONTRO_MOVI_Y_LA_ACTIVO_SOLA, origenDeSuscripcion(s))
        assertEquals(
            "Suscripción · la encontró Movi y la activó sola",
            contextoDeSuscripcionActiva(Recurrente.Suscripcion(s, yaEsRegla = false)),
        )
    }

    @Test
    fun `una que escribio el dueno no dice que la encontro nadie`() {
        val s = sub("Claude", clave = MANUAL_SUB_PREFIX + "claude")

        assertEquals(OrigenDeSuscripcion.LA_ESCRIBIO_EL_DUENO, origenDeSuscripcion(s))
        assertEquals(
            "Suscripción",
            contextoDeSuscripcionActiva(Recurrente.Suscripcion(s, yaEsRegla = false)),
        )
    }

    /**
     * El prefijo manda sobre el estado. No es un detalle de orden: [quitarBorraLaSuscripcion] lee
     * la MISMA función, así que si el estado ganara, una fila que dice «la encontró Movi» se
     * borraría de verdad al tocar «Quitar» en vez de quedar DISMISSED.
     */
    @Test
    fun `una manual sigue siendo del dueno aunque su estado sea AUTO`() {
        val s = sub("Claude", estado = SubStatus.AUTO, clave = MANUAL_SUB_PREFIX + "claude")

        assertEquals(OrigenDeSuscripcion.LA_ESCRIBIO_EL_DUENO, origenDeSuscripcion(s))
        assertTrue(quitarBorraLaSuscripcion(s))
    }

    // ── Qué significa «Quitar» ────────────────────────────────────────────────

    @Test
    fun `quitar borra la que escribio el dueno y solo descarta la que encontro el detector`() {
        assertTrue(quitarBorraLaSuscripcion(sub("Claude", clave = MANUAL_SUB_PREFIX + "claude")))
        assertFalse(quitarBorraLaSuscripcion(sub("Netflix")))
        assertFalse(quitarBorraLaSuscripcion(sub("YouTube", estado = SubStatus.AUTO)))
    }

    // ── El aviso de duplicado va antes que el origen ──────────────────────────

    @Test
    fun `si ya hay una regla con ese nombre la fila dice que no se suma dos veces`() {
        val s = sub("Netflix", estado = SubStatus.AUTO)

        assertEquals(
            "Ya lo tienes como recurrente · no se suma dos veces",
            contextoDeSuscripcionActiva(Recurrente.Suscripcion(s, yaEsRegla = true)),
        )
    }

    // ── Qué entra a la sección ────────────────────────────────────────────────

    @Test
    fun `solo entran las activas y salen ordenadas por dia del mes`() {
        val subs = SubscriptionsResult(
            listOf(
                sub("Netflix", dia = 20, estado = SubStatus.CONFIRMED),
                sub("YouTube", dia = 3, estado = SubStatus.AUTO),
                sub("Disney+", dia = 1, estado = SubStatus.CANDIDATE),
                sub("Spotify", dia = 2, estado = SubStatus.DISMISSED),
            ),
            monthlyTotalCop = 40_000,
        )

        val activas = suscripcionesActivas(resumenRecurrentes(rules = emptyList(), subs = subs))

        assertEquals(listOf("YouTube", "Netflix"), activas.map { it.sub.displayName })
    }

    /**
     * Las reglas del dueño NO son suscripciones: la sección es un inventario de cobros, no la
     * lista única que tenía la pantalla vieja (esas ya salen en «Próximos» y en la lista filtrada
     * de abajo).
     */
    @Test
    fun `las reglas del dueno no entran a la seccion`() {
        val resumen = resumenRecurrentes(
            rules = listOf(regla("Arriendo", 1_800_000, dia = 1)),
            subs = SubscriptionsResult(listOf(sub("Netflix", dia = 20)), monthlyTotalCop = 20_000),
        )

        assertEquals(listOf("Netflix"), suscripcionesActivas(resumen).map { it.sub.displayName })
    }

    /**
     * La marca de duplicado sale del reparto uno-a-uno de [resumenRecurrentes] y no de un filtro
     * propio: con dos cobros del mismo nombre y una sola regla, solo UNO queda tapado — un filtro
     * recalculado acá habría marcado los dos y la lista contradiría al total de arriba.
     */
    @Test
    fun `el reparto uno a uno llega hasta la fila que se pinta`() {
        val resumen = resumenRecurrentes(
            rules = listOf(regla("Netflix", 44_900)),
            subs = SubscriptionsResult(
                listOf(sub("Netflix", 44_900, dia = 10), sub("Netflix", 44_900, dia = 20).copy(id = "s_dos")),
                monthlyTotalCop = 89_800,
            ),
        )

        val activas = suscripcionesActivas(resumen)

        assertEquals(2, activas.size)
        assertEquals(1, activas.count { it.yaEsRegla })
        assertEquals(
            "Ya lo tienes como recurrente · no se suma dos veces",
            contextoDeSuscripcionActiva(activas.first { it.yaEsRegla }),
        )
        assertEquals(
            "Suscripción · la encontró Movi",
            contextoDeSuscripcionActiva(activas.first { !it.yaEsRegla }),
        )
    }
}
