package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.formatCOP
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
 * # El texto del widget «Te quedan hoy», desde la instantánea
 *
 * Mismo escenario que `TeQuedanEnHoyTest`: lunes 21 de septiembre de 2026, período del 25 de agosto
 * al 24 de septiembre, $10M de ingresos y $3,1M de arriendo pagado. Lo que se fija:
 *
 * - **con instantánea**, la cifra es la del héroe de Hoy ([teQuedanDelInicio]), con la hora de la
 *   instantánea abajo;
 * - **sin instantánea o sin sesión**, «Abre Movi», nunca un cero;
 * - **con una instantánea vieja**, de cuándo es («ayer», o la fecha) y, si ya es de otro período, la
 *   cifra que el héroe mostraría con ella: «Tu plata», no un «Te quedan» del período que terminó.
 */
class TextoDelWidgetTest {

    private fun en(fecha: LocalDate, hora: Int, minuto: Int): Long =
        LocalDateTime(fecha.year, fecha.monthNumber, fecha.dayOfMonth, hora, minuto).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val hoy = LocalDate(2026, 9, 21)
    private val ahora = en(hoy, 18, 30)

    private val instantanea = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 0),
        accounts = listOf(
            Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 4_500_000),
            Account(id = "c1", name = "Efectivo", type = AccountType.CASH, balance = 250_000),
        ),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true)),
        gastoVariablePorDia = mapOf("2026-09-20" to 400_000L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `con instantanea dice la cifra del heroe de Hoy y la hora`() {
        val texto = textoDelWidget(instantanea, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = ahora)

        val delHeroe = assertNotNull(teQuedanDelInicio(instantanea, hoy), "el escenario tiene «Te quedan»")
        assertEquals(delHeroe.rotulo, texto.rotulo)
        assertEquals(formatCOP(delHeroe.monto), texto.cifra)
        assertEquals("a las 9:40", texto.pie)
        assertEquals(delHeroe.enRojo, texto.enRojo)
        assertEquals(ROTULO_TE_QUEDAN, texto.rotulo)
    }

    /**
     * El ahorro del período (Ajustes) viaja en la instantánea dentro de `plataDelDisponible`: el
     * widget, que no pide red, resta lo mismo que el héroe. Guardada y leída de vuelta, sin perder el
     * tramo.
     */
    @Test
    fun `el widget resta el ahorro del periodo que viene en la instantanea`() {
        val plata = PlataDelDisponible(saldoAlInicio = 0, entradas = 10_000_000, guardado = 0)
        val sin = instantanea.copy(plataDelDisponible = plata)
        val con = instantanea.copy(plataDelDisponible = plata.copy(ahorroDelPeriodo = 2_000_000))
        val almacen = mutableMapOf<String, String>()
        val guardada = instantaneaEnMemoria(almacen)
        guardada.guardarDatos("u1", con)
        val leida = assertNotNull(guardada.datos("u1"))
        assertEquals(2_000_000L, leida.plataDelDisponible?.ahorroDelPeriodo)

        val texto = textoDelWidget(leida, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = ahora)
        val delHeroe = assertNotNull(teQuedanDelInicio(con, hoy))
        assertEquals(formatCOP(delHeroe.monto), texto.cifra)
        assertEquals(assertNotNull(teQuedanDelInicio(sin, hoy)).teQuedan - 2_000_000L, delHeroe.teQuedan)
    }

    @Test
    fun `si el heroe dice Te pasaste por, el widget tambien, en rojo`() {
        val gastoDeMas = instantanea.copy(gastoVariablePorDia = mapOf("2026-09-20" to 9_000_000L))
        val texto = textoDelWidget(gastoDeMas, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = ahora)

        assertEquals(ROTULO_TE_PASASTE, texto.rotulo)
        assertTrue(texto.enRojo)
        assertEquals(formatCOP(assertNotNull(teQuedanDelInicio(gastoDeMas, hoy)).monto), texto.cifra)
    }

    @Test
    fun `sin disponible que afirmar dice Tu plata, como el heroe`() {
        val sinIngresos = instantanea.copy(summary = instantanea.summary!!.copy(ingresos = 0), accounts = listOf(
            Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 0),
        ))
        assertNull(teQuedanDelInicio(sinIngresos, hoy))
        val texto = textoDelWidget(sinIngresos, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = ahora)

        assertEquals(HERO_BALANCE_TITLE, texto.rotulo)
        assertEquals(formatCOP(0), texto.cifra)
    }

    @Test
    fun `con los saldos ocultos la cifra sale tapada y el resto igual`() {
        val aLaVista = textoDelWidget(instantanea, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = ahora, saldosOcultos = false)
        val ocultos = textoDelWidget(instantanea, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = ahora, saldosOcultos = true)

        assertEquals(PLATA_OCULTA, ocultos.cifra)
        assertEquals(aLaVista.copy(cifra = PLATA_OCULTA), ocultos, "el rótulo, el pie y el color no cambian")
        assertEquals(formatCOP(assertNotNull(teQuedanDelInicio(instantanea, hoy)).monto), aLaVista.cifra)

        val sinIngresos = instantanea.copy(summary = instantanea.summary!!.copy(ingresos = 0))
        assertEquals(PLATA_OCULTA, textoDelWidget(sinIngresos, en(hoy, 9, 40), haySesion = true, ahora = ahora, saldosOcultos = true).cifra)
        assertNull(textoDelWidget(null, null, haySesion = true, ahora = ahora, saldosOcultos = true).cifra, "«Abre Movi» sigue igual")
    }

    @Test
    fun `sin instantanea dice Abre Movi`() {
        val texto = textoDelWidget(null, selladaEn = null, haySesion = true, ahora = ahora)
        assertEquals(WIDGET_ABRE_MOVI, texto.rotulo)
        assertNull(texto.cifra, "nunca un $0 inventado")
        assertEquals(WIDGET_PARA_VER_CUANTO_TE_QUEDA, texto.pie)
    }

    @Test
    fun `sin sesion dice Abre Movi aunque quede algo guardado`() {
        val texto = textoDelWidget(instantanea, selladaEn = en(hoy, 9, 40), haySesion = false, ahora = ahora)
        assertEquals(WIDGET_ABRE_MOVI, texto.rotulo)
        assertNull(texto.cifra)
    }

    @Test
    fun `una instantanea de ayer lo dice`() {
        val texto = textoDelWidget(instantanea, selladaEn = en(LocalDate(2026, 9, 20), 21, 5), haySesion = true, ahora = ahora)
        assertEquals("ayer a las 21:05", texto.pie)
        assertEquals(ROTULO_TE_QUEDAN, texto.rotulo, "mismo período: la cifra sigue siendo «Te quedan»")
    }

    /**
     * **Vieja de verdad: de un período que ya terminó.** Leída el 3 de octubre, la instantánea del 21
     * de septiembre es del período anterior: sus cifras del período se tiran ([conElPeriodoDe]) y el
     * héroe volvería a «Tu plata». El widget igual, y dice de qué día es.
     */
    @Test
    fun `una instantanea de otro periodo no dice Te quedan del periodo que termino`() {
        val tresDeOctubre = en(LocalDate(2026, 10, 3), 8, 0)
        val texto = textoDelWidget(instantanea, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = tresDeOctubre)

        assertEquals(HERO_BALANCE_TITLE, texto.rotulo)
        assertEquals(formatCOP(4_750_000), texto.cifra, "Tu plata: lo que no depende del período se queda")
        assertEquals("el 21 de septiembre a las 9:40", texto.pie)
        assertFalse(texto.enRojo)
    }

    /** Cuando cae a «Tu plata», lo apartado no cuenta: la misma cifra que el hero de Hoy. */
    @Test
    fun `cuando dice Tu plata descuenta lo apartado, como el heroe`() {
        val conColegio = instantanea.copy(
            apartados = listOf(Apartado(accountId = "a1", proposito = "Colegio nov-dic", monto = 4_000_000)),
        )
        val tresDeOctubre = en(LocalDate(2026, 10, 3), 8, 0)
        val texto = textoDelWidget(conColegio, selladaEn = en(hoy, 9, 40), haySesion = true, ahora = tresDeOctubre)

        assertEquals(HERO_BALANCE_TITLE, texto.rotulo)
        assertEquals(formatCOP(750_000), texto.cifra)
    }

    @Test
    fun `una instantanea sin hora guardada no inventa una`() {
        val texto = textoDelWidget(instantanea, selladaEn = null, haySesion = true, ahora = ahora)
        assertEquals("Toca para actualizar", texto.pie)
        assertEquals(ROTULO_TE_QUEDAN, texto.rotulo)
    }

    @Test
    fun `la instantanea guarda cuando se sello y la borra al cerrar sesion`() {
        val guardado = mutableMapOf<String, String>()
        val i = InstantaneaDelInicio(leer = { guardado[it] }, escribir = { k, v -> if (v == null) guardado.remove(k) else guardado[k] = v })
        var avisos = 0
        InstantaneaDelInicio.alCambiar = { avisos++ }
        try {
            i.guardarDatos("u1", instantanea, ahora = 1_234L)
            assertEquals(1_234L, i.selladaEn("u1"))
            assertEquals(1, avisos, "guardar avisa al widget")
            i.borrar("u1")
            assertNull(i.selladaEn("u1"))
            assertNull(i.datos("u1"))
            assertEquals(2, avisos, "borrar también: el widget se limpia con la sesión")
        } finally {
            InstantaneaDelInicio.alCambiar = null
        }
    }
}
