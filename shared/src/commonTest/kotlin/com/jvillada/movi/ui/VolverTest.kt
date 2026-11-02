package com.jvillada.movi.ui

import com.jvillada.movi.avisos.ABRIR_CAPTURA
import com.jvillada.movi.avisos.ABRIR_GRUPOS
import com.jvillada.movi.avisos.ABRIR_INICIO
import com.jvillada.movi.avisos.ABRIR_PLAN
import com.jvillada.movi.avisos.ABRIR_POR_REVISAR
import com.jvillada.movi.avisos.ABRIR_AGREGAR_GASTO
import com.jvillada.movi.avisos.ABRIR_AGREGAR_TRASPASO
import com.jvillada.movi.avisos.PREFIJO_ABRIR_AVISO
import com.jvillada.movi.avisos.PREFIJO_ABRIR_AVISO_CATEGORIA
import com.jvillada.movi.avisos.PREFIJO_ABRIR_GRUPO
import com.jvillada.movi.avisos.PREFIJO_ABRIR_PERIODO
import com.jvillada.movi.avisos.destinoDeAviso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 101 · Volver: la flecha ← y el atrás del sistema son la misma función
 *
 * La flecha tumbaba la app en Android 14 o anterior (`removeLast()`, ver [volver]); el atrás del sistema
 * no. Ahora las dos llaman [volver]. Acá se prueba la regla pura, sin Compose, para cada forma de llegar a
 * una pantalla sin nada detrás: la guía al arrancar, un aviso del teléfono (`DestinoDesdeAfuera`), un
 * atajo del ícono (el mismo `EXTRA_ABRIR`) y una pantalla sola en la pila (un enlace, una recarga de la
 * web). En todas: la pila nunca queda vacía, nunca se saca la última pantalla, y la flecha deja una
 * pantalla razonable. La prueba con la flecha de verdad (y con la lista que se comporta como Android 14)
 * es `LaFlechaNoTumbaLaAppTest`.
 */
class VolverTest {

    private fun pila(vararg pantallas: Screen): MutableList<Screen> = mutableListOf(*pantallas)

