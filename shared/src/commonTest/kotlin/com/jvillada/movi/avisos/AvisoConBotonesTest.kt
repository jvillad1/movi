package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AvisoPorRevisar
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.porrevisar.loQueMoviEntendio
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Ola 5 · la notificación de un aviso: qué botones lleva y qué dice
 *
 * Los avisos son los de `LaBandejaQueSeVaciaTest` y la decisión sale de la MISMA función que la fila
 * de «Por revisar» ([loQueMoviEntendio]): lo que la fila confirmaría, la notificación también.
 */
class AvisoConBotonesTest {

    private fun aviso(id: String, texto: String) =
        SmsMessage(id = id, time = "2026-10-10 08:10", bank = "85540", text = texto, state = SMS_STATE_PENDING, det = "")

    private val cafe = aviso("cafe", "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 08:10.")
    private val exito = aviso("exito", "Bancolombia: Compraste \$80.000,00 en EXITO con tu T.Deb *8133, el 10/10/2026 a las 10:00.")
    private val tarjeta = aviso("tarjeta", "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133 el 10/10/2026.")
    private val traspaso = aviso("traspaso", "Bancolombia: Transferiste \$500.000 desde tu cuenta *8133 a la cuenta *9586 el 10/10/2026.")
    private val sinCategoria = aviso("nueva", "Bancolombia: Compraste \$12.000,00 en TIENDA NUEVA con tu T.Deb *8133, el 10/10/2026 a las 11:00.")

