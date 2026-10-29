package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CATEGORIAS_QUE_NO_SE_ELIGEN
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esCategoriaQueNoSeElige
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.categoriaSirveParaTipo
import com.jvillada.movi.ui.components.categoriasFrecuentes
import com.jvillada.movi.ui.components.categoriasParaPastillas
import com.jvillada.movi.ui.components.contenidoDelSelectorDeCategoria
import com.jvillada.movi.ui.components.suggestCategoryMatches
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.accionDelPago
import com.jvillada.movi.ui.quickadd.categoriaQueNoSeAnotaAca
import com.jvillada.movi.ui.quickadd.categoriasParaLasPastillasDeAgregar
import com.jvillada.movi.ui.recurrentes.enlaceDeLaPropuestaLejana
import com.jvillada.movi.ui.recurrentes.esPropuestaLejana
import com.jvillada.movi.ui.recurrentes.propuestaDeLaFila
import com.jvillada.movi.ui.sdui.textosDeLaFranjaPorConfirmar
import com.jvillada.movi.ui.sms.categoriasDeLaTarjeta
import com.jvillada.movi.ui.sms.categoriasParaElegirEnElSms
import com.jvillada.movi.ui.sms.esCategoriaDeGastoSuelta
import com.jvillada.movi.ui.transactions.avisosPorConfirmar
import com.jvillada.movi.ui.transactions.ofreceCategoriaEscritaAMano
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 51, lo puro de la app
 *
 * - **La guarda de las categorías internas**: recorre cada selector (las pastillas de Agregar, la hoja
 *   «Todas», Revisar aviso, la tarjeta de Por revisar, Repartir, el cambio de categoría del detalle y el
 *   lote, que sale de ahí) con el dueño que más las usa, y ninguna aparece.
 * - Los avisos de más de 30 días no cuentan en «N por revisar», ni en «Por confirmar $X», ni en el lote.
 * - Un candidato muy distinto del monto no hace «Revisar»: «Anotar pago» y el enlace.
 */
class Vuelta51Test {

    // ── 2 · La guarda: ningún selector ofrece una categoría interna ─────────────────

    private val internas = CATEGORIAS_QUE_NO_SE_ELIGEN.toList()

    /** El dueño que más las usó: todas con los dos tipos y muchos usos recientes. */
    private val usadas = (internas + listOf("Comida", "Transporte", "Salario"))
        .associateWith { setOf(TransactionType.EXPENSE, TransactionType.INCOME) }
    private val usos = (internas + listOf("Comida", "Transporte")).associateWith { 40 }

    private fun ningunaInterna(lista: List<String>, donde: String) {
        val colada = lista.filter { esCategoriaQueNoSeElige(it) }
        assertTrue(colada.isEmpty(), "$donde ofrece una categoría interna: $colada")
    }

    @Test
    fun `la guarda recorre los selectores y ninguna interna aparece`() {
        // Las cuatro que nombra la revisión están en la lista única.
        listOf(CUOTA_CATEGORY, DESEMBOLSO_CATEGORY, TRANSFER_CATEGORY, CARD_PAYMENT_CATEGORY).forEach {
            assertTrue(it in CATEGORIAS_QUE_NO_SE_ELIGEN, it)
        }
        for (tipo in listOf(TransactionType.EXPENSE, TransactionType.INCOME, null)) {
            val t = tipo?.name ?: "sin tipo"
            // `ds/FilaDeCategorias` (pastillas de cualquier pantalla), aunque la pantalla pida una adelante.
            ningunaInterna(categoriasParaPastillas(internas, tipo, usadas, emptyMap(), usos, cuantas = 100), "Las pastillas ($t)")
            // Su hoja «Todas» (el selector completo), sin buscar y buscando.
            ningunaInterna(contenidoDelSelectorDeCategoria("", tipo, usadas, emptyMap(), usos).celdas.map { it.nombre }, "La hoja «Todas» ($t)")
            ningunaInterna(contenidoDelSelectorDeCategoria("cuota", tipo, usadas, emptyMap(), usos).celdas.map { it.nombre }, "La búsqueda ($t)")
            ningunaInterna(suggestCategoryMatches("", tipo, usadas, emptyMap()), "Las sugerencias ($t)")
            // Revisar aviso: con la propuesta del parser y una elegida, las dos internas.
            ningunaInterna(
                categoriasParaElegirEnElSms(CARD_PAYMENT_CATEGORY, tipo, usadas, emptyMap(), usos, cuantas = 100, elegida = CUOTA_CATEGORY),
                "Revisar aviso ($t)",
            )
            if (tipo != null) {
                ningunaInterna(categoriasFrecuentes(tipo, usadas, emptyMap(), usos, cuantas = 100), "Las frecuentes ($t)")
                // Agregar, con la categoría actual interna.
                ningunaInterna(categoriasParaLasPastillasDeAgregar(CUOTA_CATEGORY, tipo, usadas, emptyMap(), usos, cuantas = 100), "Agregar ($t)")
                internas.forEach { assertFalse(categoriaSirveParaTipo(it, tipo, usadas), "$it sirve para $t") }
            }
        }
        // La tarjeta de Por revisar, también cuando cae a la lista del server.
        ningunaInterna(categoriasDeLaTarjeta(frecuentes = internas, delDueno = internas + "Comida"), "La tarjeta")
        assertEquals(listOf("Comida"), categoriasDeLaTarjeta(frecuentes = emptyList(), delDueno = internas + "Comida"))
        // Repartir en partes.
        internas.forEach { assertFalse(esCategoriaDeGastoSuelta(it), "Repartir ofrece $it") }
        // El cambio de categoría del detalle (y el lote, que ofrece lo elegido ahí).
        internas.forEach { assertFalse(ofreceCategoriaEscritaAMano(it, "Comida"), "el detalle deja poner $it") }
        // Escrita a mano en la búsqueda: se explica y no se ofrece crearla ni usarla.
        val escrita = contenidoDelSelectorDeCategoria("cuota de crédito", TransactionType.EXPENSE, usadas, emptyMap(), usos)
        assertNotNull(escrita.reservadaEscrita)
        assertTrue(escrita.celdas.none { normalizarParaBuscar(it.nombre) == normalizarParaBuscar(CUOTA_CATEGORY) })
    }

