package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola 9 · B: las guardas del ofrecimiento «¿esto se repite todos los meses?» y el prellenado.
 * El porqué de cada una está en `RecurringOffer.kt`.
 */
class RecurringOfferTest {

    private fun evento(
        id: String = "ev_1",
        category: String = "Vivienda",
        description: String = "Arriendo",
        amount: Long = 1_800_000L,
        type: TransactionType = TransactionType.EXPENSE,
        transferId: String? = null,
        // 2025-08-05, 15:00 UTC → 5 de agosto también en Bogotá (UTC-5)
        timestamp: Long = 1_754_406_000_000L,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_1",
        type = type,
        amount = amount,
        category = category,
        description = description,
        timestamp = timestamp,
        transferId = transferId,
    )

    private fun regla(name: String) = RecurringRule(
        id = "rr_1",
        name = name,
        category = "Vivienda",
        amount = 1_800_000L,
        dayOfMonth = 5,
        type = TransactionType.EXPENSE,
    )

    @Test
    fun `un gasto comun si se ofrece`() {
        assertTrue(shouldOfferRecurring(evento(), emptyList()))
    }

    @Test
    fun `nunca en un traspaso - ni por el enlace ni por la categoria reservada`() {
        assertFalse(shouldOfferRecurring(evento(transferId = "tr_1"), emptyList()))
        assertFalse(shouldOfferRecurring(evento(category = TRANSFER_CATEGORY, description = TRANSFER_CATEGORY), emptyList()))
    }

    @Test
    fun `no se ofrece si ya existe un recurrente equivalente`() {
        // Mismo nombre normalizado: «arriendo » y «Arriendo» son la misma cosa.
        assertFalse(shouldOfferRecurring(evento(), listOf(regla("arriendo "))))
    }

    @Test
    fun `otro recurrente con otro nombre no bloquea el ofrecimiento`() {
        assertTrue(shouldOfferRecurring(evento(), listOf(regla("Netflix"))))
    }

    @Test
    fun `no se ofrece dos veces por la misma cosa en la misma sesion`() {
        val yaOfrecidas = setOf(throttleKeyFor(evento()))
        assertFalse(shouldOfferRecurring(evento(), emptyList(), yaOfrecidas))
        // Y no arrastra a lo demás: un gasto de otra categoría se sigue ofreciendo.
        assertTrue(shouldOfferRecurring(evento(category = "Gimnasio"), emptyList(), yaOfrecidas))
    }

    /**
     * La "cosa" NO es el nombre: el nombre sale de la nota, así que con «Almuerzo lunes» y
     * «Almuerzo con Ana» —el mismo menú del mediodía al mismo precio— cada gasto de comida volvía
     * a disparar la barra, que es el caso para el que la guarda existe.
     */
    @Test
    fun `la misma cosa con notas distintas no vuelve a ofrecerse`() {
        val primero = evento(category = "Comida", description = "Almuerzo lunes", amount = 22_000)
        val segundo = evento(category = "Comida", description = "Almuerzo con Ana", amount = 22_000)

        assertFalse(shouldOfferRecurring(segundo, emptyList(), setOf(throttleKeyFor(primero))))
    }

    /**
     * Y tampoco es la categoría sola: dos suscripciones de «Entretenimiento» la misma noche son
     * dos cosas distintas, y con la clave vieja la segunda no se ofrecía en toda la sesión — el
     * día de configurar la app, que es el día para el que la función existe.
     */
    @Test
    fun `dos cobros distintos de la misma categoria se ofrecen los dos`() {
        val netflix = evento(description = "Netflix", category = "Entretenimiento", amount = 44_900)
        val spotify = evento(description = "Spotify", category = "Entretenimiento", amount = 16_900)

        assertTrue(shouldOfferRecurring(spotify, emptyList(), setOf(throttleKeyFor(netflix))))
    }

    /** Un ingreso de una categoría ya ofrecida como gasto sí se ofrece: son cosas distintas. */
    @Test
    fun `la guarda por cosa distingue gasto de ingreso`() {
        val gasto = evento(category = "Carro", type = TransactionType.EXPENSE)
        val ingreso = evento(category = "Carro", type = TransactionType.INCOME)

        assertTrue(shouldOfferRecurring(ingreso, emptyList(), setOf(throttleKeyFor(gasto))))
    }

