package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La regla del «banco mudo» (Ola 2)
 *
 * Cuándo un origen de captura que andaba dejó de andar. Pura: el «ahora» y las capturas entran por
 * parámetro. Los tres casos que pidió el brief, más los frenos que evitan el ruido.
 */
class BancoMudoTest {

    private val dia = 86_400_000L
    /** 2026-10-01 15:00 UTC = 10:00 en Bogotá. */
    private val ahora = 1_790_866_800_000L

    /** Un mensaje de [origen] cada día, desde hace [desde] días hasta hace [hasta] días. */
    private fun diarios(origen: String, desde: Int, hasta: Int) =
        (hasta..desde).map { Captura(origen, ahora - it * dia) }

    @Test
    fun `sin historial no avisa`() {
        assertTrue(origenesMudos(emptyList(), ahora, diasDeSilencio = 3).isEmpty())
    }

    @Test
    fun `con capturas regulares y cuatro dias de silencio avisa`() {
        val mudos = origenesMudos(diarios("85540", desde = 30, hasta = 4), ahora, diasDeSilencio = 3)
        assertEquals(1, mudos.size)
        val mudo = mudos.single()
        assertEquals("Bancolombia", mudo.nombre)
        assertEquals(CanalDeCaptura.SMS, mudo.canal)
        assertEquals(4, mudo.diasSinCaptura)
        assertEquals("Hace 4 días no llegan los SMS de Bancolombia. Revisa la captura", textoDeOrigenMudo(mudo))
    }

    @Test
    fun `un origen que nunca fue regular no avisa`() {
        // Dos avisos en su vida, hace diez días: su silencio no dice nada.
        val pocos = listOf(Captura("Notificación · Nu", ahora - 10 * dia), Captura("Notificación · Nu", ahora - 11 * dia))
        assertTrue(origenesMudos(pocos, ahora, diasDeSilencio = 3).isEmpty())
        // Cinco de una misma tarde tampoco son una costumbre.
        val deUnaTarde = (1..5).map { Captura("Notificación · Nu", ahora - 6 * dia + it * 60_000L) }
        assertTrue(origenesMudos(deUnaTarde, ahora, diasDeSilencio = 3).isEmpty())
    }

    @Test
    fun `si todavia no pasaron los dias no avisa`() {
        assertTrue(origenesMudos(diarios("85540", desde = 30, hasta = 2), ahora, diasDeSilencio = 3).isEmpty())
    }

    @Test
    fun `un silencio que ya era normal en su historia no avisa`() {
        // Un banco que escribe cada cinco días: cuatro días sin nada es lo de siempre.
        val espaciados = (1..6).map { Captura("Correo · Bancolombia", ahora - (4 + it * 5) * dia) } +
            Captura("Correo · Bancolombia", ahora - 4 * dia)
        assertTrue(origenesMudos(espaciados, ahora, diasDeSilencio = 3).isEmpty())
    }

    @Test
    fun `cero apaga el aviso`() {
        assertTrue(origenesMudos(diarios("85540", desde = 30, hasta = 10), ahora, diasDeSilencio = 0).isEmpty())
    }

    @Test
    fun `los codigos de Bancolombia son un solo origen y cada canal es el suyo`() {
        val sms = diarios("85540", desde = 30, hasta = 20).filterIndexed { i, _ -> i % 2 == 0 } +
            diarios("891333", desde = 30, hasta = 20).filterIndexed { i, _ -> i % 2 == 1 }
        val notificaciones = diarios("Notificación · Nu", desde = 25, hasta = 6)
        val vivos = diarios("Correo · Bancolombia", desde = 25, hasta = 0)
        val mudos = origenesMudos(sms + notificaciones + vivos, ahora, diasDeSilencio = 3)

        assertEquals(listOf("SMS|bancolombia", "NOTIFICACION|nu"), mudos.map { it.clave }, "del más callado al menos")
        assertEquals("Hace 6 días no llegan las notificaciones de Nu. Revisa la captura", textoDeOrigenMudo(mudos[1]))
    }

    @Test
    fun `los nombres de origen se leen como una persona`() {
        assertEquals("Bancolombia", nombreDelOrigenDeCaptura("85540"))
        assertEquals("Nu", nombreDelOrigenDeCaptura("Notificación · Nu"))
        assertEquals(null, nombreDelOrigenDeCaptura("899776"))
        assertEquals(CanalDeCaptura.CORREO, canalDeCaptura("Correo · Bancolombia"))
        // Un SMS sin remitente es «tu banco».
        val sinRemitente = origenesMudos(diarios("", desde = 30, hasta = 5), ahora, diasDeSilencio = 3)
        assertEquals("tu banco", sinRemitente.single().nombre)
    }
}
