package com.jvillada.movi.shared.model

import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La regla que decide qué dice Movi sobre su propia captura de SMS.
 *
 * Nace de un defecto caro: la captura no entregó **ni un mensaje** durante varias entregas y
 * nadie lo supo. Lo que estos tests fijan es lo que faltaba entonces — que el hecho se pueda
 * afirmar sin adornos, y que lo que se afirme sea lo observado y nada más.
 */
class CapturaDeSmsTest {

    // ── Qué llegó, y cuándo llegó lo último ────────────────────────────────────

    @Test
    fun `sin mensajes, nunca llego nada y no hay ultimo`() {
        val captura = capturaDeSms(emptyList())
        assertEquals(0, captura.total)
        assertNull(captura.ultimo)
        assertTrue(captura.nuncaLlegoNada)
    }

    @Test
    fun `con uno, ese es el ultimo`() {
        val captura = capturaDeSms(listOf("2026-08-01 10:00"))
        assertEquals(1, captura.total)
        assertEquals("2026-08-01 10:00", captura.ultimo)
        assertFalse(captura.nuncaLlegoNada)
    }

    @Test
    fun `con varios gana el mas reciente, sin importar el orden en que vengan`() {
        val captura = capturaDeSms(
            listOf("2026-08-01 10:00", "2026-09-03 07:15", "2026-08-30 23:59", "2026-01-01 00:00"),
        )
        assertEquals(4, captura.total)
        assertEquals("2026-09-03 07:15", captura.ultimo)
    }

    /**
     * La columna `time` es un varchar libre y conviven dos formas: `"yyyy-MM-dd HH:mm"` (lo que
     * escriben los dos caminos de captura) y la ISO con 'T' y segundos, que el server tolera y
     * que tienen las filas sembradas a mano. Comparando los strings crudos, `' '` va antes que
     * `'T'` y el de las 09:00 le ganaría al de las 10:00 del mismo día: el «último mensaje»
     * sería el anteúltimo, en una pantalla que existe justamente para no mentir sobre esto.
     */
    @Test
    fun `mezclar el formato con T y el de espacio no desordena cual es el ultimo`() {
        assertEquals(
            "2026-08-01T10:00:00",
            capturaDeSms(listOf("2026-08-01 09:00", "2026-08-01T10:00:00")).ultimo,
        )
        assertEquals(
            "2026-08-02 08:00",
            capturaDeSms(listOf("2026-08-02 08:00", "2026-08-01T23:59:00")).ultimo,
        )
    }

    /** Un `time` vacío no puede ser «el último», pero la fila existió: sigue contando en el total. */
    @Test
    fun `un time vacio cuenta como mensaje recibido pero no gana como ultimo`() {
        val captura = capturaDeSms(listOf("", "2026-08-01 10:00"))
        assertEquals(2, captura.total)
        assertEquals("2026-08-01 10:00", captura.ultimo)
    }

    /** Todos ilegibles: llegaron mensajes (eso es lo que importa) y no se inventa una fecha. */
    @Test
    fun `si ningun time sirve, igual consta que llegaron`() {
        val captura = capturaDeSms(listOf("", "   "))
        assertEquals(2, captura.total)
        assertNull(captura.ultimo)
        assertFalse(captura.nuncaLlegoNada)
    }

    // ── La fecha como se lee ───────────────────────────────────────────────────

    @Test
    fun `la fecha se lee igual venga con T o con espacio, y sin segundos`() {
        assertEquals("1 de agosto a las 10:00 a. m.", fechaLegibleDeSms("2026-08-01 10:00"))
        assertEquals("1 de agosto a las 10:00 a. m.", fechaLegibleDeSms("2026-08-01T10:00:00"))
    }

    /** El caso que sale crudo en el teléfono del dueño: «2026-09-23 19:52». */
    @Test
    fun `la tarde y la noche se dicen en formato de 12 horas`() {
        assertEquals("23 de septiembre a las 7:52 p. m.", fechaLegibleDeSms("2026-09-23 19:52"))
    }

    /** Las doce del día y las doce de la noche no se dicen «las cero» ni «las trece menos una». */
    @Test
    fun `mediodia y medianoche se dicen las doce`() {
        assertEquals("1 de agosto a las 12:05 a. m.", fechaLegibleDeSms("2026-08-01 00:05"))
        assertEquals("1 de agosto a las 12:30 p. m.", fechaLegibleDeSms("2026-08-01 12:30"))
    }

    /** Un `time` con otra forma se muestra tal cual: antes eso que inventar una fecha. */
    @Test
    fun `un time raro se muestra tal cual`() {
        assertEquals("ayer", fechaLegibleDeSms(" ayer "))
    }

    // ── Lo que dice la pantalla ────────────────────────────────────────────────

    @Test
    fun `nunca llego nada es lo unico que se pinta como alerta`() {
        val aviso = avisoDeCaptura(CapturaDeSms())
        assertTrue(aviso.esAlerta)
        assertEquals("NUNCA HA LLEGADO UN MENSAJE", aviso.rotulo)
        assertTrue(
            "Movi todavía no ha recibido ningún mensaje de tu banco" in aviso.detalle,
            "el hecho se dice completo y sin rodeos: $aviso",
        )
    }

