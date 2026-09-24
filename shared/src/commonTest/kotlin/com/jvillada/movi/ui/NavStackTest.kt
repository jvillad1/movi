package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.transactions.CHIP_POR_CONFIRMAR
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

    // ── Entrar no deja el login atrás ─────────────────────────────────────────

    /**
     * El defecto: `navigate` APILABA, así que entrar dejaba la pila en `[Login, Dashboard]` y
     * nada la limpiaba. El «atrás» del teléfono devolvía al formulario de entrada —con la
     * contraseña en blanco— a alguien que acababa de entrar, y con «Entrar con huella» prendido
     * volver ahí re-dispara el efecto de arranque de esa pantalla: el prompt del lector se abría
     * solo, sin que nadie lo pidiera.
     */
    @Test
    fun `entrar reemplaza la pila - el login no queda atras`() {
        val pila = mutableListOf<Screen>(Screen.Login)
        NavStack.navegar(pila, Screen.Dashboard)
        assertEquals(listOf<Screen>(Screen.Dashboard), pila.toList())
    }

    @Test
    fun `crear la cuenta tampoco deja nada atras`() {
        // Por Registro la pila era `[Login, Register, Dashboard]`, y «atrás» le mostraba un
        // «Crear cuenta» a quien acababa de crear una.
        val pila = mutableListOf<Screen>(Screen.Login)
        NavStack.navegar(pila, Screen.Register)
        assertEquals(listOf<Screen>(Screen.Login, Screen.Register), pila.toList(), "entre Login y Registro sí se apila")

        NavStack.navegar(pila, Screen.Dashboard)
        assertEquals(listOf<Screen>(Screen.Dashboard), pila.toList())
    }

    @Test
    fun `desde el Inicio para adelante se apila como siempre`() {
        val pila = mutableListOf<Screen>(Screen.Dashboard)
        NavStack.navegar(pila, Screen.Mas)
        NavStack.navegar(pila, Screen.Credits)
        assertEquals(listOf<Screen>(Screen.Dashboard, Screen.Mas, Screen.Credits), pila.toList())

        NavStack.navegar(pila, Screen.Credits)
        assertEquals(3, pila.size, "no se duplica la de arriba")
    }

    @Test
    fun `la regla mira el fondo de la pila, no el tope`() {
        assertTrue(NavStack.shouldReplaceAll(listOf(Screen.Login), Screen.Dashboard))
        assertTrue(NavStack.shouldReplaceAll(listOf(Screen.Login, Screen.Register), Screen.Dashboard))
        assertFalse(NavStack.shouldReplaceAll(listOf(Screen.Login), Screen.Register))
        assertFalse(NavStack.shouldReplaceAll(listOf(Screen.Dashboard, Screen.Mas), Screen.Credits))
        assertFalse(NavStack.shouldReplaceAll(emptyList(), Screen.Dashboard))
    }

    @Test
    fun `entrar y crear cuenta son las pantallas de antes de la sesion`() {
        assertTrue(NavStack.esDeAutenticacion(Screen.Login))
        assertTrue(NavStack.esDeAutenticacion(Screen.Register))
        listOf(Screen.Dashboard, Screen.Mas, Screen.Profile, Screen.Transactions())
            .forEach { assertFalse(NavStack.esDeAutenticacion(it), "$it") }
    }

    // ── Ola C: las cuatro pestañas (barra y rail) ─────────────────────────────

    /** La tabla entera, pantalla por pantalla: cada `Screen` que existe, con la pestaña que marca. */
    private val pestanaEsperada: List<Pair<Screen, NavTab?>> = listOf(
        Screen.Dashboard to NavTab.HOY,
        Screen.Transactions() to NavTab.MOVIMIENTOS,
        Screen.Transactions(CHIP_POR_CONFIRMAR) to NavTab.MOVIMIENTOS,
        Screen.Plan() to NavTab.PLAN,
        Screen.Plan(SEGMENTO_PRESUPUESTOS) to NavTab.PLAN,
        Screen.Budgets to NavTab.PLAN,
        Screen.Accounts to NavTab.PATRIMONIO,
        Screen.Credits to NavTab.PATRIMONIO,
        Screen.CuadreDeSaldos to NavTab.PATRIMONIO,
        Screen.Destinos to NavTab.PATRIMONIO,
        Screen.AccountDetail("acc-1", AccountGroup.DINERO) to NavTab.PATRIMONIO,
        Screen.AccountDetail("acc-2", AccountGroup.INVERSION) to NavTab.PATRIMONIO,
        Screen.AccountDetail("acc-3", AccountGroup.DEUDA) to NavTab.PATRIMONIO,
        // Ajustes y lo que se abre desde ahí: por el avatar, sin pestaña marcada.
        Screen.Mas to null,
        Screen.Profile to null,
        Screen.Categorias to null,
        Screen.Documentos to null,
        Screen.Compartir to null,
        Screen.AIChat() to null,
        Screen.SMSInbox to null,
        Screen.SMSReconcile("s1") to null,
        Screen.PrimerosPasos to null,
        Screen.Goals to null,
        Screen.Extractos to null,
        // Sin navegación: autenticación y flujos a pantalla completa.
        Screen.Login to null,
        Screen.Register to null,
        Screen.QuickAdd() to null,
        Screen.OCRCapture to null,
        Screen.OCRConfirm to null,
        Screen.ScreenEditor to null,
        Screen.StatementReview("{}") to null,
        Screen.ImportDetail("i1") to null,
    )

    @Test
    fun `cada pantalla marca la pestana de la pregunta que contesta`() {
        pestanaEsperada.forEach { (pantalla, pestana) ->
            assertEquals(pestana, navTabFor(pantalla), "$pantalla")
        }
    }

    @Test
    fun `cada pestana abre su pantalla principal`() {
        assertEquals(Screen.Dashboard, screenForTab(NavTab.HOY))
        assertEquals(Screen.Transactions(), screenForTab(NavTab.MOVIMIENTOS))
        assertEquals(Screen.Plan(), screenForTab(NavTab.PLAN))
        assertEquals(Screen.Plan(SEGMENTO_PAGOS), screenForTab(NavTab.PLAN))
        assertEquals(Screen.Accounts, screenForTab(NavTab.PATRIMONIO))
        assertEquals(Screen.QuickAdd(), screenForTab(NavTab.ADD))
    }

    @Test
    fun `Ajustes no marca pestana pero sigue con la barra abajo`() {
        listOf(Screen.Mas, Screen.Profile, Screen.Categorias, Screen.Documentos, Screen.Compartir,
            Screen.AIChat(), Screen.SMSInbox, Screen.PrimerosPasos)
            .forEach {
                assertNull(navTabFor(it), "$it")
                assertTrue(esDeAjustes(it), "$it")
                assertTrue(muestraLaNavegacion(it), "$it")
            }
    }

    @Test
    fun `las cuatro pestanas llevan la barra y los flujos a pantalla completa no`() {
        listOf(Screen.Dashboard, Screen.Transactions(), Screen.Plan(), Screen.Accounts, Screen.Credits)
            .forEach { assertTrue(muestraLaNavegacion(it), "$it") }
        listOf(Screen.Login, Screen.Register, Screen.QuickAdd(), Screen.OCRCapture, Screen.ScreenEditor,
            Screen.StatementReview("{}"), Screen.ImportDetail("i1"))
            .forEach { assertFalse(muestraLaNavegacion(it), "$it") }
    }

    @Test
    fun `la reserva del volver y la pestaña resaltada del detalle no se contradicen`() {
        // Si la flecha ‹ sin historial cae en Créditos, la pestaña activa mientras se ve el
        // detalle también tiene que ser la de Créditos (y lo mismo con Cuentas).
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

    // ── Ola C: Recurrentes se mudó de Movimientos a Plan ──────────────────────

    @Test
    fun `pedir Movimientos con el chip Recurrentes lleva a Plan Pagos del mes`() {
        assertEquals(Screen.Plan(SEGMENTO_PAGOS), destinoVigente(Screen.Transactions(CHIP_RECURRENTES)))
        // El resto de Movimientos no se toca.
        assertEquals(Screen.Transactions(), destinoVigente(Screen.Transactions()))
        assertEquals(Screen.Transactions(CHIP_POR_CONFIRMAR), destinoVigente(Screen.Transactions(CHIP_POR_CONFIRMAR)))
        assertEquals(Screen.Mas, destinoVigente(Screen.Mas))
    }

    @Test
    fun `navegar resuelve el destino antes de apilarlo`() {
        val pila = mutableListOf<Screen>(Screen.Dashboard)
        NavStack.navegar(pila, Screen.Transactions(CHIP_RECURRENTES))
        assertEquals(listOf(Screen.Dashboard, Screen.Plan(SEGMENTO_PAGOS)), pila)

        // Y compara contra lo que de verdad se apiló: pedirlo otra vez estando en Plan no duplica.
        NavStack.navegar(pila, Screen.Transactions(CHIP_RECURRENTES))
        assertEquals(listOf(Screen.Dashboard, Screen.Plan(SEGMENTO_PAGOS)), pila)
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
            NavTab.MOVIMIENTOS, navTabFor(pilaDespues.last()),
            "la pestaña sigue siendo la de la pantalla de atrás, así que el rail y la barra siguen pintados",
        )
    }
}
