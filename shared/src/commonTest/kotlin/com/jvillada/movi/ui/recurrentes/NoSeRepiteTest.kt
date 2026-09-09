package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Este no se repite»**, la vuelta que no existía.
 *
 * El caso que lo pidió, con sus cifras: el dueño tiene una suscripción «Microsoft» de $239.900 al
 * mes, y además una compra suelta de $249.000 en Microsoft. Movimientos reconoce recurrentes **por
 * nombre**, así que las dos filas decían «recurrente», y la única puerta que ofrecía la app era
 * *«Sí, se repite todos los meses»*. Palabra por palabra: *«no lo puedo editar para indicar que no
 * es recurrente… una vez recurrente no puedo hacerlo no recurrente»*.
 *
 * Lo que se prueba acá es que la marca es **por movimiento** y que **gana sobre todo lo demás**,
 * incluidas las dos puertas estructurales (la cuota de un crédito ya pagada y el pago de una
 * tarjeta). Ver [com.jvillada.movi.shared.model.FinancialEvent.noSeRepite].
 */
class NoSeRepiteTest {

    private fun evento(
        id: String = "ev_1",
        category: String = "Entretenimiento",
        description: String = "Microsoft",
        amount: Long = 249_000L,
        type: TransactionType = TransactionType.EXPENSE,
        transferId: String? = null,
        noSeRepite: Boolean = false,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_1",
        type = type,
        amount = amount,
        category = category,
        description = description,
        timestamp = 1_757_000_000_000L,
        transferId = transferId,
        noSeRepite = noSeRepite,
    )

    private fun cobro(nombre: String) = Subscription(
        id = "sub_$nombre",
        merchantKey = nombre.lowercase(),
        displayName = nombre,
        amount = 239_900L,
        currency = "COP",
        dayOfMonth = 5,
        status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH,
        firstSeen = 0L,
        lastSeen = 0L,
        occurrences = 3,
    )

    private val microsoft = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Microsoft")))

    @Test
    fun `la compra suelta marcada deja de leerse como recurrente`() {
        // Sin la marca, el nombre alcanza y la fila entra al filtro. Esto es lo que estaba mal.
        assertEquals("Microsoft", nombreRecurrenteDe(evento(), emptyList(), microsoft))
        // Con la marca, no.
        assertNull(nombreRecurrenteDe(evento(noSeRepite = true), emptyList(), microsoft))
    }

    @Test
    fun `marcar una fila no toca a las demas con el mismo nombre`() {
        // Lo que el dueño necesita que siga pasando: el cobro mensual de Microsoft sigue
        // existiendo y sigue teniendo que aparecer. La marca es sobre UNA fila.
        val suelto = evento(id = "ev_suelto", amount = 249_000L, noSeRepite = true)
        val mensual = evento(id = "ev_mensual", amount = 239_900L)

        assertNull(nombreRecurrenteDe(suelto, emptyList(), microsoft))
        assertEquals("Microsoft", nombreRecurrenteDe(mensual, emptyList(), microsoft))
    }

    @Test
    fun `tambien le gana a una regla, no solo a una suscripcion`() {
        val regla = RecurringRule(
            id = "rr_1",
            name = "Microsoft",
            category = "Entretenimiento",
            amount = 239_900L,
            dayOfMonth = 5,
            type = TransactionType.EXPENSE,
        )
        assertEquals("Microsoft", nombreRecurrenteDe(evento(), listOf(regla), emptyList()))
        assertNull(nombreRecurrenteDe(evento(noSeRepite = true), listOf(regla), emptyList()))
    }

    @Test
    fun `le gana a la cuota de un credito ya pagada, que es estructural`() {
        // La cuota se reconoce por su FORMA, no por su nombre, y entra antes que la comparación
        // por nombre. La marca del dueño va todavía más arriba: es una afirmación explícita suya
        // sobre un hecho suyo, y a eso no le gana ninguna inferencia de la app.
        val cuota = evento(
            category = CUOTA_CATEGORY,
            description = "Cuota de Vehículo",
            amount = 4_215_223L,
            transferId = "tr_1",
        )
        assertTrue(nombreRecurrenteDe(cuota, emptyList(), emptyList()) != null)
        assertNull(nombreRecurrenteDe(cuota.copy(noSeRepite = true), emptyList(), emptyList()))
    }

    @Test
    fun `le gana al pago de una tarjeta, que tambien es estructural`() {
        val pago = evento(
            category = CARD_PAYMENT_CATEGORY,
            description = "Pago de Nu Tarjeta",
            amount = 115_113L,
            transferId = "tr_2",
        )
        assertTrue(nombreRecurrenteDe(pago, emptyList(), emptyList()) != null)
        assertNull(nombreRecurrenteDe(pago.copy(noSeRepite = true), emptyList(), emptyList()))
    }

    @Test
    fun `sin marcar, todo se comporta exactamente igual que antes`() {
        // El default `false` es lo que hace que esta ola no cambie nada de lo que ya funcionaba:
        // ninguna de las decenas de miles de filas que ya existen tiene la marca puesta.
        assertEquals("Microsoft", nombreRecurrenteDe(evento(), emptyList(), microsoft))
        assertNull(nombreRecurrenteDe(evento(description = "Carnes y Legumbres"), emptyList(), microsoft))
    }
}
