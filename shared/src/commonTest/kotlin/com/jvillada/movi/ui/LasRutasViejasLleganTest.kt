package com.jvillada.movi.ui

import com.jvillada.movi.avisos.ABRIR_AGREGAR_GASTO
import com.jvillada.movi.avisos.ABRIR_AGREGAR_TRASPASO
import com.jvillada.movi.avisos.ABRIR_CAPTURA
import com.jvillada.movi.avisos.ABRIR_PLAN
import com.jvillada.movi.avisos.ABRIR_POR_REVISAR
import com.jvillada.movi.avisos.abrirElAviso
import com.jvillada.movi.avisos.abrirElAvisoEnLaCategoria
import com.jvillada.movi.avisos.abrirElPeriodo
import com.jvillada.movi.avisos.destinoDeAviso
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import com.jvillada.movi.ui.sdui.screenForTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * # Ninguna ruta se pierde con las 4 pestañas (vuelta 38)
 *
 * Inicio · Actividad · Pagos · Tú. Patrimonio, Espacios (Personas) y Ajustes dejaron de ser raíces:
 * los avisos del teléfono y los atajos del ícono (`EXTRA_ABRIR` → `destinoDeAviso`), los destinos SDUI
 * de Hoy y los enlaces de las pantallas tienen que seguir llegando a la pantalla correcta, con la
 * pestaña correcta marcada. Se cumple igual que en App.kt: Agregar se abre encima (no entra a la
 * pila), todo lo demás pasa por [NavStack.navegar] desde el Inicio.
 */
class LasRutasViejasLleganTest {

    /** Lo que hace App.kt con un destino que llega desde afuera, con la app en el Inicio. */
    private fun llegar(destino: Screen): Pair<List<Screen>, Screen.QuickAdd?> {
        val pila = mutableListOf<Screen>(Screen.Dashboard)
        if (opensAsOverlay(destino)) return pila to (destino as Screen.QuickAdd)
        NavStack.navegar(pila, destino)
        return pila to null
    }

    private data class Esperado(val arriba: Screen, val pestana: NavTab?, val pila: List<Screen>? = null)

    // ── Los avisos y los atajos del ícono (`EXTRA_ABRIR`) ──────────────────────

    private val avisos: List<Pair<String, Esperado>> = listOf(
        ABRIR_POR_REVISAR to Esperado(Screen.PorRevisar, NavTab.MOVIMIENTOS),
        ABRIR_PLAN to Esperado(Screen.Plan(), NavTab.PLAN, listOf(Screen.Dashboard, Screen.Plan())),
        ABRIR_CAPTURA to Esperado(Screen.CapturaDelBanco, NavTab.TU),
        abrirElPeriodo("2026-09") to Esperado(Screen.DetalleDePeriodo("2026-09"), NavTab.PLAN),
        abrirElAviso("sms_1") to Esperado(Screen.SMSReconcile("sms_1"), NavTab.MOVIMIENTOS),
        abrirElAvisoEnLaCategoria("sms_1") to Esperado(Screen.SMSReconcile("sms_1", abrirCategoria = true), NavTab.MOVIMIENTOS),
    )

    @Test
    fun `cada aviso del telefono llega a su pantalla con su pestana`() {
        avisos.forEach { (valor, esperado) ->
            val destino = assertNotNull(destinoDeAviso(valor), valor)
            val (pila, hoja) = llegar(destino)
            assertEquals(null, hoja, valor)
            assertEquals(esperado.arriba, pila.last(), valor)
            assertEquals(esperado.pestana, destinoResaltadoDeLaPila(pila), valor)
            esperado.pila?.let { assertEquals(it, pila, valor) }
        }
    }

    /** Los atajos del ícono de Android (`res/xml/shortcuts.xml`): Gasto, Traspaso y Por revisar. */
    @Test
    fun `los atajos del icono abren Agregar en su pestana, encima del Inicio`() {
        listOf(
            ABRIR_AGREGAR_GASTO to PestanaDeAgregar.GASTO,
            ABRIR_AGREGAR_TRASPASO to PestanaDeAgregar.TRASPASO,
        ).forEach { (valor, pestana) ->
            val (pila, hoja) = llegar(assertNotNull(destinoDeAviso(valor), valor))
            assertEquals(listOf<Screen>(Screen.Dashboard), pila, "$valor no toca la pila")
            assertEquals(pestana, assertNotNull(hoja, valor).presetPestana)
            assertEquals(NavTab.HOY, destinoResaltadoDeLaPila(pila), "la barra sigue en Inicio")
        }
        val (pila, _) = llegar(assertNotNull(destinoDeAviso(ABRIR_POR_REVISAR)))
        assertEquals(listOf(Screen.Dashboard, Screen.PorRevisar), pila)
    }