    @Test
    fun `Agregar no guarda una interna, salvo la de la regla en modo pago`() {
        assertTrue(categoriaQueNoSeAnotaAca(CUOTA_CATEGORY, enModoPago = false))
        assertTrue(categoriaQueNoSeAnotaAca(DESEMBOLSO_CATEGORY, enModoPago = false))
        // Un recurrente con «Cuota de crédito» que el dueño paga por su cuenta: la categoría es de la regla.
        assertFalse(categoriaQueNoSeAnotaAca(CUOTA_CATEGORY, enModoPago = true))
        // Las reservadas, nunca.
        assertTrue(categoriaQueNoSeAnotaAca(TRANSFER_CATEGORY, enModoPago = true))
        assertFalse(categoriaQueNoSeAnotaAca("Comida", enModoPago = false))
    }

    // ── 3 · Los avisos antiguos ───────────────────────────────────────────────────

    private val ahora = LocalDateTime.parse("2026-10-28T12:00:00").toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun aviso(id: String, time: String) =
        SmsMessage(id = id, time = time, bank = "85540", text = "Compraste", state = SMS_STATE_PENDING, det = "")

    private val bandeja = listOf(
        aviso("uber", "2026-10-28 09:00"),
        aviso("rappi", "2026-09-02 19:40"),
        aviso("pana", "2026-09-10 14:05"),
    )

    @Test
    fun `los antiguos no cuentan en el numero pero la bandeja no esta al dia`() {
        val conteo = conteoPorRevisar(bandeja, dias = emptyList(), candidatos = emptyList(), debitos = emptyList(), ahora = ahora)
        assertEquals(1, conteo.avisos)
        assertEquals(2, conteo.avisosAntiguos)
        assertEquals(1, conteo.total, "«1 por revisar»: los antiguos van aparte")
        assertEquals(listOf("uber"), pagosRecientesPorRevisar(bandeja, ahora).map { it.id })
        assertEquals(listOf("pana", "rappi"), pagosAntiguosPorRevisar(bandeja, ahora).map { it.id }, "del más nuevo al más viejo")

        val soloAntiguos = bandeja.drop(1)
        assertEquals(0, conteoPorRevisar(soloAntiguos, emptyList(), emptyList(), emptyList(), ahora).total)
        assertFalse(bandejaAlDia(soloAntiguos, emptyList(), emptyList(), emptyList()), "esperan: no es «Todo al día»")
    }

    @Test
    fun `el lote y su suma no los incluyen`() {
        val lote = avisosPorConfirmar(bandeja, propuestas = null, cuentas = emptyList(), ahora = ahora)
        assertEquals(1, lote.cuantos)
    }

    @Test
    fun `la franja los menciona aparte`() {
        val conteo = ConteoPorRevisar(avisos = 1, avisosAntiguos = 2)
        assertEquals("Por confirmar \$18.300" to "En 1 aviso · y 2 antiguos", textosDeLaFranjaPorConfirmar(conteo, 18_300L))
        assertEquals("Por confirmar" to "1 aviso · y 1 antiguo", textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 1, avisosAntiguos = 1), null))
        // Sin antiguos, lo de siempre.
        assertEquals("Por confirmar \$18.300" to "En 1 aviso", textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 1), 18_300L))
    }

    // ── 4 · El candidato que no se parece ─────────────────────────────────────────

    private fun movimiento(id: String, monto: Long) = FinancialEvent(
        id = id, accountId = "acc", type = TransactionType.EXPENSE, amount = monto, category = "Transporte",
        description = "", timestamp = ahora,
    )

    private val carro = PagoDelPeriodo(
        ruleId = "r-carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -3,
        periodoDelSello = "2026-10", candidatos = listOf(movimiento("ev-tanqueo", 260_000L)),
    )

    @Test
    fun `el tanqueo no hace Revisar, la fila sigue con Anotar pago`() {
        assertEquals("Anotar pago", accionDelPago(carro).first)
        val propuesta = propuestaDeLaFila(carro, emptySet())
        assertTrue(esPropuestaLejana(carro, propuesta))
        assertEquals("¿Es el de \$260.000?", enlaceDeLaPropuestaLejana(propuesta!!))
        // Nunca se tilda solo: la fila sigue abierta y sin pagar.
        assertFalse(carro.pagado)
    }

    @Test
    fun `uno parecido sigue siendo la pregunta, y va antes que el lejano`() {
        val conParecido = carro.copy(candidatos = listOf(movimiento("ev-tanqueo", 260_000L), movimiento("ev-factura", 9_200L)))
        assertEquals("Revisar", accionDelPago(conParecido).first)
        val propuesta = propuestaDeLaFila(conParecido, emptySet())
        assertEquals("ev-factura", propuesta?.id)
        assertFalse(esPropuestaLejana(conParecido, propuesta))
        // Si el dueño dijo «No fue este» al parecido, queda el lejano, como enlace.
        val sinElParecido = propuestaDeLaFila(conParecido, setOf("r-carro|ev-factura"))
        assertEquals("ev-tanqueo", sinElParecido?.id)
        assertTrue(esPropuestaLejana(conParecido, sinElParecido))
    }
}
