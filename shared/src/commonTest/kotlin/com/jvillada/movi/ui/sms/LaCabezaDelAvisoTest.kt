package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # La cabeza de una tarjeta de aviso se lee primero (vuelta de diseño 2)
 *
 * En producción la tarjeta de «Por revisar» se titulaba con el banco («Falabella») y llevaba la marca
 * del comercio (la R de Rappi), con el monto enterrado en el texto del SMS. Ahora: el comercio con su
 * marca, el monto a la derecha y «banco · hora» debajo.
 */
class LaCabezaDelAvisoTest {

    private val hoy = LocalDate(2026, 10, 26)

    private fun aviso(bank: String, texto: String, time: String = "2026-10-26 11:02", id: String = "s1") =
        SmsMessage(id = id, time = time, bank = bank, text = texto, state = SMS_STATE_PENDING, det = "")

    private val deFalabella = aviso("Falabella", "Compra aprobada por \$42.500 en RAPPI el 02/09/2026.")

    @Test
    fun `el titulo es el comercio con su marca, no el banco`() {
        val cabeza = cabezaDelAviso(
            listOf(deFalabella),
            ParsedSms(42_500.0, "Rappi", TransactionType.EXPENSE, "Restaurantes"),
            hoy,
        )
        assertEquals("Rappi", cabeza.titulo)
        assertEquals("rappi", cabeza.marca?.id)
        assertEquals("Falabella · 11:02 a. m.", cabeza.subtitulo)
        assertEquals(42_500L, cabeza.monto)
        assertEquals(-42_500L, cabeza.montoConSigno, "un gasto va con «−», en el color del texto")
        assertEquals(TonoDeCifra.Neutro, cabeza.tono)
    }

    @Test
    fun `sin lectura, el comercio que nombra el texto, y el banco nunca es un comercio`() {
        val cabeza = cabezaDelAviso(listOf(deFalabella), leido = null, hoy = hoy)
        assertEquals("Rappi", cabeza.titulo)
        assertNull(cabeza.monto, "sin lectura no se inventa un monto: lo dice el texto")
        val soloElBanco = cabezaDelAviso(listOf(aviso("Bancolombia", "Bancolombia le informa un retiro.")), null, hoy)
        assertEquals("Bancolombia", soloElBanco.titulo)
    }

    @Test
    fun `si Movi no entendio el comercio, el titulo es el banco con su marca y abajo la hora`() {
        val cabeza = cabezaDelAviso(
            listOf(aviso("Notificación · Bancolombia", "Transferiste \$50.000 desde tu cuenta *8133.")),
            ParsedSms(50_000.0, "Movimiento", TransactionType.EXPENSE, "Otros"),
            hoy,
        )
        assertEquals("Bancolombia", cabeza.titulo)
        assertEquals("bancolombia", cabeza.marca?.id)
        assertEquals("11:02 a. m.", cabeza.subtitulo)
        assertEquals(50_000L, cabeza.monto)
    }

    @Test
    fun `un comercio sin marca va con el icono de su categoria`() {
        val cabeza = cabezaDelAviso(
            listOf(aviso("85540", "Bancolombia: Compraste \$12.300,00 en PANADERIA LA ESQ con tu T.Deb *1111.")),
            ParsedSms(12_300.0, "PANADERIA LA ESQ", TransactionType.EXPENSE, "Comida"),
            hoy,
        )
        assertEquals("Panaderia La Esq", cabeza.titulo, "el comercio no grita (vuelta de diseño 4)")
        assertNull(cabeza.marca)
        assertEquals("Comida", cabeza.categoria)
        // Vuelta 44: el código del remitente se dice como el banco que lo manda.
        assertEquals("Bancolombia · 11:02 a. m.", cabeza.subtitulo, "«85540» es Bancolombia")
    }

    @Test
    fun `un ingreso va en entra y un pago de tarjeta entre cuentas`() {
        val ingreso = cabezaDelAviso(
            listOf(aviso("Nu", "Te llegó dinero de CAROLINA por \$200.000")),
            ParsedSms(200_000.0, "Carolina", TransactionType.INCOME, "Ingreso"),
            hoy,
        )
        assertEquals(TonoDeCifra.Ingreso, ingreso.tono)
        assertEquals(200_000L, ingreso.montoConSigno)
        val pago = cabezaDelAviso(
            listOf(aviso("Nu", "Pagaste tu tarjeta de crédito Nu por \$1.998.960")),
            ParsedSms(1_998_960.0, "Nu", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY),
            hoy,
        )
        assertEquals(TonoDeCifra.EntreCuentas, pago.tono)
    }