    // ── La regla ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun con_historial_la_flecha_y_el_atras_sacan_el_tope() {
        val conFlecha = pila(Screen.Dashboard, Screen.Tu, Screen.Accounts)
        val conAtras = pila(Screen.Dashboard, Screen.Tu, Screen.Accounts)
        volver(PilaDeHojas(), conFlecha, reserva = Screen.Tu)
        volver(PilaDeHojas(), conAtras)
        assertEquals(listOf(Screen.Dashboard, Screen.Tu), conFlecha)
        assertEquals(conFlecha, conAtras, "la flecha y el atrás hacen lo mismo cuando hay historial")
    }

    @Test
    fun la_flecha_sin_historial_va_a_su_reserva_con_el_inicio_debajo() {
        val p = pila(Screen.CapturaDelBanco)
        volver(PilaDeHojas(), p, reserva = Screen.Tu)
        assertEquals(listOf(Screen.Dashboard, Screen.Tu), p, "una raíz queda con el Inicio debajo")
    }

    @Test
    fun la_flecha_sin_historial_hacia_una_pantalla_que_no_es_raiz_tambien_deja_el_inicio_debajo() {
        val p = pila(Screen.SMSReconcile("sms_1"))
        volver(PilaDeHojas(), p, reserva = Screen.PorRevisar)
        assertEquals(listOf(Screen.Dashboard, Screen.PorRevisar), p)
        // Patrimonio vive sobre Tú (vuelta 38).
        val q = pila(Screen.Credits)
        volver(PilaDeHojas(), q, reserva = Screen.Accounts)
        assertEquals(listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts), q)
    }

    @Test
    fun la_flecha_sin_reserva_util_va_a_la_raiz_de_su_pestana_o_al_inicio() {
        // La reserva es la misma pantalla: una flecha que no hace nada no sirve.
        assertEquals(Screen.Tu, destinoSinHistoria(Screen.Accounts, Screen.Accounts))
        // Sin pestaña (la guía): el Inicio.
        assertEquals(Screen.Dashboard, destinoSinHistoria(Screen.PrimerUso(), Screen.PrimerUso()))
        assertEquals(Screen.Dashboard, destinoSinHistoria(Screen.PrimerUso(), null))
    }

    @Test
    fun el_atras_del_sistema_con_una_sola_pantalla_no_la_saca() {
        // Lo atiende Android (sale de la app): el handler va apagado y, si igual llegara, no se toca.
        val p = pila(Screen.Plan())
        assertFalse(hayAdondeVolver(PilaDeHojas(), p))
        volver(PilaDeHojas(), p)
        assertEquals(listOf<Screen>(Screen.Plan()), p)
    }

    @Test
    fun la_pila_nunca_queda_vacia() {
        val vacia = pila()
        volver(PilaDeHojas(), vacia, reserva = Screen.Tu)
        assertEquals(listOf<Screen>(Screen.Dashboard), vacia)
        val vaciaConAtras = pila()
        volver(PilaDeHojas(), vaciaConAtras)
        assertEquals(listOf<Screen>(Screen.Dashboard), vaciaConAtras)
    }

    @Test
    fun la_flecha_en_el_inicio_solo_no_hace_nada() {
        val p = pila(Screen.Dashboard)
        volver(PilaDeHojas(), p, reserva = Screen.Dashboard)
        assertEquals(listOf<Screen>(Screen.Dashboard), p)
    }

    // ── Cada forma de llegar sin nada detrás ────────────────────────────────────────────────────

    @Test
    fun la_guia_al_arrancar_y_lo_que_se_abre_desde_ella() {
        val p = pila(Screen.Dashboard)
        abrirLaGuiaAlArrancar(p)
        assertEquals(listOf<Screen>(Screen.PrimerUso()), p)
        // Desde la guía se abre Captura del banco; su flecha vuelve a la guía, no la saca.
        NavStack.navegar(p, Screen.CapturaDelBanco)
        volver(PilaDeHojas(), p, reserva = Screen.Tu)
        assertEquals(listOf<Screen>(Screen.PrimerUso()), p)
        // Y una flecha con la guía sola (si alguna vez la tuviera) no deja la pila vacía.
        volver(PilaDeHojas(), p, reserva = Screen.Dashboard)
        assertEquals(listOf<Screen>(Screen.Dashboard), p)
    }

    /** Los valores que traen los avisos y los atajos del ícono (`res/xml/shortcuts.xml`) en `EXTRA_ABRIR`. */
    private val desdeAfuera = listOf(
        ABRIR_POR_REVISAR, ABRIR_PLAN, ABRIR_CAPTURA, ABRIR_INICIO, ABRIR_GRUPOS,
        "${PREFIJO_ABRIR_AVISO}sms_1", "${PREFIJO_ABRIR_AVISO_CATEGORIA}sms_1",
        "${PREFIJO_ABRIR_PERIODO}2026-10", "${PREFIJO_ABRIR_GRUPO}esp_1",
        ABRIR_AGREGAR_GASTO, ABRIR_AGREGAR_TRASPASO,
    )

    @Test
    fun un_aviso_o_un_atajo_del_icono_con_la_app_cerrada() {
        for (valor in desdeAfuera) {
            val destino = assertNotNull(destinoDeAviso(valor), "«$valor» abre algo")
            // «Agregar» es una hoja encima de la pantalla, no va a la pila (ver [opensAsOverlay]).
            if (opensAsOverlay(destino)) continue
            for (arranque in listOf(Screen.Dashboard, Screen.Login)) {
                // La app arranca (con la huella, en el login), entra al Inicio y cumple el destino.
                val p = pila(arranque)
                NavStack.navegar(p, Screen.Dashboard)
                NavStack.navegar(p, destino)
                val reservas = listOf(Screen.Dashboard, Screen.PorRevisar, Screen.Tu, destino)
                for (reserva in reservas) {
                    val copia = p.toMutableList()
                    volver(PilaDeHojas(), copia, reserva)
                    assertTrue(copia.isNotEmpty(), "«$valor»: la pila nunca queda vacía")
                    if (p.size > 1) {
                        assertEquals(p.dropLast(1), copia, "«$valor»: con historial, la flecha vuelve un paso")
                    } else {
                        assertEquals(Screen.Dashboard, copia.first(), "«$valor»: sin historial, el Inicio queda abajo")
                    }
                }
            }
        }
    }

    @Test
    fun una_pantalla_sola_en_la_pila_de_un_enlace_o_una_recarga() {
        val solas = listOf(
            Screen.PorRevisar to Screen.Dashboard,
            Screen.SMSReconcile("sms_1") to Screen.PorRevisar,
            Screen.CapturaDelBanco to Screen.Tu,
            Screen.Accounts to Screen.Tu,
            Screen.Categorias to Screen.Tu,
            Screen.Plan() to Screen.Dashboard,
            Screen.Transactions() to Screen.Dashboard,
            Screen.Espacios to Screen.Tu,
            Screen.Tu to Screen.Tu,
        )
        for ((sola, reserva) in solas) {
            val p = pila(sola)
            volver(PilaDeHojas(), p, reserva)
            assertTrue(p.isNotEmpty(), "$sola: la pila nunca queda vacía")
            assertTrue(p.last() != sola, "$sola: la flecha lleva a otra pantalla, no se queda quieta")
            assertEquals(Screen.Dashboard, p.first(), "$sola: el Inicio queda abajo")
        }
    }

    // ── Las hojas: el atrás cierra como la X ────────────────────────────────────────────────────

    @Test
    fun una_hoja_que_nadie_anoto_se_cierra_con_el_atras_y_no_se_lleva_la_pantalla() {
        val hojas = PilaDeHojas()
        var cerrada = false
        hojas.registrar(HojaAbierta(deReserva = true) { cerrada = true })
        val p = pila(Screen.Dashboard, Screen.Tu)
        assertTrue(hayAdondeVolver(hojas, pila(Screen.Dashboard)), "con una hoja abierta el atrás la atiende")
        volver(hojas, p)
        assertTrue(cerrada)
        assertEquals(listOf(Screen.Dashboard, Screen.Tu), p, "cerrar la hoja no es navegar")
        assertEquals(0, hojas.cuantas)
    }

    @Test
    fun la_hoja_anotada_a_mano_gana_sobre_la_de_reserva_del_marco() {
        val hojas = PilaDeHojas()
        val orden = mutableListOf<String>()
        // El patrón de siempre: `AtrasCierraEstaHoja` y después la hoja, que anota la suya encima.
        hojas.registrar(HojaAbierta { orden += "propia" })
        hojas.registrar(HojaAbierta(deReserva = true) { orden += "marco" })
        assertEquals(1, hojas.cuantas, "la del marco no cuenta")
        volver(hojas, pila(Screen.Dashboard, Screen.Tu))
        assertEquals(listOf("propia"), orden, "el atrás propio (¿Descartar?, el sub-editor) sigue igual")
        volver(hojas, pila(Screen.Dashboard, Screen.Tu))
        assertEquals(listOf("propia", "marco"), orden)
    }
}
