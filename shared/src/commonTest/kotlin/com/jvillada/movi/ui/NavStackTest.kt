package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.asBottomBarTab
import com.jvillada.movi.ui.components.railDestinations
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F22: la flecha ‹ debe volver a la pantalla anterior de verdad, no a un destino
 * fijo. `NavStack` es la lógica pura (sin Compose) detrás de `navigate`/`goBackTo`
 * en App.kt — la testeamos acá porque `SnapshotStateList` no es fácil de instanciar
 * fuera de una composición.
 */
class NavStackTest {

    @Test
    fun `shouldPush es true si la pila esta vacia`() {
        assertEquals(true, NavStack.shouldPush(emptyList(), Screen.Dashboard))
    }

    @Test
    fun `shouldPush es false si la pantalla pedida ya esta arriba de la pila`() {
        assertEquals(false, NavStack.shouldPush(listOf(Screen.Dashboard, Screen.Mas), Screen.Mas))
    }

    @Test
    fun `shouldPush es true si la pantalla pedida es distinta a la de arriba`() {
        assertEquals(true, NavStack.shouldPush(listOf(Screen.Dashboard), Screen.Mas))
    }

    @Test
    fun `back con historial hace pop, sin importar el fallback`() {
        // Inicio -> Creditos -> volver: debe sacar Creditos y quedar en Inicio,
        // no ir al fallback (Mas) aunque Creditos viva en Mas.
        val result = NavStack.back(listOf(Screen.Dashboard, Screen.Credits), fallback = Screen.Mas)
        assertIs<NavStack.BackResult.Pop>(result)
    }

    @Test
    fun `back con Mas antes de Creditos tambien hace pop, no fallback`() {
        // Mas -> Creditos -> volver: debe sacar Creditos y quedar en Mas via pop,
        // el mismo resultado que daria el fallback, pero por historial real.
        val result = NavStack.back(listOf(Screen.Mas, Screen.Credits), fallback = Screen.Mas)
        assertIs<NavStack.BackResult.Pop>(result)
    }

    @Test
    fun `back sin historial cae al fallback`() {
        // Entraste directo a Creditos (deep link o recarga de la web) — no hay
        // pantalla anterior a la que volver, así que cae al destino de reserva.
        val result = NavStack.back(listOf(Screen.Credits), fallback = Screen.Mas)
        assertIs<NavStack.BackResult.Fallback>(result)
        assertEquals(Screen.Mas, (result as NavStack.BackResult.Fallback).screen)
    }

    @Test
    fun `back sin historial con fallback distinto para pantallas de primer nivel`() {
        val result = NavStack.back(listOf(Screen.Goals), fallback = Screen.Dashboard)
        assertIs<NavStack.BackResult.Fallback>(result)
        assertEquals(Screen.Dashboard, (result as NavStack.BackResult.Fallback).screen)
    }

    // ── Ola 4: pestaña activa por pantalla (barra y rail) ─────────────────────

    @Test
    fun `Cuentas y el detalle de una cuenta de dinero marcan la pestaña Cuentas`() {
        assertEquals(NavTab.ACCOUNTS, navTabFor(Screen.Accounts))
        assertEquals(NavTab.ACCOUNTS, navTabFor(Screen.AccountDetail("acc-1", AccountGroup.DINERO)))
        assertEquals(NavTab.ACCOUNTS, navTabFor(Screen.AccountDetail("acc-2", AccountGroup.INVERSION)))
    }

    @Test
    fun `el detalle de una tarjeta o prestamo marca la pestaña Creditos`() {
        // Ola 7: las deudas se listan en Créditos, y el detalle se abre desde ahí — la
        // pestaña resaltada tiene que decir Créditos, no Cuentas.
        assertEquals(NavTab.CREDITS, navTabFor(Screen.AccountDetail("acc-3", AccountGroup.DEUDA)))
    }

    @Test
    fun `la reserva del volver y la pestaña resaltada del detalle no se contradicen`() {
        // Si la flecha ‹ sin historial cae en Créditos, la pestaña activa mientras se ve el
        // detalle también tiene que ser Créditos (y lo mismo con Cuentas).
        AccountGroup.entries.forEach { group ->
            assertEquals(
                navTabFor(homeScreenFor(group)),
                navTabFor(Screen.AccountDetail("acc-1", group)),
                "$group",
            )
        }
    }

    @Test
    fun `las deudas viven en Creditos y el resto en Cuentas`() {
        assertEquals(Screen.Credits, homeScreenFor(AccountGroup.DEUDA))
        assertEquals(Screen.Accounts, homeScreenFor(AccountGroup.DINERO))
        assertEquals(Screen.Accounts, homeScreenFor(AccountGroup.INVERSION))
    }