    private val ahorros = Account("acc-8133", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 5_000_000L, "COP")
    private val fiducuenta = Account("acc-9586", "Fiducuenta 9586", AccountType.SAVINGS, 1_000_000L, "COP")
    private val yaAnotado = FinancialEvent(
        id = "ev-exito", accountId = "acc-8133", type = TransactionType.EXPENSE, amount = 80_000L,
        category = "Mercado", description = "Éxito", timestamp = 0L,
    )

    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("cafe", leido = ParsedSms(46_000.0, "TOSTAO CAFE", TransactionType.EXPENSE, "Comida", aprendidoDe = "Así lo anotaste 4 veces")),
            PropuestaDelAviso("exito", leido = ParsedSms(80_000.0, "EXITO", TransactionType.EXPENSE, "Mercado", aprendidoDe = "x"), coincidencias = listOf(yaAnotado)),
            PropuestaDelAviso("tarjeta", leido = ParsedSms(386_902.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY)),
            PropuestaDelAviso("traspaso", leido = ParsedSms(500_000.0, "Fiducuenta", TransactionType.EXPENSE, "Comida", aprendidoDe = "x", traspasoHaciaId = "acc-9586")),
            PropuestaDelAviso("nueva", leido = ParsedSms(12_000.0, "TIENDA NUEVA", TransactionType.EXPENSE, "Otros")),
        ),
        categoriasDelDueno = listOf("Comida", "Mercado"),
    )

    private val mensajes = listOf(cafe, exito, tarjeta, traspaso, sinCategoria)

    private fun entendido(sms: SmsMessage) =
        loQueMoviEntendio(sms, mensajes, propuestas, listOf(ahorros, fiducuenta), ahora = 1_791_000_000_000L)

    // ── Qué botones ─────────────────────────────────────────────────────────

    @Test
    fun `un gasto suelto que Movi entendio entero ofrece Confirmar y Otra categoria`() {
        assertEquals(BotonesDelAviso.CONFIRMAR_U_OTRA_CATEGORIA, botonesDelAviso(1, entendido(cafe)))
    }

    @Test
    fun `un pago de tarjeta, un traspaso o un aviso con dudas solo ofrecen Revisar`() {
        assertEquals(BotonesDelAviso.SOLO_REVISAR, botonesDelAviso(1, entendido(tarjeta)), "pago de tarjeta")
        assertEquals(BotonesDelAviso.SOLO_REVISAR, botonesDelAviso(1, entendido(traspaso)), "traspaso")
        assertEquals(BotonesDelAviso.SOLO_REVISAR, botonesDelAviso(1, entendido(exito)), "¿ya lo anotaste?")
        assertEquals(BotonesDelAviso.SOLO_REVISAR, botonesDelAviso(1, entendido(sinCategoria)), "sin categoría")
        // Sin la propuesta (sin red, un server viejo) tampoco se confirma.
        assertEquals(BotonesDelAviso.SOLO_REVISAR, botonesDelAviso(1, null))
    }

    @Test
    fun `varios avisos juntos son el resumen, sin Confirmar aunque uno se entienda`() {
        assertEquals(BotonesDelAviso.RESUMEN, botonesDelAviso(3, entendido(cafe)))
        assertEquals(BotonesDelAviso.RESUMEN, botonesDelAviso(2, null))
    }

    // ── Qué dice ────────────────────────────────────────────────────────────

    private val avisoDelCafe = AvisoPorRevisar(
        id = "cafe", origen = "85540", monto = 46_000.0, descripcion = "Tostao Café", tipo = TransactionType.EXPENSE,
    )

    @Test
    fun `dice el comercio y el monto arriba, y la categoria y la cuenta abajo`() {
        val texto = textoDelAvisoSuelto(avisoDelCafe, entendido(cafe).anotacion, llevasHoy = null, ocultos = false)
        assertEquals("Tostao Café · \$46.000", texto.titulo)
        assertEquals("Comida · Bancolombia Ahorros 8133", texto.texto)
        assertTrue(texto.lineas.isEmpty())
    }

    @Test
    fun `sin propuesta dice de donde vino y que se toca para revisar`() {
        val texto = textoDelAvisoSuelto(avisoDelCafe, anotacion = null, llevasHoy = null, ocultos = false)
        assertEquals("Tostao Café · \$46.000", texto.titulo)
        assertEquals("Bancolombia — $TOCA_PARA_REVISAR", texto.texto)
    }

    @Test
    fun `con los saldos ocultos ningun monto sale en claro`() {
        val uno = textoDelAvisoSuelto(avisoDelCafe, entendido(cafe).anotacion, llevasHoy = null, ocultos = true)
        assertEquals("Tostao Café · $PLATA_OCULTA", uno.titulo)
        val varios = assertNotNull(
            textoDeMovimientos(listOf(avisoDelCafe, avisoDelCafe.copy(id = "otro", monto = 180_000.0)), ocultos = true),
        )
        assertFalse(varios.texto.contains("46.000") || varios.texto.contains("180.000"), varios.texto)
        assertTrue(varios.lineas.all { it.startsWith(PLATA_OCULTA) }, varios.lineas.toString())
    }

    // ── «Llevas $X hoy de $Y» ───────────────────────────────────────────────

    private fun en(fecha: LocalDate, hora: Int): Long =
        LocalDateTime(fecha.year, fecha.monthNumber, fecha.dayOfMonth, hora, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val hoy = LocalDate(2026, 9, 21)
    private val ahora = en(hoy, 18)

    private val instantanea = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 0),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = mapOf("2026-09-21" to 64_300L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `Llevas suma lo gastado hoy segun la instantanea y este aviso, contra la meta del dia`() {
        val meta = assertNotNull(disponibleDelInicio(instantanea, hoy)).hoy.meta
        val linea = lineaDeLlevasHoy(instantanea, selladaEn = en(hoy, 9), aviso = avisoDelCafe, ahora = ahora, ocultos = false)
        assertEquals("Llevas ${formatCOPSinOcultar(64_300 + 46_000)} hoy de ${formatCOPSinOcultar(meta)}", linea)
        assertEquals("Llevas $PLATA_OCULTA hoy de $PLATA_OCULTA", lineaDeLlevasHoy(instantanea, en(hoy, 9), avisoDelCafe, ahora, ocultos = true))
    }

    @Test
    fun `Llevas se omite sin instantanea, con una de ayer o con un ingreso`() {
        assertNull(lineaDeLlevasHoy(null, en(hoy, 9), avisoDelCafe, ahora, ocultos = false))
        assertNull(lineaDeLlevasHoy(instantanea, en(LocalDate(2026, 9, 20), 22), avisoDelCafe, ahora, ocultos = false))
        assertNull(lineaDeLlevasHoy(instantanea, en(hoy, 9), avisoDelCafe.copy(tipo = TransactionType.INCOME), ahora, ocultos = false))
        assertNull(lineaDeLlevasHoy(instantanea, en(hoy, 9), avisoDelCafe.copy(moneda = "USD"), ahora, ocultos = false))
    }

    // ── A dónde lleva cada botón, y los atajos del ícono ────────────────────

    @Test
    fun `Revisar y Otra categoria abren Reconciliar de ese aviso`() {
        assertEquals(Screen.SMSReconcile("cafe"), destinoDeAviso(abrirElAviso("cafe")))
        assertEquals(Screen.SMSReconcile("cafe", abrirCategoria = true), destinoDeAviso(abrirElAvisoEnLaCategoria("cafe")))
        assertNull(destinoDeAviso(PREFIJO_ABRIR_AVISO))
    }

    @Test
    fun `los atajos del icono abren Agregar en su pestana y Por revisar`() {
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO), destinoDeAviso(ABRIR_AGREGAR_GASTO))
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.TRASPASO), destinoDeAviso(ABRIR_AGREGAR_TRASPASO))
        assertEquals(Screen.PorRevisar, destinoDeAviso(ABRIR_POR_REVISAR))
    }
}