    @Test
    fun `un aviso sin leer muestra el monto que se puede anotar igual`() {
        val cabeza = cabezaDelAviso(
            listOf(aviso("Notificación · Google Wallet", "COP27,500 with Debito Mastercard")),
            leido = null,
            hoy = hoy,
            sinLeer = AvisoSinLeer(monto = 27_500.0),
        )
        assertEquals("Google Wallet", cabeza.titulo)
        assertEquals(27_500L, cabeza.monto)
    }

    @Test
    fun `un aviso de otro dia dice el dia`() {
        val cabeza = cabezaDelAviso(listOf(aviso("Nu", "Compraste en Éxito", time = "2026-10-25 09:15")), null, hoy)
        assertEquals("Nu · Ayer", cabeza.subtitulo)
    }

    // ── Vuelta de diseño 23: cuándo fue, dicho corto ──────────────────────────────────────

    @Test
    fun `la fecha relativa hoy ayer hace N dias y la fecha`() {
        assertEquals("11:02 a. m.", fechaRelativaDelAviso(hoy, hoy, hora = "11:02 a. m."))
        assertEquals("Hoy", fechaRelativaDelAviso(hoy, hoy, hora = null))
        assertEquals("Ayer", fechaRelativaDelAviso(LocalDate(2026, 10, 25), hoy, hora = "9:15 a. m."))
        assertEquals("hace 2 días", fechaRelativaDelAviso(LocalDate(2026, 10, 24), hoy, hora = null))
        assertEquals("hace 6 días", fechaRelativaDelAviso(LocalDate(2026, 10, 20), hoy, hora = null))
        assertEquals("19 oct", fechaRelativaDelAviso(LocalDate(2026, 10, 19), hoy, hora = null), "de una semana para atrás, la fecha")
        assertEquals("2 sep", fechaRelativaDelAviso(LocalDate(2026, 9, 2), hoy, hora = null))
        assertEquals("28 dic 2025", fechaRelativaDelAviso(LocalDate(2025, 12, 28), hoy, hora = null), "con el año si es de otro")
    }

    @Test
    fun `un aviso de hace dos meses no dice solo la hora`() {
        val viejo = aviso("Falabella", "Compra aprobada por \$42.500 en RAPPI el 02/09/2026.", time = "2026-09-02 11:02")
        val cabeza = cabezaDelAviso(listOf(viejo), ParsedSms(42_500.0, "Rappi", TransactionType.EXPENSE, "Restaurantes"), hoy)
        assertEquals("Falabella · 2 sep", cabeza.subtitulo)
    }

    @Test
    fun `la fecha que leyo Movi gana sobre la de llegada`() {
        // El correo de PSE llegó hoy, pero el pago fue hace tres días.
        val cabeza = cabezaDelAviso(
            listOf(aviso("Correo · Bancolombia", "Pago PSE a EPM", time = "2026-10-26 08:00")),
            ParsedSms(120_000.0, "EPM", TransactionType.EXPENSE, "Servicios", fecha = "2026-10-23"),
            hoy,
        )
        assertEquals("Bancolombia · hace 3 días", cabeza.subtitulo)
    }

    @Test
    fun `sin hora de llegada legible vale la fecha escrita en el texto`() {
        // La cuenta demo: el `time` es «11:02» a secas y el SMS dice «el 02/09/2026».
        val demo = aviso("Falabella", "Compra aprobada por \$42.500 en RAPPI el 02/09/2026.", time = "11:02")
        val cabeza = cabezaDelAviso(listOf(demo), ParsedSms(42_500.0, "Rappi", TransactionType.EXPENSE, "Restaurantes"), hoy)
        assertEquals("Falabella · 2 sep", cabeza.subtitulo)
        assertEquals(LocalDate(2026, 9, 2), fechaEnElTexto("el 02/09/2026."))
        assertEquals(LocalDate(2026, 9, 2), fechaEnElTexto("el 2/9/26"))
        assertNull(fechaEnElTexto("el 31/02/2026"), "una fecha que no existe no se inventa")
        assertNull(fechaEnElTexto("sin fecha"))
        // Sin nada que leer, el `time` tal cual (como antes).
        assertEquals("11:02", cuandoFueElAviso("11:02", "sin fecha", null, hoy))
    }

