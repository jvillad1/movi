package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.CanalDeCaptura
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.DestinoDeRevision
import com.jvillada.movi.ui.dashboard.cosasParaRevisar
import com.jvillada.movi.ui.dashboard.dashboardAlerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Banco mudo» del lado de la app (Ola 2): lo que dicen Hoy, la campana y la notificación del
 * teléfono, y a dónde llevan. La regla de cuándo es de `:core` (`BancoMudoTest`).
 */
class BancoMudoEnLaAppTest {

    private val bancolombia = OrigenMudo("SMS|bancolombia", CanalDeCaptura.SMS, "Bancolombia", diasSinCaptura = 4, ultima = 1_000L)
    private val nu = OrigenMudo("NOTIFICACION|nu", CanalDeCaptura.NOTIFICACION, "Nu", diasSinCaptura = 6, ultima = 2_000L)

    @Test
    fun hoy_lo_dice_primero_y_lleva_a_la_captura() {
        val cosas = cosasParaRevisar(
            checklist = emptyList(), categorias = emptyList(), flujoDelPeriodo = -1,
            smsPorConfirmar = 3, candidatosAPagoDeTarjeta = 0, bancosMudos = listOf(bancolombia),
        )
        val primera = cosas.first()
        assertEquals("Hace 4 días no llegan los SMS de Bancolombia. Revisa la captura", primera.texto)
        assertEquals(DestinoDeRevision.CAPTURA, primera.destino)
        assertTrue(primera.urgente)
    }

    @Test
    fun la_campana_tambien() {
        val alertas = dashboardAlerts(emptyList(), 0, 0, bancosMudos = listOf(bancolombia))
        assertEquals(Screen.CapturaDelBanco, alertas.single().target)
    }

    @Test
    fun la_notificacion_de_uno_y_de_varios() {
        assertNull(textoDeBancosMudos(emptyList()))
        val uno = textoDeBancosMudos(listOf(bancolombia))!!
        assertEquals("Hace 4 días no llega nada de Bancolombia", uno.titulo)
        assertEquals("No llegan los SMS de Bancolombia: revisa la captura — toca para verla", uno.texto)
        val dos = textoDeBancosMudos(listOf(nu, bancolombia))!!
        assertEquals("2 bancos dejaron de avisar", dos.titulo)
        assertEquals(listOf("Hace 6 días: las notificaciones de Nu", "Hace 4 días: los SMS de Bancolombia"), dos.lineas)
    }

    @Test
    fun el_mismo_silencio_no_suena_dos_veces() {
        // El Worker corre todos los días: mañana el mismo silencio tiene un día más pero la misma
        // última captura, y no vuelve a sonar. Uno nuevo sí.
        assertEquals(huellaDeBancosMudos(listOf(bancolombia)), huellaDeBancosMudos(listOf(bancolombia.copy(diasSinCaptura = 5))))
        assertNotEquals(huellaDeBancosMudos(listOf(bancolombia)), huellaDeBancosMudos(listOf(bancolombia, nu)))
        assertNotEquals(huellaDeBancosMudos(listOf(bancolombia)), huellaDeBancosMudos(listOf(bancolombia.copy(ultima = 9_000L))))
    }

    @Test
    fun tocar_el_aviso_abre_la_captura() {
        assertEquals(Screen.CapturaDelBanco, destinoDeAviso(ABRIR_CAPTURA))
    }
}