    /**
     * El techo de insistencia: lo que evita que un almuerzo de precio distinto cada día —tres
     * "cosas" distintas para la clave, y ninguna con forma de recurrente— vuelva a nagear.
     */
    @Test
    fun `a las tres barras no tomadas se apaga la categoria entera`() {
        val almuerzo = evento(category = "Comida", amount = 28_000)
        val clave = categoryThrottleKeyFor(almuerzo)

        assertTrue(shouldOfferRecurring(almuerzo, emptyList(), sinTomarPorCategoria = mapOf(clave to 2)))
        assertFalse(shouldOfferRecurring(almuerzo, emptyList(), sinTomarPorCategoria = mapOf(clave to MAX_SIN_TOMAR)))
        // Y solo esa categoría: otra sigue viva.
        assertTrue(
            shouldOfferRecurring(
                evento(category = "Vivienda"),
                emptyList(),
                sinTomarPorCategoria = mapOf(clave to MAX_SIN_TOMAR),
            ),
        )
    }

    @Test
    fun `el techo distingue gasto de ingreso`() {
        val gasto = evento(category = "Carro", type = TransactionType.EXPENSE)
        val ingreso = evento(category = "Carro", type = TransactionType.INCOME)

        assertTrue(
            shouldOfferRecurring(
                ingreso,
                emptyList(),
                sinTomarPorCategoria = mapOf(categoryThrottleKeyFor(gasto) to MAX_SIN_TOMAR),
            ),
        )
    }

    @Test
    fun `la clave de categoria del prellenado es la misma que la del evento`() {
        val e = evento(category = "Entretenimiento", type = TransactionType.EXPENSE)
        assertEquals(categoryThrottleKeyFor(e), categoryThrottleKeyFor(prefillFrom(e)))
    }

    /**
     * Recurrentes muestra reglas y suscripciones en una sola lista y las SUMA juntas: ofrecer
     * una regla «Netflix» a quien ya tiene el cobro detectado se lo contaría dos veces en el
     * flujo libre.
     */
    @Test
    fun `no se ofrece si el cobro ya existe como suscripcion`() {
        val netflix = evento(description = "Netflix", category = "Entretenimiento", amount = 44_900)

        assertFalse(
            shouldOfferRecurring(netflix, emptyList(), emptySet(), listOf("NETFLIX")),
        )
        assertTrue(
            shouldOfferRecurring(netflix, emptyList(), emptySet(), listOf("Spotify")),
        )
    }

    @Test
    fun `un ingreso tambien se ofrece - la nomina es lo mas recurrente que hay`() {
        assertTrue(
            shouldOfferRecurring(
                evento(description = "Nómina", category = "Salario", type = TransactionType.INCOME),
                emptyList(),
            ),
        )
    }

    @Test
    fun `el prellenado sale del movimiento, con el dia tomado de su fecha`() {
        val prefill = prefillFrom(evento())

        assertEquals("Arriendo", prefill.name)
        assertEquals(1_800_000L, prefill.amount)
        assertEquals("Vivienda", prefill.category)
        assertEquals(TransactionType.EXPENSE, prefill.type)
        assertEquals(5, prefill.dayOfMonth)
        assertEquals("acc_1", prefill.accountId)
    }

    /** Un gasto de las 9 pm del 31 en Bogotá es del 31, no del 1 del mes siguiente. */
    @Test
    fun `el dia del mes se calcula en la zona de la app, no en UTC`() {
        // 2025-08-01 02:00 UTC = 2025-07-31 21:00 en Bogotá
        val prefill = prefillFrom(evento(timestamp = 1_754_013_600_000L))

        assertEquals(31, prefill.dayOfMonth)
    }

    @Test
    fun `sin nota, el nombre es la categoria`() {
        // QuickAdd guarda la categoría como descripción cuando la nota va vacía.
        assertEquals("Comida", prefillNameFor(evento(description = "Comida", category = "Comida")))
    }

    // ── Ola 9 · D (segundo hallazgo): la categoría que propone el nombre ──────────────

    @Test
    fun `el nombre Salario propone la categoria Salario, no un generico`() {
        assertEquals("Salario", categoriaSugeridaPorNombre("salario", TransactionType.INCOME))
    }

    @Test
    fun `la propuesta respeta el tipo elegido`() {
        // «Salario» es una categoría de ingreso: en un gasto no se propone.
        assertNull(categoriaSugeridaPorNombre("Salario", TransactionType.EXPENSE))
    }

    @Test
    fun `un nombre que no es una categoria del catalogo no propone nada`() {
        assertNull(categoriaSugeridaPorNombre("Netflix", TransactionType.EXPENSE))
        assertNull(categoriaSugeridaPorNombre("", TransactionType.EXPENSE))
    }
}