    // ── Vuelta de diseño 23: la cuenta es de otro banco ───────────────────────────────────

    private fun cuenta(nombre: String) = Account(nombre.lowercase(), nombre, AccountType.SAVINGS, 0)

    @Test
    fun `un aviso de Falabella con una cuenta de Bancolombia lo advierte`() {
        assertEquals(
            "Ojo: el aviso es de Falabella y la cuenta es de Bancolombia",
            advertenciaDeBancos(listOf("Falabella"), cuenta("Bancolombia Ahorros")),
        )
        assertEquals(
            "Ojo: el aviso es de Nu y la cuenta es de Bancolombia",
            advertenciaDeBancos(listOf("Notificación · Nu"), cuenta("Bancolombia Ahorros")),
        )
    }

    @Test
    fun `sin con que comparar o del mismo banco no dice nada`() {
        assertNull(advertenciaDeBancos(listOf("Correo · Bancolombia"), cuenta("Bancolombia Ahorros")), "el mismo banco")
        assertNull(advertenciaDeBancos(listOf("Notificación · Bancolombia"), cuenta("AMEX 9208")), "la AMEX es de Bancolombia")
        assertNull(advertenciaDeBancos(listOf("85540"), cuenta("Nu")), "el código del remitente no dice el banco")
        assertNull(advertenciaDeBancos(listOf("Comprobante · transferencia.png"), cuenta("Nu")), "un comprobante no es un banco")
        assertNull(advertenciaDeBancos(listOf("Falabella"), cuenta("Efectivo")), "una cuenta que no dice su banco")
        assertNull(advertenciaDeBancos(listOf("Falabella"), null), "sin cuenta")
        assertNull(
            advertenciaDeBancos(listOf("Notificación · Google Wallet", "Notificación · Nu"), cuenta("Nu")),
            "en un pago avisado varias veces basta con que uno sea del banco de la cuenta",
        )
    }

    @Test
    fun `un pago avisado varias veces dice por donde llego cada aviso`() {
        val cabeza = cabezaDelAviso(
            listOf(
                aviso("Notificación · Google Wallet", "PANADERIA LA ESQUINA: COP12,300", id = "w"),
                aviso("85540", "Bancolombia: Compraste \$12.300,00 en PANADERIA LA ESQ", id = "s"),
            ),
            ParsedSms(12_300.0, "PANADERIA LA ESQUINA", TransactionType.EXPENSE, "Comida"),
            hoy,
        )
        assertEquals("Panaderia La Esquina", cabeza.titulo)
        assertEquals("Google Wallet · SMS · 11:02 a. m.", cabeza.subtitulo)
    }

    /** Vuelta de diseño 4: el título salía tal cual del SMS. La marca se sigue buscando en el crudo. */
    @Test
    fun `el comercio en mayusculas se muestra con su nombre`() {
        val exito = cabezaDelAviso(
            listOf(aviso("85540", "Bancolombia: Compraste \$84.900,00 en EXITO SUBA con tu T.Deb *1111.")),
            ParsedSms(84_900.0, "EXITO SUBA", TransactionType.EXPENSE, "Mercado"),
            hoy,
        )
        assertEquals("Éxito Suba", exito.titulo)
        assertEquals("exito", exito.marca?.id)
        val rappi = cabezaDelAviso(
            listOf(deFalabella),
            ParsedSms(42_500.0, "RAPPI", TransactionType.EXPENSE, "Restaurantes"),
            hoy,
        )
        assertEquals("Rappi", rappi.titulo)
    }

    @Test
    fun `el banco del aviso como se nombra en una linea`() {
        assertEquals("Nu", bancoDelAviso("Notificación · Nu"))
        assertEquals("Bancolombia", bancoDelAviso("Correo · Bancolombia"))
        assertEquals("Comprobante", bancoDelAviso("Comprobante · transferencia.png"))
        assertEquals("Bancolombia", bancoDelAviso("85540"))
        assertEquals("Bancolombia", bancoDelAviso("891333"))
        // Un código que Movi no conoce no se adivina.
        assertEquals("SMS", bancoDelAviso("899776"))
        assertEquals("SMS", bancoDelAviso(""))
        assertEquals("Falabella", bancoDelAviso("Falabella"))
    }
}