    /**
     * **Nunca se afirma que la captura funcione.** Un permiso concedido con el receiver muerto
     * es exactamente el estado en el que estuvo el dueño mientras la pantalla le decía
     * «AUTO-LECTURA ACTIVA». Lo único que se puede afirmar es lo que llegó.
     */
    @Test
    fun `haya llegado o no, nunca se dice que la captura este activa`() {
        listOf(CapturaDeSms(), CapturaDeSms(1, "2026-08-01 10:00"), CapturaDeSms(12, "2026-09-03 07:15"))
            .map { avisoDeCaptura(it) }
            .forEach { aviso ->
                val texto = (aviso.rotulo + " " + aviso.detalle).lowercase()
                listOf("activa", "activo", "funcionando", "andando correctamente").forEach { palabra ->
                    assertFalse(palabra in texto, "«$palabra» afirma algo que no se observó: $aviso")
                }
            }
    }

    @Test
    fun `con mensajes se dice cuando llego el ultimo, y no es alerta`() {
        val uno = avisoDeCaptura(CapturaDeSms(1, "2026-08-01T10:00:00"))
        assertFalse(uno.esAlerta)
        assertEquals("ÚLTIMO MENSAJE RECIBIDO", uno.rotulo)
        assertTrue("1 de agosto a las 10:00 a. m." in uno.detalle, uno.detalle)
        assertTrue("El único mensaje" in uno.detalle, uno.detalle)

        val varios = avisoDeCaptura(CapturaDeSms(12, "2026-09-03 07:15"))
        assertFalse(varios.esAlerta)
        assertTrue("los 12 mensajes" in varios.detalle, varios.detalle)
        assertTrue("3 de septiembre a las 7:15 a. m." in varios.detalle, varios.detalle)
    }

    // ── Cuándo aparece en el Inicio, y cuándo deja de aparecer ─────────────────

    @Test
    fun `el Inicio avisa solo cuando nunca llego nada`() {
        assertEquals(
            "Movi nunca ha recibido un mensaje de tu banco",
            alertaDeCapturaEnInicio(CapturaDeSms(), silenciada = false),
        )
    }

    /**
     * El primer freno al ruido: en cuanto llega un mensaje, la fila se apaga **sola y para
     * siempre**. Quien de verdad usa la captura la ve mientras está rota, que es cuando sirve.
     */
    @Test
    fun `en cuanto llega el primer mensaje el Inicio deja de avisar`() {
        assertNull(alertaDeCapturaEnInicio(CapturaDeSms(1, "2026-08-01 10:00"), silenciada = false))
        assertNull(alertaDeCapturaEnInicio(CapturaDeSms(90, "2026-09-03 07:15"), silenciada = false))
    }

    /**
     * El segundo freno, el que salva a quien nunca va a usar la captura (iOS, solo web, otro
     * país): para esa persona el freno de arriba no se dispara jamás y la fila sería un
     * reproche eterno.
     */
    @Test
    fun `silenciado, el Inicio no dice nada aunque nunca haya llegado nada`() {
        assertNull(alertaDeCapturaEnInicio(CapturaDeSms(), silenciada = true))
    }

    /**
     * Silenciar calla el recordatorio del Inicio, **no** el hecho. Que el silencio no sea
     * siquiera un parámetro de [avisoDeCaptura] es la garantía: no hay forma de que la bandeja
     * deje de decir que nunca llegó nada.
     */
    @Test
    fun `silenciar el Inicio no puede cambiar lo que dice la bandeja`() {
        assertTrue(avisoDeCaptura(CapturaDeSms()).esAlerta)
        assertNull(alertaDeCapturaEnInicio(CapturaDeSms(), silenciada = true))
    }

    @Test
    fun `el movimiento de un SMS se fecha cuando llego el mensaje, no cuando se confirma`() {
        val bogota = kotlinx.datetime.TimeZone.of("America/Bogota")
        val ahora = kotlinx.datetime.LocalDateTime(2026, 9, 13, 9, 0).toInstant(bogota).toEpochMilliseconds()
        val esperado = kotlinx.datetime.LocalDateTime(2026, 9, 10, 13, 35).toInstant(bogota).toEpochMilliseconds()
        kotlin.test.assertEquals(esperado, momentoDelSms("2026-09-10 13:35", ahora, bogota))
        // Un reloj de teléfono adelantado no fecha en el futuro, y un texto raro cae en ahora.
        kotlin.test.assertEquals(ahora, momentoDelSms("2026-09-14 08:00", ahora, bogota))
        kotlin.test.assertEquals(ahora, momentoDelSms("10/09/2026", ahora, bogota))
        kotlin.test.assertEquals(ahora, momentoDelSms("", ahora, bogota))
    }

    @Test
    fun `un SMS de antes del primer movimiento de la cuenta ya esta en el saldo inicial`() {
        val zona = com.jvillada.movi.shared.time.AppTimeZone.zone
        fun dia(d: Int, h: Int = 12) = kotlinx.datetime.LocalDateTime(2026, 8, d, h, 0).toInstant(zona).toEpochMilliseconds()
        val cuenta = listOf(
            FinancialEvent(id = "apertura", accountId = "a", type = TransactionType.INCOME, amount = 1, category = "Saldo inicial", description = "", timestamp = dia(25)),
            FinancialEvent(id = "gasto", accountId = "a", type = TransactionType.EXPENSE, amount = 1, category = "Comida", description = "", timestamp = dia(27)),
        )
        kotlin.test.assertEquals(kotlinx.datetime.LocalDate(2026, 8, 25), inicioDeLaCuentaSiElSmsEsAnterior(dia(11), cuenta))
        kotlin.test.assertEquals(null, inicioDeLaCuentaSiElSmsEsAnterior(dia(25, 8), cuenta), "el mismo día no se da por incluido")
        kotlin.test.assertEquals(null, inicioDeLaCuentaSiElSmsEsAnterior(dia(28), cuenta))
        kotlin.test.assertEquals(null, inicioDeLaCuentaSiElSmsEsAnterior(dia(1), emptyList()))
    }
}