    @Test
    fun `Presupuestos y Creditos tienen destino propio y en la barra se resaltan como Mas`() {
        assertEquals(NavTab.BUDGETS, navTabFor(Screen.Budgets))
        assertEquals(NavTab.CREDITS, navTabFor(Screen.Credits))
        assertEquals(NavTab.MORE, NavTab.BUDGETS.asBottomBarTab())
        assertEquals(NavTab.MORE, NavTab.CREDITS.asBottomBarTab())
        assertEquals(NavTab.ACCOUNTS, NavTab.ACCOUNTS.asBottomBarTab())
    }

    @Test
    fun `Recurrentes ya no es un destino - es Movimientos con su chip`() {
        // PR 4 del rediseño de Recurrentes (2026-09): sin `Screen.Recurrentes` ni
        // `NavTab.RECURRING`. Lo que antes era esa pantalla hoy se pide como Movimientos con el
        // chip puesto, y por eso resalta la pestaña Movimientos y no Más.
        assertEquals(NavTab.TRANSACTIONS, navTabFor(Screen.Transactions(CHIP_RECURRENTES)))
        assertEquals(
            NavTab.TRANSACTIONS,
            NavTab.TRANSACTIONS.asBottomBarTab(),
            "en el teléfono también: no se esconde detrás de Más",
        )
    }

    @Test
    fun `cada destino del rail vuelve a su pantalla principal`() {
        // El rail y `screenForTab` no pueden desalinearse: si una entrada del rail no
        // resolviera a una pantalla que declara ESE destino, el ítem quedaría sin resaltar.
        railDestinations.forEach { dest ->
            assertEquals(dest.tab, navTabFor(screenForTab(dest.tab)), dest.label)
        }
    }

    @Test
    fun `las pantallas de Mas marcan Mas y los flujos a pantalla completa no tienen barra`() {
        // Ola 8: sin Screen.Subscriptions — las suscripciones son recurrentes, y desde el
        // rediseño de 2026-09 los recurrentes son Movimientos con su chip (ver el test de
        // arriba), así que marcan TRANSACTIONS y no Más.
        listOf(Screen.Mas, Screen.Profile, Screen.Goals,
            Screen.Extractos, Screen.AIChat, Screen.SMSInbox, Screen.SMSReconcile("s1"))
            .forEach { assertEquals(NavTab.MORE, navTabFor(it), "$it") }
        listOf(Screen.Login, Screen.Register, Screen.QuickAdd(), Screen.OCRCapture, Screen.ScreenEditor,
            Screen.StatementReview("{}"), Screen.ImportDetail("i1"))
            .forEach { assertNull(navTabFor(it), "$it") }
    }

    // ── Agregar es una ventana modal, no un destino ───────────────────────────

    @Test
    fun `Agregar se abre como ventana modal`() {
        assertTrue(opensAsOverlay(Screen.QuickAdd()))
        assertTrue(opensAsOverlay(Screen.QuickAdd(presetAccountId = "acc_1")))
    }

    @Test
    fun `el resto de las pantallas son destinos, no modales`() {
        listOf(
            Screen.Dashboard, Screen.Transactions(), Screen.Accounts, Screen.Credits, Screen.Budgets,
            Screen.Mas, Screen.Profile, Screen.Login, Screen.OCRCapture,
            Screen.AccountDetail("acc_1", AccountGroup.DINERO),
        ).forEach { assertFalse(opensAsOverlay(it), "$it") }
    }

    /**
     * El bug que esto blinda: «Agregar» se apilaba como una pantalla más, `navTabFor` daba `null`
     * para ella y App.kt solo pinta el rail/la barra cuando hay pestaña activa — así que abrir
     * Agregar hacía desaparecer TODO el chrome. En el teléfono se veía como una hoja normal; en
     * escritorio el rail se esfumaba y la hoja quedaba flotando sobre un vacío negro.
     */
    @Test
    fun `abrir Agregar no cambia la pestaña activa, porque no entra a la pila`() {
        val pila = listOf<Screen>(Screen.Transactions())
        val destino = screenForTab(NavTab.ADD)

        val pilaDespues = if (opensAsOverlay(destino)) pila else pila + destino

        assertEquals(pila, pilaDespues, "Agregar no se apila: se dibuja encima de lo que ya estaba")
        assertEquals(
            NavTab.TRANSACTIONS, navTabFor(pilaDespues.last()),
            "la pestaña sigue siendo la de la pantalla de atrás, así que el rail y la barra siguen pintados",
        )
    }
}