    // ── Los destinos SDUI de Hoy (y del Editor de pantallas) ───────────────────

    private val destinosSdui: List<Pair<String, Esperado>> = listOf(
        "dashboard" to Esperado(Screen.Dashboard, NavTab.HOY, listOf(Screen.Dashboard)),
        "transactions" to Esperado(Screen.Transactions(), NavTab.MOVIMIENTOS, listOf(Screen.Dashboard, Screen.Transactions())),
        "budgets" to Esperado(Screen.Plan(SEGMENTO_PRESUPUESTOS), NavTab.PLAN),
        "subscriptions" to Esperado(Screen.Plan(SEGMENTO_PAGOS), NavTab.PLAN),
        "recurrentes" to Esperado(Screen.Plan(SEGMENTO_PAGOS), NavTab.PLAN),
        // Ajustes y Perfil viven en «Tú».
        "mas" to Esperado(Screen.Tu, NavTab.TU, listOf(Screen.Dashboard, Screen.Tu)),
        "profile" to Esperado(Screen.Tu, NavTab.TU, listOf(Screen.Dashboard, Screen.Tu)),
        // Patrimonio (y los destinos viejos que llevan ahí) se abre encima de Tú.
        "accounts" to Esperado(Screen.Accounts, NavTab.TU, listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts)),
        "goals" to Esperado(Screen.Accounts, NavTab.TU, listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts)),
        "investments" to Esperado(Screen.Accounts, NavTab.TU, listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts)),
        "credits" to Esperado(Screen.Credits, NavTab.TU),
        "categorias" to Esperado(Screen.Categorias, NavTab.TU),
        "extractos" to Esperado(Screen.Documentos, NavTab.TU),
        // Movi AI: en el teléfono sin pestaña; en el rail, su lugar propio.
        "aichat" to Esperado(Screen.AIChat(), NavTab.MOVI_AI),
    )

    @Test
    fun `cada destino SDUI llega a su pantalla con su pestana`() {
        destinosSdui.forEach { (target, esperado) ->
            val (pila, _) = llegar(assertNotNull(screenForTarget(target), target))
            assertEquals(esperado.arriba, pila.last(), target)
            assertEquals(esperado.pestana, destinoResaltadoDeLaPila(pila), target)
            esperado.pila?.let { assertEquals(it, pila, target) }
        }
        assertIs<Screen.QuickAdd>(screenForTarget("quickadd"))
    }

    // ── Los enlaces de las pantallas a lo que eran raíces ──────────────────────

    @Test
    fun `los enlaces de Inicio a Patrimonio, Personas y Ajustes llegan encima de Tu`() {
        listOf(
            // El tramo «Tu patrimonio» de Inicio, «Repartir» sin espacios y el avatar.
            Screen.Accounts to listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts),
            Screen.Espacios to listOf(Screen.Dashboard, Screen.Tu, Screen.Espacios),
            Screen.Tu to listOf(Screen.Dashboard, Screen.Tu),
            Screen.Mas to listOf(Screen.Dashboard, Screen.Tu),
        ).forEach { (destino, pila) ->
            assertEquals(pila, llegar(destino).first, "$destino")
            assertEquals(NavTab.TU, destinoResaltadoDeLaPila(pila))
        }
        // «3 personas o comercios sin nombre» y el detalle de una cuenta se apilan sobre Inicio,
        // marcando Tú; su volver regresa a Inicio, de donde se pidieron.
        listOf(
            Screen.Destinos(sugeridos = true),
            Screen.AccountDetail("acc-1", AccountGroup.DINERO),
            Screen.CuadreDeSaldos,
        ).forEach { destino ->
            val (pila, _) = llegar(destino)
            assertEquals(listOf(Screen.Dashboard, destino), pila, "$destino")
            assertEquals(NavTab.TU, destinoResaltadoDeLaPila(pila), "$destino")
        }
    }
}
