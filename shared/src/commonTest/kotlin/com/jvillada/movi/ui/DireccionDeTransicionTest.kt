package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.transactions.CHIP_ENTRE_CUENTAS
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ola 1 · «Que Movi se sienta viva»: hacia dónde se anima el cambio de pantalla. Apilar entra desde
 * la derecha, volver es el movimiento inverso y cambiar de pestaña raíz es un fundido. Se decide
 * comparando la pila antes y después, con [direccionDe] — pura, así que se prueba sin Compose.
 */
class DireccionDeTransicionTest {

    private val hoy = Screen.Dashboard
    private val movimientos = Screen.Transactions()

    @Test
    fun `apilar una pantalla entra desde la derecha`() {
        assertEquals(DireccionDeTransicion.APILAR, direccionDe(listOf(hoy), listOf(hoy, Screen.PorRevisar)))
        assertEquals(
            DireccionDeTransicion.APILAR,
            direccionDe(listOf(hoy, Screen.Accounts), listOf(hoy, Screen.Accounts, Screen.AccountDetail("a1", AccountGroup.DINERO))),
        )
    }

    @Test
    fun `sacar la de arriba es volver`() {
        assertEquals(DireccionDeTransicion.VOLVER, direccionDe(listOf(hoy, Screen.PorRevisar), listOf(hoy)))
        assertEquals(DireccionDeTransicion.VOLVER, direccionDe(listOf(hoy, Screen.Accounts, Screen.Credits), listOf(hoy, Screen.Accounts)))
        // Tocar la pestaña de la pantalla en la que uno ya está, desde algo apilado encima: vuelve a la raíz.
        assertEquals(
            DireccionDeTransicion.VOLVER,
            direccionDe(listOf(hoy, movimientos, Screen.PorRevisar), listOf(hoy, movimientos)),
        )
    }

    @Test
    fun `cambiar de pestana raiz es un fundido, en las dos direcciones`() {
        // Hoy → Movimientos es, en la pila, un «apilar»; pero entre pestañas no hay adelante ni atrás.
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(hoy), listOf(hoy, movimientos)))
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(hoy, movimientos), listOf(hoy)))
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(hoy, movimientos), listOf(hoy, Screen.Plan())))
        // Desde algo apilado en otra pestaña, también.
        assertEquals(
            DireccionDeTransicion.FUNDIDO,
            direccionDe(listOf(hoy, movimientos, Screen.PorRevisar), listOf(hoy, Screen.Accounts)),
        )
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(hoy, movimientos, Screen.PorRevisar), listOf(hoy)))
    }

    @Test
    fun `reemplazar la pila entera es un fundido`() {
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(Screen.Login), listOf(hoy)))
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(hoy, Screen.Profile), listOf(Screen.Login)))
        // El destino de reserva de la flecha ‹ (una pila de uno que se reemplaza).
        assertEquals(DireccionDeTransicion.FUNDIDO, direccionDe(listOf(Screen.Credits), listOf(Screen.Accounts)))
    }

    @Test
    fun `la misma pantalla arriba no se anima`() {
        assertEquals(DireccionDeTransicion.NINGUNA, direccionDe(listOf(hoy), listOf(hoy)))
        assertEquals(DireccionDeTransicion.NINGUNA, direccionDe(listOf(hoy, Screen.Mas, movimientos), listOf(hoy, movimientos)))
    }

    @Test
    fun `un chip dentro de Movimientos se apila como cualquier pantalla`() {
        assertEquals(
            DireccionDeTransicion.APILAR,
            direccionDe(listOf(hoy, movimientos), listOf(hoy, movimientos, Screen.Transactions(CHIP_ENTRE_CUENTAS))),
        )
    }
}
